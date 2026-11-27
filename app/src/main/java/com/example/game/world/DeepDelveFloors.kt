package com.example.game.world

import com.example.game.model.MonsterType
import com.example.game.model.TileType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The four dungeon floors beneath Rookvale (-1..-4), each a 96x96 hand-shaped layout with its own
 * structure and set pieces:
 *
 * - **-1 Briar Catacombs**: a tight ossuary labyrinth with burial niches, a desecrated chapel,
 *   a flooded cistern, charnel pits and a web-choked spider collapse leading down to Floor -16.
 * - **-2 Watchwood Caverns**: organic root-choked caves with an underground stream, an outlaw
 *   hideout, a glowcrystal grotto and smouldering ember fissures toward the Molten Pit (-21) and
 *   the Deep Delve stairs / rope hole to Floor -3.
 * - **-3 Abyssal Crypt**: a sunken necropolis of districts on causeways over a black abyss: ruined
 *   cathedral, mausoleum row, bone gardens, a ritual circle and the unprotected Deep Forge Outpost.
 * - **-4 Obsidian Wyrm Sanctum**: a volcanic caldera of obsidian islands and ridges over a lava sea
 *   climbing to the Elder Wyrm's throne.
 *
 * Every portal/landing coordinate lives here so [WorldMaps] portals, camps and the Lower Boss Lair
 * specs in [ContinentalDungeonGenerator] can never drift out of sync with the tiles.
 */
object DeepDelveFloors {
    const val SIZE = 96

    // ---------------- Floor -1: Briar Catacombs ----------------
    const val F1_STAIRS_UP_X = 47
    const val F1_STAIRS_UP_Y = 6
    const val F1_LANDING_X = 47
    const val F1_LANDING_Y = 7
    const val F1_BROOD_STAIRS_X = 82
    const val F1_BROOD_STAIRS_Y = 86
    const val F1_BROOD_RETURN_X = 82
    const val F1_BROOD_RETURN_Y = 85

    // ---------------- Floor -2: Watchwood Caverns ----------------
    const val F2_STAIRS_UP_X = 48
    const val F2_STAIRS_UP_Y = 89
    const val F2_LANDING_X = 48
    const val F2_LANDING_Y = 88
    const val F2_PIT_STAIRS_X = 48
    const val F2_PIT_STAIRS_Y = 8
    const val F2_PIT_RETURN_X = 48
    const val F2_PIT_RETURN_Y = 9
    const val F2_CRYPT_STAIRS_X = 22
    const val F2_CRYPT_STAIRS_Y = 11
    const val F2_CRYPT_RETURN_X = 22
    const val F2_CRYPT_RETURN_Y = 12
    const val F2_ROPE_HOLE_X = 80
    const val F2_ROPE_HOLE_Y = 13
    const val F2_ROPE_RETURN_X = 79
    const val F2_ROPE_RETURN_Y = 13

    // ---------------- Floor -3: Abyssal Crypt ----------------
    const val F3_STAIRS_UP_X = 48
    const val F3_STAIRS_UP_Y = 6
    const val F3_LANDING_X = 48
    const val F3_LANDING_Y = 7
    const val F3_ROPE_SPOT_X = 89
    const val F3_ROPE_SPOT_Y = 48
    const val F3_ROPE_LANDING_X = 88
    const val F3_ROPE_LANDING_Y = 48
    const val F3_DESCENT_X = 48
    const val F3_DESCENT_Y = 90
    const val F3_DESCENT_RETURN_X = 48
    const val F3_DESCENT_RETURN_Y = 89
    const val F3_ROPE_HOLE_X = 6
    const val F3_ROPE_HOLE_Y = 48
    const val F3_ROPE_HOLE_RETURN_X = 7
    const val F3_ROPE_HOLE_RETURN_Y = 48

    // ---------------- Floor -4: Obsidian Wyrm Sanctum ----------------
    const val F4_STAIRS_UP_X = 48
    const val F4_STAIRS_UP_Y = 89
    const val F4_LANDING_X = 48
    const val F4_LANDING_Y = 88
    const val F4_ROPE_SPOT_X = 10
    const val F4_ROPE_SPOT_Y = 66
    const val F4_ROPE_LANDING_X = 11
    const val F4_ROPE_LANDING_Y = 66

    /** Forward-camp safe zones (PZ) for the four floors. */
    val camps: List<UndergroundCampZone> = listOf(
        UndergroundCampZone(
            floorId = -1, name = "CATACOMBS VANGUARD CAMP (PZ)",
            minX = 42, minY = 5, maxX = 52, maxY = 11,
            healerX = 43, healerY = 9, healerName = "Sister Mara",
            merchantX = 51, merchantY = 9, merchantName = "Quartermaster Bram"
        ),
        UndergroundCampZone(
            floorId = -2, name = "DEEP CAVERNS FORWARD CAMP (PZ)",
            minX = 41, minY = 83, maxX = 55, maxY = 90,
            healerX = 43, healerY = 86, healerName = "Brother Tavin",
            merchantX = 53, merchantY = 86, merchantName = "Alchemist Veyra"
        ),
        UndergroundCampZone(
            floorId = -3, name = "ABYSSAL CRYPT FORWARD CAMP (PZ)",
            minX = 41, minY = 5, maxX = 55, maxY = 11,
            healerX = 43, healerY = 9, healerName = "Chaplain Oren",
            merchantX = 53, merchantY = 9, merchantName = "Supplier Kora"
        ),
        UndergroundCampZone(
            floorId = -4, name = "WYRM SANCTUM VANGUARD CAMP (PZ)",
            minX = 41, minY = 83, maxX = 55, maxY = 90,
            healerX = 43, healerY = 86, healerName = "Priestess Selene",
            merchantX = 53, merchantY = 86, merchantName = "Quartermaster Dax"
        )
    )

    fun campFor(floorId: Int): UndergroundCampZone = camps.first { it.floorId == floorId }

    /** Result of building one floor: tiles plus everything placed on them. */
    class Built(
        val floorId: Int,
        val tiles: Array<Array<TileType>>,
        val spawns: List<MonsterSpawn>,
        val landmarks: List<LandmarkLabel>
    )

    val briarCatacombs: Built by lazy { buildBriarCatacombs() }
    val watchwoodCaverns: Built by lazy { buildWatchwoodCaverns() }
    val abyssalCrypt: Built by lazy { buildAbyssalCrypt() }
    val wyrmSanctum: Built by lazy { buildWyrmSanctum() }

    val all: List<Built> get() = listOf(briarCatacombs, watchwoodCaverns, abyssalCrypt, wyrmSanctum)

    fun builtFor(floorId: Int): Built = when (floorId) {
        -1 -> briarCatacombs
        -2 -> watchwoodCaverns
        -3 -> abyssalCrypt
        -4 -> wyrmSanctum
        else -> error("Floor $floorId is not a Deep Delve floor")
    }

