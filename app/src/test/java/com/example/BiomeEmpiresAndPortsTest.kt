package com.example

import com.example.game.engine.GameEngine
import com.example.game.model.MonsterType
import com.example.game.model.TileType
import com.example.ui.screens.WorldMapViewportCamera
import com.example.game.world.BiomeIdeology
import com.example.game.world.BiomeProgressionRules
import com.example.game.world.CoastalPortValidator
import com.example.game.world.HarborPortCandidate
import com.example.game.world.NpcRole
import com.example.game.world.OverlandBiomeTier
import com.example.game.world.WorldBiomeBlueprint
import com.example.game.world.WorldMaps
import com.example.game.world.WorldPinCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BiomeEmpiresAndPortsTest {

    @Test
    fun overlandMonstersStrictlyFollowFiveBiomeLevelBandsAndScaleStatsAcrossRespawns() {
        val monsters = WorldMaps.initialMonsters()
        val floor0Monsters = monsters.filter { it.floor == 0 }
        assertTrue("Floor 0 must spawn overland monsters across all 5 biomes", floor0Monsters.size >= 40)

        val seenBiomes = mutableSetOf<OverlandBiomeTier>()
        for (m in floor0Monsters) {
            val biome = BiomeProgressionRules.biomeForGlobalTile(m.x, m.y)
            seenBiomes += biome
            val maxAllowed = if (m.type.isBoss) biome.maxLevel + 5 else biome.maxLevel
            assertTrue(
                "Overland ${m.type.displayName} at (${m.x},${m.y}) in ${biome.displayName} has level ${m.level}, expected ${biome.minLevel}..$maxAllowed",
                m.level in biome.minLevel..maxAllowed
            )
        }
        assertEquals(
            "All 5 overland biome tiers must be populated with level-locked monsters",
            OverlandBiomeTier.entries.toSet(),
            seenBiomes
        )

        // Verify higher-biome monsters dynamically scale HP, damage, armor, and XP above baseline
        val ashenMonsters = floor0Monsters.filter {
            BiomeProgressionRules.biomeForGlobalTile(it.x, it.y) == OverlandBiomeTier.ASHEN_CALDERA
        }
        assertTrue("Ashen Caldera must hold Lvl 55-75+ monsters", ashenMonsters.isNotEmpty())
        for (m in ashenMonsters) {
            assertTrue("Ashen monster level must be >= 55, was ${m.level}", m.level >= 55)
            assertTrue("Scaled HP (${m.maxHp}) must exceed base HP (${m.type.maxHp})", m.maxHp > m.type.maxHp)
            assertTrue("Scaled XP (${m.scaledXpReward}) must exceed base XP (${m.type.xpReward})", m.scaledXpReward > m.type.xpReward)
            assertTrue("Scaled maxDmg (${m.scaledMaxDmg}) must exceed base maxDmg (${m.type.maxDmg})", m.scaledMaxDmg > m.type.maxDmg)
        }

        // Outer-continent dungeons scale strictly from surfaceMinLevel + depth * 6
        // (Grimmaw -5 rebalanced to its Frostpeak home at depth 0: Lv 42-47; Gorek -6 was rebalanced to depth 1 in its badlands home: Lv 36-41)
        for ((floorId, depth) in listOf(-5 to 0, -6 to 1)) {
            val surfaceMin = BiomeProgressionRules.surfaceBiomeForDungeonFloor(floorId).minLevel
            val expectedBase = surfaceMin + depth * 6
            val floorMonsters = monsters.filter { it.floor == floorId }
            assertTrue("Floor $floorId must have monsters", floorMonsters.isNotEmpty())
            for (m in floorMonsters) {
                val expectedRange = if (m.type.isBoss) {
                    (expectedBase + 5)..(expectedBase + 5)
                } else {
                    expectedBase..(expectedBase + 4)
                }
                assertTrue(
                    "Floor $floorId ${m.type.displayName} level ${m.level} must lie in $expectedRange",
                    m.level in expectedRange
                )
            }
        }

        // The Deep Delve chain under Rookvale climbs through Lv 5-20 (bosses capped at 20)
        val deepDelveBands = mapOf(-1 to 5..9, -2 to 9..13, -3 to 13..17, -4 to 16..20)
        for ((floorId, band) in deepDelveBands) {
            for (m in monsters.filter { it.floor == floorId }) {
                val expectedRange = if (m.type.isBoss) (band.first + 5).coerceAtMost(20)..(band.first + 5).coerceAtMost(20) else band
                assertTrue("Floor $floorId ${m.type.displayName} level ${m.level} must lie in $expectedRange", m.level in expectedRange)
            }
        }

        // Verify monster level & scaled stats are preserved across death -> PendingRespawn -> respawn!
        val baseState = GameEngine.createNewWorld("Scaler")
        val target = ashenMonsters.first()
        val nowMs = 1_750_000_000_000L
        val afterKill = GameEngine.applyDamageToMonster(baseState, target.id, target.hp + 9999, nowMs)
        val pending = afterKill.pendingRespawns.firstOrNull { it.originalMonsterId == target.id }
        assertNotNull("Slain scaled monster must queue PendingRespawn", pending)
        assertEquals("PendingRespawn must preserve scaled monster level", target.level, pending!!.level)

        val readyState = afterKill.copy(
            pendingRespawns = afterKill.pendingRespawns.map {
                if (it.originalMonsterId == target.id) it.copy(ticksRemaining = 1, respawnAtMs = 0L) else it
            }
        )
        val afterRespawn = GameEngine.processCombatTick(readyState, nowMs + 60_000L)
        val respawned = afterRespawn.monsters.firstOrNull { it.id == target.id }
        assertNotNull("Respawned monster must return to the world", respawned)
        val targetBiome = com.example.game.world.BiomeMonsterSpawner.resolveBiomeTypeAt(target.spawnX, target.spawnY)
        assertTrue(
            "Respawned species ${respawned!!.type} must belong to ${targetBiome.displayName} pool",
            targetBiome.containsSpecies(respawned.type)
        )
        assertTrue(
            "Respawned level ${respawned.level} must stay strictly inside ${targetBiome.minLevel}..${targetBiome.maxLevel}",
            respawned.level in targetBiome.minLevel..targetBiome.maxLevel
        )
        assertTrue("Respawned monster must preserve scaled maxHp above base HP", respawned.maxHp > respawned.type.maxHp)
    }

    @Test
    fun allFiveOverlandBiomeIdeologiesAndSixUndergroundForwardCampsGenerateCorrectly() {
        val floor0 = WorldMaps.getFloor(0)

        // Every overland biome ideology has at least one settlement with its signature architecture
        val settlementsByIdeology = WorldBiomeBlueprint.secondarySettlements.groupBy { it.ideology }
        for (ideology in BiomeIdeology.entries) {
            val list = settlementsByIdeology[ideology].orEmpty()
            assertTrue("Biome ideology $ideology must have at least one settlement", list.isNotEmpty())
            val sample = list.first()
            val tiles = mutableSetOf<TileType>()
            for (y in sample.minY..sample.maxY) {
                for (x in sample.minX..sample.maxX) {
                    tiles += floor0.tileAt(x, y)
                }
            }
            assertTrue("Settlement ${sample.name} center must be a Protection Zone", floor0.isProtectionZone(sample.centerX, sample.centerY))
            when (ideology) {
                BiomeIdeology.HEARTLAND -> assertTrue(
                    "Heartland settlement must feature half-timber walls & cobblestone",
                    TileType.TIMBER_WALL in tiles && TileType.COBBLESTONE in tiles
                )
                BiomeIdeology.MARSHLAND -> assertTrue(
                    "Marshland settlement must feature raised plank walkways & campfire braziers",
                    TileType.WOOD_PLANK_FLOOR in tiles && TileType.CAMPFIRE_BRAZIER in tiles
                )
                BiomeIdeology.BADLANDS -> assertTrue(
                    "Badlands settlement must feature sandstone walls, bazaar awnings & oasis well",
                    TileType.SANDSTONE_WALL in tiles && TileType.MARKET_AWNING in tiles && TileType.FOUNTAIN in tiles
                )
                BiomeIdeology.FROSTPEAK -> assertTrue(
                    "Frostpeak citadel must feature heavy granite walls & hearth-forges",
                    TileType.GRANITE_WALL in tiles && TileType.SMELTING_FURNACE in tiles && TileType.CAMPFIRE_BRAZIER in tiles
                )
                BiomeIdeology.ASHEN_BLIGHT -> assertTrue(
                    "Ashen fortress must feature obsidian walls, war banners & glowing braziers",
                    TileType.OBSIDIAN_WALL in tiles && TileType.WAR_BANNER in tiles && TileType.CAMPFIRE_BRAZIER in tiles
                )
            }
        }

        // All 6 dungeon floors (-1..-6) contain Safe Protection Zone forward camps with Healer & Supply NPCs
        for (floorId in listOf(-1, -2, -3, -4, -5, -6)) {
            val camp = WorldMaps.undergroundCampForFloor(floorId)
            assertNotNull("Floor $floorId must define an UndergroundCampZone", camp)
            val floorMap = WorldMaps.getFloor(floorId)
            assertTrue(
                "Floor $floorId forward camp Healer tile (${camp!!.healerX},${camp.healerY}) must be in PZ and walkable",
                floorMap.isProtectionZone(camp.healerX, camp.healerY) && floorMap.tileAt(camp.healerX, camp.healerY).walkable
            )
            assertTrue(
                "Floor $floorId forward camp Supply NPC tile (${camp.merchantX},${camp.merchantY}) must be in PZ and walkable",
                floorMap.isProtectionZone(camp.merchantX, camp.merchantY) && floorMap.tileAt(camp.merchantX, camp.merchantY).walkable
            )
            val floorNpcs = WorldMaps.npcs.filter { it.floor == floorId }
            assertTrue("Floor $floorId camp must host a TEMPLE_PRIEST Healer NPC", floorNpcs.any { it.role == NpcRole.TEMPLE_PRIEST })
            assertTrue("Floor $floorId camp must host a SHOP_ALCHEMIST Supply NPC", floorNpcs.any { it.role == NpcRole.SHOP_ALCHEMIST })
        }
    }

    @Test
    fun allHarborPortsSitOnTrueOceanCoastlinesAndInlandCandidatesSnapToOcean() {
        val floor0 = WorldMaps.getFloor(0)
        val validatedPorts = WorldBiomeBlueprint.validatedCoastalPorts
        assertEquals(6, validatedPorts.size)

        for (vp in validatedPorts) {
            assertTrue(
                "Port ${vp.port.name} must pass strict true ocean coastline verification (>=25 connected ocean tiles within 1-2 tiles)",
                CoastalPortValidator.verifyPortOnTrueOceanCoast(vp) { x, y ->
                    WorldBiomeBlueprint.rawOverlandTerrainAt(x, y)
                }
            )
            assertTrue(
                "Port ${vp.port.name} ocean body must have >= 25 connected water tiles, had ${vp.oceanConnectedWaterCount}",
                vp.oceanConnectedWaterCount >= CoastalPortValidator.MIN_OCEAN_WATER_TILES
            )
            assertTrue(
                "Port ${vp.port.name} arrival (${vp.port.arrivalX},${vp.port.arrivalY}) must be walkable WOOD_PIER",
                floor0.tileAt(vp.port.arrivalX, vp.port.arrivalY) == TileType.WOOD_PIER
            )
            assertTrue(
                "Port ${vp.port.name} must project pier tiles over raw ocean water",
                vp.pierTiles.any { key ->
                    val px = (key and 0xFFFFFFFFL).toInt()
                    val py = (key ushr 32).toInt()
                    CoastalPortValidator.isSeaWaterTile(WorldBiomeBlueprint.rawOverlandTerrainAt(px, py))
                }
            )
            assertTrue(
                "Port ${vp.port.name} must moor at least one boat over open ocean water",
                vp.boatTiles.isNotEmpty()
            )
        }

        // Explicitly test inland candidates snapping automatically to true ocean coastlines
        val inlandRookvale = HarborPortCandidate(
            id = "test_inland_rookvale",
            name = "Inland Rookvale Test",
            regionId = "rookvale_valley",
            captainName = "Captain Test",
            candidateX = 1411,
            candidateY = 631,
            roadTargetX = 1396,
            roadTargetY = 592,
            description = "Inland river candidate",
            recommendedLevel = "Lvl 1-20",
            dangerSeverity = 0,
            preferredRegionBounds = 1210..1590,
            preferredRegionYBounds = 420..780
        )
        val snappedRookvale = CoastalPortValidator.validateAndSnapPort(inlandRookvale) { x, y ->
            WorldBiomeBlueprint.rawOverlandTerrainAt(x, y)
        }
        assertTrue(
            "Inland Rookvale candidate (1411,631) must snap onto true Eastern Ocean shoreline",
            snappedRookvale.landAnchorX >= 1550 &&
                CoastalPortValidator.verifyPortOnTrueOceanCoast(snappedRookvale) { x, y ->
                    WorldBiomeBlueprint.rawOverlandTerrainAt(x, y)
                }
        )

        val inlandDesert = HarborPortCandidate(
            id = "test_inland_desert",
            name = "Inland Desert Test",
            regionId = "sunspire_desert",
            captainName = "Captain Dunes",
            candidateX = 340,
            candidateY = 760,
            roadTargetX = 375,
            roadTargetY = 790,
            description = "Deep inland desert candidate",
            recommendedLevel = "Lvl 30-45",
            dangerSeverity = 2
        )
        val snappedDesert = CoastalPortValidator.validateAndSnapPort(inlandDesert) { x, y ->
            WorldBiomeBlueprint.rawOverlandTerrainAt(x, y)
        }
        assertTrue(
            "Inland desert candidate (340,760) must snap outward onto a verified >=25-tile ocean shoreline",
            CoastalPortValidator.verifyPortOnTrueOceanCoast(snappedDesert) { x, y ->
                WorldBiomeBlueprint.rawOverlandTerrainAt(x, y)
            }
        )
    }

    @Test
    fun worldMapAutoRevealsForwardCampsAndExactLevelRangesWhenZoomedIn() {
        val allPins = WorldBiomeBlueprint.worldMapPins
        assertTrue("World Map must include EMPIRE pins", allPins.any { it.category == WorldPinCategory.EMPIRE })
        assertTrue("World Map must include CAMP pins", allPins.any { it.category == WorldPinCategory.CAMP })

        // At overview zoom (1.0x), Forward Camps stay hidden while Empires & Ocean Ferries remain visible
        val overviewPins = WorldMapViewportCamera.filterVisiblePins(
            pins = allPins,
            mapZoom = 1.0f,
            showCities = true,
            showFerries = true,
            showDungeons = true
        )
        assertTrue("Overview zoom must show Empires", overviewPins.any { it.category == WorldPinCategory.EMPIRE })
        assertTrue("Overview zoom must show Ocean Ferries", overviewPins.any { it.category == WorldPinCategory.FERRY })
        assertFalse("Overview zoom (< 1.6x) must hide Forward Camps for clean readability", overviewPins.any { it.category == WorldPinCategory.CAMP })

        // At zoomed-in scale (>= 1.6x), Forward Camps and settlement level ranges auto-reveal!
        val zoomedPins = WorldMapViewportCamera.filterVisiblePins(
            pins = allPins,
            mapZoom = 2.0f,
            showCities = true,
            showFerries = true,
            showDungeons = true
        )
        assertTrue("Zoomed-in map (>= 1.6x) must auto-reveal Forward Camps", zoomedPins.any { it.category == WorldPinCategory.CAMP })

        val frostpeakPin = allPins.first { it.id.contains("frostpeak_citadel") }
        val overviewLabel = WorldMapViewportCamera.formatPinLabel(frostpeakPin, mapZoom = 1.0f)
        val zoomedLabel = WorldMapViewportCamera.formatPinLabel(frostpeakPin, mapZoom = 2.0f)
        assertFalse("Overview label should stay compact without level range", overviewLabel.contains("Lvl 42-58"))
        assertTrue("Zoomed-in label must reveal exact biome level range", zoomedLabel.contains("Lvl 42-58"))
    }
}
