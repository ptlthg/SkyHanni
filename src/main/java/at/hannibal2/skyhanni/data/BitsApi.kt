package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.events.BitsAvailableUpdateEvent
import at.hannibal2.skyhanni.events.BitsUpdateEvent
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.ScoreboardUpdateEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NumberUtil.formatInt
import at.hannibal2.skyhanni.utils.RegexUtils.findMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.StringUtils.removeResets
import at.hannibal2.skyhanni.utils.StringUtils.trimWhiteSpace
import at.hannibal2.skyhanni.utils.TimeUtils
import at.hannibal2.skyhanni.utils.UtilsPatterns
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.nextAfter
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.item.ItemStack
import kotlin.time.Duration.Companion.days

@SkyHanniModule
object BitsApi {
    private val profileStorage get() = ProfileStorageData.profileSpecific?.bits
    private val playerStorage get() = ProfileStorageData.playerSpecific

    // TODO: remove once issue is tracked down
    private val lastBitUpdates = mutableMapOf<String, Int>()

    var bits: Int
        get() = profileStorage?.bits ?: 0
        private set(value) {
            profileStorage?.bits = value
        }
    var fameRank: FameRank?
        get() = playerStorage?.fameRank?.let(FameRanks::getByInternalName)
        set(value) {
            if (value != null) {
                playerStorage?.fameRank = value.internalName
            }
        }

    var bitsAvailable: Int
        get() = profileStorage?.bitsAvailable ?: 0
        private set(value) {
            profileStorage?.bitsAvailable = value
        }

    var cookieBuffTime: SimpleTimeMark?
        get() = profileStorage?.boosterCookieExpiryTime
        private set(value) {
            profileStorage?.boosterCookieExpiryTime = value
        }

    private const val DEFAULT_COOKIE_BITS = 4800

    private val bitsDataGroup = RepoPattern.group("data.bits")

    // Scoreboard patterns
    /**
     * REGEX-TEST: Bits: §b140,965
     */
    val bitsScoreboardPattern by bitsDataGroup.pattern(
        "scoreboard",
        "^Bits: §b(?<amount>[\\d,.]+).*$",
    )

    // Chat patterns
    private val bitsChatGroup = bitsDataGroup.group("chat")

    /**
     * REGEX-TEST: §eYou gained §317,664 Bits Available §ecompounded from all your §epreviously eaten §6cookies§e! Click here to open §6cookie menu§e!
     */
    @Suppress("MaxLineLength")
    private val bitsFromFameRankUpChatPattern by bitsChatGroup.pattern(
        "rankup.bits",
        "§eYou gained §3(?<amount>.*) Bits Available §ecompounded from all your §epreviously eaten §6cookies§e! Click here to open §6cookie menu§e!",
    )

    /**
     * REGEX-TEST: §6  §6§lFAME RANK UP §eStatesperson
     */
    private val fameRankUpPattern by bitsChatGroup.pattern(
        "rankup.rank",
        "[§\\w\\s]+FAME RANK UP (?:§.)+(?<rank>.*)",
    )

    /**
     * REGEX-TEST: §eYou consumed a §6Booster Cookie§e! §dYummy!
     * REGEX-TEST: §eYou consumed a §6Booster Cookie§e!
     * REGEX-TEST: §eYou consumed a §6Booster Cookie§e! §dDivine!
     */
    private val boosterCookieAte by bitsChatGroup.pattern(
        "boostercookieate",
        "§eYou consumed a §6Booster Cookie§e!.*",
    )

    // GUI patterns
    private val bitsGuiGroup = bitsDataGroup.group("gui")

    /**
     * REGEX-TEST: §7Bits Available: §b19,176
     */
    private val bitsAvailableMenuPattern by bitsGuiGroup.pattern(
        "availablemenu",
        "§7Bits Available: §b(?<toClaim>[\\d,]+)(?:§3.+)?",
    )

    /**
     * REGEX-TEST: §7Bits Purse: §b283,149
     */
    private val bitsPurseMenuPattern by bitsGuiGroup.pattern(
        "bitsmenu",
        "^§7Bits Purse: §b(?<amount>[\\d,.]+)",
    )

    /**
     * REGEX-TEST: §7Your rank: §eAttaché
     */
    private val fameRankSBMenuPattern by bitsGuiGroup.pattern(
        "sbmenufamerank",
        "§7Your rank: §e(?<rank>.*)",
    )

    /**
     * REGEX-TEST:  §7Duration: §a140d 8h 35m 36s
     */
    private val cookieDurationPattern by bitsGuiGroup.pattern(
        "cookieduration",
        "\\s*§7Duration: §a(?<time>.*)",
    )

    private val noCookieActiveSBMenuPattern by bitsGuiGroup.pattern(
        "sbmenunocookieactive",
        " §7Status: §cNot active!",
    )

