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
 * - **Frostpeak** (Frostpeak Highlands, 1396,204): a snow-bowl city inside a ring of crags.
 * - **Ashen Bastion** (Ashen Caldera, 386,260): an obsidian forge city behind a lava moat.
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

    const val FROST_MIN_X = 1336
    const val FROST_MIN_Y = 148
    const val FROST_MAX_X = 1456
    const val FROST_MAX_Y = 252

    const val ASH_MIN_X = 330
    const val ASH_MIN_Y = 206
    const val ASH_MAX_X = 442
    const val ASH_MAX_Y = 314

    val mirewatch: City by lazy { buildMirewatch() }
    val frostpeak: City by lazy { buildFrostpeak() }
    val sunspire: City by lazy { buildSunspire() }
    val ashen: City by lazy { buildAshen() }

    /**
     * Sunspire sits just north of the coarse 400x400 desert region border (y = 800), so its sandy
     * surroundings would otherwise count as Writhing Marshes. Everything inside this ellipse around
     * the city (north of the border) is treated as Sunscorch Badlands for monsters, levels and forage.
     */
    fun isSunspireBadlandsRim(x: Int, y: Int): Boolean {
        if (y >= 800 || y < 705) return false
        val dx = (x - SUN_CX) / 95.0
        val dy = (y - 800) / 95.0
        return dx * dx + dy * dy <= 1.0
    }

    /**
     * Ashen Bastion straddles the coarse region border at x = 400, so its east half would otherwise
     * count as Frostpeak. The city and a small margin around it are Ashen Caldera.
     */
    fun isAshenBastionRim(x: Int, y: Int): Boolean =
        x in 400..ASH_MAX_X + 8 && y in ASH_MIN_Y - 8..ASH_MAX_Y + 8

    fun cityAt(x: Int, y: Int): City? = when {
        x in MIRE_MIN_X..MIRE_MAX_X && y in MIRE_MIN_Y..MIRE_MAX_Y -> mirewatch
        x in SUN_MIN_X..SUN_MAX_X && y in SUN_MIN_Y..SUN_MAX_Y -> sunspire
        x in FROST_MIN_X..FROST_MAX_X && y in FROST_MIN_Y..FROST_MAX_Y -> frostpeak
        x in ASH_MIN_X..ASH_MAX_X && y in ASH_MIN_Y..ASH_MAX_Y -> ashen
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
        c[550, 591] = TileType.BIOME_STALL_MARSH                     // Stilt-Town Mire Bench (crafting)
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
        c[380, 786] = TileType.BIOME_STALL_BADLANDS                  // Sunscorch Oasis Smeltery (crafting)
        c[381, 786] = TileType.SMELTING_FURNACE
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

    // =====================================================================================
    // FROSTPEAK — granite citadel in a snowbound mountain bowl
    // =====================================================================================
    const val FROST_CX = 1396
    const val FROST_CY = 204

    /**
     * A city cut into a snowy mountain bowl: a ring of crags with gatehouses on the north and south
     * passes, the Eternal Hearth plaza, a half-frozen lake with ice-fishing piers, steaming hot
     * springs, the Jarl's granite mead hall, a smithing terrace, timber longhouses, the Temple of the
     * Frost-Mother and a yak market by the south gate.
     */
    private fun buildFrostpeak(): City {
        val c = Canvas(FROST_MIN_X, FROST_MIN_Y, FROST_MAX_X, FROST_MAX_Y)
        val cx = FROST_CX; val cy = FROST_CY
        val snow = TileType.SNOW
        val granite = TileType.GRANITE_WALL
        val street = TileType.COBBLESTONE
        val plank = TileType.WOOD_PLANK_FLOOR

        // 1. Mountain bowl: snowfield inside a ragged ring of crags
        for (y in c.minY..c.maxY) for (x in c.minX..c.maxX) {
            val dx = (x - cx) / 60f; val dy = (y - cy) / 52f
            val d = sqrt(dx * dx + dy * dy) + ((noise(x / 3, y / 3, 41) % 100) - 50) / 600f
            c[x, y] = when {
                d <= 0.86f -> snow
                d <= 1.0f -> TileType.MOUNTAIN_ROCK
                else -> null
            }
        }
        // scattered snow pines (kept off the streets, which are drawn afterwards)
        for (y in c.minY..c.maxY) for (x in c.minX..c.maxX) {
            if (c[x, y] == snow && noise(x, y, 43) % 100 < 3) c[x, y] = TileType.TREE
        }

        // 2. Streets: north-south high street through both passes, and the east-west cross street
        c.rect(1393, c.minY, 1399, c.maxY, street)
        c.rect(1352, 202, 1413, 206, street)
        for (y in 160..244 step 12) { c[1392, y] = TileType.LANTERN_POST; c[1400, y] = TileType.LANTERN_POST }

        // gatehouses with portcullises on both passes
        fun gatehouse(y1: Int, y2: Int, gateY: Int) {
            c.rect(1387, y1, 1391, y2, granite); c.rect(1401, y1, 1405, y2, granite)
            for (x in 1393..1399) c[x, gateY] = TileType.IRON_BAR_GATE
            c[1392, gateY] = TileType.WAR_BANNER; c[1400, gateY] = TileType.WAR_BANNER
        }
        gatehouse(c.minY, c.minY + 7, c.minY + 4)
        gatehouse(c.maxY - 7, c.maxY, c.maxY - 4)

        // 3. Hearthstone Plaza with the Eternal Hearth
        c.ellipse(cx, cy, 10f, 8f, street)
        c[cx, cy] = TileType.CAMPFIRE_BRAZIER
        c[1390, 199] = TileType.MINI_DEPOT
        c[1401, 200] = TileType.BIOME_STALL_FROSTPEAK                 // Frostpeak Great Hearth (crafting)
        c[1402, 200] = TileType.SMELTING_FURNACE
        for ((x, y) in listOf(1387 to 197, 1405 to 197, 1387 to 211, 1405 to 211)) c[x, y] = TileType.LANTERN_POST

        // 4. The half-frozen lake (west) with ice-fishing piers and huts
        c.ellipse(1364, 188, 17f, 12f, snow)
        c.ellipse(1364, 188, 14f, 9f, TileType.WATER)
        c.ellipse(1364, 187, 7f, 4f, TileType.DEEP_WATER)
        for (px in listOf(1356, 1364, 1372)) {
            c.rect(px, 186, px + 1, 197, TileType.WOOD_PIER)
            c[px + 2, 188] = TileType.MOORED_BOAT
        }
        c.hut(1346, 193, 1350, 197, TileType.TIMBER_WALL, plank, 'E')
        c.hut(1378, 193, 1382, 197, TileType.TIMBER_WALL, plank, 'W')
        c.rect(1352, 198, 1376, 201, snow)

        // 5. Hot springs (north-east): steaming pools on warm paving, with a bathhouse
        c.ellipse(1424, 170, 12f, 9f, street)
        for ((px, py) in listOf(1418 to 168, 1428 to 166, 1426 to 175)) {
            c.ellipse(px, py, 3f, 2f, TileType.WATER)
            c[px + 3, py] = TileType.CAMPFIRE_BRAZIER                   // steam vent
        }
        c.hut(1430, 172, 1436, 177, TileType.TIMBER_WINDOW_WALL, plank, 'W')
        c.line(1400, 178, 1414, 172, 3, street)

        // 6. The Jarl's mead hall (east): a long granite hall at the end of the cross street
        c.rect(1414, 190, 1442, 218, plank)
        c.outline(1414, 190, 1442, 218, granite)
        for (y in 202..206) c[1414, y] = plank                           // great doors
        c[1427, 196] = TileType.CAMPFIRE_BRAZIER; c[1427, 212] = TileType.CAMPFIRE_BRAZIER
        for (px in 1419..1437 step 3) { c[px, 200] = TileType.TEMPLE_PEW; c[px, 208] = TileType.TEMPLE_PEW }
        c[1440, 204] = TileType.TEMPLE_ALTAR                             // the Jarl's high seat
        for (y in listOf(192, 216)) for (x in 1418..1438 step 5) c[x, y] = TileType.WAR_BANNER
        c[1441, 194] = TileType.WEAPON_RACK; c[1441, 214] = TileType.WEAPON_RACK

        // 7. Smithing terrace (north-west)
        c.rect(1350, 158, 1384, 176, TileType.DIRT_FLOOR)
        c.hut(1354, 160, 1364, 167, granite, TileType.DIRT_FLOOR, 'S')
        c[1357, 162] = TileType.BLACKSMITH_ANVIL; c[1361, 162] = TileType.SMELTING_FURNACE
        c.hut(1368, 160, 1377, 167, TileType.TIMBER_WALL, plank, 'S')
        c[1370, 162] = TileType.FLETCHING_BENCH; c[1374, 162] = TileType.RUNECRAFTING_TABLE
        for (x in 1354..1380 step 4) c[x, 174] = TileType.CRATE_BARREL
        c.rect(1384, 168, 1393, 170, street)

        // 8. Timber longhouses (south-west)
        for ((hx, hy) in listOf(1350 to 212, 1366 to 212, 1350 to 224, 1366 to 224)) {
            c.hut(hx, hy, hx + 11, hy + 6, TileType.TIMBER_WALL, plank, 'N', bed = true)
            c[hx + 9, hy + 2] = TileType.CAMPFIRE_BRAZIER
        }
        c.rect(1350, 209, 1392, 210, snow)
        c.rect(1350, 221, 1392, 222, snow)

        // 9. Temple of the Frost-Mother (south-east)
        c.rect(1410, 224, 1434, 244, TileType.TEMPLE_FLOOR)
        c.outline(1410, 224, 1434, 244, granite)
        for (y in 232..236) c[1410, y] = TileType.TEMPLE_FLOOR
        c[1431, 234] = TileType.TEMPLE_ALTAR
        for ((x, y) in listOf(1414 to 227, 1430 to 227, 1414 to 241, 1430 to 241, 1428 to 230, 1428 to 238)) c[x, y] = TileType.CRYSTAL_PILLAR
        for (py in listOf(229, 232, 236, 239)) for (px in listOf(1416, 1417, 1418, 1421, 1422, 1423)) c[px, py] = TileType.TEMPLE_PEW
        c.rect(1400, 233, 1409, 235, street)

        // 10. Yak market by the south gate
        for ((sx, sy) in listOf(1380 to 230, 1384 to 230, 1380 to 236, 1384 to 236)) {
            c[sx, sy] = TileType.MARKET_AWNING; c[sx + 1, sy] = TileType.MARKET_AWNING; c[sx, sy + 1] = TileType.CRATE_BARREL
        }
        c.outline(1370, 236, 1378, 244, TileType.WOOD_FENCE)
        c[1374, 236] = snow
        c[1386, 242] = TileType.BIOME_STALL_FROSTPEAK

        return c.build("frostpeak")
    }

    // =====================================================================================
    // ASHEN BASTION — obsidian forge city on a volcanic plateau
    // =====================================================================================
    const val ASH_CX = 386
    const val ASH_CY = 260

    /**
     * A walled obsidian city ringed by a lava moat, crossed by three gate bridges. Lava canals cut
     * the districts apart: the Crucible Plaza and its great forge-fire in the middle, the Great
     * Foundry to the north, the Cinder Temple east, the Ember Arena west, basalt-column gardens
     * around a lava pool to the north-west, and an ash market with obsidian houses to the south.
     */
    private fun buildAshen(): City {
        val c = Canvas(ASH_MIN_X, ASH_MIN_Y, ASH_MAX_X, ASH_MAX_Y)
        val cx = ASH_CX; val cy = ASH_CY
        val ash = TileType.VOLCANIC_ASH
        val wall = TileType.OBSIDIAN_WALL
        val floor = TileType.OBSIDIAN_FLOOR
        val street = TileType.COBBLESTONE
        val lava = TileType.LAVA
        val bridge = TileType.STONE_BRIDGE

        // 1. Plateau: lava moat outside an octagonal obsidian wall, ash ground inside
        fun octDist(x: Int, y: Int): Float {
            val dx = abs(x - cx).toFloat(); val dy = abs(y - cy).toFloat()
            return max(max(dx, dy * 1.04f), (dx + dy) * 0.74f)
        }
        for (y in c.minY..c.maxY) for (x in c.minX..c.maxX) {
            val d = octDist(x, y)
            c[x, y] = when {
                d <= 44f -> ash
                d <= 46f -> wall
                d <= 50f -> lava
                d <= 52f -> TileType.VOLCANIC_ASH
                else -> null
            }
        }
        // towers on the eight corners of the wall
        for (k in 0 until 8) {
            val ang = k * Math.PI / 4 + Math.PI / 8
            val tx = cx + (46 * Math.cos(ang)).toInt(); val ty = cy + (46 * Math.sin(ang)).toInt()
            c.rect(tx - 2, ty - 2, tx + 2, ty + 2, wall)
        }

        // 2. Gates & bridges over the moat (west, south, north-east), then the main streets
        c.rect(c.minX, 252, cx - 42, 256, bridge)                 // West Gate
        c.rect(383, cy + 42, 388, c.maxY, bridge)                 // South Gate
        c.line(cx + 24, cy - 34, cx + 42, cy - 34, 5, bridge)     // North-East Gate (meets the caldera road)
        c.rect(cx - 42, 252, cx + 6, 256, street)
        c.rect(383, cy - 6, 388, cy + 42, street)
        c.line(cx + 4, cy - 4, cx + 28, cy - 36, 5, street)
        c.rect(383, cy - 40, 388, cy - 6, street)                 // north street to the foundry
        for (x in listOf(cx - 40, cx - 28, cx - 16)) { c[x, 251] = TileType.LANTERN_POST; c[x, 257] = TileType.LANTERN_POST }
        for (y in listOf(cy + 16, cy + 28, cy + 40)) { c[382, y] = TileType.LANTERN_POST; c[389, y] = TileType.LANTERN_POST }

        // 3. Lava canals between the districts, bridged where streets cross
        c.rect(cx - 44, cy + 12, cx - 6, cy + 13, lava)
        c.rect(cx + 8, cy + 12, cx + 44, cy + 13, lava)
        c.rect(cx + 12, cy - 30, cx + 13, cy + 11, lava)
        c.rect(383, cy + 12, 388, cy + 13, bridge)
        c.rect(cx + 10, cy - 18, cx + 15, cy - 11, bridge)              // NE street over the canal

        // Quench pool (west): warm water where the smiths cool their blades, stocked with emberfin
        c.ellipse(cx - 24, cy + 4, 6f, 3f, TileType.WATER)

        // 4. Crucible Plaza with the great forge-fire
        c.ellipse(cx, cy, 9f, 7f, floor)
        c[cx, cy] = TileType.CAMPFIRE_BRAZIER
        c[391, 256] = TileType.BIOME_STALL_ASHEN                  // Ashen Obsidian Foundry (crafting)
        c[392, 256] = TileType.BLACKSMITH_ANVIL
        c[379, 256] = TileType.MINI_DEPOT
        for ((x, y) in listOf(377 to 251, 395 to 251, 377 to 269, 395 to 269)) c[x, y] = TileType.CRYSTAL_PILLAR
        c.rect(cx + 6, 258, cx + 12, 262, floor)                 // plaza -> temple walk

        // 5. The Great Foundry (north): forges around an inner lava pool
        c.rect(cx - 22, cy - 42, cx + 10, cy - 22, floor)
        c.outline(cx - 22, cy - 42, cx + 10, cy - 22, wall)
        for (x in 383..388) c[x, cy - 22] = floor
        c.ellipse(cx - 6, cy - 32, 5f, 3f, lava)
        for (x in (cx - 19)..(cx + 7) step 4) { c[x, cy - 40] = TileType.SMELTING_FURNACE; c[x, cy - 24] = TileType.BLACKSMITH_ANVIL }
        c[cx + 8, cy - 36] = TileType.WEAPON_RACK; c[cx + 8, cy - 28] = TileType.WEAPON_RACK
        c[cx - 20, cy - 36] = TileType.FLETCHING_BENCH; c[cx - 20, cy - 28] = TileType.RUNECRAFTING_TABLE

        // 6. Cinder Temple (east)
        c.rect(cx + 18, cy - 8, cx + 38, cy + 8, TileType.TEMPLE_FLOOR)
        c.outline(cx + 18, cy - 8, cx + 38, cy + 8, wall)
        for (y in (cy - 2)..(cy + 2)) c[cx + 18, y] = TileType.TEMPLE_FLOOR
        c[cx + 35, cy] = TileType.TEMPLE_ALTAR
        for ((x, y) in listOf(cx + 22 to cy - 5, cx + 34 to cy - 5, cx + 22 to cy + 5, cx + 34 to cy + 5)) c[x, y] = TileType.CAMPFIRE_BRAZIER
        for (py in listOf(cy - 3, cy + 3)) for (px in (cx + 24)..(cx + 31)) c[px, py] = TileType.TEMPLE_PEW
        c.rect(cx + 13, cy - 1, cx + 17, cy + 1, floor)

        // 7. Ember Arena (west): a sunken fighting ring with stands
        c.ellipse(cx - 26, cy - 14, 11f, 8f, street)
        c.ellipse(cx - 26, cy - 14, 8f, 5f, TileType.DIRT_FLOOR)
        for (k in 0 until 12) {
            val ang = k * Math.PI * 2 / 12
            val bx = cx - 26 + (10 * Math.cos(ang)).toInt(); val by = cy - 14 + (7 * Math.sin(ang)).toInt()
            if (by < 250) c[bx, by] = TileType.WAR_BANNER
        }

        // 8. Basalt gardens (north-west): cooled basalt columns around a lava pool
        c.ellipse(cx - 26, cy - 28, 3f, 2f, lava)
        for ((x, y) in listOf(cx - 30 to cy - 28, cx - 28 to cy - 24, cx - 24 to cy - 24, cx - 27 to cy - 31, cx - 31 to cy - 25)) c[x, y] = TileType.GRANITE_WALL

        // 9. Ash market & obsidian houses (south)
        for ((sx, sy) in listOf(372 to 278, 378 to 278, 393 to 278, 399 to 278)) {
            c[sx, sy] = TileType.MARKET_AWNING; c[sx + 1, sy] = TileType.MARKET_AWNING; c[sx, sy + 1] = TileType.CRATE_BARREL
        }
        c[396, 283] = TileType.BIOME_STALL_ASHEN
        listOf(
            Triple(356, 276, 'E'), Triple(356, 284, 'E'), Triple(366, 286, 'E'), Triple(366, 294, 'E'),
            Triple(400, 286, 'W'), Triple(400, 294, 'W'), Triple(410, 276, 'W'), Triple(410, 284, 'W')
        ).forEach { (hx, hy, door) -> c.hut(hx, hy, hx + 5, hy + 4, wall, floor, door, bed = true) }

        return c.build("ashen")
    }
}
