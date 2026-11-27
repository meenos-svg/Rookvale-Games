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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.clipPath
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

/**
 * Split health/mana orb: red health fills the left half, blue mana the right, and a yellow XP bar
 * runs up the divider with your level on top. Tap the left half to drink a health potion (or eat
 * hotbar food when you're out), the right half for a mana potion.
 */
@Composable
fun VitalsOrb(
    state: GameWorldState,
    onUseHealth: () -> Unit,
    onUseMana: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hpFrac = if (state.isInvulnerable) 1f else (state.hp.toFloat() / state.effectiveMaxHp().coerceAtLeast(1)).coerceIn(0f, 1f)
    val mpFrac = (state.mp.toFloat() / state.maxMp.coerceAtLeast(1)).coerceIn(0f, 1f)
    val xpFrac = state.levelProgressFraction().coerceIn(0f, 1f)
    val healthTop = if (state.isInvulnerable) Color(0xFFFFE08A) else Color(0xFFF05A46)
    val healthBottom = if (state.isInvulnerable) Color(0xFFB8861E) else Color(0xFF8C0A0E)
    Column(modifier = modifier.width(116.dp).testTag("vitals_orb"), horizontalAlignment = Alignment.CenterHorizontally) {
      Box(modifier = Modifier.size(116.dp)) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .size(116.dp)
                .pointerInput(Unit) {
                    detectTapGestures { tap -> if (tap.x < size.width / 2f) onUseHealth() else onUseMana() }
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f + 4.dp.toPx()
            val r = size.minDimension * 0.40f
            val ring = 5.dp.toPx()
            // Stone ring with gold trim
            drawCircle(Color(0xFF3A3430), radius = r + ring, center = androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(TorchGold, radius = r + ring, center = androidx.compose.ui.geometry.Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
            drawCircle(Color(0xFF100E14), radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy))
            val globe = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r))
            }
            clipPath(globe) {
                val hpTop = cy + r - 2 * r * hpFrac
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(healthTop, healthBottom), startY = cy - r, endY = cy + r),
                    topLeft = androidx.compose.ui.geometry.Offset(cx - r, hpTop),
                    size = androidx.compose.ui.geometry.Size(r, cy + r - hpTop)
                )
                val mpTop = cy + r - 2 * r * mpFrac
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF5AAAFF), Color(0xFF12288C)), startY = cy - r, endY = cy + r),
                    topLeft = androidx.compose.ui.geometry.Offset(cx, mpTop),
                    size = androidx.compose.ui.geometry.Size(r, cy + r - mpTop)
                )
                // Liquid surfaces
                drawLine(Color.White.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(cx - r, hpTop), androidx.compose.ui.geometry.Offset(cx, hpTop), strokeWidth = 1.5.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(cx, mpTop), androidx.compose.ui.geometry.Offset(cx + r, mpTop), strokeWidth = 1.5.dp.toPx())
                // Glass shine
                drawOval(
                    color = Color.White.copy(alpha = 0.16f),
                    topLeft = androidx.compose.ui.geometry.Offset(cx - r * 0.75f, cy - r * 0.85f),
                    size = androidx.compose.ui.geometry.Size(r * 0.95f, r * 0.55f)
                )
            }
            // XP divider: dark channel filling up in yellow
            val bw = 6.dp.toPx()
            drawRect(Color(0xFF281F12), topLeft = androidx.compose.ui.geometry.Offset(cx - bw / 2, cy - r), size = androidx.compose.ui.geometry.Size(bw, 2 * r))
            val xpH = 2 * r * xpFrac
            drawRect(Color(0xFFFACD3C), topLeft = androidx.compose.ui.geometry.Offset(cx - bw / 2 + 1.dp.toPx(), cy + r - xpH), size = androidx.compose.ui.geometry.Size(bw - 2.dp.toPx(), xpH))
            // Level badge
            val badgeR = 11.dp.toPx()
            val badgeC = androidx.compose.ui.geometry.Offset(cx, cy - r - 1.dp.toPx())
            drawCircle(Color(0xFF281F12), radius = badgeR, center = badgeC)
            drawCircle(TorchGold, radius = badgeR, center = badgeC, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        }
        // Level number over the badge
        Text(
            "${state.level}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp),
            color = Color(0xFFFAD75A),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp)
        )
      }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (state.isInvulnerable) "∞" else "${state.hp}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold),
                color = Color(0xFFFF8A7A)
            )
            Text("${(xpFrac * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = Color(0xFFFAD75A))
            Text("${state.mp}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold), color = Color(0xFF8FC2FF))
        }
    }
}

