package at.hannibal2.skyhanni.features.rift.everywhere.motes

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.ReceiveParticleEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.features.rift.RiftApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.RenderUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.editCopy
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.util.EnumParticleTypes

@SkyHanniModule
object RiftMotesOrb {

    private val config get() = RiftApi.config.motesOrbs
    private val enabled get() = config.enabled

    /**
     * REGEX-TEST: §5§lORB! §r§dPicked up §r§5+10 Motes§r§d!
     * REGEX-TEST: §5§lORB! §r§dPicked up §r§5+25 Motes§r§d, recovered §r§a+2ф Rift Time§r§d!
     */
    private val motesPattern by RepoPattern.pattern(
        "rift.everywhere.motesorb",
        "§5§lORB! §r§dPicked up §r§5+.* Motes§r§d.*",
    )

    private var motesOrbs = emptyList<MotesOrb>()

    class MotesOrb(
        var location: LorenzVec,
        var counter: Int = 0,
        var startTime: Long = System.currentTimeMillis(),
        var lastTime: Long = System.currentTimeMillis(),
        var isOrb: Boolean = false,
        var pickedUp: Boolean = false,
    )

    @HandleEvent(onlyOnIsland = IslandType.THE_RIFT)
    fun onReceiveParticle(event: ReceiveParticleEvent) {
        if (!enabled) return
        val location = event.location.add(-0.5, 0.0, -0.5)

        if (event.type == EnumParticleTypes.SPELL_MOB) {
            val orb =
                motesOrbs.find { it.location.distance(location) < 3 } ?: MotesOrb(location).also {
                    motesOrbs = motesOrbs.editCopy { add(it) }
                }

            orb.location = location
            orb.lastTime = System.currentTimeMillis()
            orb.counter++
            orb.pickedUp = false
            if (config.hideParticles && orb.isOrb) {
                event.cancel()
            }
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.THE_RIFT)
    fun onChat(event: SkyHanniChatEvent) {
        motesPattern.matchMatcher(event.message) {
            motesOrbs.minByOrNull { it.location.distanceToPlayer() }?.let {
                it.pickedUp = true
            }
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.THE_RIFT)
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!enabled) return

        motesOrbs = motesOrbs.editCopy { removeIf { System.currentTimeMillis() > it.lastTime + 2000 } }

        for (orb in motesOrbs) {
            val ageInSeconds = (System.currentTimeMillis() - orb.startTime).toDouble() / 1000
            if (ageInSeconds < 0.5) continue

            val particlesPerSecond = (orb.counter.toDouble() / ageInSeconds).roundTo(1)
            if (particlesPerSecond < 60 || particlesPerSecond > 90) continue
            orb.isOrb = true

            if (System.currentTimeMillis() > orb.lastTime + 300) {
                orb.pickedUp = true
            }

            val location = orb.location.up(0.5)
            val sizeOffset = (5 - config.size) * -0.1
            val color = if (orb.pickedUp) LorenzColor.GRAY else LorenzColor.LIGHT_PURPLE
            val text = color.getChatColor() + "Motes Orb"
            event.drawDynamicText(location, text, 1.5 + sizeOffset, ignoreBlocks = false)
            event.drawWaypointFilled(location, color.toColor(), extraSize = sizeOffset)
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(9, "rift.area.motesOrbsConfig", "rift.area.motesOrbs")
    }
}
