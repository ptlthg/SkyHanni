package at.hannibal2.skyhanni.utils

import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.NeuItems.getItemStackOrNull
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getPetExp
import net.minecraft.init.Items

class NeuInternalName private constructor(private val internalName: String) {

    companion object {

        private val internalNameMap = mutableMapOf<String, NeuInternalName>()

        val NONE = "NONE".toInternalName()
        val MISSING_ITEM = "MISSING_ITEM".toInternalName()

        val GEMSTONE_COLLECTION = "GEMSTONE_COLLECTION".toInternalName()
        val JASPER_CRYSTAL = "JASPER_CRYSTAL".toInternalName()
        val RUBY_CRYSTAL = "RUBY_CRYSTAL".toInternalName()
        val SKYBLOCK_COIN = "SKYBLOCK_COIN".toInternalName()
        val WISP_POTION = "WISP_POTION".toInternalName()
        val ENCHANTED_HAY_BLOCK = "ENCHANTED_HAY_BLOCK".toInternalName()
        val TIGHTLY_TIED_HAY_BALE = "TIGHTLY_TIED_HAY_BALE".toInternalName()

        fun String.toInternalName(): NeuInternalName = uppercase().replace(" ", "_").let {
            if (it.contains("§") || it.contains("&") || it.contains("'")) {
                ErrorManager.skyHanniError(
                    "Internal name found with color codes",
                    "Internal Name" to it, "Original String" to this,
                )
            }
            internalNameMap.getOrPut(it) { NeuInternalName(it) }
        }

        fun Set<String>.toInternalNames(): Set<NeuInternalName> = mapTo(mutableSetOf()) { it.toInternalName() }
        fun List<String>.toInternalNames(): List<NeuInternalName> = mapTo(mutableListOf()) { it.toInternalName() }

        private val itemNameCache = mutableMapOf<String, NeuInternalName?>()

        fun fromItemNameOrNull(itemName: String): NeuInternalName? = itemNameCache.getOrPut(itemName) {
            ItemNameResolver.getInternalNameOrNull(itemName.removeSuffix(" Pet")) ?: getCoins(itemName)
        }

        fun fromItemNameOrInternalName(itemName: String): NeuInternalName = fromItemNameOrNull(itemName) ?: itemName.toInternalName()

        private fun getCoins(itemName: String): NeuInternalName? = when {
            isCoins(itemName) -> SKYBLOCK_COIN
            else -> null
        }

        private val coinNames = setOf(
            "coin", "coins",
            "skyblock coin", "skyblock coins",
            "skyblock_coin", "skyblock_coins",
        )

        private fun isCoins(itemName: String): Boolean = itemName.lowercase() in coinNames

        fun fromItemName(itemName: String): NeuInternalName = fromItemNameOrNull(itemName) ?: run {
            val name = "itemName:$itemName"
            ItemUtils.addMissingRepoItem(name, "Could not find internal name for $name")
            MISSING_ITEM
        }
    }

    fun asString() = internalName

    override fun equals(other: Any?) = this === other

    override fun toString(): String = "internalName:$internalName"

    override fun hashCode(): Int = internalName.hashCode()

    fun contains(other: String) = internalName.contains(other)

    fun startsWith(other: String) = internalName.startsWith(other)

    fun endsWith(other: String) = internalName.endsWith(other)

    fun replace(oldValue: String, newValue: String): NeuInternalName =
        internalName.replace(oldValue, newValue, ignoreCase = true).toInternalName()

    fun isKnownItem(): Boolean = getItemStackOrNull() != null || this == SKYBLOCK_COIN

    /**
     * This is because skyblock has special ids in commands such as /viewrecipe for items like enchanted books and pets
     */
    val skyblockCommandId: String
        get() = when {
            isPet -> internalName.split(";").first()
            isEnchantedBook -> {
                val (name, level) = internalName.split(";", limit = 2)
                "ENCHANTED_BOOK_${name}_$level"
            }
            else -> internalName
        }

    private val isPet: Boolean
        get() = getItemStackOrNull()?.getPetExp() != null

    private val isEnchantedBook: Boolean
        get() = getItemStackOrNull()?.item == Items.enchanted_book
}
