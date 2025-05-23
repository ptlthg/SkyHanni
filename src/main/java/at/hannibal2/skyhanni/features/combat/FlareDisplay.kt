package at.hannibal2.skyhanni.features.combat

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.features.combat.FlareConfig
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.ReceiveParticleEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.EntityUtils.canBeSeen
import at.hannibal2.skyhanni.utils.EntityUtils.hasSkullTexture
import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.RenderUtils
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.RenderUtils.drawSphereInWorld
import at.hannibal2.skyhanni.utils.RenderUtils.drawSphereWireframeInWorld
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkullTextureHolder
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.SpecialColor.toSpecialColor
import at.hannibal2.skyhanni.utils.SpecialColor.toSpecialColorInt
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.TimeUtils.ticks
import at.hannibal2.skyhanni.utils.compat.GuiScreenUtils
import at.hannibal2.skyhanni.utils.getLorenzVec
import at.hannibal2.skyhanni.utils.renderables.Renderable
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.util.EnumParticleTypes
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object FlareDisplay {

    private val config get() = SkyHanniMod.feature.combat.flare
    private var display = emptyList<Renderable>()
    private val flares = mutableListOf<Flare>()
    private val enabled get() = config.enabled

    private var activeWarning = false

    class Flare(val type: FlareType, val entity: EntityArmorStand, val location: LorenzVec = entity.getLorenzVec())

    private val MAX_FLARE_TIME = 3.minutes

    private val flareSkins by lazy {
        mapOf(
            SkullTextureHolder.getTexture("FLARE_WARNING") to FlareType.WARNING,
            SkullTextureHolder.getTexture("FLARE_ALERT") to FlareType.ALERT,
            SkullTextureHolder.getTexture("FLARE_SOS") to FlareType.SOS,
        )
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!enabled) return

        if (config.flashScreen && activeWarning) {
            val alpha = ((2 + sin(SimpleTimeMark.now().toMillis() / 1000.0)) * 255 / 4).toInt().coerceIn(0..255)
            GuiRenderUtils.drawRect(
                0,
                0,
                GuiScreenUtils.displayWidth,
                GuiScreenUtils.displayHeight,
                (alpha shl 24) or (config.flashColor.toSpecialColorInt() and 0xFFFFFF),
            )
            GlStateManager.color(1F, 1F, 1F, 1F)
        }

        if (config.displayType == FlareConfig.DisplayType.WORLD) return
        config.position.renderRenderables(display, posLabel = "Flare Timer")
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!enabled) return
        flares.removeIf { !it.entity.isEntityAlive }
        for (entity in EntityUtils.getAllEntities().filterIsInstance<EntityArmorStand>()) {
            if (!entity.canBeSeen()) continue
            if (entity.ticksExisted.ticks > MAX_FLARE_TIME) continue
            if (isAlreadyKnownFlare(entity)) continue
            getFlareTypeForTexture(entity)?.let {
                flares.add(Flare(it, entity))
            }
            activeWarning = false
        }
        var newDisplay: List<Renderable>? = null
        for (type in FlareType.entries) {
            val flare = getFlareForType(type) ?: continue
            val remainingTime = getRemainingTime(flare)

            val name = type.displayName
            if (newDisplay == null) {
                newDisplay = buildList {
                    val displayTime = if (remainingTime.isNegative()) "§eSoon" else "§b${remainingTime.format()}"
                    add(Renderable.string("$name: $displayTime"))
                    if (config.showManaBuff) {
                        type.manaBuff?.let {
                            add(Renderable.string(" §b$it §7mana regen"))
                        }
                    }
                }
            }
            if (remainingTime !in 0.seconds..config.warnWhenAboutToExpire.seconds) continue
            activeWarning = true
            val message = "$name §eexpires in: §b${remainingTime.inWholeSeconds}s"
            when (config.alertType) {
                FlareConfig.AlertType.CHAT -> {
                    ChatUtils.chat(message)
                }

                FlareConfig.AlertType.TITLE -> {
                    TitleManager.sendTitle(message, duration = 1.seconds)
                }

                FlareConfig.AlertType.CHAT_TITLE -> {
                    ChatUtils.chat(message)
                    TitleManager.sendTitle(message, duration = 1.seconds)
                }

                else -> {}
            }
            if (config.expireSound) {
                SoundUtils.playPlingSound()
            }
        }
        display = newDisplay.orEmpty()
    }

    private fun getRemainingTime(flare: Flare): Duration {
        val entity = flare.entity
        val aliveTime = entity.ticksExisted.ticks
        val remainingTime = (MAX_FLARE_TIME - aliveTime)
        return remainingTime
    }

    private fun getFlareForType(type: FlareType): Flare? = flares.firstOrNull { it.type == type }

    private fun getFlareTypeForTexture(entity: EntityArmorStand): FlareType? =
        flareSkins.entries.firstOrNull { entity.hasSkullTexture(it.key) }?.value

    private fun isAlreadyKnownFlare(entity: EntityArmorStand): Boolean =
        flares.any { it.entity.entityId == entity.entityId }

    @HandleEvent
    fun onWorldChange() {
        flares.clear()
        display = emptyList()
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!enabled) return

        if (config.displayType != FlareConfig.DisplayType.GUI) {
            for (flare in flares) {
                val location = flare.location.add(-0.5, 0.0, -0.5)
                val name = flare.type.displayName
                val time = "§b${getRemainingTime(flare).format()}"
                event.drawDynamicText(location, name, 1.5, ignoreBlocks = false)
                event.drawDynamicText(location, time, 1.5, yOff = 10f, ignoreBlocks = false)
            }
        }

        if (config.outlineType == FlareConfig.OutlineType.NONE) return

        for (flare in flares) {
            val entity = flare.entity
            val location = flare.location

            val color = when (flare.type) {
                FlareType.WARNING -> config.warningColor
                FlareType.ALERT -> config.alertColor
                FlareType.SOS -> config.sosColor
            }.toSpecialColor()

            when (config.outlineType) {
                FlareConfig.OutlineType.FILLED -> {
                    event.drawSphereInWorld(color, location, 40f)
                }

                FlareConfig.OutlineType.WIREFRAME -> {
                    event.drawSphereWireframeInWorld(color, location, 40f)
                }

                FlareConfig.OutlineType.CIRCLE -> {
                    RenderUtils.drawCircle(entity, event.partialTicks, 40.0, color)
                }

                else -> {}
            }
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onReceiveParticle(event: ReceiveParticleEvent) {
        if (!enabled) return
        if (!config.hideParticles) return

        val location = event.location
        val distance = flares.minOfOrNull { it.location.distance(location) } ?: return
        if (distance < 2.5) {
            if (event.type == EnumParticleTypes.FLAME) {
                event.cancel()
            }
        }
    }

    enum class FlareType(val displayName: String, val manaBuff: String?) {
        SOS("§5SOS Flare", "+125%"),
        ALERT("§9Alert Flare", "+50%"),
        WARNING("§aWarning Flare", null),
    }
}
