package at.hannibal2.skyhanni.features.garden.visitor

import at.hannibal2.skyhanni.config.HasLegacyId
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NeuItems.getItemStack

enum class VisitorReward(
    rawInternalName: String,
    val displayName: String,
    private val legacyId: Int = -1,
) : HasLegacyId {
    FLOWERING_BOUQUET("FLOWERING_BOUQUET", "§9Flowering Bouquet", legacyId = 0),
    OVERGROWN_GRASS("OVERGROWN_GRASS", "§9Overgrown Grass", legacyId = 1),
    GREEN_BANDANA("GREEN_BANDANA", "§9Green Bandana", legacyId = 2),
    DEDICATION("DEDICATION;4", "§9Dedication IV", legacyId = 3),
    MUSIC_RUNE("MUSIC_RUNE;1", "§9Music Rune", legacyId = 4),
    SPACE_HELMET("DCTR_SPACE_HELM", "§cSpace Helmet", legacyId = 5),
    CULTIVATING("CULTIVATING;1", "§9Cultivating I", legacyId = 6),
    REPLENISH("REPLENISH;1", "§9Replenish I", legacyId = 7),
    DELICATE("DELICATE;5", "§9Delicate V"),
    COPPER_DYE("DYE_COPPER", "§8Copper Dye"),
    JUNGLE_KEY("JUNGLE_KEY", "§5Jungle Key"),
    ;

    private val internalName = rawInternalName.toInternalName()
    val itemStack by lazy { internalName.getItemStack() }
    // TODO use this instead of hard coded item names once moulconfig no longer calls toString before the neu repo gets loaded
//     val displayName by lazy { itemStack.nameWithEnchantment ?: internalName.asString() }

    companion object {

        fun getByInternalName(internalName: NeuInternalName) = entries.firstOrNull { it.internalName == internalName }
    }

    override fun getLegacyId(): Int {
        return legacyId
    }

    override fun toString(): String {
        return displayName
    }
}
