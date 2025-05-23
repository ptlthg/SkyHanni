package at.hannibal2.skyhanni.features.itemabilities.abilitycooldown

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.events.ActionBarUpdateEvent
import at.hannibal2.skyhanni.events.ItemClickEvent
import at.hannibal2.skyhanni.events.PlaySoundEvent
import at.hannibal2.skyhanni.events.RenderGuiItemOverlayEvent
import at.hannibal2.skyhanni.events.RenderItemTipEvent
import at.hannibal2.skyhanni.events.RenderObject
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniTickEvent
import at.hannibal2.skyhanni.features.itemabilities.abilitycooldown.ItemAbility.Companion.getMultiplier
import at.hannibal2.skyhanni.features.nether.ashfang.AshfangFreezeCooldown
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.InventoryUtils.recentlyHeld
import at.hannibal2.skyhanni.utils.ItemUtils
import at.hannibal2.skyhanni.utils.ItemUtils.cleanName
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getAbilityScrolls
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getItemId
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getItemUuid
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.equalsOneOf
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.mapKeysNotNull
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.client.Minecraft
import net.minecraft.item.ItemStack
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

@SkyHanniModule
object ItemAbilityCooldown {

    private val config get() = SkyHanniMod.feature.inventory.itemAbilities

    private val patternGroup = RepoPattern.group("item.abilities.cooldown")
    private val youAlignedOthersPattern by patternGroup.pattern(
        "alignedother",
        "§eYou aligned §r§a.* §r§eother players?!",
    )
    private val youBuffedYourselfPattern by patternGroup.pattern(
        "buffedyourself",
        "§aYou buffed yourself for §r§c\\+\\d+❁ Strength",
    )

    /**
     * REGEX-TEST: §63,848/3,473❤     §b-24 Mana (§6Instant Transmission§b)     §b2,507/2,507✎ Mana
     */
    private val abilityUsePattern by patternGroup.pattern(
        "abilityuse",
        ".*§b-\\d+ Mana \\(§6(?<type>.*)§b\\).*",
    )

    private var lastAbility = ""
    private var items = mapOf<String, List<ItemText>>()
    private var abilityItems = mapOf<ItemStack, MutableList<ItemAbility>>()
    private val WEIRD_TUBA = "WEIRD_TUBA".toInternalName()
    private val WEIRDER_TUBA = "WEIRDER_TUBA".toInternalName()
    private val VOODOO_DOLL_WILTED = "VOODOO_DOLL_WILTED".toInternalName()
    private val WARNING_FLARE = "WARNING_FLARE".toInternalName()
    private val ALERT_FLARE = "ALERT_FLARE".toInternalName()
    private val SOS_FLARE = "SOS_FLARE".toInternalName()
    private val TOTEM_OF_CORRUPTION = "TOTEM_OF_CORRUPTION".toInternalName()


