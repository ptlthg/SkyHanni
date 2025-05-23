package at.hannibal2.skyhanni.events

import at.hannibal2.skyhanni.api.event.SkyHanniEvent
import at.hannibal2.skyhanni.data.OtherInventoryData
import at.hannibal2.skyhanni.utils.PrimitiveItemStack
import at.hannibal2.skyhanni.utils.PrimitiveItemStack.Companion.toPrimitiveStackOrNull
import net.minecraft.item.ItemStack

open class InventoryOpenEvent(private val inventory: OtherInventoryData.Inventory) : SkyHanniEvent() {

    val inventoryId: Int get() = inventory.windowId
    val inventoryName: String get() = inventory.title
    val inventorySize: Int get() = inventory.slotCount
    val inventoryItems: Map<Int, ItemStack> get() = inventory.items
    val inventoryItemsWithNull: Map<Int, ItemStack?> by lazy {
        (0 until inventorySize).associateWith { inventoryItems[it] }
    }
    val inventoryItemsPrimitive: Map<Int, PrimitiveItemStack> by lazy {
        val map = mutableMapOf<Int, PrimitiveItemStack>()
        for ((slot, item) in inventoryItems) {
            item.toPrimitiveStackOrNull()?.let {
                map[slot] = it
            }
        }
        map
    }
    val fullyOpenedOnce: Boolean get() = inventory.fullyOpenedOnce
}

/**
 * This event is getting fired after every slot in the newly opened inventory has item data.
 *
 * New inventory data gets first sent as an empty inventory from the server.
 * Item stack slot information is sent afterwards, sometimes with a short delay.
 *
 * This approach is faster than to wait a fix duration after the inventory open packet is detected.
 *
 * Since this logic only works via packets, and the player inventory (pressing E) is client side,
 * this event does not get fired when opening the inventory via pressing E.
 *
 * TODO does not work for inventories with empty slots. e.g. dungeon when death ghost tp menu "Teleport to Player".
 */
class InventoryFullyOpenedEvent(inventory: OtherInventoryData.Inventory) : InventoryOpenEvent(inventory)

class InventoryUpdatedEvent(inventory: OtherInventoryData.Inventory) : InventoryOpenEvent(inventory)
