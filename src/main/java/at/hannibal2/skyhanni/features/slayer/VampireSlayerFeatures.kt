package at.hannibal2.skyhanni.features.slayer

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.data.ClickType
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.SlayerApi
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.events.ReceiveParticleEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.SkyHanniRenderEntityEvent
import at.hannibal2.skyhanni.events.entity.EntityClickEvent
import at.hannibal2.skyhanni.events.entity.EntityDeathEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniTickEvent
import at.hannibal2.skyhanni.features.rift.RiftApi
import at.hannibal2.skyhanni.mixins.hooks.RenderLivingEntityHelper
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ColorUtils.addAlpha
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.EntityUtils.canBeSeen
import at.hannibal2.skyhanni.utils.EntityUtils.getAllNameTagsInRadiusWith
import at.hannibal2.skyhanni.utils.EntityUtils.hasSkullTexture
import at.hannibal2.skyhanni.utils.EntityUtils.isNpc
import at.hannibal2.skyhanni.utils.LocationUtils
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzUtils.baseMaxHealth
import at.hannibal2.skyhanni.utils.RenderUtils.draw3DLine
import at.hannibal2.skyhanni.utils.RenderUtils.drawColor
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.RenderUtils.drawLineToEye
import at.hannibal2.skyhanni.utils.RenderUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.RenderUtils.exactLocation
import at.hannibal2.skyhanni.utils.RenderUtils.exactPlayerEyeLocation
import at.hannibal2.skyhanni.utils.SkullTextureHolder
import at.hannibal2.skyhanni.utils.SpecialColor.toSpecialColor
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.editCopy
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import at.hannibal2.skyhanni.utils.toLorenzVec
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.util.EnumParticleTypes
import java.awt.Color
import kotlin.time.Duration.Companion.milliseconds

@SkyHanniModule
object VampireSlayerFeatures {

    private val config get() = SlayerApi.config.vampire
    private val configOwnBoss get() = config.ownBoss
    private val configOtherBoss get() = config.othersBoss
    private val configCoopBoss get() = config.coopBoss
    private val configBloodIchor get() = config.bloodIchor
    private val configKillerSpring get() = config.killerSpring

    private val entityList = mutableListOf<EntityLivingBase>()
    private val taggedEntityList = mutableListOf<Int>()
    private var standList = mapOf<EntityArmorStand, EntityOtherPlayerMP>()

    // Nicked support
    private val username
        get() = EntityUtils.getEntities<EntityPlayerSP>().firstOrNull()?.name ?: error("own player is null")

    private val BLOOD_ICHOR_TEXTURE by lazy { SkullTextureHolder.getTexture("BLOOD_ICHOR") }
    private val KILLER_SPRING_TEXTURE by lazy { SkullTextureHolder.getTexture("KILLER_SPRING") }
    private var nextClawSend = 0L

