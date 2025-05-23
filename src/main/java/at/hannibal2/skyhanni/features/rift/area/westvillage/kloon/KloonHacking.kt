package at.hannibal2.skyhanni.features.rift.area.westvillage.kloon

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.events.minecraft.ToolTipEvent
import at.hannibal2.skyhanni.features.rift.RiftApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RenderUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object KloonHacking {

    private val config get() = RiftApi.config.area.westVillage.hacking

    /**
     * REGEX-TEST: You've set the color of this terminal to RED!
     */
    private val colorPattern by RepoPattern.pattern(
        "rift.area.westvillage.kloon.color",
        "You've set the color of this terminal to (?<color>.*)!",
    )

    private var wearingHelmet = false
    private var inTerminalInventory = false
    private var inColorInventory = false
    private val correctButtons = mutableListOf<String>()
    private var nearestTerminal: KloonTerminal? = null

    private val RETRO_ENCABULATING_VISOR = "RETRO_ENCABULATING_VISOR".toInternalName()

    @HandleEvent(onlyOnIsland = IslandType.THE_RIFT)
    fun onSecondPassed(event: SecondPassedEvent) {
        checkHelmet()
    }

    private fun checkHelmet() {
        wearingHelmet = InventoryUtils.getHelmet()?.getInternalName() == RETRO_ENCABULATING_VISOR
    }

    @HandleEvent(onlyOnIsland = IslandType.THE_RIFT)
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        inTerminalInventory = false
        inColorInventory = false
        nearestTerminal = null
        if (!config.solver) return
        if (event.inventoryName == "Hacking" || event.inventoryName == "Hacking (As seen on CSI)") {
            inTerminalInventory = true
            correctButtons.clear()
            for ((slot, stack) in event.inventoryItems) {
                if (slot in 2..6) {
                    correctButtons.add(stack.displayName.removeColor())
                }
            }
        }
        if (event.inventoryName == "Hacked Terminal Color Picker") {
            inColorInventory = true
        }
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        inTerminalInventory = false
        inColorInventory = false
    }

    @HandleEvent(onlyOnIsland = IslandType.THE_RIFT)
    fun onBackgroundDrawn(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (inTerminalInventory) {
            if (!config.solver) return
            var i = 0
            for (slot in InventoryUtils.getItemsInOpenChest()) {
                if (slot.slotIndex == 11 + 10 * i) {
                    val correctButton = slot.stack.displayName.removeColor() == correctButtons[i]
                    slot.highlight(if (correctButton) LorenzColor.GREEN else LorenzColor.RED)
                    continue
                }
                if (slot.slotIndex > i * 9 + 8 && slot.slotIndex < i * 9 + 18 &&
                    slot.stack.displayName.removeColor() == correctButtons[i]
                ) {
                    slot.highlight(LorenzColor.YELLOW)
                }
                if (slot.slotIndex == i * 9 + 17) {
                    i += 1
                }
            }
        }
        if (inColorInventory) {
            if (!config.color) return
            val targetColor = nearestTerminal ?: getNearestTerminal()
            for (slot in InventoryUtils.getItemsInOpenChest()) {
                if (slot.stack.getLore().any { it.contains(targetColor?.name.orEmpty()) }) {
                    slot.highlight(LorenzColor.GREEN)
                }
            }
        }
    }

    @HandleEvent(priority = HandleEvent.HIGH, onlyOnIsland = IslandType.THE_RIFT)
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        if (!inTerminalInventory) return
        event.makePickblock()
    }

    @HandleEvent(onlyOnIsland = IslandType.THE_RIFT)
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!config.waypoints) return
        if (!wearingHelmet) return
        val storage = ProfileStorageData.profileSpecific?.rift ?: return
        for (terminal in KloonTerminal.entries) {
            if (terminal !in storage.completedKloonTerminals) {
                event.drawWaypointFilled(terminal.location, LorenzColor.DARK_RED.toColor(), true, true)
            }
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.THE_RIFT)
    fun onChat(event: SkyHanniChatEvent) {
        if (!wearingHelmet) return
        colorPattern.matchMatcher(event.message.removeColor()) {
            val storage = ProfileStorageData.profileSpecific?.rift ?: return
            val color = group("color")
            val completedTerminal = KloonTerminal.entries.firstOrNull { it.name == color } ?: return
            if (completedTerminal != nearestTerminal) return
            storage.completedKloonTerminals.add(completedTerminal)
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.THE_RIFT)
    fun onToolTip(event: ToolTipEvent) {
        if (!inTerminalInventory) return
        if (!config.solver) return

        val neededTooltips = listOf(0, 2, 3, 4, 5, 6, 8, 9, 26, 27, 44, 45)
        if (event.slot.slotIndex !in neededTooltips) {
            event.toolTip.clear()
        }
    }

    private fun getNearestTerminal(): KloonTerminal? {
        var closestTerminal: KloonTerminal? = null
        var closestDistance = 8.0

        for (terminal in KloonTerminal.entries) {
            val distance = terminal.location.distanceToPlayer()
            if (distance < closestDistance) {
                closestTerminal = terminal
                closestDistance = distance
            }
        }
        nearestTerminal = closestTerminal
        return closestTerminal
    }

    @HandleEvent
    @Suppress("AvoidBritishSpelling")
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(9, "rift.area.westVillageConfig", "rift.area.westVillage")

        val basePath = "rift.area.westVillage"
        event.move(82, "$basePath.hacking.colour", "$basePath.hacking.color")
    }
}
