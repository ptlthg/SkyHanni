package at.hannibal2.skyhanni.features.inventory

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.data.jsonobjects.repo.neu.NeuCarnivalTokenCostJson
import at.hannibal2.skyhanni.data.jsonobjects.repo.neu.NeuMiscJson
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryOpenEvent
import at.hannibal2.skyhanni.events.NeuRepositoryReloadEvent
import at.hannibal2.skyhanni.events.render.gui.ReplaceItemEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.EssenceUtils
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.createItemStack
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatInt
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.groupOrNull
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.init.Items
import net.minecraft.item.ItemStack

@SkyHanniModule
object CarnivalShopHelper {

    // Where the informational item stack will be placed in the GUI
    private const val CUSTOM_STACK_LOCATION = 8
    private inline val NAME_TAG_ITEM get() = Items.name_tag

    /**
     * All upgrades will appear in slots 11 -> 15
     *
     * Filter out items outside of these bounds
     */
    private val SLOT_RANGE = 11..15

    private var repoEventShops = mutableListOf<EventShop>()
    private var currentProgress: EventShopProgress? = null
    private var currentEventType: String = ""
    private var tokensOwned: Int = 0
    private var tokensNeeded: Int = 0
    private var overviewInfoItemStack: ItemStack? = null
    private var shopSpecificInfoItemStack: ItemStack? = null

    private val patternGroup = RepoPattern.group("inventory.carnival-shop-helper")

    /**
     * REGEX-TEST: §7Your Tokens: §a1,234,567
     * REGEX-TEST: §7Your Tokens: §a0
     */
    private val currentTokenCountPattern by patternGroup.pattern(
        "carnival.tokens.current",
        ".*§7Your Tokens: §a(?<tokens>[\\d,]*)",
    )

    // TODO replace with InventoryDetector
    /**
     * REGEX-TEST: §8Souvenir Shop
     * REGEX-TEST: §8Carnival Perks
     */
    private val overviewInventoryNamesPattern by patternGroup.pattern(
        "carnival.overviewinventories",
        "(?:§.)*(?:Souvenir Shop|Carnival Perks)",
    )

    data class EventShop(val shopName: String, val upgrades: List<NeuCarnivalTokenCostJson>)
    data class EventShopUpgradeStatus(
        val upgradeName: String,
        val currentLevel: Int,
        val maxLevel: Int,
        val remainingCosts: MutableList<Int>,
    )

    data class EventShopProgress(val shopName: String, val purchasedUpgrades: Map<String, Int>) {
        private val eventShop = repoEventShops.find { it.shopName.equals(shopName, ignoreCase = true) }
        val remainingUpgrades: MutableList<EventShopUpgradeStatus> = eventShop?.upgrades?.map {
            val purchasedAmount = purchasedUpgrades[it.name] ?: 0
            EventShopUpgradeStatus(
                it.name,
                currentLevel = purchasedAmount,
                maxLevel = it.costs.count(),
                remainingCosts = it.costs.drop(purchasedAmount).toMutableList(),
            )
        }.orEmpty().toMutableList()
        val remainingUpgradeSum = remainingUpgrades.sumOf { it.remainingCosts.sum() }
        val nonRepoUpgrades = purchasedUpgrades.filter { purchasedUpgrade ->
            eventShop?.upgrades?.none { it.name.equals(purchasedUpgrade.key, ignoreCase = true) } == true
        }
    }

    @HandleEvent
    fun replaceItem(event: ReplaceItemEvent) {
        if (!isEnabled() || repoEventShops.isEmpty() || event.slot != CUSTOM_STACK_LOCATION) return
        tryReplaceShopSpecificStack(event)
        tryReplaceOverviewStack(event)
    }

    private fun isUnknownShop() = repoEventShops.none {
        it.shopName.equals(InventoryUtils.openInventoryName(), ignoreCase = true)
    }

    private fun tryReplaceShopSpecificStack(event: ReplaceItemEvent) {
        if (currentProgress == null || isUnknownShop()) return
        shopSpecificInfoItemStack?.let { event.replace(it) }
    }

    private fun tryReplaceOverviewStack(event: ReplaceItemEvent) {
        if (!overviewInventoryNamesPattern.matches(InventoryUtils.openInventoryName())) return
        overviewInfoItemStack?.let { event.replace(it) }
    }

    @HandleEvent
    fun onNeuRepoReload(event: NeuRepositoryReloadEvent) {
        val repoTokenShops = event.readConstant<NeuMiscJson>("carnivalshops").carnivalTokenShops
        repoEventShops = repoTokenShops.map { (key, value) ->
            EventShop(key.replace("_", " "), value.values.toMutableList())
        }.toMutableList()
        checkSavedProgress()
        regenerateOverviewItemStack()
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        currentProgress = null
        currentEventType = ""
        tokensOwned = 0
        tokensNeeded = 0
    }

    private fun checkSavedProgress() {
        val storage = ProfileStorageData.profileSpecific?.carnival?.carnivalShopProgress ?: return
        for (key in storage.keys) {
            if (!repoEventShops.any { shop -> shop.shopName.equals(key, ignoreCase = true) }) {
                storage.remove(key)
            }
        }
    }

