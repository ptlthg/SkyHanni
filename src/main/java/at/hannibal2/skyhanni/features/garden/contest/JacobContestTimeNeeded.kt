package at.hannibal2.skyhanni.features.garden.contest

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.InventoryUpdatedEvent
import at.hannibal2.skyhanni.features.garden.CropType
import at.hannibal2.skyhanni.features.garden.FarmingFortuneDisplay.getLatestTrueFarmingFortune
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.features.garden.farming.GardenCropSpeed.getLatestBlocksPerSecond
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.StringUtils.firstLetterUppercase
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.sorted
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addItemStack
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addString
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.RenderableUtils.addRenderableButton
import at.hannibal2.skyhanni.utils.renderables.addLine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object JacobContestTimeNeeded {

    private val config get() = GardenApi.config
    private var display = emptyList<Renderable>()
    private var currentBracket = ContestBracket.GOLD

    @HandleEvent(priority = HandleEvent.LOW)
    fun onInventoryUpdated(event: InventoryUpdatedEvent) {
        if (FarmingContestApi.inInventory) {
            update()
        }
    }

    private fun update() {
        val sorted = mutableMapOf<CropType, Duration>()
        val map = mutableMapOf<CropType, Renderable>()
        for (crop in CropType.entries) {
            testCrop(crop, sorted, map)
        }

        display = buildList {
            addString("§e§lTime Needed for ${currentBracket.displayName.firstLetterUppercase()} §eMedal!")

            addRenderableButton<ContestBracket>(
                label = "Bracket",
                getName = { type -> type.name.firstLetterUppercase() },
                current = currentBracket,
                onChange = {
                    currentBracket = it
                    update()
                },
            )
            addString("")
            for (crop in sorted.sorted().keys) {
                val text = map[crop]!!
                addLine {
                    addItemStack(crop.icon)
                    add(text)
                }
            }
        }
    }

    private fun testCrop(
        crop: CropType,
        sorted: MutableMap<CropType, Duration>,
        map: MutableMap<CropType, Renderable>,
    ) {

        val bps = crop.getBps()
        if (bps == null) {
            sorted[crop] = Duration.INFINITE
            map[crop] = Renderable.hoverTips(
                "§9${crop.cropName} §cNo speed data!",
                listOf("§cFarm ${crop.cropName} to show data!"),
            )
            return
        }
        val ff = crop.getLatestTrueFarmingFortune()
        if (ff == null) {
            sorted[crop] = Duration.INFINITE
            map[crop] = Renderable.hoverTips(
                "§9${crop.cropName} §cNo Farming Fortune data!",
                listOf("§cHold a ${crop.cropName} specific", "§cfarming tool in hand to show data!"),
            )
            return
        }

        val averages = FarmingContestApi.calculateAverages(crop).second
        if (averages.isEmpty()) {
            sorted[crop] = Duration.INFINITE - 2.milliseconds
            map[crop] = Renderable.hoverTips(
                "§9${crop.cropName} §cNo contest data!",
                listOf(
                    "§cOpen more pages or participate",
                    "§cin a ${crop.cropName} Contest to show data!",
                ),
            )
            return
        }

        val speed = (ff * crop.baseDrops * bps / 100).roundTo(1).toInt()

        renderCrop(speed, crop, averages, sorted, map)
    }

    private fun renderCrop(
        speed: Int,
        crop: CropType,
        averages: Map<ContestBracket, Int>,
        sorted: MutableMap<CropType, Duration>,
        map: MutableMap<CropType, Renderable>,
    ) {
        var lowBPSWarning = listOf<String>()
        val rawSpeed = speed.toDouble()
        val speedForFormula = crop.getBps()?.let {
            if (it < 15) {
                val v = rawSpeed / it
                (v * 19.9).toInt()
            } else speed
        } ?: speed
        var showLine = ""
        val brackets = mutableListOf<String>()
        for (bracket in ContestBracket.entries) {
            val amount = averages[bracket]
            if (amount == null) {
                sorted[crop] = Duration.INFINITE - 1.milliseconds
                brackets.add("${bracket.displayName} §cBracket not revealed!")
                showLine = "§9${crop.cropName} §cBracket not revealed!"
                continue
            }
            val timeInMinutes = (amount.toDouble() / speedForFormula).seconds
            val formatDuration = timeInMinutes.format()
            val color = if (timeInMinutes < 20.minutes) "§b" else "§c"
            var marking = ""
            var bracketText = "${bracket.displayName} $color$formatDuration"
            var blocksPerSecond = crop.getBps()
            if (blocksPerSecond == null) {
                marking += "§0§l !" // hoping this never shows
                blocksPerSecond = 19.9
                lowBPSWarning = listOf("§cYour Blocks/second is too low,", "§cshowing 19.9 Blocks/second instead!")
            } else {
                if (blocksPerSecond < 15.0) {
                    marking += "§4§l !"
                    blocksPerSecond = 19.9
                    lowBPSWarning =
                        listOf("§cYour Blocks/second is too low,", "§cshowing 19.9 Blocks/second instead!")
                } else {
                    marking += " "
                }
            }
            val line = if (timeInMinutes < 20.minutes) {
                // TODO use table: first row crop name, second row "in <time>" or error msg
                "§9${crop.cropName} §7in §b$formatDuration" + marking
            } else {
                val cropFF = crop.getLatestTrueFarmingFortune() ?: 0.0
                val cropsPerSecond = amount.toDouble() / blocksPerSecond / 60
                val ffNeeded = cropsPerSecond * 100 / 20 / crop.baseDrops
                val missing = (ffNeeded - cropFF).toInt()
                bracketText += " §7(Need ${missing.addSeparators()} FF more)"
                "§9${crop.cropName} §cNo ${currentBracket.displayName} §cmedal possible!" + marking
            }
            brackets.add(bracketText)
            if (bracket == currentBracket) {
                sorted[crop] = timeInMinutes
                showLine = line
            }
        }
        map[crop] = Renderable.hoverTips(
            showLine,
            buildList {
                add("§7Time Needed for §9${crop.cropName} Medals§7:")
                addAll(brackets)
                add("")
                val latestFF = crop.getLatestTrueFarmingFortune() ?: 0.0
                add("§7Latest FF: §e${(latestFF).addSeparators()}")
                val bps = crop.getBps()?.roundTo(1) ?: 0
                add("§7${addBpsTitle()}§e${bps.addSeparators()}")
                addAll(lowBPSWarning)
            },
        )
    }

    private fun addBpsTitle() = if (config.jacobContestCustomBps) "Custom Blocks/Second: " else "Your Blocks/Second: "

    private fun CropType.getBps() = if (config.jacobContestCustomBps) {
        config.jacobContestCustomBpsValue
    } else getLatestBlocksPerSecond()

    @HandleEvent
    fun onBackgroundDraw(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (!isEnabled()) return
        if (!FarmingContestApi.inInventory) return
        config.jacobContestTimesPosition.renderRenderables(display, posLabel = "Jacob Contest Time Needed")
    }

    fun isEnabled() = LorenzUtils.inSkyBlock && config.jacobContestTimes
}
