package at.hannibal2.skyhanni

//#if TODO
import at.hannibal2.skyhanni.api.enoughupdates.EnoughUpdatesManager
//#endif
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.api.event.SkyHanniEvents
import at.hannibal2.skyhanni.config.ConfigFileType
import at.hannibal2.skyhanni.config.ConfigManager
import at.hannibal2.skyhanni.config.Features
//#if TODO
import at.hannibal2.skyhanni.config.SackData
import at.hannibal2.skyhanni.data.OtherInventoryData
//#endif
import at.hannibal2.skyhanni.data.jsonobjects.local.FriendsJson
//#if TODO
import at.hannibal2.skyhanni.data.jsonobjects.local.JacobContestsJson
//#endif
import at.hannibal2.skyhanni.data.jsonobjects.local.KnownFeaturesJson
import at.hannibal2.skyhanni.data.jsonobjects.local.VisualWordsJson
//#if TODO
import at.hannibal2.skyhanni.data.repo.RepoManager
//#endif
import at.hannibal2.skyhanni.events.utils.PreInitFinishedEvent
import at.hannibal2.skyhanni.skyhannimodule.LoadedModules
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
//#if TODO
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.MinecraftConsoleFilter.Companion.initLogging
//#endif
import at.hannibal2.skyhanni.utils.VersionConstants
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import at.hannibal2.skyhanni.utils.system.ModVersion
import at.hannibal2.skyhanni.utils.system.PlatformUtils
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

// todo 1.21 impl needed
@SkyHanniModule
object SkyHanniMod {

    fun preInit() {
        PlatformUtils.checkIfNeuIsLoaded()

        LoadedModules.modules.forEach { SkyHanniModLoader.loadModule(it) }

        SkyHanniEvents.init(modules)
        //#if TODO
        if (!PlatformUtils.isNeuLoaded()) EnoughUpdatesManager.downloadRepo()
        //#endif

        PreInitFinishedEvent.post()
    }

    fun init() {
        configManager = ConfigManager()
        configManager.firstLoad()
        //#if TODO
        initLogging()
        //#endif
        Runtime.getRuntime().addShutdownHook(
            Thread { configManager.saveConfig(ConfigFileType.FEATURES, "shutdown-hook") },
        )
        //#if TODO
        try {
            RepoManager.initRepo()
        } catch (e: Exception) {
            Exception("Error reading repo data", e).printStackTrace()
        }
        //#endif
    }

    @HandleEvent
    fun onTick() {
        screenToOpen?.let {
            screenTicks++
            if (screenTicks == 5) {
                //#if TODO
                val title = InventoryUtils.openInventoryName()
                //#endif
                MinecraftCompat.localPlayer.closeScreen()
                //#if TODO
                OtherInventoryData.close(title)
                //#endif
                Minecraft.getMinecraft().displayGuiScreen(it)
                screenTicks = 0
                screenToOpen = null
            }
        }
    }

    const val MODID: String = "skyhanni"
    const val VERSION: String = VersionConstants.MOD_VERSION

    val modVersion: ModVersion = ModVersion.fromString(VERSION)

    val isBetaVersion: Boolean
        get() = modVersion.isBeta

    @JvmField
    var feature: Features = Features()
    //#if TODO
    lateinit var sackData: SackData
    //#endif
    lateinit var friendsData: FriendsJson
    lateinit var knownFeaturesData: KnownFeaturesJson
    //#if TODO
    lateinit var jacobContestsData: JacobContestsJson
    //#endif
    lateinit var visualWordsData: VisualWordsJson

    lateinit var configManager: ConfigManager
    val logger: Logger = LogManager.getLogger("SkyHanni")
    fun getLogger(name: String): Logger {
        return LogManager.getLogger("SkyHanni.$name")
    }

    val modules: MutableList<Any> = ArrayList()
    private val globalJob: Job = Job(null)
    val coroutineScope = CoroutineScope(
        CoroutineName("SkyHanni") + SupervisorJob(globalJob),
    )

    fun launchIOCoroutine(block: suspend CoroutineScope.() -> Unit) {
        launchCoroutine {
            withContext(Dispatchers.IO) {
                block()
            }
        }
    }

    var screenToOpen: GuiScreen? = null
    private var screenTicks = 0
    fun consoleLog(message: String) {
        logger.log(Level.INFO, message)
    }

    fun launchCoroutine(function: suspend () -> Unit) {
        coroutineScope.launch {
            try {
                function()
            } catch (e: Exception) {
                ErrorManager.logErrorWithData(
                    e,
                    e.message ?: "Asynchronous exception caught",
                )
            }
        }
    }
}
