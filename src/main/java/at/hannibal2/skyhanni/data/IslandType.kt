package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.api.event.HandleEvent.Companion.HIGHEST
import at.hannibal2.skyhanni.data.jsonobjects.repo.IslandTypeJson
import at.hannibal2.skyhanni.events.RepositoryReloadEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
//#if TODO
import at.hannibal2.skyhanni.utils.LocationUtils.isInside
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.SkyBlockUtils
//#endif
import net.minecraft.util.AxisAlignedBB

enum class IslandType(private val nameFallback: String) {
    PRIVATE_ISLAND("Private Island"),
    PRIVATE_ISLAND_GUEST("Private Island Guest"),
    THE_END("The End"),
    KUUDRA_ARENA("Kuudra"),
    CRIMSON_ISLE("Crimson Isle"),
    DWARVEN_MINES("Dwarven Mines"),
    DUNGEON_HUB("Dungeon Hub"),
    CATACOMBS("Catacombs"),

    HUB("Hub"),
    DARK_AUCTION("Dark Auction"),
    THE_FARMING_ISLANDS("The Farming Islands"),
    CRYSTAL_HOLLOWS("Crystal Hollows"),
    THE_PARK("The Park"),
    DEEP_CAVERNS("Deep Caverns"),
    GOLD_MINES("Gold Mine"),
    GARDEN("Garden"),
    GARDEN_GUEST("Garden Guest"),
    SPIDER_DEN("Spider's Den"),
    WINTER("Jerry's Workshop"),
    THE_RIFT("The Rift"),
    MINESHAFT("Mineshaft"),
    BACKWATER_BAYOU("Backwater Bayou"),

    NONE(""),
    ANY(""),
    UNKNOWN("???"),
    ;

    fun isValidIsland(): Boolean = when (this) {
        NONE,
        ANY,
        UNKNOWN,
        -> false

        else -> true
    }

    fun guestVariant(): IslandType = when (this) {
        PRIVATE_ISLAND -> PRIVATE_ISLAND_GUEST
        GARDEN -> GARDEN_GUEST
        else -> this
    }

    // TODO: IslandTags
    fun hasGuestVariant(): Boolean = when (this) {
        PRIVATE_ISLAND, GARDEN -> true
        else -> false
    }

    var islandData: IslandData? = null
        private set

    val displayName: String get() = islandData?.name ?: nameFallback

    //#if TODO
    fun isInBounds(vec: LorenzVec): Boolean = islandData?.boundingBox?.isInside(vec) ?: true
    //#endif

    @SkyHanniModule
    companion object {
        /**
         * The maximum amount of players that can be on an island.
         */
        var maxPlayers = 24
            private set

        /**
         * The maximum amount of players that can be on a mega hub.
         */
        var maxPlayersMega = 80
            private set

        fun getByName(name: String): IslandType = getByNameOrNull(name) ?: error("IslandType not found: '$name'")
        fun getByNameOrUnknown(name: String): IslandType = getByNameOrNull(name) ?: UNKNOWN
        fun getByNameOrNull(name: String): IslandType? = entries.find { it.displayName == name }

        fun getByIdOrNull(id: String): IslandType? = entries.find { it.islandData?.apiName == id }
        fun getByIdOrUnknown(id: String): IslandType = getByIdOrNull(id) ?: UNKNOWN

        @HandleEvent(priority = HIGHEST)
        fun onRepoReload(event: RepositoryReloadEvent) {
            val data = event.getConstant<IslandTypeJson>("misc/IslandType")

            val islandDataMap = data.islands.mapValues {
                val island = it.value
                val boundingBox = island.bounds?.let { bounds ->
                    AxisAlignedBB(
                        bounds.minX.toDouble(), 0.0, bounds.minZ.toDouble(),
                        bounds.maxX.toDouble(), 256.0, bounds.maxZ.toDouble(),
                    )
                }

                IslandData(island.name, island.apiName, island.maxPlayers ?: data.maxPlayers, boundingBox)
            }

            entries.forEach { islandType ->
                islandType.islandData = islandDataMap[islandType.name]
            }

            maxPlayers = data.maxPlayers
            maxPlayersMega = data.maxPlayersMega
        }
    }

    //#if TODO
    // TODO rename to isInIsland once the funciton in lorenz utils is gone
    fun isCurrent() = SkyBlockUtils.inSkyBlock && SkyBlockUtils.currentIsland == this
    //#endif
}

data class IslandData(
    val name: String,
    val apiName: String?,
    val maxPlayers: Int,
    val boundingBox: AxisAlignedBB?,
)
