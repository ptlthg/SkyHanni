package at.hannibal2.skyhanni.features.misc.discordrpc

// This entire file was taken from SkyblockAddons code, ported to SkyHanni

import at.hannibal2.skyhanni.SkyHanniMod.coroutineScope
import at.hannibal2.skyhanni.SkyHanniMod.feature
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.features.misc.DiscordRPCConfig.LineEntry
import at.hannibal2.skyhanni.config.features.misc.DiscordRPCConfig.PriorityEntry
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.data.jsonobjects.repo.StackingEnchantData
import at.hannibal2.skyhanni.data.jsonobjects.repo.StackingEnchantsJson
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.events.RepositoryReloadEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.minecraft.ClientDisconnectEvent
import at.hannibal2.skyhanni.events.minecraft.KeyPressEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ConditionalUtils
import at.hannibal2.skyhanni.utils.ConfigUtils
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import com.google.gson.JsonObject
import com.jagrosh.discordipc.IPCClient
import com.jagrosh.discordipc.IPCListener
import com.jagrosh.discordipc.entities.RichPresence
import com.jagrosh.discordipc.entities.RichPresenceButton
import com.jagrosh.discordipc.entities.pipe.PipeStatus
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object DiscordRPCManager : IPCListener {

    private const val APPLICATION_ID = 1093298182735282176L

    val config get() = feature.gui.discordRPC

    private var client: IPCClient? = null
    private var startTimestamp: Long = 0
    private var started = false
    private var nextUpdate: SimpleTimeMark = SimpleTimeMark.farPast()

    var stackingEnchants: Map<String, StackingEnchantData> = emptyMap()

    private var debugError = false
    private var debugStatusMessage = "nothing"

    fun start(fromCommand: Boolean = false) {
        coroutineScope.launch {
            try {
                if (isConnected()) return@launch

                updateDebugStatus("Starting...")
                startTimestamp = System.currentTimeMillis()
                client = IPCClient(APPLICATION_ID)
                client?.setup(fromCommand)
            } catch (e: Throwable) {
                updateDebugStatus("Unexpected error: ${e.message}", error = true)
                ErrorManager.logErrorWithData(e, "Discord RPC has thrown an unexpected error while trying to start")

            }
        }
    }

    private fun stop() {
        coroutineScope.launch {
            if (isConnected()) {
                updateDebugStatus("Stopped")
                client?.close()
                started = false
            }
        }
    }

    private fun IPCClient.setup(fromCommand: Boolean) {
        setListener(DiscordRPCManager)

        try {
            connect()
            if (!fromCommand) return

            // confirm that /shrpcstart worked
            ChatUtils.chat("Successfully started Rich Presence!", prefixColor = "§a")
            updateDebugStatus("Successfully started")
            status
        } catch (e: Exception) {
            updateDebugStatus("Failed to connect: ${e.message} (discord not started yet?)", error = true)
            ChatUtils.clickableChat(
                "Discord Rich Presence was unable to start! " +
                    "This usually happens when you join SkyBlock when Discord is not started. " +
                    "Please run /shrpcstart to retry once you have launched Discord.",
                onClick = { startCommand() },
                "§eClick to run /shrpcstart!",
            )
        }
    }

    private fun isConnected() = client?.status == PipeStatus.CONNECTED

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        ConditionalUtils.onToggle(config.firstLine, config.secondLine, config.customText) {
            if (isConnected()) {
                updatePresence()
            }
        }
        config.enabled.whenChanged { _, new ->
            if (!new) {
                stop()
            }
        }
    }

    @HandleEvent
    fun onRepoReload(event: RepositoryReloadEvent) {
        stackingEnchants = event.getConstant<StackingEnchantsJson>("StackingEnchants").enchants
    }

    private fun updatePresence() {
        val location = DiscordStatus.LOCATION.getDisplayString()
        val discordIconKey = DiscordLocationKey.getDiscordIconKey(location)
        client?.sendRichPresence(
            RichPresence.Builder().apply {
                setDetails(getStatusByConfigId(config.firstLine.get()).getDisplayString())
                setState(getStatusByConfigId(config.secondLine.get()).getDisplayString())
                setStartTimestamp(startTimestamp)
                setLargeImage(discordIconKey, location)

                if (config.showEliteBotButton.get()) {
                    addButton(
                        RichPresenceButton(
                            "https://elitebot.dev/@${LorenzUtils.getPlayerName()}/${HypixelData.profileName}",
                            "Open EliteBot",
                        ),
                    )
                }

                if (config.showSkyCryptButton.get()) {
                    addButton(
                        RichPresenceButton(
                            "https://sky.shiiyu.moe/stats/${LorenzUtils.getPlayerName()}/${HypixelData.profileName}",
                            "Open SkyCrypt",
                        ),
                    )
                }
            }.build(),
        )
    }

    override fun onReady(client: IPCClient) {
        updateDebugStatus("Discord RPC Ready.")
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!isConnected()) return
        if (event.repeatSeconds(5)) {
            updatePresence()
        }
    }

    override fun onClose(client: IPCClient, json: JsonObject?) {
        updateDebugStatus("Discord RPC closed.")
        this.client = null
    }

    override fun onDisconnect(client: IPCClient?, t: Throwable?) {
        updateDebugStatus("Discord RPC disconnected.")
        this.client = null
    }

    private fun getStatusByConfigId(entry: LineEntry): DiscordStatus {
        return DiscordStatus.entries.getOrElse(entry.ordinal) { DiscordStatus.NONE }
    }

    private fun isEnabled() = config.enabled.get()

    @HandleEvent
    fun onTick() {
        // The mod has already started the connection process. This variable is my way of running a function when
        // the player joins SkyBlock but only running it again once they join and leave.
        if (started || !isEnabled()) return
        if (LorenzUtils.inSkyBlock) {
            start()
            started = true
        }
    }

    @HandleEvent
    fun onWorldChange() {
        if (nextUpdate.isInFuture()) return
        // wait 5 seconds to check if the new world is skyblock or not before stopping the function
        nextUpdate = DelayedRun.runDelayed(5.seconds) {
            if (!LorenzUtils.inSkyBlock) {
                stop()
            }
        }
    }

    @HandleEvent
    fun onDisconnect(event: ClientDisconnectEvent) {
        stop()
    }

    private fun startCommand() {
        if (!isEnabled()) {
            ChatUtils.userError("Discord Rich Presence is disabled. Enable it in the config §e/sh discord")
            return
        }

        if (isConnected()) {
            ChatUtils.userError("Discord Rich Presence is already active!")
            return
        }

        ChatUtils.chat("Attempting to start Discord Rich Presence...")
        try {
            start(true)
            updateDebugStatus("Successfully started")
        } catch (e: Exception) {
            updateDebugStatus("Unable to start: ${e.message}", error = true)
            ErrorManager.logErrorWithData(
                e,
                "Unable to start Discord Rich Presence! Please report this on Discord and ping @netheriteminer.",
            )
        }
    }

    private fun updateDebugStatus(message: String, error: Boolean = false) {
        debugStatusMessage = message
        debugError = error
    }

    @HandleEvent
    fun onDebug(event: DebugDataCollectEvent) {
        event.title("Discord RCP")

        if (debugError) {
            event.addData {
                add("Error detected!")
                add(debugStatusMessage)
            }
        } else {
            event.addIrrelevant {
                add("no error detected.")
                add("status: $debugStatusMessage")
            }
        }
    }

    // Events that change things in DiscordStatus
    @HandleEvent
    fun onKeyPress(event: KeyPressEvent) {
        if (!isEnabled() || !PriorityEntry.AFK.isSelected()) return // autoPriority 4 is dynamic afk
        beenAfkFor = SimpleTimeMark.now()
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.transform(11, "misc.discordRPC.firstLine") { element ->
            ConfigUtils.migrateIntToEnum(element, LineEntry::class.java)
        }
        event.transform(11, "misc.discordRPC.secondLine") { element ->
            ConfigUtils.migrateIntToEnum(element, LineEntry::class.java)
        }
        event.transform(11, "misc.discordRPC.auto") { element ->
            ConfigUtils.migrateIntToEnum(element, LineEntry::class.java)
        }
        event.transform(11, "misc.discordRPC.autoPriority") { element ->
            ConfigUtils.migrateIntArrayListToEnumArrayList(element, PriorityEntry::class.java)
        }

        event.move(31, "misc.discordRPC", "gui.discordRPC")
    }

    private fun PriorityEntry.isSelected() = config.autoPriority.contains(this)

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.register("shrpcstart") {
            description = "Manually starts the Discord Rich Presence feature"
            category = CommandCategory.USERS_ACTIVE
            callback { startCommand() }
        }
    }
}
