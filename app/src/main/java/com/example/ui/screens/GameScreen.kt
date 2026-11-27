package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.game.cloud.AdminAuditAction
import com.example.game.cloud.AdminAuditUiState
import com.example.game.model.ActivePanel
import com.example.game.model.AdminEquipmentClassification
import com.example.game.model.DisplaySettings
import com.example.game.model.EquipmentSlot
import com.example.game.model.FloorWarningBanner
import com.example.game.model.GameWorldState
import com.example.game.model.GraphicsQuality
import com.example.game.model.Vocation
import com.example.game.model.normalizeRegionId
import com.example.game.multiplayer.ChatChannel
import com.example.game.multiplayer.ConnectionStatus
import com.example.game.multiplayer.MultiplayerUiState
import com.example.game.multiplayer.PlayerTradeSession
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.multiplayer.TradeSessionStatus
import com.example.game.world.BiomeWeatherSystem
import com.example.game.world.WeatherType
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.FireOrange
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.TargetRed
import com.example.ui.theme.TorchGold
import kotlinx.coroutines.delay

/**
 * Fullscreen Landscape 2.5D Isometric RPG Screen:
 * Full-bleed isometric viewport with clean perimeter HUD overlays and single-tap auto-closing modals.
 */
@Composable
fun GameScreen(
    state: GameWorldState,
    multiplayerState: MultiplayerUiState = MultiplayerUiState(),
    displaySettings: DisplaySettings = DisplaySettings(),
    saveBanner: String?,
    onTileTapped: (Int, Int) -> Unit,
    onWalkToTile: (Int, Int) -> Unit = onTileTapped,
    onBoardHarborFerry: (String) -> Unit = {},
    onBuyBackReaperItem: (String) -> Unit = {},
    onBuyBackAllReaperItems: () -> Unit = {},
    onDiscardReaperItem: (String) -> Unit = {},
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
    onSelectGraphicsQuality: (GraphicsQuality) -> Unit = {},
    onToggleMinimapSetting: (Boolean) -> Unit = {},
    onToggleFpsCounter: (Boolean) -> Unit = {},
    onToggleWeatherEffects: (Boolean) -> Unit = {},
    onToggleDamageNumbers: (Boolean) -> Unit = {},
    onToggleHealingTicks: (Boolean) -> Unit = {},
    onToggleProcNames: (Boolean) -> Unit = {},
    onToggleStatusAndBlockTicks: (Boolean) -> Unit = {},
    onToggleXpAndSkillGains: (Boolean) -> Unit = {},
    onOpenPanel: (ActivePanel) -> Unit,
    onClosePanel: () -> Unit,
    onUseItem: (String) -> Unit,
    onDropItem: (String) -> Unit,
    onUnequipSlot: (EquipmentSlot) -> Unit,
    onLootAll: (String) -> Unit,
    onLootCorpseItem: (String, Int) -> Unit = { _, _ -> },
    onLootCorpseGold: (String) -> Unit = {},
    onDepositItem: (String, Int) -> Unit,
    onWithdrawItem: (String, Int) -> Unit,
    onDepositGold: (Int) -> Unit = {},
    onDepositAllGold: () -> Unit = {},
    onWithdrawGold: (Long) -> Unit = {},
    onWithdrawAllGold: () -> Unit = {},
    onBuyItem: (String, Int) -> Unit,
    onSellItem: (String, Int) -> Unit,
    onCraftRecipe: (String) -> Unit,
    onBindRune: (String) -> Unit = {},
    onNpcAction: (String) -> Unit = {},
    onCancelWalk: () -> Unit = {},
    onChooseVocationAtOracle: (Vocation) -> Unit,
    onMultiplayerStatusTapped: () -> Unit = {},
    onToggleChat: () -> Unit = {},
    onSelectChatChannel: (ChatChannel) -> Unit = {},
    onSendChat: (String) -> Unit = {},
    onSaveManual: () -> Unit,
    onReturnTitle: () -> Unit,
    onDismissDeath: () -> Unit,
    adminAuditState: AdminAuditUiState = AdminAuditUiState(),
    onAdminSetLevel: (Int) -> Unit = {},
    onAdminGrantEquipment: (AdminEquipmentClassification) -> Unit = {},
    onToggleAdminSpectator: (Boolean) -> Unit = {},
    onAdminSetTimeOfDay: (Float, Boolean) -> Unit = { _, _ -> },
    onAdminSetWeatherOverride: (WeatherType?) -> Unit = {},
    onAdminTeleport: (Int, Int, Int, String?) -> Unit = { _, _, _, _ -> },
    onSelectAdminAuditFilter: (AdminAuditAction?) -> Unit = {},
    onRefreshAdminAuditLog: () -> Unit = {},
    tradeSession: PlayerTradeSession? = null,
    onDismissInspectedPlayer: () -> Unit = {},
    onRequestTrade: (RemotePlayerState) -> Unit = {},
    onAcceptTrade: () -> Unit = {},
    onDeclineTrade: () -> Unit = {},
    onCancelTrade: () -> Unit = {},
    onAddTradeItem: (String, Int) -> Unit = { _, _ -> },
    onRemoveTradeItem: (String, Int) -> Unit = { _, _ -> },
    onSetTradeGold: (Int) -> Unit = {},
    onToggleTradeLock: () -> Unit = {},
    onConfirmTrade: () -> Unit = {}
) {
    val cameraYawState = rememberSaveable { mutableFloatStateOf(IsometricCamera.DEFAULT_YAW_DEG) }
    var rotateDragMode by rememberSaveable { mutableStateOf(false) }
    val liveFpsState = remember { mutableIntStateOf(60) }
    var adminControlExpanded by rememberSaveable(state.isAdmin) { mutableStateOf(false) }

    BackHandler(enabled = state.isAdmin && adminControlExpanded) {
        adminControlExpanded = false
    }

    val latestState by androidx.compose.runtime.rememberUpdatedState(state)
    val latestMultiplayerState by androidx.compose.runtime.rememberUpdatedState(multiplayerState)
    val latestDisplaySettings by androidx.compose.runtime.rememberUpdatedState(displaySettings)
    val latestRotateDragMode by androidx.compose.runtime.rememberUpdatedState(rotateDragMode)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // 1. FULLSCREEN 360-DEGREE ROTATABLE 2.5D ISOMETRIC WORLD VIEWPORT
        // All per-frame state reads are deferred strictly to layout/drawWithCache phases!
        GameCanvasView(
            stateProvider = { latestState },
            remotePlayersProvider = { latestMultiplayerState.remotePlayers },
            graphicsQualityProvider = { latestDisplaySettings.graphicsQuality },
            displaySettingsProvider = { latestDisplaySettings },
            measureFpsProvider = { latestDisplaySettings.showFpsCounter },
            onFpsUpdated = { liveFpsState.intValue = it },
            cameraYawProvider = { cameraYawState.floatValue },
            rotateDragModeProvider = { latestRotateDragMode },
            onCameraYawDelta = { delta ->
                cameraYawState.floatValue = IsometricCamera.normalizeYawDeg(cameraYawState.floatValue + delta)
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
                cameraYawProvider = { cameraYawState.floatValue },
                rotateDragMode = rotateDragMode,
                onResetNorth = { cameraYawState.floatValue = IsometricCamera.DEFAULT_YAW_DEG },
                onToggleRotateDragMode = { rotateDragMode = !rotateDragMode },
                onWalkToTile = onWalkToTile,
                onExpandWorldMap = { onOpenPanel(ActivePanel.WORLD_MAP) },
                onMultiplayerStatusTapped = onMultiplayerStatusTapped,
                onOpenAdminScroll = { if (state.isAdmin) adminControlExpanded = true },
                modifier = Modifier.align(Alignment.TopStart)
            )

            // LEFT: adventure tracker (contract, today's hunt, bestiary, current walk)
            if (state.activePanel == ActivePanel.NONE) {
                AdventureTrackerCard(
                    state = state,
                    onCancelWalk = onCancelWalk,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(top = 132.dp, bottom = 52.dp)
                )
            }

            // RIGHT EDGE: big labelled menu buttons
            RightActionRail(
                activePanel = state.activePanel,
                onOpenPanel = onOpenPanel,
                onSaveManual = onSaveManual,
                onReturnTitle = onReturnTitle,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            // TOP-CENTER LIVE FPS PILL, WEATHER BADGE, BOSS HP NAMEPLATE & FLOOR DESCENT BANNER
            // Isolated into its own composable so 500ms FPS / 2s weather ticks never recompose GameScreen
            TopCenterStatusOverlay(
                state = state,
                displaySettings = displaySettings,
                liveFpsProvider = { liveFpsState.intValue },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
            )

            // TOP-RIGHT UTILITY BAR & LIVE BATTLE LIST
            TopRightUtilityAndBattleCluster(
                state = state,
                remotePlayers = multiplayerState.remotePlayers,
                onOpenPanel = onOpenPanel,
                onSaveManual = onSaveManual,
                onReturnTitle = onReturnTitle,
                onTargetMonster = onTargetMonster,
                onInspectAlly = onInspectAlly,
                onWalkToTile = onWalkToTile,
                showUtilityBar = false,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = RIGHT_RAIL_WIDTH + 6.dp)
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

            // TARGET FRAME just above the hotbar (bosses keep their big nameplate up top)
            if (state.activePanel == ActivePanel.NONE) {
                TargetFrame(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 66.dp)
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

            // BOTTOM-RIGHT COLLAPSIBLE ADMIN CONTROL OVERLAY (strictly visible only when isAdmin == true)
            if (state.isAdmin) {
                AdminControlHudOverlay(
                    state = state,
                    adminAuditState = adminAuditState,
                    expanded = adminControlExpanded,
                    onToggleExpanded = { adminControlExpanded = it },
                    onToggleSpectator = onToggleAdminSpectator,
                    onSetTimeOfDay = onAdminSetTimeOfDay,
                    onSetWeatherOverride = onAdminSetWeatherOverride,
                    onSetLevel = onAdminSetLevel,
                    onGrantEquipment = onAdminGrantEquipment,
                    onTeleport = onAdminTeleport,
                    onSelectAuditFilter = onSelectAdminAuditFilter,
                    onRefreshAuditLog = onRefreshAdminAuditLog,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                )
            }

            // CENTERED COMPACT MODAL OVERLAY (Tapping outside on isometric ground auto-closes & walks!)
            val modalMaxWidth = if (state.activePanel == ActivePanel.WORLD_MAP) 650.dp else 560.dp
            AnimatedVisibility(
                visible = state.activePanel != ActivePanel.NONE && state.activePanel != ActivePanel.ORACLE,
                enter = fadeIn() + scaleIn(initialScale = 0.94f),
                exit = fadeOut() + scaleOut(targetScale = 0.94f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(min = 340.dp, max = modalMaxWidth)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                SlideUpGamePanel(
                    state = state,
                    remotePlayers = multiplayerState.remotePlayers,
                    realmOnlinePlayers = multiplayerState.realmOnlinePlayers,
                    connectionStatus = multiplayerState.status,
                    displaySettings = displaySettings,
                    onClose = onClosePanel,
                    onUseItem = onUseItem,
                    onDropItem = onDropItem,
                    onUnequipSlot = onUnequipSlot,
                    onLootAll = onLootAll,
                    onLootCorpseItem = onLootCorpseItem,
                    onLootCorpseGold = onLootCorpseGold,
                    onDepositItem = onDepositItem,
                    onWithdrawItem = onWithdrawItem,
                    onDepositGold = onDepositGold,
                    onDepositAllGold = onDepositAllGold,
                    onWithdrawGold = onWithdrawGold,
                    onWithdrawAllGold = onWithdrawAllGold,
                    onBuyItem = onBuyItem,
                    onSellItem = onSellItem,
                    onCraftRecipe = onCraftRecipe,
                    onBindRune = onBindRune,
                    onNpcAction = onNpcAction,
                    onWalkToTile = onWalkToTile,
                    onTradePlayer = { peer ->
                        onClosePanel()
                        onRequestTrade(peer)
                    },
                    onBoardHarborFerry = onBoardHarborFerry,
                    onBuyBackReaperItem = onBuyBackReaperItem,
                    onBuyBackAllReaperItems = onBuyBackAllReaperItems,
                    onDiscardReaperItem = onDiscardReaperItem,
                    onSelectGraphicsQuality = onSelectGraphicsQuality,
                    onToggleMinimapSetting = onToggleMinimapSetting,
                    onToggleFpsCounter = onToggleFpsCounter,
                    onToggleWeatherEffects = onToggleWeatherEffects,
                    onToggleDamageNumbers = onToggleDamageNumbers,
                    onToggleHealingTicks = onToggleHealingTicks,
                    onToggleProcNames = onToggleProcNames,
                    onToggleStatusAndBlockTicks = onToggleStatusAndBlockTicks,
                    onToggleXpAndSkillGains = onToggleXpAndSkillGains,
                    onAdminSetLevel = onAdminSetLevel,
                    onAdminGrantEquipment = onAdminGrantEquipment,
                    onToggleAdminSpectator = onToggleAdminSpectator
                )
            }

            // FLOATING ADVENTURER ACTION CARD (Shown when tapping a remote player on the map)
            val inspectedPeer = remember(state.inspectedPlayerId, multiplayerState.remotePlayers, multiplayerState.realmOnlinePlayers) {
                val id = state.inspectedPlayerId ?: return@remember null
                multiplayerState.remotePlayers.firstOrNull { it.playerId == id }
                    ?: multiplayerState.realmOnlinePlayers.firstOrNull { it.playerId == id }
            }
            AnimatedVisibility(
                visible = inspectedPeer != null && tradeSession == null && state.activePanel == ActivePanel.NONE,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 78.dp)
            ) {
                if (inspectedPeer != null) {
                    AdventurerActionCard(
                        peer = inspectedPeer,
                        canTrade = multiplayerState.status == ConnectionStatus.ONLINE,
                        onTrade = { onRequestTrade(inspectedPeer) },
                        onDismiss = onDismissInspectedPlayer
                    )
                }
            }

            // TOP-CENTER FLOATING TRADE INVITE / WAITING BANNER
            AnimatedVisibility(
                visible = tradeSession != null && tradeSession.status != TradeSessionStatus.NEGOTIATING,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp)
            ) {
                if (tradeSession != null && tradeSession.status != TradeSessionStatus.NEGOTIATING) {
                    TradeInviteBanner(
                        session = tradeSession,
                        onAccept = onAcceptTrade,
                        onDecline = onDeclineTrade,
                        onCancel = onCancelTrade
                    )
                }
            }

            // CENTERED DUAL-PANE PLAYER-TO-PLAYER TRADE LEDGER MODAL
            AnimatedVisibility(
                visible = tradeSession?.status == TradeSessionStatus.NEGOTIATING,
                enter = fadeIn() + scaleIn(initialScale = 0.94f),
                exit = fadeOut() + scaleOut(targetScale = 0.94f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(min = 340.dp, max = 600.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (tradeSession != null && tradeSession.status == TradeSessionStatus.NEGOTIATING) {
                    PlayerTradeLedgerModal(
                        state = state,
                        session = tradeSession,
                        onAddItem = onAddTradeItem,
                        onRemoveItem = onRemoveTradeItem,
                        onSetGold = onSetTradeGold,
                        onToggleLock = onToggleTradeLock,
                        onConfirm = onConfirmTrade,
                        onCancel = onCancelTrade
                    )
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
private fun TopCenterStatusOverlay(
    state: GameWorldState,
    displaySettings: DisplaySettings,
    liveFpsProvider: () -> Int,
    modifier: Modifier = Modifier
) {
    var weatherClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(displaySettings.showWeatherEffects, state.floor) {
        if (!displaySettings.showWeatherEffects || state.floor != 0) return@LaunchedEffect
        while (true) {
            weatherClockMs = System.currentTimeMillis()
            delay(2_000L)
        }
    }

    val activeBoss = remember(state.monsters, state.targetMonsterId, state.floor, state.playerX, state.playerY) {
        val targetedBoss = state.monsters.firstOrNull {
            it.id == state.targetMonsterId && it.floor == state.floor && it.type.isBoss && it.hp > 0
        }
        targetedBoss ?: state.monsters
            .filter { it.floor == state.floor && it.type.isBoss && it.hp > 0 }
            .minByOrNull { maxOf(kotlin.math.abs(it.x - state.playerX), kotlin.math.abs(it.y - state.playerY)) }
            ?.takeIf { maxOf(kotlin.math.abs(it.x - state.playerX), kotlin.math.abs(it.y - state.playerY)) <= 10 }
    }

    val resolvedHudWeather = remember(
        state.regionId,
        state.floor,
        state.adminWeatherOverride,
        weatherClockMs,
        displaySettings.showWeatherEffects
    ) {
        if (!displaySettings.showWeatherEffects || state.floor != 0) {
            null
        } else {
            BiomeWeatherSystem.resolveWeather(
                regionId = normalizeRegionId(state.regionId),
                floor = state.floor,
                utcMillis = weatherClockMs,
                adminOverride = state.adminWeatherOverride
            )
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (displaySettings.showFpsCounter || resolvedHudWeather != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (displaySettings.showFpsCounter) {
                    LiveFpsHudBadge(
                        fps = liveFpsProvider(),
                        quality = displaySettings.graphicsQuality
                    )
                }
                resolvedHudWeather?.let { weather ->
                    LiveWeatherHudBadge(weather = weather)
                }
            }
        }
        AnimatedVisibility(
            visible = activeBoss != null && state.activePanel == ActivePanel.NONE,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
        ) {
            activeBoss?.let { boss ->
                BossHpNameplateBar(boss = boss)
            }
        }

        AnimatedVisibility(
            visible = state.floorWarningBanner != null && state.activePanel == ActivePanel.NONE,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
        ) {
            state.floorWarningBanner?.let { banner ->
                FloorDescentWarningBanner(banner = banner)
            }
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
                    text = if (banner.floorId == 0) banner.floorName else "Floor ${banner.floorId}: ${banner.floorName}",
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
