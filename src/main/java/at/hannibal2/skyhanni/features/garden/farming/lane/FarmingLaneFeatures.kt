package at.hannibal2.skyhanni.features.garden.farming.lane

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.garden.GardenToolChangeEvent
import at.hannibal2.skyhanni.events.garden.farming.FarmingLaneSwitchEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.features.garden.farming.lane.FarmingLaneApi.getValue
import at.hannibal2.skyhanni.features.garden.farming.lane.FarmingLaneApi.setValue
import at.hannibal2.skyhanni.features.misc.MovementSpeedDisplay
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.LocationUtils
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.RenderUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.RenderUtils.renderStrings
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.SoundUtils.playSound
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.TimeUtils.ticks
import kotlin.math.absoluteValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object FarmingLaneFeatures {
    val config get() = FarmingLaneApi.config

    private var currentPosition: Double? = null
    private var currentDistance = 0.0

    private var display = listOf<String>()
    private var titleContext: TitleManager.TitleContext? = null
    private var timeRemaining: Duration? = null
    private var lastSpeed = 0.0
    private var lastTimeFarming = SimpleTimeMark.farPast()
    private var lastPlaySound = SimpleTimeMark.farPast()
    private var lastDirection = 0
    private var movementState = MovementState.CALCULATING

    enum class MovementState(val label: String) {
        NOT_MOVING("§ePaused"),
        TOO_SLOW("§cToo slow!"),
        CALCULATING("§aCalculating.."),
        NORMAL(""),
    }

    @HandleEvent
    fun onFarmingLaneSwitch(event: FarmingLaneSwitchEvent) {
        display = emptyList()
    }

    @HandleEvent
    fun onGardenToolChange(event: GardenToolChangeEvent) {
        display = emptyList()
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onTick() {
        if (!config.distanceDisplay && !config.laneSwitchNotification.enabled) return

        if (!calculateDistance()) return
        if (!GardenApi.isCurrentlyFarming()) return

        if (calculateSpeed()) {
            showWarning()
        }

        if (config.distanceDisplay) {
            display = buildList {
                add("§7Distance until switch: §e${currentDistance.roundTo(1)}")

                val normal = movementState == MovementState.NORMAL
                val color = if (normal) "§b" else "§8"
                val timeRemaining = timeRemaining ?: return@buildList
                val format = timeRemaining.format(showMilliSeconds = timeRemaining < 20.seconds)
                val suffix = if (!normal) {
                    " §7(${movementState.label}§7)"
                } else ""
                add("§7Time remaining: $color$format$suffix")
                if (MovementSpeedDisplay.usingSoulsandSpeed) {
                    add("§7Using inaccurate soul sand speed!")
                }
            }
        }
    }

    private fun calculateDistance(): Boolean {
        val lane = FarmingLaneApi.currentLane ?: return false
        val min = lane.min
        val max = lane.max
        val position = lane.direction.getValue(LocationUtils.playerLocation())
        val outside = position !in min..max
        if (outside) {
            display = emptyList()
            return false
        }

        val direction = calculateDirection(position) ?: return false

        currentDistance = when (direction) {
            1 -> (min - position).absoluteValue
            -1 -> (max - position).absoluteValue
            else -> currentDistance
        }

        if (direction != lastDirection) {
            // reset farming time, to prevent wrong lane warnings
            lastTimeFarming = SimpleTimeMark.farPast()
            lastDirection = direction
        }
        return true
    }

    private fun calculateDirection(newPosition: Double): Int? {
        val position = currentPosition ?: run {
            currentPosition = newPosition
            return null
        }
        currentPosition = newPosition

        val diff = position - newPosition
        return if (diff > 0) {
            1
        } else if (diff < 0) {
            -1
        } else {
            0
        }
    }

    private fun showWarning() {
        with(config.laneSwitchNotification) {
            if (!enabled) return
            titleContext = when (titleContext) {
                null -> TitleManager.sendTitle(
                    text.replace("&", "§"),
                    duration = secondsBefore.seconds,
                    weight = 1.1,
                )
                else -> titleContext.takeIf { it?.alive == true }
            }
            if (lastPlaySound.passedSince() >= sound.repeatDuration.ticks) {
                lastPlaySound = SimpleTimeMark.now()
                playUserSound()
            }
        }
    }

    private var sameSpeedCounter = 0

    private fun calculateSpeed(): Boolean {
        val speed = MovementSpeedDisplay.speed.roundTo(2)
        movementState = calculateMovementState(speed)
        if (movementState != MovementState.NORMAL) return false

        val timeRemaining = (currentDistance / speed).seconds
        FarmingLaneFeatures.timeRemaining = timeRemaining
        val warnAt = config.laneSwitchNotification.secondsBefore.seconds
        if (timeRemaining >= warnAt) {
            lastTimeFarming = SimpleTimeMark.now()
            return false
        }

        // When the player was not inside the farm previously
        return lastTimeFarming.passedSince() < warnAt
    }

    private fun calculateMovementState(speed: Double): MovementState {
        if (lastSpeed != speed) {
            lastSpeed = speed
            sameSpeedCounter = 0
        }
        sameSpeedCounter++

        if (speed == 0.0 && sameSpeedCounter > 1) {
            return MovementState.NOT_MOVING
        }
        val speedTooSlow = speed < 1
        if (speedTooSlow && sameSpeedCounter > 5) {
            return MovementState.TOO_SLOW
        }
        // only calculate the time if the speed has not changed
        if (!MovementSpeedDisplay.usingSoulsandSpeed) {
            if (sameSpeedCounter < 6) {
                return MovementState.CALCULATING
            }
        }

        return MovementState.NORMAL
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!config.cornerWaypoints) return

        val lane = FarmingLaneApi.currentLane ?: return
        val direction = lane.direction
        val location = LocationUtils.playerLocation()
        val min = direction.setValue(location, lane.min).capAtBuildHeight()
        val max = direction.setValue(location, lane.max).capAtBuildHeight()

        event.drawWaypointFilled(min, LorenzColor.YELLOW.toColor(), beacon = true)
        event.drawDynamicText(min, "§eLane Corner", 1.5)
        event.drawWaypointFilled(max, LorenzColor.YELLOW.toColor(), beacon = true)
        event.drawDynamicText(max, "§eLane Corner", 1.5)
    }

    private fun LorenzVec.capAtBuildHeight(): LorenzVec = if (y > 76) copy(y = 76.0) else this

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!config.distanceDisplay) return

        config.distanceDisplayPosition.renderStrings(display, posLabel = "Lane Display")
    }

    @JvmStatic
    fun playUserSound() {
        with(config.laneSwitchNotification.sound) {
            SoundUtils.createSound(name, pitch).playSound()
        }
    }
}
