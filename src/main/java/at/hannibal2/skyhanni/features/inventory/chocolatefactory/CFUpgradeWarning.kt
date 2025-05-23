package at.hannibal2.skyhanni.features.inventory.chocolatefactory

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.ProfileJoinEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.features.fame.ReminderUtils
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.TimeUtils.minutes

@SkyHanniModule
object CFUpgradeWarning {

    private val config get() = CFApi.config.chocolateUpgradeWarnings
    private val profileStorage get() = CFApi.profileStorage

    private var lastUpgradeWarning = SimpleTimeMark.farPast()
    private var lastUpgradeSlot = -1
    private var lastUpgradeLevel = 0

    @HandleEvent(onlyOnSkyblock = true)
    fun onSecondPassed(event: SecondPassedEvent) {
        val profileStorage = profileStorage ?: return

        val upgradeAvailableAt = profileStorage.bestUpgradeAvailableAt
        if (upgradeAvailableAt.isInPast() && !upgradeAvailableAt.isFarPast()) {
            checkUpgradeWarning()
        }
    }

    private fun checkUpgradeWarning() {
        if (!CFApi.isEnabled()) return
        if (!config.upgradeWarning) return
        if (ReminderUtils.isBusy()) return
        if (CFCustomReminder.isActive()) return
        if (lastUpgradeWarning.passedSince() < config.timeBetweenWarnings.minutes) return
        lastUpgradeWarning = SimpleTimeMark.now()
        if (config.upgradeWarningSound) {
            SoundUtils.playBeepSound()
        }
        if (CFApi.inChocolateFactory) return
        ChatUtils.clickToActionOrDisable(
            "You have a Chocolate factory upgrade available to purchase!",
            config::upgradeWarning,
            actionName = "open Chocolate Factory",
            action = { HypixelCommands.chocolateFactory() },
        )
    }

    @HandleEvent
    fun onProfileChange(event: ProfileJoinEvent) {
        lastUpgradeWarning = SimpleTimeMark.farPast()
    }

    fun checkUpgradeChange(slot: Int, level: Int) {
        if (slot != lastUpgradeSlot || level != lastUpgradeLevel) {
            lastUpgradeWarning = SimpleTimeMark.now()
            lastUpgradeSlot = slot
            lastUpgradeLevel = level
        }
    }
}
