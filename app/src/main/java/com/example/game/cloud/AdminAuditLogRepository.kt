package com.example.game.cloud

import android.content.Context
import com.example.game.multiplayer.MultiplayerConfig
import com.example.game.world.BiomeProgressionRules
import com.example.game.world.DeepDelveFloors
import com.example.game.world.FrontierSiteFloors
import com.example.game.world.HeartlandSiteFloors
import com.example.game.world.OverlandBiomeTier
import com.example.game.world.WorldBiomeBlueprint
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

enum class AdminWarpCategory(val label: String, val shortLabel: String) {
    TOWNS_EMPIRES("Towns & Empires", "Towns"),
    OVERLAND_CAMPS("Overland Camps", "Camps"),
    DUNGEON_CAMPS("Dungeon Camps", "Dungeons")
}

data class AdminTeleportPreset(
    val id: String,
    val label: String,
    val floor: Int,
    val x: Int,
    val y: Int,
    val isDungeon: Boolean = false,
    val category: AdminWarpCategory = if (isDungeon) AdminWarpCategory.DUNGEON_CAMPS else AdminWarpCategory.TOWNS_EMPIRES,
    val levelRangeLabel: String = "Lv 1-20",
    val biomeTier: OverlandBiomeTier = OverlandBiomeTier.ROOKVALE_HEARTLAND
)

data class AdminWarpZone(
    val tier: OverlandBiomeTier,
    val towns: List<AdminTeleportPreset>,
    val camps: List<AdminTeleportPreset>,
    val dungeons: List<AdminTeleportPreset>
) {
    val allPresets: List<AdminTeleportPreset> get() = towns + camps + dungeons
    val title: String get() = tier.displayName
    val levelLabel: String get() = tier.levelRangeLabel.replace("Lvl ", "Lv ")
}

/**
 * Curated overland & dungeon landmark warp targets for the Admin Control Warp tab,
 * organized into Towns & Empires, Overland Forward/Frontier Camps, and Dungeon Camps.
 */
object AdminTeleportCatalog {

    private fun compactLevelLabel(tier: OverlandBiomeTier): String =
        tier.levelRangeLabel.replace("Lvl ", "Lv ")

    private fun settlementPreset(
        id: String,
        settlementId: String,
        label: String,
        category: AdminWarpCategory,
        tier: OverlandBiomeTier,
        fallbackX: Int,
        fallbackY: Int
    ): AdminTeleportPreset {
        val zone = WorldBiomeBlueprint.secondarySettlements.firstOrNull { it.id == settlementId }
        return AdminTeleportPreset(
            id = id,
            label = label,
            floor = 0,
            x = zone?.centerX ?: fallbackX,
            y = zone?.centerY ?: fallbackY,
            isDungeon = false,
            category = category,
            levelRangeLabel = compactLevelLabel(tier),
            biomeTier = tier
        )
    }

    private fun dungeonCampPreset(
        id: String,
        label: String,
        floor: Int,
        fallbackX: Int,
        fallbackY: Int,
        openEndedTop: Boolean = false
    ): AdminTeleportPreset {
        val camp = WorldMaps.undergroundCampForFloor(floor)
        val tier = BiomeProgressionRules.surfaceBiomeForDungeonFloor(floor)
        val baseLevel = BiomeProgressionRules.dungeonBaseLevel(tier.minLevel, floor)
        // Rookvale's dungeons never go past the zone cap (Lv 20)
        val topLevel = if (tier == OverlandBiomeTier.ROOKVALE_HEARTLAND) {
            (baseLevel + 5).coerceAtMost(tier.maxLevel)
        } else {
            baseLevel + 5
        }
        val levelLabel = if (openEndedTop) {
            "Lv ${baseLevel}+ • PZ"
        } else {
            "Lv ${baseLevel}-${topLevel} • PZ"
        }
        val targetX = camp?.let { (it.minX + it.maxX) / 2 } ?: fallbackX
        val targetY = camp?.let { ((it.minY + it.maxY) / 2).coerceIn(it.minY, it.maxY) } ?: fallbackY
        // Keep exact legacy F-1..F-6 safe-zone landing offsets inside each underground camp PZ
        val finalX = if (camp != null && fallbackX in camp.minX..camp.maxX) fallbackX else targetX
        val finalY = if (camp != null && fallbackY in camp.minY..camp.maxY) fallbackY else targetY
        return AdminTeleportPreset(
            id = id,
            label = label,
            floor = floor,
            x = finalX,
            y = finalY,
            isDungeon = true,
            category = AdminWarpCategory.DUNGEON_CAMPS,
            levelRangeLabel = levelLabel,
            biomeTier = tier
        )
    }

