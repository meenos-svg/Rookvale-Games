package com.example.game.world

import com.example.game.model.TileType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Hand-built overland cities that replace the small generic settlement boxes:
 *
 * - **Mirewatch** (Whispering Marsh, 545,595): a stilt city with no walls. Plank districts stand
 *   on piles over a black-water lagoon, joined by rope bridges, wrapped in a reed bog full of dead
 *   mangroves. A moot platform with the great peat fire sits in the middle, the Shrine of the
 *   Drowned Saint to the south, a lantern market north, fishers' wharf NE, crafters' stilts SE,
 *   stilt-hut hamlet NW and herb fens SW.
 * - **Sunspire** (Sunscorch Badlands, 375,790): a walled sandstone city. Thick walls with corner
 *   towers and four gates, a palm-ringed oasis lake, the Sun Plaza with its fountain and obelisks,
 *   a covered grand bazaar, the Temple of the Noon Sun, a caravanserai with camel pens and an
 *   artisans' row of flat-roofed workshops.
 *
 * Each city is a fixed tile grid covering its whole settlement box. `null` cells fall back to
 * the natural terrain (roads, sand, grass).
 */
object BiomeCities {

    class City(
        val id: String,
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
        private val cells: Array<Array<TileType?>>
    ) {
        fun contains(x: Int, y: Int): Boolean = x in minX..maxX && y in minY..maxY
        fun tileAt(x: Int, y: Int): TileType? = cells[y - minY][x - minX]
    }

    // Footprints (also used for the settlement boxes, PZ and spawn exclusion)
    const val MIRE_MIN_X = 476
    const val MIRE_MIN_Y = 536
    const val MIRE_MAX_X = 614
    const val MIRE_MAX_Y = 654

    const val SUN_MIN_X = 330
    const val SUN_MIN_Y = 748
    const val SUN_MAX_X = 420
    const val SUN_MAX_Y = 832

    val mirewatch: City by lazy { buildMirewatch() }
    val sunspire: City by lazy { buildSunspire() }

    fun cityAt(x: Int, y: Int): City? = when {
        x in MIRE_MIN_X..MIRE_MAX_X && y in MIRE_MIN_Y..MIRE_MAX_Y -> mirewatch
        x in SUN_MIN_X..SUN_MAX_X && y in SUN_MIN_Y..SUN_MAX_Y -> sunspire
        else -> null
    }

    private class Canvas(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
        val g: Array<Array<TileType?>> = Array(maxY - minY + 1) { arrayOfNulls<TileType>(maxX - minX + 1) }
        fun inside(x: Int, y: Int) = x in minX..maxX && y in minY..maxY
        operator fun set(x: Int, y: Int, t: TileType?) { if (inside(x, y)) g[y - minY][x - minX] = t }
        operator fun get(x: Int, y: Int): TileType? = if (inside(x, y)) g[y - minY][x - minX] else null
        fun rect(x1: Int, y1: Int, x2: Int, y2: Int, t: TileType?) {
            for (y in y1..y2) for (x in x1..x2) this[x, y] = t
        }
        fun outline(x1: Int, y1: Int, x2: Int, y2: Int, t: TileType) {
            for (x in x1..x2) { this[x, y1] = t; this[x, y2] = t }
            for (y in y1..y2) { this[x1, y] = t; this[x2, y] = t }
        }
        fun ellipse(cx: Int, cy: Int, rx: Float, ry: Float, t: TileType, only: ((TileType?) -> Boolean)? = null) {
            for (y in (cy - ry.toInt() - 1)..(cy + ry.toInt() + 1)) for (x in (cx - rx.toInt() - 1)..(cx + rx.toInt() + 1)) {
                val dx = (x - cx) / rx; val dy = (y - cy) / ry
                if (dx * dx + dy * dy <= 1f && (only == null || only(this[x, y]))) this[x, y] = t
            }
        }
        /** Thick straight segment from (x1,y1) to (x2,y2). */
        fun line(x1: Int, y1: Int, x2: Int, y2: Int, width: Int, t: TileType) {
            val steps = max(abs(x2 - x1), abs(y2 - y1)).coerceAtLeast(1)
            val half = width / 2
            for (i in 0..steps) {
                val x = x1 + (x2 - x1) * i / steps
                val y = y1 + (y2 - y1) * i / steps
                for (oy in -half until width - half) for (ox in -half until width - half) this[x + ox, y + oy] = t
            }
        }
        fun build(id: String) = City(id, minX, minY, maxX, maxY, g)
    }

    private fun noise(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7fffffff
    }

