package at.hannibal2.skyhanni.features.cosmetics

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.enums.OutsideSBFeature
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniTickEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.LocationUtils
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.PlayerUtils
import at.hannibal2.skyhanni.utils.RenderUtils.draw3DLine
import at.hannibal2.skyhanni.utils.RenderUtils.exactLocation
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SpecialColor.toSpecialColor
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.editCopy
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import java.awt.Color
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object CosmeticFollowingLine {

    private val config get() = SkyHanniMod.feature.gui.cosmetic.followingLine

    private var locations = mapOf<LorenzVec, LocationSpot>()
    private var latestLocations = mapOf<LorenzVec, LocationSpot>()

    class LocationSpot(val time: SimpleTimeMark, val onGround: Boolean)

    @HandleEvent
    fun onWorldChange() {
        locations = emptyMap()
    }

    @HandleEvent
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!isEnabled()) return

        updateClose(event)

        val firstPerson = PlayerUtils.isFirstPersonView()
        val color = config.lineColor.toSpecialColor()

        renderClose(event, firstPerson, color)
        renderFar(event, firstPerson, color)
    }

    private fun renderFar(
        event: SkyHanniRenderWorldEvent,
        firstPerson: Boolean,
        color: Color,
    ) {
        val last7 = locations.keys.toList().takeLast(7)
        val last2 = locations.keys.toList().takeLast(2)

        locations.keys.zipWithNext { a, b ->
            locations[b]?.let {
                if (firstPerson && !it.onGround && b in last7) {
                    // Do not render the line in the face, keep more distance while the line is in the air
                    return
                }
                if (b in last2 && it.time.passedSince() < 400.milliseconds) {
                    // Do not render the line directly next to the player, prevent laggy design
                    return
                }
                event.draw3DLine(a, b, color, it.getWidth(), !config.behindBlocks)
            }
        }
    }

    private fun updateClose(event: SkyHanniRenderWorldEvent) {
        val playerLocation = event.exactLocation(MinecraftCompat.localPlayer).up(0.3)

        latestLocations = latestLocations.editCopy {
            val locationSpot = LocationSpot(SimpleTimeMark.now(), MinecraftCompat.localPlayer.onGround)
            this[playerLocation] = locationSpot
            values.removeIf { it.time.passedSince() > 600.milliseconds }
        }
    }

    private fun renderClose(event: SkyHanniRenderWorldEvent, firstPerson: Boolean, color: Color) {
        if (firstPerson && latestLocations.any { !it.value.onGround }) return


        latestLocations.keys.zipWithNext { a, b ->
            latestLocations[b]?.let {
                event.draw3DLine(a, b, color, it.getWidth(), !config.behindBlocks)
            }
        }
    }

    private fun LocationSpot.getWidth(): Int {
        val millis = time.passedSince().inWholeMilliseconds
        val percentage = millis.toDouble() / (config.secondsAlive * 1000.0)
        val maxWidth = config.lineWidth
        val lineWidth = 1 + maxWidth - percentage * maxWidth
        return lineWidth.toInt().coerceAtLeast(1)
    }

    @HandleEvent
    fun onTick(event: SkyHanniTickEvent) {
        if (!isEnabled()) return

        if (event.isMod(5)) {
            locations = locations.editCopy { values.removeIf { it.time.passedSince() > config.secondsAlive.seconds } }

            // Safety check to not cause lags
            while (locations.size > 5_000) {
                locations = locations.editCopy { remove(keys.first()) }
            }
        }

        if (event.isMod(2)) {
            val playerLocation = LocationUtils.playerLocation().up(0.3)

            locations.keys.lastOrNull()?.let {
                if (it.distance(playerLocation) < 0.1) return
            }

            locations = locations.editCopy {
                this[playerLocation] = LocationSpot(SimpleTimeMark.now(), MinecraftCompat.localPlayer.onGround)
            }
        }
    }

    private fun isEnabled() = (LorenzUtils.inSkyBlock || OutsideSBFeature.FOLLOWING_LINE.isSelected()) && config.enabled

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(9, "misc.cosmeticConfig", "misc.cosmetic")
        event.move(9, "misc.cosmeticConfig.followingLineConfig", "misc.cosmetic.followingLine")
        event.move(9, "misc.cosmeticConfig.arrowTrailConfig", "misc.cosmetic.arrowTrail")
        event.move(31, "misc.cosmetic", "gui.cosmetic")
    }
}
