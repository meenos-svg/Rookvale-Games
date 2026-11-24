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
    val hardSnappedThisFrame: Boolean = false
)

/**
 * Client-side path prediction & 60 FPS smooth interpolation engine for remote players:
 * - Advances predicted goal coordinates along `(networkX, networkY) -> (targetX, targetY)`
 *   at normal walking speed between 120ms Realtime Broadcast packets.
 * - Smoothly interpolates (`lerp`) `renderX/Y` every frame so remote heroes glide continuously
 *   across tiles with uninterrupted walk animations and zero rubber-banding.
 * - Only hard-snaps `renderX/Y` when the floor changes or distance to `networkX/Y` exceeds 4 tiles
 *   (e.g., rope hole transition or respawn).
 */
class RemotePlayerInterpolator {

    companion object {
        /** Hard-snap only when distance between renderX/Y and networkX/Y exceeds 4 tiles. */
        const val HARD_SNAP_DISTANCE_TILES: Float = 4.0f

        /** Normal Rookvale walking speed (~1 tile per 210ms = 4.76 tiles/sec). */
        const val WALK_SPEED_TILES_PER_SEC: Float = 1000f / 210f

        /** Maximum dead-reckoning prediction lead beyond last received networkX/Y. */
        const val MAX_PREDICT_LEAD_TILES: Float = 1.15f

        /** Stop dead-reckoning if no fresh packet arrives within 280ms so peers never overshoot walls. */
        const val MAX_PREDICT_WINDOW_SEC: Float = 0.28f

        /** Frame-rate independent exponential smoothing factor (at 60 FPS -> ~0.20 per frame). */
        const val SMOOTH_LERP_RATE: Float = 13.5f

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
                hardSnappedThisFrame = true
            )
            states[rp.playerId] = initial
            return initial
        }

        val networkChanged = prev.networkX != netX || prev.networkY != netY ||
            prev.targetX != tgtX || prev.targetY != tgtY
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
                hardSnappedThisFrame = true
            )
            states[rp.playerId] = snapped
            return snapped
        }

        // 1. Path Prediction: advance gently along (networkX, networkY) -> (targetX, targetY)
        // at normal walking speed between packets, bounded by MAX_PREDICT_LEAD_TILES & MAX_PREDICT_WINDOW_SEC.
        val tDx = tgtX - netX
        val tDy = tgtY - netY
        val tLen = hypot(tDx, tDy)
        val elapsedSincePacketSec = ((nowUptimeMs - effectivePacketMs).coerceAtLeast(0L)) / 1000f
        val hasActiveTarget = tLen > 0.05f &&
            (rp.actionState == PlayerActionState.WALKING || elapsedSincePacketSec <= MAX_PREDICT_WINDOW_SEC)

        val goalX: Float
        val goalY: Float
        if (hasActiveTarget && elapsedSincePacketSec <= MAX_PREDICT_WINDOW_SEC) {
            val leadDist = min(min(tLen, MAX_PREDICT_LEAD_TILES), WALK_SPEED_TILES_PER_SEC * elapsedSincePacketSec)
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
            val lerpAlpha = (1f - exp(-SMOOTH_LERP_RATE * dtSec)).coerceIn(0.06f, 1f)
            val expStep = distToGoal * lerpAlpha
            val minWalkStep = min(distToGoal, WALK_SPEED_TILES_PER_SEC * 0.80f * dtSec)
            val maxSpeedMult = if (distToNet > 1.5f) 1.50f else 1.20f
            val maxWalkStep = WALK_SPEED_TILES_PER_SEC * maxSpeedMult * dtSec
            val step = expStep.coerceAtLeast(minWalkStep).coerceAtMost(maxWalkStep).coerceAtMost(distToGoal)
            nextRenderX = prev.renderX + (dx / distToGoal) * step
            nextRenderY = prev.renderY + (dy / distToGoal) * step
        }

        val movedDx = nextRenderX - prev.renderX
        val movedDy = nextRenderY - prev.renderY
        val movedDist = hypot(movedDx, movedDy)
        val remainingToGoal = hypot(goalX - nextRenderX, goalY - nextRenderY)
        val gliding = movedDist > 0.003f || remainingToGoal > 0.025f

        // 3. Continuous Walk Animation: keep stride phase cycling cleanly while gliding,
        // avoiding zero-crossing blips that would briefly snap legs to idle mid-step.
        val nextWalkPhase = if (gliding) {
            val rawPhase = (prev.walkPhase + dtSec * WALK_CYCLES_PER_SEC) % 1.0f
            if (rawPhase < 0.02f) 0.02f else rawPhase
        } else {
            0f
        }

        val velX = if (movedDist > 0.003f) movedDx / dtSec else if (hasActiveTarget) tDx else 0f
        val velY = if (movedDist > 0.003f) movedDy / dtSec else if (hasActiveTarget) tDy else 0f

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
            hardSnappedThisFrame = false
        )
        states[rp.playerId] = updated
        return updated
    }
}