    @HandleEvent
    fun onPlaySound(event: PlaySoundEvent) {
        when {
            // Hyperion
            event.soundName == "mob.zombie.remedy" && event.pitch == 0.6984127f && event.volume == 1f -> {
                val abilityScrolls = InventoryUtils.getItemInHand()?.getAbilityScrolls() ?: return
                if (abilityScrolls.size != 3) return

                ItemAbility.HYPERION.sound()
            }
            // Fire Fury Staff
            event.soundName == "liquid.lavapop" && event.pitch == 1f && event.volume == 1f -> {
                ItemAbility.FIRE_FURY_STAFF.sound()
            }
            // Ice Spray Wand
            event.soundName == "mob.enderdragon.growl" && event.pitch == 1f && event.volume == 1f -> {
                ItemAbility.ICE_SPRAY_WAND.sound()
            }
            // Gyrokinetic Wand & Shadow Fury
            event.soundName == "mob.endermen.portal" -> {
                // Gryokinetic Wand
                if (event.pitch == 0.61904764f && event.volume == 1f) {
                    ItemAbility.GYROKINETIC_WAND_LEFT.sound()
                }
                // Shadow Fury
                if (event.pitch == 1f && event.volume == 1f) {
                    val internalName = InventoryUtils.getItemInHand()?.getInternalName() ?: return
                    if (!internalName.equalsOneOf(
                            "SHADOW_FURY".toInternalName(),
                            "STARRED_SHADOW_FURY".toInternalName(),
                        )
                    ) return

                    ItemAbility.SHADOW_FURY.sound()
                }
            }
            // Giant's Sword
            event.soundName == "random.anvil_land" && event.pitch == 0.4920635f && event.volume == 1f -> {
                ItemAbility.GIANTS_SWORD.sound()
            }
            // Atomsplit Katana
            event.soundName == "mob.ghast.affectionate_scream" && event.pitch == 0.4920635f && event.volume == 0.15f -> {
                ItemAbility.ATOMSPLIT_KATANA.sound()
            }
            // Wand of Atonement
            event.soundName == "liquid.lavapop" && event.pitch == 0.7619048f && event.volume == 0.15f -> {
                ItemAbility.WAND_OF_ATONEMENT.sound()
            }
            // Starlight Wand
            event.soundName == "mob.bat.hurt" && event.volume == 0.1f -> {
                ItemAbility.STARLIGHT_WAND.sound()
            }
            // Voodoo Doll
            event.soundName == "mob.guardian.curse" && event.volume == 0.2f -> {
                ItemAbility.VOODOO_DOLL.sound()
            }
            // Jinxed Voodoo Doll Hit
            event.soundName == "random.successful_hit" && event.volume == 1f && event.pitch == 0.7936508f -> {
                ItemAbility.VOODOO_DOLL_WILTED.sound()
            }
            // Jinxed Voodoo Doll Miss
            event.soundName == "mob.ghast.scream" && event.volume == 1f && event.pitch >= 1.6 && event.pitch <= 1.7 -> {
                if (VOODOO_DOLL_WILTED.recentlyHeld()) {
                    ItemAbility.VOODOO_DOLL_WILTED.sound()
                }
            }
            // Golem Sword
            event.soundName == "random.explode" && event.pitch == 4.047619f && event.volume == 0.2f -> {
                ItemAbility.GOLEM_SWORD.sound()
            }
            // Weird Tuba & Weirder Tuba
            event.soundName == "mob.wolf.howl" && event.volume == 0.5f -> {
                if (WEIRD_TUBA.recentlyHeld()) {
                    ItemAbility.WEIRD_TUBA.sound()
                }
                if (WEIRDER_TUBA.recentlyHeld()) {
                    ItemAbility.WEIRDER_TUBA.sound()
                }
            }
            // End Stone Sword
            event.soundName == "mob.zombie.unfect" && event.pitch == 2f && event.volume == 0.3f -> {
                ItemAbility.END_STONE_SWORD.sound()
            }
            // Soul Esoward
            event.soundName == "mob.wolf.panting" && event.pitch == 1.3968254f && event.volume == 0.4f -> {
                ItemAbility.SOUL_ESOWARD.sound()
            }
            // Pigman Sword
            event.soundName == "mob.zombiepig.zpigangry" && event.pitch == 2f && event.volume == 0.3f -> {
                ItemAbility.PIGMAN_SWORD.sound()
            }
            // Ember Rod
            event.soundName == "mob.ghast.fireball" && event.pitch == 1f && event.volume == 0.3f -> {
                ItemAbility.EMBER_ROD.sound()
            }
            // Fire Freeze Staff
            event.soundName == "mob.guardian.elder.idle" && event.pitch == 2f && event.volume == 0.2f -> {
                ItemAbility.FIRE_FREEZE_STAFF.sound()
            }
            // Staff of the Volcano
            event.soundName == "random.explode" && event.pitch == 0.4920635f && event.volume == 0.5f -> {
                ItemAbility.STAFF_OF_THE_VOLCANO.sound()
            }
            // Staff of the Volcano
            event.soundName == "random.eat" && event.pitch == 1f && event.volume == 1f -> {
                ItemAbility.STAFF_OF_THE_VOLCANO.sound()
            }
            // Holy Ice
            event.soundName == "random.drink" && event.pitch.roundTo(1) == 1.8f && event.volume == 1f -> {
                ItemAbility.HOLY_ICE.sound()
            }
            // Royal Pigeon
            event.soundName == "mob.bat.idle" && event.pitch == 0.4920635f && event.volume == 1f -> {
                ItemAbility.ROYAL_PIGEON.sound()
            }
            // Wand of Strength
            event.soundName == "random.eat" && event.pitch == 0.4920635f && event.volume == 1f -> {
                ItemAbility.WAND_OF_STRENGTH.sound()
            }
            // Tactical Insertion
            event.soundName == "fire.ignite" && event.pitch == 0.74603176f && event.volume == 1f -> {
                ItemAbility.TACTICAL_INSERTION.activate(LorenzColor.DARK_PURPLE, 3_000)
            }

            event.soundName == "mob.zombie.remedy" && event.pitch == 1.8888888f && event.volume == 0.7f -> {
                ItemAbility.TACTICAL_INSERTION.activate(null, 17_000)
            }
            // Totem of Corruption
            event.soundName == "random.wood_click" && event.pitch == 0.84126985f && event.volume == 0.5f -> {
                if (TOTEM_OF_CORRUPTION.recentlyHeld()) {
                    ItemAbility.TOTEM_OF_CORRUPTION.sound()
                }
            }
            // Enrager
            event.soundName == "mob.enderdragon.growl" && event.pitch == 0.4920635f && event.volume == 2f -> {
                ItemAbility.ENRAGER.sound()
            }

            // Blaze Slayer Flares
            event.soundName == "fireworks.launch" && event.pitch == 1f && event.volume == 3f -> {
                if (WARNING_FLARE.recentlyHeld() || ALERT_FLARE.recentlyHeld()) {
                    ItemAbility.ALERT_FLARE.sound()
                }
                if (SOS_FLARE.recentlyHeld()) {
                    ItemAbility.SOS_FLARE.sound()
                }
            }
        }
    }

