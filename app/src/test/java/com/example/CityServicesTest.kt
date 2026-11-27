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
        val bands = mapOf(HuntBoards.MIREWATCH to 20..37, HuntBoards.SUNSPIRE to 30..46, HuntBoards.FROSTPEAK to 42..58)
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
    fun grimmawQuarryNowSitsInTheFrostpeakBand() {
        val monsters = WorldMaps.initialMonsters()
        monsters.filter { it.floor == -5 }.forEach { assertTrue("${it.type} Lv ${it.level}", it.level in 42..46) }
        monsters.filter { it.floor == -22 && it.type.isBoss }.forEach { assertEquals(47, it.level) }
        assertEquals("Lvl 42-47", BiomeProgressionRules.dungeonRecommendedLevelLabel(-5))
        // Grimmaw stone cores feed Frostpeak's Rime-Plate recipes
        assertTrue(com.example.game.model.RecipeCatalog.forgingRecipes.any { r -> r.outputItemId.startsWith("rime_plate") && r.ingredients.any { it.itemId == "grimmaw_stone_core" } })
    }
}
