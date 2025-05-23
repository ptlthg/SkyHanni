package at.hannibal2.skyhanni.features.garden.farming

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.ClickType
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.garden.GardenToolChangeEvent
import at.hannibal2.skyhanni.events.garden.farming.CropClickEvent
import at.hannibal2.skyhanni.features.garden.CropType
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getFungiCutterMode
import at.hannibal2.skyhanni.utils.SoundUtils
import net.minecraft.item.ItemStack
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object WrongFungiCutterWarning {

    private var mode = FungiMode.UNKNOWN
    private var lastPlaySoundTime = SimpleTimeMark.farPast()

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        val message = event.message
        if (message == "§eFungi Cutter Mode: §r§cRed Mushrooms") {
            mode = FungiMode.RED
        }
        if (message == "§eFungi Cutter Mode: §r§6Brown Mushrooms") {
            mode = FungiMode.BROWN
        }
    }

    @HandleEvent
    fun onCropClick(event: CropClickEvent) {
        if (event.clickType != ClickType.LEFT_CLICK) return
        if (event.crop != CropType.MUSHROOM) return

        val toString = event.blockState.toString()
        if (toString == "minecraft:red_mushroom" && mode == FungiMode.BROWN) {
            notifyWrong()
        }
        if (toString == "minecraft:brown_mushroom" && mode == FungiMode.RED) {
            notifyWrong()
        }
    }

    private fun notifyWrong() {
        if (!GardenApi.config.fungiCutterWarn) return

        TitleManager.sendTitle("§cWrong Fungi Cutter Mode!", duration = 2.seconds)
        if (lastPlaySoundTime.passedSince() > 300.milliseconds) {
            lastPlaySoundTime = SimpleTimeMark.now()
            SoundUtils.playBeepSound()
        }
    }

    @HandleEvent
    fun onGardenToolChange(event: GardenToolChangeEvent) {
        if (event.crop == CropType.MUSHROOM) {
            readItem(event.toolItem ?: error("Tool item is null"))
        } else {
            mode = FungiMode.UNKNOWN
        }
    }

    private fun readItem(item: ItemStack) {
        // The fungi cutter mode is not set into the item nbt data immediately after purchasing it.
        val rawMode = item.getFungiCutterMode() ?: return
        mode = FungiMode.getOrNull(rawMode)
    }

    enum class FungiMode {
        RED,
        BROWN,
        UNKNOWN,
        ;

        companion object {

            fun getOrNull(mode: String) =
                entries.firstOrNull { it.name == mode } ?: error("Unknown fungi mode: '$mode'")
        }
    }
}
