package at.hannibal2.skyhanni.features.inventory

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object AuctionOutbidWarning {

    /**
     * REGEX-TEST: §6[Auction] §aMrBaiacu §eoutbid you by §659,083 coins §efor §fFiredust Dagger §e§lCLICK
     */
    private val outbidPattern by RepoPattern.pattern(
        "auction.outbid",
        "§6\\[Auction].*§eoutbid you by.*§e§lCLICK"
    )

    @HandleEvent(onlyOnSkyblock = true)
    fun onChat(event: SkyHanniChatEvent) {
        if (!SkyHanniMod.feature.inventory.auctions.auctionOutbid) return
        if (!outbidPattern.matches(event.message)) return

        TitleManager.sendTitle("§cYou have been outbid!")
        SoundUtils.playBeepSound()
    }
}
