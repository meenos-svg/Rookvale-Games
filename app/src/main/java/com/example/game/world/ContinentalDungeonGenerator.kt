package com.example.game.world

import com.example.game.model.MonsterType
import com.example.game.model.TileType

/**
 * Distinct procedural floorplan archetypes so every separate dungeon entrance across Rookvale
 * Heartland and the outer continent carves its own memorable room & corridor architecture.
 */
enum class DungeonLayoutArchetype {
    SUNKEN_AQUEDUCT,  // Flooded sluice canals, raised wooden catwalks & drainage cisterns (-7)
    STEPPED_PYRAMID,  // Concentric stepped Ziggurat rings, corner stairs & solar shrine court (-8)
    TOMB_CRUCIFORM,   // Grand cruciform catacomb nave, east/west burial transepts & ossuary apse (-9)
    FORGE_CRUCIBLE,   // Industrial volcanic foundry, dual smelting aisles & molten slag bridges (-10)
    SHAFT_GALLERY,    // Main mine shaft with 4 staggered ore galleries, outer drifts & flooded sumps (-11)
    TWIN_RIDGE_RING,  // Split east/west mountain ridge loops encircling a central chasm & sky-bridge (-12)
    ROOT_WARREN,      // Asymmetric mossy root dens & winding earthen burrows over a subterranean creek (-13)
    STEPPED_VAULTS,   // Three ascending granite fortress tiers with barred guardrooms & moat crossing (-14)
    CALDERA_SANCTUM   // Obsidian rim ring, molten caldera lake, 4 cardinal bridges & ritual island (-15)
}

/**
 * Specification for a deterministic multi-room procedural upper dungeon floor (`-7..-15`).
 * Players fight through its unique chambers and dedicated monsters to reach the far-end
 * `STAIRS_DOWN` leading to its paired Lower Boss Floor (`-16..-28`).
 */
data class ContinentalDungeonSpec(
    val floorId: Int,
    val lowerBossFloorId: Int,
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
    val lowerLairName: String,
    val wallTile: TileType,
    val floorTile: TileType,
    val accentFloorTile: TileType,
    val hazardTile: TileType,
    val bridgeTile: TileType,
    val stallTile: TileType,
    val primaryOre: TileType,
    val secondaryOre: TileType,
    val seed: Int,
    val layoutArchetype: DungeonLayoutArchetype,
    val exitTileType: TileType = TileType.STAIRS_UP,
    val requiresRopeToExit: Boolean = false
)

/**
 * Fully generated deterministic upper dungeon floor package (map grid, forward camp PZ,
 * dungeon-specific regular monster patrols, descent landmark, and portal coordinates).
 */
data class GeneratedContinentalDungeon(
    val spec: ContinentalDungeonSpec,
    val floor: WorldFloor,
    val camp: UndergroundCampZone,
    val spawns: List<MonsterSpawn>,
    val descentLandmark: LandmarkLabel,
    val stairsUpX: Int,
    val stairsUpY: Int,
    val landingX: Int,
    val landingY: Int,
    val stairsDownX: Int,
    val stairsDownY: Int,
    val returnFromLowerX: Int,
    val returnFromLowerY: Int
)

/**
 * Specification for a dedicated Lower Boss Lair (`-16..-28`) paired 1-to-1 with one of the
 * 13 continental dungeons. Contains NO Protection Zone and NO surface exit — only `STAIRS_UP`
 * returning to the parent upper floor after challenging the boss.
 */
data class LowerBossLairSpec(
    val floorId: Int,
    val parentUpperFloorId: Int,
    val name: String,
    val subtitle: String,
    val dangerLevel: String,
    val dangerSeverity: Int,
    val bossType: MonsterType,
    val bossLandmarkName: String,
    val parentStairsDownX: Int,
    val parentStairsDownY: Int,
    val parentReturnX: Int,
    val parentReturnY: Int,
    val wallTile: TileType,
    val floorTile: TileType,
    val accentFloorTile: TileType,
    val hazardTile: TileType,
    val bridgeTile: TileType,
    val primaryOre: TileType,
    val secondaryOre: TileType,
    val seed: Int,
    val arenaVariant: Int
)

data class GeneratedLowerBossLair(
    val spec: LowerBossLairSpec,
    val floor: WorldFloor,
    val spawns: List<MonsterSpawn>,
    val bossLandmark: LandmarkLabel,
    val stairsUpX: Int,
    val stairsUpY: Int,
    val landingX: Int,
    val landingY: Int
)

