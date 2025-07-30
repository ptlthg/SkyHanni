package at.hannibal2.skyhanni.utils.renderables

import at.hannibal2.skyhanni.config.core.config.gui.GuiPositionEditor
import at.hannibal2.skyhanni.config.features.skillprogress.SkillProgressBarConfig
import at.hannibal2.skyhanni.data.GuiData
import at.hannibal2.skyhanni.data.HighlightOnHoverSlot
import at.hannibal2.skyhanni.data.RenderData
import at.hannibal2.skyhanni.data.ToolTipData
import at.hannibal2.skyhanni.data.model.TextInput
import at.hannibal2.skyhanni.mixins.hooks.RenderLivingEntityHelper
import at.hannibal2.skyhanni.utils.ColorUtils
import at.hannibal2.skyhanni.utils.ColorUtils.addAlpha
import at.hannibal2.skyhanni.utils.ColorUtils.darker
import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.KeyboardManager
import at.hannibal2.skyhanni.utils.KeyboardManager.LEFT_MOUSE
import at.hannibal2.skyhanni.utils.KeyboardManager.RIGHT_MOUSE
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyClicked
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzLogger
import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.RenderUtils.HorizontalAlignment
import at.hannibal2.skyhanni.utils.RenderUtils.VerticalAlignment
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.contains
import at.hannibal2.skyhanni.utils.compat.DrawContextUtils
import at.hannibal2.skyhanni.utils.compat.createResourceLocation
import at.hannibal2.skyhanni.utils.guide.GuideGui
import at.hannibal2.skyhanni.utils.render.ShaderRenderUtils
import at.hannibal2.skyhanni.utils.renderables.RenderableUtils.renderXAligned
import at.hannibal2.skyhanni.utils.renderables.RenderableUtils.renderXYAligned
import at.hannibal2.skyhanni.utils.renderables.RenderableUtils.renderYAligned
import at.hannibal2.skyhanni.utils.renderables.container.HorizontalContainerRenderable.Companion.horizontal
import at.hannibal2.skyhanni.utils.renderables.container.table.SearchableScrollTable.Companion.searchableScrollTable
import at.hannibal2.skyhanni.utils.renderables.primitives.ItemStackRenderable.Companion.item
import at.hannibal2.skyhanni.utils.renderables.primitives.placeholder
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import io.github.notenoughupdates.moulconfig.gui.GuiScreenElementWrapper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiIngameMenu
import net.minecraft.client.gui.inventory.GuiEditSign
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.max
//#if TODO
import at.hannibal2.skyhanni.features.chroma.ChromaShaderManager
import at.hannibal2.skyhanni.features.chroma.ChromaType
import at.hannibal2.skyhanni.features.misc.DarkenShader
import at.hannibal2.skyhanni.utils.shader.ShaderManager
//#endif
//#if MC < 1.21
import net.minecraft.client.gui.inventory.GuiInventory.drawEntityOnScreen
//#else
//$$ import net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity
//$$ import at.hannibal2.skyhanni.utils.compat.RenderCompat
//$$ import at.hannibal2.skyhanni.utils.render.SkyHanniRenderLayers
//#endif

// todo 1.21 impl needed
@Suppress("TooManyFunctions")
interface Renderable {

    val width: Int
    val height: Int

    val horizontalAlign: HorizontalAlignment
    val verticalAlign: VerticalAlignment

    fun isHovered(mouseOffsetX: Int, mouseOffsetY: Int) = currentRenderPassMousePosition?.let { (x, y) ->
        x in (mouseOffsetX..mouseOffsetX + width) && y in (mouseOffsetY..mouseOffsetY + height)
    } ?: false

    fun isBoxHovered(mouseOffsetX: Int, width: Int, mouseOffsetY: Int, height: Int) = currentRenderPassMousePosition?.let { (x, y) ->
        x in (mouseOffsetX..mouseOffsetX + width) && y in (mouseOffsetY..mouseOffsetY + height)
    } ?: false

    /**
     * Render the renderable. Enough said?
     * Pos x and pos y are relative to the mouse position.
     * (the GL matrix stack should already be pre transformed)
     *
     * @param mouseOffsetX The X offset of the mouse at this pass of rendering.
     * @param mouseOffsetY The Y offset of the mouse at this pass of rendering.
     */
    fun render(mouseOffsetX: Int, mouseOffsetY: Int)

