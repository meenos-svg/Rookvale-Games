package com.example.game.cloud

import android.content.Context
import android.content.SharedPreferences
import com.example.game.model.CURRENT_MAP_VERSION
import com.example.game.model.CorpseEntity
import com.example.game.model.EquippedGear
import com.example.game.model.ItemStack
import com.example.game.model.PlayerSaveData
import com.example.game.model.PlayerSkills
import com.example.game.model.SkillProgress
import com.example.game.multiplayer.MultiplayerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class CloudSession(
    val playerId: String,
    val characterName: String,
    val pinHash: String
)

data class CloudAuthUiState(
    val isBusy: Boolean = false,
    val isAutoLoggingIn: Boolean = false,
    val cachedSession: CloudSession? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val cloudConfigured: Boolean = MultiplayerConfig.isConfigured()
)

enum class CloudSaveOutcome {
    SYNCED_CLOUD,
    SAVED_LOCAL_OFFLINE
}

data class LocalBackupRecord(
    val session: CloudSession,
    val saveData: PlayerSaveData,
    val updatedAtMs: Long,
    val pendingCloudSync: Boolean
)

data class RemotePlayerRow(
    val playerId: String,
    val characterName: String,
    val pinHash: String,
    val saveData: PlayerSaveData,
    val updatedAtMs: Long
)

sealed class CloudAuthResult {
    data class Success(
        val session: CloudSession,
        val saveData: PlayerSaveData,
        val syncedFromCloud: Boolean,
        val notice: String
    ) : CloudAuthResult()

    data class Failure(val message: String) : CloudAuthResult()
}

/**
 * Deterministic PBKDF2-HMAC-SHA256 PIN hasher keyed by normalized character name.
 * Ensures plaintext PINs are never persisted or transmitted while enabling cross-device
 * recovery using only Character Name + 4-digit PIN.
 */
object PinHasher {
    private const val PBKDF2_ITERATIONS = 65_536
    private const val KEY_LENGTH_BITS = 256
    private val validNameRegex = Regex("^[A-Za-z0-9 _'-]{2,18}$")
    private val controlCharsRegex = Regex("[\\p{Cntrl}]")

    fun sanitizeName(raw: String): String {
        return raw
            .replace(controlCharsRegex, "")
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(18)
    }

    fun isValidCharacterName(name: String): Boolean {
        val cleaned = sanitizeName(name)
        return validNameRegex.matches(cleaned)
    }

    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it.isDigit() }

    fun normalizeNameKey(characterName: String): String =
        sanitizeName(characterName).lowercase(Locale.US)

    fun hashPin(characterName: String, pin: String): String {
        val salt = "rookvale:pin:v1:${normalizeNameKey(characterName)}".toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        val hex = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            hex.append(String.format(Locale.US, "%02x", b))
        }
        return hex.toString()
    }
}

/**
 * API 24+ compatible ISO-8601 UTC formatter & Postgres timestamptz parser.
 */
object IsoTimestampUtil {
    private val utcZone: TimeZone = TimeZone.getTimeZone("UTC")
    private val isoNormalizeRegex = Regex(
        "^(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d+))?(Z|[+-]\\d{2}(?::?\\d{2})?)?$"
    )

    fun formatUtc(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = utcZone
        return sdf.format(Date(epochMs))
    }

    fun parseUtcMillis(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val trimmed = raw.trim()
        val match = isoNormalizeRegex.matchEntire(trimmed) ?: return 0L
        val datePart = match.groupValues[1]
        val timePart = match.groupValues[2]
        val fracRaw = match.groupValues[3]
        val tzRaw = match.groupValues[4].ifBlank { "Z" }

        val millisPart = when {
            fracRaw.isEmpty() -> "000"
            fracRaw.length >= 3 -> fracRaw.substring(0, 3)
            else -> fracRaw.padEnd(3, '0')
        }

        val normalizedTz = when {
            tzRaw == "Z" || tzRaw == "z" -> "+0000"
            tzRaw.length == 3 -> "${tzRaw}00"
            tzRaw.contains(":") -> tzRaw.replace(":", "")
            else -> tzRaw
        }

        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            sdf.timeZone = utcZone
            sdf.parse("${datePart}T${timePart}.${millisPart}${normalizedTz}")?.time ?: 0L
        }.getOrDefault(0L)
    }
}

/**
 * Serializes and deserializes the complete Rookvale player state for the `save_data` JSONB column
 * in `public.players` and the offline SharedPreferences backup.
 */
