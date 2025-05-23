package at.hannibal2.skyhanni.features.inventory.chocolatefactory

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.GuiKeyPressEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyClicked
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import net.minecraft.client.gui.inventory.GuiChest
import kotlin.time.Duration.Companion.milliseconds

@SkyHanniModule
object CFKeybinds {
    private val config get() = CFApi.config.keybinds
    private var lastClick = SimpleTimeMark.farPast()

    @HandleEvent(onlyOnSkyblock = true)
    fun onKeyPress(event: GuiKeyPressEvent) {
        if (!config.enabled) return
        if (!CFApi.inChocolateFactory) return

        val chest = event.guiContainer as? GuiChest ?: return

        for (index in 0..6) {
            val key = getKey(index) ?: error("no key for index $index")
            if (!key.isKeyClicked()) continue
            if (lastClick.passedSince() < 200.milliseconds) break
            lastClick = SimpleTimeMark.now()

            event.cancel()

            InventoryUtils.clickSlot(28 + index, chest.inventorySlots.windowId, mouseButton = 2, mode = 3)
            break
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        if (!config.enabled) return
        if (!CFApi.inChocolateFactory) return

        // needed to not send duplicate clicks via keybind feature
        if (event.clickType == GuiContainerEvent.ClickType.HOTBAR) {
            event.cancel()
        }
    }

    private fun getKey(index: Int) = when (index) {
        0 -> config.key1
        1 -> config.key2
        2 -> config.key3
        3 -> config.key4
        4 -> config.key5
        5 -> config.key6
        6 -> config.key7
        else -> null
    }
}
