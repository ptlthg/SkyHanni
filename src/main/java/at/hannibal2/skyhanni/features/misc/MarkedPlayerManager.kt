package at.hannibal2.skyhanni.features.misc

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.enums.OutsideSBFeature
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.mixins.hooks.RenderLivingEntityHelper
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ColorUtils.addAlpha
import at.hannibal2.skyhanni.utils.ConditionalUtils.onToggle
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import net.minecraft.client.entity.EntityOtherPlayerMP

@SkyHanniModule
object MarkedPlayerManager {

    val config get() = SkyHanniMod.feature.gui.markedPlayers

    private val playerNamesToMark = mutableListOf<String>()
    private val markedPlayers = mutableMapOf<String, EntityOtherPlayerMP>()

    private fun command(args: Array<String>) {
        if (args.size != 1) {
            ChatUtils.userError("Usage: /shmarkplayer <name>")
            return
        }

        val displayName = args[0]
        val name = displayName.lowercase()

        if (name == LorenzUtils.getPlayerName().lowercase()) {
            ChatUtils.userError("You can't add or remove yourself this way! Go to the settings and toggle 'Mark your own name'.")
            return
        }

        if (name !in playerNamesToMark) {
            playerNamesToMark.add(name)
            findPlayers()
            ChatUtils.chat("§aMarked §eplayer §b$displayName§e!")
        } else {
            playerNamesToMark.remove(name)
            markedPlayers[name]?.let { RenderLivingEntityHelper.removeCustomRender(it) }
            markedPlayers.remove(name)
            ChatUtils.chat("§cUnmarked §eplayer §b$displayName§e!")
        }
    }

    private fun findPlayers() {
        for (entity in EntityUtils.getEntities<EntityOtherPlayerMP>()) {
            if (entity in markedPlayers.values) continue

            val name = entity.name.lowercase()
            if (name in playerNamesToMark) {
                markedPlayers[name] = entity
                entity.setColor()
            }
        }
    }

    private fun refreshColors() =
        markedPlayers.forEach {
            it.value.setColor()
        }

    private fun EntityOtherPlayerMP.setColor() {
        RenderLivingEntityHelper.setEntityColorWithNoHurtTime(
            this,
            config.entityColor.get().toColor().addAlpha(127),
            ::isEnabled,
        )
    }

    fun isMarkedPlayer(player: String): Boolean = player.lowercase() in playerNamesToMark

    private fun isEnabled() = (LorenzUtils.inSkyBlock || OutsideSBFeature.MARKED_PLAYERS.isSelected()) &&
        config.highlightInWorld

    fun replaceInChat(string: String): String {
        if (!config.highlightInChat) return string

        val color = config.chatColor.getChatColor()
        var text = string
        for (markedPlayer in playerNamesToMark) {
            text = text.replace(markedPlayer, "$color$markedPlayer§r")
        }
        return text
    }

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        config.markOwnName.whenChanged { _, new ->
            val name = LorenzUtils.getPlayerName()
            if (new) {
                if (!playerNamesToMark.contains(name)) {
                    playerNamesToMark.add(name)
                }
            } else {
                playerNamesToMark.remove(name)
            }
        }
        config.entityColor.onToggle(::refreshColors)
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!isEnabled()) return

        findPlayers()
    }

    @HandleEvent
    fun onWorldChange() {
        if (!MinecraftCompat.localPlayerExists) return

        markedPlayers.clear()
        if (config.markOwnName.get()) {
            val name = LorenzUtils.getPlayerName()
            if (!playerNamesToMark.contains(name)) {
                playerNamesToMark.add(name)
            }
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(31, "markedPlayers", "gui.markedPlayers")
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.register("shmarkplayer") {
            description = "Add a highlight effect to a player for better visibility"
            callback { command(it) }
        }
    }
}
