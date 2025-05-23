package at.hannibal2.skyhanni.test.graph

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.model.GraphNodeTag
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.graph.GraphEditor.distanceToPlayer
import at.hannibal2.skyhanni.utils.KeyboardManager
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.sortedDesc
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.takeIfNotEmpty
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addString
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.ScrollValue
import at.hannibal2.skyhanni.utils.renderables.SearchTextInput
import at.hannibal2.skyhanni.utils.renderables.Searchable
import at.hannibal2.skyhanni.utils.renderables.buildSearchableScrollable
import at.hannibal2.skyhanni.utils.renderables.toSearchable
import net.minecraft.client.Minecraft
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object GraphNodeEditor {

    private val scrollValueNodes = ScrollValue()
    private val scrollValueTags = ScrollValue()
    private val textInput = SearchTextInput()
    private var nodesDisplay = emptyList<Renderable>()
    private var lastUpdate = SimpleTimeMark.farPast()
    private val tagsToShow: MutableList<GraphNodeTag> = GraphNodeTag.entries.toMutableList()

    @HandleEvent(GuiRenderEvent.GuiOverlayRenderEvent::class)
    fun onRenderOverlay() {
        if (Minecraft.getMinecraft().currentScreen == null) {
            doRender()
        }
    }

    @HandleEvent(GuiRenderEvent.ChestGuiOverlayRenderEvent::class)
    fun onBackgroundDraw() {
        doRender()
    }

    private fun doRender() {
        if (!isEnabled()) return

        config.namedNodesList.renderRenderables(
            getNodeNames(),
            posLabel = "Graph Nodes List",
        )
    }

    private fun getNodeNames(): List<Renderable> {
        if (lastUpdate.passedSince() > 250.milliseconds) {
            updateNodeNames()
        }
        return nodesDisplay
    }

    private fun updateNodeNames() {
        lastUpdate = SimpleTimeMark.now()
        nodesDisplay = buildList {
            val list = drawNodeNames()
            val total = GraphEditor.nodes.count { it.name?.isNotBlank() ?: false }
            val shown = list.size
            add(
                Renderable.clickable(
                    "§eGraph Nodes: $shown/$total",
                    tips = listOf("§eClick to toggle node tags!"),
                    onLeftClick = {
                        updateToggleTags()
                    },
                ),
            )
            val height = (shown * 10).coerceAtMost(250)
            if (list.isNotEmpty()) {
                add(list.buildSearchableScrollable(height, textInput, scrollValueNodes, velocity = 10.0))
            }
        }
    }

    private fun updateToggleTags() {
        lastUpdate = SimpleTimeMark.now() + 60.seconds
        nodesDisplay = buildList {
            addString("§eToggle Visible Tags")
            val map = mutableMapOf<GraphNodeTag, Int>()
            for (tag in GraphNodeTag.entries) {
                val nodes = GraphEditor.nodes.count { tag in it.tags }
                map[tag] = nodes
            }
            for (tag in map.sortedDesc().keys) {
                val isVisible = tag in tagsToShow
                val nodes = GraphEditor.nodes.count { tag in it.tags }
                val visibilityText = if (isVisible) " §aVisible" else " §7Invisible"
                val name = " - ${tag.displayName} §8($nodes nodes) $visibilityText"
                add(
                    Renderable.clickable(
                        name,
                        tips = listOf("§eClick to " + (if (isVisible) "hide" else "show") + " nodes with this tag!"),
                        onLeftClick = {
                            toggleTag(tag)
                            updateToggleTags()
                        },
                    ),
                )
            }
            addString("")
            add(
                Renderable.clickable(
                    "§cGo Back!",
                    tips = listOf("§eClick to go back to the node list!"),
                    onLeftClick = {
                        updateNodeNames()
                    },
                ),
            )
        }

    }

    private fun toggleTag(tag: GraphNodeTag) {
        if (tag in tagsToShow) {
            tagsToShow.remove(tag)
        } else {
            tagsToShow.add(tag)
        }
    }

    private fun updateTagView(node: GraphingNode) {
        lastUpdate = SimpleTimeMark.now() + 60.seconds
        nodesDisplay = buildList {
            val list = drawTagNames(node)
            val size = list.size
            addString("§eGraph Nodes: $size")
            val height = (size * 10).coerceAtMost(250)
            if (list.isNotEmpty()) {
                add(Renderable.scrollList(list, height, scrollValueTags, velocity = 10.0))
            }
        }
    }

    private fun drawTagNames(node: GraphingNode): List<Renderable> = buildList {
        addString("§eChange tag for node '${node.name}§e'")
        addString("")

        for (tag in GraphNodeTag.entries.filter { it in node.tags || checkIsland(it) }) {
            val state = if (tag in node.tags) "§aYES" else "§cNO"
            val name = state + " §r" + tag.displayName
            add(createTagName(name, tag, node))
        }
        addString("")
        add(
            Renderable.clickable(
                "§cGo Back!",
                tips = listOf("§eClick to go back to the node list!"),
                onLeftClick = {
                    updateNodeNames()
                },
            ),
        )
    }

    private fun checkIsland(tag: GraphNodeTag): Boolean {
        val islandMatches = tag.onlyIsland?.let {
            it == LorenzUtils.skyBlockIsland
        } ?: tag.onlyIslands.takeIfNotEmpty()?.let {
            LorenzUtils.skyBlockIsland in it
        } ?: true

        val skyblockMatches = tag.onlySkyblock?.let {
            it == LorenzUtils.inSkyBlock
        } ?: true

        return islandMatches && skyblockMatches
    }

    private fun createTagName(
        name: String,
        tag: GraphNodeTag,
        node: GraphingNode,
    ) = Renderable.clickable(
        name,
        tips = listOf(
            "Tag ${tag.name}",
            "§7${tag.description}",
            "",
            "§eClick to set tag for ${node.name} to ${tag.name}!",
        ),
        onLeftClick = {
            if (tag in node.tags) {
                node.tags.remove(tag)
            } else {
                node.tags.add(tag)
            }
            updateTagView(node)
        },
    )

    private fun drawNodeNames(): List<Searchable> = buildList {
        for ((node, distance: Double) in GraphEditor.nodes.map {
            it to distanceToPlayer(it.position)
        }.sortedBy { it.second }) {
            if (node.tags.isNotEmpty()) {
                if (!node.tags.any { it in tagsToShow }) continue
            }
            val name = node.name?.takeIf { it.isNotBlank() } ?: continue
            val color = if (node == GraphEditor.activeNode) "§a" else "§7"
            val distanceFormat = sqrt(distance).toInt().addSeparators()
            val tagText = node.tags.let { tags ->
                if (tags.isEmpty()) {
                    " §cNo tag§r"
                } else {
                    val text = node.tags.joinToString(", ") { it.internalName }
                    " §f($text)"
                }
            }

            val text = "${color}Node §r$name$tagText §7[$distanceFormat]"
            add(createNodeTextLine(text, name, node))
        }
    }

    private fun createNodeTextLine(
        text: String,
        name: String,
        node: GraphingNode,
    ): Searchable = Renderable.clickable(
        text,
        tips = buildList {
            add("Node '$name'")
            add("")

            if (node.tags.isNotEmpty()) {
                add("Tags: ")
                for (tag in node.tags) {
                    add(" §8- §r${tag.displayName}")
                }
                add("")
            }

            add("§eClick to select/deselect this node!")
            add("§eControl-Click to edit the tags for this node!")

        },
        onLeftClick = {
            if (KeyboardManager.isModifierKeyDown()) {
                updateTagView(node)
            } else {
                GraphEditor.activeNode = node
                updateNodeNames()
            }
        },
    ).toSearchable(name)

    fun isEnabled() = GraphEditor.isEnabled()
    private val config get() = GraphEditor.config

}
