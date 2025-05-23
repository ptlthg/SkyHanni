package at.hannibal2.skyhanni.config.features.gui.customscoreboard

//#if TODO
import at.hannibal2.skyhanni.features.gui.customscoreboard.ChunkedStatsLine
//#endif
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

// todo 1.21 impl needed
class ChunkedStatsConfig {
    //#if TODO
    @Expose
    @ConfigOption(name = "Chunked Stats", desc = "Select the stats you want to display chunked on the scoreboard.")
    @ConfigEditorDraggableList
    var chunkedStats: MutableList<ChunkedStatsLine> = ChunkedStatsLine.entries.toMutableList()
    //#endif

    @Expose
    @ConfigOption(name = "Max Stats per Line", desc = "The maximum amount of stats that will be displayed in one line.")
    @ConfigEditorSlider(minValue = 1f, maxValue = 10f, minStep = 1f)
    var maxStatsPerLine: Int = 3
}
