package at.hannibal2.skyhanni.config.features.dev

import at.hannibal2.skyhanni.config.core.config.Position
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import org.lwjgl.input.Keyboard

class GraphConfig {
    @Expose
    @ConfigOption(name = "Enabled", desc = "Enable the graphing tool.")
    @ConfigEditorBoolean
    var enabled: Boolean = false

    @Expose
    @ConfigOption(
        name = "Place Key",
        desc = "Place a new node at the current position. If a node is active automatically connects." +
            "Deletes a node if you are only 3 blocks away instead of placing a new one."
    )
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_F)
    var placeKey: Int = Keyboard.KEY_F

    @Expose
    @ConfigOption(
        name = "Toggle Ghost Position",
        desc = "Creates or removes the Ghost Position. This helps editing nodes tht are in the air."
    )
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NONE)
    var toggleGhostPosition: Int = Keyboard.KEY_NONE

    @Expose
    @ConfigOption(name = "Select Key", desc = "Select the nearest node to be active. Double press to unselect.")
    @ConfigEditorKeybind(defaultKey = -98)
    var selectKey: Int = -98 // Middle Mouse

    @Expose
    @ConfigOption(name = "Select near look", desc = "Select the node closest to where you are looking.")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NONE)
    var selectRaycastKey: Int = Keyboard.KEY_NONE

    @Expose
    @ConfigOption(
        name = "Connect Key",
        desc = "Connect the nearest node with the active node. If the nodes are already connected removes the connection."
    )
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_C)
    var connectKey: Int = Keyboard.KEY_C

    @Expose
    @ConfigOption(name = "Exit Key", desc = "Exit out of stuff. If nothing active disables the graph editor.")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_HOME)
    var exitKey: Int = Keyboard.KEY_HOME

    @Expose
    @ConfigOption(
        name = "Edit Key",
        desc = "While holding the Key, edit the position of the active node or the selection block with the minecraft movement controls."
    )
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_TAB)
    var editKey: Int = Keyboard.KEY_TAB

    @Expose
    @ConfigOption(name = "Text Key", desc = "Start text mode, which allows editing a name of a node.")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_Y)
    var textKey: Int = Keyboard.KEY_Y

    @Expose
    @ConfigOption(
        name = "Test Dijkstra",
        desc = "On key press, show the shortest path between the nearest node and the active node."
    )
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_G)
    var dijkstraKey: Int = Keyboard.KEY_G

    @Expose
    @ConfigOption(name = "Save Key", desc = "Save the current graph to the clipboard.")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_O)
    var saveKey: Int = Keyboard.KEY_O

    @Expose
    @ConfigOption(name = "Load Key", desc = "Load a graph from clipboard, if valid.")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_I)
    var loadKey: Int = Keyboard.KEY_I

    @Expose
    @ConfigOption(
        name = "Clear Key",
        desc = "Clear the graph. Also saves the graph to the clipboard, in case of a misclick."
    )
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_P)
    var clearKey: Int = Keyboard.KEY_P

    @Expose
    @ConfigOption(name = "Vision Key", desc = "Toggle if the graph should render trough blocks.")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_M)
    var throughBlocksKey: Int = Keyboard.KEY_M

    @Expose
    @ConfigOption(
        name = "Tutorial Key",
        desc = "Toggle the tutorial mode. In this mode, you will get feedback for everything you do."
    )
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_K)
    var tutorialKey: Int = Keyboard.KEY_K

    @Expose
    @ConfigOption(
        name = "Split Key",
        desc = "Key for splitting an edge that is between the active and the closed node."
    )
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NONE)
    var splitKey: Int = Keyboard.KEY_NONE

    @Expose
    @ConfigOption(name = "Dissolve Key", desc = "Dissolve the active node into one edge if it only has two edges.")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NONE)
    var dissolveKey: Int = Keyboard.KEY_NONE

    @Expose
    @ConfigOption(
        name = "Edge Cycle",
        desc = "Cycles the direction of the edge that is between the active and the closed node. (Used to make one-directional ways)"
    )
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_H)
    var edgeCycle: Int = Keyboard.KEY_H

    @Expose
    @ConfigLink(owner = GraphConfig::class, field = "enabled")
    var infoDisplay: Position = Position(20, 20)

    @Expose
    @ConfigLink(owner = GraphConfig::class, field = "enabled")
    var namedNodesList: Position = Position(20, 20)

    @Expose
    @ConfigOption(name = "Max Node Distance", desc = "Only render nodes below this distance to the player.")
    @ConfigEditorSlider(minValue = 10f, maxValue = 500f, minStep = 10f)
    var maxNodeDistance: Int = 50

    @Expose
    @ConfigOption(name = "Shows Stats", desc = "Show funny extra statistics on save. May lag the game a bit.")
    @ConfigEditorBoolean
    var showsStats: Boolean = true

    @Expose
    @ConfigOption(
        name = "Use as Island Area",
        desc = "When saving, use the current edited graph as temporary island area for the current island."
    )
    @ConfigEditorBoolean
    var useAsIslandArea: Boolean = false
}
