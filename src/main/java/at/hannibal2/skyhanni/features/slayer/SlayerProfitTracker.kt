package at.hannibal2.skyhanni.features.slayer

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.storage.ProfileSpecificStorage
import at.hannibal2.skyhanni.data.ItemAddManager
import at.hannibal2.skyhanni.data.SlayerApi
import at.hannibal2.skyhanni.data.jsonobjects.repo.SlayerProfitTrackerItemsJson
import at.hannibal2.skyhanni.events.ItemAddEvent
import at.hannibal2.skyhanni.events.PurseChangeCause
import at.hannibal2.skyhanni.events.PurseChangeEvent
import at.hannibal2.skyhanni.events.RepositoryReloadEvent
import at.hannibal2.skyhanni.events.SlayerQuestCompleteEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.slayer.SlayerChangeEvent
import at.hannibal2.skyhanni.features.misc.ReplaceRomanNumerals
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatDouble
import at.hannibal2.skyhanni.utils.NumberUtil.formatPercentage
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RenderDisplayHelper
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addSearchString
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.Searchable
import at.hannibal2.skyhanni.utils.renderables.toSearchable
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import at.hannibal2.skyhanni.utils.tracker.ItemTrackerData
import at.hannibal2.skyhanni.utils.tracker.SkyHanniItemTracker
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.annotations.Expose

@SkyHanniModule
object SlayerProfitTracker {

    private val config get() = SlayerApi.config.itemProfitTracker

    private var category = ""
    private val categoryName get() = ReplaceRomanNumerals.replaceLine(category)
    private var baseSlayerType = ""
    private val trackers = mutableMapOf<String, SkyHanniItemTracker<Data>>()

    /**
     * REGEX-TEST: §7Took 1.9k coins from your bank for auto-slayer...
     */
    private val autoSlayerBankPattern by RepoPattern.pattern(
        "slayer.autoslayer.bank.chat",
        "§7Took (?<coins>.+) coins from your bank for auto-slayer\\.\\.\\.",
    )

    class Data : ItemTrackerData() {

        override fun resetItems() {
            slayerSpawnCost = 0
            slayerCompletedCount = 0
        }

        @Expose
        var slayerSpawnCost: Long = 0

        @Expose
        var slayerCompletedCount = 0L

        override fun getDescription(timesDropped: Long): List<String> {
            val percentage = timesDropped.toDouble() / slayerCompletedCount
            val perBoss = percentage.coerceAtMost(1.0).formatPercentage()

            return listOf(
                "§7Dropped §e${timesDropped.addSeparators()} §7times.",
                "§7Your drop rate: §c$perBoss",
            )
        }

        override fun getCoinName(item: TrackedItem) = "§6Mob Kill Coins"

        override fun getCoinDescription(item: TrackedItem): List<String> {
            val mobKillCoinsFormat = item.totalAmount.shortFormat()
            return listOf(
                "§7Killing mobs gives you coins (more with scavenger).",
                "§7You got §6$mobKillCoinsFormat coins §7that way.",
            )
        }
    }

    private val ItemTrackerData.TrackedItem.timesDropped get() = timesGained

    private fun addSlayerCosts(price: Double) {
        require(price < 0) { "slayer costs can not be positive" }
        getTracker()?.modify {
            it.slayerSpawnCost += price.toInt()
        }
    }

    private var allowedItems = mapOf<String, List<NeuInternalName>>()

    @HandleEvent
    fun onRepoReload(event: RepositoryReloadEvent) {
        allowedItems = event.getConstant<SlayerProfitTrackerItemsJson>("SlayerProfitTrackerItems").slayers
    }

