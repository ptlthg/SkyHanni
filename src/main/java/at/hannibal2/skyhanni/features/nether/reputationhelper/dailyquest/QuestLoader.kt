package at.hannibal2.skyhanni.features.nether.reputationhelper.dailyquest

import at.hannibal2.skyhanni.config.storage.ProfileSpecificStorage
import at.hannibal2.skyhanni.data.jsonobjects.repo.ReputationQuest
import at.hannibal2.skyhanni.data.model.TabWidget
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.features.nether.reputationhelper.CrimsonIsleReputationHelper
import at.hannibal2.skyhanni.features.nether.reputationhelper.dailyquest.quest.DojoQuest
import at.hannibal2.skyhanni.features.nether.reputationhelper.dailyquest.quest.FetchQuest
import at.hannibal2.skyhanni.features.nether.reputationhelper.dailyquest.quest.KuudraQuest
import at.hannibal2.skyhanni.features.nether.reputationhelper.dailyquest.quest.MiniBossQuest
import at.hannibal2.skyhanni.features.nether.reputationhelper.dailyquest.quest.ProgressQuest
import at.hannibal2.skyhanni.features.nether.reputationhelper.dailyquest.quest.Quest
import at.hannibal2.skyhanni.features.nether.reputationhelper.dailyquest.quest.QuestState
import at.hannibal2.skyhanni.features.nether.reputationhelper.dailyquest.quest.RescueMissionQuest
import at.hannibal2.skyhanni.features.nether.reputationhelper.dailyquest.quest.TrophyFishQuest
import at.hannibal2.skyhanni.features.nether.reputationhelper.dailyquest.quest.UnknownQuest
import at.hannibal2.skyhanni.features.nether.reputationhelper.kuudra.DailyKuudraBossHelper
import at.hannibal2.skyhanni.features.nether.reputationhelper.miniboss.DailyMiniBossHelper
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RegexUtils.groupOrNull
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.TabListData
import net.minecraft.item.ItemStack

object QuestLoader {

    val quests = mutableMapOf<String, Pair<String, ReputationQuest>>()
    fun loadQuests(data: Map<String, ReputationQuest>, questType: String) {
        for ((questName, questInfo) in data) {
            quests[questName] = Pair(questType, questInfo)
        }
    }

    fun loadFromTabList() {
        DailyQuestHelper.greatSpook = false
        var found = 0


        for (line in TabWidget.FACTION_QUESTS.lines) {
            readQuest(line)
            found++
            if (DailyQuestHelper.greatSpook) return
        }

        CrimsonIsleReputationHelper.tabListQuestsMissing = found == 0
        DailyQuestHelper.update()
    }

    private fun readQuest(line: String) {
        CrimsonIsleReputationHelper.tabListQuestPattern.matchMatcher(line) {
            if (line.contains("The Great Spook")) {
                DailyQuestHelper.greatSpook = true
                DailyQuestHelper.update()
                return
            }

            val name = group("name")
            val amount = groupOrNull("amount")?.toInt() ?: 1
            val green = group("status") == "✔"

            checkQuest(name, green, amount)
        }
    }

    private fun checkQuest(name: String, green: Boolean, needAmount: Int) {
        val oldQuest = getQuestByName(name)
        if (oldQuest != null) {
            if (green && oldQuest.state != QuestState.READY_TO_COLLECT && oldQuest.state != QuestState.COLLECTED) {
                oldQuest.state = QuestState.READY_TO_COLLECT
                DailyQuestHelper.update()
                ChatUtils.debug("Reputation Helper: Tab-List updated ${oldQuest.internalName} (This should not happen)")
            }
            return
        }

        val state = if (green) QuestState.READY_TO_COLLECT else QuestState.ACCEPTED
        DailyQuestHelper.update()
        addQuest(addQuest(name, state, needAmount))
    }

