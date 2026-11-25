package com.example.game.cloud

import android.content.Context
import com.example.game.multiplayer.MultiplayerConfig
import com.example.game.world.WorldMaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Strict allowlist of recordable administrative action categories in Rookvale.
 */
enum class AdminAuditAction(val wireName: String, val label: String) {
    LEVEL_CHANGE("LEVEL_CHANGE", "Level"),
    GRANT_GEAR("GRANT_GEAR", "Gear"),
    TELEPORT("TELEPORT", "Teleport"),
    SPECTATOR_TOGGLE("SPECTATOR_TOGGLE", "Spectator"),
    TIME_OF_DAY("TIME_OF_DAY", "Time");

    companion object {
        fun fromWireName(raw: String?): AdminAuditAction? {
            if (raw.isNullOrBlank()) return null
            val norm = raw.trim().uppercase(Locale.US)
            return entries.firstOrNull { it.wireName == norm }
        }
    }
}

enum class AdminAuditSyncStatus {
    SYNCED,
    QUEUED
}

data class AdminAuditEntry(
    val eventId: String,
    val createdAtMs: Long,
    val actionType: AdminAuditAction,
    val actorName: String,
    val targetName: String,
    val summary: String,
    val details: Map<String, String> = emptyMap(),
    val syncStatus: AdminAuditSyncStatus = AdminAuditSyncStatus.QUEUED
)

data class AdminAuditUiState(
    val entries: List<AdminAuditEntry> = emptyList(),
    val selectedFilter: AdminAuditAction? = null,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val pendingQueueCount: Int = 0,
    val lastSyncedAtMs: Long? = null,
    val statusMessage: String? = null
) {
    val filteredEntries: List<AdminAuditEntry>
        get() = if (selectedFilter == null) entries else entries.filter { it.actionType == selectedFilter }
}

data class AdminTeleportPreset(
    val id: String,
    val label: String,
    val floor: Int,
    val x: Int,
    val y: Int,
    val isDungeon: Boolean = false
)

/**
 * Curated overland & dungeon landmark warp targets for the Admin Control Warp tab.
 */
object AdminTeleportCatalog {
    val overlandLandmarks: List<AdminTeleportPreset> = listOf(
        AdminTeleportPreset("rookvale_plaza", "Rookvale Plaza", 0, WorldMaps.TEMPLE_SPAWN_X, WorldMaps.TEMPLE_SPAWN_Y),
        AdminTeleportPreset("reaper_temple", "Reaper Temple", 0, WorldMaps.REAPER_X, WorldMaps.REAPER_Y + 4),
        AdminTeleportPreset("mirewatch", "Mirewatch", 0, 545, 595),
        AdminTeleportPreset("sunspire", "Sunspire Oasis", 0, 375, 790),
        AdminTeleportPreset("sunleaf", "Sunleaf Village", 0, 960, 995),
        AdminTeleportPreset("ember_watch", "Ember Watch", 0, 1458, 952),
        AdminTeleportPreset("grimmaw_camp", "Grimmaw Camp", 0, WorldMaps.ROOKVALE_ORIGIN_X + 88, WorldMaps.ROOKVALE_ORIGIN_Y + 320),
        AdminTeleportPreset("gorek_camp", "Gorek Camp", 0, WorldMaps.ROOKVALE_ORIGIN_X + 312, WorldMaps.ROOKVALE_ORIGIN_Y + 320)
    )

    val dungeonFloors: List<AdminTeleportPreset> = listOf(
        AdminTeleportPreset("floor_m1", "F-1 Sewers", -1, 28, 11, isDungeon = true),
        AdminTeleportPreset("floor_m2", "F-2 Caverns", -2, 24, 37, isDungeon = true),
        AdminTeleportPreset("floor_m3", "F-3 Crypt", -3, 24, 9, isDungeon = true),
        AdminTeleportPreset("floor_m4", "F-4 Wyrm Lair", -4, 24, 39, isDungeon = true),
        AdminTeleportPreset("floor_m5", "F-5 Grimmaw", -5, 24, 40, isDungeon = true),
        AdminTeleportPreset("floor_m6", "F-6 Labyrinth", -6, 24, 40, isDungeon = true)
    )

    val allPresets: List<AdminTeleportPreset> = overlandLandmarks + dungeonFloors
}

