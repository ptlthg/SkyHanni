package at.hannibal2.skyhanni.features.slayer.enderman

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.SlayerApi
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.events.CheckRenderEntityEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.ServerBlockChangeEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.mixins.hooks.RenderLivingEntityHelper
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ColorUtils.addAlpha
import at.hannibal2.skyhanni.utils.EntityUtils.canBeSeen
import at.hannibal2.skyhanni.utils.EntityUtils.getBlockInHand
import at.hannibal2.skyhanni.utils.EntityUtils.hasSkullTexture
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzLogger
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.RenderUtils.drawColor
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.RenderUtils.drawLineToEye
import at.hannibal2.skyhanni.utils.RenderUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.RenderUtils.exactLocation
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkullTextureHolder
import at.hannibal2.skyhanni.utils.SpecialColor.toSpecialColor
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.editCopy
import at.hannibal2.skyhanni.utils.compat.getStandHelmet
import at.hannibal2.skyhanni.utils.getLorenzVec
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.entity.monster.EntityEnderman
import net.minecraft.init.Blocks
import kotlin.time.Duration.Companion.seconds

// TODO replace all drawLineToEye with LineToMobHandler
@SkyHanniModule
object EndermanSlayerFeatures {

    private val config get() = SlayerApi.config.endermen
    private val beaconConfig get() = config.beacon
    private val endermenWithBeacons = mutableListOf<EntityEnderman>()
    private val flyingBeacons = mutableSetOf<EntityArmorStand>()
    private val nukekubiSkulls = mutableSetOf<EntityArmorStand>()
    private var sittingBeacon = mapOf<LorenzVec, SimpleTimeMark>()
    private val logger = LorenzLogger("slayer/enderman")

    private val NUKEKUBI_SKULL_TEXTURE by lazy { SkullTextureHolder.getTexture("MOB_NUKEKUBI") }

    @HandleEvent(onlyOnIsland = IslandType.THE_END)
    fun onCheckRender(event: CheckRenderEntityEvent<Entity>) {
        val entity = event.entity
        if (entity in endermenWithBeacons || entity in flyingBeacons) return

        if (entity is EntityEnderman && showBeacon() && hasBeaconInHand(entity) && entity.canBeSeen(15.0)) {
            endermenWithBeacons.add(entity)
            logger.log("Added enderman with beacon at ${entity.getLorenzVec()}")
        }

        if (entity is EntityArmorStand) {
            if (showBeacon()) {
                val stack = entity.getStandHelmet() ?: return
                if (stack.displayName == "Beacon" && entity.canBeSeen(15.0)) {
                    flyingBeacons.add(entity)
                    RenderLivingEntityHelper.setEntityColor(
                        entity,
                        beaconConfig.beaconColor.toSpecialColor().addAlpha(1),
                    ) {
                        beaconConfig.highlightBeacon
                    }
                    if (beaconConfig.showWarning) {
                        TitleManager.sendTitle("§4Beacon", duration = 2.seconds)
                    }
                    logger.log("Added flying beacons at ${entity.getLorenzVec()}")
                }
            }

            if (config.highlightNukekebi && entity.hasSkullTexture(NUKEKUBI_SKULL_TEXTURE) && entity !in nukekubiSkulls) {
                nukekubiSkulls.add(entity)
                RenderLivingEntityHelper.setEntityColor(
                    entity,
                    LorenzColor.GOLD.toColor().addAlpha(1),
                ) { config.highlightNukekebi }
                logger.log("Added Nukekubi skulls at ${entity.getLorenzVec()}")
            }
        }
    }

    private fun hasBeaconInHand(enderman: EntityEnderman) = enderman.getBlockInHand()?.block == Blocks.beacon

    private fun showBeacon() = beaconConfig.highlightBeacon || beaconConfig.showWarning || beaconConfig.showLine

    @HandleEvent(onlyOnIsland = IslandType.THE_END)
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (beaconConfig.highlightBeacon) {
            endermenWithBeacons.removeIf { it.isDead || !hasBeaconInHand(it) }

            for (location in endermenWithBeacons.map { it.getLorenzVec().add(-0.5, 0.2, -0.5) }) {
                event.drawColor(location, beaconConfig.beaconColor.toSpecialColor(), alpha = 0.5f)
            }
        }

