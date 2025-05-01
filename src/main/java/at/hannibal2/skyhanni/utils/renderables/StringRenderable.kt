package at.hannibal2.skyhanni.utils.renderables

import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.RenderUtils.HorizontalAlignment
import at.hannibal2.skyhanni.utils.RenderUtils.VerticalAlignment
import at.hannibal2.skyhanni.utils.compat.DrawContextUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import java.awt.Color

open class RenderableString(
    val text: String,
    val scale: Double = 1.0,
    val color: Color = Color.WHITE,
    override val horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
    override val verticalAlign: VerticalAlignment = VerticalAlignment.CENTER,
) : Renderable {
    override val width by lazy { (Minecraft.getMinecraft().fontRendererObj.getStringWidth(text) * scale).toInt() + 1 }
    override val height = (9 * scale).toInt() + 1

    val inverseScale = 1 / scale

    override fun render(posX: Int, posY: Int) {
        RenderableUtils.renderString(text, scale, color, inverseScale)
    }
}

class WrappedRenderableString(
    text: String,
    width: Int,
    scale: Double = 1.0,
    color: Color = Color.WHITE,
    horizontalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
    verticalAlign: VerticalAlignment = VerticalAlignment.CENTER,
    private val internalAlign: HorizontalAlignment = HorizontalAlignment.LEFT,
) : Renderable, RenderableString(
    text,
    scale,
    color,
    horizontalAlign,
    verticalAlign,
) {
    private val fontRenderer: FontRenderer by lazy { Minecraft.getMinecraft().fontRendererObj }
    val map by lazy {
        fontRenderer.listFormattedStringToWidth(
            text, (width / scale).toInt(),
        ).associateWith { fontRenderer.getStringWidth(it) }
    }

    override val width by lazy { (rawWidth * scale).toInt() + 1 }

    private val rawWidth by lazy {
        if (map.size == 1) map.entries.first().value
        else map.maxOf { it.value }
    }

    override val height by lazy { map.size * ((9 * scale).toInt() + 1) }

    override fun render(posX: Int, posY: Int) {
        DrawContextUtils.translate(1.0, 1.0, 0.0)
        DrawContextUtils.scale(scale.toFloat(), scale.toFloat(), 1.0f)
        map.entries.forEachIndexed { index, (text, size) ->
            GuiRenderUtils.drawString(
                text,
                RenderableUtils.calculateAlignmentXOffset(size, rawWidth, internalAlign).toFloat(),
                index * 10.0f,
                color.rgb,
            )
        }
        DrawContextUtils.scale(inverseScale.toFloat(), inverseScale.toFloat(), 1.0f)
        DrawContextUtils.translate(-1.0, -1.0, 0.0)
    }
}
