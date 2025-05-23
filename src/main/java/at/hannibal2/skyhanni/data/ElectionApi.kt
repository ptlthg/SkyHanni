package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigManager
import at.hannibal2.skyhanni.data.ElectionCandidate.Companion.getMayorFromPerk
import at.hannibal2.skyhanni.data.ElectionCandidate.Companion.setAssumeMayorJson
import at.hannibal2.skyhanni.data.Perk.Companion.getPerkFromName
import at.hannibal2.skyhanni.data.jsonobjects.other.MayorCandidate
import at.hannibal2.skyhanni.data.jsonobjects.other.MayorElection
import at.hannibal2.skyhanni.data.jsonobjects.other.MayorJson
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.features.fame.ReminderUtils
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ApiUtils
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ConditionalUtils.onToggle
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SimpleTimeMark.Companion.asTimeMark
import at.hannibal2.skyhanni.utils.SkyBlockTime
import at.hannibal2.skyhanni.utils.SkyBlockTime.Companion.SKYBLOCK_YEAR_MILLIS
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.nextAfter
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.put
import at.hannibal2.skyhanni.utils.json.fromJson
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.item.ItemStack
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@SkyHanniModule
object ElectionApi {

    private val group = RepoPattern.group("mayorapi")

    /**
     * REGEX-TEST: Schedules an extra §bFishing Festival §7event during the year.
     */
    val foxyExtraEventPattern by group.pattern(
        "foxy.extraevent",
        "Schedules an extra §.(?<event>.*) §.event during the year\\.",
    )

    /**
     * REGEX-TEST: The election room is now closed. Clerk Seraphine is doing a final count of the votes...
     */
    private val electionOverPattern by group.pattern(
        "election.over",
        "§eThe election room is now closed\\. Clerk Seraphine is doing a final count of the votes\\.\\.\\.",
    )

    /**
     * REGEX-TEST: Calendar and Events
     */
    val calendarGuiPattern by group.pattern(
        "calendar.gui",
        "Calendar and Events",
    )

    /**
     * REGEX-TEST: §dMayor Jerry
     * REGEX-TEST: §cMayor Aatrox
     */
    private val mayorHeadPattern by group.pattern(
        "mayor.head",
        "§.Mayor (?<name>.*)",
    )

    /**
     * REGEX-TEST: §9Perkpocalypse Perks:
     */
    private val perkpocalypsePerksPattern by group.pattern(
        "perkpocalypse",
        "§9Perkpocalypse Perks:",
    )

    var currentMayor: ElectionCandidate? = null
        private set
    var currentMinister: ElectionCandidate? = null
        private set
    private var lastMayor: ElectionCandidate? = null
    var jerryExtraMayor: Pair<ElectionCandidate?, SimpleTimeMark> = null to SimpleTimeMark.farPast()
        private set
    private var lastJerryExtraMayorReminder = SimpleTimeMark.farPast()

    private var lastUpdate = SimpleTimeMark.farPast()

    var rawMayorData: MayorJson? = null
        private set
    private var candidates = mapOf<Int, MayorCandidate>()

    var nextMayorTimestamp = SimpleTimeMark.farPast()
        private set

    private const val ELECTION_END_MONTH = 3 // Late Spring
    private const val ELECTION_END_DAY = 27

    /**
     * @param input: The name of the mayor
     * @return: The NotEnoughUpdates color of the mayor; If no mayor was found, it will return "§c"
     */
    fun mayorNameToColorCode(input: String): String = ElectionCandidate.getMayorFromName(input)?.color ?: "§c"