/**
 * Procedural deterministic multi-room & corridor dungeon generator for Rookvale's
 * separated Heartland dungeons (Floors -11..-14), Ember Sanctum (Floor -15), and
 * expanded outer-continent biomes (Floors -7..-10).
 *
 * Guarantees:
 * - Safe Southern Forward Camp Protection Zone with Healer, Supply Alchemist & Biome Stall
 * - Distinct archetype geometry with narrow corridors, iron-barred gates, bridges & chambers
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
    private const val DESCENT_X = 24
    private const val DESCENT_Y = 11
    private const val DESCENT_RETURN_X = 24
    private const val DESCENT_RETURN_Y = 12

    const val LOWER_STAIRS_UP_X = 24
    const val LOWER_STAIRS_UP_Y = 39
    const val LOWER_LANDING_X = 24
    const val LOWER_LANDING_Y = 38
    const val LOWER_BOSS_X = 24
    const val LOWER_BOSS_Y = 13

    val specs: List<ContinentalDungeonSpec> = listOf(
        ContinentalDungeonSpec(
            floorId = -7,
            lowerBossFloorId = -24,
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
            bossType = MonsterType.MIREMAW_THE_DROWNED,
            lowerLairName = "Sunken Sluice Depths",
            wallTile = TileType.CAVE_WALL,
            floorTile = TileType.DIRT_FLOOR,
            accentFloorTile = TileType.CRYPT_FLOOR,
            hazardTile = TileType.WATER,
            bridgeTile = TileType.WOOD_BRIDGE,
            stallTile = TileType.BIOME_STALL_MARSH,
            primaryOre = TileType.ORE_MIRE_SILVER,
            secondaryOre = TileType.ORE_PEAT_COAL,
            seed = 701,
            layoutArchetype = DungeonLayoutArchetype.SUNKEN_AQUEDUCT
        ),
        ContinentalDungeonSpec(
            floorId = -8,
            lowerBossFloorId = -25,
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
            bossType = MonsterType.XOLATL_THE_SUN_FANG,
            lowerLairName = "Ziggurat Sun-Altar Chamber",
            wallTile = TileType.STONE_WALL,
            floorTile = TileType.CRYPT_FLOOR,
            accentFloorTile = TileType.DARK_GRASS,
            hazardTile = TileType.WATER,
            bridgeTile = TileType.STONE_BRIDGE,
            stallTile = TileType.BIOME_STALL_MARSH,
            primaryOre = TileType.ORE_MIRE_SILVER,
            secondaryOre = TileType.ORE_VESPERITE,
            seed = 809,
            layoutArchetype = DungeonLayoutArchetype.STEPPED_PYRAMID
        ),
        ContinentalDungeonSpec(
            floorId = -9,
            lowerBossFloorId = -26,
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
            bossType = MonsterType.LORD_MALGRATH_THE_UNDYING,
            lowerLairName = "Barrow Sovereign's Tomb",
            wallTile = TileType.GRANITE_WALL,
            floorTile = TileType.CRYPT_FLOOR,
            accentFloorTile = TileType.OBSIDIAN_FLOOR,
            hazardTile = TileType.WATER,
            bridgeTile = TileType.STONE_BRIDGE,
            stallTile = TileType.BIOME_STALL_FROSTPEAK,
            primaryOre = TileType.ORE_GLACIER_QUARTZ,
            secondaryOre = TileType.ORE_VESPERITE,
            seed = 919,
            layoutArchetype = DungeonLayoutArchetype.TOMB_CRUCIFORM
        ),
        ContinentalDungeonSpec(
            floorId = -10,
            lowerBossFloorId = -27,
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
            bossType = MonsterType.VULKARIS_THE_FORGE_TITAN,
            lowerLairName = "Heart of the Crucible",
            wallTile = TileType.OBSIDIAN_WALL,
            floorTile = TileType.OBSIDIAN_FLOOR,
            accentFloorTile = TileType.CRYPT_FLOOR,
            hazardTile = TileType.LAVA,
            bridgeTile = TileType.STONE_BRIDGE,
            stallTile = TileType.BIOME_STALL_ASHEN,
            primaryOre = TileType.ORE_CINDERITE,
            secondaryOre = TileType.ORE_VESPERITE,
            seed = 1031,
            layoutArchetype = DungeonLayoutArchetype.FORGE_CRUCIBLE
        ),
        // --- Separated Individual Rookvale Heartland Dungeons (Strictly Lv 1-20) ---
        ContinentalDungeonSpec(
            floorId = -11,
            lowerBossFloorId = -17,
            name = "NW Copper Quarry Mines",
            subtitle = "Rookvale Quarry Shafts • Floor -11 • Forward Camp PZ",
            hostBiome = BiomeType.HEARTLAND_WILDS,
            surfaceTier = OverlandBiomeTier.ROOKVALE_HEARTLAND,
            dangerLevel = "Low",
            dangerSeverity = 1,
            entrySurfaceX = WorldMaps.ROOKVALE_ORIGIN_X + 92,
            entrySurfaceY = WorldMaps.ROOKVALE_ORIGIN_Y + 96,
            campName = "QUARRY MINES CAMP (PZ)",
            healerName = "Quarry Medic Linna",
            merchantName = "Ore Peddler Garret",
            bossType = MonsterType.FOREMAN_IRONJAW,
            lowerLairName = "Foreman's Deep Vein",
            wallTile = TileType.CAVE_WALL,
            floorTile = TileType.DIRT_FLOOR,
            accentFloorTile = TileType.COBBLESTONE,
            hazardTile = TileType.WATER,
            bridgeTile = TileType.WOOD_BRIDGE,
            stallTile = TileType.BIOME_STALL_HEARTLAND,
            primaryOre = TileType.ORE_COPPER,
            secondaryOre = TileType.ORE_TIN,
            seed = 1103,
            layoutArchetype = DungeonLayoutArchetype.SHAFT_GALLERY,
            exitTileType = TileType.ROPE_SPOT_UP,
            requiresRopeToExit = true
        ),
        ContinentalDungeonSpec(
            floorId = -12,
            lowerBossFloorId = -18,
            name = "North Mountain Cavern",
            subtitle = "Rookvale Crag Ridge • Floor -12 • Forward Camp PZ",
            hostBiome = BiomeType.HEARTLAND_WILDS,
            surfaceTier = OverlandBiomeTier.ROOKVALE_HEARTLAND,
            dangerLevel = "Moderate",
            dangerSeverity = 1,
            entrySurfaceX = WorldMaps.ROOKVALE_ORIGIN_X + 264,
            entrySurfaceY = WorldMaps.ROOKVALE_ORIGIN_Y + 84,
            campName = "MOUNTAIN CAVERN CAMP (PZ)",
            healerName = "Ridge Cleric Dara",
            merchantName = "Mountaineer Orin",
            bossType = MonsterType.SKREE_RIDGE_MATRIARCH,
            lowerLairName = "Crag Matriarch's Roost",
            wallTile = TileType.STONE_WALL,
            floorTile = TileType.DIRT_FLOOR,
            accentFloorTile = TileType.CRYPT_FLOOR,
            hazardTile = TileType.WATER,
            bridgeTile = TileType.STONE_BRIDGE,
            stallTile = TileType.BIOME_STALL_HEARTLAND,
            primaryOre = TileType.ORE_COPPER,
            secondaryOre = TileType.ORE_BOG_IRON,
            seed = 1217,
            layoutArchetype = DungeonLayoutArchetype.TWIN_RIDGE_RING
        ),
        ContinentalDungeonSpec(
            floorId = -13,
            lowerBossFloorId = -19,
            name = "Dark Forest Sinkhole",
            subtitle = "Rookvale Deep Woods • Floor -13 • Forward Camp PZ",
            hostBiome = BiomeType.HEARTLAND_WILDS,
            surfaceTier = OverlandBiomeTier.ROOKVALE_HEARTLAND,
            dangerLevel = "Moderate",
            dangerSeverity = 1,
            entrySurfaceX = WorldMaps.ROOKVALE_ORIGIN_X + 312,
            entrySurfaceY = WorldMaps.ROOKVALE_ORIGIN_Y + 206,
            campName = "SINKHOLE WARREN CAMP (PZ)",
            healerName = "Herbalist Mela",
            merchantName = "Woods Supplier Fenn",
            bossType = MonsterType.GNARLROOT_THE_BLIGHTED,
            lowerLairName = "Heart-Root Abyss",
            wallTile = TileType.CAVE_WALL,
            floorTile = TileType.DIRT_FLOOR,
            accentFloorTile = TileType.DARK_GRASS,
            hazardTile = TileType.WATER,
            bridgeTile = TileType.WOOD_BRIDGE,
            stallTile = TileType.BIOME_STALL_HEARTLAND,
            primaryOre = TileType.ORE_BOG_IRON,
            secondaryOre = TileType.ORE_TIN,
            seed = 1321,
            layoutArchetype = DungeonLayoutArchetype.ROOT_WARREN,
            exitTileType = TileType.ROPE_SPOT_UP,
            requiresRopeToExit = true
        ),
        ContinentalDungeonSpec(
            floorId = -14,
            lowerBossFloorId = -20,
            name = "Crag Deep Hollow",
            subtitle = "NE Crag Frontier Vaults • Floor -14 • Forward Camp PZ",
            hostBiome = BiomeType.HEARTLAND_FRONTIER,
            surfaceTier = OverlandBiomeTier.ROOKVALE_HEARTLAND,
            dangerLevel = "High",
            dangerSeverity = 2,
            entrySurfaceX = WorldMaps.ROOKVALE_ORIGIN_X + 308,
            entrySurfaceY = WorldMaps.ROOKVALE_ORIGIN_Y + 66,
            campName = "CRAG HOLLOW CAMP (PZ)",
            healerName = "Warden Priest Kell",
            merchantName = "Quartermaster Syra",
            bossType = MonsterType.WARDEN_STONEHELM,
            lowerLairName = "The Sealed Granite Vault",
            wallTile = TileType.GRANITE_WALL,
            floorTile = TileType.CRYPT_FLOOR,
            accentFloorTile = TileType.COBBLESTONE,
            hazardTile = TileType.WATER,
            bridgeTile = TileType.STONE_BRIDGE,
            stallTile = TileType.BIOME_STALL_HEARTLAND,
            primaryOre = TileType.ORE_BOG_IRON,
            secondaryOre = TileType.ORE_TIN,
            seed = 1429,
            layoutArchetype = DungeonLayoutArchetype.STEPPED_VAULTS
        ),
        // --- Separated Volcanic Ember Isles Dungeon ---
        ContinentalDungeonSpec(
            floorId = -15,
            lowerBossFloorId = -28,
            name = "The Ember Sanctum",
            subtitle = "Ember Isles Volcanic Vault • Floor -15 • Vanguard Camp PZ",
            hostBiome = BiomeType.ASHEN_CALDERA,
            surfaceTier = OverlandBiomeTier.ASHEN_CALDERA,
            dangerLevel = "Extreme",
            dangerSeverity = 4,
            entrySurfaceX = 1510,
            entrySurfaceY = 975,
            campName = "EMBER SANCTUM CAMP (PZ)",
            healerName = "Pyre Cleric Aurelia",
            merchantName = "Ember Supplier Cael",
            bossType = MonsterType.IGNIVAR_ASH_WING_WYRM,
            lowerLairName = "Caldera Dragon Throne",
            wallTile = TileType.OBSIDIAN_WALL,
            floorTile = TileType.OBSIDIAN_FLOOR,
            accentFloorTile = TileType.VOLCANIC_ASH,
            hazardTile = TileType.LAVA,
            bridgeTile = TileType.STONE_BRIDGE,
            stallTile = TileType.BIOME_STALL_ASHEN,
            primaryOre = TileType.ORE_CINDERITE,
            secondaryOre = TileType.ORE_VESPERITE,
            seed = 1543,
            layoutArchetype = DungeonLayoutArchetype.CALDERA_SANCTUM
        )
    )

    /**
     * All 13 dedicated Lower Boss Lairs (`-16..-28`) — one for every overland dungeon on the
     * continent (all 6 Rookvale Heartland dungeons + all 7 outer-continent biome dungeons).
     */
    val lowerBossLairSpecs: List<LowerBossLairSpec> = listOf(
        // 4 Bespoke Upper Dungeons' Lower Boss Lairs (-1 -> -16, -2 -> -21, -5 -> -22, -6 -> -23)
        LowerBossLairSpec(
            floorId = -16,
            parentUpperFloorId = -1,
            name = "Briar Brood Sanctum",
            subtitle = "Briar Catacombs • Lower Boss Floor -16 • Stairs Up Only",
            dangerLevel = "Boss Lair",
            dangerSeverity = 2,
            bossType = MonsterType.BROODMOTHER_ARACHNE,
            bossLandmarkName = "BROODMOTHER ARACHNE'S SANCTUM",
            parentStairsDownX = DeepDelveFloors.F1_BROOD_STAIRS_X,
            parentStairsDownY = DeepDelveFloors.F1_BROOD_STAIRS_Y,
            parentReturnX = DeepDelveFloors.F1_BROOD_RETURN_X,
            parentReturnY = DeepDelveFloors.F1_BROOD_RETURN_Y,
            wallTile = TileType.CAVE_WALL,
            floorTile = TileType.CRYPT_FLOOR,
            accentFloorTile = TileType.DIRT_FLOOR,
            hazardTile = TileType.WATER,
            bridgeTile = TileType.WOOD_BRIDGE,
            primaryOre = TileType.ORE_BOG_IRON,
            secondaryOre = TileType.ORE_VESPERITE,
            seed = 1601,
            arenaVariant = 0
        ),
        LowerBossLairSpec(
            floorId = -21,
            parentUpperFloorId = -2,
            name = "Molten Pit Throne",
            subtitle = "Watchwood Caverns • Lower Boss Floor -21 • Stairs Up Only",
            dangerLevel = "Boss Lair",
            dangerSeverity = 3,
            bossType = MonsterType.IGNIS_LORD_OF_THE_PIT,
            bossLandmarkName = "IGNIS, LORD OF THE PIT'S THRONE",
            parentStairsDownX = DeepDelveFloors.F2_PIT_STAIRS_X,
            parentStairsDownY = DeepDelveFloors.F2_PIT_STAIRS_Y,
            parentReturnX = DeepDelveFloors.F2_PIT_RETURN_X,
            parentReturnY = DeepDelveFloors.F2_PIT_RETURN_Y,
            wallTile = TileType.STONE_WALL,
            floorTile = TileType.OBSIDIAN_FLOOR,
            accentFloorTile = TileType.CRYPT_FLOOR,
            hazardTile = TileType.LAVA,
            bridgeTile = TileType.STONE_BRIDGE,
            primaryOre = TileType.ORE_VESPERITE,
            secondaryOre = TileType.ORE_CINDERITE,
            seed = 2111,
            arenaVariant = 1
        ),
        LowerBossLairSpec(
            floorId = -22,
            parentUpperFloorId = -5,
            name = "Kragor's Deep Quarry Pit",
            subtitle = "The Grimmaw Quarry • Lower Boss Floor -22 • Stairs Up Only",
            dangerLevel = "Endgame Boss",
            dangerSeverity = 4,
            bossType = MonsterType.KRAGOR_THE_STONE_EYED,
            bossLandmarkName = "KRAGOR'S DEEP QUARRY PIT",
            parentStairsDownX = 24,
            parentStairsDownY = 12,
            parentReturnX = 24,
            parentReturnY = 13,
            wallTile = TileType.STONE_WALL,
            floorTile = TileType.CRYPT_FLOOR,
            accentFloorTile = TileType.DIRT_FLOOR,
            hazardTile = TileType.WATER,
            bridgeTile = TileType.STONE_BRIDGE,
            primaryOre = TileType.ORE_VESPERITE,
            secondaryOre = TileType.ORE_GLACIER_QUARTZ,
            seed = 2207,
            arenaVariant = 2
        ),
        LowerBossLairSpec(
            floorId = -23,
            parentUpperFloorId = -6,
            name = "Vaelgor's Inner Sanctum",
            subtitle = "The Gorek Labyrinth • Lower Boss Floor -23 • Stairs Up Only",
            dangerLevel = "Pinnacle Boss",
            dangerSeverity = 4,
            bossType = MonsterType.VAELGOR_LABYRINTH_WARDEN,
            bossLandmarkName = "VAELGOR'S INNER SANCTUM",
            parentStairsDownX = 24,
            parentStairsDownY = 11,
            parentReturnX = 24,
            parentReturnY = 12,
            wallTile = TileType.OBSIDIAN_WALL,
            floorTile = TileType.OBSIDIAN_FLOOR,
            accentFloorTile = TileType.CRYPT_FLOOR,
            hazardTile = TileType.LAVA,
            bridgeTile = TileType.STONE_BRIDGE,
            primaryOre = TileType.ORE_CINDERITE,
            secondaryOre = TileType.ORE_VESPERITE,
            seed = 2309,
            arenaVariant = 3
        )
    ) + specs.mapIndexed { idx, s ->
        LowerBossLairSpec(
            floorId = s.lowerBossFloorId,
            parentUpperFloorId = s.floorId,
            name = s.lowerLairName,
            subtitle = "${s.name} • Lower Boss Floor ${s.lowerBossFloorId} • Stairs Up Only",
            dangerLevel = if (s.dangerSeverity >= 4) "Endgame Boss" else "Boss Lair",
            dangerSeverity = s.dangerSeverity.coerceAtLeast(2),
            bossType = s.bossType,
            bossLandmarkName = s.lowerLairName.uppercase(),
            parentStairsDownX = DESCENT_X,
            parentStairsDownY = DESCENT_Y,
            parentReturnX = DESCENT_RETURN_X,
            parentReturnY = DESCENT_RETURN_Y,
            wallTile = s.wallTile,
            floorTile = s.floorTile,
            accentFloorTile = s.accentFloorTile,
            hazardTile = s.hazardTile,
            bridgeTile = s.bridgeTile,
            primaryOre = s.primaryOre,
            secondaryOre = s.secondaryOre,
            seed = s.seed + 5000,
            arenaVariant = idx % 4
        )
    }

    val generatedDungeons: List<GeneratedContinentalDungeon> by lazy {
        specs.map { generate(it) }
    }

    val byFloorId: Map<Int, GeneratedContinentalDungeon> by lazy {
        generatedDungeons.associateBy { it.spec.floorId }
    }

    val generatedLowerBossLairs: List<GeneratedLowerBossLair> by lazy {
        lowerBossLairSpecs.map { generateLowerBossLair(it) }
    }

    val lowerBossLairByFloorId: Map<Int, GeneratedLowerBossLair> by lazy {
        generatedLowerBossLairs.associateBy { it.spec.floorId }
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

        // 2. Carve Archetype-Specific Dungeon Chambers, Corridors, Hazards, Veins & Torches
        val patrolSlots: List<Pair<Int, Int>> = when (spec.layoutArchetype) {
            DungeonLayoutArchetype.SUNKEN_AQUEDUCT -> carveSunkenAqueduct(g, spec, ::fillRect)
            DungeonLayoutArchetype.STEPPED_PYRAMID -> carveSteppedPyramid(g, spec, ::fillRect)
            DungeonLayoutArchetype.TOMB_CRUCIFORM -> carveTombCruciform(g, spec, ::fillRect)
            DungeonLayoutArchetype.FORGE_CRUCIBLE -> carveForgeCrucible(g, spec, ::fillRect)
            DungeonLayoutArchetype.SHAFT_GALLERY -> carveShaftGallery(g, spec, ::fillRect)
            DungeonLayoutArchetype.TWIN_RIDGE_RING -> carveTwinRidgeRing(g, spec, ::fillRect)
            DungeonLayoutArchetype.ROOT_WARREN -> carveRootWarren(g, spec, ::fillRect)
            DungeonLayoutArchetype.STEPPED_VAULTS -> carveSteppedVaults(g, spec, ::fillRect)
            DungeonLayoutArchetype.CALDERA_SANCTUM -> carveCalderaSanctum(g, spec, ::fillRect)
        }

        // 3. Safe Forward Camp Fixtures + Far-End Descent Stairs to the Lower Boss Floor
        g[38][21] = TileType.CAMPFIRE_BRAZIER
        g[38][27] = TileType.CAMPFIRE_BRAZIER
        g[41][21] = TileType.WAR_BANNER
        g[41][27] = TileType.WAR_BANNER
        g[40][21] = TileType.COBBLESTONE
        g[40][27] = TileType.COBBLESTONE
        g[38][24] = spec.stallTile
        g[LANDING_Y][LANDING_X] = TileType.COBBLESTONE
        g[STAIRS_UP_Y][STAIRS_UP_X] = spec.exitTileType
        g[DESCENT_RETURN_Y][DESCENT_RETURN_X] = spec.accentFloorTile
        g[DESCENT_Y][DESCENT_X] = TileType.STAIRS_DOWN

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

        // 4. Dedicated Per-Dungeon Regular Monster Patrols (ZERO bosses on upper floor!)
        val spawns = buildList {
            patrolSlots.forEachIndexed { idx, (px, py) ->
                if (!camp.contains(px, py) && floor.tileAt(px, py).walkable && !(px == DESCENT_X && py == DESCENT_Y)) {
                    val species = BiomeMonsterSpawner.pickWeightedDungeonMonster(
                        floorId = spec.floorId,
                        seed = hash(spec.seed + idx * 37, px * 31 + py)
                    )
                    add(MonsterSpawn(spec.floorId, px, py, species))
                }
            }
        }

        val descentLandmark = LandmarkLabel(
            spec.floorId,
            DESCENT_X,
            DESCENT_Y,
            "DESCENT TO ${spec.lowerLairName.uppercase()}"
        )

        return GeneratedContinentalDungeon(
            spec = spec,
            floor = floor,
            camp = camp,
            spawns = spawns,
            descentLandmark = descentLandmark,
            stairsUpX = STAIRS_UP_X,
            stairsUpY = STAIRS_UP_Y,
            landingX = LANDING_X,
            landingY = LANDING_Y,
            stairsDownX = DESCENT_X,
            stairsDownY = DESCENT_Y,
            returnFromLowerX = DESCENT_RETURN_X,
            returnFromLowerY = DESCENT_RETURN_Y
        )
    }

    /**
     * Generates a dedicated Lower Boss Lair (`-16..-28`) with a southern return stairway
     * strictly back up to the paired upper floor (`STAIRS_UP` only, NO PZ camp, NO surface portal),
     * a guarded antechamber with 4 dungeon-specific elite escorts, and a grand sculpted Boss Arena
     * hosting that dungeon's unique named Boss.
     */
    fun generateLowerBossLair(spec: LowerBossLairSpec): GeneratedLowerBossLair {
        val s = GRID_SIZE
        val g = Array(s) { Array(s) { spec.wallTile } }

        fun fillRect(x1: Int, y1: Int, x2: Int, y2: Int, tile: TileType) {
            for (y in y1.coerceAtLeast(1)..y2.coerceAtMost(s - 2)) {
                for (x in x1.coerceAtLeast(1)..x2.coerceAtMost(s - 2)) {
                    g[y][x] = tile
                }
            }
        }

        // 1. Southern Entry Vestibule (Unprotected — return stairs back to upper floor ONLY)
        fillRect(20, 35, 28, 41, spec.accentFloorTile)
        g[36][20] = TileType.WAR_BANNER; g[36][28] = TileType.WAR_BANNER
        g[40][21] = TileType.CAMPFIRE_BRAZIER; g[40][27] = TileType.CAMPFIRE_BRAZIER
        g[LOWER_LANDING_Y][LOWER_LANDING_X] = TileType.COBBLESTONE
        g[LOWER_STAIRS_UP_Y][LOWER_STAIRS_UP_X] = TileType.STAIRS_UP

        // 2. Sculpted Approach & Guarded Antechamber (4 architectural lair variants)
        val escortSlots: List<Pair<Int, Int>> = when (spec.arenaVariant % 4) {
            0 -> { // Pillared Causeway over Hazard Moat
                fillRect(14, 29, 34, 34, spec.floorTile)
                fillRect(14, 23, 34, 28, spec.hazardTile)
                fillRect(22, 22, 26, 29, spec.bridgeTile)
                fillRect(15, 22, 17, 29, spec.bridgeTile)
                fillRect(31, 22, 33, 29, spec.bridgeTile)
                fillRect(23, 34, 25, 35, spec.floorTile)
                listOf(18 to 31, 30 to 31, 16 to 24, 32 to 24)
            }
            1 -> { // Twin Flanking Guard Galleries + Central Lava/Water Basin
                fillRect(23, 32, 25, 35, spec.floorTile)
                fillRect(10, 29, 38, 33, spec.floorTile)
                fillRect(10, 22, 16, 30, spec.accentFloorTile)
                fillRect(32, 22, 38, 30, spec.accentFloorTile)
                fillRect(18, 23, 30, 28, spec.hazardTile)
                fillRect(23, 22, 25, 29, spec.bridgeTile)
                listOf(13 to 27, 35 to 27, 13 to 23, 35 to 23)
            }
            2 -> { // Split Diamond Gauntlet Hall
                fillRect(18, 30, 30, 35, spec.floorTile)
                fillRect(12, 24, 36, 30, spec.floorTile)
                fillRect(21, 25, 27, 28, spec.hazardTile)
                listOf(16 to 27, 32 to 27).forEach { (px, py) -> g[py][px] = spec.wallTile }
                fillRect(22, 21, 26, 24, spec.bridgeTile)
                listOf(15 to 29, 33 to 29, 15 to 25, 33 to 25)
            }
            else -> { // Crucible Ring Antechamber with Cardinal Bridges
                fillRect(12, 22, 36, 34, spec.hazardTile)
                fillRect(19, 25, 29, 31, spec.accentFloorTile)
                fillRect(23, 31, 25, 35, spec.bridgeTile)
                fillRect(23, 21, 25, 25, spec.bridgeTile)
                fillRect(12, 22, 16, 34, spec.floorTile)
                fillRect(32, 22, 36, 34, spec.floorTile)
                fillRect(16, 27, 19, 29, spec.bridgeTile)
                fillRect(29, 27, 32, 29, spec.bridgeTile)
                listOf(14 to 28, 34 to 28, 21 to 28, 27 to 28)
            }
        }

        // 3. Grand Sculpted Boss Arena (x=13..35, y=7..21)
        fillRect(13, 7, 35, 21, spec.accentFloorTile)
        fillRect(16, 9, 32, 19, spec.floorTile)
        g[21][23] = TileType.IRON_BAR_GATE; g[21][24] = TileType.IRON_BAR_GATE; g[21][25] = TileType.IRON_BAR_GATE
        // Corner hazard pools & tactical cover pillars
        fillRect(13, 7, 15, 9, spec.hazardTile)
        fillRect(33, 7, 35, 9, spec.hazardTile)
        listOf(18 to 11, 30 to 11, 18 to 17, 30 to 17).forEach { (px, py) -> g[py][px] = spec.wallTile }

        // 4. Rich Biome Ore Veins along Arena Rim & Flickering Torches
        listOf(14 to 14, 34 to 14, 19 to 8, 29 to 8).forEach { (vx, vy) ->
            if (g[vy][vx].walkable) g[vy][vx] = spec.primaryOre
        }
        listOf(14 to 19, 34 to 19).forEach { (vx, vy) ->
            if (g[vy][vx].walkable) g[vy][vx] = spec.secondaryOre
        }
        placeTorches(
            g,
            listOf(
                24 to 42, 19 to 38, 29 to 38, 22 to 21, 26 to 21,
                12 to 14, 36 to 14, 24 to 6, 17 to 21, 31 to 21
            )
        )

        val floor = WorldFloor(
            floorId = spec.floorId,
            name = spec.name,
            subtitle = spec.subtitle,
            width = s,
            height = s,
            chunkedGrid = ChunkedTileGrid.fromArray(g),
            isProtectionZone = { _, _ -> false },
            dangerLevel = spec.dangerLevel,
            recommendedLevel = BiomeProgressionRules.dungeonRecommendedLevelLabel(spec.floorId),
            dangerSeverity = spec.dangerSeverity
        )

        val spawns = buildList {
            escortSlots.forEachIndexed { idx, (ex, ey) ->
                if (floor.tileAt(ex, ey).walkable) {
                    val escortType = BiomeMonsterSpawner.pickWeightedDungeonMonster(
                        floorId = spec.floorId,
                        seed = hash(spec.seed + idx * 53, ex * 31 + ey)
                    )
                    add(MonsterSpawn(spec.floorId, ex, ey, escortType))
                }
            }
            add(MonsterSpawn(spec.floorId, LOWER_BOSS_X, LOWER_BOSS_Y, spec.bossType))
        }

        val bossLandmark = LandmarkLabel(spec.floorId, LOWER_BOSS_X, LOWER_BOSS_Y, spec.bossLandmarkName)

        return GeneratedLowerBossLair(
            spec = spec,
            floor = floor,
            spawns = spawns,
            bossLandmark = bossLandmark,
            stairsUpX = LOWER_STAIRS_UP_X,
            stairsUpY = LOWER_STAIRS_UP_Y,
            landingX = LOWER_LANDING_X,
            landingY = LOWER_LANDING_Y
        )
    }

    private fun carveSunkenAqueduct(
        g: Array<Array<TileType>>,
        spec: ContinentalDungeonSpec,
        fillRect: (Int, Int, Int, Int, TileType) -> Unit
    ): List<Pair<Int, Int>> {
        // Sunken Mire Vaults (-7): Twin parallel flooded canals crossed by timber catwalks & sluice basins
        fillRect(23, 34, 25, 36, spec.floorTile)
        g[36][23] = TileType.IRON_BAR_GATE; g[36][24] = TileType.IRON_BAR_GATE; g[36][25] = TileType.IRON_BAR_GATE

        // Flooded central basin spanning y=16..34
        fillRect(10, 16, 38, 34, spec.hazardTile)
        // Dry raised stone embankments along West, Center Spine, and East walls
        fillRect(7, 15, 11, 35, spec.floorTile)
        fillRect(37, 15, 41, 35, spec.floorTile)
        fillRect(21, 16, 27, 34, spec.accentFloorTile)
        // Raised Cistern Islands inside the flooded channels
        fillRect(13, 27, 18, 33, spec.floorTile)
        fillRect(30, 27, 35, 33, spec.floorTile)
        fillRect(13, 18, 18, 24, spec.accentFloorTile)
        fillRect(30, 18, 35, 24, spec.accentFloorTile)
        // Wooden plank catwalk bridges stitching islands & embankments
        fillRect(11, 30, 21, 31, spec.bridgeTile)
        fillRect(27, 30, 37, 31, spec.bridgeTile)
        fillRect(11, 20, 21, 21, spec.bridgeTile)
        fillRect(27, 20, 37, 21, spec.bridgeTile)
        fillRect(15, 24, 16, 27, spec.bridgeTile)
        fillRect(32, 24, 33, 27, spec.bridgeTile)

        // Northern Sluice Descent Chamber
        fillRect(17, 7, 31, 15, spec.accentFloorTile)
        g[16][23] = TileType.IRON_BAR_GATE; g[16][24] = TileType.IRON_BAR_GATE; g[16][25] = TileType.IRON_BAR_GATE
        listOf(20 to 9, 28 to 9, 20 to 13, 28 to 13).forEach { (px, py) -> g[py][px] = spec.wallTile }

        placeVeins(g, spec, listOf(8 to 33, 40 to 33, 8 to 17, 40 to 17), listOf(14 to 28, 34 to 28, 18 to 8, 30 to 8))
        placeTorches(
            g,
            listOf(
                24 to 43, 22 to 36, 26 to 36, 6 to 25, 42 to 25,
                22 to 16, 26 to 16, 16 to 11, 32 to 11, 24 to 6
            )
        )

        return listOf(
            15 to 30, 33 to 30, 24 to 28, 9 to 25, 39 to 25,
            15 to 21, 33 to 21, 24 to 19, 19 to 14, 29 to 14
        )
    }

    private fun carveSteppedPyramid(
        g: Array<Array<TileType>>,
        spec: ContinentalDungeonSpec,
        fillRect: (Int, Int, Int, Int, TileType) -> Unit
    ): List<Pair<Int, Int>> {
        // Overgrown Ziggurat Vault (-8): Concentric stepped temple rings & central sun-glyph court
        fillRect(23, 35, 25, 36, spec.floorTile)
        g[36][23] = TileType.IRON_BAR_GATE; g[36][24] = TileType.IRON_BAR_GATE; g[36][25] = TileType.IRON_BAR_GATE

        // Outer Ziggurat Ring Perimeter (x=7..41, y=14..35)
        fillRect(7, 33, 41, 35, spec.floorTile)
        fillRect(7, 14, 10, 35, spec.floorTile)
        fillRect(38, 14, 41, 35, spec.floorTile)
        fillRect(7, 14, 41, 16, spec.floorTile)

        // Inner Stepped Terrace Ring (x=13..35, y=19..30)
        fillRect(13, 19, 35, 30, spec.accentFloorTile)
        // Sunken Jungle Moat separating outer and inner ziggurat rings on East/West
        fillRect(11, 17, 12, 32, spec.hazardTile)
        fillRect(36, 17, 37, 32, spec.hazardTile)
        // Four corner stone causeways stepping into the inner ring
        fillRect(10, 29, 13, 30, spec.bridgeTile)
        fillRect(35, 29, 38, 30, spec.bridgeTile)
        fillRect(10, 19, 13, 20, spec.bridgeTile)
        fillRect(35, 19, 38, 20, spec.bridgeTile)
        // Central Ziggurat Courtyard Pillar Altar
        listOf(18 to 22, 30 to 22, 18 to 27, 30 to 27, 24 to 24, 24 to 25).forEach { (px, py) -> g[py][px] = spec.wallTile }
        fillRect(23, 16, 25, 19, spec.bridgeTile)
        fillRect(23, 30, 25, 33, spec.bridgeTile)

        // Elevated Northern Solar Descent Shrine
        fillRect(17, 7, 31, 15, spec.accentFloorTile)
        g[15][23] = TileType.IRON_BAR_GATE; g[15][24] = TileType.IRON_BAR_GATE; g[15][25] = TileType.IRON_BAR_GATE
        listOf(19 to 9, 29 to 9, 19 to 13, 29 to 13).forEach { (px, py) -> g[py][px] = spec.wallTile }

        placeVeins(g, spec, listOf(8 to 34, 40 to 34, 8 to 15, 40 to 15), listOf(15 to 24, 33 to 24, 18 to 8, 30 to 8))
        placeTorches(
            g,
            listOf(
                24 to 43, 22 to 36, 26 to 36, 6 to 24, 42 to 24,
                22 to 15, 26 to 15, 16 to 10, 32 to 10, 24 to 6
            )
        )

        return listOf(
            14 to 34, 34 to 34, 8 to 25, 40 to 25,
            16 to 24, 32 to 24, 24 to 21, 24 to 28, 18 to 15, 30 to 15
        )
    }

    private fun carveTombCruciform(
        g: Array<Array<TileType>>,
        spec: ContinentalDungeonSpec,
        fillRect: (Int, Int, Int, Int, TileType) -> Unit
    ): List<Pair<Int, Int>> {
        // Blightwood Barrow Catacombs (-9): Grand cruciform nave, burial transepts & side ossuary alcoves
        fillRect(21, 15, 27, 36, spec.floorTile)
        g[36][23] = TileType.IRON_BAR_GATE; g[36][24] = TileType.IRON_BAR_GATE; g[36][25] = TileType.IRON_BAR_GATE
        // Nave colonnade pillars
        listOf(21 to 32, 27 to 32, 21 to 27, 27 to 27, 21 to 19, 27 to 19).forEach { (px, py) -> g[py][px] = spec.wallTile }

        // Grand East & West Burial Transepts (y=21..26)
        fillRect(6, 21, 42, 26, spec.accentFloorTile)
        listOf(12 to 22, 12 to 25, 36 to 22, 36 to 25).forEach { (px, py) -> g[py][px] = spec.wallTile }
        // Four sunken sarcophagus alcoves off the transepts
        fillRect(8, 28, 15, 34, spec.floorTile)
        fillRect(10, 26, 12, 28, spec.floorTile)
        fillRect(33, 28, 40, 34, spec.floorTile)
        fillRect(36, 26, 38, 28, spec.floorTile)
        fillRect(8, 13, 15, 19, spec.floorTile)
        fillRect(10, 19, 12, 21, spec.floorTile)
        fillRect(33, 13, 40, 19, spec.floorTile)
        fillRect(36, 19, 38, 21, spec.floorTile)

        // Frozen Barrow Moat dividing the nave before the Sovereign's Descent Apse
        fillRect(18, 16, 30, 18, spec.hazardTile)
        fillRect(23, 16, 25, 18, spec.bridgeTile)

        // Northern Ossuary Apse & Descent Stairs
        fillRect(16, 7, 32, 15, spec.accentFloorTile)
        g[15][23] = TileType.IRON_BAR_GATE; g[15][24] = TileType.IRON_BAR_GATE; g[15][25] = TileType.IRON_BAR_GATE
        listOf(19 to 9, 29 to 9, 19 to 13, 29 to 13).forEach { (px, py) -> g[py][px] = spec.wallTile }

        placeVeins(g, spec, listOf(9 to 33, 39 to 33, 9 to 14, 39 to 14), listOf(7 to 23, 41 to 23, 17 to 8, 31 to 8))
        placeTorches(
            g,
            listOf(
                24 to 43, 20 to 36, 28 to 36, 5 to 23, 43 to 23,
                22 to 15, 26 to 15, 15 to 11, 33 to 11, 24 to 6
            )
        )

        return listOf(
            24 to 30, 12 to 31, 36 to 31, 10 to 23, 38 to 23,
            24 to 23, 12 to 16, 36 to 16, 20 to 13, 28 to 13
        )
    }

    private fun carveForgeCrucible(
        g: Array<Array<TileType>>,
        spec: ContinentalDungeonSpec,
        fillRect: (Int, Int, Int, Int, TileType) -> Unit
    ): List<Pair<Int, Int>> {
        // Ashen Caldera Foundry (-10): Dual smelting aisles flanking twin vertical lava channels
        fillRect(23, 34, 25, 36, spec.floorTile)
        g[36][23] = TileType.IRON_BAR_GATE; g[36][24] = TileType.IRON_BAR_GATE; g[36][25] = TileType.IRON_BAR_GATE

        // Southern Foundry Floor linking East & West Smelting Aisles
        fillRect(8, 32, 40, 35, spec.floorTile)
        // Twin vertical molten slag channels
        fillRect(15, 16, 19, 31, spec.hazardTile)
        fillRect(29, 16, 33, 31, spec.hazardTile)
        // West Aisle, Central Forge Spine, and East Aisle
        fillRect(7, 14, 14, 33, spec.accentFloorTile)
        fillRect(20, 16, 28, 32, spec.floorTile)
        fillRect(34, 14, 41, 33, spec.accentFloorTile)
        // Three cross-foundry slag bridges
        fillRect(14, 28, 34, 29, spec.bridgeTile)
        fillRect(14, 22, 34, 23, spec.bridgeTile)
        fillRect(14, 16, 34, 17, spec.bridgeTile)
        // Central foundry furnaces & anvil pillars
        g[25][22] = TileType.SMELTING_FURNACE
        g[25][26] = TileType.BLACKSMITH_ANVIL
        listOf(10 to 25, 38 to 25, 10 to 19, 38 to 19).forEach { (px, py) -> g[py][px] = spec.wallTile }

        // Northern Crucible Descent Hall
        fillRect(16, 7, 32, 15, spec.accentFloorTile)
        g[15][23] = TileType.IRON_BAR_GATE; g[15][24] = TileType.IRON_BAR_GATE; g[15][25] = TileType.IRON_BAR_GATE
        g[8][17] = spec.hazardTile; g[8][31] = spec.hazardTile
        listOf(19 to 9, 29 to 9, 19 to 13, 29 to 13).forEach { (px, py) -> g[py][px] = spec.wallTile }

        placeVeins(g, spec, listOf(8 to 31, 40 to 31, 8 to 15, 40 to 15), listOf(21 to 28, 27 to 28, 18 to 8, 30 to 8))
        placeTorches(
            g,
            listOf(
                24 to 43, 22 to 36, 26 to 36, 6 to 24, 42 to 24,
                22 to 15, 26 to 15, 15 to 10, 33 to 10, 24 to 6
            )
        )

        return listOf(
            11 to 30, 37 to 30, 24 to 30, 11 to 22, 37 to 22,
            24 to 20, 11 to 16, 37 to 16, 20 to 13, 28 to 13
        )
    }

    private fun carveShaftGallery(
        g: Array<Array<TileType>>,
        spec: ContinentalDungeonSpec,
        fillRect: (Int, Int, Int, Int, TileType) -> Unit
    ): List<Pair<Int, Int>> {
        // Main timber-shored vertical mine shaft north of Forward Camp
        fillRect(22, 15, 26, 36, spec.floorTile)
        g[36][23] = TileType.IRON_BAR_GATE; g[36][24] = TileType.IRON_BAR_GATE; g[36][25] = TileType.IRON_BAR_GATE
        listOf(22 to 31, 26 to 31, 22 to 21, 26 to 21).forEach { (px, py) -> g[py][px] = spec.wallTile }

        // Four staggered horizontal ore galleries branching off the main shaft
        fillRect(7, 30, 21, 34, spec.floorTile)
        fillRect(7, 29, 13, 35, spec.accentFloorTile) // SW Ore Heading
        fillRect(27, 27, 41, 31, spec.floorTile)
        fillRect(35, 26, 41, 32, spec.accentFloorTile) // SE Ore Heading
        fillRect(7, 19, 21, 23, spec.floorTile)
        fillRect(7, 18, 13, 24, spec.accentFloorTile) // NW Ore Heading
        fillRect(27, 16, 41, 20, spec.floorTile)
        fillRect(35, 15, 41, 21, spec.accentFloorTile) // NE Ore Heading

        // Outer vertical ventilation drifts connecting West & East headings
        fillRect(9, 24, 11, 29, spec.floorTile)
        fillRect(37, 21, 39, 26, spec.floorTile)

        // Flooded mine sumps crossed by wooden plank bridges
        fillRect(21, 24, 27, 26, spec.hazardTile)
        fillRect(23, 24, 25, 26, spec.bridgeTile)
        fillRect(8, 26, 12, 27, spec.hazardTile)
        fillRect(9, 26, 11, 27, spec.bridgeTile)
        fillRect(36, 23, 40, 24, spec.hazardTile)
        fillRect(37, 23, 39, 24, spec.bridgeTile)

        // Northern Foreman's Quarry Boss Chamber
        fillRect(16, 7, 32, 14, spec.accentFloorTile)
        g[15][23] = TileType.IRON_BAR_GATE; g[15][24] = TileType.IRON_BAR_GATE; g[15][25] = TileType.IRON_BAR_GATE
        listOf(19 to 9, 29 to 9, 19 to 13, 29 to 13).forEach { (px, py) -> g[py][px] = spec.wallTile }

        placeVeins(
            g,
            spec,
            listOf(7 to 30, 41 to 27, 7 to 19, 41 to 16),
            listOf(7 to 34, 41 to 31, 7 to 23, 41 to 20)
        )
        placeTorches(
            g,
            listOf(
                24 to 43, 21 to 36, 27 to 36, 14 to 29, 34 to 26,
                21 to 28, 27 to 28, 14 to 18, 34 to 15, 22 to 15, 26 to 15, 24 to 6
            )
        )

        return listOf(
            11 to 32, 16 to 32, 37 to 29, 32 to 29,
            24 to 29, 11 to 21, 16 to 21, 37 to 18, 32 to 18
        )
    }

    private fun carveTwinRidgeRing(
        g: Array<Array<TileType>>,
        spec: ContinentalDungeonSpec,
        fillRect: (Int, Int, Int, Int, TileType) -> Unit
    ): List<Pair<Int, Int>> {
        // South Split Vestibule
        fillRect(23, 35, 25, 36, spec.floorTile)
        g[36][23] = TileType.IRON_BAR_GATE; g[36][24] = TileType.IRON_BAR_GATE; g[36][25] = TileType.IRON_BAR_GATE
        fillRect(19, 32, 29, 35, spec.floorTile)

        // West Mountain Ridge Loop
        fillRect(8, 32, 19, 34, spec.floorTile)
        fillRect(7, 17, 14, 33, spec.accentFloorTile)
        listOf(9 to 21, 12 to 25, 9 to 29).forEach { (px, py) -> g[py][px] = spec.wallTile }
        fillRect(8, 15, 19, 17, spec.floorTile)

        // East Mountain Ridge Loop
        fillRect(29, 32, 40, 34, spec.floorTile)
        fillRect(34, 17, 41, 33, spec.accentFloorTile)
        listOf(39 to 21, 36 to 25, 39 to 29).forEach { (px, py) -> g[py][px] = spec.wallTile }
        fillRect(29, 15, 40, 17, spec.floorTile)

        // Huge Central Subterranean Chasm + Crag Spires + Narrow Sky-Bridge
        fillRect(16, 19, 32, 30, spec.hazardTile)
        listOf(19 to 22, 29 to 22, 19 to 27, 29 to 27).forEach { (px, py) -> g[py][px] = spec.wallTile }
        fillRect(23, 17, 25, 31, spec.bridgeTile)
        g[31][23] = TileType.IRON_BAR_GATE; g[31][24] = TileType.IRON_BAR_GATE; g[31][25] = TileType.IRON_BAR_GATE
        g[17][23] = TileType.IRON_BAR_GATE; g[17][24] = TileType.IRON_BAR_GATE; g[17][25] = TileType.IRON_BAR_GATE

        // Northern Ridge Cavern Boss Hall
        fillRect(16, 7, 32, 16, spec.accentFloorTile)
        listOf(19 to 10, 29 to 10, 19 to 14, 29 to 14).forEach { (px, py) -> g[py][px] = spec.wallTile }

        placeVeins(
            g,
            spec,
            listOf(7 to 18, 41 to 18, 14 to 24, 34 to 24),
            listOf(7 to 31, 41 to 31, 17 to 8, 31 to 8)
        )
        placeTorches(
            g,
            listOf(
                24 to 43, 22 to 36, 26 to 36, 15 to 31, 33 to 31,
                6 to 24, 42 to 24, 15 to 14, 33 to 14, 22 to 17, 26 to 17, 24 to 6
            )
        )

        return listOf(
            11 to 32, 10 to 27, 10 to 23, 11 to 18,
            37 to 32, 38 to 27, 38 to 23, 37 to 18, 24 to 25
        )
    }

    private fun carveRootWarren(
        g: Array<Array<TileType>>,
        spec: ContinentalDungeonSpec,
        fillRect: (Int, Int, Int, Int, TileType) -> Unit
    ): List<Pair<Int, Int>> {
        // Asymmetric root tunnel curving west out of Forward Camp
        fillRect(22, 33, 26, 36, spec.floorTile)
        g[36][23] = TileType.IRON_BAR_GATE; g[36][24] = TileType.IRON_BAR_GATE; g[36][25] = TileType.IRON_BAR_GATE
        fillRect(12, 32, 23, 34, spec.floorTile)

        // SW Root Den & Central Mossy Hollow
        fillRect(7, 27, 16, 35, spec.accentFloorTile)
        fillRect(15, 28, 18, 30, spec.floorTile)
        fillRect(17, 23, 31, 30, spec.floorTile)
        fillRect(19, 24, 29, 29, spec.accentFloorTile)
        listOf(21 to 26, 27 to 27).forEach { (px, py) -> g[py][px] = spec.wallTile }

        // SE Spur & East Mire Pocket
        fillRect(27, 31, 38, 34, spec.floorTile)
        fillRect(32, 22, 41, 33, spec.floorTile)
        fillRect(34, 23, 39, 28, spec.accentFloorTile)

        // Subterranean Forest Creek + 3 Staggered Timber Bridges
        fillRect(8, 19, 39, 21, spec.hazardTile)
        fillRect(11, 19, 13, 21, spec.bridgeTile)
        fillRect(26, 19, 28, 21, spec.bridgeTile)
        fillRect(36, 19, 38, 21, spec.bridgeTile)
        // Connect south banks cleanly to bridges
        fillRect(11, 22, 13, 26, spec.floorTile)
        fillRect(26, 22, 28, 22, spec.floorTile)

        // Northern Fungal Burrows flanking Boss Nest
        fillRect(8, 12, 17, 18, spec.accentFloorTile)
        fillRect(31, 12, 40, 18, spec.floorTile)
        fillRect(18, 14, 19, 16, spec.floorTile)
        fillRect(29, 14, 30, 16, spec.floorTile)
        fillRect(26, 17, 28, 18, spec.floorTile)

        // Deep Root Nest Boss Arena
        fillRect(17, 7, 31, 16, spec.accentFloorTile)
        g[17][26] = TileType.IRON_BAR_GATE; g[17][27] = TileType.IRON_BAR_GATE; g[17][28] = TileType.IRON_BAR_GATE
        listOf(20 to 9, 28 to 9, 20 to 14, 28 to 14).forEach { (px, py) -> g[py][px] = spec.wallTile }

        placeVeins(
            g,
            spec,
            listOf(8 to 34, 40 to 32, 9 to 13, 39 to 13),
            listOf(8 to 28, 40 to 23, 18 to 8, 30 to 8)
        )
        placeTorches(
            g,
            listOf(
                24 to 43, 21 to 36, 27 to 36, 17 to 31, 31 to 29,
                10 to 22, 25 to 22, 39 to 22, 16 to 11, 32 to 11, 24 to 6
            )
        )

        return listOf(
            12 to 31, 10 to 29, 24 to 27, 20 to 25,
            36 to 29, 37 to 25, 13 to 15, 35 to 15, 27 to 24
        )
    }

    private fun carveSteppedVaults(
        g: Array<Array<TileType>>,
        spec: ContinentalDungeonSpec,
        fillRect: (Int, Int, Int, Int, TileType) -> Unit
    ): List<Pair<Int, Int>> {
        // Gatehouse into Tier 1 Lower Barracks
        fillRect(23, 35, 25, 36, spec.floorTile)
        g[36][23] = TileType.IRON_BAR_GATE; g[36][24] = TileType.IRON_BAR_GATE; g[36][25] = TileType.IRON_BAR_GATE

        // Tier 1: Fortified Lower Barracks with barred internal partitions
        fillRect(9, 30, 39, 35, spec.floorTile)
        for (y in 30..35) {
            g[y][18] = if (y in 32..33) TileType.IRON_BAR_GATE else spec.wallTile
            g[y][30] = if (y in 32..33) TileType.IRON_BAR_GATE else spec.wallTile
        }

        // Ascent Corridors to Tier 2
        fillRect(12, 27, 14, 29, spec.accentFloorTile)
        fillRect(34, 27, 36, 29, spec.accentFloorTile)
        fillRect(23, 27, 25, 29, spec.accentFloorTile)
        g[29][23] = TileType.IRON_BAR_GATE; g[29][24] = TileType.IRON_BAR_GATE; g[29][25] = TileType.IRON_BAR_GATE

        // Tier 2: Twin Granite Bastion Vaults flanking a sunken central trench
        fillRect(8, 20, 18, 27, spec.accentFloorTile)
        fillRect(30, 20, 40, 27, spec.accentFloorTile)
        listOf(10 to 22, 16 to 22, 32 to 22, 38 to 22).forEach { (px, py) -> g[py][px] = spec.wallTile }
        fillRect(19, 21, 29, 26, spec.hazardTile)
        fillRect(23, 20, 25, 27, spec.bridgeTile)
        fillRect(19, 23, 29, 24, spec.bridgeTile)

        // Upper Vault Approaches to Tier 3
        fillRect(12, 13, 15, 19, spec.floorTile)
        fillRect(33, 13, 36, 19, spec.floorTile)
        fillRect(23, 17, 25, 19, spec.floorTile)
        g[17][23] = TileType.IRON_BAR_GATE; g[17][24] = TileType.IRON_BAR_GATE; g[17][25] = TileType.IRON_BAR_GATE

        // Tier 3: Crag Vault Boss Sanctum
        fillRect(16, 7, 32, 16, spec.accentFloorTile)
        listOf(19 to 9, 29 to 9, 19 to 14, 29 to 14).forEach { (px, py) -> g[py][px] = spec.wallTile }

        placeVeins(
            g,
            spec,
            listOf(9 to 31, 39 to 31, 8 to 21, 40 to 21),
            listOf(8 to 26, 40 to 26, 17 to 8, 31 to 8)
        )
        placeTorches(
            g,
            listOf(
                24 to 43, 22 to 36, 26 to 36, 18 to 30, 30 to 30,
                11 to 28, 37 to 28, 19 to 20, 29 to 20, 22 to 17, 26 to 17, 24 to 6
            )
        )

        return listOf(
            13 to 33, 24 to 33, 35 to 33, 13 to 24,
            35 to 24, 24 to 23, 13 to 21, 35 to 21, 24 to 18
        )
    }

    private fun carveCalderaSanctum(
        g: Array<Array<TileType>>,
        spec: ContinentalDungeonSpec,
        fillRect: (Int, Int, Int, Int, TileType) -> Unit
    ): List<Pair<Int, Int>> {
        // Southern Basalt Entry Causeway
        fillRect(22, 34, 26, 36, spec.floorTile)
        g[36][23] = TileType.IRON_BAR_GATE; g[36][24] = TileType.IRON_BAR_GATE; g[36][25] = TileType.IRON_BAR_GATE

        // Outer Obsidian Rim Ring + Flanking Forge Bastions
        fillRect(8, 31, 40, 34, spec.floorTile)
        fillRect(6, 15, 12, 32, spec.accentFloorTile)
        fillRect(36, 15, 42, 32, spec.accentFloorTile)
        fillRect(8, 13, 40, 15, spec.floorTile)

        // Molten Lava Caldera Lake
        fillRect(13, 16, 35, 30, spec.hazardTile)

        // Central Volcanic Ritual Island + Four Cardinal Basalt Bridges
        fillRect(19, 19, 29, 27, spec.accentFloorTile)
        listOf(20 to 20, 28 to 20, 20 to 26, 28 to 26).forEach { (px, py) -> g[py][px] = spec.wallTile }
        fillRect(23, 27, 25, 31, spec.bridgeTile) // South Bridge
        fillRect(23, 15, 25, 19, spec.bridgeTile) // North Bridge
        fillRect(12, 22, 19, 24, spec.bridgeTile) // West Bridge
        fillRect(29, 22, 36, 24, spec.bridgeTile) // East Bridge

        // Northern Ember Wyrm Sanctum
        fillRect(16, 6, 32, 13, spec.accentFloorTile)
        g[14][23] = TileType.IRON_BAR_GATE; g[14][24] = TileType.IRON_BAR_GATE; g[14][25] = TileType.IRON_BAR_GATE
        g[7][17] = spec.hazardTile; g[7][31] = spec.hazardTile
        listOf(19 to 8, 29 to 8, 19 to 12, 29 to 12).forEach { (px, py) -> g[py][px] = spec.wallTile }

        placeVeins(
            g,
            spec,
            listOf(7 to 17, 41 to 17, 9 to 33, 39 to 33),
            listOf(7 to 29, 41 to 29, 18 to 7, 30 to 7)
        )
        placeTorches(
            g,
            listOf(
                24 to 43, 21 to 36, 27 to 36, 5 to 23, 43 to 23,
                12 to 35, 36 to 35, 15 to 10, 33 to 10, 22 to 14, 26 to 14, 24 to 5
            )
        )

        return listOf(
            12 to 32, 36 to 32, 9 to 24, 9 to 19,
            39 to 24, 39 to 19, 24 to 23, 22 to 21, 26 to 25
        )
    }

    private fun placeVeins(
        g: Array<Array<TileType>>,
        spec: ContinentalDungeonSpec,
        primarySpots: List<Pair<Int, Int>>,
        secondarySpots: List<Pair<Int, Int>>
    ) {
        primarySpots.forEach { (vx, vy) ->
            if (g[vy][vx].walkable) g[vy][vx] = spec.primaryOre
        }
        secondarySpots.forEach { (vx, vy) ->
            if (g[vy][vx].walkable) g[vy][vx] = spec.secondaryOre
        }
    }

    private fun placeTorches(g: Array<Array<TileType>>, spots: List<Pair<Int, Int>>) {
        spots.forEach { (tx, ty) ->
            if (ty in 1 until (GRID_SIZE - 1) && tx in 1 until (GRID_SIZE - 1)) {
                g[ty][tx] = TileType.TORCH_WALL
            }
        }
    }

    private fun hash(a: Int, b: Int): Int {
        var h = a * 374761393 + b * 668265263 + 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7FFFFFFF
    }
}
