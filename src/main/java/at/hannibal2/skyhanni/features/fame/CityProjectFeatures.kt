package at.hannibal2.skyhanni.features.fame

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.data.EntityMovementData
import at.hannibal2.skyhanni.data.IslandGraphs
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.features.inventory.bazaar.BazaarApi
import at.hannibal2.skyhanni.features.misc.IslandAreas
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.InventoryUtils.getUpperItems
import at.hannibal2.skyhanni.utils.ItemPriceUtils.getPrice
import at.hannibal2.skyhanni.utils.ItemUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.NeuItems.getItemStack
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderable
import at.hannibal2.skyhanni.utils.SignUtils
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.TimeUtils
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addItemStack
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addString
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.gui.inventory.GuiEditSign
import net.minecraft.inventory.ContainerChest
import net.minecraft.item.ItemStack
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object CityProjectFeatures {

    private val config get() = SkyHanniMod.feature.event.cityProject

    private var display: Renderable? = null
    private var inInventory = false
    private var lastReminderSend = SimpleTimeMark.farPast()

    private val patternGroup = RepoPattern.group("fame.projects")
    private val contributeAgainPattern by patternGroup.pattern(
        "contribute",
        "§7Contribute again: §e(?<time>.*)",
    )
    private val completedPattern by patternGroup.pattern(
        "completed",
        "§aProject is (?:being built|released)!",
    )

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!config.dailyReminder) return
        val playerSpecific = ProfileStorageData.playerSpecific ?: return
        if (ReminderUtils.isBusy()) return

        if (IslandAreas.currentAreaName == "Community Center") return

        playerSpecific.nextCityProjectParticipationTime.let {
            if (it.isFarPast() || it.isInFuture()) return
        }
        if (lastReminderSend.passedSince() < 30.seconds) return
        lastReminderSend = SimpleTimeMark.now()

        ChatUtils.clickToActionOrDisable(
            "Daily City Project Reminder!",
            config::dailyReminder,
            actionName = "warp to Elizabeth",
            action = {
                HypixelCommands.warp("elizabeth")
                EntityMovementData.onNextTeleport(IslandType.HUB) {
                    IslandGraphs.pathFind(
                        LorenzVec(-1.7, 72.0, -102.0),
                        "§aCity Project",
                        condition = { config.dailyReminder },
                    )
                }
            },
        )
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        inInventory = false
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {

        inInventory = false
        if (!inCityProject(event)) return
        inInventory = true

        if (config.showMaterials) {
            // internal name -> amount
            val materials = mutableMapOf<NeuInternalName, Int>()
            for ((_, item) in event.inventoryItems) {
                if (item.displayName != "§eContribute this component!") continue
                fetchMaterials(item, materials)
            }

            display = buildDisplay(materials)
        }

        if (config.showReady) {
            var nextTime = SimpleTimeMark.farFuture()
            val now = SimpleTimeMark.now()
            for ((_, item) in event.inventoryItems) {

                val lore = item.getLore()
                val completed = lore.lastOrNull()?.let { completedPattern.matches(it) } ?: false
                if (completed) continue
                contributeAgainPattern.firstMatcher(lore) {
                    val rawTime = group("time")
                    val duration = if (rawTime.contains("Soon!")) {
                        5.seconds
                    } else {
                        // hypixel rounds down to the next full minute, it shows "1m" when it is in fact 1-2 minutes, and "0m" for the last 60s
                        TimeUtils.getDuration(rawTime).let {
                            if (it < 1.hours) it + 1.minutes else it
                        }
                    }
                    val endTime = now + duration
                    if (endTime < nextTime) {
                        nextTime = endTime
                    }
                }
                if (item.displayName != "§eContribute this component!") continue
                nextTime = now
            }
            ProfileStorageData.playerSpecific?.nextCityProjectParticipationTime = nextTime
        }
    }

    private fun inCityProject(event: InventoryFullyOpenedEvent): Boolean {
        val lore = event.inventoryItems[4]?.getLore() ?: return false
        if (lore.isEmpty()) return false
        if (lore[0] != "§8City Project") return false
        return true
    }

    private fun buildDisplay(materials: MutableMap<NeuInternalName, Int>) = Renderable.vertical {
        addString("§7City Project Materials")

        if (materials.isEmpty()) {
            addString("§cNo Materials to contribute.")
        } else {
            for ((internalName, amount) in materials) {
                add(materialRow(internalName, amount))
            }
        }

    }

    private fun materialRow(internalName: NeuInternalName, amount: Int): Renderable {
        val stack = internalName.getItemStack()
        val name = internalName.repoItemName
        val price = internalName.getPrice() * amount

        return Renderable.line {
            addString(" §7- ")
            addItemStack(stack)
            add(materialLink(name, amount))
            addString(" §7(§6${price.shortFormat()}§7)")
        }
    }

    private fun materialLink(name: String, amount: Int): Renderable = Renderable.optionalLink(
        "$name §ex${amount.addSeparators()}",
        {
            if (Minecraft.getMinecraft().currentScreen is GuiEditSign) {
                SignUtils.setTextIntoSign("$amount")
            } else {
                BazaarApi.searchForBazaarItem(name, amount)
            }
        },
    ) { inInventory && !NeuItems.neuHasFocus() }

    private fun fetchMaterials(item: ItemStack, materials: MutableMap<NeuInternalName, Int>) {
        var next = false
        val lore = item.getLore()
        val completed = lore.lastOrNull()?.let { completedPattern.matches(it) } ?: false
        if (completed) return
        // TODO: Refactor this loop to not have so many jumps
        @Suppress("LoopWithTooManyJumpStatements")
        for (line in lore) {
            if (line == "§7Cost") {
                next = true
                continue
            }
            if (!next) continue
            if (line == "" || line.contains("Bits")) break

            val (name, amount) = ItemUtils.readItemAmount(line) ?: continue
            val internalName = NeuInternalName.fromItemName(name)
            val old = materials.getOrPut(internalName) { 0 }
            materials[internalName] = old + amount
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onBackgroundDraw(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (!config.showMaterials) return
        if (!inInventory) return

        config.pos.renderRenderable(display, posLabel = "City Project Materials")
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onBackgroundDrawn(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (!config.showReady) return
        if (!inInventory) return

        if (event.gui !is GuiChest) return
        val chest = event.container as ContainerChest

        for ((slot, stack) in chest.getUpperItems()) {
            val lore = stack.getLore()
            if (lore.isEmpty()) continue
            val last = lore.last()
            if (last == "§eClick to contribute!") {
                slot.highlight(LorenzColor.YELLOW)
            }
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(2, "misc.cityProject", "event.cityProject")
    }
}