    companion object {

        val logger = LorenzLogger("debug/renderable")
        var currentRenderPassMousePosition: Pair<Int, Int>? = null

        fun <T> withMousePosition(mousePositionX: Int, mousePositionY: Int, block: () -> T): T {
            val last = currentRenderPassMousePosition
            try {
                currentRenderPassMousePosition = Pair(mousePositionX, mousePositionY)
                return block()
            } finally {
                currentRenderPassMousePosition = last
            }
        }

        fun fromAny(any: Any?, itemScale: Double = NeuItems.ITEM_FONT_SIZE): Renderable? = when (any) {
            null -> placeholder(12)
            is Renderable -> any
            is String -> text(any)
            is ItemStack -> item(any, itemScale)
            else -> null
        }

        fun link(text: String, bypassChecks: Boolean = false, onLeftClick: () -> Unit): Renderable =
            link(text(text), onLeftClick, bypassChecks = bypassChecks)

        fun optionalLink(
            text: String,
            onLeftClick: () -> Unit,
            bypassChecks: Boolean = false,
            highlightsOnHoverSlots: List<Int> = emptyList(),
            condition: () -> Boolean = { true },
        ): Renderable = link(
            text(text),
            onLeftClick,
            bypassChecks,
            highlightsOnHoverSlots = highlightsOnHoverSlots,
            condition,
        )

        fun link(
            renderable: Renderable,
            onLeftClick: () -> Unit,
            bypassChecks: Boolean = false,
            highlightsOnHoverSlots: List<Int> = emptyList(),
            condition: () -> Boolean = { true },
            underlineColor: Color = Color.WHITE,
        ): Renderable = clickable(
            hoverable(
                underlined(renderable, underlineColor), renderable, bypassChecks,
                condition = condition,
                highlightsOnHoverSlots = highlightsOnHoverSlots,
            ),
            onLeftClick,
            bypassChecks,
            condition,
        )

        fun clickable(
            text: String,
            onLeftClick: () -> Unit,
            bypassChecks: Boolean = false,
            condition: () -> Boolean = { true },
            tips: List<Any>? = null,
            onHover: () -> Unit = {},
        ) = clickable(text(text), onLeftClick, bypassChecks, condition, tips, onHover)

        fun clickable(
            render: Renderable,
            onLeftClick: () -> Unit,
            bypassChecks: Boolean = false,
            condition: () -> Boolean = { true },
            tips: List<Any>? = null,
            onHover: () -> Unit = {},
        ) = clickable(render, mapOf(LEFT_MOUSE to onLeftClick), bypassChecks, condition, tips, onHover)

        fun clickable(
            text: String,
            /**
             * This should be a direct map of key code int, to the unit that should be invoked.
             * For mouse buttons, use [LEFT_MOUSE] and [RIGHT_MOUSE] from [at.hannibal2.skyhanni.utils.KeyboardManager].
             * For keyboard codes, use the [org.lwjgl.input.Keyboard] enums.
             */
            onAnyClick: Map<Int, () -> Unit>,
            bypassChecks: Boolean = false,
            condition: () -> Boolean = { true },
            tips: List<Any>? = null,
            onHover: () -> Unit = {},
        ) = clickable(text(text), onAnyClick, bypassChecks, condition, tips, onHover)

        fun clickable(
            render: Renderable,
            /**
             * This should be a direct map of key code int, to the unit that should be invoked.
             * For mouse buttons, use [LEFT_MOUSE] and [RIGHT_MOUSE] from [at.hannibal2.skyhanni.utils.KeyboardManager].
             * For keyboard codes, use the [org.lwjgl.input.Keyboard] enums.
             */
            onAnyClick: Map<Int, () -> Unit>,
            bypassChecks: Boolean = false,
            condition: () -> Boolean = { true },
            tips: List<Any>? = null,
            onHover: () -> Unit = {},
        ) = multiClickable(
            tips?.let {
                hoverTips(render, it, bypassChecks = bypassChecks, onHover = onHover)
            } ?: onHover.takeIf { it != {} }?.let {
                hoverable(render, render, bypassChecks = bypassChecks, onHover = onHover)
            } ?: render,
            onAnyClick,
            bypassChecks,
            condition,
        )

        private fun multiClickable(
            render: Renderable,
            onAnyClick: Map<Int, () -> Unit>,
            bypassChecks: Boolean = false,
            condition: () -> Boolean = { true },
            /**
             * This unit is invoked on 'hover & click' if no keys within [onAnyClick] invoke their unit.
             * This is useful for detecting things like scrolling, which do not have a direct key code to reference.
             *
             * See [clickableAndScrollable] for an example of how this is used.
             */
            nonStandardClick: () -> Unit = {},
        ) = object : Renderable {
            override val width = render.width
            override val height = render.height
            override val horizontalAlign = render.horizontalAlign
            override val verticalAlign = render.verticalAlign

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                if (isHovered(mouseOffsetX, mouseOffsetY) && condition() && shouldAllowLink(true, bypassChecks)) {
                    handleClickChecks()
                }
                render.render(mouseOffsetX, mouseOffsetY)
            }

            private fun handleClickChecks() {
                var processed = false
                for ((key, onKeyClicked) in onAnyClick) {
                    if (key.isKeyClicked()) {
                        onKeyClicked()
                        processed = true
                    }
                }
                if (!processed) nonStandardClick()
            }
        }

        fun clickableAndScrollable(
            render: Renderable,
            onAnyClick: Map<Int, () -> Unit>,
            bypassChecks: Boolean = false,
            condition: () -> Boolean = { true },
            scrollValue: ScrollValue = ScrollValue(),
        ): Renderable {
            val pureScrollInput = ScrollInput.Companion.PureVertical(scrollValue)

            return multiClickable(
                render = render,
                onAnyClick = onAnyClick,
                bypassChecks = bypassChecks,
                condition = condition,
                nonStandardClick = {
                    pureScrollInput.update(true)
                    when (pureScrollInput.asDirection()) {
                        ScrollInput.ScrollDirection.UP -> onAnyClick[RIGHT_MOUSE]?.invoke()
                        ScrollInput.ScrollDirection.DOWN -> onAnyClick[LEFT_MOUSE]?.invoke()
                        else -> {}
                    }
                    pureScrollInput.dispose()
                },
            )
        }

