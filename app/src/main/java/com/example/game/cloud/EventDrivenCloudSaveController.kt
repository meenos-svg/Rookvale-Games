package com.example.game.cloud

import com.example.game.model.CharacterLocationSnapshot
import com.example.game.model.GameWorldState
import com.example.game.model.PersistentCharacterFingerprint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Event-driven, coalescing cloud save controller enforcing a minimum debounce window
 * (default 5 seconds) so rapid permanent-data mutations (combat XP, looting, crafting,
 * equipping, banking, floor/region transitions) collapse into a single Postgres upsert,
 * while routine walking steps, monster attacks, regen ticks, and respawns trigger ZERO
 * database writes.
 */
class EventDrivenCloudSaveController(
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val stateProvider: () -> GameWorldState,
    private val onFlush: (snapshot: GameWorldState, logConsoleFeedback: Boolean) -> Unit
) {
    companion object {
        const val DEFAULT_DEBOUNCE_MS = 5_000L
    }

    private var lastSyncedFingerprint: PersistentCharacterFingerprint? = null
    private var lastSyncedLocation: CharacterLocationSnapshot? = null
    private var pendingDebounceJob: Job? = null

    /**
     * Records the baseline permanent character fingerprint & location right after login,
     * character creation, or load so an unmodified session does not trigger an immediate write.
     */
    fun markSynced(state: GameWorldState) {
        lastSyncedFingerprint = state.persistentSaveFingerprint()
        lastSyncedLocation = state.locationSnapshot()
    }

    /**
     * Clears pending timers and baseline snapshots when returning to the title screen.
     */
    fun reset() {
        pendingDebounceJob?.cancel()
        pendingDebounceJob = null
        lastSyncedFingerprint = null
        lastSyncedLocation = null
    }

    /**
     * Checks whether permanent character state changed relative to the last synced snapshot.
     * Routine walking steps (x/y on the same floor/region), monster AI, combat damage ticks,
     * and respawns produce an identical fingerprint and return false without scheduling a write.
     */
    fun onStateMutated(state: GameWorldState = stateProvider()): Boolean {
        if (state.saveId.isBlank()) return false
        val fp = state.persistentSaveFingerprint()
        if (fp == lastSyncedFingerprint) return false

        // Coalesce into a single trailing-window flush (at most 1 upsert per debounceMs)
        if (pendingDebounceJob?.isActive == true) {
            return true
        }
        pendingDebounceJob = scope.launch {
            delay(debounceMs)
            pendingDebounceJob = null
            val latest = stateProvider()
            if (latest.saveId.isBlank()) return@launch
            val latestFp = latest.persistentSaveFingerprint()
            if (latestFp != lastSyncedFingerprint) {
                lastSyncedFingerprint = latestFp
                lastSyncedLocation = latest.locationSnapshot()
                onFlush(latest, false)
            }
        }
        return true
    }

    /**
     * Flushes immediately on app pause, title-screen exit, or manual Save tap.
     * For lifecycle pauses/exits (`force = false`), only writes when either permanent character
     * state OR character coordinates changed since the last synced snapshot.
     */
    fun flushNow(
        state: GameWorldState = stateProvider(),
        force: Boolean = false,
        logConsoleFeedback: Boolean = false
    ): Boolean {
        if (state.saveId.isBlank()) return false
        pendingDebounceJob?.cancel()
        pendingDebounceJob = null

        val fp = state.persistentSaveFingerprint()
        val loc = state.locationSnapshot()
        val needsWrite = force || fp != lastSyncedFingerprint || loc != lastSyncedLocation
        if (!needsWrite) return false

        lastSyncedFingerprint = fp
        lastSyncedLocation = loc
        onFlush(state, logConsoleFeedback)
        return true
    }

    fun hasPendingDebouncedSave(): Boolean = pendingDebounceJob?.isActive == true
}
