package com.example

import com.example.game.engine.Bestiary
import com.example.game.engine.DungeonGuides
import com.example.game.engine.GameEngine
import com.example.game.engine.HuntContracts
import com.example.game.engine.NpcInteractions
import com.example.game.engine.TownCrier
import com.example.game.engine.VendorCatalog
import com.example.game.model.ActivePanel
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemStack
import com.example.game.model.MonsterType
import com.example.game.world.NpcRole
import com.example.game.world.WorldMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TownsfolkNpcTest {

    private val newRoles = setOf(
        NpcRole.FISHERMAN, NpcRole.BLACKSMITH, NpcRole.FLETCHER, NpcRole.WANDERING_MERCHANT,
        NpcRole.HUNT_MASTER, NpcRole.BESTIARY_SAGE, NpcRole.TOWN_CRIER, NpcRole.DUNGEON_GUIDE
    )

    private fun tapNpc(role: NpcRole, name: String? = null) = run {
        val npc = WorldMaps.npcs.first { it.role == role && (name == null || it.name == name) }
        val hero = GameEngine.createNewWorld("Tester").copy(floor = npc.floor, playerX = npc.x + 1, playerY = npc.y)
        GameEngine.handleTileTap(hero, npc.x, npc.y, 1_000L)
    }

    @Test
    fun everyNewTownsfolkStandsOnOpenGroundAwayFromPortals() {
        val floor0 = WorldMaps.getFloor(0)
        val spots = WorldMaps.npcs.filter { it.role in newRoles }
        assertEquals(16, spots.size)
        spots.forEach { n ->
            assertTrue("${n.name} must stand on walkable ground", floor0.tileAt(n.x, n.y).walkable)
            assertTrue("${n.name} must be approachable", listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dy) -> floor0.tileAt(n.x + dx, n.y + dy).walkable })
            assertTrue(WorldMaps.portals.none { it.fromFloor == 0 && it.fromX == n.x && it.fromY == n.y })
        }
        // Town NPCs are inside the safe zone
        spots.filter { it.role != NpcRole.DUNGEON_GUIDE }.forEach { assertTrue("${it.name} should be in town", floor0.isProtectionZone(it.x, it.y)) }
        // Every guide has lines
        spots.filter { it.role == NpcRole.DUNGEON_GUIDE }.forEach { assertTrue(DungeonGuides.byGuideName.containsKey(it.name)) }
    }

    @Test
    fun specialistShopsOpenWithTheirOwnStockAndPayMoreForTheirTrade() {
        val atBorin = tapNpc(NpcRole.BLACKSMITH)
        assertEquals(ActivePanel.SHOP, atBorin.activePanel)
        assertEquals(VendorCatalog.BLACKSMITH, atBorin.activeShopId)
        assertTrue(VendorCatalog.stockFor(atBorin.activeShopId).any { it.itemId == "bog_iron_pickaxe" })

        val copper = ItemCatalog.get("copper_ore").sellPrice
        assertTrue(GameEngine.shopSellPrice(atBorin, "copper_ore") > copper)
        val sold = GameEngine.sellBackpackItem(atBorin.copy(gold = 0, backpack = listOf(ItemStack("copper_ore", 4))), "copper_ore", 4)
        assertEquals(GameEngine.shopSellPrice(atBorin, "copper_ore") * 4, sold.gold)

        val bought = GameEngine.buyShopItem(atBorin.copy(gold = 1_000), "bog_iron_pickaxe", 1)
        assertEquals(1_000 - 260, bought.gold)
        assertTrue(bought.hasItem("bog_iron_pickaxe"))

        // Alaric is unchanged
        val alaric = tapNpc(NpcRole.SHOP_ALCHEMIST, "Alaric")
        assertNull(alaric.activeShopId)
        assertEquals(copper, GameEngine.shopSellPrice(alaric, "copper_ore"))

        assertEquals(VendorCatalog.FISHERMAN, tapNpc(NpcRole.FISHERMAN).activeShopId)
        assertEquals(VendorCatalog.FLETCHER, tapNpc(NpcRole.FLETCHER).activeShopId)
        assertEquals(VendorCatalog.WANDERER, tapNpc(NpcRole.WANDERING_MERCHANT).activeShopId)
    }

    @Test
    fun wanderingMerchantStockRotatesEveryFourHours() {
        val t0 = 1_700_000_000_000L
        val a = VendorCatalog.wanderingStock(t0)
        assertEquals(4, a.size)
        assertEquals(a, VendorCatalog.wanderingStock(t0 + 1_000L))
        val rotations = (0 until 12).map { VendorCatalog.wanderingStock(t0 + it * VendorCatalog.WANDERER_ROTATION_MS) }.toSet()
        assertTrue("Stock should change between rotations", rotations.size > 3)
        a.forEach { assertTrue(it.price > 0) }
    }

    @Test
    fun huntContractFlowFromAcceptToReward() {
        var s = tapNpc(NpcRole.HUNT_MASTER)
        assertEquals(ActivePanel.HUNT_BOARD, s.activePanel)
        s = NpcInteractions.handleAction(s, "hunt_accept:rats", 2_000L)
        val contract = NpcInteractions.activeContract(s)
        assertNotNull(contract)
        // Can't take two at once
        assertEquals(contract, NpcInteractions.activeContract(NpcInteractions.handleAction(s, "hunt_accept:snakes", 2_000L)))

        val rat = s.monsters.first { it.type == MonsterType.CAVE_RAT }
        val notRat = s.monsters.first { it.type != MonsterType.CAVE_RAT && !it.type.isBoss }
        s = NpcInteractions.onKillCredited(s, notRat, 3_000L)
        assertEquals(0, NpcInteractions.huntKills(s))
        repeat(contract!!.count) { s = NpcInteractions.onKillCredited(s, rat, 3_000L) }
        assertEquals(contract.count, NpcInteractions.huntKills(s))

        val goldBefore = s.gold
        val xpBefore = s.xp
        s = NpcInteractions.handleAction(s, "hunt_turn_in", 4_000L)
        assertEquals(goldBefore + contract.goldReward, s.gold)
        assertTrue(s.xp >= xpBefore + contract.xpReward)
        assertNull(NpcInteractions.activeContract(s))
        assertEquals(1, NpcInteractions.huntsCompleted(s))

        // Level-gated contracts can't be taken early
        val lowLevel = GameEngine.createNewWorld("Newbie")
        val boss = HuntContracts.byId("b_elderdragon")!!
        assertNull(NpcInteractions.activeContract(NpcInteractions.acceptContract(lowLevel, boss.id)))
    }

    @Test
    fun bestiaryEntriesUnlockAfterEnoughKillsAndBoostDamage() {
        var s = tapNpc(NpcRole.BESTIARY_SAGE)
        assertEquals(ActivePanel.BESTIARY, s.activePanel)
        val rat = s.monsters.first { it.type == MonsterType.CAVE_RAT }
        repeat(Bestiary.REGULAR_KILLS - 1) { s = NpcInteractions.onKillCredited(s, rat, 1_000L) }
        assertTrue(NpcInteractions.completedUnclaimed(s).isEmpty())
        assertEquals(s, NpcInteractions.claimBestiary(s, MonsterType.CAVE_RAT.name, 1_000L).copy(statusLog = s.statusLog))

        s = NpcInteractions.onKillCredited(s, rat, 1_000L)
        assertEquals(listOf(MonsterType.CAVE_RAT), NpcInteractions.completedUnclaimed(s))
        val gold = s.gold
        s = NpcInteractions.handleAction(s, "bestiary_claim:CAVE_RAT", 2_000L)
        assertTrue(NpcInteractions.isClaimed(s, MonsterType.CAVE_RAT))
        assertEquals(gold + Bestiary.goldReward(MonsterType.CAVE_RAT), s.gold)
        assertEquals(21, NpcInteractions.applyBestiaryBonus(s, MonsterType.CAVE_RAT, 20))
        assertEquals(20, NpcInteractions.applyBestiaryBonus(s, MonsterType.YOUNG_SNAKE, 20))

        // Progress survives a save/load round trip
        val reloaded = GameEngine.fromSaveData(GameEngine.toSaveData(s))
        assertEquals(s.npcProgress, reloaded.npcProgress)
    }

    @Test
    fun townCrierFeaturesOneCreaturePerDayForBonusXp() {
        val day = TownCrier.DAY_MS
        val t0 = 1_700_000_000_000L - (1_700_000_000_000L % day)
        val featured = TownCrier.featuredCreature(t0)
        assertEquals(featured, TownCrier.featuredCreature(t0 + day - 1))
        val week = (0 until 14).map { TownCrier.featuredCreature(t0 + it * day) }.toSet()
        assertTrue(week.size > 3)

        val hero = GameEngine.createNewWorld("Crier")
        val m = hero.monsters.first().copy(type = featured, scaledXpReward = 100, isSummon = false)
        val other = m.copy(type = if (featured == MonsterType.CAVE_RAT) MonsterType.YOUNG_SNAKE else MonsterType.CAVE_RAT)
        val withBonus = NpcInteractions.onKillCredited(hero, m, 1_000L, wallClockMs = t0)
        val without = NpcInteractions.onKillCredited(hero, other, 1_000L, wallClockMs = t0)
        assertEquals(150L, withBonus.xp - hero.xp)
        assertEquals(100L, without.xp - hero.xp)

        val news = tapNpc(NpcRole.TOWN_CRIER)
        assertTrue(news.statusLog.any { it.startsWith("Crier Tobias") })
        assertFalse(news.activePanel == ActivePanel.SHOP)
    }
}
