package at.hannibal2.skyhanni.utils.tracker

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.config.features.misc.TrackerConfig.TextPart
import at.hannibal2.skyhanni.config.storage.ProfileSpecificStorage
import at.hannibal2.skyhanni.data.TrackerManager
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ItemPriceUtils.formatCoin
import at.hannibal2.skyhanni.utils.ItemUtils
import at.hannibal2.skyhanni.utils.ItemUtils.readableInternalName
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.KeyboardManager
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.SKYBLOCK_COIN
import at.hannibal2.skyhanni.utils.NeuItems.getItemStackOrNull
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.StringUtils.pluralize
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.sortedDesc
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.ScrollValue
import at.hannibal2.skyhanni.utils.renderables.Searchable
import at.hannibal2.skyhanni.utils.renderables.toSearchable
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

open class SkyHanniItemTracker<Data : ItemTrackerData>(
    name: String,
    createNewSession: () -> Data,
    getStorage: (ProfileSpecificStorage) -> Data,
    extraDisplayModes: Map<DisplayMode, (ProfileSpecificStorage) -> Data> = emptyMap(),
    drawDisplay: (Data) -> List<Searchable>,
) : SkyHanniTracker<Data>(name, createNewSession, getStorage, extraDisplayModes, drawDisplay = drawDisplay) {

    companion object {
        private val config get() = SkyHanniMod.feature.misc.tracker
    }

    private var scrollValue = ScrollValue()

    open fun addCoins(amount: Int, command: Boolean) {
        addItem(SKYBLOCK_COIN, amount, command)
    }

    open fun addItem(internalName: NeuInternalName, amount: Int, command: Boolean, message: Boolean = true) {
        modify {
            it.addItem(internalName, amount, command)
        }
        getSharedTracker()?.let { sharedData ->
            val isHidden = sharedData.get(DisplayMode.TOTAL).items[internalName]?.hidden
            if (isHidden != null) sharedData.modify { it.items[internalName]?.hidden = isHidden }
        }

        if (command) {
            TrackerManager.commandEditTrackerSuccess = true
            val displayName = internalName.repoItemName
            if (amount > 0) {
                ChatUtils.chat("Manually added to $name: §r$displayName §7(${amount}x§7)")
            } else {
                ChatUtils.chat("Manually removed from $name: §r$displayName §7(${-amount}x§7)")
            }
            return
        }
        handlePossibleRareDrop(internalName, amount, message)
    }

    private fun NeuInternalName.getCleanName(
        items: Map<NeuInternalName, ItemTrackerData.TrackedItem>,
        getCoinName: (ItemTrackerData.TrackedItem) -> String,
    ): String {
        val item = items[this] ?: error("Item not found for $this")
        return if (this == SKYBLOCK_COIN) getCoinName.invoke(item) else this.repoItemName
    }

    open fun drawItems(
        data: Data,
        filter: (NeuInternalName) -> Boolean,
        lists: MutableList<Searchable>,
        /**
         * Extensions to allow for BucketedTrackers to re-use this code block.
         * The default values are for any 'normal' item tracker, but can in theory
         * be overridden by any tracker that needs to.
         */
        itemsAccessor: () -> Map<NeuInternalName, ItemTrackerData.TrackedItem> = { data.items },
        getCoinName: (ItemTrackerData.TrackedItem) -> String = { item -> data.getCoinName(item) },
        itemRemover: (NeuInternalName, String) -> Unit = { item, cleanName ->
            modify {
                it.items.remove(item)
            }
            ChatUtils.chat("Removed $cleanName §efrom $name.")
        },
        itemHider: (NeuInternalName, Boolean) -> Unit = { item, currentlyHidden ->
            modify {
                it.items[item]?.hidden = !currentlyHidden
            }
        },
        getLoreList: (NeuInternalName, ItemTrackerData.TrackedItem) -> List<String> = { internalName, item ->
            if (internalName == SKYBLOCK_COIN) data.getCoinDescription(item)
            else data.getDescription(item.timesGained)
        },
    ): Double {
        var profit = 0.0
        val items = mutableMapOf<NeuInternalName, Long>()
        val dataItems = itemsAccessor.invoke()
        for ((internalName, itemProfit) in dataItems) {
            if (!filter(internalName)) continue

            val amount = itemProfit.totalAmount
            val pricePer = if (internalName == SKYBLOCK_COIN) 1.0 else data.getCustomPricePer(internalName)
            val price = (pricePer * amount).toLong()
            val hidden = itemProfit.hidden

            if (isInventoryOpen() || !hidden) {
                items[internalName] = price
            }
            if (!hidden || !config.excludeHiddenItemsInPrice) {
                profit += price
            }
        }

        val table = mutableMapOf<List<Renderable>, String>()

        for ((internalName, price) in items.sortedDesc()) {
            val itemProfit = dataItems[internalName] ?: error("Item not found for $internalName")

            val amount = itemProfit.totalAmount
            val displayAmount = if (internalName == SKYBLOCK_COIN) itemProfit.timesGained else amount

            val cleanName = internalName.getCleanName(dataItems, getCoinName)

            val hidden = itemProfit.hidden
            val priceFormat = price.formatCoin(gray = hidden)
            val newDrop = itemProfit.lastTimeUpdated.passedSince() < 10.seconds && config.showRecentDrops
            val numberColor = if (newDrop) "§a§l" else "§7"

            val formattedName = cleanName.removeColor(keepFormatting = true).replace("§r", "")
            val displayName = if (hidden) "§8§m$formattedName" else cleanName

            val loreText = getLoreList.invoke(internalName, itemProfit)
            val lore: List<String> = buildLore(loreText, hidden, newDrop, internalName)

            // TODO add row abstraction to api, with common click+hover behaviour
            fun string(string: String): Renderable = if (isInventoryOpen()) Renderable.clickable(
                string,
                tips = lore,
                onLeftClick = {
                    if (KeyboardManager.isModifierKeyDown()) itemRemover.invoke(internalName, cleanName)
                    else itemHider.invoke(internalName, hidden)
                    update()
                },
            ) else Renderable.string(string)

            val row = mutableMapOf<TextPart, Renderable>()
            row[TextPart.NAME] = string(" $displayName")

            val itemStackOrNull = if (internalName == SKYBLOCK_COIN) {
                ItemUtils.getCoinItemStack(amount)
            } else {
                internalName.getItemStackOrNull()
            }
            itemStackOrNull?.let {
                row[TextPart.ICON] = Renderable.itemStack(it)
            }

            row[TextPart.TOTAL_PRICE] = string(" $priceFormat")
            row[TextPart.AMOUNT] = string(" $numberColor${displayAmount.addSeparators()}x")

            val line = config.textOrder.get().mapNotNull { row[it] }
            table[line] = cleanName
        }

        val scrollValue = (data as? BucketedItemTrackerData<*>)?.selectedScrollValue ?: scrollValue
        Renderable.searchableScrollable(
            table,
            key = 99,
            lines = min(items.size, config.itemsShown.get()),
            velocity = 5.0,
            textInput = textInput,
            scrollValue = scrollValue,
            asTable = config.showTable.get(),
            showScrollableTipsInList = isInventoryOpen(),
        )?.let {
            lists.add(it.toSearchable())
        }


        return profit
    }

    private fun buildLore(
        loreFormat: List<String>,
        hidden: Boolean,
        newDrop: Boolean,
        internalName: NeuInternalName,
    ) = buildList {
        add(internalName.repoItemName)
        add("")
        addAll(loreFormat)
        add("")
        if (newDrop) {
            add("§aYou obtained this item recently.")
            add("")
        }
        add("§eClick to " + (if (hidden) "show" else "hide") + "!")
        add("§eControl + Click to remove this item!")

        add("")
        add("§7Use §e/shedittracker ${internalName.readableInternalName} <amount>")
        add("§7to edit the number.")
        add("§7Use negative numbers to remove items.")

        if (LorenzUtils.debug) {
            add("")
            add("§7$internalName")
        }
    }

    fun addTotalProfit(profit: Double, totalAmount: Long, action: String): Searchable {
        val profitFormat = profit.toLong().addSeparators()
        val profitPrefix = if (profit < 0) "§c" else "§6"

        val tips = if (totalAmount > 0) {
            val profitPerCatch = profit / totalAmount
            val profitPerCatchFormat = profitPerCatch.shortFormat()
            listOf("§7Profit per $action: $profitPrefix$profitPerCatchFormat")
        } else emptyList()

        val coinFormat = "coin".pluralize(profit.toInt())
        val text = "§eTotal Profit: $profitPrefix$profitFormat $coinFormat"
        return Renderable.hoverTips(text, tips).toSearchable()
    }
}
