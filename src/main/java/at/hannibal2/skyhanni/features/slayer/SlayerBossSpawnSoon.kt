package at.hannibal2.skyhanni.features.slayer

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.SlayerApi
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.events.slayer.SlayerProgressChangeEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.NumberUtil.formatDouble
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object SlayerBossSpawnSoon {

    private val config get() = SlayerApi.config.slayerBossWarning

    private val progressPattern by RepoPattern.pattern(
        "slayer.bosswarning.progress",
        " \\(?(?<progress>[0-9.,k]+)/(?<total>[0-9.,k]+)\\)?.*"
    )

    private var lastCompletion = 0.0
    private var warned = false

    @HandleEvent
    fun onSlayerProgressChange(event: SlayerProgressChangeEvent) {
        if (!isEnabled()) return
        if (!SlayerApi.isInCorrectArea) return

        val completion = progressPattern.matchMatcher(event.newProgress.removeColor()) {
            group("progress").formatDouble() / group("total").formatDouble()
        } ?: return

        if (completion > config.percent / 100.0) {
            if (!warned || (config.repeat && completion != lastCompletion)) {
                SoundUtils.playBeepSound()
                TitleManager.sendTitle("§eSlayer boss soon!", duration = 2.seconds)
                warned = true
            }
        } else {
            warned = false
        }
        lastCompletion = completion
    }

    fun isEnabled() = config.enabled && SlayerApi.hasActiveSlayerQuest()
}
