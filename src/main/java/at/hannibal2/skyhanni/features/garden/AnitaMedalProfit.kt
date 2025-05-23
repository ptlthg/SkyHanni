package at.hannibal2.skyhanni.features.garden

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.features.garden.visitor.VisitorApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.DisplayTableEntry
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemCategory
import at.hannibal2.skyhanni.utils.ItemPriceUtils.getPrice
import at.hannibal2.skyhanni.utils.ItemPriceUtils.getPriceName
import at.hannibal2.skyhanni.utils.ItemUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.ItemUtils.getItemCategoryOrNull
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzColor.Companion.toLorenzColor
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.add
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.RenderableUtils
import net.minecraft.item.ItemStack

@SkyHanniModule
object AnitaMedalProfit {

    private val config get() = GardenApi.config.anitaShop
    private var display = emptyList<Renderable>()

    var inInventory = false

    enum class MedalType(
        val displayName: String,
        val simpleName: String = displayName.removeColor().split(" ")[0],
        val factorBronze: Int,
        val color: LorenzColor = displayName.substring(1, 2)[0].toLorenzColor() ?: LorenzColor.WHITE,
    ) {
        GOLD("§6Gold medal", "gold", 8),
        SILVER("§fSilver medal", "silver", 2),
        BRONZE("§cBronze medal", "bronze", 1),
        ;

        companion object {
            fun bySimpleNameOrNull(name: String) = entries.firstOrNull { it.simpleName == name }
        }
    }

    private fun getMedal(name: String) = MedalType.entries.firstOrNull { it.displayName == name }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        inInventory = false
    }

    @HandleEvent
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        if (!config.medalProfitEnabled) return
        if (event.inventoryName != "Anita") return
        if (VisitorApi.inInventory) return

        inInventory = true

        val table = mutableListOf<DisplayTableEntry>()
        for ((slot, item) in event.inventoryItems) {
            try {
                readItem(slot, item, table)
            } catch (e: Throwable) {
                ErrorManager.logErrorWithData(
                    e, "Error in AnitaMedalProfit while reading item '${item.repoItemName}'",
                    "item" to item,
                    "name" to item.repoItemName,
                    "inventory name" to InventoryUtils.openInventoryName(),
                )
            }
        }

        val newList = mutableListOf<Renderable>()
        newList.add(Renderable.string("§eProfit per Bronze Medal"))
        newList.add(RenderableUtils.fillTable(table, padding = 5, itemScale = 0.7))
        display = newList
    }

    private fun readItem(slot: Int, item: ItemStack, table: MutableList<DisplayTableEntry>) {
        val itemName = getItemName(item)
        if (isInvalidItemName(itemName)) return

        val requiredItems = getRequiredItems(item)
        val additionalMaterials = getAdditionalMaterials(requiredItems)
        val additionalCost = getAdditionalCost(additionalMaterials)

        // Ignore items without medal cost, e.g. InfiniDirt Wand
        val bronzeCost = getBronzeCost(requiredItems) ?: return

        val (name, amount) = ItemUtils.readItemAmount(itemName) ?: return

        var internalName = NeuInternalName.fromItemNameOrNull(name)
        if (internalName == null) {
            internalName = item.getInternalName()
        }

        val itemPrice = internalName.getPrice() * amount
        if (itemPrice < 0) return

        val profitPerSell = itemPrice - additionalCost

        // profit per bronze
        val profitPerBronze = profitPerSell / bronzeCost

        val profitPerSellFormat = profitPerSell.shortFormat()
        val profitPerBronzeFormat = profitPerBronze.shortFormat()
        val color = if (profitPerBronze > 0) "§6" else "§c"

        val hover = buildList {
            add(itemName)
            add("")
            add("§7Sell price: §6${itemPrice.shortFormat()}")

            // TODO add more exact material cost breakdown
            add("§7Additional cost: §6${additionalCost.shortFormat()}")
            addAdditionalMaterials(additionalMaterials)

            add("§7Profit per sell: §6$profitPerSellFormat")
            add("")
            add("§7Bronze medals required: §c$bronzeCost")
            add("§7Profit per bronze medal: §6$profitPerBronzeFormat")
        }
        table.add(
            DisplayTableEntry(
                itemName,
                "$color$profitPerBronzeFormat",
                profitPerBronze,
                internalName,
                hover,
                highlightsOnHoverSlots = listOf(slot),
            ),
        )
    }

    private fun MutableList<String>.addAdditionalMaterials(additionalMaterials: Map<NeuInternalName, Int>) {
        for ((internalName, amount) in additionalMaterials) {
            add(internalName.getPriceName(amount))
        }
    }

    private val invalidItemNames = listOf(
        " ",
        "§cClose",
        "§eUnique Gold Medals",
        "§aMedal Trades",
    )

    private fun isInvalidItemName(itemName: String): Boolean = itemName in invalidItemNames

    private fun getItemName(item: ItemStack): String {
        val name = item.displayName
        val isEnchantedBook = item.getItemCategoryOrNull() == ItemCategory.ENCHANTED_BOOK
        return if (isEnchantedBook) {
            item.repoItemName
        } else name
    }

    private fun getAdditionalMaterials(requiredItems: Map<String, Int>): Map<NeuInternalName, Int> {
        val additionalMaterials = mutableMapOf<NeuInternalName, Int>()
        for ((name, amount) in requiredItems) {
            val medal = getMedal(name)
            if (medal == null) {
                additionalMaterials[NeuInternalName.fromItemName(name)] = amount
            }
        }
        return additionalMaterials
    }

    private fun getAdditionalCost(requiredItems: Map<NeuInternalName, Int>): Double {
        var otherItemsPrice = 0.0
        for ((name, amount) in requiredItems) {
            otherItemsPrice += name.getPrice() * amount
        }
        return otherItemsPrice
    }

    private fun getBronzeCost(requiredItems: Map<String, Int>): Int? {
        for ((name, amount) in requiredItems) {
            getMedal(name)?.let {
                return it.factorBronze * amount
            }
        }
        return null
    }

    private fun getRequiredItems(item: ItemStack): MutableMap<String, Int> {
        val items = mutableMapOf<String, Int>()
        var next = false
        val lore = item.getLore()
        for (line in lore) {
            if (line == "§7Cost") {
                next = true
                continue
            }
            if (next) {
                if (line == "") {
                    next = false
                    continue
                }

                val rawItemName = line.replace("§8 ", " §8")

                val pair = ItemUtils.readItemAmount(rawItemName)
                if (pair == null) {
                    ErrorManager.logErrorStateWithData(
                        "Error in Anita Medal Contest", "Could not read item amount",
                        "rawItemName" to rawItemName,
                        "name" to item.displayName,
                        "lore" to lore,
                    )
                    continue
                }
                items.add(pair)
            }
        }
        return items
    }

    @HandleEvent
    fun onBackgroundDraw(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (!inInventory || VisitorApi.inInventory) return
        config.medalProfitPos.renderRenderables(
            display,
            extraSpace = 5,
            posLabel = "Anita Medal Profit",
        )
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(3, "garden.anitaMedalProfitEnabled", "garden.anitaShop.medalProfitEnabled")
        event.move(3, "garden.anitaMedalProfitPos", "garden.anitaShop.medalProfitPos")
    }
}
