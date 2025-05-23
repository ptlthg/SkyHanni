package at.hannibal2.skyhanni.events.entity

import at.hannibal2.skyhanni.api.event.GenericSkyHanniEvent
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat.isLocalPlayer
import net.minecraft.entity.EntityLivingBase

class EntityMoveEvent<T : EntityLivingBase>(
    val entity: T,
    val oldLocation: LorenzVec,
    val newLocation: LorenzVec,
    val distance: Double,
) : GenericSkyHanniEvent<T>(entity.javaClass) {
    val isLocalPlayer get() = entity.isLocalPlayer
}
