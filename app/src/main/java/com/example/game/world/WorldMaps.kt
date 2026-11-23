package com.example.game.world

import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.OVERLAND_REGION_SIZE
import com.example.game.model.OreNodeState
import com.example.game.model.TileType
import com.example.game.model.WORLD_CHUNK_SIZE
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class FloorPortal(
    val fromFloor: Int,
    val fromX: Int,
    val fromY: Int,
    val toFloor: Int,
    val toX: Int,
    val toY: Int,
    val requiresRope: Boolean = false
)

data class NpcSpot(
    val floor: Int,
    val x: Int,
    val y: Int,
    val name: String,
    val role: NpcRole
)

enum class NpcRole {
    SHOP_ALCHEMIST,
    TEMPLE_PRIEST,
    ORACLE
}

data class MonsterSpawn(
    val floor: Int,
    val x: Int,
    val y: Int,
    val type: MonsterType
)

data class LandmarkLabel(
    val floor: Int,
    val x: Int,
    val y: Int,
    val label: String
)

data class WorldFloor(
    val floorId: Int,
    val name: String,
    val subtitle: String,
    val width: Int,
    val height: Int,
    val chunkedGrid: ChunkedTileGrid,
    val regionId: String = DEFAULT_REGION_ID,
    val buildings: List<BuildingFootprint> = emptyList(),
    val isProtectionZone: (Int, Int) -> Boolean = { _, _ -> false },
    val dangerLevel: String = "Low",
    val recommendedLevel: String = "Lvl 1-3",
    val dangerSeverity: Int = 0
) {
    fun tileAt(x: Int, y: Int): TileType = chunkedGrid.get(x, y)

    fun visibleChunkBounds(minTx: Int, maxTx: Int, minTy: Int, maxTy: Int): ChunkBounds =
        chunkedGrid.visibleChunkBounds(minTx, maxTx, minTy, maxTy)
}

object WorldMaps {
    // Central Rookvale Sanctuary Plaza spawn in the 400x400 rookvale_valley region
    const val TEMPLE_SPAWN_X = 196
    const val TEMPLE_SPAWN_Y = 192
    const val ORACLE_X = 199
    const val ORACLE_Y = 179

    val regions: Map<String, WorldRegionMeta> = mapOf(
        DEFAULT_REGION_ID to WorldRegionMeta.ROOKVALE_VALLEY
    )

    val npcs: List<NpcSpot> = listOf(
        NpcSpot(0, 162, 204, "Alaric", NpcRole.SHOP_ALCHEMIST),
        NpcSpot(0, 193, 179, "Brother Alden", NpcRole.TEMPLE_PRIEST),
        NpcSpot(0, ORACLE_X, ORACLE_Y, "The Oracle", NpcRole.ORACLE)
    )

    val landmarks: List<LandmarkLabel> = listOf(
        LandmarkLabel(0, 196, 192, "TEMPLE & SANCTUARY PLAZA"),
        LandmarkLabel(0, 196, 197, "TOWN SQUARE FOUNTAIN"),
        LandmarkLabel(0, 164, 185, "TOWN DEPOT HALL"),
        LandmarkLabel(0, 163, 205, "ALARIC'S MARKET QUARTER"),
        LandmarkLabel(0, 225, 182, "RUNECRAFTING ACADEMY"),
        LandmarkLabel(0, 224, 204, "CRAFT & FLETCHING WARD"),
        LandmarkLabel(0, 245, 204, "LOGGING ORCHARD"),
        LandmarkLabel(0, 183, 231, "WEST HARBOR FISHING PIER"),
        LandmarkLabel(0, 211, 231, "EAST HARBOR FISHING PIER"),
        LandmarkLabel(0, 242, 180, "CITY SEWERS (FLOOR -1)"),
        LandmarkLabel(0, 152, 173, "CELLAR ROPE HOLE (FLOOR -1)"),
        LandmarkLabel(0, 196, 134, "NORTH STARTER MEADOWS"),
        LandmarkLabel(0, 92, 97, "NW OCHRE QUARRY (FLOOR -1)"),
        LandmarkLabel(0, 264, 85, "MOUNTAIN CAVERN (FLOOR -1)"),
        LandmarkLabel(0, 308, 67, "DEEP ABYSSAL CAVERN (FLOOR -2)"),
        LandmarkLabel(0, 312, 207, "DARK FOREST SINKHOLE (FLOOR -1)"),
        LandmarkLabel(0, 196, 8, "NORTH BORDER HIGHWAY"),
        LandmarkLabel(0, 8, 192, "WEST BORDER HIGHWAY"),
        LandmarkLabel(0, 391, 192, "EAST BORDER HIGHWAY"),
        LandmarkLabel(-3, 24, 18, "DEEP FORGE OUTPOST")
    )

    val portals: List<FloorPortal> = listOf(
        // Floor 0 City Sewers Stairs <-> Floor -1
        FloorPortal(0, 242, 179, -1, 28, 11, requiresRope = false),
        FloorPortal(-1, 28, 10, 0, 242, 180, requiresRope = false),
        // Floor 0 Western Town Cellar Rope-Hole <-> Floor -1
        FloorPortal(0, 152, 172, -1, 10, 11, requiresRope = false),
        FloorPortal(-1, 10, 10, 0, 152, 173, requiresRope = true),
        // Floor 0 NW Quarry Rope-Hole <-> Floor -1
        FloorPortal(0, 92, 96, -1, 14, 15, requiresRope = false),
        FloorPortal(-1, 14, 14, 0, 92, 97, requiresRope = true),
        // Floor 0 Mountain Cavern Stairs <-> Floor -1
        FloorPortal(0, 264, 84, -1, 38, 11, requiresRope = false),
        FloorPortal(-1, 38, 10, 0, 264, 85, requiresRope = false),
        // Floor 0 Dark Forest Sinkhole Rope-Hole <-> Floor -1
        FloorPortal(0, 312, 206, -1, 38, 29, requiresRope = false),
        FloorPortal(-1, 38, 28, 0, 312, 207, requiresRope = true),
        // Floor 0 NE Crag Hills Deep Cavern <-> Direct Floor -2 Deep Caverns
        FloorPortal(0, 308, 66, -2, 11, 24, requiresRope = false),
        FloorPortal(-2, 10, 24, 0, 308, 67, requiresRope = false),

        // Floor -1 <-> Floor -2 (Rope-Hole Down / Rope-Spot Up)
        FloorPortal(-1, 24, 38, -2, 24, 37, requiresRope = false),
        FloorPortal(-2, 24, 38, -1, 24, 37, requiresRope = true),

        // Floor -2 <-> Floor -3 (Crypt Stairs + Eastern Rope Pit)
        FloorPortal(-2, 24, 8, -3, 24, 9, requiresRope = false),
        FloorPortal(-3, 24, 8, -2, 24, 9, requiresRope = false),
        FloorPortal(-2, 40, 24, -3, 39, 24, requiresRope = false),
        FloorPortal(-3, 40, 24, -2, 39, 24, requiresRope = true),

        // Floor -3 <-> Floor -4 (Obsidian Stairs + Western Rope Pit)
        FloorPortal(-3, 24, 40, -4, 24, 39, requiresRope = false),
        FloorPortal(-4, 24, 40, -3, 24, 39, requiresRope = false),
        FloorPortal(-3, 10, 34, -4, 11, 34, requiresRope = false),
        FloorPortal(-4, 10, 34, -3, 11, 34, requiresRope = true)
    )

