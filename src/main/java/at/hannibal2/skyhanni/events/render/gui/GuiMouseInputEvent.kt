package at.hannibal2.skyhanni.events.render.gui

import at.hannibal2.skyhanni.api.event.CancellableSkyHanniEvent
import net.minecraft.client.gui.GuiScreen

class GuiMouseInputEvent(val gui: GuiScreen) : CancellableSkyHanniEvent()
