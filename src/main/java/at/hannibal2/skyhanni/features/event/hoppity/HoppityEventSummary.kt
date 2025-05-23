package at.hannibal2.skyhanni.features.event.hoppity

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigManager
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.features.event.hoppity.HoppityEventSummaryConfig.HoppityStat
import at.hannibal2.skyhanni.config.features.event.hoppity.HoppityEventSummaryLiveDisplayConfig.HoppityDateTimeDisplayType.CURRENT
import at.hannibal2.skyhanni.config.features.event.hoppity.HoppityEventSummaryLiveDisplayConfig.HoppityDateTimeDisplayType.NEXT_EVENT
import at.hannibal2.skyhanni.config.features.event.hoppity.HoppityEventSummaryLiveDisplayConfig.HoppityDateTimeDisplayType.PAST_EVENTS
import at.hannibal2.skyhanni.config.features.event.hoppity.HoppityEventSummaryLiveDisplayConfig.HoppityDateTimeFormat.RELATIVE
import at.hannibal2.skyhanni.config.features.event.hoppity.HoppityEventSummaryLiveDisplayConfig.HoppityLiveDisplayInventoryType
import at.hannibal2.skyhanni.config.storage.ProfileSpecificStorage.HoppityEventStats
import at.hannibal2.skyhanni.config.storage.ProfileSpecificStorage.HoppityEventStats.Companion.LeaderboardPosition
import at.hannibal2.skyhanni.config.storage.ProfileSpecificStorage.HoppityEventStats.Companion.RabbitData
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.data.jsonobjects.repo.HoppityEggLocationsJson
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.ProfileJoinEvent
import at.hannibal2.skyhanni.events.RepositoryReloadEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.hoppity.RabbitFoundEvent
import at.hannibal2.skyhanni.events.minecraft.KeyPressEvent
import at.hannibal2.skyhanni.features.event.hoppity.HoppityApi.getEventEndMark
import at.hannibal2.skyhanni.features.event.hoppity.HoppityApi.getEventStartMark
import at.hannibal2.skyhanni.features.event.hoppity.HoppityRabbitTheFishChecker.mealEggInventoryPattern
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.CFApi
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.CFApi.partyModeReplace
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.CFShopPrice.menuNamePattern
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ConditionalUtils.afterChange
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.LorenzRarity
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkyBlockTime
import at.hannibal2.skyhanni.utils.SkyBlockTime.Companion.SKYBLOCK_DAY_MILLIS
import at.hannibal2.skyhanni.utils.SkyBlockTime.Companion.SKYBLOCK_HOUR_MILLIS
import at.hannibal2.skyhanni.utils.SkyblockSeason
import at.hannibal2.skyhanni.utils.StringUtils
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.TimeUtils.getCountdownFormat
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.addOrPut
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.sumAllValues
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addString
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.RenderableUtils.addCenteredString
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.gui.inventory.GuiInventory
import org.lwjgl.input.Keyboard
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// TODO: Split into two classes, one for event summary in general, and one for live display
@SkyHanniModule
@Suppress("LargeClass")
object HoppityEventSummary {
    /**
     * REGEX-TEST: §d§lHOPPITY'S HUNT §r§7You found §r§cRabbit the Fish§r§7!
     */
    private val rabbitTheFishPattern by CFApi.patternGroup.pattern(
        "rabbit.thefish",
        "(?:§.)*HOPPITY'S HUNT (?:§.)*You found (?:§.)*Rabbit the Fish(?:§.)*!.*",
    )

    /**
     * REGEX-TEST: Hoppity's Collection
     * REGEX-TEST: (1/2) Hoppity's Collection
     * REGEX-TEST: Chocolate Factory Milestones
     * REGEX-TEST: Chocolate Shop Milestones
     */
    private val miscCfInventoryPatterns by CFApi.patternGroup.pattern(
        "cf.inventory",
        "(?:\\(\\d*\\/\\d*\\) )?Hoppity's Collection|Chocolate (?:Factory|Shop) Milestones|Rabbit Hitman",
    )

    private const val LINE_HEADER = "    "
    private val SEPARATOR = "§d§l${"▬".repeat(64)}"
    private val config get() = SkyHanniMod.feature.event.hoppityEggs
    private val statDisplayList get() = config.eventSummary.statDisplayList.get()
    private val storage get() = ProfileStorageData.profileSpecific
    private val liveDisplayConfig get() = config.eventSummary.liveDisplay
    private val updateCfConfig get() = config.eventSummary.cfReminder
    private val currentSbYear get() = SkyBlockTime.now().year

