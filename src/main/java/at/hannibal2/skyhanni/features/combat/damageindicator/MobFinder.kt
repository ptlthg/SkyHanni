package at.hannibal2.skyhanni.features.combat.damageindicator

import at.hannibal2.skyhanni.data.ElectionApi.derpy
import at.hannibal2.skyhanni.data.ElectionApi.ignoreDerpy
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.features.dungeon.DungeonApi
import at.hannibal2.skyhanni.features.dungeon.DungeonLividFinder
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.features.garden.pests.PestType
import at.hannibal2.skyhanni.features.rift.RiftApi
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.EntityUtils.hasBossHealth
import at.hannibal2.skyhanni.utils.EntityUtils.hasMaxHealth
import at.hannibal2.skyhanni.utils.EntityUtils.hasNameTagWith
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzUtils.baseMaxHealth
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SimpleTimeMark.Companion.fromNow
import at.hannibal2.skyhanni.utils.getLorenzVec
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLiving
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.boss.EntityDragon
import net.minecraft.entity.boss.EntityWither
import net.minecraft.entity.monster.EntityBlaze
import net.minecraft.entity.monster.EntityEnderman
import net.minecraft.entity.monster.EntityGhast
import net.minecraft.entity.monster.EntityGiantZombie
import net.minecraft.entity.monster.EntityGuardian
import net.minecraft.entity.monster.EntityIronGolem
import net.minecraft.entity.monster.EntityMagmaCube
import net.minecraft.entity.monster.EntityPigZombie
import net.minecraft.entity.monster.EntitySilverfish
import net.minecraft.entity.monster.EntitySkeleton
import net.minecraft.entity.monster.EntitySlime
import net.minecraft.entity.monster.EntitySpider
import net.minecraft.entity.monster.EntityZombie
import net.minecraft.entity.passive.EntityBat
import net.minecraft.entity.passive.EntityHorse
import net.minecraft.entity.passive.EntityWolf
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MobFinder {

    // F1
    private var floor1bonzo1 = false
    private var floor1bonzo1SpawnTime = SimpleTimeMark.farPast()
    private var floor1bonzo2 = false
    private var floor1bonzo2SpawnTime = SimpleTimeMark.farPast()

    // F2
    private var floor2summons1 = false
    private var floor2summons1SpawnTime = SimpleTimeMark.farPast()
    private val floor2summonsDiedOnce = mutableListOf<EntityOtherPlayerMP>()
    private var floor2secondPhase = false
    private var floor2secondPhaseSpawnTime = SimpleTimeMark.farPast()

    // F3
    private var floor3GuardianShield = false
    private var floor3GuardianShieldSpawnTime = SimpleTimeMark.farPast()
    private val guardians = mutableListOf<EntityGuardian>()
    private var floor3Professor = false
    private var floor3ProfessorSpawnTime = SimpleTimeMark.farPast()
    private var floor3ProfessorGuardianPrepare = false
    private var floor3ProfessorGuardianPrepareSpawnTime = SimpleTimeMark.farPast()
    private var floor3ProfessorGuardian = false
    private var floor3ProfessorGuardianEntity: EntityGuardian? = null

    // F5
    private var floor5lividEntity: EntityOtherPlayerMP? = null
    private var floor5lividEntitySpawnTime = SimpleTimeMark.farPast()
    private val correctLividPattern = "§c\\[BOSS] (.*) Livid§r§f: Impossible! How did you figure out which one I was\\?!".toPattern()

    // F6
    private var floor6Giants = false
    private var floor6GiantsSpawnTime = SimpleTimeMark.farPast()
    private val floor6GiantsSeparateDelay = mutableMapOf<UUID, Pair<Duration, BossType>>()
    private var floor6Sadan = false
    private var floor6SadanSpawnTime = SimpleTimeMark.farPast()

    internal fun tryAdd(entity: EntityLivingBase) = when {
        DungeonApi.inDungeon() -> tryAddDungeon(entity)
        RiftApi.inRift() -> tryAddRift(entity)
        GardenApi.inGarden() -> tryAddGarden(entity)
        else -> {
            if (entity is EntityLiving && entity.hasNameTagWith(2, "Dummy §a10M§c❤")) {
                EntityResult(bossType = BossType.DUMMY)
            } else {
                when (entity) {
                    /*
                     * Note that the order does matter here.
                     * For example, if you put EntityZombie before EntityPigZombie,
                     * EntityPigZombie will never be reached because EntityPigZombie extends EntityZombie.
                     * Please take this into consideration if you are to modify this.
                     */
                    is EntityOtherPlayerMP -> tryAddEntityOtherPlayerMP(entity)
                    is EntityIronGolem -> tryAddEntityIronGolem(entity)
                    is EntityPigZombie -> tryAddEntityPigZombie(entity)
                    is EntityMagmaCube -> tryAddEntityMagmaCube(entity)
                    is EntityEnderman -> tryAddEntityEnderman(entity)
                    is EntitySkeleton -> tryAddEntitySkeleton(entity)
                    is EntityGuardian -> tryAddEntityGuardian(entity)
                    is EntityZombie -> tryAddEntityZombie(entity)
                    is EntityWither -> tryAddEntityWither(entity)
                    is EntityDragon -> tryAddEntityDragon(entity)
                    is EntitySpider -> tryAddEntitySpider(entity)
                    is EntityHorse -> tryAddEntityHorse(entity)
                    is EntityBlaze -> tryAddEntityBlaze(entity)
                    is EntityWolf -> tryAddEntityWolf(entity)
                    else -> null
                }
            }
        }
    }

    private fun tryAddGarden(entity: EntityLivingBase): EntityResult? {
        if (entity is EntitySilverfish || entity is EntityBat) {
            return tryAddGardenPest(entity)
        }

        return null
    }

    private fun tryAddGardenPest(entity: EntityLivingBase): EntityResult? {
        if (!GardenApi.inGarden()) return null

        return PestType.filterableEntries.firstOrNull { entity.hasNameTagWith(3, it.displayName) }
            ?.let { EntityResult(bossType = it.damageIndicatorBoss) }
    }

    private fun tryAddDungeon(entity: EntityLivingBase) = when {
        DungeonApi.isOneOf("F1", "M1") -> tryAddDungeonF1(entity)
        DungeonApi.isOneOf("F2", "M2") -> tryAddDungeonF2(entity)
        DungeonApi.isOneOf("F3", "M3") -> tryAddDungeonF3(entity)
        DungeonApi.isOneOf("F4", "M4") -> tryAddDungeonF4(entity)
        DungeonApi.isOneOf("F5", "M5") -> tryAddDungeonF5(entity)
        DungeonApi.isOneOf("F6", "M6") -> tryAddDungeonF6(entity)
        else -> null
    }

    private fun tryAddDungeonF1(entity: EntityLivingBase) = when {
        floor1bonzo1 && entity is EntityOtherPlayerMP && entity.name == "Bonzo " -> {
            EntityResult(floor1bonzo1SpawnTime, bossType = BossType.DUNGEON_F1_BONZO_FIRST)
        }

        floor1bonzo2 && entity is EntityOtherPlayerMP && entity.name == "Bonzo " -> {
            EntityResult(floor1bonzo2SpawnTime, bossType = BossType.DUNGEON_F1_BONZO_SECOND, finalDungeonBoss = true)
        }

        else -> null
    }

    private fun tryAddDungeonF2(entity: EntityLivingBase): EntityResult? {
        if (entity.name == "Summon " && entity is EntityOtherPlayerMP) {
            if (floor2summons1 && !floor2summonsDiedOnce.contains(entity)) {
                if (entity.health.toInt() != 0) {
                    return EntityResult(floor2summons1SpawnTime, bossType = BossType.DUNGEON_F2_SUMMON)
                }
                floor2summonsDiedOnce.add(entity)
            }
            if (floor2secondPhase) {
                return EntityResult(floor2secondPhaseSpawnTime, bossType = BossType.DUNGEON_F2_SUMMON)
            }
        }

        if (floor2secondPhase && entity is EntityOtherPlayerMP) {
            // TODO only show scarf after (all/at least x) summons are dead?
            if (entity.name == "Scarf ") {
                return EntityResult(
                    floor2secondPhaseSpawnTime,
                    finalDungeonBoss = true,
                    bossType = BossType.DUNGEON_F2_SCARF,
                )
            }
        }
        return null
    }

    private fun tryAddDungeonF3(entity: EntityLivingBase): EntityResult? {
        if (entity is EntityGuardian && floor3GuardianShield) {
            if (guardians.size == 4) {
                calcGuardiansTotalHealth()
            } else {
                findGuardians()
            }
            if (guardians.contains(entity)) {
                return EntityResult(floor3GuardianShieldSpawnTime, true, bossType = BossType.DUNGEON_F3_GUARDIAN)
            }
        }

        if (floor3Professor && entity is EntityOtherPlayerMP && entity.name == "The Professor") {
            return EntityResult(
                floor3ProfessorSpawnTime,
                floor3ProfessorSpawnTime.passedSince() > 1.seconds,
                bossType = BossType.DUNGEON_F3_PROFESSOR_1,
            )
        }
        if (floor3ProfessorGuardianPrepare && entity is EntityOtherPlayerMP && entity.name == "The Professor") {
            return EntityResult(
                floor3ProfessorGuardianPrepareSpawnTime,
                true,
                bossType = BossType.DUNGEON_F3_PROFESSOR_2,
            )
        }

        if (entity is EntityGuardian && floor3ProfessorGuardian && entity == floor3ProfessorGuardianEntity) {
            return EntityResult(finalDungeonBoss = true, bossType = BossType.DUNGEON_F3_PROFESSOR_2)
        }
        return null
    }

    private fun tryAddDungeonF4(entity: EntityLivingBase): EntityResult? {
        if (entity is EntityGhast) {
            return EntityResult(
                bossType = BossType.DUNGEON_F4_THORN,
                ignoreBlocks = true,
                finalDungeonBoss = true,
            )
        }
        return null
    }

    private fun tryAddDungeonF5(entity: EntityLivingBase): EntityResult? {
        if (entity is EntityOtherPlayerMP && entity == DungeonLividFinder.livid) {
            return EntityResult(
                bossType = BossType.DUNGEON_F5,
                ignoreBlocks = true,
                finalDungeonBoss = true,
            )
        }
        return null
    }

    private fun tryAddDungeonF6(entity: EntityLivingBase): EntityResult? {
        if (entity !is EntityGiantZombie || entity.isInvisible) return null
        if (floor6Giants && entity.posY > 68) {
            val (extraDelay, bossType) = checkExtraF6GiantsDelay(entity)
            return EntityResult(
                floor6GiantsSpawnTime + extraDelay + 5.seconds,
                floor6GiantsSpawnTime.passedSince() > extraDelay,
                bossType = bossType,
            )
        }

        if (floor6Sadan) {
            return EntityResult(floor6SadanSpawnTime, finalDungeonBoss = true, bossType = BossType.DUNGEON_F6_SADAN, ignoreBlocks = true)
        }
        return null
    }

    private fun tryAddRift(entity: EntityLivingBase): EntityResult? {
        if (entity is EntityOtherPlayerMP) {
            if (entity.name == "Leech Supreme") {
                return EntityResult(bossType = BossType.LEECH_SUPREME)
            }

            if (entity.name == "Bloodfiend ") {
                // there is no derpy in rift
                val hp = entity.baseMaxHealth.ignoreDerpy()
                when {
                    entity.hasMaxHealth(625, true, hp) -> return EntityResult(bossType = BossType.SLAYER_BLOODFIEND_1)
                    entity.hasMaxHealth(1_100, true, hp) -> return EntityResult(bossType = BossType.SLAYER_BLOODFIEND_2)
                    entity.hasMaxHealth(1_800, true, hp) -> return EntityResult(bossType = BossType.SLAYER_BLOODFIEND_3)
                    entity.hasMaxHealth(2_400, true, hp) -> return EntityResult(bossType = BossType.SLAYER_BLOODFIEND_4)
                    entity.hasMaxHealth(3_000, true, hp) -> return EntityResult(bossType = BossType.SLAYER_BLOODFIEND_5)
                }
            }
        }
        if (entity is EntitySlime && entity.baseMaxHealth == 1_000) {
            return EntityResult(bossType = BossType.BACTE)
        }
        if (entity is EntityOtherPlayerMP && entity.baseMaxHealth == 250 && entity.name == "Sun Gecko") {
            return EntityResult(bossType = BossType.SUN_GECKO)

        }
        return null
    }

    private fun tryAddEntityBlaze(entity: EntityLivingBase) = when {
        entity.name != "Dinnerbone" &&
            entity.hasNameTagWith(2, "§e﴾ §8[§7Lv200§8] §l§8§lAshfang§r ") &&
            entity.hasMaxHealth(
                50_000_000,
                true,
            ) -> {
            EntityResult(bossType = BossType.NETHER_ASHFANG)
        }

        entity.hasNameTagWith(2, "§c☠ §bInferno Demonlord ") -> {
            when {
                entity.hasBossHealth(2_500_000) -> EntityResult(bossType = BossType.SLAYER_BLAZE_1)
                entity.hasBossHealth(10_000_000) -> EntityResult(bossType = BossType.SLAYER_BLAZE_2)
                entity.hasBossHealth(45_000_000) -> EntityResult(bossType = BossType.SLAYER_BLAZE_3)
                entity.hasBossHealth(150_000_000) -> EntityResult(bossType = BossType.SLAYER_BLAZE_4)
                else -> null
            }
        }

        else -> null
    }

    private fun tryAddEntitySkeleton(entity: EntityLivingBase) = when {
        entity.hasNameTagWith(2, "§c☠ §3ⓆⓊⒶⓏⒾⒾ ") -> {
            when {
                entity.hasBossHealth(10_000_000) -> EntityResult(bossType = BossType.SLAYER_BLAZE_QUAZII_4)
                entity.hasBossHealth(5_000_000) -> EntityResult(bossType = BossType.SLAYER_BLAZE_QUAZII_3)
                entity.hasBossHealth(1_750_000) -> EntityResult(bossType = BossType.SLAYER_BLAZE_QUAZII_2)
                entity.hasBossHealth(500_000) -> EntityResult(bossType = BossType.SLAYER_BLAZE_QUAZII_1)
                else -> null
            }
        }

        entity.hasNameTagWith(5, "§e﴾ §8[§7Lv200§8] §l§8§lBladesoul§r ") -> {
            EntityResult(bossType = BossType.NETHER_BLADESOUL)
        }

        else -> null
    }

    private fun tryAddEntityOtherPlayerMP(entity: EntityLivingBase) = when {
        entity.name == "Mage Outlaw" -> EntityResult(bossType = BossType.NETHER_MAGE_OUTLAW)
        entity.name == "DukeBarb " &&
            entity.getLorenzVec()
                .distanceToPlayer() < 30 -> EntityResult(bossType = BossType.NETHER_BARBARIAN_DUKE)

        entity.name == "Minos Inquisitor" -> EntityResult(bossType = BossType.MINOS_INQUISITOR)
        entity.name == "Minos Champion" -> EntityResult(bossType = BossType.MINOS_CHAMPION)
        entity.name == "Minotaur " -> EntityResult(bossType = BossType.MINOTAUR)
        entity.name == "Ragnarok" -> EntityResult(bossType = BossType.RAGNAROK)

        else -> null
    }

    private fun tryAddEntityWither(entity: EntityLivingBase) = when {
        entity.hasNameTagWith(4, "§8[§7Lv100§8] §c§5Vanquisher§r ") -> {
            EntityResult(bossType = BossType.NETHER_VANQUISHER)
        }

        else -> null
    }

    private fun tryAddEntityEnderman(entity: EntityLivingBase): EntityResult? {
        if (!entity.hasNameTagWith(3, "§c☠ §bVoidgloom Seraph ")) return null

        return when {
            entity.hasMaxHealth(300_000, true) -> EntityResult(bossType = BossType.SLAYER_ENDERMAN_1)
            entity.hasMaxHealth(12_000_000, true) -> EntityResult(bossType = BossType.SLAYER_ENDERMAN_2)
            entity.hasMaxHealth(50_000_000, true) -> EntityResult(bossType = BossType.SLAYER_ENDERMAN_3)
            entity.hasMaxHealth(210_000_000, true) -> EntityResult(bossType = BossType.SLAYER_ENDERMAN_4)
            else -> null
        }
    }

    // TODO testing and use sidebar data
    @Suppress("UnusedParameter")
    private fun tryAddEntityDragon(entity: EntityLivingBase) = when {
        IslandType.THE_END.isInIsland() -> EntityResult(bossType = BossType.END_ENDER_DRAGON)
        IslandType.WINTER.isInIsland() -> EntityResult(bossType = BossType.WINTER_REINDRAKE)

        else -> null
    }

    private fun tryAddEntityIronGolem(entity: EntityLivingBase) = when {
        entity.hasNameTagWith(3, "§e﴾ §8[§7Lv100§8] §lEndstone Protector§r ") -> {
            EntityResult(bossType = BossType.END_ENDSTONE_PROTECTOR)
        }

        entity.hasMaxHealth(1_500_000) -> {
            EntityResult(bossType = BossType.GAIA_CONSTRUCT)
        }

        entity.hasMaxHealth(100_000_000) -> {
            EntityResult(bossType = BossType.LORD_JAWBUS)
        }

        else -> null
    }

    private fun tryAddEntityZombie(entity: EntityLivingBase) = when {
        entity.hasNameTagWith(2, "§c☠ §bRevenant Horror") -> {
            when {
                entity.hasMaxHealth(500, true) -> EntityResult(bossType = BossType.SLAYER_ZOMBIE_1)
                entity.hasMaxHealth(20_000, true) -> EntityResult(bossType = BossType.SLAYER_ZOMBIE_2)
                entity.hasMaxHealth(400_000, true) -> EntityResult(bossType = BossType.SLAYER_ZOMBIE_3)
                entity.hasMaxHealth(1_500_000, true) -> EntityResult(bossType = BossType.SLAYER_ZOMBIE_4)

                else -> null
            }
        }

        entity.hasNameTagWith(2, "§c☠ §fAtoned Horror ") && entity.hasMaxHealth(10_000_000, true) -> {
            EntityResult(bossType = BossType.SLAYER_ZOMBIE_5)
        }

        else -> null
    }

    private fun tryAddEntityMagmaCube(entity: EntityLivingBase) = when {
        entity.hasNameTagWith(15, "§e﴾ §8[§7Lv500§8] §l§4§lMagma Boss§r ") && entity.hasMaxHealth(200_000_000, true) -> {
            EntityResult(bossType = BossType.NETHER_MAGMA_BOSS, ignoreBlocks = true)
        }

        else -> null
    }

    private fun tryAddEntityHorse(entity: EntityLivingBase) = when {
        entity.hasNameTagWith(15, "§8[§7Lv100§8] §c§6Headless Horseman§r ") && entity.hasMaxHealth(3_000_000, true) -> {
            EntityResult(bossType = BossType.HUB_HEADLESS_HORSEMAN)
        }

        else -> null
    }

    private fun tryAddEntityPigZombie(entity: EntityLivingBase) = if (entity.hasNameTagWith(2, "§c☠ §6ⓉⓎⓅⒽⓄⒺⓊⓈ ")) {
        when {
            entity.hasBossHealth(10_000_000) -> EntityResult(bossType = BossType.SLAYER_BLAZE_TYPHOEUS_4)
            entity.hasBossHealth(5_000_000) -> EntityResult(bossType = BossType.SLAYER_BLAZE_TYPHOEUS_3)
            entity.hasBossHealth(1_750_000) -> EntityResult(bossType = BossType.SLAYER_BLAZE_TYPHOEUS_2)
            entity.hasBossHealth(500_000) -> EntityResult(bossType = BossType.SLAYER_BLAZE_TYPHOEUS_1)
            else -> null
        }
    } else null

    private fun tryAddEntitySpider(entity: EntityLivingBase): EntityResult? {
        if (entity.hasNameTagWith(1, "§5☠ §4Tarantula Broodfather ")) {
            when {
                entity.hasMaxHealth(740, true) -> return EntityResult(bossType = BossType.SLAYER_SPIDER_1)
                entity.hasMaxHealth(30_000, true) -> return EntityResult(bossType = BossType.SLAYER_SPIDER_2)
                entity.hasMaxHealth(900_000, true) -> return EntityResult(bossType = BossType.SLAYER_SPIDER_3)
                entity.hasMaxHealth(2_400_000, true) -> return EntityResult(bossType = BossType.SLAYER_SPIDER_4)
            }
        }
        if (entity.hasNameTagWith(1, "[§7Lv12§8] §4Broodmother")) {
            if (entity.hasMaxHealth(6000)) {
                return EntityResult(bossType = BossType.BROODMOTHER)
            }
        }
        checkArachne(entity as EntitySpider)?.let { return it }
        return null
    }

    private fun checkArachne(entity: EntitySpider): EntityResult? {
        if (entity.hasNameTagWith(1, "[§7Lv300§8] §cArachne") || entity.hasNameTagWith(1, "[§7Lv300§8] §lArachne")) {
            val maxHealth = entity.baseMaxHealth
            // Ignore the minis
            if (maxHealth == 12 || maxHealth.derpy() == 4000) return null
            return EntityResult(bossType = BossType.ARACHNE_SMALL)
        }
        if (entity.hasNameTagWith(1, "[§7Lv500§8] §cArachne") || entity.hasNameTagWith(1, "[§7Lv500§8] §lArachne")) {
            val maxHealth = entity.baseMaxHealth
            if (maxHealth == 12 || maxHealth.derpy() == 20_000) return null
            return EntityResult(bossType = BossType.ARACHNE_BIG)
        }

        return null
    }

    private fun tryAddEntityWolf(entity: EntityLivingBase) = if (entity.hasNameTagWith(1, "§c☠ §fSven Packmaster ")) {
        when {
            entity.hasMaxHealth(2_000, true) -> EntityResult(bossType = BossType.SLAYER_WOLF_1)
            entity.hasMaxHealth(40_000, true) -> EntityResult(bossType = BossType.SLAYER_WOLF_2)
            entity.hasMaxHealth(750_000, true) -> EntityResult(bossType = BossType.SLAYER_WOLF_3)
            entity.hasMaxHealth(2_000_000, true) -> EntityResult(bossType = BossType.SLAYER_WOLF_4)
            else -> null
        }
    } else null

    private fun tryAddEntityGuardian(entity: EntityLivingBase) = if (entity.hasMaxHealth(35_000_000)) {
        EntityResult(bossType = BossType.THUNDER)
    } else null

    private fun checkExtraF6GiantsDelay(entity: EntityGiantZombie): Pair<Duration, BossType> {
        val uuid = entity.uniqueID

        floor6GiantsSeparateDelay[uuid]?.let {
            return it
        }

        val middle = LorenzVec(-8, 0, 56)

        val loc = entity.getLorenzVec()

        val pos: Int

        val type: BossType
        if (loc.x > middle.x && loc.z > middle.z) {
            // first
            pos = 2
            type = BossType.DUNGEON_F6_GIANT_3
        } else if (loc.x > middle.x && loc.z < middle.z) {
            // second
            pos = 3
            type = BossType.DUNGEON_F6_GIANT_4
        } else if (loc.x < middle.x && loc.z < middle.z) {
            // third
            pos = 0
            type = BossType.DUNGEON_F6_GIANT_1
        } else if (loc.x < middle.x && loc.z > middle.z) {
            // fourth
            pos = 1
            type = BossType.DUNGEON_F6_GIANT_2
        } else {
            pos = 0
            type = BossType.DUNGEON_F6_GIANT_1
        }

        val extraDelay = 900.milliseconds * pos
        val pair = extraDelay to type
        floor6GiantsSeparateDelay[uuid] = pair

        return pair
    }

    fun handleChat(message: String) {
        if (!DungeonApi.inDungeon()) return
        when (message) {
            // F1
            "§c[BOSS] Bonzo§r§f: Gratz for making it this far, but I'm basically unbeatable." -> {
                floor1bonzo1 = true
                floor1bonzo1SpawnTime = 11.25.seconds.fromNow()
            }

            "§c[BOSS] Bonzo§r§f: Oh noes, you got me.. what ever will I do?!" -> {
                floor1bonzo1 = false
            }

            "§c[BOSS] Bonzo§r§f: Oh I'm dead!" -> {
                floor1bonzo2 = true
                floor1bonzo2SpawnTime = 4.2.seconds.fromNow()
            }

            "§c[BOSS] Bonzo§r§f: Alright, maybe I'm just weak after all.." -> {
                floor1bonzo2 = false
            }

            // F2
            "§c[BOSS] Scarf§r§f: ARISE, MY CREATIONS!" -> {
                floor2summons1 = true
                floor2summons1SpawnTime = 3.5.seconds.fromNow()
            }

            "§c[BOSS] Scarf§r§f: Those toys are not strong enough I see." -> {
                floor2summons1 = false
            }

            "§c[BOSS] Scarf§r§f: Don't get too excited though." -> {
                floor2secondPhase = true
                floor2secondPhaseSpawnTime = 6.3.seconds.fromNow()
            }

            "§c[BOSS] Scarf§r§f: Whatever..." -> {
                floor2secondPhase = false
            }

            // F3
            "§c[BOSS] The Professor§r§f: I was burdened with terrible news recently..." -> {
                floor3GuardianShield = true
                floor3GuardianShieldSpawnTime = 15.4.seconds.fromNow()
            }

            "§c[BOSS] The Professor§r§f: Oh? You found my Guardians' one weakness?" -> {
                floor3GuardianShield = false
                DamageIndicatorManager.removeDamageIndicator(BossType.DUNGEON_F3_GUARDIAN)
                floor3Professor = true
                floor3ProfessorSpawnTime = 10.3.seconds.fromNow()
            }

            "§c[BOSS] The Professor§r§f: I see. You have forced me to use my ultimate technique." -> {
                floor3Professor = false

                floor3ProfessorGuardianPrepare = true
                floor3ProfessorGuardianPrepareSpawnTime = 10.5.seconds.fromNow()
            }

            "§c[BOSS] The Professor§r§f: The process is irreversible, but I'll be stronger than a Wither now!" -> {
                floor3ProfessorGuardian = true
            }

            "§c[BOSS] The Professor§r§f: What?! My Guardian power is unbeatable!" -> {
                floor3ProfessorGuardian = false
            }

            // F5
            "§c[BOSS] Livid§r§f: This Orb you see, is Thorn, or what is left of him." -> {
                floor5lividEntity = DungeonLividFinder.livid
                floor5lividEntitySpawnTime = 13.seconds.fromNow()
            }

            // F6
            "§c[BOSS] Sadan§r§f: ENOUGH!" -> {
                floor6Giants = true
                floor6GiantsSpawnTime = 2.8.seconds.fromNow()
            }

            "§c[BOSS] Sadan§r§f: You did it. I understand now, you have earned my respect." -> {
                floor6Giants = false
                floor6Sadan = true
                floor6SadanSpawnTime = 11.5.seconds.fromNow()
            }

            "§c[BOSS] Sadan§r§f: NOOOOOOOOO!!! THIS IS IMPOSSIBLE!!" -> {
                floor6Sadan = false
            }
        }

        correctLividPattern.matchMatcher(message) {
            floor5lividEntity = null
        }
    }

    fun handleNewEntity(entity: Entity) {
        if (DungeonApi.inDungeon() && floor3ProfessorGuardian && entity is EntityGuardian && floor3ProfessorGuardianEntity == null) {
            floor3ProfessorGuardianEntity = entity
            floor3ProfessorGuardianPrepare = false
        }
    }

    private fun findGuardians() {
        guardians.clear()

        for (entity in EntityUtils.getEntities<EntityGuardian>()) {
            // F3
            if (entity.hasMaxHealth(1_000_000, true) || entity.hasMaxHealth(1_200_000, true)) {
                guardians.add(entity)
            }

            // M3
            if (entity.hasMaxHealth(120_000_000, true) || entity.hasMaxHealth(240_000_000, true)) {
                guardians.add(entity)
            }
            // M3 Reinforced Guardian
            if (entity.hasMaxHealth(140_000_000, true) || entity.hasMaxHealth(280_000_000, true)) {
                guardians.add(entity)
            }
        }
    }

    private fun calcGuardiansTotalHealth() {
        var totalHealth = 0
        for (guardian in guardians) {
            totalHealth += guardian.health.toInt()
        }
        if (totalHealth == 0) {
            floor3GuardianShield = false
            guardians.clear()
        }
    }
}