    val monsterSpawns: List<MonsterSpawn> = listOf(
        // --- Floor 0 Starter Meadows & Farms (TIER 1 — Levels 1-3, outside Rookvale City PZ) ---
        // North Gate Starter Meadows
        MonsterSpawn(0, 188, 136, MonsterType.WILD_RABBIT),
        MonsterSpawn(0, 204, 134, MonsterType.WILD_RABBIT),
        MonsterSpawn(0, 190, 126, MonsterType.CAVE_RAT),
        MonsterSpawn(0, 208, 124, MonsterType.YOUNG_SNAKE),
        MonsterSpawn(0, 196, 114, MonsterType.CRAG_WOLF),
        // West Gate Glades & Farms
        MonsterSpawn(0, 134, 186, MonsterType.WILD_RABBIT),
        MonsterSpawn(0, 128, 198, MonsterType.CAVE_RAT),
        MonsterSpawn(0, 120, 184, MonsterType.YOUNG_SNAKE),
        MonsterSpawn(0, 114, 196, MonsterType.CAVE_RAT),
        MonsterSpawn(0, 106, 180, MonsterType.CRAG_WOLF),
        // East Gate Meadow & Whispering Edge
        MonsterSpawn(0, 266, 186, MonsterType.WILD_RABBIT),
        MonsterSpawn(0, 272, 198, MonsterType.YOUNG_SNAKE),
        MonsterSpawn(0, 280, 184, MonsterType.CRAG_WOLF),
        // South Valley Meadows (across river bridges)
        MonsterSpawn(0, 184, 260, MonsterType.WILD_RABBIT),
        MonsterSpawn(0, 210, 262, MonsterType.YOUNG_SNAKE),
        MonsterSpawn(0, 196, 278, MonsterType.CRAG_WOLF),

        // --- Floor 0 Outer Wilds (TIER 2 — Levels 3-6, Quarry, Dark Woods & Crag Hills) ---
        // NW Ochre Quarry & Foothills
        MonsterSpawn(0, 110, 114, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(0, 98, 106, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, 84, 96, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(0, 94, 82, MonsterType.SKELETON),
        MonsterSpawn(0, 76, 104, MonsterType.SWAMP_TROLL),
        // East Deep Dark Woods
        MonsterSpawn(0, 294, 196, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(0, 304, 212, MonsterType.POISON_SPIDER),
        MonsterSpawn(0, 322, 202, MonsterType.POISON_SPIDER),
        MonsterSpawn(0, 316, 220, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, 336, 194, MonsterType.POISON_SPIDER),
        // NE Crag Hills & Deep Cavern Approach
        MonsterSpawn(0, 242, 98, MonsterType.CRAG_WOLF),
        MonsterSpawn(0, 256, 90, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(0, 274, 82, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, 288, 76, MonsterType.SKELETON),
        MonsterSpawn(0, 300, 72, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, 316, 68, MonsterType.SKELETON),

        // --- Floor -1: Mossy Sewers & Caves (TIER 2 — Levels 3-6) ---
        MonsterSpawn(-1, 24, 14, MonsterType.CAVE_RAT),
        MonsterSpawn(-1, 20, 18, MonsterType.SWAMP_TROLL),
        MonsterSpawn(-1, 32, 16, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(-1, 16, 20, MonsterType.SKELETON),
        MonsterSpawn(-1, 14, 28, MonsterType.POISON_SPIDER),
        MonsterSpawn(-1, 22, 28, MonsterType.SKELETON),
        MonsterSpawn(-1, 32, 26, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(-1, 28, 33, MonsterType.POISON_SPIDER),
        MonsterSpawn(-1, 20, 35, MonsterType.SWAMP_TROLL),

        // --- Floor -2: Deep Caverns (TIER 3 — Levels 6-10) ---
        MonsterSpawn(-2, 18, 32, MonsterType.MINOTAUR),
        MonsterSpawn(-2, 30, 32, MonsterType.ORC_WARRIOR),
        MonsterSpawn(-2, 14, 24, MonsterType.FIRE_DEVIL),
        MonsterSpawn(-2, 34, 24, MonsterType.MINOTAUR),
        MonsterSpawn(-2, 24, 24, MonsterType.GHOUL),
        MonsterSpawn(-2, 18, 16, MonsterType.ORC_WARRIOR),
        MonsterSpawn(-2, 30, 16, MonsterType.GHOUL),
        MonsterSpawn(-2, 24, 12, MonsterType.FIRE_DEVIL),

        // --- Floor -3: Abyssal Crypt & Boss (TIER 4 — Levels 10-18) ---
        MonsterSpawn(-3, 14, 14, MonsterType.CYCLOPS),
        MonsterSpawn(-3, 34, 14, MonsterType.CYCLOPS),
        MonsterSpawn(-3, 12, 24, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(-3, 35, 24, MonsterType.NECROMANCER),
        MonsterSpawn(-3, 16, 32, MonsterType.NECROMANCER),
        MonsterSpawn(-3, 32, 32, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(-3, 24, 34, MonsterType.DEMON_LORD),

        // --- Floor -4: Obsidian Wyrm Sanctum (Endgame Raid — Levels 16-20+) ---
        MonsterSpawn(-4, 18, 32, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(-4, 32, 32, MonsterType.FIRE_DEVIL),
        MonsterSpawn(-4, 14, 24, MonsterType.NECROMANCER),
        MonsterSpawn(-4, 34, 24, MonsterType.DRAGON_HATCHLING),
        MonsterSpawn(-4, 24, 16, MonsterType.ELDER_DRAGON)
    )

    val floors: Map<Int, WorldFloor> = mapOf(
        0 to buildOverlandSurface(),
        -1 to buildSewersFloor(),
        -2 to buildCryptsFloor(),
        -3 to buildForgehallFloor(),
        -4 to buildWyrmSanctumFloor()
    )

    fun getFloor(floorId: Int): WorldFloor = floors[floorId] ?: floors.getValue(0)

    fun portalAt(floorId: Int, x: Int, y: Int, requiresRope: Boolean? = null): FloorPortal? {
        return portals.firstOrNull { p ->
            p.fromFloor == floorId && p.fromX == x && p.fromY == y &&
                (requiresRope == null || p.requiresRope == requiresRope)
        }
    }

    fun fallbackLandingForFloor(floorId: Int): Pair<Int, Int> = when (floorId) {
        0 -> TEMPLE_SPAWN_X to TEMPLE_SPAWN_Y
        -1 -> 28 to 11
        -2 -> 24 to 37
        -3 -> 24 to 9
        -4 -> 24 to 39
        else -> TEMPLE_SPAWN_X to TEMPLE_SPAWN_Y
    }

    /**
     * Finds the closest walkable, NPC-free tile within [maxRadius] using BFS so migrated saves
     * near redesigned props/walls nudge smoothly onto adjacent open ground without teleporting players away.
     */
    fun findNearestWalkableTile(floorId: Int, startX: Int, startY: Int, maxRadius: Int = 8): Pair<Int, Int>? {
        val floorMap = getFloor(floorId)
        if (startX !in 0 until floorMap.width || startY !in 0 until floorMap.height) return null
        val npcCoords = npcs.filter { it.floor == floorId }.map { it.x to it.y }.toSet()

        fun isValid(x: Int, y: Int): Boolean {
            return x in 0 until floorMap.width &&
                y in 0 until floorMap.height &&
                floorMap.tileAt(x, y).walkable &&
                (x to y) !in npcCoords
        }

        if (isValid(startX, startY)) return startX to startY

        for (r in 1..maxRadius) {
            for (dy in -r..r) {
                for (dx in -r..r) {
                    if (maxOf(abs(dx), abs(dy)) != r) continue
                    val nx = startX + dx
                    val ny = startY + dy
                    if (isValid(nx, ny)) return nx to ny
                }
            }
        }
        return null
    }

    fun initialMonsters(): List<MonsterEntity> {
        return monsterSpawns.mapIndexed { idx, spawn ->
            MonsterEntity(
                id = idx + 1,
                type = spawn.type,
                floor = spawn.floor,
                x = spawn.x,
                y = spawn.y,
                spawnX = spawn.x,
                spawnY = spawn.y,
                hp = spawn.type.maxHp,
                maxHp = spawn.type.maxHp,
                animOffset = (idx * 0.37f) % 1f
            )
        }
    }

    fun initialOreNodes(): Map<Triple<Int, Int, Int>, OreNodeState> {
        val map = LinkedHashMap<Triple<Int, Int, Int>, OreNodeState>()
        floors.values.forEach { fl ->
            for (y in 0 until fl.height) {
                for (x in 0 until fl.width) {
                    val kind = fl.tileAt(x, y).oreVeinKind()
                    if (kind != null) {
                        map[Triple(fl.floorId, x, y)] = OreNodeState(
                            floor = fl.floorId,
                            x = x,
                            y = y,
                            kind = kind,
                            chargesLeft = kind.maxCharges
                        )
                    }
                }
            }
        }
        return map
    }

    private fun createBlank(w: Int, h: Int, fill: TileType): Array<Array<TileType>> =
        Array(h) { Array(w) { fill } }

    private fun fillRect(g: Array<Array<TileType>>, x1: Int, y1: Int, x2: Int, y2: Int, tile: TileType) {
        for (y in y1.coerceAtLeast(0)..y2.coerceAtMost(g.lastIndex)) {
            for (x in x1.coerceAtLeast(0)..x2.coerceAtMost(g[0].lastIndex)) {
                g[y][x] = tile
            }
        }
    }

    private fun deterministicHash(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    private fun riverCenterY(x: Int): Int {
        val wave = sin(x * 0.028) * 5.0 + cos(x * 0.013) * 3.0
        // Gentle harbor bay dip directly south of Rookvale City
        val harborDip = if (x in 165..235) {
            val t = (x - 200) / 35.0
            (1.0 - t * t) * 4.0
        } else 0.0
        return (235.0 + wave + harborDip).toInt()
    }

    /**
     * FLOOR 0: 400x400 Chunked Rookvale City & Valley (`rookvale_valley`)
     * Partitioned into 32x32 spatial chunks (~169 chunks) for 60 FPS rotated viewport culling.
     */
    private fun buildOverlandSurface(): WorldFloor {
        val s = OVERLAND_REGION_SIZE // 400
        val grid = ChunkedTileGrid(s, s, WORLD_CHUNK_SIZE, TileType.GRASS)

        // 1. Natural valley meadows, wildflower patches & dark forest biomes across 400x400
        for (y in 0 until s) {
            for (x in 0 until s) {
                val h = deterministicHash(x, y, 7) and 255
                val inEastWoods = x >= 272 && y in 135..345
                val inFarWestWoods = x <= 90 && y in 250..365
                val tile = when {
                    inEastWoods -> if (h < 185) TileType.DARK_GRASS else TileType.GRASS
                    inFarWestWoods -> if (h < 150) TileType.DARK_GRASS else TileType.GRASS
                    h < 34 -> TileType.FLOWER_GRASS
                    else -> TileType.GRASS
                }
                grid.set(x, y, tile)
            }
        }

        // 2. Soft regional border rim (leaving wide open highways at North, East, West & South!)
        for (i in 0 until s) {
            if (i !in 191..201) {
                grid.set(i, 0, TileType.MOUNTAIN_ROCK)
                grid.set(i, 1, TileType.TREE)
                grid.set(i, s - 1, TileType.MOUNTAIN_ROCK)
                grid.set(i, s - 2, TileType.TREE)
            }
            if (i !in 187..197 && i !in 226..248) {
                grid.set(0, i, TileType.MOUNTAIN_ROCK)
                grid.set(1, i, TileType.TREE)
                grid.set(s - 1, i, TileType.MOUNTAIN_ROCK)
                grid.set(s - 2, i, TileType.TREE)
            }
        }

        // 3. Organic outer wilderness forests, rocky hills & clearings (avoiding central city & highways)
        for (y in 8 until s - 8 step 3) {
            for (x in 8 until s - 8 step 3) {
                // Keep central city core + immediate starter clearings + highway corridors clear
                if (x in 138..264 && y in 136..254) continue
                if (x in 188..204 || y in 184..200) continue
                if (x in 62..132 && y in 62..132) continue // NW Quarry
                if (x in 230..332 && y in 48..116) continue // NE Crag Hills

                val h = deterministicHash(x, y, 19) and 255
                val inEastForest = x >= 278 && y in 140..340
                val inNorthRidge = y <= 88 && x !in 175..220
                val inSouthWilds = y >= 264

                when {
                    inEastForest && h < 92 -> {
                        grid.set(x, y, TileType.TREE)
                        if ((h and 3) == 0) grid.set(x + 1, y + 1, TileType.TREE)
                    }
                    inNorthRidge && h < 58 -> {
                        grid.set(x, y, if (h < 32) TileType.MOUNTAIN_ROCK else TileType.TREE)
                    }
                    inSouthWilds && h < 42 -> {
                        grid.set(x, y, TileType.TREE)
                    }
                    h < 28 -> {
                        grid.set(x, y, TileType.TREE)
                    }
                }
            }
        }

        // 4. Wide Regional Border Highways (connecting to future 400x400 neighboring cities!)
        // North Border Highway (x=194..198, y=0..150)
        grid.fillRect(194, 0, 198, 150, TileType.COBBLESTONE)
        // West Border Highway (x=0..148, y=190..194)
        grid.fillRect(0, 190, 148, 194, TileType.COBBLESTONE)
        // East Border Highway (x=254..399, y=190..194)
        grid.fillRect(254, 190, 399, 194, TileType.COBBLESTONE)
        // South Frontier Road (x=194..198, y=244..399)
        grid.fillRect(194, 244, 198, 399, TileType.DIRT_FLOOR)
        grid.fillRect(195, 244, 197, 305, TileType.COBBLESTONE)

        // Roadside lanterns along highway approaches near the city
        listOf(
            193 to 132, 199 to 132, 193 to 112, 199 to 112,
            130 to 189, 130 to 195, 112 to 189, 112 to 195,
            270 to 189, 270 to 195, 290 to 189, 290 to 195
        ).forEach { (lx, ly) -> grid.set(lx, ly, TileType.LANTERN_POST) }

        // 5. Northwest Ochre Quarry (x=64..128, y=64..128) & Winding Quarry Road
        grid.fillEllipse(94, 96, 30, 26, TileType.DIRT_FLOOR)
        // Natural crescent quarry cliff walls (leaving wide east/south entrances)
        for (x in 64..122) {
            if (x < 108) grid.set(x, 70, TileType.MOUNTAIN_ROCK)
            if (x < 98) grid.set(x, 71, TileType.MOUNTAIN_ROCK)
        }
        for (y in 70..118) {
            grid.set(65, y, TileType.MOUNTAIN_ROCK)
            if (y < 104) grid.set(66, y, TileType.MOUNTAIN_ROCK)
        }
        // Ochre Veins around quarry ledges
        listOf(
            70 to 78, 72 to 86, 70 to 98, 74 to 110,
            82 to 74, 96 to 74, 108 to 78, 116 to 88,
            80 to 114, 104 to 112
        ).forEach { (vx, vy) -> grid.set(vx, vy, TileType.ORE_OCHRE) }
        // Winding trail from North Highway (194, 120) & West Highway (118, 190) into Quarry
        grid.drawRoadSegment(194, 120, 120, 112, 1, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(120, 112, 92, 98, 1, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(118, 190, 112, 116, 1, TileType.DIRT_FLOOR)
        grid.set(92, 96, TileType.ROPE_HOLE_DOWN)
        grid.set(92, 97, TileType.DIRT_FLOOR)

        // 6. Northern Crag Hills (Floor -1 Mountain Cavern + Direct Floor -2 Deep Abyssal Cavern)
        grid.fillEllipse(266, 84, 24, 18, TileType.DIRT_FLOOR)
        grid.fillEllipse(308, 68, 22, 16, TileType.DIRT_FLOOR)
        // Rocky ridge crest behind the caverns
        for (x in 244..332) {
            val ridgeY = 56 + ((sin(x * 0.14) * 3.0).toInt())
            grid.set(x, ridgeY, TileType.MOUNTAIN_ROCK)
            grid.set(x, ridgeY + 1, TileType.MOUNTAIN_ROCK)
        }
        // Ochre & Bogstone outcrops in Crag Hills
        listOf(254 to 76, 276 to 74, 296 to 62, 320 to 62).forEach { (vx, vy) ->
            grid.set(vx, vy, TileType.ORE_OCHRE)
        }
        // Winding mountain trails from North Highway to both caverns
        grid.drawRoadSegment(198, 112, 248, 96, 1, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(248, 96, 264, 86, 1, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(264, 86, 294, 74, 1, TileType.DIRT_FLOOR)
        grid.drawRoadSegment(294, 74, 308, 68, 1, TileType.DIRT_FLOOR)
        // Floor -1 Mountain Cavern Stairs
        grid.set(264, 84, TileType.STAIRS_DOWN)
        grid.set(264, 85, TileType.DIRT_FLOOR)
        grid.set(262, 83, TileType.LANTERN_POST)
        grid.set(266, 83, TileType.LANTERN_POST)
        // Floor -2 Deep Abyssal Cavern Stairs (direct overland entry to Floor -2!)
        grid.set(308, 66, TileType.STAIRS_DOWN)
        grid.set(308, 67, TileType.DIRT_FLOOR)
        grid.set(306, 65, TileType.TORCH_WALL)
        grid.set(310, 65, TileType.TORCH_WALL)

        // 7. East Deep Dark Woods Trail & Sinkhole (Floor -1)
        grid.fillEllipse(312, 206, 10, 8, TileType.DARK_GRASS)
        grid.drawRoadSegment(284, 192, 312, 206, 1, TileType.DIRT_FLOOR)
        grid.set(312, 206, TileType.ROPE_HOLE_DOWN)
        grid.set(312, 207, TileType.DIRT_FLOOR)

        // 8. Starter Meadows & Rustic Farms outside North & West Gates
        // North farmstead garden plot (fenced patch with open gate)
        grid.fillRect(172, 126, 184, 138, TileType.FLOWER_GRASS)
        for (x in 172..184) {
            if (x !in 177..179) {
                grid.set(x, 126, TileType.WOOD_FENCE)
                grid.set(x, 138, TileType.WOOD_FENCE)
            }
        }
        // West farmstead wildflower clearing
        grid.fillEllipse(124, 174, 10, 8, TileType.FLOWER_GRASS)

        // 9. WIDE WINDING ROOKVALE RIVER & SOUTHERN HARBOR BASIN (full x = 0..399)
        for (x in 0 until s) {
            val cy = riverCenterY(x)
            val halfW = if (x in 172..224) 7 else 5
            for (y in (cy - halfW)..(cy + halfW)) {
                grid.set(x, y, TileType.WATER)
            }
        }

        // 10. FULL-SCALE ROOKVALE CITY CORE (x = 146..256, y = 148..228)
        // Lush green base lawn inside city with wildflowers
        for (y in 148..228) {
            for (x in 146..256) {
                if (grid.get(x, y) != TileType.WATER) {
                    val h = deterministicHash(x, y, 41) and 31
                    grid.set(x, y, if (h < 5) TileType.FLOWER_GRASS else TileType.GRASS)
                }
            }
        }

        // City Grand Boulevards & Connecting Cobblestone Lanes
        grid.fillRect(194, 148, 198, 228, TileType.COBBLESTONE) // Central North-South Boulevard
        grid.fillRect(146, 190, 256, 194, TileType.COBBLESTONE) // Central West-East Boulevard
        grid.fillRect(150, 222, 248, 227, TileType.COBBLESTONE) // South Riverfront Promenade
        grid.fillRect(159, 170, 162, 228, TileType.COBBLESTONE) // West Market & Depot Avenue
        grid.fillRect(234, 170, 237, 228, TileType.COBBLESTONE) // East Academy & Craft Avenue
        grid.fillRect(154, 168, 244, 170, TileType.COBBLESTONE) // North Garden Ring Lane
        grid.fillRect(154, 212, 240, 214, TileType.COBBLESTONE) // South Market Ring Lane

        // Open City Perimeter: Trimmed Hedges, Wooden Fences & Stone Gateposts (no cramped curtain walls!)
        for (x in 146..256) {
            if (x !in 193..199) {
                val borderTile = when {
                    x % 14 == 0 -> TileType.LANTERN_POST
                    (x / 5) % 2 == 0 -> TileType.HEDGE
                    else -> TileType.WOOD_FENCE
                }
                grid.set(x, 148, borderTile)
            }
        }
        for (y in 149..226) {
            if (y !in 189..195) {
                val borderTile = when {
                    y % 12 == 0 -> TileType.LANTERN_POST
                    (y / 5) % 2 == 0 -> TileType.HEDGE
                    else -> TileType.WOOD_FENCE
                }
                if (grid.get(146, y) != TileType.WATER) grid.set(146, y, borderTile)
                if (grid.get(256, y) != TileType.WATER) grid.set(256, y, borderTile)
            }
        }
        // Grand Stone Gateposts framing North, West, and East Highways
        listOf(
            193 to 148, 199 to 148, // North Gate
            146 to 189, 146 to 195, // West Gate
            256 to 189, 256 to 195  // East Gate
        ).forEach { (gx, gy) -> grid.set(gx, gy, TileType.GATE_PILLAR) }

        // --- DISTRICT 1: TEMPLE & SANCTUARY PLAZA (x = 182..210, y = 172..208) ---
        grid.fillRect(182, 188, 210, 208, TileType.COBBLESTONE)
        // Corner garden beds in Town Square
        grid.fillRect(183, 204, 187, 207, TileType.FLOWER_GRASS)
        grid.fillRect(205, 204, 209, 207, TileType.FLOWER_GRASS)
        grid.set(183, 204, TileType.HEDGE); grid.set(187, 207, TileType.HEDGE)
        grid.set(209, 204, TileType.HEDGE); grid.set(205, 207, TileType.HEDGE)
        // Bubbling Stone Fountain in the heart of Town Square + 4 Lantern Posts
        grid.set(196, 198, TileType.FOUNTAIN)
        listOf(191 to 194, 201 to 194, 191 to 202, 201 to 202).forEach { (lx, ly) ->
            grid.set(lx, ly, TileType.LANTERN_POST)
        }

        // Temple Sanctuary Building (188..204, 174..187)
        grid.fillRect(188, 174, 204, 187, TileType.TEMPLE_FLOOR)
        grid.drawWallBorder(188, 174, 204, 187, TileType.TEMPLE_WALL)
        // Grand 5-tile open South Portico opening onto Sanctuary Plaza
        grid.fillRect(194, 187, 198, 188, TileType.TEMPLE_FLOOR)
        grid.set(193, 187, TileType.GATE_PILLAR)
        grid.set(199, 187, TileType.GATE_PILLAR)
        grid.set(192, 189, TileType.LANTERN_POST)
        grid.set(200, 189, TileType.LANTERN_POST)
        // Temple Altar, Sanctuary Pews & Open Walkways around Brother Alden (193,179) & The Oracle (199,179)
        grid.set(196, 177, TileType.TEMPLE_ALTAR)
        listOf(191 to 182, 191 to 184, 201 to 182, 201 to 184).forEach { (px, py) ->
            grid.set(px, py, TileType.TEMPLE_PEW)
        }
        // Keep spawn tile (196, 192) crystal clear cobblestone in Sanctuary Plaza
        grid.set(TEMPLE_SPAWN_X, TEMPLE_SPAWN_Y, TileType.COBBLESTONE)

        // --- DISTRICT 2: GRAND MARKET & DEPOT QUARTER (x = 152..180, y = 174..212) ---
        // Town Depot Hall (156..172, 176..188)
        grid.fillRect(156, 176, 172, 188, TileType.WOOD_PLANK_FLOOR)
        grid.drawWallBorder(156, 176, 172, 188, TileType.TIMBER_WALL)
        // Warm glowing windows along Depot north & south walls
        listOf(160 to 176, 164 to 176, 168 to 176, 159 to 188, 169 to 188).forEach { (wx, wy) ->
            grid.set(wx, wy, TileType.TIMBER_WINDOW_WALL)
        }
        // Wide South & East Archways
        grid.fillRect(163, 188, 165, 189, TileType.WOOD_PLANK_FLOOR)
        grid.fillRect(172, 181, 173, 183, TileType.WOOD_PLANK_FLOOR)
        // Iron-bound Depot Chests & Supply Crates/Barrels
        grid.set(158, 179, TileType.DEPOT_CHEST)
        grid.set(158, 182, TileType.DEPOT_CHEST)
        grid.set(158, 185, TileType.DEPOT_CHEST)
        listOf(161 to 178, 169 to 178, 169 to 186).forEach { (cx, cy) ->
            grid.set(cx, cy, TileType.CRATE_BARREL)
        }

        // Alaric's Shop Building (156..170, 198..210)
        grid.fillRect(156, 198, 170, 210, TileType.WOOD_PLANK_FLOOR)
        grid.drawWallBorder(156, 198, 170, 210, TileType.TIMBER_WALL)
        listOf(159 to 198, 167 to 198, 160 to 210, 166 to 210).forEach { (wx, wy) ->
            grid.set(wx, wy, TileType.TIMBER_WINDOW_WALL)
        }
        // Wide North & East Archways
        grid.fillRect(162, 197, 164, 198, TileType.WOOD_PLANK_FLOOR)
        grid.fillRect(170, 203, 171, 205, TileType.WOOD_PLANK_FLOOR)
        // Shop Counters, Weapon Rack, Crates & Alaric at (162, 204)
        grid.set(161, 201, TileType.SHOP_COUNTER)
        grid.set(163, 201, TileType.SHOP_COUNTER)
        grid.set(158, 201, TileType.WEAPON_RACK)
        grid.set(167, 200, TileType.CRATE_BARREL)
        grid.set(162, 204, TileType.WOOD_PLANK_FLOOR)

        // Open-Air Market Plaza & Colorful Awning Stalls (172..181, 196..210)
        grid.fillRect(171, 195, 181, 211, TileType.COBBLESTONE)
        listOf(175 to 199, 175 to 204, 175 to 209).forEach { (mx, my) ->
            grid.set(mx, my, TileType.MARKET_AWNING)
        }
        grid.set(178, 199, TileType.CRATE_BARREL)
        grid.set(178, 209, TileType.LANTERN_POST)

        // Western Town Cellar Rope-Hole (Floor -1) in cozy garden courtyard (152, 172)
        grid.fillRect(150, 170, 155, 175, TileType.COBBLESTONE)
        grid.set(152, 172, TileType.ROPE_HOLE_DOWN)
        grid.set(154, 171, TileType.LANTERN_POST)

        // --- DISTRICT 3: ARCANE & RUNECRAFTING QUARTER (x = 214..246, y = 172..188) ---
        grid.fillRect(216, 174, 234, 187, TileType.ARCANE_FLOOR)
        grid.drawWallBorder(216, 174, 234, 187, TileType.TIMBER_WALL)
        listOf(220 to 174, 230 to 174, 220 to 187, 230 to 187).forEach { (wx, wy) ->
            grid.set(wx, wy, TileType.TIMBER_WINDOW_WALL)
        }
        // Wide South & West Archways
        grid.fillRect(224, 187, 226, 189, TileType.ARCANE_FLOOR)
        grid.fillRect(215, 180, 216, 182, TileType.ARCANE_FLOOR)
        // Runecrafting Altar, Crystal Pillars & Scholar Bookshelves
        grid.set(225, 179, TileType.RUNECRAFTING_TABLE)
        listOf(219 to 177, 231 to 177, 219 to 184, 231 to 184).forEach { (cx, cy) ->
            grid.set(cx, cy, TileType.CRYSTAL_PILLAR)
        }
        listOf(222 to 175, 223 to 175, 227 to 175, 228 to 175).forEach { (bx, by) ->
            grid.set(bx, by, TileType.BOOKSHELF)
        }
        // City Sewers Entrance (Floor -1) neatly beside Academy courtyard
        grid.fillRect(239, 176, 245, 183, TileType.COBBLESTONE)
        grid.set(242, 179, TileType.STAIRS_DOWN)
        grid.set(240, 177, TileType.LANTERN_POST)
        grid.set(244, 177, TileType.LANTERN_POST)

        // --- DISTRICT 4: CRAFT & FLETCHING WARD + LOGGING ORCHARD (x = 214..254, y = 196..218) ---
        grid.fillRect(216, 198, 234, 211, TileType.COBBLESTONE)
        grid.fillRect(218, 200, 232, 209, TileType.WOOD_PLANK_FLOOR)
        // Back wall + open fenced courtyard sides
        for (x in 216..234) grid.set(x, 198, if (x in 220..222 || x in 228..230) TileType.TIMBER_WINDOW_WALL else TileType.TIMBER_WALL)
        for (y in 199..211) {
            if (y !in 203..206) {
                grid.set(216, y, TileType.WOOD_FENCE)
                grid.set(234, y, TileType.WOOD_FENCE)
            }
        }
        for (x in 217..233) {
            if (x !in 222..228) grid.set(x, 211, TileType.WOOD_FENCE)
        }
        // Glowing Forge, Anvil, Fletching Benches, Weapon Racks & Stacked Logs/Crates
        grid.set(219, 201, TileType.SMELTING_FURNACE)
        grid.set(223, 201, TileType.BLACKSMITH_ANVIL)
        grid.set(228, 201, TileType.FLETCHING_BENCH)
        grid.set(231, 201, TileType.FLETCHING_BENCH)
        grid.set(219, 207, TileType.WEAPON_RACK)
        grid.set(231, 207, TileType.CRATE_BARREL)
        grid.set(215, 202, TileType.LANTERN_POST)
        grid.set(235, 202, TileType.LANTERN_POST)

        // Rookvale Logging Orchard (spaced choppable trees right beside Fletching Ward!)
        for (y in 196..218) {
            for (x in 238..254) {
                grid.set(x, y, if ((x + y) % 3 == 0) TileType.FLOWER_GRASS else TileType.GRASS)
            }
        }
        listOf(
            241 to 199, 246 to 198, 251 to 200,
            240 to 204, 245 to 204, 250 to 205,
            242 to 209, 247 to 210, 252 to 210,
            241 to 215, 246 to 216, 251 to 215
        ).forEach { (tx, ty) -> grid.set(tx, ty, TileType.TREE) }

        // --- DISTRICT 5: SOUTH RIVERFRONT, BRIDGES & HARBOR DOCKS ---
        // Three Grand Bridges across the winding Rookvale River
        fun buildBridge(xStart: Int, xEnd: Int) {
            val midX = (xStart + xEnd) / 2
            val cy = riverCenterY(midX)
            for (y in (cy - 10)..(cy + 10)) {
                for (x in xStart..xEnd) {
                    if (grid.get(x, y) == TileType.WATER || y in (cy - 8)..(cy + 8)) {
                        grid.set(x, y, TileType.WOOD_BRIDGE)
                    }
                }
            }
            grid.set(xStart - 1, cy - 8, TileType.LANTERN_POST)
            grid.set(xEnd + 1, cy - 8, TileType.LANTERN_POST)
        }
        buildBridge(194, 198) // Grand Central River Bridge
        buildBridge(159, 162) // West Market Bridge
        buildBridge(234, 237) // East Craft Bridge

        // Harbor Fishing Piers extending from the Riverfront Promenade into the Harbor Basin
        grid.fillRect(180, 227, 185, 235, TileType.WOOD_PIER)
        grid.fillRect(209, 227, 214, 235, TileType.WOOD_PIER)
        // Moored Harbor Boats bobbing beside the piers
        listOf(179 to 232, 186 to 234, 208 to 234, 215 to 232).forEach { (bx, by) ->
            grid.set(bx, by, TileType.MOORED_BOAT)
        }
        // Waterfront lanterns & cargo crates along the Promenade
        listOf(176 to 224, 188 to 224, 204 to 224, 218 to 224).forEach { (lx, ly) ->
            grid.set(lx, ly, TileType.LANTERN_POST)
        }
        listOf(174 to 225, 220 to 225).forEach { (cx, cy) ->
            grid.set(cx, cy, TileType.CRATE_BARREL)
        }

        // Guarantee every Floor 0 monster spawn & portal landing is walkable ground
        for (spawn in monsterSpawns) {
            if (spawn.floor == 0 && !grid.get(spawn.x, spawn.y).walkable) {
                grid.set(spawn.x, spawn.y, TileType.GRASS)
            }
        }
        for (portal in portals) {
            if (portal.toFloor == 0 && !grid.get(portal.toX, portal.toY).walkable) {
                grid.set(portal.toX, portal.toY, TileType.DIRT_FLOOR)
            }
        }

        val cityBuildings = listOf(
            BuildingFootprint(
                id = "temple_sanctuary",
                name = "Temple Sanctuary",
                minX = 188, minY = 174, maxX = 204, maxY = 187,
                roofColorArgb = 0xFF7B6C58.toInt(),
                roofHighlightArgb = 0xFF9E8D74.toInt(),
                roofTrimArgb = 0xFFD9B462.toInt(),
                ridgeAlongX = true
            ),
            BuildingFootprint(
                id = "town_depot",
                name = "Town Depot Hall",
                minX = 156, minY = 176, maxX = 172, maxY = 188,
                roofColorArgb = 0xFF6B4631.toInt(),
                roofHighlightArgb = 0xFF8C5D43.toInt(),
                roofTrimArgb = 0xFF3E291D.toInt(),
                ridgeAlongX = true
            ),
            BuildingFootprint(
                id = "alaric_shop",
                name = "Alaric's Market Hall",
                minX = 156, minY = 198, maxX = 170, maxY = 210,
                roofColorArgb = 0xFF7E332E.toInt(),
                roofHighlightArgb = 0xFFA3453E.toInt(),
                roofTrimArgb = 0xFFD4A44C.toInt(),
                ridgeAlongX = true
            ),
            BuildingFootprint(
                id = "runecrafting_academy",
                name = "Runecrafting Academy",
                minX = 216, minY = 174, maxX = 234, maxY = 187,
                roofColorArgb = 0xFF3E3563.toInt(),
                roofHighlightArgb = 0xFF5A4E8C.toInt(),
                roofTrimArgb = 0xFF9F84E8.toInt(),
                ridgeAlongX = true
            ),
            BuildingFootprint(
                id = "craft_fletching_ward",
                name = "Craft & Fletching Ward",
                minX = 216, minY = 198, maxX = 234, maxY = 205,
                roofColorArgb = 0xFF5E4A39.toInt(),
                roofHighlightArgb = 0xFF7D634E.toInt(),
                roofTrimArgb = 0xFF3A2D23.toInt(),
                ridgeAlongX = true
            )
        )

        return WorldFloor(
            floorId = 0,
            name = "Rookvale City & Valley",
            subtitle = "Overland Region • 400×400",
            width = s,
            height = s,
            chunkedGrid = grid,
            regionId = DEFAULT_REGION_ID,
            buildings = cityBuildings,
            isProtectionZone = { x, y -> x in 147..255 && y in 149..227 },
            dangerLevel = "Low",
            recommendedLevel = "Lvl 1-6",
            dangerSeverity = 0
        )
    }

    private fun buildSewersFloor(): WorldFloor {
        val s = 48
        val g = createBlank(s, s, TileType.CAVE_WALL)

        // Northern sewer arrival galleries (matching City Sewers, Cellar, Quarry & Mountain Cave)
        fillRect(g, 8, 9, 41, 17, TileType.DIRT_FLOOR)
        fillRect(g, 11, 14, 19, 25, TileType.DIRT_FLOOR)
        // Central subterranean cistern & water channel
        fillRect(g, 16, 18, 35, 34, TileType.DIRT_FLOOR)
        fillRect(g, 22, 22, 27, 25, TileType.WATER)
        // East dark-forest cavern tunnel
        fillRect(g, 33, 25, 41, 33, TileType.DIRT_FLOOR)
        // Southern descent chamber toward Floor -2
        fillRect(g, 17, 33, 31, 41, TileType.DIRT_FLOOR)
        // Side stone pillars
        listOf(18 to 13, 31 to 13, 19 to 29, 32 to 29).forEach { (px, py) -> g[py][px] = TileType.CAVE_WALL }

        // Ochre & Bogstone Veins
        listOf(9 to 12, 13 to 22, 39 to 14).forEach { (x, y) -> g[y][x] = TileType.ORE_OCHRE }
        listOf(12 to 32, 30 to 36, 39 to 31, 20 to 40).forEach { (x, y) -> g[y][x] = TileType.ORE_BOGSTONE }

        // Torches
        g[8][28] = TileType.TORCH_WALL
        g[8][38] = TileType.TORCH_WALL
        g[41][24] = TileType.TORCH_WALL

        // Portals
        g[10][28] = TileType.STAIRS_UP
        g[10][38] = TileType.STAIRS_UP
        g[10][10] = TileType.ROPE_SPOT_UP
        g[14][14] = TileType.ROPE_SPOT_UP
        g[28][38] = TileType.ROPE_SPOT_UP
        g[38][24] = TileType.ROPE_HOLE_DOWN

        return WorldFloor(
            floorId = -1,
            name = "Mossy Sewers & Caves",
            subtitle = "Dungeon Floor -1",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            dangerLevel = "Moderate",
            recommendedLevel = "Lvl 3-6",
            dangerSeverity = 1
        )
    }

    private fun buildCryptsFloor(): WorldFloor {
        val s = 48
        val g = createBlank(s, s, TileType.STONE_WALL)

        fillRect(g, 16, 30, 32, 41, TileType.CRYPT_FLOOR) // Southern rope chamber
        fillRect(g, 20, 16, 28, 30, TileType.CRYPT_FLOOR) // Central nave
        fillRect(g, 8, 18, 20, 29, TileType.CRYPT_FLOOR)  // West ossuary
        fillRect(g, 28, 18, 42, 29, TileType.CRYPT_FLOOR) // East ossuary
        fillRect(g, 14, 6, 34, 17, TileType.CRYPT_FLOOR)  // Northern sanctum

        // Crypt pillars
        listOf(19 to 35, 29 to 35, 13 to 22, 13 to 26, 35 to 22, 35 to 26, 19 to 11, 29 to 11).forEach { (px, py) ->
            g[py][px] = TileType.STONE_WALL
        }

        // Bogstone Veins & Brimstone Seams
        listOf(9 to 20, 9 to 27, 39 to 19).forEach { (x, y) -> g[y][x] = TileType.ORE_BOGSTONE }
        listOf(15 to 8, 33 to 8, 41 to 27, 17 to 39).forEach { (x, y) -> g[y][x] = TileType.ORE_BRIMSTONE }

        // Torches
        g[41][24] = TileType.TORCH_WALL
        g[6][24] = TileType.TORCH_WALL
        g[18][14] = TileType.TORCH_WALL
        g[18][34] = TileType.TORCH_WALL

        // Portals
        g[38][24] = TileType.ROPE_SPOT_UP
        g[24][10] = TileType.STAIRS_UP // Direct return stairway up to NE Crag Hills on Floor 0!
        g[8][24] = TileType.STAIRS_DOWN
        g[24][40] = TileType.ROPE_HOLE_DOWN

        return WorldFloor(
            floorId = -2,
            name = "Deep Caverns",
            subtitle = "Dungeon Floor -2",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            dangerLevel = "High",
            recommendedLevel = "Lvl 6-10",
            dangerSeverity = 2
        )
    }

    private fun buildForgehallFloor(): WorldFloor {
        val s = 48
        val g = createBlank(s, s, TileType.CAVE_WALL)

        // Outer forgehall corridors
        fillRect(g, 8, 6, 41, 16, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 8, 16, 17, 37, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 31, 16, 42, 37, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 14, 30, 34, 42, TileType.OBSIDIAN_FLOOR)

        // Central Unprotected Deep Forge Outpost (NO Protection Zone — tense risk/reward!)
        fillRect(g, 19, 17, 29, 27, TileType.COBBLESTONE)
        fillRect(g, 17, 21, 31, 23, TileType.COBBLESTONE)
        fillRect(g, 23, 15, 25, 30, TileType.COBBLESTONE)
        g[20][22] = TileType.SMELTING_FURNACE
        g[20][26] = TileType.BLACKSMITH_ANVIL
        g[23][24] = TileType.MINI_DEPOT
        g[18][21] = TileType.TORCH_WALL
        g[18][27] = TileType.TORCH_WALL

        // Brimstone Seams & Deep Aetherium Lodes
        listOf(10 to 10, 39 to 10, 10 to 28, 39 to 30).forEach { (x, y) -> g[y][x] = TileType.ORE_BRIMSTONE }
        listOf(9 to 35, 38 to 35, 16 to 40, 32 to 40).forEach { (x, y) -> g[y][x] = TileType.ORE_AETHERIUM }

        // Portals
        g[8][24] = TileType.STAIRS_UP
        g[24][40] = TileType.ROPE_SPOT_UP
        g[40][24] = TileType.STAIRS_DOWN
        g[34][10] = TileType.ROPE_HOLE_DOWN

        return WorldFloor(
            floorId = -3,
            name = "Abyssal Crypt",
            subtitle = "Dungeon Floor -3 • Deep Outpost",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            dangerLevel = "Extreme",
            recommendedLevel = "Lvl 10-18",
            dangerSeverity = 3
        )
    }

    private fun buildWyrmSanctumFloor(): WorldFloor {
        val s = 48
        val g = createBlank(s, s, TileType.CAVE_WALL)

        // Volcanic lava caldera background
        fillRect(g, 4, 4, 43, 43, TileType.LAVA)

        // Southern entry bastion & western rope cliff
        fillRect(g, 16, 34, 32, 42, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 8, 28, 18, 37, TileType.OBSIDIAN_FLOOR)
        // Twin volcanic bridges
        fillRect(g, 12, 20, 17, 30, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 31, 20, 36, 35, TileType.OBSIDIAN_FLOOR)
        fillRect(g, 22, 24, 26, 34, TileType.OBSIDIAN_FLOOR)
        // Grand Ancient Dragon Sanctum
        fillRect(g, 11, 9, 37, 23, TileType.OBSIDIAN_FLOOR)

        // Obsidian cover pillars for dodging Dragon fire breath
        listOf(17 to 13, 31 to 13, 17 to 19, 31 to 19).forEach { (px, py) -> g[py][px] = TileType.STONE_WALL }

        // Rich Aetherium Lodes guarded by Wyrms & the Ancient Dragon
        listOf(13 to 10, 35 to 10, 12 to 22, 36 to 22, 24 to 9).forEach { (x, y) -> g[y][x] = TileType.ORE_AETHERIUM }

        // Portals
        g[40][24] = TileType.STAIRS_UP
        g[34][10] = TileType.ROPE_SPOT_UP

        return WorldFloor(
            floorId = -4,
            name = "Obsidian Wyrm Sanctum",
            subtitle = "Dungeon Floor -4 • Endgame Boss Lair",
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            dangerLevel = "Deadly",
            recommendedLevel = "Lvl 16-20+",
            dangerSeverity = 4
        )
    }
}
