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
 */
object FrontierSiteFloors {
    const val SIZE = 96

    val MIRE = Anchors(48, 90, 48, 89, 48, 6, 48, 7)
    val ZIGGURAT = Anchors(5, 48, 6, 48, 52, 44, 52, 45)
    val GOREK = Anchors(48, 90, 48, 89, 48, 7, 48, 8)
    /** New dungeons have their boss on the same floor: the "descent" fields mirror the landing. */
    val MURKFEN = Anchors(5, 48, 6, 48, 6, 48, 6, 48)
    val TOMB = Anchors(48, 90, 48, 89, 48, 89, 48, 89)

    const val MURKFEN_FLOOR = -29
    const val TOMB_FLOOR = -30

    /** Overland entrances of the two new dungeons. */
    const val MURKFEN_ENTRY_X = 452
    const val MURKFEN_ENTRY_Y = 612
    const val TOMB_ENTRY_X = 300
    const val TOMB_ENTRY_Y = 850

    val newFloorIds: List<Int> = listOf(MURKFEN_FLOOR, TOMB_FLOOR)

    fun anchorsFor(floorId: Int): Anchors? = when (floorId) {
        -6 -> GOREK
        -7 -> MIRE
        -8 -> ZIGGURAT
        MURKFEN_FLOOR -> MURKFEN
        TOMB_FLOOR -> TOMB
        else -> null
    }

    fun layoutFor(floorId: Int): Layout? = when (floorId) {
        -6 -> gorekLabyrinth
        -7 -> mireVaults
        -8 -> ziggurat
        MURKFEN_FLOOR -> murkfenHollow
        TOMB_FLOOR -> tombOfAkhet
        else -> null
    }

    val mireVaults: Layout by lazy { buildMireVaults() }
    val ziggurat: Layout by lazy { buildZiggurat() }
    val gorekLabyrinth: Layout by lazy { buildGorek() }
    val murkfenHollow: Layout by lazy { buildMurkfen() }
    val tombOfAkhet: Layout by lazy { buildTomb() }

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
        c.ores(TileType.ORE_CINDERITE, count = 8, x1 = 4, y1 = 4, x2 = 92, y2 = 82)
        c.ores(TileType.ORE_VESPERITE, count = 6, x1 = 4, y1 = 4, x2 = 92, y2 = 82)
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
}
