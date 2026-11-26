package com.example.game.multiplayer

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min

/**
 * Per-remote-player smoothed visual transform driven at 60 FPS on the client.
 * Separates authoritative network coordinates ([networkX], [networkY]) and predicted
 * destination ([targetX], [targetY]) from rendered tile coordinates ([renderX], [renderY]).
 */
data class RemotePlayerRenderState(
    val playerId: String,
    val floor: Int,
    val networkX: Float,
    val networkY: Float,
    val targetX: Float,
    val targetY: Float,
    val renderX: Float,
    val renderY: Float,
    val lastPacketMs: Long,
    val walkPhase: Float,
    val isGliding: Boolean,
    val velocityX: Float,
    val velocityY: Float,
    val hardSnappedThisFrame: Boolean = false,
    val tileOriginMs: Long = lastPacketMs
)

/**
 * Client-side path prediction & 60 FPS smooth interpolation engine for remote players:
 * - Advances predicted goal coordinates along `(networkX, networkY) -> (targetX, targetY)`
 *   at the peer's broadcasted movement speed between ~100ms Realtime Broadcast packets.
 * - Keeps a monotonic `tileOriginMs` anchor per tile so duplicate-tile keepalive packets
 *   never reset prediction progress or yank remote heroes backwards (zero rubber-banding).
 * - Smoothly interpolates (`lerp`) `renderX/Y` every frame so remote heroes glide continuously
 *   across tiles with uninterrupted walk animations and smooth directional facing.
 * - Only hard-snaps `renderX/Y` when the floor changes or distance to `networkX/Y` exceeds 4 tiles
 *   (e.g., rope hole transition or respawn).
 */
class RemotePlayerInterpolator {

    companion object {
        /** Hard-snap only when distance between renderX/Y and networkX/Y exceeds 4 tiles. */
        const val HARD_SNAP_DISTANCE_TILES: Float = 4.0f

        /** Normal Rookvale auto-walking speed (~1 tile per 210ms = 4.76 tiles/sec). */
        const val WALK_SPEED_TILES_PER_SEC: Float = 1000f / 210f

        /** Manual directional step speed (~1 tile per 155ms = 6.45 tiles/sec). */
        const val MANUAL_STEP_SPEED_TILES_PER_SEC: Float = 1000f / 155f

        /** Maximum dead-reckoning prediction lead beyond last received networkX/Y. */
        const val MAX_PREDICT_LEAD_TILES: Float = 1.55f

        /** Stop dead-reckoning if no fresh packet arrives within 340ms so peers never overshoot walls. */
        const val MAX_PREDICT_WINDOW_SEC: Float = 0.34f

        /** Frame-rate independent exponential smoothing factor (at 60 FPS -> ~0.31 per frame for crisp tracking). */
        const val SMOOTH_LERP_RATE: Float = 22.0f

        /** Continuous walk stride cycles per second (~1 full left-right stride per ~385ms). */
        const val WALK_CYCLES_PER_SEC: Float = 2.6f
    }

    private val states = HashMap<String, RemotePlayerRenderState>(16)

    fun stateFor(playerId: String): RemotePlayerRenderState? = states[playerId]

    fun clear() {
        states.clear()
    }

    fun pruneInactive(activeIds: Set<String>) {
        if (states.size <= activeIds.size) return
        val iter = states.keys.iterator()
        while (iter.hasNext()) {
            if (iter.next() !in activeIds) iter.remove()
        }
    }