    val townsAndEmpires: List<AdminTeleportPreset> = listOf(
        AdminTeleportPreset(
            id = "rookvale_plaza",
            label = "Rookvale Plaza",
            floor = 0,
            x = WorldMaps.TEMPLE_SPAWN_X,
            y = WorldMaps.TEMPLE_SPAWN_Y,
            category = AdminWarpCategory.TOWNS_EMPIRES,
            levelRangeLabel = compactLevelLabel(OverlandBiomeTier.ROOKVALE_HEARTLAND),
            biomeTier = OverlandBiomeTier.ROOKVALE_HEARTLAND
        ),
        AdminTeleportPreset(
            id = "reaper_temple",
            label = "Reaper Temple",
            floor = 0,
            x = WorldMaps.REAPER_X,
            y = WorldMaps.REAPER_Y + 4,
            category = AdminWarpCategory.TOWNS_EMPIRES,
            levelRangeLabel = compactLevelLabel(OverlandBiomeTier.ROOKVALE_HEARTLAND),
            biomeTier = OverlandBiomeTier.ROOKVALE_HEARTLAND
        ),
        settlementPreset(
            id = "mirewatch",
            settlementId = "mirewatch_fortress",
            label = "Mirewatch Citadel",
            category = AdminWarpCategory.TOWNS_EMPIRES,
            tier = OverlandBiomeTier.WHISPERING_MARSH,
            fallbackX = 545,
            fallbackY = 595
        ),
        settlementPreset(
            id = "sunleaf",
            settlementId = "sunleaf_village",
            label = "Sunleaf Village",
            category = AdminWarpCategory.TOWNS_EMPIRES,
            tier = OverlandBiomeTier.WHISPERING_MARSH,
            fallbackX = 960,
            fallbackY = 995
        ),
        settlementPreset(
            id = "sunspire",
            settlementId = "sunspire_oasis",
            label = "Sunspire Empire",
            category = AdminWarpCategory.TOWNS_EMPIRES,
            tier = OverlandBiomeTier.SUNSCORCH_BADLANDS,
            fallbackX = 375,
            fallbackY = 790
        ),
        settlementPreset(
            id = "frostpeak_citadel",
            settlementId = "frostpeak_citadel",
            label = "Frostpeak Citadel",
            category = AdminWarpCategory.TOWNS_EMPIRES,
            tier = OverlandBiomeTier.FROSTPEAK_HIGHLANDS,
            fallbackX = 1396,
            fallbackY = 204
        ),
        settlementPreset(
            id = "ashen_bastion",
            settlementId = "ashen_bastion",
            label = "Ashen Bastion",
            category = AdminWarpCategory.TOWNS_EMPIRES,
            tier = OverlandBiomeTier.ASHEN_CALDERA,
            fallbackX = 386,
            fallbackY = 260
        ),
        settlementPreset(
            id = "duskport_town",
            settlementId = "duskport_town",
            label = "Duskport Keep",
            category = AdminWarpCategory.TOWNS_EMPIRES,
            tier = OverlandBiomeTier.ASHEN_CALDERA,
            fallbackX = 904,
            fallbackY = 250
        ),
        settlementPreset(
            id = "ember_watch",
            settlementId = "ember_outpost",
            label = "Ember Watch",
            category = AdminWarpCategory.TOWNS_EMPIRES,
            tier = OverlandBiomeTier.ASHEN_CALDERA,
            fallbackX = 1462,
            fallbackY = 952
        )
    )

