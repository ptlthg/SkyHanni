package at.hannibal2.skyhanni.features.misc.items

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.features.misc.EstimatedItemValueConfig
import at.hannibal2.skyhanni.data.jsonobjects.repo.ItemValueCalculationDataJson
import at.hannibal2.skyhanni.data.jsonobjects.repo.ItemsJson
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.NeuRepositoryReloadEvent
import at.hannibal2.skyhanni.events.RenderItemTooltipEvent
import at.hannibal2.skyhanni.events.RepositoryReloadEvent
import at.hannibal2.skyhanni.events.item.ItemHoverEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ConditionalUtils
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalNameOrNull
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.ItemUtils.isRune
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyClicked
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.compat.DrawContextUtils
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.system.PlatformUtils
import io.github.moulberry.notenoughupdates.NotEnoughUpdates
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer
import net.minecraft.client.Minecraft
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import org.lwjgl.input.Keyboard
import kotlin.math.roundToLong

@SkyHanniModule
object EstimatedItemValue {

    val config: EstimatedItemValueConfig get() = SkyHanniMod.feature.inventory.estimatedItemValues
    private var display = emptyList<Renderable>()
    private val cache = mutableMapOf<ItemStack, List<Renderable>>()
    private var lastToolTipTime = 0L
    var gemstoneUnlockCosts = HashMap<NeuInternalName, HashMap<String, List<String>>>()
    var bookBundleAmount = mapOf<String, Int>()
    var crimsonPrestigeCosts = mapOf<String, Map<NeuInternalName, Int>>()
    private var currentlyShowing = false

    var itemValueCalculationData: ItemValueCalculationDataJson? = null
        private set

    fun isCurrentlyShowing() = currentlyShowing && Minecraft.getMinecraft().currentScreen != null

    @HandleEvent
    fun onNeuRepoReload(event: NeuRepositoryReloadEvent) {
        gemstoneUnlockCosts =
            event.readConstant<HashMap<NeuInternalName, HashMap<String, List<String>>>>("gemstonecosts")
    }

    @HandleEvent
    fun onRepoReload(event: RepositoryReloadEvent) {
        val data = event.getConstant<ItemsJson>("Items")
        bookBundleAmount = data.bookBundleAmount
        itemValueCalculationData = data.valueCalculationData
        crimsonPrestigeCosts = data.crimsonPrestigeCosts
    }

    private fun isInNeuOverlay(): Boolean {
        val inPv = Minecraft.getMinecraft().currentScreen is GuiProfileViewer
        val inTrade = InventoryUtils.openInventoryName().startsWith("You  ")

        // Use reflection to make sure tradeMenu exists
        val neuConfig = NotEnoughUpdates.INSTANCE.config
        val tradeField = neuConfig.javaClass.getDeclaredField("tradeMenu")
        val trade = tradeField[neuConfig]

        val booleanField = trade.javaClass.getDeclaredField("enableCustomTrade")
        val customTradeEnabled = booleanField[trade] as Boolean

        val inNeuTrade = inTrade && customTradeEnabled
        val inStorage = InventoryUtils.inStorage() && InventoryUtils.isNeuStorageEnabled

        return inPv || inNeuTrade || inStorage
    }

    fun onNeuDrawEquipment(stack: ItemStack) {
        renderedItems++
        updateItem(stack)
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onTooltip(event: ItemHoverEvent) {
        if (!config.enabled) return
        if (!PlatformUtils.isNeuLoaded()) return
        if (!isInNeuOverlay()) return

        if (renderedItems == 0) {
            updateItem(event.itemStack)
        }
        val inStorage = InventoryUtils.inStorage() && InventoryUtils.isNeuStorageEnabled
        // we use renderInNeuStorageOverlay() for this
        if (inStorage) return

        // render the estimated item value over NEU PV
        DrawContextUtils.translate(0f, 0f, 200f)
        tryRendering()
        DrawContextUtils.translate(0f, 0f, -200f)

        renderedItems++
    }

    /**
     * Workaround for NEU Profile Viewer bug where the ItemTooltipEvent gets called for two items when hovering
     * over the border between two items.
     * Also fixes complications with ChatTriggers where they call the stack.getToolTips() method that causes the
     * ItemTooltipEvent to getting triggered multiple times per frame.
     */
    private var renderedItems = 0

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        renderedItems = 0
    }

    fun tryRendering() {
        currentlyShowing = checkCurrentlyVisible()
        if (!currentlyShowing) return

        if (LorenzUtils.debug) {
            if (Keyboard.KEY_RIGHT.isKeyClicked()) {
                EstimatedItemValueCalculator.starChange += 1
                cache.clear()
            } else if (Keyboard.KEY_LEFT.isKeyClicked()) {
                EstimatedItemValueCalculator.starChange -= 1
                cache.clear()
            }
        }

        try {
            // TODO this code needs to be changed around
            config.itemPriceDataPos.renderRenderables(display, posLabel = "Estimated Item Value")
        } catch (ex: RuntimeException) {
            // "No OpenGL context found in the current thread." - caused indiscriminately by any other mod
            // that tries to over-render the tooltip, and is not explicitly something we can solve here?
            // TODO start a deep sea activity: read mixin dumps, pinpoint the culprit, write over engineered workaround
            if (ex.message?.contains("No OpenGL context found in the current thread.") == true) return
            ErrorManager.logErrorWithData(
                ex, "Error in Estimated Item Value renderer",
                "display" to display,
                "posLabel" to "Estimated Item Value",
            )
        }
    }