    // =====================================================================================
    // Floor -1: Briar Catacombs
    // =====================================================================================
    private fun buildBriarCatacombs(): Built {
        val c = Canvas(SIZE, TileType.CAVE_WALL, seed = 101)
        val camp = campFor(-1)

        // Gatehouse camp (north centre)
        c.rect(41, 4, 53, 12, TileType.CRYPT_FLOOR)
        c.rect(45, 8, 49, 10, TileType.COBBLESTONE)
        c[42, 5] = TileType.WAR_BANNER; c[52, 5] = TileType.WAR_BANNER
        c[42, 11] = TileType.CAMPFIRE_BRAZIER; c[52, 11] = TileType.CAMPFIRE_BRAZIER
        c[F1_STAIRS_UP_X, F1_STAIRS_UP_Y] = TileType.STAIRS_UP

        // Iron-barred gate south into the Bone Hall
        c.rect(46, 13, 48, 16, TileType.CRYPT_FLOOR)
        for (x in 46..48) c[x, 13] = TileType.IRON_BAR_GATE
        c.rect(34, 17, 60, 22, TileType.CRYPT_FLOOR)
        for (x in 37..57 step 5) { c[x, 18] = TileType.STONE_WALL; c[x, 21] = TileType.STONE_WALL }

        // Ossuary Maze (west): 7x12 cell labyrinth, 2-wide passages, a few loops, burial niches
        val mazeX = 5; val mazeY = 15; val cols = 7; val rows = 12
        c.maze(mazeX, mazeY, cols, rows, pitch = 4, passage = 2, extraLoops = 14, tile = TileType.CRYPT_FLOOR)
        c.rect(31, 19, 33, 20, TileType.CRYPT_FLOOR)                     // maze <-> Bone Hall
        c.burialNiches(4, 14, 34, 62, TileType.CRYPT_FLOOR, density = 9)

        // Desecrated Chapel (east)
        c.rect(61, 19, 63, 20, TileType.CRYPT_FLOOR)                     // Bone Hall -> chapel
        c.rect(64, 13, 88, 36, TileType.TEMPLE_FLOOR)
        for (y in 15..34 step 3) { c[67, y] = TileType.STONE_WALL; c[85, y] = TileType.STONE_WALL }
        for (y in 21..33 step 2) {
            for (x in 70..74) c[x, y] = if (c.hash(x, y) % 5 == 0) TileType.CRYPT_FLOOR else TileType.TEMPLE_PEW
            for (x in 78..82) c[x, y] = if (c.hash(x, y) % 5 == 0) TileType.CRYPT_FLOOR else TileType.TEMPLE_PEW
        }
        for (x in 75..77) c[x, 15] = TileType.TEMPLE_ALTAR
        c[72, 15] = TileType.CRYSTAL_PILLAR; c[80, 15] = TileType.CRYSTAL_PILLAR
        c.rect(75, 37, 77, 41, TileType.CRYPT_FLOOR)                     // chapel south gate
        for (x in 75..77) c[x, 37] = TileType.IRON_BAR_GATE

        // Flooded Cistern (centre): pillared pool, cross of stone bridges, central island
        c.rect(46, 23, 48, 31, TileType.CRYPT_FLOOR)
        c.rect(34, 32, 66, 58, TileType.CRYPT_FLOOR)
        c.rect(38, 36, 62, 54, TileType.WATER)
        for (y in 38..52 step 5) for (x in 40..60 step 5) c[x, y] = TileType.STONE_WALL
        c.rect(49, 36, 50, 54, TileType.STONE_BRIDGE)
        c.rect(38, 44, 62, 45, TileType.STONE_BRIDGE)
        c.rect(46, 42, 53, 47, TileType.CRYPT_FLOOR)
        c.rect(31, 47, 33, 48, TileType.CRYPT_FLOOR)                     // maze <-> cistern
        c[32, 47] = TileType.IRON_BAR_GATE; c[32, 48] = TileType.IRON_BAR_GATE
        c.rect(67, 44, 70, 45, TileType.CRYPT_FLOOR)                     // cistern -> east tunnel

        // Charnel Pits (south-west): three linked vaults with sarcophagi and a bone altar
        c.rect(13, 61, 14, 67, TileType.CRYPT_FLOOR)                     // maze -> vault A
        c.rect(8, 68, 20, 78, TileType.CRYPT_FLOOR)
        c.rect(24, 69, 38, 82, TileType.CRYPT_FLOOR)
        c.rect(9, 82, 22, 90, TileType.CRYPT_FLOOR)
        c.rect(21, 72, 23, 73, TileType.CRYPT_FLOOR)
        c.rect(13, 79, 14, 81, TileType.CRYPT_FLOOR)
        c.rect(23, 86, 30, 87, TileType.CRYPT_FLOOR)
        c.rect(29, 83, 30, 85, TileType.CRYPT_FLOOR)
        for ((x, y) in listOf(10 to 70, 16 to 70, 10 to 75, 16 to 75, 12 to 84, 18 to 84, 12 to 88, 18 to 88)) {
            c[x, y] = TileType.STONE_WALL; c[x + 1, y] = TileType.STONE_WALL
        }
        c.rect(28, 73, 34, 78, TileType.DIRT_FLOOR)
        c[31, 75] = TileType.TEMPLE_ALTAR
        c.rect(39, 76, 52, 77, TileType.CRYPT_FLOOR)                     // vault B -> south hall
        c.rect(50, 59, 51, 75, TileType.CRYPT_FLOOR)                     // cistern -> south hall

        // Spider Collapse (south-east): web-choked organic cave to the Brood stairs
        val spiderFloor = TileType.DIRT_FLOOR
        c.blob(62, 78, 8, spiderFloor)
        c.blob(74, 68, 7, spiderFloor)
        c.blob(80, 84, 8, spiderFloor)
        c.blob(85, 56, 6, spiderFloor)
        c.blob(69, 88, 5, spiderFloor)
        c.tunnel(listOf(52 to 77, 62 to 78), 3, spiderFloor)
        c.tunnel(listOf(62 to 78, 74 to 68, 85 to 56), 3, spiderFloor)
        c.tunnel(listOf(70 to 45, 78 to 46, 85 to 56), 2, spiderFloor)
        c.tunnel(listOf(76 to 41, 80 to 48), 2, spiderFloor)
        c.tunnel(listOf(74 to 68, 80 to 84), 2, spiderFloor)
        c.tunnel(listOf(62 to 78, 69 to 88, 80 to 84), 2, spiderFloor)
        c[F1_BROOD_STAIRS_X, F1_BROOD_STAIRS_Y] = TileType.STAIRS_DOWN
        c[F1_BROOD_RETURN_X, F1_BROOD_RETURN_Y] = spiderFloor

        c.sealPockets(F1_LANDING_X, F1_LANDING_Y)
        c.ores(TileType.ORE_BOG_IRON, count = 6, x1 = 4, y1 = 14, x2 = 40, y2 = 92)
        c.ores(TileType.ORE_VESPERITE, count = 6, x1 = 56, y1 = 40, x2 = 92, y2 = 92)
        c.torches(spacing = 6)

        val spawns = c.spawns(
            -1, camp,
            MonsterType.BRIAR_CRYPT_CRAWLER to (10 to 20), MonsterType.SKELETON to (22 to 28),
            MonsterType.BRIAR_CRYPT_CRAWLER to (14 to 40), MonsterType.SKELETON to (26 to 52),
            MonsterType.BRIAR_CRYPT_CRAWLER to (8 to 58), MonsterType.CATACOMB_BONE_ARCHER to (40 to 19),
            MonsterType.CATACOMB_BONE_ARCHER to (76 to 26), MonsterType.SKELETON to (70 to 32),
            MonsterType.CATACOMB_BONE_ARCHER to (82 to 20), MonsterType.BRIAR_CRYPT_CRAWLER to (36 to 34),
            MonsterType.CATACOMB_BONE_ARCHER to (64 to 56), MonsterType.SKELETON to (50 to 44),
            MonsterType.SKELETON to (12 to 72), MonsterType.BRIAR_CRYPT_CRAWLER to (30 to 80),
            MonsterType.CATACOMB_BONE_ARCHER to (16 to 86), MonsterType.SKELETON to (36 to 70),
            MonsterType.POISON_SPIDER to (62 to 78), MonsterType.POISON_SPIDER to (74 to 68),
            MonsterType.BRIAR_CRYPT_CRAWLER to (85 to 56), MonsterType.POISON_SPIDER to (78 to 82),
            MonsterType.POISON_SPIDER to (69 to 88), MonsterType.BRIAR_CRYPT_CRAWLER to (86 to 80)
        )
        val landmarks = listOf(
            LandmarkLabel(-1, 18, 38, "OSSUARY MAZE"),
            LandmarkLabel(-1, 76, 24, "DESECRATED CHAPEL"),
            LandmarkLabel(-1, 50, 45, "FLOODED CISTERN"),
            LandmarkLabel(-1, 22, 78, "CHARNEL PITS"),
            LandmarkLabel(-1, 74, 72, "SPIDER COLLAPSE"),
            LandmarkLabel(-1, F1_BROOD_STAIRS_X, F1_BROOD_STAIRS_Y - 2, "DESCENT TO BRIAR BROOD SANCTUM")
        )
        return Built(-1, c.g, spawns, landmarks)
    }

