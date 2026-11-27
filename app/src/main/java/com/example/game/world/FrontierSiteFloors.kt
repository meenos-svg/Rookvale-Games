package com.example.game.world

import com.example.game.model.MonsterType
import com.example.game.model.TileType
import com.example.game.world.HeartlandSiteFloors.Anchors
import com.example.game.world.HeartlandSiteFloors.Layout

/**
 * Hand-shaped 96x96 dungeons for the Whispering Marsh and the Sunscorch Badlands:
 *
 * - **-7 Sunken Mire Vaults** (marsh, Lv 26-30): a drowned sluice works. Gate hall over a
 *   spillway, a great aqueduct with catwalks, flooded cisterns, leech nests, a drowned archive,
 *   the pump works and the Sluice Sump island where the stairs go down.
 * - **-8 Overgrown Ziggurat** (jungle, Lv 33-36): a stepped temple rising out of a jungle cave.
 *   Terraces whose gates turn round the pyramid, serpent pits, a cenote, priest cloisters.
 * - **-6 Gorek Labyrinth** (badlands): a sun-baked sandstone maze around an obsidian ring and
 *   the Pyre Court, a lava moat with an island at its heart.
 * - **-29 Murkfen Hollow** (marsh, Lv 20-24, new): a root-choked bog cave. Wisp glades, gator
 *   wallows, a hag village on stilts and Hagmother Vessa's bower on a moated island.
 * - **-30 Tomb of Akhet** (badlands, Lv 30-34, new): a buried pharaoh's tomb. Sand-fall galleries,
 *   the Hall of Kings, burial wings full of niches, a scorpion nest and the sealed sanctum where
 *   Akhet the Sand Pharaoh waits.
 * - **-33 Cinderfall Mines** (Ashen Caldera, Lv 55-60): an abandoned obsidian mine. Haulage
 *   tunnels, the Great Shaft over a lava pit, collapsed galleries, magma vents and the Foreman's
 *   Forge where Ordrak waits.
 * - **-34 Emberhold, the Buried City** (Ashen Caldera, Lv 61-66): a city swallowed by an eruption.
 *   Ash-choked streets and ruined houses, the hound kennels, the witches' coven and Queen Sevra's
 *   palace.
 */
object FrontierSiteFloors {
    const val SIZE = 96

    val MIRE = Anchors(48, 90, 48, 89, 48, 6, 48, 7)
    val ZIGGURAT = Anchors(5, 48, 6, 48, 52, 44, 52, 45)
    val GOREK = Anchors(48, 90, 48, 89, 48, 7, 48, 8)
    /** New dungeons have their boss on the same floor: the "descent" fields mirror the landing. */
    val MURKFEN = Anchors(5, 48, 6, 48, 6, 48, 6, 48)
    val TOMB = Anchors(48, 90, 48, 89, 48, 89, 48, 89)
    val RIFT = Anchors(8, 88, 9, 87, 9, 87, 9, 87)
    val BARROW = Anchors(48, 90, 48, 89, 48, 6, 48, 7)
    val RIMEHEART = Anchors(90, 48, 89, 48, 89, 48, 89, 48)
    val CINDERFALL = Anchors(48, 90, 48, 89, 48, 89, 48, 89)
    val EMBERHOLD = Anchors(5, 48, 6, 48, 6, 48, 6, 48)

    const val MURKFEN_FLOOR = -29
    const val TOMB_FLOOR = -30
    const val RIFT_FLOOR = -31
    const val RIMEHEART_FLOOR = -32
    const val CINDERFALL_FLOOR = -33
    const val EMBERHOLD_FLOOR = -34

    /** Overland entrances of the two new dungeons. */
    const val MURKFEN_ENTRY_X = 452
    const val MURKFEN_ENTRY_Y = 612
    const val TOMB_ENTRY_X = 300
    const val TOMB_ENTRY_Y = 850
    const val RIFT_ENTRY_X = 190
    const val RIFT_ENTRY_Y = 880
    const val RIMEHEART_ENTRY_X = 1410
    const val RIMEHEART_ENTRY_Y = 90
    const val CINDERFALL_ENTRY_X = 382
    const val CINDERFALL_ENTRY_Y = 150
    const val EMBERHOLD_ENTRY_X = 289
    const val EMBERHOLD_ENTRY_Y = 330

    val newFloorIds: List<Int> = listOf(MURKFEN_FLOOR, TOMB_FLOOR, RIFT_FLOOR, RIMEHEART_FLOOR, CINDERFALL_FLOOR, EMBERHOLD_FLOOR)

    fun anchorsFor(floorId: Int): Anchors? = when (floorId) {
        -6 -> GOREK
        -7 -> MIRE
        -8 -> ZIGGURAT
        MURKFEN_FLOOR -> MURKFEN
        TOMB_FLOOR -> TOMB
        RIFT_FLOOR -> RIFT
        -9 -> BARROW
        RIMEHEART_FLOOR -> RIMEHEART
        CINDERFALL_FLOOR -> CINDERFALL
        EMBERHOLD_FLOOR -> EMBERHOLD
        else -> null
    }

    fun layoutFor(floorId: Int): Layout? = when (floorId) {
        -6 -> gorekLabyrinth
        -7 -> mireVaults
        -8 -> ziggurat
        MURKFEN_FLOOR -> murkfenHollow
        TOMB_FLOOR -> tombOfAkhet
        RIFT_FLOOR -> scorchglassRift
        -9 -> barrowCatacombs
        RIMEHEART_FLOOR -> rimeheartCaverns
        CINDERFALL_FLOOR -> cinderfallMines
        EMBERHOLD_FLOOR -> emberhold
        else -> null
    }

    val mireVaults: Layout by lazy { buildMireVaults() }
    val ziggurat: Layout by lazy { buildZiggurat() }
    val gorekLabyrinth: Layout by lazy { buildGorek() }
    val murkfenHollow: Layout by lazy { buildMurkfen() }
    val tombOfAkhet: Layout by lazy { buildTomb() }
    val scorchglassRift: Layout by lazy { buildRift() }
    val barrowCatacombs: Layout by lazy { buildBarrow() }
    val rimeheartCaverns: Layout by lazy { buildRimeheart() }
    val cinderfallMines: Layout by lazy { buildCinderfall() }
    val emberhold: Layout by lazy { buildEmberhold() }

    private fun layout(
        floorId: Int,
        c: DeepDelveFloors.Canvas,
        camp: UndergroundCampZone,
        a: Anchors,
        spawns: List<MonsterSpawn>,
        landmarks: List<LandmarkLabel>
    ) = Layout(
        floorId, c.g, camp, spawns, landmarks,
        a.stairsUpX, a.stairsUpY, a.landingX, a.landingY,
        a.descentX, a.descentY, a.descentReturnX, a.descentReturnY
    )

    /** Standard south-centre forward camp used by -7, -6 and -30. */
    private fun southCamp(c: DeepDelveFloors.Canvas, a: Anchors, floor: TileType, stall: TileType, camp: UndergroundCampZone) {
        c.rect(camp.minX, camp.minY, camp.maxX, camp.maxY, floor)
        c.rect(46, 86, 50, 88, TileType.COBBLESTONE)
        c[42, 85] = TileType.CAMPFIRE_BRAZIER; c[54, 85] = TileType.CAMPFIRE_BRAZIER
        c[42, 91] = TileType.WAR_BANNER; c[54, 91] = TileType.WAR_BANNER
        c[48, 85] = stall
        c[a.stairsUpX, a.stairsUpY] = TileType.STAIRS_UP
        c[a.landingX, a.landingY] = TileType.COBBLESTONE
    }