    @HandleEvent
    fun onBackgroundDraw(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        tryRendering()
    }

    private fun checkCurrentlyVisible(): Boolean {
        if (!LorenzUtils.inSkyBlock) return false
        if (!config.enabled) return false
        if (!config.hotkey.isKeyHeld() && !config.alwaysEnabled) return false
        if (System.currentTimeMillis() > lastToolTipTime + 200) return false

        if (display.isEmpty()) return false

        return true
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        cache.clear()
    }

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        with(config) {
            ConditionalUtils.onToggle(
                enchantmentsCap,
                exactPrice,
                ignoreHelmetSkins,
                ignoreArmorDyes,
                ignoreRunes,
                priceSource,
                useAttributeComposite,
            ) {
                cache.clear()
            }
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onRenderItemTooltip(event: RenderItemTooltipEvent) {
        if (!config.enabled) return

        updateItem(event.stack)
    }

    fun updateItem(item: ItemStack) {
        cache[item]?.let {
            display = it
            lastToolTipTime = System.currentTimeMillis()
            return
        }

        val openInventoryName = InventoryUtils.openInventoryName()
        if (openInventoryName.startsWith("Museum ")) {
            if (item.getLore().any { it.contains("Armor Set") }) {
                return
            }
        }
        if (openInventoryName == "Island Deliveries") {
            if (item.getLore().any { it == "§eClick to collect!" }) {
                return
            }
        }

        val newDisplay = try {
            draw(item)
        } catch (e: Exception) {
            ErrorManager.logErrorWithData(
                e, "Error in Estimated Item Value renderer",
                "openInventoryName" to openInventoryName,
                "item" to item,
                "item name" to item.repoItemName,
                "internal name" to item.getInternalNameOrNull(),
                "lore" to item.getLore(),
            )
            listOf()
        }

        cache[item] = newDisplay
        display = newDisplay
        lastToolTipTime = System.currentTimeMillis()
    }

    private fun ItemStack.shouldIgnoreDraw(): Boolean {
        this.getInternalNameOrNull()?.let { internalName ->
            val name = this.displayName
            return (
                this.item == Items.enchanted_book ||
                    name.contains("Salesperson") ||
                    name == "§6☘ Category: Item Ability (Passive)" ||
                    internalName.isRune() ||
                    internalName.startsWith("ULTIMATE_ULTIMATE_") ||
                    internalName.startsWith("CATACOMBS_PASS_") ||
                    internalName.startsWith("MASTER_CATACOMBS_PASS_") ||
                    internalName.startsWith("MAP-") ||
                    internalName.contains("UNIQUE_RUNE") ||
                    internalName.contains("WISP_POTION") ||
                    (
                        !InventoryUtils.isSlotInPlayerInventory(this) &&
                            InventoryUtils.openInventoryName() == "Choose a wardrobe slot"
                        )
                )
        } ?: return true
    }

    private fun draw(stack: ItemStack): List<Renderable> {
        if (stack.shouldIgnoreDraw()) return listOf()

        val list = mutableListOf<String>()
        list.add("§aEstimated Item Value:")
        val pair = EstimatedItemValueCalculator.calculate(stack, list)
        val (totalPrice, basePrice) = pair

        if (basePrice == totalPrice) return listOf()

        val numberFormat = if (config.exactPrice.get()) {
            totalPrice.roundToLong().addSeparators()
        } else {
            totalPrice.shortFormat()
        }
        list.add("§aTotal: §6§l$numberFormat coins")

        val newDisplay = mutableListOf<Renderable>()
        for (line in list) {
            newDisplay.add(Renderable.string(line))
        }
        return newDisplay
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(3, "misc.estimatedIemValueEnabled", "misc.estimatedItemValues.enabled")
        event.move(3, "misc.estimatedItemValueHotkey", "misc.estimatedItemValues.hotkey")
        event.move(3, "misc.estimatedIemValueAlwaysEnabled", "misc.estimatedItemValues.alwaysEnabled")
        event.move(3, "misc.estimatedIemValueEnchantmentsCap", "misc.estimatedItemValues.enchantmentsCap")
        event.move(3, "misc.estimatedIemValueExactPrice", "misc.estimatedItemValues.exactPrice")
        event.move(3, "misc.itemPriceDataPos", "misc.estimatedItemValues.itemPriceDataPos")

        event.move(31, "misc.estimatedItemValues", "inventory.estimatedItemValues")
    }

    fun renderInNeuStorageOverlay() {
        if (!config.enabled) return

        //#if MC < 1.12
        // render the estimated item value over NEU Storage
        DrawContextUtils.translate(0f, 0f, 200f)
        tryRendering()
        DrawContextUtils.translate(0f, 0f, -200f)
        renderedItems++
        //#endif
    }
}
