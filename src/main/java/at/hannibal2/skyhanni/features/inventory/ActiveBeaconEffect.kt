package at.hannibal2.skyhanni.features.inventory

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object ActiveBeaconEffect {

    val config get() = SkyHanniMod.feature.inventory

    private val patternGroup = RepoPattern.group("inventory.activebeaconeffect")

    /**
     * REGEX-TEST: Profile Stat Upgrades
     */
    private val inventoryPattern by patternGroup.pattern(
        "inventory",
        "Profile Stat Upgrades",
    )

    /**
     * REGEX-TEST: §aActive stat boost!
     */
    private val slotPattern by patternGroup.pattern(
        "slot.active",
        "§aActive stat boost!",
    )

    private var slot: Int? = null

    @HandleEvent
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        if (!isEnabled()) return
        if (!inventoryPattern.matches(event.inventoryName)) {
            slot = null
            return
        }

        slot = event.inventoryItems.filter { (_, stack) ->
            stack.getLore().any { slotPattern.matches(it) }
        }.firstNotNullOfOrNull { it.key }
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        slot = null
    }

    @HandleEvent
    fun onBackgroundDrawn(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (!isEnabled()) return
        val slot = slot ?: return

        event.container.getSlot(slot).highlight(LorenzColor.GREEN)
    }

    fun isEnabled() = IslandType.PRIVATE_ISLAND.isInIsland() && config.highlightActiveBeaconEffect
}