    /**
     * REGEX-TEST: §7§cYou do not currently have a
     * REGEX-TEST: §cBooster Cookie active!
     */
    private val noCookieActiveCookieMenuPattern by bitsGuiGroup.pattern(
        "cookiemenucookieactive",
        "§7§cYou do not currently have a|§cBooster Cookie active!",
    )

    /**
     * REGEX-TEST: §7Fame Rank: §eAttaché
     */
    private val fameRankCommunityShopPattern by bitsGuiGroup.pattern(
        "communityshopfamerank",
        "§7Fame Rank: §e(?<rank>.*)",
    )

    private val cookieGuiStackPattern by bitsGuiGroup.pattern(
        "mainmenustack",
        "^§6Booster Cookie$",
    )

    private val bitsStackPattern by bitsGuiGroup.pattern(
        "bitsstack",
        "§bBits",
    )

    /**
     * REGEX-TEST: Community Shop
     * REGEX-TEST: Booster Cookie
     */
    private val fameRankGuiNamePattern by bitsGuiGroup.pattern(
        "famerankmenuname",
        "^Community Shop|Booster Cookie$",
    )

    /**
     * REGEX-TEST: §aCommunity Shop
     * REGEX-TEST: §eFame Rank
     */
    private val fameRankGuiStackPattern by bitsGuiGroup.pattern(
        "famerankmenustack",
        "^§aCommunity Shop|§eFame Rank$",
    )

    private val museumGuiNamePattern by bitsGuiGroup.pattern(
        "museumguiname",
        "Your Museum",
    )

    private val museumRewardStackPattern by bitsGuiGroup.pattern(
        "museumrewardstack",
        "§6Museum Rewards",
    )

    /**
     * REGEX-TEST: §7§7Milestone: §e11§6/§e30
     */
    private val museumMilestonePattern by bitsGuiGroup.pattern(
        "museummilestone",
        "(?:§.)*Milestone: §e(?<milestone>\\d+)§6/§e30",
    )

    @HandleEvent
    fun onScoreboardChange(event: ScoreboardUpdateEvent) {
        if (!isEnabled()) return
        for (line in event.added) {
            val message = line.trimWhiteSpace().removeResets()

            bitsScoreboardPattern.matchMatcher(message) {
                val amount = group("amount").formatInt()
                updateBits(amount, cause = "Scoreboard update, $message")
            }
        }
    }

    private fun updateBits(amount: Int, modifyAvailable: Boolean = true, cause: String) {
        ChatUtils.debug("Updating bits to $amount, cause: $cause")
        lastBitUpdates[cause] = bits
        val diff = amount - bits
        if (diff == 0) return

        if (diff > 0) {
            if (modifyAvailable) bitsAvailable -= diff
            bits = amount
            sendBitsGainEvent(diff)
        } else {
            bits = amount
            sendBitsSpentEvent(diff)
        }
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!isEnabled()) return
        val message = event.message.trimWhiteSpace().removeResets()

        bitsFromFameRankUpChatPattern.matchMatcher(message) {
            val amount = group("amount").formatInt()
            bitsAvailable += amount
            sendBitsAvailableGainedEvent()

            return
        }

        fameRankUpPattern.matchMatcher(message) {
            val rank = group("rank")

            fameRank = FameRanks.getByName(rank) ?: run {
                ErrorManager.logErrorWithData(
                    FameRankNotFoundException(rank),
                    "FameRank $rank not found",
                    "Rank" to rank,
                    "Message" to message,
                    "FameRanks" to FameRanks.fameRanksMap,
                )
                return
            }
            return
        }