    // =====================================================================================
    // Floor -2: Watchwood Caverns
    // =====================================================================================
    private fun buildWatchwoodCaverns(): Built {
        val c = Canvas(SIZE, TileType.CAVE_WALL, seed = 202)
        val camp = campFor(-2)
        val cave = TileType.DIRT_FLOOR
        val ember = TileType.OBSIDIAN_FLOOR

        // Rootway (south) caverns and the forward camp
        c.blob(48, 86, 7, cave)
        c.rect(41, 83, 55, 90, cave)
        c.blob(52, 70, 8, cave)
        c.blob(30, 72, 9, cave)
        c.blob(72, 74, 9, cave)
        c.blob(16, 62, 7, cave)
        c.blob(84, 82, 5, cave)
        c.tunnel(listOf(48 to 83, 52 to 70), 3, cave)
        c.tunnel(listOf(52 to 70, 30 to 72, 16 to 62), 3, cave)
        c.tunnel(listOf(52 to 70, 72 to 74, 84 to 82), 3, cave)

        // Central Hollow
        c.blob(50, 46, 8, cave)
        c.tunnel(listOf(52 to 70, 50 to 46), 3, cave)

        // Outlaw Hideout (west): timber palisade camp with crates and weapon racks
        c.blob(17, 38, 11, cave)
        c.tunnel(listOf(16 to 62, 17 to 46), 3, cave)
        c.tunnel(listOf(26 to 38, 50 to 46), 3, cave)
        c.outline(9, 31, 25, 45, TileType.TIMBER_WALL)
        c.rect(10, 32, 24, 44, TileType.WOOD_PLANK_FLOOR)
        c.rect(16, 31, 18, 31, TileType.WOOD_PLANK_FLOOR)                 // north gate
        c.rect(16, 45, 18, 45, TileType.WOOD_PLANK_FLOOR)                 // south gate
        c.rect(25, 37, 25, 39, TileType.WOOD_PLANK_FLOOR)                 // east gate
        c.rect(9, 37, 9, 39, TileType.WOOD_PLANK_FLOOR)                   // west postern
        for (x in listOf(11, 12, 22, 23)) { c[x, 33] = TileType.CRATE_BARREL; c[x, 43] = TileType.CRATE_BARREL }
        for (y in 35..41 step 2) { c[10, y] = TileType.WEAPON_RACK; c[24, y] = TileType.WEAPON_RACK }
        c[17, 38] = TileType.CAMPFIRE_BRAZIER
        c[15, 30] = TileType.WAR_BANNER; c[19, 30] = TileType.WAR_BANNER

        // Glowcrystal Grotto (east)
        c.blob(78, 44, 11, cave)
        c.blob(86, 30, 6, cave)
        c.tunnel(listOf(72 to 74, 78 to 55), 3, cave)
        c.tunnel(listOf(58 to 46, 68 to 44), 3, cave)
        c.tunnel(listOf(80 to 34, 86 to 30), 2, cave)
        c.scatter(66, 32, 90, 56, TileType.CRYSTAL_PILLAR, density = 18, onlyOn = setOf(cave), keepClear = 2)

        // Underground stream cutting across the rootway; the tunnels that cross it get plank bridges
        c.stream(listOf(3 to 64, 24 to 60, 44 to 63, 64 to 60, 92 to 64), width = 2, water = TileType.WATER)
        c.tunnel(listOf(52 to 70, 50 to 46), 3, cave, bridge = TileType.WOOD_BRIDGE)
        c.tunnel(listOf(16 to 62, 17 to 46), 3, cave, bridge = TileType.WOOD_BRIDGE)
        c.tunnel(listOf(72 to 74, 78 to 55), 3, cave, bridge = TileType.WOOD_BRIDGE)

        // Roots and dark loam through the rootway
        c.scatterReplace(4, 54, 92, 92, cave, TileType.DARK_GRASS, density = 5)
        c.scatter(4, 54, 92, 82, TileType.TREE, density = 22, onlyOn = setOf(cave, TileType.DARK_GRASS), keepClear = 2)

        // Ember Fissures (north): obsidian caverns split by a lava crack, gate to the Molten Pit
        c.blob(48, 18, 11, ember)
        c.blob(24, 15, 8, ember)
        c.blob(74, 16, 8, ember)
        c.tunnel(listOf(50 to 46, 48 to 28), 3, ember)
        c.tunnel(listOf(17 to 30, 24 to 20), 3, ember)
        c.tunnel(listOf(86 to 30, 78 to 22), 2, ember)
        c.tunnel(listOf(32 to 16, 40 to 18), 3, ember)
        c.tunnel(listOf(58 to 18, 68 to 16), 3, ember)
        c.scatterReplace(14, 6, 86, 30, ember, TileType.VOLCANIC_ASH, density = 4)
        c.stream(listOf(10 to 26, 34 to 23, 50 to 26, 66 to 23, 88 to 25), width = 2, water = TileType.LAVA)
        c.tunnel(listOf(50 to 46, 48 to 28), 3, ember)
        c.tunnel(listOf(17 to 30, 24 to 20), 3, ember)
        c.tunnel(listOf(86 to 30, 78 to 22), 2, ember)
        c.rect(45, 6, 51, 10, ember)
        for (x in 45..51) c[x, 11] = TileType.IRON_BAR_GATE
        c[F2_PIT_STAIRS_X, F2_PIT_STAIRS_Y] = TileType.STAIRS_DOWN
        c[F2_CRYPT_STAIRS_X, F2_CRYPT_STAIRS_Y] = TileType.STAIRS_DOWN
        c[F2_ROPE_HOLE_X, F2_ROPE_HOLE_Y] = TileType.ROPE_HOLE_DOWN
        for ((x, y) in listOf(F2_PIT_RETURN_X to F2_PIT_RETURN_Y, F2_CRYPT_RETURN_X to F2_CRYPT_RETURN_Y, F2_ROPE_RETURN_X to F2_ROPE_RETURN_Y)) {
            c[x, y] = ember
        }

        // Camp decor and stairs
        c.rect(45, 85, 51, 88, TileType.COBBLESTONE)
        c[41, 83] = TileType.WAR_BANNER; c[55, 83] = TileType.WAR_BANNER
        c[41, 90] = TileType.CAMPFIRE_BRAZIER; c[55, 90] = TileType.CAMPFIRE_BRAZIER
        c[F2_STAIRS_UP_X, F2_STAIRS_UP_Y] = TileType.STAIRS_UP

        c.sealPockets(F2_LANDING_X, F2_LANDING_Y)
        c.ores(TileType.ORE_MIRE_SILVER, count = 4, x1 = 62, y1 = 28, x2 = 94, y2 = 60)
        c.ores(TileType.ORE_PEAT_COAL, count = 4, x1 = 4, y1 = 54, x2 = 94, y2 = 92)
        c.ores(TileType.ORE_VESPERITE, count = 5, x1 = 4, y1 = 28, x2 = 60, y2 = 60)
        c.ores(TileType.ORE_CINDERITE, count = 6, x1 = 4, y1 = 2, x2 = 94, y2 = 32)
        c.torches(spacing = 7)

        val spawns = c.spawns(
            -2, camp,
            MonsterType.WATCHWOOD_DREAD_KNIGHT to (30 to 72), MonsterType.GHOUL to (16 to 62),
            MonsterType.WATCHWOOD_DREAD_KNIGHT to (72 to 74), MonsterType.GHOUL to (84 to 82),
            MonsterType.WATCHWOOD_DREAD_KNIGHT to (14 to 36), MonsterType.WATCHWOOD_DREAD_KNIGHT to (21 to 41),
            MonsterType.CINDER_IMP to (17 to 48), MonsterType.GHOUL to (50 to 46),
            MonsterType.WATCHWOOD_DREAD_KNIGHT to (56 to 52), MonsterType.GHOUL to (78 to 44),
            MonsterType.CINDER_IMP to (84 to 50), MonsterType.GHOUL to (86 to 30),
            MonsterType.CINDER_IMP to (24 to 15), MonsterType.FIRE_DEVIL to (40 to 18),
            MonsterType.FIRE_DEVIL to (48 to 20), MonsterType.CINDER_IMP to (58 to 16),
            MonsterType.FIRE_DEVIL to (74 to 16), MonsterType.CINDER_IMP to (48 to 30),
            MonsterType.WATCHWOOD_DREAD_KNIGHT to (66 to 70), MonsterType.FIRE_DEVIL to (30 to 22)
        )
        val landmarks = listOf(
            LandmarkLabel(-2, 17, 38, "OUTLAW HIDEOUT"),
            LandmarkLabel(-2, 78, 44, "GLOWCRYSTAL GROTTO"),
            LandmarkLabel(-2, 48, 66, "ROOTWAY STREAM"),
            LandmarkLabel(-2, 50, 46, "CENTRAL HOLLOW"),
            LandmarkLabel(-2, 48, 20, "EMBER FISSURES"),
            LandmarkLabel(-2, F2_PIT_STAIRS_X, F2_PIT_STAIRS_Y + 3, "DESCENT TO MOLTEN PIT THRONE"),
            LandmarkLabel(-2, F2_CRYPT_STAIRS_X, F2_CRYPT_STAIRS_Y + 3, "CRYPT STAIRS (FLOOR -3)")
        )
        return Built(-2, c.g, spawns, landmarks)
    }