    /** Small blocky hut: walls all round, plank floor, a door gap on [doorSide] (N/S/E/W). */
    private fun Canvas.hut(x1: Int, y1: Int, x2: Int, y2: Int, wall: TileType, floor: TileType, doorSide: Char, bed: Boolean = false) {
        rect(x1, y1, x2, y2, floor)
        outline(x1, y1, x2, y2, wall)
        val mx = (x1 + x2) / 2; val my = (y1 + y2) / 2
        when (doorSide) {
            'N' -> this[mx, y1] = floor
            'S' -> this[mx, y2] = floor
            'E' -> this[x2, my] = floor
            else -> this[x1, my] = floor
        }
        if (bed) this[x1 + 1, y1 + 1] = TileType.WOOD_BED
    }

    // =====================================================================================
    // MIREWATCH — stilt city of the Whispering Marsh
    // =====================================================================================
    const val MIRE_CX = 545
    const val MIRE_CY = 595

    private fun buildMirewatch(): City {
        val c = Canvas(MIRE_MIN_X, MIRE_MIN_Y, MIRE_MAX_X, MIRE_MAX_Y)
        val cx = MIRE_CX; val cy = MIRE_CY
        val plank = TileType.WOOD_PLANK_FLOOR
        val pier = TileType.WOOD_PIER
        val bridge = TileType.WOOD_BRIDGE
        val water = TileType.WATER

        // 1. Reed bog skirt with a ragged edge that bleeds into the surrounding grassland
        for (y in c.minY..c.maxY) for (x in c.minX..c.maxX) {
            val dx = (x - cx) / 68f; val dy = (y - cy) / 58f
            val d = sqrt(dx * dx + dy * dy) + ((noise(x / 3, y / 3, 11) % 100) - 50) / 400f
            if (d > 1f) continue
            val n = noise(x, y, 7) % 100
            val pool = noise(x / 4, y / 4, 23) % 100 < 22
            c[x, y] = when {
                pool && d < 0.95f -> water
                n < 5 -> TileType.TREE                       // dead mangroves
                n < 18 -> TileType.DARK_GRASS                // tussock hummocks
                else -> TileType.MARSH
            }
        }

        // 2. Black-water lagoon the stilt districts stand in
        c.ellipse(cx, cy, 54f, 46f, water)
        // little mangrove islets dotted across the lagoon
        listOf(522 to 588, 568 to 588, 545 to 551, 545 to 640, 498 to 596, 592 to 596, 520 to 640, 572 to 552)
            .forEachIndexed { i, (ix, iy) ->
                c.ellipse(ix, iy, 2.6f, 2.0f, TileType.MARSH)
                c[ix, iy] = TileType.TREE
                if (i % 2 == 0) c[ix + 1, iy + 1] = TileType.DARK_GRASS
            }

        // 3. Stilt districts: plank decks edged with pier boards
        fun deck(x1: Int, y1: Int, x2: Int, y2: Int, top: TileType = plank) {
            c.rect(x1, y1, x2, y2, pier)
            c.rect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, top)
        }
        // Central Moot Platform
        deck(531, 582, 559, 608)
        c.ellipse(cx, cy, 6.5f, 5.5f, TileType.COBBLESTONE)          // fire-stone ring
        c[cx, cy] = TileType.CAMPFIRE_BRAZIER                        // the great peat fire
        for ((x, y) in listOf(533 to 584, 557 to 584, 533 to 606, 557 to 606)) c[x, y] = TileType.LANTERN_POST
        c[537, 586] = TileType.MINI_DEPOT                            // stilt depot
        c[538, 586] = TileType.CRATE_BARREL
        c[553, 604] = TileType.BIOME_STALL_MARSH
        c[554, 604] = TileType.CRATE_BARREL
        c[536, 604] = TileType.WAR_BANNER; c[554, 586] = TileType.WAR_BANNER

        // North Lantern Market
        deck(533, 558, 557, 577)
        for (x in listOf(537, 542, 548, 553)) {
            c[x, 562] = TileType.BIOME_STALL_MARSH; c[x + 1, 562] = TileType.MARKET_AWNING
            c[x, 572] = TileType.MARKET_AWNING; c[x + 1, 572] = TileType.CRATE_BARREL
        }
        for (x in 536..554 step 6) c[x, 567] = TileType.LANTERN_POST

