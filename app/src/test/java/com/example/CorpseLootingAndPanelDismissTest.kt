package com.example

import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.CorpseEntity
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemStack
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorpseLootingAndPanelDismissTest {

    @Test
    fun fallenBackpackTapItemTransfersFullStackUpdatesCapAndRemovesWhenEmpty() {
        val base = GameEngine.createNewWorld(name = "Idemon").copy(
            playerX = 26,
            playerY = 44,
            backpack = emptyList()
        )
        val fallen = CorpseEntity(
            id = "fallen_1",
            floor = 0,
            x = 27,
            y = 44,
            label = "Idemon's Fallen Backpack",
            isPlayerCorpse = true,
            gold = 0,
            items = listOf(
                ItemStack("health_potion", 5),
                ItemStack("wooden_arrows", 20)
            )
        )
        val opened = base.copy(
            corpses = listOf(fallen),
            activePanel = ActivePanel.LOOT_CORPSE,
            inspectingCorpseId = fallen.id
        )
        val capBefore = opened.remainingCapOz()

        // Tap first row (5x Health Potion) -> full stack transfers, Cap decreases immediately
        val afterFirst = GameEngine.lootItemFromCorpse(opened, fallen.id, stackIndex = 0, nowMs = 1_000L)
        assertEquals(5, afterFirst.itemCount("health_potion"))
        val expectedPotionWeight = ItemCatalog.get("health_potion").carriedWeightOz() * 5
        assertEquals(capBefore - expectedPotionWeight, afterFirst.remainingCapOz(), 0.01)
        assertEquals(1, afterFirst.corpses.first().items.size)
        assertEquals(ActivePanel.LOOT_CORPSE, afterFirst.activePanel)

        // Tap last remaining row (20x Wooden Arrows) -> corpse removed and panel closes automatically
        val afterSecond = GameEngine.lootItemFromCorpse(afterFirst, fallen.id, stackIndex = 0, nowMs = 2_000L)
        assertEquals(20, afterSecond.itemCount("wooden_arrows"))
        assertTrue("Fallen backpack should be removed once emptied", afterSecond.corpses.isEmpty())
        assertEquals(ActivePanel.NONE, afterSecond.activePanel)
        assertNull(afterSecond.inspectingCorpseId)
        assertTrue(afterSecond.statusLog.last().contains("recovered your fallen backpack", ignoreCase = true))
    }

    @Test
    fun tapItemRespectsCapWithPartialStackAndTooHeavyFeedback() {
        val base = GameEngine.createNewWorld(name = "Idemon").copy(
            backpack = emptyList()
        )
        val potionWeight = ItemCatalog.get("health_potion").carriedWeightOz()
        val usedNow = base.usedCapOz()
        // Allow exactly 2 health potions worth of remaining Cap
        val tightState = base.copy(maxCapOz = usedNow + potionWeight * 2.0 + 0.05)

        val corpse = CorpseEntity(
            id = "fallen_tight",
            floor = 0,
            x = base.playerX + 1,
            y = base.playerY,
            label = "Idemon's Fallen Backpack",
            isPlayerCorpse = true,
            gold = 0,
            items = listOf(ItemStack("health_potion", 5))
        )
        val opened = tightState.copy(
            corpses = listOf(corpse),
            activePanel = ActivePanel.LOOT_CORPSE,
            inspectingCorpseId = corpse.id
        )

        // First tap takes 2 of 5 potions and leaves 3 behind
        val afterPartial = GameEngine.lootItemFromCorpse(opened, corpse.id, stackIndex = 0, nowMs = 1_000L)
        assertEquals(2, afterPartial.itemCount("health_potion"))
        assertEquals(3, afterPartial.corpses.first().items.first().count)
        assertTrue(afterPartial.statusLog.last().contains("2/5"))

        // Second tap when Cap is exhausted logs clear "Too heavy!" message without changing corpse
        val afterTooHeavy = GameEngine.lootItemFromCorpse(afterPartial, corpse.id, stackIndex = 0, nowMs = 2_000L)
        assertEquals(2, afterTooHeavy.itemCount("health_potion"))
        assertEquals(3, afterTooHeavy.corpses.first().items.first().count)
        assertTrue(afterTooHeavy.statusLog.last().contains("Too heavy!"))
    }

    @Test
    fun lootGoldAndLootAllWorkOnMonsterCorpsesAndFallenBackpacks() {
        val base = GameEngine.createNewWorld(name = "Idemon").copy(
            gold = 50,
            backpack = emptyList()
        )
        val monsterCorpse = CorpseEntity(
            id = "m_corpse_1",
            floor = 0,
            x = base.playerX + 1,
            y = base.playerY,
            label = "Slain Cave Rat",
            isPlayerCorpse = false,
            gold = 35,
            items = listOf(ItemStack("fresh_meat", 2), ItemStack("blank_rune", 1))
        )
        val opened = base.copy(
            corpses = listOf(monsterCorpse),
            activePanel = ActivePanel.LOOT_CORPSE,
            inspectingCorpseId = monsterCorpse.id
        )

        // Tap Gold row first
        val afterGold = GameEngine.lootGoldFromCorpse(opened, monsterCorpse.id, nowMs = 1_000L)
        assertEquals(85, afterGold.gold)
        assertEquals(0, afterGold.corpses.first().gold)
        assertEquals(ActivePanel.LOOT_CORPSE, afterGold.activePanel)

        // Loot All transfers remaining items, removes corpse, and closes panel
        val afterAll = GameEngine.lootAllFromCorpse(afterGold, monsterCorpse.id, nowMs = 2_000L)
        assertEquals(2, afterAll.itemCount("fresh_meat"))
        assertEquals(1, afterAll.itemCount("blank_rune"))
        assertTrue(afterAll.corpses.isEmpty())
        assertEquals(ActivePanel.NONE, afterAll.activePanel)
        assertNull(afterAll.inspectingCorpseId)
    }

    @Test
    fun tappingMapGroundOrMonsterAutoClosesAnyOpenWindowInOneTap() {
        val base = GameEngine.createNewWorld(name = "Idemon").copy(
            playerX = 26,
            playerY = 44
        )
        val panelsToTest = listOf(
            ActivePanel.LOOT_CORPSE,
            ActivePanel.EQUIPMENT,
            ActivePanel.BACKPACK,
            ActivePanel.SKILLS,
            ActivePanel.RUNECRAFTING_TABLE,
            ActivePanel.FLETCHING_BENCH,
            ActivePanel.SHOP,
            ActivePanel.DEPOT
        )

        // 1. Tapping a walkable ground tile closes every panel type AND starts moving in one tap
        for (panel in panelsToTest) {
            val withPanel = base.copy(
                activePanel = panel,
                inspectingCorpseId = if (panel == ActivePanel.LOOT_CORPSE) "fallen_1" else null
            )
            val afterTap = GameEngine.handleTileTap(withPanel, tx = 28, ty = 44, nowMs = 5_000L)
            assertEquals("Panel $panel should auto-close on map ground tap", ActivePanel.NONE, afterTap.activePanel)
            assertNull(afterTap.inspectingCorpseId)
            assertTrue(
                "Player should move or queue walkPath in the same tap when closing $panel",
                afterTap.playerX != withPanel.playerX || afterTap.walkPath.isNotEmpty()
            )
        }

        // 2. Tapping a monster closes open panel AND locks target / melee auto-follow in one tap
        val rat = MonsterEntity(
            id = 777,
            type = MonsterType.CAVE_RAT,
            floor = 0,
            x = 28,
            y = 44,
            spawnX = 28,
            spawnY = 44,
            hp = 20,
            maxHp = 20
        )
        val withBackpackOpen = base.copy(
            activePanel = ActivePanel.BACKPACK,
            monsters = listOf(rat)
        )
        val afterMonsterTap = GameEngine.handleTileTap(withBackpackOpen, tx = rat.x, ty = rat.y, nowMs = 6_000L)
        assertEquals(ActivePanel.NONE, afterMonsterTap.activePanel)
        assertEquals(rat.id, afterMonsterTap.targetMonsterId)
        assertTrue(afterMonsterTap.autoFollowTarget)
    }
}