        boosterCookieAte.matchMatcher(message) {
            bitsAvailable += bitsPerCookie()
            cookieBuffTime = (cookieBuffTime ?: SimpleTimeMark.now()) + 4.days
            sendBitsAvailableGainedEvent()

            return
        }
    }

    fun bitsPerCookie(): Int {
        val museumBonus = profileStorage?.museumMilestone?.let { 1 + it * 0.01 } ?: 1.0 // Adds 1% per level
        return (DEFAULT_COOKIE_BITS * museumBonus * (fameRank?.bitsMultiplier ?: 1.0)).toInt()
    }

    @HandleEvent
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        if (!isEnabled()) return

        val stacks = event.inventoryItems.values

        when {
            UtilsPatterns.skyblockMenuGuiPattern.matches(event.inventoryName) -> handleSkyBlockMenu(stacks)
            fameRankGuiNamePattern.matches(event.inventoryName) -> handleFameRankGui(stacks)
            museumGuiNamePattern.matches(event.inventoryName) -> handleMuseumGui(stacks)
        }
    }

    private fun handleSkyBlockMenu(stacks: Collection<ItemStack>) {
        val cookieStack = stacks.lastOrNull { cookieGuiStackPattern.matches(it.displayName) }

        // If the cookie stack is null, then the player should not have any bits to claim
        if (cookieStack == null) {
            bitsAvailable = 0
            cookieBuffTime = SimpleTimeMark.farPast()
            return
        }

        val lore = cookieStack.getLore()
        bitsAvailableMenuPattern.firstMatcher(lore) {
            val amount = group("toClaim").formatInt()
            if (bitsAvailable != amount) {
                bitsAvailable = amount
                sendBitsAvailableGainedEvent()
                /**
                 * We cant increase [BitsApi.bits] here since that difference is alr accounted for,
                 * if we do, it will be counted twice
                 */
            }
        }
        cookieDurationPattern.firstMatcher(lore) {
            val duration = TimeUtils.getDuration(group("time"))
            cookieBuffTime = SimpleTimeMark.now() + duration
        }
        noCookieActiveSBMenuPattern.firstMatcher(lore) {
            val cookieTime = cookieBuffTime
            if (cookieTime == null || cookieTime.isInFuture()) cookieBuffTime = SimpleTimeMark.farPast()
        }
    }

    private fun handleFameRankGui(stacks: Collection<ItemStack>) {
        processFameRankStacks(stacks)
        processBitsStacks(stacks)
        processCookieStacks(stacks)
    }

    private fun processFameRankStacks(stacks: Collection<ItemStack>) {
        val stack = stacks.firstOrNull { fameRankGuiStackPattern.matches(it.displayName) } ?: return
        fun fameRankOrNull(rank: String) {
            fameRank = FameRanks.getByName(rank) ?: run {
                ErrorManager.logErrorWithData(
                    FameRankNotFoundException(rank),
                    "FameRank $rank not found",
                    "Rank" to rank,
                    "Lore" to stack.getLore(),
                    "FameRanks" to FameRanks.fameRanksMap,
                )
                return
            }
        }
        for (line in stack.getLore()) {
            fameRankCommunityShopPattern.matchMatcher(line) {
                val rank = group("rank")
                fameRankOrNull(rank)
                return
            }

            fameRankSBMenuPattern.matchMatcher(line) {
                val rank = group("rank")
                fameRankOrNull(rank)
                return
            }
        }
    }

    private fun processBitsStacks(stacks: Collection<ItemStack>) {
        val stack = stacks.firstOrNull { bitsStackPattern.matches(it.displayName) } ?: return
        var foundAvailable = false
        var foundBits = false
        for (line in stack.getLore()) {
            if (!foundBits) bitsPurseMenuPattern.findMatcher(line) {
                foundBits = true
                val amount = group("amount").formatInt()
                updateBits(amount, false, "Bits Stack")
            }

            if (!foundAvailable) bitsAvailableMenuPattern.matchMatcher(line) {
                foundAvailable = true
                val amount = group("toClaim").formatInt()
                if (amount != bitsAvailable) {
                    bitsAvailable = amount
                    sendBitsAvailableGainedEvent()
                }
            }

            if (foundBits && foundAvailable) break
        }
    }

    private fun processCookieStacks(stacks: Collection<ItemStack>) {
        val stack = stacks.firstOrNull { cookieGuiStackPattern.matches(it.displayName) } ?: return
        for (line in stack.getLore()) {
            cookieDurationPattern.matchMatcher(line) {
                val duration = TimeUtils.getDuration(group("time"))
                cookieBuffTime = SimpleTimeMark.now().plus(duration)
                return
            }

            noCookieActiveCookieMenuPattern.matchMatcher(line) {
                val nextLine = stack.getLore().nextAfter(line) ?: continue
                if (noCookieActiveCookieMenuPattern.matches(nextLine)) cookieBuffTime = SimpleTimeMark.farPast()
                return
            }
        }
    }

    private fun handleMuseumGui(stacks: Collection<ItemStack>) {
        val stack = stacks.firstOrNull { museumRewardStackPattern.matches(it.displayName) } ?: return

        museumMilestonePattern.firstMatcher(stack.getLore()) {
            profileStorage?.museumMilestone = group("milestone").formatInt()
        }
    }

    fun hasCookieBuff() = cookieBuffTime?.isInFuture() == true

    private fun sendBitsGainEvent(difference: Int) = BitsUpdateEvent.BitsGain(bits, bitsAvailable, difference).post()

    private fun sendBitsSpentEvent(difference: Int) =
        BitsUpdateEvent.BitsSpent(bits, bitsAvailable, difference).post()
    private fun sendBitsAvailableGainedEvent() = BitsAvailableUpdateEvent(bitsAvailable).post()

    fun isEnabled() = LorenzUtils.inSkyBlock && !LorenzUtils.isOnAlphaServer && profileStorage != null

    @HandleEvent
    fun onDebug(event: DebugDataCollectEvent) {
        event.title("Bits API")
        event.addIrrelevant {
            add("Bits: $bits")
            add("Bits Available: $bitsAvailable")
            add("Cookie Buff Time: $cookieBuffTime")
            add("Current Fame Rank: $fameRank")
            add("Last Bit Updates: $lastBitUpdates")
            add("Bits per Cookie: ${bitsPerCookie()}")
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(35, "#profile.bits.bitsToClaim", "#profile.bits.bitsAvailable")
    }

    class FameRankNotFoundException(rank: String) : Exception("FameRank not found: $rank")
}
