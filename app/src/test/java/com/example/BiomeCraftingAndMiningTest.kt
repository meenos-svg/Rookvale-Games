package com.example

import com.example.game.engine.CraftingAndMiningEngine
import com.example.game.engine.EquipmentProcEngine
import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.BiomeEquipmentClass
import com.example.game.model.CraftingStationType
import com.example.game.model.EquippedGear
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemCategory
import com.example.game.model.ItemStack
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.OreVeinKind
import com.example.game.model.PlayerSkills
import com.example.game.model.ProcBonusEffect
import com.example.game.model.ProcVisualStyle
import com.example.game.model.RecipeCatalog
import com.example.game.model.SkillProgress
import com.example.game.model.Vocation
import com.example.game.world.WorldBiomeBlueprint
import com.example.game.world.WorldMaps
import com.example.game.world.WorldPinCategory
import com.example.ui.screens.MaterialPalettes
import com.example.ui.screens.PaperDollAppearance
import com.example.ui.screens.WorldMapViewportCamera
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Biome Equipment, Location-Locked Crafting Stalls, 9 Ore Veins,
 * 6 Tiered Pickaxes, and Recipe Codex expansion.
 */
class BiomeCraftingAndMiningTest {

    @Test
    fun starterBronzePickaxeAndAllSixTieredPickaxesEnforceTierMiningLevelAndSpeedBonus() {
        val fresh = GameEngine.createNewWorld("Roland")
        assertTrue("New character must start with Bronze Pickaxe", fresh.toolBag.any { it.itemId == "bronze_pickaxe" })

        val expectedPickaxes = listOf(
            Triple("bronze_pickaxe", 1, 1),
            Triple("bog_iron_pickaxe", 2, 10),
            Triple("tempered_steel_pickaxe", 3, 20),
            Triple("vesperite_tipped_pickaxe", 4, 30),
            Triple("glacier_forged_pickaxe", 5, 42),
            Triple("cinderite_core_pickaxe", 6, 52)
        )
        for ((id, tier, reqMining) in expectedPickaxes) {
            val def = ItemCatalog.get(id)
            assertEquals(ItemCategory.TOOL_PICKAXE, def.category)
            assertEquals("Pickaxe tier mismatch for $id", tier, def.pickaxeTier)
            assertEquals("Required mining level mismatch for $id", reqMining, def.requiredMiningLevel)
        }

        // All 9 ore veins spawn in the world
        val spawnedKinds = fresh.oreNodes.values.map { it.kind }.toSet()
        for (kind in OreVeinKind.entries) {
            assertTrue("OreVeinKind.${kind.name} must spawn in world oreNodes", kind in spawnedKinds)
        }

        // Dual Pickaxe Tier + Mining skill enforcement on Mire-Silver (requires T3 + Mining 20)
        val mireNode = fresh.oreNodes.entries.first { it.value.kind == OreVeinKind.MIRE_SILVER }
        val (mFloor, mx, my) = mireNode.key

        // Case 1: High Mining (25) but only T1 Bronze Pickaxe -> blocked by pickaxe tier
        val lowTierState = fresh.copy(
            floor = mFloor,
            playerX = mx - 1,
            playerY = my,
            skills = fresh.skills.copy(mining = SkillProgress(25, 0.0)),
            toolBag = listOf(ItemStack("bronze_pickaxe", 1))
        )
        val blockedTier = CraftingAndMiningEngine.mineOreNode(lowTierState, mx, my, nowMs = 5_000L)
        assertEquals(0, blockedTier.itemCount("mire_silver_ore"))
        assertTrue(blockedTier.statusLog.last().contains("Tier 3", ignoreCase = true))

        // Case 2: T3 Tempered Steel Pickaxe in backpack (not toolBag!) but Mining 15 < 20 -> blocked by Mining skill
        val lowSkillState = fresh.copy(
            floor = mFloor,
            playerX = mx - 1,
            playerY = my,
            skills = fresh.skills.copy(mining = SkillProgress(15, 0.0)),
            toolBag = listOf(ItemStack("bronze_pickaxe", 1)),
            backpack = listOf(ItemStack("tempered_steel_pickaxe", 1))
        )
        val blockedSkill = CraftingAndMiningEngine.mineOreNode(lowSkillState, mx, my, nowMs = 6_000L)
        assertEquals(0, blockedSkill.itemCount("mire_silver_ore"))
        assertTrue(blockedSkill.statusLog.last().contains("Mining level 20", ignoreCase = true))

        // Case 3: Both Mining 20+ and T3 pickaxe in Backpack -> auto-uses best valid pickaxe and mines ore
        val validState = lowSkillState.copy(skills = fresh.skills.copy(mining = SkillProgress(20, 0.0)))
        assertEquals("tempered_steel_pickaxe", validState.bestValidPickaxeFor(20)?.id)
        var mined = validState
        var swingTime = 7_000L
        repeat(12) {
            if (mined.itemCount("mire_silver_ore") == 0) {
                mined = CraftingAndMiningEngine.mineOreNode(mined, mx, my, nowMs = swingTime)
                swingTime += 1_000L
            }
        }
        assertTrue("Player must mine Mire-Silver Ore with valid T3 pickaxe", mined.itemCount("mire_silver_ore") >= 1)

        // Higher-tier pickaxes swing faster on lower-tier veins
        val sameTierCd = CraftingAndMiningEngine.swingCooldownMs(pickaxeTier = 1, veinRequiredTier = 1)
        val highTierOnCopperCd = CraftingAndMiningEngine.swingCooldownMs(pickaxeTier = 6, veinRequiredTier = 1)
        assertTrue(
            "Tier 6 pickaxe ($highTierOnCopperCd ms) must swing significantly faster on T1 rock than T1 pickaxe ($sameTierCd ms)",
            highTierOnCopperCd < sameTierCd
        )
    }

