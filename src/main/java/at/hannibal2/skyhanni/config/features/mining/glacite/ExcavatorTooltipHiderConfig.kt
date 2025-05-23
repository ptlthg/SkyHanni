package at.hannibal2.skyhanni.config.features.mining.glacite

import at.hannibal2.skyhanni.config.FeatureToggle
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class ExcavatorTooltipHiderConfig {
    @Expose
    @ConfigOption(name = "Hide Dirt", desc = "Hides tooltips of the Dirt inside of the Fossil Excavator.")
    @ConfigEditorBoolean
    @FeatureToggle
    var hideDirt: Boolean = true

    @Expose
    @ConfigOption(name = "Hide Everything", desc = "Hide all tooltips inside of the Fossil Excavator.")
    @ConfigEditorBoolean
    @FeatureToggle
    var hideEverything: Boolean = false
}
