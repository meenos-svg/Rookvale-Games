package com.example.game.engine

import com.example.game.model.EquipmentProc
import com.example.game.model.FloatingTextKind
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCategory
import com.example.game.model.MonsterEntity
import com.example.game.multiplayer.PlayerActionState
import com.example.game.model.ProcAoeEffect
import com.example.game.model.ProcBonusEffect
import com.example.game.model.ProcTriggerType
import com.example.game.model.ProcVisualStyle
import com.example.game.world.WorldMaps
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Resolves automatic percentage-chance specialized equipment abilities (weapons, shields,
 * chest armor, necklaces, and rings) with independent 2-second per-slot internal cooldowns,
 * Protection Zone safety, hostile-only multi-monster AOE targeting, bonus status/siphon
 * effects, full XP/loot rewards on kill, and isometric VFX emission.
 */
object EquipmentProcEngine {

    const val PROC_SLOT_COOLDOWN_MS: Long = 2_000L
    const val PROC_EFFECT_DURATION_MS: Long = 560L

    /**
     * Rolls all equipped ON_ATTACK procs (Weapon, Necklace, Ring) after a successful player hit.
     */
    fun resolveAttackProcs(
        state: GameWorldState,
        primaryTarget: MonsterEntity,
        baseHitDamage: Int,
        nowMs: Long,
        rng: Random = Random.Default
    ): GameWorldState {
        val floorMap = WorldMaps.getFloor(state.floor)
        if (floorMap.isProtectionZone(state.playerX, state.playerY)) return state
        if (state.hp <= 0) return state

        var s = state
        val weaponDef = s.equipment.weaponDef()
        val isMagicWeapon = weaponDef?.category == ItemCategory.WEAPON_WAND
        val basePower = max(baseHitDamage, ((weaponDef?.attack ?: 14) + s.equipment.totalAccessoryAttackBonus() + s.level).coerceAtLeast(14))

        // 1. Weapon ON_ATTACK proc
        val weaponProc = weaponDef?.proc
        if (weaponProc != null &&
            weaponProc.triggerType == ProcTriggerType.ON_ATTACK &&
            (nowMs - s.lastWeaponProcAtMs) >= PROC_SLOT_COOLDOWN_MS
        ) {
            val chance = s.effectiveProcChancePercent(weaponProc)
            if (rng.nextInt(100) < chance) {
                val (cx, cy) = if (weaponDef.category == ItemCategory.WEAPON_MELEE && weaponProc.visualStyle == ProcVisualStyle.SHOCKWAVE_RING) {
                    primaryTarget.x.toFloat() to primaryTarget.y.toFloat()
                } else {
                    primaryTarget.x.toFloat() to primaryTarget.y.toFloat()
                }
                val resolved = executeAoeProc(
                    state = s,
                    proc = weaponProc,
                    centerX = cx,
                    centerY = cy,
                    preferredTargetId = primaryTarget.id,
                    basePower = basePower.toDouble(),
                    isMagic = isMagicWeapon ||
                        weaponProc.visualStyle == ProcVisualStyle.CHAIN_ARC ||
                        weaponProc.visualStyle == ProcVisualStyle.ARCANE_NOVA ||
                        weaponProc.visualStyle == ProcVisualStyle.ACID_SPLASH ||
                        weaponProc.visualStyle == ProcVisualStyle.FROST_SHATTER,
                    nowMs = nowMs,
                    rng = rng
                )
                if (resolved.triggered) {
                    s = resolved.state.copy(lastWeaponProcAtMs = nowMs)
                }
            }
        }

        // 2. Necklace / Amulet ON_ATTACK proc
        val amuletProc = s.equipment.amuletDef()?.proc
        if (amuletProc != null &&
            amuletProc.triggerType == ProcTriggerType.ON_ATTACK &&
            (nowMs - s.lastAmuletProcAtMs) >= PROC_SLOT_COOLDOWN_MS
        ) {
            val chance = s.effectiveProcChancePercent(amuletProc)
            if (rng.nextInt(100) < chance) {
                val resolved = executeAoeProc(
                    state = s,
                    proc = amuletProc,
                    centerX = primaryTarget.x.toFloat(),
                    centerY = primaryTarget.y.toFloat(),
                    preferredTargetId = primaryTarget.id,
                    basePower = (basePower * 0.92).coerceAtLeast(14.0),
                    isMagic = true,
                    nowMs = nowMs,
                    rng = rng
                )
                if (resolved.triggered) {
                    s = resolved.state.copy(lastAmuletProcAtMs = nowMs)
                }
            }
        }

        // 3. Ring ON_ATTACK proc
        val ringProc = s.equipment.ringDef()?.proc
        if (ringProc != null &&
            ringProc.triggerType == ProcTriggerType.ON_ATTACK &&
            (nowMs - s.lastRingProcAtMs) >= PROC_SLOT_COOLDOWN_MS
        ) {
            val chance = s.effectiveProcChancePercent(ringProc)
            if (rng.nextInt(100) < chance) {
                val resolved = executeAoeProc(
                    state = s,
                    proc = ringProc,
                    centerX = primaryTarget.x.toFloat(),
                    centerY = primaryTarget.y.toFloat(),
                    preferredTargetId = primaryTarget.id,
                    basePower = (basePower * 0.90).coerceAtLeast(14.0),
                    isMagic = true,
                    nowMs = nowMs,
                    rng = rng
                )
                if (resolved.triggered) {
                    s = resolved.state.copy(lastRingProcAtMs = nowMs)
                }
            }
        }

        return s
    }