object CloudSavePayloadCodec {
    fun encode(data: PlayerSaveData): JSONObject {
        return JSONObject().apply {
            put("slotId", data.slotId)
            put("name", data.name)
            put("vocationName", data.vocationName)
            put("level", data.level)
            put("xp", data.xp)
            put("hp", data.hp)
            put("maxHp", data.maxHp)
            put("mp", data.mp)
            put("maxMp", data.maxMp)
            put("soul", data.soul)
            put("maxSoul", data.maxSoul)
            put("cap", data.maxCapOz)
            put("maxCapOz", data.maxCapOz)
            put("gold", data.gold)
            put("floor", data.floor)
            put("x", data.x)
            put("y", data.y)
            put("poisonTicks", data.poisonTicks)
            put("fireTicks", data.fireTicks)
            put("mapVersion", data.mapVersion)
            put("tunicColorIndex", data.tunicColorIndex)
            put("hairColorIndex", data.hairColorIndex)

            put("skills", JSONObject().apply {
                put("melee", encodeSkill(data.skills.melee))
                put("distance", encodeSkill(data.skills.distance))
                put("magic", encodeSkill(data.skills.magic))
                put("shielding", encodeSkill(data.skills.shielding))
                put("fishing", encodeSkill(data.skills.fishing))
                put("mining", encodeSkill(data.skills.mining))
            })

            put("equippedItems", JSONObject().apply {
                data.equipment.helmetId?.let { put("helmetId", it) }
                data.equipment.amuletId?.let { put("amuletId", it) }
                data.equipment.armorId?.let { put("armorId", it) }
                data.equipment.weaponId?.let { put("weaponId", it) }
                data.equipment.shieldId?.let { put("shieldId", it) }
                data.equipment.legsId?.let { put("legsId", it) }
                data.equipment.bootsId?.let { put("bootsId", it) }
                data.equipment.ammoId?.let { put("ammoId", it) }
            })

            put("backpackItems", encodeItemList(data.backpack))
            put("toolBagItems", encodeItemList(data.toolBag))
            put("depotItems", encodeItemList(data.depot))
            put("playerCorpses", encodeCorpses(data.playerCorpses))

            put("questFlags", JSONObject().apply {
                data.questFlags.forEach { (k, v) -> put(k, v) }
            })
        }
    }

