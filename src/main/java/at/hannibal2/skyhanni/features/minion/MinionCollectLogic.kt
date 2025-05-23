package at.hannibal2.skyhanni.features.minion

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.MinionOpenEvent
import at.hannibal2.skyhanni.events.entity.ItemAddInInventoryEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuItems

@SkyHanniModule
object MinionCollectLogic {

    private var oldMap = mapOf<NeuInternalName, Int>()

    @HandleEvent
    fun onMinionOpen(event: MinionOpenEvent) {
        if (oldMap.isNotEmpty()) return
        oldMap = count()
    }

    private fun count(): MutableMap<NeuInternalName, Int> {
        val map = mutableMapOf<NeuInternalName, Int>()
        for (stack in InventoryUtils.getItemsInOwnInventory()) {
            val internalName = stack.getInternalName()
            val (newId, amount) = NeuItems.getPrimitiveMultiplier(internalName)
            val old = map[newId] ?: 0
            map[newId] = old + amount * stack.stackSize
        }
        return map
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        closeMinion()
    }

    private fun closeMinion() {
        if (oldMap.isEmpty()) return

        for ((internalId, amount) in count()) {
            val old = oldMap[internalId] ?: 0
            val diff = amount - old

            if (diff > 0) {
                ItemAddInInventoryEvent(internalId, diff).post()
            }
        }

        oldMap = emptyMap()
    }
}
