package at.hannibal2.skyhanni.features.garden.contest

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandTypeTags
import at.hannibal2.skyhanni.data.ScoreboardData
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.garden.farming.FarmingContestEvent
import at.hannibal2.skyhanni.features.garden.CropType
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.NumberUtil.formatInt
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkyBlockTime
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.addOrPut
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.nextAfter
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.sortedDesc
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.item.ItemStack
import kotlin.time.Duration.Companion.minutes

@SkyHanniModule
object FarmingContestApi {

    private val patternGroup = RepoPattern.group("garden.farming.contest")
    private val timePattern by patternGroup.pattern(
        "time",
        "§a(?<month>.*) (?<day>.*)(?:rd|st|nd|th), Year (?<year>.*)",
    )
    private val cropPattern by patternGroup.pattern(
        "crop",
        "§8(?<crop>.*) Contest",
    )
    private val sidebarCropPattern by patternGroup.pattern(
        "sidebarcrop",
        "\\s*(?:§e○|§6☘) §f(?<crop>.*) §a.*",
    )

    private val contests = mutableMapOf<Long, FarmingContest>()
    private var internalContest = false
    val inContest
        get() = internalContest && IslandTypeTags.CONTESTS_SHOWN.inAny()
    var contestCrop: CropType? = null
    private var startTime = SimpleTimeMark.farPast()
    var inInventory = false

    init {
        ContestBracket.entries.forEach { it.bracketPattern }
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (internalContest && startTime.passedSince() > 20.minutes) {
            FarmingContestEvent(contestCrop!!, FarmingContestPhase.STOP).post()
            internalContest = false
        }

        @Suppress("IsInIslandEarlyReturn")
        if (!GardenApi.inGarden()) return

        checkActiveContest()
    }

    private fun checkActiveContest() {
        val currentCrop = readCurrentCrop()
        val currentContest = currentCrop != null

        if (inContest != currentContest) {
            if (currentContest) {
                FarmingContestEvent(currentCrop!!, FarmingContestPhase.START).post()
                startTime = SimpleTimeMark.now()
            } else {
                if (startTime.passedSince() > 2.minutes) {
                    FarmingContestEvent(contestCrop!!, FarmingContestPhase.STOP).post()
                }
            }
            internalContest = currentContest
        } else {
            if (currentCrop != contestCrop && currentCrop != null) {
                FarmingContestEvent(currentCrop, FarmingContestPhase.CHANGE).post()
                startTime = SimpleTimeMark.now()
            }
        }
        contestCrop = currentCrop
    }

    private fun readCurrentCrop(): CropType? {
        val line = ScoreboardData.sidebarLinesFormatted.nextAfter("§eJacob's Contest") ?: return null
        return sidebarCropPattern.matchMatcher(line) {
            val cropName = group("crop")
            try {
                CropType.getByName(cropName)
            } catch (e: IllegalStateException) {
                ScoreboardData.sidebarLinesFormatted
                ErrorManager.logErrorWithData(
                    e, "Farming contest read current crop failed",
                    "cropName" to cropName,
                    "line" to line,
                    "sidebarLinesFormatted" to ScoreboardData.sidebarLinesFormatted,
                )
                null
            }
        }
    }

    @HandleEvent
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        if (event.inventoryName == "Your Contests") {
            inInventory = true
        }
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        inInventory = false
    }

    fun getSBDateFromItemName(text: String): List<String>? = timePattern.matchMatcher(text) {
        listOf(group("year"), group("month"), group("day"))
    }

    fun getSBTimeFor(text: String): Long? {
        val (year, month, day) = getSBDateFromItemName(text) ?: return null
        val monthNr = SkyBlockTime.getSBMonthByName(month)

        return SkyBlockTime(year.toInt(), monthNr, day.toInt()).toMillis()
    }

    fun addContest(time: Long, item: ItemStack) {
        contests.putIfAbsent(time, createContest(time, item))
    }

    private fun createContest(time: Long, item: ItemStack): FarmingContest {
        val lore = item.getLore()

        val crop = cropPattern.firstMatcher(lore) {
            CropType.getByName(group("crop"))
        } ?: error("Crop not found in lore!")

        val brackets = buildMap {
            for (bracket in ContestBracket.entries) {
                val amount = bracket.bracketPattern.firstMatcher(lore) {
                    group("amount").formatInt()
                } ?: continue
                put(bracket, amount)
            }
        }

        return FarmingContest(time, crop, brackets)
    }

    fun getContestAtTime(time: Long) = contests[time]

    fun getContestsOfType(crop: CropType) = contests.values.filter { it.crop == crop }

    fun calculateAverages(crop: CropType): Pair<Int, Map<ContestBracket, Int>> {
        var amount = 0
        val crops = mutableMapOf<ContestBracket, Int>()
        val contests = mutableMapOf<ContestBracket, Int>()
        for (contest in getContestsOfType(crop).associateWith { it.time }.sortedDesc().keys) {
            amount++
            val brackets = contest.brackets
            for ((bracket, count) in brackets) {
                val old = crops.getOrDefault(bracket, 0)
                crops[bracket] = count + old
                contests.addOrPut(bracket, 1)
            }
            if (amount == 10) break
        }
        return Pair(amount, crops.mapValues { (bracket, counter) -> counter / contests[bracket]!! })
    }
}
