package at.hannibal2.skyhanni.test.command

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.ReceiveParticleEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.OSUtils
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SimpleTimeMark.Companion.fromNow
import at.hannibal2.skyhanni.utils.renderables.Renderable
import net.minecraft.util.EnumParticleTypes
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object TrackParticlesCommand {

    private var cutOffTime = SimpleTimeMark.farPast()
    private var startTime = SimpleTimeMark.farPast()

    private val particles = ConcurrentLinkedDeque<Pair<Duration, ReceiveParticleEvent>>()

    private var isRecording = false

    private val position get() = SkyHanniMod.feature.dev.debug.trackParticlePosition

    private var display: List<Renderable> = emptyList()
    private var worldParticles = emptyMap<LorenzVec, List<ReceiveParticleEvent>>()
    private val ignoredTypes = mutableListOf<EnumParticleTypes>()

    // TODO write abstract code for this and TrackSoundsCommand
    private fun command(args: Array<String>) {
        if (!LorenzUtils.inSkyBlock) {
            ChatUtils.userError("This command only works in SkyBlock!")
            return
        }

        if (args.firstOrNull() == "end") {
            if (!isRecording) {
                ChatUtils.userError("Nothing to end")
            } else {
                cutOffTime = SimpleTimeMark.now()
            }
            return
        }
        if (isRecording) {
            args.getOrNull(0)?.let { name ->
                val type = getParticleTypeByName(name)
                if (type == null) {
                    ChatUtils.userError("unknown particle type: '$name'")
                    return
                }
                if (ignoredTypes.contains(type)) {
                    ignoredTypes.remove(type)
                    ChatUtils.chat("Removed $type from ignored types.")
                } else {
                    ignoredTypes.add(type)
                    ChatUtils.chat("Add $type to ignored types.")
                }
                return
            }
            ChatUtils.userError(
                "Still tracking particles, wait for the other tracking to complete before starting a new one, " +
                    "or type §e/shtrackparticles end §cto end it prematurely",
            )
            return
        }
        isRecording = true
        particles.clear()
        startTime = SimpleTimeMark.now()
        cutOffTime = args.firstOrNull()?.toInt()?.seconds?.let {
            ChatUtils.chat("Now started tracking particles for ${it.inWholeSeconds} Seconds")
            it.fromNow()
        } ?: run {
            ChatUtils.chat("Now started tracking particles until manually ended")
            SimpleTimeMark.farFuture()
        }
    }

    // TODO move into utils
    private fun getParticleTypeByName(name: String): EnumParticleTypes? =
        EnumParticleTypes.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

    @HandleEvent
    fun onTick() {
        if (!isRecording) return

        val particlesToDisplay = particles.takeWhile { startTime.passedSince() - it.first < 3.seconds }

        display = particlesToDisplay
            .take(10).reversed().map {
                Renderable.string("§3" + it.second.type + " §8c:" + it.second.count + " §7s:" + it.second.speed)
            }
        worldParticles = particlesToDisplay.map { it.second }.groupBy { it.location }

        // The function must run after cutOffTime has passed to ensure thread safety
        if (cutOffTime.passedSince() <= 0.1.seconds) return
        val string = particles.reversed().joinToString("\n") { "Time: ${it.first.inWholeMilliseconds}  ${it.second}" }
        val counter = particles.size
        OSUtils.copyToClipboard(string)
        ChatUtils.chat("$counter particles copied into the clipboard!")
        particles.clear()
        isRecording = false
    }

    @HandleEvent
    fun onReceiveParticle(event: ReceiveParticleEvent) {
        if (cutOffTime.isInPast()) return
        if (event.type in ignoredTypes) return
        event.distanceToPlayer // Need to call to initialize Lazy
        particles.addFirst(startTime.passedSince() to event)
    }

    @HandleEvent(GuiRenderEvent.GuiOverlayRenderEvent::class)
    fun onRenderOverlay() {
        if (cutOffTime.isInPast()) return
        position.renderRenderables(display, posLabel = "Track particles log")
    }

    @HandleEvent
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (cutOffTime.isInPast()) return
        worldParticles.forEach { (key, value) ->
            if (value.size != 1) {
                event.drawDynamicText(key, "§e${value.size} particles", 0.8)

                var offset = 0.2
                value.groupBy { it.type }.forEach { (particleType, particles) ->
                    event.drawDynamicText(key.down(offset), "§7§l$particleType §7(§e${particles.size}§7)", 0.8)
                    offset += 0.2
                }
            } else {
                val particle = value.first()

                event.drawDynamicText(key, "§7§l${particle.type}", 0.8)
                event.drawDynamicText(
                    key.down(0.2),
                    "§7C: §e${particle.count} §7S: §a${particle.speed.roundTo(2)}",
                    scaleMultiplier = 0.8,
                )
            }
        }
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.register("shtrackparticles") {
            description = "Tracks the particles for the specified duration (in seconds) and copies it to the clipboard"
            category = CommandCategory.DEVELOPER_TEST
            callback { command(it) }
        }
    }
}
