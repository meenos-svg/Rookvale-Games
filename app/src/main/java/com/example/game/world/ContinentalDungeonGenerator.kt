package com.example.game.world

import com.example.game.model.MonsterType
import com.example.game.model.TileType

/**
 * Specification for a deterministic outer-biome multi-room continental dungeon floor.
 */
data class ContinentalDungeonSpec(
    val floorId: Int,
    val name: String,
    val subtitle: String,
    val hostBiome: BiomeType,
    val surfaceTier: OverlandBiomeTier,
    val dangerLevel: String,
    val dangerSeverity: Int,
    val entrySurfaceX: Int,
    val entrySurfaceY: Int,
    val campName: String,
    val healerName: String,
    val merchantName: String,
    val bossType: MonsterType,
    val bossLandmarkName: String,
    val wallTile: TileType,
    val floorTile: TileType,
    val accentFloorTile: TileType,
    val hazardTile: TileType,
    val bridgeTile: TileType,
    val stallTile: TileType,
    val primaryOre: TileType,
    val secondaryOre: TileType,
    val seed: Int
)

/**
 * Fully generated deterministic dungeon floor package (map grid, forward camp PZ,
 * biome-driven monster patrols + boss, boss landmark, and return portal coordinates).
 */
data class GeneratedContinentalDungeon(
    val spec: ContinentalDungeonSpec,
    val floor: WorldFloor,
    val camp: UndergroundCampZone,
    val spawns: List<MonsterSpawn>,
    val bossLandmark: LandmarkLabel,
    val stairsUpX: Int,
    val stairsUpY: Int,
    val landingX: Int,
    val landingY: Int
)

/**
 * Procedural deterministic multi-room & corridor dungeon generator for Rookvale's
 * expanded outer-continent biomes (Floors -7 through -10).
 *
 * Guarantees:
 * - Safe Southern Forward Camp Protection Zone with Healer, Supply Alchemist & Biome Stall
 * - Interconnected multi-chamber layout with narrow 2–3 tile corridors & iron-barred gates
 * - Subterranean hazard chasm crossed by stone/wood bridges leading into a Boss Sanctum
 * - Biome-appropriate mining veins tucked in guarded alcoves
 * - Biome-driven regular patrols (via [BiomeMonsterSpawner]) + Boss encounter with 100% reachability
 * - Purely deterministic & session-based (never serialized into Supabase `save_data`)
 */
object ContinentalDungeonGenerator {

    private const val GRID_SIZE = 48
    private const val STAIRS_UP_X = 24
    private const val STAIRS_UP_Y = 41
    private const val LANDING_X = 24
    private const val LANDING_Y = 40
    private const val BOSS_X = 24
    private const val BOSS_Y = 11

