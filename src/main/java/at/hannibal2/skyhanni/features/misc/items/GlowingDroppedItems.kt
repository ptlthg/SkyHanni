package at.hannibal2.skyhanni.features.misc.items

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandTypeTags
import at.hannibal2.skyhanni.events.RenderEntityOutlineEvent
import at.hannibal2.skyhanni.features.garden.pests.SprayType
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.EnumUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalNameOrNull
import at.hannibal2.skyhanni.utils.ItemUtils.getItemRarityOrNull
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RecalculatingValue
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.entity.item.EntityItem
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object GlowingDroppedItems {

    private val config get() = SkyHanniMod.feature.misc.glowingDroppedItems

    /**
     * List of SkyBlock locations where we might see items in showcases
     */
    private val showcaseItemLocations = setOf(
        "The End",
        "Jerry's Workshop",
        "Dark Auction",
        "Photon Pathway",
        "Barrier Street",
        "Village Plaza",
        "Déjà Vu Alley",
    )

    @HandleEvent
    fun onRenderEntityOutlines(event: RenderEntityOutlineEvent) {
        if (isEnabled() && event.type === RenderEntityOutlineEvent.Type.XRAY) {
            event.queueEntitiesToOutline { getEntityOutlineColor(it) }
        }
    }

    private fun isEnabled() = LorenzUtils.inSkyBlock && config.enabled

    private fun getEntityOutlineColor(entity: Entity): Int? {
        val item = entity as? EntityItem ?: return null
        if (shouldHideShowcaseItem(entity)) return null

        val entityItem = item.entityItem
        if (!config.highlightFishingBait && entityItem.displayName.endsWith(" Bait")) {
            return null
        }

        val internalName = entityItem.getInternalNameOrNull() ?: return null
        val isSprayItem = EnumUtils.enumValueOfOrNull<SprayType>(internalName.asString()) != null
        if (isSprayItem) return null
        val rarity = entityItem.getItemRarityOrNull()
        return rarity?.color?.toColor()?.rgb
    }

    private val isShowcaseArea by RecalculatingValue(1.seconds) {
        IslandTypeTags.HAS_SHOWCASES.inAny() || LorenzUtils.skyBlockArea in showcaseItemLocations
    }

    private fun shouldHideShowcaseItem(entity: EntityItem): Boolean {
        if (!isShowcaseArea || config.highlightShowcase) return false

        for (entityArmorStand in entity.worldObj.getEntitiesWithinAABB(
            EntityArmorStand::class.java,
            entity.entityBoundingBox,
        )) {
            if (entityArmorStand.isInvisible) {
                return true
            }
        }

        return false
    }
}
