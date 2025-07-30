package at.hannibal2.skyhanni.features.garden.visitor

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.minecraft.ToolTipEvent
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.features.garden.visitor.VisitorApi.ACCEPT_SLOT
import at.hannibal2.skyhanni.features.garden.visitor.VisitorApi.REFUSE_SLOT
import at.hannibal2.skyhanni.features.garden.visitor.VisitorApi.VisitorBlockReason
import at.hannibal2.skyhanni.features.garden.visitor.VisitorApi.lastClickedNpc
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.KeyboardManager
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RenderUtils.drawBorder
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import net.minecraft.inventory.Slot
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object VisitorRewardWarning {
    private val config get() = VisitorApi.config.rewardWarning

    @HandleEvent
    fun onForegroundDrawn(event: GuiContainerEvent.ForegroundDrawnEvent) {
        if (!VisitorApi.inInventory) return

        val visitor = VisitorApi.getVisitor(lastClickedNpc) ?: return
        val refuseOfferSlot = event.container.getSlot(REFUSE_SLOT)
        val acceptOfferSlot = event.container.getSlot(ACCEPT_SLOT)
        val blockReason = visitor.blockReason ?: return

        if (blockReason.blockRefusing) {
            renderColor(refuseOfferSlot, acceptOfferSlot, LorenzColor.GREEN)
        } else {
            renderColor(acceptOfferSlot, refuseOfferSlot, LorenzColor.RED)
        }
    }

    private fun renderColor(backgroundSlot: Slot?, outlineSlot: Slot?, outlineColor: LorenzColor) {
        if (!config.bypassKey.isKeyHeld() && backgroundSlot != null) {
            backgroundSlot.highlight(LorenzColor.DARK_GRAY.addOpacity(config.opacity))
        }
        if (config.optionOutline && outlineSlot != null) {
            outlineSlot.drawBorder(outlineColor.addOpacity(200))
        }
    }

    @HandleEvent(priority = HandleEvent.HIGH)
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        if (!VisitorApi.inInventory) return
        val stack = event.slot?.stack ?: return

        val visitor = VisitorApi.getVisitor(lastClickedNpc) ?: return
        val blockReason = visitor.blockReason

        val isRefuseSlot = stack.displayName == "§cRefuse Offer"
        val isAcceptSlot = stack.displayName == "§aAccept Offer"

        val shouldBlock = blockReason?.run { blockRefusing && isRefuseSlot || !blockRefusing && isAcceptSlot } ?: false
        if (!config.bypassKey.isKeyHeld() && shouldBlock) {
            event.cancel()
            return
        }

        // all but shift click types work for accepting visitor
        if (event.clickType == GuiContainerEvent.ClickType.SHIFT) return
        if (isRefuseSlot) {
            VisitorApi.changeStatus(visitor, VisitorApi.VisitorStatus.REFUSED, "refused")
            // fallback if tab list is disabled
            DelayedRun.runDelayed(10.seconds) {
                VisitorApi.removeVisitor(visitor.visitorName)
            }
            return
        }
        if (isAcceptSlot && stack.getLore().contains("§eClick to give!")) {
            VisitorApi.changeStatus(visitor, VisitorApi.VisitorStatus.ACCEPTED, "accepted")
            return
        }
    }

    @HandleEvent(priority = HandleEvent.HIGH)
    fun onTooltip(event: ToolTipEvent) {
        if (!GardenApi.onBarnPlot) return
        if (!VisitorApi.inInventory) return
        val visitor = VisitorApi.getVisitor(lastClickedNpc) ?: return
        if (config.bypassKey.isKeyHeld()) return

        val isRefuseSlot = event.itemStack.displayName == "§cRefuse Offer"
        val isAcceptSlot = event.itemStack.displayName == "§aAccept Offer"

        val blockReason = visitor.blockReason ?: return
        if (blockReason.blockRefusing && !isRefuseSlot) return
        if (!blockReason.blockRefusing && !isAcceptSlot) return

        if (visitor.blockedLore.isEmpty()) {
            updateBlockedLore(event.toolTip.toList(), visitor, blockReason)
        }
        event.toolTip.clear()
        event.toolTip.addAll(visitor.blockedLore)
    }

    private fun updateBlockedLore(
        copiedTooltip: List<String>,
        visitor: VisitorApi.Visitor,
        blockReason: VisitorBlockReason,
    ) {
        val blockedToolTip = mutableListOf<String>()
        for (line in copiedTooltip) {
            if (line.contains("§aAccept Offer§r")) {
                blockedToolTip.add(line.replace("§aAccept Offer§r", "§7Accept Offer§8"))
            } else if (line.contains("§cRefuse Offer§r")) {
                blockedToolTip.add(line.replace("§cRefuse Offer§r", "§7Refuse Offer§8"))
            } else if (!line.contains("minecraft:") && !line.contains("NBT:")) {
                blockedToolTip.add("§8" + line.removeColor())
            }
        }

        blockedToolTip.add("")
        val pricePerCopper = visitor.pricePerCopper?.let { it.shortFormat() }
        // TODO remove !! - best by creating new class LoadedVisitor without any nullable objects
        val loss = visitor.totalPrice!! - visitor.totalReward!!
        val formattedLoss = loss.absoluteValue.shortFormat()
        blockedToolTip.add(blockDescription(blockReason, pricePerCopper, loss, formattedLoss))
        blockedToolTip.add("  §7(Bypass by holding ${KeyboardManager.getKeyName(config.bypassKey)})")

        visitor.blockedLore = blockedToolTip
    }

    private fun blockDescription(
        blockReason: VisitorBlockReason,
        pricePerCopper: String?,
        loss: Double,
        formattedLoss: String,
    ) = blockReason.description + when (blockReason) {
        VisitorBlockReason.CHEAP_COPPER, VisitorBlockReason.EXPENSIVE_COPPER ->
            " §7(paying §6$pricePerCopper §7per)"

        VisitorBlockReason.LOW_LOSS, VisitorBlockReason.HIGH_LOSS ->
            " §7(§6$formattedLoss §7${if (loss > 0) "loss" else "profit"} selling §9Green Thumb I§7)"

        else -> ""
    }
}
