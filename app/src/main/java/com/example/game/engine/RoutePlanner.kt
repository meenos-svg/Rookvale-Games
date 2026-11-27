package com.example.game.engine

import com.example.game.world.WorldFloor
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Long-distance route finder for map "walk here" taps.
 *
 * Plans the whole route tile by tile with A* over a walkability snapshot of a box around the
 * start and goal, widening the box if the first attempt can't connect them. This replaces the old
 * coarse waypoint chain, which aimed at straight-line points between highway nodes and could walk
 * the player backwards, into lakes or into dead ends.
 */
object RoutePlanner {
    /** Box paddings tried in order; later attempts allow bigger detours around lakes and ridges. */
    private val PADDINGS = intArrayOf(48, 140, 320)
    private const val MAX_BOX_TILES = 1_100_000
    private const val MAX_EXPANSIONS = 600_000

    /**
     * Returns the tile path from (sx, sy) to (gx, gy) excluding the start tile, or null when no
     * route exists within the search area. [blocked] marks extra impassable tiles (NPCs, monsters).
     */
    fun plan(
        floor: WorldFloor,
        sx: Int,
        sy: Int,
        gx: Int,
        gy: Int,
        blocked: (Int, Int) -> Boolean = { _, _ -> false }
    ): List<Pair<Int, Int>>? {
        if (sx == gx && sy == gy) return emptyList()
        if (gx !in 0 until floor.width || gy !in 0 until floor.height) return null
        if (!floor.tileAt(gx, gy).walkable) return null
        for (pad in PADDINGS) {
            val minX = max(0, min(sx, gx) - pad)
            val minY = max(0, min(sy, gy) - pad)
            val maxX = min(floor.width - 1, max(sx, gx) + pad)
            val maxY = min(floor.height - 1, max(sy, gy) + pad)
            val w = maxX - minX + 1
            val h = maxY - minY + 1
            if (w.toLong() * h > MAX_BOX_TILES) {
                // Too big to snapshot on a phone: fall back to the largest box that fits
                if (pad == PADDINGS.first()) return null else break
            }
            val result = search(floor, minX, minY, w, h, sx, sy, gx, gy, blocked)
            if (result != null) return result
            // Covered the whole floor already: nothing more to widen
            if (minX == 0 && minY == 0 && maxX == floor.width - 1 && maxY == floor.height - 1) break
        }
        return null
    }

    private fun search(
        floor: WorldFloor,
        ox: Int,
        oy: Int,
        w: Int,
        h: Int,
        sx: Int,
        sy: Int,
        gx: Int,
        gy: Int,
        blocked: (Int, Int) -> Boolean
    ): List<Pair<Int, Int>>? {
        val n = w * h
        val open = BooleanArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            val wx = ox + x
            val wy = oy + y
            open[y * w + x] = floor.tileAt(wx, wy).walkable && !blocked(wx, wy)
        }
        val start = (sy - oy) * w + (sx - ox)
        val goal = (gy - oy) * w + (gx - ox)
        open[start] = true
        open[goal] = true

        val g = IntArray(n) { Int.MAX_VALUE }
        val parent = IntArray(n) { -1 }
        val closed = BooleanArray(n)
        val queue = PriorityQueue<Long>(1024)
        val goalLx = gx - ox
        val goalLy = gy - oy
        fun h(idx: Int): Int = abs(goalLx - idx % w) + abs(goalLy - idx / w)
        // f is scaled so ties prefer nodes closer to the goal (straighter, faster searches)
        fun push(idx: Int, gScore: Int) {
            val f = gScore * 4 + h(idx) * 5
            queue.add((f.toLong() shl 32) or idx.toLong())
        }
        g[start] = 0
        push(start, 0)
        var expansions = 0
        while (queue.isNotEmpty() && expansions < MAX_EXPANSIONS) {
            val cur = (queue.poll()!! and 0xFFFFFFFFL).toInt()
            if (closed[cur]) continue
            closed[cur] = true
            expansions++
            if (cur == goal) break
            val cx = cur % w
            val cy = cur / w
            val ng = g[cur] + 1
            if (cx > 0) relax(cur - 1, ng, cur, open, closed, g, parent, ::push)
            if (cx < w - 1) relax(cur + 1, ng, cur, open, closed, g, parent, ::push)
            if (cy > 0) relax(cur - w, ng, cur, open, closed, g, parent, ::push)
            if (cy < h - 1) relax(cur + w, ng, cur, open, closed, g, parent, ::push)
        }
        if (!closed[goal]) return null
        val path = ArrayList<Pair<Int, Int>>(g[goal] + 1)
        var c = goal
        while (c != start && c >= 0) {
            path.add((ox + c % w) to (oy + c / w))
            c = parent[c]
        }
        path.reverse()
        return path
    }

    private inline fun relax(
        next: Int,
        ng: Int,
        cur: Int,
        open: BooleanArray,
        closed: BooleanArray,
        g: IntArray,
        parent: IntArray,
        push: (Int, Int) -> Unit
    ) {
        if (!open[next] || closed[next] || ng >= g[next]) return
        g[next] = ng
        parent[next] = cur
        push(next, ng)
    }
}