    // =====================================================================================
    // Floor -7: The Sunken Mire Vaults (Lv 26-30)
    // =====================================================================================
    private fun buildMireVaults(): Layout {
        val a = MIRE
        val c = DeepDelveFloors.Canvas(SIZE, TileType.CAVE_WALL, seed = 701)
        val dirt = TileType.DIRT_FLOOR
        val vault = TileType.CRYPT_FLOOR
        val water = TileType.WATER
        val cat = TileType.WOOD_BRIDGE
        val marsh = TileType.MARSH
        val camp = UndergroundCampZone(
            floorId = -7, name = "MIRE VAULTS FORWARD CAMP (PZ)",
            minX = 41, minY = 84, maxX = 55, maxY = 91,
            healerX = 43, healerY = 87, healerName = "Mire Priestess Lyra",
            merchantX = 53, merchantY = 87, merchantName = "Bog Supplier Torin"
        )
        southCamp(c, a, TileType.WOOD_PLANK_FLOOR, TileType.BIOME_STALL_MARSH, camp)

        // Sluice Gate Hall above the camp, cut by a spillway with three bridges
        c.rect(46, 80, 50, 83, vault)
        for (x in 46..50) c[x, 82] = TileType.IRON_BAR_GATE
        c.rect(34, 64, 62, 79, vault)
        c.rect(34, 71, 62, 73, water)
        for (bx in listOf(39, 47, 55)) c.rect(bx, 71, bx + 2, 73, cat)
        for (px in 38..58 step 5) { c[px, 67] = TileType.CAVE_WALL; c[px, 77] = TileType.CAVE_WALL }

        // The Great Aqueduct: a canal across the whole vault with catwalks on both banks
        c.rect(46, 54, 50, 63, vault)
        c.rect(5, 43, 90, 45, cat)
        c.rect(5, 46, 90, 50, water)
        c.rect(5, 51, 90, 53, cat)
        for (bx in listOf(20, 48, 76)) c.rect(bx, 46, bx + 2, 50, cat)

        // Flooded Cisterns (SW): a grid of square pools with walkways between
        c.rect(6, 56, 32, 82, vault)
        for (i in 0..2) for (j in 0..2) c.rect(8 + i * 8, 58 + j * 8, 12 + i * 8, 62 + j * 8, water)
        c.rect(32, 75, 34, 77, vault)
        c.rect(14, 53, 16, 56, vault)

        // Leech Nests (SE): organic marsh caverns pocked with pools
        c.blob(73, 66, 7, marsh)
        c.blob(84, 78, 6, marsh)
        c.blob(68, 84, 5, marsh)
        c.scatterReplace(60, 58, 92, 92, marsh, water, 7)
        c.tunnel(listOf(62 to 74, 68 to 70, 73 to 66), 3, marsh, bridge = cat)
        c.tunnel(listOf(77 to 53, 75 to 60), 3, marsh, bridge = cat)
        c.tunnel(listOf(73 to 66, 84 to 78), 3, marsh, bridge = cat)
        c.tunnel(listOf(84 to 78, 68 to 84), 2, marsh, bridge = cat)

        // Drowned Archive (NW): four flooded reading rooms lined with shelves
        c.rect(8, 8, 36, 36, vault)
        c.outline(8, 8, 22, 22, TileType.CAVE_WALL); c.outline(22, 8, 36, 22, TileType.CAVE_WALL)
        c.outline(8, 22, 22, 36, TileType.CAVE_WALL); c.outline(22, 22, 36, 36, TileType.CAVE_WALL)
        for ((x, y) in listOf(15 to 22, 29 to 22, 22 to 15, 22 to 29, 15 to 36, 36 to 15)) { c[x, y] = vault; c[x + 1, y] = vault; c[x, y + 1] = vault }
        for (x in listOf(10, 11, 12, 18, 19, 20, 24, 25, 26, 32, 33, 34)) { c[x, 9] = TileType.BOOKSHELF; c[x, 23] = TileType.BOOKSHELF }
        c.scatterReplace(9, 9, 35, 35, vault, water, 11)
        c.rect(15, 37, 17, 42, vault)

        // Pump Works (NE): boiler rooms, crates and a feed channel
        c.rect(60, 10, 90, 36, dirt)
        c.outline(60, 10, 90, 36, TileType.CAVE_WALL)
        c.outline(60, 10, 75, 23, TileType.CAVE_WALL); c.outline(75, 10, 90, 23, TileType.CAVE_WALL)
        for ((x, y) in listOf(67 to 23, 82 to 23, 75 to 16, 75 to 30, 68 to 36, 60 to 30)) { c[x, y] = dirt; c[x + 1, y] = dirt; c[x, y + 1] = dirt }
        for ((x, y) in listOf(63 to 12, 72 to 12, 78 to 12, 87 to 12)) c[x, y] = TileType.SMELTING_FURNACE
        c.scatter(61, 24, 89, 35, TileType.CRATE_BARREL, density = 13, onlyOn = setOf(dirt), keepClear = 1)
        c.rect(68, 37, 70, 42, dirt)

        // Sluice Sump: a moated island at the top of the vault where the stairs go down
        c.rect(47, 27, 49, 42, vault)
        c.ellipse(48, 16, 10, 8, water)
        c.ellipse(48, 16, 5, 4, vault)
        c.rect(47, 21, 49, 26, cat)
        c.rect(47, 6, 49, 12, vault)
        c.tunnel(listOf(36 to 18, 43 to 16), 3, vault, bridge = cat)
        c.tunnel(listOf(60 to 18, 53 to 16), 3, dirt, bridge = cat)
        c[a.descentX, a.descentY] = TileType.STAIRS_DOWN
        c[a.descentReturnX, a.descentReturnY] = vault

        c.sealPockets(a.landingX, a.landingY)
        c.ores(TileType.ORE_MIRE_SILVER, count = 8, x1 = 4, y1 = 4, x2 = 92, y2 = 82)
        c.ores(TileType.ORE_PEAT_COAL, count = 6, x1 = 4, y1 = 4, x2 = 92, y2 = 82)
        c.torches(spacing = 8)

        val lurker = MonsterType.MIRE_LURKER
        val weaver = MonsterType.BOG_LEECH_WEAVER
        val troll = MonsterType.SWAMP_TROLL
        val spawns = c.spawns(
            -7, camp,
            lurker to (40 to 66), weaver to (56 to 77), troll to (48 to 68),
            lurker to (12 to 44), weaver to (34 to 52), lurker to (62 to 44), troll to (86 to 52),
            weaver to (14 to 64), lurker to (22 to 72), troll to (30 to 80), weaver to (14 to 80),
            lurker to (72 to 64), troll to (84 to 78), weaver to (68 to 84), lurker to (78 to 70),
            weaver to (14 to 14), lurker to (30 to 30), troll to (14 to 30),
            troll to (66 to 28), lurker to (82 to 16), weaver to (66 to 15),
            lurker to (48 to 30), weaver to (48 to 16), troll to (44 to 16)
        )
        val landmarks = listOf(
            LandmarkLabel(-7, 48, 66, "SLUICE GATE HALL"),
            LandmarkLabel(-7, 30, 42, "THE GREAT AQUEDUCT"),
            LandmarkLabel(-7, 18, 58, "FLOODED CISTERNS"),
            LandmarkLabel(-7, 76, 62, "LEECH NESTS"),
            LandmarkLabel(-7, 22, 10, "DROWNED ARCHIVE"),
            LandmarkLabel(-7, 75, 26, "PUMP WORKS"),
            LandmarkLabel(-7, 48, 13, "SLUICE SUMP")
        )
        return layout(-7, c, camp, a, spawns, landmarks)
    }

