package at.hannibal2.skyhanni.features.misc.update

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.features.About.UpdateStream
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ApiUtils
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ConditionalUtils.onToggle
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.LorenzLogger
import at.hannibal2.skyhanni.utils.system.ModVersion
import com.google.gson.JsonElement
import io.github.notenoughupdates.moulconfig.observer.Property
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor
import moe.nea.libautoupdate.CurrentVersion
import moe.nea.libautoupdate.PotentialUpdate
import moe.nea.libautoupdate.UpdateContext
import moe.nea.libautoupdate.UpdateTarget
import moe.nea.libautoupdate.UpdateUtils
import java.util.concurrent.CompletableFuture
import javax.net.ssl.HttpsURLConnection

@SkyHanniModule
object UpdateManager {

    private val logger = LorenzLogger("update_manager")
    private var _activePromise: CompletableFuture<*>? = null
    private var activePromise: CompletableFuture<*>?
        get() = _activePromise
        set(value) {
            _activePromise?.cancel(true)
            _activePromise = value
        }

    var updateState: UpdateState = UpdateState.NONE
        private set

    fun getNextVersion(): String? {
        return potentialUpdate?.update?.versionNumber?.asString
    }

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        SkyHanniMod.feature.about.updateStream.onToggle {
            reset()
        }
    }

    private var hasCheckedForUpdate = false

    @HandleEvent
    fun onTick() {
        if (hasCheckedForUpdate) return
        hasCheckedForUpdate = true

        if (config.autoUpdates || config.fullAutoUpdates)
            checkUpdate()
    }

    fun injectConfigProcessor(processor: MoulConfigProcessor<*>) {
        processor.registerConfigEditor(ConfigVersionDisplay::class.java) { option, _ ->
            GuiOptionEditorUpdateCheck(option)
        }
    }

    private val config get() = SkyHanniMod.feature.about

    fun reset() {
        updateState = UpdateState.NONE
        _activePromise = null
        potentialUpdate = null
        logger.log("Reset update state")
    }

    fun checkUpdate(forceDownload: Boolean = false, forcedUpdateStream: UpdateStream = config.updateStream.get()) {
        var updateStream = forcedUpdateStream
        if (updateState != UpdateState.NONE) {
            logger.log("Trying to perform update check while another update is already in progress")
            return
        }
        logger.log("Starting update check")
        val currentStream = config.updateStream.get()
        if (currentStream != UpdateStream.BETA && (updateStream == UpdateStream.BETA || SkyHanniMod.isBetaVersion)) {
            config.updateStream = Property.of(UpdateStream.BETA)
            updateStream = UpdateStream.BETA
        }
        activePromise = context.checkUpdate(updateStream.stream).thenAcceptAsync(
            {
                logger.log("Update check completed")
                if (updateState != UpdateState.NONE) {
                    logger.log("This appears to be the second update check. Ignoring this one")
                    return@thenAcceptAsync
                }
                potentialUpdate = it
                if (it.isUpdateAvailable) {
                    updateState = UpdateState.AVAILABLE
                    if (config.fullAutoUpdates || forceDownload) {
                        ChatUtils.chat("§aSkyHanni found a new update: ${it.update.versionName}, starting to download now.")
                        queueUpdate()
                    } else if (config.autoUpdates) {
                        ChatUtils.chatAndOpenConfig(
                            "§aSkyHanni found a new update: ${it.update.versionName}. " +
                                "Check §b/sh download update §afor more info.",
                            config::autoUpdates,
                        )
                    }
                } else if (forceDownload) {
                    ChatUtils.chat("§aSkyHanni didn't find a new update.")
                }
            },
            DelayedRun.onThread,
        )
    }

    fun queueUpdate() {
        if (updateState != UpdateState.AVAILABLE) {
            logger.log("Trying to enqueue an update while another one is already downloaded or none is present")
        }
        updateState = UpdateState.QUEUED
        activePromise = CompletableFuture.supplyAsync {
            logger.log("Update download started")
            potentialUpdate!!.prepareUpdate()
        }.thenAcceptAsync(
            {
                logger.log("Update download completed, setting exit hook")
                updateState = UpdateState.DOWNLOADED
                potentialUpdate!!.executePreparedUpdate()
                ChatUtils.chat("Download of update complete. ")
                ChatUtils.chat("§aThe update will be installed after your next restart.")
            },
            DelayedRun.onThread,
        )
    }

    private val context = UpdateContext(
        CustomGithubReleaseUpdateSource("hannibal002", "SkyHanni"),
        UpdateTarget.deleteAndSaveInTheSameFolder(UpdateManager::class.java),
        object : CurrentVersion {
            private val debug get() = SkyHanniMod.feature.dev.debug.alwaysOutdated
            override fun display(): String = if (debug) "Force Outdated" else SkyHanniMod.VERSION

            override fun isOlderThan(element: JsonElement?): Boolean {
                if (debug) return true
                val asString = element?.asString ?: return true
                val otherVersion = ModVersion.fromString(asString)
                return SkyHanniMod.modVersion < otherVersion
            }
        },
        SkyHanniMod.MODID,
    )

    init {
        context.cleanup()
        UpdateUtils.patchConnection {
            if (it is HttpsURLConnection) {
                ApiUtils.patchHttpsRequest(it)
            }
        }
    }

    enum class UpdateState {
        AVAILABLE,
        QUEUED,
        DOWNLOADED,
        NONE
    }

    private var potentialUpdate: PotentialUpdate? = null

    fun updateCommand(args: Array<String>) {
        val currentStream = SkyHanniMod.feature.about.updateStream.get()
        val arg = args.firstOrNull() ?: "current"
        val updateStream = when {
            arg.equals("(?i)(?:full|release)s?".toRegex()) -> UpdateStream.RELEASES
            arg.equals("(?i)(?:beta|latest)s?".toRegex()) -> UpdateStream.BETA
            else -> currentStream
        }

        val switchingToBeta = updateStream == UpdateStream.BETA && (currentStream != UpdateStream.BETA || !SkyHanniMod.isBetaVersion)
        if (switchingToBeta) {
            ChatUtils.clickableChat(
                "Are you sure you want to switch to beta? These versions may be less stable.",
                onClick = {
                    val newUpdateStream = SkyHanniMod.feature.about.updateStream
                    newUpdateStream.set(UpdateStream.BETA)
                    checkUpdate(true, newUpdateStream.get())
                },
                "§eClick to confirm!",
                oneTimeClick = true,
            )
        } else {
            checkUpdate(true, updateStream)
        }
    }
}
