package com.example.ui.screens

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.model.ActivePanel
import com.example.game.model.EquipmentSlot
import com.example.game.model.FloorWarningBanner
import com.example.game.model.GameWorldState
import com.example.game.model.ThreatBand
import com.example.game.model.Vocation
import com.example.game.multiplayer.ChatChannel
import com.example.game.multiplayer.MultiplayerUiState
import com.example.game.sprites.EntitySpriteAtlas
import com.example.game.world.WorldMaps
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.FireOrange
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.HealthCrimsonDark
import com.example.ui.theme.ManaSapphire
import com.example.ui.theme.ManaSapphireDark
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.PoisonGreen
import com.example.ui.theme.SoulViolet
import com.example.ui.theme.StoneBorderDark
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.StoneElevated
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.TargetRed
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameScreen(
    state: GameWorldState,
    multiplayerState: MultiplayerUiState = MultiplayerUiState(),
    saveBanner: String?,
    onTileTapped: (Int, Int) -> Unit,
    onHotkeyHp: () -> Unit,
    onHotkeyMp: () -> Unit,
    onHotkeyHealRune: () -> Unit = {},
    onHotkeyRune: () -> Unit,
    onHotkeyRope: () -> Unit,
    onToggleMinimap: () -> Unit,
    onOpenPanel: (ActivePanel) -> Unit,
    onClosePanel: () -> Unit,
    onUseItem: (String) -> Unit,
    onDropItem: (String) -> Unit,
    onUnequipSlot: (EquipmentSlot) -> Unit,
    onLootAll: (String) -> Unit,
    onLootCorpseItem: (String, Int) -> Unit = { _, _ -> },
    onLootCorpseGold: (String) -> Unit = {},
    onDepositItem: (String) -> Unit,
    onWithdrawItem: (String) -> Unit,
    onBuyItem: (String) -> Unit,
    onSellItem: (String) -> Unit,
    onCraftRecipe: (String) -> Unit,
    onBindRune: (String) -> Unit = {},
    onChooseVocationAtOracle: (Vocation) -> Unit,
    onMultiplayerStatusTapped: () -> Unit = {},
    onToggleChat: () -> Unit = {},
    onSelectChatChannel: (ChatChannel) -> Unit = {},
    onSendChat: (String) -> Unit = {},
    onSaveManual: () -> Unit,
    onReturnTitle: () -> Unit,
    onDismissDeath: () -> Unit
) {
    val floorInfo = remember(state.floor) { WorldMaps.getFloor(state.floor) }
    val inPz = floorInfo.isProtectionZone(state.playerX, state.playerY)
    val targetedMonster = remember(state.targetMonsterId, state.monsters, state.floor) {
        state.monsters.firstOrNull { it.id == state.targetMonsterId && it.floor == state.floor }
    }
    val weaponNeedsAmmo = state.equipment.weaponDef()?.requiredAmmoKind != null
    val remainingCap = state.remainingCapOz()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // --- TOP DARK STONE FANTASY HUD ---
        Surface(
            color = BasaltDark,
            border = BorderStroke(1.dp, StoneBorderLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = state.playerName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TorchGold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = "Lvl ${state.level} ${state.vocation.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ParchmentLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            MultiplayerStatusBadge(
                                mpState = multiplayerState,
                                onClick = onMultiplayerStatusTapped
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "${floorInfo.name} (${state.floor})",
                                style = MaterialTheme.typography.labelSmall,
                                color = ParchmentMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${state.gold} gp",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = TorchGold
                            )
                            Text(
                                text = "Cap ${formatOz(remainingCap)}/${formatOz(state.maxCapOz)} oz",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (remainingCap < 25.0) HealthCrimson else ParchmentLight,
                                modifier = Modifier.testTag("hud_cap_text")
                            )
                            if (weaponNeedsAmmo) {
                                val ammoQty = state.activeAmmoCount()
                                StatusChip("AMMO $ammoQty", if (ammoQty > 0) TorchAmber else HealthCrimson)
                            }
                            if (inPz) StatusChip("PZ", ExpEmerald)
                            if (state.poisonTicks > 0) StatusChip("PSN ${state.poisonTicks}", PoisonGreen)
                            if (state.fireTicks > 0) StatusChip("BURN ${state.fireTicks}", FireOrange)
                            if (saveBanner != null) StatusChip(saveBanner, TorchGold)
                        }
                    }

                    Surface(
                        color = StoneElevated,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, TorchGold.copy(alpha = 0.75f)),
                        modifier = Modifier
                            .height(34.dp)
                            .clickable(onClick = onSaveManual)
                            .testTag("manual_save_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Save Now",
                                tint = TorchGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Save",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = TorchGold,
                                maxLines = 1
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleMinimap,
                        modifier = Modifier.size(36.dp).testTag("toggle_minimap_button")
                    ) {
                        Icon(Icons.Default.Map, "Toggle Minimap", tint = if (state.showMinimap) TorchGold else ParchmentMuted, modifier = Modifier.size(19.dp))
                    }
                    IconButton(
                        onClick = onReturnTitle,
                        modifier = Modifier.size(36.dp).testTag("return_title_button")
                    ) {
                        Icon(Icons.Default.Home, "Character Select", tint = ParchmentMuted, modifier = Modifier.size(19.dp))
                    }
                }

                // HP, Mana & Single-Line Soul Gauge Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VitalGauge(stringResource(R.string.hud_hp), state.hp, state.maxHp, HealthCrimson, HealthCrimsonDark, Modifier.weight(1f))
                    VitalGauge(stringResource(R.string.hud_mp), state.mp, state.maxMp, ManaSapphire, ManaSapphireDark, Modifier.weight(1f))
                    Surface(
                        color = StoneSurface,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, StoneBorderLight.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = "Soul ${state.soul}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = SoulViolet,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("hud_soul_text")
                        )
                    }
                }

                if (targetedMonster != null) {
                    val band = targetedMonster.type.threatBandFor(state.level)
                    val bandAccent = when (band) {
                        ThreatBand.EASY -> Color(0xFF4CE06A)
                        ThreatBand.EVEN -> ParchmentLight
                        ThreatBand.CHALLENGING -> TorchGold
                        ThreatBand.DEADLY -> TargetRed
                    }
                    val prefix = if (band == ThreatBand.DEADLY) "☠ " else ""
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2B1414), RoundedCornerShape(4.dp))
                            .border(1.dp, bandAccent, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${prefix}TARGET: ${targetedMonster.type.displayName} [Lvl ${targetedMonster.type.threatLevel}]",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = bandAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.autoFollowTarget) {
                            Spacer(Modifier.width(4.dp))
                            StatusChip("FOLLOW", TargetRed)
                            Spacer(Modifier.width(6.dp))
                        }
                        LinearProgressIndicator(
                            progress = { (targetedMonster.hp.toFloat() / targetedMonster.maxHp.toFloat()).coerceIn(0f, 1f) },
                            color = bandAccent,
                            trackColor = ObsidianVoid,
                            modifier = Modifier.width(80.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${targetedMonster.hp}/${targetedMonster.maxHp}", style = MaterialTheme.typography.labelSmall, color = ParchmentLight)
                    }
                }
            }
        }

        // --- MAIN OBLIQUE PIXEL-ART VIEWPORT + FLOOR BANNER + LOG + COLLAPSIBLE CHAT + SLIDE-UP PANEL ---
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            GameCanvasView(
                state = state,
                remotePlayers = multiplayerState.remotePlayers,
                onTileTapped = onTileTapped,
                modifier = Modifier.fillMaxSize()
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = state.floorWarningBanner != null && state.activePanel == ActivePanel.NONE,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 10.dp, top = 8.dp, end = if (state.showMinimap) 122.dp else 10.dp)
            ) {
                state.floorWarningBanner?.let { banner ->
                    FloorDescentWarningBanner(banner = banner)
                }
            }

            if (state.activePanel == ActivePanel.NONE) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                ) {
                    if (!multiplayerState.chatExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianVoid.copy(alpha = 0.65f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            state.statusLog.takeLast(2).forEachIndexed { idx, line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (idx == 1 || state.statusLog.size == 1) ParchmentLight else ParchmentMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    CollapsibleFloorChatBar(
                        mpState = multiplayerState,
                        currentFloor = state.floor,
                        onToggleExpanded = onToggleChat,
                        onSelectChannel = onSelectChatChannel,
                        onSendMessage = onSendChat
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = state.activePanel != ActivePanel.NONE && state.activePanel != ActivePanel.ORACLE,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                SlideUpGamePanel(
                    state = state,
                    onClose = onClosePanel,
                    onUseItem = onUseItem,
                    onDropItem = onDropItem,
                    onUnequipSlot = onUnequipSlot,
                    onLootAll = onLootAll,
                    onLootCorpseItem = onLootCorpseItem,
                    onLootCorpseGold = onLootCorpseGold,
                    onDepositItem = onDepositItem,
                    onWithdrawItem = onWithdrawItem,
                    onBuyItem = onBuyItem,
                    onSellItem = onSellItem,
                    onCraftRecipe = onCraftRecipe,
                    onBindRune = onBindRune
                )
            }
        }

        // --- BOTTOM DARK STONE CONTROL DOCK (SINGLE FULL-WIDTH 4-HOTKEY ROW + 3 DRAWER TABS) ---
        Surface(
            color = BasaltDark,
            border = BorderStroke(1.dp, StoneBorderLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val resolvedHealId = state.resolvedHealRuneId()
                val healRuneCount = state.itemCount(resolvedHealId)
                val resolvedAtkId = state.resolvedAttackRuneId() ?: "magic_dart_rune"
                val atkRuneCount = state.itemCount(resolvedAtkId)
                val atkRuneShortLabel = when (resolvedAtkId) {
                    "magic_dart_rune" -> "Dart"
                    "hmm_rune" -> "HMM"
                    "fireball_rune" -> "GFB"
                    "sd_rune" -> "SD"
                    else -> stringResource(R.string.hotkey_rune)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HotkeyStoneButton(
                        itemId = "health_potion",
                        label = stringResource(R.string.hotkey_hp_potion),
                        count = state.itemCount("health_potion"),
                        accent = HealthCrimson,
                        onClick = onHotkeyHp,
                        testTag = "hotkey_health_potion",
                        modifier = Modifier.weight(1f)
                    )
                    HotkeyStoneButton(
                        itemId = "mana_potion",
                        label = stringResource(R.string.hotkey_mp_potion),
                        count = state.itemCount("mana_potion"),
                        accent = ManaSapphire,
                        onClick = onHotkeyMp,
                        testTag = "hotkey_mana_potion",
                        modifier = Modifier.weight(1f)
                    )
                    HotkeyStoneButton(
                        itemId = resolvedHealId,
                        label = stringResource(R.string.hotkey_heal_rune),
                        count = healRuneCount,
                        accent = ExpEmerald,
                        onClick = onHotkeyHealRune,
                        testTag = "hotkey_heal_rune",
                        modifier = Modifier.weight(1f)
                    )
                    HotkeyStoneButton(
                        itemId = resolvedAtkId,
                        label = atkRuneShortLabel,
                        count = atkRuneCount,
                        accent = SoulViolet,
                        onClick = onHotkeyRune,
                        testTag = "hotkey_attack_rune",
                        modifier = Modifier.weight(1f)
                    )
                    HotkeyStoneButton(
                        itemId = "rope",
                        label = stringResource(R.string.hotkey_rope),
                        count = state.itemCount("rope"),
                        accent = TorchGold,
                        onClick = onHotkeyRope,
                        testTag = "hotkey_rope",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DrawerTabButton(Icons.Default.Shield, stringResource(R.string.panel_equipment), state.activePanel == ActivePanel.EQUIPMENT, { onOpenPanel(ActivePanel.EQUIPMENT) }, "tab_equipment", Modifier.weight(1f))
                    DrawerTabButton(Icons.Default.Backpack, stringResource(R.string.panel_backpack), state.activePanel == ActivePanel.BACKPACK, { onOpenPanel(ActivePanel.BACKPACK) }, "tab_backpack", Modifier.weight(1f))
                    DrawerTabButton(Icons.Default.MilitaryTech, stringResource(R.string.panel_skills), state.activePanel == ActivePanel.SKILLS, { onOpenPanel(ActivePanel.SKILLS) }, "tab_skills", Modifier.weight(1f))
                }
            }
        }
    }

    if (state.activePanel == ActivePanel.ORACLE) {
        OracleCeremonyDialog(
            state = state,
            onChooseVocation = onChooseVocationAtOracle,
            onDismiss = onClosePanel
        )
    }

    if (state.deathReport != null) {
        DeathPenaltyDialog(report = state.deathReport, onDismiss = onDismissDeath)
    }
}

@Composable
private fun VitalGauge(label: String, current: Int, max: Int, fillColor: Color, trackColor: Color, modifier: Modifier = Modifier) {
    val frac = (current.toFloat() / max.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ParchmentMuted)
            Text("$current/$max", style = MaterialTheme.typography.labelSmall, color = ParchmentLight)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(3.dp)).background(trackColor).border(1.dp, StoneBorderDark, RoundedCornerShape(3.dp))
        ) {
            Box(modifier = Modifier.fillMaxWidth(frac).height(10.dp).background(fillColor))
        }
    }
}

