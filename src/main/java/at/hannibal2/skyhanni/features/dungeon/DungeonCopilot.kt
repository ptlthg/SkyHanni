package at.hannibal2.skyhanni.features.dungeon

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.CheckRenderEntityEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.dungeon.DungeonBossRoomEnterEvent
import at.hannibal2.skyhanni.events.dungeon.DungeonEnterEvent
import at.hannibal2.skyhanni.events.dungeon.DungeonStartEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RenderUtils.renderString
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.entity.item.EntityArmorStand

@SkyHanniModule
object DungeonCopilot {

    private val config get() = SkyHanniMod.feature.dungeon.dungeonCopilot

    private val patternGroup = RepoPattern.group("dungeon.copilot")
    private val countdownPattern by patternGroup.pattern(
        "countdown",
        ".* has started the dungeon countdown. The dungeon will begin in 1 minute.",
    )

    /**
     * REGEX-TEST: §a§r§bCalMWolfs§r§a opened a §r§8§lWITHER §r§adoor!
     */
    private val witherDoorPattern by patternGroup.pattern(
        "wither.door",
        ".* opened a §r§8§lWITHER §r§adoor!",
    )
    private val bloodDoorPattern by patternGroup.pattern(
        "blood.door",
        "§cThe §r§c§lBLOOD DOOR§r§c has been opened!",
    )

    private val keyPatternsList = listOf(
        "§eA §r§a§r§[6c]§r§[8c](?<key>Wither|Blood) Key§r§e was picked up!".toPattern(),
        "(.*) §r§ehas obtained §r§a§r§[6c]§r§[8c](?<key>Wither|Blood) Key§r§e!".toPattern(),
    )

    private var nextStep = ""
    private var searchForKey = false

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!isEnabled()) return

        copilot(event.message)?.let {
            event.blockedReason = it
        }
    }

    private fun copilot(message: String): String? {
        countdownPattern.matchMatcher(message) {
            changeNextStep("Ready up")
        }

        if (message.endsWith("§a is now ready!") && message.contains(LorenzUtils.getPlayerName())) {
            changeNextStep("Wait for the dungeon to start!")
        }

        // Key Pickup
        var foundKeyOrDoor = false
        keyPatternsList.any {
            it.matchMatcher(message) {
                val key = group("key")
                changeNextStep("Open $key Door")
                foundKeyOrDoor = true
            } != null
        }

        witherDoorPattern.matchMatcher(message) {
            changeNextStep("Clear next room")
            searchForKey = true
            foundKeyOrDoor = true
        }

        bloodDoorPattern.matchMatcher(message) {
            changeNextStep("Wait for Blood Room to fully spawn")
            foundKeyOrDoor = true
        }

        if (foundKeyOrDoor && SkyHanniMod.feature.dungeon.messageFilter.keysAndDoors) return "dungeon_keys_and_doors"

        if (message == "§c[BOSS] The Watcher§r§f: That will be enough for now.") changeNextStep("Clear Blood Room")

        if (message == "§c[BOSS] The Watcher§r§f: You have proven yourself. You may pass.") {
            if (DungeonApi.getCurrentBoss() == DungeonFloor.E) {
                changeNextStep("")
            } else {
                changeNextStep("Enter Boss Room")
            }
            return "dungeon_copilot"
        }
        return null
    }

    private fun changeNextStep(step: String) {
        nextStep = step
    }

    @HandleEvent(onlyOnIsland = IslandType.CATACOMBS)
    fun onCheckRender(event: CheckRenderEntityEvent<EntityArmorStand>) {
        val entity = event.entity

        if (!searchForKey) return

        if (entity.name == "§6§8Wither Key") {
            changeNextStep("Pick up Wither Key")
            searchForKey = false
        }
        if (entity.name == "§c§cBlood Key") {
            changeNextStep("Pick up Blood Key")
            searchForKey = false
        }
    }

    @HandleEvent
    fun onDungeonStart(event: DungeonStartEvent) {
        changeNextStep("Clear first room")
        searchForKey = true
    }

    @HandleEvent
    fun onDungeonStart(event: DungeonEnterEvent) {
        changeNextStep("Talk to Mort")
        searchForKey = true
    }

    @HandleEvent
    fun onDungeonBossRoomEnter(event: DungeonBossRoomEnterEvent) {
        changeNextStep("Defeat the boss! Good luck :)")
    }

    @HandleEvent
    fun onWorldChange() {
        changeNextStep("")
    }

    private fun isEnabled(): Boolean = DungeonApi.inDungeon() && config.enabled

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!isEnabled()) return

        config.pos.renderString(nextStep, posLabel = "Dungeon Copilot")
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(3, "dungeon.messageFilterKeysAndDoors", "dungeon.messageFilter.keysAndDoors")
        event.move(3, "dungeon.copilotEnabled", "dungeon.dungeonCopilot.enabled")
        event.move(3, "dungeon.copilotPos", "dungeon.dungeonCopilot.pos")
    }
}