/**
 * Sanitizes and bounds all fields written into Admin Audit Log records,
 * and strips any credential-like keys so passcodes, hashes, or API tokens never enter logs.
 */
object AdminAuditSanitizer {
    private val controlCharsRegex = Regex("[\\p{Cntrl}]")
    private val blockedDetailKeys = setOf(
        "pin", "pinhash", "pin_hash", "passcode", "password",
        "apikey", "api_key", "authorization", "token", "secret", "key"
    )

    fun cleanText(raw: String, maxLen: Int): String {
        return raw
            .replace(controlCharsRegex, " ")
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(maxLen)
    }

    fun cleanDetails(raw: Map<String, String>): Map<String, String> {
        if (raw.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for ((k, v) in raw) {
            if (out.size >= 12) break
            val cleanKey = cleanText(k, 32)
            if (cleanKey.isBlank()) continue
            val lowerKey = cleanKey.lowercase(Locale.US)
            if (lowerKey in blockedDetailKeys ||
                lowerKey.contains("pin") ||
                lowerKey.contains("passcode") ||
                lowerKey.contains("secret") ||
                lowerKey.contains("token")
            ) {
                continue
            }
            val cleanVal = cleanText(v, 96)
            if (cleanVal.isNotEmpty()) {
                out[cleanKey] = cleanVal
            }
        }
        return out
    }
}

/**
 * Local append-only SharedPreferences store for offline audit queue entries and cached server history.
 * Strictly supports append/merge/sync-status updates; exposes no user-facing edit or delete operations.
 */
class LocalAdminAuditQueueStore(private val store: CloudKeyValueStore) {
    fun loadEntries(): List<AdminAuditEntry> {
        val raw = store.getString(KEY_AUDIT_ENTRIES) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<AdminAuditEntry>(arr.length().coerceAtMost(MAX_STORED_ENTRIES))
            for (i in 0 until arr.length().coerceAtMost(MAX_STORED_ENTRIES)) {
                val obj = arr.optJSONObject(i) ?: continue
                decodeLocalEntry(obj)?.let { list.add(it) }
            }
            list.sortedByDescending { it.createdAtMs }
        }.getOrDefault(emptyList())
    }

    fun appendEntry(entry: AdminAuditEntry): List<AdminAuditEntry> {
        val merged = mergeAndDeduplicate(existing = loadEntries(), incoming = listOf(entry))
        persistEntries(merged)
        return merged
    }

    fun markSynced(syncedEventIds: Set<String>): List<AdminAuditEntry> {
        if (syncedEventIds.isEmpty()) return loadEntries()
        val updated = loadEntries().map { entry ->
            if (entry.eventId in syncedEventIds) entry.copy(syncStatus = AdminAuditSyncStatus.SYNCED) else entry
        }
        persistEntries(updated)
        return updated
    }

    fun mergeRemoteEntries(remoteSynced: List<AdminAuditEntry>): List<AdminAuditEntry> {
        val current = loadEntries()
        // Keep any still-queued offline items alongside newly fetched remote server items
        val pendingLocal = current.filter { it.syncStatus == AdminAuditSyncStatus.QUEUED }
        val normalizedRemote = remoteSynced.map { it.copy(syncStatus = AdminAuditSyncStatus.SYNCED) }
        val merged = mergeAndDeduplicate(existing = pendingLocal, incoming = normalizedRemote)
        persistEntries(merged)
        return merged
    }

    private fun mergeAndDeduplicate(
        existing: List<AdminAuditEntry>,
        incoming: List<AdminAuditEntry>
    ): List<AdminAuditEntry> {
        val byId = LinkedHashMap<String, AdminAuditEntry>()
        for (entry in existing) {
            byId[entry.eventId] = entry
        }
        for (entry in incoming) {
            val prev = byId[entry.eventId]
            // Prefer SYNCED over QUEUED when deduplicating the same eventId
            if (prev == null || entry.syncStatus == AdminAuditSyncStatus.SYNCED) {
                byId[entry.eventId] = entry
            }
        }
        return byId.values
            .sortedByDescending { it.createdAtMs }
            .take(MAX_STORED_ENTRIES)
    }

    private fun persistEntries(entries: List<AdminAuditEntry>) {
        val arr = JSONArray()
        for (entry in entries.take(MAX_STORED_ENTRIES)) {
            arr.put(encodeLocalEntry(entry))
        }
        store.putValues {
            put(KEY_AUDIT_ENTRIES, arr.toString())
        }
    }

