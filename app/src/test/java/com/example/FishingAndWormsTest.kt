package com.example

import com.example.game.engine.CraftingAndMiningEngine
import com.example.game.engine.GameEngine
import com.example.game.model.FishingCatalog
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemCategory
import com.example.game.model.ItemStack
import com.example.game.model.OreVeinKind
import com.example.game.model.SkillProgress
import com.example.game.model.Vocation
import com.example.game.world.WorldMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FishingAndWormsTest {

    @Test
    fun fiveNewFishAndSixWormsHaveStrictlyAscendingLevelsAndHealing() {
        val tiers = FishingCatalog.tiers
        assertEquals("Expected starter fish + 5 new tiered fish", 6, tiers.size)

        val expectedFishIds = listOf(
            "fresh_fish",
            "silver_trout",
            "marsh_pike",
            "sunfin_snapper",
            "glacier_salmon",
            "emberfin_sturgeon"
        )
        assertEquals(expectedFishIds, tiers.map { it.fishId })

        for (i in tiers.indices) {
            val tier = tiers[i]
            val fish = ItemCatalog.get(tier.fishId)
            val worm = ItemCatalog.get(tier.baitId)

            assertEquals(ItemCategory.FOOD, fish.category)
            assertEquals(ItemCategory.FISHING_BAIT, worm.category)
            assertEquals(tier.fishId, worm.baitTargetFishId)
            assertEquals(tier.requiredFishingLevel, fish.requiredFishingLevel)
            assertEquals(tier.requiredFishingLevel, worm.requiredFishingLevel)
            assertTrue("Shop should stock ${worm.id}", ItemCatalog.shopInventoryIds.contains(worm.id))

            if (i > 0) {
                val prevTier = tiers[i - 1]
                val prevFish = ItemCatalog.get(prevTier.fishId)
                assertTrue(
                    "${fish.name} req level (${tier.requiredFishingLevel}) must exceed ${prevFish.name} (${prevTier.requiredFishingLevel})",
                    tier.requiredFishingLevel > prevTier.requiredFishingLevel
                )
                assertTrue(
                    "${fish.name} HP heal (${fish.restoreAmount}) must exceed ${prevFish.name} (${prevFish.restoreAmount})",
                    fish.restoreAmount > prevFish.restoreAmount
                )
                assertTrue(
                    "${fish.name} MP heal (${fish.restoreAmount / 2}) must exceed ${prevFish.name} (${prevFish.restoreAmount / 2})",
                    (fish.restoreAmount / 2) > (prevFish.restoreAmount / 2)
                )
            }
        }
    }

    @Test
    fun starterCharacterGetsGardenWormsAndManualBaitSelectionWorks() {
        val state = GameEngine.createNewWorld("Angler", Vocation.ADVENTURER)
        assertEquals("garden_worm", state.selectedBaitId)
        assertEquals(15, state.itemCount("garden_worm"))

        val withNightcrawlers = state.copy(
            backpack = GameEngine.addItemPublic(state.backpack, "dew_nightcrawler", 5)
        )
        val afterSelect = GameEngine.useOrEquipBackpackItem(withNightcrawlers, "dew_nightcrawler", 1_000L)
        assertEquals("dew_nightcrawler", afterSelect.selectedBaitId)
        assertEquals("Selecting bait must not consume worms", 5, afterSelect.itemCount("dew_nightcrawler"))
    }

    @Test
    fun missDoesNotConsumeWormButCatchConsumesExactlyOneWorm() {
        val base = GameEngine.createNewWorld("Fisher", Vocation.KNIGHT)
        assertEquals(15, base.itemCount("garden_worm"))
        assertEquals(0, base.itemCount("fresh_fish"))

        // Miss (roll = 0.99 > catchChance): ZERO worms consumed, still earns practice XP
        val afterMiss = GameEngine.attemptFishing(base, base.playerX + 1, base.playerY, 2_000L, catchRollOverride = 0.99)
        assertEquals("Missed cast must NOT consume any worm", 15, afterMiss.itemCount("garden_worm"))
        assertEquals(0, afterMiss.itemCount("fresh_fish"))
        assertTrue("Missed cast still awards practice Fishing points", afterMiss.skills.fishing.points > base.skills.fishing.points)

        // Catch (roll = 0.01 <= catchChance): consumes exactly 1 worm and awards 1 Fresh Fish
        val afterCatch = GameEngine.attemptFishing(afterMiss, base.playerX + 1, base.playerY, 3_000L, catchRollOverride = 0.01)
        assertEquals("Successful catch must consume exactly 1 worm", 14, afterCatch.itemCount("garden_worm"))
        assertEquals(1, afterCatch.itemCount("fresh_fish"))
    }

    @Test
    fun higherTierBaitRequiresFishingLevelAndCatchesMatchingFish() {
        val base = GameEngine.createNewWorld("MasterAngler", Vocation.PALADIN)
        val withMireBait = base.copy(
            backpack = GameEngine.addItemPublic(base.backpack, "mire_bloodworm", 4),
            selectedBaitId = "mire_bloodworm"
        )

        // Fishing Lv 10 < required Lv 22 -> blocked, 0 worms consumed
        val blocked = GameEngine.attemptFishing(withMireBait, base.playerX + 1, base.playerY, 1_000L, catchRollOverride = 0.01)
        assertEquals(4, blocked.itemCount("mire_bloodworm"))
        assertEquals(0, blocked.itemCount("marsh_pike"))

        // Raise Fishing to Lv 22 -> catches Marsh Pike and consumes 1 Mire Bloodworm
        val leveled = withMireBait.copy(
            skills = withMireBait.skills.copy(fishing = SkillProgress(22, 0.0))
        )
        val caught = GameEngine.attemptFishing(leveled, base.playerX + 1, base.playerY, 2_000L, catchRollOverride = 0.01)
        assertEquals(3, caught.itemCount("mire_bloodworm"))
        assertEquals(1, caught.itemCount("marsh_pike"))
    }

    @Test
    fun capFullDoesNotConsumeWormOnBite() {
        val base = GameEngine.createNewWorld("Overloaded", Vocation.ADVENTURER)
        val fullCap = base.copy(maxCapOz = base.usedCapOz() + 0.5)

        val result = GameEngine.attemptFishing(fullCap, base.playerX + 1, base.playerY, 1_000L, catchRollOverride = 0.01)
        assertEquals("Full Cap must not consume worm", 15, result.itemCount("garden_worm"))
        assertEquals(0, result.itemCount("fresh_fish"))
    }

    @Test
    fun eatingHigherCategoryFishHealsMoreHpAndMp() {
        val base = GameEngine.createNewWorld("Healer", Vocation.KNIGHT).copy(
            maxHp = 500,
            hp = 100,
            maxMp = 300,
            mp = 50
        )

        var previousHpHeal = 0
        var previousMpHeal = 0
        for (tier in FishingCatalog.tiers) {
            val withFish = base.copy(backpack = listOf(ItemStack(tier.fishId, 1)))
            val afterEat = GameEngine.useOrEquipBackpackItem(withFish, tier.fishId, 1_000L)
            val hpGained = afterEat.hp - base.hp
            val mpGained = afterEat.mp - base.mp
            assertEquals(0, afterEat.itemCount(tier.fishId))
            assertTrue("${tier.fishId} should heal more HP than previous tier", hpGained > previousHpHeal)
            assertTrue("${tier.fishId} should heal more MP than previous tier", mpGained > previousMpHeal)
            previousHpHeal = hpGained
            previousMpHeal = mpGained
        }
        assertEquals("Top-tier Emberfin Sturgeon should heal 220 HP", 220, previousHpHeal)
        assertEquals("Top-tier Emberfin Sturgeon should heal 110 MP", 110, previousMpHeal)
    }

    @Test
    fun diggingOreVeinsAndChoppingTreesYieldTieredWorms() {
        assertEquals("garden_worm", CraftingAndMiningEngine.rollWormForageForOre(OreVeinKind.COPPER, roll = 0.05)?.first)
        assertEquals("dew_nightcrawler", CraftingAndMiningEngine.rollWormForageForOre(OreVeinKind.BOG_IRON, roll = 0.05)?.first)
        assertEquals("mire_bloodworm", CraftingAndMiningEngine.rollWormForageForOre(OreVeinKind.MIRE_SILVER, roll = 0.05)?.first)
        assertEquals("dune_sandworm", CraftingAndMiningEngine.rollWormForageForOre(OreVeinKind.VESPERITE, roll = 0.05)?.first)
        assertEquals("frost_glowworm", CraftingAndMiningEngine.rollWormForageForOre(OreVeinKind.GLACIER_QUARTZ, roll = 0.05)?.first)
        assertEquals("cinder_emberworm", CraftingAndMiningEngine.rollWormForageForOre(OreVeinKind.CINDERITE, roll = 0.05)?.first)

        // End-to-end mining worm find on an existing Copper vein
        val base = GameEngine.createNewWorld("Miner", Vocation.KNIGHT)
        val copperEntry = base.oreNodes.entries.firstOrNull { (k, v) -> k.first == 0 && v.kind == OreVeinKind.COPPER }
        assertNotNull("World should contain a Floor 0 Copper vein", copperEntry)
        val (coord, _) = copperEntry!!
        val beforeWorms = base.itemCount("garden_worm")
        val afterMine = CraftingAndMiningEngine.mineOreNode(
            state = base.copy(playerX = coord.second, playerY = coord.third),
            tx = coord.second,
            ty = coord.third,
            nowMs = 5_000L,
            mineRollOverride = 0.01,
            wormRollOverride = 0.01
        )
        assertTrue("Digging ore vein should add unearthed worms", afterMine.itemCount("garden_worm") > beforeWorms)

        // End-to-end tree-chopping worm find on an overland Floor 0 tree
        val floor0 = WorldMaps.getFloor(0)
        var treeCoord: Pair<Int, Int>? = null
        outer@ for (y in 0 until floor0.height) {
            for (x in 0 until floor0.width) {
                if (floor0.tileAt(x, y) == com.example.game.model.TileType.TREE) {
                    treeCoord = x to y
                    break@outer
                }
            }
        }
        assertNotNull("Floor 0 should have at least one choppable tree", treeCoord)
        val (tx, ty) = treeCoord!!
        val withHatchet = base.copy(
            toolBag = GameEngine.addItemPublic(base.toolBag, "hatchet", 1),
            backpack = emptyList()
        )
        val afterChop = CraftingAndMiningEngine.chopTree(
            state = withHatchet,
            tx = tx,
            ty = ty,
            nowMs = 6_000L,
            wormRollOverride = 0.01
        )
        assertTrue(
            "Chopping a tree should find worms beneath the bark",
            afterChop.carriedFishingBaitStacks().sumOf { it.count } > 0
        )
    }

    @Test
    fun selectedBaitIdRoundTripsThroughSaveData() {
        val state = GameEngine.createNewWorld("Saver", Vocation.MAGE).copy(
            backpack = listOf(ItemStack("frost_glowworm", 7)),
            selectedBaitId = "frost_glowworm"
        )
        val saved = GameEngine.toSaveData(state)
        assertEquals("frost_glowworm", saved.selectedBaitId)

        val restored = GameEngine.fromSaveData(saved)
        assertEquals("frost_glowworm", restored.selectedBaitId)
    }
}
