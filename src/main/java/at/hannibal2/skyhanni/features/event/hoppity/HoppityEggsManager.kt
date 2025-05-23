package at.hannibal2.skyhanni.features.event.hoppity

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.events.ProfileJoinEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.hoppity.EggFoundEvent
import at.hannibal2.skyhanni.events.hoppity.EggSpawnedEvent
import at.hannibal2.skyhanni.features.event.hoppity.HoppityEggType.Companion.getEggType
import at.hannibal2.skyhanni.features.event.hoppity.HoppityEggType.Companion.resettingEntries
import at.hannibal2.skyhanni.features.fame.ReminderUtils
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.CFApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.LocationUtils
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SimpleTimeMark.Companion.asTimeMark
import at.hannibal2.skyhanni.utils.SimpleTimeMark.Companion.fromNow
import at.hannibal2.skyhanni.utils.SimpleTimeMark.Companion.now
import at.hannibal2.skyhanni.utils.SkyBlockTime
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.TimeUtils.format
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object HoppityEggsManager {

    val config get() = SkyHanniMod.feature.event.hoppityEggs
    private val chatConfig get() = config.chat
    private val unclaimedEggsConfig get() = config.unclaimedEggs
    private val waypointsConfig get() = config.waypoints
    private val profileStorage get() = ProfileStorageData.profileSpecific?.chocolateFactory

    // <editor-fold desc="Patterns">
    /**
     * REGEX-TEST: §d§lHOPPITY'S HUNT §r§dYou found a §r§9Chocolate Lunch Egg §r§don a ledge next to the stairs up§r§d!
     * REGEX-TEST: §d§lHOPPITY'S HUNT §r§dYou found a §r§aChocolate Dinner Egg §r§dbehind Emissary Sisko§r§d!
     * REGEX-TEST: §d§lHOPPITY'S HUNT §r§dYou found a §r§9Chocolate Lunch Egg §r§dnear the Diamond Essence Shop§r§d!
     * REGEX-TEST: §d§lHOPPITY'S HUNT §r§dYou found a §r§6Chocolate Brunch Egg §r§don a lower platform§r§d!
     * REGEX-TEST: §d§lHOPPITY'S HUNT §r§dYou found a §r§9Chocolate Déjeuner Egg §r§don the arms of the Amethyst statue§r§d!
     * REGEX-TEST: §d§lHOPPITY'S HUNT §r§dYou found a §r§aChocolate Supper Egg §r§dunderneath the stairwell§r§d!
     */
    val eggFoundPattern by CFApi.patternGroup.pattern(
        "egg.found",
        "§d§lHOPPITY'S HUNT §r§dYou found a §r§.Chocolate (?<meal>[\\wé]+) Egg §r§d(?<note>.*)§r§d!",
    )

    /**
     * REGEX-TEST: §d§lHOPPITY'S HUNT §r§dYou found a §r§cHitman Egg§r§d!
     */
    val hitmanEggFoundPattern by CFApi.patternGroup.pattern(
        "egg.found.hitman",
        "§d§lHOPPITY'S HUNT §r§dYou found a (?:§.)+Hitman Egg(?:§.)+!",
    )

    /**
     * REGEX-TEST: §aYou bought §r§9Casanova §r§afor §r§6970,000 Coins§r§a!
     * REGEX-TEST: §aYou bought §r§fHeidie §r§afor §r§6194,000 Coins§r§a!
     */
    val eggBoughtPattern by CFApi.patternGroup.pattern(
        "egg.bought",
        "§aYou bought §r(?<rabbitname>.*?) §r§afor §r§6(?<cost>[\\d,]*) Coins§r§a!",
    )

    /**
     * REGEX-TEST: §D§LHOPPITY'S HUNT §7You found §fArnie §7(§F§LCOMMON§7)!
     * REGEX-TEST: §D§LHOPPITY'S HUNT §7You found §aPenelope §7(§A§LUNCOMMON§7)!
     * REGEX-TEST: §D§LHOPPITY'S HUNT §7You found §6Solomon §7(§6§LLEGENDARY§7)!
     */
    val rabbitFoundPattern by CFApi.patternGroup.pattern(
        "rabbit.found",
        "§D§LHOPPITY'S HUNT §7You found (?<name>.*) §7\\(§.§L(?<rarity>.*)§7\\)!",
    )

    /**
     * REGEX-TEST: §d§lNEW RABBIT! §6+2 Chocolate §7and §6+0.003x Chocolate §7per second!
     * REGEX-TEST: §d§lNEW RABBIT! §6+0.02x Chocolate §7per second!
     * REGEX-TEST: §d§lNEW RABBIT! §7Your §dTime Tower §7charge time is now §a7h§7!
     */
    val newRabbitFound by CFApi.patternGroup.pattern(
        "rabbit.found.new",
        "§d§lNEW RABBIT! (?:(?:§6\\+(?<chocolate>.*) Chocolate §7and )?§6\\+(?<perSecond>.*)x Chocolate §7per second!|(?<other>.*))",
    )

    /**
     * REGEX-TEST: §7§lDUPLICATE RABBIT! §6+6,759,912 Chocolate
     */
    val duplicateRabbitFound by CFApi.patternGroup.pattern(
        "rabbit.duplicate",
        "§7§lDUPLICATE RABBIT! §6\\+(?<amount>[\\d,]+) Chocolate",
    )

    private val noEggsLeftPattern by CFApi.patternGroup.pattern(
        "egg.noneleft",
        "§cThere are no hidden Chocolate Rabbit Eggs nearby! Try again later!",
    )

    /**
     * REGEX-TEST: §d§lHOPPITY'S HUNT §r§dA §r§9Chocolate Lunch Egg §r§dhas appeared!
     * REGEX-TEST: §d§lHOPPITY'S HUNT §r§dA §r§aChocolate Déjeune Egg §r§dhas appeared!
     */
    private val eggSpawnedPattern by CFApi.patternGroup.pattern(
        "egg.spawned",
        "§d§lHOPPITY'S HUNT §r§dA §r§.Chocolate (?<meal>[\\wé]+) Egg §r§dhas appeared!",
    )

    /**
     * REGEX-TEST: §cYou have already collected this Chocolate Breakfast Egg§r§c! Try again when it respawns!
     * REGEX-TEST: §cYou have already collected this Chocolate Déjeune Egg§r§c! Try again when it respawns!
     */
    private val eggAlreadyCollectedPattern by CFApi.patternGroup.pattern(
        "egg.alreadycollected",
        "§cYou have already collected this Chocolate (?<meal>[\\wé]+) Egg§r§c! Try again when it respawns!",
    )
    private val hoppityEventNotOn by CFApi.patternGroup.pattern(
        "egg.notevent",
        "§cThis only works during Hoppity's Hunt!",
    )
    // </editor-fold>

    private var lastMeal: HoppityEggType? = null
    private var lastNote: String? = null

    // has claimed all eggs at least once
    private var warningActive = false
    private var lastWarnTime = SimpleTimeMark.farPast()

    private var latestWaypointOnclick: () -> Unit = {}
    private var syncedFromConfig: Boolean = false

    @HandleEvent
    fun onProfileJoin(event: ProfileJoinEvent) {
        if (!HoppityApi.isHoppityEvent()) return
        resettingEntries.forEach {
            val lastFound = profileStorage?.mealLastFound?.get(it) ?: SimpleTimeMark.farFuture()
            if (lastFound.isInPast()) it.markClaimed(lastFound)

            val nextSpawn = profileStorage?.mealNextSpawn?.get(it) ?: SimpleTimeMark.farFuture()
            if (nextSpawn.isInPast() && it.hasRemainingSpawns() && !it.hasNotFirstSpawnedYet()) it.markSpawned()
        }
    }

    @HandleEvent
    fun onEggSpawned(event: EggSpawnedEvent) {
        event.eggType.markSpawned(setLastReset = true)
    }

    @HandleEvent
    fun onWorldChange() {
        lastMeal = null
        lastNote = null
        syncFromConfig()
    }

    private fun syncFromConfig() {
        if (syncedFromConfig) return
        profileStorage?.mealNextSpawn?.filter {
            it.value.isInPast()
        }?.keys?.forEach {
            if (HoppityApi.isHoppityEvent()) it.markSpawned()
        }
        syncedFromConfig = true
    }

    @HandleEvent
    fun onEggFound(event: EggFoundEvent) {
        if (!HoppityApi.isHoppityEvent() || !event.type.isResetting) return
        HoppityEggLocations.saveNearestEgg()
        event.type.markClaimed()
        lastMeal = event.type
        lastNote = event.note
    }

    private fun SkyHanniChatEvent.sendNextEggAvailable() {
        val nextEgg = HoppityEggType.resettingEntries.minByOrNull { it.timeUntil } ?: return
        ChatUtils.chat("§eNext egg available in §b${nextEgg.timeUntil.format()}§e.")
        blockedReason = "hoppity_egg"
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onChat(event: SkyHanniChatEvent) {
        hoppityEventNotOn.matchMatcher(event.message) {
            val currentYear = SkyBlockTime.now().year

            if (chatConfig.eggLocatorTimeInChat) {
                val timeUntil = SkyBlockTime(currentYear + 1).asTimeMark().timeUntil()
                ChatUtils.chat("§eHoppity's Hunt is not active. The next Hoppity's Hunt is in §b${timeUntil.format()}§e.")
                event.blockedReason = "hoppity_egg"
            }
            return
        }

        if (!HoppityApi.isHoppityEvent()) return

        noEggsLeftPattern.matchMatcher(event.message) {
            HoppityEggType.markAllFound()
            if (chatConfig.eggLocatorTimeInChat) event.sendNextEggAvailable()
            return
        }

        eggAlreadyCollectedPattern.matchMatcher(event.message) {
            getEggType(event).markClaimed()
            if (chatConfig.eggLocatorTimeInChat) event.sendNextEggAvailable()
            return
        }

        eggSpawnedPattern.matchMatcher(event.message) {
            EggSpawnedEvent(getEggType(event)).post()
            return
        }
    }

    fun getAndDisposeWaypointOnclick(): () -> Unit {
        val onClick = latestWaypointOnclick
        latestWaypointOnclick = {}
        return onClick
    }

    fun shareWaypointPrompt() {
        if (!waypointsConfig.shared) return
        val meal = lastMeal ?: return
        val note = lastNote ?: return
        lastMeal = null
        lastNote = null

        val currentLocation = LocationUtils.playerLocation()
        DelayedRun.runNextTick {
            latestWaypointOnclick = { HoppityEggsShared.shareNearbyEggLocation(currentLocation, meal, note) }
            if (chatConfig.compact) return@runNextTick
            ChatUtils.clickableChat(
                "Click here to share the location of this chocolate egg with the server!",
                onClick = latestWaypointOnclick,
                "§eClick to share!",
                expireAt = 30.seconds.fromNow(),
                oneTimeClick = true,
            )
            latestWaypointOnclick = {}
        }
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        checkSpawned()
        if (!isActive()) return
        checkWarn()
    }

    private fun checkSpawned() {
        resettingEntries
            .filter { it.spawnedToday() && !it.alreadyResetToday() }
            .forEach { EggSpawnedEvent(it).post() }
    }

    private fun checkWarn() {
        val allEggsRemaining = HoppityEggType.allEggsUnclaimed()
        if (!warningActive) warningActive = !allEggsRemaining

        if (warningActive && allEggsRemaining) warn()
    }

    private val warpClickAction: Pair<() -> Unit, String>
        get() =
            if (LorenzUtils.inSkyBlock) {
                { HypixelCommands.warp(unclaimedEggsConfig.warpClickDestination) } to
                    "warp to ${unclaimedEggsConfig.warpClickDestination}".trim()
            } else {
                { HypixelCommands.skyblock() } to "join /skyblock!"
            }

    private fun warn() {
        if (!unclaimedEggsConfig.warningsEnabled) return
        if (ReminderUtils.isBusy() && !unclaimedEggsConfig.warnWhileBusy) return
        if (lastWarnTime.passedSince() < 1.minutes) return

        lastWarnTime = now()
        val amount = HoppityEggType.resettingEntries.size
        val message = "All $amount Hoppity Eggs are ready to be found!"
        if (unclaimedEggsConfig.warpClickEnabled) {
            val (action, actionName) = warpClickAction
            ChatUtils.clickToActionOrDisable(
                message,
                unclaimedEggsConfig::warpClickEnabled,
                actionName = actionName,
                action = action,
            )
        } else ChatUtils.chat(message, replaceSameMessage = true)
        TitleManager.sendTitle("§e$amount Hoppity Eggs!")
        SoundUtils.repeatSound(100, 10, SoundUtils.plingSound)
    }

    // <editor-fold desc="Mass Migration Map">
    private val massMigrationMap by lazy {
        mapOf(
            "waypoints" to "waypoints.enabled",
            "waypointsImmediately" to "waypoints.showImmediately",
            "waypointColor" to "waypoints.color",
            "showLine" to "waypoints.showLine",
            "showPathFinder" to "waypoints.showPathFinder",
            "showAllWaypoints" to "waypoints.showAll",
            "hideDuplicateWaypoints" to "waypoints.hideDuplicates",
            "sharedWaypoints" to "waypoints.shared",
            "highlightDuplicateEggLocations" to "waypoints.highlightDuplicates",
            "showNearbyDuplicates" to "waypoints.showNearbyDuplicates",
            "loadFromNeuPv" to "waypoints.loadFromNeuPv",

            "showClaimedEggs" to "unclaimedEggs.enabled",
            "position" to "unclaimedEggs.position",
            "unclaimedEggsOrder" to "unclaimeddEggs.displayOrder",
            "warnUnclaimedEggs" to "unclaimedEggs.warningsEnabled",
            "showCollectedLocationCount" to "unclaimedEggs.showCollectedLocationCount",
            "warpUnclaimedEggs" to "unclaimedEggs.warpClickEnabled",
            "warpDestination" to "unclaimedEggs.warpClickDestination",
            "showWhileBusy" to "unclaimedEggs.showWhileBusy",
            "warnWhileBusy" to "unclaimedEggs.warnWhileBusy",
            "showOutsideSkyblock" to "unclaimedEggs.showOutsideSkyblock",

            "timeInChat" to "chat.eggLocatorTimeInChat",
            "compactChat" to "chat.compact",
            "rarityInCompact" to "chat.rarityInCompact",
            "showDuplicateNumber" to "chat.showDuplicateNumber",
            "recolorTTChocolate" to "chat.recolorTTChocolate",
        )
    }
    // </editor-fold>

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(
            44,
            "event.chocolateFactory.highlightHoppityShop",
            "event.chocolateFactory.hoppityEggs.highlightHoppityShop",
        )
        event.move(44, "event.chocolateFactory.hoppityEggs", "event.hoppityEggs")
        event.move(50, "event.hoppityEggs.showDuringContest", "event.hoppityEggs.showWhileBusy")
        event.move(62, "event.hoppityEggs.uniquesWarpMenu", "event.hoppityEggs.warpMenu.enabled")
        event.move(62, "event.hoppityEggs.uniquesWarpMenuHideMax", "event.hoppityEggs.warpMenu.hideWhenMaxed")

        val baseConfig = "event.hoppityEggs"
        massMigrationMap.forEach { (oldKey, newKey) ->
            event.move(79, "$baseConfig.$oldKey", "$baseConfig.$newKey")
        }
    }

    fun isActive() = (LorenzUtils.inSkyBlock || (LorenzUtils.onHypixel && unclaimedEggsConfig.showOutsideSkyblock)) &&
        HoppityApi.isHoppityEvent()
}
