package at.hannibal2.skyhanni.features.inventory.chocolatefactory

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.core.config.Position
import at.hannibal2.skyhanni.data.hypixel.chat.event.SystemMessageEvent
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.features.fame.ReminderUtils
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.CFApi.partyModeReplace
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.data.ChocolateAmount
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NumberUtil.formatLong
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.TimeUtils.minutes
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.item.ItemStack

@SkyHanniModule
object CFCustomReminder {
    private val configReminder get() = CFApi.config.customReminder
    private val configUpgradeWarnings get() = CFApi.config.chocolateUpgradeWarnings

    private var targetGoal: Long?
        get() = CFApi.profileStorage?.targetGoal
        set(value) {
            CFApi.profileStorage?.targetGoal = value
        }
    private var targetName: String?
        get() = CFApi.profileStorage?.targetName
        set(value) {
            CFApi.profileStorage?.targetName = value
        }

    fun isActive() = targetGoal != null && configReminder.enabled

    private var display = emptyList<Renderable>()

    private var lastUpgradeWarning = SimpleTimeMark.farPast()

    private val patternGroup = RepoPattern.group("inventory.chocolate.factory")

    /**
     * REGEX-TEST: §cRequires 400B all-time Chocolate!
     */
    private val milestoneCostLorePattern by patternGroup.pattern(
        "milestone.cost",
        "§cRequires (?<amount>.*) all-time Chocolate!",
    )

    /**
     * REGEX-TEST: §cYou don't have enough Chocolate!
     * REGEX-TEST: §cYou don't have the required items!
     * REGEX-TEST: §cYou must collect 300B all-time Chocolate!
     */
    private val chatMessagePattern by patternGroup.list(
        "chat.hide",
        "§cYou don't have enough Chocolate!",
        "§cYou don't have the required items!",
        "§cYou must collect (.*) all-time Chocolate!",
    )

    @HandleEvent
    fun onChat(event: SystemMessageEvent) {
        if (!isEnabled()) return
        if (!CFApi.inChocolateFactory) return
        if (configReminder.hideChat) {
            if (chatMessagePattern.matches(event.message)) {
                event.blockedReason = "custom_reminder"
            }
        }
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!isEnabled()) return
        update()
    }

    @HandleEvent(receiveCancelled = true)
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        if (!isEnabled()) return
        val item = event.item ?: return
        if (event.clickedButton != 0) return
        val (cost, name) = getCostAndName(item) ?: return
        val duration = ChocolateAmount.CURRENT.timeUntilGoal(cost)

        // the user has enough chocolate, and just bought something
        if (duration.isNegative()) {
            reset()
            return
        }
        setReminder(cost, name)
    }

    // TODO add support for prestige
    private fun getCostAndName(item: ItemStack): Pair<Long, String>? {
        val list = item.getLore()
        val cost = CFApi.getChocolateBuyCost(list)
            ?: return milestoneCostLorePattern.firstMatcher(list) {
                // math needed to get from "time until current chocolate" to "time until all time chocolate"
                val amount = group("amount").formatLong()
                val allTime = ChocolateAmount.ALL_TIME.chocolate()
                val missingAllTime = amount - allTime
                val current = ChocolateAmount.CURRENT.chocolate()
                val missing = missingAllTime + current

                missing to "§6${amount.shortFormat()} Chocolate Milestone"
            }

        val nextLevelName = CFApi.getNextLevelName(item) ?: item.displayName
        return cost to nextLevelName
    }

    @HandleEvent
    fun onBackgroundDraw(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (!isEnabled()) return
        if (!inChocolateMenu()) return
        if (ReminderUtils.isBusy()) return

        configReminder.position.renderRenderables(display, posLabel = "Chocolate Factory Custom Reminder")
    }

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!isEnabled()) return
        if (!configReminder.always) return
        if (Minecraft.getMinecraft().currentScreen is GuiChest) return
        if (ReminderUtils.isBusy()) return

        configReminder.position.renderRenderables(display, posLabel = "Chocolate Factory Custom Reminder")
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.transform(72, "inventory.chocolateFactory.customReminder.position", Position::migrate)
    }

    private fun inChocolateMenu() = CFShopPrice.inInventory || CFApi.inChocolateFactory ||
        CFApi.chocolateFactoryPaused

    private fun setReminder(target: Long, name: String) {
        if (targetName == name) {
            reset()
            return
        }
        targetGoal = target
        targetName = name
        update()
    }

    private fun update() {
        display = mutableListOf<Renderable>().also { list ->
            getTargetDescription()?.let {
                list.add(
                    Renderable.clickable(
                        it,
                        tips = listOf("§eClick to remove the goal!"),
                        onLeftClick = {
                            reset()
                        },
                    ),
                )
            }
        }
    }

    private fun getTargetDescription(): String? {
        val goal = targetGoal ?: return null
        val duration = ChocolateAmount.CURRENT.timeUntilGoal(goal)
        if (duration.isNegative()) {
            warn()
            return "§aGoal Reached! §eBuy §f$targetName"
        }
        val format = duration.format(maxUnits = 2)
        return "§f$targetName §ein §b$format".partyModeReplace()
    }

    private fun warn() {
        if (ReminderUtils.isBusy()) return
        if (inChocolateMenu()) return

        if (lastUpgradeWarning.passedSince() < configUpgradeWarnings.timeBetweenWarnings.minutes) return
        lastUpgradeWarning = SimpleTimeMark.now()

        if (configUpgradeWarnings.upgradeWarningSound) {
            SoundUtils.playBeepSound()
        }
        ChatUtils.clickToActionOrDisable(
            "You can now purchase §f$targetName §ein Chocolate factory!",
            configReminder::enabled,
            actionName = "open Chocolate Factory",
            action = { HypixelCommands.chocolateFactory() },
        )
    }

    private fun reset() {
        targetGoal = null
        targetName = ""

        display = emptyList()
    }

    fun isEnabled() = LorenzUtils.inSkyBlock && configReminder.enabled
}
