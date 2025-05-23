package at.hannibal2.skyhanni.features.chat

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.render.gui.GuiMouseInputEvent
import at.hannibal2.skyhanni.features.misc.visualwords.ModifyVisualWords
import at.hannibal2.skyhanni.mixins.transformers.AccessorMixinGuiNewChat
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ChatUtils.fullComponent
import at.hannibal2.skyhanni.utils.ClipboardUtils
import at.hannibal2.skyhanni.utils.KeyboardManager
import at.hannibal2.skyhanni.utils.ReflectionUtils.getDeclaredFieldOrNull
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.StringUtils.stripHypixelMessage
import at.hannibal2.skyhanni.utils.compat.GuiScreenUtils
import at.hannibal2.skyhanni.utils.compat.MouseCompat
import at.hannibal2.skyhanni.utils.system.PlatformUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ChatLine
import net.minecraft.client.gui.GuiChat
import net.minecraft.util.MathHelper

@SkyHanniModule
object CopyChat {
    private val config get() = SkyHanniMod.feature.chat.copyChat

    @HandleEvent
    fun onMouseInput(event: GuiMouseInputEvent) {
        if (event.gui !is GuiChat) return
        if (!config || !KeyboardManager.isRightMouseClicked()) return
        val chatLine = getChatLine(MouseCompat.getX(), MouseCompat.getY()) ?: return
        val formatted = chatLine.fullComponent.formattedText

        val (clipboard, infoMessage) = when {
            KeyboardManager.isMenuKeyDown() -> formatted.stripHypixelMessage() to "formatted message"

            KeyboardManager.isShiftKeyDown() -> (ModifyVisualWords.modifyText(formatted)?.removeColor() ?: formatted) to "modified message"

            KeyboardManager.isControlKeyDown() -> chatLine.chatComponent.unformattedText.removeColor() to "line"

            else -> formatted.removeColor() to "message"
        }

        ClipboardUtils.copyToClipboard(clipboard)
        ChatUtils.chat("Copied $infoMessage to clipboard!")
    }

    private fun getChatLine(mouseX: Int, mouseY: Int): ChatLine? {
        val mc = Minecraft.getMinecraft() ?: return null
        val chatGui = mc.ingameGUI.chatGUI ?: return null
        val access = chatGui as AccessorMixinGuiNewChat
        val chatScale = chatGui.chatScale
        val scaleFactor = GuiScreenUtils.scaleFactor

        val x = MathHelper.floor_float((mouseX / scaleFactor - 3) / chatScale)
        val y = MathHelper.floor_float((mouseY / scaleFactor - 27 - getOffset()) / chatScale)

        if (x < 0 || y < 0) return null

        val fontHeight = mc.fontRendererObj.FONT_HEIGHT
        val chatLines = access.drawnChatLines_skyhanni
        val maxLines = chatGui.lineCount.coerceAtMost(chatLines.size)
        if (x <= MathHelper.floor_float(chatGui.chatWidth.toFloat() / chatGui.chatScale) && y < fontHeight * maxLines + maxLines) {
            val lineIndex = y / fontHeight + access.scrollPos_skyhanni
            if (lineIndex in 0 until chatLines.size) {
                return chatLines[lineIndex]
            }
        }
        return null
    }

    private val isPatcherLoaded by lazy { PlatformUtils.isModInstalled("patcher") }

    private fun getOffset(): Int {
        if (!isPatcherLoaded) return 0
        return runCatching {
            val patcherConfigClass = Class.forName("club.sk1er.patcher.config.PatcherConfig")
            if (patcherConfigClass.getDeclaredFieldOrNull("chatPosition")?.getBoolean(null) == true) 12 else 0
        }.getOrDefault(0)
    }
}
