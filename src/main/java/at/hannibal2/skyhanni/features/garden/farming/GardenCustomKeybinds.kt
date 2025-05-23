package at.hannibal2.skyhanni.features.garden.farming

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ConditionalUtils
import at.hannibal2.skyhanni.utils.KeyboardManager
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyClicked
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import io.github.notenoughupdates.moulconfig.observer.Property
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiEditSign
import net.minecraft.client.settings.KeyBinding
import org.lwjgl.input.Keyboard
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object GardenCustomKeybinds {

    private val config get() = GardenApi.config.keyBind
    private val mcSettings get() = Minecraft.getMinecraft().gameSettings

    private var map: Map<KeyBinding, Int> = emptyMap()
    private var lastWindowOpenTime = SimpleTimeMark.farPast()
    private var lastDuplicateKeybindsWarnTime = SimpleTimeMark.farPast()
    private var isDuplicate = false

    @JvmStatic
    fun isKeyDown(keyBinding: KeyBinding, cir: CallbackInfoReturnable<Boolean>) {
        if (!isActive()) return
        val override = map[keyBinding] ?: run {
            if (map.containsValue(keyBinding.keyCode)) {
                cir.returnValue = false
            }
            return
        }

        cir.returnValue = override.isKeyHeld()
    }

    @JvmStatic
    fun isKeyPressed(keyBinding: KeyBinding, cir: CallbackInfoReturnable<Boolean>) {
        if (!isActive()) return
        val override = map[keyBinding] ?: run {
            if (map.containsValue(keyBinding.keyCode)) {
                cir.returnValue = false
            }
            return
        }
        cir.returnValue = override.isKeyClicked()
    }

    @HandleEvent
    fun onTick() {
        if (!isEnabled()) return
        val screen = Minecraft.getMinecraft().currentScreen ?: return
        if (screen !is GuiEditSign) return
        lastWindowOpenTime = SimpleTimeMark.now()
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!isEnabled()) return
        if (!isDuplicate || lastDuplicateKeybindsWarnTime.passedSince() < 30.seconds) return
        ChatUtils.chatAndOpenConfig(
            "Duplicate Custom Keybinds aren't allowed!",
            GardenApi.config::keyBind,
        )
        lastDuplicateKeybindsWarnTime = SimpleTimeMark.now()
    }

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        with(config) {
            ConditionalUtils.onToggle(attack, useItem, left, right, forward, back, jump, sneak) {
                update()
            }
            update()
        }
    }

    private fun update() {
        with(config) {
            with(mcSettings) {
                map = buildMap {
                    fun add(keyBinding: KeyBinding, property: Property<Int>) {
                        put(keyBinding, property.get())
                    }
                    add(keyBindAttack, attack)
                    add(keyBindUseItem, useItem)
                    add(keyBindLeft, left)
                    add(keyBindRight, right)
                    add(keyBindForward, forward)
                    add(keyBindBack, back)
                    add(keyBindJump, jump)
                    add(keyBindSneak, sneak)
                }
            }
        }
        calculateDuplicates()
        lastDuplicateKeybindsWarnTime = SimpleTimeMark.farPast()
        KeyBinding.unPressAllKeys()
    }

    private fun calculateDuplicates() {
        isDuplicate = map.values
            .filter { it != Keyboard.KEY_NONE }
            .let { values -> values.size != values.toSet().size }
    }

    private fun isEnabled() = GardenApi.inGarden() && config.enabled && !(GardenApi.onBarnPlot && config.excludeBarn)

    private fun isActive(): Boolean =
        isEnabled() && GardenApi.toolInHand != null && !isDuplicate && !hasGuiOpen() && lastWindowOpenTime.passedSince() > 300.milliseconds

    private fun hasGuiOpen() = Minecraft.getMinecraft().currentScreen != null

    @JvmStatic
    fun disableAll() {
        with(config) {
            attack.set(Keyboard.KEY_NONE)
            useItem.set(Keyboard.KEY_NONE)
            left.set(Keyboard.KEY_NONE)
            right.set(Keyboard.KEY_NONE)
            forward.set(Keyboard.KEY_NONE)
            back.set(Keyboard.KEY_NONE)
            jump.set(Keyboard.KEY_NONE)
            sneak.set(Keyboard.KEY_NONE)
        }
    }

    @JvmStatic
    fun defaultAll() {
        with(config) {
            attack.set(KeyboardManager.LEFT_MOUSE)
            useItem.set(KeyboardManager.RIGHT_MOUSE)
            left.set(Keyboard.KEY_A)
            right.set(Keyboard.KEY_D)
            forward.set(Keyboard.KEY_W)
            back.set(Keyboard.KEY_S)
            jump.set(Keyboard.KEY_SPACE)
            sneak.set(Keyboard.KEY_LSHIFT)
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(3, "garden.keyBindEnabled", "garden.keyBind.enabled")
        event.move(3, "garden.keyBindAttack", "garden.keyBind.attack")
        event.move(3, "garden.keyBindUseItem", "garden.keyBind.useItem")
        event.move(3, "garden.keyBindLeft", "garden.keyBind.left")
        event.move(3, "garden.keyBindRight", "garden.keyBind.right")
        event.move(3, "garden.keyBindForward", "garden.keyBind.forward")
        event.move(3, "garden.keyBindBack", "garden.keyBind.back")
        event.move(3, "garden.keyBindJump", "garden.keyBind.jump")
        event.move(3, "garden.keyBindSneak", "garden.keyBind.sneak")
    }
}
