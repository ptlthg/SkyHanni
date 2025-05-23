package at.hannibal2.skyhanni.features.misc.update

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.config.core.elements.GuiElementButton
import at.hannibal2.skyhanni.utils.compat.MouseCompat
import io.github.notenoughupdates.moulconfig.common.RenderContext
import io.github.notenoughupdates.moulconfig.gui.GuiOptionEditor
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption
import net.minecraft.util.EnumChatFormatting.GREEN
import net.minecraft.util.EnumChatFormatting.RED

class GuiOptionEditorUpdateCheck(option: ProcessedOption) : GuiOptionEditor(option) {

    val button = GuiElementButton()

    override fun render(context: RenderContext, x: Int, y: Int, width: Int) {
        val fr = context.minecraft.defaultFontRenderer

        context.pushMatrix()
        context.translate(x.toFloat() + 10, y.toFloat(), 1F)
        val adjustedWidth = width - 20
        val nextVersion = UpdateManager.getNextVersion()

        button.text = when (UpdateManager.updateState) {
            UpdateManager.UpdateState.AVAILABLE -> "Download update"
            UpdateManager.UpdateState.QUEUED -> "Downloading..."
            UpdateManager.UpdateState.DOWNLOADED -> "Downloaded"
            UpdateManager.UpdateState.NONE -> if (nextVersion == null) "Check for Updates" else "Up to date"
        }
        button.width = button.getWidth(context)
        button.render(context, getButtonPosition(adjustedWidth), 10)

        if (UpdateManager.updateState == UpdateManager.UpdateState.DOWNLOADED) {
            val updateText = "${GREEN}The update will be installed after your next restart."
            context.drawStringCenteredScaledMaxWidth(
                updateText,
                fr,
                adjustedWidth / 2F,
                40f,
                true,
                x - fr.getStringWidth(updateText) / 2,
                -1
            )
        }

        val widthRemaining = adjustedWidth - button.width - 10

        context.scale(2F, 2F, 1F)
        val currentVersion = SkyHanniMod.VERSION
        val sameVersion = currentVersion.equals(nextVersion, ignoreCase = true)
        context.drawStringCenteredScaledMaxWidth(
            "${if (UpdateManager.updateState == UpdateManager.UpdateState.NONE) GREEN else RED}$currentVersion" +
                if (nextVersion != null && !sameVersion) "➜ $GREEN$nextVersion" else "",
            fr,
            widthRemaining / 4F,
            10F,
            true,
            widthRemaining / 2,
            -1,
        )

        context.popMatrix()
    }

    private fun getButtonPosition(width: Int) = width - button.width
    override fun getHeight(): Int {
        return 55
    }

    override fun mouseInput(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int): Boolean {
        val adjustedWidth = width - 20
        if (MouseCompat.getEventButtonState() &&
            (mouseX - getButtonPosition(adjustedWidth) - x) in (0..button.width) &&
            (mouseY - 10 - y) in (0..button.height)
        ) {
            when (UpdateManager.updateState) {
                UpdateManager.UpdateState.AVAILABLE -> UpdateManager.queueUpdate()
                UpdateManager.UpdateState.QUEUED -> {}
                UpdateManager.UpdateState.DOWNLOADED -> {}
                UpdateManager.UpdateState.NONE -> UpdateManager.checkUpdate()
            }
            return true
        }
        return false
    }

    override fun keyboardInput(): Boolean {
        return false
    }

    override fun fulfillsSearch(word: String): Boolean {
        return super.fulfillsSearch(word) || word in "download" || word in "update"
    }
}
