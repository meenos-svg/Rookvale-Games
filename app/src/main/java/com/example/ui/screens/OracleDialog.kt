package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.game.model.GameWorldState
import com.example.game.model.ORACLE_MIN_LEVEL
import com.example.game.model.Vocation
import com.example.game.sprites.EntitySpriteAtlas
import com.example.game.world.NpcRole
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.ManaSapphire
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.StoneBorderDark
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.StoneElevated
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold

@Composable
fun OracleCeremonyDialog(
    state: GameWorldState,
    onChooseVocation: (Vocation) -> Unit,
    onDismiss: () -> Unit
) {
    val oracleSprite = remember { EntitySpriteAtlas.npcBitmap(NpcRole.ORACLE) }
    var selectedVocation by remember { mutableStateOf<Vocation?>(null) }
    var confirmingChoice by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = BasaltDark,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(2.dp, TorchGold),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .heightIn(max = 620.dp)
                .testTag("oracle_ceremony_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with Oracle Statue Sprite
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PixelSpritePreview(
                        bitmap = oracleSprite,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianVoid)
                            .border(1.5.dp, TorchGold, RoundedCornerShape(8.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AutoAwesome, null, tint = TorchGold, modifier = Modifier.size(18.dp))
                            Text(
                                text = "THE ORACLE",
                                style = MaterialTheme.typography.titleLarge,
                                color = TorchGold
                            )
                        }
                        Text(
                            text = "Rookvale Temple Sanctuary",
                            style = MaterialTheme.typography.labelMedium,
                            color = ParchmentMuted
                        )
                    }
                }

                HorizontalDivider(color = StoneBorderDark, thickness = 1.dp)

                when {
                    // Case 1: Already has a chosen vocation (or legacy Knight/Paladin/Mage)
                    state.vocation != Vocation.ADVENTURER -> {
                        Surface(
                            color = StoneSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StoneBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "\"Your destiny is already written in stone, ${state.playerName}. Walk with honor as a ${state.vocation.displayName} (${state.vocation.subtitle}).\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ParchmentLight
                                )
                                Text(
                                    text = "Level ${state.level} ${state.vocation.displayName} • Growth: +${state.vocation.hpPerLevel} HP, +${state.vocation.mpPerLevel} MP, +${state.vocation.capPerLevel.toInt()} oz/lvl",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TorchGold
                                )
                            }
                        }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = TorchGold, contentColor = ObsidianVoid),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("oracle_close_button")
                        ) {
                            Text("DEPART IN PEACE", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    // Case 2: Blank-slate Adventurer below Level 20
                    state.level < ORACLE_MIN_LEVEL -> {
                        Surface(
                            color = StoneSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StoneBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "\"Child of Rookvale, you are not yet ready to choose your destiny. Hone your blade, bow, magic, and pickaxe in the wilds—return to me when you have reached Level $ORACLE_MIN_LEVEL.\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ParchmentLight
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Ceremony Readiness", style = MaterialTheme.typography.labelMedium, color = ParchmentMuted)
                                    Text(
                                        "Level ${state.level} / $ORACLE_MIN_LEVEL",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TorchGold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (state.level.toFloat() / ORACLE_MIN_LEVEL.toFloat()).coerceIn(0f, 1f) },
                                    color = TorchGold,
                                    trackColor = ObsidianVoid,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Text(
                                    text = "All skills you train as an Adventurer carry over permanently when you ascend at Level $ORACLE_MIN_LEVEL.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ParchmentMuted
                                )
                            }
                        }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = TorchAmber, contentColor = ObsidianVoid),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("oracle_depart_button")
                        ) {
                            Text("I SHALL RETURN AT LEVEL $ORACLE_MIN_LEVEL", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    // Case 3: Adventurer Level 20+ -> Vocation Ceremony
                    confirmingChoice && selectedVocation != null -> {
                        val chosen = selectedVocation!!
                        val sprite = remember(chosen) { EntitySpriteAtlas.playerBitmap(chosen, 0) }
                        Surface(
                            color = StoneElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, TorchGold),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PixelSpritePreview(
                                    bitmap = sprite,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ObsidianVoid)
                                        .border(1.5.dp, TorchGold, RoundedCornerShape(8.dp))
                                )
                                Text(
                                    text = "CLAIM THE PATH OF THE ${chosen.displayName.uppercase()}?",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TorchGold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "\"Are you certain, ${state.playerName}? This vow is permanent. Your outfit will change to the ${chosen.displayName} vestments, all 6 earned skills will remain intact, and your new per-level stat growth begins on your next level up.\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ParchmentLight,
                                    textAlign = TextAlign.Center
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OracleStatPill("+${chosen.hpPerLevel} HP/lvl", HealthCrimson)
                                    OracleStatPill("+${chosen.mpPerLevel} MP/lvl", ManaSapphire)
                                    OracleStatPill("+${chosen.capPerLevel.toInt()} oz/lvl", TorchAmber)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { confirmingChoice = false },
                                border = BorderStroke(1.dp, StoneBorderLight),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("oracle_back_button")
                            ) {
                                Text("BACK", color = ParchmentLight)
                            }
                            Button(
                                onClick = { onChooseVocation(chosen) },
                                colors = ButtonDefaults.buttonColors(containerColor = TorchGold, contentColor = ObsidianVoid),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(48.dp)
                                    .testTag("oracle_confirm_vocation_button")
                            ) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("SEAL DESTINY", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = "\"Hail, ${state.playerName}! You have proven yourself at Level ${state.level}. Choose your permanent vocation—all skills you have earned will be preserved.\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ParchmentLight
                        )

                        Vocation.ceremonyVocations.forEach { voc ->
                            OracleVocationCard(
                                vocation = voc,
                                selected = selectedVocation == voc,
                                onClick = { selectedVocation = voc }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                border = BorderStroke(1.dp, StoneBorderLight),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("oracle_later_button")
                            ) {
                                Text("DECIDE LATER", color = ParchmentMuted)
                            }
                            Button(
                                onClick = { if (selectedVocation != null) confirmingChoice = true },
                                enabled = selectedVocation != null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TorchGold,
                                    contentColor = ObsidianVoid,
                                    disabledContainerColor = StoneBorderDark,
                                    disabledContentColor = ParchmentMuted
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                                    .testTag("oracle_proceed_ceremony_button")
                            ) {
                                Text(
                                    text = selectedVocation?.let { "CHOOSE ${it.displayName.uppercase()}" } ?: "SELECT VOCATION",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OracleVocationCard(
    vocation: Vocation,
    selected: Boolean,
    onClick: () -> Unit
) {
    val sprite = remember(vocation) { EntitySpriteAtlas.playerBitmap(vocation, 0) }
    val borderColor = if (selected) TorchGold else StoneBorderLight
    val bgColor = if (selected) StoneElevated else StoneSurface

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("oracle_vocation_${vocation.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PixelSpritePreview(
                bitmap = sprite,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ObsidianVoid)
                    .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = vocation.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) TorchGold else ParchmentLight
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = vocation.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = ParchmentMuted
                    )
                }
                Text(
                    text = vocation.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ParchmentMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OracleStatPill("+${vocation.hpPerLevel} HP", HealthCrimson)
                    OracleStatPill("+${vocation.mpPerLevel} MP", ManaSapphire)
                    OracleStatPill("+${vocation.capPerLevel.toInt()} oz", TorchAmber)
                    val specialty = when (vocation) {
                        Vocation.KNIGHT -> "Melee/Shield++"
                        Vocation.PALADIN -> "Distance++"
                        Vocation.MAGE -> "Magic++"
                        Vocation.ADVENTURER -> "Balanced"
                    }
                    OracleStatPill(specialty, ExpEmerald)
                }
            }
        }
    }
}

@Composable
private fun OracleStatPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = ParchmentLight)
    }
}
