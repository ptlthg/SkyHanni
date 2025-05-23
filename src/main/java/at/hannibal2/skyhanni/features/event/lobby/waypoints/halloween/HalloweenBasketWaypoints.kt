package at.hannibal2.skyhanni.features.event.lobby.waypoints.halloween

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.data.IslandGraphs
import at.hannibal2.skyhanni.data.ScoreboardData
import at.hannibal2.skyhanni.data.model.GraphNode
import at.hannibal2.skyhanni.data.model.GraphNodeTag
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.ScoreboardUpdateEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.features.event.lobby.waypoints.EventWaypoint
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ConditionalUtils.onToggle
import at.hannibal2.skyhanni.utils.GraphUtils
import at.hannibal2.skyhanni.utils.LocationUtils.distanceSqToPlayer
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object HalloweenBasketWaypoints {

    private val config get() = SkyHanniMod.feature.event.lobbyWaypoints.halloweenBasket
    private var isActive: Boolean = false

    private val basketList = mutableListOf<EventWaypoint>()
    private var closestBasket: EventWaypoint? = null

    private val patternGroup = RepoPattern.group("event.lobbywaypoints")

    // TODO add regex tests
    private val scoreboardTitlePattern by patternGroup.pattern(
        "main.scoreboard.title",
        "^HYPIXEL$",
    )
    private val halloweenEventPattern by patternGroup.pattern(
        "main.scoreboard.halloween",
        "^§6Halloween \\d+$",
    )
    private val scoreboardBasketPattern by patternGroup.pattern(
        "main.scoreboard.basket",
        "^Baskets Found: §a\\d+§f/§a\\d+\$",
    )

    @Suppress("MaxLineLength")
    private val basketPattern by patternGroup.pattern(
        "basket",
        "^(?:(?:§.)+You found a Candy Basket! (?:(?:§.)+\\((?:§.)+(?<current>\\d+)(?:§.)+/(?:§.)+(?<max>\\d+)(?:§.)+\\))?|(?:§.)+You already found this Candy Basket!)\$",
    )
    private val basketAllFoundPattern by patternGroup.pattern(
        "basket.allfound",
        "^§a§lCongratulations! You found all Candy Baskets!$",
    )

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!event.repeatSeconds(2)) return
        if (!isActive || !isEnabled()) return

        val newClosest = getClosest()
        if (newClosest == closestBasket) return

        closestBasket = newClosest
        if (config.pathfind.get() && config.allWaypoints) startPathfind()
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!config.allWaypoints) return
        if (!isActive) return
        if (!isEnabled()) return

        if (basketAllFoundPattern.matches(event.message)) {
            disableFeature()
            return
        }

        if (!basketPattern.matches(event.message)) return
        basketList.minByOrNull { it.position.distanceSqToPlayer() }?.isFound = true
        if (basketList.all { it.isFound }) {
            disableFeature()
            return
        }
        closestBasket = getClosest()
        startPathfind()
    }

    @HandleEvent
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!isEnabled()) return
        if (!isActive) return
        if (!config.allWaypoints) return

        if (config.onlyClosest) {
            closestBasket.render(event)
        } else {
            basketList.forEach {
                it.render(event)
            }
        }
    }

    private fun EventWaypoint?.render(event: SkyHanniRenderWorldEvent) {
        if (this == null) return
        event.drawDynamicText(position, "§dBasket", 1.0)
    }

    @HandleEvent
    fun onScoreboardChange(event: ScoreboardUpdateEvent) {
        if (LorenzUtils.inSkyBlock) {
            isActive = false
            return
        }
        var inHub = false
        var halloweenMatches = false
        var basketMatches = false

        if (scoreboardTitlePattern.matches(ScoreboardData.objectiveTitle.removeColor())) {
            inHub = true
        }
        event.full.forEach {
            if (halloweenEventPattern.matches(it)) {
                halloweenMatches = true
            } else if (scoreboardBasketPattern.matches(it)) {
                basketMatches = true
            }
        }

        val newIsActive = inHub && halloweenMatches && basketMatches
        if (isActive != newIsActive && newIsActive) {
            IslandGraphs.loadLobby("MAIN_LOBBY")

            val nodeList = IslandGraphs.currentIslandGraph?.nodes?.filter { it.hasTag(GraphNodeTag.HALLOWEEN_BASKET) }.orEmpty()
            basketList.clear()
            nodeList.forEach { node ->
                basketList.add(EventWaypoint(position = node.position, isFound = false))
            }
            closestBasket = getClosest(nodeList)
            if (config.pathfind.get() && config.allWaypoints) startPathfind()
        }
        isActive = newIsActive
    }

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        config.pathfind.onToggle {
            if (config.pathfind.get() && isActive && isEnabled()) startPathfind()
        }
    }

    private fun startPathfind() {
        val basket = closestBasket ?: return

        IslandGraphs.pathFind(
            basket.position,
            "§dNext Basket",
            LorenzColor.LIGHT_PURPLE.toColor(),
            condition = { config.pathfind.get() && closestBasket != null && config.allWaypoints },
        )
    }

    private fun getClosest(nodeList: List<GraphNode>? = null): EventWaypoint? {
        val nodes = nodeList ?: IslandGraphs.currentIslandGraph?.nodes?.filter {
            it.hasTag(GraphNodeTag.HALLOWEEN_BASKET)
        }.orEmpty()

        val unFoundBaskets = basketList.filter { !it.isFound }.map { it.position }
        val unFoundNodes = nodes.filter { it.position in unFoundBaskets }
        val currentNode = IslandGraphs.closestNode ?: return null

        val closestNode = unFoundNodes.minByOrNull { GraphUtils.findShortestDistance(currentNode, it) } ?: return null
        return EventWaypoint(position = closestNode.position, isFound = false)
    }

    private fun disableFeature() {
        ChatUtils.chat("Disabling Halloween Basket waypoints since you found all of them!")
        config.allWaypoints = false
    }

    private fun isEnabled() = HypixelData.hypixelLive && !LorenzUtils.inSkyBlock

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(13, "event.halloweenBasket", "event.lobbyWaypoints.halloweenBasket")
    }
}
