package at.hannibal2.skyhanni.config.features.rift

import at.hannibal2.skyhanni.config.FeatureToggle
import at.hannibal2.skyhanni.config.core.config.Position
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class PunchcardConfig {
    @Expose
    @ConfigOption(name = "Highlight", desc = "Highlights unpunched players in the Rift.")
    @ConfigEditorBoolean
    @FeatureToggle
    var highlight: Property<Boolean> = Property.of(false)

    @Expose
    @ConfigOption(name = "Color", desc = "Color used for highlighting.")
    @ConfigEditorColour
    var color: Property<String> = Property.of("0:163:122:11:143")

    @Expose
    @ConfigOption(name = "Enable Overlay", desc = "Shows an overlay with the amount of punched players.")
    @ConfigEditorBoolean
    @FeatureToggle
    var gui: Property<Boolean> = Property.of(false)

    @Expose
    @ConfigOption(name = "Compact Overlay", desc = "Compacts the overlay, requires it to be enabled.")
    @ConfigEditorBoolean
    var compact: Property<Boolean> = Property.of(false)

    // TODO rename to reverseGui
    @Expose
    @ConfigOption(name = "Countdown Overlay", desc = "Shows the amount of remaining players in the overlay.")
    @ConfigEditorBoolean
    var reverseGUI: Property<Boolean> = Property.of(false)

    @Expose
    @ConfigOption(name = "Only punched players", desc = "Highlights only punched players instead.")
    @ConfigEditorBoolean
    var reverse: Property<Boolean> = Property.of(false)

    @Expose
    @ConfigLink(owner = PunchcardConfig::class, field = "gui")
    var position: Position = Position(10, 27)
}