    val overlandCamps: List<AdminTeleportPreset> = listOf(
        settlementPreset(
            id = "spirewood_crossing",
            settlementId = "spirewood_crossing",
            label = "Spirewood Camp",
            category = AdminWarpCategory.OVERLAND_CAMPS,
            tier = OverlandBiomeTier.ROOKVALE_HEARTLAND,
            fallbackX = 1042,
            fallbackY = 592
        ),
        settlementPreset(
            id = "boghollow_camp",
            settlementId = "boghollow_camp",
            label = "Boghollow Camp",
            category = AdminWarpCategory.OVERLAND_CAMPS,
            tier = OverlandBiomeTier.WHISPERING_MARSH,
            fallbackX = 358,
            fallbackY = 560
        ),
        settlementPreset(
            id = "scorchwind_camp",
            settlementId = "scorchwind_camp",
            label = "Scorchwind Camp",
            category = AdminWarpCategory.OVERLAND_CAMPS,
            tier = OverlandBiomeTier.SUNSCORCH_BADLANDS,
            fallbackX = 315,
            fallbackY = 912
        ),
        settlementPreset(
            id = "boreal_watch_camp",
            settlementId = "boreal_watch_camp",
            label = "Boreal Watch Camp",
            category = AdminWarpCategory.OVERLAND_CAMPS,
            tier = OverlandBiomeTier.FROSTPEAK_HIGHLANDS,
            fallbackX = 1268,
            fallbackY = 258
        ),
        AdminTeleportPreset(
            id = "grimmaw_camp",
            label = "Grimmaw Ridge",
            floor = 0,
            x = 1320,
            y = 261,
            category = AdminWarpCategory.OVERLAND_CAMPS,
            levelRangeLabel = compactLevelLabel(OverlandBiomeTier.FROSTPEAK_HIGHLANDS),
            biomeTier = OverlandBiomeTier.FROSTPEAK_HIGHLANDS
        ),
        AdminTeleportPreset(
            id = "gorek_camp",
            label = "Gorek Gate",
            floor = 0,
            x = 242,
            y = 1021,
            category = AdminWarpCategory.OVERLAND_CAMPS,
            levelRangeLabel = compactLevelLabel(OverlandBiomeTier.SUNSCORCH_BADLANDS),
            biomeTier = OverlandBiomeTier.SUNSCORCH_BADLANDS
        )
    )

    val dungeonCamps: List<AdminTeleportPreset> = listOf(
        dungeonCampPreset("floor_m1", "F-1 Briar Catacombs", -1, DeepDelveFloors.F1_LANDING_X, DeepDelveFloors.F1_LANDING_Y),
        dungeonCampPreset("floor_m2", "F-2 Watchwood Caverns", -2, DeepDelveFloors.F2_LANDING_X, DeepDelveFloors.F2_LANDING_Y),
        dungeonCampPreset("floor_m3", "F-3 Abyssal Crypt", -3, DeepDelveFloors.F3_LANDING_X, DeepDelveFloors.F3_LANDING_Y),
        dungeonCampPreset("floor_m4", "F-4 Obsidian Wyrm Sanctum", -4, DeepDelveFloors.F4_LANDING_X, DeepDelveFloors.F4_LANDING_Y),
        dungeonCampPreset("floor_m5", "F-5 Grimmaw Camp", -5, 24, 40),
        dungeonCampPreset("floor_m6", "F-6 Labyrinth Camp", -6, FrontierSiteFloors.GOREK.landingX, FrontierSiteFloors.GOREK.landingY),
        dungeonCampPreset("floor_m7", "F-7 Mire Vaults Camp", -7, FrontierSiteFloors.MIRE.landingX, FrontierSiteFloors.MIRE.landingY),
        dungeonCampPreset("floor_m8", "F-8 Ziggurat Camp", -8, FrontierSiteFloors.ZIGGURAT.landingX, FrontierSiteFloors.ZIGGURAT.landingY),
        dungeonCampPreset("floor_m9", "F-9 Barrow Camp", -9, 24, 40),
        dungeonCampPreset("floor_m10", "F-10 Caldera Camp", -10, 24, 40, openEndedTop = true),
        dungeonCampPreset("floor_m11", "F-11 Copper Quarry Mines", -11, HeartlandSiteFloors.QUARRY.landingX, HeartlandSiteFloors.QUARRY.landingY),
        dungeonCampPreset("floor_m12", "F-12 North Mountain Cavern", -12, HeartlandSiteFloors.MOUNTAIN.landingX, HeartlandSiteFloors.MOUNTAIN.landingY),
        dungeonCampPreset("floor_m13", "F-13 Forest Sinkhole", -13, 24, 40),
        dungeonCampPreset("floor_m14", "F-14 Crag Deep Hollow", -14, HeartlandSiteFloors.CRAG.landingX, HeartlandSiteFloors.CRAG.landingY),
        dungeonCampPreset("floor_m15", "F-15 Ember Sanctum", -15, 24, 40, openEndedTop = true),
        dungeonCampPreset("floor_m29", "F-29 Murkfen Hollow", -29, FrontierSiteFloors.MURKFEN.landingX, FrontierSiteFloors.MURKFEN.landingY),
        dungeonCampPreset("floor_m30", "F-30 Tomb of Akhet", -30, FrontierSiteFloors.TOMB.landingX, FrontierSiteFloors.TOMB.landingY)
    )

