package at.hannibal2.skyhanni.features.skillprogress

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.SkillApi
import at.hannibal2.skyhanni.api.SkillApi.activeSkill
import at.hannibal2.skyhanni.api.SkillApi.lastUpdate
import at.hannibal2.skyhanni.api.SkillApi.oldSkillInfoMap
import at.hannibal2.skyhanni.api.SkillApi.showDisplay
import at.hannibal2.skyhanni.api.SkillApi.skillXPInfoMap
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.features.skillprogress.SkillProgressConfig
import at.hannibal2.skyhanni.events.ActionBarUpdateEvent
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.ProfileJoinEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.SkillOverflowLevelUpEvent
import at.hannibal2.skyhanni.features.skillprogress.SkillUtil.XP_NEEDED_FOR_50
import at.hannibal2.skyhanni.features.skillprogress.SkillUtil.XP_NEEDED_FOR_60
import at.hannibal2.skyhanni.features.skillprogress.SkillUtil.calculateSkillLevel
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils.chat
import at.hannibal2.skyhanni.utils.ConditionalUtils.onToggle
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatDouble
import at.hannibal2.skyhanni.utils.NumberUtil.interpolate
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.RenderUtils
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderable
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.SoundUtils.playSound
import at.hannibal2.skyhanni.utils.SpecialColor.toSpecialColor
import at.hannibal2.skyhanni.utils.TimeUnit
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.renderables.Renderable
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object SkillProgress {

    val config get() = SkyHanniMod.feature.skillProgress
    private val barConfig get() = config.skillProgressBarConfig
    private val allSkillConfig get() = config.allSkillDisplayConfig
    private val customGoalConfig get() = config.customGoalConfig
    val etaConfig get() = config.skillETADisplayConfig

    private var skillExpPercentage = 0.0
    private var display = emptyList<Renderable>()
    private var allDisplay = emptyList<Renderable>()
    private var etaDisplay = emptyList<Renderable>()
    private var lastGainUpdate = SimpleTimeMark.farPast()
    private var maxWidth = 182
    var hideInActionBar = listOf<String>()

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!isDisplayEnabled()) return
        if (display.isEmpty()) return

        if (showDisplay) {
            renderDisplay()

            if (barConfig.enabled.get()) {
                renderBar()
            }
        }

        if (etaConfig.enabled.get()) {
            config.etaPosition.renderRenderables(etaDisplay, posLabel = "Skill ETA")
        }
    }

    @HandleEvent
    fun onBackgroundDraw(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (!isDisplayEnabled()) return
        if (display.isEmpty()) return

        if (etaConfig.enabled.get()) {
            config.etaPosition.renderRenderables(etaDisplay, posLabel = "Skill ETA")
        }
    }

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent) {
        if (!isDisplayEnabled()) return
        if (display.isEmpty()) return

        if (allSkillConfig.enabled.get()) {
            config.allSkillPosition.renderRenderables(allDisplay, posLabel = "All Skills Display")
        }
    }

    private fun renderDisplay() {
        when (val textAlignment = config.textAlignmentProperty.get()) {
            SkillProgressConfig.TextAlignment.NONE -> {
                val content = Renderable.horizontalContainer(display)
                config.displayPosition.renderRenderable(content, posLabel = "Skill Progress")
            }

            SkillProgressConfig.TextAlignment.CENTERED,
            SkillProgressConfig.TextAlignment.LEFT,
            SkillProgressConfig.TextAlignment.RIGHT,
            -> {
                val horizontalAlignment = textAlignment.alignment ?: RenderUtils.HorizontalAlignment.LEFT
                val content = Renderable.horizontalContainer(display, horizontalAlign = horizontalAlignment)
                val renderables = listOf(Renderable.fixedSizeLine(content, maxWidth))
                config.displayPosition.renderRenderables(renderables, posLabel = "Skill Progress")
            }

            else -> {}
        }
    }

    private fun renderBar() {
        val progress = if (barConfig.useTexturedBar.get()) {
            val factor = (skillExpPercentage.toFloat().coerceAtMost(1f)) * 182
            maxWidth = 182
            Renderable.progressBar(
                percent = factor.toDouble(),
                startColor = barConfig.barStartColor.toSpecialColor(),
                texture = barConfig.texturedBar.usedTexture.get(),
                useChroma = barConfig.useChroma.get(),
            )

        } else {
            maxWidth = barConfig.regularBar.width
            val factor = skillExpPercentage.coerceAtMost(1.0)
            Renderable.progressBar(
                percent = factor,
                startColor = barConfig.barStartColor.toSpecialColor(),
                endColor = barConfig.barStartColor.toSpecialColor(),
                width = maxWidth,
                height = barConfig.regularBar.height,
                useChroma = barConfig.useChroma.get(),
            )
        }

        config.barPosition.renderRenderables(listOf(progress), posLabel = "Skill Progress Bar")
    }

    @HandleEvent
    fun onProfileJoin(event: ProfileJoinEvent) {
        display = emptyList()
        allDisplay = emptyList()
        etaDisplay = emptyList()
        skillExpPercentage = 0.0
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!isDisplayEnabled()) return
        if (lastUpdate.passedSince() > 3.seconds) showDisplay = config.alwaysShow.get()

        allDisplay = formatAllDisplay(drawAllDisplay())
        etaDisplay = drawETADisplay()

        if (event.repeatSeconds(1)) {
            update()
            updateSkillInfo()
        }
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onLevelUp(event: SkillOverflowLevelUpEvent) {
        if (!config.overflowConfig.enableInChat) return
        val skillName = event.skill.displayName
        val oldLevel = event.oldLevel
        val newLevel = event.newLevel
        val skill = SkillApi.storage?.get(event.skill) ?: return
        val goalReached = newLevel == skill.customGoalLevel && customGoalConfig.enableInChat

        val rewards = buildList {
            add("  §r§7§8+§b1 Flexing Point")
            if (newLevel % 5 == 0)
                add("  §r§7§8+§d50 SkyHanni User Luck")
        }
        val messages = listOf(
            "§3§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "  §r§b§lSKILL LEVEL UP §3$skillName §8$oldLevel➜§3$newLevel",
            if (goalReached)
                listOf(
                    "",
                    "  §r§d§lGOAL REACHED!",
                    "",
                ).joinToString("\n") else
                "",
            "  §r§a§lREWARDS",
            rewards.joinToString("\n"),
            "§3§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
        )

        chat(messages.joinToString("\n"), false)

        if (goalReached)
            chat("§lYou have reached your goal level of §b§l${skill.customGoalLevel} §e§lin the §b§l$skillName §e§lskill!")

        SoundUtils.createSound("random.levelup", 1f, 1f).playSound()
    }

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        onToggle(
            config.enabled,
            config.alwaysShow,
            config.showActionLeft,
            config.useIcon,
            config.usePercentage,
            config.useSkillName,
            config.overflowConfig.enableInDisplay,
            config.overflowConfig.enableInProgressBar,
            config.overflowConfig.enableInEtaDisplay,
            barConfig.enabled,
            barConfig.useChroma,
            barConfig.useTexturedBar,
            allSkillConfig.enabled,
            etaConfig.enabled,
        ) {
            updateDisplay()
            update()
        }
    }

    @HandleEvent(priority = HandleEvent.LOW)
    fun onActionBar(event: ActionBarUpdateEvent) {
        if (!config.hideInActionBar || !isDisplayEnabled()) return
        var msg = event.actionBar
        for (line in hideInActionBar) {
            msg = msg.replace(Regex("\\s*" + Regex.escape(line)), "")
        }
        msg = msg.trim()

        event.changeActionBar(msg)
    }

    fun updateDisplay() {
        display = drawDisplay()
    }

    private fun update() {
        lastGainUpdate = SimpleTimeMark.now()
        skillXPInfoMap.forEach {
            it.value.xpGainLast = it.value.xpGainHour
        }
    }

    private fun formatAllDisplay(map: Map<SkillType, Renderable>): List<Renderable> {
        val newList = mutableListOf<Renderable>()
        if (map.isEmpty()) return newList
        for (skillType in allSkillConfig.skillEntryList) {
            map[skillType]?.let {
                newList.add(it)
            }
        }
        return newList
    }

    private fun drawAllDisplay() = buildMap {
        val skillMap = SkillApi.storage ?: return@buildMap
        val sortedMap = SkillType.entries.filter { it.displayName.isNotEmpty() }.sortedBy { it.displayName.take(2) }

        for (skill in sortedMap) {
            val skillInfo = skillMap[skill] ?: SkillApi.SkillInfo(level = -1, overflowLevel = -1)
            val lockedLevels = skillInfo.overflowCurrentXp > skillInfo.overflowCurrentXpMax
            val useCustomGoalLevel =
                skillInfo.customGoalLevel != 0 && skillInfo.customGoalLevel > skillInfo.overflowLevel && customGoalConfig.enableInAllDisplay
            val targetLevel = skillInfo.customGoalLevel
            var xp = skillInfo.overflowTotalXp
            if (targetLevel in 50..60 && skillInfo.overflowLevel >= 50) xp += SkillUtil.xpRequiredForLevel(50)
            else if (targetLevel > 60 && skillInfo.overflowLevel >= 60) xp += SkillUtil.xpRequiredForLevel(60)

            var have = skillInfo.overflowTotalXp
            val need = SkillUtil.xpRequiredForLevel(targetLevel)
            if (targetLevel in 51..59) have += SkillUtil.xpRequiredForLevel(50)
            else if (targetLevel > 60) have += SkillUtil.xpRequiredForLevel(60)

            val (level, currentXP, currentXPMax, totalXP) =
                if (useCustomGoalLevel)
                    SkillLevel(skillInfo.overflowLevel, have, need, xp)
                else if (config.overflowConfig.enableInAllDisplay.get() && !lockedLevels)
                    SkillLevel(
                        skillInfo.overflowLevel,
                        skillInfo.overflowCurrentXp,
                        skillInfo.overflowCurrentXpMax,
                        skillInfo.overflowTotalXp,
                    )
                else
                    SkillLevel(skillInfo.level, skillInfo.currentXp, skillInfo.currentXpMax, skillInfo.totalXp)

            this[skill] = if (level == -1) {
                Renderable.clickable(
                    "§cOpen your skills menu!",
                    tips = listOf("§eClick here to execute §6/skills"),
                    onLeftClick = { HypixelCommands.skills() },
                )
            } else {
                val tips = buildList {
                    add("§6Level: §b$level")
                    add("§6Current XP: §b${currentXP.addSeparators()}")
                    add("§6Needed XP: §b${currentXPMax.addSeparators()}")
                    add("§6Total XP: §b${totalXP.addSeparators()}")
                }
                val nameColor = if (skill == activeSkill) "§2" else "§a"
                Renderable.hoverTips(
                    buildString {
                        append("$nameColor${skill.displayName} $level ")
                        append("§7(")
                        append("§b${currentXP.addSeparators()}")
                        if (currentXPMax != 0L) {
                            append("§6/")
                            append("§b${currentXPMax.addSeparators()}")
                        }
                        append("§7)")
                    },
                    tips,
                )
            }
        }
    }

    private fun drawETADisplay() = buildList {
        val activeSkill = activeSkill ?: return@buildList
        val skillInfo = SkillApi.storage?.get(activeSkill) ?: return@buildList
        val xpInfo = skillXPInfoMap[activeSkill] ?: return@buildList
        val skillInfoLast = oldSkillInfoMap[activeSkill] ?: return@buildList
        oldSkillInfoMap[activeSkill] = skillInfo
        val level = if (config.overflowConfig.enableInEtaDisplay.get() || config.customGoalConfig.enableInETADisplay) {
            skillInfo.overflowLevel
        } else {
            skillInfo.level
        }

        val useCustomGoalLevel =
            skillInfo.customGoalLevel != 0 && skillInfo.customGoalLevel > skillInfo.overflowLevel && customGoalConfig.enableInETADisplay
        var targetLevel = if (useCustomGoalLevel) skillInfo.customGoalLevel else level + 1
        if (targetLevel <= level || targetLevel > 400) targetLevel = (level + 1)

        val need = skillInfo.overflowCurrentXpMax
        val have = skillInfo.overflowCurrentXp

        val currentLevelNeededXP = SkillUtil.xpRequiredForLevel(level) + have
        val targetNeededXP = SkillUtil.xpRequiredForLevel(targetLevel)

        var remaining = if (useCustomGoalLevel) targetNeededXP - currentLevelNeededXP else need - have

        if (!useCustomGoalLevel && have < need) {
            if (skillInfo.overflowCurrentXpMax == skillInfoLast.overflowCurrentXpMax) {
                remaining =
                    interpolate(remaining.toFloat(), (need - have).toFloat(), lastGainUpdate.toMillis()).toLong()
            }
        }

        add(Renderable.string("§6Skill: §a${activeSkill.displayName} §8$level➜§3$targetLevel"))

        if (useCustomGoalLevel)
            add(Renderable.string("§7Needed XP: §e${remaining.addSeparators()}"))

        var xpInterp = xpInfo.xpGainHour

        if (have > need) {
            add(Renderable.string("§7In §cIncrease level cap!"))
        } else if (xpInfo.xpGainHour < 1000) {
            add(Renderable.string("§7In §cN/A"))
        } else {
            val duration = ((remaining) * 1000 * 60 * 60 / xpInterp.toLong()).milliseconds
            val format = duration.format(TimeUnit.DAY)
            add(
                Renderable.string(
                    "§7In §b$format " +
                        if (xpInfo.isActive) "" else "§c(PAUSED)",
                ),
            )
        }

        if (xpInfo.xpGainLast == xpInfo.xpGainHour && xpInfo.xpGainHour <= 0) {
            add(Renderable.string("§7XP/h: §cN/A"))
        } else {
            xpInterp = interpolate(xpInfo.xpGainHour, xpInfo.xpGainLast, lastGainUpdate.toMillis())
            add(
                Renderable.string(
                    "§7XP/h: §e${xpInterp.toLong().addSeparators()} " +
                        if (xpInfo.isActive) "" else "§c(PAUSED)",
                ),
            )
        }

        val session = xpInfo.timeActive.seconds.format(TimeUnit.HOUR)
        add(
            Renderable.clickable(
                "§7Session: §e$session ${if (xpInfo.sessionTimerActive) "" else "§c(PAUSED)"}",
                tips = listOf("§eClick to reset!"),
                onLeftClick = {
                    xpInfo.sessionTimerActive = false
                    xpInfo.timeActive = 0L
                    chat("Timer for §b${activeSkill.displayName} §ehas been reset!")
                    updateDisplay()
                    update()
                },
            ),
        )
    }

    private fun drawDisplay() = buildList {
        val activeSkill = activeSkill ?: return@buildList
        val skillMap = SkillApi.storage ?: return@buildList
        val skill = skillMap[activeSkill] ?: return@buildList
        val useCustomGoalLevel = skill.customGoalLevel != 0 && skill.customGoalLevel > skill.overflowLevel
        val targetLevel = skill.customGoalLevel
        val xp = skill.totalXp
        val lvl = skill.level
        val cap = activeSkill.maxLevel
        val add = if (lvl >= 50) {
            when (cap) {
                50 -> XP_NEEDED_FOR_50
                60 -> XP_NEEDED_FOR_60
                else -> 0
            }
        } else {
            0
        }
        val (currentLevel, _, _, xpTotalCurrent) = calculateSkillLevel(xp + add, cap)
        val need = SkillUtil.xpRequiredForLevel(targetLevel)

        val (level, currentXP, currentXPMax, _) =
            if (useCustomGoalLevel && customGoalConfig.enableInDisplay)
                SkillLevel(currentLevel, xp + add, need, xpTotalCurrent)
            else if (config.overflowConfig.enableInDisplay.get())
                SkillLevel(skill.overflowLevel, skill.overflowCurrentXp, skill.overflowCurrentXpMax, skill.overflowTotalXp)
            else
                SkillLevel(skill.level, skill.currentXp, skill.currentXpMax, skill.totalXp)

        if (config.showLevel.get())
            add(Renderable.string("§9[§d$level§9] "))

        if (config.useIcon.get()) {
            add(Renderable.itemStack(activeSkill.item, 1.0))
        }

        add(
            Renderable.string(
                buildString {
                    append("§b+${skill.lastGain} ")

                    if (config.useSkillName.get())
                        append("${activeSkill.displayName} ")

                    val (barCurrent, barMax) =
                        if (useCustomGoalLevel && customGoalConfig.enableInProgressBar)
                            Pair(currentXP, currentXPMax)
                        else if (config.overflowConfig.enableInProgressBar.get())
                            Pair(skill.overflowCurrentXp, skill.overflowCurrentXpMax)
                        else
                            Pair(skill.currentXp, skill.currentXpMax)

                    val barPercent = if (barMax == 0L) 100F else 100F * barCurrent / barMax
                    skillExpPercentage = (barPercent.toDouble() / 100)

                    val percent = if (currentXPMax == 0L) 100F else 100F * currentXP / currentXPMax

                    if (config.usePercentage.get())
                        append("§7(§6${percent.roundTo(2)}%§7)")
                    else {
                        if (currentXPMax == 0L)
                            append("§7(§6${currentXP.addSeparators()}§7)")
                        else
                            append("§7(§6${currentXP.addSeparators()}§7/§6${currentXPMax.addSeparators()}§7)")
                    }

                    if (config.showActionLeft.get() && percent != 100f) {
                        append(" - " + addActionsLeft(skill, currentXPMax, currentXP))
                    }
                },
            ),
        )
    }

    private fun addActionsLeft(
        skill: SkillApi.SkillInfo,
        currentXPMax: Long,
        currentXP: Long,
    ): String {
        if (skill.lastGain != "") {
            val gain = skill.lastGain.formatDouble()
            val actionLeft = (ceil(currentXPMax.toDouble() - currentXP) / gain).toLong().plus(1).addSeparators()
            if (skill.lastGain != "" && !actionLeft.contains("-")) {
                return "§6$actionLeft Left"
            }
        }
        return "§6∞ Left"
    }

    private fun updateSkillInfo() {
        val activeSkill = activeSkill ?: return
        val xpInfo = skillXPInfoMap.getOrPut(activeSkill) { SkillApi.SkillXPInfo() }
        val skillInfo = SkillApi.storage?.get(activeSkill) ?: return
        oldSkillInfoMap[activeSkill] = skillInfo

        val totalXP = skillInfo.currentXp

        if (xpInfo.lastTotalXP > 0) {
            val delta = totalXP - xpInfo.lastTotalXP
            if (delta > 0 && delta < 1000) {

                xpInfo.timer = when (SkillApi.activeSkill) {
                    SkillType.FARMING -> etaConfig.farmingPauseTime
                    SkillType.MINING -> etaConfig.miningPauseTime
                    SkillType.COMBAT -> etaConfig.combatPauseTime
                    SkillType.FORAGING -> etaConfig.foragingPauseTime
                    SkillType.FISHING -> etaConfig.fishingPauseTime
                    else -> 3
                }

                xpInfo.xpGainQueue.add(0, delta)

                calculateXPHour(xpInfo)
            } else if (xpInfo.timer > 0) {
                xpInfo.timer--
                xpInfo.xpGainQueue.add(0, 0f)

                calculateXPHour(xpInfo)
            } else if (delta <= 0) {
                xpInfo.isActive = false
            }
        }
        xpInfo.lastTotalXP = totalXP.toFloat()
    }

    private fun calculateXPHour(xpInfo: SkillApi.SkillXPInfo) {
        while (xpInfo.xpGainQueue.size > 30) {
            xpInfo.xpGainQueue.removeLast()
        }

        val totalGain = xpInfo.xpGainQueue.sum()

        xpInfo.xpGainHour = totalGain * (60 * 60) / xpInfo.xpGainQueue.size
        xpInfo.isActive = true
    }

    private fun isDisplayEnabled() = LorenzUtils.inSkyBlock && config.enabled.get()
}