        fun hoverTips(
            content: Any,
            tips: List<Any>,
            highlightsOnHoverSlots: List<Int> = listOf(),
            stack: ItemStack? = null,
            color: LorenzColor? = null,
            spacedTitle: Boolean = false,
            bypassChecks: Boolean = false,
            snapsToTopIfToLong: Boolean = true,
            condition: () -> Boolean = { true },
            onHover: () -> Unit = {},
        ): Renderable {

            val render = fromAny(content) ?: text("Error")
            return object : Renderable {
                override val width = render.width
                override val height = render.height
                override val horizontalAlign = render.horizontalAlign
                override val verticalAlign = render.verticalAlign

                val tipsRender = tips.mapNotNull { fromAny(it) }

                override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                    render.render(mouseOffsetX, mouseOffsetY)
                    val pair = Pair(mouseOffsetX, mouseOffsetY)
                    if (isHovered(mouseOffsetX, mouseOffsetY)) {
                        if (condition() && shouldAllowLink(true, bypassChecks)) {
                            onHover.invoke()
                            HighlightOnHoverSlot.currentSlots[pair] = highlightsOnHoverSlots
                            DrawContextUtils.pushMatrix()
                            DrawContextUtils.translate(0F, 0F, 400F)

                            RenderableTooltips.setTooltipForRender(
                                tips = tipsRender,
                                stack = stack,
                                borderColor = color,
                                snapsToTopIfToLong = snapsToTopIfToLong,
                                spacedTitle = spacedTitle,
                            )
                            DrawContextUtils.popMatrix()
                        }
                    } else {
                        HighlightOnHoverSlot.currentSlots.remove(pair)
                    }
                }
            }
        }

        internal fun shouldAllowLink(debug: Boolean = false, bypassChecks: Boolean): Boolean {
            val guiScreen = Minecraft.getMinecraft().currentScreen.takeIf { it != null } ?: return false

            // Never support grayed out inventories
            if (RenderData.outsideInventory) return false

            if (bypassChecks) return true

            val inMenu = Minecraft.getMinecraft().currentScreen !is GuiIngameMenu
            val isGuiPositionEditor = guiScreen !is GuiPositionEditor
            val isNotInSignAndOnSlot = if (guiScreen !is GuiEditSign && guiScreen !is GuideGui<*>) {
                ToolTipData.lastSlot == null
                    || GuiData.preDrawEventCancelled
            } else true
            val isConfigScreen = guiScreen !is GuiScreenElementWrapper

            val openGui = guiScreen.javaClass.name ?: "none"
            val isInNeuPv = openGui == "io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer"
            val neuFocus = NeuItems.neuHasFocus()
            val isInSkytilsPv = openGui == "gg.skytils.skytilsmod.gui.profile.ProfileGui"
            val isInSkytilsSettings =
                openGui.let { it.startsWith("gg.skytils.vigilance.gui.") || it.startsWith("gg.skytils.skytilsmod.gui.") }
            val isInNeuSettings = openGui.startsWith("io.github.moulberry.notenoughupdates.")

            val result =
                isGuiPositionEditor &&
                    inMenu &&
                    isNotInSignAndOnSlot &&
                    isConfigScreen &&
                    !isInNeuPv &&
                    !isInSkytilsPv &&
                    !neuFocus &&
                    !isInSkytilsSettings &&
                    !isInNeuSettings

            if (debug) {
                if (!result) {
                    logger.log("")
                    logger.log("blocked link because:")
                    if (!isGuiPositionEditor) logger.log("isGuiPositionEditor")
                    if (!inMenu) logger.log("inMenu")
                    if (!isNotInSignAndOnSlot) logger.log("isNotInSignAndOnSlot")
                    if (!isConfigScreen) logger.log("isConfigScreen")
                    if (isInNeuPv) logger.log("isInNeuPv")
                    if (neuFocus) logger.log("neuFocus")
                    if (isInSkytilsPv) logger.log("isInSkytilsPv")
                    if (isInSkytilsSettings) logger.log("isInSkytilsSettings")
                    if (isInNeuSettings) logger.log("isInNeuSettings")
                    logger.log("")
                } else {
                    logger.log("allowed click")
                }
            }

            return result
        }

        fun underlined(renderable: Renderable, color: Color = Color.WHITE) = object : Renderable {
            override val width: Int
                get() = renderable.width
            override val height: Int
                get() = renderable.height + 1
            override val horizontalAlign = renderable.horizontalAlign
            override val verticalAlign = renderable.verticalAlign

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                GuiRenderUtils.drawRect(0, height, width, 11, color.rgb)
                GlStateManager.color(1F, 1F, 1F, 1F)
                renderable.render(mouseOffsetX, mouseOffsetY)
            }
        }

        fun hoverable(
            hovered: Renderable,
            unHovered: Renderable,
            bypassChecks: Boolean = false,
            condition: () -> Boolean = { true },
            highlightsOnHoverSlots: List<Int> = emptyList(),
            onHover: () -> Unit = {},
        ) = object : Renderable {
            override val width = max(hovered.width, unHovered.width)
            override val height = max(hovered.height, unHovered.height)
            override val horizontalAlign get() = if (isHovered) hovered.horizontalAlign else unHovered.horizontalAlign
            override val verticalAlign get() = if (isHovered) hovered.verticalAlign else unHovered.verticalAlign

            var isHovered = false

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                val pair = Pair(mouseOffsetX, mouseOffsetY)
                isHovered = if (isHovered(mouseOffsetX, mouseOffsetY) && condition() && shouldAllowLink(true, bypassChecks)) {
                    onHover()
                    hovered.render(mouseOffsetX, mouseOffsetY)
                    HighlightOnHoverSlot.currentSlots[pair] = highlightsOnHoverSlots
                    true
                } else {
                    unHovered.render(mouseOffsetX, mouseOffsetY)
                    HighlightOnHoverSlot.currentSlots.remove(pair)
                    false
                }
            }
        }

        /** Bottom Layer must be bigger then the top layer */
        fun doubleLayered(
            bottomLayer: Renderable,
            topLayer: Renderable,
            blockBottomHover: Boolean = true,
        ) = object : Renderable {
            override val width = bottomLayer.width
            override val height = bottomLayer.height
            override val horizontalAlign = bottomLayer.horizontalAlign
            override val verticalAlign = bottomLayer.verticalAlign

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                val (x, y) = topLayer.renderXYAligned(mouseOffsetX, mouseOffsetY, width, height)
                val topLayerHovered = topLayer.isHovered(mouseOffsetX + x, mouseOffsetY + y)
                val (nMouseOffsetX, nMouseOffsetY) = if (topLayerHovered && blockBottomHover) {
                    bottomLayer.width + 1 to bottomLayer.height + 1
                } else {
                    mouseOffsetX to mouseOffsetY
                }
                bottomLayer.render(nMouseOffsetX, nMouseOffsetY)
            }
        }

        fun Renderable.darken(amount: Float = 1f) = object : Renderable {
            override val width = this@darken.width
            override val height = this@darken.height
            override val horizontalAlign = this@darken.horizontalAlign
            override val verticalAlign = this@darken.verticalAlign

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                //#if TODO
                DarkenShader.darknessLevel = amount
                ShaderManager.enableShader(ShaderManager.Shaders.DARKEN)
                //#endif
                this@darken.render(mouseOffsetX, mouseOffsetY)
                //#if TODO
                ShaderManager.disableShader()
                //#endif
            }
        }

        /**
         * @param searchPrefix text that is static in front of the textbox
         * @param onUpdateSize function that is called if the size changes (since the search text can get bigger than [content])
         * @param textInput The text input, can be external or internal
         * @param shouldRenderTopElseBottom true == Renders on top, false == Renders at the Bottom
         * @param hideIfNoText hides text box if no input is given
         * @param ySpacing space between the search and [content]
         * @param onHover is triggered if [content] or the text box is hovered
         * @param bypassChecks bypass the [shouldAllowLink] logic
         * @param condition condition to being able to input / [onHover] to trigger
         * @param scale text scale of the textbox
         * @param color color of the textbox
         * @param key event key for the [textInput] to register the event, needs clearing if [textInput] is external, default = 0
         */
        fun searchBox(
            content: Renderable,
            searchPrefix: String,
            onUpdateSize: (Renderable) -> Unit,
            textInput: TextInput = TextInput(),
            shouldRenderTopElseBottom: Boolean = true,
            hideIfNoText: Boolean = true,
            ySpacing: Int = 0,
            onHover: (TextInput) -> Unit = {},
            bypassChecks: Boolean = false,
            condition: () -> Boolean = { true },
            scale: Double = 1.0,
            color: Color = Color.WHITE,
            key: Int = 0,
        ) = object : Renderable {
            val textBoxHeight = (9 * scale).toInt() + 1

            val isTextBoxEmpty get() = textInput.textBox.isEmpty()

            override var width: Int = content.width
            override var height: Int = content.height + if (hideIfNoText && isTextBoxEmpty) 0 else (ySpacing + textBoxHeight)
            override val horizontalAlign = content.horizontalAlign
            override val verticalAlign = content.verticalAlign

            val searchWidth: Int
                get() {
                    val fontRenderer = Minecraft.getMinecraft().fontRendererObj
                    val string = searchPrefix + textInput.editTextWithAlwaysCarriage()
                    return (fontRenderer.getStringWidth(string) * scale).toInt() + 1
                }

            init {
                textInput.registerToEvent(key) {
                    var shouldUpdate = false
                    if (hideIfNoText) {
                        if (isTextBoxEmpty) {
                            if (height != content.height) {
                                height = content.height
                                shouldUpdate = true
                            }
                        } else {
                            if (height == content.height) {
                                height = content.height + ySpacing + textBoxHeight
                                shouldUpdate = true
                            }
                        }
                    }
                    val searchWidth = searchWidth
                    if (searchWidth > width) {
                        width = searchWidth
                        shouldUpdate = true
                    } else {
                        if (width > content.width) {
                            width = maxOf(content.width, searchWidth)
                            shouldUpdate = true
                        }
                    }
                    if (shouldUpdate) {
                        onUpdateSize(this)
                    }
                }
            }

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                if (shouldRenderTopElseBottom && !(hideIfNoText && isTextBoxEmpty)) {
                    RenderableUtils.renderString(searchPrefix + textInput.editText(), scale, color)
                    DrawContextUtils.translate(0f, (ySpacing + textBoxHeight).toFloat(), 0f)
                }
                if (isHovered(mouseOffsetX, mouseOffsetY) && condition() && shouldAllowLink(true, bypassChecks)) {
                    onHover(textInput)
                    textInput.makeActive()
                    textInput.handle()
                    val yOff: Int = if (shouldRenderTopElseBottom) 0 else content.height + ySpacing
                    if (isBoxHovered(mouseOffsetX, width, mouseOffsetY + yOff, textBoxHeight) && RIGHT_MOUSE.isKeyClicked()) {
                        textInput.clear()
                    }
                } else {
                    textInput.disable()
                }
                if (hideIfNoText && isTextBoxEmpty) {
                    content.render(mouseOffsetX, mouseOffsetY)
                } else if (!shouldRenderTopElseBottom) {
                    content.render(mouseOffsetX, mouseOffsetY)
                    DrawContextUtils.translate(0f, (ySpacing).toFloat(), 0f)
                    if (!(hideIfNoText && textInput.textBox.isEmpty())) {
                        RenderableUtils.renderString(searchPrefix + textInput.editText(), scale, color)
                    }
                    DrawContextUtils.translate(0f, -(ySpacing).toFloat(), 0f)
                } else {
                    content.render(mouseOffsetX, mouseOffsetY + textBoxHeight + ySpacing)
                    DrawContextUtils.translate(0f, -(ySpacing + textBoxHeight).toFloat(), 0f)
                }
            }

        }

        fun progressBar(
            percent: Double,
            startColor: Color = Color(255, 0, 0),
            endColor: Color = Color(0, 255, 0),
            useChroma: Boolean = false,
            texture: SkillProgressBarConfig.TexturedBar.UsedTexture? = null,
            width: Int = 182,
            height: Int = 5,
            horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlign: VerticalAlignment = VerticalAlignment.TOP,
        ) = object : Renderable {
            override val width = width
            override val height = height
            override val horizontalAlign = horizontalAlign
            override val verticalAlign = verticalAlign

            private val progress = if (texture == null) {
                (1.0 + percent * (width - 2.0)).toInt()
            } else {
                percent.toInt()
            }

            private val color = if (texture == null) {
                ColorUtils.blendRGB(startColor, endColor, percent)
            } else {
                startColor
            }

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                if (texture == null) {
                    GuiRenderUtils.drawRect(0, 0, width, height, 0xFF43464B.toInt())

                    //#if MC < 1.21
                    if (useChroma) ChromaShaderManager.begin(ChromaType.STANDARD)
                    //#endif

                    val factor = 0.2
                    val bgColor = if (useChroma) Color.GRAY.darker() else color
                    GuiRenderUtils.drawRect(1, 1, width - 1, height - 1, bgColor.darker(factor).rgb)
                    //#if MC < 1.21
                    GuiRenderUtils.drawRect(1, 1, progress, height - 1, color.rgb)
                    //#else
                    //$$ if (useChroma) {
                    //$$     DrawContextUtils.drawContext.fill(SkyHanniRenderLayers.getChromaStandard(), 1, 1, progress, height - 1, color.rgb)
                    //$$ } else {
                    //$$     GuiRenderUtils.drawRect(1, 1, progress, height - 1, color.rgb)
                    //$$ }
                    //#endif

                    //#if MC < 1.21
                    if (useChroma) ChromaShaderManager.end()
                    //#endif
                } else {
                    val scale = 0.00390625f

                    val (uMin, vMin) = if (texture == SkillProgressBarConfig.TexturedBar.UsedTexture.MATCH_PACK)
                        Pair(0f, 64f * scale) else Pair(0f, 0f)
                    val (uMax, vMax) = Pair(uMin + (width * scale), vMin + (height * scale))

                    //#if MC < 1.21
                    GuiRenderUtils.drawTexturedRect(
                        mouseOffsetX, mouseOffsetY, width, height, uMin, uMax, vMin, vMax, createResourceLocation(texture.path),
                        alpha = 1f, filter = GL11.GL_NEAREST
                    )
                    //#else
                    //$$ if (texture == SkillProgressBarConfig.TexturedBar.UsedTexture.MATCH_PACK) {
                    //$$     DrawContextUtils.drawContext.drawGuiTexture(RenderCompat.getMinecraftGuiTextured(), createResourceLocation("hud/experience_bar_background"),
                    //$$         mouseOffsetX, mouseOffsetY, width, height)
                    //$$ } else {
                    //$$     DrawContextUtils.drawContext.drawTexture(RenderCompat.getMinecraftGuiTextured(), createResourceLocation(texture.path),
                    //$$         mouseOffsetX, mouseOffsetY, 0f, 0f, width, height, 182, 5, 256, 256, -1)
                    //$$ }
                    //#endif

                    if (useChroma) {
                        GlStateManager.color(1f, 1f, 1f, 1f)
                        //#if MC < 1.21
                        ChromaShaderManager.begin(ChromaType.TEXTURED)
                        GuiRenderUtils.drawTexturedRect(
                            mouseOffsetX, mouseOffsetY, progress, height, uMin, uMin + (progress * scale),
                            vMin + (height * scale), vMin + (2 * height * scale), createResourceLocation(texture.path),
                            alpha = 1f, filter = GL11.GL_NEAREST
                        )
                        //#else
                        //$$ if (texture == SkillProgressBarConfig.TexturedBar.UsedTexture.MATCH_PACK) {
                        //$$     DrawContextUtils.drawContext.drawGuiTexture(SkyHanniRenderLayers.getChromaTextured(), createResourceLocation("hud/experience_bar_progress"),
                        //$$         width, height, 0, 0, mouseOffsetX, mouseOffsetY, progress, height)
                        //$$ } else {
                        //$$     DrawContextUtils.drawContext.drawTexture(SkyHanniRenderLayers.getChromaTextured(), createResourceLocation(texture.path),
                        //$$         mouseOffsetX, mouseOffsetY, 0f, 5f, progress, height, progress, 5, 256, 256, -1)
                        //$$ }
                        //#endif
                    } else {
                        GlStateManager.color(color.red / 255f, color.green / 255f, color.blue / 255f, 1f)
                        //#if MC < 1.21
                        GuiRenderUtils.drawTexturedRect(
                            mouseOffsetX, mouseOffsetY, progress, height, uMin, uMin + (progress * scale),
                            vMin + (height * scale), vMin + (2 * height * scale), createResourceLocation(texture.path),
                            alpha = 1f, filter = GL11.GL_NEAREST
                        )
                        //#else
                        //$$ if (texture == SkillProgressBarConfig.TexturedBar.UsedTexture.MATCH_PACK) {
                        //$$     DrawContextUtils.drawContext.drawGuiTexture(RenderCompat.getMinecraftGuiTextured(), createResourceLocation("hud/experience_bar_progress"),
                        //$$         width, height, 0, 0, mouseOffsetX, mouseOffsetY, progress, height)
                        //$$ } else {
                        //$$     DrawContextUtils.drawContext.drawTexture(RenderCompat.getMinecraftGuiTextured(), createResourceLocation(texture.path),
                        //$$         mouseOffsetX, mouseOffsetY, 0f, 5f, progress, height, progress, 5, 256, 256, -1)
                        //$$ }
                        //#endif
                    }

                    //#if MC < 1.21
                    if (useChroma) ChromaShaderManager.end()
                    //#endif
                }
            }
        }

        fun Renderable.renderBounds(color: Color = LorenzColor.GREEN.toColor().addAlpha(100)) = object : Renderable {
            override val width = this@renderBounds.width
            override val height = this@renderBounds.height
            override val horizontalAlign = this@renderBounds.horizontalAlign
            override val verticalAlign = this@renderBounds.verticalAlign

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                GuiRenderUtils.drawRect(0, 0, width, height, color.rgb)
                this@renderBounds.render(mouseOffsetX, mouseOffsetY)
            }

        }

        fun rectButton(
            content: Renderable,
            activeColor: Color,
            inActiveColor: Color = activeColor.darker(0.4),
            hoveredColor: (Color) -> Color = { it.darker(0.5) },
            onClick: (Boolean) -> Unit,
            onHover: (Boolean) -> Unit = {},
            button: Int = KeyboardManager.LEFT_MOUSE,
            bypassChecks: Boolean = false,
            condition: (Boolean) -> Boolean = { true },
            startState: Boolean = false,
            padding: Int = 2,
            radius: Int = 10,
            smoothness: Int = 2,
            horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlign: VerticalAlignment = VerticalAlignment.TOP,
        ) = object : Renderable {

            var state = startState

            val color get() = if (state) activeColor else inActiveColor

            override val width = content.width + padding * 2
            override val height = content.height + padding * 2
            override val horizontalAlign = horizontalAlign
            override val verticalAlign = verticalAlign

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                val realColor: Color
                if (isHovered(mouseOffsetX, mouseOffsetY) && condition(state) && shouldAllowLink(true, bypassChecks)) {
                    if (button.isKeyClicked()) {
                        state = !state
                        onClick(state)
                    }
                    onHover(state)
                    realColor = hoveredColor(color)
                } else {
                    realColor = color
                }
                ShaderRenderUtils.drawRoundRect(0, 0, width, height, realColor.rgb, radius, smoothness.toFloat())
                DrawContextUtils.translate(padding.toFloat(), padding.toFloat(), 0f)
                content.render(mouseOffsetX + padding, mouseOffsetY + padding)
                DrawContextUtils.translate(-padding.toFloat(), -padding.toFloat(), 0f)
            }
        }

        fun darkRectButton(
            content: Renderable,
            onClick: (Boolean) -> Unit,
            onHover: (Boolean) -> Unit = {},
            button: Int = KeyboardManager.LEFT_MOUSE,
            bypassChecks: Boolean = false,
            condition: (Boolean) -> Boolean = { true },
            startState: Boolean = false,
            padding: Int = 2,
            horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlign: VerticalAlignment = VerticalAlignment.TOP,
        ) = object : Renderable {

            var state = startState

            override val width = content.width + padding * 2
            override val height = content.height + padding * 2
            override val horizontalAlign = horizontalAlign
            override val verticalAlign = verticalAlign

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                if (isHovered(mouseOffsetX, mouseOffsetY) && condition(state) && shouldAllowLink(true, bypassChecks)) {
                    if (button.isKeyClicked()) {
                        state = !state
                        onClick(state)
                    }
                    onHover(state)
                    if (state) {
                        GuiRenderUtils.drawFloatingRectLight(0, 0, width, height, true)
                    } else {
                        GuiRenderUtils.drawFloatingRectDark(0, 0, width, height, true)
                    }
                } else {
                    if (state) {
                        GuiRenderUtils.drawFloatingRectLight(0, 0, width, height, false)
                    } else {
                        GuiRenderUtils.drawFloatingRectDark(0, 0, width, height, false)
                    }
                }
                DrawContextUtils.translate(padding.toFloat(), padding.toFloat(), 0f)
                content.render(mouseOffsetX + padding, mouseOffsetY + padding)
                DrawContextUtils.translate(-padding.toFloat(), -padding.toFloat(), 0f)
            }
        }

        fun fixedSizeLine(
            content: Renderable,
            width: Int,
            horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlign: VerticalAlignment = VerticalAlignment.TOP,
        ) = object : Renderable {
            val render = content

            override val width = width
            override val height = render.height
            override val horizontalAlign = horizontalAlign
            override val verticalAlign = verticalAlign
            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                render.renderXAligned(mouseOffsetX, mouseOffsetY, width)
            }
        }

        fun fixedSizeLine(
            content: List<Renderable>,
            width: Int,
            horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlign: VerticalAlignment = VerticalAlignment.TOP,
        ) = object : Renderable {
            val render = content

            override val width = width
            override val height = render.maxOfOrNull { it.height } ?: 0
            override val horizontalAlign = horizontalAlign
            override val verticalAlign = verticalAlign

            val emptySpace = width - render.sumOf { it.width }
            val spacing = emptySpace / render.size

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                var xOffset = mouseOffsetX
                render.forEach {
                    val x = it.width + spacing
                    it.renderXYAligned(xOffset, mouseOffsetY, x, height)
                    xOffset += x
                    DrawContextUtils.translate(x.toFloat(), 0f, 0f)
                }
                DrawContextUtils.translate(-(xOffset - mouseOffsetX).toFloat(), 0f, 0f)
            }
        }

        fun fixedSizeColumn(
            content: Renderable,
            height: Int,
            horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlign: VerticalAlignment = VerticalAlignment.TOP,
        ) = object : Renderable {
            val render = content

            override val width = render.width
            override val height = height
            override val horizontalAlign = horizontalAlign
            override val verticalAlign = verticalAlign
            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                render.renderYAligned(mouseOffsetX, mouseOffsetY, height)
            }
        }

        fun scrollList(
            list: List<Renderable>,
            height: Int,
            scrollValue: ScrollValue = ScrollValue(),
            velocity: Double = 2.0,
            button: Int? = null,
            bypassChecks: Boolean = false,
            horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlign: VerticalAlignment = VerticalAlignment.TOP,
            showScrollableTipsInList: Boolean = false,
        ) = object : Renderable {
            private val scrollUpTip = text("§7§oMore items above (scroll)")
            private val scrollDownTip = text("§7§oMore items below (scroll)")

            override val width = maxOf(list.maxOfOrNull { it.width } ?: 0, scrollDownTip.width, scrollUpTip.width)
            override val height = height
            override val horizontalAlign = horizontalAlign
            override val verticalAlign = verticalAlign

            private val virtualHeight = list.sumOf { it.height }

            private val scroll = ScrollInput.Companion.Vertical(
                scrollValue,
                0,
                virtualHeight - height + if (showScrollableTipsInList && virtualHeight > height) scrollUpTip.height else 0,
                velocity,
                button,
            )

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                scroll.update(
                    isHovered(mouseOffsetX, mouseOffsetY) && shouldAllowLink(true, bypassChecks),
                )

                scrollListRender(
                    mouseOffsetX,
                    mouseOffsetY,
                    height,
                    width,
                    list,
                    scroll,
                    showScrollableTipsInList,
                    scrollUpTip,
                    scrollDownTip,
                )
            }
        }

        fun searchableScrollList(
            content: Map<Renderable, String?>,
            height: Int,
            scrollValue: ScrollValue = ScrollValue(),
            velocity: Double = 2.0,
            button: Int? = null,
            textInput: TextInput,
            key: Int,
            bypassChecks: Boolean = false,
            showScrollableTipsInList: Boolean = false,
            horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlign: VerticalAlignment = VerticalAlignment.TOP,
        ) = object : Renderable {
            private var list: Set<Renderable> = filterList(content, textInput.textBox)

            private val scrollUpTip = text("§7§oMore items above (scroll)")
            private val scrollDownTip = text("§7§oMore items below (scroll)")

            override val width = maxOf(list.maxOfOrNull { it.width } ?: 0, scrollUpTip.width, scrollDownTip.width)
            override val height = height
            override val horizontalAlign = horizontalAlign
            override val verticalAlign = verticalAlign

            private val virtualHeight get() = list.sumOf { it.height }
            private var scroll = createScroll()

            init {
                textInput.registerToEvent(key) {
                    // null = ignored, never filtered
                    list = filterList(content, textInput.textBox)
                    scroll = createScroll()
                }
            }

            private fun createScroll() = ScrollInput.Companion.Vertical(
                scrollValue,
                0,
                virtualHeight - height + if (showScrollableTipsInList && virtualHeight > height) scrollUpTip.height else 0,
                velocity,
                button,
            )

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                scroll.update(
                    isHovered(mouseOffsetX, mouseOffsetY) && shouldAllowLink(true, bypassChecks),
                )

                scrollListRender(
                    mouseOffsetX,
                    mouseOffsetY,
                    height,
                    width,
                    list,
                    scroll,
                    showScrollableTipsInList,
                    scrollUpTip,
                    scrollDownTip,
                )
            }
        }

        private fun scrollListRender(
            mouseOffsetX: Int,
            mouseOffsetY: Int,
            height: Int,
            width: Int,
            list: Collection<Renderable>,
            scroll: ScrollInput.Companion.Vertical,
            showScrollableTipsInList: Boolean,
            scrollUpTip: Renderable,
            scrollDownTip: Renderable,
        ) {
            val end = scroll.asInt() + height + 1

            var renderY = 0
            var virtualY = 0
            var found = false

            var negativeSpace1 = 0
            var negativeSpace2 = 0

            // If showScrollableTipsInList is true, and we are scrolled 'down', display a tip indicating
            // there are more items above
            if (showScrollableTipsInList && !scroll.atMinimum()) {
                scrollUpTip.renderXAligned(mouseOffsetX, mouseOffsetY, width)
                DrawContextUtils.translate(0f, scrollUpTip.height.toFloat(), 0f)
                renderY += scrollUpTip.height
                negativeSpace1 -= scrollUpTip.height
            }

            val atScrollEnd = scroll.atMaximum()
            if (!atScrollEnd) {
                negativeSpace2 -= scrollDownTip.height
            }

            val window = scroll.asInt()..(end + negativeSpace1 + negativeSpace2)

            for (renderable in list) {
                if ((virtualY..virtualY + renderable.height) in window) {
                    renderable.renderXAligned(mouseOffsetX, mouseOffsetY + renderY, width)
                    DrawContextUtils.translate(0f, renderable.height.toFloat(), 0f)
                    renderY += renderable.height
                    found = true
                } else if (found) {
                    if (renderY + renderable.height <= height + negativeSpace2) {
                        renderable.renderXAligned(mouseOffsetX, mouseOffsetY + renderY, width)
                        DrawContextUtils.translate(0f, renderable.height.toFloat(), 0f)
                        renderY += renderable.height
                    }
                    break
                }
                virtualY += renderable.height
            }

            // If showScrollableTipsInList is true, and we are scrolled 'up', display a tip indicating
            // there are more items below
            if (showScrollableTipsInList && !atScrollEnd) {
                scrollDownTip.renderXAligned(mouseOffsetX, mouseOffsetY + height - scrollDownTip.height, width)
            }

            DrawContextUtils.translate(0f, -renderY.toFloat(), 0f)
        }

        fun filterList(content: Map<Renderable, String?>, textBox: String) =
            filterListBase(content, textBox, text("§cNo search results!"))

        fun filterListMap(content: Map<List<Renderable>, String?>, textBox: String) =
            filterListBase(content, textBox, listOf(text("§cNo search results!")))

        private fun <T> filterListBase(content: Map<T, String?>, textBox: String, empty: T): Set<T> {
            val map = content.filter { it.value?.contains(textBox, ignoreCase = true) != false }
            val set = map.keys.toMutableSet()
            if (map.filter { it.value != null }.isEmpty()) {
                if (textBox.isNotEmpty()) {
                    set.add(empty)
                }
            }
            return set
        }

        fun searchableScrollable(
            table: Map<List<Renderable>, String>,
            key: Int,
            lines: Int,
            velocity: Double,
            textInput: TextInput,
            scrollValue: ScrollValue,
            showScrollableTipsInList: Boolean = true,
            asTable: Boolean = true,
        ): Renderable? {
            if (table.isEmpty()) return null
            return if (asTable) {
                val height = RenderableUtils.calculateTableY(table.keys, 0).maxOf { it.value }
                searchableScrollTable(
                    table,
                    key = key,
                    height = lines * height,
                    textInput = textInput,
                    velocity = velocity,
                    scrollValue = scrollValue,
                    showScrollableTipsInList = showScrollableTipsInList,
                )
            } else {
                @Suppress("USELESS_CAST")
                val content = table.mapKeys { horizontal(it.key) as Renderable }
                val height = content.maxOf { it.key.height }
                searchableScrollList(
                    content,
                    key = key,
                    height = lines * height,
                    textInput = textInput,
                    velocity = velocity,
                    scrollValue = scrollValue,
                    showScrollableTipsInList = showScrollableTipsInList,
                )
            }
        }

        fun drawInsideRoundedRect(
            input: Renderable,
            color: Color,
            padding: Int = 2,
            radius: Int = 10,
            smoothness: Int = 2,
            horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlign: VerticalAlignment = VerticalAlignment.TOP,
        ) = object : Renderable {
            override val width = input.width + padding * 2
            override val height = input.height + padding * 2
            override val horizontalAlign = horizontalAlign
            override val verticalAlign = verticalAlign

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                ShaderRenderUtils.drawRoundRect(0, 0, width, height, color.rgb, radius, smoothness.toFloat())
                DrawContextUtils.translate(padding.toFloat(), padding.toFloat(), 0f)
                input.render(mouseOffsetX + padding, mouseOffsetY + padding)
                DrawContextUtils.translate(-padding.toFloat(), -padding.toFloat(), 0f)
            }
        }

        fun drawInsideDarkRect(
            input: Renderable,
            padding: Int = 2,
            horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlign: VerticalAlignment = VerticalAlignment.TOP,
        ) = object : Renderable {
            override val width = input.width + padding * 2
            override val height = input.height + padding * 2
            override val horizontalAlign = horizontalAlign
            override val verticalAlign = verticalAlign

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                GuiRenderUtils.drawFloatingRectDark(0, 0, width, height)
                DrawContextUtils.translate(padding.toFloat(), padding.toFloat(), 0f)
                input.render(mouseOffsetX + padding, mouseOffsetY + padding)
                DrawContextUtils.translate(-padding.toFloat(), -padding.toFloat(), 0f)
            }
        }

        fun drawInsideRoundedRectOutline(
            input: Renderable,
            padding: Int = 2,
            radius: Int = 10,
            topOutlineColor: Int,
            bottomOutlineColor: Int,
            borderOutlineThickness: Int,
            blur: Float = 0.7f,
            horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlign: VerticalAlignment = VerticalAlignment.TOP,
        ) = object : Renderable {
            override val width = input.width + padding * 2
            override val height = input.height + padding * 2
            override val horizontalAlign = horizontalAlign
            override val verticalAlign = verticalAlign

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                DrawContextUtils.translate(padding.toFloat(), padding.toFloat(), 0f)
                input.render(mouseOffsetX + padding, mouseOffsetY + padding)
                DrawContextUtils.translate(-padding.toFloat(), -padding.toFloat(), 0f)

                ShaderRenderUtils.drawRoundRectOutline(
                    0,
                    0,
                    width,
                    height,
                    topOutlineColor,
                    bottomOutlineColor,
                    borderOutlineThickness,
                    radius,
                    blur,
                )
            }
        }

        fun drawInsideImage(
            input: Renderable,
            texture: ResourceLocation,
            alpha: Int = 255,
            padding: Int = 2,
            horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlign: VerticalAlignment = VerticalAlignment.TOP,
            radius: Int = 0,
            smoothness: Float = 0f,
        ) = object : Renderable {
            override val width = input.width + padding * 2
            override val height = input.height + padding * 2
            override val horizontalAlign = horizontalAlign
            override val verticalAlign = verticalAlign

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                ShaderRenderUtils.drawRoundTexturedRect(
                    0,
                    0,
                    width,
                    height,
                    GL11.GL_NEAREST,
                    radius,
                    smoothness,
                    texture = texture,
                    alpha = alpha / 255f,
                )

                DrawContextUtils.translate(padding.toFloat(), padding.toFloat(), 0f)
                input.render(mouseOffsetX + padding, mouseOffsetY + padding)
                DrawContextUtils.translate(-padding.toFloat(), -padding.toFloat(), 0f)
            }
        }

        fun drawInsideFixedSizedImage(
            input: Renderable,
            texture: ResourceLocation,
            width: Int = input.width,
            height: Int = input.height,
            alpha: Int = 255,
            padding: Int = 2,
            uMin: Float = 0f,
            uMax: Float = 1f,
            vMin: Float = 0f,
            vMax: Float = 1f,
            horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlign: VerticalAlignment = VerticalAlignment.TOP,
        ) = object : Renderable {
            override val width = width
            override val height = height
            override val horizontalAlign = horizontalAlign
            override val verticalAlign = verticalAlign

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                GuiRenderUtils.drawTexturedRect(0, 0, width, height, uMin, uMax, vMin, vMax, texture, alpha / 255f)

                DrawContextUtils.translate(padding.toFloat(), padding.toFloat(), 0f)
                input.render(mouseOffsetX + padding, mouseOffsetY + padding)
                DrawContextUtils.translate(-padding.toFloat(), -padding.toFloat(), 0f)
            }
        }

        fun drawInsideRoundedRectWithOutline(
            input: Renderable,
            color: Color,
            padding: Int = 2,
            radius: Int = 10,
            smoothness: Int = 2,
            topOutlineColor: Int,
            bottomOutlineColor: Int,
            borderOutlineThickness: Int,
            blur: Float = 0.7f,
            horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlign: VerticalAlignment = VerticalAlignment.TOP,
        ) = object : Renderable {
            override val width = input.width + padding * 2
            override val height = input.height + padding * 2
            override val horizontalAlign = horizontalAlign
            override val verticalAlign = verticalAlign

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                ShaderRenderUtils.drawRoundRect(0, 0, width, height, color.rgb, radius, smoothness.toFloat())
                ShaderRenderUtils.drawRoundRectOutline(
                    0,
                    0,
                    width,
                    height,
                    topOutlineColor,
                    bottomOutlineColor,
                    borderOutlineThickness,
                    radius,
                    blur,
                )

                DrawContextUtils.translate(padding.toFloat(), padding.toFloat(), 0f)
                input.render(mouseOffsetX + padding, mouseOffsetY + padding)
                DrawContextUtils.translate(-padding.toFloat(), -padding.toFloat(), 0f)
            }
        }

        fun fakePlayer(
            player: EntityPlayer,
            followMouse: Boolean = false,
            eyesX: Float = 0f,
            eyesY: Float = 0f,
            width: Int = 50,
            height: Int = 100,
            entityScale: Int = 30,
            padding: Int = 5,
            color: Color? = null,
            colorCondition: () -> Boolean = { true },
        ) = object : Renderable {
            override val width = width + 2 * padding
            override val height = height + 2 * padding
            override val horizontalAlign = HorizontalAlignment.LEFT
            override val verticalAlign = VerticalAlignment.TOP
            val playerHeight = entityScale * 2
            val playerX = width / 2 + padding
            val playerY = height / 2 + playerHeight / 2 + padding

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                GlStateManager.color(1f, 1f, 1f, 1f)
                if (color != null) RenderLivingEntityHelper.setEntityColor(player, color, colorCondition)
                val mouse = currentRenderPassMousePosition ?: return
                val (mouseXRelativeToPlayer, mouseYRelativeToPlayer) = if (followMouse) {
                    val newOffsetX = (mouseOffsetX + playerX - mouse.first).toFloat()
                    val newOffsetY = (mouseOffsetY + playerY - mouse.second - 1.62 * entityScale).toFloat()
                    newOffsetX to newOffsetY
                } else eyesX to eyesY
                DrawContextUtils.translate(0f, 0f, 100f)
                //#if MC < 1.21
                drawEntityOnScreen(
                    playerX,
                    playerY,
                    entityScale,
                    mouseXRelativeToPlayer,
                    mouseYRelativeToPlayer,
                    player,
                )
                //#else
                //$$ DrawContextUtils.translate(-35f, -125f, 0f)
                //$$ drawEntity(
                //$$     DrawContextUtils.drawContext,
                //$$     playerX,
                //$$     playerY,
                //$$     playerX + width,
                //$$     playerY + height,
                //$$     entityScale,
                //$$     0.0625f,
                //$$     -mouseXRelativeToPlayer + if (followMouse) 70f else 0f,
                //$$     -mouseYRelativeToPlayer + if (followMouse) 195f else 0f,
                //$$     player
                //$$ )
                //$$ DrawContextUtils.translate(35f, 125f, 0f)
                //#endif
                DrawContextUtils.translate(0f, 0f, -100f)
            }
        }
    }
}
