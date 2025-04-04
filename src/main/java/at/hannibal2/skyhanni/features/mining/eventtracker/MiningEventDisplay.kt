package at.hannibal2.skyhanni.features.mining.eventtracker

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.enums.OutsideSBFeature
import at.hannibal2.skyhanni.config.features.mining.MiningEventConfig
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.features.mining.eventtracker.MiningEventType.Companion.CompressFormat
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ConfigUtils
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NeuItems.getItemStack
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SimpleTimeMark.Companion.asTimeMark
import at.hannibal2.skyhanni.utils.renderables.Renderable

@SkyHanniModule
object MiningEventDisplay {
    private val config get() = SkyHanniMod.feature.mining.miningEvent
    private var display = listOf<Renderable>()

    private val islandEventData = mutableMapOf<IslandType, MiningIslandEventInfo>()

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        updateDisplay()
    }

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!shouldDisplay()) return
        config.position.renderRenderables(display, posLabel = "Mining Event Tracker")
    }

    private fun updateDisplay() {
        display = updateEvents()
    }

    private fun updateEvents() = buildList {
        if (MiningEventTracker.apiError) {
            val count = MiningEventTracker.apiErrorCount
            add(Renderable.string("§cMining Event API Error! ($count)"))
            add(Renderable.string("§cSwap servers to try again!"))
        }

        val sortedIslandEventData = islandEventData.entries
            .sortedBy { entry ->
                when (entry.key) {
                    IslandType.DWARVEN_MINES -> 0
                    IslandType.CRYSTAL_HOLLOWS -> 1
                    else -> Int.MAX_VALUE
                }
            }
            .associate { it.key to it.value }

        for ((islandType, eventDetails) in sortedIslandEventData) {
            val shouldShow = when (config.showType) {
                MiningEventConfig.ShowType.DWARVEN -> islandType == IslandType.DWARVEN_MINES
                MiningEventConfig.ShowType.CRYSTAL -> islandType == IslandType.CRYSTAL_HOLLOWS
                MiningEventConfig.ShowType.CURRENT -> islandType.isInIsland()
                else -> true
            }

            eventDetails.islandEvents.firstOrNull()?.let { firstEvent ->
                if (firstEvent.endsAt.asTimeMark().isInPast()) {
                    eventDetails.lastEvent = firstEvent.event
                }
            }

            if (!shouldShow) continue
            val upcomingEvents = formatUpcomingEvents(eventDetails.islandEvents, eventDetails.lastEvent)
            val islandName = if (config.islandAsIcon) {
                Renderable.horizontalContainer(getIslandIcon(islandType))
            } else {
                Renderable.string("§a${islandType.displayName}§8:")
            }
            add(Renderable.horizontalContainer(listOf(islandName) + upcomingEvents, 3))
        }
    }

    private fun getIslandIcon(islandType: IslandType) = listOf(
        when (islandType) {
            IslandType.DWARVEN_MINES -> Renderable.itemStack(
                "MITHRIL_ORE".toInternalName().getItemStack(),
            )

            IslandType.CRYSTAL_HOLLOWS -> Renderable.itemStack(
                "PERFECT_RUBY_GEM".toInternalName().getItemStack(),
            )

            else -> unknownDisplay
        },
        Renderable.string("§8:"),
    )

    private val unknownDisplay = Renderable.string("§7???")
    private val transitionDisplay = Renderable.string("§8->")

    private fun formatUpcomingEvents(events: List<RunningEventType>, lastEvent: MiningEventType?): Array<Renderable> {
        val upcoming = events.filter { !it.endsAt.asTimeMark().isInPast() }
            .flatMap {
                if (it.isDoubleEvent) listOf(it.event, it.event) else listOf(it.event)
                /* if (it.isDoubleEvent) "${it.event} §8-> ${it.event}" else it.event.toString() */
            }.map { it.getRenderable() }.toMutableList()

        if (upcoming.isEmpty()) upcoming.add(unknownDisplay)
        if (config.passedEvents && upcoming.size < 4) lastEvent?.let { upcoming.add(0, it.getRenderableAsPast()) }
        return upcoming.flatMap { listOf(it, transitionDisplay) }.dropLast(1).toTypedArray()
        /* return upcoming.joinToString(" §8-> ") */
    }

    fun updateData(eventData: MiningEventData) {
        for ((islandType, events) in eventData.runningEvents) {
            // we now ignore mineshaft events.
            if (islandType == IslandType.MINESHAFT) continue
            val sorted = events.filter { islandType == IslandType.DWARVEN_MINES || !it.event.dwarvenSpecific }
                .sortedBy { it.endsAt - it.event.defaultLength.inWholeMilliseconds }

            val oldData = islandEventData[islandType]
            if (oldData == null) {
                if (sorted.isNotEmpty()) {
                    islandEventData[islandType] = MiningIslandEventInfo(sorted)
                }
            } else {
                oldData.islandEvents = sorted
            }
        }
    }

    private fun shouldDisplay(): Boolean {
        val isOnValidMiningLocation = LorenzUtils.inSkyBlock && (config.outsideMining || MiningEventTracker.isMiningIsland())

        return (isOnValidMiningLocation || OutsideSBFeature.MINING_EVENT_DISPLAY.isSelected()) && config.enabled
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.transform(46, "mining.miningEvent.compressedFormat") {
            ConfigUtils.migrateBooleanToEnum(it, CompressFormat.COMPACT_TEXT, CompressFormat.DEFAULT)
        }
    }
}

private class MiningIslandEventInfo(var islandEvents: List<RunningEventType>, var lastEvent: MiningEventType? = null)
