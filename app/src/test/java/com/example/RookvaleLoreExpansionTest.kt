package com.example

import com.example.game.engine.CraftingAndMiningEngine
import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.BossSpecialKind
import com.example.game.model.Direction
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemCategory
import com.example.game.model.ItemStack
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterLootTable
import com.example.game.model.MonsterType
import com.example.game.model.OreVeinKind
import com.example.game.model.RecipeCatalog
import com.example.game.model.TileType
import com.example.game.model.Vocation
import com.example.game.multiplayer.MultiplayerSanitizer
import com.example.game.world.WorldBiomeBlueprint
import com.example.game.world.WorldMaps
import com.example.ui.screens.MaterialPalettes
import com.example.ui.screens.PaperDollAppearance
import com.example.ui.screens.WeaponVisualKind
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies Rookvale's original lore expansion:
 * - 5 original ore tiers (Copper, Tin, Bog Iron in outskirts; Vesperite & Cinderite in dungeons)
 * - 10 Tier 4 Vesperite & Tier 5 Cinderite weapons across all 5 combat classes (craftable + boss drops + 3D paper-doll)
 * - 2 isolated high-difficulty frontier camps far from Rookvale City + Floor -5 Grimmaw Quarry & Floor -6 Gorek Labyrinth
 * - Strict difficulty scaling above original Rookvale & classic dungeons, zero generic fantasy names, legacy save compatibility
 */
class RookvaleLoreExpansionTest {

    @Test
    fun zeroGenericFantasyNamesExistAcrossOresItemsMonstersAndFloors() {
        val forbidden = listOf("mithril", "adamantite", "runite", "cyclops", "minotaur")
        fun assertOriginal(label: String, text: String) {
            val lower = text.lowercase()
            for (word in forbidden) {
                assertFalse("$label must not contain generic name '$word': $text", lower.contains(word))
            }
        }

        OreVeinKind.entries.forEach { assertOriginal("OreVeinKind", it.displayName) }
        ItemCatalog.all.values.forEach { assertOriginal("ItemCatalog(${it.id})", it.name) }
        MonsterType.entries.forEach {
            assertOriginal("MonsterType(${it.name})", it.displayName)
            assertOriginal("MonsterBossTitle(${it.name})", it.bossTitle)
        }
        WorldMaps.floors.values.forEach { assertOriginal("WorldFloor(${it.floorId})", it.name) }
    }

