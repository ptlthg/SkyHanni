package at.hannibal2.skyhanni.events

import at.hannibal2.skyhanni.api.event.SkyHanniEvent
import net.minecraft.event.HoverEvent
import net.minecraft.util.IChatComponent

/**
 * This event is mainly used for doing things on chat hover and reading the chat component
 * of the hovered chat.
 *
 * To edit the chat component, add to, or use methods in [GuiChatHook][at.hannibal2.skyhanni.mixins.hooks.GuiChatHook].
 *
 * The edited chat component in [GuiChatHook][at.hannibal2.skyhanni.mixins.hooks.GuiChatHook] does not change the actual
 * chat component, but rather makes a new one just before rendering.
 */
class ChatHoverEvent(val component: IChatComponent) : SkyHanniEvent() {
    fun getHoverEvent(): HoverEvent = component.chatStyle.chatHoverEvent!!
}
