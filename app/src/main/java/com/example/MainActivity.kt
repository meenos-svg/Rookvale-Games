package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.model.ActivePanel
import com.example.game.update.GitHubUpdateChecker
import com.example.game.viewmodel.AppScreen
import com.example.game.viewmodel.GameViewModel
import com.example.ui.screens.GameScreen
import com.example.ui.screens.TitleAndCreationScreen
import com.example.ui.theme.RookvaleTheme

class MainActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyImmersiveFullscreen()
        setContent {
            RookvaleTheme {
                val screen by gameViewModel.screen.collectAsStateWithLifecycle()
                val savedSlots by gameViewModel.savedSlots.collectAsStateWithLifecycle()
                val world by gameViewModel.world.collectAsStateWithLifecycle()
                val mpState by gameViewModel.multiplayerState.collectAsStateWithLifecycle()
                val saveBanner by gameViewModel.saveBanner.collectAsStateWithLifecycle()
                val updateState by gameViewModel.updateState.collectAsStateWithLifecycle()
                val cloudAuthState by gameViewModel.cloudAuthState.collectAsStateWithLifecycle()
                val displaySettings by gameViewModel.displaySettings.collectAsStateWithLifecycle()
                val adminAuditState by gameViewModel.adminAuditState.collectAsStateWithLifecycle()
                val tradeSession by gameViewModel.tradeSession.collectAsStateWithLifecycle()

                when (screen) {
                    AppScreen.TITLE_AND_CREATE -> {
                        TitleAndCreationScreen(
                            savedSlots = savedSlots,
                            updateState = updateState,
                            cloudAuthState = cloudAuthState,
                            displaySettings = displaySettings,
                            onSelectGraphicsQuality = gameViewModel::setGraphicsQuality,
                            onToggleMinimapSetting = gameViewModel::setShowMinimap,
                            onToggleFpsCounter = gameViewModel::setShowFpsCounter,
                            onToggleWeatherEffects = gameViewModel::setShowWeatherEffects,
                            onDownloadUpdate = { url -> openReleaseInBrowser(url) },
                            onDismissUpdate = gameViewModel::dismissUpdateBanner,
                            onCreateCloudCharacter = { name, pin, tunicIdx, hairIdx ->
                                gameViewModel.createCloudCharacter(name, pin, tunicIdx, hairIdx)
                            },
                            onLoginCloudCharacter = { name, pin ->
                                gameViewModel.loginCloudCharacter(name, pin)
                            },
                            onResumeCachedSession = gameViewModel::resumeCachedCloudSession,
                            onSignOutCachedSession = gameViewModel::signOutCachedSession,
                            onClearCloudError = gameViewModel::clearCloudAuthError,
                            onLoadCharacter = { slotId -> gameViewModel.loadCharacter(slotId) },
                            onDeleteSlot = { slotId -> gameViewModel.deleteSaveSlot(slotId) }
                        )
                    }
                    AppScreen.PLAYING -> {
                        BackHandler {
                            if (tradeSession != null) {
                                gameViewModel.cancelTrade()
                            } else if (world.activePanel != ActivePanel.NONE) {
                                gameViewModel.closePanel()
                            } else {
                                gameViewModel.returnToTitle()
                            }
                        }
                        GameScreen(
                            state = world,
                            multiplayerState = mpState,
                            displaySettings = displaySettings,
                            saveBanner = saveBanner,
                            onTileTapped = gameViewModel::onTileTapped,
                            onWalkToTile = gameViewModel::onWalkToTile,
                            onBoardHarborFerry = gameViewModel::onBoardHarborFerry,
                            onBuyBackReaperItem = gameViewModel::onBuyBackReaperItem,
                            onBuyBackAllReaperItems = gameViewModel::onBuyBackAllReaperItems,
                            onDiscardReaperItem = gameViewModel::onDiscardReaperItem,
                            onTargetMonster = gameViewModel::onTargetMonster,
                            onInspectAlly = gameViewModel::onInspectAlly,
                            onHotkeyHp = gameViewModel::onHotkeyHealthPotion,
                            onHotkeyMp = gameViewModel::onHotkeyManaPotion,
                            onHotkeyHealRune = gameViewModel::onHotkeyHealRune,
                            onHotkeyRune = gameViewModel::onHotkeyAttackRune,
                            onHotkeyRope = gameViewModel::onHotkeyRope,
                            onUseCustomHotbarSlot = gameViewModel::onUseCustomHotbarSlot,
                            onBindCustomHotbarSlot = gameViewModel::onBindCustomHotbarSlot,
                            onToggleMinimap = gameViewModel::toggleMinimap,
                            onSelectGraphicsQuality = gameViewModel::setGraphicsQuality,
                            onToggleMinimapSetting = gameViewModel::setShowMinimap,
                            onToggleFpsCounter = gameViewModel::setShowFpsCounter,
                            onToggleWeatherEffects = gameViewModel::setShowWeatherEffects,
                            onToggleDamageNumbers = gameViewModel::setShowDamageNumbers,
                            onToggleHealingTicks = gameViewModel::setShowHealingTicks,
                            onToggleProcNames = gameViewModel::setShowProcNames,
                            onToggleStatusAndBlockTicks = gameViewModel::setShowStatusAndBlockTicks,
                            onToggleXpAndSkillGains = gameViewModel::setShowXpAndSkillGains,
                            onOpenPanel = gameViewModel::openPanel,
                            onClosePanel = gameViewModel::closePanel,
                            onUseItem = gameViewModel::useBackpackItem,
                            onDropItem = gameViewModel::dropBackpackItem,
                            onUnequipSlot = gameViewModel::unequipSlot,
                            onLootAll = gameViewModel::lootAllCorpse,
                            onLootCorpseItem = gameViewModel::lootCorpseItem,
                            onLootCorpseGold = gameViewModel::lootCorpseGold,
                            onDepositItem = gameViewModel::depositItem,
                            onWithdrawItem = gameViewModel::withdrawItem,
                            onDepositGold = gameViewModel::depositGold,
                            onDepositAllGold = gameViewModel::depositAllGold,
                            onWithdrawGold = gameViewModel::withdrawGold,
                            onWithdrawAllGold = gameViewModel::withdrawAllGold,
                            onBuyItem = gameViewModel::buyItem,
                            onSellItem = gameViewModel::sellItem,
                            onCraftRecipe = gameViewModel::craftRecipe,
                            onBindRune = gameViewModel::bindRuneToHotbar,
                            onNpcAction = gameViewModel::onNpcAction,
                            onCancelWalk = gameViewModel::cancelWalk,
                            onOrbTap = gameViewModel::onOrbTap,
                            onUpdateHudLayout = gameViewModel::updateHudLayout,
                            onResetHotbar = gameViewModel::resetHotbar,
                            onChooseVocationAtOracle = gameViewModel::chooseVocationAtOracle,
                            onMultiplayerStatusTapped = gameViewModel::onMultiplayerStatusTapped,
                            onToggleChat = gameViewModel::toggleChat,
                            onSelectChatChannel = gameViewModel::selectChatChannel,
                            onSendChat = gameViewModel::sendChatMessage,
                            onSaveManual = gameViewModel::onManualCloudSave,
                            onReturnTitle = gameViewModel::returnToTitle,
                            onDismissDeath = gameViewModel::dismissDeathReport,
                            adminAuditState = adminAuditState,
                            onAdminSetLevel = { lvl -> gameViewModel.onAdminSetLevel(lvl) },
                            onAdminGrantEquipment = gameViewModel::onAdminGrantEquipment,
                            onToggleAdminSpectator = gameViewModel::onToggleAdminSpectator,
                            onAdminSetTimeOfDay = gameViewModel::onAdminSetTimeOfDay,
                            onAdminSetWeatherOverride = gameViewModel::onAdminSetWeatherOverride,
                            onAdminTeleport = gameViewModel::onAdminTeleport,
                            onSelectAdminAuditFilter = gameViewModel::onSelectAdminAuditFilter,
                            onRefreshAdminAuditLog = gameViewModel::onRefreshAdminAuditLog,
                            tradeSession = tradeSession,
                            onDismissInspectedPlayer = gameViewModel::dismissInspectedPlayer,
                            onRequestTrade = gameViewModel::requestTradeWithPlayer,
                            onAcceptTrade = gameViewModel::acceptTradeInvite,
                            onDeclineTrade = gameViewModel::declineTradeInvite,
                            onCancelTrade = { gameViewModel.cancelTrade() },
                            onAddTradeItem = gameViewModel::addTradeItem,
                            onRemoveTradeItem = gameViewModel::removeTradeItem,
                            onSetTradeGold = gameViewModel::setTradeGold,
                            onToggleTradeLock = gameViewModel::toggleTradeLock,
                            onConfirmTrade = gameViewModel::confirmTrade
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        gameViewModel.onAppResumed()
    }

    override fun onPause() {
        super.onPause()
        gameViewModel.onAppBackgrounded()
    }

    override fun onStop() {
        super.onStop()
        gameViewModel.onAppBackgrounded()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveFullscreen()
        }
    }

    private fun applyImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun openReleaseInBrowser(rawUrl: String) {
        val safeUrl = GitHubUpdateChecker.validateReleaseUrl(rawUrl)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        runCatching { startActivity(intent) }
    }
}