    @HandleEvent
    fun onTick(event: SkyHanniTickEvent) {
        if (!isEnabled()) return
        if (!event.isMod(5)) return
        val start = LocationUtils.playerLocation()
        if (configOwnBoss.highlight || configOtherBoss.highlight || configCoopBoss.highlight) {
            for (player in EntityUtils.getEntities<EntityOtherPlayerMP>()) {
                val distance = start.distance(player.position.toLorenzVec())
                if (distance <= 15)
                    player.process()
            }
        }
        if (configBloodIchor.highlight || configKillerSpring.highlight) {
            for (stand in EntityUtils.getEntities<EntityArmorStand>()) {
                val vec = stand.position.toLorenzVec()
                val distance = start.distance(vec)
                val isIchor = stand.hasSkullTexture(BLOOD_ICHOR_TEXTURE)
                if (isIchor || stand.hasSkullTexture(KILLER_SPRING_TEXTURE)) {
                    val color =
                        (if (isIchor) configBloodIchor.color else configKillerSpring.color).toSpecialColor().addAlpha(config.withAlpha)
                    if (distance <= 15) {
                        RenderLivingEntityHelper.setEntityColor(
                            stand,
                            color,
                        ) { isEnabled() }
                        if (isIchor)
                            entityList.add(stand)
                    }
                }
            }
        }
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!isEnabled()) return
        entityList.editCopy { removeIf { it.isDead } }
    }

    private fun List<String>.spawnedByCoop(stand: EntityArmorStand): Boolean = any {
        var contain = false
        if (".*§(?:\\d|\\w)+Spawned by: §(?:\\d|\\w)(\\w*).*".toRegex().matches(stand.name)) {
            val name = ".*§(?:\\d|\\w)+Spawned by: §(?:\\d|\\w)(\\w*)".toRegex()
                .find(stand.name)?.groupValues?.get(1)
            contain = it == name
        }
        contain
    }

    private fun EntityOtherPlayerMP.process() {
        if (name != "Bloodfiend ") return

        if (configOwnBoss.twinClawsTitle || configOtherBoss.twinClawsTitle || configCoopBoss.twinClawsTitle) {
            for (stand in getAllNameTagsInRadiusWith("TWINCLAWS")) {
                if (!".*(?:§(?:\\d|\\w))+TWINCLAWS (?:§(?:\\w|\\d))+[0-9.,]+s.*".toRegex().matches(stand.name)) continue
                val coopList = configCoopBoss.coopMembers.split(",").toList()
                val containUser = getAllNameTagsInRadiusWith("Spawned by").any {
                    it.name.contains(username)
                }
                val containCoop = getAllNameTagsInRadiusWith("Spawned by").any {
                    configCoopBoss.highlight && coopList.spawnedByCoop(it)
                }
                val shouldSendTitle =
                    if (containUser && configOwnBoss.twinClawsTitle) true
                    else if (containCoop && configCoopBoss.twinClawsTitle) true
                    else taggedEntityList.contains(this.entityId) && configOtherBoss.twinClawsTitle

                if (!shouldSendTitle) continue
                DelayedRun.runDelayed(config.twinclawsDelay.milliseconds) {
                    if (nextClawSend < System.currentTimeMillis()) {
                        TitleManager.sendTitle(
                            "§6§lTWINCLAWS",
                            duration = (1750 - config.twinclawsDelay).milliseconds,
                        )
                        nextClawSend = System.currentTimeMillis() + 5_000
                    }
                }
            }
        }
        for (it in getAllNameTagsInRadiusWith("Spawned by")) {
            val coopList = configCoopBoss.coopMembers.split(",").toList()
            val containUser = it.name.contains(username)
            val containCoop = coopList.spawnedByCoop(it)
            val neededHealth = baseMaxHealth * 0.2f
            if (containUser && taggedEntityList.contains(entityId)) {
                taggedEntityList.remove(entityId)
            }
            val canUseSteak = health <= neededHealth
            val ownBoss = configOwnBoss.highlight && containUser && isNpc()
            val otherBoss = configOtherBoss.highlight && taggedEntityList.contains(entityId) && isNpc()
            val coopBoss = configCoopBoss.highlight && containCoop && isNpc()
            val shouldRender = if (ownBoss) true else if (otherBoss) true else coopBoss

            val color = when {
                canUseSteak && config.changeColorWhenCanSteak -> config.steakColor.color()
                ownBoss -> configOwnBoss.highlightColor.color()
                otherBoss -> configOtherBoss.highlightColor.color()
                coopBoss -> configCoopBoss.highlightColor.color()

                else -> Color.BLACK
            }

            val shouldSendSteakTitle =
                if (canUseSteak && configOwnBoss.steakAlert && containUser) true
                else if (canUseSteak && configOtherBoss.steakAlert && taggedEntityList.contains(entityId)) true
                else canUseSteak && configCoopBoss.steakAlert && containCoop

            if (shouldSendSteakTitle) {
                TitleManager.sendTitle("§c§lSTEAK!", duration = 300.milliseconds)
            }

            if (shouldRender) {
                RenderLivingEntityHelper.setEntityColorWithNoHurtTime(this, color) { isEnabled() }
                entityList.add(this)
            }
        }
    }

    private fun EntityOtherPlayerMP.isHighlighted(): Boolean {
        return entityList.contains(this) || taggedEntityList.contains(entityId)
    }

    private fun String.color(): Color {
        return this.toSpecialColor().addAlpha(config.withAlpha)
    }

    @HandleEvent(onlyOnIsland = IslandType.THE_RIFT)
    fun onEntityClick(event: EntityClickEvent) {
        if (!isEnabled()) return
        if (event.clickType != ClickType.LEFT_CLICK) return
        if (event.clickedEntity !is EntityOtherPlayerMP) return
        if (!event.clickedEntity.isNpc()) return
        val coopList = configCoopBoss.coopMembers.split(",").toList()
        val regexA = ".*§(?:\\d|\\w)+Spawned by: §(?:\\d|\\w)(\\w*).*".toRegex()
        val regexB = ".*§(?:\\d|\\w)+Spawned by: §(?:\\d|\\w)(\\w*)".toRegex()
        for (armorStand in event.clickedEntity.getAllNameTagsInRadiusWith("Spawned by")) {
            val containCoop = coopList.isNotEmpty() &&
                coopList.any {
                    var contain = false
                    if (regexA.matches(armorStand.name)) {
                        val name = regexB.find(armorStand.name)?.groupValues?.get(1)
                        contain = it == name
                    }
                    contain
                }
            if (armorStand.name.contains(username) || containCoop) return
            if (!taggedEntityList.contains(event.clickedEntity.entityId)) {
                taggedEntityList.add(event.clickedEntity.entityId)
            }
        }
    }

    @HandleEvent
    fun onEntityDeath(event: EntityDeathEvent<*>) {
        if (!isEnabled()) return
        val entity = event.entity
        if (entityList.contains(entity)) {
            entityList.remove(entity)
        }
        if (taggedEntityList.contains(entity.entityId)) {
            taggedEntityList.remove(entity.entityId)
        }
    }

    @HandleEvent
    fun onRenderLivingPre(event: SkyHanniRenderEntityEvent.Pre<EntityOtherPlayerMP>) {
        if (!isEnabled()) return
        if (!config.seeThrough) return
        if (entityList.contains(event.entity) && event.entity.canBeSeen()) {
            GlStateManager.disableDepth()
        }
    }

    @HandleEvent
    fun onRenderLivingPost(event: SkyHanniRenderEntityEvent.Post<EntityOtherPlayerMP>) {
        if (!isEnabled()) return
        if (!config.seeThrough) return
        if (entityList.contains(event.entity) && event.entity.canBeSeen()) {
            GlStateManager.enableDepth()
        }
    }

    @HandleEvent
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!isEnabled()) return

        if (config.drawLine) {
            for (it in EntityUtils.getEntities<EntityOtherPlayerMP>()) {
                if (!it.isHighlighted()) continue
                if (!it.canBeSeen(15)) continue
                val vec = event.exactLocation(it)
                event.drawLineToEye(
                    vec.up(1.54),
                    config.lineColor.toSpecialColor(),
                    config.lineWidth,
                    true,
                )
            }
        }
        if (!configBloodIchor.highlight && !configKillerSpring.highlight) return
        for (stand in MinecraftCompat.localWorld.loadedEntityList.filterIsInstance<EntityArmorStand>()) {
            val vec = stand.position.toLorenzVec()
            val distance = vec.distanceToPlayer()
            val isIchor = stand.hasSkullTexture(BLOOD_ICHOR_TEXTURE)
            val isSpring = stand.hasSkullTexture(KILLER_SPRING_TEXTURE)
            if (!(isIchor && config.bloodIchor.highlight) && !(isSpring && config.killerSpring.highlight)) continue
            val color = (if (isIchor) configBloodIchor.color else configKillerSpring.color).toSpecialColor().addAlpha(config.withAlpha)
            if (distance <= 15) {
                RenderLivingEntityHelper.setEntityColor(
                    stand,
                    color,
                ) { isEnabled() }

                val linesColorStart =
                    (if (isIchor) configBloodIchor.linesColor else configKillerSpring.linesColor).toSpecialColor()
                val text = if (isIchor) "§4Ichor" else "§4Spring"
                event.drawColor(
                    stand.position.toLorenzVec().up(2.0),
                    LorenzColor.DARK_RED,
                    alpha = 1f,
                )
                event.drawDynamicText(
                    stand.position.toLorenzVec().add(0.5, 2.5, 0.5),
                    text,
                    1.5,
                    ignoreBlocks = false,
                )
                for ((player, stand2) in standList) {
                    if (!(configBloodIchor.showLines && isIchor) && !(configKillerSpring.showLines && isSpring)) continue
                    if (!player.canBeSeen()) continue
                    if (!stand2.canBeSeen()) continue
                    event.draw3DLine(
                        event.exactPlayerEyeLocation(player),
                        event.exactPlayerEyeLocation(stand2),
                        linesColorStart,
                        3,
                        true,
                    )

                }
            }
            if (configBloodIchor.renderBeam && isIchor && stand.isEntityAlive) {
                event.drawWaypointFilled(
                    event.exactLocation(stand).add(0, y = -2, 0),
                    configBloodIchor.color.toSpecialColor(),
                    beacon = true,
                )
            }
        }
    }

    @HandleEvent
    fun onWorldChange() {
        entityList.clear()
        taggedEntityList.clear()
        standList = mutableMapOf()
    }

    @HandleEvent(onlyOnIsland = IslandType.THE_RIFT)
    fun onReceiveParticle(event: ReceiveParticleEvent) {
        if (!isEnabled()) return
        val loc = event.location
        for (player in EntityUtils.getEntitiesNearby<EntityOtherPlayerMP>(loc, 3.0)) {
            if (!player.isHighlighted() || event.type != EnumParticleTypes.ENCHANTMENT_TABLE) continue
            for (stand in EntityUtils.getEntitiesNearby<EntityArmorStand>(event.location, 3.0)) {
                if (stand.hasSkullTexture(KILLER_SPRING_TEXTURE) || stand.hasSkullTexture(BLOOD_ICHOR_TEXTURE)) {
                    standList = standList.editCopy { this[stand] = player }
                }
            }
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(9, "slayer.vampireSlayerConfig", "slayer.vampire")
    }

    fun isEnabled() = RiftApi.inRift() && RiftApi.inStillgoreChateau()
}
