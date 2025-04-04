package at.hannibal2.skyhanni.test

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils

@SkyHanniModule
object SkyBlockIslandTest {

    var testIsland: IslandType? = null

    fun onCommand(args: Array<String>) {
        if (args.isEmpty()) {
            ChatUtils.userError("Usage: /shtestisland <island name>/reset")
            return
        }

        val search = args.joinToString(" ").lowercase()
        if (search == "reset") {
            testIsland?.let {
                ChatUtils.chat("Disabled test island (was ${it.displayName})")
                testIsland = null
                return
            }
            ChatUtils.chat("Test island was not set.")
            return
        }
        val found = find(search)
        if (found == null) {
            ChatUtils.userError("Unknown island type! ($search)")
            return
        }
        testIsland = found
        ChatUtils.chat("Set test island to ${found.displayName}")

    }

    @HandleEvent
    fun onDebug(event: DebugDataCollectEvent) {
        event.title("Island Test")
        testIsland?.let {
            event.addData {
                add("debug active!")
                add("island: '$it'")
            }
        } ?: run {
            event.addIrrelevant("not active.")
        }
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.register("shtestisland") {
            description = "Changes the SkyBlock island SkyHanni thinks you are on"
            category = CommandCategory.DEVELOPER_TEST
            callback { onCommand(it) }
        }
    }

    private fun find(search: String): IslandType? {
        for (type in IslandType.entries) {
            if (type.name.equals(search, ignoreCase = true)) return type
            if (type.displayName.equals(search, ignoreCase = true)) return type
        }

        return null
    }
}
