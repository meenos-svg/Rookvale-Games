package com.example

import com.example.game.model.ItemCatalog
import com.example.game.model.LootLevels
import com.example.game.model.LootRarity
import com.example.game.model.MonsterLootTable
import com.example.game.model.MonsterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Best loot is rare, and monsters only drop items suited to their own level. */
class LootRarityAndLevelGatingTest {

    private fun dropRate(type: MonsterType, level: Int, itemId: String, kills: Int = 20_000, seed: Int = 7): Double {
        val rng = Random(seed)
        var hits = 0
        repeat(kills) { if (MonsterLootTable.rollLoot(type, rng, level).items.any { it.itemId == itemId }) hits++ }
        return hits.toDouble() / kills
    }

    @Test
    fun materialsInheritTheLevelOfTheGearTheyAreCraftedInto() {
        assertEquals(1, LootLevels.itemLevel("copper_ore"))
        assertEquals(18, LootLevels.itemLevel("vesperite_ore"))          // feeds tier-3 quenchsteel
        assertTrue(LootLevels.itemLevel("ember_heart") >= 28)              // only feeds tier-4/5 gear
        assertTrue(LootLevels.itemLevel("cinderite_bar") >= 36)
        assertEquals(18, LootLevels.itemLevel("dragon_mail"))
        assertEquals(LootRarity.VERY_RARE, LootLevels.rarityOf("dragon_mail"))
        assertEquals(LootRarity.RARE, LootLevels.rarityOf("rookvale_gladius"))
        assertEquals(LootRarity.COMMON, LootLevels.rarityOf("spider_silk"))
    }

    @Test
    fun lowLevelMonstersNeverDropHighLevelGearOrMaterials() {
        val rng = Random(11)
        repeat(5_000) {
            MonsterLootTable.rollLoot(MonsterType.FIRE_DEVIL, rng, monsterLevel = 10).items.forEach { stack ->
                assertTrue(
                    "Lv 10 Fire Devil dropped ${stack.itemId} (item level ${LootLevels.itemLevel(stack.itemId)})",
                    LootLevels.itemLevel(stack.itemId) <= 10 + LootLevels.LOOT_LEVEL_SLACK
                )
            }
            MonsterLootTable.rollLoot(MonsterType.DRAGON_HATCHLING, rng, monsterLevel = 14).items.forEach { stack ->
                assertFalse("Lv 14 Dragon Hatchling must not drop ${stack.itemId}", stack.itemId in setOf("dragon_shield", "wyrmscale_plate", "fireball_rune"))
            }
        }
        // The same species at a higher level can drop them
        assertTrue(dropRate(MonsterType.DRAGON_HATCHLING, 19, "wyrmscale_plate") > 0.0)
    }

    @Test
    fun bestGearIsRareFromRegularMonstersAndBossesNeedFarming() {
        assertTrue("Skeleton gladius should take ~40+ kills", dropRate(MonsterType.SKELETON, 6, "rookvale_gladius") < 1.0 / 40)
        assertTrue("Tier-3 crosier should take ~100+ kills", dropRate(MonsterType.NECROMANCER, 18, "tallowbone_crosier") < 1.0 / 100)
        val dragonMail = dropRate(MonsterType.ELDER_DRAGON, 20, "dragon_mail")
        assertTrue("Elder Dragon Dragon Mail should be roughly 1 in 6-15 kills (got $dragonMail)", dragonMail in (1.0 / 15)..(1.0 / 6))

        // No boss hands out gear on more than ~35% of kills, even with many gear entries
        for (boss in listOf(MonsterType.WARDEN_STONEHELM, MonsterType.FOREMAN_IRONJAW, MonsterType.BROODMOTHER_ARACHNE, MonsterType.KRAGOR_THE_STONE_EYED)) {
            val rng = Random(3)
            var gearKills = 0
            val kills = 10_000
            repeat(kills) {
                val loot = MonsterLootTable.rollLoot(boss, rng, boss.threatLevel)
                assertTrue("$boss must still drop items every kill", loot.items.isNotEmpty())
                if (loot.items.any { LootLevels.isGear(ItemCatalog.get(it.itemId)) }) gearKills++
            }
            assertTrue("$boss gear on ${gearKills * 100 / kills}% of kills", gearKills <= kills * 0.38)
        }
    }
}
