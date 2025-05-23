package at.hannibal2.skyhanni.features.misc.compacttablist

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.SkipTabListLineEvent
import at.hannibal2.skyhanni.events.render.gui.GameOverlayRenderPreEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.KeyboardManager.isActive
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.TabListData
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.filterToMutable
import at.hannibal2.skyhanni.utils.compat.DrawContextUtils
import at.hannibal2.skyhanni.utils.compat.GuiScreenUtils
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.EnumPlayerModelParts
import net.minecraftforge.client.event.RenderGameOverlayEvent

@SkyHanniModule
object TabListRenderer {

    private val config get() = SkyHanniMod.feature.gui.compactTabList

    const val MAX_LINES = 22
    private const val LINE_HEIGHT = 8 + 1
    private const val TAB_PADDING = 3
    private const val COLUMN_SPACING = 6
    private const val TAB_Z_OFFSET = 10f

    @HandleEvent(onlyOnSkyblock = true)
    fun onRenderOverlayPre(event: GameOverlayRenderPreEvent) {
        if (event.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST) return
        if (!config.enabled.get()) return
        event.cancel()

        if (config.toggleTab) return

        drawTabList()
    }

    private var isPressed = false
    private var isTabToggled = false

    @HandleEvent(onlyOnSkyblock = true)
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!config.enabled.get()) return
        if (!config.toggleTab) return
        if (Minecraft.getMinecraft().currentScreen != null) return

        if (Minecraft.getMinecraft().gameSettings.keyBindPlayerList.isActive()) {
            if (!isPressed) {
                isPressed = true
                isTabToggled = !isTabToggled
            }
        } else {
            isPressed = false
        }

        if (isTabToggled) {
            drawTabList()
        }
    }

    private fun drawTabList() {
        val columns = TabListReader.renderColumns

        if (columns.isEmpty()) return

        DrawContextUtils.translate(0f, 0f, TAB_Z_OFFSET)

        var maxLines = 0
        var totalWidth = 0 - COLUMN_SPACING

        for (column in columns) {
            maxLines = maxLines.coerceAtLeast(column.size())
            totalWidth += column.getMaxWidth() + COLUMN_SPACING
        }

        var totalHeight = maxLines * LINE_HEIGHT

        var header = listOf<String>()

        if (!config.hideAdverts) {
            header = TabListData.getHeader().split("\n").toMutableList()
            header.removeIf { line -> !line.contains(TabListReader.hypixelAdvertisingString) }
            totalHeight += header.size * LINE_HEIGHT + TAB_PADDING
        }

        var footer = listOf<String>()

        if (!config.hideAdverts) {
            footer = TabListData.getFooter().split("\n").toMutableList()
            footer.removeIf { line -> !line.contains(TabListReader.hypixelAdvertisingString) }
            totalHeight += footer.size * LINE_HEIGHT + TAB_PADDING
        }

        val minecraft = Minecraft.getMinecraft()
        val screenWidth = GuiScreenUtils.scaledWindowWidth / 2
        val x = screenWidth - totalWidth / 2
        val y = 10

        if (!config.hideTabBackground) {
            GuiRenderUtils.drawRect(
                x - COLUMN_SPACING,
                y - TAB_PADDING,
                screenWidth + totalWidth / 2 + COLUMN_SPACING,
                10 + totalHeight + TAB_PADDING,
                -0x80000000,
            )
        }

        var headerY = y
        if (header.isNotEmpty()) {
            for (line in header) {
                GuiRenderUtils.drawString(
                    line,
                    x + totalWidth / 2f - minecraft.fontRendererObj.getStringWidth(line) / 2f,
                    headerY.toFloat(),
                    0xFFFFFF,
                )
                headerY += 8 + 1
            }
        }

        drawColumns(x, headerY, columns, minecraft)

        if (footer.isNotEmpty()) {
            var footerY = y + totalHeight - footer.size * LINE_HEIGHT + TAB_PADDING / 2 + 1
            for (line in footer) {
                GuiRenderUtils.drawString(
                    line,
                    x + totalWidth / 2f - minecraft.fontRendererObj.getStringWidth(line) / 2f,
                    footerY.toFloat(),
                    -0x1,
                )
                footerY += LINE_HEIGHT
            }
        }
        DrawContextUtils.translate(0f, 0f, -TAB_Z_OFFSET)
    }

    private fun drawColumns(x: Int, headerY: Int, columns: List<RenderColumn>, minecraft: Minecraft) {
        var middleX = x
        var lastTitle: TabLine? = null
        var lastSubTitle: TabLine? = null
        for (originalColumn in columns) {
            var middleY = if (config.hideAdverts) headerY else headerY + TAB_PADDING + 2

            val column = originalColumn.lines.filterToMutable { tabLine ->
                if (tabLine.type == TabStringType.TITLE) {
                    lastSubTitle = null
                    lastTitle = tabLine
                }
                if (tabLine.type == TabStringType.SUB_TITLE) {
                    lastSubTitle = tabLine
                }
                !SkipTabListLineEvent(tabLine, lastSubTitle, lastTitle).post()
            }.let(::RenderColumn)

            GuiRenderUtils.drawRect(
                middleX - TAB_PADDING + 1,
                middleY - TAB_PADDING + 1,
                middleX + column.getMaxWidth() + TAB_PADDING - 2,
                middleY + column.size() * LINE_HEIGHT + TAB_PADDING - 2,
                if (config.hideTabBackground) 0x8F262626.toInt() else 0x20AAAAAA,
            )

            for (tabLine in column.lines) {
                val savedX = middleX

                val hideIcons = config.advancedPlayerList.hidePlayerIcons && !AdvancedPlayerList.ignoreCustomTabList()
                if (tabLine.type == TabStringType.PLAYER && !hideIcons) {
                    val playerInfo = tabLine.getInfo()
                    if (playerInfo != null) {
                        minecraft.textureManager.bindTexture(playerInfo.locationSkin)
                        GlStateManager.color(1f, 1f, 1f, 1f)
                        Gui.drawScaledCustomSizeModalRect(middleX, middleY, 8f, 8f, 8, 8, 8, 8, 64f, 64f)

                        val player = tabLine.getEntity(playerInfo)
                        if (player != null && player.isWearing(EnumPlayerModelParts.HAT)) {
                            Gui.drawScaledCustomSizeModalRect(middleX, middleY, 40f, 8f, 8, 8, 8, 8, 64f, 64f)
                        }
                    }
                    middleX += 8 + 2
                }

                var text = if (AdvancedPlayerList.ignoreCustomTabList()) tabLine.text else tabLine.customName
                if (text.contains("§l")) text = "§r$text"
                if (tabLine.type == TabStringType.TITLE) {
                    GuiRenderUtils.drawString(
                        text,
                        middleX + column.getMaxWidth() / 2f - tabLine.getWidth() / 2f,
                        middleY.toFloat(),
                        0xFFFFFF,
                    )
                } else {
                    GuiRenderUtils.drawString(
                        text,
                        middleX.toFloat(),
                        middleY.toFloat(),
                        0xFFFFFF,
                    )
                }
                middleY += LINE_HEIGHT
                middleX = savedX
            }
            middleX += column.getMaxWidth() + COLUMN_SPACING
        }
    }

    private val fireSalePattern by RepoPattern.pattern(
        "tablist.firesaletitle",
        "§.§lFire Sales: §r§f\\([0-9]+\\)"
    )

    @HandleEvent
    fun onSkipTablistLine(event: SkipTabListLineEvent) {
        if (config.hideFiresales && event.lastSubTitle != null && fireSalePattern.matches(event.lastSubTitle.text)) {
            event.cancel()
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(31, "misc.compactTabList", "gui.compactTabList")
    }
}