    // =====================================================================================
    // Floor -8: The Overgrown Ziggurat Vault (Lv 33-36)
    // =====================================================================================
    private fun buildZiggurat(): Layout {
        val a = ZIGGURAT
        val c = DeepDelveFloors.Canvas(SIZE, TileType.STONE_WALL, seed = 801)
        val crypt = TileType.CRYPT_FLOOR
        val jungle = TileType.JUNGLE_FLOOR
        val grass = TileType.DARK_GRASS
        val water = TileType.WATER
        val temple = TileType.TEMPLE_FLOOR
        val camp = UndergroundCampZone(
            floorId = -8, name = "ZIGGURAT VAULT CAMP (PZ)",
            minX = 3, minY = 42, maxX = 13, maxY = 54,
            healerX = 5, healerY = 44, healerName = "Sunleaf Cleric Sera",
            merchantX = 5, merchantY = 52, merchantName = "Expeditionist Kael"
        )
        // Expedition camp (west)
        c.rect(3, 42, 13, 54, crypt)
        c.rect(7, 46, 11, 50, TileType.COBBLESTONE)
        c[12, 43] = TileType.CAMPFIRE_BRAZIER; c[12, 53] = TileType.CAMPFIRE_BRAZIER
        c[3, 43] = TileType.WAR_BANNER; c[3, 53] = TileType.WAR_BANNER
        c[8, 43] = TileType.BIOME_STALL_MARSH
        c[a.stairsUpX, a.stairsUpY] = TileType.STAIRS_UP
        c[a.landingX, a.landingY] = TileType.COBBLESTONE

        // Jungle approach cavern
        c.rect(14, 46, 16, 50, jungle)
        c.blob(22, 48, 8, jungle)

        // Temple court and the stepped ziggurat (terrace gates turn W -> N -> E)
        c.rect(30, 24, 74, 72, crypt)
        c.scatterReplace(30, 24, 74, 72, crypt, jungle, 3)
        c.rect(24, 47, 30, 49, jungle)
        c.outline(34, 28, 70, 68, TileType.STONE_WALL)
        c.rect(34, 47, 34, 49, temple)
        c.rect(35, 29, 69, 67, crypt)
        c.outline(39, 33, 65, 63, TileType.STONE_WALL)
        c.rect(51, 33, 53, 33, temple)
        c.rect(40, 34, 64, 62, crypt)
        c.outline(44, 38, 60, 58, TileType.STONE_WALL)
        c.rect(60, 47, 60, 49, temple)
        c.rect(45, 39, 59, 57, temple)
        c.scatterReplace(35, 29, 69, 67, crypt, grass, 5)
        for ((x, y) in listOf(46 to 40, 58 to 40, 46 to 56, 58 to 56)) c[x, y] = TileType.CRYSTAL_PILLAR
        c[52, 51] = TileType.TEMPLE_ALTAR
        c[a.descentX, a.descentY] = TileType.STAIRS_DOWN
        c[a.descentReturnX, a.descentReturnY] = temple
        c.scatter(30, 24, 74, 72, TileType.TREE, density = 29, onlyOn = setOf(jungle), keepClear = 1)

        // Serpent Pits (north): three jungle hollows around sunken pools
        for ((px, py, r) in listOf(Triple(28, 13, 6), Triple(52, 11, 7), Triple(77, 13, 6))) {
            c.blob(px, py, r, jungle)
            c.ellipse(px, py, 2, 2, water)
        }
        c.tunnel(listOf(52 to 18, 52 to 24), 3, jungle)
        c.tunnel(listOf(30 to 19, 36 to 24), 3, jungle)
        c.tunnel(listOf(75 to 19, 68 to 24), 3, jungle)
        c.tunnel(listOf(34 to 12, 45 to 11), 2, jungle)
        c.tunnel(listOf(59 to 11, 71 to 13), 2, jungle)

        // The Cenote (south): a jungle-ringed sinkhole lake with a shrine islet
        c.ellipse(52, 83, 18, 10, jungle)
        c.ellipse(52, 83, 13, 7, water)
        c.ellipse(52, 83, 3, 2, grass)
        c.rect(51, 72, 53, 80, TileType.STONE_BRIDGE)
        c[52, 83] = TileType.CRYSTAL_PILLAR

        // Priest Cloisters (east): a row of cells off a long gallery
        c.rect(78, 28, 92, 68, crypt)
        for (y in 30..62 step 8) {
            c.outline(79, y, 86, y + 5, TileType.STONE_WALL)
            c[86, y + 2] = crypt; c[86, y + 3] = crypt
            c[81, y + 2] = TileType.TEMPLE_PEW
        }
        c.rect(74, 47, 78, 49, crypt)

        // Root caverns linking the approach with the pits and the cenote
        c.blob(17, 24, 7, jungle)
        c.blob(19, 74, 7, jungle)
        c.tunnel(listOf(22 to 41, 17 to 30), 3, jungle)
        c.tunnel(listOf(17 to 18, 23 to 13), 2, jungle)
        c.tunnel(listOf(22 to 55, 19 to 67), 3, jungle)
        c.tunnel(listOf(26 to 76, 35 to 82), 3, jungle, bridge = TileType.STONE_BRIDGE)
        c.scatter(10, 4, 92, 92, TileType.TREE, density = 31, onlyOn = setOf(jungle), keepClear = 1)

        c.sealPockets(a.landingX, a.landingY)
        c.ores(TileType.ORE_MIRE_SILVER, count = 6, x1 = 4, y1 = 4, x2 = 92, y2 = 92)
        c.ores(TileType.ORE_PEAT_COAL, count = 6, x1 = 4, y1 = 4, x2 = 92, y2 = 92)
        c.torches(spacing = 8)

        val serpent = MonsterType.TEMPLE_SERPENT_GUARD
        val priest = MonsterType.SUN_PRIEST_ACOLYTE
        val spider = MonsterType.POISON_SPIDER
        val spawns = c.spawns(
            -8, camp,
            spider to (22 to 46), spider to (17 to 24), serpent to (19 to 74),
            serpent to (32 to 30), priest to (72 to 30), serpent to (32 to 66), priest to (72 to 66),
            serpent to (36 to 48), spider to (52 to 30), priest to (68 to 48), serpent to (52 to 66),
            priest to (42 to 36), serpent to (62 to 60), serpent to (48 to 48), priest to (56 to 54),
            spider to (28 to 13), serpent to (52 to 9), spider to (77 to 13),
            spider to (42 to 82), serpent to (62 to 84),
            priest to (88 to 34), priest to (88 to 50), serpent to (88 to 64)
        )
        val landmarks = listOf(
            LandmarkLabel(-8, 22, 44, "JUNGLE APPROACH"),
            LandmarkLabel(-8, 52, 26, "TEMPLE COURT"),
            LandmarkLabel(-8, 52, 41, "ZIGGURAT SUMMIT"),
            LandmarkLabel(-8, 52, 6, "SERPENT PITS"),
            LandmarkLabel(-8, 52, 75, "THE CENOTE"),
            LandmarkLabel(-8, 85, 27, "PRIEST CLOISTERS")
        )
        return layout(-8, c, camp, a, spawns, landmarks)
    }

    // =====================================================================================
    // Floor -6: The Gorek Labyrinth (sun-baked sandstone maze)
    // =====================================================================================
    private fun buildGorek(): Layout {
        val a = GOREK
        val wall = TileType.SANDSTONE_WALL
        val c = DeepDelveFloors.Canvas(SIZE, wall, seed = 601)
        val sand = TileType.SAND
        val obs = TileType.OBSIDIAN_FLOOR
        val cobble = TileType.COBBLESTONE
        val camp = UndergroundCampZone(
            floorId = -6, name = "GOREK LABYRINTH VANGUARD CAMP (PZ)",
            minX = 41, minY = 84, maxX = 55, maxY = 91,
            healerX = 43, healerY = 87, healerName = "High Cleric Vesper",
            merchantX = 53, merchantY = 87, merchantName = "Quartermaster Ryn"
        )

        // Outer sand maze filling the whole vault
        c.maze(6, 6, cols = 22, rows = 19, pitch = 4, passage = 2, extraLoops = 34, tile = sand)
        // war-band barracks squares carved into the maze corners
        for ((x1, y1) in listOf(7 to 7, 74 to 7, 7 to 66, 74 to 66)) {
            c.rect(x1, y1, x1 + 13, y1 + 9, cobble)
            c[x1 + 1, y1 + 1] = TileType.WAR_BANNER; c[x1 + 12, y1 + 8] = TileType.CAMPFIRE_BRAZIER
        }

        southCamp(c, a, cobble, TileType.BIOME_STALL_BADLANDS, camp)
        c.rect(46, 80, 50, 83, sand)
        for (x in 46..50) c[x, 82] = TileType.IRON_BAR_GATE

        // Obsidian ring around the heart
        c.rect(24, 20, 72, 64, wall)
        c.rect(24, 20, 72, 22, obs); c.rect(24, 62, 72, 64, obs)
        c.rect(24, 20, 26, 64, obs); c.rect(70, 20, 72, 64, obs)
        c.rect(46, 18, 47, 20, sand); c.rect(46, 64, 47, 67, sand)
        c.rect(21, 42, 24, 43, sand); c.rect(72, 42, 75, 43, sand)

        // The Pyre Court: lava moat with an island and four bridges
        c.rect(30, 26, 66, 58, obs)
        c.rect(34, 30, 62, 54, TileType.LAVA)
        c.rect(38, 34, 58, 50, obs)
        c.rect(47, 30, 49, 34, TileType.STONE_BRIDGE); c.rect(47, 50, 49, 54, TileType.STONE_BRIDGE)
        c.rect(34, 41, 38, 43, TileType.STONE_BRIDGE); c.rect(58, 41, 62, 43, TileType.STONE_BRIDGE)
        c[48, 42] = TileType.CAMPFIRE_BRAZIER
        for ((x, y) in listOf(41 to 37, 55 to 37, 41 to 47, 55 to 47)) c[x, y] = TileType.WAR_BANNER
        for ((x1, y1) in listOf(47 to 23, 47 to 59, 27 to 41, 67 to 41)) {
            val horizontal = y1 == 41
            if (horizontal) c.rect(x1, 41, x1 + 2, 43, obs) else c.rect(47, y1, 49, y1 + 2, obs)
        }
        for (x in 47..49) { c[x, 24] = TileType.IRON_BAR_GATE; c[x, 60] = TileType.IRON_BAR_GATE }

        // Walled descent passage north of the ring
        c.rect(45, 6, 51, 19, wall)
        c.rect(47, 7, 49, 19, obs)
        c[a.descentX, a.descentY] = TileType.STAIRS_DOWN
        c[a.descentReturnX, a.descentReturnY] = obs

        c.sealPockets(a.landingX, a.landingY)
        c.ores(TileType.ORE_VESPERITE, count = 9, x1 = 4, y1 = 4, x2 = 92, y2 = 82)
        c.ores(TileType.ORE_BOG_IRON, count = 5, x1 = 4, y1 = 4, x2 = 92, y2 = 82)
        c.torches(spacing = 9)

        val raider = MonsterType.GOREK_RAIDER
        val phalanx = MonsterType.GOREK_PHALANX
        val hex = MonsterType.GOREK_HEX_CALLER
        val spawns = c.spawns(
            -6, camp,
            raider to (12 to 11), phalanx to (80 to 11), raider to (12 to 70), phalanx to (80 to 70),
            raider to (30 to 76), raider to (66 to 76), hex to (18 to 50), hex to (78 to 50),
            phalanx to (34 to 10), raider to (62 to 10), hex to (10 to 30), raider to (86 to 30),
            raider to (25 to 30), phalanx to (71 to 30), raider to (25 to 56), phalanx to (71 to 56),
            hex to (36 to 21), hex to (60 to 63),
            phalanx to (32 to 28), raider to (64 to 28), hex to (32 to 56), raider to (64 to 56),
            phalanx to (44 to 38), hex to (52 to 46), phalanx to (48 to 14)
        )
        val landmarks = listOf(
            LandmarkLabel(-6, 48, 78, "SUNBAKED MAZE"),
            LandmarkLabel(-6, 48, 21, "OBSIDIAN RING"),
            LandmarkLabel(-6, 48, 36, "THE PYRE COURT"),
            LandmarkLabel(-6, 14, 7, "RAIDER BARRACKS"),
            LandmarkLabel(-6, 80, 7, "PHALANX BARRACKS")
        )
        return layout(-6, c, camp, a, spawns, landmarks)
    }

