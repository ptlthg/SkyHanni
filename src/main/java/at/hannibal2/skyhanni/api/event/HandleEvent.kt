package at.hannibal2.skyhanni.api.event

import at.hannibal2.skyhanni.data.IslandType
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class HandleEvent(
    /**
     * For cases where the event properties are themselves not needed, and solely a listener for an event fire suffices.
     * To specify multiple events, use [eventTypes] instead.
     */
    val eventType: KClass<out SkyHanniEvent> = SkyHanniEvent::class,

    /**
     * For cases where multiple events are listened to, and properties are unnecessary.
     * To specify only one event, use [eventType] instead.
     */
    val eventTypes: Array<KClass<out SkyHanniEvent>> = [],

    /**
     * If the event should only be received while on SkyBlock.
     */
    val onlyOnSkyblock: Boolean = false,

    /**
     * If the event should only be received while on a specific skyblock island.
     * To specify multiple islands, use [onlyOnIslands] instead.
     */
    val onlyOnIsland: IslandType = IslandType.ANY,

    /**
     * If the event should only be received while being on specific skyblock islands.
     * To specify only one island, use [onlyOnIsland] instead.
     */
    vararg val onlyOnIslands: IslandType = [],

    /**
     * The priority of when the event will be called, lower priority will be called first, see the companion object.
     */
    val priority: Int = 0,

    /**
     * If the event is cancelled & receiveCancelled is true, then the method will still invoke.
     */
    val receiveCancelled: Boolean = false,
) {
    companion object {
        const val HIGHEST = -2 // First to execute
        const val HIGH = -1
        const val LOW = 1
        const val LOWEST = 2 // Last to execute
    }
}