    private var allowedHoppityIslands: Set<IslandType> = setOf()
    private var displayCardRenderables = listOf<Renderable>()
    private var lastKnownStatHash = 0
    private var lastKnownInInvState = false
    private var lastAddedCfMillis: SimpleTimeMark = SimpleTimeMark.farPast()
    private var lastSentCfUpdateMessage: SimpleTimeMark = SimpleTimeMark.farPast()
    private var lastToggleMark: SimpleTimeMark = SimpleTimeMark.farPast()
    private var currentEventEndMark: SimpleTimeMark = SimpleTimeMark.farPast()
    private var lastSnapshotServer: String? = null
    private var statYear: Int = currentSbYear
    private var currentTimerActive = false
    private var onHoppityIsland = false

    private fun inMatchingInventory(): Boolean {
        val setting = liveDisplayConfig.specificInventories
        val currentScreen = Minecraft.getMinecraft().currentScreen ?: return HoppityLiveDisplayInventoryType.NO_INVENTORY in setting

        // Get the inventory name and check if it matches any of the specific inventories
        val inventoryName = InventoryUtils.openInventoryName()

        val inChocolateFactory =
            CFApi.inChocolateFactory ||
                menuNamePattern.matches(inventoryName) ||
                miscCfInventoryPatterns.matches(inventoryName)

        return if (currentScreen is GuiInventory) {
            HoppityLiveDisplayInventoryType.OWN_INVENTORY in setting
        } else if (inChocolateFactory) {
            HoppityLiveDisplayInventoryType.CHOCOLATE_FACTORY in setting
        } else if (inventoryName == "Hoppity") {
            HoppityLiveDisplayInventoryType.HOPPITY in setting
        } else if (mealEggInventoryPattern.matches(inventoryName)) {
            HoppityLiveDisplayInventoryType.MEAL_EGGS in setting
        } else false
    }

    private fun liveDisplayEnabled(): Boolean {
        val storage = storage ?: return false
        val isToggledOff = storage.hoppityStatLiveDisplayToggledOff
        val isEnabled = liveDisplayConfig.enabled
        val isIslandEnabled = !liveDisplayConfig.onlyHoppityIslands || onHoppityIsland
        val isEventEnabled = !liveDisplayConfig.onlyDuringEvent || HoppityApi.isHoppityEvent()
        val isEggLocatorEnabled = !liveDisplayConfig.mustHoldEggLocator || InventoryUtils.itemInHandId == HoppityEggLocator.locatorItem
        val isInventoryEnabled = liveDisplayConfig.specificInventories.isEmpty() || inMatchingInventory()

        return LorenzUtils.inSkyBlock &&
            !isToggledOff &&
            isEnabled &&
            isIslandEnabled &&
            isEventEnabled &&
            isEggLocatorEnabled &&
            isInventoryEnabled
    }

    private fun MutableList<StatString>.chromafyLiveDisplay(): MutableList<StatString> =
        if (CFApi.config.partyMode.get()) map { it.copy(string = it.string.partyModeReplace()) }.toMutableList()
        else this

    private data class StatString(var string: String, val headed: Boolean = true)

    private fun MutableList<StatString>.addStr(string: String, headed: Boolean = true) = this.add(StatString(string, headed))

    private fun MutableList<StatString>.addEmptyLine() = this.add(StatString("", false))

    @HandleEvent
    fun onRepoReload(event: RepositoryReloadEvent) {
        allowedHoppityIslands = event.getConstant<HoppityEggLocationsJson>("HoppityEggLocations").apiEggLocations.keys.toSet()
    }

    @HandleEvent
    fun onIslandChange(event: IslandChangeEvent) {
        onHoppityIsland = LorenzUtils.inSkyBlock && allowedHoppityIslands.any { it.isInIsland() }
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.register("shresethoppityeventstats") {
            description = "Reset Hoppity Event stats for all years."
            category = CommandCategory.USERS_RESET
            callback { handleResetRequest(it) }
        }
    }