        drawSittingBeacon(event)
        drawFlyingBeacon(event)
        drawNukekubiSkulls(event)
    }

    private fun drawNukekubiSkulls(event: SkyHanniRenderWorldEvent) {
        for (skull in nukekubiSkulls) {
            if (skull.isDead) continue
            if (config.highlightNukekebi) {
                event.drawDynamicText(
                    skull.getLorenzVec().add(-0.5, 1.5, -0.5),
                    "§6Nukekubi Skull",
                    1.6,
                    ignoreBlocks = false,
                    maxDistance = 20,
                )
            }
            if (config.drawLineToNukekebi) {
                val skullLocation = event.exactLocation(skull)
                // TODO remove visibility check once the skull stops moving
                if (!skull.canBeSeen(viewDistance = 20)) continue
                event.drawLineToEye(
                    skullLocation.up(),
                    LorenzColor.GOLD.toColor(),
                    3,
                    true,
                )
            }
        }
    }

    private fun drawFlyingBeacon(event: SkyHanniRenderWorldEvent) {
        for (beacon in flyingBeacons) {
            if (!beacon.canBeSeen()) continue
            if (beaconConfig.highlightBeacon) {
                val beaconLocation = event.exactLocation(beacon)
                event.drawDynamicText(beaconLocation.add(y = 1), "§4Beacon", 1.8)
            }

            if (beaconConfig.showLine) {
                val beaconLocation = event.exactLocation(beacon)
                event.drawLineToEye(
                    beaconLocation.add(0.5, 1.0, 0.5),
                    beaconConfig.lineColor.toSpecialColor(),
                    beaconConfig.lineWidth,
                    true,
                )
            }
        }
    }

    private fun drawSittingBeacon(event: SkyHanniRenderWorldEvent) {
        for ((location, time) in sittingBeacon) {
            if (location.distanceToPlayer() > 20) continue
            if (beaconConfig.showLine) {
                event.drawLineToEye(
                    location.add(0.5, 1.0, 0.5),
                    beaconConfig.lineColor.toSpecialColor(),
                    beaconConfig.lineWidth,
                    true,
                )
            }

            if (beaconConfig.highlightBeacon) {
                val duration = 5.seconds - time.passedSince()
                val durationFormat = duration.format(showMilliSeconds = true)
                event.drawColor(location, beaconConfig.beaconColor.toSpecialColor(), alpha = 1f)
                event.drawWaypointFilled(location, beaconConfig.beaconColor.toSpecialColor(), true, true)
                event.drawDynamicText(location.add(y = 1), "§4Beacon §b$durationFormat", 1.8)
            }
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.THE_END)
    fun onSecondPassed(event: SecondPassedEvent) {
        nukekubiSkulls.removeAll {
            if (it.isDead) {
                RenderLivingEntityHelper.removeEntityColor(it)
            }
            it.isDead
        }
        flyingBeacons.removeAll {
            if (it.isDead) {
                RenderLivingEntityHelper.removeEntityColor(it)
            }
            it.isDead
        }

        // Removing the beacon if It's still there after 7 seconds.
        // This is just a workaround for the cases where the ServerBlockChangeEvent don't detect the beacon despawn info.
        val toRemove = sittingBeacon.filter { it.value.passedSince() > 7.seconds }
        if (toRemove.isNotEmpty()) {
            sittingBeacon = sittingBeacon.editCopy {
                toRemove.keys.forEach { remove(it) }
            }
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.THE_END)
    fun onBlockChange(event: ServerBlockChangeEvent) {
        if (!showBeacon()) return

        val location = event.location
        if (event.new == "beacon") {
            val armorStand = flyingBeacons.find { location.distance(it.getLorenzVec()) < 3 }
            if (armorStand != null) {
                flyingBeacons.remove(armorStand)
                RenderLivingEntityHelper.removeEntityColor(armorStand)
                sittingBeacon = sittingBeacon.editCopy { this[location] = SimpleTimeMark.now() }
                logger.log("Replaced flying beacon with sitting beacon at $location")
            }
        } else {
            if (location in sittingBeacon) {
                logger.log("Removed sitting beacon $location")
                sittingBeacon = sittingBeacon.editCopy { remove(location) }
            }
        }
    }

    @HandleEvent
    fun onWorldChange() {
        endermenWithBeacons.clear()
        flyingBeacons.clear()
        nukekubiSkulls.clear()
        sittingBeacon = emptyMap()
        logger.log("Reset everything (world change)")
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(
            3,
            "slayer.endermanBeaconConfig.highlightBeacon",
            "slayer.endermen.endermanBeaconConfig.highlightBeacon",
        )
        event.move(3, "slayer.endermanBeaconConfig.beaconColor", "slayer.endermen.endermanBeaconConfig.beaconColor")
        event.move(3, "slayer.endermanBeaconConfig.showWarning", "slayer.endermen.endermanBeaconConfig.showWarning")
        event.move(3, "slayer.endermanBeaconConfig.showLine", "slayer.endermen.endermanBeaconConfig.showLine")
        event.move(3, "slayer.endermanBeaconConfig.lneColor", "slayer.endermen.endermanBeaconConfig.lineColor")
        event.move(3, "slayer.endermanBeaconConfig.lineWidth", "slayer.endermen.endermanBeaconConfig.lineWidth")
        event.move(3, "slayer.endermanHighlightNukekebi", "slayer.endermen.highlightNukekebi")
        event.move(9, "slayer.enderman.endermanBeaconConfig", "slayer.endermen.beacon")
    }
}
