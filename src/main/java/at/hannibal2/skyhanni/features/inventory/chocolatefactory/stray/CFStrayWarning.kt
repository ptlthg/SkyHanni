package at.hannibal2.skyhanni.features.inventory.chocolatefactory.stray

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.features.inventory.chocolatefactory.CFStrayRabbitWarningConfig.StrayTypeEntry
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryUpdatedEvent
import at.hannibal2.skyhanni.events.hoppity.RabbitFoundEvent
import at.hannibal2.skyhanni.features.event.hoppity.HoppityApi
import at.hannibal2.skyhanni.features.event.hoppity.HoppityEggType
import at.hannibal2.skyhanni.features.event.hoppity.HoppityTextureHandler
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.CFApi
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.CFApi.caughtRabbitPattern
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.CFApi.specialRabbitTextures
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.data.CFDataLoader.clickMeGoldenRabbitPattern
import at.hannibal2.skyhanni.features.inventory.chocolatefactory.data.CFDataLoader.clickMeRabbitPattern
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.InventoryUtils.getUpperItems
import at.hannibal2.skyhanni.utils.ItemUtils.getSingleLineLore
import at.hannibal2.skyhanni.utils.ItemUtils.getSkullTexture
import at.hannibal2.skyhanni.utils.LorenzRarity
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.SpecialColor.toSpecialColor
import at.hannibal2.skyhanni.utils.SpecialColor.toSpecialColorInt
import at.hannibal2.skyhanni.utils.compat.GuiScreenUtils
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.inventory.ContainerChest
import net.minecraft.item.ItemStack
import kotlin.math.sin

@SkyHanniModule
object CFStrayWarning {

    private val config get() = CFApi.config
    private val warningConfig get() = config.rabbitWarning
    private const val CHROMA_COLOR = "249:255:255:85:85"
    private const val CHROMA_COLOR_ALT = "246:255:255:85:85"
    private const val CHROMA_COLOR_ALT2 = "243:255:255:85:85"

    private var flashScreen = false
    private var activeStraySlots: Set<Int> = setOf()

    private fun reset() {
        flashScreen = false
        activeStraySlots = setOf()
    }

    private fun isRarityOrHigher(stack: ItemStack, rarity: LorenzRarity) =
        stack.getSkullTexture()?.let { skullTexture ->
            HoppityTextureHandler.getRarityBySkullId(skullTexture)?.let { skullRarity ->
                skullRarity.ordinal >= rarity.ordinal
            } ?: false
        } ?: false

    private fun isSpecial(stack: ItemStack) =
        clickMeGoldenRabbitPattern.matches(stack.displayName) || stack.getSkullTexture() in specialRabbitTextures

    private fun shouldWarnAboutStray(item: ItemStack) = when (config.rabbitWarning.rabbitWarningLevel) {
        StrayTypeEntry.SPECIAL -> isSpecial(item)

        StrayTypeEntry.LEGENDARY_P -> isRarityOrHigher(item, LorenzRarity.LEGENDARY)
        StrayTypeEntry.EPIC_P -> isRarityOrHigher(item, LorenzRarity.EPIC)
        StrayTypeEntry.RARE_P -> isRarityOrHigher(item, LorenzRarity.RARE)
        StrayTypeEntry.UNCOMMON_P -> isRarityOrHigher(item, LorenzRarity.UNCOMMON)

        StrayTypeEntry.ALL -> clickMeRabbitPattern.matches(item.displayName) || isSpecial(item)

        StrayTypeEntry.NONE -> false
        else -> false
    }

    private fun handleRabbitWarnings(item: ItemStack) {
        if (caughtRabbitPattern.matches(item.getSingleLineLore())) return

        val clickMeMatches = clickMeRabbitPattern.matches(item.displayName)
        val goldenClickMeMatches = clickMeGoldenRabbitPattern.matches(item.displayName)
        if (!clickMeMatches && !goldenClickMeMatches || !shouldWarnAboutStray(item)) return

        val isSpecial = goldenClickMeMatches || item.getSkullTexture() in specialRabbitTextures

        if (isSpecial) SoundUtils.repeatSound(100, warningConfig.repeatSound, CFApi.warningSound)
        else SoundUtils.playBeepSound()
    }

