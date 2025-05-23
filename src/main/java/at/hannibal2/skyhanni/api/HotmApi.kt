package at.hannibal2.skyhanni.api

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.config.storage.ProfileSpecificStorage
import at.hannibal2.skyhanni.data.HotmData
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.events.mining.PowderEvent
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemCategory
import at.hannibal2.skyhanni.utils.ItemUtils.getItemCategoryOrNull
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getDrillUpgrades
import at.hannibal2.skyhanni.utils.TimeLimitedCache
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.item.ItemStack
import kotlin.time.Duration.Companion.seconds

object HotmApi {

    fun copyCurrentTree() = HotmData.storage?.deepCopy()

    val activeMiningAbility get() = HotmData.abilities.firstOrNull { it.enabled }

    private val blueGoblinEgg = "GOBLIN_OMELETTE_BLUE_CHEESE".toInternalName()

    private val blueEggCache = TimeLimitedCache<ItemStack, Boolean>(10.0.seconds)
    val isBlueEggActive
        get() = InventoryUtils.getItemInHand()?.let {
            blueEggCache.getOrPut(it) {
                it.getItemCategoryOrNull() == ItemCategory.DRILL &&
                    it.getDrillUpgrades()?.contains(blueGoblinEgg) == true
            }
        } == true

    enum class PowderType(val displayName: String, val color: String) {
        MITHRIL("Mithril", "§2"),
        GEMSTONE("Gemstone", "§d"),
        GLACITE("Glacite", "§b"),

        ;

        val heartPattern by RepoPattern.pattern(
            "inventory.${name.lowercase()}.heart",
            "§7$displayName Powder: §a§.(?<powder>[\\d,]+)",
        )
        val resetPattern by RepoPattern.pattern(
            "inventory.${name.lowercase()}.reset",
            "\\s+§8- §.(?<powder>[\\d,]+) $displayName Powder",
        )

        fun pattern(isHeart: Boolean) = if (isHeart) heartPattern else resetPattern

        private val storage: ProfileSpecificStorage.MiningStorage.PowderStorage?
            get() = ProfileStorageData.profileSpecific?.mining?.powder?.getOrPut(this, ProfileSpecificStorage.MiningStorage::PowderStorage)

        var current: Long
            get() = storage?.available ?: 0L
            private set(value) {
                storage?.available = value
            }

        var total: Long
            get() = storage?.total ?: 0L
            set(value) {
                storage?.total = value
            }

        fun setAmount(value: Long, postEvent: Boolean = false) {
            val diff = value - current
            if (diff == 0L) return
            total += diff
            current = value
            if (!postEvent) return
            if (diff > 0) {
                if (shouldSendDebug) ChatUtils.debug("Gained §a${diff.addSeparators()} $color$displayName Powder")
                PowderEvent.Gain(this, diff).post()
            } else {
                if (shouldSendDebug) ChatUtils.debug("Spent §a${diff.addSeparators()} $color$displayName Powder")
                PowderEvent.Spent(this, diff).post()
            }
        }

        fun resetTree() {
            current = total
            PowderEvent(this).post()
        }

        fun resetFull() {
            current = 0L
            total = 0L
            PowderEvent(this).post()
        }

        companion object {
            private val shouldSendDebug: Boolean get() = SkyHanniMod.feature.dev.debug.powderMessages
        }
    }

    var skymall: SkymallPerk? = null

    var mineshaftMayhem: MayhemPerk? = null

    enum class SkymallPerk(chat: String, itemString: String) {
        MINING_SPEED("Gain §r§6\\+100⸕ Mining Speed§r§f\\.", "Gain §6\\+100⸕ Mining Speed§7\\."),
        MINING_FORTUNE("Gain §r§6\\+50☘ Mining Fortune§r§f\\.", "Gain §6\\+50☘ Mining Fortune§7\\."),
        EXTRA_POWDER("Gain §r§a\\+15% §r§fmore Powder while mining\\.", "Gain §a\\+15% §7more Powder while mining\\."),
        ABILITY_COOLDOWN("§r§a-20%§r§f Pickaxe Ability cooldowns\\.", "§a-20%§7 Pickaxe Ability cooldowns\\."),
        GOBLIN_CHANCE("§r§a10x §r§fchance to find Golden and Diamond Goblins\\.", "§a10x §7chance to find Golden and"),
        TITANIUM("Gain §r§a5x §r§9Titanium §r§fdrops", "Gain §a5x §9Titanium §7drops\\.")
        ;

        private val patternName = name.lowercase().replace("_", ".")

        val chatPattern by RepoPattern.pattern("mining.hotm.skymall.chat.$patternName", chat)
        val itemPattern by RepoPattern.pattern("mining.hotm.skymall.item.$patternName", itemString)
    }

    enum class MayhemPerk(chat: String) {
        SCRAP_CHANCE("Your §r§9Suspicious Scrap §r§7chance was buffed by your §r§aMineshaft Mayhem §r§7perk!"),
        MINING_FORTUNE("You received a §r§a§r§6☘ Mining Fortune §r§7buff from your §r§aMineshaft Mayhem §r§7perk!"),
        MINING_SPEED("You received a §r§a§r§6⸕ Mining Speed §r§7buff from your §r§aMineshaft Mayhem §r§7perk!"),
        COLD_RESISTANCE("You received a §r§a§r§b❄ Cold Resistance §r§7buff from your §r§aMineshaft Mayhem §r§7perk!"),
        ABILITY_COOLDOWN("Your Pickaxe Ability cooldown was reduced §r§7from your §r§aMineshaft Mayhem §r§7perk!");

        private val patternName = name.lowercase().replace("_", ".")

        val chatPattern by RepoPattern.pattern("mining.hotm.mayhem.chat.$patternName", chat)
    }
}
