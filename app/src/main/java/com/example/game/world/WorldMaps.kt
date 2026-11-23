package com.example.game.world

import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.OreNodeState
import com.example.game.model.TileType
import com.example.game.model.V1_SURFACE_OFFSET

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
    val tiles: Array<Array<TileType>>,
    val isProtectionZone: (Int, Int) -> Boolean = { _, _ -> false },
    val dangerLevel: String = "Low",
    val recommendedLevel: String = "Lvl 1-3",
    val dangerSeverity: Int = 0
) {
    fun tileAt(x: Int, y: Int): TileType {
        if (x !in 0 until width || y !in 0 until height) return TileType.STONE_WALL
        return tiles[y][x]
    }
}

object WorldMaps {
    // Anchored at V1 (15, 14) + (32, 32) so V1 saves migrate seamlessly onto the exact Temple tile
    const val TEMPLE_SPAWN_X = 15 + V1_SURFACE_OFFSET // 47
    const val TEMPLE_SPAWN_Y = 14 + V1_SURFACE_OFFSET // 46
    const val ORACLE_X = 17 + V1_SURFACE_OFFSET // 49
    const val ORACLE_Y = 11 + V1_SURFACE_OFFSET // 43

    val npcs: List<NpcSpot> = listOf(
        NpcSpot(0, 22 + V1_SURFACE_OFFSET, 13 + V1_SURFACE_OFFSET, "Alaric", NpcRole.SHOP_ALCHEMIST),
        NpcSpot(0, 15 + V1_SURFACE_OFFSET, 11 + V1_SURFACE_OFFSET, "Brother Alden", NpcRole.TEMPLE_PRIEST),
        NpcSpot(0, ORACLE_X, ORACLE_Y, "The Oracle", NpcRole.ORACLE)
    )

    val landmarks: List<LandmarkLabel> = listOf(
        LandmarkLabel(0, 47, 42, "TEMPLE"),
        LandmarkLabel(0, 40, 43, "DEPOT"),
        LandmarkLabel(0, 54, 43, "ALARIC'S SHOP"),
        LandmarkLabel(0, 47, 36, "SMITHY & FLETCHER"),
        LandmarkLabel(0, 47, 52, "FISHING PIER"),
        LandmarkLabel(0, 56, 38, "SEWER STAIRS"),
        LandmarkLabel(0, 40, 38, "ROPE HOLE"),
        LandmarkLabel(0, 18, 16, "QUARRY & FOOTHILLS"),
        LandmarkLabel(0, 76, 12, "MOUNTAIN CAVERN"),
        LandmarkLabel(0, 78, 46, "DARK FOREST"),
        LandmarkLabel(-3, 24, 18, "DEEP FORGE OUTPOST")
    )

