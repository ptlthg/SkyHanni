package at.hannibal2.skyhanni.features.garden.inventory

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.data.jsonobjects.repo.AnitaUpgradeCostsJson
import at.hannibal2.skyhanni.data.jsonobjects.repo.AnitaUpgradePrice
import at.hannibal2.skyhanni.events.RepositoryReloadEvent
import at.hannibal2.skyhanni.events.minecraft.ToolTipEvent
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemPriceUtils.getPrice
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatDouble
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.indexOfFirst
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object AnitaExtraFarmingFortune {

    private val config get() = GardenApi.config.anitaShop

    /**
     * REGEX-TEST: §5§o§aJacob's Ticket §8x450
     */
    private val realAmountPattern by RepoPattern.pattern(
        "garden.inventory.anita.extrafortune.realamount",
        "§5§o§aJacob's Ticket §8x(?<realAmount>.*)",
    )

    private var levelPrice = mapOf<Int, AnitaUpgradePrice>()

    @HandleEvent
    fun onToolTip(event: ToolTipEvent) {
        if (!config.extraFarmingFortune) return

        if (InventoryUtils.openInventoryName() != "Anita") return

        if (!event.itemStack.displayName.contains("Extra Farming Fortune")) return

        val anitaUpgrade = GardenApi.storage?.fortune?.anitaUpgrade ?: return

        var contributionFactor = 1.0
        val baseAmount = levelPrice[anitaUpgrade + 1]?.jacobTickets ?: return
        for (line in event.toolTip) {
            realAmountPattern.matchMatcher(line) {
                val realAmount = group("realAmount").formatDouble()
                contributionFactor = realAmount / baseAmount
            }
        }

        var goldMedals = 0
        var jacobTickets = 0
        for ((level, price) in levelPrice) {
            if (level > anitaUpgrade) {
                goldMedals += price.goldMedals
                jacobTickets += price.jacobTickets
            }
        }
        jacobTickets = (contributionFactor * jacobTickets).toInt()

        val index = event.toolTip.indexOfFirst("§5§o§eClick to trade!")?.let { it - 1 } ?: return

        // TODO: maybe only show the price when playing classic
//        if (!LorenzUtils.noTradeMode) {
        val price = jacobTickets * "JACOBS_TICKET".toInternalName().getPrice()
        event.toolTip.add(index, "  §7Price: §6${price.shortFormat()} coins")
//        }
        event.toolTip.add(index, "§aJacob Tickets §8x${jacobTickets.addSeparators()}")
        event.toolTip.add(index, "§6Gold medals: §8x$goldMedals")
        event.toolTip.add(index, "§7Cost to max out")
        event.toolTip.add(index, "")

        val upgradeIndex = event.toolTip.indexOfFirst { it.contains("You have") }
        if (upgradeIndex != -1) {
            event.toolTip.add(upgradeIndex + 1, "§7Current Tier: §e$anitaUpgrade/${levelPrice.size}")
        }
    }

    @HandleEvent
    fun onRepoReload(event: RepositoryReloadEvent) {
        val data = event.getConstant<AnitaUpgradeCostsJson>("AnitaUpgradeCosts")
        levelPrice = data.levelPrice
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(3, "garden.extraFarmingFortune", "garden.anitaShop.extraFarmingFortune")
    }
}
