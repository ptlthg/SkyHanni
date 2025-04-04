package at.hannibal2.skyhanni.data.mob

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.features.dev.DebugMobConfig.HowToShow
import at.hannibal2.skyhanni.events.MobEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.CopyNearbyEntitiesCommand.getMobInfo
import at.hannibal2.skyhanni.utils.LocationUtils.getTopCenter
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzDebug
import at.hannibal2.skyhanni.utils.MobUtils
import at.hannibal2.skyhanni.utils.RenderUtils.drawFilledBoundingBox
import at.hannibal2.skyhanni.utils.RenderUtils.drawString
import at.hannibal2.skyhanni.utils.RenderUtils.expandBlock
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import net.minecraft.client.entity.EntityPlayerSP

@SkyHanniModule
object MobDebug {

    private val config get() = SkyHanniMod.feature.dev.mobDebug.mobDetection

    private var lastRayHit: Mob? = null

    private fun HowToShow.isHighlight() =
        this == HowToShow.ONLY_HIGHLIGHT || this == HowToShow.NAME_AND_HIGHLIGHT

    private fun HowToShow.isName() =
        this == HowToShow.ONLY_NAME || this == HowToShow.NAME_AND_HIGHLIGHT

    private fun Mob.isNotInvisible() = !this.isInvisible() || (config.showInvisible && this == lastRayHit)

    private fun MobData.MobSet.highlight(event: SkyHanniRenderWorldEvent, color: (Mob) -> (LorenzColor)) {
        for (mob in filter { it.isNotInvisible() }) {
            event.drawFilledBoundingBox(mob.boundingBox.expandBlock(), color.invoke(mob).toColor(), 0.3f)
        }
    }

    private fun MobData.MobSet.showName(event: SkyHanniRenderWorldEvent) {
        val map = filter { it.canBeSeen() && it.isNotInvisible() }
            .map { it.boundingBox.getTopCenter() to it.name }
        for ((location, text) in map) {
            event.drawString(location.up(0.5), "§5$text", seeThroughBlocks = true)
        }
    }

    @HandleEvent
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (config.showRayHit || config.showInvisible) {
            lastRayHit = MobUtils.rayTraceForMobs(MinecraftCompat.localPlayer, event.partialTicks)
                ?.firstOrNull { it.canBeSeen() && (!config.showInvisible || !it.isInvisible()) }
        }

        if (config.skyblockMob.isHighlight()) {
            MobData.skyblockMobs.highlight(event) { if (it.mobType == Mob.Type.BOSS) LorenzColor.DARK_GREEN else LorenzColor.GREEN }
        }
        if (config.displayNPC.isHighlight()) {
            MobData.displayNpcs.highlight(event) { LorenzColor.RED }
        }
        if (config.realPlayerHighlight) {
            MobData.players.highlight(event) { if (it.baseEntity is EntityPlayerSP) LorenzColor.CHROMA else LorenzColor.BLUE }
        }
        if (config.summon.isHighlight()) {
            MobData.summoningMobs.highlight(event) { LorenzColor.YELLOW }
        }
        if (config.special.isHighlight()) {
            MobData.special.highlight(event) { LorenzColor.AQUA }
        }
        if (config.skyblockMob.isName()) {
            MobData.skyblockMobs.showName(event)
        }
        if (config.displayNPC.isName()) {
            MobData.displayNpcs.showName(event)
        }
        if (config.summon.isName()) {
            MobData.summoningMobs.showName(event)
        }
        if (config.special.isName()) {
            MobData.special.showName(event)
        }
        if (config.showRayHit) {
            lastRayHit?.let {
                event.drawFilledBoundingBox(it.boundingBox.expandBlock(), LorenzColor.GOLD.toColor(), 0.5f)
            }
        }
    }

    @HandleEvent
    fun onMobEvent(event: MobEvent) {
        if (!config.logEvents) return
        val text = "Mob ${if (event is MobEvent.Spawn) "Spawn" else "Despawn"}: ${
            getMobInfo(event.mob).joinToString(", ")
        }"
        MobData.logger.log(text)
        LorenzDebug.log(text)
    }
}
