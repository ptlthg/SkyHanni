package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.ElectionApi.derpy
import at.hannibal2.skyhanni.events.CheckRenderEntityEvent
import at.hannibal2.skyhanni.events.entity.EntityDisplayNameEvent
import at.hannibal2.skyhanni.events.entity.EntityHealthDisplayEvent
import at.hannibal2.skyhanni.events.entity.EntityLeaveWorldEvent
import at.hannibal2.skyhanni.events.entity.EntityMaxHealthUpdateEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.baseMaxHealth
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.TimeLimitedCache
import net.minecraft.client.renderer.culling.ICamera
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.ChatComponentText
import kotlin.time.Duration.Companion.milliseconds

@SkyHanniModule
object EntityData {

    private val maxHealthMap = mutableMapOf<Int, Int>()
    private val nametagCache = TimeLimitedCache<Entity, ChatComponentText>(50.milliseconds)
    private val healthDisplayCache = TimeLimitedCache<String, String>(50.milliseconds)
    private val lastVisibilityCheck = TimeLimitedCache<Entity, Pair<SimpleTimeMark, Boolean>>(500.milliseconds)

    @HandleEvent
    fun onTick() {
        for (entity in EntityUtils.getEntities<EntityLivingBase>()) { // this completely ignores the ignored entities list?
            val maxHealth = entity.baseMaxHealth
            val id = entity.entityId
            val oldMaxHealth = maxHealthMap.getOrDefault(id, -1)
            if (oldMaxHealth != maxHealth) {
                maxHealthMap[id] = maxHealth
                EntityMaxHealthUpdateEvent(entity, maxHealth.derpy()).post()
            }
        }
    }

    @HandleEvent
    fun onEntityLeaveWorld(event: EntityLeaveWorldEvent<EntityLivingBase>) {
        maxHealthMap -= event.entity.entityId
    }

    @HandleEvent
    fun onWorldChange() {
        maxHealthMap.clear()
    }

    @JvmStatic
    fun getDisplayName(entity: Entity, oldValue: ChatComponentText): ChatComponentText {
        return postRenderNametag(entity, oldValue)
    }

    @JvmStatic
    fun despawnEntity(entity: Entity) {
        EntityLeaveWorldEvent(entity).post()
    }

    private fun postRenderNametag(entity: Entity, chatComponent: ChatComponentText) = nametagCache.getOrPut(entity) {
        val event = EntityDisplayNameEvent(entity, chatComponent)
        event.post()
        event.chatComponent
    }

    @JvmStatic
    fun getHealthDisplay(text: String) = healthDisplayCache.getOrPut(text) {
        val event = EntityHealthDisplayEvent(text)
        event.post()
        event.text
    }

    @JvmStatic
    fun onRenderCheck(entity: Entity, camera: ICamera, camX: Double, camY: Double, camZ: Double): Boolean {
        lastVisibilityCheck[entity]?.let { (time, result) ->
            if (time.passedSince() < 200.milliseconds) {
                return result
            }
        }
        val result = CheckRenderEntityEvent(entity, camera, camX, camY, camZ).post()
        lastVisibilityCheck[entity] = SimpleTimeMark.now() to result
        return result
    }
}
