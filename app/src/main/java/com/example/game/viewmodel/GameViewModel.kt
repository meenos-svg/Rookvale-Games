package com.example.game.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.cloud.AdminAuditAction
import com.example.game.cloud.AdminAuditLogRepository
import com.example.game.cloud.AdminAuditUiState
import com.example.game.cloud.CloudAuthResult
import com.example.game.cloud.CloudAuthUiState
import com.example.game.cloud.CloudSaveOutcome
import com.example.game.cloud.CloudSavePayloadCodec
import com.example.game.cloud.CloudSession
import com.example.game.cloud.EventDrivenCloudSaveController
import com.example.game.cloud.SupabaseCloudSaveRepository
import com.example.game.data.DisplaySettingsStore
import com.example.game.data.GameSaveRepository
import com.example.game.data.RookvaleDatabase
import com.example.game.data.SaveSlotEntity
import com.example.game.engine.CraftingAndMiningEngine
import com.example.game.engine.GameEngine
import com.example.game.engine.addLogInternal
import com.example.game.model.ActivePanel
import com.example.game.model.AdminEquipmentClassification
import com.example.game.model.DEFAULT_TIME_OF_DAY_HOURS
import com.example.game.model.Direction
import com.example.game.model.DisplaySettings
import com.example.game.model.EquipmentSlot
import com.example.game.model.GameWorldState
import com.example.game.model.GraphicsQuality
import com.example.game.model.PlayerSkills
import com.example.game.model.Vocation
import com.example.game.model.normalizeRegionId
import com.example.game.multiplayer.ChatChannel
import com.example.game.multiplayer.ConnectionStatus
import com.example.game.multiplayer.LocalPlayerPresencePayload
import com.example.game.multiplayer.MultiplayerConfig
import com.example.game.multiplayer.MultiplayerUiState
import com.example.game.multiplayer.PlayerActionState
import com.example.game.multiplayer.PlayerTradeCoordinator
import com.example.game.multiplayer.PlayerTradeSession
import com.example.game.multiplayer.RealtimeMultiplayerManager
import com.example.game.multiplayer.RemotePlayerInterpolator
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.multiplayer.TradeSessionStatus
import com.example.game.sprites.EntitySpriteAtlas
import com.example.game.sprites.TileSpriteAtlas
import com.example.game.update.GitHubUpdateChecker
import com.example.game.update.UpdateCheckState
import com.example.game.world.WeatherType
import com.example.game.world.WorldMaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class AppScreen {
    TITLE_AND_CREATE,
    PLAYING
}


