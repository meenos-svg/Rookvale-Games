package com.example

import com.example.game.cloud.CloudSavePayloadCodec
import com.example.game.engine.CraftingAndMiningEngine
import com.example.game.model.RecipeCatalog
import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.EquipmentSlot
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemStack
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.ORACLE_MIN_LEVEL
import com.example.game.model.TREE_CHOP_COOLDOWN_MS
import com.example.game.model.TREE_REGROW_TICKS
import com.example.game.model.TileType
import com.example.game.model.Vocation
import com.example.game.world.WorldFloor
import com.example.game.world.WorldMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayUpgradesTest {

    /** Nearest walkable, non-protection-zone strip (start + 6 tiles east) around the Rookvale temple. */
    private fun findOpenStrip(map: WorldFloor): Pair<Int, Int> {
        for (r in 5..200) for (dy in -r..r) for (dx in listOf(-r, r)) {
            val x = WorldMaps.TEMPLE_SPAWN_X + dx
            val y = WorldMaps.TEMPLE_SPAWN_Y + dy
            if ((0..6).all { map.tileAt(x + it, y).walkable && !map.isProtectionZone(x + it, y) }) return x to y
        }
        error("No open ground found near Rookvale")
    }

    @Test
    fun levelScalingCap_andLevel20OracleJobAdvanceSurge() {
        val lvl1 = GameEngine.createNewWorld(name = "Alden")
        assertEquals(1, lvl1.level)
        assertEquals(Vocation.ADVENTURER, lvl1.vocation)
        assertEquals(1500.0, lvl1.maxCapOz, 0.01)

        // Level up to Level 2 -> +125 oz = 1625.0 oz
        val lvl2 = GameEngine.grantExperience(lvl1, xpGain = 100L, fx = lvl1.playerX.toFloat(), fy = lvl1.playerY.toFloat(), nowMs = 1_000L)
        assertEquals(2, lvl2.level)
        assertEquals(1625.0, lvl2.maxCapOz, 0.01)

        // Verify remaining Cap updates cleanly when unequipping and dropping gear
        val capBeforeUnequip = lvl2.remainingCapOz()
        val unequipped = GameEngine.unequipSlot(lvl2, EquipmentSlot.ARMOR)
        assertEquals(capBeforeUnequip, unequipped.remainingCapOz(), 0.01) // moved to backpack -> same total weight
        val droppedArmor = GameEngine.dropItem(unequipped, "leather_armor")
        assertTrue(
            "Dropping leather armor should free carrying Cap",
            droppedArmor.remainingCapOz() > capBeforeUnequip + 10.0
        )

        // Level 20 Adventurer baseline = 1500 + 19 * 125 = 3875.0 oz
        assertEquals(3875.0, GameWorldState.maxCapForLevel(ORACLE_MIN_LEVEL, Vocation.ADVENTURER), 0.01)

        // Level 20 Oracle Promotion: Knight (+1250 oz surge -> 5125.0 oz, then +200 oz/lvl)
        val adv20 = lvl1.copy(
            level = ORACLE_MIN_LEVEL,
            xp = lvl1.xpForLevel(ORACLE_MIN_LEVEL),
            maxCapOz = GameWorldState.maxCapForLevel(ORACLE_MIN_LEVEL, Vocation.ADVENTURER)
        )
        val knight20 = GameEngine.promoteVocation(adv20, Vocation.KNIGHT, nowMs = 2_000L)
        assertEquals(Vocation.KNIGHT, knight20.vocation)
        assertEquals(5125.0, knight20.maxCapOz, 0.01)
        val xpTo21 = knight20.xpForLevel(21) - knight20.xp
        val knight21 = GameEngine.grantExperience(knight20, xpGain = xpTo21, fx = knight20.playerX.toFloat(), fy = knight20.playerY.toFloat(), nowMs = 3_000L)
        assertEquals(21, knight21.level)
        assertEquals(5325.0, knight21.maxCapOz, 0.01)

        // Level 20 Oracle Promotion: Paladin (+900 oz surge -> 4775.0 oz, then +160 oz/lvl)
        val paladin20 = GameEngine.promoteVocation(adv20, Vocation.PALADIN, nowMs = 2_000L)
        assertEquals(4775.0, paladin20.maxCapOz, 0.01)
        assertEquals(4935.0, GameWorldState.maxCapForLevel(21, Vocation.PALADIN), 0.01)

        // Level 20 Oracle Promotion: Mage (+600 oz surge -> 4475.0 oz, then +125 oz/lvl)
        val mage20 = GameEngine.promoteVocation(adv20, Vocation.MAGE, nowMs = 2_000L)
        assertEquals(4475.0, mage20.maxCapOz, 0.01)
        assertEquals(4600.0, GameWorldState.maxCapForLevel(21, Vocation.MAGE), 0.01)

        // Cloud save codec preserves upgraded maxCapOz and upgrades legacy low-Cap saves on decode
        val saveData = GameEngine.toSaveData(knight21)
        val encoded = CloudSavePayloadCodec.encode(saveData)
        val decoded = CloudSavePayloadCodec.decode(encoded, saveData.slotId, saveData.name)
        assertEquals(5325.0, decoded.maxCapOz, 0.01)
        val hydrated = GameEngine.fromSaveData(decoded)
        assertEquals(5325.0, hydrated.maxCapOz, 0.01)
    }

    @Test
    fun tapToTrackAutoFollow_meleeTracksAndCancels_rangedDoesNotForceMelee() {
        val base = GameEngine.createNewWorld(name = "Bram")
        // Place player on open overland ground outside the protection zone
        val overland = WorldMaps.getFloor(0)
        val (px, py) = findOpenStrip(overland)
        val monster = MonsterEntity(
            id = 9001,
            type = MonsterType.CAVE_RAT,
            floor = 0,
            x = px + 3,
            y = py,
            spawnX = px + 3,
            spawnY = py,
            hp = MonsterType.CAVE_RAT.maxHp,
            maxHp = MonsterType.CAVE_RAT.maxHp
        )
        val world = base.copy(
            floor = 0,
            playerX = px,
            playerY = py,
            prevPlayerX = px,
            prevPlayerY = py,
            monsters = listOf(monster),
            targetMonsterId = null,
            autoFollowTarget = false
        )
        assertTrue(world.isMeleeCombatSetup())

        // Tapping a distant monster with melee weapon enables autoFollowTarget and queues path
        val targeted = GameEngine.handleTileTap(world, monster.x, monster.y, nowMs = 1_000L)
        assertEquals(monster.id, targeted.targetMonsterId)
        assertTrue("Melee tap must engage auto-follow", targeted.autoFollowTarget)
        // The tap takes the first approach step immediately (or queues a path) so movement feels instant
        assertTrue("Melee tap should start approaching", targeted.playerX > px || targeted.walkPath.isNotEmpty())

        // Stepping via stepAutoFollowOrPath moves player closer to the monster
        val stepped = GameEngine.stepAutoFollowOrPath(targeted, nowMs = 1_300L)
        assertTrue("Player should advance toward target", stepped.playerX > px)
        assertTrue(stepped.autoFollowTarget)

        // If monster retreats further away, stepAutoFollowOrPath recomputes path and chases
        val monsterMoved = stepped.copy(
            walkPath = emptyList(),
            monsters = listOf(monster.copy(x = stepped.playerX + 3, y = stepped.playerY))
        )
        val chased = GameEngine.stepAutoFollowOrPath(monsterMoved, nowMs = 1_600L)
        assertTrue("Auto-follow should chase fleeing monster", chased.playerX > stepped.playerX)

        // Tapping a ground tile cancels auto-follow cleanly
        val groundTap = GameEngine.handleTileTap(chased, chased.playerX - 1, chased.playerY, nowMs = 1_900L)
        assertFalse("Tapping a non-monster tile must cancel auto-follow", groundTap.autoFollowTarget)

        // Ranged weapon (Ashwood Shortbow) targets without forcing melee auto-follow
        val withBow = world.copy(
            equipment = world.equipment.copy(weaponId = "ashwood_shortbow", shieldId = null, ammoId = "wooden_arrows"),
            backpack = world.backpack + ItemStack("wooden_arrows", 20)
        )
        assertFalse(withBow.isMeleeCombatSetup())
        val rangedTarget = GameEngine.handleTileTap(withBow, monster.x, monster.y, nowMs = 2_100L)
        assertEquals(monster.id, rangedTarget.targetMonsterId)
        assertFalse("Ranged weapon should not engage melee auto-follow", rangedTarget.autoFollowTarget)
    }

    @Test
    fun treeChopping_hatchetShopOnly_andFletchingWorkbenchFlow() {
        val fresh = GameEngine.createNewWorld(name = "Sylva")

        // Hatchet is NOT in starter gear, and IS sold in Alaric's Shop
        assertFalse("Hatchet should not be in starter gear", fresh.hasHatchet())
        assertTrue("Hatchet must be in Alaric's shop", "hatchet" in ItemCatalog.shopInventoryIds)

        // Find an overland TREE tile and place player adjacent to it
        val overland = WorldMaps.getFloor(0)
        var treeCoord: Pair<Int, Int>? = null
        var standCoord: Pair<Int, Int>? = null
        outer@ for (y in 2..overland.height - 3) {
            for (x in 2..overland.width - 3) {
                if (overland.tileAt(x, y) == TileType.TREE) {
                    val adj = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)
                        .firstOrNull { (ax, ay) -> overland.tileAt(ax, ay).walkable }
                    if (adj != null) {
                        treeCoord = x to y
                        standCoord = adj
                        break@outer
                    }
                }
            }
        }
        assertNotNull("Overland map must contain accessible TREE tiles", treeCoord)
        val (tx, ty) = treeCoord!!
        val (sx, sy) = standCoord!!

        // Attempting to chop without a Hatchet yields no logs and prompts Alaric's Shop
        val noHatchetWorld = fresh.copy(floor = 0, playerX = sx, playerY = sy, prevPlayerX = sx, prevPlayerY = sy)
        val deniedChop = CraftingAndMiningEngine.chopTree(noHatchetWorld, tx, ty, nowMs = 5_000L)
        assertEquals(0, deniedChop.itemCount("wood_log"))
        assertTrue(deniedChop.statusLog.last().contains("Hatchet", ignoreCase = true))

        // Buy Hatchet from Alaric's Shop -> stored in Tool Bag at 50% weight
        val rich = noHatchetWorld.copy(gold = 200)
        val boughtHatchet = GameEngine.buyShopItem(rich, "hatchet")
        assertTrue("Player should own Hatchet after buying from Alaric", boughtHatchet.hasHatchet())
        assertTrue(boughtHatchet.toolBag.any { it.itemId == "hatchet" })

        // Chop adjacent tree -> harvests 1..2 Wood Logs and sets 60-tick regrowth timer
        val chopped = CraftingAndMiningEngine.chopTree(boughtHatchet, tx, ty, nowMs = 6_000L)
        assertTrue("Chopping tree should harvest at least 1 Wood Log", chopped.itemCount("wood_log") in 1..2)
        assertEquals(TREE_REGROW_TICKS, chopped.choppedTrees[Triple(0, tx, ty)])

        // Immediate second chop within cooldown does not harvest
        val cooldownChop = CraftingAndMiningEngine.chopTree(chopped, tx, ty, nowMs = 6_000L + TREE_CHOP_COOLDOWN_MS - 100L)
        assertEquals(chopped.itemCount("wood_log"), cooldownChop.itemCount("wood_log"))

        // Advance 60 ticks -> tree regrows
        var regrown = chopped
        repeat(TREE_REGROW_TICKS) {
            regrown = CraftingAndMiningEngine.regenerateTreesTick(regrown)
        }
        assertFalse("Tree should regrow after 60 ticks", Triple(0, tx, ty) in regrown.choppedTrees)

        // Locate Fletching Workbench tile in Rookvale town and tap it to open panel
        var benchCoord: Pair<Int, Int>? = null
        var benchStand: Pair<Int, Int>? = null
        benchSearch@ for (y in 2..overland.height - 3) {
            for (x in 2..overland.width - 3) {
                if (overland.tileAt(x, y) == TileType.FLETCHING_BENCH) {
                    val adj = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)
                        .firstOrNull { (ax, ay) -> overland.tileAt(ax, ay).walkable }
                    if (adj != null) {
                        benchCoord = x to y
                        benchStand = adj
                        break@benchSearch
                    }
                }
            }
        }
        assertNotNull("Rookvale town must have a Fletching Workbench tile", benchCoord)
        val (bx, by) = benchCoord!!
        val (bsx, bsy) = benchStand!!

        val atBench = boughtHatchet.copy(
            playerX = bsx,
            playerY = bsy,
            prevPlayerX = bsx,
            prevPlayerY = bsy,
            backpack = boughtHatchet.backpack + ItemStack("wood_log", 6) + ItemStack("wild_feather", 4) +
                ItemStack("spider_silk", 2) + ItemStack("brindle_hide", 2)
        )
        val arrowRecipe = RecipeCatalog.fletchingRecipes.first { it.id == "fletch_wooden_arrows" }
        val bowRecipe = RecipeCatalog.fletchingRecipes.first { it.id == "fletch_hunting_bow" }
        fun logs(r: com.example.game.model.CraftingRecipe) = r.ingredients.filter { it.itemId == "wood_log" }.sumOf { it.count }
        val logsAfterArrows = 6 - logs(arrowRecipe)
        val logsAfterBow = logsAfterArrows - logs(bowRecipe)
        val openedBench = GameEngine.handleTileTap(atBench, bx, by, nowMs = 9_000L)
        assertEquals(ActivePanel.FLETCHING_BENCH, openedBench.activePanel)

        // Fletch Wooden Arrows and a Hunting Bow using the current recipes
        val fletchedArrows = CraftingAndMiningEngine.executeRecipe(openedBench, "fletch_wooden_arrows", nowMs = 9_500L)
        assertEquals(arrowRecipe.outputCount, fletchedArrows.itemCount("wooden_arrows"))
        assertEquals(logsAfterArrows, fletchedArrows.itemCount("wood_log"))

        val fletchedBow = CraftingAndMiningEngine.executeRecipe(fletchedArrows, "fletch_hunting_bow", nowMs = 10_000L)
        assertEquals(1, fletchedBow.itemCount("hunting_bow"))
        assertEquals(logsAfterBow, fletchedBow.itemCount("wood_log"))

        // Equip Hunting Bow & load Wooden Arrows, then verify full Supabase cloud save round-trip
        val equippedBow = GameEngine.useOrEquipBackpackItem(fletchedBow, "hunting_bow", nowMs = 10_500L)
        val loadedAmmo = GameEngine.useOrEquipBackpackItem(equippedBow, "wooden_arrows", nowMs = 11_000L)
        assertEquals("hunting_bow", loadedAmmo.equipment.weaponId)
        assertEquals("wooden_arrows", loadedAmmo.equipment.ammoId)

        val saveData = GameEngine.toSaveData(loadedAmmo)
        val json = CloudSavePayloadCodec.encode(saveData)
        val decoded = CloudSavePayloadCodec.decode(json, saveData.slotId, saveData.name)
        val hydrated = GameEngine.fromSaveData(decoded)
        assertTrue(hydrated.hasHatchet())
        assertEquals(logsAfterBow, hydrated.itemCount("wood_log"))
        assertEquals("hunting_bow", hydrated.equipment.weaponId)
        assertEquals("wooden_arrows", hydrated.equipment.ammoId)
        assertEquals(arrowRecipe.outputCount, hydrated.itemCount("wooden_arrows"))
    }
}
