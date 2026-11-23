package com.example

import com.example.game.cloud.CloudSavePayloadCodec
import com.example.game.engine.CraftingAndMiningEngine
import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemStack
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.RecipeCatalog
import com.example.game.model.SkillProgress
import com.example.game.model.TileType
import com.example.game.model.Vocation
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.world.WorldMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuneMagicSystemTest {

    private fun freshWorld(magicLevel: Int = 1, magicXp: Double = 0.0): GameWorldState {
        val base = GameEngine.createNewWorld(name = "Aelindra", vocation = Vocation.MAGE)
        return base.copy(
            mp = 200,
            maxMp = 200,
            hp = 180,
            maxHp = 220,
            soul = 100,
            maxSoul = 100,
            skills = base.skills.copy(magic = SkillProgress(magicLevel, magicXp))
        )
    }

    @Test
    fun newCharacterStartsAtMagicLevelOneWithBlankAndArcaneDartRunes() {
        val state = GameEngine.createNewWorld(name = "Novice", vocation = Vocation.ADVENTURER)
        assertEquals(1, state.magicLevel)
        assertEquals(0.0, state.magicXp, 0.001)
        assertTrue(state.itemCount("magic_dart_rune") >= 1)
        assertTrue(state.itemCount("blank_rune") >= 1)
    }

    @Test
    fun spendingManaAwardsMagicXpAndAdvancesMagicLevel() {
        var state = freshWorld(magicLevel = 1, magicXp = 0.0)
        val reqLv1 = state.magicXpToNext()
        assertEquals(30.0, reqLv1, 0.01)

        // Spend 35 mana -> should advance to Magic Level 2 with 5 XP rollover
        state = GameEngine.awardMagicXpFromMana(state, manaSpent = 35, nowMs = 1_000L)
        assertEquals(2, state.magicLevel)
        assertEquals(5.0, state.magicXp, 0.05)
        assertTrue(state.magicXpToNext() > reqLv1)
    }

    @Test
    fun runeMagicLevelRequirementsBlockCastingWithoutConsumingResources() {
        val target = MonsterEntity(
            id = 9001,
            type = MonsterType.CAVE_RAT,
            floor = 0,
            x = 28,
            y = 44,
            spawnX = 28,
            spawnY = 44,
            hp = 80,
            maxHp = 80
        )
        // Player at Magic Level 3 cannot cast Healing (Lv 4), HMM (Lv 7), GFB (Lv 12), or SD (Lv 15)
        val state = freshWorld(magicLevel = 3).copy(
            playerX = 26,
            playerY = 44,
            targetMonsterId = target.id,
            monsters = listOf(target),
            backpack = listOf(
                ItemStack("healing_rune", 2),
                ItemStack("hmm_rune", 2),
                ItemStack("fireball_rune", 2),
                ItemStack("sd_rune", 2)
            )
        )

        val blockedHeal = GameEngine.useHealingRune(state, nowMs = 5_000L)
        assertEquals(2, blockedHeal.itemCount("healing_rune"))
        assertEquals(state.mp, blockedHeal.mp)
        assertTrue(blockedHeal.statusLog.last().contains("You need Magic Level 4 to use this rune."))

        val blockedHmm = GameEngine.useAttackRune(state, nowMs = 5_000L, explicitRuneId = "hmm_rune")
        assertEquals(2, blockedHmm.itemCount("hmm_rune"))
        assertEquals(state.mp, blockedHmm.mp)
        assertTrue(blockedHmm.statusLog.last().contains("You need Magic Level 7 to use this rune."))

        val blockedGfb = GameEngine.useAttackRune(state, nowMs = 5_000L, explicitRuneId = "fireball_rune")
        assertEquals(2, blockedGfb.itemCount("fireball_rune"))
        assertTrue(blockedGfb.statusLog.last().contains("You need Magic Level 12 to use this rune."))

        val blockedSd = GameEngine.useAttackRune(state, nowMs = 5_000L, explicitRuneId = "sd_rune")
        assertEquals(2, blockedSd.itemCount("sd_rune"))
        assertTrue(blockedSd.statusLog.last().contains("You need Magic Level 15 to use this rune."))
    }

    @Test
    fun healingRuneRestoresSelfHpEasesPoisonAndGrantsMagicXp() {
        val state = freshWorld(magicLevel = 4).copy(
            hp = 100,
            maxHp = 220,
            poisonTicks = 5,
            backpack = listOf(ItemStack("healing_rune", 3))
        )

        val after = GameEngine.useHealingRune(state, nowMs = 10_000L)
        assertEquals(2, after.itemCount("healing_rune"))
        assertTrue(after.hp > 100)
        assertEquals(2, after.poisonTicks)
        assertTrue(after.mp < state.mp)
        assertTrue(after.magicXp > 0.0 || after.magicLevel > 4)
    }

    @Test
    fun healingRuneCanHealInspectedNearbyCoopAlly() {
        val ally = RemotePlayerState(
            playerId = "ally-42",
            name = "Bram",
            vocation = Vocation.KNIGHT,
            tunicColorIndex = 0,
            hairColorIndex = 0,
            level = 12,
            floor = 0,
            x = 28,
            y = 44,
            hp = 90,
            maxHp = 240
        )
        val state = freshWorld(magicLevel = 5).copy(
            playerX = 26,
            playerY = 44,
            hp = 220,
            maxHp = 220,
            inspectedPlayerId = ally.playerId,
            inspectedPlayerUntilMs = 20_000L,
            backpack = listOf(ItemStack("healing_rune", 2))
        )

        val after = GameEngine.useHealingRune(
            state = state,
            nowMs = 10_000L,
            remotePlayersOnFloor = listOf(ally)
        )
        assertEquals(1, after.itemCount("healing_rune"))
        assertTrue(after.statusLog.any { it.contains("Bram") && it.contains("Intense Healing Rune") })
        assertTrue(after.outboundPlayerEffects.isNotEmpty())
    }

    @Test
    fun greatFireballAndSuddenDeathDealSplashAoeToAdjacentMonsters() {
        val m1 = MonsterEntity(id = 101, type = MonsterType.SKELETON, floor = 0, x = 28, y = 44, spawnX = 28, spawnY = 44, hp = 300, maxHp = 300)
        val m2 = MonsterEntity(id = 102, type = MonsterType.SKELETON, floor = 0, x = 29, y = 44, spawnX = 29, spawnY = 44, hp = 300, maxHp = 300)
        val far = MonsterEntity(id = 103, type = MonsterType.SKELETON, floor = 0, x = 32, y = 44, spawnX = 32, spawnY = 44, hp = 300, maxHp = 300)

        val base = freshWorld(magicLevel = 15).copy(
            playerX = 26,
            playerY = 44,
            targetMonsterId = m1.id,
            monsters = listOf(m1, m2, far),
            backpack = listOf(ItemStack("fireball_rune", 2), ItemStack("sd_rune", 2))
        )

        val afterGfb = GameEngine.useAttackRune(base, nowMs = 10_000L, explicitRuneId = "fireball_rune")
        assertEquals(1, afterGfb.itemCount("fireball_rune"))
        val gfbM1 = afterGfb.monsters.first { it.id == m1.id }
        val gfbM2 = afterGfb.monsters.first { it.id == m2.id }
        val gfbFar = afterGfb.monsters.first { it.id == far.id }
        assertTrue("Primary target should take GFB damage", gfbM1.hp < 300)
        assertTrue("Adjacent monster should take GFB 3x3 splash", gfbM2.hp < 300)
        assertEquals("Distant monster outside 3x3 should be untouched", 300, gfbFar.hp)

        val afterSd = GameEngine.useAttackRune(afterGfb, nowMs = 15_000L, explicitRuneId = "sd_rune")
        assertEquals(1, afterSd.itemCount("sd_rune"))
        assertTrue(afterSd.monsters.first { it.id == m1.id }.hp < gfbM1.hp)
        assertTrue(afterSd.monsters.first { it.id == m2.id }.hp < gfbM2.hp)
    }

    @Test
    fun runecraftingTableValidatesRequirementsAndInscribesRunesWithMagicXp() {
        // Verify Runecrafting Table tiles exist in Rookvale Town on Floor 0
        val townMap = WorldMaps.getFloor(0)
        var tableCount = 0
        for (y in 0 until townMap.height) {
            for (x in 0 until townMap.width) {
                if (townMap.tileAt(x, y) == TileType.RUNECRAFTING_TABLE) tableCount++
            }
        }
        assertTrue("Rookvale should have at least one Runecrafting Table tile", tableCount >= 1)

        // Verify Alaric's Shop sells Blank Runes and does not sell finished tiered runes
        assertTrue(ItemCatalog.shopInventoryIds.contains("blank_rune"))
        assertFalse(ItemCatalog.shopInventoryIds.contains("hmm_rune"))
        assertFalse(ItemCatalog.shopInventoryIds.contains("sd_rune"))
        assertEquals(5, RecipeCatalog.runecraftingRecipes.size)

        // 1. Missing Magic Level blocks inscription
        val lowMagic = freshWorld(magicLevel = 2).copy(
            activePanel = ActivePanel.RUNECRAFTING_TABLE,
            backpack = listOf(ItemStack("blank_rune", 5), ItemStack("spider_silk", 3))
        )
        val blockedMagic = CraftingAndMiningEngine.executeRecipe(lowMagic, "rune_healing", nowMs = 1_000L)
        assertEquals(0, blockedMagic.itemCount("healing_rune"))
        assertTrue(blockedMagic.statusLog.last().contains("Magic Level 4"))

        // 2. Missing Mana blocks inscription
        val lowMana = lowMagic.copy(skills = lowMagic.skills.copy(magic = SkillProgress(5, 0.0)), mp = 10)
        val blockedMana = CraftingAndMiningEngine.executeRecipe(lowMana, "rune_healing", nowMs = 2_000L)
        assertEquals(0, blockedMana.itemCount("healing_rune"))
        assertTrue(blockedMana.statusLog.last().contains("Mana"))

        // 3. Missing Soul blocks inscription
        val lowSoul = lowMana.copy(mp = 100, soul = 0)
        val blockedSoul = CraftingAndMiningEngine.executeRecipe(lowSoul, "rune_healing", nowMs = 3_000L)
        assertEquals(0, blockedSoul.itemCount("healing_rune"))
        assertTrue(blockedSoul.statusLog.last().contains("Soul"))

        // 4. Valid craft succeeds, consumes Blank Rune + Reagent + MP + Soul, grants Healing Runes + Magic XP
        val ready = lowSoul.copy(soul = 20)
        val crafted = CraftingAndMiningEngine.executeRecipe(ready, "rune_healing", nowMs = 4_000L)
        assertEquals(2, crafted.itemCount("healing_rune"))
        assertEquals(4, crafted.itemCount("blank_rune"))
        assertEquals(2, crafted.itemCount("spider_silk"))
        assertEquals(ready.mp - 45, crafted.mp)
        assertEquals(ready.soul - 2, crafted.soul)
        assertTrue(crafted.magicXp > 0.0 || crafted.magicLevel > 5)
    }

    @Test
    fun hotbarRuneBindingAndCloudSaveRoundTripPreserveMagicState() {
        val state = freshWorld(magicLevel = 12, magicXp = 84.5).copy(
            backpack = listOf(
                ItemStack("healing_rune", 4),
                ItemStack("fireball_rune", 6),
                ItemStack("sd_rune", 2)
            )
        )
        val bound = GameEngine.bindRuneToHotbar(
            GameEngine.bindRuneToHotbar(state, "fireball_rune"),
            "healing_rune"
        )
        assertEquals("fireball_rune", bound.boundAttackRuneId)
        assertEquals("healing_rune", bound.boundHealRuneId)
        assertEquals("fireball_rune", bound.resolvedAttackRuneId())
        assertEquals("healing_rune", bound.resolvedHealRuneId())

        val saveData = GameEngine.toSaveData(bound)
        val encoded = CloudSavePayloadCodec.encode(saveData)
        assertNotNull(encoded)
        val decoded = CloudSavePayloadCodec.decode(encoded, saveData.slotId, saveData.name)

        val hydrated = GameEngine.fromSaveData(decoded)
        assertEquals(12, hydrated.magicLevel)
        assertEquals(84.5, hydrated.magicXp, 0.01)
        assertEquals("fireball_rune", hydrated.boundAttackRuneId)
        assertEquals("healing_rune", hydrated.boundHealRuneId)
        assertEquals(4, hydrated.itemCount("healing_rune"))
        assertEquals(6, hydrated.itemCount("fireball_rune"))
        assertEquals(2, hydrated.itemCount("sd_rune"))
    }
}
