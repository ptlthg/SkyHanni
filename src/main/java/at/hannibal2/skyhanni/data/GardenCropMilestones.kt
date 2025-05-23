package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.jsonobjects.repo.GardenJson
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.RepositoryReloadEvent
import at.hannibal2.skyhanni.events.garden.farming.CropMilestoneUpdateEvent
import at.hannibal2.skyhanni.features.garden.CropType
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils.chat
import at.hannibal2.skyhanni.utils.ChatUtils.clickableChat
import at.hannibal2.skyhanni.utils.ClipboardUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.NumberUtil.formatLong
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.SoundUtils.playSound
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.item.ItemStack

@SkyHanniModule
object GardenCropMilestones {

    private val patternGroup = RepoPattern.group("data.garden.milestone")

    /**
     * REGEX-TEST: §7Harvest §fWheat §7on your Garden to
     * REGEX-TEST: §7Harvest §fCocoa Beans §7on your
     */
    private val cropPattern by patternGroup.pattern(
        "crop",
        "§7Harvest §f(?<name>.*) §7on .*",
    )

    /**
     * REGEX-TEST: §7Total: §a36,967,397
     */
    val totalPattern by patternGroup.pattern(
        "total",
        "§7Total: §a(?<name>.*)",
    )

    private val config get() = GardenApi.config.cropMilestones

    fun getCropTypeByLore(itemStack: ItemStack): CropType? {
        cropPattern.firstMatcher(itemStack.getLore()) {
            val name = group("name")
            return CropType.getByNameOrNull(name)
        }
        return null
    }

    @HandleEvent
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        if (event.inventoryName != "Crop Milestones") return

        for ((_, stack) in event.inventoryItems) {
            val crop = getCropTypeByLore(stack) ?: continue
            totalPattern.firstMatcher(stack.getLore()) {
                val amount = group("name").formatLong()
                crop.setCounter(amount)
            }
        }
        CropMilestoneUpdateEvent.post()
        GardenCropMilestonesCommunityFix.openInventory(event.inventoryItems)
    }

    fun onOverflowLevelUp(crop: CropType, oldLevel: Int, newLevel: Int) {
        val customGoalLevel = ProfileStorageData.profileSpecific?.garden?.customGoalMilestone?.get(crop) ?: 0
        val goalReached = newLevel == customGoalLevel

        // TODO utils function that is shared with Garden Level Display
        val rewards = buildList {
            add("    §r§8+§aRespect from Elite Farmers and SkyHanni members :)")
            add("    §r§8+§b1 Flexing Point")
            if (newLevel % 5 == 0)
                add("    §r§7§8+§d2 SkyHanni User Luck")
        }

        val cropName = crop.cropName
        val levelUpLine = "§r§b§lGARDEN MILESTONE §3$cropName §8$oldLevel➜§3$newLevel§r"
        val messages = listOf(
            "§r§3§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬§r",
            "  $levelUpLine",
            if (goalReached)
                listOf(
                    "",
                    "  §r§d§lGOAL REACHED!",
                    "",
                ).joinToString("\n")
            else
                "",
            "  §r§a§lREWARDS§r",
            rewards.joinToString("\n"),
            "§r§3§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬§r",
        )

        clickableChat(
            messages.joinToString("\n"),
            { ClipboardUtils.copyToClipboard(levelUpLine.removeColor()) },
            "Click to copy!",
            prefix = false
        )

        val message = "§e§lYou have reached your milestone goal of §b§l$customGoalLevel " +
            "§e§lin the §b§l$cropName §e§lcrop!"
        if (goalReached) {
            chat(message, false)
        }

        SoundUtils.createSound("random.levelup", 1f, 1f).playSound()
    }

    var cropMilestoneData: Map<CropType, List<Int>> = emptyMap()

    val cropCounter: MutableMap<CropType, Long>? get() = GardenApi.storage?.cropCounter

    // TODO make nullable
    fun CropType.getCounter() = cropCounter?.get(this) ?: 0

    fun CropType.setCounter(counter: Long) {
        cropCounter?.set(this, counter)
    }

    fun CropType.isMaxed(useOverflow: Boolean): Boolean {
        if (useOverflow) return false

        // TODO change 1b
        val maxValue = cropMilestoneData[this]?.sum() ?: 1_000_000_000 // 1 bil for now
        return getCounter() >= maxValue
    }

    fun CropType.getTier(allowOverflow: Boolean = false): Int =
        getTierForCrop(this, allowOverflow)

    private fun getTierForCrop(crop: CropType, allowOverflow: Boolean = false): Int =
        getTierForCropCount(crop.getCounter(), crop, allowOverflow)

    fun getTierForCropCount(count: Long, crop: CropType, allowOverflow: Boolean = false): Int {
        var tier = 0
        var totalCrops = 0L
        val cropMilestone = cropMilestoneData[crop] ?: return 0
        val last = cropMilestone.last()

        for (tierCrops in cropMilestone) {
            totalCrops += tierCrops
            if (totalCrops >= count) {
                return tier
            }
            tier++
        }

        if (allowOverflow) {
            while (totalCrops < count) {
                totalCrops += last
                if (totalCrops >= count) {
                    return tier
                }
                tier++
            }
        }
        return tier
    }

    fun getMaxTier() = cropMilestoneData.values.firstOrNull()?.size ?: 0

    fun getCropsForTier(requestedTier: Int, crop: CropType, allowOverflow: Boolean = false): Long {
        var totalCrops = 0L
        var tier = 0
        val cropMilestone = cropMilestoneData[crop] ?: return 0
        val definedTiers = cropMilestone.size

        if (requestedTier <= definedTiers || !allowOverflow) {
            for (tierCrops in cropMilestone) {
                totalCrops += tierCrops
                tier++
                if (tier == requestedTier) {
                    return totalCrops
                }
            }

            return if (!allowOverflow) 0 else totalCrops
        }


        for (tierCrops in cropMilestone) {
            totalCrops += tierCrops
            tier++
        }

        val additionalTiers = requestedTier - definedTiers

        val lastIncrement = cropMilestone.last().toLong()

        totalCrops += lastIncrement * additionalTiers

        return totalCrops
    }

    fun CropType.progressToNextLevel(allowOverflow: Boolean = false): Double {
        val progress = getCounter()
        val startTier = getTierForCropCount(progress, this, allowOverflow)
        val startCrops = getCropsForTier(startTier, this, allowOverflow)
        val end = getCropsForTier(startTier + 1, this, allowOverflow)
        return (progress - startCrops).toDouble() / (end - startCrops)
    }

    @HandleEvent
    fun onRepoReload(event: RepositoryReloadEvent) {
        cropMilestoneData = event.getConstant<GardenJson>("Garden").cropMilestones
    }
}
