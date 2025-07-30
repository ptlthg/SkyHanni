package at.hannibal2.skyhanni.features.garden.visitor

import at.hannibal2.skyhanni.api.ItemBuyApi.buy
import at.hannibal2.skyhanni.api.ItemBuyApi.createBuyTip
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.features.garden.visitor.VisitorConfig.HighlightMode
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.SackApi.getAmountInSacks
import at.hannibal2.skyhanni.data.SackApi.getAmountInSacksOrNull
import at.hannibal2.skyhanni.data.title.TitleManager
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.OwnInventoryItemUpdateEvent
import at.hannibal2.skyhanni.events.ProfileJoinEvent
import at.hannibal2.skyhanni.events.SackDataUpdateEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.garden.visitor.VisitorAcceptEvent
import at.hannibal2.skyhanni.events.garden.visitor.VisitorAcceptedEvent
import at.hannibal2.skyhanni.events.garden.visitor.VisitorArrivalEvent
import at.hannibal2.skyhanni.events.garden.visitor.VisitorOpenEvent
import at.hannibal2.skyhanni.events.garden.visitor.VisitorRefusedEvent
import at.hannibal2.skyhanni.events.garden.visitor.VisitorRenderEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniTickEvent
import at.hannibal2.skyhanni.events.render.gui.ScreenDrawnEvent
import at.hannibal2.skyhanni.features.garden.CropType.Companion.getByNameOrNull
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.features.garden.farming.GardenCropSpeed.getSpeed
import at.hannibal2.skyhanni.features.garden.visitor.VisitorApi.blockReason
import at.hannibal2.skyhanni.features.inventory.bazaar.BazaarApi
import at.hannibal2.skyhanni.mixins.hooks.RenderLivingEntityHelper
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.InventoryUtils.getAmountInInventory
import at.hannibal2.skyhanni.utils.ItemBlink
import at.hannibal2.skyhanni.utils.ItemPriceUtils.getPrice
import at.hannibal2.skyhanni.utils.ItemUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.LorenzLogger
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.NeuItems.getItemStack
import at.hannibal2.skyhanni.utils.NeuItems.getItemStackOrNull
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatInt
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.PrimitiveIngredient.Companion.toPrimitiveItemStacks
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SignUtils
import at.hannibal2.skyhanni.utils.SignUtils.isBazaarSign
import at.hannibal2.skyhanni.utils.SignUtils.isSupercraftAmountSetSign
import at.hannibal2.skyhanni.utils.SkyBlockUtils
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.addOrPut
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addItemStack
import at.hannibal2.skyhanni.utils.collection.RenderableCollectionUtils.addString
import at.hannibal2.skyhanni.utils.getLorenzVec
import at.hannibal2.skyhanni.utils.render.WorldRenderUtils.drawString
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.container.HorizontalContainerRenderable.Companion.horizontal
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.client.gui.inventory.GuiEditSign
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.item.ItemStack
import kotlin.math.round
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object GardenVisitorFeatures {

    private val config get() = VisitorApi.config
    private var display = emptyList<Renderable>()

    private val patternGroup = RepoPattern.group("garden.visitor")

    /**
     * REGEX-TEST: §a§r§aBanker Broadjaw §r§ehas arrived on your §r§aGarden§r§e!
     */
    private val visitorArrivePattern by patternGroup.pattern(
        "visitorarrive",
        ".* §r§ehas arrived on your §r§[ba]Garden§r§e!",
    )

    /**
     * REGEX-TEST:  §8+§c20 Copper
     */
    private val copperPattern by patternGroup.pattern(
        "copper",
        " §8\\+§c(?<amount>.*) Copper",
    )

    /**
     * REGEX-TEST:  §8+§215 §7Garden Experience
     */
    private val gardenExperiencePattern by patternGroup.pattern(
        "gardenexperience",
        " §8\\+§2(?<amount>.*) §7Garden Experience",
    )

    /**
     * REGEX-TEST: §e[NPC] §6Madame Eleanor Q. Goldsworth III§f: §r§fI'm here to put a value on your farm. Bring me your fanciest crop.
     * REGEX-TEST: §e[NPC] §aRhys§f: §r§fI found an unexplored cave while mining for titanium. But it's too dark to see in there, even for me! Can you spare any glowing pumpkins?
     */
    private val visitorChatMessagePattern by patternGroup.pattern(
        "visitorchat",
        "§e\\[NPC] (?<color>§.)?(?<name>.*)§f: §r.*",
    )
    private val partialAcceptedPattern by patternGroup.pattern(
        "partialaccepted",
        "§aYou gave some of the required items!",
    )

    private val logger = LorenzLogger("garden/visitors")
    private var lastFullPrice = 0.0
    private val greenThumb = "GREEN_THUMB;1".toInternalName()

    @HandleEvent
    fun onProfileJoin(event: ProfileJoinEvent) {
        display = emptyList()
    }

    @HandleEvent
    fun onVisitorOpen(event: VisitorOpenEvent) {
        val visitor = event.visitor
        val offerItem = visitor.offer?.offerItem ?: return

        val lore = offerItem.getLore()

        // TODO make this workaround unnecessary (only read non lore info)
        readToolTip(visitor, offerItem, lore.toMutableList())
        visitor.lastLore = emptyList()

        for (line in lore) {
            if (line == "§7Items Required:") continue
            if (line.isEmpty()) break

            val (itemName, amount) = ItemUtils.readItemAmount(line) ?: run {
                ErrorManager.logErrorStateWithData(
                    "Could not read Shopping List in Visitor Inventory", "ItemUtils.readItemAmount returns null",
                    "line" to line,
                    "offerItem" to offerItem,
                    "lore" to lore,
                    "visitor" to visitor,
                )
                continue
            }
            val internalName = NeuInternalName.fromItemName(itemName)
            visitor.shoppingList[internalName] = amount
        }

        visitor.lastLore = listOf()
        visitor.blockedLore = listOf()
        visitor.blockReason = visitor.blockReason()

        val alreadyReady = offerItem.getLore().any { it == "§eClick to give!" }
        if (alreadyReady) {
            VisitorApi.changeStatus(visitor, VisitorApi.VisitorStatus.READY, "tooltipClickToGive")
        } else {
            VisitorApi.changeStatus(visitor, VisitorApi.VisitorStatus.WAITING, "tooltipMissingItems")
        }
        update()
    }

    private fun updateDisplay() {
        display = drawDisplay()
    }

    private fun drawDisplay() = buildList {
        if (!config.shoppingList.enabled) return@buildList
        val (shoppingList, newVisitors) = prepareDrawingData()

        drawShoppingList(shoppingList)
        drawVisitors(newVisitors, shoppingList)
    }

    private fun prepareDrawingData(): Pair<MutableMap<NeuInternalName, Int>, MutableList<String>> {
        val globalShoppingList = mutableMapOf<NeuInternalName, Int>()
        val newVisitors = mutableListOf<String>()
        for ((visitorName, visitor) in VisitorApi.getVisitorsMap()) {
            if (visitor.status == VisitorApi.VisitorStatus.ACCEPTED || visitor.status == VisitorApi.VisitorStatus.REFUSED) continue

            if (visitor.visitorName.removeColor() == "Spaceman" && config.shoppingList.ignoreSpaceman) continue

            val shoppingList = visitor.shoppingList
            if (shoppingList.isEmpty()) {
                newVisitors.add(visitorName)
            }
            for ((internalName, amount) in shoppingList) {
                val old = globalShoppingList.getOrDefault(internalName, 0)
                globalShoppingList[internalName] = old + amount
            }
        }
        return globalShoppingList to newVisitors
    }

    private fun MutableList<Renderable>.drawShoppingList(shoppingList: MutableMap<NeuInternalName, Int>) {
        if (shoppingList.isEmpty()) return

        var totalPrice = 0.0
        addString("§7Visitor Shopping List:")
        for ((internalName, amount) in shoppingList) {
            val name = internalName.repoItemName
            val itemStack = internalName.getItemStack()

            val list = mutableListOf<Renderable>()
            list.addString(" §7- ")
            list.addItemStack(itemStack)

            list.add(
                Renderable.clickable(
                    "$name §ex${amount.addSeparators()}",
                    tips = internalName.createBuyTip(),
                    onLeftClick = {
                        if (!GardenApi.inGarden() || NeuItems.neuHasFocus()) return@clickable
                        if (Minecraft.getMinecraft().currentScreen is GuiEditSign) {
                            SignUtils.setTextIntoSign("$amount")
                        } else {
                            internalName.buy(amount)
                        }
                    },
                ),
            )

            if (config.shoppingList.showPrice) {
                val price = internalName.getPrice() * amount
                totalPrice += price
                val format = price.shortFormat()
                list.addString(" §7(§6$format§7)")
            }

            addSackData(internalName, amount, list)

            add(Renderable.horizontal(list))
        }
        if (totalPrice > 0) {
            val format = totalPrice.shortFormat()
            this[0] = Renderable.text("§7Visitor Shopping List: §7(§6$format§7)")
        }
    }

    private fun addSackData(
        internalName: NeuInternalName,
        amount: Int,
        list: MutableList<Renderable>,
    ) {
        if (!config.shoppingList.showSackCount) return

        var amountInSacks = 0
        internalName.getAmountInSacksOrNull()?.let {
            amountInSacks = it
            val textColor = if (it >= amount) "a" else "e"
            list.addString(" §7(§${textColor}x${it.addSeparators()} §7in sacks)")
        }

        val ingredients = NeuItems.getRecipes(internalName)
            // TODO describe what this line does
            .firstOrNull { !it.ingredients.first().internalName.contains("PEST") }
            ?.ingredients.orEmpty()
        if (ingredients.isEmpty()) return

        val requiredIngredients = mutableMapOf<NeuInternalName, Int>()
        for ((key, count) in ingredients.toPrimitiveItemStacks()) {
            requiredIngredients.addOrPut(key, count)
        }
        var hasIngredients = true
        for ((key, value) in requiredIngredients) {
            val sackItem = key.getAmountInSacks()
            if (sackItem < value * (amount - amountInSacks)) {
                hasIngredients = false
                break
            }
        }
        if (hasIngredients && (amount - amountInSacks) > 0) {
            val leftToCraft = amount - amountInSacks
            list.addString(" §7(")
            list.add(
                Renderable.optionalLink(
                    "§aCraftable!",
                    {
                        if (Minecraft.getMinecraft().currentScreen is GuiEditSign) {
                            SignUtils.setTextIntoSign("$leftToCraft")
                        } else {
                            HypixelCommands.viewRecipe(internalName)
                        }
                    },
                ) { GardenApi.inGarden() && !NeuItems.neuHasFocus() },
            )
            list.addString("§7)")
        }
    }

    private fun MutableList<Renderable>.drawVisitors(
        newVisitors: List<String>,
        shoppingList: Map<NeuInternalName, Int>,
    ) {
        if (newVisitors.isEmpty()) return
        if (shoppingList.isNotEmpty()) {
            addString("")
        }
        val amount = newVisitors.size
        val visitorLabel = if (amount == 1) "visitor" else "visitors"
        addString("§e$amount §7new $visitorLabel:")
        for (visitor in newVisitors) {
            drawVisitor(visitor)
        }
    }

    private fun MutableList<Renderable>.drawVisitor(visitor: String) {
        val displayName = GardenVisitorColorNames.getColoredName(visitor)

        val list = mutableListOf<Renderable>()
        list.addString(" §7- $displayName")

        if (config.shoppingList.itemPreview) {
            val items = GardenVisitorColorNames.visitorItems[visitor.removeColor()]
            if (items == null) {
                val text = "Visitor '$visitor' has no items in repo!"
                logger.log(text)
                ChatUtils.debug(text)
                list.addString(" §7(§c?§7)")
                return
            }
            if (items.isEmpty()) {
                list.addString(" §7(§fAny§7)")
            } else {
                for (item in items) {
                    list.addItemStack(NeuInternalName.fromItemName(item).getItemStack())
                }
            }
        }

        add(Renderable.horizontal(list))
    }

    @HandleEvent
    fun onOwnInventoryItemUpdate(event: OwnInventoryItemUpdateEvent) {
        if (GardenApi.onBarnPlot) {
            update()
        }
    }

    @HandleEvent
    fun onSackUpdate(event: SackDataUpdateEvent) {
        update()
    }

    @HandleEvent
    fun onVisitorRefused(event: VisitorRefusedEvent) {
        update()
        GardenApi.storage?.visitorDrops?.let { it.deniedVisitors += 1 }
        GardenVisitorDropStatistics.saveAndUpdate()
    }

    @HandleEvent
    fun onVisitorAccepted(event: VisitorAcceptedEvent) {
        VisitorAcceptEvent(event.visitor).post()
        update()
        GardenApi.storage?.visitorDrops?.let { it.coinsSpent += round(lastFullPrice).toLong() }
    }

    @HandleEvent
    fun onVisitorRender(event: VisitorRenderEvent) {
        val visitor = event.visitor
        val text = visitor.status.displayName
        val location = event.location
        event.parent.drawString(location.up(2.23), text)
        if (config.rewardWarning.showOverName) {
            val initialOffset = 2.73
            val heightOffset = 0.25
            var counter = 0
            visitor.getRewardWarningAwards().forEach { reward ->
                val name = reward.displayName
                val offset = initialOffset + (counter * heightOffset)
                event.parent.drawString(location.up(offset), "§c§l! $name §c§l!")
                counter++
            }
        }
    }

    fun onTooltip(visitor: VisitorApi.Visitor, itemStack: ItemStack, toolTip: MutableList<String>) {
        if (itemStack.displayName != "§aAccept Offer") return

        if (visitor.lastLore.isEmpty()) {
            readToolTip(visitor, itemStack, toolTip)
        }
        toolTip.clear()
        toolTip.addAll(visitor.lastLore)
    }

    private fun readToolTip(visitor: VisitorApi.Visitor, itemStack: ItemStack?, toolTip: MutableList<String>) {
        val stack = itemStack ?: error("Accept offer item not found for visitor ${visitor.visitorName}")
        var totalPrice = 0.0
        var farmingTimeRequired = 0.seconds
        var readingShoppingList = true
        lastFullPrice = 0.0
        val foundRewards = mutableListOf<NeuInternalName>()

        // Todo: Extract duplicated code
        for (formattedLine in stack.getLore()) {
            if (formattedLine.contains("Rewards")) {
                readingShoppingList = false
            }

            val (itemName, amount) = ItemUtils.readItemAmount(formattedLine) ?: continue
            val internalName = NeuInternalName.fromItemNameOrNull(itemName)?.replace("◆_", "") ?: continue

            // Ignoring custom NEU items like copper
            if (internalName.startsWith("SKYBLOCK_")) continue
            val price = internalName.getPrice() * amount

            if (readingShoppingList) {
                totalPrice += price
                lastFullPrice += price
            } else {
                foundRewards.add(internalName)
                totalPrice -= price
            }
        }
        if (totalPrice < 0) {
            totalPrice = 0.0
        }

        if (foundRewards.isNotEmpty()) {
            val wasEmpty = visitor.allRewards.isEmpty()
            visitor.allRewards = foundRewards
            if (wasEmpty && config.rewardWarning.notifyInChat) {
                visitor.getRewardWarningAwards().forEach { reward ->
                    ChatUtils.chat("Found Visitor Reward ${reward.displayName}§e!")
                }
            }
        }

        readingShoppingList = true
        val finalList = toolTip.map { it.removePrefix("§5§o") }.toMutableList()
        var offset = 0
        for ((i, formattedLine) in finalList.toMutableList().withIndex()) {
            val index = i + offset
            if (config.inventory.experiencePrice) {
                gardenExperiencePattern.matchMatcher(formattedLine) {
                    val gardenExp = group("amount").formatInt()
                    val pricePerCopper = (totalPrice / gardenExp).toInt().shortFormat()
                    finalList.set(index, "$formattedLine §7(paying §6$pricePerCopper §7per)")
                }
            }

            copperPattern.matchMatcher(formattedLine) {
                val copper = group("amount").formatInt()
                val pricePerCopper = (totalPrice / copper).toInt().shortFormat()
                visitor.pricePerCopper = (totalPrice / copper).toInt()
                visitor.totalPrice = totalPrice
                // Estimate could be changed to most value per copper item, instead of green thumb
                val estimatedCopperValue = greenThumb.getPrice() / 1500
                visitor.totalReward = copper * estimatedCopperValue
                val timePerCopper = (farmingTimeRequired / copper).format()
                var copperLine = formattedLine
                if (config.inventory.copperPrice) copperLine += " §7(paying §6$pricePerCopper §7per)"
                if (config.inventory.copperTime) {
                    copperLine += if (farmingTimeRequired != 0.seconds) " §7(§b$timePerCopper §7per)" else " §7(§cno speed data!§7)"
                }
                finalList.set(index, copperLine)
            }

            if (formattedLine.contains("Rewards")) {
                readingShoppingList = false
            }
            val (itemName, amount) = ItemUtils.readItemAmount(formattedLine) ?: continue
            val internalName = NeuInternalName.fromItemNameOrNull(itemName)?.replace("◆_", "") ?: continue

            // Ignoring custom NEU items like copper
            if (internalName.startsWith("SKYBLOCK_")) continue
            val price = internalName.getPrice() * amount

            if (config.inventory.showPrice) {
                val format = price.shortFormat()
                finalList[index] = "$formattedLine §7(§6$format§7)"
            }
            if (!readingShoppingList) continue
            val primitiveStack = NeuItems.getPrimitiveMultiplier(internalName)

            val rawName = primitiveStack.internalName.itemNameWithoutColor
            val cropType = getByNameOrNull(rawName) ?: continue

            val cropAmount = primitiveStack.amount.toLong() * amount
            val formattedName = "§e${cropAmount.addSeparators()}§7x ${cropType.cropName} "
            val formattedSpeed = cropType.getSpeed()?.let { speed ->
                val duration = (cropAmount / speed).seconds
                farmingTimeRequired += duration
                "in §b$duration"
            } ?: "§cno speed data!"
            if (config.inventory.exactAmountAndTime) {
                finalList.add(index + 1, "§7- $formattedName($formattedSpeed§7)")
                offset++
            }
        }
        visitor.lastLore = finalList
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onTick(event: SkyHanniTickEvent) {
        if (!config.shoppingList.enabled && config.highlightStatus == HighlightMode.DISABLED) return
        if (!event.isMod(10, 2)) return

        if (GardenApi.onBarnPlot && config.highlightStatus != HighlightMode.DISABLED) {
            checkVisitorsReady()
        }
    }

    val LEGENDARY_JERRY = "JERRY;4".toInternalName()
    val SPACE_HELM = "DCTR_SPACE_HELM".toInternalName()

    @HandleEvent
    fun onVisitorArrival(event: VisitorArrivalEvent) {
        val visitor = event.visitor
        val name = visitor.visitorName

        update()

        logger.log("New visitor detected: '$name'")
        // do not show titles and chat messages for visitors that spawned while the player was offline
        if (SkyBlockUtils.lastWorldSwitch.passedSince() < 3.seconds) return

        if (config.notificationTitle) {
            TitleManager.sendTitle("§eNew Visitor")
        }
        if (config.notificationChat) {
            val displayName = GardenVisitorColorNames.getColoredName(name)
            ChatUtils.chat("$displayName §eis visiting your garden!")
        }

        if (name.removeColor().contains("Jerry")) {
            logger.log("Jerry!")
            ItemBlink.setBlink(LEGENDARY_JERRY.getItemStackOrNull(), 5_000)
        }
        if (name.removeColor().contains("Spaceman")) {
            logger.log("Spaceman!")
            ItemBlink.setBlink(SPACE_HELM.getItemStackOrNull(), 5_000)
        }
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (config.hypixelArrivedMessage && visitorArrivePattern.matcher(event.message).matches()) {
            event.blockedReason = "new_visitor_arrived"
        }

        // TODO use NpcChatEvent
        if (GardenApi.inGarden() && config.hideChat && hideVisitorMessage(event.message)) {
            event.blockedReason = "garden_visitor_message"
        }

        if (config.shoppingList.enabled) {
            partialAcceptedPattern.matchMatcher(event.message) {
                ChatUtils.chat("Talk to the visitor again to update the number of items needed!")
            }
        }
    }

    private fun doesVisitorEntityExist(name: String) = EntityUtils.getEntities<EntityOtherPlayerMP>().any {
        it.name.trim().equals(name, true)
    }

    private fun hideVisitorMessage(message: String) = visitorChatMessagePattern.matchMatcher(message) {
        val color = group("color")
        if (color == null || color == "§e") return false // Non-visitor NPC, probably Jacob

        val name = group("name")
        if (name in setOf("Beth", "Maeve", "Spaceman")) return false

        val isInKnownVisitors = VisitorApi.getVisitorsMap().keys.any { it.removeColor() == name }

        return if (isInKnownVisitors) true
        else doesVisitorEntityExist(name)
    } ?: false

    private fun update() {
        checkVisitorsReady()
        updateDisplay()
    }

    private fun checkVisitorsReady() {
        for (visitor in VisitorApi.getVisitors()) {
            val visitorName = visitor.visitorName
            val entity = visitor.getEntity()
            if (entity == null) {
                NpcVisitorFix.findNametag(visitorName.removeColor())?.let {
                    findEntity(it, visitor)
                }
            }

            if (visitor.status in setOf(VisitorApi.VisitorStatus.WAITING, VisitorApi.VisitorStatus.READY)) {
                if (hasItems(visitor)) {
                    VisitorApi.changeStatus(visitor, VisitorApi.VisitorStatus.READY, "hasItems")
                } else {
                    VisitorApi.changeStatus(visitor, VisitorApi.VisitorStatus.WAITING, "noLongerHasItems")
                }
            }

            if ((config.highlightStatus == HighlightMode.COLOR || config.highlightStatus == HighlightMode.BOTH) &&
                entity is EntityLivingBase
            ) {
                val color = visitor.status.color
                if (color != null) {
                    RenderLivingEntityHelper.setEntityColor(
                        entity,
                        color,
                    ) { config.highlightStatus == HighlightMode.COLOR || config.highlightStatus == HighlightMode.BOTH }
                }
                if (color == null || !GardenApi.inGarden()) {
                    // Haven't gotten either of the known effected visitors (Vex and Leo) so can't test for sure
                    RenderLivingEntityHelper.removeEntityColor(entity)
                }
            }
        }
    }

    private fun findEntity(nameTag: EntityArmorStand, visitor: VisitorApi.Visitor) {
        for (entity in EntityUtils.getAllEntities()) {
            if (entity is EntityArmorStand) continue
            if (entity.getLorenzVec().distanceIgnoreY(nameTag.getLorenzVec()) != 0.0) continue

            visitor.entityId = entity.entityId
            visitor.nameTagEntityId = nameTag.entityId
        }
    }

    private fun hasItems(visitor: VisitorApi.Visitor): Boolean {
        var ready = true
        for ((internalName, required) in visitor.shoppingList) {
            val having = internalName.getAmountInInventory() + internalName.getAmountInSacks()
            if (having < required) {
                ready = false
            }
        }
        return ready
    }

    // todo use RenderDisplayHelper
    private fun renderDisplay() {
        if (showGui() && shouldShowShoppingList()) {
            config.shoppingList.position.renderRenderables(display, posLabel = "Visitor Shopping List")
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onScreenDrawn(event: ScreenDrawnEvent) {
        if (!config.shoppingList.enabled) return
        val gui = event.gui
        if (gui !is GuiEditSign) return

        renderDisplay()
    }

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent) {
        if (!config.shoppingList.enabled) return
        val currentScreen = Minecraft.getMinecraft().currentScreen
        if (currentScreen is GuiEditSign) return

        renderDisplay()
    }

    private fun shouldShowShoppingList(): Boolean {
        if (VisitorApi.inInventory) return true
        if (BazaarApi.inBazaarInventory) return true
        val currentScreen = Minecraft.getMinecraft().currentScreen ?: return true
        val isInOwnInventory = currentScreen is GuiInventory
        if (isInOwnInventory) return true
        if (currentScreen is GuiEditSign && (currentScreen.isBazaarSign() || currentScreen.isSupercraftAmountSetSign())) return true

        return false
    }

    private fun hideExtraGuis() = GardenApi.hideExtraGuis() && !VisitorApi.inInventory

    private fun showGui(): Boolean {
        if (IslandType.HUB.isCurrent()) {
            if (config.shoppingList.inBazaarAlley && SkyBlockUtils.graphArea == "Bazaar Alley") {
                return true
            }
            if (config.shoppingList.inFarmingAreas && SkyBlockUtils.graphArea == "Farm") {
                return true
            }
        }
        if (config.shoppingList.inFarmingAreas && IslandType.THE_FARMING_ISLANDS.isCurrent()) return true
        if (hideExtraGuis()) return false
        if (GardenApi.inGarden()) {
            if (GardenApi.onBarnPlot) return true
            if (!config.shoppingList.onlyWhenClose) return true
        }
        return false
    }

    @HandleEvent
    fun onDebug(event: DebugDataCollectEvent) {
        event.title("Garden Visitor Stats")

        if (!GardenApi.inGarden()) {
            event.addIrrelevant("not in garden")
            return
        }

        event.addIrrelevant {
            val visitors = VisitorApi.getVisitors()

            add("visitors: ${visitors.size}")

            for (visitor in visitors) {
                add(" ")
                add("visitorName: '${visitor.visitorName}'")
                add("status: '${visitor.status}'")
                if (visitor.shoppingList.isNotEmpty()) {
                    add("shoppingList: '${visitor.shoppingList}'")
                }
                visitor.offer?.offerItem?.getInternalName()?.let {
                    add("offer: '$it'")
                }
            }
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(3, "garden.visitorNeedsDisplay", "garden.visitors.needs.display")
        event.move(3, "garden.visitorNeedsPos", "garden.visitors.needs.pos")
        event.move(3, "garden.visitorNeedsOnlyWhenClose", "garden.visitors.needs.onlyWhenClose")
        event.move(3, "garden.visitorNeedsInBazaarAlley", "garden.visitors.needs.inBazaarAlley")
        event.move(3, "garden.visitorNeedsShowPrice", "garden.visitors.needs.showPrice")
        event.move(3, "garden.visitorItemPreview", "garden.visitors.needs.itemPreview")
        event.move(3, "garden.visitorShowPrice", "garden.visitors.inventory.showPrice")
        event.move(3, "garden.visitorExactAmountAndTime", "garden.visitors.inventory.exactAmountAndTime")
        event.move(3, "garden.visitorCopperPrice", "garden.visitors.inventory.copperPrice")
        event.move(3, "garden.visitorCopperTime", "garden.visitors.inventory.copperTime")
        event.move(3, "garden.visitorExperiencePrice", "garden.visitors.inventory.experiencePrice")
        event.move(3, "garden.visitorRewardWarning.notifyInChat", "garden.visitors.rewardWarning.notifyInChat")
        event.move(3, "garden.visitorRewardWarning.showOverName", "garden.visitors.rewardWarning.showOverName")
        event.move(
            3,
            "garden.visitorRewardWarning.preventRefusing",
            "garden.visitors.rewardWarning.preventRefusing",
        )
        event.move(3, "garden.visitorRewardWarning.bypassKey", "garden.visitors.rewardWarning.bypassKey")
        event.move(3, "garden.visitorRewardWarning.drops", "garden.visitors.rewardWarning.drops")
        event.move(3, "garden.visitorNotificationChat", "garden.visitors.notificationChat")
        event.move(3, "garden.visitorNotificationTitle", "garden.visitors.notificationTitle")
        event.move(3, "garden.visitorHighlightStatus", "garden.visitors.highlightStatus")
        event.move(3, "garden.visitorColoredName", "garden.visitors.coloredName")
        event.move(3, "garden.visitorHypixelArrivedMessage", "garden.visitors.hypixelArrivedMessage")
        event.move(3, "garden.visitorHideChat", "garden.visitors.hideChat")

        event.transform(12, "garden.visitors.rewardWarning.drops") { element ->
            val drops = JsonArray()
            for (jsonElement in element.asJsonArray) {
                val old = jsonElement.asString
                val new = VisitorReward.entries.firstOrNull { old.startsWith(it.name) }
                if (new == null) {
                    println("error with migrating old VisitorReward entity: '$old'")
                    continue
                }
                drops.add(JsonPrimitive(new.name))
            }

            drops
        }
        event.transform(54, "garden.visitors.rewardWarning.drops") { element ->
            val drops = JsonArray()
            for (entry in element.asJsonArray) {
                drops.add(JsonPrimitive(entry.asString))
            }
            drops.add(JsonPrimitive(VisitorReward.COPPER_DYE.name))
            drops
        }

        event.move(18, "garden.visitors.needs", "garden.visitors.shoppingList")

        event.move(87, "garden.visitors.shoppingList.display", "garden.visitors.shoppingList.enabled")
        event.move(87, "garden.visitors.shoppingList.pos", "garden.visitors.shoppingList.position")
    }
}