    /**
     * Rolls all equipped ON_DEFEND procs (Shield, Chest Armor, Necklace, Ring) when struck by a hostile monster.
     */
    fun resolveDefenseProcs(
        state: GameWorldState,
        attacker: MonsterEntity,
        nowMs: Long,
        rng: Random = Random.Default
    ): GameWorldState {
        val floorMap = WorldMaps.getFloor(state.floor)
        if (floorMap.isProtectionZone(state.playerX, state.playerY)) return state
        if (state.hp <= 0) return state

        var s = state
        val px = s.playerX.toFloat()
        val py = s.playerY.toFloat()
        val shieldingScale = 1.0 + (s.skills.shielding.level - 10).coerceAtLeast(0) * 0.035

        // 1. Shield ON_DEFEND proc (only when shield is actively wielded, not overridden by 2H weapon)
        val shieldDef = if (s.hasActiveShield()) s.equipment.shieldDef() else null
        val shieldProc = shieldDef?.proc
        if (shieldProc != null &&
            shieldProc.triggerType == ProcTriggerType.ON_DEFEND &&
            (nowMs - s.lastShieldProcAtMs) >= PROC_SLOT_COOLDOWN_MS
        ) {
            val chance = s.effectiveProcChancePercent(shieldProc)
            if (rng.nextInt(100) < chance) {
                val shieldPower = (shieldDef.defense * 1.15 + s.level * 0.95) * shieldingScale
                val resolved = executeAoeProc(
                    state = s,
                    proc = shieldProc,
                    centerX = px,
                    centerY = py,
                    preferredTargetId = attacker.id,
                    basePower = shieldPower.coerceAtLeast(16.0),
                    isMagic = true,
                    nowMs = nowMs,
                    rng = rng
                )
                if (resolved.triggered) {
                    s = resolved.state.copy(lastShieldProcAtMs = nowMs)
                }
            }
        }

        // 2. Chest Armor ON_DEFEND proc
        val armorDef = s.equipment.armorDef()
        val armorProc = armorDef?.proc
        if (armorProc != null &&
            armorProc.triggerType == ProcTriggerType.ON_DEFEND &&
            (nowMs - s.lastArmorProcAtMs) >= PROC_SLOT_COOLDOWN_MS
        ) {
            val chance = s.effectiveProcChancePercent(armorProc)
            if (rng.nextInt(100) < chance) {
                val armorPower = (s.equipment.totalArmor() * 1.35 + armorDef.armor * 1.1 + s.level * 0.85) * shieldingScale
                val resolved = executeAoeProc(
                    state = s,
                    proc = armorProc,
                    centerX = px,
                    centerY = py,
                    preferredTargetId = attacker.id,
                    basePower = armorPower.coerceAtLeast(16.0),
                    isMagic = true,
                    nowMs = nowMs,
                    rng = rng
                )
                if (resolved.triggered) {
                    s = resolved.state.copy(lastArmorProcAtMs = nowMs)
                }
            }
        }

        // 3. Necklace / Amulet ON_DEFEND proc
        val amuletProc = s.equipment.amuletDef()?.proc
        if (amuletProc != null &&
            amuletProc.triggerType == ProcTriggerType.ON_DEFEND &&
            (nowMs - s.lastAmuletProcAtMs) >= PROC_SLOT_COOLDOWN_MS
        ) {
            val chance = s.effectiveProcChancePercent(amuletProc)
            if (rng.nextInt(100) < chance) {
                val wardPower = (s.equipment.totalArmor() * 1.15 + s.level * 1.15 + 14.0) * shieldingScale
                val resolved = executeAoeProc(
                    state = s,
                    proc = amuletProc,
                    centerX = px,
                    centerY = py,
                    preferredTargetId = attacker.id,
                    basePower = wardPower.coerceAtLeast(18.0),
                    isMagic = true,
                    nowMs = nowMs,
                    rng = rng
                )
                if (resolved.triggered) {
                    s = resolved.state.copy(lastAmuletProcAtMs = nowMs)
                }
            }
        }

        // 4. Ring ON_DEFEND proc (future-proofed if a defensive ring is added)
        val ringProc = s.equipment.ringDef()?.proc
        if (ringProc != null &&
            ringProc.triggerType == ProcTriggerType.ON_DEFEND &&
            (nowMs - s.lastRingProcAtMs) >= PROC_SLOT_COOLDOWN_MS
        ) {
            val chance = s.effectiveProcChancePercent(ringProc)
            if (rng.nextInt(100) < chance) {
                val ringPower = (s.equipment.totalArmor() * 1.1 + s.level * 1.1 + 14.0) * shieldingScale
                val resolved = executeAoeProc(
                    state = s,
                    proc = ringProc,
                    centerX = px,
                    centerY = py,
                    preferredTargetId = attacker.id,
                    basePower = ringPower.coerceAtLeast(16.0),
                    isMagic = true,
                    nowMs = nowMs,
                    rng = rng
                )
                if (resolved.triggered) {
                    s = resolved.state.copy(lastRingProcAtMs = nowMs)
                }
            }
        }

        return s
    }

