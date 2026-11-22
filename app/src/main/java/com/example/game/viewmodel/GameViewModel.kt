package com.example.game.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.data.GameSaveRepository
import com.example.game.data.RookvaleDatabase
import com.example.game.data.SaveSlotEntity
import com.example.game.engine.CraftingAndMiningEngine
import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.Direction
import com.example.game.model.EquipmentSlot
import com.example.game.model.GameWorldState
import com.example.game.model.Vocation
import com.example.game.multiplayer.ChatChannel
import com.example.game.multiplayer.ConnectionStatus
import com.example.game.multiplayer.LocalPlayerPresencePayload
import com.example.game.multiplayer.MultiplayerUiState
import com.example.game.multiplayer.RealtimeMultiplayerManager
import com.example.game.sprites.EntitySpriteAtlas
import com.example.game.sprites.TileSpriteAtlas
import com.example.game.update.GitHubUpdateChecker
import com.example.game.update.UpdateCheckState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    TITLE_AND_CREATE,
    PLAYING
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameSaveRepository(RookvaleDatabase.getInstance(application).saveSlotDao())
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

    private val _saveBanner = MutableStateFlow<String?>(null)
    val saveBanner: StateFlow<String?> = _saveBanner.asStateFlow()

    private var worldTickJob: Job? = null
    private var attackSubTickJob: Job? = null
    private var walkLoopJob: Job? = null
    private var lastStepMs: Long = 0L

    init {
        viewModelScope.launch(Dispatchers.Default) {
            TileSpriteAtlas.warmUp()
            EntitySpriteAtlas.warmUp()
        }
        checkForGitHubUpdates()

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
    }

    fun startNewCharacter(name: String, tunicColorIndex: Int = 0, hairColorIndex: Int = 0) {
        val fresh = GameEngine.createNewWorld(
            name = name,
            vocation = Vocation.ADVENTURER,
            tunicColorIndex = tunicColorIndex,
            hairColorIndex = hairColorIndex
        )
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
        _screen.value = AppScreen.TITLE_AND_CREATE
    }

    fun persistCurrentState(silent: Boolean = false) {
        val snapshot = _world.value
        if (snapshot.saveId.isBlank() || _screen.value != AppScreen.PLAYING) return
        viewModelScope.launch {
            repository.saveGame(GameEngine.toSaveData(snapshot))
            if (!silent) {
                _saveBanner.value = "Saved offline"
                delay(1600L)
                _saveBanner.value = null
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
                        next
                    }
                }
                flushOutboundMonsterHits()
                if (leveledUp) {
                    multiplayerManager.broadcastSystemCallout("$name advanced to Level $newLevel!", floor)
                    broadcastCurrentState()
                    persistCurrentState(silent = true)
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
                var floorChanged = false
                var vitalsChanged = false
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
                    if (next.floor != prev.floor) floorChanged = true
                    if (next.hp != prev.hp ||
                        next.maxHp != prev.maxHp ||
                        next.playerX != prev.playerX ||
                        next.playerY != prev.playerY ||
                        (next.poisonTicks > 0) != (prev.poisonTicks > 0) ||
                        (next.fireTicks > 0) != (prev.fireTicks > 0)
                    ) {
                        vitalsChanged = true
                    }
                    curFloor = next.floor
                    name = next.playerName
                    next
                }

                flushOutboundMonsterHits()
                flushOutboundMonsterAttacks()

                val latest = _world.value
                if (floorChanged) {
                    multiplayerManager.switchFloor(latest.floor, latest.toPresencePayload())
                } else if (vitalsChanged) {
                    multiplayerManager.broadcastLocalState(latest.toPresencePayload())
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

                if (diedOrLeveled) persistCurrentState(silent = true)
            }
        }

        // Smooth 210ms auto-walk stepping loop
        walkLoopJob = viewModelScope.launch {
            while (true) {
                delay(210L)
                val now = SystemClock.uptimeMillis()
                var changedFloor = false
                var movedTile = false
                _world.update { prev ->
                    if (prev.walkPath.isEmpty()) prev
                    else {
                        val next = GameEngine.stepAlongPath(prev, now)
                        if (next.floor != prev.floor) changedFloor = true
                        if (next.playerX != prev.playerX || next.playerY != prev.playerY) movedTile = true
                        next
                    }
                }
                if (changedFloor) {
                    val cur = _world.value
                    multiplayerManager.switchFloor(cur.floor, cur.toPresencePayload())
                    persistCurrentState(silent = true)
                } else if (movedTile) {
                    broadcastCurrentState()
                }
            }
        }
    }

    private fun stopGameLoops() {
        attackSubTickJob?.cancel()
        worldTickJob?.cancel()
        walkLoopJob?.cancel()
        attackSubTickJob = null
        worldTickJob = null
        walkLoopJob = null
    }

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

    private fun broadcastCurrentState() {
        val cur = _world.value
        if (cur.saveId.isNotBlank()) {
            multiplayerManager.broadcastLocalState(cur.toPresencePayload())
        }
    }

    private fun GameWorldState.toPresencePayload(): LocalPlayerPresencePayload =
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
            isBurning = fireTicks > 0
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
        var floorChanged = false
        var moved = false
        _world.update { prev ->
            val next = GameEngine.handleTileTap(prev, tx, ty, now)
            if (next.floor != prev.floor) floorChanged = true
            if (next.playerX != prev.playerX || next.playerY != prev.playerY) moved = true
            next
        }
        if (floorChanged) {
            val cur = _world.value
            multiplayerManager.switchFloor(cur.floor, cur.toPresencePayload())
            persistCurrentState(silent = true)
        } else if (moved) {
            broadcastCurrentState()
        }
    }

    fun onDirectionStep(dir: Direction) {
        val now = SystemClock.uptimeMillis()
        if (now - lastStepMs < 155L) return
        lastStepMs = now
        var floorChanged = false
        var moved = false
        _world.update { prev ->
            val next = GameEngine.stepDirection(prev, dir, now)
            if (next.floor != prev.floor) floorChanged = true
            if (next.playerX != prev.playerX || next.playerY != prev.playerY) moved = true
            next
        }
        if (floorChanged) {
            val cur = _world.value
            multiplayerManager.switchFloor(cur.floor, cur.toPresencePayload())
            persistCurrentState(silent = true)
        } else if (moved) {
            broadcastCurrentState()
        }
    }

    fun onHotkeyHealthPotion() {
        val now = SystemClock.uptimeMillis()
        var hpChanged = false
        _world.update { prev ->
            val next = GameEngine.useHealthPotion(prev, now)
            if (next.hp != prev.hp) hpChanged = true
            next
        }
        if (hpChanged) broadcastCurrentState()
    }

    fun onHotkeyManaPotion() {
        val now = SystemClock.uptimeMillis()
        _world.update { GameEngine.useManaPotion(it, now) }
    }

    fun onHotkeyAttackRune() {
        val now = SystemClock.uptimeMillis()
        var leveledUp = false
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
            next
        }
        flushOutboundMonsterHits()
        if (leveledUp) {
            multiplayerManager.broadcastSystemCallout("$name advanced to Level $newLevel!", floor)
            broadcastCurrentState()
            persistCurrentState(silent = true)
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
        _world.update { CraftingAndMiningEngine.executeRecipe(it, recipeId, now) }
        persistCurrentState(silent = true)
    }

    fun useBackpackItem(itemId: String) {
        val now = SystemClock.uptimeMillis()
        var hpChanged = false
        _world.update { prev ->
            val next = GameEngine.useOrEquipBackpackItem(prev, itemId, now)
            if (next.hp != prev.hp) hpChanged = true
            next
        }
        if (hpChanged) broadcastCurrentState()
    }

    fun dropBackpackItem(itemId: String) {
        _world.update { GameEngine.dropItem(it, itemId) }
    }

    fun unequipSlot(slot: EquipmentSlot) {
        _world.update { GameEngine.unequipSlot(it, slot) }
    }

    fun lootAllCorpse(corpseId: String) {
        val now = SystemClock.uptimeMillis()
        _world.update { GameEngine.lootAllFromCorpse(it, corpseId, now) }
        persistCurrentState(silent = true)
    }

    fun depositItem(itemId: String) {
        _world.update { GameEngine.depositItem(it, itemId) }
        persistCurrentState(silent = true)
    }

    fun withdrawItem(itemId: String) {
        _world.update { GameEngine.withdrawItem(it, itemId) }
        persistCurrentState(silent = true)
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
