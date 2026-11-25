package com.example.game.world

import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.OVERLAND_REGION_SIZE
import com.example.game.model.TileType
import com.example.game.model.WORLD_CHUNK_SIZE
import com.example.game.model.WORLD_REGION_COLS
import com.example.game.model.WORLD_REGION_ROWS
import kotlin.math.abs

/**
 * Compact 32x32 spatial tile chunk storing tile ordinals as a flat ByteArray (1 KB per chunk).
 * Enables memory-safe 1600x1200 multi-region streaming worlds with fast chunk-level viewport culling.
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
 * Supports two modes:
 * - Dense mode (`chunkGenerator == null`): Pre-allocates all chunks (ideal for 48x48 dungeon floors).
 * - Streaming LRU mode (`chunkGenerator != null`): Generates 32x32 chunks on demand around the player
 *   and bounds RAM usage via a thread-safe LRU cache (~180 chunks ≈ 185 KB for a 1600x1200 world).
 */
class ChunkedTileGrid(
    val width: Int,
    val height: Int,
    val chunkSize: Int = WORLD_CHUNK_SIZE,
    defaultTile: TileType = TileType.GRASS,
    private val chunkGenerator: ((chunkX: Int, chunkY: Int) -> TileChunk)? = null,
    private val maxCachedChunks: Int = 180
) {
    val chunksX: Int = (width + chunkSize - 1) / chunkSize
    val chunksY: Int = (height + chunkSize - 1) / chunkSize
    val isStreaming: Boolean get() = chunkGenerator != null

    private val denseChunks: Array<TileChunk>? = if (chunkGenerator == null) {
        Array(chunksX * chunksY) { idx ->
            TileChunk(idx % chunksX, idx / chunksX, chunkSize, defaultTile)
        }
    } else null

    private val cacheLock = Any()
    private val lruChunks: LinkedHashMap<Int, TileChunk>? = if (chunkGenerator != null) {
        object : LinkedHashMap<Int, TileChunk>(maxCachedChunks + 16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, TileChunk>?): Boolean {
                return size > maxCachedChunks
            }
        }
    } else null

    // Sparse persistent overrides for dynamic edits on streamed chunks
    private val tileOverrides: HashMap<Long, TileType> = HashMap()

    val totalChunks: Int get() = chunksX * chunksY

    val cachedChunkCount: Int
        get() = denseChunks?.size ?: synchronized(cacheLock) { lruChunks?.size ?: 0 }

    private fun packCoord(x: Int, y: Int): Long = (y.toLong() shl 32) or (x.toLong() and 0xFFFFFFFFL)

    private fun resolveChunk(cx: Int, cy: Int): TileChunk {
        val dense = denseChunks
        if (dense != null) {
            return dense[cy * chunksX + cx]
        }
        val key = cy * chunksX + cx
        val cached = synchronized(cacheLock) { lruChunks?.get(key) }
        if (cached != null) return cached
        val generated = chunkGenerator!!.invoke(cx, cy)
        synchronized(cacheLock) {
            lruChunks?.put(key, generated)
        }
        return generated
    }

    fun get(x: Int, y: Int): TileType {
        if (x !in 0 until width || y !in 0 until height) return TileType.STONE_WALL
        if (isStreaming && tileOverrides.isNotEmpty()) {
            val override = synchronized(cacheLock) { tileOverrides[packCoord(x, y)] }
            if (override != null) return override
        }
        val cx = x / chunkSize
        val cy = y / chunkSize
        return resolveChunk(cx, cy).getLocal(x - cx * chunkSize, y - cy * chunkSize)
    }

    fun set(x: Int, y: Int, tile: TileType) {
        if (x !in 0 until width || y !in 0 until height) return
        val cx = x / chunkSize
        val cy = y / chunkSize
        val dense = denseChunks
        if (dense != null) {
            dense[cy * chunksX + cx].setLocal(x - cx * chunkSize, y - cy * chunkSize, tile)
            return
        }
        synchronized(cacheLock) {
            tileOverrides[packCoord(x, y)] = tile
            lruChunks?.get(cy * chunksX + cx)?.setLocal(x - cx * chunkSize, y - cy * chunkSize, tile)
        }
    }

    fun chunkAt(chunkX: Int, chunkY: Int): TileChunk? {
        if (chunkX !in 0 until chunksX || chunkY !in 0 until chunksY) return null
        return resolveChunk(chunkX, chunkY)
    }

    fun prewarmAround(centerTx: Int, centerTy: Int, radiusChunks: Int = 2) {
        if (!isStreaming) return
        val centerCx = (centerTx.coerceIn(0, width - 1)) / chunkSize
        val centerCy = (centerTy.coerceIn(0, height - 1)) / chunkSize
        for (cy in (centerCy - radiusChunks).coerceAtLeast(0)..(centerCy + radiusChunks).coerceAtMost(chunksY - 1)) {
            for (cx in (centerCx - radiusChunks).coerceAtLeast(0)..(centerCx + radiusChunks).coerceAtMost(chunksX - 1)) {
                resolveChunk(cx, cy)
            }
        }
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
        val ey = maxOf(y1, y2).coerceAtMost(height - 1)
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
 * Regional metadata registry for Rookvale's 4x3 (1600x1200) multi-region overland world map.
 * Each region occupies a 400x400 tile sector at (gridCol * 400, gridRow * 400).
 */
data class WorldRegionMeta(
    val regionId: String,
    val displayName: String,
    val gridCol: Int,
    val gridRow: Int,
    val biomeSummary: String,
    val dangerLevel: String,
    val recommendedLevel: String,
    val dangerSeverity: Int,
    val harborPortId: String? = null,
    val width: Int = OVERLAND_REGION_SIZE,
    val height: Int = OVERLAND_REGION_SIZE
) {
    val originX: Int get() = gridCol * OVERLAND_REGION_SIZE
    val originY: Int get() = gridRow * OVERLAND_REGION_SIZE
    val centerX: Int get() = originX + width / 2
    val centerY: Int get() = originY + height / 2

    companion object {
        val CALDERA_PEAKS = WorldRegionMeta(
            regionId = "caldera_peaks",
            displayName = "Ashen Caldera Peaks",
            gridCol = 0,
            gridRow = 0,
            biomeSummary = "Volcanic caldera, basalt ridges & northern snowfields",
            dangerLevel = "High",
            recommendedLevel = "Lvl 14-22",
            dangerSeverity = 3
        )
        val FROSTBAY_STRAITS = WorldRegionMeta(
            regionId = "frostbay_straits",
            displayName = "Frostbay Straits",
            gridCol = 1,
            gridRow = 0,
            biomeSummary = "Frozen northern straits & the Frostbay Sea-Bridge",
            dangerLevel = "Moderate",
            recommendedLevel = "Lvl 10-16",
            dangerSeverity = 2
        )
        val BLIGHTWOOD_REACH = WorldRegionMeta(
            regionId = "blightwood_reach",
            displayName = "Blightwood & Duskport",
            gridCol = 2,
            gridRow = 0,
            biomeSummary = "Corrupted twilight woods & Duskport coastal quay",
            dangerLevel = "Moderate",
            recommendedLevel = "Lvl 8-14",
            dangerSeverity = 2,
            harborPortId = "duskport_quay"
        )
        val FROSTCREST_HIGHLANDS = WorldRegionMeta(
            regionId = "frostcrest_highlands",
            displayName = "Frostcrest Highlands",
            gridCol = 3,
            gridRow = 0,
            biomeSummary = "Snow-capped alpine peaks, pine timber & Grimmaw ridge",
            dangerLevel = "High",
            recommendedLevel = "Lvl 12-20",
            dangerSeverity = 3
        )
        val WRITHING_MARSHES = WorldRegionMeta(
            regionId = "writhing_marshes",
            displayName = "The Writhing Marshes",
            gridCol = 0,
            gridRow = 1,
            biomeSummary = "Misty boglands, bog-iron outcrops & sunken sinkholes",
            dangerLevel = "Moderate",
            recommendedLevel = "Lvl 6-12",
            dangerSeverity = 2
        )
        val ALDER_SEA_PLAINS = WorldRegionMeta(
            regionId = "alder_sea_plains",
            displayName = "Mirewatch & Central Sea",
            gridCol = 1,
            gridRow = 1,
            biomeSummary = "Mirewatch Peninsula fortress, bay port & sea causeway",
            dangerLevel = "Moderate",
            recommendedLevel = "Lvl 6-12",
            dangerSeverity = 1,
            harborPortId = "mirewatch_bay"
        )
        val SPIREWOOD_WEST = WorldRegionMeta(
            regionId = "spirewood_west",
            displayName = "Spirewood Western Reach",
            gridCol = 2,
            gridRow = 1,
            biomeSummary = "Rolling coastal woods, inland rivers & eastern causeway",
            dangerLevel = "Low-Mod",
            recommendedLevel = "Lvl 4-10",
            dangerSeverity = 1
        )
        val ROOKVALE_VALLEY = WorldRegionMeta(
            regionId = DEFAULT_REGION_ID,
            displayName = "Rookvale City & Valley",
            gridCol = 3,
            gridRow = 1,
            biomeSummary = "Walled capital heartland, Royal Harbor & frontier camps",
            dangerLevel = "Low",
            recommendedLevel = "Lvl 1-8",
            dangerSeverity = 0,
            harborPortId = "rookvale_harbor"
        )
        val SUNSPIRE_DESERT = WorldRegionMeta(
            regionId = "sunspire_desert",
            displayName = "Sunspire Desert & Badlands",
            gridCol = 0,
            gridRow = 2,
            biomeSummary = "Golden dunes, Sunspire Oasis City & volcanic badlands",
            dangerLevel = "High",
            recommendedLevel = "Lvl 12-22",
            dangerSeverity = 3,
            harborPortId = "sunspire_dock"
        )
        val MAELSTROM_JUNGLE_WEST = WorldRegionMeta(
            regionId = "maelstrom_jungle_west",
            displayName = "Maelstrom Sea & West Jungle",
            gridCol = 1,
            gridRow = 2,
            biomeSummary = "Sea-Dragon Shrine Isle & western emerald rainforest",
            dangerLevel = "High",
            recommendedLevel = "Lvl 12-18",
            dangerSeverity = 3
        )
        val EMERALD_JUNGLE_EAST = WorldRegionMeta(
            regionId = "emerald_jungle_east",
            displayName = "Sunleaf Emerald Jungle",
            gridCol = 2,
            gridRow = 2,
            biomeSummary = "Sunleaf Village canopy settlements & jungle pier",
            dangerLevel = "Moderate",
            recommendedLevel = "Lvl 10-16",
            dangerSeverity = 2,
            harborPortId = "sunleaf_pier"
        )
        val EMBER_ISLES = WorldRegionMeta(
            regionId = "ember_isles",
            displayName = "The Ember Isles",
            gridCol = 3,
            gridRow = 2,
            biomeSummary = "Volcanic eastern archipelago, lighthouse & landing port",
            dangerLevel = "Extreme",
            recommendedLevel = "Lvl 18-26",
            dangerSeverity = 4,
            harborPortId = "ember_landing"
        )

        val ALL_REGIONS: List<WorldRegionMeta> = listOf(
            CALDERA_PEAKS, FROSTBAY_STRAITS, BLIGHTWOOD_REACH, FROSTCREST_HIGHLANDS,
            WRITHING_MARSHES, ALDER_SEA_PLAINS, SPIREWOOD_WEST, ROOKVALE_VALLEY,
            SUNSPIRE_DESERT, MAELSTROM_JUNGLE_WEST, EMERALD_JUNGLE_EAST, EMBER_ISLES
        )

        val byId: Map<String, WorldRegionMeta> = ALL_REGIONS.associateBy { it.regionId }

        fun forGlobalTile(x: Int, y: Int): WorldRegionMeta {
            val col = (x / OVERLAND_REGION_SIZE).coerceIn(0, WORLD_REGION_COLS - 1)
            val row = (y / OVERLAND_REGION_SIZE).coerceIn(0, WORLD_REGION_ROWS - 1)
            return ALL_REGIONS[row * WORLD_REGION_COLS + col]
        }
    }
}
