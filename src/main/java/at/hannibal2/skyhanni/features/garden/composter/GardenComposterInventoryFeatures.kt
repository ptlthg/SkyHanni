package at.hannibal2.skyhanni.features.garden.composter

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.minecraft.ToolTipEvent
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.InventoryUtils.getUpperItems
import at.hannibal2.skyhanni.utils.ItemPriceUtils.getPrice
import at.hannibal2.skyhanni.utils.ItemUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.inventory.ContainerChest

@SkyHanniModule
object GardenComposterInventoryFeatures {

    private val config get() = GardenApi.config.composters

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onToolTip(event: ToolTipEvent) {
        if (!config.upgradePrice) return

        if (InventoryUtils.openInventoryName() != "Composter Upgrades") return

        var next = false
        val list = event.toolTip
        var i = -1
        var indexFullCost = 0
        var fullPrice = 0.0
        var amountItems = 0
        for (line in event.toolTipRemovedPrefix()) {
            i++
            if (line == "§7Upgrade Cost:") {
                next = true
                indexFullCost = i
                continue
            }

            if (next) {
                if (line.endsWith(" Copper")) continue
                if (line == "") break
                val (itemName, amount) = ItemUtils.readItemAmount(line) ?: run {
                    ErrorManager.logErrorStateWithData(
                        "Error reading item line",
                        "could not read item line",
                        "line" to line,
                    )
                    continue
                }
                val internalName = NeuInternalName.fromItemName(itemName)
                val lowestBin = internalName.getPrice()
                val price = lowestBin * amount
                fullPrice += price
                val format = price.shortFormat()
                list[i] = list[i] + " §7(§6$format§7)"
                amountItems++
            }
        }

        if (amountItems > 1) {
            val format = fullPrice.shortFormat()
            list[indexFullCost] = list[indexFullCost] + " §7(§6$format§7)"
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onBackgroundDrawn(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (!config.highlightUpgrade) return

        if (InventoryUtils.openInventoryName() == "Composter Upgrades") {
            if (event.gui !is GuiChest) return
            val chest = event.container as ContainerChest

            for ((slot, stack) in chest.getUpperItems()) {
                if (stack.getLore().any { it == "§eClick to upgrade!" }) {
                    slot.highlight(LorenzColor.GOLD)
                }
            }
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(3, "garden.composterUpgradePrice", "garden.composters.upgradePrice")
        event.move(3, "garden.composterHighLightUpgrade", "garden.composters.highlightUpgrade")
    }
}
