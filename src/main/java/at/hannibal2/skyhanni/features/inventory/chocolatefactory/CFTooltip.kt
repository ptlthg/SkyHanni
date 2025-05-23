package at.hannibal2.skyhanni.features.inventory.chocolatefactory

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.minecraft.ToolTipEvent
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.CFApi.profileStorage
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo

@SkyHanniModule
object CFTooltip {

    private val config get() = CFApi.config

    @HandleEvent(priority = HandleEvent.HIGH)
    fun onTooltip(event: ToolTipEvent) {
        if (!CFApi.inChocolateFactory) return
        if (!config.extraTooltipStats) return

        val slotIndex = event.slot.slotNumber
        if (slotIndex !in CFApi.otherUpgradeSlots && slotIndex !in CFApi.rabbitSlots) return

        val upgradeInfo = CFApi.factoryUpgrades.find { it.slotIndex == slotIndex } ?: return

        if (slotIndex == CFApi.timeTowerIndex && upgradeInfo.isMaxed) {
            event.toolTip.add("§8§m-----------------")
            event.toolTip.add("§7One charge will give: §6${chocPerTimeTower().addSeparators()}")
        }

        if (upgradeInfo.isMaxed) return

        event.toolTip.add("§8§m-----------------")
        event.toolTip.add("§7Time until upgrade: §e${upgradeInfo.formattedTimeUntilGoal()}")

        if (upgradeInfo.effectiveCost == null) return

        event.toolTip.add("§7Extra: §6${upgradeInfo.extraPerSecond?.roundTo(2) ?: "N/A"} §7choc/s")
        event.toolTip.add("§7Effective Cost: §6${upgradeInfo.effectiveCost.addSeparators()}")

        if (slotIndex == CFApi.timeTowerIndex) {
            event.toolTip.add("§7One charge will give: §6${chocPerTimeTower().addSeparators()}")
        }
    }

    private fun chocPerTimeTower(): Int {
        val profileStorage = profileStorage ?: return 0
        val amountPerSecond = profileStorage.rawChocPerSecond * CFApi.timeTowerMultiplier()
        val amountPerHour = amountPerSecond * 60 * 60
        return amountPerHour.toInt()
    }
}
