package com.example

import com.example.game.engine.EquipmentProcEngine
import com.example.game.engine.GameEngine
import com.example.game.model.EquipmentSlot
import com.example.game.model.EquippedGear
import com.example.game.model.FloatingTextKind
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemStack
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterLootTable
import com.example.game.model.MonsterType
import com.example.game.model.PlayerSkills
import com.example.game.model.ProcTriggerType
import com.example.game.model.RecipeCatalog
import com.example.game.model.SkillProgress
import com.example.game.model.SkillType
import com.example.game.model.Vocation
import com.example.game.world.WorldMaps
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies Rookvale's 5-tier equipment requirements, grandfathered save compatibility,
 * Tier 4/5 Vesperite & Cinderite shields/chests, Ring slot + tiered Necklaces & Rings,
 * automatic weapon/shield/armor/jewelry AOE procs with 2s per-slot ICD, PZ safety,
 * jewelry synergy stacking, XP & loot on AOE kills, and isometric ProcAoeEffect emission.
 */
class EquipmentRequirementsAndProcsTest {

    private class AlwaysTriggerRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextInt(until: Int): Int = 0
        override fun nextDouble(from: Double, until: Double): Double = (from + until) * 0.5
    }

    @Test
    fun allFiveEquipmentTiersHaveExplicitLevelAndSkillRequirements() {
        // Tier 1: Level 1, Skill 1
        val club = ItemCatalog.get("wooden_club")
        assertEquals(1, club.equipmentTier)
        assertEquals(1, club.requiredLevel)
        assertEquals(SkillType.CLUB to 1, club.requiredSkill)

        val woodenShield = ItemCatalog.get("wooden_shield")
        assertEquals(1, woodenShield.equipmentTier)
        assertEquals(1, woodenShield.requiredLevel)
        assertEquals(SkillType.SHIELDING to 1, woodenShield.requiredSkill)

        // Tier 2: Level 8, Skill 12
        val falchion = ItemCatalog.get("duskmetal_falchion")
        assertEquals(2, falchion.equipmentTier)
        assertEquals(8, falchion.requiredLevel)
        assertEquals(SkillType.SWORD to 12, falchion.requiredSkill)

        val dwarvenShield = ItemCatalog.get("steel_shield")
        assertEquals(2, dwarvenShield.equipmentTier)
        assertEquals(8, dwarvenShield.requiredLevel)
        assertEquals(SkillType.SHIELDING to 12, dwarvenShield.requiredSkill)

        // Tier 3: Level 18, Skill 22
        val quenchBlade = ItemCatalog.get("quenchsteel_bastard")
        assertEquals(3, quenchBlade.equipmentTier)
        assertEquals(18, quenchBlade.requiredLevel)
        assertEquals(SkillType.SWORD to 22, quenchBlade.requiredSkill)

        val wyrmscaleShield = ItemCatalog.get("dragon_shield")
        assertEquals(3, wyrmscaleShield.equipmentTier)
        assertEquals(18, wyrmscaleShield.requiredLevel)
        assertEquals(SkillType.SHIELDING to 22, wyrmscaleShield.requiredSkill)

        // Tier 4: Level 28, Skill 32 (Vesperite weapons + shield + chest)
        val vespLongblade = ItemCatalog.get("vesperite_longblade")
        assertEquals(4, vespLongblade.equipmentTier)
        assertEquals(28, vespLongblade.requiredLevel)
        assertEquals(SkillType.SWORD to 32, vespLongblade.requiredSkill)
        assertEquals("Astral Cleave", vespLongblade.proc?.name)

        val vespBulwark = ItemCatalog.get("vesperite_bulwark")
        assertEquals(4, vespBulwark.equipmentTier)
        assertEquals(28, vespBulwark.requiredLevel)
        assertEquals(SkillType.SHIELDING to 32, vespBulwark.requiredSkill)
        assertEquals("Aegis Retaliation", vespBulwark.proc?.name)
        assertEquals(ProcTriggerType.ON_DEFEND, vespBulwark.proc?.triggerType)

        val vespMail = ItemCatalog.get("vesperite_aegis_mail")
        assertEquals(4, vespMail.equipmentTier)
        assertEquals(28, vespMail.requiredLevel)
        assertEquals(SkillType.SHIELDING to 32, vespMail.requiredSkill)
        assertEquals("Ember Thorns", vespMail.proc?.name)

        // Tier 5: Level 45, Skill 42 (Cinderite / Ashen weapons + shield + chest)
        val cindGreatsword = ItemCatalog.get("cinderite_greatsword")
        assertEquals(5, cindGreatsword.equipmentTier)
        assertEquals(45, cindGreatsword.requiredLevel)
        assertEquals(SkillType.SWORD to 42, cindGreatsword.requiredSkill)
        assertEquals("Cataclysm Wave", cindGreatsword.proc?.name)

        val cindBastion = ItemCatalog.get("cinderite_bastion")
        assertEquals(5, cindBastion.equipmentTier)
        assertEquals(45, cindBastion.requiredLevel)
        assertEquals(SkillType.SHIELDING to 42, cindBastion.requiredSkill)
        assertEquals("Aegis Retaliation", cindBastion.proc?.name)
        assertEquals(ProcTriggerType.ON_DEFEND, cindBastion.proc?.triggerType)

        val cindWarplate = ItemCatalog.get("cinderite_warplate")
        assertEquals(5, cindWarplate.equipmentTier)
        assertEquals(45, cindWarplate.requiredLevel)
        assertEquals(SkillType.SHIELDING to 42, cindWarplate.requiredSkill)
        assertEquals("Ember Thorns", cindWarplate.proc?.name)
    }

    @Test
    fun allTenNamedVesperiteAndCinderiteWeaponProcsExistWithValidRatesAndRadius() {
        val expectedWeaponProcs = mapOf(
            "vesperite_longblade" to "Astral Cleave",
            "cinderite_greatsword" to "Cataclysm Wave",
            "vesperite_cleaver" to "Shatter Pulse",
            "cinderite_greataxe" to "Magma Eruption",
            "vesperite_warmaul" to "Thunderous Slam",
            "cinderite_worldbreaker" to "Meteor Impact",
            "vesperite_recurve_bow" to "Starfall Volley",
            "cinderite_ashen_bow" to "Ember Rain",
            "vesperite_conduit_staff" to "Void Chain",
            "cinderite_soulspire" to "Inferno Nova"
        )
        expectedWeaponProcs.forEach { (itemId, procName) ->
            val def = ItemCatalog.get(itemId)
            val proc = def.proc
            assertNotNull("$itemId must define a specialized proc", proc)
            assertEquals(procName, proc!!.name)
            assertEquals(ProcTriggerType.ON_ATTACK, proc.triggerType)
            assertTrue("$itemId proc chance should be 15..25%", proc.triggerChancePercent in 15..25)
            assertTrue("$itemId AOE radius should be 1.5..2.8 tiles", proc.aoeRadiusTiles in 1.5f..2.8f)
            assertTrue("$itemId must hit multiple monsters", proc.maxTargets >= 2)
        }
    }

    @Test
    fun backpackEquipEnforcesLevelAndSkillRequirementsAndGrandfathersLoadedGear() {
        val lowLevel = GameEngine.createNewWorld("Aric").copy(
            level = 5,
            equipment = EquippedGear(),
            skills = PlayerSkills(sword = SkillProgress(10), shielding = SkillProgress(10)),
            backpack = listOf(ItemStack("vesperite_longblade", 1), ItemStack("cinderite_bastion", 1))
        )

        // Blocked by both level and Sword skill
        val blocked = GameEngine.useOrEquipBackpackItem(lowLevel, "vesperite_longblade", 1000L)
        assertNull("Under-level player must not equip Vesperite Longblade", blocked.equipment.weaponId)
        assertEquals(1, blocked.itemCount("vesperite_longblade"))
        assertTrue(
            "Status log must explain exact missing Level & Sword requirements",
            blocked.statusLog.last().contains("Level 28") && blocked.statusLog.last().contains("Sword")
        )
        assertTrue(blocked.floatingTexts.any { it.text == "REQ UNMET" })

        // Level met (28) but Sword skill too low (20 < 32) -> still blocked
        val skillShort = lowLevel.copy(level = 28, skills = PlayerSkills(sword = SkillProgress(20)))
        val blockedSkill = GameEngine.useOrEquipBackpackItem(skillShort, "vesperite_longblade", 2000L)
        assertNull(blockedSkill.equipment.weaponId)
        assertTrue(blockedSkill.statusLog.last().contains("Sword Fighting 32"))

        // Both level & Sword met -> equips cleanly
        val qualified = lowLevel.copy(level = 28, skills = PlayerSkills(sword = SkillProgress(32)))
        val equipped = GameEngine.useOrEquipBackpackItem(qualified, "vesperite_longblade", 3000L)
        assertEquals("vesperite_longblade", equipped.equipment.weaponId)
        assertEquals(0, equipped.itemCount("vesperite_longblade"))
        assertTrue(equipped.statusLog.last().contains("Astral Cleave"))

        // Grandfathered save: low-level character already wearing Tier 5 sword keeps it until unequipped
        val grandfatheredState = lowLevel.copy(
            equipment = EquippedGear(weaponId = "cinderite_greatsword", ringId = "vesperite_catalyst_ring")
        )
        val saved = GameEngine.toSaveData(grandfatheredState)
        val reloaded = GameEngine.fromSaveData(saved)
        assertEquals("Grandfathered weapon must stay equipped on load", "cinderite_greatsword", reloaded.equipment.weaponId)
        assertEquals("Ring slot must round-trip cleanly through save/load", "vesperite_catalyst_ring", reloaded.equipment.ringId)

        // Once unequipped, re-equip is strictly enforced
        val unequipped = GameEngine.unequipSlot(reloaded, EquipmentSlot.WEAPON)
        assertNull(unequipped.equipment.weaponId)
        assertEquals(1, unequipped.itemCount("cinderite_greatsword"))
        val reEquipAttempt = GameEngine.useOrEquipBackpackItem(unequipped, "cinderite_greatsword", 4000L)
        assertNull("Re-equipping grandfathered gear below requirements must be blocked", reEquipAttempt.equipment.weaponId)
    }

    @Test
    fun newNecklacesAndRingsStackProcSynergyAndAreCraftableAndDroppable() {
        val jewelryIds = listOf(
            "life_amulet",
            "bog_iron_ward_pendant",
            "stormglass_pendant",
            "vesperite_resonance_necklace",
            "cinderheart_sovereign_torc",
            "sunbrass_signet",
            "duskmetal_vampire_band",
            "frostfang_coil",
            "vesperite_catalyst_ring",
            "cinderite_overlord_band"
        )
        jewelryIds.forEach { id ->
            val def = ItemCatalog.get(id)
            assertTrue("$id must equip in AMULET or RING", def.equipSlot == EquipmentSlot.AMULET || def.equipSlot == EquipmentSlot.RING)
        }

        // Verify Jewelry Anvil recipes exist for newly crafted jewelry + T4/T5 shields/chests
        val forgeOutputs = RecipeCatalog.forgingRecipes.map { it.outputItemId }.toSet()
        assertTrue("vesperite_bulwark" in forgeOutputs)
        assertTrue("vesperite_aegis_mail" in forgeOutputs)
        assertTrue("cinderite_bastion" in forgeOutputs)
        assertTrue("cinderite_warplate" in forgeOutputs)
        assertTrue("vesperite_resonance_necklace" in forgeOutputs)
        assertTrue("cinderheart_sovereign_torc" in forgeOutputs)
        assertTrue("vesperite_catalyst_ring" in forgeOutputs)
        assertTrue("cinderite_overlord_band" in forgeOutputs)

        // Verify Kragor & Vaelgor boss loot tables include T4/T5 shields/chests & jewelry
        val kragorPool = MonsterLootTable.entryFor(MonsterType.KRAGOR_THE_STONE_EYED).weightedDrops.map { it.itemId }.toSet()
        assertTrue("vesperite_bulwark" in kragorPool)
        assertTrue("vesperite_aegis_mail" in kragorPool)
        assertTrue("vesperite_resonance_necklace" in kragorPool)
        assertTrue("vesperite_catalyst_ring" in kragorPool)

        val vaelgorPool = (MonsterLootTable.entryFor(MonsterType.VULKARIS_THE_FORGE_TITAN).weightedDrops +
            MonsterLootTable.entryFor(MonsterType.IGNIVAR_ASH_WING_WYRM).weightedDrops).map { it.itemId }.toSet()
        assertTrue("cinderite_bastion" in vaelgorPool)
        assertTrue("cinderite_warplate" in vaelgorPool)
        assertTrue("cinderheart_sovereign_torc" in vaelgorPool)
        assertTrue("cinderite_overlord_band" in vaelgorPool)

        // Verify stacking proc bonuses + Max HP
        val state = GameEngine.createNewWorld("Lyra").copy(
            level = 45,
            maxHp = 500,
            hp = 500,
            equipment = EquippedGear(
                weaponId = "cinderite_greatsword", // 25% base proc chance
                amuletId = "cinderheart_sovereign_torc", // +8% chance, +25% power, +1 target, +60 Max HP
                ringId = "cinderite_overlord_band" // +8% chance, +25% power, +1 target, +8 ATK
            )
        )
        assertEquals(16, state.equipment.totalProcChanceBonusPct())
        assertEquals(50, state.equipment.totalProcPowerBonusPct())
        assertEquals(2, state.equipment.totalProcExtraTargets())
        assertEquals(60, state.equipment.totalBonusMaxHp())
        assertEquals(560, state.effectiveMaxHp())
        val weaponProc = ItemCatalog.get("cinderite_greatsword").proc!!
        assertEquals(41, state.effectiveProcChancePercent(weaponProc))
    }

    @Test
    fun weaponAoeProcHitsMultipleHostilesTriggersAggroAwardsXpAndLootAndRespectsIcdAndPz() {
        // Place hero outside Protection Zone (Rookvale city PZ is x in 147..255, y in 149..227)
        val px = 280
        val py = 200
        assertFalse("Test tile must be outside Protection Zone", WorldMaps.getFloor(0).isProtectionZone(px, py))

        val m1 = MonsterEntity(id = 101, type = MonsterType.ORC_WARRIOR, floor = 0, x = px + 1, y = py, spawnX = px + 1, spawnY = py, hp = 400, maxHp = 400)
        val m2 = MonsterEntity(id = 102, type = MonsterType.ORC_WARRIOR, floor = 0, x = px + 2, y = py, spawnX = px + 2, spawnY = py, hp = 15, maxHp = 120) // Dies to AOE!
        val m3 = MonsterEntity(id = 103, type = MonsterType.ORC_WARRIOR, floor = 0, x = px + 1, y = py + 1, spawnX = px + 1, spawnY = py + 1, hp = 400, maxHp = 400)
        val farMonster = MonsterEntity(id = 104, type = MonsterType.ORC_WARRIOR, floor = 0, x = px + 8, y = py + 8, spawnX = px + 8, spawnY = py + 8, hp = 400, maxHp = 400)

        val baseState = GameEngine.createNewWorld("Kael").copy(
            saveId = "hero_kael",
            level = 42,
            vocation = Vocation.KNIGHT,
            xp = 0L,
            floor = 0,
            playerX = px,
            playerY = py,
            hp = 600,
            maxHp = 600,
            skills = PlayerSkills(sword = SkillProgress(45), shielding = SkillProgress(45)),
            equipment = EquippedGear(
                weaponId = "cinderite_greatsword" // Cataclysm Wave: 2.4 tile radius, up to 5 targets
            ),
            monsters = listOf(m1, m2, m3, farMonster),
            corpses = emptyList(),
            floatingTexts = emptyList(),
            procAoeEffects = emptyList()
        )

        val afterProc = EquipmentProcEngine.resolveAttackProcs(
            state = baseState,
            primaryTarget = m1,
            baseHitDamage = 90,
            nowMs = 10_000L,
            rng = AlwaysTriggerRandom()
        )

        // m1 & m3 damaged and aggroed; m2 killed -> XP gained & corpse spawned; farMonster untouched
        val m1After = afterProc.monsters.first { it.id == 101 }
        val m3After = afterProc.monsters.first { it.id == 103 }
        val farAfter = afterProc.monsters.first { it.id == 104 }

        assertTrue("Primary target must take proc damage", m1After.hp < m1.hp)
        assertTrue("Nearby monster within AOE radius must take proc damage", m3After.hp < m3.hp)
        assertEquals("Nearby monster hit by AOE must aggro the player", "hero_kael", m3After.targetPlayerId)
        assertEquals("Monster outside AOE radius must be untouched", farMonster.hp, farAfter.hp)
        assertNull("Monster outside AOE radius must not aggro", farAfter.targetPlayerId)

        assertTrue("Low-HP monster killed by AOE proc must be removed from live monsters", afterProc.monsters.none { it.id == 102 })
        assertTrue("AOE proc kill must award full monster XP", afterProc.xp >= MonsterType.ORC_WARRIOR.xpReward)
        assertTrue("AOE proc kill must spawn a loot corpse", afterProc.corpses.isNotEmpty())

        // Visual feedback: ProcAoeEffect + PROC_CALLOUT floating text
        assertTrue("Must emit isometric ProcAoeEffect", afterProc.procAoeEffects.isNotEmpty())
        assertTrue(
            "Must show floating proc callout 'CATACLYSM WAVE!'",
            afterProc.floatingTexts.any { it.kind == FloatingTextKind.PROC_CALLOUT && it.text == "CATACLYSM WAVE!" }
        )

        // Internal cooldown check: calling again 500ms later (< 2000ms ICD) must NOT re-trigger weapon proc
        val effectCountBeforeIcd = afterProc.procAoeEffects.size
        val duringIcd = EquipmentProcEngine.resolveAttackProcs(
            state = afterProc,
            primaryTarget = m1After,
            baseHitDamage = 90,
            nowMs = 10_500L,
            rng = AlwaysTriggerRandom()
        )
        assertEquals("Weapon proc must respect 2-second ICD", effectCountBeforeIcd, duringIcd.procAoeEffects.size)

        // Calling 2100ms later (>= 2000ms ICD) triggers again
        val afterIcd = EquipmentProcEngine.resolveAttackProcs(
            state = duringIcd,
            primaryTarget = m1After,
            baseHitDamage = 90,
            nowMs = 12_100L,
            rng = AlwaysTriggerRandom()
        )
        assertTrue("Weapon proc must fire again after 2-second ICD", afterIcd.procAoeEffects.size > effectCountBeforeIcd)

        // Protection Zone safety: inside Temple PZ, procs never fire
        val pzState = baseState.copy(playerX = WorldMaps.TEMPLE_SPAWN_X, playerY = WorldMaps.TEMPLE_SPAWN_Y)
        assertTrue(WorldMaps.getFloor(0).isProtectionZone(pzState.playerX, pzState.playerY))
        val pzResult = EquipmentProcEngine.resolveAttackProcs(
            state = pzState,
            primaryTarget = m1,
            baseHitDamage = 90,
            nowMs = 20_000L,
            rng = AlwaysTriggerRandom()
        )
        assertTrue("No procs may trigger inside Protection Zone", pzResult.procAoeEffects.isEmpty())
    }

    @Test
    fun defensiveShieldAndArmorProcsTriggerIndependentlyWithPerSlotIcd() {
        val px = 280
        val py = 200
        val attacker = MonsterEntity(id = 201, type = MonsterType.ORC_WARRIOR, floor = 0, x = px + 1, y = py, spawnX = px + 1, spawnY = py, hp = 500, maxHp = 500)
        val adjacent = MonsterEntity(id = 202, type = MonsterType.ORC_WARRIOR, floor = 0, x = px, y = py + 1, spawnX = px, spawnY = py + 1, hp = 500, maxHp = 500)

        val state = GameEngine.createNewWorld("Bram").copy(
            saveId = "hero_bram",
            level = 42,
            vocation = Vocation.KNIGHT,
            floor = 0,
            playerX = px,
            playerY = py,
            hp = 450,
            maxHp = 600,
            skills = PlayerSkills(shielding = SkillProgress(45)),
            equipment = EquippedGear(
                weaponId = "vesperite_longblade", // 1H so shield is active
                shieldId = "vesperite_bulwark",   // Aegis Retaliation (ON_DEFEND)
                armorId = "cinderite_warplate"    // Ember Thorns (ON_DEFEND)
            ),
            monsters = listOf(attacker, adjacent),
            procAoeEffects = emptyList(),
            floatingTexts = emptyList()
        )

        val afterDefend = EquipmentProcEngine.resolveDefenseProcs(
            state = state,
            attacker = attacker,
            nowMs = 15_000L,
            rng = AlwaysTriggerRandom()
        )

        // Both Shield (Aegis Retaliation) and Armor (Ember Thorns) have independent slots & should trigger
        assertEquals("Both shield & armor defensive procs must emit VFX", 2, afterDefend.procAoeEffects.size)
        assertTrue(afterDefend.floatingTexts.any { it.text == "AEGIS RETALIATION!" })
        assertTrue(afterDefend.floatingTexts.any { it.text == "EMBER THORNS!" })
        assertTrue("Adjacent monster within 1.5 tiles must take defensive proc damage", afterDefend.monsters.first { it.id == 202 }.hp < adjacent.hp)
        assertTrue("Ember Thorns must ignite monsters with burnTicks", afterDefend.monsters.first { it.id == 201 }.burnTicks > 0)
        assertTrue("Aegis Retaliation must heal hero slightly via AEGIS_WARD", afterDefend.hp > state.hp)
    }
}