    // =====================================================================================
    // Floor -29: Murkfen Hollow (Lv 20-24)
    // =====================================================================================
    private fun buildMurkfen(): Layout {
        val a = MURKFEN
        val c = DeepDelveFloors.Canvas(SIZE, TileType.CAVE_WALL, seed = 2901)
        val marsh = TileType.MARSH
        val grass = TileType.DARK_GRASS
        val water = TileType.WATER
        val plank = TileType.WOOD_PLANK_FLOOR
        val bridge = TileType.WOOD_BRIDGE
        val camp = UndergroundCampZone(
            floorId = MURKFEN_FLOOR, name = "MURKFEN HOLLOW CAMP (PZ)",
            minX = 3, minY = 43, maxX = 12, maxY = 53,
            healerX = 5, healerY = 45, healerName = "Fen Warden Ilsa",
            merchantX = 5, merchantY = 51, merchantName = "Reed-Cutter Bram"
        )
        // Reed-cutters' camp (west)
        c.rect(3, 43, 12, 53, plank)
        c[11, 44] = TileType.CAMPFIRE_BRAZIER; c[11, 52] = TileType.CAMPFIRE_BRAZIER
        c[9, 44] = TileType.BIOME_STALL_MARSH; c[9, 52] = TileType.CRATE_BARREL
        c[a.stairsUpX, a.stairsUpY] = TileType.STAIRS_UP
        c[a.landingX, a.landingY] = TileType.COBBLESTONE

        // The Drowned Mere: a great bog cavern
        c.blob(38, 48, 15, marsh)
        for ((px, py, rx, ry) in listOf(listOf(32, 42, 4, 3), listOf(44, 54, 5, 3), listOf(46, 40, 3, 2), listOf(30, 56, 3, 2))) c.ellipse(px, py, rx, ry, water)
        c.scatterReplace(22, 32, 54, 64, marsh, grass, 6)
        c.tunnel(listOf(12 to 48, 24 to 48), 3, marsh, bridge = bridge)

        // Wisp Glades (north): mossy hollows full of lights
        c.blob(34, 16, 9, grass)
        c.blob(58, 13, 7, grass)
        c.scatterReplace(24, 6, 66, 24, grass, TileType.FLOWER_GRASS, 5)
        c.tunnel(listOf(36 to 34, 34 to 24), 3, marsh, bridge = bridge)
        c.tunnel(listOf(42 to 16, 52 to 13), 3, grass, bridge = bridge)

        // Gator Wallows (south): deep pools and plank islands
        c.blob(34, 79, 11, marsh)
        c.ellipse(30, 78, 6, 4, water); c.ellipse(42, 84, 5, 3, water)
        c.rect(28, 77, 32, 79, plank)
        c.tunnel(listOf(38 to 62, 35 to 70), 3, marsh, bridge = bridge)
        c.blob(14, 76, 5, marsh)
        c.tunnel(listOf(24 to 78, 17 to 76), 2, marsh, bridge = bridge)

        // Hag Village (east): stilt huts on a boardwalk over the bog
        c.rect(58, 36, 82, 62, marsh)
        c.ellipse(70, 49, 10, 9, water)
        c.rect(58, 48, 82, 50, plank)              // boardwalk east-west
        c.rect(69, 36, 71, 62, plank)              // boardwalk north-south
        for ((hx, hy) in listOf(60 to 38, 75 to 38, 60 to 55, 75 to 55)) {
            c.rect(hx, hy, hx + 5, hy + 4, plank)
            c.outline(hx, hy, hx + 5, hy + 4, TileType.TIMBER_WALL)
            c[hx + 2, if (hy < 49) hy + 4 else hy] = plank
            c[hx + 1, hy + (if (hy < 49) 1 else 3)] = TileType.CAMPFIRE_BRAZIER
        }
        c.rect(53, 47, 58, 50, marsh)
        c.tunnel(listOf(52 to 48, 58 to 49), 3, marsh, bridge = bridge)
        c.tunnel(listOf(62 to 16, 66 to 30, 70 to 36), 3, grass, bridge = bridge)
        c.tunnel(listOf(46 to 70, 60 to 64), 3, marsh, bridge = bridge)

        // Hagmother's Bower (far east): a moated island reached by one bridge
        c.rect(83, 47, 86, 50, marsh)
        c.ellipse(84, 26, 9, 9, water)
        c.ellipse(84, 26, 6, 6, plank)
        c.rect(83, 31, 85, 46, bridge)
        for (y in 44..46) c[84, y] = TileType.IRON_BAR_GATE
        c[84, 23] = TileType.CAMPFIRE_BRAZIER                    // the cauldron
        for ((x, y) in listOf(80 to 22, 88 to 22, 80 to 30, 88 to 30)) c[x, y] = TileType.CRYSTAL_PILLAR

        c.sealPockets(a.landingX, a.landingY)
        c.ores(TileType.ORE_MIRE_SILVER, count = 6, x1 = 4, y1 = 4, x2 = 92, y2 = 92)
        c.ores(TileType.ORE_PEAT_COAL, count = 7, x1 = 4, y1 = 4, x2 = 92, y2 = 92)
        c.ores(TileType.ORE_BOG_IRON, count = 4, x1 = 4, y1 = 4, x2 = 92, y2 = 92)
        c.torches(spacing = 9)

        val hag = MonsterType.BOG_HAG
        val gator = MonsterType.GATOR_BRUTE
        val wisp = MonsterType.FEN_WISP
        val spawns = c.spawns(
            MURKFEN_FLOOR, camp,
            gator to (26 to 48), wisp to (36 to 38), gator to (40 to 58), hag to (48 to 46), wisp to (28 to 60),
            wisp to (30 to 14), wisp to (38 to 20), wisp to (58 to 12), hag to (54 to 16),
            gator to (26 to 80), gator to (40 to 76), gator to (34 to 86), wisp to (14 to 76),
            hag to (62 to 42), hag to (78 to 42), hag to (62 to 58), hag to (78 to 58), gator to (70 to 40), wisp to (66 to 49),
            wisp to (64 to 26), gator to (56 to 66),
            MonsterType.HAGMOTHER_VESSA to (84 to 27)
        )
        val landmarks = listOf(
            LandmarkLabel(MURKFEN_FLOOR, 38, 36, "THE DROWNED MERE"),
            LandmarkLabel(MURKFEN_FLOOR, 44, 8, "WISP GLADES"),
            LandmarkLabel(MURKFEN_FLOOR, 34, 70, "GATOR WALLOWS"),
            LandmarkLabel(MURKFEN_FLOOR, 70, 34, "HAG VILLAGE"),
            LandmarkLabel(MURKFEN_FLOOR, 84, 17, "HAGMOTHER'S BOWER")
        )
        return layout(MURKFEN_FLOOR, c, camp, a, spawns, landmarks)
    }

