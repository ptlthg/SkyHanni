package at.hannibal2.skyhanni.utils.json

import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.features.fishing.trophy.TrophyRarity
import at.hannibal2.skyhanni.utils.KotlinTypeAdapterFactory
import at.hannibal2.skyhanni.utils.LorenzRarity
//#if TODO
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.NeuInternalName
//#endif
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.system.ModVersion
//#if TODO
import at.hannibal2.skyhanni.utils.tracker.SkyHanniTracker
//#endif
import com.google.gson.GsonBuilder
import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.LegacyStringChromaColourTypeAdapter
import io.github.notenoughupdates.moulconfig.observer.PropertyTypeAdapterFactory
import net.minecraft.item.ItemStack
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration

// todo 1.21 impl needed
object BaseGsonBuilder {
    fun gson(): GsonBuilder = GsonBuilder().setPrettyPrinting()
        .excludeFieldsWithoutExposeAnnotation()
        .serializeSpecialFloatingPointValues()
        .registerTypeAdapterFactory(PropertyTypeAdapterFactory())
        .registerTypeAdapterFactory(KotlinTypeAdapterFactory())
        .registerTypeAdapter(UUID::class.java, SkyHanniTypeAdapters.UUID.nullSafe())
        //#if TODO
        .registerTypeAdapter(LorenzVec::class.java, SkyHanniTypeAdapters.VEC_STRING.nullSafe())
        //#endif
        .registerTypeAdapter(TrophyRarity::class.java, SkyHanniTypeAdapters.TROPHY_RARITY.nullSafe())
        //#if TODO
        .registerTypeAdapter(ItemStack::class.java, SkyHanniTypeAdapters.NEU_ITEMSTACK.nullSafe())
        .registerTypeAdapter(NeuInternalName::class.java, SkyHanniTypeAdapters.INTERNAL_NAME.nullSafe())
        //#endif
        .registerTypeAdapter(LorenzRarity::class.java, SkyHanniTypeAdapters.RARITY.nullSafe())
        .registerTypeAdapter(IslandType::class.java, SkyHanniTypeAdapters.ISLAND_TYPE.nullSafe())
        .registerTypeAdapter(ModVersion::class.java, SkyHanniTypeAdapters.MOD_VERSION.nullSafe())
        .registerTypeAdapter(ChromaColour::class.java, LegacyStringChromaColourTypeAdapter(true).nullSafe())
        //#if TODO
        .registerTypeAdapter(
            SkyHanniTracker.DefaultDisplayMode::class.java,
            SkyHanniTypeAdapters.TRACKER_DISPLAY_MODE.nullSafe(),
        )
        //#endif
        .registerTypeAdapter(SimpleTimeMark::class.java, SkyHanniTypeAdapters.TIME_MARK.nullSafe())
        .registerTypeAdapter(Duration::class.java, SkyHanniTypeAdapters.DURATION.nullSafe())
        .registerTypeAdapter(LocalDate::class.java, SkyHanniTypeAdapters.LOCALE_DATE.nullSafe())
        .enableComplexMapKeySerialization()

    fun lenientGson(): GsonBuilder = gson()
        .registerTypeAdapterFactory(SkippingTypeAdapterFactory)
        .registerTypeAdapterFactory(ListEnumSkippingTypeAdapterFactory)
}
