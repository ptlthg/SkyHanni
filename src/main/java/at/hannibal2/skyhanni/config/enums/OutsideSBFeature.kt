package at.hannibal2.skyhanni.config.enums

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat

enum class OutsideSBFeature(private val displayName: String) {
    MODIFY_VISUAL_WORDS("Modify Visual Words"),
    CUSTOM_TEXT_BOX("Custom Text Box"),
    REAL_TIME("Real Time"),
    TPS_DISPLAY("TPS Display"),
    MARKED_PLAYERS("Marked Players"),
    FARMING_WEIGHT("Farming Weight"),
    NEXT_JACOB_CONTEST("Next Jacobs's Contest"),
    COMPOSTER_TIME("Composter Time"),
    YAW_AND_PITCH("Yaw and Pitch"),
    ATMOSPHERIC_FILTER("Atmospheric Filter"),
    QUICK_MOD_MENU_SWITCH("Quick Mod Menu Switch"),
    FOLLOWING_LINE("Following Line"),
    ARROW_TRAIL("Arrow Trail"),
    HIGHLIGHT_PARTY_MEMBERS("Highlight Party Members"),
    MOVEMENT_SPEED("Movement Speed"),
    CUSTOM_SCOREBOARD("Custom Scoreboard (only on Hypixel)"),
    MAYOR_OVERLAY("Mayor Overlay"),
    MINING_EVENT_DISPLAY("Mining Event Display"),
    ;

    override fun toString() = displayName

    fun isSelected() = MinecraftCompat.localPlayerExists && SkyHanniMod.feature.misc.showOutsideSB.get().contains(this)
}