    @Test
    fun fourBiomeEquipmentClassificationsCoverAllSevenSlotsProcsPoisonResistAndPaperDoll() {
        val mireGear = listOf(
            "bog_steel_sabre",
            "bog_steel_waraxe",
            "bog_steel_mace",
            "mire_woven_longbow",
            "mire_woven_staff",
            "bog_steel_shield",
            "mire_woven_brigandine"
        )
        val vesperiteGear = listOf(
            "vesperite_longblade",
            "vesperite_cleaver",
            "vesperite_warmaul",
            "vesperite_recurve_bow",
            "vesperite_conduit_staff",
            "vesperite_bulwark",
            "vesperite_aegis_mail"
        )
        val rimeGear = listOf(
            "rime_plate_longsword",
            "frostpeak_battleaxe",
            "frostpeak_warhammer",
            "frostpeak_frostbow",
            "glacier_quartz_staff",
            "rime_plate_aegis",
            "rime_plate_cuirass"
        )
        val cinderiteGear = listOf(
            "cinderite_greatsword",
            "cinderite_greataxe",
            "cinderite_worldbreaker",
            "cinderite_ashen_bow",
            "cinderite_soulspire",
            "cinderite_bastion",
            "cinderite_warplate"
        )

        fun verifyClass(ids: List<String>, expectedClass: BiomeEquipmentClass, minLv: Int) {
            assertEquals("Must cover all 7 slots for $expectedClass", 7, ids.size)
            ids.forEach { id ->
                val def = ItemCatalog.get(id)
                assertEquals("Biome class mismatch for $id", expectedClass, def.biomeClass)
                assertTrue("$id must require Level >= $minLv", def.requiredLevel >= minLv)
                assertNotNull("$id must define an automatic combat proc", def.proc)
            }
        }

        verifyClass(mireGear, BiomeEquipmentClass.MIRE_WOVEN, 20)
        verifyClass(vesperiteGear, BiomeEquipmentClass.VESPERITE_SUNSCORCH, 28)
        verifyClass(rimeGear, BiomeEquipmentClass.RIME_PLATE, 36)
        verifyClass(cinderiteGear, BiomeEquipmentClass.CINDERITE_ASHEN, 45)

        // Mire-Woven armor & Bog-Steel shield grant poison resistance and reduce poison tick damage
        val targe = ItemCatalog.get("bog_steel_shield")
        val brig = ItemCatalog.get("mire_woven_brigandine")
        assertTrue(targe.poisonResistPercent > 0)
        assertTrue(brig.poisonResistPercent > 0)
        val mireEq = EquippedGear(shieldId = "bog_steel_shield", armorId = "mire_woven_brigandine")
        assertEquals(targe.poisonResistPercent + brig.poisonResistPercent, mireEq.totalPoisonResistPercent())

        val poisonedBase = GameEngine.createNewWorld("Roland").copy(
            floor = -1,
            playerX = 14,
            playerY = 14,
            hp = 400,
            maxHp = 400,
            poisonTicks = 2,
            monsters = emptyList()
        )
        val unresistedTick = GameEngine.processCombatTick(poisonedBase.copy(equipment = EquippedGear()), 10_000L)
        val resistedTick = GameEngine.processCombatTick(poisonedBase.copy(equipment = mireEq), 10_000L)
        assertTrue(
            "Mire-Woven/Bog-Steel poison resistance must reduce poison tick damage (${resistedTick.hp} vs ${unresistedTick.hp})",
            resistedTick.hp > unresistedTick.hp
        )

        // Mire-Woven ACID_SPLASH + VENOM_DOT and Rime-Plate FROST_SHATTER + FROST_SHATTER_SLOW trigger cleanly
        val sabreProc = ItemCatalog.get("bog_steel_sabre").proc!!
        assertEquals(ProcVisualStyle.ACID_SPLASH, sabreProc.visualStyle)
        assertEquals(ProcBonusEffect.VENOM_DOT, sabreProc.bonusEffect)

        val rimeProc = ItemCatalog.get("rime_plate_longsword").proc!!
        assertEquals(ProcVisualStyle.FROST_SHATTER, rimeProc.visualStyle)
        assertEquals(ProcBonusEffect.FROST_SHATTER_SLOW, rimeProc.bonusEffect)

        val foes = listOf(
            MonsterEntity(1, MonsterType.SWAMP_TROLL, -1, 15, 14, 15, 14, 300, 300),
            MonsterEntity(2, MonsterType.SWAMP_TROLL, -1, 15, 15, 15, 15, 300, 300)
        )
        val frostArena = poisonedBase.copy(
            equipment = EquippedGear(weaponId = "rime_plate_longsword"),
            monsters = foes
        )
        val alwaysTriggerRng = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = 0
        }
        val frostHit = EquipmentProcEngine.resolveAttackProcs(
            state = frostArena,
            primaryTarget = foes[0],
            baseHitDamage = 60,
            nowMs = 20_000L,
            rng = alwaysTriggerRng
        )
        assertTrue("Rime-Plate proc must emit FROST_SHATTER AOE effect", frostHit.procAoeEffects.any { it.visualStyle == ProcVisualStyle.FROST_SHATTER })
        assertTrue("Rime-Plate proc must slow nearby monsters", frostHit.monsters.any { it.slowTicks >= 4 })

