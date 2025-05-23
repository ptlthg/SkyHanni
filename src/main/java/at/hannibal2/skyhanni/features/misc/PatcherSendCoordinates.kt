package at.hannibal2.skyhanni.features.misc

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniTickEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.LocationUtils
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzLogger
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RenderUtils.drawColor
import at.hannibal2.skyhanni.utils.RenderUtils.drawString
import at.hannibal2.skyhanni.utils.RenderUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.SpecialColor.toSpecialColor
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object PatcherSendCoordinates {

    private val config get() = SkyHanniMod.feature.misc.patcherCoordsWaypoint
    private val patcherBeacon = mutableListOf<PatcherBeacon>()
    private val logger = LorenzLogger("misc/patchercoords")

    /**
     * REGEX-TEST: hannibal2: x: 2, y: 3, z: 4
     * REGEX-TEST: hannibal2: x: 2, y: 3, z: 4broken
     * REGEX-TEST: hannibal2: x: 2, y: 3, z: 4 extra text
     */
    private val coordinatePattern by RepoPattern.pattern(
        "misc.patchercoords.coords",
        "(?<playerName>.*): [xX]: (?<x>[0-9.-]+),? [yY]: (?<y>[0-9.-]+),? [zZ]: (?<z>[0-9.-]+(?: .*)?)",
    )

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!config.enabled) return

        val message = event.message.removeColor()
        coordinatePattern.matchMatcher(message) {
            var description = group("playerName").split(" ").last()
            val x = group("x").toFloat()
            val y = group("y").toFloat()

            val end = group("z")
            val z = if (end.contains(" ")) {
                val split = end.split(" ")
                val extra = split.drop(1).joinToString(" ").take(50)
                description += " $extra"

                split.first().toFloat()
            } else end.toFloat()
            patcherBeacon.add(PatcherBeacon(LorenzVec(x, y, z), description, System.currentTimeMillis() / 1000))
            logger.log("got Patcher coords and username")
        }
    }

    @HandleEvent(priority = HandleEvent.HIGH)
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!config.enabled) return

        for (beacon in patcherBeacon) {
            val location = beacon.location
            val distance = location.distanceToPlayer()
            val formattedDistance = distance.toInt().addSeparators()

            event.drawColor(location, LorenzColor.DARK_GREEN, alpha = 1f)
            event.drawWaypointFilled(location, config.color.toSpecialColor(), true, true)
            event.drawString(location.blockCenter(), beacon.name + " §e[${formattedDistance}m]", true, LorenzColor.DARK_BLUE.toColor())
        }
    }

    @HandleEvent
    fun onTick(event: SkyHanniTickEvent) {
        if (!event.isMod(10)) return

        val location = LocationUtils.playerLocation()
        // removed Patcher beacon!
        patcherBeacon.removeIf { System.currentTimeMillis() / 1000 > it.time + 5 && location.distanceIgnoreY(it.location) < 5 }

        // removed Patcher beacon after time!
        patcherBeacon.removeIf { System.currentTimeMillis() / 1000 > it.time + config.duration }
    }

    @HandleEvent
    fun onWorldChange() {
        patcherBeacon.clear()
        logger.log("Reset everything (world change)")
    }

    data class PatcherBeacon(val location: LorenzVec, val name: String, val time: Long)

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(39, "misc.patcherSendCoordWaypoint", "misc.patcherCoordsWaypoint.enabled")
    }
}