    // =====================================================================================
    // Floor -3: Abyssal Crypt
    // =====================================================================================
    private fun buildAbyssalCrypt(): Built {
        val c = Canvas(SIZE, TileType.CAVE_WALL, seed = 303)
        val camp = campFor(-3)
        val abyss = TileType.DEEP_WATER
        val ground = TileType.OBSIDIAN_FLOOR
        val road = TileType.COBBLESTONE
        val bridge = TileType.STONE_BRIDGE

        c.rect(4, 13, 91, 93, abyss)

        // Forward camp (north)
        c.rect(40, 4, 56, 12, ground)
        c.rect(45, 7, 51, 10, road)
        c[41, 5] = TileType.OBSIDIAN_WALL; c[55, 5] = TileType.OBSIDIAN_WALL
        c[41, 11] = TileType.CAMPFIRE_BRAZIER; c[55, 11] = TileType.CAMPFIRE_BRAZIER
        c[44, 5] = TileType.WAR_BANNER; c[52, 5] = TileType.WAR_BANNER
        c[F3_STAIRS_UP_X, F3_STAIRS_UP_Y] = TileType.STAIRS_UP

        // District islands
        c.rect(10, 24, 40, 42, ground)   // NW: Ruined Cathedral
        c.rect(56, 24, 86, 42, ground)   // NE: Mausoleum Row
        c.rect(10, 54, 40, 70, ground)   // SW: Bone Gardens
        c.rect(56, 54, 86, 70, ground)   // SE: Deep Forge Outpost
        c.rect(34, 78, 62, 92, ground)   // S:  Gate of the Deep
        for ((x1, y1, x2, y2) in listOf(listOf(10, 24, 40, 42), listOf(56, 24, 86, 42), listOf(10, 54, 40, 70), listOf(56, 54, 86, 70), listOf(34, 78, 62, 92))) {
            c.ragged(x1, y1, x2, y2, depth = 2, from = ground, into = abyss, chance = 3)
        }
        c.blob(85, 25, 4, abyss)                                          // collapsed corner of Mausoleum Row
        c.blob(11, 69, 3, abyss)                                          // sinkhole in the Bone Gardens

        // Grand causeways (N-S and E-W), bridges where they cross open abyss
        c.rect(46, 13, 50, 91, road)
        c.rect(8, 46, 88, 50, road)
        for (y in 13..23) for (x in 46..50) c[x, y] = bridge
        for (y in 71..77) for (x in 46..50) c[x, y] = bridge
        for (x in 41..45) for (y in 46..50) c[x, y] = bridge
        for (x in 51..55) for (y in 46..50) c[x, y] = bridge
        for (x in 8..9) for (y in 46..50) c[x, y] = bridge
        for (x in 87..88) for (y in 46..50) c[x, y] = bridge
        c.rect(4, 45, 9, 51, ground)                                         // west rope ledge
        c.rect(86, 45, 91, 51, ground)                                       // east rope ledge
        c[F3_ROPE_HOLE_X, F3_ROPE_HOLE_Y] = TileType.ROPE_HOLE_DOWN
        c[F3_ROPE_SPOT_X, F3_ROPE_SPOT_Y] = TileType.ROPE_SPOT_UP

        // Ritual Circle at the crossing
        c.ellipse(48, 48, 7, 7, ground)
        c.ring(48, 48, 4.5, 6.5, TileType.ARCANE_FLOOR)
        c.rect(46, 41, 50, 55, road); c.rect(41, 46, 55, 50, road)
        c.ring(48, 48, 4.5, 6.5, TileType.ARCANE_FLOOR, onlyOn = setOf(road, ground))
        for ((x, y) in listOf(43 to 43, 53 to 43, 43 to 53, 53 to 53)) c[x, y] = TileType.CRYSTAL_PILLAR

        // Short bridges linking districts to the E-W causeway
        c.rect(24, 43, 26, 45, bridge); c.rect(70, 43, 72, 45, bridge)
        c.rect(24, 51, 26, 53, bridge); c.rect(70, 51, 72, 53, bridge)
        c.rect(41, 32, 45, 34, bridge); c.rect(51, 32, 55, 34, bridge)       // districts <-> N-S causeway
        c.rect(41, 62, 45, 64, bridge); c.rect(51, 62, 55, 64, bridge)
        // solid landings where those bridges meet the eroded island edges
        c.rect(24, 40, 26, 42, ground); c.rect(70, 40, 72, 42, ground)
        c.rect(24, 54, 26, 56, ground); c.rect(70, 54, 72, 56, ground)
        c.rect(38, 32, 40, 34, ground); c.rect(56, 32, 58, 34, ground)
        c.rect(38, 62, 40, 64, ground); c.rect(56, 62, 58, 64, ground)

        // NW Ruined Cathedral
        c.outline(12, 26, 38, 40, TileType.TEMPLE_WALL)
        c.rect(13, 27, 37, 39, TileType.TEMPLE_FLOOR)
        c.rect(24, 40, 26, 40, TileType.TEMPLE_FLOOR)                        // south door
        c.rect(38, 32, 38, 34, TileType.TEMPLE_FLOOR)                        // east door
        for (x in 15..35 step 4) { c[x, 29] = TileType.STONE_WALL; c[x, 37] = TileType.STONE_WALL }
        for (y in 31..36 step 2) for (x in 16..34) if (x !in 24..26 && c.hash(x, y) % 4 != 0) c[x, y] = TileType.TEMPLE_PEW
        for (x in 23..27) c[x, 28] = TileType.TEMPLE_ALTAR
        c.scatterReplace(13, 27, 37, 39, TileType.TEMPLE_FLOOR, TileType.CRYPT_FLOOR, density = 5)

        // NE Mausoleum Row: walled tombs with doors facing the lane
        for (mx in 58..80 step 7) for (my in listOf(26, 35)) {
            c.outline(mx, my, mx + 4, my + 4, TileType.STONE_WALL)
            c.rect(mx + 1, my + 1, mx + 3, my + 3, TileType.TEMPLE_FLOOR)
            val doorY = if (my == 26) my + 4 else my
            c[mx + 2, doorY] = TileType.TEMPLE_FLOOR
            if (c.hash(mx, my) % 2 == 0) c[mx + 2, my + 2] = TileType.TEMPLE_ALTAR
        }

        // SW Bone Gardens: blighted soil, sarcophagi, ore outcrops
        c.scatterReplace(10, 54, 40, 70, ground, TileType.BLIGHT_SOIL, density = 2)
        for (y in 57..67 step 5) for (x in 13..37 step 6) { c[x, y] = TileType.STONE_WALL; c[x + 1, y] = TileType.STONE_WALL }

        // SE Deep Forge Outpost (no protection zone: tense risk/reward)
        c.rect(62, 56, 82, 68, road)
        c[66, 57] = TileType.SMELTING_FURNACE
        c[78, 57] = TileType.BLACKSMITH_ANVIL
        c[72, 62] = TileType.MINI_DEPOT
        c[64, 56] = TileType.CAMPFIRE_BRAZIER; c[80, 56] = TileType.CAMPFIRE_BRAZIER

        // Gate of the Deep (south) with stairs down to the Wyrm Sanctum
        c.rect(40, 84, 56, 92, road)
        for (x in listOf(38, 42, 54, 58)) for (y in listOf(80, 88)) c[x, y] = TileType.OBSIDIAN_WALL
        c[F3_DESCENT_X, F3_DESCENT_Y] = TileType.STAIRS_DOWN

        // Ore outcrops standing in the districts (mined from adjacent ground)
        c.sealPockets(F3_LANDING_X, F3_LANDING_Y)
        for ((x, y) in listOf(13 to 56, 37 to 67, 22 to 67, 83 to 67, 59 to 67, 83 to 57, 59 to 57, 38 to 90)) c[x, y] = TileType.ORE_VESPERITE
        for ((x, y) in listOf(16 to 67, 37 to 57, 58 to 90, 37 to 81, 59 to 81, 82 to 39, 59 to 27, 13 to 27)) c[x, y] = TileType.ORE_CINDERITE

        c.torches(spacing = 6)

        val spawns = c.spawns(
            -3, camp,
            MonsterType.NECROMANCER to (20 to 33), MonsterType.NECROMANCER to (32 to 33),
            MonsterType.GHOUL to (18 to 38), MonsterType.DRAGON_HATCHLING to (62 to 33),
            MonsterType.NECROMANCER to (76 to 33), MonsterType.GHOUL to (82 to 26),
            MonsterType.DRAGON_HATCHLING to (18 to 62), MonsterType.GHOUL to (32 to 58),
            MonsterType.NECROMANCER to (28 to 66), MonsterType.DRAGON_HATCHLING to (60 to 64),
            MonsterType.DRAGON_HATCHLING to (84 to 66), MonsterType.DEMON_LORD to (48 to 48),
            MonsterType.NECROMANCER to (48 to 84), MonsterType.DRAGON_HATCHLING to (38 to 86),
            MonsterType.DRAGON_HATCHLING to (58 to 86), MonsterType.GHOUL to (20 to 48)
        )
        val landmarks = listOf(
            LandmarkLabel(-3, 25, 33, "RUINED CATHEDRAL"),
            LandmarkLabel(-3, 71, 33, "MAUSOLEUM ROW"),
            LandmarkLabel(-3, 48, 48, "RITUAL CIRCLE"),
            LandmarkLabel(-3, 25, 62, "BONE GARDENS"),
            LandmarkLabel(-3, 72, 60, "DEEP FORGE OUTPOST"),
            LandmarkLabel(-3, 48, 86, "GATE OF THE DEEP")
        )
        return Built(-3, c.g, spawns, landmarks)
    }