    // =====================================================================================
    // Floor -30: Tomb of Akhet (Lv 30-34)
    // =====================================================================================
    private fun buildTomb(): Layout {
        val a = TOMB
        val wall = TileType.SANDSTONE_WALL
        val c = DeepDelveFloors.Canvas(SIZE, wall, seed = 3001)
        val sand = TileType.SAND
        val tomb = TileType.TEMPLE_FLOOR
        val crypt = TileType.CRYPT_FLOOR
        val camp = UndergroundCampZone(
            floorId = TOMB_FLOOR, name = "TOMB OF AKHET DIG CAMP (PZ)",
            minX = 41, minY = 84, maxX = 55, maxY = 91,
            healerX = 43, healerY = 87, healerName = "Sun Cleric Amunet",
            merchantX = 53, merchantY = 87, merchantName = "Dig Master Hesper"
        )
        southCamp(c, a, sand, TileType.BIOME_STALL_BADLANDS, camp)

        // Sand-choked entry ramp
        c.rect(46, 72, 50, 83, sand)
        for (x in 46..50) c[x, 77] = TileType.IRON_BAR_GATE

        // Hall of Kings: colonnaded processional hall
        c.rect(26, 50, 70, 71, tomb)
        for (px in 30..66 step 6) { c.rect(px, 54, px + 1, 55, wall); c.rect(px, 66, px + 1, 67, wall) }
        c.scatterReplace(27, 51, 69, 70, tomb, sand, 9)

        // Sand-fall galleries (west): a crumbling maze
        c.maze(6, 40, cols = 5, rows = 10, pitch = 4, passage = 2, extraLoops = 8, tile = sand)
        c.rect(24, 58, 26, 59, sand)

        // Scorpion nest (east): sand dens
        c.blob(81, 60, 8, sand)
        c.blob(84, 80, 6, sand)
        c.tunnel(listOf(70 to 60, 74 to 60), 3, sand)
        c.tunnel(listOf(81 to 66, 84 to 76), 3, sand)
        c.tunnel(listOf(78 to 82, 55 to 88), 2, sand)

        // Burial wings (north): a long gallery with side passages lined with niches
        c.rect(8, 8, 88, 10, crypt)
        for (x in listOf(10, 22, 34, 62, 74, 86)) c.rect(x, 11, x + 1, 30, crypt)
        c.burialNiches(6, 6, 92, 34, crypt, density = 3)
        c.rect(10, 31, 11, 41, crypt)                         // wings <-> galleries
        c.rect(86, 31, 87, 54, crypt)                         // wings <-> scorpion nest
        c.rect(86, 54, 88, 56, sand)

        // Pharaoh's sanctum: sealed behind gates at the top of the processional way
        c.rect(47, 31, 49, 49, tomb)
        for (x in 47..49) { c[x, 40] = TileType.IRON_BAR_GATE; c[x, 32] = TileType.IRON_BAR_GATE }
        c.rect(38, 13, 58, 30, tomb)
        c.outline(37, 12, 59, 31, wall)
        for (x in 47..49) c[x, 31] = tomb
        for ((x, y) in listOf(40 to 15, 56 to 15, 40 to 27, 56 to 27)) c[x, y] = TileType.CRYSTAL_PILLAR
        c.rect(44, 14, 52, 15, crypt)                         // raised dais
        c[48, 13] = TileType.TEMPLE_ALTAR                     // the sarcophagus

        c.sealPockets(a.landingX, a.landingY)
        c.ores(TileType.ORE_VESPERITE, count = 7, x1 = 4, y1 = 4, x2 = 92, y2 = 92)
        c.ores(TileType.ORE_BOG_IRON, count = 5, x1 = 4, y1 = 4, x2 = 92, y2 = 92)
        c.torches(spacing = 8)

        val scorpion = MonsterType.DUNE_SCORPION
        val wraith = MonsterType.SAND_WRAITH
        val basilisk = MonsterType.SUNSCALE_BASILISK
        val spawns = c.spawns(
            TOMB_FLOOR, camp,
            scorpion to (48 to 74),
            basilisk to (32 to 60), wraith to (44 to 52), basilisk to (60 to 60), wraith to (52 to 69), scorpion to (66 to 52),
            wraith to (8 to 44), scorpion to (16 to 56), wraith to (8 to 70), scorpion to (20 to 78),
            scorpion to (80 to 58), scorpion to (86 to 64), basilisk to (84 to 80),
            wraith to (14 to 9), wraith to (30 to 9), wraith to (66 to 9), wraith to (82 to 9),
            basilisk to (22 to 24), basilisk to (74 to 24),
            basilisk to (48 to 36), wraith to (42 to 26), wraith to (54 to 26),
            MonsterType.AKHET_THE_SAND_PHARAOH to (48 to 18)
        )
        val landmarks = listOf(
            LandmarkLabel(TOMB_FLOOR, 48, 50, "HALL OF KINGS"),
            LandmarkLabel(TOMB_FLOOR, 15, 38, "SAND-FALL GALLERIES"),
            LandmarkLabel(TOMB_FLOOR, 82, 52, "SCORPION NEST"),
            LandmarkLabel(TOMB_FLOOR, 48, 6, "BURIAL WINGS"),
            LandmarkLabel(TOMB_FLOOR, 48, 12, "SANCTUM OF AKHET")
        )
        return layout(TOMB_FLOOR, c, camp, a, spawns, landmarks)
    }

    // =====================================================================================
    // Floor -31: Scorchglass Rift (Lv 41-45)
    // =====================================================================================
    private fun buildRift(): Layout {
        val a = RIFT
        val c = DeepDelveFloors.Canvas(SIZE, TileType.MOUNTAIN_ROCK, seed = 3101)
        val sand = TileType.SAND
        val glass = TileType.OBSIDIAN_FLOOR
        val salt = TileType.TEMPLE_FLOOR
        val brine = TileType.WATER
        val camp = UndergroundCampZone(
            floorId = RIFT_FLOOR, name = "SCORCHGLASS RIFT CAMP (PZ)",
            minX = 3, minY = 82, maxX = 14, maxY = 92,
            healerX = 5, healerY = 84, healerName = "Windcaller Nadira",
            merchantX = 12, merchantY = 84, merchantName = "Glass-Hauler Tarek"
        )
        // Prospectors' camp (south-west)
        c.rect(3, 82, 14, 92, sand)
        c[4, 91] = TileType.CAMPFIRE_BRAZIER; c[13, 91] = TileType.CAMPFIRE_BRAZIER
        c[8, 83] = TileType.BIOME_STALL_BADLANDS; c[13, 88] = TileType.CRATE_BARREL
        c[a.stairsUpX, a.stairsUpY] = TileType.STAIRS_UP
        c[a.landingX, a.landingY] = TileType.COBBLESTONE

        // The Rift: a wide winding canyon from the camp up to the Djinn's Eye
        c.tunnel(listOf(14 to 86, 26 to 78, 34 to 64, 46 to 56, 52 to 44, 62 to 34, 70 to 24), 6, sand)
        c.scatterReplace(14, 20, 76, 90, sand, glass, 5)        // glassed streaks where lightning struck

        // Glass Gardens (west): a field of glass spires on fused sand
        c.blob(18, 52, 10, glass)
        c.scatter(8, 42, 28, 62, TileType.CRYSTAL_PILLAR, density = 9, onlyOn = setOf(glass), keepClear = 1)
        c.tunnel(listOf(26 to 56, 38 to 62), 3, glass)
        c.blob(16, 28, 7, sand)
        c.tunnel(listOf(18 to 42, 16 to 34), 3, sand)

        // Salt Flats (east): pale salt crust broken by brine pools
        c.blob(72, 70, 13, salt)
        for ((px, py, rx, ry) in listOf(listOf(66, 66, 4, 3), listOf(78, 74, 5, 3), listOf(74, 62, 3, 2), listOf(64, 78, 3, 2))) c.ellipse(px, py, rx, ry, brine)
        c.tunnel(listOf(46 to 60, 60 to 68), 4, salt, bridge = TileType.STONE_BRIDGE)
        c.blob(86, 88, 5, salt)
        c.tunnel(listOf(78 to 80, 84 to 86), 3, salt, bridge = TileType.STONE_BRIDGE)

        // Dust Bowl (north-west): a round wind-scoured arena
        c.ellipse(36, 18, 11, 9, sand)
        c.ellipse(36, 18, 4, 3, glass)
        c.tunnel(listOf(44 to 24, 54 to 40), 4, sand)

        // The Djinn's Eye (north-east): a glass disc ringed by lava, one bridge in
        c.ellipse(78, 16, 13, 11, TileType.LAVA)
        c.ellipse(78, 16, 9, 7, glass)
        c.rect(69, 22, 73, 27, TileType.STONE_BRIDGE)
        for (y in 25..27) for (x in 69..73) if (y == 27) c[x, y] = TileType.IRON_BAR_GATE
        for ((x, y) in listOf(72 to 12, 84 to 12, 72 to 20, 84 to 20)) c[x, y] = TileType.CRYSTAL_PILLAR
        c[78, 11] = TileType.CAMPFIRE_BRAZIER                      // the storm-lamp

        c.sealPockets(a.landingX, a.landingY)
        c.ores(TileType.ORE_VESPERITE, count = 9, x1 = 4, y1 = 4, x2 = 92, y2 = 92)
        c.ores(TileType.ORE_BOG_IRON, count = 5, x1 = 4, y1 = 4, x2 = 92, y2 = 92)
        c.torches(spacing = 9)

        val stalker = MonsterType.SCORCHGLASS_STALKER
        val devil = MonsterType.DUST_DEVIL
        val golem = MonsterType.SALTGLASS_GOLEM
        val spawns = c.spawns(
            RIFT_FLOOR, camp,
            stalker to (24 to 78), stalker to (34 to 66), devil to (44 to 56), stalker to (52 to 46), devil to (60 to 36),
            stalker to (14 to 50), stalker to (22 to 56), devil to (18 to 46), stalker to (16 to 28),
            golem to (68 to 66), golem to (78 to 70), golem to (72 to 78), devil to (62 to 72), golem to (86 to 88), stalker to (80 to 64),
            devil to (30 to 16), devil to (42 to 20), devil to (36 to 24), stalker to (50 to 32),
            golem to (66 to 30), devil to (56 to 62),
            MonsterType.SIROCCO_THE_STORM_DJINN to (78 to 15)
        )
        val landmarks = listOf(
            LandmarkLabel(RIFT_FLOOR, 40, 58, "THE RIFT"),
            LandmarkLabel(RIFT_FLOOR, 18, 40, "GLASS GARDENS"),
            LandmarkLabel(RIFT_FLOOR, 72, 56, "SALT FLATS"),
            LandmarkLabel(RIFT_FLOOR, 36, 8, "DUST BOWL"),
            LandmarkLabel(RIFT_FLOOR, 78, 3, "THE DJINN'S EYE")
        )
        return layout(RIFT_FLOOR, c, camp, a, spawns, landmarks)
    }

