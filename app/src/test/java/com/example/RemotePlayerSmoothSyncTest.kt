package com.example

import com.example.game.model.Direction
import com.example.game.model.Vocation
import com.example.game.multiplayer.LocalPlayerPresencePayload
import com.example.game.multiplayer.PlayerActionState
import com.example.game.multiplayer.RealtimeMultiplayerManager
import com.example.game.multiplayer.RemotePlayerInterpolator
import com.example.game.multiplayer.RemotePlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePlayerSmoothSyncTest {

    @Test
    fun lightweightMovementBroadcastEncodesRequiredFieldsAndResistsStaleDbRubberBanding() {
        var animClock = 10_000L
        val mgr = RealtimeMultiplayerManager(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            nowProvider = { 1_700_000_000_000L },
            animClockProvider = { animClock }
        )

        val localPayload = LocalPlayerPresencePayload(
            playerId = "p_local",
            name = "Roland",
            level = 12,
            vocation = Vocation.KNIGHT,
            tunicColorIndex = 1,
            hairColorIndex = 2,
            x = 200,
            y = 195,
            targetX = 201,
            targetY = 195,
            floor = 0,
            hp = 240,
            maxHp = 260,
            joinedAt = 1000L,
            direction = Direction.EAST,
            actionState = PlayerActionState.WALKING,
            regionId = "rookvale_overworld",
            weaponId = "rookvale_gladius",
            offhandId = "steel_shield",
            armorId = "brass_armor",
            helmetId = "viking_helmet"
        )
        mgr.connect(localPayload)
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"phx_reply","payload":{"status":"ok"},"ref":"1"}"""
        )

        // 1. Verify lightweight movement payload includes every required real-time movement field
        val moveMap = mgr.movementPayloadToMap(localPayload)
        val requiredKeys = listOf(
            "playerId", "name", "x", "y", "targetX", "targetY",
            "floor", "regionId", "facing", "hp", "maxHp",
            "weaponId", "offhandId", "armorId", "helmetId"
        )
        for (key in requiredKeys) {
            assertTrue("Lightweight movement packet must contain '$key'", moveMap.containsKey(key))
        }
        assertEquals(201, moveMap["targetX"])
        assertEquals(195, moveMap["targetY"])
        assertEquals("EAST", moveMap["facing"])

        // 2. Receive incoming player_move broadcast for a remote peer gliding from (204,195) toward (205,195)
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"broadcast","payload":{"event":"player_move","payload":{"playerId":"p_lyra","name":"Lyra","x":204,"y":195,"targetX":205,"targetY":195,"floor":0,"regionId":"rookvale_overworld","facing":"EAST","actionState":"WALKING","hp":210,"maxHp":240,"weaponId":"duskmetal_falchion","offhandId":"steel_shield","armorId":"brass_armor","helmetId":"viking_helmet"}}}"""
        )
        val lyraLive = mgr.uiState.value.remotePlayers.firstOrNull { it.playerId == "p_lyra" }
        assertNotNull(lyraLive)
        assertEquals(204, lyraLive!!.x)
        assertEquals(195, lyraLive.y)
        assertEquals(205, lyraLive.targetX)
        assertEquals(195, lyraLive.targetY)
        assertEquals(Direction.EAST, lyraLive.direction)
        assertEquals("duskmetal_falchion", lyraLive.weaponId)
        assertEquals("steel_shield", lyraLive.offhandId)

        // 3. Stale public.players DB poll arrives 500ms later with an older coordinate (200,195) — MUST NOT rubber-band Lyra!
        animClock += 500L
        val staleDbSnapshot = RemotePlayerState(
            playerId = "p_lyra",
            name = "Lyra",
            level = 15,
            vocation = Vocation.PALADIN,
            tunicColorIndex = 0,
            hairColorIndex = 0,
            x = 200,
            y = 195,
            floor = 0,
            hp = 210,
            maxHp = 240,
            regionId = "rookvale_overworld",
            lastSeenMs = 1_700_000_005_000L // Even with skewed future wall-clock timestamp!
        )
        mgr.mergePolledPlayers(
            polled = listOf(staleDbSnapshot),
            localFloor = 0,
            localRegionId = "rookvale_overworld",
            localPlayerId = "p_local",
            localPlayerName = "Roland"
        )
        val lyraAfterDbPoll = mgr.uiState.value.remotePlayers.first { it.playerId == "p_lyra" }
        assertEquals("Stale DB row must never rubber-band live Realtime peer x", 204, lyraAfterDbPoll.x)
        assertEquals("Stale DB row must never rubber-band live Realtime peer targetX", 205, lyraAfterDbPoll.targetX)
    }

    @Test
    fun interpolatorGlidesSmoothlyWithPathPredictionAndOnlyHardSnapsBeyondFourTiles() {
        val interpolator = RemotePlayerInterpolator()
        val dt60 = 1f / 60f
        var tMs = 1_000L

        // Initial spawn at (200, 200) moving east toward (201, 200)
        var rp = RemotePlayerState(
            playerId = "p_ally",
            name = "Lyra",
            level = 14,
            vocation = Vocation.KNIGHT,
            tunicColorIndex = 0,
            hairColorIndex = 0,
            x = 200,
            y = 200,
            targetX = 201,
            targetY = 200,
            lastMoveMs = tMs,
            networkReceivedMs = tMs,
            floor = 0,
            hp = 200,
            maxHp = 200,
            direction = Direction.EAST,
            actionState = PlayerActionState.WALKING
        )

        val init = interpolator.updatePlayer(rp, nowUptimeMs = tMs, dtSecRaw = dt60)
        assertEquals(200f, init.renderX, 0.001f)
        assertEquals(200f, init.renderY, 0.001f)

        // Advance 7 frames (~117ms, between 120ms packets): path prediction should smoothly glide renderX > 200 toward 201
        var prevX = init.renderX
        var lastState = init
        repeat(7) {
            tMs += 17L
            lastState = interpolator.updatePlayer(rp, nowUptimeMs = tMs, dtSecRaw = dt60)
            assertFalse("Normal glide must never hard-snap", lastState.hardSnappedThisFrame)
            assertTrue("renderX should advance monotonically toward targetX", lastState.renderX >= prevX)
            assertTrue("Walk animation must remain active while gliding", lastState.walkPhase >= 0.02f)
            prevX = lastState.renderX
        }
        assertTrue(
            "Between packets, path prediction must advance renderX smoothly past 200.25 (was ${lastState.renderX})",
            lastState.renderX in 200.25f..201.0f
        )

        // Next 120ms packet arrives: peer stepped to (201, 200) heading to (202, 200)
        rp = rp.copy(x = 201, targetX = 202, lastMoveMs = tMs, networkReceivedMs = tMs)
        repeat(8) {
            tMs += 17L
            val next = interpolator.updatePlayer(rp, nowUptimeMs = tMs, dtSecRaw = dt60)
            assertFalse("Continuous step across tiles must not hard-snap", next.hardSnappedThisFrame)
            assertTrue("renderX should keep gliding forward smoothly", next.renderX >= lastState.renderX - 0.001f)
            assertTrue("Continuous walk stride must never drop to 0 mid-walk", next.walkPhase >= 0.02f)
            lastState = next
        }

        // Distance of 3.5 tiles (<= 4.0f) must STILL smoothly interpolate, NOT hard-snap
        rp = rp.copy(x = (lastState.renderX + 3.2f).toInt(), targetX = (lastState.renderX + 3.2f).toInt(), actionState = PlayerActionState.IDLE, networkReceivedMs = tMs)
        tMs += 17L
        val mediumGap = interpolator.updatePlayer(rp, nowUptimeMs = tMs, dtSecRaw = dt60)
        assertFalse("Gap <= 4 tiles must interpolate instead of hard-snapping", mediumGap.hardSnappedThisFrame)
        assertTrue("Gap <= 4 tiles should advance smoothly without teleporting immediately", mediumGap.renderX < rp.x.toFloat())

        // Distance > 4 tiles (e.g. temple respawn or teleport 12 tiles away) MUST hard-snap immediately
        rp = rp.copy(x = 225, y = 200, targetX = 225, targetY = 200, networkReceivedMs = tMs)
        tMs += 17L
        val teleported = interpolator.updatePlayer(rp, nowUptimeMs = tMs, dtSecRaw = dt60)
        assertTrue("Distance > 4 tiles must hard-snap", teleported.hardSnappedThisFrame)
        assertEquals(225f, teleported.renderX, 0.001f)
        assertEquals(200f, teleported.renderY, 0.001f)

        // Floor change (e.g., rope hole) MUST hard-snap immediately
        rp = rp.copy(floor = -1, x = 24, y = 38, targetX = 24, targetY = 38, networkReceivedMs = tMs)
        tMs += 17L
        val floorChanged = interpolator.updatePlayer(rp, nowUptimeMs = tMs, dtSecRaw = dt60)
        assertTrue("Floor change must hard-snap", floorChanged.hardSnappedThisFrame)
        assertEquals(24f, floorChanged.renderX, 0.001f)
        assertEquals(38f, floorChanged.renderY, 0.001f)
    }

    @Test
    fun movementBroadcastEncodesFromToSpeedTsAndParsesToXYAndSpeed() {
        var animClock = 5_000L
        val mgr = RealtimeMultiplayerManager(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            nowProvider = { 1_700_000_000_123L },
            animClockProvider = { animClock }
        )

        val payload = LocalPlayerPresencePayload(
            playerId = "p_runner",
            name = "Kael",
            level = 20,
            vocation = Vocation.KNIGHT,
            tunicColorIndex = 2,
            hairColorIndex = 1,
            x = 210,
            y = 180,
            fromX = 209,
            fromY = 180,
            targetX = 213,
            targetY = 180,
            moveSpeed = RemotePlayerInterpolator.MANUAL_STEP_SPEED_TILES_PER_SEC,
            floor = 0,
            hp = 300,
            maxHp = 300,
            joinedAt = 100L,
            direction = Direction.EAST,
            actionState = PlayerActionState.WALKING,
            regionId = "rookvale_overworld"
        )
        val map = mgr.movementPayloadToMap(payload)
        assertEquals(209, map["fromX"])
        assertEquals(180, map["fromY"])
        assertEquals(213, map["toX"])
        assertEquals(180, map["toY"])
        assertEquals(213, map["targetX"])
        assertEquals(180, map["targetY"])
        assertEquals(RemotePlayerInterpolator.MANUAL_STEP_SPEED_TILES_PER_SEC, (map["speed"] as Number).toFloat(), 0.01f)
        assertTrue("Movement packet must include timestamp ts", map.containsKey("ts"))

        mgr.connect(payload)
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"phx_reply","payload":{"status":"ok"},"ref":"1"}"""
        )
        // Incoming packet using toX/toY & speed (without targetX/targetY) should still parse cleanly
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"broadcast","payload":{"event":"player_move","payload":{"playerId":"p_scout","name":"Mira","fromX":214,"fromY":180,"x":215,"y":180,"toX":218,"toY":180,"speed":6.45,"floor":0,"regionId":"rookvale_overworld","facing":"EAST","actionState":"WALKING","hp":190,"maxHp":200}}}"""
        )
        val scout = mgr.uiState.value.remotePlayers.firstOrNull { it.playerId == "p_scout" }
        assertNotNull(scout)
        assertEquals(215, scout!!.x)
        assertEquals(218, scout.targetX)
        assertEquals(180, scout.targetY)
        assertEquals(6.45f, scout.moveSpeed, 0.01f)
    }

    @Test
    fun duplicateTileKeepaliveBroadcastDoesNotRubberBandMidStride() {
        val interpolator = RemotePlayerInterpolator()
        val dt60 = 1f / 60f
        var tMs = 2_000L

        var rp = RemotePlayerState(
            playerId = "p_walker",
            name = "Aria",
            level = 10,
            vocation = Vocation.PALADIN,
            tunicColorIndex = 0,
            hairColorIndex = 0,
            x = 100,
            y = 100,
            targetX = 103,
            targetY = 100,
            moveSpeed = RemotePlayerInterpolator.WALK_SPEED_TILES_PER_SEC,
            lastMoveMs = tMs,
            networkReceivedMs = tMs,
            floor = 0,
            hp = 150,
            maxHp = 150,
            direction = Direction.EAST,
            actionState = PlayerActionState.WALKING
        )

        var state = interpolator.updatePlayer(rp, nowUptimeMs = tMs, dtSecRaw = dt60)
        // Advance 6 frames (~100ms) mid-step toward tile 101
        repeat(6) {
            tMs += 17L
            state = interpolator.updatePlayer(rp, nowUptimeMs = tMs, dtSecRaw = dt60)
        }
        val midStepX = state.renderX
        assertTrue("Expected forward dead-reckoning progress (was $midStepX)", midStepX > 100.35f)

        // 100ms keepalive broadcast arrives while peer is still on tile (100, 100) before 210ms step finishes
        rp = rp.copy(networkReceivedMs = tMs, lastMoveMs = tMs)
        repeat(4) {
            tMs += 17L
            val next = interpolator.updatePlayer(rp, nowUptimeMs = tMs, dtSecRaw = dt60)
            assertTrue(
                "Duplicate-tile 100ms broadcast must never yank renderX backward (prev=${state.renderX}, next=${next.renderX})",
                next.renderX >= state.renderX - 0.0001f
            )
            assertTrue("Walk animation should stay active mid-stride", next.isGliding && next.walkPhase >= 0.02f)
            state = next
        }
    }

    @Test
    fun manualStepSpeedGlidesWithoutSnappingAndReturnsToIdleOnArrival() {
        val interpolator = RemotePlayerInterpolator()
        val dt60 = 1f / 60f
        var tMs = 3_000L

        var rp = RemotePlayerState(
            playerId = "p_fast",
            name = "Bram",
            level = 25,
            vocation = Vocation.KNIGHT,
            tunicColorIndex = 1,
            hairColorIndex = 0,
            x = 50,
            y = 50,
            targetX = 51,
            targetY = 50,
            moveSpeed = RemotePlayerInterpolator.MANUAL_STEP_SPEED_TILES_PER_SEC,
            lastMoveMs = tMs,
            networkReceivedMs = tMs,
            floor = 0,
            hp = 350,
            maxHp = 350,
            direction = Direction.EAST,
            actionState = PlayerActionState.WALKING
        )
        var state = interpolator.updatePlayer(rp, nowUptimeMs = tMs, dtSecRaw = dt60)

        // Simulate 10 consecutive rapid manual steps (155ms per tile) eastward
        for (step in 1..10) {
            // ~9 frames (~153ms) per tile
            repeat(9) {
                tMs += 17L
                state = interpolator.updatePlayer(rp, nowUptimeMs = tMs, dtSecRaw = dt60)
                assertFalse("Manual stepping at 155ms/tile must never hard-snap (step=$step)", state.hardSnappedThisFrame)
                assertTrue("Moving unit must report gliding & positive walkPhase", state.isGliding && state.walkPhase >= 0.02f)
                assertTrue("Moving east should maintain positive velocityX", state.velocityX >= 0f)
            }
            rp = rp.copy(
                x = 50 + step,
                targetX = 50 + step + 1,
                lastMoveMs = tMs,
                networkReceivedMs = tMs
            )
        }

        // Player stops at (60, 50)
        rp = rp.copy(
            x = 60,
            y = 50,
            targetX = 60,
            targetY = 50,
            actionState = PlayerActionState.IDLE,
            lastMoveMs = tMs,
            networkReceivedMs = tMs
        )
        repeat(45) {
            tMs += 17L
            state = interpolator.updatePlayer(rp, nowUptimeMs = tMs, dtSecRaw = dt60)
            assertFalse("Arrival settle must never hard-snap", state.hardSnappedThisFrame)
        }
        assertEquals(60f, state.renderX, 0.02f)
        assertEquals(50f, state.renderY, 0.02f)
        assertFalse("Unit must switch back to idle once arrived", state.isGliding)
        assertEquals("Walk phase must reset to 0 on idle arrival", 0f, state.walkPhase, 0.001f)
    }
}

