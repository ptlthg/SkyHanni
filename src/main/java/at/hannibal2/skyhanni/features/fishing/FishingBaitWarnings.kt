package at.hannibal2.skyhanni.features.fishing

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.events.entity.EntityEnterWorldEvent
import at.hannibal2.skyhanni.events.fishing.FishingBobberInLiquidEvent
import at.hannibal2.skyhanni.features.fishing.FishingApi.isBait
import at.hannibal2.skyhanni.features.nether.kuudra.KuudraApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.TimeLimitedSet
import at.hannibal2.skyhanni.utils.getLorenzVec
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.projectile.EntityFishHook
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object FishingBaitWarnings {

    private val config get() = SkyHanniMod.feature.fishing.fishingBaitWarnings

    private data class Bait(
        private val entity: EntityItem,
        val name: String = entity.entityItem.displayName,
        val location: LorenzVec = entity.getLorenzVec(),
    ) {
        fun distanceTo(bobber: EntityFishHook) = location.distance(bobber.getLorenzVec())
    }

    private var lastBait: String? = null
    private var wasUsingBait = true

    private val baitEntities = TimeLimitedSet<Bait>(750.seconds)

    @HandleEvent
    fun onWorldChange() {
        lastBait = null
        wasUsingBait = true
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onBobber(event: FishingBobberInLiquidEvent) {
        if (KuudraApi.inKuudra()) return
        DelayedRun.runDelayed(500.milliseconds) {
            checkBait()
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onEntityEnterWorld(event: EntityEnterWorldEvent<EntityItem>) {
        if (KuudraApi.inKuudra() || !FishingApi.isFishing()) return
        if (event.entity.distanceToPlayer() > 10) return
        DelayedRun.runNextTick {
            val isBait = event.entity.entityItem.isBait()
            if (isBait) {
                baitEntities += Bait(event.entity)
            }
        }
    }

    private fun checkBait() {
        val bobber = FishingApi.bobber ?: return
        val bait = baitEntities.filter { it.distanceTo(bobber) < 8 }.minByOrNull { it.distanceTo(bobber) }?.name
        baitEntities.clear()

        if (bait == null) {
            if (config.noBaitWarning && !wasUsingBait) {
                showNoBaitWarning()
            }
        } else if (config.baitChangeWarning) {
            lastBait?.let {
                if (it != bait) {
                    showBaitChangeWarning(it, bait)
                }
            }
        }

        wasUsingBait = bait != null
        lastBait = bait
    }

    private fun showBaitChangeWarning(before: String, after: String) {
        SoundUtils.playClickSound()
        TitleManager.sendTitle("§eBait changed!", duration = 2.seconds)
        ChatUtils.chat("Fishing Bait changed: $before §e-> $after")
    }

    private fun showNoBaitWarning() {
        SoundUtils.playErrorSound()
        TitleManager.sendTitle("§cNo bait is used!", duration = 2.seconds)
        ChatUtils.chat("You're not using any fishing baits!")
    }
}
