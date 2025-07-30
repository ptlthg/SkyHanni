package at.hannibal2.skyhanni.data.hotx

import at.hannibal2.skyhanni.data.jsonobjects.local.HotxTree
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack
import java.util.regex.Pattern

// Abstraction for Heart of the Mountain/Forest
interface HotxData<Reward> {
    val maxLevel: Int
    val costFun: (Int) -> (Double?)
    val rewardFun: (Int) -> (Map<Reward, Double>)

    /**
     * Identifier
     */
    val name: String

    /**
     * Name of the Item in the gui for reading
     */
    val guiName: String

    /**
     * Userfacing name of the Perk
     *
     * `name.allLettersFirstUppercase()`
     */
    val printName: String

    /**
     * Level with buffs applied
     */
    val effectiveLevel: Int
    fun getStorage(): HotxTree?

    val guiNamePattern: Pattern

    var slot: Slot?
    var item: ItemStack?

    /**
     * Level for which the effect that is present (considers [enabled] and [effectiveLevel])
     */
    val activeLevel: Int get() = if (enabled) effectiveLevel else 0

    /**
     * Level which are actually paid for (without any buffs)
     */
    var rawLevel: Int
        get() = getPerk()?.level ?: Int.MIN_VALUE
        set(value) {
            getPerk()?.level = value
        }

    var enabled: Boolean
        get() = getPerk()?.enabled ?: false
        set(value) {
            getPerk()?.enabled = value
        }

    var isUnlocked: Boolean
        get() = getPerk()?.isUnlocked ?: false
        set(value) {
            getPerk()?.isUnlocked = value
        }

    private fun getPerk() = getStorage()?.perks?.getOrPut(this.name) { HotxTree.HotxPerk() }

    val isMaxLevel: Boolean get() = effectiveLevel >= maxLevel

    fun getLevelUpCost() = costFun(rawLevel)

    fun getReward() = if (enabled) rewardFun(activeLevel) else emptyMap()

    fun calculateTotalCost(desiredLevel: Int) = (2..desiredLevel).sumOf { level -> costFun(level)?.toInt() ?: 0 }

    /**
     * `calculateTotalCost(maxLevel)`
     */
    val totalCostMaxLevel: Int
}
