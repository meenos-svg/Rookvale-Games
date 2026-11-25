package com.example

import com.example.game.cloud.CloudSavePayloadCodec
import com.example.game.engine.GameEngine
import com.example.game.model.MonsterType
import com.example.game.model.TileType
import com.example.game.multiplayer.MultiplayerSanitizer
import com.example.game.world.BiomeMonsterSpawner
import com.example.game.world.BiomeProgressionRules
import com.example.game.world.BiomeType
import com.example.game.world.ContinentalDungeonGenerator
import com.example.game.world.NpcRole
import com.example.game.world.OverlandBiomeTier
import com.example.game.world.WorldBiomeBlueprint
import com.example.game.world.WorldFloor
import com.example.game.world.WorldMaps
import com.example.game.world.WorldPinCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BiomeSpawningAndContinentalDungeonsTest {

    private fun reachableTiles(floor: WorldFloor, startX: Int, startY: Int): Set<Pair<Int, Int>> {
        val visited = HashSet<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        if (!floor.tileAt(startX, startY).walkable) return emptySet()
        visited.add(startX to startY)
        queue.add(startX to startY)
        val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            for ((dx, dy) in dirs) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until floor.width && ny in 0 until floor.height) {
                    val key = nx to ny
                    if (key !in visited && floor.tileAt(nx, ny).walkable) {
                        visited.add(key)
                        queue.add(key)
                    }
                }
            }
        }
        return visited
    }

    @Test
    fun rookvaleCityHasZeroDungeonEntrancesPortalsOrPinsAndReplacesThemWithOpenCourtyards() {
        val ox = WorldMaps.ROOKVALE_ORIGIN_X
        val oy = WorldMaps.ROOKVALE_ORIGIN_Y
        val cityMinX = ox + 146
        val cityMaxX = ox + 256
        val cityMinY = oy + 148
        val cityMaxY = oy + 228

        val floor0 = WorldMaps.getFloor(0)

        // 1. Zero STAIRS_DOWN or ROPE_HOLE_DOWN tiles anywhere inside Rookvale City limits
        for (y in cityMinY..cityMaxY) {
            for (x in cityMinX..cityMaxX) {
                val tile = floor0.tileAt(x, y)
                assertFalse(
                    "Rookvale City tile ($x,$y) must NOT be STAIRS_DOWN",
                    tile == TileType.STAIRS_DOWN
                )
                assertFalse(
                    "Rookvale City tile ($x,$y) must NOT be ROPE_HOLE_DOWN",
                    tile == TileType.ROPE_HOLE_DOWN
                )
            }
        }

        // 2. Old sewer & cellar spots are now open plazas / stalls / garden courtyards
        val oldCellarTile = floor0.tileAt(ox + 152, oy + 172)
        assertTrue(
            "Old western cellar spot must be open walkable garden/plaza, was $oldCellarTile",
            oldCellarTile == TileType.FLOWER_GRASS || oldCellarTile == TileType.COBBLESTONE
        )
        assertEquals(
            "Old academy sewer courtyard must host a market awning stall",
            TileType.MARKET_AWNING,
            floor0.tileAt(ox + 242, oy + 178)
        )
        assertTrue(
            "Old academy sewer landing (242,179) must be open walkable cobblestone plaza",
            floor0.tileAt(ox + 242, oy + 179) == TileType.COBBLESTONE
        )

        // 3. Zero FloorPortal entrances OR exits inside Rookvale City limits, and every portal lands on walkable ground
        for (portal in WorldMaps.portals) {
            if (portal.fromFloor == 0) {
                assertFalse(
                    "Portal entrance (${portal.fromX},${portal.fromY}) must NOT lie inside Rookvale City",
                    portal.fromX in cityMinX..cityMaxX && portal.fromY in cityMinY..cityMaxY
                )
            }
            if (portal.toFloor == 0) {
                assertFalse(
                    "Portal exit (${portal.toX},${portal.toY}) from F${portal.fromFloor} must NOT land inside Rookvale City",
                    portal.toX in cityMinX..cityMaxX && portal.toY in cityMinY..cityMaxY
                )
            }
            val destTile = WorldMaps.getFloor(portal.toFloor).tileAt(portal.toX, portal.toY)
            assertTrue(
                "Portal F${portal.fromFloor}(${portal.fromX},${portal.fromY}) -> F${portal.toFloor}(${portal.toX},${portal.toY}) must land on walkable tile, got $destTile",
                destTile.walkable
            )
        }

        // 4. Zero World Map DUNGEON pins inside Rookvale City limits
        val cityDungeonPins = WorldBiomeBlueprint.worldMapPins.filter { pin ->
            pin.category == WorldPinCategory.DUNGEON &&
                pin.x in cityMinX..cityMaxX &&
                pin.y in cityMinY..cityMaxY
        }
        assertTrue(
            "World Map must have zero DUNGEON pins inside Rookvale City, found: $cityDungeonPins",
            cityDungeonPins.isEmpty()
        )
    }

    @Test
    fun rookvaleHeartlandEnforcesStrictLevel1To20PersonaAndGentleGateScaling() {
        val monsters = WorldMaps.initialMonsters()
        val floor0 = WorldMaps.getFloor(0)

        val heartlandMonsters = monsters.filter {
            it.floor == 0 && BiomeProgressionRules.biomeForGlobalTile(it.x, it.y) == OverlandBiomeTier.ROOKVALE_HEARTLAND
        }
        assertTrue("Rookvale Heartland must populate natural wildlife & frontier foes", heartlandMonsters.size >= 35)

        val nearGateLevels = mutableListOf<Int>()
        val outerBorderLevels = mutableListOf<Int>()
        for (m in heartlandMonsters) {
            assertTrue(
                "Heartland ${m.type.displayName} at (${m.x},${m.y}) has level ${m.level}, must be strictly 1..20",
                m.level in 1..20
            )
            assertFalse(
                "Heartland spawn (${m.x},${m.y}) must never lie in a Protection Zone",
                floor0.isProtectionZone(m.x, m.y)
            )
            assertTrue(
                "Heartland spawn (${m.x},${m.y}) must be walkable",
                floor0.tileAt(m.x, m.y).walkable
            )
            assertTrue(
                "Tier 5-6 Grimmaw/Gorek must never spawn in Rookvale Heartland, found ${m.type}",
                m.type.tier <= 4
            )
            val dist = hypot((m.x - WorldMaps.TEMPLE_SPAWN_X).toDouble(), (m.y - WorldMaps.TEMPLE_SPAWN_Y).toDouble())
            if (dist <= 88.0) nearGateLevels += m.level
            if (dist >= 145.0) outerBorderLevels += m.level
        }

        assertTrue("Near-gate starter meadows must have Lv 1-5 spawns", nearGateLevels.isNotEmpty())
        assertTrue("All near-gate meadow spawns must stay <= Lv 5, got max=${nearGateLevels.max()}", nearGateLevels.max() <= 5)
        assertTrue("Outer Heartland borders must scale up to Lv 12-20", outerBorderLevels.isNotEmpty())
        assertTrue("Outer Heartland borders must contain Lv 14+ encounters, got max=${outerBorderLevels.max()}", outerBorderLevels.max() >= 14)

        // Upper starter ruins (Floor -1) must stay strictly within early Lv 1-20 progression
        val f1Monsters = monsters.filter { it.floor == -1 }
        assertTrue("Floor -1 must have starter ruin monsters", f1Monsters.isNotEmpty())
        f1Monsters.forEach { m ->
            assertTrue("Floor -1 ${m.type} level ${m.level} must remain within early Lv 1-20 band", m.level in 1..20)
        }
    }

    @Test
    fun biomeDrivenSpawningAndRespawningPopulatesEveryBiomePoolWithZeroChunkOverflow() {
        val monsters = WorldMaps.initialMonsters()
        val floor0 = WorldMaps.getFloor(0)
        val overland = monsters.filter { it.floor == 0 }

        val coveredBiomeTypes = mutableSetOf<BiomeType>()
        for (m in overland) {
            val biome = BiomeMonsterSpawner.resolveBiomeTypeAt(m.x, m.y)
            coveredBiomeTypes += biome
            assertTrue(
                "Monster ${m.type} at (${m.x},${m.y}) must belong to ${biome.displayName} pool",
                biome.containsSpecies(m.type)
            )
            assertTrue(
                "Monster ${m.type} level ${m.level} at (${m.x},${m.y}) must lie in ${biome.minLevel}..${biome.maxLevel}",
                m.level in biome.minLevel..biome.maxLevel
            )
            assertFalse(
                "Overland spawn (${m.x},${m.y}) must never be in a Protection Zone",
                floor0.isProtectionZone(m.x, m.y)
            )
        }

        assertEquals(
            "All 9 ecological BiomeTypes must be dynamically populated across Floor 0",
            BiomeType.entries.toSet(),
            coveredBiomeTypes
        )

        // Verify chunk LRU stays strictly bounded even after generating full continent spawns
        assertTrue(
            "Streaming chunk cache must stay within LRU cap (<= 180), was ${floor0.chunkedGrid.cachedChunkCount}",
            floor0.chunkedGrid.cachedChunkCount <= 180
        )

        // Verify dynamic respawn re-samples biome pool and clamps level strictly within biome range
        val marshTile = 310 to 530
        val marshBiome = BiomeMonsterSpawner.resolveBiomeTypeAt(marshTile.first, marshTile.second)
        assertEquals(BiomeType.WHISPERING_MARSH, marshBiome)
        val seenSpecies = mutableSetOf<MonsterType>()
        for (seed in 1..30) {
            val (sp, lvl) = BiomeMonsterSpawner.resolveRespawnSpeciesAndLevel(
                floor = 0,
                spawnX = marshTile.first,
                spawnY = marshTile.second,
                originalType = MonsterType.SWAMP_TROLL,
                fallbackLevel = 22,
                respawnSeed = seed * 97
            )
            seenSpecies += sp
            assertTrue("Respawn species $sp must belong to Marsh pool", marshBiome.containsSpecies(sp))
            assertTrue("Respawn level $lvl must lie in ${marshBiome.minLevel}..${marshBiome.maxLevel}", lvl in marshBiome.minLevel..marshBiome.maxLevel)
        }
        assertTrue("Dynamic biome respawn must draw diverse species from the biome pool", seenSpecies.size >= 3)
    }

    @Test
    fun dedicatedContinentalDungeonsFloorsMinus7ToMinus10AreReachableAndExcludedFromCloudSaves() {
        val generated = ContinentalDungeonGenerator.generatedDungeons
        assertEquals(4, generated.size)

        val allMonsters = WorldMaps.initialMonsters()
        val allOres = WorldMaps.initialOreNodes()

        for (floorId in listOf(-7, -8, -9, -10)) {
            assertTrue("MultiplayerSanitizer must accept Floor $floorId", MultiplayerSanitizer.isValidFloor(floorId))
            val floor = WorldMaps.getFloor(floorId)
            val camp = WorldMaps.undergroundCampForFloor(floorId)
            assertNotNull("Floor $floorId must define an UndergroundCampZone", camp)

            // Verify bidirectional surface <-> dungeon portals
            val entryPortal = WorldMaps.portals.firstOrNull { it.fromFloor == 0 && it.toFloor == floorId }
            val exitPortal = WorldMaps.portals.firstOrNull { it.fromFloor == floorId && it.toFloor == 0 }
            assertNotNull("Floor $floorId must have an overland entry portal", entryPortal)
            assertNotNull("Floor $floorId must have a return portal to Floor 0", exitPortal)

            // Verify safe forward camp PZ, Healer, and Supply Alchemist
            assertTrue(floor.isProtectionZone(camp!!.healerX, camp.healerY))
            assertTrue(floor.isProtectionZone(camp.merchantX, camp.merchantY))
            val floorNpcs = WorldMaps.npcs.filter { it.floor == floorId }
            assertTrue("Floor $floorId must host Healer NPC", floorNpcs.any { it.role == NpcRole.TEMPLE_PRIEST })
            assertTrue("Floor $floorId must host Supply Alchemist NPC", floorNpcs.any { it.role == NpcRole.SHOP_ALCHEMIST })

            // Verify 100% BFS reachability from entry landing to exit stairs, every monster, and boss
            val reachable = reachableTiles(floor, entryPortal!!.toX, entryPortal.toY)
            assertTrue("Floor $floorId must have substantial connected chambers", reachable.size >= 250)
            assertTrue("Floor $floorId return stairs must be reachable", (exitPortal!!.fromX to exitPortal.fromY) in reachable)

            val floorMonsters = allMonsters.filter { it.floor == floorId }
            assertTrue("Floor $floorId must have biome patrols + boss", floorMonsters.size >= 8)
            val boss = floorMonsters.firstOrNull { it.type.isBoss }
            assertNotNull("Floor $floorId must host a Boss chamber encounter", boss)

            val expectedBase = BiomeProgressionRules.dungeonBaseLevel(
                BiomeProgressionRules.surfaceBiomeForDungeonFloor(floorId).minLevel,
                floorId
            )
            for (m in floorMonsters) {
                assertTrue("Floor $floorId ${m.type} at (${m.x},${m.y}) must be reachable", (m.x to m.y) in reachable)
                assertFalse("Floor $floorId ${m.type} must not spawn in safe camp PZ", floor.isProtectionZone(m.x, m.y))
                val range = if (m.type.isBoss) (expectedBase + 5)..(expectedBase + 5) else expectedBase..(expectedBase + 4)
                assertTrue("Floor $floorId ${m.type} level ${m.level} must lie in $range", m.level in range)
            }

            // Verify biome-appropriate mining nodes exist on the dungeon floor
            val floorOres = allOres.keys.filter { it.first == floorId }
            assertTrue("Floor $floorId must include biome mining nodes", floorOres.size >= 4)
        }

        // Verify Supabase slim character save supports Floor -10 and NEVER serializes dungeon/monster entities
        val heroInCaldera = GameEngine.createNewWorld("Delver").copy(floor = -10, playerX = 24, playerY = 40)
        val save = GameEngine.toSaveData(heroInCaldera)
        assertEquals(-10, save.floor)
        val encoded = CloudSavePayloadCodec.encode(save)
        val raw = encoded.toString()
        assertFalse("Cloud save_data must never serialize monsters", raw.contains("\"monsters\""))
        assertFalse("Cloud save_data must never serialize pendingRespawns", raw.contains("\"pendingRespawns\""))
        assertFalse("Cloud save_data must never serialize oreNodes", raw.contains("\"oreNodes\""))
        val decoded = CloudSavePayloadCodec.decode(encoded, "slot_delver", "Delver")
        assertEquals("Decoded floor must preserve Floor -10", -10, decoded.floor)
    }

    @Test
    fun rookvaleOuterBorderRebuiltAsRusticOutlawCampsAndDungeonsTunedToLevel1To20() {
        val floor0 = WorldMaps.getFloor(0)

        // 1. Cleared SW (1266..1312, 704..736) & SE (1488..1534, 704..736) sites must have ZERO rectangular stone boxes
        val swBox = Triple(1266..1312, 704..736, "SW Briar Outlaw Camp")
        val seBox = Triple(1488..1534, 704..736, "SE Watchwood Outlaw Camp")
        for ((xs, ys, label) in listOf(swBox, seBox)) {
            var campfires = 0
            var tents = 0
            var grassOrDirt = 0
            for (y in ys) {
                for (x in xs) {
                    val t = floor0.tileAt(x, y)
                    assertFalse("$label at ($x,$y) must NOT contain rectangular STONE_WALL", t == TileType.STONE_WALL)
                    assertFalse("$label at ($x,$y) must NOT contain IRON_BAR_GATE fortress wall", t == TileType.IRON_BAR_GATE)
                    assertFalse("$label at ($x,$y) must NOT contain COBBLESTONE slab", t == TileType.COBBLESTONE)
                    assertFalse("$label at ($x,$y) must NOT contain CRYPT_FLOOR slab", t == TileType.CRYPT_FLOOR)
                    assertFalse("$label at ($x,$y) must NOT contain LAVA", t == TileType.LAVA)
                    if (t == TileType.CAMPFIRE_BRAZIER) campfires++
                    if (t == TileType.MARKET_AWNING) tents++
                    if (t == TileType.GRASS || t == TileType.DARK_GRASS || t == TileType.FLOWER_GRASS || t == TileType.DIRT_FLOOR) {
                        grassOrDirt++
                    }
                }
            }
            assertTrue("$label must have rustic campfires (got $campfires)", campfires >= 2)
            assertTrue("$label must have rustic canvas tents (got $tents)", tents >= 4)
            assertTrue("$label must blend continuous Heartland grass & dirt terrain", grassOrDirt >= 900)
        }

        // 2. Zero Grimmaw/Gorek landmarks or World Map pins anywhere inside Rookvale Heartland (1200..1599, 400..799)
        val heartlandPins = WorldBiomeBlueprint.worldMapPins.filter { WorldBiomeBlueprint.isInRookvaleHeartland(it.x, it.y) }
        assertTrue(
            "Rookvale Heartland must have zero Grimmaw or Gorek World Map pins",
            heartlandPins.none { it.label.contains("Grimmaw", ignoreCase = true) || it.label.contains("Gorek", ignoreCase = true) }
        )
        assertTrue(
            "Briar Outlaw Camp pin must exist in SW Heartland",
            heartlandPins.any { it.id == "camp_briar_outlaws" }
        )
        assertTrue(
            "Watchwood Outlaw Camp pin must exist in SE Heartland",
            heartlandPins.any { it.id == "camp_watchwood_outlaws" }
        )
        val heartlandLandmarks = WorldMaps.landmarks.filter {
            it.floor == 0 && WorldBiomeBlueprint.isInRookvaleHeartland(it.x, it.y)
        }
        assertTrue(
            "Rookvale Heartland must have zero Grimmaw or Gorek landmarks",
            heartlandLandmarks.none { it.label.contains("GRIMMAW", ignoreCase = true) || it.label.contains("GOREK", ignoreCase = true) }
        )
        // Grimmaw Quarry (-5) and Gorek Labyrinth (-6) must still live in their distant outer biomes
        val grimmawPin = WorldBiomeBlueprint.worldMapPins.firstOrNull { it.id == "dng_frostcrest" }
        val gorekPin = WorldBiomeBlueprint.worldMapPins.firstOrNull { it.id == "dng_badlands" }
        assertNotNull("Relocated Grimmaw Quarry pin must exist in Frostpeak Highlands", grimmawPin)
        assertNotNull("Relocated Gorek Labyrinth pin must exist in Sunscorch Badlands", gorekPin)
        assertFalse(WorldBiomeBlueprint.isInRookvaleHeartland(grimmawPin!!.x, grimmawPin.y))
        assertFalse(WorldBiomeBlueprint.isInRookvaleHeartland(gorekPin!!.x, gorekPin.y))

        // 3. Dedicated Outlaw Bandit & Outlaw Rogue monsters populate the Rookvale Outlaw Camps strictly in Lv 12..20
        val allMonsters = WorldMaps.initialMonsters()
        val campMonsters = allMonsters.filter {
            it.floor == 0 && BiomeMonsterSpawner.isInOutlawCampZone(it.x, it.y)
        }
        assertTrue("Outlaw Camps must spawn active outlaw packs", campMonsters.size >= 10)
        assertTrue(
            "Outlaw Camps must spawn dedicated OUTLAW_BANDIT enemies",
            campMonsters.any { it.type == MonsterType.OUTLAW_BANDIT }
        )
        assertTrue(
            "Outlaw Camps must spawn dedicated OUTLAW_ROGUE enemies",
            campMonsters.any { it.type == MonsterType.OUTLAW_ROGUE }
        )
        campMonsters.forEach { m ->
            assertTrue(
                "Outlaw Camp ${m.type} at (${m.x},${m.y}) level ${m.level} must stay in Lv 12..20",
                m.level in 12..20
            )
            assertTrue(
                "Outlaw Camp ${m.type} must belong to HEARTLAND_OUTLAW_CAMP pool",
                BiomeType.HEARTLAND_OUTLAW_CAMP.containsSpecies(m.type)
            )
        }

        // 4. Both Rookvale-linked underground floors (Floor -1: Lv 6-12, Floor -2: Lv 13-20) strictly match Rookvale Lv 1..20!
        val floor1Monsters = allMonsters.filter { it.floor == -1 }
        val floor2Monsters = allMonsters.filter { it.floor == -2 }
        assertTrue("Floor -1 must have monsters", floor1Monsters.isNotEmpty())
        assertTrue("Floor -2 must have monsters", floor2Monsters.isNotEmpty())
        floor1Monsters.forEach { m ->
            assertTrue(
                "Floor -1 ${m.type} level ${m.level} must lie strictly within Lv 6..12",
                m.level in 6..12
            )
        }
        floor2Monsters.forEach { m ->
            assertTrue(
                "Floor -2 ${m.type} level ${m.level} must lie strictly within Lv 13..20",
                m.level in 13..20
            )
        }
    }
}
