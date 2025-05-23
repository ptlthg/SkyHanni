package at.hannibal2.skyhanni.config.features.chat

import at.hannibal2.skyhanni.config.FeatureToggle
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class StashConfig {
    @Expose
    @ConfigOption(name = "Stash Warnings", desc = "Compact warnings relating to items/materials in your stash.")
    @ConfigEditorBoolean
    @FeatureToggle
    var enabled: Boolean = true

    @ConfigOption(
        name = "§cNotice",
        desc = "Hypixel sends un-detectable empty messages wrapping the stash message. " +
            "Enable §e§l/sh empty messages §r§7to hide them."
    )
    @ConfigEditorInfoText
    var notice: String = ""

    @Expose
    @ConfigOption(name = "Hide Dupe Warnings", desc = "")
    @Accordion
    var hideDuplicateWarning: HideDuplicateWarningConfig = HideDuplicateWarningConfig()

    class HideDuplicateWarningConfig {
        @Expose
        @ConfigOption(
            name = "Enabled",
            desc = "Hide duplicate warnings for previously reported stash counts."
        )
        @ConfigEditorBoolean
        var enabled: Boolean = true

        @Expose
        @ConfigOption(
            name = "Once Per World",
            desc = "Show warnings even if the counts are previously reported, once per world change."
        )
        @ConfigEditorBoolean
        var worldChangeReset: Boolean = true
    }

    @Expose
    @ConfigOption(name = "Hide Added Messages", desc = "Hide the messages when something is added to your stash.")
    @ConfigEditorBoolean
    var hideAddedMessages: Boolean = true

    @Expose
    @ConfigOption(name = "Hide Low Warnings", desc = "Hide warnings with a total count below this number.")
    @ConfigEditorSlider(minValue = 0f, maxValue = 1_000_000f, minStep = 100f)
    var hideLowWarningsThreshold: Int = 0

    @Expose
    @ConfigOption(name = "Use /ViewStash", desc = "Use /viewstash [type] instead of /pickupstash.")
    @ConfigEditorBoolean
    var useViewStash: Boolean = false

    @Expose
    @ConfigOption(name = "Disable Empty Warnings", desc = "Disable first-time warnings for empty messages left behind.")
    @ConfigEditorBoolean
    var disableEmptyWarnings: Boolean = false
}
