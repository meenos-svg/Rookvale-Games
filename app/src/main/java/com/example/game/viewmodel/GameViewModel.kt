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
import com.example.game.sprites.EntitySpriteAtlas
import com.example.game.sprites.TileSpriteAtlas
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

    val savedSlots: StateFlow<List<SaveSlotEntity>> = repository.allSaves
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _screen = MutableStateFlow(AppScreen.TITLE_AND_CREATE)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _world = MutableStateFlow(GameWorldState())
    val world: StateFlow<GameWorldState> = _world.asStateFlow()

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
        startGameLoops()
        persistCurrentState(silent = true)
    }

    fun chooseVocationAtOracle(vocation: Vocation) {
        val now = SystemClock.uptimeMillis()
        var promoted = false
        _world.update { prev ->
            val next = GameEngine.promoteVocation(prev, vocation, now)
            if (next.vocation != prev.vocation) promoted = true
            next
        }
        if (promoted) persistCurrentState(silent = true)
    }

    fun loadCharacter(slotId: String) {
        viewModelScope.launch {
            val loaded = repository.loadGame(slotId) ?: return@launch
            _world.value = GameEngine.fromSaveData(loaded)
            _screen.value = AppScreen.PLAYING
            startGameLoops()
        }
    }

    fun deleteSaveSlot(slotId: String) {
        viewModelScope.launch { repository.deleteSave(slotId) }
    }

    fun returnToTitle() {
        persistCurrentState(silent = true)
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
                _world.update { prev ->
                    if (prev.targetMonsterId == null) prev
                    else GameEngine.processPlayerAttackSubTick(prev, now)
                }
            }
        }

        // 1.0s World Tick: monster AI, poison/fire status ticks, ore vein regen & monster respawns
        worldTickJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val now = SystemClock.uptimeMillis()
                var diedOrLeveled = false
                _world.update { prev ->
                    val next = GameEngine.processCombatTick(prev, now)
                    if (next.level != prev.level || (next.deathReport != null && prev.deathReport == null)) {
                        diedOrLeveled = true
                    }
                    next
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
                _world.update { prev ->
                    if (prev.walkPath.isEmpty()) prev
                    else {
                        val next = GameEngine.stepAlongPath(prev, now)
                        if (next.floor != prev.floor) changedFloor = true
                        next
                    }
                }
                if (changedFloor) persistCurrentState(silent = true)
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

    fun onTileTapped(tx: Int, ty: Int) {
        val now = SystemClock.uptimeMillis()
        var floorChanged = false
        _world.update { prev ->
            val next = GameEngine.handleTileTap(prev, tx, ty, now)
            if (next.floor != prev.floor) floorChanged = true
            next
        }
        if (floorChanged) persistCurrentState(silent = true)
    }

    fun onDirectionStep(dir: Direction) {
        val now = SystemClock.uptimeMillis()
        if (now - lastStepMs < 155L) return
        lastStepMs = now
        var floorChanged = false
        _world.update { prev ->
            val next = GameEngine.stepDirection(prev, dir, now)
            if (next.floor != prev.floor) floorChanged = true
            next
        }
        if (floorChanged) persistCurrentState(silent = true)
    }

    fun onHotkeyHealthPotion() {
        val now = SystemClock.uptimeMillis()
        _world.update { GameEngine.useHealthPotion(it, now) }
    }

    fun onHotkeyManaPotion() {
        val now = SystemClock.uptimeMillis()
        _world.update { GameEngine.useManaPotion(it, now) }
    }

    fun onHotkeyAttackRune() {
        val now = SystemClock.uptimeMillis()
        _world.update { GameEngine.useAttackRune(it, now) }
    }

    fun onHotkeyRope() {
        val now = SystemClock.uptimeMillis()
        var changed = false
        _world.update { prev ->
            val next = GameEngine.useRope(prev, now)
            if (next.floor != prev.floor) changed = true
            next
        }
        if (changed) persistCurrentState(silent = true)
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
        _world.update { GameEngine.useOrEquipBackpackItem(it, itemId, now) }
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
        stopGameLoops()
        super.onCleared()
    }
}
