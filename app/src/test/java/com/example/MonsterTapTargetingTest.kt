package com.example

import com.example.game.engine.GameEngine
import com.example.game.engine.MonsterTapTargeting
import com.example.game.engine.MonsterTapTargeting.Candidate
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterTapTargetingTest {
    private val tileW = 96f
    private val tileH = 48f
    private val finger = 70f  // ~26dp on a 2.7x screen

    @Test
    fun tappingAnywhereOnTheBodyHitsTheMonster() {
        val rat = Candidate(monsterId = 1, feetX = 500f, feetY = 600f, bodyHeightPx = 110f)
        // Feet, chest, head and a bit beside the body all count
        for ((x, y) in listOf(500f to 600f, 500f to 550f, 500f to 495f, 540f to 560f, 470f to 620f)) {
            assertEquals("tap at ($x,$y)", 1, MonsterTapTargeting.pick(listOf(rat), x, y, tileW, tileH, finger))
        }
        // Clearly off to the side or far above misses
        assertNull(MonsterTapTargeting.pick(listOf(rat), 700f, 560f, tileW, tileH, finger))
        assertNull(MonsterTapTargeting.pick(listOf(rat), 500f, 300f, tileW, tileH, finger))
    }

    @Test
    fun smallMonstersStillGetAFingerSizedTarget() {
        val snake = Candidate(monsterId = 2, feetX = 300f, feetY = 400f, bodyHeightPx = 30f)
        assertEquals(2, MonsterTapTargeting.pick(listOf(snake), 300f + finger * 0.9f, 395f, tileW, tileH, finger))
    }

    @Test
    fun overlappingMonstersPickTheOneClosestToTheTap() {
        val a = Candidate(1, 500f, 600f, 110f)
        val b = Candidate(2, 560f, 620f, 110f)
        assertEquals(1, MonsterTapTargeting.pick(listOf(a, b), 495f, 560f, tileW, tileH, finger))
        assertEquals(2, MonsterTapTargeting.pick(listOf(a, b), 565f, 580f, tileW, tileH, finger))
    }

    @Test
    fun hitBoxFollowsAGlidingMonsterAndTappingAgainToggles() {
        val m = MonsterEntity(id = 7, type = MonsterType.CAVE_RAT, floor = 0, x = 11, y = 10, spawnX = 10, spawnY = 10,
            hp = 20, maxHp = 20, lastMoveFromX = 10, lastMoveFromY = 10, lastMoveTimeMs = 1_000L)
        val (midX, _) = MonsterTapTargeting.visualTile(m, 1_110L)
        assertEquals(10.5f, midX, 0.01f)
        assertEquals(11f, MonsterTapTargeting.visualTile(m, 5_000L).first, 0.001f)

        // The engine toggles: first tap highlights, second tap on the same monster clears it
        val hero = GameEngine.createNewWorld("Hunter")
        val rat = m.copy(x = hero.playerX + 2, y = hero.playerY, lastMoveFromX = hero.playerX + 2, lastMoveFromY = hero.playerY)
        val world = hero.copy(monsters = listOf(rat))
        val targeted = GameEngine.handleTileTap(world, rat.x, rat.y, nowMs = 10_000L)
        assertEquals(rat.id, targeted.targetMonsterId)
        val cleared = GameEngine.handleTileTap(targeted.copy(walkPath = emptyList(), autoFollowTarget = false, playerX = hero.playerX, playerY = hero.playerY), rat.x, rat.y, nowMs = 12_000L)
        assertNull(cleared.targetMonsterId)
        assertTrue(cleared.statusLog.last().contains("Target cleared"))
    }
}
