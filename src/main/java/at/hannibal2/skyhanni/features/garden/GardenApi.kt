package at.hannibal2.skyhanni.features.garden

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.PetApi
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.data.jsonobjects.repo.GardenJson
import at.hannibal2.skyhanni.events.BlockClickEvent
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.RepositoryReloadEvent
import at.hannibal2.skyhanni.events.garden.GardenToolChangeEvent
import at.hannibal2.skyhanni.events.garden.farming.CropClickEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniTickEvent
import at.hannibal2.skyhanni.events.minecraft.packet.PacketSentEvent
import at.hannibal2.skyhanni.features.event.hoppity.HoppityCollectionStats
import at.hannibal2.skyhanni.features.garden.CropType.Companion.getCropType
import at.hannibal2.skyhanni.features.garden.composter.ComposterOverlay
import at.hannibal2.skyhanni.features.garden.contest.FarmingContestApi
import at.hannibal2.skyhanni.features.garden.farming.GardenBestCropTime
import at.hannibal2.skyhanni.features.garden.farming.GardenCropSpeed
import at.hannibal2.skyhanni.features.garden.fortuneguide.FFGuideGui
import at.hannibal2.skyhanni.features.garden.fortuneguide.FarmingItemType
import at.hannibal2.skyhanni.features.garden.inventory.SkyMartCopperPrice
import at.hannibal2.skyhanni.features.garden.pests.PesthunterProfit
import at.hannibal2.skyhanni.features.garden.visitor.VisitorApi
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.CFApi
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.CFShopPrice
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.BlockUtils.isBabyCrop
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.ItemUtils.getItemRarityOrNull
import at.hannibal2.skyhanni.utils.LocationUtils.isPlayerInside
import at.hannibal2.skyhanni.utils.LorenzRarity
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getCultivatingCounter
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getHoeCounter
import at.hannibal2.skyhanni.utils.TimeLimitedCache
import net.minecraft.client.Minecraft
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minecraft.util.AxisAlignedBB
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@SkyHanniModule
object GardenApi {

    var toolInHand: String? = null
    var itemInHand: ItemStack? = null
    var cropInHand: CropType? = null
    var pestCooldownEndTime = SimpleTimeMark.farPast()
    var lastCropBrokenTime = SimpleTimeMark.farPast()
    val mushroomCowPet
        get() = PetApi.isCurrentPet("Mooshroom Cow") &&
            FarmingItemType.MOOSHROOM_COW.getItemOrNull()?.let { it.getItemRarityOrNull()?.isAtLeast(LorenzRarity.RARE) } ?: false
    private var inBarn = false
    val onBarnPlot get() = inBarn && inGarden()
    val storage get() = ProfileStorageData.profileSpecific?.garden
    val config get() = SkyHanniMod.feature.garden
    var totalAmountVisitorsExisting = 0
    var gardenExp: Long?
        get() = storage?.experience
        set(value) {
            value?.let {
                storage?.experience = it
            }
        }
    private val cropIconCache: TimeLimitedCache<String, ItemStack> = TimeLimitedCache(10.minutes)
    private val barnArea = AxisAlignedBB(35.5, 70.0, -4.5, -32.5, 100.0, -46.5)

    // TODO USE SH-REPO
    private val otherToolsList = listOf(
        "BASIC_GARDENING_HOE",
        "ADVANCED_GARDENING_AXE",
        "BASIC_GARDENING_AXE",
        "ADVANCED_GARDENING_HOE",
        "ROOKIE_HOE",
        "BINGHOE",
    )

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onSendPacket(event: PacketSentEvent) {
        if (event.packet !is C09PacketHeldItemChange) return
        checkItemInHand()
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (!inGarden()) return
        checkItemInHand()
        DelayedRun.runDelayed(500.milliseconds) {
            if (inGarden()) {
                checkItemInHand()
            }
        }
    }

    @HandleEvent
    fun onTick(event: SkyHanniTickEvent) {
        if (!inGarden()) return
        if (event.isMod(10, 1)) {
            inBarn = barnArea.isPlayerInside()

            // We ignore random hypixel moments
            Minecraft.getMinecraft().currentScreen ?: return
            checkItemInHand()
        }
    }

    @HandleEvent
    fun onIslandChange(event: IslandChangeEvent) {
        if (event.newIsland != IslandType.GARDEN) return
        checkItemInHand()
    }

    @HandleEvent
    fun onDebug(event: DebugDataCollectEvent) {
        event.title("Garden API")
        if (!inGarden()) return event.addIrrelevant("Not in garden")

        event.addData {
            if (cropIconCache.isNotEmpty()) {
                add("cropIconCache:")
                addAll(
                    cropIconCache.map { (key, value) ->
                        " $key: ${value.getInternalName()}"
                    },
                )
            } else {
                add("cropIconCache is empty")
            }
        }
    }