@Composable
private fun FloorDescentWarningBanner(banner: FloorWarningBanner) {
    val accent = when {
        banner.severity <= 1 -> ExpEmerald
        banner.severity == 2 -> TorchGold
        banner.severity == 3 -> FireOrange
        else -> TargetRed
    }
    Surface(
        color = BasaltDark.copy(alpha = 0.94f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.5.dp, accent),
        modifier = Modifier.testTag("floor_warning_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Floor Danger Warning",
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Floor ${banner.floorId}: ${banner.floorName}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ParchmentLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Danger: ${banner.dangerLevel} • ${banner.recommendedLevel}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier.background(color.copy(alpha = 0.22f), RoundedCornerShape(3.dp)).border(1.dp, color, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun HotkeyStoneButton(
    itemId: String,
    label: String,
    count: Int,
    accent: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val icon = remember(itemId) { EntitySpriteAtlas.itemIcon(itemId) }
    val enabledAlpha = if (count > 0) 1f else 0.45f
    Surface(
        color = StoneSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.2.dp, if (count > 0) accent.copy(alpha = 0.85f) else StoneBorderLight),
        modifier = modifier.height(54.dp).clickable(onClick = onClick).testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PixelSpritePreview(
                    icon,
                    Modifier
                        .size(22.dp)
                        .background(ObsidianVoid, RoundedCornerShape(4.dp))
                )
                Text(
                    text = "x$count",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (count > 0) TorchGold else ParchmentMuted,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ParchmentLight.copy(alpha = enabledAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DrawerTabButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit, testTag: String, modifier: Modifier = Modifier) {
    Surface(
        color = if (active) TorchGold else StoneElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (active) TorchGold else StoneBorderLight),
        modifier = modifier.height(48.dp).clickable(onClick = onClick).testTag(testTag)
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = if (active) ObsidianVoid else TorchGold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = if (active) ObsidianVoid else ParchmentLight)
        }
    }
}
