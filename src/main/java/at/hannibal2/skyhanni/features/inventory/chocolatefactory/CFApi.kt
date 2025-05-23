package at.hannibal2.skyhanni.features.inventory.chocolatefactory

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.features.inventory.chocolatefactory.CFConfig
import at.hannibal2.skyhanni.config.storage.ProfileSpecificStorage.CFStorage
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.data.jsonobjects.repo.HoppityEggLocationsJson
import at.hannibal2.skyhanni.data.jsonobjects.repo.MilestoneJson
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.RepositoryReloadEvent
import at.hannibal2.skyhanni.features.chroma.ChromaManager
import at.hannibal2.skyhanni.features.event.hoppity.HoppityCollectionStats
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.data.CFDataLoader
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.data.CFUpgrade
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.InventoryDetector
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatLong
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.groupOrNull
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.StringUtils
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.UtilsPatterns
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.nextAfter
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.item.ItemStack
import java.util.TreeSet
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object CFApi {

    private val chromaEnabled get() = ChromaManager.config.enabled.get()
    val config: CFConfig get() = SkyHanniMod.feature.inventory.chocolateFactory
    val profileStorage: CFStorage? get() = ProfileStorageData.profileSpecific?.chocolateFactory
    val patternGroup = RepoPattern.group("misc.chocolatefactory")

    // <editor-fold desc="Patterns">
    /**
     * REGEX-TEST: 46,559,892,200 Chocolate
     */
    val chocolateAmountPattern by patternGroup.pattern(
        "chocolate.amount",
        "(?<amount>[\\d,]+) Chocolate",
    )

    /**
     * REGEX-TEST: Hoppity
     * REGEX-TEST: Chocolate Factory Milestones
     */
    private val chocolateFactoryInventoryNamePattern by patternGroup.pattern(
        "inventory.name",
        "Hoppity|Chocolate Factory Milestones",
    )

    /**
     * REGEX-TEST: §a§lPROMOTE §8➜ §7[208§7] §dExecutive
     * REGEX-TEST: §a§lUPGRADE §8➜ §aRabbit Barn CCXXI
     */
    private val upgradeLorePattern by patternGroup.pattern(
        "item.lore.upgrade",
        "§a§l(?:UPGRADE|PROMOTE) §8➜ (?:§7\\[(?<nextlevel>\\d+)§7] )?(?<upgradename>.*?) ?(?<nextlevelalt>[IVXLCDM]*)\$",
    )

    /**
     * REGEX-TEST: §bRabbit Bro§8 - §7[220§7] §bBoard Member
     * REGEX-TEST: §6Rabbit Dog§8 - §7[190§7] §6Director
     * REGEX-TEST: §dRabbit Daddy§8 - §7[201§7] §dExecutive
     */
    private val employeeNamePattern by patternGroup.pattern(
        "item.name.employee",
        "(?<employee>(?:§.+)+Rabbit .*)§8 - §7\\[\\d*§7] .*",
    )

    /**
     * REGEX-TEST: §7You caught a stray §6§lGolden Rabbit§7! §7You caught a glimpse of §6El Dorado§7, ...
     * REGEX-TEST: §7You caught a stray §9Fish the Rabbit§7
     */
    val caughtRabbitPattern by patternGroup.pattern(
        "rabbit.caught",
        ".*§7You caught.*",
    )
    // </editor-fold>

    var rabbitSlots = mapOf<Int, Int>()
    var otherUpgradeSlots = setOf<Int>()
    var noPickblockSlots = setOf<Int>()
    var barnIndex = 34
    var infoIndex = 13
    var productionInfoIndex = 45
    var prestigeIndex = 27
    var milestoneIndex = 53
    var leaderboardIndex = 52
    var handCookieIndex = 38
    var timeTowerIndex = 39
    var shrineIndex = 41
    var coachRabbitIndex = 42
    var rabbitHitmanIndex = 51
    var maxRabbits = 503
    var hitmanCosts = TreeSet<Long>()
    private var chocolateMilestones = TreeSet<Long>()
    private var chocolateFactoryMilestones: MutableList<MilestoneJson> = mutableListOf()
    private var chocolateShopMilestones: MutableList<MilestoneJson> = mutableListOf()
    private var maxPrestige = 6
    var cfShortcutIndex = 16

    val inChocolateFactory get() = mainInventory.isInside()
    var chocolateFactoryPaused = false

    var currentPrestige = 1
    var chocolatePerSecond = 0.0
    var leaderboardPosition: Int? = null
    var leaderboardPercentile: Double? = null
    var chocolateForPrestige = 150_000_000L

    var factoryUpgrades = listOf<CFUpgrade>()
    var bestAffordableSlot = -1
    var bestPossibleSlot = -1

    var specialRabbitTextures = listOf<String>()
    var warningSound = SoundUtils.createSound("note.pling", 1f)
    val mainInventory = InventoryDetector { name -> name == "Chocolate Factory" }

    @HandleEvent(onlyOnSkyblock = true)
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        if (chocolateFactoryInventoryNamePattern.matches(event.inventoryName)) {
            if (config.enabled) {
                chocolateFactoryPaused = true
                CFStats.updateDisplay()
            }
            return
        }
        if (!mainInventory.isInside()) return

        if (config.enabled) {
            factoryUpgrades = emptyList()
            DelayedRun.runNextTick {
                CFDataLoader.updateInventoryItems(event.inventoryItems)
            }
        }
    }

    @HandleEvent
    fun onRepoReload(event: RepositoryReloadEvent) {
        val data = event.getConstant<HoppityEggLocationsJson>("HoppityEggLocations")

        rabbitSlots = data.rabbitSlots
        otherUpgradeSlots = data.otherUpgradeSlots
        noPickblockSlots = data.noPickblockSlots
        barnIndex = data.barnIndex
        infoIndex = data.infoIndex
        productionInfoIndex = data.productionInfoIndex
        prestigeIndex = data.prestigeIndex
        milestoneIndex = data.milestoneIndex
        leaderboardIndex = data.leaderboardIndex
        handCookieIndex = data.handCookieIndex
        timeTowerIndex = data.timeTowerIndex
        shrineIndex = data.shrineIndex
        coachRabbitIndex = data.coachRabbitIndex
        rabbitHitmanIndex = data.rabbitHitmanIndex
        maxRabbits = data.maxRabbits
        maxPrestige = data.maxPrestige
        cfShortcutIndex = data.cfShortcutIndex
        chocolateMilestones = data.chocolateMilestones
        hitmanCosts = data.hitmanCosts
        chocolateFactoryMilestones = data.chocolateFactoryMilestones.toMutableList()
        chocolateShopMilestones = data.chocolateShopMilestones.toMutableList()
        specialRabbitTextures = data.specialRabbits

        CFUpgrade.updateIgnoredSlots()
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        val old = "event.chocolateFactory"
        val new = "inventory.chocolateFactory"
        event.move(44, "$old.enabled", "$new.enabled")
        event.move(44, "$old.statsDisplay", "$new.statsDisplay")
        event.move(44, "$old.statsDisplayList", "$new.statsDisplayList")
        event.move(44, "$old.showStackSizes", "$new.showStackSizes")
        event.move(44, "$old.highlightUpgrades", "$new.highlightUpgrades")
        event.move(44, "$old.useMiddleClick", "$new.useMiddleClick")
        event.move(44, "$old.rabbitWarning", "$new.rabbitWarning")
        event.move(44, "$old.barnCapacityThreshold", "$new.barnCapacityThreshold")
        event.move(44, "$old.extraTooltipStats", "$new.extraTooltipStats")
        event.move(44, "$old.timeTowerWarning", "$new.timeTowerWarning")
        event.move(44, "$old.position", "$new.position")
        event.move(44, "$old.compactOnClick", "$new.compactOnClick")
        event.move(44, "$old.compactOnClickAlways", "$new.compactOnClickAlways")
        event.move(44, "$old.tooltipMove", "$new.tooltipMove")
        event.move(44, "$old.tooltipMovePosition", "$new.tooltipMovePosition")
        event.move(44, "$old.hoppityMenuShortcut", "$new.hoppityMenuShortcut")
        event.move(44, "$old.hoppityCollectionStats", "$new.hoppityCollectionStats")
        event.move(44, "$old.hoppityStatsPosition", "$new.hoppityStatsPosition")
    }

    fun getChocolateBuyCost(lore: List<String>): Long? {
        val nextLine = lore.nextAfter({ UtilsPatterns.costLinePattern.matches(it) }) ?: return null
        return chocolateAmountPattern.matchMatcher(nextLine.removeColor()) {
            group("amount").formatLong()
        }
    }

    fun getNextLevelName(stack: ItemStack): String? = upgradeLorePattern.firstMatcher(stack.getLore()) {
        val upgradeName = if (stack.getLore().any { it == "§8Employee" }) employeeNamePattern.matchMatcher(stack.displayName) {
            groupOrNull("employee")
        } else groupOrNull("upgradename")
        val nextLevel = groupOrNull("nextlevel") ?: groupOrNull("nextlevelalt")
        if (upgradeName == null || nextLevel == null) null
        else "$upgradeName $nextLevel"
    }

    fun getNextMilestoneChocolate(amount: Long): Long {
        return chocolateMilestones.higher(amount) ?: 0
    }

    fun isEnabled() = LorenzUtils.inSkyBlock && config.enabled

    fun isMaxPrestige() = currentPrestige >= maxPrestige

    fun timeTowerChargeDuration() = if (HoppityCollectionStats.hasFoundRabbit("Einstein")) 7.hours else 8.hours

    fun timeTowerMultiplier(): Double {
        var multiplier = (profileStorage?.timeTowerLevel ?: 0) * 0.1
        if (HoppityCollectionStats.hasFoundRabbit("Mu")) multiplier += 0.7
        return multiplier
    }

    fun timeUntilNeed(goal: Long): Duration {
        var needed = goal
        val profileStorage = profileStorage ?: return Duration.ZERO

        val baseMultiplier = profileStorage.rawChocolateMultiplier
        val rawChocolatePerSecond = profileStorage.rawChocPerSecond

        if (rawChocolatePerSecond == 0) return Duration.INFINITE

        val secondsUntilTowerExpires = CFTimeTowerManager.timeTowerActiveDuration().inWholeSeconds

        val timeTowerChocPerSecond = rawChocolatePerSecond * (baseMultiplier + timeTowerMultiplier())

        val secondsAtRate = needed / timeTowerChocPerSecond
        if (secondsAtRate < secondsUntilTowerExpires) {
            return secondsAtRate.seconds
        }

        needed -= (secondsUntilTowerExpires * timeTowerChocPerSecond).toLong()
        val basePerSecond = rawChocolatePerSecond * baseMultiplier
        return (needed / basePerSecond + secondsUntilTowerExpires).seconds
    }

    fun milestoneByRabbit(rabbitName: String): MilestoneJson? {
        return chocolateFactoryMilestones.firstOrNull {
            it.rabbit.removeColor() == rabbitName.removeColor()
        } ?: chocolateShopMilestones.firstOrNull {
            it.rabbit.removeColor() == rabbitName.removeColor()
        }
    }

    fun isMax(): Boolean = profileStorage?.let {
        it.maxChocolate == it.currentChocolate
    } ?: false

    fun String.partyModeReplace(): String =
        if (config.partyMode.get() && inChocolateFactory && chromaEnabled) replace(Regex("§[a-fA-F0-9]"), "§z")
        else this

    fun updatePosition(position: Int?, leaderboard: String) {
        position ?: return
        val storage = profileStorage?.positionChange ?: return
        val lastTime = storage.lastTime
        val lastPosition = storage.lastPosition
        val lastLeaderboard = storage.lastLeaderboard

        if (lastLeaderboard == leaderboard) return

        lastLeaderboard?.let { lastLb ->
            if (lastPosition == -1 || lastPosition == position || !config.leaderboardChange) return@let

            var message = "§b$lastLb §c-> §b$leaderboard"
            val change = lastPosition - position
            val color = if (change > 0) "§a+" else "§c"
            message += "\n §7Changed by $color${change.addSeparators()} ${StringUtils.pluralize(change, "spot")}"

            lastTime?.let {
                message += " §7in §b${it.passedSince().format(maxUnits = 2)}"
            }

            ChatUtils.chat(" \n§7(SkyHanni) §6CF Leaderboard Change§7:\n $message\n ", prefix = false)
        }

        storage.lastTime = SimpleTimeMark.now()
        storage.lastLeaderboard = leaderboard
        storage.lastPosition = position
    }
}
