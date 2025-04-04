package at.hannibal2.skyhanni.features.inventory.bazaar

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.features.chat.ShortenCoins.formatChatCoins
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatInt
import at.hannibal2.skyhanni.utils.OSUtils
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object BazaarCancelledBuyOrderClipboard {

    private val patternGroup = RepoPattern.group("bazaar.cancelledorder")

    /**
     * REGEX-TEST: §6§7from §a50§7x §7missing items.
     * REGEX-TEST: §7§a22§7x §7missing items.
     * REGEX-TEST: §6coins §7from §a2,000§7x §7missing items.
     */
    private val lastAmountPattern by patternGroup.pattern(
        "lastamount",
        "(?:§6coins §7from |§6§7from |§7)§a(?<amount>.*)§7x §7missing items\\.",
    )
    private val cancelledMessagePattern by patternGroup.pattern(
        "cancelledmessage",
        "§6\\[Bazaar] §r§7§r§cCancelled! §r§7Refunded §r§6(?<coins>.*) coins §r§7from cancelling Buy Order!",
    )
    private val inventoryTitlePattern by patternGroup.pattern(
        "inventorytitle",
        "Order options",
    )

    private var latestAmount: Int? = null

    @HandleEvent
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        if (!isEnabled()) return
        if (!inventoryTitlePattern.matches(event.inventoryName)) return
        val stack = event.inventoryItems[11] ?: return
        if (!stack.displayName.contains("Cancel Order")) return

        val lore = stack.getLore()
        lastAmountPattern.firstMatcher(lore) {
            latestAmount = group("amount").formatInt()
            return
        }

        // nothing to cancel
        if (lore.firstOrNull() == "§7Cannot cancel order while there are") {
            return
        }

        ErrorManager.logErrorStateWithData(
            "BazaarCancelledBuyOrderClipboard error",
            "lastAmountPattern can not find latestAmount",
            "lore" to lore,
        )
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!isEnabled()) return
        val coins = cancelledMessagePattern.matchMatcher(event.message) {
            group("coins").formatInt()
        } ?: return

        val latestAmount = latestAmount ?: return
        event.blockedReason = "bazaar cancelled buy order clipboard"
        val lastClicked = BazaarApi.orderOptionProduct
            ?: ErrorManager.skyHanniError("Cancel buy order clipboard could not detect the last bazaar product.")

        val message = "Bazaar buy order cancelled. Click to re-order.\n" +
            "§e(§8${latestAmount.addSeparators()}x §r${lastClicked.repoItemName}§e for ${coins.formatChatCoins()} coins§e)"
        ChatUtils.clickableChat(
            message,
            onClick = {
                BazaarApi.searchForBazaarItem(lastClicked, latestAmount)
            },
        )
        OSUtils.copyToClipboard(latestAmount.toString())
        this.latestAmount = null
    }

    fun isEnabled() = LorenzUtils.inSkyBlock && SkyHanniMod.feature.inventory.bazaar.cancelledBuyOrderClipboard
}
