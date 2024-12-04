package at.hannibal2.skyhanni.features.nether.reputationhelper.dailyquest.quest

enum class QuestState(val displayName: String, val color: String) {
    ACCEPTED("Active", "§b"),
    READY_TO_COLLECT("Ready to collect", "§a"),
    COLLECTED("Collected", "§7"),
}