    /**
     * @param input: The name of the mayor
     * @return: The NotEnoughUpdates color of the mayor with the name of the mayor; If no mayor was found, it will return "§c[input]"
     */
    fun mayorNameWithColorCode(input: String) = mayorNameToColorCode(input) + input

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!LorenzUtils.onHypixel) return
        if (event.repeatSeconds(2)) {
            checkHypixelApi()
            getTimeTillNextMayor()
        }

        @Suppress("InSkyBlockEarlyReturn")
        if (!LorenzUtils.inSkyBlock) return
        if (!ElectionCandidate.JERRY.isActive()) return
        if (jerryExtraMayor.first != null && jerryExtraMayor.second.isInPast()) {
            jerryExtraMayor = null to SimpleTimeMark.farPast()
            ChatUtils.clickableChat(
                "The Perkpocalypse Mayor has expired! Click here to update the new temporary Mayor.",
                onClick = { HypixelCommands.calendar() },
            )
        }
        val misc = SkyHanniMod.feature.misc
        if (jerryExtraMayor.first == null && misc.unknownPerkpocalypseMayorWarning) {
            if (lastJerryExtraMayorReminder.passedSince() < 5.minutes) return
            if (ReminderUtils.isBusy()) return
            lastJerryExtraMayorReminder = SimpleTimeMark.now()
            ChatUtils.clickableChat(
                "The Perkpocalypse Mayor is not known! Click here to update the temporary Mayor.",
                onClick = { HypixelCommands.calendar() },
                replaceSameMessage = true,
            )
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onChat(event: SkyHanniChatEvent) {
        if (electionOverPattern.matches(event.message)) {
            lastMayor = currentMayor
            currentMayor = ElectionCandidate.UNKNOWN
            currentMinister = null
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {

        if (!calendarGuiPattern.matches(event.inventoryName)) return

        val stack: ItemStack = event.inventoryItems.values.firstOrNull {
            mayorHeadPattern.matchMatcher(it.displayName) {
                group("name") == "Jerry"
            } ?: false
        } ?: return

        val perk = stack.getLore().nextAfter({ perkpocalypsePerksPattern.matches(it) }, 2) ?: return
        // This is the first Perk of the Perkpocalypse Mayor
        val jerryMayor = getMayorFromPerk(getPerkFromName(perk.removeColor()) ?: return)?.addAllPerks() ?: return

        val lastMayorTimestamp = nextMayorTimestamp - SKYBLOCK_YEAR_MILLIS.milliseconds

        val expireTime = (1..21)
            .map { lastMayorTimestamp + (6.hours * it) }
            .firstOrNull { it.isInFuture() }
            ?.coerceAtMost(nextMayorTimestamp) ?: return

        ChatUtils.debug("Jerry Mayor found: ${jerryMayor.name} expiring at: ${expireTime.timeUntil()}")

        jerryExtraMayor = jerryMayor to expireTime
    }

    fun SkyBlockTime.getElectionYear(): Int {
        var mayorYear = year

        // Check if this year's election has not happened yet
        if (month < ELECTION_END_MONTH || (day < ELECTION_END_DAY && month == ELECTION_END_MONTH)) {
            // If so, the current mayor is still from last year's election
            mayorYear--
        }
        return mayorYear
    }

    private fun calculateNextMayorTime(): SimpleTimeMark {
        val now = SkyBlockTime.now()

        return SkyBlockTime(now.getElectionYear() + 1, ELECTION_END_MONTH, day = ELECTION_END_DAY).asTimeMark()
    }

    private fun getTimeTillNextMayor() {
        nextMayorTimestamp = calculateNextMayorTime()
    }

    private fun checkHypixelApi(forceReload: Boolean = false) {
        if (!forceReload) {
            if (currentMayor == ElectionCandidate.UNKNOWN) {
                if (lastUpdate.passedSince() < 1.minutes) return
            } else {
                if (lastUpdate.passedSince() < 20.minutes) return
            }
        }
        lastUpdate = SimpleTimeMark.now()

        SkyHanniMod.launchIOCoroutine {
            val jsonObject = ApiUtils.getJSONResponse(
                "https://api.hypixel.net/v2/resources/skyblock/election",
                apiName = "Hypixel Election",
            )
            rawMayorData = ConfigManager.gson.fromJson<MayorJson>(jsonObject)
            val data = rawMayorData ?: return@launchIOCoroutine
            val mayor = data.mayor ?: error("mayor is null")
            val election = mayor.election ?: error("election is null")
            val map = mutableMapOf<Int, MayorCandidate>()
            map put election.getPairs()
            data.current?.let {
                map put data.current.getPairs()
            }
            candidates = map

            val currentMayorName = mayor.name
            if (lastMayor?.name != currentMayorName) {
                Perk.resetPerks()
                currentMayor = setAssumeMayorJson(currentMayorName, mayor.perks)
                currentMinister = mayor.minister?.let { setAssumeMayorJson(it.name, listOf(it.perk)) }
            }
        }
    }

    private fun MayorElection.getPairs() = year + 1 to candidates.bestCandidate()

    private fun List<MayorCandidate>.bestCandidate() = maxBy { it.votes }

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        val config = SkyHanniMod.feature.dev.debug.assumeMayor
        config.onToggle {
            val mayor = config.get()

            if (mayor == ElectionCandidate.DISABLED) {
                checkHypixelApi(forceReload = true)
            } else {
                mayor.addPerks(mayor.perks.toList())
                currentMayor = mayor
            }
        }
    }

    @HandleEvent
    fun onDebug(event: DebugDataCollectEvent) {
        event.title("Mayor Election")

        val assumeMayor = SkyHanniMod.feature.dev.debug.assumeMayor.get()

        val list = buildList {
            add("Current Mayor: ${currentMayor?.name ?: "Unknown"}")
            add("Active Perks: ${currentMayor?.activePerks}")
            add("Last Update: ${lastUpdate.formattedDate("EEEE, MMM d h:mm a")} (${lastUpdate.passedSince()} ago)")
            add("Time Till Next Mayor: ${nextMayorTimestamp.timeUntil()}")
            add("Current Minister: ${currentMinister?.name ?: "Unknown"}")
            add("Current Minister Perk: ${currentMinister?.activePerks}")
            if (jerryExtraMayor.first != null) {
                add("Jerry Mayor: ${jerryExtraMayor.first?.name} expiring at: ${jerryExtraMayor.second.timeUntil()}")
            }
            add("assumeMayor: $assumeMayor")
        }

        if (currentMayor == null || currentMayor == ElectionCandidate.UNKNOWN || assumeMayor != ElectionCandidate.DISABLED) {
            event.addData(list)
        } else {
            event.addIrrelevant(list)
        }

    }

    val isDerpy get() = Perk.DOUBLE_MOBS_HP.isActive

    fun Int.derpy() = if (isDerpy) this / 2 else this

    fun Int.ignoreDerpy() = if (isDerpy) this * 2 else this
}