    private fun encodeLocalEntry(entry: AdminAuditEntry): JSONObject {
        val detailsObj = JSONObject()
        entry.details.forEach { (k, v) -> detailsObj.put(k, v) }
        return JSONObject().apply {
            put("event_id", entry.eventId)
            put("created_at_ms", entry.createdAtMs)
            put("action_type", entry.actionType.wireName)
            put("actor_name", entry.actorName)
            put("target_name", entry.targetName)
            put("summary", entry.summary)
            put("details", detailsObj)
            put("sync_status", entry.syncStatus.name)
        }
    }

    private fun decodeLocalEntry(obj: JSONObject): AdminAuditEntry? {
        val eventId = obj.optString("event_id", "").trim()
        if (eventId.isBlank()) return null
        val action = AdminAuditAction.fromWireName(obj.optString("action_type", "")) ?: return null
        val createdAtMs = obj.optLong("created_at_ms", 0L).coerceAtLeast(0L)
        val actor = AdminAuditSanitizer.cleanText(obj.optString("actor_name", "Admin"), 32).ifBlank { "Admin" }
        val target = AdminAuditSanitizer.cleanText(obj.optString("target_name", "Admin"), 32).ifBlank { "Admin" }
        val summary = AdminAuditSanitizer.cleanText(obj.optString("summary", ""), 180)
        if (summary.isBlank()) return null
        val detailsObj = obj.optJSONObject("details")
        val details = buildMap {
            if (detailsObj != null) {
                val keys = detailsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, detailsObj.optString(k, ""))
                }
            }
        }
        val syncStatus = runCatching {
            AdminAuditSyncStatus.valueOf(obj.optString("sync_status", AdminAuditSyncStatus.QUEUED.name))
        }.getOrDefault(AdminAuditSyncStatus.QUEUED)

        return AdminAuditEntry(
            eventId = eventId,
            createdAtMs = createdAtMs,
            actionType = action,
            actorName = actor,
            targetName = target,
            summary = summary,
            details = AdminAuditSanitizer.cleanDetails(details),
            syncStatus = syncStatus
        )
    }

    companion object {
        private const val KEY_AUDIT_ENTRIES = "admin_audit_entries_v1"
        const val MAX_STORED_ENTRIES = 200
    }
}

data class AdminAuditRefreshResult(
    val entries: List<AdminAuditEntry>,
    val syncedFromServer: Boolean,
    val pendingQueueCount: Int,
    val statusMessage: String?
)

/**
 * Append-only Admin Audit Log repository backed by Supabase PostgREST (`public.admin_audit_logs`)
 * with an offline SharedPreferences queue that flushes automatically once connectivity returns.
 *
 * Security invariants:
 * - Strictly append-only: only POST (insert) and GET (select) are ever issued.
 * - Requires verified Admin session credentials when a CloudSession is active.
 * - All PostgREST URLs are strictly validated HTTPS URLs with parameterized query filters.
 * - Credentials, PIN hashes, and API keys are never persisted in audit records.
 */