    private data class ProcExecutionResult(
        val state: GameWorldState,
        val triggered: Boolean
    )

    private fun executeAoeProc(
        state: GameWorldState,
        proc: EquipmentProc,
        centerX: Float,
        centerY: Float,
        preferredTargetId: Int?,
        basePower: Double,
        isMagic: Boolean,
        nowMs: Long,
        rng: Random
    ): ProcExecutionResult {
        val floorMap = WorldMaps.getFloor(state.floor)
        if (floorMap.isProtectionZone(state.playerX, state.playerY)) {
            return ProcExecutionResult(state, triggered = false)
        }

        val effectiveMaxTargets = (proc.maxTargets + state.equipment.totalProcExtraTargets()).coerceAtLeast(1)
        val radiusLimit = proc.aoeRadiusTiles + 0.15f

        // Strictly hostile living monsters on the same floor outside Protection Zones
        val candidates = state.monsters
            .asSequence()
            .filter { m ->
                m.floor == state.floor &&
                    m.hp > 0 &&
                    !floorMap.isProtectionZone(m.x, m.y) &&
                    hypot((m.x - centerX).toDouble(), (m.y - centerY).toDouble()) <= radiusLimit
            }
            .sortedWith(
                compareBy<MonsterEntity> { if (it.id == preferredTargetId) 0 else 1 }
                    .thenBy { hypot((it.x - centerX).toDouble(), (it.y - centerY).toDouble()) }
            )
            .take(effectiveMaxTargets)
            .toList()

        if (candidates.isEmpty()) {
            return ProcExecutionResult(state, triggered = false)
        }

        var s = state
        val localId = s.saveId.ifBlank { "local" }
        val powerBonusMult = 1.0 + s.equipment.totalProcPowerBonusPct() / 100.0
        var totalDamage = 0
        var slainCount = 0
        val hitCoords = mutableListOf<Pair<Float, Float>>()

        for (target in candidates) {
            // Re-fetch in case an earlier effect modified list
            val liveTarget = s.monsters.firstOrNull { it.id == target.id } ?: continue
            hitCoords.add(liveTarget.x.toFloat() to liveTarget.y.toFloat())
            val variance = 0.88 + rng.nextDouble() * 0.24
            val rawDmg = (basePower * proc.damageMultiplier * powerBonusMult * variance).roundToInt().coerceAtLeast(6)
            val effectiveArmor = if (liveTarget.shatterTicks > 0 || proc.bonusEffect == ProcBonusEffect.ARMOR_SHATTER) {
                (liveTarget.scaledArmor * 0.55).roundToInt()
            } else {
                liveTarget.scaledArmor
            }
            val finalDmg = GameEngine.mitigateMonsterDamage(rawDmg, effectiveArmor, isMagic = isMagic)
            totalDamage += finalDmg

            val preHp = liveTarget.hp
            s = GameEngine.applyDamageToMonster(
                state = s,
                monsterId = liveTarget.id,
                damage = finalDmg,
                nowMs = nowMs
            )

            val survived = s.monsters.any { it.id == liveTarget.id }
            if (!survived && preHp > 0) {
                slainCount++
            } else if (survived) {
                // Ensure monster aggros local player and receives any bonus status effect
                s = s.copy(
                    monsters = s.monsters.map { m ->
                        if (m.id != liveTarget.id) m
                        else {
                            val appliesDot = proc.bonusEffect == ProcBonusEffect.IGNITE_BURN || proc.bonusEffect == ProcBonusEffect.VENOM_DOT
                            val nextBurnTicks = if (appliesDot) max(m.burnTicks, if (proc.bonusEffect == ProcBonusEffect.VENOM_DOT) 4 else 3) else m.burnTicks
                            val nextBurnDmg = if (appliesDot) {
                                max(m.burnDamagePerTick, (finalDmg * 0.30).roundToInt().coerceAtLeast(5))
                            } else {
                                m.burnDamagePerTick
                            }
                            val nextShatter = if (proc.bonusEffect == ProcBonusEffect.ARMOR_SHATTER) max(m.shatterTicks, 4) else m.shatterTicks
                            val nextSlow = when (proc.bonusEffect) {
                                ProcBonusEffect.FROST_SHATTER_SLOW -> max(m.slowTicks, 4)
                                ProcBonusEffect.STAGGER_SLOW -> max(m.slowTicks, 3)
                                else -> m.slowTicks
                            }
                            m.copy(
                                targetPlayerId = m.targetPlayerId ?: localId,
                                contributors = m.contributors + localId,
                                burnTicks = nextBurnTicks,
                                burnDamagePerTick = nextBurnDmg,
                                shatterTicks = nextShatter,
                                slowTicks = nextSlow
                            )
                        }
                    }
                )
            }
        }

        // Apply self-beneficial bonus effects (Mana Siphon, Aegis Ward heal, Vampiric Life Siphon)
        val bonusNote = when (proc.bonusEffect) {
            ProcBonusEffect.MANA_SIPHON -> {
                val restoreMp = (10 + candidates.size * 3).coerceAtMost(28)
                val newMp = (s.mp + restoreMp).coerceAtMost(s.maxMp)
                val gained = (newMp - s.mp).coerceAtLeast(0)
                if (gained > 0) {
                    s = s.copy(mp = newMp)
                        .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat() - 0.25f, "+$gained MP", FloatingTextKind.MANA, nowMs)
                    " (+$gained MP)"
                } else ""
            }
            ProcBonusEffect.AEGIS_WARD -> {
                val wardHeal = (14 + candidates.size * 5 + s.level / 2).coerceAtMost(48)
                val capHp = s.effectiveMaxHp()
                val newHp = (s.hp + wardHeal).coerceAtMost(capHp)
                val healed = (newHp - s.hp).coerceAtLeast(0)
                if (healed > 0) {
                    s = s.copy(hp = newHp)
                        .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat() - 0.25f, "+$healed HP", FloatingTextKind.HEAL, nowMs)
                    " (+$healed HP)"
                } else ""
            }
            ProcBonusEffect.LIFE_SIPHON -> {
                val drainHeal = (totalDamage * 0.35).roundToInt().coerceIn(8, 75)
                val capHp = s.effectiveMaxHp()
                val newHp = (s.hp + drainHeal).coerceAtMost(capHp)
                val healed = (newHp - s.hp).coerceAtLeast(0)
                if (healed > 0) {
                    s = s.copy(hp = newHp)
                        .addFloatingInternal(s.floor, s.playerX.toFloat(), s.playerY.toFloat() - 0.25f, "+$healed HP", FloatingTextKind.HEAL, nowMs)
                    " (Siphoned +$healed HP)"
                } else ""
            }
            ProcBonusEffect.IGNITE_BURN -> " (Ignited)"
            ProcBonusEffect.VENOM_DOT -> " (Venomized)"
            ProcBonusEffect.ARMOR_SHATTER -> " (Armor Shattered)"
            ProcBonusEffect.STAGGER_SLOW -> " (Staggered)"
            ProcBonusEffect.FROST_SHATTER_SLOW -> " (Frost Slowed)"
            ProcBonusEffect.NONE -> ""
        }

        // Emit expanding isometric AOE VFX + crisp uppercase floating proc callout banner
        val effect = ProcAoeEffect(
            id = GameEngine.allocateEffectId(),
            floor = s.floor,
            centerX = centerX,
            centerY = centerY,
            aoeRadiusTiles = proc.aoeRadiusTiles,
            elementColor = proc.elementColor,
            visualStyle = proc.visualStyle,
            chainTargets = if (proc.visualStyle == ProcVisualStyle.CHAIN_ARC) hitCoords else emptyList(),
            createdAtMs = nowMs,
            durationMs = PROC_EFFECT_DURATION_MS
        )
        val calloutText = "${proc.name.uppercase()}!"
        s = s.copy(procAoeEffects = (s.procAoeEffects + effect).takeLast(12))
            .addFloatingInternal(
                floor = s.floor,
                x = centerX,
                y = centerY - 0.42f,
                text = calloutText,
                kind = FloatingTextKind.PROC_CALLOUT,
                nowMs = nowMs,
                customColorHex = proc.elementColor
            )
            .queuePlayerFx(calloutText, FloatingTextKind.PROC_CALLOUT, PlayerActionState.CASTING)

        val foeLabel = if (candidates.size == 1) "1 foe" else "${candidates.size} foes"
        val killNote = if (slainCount > 0) " • $slainCount slain" else ""
        s = s.addLogInternal("[PROC] ${proc.name} hit $foeLabel for $totalDamage dmg$bonusNote$killNote!")
        return ProcExecutionResult(s, triggered = true)
    }
}
