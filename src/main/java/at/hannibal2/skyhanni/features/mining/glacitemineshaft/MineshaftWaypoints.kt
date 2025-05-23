package at.hannibal2.skyhanni.features.mining.glacitemineshaft

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.PartyApi
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.minecraft.KeyPressEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.RenderUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import net.minecraft.client.Minecraft
import kotlin.time.Duration.Companion.milliseconds

// TODO rename to something else to reduce confusion
@SkyHanniModule
object MineshaftWaypoints {
    private val config get() = SkyHanniMod.feature.mining.glaciteMineshaft

    private const val BLOCKS_FORWARD: Int = 7

    val waypoints = mutableListOf<MineshaftWaypoint>()
    private var timeLastShared = SimpleTimeMark.farPast()

    @HandleEvent
    fun onWorldChange() {
        waypoints.clear()
    }

    @HandleEvent
    fun onIslandChange(event: IslandChangeEvent) {
        if (event.newIsland != IslandType.MINESHAFT) return

        val playerLocation = LorenzVec.getBlockBelowPlayer()

        if (config.mineshaftWaypoints.entranceLocation) {
            waypoints.add(MineshaftWaypoint(waypointType = MineshaftWaypointType.ENTRANCE, location = playerLocation))
        }

        if (config.mineshaftWaypoints.ladderLocation) {
            val vec = MinecraftCompat.localPlayer.horizontalFacing.directionVec
            val location = playerLocation
                // Move 7 blocks in front of the player to be in the ladder shaft
                .add(x = vec.x * BLOCKS_FORWARD, z = vec.z * BLOCKS_FORWARD)
                // Adjust 2 blocks to the right to be in the center of the ladder shaft
                .add(x = vec.z * -2, z = vec.x * 2)
                // Move 15 blocks down to be at the bottom of the ladder shaft
                .add(y = -15)
            waypoints.add(MineshaftWaypoint(waypointType = MineshaftWaypointType.LADDER, location = location))
        }
    }

    @HandleEvent
    fun onKeyPress(event: KeyPressEvent) {
        if (Minecraft.getMinecraft().currentScreen != null) return
        if (event.keyCode != config.shareWaypointLocation) return
        if (timeLastShared.passedSince() < 500.milliseconds) return

        val closestWaypoint = waypoints.filter { it.location.distanceToPlayer() <= 5 }
            .minByOrNull { it.location.distanceToPlayer() } ?: return

        timeLastShared = SimpleTimeMark.now()
        val location = closestWaypoint.location
        val (x, y, z) = location.toDoubleArray().map { it.toInt() }
        val type = closestWaypoint.waypointType.displayText

        val message = "x: $x, y: $y, z: $z | ($type)"

        if (PartyApi.partyMembers.isNotEmpty()) {
            HypixelCommands.partyChat(message)
        } else {
            HypixelCommands.allChat(message)
        }
    }

    @HandleEvent
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (waypoints.isEmpty()) return

        waypoints
            .filter {
                (it.isCorpse && config.corpseLocator.enabled) || (!it.isCorpse && config.mineshaftWaypoints.enabled)
            }
            .forEach {
                event.drawWaypointFilled(it.location, it.waypointType.color.toColor(), seeThroughBlocks = true)
                event.drawDynamicText(it.location, "§e${it.waypointType.displayText}", 1.0)
            }
    }

    fun isEnabled() = IslandType.MINESHAFT.isInIsland() && config.mineshaftWaypoints.enabled
}