        // Paper-doll appearance resolves distinct biome palettes for Mire-Woven and Rime-Plate
        val mireDoll = PaperDollAppearance.fromItemIds(
            vocation = Vocation.KNIGHT,
            tunicColorIndex = 0,
            hairColorIndex = 0,
            weaponId = "bog_steel_sabre",
            offhandId = "bog_steel_shield",
            armorId = "mire_woven_brigandine",
            helmetId = null
        )
        assertEquals(MaterialPalettes.MIRE_WOVEN, mireDoll.weaponPalette)
        assertEquals(MaterialPalettes.MIRE_WOVEN, mireDoll.armorPalette)

        val rimeDoll = PaperDollAppearance.fromItemIds(
            vocation = Vocation.KNIGHT,
            tunicColorIndex = 0,
            hairColorIndex = 0,
            weaponId = "rime_plate_longsword",
            offhandId = "rime_plate_aegis",
            armorId = "rime_plate_cuirass",
            helmetId = null
        )
        assertEquals(MaterialPalettes.RIME_PLATE, rimeDoll.weaponPalette)
        assertEquals(MaterialPalettes.RIME_PLATE, rimeDoll.armorPalette)
    }

    @Test
    fun fiveBiomeStallsEnforcePhysicalLocationLockAndRevealOnZoomedWorldMap() {
        val surface = WorldMaps.getFloor(0)
        assertEquals(5, WorldBiomeBlueprint.biomeCraftingStalls.size)

        // Each of the 5 Biome Crafting Stalls exists on the overworld map and forges the next biome's pickaxe
        val expectedNextPickaxeByStation = mapOf(
            CraftingStationType.ROOKVALE_TOWN_FORGE to "tempered_steel_pickaxe",
            CraftingStationType.STILT_TOWN_MIRE_BENCH to "vesperite_tipped_pickaxe",
            CraftingStationType.SUNSCORCH_OASIS_SMELTERY to "glacier_forged_pickaxe",
            CraftingStationType.FROSTPEAK_GREAT_HEARTH to "cinderite_core_pickaxe",
            CraftingStationType.ASHEN_OBSIDIAN_FOUNDRY to "cinderite_core_pickaxe"
        )

        for (spot in WorldBiomeBlueprint.biomeCraftingStalls) {
            val actualTile = surface.tileAt(spot.x, spot.y)
            assertEquals(
                "Stall ${spot.station.displayName} must be placed at (${spot.x},${spot.y})",
                spot.station.stallTileType(),
                actualTile
            )
            val stationRecipes = RecipeCatalog.recipesForBiomeStation(spot.station)
            val nextPickId = expectedNextPickaxeByStation.getValue(spot.station)
            assertTrue(
                "${spot.station.displayName} must offer next-tier pickaxe recipe for $nextPickId",
                stationRecipes.any { it.outputItemId == nextPickId }
            )
        }

        // World Map Forge pins stay hidden at low zoom (1.0x) and reveal when zoomed in (>= 1.6x)
        val forgePins = WorldBiomeBlueprint.worldMapPins.filter { it.category == WorldPinCategory.FORGE }
        assertEquals(5, forgePins.size)
        val visibleAtOverview = WorldMapViewportCamera.filterVisiblePins(
            pins = WorldBiomeBlueprint.worldMapPins,
            mapZoom = 1.0f,
            showCities = true,
            showFerries = true,
            showDungeons = true
        ).filter { it.category == WorldPinCategory.FORGE }
        assertTrue("Forge pins must remain uncluttered at 1.0x zoom", visibleAtOverview.isEmpty())

        val visibleZoomedIn = WorldMapViewportCamera.filterVisiblePins(
            pins = WorldBiomeBlueprint.worldMapPins,
            mapZoom = 1.8f,
            showCities = true,
            showFerries = true,
            showDungeons = true
        ).filter { it.category == WorldPinCategory.FORGE }
        assertEquals("All 5 Biome Crafting Stall pins must reveal when zoomed in", 5, visibleZoomedIn.size)

        // Location lock test: attempting to craft Rime-Plate Longsword while in Rookvale (even via Recipe Codex) is blocked
        val rimeRecipe = RecipeCatalog.findById("forge_rime_plate_longsword")!!
        val ingredients = rimeRecipe.ingredients.map { ItemStack(it.itemId, it.count) }
        val inRookvale = GameEngine.createNewWorld("Roland").copy(
            floor = 0,
            playerX = WorldMaps.TEMPLE_SPAWN_X,
            playerY = WorldMaps.TEMPLE_SPAWN_Y,
            activePanel = ActivePanel.RECIPE_CODEX,
            backpack = ingredients
        )
        val blockedRime = CraftingAndMiningEngine.executeRecipe(inRookvale, rimeRecipe.id, nowMs = 3_000L)
        assertEquals("Must not craft Rime-Plate gear outside Frostpeak Citadel Great Hearth", 0, blockedRime.itemCount("rime_plate_longsword"))
        assertEquals(
            "Must be at the Frostpeak Citadel Great Hearth to forge Rime-Plate gear",
            blockedRime.statusLog.last()
        )

        // Standing within 2 tiles of Frostpeak Citadel Great Hearth succeeds!
        val frostStall = WorldBiomeBlueprint.biomeCraftingStalls.first { it.station == CraftingStationType.FROSTPEAK_GREAT_HEARTH }
        val atFrostStall = inRookvale.copy(
            playerX = frostStall.x,
            playerY = frostStall.y + 1,
            activePanel = ActivePanel.BIOME_STALL,
            activeBiomeStall = CraftingStationType.FROSTPEAK_GREAT_HEARTH
        )
        val forgedRime = CraftingAndMiningEngine.executeRecipe(atFrostStall, rimeRecipe.id, nowMs = 4_000L)
        assertEquals(1, forgedRime.itemCount("rime_plate_longsword"))
    }
}
