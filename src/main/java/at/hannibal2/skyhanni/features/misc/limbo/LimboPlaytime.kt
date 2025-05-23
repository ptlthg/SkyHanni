package at.hannibal2.skyhanni.features.misc.limbo

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.events.InventoryOpenEvent
import at.hannibal2.skyhanni.events.minecraft.ToolTipEvent
import at.hannibal2.skyhanni.events.render.gui.ReplaceItemEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NeuItems.getItemStack
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.client.player.inventory.ContainerLocalMenu
import net.minecraft.item.ItemStack
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object LimboPlaytime {
    private lateinit var modifiedList: MutableList<String>
    private var setMinutes = false
    private val patternGroup = RepoPattern.group("misc.limbo.tooltip")

    /**
     * REGEX-TEST: §5§o§a10,032.8 minutes
     */
    private val minutesPattern by patternGroup.pattern(
        "minutes",
        "§5§o§a(?<minutes>[\\d.,]+) minutes.*$",
    )

    /**
     * REGEX-TEST: §5§o§b1,000.4 hours
     */
    private val hoursPattern by patternGroup.pattern(
        "hours",
        "§5§o§b(?<hours>[\\d.,]+) hours.*$",
    )

    var tooltipPlaytime = mutableListOf<String>()

    private var wholeMinutes = 0
    private var hoursString: String = ""

    private val storage get() = ProfileStorageData.playerSpecific?.limbo
    private val enabled get() = SkyHanniMod.feature.misc.showLimboTimeInPlaytimeDetailed

    private val itemID = "ENDER_PEARL".toInternalName()
    private const val ITEM_NAME = "§aLimbo"
    private lateinit var limboItem: ItemStack
    private var lastCreateCooldown = SimpleTimeMark.farPast()

    @HandleEvent
    fun replaceItem(event: ReplaceItemEvent) {
        if (!enabled) return
        if (event.inventory !is ContainerLocalMenu) return
        // TODO replace with InventoryDetector
        if (InventoryUtils.openInventoryName() != "Detailed /playtime") return
        if (event.slot != 43) return
        val playtime = storage?.playtime ?: 0
        if (playtime < 60) return

        if (lastCreateCooldown.passedSince() > 3.seconds) {
            lastCreateCooldown = SimpleTimeMark.now()
            limboItem = ItemUtils.createItemStack(
                itemID.getItemStack().item,
                ITEM_NAME,
                *createItemLore()
            )
        }
        event.replace(limboItem)
    }

    private fun createItemLore(): Array<String> = when {
        wholeMinutes >= 60 -> arrayOf(
            "§7Playtime: §a${wholeMinutes.addSeparators()} minutes",
            "§7Or: §b$hoursString hours",
        )

        wholeMinutes == 1 -> arrayOf("§7Playtime: §a$wholeMinutes minute")

        else -> arrayOf("§7Playtime: §a$wholeMinutes minutes")
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onToolTip(event: ToolTipEvent) {
        if (!enabled) return
        // TODO replace with InventoryDetector
        if (!InventoryUtils.openInventoryName().startsWith("Detailed /playtime")) return
        if (event.slot.slotIndex != 4) return
        val playtime = storage?.playtime ?: 0
        if (playtime <= 120) return

        val lore = event.toolTip
        val hoursList = lore.filter { hoursPattern.matches(it) }.toMutableList()
        val minutesList = lore.filter { minutesPattern.matches(it) }.toMutableList()

        addLimbo(hoursList, minutesList)
        remakeList(event.toolTip, minutesList, hoursList)
    }

    @HandleEvent
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (event.inventoryName != "Detailed /playtime") return
        val playtime = (storage?.playtime ?: 0).seconds
        if (playtime < 60.seconds) return
        val wholeHours = playtime.inWholeHours
        wholeMinutes = playtime.inWholeMinutes.toInt()
        if ((wholeMinutes % 60) == 0) {
            hoursString = "$wholeHours"
        } else {
            val minutes: Float = ((wholeMinutes - wholeHours * 60).toFloat() / 60).roundTo(1)
            hoursString = wholeHours.addSeparators()
            if (findFloatDecimalPlace(minutes) != 0) {
                val minutesString = minutes.toString()
                hoursString += minutesString.substring(minutesString.indexOf("."))
            }
        }
    }

    private fun addLimbo(hoursList: MutableList<String>, minutesList: MutableList<String>) {
        val storedPlaytime = storage?.playtime ?: 0
        if (wholeMinutes >= 60) {
            modifiedList = hoursList
            modifiedList.add("§5§o§b$hoursString hours §7on Limbo")
            modifiedList = modifiedList.sortedByDescending {
                val matcher = hoursPattern.matcher(it)
                if (matcher.find()) {
                    matcher.group("hours").replace(",", "").toDoubleOrNull() ?: 0.0
                } else 0.0
            }.toMutableList()
            setMinutes = false
        } else {
            val minutes = storedPlaytime.seconds.inWholeMinutes
            modifiedList = minutesList
            modifiedList.add("§5§o§a$minutes minutes §7on Limbo")
            modifiedList = modifiedList.sortedByDescending {
                val matcher = minutesPattern.matcher(it)
                if (matcher.find()) {
                    matcher.group("minutes").toDoubleOrNull() ?: 0.0
                } else 0.0
            }.toMutableList()
            setMinutes = true
        }
    }

    private fun remakeList(
        toolTip: MutableList<String>,
        minutesList: MutableList<String>,
        hoursList: MutableList<String>,
    ) {
        val firstList = mutableListOf<String>()
        val lastList = mutableListOf<String>()
        var hasPassed = false
        for (line in toolTip) {
            if (!(hoursPattern.matches(line) || minutesPattern.matches(line)) && !hasPassed) {
                firstList.add(line)
            } else hasPassed = true
        }
        hasPassed = false
        for (line in toolTip) {
            if (!(hoursPattern.matches(line) || minutesPattern.matches(line)) && hasPassed) {
                lastList.add(line)
            } else hasPassed = true
        }
        toolTip.clear()
        toolTip.addAll(firstList)
        if (!setMinutes) {
            toolTip.addAll(modifiedList)
            toolTip.addAll(minutesList)
        } else {
            toolTip.addAll(hoursList)
            toolTip.addAll(modifiedList)
        }
        toolTip.addAll(lastList)

        tooltipPlaytime = toolTip
    }

    private fun findFloatDecimalPlace(input: Float): Int {
        val string = input.toString()
        val dotIndex = string.indexOf(".")
        return (string[dotIndex + 1].toString().toInt())
    }
}
