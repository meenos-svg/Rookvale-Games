package com.example.game.world

import com.example.game.model.TileType
import com.example.game.model.WORLD_OVERLAND_HEIGHT
import com.example.game.model.WORLD_OVERLAND_WIDTH
import kotlin.math.abs

/**
 * Result of strict coastline validation & shoreline snapping for a continental Harbor Port.
 * Guarantees that every port anchors on coastal land directly adjacent (1-2 tiles) to a verified
 * open Sea/Ocean body (>= 25 connected water tiles), projects timber piers straight into open water,
 * moors boats along the water flank, and connects its landward entrance to the nearest inland road.
 */
data class ValidatedCoastalPort(
    val port: HarborPort,
    val landAnchorX: Int,
    val landAnchorY: Int,
    val oceanSeedX: Int,
    val oceanSeedY: Int,
    val oceanConnectedWaterCount: Int,
    val waterDirX: Int,
    val waterDirY: Int,
    val pierTiles: Set<Long>,
    val boatTiles: Set<Long>,
    val roadTargetX: Int,
    val roadTargetY: Int
)

data class HarborPortCandidate(
    val id: String,
    val name: String,
    val regionId: String,
    val captainName: String,
    val candidateX: Int,
    val candidateY: Int,
    val roadTargetX: Int,
    val roadTargetY: Int,
    val description: String,
    val recommendedLevel: String,
    val dangerSeverity: Int,
    val preferredRegionBounds: IntRange? = null,
    val preferredRegionYBounds: IntRange? = null
)

/**
 * Deterministic coastline validator ensuring Harbor Ports NEVER spawn inland or on small ponds.
 *
 * Rules enforced:
 * 1. Open sea/ocean water (`WATER` or `DEEP_WATER`) must belong to an unobstructed water body of
 *    at least [MIN_OCEAN_WATER_TILES] (25) connected water tiles (verified via bounded BFS).
 * 2. Every port land anchor must be a coastal land tile within [MAX_SHORELINE_DISTANCE] (1-2 tiles)
 *    of verified open ocean water, with open water extending outward along a clear cardinal axis.
 * 3. Any inland candidate automatically spirals outward to snap onto the closest true ocean shoreline,
 *    building wooden piers over the adjacent water tiles and linking back to the nearest inland road.
 */
object CoastalPortValidator {
    const val MIN_OCEAN_WATER_TILES: Int = 25
    const val MAX_SHORELINE_DISTANCE: Int = 2
    const val PIER_LENGTH_OVER_WATER: Int = 6
    const val PIER_HALF_WIDTH: Int = 1

    private val cardinalDirs = arrayOf(
        1 to 0,
        -1 to 0,
        0 to 1,
        0 to -1
    )

    fun isSeaWaterTile(tile: TileType): Boolean =
        tile == TileType.WATER || tile == TileType.DEEP_WATER

    fun isCoastalLandTile(tile: TileType): Boolean =
        tile.walkable && !isSeaWaterTile(tile) && tile != TileType.LAVA