    @HandleEvent
    fun onItemClick(event: ItemClickEvent) {
        if (AshfangFreezeCooldown.isCurrentlyFrozen()) return
        handleItemClick(event.itemInHand)
    }

    private fun handleItemClick(itemInHand: ItemStack?) {
        if (!LorenzUtils.inSkyBlock) return
        itemInHand?.getInternalName()?.run {
            ItemAbility.getByInternalName(this)?.setItemClick()
        }
    }

    @HandleEvent
    fun onWorldChange() {
        for (ability in ItemAbility.entries) {
            ability.lastActivation = SimpleTimeMark.farPast()
            ability.specialColor = null
        }
    }

    @HandleEvent
    fun onActionBarUpdate(event: ActionBarUpdateEvent) {
        if (!isEnabled()) return

        val message: String = event.actionBar
        handleOldAbilities(message)

        when {
            message.contains("§lCASTING IN ") -> {
                if (!ItemAbility.RAGNAROCK_AXE.isOnCooldown()) {
                    ItemAbility.RAGNAROCK_AXE.activate(LorenzColor.WHITE, 3_000)
                }
            }

            message.contains("§lCASTING") -> {
                if (ItemAbility.RAGNAROCK_AXE.specialColor != LorenzColor.DARK_PURPLE) {
                    ItemAbility.RAGNAROCK_AXE.activate(LorenzColor.DARK_PURPLE, 10_000)
                }
            }

            message.contains("§c§lCANCELLED") -> {
                ItemAbility.RAGNAROCK_AXE.activate(null, 17_000)
            }
        }
    }

    private fun handleOldAbilities(message: String) {
        abilityUsePattern.matchMatcher(message) {
            val name = group("type")
            if (name == lastAbility) return
            lastAbility = name
            for (ability in ItemAbility.entries) {
                if (ability.abilityName == name) {
                    click(ability)
                    return
                }
            }
            return
        }
        lastAbility = ""
    }

    private fun isEnabled(): Boolean = LorenzUtils.inSkyBlock && config.itemAbilityCooldown

    private fun click(ability: ItemAbility) {
        if (ability.actionBarDetection) {
            ability.activate()
        }
    }

    @HandleEvent
    fun onTick(event: SkyHanniTickEvent) {
        if (!isEnabled()) return

        checkHotBar(event.isMod(10))
    }

    private fun checkHotBar(recheckInventorySlots: Boolean = false) {
        if (recheckInventorySlots || abilityItems.isEmpty()) {
            abilityItems = ItemUtils.getItemsInInventory(true).associateWith { hasAbility(it) }
        }

        items = abilityItems.entries.associateByTo(
            mutableMapOf(),
            { it.key.getIdentifier() },
            { kp -> kp.value.map { createItemText(it) } },
        ).mapKeysNotNull { it.key }

    }

    private fun createItemText(ability: ItemAbility): ItemText {
        val specialColor = ability.specialColor
        val readyText = if (config.itemAbilityShowWhenReady) "R" else ""
        return if (ability.isOnCooldown()) {
            val duration = ability.lastActivation + ability.getCooldown() - SimpleTimeMark.now()
            val color = specialColor ?: if (duration < 600.milliseconds) LorenzColor.RED else LorenzColor.YELLOW
            ItemText(color, ability.getDurationText(), true, ability.alternativePosition)
        } else {
            if (specialColor != null) {
                ability.specialColor = null
                tryHandleNextPhase(ability, specialColor)
                return createItemText(ability)
            }
            ItemText(LorenzColor.GREEN, readyText, false, ability.alternativePosition)
        }
    }

