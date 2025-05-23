package at.hannibal2.skyhanni.features.rift.everywhere.motes

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.features.rift.motes.RiftInventoryValueConfig.NumberFormatEntry
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniTickEvent
import at.hannibal2.skyhanni.events.minecraft.ToolTipEvent
import at.hannibal2.skyhanni.features.rift.RiftApi
import at.hannibal2.skyhanni.features.rift.RiftApi.motesNpcPrice
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils.chat
import at.hannibal2.skyhanni.utils.ConfigUtils
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuItems.getItemStack
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addItemStack
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addString
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.RenderableUtils.addRenderableButton
import at.hannibal2.skyhanni.utils.renderables.addLine
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object ShowMotesNpcSellPrice {

    private val config get() = RiftApi.config.motes

    private val burgerPattern by RepoPattern.pattern(
        "rift.everywhere.burger",
        ".*(?:§\\w)+You have (?:§\\w)+(?<amount>\\d) Grubber Stacks.*",
    )

    private var display = emptyList<Renderable>()
    private val itemMap = mutableMapOf<NeuInternalName, Pair<MutableList<Int>, Double>>()
    private var inInventory = false
    private val slotList = mutableListOf<Int>()

    @HandleEvent
    fun onBackgroundDraw(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (!isInventoryValueEnabled()) return
        if (inInventory) {
            config.inventoryValue.position.renderRenderables(
                display,
                posLabel = "Inventory Motes Value",
            )
        }
    }

    @HandleEvent
    fun onTick(event: SkyHanniTickEvent) {
        if (!isInventoryValueEnabled()) return
        if (!event.isMod(10, 1)) return
        processItems()
    }

    @HandleEvent
    fun onToolTip(event: ToolTipEvent) {
        if (!isShowPriceEnabled()) return

        val itemStack = event.itemStack

        val baseMotes = itemStack.motesNpcPrice() ?: return
        val burgerStacks = config.burgerStacks
        val burgerText = if (burgerStacks > 0) "(${burgerStacks}x≡) " else ""
        val size = itemStack.stackSize
        if (size > 1) {
            event.toolTip.add(
                "§6NPC price: $burgerText§d${baseMotes.addSeparators()} Motes " +
                    "§7($size x §d${(baseMotes / size).addSeparators()} Motes§7)",
            )
        } else {
            event.toolTip.add("§6NPC price: $burgerText§d${baseMotes.addSeparators()} Motes")
        }
    }

    @HandleEvent
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        reset()
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        reset()
    }

    private fun reset() {
        if (!isInventoryValueEnabled()) return
        itemMap.clear()
        slotList.clear()
        inInventory = false
    }

    private fun processItems() {
        val inventoryName = InventoryUtils.openInventoryName()
        if (!inventoryName.contains("Rift Storage")) return
        val stacks = InventoryUtils.getItemsInOpenChest().map { it.slotIndex to it.stack }
        itemMap.clear()
        for ((index, stack) in stacks) {
            val itemValue = stack.motesNpcPrice() ?: continue
            val internalName = stack.getInternalName()
            if (itemMap.contains(internalName)) {
                val (oldIndex, oldValue) = itemMap[internalName] ?: return
                oldIndex.add(index)
                itemMap[internalName] = Pair(oldIndex, oldValue + itemValue)
            } else {
                itemMap[internalName] = Pair(mutableListOf(index), itemValue)
            }
        }
        inInventory = true
        update()
    }

    @HandleEvent(onlyOnIsland = IslandType.THE_RIFT)
    fun onChat(event: SkyHanniChatEvent) {
        burgerPattern.matchMatcher(event.message) {
            config.burgerStacks = group("amount").toInt()
            chat("Set your McGrubber's burger stacks to ${group("amount")}.")
        }
    }

    private fun update() {
        display = drawDisplay()
    }

    private fun drawDisplay() = buildList {
        addString("§7Item Values:")
        val sorted = itemMap.toList().sortedByDescending { it.second.second }.toMap().toMutableMap()

        for ((internalName, pair) in sorted) {
            val (index, value) = pair
            val stack = internalName.getItemStack()
            val valuePer = stack.motesNpcPrice() ?: continue
            val price = value.formatPrice()
            addLine {
                addString("  §7- ")
                addItemStack(stack)
                val tips = buildList {
                    add("§6Item: ${stack.displayName}")
                    add("§6Value per: §d$valuePer Motes")
                    add("§6Total in chest: §d${(value / valuePer).toInt()}")
                    add("")
                    add("§6Total value: §d$price coins")
                }
                add(
                    Renderable.hoverTips(
                        "§6${stack.displayName}: §b$price",
                        tips,
                        highlightsOnHoverSlots = index,
                        stack = stack,
                    ),
                )
            }
        }
        val total = itemMap.values.fold(0.0) { acc, pair -> acc + pair.second }.formatPrice()
        addString("§7Total price: §b$total")
        addRenderableButton<NumberFormatEntry>(
            label = "Number Format",
            current = config.inventoryValue.formatType.get(),
            onChange = {
                config.inventoryValue.formatType.set(it)
                update()
            },
        )
    }

    private fun Double.formatPrice(): String = when (config.inventoryValue.formatType.get()) {
        NumberFormatEntry.SHORT -> this.shortFormat()
        NumberFormatEntry.LONG -> this.addSeparators()
        else -> "0"
    }

    private fun isShowPriceEnabled() = RiftApi.inRift() && config.showPrice
    private fun isInventoryValueEnabled() = RiftApi.inRift() && config.inventoryValue.enabled

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.transform(15, "rift.motes.inventoryValue.formatType") { element ->
            ConfigUtils.migrateIntToEnum(element, NumberFormatEntry::class.java)
        }
    }
}