    val specs: List<ContinentalDungeonSpec> = listOf(
        ContinentalDungeonSpec(
            floorId = -7,
            name = "The Sunken Mire Vaults",
            subtitle = "Whispering Marsh Dungeon • Floor -7 • Forward Camp PZ",
            hostBiome = BiomeType.WHISPERING_MARSH,
            surfaceTier = OverlandBiomeTier.WHISPERING_MARSH,
            dangerLevel = "Moderate",
            dangerSeverity = 2,
            entrySurfaceX = 295,
            entrySurfaceY = 515,
            campName = "MIRE VAULTS FORWARD CAMP (PZ)",
            healerName = "Mire Priestess Lyra",
            merchantName = "Bog Supplier Torin",
            bossType = MonsterType.BROODMOTHER_ARACHNE,
            bossLandmarkName = "MIRE MATRIARCH'S HOLLOW",
            wallTile = TileType.CAVE_WALL,
            floorTile = TileType.DIRT_FLOOR,
            accentFloorTile = TileType.CRYPT_FLOOR,
            hazardTile = TileType.WATER,
            bridgeTile = TileType.WOOD_BRIDGE,
            stallTile = TileType.BIOME_STALL_MARSH,
            primaryOre = TileType.ORE_MIRE_SILVER,
            secondaryOre = TileType.ORE_PEAT_COAL,
            seed = 701
        ),
        ContinentalDungeonSpec(
            floorId = -8,
            name = "The Overgrown Ziggurat Vault",
            subtitle = "Emerald Jungle Dungeon • Floor -8 • Forward Camp PZ",
            hostBiome = BiomeType.EMERALD_JUNGLE,
            surfaceTier = OverlandBiomeTier.WHISPERING_MARSH,
            dangerLevel = "High",
            dangerSeverity = 2,
            entrySurfaceX = 720,
            entrySurfaceY = 1020,
            campName = "ZIGGURAT VAULT CAMP (PZ)",
            healerName = "Sunleaf Cleric Sera",
            merchantName = "Expeditionist Kael",
            bossType = MonsterType.IGNIS_LORD_OF_THE_PIT,
            bossLandmarkName = "ZIGGURAT SUN-PYRE SANCTUM",
            wallTile = TileType.STONE_WALL,
            floorTile = TileType.CRYPT_FLOOR,
            accentFloorTile = TileType.DARK_GRASS,
            hazardTile = TileType.WATER,
            bridgeTile = TileType.STONE_BRIDGE,
            stallTile = TileType.BIOME_STALL_MARSH,
            primaryOre = TileType.ORE_MIRE_SILVER,
            secondaryOre = TileType.ORE_VESPERITE,
            seed = 809
        ),
        ContinentalDungeonSpec(
            floorId = -9,
            name = "The Blightwood Barrow Catacombs",
            subtitle = "Blightwood Frontier Dungeon • Floor -9 • Forward Camp PZ",
            hostBiome = BiomeType.BLIGHTWOOD_REACH,
            surfaceTier = OverlandBiomeTier.FROSTPEAK_HIGHLANDS,
            dangerLevel = "Very High",
            dangerSeverity = 3,
            entrySurfaceX = 1020,
            entrySurfaceY = 175,
            campName = "BARROW CATACOMB CAMP (PZ)",
            healerName = "Frost Cleric Eirik",
            merchantName = "Shadow Quartermaster Vex",
            bossType = MonsterType.DEMON_LORD,
            bossLandmarkName = "BARROW ARCHFIEND CRYPT",
            wallTile = TileType.GRANITE_WALL,
            floorTile = TileType.CRYPT_FLOOR,
            accentFloorTile = TileType.OBSIDIAN_FLOOR,
            hazardTile = TileType.WATER,
            bridgeTile = TileType.STONE_BRIDGE,
            stallTile = TileType.BIOME_STALL_FROSTPEAK,
            primaryOre = TileType.ORE_GLACIER_QUARTZ,
            secondaryOre = TileType.ORE_VESPERITE,
            seed = 919
        ),
        ContinentalDungeonSpec(
            floorId = -10,
            name = "The Ashen Caldera Foundry",
            subtitle = "Ashen Caldera Dungeon • Floor -10 • Vanguard Camp PZ",
            hostBiome = BiomeType.ASHEN_CALDERA,
            surfaceTier = OverlandBiomeTier.ASHEN_CALDERA,
            dangerLevel = "Extreme",
            dangerSeverity = 4,
            entrySurfaceX = 318,
            entrySurfaceY = 252,
            campName = "CALDERA FOUNDRY CAMP (PZ)",
            healerName = "Ashen Cleric Solon",
            merchantName = "Forge Supplier Draven",
            bossType = MonsterType.ELDER_DRAGON,
            bossLandmarkName = "CALDERA WYRM FOUNDRY",
            wallTile = TileType.OBSIDIAN_WALL,
            floorTile = TileType.OBSIDIAN_FLOOR,
            accentFloorTile = TileType.CRYPT_FLOOR,
            hazardTile = TileType.LAVA,
            bridgeTile = TileType.STONE_BRIDGE,
            stallTile = TileType.BIOME_STALL_ASHEN,
            primaryOre = TileType.ORE_CINDERITE,
            secondaryOre = TileType.ORE_VESPERITE,
            seed = 1031
        )
    )

    val generatedDungeons: List<GeneratedContinentalDungeon> by lazy {
        specs.map { generate(it) }
    }

