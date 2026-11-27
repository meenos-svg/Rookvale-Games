package com.example

import com.example.game.model.AmmoKind
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemCategory
import com.example.game.model.ItemDefinition
import com.example.game.model.RecipeCatalog
import com.example.game.model.SkillType
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every equipment line has a piece in every level band, so no class is left without an upgrade. */
class GearCoverageTest {

    private val gear = ItemCatalog.all.values.filter { !it.adminOnly }
    private val crafted = (RecipeCatalog.forgingRecipes + RecipeCatalog.fletchingRecipes + RecipeCatalog.runecraftingRecipes).map { it.outputItemId }.toSet()

    private fun line(name: String, bands: List<Int>, pick: (ItemDefinition) -> Boolean) {
        val pieces = gear.filter(pick)
        val levels = pieces.map { it.requiredLevel }.toSet()
        bands.forEach { band ->
            assertTrue("$name has no piece at Lv $band (has $levels)", band in levels)
        }
        // From Lv 18 up, every band has something you can make yourself
        bands.filter { it >= 18 }.forEach { band ->
            assertTrue("$name has nothing craftable at Lv $band", pieces.any { it.requiredLevel == band && it.id in crafted })
        }
    }

    @Test
    fun everyEquipmentLineReachesFromStarterToTierSix() {
        val all = listOf(1, 8, 28, 36, 45, 60)
        fun melee(skill: SkillType) = { d: ItemDefinition -> d.category == ItemCategory.WEAPON_MELEE && d.requiredSkill?.first == skill }
        line("Swords", all, melee(SkillType.SWORD))
        line("Axes", all, melee(SkillType.AXE))
        line("Clubs", all + 18, melee(SkillType.CLUB))
        line("Bows", all, { it.category == ItemCategory.WEAPON_DISTANCE && it.requiredAmmoKind == AmmoKind.SHAFTS })
        line("Crossbows", listOf(8, 18, 28, 36, 45, 60), { it.category == ItemCategory.WEAPON_DISTANCE && it.requiredAmmoKind == AmmoKind.QUARRELS })
        line("Javelins", all, { it.category == ItemCategory.WEAPON_DISTANCE && it.requiredAmmoKind == null })
        line("Wands", all, { it.category == ItemCategory.WEAPON_WAND })
        for (cat in listOf(ItemCategory.SHIELD, ItemCategory.ARMOR, ItemCategory.HELMET, ItemCategory.LEGS, ItemCategory.BOOTS)) {
            line(cat.name, all, { it.category == cat })
        }
        line("Amulets", listOf(1, 8, 18, 28, 36, 60), { it.category == ItemCategory.AMULET })
        line("Rings", listOf(1, 8, 18, 28, 36, 60), { it.category == ItemCategory.RING })
    }

    @Test
    fun ammoKeepsUpWithTheBowsAndCrossbows() {
        for (kind in listOf(AmmoKind.SHAFTS, AmmoKind.QUARRELS)) {
            val ammo = gear.filter { it.category == ItemCategory.AMMO && it.ammoKind == kind }
            listOf(36, 45, 60).forEach { lvl -> assertTrue("$kind missing Lv $lvl ammo", ammo.any { it.requiredLevel == lvl && it.id in crafted }) }
            // Higher-level ammo always hits harder
            val byLevel = ammo.filter { it.requiredLevel > 1 }.sortedBy { it.requiredLevel }
            byLevel.zipWithNext().forEach { (a, b) -> assertTrue("${b.id} must out-hit ${a.id}", b.attack > a.attack) }
        }
    }
}
