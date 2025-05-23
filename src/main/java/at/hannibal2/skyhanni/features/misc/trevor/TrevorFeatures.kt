package at.hannibal2.skyhanni.features.misc.trevor

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.features.misc.TrevorTheTrapperConfig.TrackerEntry
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.Perk
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.data.mob.MobData
import at.hannibal2.skyhanni.events.CheckRenderEntityEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.minecraft.KeyPressEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.features.misc.IslandAreas
import at.hannibal2.skyhanni.mixins.hooks.RenderLivingEntityHelper
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ColorUtils.addAlpha
import at.hannibal2.skyhanni.utils.ConfigUtils
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.LocationUtils
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.RegexUtils.findMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.RenderUtils.drawString
import at.hannibal2.skyhanni.utils.RenderUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.RenderUtils.renderString
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.TabListData
import at.hannibal2.skyhanni.utils.compat.command
import at.hannibal2.skyhanni.utils.getLorenzVec
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.client.Minecraft
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object TrevorFeatures {
    private val patternGroup = RepoPattern.group("misc.trevor")

    /**
     * REGEX-TEST: [NPC] Trevor: You can find your TRACKABLE animal near the §eDesert Mountain.
     */
    private val trapperPattern by patternGroup.pattern(
        "trapper",
        "\\[NPC] Trevor: You can find your (?<rarity>.*) animal near the (?<location>.*)\\.",
    )

    /**
     * REGEX-TEST: The target is around 40 blocks above, at a 45 degrees angle!
     */
    private val talbotPatternAbove by patternGroup.pattern(
        "above",
        "The target is around (?<height>.*) blocks above, at a (?<angle>.*) degrees angle!",
    )

    /**
     * REGEX-TEST: The target is around 15 blocks below, at a 30 degrees angle!
     */
    private val talbotPatternBelow by patternGroup.pattern(
        "below",
        "The target is around (?<height>.*) blocks below, at a (?<angle>.*) degrees angle!",
    )
    private val talbotPatternAt by patternGroup.pattern(
        "at",
        "You are at the exact height!",
    )

    /**
     * REGEX-TEST: Location: Mushroom Gorge
     */
    private val locationPattern by patternGroup.pattern(
        "zone",
        "Location: (?<zone>.*)",
    )
    private val mobDiedPattern by patternGroup.pattern(
        "mob.died",
        "§aReturn to the Trapper soon to get a new animal to hunt!",
    )
    private val outOfTimePattern by patternGroup.pattern(
        "outoftime",
        "You ran out of time and the animal disappeared!",
    )
    private val clickOptionPattern by patternGroup.pattern(
        "clickoption",
        "Click an option: §r§a§l\\[YES]§r§7 - §r§c§l\\[NO]",
    )
    private val areaTrappersDenPattern by patternGroup.pattern(
        "area.trappersden",
        "Trapper's Den",
    )

    private var timeUntilNextReady = 0
    private var trapperReady: Boolean = true
    private var currentStatus = TrapperStatus.READY
    private var currentLabel = "§2Ready"
    private const val TRAPPER_ID: Int = 56
    private const val BACKUP_TRAPPER_ID: Int = 17
    private var timeLastWarped = SimpleTimeMark.farPast()
    private var lastChatPrompt = ""
    private var lastChatPromptTime = SimpleTimeMark.farPast()

    var questActive = false
    var inBetweenQuests = false
    var inTrapperDen = false

    private val config get() = SkyHanniMod.feature.misc.trevorTheTrapper

    @HandleEvent(SecondPassedEvent::class)
    fun onSecondPassed() {
        if (!onFarmingIsland()) return
        updateTrapper()
        TrevorTracker.update()
        TrevorTracker.calculatePeltsPerHour()
        if (config.trapperSolver && questActive) {
            TrevorSolver.findMob()
        }
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!onFarmingIsland()) return

        val formattedMessage = event.message.removeColor()

        mobDiedPattern.matchMatcher(event.message) {
            TrevorSolver.resetLocation()
            if (config.trapperMobDiedMessage) {
                TitleManager.sendTitle("§2Mob Died ")
                SoundUtils.playBeepSound()
            }
            trapperReady = true
            TrevorSolver.mobLocation = TrapperMobArea.NONE
            if (timeUntilNextReady <= 0) {
                currentStatus = TrapperStatus.READY
                currentLabel = "§2Ready"
            } else {
                currentStatus = TrapperStatus.WAITING
                currentLabel = if (timeUntilNextReady == 1) "§31 second left" else "§3$timeUntilNextReady seconds left"
            }
            TrevorSolver.mobLocation = TrapperMobArea.NONE
        }

        trapperPattern.matchMatcher(formattedMessage) {
            timeUntilNextReady = if (Perk.PELT_POCALYPSE.isActive) 16 else 21
            currentStatus = TrapperStatus.ACTIVE
            currentLabel = "§cActive Quest"
            trapperReady = false
            TrevorTracker.startQuest(this)
            updateTrapper()
            lastChatPromptTime = SimpleTimeMark.farPast()
        }

        talbotPatternAbove.matchMatcher(formattedMessage) {
            val height = group("height").toInt()
            TrevorSolver.findMobHeight(height, true)
        }
        talbotPatternBelow.matchMatcher(formattedMessage) {
            val height = group("height").toInt()
            TrevorSolver.findMobHeight(height, false)
        }
        talbotPatternAt.matchMatcher(formattedMessage) {
            TrevorSolver.averageHeight = LocationUtils.playerLocation().y
        }

        outOfTimePattern.matchMatcher(formattedMessage) {
            resetTrapper()
        }

        clickOptionPattern.findMatcher(event.message) {
            for (sibling in event.chatComponent.siblings) {
                val clickEvent = sibling.command ?: continue

                if (clickEvent.contains("YES")) {
                    lastChatPromptTime = SimpleTimeMark.now()
                    lastChatPrompt = clickEvent.substringAfter(" ")
                }
            }
        }
    }

    @HandleEvent(GuiRenderEvent.GuiOverlayRenderEvent::class, priority = HandleEvent.LOWEST)
    fun onRenderOverlay() {
        if (!config.trapperCooldownGui) return
        if (!onFarmingIsland()) return

        val cooldownMessage = if (timeUntilNextReady <= 0) "Trapper Ready"
        else if (timeUntilNextReady == 1) "1 second left"
        else "$timeUntilNextReady seconds left"

        config.trapperCooldownPos.renderString(
            "${currentStatus.colorCode}Trapper Cooldown: $cooldownMessage",
            posLabel = "Trapper Cooldown GUI",
        )
    }

    private fun updateTrapper() {
        timeUntilNextReady -= 1
        if (trapperReady && timeUntilNextReady > 0) {
            currentStatus = TrapperStatus.WAITING
            currentLabel = if (timeUntilNextReady == 1) "§31 second left" else "§3$timeUntilNextReady seconds left"
        }

        if (timeUntilNextReady <= 0 && trapperReady) {
            if (timeUntilNextReady == 0) {
                if (config.readyTitle) {
                    TitleManager.sendTitle("§2Trapper Ready")
                    SoundUtils.playBeepSound()
                }
            }
            currentStatus = TrapperStatus.READY
            currentLabel = "§2Ready"
        }

        var found = false
        var active = false
        val previousLocation = TrevorSolver.mobLocation
        // TODO work with trapper widget, widget api, repo patterns, when not found, warn in chat and dont update
        for (line in TabListData.getTabList()) {
            val formattedLine = line.removeColor().drop(1)
            if (formattedLine.startsWith("Time Left: ")) {
                trapperReady = false
                currentStatus = TrapperStatus.ACTIVE
                currentLabel = "§cActive Quest"
                active = true
            }

            TrapperMobArea.entries.firstOrNull { it.location == formattedLine }?.let {
                TrevorSolver.mobLocation = it
                found = true
            }
            locationPattern.matchMatcher(formattedLine) {
                val zone = group("zone")
                TrevorSolver.mobLocation = TrapperMobArea.entries.firstOrNull { it.location == zone } ?: TrapperMobArea.NONE
                found = true
            }
        }
        if (!found) TrevorSolver.mobLocation = TrapperMobArea.NONE
        if (!active) {
            trapperReady = true
        } else {
            inBetweenQuests = true
        }
        if (TrevorSolver.mobCoordinates != LorenzVec(0.0, 0.0, 0.0) && active) {
            TrevorSolver.mobLocation = previousLocation
        }
        questActive = active
    }

    @HandleEvent
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!onFarmingIsland()) return
        var entityTrapper = EntityUtils.getEntityByID(TRAPPER_ID)
        if (entityTrapper !is EntityLivingBase) entityTrapper = EntityUtils.getEntityByID(BACKUP_TRAPPER_ID)
        if (entityTrapper is EntityLivingBase && config.trapperTalkCooldown) {
            // Solve for the fact that Moby also has the same ID as the Trapper
            val entityMob = MobData.entityToMob[entityTrapper] ?: return
            if (entityMob.name == "Moby") return
            RenderLivingEntityHelper.setEntityColorWithNoHurtTime(entityTrapper, currentStatus.color) {
                config.trapperTalkCooldown
            }
            entityTrapper.getLorenzVec().let {
                if (it.distanceToPlayer() < 15) {
                    event.drawString(it.up(2.23), currentLabel)
                }
            }
        }

        if (config.trapperSolver) {
            var location = TrevorSolver.mobLocation.coordinates
            if (TrevorSolver.mobLocation == TrapperMobArea.NONE) return
            if (TrevorSolver.averageHeight != 0.0) {
                location = LorenzVec(location.x, TrevorSolver.averageHeight, location.z)
            }
            if (TrevorSolver.mobLocation == TrapperMobArea.FOUND) {
                val displayName = TrevorSolver.currentMob?.mobName ?: "Mob Location"
                location = TrevorSolver.mobCoordinates
                event.drawWaypointFilled(location.down(2), LorenzColor.GREEN.toColor(), seeThroughBlocks = true, beacon = true)
                event.drawDynamicText(location.up(), displayName, 1.5)
            } else {
                event.drawWaypointFilled(location, LorenzColor.GOLD.toColor(), seeThroughBlocks = true, beacon = true)
                event.drawDynamicText(location.up(), TrevorSolver.mobLocation.location, 1.5)
            }
        }
    }

    @HandleEvent
    fun onKeyPress(event: KeyPressEvent) {
        if (!onFarmingIsland()) return
        if (Minecraft.getMinecraft().currentScreen != null) return
        if (NeuItems.neuHasFocus()) return

        if (event.keyCode != config.keyBindWarpTrapper) return

        if (config.acceptQuest) {
            val timeSince = lastChatPromptTime.passedSince()
            if (timeSince > 200.milliseconds && timeSince < 5.seconds) {
                lastChatPromptTime = SimpleTimeMark.farPast()
                HypixelCommands.chatPrompt(lastChatPrompt)
                lastChatPrompt = ""
                timeLastWarped = SimpleTimeMark.now()
                return
            }
        }

        if (config.warpToTrapper && timeLastWarped.passedSince() > 3.seconds) {
            HypixelCommands.warp("trapper")
            timeLastWarped = SimpleTimeMark.now()
        }
    }

    @HandleEvent(priority = HandleEvent.HIGHEST, onlyOnIsland = IslandType.THE_FARMING_ISLANDS)
    fun onCheckRender(event: CheckRenderEntityEvent<EntityArmorStand>) {
        if (!inTrapperDen) return
        if (!config.trapperTalkCooldown) return
        val entity = event.entity
        if (entity.name == "§e§lCLICK") {
            event.cancel()
        }
    }

    private fun resetTrapper() {
        TrevorSolver.resetLocation()
        currentStatus = TrapperStatus.READY
        currentLabel = "§2Ready"
        questActive = false
        inBetweenQuests = false
    }

    @HandleEvent
    fun onWorldChange() {
        resetTrapper()
    }

    @HandleEvent
    fun onTick() {
        inTrapperDen = areaTrappersDenPattern.matches(IslandAreas.currentArea)
    }

    enum class TrapperStatus(baseColor: LorenzColor) {
        READY(LorenzColor.DARK_GREEN),
        WAITING(LorenzColor.DARK_AQUA),
        ACTIVE(LorenzColor.DARK_RED),
        ;

        val color = baseColor.toColor().addAlpha(75)
        val colorCode = baseColor.getChatColor()
    }

    fun onFarmingIsland() = IslandType.THE_FARMING_ISLANDS.isInIsland()

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.transform(11, "misc.trevorTheTrapper.textFormat") { element ->
            ConfigUtils.migrateIntArrayListToEnumArrayList(element, TrackerEntry::class.java)
        }
    }
}
