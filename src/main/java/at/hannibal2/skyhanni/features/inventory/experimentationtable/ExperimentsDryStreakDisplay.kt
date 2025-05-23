package at.hannibal2.skyhanni.features.inventory.experimentationtable

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.api.event.HandleEvent.Companion.HIGH
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryOpenEvent
import at.hannibal2.skyhanni.events.InventoryUpdatedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.features.inventory.experimentationtable.ExperimentationTableApi.bookPattern
import at.hannibal2.skyhanni.features.inventory.experimentationtable.ExperimentationTableApi.ultraRarePattern
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils.renderStrings
import at.hannibal2.skyhanni.utils.StringUtils.removeColor

@SkyHanniModule
object ExperimentsDryStreakDisplay {

    private val config get() = SkyHanniMod.feature.inventory.experimentationTable.dryStreak
    private val storage get() = ProfileStorageData.profileSpecific?.experimentation?.dryStreak

    private var display = emptyList<String>()

    private var didJustFind = false

    @HandleEvent
    fun onBackgroundDraw(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (!isEnabled()) return
        if (!ExperimentationTableApi.inventoriesPattern.matches(InventoryUtils.openInventoryName())) return

        display = drawDisplay()
        config.position.renderStrings(
            display,
            posLabel = "Experimentation Table Dry Streak",
        )
    }

    @HandleEvent
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (event.inventoryName == "Experimentation Table" && didJustFind) didJustFind = false
    }

    @HandleEvent
    fun onInventoryUpdated(event: InventoryUpdatedEvent) {
        if (!isEnabled() || didJustFind || ExperimentationTableApi.currentExperiment == null) return

        for (lore in event.inventoryItems.map { it.value.getLore() }) {
            val firstLine = lore.firstOrNull() ?: continue
            if (!ultraRarePattern.matches(firstLine)) continue
            val bookNameLine = lore.getOrNull(2) ?: continue
            bookPattern.matchMatcher(bookNameLine) {
                val storage = storage ?: return
                ChatUtils.chat(
                    "§a§lDRY-STREAK ENDED! §eYou have (finally) " +
                        "found a §5ULTRA-RARE §eafter §3${storage.xpSince.shortFormat()} Enchanting Exp " +
                        "§eand §2${storage.attemptsSince} attempts§e!",
                )
                storage.attemptsSince = 0
                storage.xpSince = 0
                didJustFind = true
            }
        }
    }

    @HandleEvent(priority = HIGH)
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (didJustFind || ExperimentationTableApi.currentExperiment == null) return

        val storage = storage ?: return
        storage.attemptsSince += 1
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!isEnabled() || didJustFind) return

        ExperimentationTableApi.enchantingExpChatPattern.matchMatcher(event.message.removeColor()) {
            val storage = storage ?: return
            storage.xpSince += group("amount").substringBefore(",").toInt() * 1000
        }
    }

    private fun drawDisplay() = buildList {
        val storage = storage ?: return@buildList

        add("§cDry-Streak since last §5ULTRA-RARE")

        val colorPrefix = "§e"
        val attemptsSince = storage.attemptsSince
        val xpSince = storage.xpSince.shortFormat()
        val attemptsSuffix = if (attemptsSince == 1) "" else "s"

        if (config.attemptsSince && config.xpSince) {
            add("$colorPrefix ├ $attemptsSince Attempt$attemptsSuffix")
            add("$colorPrefix └ $xpSince XP")
        } else if (config.attemptsSince) {
            add("$colorPrefix └ $attemptsSince Attempt$attemptsSuffix")
        } else {
            add("$colorPrefix └ $xpSince XP")
        }
    }

    private fun isEnabled() =
        LorenzUtils.inSkyBlock && config.enabled && (config.xpSince || config.attemptsSince)
}
