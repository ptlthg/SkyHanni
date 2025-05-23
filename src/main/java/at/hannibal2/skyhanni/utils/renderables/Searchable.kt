package at.hannibal2.skyhanni.utils.renderables

import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.renderables.container.SearchableVerticalContainer

class Searchable(val renderable: Renderable, val string: String?)

fun Renderable.toSearchable(searchText: String? = null) = Searchable(this, searchText?.removeColor())
fun Searchable.toRenderable() = renderable
fun List<Searchable>.toRenderable() = map { it.toRenderable() }
fun List<Searchable>.toMap() = associate { it.renderable to it.string }
const val SEARCH_PREFIX = "§eSearch: §7"

fun List<Searchable>.buildSearchBox(
    textInput: SearchTextInput,
): Renderable {
    val key = 0
    return Renderable.searchBox(
        SearchableVerticalContainer(toMap(), textInput = textInput, key = key + 1, spacing = 1),
        SEARCH_PREFIX,
        onUpdateSize = {},
        textInput = textInput,
        key = key,
    )
}

fun List<Searchable>.buildSearchableScrollable(
    height: Int,
    textInput: SearchTextInput,
    scrollValue: ScrollValue = ScrollValue(),
    velocity: Double = 2.0,
): Renderable {
    val key = 0
    return Renderable.searchBox(
        Renderable.searchableScrollList(
            toMap(),
            textInput = textInput,
            key = key + 1,
            height = height,
            scrollValue = scrollValue,
            velocity = velocity,
        ),
        SEARCH_PREFIX,
        onUpdateSize = {},
        textInput = textInput,
        key = key,
    )
}

fun Map<List<Renderable>, String>.buildSearchableTable(textInput: SearchTextInput): Renderable {
    val key = 0
    return Renderable.searchBox(
        Renderable.searchableTable(toMap(), textInput = textInput, key = key + 1),
        SEARCH_PREFIX,
        onUpdateSize = {},
        textInput = textInput,
        key = key,
    )
}
