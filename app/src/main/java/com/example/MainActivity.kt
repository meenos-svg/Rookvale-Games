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
        setContent {
            RookvaleTheme {
                val screen by gameViewModel.screen.collectAsStateWithLifecycle()
                val savedSlots by gameViewModel.savedSlots.collectAsStateWithLifecycle()
                val world by gameViewModel.world.collectAsStateWithLifecycle()
                val mpState by gameViewModel.multiplayerState.collectAsStateWithLifecycle()
                val saveBanner by gameViewModel.saveBanner.collectAsStateWithLifecycle()
                val updateState by gameViewModel.updateState.collectAsStateWithLifecycle()
                val cloudAuthState by gameViewModel.cloudAuthState.collectAsStateWithLifecycle()

                when (screen) {
                    AppScreen.TITLE_AND_CREATE -> {
                        TitleAndCreationScreen(
                            savedSlots = savedSlots,
                            updateState = updateState,
                            cloudAuthState = cloudAuthState,
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
                            if (world.activePanel != ActivePanel.NONE) {
                                gameViewModel.closePanel()
                            } else {
                                gameViewModel.returnToTitle()
                            }
                        }
                        GameScreen(
                            state = world,
                            multiplayerState = mpState,
                            saveBanner = saveBanner,
                            onTileTapped = gameViewModel::onTileTapped,
                            onHotkeyHp = gameViewModel::onHotkeyHealthPotion,
                            onHotkeyMp = gameViewModel::onHotkeyManaPotion,
                            onHotkeyHealRune = gameViewModel::onHotkeyHealRune,
                            onHotkeyRune = gameViewModel::onHotkeyAttackRune,
                            onHotkeyRope = gameViewModel::onHotkeyRope,
                            onToggleMinimap = gameViewModel::toggleMinimap,
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
                            onBuyItem = gameViewModel::buyItem,
                            onSellItem = gameViewModel::sellItem,
                            onCraftRecipe = gameViewModel::craftRecipe,
                            onBindRune = gameViewModel::bindRuneToHotbar,
                            onChooseVocationAtOracle = gameViewModel::chooseVocationAtOracle,
                            onMultiplayerStatusTapped = gameViewModel::onMultiplayerStatusTapped,
                            onToggleChat = gameViewModel::toggleChat,
                            onSelectChatChannel = gameViewModel::selectChatChannel,
                            onSendChat = gameViewModel::sendChatMessage,
                            onSaveManual = gameViewModel::onManualCloudSave,
                            onReturnTitle = gameViewModel::returnToTitle,
                            onDismissDeath = gameViewModel::dismissDeathReport
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

    private fun openReleaseInBrowser(rawUrl: String) {
        val safeUrl = GitHubUpdateChecker.validateReleaseUrl(rawUrl)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        runCatching { startActivity(intent) }
    }
}
