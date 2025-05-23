package at.hannibal2.skyhanni.features.gui.customscoreboard.elements

import at.hannibal2.skyhanni.data.IslandTypeTags
import at.hannibal2.skyhanni.features.gui.customscoreboard.CustomScoreboardUtils.getSBLines
import at.hannibal2.skyhanni.features.gui.customscoreboard.ScoreboardPattern
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatches

// scoreboard
// scoreboard update event
object ScoreboardElementVisiting : ScoreboardElement() {
    override fun getDisplay() = ScoreboardPattern.visitingPattern.firstMatches(getSBLines())

    override val configLine = " §a✌ §7(§a1§7/6)"

    override val elementPatterns = listOf(ScoreboardPattern.visitingPattern)

    override fun showIsland() = IslandTypeTags.PERSONAL_ISLAND.inAny()
}
