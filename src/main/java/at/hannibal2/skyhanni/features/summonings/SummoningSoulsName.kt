package at.hannibal2.skyhanni.features.summonings

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniTickEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.EntityUtils.getNameTagWith
import at.hannibal2.skyhanni.utils.EntityUtils.hasSkullTexture
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.RenderUtils.drawString
import at.hannibal2.skyhanni.utils.SkullTextureHolder
import at.hannibal2.skyhanni.utils.TimeLimitedCache
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.sorted
import at.hannibal2.skyhanni.utils.getLorenzVec
import net.minecraft.entity.EntityLiving
import net.minecraft.entity.item.EntityArmorStand
import kotlin.time.Duration.Companion.minutes

@SkyHanniModule
object SummoningSoulsName {

    private val SUMMONING_SOUL_TEXTURE by lazy { SkullTextureHolder.getTexture("SUMMONING_SOUL") }

    private val souls = mutableMapOf<EntityArmorStand, String>()
    private val mobsLastLocation = TimeLimitedCache<Int, LorenzVec>(6.minutes)
    private val mobsName = TimeLimitedCache<Int, String>(6.minutes)

    @HandleEvent
    fun onTick(event: SkyHanniTickEvent) {
        if (!isEnabled()) return

        // TODO use packets instead of this
        check()
    }

    private fun check() {
        for (entity in EntityUtils.getEntities<EntityArmorStand>()) {
            if (entity in souls) continue

            if (entity.hasSkullTexture(SUMMONING_SOUL_TEXTURE)) {
                val soulLocation = entity.getLorenzVec()

                val map = mutableMapOf<Int, Double>()
                for ((mob, loc) in mobsLastLocation) {
                    val distance = loc.distance(soulLocation)
                    map[mob] = distance
                }

                val nearestMob = map.sorted().firstNotNullOfOrNull { it.key }
                if (nearestMob != null) {
                    souls[entity] = mobsName[nearestMob] ?: continue
                }
            }
        }

        for (entity in EntityUtils.getEntities<EntityLiving>()) {
            val id = entity.entityId
            val consumer = entity.getNameTagWith(2, "§c❤")
            if (consumer != null && !consumer.name.contains("§e0")) {
                mobsLastLocation[id] = entity.getLorenzVec()
                mobsName[id] = consumer.name
            }
        }

        val entityList = EntityUtils.getEntities<EntityArmorStand>()
        souls.keys.removeIf { it !in entityList }
        // TODO fix overhead!
//        mobs.keys.removeIf { it !in world.loadedEntityList }
    }

    @HandleEvent
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!isEnabled()) return

        for ((entity, name) in souls) {
            val vec = entity.getLorenzVec()
            event.drawString(vec.up(2.5), name)
        }
    }

    @HandleEvent
    fun onWorldChange() {
        souls.clear()
        mobsLastLocation.clear()
        mobsName.clear()
    }

    private fun isEnabled() = LorenzUtils.inSkyBlock && SkyHanniMod.feature.combat.summonings.summoningSoulDisplay
}