    // =====================================================================================
    // Floor -4: Obsidian Wyrm Sanctum
    // =====================================================================================
    private fun buildWyrmSanctum(): Built {
        val c = Canvas(SIZE, TileType.CAVE_WALL, seed = 404)
        val camp = campFor(-4)
        val rock = TileType.OBSIDIAN_FLOOR
        val ash = TileType.VOLCANIC_ASH

        c.ellipse(48, 48, 45, 45, TileType.LAVA)

        // Southern vanguard bastion (camp)
        c.ellipse(48, 86, 11, 7, rock)
        c.rect(41, 83, 55, 90, rock)
        c.rect(45, 85, 51, 88, TileType.COBBLESTONE)
        c[41, 83] = TileType.OBSIDIAN_WALL; c[55, 83] = TileType.OBSIDIAN_WALL
        c[42, 90] = TileType.CAMPFIRE_BRAZIER; c[54, 90] = TileType.CAMPFIRE_BRAZIER
        c[44, 83] = TileType.WAR_BANNER; c[52, 83] = TileType.WAR_BANNER
        c[F4_STAIRS_UP_X, F4_STAIRS_UP_Y] = TileType.STAIRS_UP

        // Frost Vent Cliff (west) with the rope back up
        c.blob(12, 66, 7, rock)
        c[F4_ROPE_SPOT_X, F4_ROPE_SPOT_Y] = TileType.ROPE_SPOT_UP

        // Hatchling nest islands
        val nests = listOf(26 to 72, 70 to 70, 20 to 44, 76 to 46, 48 to 58)
        for ((i, n) in nests.withIndex()) {
            c.blob(n.first, n.second, if (i == 4) 6 else 7, rock)
            c.ellipse(n.first, n.second, 3, 2, ash)
        }

        // Elder Wyrm Throne plateau (north) with obsidian cover pillars
        c.ellipse(48, 24, 21, 13, rock)
        c.ellipse(48, 21, 7, 4, ash)
        for (a in 0 until 10) {
            val ang = a * Math.PI * 2 / 10
            c[(48 + cos(ang) * 14).toInt(), (24 + sin(ang) * 8).toInt()] = TileType.OBSIDIAN_WALL
        }

        // Ridges and bridges climbing from the bastion to the throne
        c.tunnel(listOf(42 to 82, 26 to 72, 20 to 44, 30 to 30), 2, rock)
        c.tunnel(listOf(54 to 82, 70 to 70, 76 to 46, 66 to 30), 2, rock)
        c.tunnel(listOf(26 to 72, 12 to 66), 2, rock)
        c.rect(47, 64, 49, 80, TileType.STONE_BRIDGE)
        c.rect(47, 37, 49, 52, TileType.STONE_BRIDGE)
        c.rect(27, 44, 46, 45, TileType.STONE_BRIDGE)                        // west nest <-> central span (risky shortcut)

        c.sealPockets(F4_LANDING_X, F4_LANDING_Y)

        // Veins: frozen vent minerals on the cliff, cinderite around the throne
        for ((x, y) in listOf(8 to 63, 15 to 71)) c[x, y] = TileType.ORE_GLACIER_QUARTZ
        for ((x, y) in listOf(8 to 69, 74 to 73)) c[x, y] = TileType.ORE_FROST_IRON
        for ((x, y) in listOf(32 to 18, 64 to 18, 30 to 29, 66 to 29, 48 to 13, 22 to 41, 79 to 49)) c[x, y] = TileType.ORE_CINDERITE

        c.torches(spacing = 8)

        val spawns = c.spawns(
            -4, camp,
            MonsterType.DRAGON_HATCHLING to (26 to 72), MonsterType.DRAGON_HATCHLING to (70 to 70),
            MonsterType.FIRE_DEVIL to (34 to 78), MonsterType.FIRE_DEVIL to (62 to 77),
            MonsterType.NECROMANCER to (48 to 58), MonsterType.DRAGON_HATCHLING to (20 to 44),
            MonsterType.FIRE_DEVIL to (23 to 58), MonsterType.DRAGON_HATCHLING to (76 to 46),
            MonsterType.FIRE_DEVIL to (74 to 58), MonsterType.NECROMANCER to (34 to 30),
            MonsterType.NECROMANCER to (62 to 30), MonsterType.FIRE_DEVIL to (12 to 66),
            MonsterType.DRAGON_HATCHLING to (40 to 22), MonsterType.ELDER_DRAGON to (48 to 20)
        )
        val landmarks = listOf(
            LandmarkLabel(-4, 48, 16, "ELDER WYRM THRONE"),
            LandmarkLabel(-4, 12, 62, "FROST VENT CLIFF"),
            LandmarkLabel(-4, 48, 58, "HATCHLING NESTS")
        )
        return Built(-4, c.g, spawns, landmarks)
    }