    fun decode(json: JSONObject, fallbackPlayerId: String, fallbackName: String): PlayerSaveData {
        val skillsObj = json.optJSONObject("skills")
        val skills = if (skillsObj != null) {
            PlayerSkills(
                melee = decodeSkill(skillsObj.optJSONObject("melee")),
                distance = decodeSkill(skillsObj.optJSONObject("distance")),
                magic = decodeSkill(skillsObj.optJSONObject("magic")),
                shielding = decodeSkill(skillsObj.optJSONObject("shielding")),
                fishing = decodeSkill(skillsObj.optJSONObject("fishing")),
                mining = decodeSkill(skillsObj.optJSONObject("mining"))
            )
        } else {
            PlayerSkills()
        }

        val eqObj = json.optJSONObject("equippedItems") ?: json.optJSONObject("equipment")
        val equipment = if (eqObj != null) {
            EquippedGear(
                helmetId = eqObj.optNullableString("helmetId"),
                amuletId = eqObj.optNullableString("amuletId"),
                armorId = eqObj.optNullableString("armorId"),
                weaponId = eqObj.optNullableString("weaponId"),
                shieldId = eqObj.optNullableString("shieldId"),
                legsId = eqObj.optNullableString("legsId"),
                bootsId = eqObj.optNullableString("bootsId"),
                ammoId = eqObj.optNullableString("ammoId")
            )
        } else {
            EquippedGear()
        }

        val backpackArr = json.optJSONArray("backpackItems") ?: json.optJSONArray("backpack")
        val toolBagArr = json.optJSONArray("toolBagItems") ?: json.optJSONArray("toolBag")
        val depotArr = json.optJSONArray("depotItems") ?: json.optJSONArray("depot")
        val corpsesArr = json.optJSONArray("playerCorpses")

        val flagsObj = json.optJSONObject("questFlags")
        val questFlags = buildMap {
            if (flagsObj != null) {
                val keys = flagsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.length <= 64) {
                        put(key, flagsObj.optBoolean(key, false))
                    }
                }
            }
        }

        val maxHp = json.optInt("maxHp", 150).coerceAtLeast(1)
        val maxMp = json.optInt("maxMp", 55).coerceAtLeast(0)
        val maxSoul = json.optInt("maxSoul", 100).coerceAtLeast(1)
        val capVal = if (json.has("cap")) json.optDouble("cap", 400.0) else json.optDouble("maxCapOz", 400.0)

        return PlayerSaveData(
            slotId = json.optNullableString("slotId") ?: fallbackPlayerId,
            name = PinHasher.sanitizeName(json.optNullableString("name") ?: fallbackName).ifBlank { fallbackName },
            vocationName = json.optNullableString("vocationName") ?: "ADVENTURER",
            level = json.optInt("level", 1).coerceIn(1, 999),
            xp = json.optLong("xp", 0L).coerceAtLeast(0L),
            hp = json.optInt("hp", maxHp).coerceIn(1, maxHp),
            maxHp = maxHp,
            mp = json.optInt("mp", maxMp).coerceIn(0, maxMp),
            maxMp = maxMp,
            soul = json.optInt("soul", maxSoul).coerceIn(0, maxSoul),
            maxSoul = maxSoul,
            maxCapOz = capVal.coerceAtLeast(100.0),
            gold = json.optInt("gold", 0).coerceAtLeast(0),
            floor = json.optInt("floor", 0).coerceIn(-4, 0),
            x = json.optInt("x", 47),
            y = json.optInt("y", 46),
            poisonTicks = json.optInt("poisonTicks", 0).coerceAtLeast(0),
            fireTicks = json.optInt("fireTicks", 0).coerceAtLeast(0),
            skills = skills,
            equipment = equipment,
            backpack = decodeItemList(backpackArr),
            toolBag = decodeItemList(toolBagArr),
            depot = decodeItemList(depotArr),
            playerCorpses = decodeCorpses(corpsesArr),
            mapVersion = json.optInt("mapVersion", CURRENT_MAP_VERSION),
            tunicColorIndex = json.optInt("tunicColorIndex", 0).coerceAtLeast(0),
            hairColorIndex = json.optInt("hairColorIndex", 0).coerceAtLeast(0),
            questFlags = questFlags
        )
    }

    private fun encodeSkill(sp: SkillProgress): JSONObject = JSONObject().apply {
        put("level", sp.level)
        put("points", sp.points)
    }

    private fun decodeSkill(obj: JSONObject?): SkillProgress {
        if (obj == null) return SkillProgress()
        return SkillProgress(
            level = obj.optInt("level", 10).coerceIn(1, 200),
            points = obj.optDouble("points", 0.0).coerceAtLeast(0.0)
        )
    }

    private fun encodeItemList(items: List<ItemStack>): JSONArray = JSONArray().apply {
        items.forEach { stack ->
            put(JSONObject().apply {
                put("itemId", stack.itemId)
                put("count", stack.count)
            })
        }
    }

    private fun decodeItemList(arr: JSONArray?): List<ItemStack> {
        if (arr == null) return emptyList()
        val out = mutableListOf<ItemStack>()
        for (i in 0 until arr.length().coerceAtMost(200)) {
            val obj = arr.optJSONObject(i) ?: continue
            val itemId = obj.optNullableString("itemId") ?: continue
            val count = obj.optInt("count", 1).coerceIn(1, 9999)
            out += ItemStack(itemId, count)
        }
        return out
    }

    private fun encodeCorpses(corpses: List<CorpseEntity>): JSONArray = JSONArray().apply {
        corpses.takeLast(12).forEach { c ->
            put(JSONObject().apply {
                put("id", c.id)
                put("floor", c.floor)
                put("x", c.x)
                put("y", c.y)
                put("label", c.label)
                put("isPlayerCorpse", c.isPlayerCorpse)
                c.monsterTypeName?.let { put("monsterTypeName", it) }
                put("gold", c.gold)
                put("items", encodeItemList(c.items))
            })
        }
    }

    private fun decodeCorpses(arr: JSONArray?): List<CorpseEntity> {
        if (arr == null) return emptyList()
        val out = mutableListOf<CorpseEntity>()
        for (i in 0 until arr.length().coerceAtMost(20)) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optNullableString("id") ?: continue
            out += CorpseEntity(
                id = id,
                floor = obj.optInt("floor", 0).coerceIn(-4, 0),
                x = obj.optInt("x", 47),
                y = obj.optInt("y", 46),
                label = obj.optNullableString("label") ?: "Fallen Backpack",
                isPlayerCorpse = obj.optBoolean("isPlayerCorpse", true),
                monsterTypeName = obj.optNullableString("monsterTypeName"),
                gold = obj.optInt("gold", 0).coerceAtLeast(0),
                items = decodeItemList(obj.optJSONArray("items"))
            )
        }
        return out
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val raw = optString(key, "").trim()
        return raw.ifEmpty { null }
    }
}

