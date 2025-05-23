package at.hannibal2.skyhanni.features.misc.trevor

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.storage.ProfileSpecificStorage
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.editCopy
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addString
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import java.util.regex.Matcher

// TODO change to use skyhanni tracker
@SkyHanniModule
object TrevorTracker {

    private val config get() = SkyHanniMod.feature.misc.trevorTheTrapper

    private val patternGroup = RepoPattern.group("misc.trevor")

    // TODO regex tests
    /**
     * REGEX-TEST: §aYour mob died randomly, you are rewarded §r§53 pelts§r§a.
     */
    private val selfKillMobPattern by patternGroup.pattern(
        "selfkill",
        "§aYour mob died randomly, you are rewarded §r§5(?<pelts>.*) pelts§r§a.",
    )

    /**
     * REGEX-TEST: §aKilling the animal rewarded you §r§53 pelts§r§a.
     */
    private val killMobPattern by patternGroup.pattern(
        "kill",
        "§aKilling the animal rewarded you §r§5(?<pelts>.*) pelts§r§a.",
    )

    private var display = emptyList<Renderable>()

    private val peltsPerSecond = mutableListOf<Int>()
    private var peltsPerHour = 0
    private var stoppedChecks = 0
    private var lastPelts = 0

    fun calculatePeltsPerHour() {
        val storage = ProfileStorageData.profileSpecific?.trapperData ?: return
        val difference = storage.peltsGained - lastPelts
        lastPelts = storage.peltsGained

        if (difference == storage.peltsGained) return

        if (difference == 0) {
            if (peltsPerSecond.isEmpty()) return
            stoppedChecks += 1
        } else {
            if (stoppedChecks > 150) {
                peltsPerSecond.clear()
                peltsPerHour = 0
                stoppedChecks = 0
            }
            while (stoppedChecks > 0) {
                stoppedChecks -= 1
                peltsPerSecond.add(0)
            }
            peltsPerSecond.add(difference)
        }

        peltsPerHour = (peltsPerSecond.average() * 3600).toInt()
    }

    @HandleEvent
    fun onWorldChange() {
        peltsPerSecond.clear()
        peltsPerHour = 0
        stoppedChecks = 0
    }

    // TODO change functionality to use enum rather than ordinals
    private fun formatDisplay(map: List<Renderable>) = config.textFormat.map { map[it.ordinal] }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!TrevorFeatures.onFarmingIsland()) return
        val storage = ProfileStorageData.profileSpecific?.trapperData ?: return

        selfKillMobPattern.matchMatcher(event.message) {
            val pelts = group("pelts").toInt()
            storage.peltsGained += pelts
            storage.selfKillingAnimals += 1
            update()
        }

        killMobPattern.matchMatcher(event.message) {
            val pelts = group("pelts").toInt()
            storage.peltsGained += pelts
            storage.killedAnimals += 1
            update()
        }
    }

    fun startQuest(matcher: Matcher) {
        val storage = ProfileStorageData.profileSpecific?.trapperData ?: return
        storage.questsDone += 1
        val rarity = matcher.group("rarity")
        val foundRarity = TrapperMobRarity.entries.firstOrNull { it.formattedName == rarity } ?: return
        val old = storage.animalRarities[foundRarity] ?: 0
        storage.animalRarities = storage.animalRarities.editCopy { this[foundRarity] = old + 1 }
        update()
    }

    fun update() {
        val storage = ProfileStorageData.profileSpecific?.trapperData ?: return
        display = formatDisplay(drawTrapperDisplay(storage))
    }

    private fun drawTrapperDisplay(storage: ProfileSpecificStorage.TrapperData) = buildList {
        addString("§b§lTrevor Data Tracker")
        addString("§b${storage.questsDone.addSeparators()} §9Quests Started")
        addString("§b${storage.peltsGained.addSeparators()} §5Total Pelts Gained")
        addString("§b${peltsPerHour.addSeparators()} §5Pelts Per Hour")
        addString("")
        addString("§b${storage.killedAnimals.addSeparators()} §cKilled Animals")
        addString("§b${storage.selfKillingAnimals.addSeparators()} §cSelf Killing Animals")
        addString("§b${(storage.animalRarities[TrapperMobRarity.TRACKABLE] ?: 0).addSeparators()} §fTrackable Animals")
        addString("§b${(storage.animalRarities[TrapperMobRarity.UNTRACKABLE] ?: 0).addSeparators()} §aUntrackable Animals")
        addString("§b${(storage.animalRarities[TrapperMobRarity.UNDETECTED] ?: 0).addSeparators()} §9Undetected Animals")
        addString("§b${(storage.animalRarities[TrapperMobRarity.ENDANGERED] ?: 0).addSeparators()} §5Endangered Animals")
        addString("§b${(storage.animalRarities[TrapperMobRarity.ELUSIVE] ?: 0).addSeparators()} §6Elusive Animals")
    }

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!shouldDisplay()) return
        config.position.renderRenderables(display, posLabel = "Trevor Tracker")
    }

    private fun shouldDisplay(): Boolean {
        if (!config.dataTracker) return false
        if (!TrevorFeatures.onFarmingIsland()) return false
        if (TrevorFeatures.inTrapperDen) return true
        return when (config.displayType) {
            true -> (TrevorFeatures.inBetweenQuests || TrevorFeatures.questActive)
            else -> TrevorFeatures.questActive
        }
    }

    enum class TrapperMobRarity(val formattedName: String) {
        TRACKABLE("TRACKABLE"),
        UNTRACKABLE("UNTRACKABLE"),
        UNDETECTED("UNDETECTED"),
        ENDANGERED("ENDANGERED"),
        ELUSIVE("ELUSIVE")
    }
}
