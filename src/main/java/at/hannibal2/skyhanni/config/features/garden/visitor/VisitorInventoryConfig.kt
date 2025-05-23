package at.hannibal2.skyhanni.config.features.garden.visitor

import at.hannibal2.skyhanni.config.FeatureToggle
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class VisitorInventoryConfig {
    @Expose
    @ConfigOption(
        name = "Visitor Price",
        desc = "Show the Bazaar price of the items required for the visitors, like in NEU."
    )
    @ConfigEditorBoolean
    @FeatureToggle
    var showPrice: Boolean = false

    @Expose
    @ConfigOption(
        name = "Amount and Time",
        desc = "Show the exact item amount and the remaining time when farmed manually. Especially useful for Ironman."
    )
    @ConfigEditorBoolean
    var exactAmountAndTime: Boolean = true

    @Expose
    @ConfigOption(name = "Copper Price", desc = "Show the price per copper inside the visitor GUI.")
    @ConfigEditorBoolean
    @FeatureToggle
    var copperPrice: Boolean = true

    @Expose
    @ConfigOption(name = "Copper Time", desc = "Show the time required per copper inside the visitor GUI.")
    @ConfigEditorBoolean
    @FeatureToggle
    var copperTime: Boolean = false

    @Expose
    @ConfigOption(name = "Garden Exp Price", desc = "Show the price per garden experience inside the visitor GUI.")
    @ConfigEditorBoolean
    @FeatureToggle
    var experiencePrice: Boolean = false
}
