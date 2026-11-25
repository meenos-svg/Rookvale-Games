package com.example

import com.example.game.cloud.CloudSavePayloadCodec
import com.example.game.cloud.EventDrivenCloudSaveController
import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.EquippedGear
import com.example.game.model.ItemStack
import com.example.game.model.MonsterType
import com.example.game.model.OreVeinKind
import com.example.game.model.Vocation
import com.example.game.world.NpcRole
import com.example.game.world.WorldMaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SlimCloudSaveAndReaperTempleTest {

    @Test
    fun slimCloudPayloadContainsOnlyPermanentCharacterDataAndExcludesWorldEntities() {
        val base = GameEngine.createNewWorld("Aelric", Vocation.KNIGHT, 3, 2)
        val populated = base.copy(
            isAdmin = true,
            regionId = "region_2_0",
            playerX = 1140,
            playerY = 220,
            floor = -6, // Gorek Labyrinth
            level = 42,
            xp = 1_234_567L,
            hp = 680,
            maxHp = 710,
            mp = 240,
            maxMp = 260,
            soul = 88,
            gold = 4_250,
            bankGold = 95_000L,
            equipment = EquippedGear(
                weaponId = "cinderite_blade",
                shieldId = "cinderite_bulwark",
                armorId = "cinderite_cuirass",
                helmetId = "steel_helmet",
                legsId = "steel_legs",
                bootsId = "leather_boots",
                amuletId = "amulet_of_cinders",
                ringId = "ring_of_vesper"
            ),
            backpack = listOf(ItemStack("health_potion", 12), ItemStack("cinderite_ore", 4)),
            depot = listOf(ItemStack("vesperite_ingot", 9)),
            reaperVault = listOf(ItemStack("vesperite_blade", 1)),
            customHotbarSlots = listOf("health_potion", "mana_potion"),
            questFlags = mapOf("oracle_chosen" to true, "slain_vaelgor" to true),
            poisonTicks = 5,
            fireTicks = 3
        )

        val saveData = GameEngine.toSaveData(populated)
        val json = CloudSavePayloadCodec.encode(saveData)

        // Verify required permanent character fields exist
        assertEquals("Aelric", json.getString("name"))
        assertEquals("KNIGHT", json.getString("vocationName"))
        assertTrue(json.getBoolean("isAdmin"))
        assertEquals("region_2_0", json.getString("regionId"))
        assertEquals(1140, json.getInt("x"))
        assertEquals(220, json.getInt("y"))
        assertEquals(-6, json.getInt("floor"))
        assertEquals(42, json.getInt("level"))
        assertEquals(1_234_567L, json.getLong("xp"))
        assertEquals(680, json.getInt("hp"))
        assertEquals(710, json.getInt("maxHp"))
        assertEquals(240, json.getInt("mp"))
        assertEquals(260, json.getInt("maxMp"))
        assertEquals(88, json.getInt("soul"))
        assertEquals(4_250, json.getInt("gold"))
        assertEquals(95_000L, json.getLong("bankGold"))
        assertTrue(json.has("skills"))
        assertTrue(json.has("equippedItems"))
        assertTrue(json.has("backpackItems"))
        assertTrue(json.has("toolBagItems"))
        assertTrue(json.has("depotItems"))
        assertTrue(json.has("reaperVaultItems"))
        assertTrue(json.has("customHotbarSlots"))
        assertTrue(json.has("questFlags"))

        // Verify ALL world/ephemeral keys are completely absent from Postgres payload
        val forbiddenKeys = listOf(
            "playerCorpses", "corpses", "monsters", "oreNodes",
            "poisonTicks", "fireTicks", "facing", "chunks", "projectiles", "floatingTexts"
        )
        for (key in forbiddenKeys) {
            assertFalse("Slim save_data must NOT contain forbidden world key '$key'", json.has(key))
        }

        // Verify round-trip decode preserves every permanent field including Floor -6 and Reaper Vault
        val decoded = CloudSavePayloadCodec.decode(
            json = json,
            fallbackPlayerId = saveData.slotId,
            fallbackName = "Aelric"
        )
        assertTrue(decoded.isAdmin)
        assertEquals("region_2_0", decoded.regionId)
        assertEquals(-6, decoded.floor)
        assertEquals(42, decoded.level)
        assertEquals(95_000L, decoded.bankGold)
        assertEquals("cinderite_blade", decoded.equipment.weaponId)
        assertEquals(1, decoded.reaperVault.size)
        assertEquals("vesperite_blade", decoded.reaperVault.first().itemId)
        assertEquals(mapOf("oracle_chosen" to true, "slain_vaelgor" to true), decoded.questFlags)
        assertTrue("playerCorpses must always be empty after decode", decoded.playerCorpses.isEmpty())
        assertEquals(0, decoded.poisonTicks)
        assertEquals(0, decoded.fireTicks)

        // Verify reaperVaultItems and questFlags are omitted entirely when empty (extra slim JSON)
        val cleanJson = CloudSavePayloadCodec.encode(saveData.copy(reaperVault = emptyList(), questFlags = emptyMap()))
        assertFalse(cleanJson.has("reaperVaultItems"))
        assertFalse(cleanJson.has("questFlags"))
    }

    @Test
    fun legacyPlayerCorpsesInOldCloudSavesMigrateCleanlyIntoReaperVault() {
        val legacyJson = JSONObject().apply {
            put("name", "OldHero")
            put("vocationName", "ARCHER")
            put("regionId", DEFAULT_REGION_ID)
            put("x", WorldMaps.TEMPLE_SPAWN_X)
            put("y", WorldMaps.TEMPLE_SPAWN_Y)
            put("floor", -5) // Grimmaw Quarry
            put("mapVersion", 2)
            put("level", 30)
            put("xp", 500_000L)
            put("gold", 300)
            put("bankGold", 20_000L)
            put(
                "playerCorpses",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "old_corpse_1")
                        put("floor", -5)
                        put("x", 40)
                        put("y", 40)
                        put("gold", 150)
                        put(
                            "items",
                            JSONArray()
                                .put(JSONObject().apply { put("itemId", "vesperite_bow"); put("count", 1) })
                                .put(JSONObject().apply { put("itemId", "health_potion"); put("count", 5) })
                        )
                    }
                )
            )
        }

        val decoded = CloudSavePayloadCodec.decode(legacyJson, "slot_legacy", "OldHero")
        assertEquals(-5, decoded.floor)
        assertTrue("Legacy corpses should not remain as world corpses", decoded.playerCorpses.isEmpty())
        assertEquals(2, decoded.reaperVault.size)
        assertTrue(decoded.reaperVault.any { it.itemId == "vesperite_bow" && it.count == 1 })
        assertTrue(decoded.reaperVault.any { it.itemId == "health_potion" && it.count == 5 })

        val loadedWorld = GameEngine.fromSaveData(decoded)
        assertTrue(loadedWorld.corpses.isEmpty())
        assertEquals(2, loadedWorld.reaperVault.size)
        assertTrue(loadedWorld.statusLog.any { it.contains("Mortis", ignoreCase = true) })
    }

    @Test
    fun eventDrivenControllerSkipsWalkingAndCombatTicksAndCoalescesPermanentChanges() = runBlocking {
        val flushCount = AtomicInteger(0)
        val flushedStates = mutableListOf<Pair<Int, Long>>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var currentWorld = GameEngine.createNewWorld("DebouncedHero", Vocation.MAGE, 1, 1)
        try {
            val controller = EventDrivenCloudSaveController(
                scope = scope,
                debounceMs = 120L, // Fast test window exercising identical debounce semantics as 5_000L prod default
                stateProvider = { currentWorld },
                onFlush = { state, _ ->
                    flushCount.incrementAndGet()
                    flushedStates.add(state.level to state.xp)
                }
            )
            assertEquals(5_000L, EventDrivenCloudSaveController.DEFAULT_DEBOUNCE_MS)

            controller.markSynced(currentWorld)

            // 1. Routine walking steps, monster attacks, poison ticks, and monster respawns MUST NOT schedule any Postgres write
            currentWorld = currentWorld.copy(playerX = currentWorld.playerX + 1, playerY = currentWorld.playerY)
            assertFalse(controller.onStateMutated(currentWorld))
            currentWorld = currentWorld.copy(
                monsters = currentWorld.monsters.drop(1),
                poisonTicks = 4,
                floatingTexts = emptyList()
            )
            assertFalse(controller.onStateMutated(currentWorld))
            assertFalse(controller.hasPendingDebouncedSave())
            delay(200L)
            assertEquals("Zero Postgres writes should occur for walking or world entity ticks", 0, flushCount.get())

            // 2. Rapid burst of permanent character changes (XP, gold, item pickup, equip) coalesces into ONE write
            val gain1 = currentWorld.copy(xp = currentWorld.xp + 250L, gold = currentWorld.gold + 40)
            currentWorld = gain1
            assertTrue(controller.onStateMutated(currentWorld))
            delay(40L)
            val gain2 = gain1.copy(xp = gain1.xp + 500L, backpack = gain1.backpack + ItemStack("copper_ore", 2))
            currentWorld = gain2
            assertTrue(controller.onStateMutated(currentWorld))
            delay(40L)
            val gain3 = gain2.copy(level = gain2.level + 1, xp = gain2.xp + 1200L)
            currentWorld = gain3
            assertTrue(controller.onStateMutated(currentWorld))
            assertTrue(controller.hasPendingDebouncedSave())
            assertEquals("Should still be waiting inside debounce window", 0, flushCount.get())

            delay(220L)
            assertEquals("Rapid permanent mutations must coalesce into a single Postgres upsert", 1, flushCount.get())
            assertEquals(gain3.level to gain3.xp, flushedStates.last())

            // 3. Pause/Exit flush skips if nothing changed, but flushes immediately if player walked to new coords
            assertFalse("Clean state on pause should skip redundant upsert", controller.flushNow(currentWorld))
            val walkedFurther = currentWorld.copy(playerX = currentWorld.playerX + 6)
            currentWorld = walkedFurther
            assertTrue("New coordinates on pause/exit must persist cleanly", controller.flushNow(currentWorld))
            assertEquals(2, flushCount.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun deterministicWorldInitSpawnsAllBossesAndFiveOreTiersFromLayoutNotSaveFile() {
        val baseSave = GameEngine.toSaveData(GameEngine.createNewWorld("Explorer", Vocation.KNIGHT, 0, 0))
        val world = GameEngine.fromSaveData(baseSave)

        assertTrue("Session world starts with zero corpses", world.corpses.isEmpty())
        assertTrue("World spawns deterministic monsters", world.monsters.size > 40)
        assertTrue("Kragor boss present in Grimmaw Quarry", world.monsters.any { it.type == MonsterType.KRAGOR_THE_STONE_EYED })
        assertTrue("Vaelgor boss present in Gorek Labyrinth", world.monsters.any { it.type == MonsterType.VAELGOR_LABYRINTH_WARDEN })
        assertTrue("Elder Dragon present", world.monsters.any { it.type == MonsterType.ELDER_DRAGON })

        val oreKinds = world.oreNodes.values.map { it.kind }.toSet()
        assertTrue(OreVeinKind.COPPER in oreKinds)
        assertTrue(OreVeinKind.TIN in oreKinds)
        assertTrue(OreVeinKind.BOG_IRON in oreKinds)
        assertTrue(OreVeinKind.VESPERITE in oreKinds)
        assertTrue(OreVeinKind.CINDERITE in oreKinds)
    }

    @Test
    fun templeOfReapersIsReachableAndSupportsDeathVaultAndGoldBuybackWithBankFallback() {
        val floor0 = WorldMaps.getFloor(0)
        val mortis = WorldMaps.npcs.firstOrNull {
            it.floor == 0 && it.x == WorldMaps.REAPER_X && it.y == WorldMaps.REAPER_Y
        }
        assertNotNull("Mortis NPC must be placed at Temple of Reapers", mortis)
        assertEquals(NpcRole.REAPER_KEEPER, mortis!!.role)
        assertTrue("Temple of Reapers must be inside safe Protection Zone", floor0.isProtectionZone(WorldMaps.REAPER_X, WorldMaps.REAPER_Y))

        // Verify Temple of Reapers is reachable via GameEngine pathfinding from Temple Spawn
        val atSpawn = GameEngine.createNewWorld("ReaperTester", Vocation.KNIGHT, 0, 0)
        val walkingToReaper = GameEngine.walkToCoordinate(atSpawn, WorldMaps.REAPER_X, WorldMaps.REAPER_Y + 1)
        assertTrue("Player must be able to walk from Temple Spawn into Temple of Reapers", walkingToReaper.walkPath.isNotEmpty())

        // Tapping Mortis while adjacent opens ActivePanel.REAPER_TEMPLE
        val nearMortis = atSpawn.copy(
            playerX = WorldMaps.REAPER_X,
            playerY = WorldMaps.REAPER_Y + 1
        )
        val opened = GameEngine.handleTileTap(nearMortis, WorldMaps.REAPER_X, WorldMaps.REAPER_Y, nowMs = 1_000L)
        assertEquals(ActivePanel.REAPER_TEMPLE, opened.activePanel)

        // Simulate death in Floor -5 with valuable backpack items + bank gold via lethal poison tick in combat tick
        val doomed = opened.copy(
            floor = -5,
            playerX = 40,
            playerY = 40,
            hp = 1,
            poisonTicks = 3,
            gold = 200,
            bankGold = 5_000L,
            backpack = listOf(ItemStack("vesperite_blade", 1), ItemStack("health_potion", 4)),
            activePanel = ActivePanel.NONE
        )
        val afterDeath = GameEngine.processCombatTick(doomed, nowMs = 5_000L)

        assertNotNull("DeathReport should be shown on death", afterDeath.deathReport)
        assertEquals("Respawned on Floor 0", 0, afterDeath.floor)
        assertEquals(WorldMaps.TEMPLE_SPAWN_X, afterDeath.playerX)
        assertEquals(WorldMaps.TEMPLE_SPAWN_Y, afterDeath.playerY)
        assertEquals(DEFAULT_REGION_ID, afterDeath.regionId)
        assertTrue("No cloud/world player corpse should be created on death", afterDeath.corpses.none { it.isPlayerCorpse })
        assertTrue("Backpack should be empty after death", afterDeath.backpack.isEmpty())
        assertEquals("Bank gold stays completely safe on death", 5_000L, afterDeath.bankGold)
        assertEquals("Lost backpack items must transfer to Reaper Vault", 2, afterDeath.reaperVault.size)

        // Buy back single item using carried gold (0) + Bank Gold fallback
        val bladeFee = GameEngine.reaperReclaimFeeForStack(ItemStack("vesperite_blade", 1))
        assertTrue(bladeFee > 0)
        val afterBuyBlade = GameEngine.buyBackReaperItem(afterDeath.copy(deathReport = null), "vesperite_blade")
        assertEquals(5_000L - bladeFee.toLong(), afterBuyBlade.bankGold)
        assertTrue(afterBuyBlade.backpack.any { it.itemId == "vesperite_blade" })
        assertEquals(1, afterBuyBlade.reaperVault.size)

        // Buy back remaining items using carried purse gold first, then bank
        val potionFee = GameEngine.reaperReclaimFeeForStack(ItemStack("health_potion", 4))
        val withSmallPurse = afterBuyBlade.copy(gold = 10)
        val afterBuyAll = GameEngine.buyBackAllReaperItems(withSmallPurse)
        assertTrue("Reaper vault should be empty after Reclaim All", afterBuyAll.reaperVault.isEmpty())
        assertTrue(afterBuyAll.backpack.any { it.itemId == "health_potion" && it.count == 4 })
        val expectedSpentFromBank = (potionFee - 10).coerceAtLeast(0).toLong()
        val expectedRemainingPurse = (10 - potionFee).coerceAtLeast(0)
        assertEquals(expectedRemainingPurse, afterBuyAll.gold)
        assertEquals(afterBuyBlade.bankGold - expectedSpentFromBank, afterBuyAll.bankGold)

        // Verify discard removes unwanted stack without spending gold
        val withTrash = afterBuyAll.copy(reaperVault = listOf(ItemStack("wooden_club", 1)))
        val afterDiscard = GameEngine.discardReaperItem(withTrash, "wooden_club")
        assertTrue(afterDiscard.reaperVault.isEmpty())
        assertEquals(withTrash.gold, afterDiscard.gold)
        assertEquals(withTrash.bankGold, afterDiscard.bankGold)
    }
}
