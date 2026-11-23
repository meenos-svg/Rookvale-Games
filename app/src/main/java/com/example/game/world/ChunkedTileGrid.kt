package com.example.game.world

import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.OVERLAND_REGION_SIZE
import com.example.game.model.TileType
import com.example.game.model.WORLD_CHUNK_SIZE
import kotlin.math.abs

/**
 * Compact 32x32 spatial tile chunk storing tile ordinals as a flat ByteArray (1 KB per chunk).
 * Enables memory-safe 400x400 (and larger) regional maps with fast chunk-level viewport culling.
 */
class TileChunk(
    val chunkX: Int,
    val chunkY: Int,
    val size: Int = WORLD_CHUNK_SIZE,
    defaultTile: TileType = TileType.GRASS
) {
    private val data: ByteArray = ByteArray(size * size) { defaultTile.ordinal.toByte() }

    fun getLocal(lx: Int, ly: Int): TileType {
        val ord = data[ly * size + lx].toInt() and 0xFF
        return TILE_VALUES.getOrElse(ord) { TileType.STONE_WALL }
    }

    fun setLocal(lx: Int, ly: Int, tile: TileType) {
        data[ly * size + lx] = tile.ordinal.toByte()
    }

    companion object {
        private val TILE_VALUES: Array<TileType> = TileType.entries.toTypedArray()
    }
}

data class ChunkBounds(
    val minChunkX: Int,
    val maxChunkX: Int,
    val minChunkY: Int,
    val maxChunkY: Int
)

/**
 * 2D chunked tile grid partitioned into 32x32 spatial chunks.
 * For a 400x400 regional map: 13x13 = 169 chunks (~173 KB total memory).
 */
class ChunkedTileGrid(
    val width: Int,
    val height: Int,
    val chunkSize: Int = WORLD_CHUNK_SIZE,
    defaultTile: TileType = TileType.GRASS
) {
    val chunksX: Int = (width + chunkSize - 1) / chunkSize
    val chunksY: Int = (height + chunkSize - 1) / chunkSize
    private val chunks: Array<TileChunk> = Array(chunksX * chunksY) { idx ->
        TileChunk(idx % chunksX, idx / chunksX, chunkSize, defaultTile)
    }

    val totalChunks: Int get() = chunks.size

    fun get(x: Int, y: Int): TileType {
        if (x !in 0 until width || y !in 0 until height) return TileType.STONE_WALL
        val cx = x / chunkSize
        val cy = y / chunkSize
        return chunks[cy * chunksX + cx].getLocal(x - cx * chunkSize, y - cy * chunkSize)
    }

    fun set(x: Int, y: Int, tile: TileType) {
        if (x !in 0 until width || y !in 0 until height) return
        val cx = x / chunkSize
        val cy = y / chunkSize
        chunks[cy * chunksX + cx].setLocal(x - cx * chunkSize, y - cy * chunkSize, tile)
    }

    fun chunkAt(chunkX: Int, chunkY: Int): TileChunk? {
        if (chunkX !in 0 until chunksX || chunkY !in 0 until chunksY) return null
        return chunks[chunkY * chunksX + chunkX]
    }

    fun visibleChunkBounds(minTx: Int, maxTx: Int, minTy: Int, maxTy: Int): ChunkBounds {
        val cMinX = (minTx.coerceIn(0, width - 1)) / chunkSize
        val cMaxX = (maxTx.coerceIn(0, width - 1)) / chunkSize
        val cMinY = (minTy.coerceIn(0, height - 1)) / chunkSize
        val cMaxY = (maxTy.coerceIn(0, height - 1)) / chunkSize
        return ChunkBounds(cMinX, cMaxX, cMinY, cMaxY)
    }

    fun fillRect(x1: Int, y1: Int, x2: Int, y2: Int, tile: TileType) {
        val sx = minOf(x1, x2).coerceIn(0, width - 1)
        val ex = maxOf(x1, x2).coerceIn(0, width - 1)
        val sy = minOf(y1, y2).coerceIn(0, height - 1)
        val ey = maxOf(y1, y2).coerceIn(0, height - 1)
        for (y in sy..ey) {
            for (x in sx..ex) {
                set(x, y, tile)
            }
        }
    }

    fun drawWallBorder(x1: Int, y1: Int, x2: Int, y2: Int, tile: TileType) {
        for (x in x1..x2) {
            set(x, y1, tile)
            set(x, y2, tile)
        }
        for (y in y1..y2) {
            set(x1, y, tile)
            set(x2, y, tile)
        }
    }

    fun fillEllipse(cx: Int, cy: Int, rx: Int, ry: Int, tile: TileType) {
        val rxF = rx.coerceAtLeast(1).toFloat()
        val ryF = ry.coerceAtLeast(1).toFloat()
        for (y in (cy - ry).coerceAtLeast(0)..(cy + ry).coerceAtMost(height - 1)) {
            for (x in (cx - rx).coerceAtLeast(0)..(cx + rx).coerceAtMost(width - 1)) {
                val dx = (x - cx) / rxF
                val dy = (y - cy) / ryF
                if (dx * dx + dy * dy <= 1.0f) {
                    set(x, y, tile)
                }
            }
        }
    }

    /**
     * Draws an organic road/trail between (x0, y0) and (x1, y1) with halfWidth radius.
     */
    fun drawRoadSegment(x0: Int, y0: Int, x1: Int, y1: Int, halfWidth: Int, tile: TileType) {
        val steps = maxOf(abs(x1 - x0), abs(y1 - y0)).coerceAtLeast(1)
        for (i in 0..steps) {
            val t = i.toFloat() / steps.toFloat()
            val px = (x0 + (x1 - x0) * t).toInt()
            val py = (y0 + (y1 - y0) * t).toInt()
            fillRect(px - halfWidth, py - halfWidth, px + halfWidth, py + halfWidth, tile)
        }
    }

    companion object {
        fun fromArray(tiles: Array<Array<TileType>>): ChunkedTileGrid {
            val h = tiles.size
            val w = tiles.firstOrNull()?.size ?: 0
            val grid = ChunkedTileGrid(w, h, WORLD_CHUNK_SIZE, TileType.STONE_WALL)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    grid.set(x, y, tiles[y][x])
                }
            }
            return grid
        }
    }
}