    @Test
    fun fiveOriginalOreTiersSpawnAcrossOutskirtsAndDeepDungeonsAndSmeltCleanly() {
        assertEquals(9, OreVeinKind.entries.size)
        val state = GameEngine.createNewWorld("Roland")
        val heartlandKinds = state.oreNodes
            .filterKeys { (f, x, y) -> f == 0 && WorldBiomeBlueprint.isInRookvaleHeartland(x, y) }
            .values.map { it.kind }.toSet()
        val overworldKinds = state.oreNodes.filterKeys { it.first == 0 }.values.map { it.kind }.toSet()
        assertTrue("Rookvale Heartland outskirts must have Copper", OreVeinKind.COPPER in heartlandKinds)
        assertTrue("Rookvale Heartland outskirts must have Tin", OreVeinKind.TIN in heartlandKinds)
        assertTrue("Rookvale Heartland outskirts must have Bog Iron", OreVeinKind.BOG_IRON in heartlandKinds)
        assertFalse("Vesperite must not spawn in starter Rookvale Heartland", OreVeinKind.VESPERITE in heartlandKinds)
        assertFalse("Cinderite must not spawn in starter Rookvale Heartland", OreVeinKind.CINDERITE in heartlandKinds)
        assertTrue("High-tier outer overland biomes (Badlands/Frostpeak) must hold Vesperite", OreVeinKind.VESPERITE in overworldKinds)
        assertTrue("Endgame overland Ashen/Blight wastes must hold Cinderite", OreVeinKind.CINDERITE in overworldKinds)

        // All overworld ore veins must sit away from Rookvale central city square
        val overworldNodes = state.oreNodes.filterKeys { it.first == 0 }.keys
        for ((_, ox, oy) in overworldNodes) {
            val distFromCity = hypot((ox - WorldMaps.TEMPLE_SPAWN_X).toDouble(), (oy - WorldMaps.TEMPLE_SPAWN_Y).toDouble())
            assertTrue("Overworld ore at ($ox,$oy) must be in outskirts away from city square (dist=$distFromCity)", distFromCity >= 22.0)
        }

        val f1Kinds = state.oreNodes.filterKeys { it.first == -1 }.values.map { it.kind }.toSet()
        val f2Kinds = state.oreNodes.filterKeys { it.first == -2 }.values.map { it.kind }.toSet()
        val f5Kinds = state.oreNodes.filterKeys { it.first == -5 }.values.map { it.kind }.toSet()
        val f6Kinds = state.oreNodes.filterKeys { it.first == -6 }.values.map { it.kind }.toSet()

        assertTrue("Floor -1 must contain Vesperite veins", OreVeinKind.VESPERITE in f1Kinds)
        assertTrue("Floor -5 Grimmaw Quarry must contain Vesperite veins", OreVeinKind.VESPERITE in f5Kinds)
        assertTrue("Floor -2 must contain Cinderite veins", OreVeinKind.CINDERITE in f2Kinds)
        assertTrue("Floor -6 Gorek Labyrinth must contain Cinderite veins", OreVeinKind.CINDERITE in f6Kinds)

        // Verify Vesperite & Cinderite smelting recipes at their respective biome stalls
        val vesperiteStall = WorldBiomeBlueprint.biomeCraftingStalls.first { it.station.name == "SUNSCORCH_OASIS_SMELTERY" }
        val cinderiteStall = WorldBiomeBlueprint.biomeCraftingStalls.first { it.station.name == "ASHEN_OBSIDIAN_FOUNDRY" }
        val smeltingState = state.copy(
            floor = 0,
            playerX = vesperiteStall.x,
            playerY = vesperiteStall.y + 1,
            activePanel = ActivePanel.BIOME_STALL,
            backpack = listOf(ItemStack("vesperite_ore", 2), ItemStack("cinderite_ore", 2))
        )
        val afterVesperite = CraftingAndMiningEngine.executeRecipe(smeltingState, "smelt_vesperite", 1000L)
        assertEquals(1, afterVesperite.itemCount("vesperite_bar"))
        val atAshenFoundry = afterVesperite.copy(
            playerX = cinderiteStall.x,
            playerY = cinderiteStall.y + 1
        )
        val afterCinderite = CraftingAndMiningEngine.executeRecipe(atAshenFoundry, "smelt_cinderite", 2000L)
        assertEquals(1, afterCinderite.itemCount("cinderite_bar"))
    }

