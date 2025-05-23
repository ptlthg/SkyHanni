package at.hannibal2.skyhanni.test

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.data.repo.RepoManager
import at.hannibal2.skyhanni.data.repo.RepoManager.hasDefaultSettings
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.features.misc.CurrentPing
import at.hannibal2.skyhanni.features.misc.IslandAreas
import at.hannibal2.skyhanni.features.misc.TpsCounter
import at.hannibal2.skyhanni.features.misc.limbo.LimboTimeTracker
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.OSUtils
import at.hannibal2.skyhanni.utils.StringUtils.equalsIgnoreColor
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import at.hannibal2.skyhanni.utils.toLorenzVec
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object DebugCommand {

    fun command(args: Array<String>) {
        if (args.size == 2 && args[0] == "profileName") {
            HypixelData.profileName = args[1].lowercase()
            ChatUtils.chat("§eManually set profileName to '${HypixelData.profileName}'")
            return
        }
        val list = mutableListOf<String>()
        list.add("```")
        list.add("= Debug Information for SkyHanni ${SkyHanniMod.VERSION} =")
        list.add("")

        val search = args.joinToString(" ")
        list.add(
            if (search.isNotEmpty()) {
                if (search.equalsIgnoreColor("all")) {
                    "search for everything:"
                } else "search '$search':"
            } else "no search specified, only showing interesting stuff:",
        )

        val event = DebugDataCollectEvent(list, search)

        // calling default debug stuff
        player(event)
        repoData(event)
        globalRender(event)
        skyblockStatus(event)
        networkInfo(event)
        profileName(event)
        profileType(event)

        event.post()

        if (event.empty) {
            list.add("")
            list.add("Nothing interesting to show right now!")
            list.add("Looking for something specific? /shdebug <search>")
            list.add("Wanna see everything? /shdebug all")
        }

        list.add("```")
        OSUtils.copyToClipboard(list.joinToString("\n"))
        ChatUtils.chat("§eCopied SkyHanni debug data in the clipboard.")
    }

    private fun profileType(event: DebugDataCollectEvent) {
        event.title("Profile Type")
        if (!LorenzUtils.inSkyBlock) {
            event.addIrrelevant("Not on SkyBlock")
            return
        }

        if (ProfileStorageData.playerSpecific == null) {
            event.addData("playerSpecific is null!")
            return
        }

        val classic = !LorenzUtils.noTradeMode
        if (classic) {
            event.addIrrelevant("on classic")
        } else {
            if (HypixelData.ironman) {
                event.addData("on ironman")
            }
            if (HypixelData.stranded) {
                event.addData("on stranded")
            }
            if (HypixelData.bingo) {
                event.addData("on bingo")
            }
        }
    }

    private fun profileName(event: DebugDataCollectEvent) {
        event.title("Profile Name")
        if (!LorenzUtils.inSkyBlock) {
            event.addIrrelevant("Not on SkyBlock")
            return
        }

        if (HypixelData.profileName != "") {
            event.addIrrelevant("profileName: '${HypixelData.profileName}'")
        } else {
            event.addData("profile name is empty!")
        }
    }

    private fun skyblockStatus(event: DebugDataCollectEvent) {
        event.title("SkyBlock Status")
        if (!LorenzUtils.onHypixel) {
            event.addData("not on Hypixel")
            return
        }
        if (!LorenzUtils.inSkyBlock) {
            event.addData("not on SkyBlock, but on Hypixel")
            return
        }
        if (LorenzUtils.skyBlockIsland == IslandType.UNKNOWN) {
            event.addData("Unknown SkyBlock island!")
            return
        }
        if (LorenzUtils.skyBlockIsland == IslandType.NONE) {
            event.addData("No SkyBlock island found!")
            return
        }

        if (LorenzUtils.skyBlockIsland != HypixelData.skyBlockIsland) {
            event.addData {
                add("using a test island!")
                add("test island: ${SkyBlockIslandTest.testIsland}")
                add("real island: ${HypixelData.skyBlockIsland}")
            }
            return
        }

        event.addIrrelevant {
            add("on Hypixel SkyBlock")
            add("skyBlockIsland: ${LorenzUtils.skyBlockIsland}")
            add("skyBlockArea:")
            add("  scoreboard: '${LorenzUtils.skyBlockArea}'")
            add("  graph network: '${IslandAreas.currentAreaName}'")
            with(MinecraftCompat.localPlayer.position.toLorenzVec().roundTo(1)) {
                add(" /shtestwaypoint $x $y $z pathfind")
            }
            add("isOnAlphaServer: '${LorenzUtils.isOnAlphaServer}'")
        }
    }

    private fun globalRender(event: DebugDataCollectEvent) {
        event.title("Global Render")
        if (SkyHanniDebugsAndTests.globalRender) {
            event.addIrrelevant("normal enabled")
        } else {
            event.addData {
                add("Global renderer is disabled!")
                add("No renderable elements from SkyHanni will show up anywhere!")
            }
        }
    }

    private fun repoData(event: DebugDataCollectEvent) {
        event.title("Repo Information")
        val config = SkyHanniMod.feature.dev.repo

        val hasDefaultSettings = config.location.hasDefaultSettings()
        val list = buildList {
            add(" repoAutoUpdate: ${config.repoAutoUpdate}")
            add(" usingBackupRepo: ${RepoManager.usingBackupRepo}")
            if (hasDefaultSettings) {
                add((" repo location: default"))
            } else {
                add(" non-default repo location: '${RepoManager.getRepoLocation()}'")
            }

            if (RepoManager.unsuccessfulConstants.isNotEmpty()) {
                add(" unsuccessful constants:")
                for (constant in RepoManager.unsuccessfulConstants) {
                    add("  - $constant")
                }
            }

            add(" loaded neu items: ${NeuItems.allNeuRepoItems().size}")
        }

        val isRelevant = RepoManager.usingBackupRepo || RepoManager.unsuccessfulConstants.isNotEmpty() || !hasDefaultSettings
        if (isRelevant) {
            event.addData(list)
        } else {
            event.addIrrelevant(list)
        }
    }

    private fun player(event: DebugDataCollectEvent) {
        event.title("Player")
        event.addIrrelevant {
            add("name: '${LorenzUtils.getPlayerName()}'")
            add("uuid: '${LorenzUtils.getPlayerUuid()}'")
        }
    }

    private const val TPS_LIMIT = 15.0
    private val pingLimit = 1.5.seconds

    private fun networkInfo(event: DebugDataCollectEvent) {
        event.title("Network Information")
        val tps = TpsCounter.tps ?: 0.0
        val pingEnabled = SkyHanniMod.feature.dev.hypixelPingApi

        val list = buildList {
            add("tps: $tps")
            add("ping: ${CurrentPing.averagePing.inWholeMilliseconds.formatTime()}")

            val lastWorldSwitch = LorenzUtils.lastWorldSwitch.passedSince()
            var showPreviousPings = CurrentPing.averagePing > pingLimit
            if (!pingEnabled) {
                add("Hypixel Ping Packet disabled in settings!")
                showPreviousPings = true
            }
            if (lastWorldSwitch < 1.minutes) {
                add("last world switch: ${lastWorldSwitch.format()} ago")
                showPreviousPings = true
            }
            if (CurrentPing.previousPings.any { it > 5_000 }) {
                showPreviousPings = true
            }
            if (showPreviousPings) {
                add("previousPings: ${CurrentPing.previousPings.map { it.formatTime() }}")
            }

            if (LimboTimeTracker.inLimbo) {
                add("currently in limbo!")
            }
        }



        if (tps < TPS_LIMIT || CurrentPing.averagePing > pingLimit || !pingEnabled) {
            event.addData(list)
        } else {
            event.addIrrelevant(list)
        }
    }

    private fun Long.formatTime(): String = if (this > 999) {
        this.milliseconds.format(showMilliSeconds = true)
    } else this.addSeparators() + "ms"
}