    @HandleEvent
    fun onRabbitFound(event: RabbitFoundEvent) {
        val stats = getYearStats() ?: return
        if (!HoppityApi.isHoppityEvent()) {
            DelayedRun.runDelayed(5.seconds) {
                stats.typeCountsSince = HoppityCollectionStats.getTypeCountSnapshot()
            }
            return
        }

        stats.mealsFound.addOrPut(event.eggType, 1)
        val rarity = HoppityApi.rarityByRabbit(event.rabbitName) ?: return
        val rarityMap = stats.rabbitsFound.getOrPut(rarity) { RabbitData() }
        if (event.duplicate) rarityMap.dupes++
        else rarityMap.uniques++
        if (event.chocGained > 0) stats.dupeChocolateGained += event.chocGained

        // Make sure we account for event priority, since HoppityCollectionStats has a statically set lower priority
        DelayedRun.runDelayed(5.seconds) {
            stats.typeCountSnapshot = HoppityCollectionStats.getTypeCountSnapshot()
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        reCheckInventoryState()
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onInventoryClose(event: InventoryCloseEvent) {
        reCheckInventoryState()
    }

    @HandleEvent
    fun onKeyPress(event: KeyPressEvent) {
        reCheckInventoryState()
        if (!liveDisplayConfig.enabled) return
        if (liveDisplayConfig.toggleKeybind == Keyboard.KEY_NONE || liveDisplayConfig.toggleKeybind != event.keyCode) return
        // Only toggle from inventory if the user is in the Chocolate Factory
        if (Minecraft.getMinecraft().currentScreen != null && !CFApi.inChocolateFactory) return
        if (lastToggleMark.passedSince() < 250.milliseconds) return
        val storage = storage ?: return
        storage.hoppityStatLiveDisplayToggledOff = !storage.hoppityStatLiveDisplayToggledOff
        lastToggleMark = SimpleTimeMark.now()
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!HoppityApi.isHoppityEvent()) return
        val stats = getYearStats() ?: return

        if (rabbitTheFishPattern.matches(event.message)) {
            stats.rabbitTheFishFinds++
        }
    }

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent) {
        if (!liveDisplayEnabled()) return

        val stats = getYearStats(statYear) ?: return
        // Calculate a 'hash' of the stats to determine if they have changed
        val statsHash = stats.hashCode()
        if (statsHash != lastKnownStatHash) {
            lastKnownStatHash = statsHash
            displayCardRenderables = buildDisplayRenderables(stats, statYear)
        }

        config.eventSummary.liveDisplayPosition.renderRenderables(
            displayCardRenderables,
            posLabel = "Hoppity's Hunt Stats",
        )
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(64, "event.hoppity.preventMissingFish", "event.hoppity.preventMissingRabbitTheFish")
        event.move(65, "hoppityStatLiveDisplayToggled", "hoppityStatLiveDisplayToggledOff")

        event.transform(79, "#profile.hoppityEventStats") { element ->
            element.asJsonObject.apply {
                val empty = ConfigManager.gson.toJsonTree(RabbitData.EMPTY)
                entrySet().forEach { (_, stats) ->
                    stats.asJsonObject.add("typeCountSnapshot", empty)
                    stats.asJsonObject.add("typeCountsSince", empty)
                }
            }
        }
    }

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        config.eventSummary.statDisplayList.afterChange {
            lastKnownStatHash = 0
        }
        CFApi.config.partyMode.afterChange {
            lastKnownStatHash = 0
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onSecondPassed(event: SecondPassedEvent) {
        checkStatsTypeCountInit()
        checkLbUpdateWarning()
        reCheckInventoryState()
        checkEnded()
        recheckHashClear(event)
        if (!HoppityApi.isHoppityEvent()) return
        checkAddCfTime()
    }

    @HandleEvent
    fun onProfileJoin(event: ProfileJoinEvent) {
        lastSnapshotServer = null
        checkEnded()
    }

    private fun isInInventory(): Boolean =
        Minecraft.getMinecraft().currentScreen is GuiInventory || Minecraft.getMinecraft().currentScreen is GuiChest

    private fun recheckHashClear(event: SecondPassedEvent) {
        if (!currentTimerActive) return
        // Refresh every 5 seconds
        if (!event.repeatSeconds(5)) return
        lastKnownStatHash = 0
    }

    private fun reCheckInventoryState() {
        if (isInInventory() != lastKnownInInvState) {
            lastKnownInInvState = !lastKnownInInvState
            lastKnownStatHash = 0
        }
    }

    private fun handleResetRequest(args: Array<String>) {
        // Send a confirmation message confirming this is destructive
        if (args.any { it.equals("confirm", ignoreCase = true) }) {
            resetStats()
            return
        }
        ChatUtils.clickableChat(
            "§c§lWARNING! §r§7This will reset all Hoppity Event stats for all years. " +
                "Click here or type §c/shresethoppityeventstats confirm §7to confirm.",
            onClick = ::resetStats,
        )
    }

    private fun resetStats() {
        storage?.let {
            it.hoppityEventStats.clear()
            ChatUtils.chat("Hoppity Event stats have been reset.")
        } ?: ErrorManager.skyHanniError("Could not reset Hoppity Event stats.")
    }

    private fun checkStatsTypeCountInit() {
        val stats = getYearStats() ?: return
        for (i in 0..2) {
            if (stats.typeCountSnapshot?.getByIndex(i) != 0) return
        }
        stats.typeCountSnapshot = HoppityCollectionStats.getTypeCountSnapshot()
    }

    private fun checkLbUpdateWarning() {
        if (!LorenzUtils.inSkyBlock || !HoppityApi.isHoppityEvent() || !updateCfConfig.enabled) return

        // Only run if the user has leaderboard stats enabled
        if (!statDisplayList.contains(HoppityStat.LEADERBOARD_CHANGE)) return

        // If we're only showing the live display during the last {X} hours of the hunt,
        // check if we're in that time frame
        val showLastXHours = updateCfConfig.showForLastXHours.takeIf { it > 0 } ?: return

        // Initialize the current event end mark if it hasn't been set yet
        if (currentEventEndMark.isFarPast()) currentEventEndMark = getEventEndMark(currentSbYear)
        if (showLastXHours < 30 && currentEventEndMark.timeUntil() >= showLastXHours.hours) return

        // If it's been less than {config} minutes since the last warning message, don't send another
        lastSentCfUpdateMessage.takeIfInitialized()?.let {
            if (it.passedSince() < updateCfConfig.reminderInterval.minutes) return
        }

        // If it's been more than {config} since the last leaderboard update, send a message
        val stats = getYearStats() ?: return
        val lastLbUpdate = stats.lastLbUpdate.takeIfInitialized() ?: SimpleTimeMark.farPast()
        if (lastLbUpdate.passedSince() >= updateCfConfig.reminderInterval.minutes) {
            lastSentCfUpdateMessage = SimpleTimeMark.now()
            ChatUtils.chat(
                "§6§lReminder! §r§eSwitch to a new server and run §6/cf §eto " +
                    "update your leaderboard position in Hoppity Event stats.",
            )
        }
    }

    private fun buildDisplayRenderables(stats: HoppityEventStats?, statYear: Int): List<Renderable> = buildList {
        // Add title renderable with centered alignment
        currentTimerActive = true
        add(buildTitle(statYear))

        // Conditionally add year switcher renderable for inventory or chest screens
        if (isInInventory()) {
            buildYearSwitcherRenderables(statYear)?.let { yearSwitcher ->
                add(
                    Renderable.horizontalContainer(
                        yearSwitcher,
                        spacing = 5,
                        horizontalAlign = RenderUtils.HorizontalAlignment.CENTER,
                    ),
                )
            }
        }

        // Add card renderable based on stats availability
        val cardRenderable = if (stats == null) {
            Renderable.verticalContainer(
                mutableListOf(Renderable.string("§cNo stats found for Hunt #${getHoppityEventNumber(statYear)}.")),
            )
        } else {
            Renderable.verticalContainer(
                getStatsStrings(stats, statYear).map { Renderable.string(it.string) }.toMutableList(),
            )
        }
        add(cardRenderable)
    }

    private fun SimpleTimeMark.formatForHoppity(): Pair<String, Boolean> =
        if (SkyHanniMod.feature.event.hoppityEggs.eventSummary.liveDisplay.dateTimeFormat == RELATIVE)
            Pair(passedSince().absoluteValue.format(maxUnits = 2), false)
        else {
            val countDownFormat = toLocalDateTime().getCountdownFormat()
            Pair(formattedDate(countDownFormat), true)
        }

    private fun buildTitle(statYear: Int) = Renderable.verticalContainer(
        buildList {
            addString(
                when (statYear) {
                    Int.MAX_VALUE -> "§dHoppity's Hunt All-Time Stats"
                    else -> "§dHoppity's Hunt #${getHoppityEventNumber(statYear)} Stats"
                }.partyModeReplace(),
                horizontalAlign = RenderUtils.HorizontalAlignment.CENTER,
            )
            if (statYear == Int.MAX_VALUE) {
                val numberEvents = storage?.hoppityEventStats?.keys?.count { it <= currentSbYear } ?: 0
                addCenteredString(
                    "§7Compiled from §f$numberEvents §7events".partyModeReplace(),
                )
                return@buildList
            }

            val eventEnd = getEventEndMark(statYear)
            val isHoppity = HoppityApi.isHoppityEvent()

            val isCurrentEvent = isHoppity && statYear == currentSbYear
            val isPastEvent = statYear < currentSbYear || (statYear == currentSbYear && !isHoppity)

            val configMatches = when {
                isCurrentEvent -> liveDisplayConfig.dateTimeDisplay.contains(CURRENT)
                isPastEvent -> liveDisplayConfig.dateTimeDisplay.contains(PAST_EVENTS)
                else -> liveDisplayConfig.dateTimeDisplay.contains(NEXT_EVENT)
            }
            if (!configMatches) return@buildList

            val (timeMarkFormat, timeMarkAbs) = when {
                isCurrentEvent || isPastEvent -> eventEnd
                else -> getEventStartMark(statYear)
            }.formatForHoppity()

            val grammarFormat = when {
                isCurrentEvent -> if (timeMarkAbs) "Ends" else "Ends in"
                isPastEvent -> if (timeMarkAbs) "" else " ago"
                else -> if (timeMarkAbs) "Starts" else "Starts in"
            }

            addCenteredString(
                when {
                    isCurrentEvent -> "§7$grammarFormat §f$timeMarkFormat"
                    isPastEvent -> "§7Ended §f$timeMarkFormat$grammarFormat"
                    else -> "§7$grammarFormat §f$timeMarkFormat"
                }.partyModeReplace(),
            )
        },
        horizontalAlign = RenderUtils.HorizontalAlignment.CENTER,
    )

    private fun buildYearSwitcherRenderables(currentStatYear: Int): List<Renderable>? {
        val storage = storage ?: return null
        val statsStorage = storage.hoppityEventStats

        val isNextEventEnabled = liveDisplayConfig.dateTimeDisplay.contains(NEXT_EVENT)
        val isAllTimeEnabled = liveDisplayConfig.showAllTime

        val isAllTime = currentStatYear == Int.MAX_VALUE
        val nextYear = currentSbYear + 1
        val isAlreadyNextEvent = currentStatYear == nextYear
        val predecessorYear = statsStorage.keys.filter {
            it < currentStatYear &&
                (it != nextYear || isNextEventEnabled)
        }.maxOrNull()
        val successorYear =
            if (isAllTime) null
            else statsStorage.keys.filter { it in (currentStatYear + 1)..<nextYear }.minOrNull()

        val predecessorButton = predecessorYear?.let {
            buildStatYearSwitcher(
                "Hunt #${getHoppityEventNumber(it)}".toLeftButtonString(),
                it,
            )
        }

        val successorButton = successorYear?.let {
            buildStatYearSwitcher(
                "Hunt #${getHoppityEventNumber(it)}".toRightButtonString(),
                it,
            )
        }

        val nextEventButton = buildStatYearSwitcher(
            "Next Hunt".toRightButtonString(),
            nextYear,
        ).takeIf { isNextEventEnabled && !isAlreadyNextEvent && !isAllTime }

        val allTimeButton = buildStatYearSwitcher(
            "All-Time".toRightButtonString(),
            Int.MAX_VALUE,
        ).takeIf { isAllTimeEnabled && !isAllTime }

        val rightButton = successorButton
            ?: nextEventButton
            ?: allTimeButton

        return listOfNotNull(
            predecessorButton,
            rightButton,
        )
    }

    private fun buildStatYearSwitcher(text: String, year: Int) =
        Renderable.optionalLink(text, onLeftClick = { statYear = year })

    private fun String.toLeftButtonString() = "§d[ §r§f§l<- §r§7$this §r§d]".partyModeReplace()
    private fun String.toRightButtonString() = "§d[ §7$this §r§f§l-> §r§d]".partyModeReplace()

    private fun getUnsummarizedYearStats(): Map<Int, HoppityEventStats> =
        storage?.hoppityEventStats?.filterValues { !it.summarized }.orEmpty()

    private fun getYearStats(year: Int = currentSbYear): HoppityEventStats? =
        if (year == Int.MAX_VALUE) getAllTimeStats()
        else storage?.hoppityEventStats?.getOrPut(year) { HoppityEventStats(year) }

    private fun getAllTimeStats(): HoppityEventStats {
        val storageYears = storage?.hoppityEventStats?.keys ?: return HoppityEventStats()
        val allTimeStats = HoppityEventStats(storageYears)
        val statsStorage = storage?.hoppityEventStats ?: return allTimeStats
        statsStorage.values.forEach {
            allTimeStats += it
        }
        allTimeStats.initialLeaderboardPosition = statsStorage.values.firstOrNull {
            it.initialLeaderboardPosition.position != -1
        }?.initialLeaderboardPosition ?: LeaderboardPosition(-1, -1.0)
        allTimeStats.finalLeaderboardPosition = statsStorage.values.reversed().firstOrNull {
            it.finalLeaderboardPosition.position != -1
        }?.finalLeaderboardPosition ?: LeaderboardPosition(-1, -1.0)
        return allTimeStats
    }

    private fun checkAddCfTime() {
        if (!CFApi.inChocolateFactory) {
            lastAddedCfMillis = SimpleTimeMark.farPast()
            return
        }
        val stats = getYearStats() ?: return
        lastAddedCfMillis.takeIfInitialized()?.let {
            stats.millisInCf += it.passedSince()
        }
        lastAddedCfMillis = SimpleTimeMark.now()
    }

    private fun checkEnded() {
        if (!config.eventSummary.enabled) return
        if (SkyBlockTime.now().isSeasonBorder()) return

        getUnsummarizedYearStats().filter {
            it.key < currentSbYear || (it.key == currentSbYear && !SkyblockSeason.SPRING.isSeason()) &&
                (it.key != currentSbYear || !HoppityApi.isHoppityEvent()) // Secondary sanity check
        }.forEach { (year, stats) ->
            storage?.hoppityEventStats?.get(year)?.let {
                // Only send the message if we're going to be able to set the stats as summarized
                sendStatsMessage(stats, year)
                it.summarized = true
            }
        }
    }

    // First event was year 346 -> #1, 20th event was year 365, etc.
    private fun getHoppityEventNumber(skyblockYear: Int): Int = (skyblockYear - 345)

    private fun inSameServer(): Boolean {
        val serverId = HypixelData.serverId ?: return false
        val lastServer = lastSnapshotServer
        lastSnapshotServer = serverId
        return serverId == lastServer
    }

    fun updateCfPosition(position: Int?, percentile: Double?) {
        if (!HoppityApi.isHoppityEvent() || inSameServer() || position == null || percentile == null) return
        val stats = getYearStats() ?: return
        val snapshot = LeaderboardPosition(position, percentile)
        stats.initialLeaderboardPosition = stats.initialLeaderboardPosition.takeIf { it.position != -1 } ?: snapshot
        stats.finalLeaderboardPosition = snapshot
        stats.lastLbUpdate = SimpleTimeMark.now()
    }

    fun addStrayCaught(rarity: LorenzRarity, chocGained: Long) {
        if (!HoppityApi.isHoppityEvent()) return
        val stats = getYearStats() ?: return
        val rarityMap = stats.rabbitsFound.getOrPut(rarity) { RabbitData() }
        rarityMap.strays++
        stats.strayChocolateGained += chocGained
    }

    private fun StringBuilder.appendHeadedLine(line: String) {
        appendLine("$LINE_HEADER$line")
    }

    private fun MutableList<StatString>.addExtraChocFormatLine(chocGained: Long) {
        if (chocGained <= 0) return
        val chocFormatLine = buildString {
            append(" §6+${chocGained.addSeparators()} Chocolate")
            if (SkyHanniMod.feature.inventory.chocolateFactory.showDuplicateTime) {
                val timeFormatted = CFApi.timeUntilNeed(chocGained).format(maxUnits = 2)
                append(" §7(§a+§b$timeFormatted§7)")
            }
        }
        add(StatString(chocFormatLine))
    }

    private fun getPreviousStats(year: Int): HoppityEventStats? =
        storage?.hoppityEventStats?.get(year - 1)

    private fun HoppityEventStats.getMilestoneCount(): Int =
        (mealsFound[HoppityEggType.CHOCOLATE_FACTORY_MILESTONE] ?: 0) +
            (mealsFound[HoppityEggType.CHOCOLATE_SHOP_MILESTONE] ?: 0)

    private fun HoppityEventStats.getBoughtCount(): Int =
        (mealsFound[HoppityEggType.BOUGHT] ?: 0) + (mealsFound[HoppityEggType.BOUGHT_ABIPHONE] ?: 0)

    private fun HoppityEventStats.getMealEggCount(): Int =
        mealsFound.filterKeys { it in HoppityEggType.resettingEntries }.sumAllValues().toInt()

    private val summaryOperationList by lazy {
        buildMap<HoppityStat, (statList: MutableList<StatString>, stats: HoppityEventStats, year: Int) -> Unit> {
            put(HoppityStat.MEAL_EGGS_FOUND) { statList, stats, year ->
                stats.getMealEggCount().takeIf { it > 0 }?.let {
                    val spawnedMealEggs = getSpawnedEggCount(year)
                    val eggFormat = StringUtils.pluralize(it, "Egg")
                    val amount = "${it.addSeparators()}§7/§a${spawnedMealEggs.addSeparators()}"
                    statList.addStr("§7You found §b$amount §6Chocolate Meal $eggFormat§7.")
                }
            }

            put(HoppityStat.HITMAN_EGGS) { statList, stats, year ->
                val spawnedMealEggs = getSpawnedEggCount(year)
                val trueMissed = spawnedMealEggs - stats.getMealEggCount()
                // We only want to show events after hitman was added (Hunt #41)
                val missedMealEggs = if (year < 41) return@put
                else if (year == Int.MAX_VALUE) {
                    stats.containingYears.mapNotNull { containingYear ->
                        if (containingYear < 41) return@mapNotNull null
                        val yearMealEggs = getYearStats(containingYear)?.getMealEggCount() ?: 0

                        getSpawnedEggCount(containingYear) - yearMealEggs
                    }.sum()
                } else trueMissed

                stats.mealsFound[HoppityEggType.HITMAN]?.let {
                    val eggFormat = StringUtils.pluralize(it, "Egg")
                    val divisorFormat = "§b${it.addSeparators()}§7/§a${missedMealEggs.addSeparators()}"
                    statList.addStr("§7You recovered $divisorFormat §7missed §6Meal $eggFormat §7from §cRabbit Hitman§7.")
                }
            }

            put(HoppityStat.HOPPITY_RABBITS_BOUGHT) { statList, stats, _ ->
                stats.getBoughtCount().takeIf { it > 0 }?.let {
                    val rabbitFormat = StringUtils.pluralize(it, "Rabbit")
                    statList.addStr("§7You bought §b${it.addSeparators()} §f$rabbitFormat §7from §aHoppity§7.")
                }
            }

            put(HoppityStat.SIDE_DISH_EGGS) { statList, stats, _ ->
                stats.mealsFound[HoppityEggType.SIDE_DISH]?.let {
                    val eggFormat = StringUtils.pluralize(it, "Egg")
                    statList.addStr("§7You found §b${it.addSeparators()} §6§lSide Dish $eggFormat §r§7in the §6Chocolate Factory§7.")
                }
            }

            put(HoppityStat.MILESTONE_RABBITS) { statList, stats, _ ->
                stats.getMilestoneCount().takeIf { it > 0 }?.let {
                    val rabbitFormat = StringUtils.pluralize(it, "Rabbit")
                    statList.addStr("§7You claimed §b$it §6§lMilestone $rabbitFormat§7.")
                }
            }

            put(HoppityStat.NEW_RABBITS) { statList, stats, year ->
                getRabbitsFormat(
                    rarityMap = stats.rabbitsFound.mapValues { m -> m.value.uniques },
                    name = "Unique",
                    countTriple = stats.getPairTriple(year, 0),
                ).forEach {
                    statList.addStr(it)
                }
            }

            put(HoppityStat.DUPLICATE_RABBITS) { statList, stats, year ->
                getRabbitsFormat(
                    rarityMap = stats.rabbitsFound.mapValues { m -> m.value.dupes },
                    name = "Duplicate",
                    countTriple = stats.getPairTriple(year, 1),
                ).forEach {
                    statList.addStr(it)
                }
                statList.addExtraChocFormatLine(stats.dupeChocolateGained)
            }

            put(HoppityStat.STRAY_RABBITS) { statList, stats, year ->
                getRabbitsFormat(
                    rarityMap = stats.rabbitsFound.mapValues { m -> m.value.strays },
                    name = "Stray",
                    countTriple = stats.getPairTriple(year, 2),
                ).forEach {
                    statList.addStr(it)
                }
                statList.addExtraChocFormatLine(stats.strayChocolateGained)
            }

            put(HoppityStat.TIME_IN_CF) { statList, stats, _ ->
                stats.millisInCf.takeIf { it > 0.seconds }?.let {
                    val cfTimeFormat = it.format(maxUnits = 2)
                    statList.addStr("§7You spent §b$cfTimeFormat §7in the §6Chocolate Factory§7.")
                }
            }

            put(HoppityStat.RABBIT_THE_FISH_FINDS) { statList, stats, _ ->
                stats.rabbitTheFishFinds.takeIf { it > 0 }?.let {
                    val timesFormat = StringUtils.pluralize(it, "time")
                    val eggsFormat = StringUtils.pluralize(it, "Egg")
                    statList.addStr("§7You found §cRabbit the Fish §7in Meal $eggsFormat §b$it §7$timesFormat.")
                }
            }

            put(HoppityStat.LEADERBOARD_CHANGE) { statList, stats, _ ->
                val initial = stats.initialLeaderboardPosition
                val final = stats.finalLeaderboardPosition
                if (
                    initial.position == -1 || final.position == -1 ||
                    initial.percentile == -1.0 || final.percentile == -1.0 ||
                    initial.position == final.position
                ) return@put
                getFullLeaderboardMessage(initial, final).forEach {
                    statList.addStr(it)
                }
            }

            put(HoppityStat.EMPTY_1) { sl, _, _ -> sl.addEmptyLine() }
            put(HoppityStat.EMPTY_2) { sl, _, _ -> sl.addEmptyLine() }
            put(HoppityStat.EMPTY_3) { sl, _, _ -> sl.addEmptyLine() }
            put(HoppityStat.EMPTY_4) { sl, _, _ -> sl.addEmptyLine() }
        }
    }

    private fun getFullLeaderboardMessage(initial: LeaderboardPosition, final: LeaderboardPosition) = buildList {
        add("§7Leaderboard: ${getPrimaryLbString(initial, final)}")
        add(getSecondaryLbLine(initial, final))
    }

    private fun getPrimaryLbString(initial: LeaderboardPosition, final: LeaderboardPosition): String {
        val iPo = initial.position
        val fPo = final.position
        return "§b#${iPo.addSeparators()} §c-> §b#${fPo.addSeparators()}"
    }

    private fun getSecondaryLbLine(initial: LeaderboardPosition, final: LeaderboardPosition): String {
        val initPosition = initial.position
        val finalPosition = final.position
        val diffPosition = finalPosition - initPosition
        val initialPercentile = initial.percentile
        val finalPercentile = final.percentile
        val diffPercentile = finalPercentile - initialPercentile
        val preambleFormat = if (initPosition > finalPosition) "§a+" else "§c"

        return buildString {
            append(" §7($preambleFormat${(-1 * diffPosition).addSeparators()} ${StringUtils.pluralize(diffPosition, "spot")}§7)")
            if (diffPercentile != 0.0) append(" §7Top §a$initialPercentile% §c-> §7Top §a$finalPercentile%")
            else append(" §7Top §a$initialPercentile%")
        }
    }

    private fun getStatsStrings(stats: HoppityEventStats, eventYear: Int?): MutableList<StatString> {
        if (eventYear == null) return mutableListOf()
        val statList = mutableListOf<StatString>()

        // Various stats from config
        statDisplayList.forEach {
            summaryOperationList[it]?.invoke(statList, stats, eventYear)
        }

        // Remove any consecutive empty lines
        val iterator = statList.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (next.string.isEmpty() && iterator.hasNext()) {
                val nextNext = iterator.next()
                if (nextNext.string.isEmpty()) iterator.remove()
            }
        }

        // If no stats are found, or the stats are only newlines, display a message
        if (statList.all { it.string.isBlank() } || statList.isEmpty()) {
            statList.clear()
            statList.addEmptyLine()
            statList.addStr("§c§lNothing to show!")
            val isCurrentEvent = HoppityApi.isHoppityEvent() && eventYear == currentSbYear
            val timeFormat = if (isCurrentEvent) "§c§l§oRIGHT NOW§c§o" else "in the future"
            statList.addStr("§c§oFind some eggs $timeFormat!")
        }

        return statList.chromafyLiveDisplay()
    }

