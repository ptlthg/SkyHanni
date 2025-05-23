package at.hannibal2.skyhanni.features.misc

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.enums.OutsideSBFeature
import at.hannibal2.skyhanni.data.jsonobjects.repo.ModGuiSwitcherJson
import at.hannibal2.skyhanni.events.RepositoryReloadEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniTickEvent
import at.hannibal2.skyhanni.events.render.gui.ScreenDrawnEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.ReflectionUtils.makeAccessible
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addString
import at.hannibal2.skyhanni.utils.compat.DrawContextUtils
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.addLine
import net.minecraft.client.Minecraft
import net.minecraftforge.client.ClientCommandHandler

@SkyHanniModule
object QuickModMenuSwitch {

    private val config get() = SkyHanniMod.feature.misc.quickModMenuSwitch
    private var display = emptyList<Renderable>()
    private var latestGuiPath = ""

    private var mods: List<Mod>? = null

    private var currentlyOpeningMod = ""
    private var lastGuiOpen = 0L

    @HandleEvent
    fun onRepoReload(event: RepositoryReloadEvent) {
        val modsJar = event.getConstant<ModGuiSwitcherJson>("ModGuiSwitcher")
        mods = modsJar.mods.filter { mod ->
            mod.value.guiPath.any { runCatching { Class.forName(it) }.isSuccess }
        }.map { (name, mod) ->
            Mod(name, mod.description, mod.command, mod.guiPath)
        }
    }

    @HandleEvent
    fun onTick(event: SkyHanniTickEvent) {
        if (!isEnabled()) return

        if (event.isMod(5)) {
            update()
        }
    }

    class Mod(val name: String, val description: List<String>, val command: String, private val guiPath: List<String>) {

        fun isInGui() = guiPath.any { latestGuiPath.startsWith(it) }
    }

    private fun update() {
        var openGui = Minecraft.getMinecraft().currentScreen?.javaClass?.name ?: "none"
        openGui = handleAbstractGuis(openGui)
        if (latestGuiPath != openGui) {
            latestGuiPath = openGui

            if (SkyHanniMod.feature.dev.debug.modMenuLog) {
                ChatUtils.debug("Open GUI: $latestGuiPath")
            }
        }
        val mods = mods ?: return

        display = if (!shouldShow(mods)) {
            emptyList()
        } else {
            renderDisplay(mods)
        }
    }

    private fun shouldShow(mods: List<Mod>): Boolean {
        if (config.insideEscapeMenu && isEscapeMenu(latestGuiPath)) return true
        if (config.insidePlayerInventory && latestGuiPath == "net.minecraft.client.gui.inventory.GuiInventory") return true

        return mods.any { it.isInGui() }
    }

    private fun isEscapeMenu(path: String) = when (path) {
        "net.minecraft.client.gui.GuiIngameMenu" -> true
        "me.powns.togglesneak.gui.screens.GuiOptionsReplace" -> true

        else -> false
    }

    private fun handleAbstractGuis(openGui: String): String {
        if (openGui == "gg.essential.vigilance.gui.SettingsGui") {
            val clazz = Class.forName("gg.essential.vigilance.gui.SettingsGui")
            val titleBarDelegate = clazz.getDeclaredField("titleBar\$delegate").makeAccessible()
                .get(Minecraft.getMinecraft().currentScreen)
            val titleBar =
                titleBarDelegate.javaClass.declaredFields[0].makeAccessible().get(titleBarDelegate)
            val gui = titleBar.javaClass.getDeclaredField("gui").makeAccessible().get(titleBar)
            val config = gui.javaClass.getDeclaredField("config").makeAccessible().get(gui)

            return config.javaClass.name
        }
        if (openGui == "cc.polyfrost.oneconfig.gui.OneConfigGui") {
            val actualGui = Minecraft.getMinecraft().currentScreen ?: return openGui
            val currentPage = actualGui.javaClass.getDeclaredField("currentPage")
                .makeAccessible()
                .get(actualGui)
            if (currentPage.javaClass.simpleName == "ModConfigPage") {
                val optionPage = currentPage.javaClass.getDeclaredField("page")
                    .makeAccessible()
                    .get(currentPage)
                val mod = optionPage.javaClass.getField("mod")
                    .makeAccessible()
                    .get(optionPage)
                val modName = mod.javaClass.getField("name")
                    .get(mod) as String
                return "cc.polyfrost.oneconfig.gui.OneConfigGui:$modName"
            }
        }

        return openGui
    }

    private fun renderDisplay(mods: List<Mod>) = buildList {
        for (mod in mods) {
            val currentlyOpen = mod.isInGui()
            val nameFormat = if (currentlyOpen) "§c" else ""
            var opening = mod.name == currentlyOpeningMod
            if (currentlyOpen && opening) {
                currentlyOpeningMod = ""
                opening = false
            }
            val nameSuffix = if (opening) " §7(opening...)" else ""
            val renderable = Renderable.link(
                Renderable.string(nameFormat + mod.name),
                bypassChecks = true,
                onLeftClick = { open(mod) },
                condition = { System.currentTimeMillis() > lastGuiOpen + 250 },
            )
            addLine {
                add(renderable)
                addString(nameSuffix)
            }
        }
    }

    private fun open(mod: Mod) {
        lastGuiOpen = System.currentTimeMillis()
        currentlyOpeningMod = mod.name
        update()
        try {
            ClientCommandHandler.instance.executeCommand(MinecraftCompat.localPlayer, "/" + mod.command)
        } catch (e: Exception) {
            ErrorManager.logErrorWithData(e, "Error trying to open the gui for mod " + mod.name)
        }
    }

    @HandleEvent
    fun onScreenDrawn(event: ScreenDrawnEvent) {
        if (!isEnabled()) return

        DrawContextUtils.pushMatrix()
        config.pos.renderRenderables(display, posLabel = "Quick Mod Menu Switch")
        DrawContextUtils.popMatrix()
    }

    private fun isEnabled() = (LorenzUtils.inSkyBlock || OutsideSBFeature.QUICK_MOD_MENU_SWITCH.isSelected()) && config.enabled

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(3, "dev.modMenuLog", "dev.debug.modMenuLog")
    }
}
