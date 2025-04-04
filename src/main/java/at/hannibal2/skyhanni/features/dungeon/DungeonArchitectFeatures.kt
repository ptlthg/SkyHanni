package at.hannibal2.skyhanni.features.dungeon

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.GetFromSackApi
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.SackApi.getAmountInSacks
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.PrimitiveItemStack.Companion.makePrimitiveStack
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object DungeonArchitectFeatures {

    private val config get() = SkyHanniMod.feature.dungeon
    private val patternGroup = RepoPattern.group("dungeon.architectsdraft")

    private val puzzleFailPattern by patternGroup.pattern(
        "puzzle.fail.normal",
        "(?:§c§lPUZZLE FAIL!|§4) §.§.(?<name>\\S*) .*",
    )
    private val quizPuzzleFailPattern by patternGroup.pattern(
        "puzzle.fail.quiz",
        "§4\\[STATUE] Oruo the Omniscient§r§f: (?:§.)*(?<name>\\S*) (?:§.)*chose the wrong .*",
    )

    private val architectsFirstDraftItem = "ARCHITECT_FIRST_DRAFT".toInternalName()

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!isEnabled()) return

        puzzleFailPattern.matchMatcher(event.message) {
            generateMessage(group("name"), event)
        }
        quizPuzzleFailPattern.matchMatcher(event.message) {
            generateMessage(group("name"), event)
        }
    }

    private val architectsFirstDraft = "ARCHITECT_FIRST_DRAFT".toInternalName().makePrimitiveStack()

    private fun generateMessage(name: String, event: SkyHanniChatEvent) {
        val architectItemAmount = architectsFirstDraftItem.getAmountInSacks()
        if (architectItemAmount <= 0) return

        GetFromSackApi.getFromChatMessageSackItems(
            architectsFirstDraft,
            "§c§lPUZZLE FAILED! §r§b$name §r§efailed a puzzle. \n" +
                "§eClick here to get §5Architect's First Draft §7(§e${architectItemAmount}x left§7)",
        )

        TitleManager.sendTitle("§c§lPUZZLE FAILED!")
        event.blockedReason = "puzzle_fail"
    }

    private fun isEnabled(): Boolean = DungeonApi.inDungeon() && config.architectNotifier
}