        // NW Stilt-Hut Hamlet
        deck(497, 554, 527, 580)
        listOf(
            Triple(500, 557, 'S'), Triple(507, 557, 'S'), Triple(514, 557, 'S'), Triple(521, 557, 'S'),
            Triple(500, 573, 'N'), Triple(507, 573, 'N'), Triple(514, 573, 'N')
        ).forEach { (hx, hy, door) -> c.hut(hx, hy, hx + 4, hy + 4, TileType.TIMBER_WALL, plank, door, bed = true) }
        c[523, 575] = TileType.CAMPFIRE_BRAZIER
        c[520, 566] = TileType.LANTERN_POST; c[504, 566] = TileType.LANTERN_POST

        // NE Fishers' Wharf with piers jutting into the lagoon and moored punts
        deck(563, 555, 595, 580)
        for (px in listOf(568, 576, 584, 592)) {
            c.rect(px, 548, px + 1, 554, pier)
            c[px + 2, 550] = TileType.MOORED_BOAT
            c[px - 1, 552] = TileType.MOORED_BOAT
        }
        c.hut(566, 558, 573, 563, TileType.TIMBER_WINDOW_WALL, plank, 'S')      // net loft
        c.hut(584, 558, 591, 563, TileType.TIMBER_WALL, plank, 'S')             // smokehouse
        c[587, 560] = TileType.SMELTING_FURNACE
        for (x in 567..591 step 4) c[x, 576] = TileType.CRATE_BARREL
        c[579, 568] = TileType.CAMPFIRE_BRAZIER
        c[575, 568] = TileType.LANTERN_POST; c[583, 568] = TileType.LANTERN_POST

        // SW Herb Fens: bog gardens on a low deck
        deck(497, 604, 527, 632, top = TileType.MARSH)
        for ((gx, gy) in listOf(500 to 607, 509 to 607, 518 to 607, 500 to 619, 509 to 619, 518 to 619)) {
            c.rect(gx, gy, gx + 6, gy + 8, TileType.FLOWER_GRASS)
            c.outline(gx, gy, gx + 6, gy + 8, TileType.HEDGE)
            c[gx + 3, gy] = TileType.MARSH; c[gx + 3, gy + 8] = TileType.MARSH
        }
        c.rect(497, 616, 527, 618, plank)                                      // boardwalk through the fens
        c.rect(511, 604, 514, 632, plank)
        c[525, 617] = TileType.RUNECRAFTING_TABLE

        // SE Crafters' Stilts
        deck(563, 610, 595, 634)
        c.hut(566, 613, 575, 619, TileType.TIMBER_WALL, plank, 'S')            // forge shed
        c[568, 615] = TileType.BLACKSMITH_ANVIL; c[572, 615] = TileType.SMELTING_FURNACE
        c.hut(583, 613, 592, 619, TileType.TIMBER_WINDOW_WALL, plank, 'S')     // bowyer's loft
        c[586, 615] = TileType.FLETCHING_BENCH; c[589, 615] = TileType.WEAPON_RACK
        c.hut(566, 625, 575, 631, TileType.TIMBER_WALL, plank, 'N')            // tannery
        c[570, 629] = TileType.CRATE_BARREL
        c[586, 628] = TileType.CAMPFIRE_BRAZIER
        c[579, 622] = TileType.LANTERN_POST

        // South Shrine of the Drowned Saint
        deck(533, 614, 557, 638, top = TileType.TEMPLE_FLOOR)
        c.outline(536, 620, 554, 636, TileType.TIMBER_WALL)
        for (x in 543..547) c[x, 620] = TileType.TEMPLE_FLOOR                 // wide shrine doors
        c[545, 633] = TileType.TEMPLE_ALTAR
        c[541, 633] = TileType.CRYSTAL_PILLAR; c[549, 633] = TileType.CRYSTAL_PILLAR
        for (py in listOf(624, 627, 630)) for (px in listOf(539, 540, 541, 549, 550, 551)) c[px, py] = TileType.TEMPLE_PEW
        c[535, 616] = TileType.LANTERN_POST; c[555, 616] = TileType.LANTERN_POST

        // 4. Bridges between the districts
        c.rect(543, 578, 547, 581, bridge)        // market <-> moot
        c.rect(543, 609, 547, 613, bridge)        // moot <-> shrine
        c.rect(528, 569, 532, 571, bridge)        // hamlet <-> market
        c.rect(558, 569, 562, 571, bridge)        // market <-> wharf
        c.rect(528, 624, 532, 626, bridge)        // fens <-> shrine
        c.rect(558, 624, 562, 626, bridge)        // shrine <-> crafters
        c.rect(511, 581, 514, 588, bridge)        // hamlet <-> west causeway
        c.rect(511, 593, 514, 603, bridge)        // fens <-> west causeway
        c.rect(577, 581, 580, 588, bridge)        // wharf <-> east causeway
        c.rect(577, 593, 580, 609, bridge)        // crafters <-> east causeway
        // Causeways carrying the west & east highways into the moot platform
        c.rect(c.minX, 589, 530, 592, bridge)
        c.rect(560, 589, c.maxX, 592, bridge)
        for (x in listOf(492, 506, 520, 568, 584, 598)) { c[x, 588] = TileType.LANTERN_POST; c[x, 593] = TileType.LANTERN_POST }
        // NE boardwalk toward the harbor captain at Mirewatch Bay
        c.line(594, 557, 612, 541, 3, pier)