    // =====================================================================================
    // Floor -9: The Blightwood Barrow Catacombs (Frostpeak, Lv 48-53)
    // =====================================================================================
    private fun buildBarrow(): Layout {
        val a = BARROW
        val wall = TileType.GRANITE_WALL
        val c = DeepDelveFloors.Canvas(SIZE, wall, seed = 909)
        val crypt = TileType.CRYPT_FLOOR
        val snow = TileType.SNOW
        val camp = UndergroundCampZone(
            floorId = -9, name = "BARROW CATACOMB CAMP (PZ)",
            minX = 41, minY = 84, maxX = 55, maxY = 91,
            healerX = 43, healerY = 87, healerName = "Frost Cleric Eirik",
            merchantX = 53, merchantY = 87, merchantName = "Shadow Quartermaster Vex"
        )
        southCamp(c, a, crypt, TileType.BIOME_STALL_FROSTPEAK, camp)

        // The Frostbitten Stair up from the camp
        c.rect(46, 72, 50, 83, crypt)
        c.scatterReplace(46, 72, 50, 83, crypt, snow, 3)
        for (x in 46..50) c[x, 78] = TileType.IRON_BAR_GATE

        // Hall of Cairns: a great cross-shaped hall lined with burial cairns
        c.rect(30, 46, 66, 71, crypt)
        c.rect(40, 34, 56, 46, crypt)
        for (px in 33..61 step 7) for (py in listOf(50, 66)) c.rect(px, py, px + 1, py + 1, wall)
        c.scatterReplace(30, 34, 66, 71, crypt, snow, 6)
        c[48, 58] = TileType.CAMPFIRE_BRAZIER

        // Ossuary Galleries (west): long galleries packed with burial niches
        c.rect(6, 40, 26, 80, crypt)
        for (y in 44..76 step 8) c.rect(8, y, 24, y + 1, wall)
        for (y in 44..76 step 8) { c[10 + (y % 3) * 5, y] = crypt; c[11 + (y % 3) * 5, y] = crypt }
        c.burialNiches(4, 38, 28, 82, crypt, density = 2)
        c.rect(26, 58, 30, 60, crypt)

        // Frozen Mere (east): a black lake crossed by stone causeways
        c.ellipse(80, 60, 12, 14, crypt)
        c.ellipse(80, 60, 9, 11, TileType.WATER)
        c.rect(70, 59, 90, 61, TileType.STONE_BRIDGE)
        c.rect(79, 48, 81, 72, TileType.STONE_BRIDGE)
        c.rect(66, 59, 70, 61, crypt)

        // Banshee Choir (north-west): a ring of ice-crystal pillars
        c.ellipse(20, 20, 11, 10, crypt)
        for (k in 0 until 10) {
            val ang = k * Math.PI * 2 / 10
            c[20 + (8 * Math.cos(ang)).toInt(), 20 + (7 * Math.sin(ang)).toInt()] = TileType.CRYSTAL_PILLAR
        }
        c.tunnel(listOf(26 to 28, 40 to 38), 3, crypt)
        c.tunnel(listOf(16 to 30, 14 to 40), 3, crypt)

        // Necromancer's Study (north-east): cells of bookshelves and braziers
        c.rect(62, 10, 90, 32, crypt)
        c.outline(62, 10, 76, 21, wall); c.outline(76, 10, 90, 21, wall)
        for ((x, y) in listOf(69 to 21, 83 to 21, 76 to 15)) { c[x, y] = crypt; c[x + 1, y] = crypt }
        for (x in listOf(64, 65, 66, 78, 79, 80, 86, 87)) c[x, 11] = TileType.BOOKSHELF
        c[70, 16] = TileType.CAMPFIRE_BRAZIER; c[84, 16] = TileType.CAMPFIRE_BRAZIER
        c.tunnel(listOf(62 to 26, 56 to 38), 3, crypt)
        c.tunnel(listOf(80 to 32, 80 to 46), 3, crypt)

        // The Sovereign's Gate (north): gated stair to the Barrow Sovereign's Tomb
        c.rect(46, 6, 50, 33, crypt)
        for (x in 46..50) { c[x, 14] = TileType.IRON_BAR_GATE; c[x, 24] = TileType.IRON_BAR_GATE }
        c.rect(42, 10, 54, 12, crypt)
        c[a.descentX, a.descentY] = TileType.STAIRS_DOWN
        c[a.descentReturnX, a.descentReturnY] = crypt

        c.sealPockets(a.landingX, a.landingY)
        c.ores(TileType.ORE_FROST_IRON, count = 6, x1 = 4, y1 = 4, x2 = 92, y2 = 82)
        c.ores(TileType.ORE_GLACIER_QUARTZ, count = 5, x1 = 4, y1 = 4, x2 = 92, y2 = 82)
        c.torches(spacing = 8)

        val wight = MonsterType.BARROW_WIGHT
        val banshee = MonsterType.FROST_BANSHEE
        val necro = MonsterType.NECROMANCER
        val spawns = c.spawns(
            -9, camp,
            wight to (48 to 74),
            wight to (34 to 54), banshee to (62 to 54), wight to (36 to 68), necro to (60 to 68), banshee to (48 to 40), wight to (48 to 50),
            wight to (10 to 46), wight to (20 to 62), necro to (12 to 78), banshee to (22 to 54),
            banshee to (72 to 50), wight to (88 to 60), wight to (80 to 70), banshee to (80 to 50),
            banshee to (16 to 16), banshee to (24 to 24), wight to (20 to 32),
            necro to (68 to 15), necro to (84 to 26), wight to (70 to 28),
            wight to (48 to 20), banshee to (48 to 30)
        )
        val landmarks = listOf(
            LandmarkLabel(-9, 48, 44, "HALL OF CAIRNS"),
            LandmarkLabel(-9, 16, 37, "OSSUARY GALLERIES"),
            LandmarkLabel(-9, 80, 44, "FROZEN MERE"),
            LandmarkLabel(-9, 20, 8, "BANSHEE CHOIR"),
            LandmarkLabel(-9, 76, 8, "NECROMANCER'S STUDY"),
            LandmarkLabel(-9, 48, 4, "SOVEREIGN'S GATE")
        )
        return layout(-9, c, camp, a, spawns, landmarks)
    }

