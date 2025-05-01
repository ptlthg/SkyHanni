package at.hannibal2.skyhanni.config.features.itemability

import at.hannibal2.skyhanni.config.FeatureToggle
import at.hannibal2.skyhanni.config.core.config.Position
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class ChickenHeadConfig {
    @Expose
    @ConfigOption(
        name = "Chicken Head Timer",
        desc = "Show the cooldown until the next time you can lay an egg with the Chicken Head."
    )
    @ConfigEditorBoolean
    @FeatureToggle
    var displayTimer: Boolean = false

    @Expose
    @ConfigLink(owner = ChickenHeadConfig::class, field = "displayTimer")
    var position: Position = Position(-372, 73)

    @Expose
    @ConfigOption(name = "Hide Chat", desc = "Hide the 'You laid an egg!' chat message.")
    @ConfigEditorBoolean
    @FeatureToggle
    var hideChat: Boolean = true
}
