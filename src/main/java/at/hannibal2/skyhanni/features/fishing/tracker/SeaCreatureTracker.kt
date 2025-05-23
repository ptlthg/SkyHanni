package at.hannibal2.skyhanni.features.fishing.tracker

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.ProfileJoinEvent
import at.hannibal2.skyhanni.events.fishing.FishingBobberCastEvent
import at.hannibal2.skyhanni.events.fishing.SeaCreatureFishEvent
import at.hannibal2.skyhanni.features.fishing.FishingApi
import at.hannibal2.skyhanni.features.fishing.SeaCreatureManager
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ConditionalUtils
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatPercentage
import at.hannibal2.skyhanni.utils.StringUtils.allLettersFirstUppercase
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.addOrPut
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.sumAllValues
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addSearchString
import at.hannibal2.skyhanni.utils.renderables.RenderableUtils.addButton
import at.hannibal2.skyhanni.utils.renderables.Searchable
import at.hannibal2.skyhanni.utils.tracker.SkyHanniTracker
import at.hannibal2.skyhanni.utils.tracker.TrackerData
import com.google.gson.annotations.Expose

@SkyHanniModule
object SeaCreatureTracker {
    private var needMigration = true

    private val config get() = SkyHanniMod.feature.fishing.seaCreatureTracker

    private val tracker = SkyHanniTracker("Sea Creature Tracker", { Data() }, { it.fishing.seaCreatureTracker }) {
        drawDisplay(it)
    }

    class Data : TrackerData() {

        override fun reset() {
            amount.clear()
        }

        @Expose
        var amount: MutableMap<String, Int> = mutableMapOf()
    }

    @HandleEvent
    fun onSeaCreatureFish(event: SeaCreatureFishEvent) {
        if (!isEnabled()) return

        tracker.modify {
            val amount = if (event.doubleHook && config.countDouble) 2 else 1
            it.amount.addOrPut(event.seaCreature.name, amount)
        }

        if (config.hideChat) {
            event.chatEvent.blockedReason = "sea_creature_tracker"
        }
    }

    private const val NAME_ALL: CategoryName = "All"
    private var currentCategory: CategoryName = NAME_ALL

    private fun getCurrentCategories(data: Data): Map<CategoryName, Int> {
        val map = mutableMapOf<CategoryName, Int>()
        map[NAME_ALL] = data.amount.size
        for ((category, names) in SeaCreatureManager.allVariants) {
            val amount = names.count { it in data.amount }
            if (amount > 0) {
                map[category] = amount
            }
        }

        return map
    }

    @HandleEvent
    fun onProfileJoin(event: ProfileJoinEvent) {
        needMigration = true
    }

    private fun drawDisplay(data: Data): List<Searchable> = buildList {
        tryToMigrate(data.amount)

        addSearchString("§7Sea Creature Tracker:")

        val filter: (String) -> Boolean = addCategories(data)
        val realAmount = data.amount.filter { filter(it.key) }

        val total = realAmount.sumAllValues()
        for ((name, amount) in realAmount.entries.sortedByDescending { it.value }) {
            val displayName = SeaCreatureManager.allFishingMobs[name]?.displayName ?: run {
                ErrorManager.logErrorStateWithData(
                    "Sea Creature Tracker can not display a name correctly",
                    "Could not find sea creature by name",
                    "SeaCreatureManager.allFishingMobs.keys" to SeaCreatureManager.allFishingMobs.keys,
                    "name" to name,
                )
                name
            }

            val percentageSuffix = if (config.showPercentage.get()) {
                val percentage = (amount.toDouble() / total).formatPercentage()
                " §7$percentage"
            } else ""

            addSearchString(" §7- §e${amount.addSeparators()} $displayName$percentageSuffix", displayName)
        }
        addSearchString(" §7- §e${total.addSeparators()} §7Total Sea Creatures")
    }

    // Hypixel renames sea creatures from time to time. This migration process fixes the invalid config entries.
    private fun tryToMigrate(data: MutableMap<String, Int>) {
        if (!needMigration) return
        needMigration = false

        val map = mutableMapOf(
            "Phlhlegblast" to "Plhlegblast",
            "Sea Emperor" to "The Sea Emperor",
        )

        for ((oldName, newName) in map) {
            // only migrate once the repo contains the new name
            if (SeaCreatureManager.allFishingMobs.containsKey(newName)) {
                data[oldName]?.let {
                    ChatUtils.debug("Sea Creature Tracker migrated $it $oldName to $newName")
                    data[newName] = it + (data[newName] ?: 0)
                    data.remove(oldName)
                }
            }
        }
    }

    private fun MutableList<Searchable>.addCategories(data: Data): (String) -> Boolean {
        val amounts = getCurrentCategories(data)
        val list = amounts.keys.toList()
        if (currentCategory !in list) {
            currentCategory = NAME_ALL
        }

        if (tracker.isInventoryOpen()) {
            addButton<String>(
                label = "Category",
                current = currentCategory,
                getName = { it.allLettersFirstUppercase() + " §7(" + amounts[it] + ")" },
                onChange = {
                    currentCategory = it
                    tracker.update()
                },
                universe = list,
            )
        }

        return if (currentCategory == NAME_ALL) {
            { true }
        } else filterCurrentCategory()
    }

    private fun filterCurrentCategory(): (String) -> Boolean {
        val items = SeaCreatureManager.allVariants[currentCategory] ?: run {
            ErrorManager.logErrorStateWithData(
                "Sea Creature Tracker can not find all sea creature variants",
                "Sea creature variant is not found",
                "SeaCreatureManager.allVariants.keys" to SeaCreatureManager.allVariants.keys,
                "currentCategory" to currentCategory,
            )
            return { true }
        }
        return { it in items }
    }

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        ConditionalUtils.onToggle(config.showPercentage) {
            tracker.update()
        }
    }

    @HandleEvent
    fun onBobberThrow(event: FishingBobberCastEvent) {
        tracker.firstUpdate()
    }

    init {
        tracker.initRenderer({ config.position }) { shouldShowDisplay() }
    }

    private fun shouldShowDisplay(): Boolean {
        if (!config.enabled) return false
        if (!isEnabled()) return false
        if (!FishingApi.isFishing(checkRodInHand = false)) return false

        return true
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.register("shresetseacreaturetracker") {
            description = "Resets the Sea Creature Tracker"
            category = CommandCategory.USERS_RESET
            callback { tracker.resetCommand() }
        }
    }

    private fun isEnabled() =
        LorenzUtils.inSkyBlock && !FishingApi.hasTreasureHook && !FishingApi.wearingTrophyArmor && !LorenzUtils.inKuudraFight
}