        return c.build("mirewatch")
    }

    // =====================================================================================
    // SUNSPIRE — walled oasis city of the Sunscorch Badlands
    // =====================================================================================
    const val SUN_CX = 375
    const val SUN_CY = 790

    private fun buildSunspire(): City {
        val c = Canvas(SUN_MIN_X, SUN_MIN_Y, SUN_MAX_X, SUN_MAX_Y)
        val cx = SUN_CX; val cy = SUN_CY
        val wall = TileType.SANDSTONE_WALL
        val sand = TileType.SAND
        val street = TileType.COBBLESTONE
        val paving = TileType.TEMPLE_FLOOR

        // 1. Sand ground inside the walls
        c.rect(c.minX, c.minY, c.maxX, c.maxY, sand)

        // 2. Thick city walls with towers and four gates
        c.outline(c.minX, c.minY, c.maxX, c.maxY, wall)
        c.outline(c.minX + 1, c.minY + 1, c.maxX - 1, c.maxY - 1, wall)
        fun tower(tx: Int, ty: Int) {
            c.rect(tx - 2, ty - 2, tx + 2, ty + 2, wall)
        }
        listOf(
            c.minX + 2 to c.minY + 2, c.maxX - 2 to c.minY + 2, c.minX + 2 to c.maxY - 2, c.maxX - 2 to c.maxY - 2,
            c.minX + 2 to cy, 352 to c.minY + 2, 398 to c.minY + 2
        ).forEach { (tx, ty) -> tower(tx, ty) }
        // gates (cleared to street) with flanking lanterns
        fun gateH(x1: Int, x2: Int, y: Int, inward: Int) {
            for (x in x1..x2) { c[x, y] = street; c[x, y + inward] = street }
            c[x1 - 1, y + 2 * inward] = TileType.LANTERN_POST; c[x2 + 1, y + 2 * inward] = TileType.LANTERN_POST
        }
        gateH(372, 378, c.minY, 1)          // North Gate
        gateH(351, 359, c.maxY, -1)         // Southwest Gate
        gateH(388, 396, c.maxY, -1)         // South Gate
        for (y in 817..825) { c[c.maxX, y] = street; c[c.maxX - 1, y] = street }   // East Gate
        c[c.maxX - 2, 816] = TileType.LANTERN_POST; c[c.maxX - 2, 826] = TileType.LANTERN_POST

        // 3. Main avenues
        c.line(375, c.minY, 375, 757, 7, street)                 // north gate -> oasis
        c.line(358, 757, 392, 757, 4, street)                    // oasis north promenade
        c.line(358, 757, 358, 781, 4, street)                    // west of the oasis
        c.line(392, 757, 392, 781, 4, street)                    // east of the oasis
        c.line(355, c.maxY - 1, 368, 797, 5, street)             // SW gate -> plaza
        c.line(392, c.maxY - 1, 382, 797, 5, street)             // S gate -> plaza
        c.line(c.maxX - 1, 821, 386, 795, 5, street)             // E gate -> plaza
        c.line(cx - 22, cy, cx + 22, cy, 4, street)              // east-west cross street

        // 4. The Grand Oasis: palm-ringed lake north of the plaza
        c.ellipse(cx, 769, 17f, 10f, TileType.DARK_GRASS)
        c.ellipse(cx, 769, 15f, 8.5f, TileType.FLOWER_GRASS)
        c.ellipse(cx, 769, 12f, 6.5f, TileType.WATER)
        c.ellipse(cx, 769, 6f, 3f, TileType.DEEP_WATER)
        c.rect(373, 770, 377, 777, TileType.WOOD_PIER)           // fishing jetty from the plaza side
        for (a in 0 until 20) {                                    // palms around the shore
            val ang = a * Math.PI * 2 / 20
            val px = cx + (14.5 * Math.cos(ang)).toInt(); val py = 769 + (8.3 * Math.sin(ang)).toInt()
            if (c[px, py] == TileType.FLOWER_GRASS || c[px, py] == TileType.DARK_GRASS) c[px, py] = TileType.TREE
        }

        // 5. Sun Plaza
        c.ellipse(cx, cy, 10f, 8f, paving)
        c[cx, cy] = TileType.FOUNTAIN
        for ((x, y) in listOf(367 to 785, 383 to 785, 367 to 795, 383 to 795)) c[x, y] = TileType.CRYSTAL_PILLAR  // sun obelisks
        c[369, 788] = TileType.MINI_DEPOT
        c[378, 793] = TileType.BIOME_STALL_BADLANDS
        c[379, 793] = TileType.MARKET_AWNING
        c.rect(373, 778, 377, 781, paving)                       // plaza -> jetty

        // 6. Grand Bazaar (east): covered stall rows with aisles between
        c.rect(397, 762, 414, 812, street)
        for (row in 0 until 6) {
            val sy = 765 + row * 8
            for (sx in 399..412 step 4) {
                c[sx, sy] = TileType.MARKET_AWNING
                c[sx + 1, sy] = if ((sx + row) % 3 == 0) TileType.BIOME_STALL_BADLANDS else TileType.MARKET_AWNING
                c[sx, sy + 1] = TileType.CRATE_BARREL
            }
        }
        c.rect(395, 788, 397, 792, street)                       // bazaar <-> cross street

        // 7. Caravanserai (west): fenced camel pens around a courtyard fire
        c.rect(336, 760, 352, 782, TileType.DIRT_FLOOR)
        c.outline(336, 760, 352, 782, wall)
        for (y in 769..773) c[352, y] = street                   // courtyard gate
        c[344, 771] = TileType.CAMPFIRE_BRAZIER
        c.outline(338, 762, 345, 767, TileType.WOOD_FENCE); c[341, 767] = TileType.DIRT_FLOOR
        c.outline(338, 775, 345, 780, TileType.WOOD_FENCE); c[341, 775] = TileType.DIRT_FLOOR
        c[349, 763] = TileType.CRATE_BARREL; c[350, 763] = TileType.CRATE_BARREL; c[349, 780] = TileType.CRATE_BARREL
        c[350, 771] = TileType.WAR_BANNER
        c.rect(353, 770, 356, 772, street)

        // 8. Temple of the Noon Sun (southwest)
        c.rect(334, 800, 352, 826, paving)
        c.outline(334, 800, 352, 826, wall)
        for (x in 341..345) c[x, 800] = paving                   // great doors (north)
        c[343, 822] = TileType.TEMPLE_ALTAR
        c[339, 822] = TileType.CRYSTAL_PILLAR; c[347, 822] = TileType.CRYSTAL_PILLAR
        for (py in 804..818 step 3) { c[337, py] = wall; c[349, py] = wall }   // colonnade
        for (py in listOf(808, 811, 814, 817)) for (px in listOf(339, 340, 341, 345, 346, 347)) c[px, py] = TileType.TEMPLE_PEW
        c.line(343, 797, 343, 799, 3, street)
        c.line(343, 797, 360, 797, 3, street)

        // 9. Artisans' Row (southeast workshops)
        c.hut(398, 814, 404, 820, wall, paving, 'N'); c[400, 816] = TileType.BLACKSMITH_ANVIL; c[402, 816] = TileType.SMELTING_FURNACE
        c.hut(406, 814, 412, 820, wall, paving, 'N'); c[408, 816] = TileType.FLETCHING_BENCH; c[410, 816] = TileType.WEAPON_RACK
        c.hut(398, 824, 404, 829, wall, paving, 'N'); c[401, 826] = TileType.RUNECRAFTING_TABLE
        c.rect(397, 812, 413, 813, street)

        // 10. Flat-roofed houses filling the residential quarters
        listOf(
            Triple(356, 805, 'E'), Triple(356, 812, 'E'), Triple(362, 819, 'N'),
            Triple(371, 806, 'W'), Triple(371, 813, 'W'), Triple(378, 820, 'N'),
            Triple(338, 787, 'N'), Triple(345, 787, 'N'),
            Triple(404, 750, 'S'), Triple(410, 750, 'S'), Triple(340, 750, 'S'), Triple(346, 751, 'S')
        ).forEach { (hx, hy, door) -> c.hut(hx, hy, hx + 4, hy + 4, wall, paving, door, bed = true) }
        // shaded wells
        c[363, 801] = TileType.FOUNTAIN; c[388, 805] = TileType.FOUNTAIN
        // lanterns along the avenues
        for (y in 750..756 step 6) { c[371, y] = TileType.LANTERN_POST; c[379, y] = TileType.LANTERN_POST }

        return c.build("sunspire")
    }
}