    private fun updateGardenTool() {
        GardenToolChangeEvent(cropInHand, itemInHand).post()
    }

    private fun checkItemInHand() {
        val toolItem = InventoryUtils.getItemInHand()
        val crop = toolItem?.getCropType()
        val newTool = getToolInHand(toolItem, crop)
        if (toolInHand != newTool) {
            toolInHand = newTool
            cropInHand = crop
            itemInHand = toolItem
            updateGardenTool()
        }
    }

    private fun getToolInHand(toolItem: ItemStack?, crop: CropType?): String? {
        if (crop != null) return crop.cropName

        val internalName = toolItem?.getInternalName() ?: return null
        return if (isOtherTool(internalName)) internalName.asString() else null
    }

    private fun isOtherTool(internalName: NeuInternalName): Boolean {
        return internalName.asString() in otherToolsList
    }

    fun inGarden() = IslandType.GARDEN.isInIsland()

    fun isCurrentlyFarming() = inGarden() && GardenCropSpeed.averageBlocksPerSecond > 0.0 && hasFarmingToolInHand()

    fun hasFarmingToolInHand() = InventoryUtils.getItemInHand()?.let {
        val crop = it.getCropType()
        getToolInHand(it, crop) != null
    } ?: false

    fun ItemStack.getCropType(): CropType? {
        val internalName = getInternalName()
        return CropType.entries.firstOrNull { internalName.startsWith(it.toolName) }
    }

    fun readCounter(itemStack: ItemStack): Long? = itemStack.getHoeCounter() ?: itemStack.getCultivatingCounter()

    fun CropType.getItemStackCopy(iconId: String): ItemStack = cropIconCache.getOrPut(iconId) { icon.copy() }

    fun hideExtraGuis() = ComposterOverlay.inInventory ||
        AnitaMedalProfit.inInventory ||
        SkyMartCopperPrice.inInventory ||
        FarmingContestApi.inInventory ||
        VisitorApi.inInventory ||
        FFGuideGui.isInGui() ||
        CFShopPrice.inInventory ||
        CFApi.inChocolateFactory ||
        CFApi.chocolateFactoryPaused ||
        HoppityCollectionStats.inInventory ||
        PesthunterProfit.isInInventory()

    fun resetCropSpeed() {
        storage?.cropsPerSecond?.clear()
        GardenBestCropTime.reset()
        updateGardenTool()
        ChatUtils.chat("Manually reset all crop speed data!")
    }

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        GardenBestCropTime.reset()
    }

    fun getCurrentlyFarmedCrop(): CropType? {
        val brokenCrop = if (toolInHand != null) GardenCropSpeed.lastBrokenCrop else null
        return cropInHand ?: brokenCrop
    }

    private var lastLocation: LorenzVec? = null

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onBlockClick(event: BlockClickEvent) {
        val blockState = event.getBlockState
        val cropBroken = blockState.getCropType() ?: return
        if (cropBroken.multiplier == 1 && blockState.isBabyCrop()) return

        val position = event.position
        if (lastLocation == position) {
            return
        }

        lastLocation = position
        CropClickEvent(position, cropBroken, blockState, event.clickType, event.itemInHand).post()
    }

    fun getExpForLevel(requestedLevel: Int): Long {
        var totalExp = 0L
        var tier = 0
        for (tierExp in gardenExperience) {
            totalExp += tierExp
            tier++
            if (tier == requestedLevel) {
                return totalExp
            }
        }

        while (tier < requestedLevel) {
            totalExp += gardenOverflowExp
            tier++
            if (tier == requestedLevel) {
                return totalExp
            }
        }
        return 0
    }

    fun getGardenLevel(overflow: Boolean = true): Int {
        val gardenExp = this.gardenExp ?: return 0
        var tier = 0
        var totalExp = 0L
        for (tierExp in gardenExperience) {
            totalExp += tierExp
            if (totalExp > gardenExp) {
                return tier
            }
            tier++
        }
        if (overflow) {
            totalExp += gardenOverflowExp

            while (totalExp < gardenExp) {
                tier++
                totalExp += gardenOverflowExp
            }
        }
        return tier
    }

    @HandleEvent
    fun onRepoReload(event: RepositoryReloadEvent) {
        val data = event.getConstant<GardenJson>("Garden")
        gardenExperience = data.gardenExp
        totalAmountVisitorsExisting = data.visitors.size
    }

    private var gardenExperience = listOf<Int>()
    private const val gardenOverflowExp = 10000
}