/**
 * Architectural footprint for pitched cutaway roofs that smoothly fade translucent when the player approaches or enters.
 */
data class BuildingFootprint(
    val id: String,
    val name: String,
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
    val roofColorArgb: Int,
    val roofHighlightArgb: Int,
    val roofTrimArgb: Int,
    val ridgeAlongX: Boolean = true
) {
    fun containsPoint(px: Float, py: Float, margin: Float = 0f): Boolean {
        return px >= (minX - margin) && px <= (maxX + margin) &&
            py >= (minY - margin) && py <= (maxY + margin)
    }

    fun distanceToPoint(px: Float, py: Float): Float {
        val dx = when {
            px < minX -> minX - px
            px > maxX -> px - maxX
            else -> 0f
        }
        val dy = when {
            py < minY -> minY - py
            py > maxY -> py - maxY
            else -> 0f
        }
        return kotlin.math.hypot(dx, dy)
    }
}

/**
 * Regional metadata registry enabling seamless expansion of neighboring 400x400 city regions
 * along the North, East, West highways and Southern Harbor sea port.
 */
data class WorldRegionMeta(
    val regionId: String,
    val displayName: String,
    val width: Int = OVERLAND_REGION_SIZE,
    val height: Int = OVERLAND_REGION_SIZE,
    val northHighwayTarget: String? = null,
    val eastHighwayTarget: String? = null,
    val westHighwayTarget: String? = null,
    val hasSouthSeaPort: Boolean = true
) {
    companion object {
        val ROOKVALE_VALLEY = WorldRegionMeta(
            regionId = DEFAULT_REGION_ID,
            displayName = "Rookvale City & Valley",
            width = OVERLAND_REGION_SIZE,
            height = OVERLAND_REGION_SIZE,
            northHighwayTarget = "highland_citadel_planned",
            eastHighwayTarget = "thornwood_reach_planned",
            westHighwayTarget = "sunshore_haven_planned",
            hasSouthSeaPort = true
        )
    }
}