    @Test
    fun allTenVesperiteAndCinderiteWeaponsAreCraftableDroppableAndRenderInPaperDoll() {
        val tier4And5Expected = mapOf(
            "vesperite_longblade" to (WeaponVisualKind.SWORD to MaterialPalettes.VESPERITE),
            "cinderite_greatsword" to (WeaponVisualKind.CLAYMORE to MaterialPalettes.CINDERITE),
            "vesperite_cleaver" to (WeaponVisualKind.AXE to MaterialPalettes.VESPERITE),
            "cinderite_greataxe" to (WeaponVisualKind.AXE to MaterialPalettes.CINDERITE),
            "vesperite_warmaul" to (WeaponVisualKind.CLUB to MaterialPalettes.VESPERITE),
            "cinderite_worldbreaker" to (WeaponVisualKind.CLUB to MaterialPalettes.CINDERITE),
            "vesperite_recurve_bow" to (WeaponVisualKind.BOW to MaterialPalettes.VESPERITE),
            "cinderite_ashen_bow" to (WeaponVisualKind.BOW to MaterialPalettes.CINDERITE),
            "vesperite_conduit_staff" to (WeaponVisualKind.STAFF to MaterialPalettes.VESPERITE),
            "cinderite_soulspire" to (WeaponVisualKind.STAFF to MaterialPalettes.CINDERITE)
        )

        val allRecipes = RecipeCatalog.forgingRecipes + RecipeCatalog.fletchingRecipes + RecipeCatalog.runecraftingRecipes
        val kragorDrops = MonsterLootTable.entries.getValue(MonsterType.KRAGOR_THE_STONE_EYED).weightedDrops.map { it.itemId }.toSet()
        val vaelgorDrops = MonsterLootTable.entries.getValue(MonsterType.VAELGOR_LABYRINTH_WARDEN).weightedDrops.map { it.itemId }.toSet()

        for ((weaponId, expectedVisual) in tier4And5Expected) {
            val def = ItemCatalog.all[weaponId]
            assertNotNull("Weapon $weaponId must exist in ItemCatalog", def)
            assertTrue(
                "Weapon $weaponId must have a crafting recipe",
                allRecipes.any { it.outputItemId == weaponId }
            )
            if (weaponId.startsWith("vesperite_")) {
                assertTrue("Kragor must have $weaponId in weighted loot table", weaponId in kragorDrops)
            } else {
                assertTrue("Vaelgor must have $weaponId in weighted loot table", weaponId in vaelgorDrops)
            }
            val paperDoll = PaperDollAppearance.fromItemIds(
                vocation = Vocation.KNIGHT,
                tunicColorIndex = 0,
                hairColorIndex = 0,
                weaponId = weaponId,
                offhandId = null,
                armorId = null,
                helmetId = null
            )
            assertEquals("PaperDoll weaponKind mismatch for $weaponId", expectedVisual.first, paperDoll.weaponKind)
            assertEquals("PaperDoll weaponPalette mismatch for $weaponId", expectedVisual.second, paperDoll.weaponPalette)
        }

        // Tier 5 Cinderite weapons must outclass Tier 4 Vesperite counterparts
        assertTrue(ItemCatalog.get("cinderite_greatsword").attack > ItemCatalog.get("vesperite_longblade").attack)
        assertTrue(ItemCatalog.get("cinderite_greataxe").attack > ItemCatalog.get("vesperite_cleaver").attack)
        assertTrue(ItemCatalog.get("cinderite_worldbreaker").attack > ItemCatalog.get("vesperite_warmaul").attack)
        assertTrue(ItemCatalog.get("cinderite_ashen_bow").attack > ItemCatalog.get("vesperite_recurve_bow").attack)
        assertTrue(ItemCatalog.get("cinderite_soulspire").attack > ItemCatalog.get("vesperite_conduit_staff").attack)
    }

