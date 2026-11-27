package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.game.model.BiomeEquipmentClass
import com.example.game.model.EquipmentProc
import com.example.game.model.EquipmentSlot
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemCategory
import com.example.game.model.ItemDefinition
import com.example.game.model.ProcBonusEffect
import com.example.game.model.ProcTriggerType
import com.example.game.sprites.EntitySpriteAtlas
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.FireOrange
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.ManaSapphire
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.SoulViolet
import com.example.ui.theme.StoneBorderDark
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.StoneElevated
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold
import java.util.Locale

internal fun tierBadgeColor(tier: Int): Color = when (tier) {
    1 -> Color(0xFFD4A36A)
    2 -> Color(0xFF9EB5C4)
    3 -> Color(0xFF66B2FF)
    4 -> Color(0xFFBA8CFF)
    5 -> Color(0xFFFF7A38)
    6 -> Color(0xFFFFD66B)
    else -> TorchGold
}

internal fun tierBadgeLabel(tier: Int): String = when (tier) {
    1 -> "TIER 1 • STARTER"
    2 -> "TIER 2 • BOG IRON"
    3 -> "TIER 3 • STEEL"
    4 -> "TIER 4 • VESPERITE"
    5 -> "TIER 5 • CINDERITE"
    6 -> "TIER 6 • EMBERFORGED"
    else -> "GEAR"
}