    // =====================================================================================
    // Drawing toolkit (deterministic: same map on every device and every launch)
    // =====================================================================================
    internal class Canvas(val size: Int, private val base: TileType, private val seed: Int) {
        val g: Array<Array<TileType>> = Array(size) { Array(size) { base } }

        private val wallTiles = setOf(TileType.CAVE_WALL, TileType.STONE_WALL)

        operator fun get(x: Int, y: Int): TileType = if (x in 0 until size && y in 0 until size) g[y][x] else base

        /** Writes inside the map, always keeping a 1-tile solid border. */
        operator fun set(x: Int, y: Int, t: TileType) {
            if (x in 1 until size - 1 && y in 1 until size - 1) g[y][x] = t
        }

        fun hash(x: Int, y: Int, salt: Int = 0): Int {
            var h = x * 374761393 + y * 668265263 + (seed + salt) * 1442695041
            h = (h xor (h ushr 13)) * 1274126177
            return (h xor (h ushr 16)) and 0x7FFFFFFF
        }

        fun rect(x1: Int, y1: Int, x2: Int, y2: Int, t: TileType) {
            for (y in min(y1, y2)..max(y1, y2)) for (x in min(x1, x2)..max(x1, x2)) this[x, y] = t
        }

        fun outline(x1: Int, y1: Int, x2: Int, y2: Int, t: TileType) {
            for (x in x1..x2) { this[x, y1] = t; this[x, y2] = t }
            for (y in y1..y2) { this[x1, y] = t; this[x2, y] = t }
        }

