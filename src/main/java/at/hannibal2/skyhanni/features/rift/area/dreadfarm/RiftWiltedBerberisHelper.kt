package at.hannibal2.skyhanni.features.rift.area.dreadfarm

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.events.PlaySoundEvent
import at.hannibal2.skyhanni.events.ReceiveParticleEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniTickEvent
import at.hannibal2.skyhanni.features.rift.RiftApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.BlockUtils.getBlockAt
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.LocationUtils
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.RenderUtils.draw3DLine
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.RenderUtils.drawFilledBoundingBox
import at.hannibal2.skyhanni.utils.RenderUtils.expandBlock
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.editCopy
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import net.minecraft.init.Blocks
import net.minecraft.util.EnumParticleTypes
import java.awt.Color
import kotlin.time.Duration.Companion.milliseconds

@SkyHanniModule
object RiftWiltedBerberisHelper {

    private val config get() = RiftApi.config.area.dreadfarm.wiltedBerberis
    private var isOnFarmland = false
    private var hasFarmingToolInHand = false
    private var list = listOf<WiltedBerberis>()

    data class WiltedBerberis(var currentParticles: LorenzVec) {

        var previous: LorenzVec? = null
        var moving = true
        var y = 0.0
        var lastTime = SimpleTimeMark.now()
    }

    @HandleEvent
    fun onTick(event: SkyHanniTickEvent) {
        if (!isEnabled()) return
        if (!event.isMod(5)) return

        list = list.editCopy { removeIf { it.lastTime.passedSince() > 500.milliseconds } }

        hasFarmingToolInHand = InventoryUtils.getItemInHand()?.getInternalName() == RiftApi.farmingTool

        if (MinecraftCompat.localPlayer.onGround) {
            val block = LorenzVec.getBlockBelowPlayer().getBlockAt()
            val currentY = LocationUtils.playerLocation().y
            isOnFarmland = block == Blocks.farmland && (currentY % 1 == 0.0)
        }
    }

    private fun nearestBerberis(location: LorenzVec): WiltedBerberis? =
        list.filter { it.currentParticles.distanceSq(location) < 8 }
            .minByOrNull { it.currentParticles.distanceSq(location) }

    @HandleEvent
    fun onReceiveParticle(event: ReceiveParticleEvent) {
        if (!isEnabled()) return
        if (!hasFarmingToolInHand) return

        val location = event.location
        val berberis = nearestBerberis(location)

        if (event.type != EnumParticleTypes.FIREWORKS_SPARK) {
            if (config.hideParticles && berberis != null) {
                event.cancel()
            }
            return
        }

        if (config.hideParticles) {
            event.cancel()
        }

        if (berberis == null) {
            list = list.editCopy { add(WiltedBerberis(location)) }
            return
        }

        with(berberis) {
            val isMoving = currentParticles != location
            if (isMoving) {
                if (currentParticles.distance(location) > 3) {
                    previous = null
                    moving = true
                }
                if (!moving) {
                    previous = currentParticles
                }
            }
            if (!isMoving) {
                y = location.y - 1
            }

            moving = isMoving
            currentParticles = location
            lastTime = SimpleTimeMark.now()
        }
    }

    @HandleEvent
    fun onPlaySound(event: PlaySoundEvent) {
        if (!isMuteOthersSoundsEnabled()) return
        val soundName = event.soundName

        if (soundName == "mob.horse.donkey.death" || soundName == "mob.horse.donkey.hit") {
            event.cancel()
        }
    }

    @HandleEvent
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!isEnabled()) return
        if (!hasFarmingToolInHand) return

        if (config.onlyOnFarmland && !isOnFarmland) return

        for (berberis in list) {
            with(berberis) {
                if (currentParticles.distanceToPlayer() > 20) continue
                if (y == 0.0) continue

                val location = currentParticles.fixLocation(berberis)
                if (!moving) {
                    event.drawFilledBoundingBox(axisAlignedBB(location), Color.YELLOW, 0.7f)
                    event.drawDynamicText(location.up(), "§eWilted Berberis", 1.5, ignoreBlocks = false)
                } else {
                    event.drawFilledBoundingBox(axisAlignedBB(location), Color.WHITE, 0.5f)
                    previous?.fixLocation(berberis)?.let {
                        event.drawFilledBoundingBox(axisAlignedBB(it), Color.LIGHT_GRAY, 0.2f)
                        event.draw3DLine(it.add(0.5, 0.0, 0.5), location.add(0.5, 0.0, 0.5), Color.WHITE, 3, false)
                    }
                }
            }
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(60, "rift.area.dreadfarm.wiltedBerberis.hideparticles", "rift.area.dreadfarm.wiltedBerberis.hideParticles")
    }

    private fun axisAlignedBB(loc: LorenzVec) = loc.add(0.1, -0.1, 0.1).boundingToOffset(0.8, 1.0, 0.8).expandBlock()

    private fun LorenzVec.fixLocation(wiltedBerberis: WiltedBerberis): LorenzVec {
        val x = x - 0.5
        val y = wiltedBerberis.y
        val z = z - 0.5
        return LorenzVec(x, y, z)
    }

    private fun isEnabled() = RiftApi.inRift() && RiftApi.inDreadfarm() && config.enabled

    private fun isMuteOthersSoundsEnabled() = RiftApi.inRift() &&
        config.muteOthersSounds &&
        (RiftApi.inDreadfarm() || RiftApi.inWestVillage()) &&
        !(hasFarmingToolInHand && isOnFarmland)
}
