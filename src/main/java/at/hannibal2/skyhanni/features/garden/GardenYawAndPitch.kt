package at.hannibal2.skyhanni.features.garden

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.enums.OutsideSBFeature
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.garden.GardenToolChangeEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.LocationUtils
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.RenderUtils.renderStrings
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object GardenYawAndPitch {

    private val config get() = GardenApi.config.yawPitchDisplay
    private var lastChange = SimpleTimeMark.farPast()
    private var lastYaw = 0f
    private var lastPitch = 0f

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!LorenzUtils.onHypixel) return
        if (!isEnabled()) return
        if (GardenApi.hideExtraGuis()) return
        if (GardenApi.toolInHand == null && !config.showWithoutTool) return

        val player = MinecraftCompat.localPlayer
        val yaw = LocationUtils.calculatePlayerYaw()
        val pitch = player.rotationPitch

        if (yaw != lastYaw || pitch != lastPitch) {
            lastChange = SimpleTimeMark.now()
        }
        lastYaw = yaw
        lastPitch = pitch

        if (!config.showAlways && lastChange.passedSince() > config.timeout.seconds) return

        val yawText = yaw.roundTo(config.yawPrecision).toBigDecimal().toPlainString()
        val pitchText = pitch.roundTo(config.pitchPrecision).toBigDecimal().toPlainString()
        val displayList = listOf(
            "§aYaw: §f$yawText",
            "§aPitch: §f$pitchText",
        )
        if (GardenApi.inGarden()) {
            config.pos.renderStrings(displayList, posLabel = "Yaw and Pitch")
        } else {
            config.posOutside.renderStrings(displayList, posLabel = "Yaw and Pitch")
        }
    }

    @HandleEvent
    fun onGardenToolChange(event: GardenToolChangeEvent) {
        lastChange = SimpleTimeMark.farPast()
    }

    private fun isEnabled() =
        config.enabled && (
            (OutsideSBFeature.YAW_AND_PITCH.isSelected() && !LorenzUtils.inSkyBlock) ||
                (LorenzUtils.inSkyBlock && (GardenApi.inGarden() || config.showOutsideGarden))
            )

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(18, "garden.yawPitchDisplay.showEverywhere", "garden.yawPitchDisplay.showOutsideGarden")
    }
}