/**
 * Simple key-value storage abstraction backed by SharedPreferences in production
 * and an in-memory map in JVM unit tests.
 */
interface CloudKeyValueStore {
    fun getString(key: String): String?
    fun getLong(key: String, defaultValue: Long = 0L): Long
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun putValues(block: MutableMap<String, Any?>.() -> Unit)
    fun removeKeys(vararg keys: String)
}

class SharedPrefsCloudStore(private val prefs: SharedPreferences) : CloudKeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)

    override fun putValues(block: MutableMap<String, Any?>.() -> Unit) {
        val map = linkedMapOf<String, Any?>()
        map.block()
        val editor = prefs.edit()
        for ((k, v) in map) {
            when (v) {
                null -> editor.remove(k)
                is String -> editor.putString(k, v)
                is Long -> editor.putLong(k, v)
                is Boolean -> editor.putBoolean(k, v)
                is Int -> editor.putInt(k, v)
            }
        }
        editor.apply()
    }

    override fun removeKeys(vararg keys: String) {
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
    }
}

/**
 * Local SharedPreferences cache for active cloud login session + offline safety backups.
 */
class LocalCloudBackupStore(private val store: CloudKeyValueStore) {
    fun getCachedSession(): CloudSession? {
        val id = store.getString(KEY_ACTIVE_PLAYER_ID)?.trim().orEmpty()
        val name = store.getString(KEY_ACTIVE_CHARACTER_NAME)?.trim().orEmpty()
        val pinHash = store.getString(KEY_ACTIVE_PIN_HASH)?.trim().orEmpty()
        if (id.isBlank() || name.isBlank() || pinHash.isBlank()) return null
        return CloudSession(id, name, pinHash)
    }

    fun saveCachedSession(session: CloudSession) {
        store.putValues {
            put(KEY_ACTIVE_PLAYER_ID, session.playerId)
            put(KEY_ACTIVE_CHARACTER_NAME, session.characterName)
            put(KEY_ACTIVE_PIN_HASH, session.pinHash)
        }
    }

    fun clearCachedSession() {
        store.removeKeys(KEY_ACTIVE_PLAYER_ID, KEY_ACTIVE_CHARACTER_NAME, KEY_ACTIVE_PIN_HASH)
    }

    fun saveLocalBackup(
        session: CloudSession,
        saveData: PlayerSaveData,
        updatedAtMs: Long,
        pendingCloudSync: Boolean
    ) {
        val encoded = CloudSavePayloadCodec.encode(saveData).toString()
        val nameKey = PinHasher.normalizeNameKey(session.characterName)
        store.putValues {
            put(backupJsonKey(session.playerId), encoded)
            put(backupUpdatedKey(session.playerId), updatedAtMs)
            put(backupPendingKey(session.playerId), pendingCloudSync)
            put(backupNameKey(session.playerId), session.characterName)
            put(backupPinHashKey(session.playerId), session.pinHash)
            put(nameIndexKey(nameKey), session.playerId)
        }
    }

    fun markCloudSynced(playerId: String, syncedAtMs: Long) {
        val currentUpdated = store.getLong(backupUpdatedKey(playerId), 0L)
        // Only clear pending flag if no newer local write occurred while the HTTP request was in flight
        if (currentUpdated <= syncedAtMs) {
            store.putValues {
                put(backupPendingKey(playerId), false)
            }
        }
    }

    fun getLocalBackupByPlayerId(playerId: String): LocalBackupRecord? {
        val rawJson = store.getString(backupJsonKey(playerId)) ?: return null
        val name = store.getString(backupNameKey(playerId)) ?: return null
        val pinHash = store.getString(backupPinHashKey(playerId)) ?: return null
        val updatedAtMs = store.getLong(backupUpdatedKey(playerId), 0L)
        val pending = store.getBoolean(backupPendingKey(playerId), false)
        val parsed = runCatching {
            CloudSavePayloadCodec.decode(JSONObject(rawJson), playerId, name)
        }.getOrNull() ?: return null
        return LocalBackupRecord(
            session = CloudSession(playerId, name, pinHash),
            saveData = parsed,
            updatedAtMs = updatedAtMs,
            pendingCloudSync = pending
        )
    }

    fun getLocalBackupByNameAndPin(characterName: String, pinHash: String): LocalBackupRecord? {
        val playerId = store.getString(nameIndexKey(PinHasher.normalizeNameKey(characterName))) ?: return null
        val record = getLocalBackupByPlayerId(playerId) ?: return null
        return if (record.session.pinHash == pinHash) record else null
    }

