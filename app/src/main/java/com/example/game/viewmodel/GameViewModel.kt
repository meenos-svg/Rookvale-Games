package com.example.game.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.cloud.CloudAuthResult
import com.example.game.cloud.CloudAuthUiState
import com.example.game.cloud.CloudSaveOutcome
import com.example.game.cloud.CloudSavePayloadCodec
import com.example.game.cloud.CloudSession
import com.example.game.cloud.SupabaseCloudSaveRepository
import com.example.game.data.GameSaveRepository
import com.example.game.data.RookvaleDatabase
import com.example.game.data.SaveSlotEntity
import com.example.game.engine.CraftingAndMiningEngine
import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.Direction
import com.example.game.model.EquipmentSlot
import com.example.game.model.GameWorldState
import com.example.game.model.PlayerSkills
import com.example.game.model.Vocation
import com.example.game.multiplayer.ChatChannel
import com.example.game.multiplayer.ConnectionStatus
import com.example.game.multiplayer.LocalPlayerPresencePayload
import com.example.game.multiplayer.MultiplayerUiState
import com.example.game.multiplayer.PlayerActionState
import com.example.game.multiplayer.RealtimeMultiplayerManager
import com.example.game.sprites.EntitySpriteAtlas
import com.example.game.sprites.TileSpriteAtlas
import com.example.game.update.GitHubUpdateChecker
import com.example.game.update.UpdateCheckState
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

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameSaveRepository(RookvaleDatabase.getInstance(application).saveSlotDao())
    private val cloudRepository = SupabaseCloudSaveRepository(application)
    private val multiplayerManager = RealtimeMultiplayerManager(
        scope = viewModelScope,
        nowProvider = { System.currentTimeMillis() },
        animClockProvider = { SystemClock.uptimeMillis() }
    )

    val savedSlots: StateFlow<List<SaveSlotEntity>> = repository.allSaves
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _screen = MutableStateFlow(AppScreen.TITLE_AND_CREATE)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _world = MutableStateFlow(GameWorldState())
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

    private var activeCloudSession: CloudSession? = null
    private val persistMutex = Mutex()
    private var worldTickJob: Job? = null
    private var attackSubTickJob: Job? = null
    private var walkLoopJob: Job? = null
    private var cloudAutoSaveJob: Job? = null
    private var skillDebounceSaveJob: Job? = null
    private var lastStepMs: Long = 0L
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
                var newLevel = 1
                var playerName = ""
                _world.update { prev ->
                    val next = GameEngine.applyRemoteMonsterSync(prev, floor, snapshots, prev.saveId, now)
                    if (next.level > prev.level) {
                        leveledUp = true
                        newLevel = next.level
                        playerName = next.playerName
                    }
                    next
                }
                if (leveledUp) {
                    multiplayerManager.broadcastSystemCallout("$playerName advanced to Level $newLevel!", floor)
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
                    next
                }
                if (leveledUp) {
                    multiplayerManager.broadcastSystemCallout("$playerName advanced to Level $newLevel!", curFloor)
                    broadcastCurrentState()
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
            val result = cloudRepository.loginAndRecover(name, pin)
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
            statusLog = (hydrated.statusLog + result.notice).takeLast(6)
        )
        // Keep Room local slot list in sync as well
        repository.saveGame(result.saveData)
        _world.value = withNotice
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
        multiplayerManager.connect(withNotice.toPresencePayload())
        startGameLoops()
    }

    fun startNewCharacter(name: String, tunicColorIndex: Int = 0, hairColorIndex: Int = 0) {
        val fresh = GameEngine.createNewWorld(
            name = name,
            vocation = Vocation.ADVENTURER,
            tunicColorIndex = tunicColorIndex,
            hairColorIndex = hairColorIndex
        )
        activeCloudSession = null
        _world.value = fresh
        _screen.value = AppScreen.PLAYING
        multiplayerManager.connect(fresh.toPresencePayload())
        startGameLoops()
        persistCurrentState(silent = true)
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
            val state = GameEngine.fromSaveData(loaded)
            val backup = cloudRepository.localStore.getLocalBackupByPlayerId(slotId)
            activeCloudSession = backup?.session
            if (backup != null) {
                cloudRepository.localStore.saveCachedSession(backup.session)
                _cloudAuthState.update { it.copy(cachedSession = backup.session) }
            }
            _world.value = state
            _screen.value = AppScreen.PLAYING
            multiplayerManager.connect(state.toPresencePayload())
            startGameLoops()
        }
    }

    fun deleteSaveSlot(slotId: String) {
        viewModelScope.launch { repository.deleteSave(slotId) }
    }

    fun returnToTitle() {
        persistCurrentState(silent = true)
        multiplayerManager.disconnect()
        stopGameLoops()
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
        persistCurrentState(silent = false, logConsoleFeedback = true)
    }

    fun onAppBackgrounded() {
        val snapshot = _world.value
        if (snapshot.saveId.isBlank() || _screen.value != AppScreen.PLAYING) return
        val now = SystemClock.uptimeMillis()
        val saveData = GameEngine.toSaveData(snapshot)
        // Snapshot save_data JSON synchronously on the main thread before any background IO
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
        viewModelScope.launch {
            withContext(NonCancellable) {
                persistMutex.withLock {
                    runCatching { repository.saveGame(saveData) }
                    if (session != null) {
                        runCatching {
                            cloudRepository.saveProgress(
                                session = session,
                                saveData = saveData,
                                preEncodedSaveJson = preEncodedJson
                            )
                        }
                    }
                }
            }
        }
    }

    fun persistCurrentState(silent: Boolean = false, logConsoleFeedback: Boolean = false) {
        val snapshot = _world.value
        if (snapshot.saveId.isBlank() || _screen.value != AppScreen.PLAYING) return
        // Snapshot immutable save data + encoded JSON synchronously on the main thread
        // before launching the background coroutine to avoid races during rapid depot taps.
        val saveData = GameEngine.toSaveData(snapshot)
        val preEncodedJson = runCatching { CloudSavePayloadCodec.encode(saveData) }.getOrNull()
        val session = activeCloudSession
        viewModelScope.launch {
            val outcome = persistMutex.withLock {
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
        skillDebounceSaveJob?.cancel()
        skillDebounceSaveJob = viewModelScope.launch {
            delay(3000L)
            persistCurrentState(silent = true)
        }
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
        }
    }

    private fun startGameLoops() {
        stopGameLoops()

        // 200ms combat sub-tick so variable weapon speeds (0.8s fast vs 1.0s balanced vs 1.4s heavy) feel snappy
        attackSubTickJob = viewModelScope.launch {
            while (true) {
                delay(200L)
                val now = SystemClock.uptimeMillis()
                var leveledUp = false
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
                    if (skillLevelUp) {
                        persistCurrentState(silent = true)
                    } else if (skillPointsGained) {
                        scheduleDebouncedSkillSave()
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
                    multiplayerManager.broadcastMonsterSync(
                        floor = latest.floor,
                        monsters = GameEngine.buildFloorMonsterSnapshots(latest)
                    )
                }

                if (diedOrLeveled || floorChanged || skillLevelUp) {
                    persistCurrentState(silent = true)
                } else if (skillPointsGained) {
                    scheduleDebouncedSkillSave()
                }
            }
        }

        // Smooth 210ms auto-walk & melee Target Auto-Follow stepping loop
        walkLoopJob = viewModelScope.launch {
            while (true) {
                delay(210L)
                val now = SystemClock.uptimeMillis()
                val remotes = multiplayerManager.uiState.value.remotePlayers
                var changedFloor = false
                var movedTile = false
                var facingChanged = false
                _world.update { prev ->
                    if (prev.walkPath.isEmpty() && !prev.autoFollowTarget) prev
                    else {
                        val next = GameEngine.stepAutoFollowOrPath(prev, now, remotes)
                        if (next.floor != prev.floor) changedFloor = true
                        if (next.playerX != prev.playerX || next.playerY != prev.playerY) movedTile = true
                        if (next.playerFacing != prev.playerFacing) facingChanged = true
                        next
                    }
                }
                if (changedFloor) {
                    val cur = _world.value
                    multiplayerManager.switchFloor(cur.floor, cur.toPresencePayload())
                    persistCurrentState(silent = true)
                } else if (movedTile) {
                    broadcastCurrentState(PlayerActionState.WALKING)
                } else if (facingChanged) {
                    broadcastCurrentState(PlayerActionState.IDLE)
                }
            }
        }

        // Periodic 60-second cloud autosave while playing
        cloudAutoSaveJob = viewModelScope.launch {
            while (true) {
                delay(60_000L)
                if (_screen.value == AppScreen.PLAYING) {
                    persistCurrentState(silent = true)
                }
            }
        }
    }

    private fun stopGameLoops() {
        attackSubTickJob?.cancel()
        worldTickJob?.cancel()
        walkLoopJob?.cancel()
        cloudAutoSaveJob?.cancel()
        skillDebounceSaveJob?.cancel()
        attackSubTickJob = null
        worldTickJob = null
        walkLoopJob = null
        cloudAutoSaveJob = null
        skillDebounceSaveJob = null
    }

    private fun totalSkillLevels(skills: PlayerSkills): Int =
        skills.melee.level + skills.fist.level + skills.club.level + skills.sword.level + skills.axe.level +
            skills.distance.level + skills.shielding.level +
            skills.magic.level + skills.fishing.level + skills.mining.level

    private fun flushOutboundMonsterHits() {
        val pending = _world.value.outboundMonsterHits
        if (pending.isEmpty()) return
        val snapshot = _world.value
        _world.update { it.copy(outboundMonsterHits = emptyList()) }
        for (hit in pending) {
            multiplayerManager.broadcastMonsterHit(
                hit = hit,
                attackerId = snapshot.saveId,
                attackerName = snapshot.playerName
            )
            if (hit.resultingHp <= 0 && hit.isBoss) {
                multiplayerManager.broadcastSystemCallout(
                    "${snapshot.playerName} helped slay the ${hit.monsterTypeName}!",
                    hit.floor
                )
            }
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

    private fun broadcastCurrentState(actionState: PlayerActionState = PlayerActionState.IDLE) {
        val cur = _world.value
        if (cur.saveId.isNotBlank()) {
            multiplayerManager.broadcastLocalState(cur.toPresencePayload(actionState))
        }
    }

    private fun GameWorldState.toPresencePayload(
        actionState: PlayerActionState = PlayerActionState.IDLE
    ): LocalPlayerPresencePayload =
        LocalPlayerPresencePayload(
            playerId = saveId,
            name = playerName,
            level = level,
            vocation = vocation,
            tunicColorIndex = tunicColorIndex,
            hairColorIndex = hairColorIndex,
            x = playerX,
            y = playerY,
            floor = floor,
            hp = hp,
            maxHp = maxHp,
            joinedAt = System.currentTimeMillis(),
            isPoisoned = poisonTicks > 0,
            isBurning = fireTicks > 0,
            direction = playerFacing,
            actionState = actionState
        )

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
        val msg = when (result) {
            RealtimeMultiplayerManager.RetryResult.ALREADY_ONLINE ->
                "Online on Floor ${cur.floor} (${multiplayerManager.uiState.value.floorOnlineCount} hero(es) present)."
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
            floor = cur.floor
        )
    }

    fun onTileTapped(tx: Int, ty: Int) {
        val now = SystemClock.uptimeMillis()
        val remotes = multiplayerManager.uiState.value.remotePlayers
        var floorChanged = false
        var moved = false
        var facingChanged = false
        var healed = false
        var skillChanged = false
        var skillLevelUp = false
        var inventoryChanged = false
        _world.update { prev ->
            val next = GameEngine.handleTileTap(prev, tx, ty, now, remotes)
            if (next.floor != prev.floor) floorChanged = true
            if (next.playerX != prev.playerX || next.playerY != prev.playerY) moved = true
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
            if (moved) {
                broadcastCurrentState(PlayerActionState.WALKING)
            } else if (healed) {
                broadcastCurrentState(PlayerActionState.HEALING)
            } else if (facingChanged) {
                broadcastCurrentState(PlayerActionState.IDLE)
            }
            if (skillLevelUp) persistCurrentState(silent = true)
            else if (skillChanged || inventoryChanged) scheduleDebouncedSkillSave()
        }
    }

    fun onDirectionStep(dir: Direction) {
        val now = SystemClock.uptimeMillis()
        if (now - lastStepMs < 155L) return
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
            broadcastCurrentState(PlayerActionState.WALKING)
        } else if (facingChanged) {
            broadcastCurrentState(PlayerActionState.IDLE)
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
                inspectedPlayerUntilMs = now + 4500L,
                statusLog = (prev.statusLog + "Ally ${ally.name} (Lvl ${ally.level} ${ally.vocation.displayName}) at (${ally.x}, ${ally.y}).").takeLast(6)
            )
        }
    }

    fun onWalkToTile(x: Int, y: Int) {
        _world.update { prev -> GameEngine.walkToCoordinate(prev, x, y) }
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

    fun toggleMinimap() {
        _world.update { it.copy(showMinimap = !it.showMinimap) }
    }

    fun openPanel(panel: ActivePanel) {
        _world.update {
            it.copy(
                activePanel = if (it.activePanel == panel) ActivePanel.NONE else panel,
                inspectingCorpseId = if (panel != ActivePanel.LOOT_CORPSE) null else it.inspectingCorpseId
            )
        }
    }

    fun closePanel() {
        _world.update { it.copy(activePanel = ActivePanel.NONE, inspectingCorpseId = null) }
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
        _world.update { prev ->
            val next = GameEngine.useOrEquipBackpackItem(prev, itemId, now)
            if (next.hp != prev.hp) hpChanged = true
            if (next.equipment != prev.equipment) equipChanged = true
            if (next.floor != prev.floor) floorChanged = true
            if (next.backpack != prev.backpack) bagChanged = true
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
            if (hpChanged || action != null) broadcastCurrentState(action ?: PlayerActionState.IDLE)
            if (equipChanged || skillLevelUp) persistCurrentState(silent = true)
            else if (bagChanged) scheduleDebouncedSkillSave()
        }
    }

    fun dropBackpackItem(itemId: String) {
        _world.update { GameEngine.dropItem(it, itemId) }
        persistCurrentState(silent = true)
    }

    fun unequipSlot(slot: EquipmentSlot) {
        var changed = false
        _world.update { prev ->
            val next = GameEngine.unequipSlot(prev, slot)
            if (next.equipment != prev.equipment) changed = true
            next
        }
        if (changed) persistCurrentState(silent = true)
    }

    fun lootCorpseItem(corpseId: String, stackIndex: Int) {
        val now = SystemClock.uptimeMillis()
        var looted = false
        _world.update { prev ->
            val next = GameEngine.lootItemFromCorpse(prev, corpseId, stackIndex, now)
            if (next.backpack != prev.backpack || next.toolBag != prev.toolBag || next.corpses != prev.corpses) {
                looted = true
            }
            next
        }
        if (looted) persistCurrentState(silent = true)
    }

    fun lootCorpseGold(corpseId: String) {
        val now = SystemClock.uptimeMillis()
        var looted = false
        _world.update { prev ->
            val next = GameEngine.lootGoldFromCorpse(prev, corpseId, now)
            if (next.gold != prev.gold || next.corpses != prev.corpses) {
                looted = true
            }
            next
        }
        if (looted) persistCurrentState(silent = true)
    }

    fun lootAllCorpse(corpseId: String) {
        val now = SystemClock.uptimeMillis()
        var looted = false
        _world.update { prev ->
            val next = GameEngine.lootAllFromCorpse(prev, corpseId, now)
            if (next.gold != prev.gold || next.backpack != prev.backpack || next.toolBag != prev.toolBag || next.corpses != prev.corpses) {
                looted = true
            }
            next
        }
        if (looted) persistCurrentState(silent = true)
    }

    fun depositItem(itemId: String) {
        if (itemId.isBlank()) return
        var changed = false
        _world.update { prev ->
            val next = GameEngine.depositItem(prev, itemId)
            if (next.depot != prev.depot || next.backpack != prev.backpack || next.toolBag != prev.toolBag) {
                changed = true
            }
            next
        }
        if (changed) persistCurrentState(silent = true)
    }

    fun withdrawItem(itemId: String) {
        if (itemId.isBlank()) return
        var changed = false
        _world.update { prev ->
            val next = GameEngine.withdrawItem(prev, itemId)
            if (next.depot != prev.depot || next.backpack != prev.backpack || next.toolBag != prev.toolBag) {
                changed = true
            }
            next
        }
        if (changed) persistCurrentState(silent = true)
    }

    fun buyItem(itemId: String) {
        _world.update { GameEngine.buyShopItem(it, itemId) }
        persistCurrentState(silent = true)
    }

    fun sellItem(itemId: String) {
        _world.update { GameEngine.sellBackpackItem(it, itemId) }
        persistCurrentState(silent = true)
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
