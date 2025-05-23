package at.hannibal2.skyhanni.config.features.commands

import at.hannibal2.skyhanni.config.FeatureToggle
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class CommandsConfig {
    @ConfigOption(name = "Tab Complete", desc = "")
    @Accordion
    @Expose
    var tabComplete: TabCompleteConfig = TabCompleteConfig()

    @ConfigOption(name = "Better §e/wiki", desc = "")
    @Accordion
    @Expose
    var betterWiki: BetterWikiCommandConfig = BetterWikiCommandConfig()

    @ConfigOption(name = "Reverse Party Transfer", desc = "")
    @Accordion
    @Expose
    var reversePT: ReversePartyTransferConfig = ReversePartyTransferConfig()

    @ConfigOption(
        name = "Party Commands",
        desc = "Shortens party commands and allows tab-completing for them. " +
            "§eCommands: /pt, /pp, /pko, /pk, /pd §7(SkyBlock command §e/pt §7to check your play time will still work)"
    )
    @Expose
    @ConfigEditorBoolean
    @FeatureToggle
    var shortCommands: Boolean = true

    @Expose
    @ConfigOption(
        name = "Accept Last Invite",
        desc = "Automatically accept the latest party invite if no player is specified with /p accept.",
    )
    @ConfigEditorBoolean
    @FeatureToggle
    var acceptLastInvite: Boolean = true

    @ConfigOption(
        name = "Party Kick Reason",
        desc = "Send a reason when kicking people using §e/pk lrg89 Dupe Archer §7or §e/party kick nea89o Low Cata Level§7."
    )
    @Expose
    @ConfigEditorBoolean
    @FeatureToggle
    var partyKickReason: Boolean = true

    @ConfigOption(
        name = "Shorten §e/warp",
        desc = "Allows warping without the need for the §ewarp §7prefix.\n(§e/warp wizard §7-> §e/wizard§7)"
    )
    @Expose
    @ConfigEditorBoolean
    @FeatureToggle
    var shortenWarp: Boolean = false

    @Expose
    @ConfigOption(name = "Replace §e/warp is", desc = "Add §e/warp is §7alongside §e/is§7. Idk why. Ask §cKaeso")
    @ConfigEditorBoolean
    @FeatureToggle
    var replaceWarpIs: Boolean = false

    @Expose
    @ConfigOption(
        name = "Lower Case §e/viewrecipe",
        desc = "Add support for lower case item IDs to the Hypixel command §e/viewrecipe§7."
    )
    @ConfigEditorBoolean
    @FeatureToggle
    var viewRecipeLowerCase: Boolean = true

    @Expose
    @ConfigOption(
        name = "Fix Transfer Cooldown",
        desc = "Waits for the transfer cooldown to complete if you try to warp."
    )
    @ConfigEditorBoolean
    @FeatureToggle
    var transferCooldown: Boolean = false

    @Expose
    @ConfigOption(name = "Transfer Cooldown Message", desc = "Sends a message in chat when the transfer cooldown ends.")
    @ConfigEditorBoolean
    var transferCooldownMessage: Boolean = false
}
