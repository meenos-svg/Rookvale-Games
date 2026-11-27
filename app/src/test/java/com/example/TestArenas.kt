package com.example

import com.example.game.model.TileType
import com.example.game.world.WorldMaps

/**
 * Finds open ground on a floor so combat/AI tests don't depend on hand-picked coordinates
 * that break whenever a map layout changes.
 */
object TestArenas {
    private val notPlainGround = setOf(
        TileType.STAIRS_UP, TileType.STAIRS_DOWN, TileType.ROPE_HOLE_DOWN,
        TileType.ROPE_SPOT_UP, TileType.IRON_BAR_GATE
    )

    /**
     * Centre of the first [width]x[height] block of plain walkable, non-protection-zone tiles on
     * [floorId] (scanning top-left to bottom-right, so the result is deterministic).
     */
    fun openCenter(floorId: Int, width: Int = 9, height: Int = 7): Pair<Int, Int> {
        val floor = WorldMaps.getFloor(floorId)
        val npcs = WorldMaps.npcs.filter { it.floor == floorId }.map { it.x to it.y }.toSet()
        fun ok(x: Int, y: Int): Boolean {
            val t = floor.tileAt(x, y)
            return t.walkable && t !in notPlainGround && !floor.isProtectionZone(x, y) && (x to y) !in npcs
        }
        for (y in 1 until floor.height - height) {
            for (x in 1 until floor.width - width) {
                var clear = true
                loop@ for (dy in 0 until height) for (dx in 0 until width) {
                    if (!ok(x + dx, y + dy)) { clear = false; break@loop }
                }
                if (clear) return (x + width / 2) to (y + height / 2)
            }
        }
        error("No open ${width}x$height area on floor $floorId")
    }
}
