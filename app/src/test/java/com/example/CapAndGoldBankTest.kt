package com.example

import com.example.game.cloud.CloudSavePayloadCodec
import com.example.game.engine.GameEngine
import com.example.game.model.ADVENTURER_CAP_PER_LEVEL_OZ
import com.example.game.model.BASE_MAX_CAP_OZ
import com.example.game.model.GOLD_COIN_WEIGHT_OZ
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemStack
import com.example.game.model.ORACLE_MIN_LEVEL
import com.example.game.model.Vocation
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CapAndGoldBankTest {

    @Test
    fun upgradedCapFormula_scalesAcrossLevelsAndOracleVocations() {
        assertEquals(1500.0, BASE_MAX_CAP_OZ, 0.001)
        assertEquals(125.0, ADVENTURER_CAP_PER_LEVEL_OZ, 0.001)

        // Adventurer Levels 1, 10, 20
        assertEquals(1500.0, GameWorldState.maxCapForLevel(1, Vocation.ADVENTURER), 0.01)
        assertEquals(2625.0, GameWorldState.maxCapForLevel(10, Vocation.ADVENTURER), 0.01)
        assertEquals(3875.0, GameWorldState.maxCapForLevel(ORACLE_MIN_LEVEL, Vocation.ADVENTURER), 0.01)

        // Proportionally scaled Oracle Vocation promotions at Level 20 & Level 25
        assertEquals(5125.0, GameWorldState.maxCapForLevel(20, Vocation.KNIGHT), 0.01)
        assertEquals(6125.0, GameWorldState.maxCapForLevel(25, Vocation.KNIGHT), 0.01)

        assertEquals(4775.0, GameWorldState.maxCapForLevel(20, Vocation.PALADIN), 0.01)
        assertEquals(5575.0, GameWorldState.maxCapForLevel(25, Vocation.PALADIN), 0.01)

        assertEquals(4475.0, GameWorldState.maxCapForLevel(20, Vocation.MAGE), 0.01)
        assertEquals(5100.0, GameWorldState.maxCapForLevel(25, Vocation.MAGE), 0.01)
    }

    @Test
    fun stackableResourceAndGoldWeightsAreReduced() {
        assertEquals(0.01, GOLD_COIN_WEIGHT_OZ, 0.0001)
        assertEquals(2.0, ItemCatalog.get("wood_log").weightOz, 0.001)
        assertEquals(0.1, ItemCatalog.get("wild_feather").weightOz, 0.001)
        assertEquals(0.3, ItemCatalog.get("blank_rune").weightOz, 0.001)
        assertEquals(0.1, ItemCatalog.get("wooden_arrows").weightOz, 0.001)
        assertEquals(0.12, ItemCatalog.get("ochre_shafts").weightOz, 0.001)
        assertEquals(0.15, ItemCatalog.get("venom_shafts").weightOz, 0.001)
        assertEquals(0.18, ItemCatalog.get("starlight_quarrels").weightOz, 0.001)
        assertEquals(0.2, ItemCatalog.get("quenchsteel_quarrels").weightOz, 0.001)

        val emptyHero = GameWorldState(gold = 10_000, bankGold = 500_000L)
        // 10,000 carried gp * 0.01 oz = 100.0 oz; 500,000 bankGold = 0.0 oz
        assertEquals(100.0, emptyHero.usedCapOz(), 0.01)
    }

    @Test
    fun bankVaultDepositWithdrawAndCapClampingWorkCleanly() {
        val start = GameEngine.createNewWorld("Aldric").copy(gold = 2_500, bankGold = 0L)
        val weightBeforeDeposit = start.usedCapOz()

        // Deposit 100
        val after100 = GameEngine.depositGold(start, 100)
        assertEquals(2_400, after100.gold)
        assertEquals(100L, after100.bankGold)
        assertEquals(weightBeforeDeposit - 1.0, after100.usedCapOz(), 0.01)

        // Deposit 1,000
        val after1100 = GameEngine.depositGold(after100, 1_000)
        assertEquals(1_400, after1100.gold)
        assertEquals(1_100L, after1100.bankGold)

        // Deposit All
        val afterAll = GameEngine.depositAllGold(after1100)
        assertEquals(0, afterAll.gold)
        assertEquals(2_500L, afterAll.bankGold)
        assertEquals(weightBeforeDeposit - 25.0, afterAll.usedCapOz(), 0.01)

        // Withdraw 1,000
        val afterW1000 = GameEngine.withdrawGold(afterAll, 1_000L)
        assertEquals(1_000, afterW1000.gold)
        assertEquals(1_500L, afterW1000.bankGold)

        // Withdraw All with tight Cap -> withdraws only what fits at 0.01 oz/gp
        val used = afterW1000.usedCapOz()
        val tight = afterW1000.copy(maxCapOz = used + 5.0) // room for 500 gp
        val partialWithdraw = GameEngine.withdrawAllGold(tight)
        assertEquals(1_500, partialWithdraw.gold)
        assertEquals(1_000L, partialWithdraw.bankGold)
        assertTrue(partialWithdraw.statusLog.last().contains("Cap limit reached"))

        // Invalid non-positive amounts are rejected without mutating balances
        val invalidDep = GameEngine.depositGold(partialWithdraw, 0)
        assertEquals(partialWithdraw.gold, invalidDep.gold)
        assertEquals(partialWithdraw.bankGold, invalidDep.bankGold)
        val invalidWith = GameEngine.withdrawGold(partialWithdraw, -50L)
        assertEquals(partialWithdraw.gold, invalidWith.gold)
        assertEquals(partialWithdraw.bankGold, invalidWith.bankGold)
    }

    @Test
    fun npcShopsSpendCarriedGoldOnlyAndNeverTouchBankGold() {
        val hero = GameEngine.createNewWorld("AlaricGuest").copy(
            gold = 10,
            bankGold = 50_000L
        )
        // Health potion costs 45 gp -> must fail because carried gold is only 10 gp
        val attempted = GameEngine.buyShopItem(hero, "health_potion")
        assertEquals(10, attempted.gold)
        assertEquals(50_000L, attempted.bankGold)
        assertTrue(attempted.statusLog.last().contains("Not enough Gold"))

        // After withdrawing 100 gp from Bank, purchase succeeds using carried gold
        val withdrawn = GameEngine.withdrawGold(attempted, 100L)
        val bought = GameEngine.buyShopItem(withdrawn, "health_potion")
        assertEquals(65, bought.gold)
        assertEquals(49_900L, bought.bankGold)
    }

    @Test
    fun playerDeathDropsCarriedGoldIntoFallenBackpackAndProtectsBankGold() {
        val doomed = GameEngine.createNewWorld("Vex").copy(
            xp = 1_000L,
            level = 4,
            floor = -1,
            playerX = 16,
            playerY = 16,
            hp = 1,
            poisonTicks = 3,
            gold = 1_234,
            bankGold = 25_000L,
            backpack = listOf(ItemStack("wood_log", 5))
        )
        val afterDeath = GameEngine.processCombatTick(doomed, 5_000L)

        // Carried gold drops into Fallen Backpack; bankGold stays 100% safe
        assertEquals(0, afterDeath.gold)
        assertEquals(25_000L, afterDeath.bankGold)
        val fallen = afterDeath.corpses.firstOrNull { it.isPlayerCorpse }
        assertNotNull("Player corpse must spawn with dropped gold", fallen)
        assertEquals(1_234, fallen!!.gold)
        assertEquals(1_234, afterDeath.deathReport?.droppedGold)
        assertEquals(25_000L, afterDeath.deathReport?.safeBankGold)

        // Looting the Fallen Backpack recovers all dropped gold at 0.01 oz/gp
        val recovered = GameEngine.lootGoldFromCorpse(afterDeath, fallen.id, 6_000L)
        assertEquals(1_234, recovered.gold)
        assertEquals(25_000L, recovered.bankGold)
    }

    @Test
    fun bulkDepotItemDepositAndWithdrawSupport1_5_AndAllWithCapSafety() {
        val start = GameEngine.createNewWorld("Lumberjack").copy(
            backpack = listOf(ItemStack("wood_log", 12)),
            depot = emptyList()
        )

        // Deposit 1
        val after1 = GameEngine.depositItem(start, "wood_log", 1)
        assertEquals(11, after1.itemCount("wood_log"))
        assertEquals(1, after1.depot.first { it.itemId == "wood_log" }.count)

        // Deposit 5
        val after5 = GameEngine.depositItem(after1, "wood_log", 5)
        assertEquals(6, after5.itemCount("wood_log"))
        assertEquals(6, after5.depot.first { it.itemId == "wood_log" }.count)

        // Deposit All (remaining 6)
        val afterAll = GameEngine.depositItem(after5, "wood_log", 6)
        assertEquals(0, afterAll.itemCount("wood_log"))
        assertEquals(12, afterAll.depot.first { it.itemId == "wood_log" }.count)

        // Withdraw 5
        val withdrew5 = GameEngine.withdrawItem(afterAll, "wood_log", 5)
        assertEquals(5, withdrew5.itemCount("wood_log"))
        assertEquals(7, withdrew5.depot.first { it.itemId == "wood_log" }.count)

        // Withdraw All with tight Cap (wood_log = 2.0 oz each; allow only 3 logs = 6.0 oz)
        val tight = withdrew5.copy(maxCapOz = withdrew5.usedCapOz() + 6.05)
        val withdrewCapped = GameEngine.withdrawItem(tight, "wood_log", 7)
        assertEquals(8, withdrewCapped.itemCount("wood_log"))
        assertEquals(4, withdrewCapped.depot.first { it.itemId == "wood_log" }.count)
        assertTrue(withdrewCapped.statusLog.last().contains("Cap limit reached"))
    }

    @Test
    fun legacySupabaseSaveWithoutBankGoldAndLowCapUpgradesAutomaticallyOnLoad() {
        val legacyJson = JSONObject().apply {
            put("slotId", "legacy_20")
            put("name", "OldHero")
            put("vocationName", "ADVENTURER")
            put("level", 20)
            put("xp", 20_000L)
            put("hp", 340)
            put("maxHp", 340)
            put("mp", 245)
            put("maxMp", 245)
            put("soul", 100)
            put("maxSoul", 100)
            put("cap", 875.0) // Old pre-upgrade Level 20 cap
            put("gold", 500)
            // Intentionally omit "bankGold"
        }

        val decoded = CloudSavePayloadCodec.decode(legacyJson, "legacy_20", "OldHero")
        assertEquals(0L, decoded.bankGold)
        assertEquals(3875.0, decoded.maxCapOz, 0.01)

        val hydrated = GameEngine.fromSaveData(decoded)
        assertEquals(0L, hydrated.bankGold)
        assertEquals(3875.0, hydrated.maxCapOz, 0.01)

        // Saving after depositing gold persists bankGold cleanly in JSON
        val banked = GameEngine.depositGold(hydrated, 350)
        val encoded = CloudSavePayloadCodec.encode(GameEngine.toSaveData(banked))
        assertEquals(350L, encoded.getLong("bankGold"))
        val roundTripped = CloudSavePayloadCodec.decode(encoded, "legacy_20", "OldHero")
        assertEquals(350L, roundTripped.bankGold)
        assertEquals(150, roundTripped.gold)
    }
}