    /**
     * Advances a single remote player's smoothed `renderX/Y` and continuous `walkPhase`
     * for the current 60 FPS frame.
     */
    fun updatePlayer(
        rp: RemotePlayerState,
        nowUptimeMs: Long,
        dtSecRaw: Float
    ): RemotePlayerRenderState {
        val dtSec = dtSecRaw.coerceIn(0.001f, 0.12f)
        val netX = rp.x.toFloat()
        val netY = rp.y.toFloat()
        val tgtX = rp.targetX.toFloat()
        val tgtY = rp.targetY.toFloat()
        val walkSpeed = rp.moveSpeed.coerceIn(2.5f, 8.5f)
        val packetMs = if (rp.networkReceivedMs > 0L) rp.networkReceivedMs else nowUptimeMs

        val prev = states[rp.playerId]
        if (prev == null || prev.floor != rp.floor) {
            val initial = RemotePlayerRenderState(
                playerId = rp.playerId,
                floor = rp.floor,
                networkX = netX,
                networkY = netY,
                targetX = tgtX,
                targetY = tgtY,
                renderX = netX,
                renderY = netY,
                lastPacketMs = packetMs,
                walkPhase = 0f,
                isGliding = false,
                velocityX = 0f,
                velocityY = 0f,
                hardSnappedThisFrame = true,
                tileOriginMs = packetMs
            )
            states[rp.playerId] = initial
            return initial
        }

        val tileChanged = prev.networkX != netX || prev.networkY != netY
        val targetChanged = prev.targetX != tgtX || prev.targetY != tgtY
        val networkChanged = tileChanged || targetChanged
        val effectivePacketMs = when {
            rp.networkReceivedMs > prev.lastPacketMs -> rp.networkReceivedMs
            networkChanged -> nowUptimeMs
            else -> prev.lastPacketMs
        }

        val distToNet = hypot(netX - prev.renderX, netY - prev.renderY)
        if (distToNet > HARD_SNAP_DISTANCE_TILES) {
            val snapped = RemotePlayerRenderState(
                playerId = rp.playerId,
                floor = rp.floor,
                networkX = netX,
                networkY = netY,
                targetX = tgtX,
                targetY = tgtY,
                renderX = netX,
                renderY = netY,
                lastPacketMs = effectivePacketMs,
                walkPhase = 0f,
                isGliding = false,
                velocityX = 0f,
                velocityY = 0f,
                hardSnappedThisFrame = true,
                tileOriginMs = effectivePacketMs
            )
            states[rp.playerId] = snapped
            return snapped
        }

        val tDx = tgtX - netX
        val tDy = tgtY - netY
        val tLen = hypot(tDx, tDy)
        val prevHadTarget = hypot(prev.targetX - prev.networkX, prev.targetY - prev.networkY) > 0.05f

        // Anchor dead-reckoning time to when the peer arrived on (netX, netY) (or started walking from rest).
        // Duplicate-tile keepalive packets (~100ms) keep tileOriginMs intact so prediction never yanks backwards!
        val effectiveTileOriginMs = when {
            tileChanged -> effectivePacketMs
            !prevHadTarget && tLen > 0.05f -> effectivePacketMs
            else -> prev.tileOriginMs
        }

        // 1. Dead-Reckoning Path Prediction: advance smoothly along (networkX, networkY) -> (targetX, targetY)
        // at the peer's actual movement speed between WebSocket packets.
        val elapsedSincePacketSec = ((nowUptimeMs - effectivePacketMs).coerceAtLeast(0L)) / 1000f
        val elapsedSinceTileSec = ((nowUptimeMs - effectiveTileOriginMs).coerceAtLeast(0L)) / 1000f
        val hasActiveTarget = tLen > 0.05f &&
            (rp.actionState == PlayerActionState.WALKING || elapsedSincePacketSec <= MAX_PREDICT_WINDOW_SEC)

        val goalX: Float
        val goalY: Float
        if (hasActiveTarget && elapsedSincePacketSec <= MAX_PREDICT_WINDOW_SEC) {
            val leadDist = min(min(tLen, MAX_PREDICT_LEAD_TILES), walkSpeed * elapsedSinceTileSec)
            goalX = netX + (tDx / tLen) * leadDist
            goalY = netY + (tDy / tLen) * leadDist
        } else {
            goalX = netX
            goalY = netY
        }

        // 2. Smooth 60 FPS Client-Side Interpolation (lerp) toward (goalX, goalY)
        val dx = goalX - prev.renderX
        val dy = goalY - prev.renderY
        val distToGoal = hypot(dx, dy)

        val nextRenderX: Float
        val nextRenderY: Float
        if (distToGoal <= 0.008f) {
            nextRenderX = goalX
            nextRenderY = goalY
        } else {
            val lerpAlpha = (1f - exp(-SMOOTH_LERP_RATE * dtSec)).coerceIn(0.08f, 1f)
            val expStep = distToGoal * lerpAlpha
            val minWalkStep = min(distToGoal, walkSpeed * 0.92f * dtSec)
            val maxSpeedMult = if (distToNet > 1.2f) 1.85f else 1.40f
            val maxWalkStep = walkSpeed * maxSpeedMult * dtSec
            val step = expStep.coerceAtLeast(minWalkStep).coerceAtMost(maxWalkStep).coerceAtMost(distToGoal)
            nextRenderX = prev.renderX + (dx / distToGoal) * step
            nextRenderY = prev.renderY + (dy / distToGoal) * step
        }

        val movedDx = nextRenderX - prev.renderX
        val movedDy = nextRenderY - prev.renderY
        val movedDist = hypot(movedDx, movedDy)
        val remainingToGoal = hypot(goalX - nextRenderX, goalY - nextRenderY)
        val gliding = movedDist > 0.002f || remainingToGoal > 0.01f

        // 3. Continuous Walk Animation: keep stride phase cycling cleanly while gliding,
        // avoiding zero-crossing blips that would briefly snap legs to idle mid-step.
        val nextWalkPhase = if (gliding) {
            val speedRatio = (walkSpeed / WALK_SPEED_TILES_PER_SEC).coerceIn(0.85f, 1.45f)
            val rawPhase = (prev.walkPhase + dtSec * WALK_CYCLES_PER_SEC * speedRatio) % 1.0f
            if (rawPhase < 0.02f) 0.02f else rawPhase
        } else {
            0f
        }

        val velX = if (movedDist > 0.002f) movedDx / dtSec else if (hasActiveTarget) tDx else 0f
        val velY = if (movedDist > 0.002f) movedDy / dtSec else if (hasActiveTarget) tDy else 0f

        val updated = RemotePlayerRenderState(
            playerId = rp.playerId,
            floor = rp.floor,
            networkX = netX,
            networkY = netY,
            targetX = tgtX,
            targetY = tgtY,
            renderX = nextRenderX,
            renderY = nextRenderY,
            lastPacketMs = effectivePacketMs,
            walkPhase = nextWalkPhase,
            isGliding = gliding,
            velocityX = velX,
            velocityY = velY,
            hardSnappedThisFrame = false,
            tileOriginMs = effectiveTileOriginMs
        )
        states[rp.playerId] = updated
        return updated
    }
}
