package com.example

import com.example.game.engine.GameEngine
import com.example.game.model.FRONTIER_SITE_REBUILD_MAP_VERSION
import com.example.game.model.CraftingStationType
import com.example.game.model.ItemCatalog
import com.example.game.model.MonsterLootTable
import com.example.game.model.MonsterType
import com.example.game.model.OreVeinKind
import com.example.game.model.RecipeCatalog
import com.example.game.model.TileType
import com.example.game.world.BiomeCities
import com.example.game.world.BiomeProgressionRules
import com.example.game.world.FrontierSiteFloors
import com.example.game.world.WorldBiomeBlueprint
import com.example.game.world.WorldMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontierCitiesAndDungeonsTest {

    private fun reachable(floorId: Int, sx: Int, sy: Int, box: IntArray? = null): Set<Pair<Int, Int>> {
        val fl = WorldMaps.getFloor(floorId)
        val seen = HashSet<Pair<Int, Int>>()
        val q = ArrayDeque<Pair<Int, Int>>()
        seen += sx to sy; q.add(sx to sy)
        while (q.isNotEmpty()) {
            val (x, y) = q.removeFirst()
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nx = x + dx; val ny = y + dy
                if (box != null && (nx !in box[0]..box[2] || ny !in box[1]..box[3])) continue
                val n = nx to ny
                if (nx in 0 until fl.width && ny in 0 until fl.height && n !in seen && fl.tileAt(nx, ny).walkable) {
                    seen += n; q.add(n)
                }
            }
        }
        return seen
    }

    @Test
    fun mirewatchAndSunspireAreBiomeUniqueWalkableSafeCities() {
        val f0 = WorldMaps.getFloor(0)
        fun tilesIn(city: BiomeCities.City): Set<TileType> = buildSet {
            for (y in city.minY..city.maxY) for (x in city.minX..city.maxX) add(f0.tileAt(x, y))
        }
        val mire = tilesIn(BiomeCities.mirewatch)
        val sun = tilesIn(BiomeCities.sunspire)
        // Mirewatch: stilt decks over a lagoon, rope bridges, bog — and no sandstone
        assertTrue(TileType.WOOD_PIER in mire && TileType.WOOD_BRIDGE in mire && TileType.MARSH in mire && TileType.WATER in mire)
        assertFalse(TileType.SANDSTONE_WALL in mire)
        // Sunspire: sandstone walls, oasis water, bazaar awnings, palms — and no bog
        assertTrue(TileType.SANDSTONE_WALL in sun && TileType.WATER in sun && TileType.MARKET_AWNING in sun && TileType.TREE in sun)
        assertFalse(TileType.MARSH in sun)

        // Both cities have a depot, a temple altar and crafting stations
        for (set in listOf(mire, sun)) {
            assertTrue(TileType.MINI_DEPOT in set && TileType.TEMPLE_ALTAR in set && TileType.BLACKSMITH_ANVIL in set)
        }

        // Every city NPC stands on walkable ground inside the PZ and can be reached from the highway
        val mireBox = intArrayOf(BiomeCities.MIRE_MIN_X - 4, BiomeCities.MIRE_MIN_Y - 4, BiomeCities.MIRE_MAX_X + 4, BiomeCities.MIRE_MAX_Y + 4)
        val sunBox = intArrayOf(BiomeCities.SUN_MIN_X - 6, BiomeCities.SUN_MIN_Y - 10, BiomeCities.SUN_MAX_X + 6, BiomeCities.SUN_MAX_Y + 6)
        val mireReach = reachable(0, BiomeCities.MIRE_MIN_X - 2, 590, mireBox)
        val sunReach = reachable(0, 375, BiomeCities.SUN_MIN_Y - 6, sunBox)
        for ((city, reach) in listOf(BiomeCities.mirewatch to mireReach, BiomeCities.sunspire to sunReach)) {
            val npcs = WorldMaps.npcs.filter { it.floor == 0 && city.contains(it.x, it.y) }
            assertTrue("${city.id} keeps its townsfolk", npcs.size >= 2)
            npcs.forEach { n ->
                assertTrue("${n.name} must stand on walkable ground", f0.tileAt(n.x, n.y).walkable)
                assertTrue("${n.name} must be in the PZ", f0.isProtectionZone(n.x, n.y))
                assertTrue("${n.name} must be reachable from the road", listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dy) -> (n.x + dx to n.y + dy) in reach })
            }
        }
        // All four Sunspire gates connect to the plaza
        for ((gx, gy) in listOf(375 to BiomeCities.SUN_MIN_Y, 355 to BiomeCities.SUN_MAX_Y, 392 to BiomeCities.SUN_MAX_Y, BiomeCities.SUN_MAX_X to 821)) {
            assertTrue("Sunspire gate ($gx,$gy) must be open", (gx to gy) in sunReach)
        }
        // Biome crafting stalls still sit where the forge pins point
        WorldBiomeBlueprint.biomeCraftingStalls.forEach { assertEquals(it.station.stallTileType(), f0.tileAt(it.x, it.y)) }
    }

    @Test
    fun rebuiltAndNewFrontierDungeonsAreLargeConnectedAndPopulated() {
        val monsters = WorldMaps.initialMonsters()
        for (floorId in listOf(-6, -7, -8, -29, -30, -31)) {
            val fl = WorldMaps.getFloor(floorId)
            assertEquals(FrontierSiteFloors.SIZE, fl.width)
            val entry = WorldMaps.portals.single { it.fromFloor == 0 && it.toFloor == floorId }
            val exit = WorldMaps.portals.single { it.fromFloor == floorId && it.toFloor == 0 }
            assertTrue(f0Tile(entry.fromX, entry.fromY) in setOf(TileType.STAIRS_DOWN, TileType.ROPE_HOLE_DOWN))
            val seen = reachable(floorId, entry.toX, entry.toY)
            var walkable = 0
            for (y in 0 until fl.height) for (x in 0 until fl.width) if (fl.tileAt(x, y).walkable) walkable++
            assertEquals("Floor $floorId has walkable tiles you can't reach", walkable, seen.size)
            assertTrue((exit.fromX to exit.fromY) in seen)
            assertTrue(fl.isProtectionZone(entry.toX, entry.toY))
            WorldMaps.portals.filter { it.fromFloor == floorId && it.toFloor in -28..-16 }
                .forEach { down -> assertTrue("Descent on $floorId must be reachable", (down.fromX to down.fromY) in seen) }

            val here = monsters.filter { it.floor == floorId }
            assertTrue("Floor $floorId has only ${here.size} monsters", here.size >= 20)
            here.forEach { m ->
                assertTrue((m.x to m.y) in seen)
                assertFalse(fl.isProtectionZone(m.x, m.y))
            }
        }
    }

    @Test
    fun newDungeonsHostTheirOwnBiomeMonstersBossesAndLevelBands() {
        val monsters = WorldMaps.initialMonsters()
        val murk = monsters.filter { it.floor == FrontierSiteFloors.MURKFEN_FLOOR }
        val tomb = monsters.filter { it.floor == FrontierSiteFloors.TOMB_FLOOR }
        assertEquals(listOf(MonsterType.HAGMOTHER_VESSA), murk.filter { it.type.isBoss }.map { it.type })
        assertEquals(listOf(MonsterType.AKHET_THE_SAND_PHARAOH), tomb.filter { it.type.isBoss }.map { it.type })
        assertEquals(setOf(MonsterType.BOG_HAG, MonsterType.GATOR_BRUTE, MonsterType.FEN_WISP), murk.filter { !it.type.isBoss }.map { it.type }.toSet())
        assertEquals(setOf(MonsterType.DUNE_SCORPION, MonsterType.SAND_WRAITH, MonsterType.SUNSCALE_BASILISK), tomb.filter { !it.type.isBoss }.map { it.type }.toSet())
        murk.forEach { assertTrue("Murkfen ${it.type} Lv ${it.level}", it.level in 20..25) }
        tomb.forEach { assertTrue("Tomb ${it.type} Lv ${it.level}", it.level in 30..35) }
        // Gorek Labyrinth now matches its badlands home (Lv 36-41) instead of Lv 90+
        monsters.filter { it.floor == -6 }.forEach { assertTrue("Gorek ${it.type} Lv ${it.level}", it.level in 36..41) }
        assertEquals("Lvl 36-41", BiomeProgressionRules.dungeonRecommendedLevelLabel(-6))
        // Scorchglass Rift tops off the badlands band above Gorek
        val rift = monsters.filter { it.floor == FrontierSiteFloors.RIFT_FLOOR }
        assertEquals(listOf(MonsterType.SIROCCO_THE_STORM_DJINN), rift.filter { it.type.isBoss }.map { it.type })
        rift.forEach { assertTrue("Rift ${it.type} Lv ${it.level}", it.level in 41..46) }
        assertEquals(-31, BiomeProgressionRules.MIN_DUNGEON_FLOOR)
        assertFalse(BiomeProgressionRules.isLowerBossFloor(-29))
    }

    @Test
    fun oldSavesInsideRebuiltFrontierFloorsResumeAtTheCampLanding() {
        for (floorId in listOf(-6, -7, -8)) {
            val hero = GameEngine.createNewWorld("Delver").copy(floor = floorId, playerX = 24, playerY = 40)
            val oldSave = GameEngine.toSaveData(hero).copy(mapVersion = FRONTIER_SITE_REBUILD_MAP_VERSION - 1)
            val loaded = GameEngine.fromSaveData(oldSave)
            assertEquals(floorId, loaded.floor)
            assertEquals(WorldMaps.fallbackLandingForFloor(floorId), loaded.playerX to loaded.playerY)
        }
        // A save on a new floor survives the round trip
        val deep = GameEngine.createNewWorld("Delver").copy(floor = -30, playerX = 48, playerY = 89)
        val back = GameEngine.fromSaveData(GameEngine.toSaveData(deep))
        assertEquals(-30, back.floor)
    }

    /** Marsh (Lv 20-32) and badlands (Lv 30-45) dungeon ladders leave no level gaps. */
    @Test
    fun marshAndBadlandsDungeonLaddersCoverTheirWholeBands() {
        val monsters = WorldMaps.initialMonsters()
        fun band(floors: List<Int>) = floors.flatMap { f -> monsters.filter { it.floor == f }.map { it.level } }.toSet()
        val marsh = band(listOf(-29, -7, -24, -8, -25))
        val badlands = band(listOf(-30, -6, -23, -31))
        assertTrue("Marsh dungeons must cover Lv 20-32: $marsh", (20..32).all { it in marsh })
        assertTrue("Badlands dungeons must cover Lv 30-45: $badlands", (30..45).all { it in badlands })
    }

    /**
     * Gear crafted at Mirewatch and Sunspire must be makeable from materials that drop or can be
     * mined inside (or below) that biome's level band, and dungeon loot in these two biomes must not
     * hand out gear far above the level of the monster dropping it.
     */
    @Test
    fun marshAndBadlandsGearUsesLocalMaterialsAndFairDrops() {
        val monsters = WorldMaps.initialMonsters()
        val minLevel = monsters.groupBy { it.type }.mapValues { (_, l) -> l.minOf { it.level } }
        fun obtainable(itemId: String, maxLevel: Int, depth: Int = 0): Boolean {
            if (depth > 4) return false
            if (OreVeinKind.entries.any { it.oreItemId == itemId && it.requiredMiningLevel <= maxLevel }) return true
            if ((ItemCatalog.all[itemId]?.buyPrice ?: 0) > 0) return true
            if (itemId == "wood_log") return true
            if (MonsterLootTable.entries.any { (t, e) ->
                    (minLevel[t] ?: Int.MAX_VALUE) <= maxLevel &&
                        (e.guaranteedDrops.any { it.itemId == itemId } || e.weightedDrops.any { it.itemId == itemId })
                }) return true
            return RecipeCatalog.smeltingRecipes.filter { it.outputItemId == itemId }
                .any { r -> r.ingredients.all { obtainable(it.itemId, maxLevel, depth + 1) } }
        }
        val stations = mapOf(
            CraftingStationType.STILT_TOWN_MIRE_BENCH to 32,
            CraftingStationType.SUNSCORCH_OASIS_SMELTERY to 45
        )
        val recipes = RecipeCatalog.smeltingRecipes + RecipeCatalog.forgingRecipes + RecipeCatalog.fletchingRecipes + RecipeCatalog.runecraftingRecipes
        for ((station, cap) in stations) {
            recipes.filter { it.requiredBiomeStation == station }.forEach { r ->
                r.ingredients.forEach { ing ->
                    assertTrue("${r.id} needs ${ing.itemId}, which isn't obtainable by Lv $cap", obtainable(ing.itemId, cap))
                }
            }
        }
        // Loot sanity in the marsh & badlands dungeons and their lairs
        for (floorId in listOf(-6, -7, -8, -23, -24, -25, -29, -30, -31)) {
            monsters.filter { it.floor == floorId }.map { it.type to it.level }.distinct().forEach { (type, level) ->
                MonsterLootTable.entryFor(type).weightedDrops.forEach { d ->
                    val req = ItemCatalog.all[d.itemId]?.requiredLevel ?: 1
                    assertTrue("${type.name} (Lv $level) drops ${d.itemId} needing Lv $req", req <= level + 5)
                }
            }
        }
    }

    /** No weapon out-damages the top (Lv 45) tier of its class, so lower-level gear never wins. */
    @Test
    fun topTierWeaponsHaveTheBestDamagePerSecond() {
        val weapons = ItemCatalog.all.values.filter { !it.adminOnly && it.category.name.startsWith("WEAPON_") && it.attack > 0 }
        for ((cat, list) in weapons.groupBy { it.category }) {
            val dps = list.associateWith { it.attack * 1000.0 / it.attackIntervalMs }
            val best = dps.maxByOrNull { it.value }!!.key
            val topLevel = list.maxOf { it.requiredLevel }
            assertEquals("$cat best DPS is ${best.id} (Lv ${best.requiredLevel})", topLevel, best.requiredLevel)
        }
    }

    @Test
    fun sunspireSurroundingsAreBadlands() {
        assertEquals(com.example.game.world.OverlandBiomeTier.SUNSCORCH_BADLANDS, BiomeProgressionRules.biomeForGlobalTile(375, 790))
        assertEquals(com.example.game.world.OverlandBiomeTier.SUNSCORCH_BADLANDS, BiomeProgressionRules.biomeForGlobalTile(375, 730))
        // Mirewatch stays marsh
        assertEquals(com.example.game.world.OverlandBiomeTier.WHISPERING_MARSH, BiomeProgressionRules.biomeForGlobalTile(545, 595))
        val near = WorldMaps.initialMonsters().filter { it.floor == 0 && BiomeCities.isSunspireBadlandsRim(it.x, it.y) }
        assertTrue("Sunspire's surroundings should host monsters", near.isNotEmpty())
        near.forEach { assertTrue("${it.type} near Sunspire is Lv ${it.level}", it.level in 30..45) }
    }

    @Test
    fun helmetsLegsAndBootsProgressThroughEveryBiomeTier() {
        val recipes = RecipeCatalog.forgingRecipes
        for (cat in listOf(com.example.game.model.ItemCategory.HELMET, com.example.game.model.ItemCategory.LEGS, com.example.game.model.ItemCategory.BOOTS)) {
            val pieces = ItemCatalog.all.values.filter { it.category == cat && !it.adminOnly }
            for (lvl in listOf(20, 28, 36, 45)) {
                val p = pieces.singleOrNull { it.requiredLevel == lvl }
                assertTrue("$cat missing a Lv $lvl piece", p != null)
                assertTrue("${p!!.id} must be craftable", recipes.any { it.outputItemId == p.id })
            }
            // Armor never goes down as the required level goes up
            val sorted = pieces.sortedBy { it.requiredLevel }
            sorted.zipWithNext().forEach { (a, b) -> assertTrue("${b.id} (${b.armor}) weaker than ${a.id} (${a.armor})", b.armor >= a.armor) }
        }
    }

    @Test
    fun highLevelMonstersSalvageOutgrownStarterGear() {
        val rng = kotlin.random.Random(7)
        val starter = setOf("sunbrass_dirk", "rookvale_gladius", "wooden_shield", "viking_helmet")
        var ore = 0
        repeat(4000) {
            val loot = MonsterLootTable.rollLoot(MonsterType.SKELETON, rng, monsterLevel = 33)
            assertTrue("Lv 33 skeleton dropped starter gear: ${loot.items}", loot.items.none { it.itemId in starter })
            ore += loot.items.filter { it.itemId == "vesperite_ore" }.sumOf { it.count }
        }
        assertTrue("Outgrown gear should salvage into badlands ore", ore > 0)
        // Low-level skeletons still drop their starter gear
        val low = (1..4000).flatMap { MonsterLootTable.rollLoot(MonsterType.SKELETON, rng, monsterLevel = 6).items.map { it.itemId } }
        assertTrue(low.any { it in starter })
    }

    private fun f0Tile(x: Int, y: Int) = WorldMaps.getFloor(0).tileAt(x, y)
}