    @HandleEvent
    fun onPurseChange(event: PurseChangeEvent) {
        if (!isEnabled()) return
        val coins = event.coins
        if (event.reason == PurseChangeCause.GAIN_MOB_KILL && SlayerApi.isInCorrectArea) {
            tryAddItem(NeuInternalName.SKYBLOCK_COIN, coins.toInt(), command = false)
        }
        if (event.reason == PurseChangeCause.LOSE_SLAYER_QUEST_STARTED) {
            addSlayerCosts(coins)
        }
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!isEnabled()) return
        autoSlayerBankPattern.matchMatcher(event.message) {
            addSlayerCosts(-group("coins").formatDouble())
        }
    }

    @HandleEvent
    fun onSlayerChange(event: SlayerChangeEvent) {
        val newSlayer = event.newSlayer
        category = newSlayer.removeColor()
        baseSlayerType = category.substringBeforeLast(" ")
        getTracker()?.update()
    }

    private fun getTracker(): SkyHanniItemTracker<Data>? {
        if (category == "") return null

        return trackers.getOrPut(category) {
            val getStorage: (ProfileSpecificStorage) -> Data = {
                it.slayerProfitData.getOrPut(
                    category,
                ) { Data() }
            }
            SkyHanniItemTracker("$categoryName Profit Tracker", { Data() }, getStorage) { drawDisplay(it) }
        }
    }

    @HandleEvent
    fun onQuestComplete(event: SlayerQuestCompleteEvent) {
        getTracker()?.modify {
            it.slayerCompletedCount++
        }
    }

    @HandleEvent
    fun onItemAdd(event: ItemAddEvent) {
        // TODO remove is config enabled check for tracker
        if (!isEnabled()) return
        if (!SlayerApi.isInCorrectArea) return
        if (!SlayerApi.hasActiveSlayerQuest()) return

        tryAddItem(event.internalName, event.amount, event.source == ItemAddManager.Source.COMMAND)
    }

    private fun tryAddItem(internalName: NeuInternalName, amount: Int, command: Boolean) {
        if (!isAllowedItem(internalName) && internalName != NeuInternalName.SKYBLOCK_COIN) {
            ChatUtils.debug("Ignored non-slayer item pickup: '$internalName' '$category'")
            return
        }

        getTracker()?.addItem(internalName, amount, command)
    }

    private fun isAllowedItem(internalName: NeuInternalName): Boolean {
        val allowedList = allowedItems[baseSlayerType] ?: return false
        return internalName in allowedList
    }

    private fun drawDisplay(data: Data) = buildList<Searchable> {
        val tracker = getTracker() ?: return@buildList
        addSearchString("§e§l$categoryName Profit Tracker")

        var profit = tracker.drawItems(data, { true }, this)
        val slayerSpawnCost = data.slayerSpawnCost
        val slayerSpawnCostFormat = slayerSpawnCost.shortFormat()
        add(
            Renderable.hoverTips(
                " §7Slayer Spawn Costs: §c$slayerSpawnCostFormat",
                listOf("§7You paid §c$slayerSpawnCostFormat §7in total", "§7for starting the slayer quests."),
            ).toSearchable(),
        )
        profit += slayerSpawnCost

        val slayerCompletedCount = data.slayerCompletedCount.addSeparators()
        add(
            Renderable.hoverTips(
                "§7Bosses killed: §e$slayerCompletedCount",
                listOf(
                    "§7You killed the $categoryName boss",
                    "§e$slayerCompletedCount §7times.",
                ),
            ).toSearchable(),
        )

        add(tracker.addTotalProfit(profit, data.slayerCompletedCount, "boss"))

        tracker.addPriceFromButton(this)
    }

    // TODO reintroduce this? whats going on?
    val coinFormat: (ItemTrackerData.TrackedItem) -> Pair<String, List<String>> = { item ->
        val mobKillCoinsFormat = item.totalAmount.shortFormat()
        val text = " §6Mob kill coins§7: §6$mobKillCoinsFormat"
        val lore = listOf(
            "§7Killing mobs gives you coins (more with scavenger)",
            "§7You got §e$mobKillCoinsFormat §7coins in total this way",
        )

        text to lore
    }

    init {
        // Can not use tracker.initRenderer(), since we have multiple tracker instances in use
        RenderDisplayHelper(
            outsideInventory = true,
            inOwnInventory = true,
            condition = { shouldShowDisplay() },
            onRender = {
                getTracker()?.renderDisplay(config.pos)
            },
        )
    }

    private fun shouldShowDisplay(): Boolean {
        if (!isEnabled()) return false
        if (!SlayerApi.isInCorrectArea) return false

        return true
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.transform(10, "#profile.slayerProfitData") { old ->
            for (data in old.asJsonObject.entrySet().map { it.value.asJsonObject }) {
                val items = data.get("items").asJsonObject
                for (item in items.entrySet().map { it.value.asJsonObject }) {
                    val oldValue = item.get("timesDropped")
                    item.add("timesGained", oldValue)
                }

                val coinAmount = data.get("mobKillCoins")
                val coins = JsonObject()
                coins.add("internalName", JsonPrimitive("SKYBLOCK_COIN"))
                coins.add("timesDropped", JsonPrimitive(1))
                coins.add("totalAmount", coinAmount)
                items.add("SKYBLOCK_COIN", coins)
            }

            old
        }
    }

    fun isEnabled() = LorenzUtils.inSkyBlock && config.enabled

    fun resetCommand() {
        if (category == "") {
            ChatUtils.userError(
                "No current slayer data found! " +
                    "§eGo to a slayer area and start the specific slayer type you want to reset the data of.",
            )
            return
        }

        getTracker()?.resetCommand()
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.register("shresetslayerprofits") {
            description = "Resets the total slayer profit for the current slayer type"
            category = CommandCategory.USERS_RESET
            callback { resetCommand() }
        }
    }
}
