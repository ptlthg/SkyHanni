package at.hannibal2.skyhanni.config.features.garden

import at.hannibal2.skyhanni.config.FeatureToggle
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import org.lwjgl.input.Keyboard

class GardenCommandsConfig {
    @Expose
    @ConfigOption(
        name = "Warp Commands",
        desc = "Enable commands §e/home§7, §e/barn §7and §e/tp <plot>§7. §cOnly works while in the garden."
    )
    @ConfigEditorBoolean
    @FeatureToggle
    var warpCommands: Boolean = true

    @Expose
    @ConfigOption(
        name = "Home Hotkey",
        desc = "Press this key to teleport you to your Garden home. §cOnly works while in the garden."
    )
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NONE)
    var homeHotkey: Int = Keyboard.KEY_NONE

    @Expose
    @ConfigOption(
        name = "Sethome Hotkey",
        desc = "Press this key to set your Garden home. §cOnly works while in the garden."
    )
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NONE)
    var sethomeHotkey: Int = Keyboard.KEY_NONE

    @Expose
    @ConfigOption(
        name = "Barn Hotkey",
        desc = "Press this key to teleport you to the Garden barn. §cOnly works while in the garden."
    )
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NONE)
    var barnHotkey: Int = Keyboard.KEY_NONE
}
