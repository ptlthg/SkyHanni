package at.hannibal2.skyhanni.features.misc.trevor

import at.hannibal2.skyhanni.data.ElectionApi.derpy
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.data.mob.Mob
import at.hannibal2.skyhanni.data.mob.MobData
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.EntityUtils.canBeSeen
import at.hannibal2.skyhanni.utils.LocationUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.baseMaxHealth
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.compat.EffectsCompat
import at.hannibal2.skyhanni.utils.compat.EffectsCompat.Companion.hasPotionEffect
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import at.hannibal2.skyhanni.utils.toLorenzVec
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.entity.EntityLivingBase

object TrevorSolver {

    private val animalHealths = setOf(100, 200, 500, 1000, 2000, 5000, 10000, 30000)

    var currentMob: TrevorMob? = null
    private var maxHeight: Double = 0.0
    private var minHeight: Double = 0.0
    private var foundID = -1
    var mobCoordinates = LorenzVec(0.0, 0.0, 0.0)
    var mobLocation = TrapperMobArea.NONE
    var averageHeight = (minHeight + maxHeight) / 2

    fun findMobHeight(height: Int, above: Boolean) {
        val playerPosition = LocationUtils.playerLocation().roundTo(2)
        val mobHeight = if (above) playerPosition.y + height else playerPosition.y - height
        if (maxHeight == 0.0) {

            maxHeight = mobHeight + 2.5
            minHeight = mobHeight - 2.5
        } else {
            if (mobHeight + 2.5 in minHeight..maxHeight) {
                maxHeight = mobHeight + 2.5
            } else if (mobHeight - 2.5 in minHeight..maxHeight) {
                minHeight = mobHeight - 2.5
            } else {
                maxHeight = mobHeight + 2.5
                minHeight = mobHeight - 2.5
            }
        }
        averageHeight = (minHeight + maxHeight) / 2
    }

    fun findMob() {
        val hasBlindness = MinecraftCompat.localPlayer.hasPotionEffect(EffectsCompat.BLINDNESS)
        for (entity in EntityUtils.getAllEntities()) {
            if (entity is EntityOtherPlayerMP) continue
            val name = entity.name
            val isTrevor = MobData.entityToMob[entity]?.let { it.name != name && isTrevorMob(it) } ?: false
            val entityHealth = if (entity is EntityLivingBase) entity.baseMaxHealth.derpy() else 0
            currentMob = TrevorMob.entries.firstOrNull { it.mobName.contains(name) || it.entityName.contains(name) }
            if ((animalHealths.any { it == entityHealth } && currentMob != null) || isTrevor) {

                val currentMob = currentMob ?: run {
                    ErrorManager.skyHanniError(
                        "Found trevor mob but current mob is null",
                        "entity" to entity,
                        "mobDataMob" to MobData.entityToMob[entity],
                    )
                }

                if (foundID == entity.entityId) {
                    val isOasisMob = currentMob == TrevorMob.RABBIT || currentMob == TrevorMob.SHEEP
                    if (isOasisMob && mobLocation == TrapperMobArea.OASIS && !isTrevor) return
                    val canSee = entity.canBeSeen(currentMob.renderDistance) && !entity.isInvisible && !hasBlindness
                    if (canSee) {
                        if (mobLocation != TrapperMobArea.FOUND) {
                            TitleManager.sendTitle("§2Saw ${currentMob.mobName}!")
                        }
                        mobLocation = TrapperMobArea.FOUND
                        mobCoordinates = entity.position.toLorenzVec()
                    }
                } else {
                    foundID = entity.entityId
                }
                return
            }
        }
        if (foundID != -1) {
            mobCoordinates = LorenzVec(0.0, 0.0, 0.0)
            foundID = -1
        }
    }

    private fun isTrevorMob(mob: Mob): Boolean =
        TrevorTracker.TrapperMobRarity.entries.any { mob.name.startsWith(it.formattedName + " ", ignoreCase = true) }

    fun resetLocation() {
        maxHeight = 0.0
        minHeight = 0.0
        averageHeight = (minHeight + maxHeight) / 2
        foundID = -1
        mobCoordinates = LorenzVec(0.0, 0.0, 0.0)
    }
}
