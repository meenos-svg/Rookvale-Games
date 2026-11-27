package com.example.game.world

import com.example.game.model.MonsterType
import com.example.game.model.TileType

/**
 * Hand-shaped 96x96 layouts for three of Rookvale's standalone dungeons:
 *
 * - **-11 NW Copper Quarry Mines** (Lv 5-9): a working mine. Rope down into a timber mining camp,
 *   a long haulage drift, a stepped open-pit quarry around a flooded pit, kobold warrens, comb-like
 *   ore galleries, a flooded sump and the collapsed workings around the Foreman's office.
 * - **-12 North Mountain Cavern** (Lv 7-11): the inside of a mountain. Wolf dens above the camp,
 *   switchback ledges up the west wall, a bottomless chasm crossed by one long sky-bridge, harpy
 *   roosts climbing the east face, a crystal golem quarry and the summit hollow.
 * - **-14 Crag Deep Hollow** (Lv 13-17): an outlaw-held granite fortress. Bailey and moat, a keep
 *   with barracks, prison cells, a great hall, an armory and treasury vaults, crag caves outside
 *   the walls and the Sealed Vault to the north.
 *
 * All portal and landing coordinates live here so [WorldMaps] portals, the Lower Boss Lair specs
 * in [ContinentalDungeonGenerator] and the tiles always agree.
 */
object HeartlandSiteFloors {
    const val SIZE = 96

    /** Everything [ContinentalDungeonGenerator] needs to turn a layout into a floor. */
    class Layout(
        val floorId: Int,
        val tiles: Array<Array<TileType>>,
        val camp: UndergroundCampZone,
        val spawns: List<MonsterSpawn>,
        val landmarks: List<LandmarkLabel>,
        val stairsUpX: Int,
        val stairsUpY: Int,
        val landingX: Int,
        val landingY: Int,
        val descentX: Int,
        val descentY: Int,
        val descentReturnX: Int,
        val descentReturnY: Int
    )

    /** Fixed entry/exit coordinates per floor (available without building the tiles). */
    data class Anchors(
        val stairsUpX: Int, val stairsUpY: Int,
        val landingX: Int, val landingY: Int,
        val descentX: Int, val descentY: Int,
        val descentReturnX: Int, val descentReturnY: Int
    )

    // -11: rope spot in the NW mining camp, descent in the SE Foreman's office
    val QUARRY = Anchors(12, 7, 12, 8, 84, 90, 84, 89)
    // -12: stairs in the south camp, descent at the summit
    val MOUNTAIN = Anchors(48, 89, 48, 88, 48, 7, 48, 8)
    // -14: stairs in the west camp, descent in the Sealed Vault (north)
    val CRAG = Anchors(5, 48, 6, 48, 52, 4, 52, 5)

    val floorIds: Set<Int> = setOf(-11, -12, -14)

    fun anchorsFor(floorId: Int): Anchors? = when (floorId) {
        -11 -> QUARRY
        -12 -> MOUNTAIN
        -14 -> CRAG
        else -> null
    }

    fun layoutFor(floorId: Int): Layout? = when (floorId) {
        -11 -> quarryMines
        -12 -> mountainCavern
        -14 -> cragHollow
        else -> null
    }

    val quarryMines: Layout by lazy { buildQuarryMines() }
    val mountainCavern: Layout by lazy { buildMountainCavern() }
    val cragHollow: Layout by lazy { buildCragHollow() }

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

