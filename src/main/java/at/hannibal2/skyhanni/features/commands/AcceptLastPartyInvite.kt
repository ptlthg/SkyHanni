package at.hannibal2.skyhanni.features.commands

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.MessageSendToServerEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ChatUtils.senderIsSkyhanni
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.RegexUtils.findMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object AcceptLastPartyInvite {

    private val config get() = SkyHanniMod.feature.misc.commands

    private val patternGroup = RepoPattern.group("party.invite")

    /**
     * REGEX-TEST: §r§b[MVP§r§c+§r§b] STPREAPER §r§ehas invited you to join their party!
     * REGEX-TEST: §r§a[VIP] VrxyOwnsYou_ §r§ehas invited you to join their party!
     * REGEX-TEST: §r§7SkyLime1213 §r§ehas invited you to join their party!
     */
    private val inviteReceivedPattern by patternGroup.pattern(
        "received",
        "§r§.(?:\\[.*].)?(?<player>\\S+) §r§ehas invited you to join their party!",
    )

    /**
     * REGEX-TEST: §eThe party invite from §r§b[MVP§r§f+§r§b] OE07 §r§ehas expired.
     * REGEX-TEST: §eThe party invite from §r§a[VIP] VrxyOwnsYou_ §r§ehas expired.
     * REGEX-TEST: §eThe party invite from §r§7TMOffline96 §r§ehas expired.
     */
    private val inviteExpiredPattern by patternGroup.pattern(
        "expired",
        "§eThe party invite from §r§.(?:\\[.*].)?(?<player>\\S+) §r§ehas expired\\.",
    )

    // TODO move into PartyApi
    private var lastInviter = ""

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!config.acceptLastInvite) return
        inviteReceivedPattern.findMatcher(event.message) {
            lastInviter = group("player")
            ChatUtils.debug("Party invite received from $lastInviter")
            return
        }
        inviteExpiredPattern.matchMatcher(event.message) {
            if (lastInviter == group("player")) {
                ChatUtils.debug("Party invite from $lastInviter expired")
                lastInviter = ""
                return
            }
        }
    }

    @HandleEvent
    fun onMessageSendToServer(event: MessageSendToServerEvent) {
        if (!config.acceptLastInvite) return
        if (event.senderIsSkyhanni()) return
        val message = event.message.lowercase()
        if (message != "/party accept" && message != "/p accept") return

        event.cancel()
        if (lastInviter == "") {
            ChatUtils.chat("There is no party invite to accept!")
            return
        }
        HypixelCommands.partyAccept(lastInviter)
        lastInviter = ""
    }
}
