package at.hannibal2.skyhanni.utils

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.jsonobjects.repo.neu.NeuEssenceCostJson
import at.hannibal2.skyhanni.events.NeuRepositoryReloadEvent
import at.hannibal2.skyhanni.features.inventory.EssenceShopHelper.essenceUpgradePattern
import at.hannibal2.skyhanni.features.inventory.EssenceShopHelper.maxedUpgradeLorePattern
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.romanToDecimal
import at.hannibal2.skyhanni.utils.RegexUtils.groupOrNull
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.addOrPut
import net.minecraft.item.ItemStack

@SkyHanniModule
object EssenceUtils {
    var itemPrices = mapOf<NeuInternalName, Map<Int, EssenceUpgradePrice>>()

    @HandleEvent
    fun onNeuRepoReload(event: NeuRepositoryReloadEvent) {
        val unformattedData = event.getConstant<Map<String, NeuEssenceCostJson>>("essencecosts", NeuEssenceCostJson.TYPE)
        this.itemPrices = reformatData(unformattedData)
    }

    fun NeuInternalName.getEssencePrices(): Map<Int, EssenceUpgradePrice>? = itemPrices[this]

    private fun reformatData(unformattedData: Map<String, NeuEssenceCostJson>): MutableMap<NeuInternalName, Map<Int, EssenceUpgradePrice>> {
        val itemPrices = mutableMapOf<NeuInternalName, Map<Int, EssenceUpgradePrice>>()
        for ((name, data) in unformattedData) {

            val essencePrices = loadEssencePrices(data)
            val extraItems = data.extraItems.orEmpty()
            val (coinPrices, iemPrices) = loadCoinAndItemPrices(extraItems)

            val upgradePrices = mutableMapOf<Int, EssenceUpgradePrice>()
            for ((tier, essencePrice) in essencePrices) {
                val coinPrice = coinPrices[tier]
                val itemPrice = iemPrices[tier].orEmpty()
                upgradePrices[tier] = EssenceUpgradePrice(essencePrice, coinPrice, itemPrice)
            }

            val internalName = name.toInternalName()
            itemPrices[internalName] = upgradePrices
        }
        return itemPrices
    }

    private fun loadCoinAndItemPrices(
        extraItems: Map<String, List<String>>,
    ): Pair<MutableMap<Int, Long>, MutableMap<Int, Map<NeuInternalName, Int>>> {

        val collectCoinPrices = mutableMapOf<Int, Long>()
        val collectItemPrices = mutableMapOf<Int, Map<NeuInternalName, Int>>()

        for ((tier, rawItems) in extraItems.mapKeys { it.key.toInt() }) {
            val itemPrices = mutableMapOf<NeuInternalName, Int>()

            for ((itemName, amount) in rawItems.map { split(it) }) {
                if (itemName == NeuInternalName.SKYBLOCK_COIN) {
                    collectCoinPrices[tier] = amount
                } else {
                    itemPrices[itemName] = amount.toInt()
                }
            }

            collectItemPrices[tier] = itemPrices
        }
        return Pair(collectCoinPrices, collectItemPrices)
    }

    private fun split(string: String): Pair<NeuInternalName, Long> = string.split(":").let {
        it[0].toInternalName() to it[1].toLong()
    }

    private fun loadEssencePrices(data: NeuEssenceCostJson): MutableMap<Int, EssencePrice> {
        val map = mutableMapOf<Int, EssencePrice>()
        val essenceType = data.essenceType
        data.essenceFor1?.let { map[1] = EssencePrice(it, essenceType) }
        data.essenceFor2?.let { map[2] = EssencePrice(it, essenceType) }
        data.essenceFor3?.let { map[3] = EssencePrice(it, essenceType) }
        data.essenceFor4?.let { map[4] = EssencePrice(it, essenceType) }
        data.essenceFor5?.let { map[5] = EssencePrice(it, essenceType) }
        data.essenceFor6?.let { map[6] = EssencePrice(it, essenceType) }
        data.essenceFor7?.let { map[7] = EssencePrice(it, essenceType) }
        data.essenceFor8?.let { map[8] = EssencePrice(it, essenceType) }
        data.essenceFor9?.let { map[9] = EssencePrice(it, essenceType) }
        data.essenceFor10?.let { map[10] = EssencePrice(it, essenceType) }
        data.essenceFor11?.let { map[11] = EssencePrice(it, essenceType) }
        data.essenceFor12?.let { map[12] = EssencePrice(it, essenceType) }
        data.essenceFor13?.let { map[13] = EssencePrice(it, essenceType) }
        data.essenceFor14?.let { map[14] = EssencePrice(it, essenceType) }
        data.essenceFor15?.let { map[15] = EssencePrice(it, essenceType) }
        return map
    }

    data class EssenceUpgradePrice(
        val essencePrice: EssencePrice,
        val coinPrice: Long?,
        val itemPrice: Map<NeuInternalName, Int>,
    ) {

        operator fun plus(other: EssenceUpgradePrice): EssenceUpgradePrice {
            if (other.essencePrice.essenceType != essencePrice.essenceType) ErrorManager.skyHanniError(
                "Trying to add non compatible EssenceUpgradePrices!",
                "essencePrice.essenceType" to essencePrice.essenceType,
                "other.essencePrice.essenceType" to other.essencePrice.essenceType,
            )

            val coinPrice = coinPrice ?: 0L
            val otherCoinPrice = other.coinPrice ?: 0L

            val map = itemPrice.toMutableMap()
            for (entry in other.itemPrice) {
                map.addOrPut(entry.key, entry.value)
            }

            return EssenceUpgradePrice(essencePrice + other.essencePrice, coinPrice + otherCoinPrice, map)
        }
    }

    data class EssencePrice(val essenceAmount: Int, val essenceType: String) {

        operator fun plus(other: EssencePrice): EssencePrice {
            if (other.essenceType != essenceType) ErrorManager.skyHanniError(
                "Trying to add non compatible essence prices!",
                "essenceType" to essenceType,
                "other.essenceType" to other.essenceType,
            )

            return EssencePrice(essenceAmount + other.essenceAmount, essenceType)
        }
    }

    fun extractPurchasedUpgrades(
        inventoryItems: Map<Int, ItemStack>,
        keyRange: IntRange,
    ) = extractPurchasedUpgrades(
        inventoryItems.filter { it.key in keyRange && it.value.item != null },
    )

    private fun extractPurchasedUpgrades(inventoryItems: Map<Int, ItemStack>) = buildMap {
        for (value in inventoryItems.values) {
            // Right now Carnival and Essence Upgrade patterns are 'in-sync'
            // This may change in the future, and this would then need its own pattern
            essenceUpgradePattern.matchMatcher(value.displayName) {
                val upgradeName = groupOrNull("upgrade") ?: continue
                val nextUpgradeRoman = groupOrNull("tier") ?: continue
                val nextUpgrade = nextUpgradeRoman.romanToDecimal()
                val isMaxed = value.getLore().any { loreLine -> maxedUpgradeLorePattern.matches(loreLine) }
                put(upgradeName, if (isMaxed) nextUpgrade else nextUpgrade - 1)
            }
        }
    }
}
