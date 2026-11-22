package com.example.ui.screens

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
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
import com.example.game.model.Direction
import com.example.game.model.EquipmentSlot
import com.example.game.model.GameWorldState
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

@Composable
fun GameScreen(
    state: GameWorldState,
    multiplayerState: MultiplayerUiState = MultiplayerUiState(),
    saveBanner: String?,
    onTileTapped: (Int, Int) -> Unit,
    onDirectionStep: (Direction) -> Unit,
    onHotkeyHp: () -> Unit,
    onHotkeyMp: () -> Unit,
    onHotkeyRune: () -> Unit,
    onHotkeyRope: () -> Unit,
    onToggleMinimap: () -> Unit,
    onOpenPanel: (ActivePanel) -> Unit,
    onClosePanel: () -> Unit,
    onUseItem: (String) -> Unit,
    onDropItem: (String) -> Unit,
    onUnequipSlot: (EquipmentSlot) -> Unit,
    onLootAll: (String) -> Unit,
    onDepositItem: (String) -> Unit,
    onWithdrawItem: (String) -> Unit,
    onBuyItem: (String) -> Unit,
    onSellItem: (String) -> Unit,
    onCraftRecipe: (String) -> Unit,
    onChooseVocationAtOracle: (Vocation) -> Unit,
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
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(state.playerName, style = MaterialTheme.typography.titleMedium, color = TorchGold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Lvl ${state.level} ${state.vocation.displayName}", style = MaterialTheme.typography.labelMedium, color = ParchmentLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            MultiplayerStatusBadge(mpState = multiplayerState)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${floorInfo.name} (${state.floor})", style = MaterialTheme.typography.labelSmall, color = ParchmentMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${state.gold} gp", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TorchGold)
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

                    IconButton(
                        onClick = onToggleMinimap,
                        modifier = Modifier.size(38.dp).testTag("toggle_minimap_button")
                    ) {
                        Icon(Icons.Default.Map, "Toggle Minimap", tint = if (state.showMinimap) TorchGold else ParchmentMuted, modifier = Modifier.size(19.dp))
                    }
                    IconButton(
                        onClick = onSaveManual,
                        modifier = Modifier.size(38.dp).testTag("manual_save_button")
                    ) {
                        Icon(Icons.Default.Save, stringResource(R.string.action_save), tint = TorchGold, modifier = Modifier.size(19.dp))
                    }
                    IconButton(
                        onClick = onReturnTitle,
                        modifier = Modifier.size(38.dp).testTag("return_title_button")
                    ) {
                        Icon(Icons.Default.Home, "Character Select", tint = ParchmentMuted, modifier = Modifier.size(19.dp))
                    }
                }

                // HP, Mana, Soul & Cap Gauges
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
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), horizontalAlignment = Alignment.End) {
                            Text("Soul ${state.soul}", style = MaterialTheme.typography.labelSmall, color = SoulViolet)
                            Text("Cap ${formatOz(state.remainingCapOz())}oz", style = MaterialTheme.typography.labelSmall, color = if (state.remainingCapOz() < 25.0) HealthCrimson else ParchmentLight)
                        }
                    }
                }

                if (targetedMonster != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2B1414), RoundedCornerShape(4.dp))
                            .border(1.dp, TargetRed, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TARGET: ${targetedMonster.type.displayName}", style = MaterialTheme.typography.labelMedium, color = ParchmentLight, modifier = Modifier.weight(1f))
                        LinearProgressIndicator(
                            progress = { (targetedMonster.hp.toFloat() / targetedMonster.maxHp.toFloat()).coerceIn(0f, 1f) },
                            color = TargetRed,
                            trackColor = ObsidianVoid,
                            modifier = Modifier.width(84.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${targetedMonster.hp}/${targetedMonster.maxHp}", style = MaterialTheme.typography.labelSmall, color = ParchmentLight)
                    }
                }
            }
        }

        // --- MAIN OBLIQUE PIXEL-ART VIEWPORT + LOG + COLLAPSIBLE CHAT + SLIDE-UP PANEL ---
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            GameCanvasView(
                state = state,
                remotePlayers = multiplayerState.remotePlayers,
                onTileTapped = onTileTapped,
                modifier = Modifier.fillMaxSize()
            )

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
                    onDepositItem = onDepositItem,
                    onWithdrawItem = onWithdrawItem,
                    onBuyItem = onBuyItem,
                    onSellItem = onSellItem,
                    onCraftRecipe = onCraftRecipe
                )
            }
        }

        // --- BOTTOM DARK STONE CONTROL DOCK (D-PAD + 4 HOTKEYS + 3 DRAWER TABS; NO ANYWHERE CRAFTING BUTTON) ---
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactStepPad(onDirectionStep = onDirectionStep)

                    val bestRuneId = state.bestAttackRuneId() ?: "hmm_rune"
                    val totalRunes = state.itemCount("sd_rune") + state.itemCount("hmm_rune")

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HotkeyStoneButton("health_potion", stringResource(R.string.hotkey_hp_potion), state.itemCount("health_potion"), HealthCrimson, onHotkeyHp, "hotkey_health_potion", Modifier.weight(1f))
                            HotkeyStoneButton("mana_potion", stringResource(R.string.hotkey_mp_potion), state.itemCount("mana_potion"), ManaSapphire, onHotkeyMp, "hotkey_mana_potion", Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HotkeyStoneButton(bestRuneId, stringResource(R.string.hotkey_rune), totalRunes, SoulViolet, onHotkeyRune, "hotkey_attack_rune", Modifier.weight(1f))
                            HotkeyStoneButton("rope", stringResource(R.string.hotkey_rope), state.itemCount("rope"), TorchGold, onHotkeyRope, "hotkey_rope", Modifier.weight(1f))
                        }
                    }
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
private fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier.background(color.copy(alpha = 0.22f), RoundedCornerShape(3.dp)).border(1.dp, color, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun CompactStepPad(onDirectionStep: (Direction) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        StepArrowButton(Icons.Default.KeyboardArrowUp, "Move North", { onDirectionStep(Direction.NORTH) }, "dpad_north")
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            StepArrowButton(Icons.AutoMirrored.Filled.ArrowBack, "Move West", { onDirectionStep(Direction.WEST) }, "dpad_west")
            StepArrowButton(Icons.Default.KeyboardArrowDown, "Move South", { onDirectionStep(Direction.SOUTH) }, "dpad_south")
            StepArrowButton(Icons.AutoMirrored.Filled.ArrowForward, "Move East", { onDirectionStep(Direction.EAST) }, "dpad_east")
        }
    }
}