        fun ellipse(cx: Int, cy: Int, rx: Int, ry: Int, t: TileType) {
            for (y in cy - ry..cy + ry) for (x in cx - rx..cx + rx) {
                val dx = (x - cx).toDouble() / rx
                val dy = (y - cy).toDouble() / ry
                if (dx * dx + dy * dy <= 1.0) this[x, y] = t
            }
        }

        fun ring(cx: Int, cy: Int, rIn: Double, rOut: Double, t: TileType, onlyOn: Set<TileType>? = null) {
            val r = rOut.toInt() + 1
            for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
                val d = hypot((x - cx).toDouble(), (y - cy).toDouble())
                if (d in rIn..rOut && (onlyOn == null || this[x, y] in onlyOn)) this[x, y] = t
            }
        }

        /** Irregular, organic cavern centred on (cx, cy). */
        fun blob(cx: Int, cy: Int, r: Int, t: TileType) {
            val p1 = (hash(cx, cy, 7) % 628) / 100.0
            val p2 = (hash(cy, cx, 9) % 628) / 100.0
            val reach = (r * 1.4).toInt() + 1
            for (y in cy - reach..cy + reach) for (x in cx - reach..cx + reach) {
                val dx = (x - cx).toDouble()
                val dy = (y - cy).toDouble()
                val ang = atan2(dy, dx)
                val wobble = 1.0 + 0.22 * sin(ang * 3 + p1) + 0.12 * sin(ang * 5 + p2) + ((hash(x, y, 3) % 100) - 50) / 500.0
                if (hypot(dx, dy) <= r * wobble) this[x, y] = t
            }
        }

