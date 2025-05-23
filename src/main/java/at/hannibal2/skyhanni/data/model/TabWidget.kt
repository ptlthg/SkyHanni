package at.hannibal2.skyhanni.data.model

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.RepositoryReloadEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.TabListUpdateEvent
import at.hannibal2.skyhanni.events.WidgetUpdateEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ConditionalUtils.transformIf
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.TabListData
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.editCopy
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.time.Duration.Companion.seconds

private val repoGroup by RepoPattern.exclusiveGroup("tab.widget.enum")

/**
 * This class defines various widgets within the tab list, specifically focusing on the reading of the values.
 * Each enum value corresponds to a distinct widget in the tab list, ensuring no overlap between them.
 * The general info widget is broken up into multiple smaller ones.
 * The class facilitates access to the lines associated with each widget and triggers events when a widget undergoes changes or becomes invisible.
 */
enum class TabWidget(
    pattern0: String,
) {
    PLAYER_LIST(
        // language=RegExp
        "(?:§.)*Players (?:§.)*\\(\\d+\\)",
    ),

    /** This line holds no information, only here because every widget must be present */
    INFO(
        // language=RegExp
        "(?:§.)*Info",
    ),
    AREA(
        // language=RegExp
        "(?:§.)*(Area|Dungeon): (?:§.)*(?<island>.*)",
    ),
    SERVER(
        // language=RegExp
        "Server: (?:§.)*(?<serverid>.*)",
    ),
    GEMS(
        // language=RegExp
        "Gems: (?:§.)*(?<gems>.*)",
    ),
    FAIRY_SOULS(
        // language=RegExp
        "Fairy Souls: (?:§.)*(?<got>\\d+)(?:§.)*\\/(?:§.)*(?<max>\\d+)",
    ),
    PROFILE(
        // language=RegExp
        "(?:§.)+Profile: §r§a(?<profile>[\\w\\s]+[^ §]).*",
    ),
    SB_LEVEL(
        // language=RegExp
        "SB Level(?:§.)*: (?:§.)*\\[(?:§.)*(?<level>\\d+)(?:§.)*\\] (?:§.)*(?<xp>\\d+).*",
    ),
    BANK(
        // language=RegExp
        "Bank: (?:§.)*(?<amount>[^§]+)(?:(?:§.)* \\/ (?:§.)*(?<personal>.*))?",
    ),
    INTEREST(
        // language=RegExp
        "Interest: (?:§.)*(?<time>[^§]+)(?:§.)*( \\((?<amount>[^)]+)\\))?",
    ),
    SOULFLOW(
        // language=RegExp
        "Soulflow: (?:§.)*(?<amount>.*)",
    ),
    PET(
        // language=RegExp
        "(?:§.)*Pet:",
    ),
    PET_TRAINING(
        // language=RegExp
        "(?:§.)*Pet Training:",
    ),
    PET_SITTER(
        // language=RegExp
        "Kat: .*",
    ),
    FIRE_SALE(
        // language=RegExp
        "(?:§.)*Fire Sales: .*",
    ),
    ELECTION(
        // language=RegExp
        "(?:§.)*Election: (?:§.)*(?<time>.*)",
    ),
    EVENT(
        // language=RegExp
        "(?:§.)*Event: (?<color>(?:§.)*)(?<event>.*)",
    ),
    SKILLS(
        // language=RegExp
        "(?:§.)*Skills: ?(?:§.)*(?<avg>[\\d.]*)",
    ),
    STATS(
        // language=RegExp
        "(?:§.)*Stats:",
    ),
    GUESTS(
        // language=RegExp
        "(?:§.)*Guests (?:§.)*.*",
    ),
    COOP(
        // language=RegExp
        "(?:§.)*Coop (?:§.)*.*",
    ),
    ISLAND(
        // language=RegExp
        "(?:§.)*Island",
    ),
    MINION(
        // language=RegExp
        "(?:§.)*Minions: (?:§.)*(?<used>\\d+)(?:§.)*/(?:§.)*(?<max>\\d+)",
    ),
    JERRY_ISLAND_CLOSING(
        // language=RegExp
        "Island closes in: (?:§.)*(?<time>.*)",
    ),
    NORTH_STARS(
        // language=RegExp
        "North Stars: (?:§.)*(?<amount>\\d+)",
    ),
    COLLECTION(
        // language=RegExp
        "(?:§.)*Collection:",
    ),
    JACOB_CONTEST(
        // language=RegExp
        "(?:§.)*Jacob's Contest:.*",
    ),
    SLAYER(
        // language=RegExp
        "(?:§.)*Slayer:",
    ),
    DAILY_QUESTS(
        // language=RegExp
        "(?:§.)*Daily Quests:",
    ),
    ACTIVE_EFFECTS(
        // language=RegExp
        "(?:§.)*Active Effects: (?:§.)*\\((?<amount>\\d+)\\)",
    ),
    BESTIARY(
        // language=RegExp
        "(?:§.)*Bestiary:",
    ),
    ESSENCE(
        // language=RegExp
        "(?:§.)*Essence:.*",
    ),
    FORGE(
        // language=RegExp
        "(?:§.)*Forges:",
    ),
    TIMERS(
        // language=RegExp
        "(?:§.)*Timers:",
    ),
    DUNGEON_STATS(
        // language=RegExp
        "Opened Rooms: (?:§.)*(?<opend>\\d+)",
    ),
    PARTY(
        // language=RegExp
        "(?:§.)*Party:.*",
    ),
    TRAPPER(
        // language=RegExp
        "(?:§.)*Trapper:",
    ),
    COMMISSIONS(
        // language=RegExp
        "(?:§.)*Commissions:",
    ),
    POWDER(
        // language=RegExp
        "(?:§.)*Powders:",
    ),
    CRYSTAL(
        // language=RegExp
        "(?:§.)*Crystals:",
    ),
    UNCLAIMED_CHESTS(
        // language=RegExp
        "Unclaimed chests: (?:§.)*(?<amount>\\d+)",
    ),
    RAIN(
        // language=RegExp
        "(?<type>Thunder|Rain): (?:§.)*(?<time>.*)",
    ),
    BROODMOTHER(
        // language=RegExp
        "Broodmother: (?:§.)*(?<stage>.*)",
    ),
    EYES_PLACED(
        // language=RegExp
        "Eyes placed: (?:§.)*(?<amount>\\d).*|(?:§.)*Dragon spawned!|(?:§.)*Egg respawning!",
    ),
    PROTECTOR(
        // language=RegExp
        "Protector: (?:§.)*(?<time>.*)",
    ),
    DRAGON(
        // language=RegExp
        "(?:§.)*Dragon: (?:§.)*\\((?<type>[^)]*)\\)",
    ),
    VOLCANO(
        // language=RegExp
        "Volcano: (?:§.)*(?<time>.*)",
    ),
    REPUTATION(
        // language=RegExp
        "(?:§.)*(?<faction>Barbarian|Mage) Reputation:",
    ),
    FACTION_QUESTS(
        // language=RegExp
        "(?:§.)*Faction Quests:",
    ),
    TROPHY_FISH(
        // language=RegExp
        "(?:§.)*Trophy Fish:",
    ),
    RIFT_INFO(
        // language=RegExp
        "(?:§.)*Good to know:",
    ),
    RIFT_SHEN(
        // language=RegExp
        "(?:§.)*Shen: (?:§.)*\\((?<time>[^)])\\)",
    ),
    RIFT_BARRY(
        // language=RegExp
        "(?:§.)*Advertisement:",
    ),
    COMPOSTER(
        // language=RegExp
        "(?:§.)*Composter:",
    ),
    GARDEN_LEVEL(
        // language=RegExp
        "Garden Level: (?:§.)*(?<level>.*)",
    ),
    COPPER(
        // language=RegExp
        "Copper: (?:§.)*(?<amount>\\d+)",
    ),
    PESTS(
        // language=RegExp
        "(?:§.)*Pests:",
    ),
    PEST_TRAPS(
        // language=RegExp
        "(?:§.)*Pest Traps: (?:§.)*(?<count>\\d+)/(?<max>\\d+)",
    ),
    VISITORS(
        // language=RegExp
        "(?:§.)*Visitors: (?:§.)*\\((?<count>\\d+)\\)",
    ),
    CROP_MILESTONE(
        // language=RegExp
        "(?:§.)*Crop Milestones:",
    ),
    PRIVATE_ISLAND_CRYSTALS(
        // language=RegExp
        "Crystals: (?:§.)*(?<count>\\d+)",
    ),
    OLD_PET_SITTER(
        // language=RegExp
        "Pet Sitter:.*",
    ),
    DUNGEON_HUB_PROGRESS(
        // language=RegExp
        "(?:§.)*Dungeons:",
    ),
    DUNGEON_PUZZLE(
        // language=RegExp
        "(?:§.)*Puzzles: (?:§.)*\\((?<amount>\\d+)\\)",
    ),
    DUNGEON_PARTY(
        // language=RegExp
        "(?:§.)*Party (?:§.)*\\(\\d+\\)",
    ),
    DUNGEON_PLAYER_STATS(
        // language=RegExp
        "(?:§.)*Player Stats",
    ),
    DUNGEON_SKILLS_AND_STATS(
        // language=RegExp
        "(?:§.)*Skills: (?:§.)*\\w+ \\d+: (?:§.)*[\\d.]+%",
    ),

    /** This line holds no information, only here because every widget must be present */
    DUNGEON_ACCOUNT_INFO_LINE(
        // language=RegExp
        "(?:§.)*Account Info",
    ),
    DUNGEON_STATS_LINE(
        // language=RegExp
        "(?:§.)*Dungeon Stats",
    ),
    FROZEN_CORPSES(
        // language=RegExp
        "§b§lFrozen Corpses:",
    ),
    SCRAP(
        // language=RegExp
        "Scrap: (?:§.)*(?<amount>\\d)(?:§.)*/(?:§.)*\\d",
    ),
    EVENT_TRACKERS(
        // language=RegExp
        "§e§lEvent Trackers:",
    ),
    ;

    /** The pattern for the first line of the widget*/
    val pattern by repoGroup.pattern(name.replace("_", ".").lowercase(), "\\s*$pattern0")

    /** The current active information from tab list.
     *
     * When the widget isn't visible, it will be empty
     * */
    var lines: List<String> = emptyList()
        private set

    /** Both are inclusive */
    var boundary = -1 to -1
        private set

    /** Is this widget currently visible in the tab list */
    var isActive: Boolean = false
        private set

    /** Internal value for the checking to set [isActive] */
    private var gotChecked = false

    private var sendOnThisIsland = false

    /** A [matchMatcher] for the first line using the pattern from the widget*/
    inline fun <T> matchMatcherFirstLine(consumer: Matcher.() -> T) =
        if (isActive)
            pattern.matchMatcher(lines.first(), consumer)
        else null

    private fun postNewEvent(lines: List<String>) {
        // Prevent Post if lines are equal
        if (lines == this.lines) return
        this.lines = lines
        isActive = true
        WidgetUpdateEvent(this, lines).post()
    }

    private fun postClearEvent() {
        lines = emptyList()
        WidgetUpdateEvent(this, lines).post()
    }

    /** Update the state of the widget, posts the clear if [isActive] == true && [gotChecked] == false */
    private fun updateIsActive() {
        if (isActive == gotChecked) return
        isActive = gotChecked
        if (!gotChecked) {
            postClearEvent()
        }
    }

    @SkyHanniModule
    companion object {

        /** The index for the start of each Widget (inclusive) */
        private val separatorIndexes = mutableListOf<Pair<Int, TabWidget?>>()

        /** Patterns that where loaded from a future version*/
        private var extraPatterns: List<Pattern> = emptyList()

        private var sentSinceWorldChange = false

        init {
            entries.forEach { it.pattern }
        }

        private val FORCE_UPDATE_DELAY = 2.seconds

        @HandleEvent(onlyOnSkyblock = true)
        fun onSecondPassed(event: SecondPassedEvent) {
            if (sentSinceWorldChange) return
            if (LorenzUtils.lastWorldSwitch.passedSince() < FORCE_UPDATE_DELAY) return
            sentSinceWorldChange = true
            @Suppress("DEPRECATION")
            update(TabListData.getTabList())
            ChatUtils.debug("Forcefully Updated Widgets")
        }

        @HandleEvent(priority = HandleEvent.HIGH)
        fun onTabListUpdate(event: TabListUpdateEvent) {
            if (!LorenzUtils.inSkyBlock) {
                if (separatorIndexes.isNotEmpty()) {
                    separatorIndexes.forEach { it.second?.updateIsActive() }
                    separatorIndexes.clear()
                }
                return
            }
            update(event.tabList)
        }

        // TODO remove this workaround once the WidgetUpdateEvent gets send when the tab list gets first loaded, as intended.
        @HandleEvent(priority = HandleEvent.HIGHEST)
        fun onIslandChange(event: IslandChangeEvent) {
            for (widget in entries) {
                widget.sendOnThisIsland = false
            }

            DelayedRun.runDelayed(2.seconds) {
                TabWidget.reSendEvents()
                for (widget in entries) {
                    if (widget.isActive && !widget.sendOnThisIsland) {
                        WidgetUpdateEvent(widget, widget.lines).post()
                    }
                }
            }
        }

        private fun update(newTablist: List<String>) {
            val tabList = filterTabList(newTablist)

            separatorIndexes.clear()

            for ((index, line) in tabList.withIndex()) {
                val match = entries.firstOrNull { it.pattern.matches(line) }
                    ?: if (extraPatterns.any { it.matches(line) }) null else continue
                separatorIndexes.add(index to match)
            }
            separatorIndexes.add(tabList.size to null)

            separatorIndexes.zipWithNext { (firstIndex, widget), (secondIndex, _) ->
                widget?.boundary = firstIndex to secondIndex - 1
                widget?.gotChecked = true
                widget?.postNewEvent(tabList.subList(firstIndex, secondIndex).filter { it.isNotEmpty() })
            }

            entries.forEach { it.updateIsActive() }

            separatorIndexes.forEach {
                it.second?.gotChecked = false
            }
        }

        @HandleEvent
        fun onWorldChange() {
            sentSinceWorldChange = false
        }

        @HandleEvent(priority = HandleEvent.LOW)
        fun onRepoReload(event: RepositoryReloadEvent) {
            extraPatterns = repoGroup.getUnusedPatterns()
        }

        private fun filterTabList(tabList: List<String>): List<String> {
            var playerListFound = false
            var infoFound = false

            val headers = generateSequence(0) { it + 20 }.take(4).map { it to tabList.getOrNull(it) }

            val removeIndexes = mutableListOf<Int>()

            for ((index, header) in headers) when {
                PLAYER_LIST.pattern.matches(header) ->
                    if (playerListFound) removeIndexes.add(index - removeIndexes.size)
                    else playerListFound = true

                INFO.pattern.matches(header) ->
                    if (infoFound) removeIndexes.add(index - removeIndexes.size)
                    else infoFound = true
            }

            return tabList.transformIf({ size > 81 }, { dropLast(size - 80) }).editCopy {
                removeIndexes.forEach {
                    removeAt(it)
                }
            }
        }

        fun reSendEvents() = entries.forEach {
            if (it.isActive) {
                it.postNewEvent(it.lines)
            } else {
                it.postClearEvent()
            }
        }
    }
}
