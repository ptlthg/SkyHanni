package at.hannibal2.skyhanni.features.inventory

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.RenderItemTipEvent
import at.hannibal2.skyhanni.events.RenderObject
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getHypixelEnchantments
import net.minecraft.init.Items

@SkyHanniModule
object UltimateEnchantStar {

    private val config get() = SkyHanniMod.feature.inventory

    @HandleEvent
    fun onRenderItemTip(event: RenderItemTipEvent) {
        if (!isEnabled()) return
        if (event.stack.item != Items.enchanted_book) return
        val enchants = event.stack.getHypixelEnchantments() ?: return
        if (enchants.size != 1 || !enchants.keys.first().startsWith("ultimate_")) return
        event.renderObjects += RenderObject("§d✦", -10, -10)
    }

    private fun isEnabled() = LorenzUtils.inSkyBlock && config.ultimateEnchantStar

}