    private fun saveProgress() {
        val storage = ProfileStorageData.profileSpecific?.carnival?.carnivalShopProgress ?: return
        val progress = currentProgress ?: return
        storage[progress.shopName] = progress.purchasedUpgrades
    }

    private fun MutableList<String>.addNeededRemainingTokens(cost: Int, extraFormatting: String? = null) {
        this.add("")
        this.add("§7Total Tokens Needed: §8${cost.addSeparators()}${extraFormatting.orEmpty()}")
        val tokensNeeded = cost - tokensOwned
        if (tokensOwned > 0) this.add("§7Tokens Owned: §8${tokensOwned.addSeparators()}")
        if (tokensNeeded > 0) this.add("§7Additional Tokens Needed: §8${tokensNeeded.addSeparators()}${extraFormatting.orEmpty()}")
        else this.addAll(listOf("", "§eYou have enough tokens"))
    }

    private fun regenerateOverviewItemStack() {
        if (repoEventShops.isEmpty()) return
        val storage = ProfileStorageData.profileSpecific?.carnival?.carnivalShopProgress ?: return
        val lore = buildList {
            add("§8(From SkyHanni)")
            add("")
            var sumTokensNeeded = 0
            var foundShops = 0
            for (repoShop in repoEventShops) {
                val purchasedUpgrades = storage[repoShop.shopName] ?: run {
                    add("§7${repoShop.shopName}: §copen shop to load...")
                    continue
                }
                foundShops++
                val shopProgress = EventShopProgress(repoShop.shopName, purchasedUpgrades)
                when (shopProgress.remainingUpgradeSum) {
                    0 -> add("§a${repoShop.shopName}§7: §aall upgrades purchased!")
                    else -> add("§7${repoShop.shopName}: §8${shopProgress.remainingUpgradeSum.addSeparators()} tokens needed")
                }
                sumTokensNeeded += shopProgress.remainingUpgradeSum
            }
            val extraFormatting = if (foundShops != repoEventShops.size) "*" else ""
            sumTokensNeeded.takeIf { it > 0 }?.let { addNeededRemainingTokens(it, extraFormatting) }
        }
        overviewInfoItemStack = createItemStack(
            NAME_TAG_ITEM,
            "§bRemaining Event Shop Token Upgrades",
            lore,
        )
    }

    private fun regenerateShopSpecificItemStack() {
        val progress = currentProgress ?: return
        val lore = buildList {
            add("§8(From SkyHanni)")
            add("")
            val remaining = progress.remainingUpgrades.filter { it.remainingCosts.isNotEmpty() }
            if (remaining.isEmpty()) {
                add("§a§lAll upgrades purchased!")
            } else {
                add("")
                remaining.forEach {
                    add(
                        "  §a${it.upgradeName} §b${it.currentLevel} §7-> §b${it.maxLevel}§7: §8${
                            it.remainingCosts.sum().addSeparators()
                        }",
                    )
                }
                val upgradeTotal = progress.remainingUpgradeSum
                tokensNeeded = upgradeTotal - tokensOwned
                upgradeTotal.takeIf { it > 0 }?.let { addNeededRemainingTokens(it) }
            }

            if (progress.nonRepoUpgrades.any()) {
                add("")
                add("§cFound upgrades not in repo§c§l:")
                progress.nonRepoUpgrades.forEach { add("  §4${it.key}") }
            }
        }
        shopSpecificInfoItemStack = createItemStack(
            NAME_TAG_ITEM,
            "§bRemaining $currentEventType Token Upgrades",
            lore,
        )
    }

    @HandleEvent
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (!isEnabled() || repoEventShops.isEmpty()) return
        var shouldUpdate = processTokenShopFooter(event)
        repoEventShops.find { it.shopName.equals(event.inventoryName, ignoreCase = true) }?.let { matchingShop ->
            currentEventType = matchingShop.shopName
            processEventShopUpgrades(event.inventoryItems)
            shouldUpdate = true
        }

        if (!shouldUpdate) return
        regenerateShopSpecificItemStack()
        regenerateOverviewItemStack()
        saveProgress()
    }

    private fun processTokenShopFooter(event: InventoryOpenEvent): Boolean {
        val tokenFooterStack = event.inventoryItems[32]
        if (tokenFooterStack === null || tokenFooterStack.displayName != "§eCarnival Tokens") return false
        currentTokenCountPattern.firstMatcher(tokenFooterStack.getLore()) {
            val new = groupOrNull("tokens")?.formatInt() ?: 0
            val changed = new != tokensOwned
            tokensOwned = new
            return changed
        }

        return false
    }

    private fun processEventShopUpgrades(inventoryItems: Map<Int, ItemStack>) {
        val purchasedUpgrades = EssenceUtils.extractPurchasedUpgrades(inventoryItems, SLOT_RANGE)
        currentProgress = EventShopProgress(currentEventType, purchasedUpgrades)
    }

    private fun isEnabled() = LorenzUtils.inSkyBlock && SkyHanniMod.feature.event.carnival.tokenShopHelper
}
