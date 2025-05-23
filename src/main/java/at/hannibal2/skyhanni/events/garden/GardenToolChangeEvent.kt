package at.hannibal2.skyhanni.events.garden

import at.hannibal2.skyhanni.api.event.SkyHanniEvent
import at.hannibal2.skyhanni.features.garden.CropType
import net.minecraft.item.ItemStack

class GardenToolChangeEvent(val crop: CropType?, val toolItem: ItemStack?) : SkyHanniEvent()