    // =====================================================================================
    // Floor -11: NW Copper Quarry Mines (Lv 5-9)
    // =====================================================================================
    private fun buildQuarryMines(): Layout {
        val a = QUARRY
        val c = DeepDelveFloors.Canvas(SIZE, TileType.CAVE_WALL, seed = 1103)
        val dirt = TileType.DIRT_FLOOR
        val cobble = TileType.COBBLESTONE
        val plank = TileType.WOOD_PLANK_FLOOR
        val camp = UndergroundCampZone(
            floorId = -11, name = "QUARRY MINES CAMP (PZ)",
            minX = 6, minY = 5, maxX = 18, maxY = 13,
            healerX = 8, healerY = 10, healerName = "Quarry Medic Linna",
            merchantX = 16, merchantY = 10, merchantName = "Ore Peddler Garret"
        )

        // Timber mining camp under the rope hole (NW)
        c.rect(6, 5, 18, 13, plank)
        c.rect(10, 9, 14, 11, cobble)
        c[7, 6] = TileType.CAMPFIRE_BRAZIER; c[17, 6] = TileType.CAMPFIRE_BRAZIER
        c[7, 12] = TileType.WAR_BANNER; c[17, 12] = TileType.WAR_BANNER
        c[9, 6] = TileType.CRATE_BARREL; c[15, 6] = TileType.CRATE_BARREL
        c[12, 12] = TileType.BIOME_STALL_HEARTLAND
        c[a.stairsUpX, a.stairsUpY] = TileType.ROPE_SPOT_UP
        c[a.landingX, a.landingY] = cobble

        // Main haulage drift running east, with a cobbled cart track down the middle
        c.rect(19, 9, 90, 12, dirt)
        c.rect(19, 10, 90, 11, cobble)
        for (y in 9..12) c[19, y] = TileType.IRON_BAR_GATE
        for (x in 26..86 step 10) { c[x, 8] = TileType.CRATE_BARREL; c[x + 4, 13] = TileType.CRATE_BARREL }

        // Comb of ore galleries hanging off the drift (NE)
        for (x in listOf(58, 66, 74, 82, 89)) c.rect(x, 13, x + 1, 30, dirt)
        c.rect(58, 21, 90, 22, dirt)
        c.rect(58, 29, 75, 30, dirt)
        c.rect(81, 29, 90, 30, dirt)
        c.blob(62, 32, 3, dirt)
        c.blob(86, 34, 3, dirt)

        // Stepped open-pit quarry (centre) around a flooded pit with a cart island
        c.rect(47, 13, 49, 27, dirt)                                        // drift -> pit rim
        c.rect(30, 28, 66, 64, dirt)
        c.outline(34, 32, 62, 60, TileType.CAVE_WALL)
        c.rect(35, 33, 61, 59, cobble)
        c.outline(39, 37, 57, 55, TileType.CAVE_WALL)
        c.rect(40, 38, 56, 54, dirt)
        c.rect(43, 41, 53, 51, TileType.WATER)
        c.rect(46, 44, 50, 48, cobble)
        c.rect(47, 41, 49, 43, TileType.WOOD_BRIDGE)                         // ramps & bridges
        c.rect(47, 49, 49, 51, TileType.WOOD_BRIDGE)
        for ((x, y) in listOf(47 to 32, 48 to 32, 61 to 46, 62 to 46, 34 to 52, 34 to 53)) c[x, y] = dirt
        for ((x, y) in listOf(39 to 44, 39 to 45, 57 to 40, 57 to 41, 48 to 55, 49 to 55)) c[x, y] = cobble
        c.scatter(30, 28, 66, 64, TileType.CRATE_BARREL, density = 19, onlyOn = setOf(dirt), keepClear = 1)

        // Kobold warrens (W): a cramped burrow maze with dead-end nooks
        c.rect(6, 14, 7, 19, dirt)                                          // camp -> warrens
        c.maze(6, 20, cols = 6, rows = 10, pitch = 4, passage = 2, extraLoops = 10, tile = dirt)
        c.burialNiches(5, 19, 29, 60, dirt, density = 6)
        c.rect(27, 44, 30, 45, dirt)                                        // warrens -> quarry

        // Flooded sump (SE): caverns cut by an underground stream, crossed by plank bridges
        c.blob(76, 50, 8, dirt)
        c.blob(86, 64, 6, dirt)
        c.blob(72, 72, 7, dirt)
        c.stream(listOf(67 to 58, 76 to 60, 92 to 70), 3, TileType.WATER)
        c.tunnel(listOf(66 to 46, 76 to 50), 3, dirt, bridge = TileType.WOOD_BRIDGE)
        c.tunnel(listOf(76 to 50, 86 to 64), 2, dirt, bridge = TileType.WOOD_BRIDGE)
        c.tunnel(listOf(76 to 50, 72 to 72), 3, dirt, bridge = TileType.WOOD_BRIDGE)
        c.tunnel(listOf(86 to 34, 88 to 44, 84 to 50), 2, dirt, bridge = TileType.WOOD_BRIDGE)

        // Collapsed workings (S & SW) and the Foreman's office (SE corner)
        c.rect(47, 65, 49, 72, dirt)
        c.blob(48, 78, 6, dirt)
        c.blob(22, 80, 6, dirt)
        c.blob(10, 70, 4, dirt)
        c.tunnel(listOf(48 to 78, 34 to 82, 22 to 80), 2, dirt)
        c.tunnel(listOf(10 to 57, 10 to 70, 22 to 80), 2, dirt)
        c.tunnel(listOf(48 to 78, 64 to 84, 77 to 86), 2, dirt)
        c.tunnel(listOf(72 to 72, 80 to 80), 2, dirt, bridge = TileType.WOOD_BRIDGE)
        c.rect(78, 80, 91, 91, plank)
        c.outline(77, 79, 92, 92, TileType.CAVE_WALL)
        for (y in 85..86) c[77, y] = TileType.IRON_BAR_GATE
        c[80, 78] = dirt; c[80, 79] = TileType.IRON_BAR_GATE
        c[79, 81] = TileType.CRATE_BARREL; c[90, 81] = TileType.CRATE_BARREL; c[79, 90] = TileType.CRATE_BARREL
        c[82, 82] = TileType.BOOKSHELF; c[83, 82] = TileType.BOOKSHELF
        c[a.descentX, a.descentY] = TileType.STAIRS_DOWN
        c[a.descentReturnX, a.descentReturnY] = plank

        c.sealPockets(a.landingX, a.landingY)
        c.ores(TileType.ORE_COPPER, count = 10, x1 = 20, y1 = 6, x2 = 92, y2 = 66)
        c.ores(TileType.ORE_TIN, count = 7, x1 = 4, y1 = 14, x2 = 92, y2 = 92)
        c.torches(spacing = 7)

        val kobold = MonsterType.TUNNEL_KOBOLD_SAPPER
        val mole = MonsterType.QUARRY_MOLE_BRUTE
        val rat = MonsterType.CAVE_RAT
        val spawns = c.spawns(
            -11, camp,
            rat to (34 to 11), kobold to (54 to 11), rat to (78 to 11),
            mole to (66 to 26), kobold to (82 to 18), mole to (89 to 28),
            kobold to (10 to 26), kobold to (22 to 30), rat to (14 to 42), kobold to (26 to 54),
            mole to (32 to 34), mole to (62 to 60), kobold to (44 to 36), mole to (48 to 46),
            rat to (76 to 52), mole to (86 to 64), rat to (72 to 74),
            kobold to (48 to 80), rat to (22 to 82), mole to (36 to 82),
            kobold to (82 to 84), mole to (88 to 88)
        )
        val landmarks = listOf(
            LandmarkLabel(-11, 54, 10, "HAULAGE DRIFT"),
            LandmarkLabel(-11, 74, 25, "ORE GALLERIES"),
            LandmarkLabel(-11, 48, 30, "OPEN-PIT QUARRY"),
            LandmarkLabel(-11, 16, 38, "KOBOLD WARRENS"),
            LandmarkLabel(-11, 78, 62, "FLOODED SUMP"),
            LandmarkLabel(-11, 34, 80, "COLLAPSED WORKINGS"),
            LandmarkLabel(-11, 84, 82, "FOREMAN'S OFFICE")
        )
        return layout(-11, c, camp, a, spawns, landmarks)
    }

