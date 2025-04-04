package at.hannibal2.skyhanni.features.event.hoppity

import at.hannibal2.skyhanni.data.PetApi
import at.hannibal2.skyhanni.data.TitleManager
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import kotlin.time.Duration.Companion.seconds

object MythicRabbitPetWarning {
    private const val MYTHIC_RABBIT_DISPLAY_NAME = "§dRabbit"
    private var lastCheck = SimpleTimeMark.farPast()

    fun check() {
        if (!HoppityEggsManager.config.petWarning) return

        if (lastCheck.passedSince() < 30.seconds) return

        if (!correctPet()) {
            lastCheck = SimpleTimeMark.now()
            warn()
        }
    }

    fun correctPet() = PetApi.isCurrentPet(MYTHIC_RABBIT_DISPLAY_NAME)

    private fun warn() {
        ChatUtils.chat("Use a §dMythic Rabbit Pet §efor more chocolate!")
        TitleManager.sendTitle("§cNo Rabbit Pet!")
    }
}
