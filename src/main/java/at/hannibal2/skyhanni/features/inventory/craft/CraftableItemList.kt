package at.hannibal2.skyhanni.features.inventory.craft

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.SackApi
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryOpenEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemPriceUtils.getPrice
import at.hannibal2.skyhanni.utils.ItemUtils
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.NeuItems.isVanillaItem
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.PrimitiveItemStack.Companion.toPrimitiveStackOrNull
import at.hannibal2.skyhanni.utils.PrimitiveRecipe
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.StringUtils
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.addOrPut
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.sortedDesc
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.toSingletonListOrEmpty
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.SearchTextInput
import at.hannibal2.skyhanni.utils.renderables.Searchable
import at.hannibal2.skyhanni.utils.renderables.buildSearchableScrollable
import at.hannibal2.skyhanni.utils.renderables.toSearchable
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import kotlin.math.floor

@SkyHanniModule
object CraftableItemList {
    private val config get() = SkyHanniMod.feature.inventory.craftableItemList

    private var display = listOf<Renderable>()
    private var inInventory = false
    private val textInput = SearchTextInput()
    private val craftItemPattern by RepoPattern.pattern(
        "craftableitemlist.craftitem",
        "Craft Item",
    )

    @HandleEvent
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (!isEnabled()) return
        if (!craftItemPattern.matches(event.inventoryName)) return
        inInventory = true

        val pricePer = mutableMapOf<NeuInternalName, Double>()
        val lines = mutableMapOf<NeuInternalName, Searchable>()
        loadItems(pricePer, lines)

        display = if (lines.isEmpty()) {
            Renderable.hoverTips(
                "§7No Items to craft",
                tips = listOf(
                    "§7No items found in your Inventory",
                    "or sacks that can be used as",
                    "material in crafting recipes.",
                ),
            ).toSingletonListOrEmpty()
        } else {
            buildList<Renderable> {
                val items = pricePer.sortedDesc().keys.map { lines[it] ?: error("impossible") }
                add(Renderable.string("§e§lCraftable Items §7(${items.size})"))
                add(items.buildSearchableScrollable(height = 250, textInput, velocity = 20.0))
            }
        }
    }

    private fun loadItems(
        pricePer: MutableMap<NeuInternalName, Double>,
        lines: MutableMap<NeuInternalName, Searchable>,
    ) {
        val availableMaterial = readItems()
        for (internalName in NeuItems.allInternalNames.values) {
            if (config.excludeVanillaItems && internalName.isVanillaItem()) continue

            val recipes = NeuItems.getRecipes(internalName)
            for (recipe in recipes) {
                if (!recipe.isCraftingRecipe()) continue
                val renderable = createItemRenderable(recipe, availableMaterial, pricePer, internalName) ?: continue
                lines[internalName] = renderable
            }
        }
    }

    private fun createItemRenderable(
        recipe: PrimitiveRecipe,
        availableMaterial: Map<NeuInternalName, Long>,
        pricePer: MutableMap<NeuInternalName, Double>,
        internalName: NeuInternalName,
    ): Searchable? {
        val neededItems = ItemUtils.neededItems(recipe)
        // Just a fail save, should not happen normally
        if (neededItems.isEmpty()) return null

        val canCraftAmount = canCraftAmount(neededItems, availableMaterial)
        if (canCraftAmount <= 0) return null

        val amountFormat = canCraftAmount.addSeparators()
        val totalPrice = pricePer(neededItems)
        pricePer[internalName] = totalPrice
        val itemName = internalName.repoItemName
        val tooltip = buildList {
            add(itemName)
            add("")
            add("§7Craft cost: §6${totalPrice.shortFormat()}")
            for ((item, amount) in neededItems) {
                val name = item.repoItemName
                val price = item.getPrice() * amount
                add(" §8x${amount.addSeparators()} $name §7(§6${price.shortFormat()}§7)")
            }
            add("")
            add("§7You have enough materials")
            val timeName = StringUtils.pluralize(canCraftAmount, "time", "times")
            add("§7to craft this item §e$amountFormat §7$timeName!")
            add("")
            add("§eClick to craft!")
        }
        return Renderable.clickable(
            "§8x$amountFormat $itemName",
            tips = tooltip,
            onLeftClick = {
                HypixelCommands.viewRecipe(internalName)
            },
        ).toSearchable(itemName)
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        inInventory = false
    }

    private fun pricePer(neededItems: Map<NeuInternalName, Int>): Double = neededItems.map {
        it.key.getPrice() * it.value
    }.sum()

    private fun canCraftAmount(
        need: Map<NeuInternalName, Int>,
        available: Map<NeuInternalName, Long>,
    ): Int {
        val canCraftTotal = mutableListOf<Int>()
        for ((name, neededAmount) in need) {
            val having = available[name] ?: 0
            val canCraft = floor(having.toDouble() / neededAmount).toInt()
            canCraftTotal.add(canCraft)
        }
        return canCraftTotal.min()
    }

    private fun readItems(): Map<NeuInternalName, Long> {
        val materials = mutableMapOf<NeuInternalName, Long>()
        for (stack in InventoryUtils.getItemsInOwnInventory()) {
            val item = stack.toPrimitiveStackOrNull() ?: continue
            materials.addOrPut(item.internalName, item.amount.toLong())
        }
        if (config.includeSacks) {
            for ((internalName, item) in SackApi.sackData) {
                materials.addOrPut(internalName, item.amount.toLong())
            }
        }
        return materials
    }

    @HandleEvent
    fun onBackgroundDraw(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (!isEnabled()) return
        if (!inInventory) return

        config.position.renderRenderables(display, posLabel = "Craftable Item List")
    }

    fun isEnabled() = LorenzUtils.inSkyBlock && config.enabled
}