    val overlandLandmarks: List<AdminTeleportPreset> = townsAndEmpires + overlandCamps

    val dungeonFloors: List<AdminTeleportPreset> = dungeonCamps

    val allPresets: List<AdminTeleportPreset> = overlandLandmarks + dungeonFloors

    /**
     * Warp targets grouped by zone (Rookvale Lv 1-20 first, then each zone in level order).
     * Within a zone: towns, then camps, then dungeons in floor order (F-1, F-2, ...).
     */
    val zones: List<AdminWarpZone> = OverlandBiomeTier.entries
        .sortedBy { it.minLevel }
        .map { tier ->
            AdminWarpZone(
                tier = tier,
                towns = townsAndEmpires.filter { it.biomeTier == tier },
                camps = overlandCamps.filter { it.biomeTier == tier },
                dungeons = dungeonCamps.filter { it.biomeTier == tier }.sortedByDescending { it.floor }
            )
        }
        .filter { it.allPresets.isNotEmpty() }

    /** Zone the admin is standing in, used to open that zone's section by default. */
    fun zoneTierForFloor(floor: Int): OverlandBiomeTier =
        if (floor < 0) BiomeProgressionRules.surfaceBiomeForDungeonFloor(floor) else OverlandBiomeTier.ROOKVALE_HEARTLAND

    fun presetsFor(category: AdminWarpCategory?): List<AdminTeleportPreset> = when (category) {
        null -> allPresets
        AdminWarpCategory.TOWNS_EMPIRES -> townsAndEmpires
        AdminWarpCategory.OVERLAND_CAMPS -> overlandCamps
        AdminWarpCategory.DUNGEON_CAMPS -> dungeonCamps
    }
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

    /** Admin status comes from the server (`players.is_admin`) via the PIN-verified cloud session. */
    fun isAuthorizedAdmin(isAdminFlag: Boolean, session: CloudSession?): Boolean =
        isAdminFlag && session?.isAdmin == true

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
        val pushed = if (isCloudConfigured()) insertAuditRows(listOf(entry), session) else false
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
        val pushed = insertAuditRows(pending, session)
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

    /**
     * Appends rows through the server-side `rv_append_admin_audit` function, which re-verifies the
     * caller's PIN and admin flag and records the real character name as the actor.
     */
    private fun insertAuditRows(entries: List<AdminAuditEntry>, session: CloudSession?): Boolean {
        if (entries.isEmpty()) return true
        if (session == null) return false
        val url = MultiplayerConfig.restRpcUrl(RPC_APPEND_AUDIT, urlProvider(), anonKeyProvider()) ?: return false

        val rows = JSONArray()
        for (entry in entries) {
            val detailsObj = JSONObject()
            entry.details.forEach { (k, v) -> detailsObj.put(k, v) }
            rows.put(JSONObject().apply {
                put("event_id", entry.eventId)
                put("created_at", IsoTimestampUtil.formatUtc(entry.createdAtMs))
                put("action_type", entry.actionType.wireName)
                put("target_name", entry.targetName)
                put("summary", entry.summary)
                put("details", detailsObj)
            })
        }
        val payload = JSONObject().apply {
            put("p_player_id", session.playerId)
            put("p_pin_hash", session.pinHash)
            put("p_entries", rows)
        }

        val req = baseRequestBuilder(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                resp.isSuccessful && JSONObject(resp.body?.string().orEmpty()).optString("status") == "ok"
            }
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
        private const val RPC_APPEND_AUDIT = "rv_append_admin_audit"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