internal fun procBonusEffectLabel(effect: ProcBonusEffect): String = when (effect) {
    ProcBonusEffect.NONE -> ""
    ProcBonusEffect.IGNITE_BURN -> "Ignite Burn (DoT)"
    ProcBonusEffect.ARMOR_SHATTER -> "Armor Shatter (+20% DMG taken)"
    ProcBonusEffect.STAGGER_SLOW -> "Stagger Slow"
    ProcBonusEffect.MANA_SIPHON -> "Mana Siphon (+MP)"
    ProcBonusEffect.AEGIS_WARD -> "Aegis Ward (+HP)"
    ProcBonusEffect.LIFE_SIPHON -> "Life Siphon (+HP)"
    ProcBonusEffect.VENOM_DOT -> "Corrosive Venom (DoT)"
    ProcBonusEffect.FROST_SHATTER_SLOW -> "Frost Shatter Slow"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemInspectionDialog(
    state: GameWorldState,
    itemId: String,
    equippedSlot: EquipmentSlot? = null,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val def = remember(itemId) { ItemCatalog.get(itemId) }
    val icon = remember(itemId) { EntitySpriteAtlas.itemIcon(itemId) }
    val unmetReqs = remember(state.level, state.skills, itemId) { state.unmetEquipRequirements(def) }
    val meetsAll = unmetReqs.isEmpty()
    val tierColor = if (def.equipmentTier > 0) tierBadgeColor(def.equipmentTier) else TorchGold

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = BasaltDark,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, tierColor.copy(alpha = 0.85f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .testTag("item_inspection_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StoneElevated)
                        .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PixelSpritePreview(
                        bitmap = icon,
                        modifier = Modifier
                            .size(44.dp)
                            .background(ObsidianVoid, RoundedCornerShape(6.dp))
                            .border(1.5.dp, tierColor.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = def.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = ParchmentLight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("item_inspect_name")
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (def.biomeClass != BiomeEquipmentClass.NONE) {
                                val bCol = Color(def.biomeClass.accentColorHex)
                                Text(
                                    text = def.biomeClass.displayName.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp
                                    ),
                                    color = bCol,
                                    modifier = Modifier
                                        .background(bCol.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                                        .border(1.dp, bCol.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                        .testTag("item_inspect_biome_badge")
                                )
                            } else if (def.equipmentTier > 0) {
                                Text(
                                    text = tierBadgeLabel(def.equipmentTier),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = tierColor,
                                    modifier = Modifier
                                        .background(tierColor.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                                        .border(1.dp, tierColor.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                        .testTag("item_inspect_tier_badge")
                                )
                            }
                            val slotText = def.equipSlot?.label ?: def.category.name.lowercase()
                                .replace('_', ' ')
                                .replaceFirstChar { it.uppercase() }
                            Text(
                                text = slotText,
                                style = MaterialTheme.typography.labelSmall,
                                color = ParchmentMuted
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("item_inspect_header_close")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close inspection", tint = ParchmentLight)
                    }
                }

                HorizontalDivider(color = StoneBorderDark, thickness = 1.dp)

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Core Stats Strip
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (def.attack > 0) {
                            StatPill("ATK ${def.attack}", TorchGold)
                        }
                        if (def.defense > 0) {
                            StatPill("DEF ${def.defense}", ManaSapphire)
                        }
                        if (def.armor > 0) {
                            StatPill("ARM ${def.armor}", ExpEmerald)
                        }
                        if (def.bonusMaxHp > 0) {
                            StatPill("+${def.bonusMaxHp} Max HP", Color(0xFFFF7A8A))
                        }
                        if (def.equipSlot == EquipmentSlot.WEAPON) {
                            StatPill("Speed ${def.speedLabel()}", ParchmentLight)
                            if (def.range > 1) StatPill("Range ${def.range}", ExpEmerald)
                            if (def.isTwoHanded) StatPill("Two-Handed", TorchAmber)
                            if (def.category == ItemCategory.WEAPON_WAND) StatPill("Magic Conduit", SoulViolet)
                        }
                        if (def.poisonResistPercent > 0) {
                            StatPill("Poison Resist +${def.poisonResistPercent}%", Color(0xFF5CE88A))
                        }
                        if (def.pickaxeTier > 0) {
                            StatPill("Tier ${def.pickaxeTier} Pickaxe", TorchAmber)
                            StatPill("Req Mining ${def.requiredMiningLevel}", ExpEmerald)
                        }
                        if (def.category == ItemCategory.FOOD && def.restoreAmount > 0) {
                            StatPill("+${def.restoreAmount} HP", ExpEmerald)
                            val mpHeal = def.restoreAmount / 2
                            if (mpHeal > 0) StatPill("+$mpHeal MP", ManaSapphire)
                            if (def.requiredFishingLevel > 0) {
                                StatPill("Fishing Lv ${def.requiredFishingLevel}", TorchGold)
                            }
                        }
                        if (def.category == ItemCategory.FISHING_BAIT) {
                            val meetsFishing = state.isAdmin || state.skills.fishing.level >= def.requiredFishingLevel
                            StatPill(
                                text = "Req Fishing Lv ${def.requiredFishingLevel}",
                                color = if (meetsFishing) ExpEmerald else HealthCrimson
                            )
                            def.baitTargetFishId?.let { fishId ->
                                val fish = ItemCatalog.get(fishId)
                                StatPill("Catches ${fish.name} (+${fish.restoreAmount} HP)", TorchGold)
                            }
                            StatPill("Used Only On Catch", ManaSapphire)
                        }
                        val wtLabel = if (def.isTool) {
                            "${formatOz(def.carriedWeightOz())} oz (Tool Bag)"
                        } else {
                            "${formatOz(def.weightOz)} oz"
                        }
                        StatPill(wtLabel, ParchmentMuted)
                    }

                    // Requirements Section
                    val showReqs = def.equipSlot != null ||
                        def.requiredLevel > 1 ||
                        def.requiredSkill != null ||
                        def.requiredMagicLevel > 0 ||
                        def.requiredMiningLevel > 0
                    if (showReqs) {
                        RequirementsSection(
                            state = state,
                            def = def,
                            meetsAll = meetsAll && (def.requiredMiningLevel <= 0 || state.skills.mining.level >= def.requiredMiningLevel),
                            isGrandfatheredEquipped = equippedSlot != null && !meetsAll
                        )
                    }

                    // Jewelry Synergy Enhancements Section
                    if (def.hasJewelryEnhancements()) {
                        JewelryEnhancementCard(def = def)
                    }

                    // Specialized Combat Proc Section
                    val proc = def.proc
                    if (proc != null) {
                        ProcAbilityCard(state = state, proc = proc)
                    }

                    // Item Lore / Description
                    if (def.description.isNotBlank()) {
                        Surface(
                            color = StoneSurface.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, StoneBorderDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = def.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ParchmentMuted,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = StoneBorderDark, thickness = 1.dp)

                // Footer Action Bar (48dp touch targets)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StoneElevated)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (primaryActionLabel != null && onPrimaryAction != null) {
                        val isBlockedEquip = equippedSlot == null && def.equipSlot != null && !meetsAll
                        Button(
                            onClick = {
                                onPrimaryAction()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    equippedSlot != null -> TorchAmber
                                    isBlockedEquip -> HealthCrimson.copy(alpha = 0.85f)
                                    else -> TorchGold
                                },
                                contentColor = if (isBlockedEquip) ParchmentLight else ObsidianVoid
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("item_inspect_primary_action")
                        ) {
                            Text(
                                text = if (isBlockedEquip) "Requirements Unmet" else primaryActionLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, StoneBorderLight),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .then(if (primaryActionLabel == null) Modifier.fillMaxWidth() else Modifier.width(104.dp))
                            .height(48.dp)
                            .testTag("item_inspect_close_button")
                    ) {
                        Text(
                            text = "Close",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = ParchmentLight
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequirementsSection(
    state: GameWorldState,
    def: ItemDefinition,
    meetsAll: Boolean,
    isGrandfatheredEquipped: Boolean
) {
    val borderColor = if (meetsAll) ExpEmerald.copy(alpha = 0.55f) else HealthCrimson.copy(alpha = 0.8f)
    Surface(
        color = StoneSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("item_inspect_requirements_card")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (meetsAll) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    contentDescription = if (meetsAll) "Requirements met" else "Requirements unmet",
                    tint = if (meetsAll) ExpEmerald else HealthCrimson,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (meetsAll) "EQUIP REQUIREMENTS MET" else "EQUIP REQUIREMENTS",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (meetsAll) ExpEmerald else HealthCrimson
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val lvlMet = state.level >= def.requiredLevel
                RequirementBadge(
                    label = if (lvlMet) "Level ${def.requiredLevel}" else "Level ${def.requiredLevel} (You: ${state.level})",
                    met = lvlMet
                )

                val reqSkill = def.requiredSkill
                if (reqSkill != null) {
                    val (skillType, reqVal) = reqSkill
                    val curVal = state.skills.levelFor(skillType)
                    val skillMet = curVal >= reqVal
                    RequirementBadge(
                        label = if (skillMet) {
                            "${skillType.displayName} $reqVal"
                        } else {
                            "${skillType.displayName} $reqVal (You: $curVal)"
                        },
                        met = skillMet
                    )
                }

                if (def.requiredMagicLevel > 0) {
                    val mlvMet = state.magicLevel >= def.requiredMagicLevel
                    RequirementBadge(
                        label = if (mlvMet) "MLv ${def.requiredMagicLevel}" else "MLv ${def.requiredMagicLevel} (You: ${state.magicLevel})",
                        met = mlvMet
                    )
                }

                if (def.requiredMiningLevel > 0) {
                    val mineLvl = state.skills.mining.level
                    val mineMet = mineLvl >= def.requiredMiningLevel
                    RequirementBadge(
                        label = if (mineMet) "Mining ${def.requiredMiningLevel}" else "Mining ${def.requiredMiningLevel} (You: $mineLvl)",
                        met = mineMet
                    )
                }
            }

            if (def.preferredVocation != null) {
                val matchesVoc = state.vocation == def.preferredVocation
                Text(
                    text = if (matchesVoc) {
                        "Vocation Mastery: ${def.preferredVocation.displayName} (Full Power)"
                    } else {
                        "Vocation Mastery: ${def.preferredVocation.displayName} (-25% DMG off-vocation)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (matchesVoc) ExpEmerald else TorchAmber
                )
            }

            if (isGrandfatheredEquipped) {
                Text(
                    text = "Currently worn from an earlier save. Stays equipped until removed; requirements apply on re-equip.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TorchAmber
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JewelryEnhancementCard(def: ItemDefinition) {
    Surface(
        color = Color(0xFF1D1B2A),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, TorchGold.copy(alpha = 0.72f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("item_inspect_jewelry_bonuses_card")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Ability Enhancement",
                    tint = TorchGold,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "ABILITY ENHANCEMENTS (STACKS WITH GEAR)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = TorchGold
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (def.procChanceBonusPct > 0) {
                    StatPill("+${def.procChanceBonusPct}% Proc Chance", TorchGold)
                }
                if (def.procPowerBonusPct > 0) {
                    StatPill("+${def.procPowerBonusPct}% Proc Damage", FireOrange)
                }
                if (def.procExtraTargets > 0) {
                    StatPill("+${def.procExtraTargets} Max AOE Target${if (def.procExtraTargets > 1) "s" else ""}", SoulViolet)
                }
                if (def.bonusMaxHp > 0) {
                    StatPill("+${def.bonusMaxHp} Max HP", Color(0xFFFF7A8A))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProcAbilityCard(state: GameWorldState, proc: EquipmentProc) {
    val accent = Color(proc.elementColor)
    val baseChance = proc.triggerChancePercent
    val effectiveChance = state.effectiveProcChancePercent(proc)
    val extraTargets = state.equipment.totalProcExtraTargets()
    val powerBonus = state.equipment.totalProcPowerBonusPct()
    val effectiveMult = proc.damageMultiplier * (1.0 + powerBonus / 100.0)
    val isDefend = proc.triggerType == ProcTriggerType.ON_DEFEND

    Surface(
        color = accent.copy(alpha = 0.11f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.2.dp, accent.copy(alpha = 0.85f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("item_inspect_proc_card")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isDefend) Icons.Default.Shield else Icons.Default.Bolt,
                        contentDescription = "Specialized Combat Proc",
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = proc.name.uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isDefend) "ON DEFEND" else "ON ATTACK",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    color = ObsidianVoid,
                    modifier = Modifier
                        .background(accent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val chanceText = if (effectiveChance > baseChance) {
                    "$baseChance% → $effectiveChance% Chance"
                } else {
                    "$baseChance% Chance"
                }
                StatPill(chanceText, accent)
                val radiusText = if (proc.aoeRadiusTiles > 0f) {
                    String.format(Locale.US, "AOE %.1f tiles", proc.aoeRadiusTiles)
                } else {
                    "Single Target"
                }
                StatPill(radiusText, ParchmentLight)
                val targetsText = if (extraTargets > 0 && proc.aoeRadiusTiles > 0f) {
                    "Up to ${proc.maxTargets + extraTargets} foes (+$extraTargets)"
                } else {
                    "Up to ${proc.maxTargets} ${if (proc.maxTargets == 1) "foe" else "foes"}"
                }
                StatPill(targetsText, ParchmentLight)
                val dmgText = if (powerBonus > 0) {
                    String.format(Locale.US, "%.2fx DMG (+%d%%)", effectiveMult, powerBonus)
                } else {
                    String.format(Locale.US, "%.2fx DMG", proc.damageMultiplier)
                }
                StatPill(dmgText, TorchGold)
                StatPill("2.0s Slot CD", ParchmentMuted)
            }

            val bonusLabel = procBonusEffectLabel(proc.bonusEffect)
            if (bonusLabel.isNotEmpty()) {
                Text(
                    text = "Bonus Effect: $bonusLabel",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = ExpEmerald
                )
            }

            if (proc.summary.isNotBlank()) {
                Text(
                    text = proc.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = ParchmentLight.copy(alpha = 0.92f)
                )
            }
        }
    }
}

@Composable
private fun RequirementBadge(label: String, met: Boolean) {
    val color = if (met) ExpEmerald else HealthCrimson
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(5.dp))
            .border(1.dp, color.copy(alpha = 0.72f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun StatPill(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .background(ObsidianVoid.copy(alpha = 0.8f), RoundedCornerShape(5.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}
