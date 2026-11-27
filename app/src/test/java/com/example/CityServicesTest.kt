package com.example

import com.example.game.engine.GameEngine
import com.example.game.engine.HuntBoards
import com.example.game.engine.HuntContracts
import com.example.game.engine.NpcInteractions
import com.example.game.engine.TownCrier
import com.example.game.engine.VendorCatalog
import com.example.game.model.ActivePanel
import com.example.game.model.ItemCatalog
import com.example.game.model.MonsterType
import com.example.game.model.TileType
import com.example.game.world.BiomeCities
import com.example.game.world.BiomeProgressionRules
import com.example.game.world.WorldMaps
import com.example.game.world.WorldBiomeBlueprint
import com.example.game.world.WorldPinCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CityServicesTest {

    private fun npc(name: String) = WorldMaps.npcs.single { it.name == name }

    private fun talk(name: String, level: Int = 1) = run {
        val n = npc(name)
        val hero = GameEngine.createNewWorld("Tester").copy(floor = 0, playerX = n.x + 1, playerY = n.y, level = level)
        NpcInteractions.talkTo(hero, n, 1_000L)!!
    }

    @Test
    fun mirewatchAndSunspireEachHaveAHuntMasterCrierAndShop() {
        val mire = listOf("Warden Brisk", "Crier Pell", "Odra")
        val sun = listOf("Sergeant Kasim", "Crier Nadim", "Tahir")
        mire.forEach { assertTrue("$it must be in Mirewatch", BiomeCities.mirewatch.contains(npc(it).x, npc(it).y)) }
        sun.forEach { assertTrue("$it must be in Sunspire", BiomeCities.sunspire.contains(npc(it).x, npc(it).y)) }

        val odra = talk("Odra")
        assertEquals(ActivePanel.SHOP, odra.activePanel)
        assertEquals(VendorCatalog.MIRE_OUTFITTER, odra.activeShopId)
        val tahir = talk("Tahir")
        assertEquals(VendorCatalog.SUN_CARAVANEER, tahir.activeShopId)
        for (vid in listOf(VendorCatalog.MIRE_OUTFITTER, VendorCatalog.SUN_CARAVANEER)) {
            VendorCatalog.stockFor(vid).forEach {
                assertTrue("${it.itemId} must exist", ItemCatalog.all.containsKey(it.itemId))
                assertTrue("${it.itemId} must have a price", it.price > 0)
            }
        }
        // Each specialist pays extra for its own biome's goods
        assertTrue(VendorCatalog.sellPrice(VendorCatalog.MIRE_OUTFITTER, "bile_gland") > VendorCatalog.sellPrice(null, "bile_gland"))
        assertTrue(VendorCatalog.sellPrice(VendorCatalog.SUN_CARAVANEER, "gorek_obsidian_horn") > VendorCatalog.sellPrice(null, "gorek_obsidian_horn"))
    }

    @Test
    fun cityHuntBoardsPostLocalContractsThatCanActuallyBeHunted() {
        val brisk = talk("Warden Brisk", level = 30)
        assertEquals(ActivePanel.HUNT_BOARD, brisk.activePanel)
        assertEquals(HuntBoards.MIREWATCH, HuntBoards.fromPanelId(brisk.activeShopId).id)
        assertEquals(HuntBoards.SUNSPIRE, HuntBoards.fromPanelId(talk("Sergeant Kasim").activeShopId).id)
        assertEquals(HuntBoards.ROOKVALE, HuntBoards.fromPanelId(talk("Captain Hale").activeShopId).id)

        val monsters = WorldMaps.initialMonsters()
        val bands = mapOf(HuntBoards.MIREWATCH to 20..37, HuntBoards.SUNSPIRE to 30..46, HuntBoards.FROSTPEAK to 42..59, HuntBoards.ASHEN to 55..75)
        for ((board, band) in bands) {
            val contracts = HuntContracts.forBoard(board)
            assertTrue(contracts.size >= 7)
            contracts.forEach { c ->
                val levels = monsters.filter { it.type == c.target }.map { it.level }
                assertTrue("${c.id}: ${c.target} must spawn somewhere", levels.isNotEmpty())
                assertTrue("${c.id}: ${c.target} must spawn within $band, got $levels", levels.any { it in band })
                assertTrue("${c.id} level ${c.minLevel} outside $band", c.minLevel in band)
            }
        }
        // Old Rookvale contracts keep their saved indexes
        assertEquals(0, HuntContracts.indexOf("rats"))
        assertEquals("b_elderdragon", HuntContracts.byIndex(18)!!.id)

        // Accept and complete a Mirewatch contract
        var s = NpcInteractions.handleAction(brisk, "hunt_accept:m_hags", 2_000L)
        assertEquals("m_hags", NpcInteractions.activeContract(s)!!.id)
        val hag = monsters.first { it.type == MonsterType.BOG_HAG }
        repeat(20) { s = NpcInteractions.onKillCredited(s, hag, 3_000L) }
        val gold = s.gold
        s = NpcInteractions.handleAction(s, "hunt_turn_in", 4_000L)
        assertEquals(gold + HuntContracts.byId("m_hags")!!.goldReward, s.gold)
        assertTrue(s.statusLog.any { it.contains("Warden Brisk") })
    }

    @Test
    fun eachCityCrierAnnouncesALocalCreatureThatEarnsBonusXp() {
        val day = TownCrier.DAY_MS
        val featured = TownCrier.featuredCreatures(5 * day)
        assertTrue(featured.size >= 3)
        assertTrue(TownCrier.featuredMarshCreature(5 * day) in featured)
        assertTrue(TownCrier.featuredBadlandsCreature(5 * day) in featured)
        assertTrue(TownCrier.featuredAshCreature(5 * day) in featured)
        val pell = talk("Crier Pell")
        assertTrue(pell.statusLog.any { it.startsWith("Crier Pell") })
        val nadim = talk("Crier Nadim")
        assertTrue(nadim.statusLog.any { it.startsWith("Crier Nadim") })
    }

    @Test
    fun frostpeakIsASnowboundCityWithItsOwnTownsfolk() {
        val f0 = WorldMaps.getFloor(0)
        val city = BiomeCities.frostpeak
        val tiles = buildSet { for (y in city.minY..city.maxY) for (x in city.minX..city.maxX) add(f0.tileAt(x, y)) }
        assertTrue(TileType.SNOW in tiles && TileType.GRANITE_WALL in tiles && TileType.WATER in tiles && TileType.MOUNTAIN_ROCK in tiles && TileType.MINI_DEPOT in tiles)
        assertTrue(TileType.SAND !in tiles && TileType.MARSH !in tiles)
        for (n in listOf("Huntmaster Sigrun", "Crier Halvard", "Ulfa", "Frost Cleric Bjorn", "Runesmith Astrid")) {
            val spot = npc(n)
            assertTrue("$n must be in Frostpeak", city.contains(spot.x, spot.y))
            assertTrue("$n must stand on open ground", f0.tileAt(spot.x, spot.y).walkable)
            assertTrue("$n must be in the PZ", f0.isProtectionZone(spot.x, spot.y))
        }
        // Reachable from the north road through the gatehouse
        val seen = HashSet<Pair<Int, Int>>(); val q = ArrayDeque<Pair<Int, Int>>()
        val start = 1396 to city.minY - 4
        seen += start; q.add(start)
        while (q.isNotEmpty()) {
            val (x, y) = q.removeFirst()
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val n = x + dx to y + dy
                if (n.first in city.minX - 6..city.maxX + 6 && n.second in city.minY - 6..city.maxY + 6 && n !in seen && f0.tileAt(n.first, n.second).walkable) { seen += n; q.add(n) }
            }
        }
        for (n in listOf("Huntmaster Sigrun", "Crier Halvard", "Ulfa", "Frost Cleric Bjorn", "Runesmith Astrid")) {
            val spot = npc(n)
            assertTrue("$n must be reachable", listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dy) -> (spot.x + dx to spot.y + dy) in seen })
        }
        assertEquals(VendorCatalog.FROST_FURRIER, talk("Ulfa").activeShopId)
        assertEquals(HuntBoards.FROSTPEAK, HuntBoards.fromPanelId(talk("Huntmaster Sigrun").activeShopId).id)
        assertTrue(talk("Crier Halvard").statusLog.any { it.startsWith("Crier Halvard") })
    }

    @Test
    fun ashenBastionIsAnObsidianForgeCityWithItsOwnTownsfolk() {
        val f0 = WorldMaps.getFloor(0)
        val city = BiomeCities.ashen
        val tiles = buildSet { for (y in city.minY..city.maxY) for (x in city.minX..city.maxX) add(f0.tileAt(x, y)) }
        assertTrue(TileType.OBSIDIAN_WALL in tiles && TileType.LAVA in tiles && TileType.STONE_BRIDGE in tiles && TileType.WATER in tiles && TileType.MINI_DEPOT in tiles)
        assertTrue(TileType.BIOME_STALL_ASHEN in tiles && TileType.SMELTING_FURNACE in tiles)
        assertTrue(TileType.SNOW !in tiles && TileType.SAND !in tiles && TileType.MARSH !in tiles)
        val folk = listOf("Warmaster Dravek", "Crier Ignatius", "Sorrel", "Ash Cleric Malakor", "War Alchemist Nyx")
        for (n in folk) {
            val spot = npc(n)
            assertTrue("$n must be in Ashen Bastion", city.contains(spot.x, spot.y))
            assertTrue("$n must stand on open ground", f0.tileAt(spot.x, spot.y).walkable)
            assertTrue("$n must be in the PZ", f0.isProtectionZone(spot.x, spot.y))
        }
        // Reachable from outside the West Gate, where the Foundry shaft sits
        val seen = HashSet<Pair<Int, Int>>(); val q = ArrayDeque<Pair<Int, Int>>()
        val start = city.minX - 2 to 254
        assertTrue(f0.tileAt(start.first, start.second).walkable)
        seen += start; q.add(start)
        while (q.isNotEmpty()) {
            val (x, y) = q.removeFirst()
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val n = x + dx to y + dy
                if (n.first in city.minX - 6..city.maxX + 6 && n.second in city.minY - 6..city.maxY + 6 && n !in seen && f0.tileAt(n.first, n.second).walkable) { seen += n; q.add(n) }
            }
        }
        for (n in folk) {
            val spot = npc(n)
            assertTrue("$n must be reachable", listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dy) -> (spot.x + dx to spot.y + dy) in seen })
        }
        // all three gates lead in: south gate and north-east gate too
        assertTrue((386 to city.maxY) in seen)
        assertTrue((BiomeCities.ASH_CX + 40 to BiomeCities.ASH_CY - 34) in seen)
        assertEquals(VendorCatalog.ASH_SLAGWRIGHT, talk("Sorrel").activeShopId)
        VendorCatalog.stockFor(VendorCatalog.ASH_SLAGWRIGHT).forEach { assertTrue(ItemCatalog.all.containsKey(it.itemId)) }
        assertTrue(VendorCatalog.sellPrice(VendorCatalog.ASH_SLAGWRIGHT, "molten_core") > VendorCatalog.sellPrice(null, "molten_core"))
        assertEquals(HuntBoards.ASHEN, HuntBoards.fromPanelId(talk("Warmaster Dravek").activeShopId).id)
        assertTrue(talk("Crier Ignatius").statusLog.any { it.startsWith("Crier Ignatius") })
    }

    @Test
    fun cityDistrictsAreTappableMapPinsOnReachableGround() {
        val f0 = WorldMaps.getFloor(0)
        val gates = mapOf("Mirewatch" to (545 to 590), "Sunspire" to (375 to 752), "Frostpeak" to (1396 to 150), "Ashen Bastion" to (328 to 254))
        val cities = mapOf("Mirewatch" to BiomeCities.mirewatch, "Sunspire" to BiomeCities.sunspire, "Frostpeak" to BiomeCities.frostpeak, "Ashen Bastion" to BiomeCities.ashen)
        assertEquals(gates.keys, BiomeCities.districts.keys)
        for ((city, list) in BiomeCities.districts) {
            assertTrue("$city needs several districts", list.size >= 5)
            assertEquals("$city district names must be unique", list.size, list.map { it.name }.toSet().size)
            val box = cities.getValue(city)
            val (sx, sy) = gates.getValue(city)
            val seen = HashSet<Pair<Int, Int>>(); val q = ArrayDeque<Pair<Int, Int>>()
            seen += sx to sy; q.add(sx to sy)
            while (q.isNotEmpty()) {
                val (x, y) = q.removeFirst()
                for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val n = x + dx to y + dy
                    if (n.first in box.minX - 8..box.maxX + 8 && n.second in box.minY - 8..box.maxY + 8 && n !in seen && f0.tileAt(n.first, n.second).walkable) { seen += n; q.add(n) }
                }
            }
            list.forEach { d ->
                assertTrue("${d.name} must lie inside $city", box.contains(d.x, d.y))
                assertTrue("${d.name} must be open ground", f0.tileAt(d.x, d.y).walkable)
                assertTrue("${d.name} must be reachable from the gate", (d.x to d.y) in seen)
            }
        }
        // Each district is a world-map pin that only appears once zoomed in close
        val pins = WorldBiomeBlueprint.worldMapPins.filter { it.category == WorldPinCategory.DISTRICT }
        assertEquals(BiomeCities.districts.values.sumOf { it.size }, pins.size)
        assertEquals(pins.size, pins.map { it.id }.toSet().size)
        pins.forEach { assertTrue(it.minZoomToShow > 1.6f) }
        assertTrue(pins.any { it.label == "Crucible Plaza" && it.subtitle == "Ashen Bastion district" })
    }

    @Test
    fun grimmawQuarryNowSitsInTheFrostpeakBand() {
        val monsters = WorldMaps.initialMonsters()
        monsters.filter { it.floor == -5 }.forEach { assertTrue("${it.type} Lv ${it.level}", it.level in 42..46) }
        monsters.filter { it.floor == -22 && it.type.isBoss }.forEach { assertEquals(47, it.level) }
        assertEquals("Lvl 42-47", BiomeProgressionRules.dungeonRecommendedLevelLabel(-5))
        // Grimmaw stone cores feed Frostpeak's Rime-Plate recipes
        assertTrue(com.example.game.model.RecipeCatalog.forgingRecipes.any { r -> r.outputItemId.startsWith("rime_plate") && r.ingredients.any { it.itemId == "grimmaw_stone_core" } })
    }

    @Test
    fun eachWormBitesBestInItsOwnBiomesCityWaters() {
        val f0 = WorldMaps.getFloor(0)
        val tiers = com.example.game.model.FishingCatalog.tiers.associateBy { it.baitId }
        fun waterIn(city: BiomeCities.City) = (city.minY..city.maxY).asSequence().flatMap { y -> (city.minX..city.maxX).asSequence().map { it to y } }
            .first { (x, y) -> f0.tileAt(x, y) == TileType.WATER && listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dy) -> f0.tileAt(x + dx, y + dy).walkable } }
        val cases = listOf(
            Triple(BiomeCities.mirewatch, "mire_bloodworm", "dune_sandworm"),
            Triple(BiomeCities.sunspire, "dune_sandworm", "frost_glowworm"),
            Triple(BiomeCities.frostpeak, "frost_glowworm", "mire_bloodworm")
        )
        for ((city, home, away) in cases) {
            val (wx, wy) = waterIn(city)
            assertTrue("${city.id}: $home should be home waters", com.example.game.model.FishingCatalog.isHomeWaters(tiers.getValue(home), 0, wx, wy))
            assertTrue("${city.id}: $away should not be", !com.example.game.model.FishingCatalog.isHomeWaters(tiers.getValue(away), 0, wx, wy))
        }
    }
}
