package at.hannibal2.skyhanni.config.features.gui.customscoreboard

//#if TODO
import at.hannibal2.skyhanni.features.gui.customscoreboard.ScoreboardConfigEventElement
//#endif
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.observer.Property

// todo 1.21 impl needed
class EventsConfig {
    //#if TODO
    @Expose
    @ConfigOption(name = "Events Priority", desc = "Drag your list to select the priority of each event.")
    @ConfigEditorDraggableList
    var eventEntries: Property<MutableList<ScoreboardConfigEventElement>> =
        Property.of(ArrayList(ScoreboardConfigEventElement.defaultOption))

    // TODO move into kotlin
    @ConfigOption(name = "Reset Events Priority", desc = "Reset the priority of all events.")
    @ConfigEditorButton(buttonText = "Reset")
    var reset: Runnable = Runnable {
        eventEntries.get().clear()
        eventEntries.get().addAll(ScoreboardConfigEventElement.defaultOption)
        eventEntries.notifyObservers()
    }
    //#endif

    @Expose
    @ConfigOption(
        name = "Show all active events",
        desc = "Show all active events in the scoreboard instead of the one with the highest priority."
    )
    @ConfigEditorBoolean
    var showAllActiveEvents: Boolean = true
}