/** How often the walk loop checks for new work while idle (and the longest it sleeps at once). */
private const val WALK_LOOP_IDLE_POLL_MS = 30L

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameSaveRepository(RookvaleDatabase.getInstance(application).saveSlotDao())
    private val cloudRepository = SupabaseCloudSaveRepository(application)
    private val adminAuditRepository = AdminAuditLogRepository(application)
    private val displaySettingsStore = DisplaySettingsStore(application)
    private val multiplayerManager = RealtimeMultiplayerManager(
        scope = viewModelScope,
        nowProvider = { System.currentTimeMillis() },
        animClockProvider = { SystemClock.uptimeMillis() }
    )

    val savedSlots: StateFlow<List<SaveSlotEntity>> = repository.allSaves
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _displaySettings = MutableStateFlow(displaySettingsStore.load())
    val displaySettings: StateFlow<DisplaySettings> = _displaySettings.asStateFlow()

    private val _screen = MutableStateFlow(AppScreen.TITLE_AND_CREATE)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _world = MutableStateFlow(GameWorldState(showMinimap = _displaySettings.value.showMinimap))
    val world: StateFlow<GameWorldState> = _world.asStateFlow()

    val multiplayerState: StateFlow<MultiplayerUiState> = multiplayerManager.uiState

    private val _updateState = MutableStateFlow(UpdateCheckState())
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    private val _cloudAuthState = MutableStateFlow(
        CloudAuthUiState(
            cachedSession = cloudRepository.getCachedSession(),
            cloudConfigured = cloudRepository.isCloudConfigured()
        )
    )
    val cloudAuthState: StateFlow<CloudAuthUiState> = _cloudAuthState.asStateFlow()

    private val _saveBanner = MutableStateFlow<String?>(null)
    val saveBanner: StateFlow<String?> = _saveBanner.asStateFlow()

    private val _adminAuditState = MutableStateFlow(AdminAuditUiState())
    val adminAuditState: StateFlow<AdminAuditUiState> = _adminAuditState.asStateFlow()

    private val _tradeSession = MutableStateFlow<PlayerTradeSession?>(null)
    val tradeSession: StateFlow<PlayerTradeSession?> = _tradeSession.asStateFlow()

    private var activeCloudSession: CloudSession? = null
    private val persistMutex = Mutex()
    private val cloudSaveController = EventDrivenCloudSaveController(
        scope = viewModelScope,
        debounceMs = EventDrivenCloudSaveController.DEFAULT_DEBOUNCE_MS,
        stateProvider = { _world.value },
        onFlush = { snapshot, logFeedback ->
            executeCloudPersist(snapshot, silent = !logFeedback, logConsoleFeedback = logFeedback)
        }
    )
    private var worldTickJob: Job? = null
    private var attackSubTickJob: Job? = null
    private var fastMonsterSyncJob: Job? = null
    private var walkLoopJob: Job? = null
    private var movementBroadcastJob: Job? = null
    private var onlinePresenceSyncJob: Job? = null
    private var tradeTimeoutJob: Job? = null
    private val emptiedLootTombstones = LinkedHashSet<String>()
    private val completedTradeIds = LinkedHashSet<String>()
    private var lastStepMs: Long = 0L
    @Volatile
    private var lastLocalMoveUptimeMs: Long = 0L
    @Volatile
    private var lastMoveBroadcastUptimeMs: Long = 0L
    @Volatile
    private var lastMoveBroadcastSignature: Long = 0L
    @Volatile
    private var lastHostMonsterSyncHash: Int = 0
    @Volatile
    private var lastMoveSpeedTilesPerSec: Float = RemotePlayerInterpolator.WALK_SPEED_TILES_PER_SEC
    private var lastBackgroundSaveMs: Long = 0L
    private var lastKnownConnectionStatus: ConnectionStatus = ConnectionStatus.OFFLINE_SOLO

    init {
        viewModelScope.launch(Dispatchers.Default) {
            TileSpriteAtlas.warmUp()
            EntitySpriteAtlas.warmUp()
        }
        checkForGitHubUpdates()
        attemptStartupAutoLogin()

        // Automatically push any unsynced offline progress to Supabase as soon as connection returns
        viewModelScope.launch {
            multiplayerManager.uiState.collect { mp ->
                val becameOnline = mp.status == ConnectionStatus.ONLINE && lastKnownConnectionStatus != ConnectionStatus.ONLINE
                lastKnownConnectionStatus = mp.status
                if (becameOnline && _screen.value == AppScreen.PLAYING) {
                    syncPendingOfflineBackupIfNeeded()
                }
            }
        }

        // Apply incoming host monster sync snapshots on non-host clients
        viewModelScope.launch {
            multiplayerManager.monsterSyncEvents.collect { (floor, snapshots) ->
                if (_screen.value != AppScreen.PLAYING) return@collect
                if (multiplayerManager.uiState.value.isFloorHost) return@collect
                val now = SystemClock.uptimeMillis()
                var leveledUp = false
                var xpChanged = false
                var newLevel = 1
                var playerName = ""
                _world.update { prev ->
                    val next = GameEngine.applyRemoteMonsterSync(prev, floor, snapshots, prev.saveId, now)
                    if (next.level > prev.level) {
                        leveledUp = true
                        newLevel = next.level
                        playerName = next.playerName
                    }
                    if (next.xp != prev.xp) xpChanged = true
                    next
                }
                flushOutboundLootSpawns(now)
                if (leveledUp) {
                    multiplayerManager.broadcastSystemCallout("$playerName advanced to Level $newLevel!", floor)
                    persistCurrentState(silent = true)
                } else if (xpChanged) {
                    persistCurrentState(silent = true)
                }
            }
        }

        // Apply incoming co-op monster hits (floating damage + personal instanced loot on kill)
        viewModelScope.launch {
            multiplayerManager.monsterHitEvents.collect { hitEvent ->
                if (_screen.value != AppScreen.PLAYING) return@collect
                val now = SystemClock.uptimeMillis()
                val isHost = multiplayerManager.uiState.value.isFloorHost
                var leveledUp = false
                var xpChanged = false
                var newLevel = 1
                var playerName = ""
                var curFloor = 0
                _world.update { prev ->
                    val next = GameEngine.applyRemoteMonsterHit(prev, hitEvent, prev.saveId, isHost, now)
                    if (next.level > prev.level) {
                        leveledUp = true
                        newLevel = next.level
                        playerName = next.playerName
                        curFloor = next.floor
                    }
                    if (next.xp != prev.xp) xpChanged = true
                    next
                }
                flushOutboundLootSpawns(now)
                if (isHost) {
                    val mpNow = multiplayerManager.uiState.value
                    if (mpNow.status == ConnectionStatus.ONLINE && mpNow.remotePlayers.isNotEmpty()) {
                        broadcastHostMonsterSyncIfChanged(force = true)
                    }
                }
                if (leveledUp) {
                    multiplayerManager.broadcastSystemCallout("$playerName advanced to Level $newLevel!", curFloor)
                    broadcastCurrentState()
                    persistCurrentState(silent = true)
                } else if (xpChanged) {
                    persistCurrentState(silent = true)
                }
            }
        }

        // Apply incoming co-op monster ranged projectiles, boss Fire Wave AoE detonations & status cues
        viewModelScope.launch {
            multiplayerManager.monsterAttackEvents.collect { attackEvent ->
                if (_screen.value != AppScreen.PLAYING) return@collect
                val now = SystemClock.uptimeMillis()
                var died = false
                var statusChanged = false
                _world.update { prev ->
                    val next = GameEngine.applyRemoteMonsterAttack(prev, attackEvent, prev.saveId, now)
                    if (next.deathReport != null && prev.deathReport == null) died = true
                    if (next.hp != prev.hp || next.poisonTicks != prev.poisonTicks || next.fireTicks != prev.fireTicks) {
                        statusChanged = true
                    }
                    next
                }
                if (statusChanged) broadcastCurrentState()
                if (died) persistCurrentState(silent = true)
            }
        }

        // Apply incoming co-op player heal/damage/block floating numbers & spell projectiles
        viewModelScope.launch {
            multiplayerManager.playerActionFxEvents.collect { fxEvent ->
                if (_screen.value != AppScreen.PLAYING) return@collect
                val now = SystemClock.uptimeMillis()
                _world.update { prev ->
                    GameEngine.applyRemotePlayerActionFx(prev, fxEvent, now)
                }
            }
        }

        // Apply incoming shared ground loot (monster kills & dropped bags with 5-second owner lock)
        viewModelScope.launch {
            multiplayerManager.lootSpawnEvents.collect { spawnEvent ->
                if (_screen.value != AppScreen.PLAYING) return@collect
                if (spawnEvent.corpseId in emptiedLootTombstones) return@collect
                val now = SystemClock.uptimeMillis()
                _world.update { prev ->
                    GameEngine.applyRemoteLootSpawn(prev, spawnEvent, now)
                }
            }
        }

        // Apply incoming ground loot claims from nearby players so shared piles update immediately
        viewModelScope.launch {
            multiplayerManager.lootClaimEvents.collect { claimEvent ->
                if (_screen.value != AppScreen.PLAYING) return@collect
                if (claimEvent.emptied || (claimEvent.remainingGold <= 0 && claimEvent.remainingItems.isEmpty())) {
                    recordEmptiedLootTombstone(claimEvent.corpseId)
                }
                _world.update { prev ->
                    GameEngine.applyRemoteLootClaim(prev, claimEvent)
                }
            }
        }

        // Apply incoming real-time player-to-player trade protocol signals
        viewModelScope.launch {
            multiplayerManager.tradeSignalEvents.collect { signal ->
                if (_screen.value != AppScreen.PLAYING) return@collect
                val now = SystemClock.uptimeMillis()
                val res = PlayerTradeCoordinator.handleIncomingSignal(
                    world = _world.value,
                    currentSession = _tradeSession.value,
                    signal = signal,
                    completedTradeIds = completedTradeIds,
                    nowMs = now
                )
                applyTradeTransition(res)
            }
        }
    }

    private fun attemptStartupAutoLogin() {
        val cached = cloudRepository.getCachedSession() ?: return
        _cloudAuthState.update {
            it.copy(
                isAutoLoggingIn = true,
                isBusy = true,
                cachedSession = cached,
                errorMessage = null,
                infoMessage = "Signing in as ${cached.characterName}..."
            )
        }
        viewModelScope.launch {
            val result = cloudRepository.autoLoginCachedSession()
            if (result is CloudAuthResult.Success && _screen.value == AppScreen.TITLE_AND_CREATE) {
                enterGameWithCloudResult(result)
            } else {
                _cloudAuthState.update {
                    it.copy(
                        isAutoLoggingIn = false,
                        isBusy = false,
                        cachedSession = cloudRepository.getCachedSession(),
                        infoMessage = null
                    )
                }
            }
        }
    }

    fun createCloudCharacter(
        name: String,
        pin: String,
        tunicColorIndex: Int = 0,
        hairColorIndex: Int = 0
    ) {
        if (_cloudAuthState.value.isBusy) return
        _cloudAuthState.update { it.copy(isBusy = true, errorMessage = null, infoMessage = "Creating hero...") }
        viewModelScope.launch {
            val result = cloudRepository.createCharacter(name, pin) { cleanName ->
                val fresh = GameEngine.createNewWorld(
                    name = cleanName,
                    vocation = Vocation.ADVENTURER,
                    tunicColorIndex = tunicColorIndex,
                    hairColorIndex = hairColorIndex
                )
                GameEngine.toSaveData(fresh)
            }
            when (result) {
                is CloudAuthResult.Success -> enterGameWithCloudResult(result)
                is CloudAuthResult.Failure -> _cloudAuthState.update {
                    it.copy(isBusy = false, errorMessage = result.message, infoMessage = null)
                }
            }
        }
    }

    fun loginCloudCharacter(name: String, pin: String) {
        if (_cloudAuthState.value.isBusy) return
        _cloudAuthState.update { it.copy(isBusy = true, errorMessage = null, infoMessage = "Loading hero from cloud...") }
        viewModelScope.launch {
            val result = cloudRepository.loginAndRecover(
                rawName = name,
                pin = pin
            )
            when (result) {
                is CloudAuthResult.Success -> enterGameWithCloudResult(result)
                is CloudAuthResult.Failure -> _cloudAuthState.update {
                    it.copy(isBusy = false, errorMessage = result.message, infoMessage = null)
                }
            }
        }
    }

    fun resumeCachedCloudSession() {
        if (_cloudAuthState.value.isBusy) return
        val cached = cloudRepository.getCachedSession() ?: return
        _cloudAuthState.update {
            it.copy(isBusy = true, errorMessage = null, infoMessage = "Syncing ${cached.characterName}...")
        }
        viewModelScope.launch {
            val result = cloudRepository.autoLoginCachedSession()
            if (result is CloudAuthResult.Success) {
                enterGameWithCloudResult(result)
            } else {
                _cloudAuthState.update {
                    it.copy(
                        isBusy = false,
                        cachedSession = cloudRepository.getCachedSession(),
                        errorMessage = "Could not load cached hero. Use Name + PIN to recover from cloud.",
                        infoMessage = null
                    )
                }
            }
        }
    }

    fun signOutCachedSession() {
        cloudRepository.clearCachedSession()
        activeCloudSession = null
        _adminAuditState.value = AdminAuditUiState()
        _world.update {
            it.copy(
                isAdmin = false,
                infiniteHealth = false,
                isSpectator = false,
                timeOfDayHours = DEFAULT_TIME_OF_DAY_HOURS,
                activePanel = ActivePanel.NONE
            )
        }
        _cloudAuthState.update {
            it.copy(cachedSession = null, errorMessage = null, infoMessage = "Cleared cached login on this device.")
        }
    }

    fun clearCloudAuthError() {
        _cloudAuthState.update { it.copy(errorMessage = null) }
    }

    private suspend fun enterGameWithCloudResult(result: CloudAuthResult.Success) {
        activeCloudSession = result.session
        val hydrated = GameEngine.fromSaveData(result.saveData)
        val withNotice = hydrated.copy(
            showMinimap = _displaySettings.value.showMinimap,
            statusLog = (hydrated.statusLog + result.notice).takeLast(6)
        )
        // Keep Room local slot list in sync as well
        repository.saveGame(result.saveData)
        _world.value = withNotice
        cloudSaveController.markSynced(withNotice)
        _screen.value = AppScreen.PLAYING
        _cloudAuthState.update {
            it.copy(
                isBusy = false,
                isAutoLoggingIn = false,
                cachedSession = result.session,
                errorMessage = null,
                infoMessage = null
            )
        }
        if (withNotice.isAdmin) {
            hydrateAndSyncAdminAuditLog()
        } else {
            _adminAuditState.value = AdminAuditUiState()
        }
        multiplayerManager.connect(withNotice.toPresencePayload())
        startGameLoops()
    }

    fun startNewCharacter(name: String, tunicColorIndex: Int = 0, hairColorIndex: Int = 0) {
        val fresh = GameEngine.createNewWorld(
            name = name,
            vocation = Vocation.ADVENTURER,
            tunicColorIndex = tunicColorIndex,
            hairColorIndex = hairColorIndex
        ).copy(showMinimap = _displaySettings.value.showMinimap)
        activeCloudSession = null
        _world.value = fresh
        _screen.value = AppScreen.PLAYING
        multiplayerManager.connect(fresh.toPresencePayload())
        startGameLoops()
        cloudSaveController.flushNow(fresh, force = true, logConsoleFeedback = false)
    }

    fun chooseVocationAtOracle(vocation: Vocation) {
        val now = SystemClock.uptimeMillis()
        var promoted = false
        var name = ""
        var floor = 0
        _world.update { prev ->
            val next = GameEngine.promoteVocation(prev, vocation, now)
            if (next.vocation != prev.vocation) {
                promoted = true
                name = next.playerName
                floor = next.floor
            }
            next
        }
        if (promoted) {
            multiplayerManager.broadcastSystemCallout("$name has been chosen as a ${vocation.displayName} by The Oracle!", floor)
            broadcastCurrentState()
            persistCurrentState(silent = true)
        }
    }

    fun loadCharacter(slotId: String) {
        viewModelScope.launch {
            val loaded = repository.loadGame(slotId) ?: return@launch
            val state = GameEngine.fromSaveData(loaded).copy(showMinimap = _displaySettings.value.showMinimap)
            val backup = cloudRepository.localStore.getLocalBackupByPlayerId(slotId)
            activeCloudSession = backup?.session
            if (backup != null) {
                cloudRepository.localStore.saveCachedSession(backup.session)
                _cloudAuthState.update { it.copy(cachedSession = backup.session) }
            }
            _world.value = state
            cloudSaveController.markSynced(state)
            if (state.isAdmin) {
                hydrateAndSyncAdminAuditLog()
            } else {
                _adminAuditState.value = AdminAuditUiState()
            }
            _screen.value = AppScreen.PLAYING
            multiplayerManager.connect(state.toPresencePayload())
            startGameLoops()
        }
    }

    fun deleteSaveSlot(slotId: String) {
        viewModelScope.launch { repository.deleteSave(slotId) }
    }

    fun returnToTitle() {
        cloudSaveController.flushNow(_world.value, force = false, logConsoleFeedback = false)
        cloudSaveController.reset()
        multiplayerManager.disconnect()
        stopGameLoops()
        // Explicitly clear all elevated session flags & audit memory on logout / character switch
        _adminAuditState.value = AdminAuditUiState()
        _world.update {
            it.copy(
                isAdmin = false,
                infiniteHealth = false,
                isSpectator = false,
                timeOfDayHours = DEFAULT_TIME_OF_DAY_HOURS,
                activePanel = ActivePanel.NONE
            )
        }
        _cloudAuthState.update {
            it.copy(
                isBusy = false,
                isAutoLoggingIn = false,
                cachedSession = cloudRepository.getCachedSession(),
                errorMessage = null,
                infoMessage = null
            )
        }
        _screen.value = AppScreen.TITLE_AND_CREATE
    }

    fun onManualCloudSave() {
        if (_world.value.saveId.isBlank() || _screen.value != AppScreen.PLAYING) return
        cloudSaveController.flushNow(_world.value, force = true, logConsoleFeedback = true)
    }

    fun onAppBackgrounded() {
        val snapshot = _world.value
        if (snapshot.saveId.isBlank() || _screen.value != AppScreen.PLAYING) return
        val now = SystemClock.uptimeMillis()
        val saveData = GameEngine.toSaveData(snapshot)
        // Snapshot slim character-only save_data JSON synchronously on the main thread
        val preEncodedJson = runCatching { CloudSavePayloadCodec.encode(saveData) }.getOrNull()
        val session = activeCloudSession
        // Synchronous SharedPreferences write so progress is safe even if OS kills process immediately
        if (session != null) {
            cloudRepository.localStore.saveCachedSession(session)
            cloudRepository.localStore.saveLocalBackup(
                session = session,
                saveData = saveData,
                updatedAtMs = System.currentTimeMillis(),
                pendingCloudSync = true,
                preEncodedJsonString = preEncodedJson?.toString()
            )
        }
        if (now - lastBackgroundSaveMs < 1500L) return
        lastBackgroundSaveMs = now
        val flushedCloud = cloudSaveController.flushNow(snapshot, force = false, logConsoleFeedback = false)
        if (!flushedCloud) {
            viewModelScope.launch {
                withContext(NonCancellable) {
                    persistMutex.withLock { runCatching { repository.saveGame(saveData) } }
                }
            }
        }
    }

    /**
     * Event-driven entry point for gameplay mutations: compares permanent character state
     * and schedules a single coalesced cloud upsert debounced by 5 seconds.
     * Routine walking steps, monster attacks, and respawns trigger ZERO Postgres writes.
     */
    fun persistCurrentState(silent: Boolean = false, logConsoleFeedback: Boolean = false) {
        val snapshot = _world.value
        if (snapshot.saveId.isBlank() || _screen.value != AppScreen.PLAYING) return
        if (logConsoleFeedback || !silent) {
            cloudSaveController.flushNow(snapshot, force = true, logConsoleFeedback = logConsoleFeedback)
        } else {
            cloudSaveController.onStateMutated(snapshot)
        }
    }

    private fun executeCloudPersist(
        snapshot: GameWorldState,
        silent: Boolean,
        logConsoleFeedback: Boolean
    ) {
        if (snapshot.saveId.isBlank()) return
        val saveData = GameEngine.toSaveData(snapshot)
        val preEncodedJson = runCatching { CloudSavePayloadCodec.encode(saveData) }.getOrNull()
        val session = activeCloudSession
        viewModelScope.launch {
            val outcome = withContext(NonCancellable) {
                persistMutex.withLock {
                    runCatching { repository.saveGame(saveData) }
                    if (session != null) {
                        runCatching {
                            cloudRepository.saveProgress(
                                session = session,
                                saveData = saveData,
                                preEncodedSaveJson = preEncodedJson
                            )
                        }.getOrDefault(CloudSaveOutcome.SAVED_LOCAL_OFFLINE)
                    } else {
                        CloudSaveOutcome.SAVED_LOCAL_OFFLINE
                    }
                }
            }

            if (logConsoleFeedback) {
                val consoleMsg = when (outcome) {
                    CloudSaveOutcome.SYNCED_CLOUD -> "Character saved to cloud."
                    CloudSaveOutcome.SAVED_LOCAL_OFFLINE -> if (session != null) {
                        "Saved locally (offline — will sync to cloud when connected)."
                    } else {
                        "Character saved locally."
                    }
                }
                _world.update { st -> st.copy(statusLog = (st.statusLog + consoleMsg).takeLast(6)) }
            }

            if (!silent) {
                _saveBanner.value = if (outcome == CloudSaveOutcome.SYNCED_CLOUD) "Cloud Saved" else "Saved Offline"
                delay(1800L)
                _saveBanner.value = null
            }
        }
    }

    private fun scheduleDebouncedSkillSave() {
        cloudSaveController.onStateMutated(_world.value)
    }

    private fun syncPendingOfflineBackupIfNeeded() {
        val session = activeCloudSession ?: return
        viewModelScope.launch {
            val outcome = cloudRepository.syncPendingOfflineBackup(session)
            if (outcome == CloudSaveOutcome.SYNCED_CLOUD) {
                _world.update { st ->
                    st.copy(statusLog = (st.statusLog + "Synced offline progress to cloud.").takeLast(6))
                }
            }
            if (_world.value.isAdmin) {
                flushAdminAuditQueueIfNeeded()
            }
        }
    }

    private fun startGameLoops() {
        stopGameLoops()

        // 100ms combat sub-tick so variable weapon speeds and multiplayer hit broadcasts stay ultra-responsive
        attackSubTickJob = viewModelScope.launch {
            while (true) {
                delay(100L)
                val now = SystemClock.uptimeMillis()
                var leveledUp = false
                var xpGained = false
                var skillLevelUp = false
                var skillPointsGained = false
                var attackedAction: PlayerActionState? = null
                var facingChanged = false
                var newLevel = 1
                var floor = 0
                var name = ""
                _world.update { prev ->
                    if (prev.targetMonsterId == null) prev
                    else {
                        val next = GameEngine.processPlayerAttackSubTick(prev, now)
                        if (next.level > prev.level) {
                            leveledUp = true
                            newLevel = next.level
                            floor = next.floor
                            name = next.playerName
                        }
                        if (next.xp != prev.xp) xpGained = true
                        if (totalSkillLevels(next.skills) > totalSkillLevels(prev.skills)) {
                            skillLevelUp = true
                        } else if (next.skills != prev.skills) {
                            skillPointsGained = true
                        }
                        if (next.playerFacing != prev.playerFacing) facingChanged = true
                        if (next.outboundPlayerEffects.size > prev.outboundPlayerEffects.size) {
                            attackedAction = next.outboundPlayerEffects.lastOrNull()?.actionState
                        }
                        next
                    }
                }
                flushOutboundMonsterHits()
                flushOutboundPlayerEffects()
                if (leveledUp) {
                    multiplayerManager.broadcastSystemCallout("$name advanced to Level $newLevel!", floor)
                    broadcastCurrentState(attackedAction ?: PlayerActionState.IDLE)
                    persistCurrentState(silent = true)
                } else {
                    if (attackedAction != null || facingChanged) {
                        broadcastCurrentState(attackedAction ?: PlayerActionState.IDLE)
                    }
                    if (xpGained || skillLevelUp || skillPointsGained) {
                        persistCurrentState(silent = true)
                    }
                }
            }
        }

        // 1.0s World Tick: monster AI (host-driven when online), status ticks, ore vein regen & monster respawns
        worldTickJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val now = SystemClock.uptimeMillis()
                val mp = multiplayerManager.uiState.value
                val isHost = mp.isFloorHost || mp.status != ConnectionStatus.ONLINE

                var diedOrLeveled = false
                var leveledUp = false
                var skillLevelUp = false
                var skillPointsGained = false
                var floorChanged = false
                var vitalsChanged = false
                var tickAction: PlayerActionState? = null
                var newLevel = 1
                var curFloor = 0
                var name = ""

                _world.update { prev ->
                    val next = GameEngine.processCombatTick(
                        state = prev,
                        nowMs = now,
                        isFloorHost = isHost,
                        localPlayerId = prev.saveId,
                        remotePlayersOnFloor = mp.remotePlayers
                    )
                    if (next.level > prev.level) {
                        leveledUp = true
                        newLevel = next.level
                    }
                    if (next.level != prev.level || (next.deathReport != null && prev.deathReport == null)) {
                        diedOrLeveled = true
                    }
                    if (totalSkillLevels(next.skills) > totalSkillLevels(prev.skills)) {
                        skillLevelUp = true
                    } else if (next.skills != prev.skills) {
                        skillPointsGained = true
                    }
                    if (next.floor != prev.floor) floorChanged = true
                    if (next.hp != prev.hp ||
                        next.maxHp != prev.maxHp ||
                        next.playerX != prev.playerX ||
                        next.playerY != prev.playerY ||
                        next.playerFacing != prev.playerFacing ||
                        (next.poisonTicks > 0) != (prev.poisonTicks > 0) ||
                        (next.fireTicks > 0) != (prev.fireTicks > 0)
                    ) {
                        vitalsChanged = true
                    }
                    val lastAction = next.outboundPlayerEffects.lastOrNull()?.actionState
                    if (lastAction != null && lastAction != PlayerActionState.IDLE) {
                        tickAction = lastAction
                    }
                    curFloor = next.floor
                    name = next.playerName
                    next
                }

                flushOutboundMonsterHits()
                flushOutboundMonsterAttacks()
                flushOutboundPlayerEffects()
                flushOutboundSystemAlerts()

                val latest = _world.value
                if (floorChanged) {
                    multiplayerManager.switchFloor(latest.floor, latest.toPresencePayload())
                } else if (vitalsChanged || tickAction != null) {
                    multiplayerManager.broadcastLocalState(latest.toPresencePayload(tickAction ?: PlayerActionState.IDLE))
                }

                if (leveledUp) {
                    multiplayerManager.broadcastSystemCallout("$name advanced to Level $newLevel!", curFloor)
                }

                // Elected Floor Host broadcasts authoritative monster positions, HP & targetPlayerId
                val updatedMp = multiplayerManager.uiState.value
                if (updatedMp.status == ConnectionStatus.ONLINE && updatedMp.isFloorHost && !floorChanged) {
                    broadcastHostMonsterSyncIfChanged(force = true)
                }

                if (diedOrLeveled || floorChanged || skillLevelUp) {
                    persistCurrentState(silent = true)
                } else if (skillPointsGained) {
                    scheduleDebouncedSkillSave()
                }
            }
        }

        // Fast 350ms delta-aware Floor Host monster sync (only active while online with peers on the same floor)
        fastMonsterSyncJob = viewModelScope.launch {
            while (true) {
                delay(350L)
                if (_screen.value != AppScreen.PLAYING) continue
                val mp = multiplayerManager.uiState.value
                if (mp.status == ConnectionStatus.ONLINE && mp.isFloorHost && mp.remotePlayers.isNotEmpty()) {
                    broadcastHostMonsterSyncIfChanged(force = false)
                }
            }
        }

        // Auto-walk & melee Target Auto-Follow stepping loop. Steps are scheduled from the previous
        // step's timestamp (level-based interval) so they never drift, double up, or pause mid-walk.
        walkLoopJob = viewModelScope.launch {
            while (true) {
                val snapshot = _world.value
                val hasWalkWork = snapshot.walkPath.isNotEmpty() || snapshot.longRangeWaypoints.isNotEmpty() ||
                    snapshot.longRangeGoal != null || snapshot.autoFollowTarget
                if (!hasWalkWork) {
                    delay(WALK_LOOP_IDLE_POLL_MS)
                    continue
                }
                val waitMs = snapshot.lastPlayerMoveMs + GameEngine.stepIntervalMs(snapshot.level) - SystemClock.uptimeMillis()
                if (waitMs > 0L) {
                    // Sleep exactly until the next step is due (taps in between only change the route)
                    delay(waitMs.coerceAtMost(GameEngine.BASE_STEP_INTERVAL_MS))
                    continue
                }
                val now = SystemClock.uptimeMillis()
                val remotes = multiplayerManager.uiState.value.remotePlayers
                var changedFloor = false
                var changedRegion = false
                var movedTile = false
                var facingChanged = false
                _world.update { prev ->
                    if (prev.walkPath.isEmpty() && prev.longRangeWaypoints.isEmpty() && prev.longRangeGoal == null && !prev.autoFollowTarget) prev
                    else {
                        val next = GameEngine.stepAutoFollowOrPath(prev, now, remotes)
                        if (next.floor != prev.floor) changedFloor = true
                        if (next.regionId != prev.regionId) changedRegion = true
                        if (next.playerX != prev.playerX || next.playerY != prev.playerY) movedTile = true
                        if (next.playerFacing != prev.playerFacing) facingChanged = true
                        next
                    }
                }
                if (changedFloor) {
                    val cur = _world.value
                    multiplayerManager.switchFloor(cur.floor, cur.toPresencePayload())
                    persistCurrentState(silent = true)
                } else if (movedTile || changedRegion) {
                    lastLocalMoveUptimeMs = now
                    lastMoveSpeedTilesPerSec = GameEngine.walkSpeedTilesPerSec(_world.value.level)
                    broadcastMovement(PlayerActionState.WALKING, now)
                    if (changedRegion) persistCurrentState(silent = true)
                } else if (facingChanged) {
                    broadcastMovement(PlayerActionState.IDLE, now)
                }
                // Nothing moved (e.g. already adjacent to a followed target): don't spin
                if (!movedTile && !changedFloor) delay(WALK_LOOP_IDLE_POLL_MS)
            }
        }

        // Adaptive 50ms Supabase Realtime Broadcast movement streamer (fast while moving, silent when idle)
        movementBroadcastJob = viewModelScope.launch {
            var wasMoving = false
            while (true) {
                delay(50L)
                if (_screen.value != AppScreen.PLAYING) {
                    wasMoving = false
                    continue
                }
                val cur = _world.value
                if (cur.saveId.isBlank()) continue
                val nowUptime = SystemClock.uptimeMillis()
                val isMoving = cur.walkPath.isNotEmpty() ||
                    cur.longRangeGoal != null ||
                    (cur.autoFollowTarget && cur.targetMonsterId != null) ||
                    (nowUptime - lastLocalMoveUptimeMs) <= 220L
                if (isMoving) {
                    val signature = computeMovementSignature(cur)
                    val elapsed = nowUptime - lastMoveBroadcastUptimeMs
                    if ((signature != lastMoveBroadcastSignature && elapsed >= 45L) || elapsed >= 140L) {
                        broadcastMovement(PlayerActionState.WALKING, nowUptime)
                    }
                    wasMoving = true
                } else if (wasMoving) {
                    wasMoving = false
                    broadcastMovement(PlayerActionState.IDLE, nowUptime)
                }
            }
        }

        // Adaptive online presence & REST peer discovery (immediate first fetch, 3s offline recovery, 10s online check)
        onlinePresenceSyncJob = viewModelScope.launch(Dispatchers.IO) {
            var offlineTicks = 0
            var initialFetchDone = false
            while (true) {
                if (initialFetchDone) {
                    val statusBeforeSleep = multiplayerManager.uiState.value.status
                    val pollDelayMs = when (statusBeforeSleep) {
                        ConnectionStatus.ONLINE -> 10_000L
                        ConnectionStatus.CONNECTING -> 3_000L
                        ConnectionStatus.OFFLINE_SOLO -> 3_000L
                    }
                    delay(pollDelayMs)
                }
                if (_screen.value != AppScreen.PLAYING) {
                    offlineTicks = 0
                    initialFetchDone = false
                    delay(1_000L)
                    continue
                }
                val cur = _world.value
                if (cur.saveId.isBlank()) {
                    delay(500L)
                    continue
                }
                initialFetchDone = true

                val status = multiplayerManager.uiState.value.status
                if (MultiplayerConfig.isConfigured()) {
                    if (status == ConnectionStatus.OFFLINE_SOLO) {
                        offlineTicks++
                        multiplayerManager.ensureConnectedOrRetry(cur.toPresencePayload())
                    } else {
                        offlineTicks = 0
                    }
                    val polled = runCatching {
                        cloudRepository.fetchRecentOnlinePlayers(
                            sinceEpochMs = System.currentTimeMillis() - 90_000L,
                            excludePlayerId = cur.saveId,
                            excludeCharacterName = cur.playerName
                        )
                    }.getOrDefault(emptyList())
                    multiplayerManager.mergePolledPlayers(
                        polled = polled,
                        localFloor = cur.floor,
                        localRegionId = cur.regionId,
                        localPlayerId = cur.saveId,
                        localPlayerName = cur.playerName
                    )
                    // If WebSocket is blocked by network/firewall, periodically flush cloud presence so REST peers see us
                    if (status == ConnectionStatus.OFFLINE_SOLO && activeCloudSession != null && offlineTicks % 6 == 0) {
                        runCatching {
                            cloudSaveController.flushNow(cur, force = false, logConsoleFeedback = false)
                        }
                    }
                }
            }
        }
    }

    private fun stopGameLoops() {
        attackSubTickJob?.cancel()
        worldTickJob?.cancel()
        fastMonsterSyncJob?.cancel()
        walkLoopJob?.cancel()
        movementBroadcastJob?.cancel()
        onlinePresenceSyncJob?.cancel()
        attackSubTickJob = null
        worldTickJob = null
        fastMonsterSyncJob = null
        walkLoopJob = null
        movementBroadcastJob = null
        onlinePresenceSyncJob = null
    }

    private fun totalSkillLevels(skills: PlayerSkills): Int =
        skills.melee.level + skills.fist.level + skills.club.level + skills.sword.level + skills.axe.level +
            skills.distance.level + skills.shielding.level +
            skills.magic.level + skills.fishing.level + skills.mining.level

    private fun flushOutboundMonsterHits() {
        val pending = _world.value.outboundMonsterHits
        if (pending.isNotEmpty()) {
            val snapshot = _world.value
            _world.update { it.copy(outboundMonsterHits = emptyList()) }
            for (hit in pending) {
                multiplayerManager.broadcastMonsterHit(
                    hit = hit,
                    attackerId = snapshot.saveId,
                    attackerName = snapshot.playerName
                )
            }
        }
        flushOutboundSystemAlerts()
        flushOutboundLootSpawns()
    }

    private fun flushOutboundLootSpawns(nowMs: Long = SystemClock.uptimeMillis()) {
        val pending = _world.value.outboundLootSpawns
        if (pending.isEmpty()) return
        _world.update { it.copy(outboundLootSpawns = emptyList()) }
        for (corpse in pending) {
            multiplayerManager.broadcastLootSpawn(corpse, nowMs)
        }
    }

    private fun recordEmptiedLootTombstone(corpseId: String) {
        if (emptiedLootTombstones.size >= 128) {
            emptiedLootTombstones.iterator().let { it.next(); it.remove() }
        }
        emptiedLootTombstones.add(corpseId)
    }

    private fun broadcastLootClaimState(corpseId: String, floor: Int, claimerId: String, claimerName: String) {
        val remaining = _world.value.corpses.firstOrNull { it.id == corpseId }
        val emptied = remaining == null || (remaining.gold <= 0 && remaining.items.isEmpty())
        if (emptied) recordEmptiedLootTombstone(corpseId)
        multiplayerManager.broadcastLootClaim(
            corpseId = corpseId,
            floor = floor,
            claimerId = claimerId,
            claimerName = claimerName,
            remainingGold = remaining?.gold ?: 0,
            remainingItems = remaining?.items ?: emptyList(),
            emptied = emptied
        )
    }

    private fun flushOutboundSystemAlerts() {
        val alerts = _world.value.outboundSystemAlerts
        if (alerts.isEmpty()) return
        val floor = _world.value.floor
        _world.update { it.copy(outboundSystemAlerts = emptyList()) }
        for (msg in alerts) {
            multiplayerManager.broadcastSystemCallout(msg, floor)
        }
    }

    private fun flushOutboundMonsterAttacks() {
        val pending = _world.value.outboundMonsterAttacks
        if (pending.isEmpty()) return
        val snapshot = _world.value
        _world.update { it.copy(outboundMonsterAttacks = emptyList()) }
        for (atk in pending) {
            multiplayerManager.broadcastMonsterAttack(
                attack = atk,
                senderId = snapshot.saveId
            )
        }
    }

    private fun flushOutboundPlayerEffects() {
        val pending = _world.value.outboundPlayerEffects
        if (pending.isEmpty()) return
        val snapshot = _world.value
        _world.update { it.copy(outboundPlayerEffects = emptyList()) }
        for (fx in pending) {
            multiplayerManager.broadcastPlayerActionFx(
                event = fx,
                senderId = snapshot.saveId
            )
        }
    }

    private fun computeMovementSignature(state: GameWorldState): Long {
        val nextStep = state.walkPath.firstOrNull()
        var h = state.floor.toLong() * 31L + state.playerX.toLong()
        h = h * 31L + state.playerY.toLong()
        h = h * 31L + state.playerFacing.ordinal.toLong()
        h = h * 31L + (nextStep?.first ?: state.playerX).toLong()
        h = h * 31L + (nextStep?.second ?: state.playerY).toLong()
        h = h * 31L + state.hp.toLong()
        return h
    }

    private fun broadcastHostMonsterSyncIfChanged(force: Boolean) {
        val latest = _world.value
        val snapshots = GameEngine.buildFloorMonsterSnapshots(latest)
        val syncHash = 31 * latest.floor + snapshots.hashCode()
        if (!force && syncHash == lastHostMonsterSyncHash) return
        lastHostMonsterSyncHash = syncHash
        multiplayerManager.broadcastMonsterSync(
            floor = latest.floor,
            monsters = snapshots
        )
    }

    private fun broadcastMovement(
        actionState: PlayerActionState = PlayerActionState.WALKING,
        nowUptimeMs: Long = SystemClock.uptimeMillis()
    ) {
        val cur = _world.value
        if (cur.saveId.isNotBlank()) {
            lastMoveBroadcastUptimeMs = nowUptimeMs
            lastMoveBroadcastSignature = computeMovementSignature(cur)
            multiplayerManager.broadcastMovementPacket(cur.toPresencePayload(actionState, nowUptimeMs))
        }
    }

    private fun broadcastCurrentState(actionState: PlayerActionState = PlayerActionState.IDLE) {
        val cur = _world.value
        if (cur.saveId.isNotBlank()) {
            multiplayerManager.broadcastLocalState(cur.toPresencePayload(actionState))
        }
    }

    private fun GameWorldState.toPresencePayload(
        actionState: PlayerActionState = PlayerActionState.IDLE,
        nowUptimeMs: Long = SystemClock.uptimeMillis()
    ): LocalPlayerPresencePayload {
        val nowMs = System.currentTimeMillis()
        val (tgtX, tgtY) = when {
            walkPath.isNotEmpty() -> {
                // Look ahead up to 3 collinear tiles along the active walk path so dead-reckoning
                // maintains a smooth forward heading across minor WebSocket jitter without cutting corners.
                val first = walkPath.first()
                val stepDx = (first.first - playerX).coerceIn(-1, 1)
                val stepDy = (first.second - playerY).coerceIn(-1, 1)
                var endPt = first
                var lookIdx = 1
                while (lookIdx < walkPath.size && lookIdx < 3) {
                    val nxt = walkPath[lookIdx]
                    if (nxt.first - endPt.first == stepDx && nxt.second - endPt.second == stepDy) {
                        endPt = nxt
                        lookIdx++
                    } else {
                        break
                    }
                }
                endPt
            }
            actionState == PlayerActionState.WALKING && (nowUptimeMs - lastLocalMoveUptimeMs) <= 260L -> {
                val fl = WorldMaps.getFloor(floor)
                val nx = (playerX + playerFacing.dx).coerceIn(0, fl.width - 1)
                val ny = (playerY + playerFacing.dy).coerceIn(0, fl.height - 1)
                if (fl.tileAt(nx, ny).walkable) nx to ny else playerX to playerY
            }
            else -> playerX to playerY
        }
        return LocalPlayerPresencePayload(
            playerId = saveId,
            name = playerName,
            level = level,
            vocation = vocation,
            tunicColorIndex = tunicColorIndex,
            hairColorIndex = hairColorIndex,
            x = playerX,
            y = playerY,
            fromX = prevPlayerX,
            fromY = prevPlayerY,
            targetX = tgtX,
            targetY = tgtY,
            moveSpeed = lastMoveSpeedTilesPerSec,
            floor = floor,
            regionId = normalizeRegionId(regionId),
            hp = hp,
            maxHp = maxHp,
            joinedAt = nowMs,
            lastSeenMs = nowMs,
            isPoisoned = poisonTicks > 0,
            isBurning = fireTicks > 0,
            direction = playerFacing,
            actionState = actionState,
            weaponId = equipment.weaponId,
            offhandId = equipment.shieldId,
            armorId = equipment.armorId,
            helmetId = equipment.helmetId,
            bootsId = equipment.bootsId,
            isAdmin = isAdmin,
            isSpectator = isSpectator
        )
    }

    fun checkForGitHubUpdates() {
        viewModelScope.launch {
            val result = GitHubUpdateChecker.checkForUpdate(getApplication())
            _updateState.value = result
        }
    }

    fun dismissUpdateBanner() {
        val ts = _updateState.value.releaseTimestampIso
        GitHubUpdateChecker.dismissRelease(getApplication(), ts)
        _updateState.update { it.copy(updateAvailable = false) }
    }

    fun onAppResumed() {
        if (_screen.value == AppScreen.PLAYING) {
            val cur = _world.value
            if (cur.saveId.isNotBlank()) {
                multiplayerManager.ensureConnectedOrRetry(cur.toPresencePayload())
                syncPendingOfflineBackupIfNeeded()
            }
        }
    }

    fun onMultiplayerStatusTapped() {
        val cur = _world.value
        if (cur.saveId.isBlank()) return
        val result = multiplayerManager.ensureConnectedOrRetry(cur.toPresencePayload())
        val mp = multiplayerManager.uiState.value
        val realmCount = maxOf(mp.realmOnlineCount, mp.floorOnlineCount)
        val msg = when (result) {
            RealtimeMultiplayerManager.RetryResult.ALREADY_ONLINE ->
                "Online in Rookvale ($realmCount online, ${mp.floorOnlineCount} on Floor ${cur.floor})."
            RealtimeMultiplayerManager.RetryResult.CONNECTING ->
                "Connecting to Rookvale Floor ${cur.floor} live co-op..."
            RealtimeMultiplayerManager.RetryResult.MISSING_CREDENTIALS ->
                "Offline Solo: configure SUPABASE_ANON_KEY in Secrets to enable live co-op."
        }
        _world.update { st -> st.copy(statusLog = (st.statusLog + msg).takeLast(14)) }
    }

    fun toggleChat() {
        multiplayerManager.toggleChatExpanded()
    }

    fun selectChatChannel(channel: ChatChannel) {
        multiplayerManager.selectChatChannel(channel)
    }

    fun sendChatMessage(text: String) {
        val cur = _world.value
        val ch = multiplayerManager.uiState.value.activeChatChannel
        multiplayerManager.sendChatMessage(
            rawText = text,
            channel = ch,
            senderName = cur.playerName,
            floor = cur.floor,
            senderIsAdmin = cur.isAdmin
        )
    }

    fun onTileTapped(tx: Int, ty: Int) {
        val now = SystemClock.uptimeMillis()
        val remotes = multiplayerManager.uiState.value.remotePlayers
        var floorChanged = false
        var moved = false
        var pathStarted = false
        var facingChanged = false
        var healed = false
        var skillChanged = false
        var skillLevelUp = false
        var inventoryChanged = false
        _world.update { prev ->
            val next = GameEngine.handleTileTap(prev, tx, ty, now, remotes)
            if (next.floor != prev.floor) floorChanged = true
            if (next.playerX != prev.playerX || next.playerY != prev.playerY) moved = true
            if (next.walkPath != prev.walkPath && next.walkPath.isNotEmpty()) pathStarted = true
            if (next.playerFacing != prev.playerFacing) facingChanged = true
            if (next.hp != prev.hp) healed = true
            if (next.backpack != prev.backpack) inventoryChanged = true
            if (totalSkillLevels(next.skills) > totalSkillLevels(prev.skills)) skillLevelUp = true
            else if (next.skills != prev.skills) skillChanged = true
            next
        }
        flushOutboundPlayerEffects()
        if (floorChanged) {
            val cur = _world.value
            multiplayerManager.switchFloor(cur.floor, cur.toPresencePayload())
            persistCurrentState(silent = true)
        } else {
            if (moved || pathStarted) {
                lastLocalMoveUptimeMs = now
                lastMoveSpeedTilesPerSec = GameEngine.walkSpeedTilesPerSec(_world.value.level)
                broadcastMovement(PlayerActionState.WALKING, now)
            } else if (healed) {
                broadcastCurrentState(PlayerActionState.HEALING)
            } else if (facingChanged) {
                broadcastMovement(PlayerActionState.IDLE, now)
            }
            if (skillLevelUp) persistCurrentState(silent = true)
            else if (skillChanged || inventoryChanged) scheduleDebouncedSkillSave()
        }
    }

    fun onDirectionStep(dir: Direction) {
        val now = SystemClock.uptimeMillis()
        // Manual steps follow the same level-based pace as walking, and never cut a glide short
        if (now - lastStepMs < GameEngine.stepIntervalMs(_world.value.level)) return
        if (!GameEngine.isStepReady(_world.value, now)) return
        lastStepMs = now
        val remotes = multiplayerManager.uiState.value.remotePlayers
        var floorChanged = false
        var moved = false
        var facingChanged = false
        _world.update { prev ->
            val next = GameEngine.stepDirection(prev, dir, now, remotes)
            if (next.floor != prev.floor) floorChanged = true
            if (next.playerX != prev.playerX || next.playerY != prev.playerY) moved = true
            if (next.playerFacing != prev.playerFacing) facingChanged = true
            next
        }
        if (floorChanged) {
            val cur = _world.value
            multiplayerManager.switchFloor(cur.floor, cur.toPresencePayload())
            persistCurrentState(silent = true)
        } else if (moved) {
            lastLocalMoveUptimeMs = now
            lastMoveSpeedTilesPerSec = GameEngine.walkSpeedTilesPerSec(_world.value.level)
            broadcastMovement(PlayerActionState.WALKING, now)
        } else if (facingChanged) {
            broadcastMovement(PlayerActionState.IDLE, now)
        }
    }

    fun onHotkeyHealthPotion() {
        val now = SystemClock.uptimeMillis()
        var used = false
        _world.update { prev ->
            val next = GameEngine.useHealthPotion(prev, now)
            if (next.hp != prev.hp || next.outboundPlayerEffects.size > prev.outboundPlayerEffects.size) used = true
            next
        }
        flushOutboundPlayerEffects()
        if (used) broadcastCurrentState(PlayerActionState.HEALING)
    }

    fun onHotkeyManaPotion() {
        val now = SystemClock.uptimeMillis()
        var used = false
        _world.update { prev ->
            val next = GameEngine.useManaPotion(prev, now)
            if (next.mp != prev.mp || next.outboundPlayerEffects.size > prev.outboundPlayerEffects.size) used = true
            next
        }
        flushOutboundPlayerEffects()
        if (used) broadcastCurrentState(PlayerActionState.HEALING)
    }

    fun onHotkeyHealRune() {
        val now = SystemClock.uptimeMillis()
        val remotes = multiplayerState.value.remotePlayers
        var used = false
        var skillLevelUp = false
        var skillChanged = false
        _world.update { prev ->
            val next = GameEngine.useHealingRune(prev, now, remotes)
            if (next.backpack != prev.backpack || next.hp != prev.hp) used = true
            if (totalSkillLevels(next.skills) > totalSkillLevels(prev.skills)) skillLevelUp = true
            else if (next.skills != prev.skills) skillChanged = true
            next
        }
        flushOutboundPlayerEffects()
        if (used) broadcastCurrentState(PlayerActionState.HEALING)
        if (skillLevelUp) persistCurrentState(silent = true)
        else if (skillChanged || used) scheduleDebouncedSkillSave()
    }

    fun bindRuneToHotbar(runeId: String) {
        var changed = false
        _world.update { prev ->
            val next = GameEngine.bindRuneToHotbar(prev, runeId)
            if (next.boundAttackRuneId != prev.boundAttackRuneId || next.boundHealRuneId != prev.boundHealRuneId) {
                changed = true
            }
            next
        }
        if (changed) persistCurrentState(silent = true)
    }

    fun onHotkeyAttackRune() {
        val now = SystemClock.uptimeMillis()
        var leveledUp = false
        var skillLevelUp = false
        var skillChanged = false
        var casted = false
        var newLevel = 1
        var floor = 0
        var name = ""
        _world.update { prev ->
            val next = GameEngine.useAttackRune(prev, now)
            if (next.level > prev.level) {
                leveledUp = true
                newLevel = next.level
                floor = next.floor
                name = next.playerName
            }
            if (totalSkillLevels(next.skills) > totalSkillLevels(prev.skills)) skillLevelUp = true
            else if (next.skills != prev.skills) skillChanged = true
            if (next.outboundMonsterHits.size > prev.outboundMonsterHits.size) casted = true
            next
        }
        flushOutboundMonsterHits()
        flushOutboundPlayerEffects()
        if (leveledUp) {
            multiplayerManager.broadcastSystemCallout("$name advanced to Level $newLevel!", floor)
            broadcastCurrentState(PlayerActionState.CASTING)
            persistCurrentState(silent = true)
        } else {
            if (casted) broadcastCurrentState(PlayerActionState.CASTING)
            if (skillLevelUp) persistCurrentState(silent = true)
            else if (skillChanged) scheduleDebouncedSkillSave()
        }
    }

    fun onTargetMonster(monsterId: Int) {
        _world.update { prev -> GameEngine.targetMonsterById(prev, monsterId) }
    }

    fun onInspectAlly(playerId: String) {
        val now = SystemClock.uptimeMillis()
        val ally = multiplayerState.value.remotePlayers.firstOrNull { it.playerId == playerId } ?: return
        _world.update { prev ->
            prev.copy(
                inspectedPlayerId = ally.playerId,
                inspectedPlayerUntilMs = now + 8_000L,
                statusLog = (prev.statusLog + "Ally ${ally.name} (Lvl ${ally.level} ${ally.vocation.displayName}) at (${ally.x}, ${ally.y}).").takeLast(6)
            )
        }
    }

    fun dismissInspectedPlayer() {
        _world.update { it.copy(inspectedPlayerId = null, inspectedPlayerUntilMs = 0L) }
    }

    fun requestTradeWithPlayer(partner: RemotePlayerState) {
        if (multiplayerState.value.status != ConnectionStatus.ONLINE) {
            _world.update { prev ->
                prev.copy(statusLog = (prev.statusLog + "Connect online to trade with other players.").takeLast(6))
            }
            return
        }
        val now = SystemClock.uptimeMillis()
        val res = PlayerTradeCoordinator.startOutgoingInvite(
            world = _world.value,
            currentSession = _tradeSession.value,
            partner = partner,
            nowMs = now
        )
        applyTradeTransition(res)
    }

    fun acceptTradeInvite() {
        val res = PlayerTradeCoordinator.acceptIncomingInvite(_world.value, _tradeSession.value)
        applyTradeTransition(res)
    }

    fun declineTradeInvite() {
        val res = PlayerTradeCoordinator.declineIncomingInvite(_world.value, _tradeSession.value)
        applyTradeTransition(res)
    }

    fun cancelTrade(reason: String = "Trade cancelled.") {
        val res = PlayerTradeCoordinator.cancelTrade(_world.value, _tradeSession.value, reason)
        applyTradeTransition(res)
    }

    fun addTradeItem(itemId: String, delta: Int = 1) {
        val res = PlayerTradeCoordinator.addLocalItem(_world.value, _tradeSession.value, itemId, delta)
        applyTradeTransition(res)
    }

    fun removeTradeItem(itemId: String, delta: Int = 1) {
        val res = PlayerTradeCoordinator.removeLocalItem(_world.value, _tradeSession.value, itemId, delta)
        applyTradeTransition(res)
    }

    fun setTradeGold(amount: Int) {
        val res = PlayerTradeCoordinator.setLocalGold(_world.value, _tradeSession.value, amount)
        applyTradeTransition(res)
    }

    fun toggleTradeLock() {
        val res = PlayerTradeCoordinator.toggleLocalLock(_world.value, _tradeSession.value)
        applyTradeTransition(res)
    }

    fun confirmTrade() {
        val now = SystemClock.uptimeMillis()
        val res = PlayerTradeCoordinator.confirmTrade(
            world = _world.value,
            currentSession = _tradeSession.value,
            completedTradeIds = completedTradeIds,
            nowMs = now
        )
        applyTradeTransition(res)
    }

    private fun applyTradeTransition(res: PlayerTradeCoordinator.TransitionResult) {
        val nextWorld = if (res.logMessage != null) {
            res.worldState.copy(statusLog = (res.worldState.statusLog + res.logMessage).takeLast(6))
        } else {
            res.worldState
        }
        _world.value = nextWorld
        _tradeSession.value = res.session
        res.outboundSignal?.let { multiplayerManager.broadcastTradeSignal(it) }

        tradeTimeoutJob?.cancel()
        val session = res.session
        if (session != null && (session.status == TradeSessionStatus.INVITING || session.status == TradeSessionStatus.INCOMING_INVITE)) {
            val expectedId = session.tradeId
            tradeTimeoutJob = viewModelScope.launch {
                delay(PlayerTradeCoordinator.INVITE_TIMEOUT_MS)
                val cur = _tradeSession.value
                if (cur != null && cur.tradeId == expectedId && cur.status != TradeSessionStatus.NEGOTIATING) {
                    cancelTrade("Trade request with ${cur.partnerName} timed out.")
                }
            }
        }

        if (res.swapCompleted) {
            if (completedTradeIds.size > 64) {
                completedTradeIds.iterator().let { it.next(); it.remove() }
            }
            persistCurrentState(silent = true)
        }
    }

    private var routePlanJob: Job? = null

    /** Stop the current auto-walk (from the tracker's X button). */
    fun cancelWalk() {
        routePlanJob?.cancel()
        _world.update {
            it.copy(walkPath = emptyList(), longRangeGoal = null, longRangeWaypoints = emptyList())
                .addLogInternal("Stopped walking.")
        }
    }

    /**
     * Map "walk here": long routes are planned off the main thread so the screen never freezes,
     * then applied if the hero is still where the plan started.
     */
    fun onWalkToTile(x: Int, y: Int) {
        routePlanJob?.cancel()
        // Stop any current walk and close the map right away so the tap feels instant
        _world.update { it.copy(walkPath = emptyList(), longRangeGoal = null, longRangeWaypoints = emptyList(), activePanel = ActivePanel.NONE).addLogInternal("Charting a route...") }
        routePlanJob = viewModelScope.launch {
            repeat(2) {
                val snapshot = _world.value
                val planned = withContext(Dispatchers.Default) { GameEngine.walkToCoordinate(snapshot, x, y) }
                var applied = false
                _world.update { cur ->
                    if (cur.floor != snapshot.floor || cur.playerX != snapshot.playerX || cur.playerY != snapshot.playerY) {
                        cur
                    } else {
                        applied = true
                        val routed = cur.copy(
                            walkPath = planned.walkPath,
                            longRangeGoal = planned.longRangeGoal,
                            longRangeWaypoints = emptyList(),
                            autoFollowTarget = false
                        )
                        val note = if (planned.statusLog != snapshot.statusLog) planned.statusLog.lastOrNull() else null
                        if (note != null) routed.addLogInternal(note) else routed
                    }
                }
                if (applied) {
                    val st = _world.value
                    if (st.walkPath.isNotEmpty() || st.longRangeGoal != null) {
                        val now = SystemClock.uptimeMillis()
                        lastLocalMoveUptimeMs = now
                        lastMoveSpeedTilesPerSec = GameEngine.walkSpeedTilesPerSec(st.level)
                        broadcastMovement(PlayerActionState.WALKING, now)
                    }
                    return@launch
                }
            }
        }
    }

    fun onBoardHarborFerry(destinationPortId: String) {
        val now = SystemClock.uptimeMillis()
        var sailed = false
        _world.update { prev ->
            val next = GameEngine.boardHarborFerry(prev, destinationPortId, now)
            if (next.playerX != prev.playerX || next.playerY != prev.playerY) sailed = true
            next
        }
        if (sailed) {
            lastLocalMoveUptimeMs = now
            broadcastCurrentState(PlayerActionState.IDLE)
            persistCurrentState(silent = true)
        }
    }

    fun onBindCustomHotbarSlot(slotIndex: Int, itemId: String?) {
        var changed = false
        _world.update { prev ->
            val next = GameEngine.bindCustomHotbarSlot(prev, slotIndex, itemId)
            if (next.customHotbarSlots != prev.customHotbarSlots) changed = true
            next
        }
        if (changed) persistCurrentState(silent = true)
    }

    fun onUseCustomHotbarSlot(slotIndex: Int) {
        val now = SystemClock.uptimeMillis()
        var leveledUp = false
        var skillLevelUp = false
        var skillChanged = false
        var used = false
        var newLevel = 1
        var floor = 0
        var name = ""
        _world.update { prev ->
            val next = GameEngine.useCustomHotbarSlot(prev, slotIndex, now)
            if (next.level > prev.level) {
                leveledUp = true
                newLevel = next.level
                floor = next.floor
                name = next.playerName
            }
            if (totalSkillLevels(next.skills) > totalSkillLevels(prev.skills)) skillLevelUp = true
            else if (next.skills != prev.skills) skillChanged = true
            if (next.hp != prev.hp || next.mp != prev.mp || next.backpack != prev.backpack || next.floor != prev.floor) {
                used = true
            }
            next
        }
        flushOutboundMonsterHits()
        flushOutboundPlayerEffects()
        if (leveledUp) {
            multiplayerManager.broadcastSystemCallout("$name advanced to Level $newLevel!", floor)
            broadcastCurrentState(PlayerActionState.CASTING)
            persistCurrentState(silent = true)
        } else {
            if (used) broadcastCurrentState(PlayerActionState.HEALING)
            if (skillLevelUp) persistCurrentState(silent = true)
            else if (skillChanged || used) scheduleDebouncedSkillSave()
        }
    }

    fun onHotkeyRope() {
        val now = SystemClock.uptimeMillis()
        var changed = false
        _world.update { prev ->
            val next = GameEngine.useRope(prev, now)
            if (next.floor != prev.floor) changed = true
            next
        }
        if (changed) {
            val cur = _world.value
            multiplayerManager.switchFloor(cur.floor, cur.toPresencePayload())
            persistCurrentState(silent = true)
        }
    }

    fun setGraphicsQuality(quality: GraphicsQuality) {
        if (_displaySettings.value.graphicsQuality == quality) return
        displaySettingsStore.saveGraphicsQuality(quality)
        _displaySettings.update { it.copy(graphicsQuality = quality) }
    }

    fun setShowMinimap(show: Boolean) {
        displaySettingsStore.saveShowMinimap(show)
        _displaySettings.update { it.copy(showMinimap = show) }
        _world.update { it.copy(showMinimap = show) }
    }

    fun setShowFpsCounter(show: Boolean) {
        displaySettingsStore.saveShowFpsCounter(show)
        _displaySettings.update { it.copy(showFpsCounter = show) }
    }

    fun setShowWeatherEffects(show: Boolean) {
        displaySettingsStore.saveShowWeatherEffects(show)
        _displaySettings.update { it.copy(showWeatherEffects = show) }
    }

    fun setShowDamageNumbers(show: Boolean) {
        displaySettingsStore.saveShowDamageNumbers(show)
        _displaySettings.update { it.copy(showDamageNumbers = show) }
    }

    fun setShowHealingTicks(show: Boolean) {
        displaySettingsStore.saveShowHealingTicks(show)
        _displaySettings.update { it.copy(showHealingTicks = show) }
    }

    fun setShowProcNames(show: Boolean) {
        displaySettingsStore.saveShowProcNames(show)
        _displaySettings.update { it.copy(showProcNames = show) }
    }

    fun setShowStatusAndBlockTicks(show: Boolean) {
        displaySettingsStore.saveShowStatusAndBlockTicks(show)
        _displaySettings.update { it.copy(showStatusAndBlockTicks = show) }
    }

    fun setShowXpAndSkillGains(show: Boolean) {
        displaySettingsStore.saveShowXpAndSkillGains(show)
        _displaySettings.update { it.copy(showXpAndSkillGains = show) }
    }

    fun toggleMinimap() {
        setShowMinimap(!_world.value.showMinimap)
    }

    fun openPanel(panel: ActivePanel) {
        _world.update {
            val nextPanel = if (it.activePanel == panel) ActivePanel.NONE else panel
            it.copy(
                activePanel = nextPanel,
                inspectingCorpseId = if (nextPanel != ActivePanel.LOOT_CORPSE) null else it.inspectingCorpseId,
                activeBiomeStall = if (nextPanel != ActivePanel.BIOME_STALL) null else it.activeBiomeStall
            )
        }
    }

    fun closePanel() {
        _world.update { it.copy(activePanel = ActivePanel.NONE, inspectingCorpseId = null, activeBiomeStall = null) }
    }

    fun craftRecipe(recipeId: String) {
        val now = SystemClock.uptimeMillis()
        var crafted = false
        _world.update { prev ->
            val next = CraftingAndMiningEngine.executeRecipe(prev, recipeId, now)
            if (next.backpack != prev.backpack || next.toolBag != prev.toolBag || next.skills != prev.skills) {
                crafted = true
            }
            next
        }
        flushOutboundPlayerEffects()
        if (crafted) {
            broadcastCurrentState(PlayerActionState.CASTING)
            persistCurrentState(silent = true)
        }
    }

    fun useBackpackItem(itemId: String) {
        val now = SystemClock.uptimeMillis()
        var action: PlayerActionState? = null
        var hpChanged = false
        var equipChanged = false
        var floorChanged = false
        var skillLevelUp = false
        var bagChanged = false
        var baitChanged = false
        _world.update { prev ->
            val next = GameEngine.useOrEquipBackpackItem(prev, itemId, now)
            if (next.hp != prev.hp) hpChanged = true
            if (next.equipment != prev.equipment) equipChanged = true
            if (next.floor != prev.floor) floorChanged = true
            if (next.backpack != prev.backpack) bagChanged = true
            if (next.selectedBaitId != prev.selectedBaitId) baitChanged = true
            if (totalSkillLevels(next.skills) > totalSkillLevels(prev.skills)) skillLevelUp = true
            if (next.outboundPlayerEffects.size > prev.outboundPlayerEffects.size) {
                action = next.outboundPlayerEffects.lastOrNull()?.actionState
            }
            next
        }
        flushOutboundMonsterHits()
        flushOutboundPlayerEffects()
        if (floorChanged) {
            val cur = _world.value
            multiplayerManager.switchFloor(cur.floor, cur.toPresencePayload())
            persistCurrentState(silent = true)
        } else {
            if (hpChanged || equipChanged || action != null) broadcastCurrentState(action ?: PlayerActionState.IDLE)
            if (equipChanged || skillLevelUp || baitChanged) persistCurrentState(silent = true)
            else if (bagChanged) scheduleDebouncedSkillSave()
        }
    }

    fun dropBackpackItem(itemId: String) {
        val now = SystemClock.uptimeMillis()
        var dropped = false
        _world.update { prev ->
            val next = GameEngine.dropItem(prev, itemId, now)
            if (next.corpses.size != prev.corpses.size || next.backpack != prev.backpack || next.toolBag != prev.toolBag) {
                dropped = true
            }
            next
        }
        flushOutboundLootSpawns(now)
        if (dropped) persistCurrentState(silent = true)
    }

    fun unequipSlot(slot: EquipmentSlot) {
        var changed = false
        _world.update { prev ->
            val next = GameEngine.unequipSlot(prev, slot)
            if (next.equipment != prev.equipment) changed = true
            next
        }
        if (changed) {
            broadcastCurrentState(PlayerActionState.IDLE)
            persistCurrentState(silent = true)
        }
    }

    fun lootCorpseItem(corpseId: String, stackIndex: Int) {
        val now = SystemClock.uptimeMillis()
        var looted = false
        var corpseFloor = _world.value.floor
        var claimerId = _world.value.saveId
        var claimerName = _world.value.playerName
        _world.update { prev ->
            corpseFloor = prev.corpses.firstOrNull { it.id == corpseId }?.floor ?: prev.floor
            claimerId = prev.saveId
            claimerName = prev.playerName
            val next = GameEngine.lootItemFromCorpse(prev, corpseId, stackIndex, now)
            if (next.backpack != prev.backpack || next.toolBag != prev.toolBag || next.corpses != prev.corpses) {
                looted = true
            }
            next
        }
        if (looted) {
            broadcastLootClaimState(corpseId, corpseFloor, claimerId, claimerName)
            persistCurrentState(silent = true)
        }
    }

    fun lootCorpseGold(corpseId: String) {
        val now = SystemClock.uptimeMillis()
        var looted = false
        var corpseFloor = _world.value.floor
        var claimerId = _world.value.saveId
        var claimerName = _world.value.playerName
        _world.update { prev ->
            corpseFloor = prev.corpses.firstOrNull { it.id == corpseId }?.floor ?: prev.floor
            claimerId = prev.saveId
            claimerName = prev.playerName
            val next = GameEngine.lootGoldFromCorpse(prev, corpseId, now)
            if (next.gold != prev.gold || next.corpses != prev.corpses) {
                looted = true
            }
            next
        }
        if (looted) {
            broadcastLootClaimState(corpseId, corpseFloor, claimerId, claimerName)
            persistCurrentState(silent = true)
        }
    }

    fun lootAllCorpse(corpseId: String) {
        val now = SystemClock.uptimeMillis()
        var looted = false
        var corpseFloor = _world.value.floor
        var claimerId = _world.value.saveId
        var claimerName = _world.value.playerName
        _world.update { prev ->
            corpseFloor = prev.corpses.firstOrNull { it.id == corpseId }?.floor ?: prev.floor
            claimerId = prev.saveId
            claimerName = prev.playerName
            val next = GameEngine.lootAllFromCorpse(prev, corpseId, now)
            if (next.gold != prev.gold || next.backpack != prev.backpack || next.toolBag != prev.toolBag || next.corpses != prev.corpses) {
                looted = true
            }
            next
        }
        if (looted) {
            broadcastLootClaimState(corpseId, corpseFloor, claimerId, claimerName)
            persistCurrentState(silent = true)
        }
    }

    fun depositItem(itemId: String, count: Int = 1) {
        if (itemId.isBlank() || count <= 0) return
        var changed = false
        _world.update { prev ->
            val next = GameEngine.depositItem(prev, itemId, count)
            if (next.depot != prev.depot || next.backpack != prev.backpack || next.toolBag != prev.toolBag) {
                changed = true
            }
            next
        }
        if (changed) persistCurrentState(silent = true)
    }

    fun withdrawItem(itemId: String, count: Int = 1) {
        if (itemId.isBlank() || count <= 0) return
        var changed = false
        _world.update { prev ->
            val next = GameEngine.withdrawItem(prev, itemId, count)
            if (next.depot != prev.depot || next.backpack != prev.backpack || next.toolBag != prev.toolBag) {
                changed = true
            }
            next
        }
        if (changed) persistCurrentState(silent = true)
    }

    fun depositGold(amount: Int) {
        var changed = false
        _world.update { prev ->
            val next = GameEngine.depositGold(prev, amount)
            if (next.gold != prev.gold || next.bankGold != prev.bankGold) {
                changed = true
            }
            next
        }
        if (changed) persistCurrentState(silent = true)
    }

    fun depositAllGold() {
        var changed = false
        _world.update { prev ->
            val next = GameEngine.depositAllGold(prev)
            if (next.gold != prev.gold || next.bankGold != prev.bankGold) {
                changed = true
            }
            next
        }
        if (changed) persistCurrentState(silent = true)
    }

    fun withdrawGold(amount: Long) {
        var changed = false
        _world.update { prev ->
            val next = GameEngine.withdrawGold(prev, amount)
            if (next.gold != prev.gold || next.bankGold != prev.bankGold) {
                changed = true
            }
            next
        }
        if (changed) persistCurrentState(silent = true)
    }

    fun withdrawAllGold() {
        var changed = false
        _world.update { prev ->
            val next = GameEngine.withdrawAllGold(prev)
            if (next.gold != prev.gold || next.bankGold != prev.bankGold) {
                changed = true
            }
            next
        }
        if (changed) persistCurrentState(silent = true)
    }

    fun buyItem(itemId: String, quantity: Int = 1) {
        _world.update { GameEngine.buyShopItem(it, itemId, quantity) }
        persistCurrentState(silent = true)
    }

    fun sellItem(itemId: String, quantity: Int = 1) {
        _world.update { GameEngine.sellBackpackItem(it, itemId, quantity) }
        persistCurrentState(silent = true)
    }

    /** Actions from the townsfolk panels (hunt contracts, bestiary claims). */
    fun onNpcAction(action: String) {
        val now = SystemClock.uptimeMillis()
        _world.update { com.example.game.engine.NpcInteractions.handleAction(it, action, now) }
        persistCurrentState(silent = true)
    }

    fun onBuyBackReaperItem(itemId: String) {
        var changed = false
        _world.update { prev ->
            val next = GameEngine.buyBackReaperItem(prev, itemId)
            if (next.reaperVault != prev.reaperVault) changed = true
            next
        }
        if (changed) persistCurrentState(silent = true)
    }

    fun onBuyBackAllReaperItems() {
        var changed = false
        _world.update { prev ->
            val next = GameEngine.buyBackAllReaperItems(prev)
            if (next.reaperVault != prev.reaperVault) changed = true
            next
        }
        if (changed) persistCurrentState(silent = true)
    }

    fun onDiscardReaperItem(itemId: String) {
        var changed = false
        _world.update { prev ->
            val next = GameEngine.discardReaperItem(prev, itemId)
            if (next.reaperVault != prev.reaperVault) changed = true
            next
        }
        if (changed) persistCurrentState(silent = true)
    }

    fun onAdminSetLevel(targetLevel: Int, syncSkills: Boolean = true) {
        var changed = false
        var prevLevel = 1
        var nextLevel = 1
        var targetName = "Admin"
        _world.update { prev ->
            if (!prev.isAdmin) return@update prev
            prevLevel = prev.level
            targetName = prev.playerName
            val next = GameEngine.setAdminLevel(prev, targetLevel, syncSkills)
            nextLevel = next.level
            if (next.level != prev.level || next.skills != prev.skills) changed = true
            next
        }
        if (changed) {
            broadcastCurrentState()
            persistCurrentState(silent = true)
            recordAdminAuditEvent(
                action = AdminAuditAction.LEVEL_CHANGE,
                targetName = targetName,
                summary = "Set level Lv $prevLevel -> Lv $nextLevel",
                details = mapOf(
                    "from" to "Lv $prevLevel",
                    "to" to "Lv $nextLevel",
                    "syncSkills" to syncSkills.toString()
                )
            )
        }
    }

    fun onAdminGrantEquipment(classification: AdminEquipmentClassification) {
        var changed = false
        var targetName = "Admin"
        var equippedWeapon = ""
        _world.update { prev ->
            if (!prev.isAdmin) return@update prev
            targetName = prev.playerName
            val next = GameEngine.grantAdminEquipmentKit(prev, classification)
            equippedWeapon = next.equipment.weaponId.orEmpty()
            if (next.equipment != prev.equipment || next.backpack != prev.backpack) changed = true
            next
        }
        if (changed) {
            broadcastCurrentState()
            persistCurrentState(silent = true)
            recordAdminAuditEvent(
                action = AdminAuditAction.GRANT_GEAR,
                targetName = targetName,
                summary = "Granted Tier 5 ${classification.label} Armory",
                details = buildMap {
                    put("classification", classification.name)
                    if (equippedWeapon.isNotBlank()) put("weapon", equippedWeapon)
                }
            )
        }
    }

    fun onToggleAdminSpectator(enabled: Boolean) {
        var changed = false
        var targetName = "Admin"
        var curFloor = 0
        _world.update { prev ->
            if (!prev.isAdmin) return@update prev
            targetName = prev.playerName
            curFloor = prev.floor
            val next = GameEngine.toggleAdminSpectatorMode(prev, enabled)
            if (next.isSpectator != prev.isSpectator) changed = true
            next
        }
        if (changed) {
            // Immediately trigger Phoenix untrack + player_leave (when entering Spectator)
            // or re-track presence (when leaving Spectator)
            broadcastCurrentState()
            recordAdminAuditEvent(
                action = AdminAuditAction.SPECTATOR_TOGGLE,
                targetName = targetName,
                summary = if (enabled) "Spectator Mode ENABLED (hidden)" else "Spectator Mode DISABLED (visible)",
                details = mapOf(
                    "enabled" to enabled.toString(),
                    "floor" to "F$curFloor"
                )
            )
        }
    }

    fun onAdminSetTimeOfDay(hours: Float, commitAudit: Boolean = false) {
        var isAdminNow = false
        var appliedHours = DEFAULT_TIME_OF_DAY_HOURS
        var targetName = "Admin"
        _world.update { prev ->
            if (!prev.isAdmin) return@update prev
            isAdminNow = true
            targetName = prev.playerName
            val next = GameEngine.setAdminTimeOfDay(prev, hours)
            appliedHours = next.timeOfDayHours
            next
        }
        if (isAdminNow && commitAudit) {
            val clockStr = formatAuditClock(appliedHours)
            recordAdminAuditEvent(
                action = AdminAuditAction.TIME_OF_DAY,
                targetName = targetName,
                summary = "Adjusted local time of day to $clockStr",
                details = mapOf("clock" to clockStr)
            )
        }
    }

    fun onAdminSetWeatherOverride(weather: WeatherType?) {
        _world.update { prev ->
            if (!prev.isAdmin) return@update prev
            val note = if (weather == null) {
                "Admin Weather: Auto (Biome Clock)."
            } else {
                "Admin Weather Override: ${weather.displayName}."
            }
            prev.copy(
                adminWeatherOverride = weather,
                statusLog = (prev.statusLog + note).takeLast(60)
            )
        }
    }

    fun onAdminTeleport(targetFloor: Int, targetX: Int, targetY: Int, destinationLabel: String? = null) {
        val now = SystemClock.uptimeMillis()
        var warped = false
        var floorChanged = false
        var fromFloor = 0
        var fromX = 0
        var fromY = 0
        var toFloor = 0
        var toX = 0
        var toY = 0
        var targetName = "Admin"
        _world.update { prev ->
            if (!prev.isAdmin) return@update prev
            fromFloor = prev.floor
            fromX = prev.playerX
            fromY = prev.playerY
            targetName = prev.playerName
            val next = GameEngine.teleportAdmin(
                state = prev,
                targetFloor = targetFloor,
                targetX = targetX,
                targetY = targetY,
                destinationLabel = destinationLabel,
                nowMs = now
            )
            toFloor = next.floor
            toX = next.playerX
            toY = next.playerY
            if (next.floor != prev.floor || next.playerX != prev.playerX || next.playerY != prev.playerY) {
                warped = true
                floorChanged = next.floor != prev.floor
            }
            next
        }
        if (warped) {
            val cur = _world.value
            if (floorChanged) {
                multiplayerManager.switchFloor(cur.floor, cur.toPresencePayload())
            } else {
                broadcastCurrentState(PlayerActionState.IDLE)
            }
            persistCurrentState(silent = true)
            val label = destinationLabel?.trim()?.takeIf { it.isNotEmpty() } ?: "F$toFloor ($toX, $toY)"
            recordAdminAuditEvent(
                action = AdminAuditAction.TELEPORT,
                targetName = targetName,
                summary = "Teleported to $label",
                details = mapOf(
                    "from" to "F$fromFloor ($fromX,$fromY)",
                    "to" to "F$toFloor ($toX,$toY)",
                    "destination" to label
                )
            )
        }
    }

    fun onSelectAdminAuditFilter(action: AdminAuditAction?) {
        if (!_world.value.isAdmin) return
        _adminAuditState.update { it.copy(selectedFilter = action) }
    }

    fun onRefreshAdminAuditLog() {
        if (!_world.value.isAdmin) return
        val session = activeCloudSession
        _adminAuditState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = adminAuditRepository.refreshFromServer(isAdminFlag = true, session = session)
            if (result != null) {
                _adminAuditState.update { prev ->
                    prev.copy(
                        entries = result.entries,
                        isLoading = false,
                        isSyncing = false,
                        pendingQueueCount = result.pendingQueueCount,
                        lastSyncedAtMs = if (result.syncedFromServer) System.currentTimeMillis() else prev.lastSyncedAtMs,
                        statusMessage = result.statusMessage
                    )
                }
            } else {
                _adminAuditState.update { it.copy(isLoading = false, isSyncing = false) }
            }
        }
    }

    private fun hydrateAndSyncAdminAuditLog() {
        val cached = adminAuditRepository.loadLocalEntries()
        _adminAuditState.value = AdminAuditUiState(
            entries = cached,
            pendingQueueCount = cached.count { it.syncStatus == com.example.game.cloud.AdminAuditSyncStatus.QUEUED }
        )
        onRefreshAdminAuditLog()
    }

    private fun flushAdminAuditQueueIfNeeded() {
        if (!_world.value.isAdmin) return
        val session = activeCloudSession
        viewModelScope.launch {
            val result = adminAuditRepository.flushPendingQueue(isAdminFlag = true, session = session) ?: return@launch
            _adminAuditState.update { prev ->
                prev.copy(
                    entries = result.entries,
                    isSyncing = false,
                    pendingQueueCount = result.pendingQueueCount,
                    lastSyncedAtMs = if (result.syncedFromServer) System.currentTimeMillis() else prev.lastSyncedAtMs,
                    statusMessage = result.statusMessage ?: prev.statusMessage
                )
            }
        }
    }

    private fun recordAdminAuditEvent(
        action: AdminAuditAction,
        targetName: String,
        summary: String,
        details: Map<String, String>
    ) {
        if (!_world.value.isAdmin) return
        val session = activeCloudSession
        _adminAuditState.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            val result = adminAuditRepository.recordEvent(
                isAdminFlag = _world.value.isAdmin,
                session = session,
                action = action,
                targetName = targetName,
                summary = summary,
                details = details
            )
            if (result != null) {
                _adminAuditState.update { prev ->
                    prev.copy(
                        entries = result.entries,
                        isSyncing = false,
                        pendingQueueCount = result.pendingQueueCount,
                        lastSyncedAtMs = if (result.syncedFromServer) System.currentTimeMillis() else prev.lastSyncedAtMs,
                        statusMessage = result.statusMessage ?: prev.statusMessage
                    )
                }
            } else {
                _adminAuditState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private fun formatAuditClock(hours: Float): String {
        val clamped = hours.coerceIn(0f, 24f)
        val totalMinutes = (clamped * 60f).toInt().coerceIn(0, 24 * 60)
        val h = (totalMinutes / 60) % 24
        val m = totalMinutes % 60
        return String.format(java.util.Locale.US, "%02d:%02d", h, m)
    }

    fun dismissDeathReport() {
        _world.update { it.copy(deathReport = null) }
    }

    override fun onCleared() {
        multiplayerManager.disconnect()
        stopGameLoops()
        super.onCleared()
    }
}
