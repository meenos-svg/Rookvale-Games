package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.game.model.ActivePanel
import com.example.game.model.EquipmentSlot
import com.example.game.model.FloorWarningBanner
import com.example.game.model.GameWorldState
import com.example.game.model.Vocation
import com.example.game.multiplayer.ChatChannel
import com.example.game.multiplayer.MultiplayerUiState
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.FireOrange
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.TargetRed
import com.example.ui.theme.TorchGold

/**
 * Fullscreen Landscape 2.5D Isometric RPG Screen:
 * Full-bleed isometric viewport with clean perimeter HUD overlays and single-tap auto-closing modals.
 */
@Composable
fun GameScreen(
    state: GameWorldState,
    multiplayerState: MultiplayerUiState = MultiplayerUiState(),
    saveBanner: String?,
    onTileTapped: (Int, Int) -> Unit,
    onWalkToTile: (Int, Int) -> Unit = onTileTapped,
    onTargetMonster: (Int) -> Unit = {},
    onInspectAlly: (String) -> Unit = {},
    onHotkeyHp: () -> Unit,
    onHotkeyMp: () -> Unit,
    onHotkeyHealRune: () -> Unit = {},
    onHotkeyRune: () -> Unit,
    onHotkeyRope: () -> Unit,
    onUseCustomHotbarSlot: (Int) -> Unit = {},
    onBindCustomHotbarSlot: (Int, String?) -> Unit = { _, _ -> },
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
    var cameraYawDeg by rememberSaveable { mutableFloatStateOf(IsometricCamera.DEFAULT_YAW_DEG) }
    var rotateDragMode by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // 1. FULLSCREEN 360-DEGREE ROTATABLE 2.5D ISOMETRIC WORLD VIEWPORT
        GameCanvasView(
            state = state,
            remotePlayers = multiplayerState.remotePlayers,
            cameraYawDeg = cameraYawDeg,
            rotateDragMode = rotateDragMode,
            onCameraYawDelta = { delta ->
                cameraYawDeg = IsometricCamera.normalizeYawDeg(cameraYawDeg + delta)
            },
            onTileTapped = onTileTapped,
            modifier = Modifier.fillMaxSize()
        )

        // 2. SAFE-AREA HUD OVERLAY LAYER (respects camera display cutouts in landscape)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .displayCutoutPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // TOP-LEFT CLUSTER: Clickable 360-Degree Minimap + Portrait/Name/EXP/HP/MP/Cap/Online
            TopLeftHudCluster(
                state = state,
                multiplayerState = multiplayerState,
                saveBanner = saveBanner,
                cameraYawDeg = cameraYawDeg,
                rotateDragMode = rotateDragMode,
                onResetNorth = { cameraYawDeg = IsometricCamera.DEFAULT_YAW_DEG },
                onToggleRotateDragMode = { rotateDragMode = !rotateDragMode },
                onWalkToTile = onWalkToTile,
                onExpandWorldMap = { onOpenPanel(ActivePanel.WORLD_MAP) },
                onMultiplayerStatusTapped = onMultiplayerStatusTapped,
                modifier = Modifier.align(Alignment.TopStart)
            )

            // LEFT EDGE VERTICAL SKILL DOCK (below Top-Left Minimap, above Bottom-Left Console)
            VerticalSkillDock(
                state = state,
                onOpenSkills = { onOpenPanel(ActivePanel.SKILLS) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(top = 108.dp, bottom = 52.dp)
            )

            // TOP-CENTER FLOOR DESCENT DANGER BANNER
            AnimatedVisibility(
                visible = state.floorWarningBanner != null && state.activePanel == ActivePanel.NONE,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
            ) {
                state.floorWarningBanner?.let { banner ->
                    FloorDescentWarningBanner(banner = banner)
                }
            }

            // TOP-RIGHT UTILITY BAR & LIVE BATTLE LIST
            TopRightUtilityAndBattleCluster(
                state = state,
                remotePlayers = multiplayerState.remotePlayers,
                onOpenPanel = onOpenPanel,
                onSaveManual = onSaveManual,
                onReturnTitle = onReturnTitle,
                onTargetMonster = onTargetMonster,
                onInspectAlly = onInspectAlly,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            // BOTTOM-LEFT TRANSLUCENT CONSOLE & COLLAPSIBLE CHAT
            if (state.activePanel == ActivePanel.NONE) {
                BottomLeftConsoleOverlay(
                    statusLog = state.statusLog,
                    mpState = multiplayerState,
                    currentFloor = state.floor,
                    onToggleExpanded = onToggleChat,
                    onSelectChannel = onSelectChatChannel,
                    onSendMessage = onSendChat,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }

            // BOTTOM-CENTER QUICK ACTION HOTBAR (Core + 2 Customizable Backpack Bind Slots)
            BottomCenterQuickHotbar(
                state = state,
                onHotkeyHp = onHotkeyHp,
                onHotkeyMp = onHotkeyMp,
                onHotkeyHealRune = onHotkeyHealRune,
                onHotkeyRune = onHotkeyRune,
                onHotkeyRope = onHotkeyRope,
                onUseCustomSlot = onUseCustomHotbarSlot,
                onBindCustomSlot = onBindCustomHotbarSlot,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // CENTERED COMPACT MODAL OVERLAY (Tapping outside on isometric ground auto-closes & walks!)
            AnimatedVisibility(
                visible = state.activePanel != ActivePanel.NONE && state.activePanel != ActivePanel.ORACLE,
                enter = fadeIn() + scaleIn(initialScale = 0.94f),
                exit = fadeOut() + scaleOut(targetScale = 0.94f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(min = 340.dp, max = 560.dp)
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                SlideUpGamePanel(
                    state = state,
                    remotePlayers = multiplayerState.remotePlayers,
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
                    onBindRune = onBindRune,
                    onWalkToTile = onWalkToTile
                )
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
private fun FloorDescentWarningBanner(banner: FloorWarningBanner) {
    val accent = when {
        banner.severity <= 1 -> ExpEmerald
        banner.severity == 2 -> TorchGold
        banner.severity == 3 -> FireOrange
        else -> TargetRed
    }
    Surface(
        color = BasaltDark.copy(alpha = 0.92f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.4.dp, accent),
        modifier = Modifier.testTag("floor_warning_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Floor Danger Warning",
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Floor ${banner.floorId}: ${banner.floorName}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = ParchmentLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${banner.dangerLevel} • ${banner.recommendedLevel}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