    // =====================================================================================
    // Floor -32: Rimeheart Caverns (Frostpeak, Lv 54-58)
    // =====================================================================================
    private fun buildRimeheart(): Layout {
        val a = RIMEHEART
        val c = DeepDelveFloors.Canvas(SIZE, TileType.MOUNTAIN_ROCK, seed = 3201)
        val snow = TileType.SNOW
        val ice = TileType.CRYPT_FLOOR
        val water = TileType.WATER
        val camp = UndergroundCampZone(
            floorId = RIMEHEART_FLOOR, name = "RIMEHEART CAVERNS CAMP (PZ)",
            minX = 82, minY = 42, maxX = 93, maxY = 54,
            healerX = 91, healerY = 44, healerName = "Ice Priestess Runa",
            merchantX = 91, merchantY = 52, merchantName = "Glacier Guide Tor"
        )
        // Climbers' camp (east)
        c.rect(82, 42, 93, 54, snow)
        c[83, 43] = TileType.CAMPFIRE_BRAZIER; c[83, 53] = TileType.CAMPFIRE_BRAZIER
        c[87, 43] = TileType.BIOME_STALL_FROSTPEAK; c[87, 53] = TileType.CRATE_BARREL
        c[a.stairsUpX, a.stairsUpY] = TileType.STAIRS_UP
        c[a.landingX, a.landingY] = TileType.COBBLESTONE

        // Snowdrift tunnels winding west
        c.tunnel(listOf(82 to 48, 72 to 44, 64 to 50, 56 to 46), 5, snow)

        // Frozen Falls (centre): a frozen river spilling south, crossed by stone bridges
        c.stream(listOf(50 to 4, 52 to 24, 48 to 46, 52 to 70, 50 to 92), 4, water)
        c.ellipse(50, 48, 9, 8, ice)
        c.rect(44, 46, 56, 50, TileType.STONE_BRIDGE)
        c.scatterReplace(40, 38, 60, 58, ice, snow, 4)

        // Yeti Dens (south): wide snowy caves littered with bones
        c.blob(70, 76, 10, snow)
        c.blob(34, 80, 9, snow)
        c.tunnel(listOf(62 to 52, 68 to 66), 4, snow)
        c.tunnel(listOf(60 to 78, 44 to 80), 4, snow, bridge = TileType.STONE_BRIDGE)
        c.tunnel(listOf(48 to 56, 38 to 72), 3, snow, bridge = TileType.STONE_BRIDGE)

        // Shaman Grotto (north): pools of meltwater among ice spires
        c.blob(70, 18, 11, ice)
        for ((px, py) in listOf(66 to 14, 76 to 20, 68 to 24)) c.ellipse(px, py, 2, 2, water)
        c.scatter(60, 8, 82, 30, TileType.CRYSTAL_PILLAR, density = 5, onlyOn = setOf(ice), keepClear = 1)
        c.tunnel(listOf(68 to 30, 64 to 44), 4, snow)
        c.blob(30, 18, 8, snow)
        c.tunnel(listOf(58 to 18, 38 to 18), 3, snow, bridge = TileType.STONE_BRIDGE)

        // Throne of Rime (west): an ice palace where the Glacier King waits
        c.rect(6, 34, 30, 62, TileType.TEMPLE_FLOOR)
        c.outline(6, 34, 30, 62, TileType.GRANITE_WALL)
        for (y in 46..50) c[30, y] = TileType.TEMPLE_FLOOR
        for (y in 46..50) c[30, y] = TileType.IRON_BAR_GATE
        for ((x, y) in listOf(10 to 38, 26 to 38, 10 to 58, 26 to 58, 18 to 40, 18 to 56)) c[x, y] = TileType.CRYSTAL_PILLAR
        c.rect(8, 46, 12, 50, ice)
        c[8, 48] = TileType.TEMPLE_ALTAR                                // the frozen throne
        c.tunnel(listOf(40 to 48, 31 to 48), 5, snow, bridge = TileType.STONE_BRIDGE)
        c.tunnel(listOf(30 to 26, 22 to 33), 3, snow)
        for (x in 21..23) c[x, 34] = TileType.IRON_BAR_GATE

        c.sealPockets(a.landingX, a.landingY)
        c.ores(TileType.ORE_FROST_IRON, count = 8, x1 = 4, y1 = 4, x2 = 92, y2 = 92)
        c.ores(TileType.ORE_GLACIER_QUARTZ, count = 7, x1 = 4, y1 = 4, x2 = 92, y2 = 92)
        c.torches(spacing = 9)

        val yeti = MonsterType.YETI_RAVAGER
        val fang = MonsterType.RIMEFANG_STALKER
        val shaman = MonsterType.GLACIER_SHAMAN
        val spawns = c.spawns(
            RIMEHEART_FLOOR, camp,
            fang to (74 to 45), fang to (64 to 50), shaman to (56 to 46),
            fang to (50 to 40), yeti to (46 to 54), shaman to (54 to 56),
            yeti to (70 to 74), yeti to (76 to 80), fang to (64 to 70), yeti to (34 to 80), fang to (40 to 76), yeti to (28 to 84),
            shaman to (66 to 16), shaman to (74 to 22), fang to (72 to 10), shaman to (30 to 16), fang to (34 to 22),
            yeti to (22 to 40), shaman to (22 to 56), fang to (16 to 44),
            MonsterType.HRIMGAR_THE_GLACIER_KING to (12 to 48)
        )
        val landmarks = listOf(
            LandmarkLabel(RIMEHEART_FLOOR, 66, 40, "SNOWDRIFT TUNNELS"),
            LandmarkLabel(RIMEHEART_FLOOR, 50, 36, "FROZEN FALLS"),
            LandmarkLabel(RIMEHEART_FLOOR, 60, 88, "YETI DENS"),
            LandmarkLabel(RIMEHEART_FLOOR, 70, 4, "SHAMAN GROTTO"),
            LandmarkLabel(RIMEHEART_FLOOR, 18, 31, "THRONE OF RIME")
        )
        return layout(RIMEHEART_FLOOR, c, camp, a, spawns, landmarks)
    }

    // =====================================================================================
    // Floor -33: Cinderfall Mines (Ashen Caldera, Lv 55-60)
    // =====================================================================================
    private fun buildCinderfall(): Layout {
        val a = CINDERFALL
        val c = DeepDelveFloors.Canvas(SIZE, TileType.MOUNTAIN_ROCK, seed = 3301)
        val dirt = TileType.DIRT_FLOOR
        val ash = TileType.VOLCANIC_ASH
        val glass = TileType.OBSIDIAN_FLOOR
        val lava = TileType.LAVA
        val camp = UndergroundCampZone(
            floorId = CINDERFALL_FLOOR, name = "CINDERFALL MINES CAMP (PZ)",
            minX = 41, minY = 84, maxX = 55, maxY = 91,
            healerX = 43, healerY = 87, healerName = "Ash Mender Hesta",
            merchantX = 53, merchantY = 87, merchantName = "Shaft Boss Grenn"
        )
        southCamp(c, a, ash, TileType.BIOME_STALL_ASHEN, camp)

        // Haulage tunnel north from the camp, lined with old ore carts
        c.rect(46, 66, 50, 83, dirt)
        for (y in 70..80 step 5) c[45, y] = TileType.CRATE_BARREL

        // The Great Shaft (centre): a wide cavern around a lava pit, crossed by a stone bridge
        c.blob(48, 54, 13, dirt)
        c.ellipse(48, 52, 5, 4, lava)
        c.rect(46, 47, 50, 57, TileType.STONE_BRIDGE)
        c.scatterReplace(34, 40, 62, 68, dirt, ash, 4)

        // Collapsed Galleries (west): long galleries choked with fallen pillars
        c.rect(6, 40, 28, 78, dirt)
        for (y in 44..74 step 6) c.rect(8, y, 26, y, TileType.MOUNTAIN_ROCK)
        for (y in 44..74 step 6) { val gx = if ((y / 6) % 2 == 0) 9 else 23; c.rect(gx, y, gx + 2, y, dirt) }
        c.scatter(6, 40, 28, 78, TileType.GRANITE_WALL, density = 11, onlyOn = setOf(dirt), keepClear = 1)
        c.tunnel(listOf(28 to 58, 36 to 56), 4, dirt)

        // Magma Vents (east): ash fields broken by bubbling lava pools
        c.blob(76, 64, 13, ash)
        for ((px, py, rx, ry) in listOf(listOf(70, 60, 3, 2), listOf(82, 66, 4, 3), listOf(76, 74, 3, 2), listOf(86, 56, 2, 2))) c.ellipse(px, py, rx, ry, lava)
        c.scatterReplace(62, 50, 90, 80, ash, glass, 6)
        c.tunnel(listOf(60 to 56, 66 to 62), 4, dirt)
        c.tunnel(listOf(56 to 80, 70 to 78), 3, ash)

        // Slinger Roost (north-west): a high cave of glassy ledges
        c.blob(22, 20, 10, glass)
        c.scatter(14, 12, 30, 28, TileType.CRYSTAL_PILLAR, density = 9, onlyOn = setOf(glass), keepClear = 1)
        c.tunnel(listOf(18 to 30, 16 to 40), 3, dirt)
        c.tunnel(listOf(32 to 22, 42 to 40), 3, dirt)

        // The Foreman's Forge (north-east): an obsidian hall over lava channels, one gate in
        c.rect(60, 6, 90, 30, glass)
        c.outline(60, 6, 90, 30, TileType.OBSIDIAN_WALL)
        c.rect(64, 16, 86, 17, lava)
        c.rect(73, 16, 77, 17, TileType.STONE_BRIDGE)
        for (x in 66..84 step 6) { c[x, 8] = TileType.SMELTING_FURNACE; c[x, 28] = TileType.BLACKSMITH_ANVIL }
        c[75, 10] = TileType.CAMPFIRE_BRAZIER
        for (x in 73..77) c[x, 30] = TileType.IRON_BAR_GATE
        c.tunnel(listOf(56 to 44, 75 to 34, 75 to 31), 4, dirt)
        c.tunnel(listOf(48 to 41, 56 to 44), 4, dirt)

        c.sealPockets(a.landingX, a.landingY)
        c.ores(TileType.ORE_CINDERITE, count = 10, x1 = 4, y1 = 4, x2 = 92, y2 = 82)
        c.torches(spacing = 9)

        val miner = MonsterType.ASHBORN_MINER
        val crawler = MonsterType.MAGMA_CRAWLER
        val slinger = MonsterType.CINDER_SLINGER
        val spawns = c.spawns(
            CINDERFALL_FLOOR, camp,
            miner to (48 to 72),
            miner to (40 to 60), crawler to (56 to 60), slinger to (42 to 46), miner to (56 to 46), crawler to (38 to 52),
            miner to (12 to 42), miner to (20 to 54), slinger to (14 to 66), miner to (22 to 76), crawler to (10 to 72),
            crawler to (70 to 66), crawler to (80 to 60), slinger to (84 to 72), crawler to (72 to 76), miner to (66 to 54),
            slinger to (18 to 16), slinger to (26 to 24), crawler to (22 to 32),
            miner to (66 to 24), slinger to (84 to 24), crawler to (68 to 12),
            MonsterType.ORDRAK_THE_MOLTEN_FOREMAN to (75 to 12)
        )
        val landmarks = listOf(
            LandmarkLabel(CINDERFALL_FLOOR, 48, 38, "THE GREAT SHAFT"),
            LandmarkLabel(CINDERFALL_FLOOR, 17, 37, "COLLAPSED GALLERIES"),
            LandmarkLabel(CINDERFALL_FLOOR, 76, 48, "MAGMA VENTS"),
            LandmarkLabel(CINDERFALL_FLOOR, 22, 8, "SLINGER ROOST"),
            LandmarkLabel(CINDERFALL_FLOOR, 75, 3, "THE FOREMAN'S FORGE")
        )
        return layout(CINDERFALL_FLOOR, c, camp, a, spawns, landmarks)
    }

