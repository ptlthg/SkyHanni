package at.hannibal2.skyhanni.features.inventory.chocolatefactory

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandTypeTags
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.render.gui.ReplaceItemEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.ItemUtils
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkullTextureHolder
import net.minecraft.client.player.inventory.ContainerLocalMenu
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object CFShortcut {

    private val config get() = CFApi.config
    private var showItem = false
    private var lastClick = SimpleTimeMark.farPast()

    private val slotId get() = CFApi.cfShortcutIndex

    private val item by lazy {
        ItemUtils.createSkull(
            displayName = "§6Open Chocolate Factory",
            uuid = "d7ac85e6-bd40-359e-a2c5-86082959309e",
            value = SkullTextureHolder.getTexture("CHOC_FAC_SHORTCUT"),
            "§8(From SkyHanni)",
            "",
            "§7Click here to run",
            "§e/chocolatefactory"
        )
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        if (IslandTypeTags.HOPPITY_DISALLOWED.inAny()) return
        showItem = config.hoppityMenuShortcut && event.inventoryName == "SkyBlock Menu"
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        showItem = false
    }

    @HandleEvent
    fun replaceItem(event: ReplaceItemEvent) {
        if (event.inventory is ContainerLocalMenu && showItem && event.slot == slotId) {
            event.replace(item)
        }
    }

    @HandleEvent(priority = HandleEvent.HIGH)
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        if (!showItem || event.slotId != slotId) return
        event.cancel()
        if (lastClick.passedSince() > 2.seconds) {
            HypixelCommands.chocolateFactory()
            lastClick = SimpleTimeMark.now()
        }
    }
}
