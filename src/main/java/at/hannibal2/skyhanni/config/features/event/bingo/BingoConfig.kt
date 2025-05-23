package at.hannibal2.skyhanni.config.features.event.bingo

import at.hannibal2.skyhanni.config.FeatureToggle
import at.hannibal2.skyhanni.config.core.config.Position
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class BingoConfig {
    @Expose
    @ConfigOption(name = "Bingo Card", desc = "")
    @Accordion
    var bingoCard: BingoCardConfig = BingoCardConfig()

    @Expose
    @ConfigOption(name = "Compact Chat Messages", desc = "")
    @Accordion
    var compactChat: CompactChatConfig = CompactChatConfig()

    // TODO move into own category
    @Expose
    @ConfigOption(
        name = "Minion Craft Helper",
        desc = "Show how many more items you need to upgrade the minion in your inventory. Especially useful for Bingo."
    )
    @ConfigEditorBoolean
    @FeatureToggle
    var minionCraftHelperEnabled: Boolean = true

    @Expose
    @ConfigOption(
        name = "Show Progress to T1",
        desc = "Show tier 1 Minion Crafts in the Helper display even if needed items are not fully collected."
    )
    @ConfigEditorBoolean
    @FeatureToggle
    var minionCraftHelperProgressFirst: Boolean = false

    @Expose
    @ConfigLink(owner = BingoConfig::class, field = "minionCraftHelperEnabled")
    var minionCraftHelperPos: Position = Position(10, 10)

    @Expose
    @ConfigOption(
        name = "Boop Party",
        desc = "Send party invite to players that boop you while you are on a Bingo profile."
    )
    @ConfigEditorBoolean
    @FeatureToggle
    var boopParty: Boolean = false
}
