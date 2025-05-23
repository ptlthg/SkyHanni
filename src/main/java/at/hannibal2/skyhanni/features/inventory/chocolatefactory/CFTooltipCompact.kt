package at.hannibal2.skyhanni.features.inventory.chocolatefactory

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.minecraft.ToolTipEvent
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.CFApi.partyModeReplace
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.RenderUtils.renderStrings
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object CFTooltipCompact {
    private val config get() = CFApi.config

    private var lastClick = SimpleTimeMark.farPast()
    private var lastHover = SimpleTimeMark.farPast()
    private var tooltipToHover = listOf<String>()

    @HandleEvent
    fun onToolTip(event: ToolTipEvent) {
        if (!CFApi.inChocolateFactory) return

        if (config.tooltipMove) {
            if (event.slot.slotNumber <= 44) {
                lastHover = SimpleTimeMark.now()
                tooltipToHover = event.toolTip.toList().map { it.partyModeReplace() }
                event.cancel()
            } else {
                lastHover = SimpleTimeMark.farPast()
            }
            return
        }

        onCompactClick(event)
    }

    @HandleEvent
    fun onBackgroundDraw(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (!CFApi.inChocolateFactory) return
        if (config.tooltipMove) {
            if (lastHover.passedSince() < 1.seconds) {
                config.tooltipMovePosition.renderStrings(tooltipToHover, posLabel = "Tooltip Move")
            }
        }
    }

    private fun onCompactClick(event: ToolTipEvent) {
        if (!config.compactOnClick) return

        val itemStack = event.itemStack
        val lore = itemStack.getLore()
        if (!lore.any { it == "§7§eClick to uncover the meaning of life!" }) return
        if (lastClick.passedSince() >= 1.seconds && !config.compactOnClickAlways) return
        val list = mutableListOf<String>()
        list.add(itemStack.displayName)
        lore.getOrNull(5)?.let {
            list.add(it)
        }
        event.toolTip = list
        return
    }

    @HandleEvent(priority = HandleEvent.HIGH)
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {

        if (CFApi.inChocolateFactory) {
            if (event.slotId == 13) {
                lastClick = SimpleTimeMark.now()
            }
        }
    }
}
