package at.hannibal2.skyhanni.config.features.garden.cropmilestones

import at.hannibal2.skyhanni.config.FeatureToggle
import at.hannibal2.skyhanni.config.HasLegacyId
import at.hannibal2.skyhanni.config.core.config.Position
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

// TODO moulconfig runnable support
class MushroomPetPerkConfig {
    @Expose
    @ConfigOption(
        name = "Display Enabled",
        desc = "Show the progress and ETA for mushroom crops when farming other crops because of the Mooshroom Cow perk.",
    )
    @ConfigEditorBoolean
    @FeatureToggle
    var enabled: Boolean = true

    @Expose
    @ConfigOption(
        name = "Mushroom Text",
        desc = "Drag text to change the appearance of the overlay.\n" + "Hold a farming tool to show the overlay.",
    )
    @ConfigEditorDraggableList
    var text: MutableList<MushroomTextEntry> = mutableListOf(
        MushroomTextEntry.TITLE,
        MushroomTextEntry.MUSHROOM_TIER,
        MushroomTextEntry.NUMBER_OUT_OF_TOTAL,
        MushroomTextEntry.TIME,
    )

    enum class MushroomTextEntry(
        private val displayName: String,
        private val legacyId: Int = -1,
    ) : HasLegacyId {
        TITLE("§6Mooshroom Cow Perk", 0),
        MUSHROOM_TIER("§7Mushroom Milestone 8", 1), // TODO Change MUSHROOM_TIER to MUSHROOM_MILESTONE
        NUMBER_OUT_OF_TOTAL("§e6,700§8/§e15,000", 2),
        TIME("§7In §b12m 34s", 3),
        PERCENTAGE("§7Percentage: §e12.34%", 4),
        ;

        override fun getLegacyId() = legacyId
        override fun toString() = displayName
    }

    @Expose
    @ConfigLink(owner = MushroomPetPerkConfig::class, field = "enabled")
    var pos: Position = Position(-112, -143)
}
