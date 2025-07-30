package at.hannibal2.skyhanni.features.garden.fortuneguide.pages

import at.hannibal2.skyhanni.features.garden.CropType
import at.hannibal2.skyhanni.features.garden.fortuneguide.FarmingItemType
import at.hannibal2.skyhanni.features.garden.fortuneguide.FortuneUpgrade
import at.hannibal2.skyhanni.features.garden.fortuneguide.FortuneUpgrades
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.NeuItems.getItemStack
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RenderUtils.HorizontalAlignment
import at.hannibal2.skyhanni.utils.RenderUtils.VerticalAlignment
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addString
import at.hannibal2.skyhanni.utils.guide.GuideScrollPage
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.primitives.ItemStackRenderable.Companion.item
import at.hannibal2.skyhanni.utils.renderables.primitives.WrappedStringRenderable.Companion.wrappedText
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import java.text.DecimalFormat

class UpgradePage(val crop0: () -> CropType?, sizeX: Int, sizeY: Int, paddingX: Int = 15, paddingY: Int = 7) :
    GuideScrollPage(
        sizeX,
        sizeY,
        paddingX,
        paddingY,
        marginY = 10,
    ) {

    val crop get() = crop0()

    override fun onEnter() {
        crop?.let {
            FortuneUpgrades.getCropSpecific(it.farmingItem.getItemOrNull())
        } ?: {
            FortuneUpgrades.getCropSpecific(null) // TODO
        }

        FarmingItemType.resetClickState()
        val upgradeList = if (crop == null)
            FortuneUpgrades.genericUpgrades
        else
            FortuneUpgrades.cropSpecificUpgrades
        update(
            header = header(),
            content = upgradeList.map { upgrade -> upgrade.print() },
        )
    }

    private fun header() = listOf("Upgrade", "", "Item", "FF", "Cost/FF", "Total").map {
        Renderable.text(it, scale = 0.9, horizontalAlign = HorizontalAlignment.CENTER)
    }

    private fun FortuneUpgrade.print() = buildList {
        add(
            Renderable.wrappedText(
                description,
                136,
                0.75,
                verticalAlign = VerticalAlignment.CENTER,
            ),
        )
        add(
            Renderable.item(
                requiredItem.getItemStack(),
                8.0 / 9.0,
                verticalAlign = VerticalAlignment.CENTER,
            ).withTip(),
        )
        add(
            Renderable.wrappedText(
                requiredItem.repoItemName.let { if (itemQuantity == 1) it else "$it §fx$itemQuantity" }, // TODO wtf
                70,
                0.75,
                verticalAlign = VerticalAlignment.CENTER,
            ),
        )
        addString(
            "§a${DecimalFormat("0.##").format(fortuneIncrease)}",
            horizontalAlign = HorizontalAlignment.CENTER,
            verticalAlign = VerticalAlignment.CENTER,
        ) // TODO cleaner formating
        addString(
            "§6" + costPerFF?.shortFormat(),
            horizontalAlign = HorizontalAlignment.CENTER,
            verticalAlign = VerticalAlignment.CENTER,
        )
        addString(
            "§6" + cost?.shortFormat(),
            horizontalAlign = HorizontalAlignment.CENTER,
            verticalAlign = VerticalAlignment.CENTER,
        )
    }
}

