package at.hannibal2.skyhanni.features.fishing

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.BlockUtils.getBlockAt
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.EntityUtils.hasSkullTexture
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.RenderUtils.drawString
import at.hannibal2.skyhanni.utils.RenderUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.SkullTextureHolder
import at.hannibal2.skyhanni.utils.SpecialColor.toSpecialColor
import at.hannibal2.skyhanni.utils.getLorenzVec
import net.minecraft.entity.item.EntityArmorStand

@SkyHanniModule
object ThunderSparksHighlight {

    private val config get() = SkyHanniMod.feature.fishing.thunderSpark
    private val THUNDER_SPARK_TEXTURE by lazy { SkullTextureHolder.getTexture("THUNDER_SPARK") }
    private val sparks = mutableListOf<EntityArmorStand>()

    @HandleEvent
    fun onTick() {
        if (!isEnabled()) return

        EntityUtils.getEntities<EntityArmorStand>().filter {
            it !in sparks && it.hasSkullTexture(THUNDER_SPARK_TEXTURE)
        }.forEach { sparks.add(it) }
    }

    @HandleEvent
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!isEnabled()) return

        val color = config.color.toSpecialColor()

        for (spark in sparks) {
            if (spark.isDead) continue
            val sparkLocation = spark.getLorenzVec()
            val block = sparkLocation.getBlockAt()
            val seeThroughBlocks = sparkLocation.distanceToPlayer() < 6 && (block in FishingApi.lavaBlocks)
            event.drawWaypointFilled(
                sparkLocation.add(-0.5, 0.0, -0.5), color, extraSize = -0.25, seeThroughBlocks = seeThroughBlocks,
            )
            if (sparkLocation.distanceToPlayer() < 10) {
                event.drawString(sparkLocation.up(1.5), "Thunder Spark", seeThroughBlocks = seeThroughBlocks)
            }
        }
    }

    @HandleEvent
    fun onWorldChange() {
        sparks.clear()
    }

    private fun isEnabled() =
        (IslandType.CRIMSON_ISLE.isInIsland() || LorenzUtils.isStrandedProfile) && config.highlight

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(3, "fishing.thunderSparkHighlight", "fishing.thunderSpark.highlight")
        event.move(3, "fishing.thunderSparkColor", "fishing.thunderSpark.color")
    }
}
