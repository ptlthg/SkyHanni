package at.hannibal2.skyhanni.features.misc

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.hypixel.HypixelJoinEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object AutoJoinSkyblock {

    private var lastJoin = SimpleTimeMark.farPast()

    @HandleEvent
    fun onHypixelJoin(event: HypixelJoinEvent) {
        if (!SkyHanniMod.feature.misc.autoJoinSkyblock) return
        if (lastJoin.passedSince() < 30.seconds) return
        lastJoin = SimpleTimeMark.now()

        DelayedRun.runDelayed(1.seconds) {
            HypixelCommands.skyblock()
        }
    }
}
