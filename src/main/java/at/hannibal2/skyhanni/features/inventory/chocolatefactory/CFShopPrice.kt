package at.hannibal2.skyhanni.features.inventory.chocolatefactory

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.data.ChocolateAmount
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.DisplayTableEntry
import at.hannibal2.skyhanni.utils.ItemPriceUtils.getPrice
import at.hannibal2.skyhanni.utils.ItemPriceUtils.getPriceOrNull
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.ItemUtils.loreCosts
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatLong
import at.hannibal2.skyhanni.utils.NumberUtil.million
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.groupOrNull
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.StringUtils.addStrikethorugh
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.UtilsPatterns
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.RenderableUtils
import net.minecraft.item.ItemStack

@SkyHanniModule
object CFShopPrice {
    private val config get() = CFApi.config.chocolateShopPrice

    private var display = emptyList<Renderable>()
    private var products = emptyList<Product>()

    val menuNamePattern by CFApi.patternGroup.pattern(
        "shop.title",
        "Chocolate Shop",
    )

    /**
     * REGEX-TEST: §aYou bought §r§aSupreme Chocolate Bar§r§a!
     * REGEX-TEST: §aYou bought §r§aSupreme Chocolate Bar§r§8 x5§r§a!
     */
    private val itemBoughtPattern by CFApi.patternGroup.pattern(
        "shop.bought",
        "§aYou bought §r§.(?<item>[\\w ]+)§r(?:§8 x(?<amount>\\d+)§r)?§a!",
    )

    /**
     * REGEX-TEST: §7Chocolate Spent: §60
     */
    private val chocolateSpentPattern by CFApi.patternGroup.pattern(
        "shop.spent",
        "§7Chocolate Spent: §6(?<amount>[\\d,]+)",
    )

    var inInventory = false
    private var callUpdate = false
    var inventoryItems = emptyMap<Int, ItemStack>()

    private const val MILESTONE_INDEX = 50
    private var chocolateSpent = 0L

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (inInventory) {
            update()
        }
    }

    @HandleEvent
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        if (!isEnabled()) return
        val isInShop = menuNamePattern.matches(event.inventoryName)
        val isInShopOptions = UtilsPatterns.shopOptionsPattern.matches(event.inventoryName)

        if (!isInShop && !isInShopOptions) return
        if (event.inventoryItems[48]?.getLore()?.first() != "§7To Chocolate Shop" && isInShopOptions) return

        inInventory = true
        callUpdate = isInShop

        inventoryItems = event.inventoryItems
        if (!callUpdate) {
            products.forEach { it.slot = null }
        }
        update()
    }

    private fun updateProducts() {
        val newProducts = mutableListOf<Product>()
        for ((slot, item) in inventoryItems) {
            val lore = item.getLore()

            if (slot == MILESTONE_INDEX) {
                chocolateSpentPattern.firstMatcher(lore) {
                    chocolateSpent = group("amount").formatLong()
                }
            }

            val chocolate = CFApi.getChocolateBuyCost(lore) ?: continue
            val internalName = item.getInternalName()
            val itemPrice = internalName.getPriceOrNull() ?: continue
            val otherItemsPrice = item.loreCosts().sumOf { it.getPrice() }.takeIf { it != 0.0 }
            val canBeBought = lore.any { it == "§eClick to trade!" }

            newProducts.add(Product(slot, item.repoItemName, internalName, chocolate, itemPrice, otherItemsPrice, canBeBought))
        }
        products = newProducts
    }

    private fun update() {
        if (callUpdate) updateProducts()

        val multiplier = 1.million
        // TODO merge core with SkyMartCopperPrice into a utils
        val table = mutableListOf<DisplayTableEntry>()

        for (product in products) {

            val profit = product.itemPrice - (product.otherItemPrice ?: 0.0)
            val factor = (profit / product.chocolate) * multiplier
            val perFormat = factor.shortFormat()

            val hover = buildList {
                add(product.name)

                add("")
                add("§7Item price: §6${product.itemPrice.shortFormat()} ")
                product.otherItemPrice?.let {
                    add("§7Additional cost: §6${it.shortFormat()} ")
                }
                add("§7Profit per purchase: §6${profit.shortFormat()} ")
                add("")
                add("§7Chocolate amount: §c${product.chocolate.shortFormat()} ")
                add("§7Profit per million chocolate: §6$perFormat ")
                add("")
                val formattedTimeUntilGoal = ChocolateAmount.CURRENT.formattedTimeUntilGoal(product.chocolate)
                add("§7Time until affordable: §6$formattedTimeUntilGoal ")

                if (!product.canBeBought) {
                    add("")
                    add("§cCannot be bought!")
                }
            }
            table.add(
                DisplayTableEntry(
                    product.name.addStrikethorugh(!product.canBeBought),
                    "§6§l$perFormat",
                    factor,
                    product.item,
                    hover,
                    highlightsOnHoverSlots = product.slot?.let { listOf(it) }.orEmpty(),
                ),
            )
        }

        display = buildList {
            add(Renderable.string("§e§lCoins per million chocolate§f:"))
            // TODO update this value every second
            // TODO add time until can afford
            add(Renderable.string("§eChocolate available: §6${ChocolateAmount.CURRENT.formatted}"))
            // TODO add chocolate spend needed for next milestone
            add(Renderable.string("§eChocolate spent: §6${chocolateSpent.addSeparators()}"))
            add(RenderableUtils.fillTable(table, padding = 5, itemScale = config.itemScale.toDouble()))
        }
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        inInventory = false
        callUpdate = false
    }

    @HandleEvent
    fun onBackgroundDraw(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (inInventory) {
            config.position.renderRenderables(
                display,
                extraSpace = 5,
                posLabel = "Chocolate Shop Price",
            )
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onChat(event: SkyHanniChatEvent) {
        if (!inInventory) return
        itemBoughtPattern.matchMatcher(event.message) {
            val item = group("item")
            val amount = groupOrNull("amount")?.toIntOrNull() ?: 1
            val product = products.find { it.name.removeColor() == item } ?: return

            ChocolateAmount.addToCurrent(product.chocolate * -amount)
        }

    }

    private fun isEnabled() = LorenzUtils.inSkyBlock && config.enabled

    private data class Product(
        var slot: Int?,
        val name: String,
        val item: NeuInternalName,
        val chocolate: Long,
        val itemPrice: Double,
        val otherItemPrice: Double?,
        val canBeBought: Boolean,
    )
}