    @Test
    fun grimmawAndGorekLiveInTwoIsolatedHighDifficultyFrontierCampsAndDedicatedDungeons() {
        val spawns = WorldMaps.initialMonsters()
        val grimmawTypes = setOf(
            MonsterType.GRIMMAW_QUARRYMAN,
            MonsterType.GRIMMAW_ROCK_HURLER,
            MonsterType.GRIMMAW_CRUSHER,
            MonsterType.KRAGOR_THE_STONE_EYED
        )
        val gorekTypes = setOf(
            MonsterType.GOREK_RAIDER,
            MonsterType.GOREK_PHALANX,
            MonsterType.GOREK_HEX_CALLER,
            MonsterType.VAELGOR_LABYRINTH_WARDEN
        )

        // Overworld Grimmaw & Gorek live exclusively in outer-continent biomes (never in Lv 1-20 Rookvale Heartland)
        val ox = WorldMaps.ROOKVALE_ORIGIN_X
        val oy = WorldMaps.ROOKVALE_ORIGIN_Y
        val overworldGrimmaw = spawns.filter { it.floor == 0 && it.type in grimmawTypes }
        val overworldGorek = spawns.filter { it.floor == 0 && it.type in gorekTypes }
        val heartlandGrimmaw = overworldGrimmaw.filter { it.x in ox until (ox + 400) && it.y in oy until (oy + 400) }
        val heartlandGorek = overworldGorek.filter { it.x in ox until (ox + 400) && it.y in oy until (oy + 400) }
        val dungeonGrimmaw = spawns.filter { it.floor == -5 && it.type in grimmawTypes }
        val dungeonGorek = spawns.filter { it.floor == -6 && it.type in gorekTypes }
        assertEquals("Grimmaw must NOT spawn inside Lv 1-20 Rookvale Heartland", 0, heartlandGrimmaw.size)
        assertEquals("Gorek must NOT spawn inside Lv 1-20 Rookvale Heartland", 0, heartlandGorek.size)
        assertTrue("Grimmaw frontier biomes must have surface defenders", overworldGrimmaw.size >= 4)
        assertTrue("Gorek frontier biomes must have surface defenders", overworldGorek.size >= 4)
        assertTrue("Grimmaw Quarry must have interior monsters + boss", dungeonGrimmaw.size >= 6)
        assertTrue("Gorek Labyrinth must have interior monsters + boss", dungeonGorek.size >= 6)

        val surfaceFloor = WorldMaps.getFloor(0)
        for (m in overworldGrimmaw) {
            val dCity = hypot((m.x - WorldMaps.TEMPLE_SPAWN_X).toDouble(), (m.y - WorldMaps.TEMPLE_SPAWN_Y).toDouble())
            assertTrue("Grimmaw frontier spawn (${m.x},${m.y}) must be far off from Rookvale City (d=$dCity)", dCity >= 180.0)
            assertTrue("Grimmaw frontier spawn (${m.x},${m.y}) must be walkable", surfaceFloor.tileAt(m.x, m.y).walkable)
        }
        for (m in overworldGorek) {
            val dCity = hypot((m.x - WorldMaps.TEMPLE_SPAWN_X).toDouble(), (m.y - WorldMaps.TEMPLE_SPAWN_Y).toDouble())
            assertTrue("Gorek frontier spawn (${m.x},${m.y}) must be far off from Rookvale City (d=$dCity)", dCity >= 180.0)
            assertTrue("Gorek frontier spawn (${m.x},${m.y}) must be walkable", surfaceFloor.tileAt(m.x, m.y).walkable)
        }

        // Neither clan appears in classic Floors -1..-4
        assertTrue(
            "Grimmaw & Gorek must not spawn in original classic Floors -1..-4",
            spawns.none { it.floor in -4..-1 && (it.type in grimmawTypes || it.type in gorekTypes) }
        )

        // Dedicated dungeons Floor -5 & Floor -6 have walkable bidirectional overworld portals and valid multiplayer floors
        assertTrue(MultiplayerSanitizer.isValidFloor(-5))
        assertTrue(MultiplayerSanitizer.isValidFloor(-6))

        val toF5 = WorldMaps.portals.firstOrNull { it.fromFloor == 0 && it.toFloor == -5 }
        val fromF5 = WorldMaps.portals.firstOrNull { it.fromFloor == -5 && it.toFloor == 0 }
        val toF6 = WorldMaps.portals.firstOrNull { it.fromFloor == 0 && it.toFloor == -6 }
        val fromF6 = WorldMaps.portals.firstOrNull { it.fromFloor == -6 && it.toFloor == 0 }
        assertNotNull("Overworld entrance portal to Floor -5 Grimmaw Quarry must exist", toF5)
        assertNotNull("Exit portal from Floor -5 Grimmaw Quarry to surface must exist", fromF5)
        assertNotNull("Overworld entrance portal to Floor -6 Gorek Labyrinth must exist", toF6)
        assertNotNull("Exit portal from Floor -6 Gorek Labyrinth to surface must exist", fromF6)

        assertTrue(WorldMaps.getFloor(-5).tileAt(toF5!!.toX, toF5.toY).walkable)
        assertTrue(WorldMaps.getFloor(-6).tileAt(toF6!!.toX, toF6.toY).walkable)

        // Difficulty strictly greater than original Rookvale & classic dungeons (Floors -1..-4)
        val classicRegularMaxThreat = MonsterType.entries.filter { it.tier <= 4 && !it.isBoss }.maxOf { it.threatLevel }
        val classicBossMaxHp = MonsterType.entries.filter { it.tier <= 4 && it.isBoss }.maxOf { it.maxHp }
        assertTrue(
            "Even entry Grimmaw Quarryman threat must exceed all classic non-boss dungeon foes",
            MonsterType.GRIMMAW_QUARRYMAN.threatLevel > classicRegularMaxThreat
        )
        assertTrue(
            "Kragor HP (${MonsterType.KRAGOR_THE_STONE_EYED.maxHp}) must exceed all classic Floor -1..-4 bosses ($classicBossMaxHp)",
            MonsterType.KRAGOR_THE_STONE_EYED.maxHp > classicBossMaxHp
        )
        assertTrue(
            "Vaelgor HP (${MonsterType.VAELGOR_LABYRINTH_WARDEN.maxHp}) must exceed Kragor HP",
            MonsterType.VAELGOR_LABYRINTH_WARDEN.maxHp > MonsterType.KRAGOR_THE_STONE_EYED.maxHp
        )
    }