    // =====================================================================================
    // Floor -12: North Mountain Cavern (Lv 7-11)
    // =====================================================================================
    private fun buildMountainCavern(): Layout {
        val a = MOUNTAIN
        val c = DeepDelveFloors.Canvas(SIZE, TileType.STONE_WALL, seed = 1217)
        val dirt = TileType.DIRT_FLOOR
        val ledge = TileType.CRYPT_FLOOR
        val chasm = TileType.DEEP_WATER
        val camp = UndergroundCampZone(
            floorId = -12, name = "MOUNTAIN CAVERN CAMP (PZ)",
            minX = 41, minY = 84, maxX = 55, maxY = 90,
            healerX = 43, healerY = 87, healerName = "Ridge Cleric Dara",
            merchantX = 53, merchantY = 87, merchantName = "Mountaineer Orin"
        )

        // Base camp (south centre)
        c.rect(41, 84, 55, 90, ledge)
        c.rect(46, 86, 50, 88, TileType.COBBLESTONE)
        c[42, 85] = TileType.CAMPFIRE_BRAZIER; c[54, 85] = TileType.CAMPFIRE_BRAZIER
        c[42, 90] = TileType.WAR_BANNER; c[54, 90] = TileType.WAR_BANNER
        c[48, 85] = TileType.BIOME_STALL_HEARTLAND
        c[a.stairsUpX, a.stairsUpY] = TileType.STAIRS_UP
        c[a.landingX, a.landingY] = TileType.COBBLESTONE

        // Foothill wolf dens just above camp
        c.rect(47, 79, 49, 83, dirt)
        for (x in 47..49) c[x, 83] = TileType.IRON_BAR_GATE
        c.blob(48, 74, 7, dirt)
        c.blob(36, 78, 4, dirt)
        c.blob(60, 78, 4, dirt)
        c.tunnel(listOf(48 to 74, 36 to 78), 2, dirt)
        c.tunnel(listOf(48 to 74, 60 to 78), 2, dirt)

        // Bottomless chasm with crag spires, crossed by a single long sky-bridge
        c.ellipse(48, 44, 19, 18, chasm)
        c.blob(48, 44, 4, ledge)
        c.blob(38, 34, 2, TileType.STONE_WALL)
        c.blob(58, 52, 2, TileType.STONE_WALL)
        c.blob(40, 56, 2, TileType.STONE_WALL)
        c.tunnel(listOf(48 to 74, 48 to 44), 2, ledge)
        c.tunnel(listOf(48 to 44, 48 to 14), 2, ledge)

        // West switchback ledges climbing the mountain wall
        val ledgeRows = listOf(70, 60, 50, 40, 30, 20)
        for (y in ledgeRows) c.rect(7, y, 26, y + 1, ledge)
        ledgeRows.zipWithNext().forEachIndexed { i, (lower, upper) ->
            val x = if (i % 2 == 0) 7 else 25
            c.rect(x, upper, x + 1, lower + 1, ledge)
        }
        c.tunnel(listOf(40 to 76, 26 to 71), 2, dirt)
        for (y in listOf(65, 45, 25)) {                                                  // wolf hollows off the ledges
            c.rect(16, y - 3, 17, y - 1, dirt)
            c.blob(16, y, 2, dirt)
        }
        c.rect(27, 40, 30, 41, ledge)                                                    // chasm overlook (dead end)

        // East face: harpy roosts climbing toward the summit, frosted near the top
        c.tunnel(listOf(56 to 76, 74 to 74), 2, dirt)
        c.blob(78, 72, 5, dirt)
        c.blob(86, 58, 5, dirt)
        c.blob(76, 46, 5, dirt)
        c.blob(86, 32, 5, dirt)
        c.blob(76, 20, 5, dirt)
        c.tunnel(listOf(78 to 72, 86 to 58, 76 to 46, 86 to 32, 76 to 20), 2, dirt)
        c.scatterReplace(68, 10, 94, 40, dirt, TileType.SNOW, density = 2)

        // Golem quarry hall (NW top) with crystal pillars
        c.rect(16, 15, 18, 20, ledge)
        c.rect(8, 5, 36, 14, ledge)
        for (x in 11..33 step 5) { c[x, 7] = TileType.CRYSTAL_PILLAR; c[x, 12] = TileType.CRYSTAL_PILLAR }
        c.scatterReplace(8, 5, 36, 14, ledge, TileType.COBBLESTONE, density = 5)

        // Summit hollow with a mountain tarn and the descent to the Matriarch's Roost
        c.blob(48, 14, 6, dirt)
        c.blob(66, 10, 4, TileType.WATER)
        c.tunnel(listOf(36 to 10, 43 to 12), 2, dirt)
        c.tunnel(listOf(54 to 13, 66 to 10, 76 to 20), 2, dirt, bridge = TileType.STONE_BRIDGE)
        c.rect(47, 6, 49, 9, dirt)
        c[a.descentX, a.descentY] = TileType.STAIRS_DOWN
        c[a.descentReturnX, a.descentReturnY] = dirt

        c.sealPockets(a.landingX, a.landingY)
        c.ores(TileType.ORE_COPPER, count = 8, x1 = 4, y1 = 4, x2 = 92, y2 = 82)
        c.ores(TileType.ORE_BOG_IRON, count = 6, x1 = 4, y1 = 4, x2 = 92, y2 = 82)
        c.torches(spacing = 7)

        val harpy = MonsterType.CRAG_HARPY
        val golem = MonsterType.SHALE_GOLEM
        val wolf = MonsterType.CRAG_WOLF
        val spawns = c.spawns(
            -12, camp,
            wolf to (48 to 72), wolf to (36 to 78), wolf to (60 to 78), wolf to (44 to 76),
            wolf to (20 to 70), wolf to (16 to 65), harpy to (12 to 60), wolf to (20 to 50),
            golem to (16 to 45), harpy to (10 to 40), wolf to (20 to 30), golem to (16 to 25),
            harpy to (48 to 58), harpy to (48 to 44), harpy to (48 to 30),
            harpy to (78 to 72), harpy to (86 to 58), golem to (76 to 46), harpy to (86 to 32), harpy to (76 to 20),
            golem to (14 to 10), golem to (24 to 9), golem to (32 to 12), golem to (48 to 16)
        )
        val landmarks = listOf(
            LandmarkLabel(-12, 48, 70, "FOOTHILL DENS"),
            LandmarkLabel(-12, 16, 56, "SWITCHBACK LEDGES"),
            LandmarkLabel(-12, 48, 52, "THE SKY-BRIDGE"),
            LandmarkLabel(-12, 84, 46, "HARPY ROOSTS"),
            LandmarkLabel(-12, 22, 9, "GOLEM QUARRY"),
            LandmarkLabel(-12, 48, 12, "SUMMIT HOLLOW")
        )
        return layout(-12, c, camp, a, spawns, landmarks)
    }

