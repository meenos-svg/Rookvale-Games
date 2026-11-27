package com.example.game.engine

import com.example.game.model.MonsterEntity
import kotlin.math.abs
import kotlin.math.max

/**
 * Forgiving tap-to-target for monsters.
 *
 * Monsters are drawn as tall figures standing on their tile and glide between tiles while moving,
 * so requiring a tap on the exact floor tile made them hard to hit. Instead each monster gets a
 * screen-space hit box covering its whole drawn body (feet to head, at least a finger wide) at its
 * current on-screen position, and the closest body to the tap wins.
 */
object MonsterTapTargeting {
    /** How long a monster's glide between tiles lasts (matches the renderer). */
    const val MONSTER_GLIDE_MS: Float = 220f

    /** Where a monster is currently drawn, in fractional tile coordinates. */
    fun visualTile(m: MonsterEntity, nowMs: Long): Pair<Float, Float> {
        val frac = ((nowMs - m.lastMoveTimeMs).toFloat() / MONSTER_GLIDE_MS).coerceIn(0f, 1f)
        return (m.lastMoveFromX + (m.x - m.lastMoveFromX) * frac) to (m.lastMoveFromY + (m.y - m.lastMoveFromY) * frac)
    }

    /** One on-screen monster: its feet position and drawn body height, in screen pixels. */
    class Candidate(val monsterId: Int, val feetX: Float, val feetY: Float, val bodyHeightPx: Float)

    /**
     * Returns the id of the monster whose body was tapped at ([tapX], [tapY]), or null.
     *
     * @param tileW on-screen width of one tile
     * @param tileH on-screen height of one tile
     * @param minHalfWidthPx smallest half-width of a hit box (about half a fingertip)
     */
    fun pick(
        candidates: List<Candidate>,
        tapX: Float,
        tapY: Float,
        tileW: Float,
        tileH: Float,
        minHalfWidthPx: Float
    ): Int? {
        var bestId: Int? = null
        var bestScore = Float.MAX_VALUE
        for (c in candidates) {
            val halfW = max(max(tileW * 0.42f, c.bodyHeightPx * 0.38f), minHalfWidthPx)
            val top = c.feetY - c.bodyHeightPx * 1.08f          // a little above the head
            val bottom = c.feetY + max(tileH * 0.55f, minHalfWidthPx * 0.6f) // the tile under its feet
            if (abs(tapX - c.feetX) > halfW || tapY < top || tapY > bottom) continue
            // Closest to the body's centre wins when hit boxes overlap
            val centerY = c.feetY - c.bodyHeightPx * 0.45f
            val halfH = (bottom - top) / 2f
            val nx = (tapX - c.feetX) / halfW
            val ny = (tapY - centerY) / halfH
            val score = nx * nx + ny * ny
            if (score < bestScore) {
                bestScore = score
                bestId = c.monsterId
            }
        }
        return bestId
    }
}
