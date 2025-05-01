package at.hannibal2.skyhanni.features.event.diana

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.data.ElectionCandidate
import at.hannibal2.skyhanni.data.EntityMovementData
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.events.BlockClickEvent
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.diana.BurrowDetectEvent
import at.hannibal2.skyhanni.events.diana.BurrowDugEvent
import at.hannibal2.skyhanni.events.diana.BurrowGuessEvent
import at.hannibal2.skyhanni.events.entity.EntityMoveEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.features.event.diana.DianaApi.isDianaSpade
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.BlockUtils.getBlockAt
import at.hannibal2.skyhanni.utils.BlockUtils.isInLoadedChunk
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.KeyboardManager
import at.hannibal2.skyhanni.utils.LocationUtils
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.RenderUtils.drawColor
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.RenderUtils.drawLineToEye
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.editCopy
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import at.hannibal2.skyhanni.utils.compat.addDoublePlant
import at.hannibal2.skyhanni.utils.compat.addLeaves
import at.hannibal2.skyhanni.utils.compat.addLeaves2
import at.hannibal2.skyhanni.utils.compat.addRedFlower
import at.hannibal2.skyhanni.utils.compat.addTallGrass
import at.hannibal2.skyhanni.utils.toLorenzVec
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.init.Blocks
import org.lwjgl.input.Keyboard
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object GriffinBurrowHelper {

    private val config get() = SkyHanniMod.feature.event.diana

    private val allowedBlocksAboveGround = buildList {
        add(Blocks.air)
        add(Blocks.yellow_flower)
        add(Blocks.spruce_fence)
        addLeaves()
        addLeaves2()
        addTallGrass()
        addDoublePlant()
        addRedFlower()
    }

    var targetLocation: LorenzVec? = null

    class Guess(private val location: LorenzVec, val precise: Boolean) {

        fun getLocation(): LorenzVec = if (precise) {
            location
        } else {
            findBlock(location)
        }
    }

    private var latestGuess: Guess? = null
    private val additionalGuesses = mutableListOf<Guess>()

    private var allGuessLocations: List<LorenzVec> = emptyList()

    private var particleBurrows = mapOf<LorenzVec, BurrowType>()
    var lastTitleSentTime = SimpleTimeMark.farPast()
    private var shouldFocusOnInquis = false

    private var testList = listOf<LorenzVec>()
    private var testGriffinSpots = false

    @HandleEvent
    fun onDebug(event: DebugDataCollectEvent) {
        event.title("Griffin Burrow Helper")

        if (!DianaApi.isDoingDiana()) {
            event.addIrrelevant("not doing diana")
            return
        }

        event.addData {
            add("targetLocation: ${targetLocation?.printWithAccuracy(1)}")
            add("guessLocation: ${latestGuess?.getLocation()?.printWithAccuracy(1)}")
            add("additionalGuesses: ${additionalGuesses.size}")
            for (guess in additionalGuesses) {
                add("  ${guess.getLocation().printWithAccuracy(1)} (precise=${guess.precise})")
            }
            add("particleBurrows: ${particleBurrows.size}")
            for ((location, type) in particleBurrows) {
                add("  ${location.printWithAccuracy(1)} (${type.name})")
            }
        }
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!isEnabled()) return
        update()
        loadTestGriffinSpots()
    }

    fun testGriffinSpots() {
        testGriffinSpots = !testGriffinSpots
        val state = if (testGriffinSpots) "§aenabled" else "§cdisabled"
        ChatUtils.chat("Test Griffin Spots $state§e.")
    }

    private fun loadTestGriffinSpots() {
        if (!testGriffinSpots) return
        val center = LocationUtils.playerLocation().toBlockPos().toLorenzVec()
        val list = mutableListOf<LorenzVec>()
        for (x in -5 until 5) {
            for (z in -5 until 5) {
                list.add(findBlock(center.add(x, 0, z)))
            }
        }
        testList = list
    }

    fun update() {
        if (config.burrowsNearbyDetection) {
            checkRemoveNearbyGuess()
        }

        val additionalGuesses = if (config.multiGuesses) additionalGuesses else emptyList()
        allGuessLocations = (latestGuess?.let { additionalGuesses + it } ?: additionalGuesses).map { it.getLocation() }

        val newLocation = calculateNewTarget()
        if (targetLocation != newLocation) {
            targetLocation = newLocation
            // TODO: add island graphs here some day when the hub is fully added in the graph
//             newLocation?.let {
//                 IslandGraphs.find(it)
//             }
        }

        if (config.burrowNearestWarp) {
            targetLocation?.let {
                BurrowWarpHelper.shouldUseWarps(it)
            }
        }
    }

    // TODO add option to only focus on last guess - highly requersted method that is less optimal for money per hour. users choice
    private fun calculateNewTarget(): LorenzVec? {
        val locations = mutableListOf<LorenzVec>()

        if (config.inquisitorSharing.enabled) {
            for (waypoint in InquisitorWaypointShare.waypoints) {
                locations.add(waypoint.value.location)
            }
        }
        shouldFocusOnInquis = config.inquisitorSharing.focusInquisitor && locations.isNotEmpty()
        if (!shouldFocusOnInquis) {
            locations.addAll(particleBurrows.keys.toMutableList())

            locations.addAll(allGuessLocations)
            locations.addAll(InquisitorWaypointShare.waypoints.values.map { it.location })
        }
        val newLocation = locations.minByOrNull { it.distanceToPlayer() }
        return newLocation
    }

    @HandleEvent
    fun onBurrowGuess(event: BurrowGuessEvent) {
        EntityMovementData.addToTrack(MinecraftCompat.localPlayer)
        val newLocation = event.guessLocation
        val playerLocation = LocationUtils.playerLocation()

        if (newLocation.distance(playerLocation) < 6) return

        latestGuess?.let {
            if (it.precise && config.multiGuesses && event.new && it.getLocation() !in particleBurrows) {
                additionalGuesses.add(it)
            }
        }

        latestGuess = Guess(newLocation, event.precise)
        update()
    }

    @HandleEvent
    fun onBurrowDetect(event: BurrowDetectEvent) {
        EntityMovementData.addToTrack(MinecraftCompat.localPlayer)
        val burrowLocation = event.burrowLocation
        particleBurrows = particleBurrows.editCopy { this[burrowLocation] = event.type }

        removePreciseGuess(burrowLocation)
        update()
    }

    private fun removePreciseGuess(location: LorenzVec) {
        latestGuess?.let {
            if (it.precise && location == it.getLocation()) {
                latestGuess = null
            }
        }
        additionalGuesses.removeIf { it.getLocation() == location }
    }

    private fun checkRemoveNearbyGuess() {
        val guess = latestGuess ?: return
        val distance = if (guess.precise) 5 else 50
        val location = guess.getLocation()
        if (particleBurrows.any { location.distance(it.key) < distance }) {
            latestGuess = null
        }
    }

    @HandleEvent
    fun onBurrowDug(event: BurrowDugEvent) {
        val location = event.burrowLocation
        particleBurrows = particleBurrows.editCopy { remove(location) }
        removePreciseGuess(location)
        update()
    }

    @HandleEvent
    fun onPlayerMove(event: EntityMoveEvent<EntityPlayerSP>) {
        if (!isEnabled()) return
        if (event.distance > 10 && event.isLocalPlayer) {
            update()
        }
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!isEnabled()) return
        if (event.message.startsWith("§c ☠ §r§7You were killed by §r")) {
            particleBurrows = particleBurrows.editCopy { keys.removeIf { this[it] == BurrowType.MOB } }
        }

        // talking to Diana NPC
        if (event.message == "§6Poof! §r§eYou have cleared your griffin burrows!") {
            resetAllData()
        }
    }

    private fun resetAllData() {
        latestGuess = null
        additionalGuesses.clear()
        targetLocation = null
        particleBurrows = emptyMap()
        GriffinBurrowParticleFinder.reset()

        BurrowWarpHelper.currentWarp = null
        if (isEnabled()) {
            update()
        }
    }

    @HandleEvent
    fun onWorldChange() {
        resetAllData()
    }

    private fun findBlock(point: LorenzVec): LorenzVec {
        if (!point.isInLoadedChunk()) {
            return point.copy(y = LocationUtils.playerLocation().y)
        }
        findGround(point)?.let {
            return it
        }

        return findBlockBelowAir(point)
    }

    private fun findGround(point: LorenzVec): LorenzVec? {
        fun isValidGround(y: Double): Boolean {
            val isGround = point.copy(y = y).getBlockAt() == Blocks.grass
            val isValidBlockAbove = point.copy(y = y + 1).getBlockAt() in allowedBlocksAboveGround
            return isGround && isValidBlockAbove
        }

        var gY = 140.0
        while (!isValidGround(gY)) {
            gY--
            if (gY < 65) {
                // no ground detected, find the lowest block below air
                return null
            }
        }
        return point.copy(y = gY)
    }

    private fun findBlockBelowAir(point: LorenzVec): LorenzVec {
        val start = 65.0
        var gY = start
        while (point.copy(y = gY).getBlockAt() != Blocks.air) {
            gY++
            if (gY > 140) {
                // no blocks at this spot, assuming outside of island
                return point.copy(y = LocationUtils.playerLocation().y)
            }
        }

        if (gY == start) {
            return point.copy(y = LocationUtils.playerLocation().y)
        }
        return point.copy(y = gY - 1)
    }

    @HandleEvent
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!isEnabled()) return

        showTestLocations(event)

        showWarpSuggestions()

        val playerLocation = LocationUtils.playerLocation()
        if (config.inquisitorSharing.enabled) {
            for (inquis in InquisitorWaypointShare.waypoints.values) {
                val location = inquis.location
                event.drawColor(location, LorenzColor.LIGHT_PURPLE)
                val distance = location.distance(playerLocation)
                if (distance > 10) {
                    // TODO use round(1)
                    val formattedDistance = distance.toInt().addSeparators()
                    event.drawDynamicText(location.up(), "§d§lInquisitor §e${formattedDistance}m", 1.7)
                } else {
                    event.drawDynamicText(location.up(), "§d§lInquisitor", 1.7)
                }
                if (distance < 5) {
                    InquisitorWaypointShare.maybeRemove(inquis)
                }
                event.drawDynamicText(location.up(), "§eFrom §b${inquis.displayName}", 1.6, yOff = 9f)

                if (config.inquisitorSharing.showDespawnTime) {
                    val spawnTime = inquis.spawnTime
                    val format = (75.seconds - spawnTime.passedSince()).format()
                    event.drawDynamicText(location.up(), "§eDespawns in §b$format", 1.6, yOff = 18f)
                }
            }
        }

        val currentWarp = BurrowWarpHelper.currentWarp
        if (config.lineToNext) {
            var color: LorenzColor?
            val renderLocation = if (currentWarp != null) {
                color = LorenzColor.AQUA
                currentWarp.location
            } else {
                color = if (shouldFocusOnInquis) LorenzColor.LIGHT_PURPLE else LorenzColor.WHITE
                targetLocation?.blockCenter() ?: return
            }

            val lineWidth = if (targetLocation in particleBurrows) {
                color = particleBurrows[targetLocation]!!.color
                3
            } else 2
            if (currentWarp == null) {
                event.drawLineToEye(renderLocation, color.toColor(), lineWidth, false)
            }
        }

        if (InquisitorWaypointShare.waypoints.isNotEmpty() && config.inquisitorSharing.focusInquisitor) {
            return
        }

        if (config.burrowsNearbyDetection) {
            for (burrow in particleBurrows) {
                val location = burrow.key
                val distance = location.distance(playerLocation)
                val burrowType = burrow.value
                event.drawColor(location, burrowType.color, distance > 10)
                event.drawDynamicText(location.up(), burrowType.text, 1.5)
            }
        }

        if (config.guess) {
            for (guessLocation in allGuessLocations) {
                if (guessLocation in particleBurrows) continue
                val distance = guessLocation.distance(playerLocation)
                event.drawColor(guessLocation, LorenzColor.WHITE, distance > 10)
                val color = if (currentWarp != null && targetLocation == guessLocation) "§b" else "§f"
                event.drawDynamicText(guessLocation.up(), "${color}Guess", 1.5)
                if (distance > 5) {
                    val formattedDistance = distance.toInt().addSeparators()
                    event.drawDynamicText(guessLocation.up(), "§e${formattedDistance}m", 1.7, yOff = 10f)
                }
            }
        }
    }

    private fun showTestLocations(event: SkyHanniRenderWorldEvent) {
        if (!testGriffinSpots) return
        for (location in testList) {
            event.drawColor(location, LorenzColor.WHITE)
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(2, "diana", "event.diana")
    }

    @HandleEvent(onlyOnIsland = IslandType.HUB)
    fun onBlockClick(event: BlockClickEvent) {
        if (!isEnabled()) return

        val location = event.position
        if (event.itemInHand?.isDianaSpade != true || location.getBlockAt() !== Blocks.grass) return
        removePreciseGuess(location)

        if (particleBurrows.containsKey(location)) {
            DelayedRun.runDelayed(1.seconds) {
                if (BurrowApi.lastBurrowRelatedChatMessage.passedSince() > 2.seconds && particleBurrows.containsKey(location)) {
                    // workaround
                    particleBurrows = particleBurrows.editCopy { keys.remove(location) }
                }
            }
        }
    }

    private fun showWarpSuggestions() {
        if (!config.burrowNearestWarp) return
        val warp = BurrowWarpHelper.currentWarp ?: return

        val text = "§bWarp to " + warp.displayName
        val keybindSuffix = if (config.keyBindWarp != Keyboard.KEY_NONE) {
            val keyName = KeyboardManager.getKeyName(config.keyBindWarp)
            " §7(§ePress $keyName§7)"
        } else ""
        if (lastTitleSentTime.passedSince() > 2.seconds) {
            lastTitleSentTime = SimpleTimeMark.now()
            TitleManager.sendTitle(text + keybindSuffix, duration = 2.seconds)
        }
    }

    private fun isEnabled() = DianaApi.isDoingDiana()

    private fun setTestBurrow(strings: Array<String>) {
        if (!IslandType.HUB.isInIsland()) {
            ChatUtils.userError("You can only create test burrows on the hub island!")
            return
        }

        if (!isEnabled()) {
            if (!ElectionCandidate.DIANA.isActive()) {
                ChatUtils.chatAndOpenConfig(
                    "§cSelect Diana as mayor overwrite!",
                    SkyHanniMod.feature.dev.debug::assumeMayor,
                )

            } else {
                ChatUtils.userError("Have an Ancestral Spade in the inventory!")
            }
            return
        }

        if (strings.size != 1) {
            ChatUtils.userError("/shtestburrow <type>")
            return
        }

        val type: BurrowType = when (strings[0].lowercase()) {
            "reset" -> {
                resetAllData()
                ChatUtils.chat("Manually reset all burrow data.")
                return
            }

            "1", "start" -> BurrowType.START
            "2", "mob" -> BurrowType.MOB
            "3", "treasure" -> BurrowType.TREASURE
            else -> {
                ChatUtils.userError("Unknown burrow type! Try 1-3 instead.")
                return
            }
        }

        EntityMovementData.addToTrack(MinecraftCompat.localPlayer)
        val location = LocationUtils.playerLocation().roundLocation()
        particleBurrows = particleBurrows.editCopy { this[location] = type }
        update()
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.register("shtestburrow") {
            description = "Sets a test burrow waypoint at your location"
            category = CommandCategory.DEVELOPER_TEST
            callback { setTestBurrow(it) }
        }
    }
}
