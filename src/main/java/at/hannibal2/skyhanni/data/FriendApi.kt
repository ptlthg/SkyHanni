package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigFileType
import at.hannibal2.skyhanni.data.jsonobjects.local.FriendsJson
import at.hannibal2.skyhanni.data.jsonobjects.local.FriendsJson.PlayerFriends.Friend
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.hypixel.HypixelJoinEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.StringUtils.cleanPlayerName
import at.hannibal2.skyhanni.utils.compat.command
import at.hannibal2.skyhanni.utils.compat.hover
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.util.IChatComponent
import java.util.UUID

@SkyHanniModule
object FriendApi {
    private val patternGroup = RepoPattern.group("data.friends")

    /**
     * REGEX-TEST: §r§eYou removed §r§b[MVP§r§d+§r§b] Throwpo§r§e from your friends list!§r§9§m
     */
    private val removedFriendPattern by patternGroup.pattern(
        "remove",
        ".*\n§r§eYou removed §r(?<name>.*)§e from your friends list!§r§9§m\n.*",
    )

    /**
     * REGEX-TEST: §aYou are now friends with §r§b[MVP§r§d+§r§b] Throwpo
     */
    private val addedFriendPattern by patternGroup.pattern(
        "add",
        "§aYou are now friends with (?<name>.*)",
    )

    /**
     * REGEX-TEST: §r§b[MVP§r§c+§r§b] hannibal2§r§e is no longer a best friend!§r§9§m
     */
    private val noBestFriendPattern by patternGroup.pattern(
        "removebest",
        ".*\n§r(?<name>.*)§e is no longer a best friend!§r§9§m\n.*",
    )

    /**
     * REGEX-TEST: §r§b[MVP§r§c+§r§b] hannibal2§r§a is now a best friend!§r§9§m
     */
    private val bestFriendPattern by patternGroup.pattern(
        "addbest",
        ".*\n(?<name>.*)§a is now a best friend!§r§9§m\n.*",
    )

    /**
     * REGEX-TEST: §eClick here to view §bThrowpo§e's profile
     */
    private val rawNamePattern by patternGroup.pattern(
        "rawname",
        "\n§eClick here to view §.(?<name>.*)§e's profile",
    )

    /**
     * REGEX-TEST: /viewprofile 503450fc-72c2-4e87-8243-94e264977437
     */
    private val readFriendListPattern by patternGroup.pattern(
        "readfriends",
        "/viewprofile (?<uuid>.*)",
    )

    private val tempFriends = mutableListOf<Friend>()

    private fun getFriends() = SkyHanniMod.friendsData.players.getOrPut(LorenzUtils.getRawPlayerUuid()) {
        FriendsJson.PlayerFriends().also { it.friends = mutableMapOf() }
    }.friends

    @HandleEvent
    fun onHypixelJoin(event: HypixelJoinEvent) {
        if (SkyHanniMod.friendsData.players == null) {
            SkyHanniMod.friendsData.players = mutableMapOf()
            saveConfig()
        }
    }

    fun getAllFriends(): List<Friend> {
        val list = mutableListOf<Friend>()
        list.addAll(getFriends().values)
        list.addAll(tempFriends)
        return list
    }

    fun saveConfig() {
        SkyHanniMod.configManager.saveConfig(ConfigFileType.FRIENDS, "Save file")
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        readFriendsList(event)

        removedFriendPattern.matchMatcher(event.message) {
            val name = group("name").cleanPlayerName()
            removedFriend(name)
        }
        addedFriendPattern.matchMatcher(event.message) {
            val name = group("name").cleanPlayerName()
            addFriend(name)
        }

        noBestFriendPattern.matchMatcher(event.message) {
            val name = group("name").cleanPlayerName()
            setBestFriend(name, false)
        }
        bestFriendPattern.matchMatcher(event.message) {
            val name = group("name").cleanPlayerName()
            setBestFriend(name, true)
        }
    }

    private fun setBestFriend(name: String, bestFriend: Boolean) {
        getFriends().entries.firstOrNull { it.value.name == name }?.let {
            it.value.bestFriend = bestFriend
            saveConfig()
        }
    }

    private fun addFriend(name: String) {
        tempFriends.add(Friend().also { it.name = name })
    }

    private fun removedFriend(name: String) {
        tempFriends.removeIf { it.name == name }
        getFriends().entries.removeIf { it.value.name == name }
        saveConfig()
    }

    private fun readFriendsList(event: SkyHanniChatEvent) {
        if (!event.message.contains("Friends")) return

        for (sibling in event.chatComponent.siblings) {
            val chatStyle = sibling.chatStyle ?: continue
            val value = sibling.command ?: continue
            if (!value.startsWith("/viewprofile")) continue

            val uuid = readFriendListPattern.matchMatcher(value) {
                group("uuid")?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: IllegalArgumentException) {
                        ErrorManager.logErrorWithData(
                            e, "Error reading friend list.",
                            "raw uuid" to it,
                            "value" to value,
                            "chatStyle" to chatStyle,
                            "event.chatComponent" to event.chatComponent,
                            "event.message" to event.message,
                        )
                        return
                    }
                }
            }
            val bestFriend = sibling.unformattedText.contains("§l")
            val name = readName(sibling)
            if (uuid != null && name != null) {
                getFriends()[uuid] = Friend().also {
                    it.name = name
                    it.bestFriend = bestFriend
                }
            }
        }

        saveConfig()
    }

    private fun readName(chatComponent: IChatComponent): String? {
        val hoverEventSiblings = chatComponent.hover?.siblings ?: return null
        for (component in hoverEventSiblings) {
            val rawName = component.unformattedText
            rawNamePattern.matchMatcher(rawName) {
                return group("name").cleanPlayerName()
            }
        }

        return null
    }
}