    // =====================================================================================
    // Floor -14: Crag Deep Hollow (Lv 13-17)
    // =====================================================================================
    private fun buildCragHollow(): Layout {
        val a = CRAG
        val wall = TileType.GRANITE_WALL
        val c = DeepDelveFloors.Canvas(SIZE, wall, seed = 1429)
        val floor = TileType.CRYPT_FLOOR
        val cobble = TileType.COBBLESTONE
        val dirt = TileType.DIRT_FLOOR
        val gate = TileType.IRON_BAR_GATE
        val camp = UndergroundCampZone(
            floorId = -14, name = "CRAG HOLLOW CAMP (PZ)",
            minX = 3, minY = 42, maxX = 13, maxY = 54,
            healerX = 5, healerY = 44, healerName = "Warden Priest Kell",
            merchantX = 5, merchantY = 52, merchantName = "Quartermaster Syra"
        )

        // Warden camp (west)
        c.rect(3, 42, 13, 54, cobble)
        c[12, 43] = TileType.CAMPFIRE_BRAZIER; c[12, 53] = TileType.CAMPFIRE_BRAZIER
        c[3, 42] = TileType.WAR_BANNER; c[3, 54] = TileType.WAR_BANNER
        c[9, 43] = TileType.BIOME_STALL_HEARTLAND
        c[a.stairsUpX, a.stairsUpY] = TileType.STAIRS_UP
        c[a.landingX, a.landingY] = cobble

        // Outer bailey, moat and keep walls
        c.rect(14, 47, 17, 49, floor)
        for (y in 47..49) c[14, y] = gate
        c.rect(18, 14, 80, 80, cobble)
        c.rect(22, 18, 76, 76, TileType.WATER)
        c.rect(26, 22, 72, 72, wall)
        c.rect(27, 23, 71, 71, floor)
        c.rect(22, 47, 26, 49, TileType.STONE_BRIDGE)                       // west drawbridge
        for (y in 47..49) c[26, y] = gate
        c.rect(72, 47, 76, 49, TileType.STONE_BRIDGE)                       // east drawbridge
        for (y in 47..49) c[72, y] = gate
        c.rect(48, 18, 50, 22, TileType.STONE_BRIDGE)                       // north drawbridge
        for (x in 48..50) c[x, 22] = gate
        c.scatter(18, 14, 80, 80, TileType.CRATE_BARREL, density = 23, onlyOn = setOf(cobble), keepClear = 1)
        c.scatter(18, 14, 80, 80, TileType.WEAPON_RACK, density = 37, onlyOn = setOf(cobble), keepClear = 1)

        // Keep interior: divider walls with a gated cross-corridor
        for (y in 23..71) { c[42, y] = wall; c[56, y] = wall }
        for (x in 27..41) { c[x, 46] = wall; c[x, 50] = wall }
        for (x in 57..71) { c[x, 46] = wall; c[x, 50] = wall }
        for (y in 47..49) { c[42, y] = gate; c[56, y] = gate }

        // West barracks (bunks) and doors off the corridor
        for (x in listOf(30, 31, 38, 39)) c[x, 46] = floor
        for (y in 25..43 step 3) for (x in listOf(28, 29, 33, 34, 36, 37)) c[x, y] = TileType.WOOD_BED
        c[40, 24] = TileType.CRATE_BARREL; c[40, 44] = TileType.CRATE_BARREL

        // Prison block: barred cells either side of an aisle
        for (x in 33..35) c[x, 50] = floor
        for (cy in 52..67 step 5) {
            for (x in 27..41) c[x, cy - 1] = wall
            c.rect(28, cy, 31, cy + 3, dirt); c[32, cy + 1] = gate
            c.rect(37, cy, 40, cy + 3, dirt); c[36, cy + 1] = gate
            c[32, cy] = wall; c[32, cy + 2] = wall; c[32, cy + 3] = wall
            c[36, cy] = wall; c[36, cy + 2] = wall; c[36, cy + 3] = wall
        }
        c.rect(33, 51, 35, 71, floor)

        // Great hall: pillar rows and long benches
        for (y in 26..68 step 4) { c[45, y] = wall; c[53, y] = wall }
        for (y in 30..64 step 6) for (x in 47..51) c[x, y] = TileType.TEMPLE_PEW
        for (x in 48..50) c[x, 24] = TileType.TEMPLE_ALTAR
        c[48, 23] = floor; c[49, 23] = floor; c[50, 23] = floor

        // Armory (NE) and treasury vaults (SE)
        for (x in listOf(60, 61, 67, 68)) c[x, 46] = floor
        for (y in 25..43 step 4) for (x in listOf(58, 59, 62, 63, 65, 66, 69, 70)) c[x, y] = TileType.WEAPON_RACK
        for (x in 63..65) c[x, 50] = floor
        c.rect(57, 51, 71, 71, floor)
        c.outline(59, 56, 69, 70, wall); for (x in 63..65) c[x, 56] = gate
        c.outline(61, 61, 67, 69, wall); for (x in 63..65) c[x, 61] = gate
        c.rect(62, 62, 66, 68, cobble)

        // Sealed Vault approach (north, beyond the moat)
        c.rect(48, 9, 50, 13, floor)
        c.rect(38, 2, 66, 8, floor)
        for (x in 41..63 step 4) { c[x, 3] = TileType.CRYSTAL_PILLAR; c[x, 7] = TileType.CRYSTAL_PILLAR }
        for (x in 48..50) c[x, 8] = gate
        c[a.descentX, a.descentY] = TileType.STAIRS_DOWN
        c[a.descentReturnX, a.descentReturnY] = floor

        // Crag caves outside the walls (SW hideout, east hollows) with a back route to the vault
        c.blob(12, 70, 5, dirt)
        c.blob(10, 86, 5, dirt)
        c.tunnel(listOf(8 to 55, 12 to 70, 19 to 76), 2, dirt)
        c.tunnel(listOf(12 to 70, 10 to 86, 30 to 88, 48 to 81), 2, dirt)
        c.blob(88, 62, 5, dirt)
        c.blob(88, 34, 5, dirt)
        c.blob(84, 12, 4, dirt)
        c.tunnel(listOf(80 to 62, 88 to 62, 88 to 34, 84 to 12, 67 to 6), 2, dirt)
        c.tunnel(listOf(80 to 30, 88 to 34), 2, dirt)

        c.sealPockets(a.landingX, a.landingY)
        c.ores(TileType.ORE_BOG_IRON, count = 7, x1 = 2, y1 = 2, x2 = 93, y2 = 93)
        c.ores(TileType.ORE_TIN, count = 6, x1 = 2, y1 = 2, x2 = 93, y2 = 93)
        c.torches(spacing = 7)

        val sentinel = MonsterType.VAULT_SENTINEL
        val xbow = MonsterType.HOLLOW_CROSSBOWMAN
        val bandit = MonsterType.OUTLAW_BANDIT
        val spawns = c.spawns(
            -14, camp,
            bandit to (20 to 30), bandit to (20 to 66), xbow to (34 to 16), bandit to (64 to 16),
            xbow to (78 to 40), bandit to (78 to 70), xbow to (40 to 78), bandit to (60 to 78),
            bandit to (31 to 30), bandit to (38 to 38), xbow to (35 to 25),
            xbow to (34 to 56), bandit to (34 to 66), xbow to (39 to 62),
            sentinel to (49 to 35), sentinel to (49 to 58), xbow to (44 to 48),
            xbow to (64 to 30), bandit to (64 to 40), sentinel to (60 to 53), sentinel to (64 to 65),
            sentinel to (44 to 5), sentinel to (60 to 5),
            bandit to (12 to 70), bandit to (10 to 86), xbow to (88 to 62), sentinel to (88 to 34)
        )
        val landmarks = listOf(
            LandmarkLabel(-14, 49, 16, "OUTER BAILEY"),
            LandmarkLabel(-14, 34, 35, "BARRACKS"),
            LandmarkLabel(-14, 34, 60, "PRISON BLOCK"),
            LandmarkLabel(-14, 49, 45, "GREAT HALL"),
            LandmarkLabel(-14, 64, 35, "ARMORY"),
            LandmarkLabel(-14, 64, 64, "TREASURY VAULTS"),
            LandmarkLabel(-14, 88, 48, "CRAG CAVES"),
            LandmarkLabel(-14, 52, 6, "THE SEALED VAULT")
        )
        return layout(-14, c, camp, a, spawns, landmarks)
    }
}