@Composable
private fun StepArrowButton(icon: ImageVector, contentDesc: String, onClick: () -> Unit, testTag: String) {
    Surface(
        color = StoneElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, StoneBorderLight),
        modifier = Modifier.size(48.dp).clickable(onClick = onClick).testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDesc, tint = ParchmentLight, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun HotkeyStoneButton(itemId: String, label: String, count: Int, accent: Color, onClick: () -> Unit, testTag: String, modifier: Modifier = Modifier) {
    val icon = remember(itemId) { EntitySpriteAtlas.itemIcon(itemId) }
    val enabledAlpha = if (count > 0) 1f else 0.45f
    Surface(
        color = StoneSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.2.dp, if (count > 0) accent.copy(alpha = 0.85f) else StoneBorderLight),
        modifier = modifier.height(48.dp).clickable(onClick = onClick).testTag(testTag)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PixelSpritePreview(icon, Modifier.size(26.dp).background(ObsidianVoid, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = ParchmentLight.copy(alpha = enabledAlpha), maxLines = 1)
                Text("x$count", style = MaterialTheme.typography.labelSmall, color = if (count > 0) TorchGold else ParchmentMuted)
            }
        }
    }
}

@Composable
private fun DrawerTabButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit, testTag: String, modifier: Modifier = Modifier) {
    Surface(
        color = if (active) TorchGold else StoneElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (active) TorchGold else StoneBorderLight),
        modifier = modifier.height(44.dp).clickable(onClick = onClick).testTag(testTag)
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = if (active) ObsidianVoid else TorchGold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = if (active) ObsidianVoid else ParchmentLight)
        }
    }
}