    private fun sendStatsMessage(stats: HoppityEventStats, eventYear: Int?) {
        if (eventYear == null) return

        val statsString = buildString {
            getStatsStrings(stats, eventYear).forEach {
                if (it.headed) appendHeadedLine(it.string)
                else appendLine(it.string)
            }
        }

        val summary = buildString {
            appendLine(SEPARATOR)

            // Header
            appendLine("${" ".repeat(26)}§d§lHoppity's Hunt #${getHoppityEventNumber(eventYear)} Stats")
            appendLine()

            // Append stats
            append(statsString)

            append(SEPARATOR)
        }

        ChatUtils.chat(summary, prefix = false)
    }

    private fun getAllTimeSpawnedEggCount(): Int {
        val negativeOffset = if (HoppityApi.isHoppityEvent()) 1 else 0
        val completedEvents = storage?.hoppityEventStats?.size?.minus(negativeOffset) ?: 0
        val spawnedThisEvent = if (HoppityApi.isHoppityEvent()) getSpawnedEggCount(currentSbYear) else 0
        return completedEvents * 279 + spawnedThisEvent
    }

    private fun getSpawnedEggCount(year: Int): Int {
        if (year == Int.MAX_VALUE) return getAllTimeSpawnedEggCount()
        val milliDifference = SkyBlockTime.now().toMillis() - SkyBlockTime.fromSBYear(year).toMillis()
        val pastEvent = milliDifference > SkyBlockTime.SKYBLOCK_SEASON_MILLIS
        // Calculate total eggs from complete days and incomplete day periods
        val previousEggs = if (pastEvent) 279 else (milliDifference / SKYBLOCK_DAY_MILLIS).toInt() * 3
        val currentEggs = when {
            pastEvent -> 0
            // Add eggs for the current day based on time of day
            milliDifference % SKYBLOCK_DAY_MILLIS >= SKYBLOCK_HOUR_MILLIS * 21 -> 3 // Dinner egg, 9 PM
            milliDifference % SKYBLOCK_DAY_MILLIS >= SKYBLOCK_HOUR_MILLIS * 14 -> 2 // Lunch egg, 2 PM
            milliDifference % SKYBLOCK_DAY_MILLIS >= SKYBLOCK_HOUR_MILLIS * 7 -> 1 // Breakfast egg, 7 AM
            else -> 0
        }
        return previousEggs + currentEggs
    }

