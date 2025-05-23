package at.hannibal2.skyhanni.features.inventory.bazaar

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.features.inventory.bazaar.BazaarApi.getBazaarDataOrError
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.InventoryUtils.getUpperItems
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.formatDouble
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.inventory.ContainerChest
import net.minecraft.inventory.Slot

@SkyHanniModule
object BazaarOrderHelper {
    private val patternGroup = RepoPattern.group("bazaar.orderhelper")

    /**
     * REGEX-TEST: §a§lBUY §fWheat
     */
    private val bazaarItemNamePattern by patternGroup.pattern(
        "itemname",
        "§.§l(?<type>BUY|SELL) (?<name>.*)",
    )

    /**
     * REGEX-TEST: §7Filled: §a200§7/200 §a§l100%!
     */
    private val filledPattern by patternGroup.pattern(
        "filled",
        "§7Filled: §[a6].*§7/.* §a§l100%!",
    )

    /**
     * REGEX-TEST: §7Price per unit: §63.1 coins
     */
    private val pricePattern by patternGroup.pattern(
        "price",
        "§7Price per unit: §6(?<number>.*) coins",
    )

    @HandleEvent(onlyOnSkyblock = true)
    fun onBackgroundDrawn(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (!SkyHanniMod.feature.inventory.bazaar.orderHelper) return
        if (event.gui !is GuiChest) return

        val chest = event.container as ContainerChest
        val inventoryName = InventoryUtils.openInventoryName()
        if (!BazaarApi.isBazaarOrderInventory(inventoryName)) return

        for ((slot, stack) in chest.getUpperItems()) {
            bazaarItemNamePattern.matchMatcher(stack.displayName) {
                val buyOrSell = group("type").let { (it == "BUY") to (it == "SELL") }
                if (buyOrSell.let { !it.first && !it.second }) return

                highlightItem(group("name"), slot, buyOrSell)
            }
        }
    }

    private fun highlightItem(itemName: String, slot: Slot, buyOrSell: Pair<Boolean, Boolean>) {
        val data = NeuInternalName.fromItemName(itemName).getBazaarDataOrError()

        val itemLore = slot.stack.getLore()
        for (line in itemLore) {
            filledPattern.matchMatcher(line) {
                slot.highlight(LorenzColor.GREEN)
                return
            }

            pricePattern.matchMatcher(line) {
                val price = group("number").formatDouble()
                if (buyOrSell.first && price < data.instantBuyPrice || buyOrSell.second && price > data.sellOfferPrice) {
                    slot.highlight(LorenzColor.GOLD)
                    return
                }
            }
        }
    }
}
