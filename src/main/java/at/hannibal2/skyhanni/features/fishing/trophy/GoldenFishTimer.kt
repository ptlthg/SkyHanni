package at.hannibal2.skyhanni.features.fishing.trophy

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.entity.EntityMaxHealthUpdateEvent
import at.hannibal2.skyhanni.events.fishing.FishingBobberCastEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.features.fishing.FishingApi
import at.hannibal2.skyhanni.features.fishing.FishingApi.isLavaRod
import at.hannibal2.skyhanni.mixins.hooks.RenderLivingEntityHelper
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ColorUtils.addAlpha
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.EntityUtils.wearingSkullTexture
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalNameOrNull
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.NumberUtil.formatPercentage
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils
import at.hannibal2.skyhanni.utils.RenderUtils.drawString
import at.hannibal2.skyhanni.utils.RenderUtils.exactLocation
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderable
import at.hannibal2.skyhanni.utils.ServerTimeMark
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkullTextureHolder
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object GoldenFishTimer {

    private val config get() = SkyHanniMod.feature.fishing.trophyFishing.goldenFishTimer

    private val patternGroup = RepoPattern.group("fishing.goldenfish")

    /**
     * REGEX-TEST: §9You spot a §r§6Golden Fish §r§9surface from beneath the lava!
     */
    private val spawnPattern by patternGroup.pattern(
        "spawn",
        "§9You spot a §r§6Golden Fish §r§9surface from beneath the lava!",
    )

    /**
     * REGEX-TEST: §9The §r§6Golden Fish §r§9escapes your hook but looks weakened.
     */
    private val interactPattern by patternGroup.pattern(
        "interact",
        "§9The §r§6Golden Fish §r§9escapes your hook but looks weakened\\.",
    )

    /**
     * REGEX-TEST: §9The §r§6Golden Fish §r§9is weak!
     */
    private val weakPattern by patternGroup.pattern(
        "weak",
        "§9The §r§6Golden Fish §r§9is weak!",
    )

    /**
     * REGEX-TEST: §9The §r§6Golden Fish §r§9swims back beneath the lava...
     */
    private val despawnPattern by patternGroup.pattern(
        "despawn",
        "§9The §r§6Golden Fish §r§9swims back beneath the lava\\.\\.\\.",
    )

    private val timeOut = 10.seconds
    private val despawnTime = 1.minutes
    private val maxRodTime = 3.minutes
    private val minimumSpawnTime = 8.minutes
    private val maximumSpawnTime = 12.minutes
    private const val MAX_INTERACTIONS = 3

    private var lastFishEntity = SimpleTimeMark.farPast()
    private var lastChatMessage = SimpleTimeMark.farPast()

    private var lastGoldenFishTime = ServerTimeMark.FAR_PAST

    private var lastRodThrowTime = ServerTimeMark.FAR_PAST
    private var goldenFishDespawnTimer = ServerTimeMark.FAR_FUTURE
    private var timePossibleSpawn = ServerTimeMark.FAR_FUTURE

    private val isFishing get() = FishingApi.isFishing() || lastRodThrowTime.passedSince() < maxRodTime
    private var hasLavaRodInInventory = false

    private fun checkGoldenFish(entity: EntityArmorStand) {
        if (!entity.wearingSkullTexture(GOLDEN_FISH_SKULL_TEXTURE)) return
        possibleGoldenFishEntity = entity
        lastFishEntity = SimpleTimeMark.now()
        handle()
    }

    private val GOLDEN_FISH_SKULL_TEXTURE by lazy { SkullTextureHolder.getTexture("GOLDEN_FISH") }
    private val goldenFishSkullItem by lazy {
        ItemUtils.createSkull(
            displayName = "§6Golden Fish",
            uuid = "b7fdbe67cd004683b9fa9e3e17738254",
            value = GOLDEN_FISH_SKULL_TEXTURE,
        )
    }
    private var interactions = 0
    private var goingDownInit = true
    private var goingDownPost = false
    private var hasWarnedRod = false

    private var possibleGoldenFishEntity: EntityLivingBase? = null
    private var confirmedGoldenFishEntity: EntityLivingBase? = null

    private var display: Renderable? = null

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!isActive()) return
        if (spawnPattern.matches(event.message)) {
            lastChatMessage = SimpleTimeMark.now()
            handle()
            return
        }
        if (interactPattern.matches(event.message)) {
            goldenFishDespawnTimer = ServerTimeMark.now() + despawnTime
            interactions++
            return
        }
        if (weakPattern.matches(event.message)) {
            goldenFishDespawnTimer = ServerTimeMark.now() + despawnTime
            val entity = confirmedGoldenFishEntity ?: return
            if (config.highlight) RenderLivingEntityHelper.setEntityColorWithNoHurtTime(
                entity,
                LorenzColor.GREEN.toColor().addAlpha(100),
            ) { true }
            return
        }
        if (despawnPattern.matches(event.message)) {
            timePossibleSpawn = ServerTimeMark.now() + minimumSpawnTime
            removeGoldenFish()
            return
        }
        TrophyFishMessages.trophyFishPattern.matchMatcher(event.message) {
            val internalName = TrophyFishApi.getInternalName(group("displayName"))
            if (internalName != "goldenfish") return@matchMatcher
            timePossibleSpawn = ServerTimeMark.now() + minimumSpawnTime
            removeGoldenFish()
            return
        }
    }

    @HandleEvent
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!isActive()) return
        if (!config.nametag) return
        val entity = confirmedGoldenFishEntity ?: return

        val location = event.exactLocation(entity).add(y = 2.5)
        if (location.distanceToPlayer() > 20) return
        event.drawString(location.add(y = 0.5), "§b${(goldenFishDespawnTimer + 1.seconds).timeUntil().format()}", false)
        if (interactions >= MAX_INTERACTIONS) event.drawString(location.add(y = 0.25), "§cPULL", false)
        event.drawString(location, "§6Golden Fish §a($interactions/$MAX_INTERACTIONS)", false)
    }

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!isActive()) return
        config.position.renderRenderable(display, posLabel = "Golden Fish Timer")
    }

    private fun updateDisplay() {
        display = Renderable.horizontalContainer(drawDisplay())
    }

    private fun drawDisplay() = buildList {
        if (config.showHead) add(
            Renderable.itemStack(
                goldenFishSkullItem,
                2.5,
                verticalAlign = RenderUtils.VerticalAlignment.CENTER,
            ),
        )
        val text = buildList {
            add("§6§lGolden Fish Timer")
            if (!isGoldenFishActive()) {
                if (lastGoldenFishTime.isFarPast()) add("§7Last Golden Fish: §cNone this session")
                else add("§7Last Golden Fish: §b${lastGoldenFishTime.passedSince().formatTime()}")
                if (lastRodThrowTime.isFarPast()) add("§7Last Rod Throw: §cNone yet")
                else add(
                    "§7Last Rod Throw: §b${lastRodThrowTime.passedSince().formatTime()} " +
                        "§3(${(lastRodThrowTime + maxRodTime + 1.seconds).timeUntil().formatTime()})",
                )
                if (timePossibleSpawn.isFarFuture()) add("§7Can spawn in: §cUnknown")
                else if (timePossibleSpawn.isInFuture()) add(
                    "§7Can spawn in: §b${
                        (timePossibleSpawn + 1.seconds).timeUntil().formatTime()
                    }",
                )
                else {
                    add("§7Can spawn since: §b${timePossibleSpawn.passedSince().formatTime()}")
                    val diff = maximumSpawnTime - minimumSpawnTime
                    val chance = timePossibleSpawn.passedSince().inWholeSeconds.toDouble() / diff.inWholeSeconds
                    add("§7Chance: §b${chance.coerceAtMost(1.0).formatPercentage()}")
                }
            } else {
                add("§7Interactions: §b$interactions/$MAX_INTERACTIONS")
                add("§7Despawn in: §b${(goldenFishDespawnTimer + 1.seconds).timeUntil().formatTime()}")
            }
        }

        add(
            Renderable.verticalContainer(
                text.map { Renderable.string(it) },
                spacing = 1,
                verticalAlign = RenderUtils.VerticalAlignment.CENTER,
            ),
        )
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!isEnabled()) return
        hasLavaRodInInventory = InventoryUtils.containsInLowerInventory { it.getInternalNameOrNull()?.isLavaRod() == true }

        if (!isActive()) return

        if (lastRodThrowTime.passedSince() > maxRodTime) {
            timePossibleSpawn = ServerTimeMark.FAR_FUTURE
            lastRodThrowTime = ServerTimeMark.FAR_PAST
        }
        if (!lastRodThrowTime.isFarPast() && (lastRodThrowTime + maxRodTime).timeUntil() < config.throwRodWarningTime.seconds) {
            rodWarning()
        }

        updateDisplay()
    }

    private fun rodWarning() {
        if (!config.throwRodWarning || hasWarnedRod) return
        hasWarnedRod = true
        TitleManager.sendTitle("§cThrow your rod!")
        SoundUtils.repeatSound(100, 10, SoundUtils.plingSound)
    }

    @HandleEvent
    fun onTick() {
        if (!isActive()) return
        // This makes it only count as the rod being throw into lava if the rod goes down, up, and down again.
        // Not confirmed that this is correct, but it's the best solution found.
        val bobber = FishingApi.bobber ?: return
        if (!bobber.isInLava || bobber.ticksExisted < 5) return
        if (bobber.motionY > 0 && goingDownInit) goingDownInit = false
        else if (bobber.motionY < 0 && !goingDownInit && !goingDownPost) {
            hasWarnedRod = false
            goingDownPost = true
            lastRodThrowTime = ServerTimeMark.now()
            if (timePossibleSpawn.isFarFuture()) timePossibleSpawn = ServerTimeMark.now() + minimumSpawnTime
        }
    }

    @HandleEvent
    fun onBobberThrow(event: FishingBobberCastEvent) {
        if (!isActive()) return
        goingDownInit = true
        goingDownPost = false
    }

    @HandleEvent
    fun onEntityHealthUpdate(event: EntityMaxHealthUpdateEvent) {
        if (!isActive()) return
        if (isGoldenFishActive()) return
        val entity = event.entity as? EntityArmorStand ?: return

        DelayedRun.runDelayed(1.seconds) { checkGoldenFish(entity) }
    }

    @HandleEvent
    fun onWorldChange() {
        lastChatMessage = SimpleTimeMark.farPast()
        lastFishEntity = SimpleTimeMark.farPast()
        lastGoldenFishTime = ServerTimeMark.FAR_PAST
        possibleGoldenFishEntity = null
        lastRodThrowTime = ServerTimeMark.FAR_PAST
        timePossibleSpawn = ServerTimeMark.FAR_FUTURE
        interactions = 0
        display = null
        removeGoldenFish()
    }

    @HandleEvent
    fun onDebug(event: DebugDataCollectEvent) {
        event.title("Golden Fish Timer")
        if (!isEnabled()) {
            event.addIrrelevant("Not Enabled")
        } else {
            event.addIrrelevant {
                add("lastChatMessage: ${lastChatMessage.passedSince().format()}")
                add("lastFishEntity: ${lastFishEntity.passedSince().format()}")
                add("lastGoldenFishTime: ${lastGoldenFishTime.passedSince().format()}")
                add("lastRodThrowTime: ${lastRodThrowTime.passedSince().format()}")
                add("goldenFishDespawnTimer: ${goldenFishDespawnTimer.timeUntil().format()}")
                add("timePossibleSpawn: ${timePossibleSpawn.timeUntil().format()}")
                add("interactions: $interactions")
                add("goingDownInit: $goingDownInit")
                add("goingDownPost: $goingDownPost")
                add("hasWarnedRod: $hasWarnedRod")
                add("possibleGoldenFishEntity: $possibleGoldenFishEntity")
                add("confirmedGoldenFishEntity: $confirmedGoldenFishEntity")
            }
        }
    }

    private fun removeGoldenFish() {
        goldenFishDespawnTimer = ServerTimeMark.FAR_FUTURE
        confirmedGoldenFishEntity?.let {
            confirmedGoldenFishEntity = null
            RenderLivingEntityHelper.removeEntityColor(it)
        }
    }

    private fun handle() {
        if (lastChatMessage.passedSince() > timeOut || lastFishEntity.passedSince() > timeOut) return
        lastFishEntity = SimpleTimeMark.farPast()
        lastChatMessage = SimpleTimeMark.farPast()
        lastGoldenFishTime = ServerTimeMark.now()
        interactions = 0
        ChatUtils.debug("Found Golden Fish!")
        confirmedGoldenFishEntity = possibleGoldenFishEntity
        possibleGoldenFishEntity = null
        goldenFishDespawnTimer = ServerTimeMark.now() + despawnTime
    }

    private fun Duration.formatTime(): String {
        val duration = this.inWholeSeconds.seconds // workaround to not show milliseconds under 1s
        return duration.format(showMilliSeconds = false, showSmallerUnits = true)
    }

    private fun isGoldenFishActive() = confirmedGoldenFishEntity != null

    private fun isEnabled() = config.enabled && (IslandType.CRIMSON_ISLE.isInIsland() || LorenzUtils.isStrandedProfile)
    private fun isActive() = isEnabled() && isFishing && hasLavaRodInInventory

}