    @HandleEvent
    fun onBackgroundDrawn(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (!CFApi.inChocolateFactory) return
        if (config.partyMode.get()) event.partyModeHighlight()
        else event.strayHighlight()
    }

    private fun GuiContainerEvent.getEventChest(): ContainerChest? =
        gui.inventorySlots as? ContainerChest

    private fun GuiContainerEvent.BackgroundDrawnEvent.partyModeHighlight() {
        val eventChest = getEventChest() ?: return
        eventChest.getUpperItems().keys.forEach { it.highlight(CHROMA_COLOR_ALT.toSpecialColor()) }
        eventChest.inventorySlots.filter {
            it.slotNumber != it.slotIndex
        }.forEach {
            it.highlight(CHROMA_COLOR_ALT2.toSpecialColor())
        }
    }

    private fun GuiContainerEvent.BackgroundDrawnEvent.strayHighlight() {
        val eventChest = getEventChest() ?: return
        eventChest.getUpperItems().keys.filter {
            it.slotNumber in activeStraySlots
        }.forEach {
            it.highlight(warningConfig.inventoryHighlightColor.toSpecialColor())
        }
    }

    @HandleEvent
    fun onInventoryUpdated(event: InventoryUpdatedEvent) {
        if (!CFApi.inChocolateFactory) {
            flashScreen = false
            return
        }
        val strayStacks = HoppityApi.filterMayBeStray(event.inventoryItems)
        strayStacks.forEach { handleRabbitWarnings(it.value) }
        val activeStrays = strayStacks.filterValues { !caughtRabbitPattern.matches(it.getSingleLineLore()) }
        activeStraySlots = activeStrays.keys
        flashScreen = activeStrays.any {
            val stack = it.value
            when (config.rabbitWarning.flashScreenLevel) {
                StrayTypeEntry.SPECIAL -> isSpecial(stack)

                StrayTypeEntry.LEGENDARY_P -> isRarityOrHigher(stack, LorenzRarity.LEGENDARY)
                StrayTypeEntry.EPIC_P -> isRarityOrHigher(stack, LorenzRarity.EPIC)
                StrayTypeEntry.RARE_P -> isRarityOrHigher(stack, LorenzRarity.RARE)
                StrayTypeEntry.UNCOMMON_P -> isRarityOrHigher(stack, LorenzRarity.UNCOMMON)

                StrayTypeEntry.ALL -> {
                    clickMeRabbitPattern.matches(it.value.displayName) || isSpecial(stack)
                }

                StrayTypeEntry.NONE -> false
                else -> false
            }
        }
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        reset()
    }

    @HandleEvent
    fun onRabbitFound(event: RabbitFoundEvent) {
        if (event.eggType != HoppityEggType.STRAY) return
        flashScreen = false
    }

    @HandleEvent(priority = HandleEvent.HIGHEST)
    fun onBackgroundDraw(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (!CFApi.inChocolateFactory) return
        if (!flashScreen && !config.partyMode.get()) return
        val alpha = ((2 + sin(SimpleTimeMark.now().toMillis() / 1000.0)) * 255 / 4).toInt().coerceIn(0..255)
        val toUse = if (config.partyMode.get()) CHROMA_COLOR else warningConfig.flashColor
        val color = (alpha shl 24) or (toUse.toSpecialColorInt() and 0xFFFFFF)
        GuiRenderUtils.drawRect(0, 0, GuiScreenUtils.displayWidth, GuiScreenUtils.displayHeight, color)
        GlStateManager.color(1F, 1F, 1F, 1F)
    }
}
