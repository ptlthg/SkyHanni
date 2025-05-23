package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.OwnInventoryItemUpdateEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.entity.ItemAddInInventoryEvent
import at.hannibal2.skyhanni.events.minecraft.packet.PacketReceivedEvent
import at.hannibal2.skyhanni.events.minecraft.packet.PacketSentEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalNameOrNull
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.addOrPut
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import at.hannibal2.skyhanni.utils.compat.getItemOnCursor
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.network.play.client.C0EPacketClickWindow
import net.minecraft.network.play.server.S0DPacketCollectItem
import net.minecraft.network.play.server.S2FPacketSetSlot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object OwnInventoryData {

    private var itemAmounts = mapOf<NeuInternalName, Int>()
    private var dirty = false

    /**
     * REGEX-TEST: §aMoved §r§e10 Wheat§r§a from your Sacks to your inventory.
     */
    private val sackToInventoryChatPattern by RepoPattern.pattern(
        "data.owninventory.chat.movedsacktoinventory",
        "§aMoved §r§e\\d* (?<name>.*)§r§a from your Sacks to your inventory.",
    )

    @HandleEvent(priority = HandleEvent.LOW, receiveCancelled = true, onlyOnSkyblock = true)
    fun onItemPickupReceivePacket(event: PacketReceivedEvent) {
        val packet = event.packet
        if (packet is S2FPacketSetSlot || packet is S0DPacketCollectItem) {
            dirty = true
        }
        if (packet is S2FPacketSetSlot) {
            val windowId = packet.func_149175_c()
            if (windowId == 0) {
                val slot = packet.func_149173_d()
                val item = packet.func_149174_e() ?: return
                DelayedRun.runNextTick {
                    OwnInventoryItemUpdateEvent(item, slot).post()
                }
            }
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onClickEntity(event: PacketSentEvent) {
        val packet = event.packet

        if (packet is C0EPacketClickWindow) {
            dirty = true
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onTick() {
        if (itemAmounts.isEmpty()) {
            itemAmounts = getCurrentItems()
        }

        if (!dirty) return
        dirty = false

        val map = getCurrentItems()
        for ((internalName, amount) in map) {
            calculateDifference(internalName, amount)
        }
        itemAmounts = map
    }

    private fun getCurrentItems(): MutableMap<NeuInternalName, Int> {
        val map = mutableMapOf<NeuInternalName, Int>()
        for (itemStack in InventoryUtils.getItemsInOwnInventory()) {
            val internalName = itemStack.getInternalNameOrNull() ?: continue
            map.addOrPut(internalName, itemStack.stackSize)
        }
        return map
    }

    @HandleEvent
    fun onWorldChange() {
        itemAmounts = emptyMap()
    }

    private fun calculateDifference(internalName: NeuInternalName, newAmount: Int) {
        val oldAmount = itemAmounts[internalName] ?: 0

        val diff = newAmount - oldAmount
        if (diff > 0) {
            addItem(internalName, diff)
        }
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        val item = MinecraftCompat.localPlayer.getItemOnCursor() ?: return
        val internalNameOrNull = item.getInternalNameOrNull() ?: return
        ignoreItem(500.milliseconds, internalNameOrNull)
    }

    @HandleEvent
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        ignoreItem(500.milliseconds) { true }

        val itemName = event.item?.displayName ?: return
        checkAHMovements(itemName)
    }

    private fun checkAHMovements(itemName: String) {
        val inventoryName = InventoryUtils.openInventoryName()

        // cancel own auction
        if (inventoryName.let { it == "BIN Auction View" || it == "Auction View" }) {
            if (itemName == "§cCancel Auction") {
                val item = InventoryUtils.getItemAtSlotIndex(13)
                val internalName = item?.getInternalNameOrNull() ?: return
                ignoreItem(5.seconds, internalName)
            }
        }

        // bought item from bin ah
        if (inventoryName == "Confirm Purchase" && itemName == "§aConfirm") {
            val item = InventoryUtils.getItemAtSlotIndex(13)
            val internalName = item?.getInternalNameOrNull() ?: return
            ignoreItem(5.seconds, internalName)
        }

        // bought item from normal ah
        if (inventoryName == "Auction View" && itemName == "§6Collect Auction") {
            val item = InventoryUtils.getItemAtSlotIndex(13)
            val internalName = item?.getInternalNameOrNull() ?: return
            ignoreItem(5.seconds, internalName)
        }

        // collected all items in "own bins"
        if (inventoryName == "Your Bids" && itemName == "§aClaim All") {
            for (stack in InventoryUtils.getItemsInOpenChest().map { it.stack }) {
                if (stack.getLore().any { it == "§7Status: §aSold!" || it == "7Status: §aEnded!" }) {
                    val internalName = stack.getInternalNameOrNull() ?: return
                    ignoreItem(5.seconds, internalName)
                }
            }
        }

        // items in anvil
        if (inventoryName == "Anvil") {
            for (stack in InventoryUtils.getItemsAtSlots(13, 29, 33)) {
                val internalName = stack.getInternalNameOrNull() ?: continue
                ignoreItem(5.seconds, internalName)
            }
        }
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        sackToInventoryChatPattern.matchMatcher(event.message) {
            val name = group("name")
            ignoreItem(500.milliseconds) { it.repoItemName.contains(name) }
        }
    }

    fun ignoreItem(duration: Duration, internalName: NeuInternalName) {
        ignoreItem(duration) { it == internalName }
    }

    fun ignoreItem(duration: Duration, condition: (NeuInternalName) -> Boolean) {
        ignoredItemsUntil.add(IgnoredItem(condition, SimpleTimeMark.now() + duration))
    }

    private val ignoredItemsUntil = mutableListOf<IgnoredItem>()

    class IgnoredItem(val condition: (NeuInternalName) -> Boolean, val blockedUntil: SimpleTimeMark)

    private fun addItem(internalName: NeuInternalName, add: Int) {
        if (LorenzUtils.lastWorldSwitch.passedSince() < 3.seconds) return

        ignoredItemsUntil.removeIf { it.blockedUntil.isInPast() }
        if (ignoredItemsUntil.any { it.condition(internalName) }) {
            return
        }

        if (internalName.startsWith("MAP-")) return

        ItemAddInInventoryEvent(internalName, add).post()
    }
}