    private fun addQuest(name: String, state: QuestState, needAmount: Int): Quest {
        for (miniBoss in DailyMiniBossHelper.miniBosses) {
            if (name == miniBoss.displayName) {
                return MiniBossQuest(miniBoss, state, needAmount)
            }
        }
        for (kuudraTier in DailyKuudraBossHelper.kuudraTiers) {
            val kuudraName = kuudraTier.name
            if (name == "Kill Kuudra $kuudraName Tier") {
                return KuudraQuest(kuudraTier, state)
            }
        }
        var questName = name
        var dojoGoal = ""

        if (name.contains(" Rank ")) {
            val split = name.split(" Rank ")
            questName = split[0]
            dojoGoal = split[1]
        }

        if (questName in quests) {
            val questInfo = quests[questName] ?: return UnknownQuest(name)
            val locationInfo = questInfo.second.location
            val location = CrimsonIsleReputationHelper.readLocationData(locationInfo)
            val displayItem = questInfo.second.item

            when (questInfo.first) {
                "FISHING" -> return TrophyFishQuest(name, location, displayItem, state, needAmount)
                "RESCUE" -> return RescueMissionQuest(displayItem, location, state)
                "FETCH" -> return FetchQuest(name, location, displayItem, state, needAmount)
                "DOJO" -> return DojoQuest(questName, location, displayItem, dojoGoal, state)
            }
        }
        ErrorManager.logErrorStateWithData(
            "Unknown Crimson Isle quest: '$name'",
            "Unknown quest detected",
            "name" to name,
            "questName" to questName,
            "dojoGoal" to dojoGoal,
            "state" to state,
            "needAmount" to needAmount,
            "tablist" to TabListData.getTabList(),
        )
        return UnknownQuest(name)
    }

    private fun getQuestByName(name: String): Quest? {
        return DailyQuestHelper.quests.firstOrNull { it.internalName == name }
    }

    fun checkInventory(event: InventoryFullyOpenedEvent) {
        val inMageRegion = LorenzUtils.skyBlockArea == "Community Center"
        val inBarbarianRegion = LorenzUtils.skyBlockArea == "Dragontail"
        if (!inMageRegion && !inBarbarianRegion) return

        val name = event.inventoryName
        for (quest in DailyQuestHelper.quests) {
            val categoryName = quest.category.name
            if (!categoryName.equals(name, ignoreCase = true)) continue
            val stack = event.inventoryItems[22] ?: continue

            val completed = stack.getLore().any { DailyQuestHelper.townBoardCompletedPattern.matches(it) }
            if (completed && quest.state != QuestState.COLLECTED) {
                quest.state = QuestState.COLLECTED
                DailyQuestHelper.update()
            }

            if (name == "Miniboss") {
                fixMinibossAmount(quest, stack)
            }
        }
    }

    // TODO remove this workaround once hypixel fixes the bug that amount is not in tab list for mini bosses
    private fun fixMinibossAmount(quest: Quest, stack: ItemStack) {
        if (quest !is MiniBossQuest) return
        val storedAmount = quest.needAmount
        if (storedAmount != 1) return
        for (line in stack.getLore()) {
            val realAmount = DailyQuestHelper.minibossAmountPattern.matchMatcher(line) {
                group("amount").toInt()
            } ?: continue
            if (storedAmount == realAmount) continue

            ChatUtils.debug("Wrong amount detected! realAmount: $realAmount, storedAmount: $storedAmount")
            val newQuest = MiniBossQuest(quest.miniBoss, quest.state, realAmount)
            newQuest.haveAmount = quest.haveAmount
            DelayedRun.runNextTick {
                DailyQuestHelper.quests.remove(quest)
                DailyQuestHelper.quests.add(newQuest)
                ChatUtils.chat("Fixed wrong miniboss amount from Town Board.")
                DailyQuestHelper.update()
            }
        }
    }

    fun loadConfig(storage: ProfileSpecificStorage.CrimsonIsleStorage) {
        if (DailyQuestHelper.greatSpook) return
        if (storage.quests.toList().any { hasGreatSpookLine(it) }) {
            DailyQuestHelper.greatSpook = true
            return
        }
        for (text in storage.quests.toList()) {
            val split = text.split(":")
            val name = split[0]
            val state = if (split[1] == "NOT_ACCEPTED") {
                QuestState.ACCEPTED
            } else {
                QuestState.valueOf(split[1])
            }
            val needAmount = split[2].toInt()
            val quest = addQuest(name, state, needAmount)
            if (quest is UnknownQuest) {
                DailyQuestHelper.quests.clear()
                storage.quests.clear()
                println("Reset crimson isle quest data from the config because the config was invalid!")
                return
            }
            if (quest is ProgressQuest && split.size == 4) {
                try {
                    val haveAmount = split[3].toInt()
                    quest.haveAmount = haveAmount
                } catch (e: IndexOutOfBoundsException) {
                    ErrorManager.logErrorWithData(
                        e,
                        "Error loading Crimson Isle Quests from config.",
                        "text" to text,
                    )
                }
            }
            addQuest(quest)
        }
    }

    private fun hasGreatSpookLine(text: String) = when {
        text.contains("The Great Spook") -> true
        text.contains(" Days") -> true
        text.contains("Fear: §r") -> true
        text.contains("Primal Fears") -> true

        else -> false
    }

    private fun addQuest(element: Quest) {
        DailyQuestHelper.quests.add(element)
        if (DailyQuestHelper.quests.size > 5) {
            CrimsonIsleReputationHelper.reset()
        }
    }
}