    @Test
    fun legacySaveItemIdsAndWireNamesMigrateCleanlyAndBossSpecialsTelegraph() {
        // Legacy ore IDs in existing saves seamlessly upgrade to the 5 Rookvale ores
        val legacySave = GameEngine.toSaveData(GameEngine.createNewWorld("Aldric")).copy(
            backpack = listOf(
                ItemStack("ochre_nugget", 4),
                ItemStack("bogstone_shard", 3),
                ItemStack("cinder_pitch", 2),
                ItemStack("pale_starstone", 5)
            ),
            depot = listOf(ItemStack("pale_starstone", 2))
        )
        val hydrated = GameEngine.fromSaveData(legacySave)
        assertEquals(4, hydrated.itemCount("copper_ore"))
        assertEquals(3, hydrated.itemCount("bog_iron_ore"))
        assertEquals(2, hydrated.itemCount("vesperite_ore"))
        assertEquals(5, hydrated.itemCount("cinderite_ore"))
        assertEquals(2, hydrated.depot.first { it.itemId == "cinderite_ore" }.count)

        // Legacy multiplayer monster wire names resolve safely to Grimmaw/Gorek
        assertEquals(MonsterType.GOREK_RAIDER, MonsterType.fromWireName("MINOTAUR"))
        assertEquals(MonsterType.GRIMMAW_CRUSHER, MonsterType.fromWireName("CYCLOPS"))

        // Kragor (QUARRY_QUAKE) & Vaelgor (LABYRINTH_PYRE) telegraph & detonate AoE danger zones
        val kragor = MonsterEntity(
            id = 5001,
            type = MonsterType.KRAGOR_THE_STONE_EYED,
            floor = -5,
            x = 24,
            y = 14,
            spawnX = 24,
            spawnY = 14,
            hp = MonsterType.KRAGOR_THE_STONE_EYED.maxHp,
            maxHp = MonsterType.KRAGOR_THE_STONE_EYED.maxHp,
            aoeCooldownTicks = 0
        )
        assertEquals(BossSpecialKind.QUARRY_QUAKE, kragor.type.specialKind)
        val kragorArena = GameEngine.createNewWorld("Roland").copy(
            floor = -5,
            playerX = 24,
            playerY = 16,
            hp = 800,
            maxHp = 800,
            monsters = listOf(kragor)
        )
        val kragorTelegraph = GameEngine.processCombatTick(kragorArena, nowMs = 1000L, isFloorHost = true)
        assertTrue(
            "Kragor must telegraph Quarry Quake AoE tiles",
            kragorTelegraph.monsters.first { it.id == 5001 }.pendingAoeTiles.isNotEmpty()
        )

        val vaelgor = MonsterEntity(
            id = 6001,
            type = MonsterType.VAELGOR_LABYRINTH_WARDEN,
            floor = -6,
            x = 24,
            y = 24,
            spawnX = 24,
            spawnY = 24,
            hp = MonsterType.VAELGOR_LABYRINTH_WARDEN.maxHp,
            maxHp = MonsterType.VAELGOR_LABYRINTH_WARDEN.maxHp,
            aoeCooldownTicks = 0
        )
        assertEquals(BossSpecialKind.LABYRINTH_PYRE, vaelgor.type.specialKind)
        val vaelgorArena = GameEngine.createNewWorld("Roland").copy(
            floor = -6,
            playerX = 24,
            playerY = 26,
            hp = 900,
            maxHp = 900,
            monsters = listOf(vaelgor)
        )
        val vaelgorTelegraph = GameEngine.processCombatTick(vaelgorArena, nowMs = 1000L, isFloorHost = true)
        assertTrue(
            "Vaelgor must telegraph Labyrinth Pyre AoE tiles",
            vaelgorTelegraph.monsters.first { it.id == 6001 }.pendingAoeTiles.isNotEmpty()
        )
    }
}
