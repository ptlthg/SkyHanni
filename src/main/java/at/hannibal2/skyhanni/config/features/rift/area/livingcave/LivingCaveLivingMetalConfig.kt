package at.hannibal2.skyhanni.config.features.rift.area.livingcave

import at.hannibal2.skyhanni.config.FeatureToggle
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

class LivingCaveLivingMetalConfig {
    @Expose
    @ConfigOption(name = "Living Metal", desc = "Show a moving animation between Living Metal and the next block.")
    @ConfigEditorBoolean
    @FeatureToggle
    var enabled: Boolean = true

    @Expose
    @ConfigOption(name = "Hide Particles", desc = "Hide Living Metal particles.")
    @ConfigEditorBoolean
    @FeatureToggle
    var hideParticles: Boolean = false

    @Expose
    @ConfigOption(name = "Color", desc = "Set the color to highlight the blocks in.")
    @ConfigEditorColour
    var color: Property<String> = Property.of("0:255:85:255:255")
}
