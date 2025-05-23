package at.hannibal2.skyhanni.features.inventory

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.ElectionApi
import at.hannibal2.skyhanni.data.ElectionCandidate
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryOpenEvent
import at.hannibal2.skyhanni.events.render.gui.ReplaceItemEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.setLore
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NeuItems.getItemStack
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.StringUtils.removeResets
import at.hannibal2.skyhanni.utils.StringUtils.splitLines
import at.hannibal2.skyhanni.utils.compat.setCustomItemName
import net.minecraft.client.player.inventory.ContainerLocalMenu
import net.minecraft.item.ItemStack

@SkyHanniModule
object MinisterInCalendar {

    private const val MINISTER_SLOT = 38
    private var ministerItemStack: ItemStack? = null

    private val prefix = listOf(
        "§8(From SkyHanni)",
        "",
        "§8§m--------------------------",
    )
    private val suffix = listOf(
        "§8§m--------------------------",
        "",
        "§7The Minister is who came in 2nd place",
        "§7during the election. They have one",
        "§7of their perks active.",
    )

    @HandleEvent
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (!isEnabled()) return
        if (!ElectionApi.calendarGuiPattern.matches(InventoryUtils.openInventoryName())) return
        val minister = ElectionApi.currentMinister ?: return

        val itemStack = "${minister.name}_MAYOR_MONSTER".toInternalName().getItemStack()
        val ministerColor = ElectionApi.mayorNameToColorCode(minister.mayorName)

        ministerItemStack = changeItem(ministerColor, minister, itemStack)
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (!isEnabled()) return
        if (!ElectionApi.calendarGuiPattern.matches(InventoryUtils.openInventoryName())) return
        ministerItemStack = null
    }

    @HandleEvent
    fun replaceItem(event: ReplaceItemEvent) {
        if (!isEnabled()) return
        if (event.inventory !is ContainerLocalMenu || event.slot != MINISTER_SLOT) return
        if (!ElectionApi.calendarGuiPattern.matches(InventoryUtils.openInventoryName())) return
        event.replace(ministerItemStack ?: return)
    }

    private fun changeItem(
        ministerColor: String,
        minister: ElectionCandidate,
        item: ItemStack,
    ): ItemStack? {
        val ministerDisplayName = "${ministerColor}Minister ${minister.mayorName}"
        val ministerLore = buildList {
            addAll(prefix)
            for (perk in minister.activePerks) {
                add("$ministerColor${perk.perkName}")
                addAll(perk.description.splitLines(170).removeResets().split("\n").map { "§7$it" })
            }
            addAll(suffix)
        }

        return item.setLore(ministerLore).setCustomItemName(ministerDisplayName)
    }

    fun isEnabled() = LorenzUtils.inSkyBlock && SkyHanniMod.feature.inventory.ministerInCalendar
}
