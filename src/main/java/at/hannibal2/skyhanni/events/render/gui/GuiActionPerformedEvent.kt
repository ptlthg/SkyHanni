package at.hannibal2.skyhanni.events.render.gui

import at.hannibal2.skyhanni.api.event.SkyHanniEvent
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen

class GuiActionPerformedEvent(val gui: GuiScreen?, val button: GuiButton) : SkyHanniEvent()