    val byFloorId: Map<Int, GeneratedContinentalDungeon> by lazy {
        generatedDungeons.associateBy { it.spec.floorId }
    }

    fun generate(spec: ContinentalDungeonSpec): GeneratedContinentalDungeon {
        val s = GRID_SIZE
        val g = Array(s) { Array(s) { spec.wallTile } }

        fun fillRect(x1: Int, y1: Int, x2: Int, y2: Int, tile: TileType) {
            for (y in y1.coerceAtLeast(1)..y2.coerceAtMost(s - 2)) {
                for (x in x1.coerceAtLeast(1)..x2.coerceAtMost(s - 2)) {
                    g[y][x] = tile
                }
            }
        }

        val j1 = (hash(spec.seed, 1) % 3) - 1 // -1..1 subtle chamber variation
        val j2 = (hash(spec.seed, 2) % 3) - 1

        // 1. Southern Safe Forward Camp PZ (x=20..28, y=37..42)
        val camp = UndergroundCampZone(
            floorId = spec.floorId,
            name = spec.campName,
            minX = 20,
            minY = 37,
            maxX = 28,
            maxY = 42,
            healerX = 21,
            healerY = 40,
            healerName = spec.healerName,
            merchantX = 27,
            merchantY = 40,
            merchantName = spec.merchantName
        )
        fillRect(camp.minX, camp.minY, camp.maxX, camp.maxY, spec.accentFloorTile)

        // 2. Entry Gatehouse Corridor (narrow 3-tile north exit with Iron-Barred Gate)
        fillRect(23, 32, 25, 36, spec.floorTile)
        g[36][23] = TileType.IRON_BAR_GATE
        g[36][24] = TileType.IRON_BAR_GATE
        g[36][25] = TileType.IRON_BAR_GATE

        // 3. Lower Branching Corridors & Flanking Guard Chambers (SW & SE)
        fillRect(10, 33, 22, 34, spec.floorTile)
        fillRect(26, 33, 38, 34, spec.floorTile)
        fillRect(8 + j1, 29, 16, 36, spec.accentFloorTile)  // SW Chamber
        fillRect(32, 29, 40 + j2, 36, spec.accentFloorTile) // SE Chamber

        // 4. Central Crossroads Chamber & Winding North-South Passages
        fillRect(11, 22, 13, 28, spec.floorTile) // West Wing Corridor
        fillRect(35, 22, 37, 28, spec.floorTile) // East Wing Corridor
        fillRect(14, 24, 19, 26, spec.floorTile) // West Cross-Passage
        fillRect(29, 24, 34, 26, spec.floorTile) // East Cross-Passage
        fillRect(20, 22, 28, 28, spec.accentFloorTile) // Central Chamber
        fillRect(23, 29, 25, 31, spec.floorTile) // Center-South Link
        // Decorative pillars inside central chamber
        listOf(21 to 23, 27 to 23, 21 to 27, 27 to 27).forEach { (px, py) ->
            g[py][px] = spec.wallTile
        }
        // Iron-barred chokepoint entering central chamber
        g[30][23] = TileType.IRON_BAR_GATE
        g[30][24] = TileType.IRON_BAR_GATE
        g[30][25] = TileType.IRON_BAR_GATE

        // 5. Subterranean Hazard Chasm (y=18..21) + Triple Bridges (West, Center, East)
        fillRect(10, 18, 38, 21, spec.hazardTile)
        fillRect(23, 18, 25, 21, spec.bridgeTile)
        fillRect(11, 18, 13, 21, spec.bridgeTile)
        fillRect(35, 18, 37, 21, spec.bridgeTile)

        // Northern Flanking Alcoves (ore vaults + side corridors)
        fillRect(9, 12, 15, 17, spec.floorTile)
        fillRect(33, 12, 39, 17, spec.floorTile)
        fillRect(16, 14, 17, 15, spec.floorTile)
        fillRect(31, 14, 32, 15, spec.floorTile)

        // 6. Boss Sanctum Chamber (x=17..31, y=7..16)
        fillRect(17, 7, 31, 16, spec.accentFloorTile)
        g[17][23] = TileType.IRON_BAR_GATE
        g[17][24] = TileType.IRON_BAR_GATE
        g[17][25] = TileType.IRON_BAR_GATE
        // Corner hazard pools & pillars inside Boss Sanctum
        g[8][18] = spec.hazardTile; g[8][30] = spec.hazardTile
        listOf(20 to 10, 28 to 10, 20 to 14, 28 to 14).forEach { (px, py) ->
            g[py][px] = spec.wallTile
        }

        // 7. Biome-Appropriate Mining Veins tucked in guarded chamber alcoves
        listOf(
            9 to 30, 39 to 30, 10 to 13, 38 to 13
        ).forEach { (vx, vy) ->
            if (g[vy][vx].walkable) g[vy][vx] = spec.primaryOre
        }
        listOf(
            9 to 35, 39 to 35, 19 to 8, 29 to 8
        ).forEach { (vx, vy) ->
            if (g[vy][vx].walkable) g[vy][vx] = spec.secondaryOre
        }

        // 8. Flickering Wall Torches along corridors & sanctum walls
        listOf(
            24 to 43, 22 to 36, 26 to 36, 14 to 28, 34 to 28,
            19 to 25, 29 to 25, 22 to 17, 26 to 17, 16 to 11, 32 to 11, 24 to 6
        ).forEach { (tx, ty) -> g[ty][tx] = TileType.TORCH_WALL }

        // 9. Safe Forward Camp Fixtures (Campfires, Banners, Biome Crafting Stall, Return Stairs)
        g[38][21] = TileType.CAMPFIRE_BRAZIER
        g[38][27] = TileType.CAMPFIRE_BRAZIER
        g[41][21] = TileType.WAR_BANNER
        g[41][27] = TileType.WAR_BANNER
        g[40][21] = TileType.COBBLESTONE
        g[40][27] = TileType.COBBLESTONE
        g[38][24] = spec.stallTile
        g[LANDING_Y][LANDING_X] = TileType.COBBLESTONE
        g[STAIRS_UP_Y][STAIRS_UP_X] = TileType.STAIRS_UP

        val floor = WorldFloor(
            floorId = spec.floorId,
            name = spec.name,
            subtitle = spec.subtitle,
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            isProtectionZone = { x, y -> camp.contains(x, y) },
            dangerLevel = spec.dangerLevel,
            recommendedLevel = BiomeProgressionRules.dungeonRecommendedLevelLabel(spec.floorId),
            dangerSeverity = spec.dangerSeverity
        )

        // 10. Biome-Driven Patrol Spawns across Chambers + Terminal Boss Encounter
        val patrolSlots = listOf(
            12 to 33, // SW Chamber
            14 to 31, // SW Chamber North
            36 to 33, // SE Chamber
            34 to 31, // SE Chamber North
            12 to 25, // West Corridor
            36 to 25, // East Corridor
            24 to 25, // Central Crossroads
            12 to 15, // NW Alcove Guard
            36 to 15  // NE Alcove Guard
        )
        val spawns = buildList {
            patrolSlots.forEachIndexed { idx, (px, py) ->
                if (!camp.contains(px, py) && floor.tileAt(px, py).walkable) {
                    val species = BiomeMonsterSpawner.pickWeightedMonster(
                        biome = spec.hostBiome,
                        seed = hash(spec.seed + idx * 37, px * 31 + py)
                    )
                    add(MonsterSpawn(spec.floorId, px, py, species))
                }
            }
            add(MonsterSpawn(spec.floorId, BOSS_X, BOSS_Y, spec.bossType))
        }

        val bossLandmark = LandmarkLabel(spec.floorId, BOSS_X, BOSS_Y, spec.bossLandmarkName)

        return GeneratedContinentalDungeon(
            spec = spec,
            floor = floor,
            camp = camp,
            spawns = spawns,
            bossLandmark = bossLandmark,
            stairsUpX = STAIRS_UP_X,
            stairsUpY = STAIRS_UP_Y,
            landingX = LANDING_X,
            landingY = LANDING_Y
        )
    }

    private fun hash(a: Int, b: Int): Int {
        var h = a * 374761393 + b * 668265263 + 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7FFFFFFF
    }
}
