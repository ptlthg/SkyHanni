package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.GuiKeyPressEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.NeuRenderEvent
import at.hannibal2.skyhanni.events.minecraft.ClientDisconnectEvent
import at.hannibal2.skyhanni.events.render.gui.GuiMouseInputEvent
import at.hannibal2.skyhanni.events.render.gui.GuiScreenOpenEvent
import at.hannibal2.skyhanni.features.inventory.wardrobe.CustomWardrobeKeybinds
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.KeyboardManager.isActive
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import at.hannibal2.skyhanni.utils.system.PlatformUtils
import io.github.moulberry.notenoughupdates.NEUApi
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiChest
import org.lwjgl.input.Keyboard

@SkyHanniModule
object GuiData {

    var preDrawEventCancelled = false

    @HandleEvent(priority = HandleEvent.HIGH)
    fun onNeuRenderEvent(event: NeuRenderEvent) {
        if (preDrawEventCancelled) event.cancel()
    }

    @HandleEvent(priority = HandleEvent.HIGH)
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        if (preDrawEventCancelled) event.cancel()
    }

    @HandleEvent
    fun onMouseInput(event: GuiMouseInputEvent) {
        if (CustomWardrobeKeybinds.allowMouseClick()) return

        if (preDrawEventCancelled) event.cancel()
    }

    @HandleEvent(priority = HandleEvent.HIGHEST)
    fun onGuiKeyPress(event: GuiKeyPressEvent) {
        val allowedKeys = with(Minecraft.getMinecraft().gameSettings) {
            listOf(
                keyBindInventory,
                keyBindScreenshot,
                keyBindFullscreen,
            )
        }
        if (allowedKeys.any { it.isActive() }) return
        if (Keyboard.KEY_ESCAPE.isKeyHeld()) return

        if (CustomWardrobeKeybinds.allowKeyboardClick()) return

        if (preDrawEventCancelled) event.cancel()
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        DelayedRun.runNextTick {
            if (Minecraft.getMinecraft().currentScreen !is GuiChest) {
                preDrawEventCancelled = false
            }
        }
    }

    @HandleEvent
    fun onWorldChange() {
        preDrawEventCancelled = false
    }

    @HandleEvent
    fun onDisconnect(event: ClientDisconnectEvent) {
        preDrawEventCancelled = false
    }

    @HandleEvent(priority = HandleEvent.LOW)
    fun onGuiOpen(event: GuiScreenOpenEvent) {
        if (preDrawEventCancelled) {
            if (PlatformUtils.isNeuLoaded()) NEUApi.setInventoryButtonsToDisabled()
        }
    }
}