/**
 * Places a HUD piece at its default spot plus the player's saved offset. In edit mode the piece
 * gets a gold outline and can be dragged; taps on it are blocked so nothing fires while moving.
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.HudMovable(
    element: com.example.game.model.HudElement,
    alignment: Alignment,
    hud: com.example.game.model.HudLayout,
    editMode: Boolean,
    onMoved: (com.example.game.model.HudLayout) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!editMode && !hud.isVisible(element)) return
    val saved = hud.offsetOf(element)
    var dragX by remember(saved) { mutableStateOf(saved.first) }
    var dragY by remember(saved) { mutableStateOf(saved.second) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(
        modifier = Modifier
            .align(alignment)
            .then(modifier)
            .offset { androidx.compose.ui.unit.IntOffset(dragX.dp.roundToPx(), dragY.dp.roundToPx()) }
    ) {
        Box(modifier = Modifier.then(if (editMode && !hud.isVisible(element)) Modifier.alpha(0.35f) else Modifier)) { content() }
        if (editMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(2.dp, TorchGold, RoundedCornerShape(8.dp))
                    .background(TorchGold.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                    .pointerInput(element) {
                        androidx.compose.foundation.gestures.detectDragGestures(
                            onDragEnd = { onMoved(hud.withOffset(element, dragX, dragY)) }
                        ) { change, drag ->
                            change.consume()
                            with(density) {
                                dragX += drag.x.toDp().value
                                dragY += drag.y.toDp().value
                            }
                        }
                    }
                    .testTag("hud_move_${element.name.lowercase()}")
            ) {
                Text(
                    element.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp),
                    color = ObsidianVoid,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(TorchGold, RoundedCornerShape(bottomEnd = 6.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/** Banner shown while moving HUD pieces. */
@Composable
fun HudEditBanner(onDone: () -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = BasaltDark.copy(alpha = 0.95f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.5.dp, TorchGold),
        modifier = modifier.testTag("hud_edit_banner")
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Drag the gold boxes to move them", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = ParchmentLight)
            TextButton(onClick = onReset, modifier = Modifier.height(44.dp)) { Text("Reset", color = HealthCrimson) }
            androidx.compose.material3.Button(
                onClick = onDone,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = TorchGold, contentColor = ObsidianVoid),
                modifier = Modifier.height(44.dp).testTag("hud_edit_done")
            ) { Text("Done") }
        }
    }
}

/**
 * HUD section of the in-game settings drawer: vitals style, hotbar size, show/hide pieces,
 * move mode and resets.
 */
@Composable
fun HudSettingsSection(
    hud: com.example.game.model.HudLayout,
    onUpdate: (com.example.game.model.HudLayout) -> Unit,
    onStartMove: () -> Unit,
    onResetHotbar: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().testTag("hud_settings_section")) {
        Text("HUD LAYOUT", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = TorchGold)

        Text("Health & mana display", style = MaterialTheme.typography.labelMedium, color = ParchmentMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.example.game.model.VitalsStyle.entries.forEach { style ->
                ChoiceChip(style.label, hud.vitalsStyle == style, Modifier.weight(1f).testTag("hud_vitals_${style.name.lowercase()}")) {
                    onUpdate(hud.copy(vitalsStyle = style))
                }
            }
        }

        Text("Hotbar slots", style = MaterialTheme.typography.labelMedium, color = ParchmentMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.example.game.model.HudLayout.HOTBAR_SIZES.forEach { n ->
                ChoiceChip("$n", hud.hotbarSize == n, Modifier.weight(1f).testTag("hud_hotbar_$n")) { onUpdate(hud.copy(hotbarSize = n)) }
            }
        }
        Text("Long-press any hotbar slot to put an item or action on it.", style = MaterialTheme.typography.labelSmall, color = ParchmentMuted)

        Text("Show on screen", style = MaterialTheme.typography.labelMedium, color = ParchmentMuted)
        com.example.game.model.HudElement.entries.filter { it.hideable }.forEach { e ->
            ChoiceChip(
                (if (hud.isVisible(e)) "✓ " else "✗ ") + e.label,
                hud.isVisible(e),
                Modifier.fillMaxWidth().testTag("hud_show_${e.name.lowercase()}")
            ) { onUpdate(hud.toggled(e)) }
        }

        androidx.compose.material3.Button(
            onClick = onStartMove,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = TorchGold, contentColor = ObsidianVoid),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("hud_move_button")
        ) { Text("Move HUD pieces") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.OutlinedButton(
                onClick = { onUpdate(hud.copy(offsets = emptyMap(), hidden = emptySet())) },
                modifier = Modifier.weight(1f).height(48.dp).testTag("hud_reset_layout")
            ) { Text("Reset layout", color = ParchmentLight) }
            androidx.compose.material3.OutlinedButton(
                onClick = onResetHotbar,
                modifier = Modifier.weight(1f).height(48.dp).testTag("hud_reset_hotbar")
            ) { Text("Reset hotbar", color = ParchmentLight) }
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) TorchGold else StoneSurface)
            .border(1.dp, if (selected) TorchGold else StoneBorderLight, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = if (selected) ObsidianVoid else ParchmentLight)
    }
}

/** Stand-in shown for the target frame while moving HUD pieces. */
@Composable
fun TargetFramePlaceholder() {
    Surface(
        color = BasaltDark.copy(alpha = 0.9f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.2.dp, TargetRed.copy(alpha = 0.8f)),
        modifier = Modifier.width(230.dp).height(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("Target frame", style = MaterialTheme.typography.labelMedium, color = ParchmentMuted)
        }
    }
}