class AdminAuditLogRepository(
    val localStore: LocalAdminAuditQueueStore,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val urlProvider: () -> String = { MultiplayerConfig.SUPABASE_URL },
    private val anonKeyProvider: () -> String = { MultiplayerConfig.SUPABASE_ANON_KEY },
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    constructor(context: Context) : this(
        localStore = LocalAdminAuditQueueStore(
            SharedPrefsCloudStore(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
        )
    )

    fun isCloudConfigured(): Boolean =
        MultiplayerConfig.restAdminAuditLogsBaseUrl(urlProvider(), anonKeyProvider()) != null

    fun loadLocalEntries(): List<AdminAuditEntry> = localStore.loadEntries()

    fun isAuthorizedAdmin(isAdminFlag: Boolean, session: CloudSession?): Boolean {
        if (!isAdminFlag) return false
        if (session != null && !AdminAccountPolicy.verifyAdminSession(session.characterName, session.pinHash)) {
            return false
        }
        return true
    }

    /**
     * Appends a new Admin Audit Log event locally (QUEUED) and immediately attempts a server-side
     * POST insert into Supabase `public.admin_audit_logs`.
     */
    suspend fun recordEvent(
        isAdminFlag: Boolean,
        session: CloudSession?,
        action: AdminAuditAction,
        targetName: String,
        summary: String,
        details: Map<String, String> = emptyMap(),
        eventId: String = UUID.randomUUID().toString()
    ): AdminAuditRefreshResult? = withContext(Dispatchers.IO) {
        if (!isAuthorizedAdmin(isAdminFlag, session)) return@withContext null

        val cleanSummary = AdminAuditSanitizer.cleanText(summary, 180)
        if (cleanSummary.isBlank()) return@withContext null

        val cleanTarget = AdminAuditSanitizer.cleanText(targetName, 32).ifBlank { AdminAccountPolicy.ADMIN_CANONICAL_NAME }
        val entry = AdminAuditEntry(
            eventId = AdminAuditSanitizer.cleanText(eventId, 64).ifBlank { UUID.randomUUID().toString() },
            createdAtMs = nowProvider(),
            actionType = action,
            actorName = AdminAccountPolicy.ADMIN_CANONICAL_NAME,
            targetName = cleanTarget,
            summary = cleanSummary,
            details = AdminAuditSanitizer.cleanDetails(details),
            syncStatus = AdminAuditSyncStatus.QUEUED
        )

        var entries = localStore.appendEntry(entry)
        val pushed = if (isCloudConfigured()) insertAuditRows(listOf(entry)) else false
        if (pushed) {
            entries = localStore.markSynced(setOf(entry.eventId))
        }
        val pendingCount = entries.count { it.syncStatus == AdminAuditSyncStatus.QUEUED }
        AdminAuditRefreshResult(
            entries = entries,
            syncedFromServer = pushed,
            pendingQueueCount = pendingCount,
            statusMessage = if (pushed) null else if (isCloudConfigured()) "Saved to offline audit queue" else "Offline mode — queued locally"
        )
    }

    /**
     * Flushes all locally QUEUED audit entries to Supabase in chronological order using
     * idempotent insert (`resolution=ignore-duplicates`).
     */
    suspend fun flushPendingQueue(
        isAdminFlag: Boolean,
        session: CloudSession?
    ): AdminAuditRefreshResult? = withContext(Dispatchers.IO) {
        if (!isAuthorizedAdmin(isAdminFlag, session)) return@withContext null
        val current = localStore.loadEntries()
        val pending = current
            .filter { it.syncStatus == AdminAuditSyncStatus.QUEUED }
            .sortedBy { it.createdAtMs }
        if (pending.isEmpty()) {
            return@withContext AdminAuditRefreshResult(
                entries = current,
                syncedFromServer = true,
                pendingQueueCount = 0,
                statusMessage = null
            )
        }
        if (!isCloudConfigured()) {
            return@withContext AdminAuditRefreshResult(
                entries = current,
                syncedFromServer = false,
                pendingQueueCount = pending.size,
                statusMessage = "${pending.size} queued locally (cloud offline)"
            )
        }
        val pushed = insertAuditRows(pending)
        val updated = if (pushed) localStore.markSynced(pending.map { it.eventId }.toSet()) else current
        val remaining = updated.count { it.syncStatus == AdminAuditSyncStatus.QUEUED }
        AdminAuditRefreshResult(
            entries = updated,
            syncedFromServer = pushed,
            pendingQueueCount = remaining,
            statusMessage = if (pushed) "Synced ${pending.size} queued audit event(s)" else "Unable to sync $remaining queued event(s)"
        )
    }

    /**
     * Flushes any pending offline queue entries first, then fetches the newest server-side
     * `admin_audit_logs` rows from Supabase and merges them cleanly with local queued records.
     */
    suspend fun refreshFromServer(
        isAdminFlag: Boolean,
        session: CloudSession?,
        limit: Int = 100
    ): AdminAuditRefreshResult? = withContext(Dispatchers.IO) {
        if (!isAuthorizedAdmin(isAdminFlag, session)) return@withContext null

        // Flush local queued records first so server view stays complete
        flushPendingQueue(isAdminFlag = true, session = session)

        if (!isCloudConfigured()) {
            val local = localStore.loadEntries()
            val pending = local.count { it.syncStatus == AdminAuditSyncStatus.QUEUED }
            return@withContext AdminAuditRefreshResult(
                entries = local,
                syncedFromServer = false,
                pendingQueueCount = pending,
                statusMessage = "Offline mode — showing local audit log"
            )
        }

        val fetched = fetchRemoteRows(limit.coerceIn(1, 200))
        if (fetched != null) {
            val merged = localStore.mergeRemoteEntries(fetched)
            val pending = merged.count { it.syncStatus == AdminAuditSyncStatus.QUEUED }
            AdminAuditRefreshResult(
                entries = merged,
                syncedFromServer = true,
                pendingQueueCount = pending,
                statusMessage = if (pending > 0) "Synced server log ($pending queued)" else "Synced with server"
            )
        } else {
            val local = localStore.loadEntries()
            val pending = local.count { it.syncStatus == AdminAuditSyncStatus.QUEUED }
            AdminAuditRefreshResult(
                entries = local,
                syncedFromServer = false,
                pendingQueueCount = pending,
                statusMessage = "Server unreachable — showing cached log"
            )
        }
    }

    private fun insertAuditRows(entries: List<AdminAuditEntry>): Boolean {
        if (entries.isEmpty()) return true
        val base = MultiplayerConfig.restAdminAuditLogsBaseUrl(urlProvider(), anonKeyProvider()) ?: return false
        val url = base.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("on_conflict", "event_id")
            ?.build()
            ?: return false

        val payload = JSONArray()
        for (entry in entries) {
            val detailsObj = JSONObject()
            entry.details.forEach { (k, v) -> detailsObj.put(k, v) }
            payload.put(JSONObject().apply {
                put("event_id", entry.eventId)
                put("created_at", IsoTimestampUtil.formatUtc(entry.createdAtMs))
                put("action_type", entry.actionType.wireName)
                put("actor_name", entry.actorName)
                put("target_name", entry.targetName)
                put("summary", entry.summary)
                put("details", detailsObj)
            })
        }

        val req = baseRequestBuilder(url.toString())
            .header("Prefer", "resolution=ignore-duplicates,return=minimal")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp -> resp.isSuccessful }
        }.getOrDefault(false)
    }

    private fun fetchRemoteRows(limit: Int): List<AdminAuditEntry>? {
        val base = MultiplayerConfig.restAdminAuditLogsBaseUrl(urlProvider(), anonKeyProvider()) ?: return null
        val url = base.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("select", "event_id,created_at,action_type,actor_name,target_name,summary,details")
            ?.addQueryParameter("order", "created_at.desc")
            ?.addQueryParameter("limit", limit.toString())
            ?.build()
            ?: return null

        val req = baseRequestBuilder(url.toString()).get().build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                val arr = JSONArray(body)
                val out = ArrayList<AdminAuditEntry>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    parseRemoteRow(obj)?.let { out.add(it) }
                }
                out
            }
        }.getOrNull()
    }

    private fun parseRemoteRow(obj: JSONObject): AdminAuditEntry? {
        val eventId = obj.optString("event_id", "").trim()
        if (eventId.isBlank()) return null
        val action = AdminAuditAction.fromWireName(obj.optString("action_type", "")) ?: return null
        val createdAtMs = IsoTimestampUtil.parseUtcMillis(obj.optString("created_at", ""))
        val actor = AdminAuditSanitizer.cleanText(obj.optString("actor_name", "Admin"), 32).ifBlank { "Admin" }
        val target = AdminAuditSanitizer.cleanText(obj.optString("target_name", "Admin"), 32).ifBlank { "Admin" }
        val summary = AdminAuditSanitizer.cleanText(obj.optString("summary", ""), 180)
        if (summary.isBlank()) return null
        val detailsObj = obj.optJSONObject("details")
        val details = buildMap {
            if (detailsObj != null) {
                val keys = detailsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, detailsObj.optString(k, ""))
                }
            }
        }
        return AdminAuditEntry(
            eventId = eventId,
            createdAtMs = createdAtMs,
            actionType = action,
            actorName = actor,
            targetName = target,
            summary = summary,
            details = AdminAuditSanitizer.cleanDetails(details),
            syncStatus = AdminAuditSyncStatus.SYNCED
        )
    }

    private fun baseRequestBuilder(url: String): Request.Builder {
        val key = anonKeyProvider().trim()
        return Request.Builder()
            .url(url)
            .header("apikey", key)
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
    }

    companion object {
        private const val PREFS_NAME = "rookvale_admin_audit_prefs"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