    private fun backupJsonKey(playerId: String) = "backup_json_$playerId"
    private fun backupUpdatedKey(playerId: String) = "backup_updated_$playerId"
    private fun backupPendingKey(playerId: String) = "backup_pending_$playerId"
    private fun backupNameKey(playerId: String) = "backup_name_$playerId"
    private fun backupPinHashKey(playerId: String) = "backup_pin_$playerId"
    private fun nameIndexKey(normalizedName: String) = "name_idx_$normalizedName"

    companion object {
        private const val KEY_ACTIVE_PLAYER_ID = "active_player_id"
        private const val KEY_ACTIVE_CHARACTER_NAME = "active_character_name"
        private const val KEY_ACTIVE_PIN_HASH = "active_pin_hash"
    }
}

/**
 * Persistent cloud character save & recovery repository backed by Supabase PostgREST (`public.players`)
 * with automatic SharedPreferences offline backup and `updated_at` timestamp conflict resolution.
 */
class SupabaseCloudSaveRepository(
    val localStore: LocalCloudBackupStore,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val urlProvider: () -> String = { MultiplayerConfig.SUPABASE_URL },
    private val anonKeyProvider: () -> String = { MultiplayerConfig.SUPABASE_ANON_KEY },
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    constructor(context: Context) : this(
        localStore = LocalCloudBackupStore(
            SharedPrefsCloudStore(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
        )
    )

    fun isCloudConfigured(): Boolean =
        MultiplayerConfig.restPlayersBaseUrl(urlProvider(), anonKeyProvider()) != null

    fun getCachedSession(): CloudSession? = localStore.getCachedSession()

    fun clearCachedSession() {
        localStore.clearCachedSession()
    }

    /**
     * Registers a brand new hero with Name + 4-digit PIN.
     * Verifies the name is unclaimed when online, caches session & local backup immediately,
     * and upserts the initial row to Supabase `public.players`.
     */
    suspend fun createCharacter(
        rawName: String,
        pin: String,
        initialSaveBuilder: (cleanName: String) -> PlayerSaveData
    ): CloudAuthResult = withContext(Dispatchers.IO) {
        val cleanName = PinHasher.sanitizeName(rawName)
        if (!PinHasher.isValidCharacterName(cleanName)) {
            return@withContext CloudAuthResult.Failure(
                "Hero name must be 2-18 letters, numbers, spaces, apostrophes, or hyphens."
            )
        }
        if (!PinHasher.isValidPin(pin)) {
            return@withContext CloudAuthResult.Failure("Please enter a 4-digit numeric PIN.")
        }

        val pinHash = PinHasher.hashPin(cleanName, pin)

        // Check if hero name is already claimed in Supabase when online
        if (isCloudConfigured()) {
            when (checkCharacterNameExists(cleanName)) {
                true -> return@withContext CloudAuthResult.Failure(
                    "\"$cleanName\" is already taken. Use Load Character with your PIN, or pick another name."
                )
                false, null -> Unit
            }
        }

        val initialSave = initialSaveBuilder(cleanName)
        val session = CloudSession(
            playerId = initialSave.slotId,
            characterName = cleanName,
            pinHash = pinHash
        )
        val now = nowProvider()
        localStore.saveCachedSession(session)
        localStore.saveLocalBackup(session, initialSave, now, pendingCloudSync = true)

        val pushed = upsertPlayerRow(session, initialSave, now)
        if (pushed) {
            localStore.markCloudSynced(session.playerId, now)
            CloudAuthResult.Success(
                session = session,
                saveData = initialSave,
                syncedFromCloud = true,
                notice = "Hero \"$cleanName\" created and saved to cloud."
            )
        } else {
            CloudAuthResult.Success(
                session = session,
                saveData = initialSave,
                syncedFromCloud = false,
                notice = "Hero \"$cleanName\" created offline — will sync to cloud when connected."
            )
        }
    }

    /**
     * Loads/recovers an existing character using Name + 4-digit PIN from Supabase `public.players`.
     * Compares `updated_at` against any local backup so newer offline progress is never overwritten.
     */
    suspend fun loginAndRecover(rawName: String, pin: String): CloudAuthResult = withContext(Dispatchers.IO) {
        val cleanName = PinHasher.sanitizeName(rawName)
        if (!PinHasher.isValidCharacterName(cleanName)) {
            return@withContext CloudAuthResult.Failure("Enter a valid character name (2-18 characters).")
        }
        if (!PinHasher.isValidPin(pin)) {
            return@withContext CloudAuthResult.Failure("Enter your 4-digit numeric PIN.")
        }

        val pinHash = PinHasher.hashPin(cleanName, pin)
        val localMatch = localStore.getLocalBackupByNameAndPin(cleanName, pinHash)

        if (!isCloudConfigured()) {
            return@withContext if (localMatch != null) {
                localStore.saveCachedSession(localMatch.session)
                CloudAuthResult.Success(
                    session = localMatch.session,
                    saveData = localMatch.saveData,
                    syncedFromCloud = false,
                    notice = "Loaded local backup for ${localMatch.session.characterName} (cloud offline)."
                )
            } else {
                CloudAuthResult.Failure("Cloud save unavailable offline and no local backup matched that Name + PIN.")
            }
        }

        val fetchResult = fetchPlayerByNameAndPin(cleanName, pinHash)
        when (fetchResult) {
            is FetchOutcome.Found -> {
                val remote = fetchResult.row
                val session = CloudSession(remote.playerId, remote.characterName, pinHash)
                val existingLocal = localStore.getLocalBackupByPlayerId(remote.playerId) ?: localMatch
                reconcileAndCache(session, remote, existingLocal)
            }
            FetchOutcome.NotFound -> {
                // If created offline earlier on this device and never yet uploaded, sync it now!
                if (localMatch != null && localMatch.pendingCloudSync) {
                    val now = nowProvider()
                    localStore.saveCachedSession(localMatch.session)
                    val pushed = upsertPlayerRow(localMatch.session, localMatch.saveData, localMatch.updatedAtMs.coerceAtLeast(now))
                    if (pushed) localStore.markCloudSynced(localMatch.session.playerId, localMatch.updatedAtMs.coerceAtLeast(now))
                    CloudAuthResult.Success(
                        session = localMatch.session,
                        saveData = localMatch.saveData,
                        syncedFromCloud = pushed,
                        notice = if (pushed) "Recovered local hero and synced to cloud." else "Loaded local hero backup."
                    )
                } else {
                    CloudAuthResult.Failure("No character found matching \"$cleanName\" and that PIN.")
                }
            }
            FetchOutcome.NetworkError -> {
                if (localMatch != null) {
                    localStore.saveCachedSession(localMatch.session)
                    CloudAuthResult.Success(
                        session = localMatch.session,
                        saveData = localMatch.saveData,
                        syncedFromCloud = false,
                        notice = "Offline mode: loaded cached backup for ${localMatch.session.characterName}."
                    )
                } else {
                    CloudAuthResult.Failure("Unable to reach Rookvale cloud. Check your internet connection and try again.")
                }
            }
        }
    }

    /**
     * Auto-logs in the cached session on app startup without requiring the user to retype their PIN.
     * Reconciles cloud vs local backup timestamps so the newest progress always wins.
     */
    suspend fun autoLoginCachedSession(): CloudAuthResult? = withContext(Dispatchers.IO) {
        val cached = localStore.getCachedSession() ?: return@withContext null
        val localBackup = localStore.getLocalBackupByPlayerId(cached.playerId)
            ?: localStore.getLocalBackupByNameAndPin(cached.characterName, cached.pinHash)

        if (!isCloudConfigured()) {
            return@withContext localBackup?.let {
                CloudAuthResult.Success(
                    session = it.session,
                    saveData = it.saveData,
                    syncedFromCloud = false,
                    notice = "Resumed ${it.session.characterName} from local backup."
                )
            }
        }

        val remoteFetch = when (val byId = fetchPlayerByIdAndPin(cached.playerId, cached.pinHash)) {
            is FetchOutcome.Found -> byId
            FetchOutcome.NotFound -> fetchPlayerByNameAndPin(cached.characterName, cached.pinHash)
            FetchOutcome.NetworkError -> FetchOutcome.NetworkError
        }

        when (remoteFetch) {
            is FetchOutcome.Found -> {
                val remote = remoteFetch.row
                val resolvedSession = CloudSession(remote.playerId, remote.characterName, cached.pinHash)
                reconcileAndCache(resolvedSession, remote, localBackup)
            }
            FetchOutcome.NotFound -> {
                if (localBackup != null) {
                    val pushTs = localBackup.updatedAtMs.coerceAtLeast(nowProvider())
                    val pushed = upsertPlayerRow(cached, localBackup.saveData, pushTs)
                    if (pushed) localStore.markCloudSynced(cached.playerId, pushTs)
                    CloudAuthResult.Success(
                        session = cached,
                        saveData = localBackup.saveData,
                        syncedFromCloud = pushed,
                        notice = if (pushed) "Restored ${cached.characterName} and backed up to cloud."
                        else "Resumed ${cached.characterName} from local backup."
                    )
                } else {
                    localStore.clearCachedSession()
                    null
                }
            }
            FetchOutcome.NetworkError -> {
                localBackup?.let {
                    CloudAuthResult.Success(
                        session = cached,
                        saveData = it.saveData,
                        syncedFromCloud = false,
                        notice = "Offline backup loaded for ${cached.characterName} — will sync when online."
                    )
                }
            }
        }
    }

    /**
     * Always writes a fast local backup in SharedPreferences first, then upserts to Supabase `public.players`.
     */
    suspend fun saveProgress(session: CloudSession, saveData: PlayerSaveData): CloudSaveOutcome =
        withContext(Dispatchers.IO) {
            val now = nowProvider()
            localStore.saveCachedSession(session)
            localStore.saveLocalBackup(session, saveData, now, pendingCloudSync = true)

            if (!isCloudConfigured()) {
                return@withContext CloudSaveOutcome.SAVED_LOCAL_OFFLINE
            }

            val ok = upsertPlayerRow(session, saveData, now)
            if (ok) {
                localStore.markCloudSynced(session.playerId, now)
                CloudSaveOutcome.SYNCED_CLOUD
            } else {
                CloudSaveOutcome.SAVED_LOCAL_OFFLINE
            }
        }

    /**
     * Synchronizes any unsynced offline backup once connectivity returns, checking remote `updated_at`
     * first so newer cloud progress from another device is never overwritten.
     */
    suspend fun syncPendingOfflineBackup(session: CloudSession): CloudSaveOutcome? = withContext(Dispatchers.IO) {
        val backup = localStore.getLocalBackupByPlayerId(session.playerId) ?: return@withContext null
        if (!backup.pendingCloudSync) return@withContext null
        if (!isCloudConfigured()) return@withContext CloudSaveOutcome.SAVED_LOCAL_OFFLINE

        val remote = fetchPlayerByIdAndPin(session.playerId, session.pinHash)
        if (remote is FetchOutcome.Found && remote.row.updatedAtMs > backup.updatedAtMs) {
            // Cloud has a strictly newer timestamp — do not overwrite it with older offline data
            localStore.saveLocalBackup(session, remote.row.saveData, remote.row.updatedAtMs, pendingCloudSync = false)
            return@withContext CloudSaveOutcome.SYNCED_CLOUD
        }
        if (remote is FetchOutcome.NetworkError) {
            return@withContext CloudSaveOutcome.SAVED_LOCAL_OFFLINE
        }

        val pushed = upsertPlayerRow(session, backup.saveData, backup.updatedAtMs)
        if (pushed) {
            localStore.markCloudSynced(session.playerId, backup.updatedAtMs)
            CloudSaveOutcome.SYNCED_CLOUD
        } else {
            CloudSaveOutcome.SAVED_LOCAL_OFFLINE
        }
    }

    private fun reconcileAndCache(
        session: CloudSession,
        remote: RemotePlayerRow,
        localBackup: LocalBackupRecord?
    ): CloudAuthResult.Success {
        localStore.saveCachedSession(session)
        return if (localBackup != null && localBackup.updatedAtMs > remote.updatedAtMs) {
            // Local backup is newer than cloud (e.g., offline play) — keep local and push to cloud!
            val pushed = upsertPlayerRow(session, localBackup.saveData, localBackup.updatedAtMs)
            localStore.saveLocalBackup(
                session = session,
                saveData = localBackup.saveData,
                updatedAtMs = localBackup.updatedAtMs,
                pendingCloudSync = !pushed
            )
            CloudAuthResult.Success(
                session = session,
                saveData = localBackup.saveData,
                syncedFromCloud = pushed,
                notice = if (pushed) "Synced newer local progress for ${session.characterName} to cloud."
                else "Loaded newer local progress for ${session.characterName}."
            )
        } else {
            localStore.saveLocalBackup(
                session = session,
                saveData = remote.saveData,
                updatedAtMs = remote.updatedAtMs.coerceAtLeast(1L),
                pendingCloudSync = false
            )
            CloudAuthResult.Success(
                session = session,
                saveData = remote.saveData,
                syncedFromCloud = true,
                notice = "Loaded ${session.characterName} (Lvl ${remote.saveData.level}) from Rookvale cloud."
            )
        }
    }

    internal fun checkCharacterNameExists(cleanName: String): Boolean? {
        val base = MultiplayerConfig.restPlayersBaseUrl(urlProvider(), anonKeyProvider()) ?: return null
        val url = base.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("select", "player_id")
            ?.addQueryParameter("character_name", "eq.$cleanName")
            ?.addQueryParameter("limit", "1")
            ?.build()
            ?: return null

        val req = baseRequestBuilder(url.toString()).get().build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                val arr = JSONArray(body)
                arr.length() > 0
            }
        }.getOrNull()
    }

    internal fun fetchPlayerByNameAndPin(cleanName: String, pinHash: String): FetchOutcome {
        val base = MultiplayerConfig.restPlayersBaseUrl(urlProvider(), anonKeyProvider())
            ?: return FetchOutcome.NetworkError
        val url = base.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("select", "player_id,character_name,pin_hash,save_data,updated_at")
            ?.addQueryParameter("character_name", "eq.$cleanName")
            ?.addQueryParameter("pin_hash", "eq.$pinHash")
            ?.addQueryParameter("limit", "1")
            ?.build()
            ?: return FetchOutcome.NetworkError
        return executeFetch(url.toString(), cleanName, pinHash)
    }

    internal fun fetchPlayerByIdAndPin(playerId: String, pinHash: String): FetchOutcome {
        val base = MultiplayerConfig.restPlayersBaseUrl(urlProvider(), anonKeyProvider())
            ?: return FetchOutcome.NetworkError
        val url = base.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("select", "player_id,character_name,pin_hash,save_data,updated_at")
            ?.addQueryParameter("player_id", "eq.$playerId")
            ?.addQueryParameter("pin_hash", "eq.$pinHash")
            ?.addQueryParameter("limit", "1")
            ?.build()
            ?: return FetchOutcome.NetworkError
        return executeFetch(url.toString(), "", pinHash)
    }

    private fun executeFetch(url: String, fallbackName: String, expectedPinHash: String): FetchOutcome {
        val req = baseRequestBuilder(url).get().build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return FetchOutcome.NetworkError
                val body = resp.body?.string().orEmpty()
                val arr = JSONArray(body)
                if (arr.length() == 0) return FetchOutcome.NotFound
                val row = arr.getJSONObject(0)
                val playerId = row.optString("player_id", "").trim()
                val charName = row.optString("character_name", fallbackName).trim()
                val rowPinHash = row.optString("pin_hash", "").trim()
                if (playerId.isBlank() || rowPinHash != expectedPinHash) {
                    return FetchOutcome.NotFound
                }
                val saveDataObj = row.optJSONObject("save_data")
                    ?: row.optString("save_data", "").takeIf { it.startsWith("{") }?.let { JSONObject(it) }
                    ?: return FetchOutcome.NotFound
                val updatedAtMs = IsoTimestampUtil.parseUtcMillis(row.optString("updated_at", ""))
                val saveData = CloudSavePayloadCodec.decode(saveDataObj, playerId, charName)
                FetchOutcome.Found(
                    RemotePlayerRow(
                        playerId = playerId,
                        characterName = charName,
                        pinHash = rowPinHash,
                        saveData = saveData,
                        updatedAtMs = updatedAtMs
                    )
                )
            }
        }.getOrElse { FetchOutcome.NetworkError }
    }

    internal fun upsertPlayerRow(
        session: CloudSession,
        saveData: PlayerSaveData,
        updatedAtMs: Long
    ): Boolean {
        val base = MultiplayerConfig.restPlayersBaseUrl(urlProvider(), anonKeyProvider()) ?: return false
        val url = base.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("on_conflict", "player_id")
            ?.build()
            ?: return false

        val payload = JSONObject().apply {
            put("player_id", session.playerId)
            put("character_name", session.characterName)
            put("pin_hash", session.pinHash)
            put("save_data", CloudSavePayloadCodec.encode(saveData))
            put("updated_at", IsoTimestampUtil.formatUtc(updatedAtMs))
        }

        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val req = baseRequestBuilder(url.toString())
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(body)
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp -> resp.isSuccessful }
        }.getOrDefault(false)
    }

    private fun baseRequestBuilder(url: String): Request.Builder {
        val anonKey = anonKeyProvider().trim()
        return Request.Builder()
            .url(url)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
    }

    internal sealed class FetchOutcome {
        data class Found(val row: RemotePlayerRow) : FetchOutcome()
        data object NotFound : FetchOutcome()
        data object NetworkError : FetchOutcome()
    }

    companion object {
        private const val PREFS_NAME = "rookvale_cloud_saves"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