        private fun disc(cx: Int, cy: Int, radius: Double, t: TileType, onWater: TileType? = null, waterSet: Set<TileType> = emptySet()) {
            val r = radius.toInt() + 1
            for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
                if (hypot((x - cx).toDouble(), (y - cy).toDouble()) <= radius) {
                    this[x, y] = if (onWater != null && this[x, y] in waterSet) onWater else t
                }
            }
        }

        /** Winding tunnel through the given waypoints. */
        fun tunnel(points: List<Pair<Int, Int>>, width: Int, t: TileType, bridge: TileType = TileType.STONE_BRIDGE) {
            val radius = width / 2.0
            for (i in 0 until points.size - 1) {
                val (ax, ay) = points[i]
                val (bx, by) = points[i + 1]
                val len = hypot((bx - ax).toDouble(), (by - ay).toDouble())
                val steps = max(1, (len * 2).toInt())
                val nx = -(by - ay) / max(len, 1.0)
                val ny = (bx - ax) / max(len, 1.0)
                val phase = (hash(ax, ay, 11) % 628) / 100.0
                for (s in 0..steps) {
                    val f = s.toDouble() / steps
                    val taper = sin(f * Math.PI)                     // ends stay on the waypoints
                    val off = sin(f * len / 6.0 + phase) * 1.6 * taper
                    val px = (ax + (bx - ax) * f + nx * off).toInt()
                    val py = (ay + (by - ay) * f + ny * off).toInt()
                    disc(px, py, radius, t, onWater = bridge, waterSet = setOf(TileType.WATER, TileType.LAVA, TileType.DEEP_WATER))
                }
            }
        }

        /**
         * Continuous water/lava channel cut through rock and caverns alike. Callers re-trace the
         * tunnels that cross it afterwards so those crossings become bridges.
         */
        fun stream(points: List<Pair<Int, Int>>, width: Int, water: TileType) {
            val radius = width / 2.0
            val keep = setOf(TileType.STAIRS_UP, TileType.STAIRS_DOWN, TileType.ROPE_HOLE_DOWN, TileType.ROPE_SPOT_UP, TileType.IRON_BAR_GATE)
            for (i in 0 until points.size - 1) {
                val (ax, ay) = points[i]
                val (bx, by) = points[i + 1]
                val len = hypot((bx - ax).toDouble(), (by - ay).toDouble())
                val steps = max(1, (len * 2).toInt())
                for (s in 0..steps) {
                    val f = s.toDouble() / steps
                    val px = (ax + (bx - ax) * f).toInt()
                    val py = (ay + (by - ay) * f + sin(f * 3.0 + i) * 1.2).toInt()
                    val r = radius.toInt() + 1
                    for (y in py - r..py + r) for (x in px - r..px + r) {
                        if (hypot((x - px).toDouble(), (y - py).toDouble()) <= radius && this[x, y] !in keep) this[x, y] = water
                    }
                }
            }
        }

        /** Turns walkable tiles that cannot be reached from (sx, sy) back into solid rock. */
        fun sealPockets(sx: Int, sy: Int) {
            val seen = Array(size) { BooleanArray(size) }
            val q = ArrayDeque<Pair<Int, Int>>()
            seen[sy][sx] = true; q.addLast(sx to sy)
            while (q.isNotEmpty()) {
                val (x, y) = q.removeFirst()
                for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until size && ny in 0 until size && !seen[ny][nx] && g[ny][nx].walkable) {
                        seen[ny][nx] = true; q.addLast(nx to ny)
                    }
                }
            }
            for (y in 0 until size) for (x in 0 until size) if (g[y][x].walkable && !seen[y][x]) g[y][x] = base
        }

        /** Crumbles the outer [depth] tiles of a rectangle into [into] so islands look naturally eroded. */
        fun ragged(x1: Int, y1: Int, x2: Int, y2: Int, depth: Int, from: TileType, into: TileType, chance: Int) {
            for (y in y1..y2) for (x in x1..x2) {
                val edge = min(min(x - x1, x2 - x), min(y - y1, y2 - y))
                if (edge >= depth || this[x, y] != from) continue
                val odds = if (edge == 0) chance else chance * 2
                if (hash(x, y, 71) % odds == 0) this[x, y] = into
            }
        }

        /** Perfect maze (with extra loops) on a cell grid; cells and passages are `passage` wide. */
        fun maze(ox: Int, oy: Int, cols: Int, rows: Int, pitch: Int, passage: Int, extraLoops: Int, tile: TileType) {
            val visited = Array(rows) { BooleanArray(cols) }
            fun cellRect(i: Int, j: Int) = rect(ox + i * pitch, oy + j * pitch, ox + i * pitch + passage - 1, oy + j * pitch + passage - 1, tile)
            fun open(i: Int, j: Int, di: Int, dj: Int) {
                val x = ox + i * pitch; val y = oy + j * pitch
                if (di != 0) rect(x + if (di > 0) passage else -(pitch - passage), y, x + if (di > 0) pitch - 1 else -1, y + passage - 1, tile)
                if (dj != 0) rect(x, y + if (dj > 0) passage else -(pitch - passage), x + passage - 1, y + if (dj > 0) pitch - 1 else -1, tile)
            }
            val stack = ArrayDeque<Pair<Int, Int>>()
            stack.addLast(0 to 0); visited[0][0] = true; cellRect(0, 0)
            var step = 0
            while (stack.isNotEmpty()) {
                val (i, j) = stack.last()
                val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
                    .filter { (di, dj) -> i + di in 0 until cols && j + dj in 0 until rows && !visited[j + dj][i + di] }
                if (dirs.isEmpty()) { stack.removeLast(); continue }
                val (di, dj) = dirs[hash(i, j, step++) % dirs.size]
                open(i, j, di, dj)
                visited[j + dj][i + di] = true
                cellRect(i + di, j + dj)
                stack.addLast(i + di to j + dj)
            }
            var added = 0; var tries = 0
            while (added < extraLoops && tries < 500) {
                val i = hash(tries, 1, 21) % cols; val j = hash(tries, 2, 23) % rows
                val horizontal = hash(tries, 3, 25) % 2 == 0
                if (horizontal && i + 1 < cols) { open(i, j, 1, 0); added++ }
                if (!horizontal && j + 1 < rows) { open(i, j, 0, 1); added++ }
                tries++
            }
        }

        /** Carves 1-tile burial niches into solid walls that line corridors. */
        fun burialNiches(x1: Int, y1: Int, x2: Int, y2: Int, floor: TileType, density: Int) {
            val picks = mutableListOf<Pair<Int, Int>>()
            for (y in y1..y2) for (x in x1..x2) {
                if (this[x, y] !in wallTiles) continue
                val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
                val open = dirs.filter { (dx, dy) -> this[x + dx, y + dy] == floor }
                if (open.size != 1) continue
                val (dx, dy) = open[0]
                // the tile behind and both sides must be solid so the niche stays a dead-end recess
                if (this[x - dx, y - dy] !in wallTiles) continue
                if (this[x + dy, y + dx] !in wallTiles || this[x - dy, y - dx] !in wallTiles) continue
                if (hash(x, y, 31) % density == 0) picks += x to y
            }
            picks.forEach { (x, y) -> this[x, y] = floor }
        }

        fun scatter(x1: Int, y1: Int, x2: Int, y2: Int, t: TileType, density: Int, onlyOn: Set<TileType>, keepClear: Int) {
            for (y in y1..y2) for (x in x1..x2) {
                if (this[x, y] !in onlyOn || hash(x, y, 41) % density != 0) continue
                // only in open areas so props never seal a corridor
                var openCount = 0
                for (dy in -keepClear..keepClear) for (dx in -keepClear..keepClear) if (this[x + dx, y + dy].walkable) openCount++
                val area = (2 * keepClear + 1) * (2 * keepClear + 1)
                if (openCount == area) this[x, y] = t
            }
        }

        fun scatterReplace(x1: Int, y1: Int, x2: Int, y2: Int, from: TileType, to: TileType, density: Int) {
            for (y in y1..y2) for (x in x1..x2) if (this[x, y] == from && hash(x, y, 51) % density == 0) this[x, y] = to
        }

        /** Places [count] ore veins in wall alcoves that touch walkable ground. */
        fun ores(t: TileType, count: Int, x1: Int, y1: Int, x2: Int, y2: Int) {
            val candidates = mutableListOf<Pair<Int, Int>>()
            for (y in y1..y2) for (x in x1..x2) {
                if (this[x, y] !in wallTiles) continue
                val touches = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).count { (dx, dy) -> this[x + dx, y + dy].walkable && this[x + dx, y + dy] != TileType.IRON_BAR_GATE }
                var solid = 0
                for (dy in -1..1) for (dx in -1..1) if (this[x + dx, y + dy] in wallTiles) solid++
                if (touches == 1 && solid >= 5) candidates += x to y
            }
            candidates.sortBy { (x, y) -> hash(x, y, t.ordinal) }
            val chosen = mutableListOf<Pair<Int, Int>>()
            for (p in candidates) {
                if (chosen.size >= count) break
                if (chosen.all { abs(it.first - p.first) + abs(it.second - p.second) >= 10 }) chosen += p
            }
            chosen.forEach { (x, y) -> this[x, y] = t }
        }

        /** Wall torches on walls facing open ground, roughly [spacing] tiles apart. */
        fun torches(spacing: Int) {
            val placed = mutableListOf<Pair<Int, Int>>()
            for (y in 1 until size - 1) for (x in 1 until size - 1) {
                if (this[x, y] !in wallTiles) continue
                val facesFloor = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dy) -> this[x + dx, y + dy].walkable }
                if (!facesFloor || hash(x, y, 61) % 3 != 0) continue
                if (placed.any { abs(it.first - x) + abs(it.second - y) < spacing }) continue
                placed += x to y
                this[x, y] = TileType.TORCH_WALL
            }
        }

        /** Snaps each requested spawn to the nearest open, non-PZ, unclaimed tile. */
        fun spawns(floorId: Int, camp: UndergroundCampZone, vararg wanted: Pair<MonsterType, Pair<Int, Int>>): List<MonsterSpawn> {
            val taken = HashSet<Pair<Int, Int>>()
            return wanted.map { (type, at) ->
                val (sx, sy) = nearestOpen(at.first, at.second) { x, y ->
                    !camp.contains(x, y) && (x to y) !in taken && isPlainGround(this[x, y])
                } ?: error("No open tile for $type near $at on floor $floorId")
                taken += sx to sy
                MonsterSpawn(floorId, sx, sy, type)
            }
        }

        private fun isPlainGround(t: TileType): Boolean =
            t.walkable && t != TileType.STAIRS_UP && t != TileType.STAIRS_DOWN &&
                t != TileType.ROPE_HOLE_DOWN && t != TileType.ROPE_SPOT_UP && t != TileType.IRON_BAR_GATE

        private fun nearestOpen(x0: Int, y0: Int, ok: (Int, Int) -> Boolean): Pair<Int, Int>? {
            for (r in 0..12) for (dy in -r..r) for (dx in -r..r) {
                if (max(abs(dx), abs(dy)) != r) continue
                val x = x0 + dx; val y = y0 + dy
                if (x in 1 until size - 1 && y in 1 until size - 1 && ok(x, y)) return x to y
            }
            return null
        }
    }
}
