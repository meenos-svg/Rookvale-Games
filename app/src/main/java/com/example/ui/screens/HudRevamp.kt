package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.engine.NpcInteractions
import com.example.game.engine.TownCrier
import com.example.game.model.ActivePanel
import com.example.game.model.GameWorldState
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.FireOrange
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.StoneBorderDark
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.TargetRed
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold
import kotlin.math.abs
import kotlin.math.max

/** Width of [RightActionRail], so neighbours can leave room for it. */
val RIGHT_RAIL_WIDTH = 56.dp

/**
 * Right-edge menu rail: big, labelled 48dp buttons for the panels you open most,
 * plus a Menu button for Codex, Save, Settings and Character Select.
 */
@Composable
fun RightActionRail(
    activePanel: ActivePanel,
    onOpenPanel: (ActivePanel) -> Unit,
    onSaveManual: () -> Unit,
    onReturnTitle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    Surface(
        color = BasaltDark.copy(alpha = 0.88f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.2.dp, StoneBorderLight),
        modifier = modifier.width(RIGHT_RAIL_WIDTH).testTag("right_action_rail")
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RailButton(Icons.Default.Backpack, "Bag", activePanel == ActivePanel.BACKPACK, "tab_backpack") { onOpenPanel(ActivePanel.BACKPACK) }
            RailButton(Icons.Default.Shield, "Gear", activePanel == ActivePanel.EQUIPMENT, "tab_equipment") { onOpenPanel(ActivePanel.EQUIPMENT) }
            RailButton(Icons.Default.MilitaryTech, "Skills", activePanel == ActivePanel.SKILLS, "tab_skills") { onOpenPanel(ActivePanel.SKILLS) }
            RailButton(Icons.Default.Map, "Map", activePanel == ActivePanel.WORLD_MAP, "utility_world_map_button") { onOpenPanel(ActivePanel.WORLD_MAP) }
            Box {
                RailButton(Icons.Default.Menu, "Menu", menuOpen, "rail_menu_button") { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuEntry(Icons.AutoMirrored.Filled.MenuBook, "Recipe Codex", "utility_recipe_codex_button") { menuOpen = false; onOpenPanel(ActivePanel.RECIPE_CODEX) }
                    MenuEntry(Icons.Default.CloudUpload, "Save Now", "manual_save_button") { menuOpen = false; onSaveManual() }
                    MenuEntry(Icons.Default.Settings, "Settings", "utility_settings_button") { menuOpen = false; onOpenPanel(ActivePanel.SETTINGS) }
                    MenuEntry(Icons.Default.Home, "Character Select", "return_title_button") { menuOpen = false; confirmExit = true }
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Return to character select?") },
            text = { Text("Your progress is saved first.") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; onReturnTitle() }) { Text("Leave", color = TorchGold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("Stay", color = ParchmentLight) }
            },
            containerColor = BasaltDark
        )
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, active: Boolean, testTag: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(width = 48.dp, height = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) TorchGold else StoneSurface)
            .border(1.dp, if (active) TorchGold else StoneBorderDark, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = if (active) ObsidianVoid else ParchmentLight, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = if (active) ObsidianVoid else ParchmentMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun MenuEntry(icon: ImageVector, label: String, testTag: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium, color = ParchmentLight) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = TorchGold) },
        onClick = onClick,
        modifier = Modifier.height(48.dp).testTag(testTag)
    )
}

/**
 * Left-side tracker: your active hunt contract, today's bonus creature, bestiary entries waiting
 * at Sage Ilsa, and the current map walk with a cancel button. Hides itself when there's nothing to show.
 */
@Composable
fun AdventureTrackerCard(
    state: GameWorldState,
    onCancelWalk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contract = remember(state.npcProgress) { NpcInteractions.activeContract(state) }
    val kills = state.npcProgress["hunt_kills"] ?: 0
    val readyEntries = remember(state.npcProgress) { NpcInteractions.completedUnclaimed(state).size }
    val featured = TownCrier.featuredCreature()
    val walking = state.longRangeGoal != null && state.walkPath.size > 12
    var collapsed by remember { mutableStateOf(false) }

    Surface(
        color = BasaltDark.copy(alpha = 0.84f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, StoneBorderLight),
        modifier = modifier.width(196.dp).testTag("adventure_tracker")
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth().height(28.dp).clickable { collapsed = !collapsed },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TRACKER", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold), color = TorchGold, modifier = Modifier.weight(1f))
                Text(if (collapsed) "Show" else "Hide", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = ParchmentMuted)
            }
            if (!collapsed) {
                if (walking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Walking • ${state.walkPath.size} steps left",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = ParchmentLight,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(StoneSurface).clickable(onClick = onCancelWalk).testTag("cancel_walk_button"),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Close, "Stop walking", tint = HealthCrimson, modifier = Modifier.size(18.dp)) }
                    }
                }
                if (contract != null) {
                    val done = kills >= contract.count
                    Text(
                        if (done) "${contract.title}: done! See Captain Hale" else "${contract.title}: $kills/${contract.count}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = if (done) ExpEmerald else ParchmentLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    LinearProgressIndicator(
                        progress = { (kills.toFloat() / contract.count).coerceIn(0f, 1f) },
                        color = ExpEmerald,
                        trackColor = StoneBorderDark,
                        modifier = Modifier.fillMaxWidth().height(5.dp)
                    )
                }
                Text(
                    "Today's hunt: ${featured.displayName} (+50% XP)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TorchAmber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (readyEntries > 0) {
                    Text(
                        "$readyEntries bestiary ${if (readyEntries == 1) "entry" else "entries"} ready: see Sage Ilsa",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = ExpEmerald,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Compact frame for your current (non-boss) target: name, level, HP bar and distance.
 * Bosses keep their big nameplate.
 */
@Composable
fun TargetFrame(state: GameWorldState, modifier: Modifier = Modifier) {
    val target = state.monsters.firstOrNull {
        it.id == state.targetMonsterId && it.floor == state.floor && it.hp > 0 && !it.type.isBoss
    } ?: return
    val frac = (target.hp.toFloat() / target.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
    val dist = max(abs(target.x - state.playerX), abs(target.y - state.playerY))
    val levelGap = target.level - state.level
    val levelColor = when {
        levelGap >= 5 -> TargetRed
        levelGap >= 2 -> FireOrange
        levelGap <= -5 -> ParchmentMuted
        else -> ParchmentLight
    }
    Surface(
        color = BasaltDark.copy(alpha = 0.9f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.2.dp, TargetRed.copy(alpha = 0.8f)),
        modifier = modifier.width(230.dp).testTag("target_frame")
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    target.type.displayName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ParchmentLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("Lv ${target.level}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = levelColor)
            }
            LinearProgressIndicator(
                progress = { frac },
                color = when {
                    frac > 0.6f -> ExpEmerald
                    frac > 0.3f -> TorchAmber
                    else -> HealthCrimson
                },
                trackColor = StoneBorderDark,
                modifier = Modifier.fillMaxWidth().height(7.dp)
            )
            Row {
                Text("${target.hp}/${target.maxHp} HP", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = ParchmentMuted)
                Spacer(Modifier.weight(1f))
                Text(if (dist <= 1) "In reach" else "$dist tiles", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color(0xFF9FC3FF))
            }
        }
    }
}
