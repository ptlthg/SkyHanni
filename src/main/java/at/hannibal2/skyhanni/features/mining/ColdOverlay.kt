package at.hannibal2.skyhanni.features.mining

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.MiningApi.inColdIsland
import at.hannibal2.skyhanni.events.ColdUpdateEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.NumberUtil
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.compat.DrawContextUtils
import at.hannibal2.skyhanni.utils.compat.createResourceLocation
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.opengl.GL11
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object ColdOverlay {

    private val config get() = SkyHanniMod.feature.mining.coldOverlay

    private var cold = 0
    private var lastCold = 0
    private var lastColdUpdate = SimpleTimeMark.farPast()

    private val textureLocation = createResourceLocation("skyhanni", "cold_overlay.png")

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!isEnabled()) return
        val alpha = getColdAlpha()
        if (alpha == 0f) return

        DrawContextUtils.pushMatrix()
        GlStateManager.pushAttrib()

        GL11.glDepthMask(false)
        DrawContextUtils.translate(0f, 0f, -500f)
        GuiRenderUtils.drawTexturedRect(0f, 0f, textureLocation, alpha)

        GL11.glDepthMask(true)

        DrawContextUtils.popMatrix()
        GlStateManager.popAttrib()
    }

    // TODO fix small bug with high cold and low threshold having the same opacity than high cold and a b it smaller threshold
    private fun getColdAlpha(): Float {
        val coldInterp = NumberUtil.interpolate(cold.toFloat(), lastCold.toFloat(), lastColdUpdate.toMillis())
        val coldPercentage = (coldInterp - config.coldThreshold) / (100 - config.coldThreshold)
        return coldPercentage.coerceAtLeast(0f) * (config.maxAlpha / 100)
    }

    @HandleEvent
    fun onColdUpdate(event: ColdUpdateEvent) {
        val duration = if (event.cold == 0) 1.seconds else 0.seconds
        DelayedRun.runDelayed(duration) {
            lastCold = cold
            cold = event.cold
            lastColdUpdate = SimpleTimeMark.now()
        }
    }

    private fun isEnabled() = inColdIsland() && config.enabled
}
