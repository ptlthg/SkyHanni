package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.events.ActionBarUpdateEvent
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.OSUtils
import at.hannibal2.skyhanni.utils.StringUtils.stripHypixelMessage
import kotlinx.coroutines.launch
import net.minecraft.util.IChatComponent

@SkyHanniModule
object ActionBarData {
    private var actionBar = ""
    private var debugActionBar: String? = null

    fun getActionBar() = actionBar

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.register("shtestactionbar") {
            description = "Set your clipboard as a fake action bar."
            category = CommandCategory.DEVELOPER_TEST
            callback { debugCommand() }
        }
    }

    private fun debugCommand() {
        SkyHanniMod.coroutineScope.launch {
            val clipboard = OSUtils.readFromClipboard()
            if (debugActionBar == clipboard) {
                debugActionBar = null
                ChatUtils.chat("Disabled action bar test!")
            } else {
                debugActionBar = clipboard
                ChatUtils.chat("Set action bar test to '$clipboard'")
            }
        }
    }

    @HandleEvent
    fun onDebug(event: DebugDataCollectEvent) {
        event.title("Action Bar")
        debugActionBar?.let {
            event.addData {
                add("debug active!")
                add("line: '$it'")
            }
        } ?: run {
            event.addIrrelevant("not active.")
        }
    }

    @HandleEvent
    fun onWorldChange() {
        actionBar = ""
    }

    /**
     * If the action bar is modified return the new one, otherwise return null.
     */
    fun onChatReceive(component: IChatComponent): IChatComponent? {
        val message = debugActionBar ?: component.formattedText.stripHypixelMessage()

        actionBar = message
        val actionBarEvent = ActionBarUpdateEvent(actionBar, component)
        actionBarEvent.post()

        if (component.formattedText != actionBarEvent.chatComponent.formattedText) {
            return actionBarEvent.chatComponent
        }
        return null
    }
}
