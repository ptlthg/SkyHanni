package at.hannibal2.skyhanni.features.rift.everywhere

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandGraphs
import at.hannibal2.skyhanni.data.jsonobjects.repo.EnigmaSoulsJson
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.RepositoryReloadEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.events.render.gui.ReplaceItemEvent
import at.hannibal2.skyhanni.features.rift.RiftApi
import at.hannibal2.skyhanni.features.rift.area.dreadfarm.WoodenButtonsHelper
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.InventoryUtils.getAllItems
import at.hannibal2.skyhanni.utils.ItemUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NeuItems.getItemStack
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.RenderUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import at.hannibal2.skyhanni.utils.SpecialColor.toSpecialColor
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.player.inventory.ContainerLocalMenu
import net.minecraft.inventory.ContainerChest

@SkyHanniModule
object EnigmaSoulWaypoints {

    private val config get() = RiftApi.config.enigmaSoulWaypoints
    private var inInventory = false
    var soulLocations = mapOf<String, LorenzVec>()
    private val trackedSouls = mutableListOf<String>()
    private val inventoryUnfound = mutableListOf<String>()
    private var adding = true

    private val item by lazy {
        val neuItem = "SKYBLOCK_ENIGMA_SOUL".toInternalName().getItemStack()
        ItemUtils.createItemStack(
            neuItem.item,
            "§5Toggle Missing",
            "§7Click here to toggle",
            "§7the waypoints for each",
            "§7missing souls on this page",
        )
    }

    @HandleEvent
    fun replaceItem(event: ReplaceItemEvent) {
        if (!isEnabled()) return

        if (inventoryUnfound.isEmpty()) return
        if (event.inventory is ContainerLocalMenu && inInventory && event.slot == 31) {
            event.replace(item)
        }
    }

    @HandleEvent
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        inInventory = false
        if (!event.inventoryName.contains("Enigma Souls")) return
        inInventory = true

        for (stack in event.inventoryItems.values) {
            val split = stack.displayName.split("Enigma: ")
            if (split.size == 2 && stack.getLore().last() == "§8✖ Not completed yet!") {
                inventoryUnfound.add(split.last())
            }
        }
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        inInventory = false
        inventoryUnfound.clear()
        adding = true
    }

    @HandleEvent(priority = HandleEvent.HIGH)
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        if (!inInventory || !isEnabled()) return

        if (event.slotId == 31 && inventoryUnfound.isNotEmpty()) {
            event.makePickblock()
            if (inventoryUnfound.contains("Buttons")) {
                RiftApi.trackingButtons = !RiftApi.trackingButtons
            }
            if (adding) {
                trackedSouls.addAll(inventoryUnfound)
                adding = false
            } else {
                trackedSouls.removeAll(inventoryUnfound)
                adding = true
            }
        }

        if (event.slot?.stack == null) return

        val split = event.slot.stack.displayName.split("Enigma: ")
        if (split.size != 2) return

        event.makePickblock()
        val name = split.last()
        if (!soulLocations.contains(name)) return

        if (name == "Buttons") {
            RiftApi.trackingButtons = !RiftApi.trackingButtons
        }

        if (!trackedSouls.contains(name)) {
            ChatUtils.chat("§5Tracking the $name Enigma Soul!", prefixColor = "§5")
            if (config.showPathFinder) {
                soulLocations[name]?.let {
                    if (!(name == "Buttons" && WoodenButtonsHelper.showButtons())) {
                        IslandGraphs.pathFind(
                            it,
                            "$name Enigma Soul",
                            config.color.toSpecialColor(),
                            condition = { config.showPathFinder }
                        )
                    }
                }
            }
            trackedSouls.add(name)
        } else {
            trackedSouls.remove(name)
            ChatUtils.chat("§5No longer tracking the $name Enigma Soul!", prefixColor = "§5")
            IslandGraphs.stop()
        }
    }

    @HandleEvent(priority = HandleEvent.LOWEST)
    fun onBackgroundDrawn(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (!isEnabled() || !inInventory) return

        if (event.gui !is GuiChest) return
        val chest = event.container as ContainerChest

        for ((slot, stack) in chest.getAllItems()) {
            for (soul in trackedSouls) {
                if (stack.displayName.removeColor().contains(soul)) {
                    slot.highlight(LorenzColor.DARK_PURPLE)
                }
            }
        }
        if (!adding) {
            chest.inventorySlots[31].highlight(LorenzColor.DARK_PURPLE)
        }
    }

    @HandleEvent
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!isEnabled()) return
        for (soul in trackedSouls) {
            soulLocations[soul]?.let {
                event.drawWaypointFilled(it, config.color.toSpecialColor(), seeThroughBlocks = true, beacon = true)
                event.drawDynamicText(it.up(), "§5${soul.removeSuffix(" Soul")} Soul", 1.5)
            }
        }
    }

    @HandleEvent
    fun onRepoReload(event: RepositoryReloadEvent) {
        val data = event.getConstant<EnigmaSoulsJson>("EnigmaSouls")
        val areas = data.areas
        soulLocations = buildMap {
            for ((_, locations) in areas) {
                for (location in locations) {
                    this[location.name] = location.position
                }
            }
        }
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!isEnabled()) return
        val message = event.message.removeColor().trim()
        if (message == "You have already found that Enigma Soul!" || message == "SOUL! You unlocked an Enigma Soul!") {
            hideClosestSoul()
        }
    }

    private fun hideClosestSoul() {
        var closestSoul = ""
        var closestDistance = 8.0

        for ((soul, location) in soulLocations) {
            if (location.distanceToPlayer() < closestDistance) {
                closestSoul = soul
                closestDistance = location.distanceToPlayer()
            }
        }
        if (closestSoul in trackedSouls) {
            trackedSouls.remove(closestSoul)
            ChatUtils.chat("§5Found the $closestSoul Enigma Soul!", prefixColor = "§5")
            if (closestSoul == "Buttons") {
                RiftApi.trackingButtons = false
            }
        }
    }

    fun isEnabled() = RiftApi.inRift() && config.enabled
}
