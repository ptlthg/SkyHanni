package at.hannibal2.skyhanni.features.gui

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.enums.OutsideSBFeature
import at.hannibal2.skyhanni.data.ElectionApi
import at.hannibal2.skyhanni.data.Perk
import at.hannibal2.skyhanni.data.Perk.Companion.toPerk
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderable
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.renderables.Renderable

private val config get() = SkyHanniMod.feature.gui.mayorOverlay

enum class MayorOverlay(private val configLine: String, private val createLines: () -> Renderable) {
    TITLE(
        "Title",
        {
            Renderable.string("§6§lMAYOR OVERLAY")
        },
    ),
    MAYOR(
        "Mayor",
        {
            val currentMayor = ElectionApi.currentMayor
            renderPerson(
                "Mayor",
                currentMayor?.mayorName,
                currentMayor?.activePerks,
            )
        },
    ),
    MINISTER(
        "Minister",
        {
            val currentMinister = ElectionApi.currentMinister
            renderPerson(
                "Minister",
                currentMinister?.mayorName,
                currentMinister?.activePerks,
            )
        },
    ),
    CANDIDATES(
        "Candidates",
        {
            val candidates = ElectionApi.rawMayorData?.current?.candidates.orEmpty()

            Renderable.verticalContainer(
                candidates.map { candidate ->
                    renderPerson(
                        "Candidate",
                        candidate.name,
                        candidate.perks.mapNotNull { it.toPerk() },
                    )
                },
                spacing = config.candidateSpacing,
            )
        },
    ),
    NEW_MAYOR(
        "New Mayor Time",
        {
            Renderable.string("§7New Mayor in: §e${ElectionApi.nextMayorTimestamp.timeUntil().format(showMilliSeconds = false)}")
        },
    ),
    ;

    override fun toString() = configLine

    @SkyHanniModule
    companion object {
        var display: Renderable? = null

        @HandleEvent
        fun onSecondPassed(event: SecondPassedEvent) {
            if (!isEnabled()) return
            with(config) {
                display = mayorOverlay.map { it.createLines() }.let { Renderable.verticalContainer(it, spacing = spacing) }
            }
        }

        @HandleEvent
        fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
            if (!isEnabled()) return
            display?.let { config.position.renderRenderable(it, posLabel = "Mayor Overlay") }
        }

        private fun isEnabled() = (LorenzUtils.inSkyBlock || OutsideSBFeature.MAYOR_OVERLAY.isSelected()) && config.enabled
    }
}

private fun renderPerson(title: String, name: String?, perks: List<Perk>?): Renderable {
    val colorCode = ElectionApi.mayorNameToColorCode(name.orEmpty())
    val perkLines = perks?.takeIf { config.showPerks }?.map { perk ->
        " ${if (perk.minister) "§6✯ " else ""}§e${perk.perkName}" to "§7${perk.description}"
    }.orEmpty()

    return Renderable.verticalContainer(
        buildMap {
            name?.let { put("$colorCode$title $it", null) }
            putAll(perkLines)
        }.map { (key, value) ->
            value?.let {
                Renderable.hoverTips(
                    Renderable.string(key),
                    listOf(Renderable.wrappedString(it, 200)),
                )
            } ?: Renderable.string(key)
        },
    )
}