    // =====================================================================================
    // Floor -34: Emberhold, the Buried City (Ashen Caldera, Lv 61-66)
    // =====================================================================================
    private fun buildEmberhold(): Layout {
        val a = EMBERHOLD
        val c = DeepDelveFloors.Canvas(SIZE, TileType.MOUNTAIN_ROCK, seed = 3401)
        val ash = TileType.VOLCANIC_ASH
        val street = TileType.COBBLESTONE
        val wall = TileType.OBSIDIAN_WALL
        val floor = TileType.OBSIDIAN_FLOOR
        val lava = TileType.LAVA
        val camp = UndergroundCampZone(
            floorId = EMBERHOLD_FLOOR, name = "EMBERHOLD DIGSITE CAMP (PZ)",
            minX = 2, minY = 42, maxX = 13, maxY = 54,
            healerX = 4, healerY = 44, healerName = "Ash Cleric Orsa",
            merchantX = 4, merchantY = 52, merchantName = "Relic Digger Fenn"
        )
        // Diggers' camp (west), where the excavation broke into the buried city
        c.rect(2, 42, 13, 54, ash)
        c[12, 43] = TileType.CAMPFIRE_BRAZIER; c[12, 53] = TileType.CAMPFIRE_BRAZIER
        c[8, 43] = TileType.BIOME_STALL_ASHEN; c[8, 53] = TileType.CRATE_BARREL
        c[a.stairsUpX, a.stairsUpY] = TileType.STAIRS_UP
        c[a.landingX, a.landingY] = TileType.COBBLESTONE

        // The buried city: a grid of ash-choked streets
        c.rect(14, 10, 62, 86, ash)
        for (y in listOf(20, 36, 48, 60, 76)) c.rect(14, y - 1, 62, y + 1, street)
        for (x in listOf(24, 38, 52)) c.rect(x - 1, 10, x + 1, 86, street)
        c.scatterReplace(14, 10, 62, 86, street, ash, 5)
        // ruined houses in every block, walls broken by the eruption
        val blocksX = listOf(15 to 22, 26 to 36, 40 to 50, 54 to 62)
        val blocksY = listOf(11 to 18, 22 to 34, 38 to 46, 62 to 74, 78 to 86)
        for ((bx1, bx2) in blocksX) for ((by1, by2) in blocksY) {
            val x1 = bx1 + 1; val x2 = bx2 - 1; val y1 = by1 + 1; val y2 = by2 - 1
            if (x2 - x1 < 4 || y2 - y1 < 4) continue
            c.rect(x1, y1, x2, y2, floor)
            c.outline(x1, y1, x2, y2, wall)
            c[(x1 + x2) / 2, y2] = floor; c[(x1 + x2) / 2, y1] = floor
            if (c.hash(x1, y1, 5) % 2 == 0) c[x1, (y1 + y2) / 2] = ash else c[x2, (y1 + y2) / 2] = ash
        }

        // Ashfall Plaza (centre): a cracked fountain now brimming with lava
        c.rect(26, 50, 50, 58, street)
        c.ellipse(38, 54, 3, 2, lava)
        for ((x, y) in listOf(30 to 51, 46 to 51, 30 to 57, 46 to 57)) c[x, y] = TileType.CRYSTAL_PILLAR

        // Hound Kennels (south-east): a scorched yard of pens
        c.blob(76, 80, 10, ash)
        for (px in 68..84 step 5) c.rect(px, 73, px, 76, TileType.IRON_BAR_GATE)
        c.tunnel(listOf(62 to 80, 68 to 80), 4, street)

        // Witches' Coven (north-east): a ring of braziers around a lava well
        c.blob(78, 16, 10, floor)
        c.ellipse(78, 16, 2, 2, lava)
        for (k in 0 until 8) {
            val ang = k * Math.PI * 2 / 8
            c[78 + (6 * Math.cos(ang)).toInt(), 16 + (5 * Math.sin(ang)).toInt()] = TileType.CAMPFIRE_BRAZIER
        }
        c.tunnel(listOf(62 to 20, 70 to 18), 4, street)

        // Palace of Sevra (east): a gated hall with the ash throne
        c.rect(66, 32, 92, 64, TileType.TEMPLE_FLOOR)
        c.outline(66, 32, 92, 64, wall)
        for (y in listOf(36, 60)) for (x in 70..88 step 6) c[x, y] = TileType.CRYSTAL_PILLAR
        c.rect(84, 44, 90, 52, floor)
        c[90, 48] = TileType.TEMPLE_ALTAR                               // the ash throne
        c.rect(78, 40, 79, 56, lava); c.rect(78, 46, 79, 50, TileType.STONE_BRIDGE)
        c.tunnel(listOf(62 to 48, 65 to 48), 5, street)
        for (y in 46..50) c[66, y] = TileType.IRON_BAR_GATE

        // diggers' trench into the city
        c.tunnel(listOf(13 to 48, 16 to 48), 5, ash)

        c.sealPockets(a.landingX, a.landingY)
        c.ores(TileType.ORE_CINDERITE, count = 9, x1 = 4, y1 = 4, x2 = 92, y2 = 92)
        c.torches(spacing = 9)

        val rev = MonsterType.ASHEN_REVENANT
        val hound = MonsterType.SMOLDER_HOUND
        val witch = MonsterType.CINDER_WITCH
        val spawns = c.spawns(
            EMBERHOLD_FLOOR, camp,
            rev to (20 to 48), hound to (24 to 40),
            rev to (32 to 54), witch to (44 to 54), rev to (38 to 36), hound to (52 to 48), rev to (24 to 60),
            rev to (18 to 20), witch to (30 to 14), rev to (44 to 28), hound to (56 to 20), rev to (52 to 70), witch to (30 to 80), hound to (42 to 76),
            hound to (72 to 82), hound to (80 to 78), rev to (84 to 84),
            witch to (74 to 14), witch to (84 to 18), witch to (78 to 24),
            rev to (70 to 40), rev to (72 to 58), witch to (74 to 48),
            MonsterType.SEVRA_THE_ASH_CROWNED to (86 to 48)
        )
        val landmarks = listOf(
            LandmarkLabel(EMBERHOLD_FLOOR, 38, 47, "ASHFALL PLAZA"),
            LandmarkLabel(EMBERHOLD_FLOOR, 38, 8, "BURIED STREETS"),
            LandmarkLabel(EMBERHOLD_FLOOR, 76, 70, "HOUND KENNELS"),
            LandmarkLabel(EMBERHOLD_FLOOR, 78, 4, "WITCHES' COVEN"),
            LandmarkLabel(EMBERHOLD_FLOOR, 80, 29, "PALACE OF SEVRA")
        )
        return layout(EMBERHOLD_FLOOR, c, camp, a, spawns, landmarks)
    }
}
