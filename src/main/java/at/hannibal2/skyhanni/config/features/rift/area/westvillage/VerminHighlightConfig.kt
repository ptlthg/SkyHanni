package at.hannibal2.skyhanni.config.features.rift.area.westvillage

import at.hannibal2.skyhanni.config.FeatureToggle
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class VerminHighlightConfig {
    @Expose
    @ConfigOption(name = "Enabled", desc = "Highlight Vermins in the West Village.")
    @ConfigEditorBoolean
    @FeatureToggle
    var enabled: Boolean = true

    @Expose
    @ConfigOption(name = "Color", desc = "Change Vermin highlight color.")
    @ConfigEditorColour
    var color: Property<String> = Property.of("0:60:0:0:255")
}
