package com.example

import com.example.game.engine.CraftingAndMiningEngine
import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.CorpseEntity
import com.example.game.model.CURRENT_MAP_VERSION
import com.example.game.model.EquippedGear
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemCategory
import com.example.game.model.ItemStack
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.ORACLE_MIN_LEVEL
import com.example.game.model.OreVeinKind
import com.example.game.model.PlayerSaveData
import com.example.game.model.PlayerSkills
import com.example.game.model.SkillProgress
import com.example.game.model.TOOL_BAG_WEIGHT_MULTIPLIER
import com.example.game.model.TileType
import com.example.game.model.Vocation
import com.example.game.world.NpcRole
import com.example.game.world.WorldMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun newAdventurerStartsBlankSlateWithIdenticalStatsAndToolsInToolBag() {
        val heroA = GameEngine.createNewWorld("Roland", tunicColorIndex = 2, hairColorIndex = 4)
        val heroB = GameEngine.createNewWorld("Lyra", tunicColorIndex = 5, hairColorIndex = 1)

        assertEquals(Vocation.ADVENTURER, heroA.vocation)
        assertEquals(Vocation.ADVENTURER, heroB.vocation)
        assertEquals(2, heroA.tunicColorIndex)
        assertEquals(4, heroA.hairColorIndex)

        // Identical balanced stats regardless of cosmetic colors
        assertEquals(150, heroA.maxHp)
        assertEquals(55, heroA.maxMp)
        assertEquals(1500.0, heroA.maxCapOz, 0.01)
        assertEquals(heroA.maxHp, heroB.maxHp)
        assertEquals(heroA.maxMp, heroB.maxMp)
        assertEquals(heroA.maxCapOz, heroB.maxCapOz, 0.01)

        // All 6 skills start at 10
        assertEquals(10, heroA.skills.melee.level)
        assertEquals(10, heroA.skills.distance.level)
        assertEquals(10, heroA.skills.shielding.level)
        assertEquals(10, heroA.skills.magic.level)
        assertEquals(10, heroA.skills.fishing.level)
        assertEquals(10, heroA.skills.mining.level)

        // Sunbrass Dirk + Wooden Shield equipped; tools packed inside Tool Bag, not Main Bag
        assertEquals("sunbrass_dirk", heroA.equipment.weaponId)
        assertEquals("wooden_shield", heroA.equipment.shieldId)
        assertTrue(heroA.toolBag.any { it.itemId == "quarry_mattock" })
        assertTrue(heroA.toolBag.any { it.itemId == "rope" })
        assertTrue(heroA.toolBag.any { it.itemId == "fishing_rod" })
        assertFalse(heroA.backpack.any { ItemCatalog.get(it.itemId).isTool })
    }

    @Test
    fun toolBagHalvesWeightAutoRoutesToolsAndSurvivesDeath() {
        val hero = GameEngine.createNewWorld("Aldric")
        val rawToolWeight = hero.toolBag.sumOf { ItemCatalog.get(it.itemId).weightOz * it.count }
        assertEquals(rawToolWeight * TOOL_BAG_WEIGHT_MULTIPLIER, hero.toolBagWeightOz(), 0.01)

        // Buying a Rope auto-routes into toolBag
        val afterBuy = GameEngine.buyShopItem(hero.copy(gold = 500), "rope")
        assertEquals(2, afterBuy.toolBag.filter { it.itemId == "rope" }.sumOf { it.count })
        assertFalse(afterBuy.backpack.any { it.itemId == "rope" })

        // Forging a Tier 2 Sunbrass Pick at Anvil auto-routes into toolBag
        val readyForge = hero.copy(
            activePanel = ActivePanel.ANVIL,
            backpack = listOf(ItemStack("sunbrass_ingot", 2), ItemStack("brindle_hide", 1))
        )
        val afterForge = CraftingAndMiningEngine.executeRecipe(readyForge, "forge_sunbrass_pick", 1000L)
        assertTrue(afterForge.toolBag.any { it.itemId == "sunbrass_pick" })
        assertFalse(afterForge.backpack.any { it.itemId == "sunbrass_pick" })
        assertEquals("sunbrass_pick", afterForge.bestPickaxe()?.id)

        // On death, Main Bag drops into recoverable corpse while Tool Bag stays strapped safely
        val doomed = afterForge.copy(
            xp = 1000L,
            level = 4,
            floor = -1,
            playerX = 15,
            playerY = 15,
            hp = 1,
            poisonTicks = 3,
            backpack = listOf(ItemStack("health_potion", 2), ItemStack("spider_silk", 3))
        )
        val afterDeath = GameEngine.processCombatTick(doomed, 3000L)
        assertTrue("Tool Bag must remain strapped after death", afterDeath.toolBag.any { it.itemId == "sunbrass_pick" })
        assertTrue("Rope must remain in Tool Bag after death", afterDeath.toolBag.any { it.itemId == "rope" })
        assertTrue("Main Bag should be empty after dropping into corpse", afterDeath.backpack.isEmpty())
        val corpse = afterDeath.corpses.firstOrNull { it.isPlayerCorpse }
        assertNotNull(corpse)
        assertTrue(corpse!!.items.any { it.itemId == "spider_silk" && it.count == 3 })
        assertFalse("Corpse must not contain Tool Bag items", corpse.items.any { it.itemId == "sunbrass_pick" })
    }

    @Test
    fun oraclePlacedInTempleRejectsBelowLevel20PromotesAt20WithForwardOnlyClassicGrowth() {
        val oracleNpc = WorldMaps.npcs.firstOrNull { it.role == NpcRole.ORACLE }
        assertNotNull("The Oracle must exist in WorldMaps.npcs", oracleNpc)
        assertEquals(0, oracleNpc!!.floor)
        assertEquals(WorldMaps.ORACLE_X, oracleNpc.x)
        assertEquals(WorldMaps.ORACLE_Y, oracleNpc.y)

        // Adjacent tile inside Temple Sanctuary must be walkable
        val surface = WorldMaps.getFloor(0)
        assertTrue(surface.tileAt(WorldMaps.ORACLE_X - 1, WorldMaps.ORACLE_Y).walkable)

        // Below Level 20: Oracle ceremony promotion is rejected
        val lvl19 = GameEngine.createNewWorld("Aldric").copy(
            level = 19,
            maxHp = 330,
            maxMp = 235,
            maxCapOz = 670.0,
            skills = PlayerSkills(
                melee = SkillProgress(18, 40.0),
                distance = SkillProgress(14, 10.0),
                shielding = SkillProgress(19, 25.0),
                magic = SkillProgress(13, 5.0),
                fishing = SkillProgress(12, 0.0),
                mining = SkillProgress(16, 15.0)
            )
        )
        val rejected = GameEngine.promoteVocation(lvl19, Vocation.KNIGHT, 1000L)
        assertEquals(Vocation.ADVENTURER, rejected.vocation)

        // At Level 20: Oracle promotes permanently to Knight, keeping earned skills & current caps unchanged
        val lvl20 = lvl19.copy(
            level = ORACLE_MIN_LEVEL,
            xp = lvl19.xpForLevel(ORACLE_MIN_LEVEL),
            maxHp = 340,
            hp = 340,
            maxMp = 245,
            mp = 245,
            maxCapOz = GameWorldState.maxCapForLevel(ORACLE_MIN_LEVEL, Vocation.ADVENTURER)
        )
        val promoted = GameEngine.promoteVocation(lvl20, Vocation.KNIGHT, 2000L)
        assertEquals(Vocation.KNIGHT, promoted.vocation)
        assertEquals(ActivePanel.NONE, promoted.activePanel)
        // All 6 earned skills preserved
        assertEquals(18, promoted.skills.melee.level)
        assertEquals(14, promoted.skills.distance.level)
        assertEquals(19, promoted.skills.shielding.level)
        assertEquals(13, promoted.skills.magic.level)
        assertEquals(12, promoted.skills.fishing.level)
        assertEquals(16, promoted.skills.mining.level)
        // Forward-only HP/MP preserved, plus Knight's Level 20 Cap promotion surge
        assertEquals(340, promoted.maxHp)
        assertEquals(245, promoted.maxMp)
        assertEquals(GameWorldState.maxCapForLevel(ORACLE_MIN_LEVEL, Vocation.KNIGHT), promoted.maxCapOz, 0.01)

        // Re-promotion is blocked once chosen
        val secondAttempt = GameEngine.promoteVocation(promoted, Vocation.MAGE, 3000L)
        assertEquals(Vocation.KNIGHT, secondAttempt.vocation)

        // Next level up (20 -> 21) applies Knight's forward growth (+15 HP, +5 MP, +200 oz)
        val xpNeeded = promoted.xpForLevel(21) - promoted.xp
        val lvl21 = GameEngine.grantExperience(promoted, xpNeeded, promoted.playerX.toFloat(), promoted.playerY.toFloat(), 4000L)
        assertEquals(21, lvl21.level)
        assertEquals(340 + Vocation.KNIGHT.hpPerLevel, lvl21.maxHp)
        assertEquals(245 + Vocation.KNIGHT.mpPerLevel, lvl21.maxMp)
        assertEquals(promoted.maxCapOz + Vocation.KNIGHT.capPerLevel, lvl21.maxCapOz, 0.01)
    }

    @Test
    fun attackSkillsMultiplyDamageAndOffVocationTier34AppliesPenalty() {
        val base = GameEngine.createNewWorld("Roland")
        assertEquals(1.0, base.attackSkillMultiplier(10), 0.001)
        assertEquals(1.6, base.attackSkillMultiplier(20), 0.001)
        assertEquals(2.2, base.attackSkillMultiplier(30), 0.001)

        // Tier 1-2 weapons have no off-vocation penalty
        val dirk = ItemCatalog.get("sunbrass_dirk")
        assertEquals(1.0, base.weaponOffVocationMultiplier(dirk), 0.001)

        // Tier 3-4 weapons apply -25% penalty (0.75x) off-vocation and 1.0x on matching vocation
        val claymore = ItemCatalog.get("wyrmtooth_claymore")
        assertEquals(Vocation.KNIGHT, claymore.preferredVocation)
        assertEquals(0.75, base.weaponOffVocationMultiplier(claymore), 0.001)
        assertEquals(0.75, base.copy(vocation = Vocation.MAGE).weaponOffVocationMultiplier(claymore), 0.001)
        assertEquals(1.0, base.copy(vocation = Vocation.KNIGHT).weaponOffVocationMultiplier(claymore), 0.001)

        // Higher Melee skill deals measurably higher average damage across combat strikes
        val target = MonsterEntity(777, MonsterType.ELDER_DRAGON, -4, 16, 15, 16, 15, 5000, 5000)
        val lowSkill = base.copy(
            vocation = Vocation.KNIGHT,
            floor = -4,
            playerX = 15,
            playerY = 15,
            monsters = listOf(target),
            targetMonsterId = 777,
            equipment = EquippedGear(weaponId = "sunbrass_dirk"),
            skills = PlayerSkills(melee = SkillProgress(10, 0.0))
        )
        val highSkill = lowSkill.copy(skills = PlayerSkills(melee = SkillProgress(35, 0.0)))

        var lowTotal = 0
        var highTotal = 0
        repeat(20) { i ->
            val rLow = GameEngine.processPlayerAttackSubTick(lowSkill.copy(lastPlayerAttackMs = 0L), 1000L + i)
            val rHigh = GameEngine.processPlayerAttackSubTick(highSkill.copy(lastPlayerAttackMs = 0L), 1000L + i)
            lowTotal += 5000 - rLow.monsters.first().hp
            highTotal += 5000 - rHigh.monsters.first().hp
        }
        assertTrue("Melee 35 ($highTotal) must deal far more damage than Melee 10 ($lowTotal)", highTotal > lowTotal * 1.8)
    }

    @Test
    fun twoHandedWeaponsLockShieldAndPenalizeShieldingDodge() {
        val base = GameEngine.createNewWorld("Lyra").copy(
            skills = PlayerSkills(shielding = SkillProgress(20, 0.0)),
            backpack = listOf(ItemStack("ashwood_shortbow", 1), ItemStack("bog_cleaver_axe", 1))
        )
        assertTrue(base.hasActiveShield())
        assertFalse(base.isUsingTwoHandedWeapon())
        val dodgeWithShield = base.shieldDodgeChance(isRanged = false)

        // Equipping 2H Bow automatically unequips Wooden Shield into backpack
        val withBow = GameEngine.useOrEquipBackpackItem(base, "ashwood_shortbow", 1000L)
        assertEquals("ashwood_shortbow", withBow.equipment.weaponId)
        assertNull("2H Bow must auto-unequip Shield", withBow.equipment.shieldId)
        assertTrue("Unequipped Wooden Shield must return to backpack", withBow.backpack.any { it.itemId == "wooden_shield" })
        assertTrue(withBow.isUsingTwoHandedWeapon())
        val dodgeWith2H = withBow.shieldDodgeChance(isRanged = false)

        assertTrue(
            "1H + Shield dodge ($dodgeWithShield) must be substantially higher than 2H dodge ($dodgeWith2H)",
            dodgeWithShield > dodgeWith2H + 0.20
        )

        // Equipping Wooden Shield back auto-unequips the 2H Bow into backpack
        val backToShield = GameEngine.useOrEquipBackpackItem(withBow, "wooden_shield", 2000L)
        assertEquals("wooden_shield", backToShield.equipment.shieldId)
        assertNull("Equipping Shield must auto-unequip 2H Bow", backToShield.equipment.weaponId)
        assertTrue(backToShield.backpack.any { it.itemId == "ashwood_shortbow" })
    }

    @Test
    fun v1LegacySaveMigratesSeamlesslyIncludingToolBagAndTwoHandedResolution() {
        val legacySave = PlayerSaveData(
            slotId = "legacy-slot",
            name = "OldAldric",
            vocationName = Vocation.PALADIN.name,
            level = 5,
            xp = 1500L,
            hp = 200,
            maxHp = 245,
            mp = 40,
            maxMp = 55,
            soul = 100,
            maxSoul = 100,
            maxCapOz = 570.0,
            gold = 250,
            floor = 0,
            x = 15,
            y = 14,
            poisonTicks = 0,
            fireTicks = 0,
            skills = PlayerSkills(),
            equipment = EquippedGear(weaponId = "ashwood_shortbow", shieldId = "wooden_shield"),
            backpack = listOf(ItemStack("rope", 1), ItemStack("fishing_rod", 1), ItemStack("health_potion", 2)),
            depot = listOf(ItemStack("cheese", 1)),
            playerCorpses = listOf(
                CorpseEntity(
                    id = "legacy_corpse",
                    floor = 0,
                    x = 18,
                    y = 16,
                    label = "OldAldric's Fallen Backpack",
                    isPlayerCorpse = true,
                    items = listOf(ItemStack("spider_silk", 2))
                )
            ),
            mapVersion = 1
        )

        val migrated = GameEngine.fromSaveData(legacySave)
        assertEquals(Vocation.PALADIN, migrated.vocation)
        assertEquals(0, migrated.floor)
        assertEquals(WorldMaps.TEMPLE_SPAWN_X, migrated.playerX) // 15 + 32 = 47
        assertEquals(WorldMaps.TEMPLE_SPAWN_Y, migrated.playerY) // 14 + 32 = 46
        assertEquals(10, migrated.skills.mining.level)
        // Legacy 2H Bow + Shield conflict resolved safely: shield returned to backpack
        assertEquals("ashwood_shortbow", migrated.equipment.weaponId)
        assertNull(migrated.equipment.shieldId)
        assertTrue(migrated.backpack.any { it.itemId == "wooden_shield" })
        // Legacy tools auto-migrated from backpack into toolBag
        assertTrue(migrated.toolBag.any { it.itemId == "rope" })
        assertTrue(migrated.toolBag.any { it.itemId == "fishing_rod" })
        assertFalse(migrated.backpack.any { it.itemId == "rope" })
        // Legacy V1 save receives starter Quarry Mattock in Depot
        assertTrue("Legacy V1 save must receive starter Quarry Mattock in Depot", migrated.depot.any { it.itemId == "quarry_mattock" })

        val migratedCorpse = migrated.corpses.first { it.id == "legacy_corpse" }
        assertEquals(50, migratedCorpse.x) // 18 + 32
        assertEquals(48, migratedCorpse.y) // 16 + 32

        val resaved = GameEngine.toSaveData(migrated)
        assertEquals(CURRENT_MAP_VERSION, resaved.mapVersion)
        assertTrue(resaved.toolBag.any { it.itemId == "rope" })
    }

    @Test
    fun expandedSurfaceAndFourFloorDungeonPortalsAreValid() {
        val surface = WorldMaps.getFloor(0)
        assertEquals(96, surface.width)
        assertEquals(96, surface.height)

        for (floorId in listOf(-1, -2, -3, -4)) {
            val f = WorldMaps.getFloor(floorId)
            assertEquals(48, f.width)
            assertEquals(48, f.height)
        }

        for (portal in WorldMaps.portals) {
            val destFloor = WorldMaps.getFloor(portal.toFloor)
            val destTile = destFloor.tileAt(portal.toX, portal.toY)
            assertTrue(
                "Portal from (${portal.fromFloor}:${portal.fromX},${portal.fromY}) lands on non-walkable $destTile",
                destTile.walkable
            )
        }

        val f3 = WorldMaps.getFloor(-3)
        assertFalse(f3.isProtectionZone(24, 21))
        var hasFurnace = false
        var hasAnvil = false
        var hasMiniDepot = false
        for (y in 0 until f3.height) {
            for (x in 0 until f3.width) {
                when (f3.tileAt(x, y)) {
                    TileType.SMELTING_FURNACE -> hasFurnace = true
                    TileType.BLACKSMITH_ANVIL -> hasAnvil = true
                    TileType.MINI_DEPOT -> hasMiniDepot = true
                    else -> Unit
                }
            }
        }
        assertTrue("Floor -3 outpost must contain a Smelting Furnace", hasFurnace)
        assertTrue("Floor -3 outpost must contain a Blacksmith Anvil", hasAnvil)
        assertTrue("Floor -3 outpost must contain a Mini-Depot", hasMiniDepot)
    }

    @Test
    fun ropeRequiredToClimbFromFloorMinusTwoAndMinusFour() {
        val hero = GameEngine.createNewWorld("Lyra")
        val noRopeF2 = hero.copy(floor = -2, playerX = 24, playerY = 38, backpack = emptyList(), toolBag = emptyList())
        assertEquals(-2, GameEngine.useRope(noRopeF2, 1000L).floor)

        // Rope works directly from Tool Bag
        val withRopeF2 = noRopeF2.copy(toolBag = listOf(ItemStack("rope", 1)))
        assertEquals(-1, GameEngine.useRope(withRopeF2, 2000L).floor)

        val withRopeF4 = hero.copy(floor = -4, playerX = 10, playerY = 34, toolBag = listOf(ItemStack("rope", 1)))
        assertEquals(-3, GameEngine.useRope(withRopeF4, 3000L).floor)
    }

    @Test
    fun oreMiningEnforcesPickaxeTierDepletionAndRegeneration() {
        val hero = GameEngine.createNewWorld("Roland")
        val cinderNodeEntry = hero.oreNodes.entries.first { it.value.kind == OreVeinKind.CINDERITE }
        val (cFloor, cX, cY) = cinderNodeEntry.key

        val atCinderT1 = hero.copy(floor = cFloor, playerX = cX - 1, playerY = cY)
        val rejected = CraftingAndMiningEngine.mineOreNode(atCinderT1, cX, cY, 1000L)
        assertEquals(0, rejected.itemCount("cinderite_ore"))
        assertEquals(cinderNodeEntry.value.chargesLeft, rejected.oreNodes[cinderNodeEntry.key]!!.chargesLeft)

        var state = atCinderT1.copy(
            toolBag = listOf(ItemStack("quenchsteel_delver", 1)),
            maxCapOz = 900.0
        )
        var attempts = 0
        while (!state.oreNodes[cinderNodeEntry.key]!!.isDepleted && attempts < 40) {
            state = CraftingAndMiningEngine.mineOreNode(state, cX, cY, 2000L + attempts * 500L)
            attempts++
        }
        assertTrue("Cinderite Seam should deplete after mining", state.oreNodes[cinderNodeEntry.key]!!.isDepleted)
        assertTrue("Player should obtain Cinderite Ore", state.itemCount("cinderite_ore") >= 1)

        repeat(OreVeinKind.CINDERITE.regenTicks) {
            state = CraftingAndMiningEngine.regenerateOreNodesTick(state)
        }
        val restored = state.oreNodes[cinderNodeEntry.key]!!
        assertFalse("Cinderite Seam must regenerate after regenTicks", restored.isDepleted)
        assertEquals(OreVeinKind.CINDERITE.maxCharges, restored.chargesLeft)
    }

    @Test
    fun craftingRequiresPhysicalStationAndSmeltingCompressesCapWeight() {
        val base = GameEngine.createNewWorld("Roland").copy(
            backpack = listOf(ItemStack("copper_ore", 1), ItemStack("tin_ore", 1), ItemStack("jagged_ivory", 2)),
            activePanel = ActivePanel.NONE
        )

        val blocked = CraftingAndMiningEngine.executeRecipe(base, "smelt_sunbrass", 1000L)
        assertEquals(0, blocked.itemCount("sunbrass_ingot"))
        assertEquals(1, blocked.itemCount("copper_ore"))
        assertEquals(1, blocked.itemCount("tin_ore"))

        val capBefore = base.usedCapOz()
        val smelted = CraftingAndMiningEngine.executeRecipe(
            base.copy(activePanel = ActivePanel.FURNACE),
            "smelt_sunbrass",
            2000L
        )
        assertEquals(1, smelted.itemCount("sunbrass_ingot"))
        assertEquals(0, smelted.itemCount("copper_ore"))
        assertEquals(0, smelted.itemCount("tin_ore"))
        assertTrue("Smelting should compress Cap weight", smelted.usedCapOz() < capBefore)

        val forged = CraftingAndMiningEngine.executeRecipe(
            smelted.copy(activePanel = ActivePanel.ANVIL),
            "forge_sunbrass_dirk",
            3000L
        )
        assertEquals(1, forged.itemCount("sunbrass_dirk"))
        assertEquals(0, forged.itemCount("sunbrass_ingot"))
        assertEquals(0, forged.itemCount("jagged_ivory"))
    }

    @Test
    fun shopContainsZeroFinishedWeaponsAndRegularMonstersRespectRareBossWeaponDrops() {
        val weaponCategories = setOf(
            ItemCategory.WEAPON_MELEE,
            ItemCategory.WEAPON_DISTANCE,
            ItemCategory.WEAPON_WAND
        )

        for (shopId in ItemCatalog.shopInventoryIds) {
            val def = ItemCatalog.get(shopId)
            assertFalse("Alaric's Shop must not sell finished weapon: ${def.name}", def.category in weaponCategories)
        }

        for (type in MonsterType.entries.filter { !it.isBoss && it.tier <= 4 }) {
            repeat(10) { i ->
                val dummy = MonsterEntity(i, type, -1, 10, 10, 10, 10, type.maxHp, type.maxHp)
                val corpse = GameEngine.generateMonsterCorpse(dummy)
                for (stack in corpse.items) {
                    val def = ItemCatalog.get(stack.itemId)
                    if (def.id == "wooden_club") continue
                    assertFalse(
                        "Classic regular monster ${type.displayName} dropped forged weapon ${def.name}",
                        def.category in weaponCategories
                    )
                }
            }
        }
    }

    @Test
    fun variableWeaponAttackSpeedsAndAmmoConsumptionWorkCorrectly() {
        val dummyTarget = MonsterEntity(9999, MonsterType.GRIMMAW_CRUSHER, -1, 16, 15, 16, 15, 300, 300)

        val fastHero = GameEngine.createNewWorld("Roland").copy(
            floor = -1,
            playerX = 15,
            playerY = 15,
            monsters = listOf(dummyTarget),
            targetMonsterId = 9999,
            lastPlayerAttackMs = 1000L,
            equipment = EquippedGear(weaponId = "sunbrass_dirk")
        )
        val fastAfter800 = GameEngine.processPlayerAttackSubTick(fastHero, 1800L)
        assertTrue("Fast 800ms weapon must attack at +800ms", fastAfter800.monsters.first().hp < 300)

        val heavyHero = fastHero.copy(equipment = EquippedGear(weaponId = "bog_cleaver_axe"))
        val heavyAfter800 = GameEngine.processPlayerAttackSubTick(heavyHero, 1800L)
        assertEquals("Heavy 1400ms weapon must NOT attack at +800ms", 300, heavyAfter800.monsters.first().hp)
        val heavyAfter1400 = GameEngine.processPlayerAttackSubTick(heavyHero, 2400L)
        assertTrue("Heavy 1400ms weapon must attack at +1400ms", heavyAfter1400.monsters.first().hp < 300)

        val archer = GameEngine.createNewWorld("Lyra").copy(
            floor = -1,
            playerX = 13,
            playerY = 15,
            monsters = listOf(dummyTarget),
            targetMonsterId = 9999,
            lastPlayerAttackMs = 0L,
            equipment = EquippedGear(weaponId = "ashwood_shortbow", ammoId = "ochre_shafts"),
            backpack = listOf(ItemStack("ochre_shafts", 10))
        )
        val afterShot = GameEngine.processPlayerAttackSubTick(archer, 1000L)
        assertEquals(9, afterShot.itemCount("ochre_shafts"))
    }

    @Test
    fun capacityWeightEnforcedOnWithdrawAndLoot() {
        val hero = GameEngine.createNewWorld("Vesper")
        val overloaded = hero.copy(maxCapOz = 10.0, depot = listOf(ItemStack("dragon_mail", 1)))
        val afterWithdraw = GameEngine.withdrawItem(overloaded, "dragon_mail")
        assertEquals(1, afterWithdraw.depot.size)
        assertEquals(0, afterWithdraw.itemCount("dragon_mail"))
    }
}
