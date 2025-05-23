package at.hannibal2.skyhanni.features.mining.fossilexcavator

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.ItemAddManager
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.ItemAddEvent
import at.hannibal2.skyhanni.events.mining.FossilExcavationEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatPercentage
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.StringUtils
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addSearchString
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.Searchable
import at.hannibal2.skyhanni.utils.renderables.toSearchable
import at.hannibal2.skyhanni.utils.tracker.ItemTrackerData
import at.hannibal2.skyhanni.utils.tracker.SkyHanniItemTracker
import at.hannibal2.skyhanni.utils.tracker.SkyHanniTracker
import com.google.gson.annotations.Expose
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiChest

@SkyHanniModule
object ExcavatorProfitTracker {

    private val config get() = SkyHanniMod.feature.mining.fossilExcavator.profitTracker

    private val tracker = SkyHanniItemTracker(
        "Fossil Excavation Profit Tracker",
        { Data() },
        { it.mining.fossilExcavatorProfitTracker },
    ) { drawDisplay(it) }

    class Data : ItemTrackerData() {
        override fun resetItems() {
            timesExcavated = 0
            glacitePowderGained = 0
            fossilDustGained = 0
        }

        override fun getDescription(timesGained: Long): List<String> {
            val percentage = timesGained.toDouble() / timesExcavated
            val dropRate = percentage.coerceAtMost(1.0).formatPercentage()
            return listOf(
                "§7Dropped §e${timesGained.addSeparators()} §7times.",
                "§7Your drop rate: §c$dropRate.",
            )
        }

        override fun getCoinName(item: TrackedItem) = "<no coins>"

        override fun getCoinDescription(item: TrackedItem): List<String> {
            return listOf(
                "<no coins>",
            )
        }

        @Expose
        var timesExcavated = 0L

        @Expose
        var glacitePowderGained = 0L

        @Expose
        var fossilDustGained = 0L
    }

    private val scrapItem get() = FossilExcavatorApi.scrapItem

    private fun drawDisplay(data: Data): List<Searchable> = buildList {
        addSearchString("§e§lFossil Excavation Profit Tracker")
        var profit = tracker.drawItems(data, { true }, this)

        val timesExcavated = data.timesExcavated
        add(
            Renderable.hoverTips(
                "§7Times excavated: §e${timesExcavated.addSeparators()}",
                listOf("§7You excavated §e${timesExcavated.addSeparators()} §7times."),
            ).toSearchable(),
        )

        profit = addScrap(timesExcavated, profit)
        if (config.showFossilDust) {
            profit = addFossilDust(data.fossilDustGained, profit)
        }
        if (config.trackGlacitePowder) {
            addGlacitePowder(data)
        }

        add(tracker.addTotalProfit(profit, data.timesExcavated, "excavation"))

        tracker.addPriceFromButton(this)
    }

    private fun MutableList<Searchable>.addFossilDust(
        fossilDustGained: Long,
        profit: Double,
    ): Double {
        if (fossilDustGained <= 0) return profit
        val pricePer = SkyHanniTracker.getPricePer(scrapItem) / 500
        val fossilDustPrice = pricePer * fossilDustGained
        add(
            Renderable.hoverTips(
                "§7${fossilDustGained.shortFormat()}x §fFossil Dust§7: §6${fossilDustPrice.shortFormat()}",
                listOf(
                    "§7You gained §6${fossilDustPrice.shortFormat()} coins §7in total",
                    "§7for all §e$fossilDustGained §fFossil Dust",
                    "§7you have collected.",
                    "",
                    "§7Price Per Fossil Dust: §6${pricePer.shortFormat()}",
                ),
            ).toSearchable("Fossil Dust"),
        )
        return profit + fossilDustPrice
    }

    private fun MutableList<Searchable>.addGlacitePowder(data: Data) {
        val glacitePowderGained = data.glacitePowderGained
        if (glacitePowderGained <= 0) return
        add(
            Renderable.hoverTips(
                "§bGlacite Powder§7: §e${glacitePowderGained.addSeparators()}",
                listOf(
                    "§7No real profit,",
                    "§7but still nice to see! Right?",
                ),
            ).toSearchable("Glacite Powder"),
        )
    }

    private fun MutableList<Searchable>.addScrap(
        timesExcavated: Long,
        profit: Double,
    ): Double {
        if (timesExcavated <= 0) return profit
        // TODO use same price source as profit tracker
        val scrapPrice = timesExcavated * SkyHanniTracker.getPricePer(scrapItem)
        val name = StringUtils.pluralize(timesExcavated.toInt(), scrapItem.repoItemName)
        add(
            Renderable.hoverTips(
                "$name §7price: §c-${scrapPrice.shortFormat()}",
                listOf(
                    "§7You paid §c${scrapPrice.shortFormat()} coins §7in total",
                    "§7for all §e$timesExcavated $name",
                    "§7you have used.",
                ),
            ).toSearchable("Scrap"),
        )
        return profit - scrapPrice
    }

    @HandleEvent
    fun onItemAdd(event: ItemAddEvent) {
        if (!config.enabled) return
        if (!isEnabled()) return

        val internalName = event.internalName
        if (event.source == ItemAddManager.Source.COMMAND) {
            tryAddItem(internalName, event.amount, command = true)
        }
    }

    private fun tryAddItem(internalName: NeuInternalName, amount: Int, command: Boolean) {
        tracker.addItem(internalName, amount, command)
    }

    @HandleEvent
    fun onFossilExcavation(event: FossilExcavationEvent) {
        if (!isEnabled()) return
        for ((name, amount) in event.loot) {
            addItem(name, amount)
        }
        tracker.modify {
            it.timesExcavated++
        }
    }

    private fun addItem(name: String, amount: Int) {
        if (name == "§bGlacite Powder") {
            if (config.trackGlacitePowder) {
                tracker.modify {
                    it.glacitePowderGained += amount
                }
            }
            return
        }
        if (name == "§fFossil Dust") {
            if (config.showFossilDust) {
                tracker.modify {
                    it.fossilDustGained += amount
                }
            }
            return
        }

        val internalName = NeuInternalName.fromItemNameOrNull(name)
        if (internalName == null) {
            ChatUtils.debug("no price for excavator profit: '$name'")
            return
        }
        // TODO use primitive item stacks in trackers
        tryAddItem(internalName, amount, command = false)
    }

    init {
        tracker.initRenderer({ config.position }) { shouldShowDisplay() }
    }

    private fun shouldShowDisplay(): Boolean {
        if (!config.enabled) return false
        if (!isEnabled()) return false
        val inChest = Minecraft.getMinecraft().currentScreen is GuiChest
        // Only show in excavation menu
        if (inChest && !FossilExcavatorApi.inExcavatorMenu) return false

        return true
    }

    @HandleEvent
    fun onIslandChange(event: IslandChangeEvent) {
        if (event.newIsland == IslandType.DWARVEN_MINES) {
            tracker.firstUpdate()
        }
    }

    private fun isEnabled() = IslandType.DWARVEN_MINES.isInIsland() && LorenzUtils.skyBlockArea == "Fossil Research Center"

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.register("shresetexcavatortracker") {
            description = "Resets the Fossil Excavator Profit Tracker"
            category = CommandCategory.USERS_RESET
            callback { tracker.resetCommand() }
        }
    }
}
