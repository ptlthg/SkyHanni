package at.hannibal2.skyhanni.features.inventory

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.minecraft.ToolTipEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RegexUtils.anyMatches
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import org.intellij.lang.annotations.Language

private val patternGroup = RepoPattern.group("skyblockguide.highlight")

private const val KEY_PREFIX_INVENTORY = "inventory"
private const val KEY_PREFIX_CONDITION = "condition"

class SkyblockGuideHighlightFeature private constructor(
    private val config: () -> Boolean,
    inventory: RepoPattern,
    loreCondition: RepoPattern,
    private val onSlotClicked: (GuiContainerEvent.SlotClickEvent) -> Unit = {},
    private val onTooltip: (ToolTipEvent) -> Unit = {},
) {

    private val inventoryPattern by inventory
    private val conditionPattern by loreCondition

    private constructor(
        config: () -> Boolean,
        key: String,
        @Language("RegExp") inventory: String,
        @Language("RegExp") loreCondition: String,
        onSlotClicked: (GuiContainerEvent.SlotClickEvent) -> Unit = {},
        onTooltip: (ToolTipEvent) -> Unit = {},
    ) : this(
        config,
        patternGroup.pattern("$key.$KEY_PREFIX_INVENTORY", inventory),
        patternGroup.pattern("$key.$KEY_PREFIX_CONDITION", loreCondition),
        onSlotClicked,
        onTooltip,
    )

    private constructor(
        config: () -> Boolean,
        key: String,
        @Language("RegExp") inventory: String,
        loreCondition: RepoPattern,
        onSlotClicked: (GuiContainerEvent.SlotClickEvent) -> Unit = {},
        onTooltip: (ToolTipEvent) -> Unit = {},
    ) : this(
        config,
        patternGroup.pattern("$key.$KEY_PREFIX_INVENTORY", inventory),
        loreCondition,
        onSlotClicked,
        onTooltip,
    )

    init {
        objectList.add(this)
    }

    @SkyHanniModule
    companion object {

        private val skyblockGuideConfig get() = SkyHanniMod.feature.inventory.skyblockGuideConfig

        private val objectList = mutableListOf<SkyblockGuideHighlightFeature>()

        private var activeObject: SkyblockGuideHighlightFeature? = null
        private val missing = mutableSetOf<Int>()

        fun isEnabled() = LorenzUtils.inSkyBlock
        fun close() {
            activeObject = null
        }

        @HandleEvent
        fun onInventoryClose(event: InventoryCloseEvent) {
            close()
        }

        @HandleEvent
        fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
            if (!isEnabled()) return
            val current = activeObject ?: return
            if (!missing.contains(event.slotId)) return
            current.onSlotClicked.invoke(event)
        }

        @HandleEvent
        fun onBackgroundDrawn(event: GuiContainerEvent.BackgroundDrawnEvent) {
            if (!isEnabled()) return
            if (activeObject == null) return

            event.container.inventorySlots
                .filter { missing.contains(it.slotNumber) }
                .forEach { it.highlight(LorenzColor.RED) }
        }

        @HandleEvent
        fun onTooltip(event: ToolTipEvent) {
            if (!isEnabled()) return
            val current = activeObject ?: return
            if (!missing.contains(event.slot.slotNumber)) return
            current.onTooltip.invoke(event)
        }

        @HandleEvent
        fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
            if (!isEnabled()) return
            val current =
                objectList.firstOrNull { it.config.invoke() && it.inventoryPattern.matches(event.inventoryName) }
                    ?: return

            missing.clear()
            activeObject = current

            for ((slot, item) in event.inventoryItems) {
                if (slot == 4) continue // Overview Item
                val loreAndName = listOf(item.displayName) + item.getLore()
                if (!current.conditionPattern.anyMatches(loreAndName)) continue
                missing.add(slot)
            }
        }

        private val taskOnlyCompleteOncePattern =
            patternGroup.pattern("$KEY_PREFIX_CONDITION.once", "§7§eThis task can only be completed once!")
        private val xPattern = patternGroup.pattern("$KEY_PREFIX_CONDITION.x", "§c ?✖.*")
        private val totalProgressPattern =
            patternGroup.pattern("$KEY_PREFIX_CONDITION.total", "§7Total Progress: §3\\d{1,2}(?:\\.\\d)?%")
        private val categoryProgressPattern =
            patternGroup.pattern(
                "$KEY_PREFIX_CONDITION.category",
                "§7Progress to Complete Category: §6\\d{1,2}(?:\\.\\d)?%",
            )

        private val openWikiOnClick: (GuiContainerEvent.SlotClickEvent) -> Unit = { event ->
            val internalName = event.item?.getInternalName()
            if (internalName != null) {
                HypixelCommands.wiki(internalName.asString())
            }
        }

        private val openWikiTooltip: (ToolTipEvent) -> Unit = { event ->
            event.toolTip.add("")
            event.toolTip.add("§7§eClick to view on the SkyBlock Wiki!")
        }

        init {
            SkyblockGuideHighlightFeature(
                { SkyHanniMod.feature.inventory.highlightMissingSkyBlockLevelGuide },
                "level.guide",
                ".*Guide ➜.*",
                xPattern,
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.abiphoneGuide },
                "abiphone",
                "Miscellaneous ➜ Abiphone Contac",
                taskOnlyCompleteOncePattern,
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.oneTimeCompletion }, "bank", "Core ➜ Bank Upgrades", taskOnlyCompleteOncePattern,
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.storyGuide },
                "travel",
                "Core ➜ Fast Travels Unlocked",
                taskOnlyCompleteOncePattern,
                // The items do not have proper internal names and using the fact that all travel scrolls lead to the same wiki page
                { HypixelCommands.wiki("MUSEUM_TRAVEL_SCROLL") },
                openWikiTooltip,
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.oneTimeCompletion },
                "spooky",
                "Event ➜ Spooky Festival",
                taskOnlyCompleteOncePattern,
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.oneTimeCompletion },
                "belt",
                "Miscellaneous ➜ The Dojo",
                taskOnlyCompleteOncePattern,
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.jacobGuide },
                "jacob",
                "Event ➜ Jacob's Farming Contest",
                taskOnlyCompleteOncePattern,
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.oneTimeCompletion },
                "slaying",
                "Slaying ➜ .*",
                taskOnlyCompleteOncePattern,
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.storyGuide }, "story", "Story ➜ Complete Objectives", taskOnlyCompleteOncePattern,
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.oneTimeCompletion },
                "pet.rock",
                "Mining ➜ Rock Milestones",
                taskOnlyCompleteOncePattern,
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.oneTimeCompletion },
                "pet.dolphin",
                "Fishing ➜ Dolphin Milestones",
                taskOnlyCompleteOncePattern,
            )
            SkyblockGuideHighlightFeature({ skyblockGuideConfig.essenceGuide }, "essence", "Essence Shop ➜.*", xPattern)
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.minionGuide },
                "minion",
                "Crafted Minions",
                "§c ?✖.*|§7You haven't crafted this minion.",
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.storyGuide }, "harp", "Miscellaneous ➜ Harp Songs", xPattern,
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.consumableGuide },
                "consumable",
                "Miscellaneous ➜ Consumable Items",
                "§7§eThis task can be completed \\d+ times!",
                openWikiOnClick,
                openWikiTooltip,
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.oneTimeCompletion },
                "dungeon.floor",
                "Complete Dungeons ➜.*",
                "§7§eThis task can only be completed once!|§7§7You have not unlocked the content",
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.oneTimeCompletion }, "dungeon.layers", "Dungeon ➜ Complete Dungeons", xPattern,
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.menuGuide }, "tasks", "Tasks ➜ .*", totalProgressPattern,
            )
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.menuGuide }, "tasks.skill", "Skill Related Tasks", categoryProgressPattern,
            )
            @Suppress("MaxLineLength")
            SkyblockGuideHighlightFeature(
                { skyblockGuideConfig.collectionGuide },
                "collections",
                "\\w+ Collections|Collections",
                "§7Progress to .*|§7Find this item to add it to your|§7Kill this boss once to view collection|§7(?:Boss )?Collections (?:Unlocked|Maxed Out): §e.*",
            )
            SkyblockGuideHighlightFeature(
                { SkyHanniMod.feature.event.anniversaryCelebration400.highlightDailyTasks },
                "century",
                "Daily Tasks",
                "§c§lINCOMPLETE",
            )
        }
    }
}
