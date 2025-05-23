package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.core.config.Position
import at.hannibal2.skyhanni.config.core.config.gui.GuiPositionEditor
import at.hannibal2.skyhanni.events.GuiPositionMovedEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.minecraft.KeyPressEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.SkyHanniDebugsAndTests
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.SignUtils.isGardenSign
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.StringUtils
import at.hannibal2.skyhanni.utils.TimeLimitedCache
import at.hannibal2.skyhanni.utils.compat.DrawContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.client.gui.inventory.GuiEditSign
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object GuiEditManager {

    private var lastHotkeyPressed = SimpleTimeMark.farPast()

    private val currentPositions = TimeLimitedCache<String, Position>(15.seconds)
    private val currentBorderSize = mutableMapOf<String, Pair<Int, Int>>()
    private var lastMovedGui: String? = null

    @HandleEvent
    fun onKeyPress(event: KeyPressEvent) {
        if (event.keyCode != SkyHanniMod.feature.gui.keyBindOpen) return
        if (event.keyCode == Keyboard.KEY_RETURN) {
            ChatUtils.chat("You can't use Enter as a keybind to open the gui editor!")
            return
        }
        if (isInGui()) return

        val guiScreen = Minecraft.getMinecraft().currentScreen
        val openGui = guiScreen?.javaClass?.name ?: "none"
        val isInNeuPv = openGui == "io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer"
        if (isInNeuPv) return
        guiScreen?.let {
            if (it !is GuiInventory && it !is GuiChest && it !is GuiEditSign) return
            if (it is GuiEditSign && !it.isGardenSign()) return
        }

        if (lastHotkeyPressed.passedSince() < 500.milliseconds) return
        if (NeuItems.neuHasFocus()) return
        lastHotkeyPressed = SimpleTimeMark.now()

        openGuiPositionEditor(hotkeyReminder = false)
    }

    @HandleEvent(priority = HandleEvent.LOWEST)
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        GlStateManager.color(1f, 1f, 1f, 1f)
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0)
    }

    @HandleEvent
    fun onTick() {
        lastMovedGui?.let {
            GuiPositionMovedEvent(it).post()
            lastMovedGui = null
        }
    }

    @JvmStatic
    fun add(position: Position, posLabel: String, width: Int, height: Int) {
        val name = position.getOrSetInternalName {
            if (posLabel == "none") "none ${StringUtils.generateRandomId()}" else posLabel
        }
        currentPositions[name] = position
        currentBorderSize[posLabel] = Pair(width, height)
    }

    private var lastHotkeyReminded = SimpleTimeMark.farPast()

    @JvmStatic
    fun openGuiPositionEditor(hotkeyReminder: Boolean) {
        SkyHanniMod.screenToOpen = GuiPositionEditor(
            currentPositions.values.toList(),
            2,
            Minecraft.getMinecraft().currentScreen as? GuiContainer,
        )
        if (hotkeyReminder && lastHotkeyReminded.passedSince() > 30.minutes) {
            lastHotkeyReminded = SimpleTimeMark.now()
            ChatUtils.chat(
                "§eTo edit hidden GUI elements:\n" +
                    " §7- §e1. Set a key in /sh edit.\n" +
                    " §7- §e2. Click that key while the GUI element is visible.",
            )
        }
    }

    @JvmStatic
    fun renderLast(context: DrawContext) {
        if (!isInGui()) return
        if (!SkyHanniDebugsAndTests.globalRender) return

        context.matrices.translate(0f, 0f, 200f)

        RenderData.renderOverlay(context)

        context.matrices.pushMatrix()
        GlStateManager.enableDepth()
        GuiRenderEvent.ChestGuiOverlayRenderEvent(context).post()
        context.matrices.popMatrix()

        context.matrices.translate(0f, 0f, -200f)
    }

    fun isInGui() = Minecraft.getMinecraft().currentScreen is GuiPositionEditor

    fun Position.getDummySize(random: Boolean = false): Vector2i {
        if (random) return Vector2i(5, 5)
        val (x, y) = currentBorderSize[internalName] ?: return Vector2i(1, 1)
        return Vector2i((x * effectiveScale).toInt(), (y * effectiveScale).toInt())
    }

    fun Position.getAbsX() = getAbsX0(getDummySize(true).x)

    fun Position.getAbsY() = getAbsY0(getDummySize(true).y)

    fun handleGuiPositionMoved(guiName: String) {
        lastMovedGui = guiName
    }
}

// TODO remove
class Vector2i(val x: Int, val y: Int)