    val portals: List<FloorPortal> = listOf(
        // Floor 0 Rookvale NE Gate <-> Floor -1
        FloorPortal(0, 56, 39, -1, 28, 11, requiresRope = false),
        FloorPortal(-1, 28, 10, 0, 56, 40, requiresRope = false),
        // Floor 0 Rookvale NW Rope-Hole <-> Floor -1
        FloorPortal(0, 40, 39, -1, 10, 11, requiresRope = false),
        FloorPortal(-1, 10, 10, 0, 40, 40, requiresRope = true),
        // Floor 0 Quarry Rope-Hole <-> Floor -1
        FloorPortal(0, 18, 22, -1, 14, 15, requiresRope = false),
        FloorPortal(-1, 14, 14, 0, 18, 23, requiresRope = true),
        // Floor 0 Mountain Cavern Stairs <-> Floor -1
        FloorPortal(0, 76, 14, -1, 38, 11, requiresRope = false),
        FloorPortal(-1, 38, 10, 0, 76, 15, requiresRope = false),
        // Floor 0 Dark Forest Rope-Hole <-> Floor -1
        FloorPortal(0, 79, 52, -1, 38, 29, requiresRope = false),
        FloorPortal(-1, 38, 28, 0, 79, 53, requiresRope = true),

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
        // --- Floor 0 Town Outskirts (TIER 1 — Levels 1-3, safely outside Rookvale City PZ) ---
        // West Gate Outskirts
        MonsterSpawn(0, 29, 45, MonsterType.WILD_RABBIT),
        MonsterSpawn(0, 27, 42, MonsterType.CAVE_RAT),
        MonsterSpawn(0, 25, 48, MonsterType.YOUNG_SNAKE),
        MonsterSpawn(0, 22, 42, MonsterType.CAVE_RAT),
        MonsterSpawn(0, 19, 39, MonsterType.CRAG_WOLF),
        // North Gate Outskirts
        MonsterSpawn(0, 47, 29, MonsterType.WILD_RABBIT),
        MonsterSpawn(0, 50, 27, MonsterType.YOUNG_SNAKE),
        MonsterSpawn(0, 48, 24, MonsterType.CRAG_WOLF),
        // East Gate Outskirts
        MonsterSpawn(0, 66, 46, MonsterType.WILD_RABBIT),
        MonsterSpawn(0, 68, 43, MonsterType.YOUNG_SNAKE),
        MonsterSpawn(0, 71, 46, MonsterType.CRAG_WOLF),

        // --- Floor 0 Outer Wilds (TIER 2 — Levels 3-6, far Quarry, Deep Dark Forest & Mountain Pass) ---
        // Far NW Quarry & Foothills
        MonsterSpawn(0, 14, 28, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(0, 23, 24, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, 12, 16, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(0, 25, 14, MonsterType.SKELETON),
        // Deep East Dark Forest
        MonsterSpawn(0, 78, 46, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(0, 74, 54, MonsterType.POISON_SPIDER),
        MonsterSpawn(0, 84, 56, MonsterType.POISON_SPIDER),
        MonsterSpawn(0, 70, 66, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, 82, 70, MonsterType.POISON_SPIDER),
        // NE Mountain Cavern Pass
        MonsterSpawn(0, 66, 22, MonsterType.GOBLIN_SCOUT),
        MonsterSpawn(0, 72, 19, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, 82, 18, MonsterType.SWAMP_TROLL),
        MonsterSpawn(0, 68, 12, MonsterType.SKELETON),

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
                    val kind = fl.tiles[y][x].oreVeinKind()
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
        val h = g.size
        val w = g[0].size
        for (y in y1.coerceAtLeast(0)..y2.coerceAtMost(h - 1)) {
            for (x in x1.coerceAtLeast(0)..x2.coerceAtMost(w - 1)) {
                g[y][x] = tile
            }
        }
    }

    private fun strokeRect(g: Array<Array<TileType>>, x1: Int, y1: Int, x2: Int, y2: Int, tile: TileType) {
        val h = g.size
        val w = g[0].size
        val cx1 = x1.coerceIn(0, w - 1)
        val cx2 = x2.coerceIn(0, w - 1)
        val cy1 = y1.coerceIn(0, h - 1)
        val cy2 = y2.coerceIn(0, h - 1)
        for (x in cx1..cx2) {
            g[cy1][x] = tile
            g[cy2][x] = tile
        }
        for (y in cy1..cy2) {
            g[y][cx1] = tile
            g[y][cx2] = tile
        }
    }

    private fun buildOverlandSurface(): WorldFloor {
        val w = 96
        val h = 96
        val g = createBlank(w, h, TileType.GRASS)

        // Outer impenetrable mountain border
        strokeRect(g, 0, 0, w - 1, h - 1, TileType.MOUNTAIN_ROCK)
        strokeRect(g, 1, 1, w - 2, h - 2, TileType.MOUNTAIN_ROCK)

        // --- 1. SOUTHERN RIVER & LAKE ---
        fillRect(g, 2, 54, 62, 62, TileType.WATER)
        fillRect(g, 2, 62, 62, 93, TileType.GRASS)
        // Southern woodland riverbank trees
        for (x in 6..58 step 5) {
            g[66][x] = TileType.TREE
            g[74][(x + 2) % 56 + 4] = TileType.TREE
            g[84][x] = TileType.TREE
        }

        // --- 2. NW QUARRY & FOOTHILLS (x=3..31, y=4..52) ---
        fillRect(g, 4, 5, 31, 51, TileType.DIRT_FLOOR)
        // Rocky quarry terraces & switchbacks
        fillRect(g, 5, 5, 30, 8, TileType.MOUNTAIN_ROCK)
        fillRect(g, 4, 19, 10, 21, TileType.MOUNTAIN_ROCK)
        fillRect(g, 22, 18, 28, 20, TileType.MOUNTAIN_ROCK)
        fillRect(g, 8, 34, 15, 36, TileType.MOUNTAIN_ROCK)
        // Ochre Veins (Tier 1) & Bogstone Veins (Tier 2) in the Quarry
        val quarryOchre = listOf(8 to 11, 11 to 10, 15 to 12, 22 to 11, 9 to 25, 13 to 30, 25 to 30, 20 to 44, 12 to 46)
        quarryOchre.forEach { (ox, oy) -> g[oy][ox] = TileType.ORE_OCHRE }
        val quarryBogstone = listOf(7 to 14, 16 to 17, 26 to 16, 7 to 29)
        quarryBogstone.forEach { (bx, by) -> g[by][bx] = TileType.ORE_BOGSTONE }
        // Quarry Rope-Hole shortcut to Floor -1
        g[22][18] = TileType.ROPE_HOLE_DOWN

        // --- 3. NE MOUNTAIN CAVERN ENTRANCE (x=54..92, y=4..31) ---
        fillRect(g, 54, 4, 92, 31, TileType.MOUNTAIN_ROCK)
        // Carve mountain pass from Rookvale North Gate -> Cavern Gatehouse
        fillRect(g, 45, 22, 56, 33, TileType.DIRT_FLOOR)
        fillRect(g, 56, 16, 86, 25, TileType.DIRT_FLOOR)
        fillRect(g, 64, 9, 86, 17, TileType.DIRT_FLOOR)
        // Mountain Bogstone & Brimstone Seams
        val mountainBog = listOf(60 to 17, 65 to 24, 83 to 22)
        mountainBog.forEach { (mx, my) -> g[my][mx] = TileType.ORE_BOGSTONE }
        val mountainBrim = listOf(66 to 10, 72 to 10, 82 to 11, 85 to 16)
        mountainBrim.forEach { (bx, by) -> g[by][bx] = TileType.ORE_BRIMSTONE }
        // Grand Mountain Cavern Stairway -> Floor -1
        g[13][76] = TileType.TORCH_WALL
        g[14][76] = TileType.STAIRS_DOWN

        // --- 4. EAST DARK FOREST (x=64..93, y=32..91) ---
        fillRect(g, 64, 32, 93, 91, TileType.DARK_GRASS)
        // Winding dirt forest paths
        fillRect(g, 63, 45, 86, 48, TileType.DIRT_FLOOR)
        fillRect(g, 76, 46, 81, 76, TileType.DIRT_FLOOR)
        fillRect(g, 68, 62, 86, 66, TileType.DIRT_FLOOR)
        // Dense oblique pine canopy clusters
        for (y in 34..88 step 4) {
            for (x in 66..91 step 5) {
                val tx = x + ((y / 4) % 2) * 2
                if (tx in 65..92 && g[y][tx] == TileType.DARK_GRASS) {
                    g[y][tx] = TileType.TREE
                }
            }
        }
        // Hidden Dark Forest Rope-Hole -> Floor -1
        fillRect(g, 77, 50, 81, 54, TileType.DIRT_FLOOR)
        g[52][79] = TileType.ROPE_HOLE_DOWN

        // --- 5. ROOKVALE CITY CORE (anchored at offset +32, +32 -> x=32..63, y=32..63) ---
        val ox = V1_SURFACE_OFFSET
        val oy = V1_SURFACE_OFFSET
        strokeRect(g, ox, oy, ox + 31, oy + 31, TileType.CITY_WALL)
        strokeRect(g, ox + 1, oy + 1, ox + 30, oy + 30, TileType.CITY_WALL)
        fillRect(g, ox + 2, oy + 2, ox + 29, oy + 29, TileType.GRASS)
        fillRect(g, ox + 6, oy + 6, ox + 26, oy + 20, TileType.COBBLESTONE)

        // Open City Gates connecting seamlessly to Quarry (West), Mountains (North), Dark Forest (East)
        fillRect(g, ox, oy + 13, ox + 6, oy + 15, TileType.COBBLESTONE) // West Gate -> Quarry
        fillRect(g, 28, oy + 13, ox, oy + 15, TileType.DIRT_FLOOR)
        fillRect(g, ox + 14, oy, ox + 16, oy + 6, TileType.COBBLESTONE) // North Gate -> Mountains
        fillRect(g, ox + 26, oy + 13, ox + 31, oy + 15, TileType.COBBLESTONE) // East Gate -> Dark Forest

        // River & Fishing Pier inside southern Rookvale
        fillRect(g, ox + 2, oy + 22, ox + 29, oy + 29, TileType.WATER)
        fillRect(g, ox + 14, oy + 20, ox + 17, oy + 26, TileType.WOOD_PIER)
        fillRect(g, ox + 12, oy + 24, ox + 19, oy + 25, TileType.WOOD_PIER)

        // Temple Sanctuary (Center)
        strokeRect(g, ox + 12, oy + 10, ox + 18, oy + 16, TileType.CITY_WALL)
        fillRect(g, ox + 13, oy + 11, ox + 17, oy + 15, TileType.TEMPLE_FLOOR)
        g[oy + 16][ox + 15] = TileType.TEMPLE_FLOOR
        g[oy + 13][ox + 12] = TileType.TEMPLE_FLOOR
        g[oy + 13][ox + 18] = TileType.TEMPLE_FLOOR
        g[oy + 12][ox + 15] = TileType.TEMPLE_ALTAR
        g[oy + 10][ox + 13] = TileType.TORCH_WALL
        g[oy + 10][ox + 17] = TileType.TORCH_WALL

        // Smithy & Fletching Courtyard (Just North of Temple: Smelting Furnaces, Blacksmith Anvils & Fletching Workbench)
        fillRect(g, ox + 12, oy + 4, ox + 18, oy + 8, TileType.COBBLESTONE)
        g[oy + 5][ox + 13] = TileType.SMELTING_FURNACE
        g[oy + 5][ox + 17] = TileType.SMELTING_FURNACE
        g[oy + 7][ox + 13] = TileType.BLACKSMITH_ANVIL
        g[oy + 7][ox + 17] = TileType.BLACKSMITH_ANVIL
        g[oy + 6][ox + 12] = TileType.FLETCHING_BENCH
        g[oy + 6][ox + 18] = TileType.FLETCHING_BENCH

        // Accessible overland timber trees right outside West, North & East town gates for Hatchet harvesting
        listOf(30 to 40, 30 to 49, 43 to 29, 52 to 29, 65 to 41, 65 to 50).forEach { (tx, ty) ->
            if (g[ty][tx] == TileType.GRASS || g[ty][tx] == TileType.DARK_GRASS || g[ty][tx] == TileType.DIRT_FLOOR) {
                g[ty][tx] = TileType.TREE
            }
        }

        // Depot Hall (West)
        strokeRect(g, ox + 6, oy + 11, ox + 10, oy + 17, TileType.CITY_WALL)
        fillRect(g, ox + 7, oy + 12, ox + 9, oy + 16, TileType.COBBLESTONE)
        g[oy + 14][ox + 10] = TileType.COBBLESTONE
        g[oy + 12][ox + 7] = TileType.DEPOT_CHEST
        g[oy + 16][ox + 7] = TileType.DEPOT_CHEST
        g[oy + 11][ox + 8] = TileType.TORCH_WALL

        // Alaric's Supplies Shop (East)
        strokeRect(g, ox + 20, oy + 11, ox + 25, oy + 16, TileType.CITY_WALL)
        fillRect(g, ox + 21, oy + 12, ox + 24, oy + 15, TileType.COBBLESTONE)
        g[oy + 14][ox + 20] = TileType.COBBLESTONE
        g[oy + 13][ox + 23] = TileType.SHOP_COUNTER
        g[oy + 12][ox + 23] = TileType.SHOP_COUNTER
        g[oy + 11][ox + 22] = TileType.TORCH_WALL

        // City NE Dungeon Gatehouse Stairs
        strokeRect(g, ox + 22, oy + 5, ox + 26, oy + 9, TileType.STONE_WALL)
        fillRect(g, ox + 23, oy + 6, ox + 25, oy + 8, TileType.DIRT_FLOOR)
        g[oy + 9][ox + 24] = TileType.COBBLESTONE
        g[oy + 5][ox + 24] = TileType.TORCH_WALL
        g[oy + 7][ox + 24] = TileType.STAIRS_DOWN

        // City NW Cave Rope-Hole
        fillRect(g, ox + 6, oy + 5, ox + 10, oy + 9, TileType.DIRT_FLOOR)
        g[oy + 7][ox + 8] = TileType.ROPE_HOLE_DOWN

        return WorldFloor(
            floorId = 0,
            name = "Rookvale Overland",
            subtitle = "Surface • Safe Town PZ",
            width = w,
            height = h,
            tiles = g,
            // Temple & entire walled Rookvale City perimeter remain a safe Protection Zone
            isProtectionZone = { x, y -> x in (ox + 2)..(ox + 29) && y in (oy + 2)..(oy + 29) },
            dangerLevel = "Low (Outskirts)",
            recommendedLevel = "Lvl 1-6",
            dangerSeverity = 0
        )
    }

    private fun buildSewersFloor(): WorldFloor {
        val s = 48
        val g = createBlank(s, s, TileType.CAVE_WALL)

        // Connected sewer & mine shaft chambers
        fillRect(g, 8, 8, 41, 16, TileType.DIRT_FLOOR)
        fillRect(g, 10, 16, 22, 34, TileType.DIRT_FLOOR)
        fillRect(g, 26, 16, 41, 32, TileType.DIRT_FLOOR)
        fillRect(g, 16, 22, 32, 27, TileType.DIRT_FLOOR)
        fillRect(g, 18, 32, 30, 41, TileType.DIRT_FLOOR)

        // Pillars for kiting
        listOf(16 to 12, 32 to 12, 16 to 26, 32 to 24, 24 to 34).forEach { (px, py) -> g[py][px] = TileType.CAVE_WALL }

        // Underground Ochre & Bogstone Veins
        listOf(11 to 19, 13 to 29, 36 to 18, 40 to 22).forEach { (x, y) -> g[y][x] = TileType.ORE_OCHRE }
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
            tiles = g,
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
        g[8][24] = TileType.STAIRS_DOWN
        g[24][40] = TileType.ROPE_HOLE_DOWN

        return WorldFloor(
            floorId = -2,
            name = "Deep Caverns",
            subtitle = "Dungeon Floor -2",
            width = s,
            height = s,
            tiles = g,
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
        fillRect(g, 17, 21, 31, 23, TileType.COBBLESTONE) // East/West entryways
        fillRect(g, 23, 15, 25, 30, TileType.COBBLESTONE) // North/South entryways
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
            tiles = g,
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
            tiles = g,
            dangerLevel = "Deadly",
            recommendedLevel = "Lvl 16-20+",
            dangerSeverity = 4
        )
    }
}
