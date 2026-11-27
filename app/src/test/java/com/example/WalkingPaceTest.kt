package com.example

import com.example.game.engine.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Walking speeds up with level, and taps never cause double steps or cut a glide short. */
class WalkingPaceTest {

    @Test
    fun walkingGetsFasterWithLevelWithDiminishingReturns() {
        assertEquals(210L, GameEngine.stepIntervalMs(1))
        val lv20 = GameEngine.stepIntervalMs(20)
        val lv50 = GameEngine.stepIntervalMs(50)
        val lv100 = GameEngine.stepIntervalMs(100)
        assertTrue("Lv 20 should be noticeably faster ($lv20 ms)", lv20 in 175L..190L)
        assertTrue("Lv 50 faster still ($lv50 ms)", lv50 in 140L..155L)
        assertTrue("Lv 100 near the cap ($lv100 ms)", lv100 in 120L..132L)
        assertEquals(GameEngine.MIN_STEP_INTERVAL_MS, GameEngine.stepIntervalMs(500))
        var previous = Long.MAX_VALUE
        for (level in 1..150) {
            val ms = GameEngine.stepIntervalMs(level)
            assertTrue("Pace must never get slower with level (Lv $level)", ms <= previous)
            previous = ms
        }
        // Remote players are told the same pace (stays within the network clamp 2.5..8.5 tiles/s)
        assertEquals(1000f / 210f, GameEngine.walkSpeedTilesPerSec(1), 0.001f)
        assertTrue(GameEngine.walkSpeedTilesPerSec(200) <= 8.5f)
    }

    @Test
    fun glideFollowsTheStepIntervalSoMotionIsContinuous() {
        val hero = GameEngine.createNewWorld("Pacer").copy(level = 50, lastPlayerMoveMs = 10_000L)
        val interval = GameEngine.stepIntervalMs(50)
        assertEquals(0f, GameEngine.stepGlideFraction(hero, 10_000L), 0.001f)
        assertEquals(0.5f, GameEngine.stepGlideFraction(hero, 10_000L + interval / 2), 0.02f)
        assertEquals(1f, GameEngine.stepGlideFraction(hero, 10_000L + interval), 0.001f)
        assertFalse(GameEngine.isStepReady(hero, 10_000L + interval - 1))
        assertTrue(GameEngine.isStepReady(hero, 10_000L + interval))
    }

    @Test
    fun tappingMidStepReroutesWithoutAnExtraStep() {
        val start = GameEngine.createNewWorld("Tapper")
        val (sx, sy) = start.playerX to start.playerY

        // First tap: ready to move, so the hero steps immediately
        val first = GameEngine.handleTileTap(start, sx + 4, sy, nowMs = 5_000L)
        assertEquals(sx + 1, first.playerX)
        assertEquals(5_000L, first.lastPlayerMoveMs)

        // Second tap 20 ms later (mid-glide): route changes, but no second step yet
        val second = GameEngine.handleTileTap(first, sx + 1, sy + 3, nowMs = 5_020L)
        assertEquals("No double step mid-glide", first.playerX, second.playerX)
        assertEquals(first.playerY, second.playerY)
        assertEquals(5_000L, second.lastPlayerMoveMs)
        assertTrue("New route is queued for the walk loop", second.walkPath.isNotEmpty())

        // Once the glide has finished, the queued route continues on rhythm
        val due = 5_000L + GameEngine.stepIntervalMs(second.level)
        val next = GameEngine.stepAutoFollowOrPath(second, due)
        assertTrue("Hero continues along the new route", next.playerY == sy + 1 || next.playerX != second.playerX)
    }
}