    private fun HoppityEventStats.getPairTriple(
        year: Int,
        index: Int,
    ): Triple<Int, Int, Int> = getPreviousStats(year)?.let {
        val currentValue = this.typeCountSnapshot?.getByIndex(index) ?: 0
        val previousValue = it.typeCountSnapshot?.getByIndex(index) ?: 0
        val sinceValue = ((it.typeCountsSince?.getByIndex(index) ?: previousValue) - previousValue)
        val validData = previousValue > 0 && previousValue != currentValue && sinceValue > 0
        Triple(
            if (validData) previousValue else 0,
            if (validData) currentValue else 0,
            if (validData) sinceValue else 0,
        )
    } ?: Triple(0, 0, 0)

    fun getRabbitsFormat(
        rarityMap: Map<LorenzRarity, Int>,
        name: String,
        countTriple: Triple<Int, Int, Int> = Triple(0, 0, 0),
    ): List<String> {
        val (prevCount, currCount, sinceCount) = countTriple
        val rabbitsSum = rarityMap.values.sum()
        if (rabbitsSum == 0) return emptyList()

        val sinceFormat = if (sinceCount > 0) " §8+$sinceCount§7" else ""
        val countFormat = if (config.eventSummary.showCountDiff && prevCount != 0 && currCount != 0) {
            " §7($prevCount$sinceFormat -> $currCount)"
        } else ""

        return mutableListOf(
            "§7$name Rabbits: §f${rabbitsSum.addSeparators()}$countFormat",
            HoppityApi.hoppityRarities.joinToString(" §7-") {
                " ${it.chatColorCode}${(rarityMap[it] ?: 0).addSeparators()}"
            },
        )
    }
}