    /**
     * Bounded BFS flood-fill counting connected water tiles starting from `(startX, startY)`.
     * Stops early once [minRequired] connected water tiles are verified for O(25) speed.
     */
    fun countConnectedOceanWater(
        startX: Int,
        startY: Int,
        minRequired: Int = MIN_OCEAN_WATER_TILES,
        tileAt: (Int, Int) -> TileType
    ): Int {
        if (startX !in 0 until WORLD_OVERLAND_WIDTH || startY !in 0 until WORLD_OVERLAND_HEIGHT) return 0
        if (!isSeaWaterTile(tileAt(startX, startY))) return 0

        val visited = HashSet<Long>(minRequired * 2)
        val queue = ArrayDeque<Long>(minRequired * 2)
        val startKey = pack(startX, startY)
        visited.add(startKey)
        queue.addLast(startKey)

        while (queue.isNotEmpty() && visited.size < minRequired) {
            val cur = queue.removeFirst()
            val cx = unpackX(cur)
            val cy = unpackY(cur)
            for ((dx, dy) in cardinalDirs) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx !in 0 until WORLD_OVERLAND_WIDTH || ny !in 0 until WORLD_OVERLAND_HEIGHT) continue
                val key = pack(nx, ny)
                if (key in visited) continue
                if (isSeaWaterTile(tileAt(nx, ny))) {
                    visited.add(key)
                    if (visited.size >= minRequired) return visited.size
                    queue.addLast(key)
                }
            }
        }
        return visited.size
    }

    /**
     * Verifies whether `(x, y)` is a valid coastal port anchor on land within 1-2 tiles of a true
     * open ocean body (`>= 25` connected water tiles) with open water extending outward along a
     * cardinal pier direction.
     *
     * Returns `(waterDirX, waterDirY, oceanSeedX, oceanSeedY, connectedCount)` or `null` if invalid.
     */
    fun evaluateCoastalAnchor(
        x: Int,
        y: Int,
        tileAt: (Int, Int) -> TileType
    ): AnchorEvaluation? {
        if (x !in 8 until (WORLD_OVERLAND_WIDTH - 8) || y !in 8 until (WORLD_OVERLAND_HEIGHT - 8)) return null
        if (!isCoastalLandTile(tileAt(x, y))) return null

        var bestEval: AnchorEvaluation? = null
        var bestWaterReach = -1

        for ((dx, dy) in cardinalDirs) {
            // Must find open sea within 1..2 tiles along this cardinal direction
            var seedX = -1
            var seedY = -1
            for (step in 1..MAX_SHORELINE_DISTANCE) {
                val wx = x + dx * step
                val wy = y + dy * step
                if (isSeaWaterTile(tileAt(wx, wy))) {
                    seedX = wx
                    seedY = wy
                    break
                }
            }
            if (seedX < 0) continue

            // Ensure pier projection has clear water ahead (at least 4 water tiles out)
            var clearWaterSteps = 0
            for (step in 1..(PIER_LENGTH_OVER_WATER + 2)) {
                val wx = seedX + dx * (step - 1)
                val wy = seedY + dy * (step - 1)
                if (isSeaWaterTile(tileAt(wx, wy))) clearWaterSteps++ else break
            }
            if (clearWaterSteps < 4) continue

            // Ensure landward approach behind the anchor is walkable land (at least 2 tiles inland)
            val back1 = tileAt(x - dx, y - dy)
            val back2 = tileAt(x - dx * 2, y - dy * 2)
            if (!isCoastalLandTile(back1) || !isCoastalLandTile(back2)) continue

            val connected = countConnectedOceanWater(seedX, seedY, MIN_OCEAN_WATER_TILES, tileAt)
            if (connected >= MIN_OCEAN_WATER_TILES && clearWaterSteps > bestWaterReach) {
                bestWaterReach = clearWaterSteps
                bestEval = AnchorEvaluation(
                    waterDirX = dx,
                    waterDirY = dy,
                    oceanSeedX = seedX,
                    oceanSeedY = seedY,
                    connectedWaterTiles = connected
                )
            }
        }
        return bestEval
    }

    data class AnchorEvaluation(
        val waterDirX: Int,
        val waterDirY: Int,
        val oceanSeedX: Int,
        val oceanSeedY: Int,
        val connectedWaterTiles: Int
    )

    /**
     * Validates a candidate port location and, if inland or non-coastal, automatically searches
     * outward in concentric rings to snap onto the nearest true open Sea/Ocean shoreline tile.
     */
    fun validateAndSnapPort(
        candidate: HarborPortCandidate,
        maxSearchRadius: Int = 220,
        tileAt: (Int, Int) -> TileType
    ): ValidatedCoastalPort {
        val xBounds = candidate.preferredRegionBounds ?: (12..(WORLD_OVERLAND_WIDTH - 13))
        val yBounds = candidate.preferredRegionYBounds ?: (12..(WORLD_OVERLAND_HEIGHT - 13))

        var anchorX = candidate.candidateX.coerceIn(xBounds.first, xBounds.last)
        var anchorY = candidate.candidateY.coerceIn(yBounds.first, yBounds.last)
        var eval = evaluateCoastalAnchor(anchorX, anchorY, tileAt)

        if (eval == null) {
            // Spiral outward in concentric Chebyshev rings to snap onto the nearest valid ocean shoreline
            val stepSize = 2
            outer@ for (r in 1..maxSearchRadius step stepSize) {
                // Sample perimeter of square ring at radius r
                for (dx in -r..r step stepSize) {
                    val topY = anchorY - r
                    val botY = anchorY + r
                    val cx = anchorX + dx
                    if (cx in xBounds) {
                        if (topY in yBounds) {
                            val e = evaluateCoastalAnchor(cx, topY, tileAt)
                            if (e != null) {
                                anchorX = cx; anchorY = topY; eval = e; break@outer
                            }
                        }
                        if (botY in yBounds) {
                            val e = evaluateCoastalAnchor(cx, botY, tileAt)
                            if (e != null) {
                                anchorX = cx; anchorY = botY; eval = e; break@outer
                            }
                        }
                    }
                }
                for (dy in (-r + stepSize)..(r - stepSize) step stepSize) {
                    val leftX = anchorX - r
                    val rightX = anchorX + r
                    val cy = anchorY + dy
                    if (cy in yBounds) {
                        if (leftX in xBounds) {
                            val e = evaluateCoastalAnchor(leftX, cy, tileAt)
                            if (e != null) {
                                anchorX = leftX; anchorY = cy; eval = e; break@outer
                            }
                        }
                        if (rightX in xBounds) {
                            val e = evaluateCoastalAnchor(rightX, cy, tileAt)
                            if (e != null) {
                                anchorX = rightX; anchorY = cy; eval = e; break@outer
                            }
                        }
                    }
                }
            }
        }

        // Secondary global fallback if tight regional bounds had no shoreline (never returns landlocked!)
        if (eval == null) {
            outerGlobal@ for (r in 2..360 step 4) {
                for (dx in -r..r step 4) {
                    for (sy in intArrayOf(candidate.candidateY - r, candidate.candidateY + r)) {
                        val sx = candidate.candidateX + dx
                        val e = evaluateCoastalAnchor(sx, sy, tileAt)
                        if (e != null) {
                            anchorX = sx; anchorY = sy; eval = e; break@outerGlobal
                        }
                    }
                }
                for (dy in -r..r step 4) {
                    for (sx in intArrayOf(candidate.candidateX - r, candidate.candidateX + r)) {
                        val sy = candidate.candidateY + dy
                        val e = evaluateCoastalAnchor(sx, sy, tileAt)
                        if (e != null) {
                            anchorX = sx; anchorY = sy; eval = e; break@outerGlobal
                        }
                    }
                }
            }
        }

        val resolved = checkNotNull(eval) {
            "CoastalPortValidator could not locate an open-ocean shoreline near (${candidate.candidateX}, ${candidate.candidateY})"
        }

        val wdx = resolved.waterDirX
        val wdy = resolved.waterDirY
        val perpX = -wdy
        val perpY = wdx

        // Build wooden pier extending from 2 tiles inland on the coastal bank straight over open ocean water
        val pierTiles = HashSet<Long>(64)
        for (along in -2..PIER_LENGTH_OVER_WATER) {
            for (across in -PIER_HALF_WIDTH..PIER_HALF_WIDTH) {
                val px = anchorX + wdx * along + perpX * across
                val py = anchorY + wdy * along + perpY * across
                if (px in 0 until WORLD_OVERLAND_WIDTH && py in 0 until WORLD_OVERLAND_HEIGHT) {
                    pierTiles.add(pack(px, py))
                }
            }
        }

        // Moor boats beside the seaward tip of the pier over open water
        val boatTiles = HashSet<Long>(4)
        val tipAlong = PIER_LENGTH_OVER_WATER - 1
        for (side in intArrayOf(-(PIER_HALF_WIDTH + 1), PIER_HALF_WIDTH + 1)) {
            val bx = anchorX + wdx * tipAlong + perpX * side
            val by = anchorY + wdy * tipAlong + perpY * side
            if (isSeaWaterTile(tileAt(bx, by))) {
                boatTiles.add(pack(bx, by))
            }
        }
        // Fallback seaward end boat if side tiles happened to be shoreline
        if (boatTiles.isEmpty()) {
            val bx = anchorX + wdx * (PIER_LENGTH_OVER_WATER + 1)
            val by = anchorY + wdy * (PIER_LENGTH_OVER_WATER + 1)
            if (isSeaWaterTile(tileAt(bx, by))) {
                boatTiles.add(pack(bx, by))
            }
        }

        // Place Harbor Captain near the mid-pier over open water, and arrival landing 1 tile landward on the pier
        val dockX = anchorX + wdx * 3
        val dockY = anchorY + wdy * 3
        val arrivalX = anchorX + wdx * 1
        val arrivalY = anchorY + wdy * 1

        val resolvedRegionId = WorldRegionMeta.forGlobalTile(arrivalX, arrivalY).regionId

        val validatedPort = HarborPort(
            id = candidate.id,
            name = candidate.name,
            regionId = resolvedRegionId,
            captainName = candidate.captainName,
            dockX = dockX,
            dockY = dockY,
            arrivalX = arrivalX,
            arrivalY = arrivalY,
            description = candidate.description,
            recommendedLevel = candidate.recommendedLevel,
            dangerSeverity = candidate.dangerSeverity
        )

        return ValidatedCoastalPort(
            port = validatedPort,
            landAnchorX = anchorX,
            landAnchorY = anchorY,
            oceanSeedX = resolved.oceanSeedX,
            oceanSeedY = resolved.oceanSeedY,
            oceanConnectedWaterCount = resolved.connectedWaterTiles,
            waterDirX = wdx,
            waterDirY = wdy,
            pierTiles = pierTiles,
            boatTiles = boatTiles,
            roadTargetX = candidate.roadTargetX,
            roadTargetY = candidate.roadTargetY
        )
    }

    /**
     * Public verification helper used by world invariants and tests to confirm any port's anchor
     * lies within 1-2 tiles of a true >= 25-tile open Sea/Ocean water body.
     */
    fun verifyPortOnTrueOceanCoast(
        validated: ValidatedCoastalPort,
        rawTileAt: (Int, Int) -> TileType
    ): Boolean {
        val distToWater = maxOf(
            abs(validated.landAnchorX - validated.oceanSeedX),
            abs(validated.landAnchorY - validated.oceanSeedY)
        )
        if (distToWater !in 1..MAX_SHORELINE_DISTANCE) return false
        if (!isCoastalLandTile(rawTileAt(validated.landAnchorX, validated.landAnchorY))) return false
        val waterCount = countConnectedOceanWater(
            validated.oceanSeedX,
            validated.oceanSeedY,
            MIN_OCEAN_WATER_TILES,
            rawTileAt
        )
        return waterCount >= MIN_OCEAN_WATER_TILES
    }

    fun pack(x: Int, y: Int): Long = (y.toLong() shl 32) or (x.toLong() and 0xFFFFFFFFL)
    private fun unpackX(key: Long): Int = (key and 0xFFFFFFFFL).toInt()
    private fun unpackY(key: Long): Int = (key ushr 32).toInt()
}