    private fun tryHandleNextPhase(ability: ItemAbility, specialColor: LorenzColor) {
        if (ability == ItemAbility.GYROKINETIC_WAND_RIGHT && specialColor == LorenzColor.BLUE) {
            ability.activate(null, 4_000)
        }
        if (ability == ItemAbility.RAGNAROCK_AXE && specialColor == LorenzColor.DARK_PURPLE) {
            ability.activate(null, max((20_000 * ability.getMultiplier()) - 13_000, 0.0).toInt())
        }
    }

    @HandleEvent
    fun onRenderItemTip(event: RenderItemTipEvent) {
        if (!isEnabled()) return

        val stack = event.stack

        val guiOpen = Minecraft.getMinecraft().currentScreen != null
        val uuid = stack.getIdentifier() ?: return
        val list = items[uuid] ?: return

        for (itemText in list) {
            if (guiOpen && !itemText.onCooldown) continue
            val color = itemText.color
            val renderObject = RenderObject(color.getChatColor() + itemText.text)
            if (itemText.alternativePosition) {
                renderObject.offsetX = -8
                renderObject.offsetY = -10
            }
            event.renderObjects.add(renderObject)
        }
    }

    @HandleEvent
    fun onRenderItem(event: RenderGuiItemOverlayEvent) {
        if (!isEnabled()) return
        if (!config.itemAbilityCooldownBackground) return

        val guiOpen = Minecraft.getMinecraft().currentScreen != null
        val stack = event.stack

        val uuid = stack?.getIdentifier() ?: return
        val list = items[uuid] ?: return

        for (itemText in list) {
            if (guiOpen && !itemText.onCooldown) continue
            val color = itemText.color

            // fix multiple problems when having multiple abilities
            var opacity = 130
            if (color == LorenzColor.GREEN) {
                opacity = 80
                if (!config.itemAbilityShowWhenReady) return
            }
            event.highlight(color.addOpacity(opacity))
        }
    }

    private fun ItemStack.getIdentifier() = getItemUuid() ?: getItemId()

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!isEnabled()) return

        val message = event.message
        if (message == "§dCreeper Veil §r§aActivated!") {
            ItemAbility.WITHER_CLOAK.activate(LorenzColor.LIGHT_PURPLE)
        }
        if (message == "§dCreeper Veil §r§cDe-activated! §r§8(Expired)" ||
            message == "§cNot enough mana! §r§dCreeper Veil §r§cDe-activated!"
        ) {
            ItemAbility.WITHER_CLOAK.activate()
        }
        if (message == "§dCreeper Veil §r§cDe-activated!") {
            ItemAbility.WITHER_CLOAK.activate(null, 5000)
        }

        youAlignedOthersPattern.matchMatcher(message) {
            ItemAbility.GYROKINETIC_WAND_RIGHT.activate(LorenzColor.BLUE, 6_000)
        }
        if (message == "§eYou §r§aaligned §r§eyourself!") {
            ItemAbility.GYROKINETIC_WAND_RIGHT.activate(LorenzColor.BLUE, 6_000)
        }
        if (message == "§cRagnarock was cancelled due to being hit!") {
            ItemAbility.RAGNAROCK_AXE.activate(null, 17_000)
        }
        youBuffedYourselfPattern.matchMatcher(message) {
            ItemAbility.SWORD_OF_BAD_HEALTH.activate()
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(31, "itemAbilities", "inventory.itemAbilities")
    }

    private fun hasAbility(stack: ItemStack): MutableList<ItemAbility> {
        val itemName: String = stack.cleanName()
        val internalName = stack.getInternalName()

        val list = mutableListOf<ItemAbility>()
        for (ability in ItemAbility.entries) {
            if (ability.newVariant) {
                if (ability.internalNames.contains(internalName)) {
                    list.add(ability)
                }
            } else {
                for (name in ability.itemNames) {
                    if (itemName.contains(name)) {
                        list.add(ability)
                    }
                }
            }
        }
        return list
    }

    private fun ItemAbility.sound() {
        val ping = lastItemClick.passedSince()
        if (ping < 400.milliseconds) {
            activate()
        }
    }

    class ItemText(
        val color: LorenzColor,
        val text: String,
        val onCooldown: Boolean,
        val alternativePosition: Boolean,
    )
}
