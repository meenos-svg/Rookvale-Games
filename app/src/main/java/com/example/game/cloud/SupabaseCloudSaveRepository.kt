package com.example.game.cloud

import android.content.Context
import android.content.SharedPreferences
import com.example.game.model.CURRENT_MAP_VERSION
import com.example.game.model.CorpseEntity
import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.Direction
import com.example.game.model.EquippedGear
import com.example.game.model.GameWorldState
import com.example.game.model.ItemStack
import com.example.game.model.PlayerSaveData
import com.example.game.model.PlayerSkills
import com.example.game.model.SkillProgress
import com.example.game.model.Vocation
import com.example.game.model.normalizeRegionId
import com.example.game.multiplayer.MultiplayerConfig
import com.example.game.multiplayer.MultiplayerSanitizer
import com.example.game.multiplayer.RemotePlayerState
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
    val pinHash: String,
    /** Last admin status reported by the server (`players.is_admin`). Never set client-side. */
    val isAdmin: Boolean = false
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
    val updatedAtMs: Long,
    val isAdmin: Boolean = false
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
 * Admin status is decided by the server (`players.is_admin`, see supabase/players_security.sql).
 * The client never holds an admin secret; it only mirrors the flag the server returned for the
 * PIN-verified session. The name "Admin" is reserved server-side and can only be claimed by the
 * project owner opening a one-time claim window.
 */
object AdminAccountPolicy {
    const val ADMIN_CANONICAL_NAME = "Admin"

    fun isAdminName(rawName: String): Boolean =
        PinHasher.normalizeNameKey(rawName) == "admin"

    fun enforceAdminFlag(session: CloudSession, saveData: PlayerSaveData): PlayerSaveData {
        val verified = session.isAdmin
        return saveData.copy(
            name = if (verified && isAdminName(session.characterName)) ADMIN_CANONICAL_NAME else saveData.name,
            isAdmin = verified
        )
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
    /**
     * Serializes ONLY permanent character data for Supabase `public.players.save_data`.
     * Strictly excludes world entities (monsters, bosses, corpses, ground loot, ore node
     * cooldowns, map chunks) and ephemeral combat/status timers (poisonTicks, fireTicks).
     */
    fun encode(data: PlayerSaveData): JSONObject {
        return JSONObject().apply {
            put("slotId", data.slotId)
            put("name", data.name)
            put("vocationName", data.vocationName)
            put("isAdmin", data.isAdmin)
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
            put("bankGold", data.bankGold.coerceAtLeast(0L))
            put("regionId", normalizeRegionId(data.regionId))
            put("floor", data.floor.coerceIn(-28, 0))
            put("x", data.x)
            put("y", data.y)
            put("mapVersion", data.mapVersion)
            put("tunicColorIndex", data.tunicColorIndex)
            put("hairColorIndex", data.hairColorIndex)

            put("magicLevel", data.skills.magic.level)
            put("magicXp", data.skills.magic.points)
            data.boundAttackRuneId?.let { put("boundAttackRuneId", it) }
            data.boundHealRuneId?.let { put("boundHealRuneId", it) }
            data.selectedBaitId?.let { put("selectedBaitId", it) }
            put("customHotbarSlots", JSONArray().apply {
                val normalized = List(2) { idx -> data.customHotbarSlots.getOrNull(idx) }
                normalized.forEach { put(it ?: JSONObject.NULL) }
            })

            put("skills", JSONObject().apply {
                put("melee", encodeSkill(data.skills.melee))
                put("fist", encodeSkill(data.skills.fist))
                put("club", encodeSkill(data.skills.club))
                put("sword", encodeSkill(data.skills.sword))
                put("axe", encodeSkill(data.skills.axe))
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
                data.equipment.ringId?.let { put("ringId", it) }
                data.equipment.legsId?.let { put("legsId", it) }
                data.equipment.bootsId?.let { put("bootsId", it) }
                data.equipment.ammoId?.let { put("ammoId", it) }
            })

            put("backpackItems", encodeItemList(data.backpack))
            put("toolBagItems", encodeItemList(data.toolBag))
            put("depotItems", encodeItemList(data.depot))
            if (data.reaperVault.isNotEmpty()) {
                put("reaperVaultItems", encodeItemList(data.reaperVault))
            }

            if (data.questFlags.isNotEmpty()) {
                put("questFlags", JSONObject().apply {
                    data.questFlags.forEach { (k, v) -> put(k, v) }
                })
            }
        }
    }

    fun decode(json: JSONObject, fallbackPlayerId: String, fallbackName: String): PlayerSaveData {
        val skillsObj = json.optJSONObject("skills")
        val decodedMagic = run {
            val nested = skillsObj?.optJSONObject("magic")
            val topLvl = if (json.has("magicLevel")) json.optInt("magicLevel", 1).coerceIn(1, 200) else null
            val topXp = if (json.has("magicXp")) json.optDouble("magicXp", 0.0).coerceAtLeast(0.0) else null
            when {
                topLvl != null -> SkillProgress(topLvl, topXp ?: nested?.optDouble("points", 0.0)?.coerceAtLeast(0.0) ?: 0.0)
                nested != null -> decodeSkill(nested, defaultLevel = 1)
                else -> SkillProgress(1, 0.0)
            }
        }
        val skills = if (skillsObj != null) {
            val decodedMelee = decodeSkill(skillsObj.optJSONObject("melee"))
            val fistSkill = skillsObj.optJSONObject("fist")?.let { decodeSkill(it) } ?: decodedMelee
            val clubSkill = skillsObj.optJSONObject("club")?.let { decodeSkill(it) } ?: decodedMelee
            val swordSkill = skillsObj.optJSONObject("sword")?.let { decodeSkill(it) } ?: decodedMelee
            val axeSkill = skillsObj.optJSONObject("axe")?.let { decodeSkill(it) } ?: decodedMelee
            PlayerSkills(
                melee = decodedMelee,
                fist = fistSkill,
                club = clubSkill,
                sword = swordSkill,
                axe = axeSkill,
                distance = decodeSkill(skillsObj.optJSONObject("distance")),
                magic = decodedMagic,
                shielding = decodeSkill(skillsObj.optJSONObject("shielding")),
                fishing = decodeSkill(skillsObj.optJSONObject("fishing")),
                mining = decodeSkill(skillsObj.optJSONObject("mining"))
            )
        } else {
            PlayerSkills(magic = decodedMagic)
        }

        val eqObj = json.optJSONObject("equippedItems") ?: json.optJSONObject("equipment")
        val equipment = if (eqObj != null) {
            EquippedGear(
                helmetId = eqObj.optNullableString("helmetId"),
                amuletId = eqObj.optNullableString("amuletId"),
                armorId = eqObj.optNullableString("armorId"),
                weaponId = eqObj.optNullableString("weaponId"),
                shieldId = eqObj.optNullableString("shieldId"),
                ringId = eqObj.optNullableString("ringId"),
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
        val reaperVaultArr = json.optJSONArray("reaperVaultItems") ?: json.optJSONArray("reaperVault")
        // Legacy migration: rescue items from any old cloud-persisted playerCorpses into reaperVault
        val legacyCorpseItems = decodeLegacyCorpseItems(json.optJSONArray("playerCorpses"))
        val decodedReaperVault = mergeItemStacks(decodeItemList(reaperVaultArr), legacyCorpseItems)

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
        val parsedLevel = json.optInt("level", 1).coerceIn(1, 999)
        val parsedVocationName = json.optNullableString("vocationName") ?: "ADVENTURER"
        val parsedVocation = runCatching { Vocation.valueOf(parsedVocationName) }.getOrDefault(Vocation.ADVENTURER)
        val rawCapVal = if (json.has("cap")) json.optDouble("cap", com.example.game.model.BASE_MAX_CAP_OZ) else json.optDouble("maxCapOz", com.example.game.model.BASE_MAX_CAP_OZ)
        val hydratedMaxCap = maxOf(rawCapVal, GameWorldState.maxCapForLevel(parsedLevel, parsedVocation))

        return PlayerSaveData(
            slotId = json.optNullableString("slotId") ?: fallbackPlayerId,
            name = PinHasher.sanitizeName(json.optNullableString("name") ?: fallbackName).ifBlank { fallbackName },
            vocationName = parsedVocationName,
            isAdmin = json.optBoolean("isAdmin", false),
            level = parsedLevel,
            xp = json.optLong("xp", 0L).coerceAtLeast(0L),
            hp = json.optInt("hp", maxHp).coerceIn(1, maxHp),
            maxHp = maxHp,
            mp = json.optInt("mp", maxMp).coerceIn(0, maxMp),
            maxMp = maxMp,
            soul = json.optInt("soul", maxSoul).coerceIn(0, maxSoul),
            maxSoul = maxSoul,
            maxCapOz = hydratedMaxCap,
            gold = json.optInt("gold", 0).coerceAtLeast(0),
            bankGold = json.optLong("bankGold", 0L).coerceAtLeast(0L),
            floor = json.optInt("floor", 0).coerceIn(-28, 0),
            x = json.optInt("x", WorldMaps.TEMPLE_SPAWN_X),
            y = json.optInt("y", WorldMaps.TEMPLE_SPAWN_Y),
            poisonTicks = 0,
            fireTicks = 0,
            skills = skills,
            equipment = equipment,
            backpack = decodeItemList(backpackArr),
            toolBag = decodeItemList(toolBagArr),
            depot = decodeItemList(depotArr),
            reaperVault = decodedReaperVault,
            playerCorpses = emptyList(),
            mapVersion = json.optInt("mapVersion", CURRENT_MAP_VERSION),
            tunicColorIndex = json.optInt("tunicColorIndex", 0).coerceAtLeast(0),
            hairColorIndex = json.optInt("hairColorIndex", 0).coerceAtLeast(0),
            questFlags = questFlags,
            boundAttackRuneId = json.optNullableString("boundAttackRuneId"),
            boundHealRuneId = json.optNullableString("boundHealRuneId") ?: "healing_rune",
            selectedBaitId = json.optNullableString("selectedBaitId"),
            customHotbarSlots = run {
                val arr = json.optJSONArray("customHotbarSlots")
                List(2) { idx ->
                    if (arr != null && idx < arr.length() && !arr.isNull(idx)) {
                        arr.optString(idx, "").trim().takeIf { it.isNotEmpty() }
                    } else null
                }
            },
            regionId = normalizeRegionId(json.optNullableString("regionId")),
            facingName = Direction.SOUTH.name
        )
    }

    private fun encodeSkill(sp: SkillProgress): JSONObject = JSONObject().apply {
        put("level", sp.level)
        put("points", sp.points)
    }

    private fun decodeSkill(obj: JSONObject?, defaultLevel: Int = 10): SkillProgress {
        if (obj == null) return SkillProgress(level = defaultLevel, points = 0.0)
        return SkillProgress(
            level = obj.optInt("level", defaultLevel).coerceIn(1, 200),
            points = obj.optDouble("points", 0.0).coerceAtLeast(0.0)
        )
    }

    private fun encodeItemList(items: List<ItemStack>): JSONArray = JSONArray().apply {
        items.toList().forEach { stack ->
            if (stack.itemId.isNotBlank() && stack.count > 0) {
                put(JSONObject().apply {
                    put("itemId", stack.itemId.trim())
                    put("count", stack.count.coerceIn(1, 999_999))
                })
            }
        }
    }

    private fun decodeItemList(arr: JSONArray?): List<ItemStack> {
        if (arr == null || arr.length() <= 0) return emptyList()
        val out = ArrayList<ItemStack>(arr.length().coerceAtMost(200))
        for (i in 0 until arr.length().coerceAtMost(200)) {
            runCatching {
                val obj = arr.optJSONObject(i) ?: return@runCatching
                val itemId = obj.optNullableString("itemId")?.trim()?.takeIf { it.isNotEmpty() } ?: return@runCatching
                val count = obj.optInt("count", 1)
                if (count > 0) {
                    out.add(ItemStack(itemId, count.coerceIn(1, 999_999)))
                }
            }
        }
        return out.toList()
    }

    /**
     * One-time legacy compatibility helper: extracts carried items from any pre-refactor
     * `playerCorpses` array in older cloud saves so they migrate safely into `reaperVault`.
     */
    private fun decodeLegacyCorpseItems(arr: JSONArray?): List<ItemStack> {
        if (arr == null || arr.length() <= 0) return emptyList()
        val out = mutableListOf<ItemStack>()
        for (i in 0 until arr.length().coerceAtMost(20)) {
            val obj = arr.optJSONObject(i) ?: continue
            out += decodeItemList(obj.optJSONArray("items"))
        }
        return out
    }

    private fun mergeItemStacks(base: List<ItemStack>, extra: List<ItemStack>): List<ItemStack> {
        if (extra.isEmpty()) return base
        val counts = LinkedHashMap<String, Int>()
        for (stack in base + extra) {
            if (stack.itemId.isNotBlank() && stack.count > 0) {
                counts[stack.itemId] = ((counts[stack.itemId] ?: 0) + stack.count).coerceAtMost(999_999)
            }
        }
        return counts.entries.map { (id, cnt) -> ItemStack(id, cnt) }
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
        return CloudSession(id, name, pinHash, store.getBoolean(KEY_ACTIVE_IS_ADMIN, false))
    }

    fun saveCachedSession(session: CloudSession) {
        store.putValues {
            put(KEY_ACTIVE_PLAYER_ID, session.playerId)
            put(KEY_ACTIVE_CHARACTER_NAME, session.characterName)
            put(KEY_ACTIVE_PIN_HASH, session.pinHash)
            put(KEY_ACTIVE_IS_ADMIN, session.isAdmin)
        }
    }

    fun clearCachedSession() {
        store.removeKeys(KEY_ACTIVE_PLAYER_ID, KEY_ACTIVE_CHARACTER_NAME, KEY_ACTIVE_PIN_HASH, KEY_ACTIVE_IS_ADMIN)
    }

    fun saveLocalBackup(
        session: CloudSession,
        saveData: PlayerSaveData,
        updatedAtMs: Long,
        pendingCloudSync: Boolean,
        preEncodedJsonString: String? = null
    ) {
        val encoded = preEncodedJsonString ?: CloudSavePayloadCodec.encode(saveData).toString()
        val nameKey = PinHasher.normalizeNameKey(session.characterName)
        store.putValues {
            put(backupJsonKey(session.playerId), encoded)
            put(backupUpdatedKey(session.playerId), updatedAtMs)
            put(backupPendingKey(session.playerId), pendingCloudSync)
            put(backupNameKey(session.playerId), session.characterName)
            put(backupPinHashKey(session.playerId), session.pinHash)
            put(backupIsAdminKey(session.playerId), session.isAdmin)
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
            session = CloudSession(playerId, name, pinHash, store.getBoolean(backupIsAdminKey(playerId), false)),
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
    private fun backupIsAdminKey(playerId: String) = "backup_admin_$playerId"
    private fun nameIndexKey(normalizedName: String) = "name_idx_$normalizedName"

    companion object {
        private const val KEY_ACTIVE_PLAYER_ID = "active_player_id"
        private const val KEY_ACTIVE_CHARACTER_NAME = "active_character_name"
        private const val KEY_ACTIVE_PIN_HASH = "active_pin_hash"
        private const val KEY_ACTIVE_IS_ADMIN = "active_is_admin"
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
     * and creates the row through the server-side `rv_save_player` function.
     *
     * The name "Admin" is reserved by the server; it can only be created online while the
     * project owner has opened the one-time admin claim window.
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

        if (AdminAccountPolicy.isAdminName(cleanName)) {
            return@withContext claimAdminCharacter(pin, initialSaveBuilder)
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

        val initialSave = initialSaveBuilder(cleanName).copy(isAdmin = false)
        val session = CloudSession(
            playerId = initialSave.slotId,
            characterName = cleanName,
            pinHash = pinHash
        )
        val now = nowProvider()
        localStore.saveCachedSession(session)
        localStore.saveLocalBackup(session, initialSave, now, pendingCloudSync = true)

        val pushed = if (isCloudConfigured()) savePlayerRow(session, initialSave, now) else SaveRpcResult.NetworkError
        when (pushed) {
            is SaveRpcResult.Saved -> {
                localStore.markCloudSynced(session.playerId, now)
                CloudAuthResult.Success(
                    session = session,
                    saveData = initialSave,
                    syncedFromCloud = true,
                    notice = "Hero \"$cleanName\" created and saved to cloud."
                )
            }
            SaveRpcResult.NameTaken, SaveRpcResult.Reserved, SaveRpcResult.Rejected -> {
                localStore.clearCachedSession()
                CloudAuthResult.Failure(
                    "\"$cleanName\" is already taken. Use Load Character with your PIN, or pick another name."
                )
            }
            SaveRpcResult.Locked, SaveRpcResult.NetworkError -> CloudAuthResult.Success(
                session = session,
                saveData = initialSave,
                syncedFromCloud = false,
                notice = "Hero \"$cleanName\" created offline — will sync to cloud when connected."
            )
        }
    }

    /**
     * Creates the reserved "Admin" hero. Only succeeds online, and only while the owner has opened
     * the claim window in Supabase (`rv_config.admin_claim_open = 'true'`).
     */
    private fun claimAdminCharacter(
        pin: String,
        initialSaveBuilder: (cleanName: String) -> PlayerSaveData
    ): CloudAuthResult {
        if (!isCloudConfigured()) {
            return CloudAuthResult.Failure("The Admin hero can only be created while online.")
        }
        val name = AdminAccountPolicy.ADMIN_CANONICAL_NAME
        val pinHash = PinHasher.hashPin(name, pin)
        val baseSave = initialSaveBuilder(name).copy(name = name, isAdmin = false)
        val draftSession = CloudSession(playerId = baseSave.slotId, characterName = name, pinHash = pinHash)
        val now = nowProvider()
        return when (val result = savePlayerRow(draftSession, baseSave, now)) {
            is SaveRpcResult.Saved -> {
                val session = draftSession.copy(isAdmin = result.isAdmin)
                val save = AdminAccountPolicy.enforceAdminFlag(session, baseSave)
                localStore.saveCachedSession(session)
                localStore.saveLocalBackup(session, save, now, pendingCloudSync = false)
                CloudAuthResult.Success(
                    session = session,
                    saveData = save,
                    syncedFromCloud = true,
                    notice = if (result.isAdmin) "Admin claimed and saved to cloud." else "Hero \"$name\" created."
                )
            }
            SaveRpcResult.NetworkError -> CloudAuthResult.Failure(
                "Unable to reach Rookvale cloud. The Admin hero can only be created while online."
            )
            else -> CloudAuthResult.Failure(
                "The name \"Admin\" is reserved. Use Load Character with the Admin PIN, or choose another name."
            )
        }
    }

    /**
     * Loads/recovers an existing character using Name + 4-digit PIN. The PIN is verified by the
     * server (`rv_load_player`), which also reports whether the character is an admin.
     */
    suspend fun loginAndRecover(
        rawName: String,
        pin: String
    ): CloudAuthResult = withContext(Dispatchers.IO) {
        val rawCleanName = PinHasher.sanitizeName(rawName)
        if (!PinHasher.isValidCharacterName(rawCleanName)) {
            return@withContext CloudAuthResult.Failure("Enter a valid character name (2-18 characters).")
        }
        if (!PinHasher.isValidPin(pin)) {
            return@withContext CloudAuthResult.Failure("Enter your 4-digit numeric PIN.")
        }

        val isAdminLogin = AdminAccountPolicy.isAdminName(rawCleanName)
        val cleanName = if (isAdminLogin) AdminAccountPolicy.ADMIN_CANONICAL_NAME else rawCleanName
        val pinHash = PinHasher.hashPin(cleanName, pin)
        val localMatch = localStore.getLocalBackupByNameAndPin(cleanName, pinHash)

        if (!isCloudConfigured()) {
            return@withContext if (localMatch != null) {
                localStore.saveCachedSession(localMatch.session)
                val enforced = AdminAccountPolicy.enforceAdminFlag(localMatch.session, localMatch.saveData)
                CloudAuthResult.Success(
                    session = localMatch.session,
                    saveData = enforced,
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
                val session = CloudSession(remote.playerId, remote.characterName.ifBlank { cleanName }, pinHash, remote.isAdmin)
                val existingLocal = localStore.getLocalBackupByPlayerId(remote.playerId) ?: localMatch
                reconcileAndCache(session, remote, existingLocal)
            }
            FetchOutcome.Locked -> CloudAuthResult.Failure(TOO_MANY_ATTEMPTS_MESSAGE)
            FetchOutcome.NotFound -> {
                if (localMatch != null && localMatch.pendingCloudSync) {
                    val now = nowProvider()
                    val enforced = AdminAccountPolicy.enforceAdminFlag(localMatch.session, localMatch.saveData)
                    localStore.saveCachedSession(localMatch.session)
                    val pushed = upsertPlayerRow(localMatch.session, enforced, localMatch.updatedAtMs.coerceAtLeast(now))
                    if (pushed) localStore.markCloudSynced(localMatch.session.playerId, localMatch.updatedAtMs.coerceAtLeast(now))
                    CloudAuthResult.Success(
                        session = localMatch.session,
                        saveData = enforced,
                        syncedFromCloud = pushed,
                        notice = if (pushed) "Recovered ${localMatch.session.characterName} and synced to cloud." else "Loaded local hero backup."
                    )
                } else {
                    CloudAuthResult.Failure("No character found matching \"$cleanName\" and that PIN.")
                }
            }
            FetchOutcome.NetworkError -> {
                if (localMatch != null) {
                    localStore.saveCachedSession(localMatch.session)
                    val enforced = AdminAccountPolicy.enforceAdminFlag(localMatch.session, localMatch.saveData)
                    CloudAuthResult.Success(
                        session = localMatch.session,
                        saveData = enforced,
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
                    saveData = AdminAccountPolicy.enforceAdminFlag(it.session, it.saveData),
                    syncedFromCloud = false,
                    notice = "Resumed ${it.session.characterName} from local backup."
                )
            }
        }

        val remoteFetch = when (val byId = fetchPlayerByIdAndPin(cached.playerId, cached.pinHash)) {
            is FetchOutcome.Found -> byId
            FetchOutcome.NotFound -> fetchPlayerByNameAndPin(cached.characterName, cached.pinHash)
            FetchOutcome.Locked -> FetchOutcome.Locked
            FetchOutcome.NetworkError -> FetchOutcome.NetworkError
        }

        when (remoteFetch) {
            is FetchOutcome.Found -> {
                val remote = remoteFetch.row
                val resolvedSession = CloudSession(remote.playerId, remote.characterName, cached.pinHash, remote.isAdmin)
                reconcileAndCache(resolvedSession, remote, localBackup)
            }
            FetchOutcome.NotFound -> {
                if (localBackup != null) {
                    val pushTs = localBackup.updatedAtMs.coerceAtLeast(nowProvider())
                    val enforced = AdminAccountPolicy.enforceAdminFlag(cached, localBackup.saveData)
                    val pushed = upsertPlayerRow(cached, enforced, pushTs)
                    if (pushed) localStore.markCloudSynced(cached.playerId, pushTs)
                    CloudAuthResult.Success(
                        session = cached,
                        saveData = enforced,
                        syncedFromCloud = pushed,
                        notice = if (pushed) "Restored ${cached.characterName} and backed up to cloud."
                        else "Resumed ${cached.characterName} from local backup."
                    )
                } else {
                    localStore.clearCachedSession()
                    null
                }
            }
            FetchOutcome.Locked, FetchOutcome.NetworkError -> {
                localBackup?.let {
                    CloudAuthResult.Success(
                        session = cached,
                        saveData = AdminAccountPolicy.enforceAdminFlag(cached, it.saveData),
                        syncedFromCloud = false,
                        notice = "Offline backup loaded for ${cached.characterName} — will sync when online."
                    )
                }
            }
        }
    }

    /**
     * Always writes a fast local backup in SharedPreferences first, then upserts to Supabase `public.players`.
     * Accepts an optional pre-encoded `save_data` JSON snapshot captured synchronously on the main thread
     * so rapid inventory/depot transfers never race background serialization.
     */
    suspend fun saveProgress(
        session: CloudSession,
        saveData: PlayerSaveData,
        preEncodedSaveJson: JSONObject? = null
    ): CloudSaveOutcome =
        withContext(Dispatchers.IO) {
            val now = nowProvider()
            val encodedJson = preEncodedSaveJson ?: CloudSavePayloadCodec.encode(saveData)
            localStore.saveCachedSession(session)
            localStore.saveLocalBackup(
                session = session,
                saveData = saveData,
                updatedAtMs = now,
                pendingCloudSync = true,
                preEncodedJsonString = encodedJson.toString()
            )

            if (!isCloudConfigured()) {
                return@withContext CloudSaveOutcome.SAVED_LOCAL_OFFLINE
            }

            val ok = upsertPlayerRow(session, saveData, now, preEncodedSaveJson = encodedJson)
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
        if (remote is FetchOutcome.NetworkError || remote is FetchOutcome.Locked) {
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
            val enforcedLocal = AdminAccountPolicy.enforceAdminFlag(session, localBackup.saveData)
            val pushed = upsertPlayerRow(session, enforcedLocal, localBackup.updatedAtMs)
            localStore.saveLocalBackup(
                session = session,
                saveData = enforcedLocal,
                updatedAtMs = localBackup.updatedAtMs,
                pendingCloudSync = !pushed
            )
            CloudAuthResult.Success(
                session = session,
                saveData = enforcedLocal,
                syncedFromCloud = pushed,
                notice = if (pushed) "Synced newer local progress for ${session.characterName} to cloud."
                else "Loaded newer local progress for ${session.characterName}."
            )
        } else {
            val enforcedRemote = AdminAccountPolicy.enforceAdminFlag(session, remote.saveData)
            localStore.saveLocalBackup(
                session = session,
                saveData = enforcedRemote,
                updatedAtMs = remote.updatedAtMs.coerceAtLeast(1L),
                pendingCloudSync = false
            )
            CloudAuthResult.Success(
                session = session,
                saveData = enforcedRemote,
                syncedFromCloud = true,
                notice = "Loaded ${session.characterName} (Lvl ${enforcedRemote.level}) from Rookvale cloud."
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

    internal fun fetchPlayerByNameAndPin(cleanName: String, pinHash: String): FetchOutcome =
        executeLoadRpc(
            JSONObject().apply {
                put("p_pin_hash", pinHash)
                put("p_character_name", cleanName)
            },
            fallbackName = cleanName,
            pinHash = pinHash
        )

    internal fun fetchPlayerByIdAndPin(playerId: String, pinHash: String): FetchOutcome =
        executeLoadRpc(
            JSONObject().apply {
                put("p_pin_hash", pinHash)
                put("p_player_id", playerId)
            },
            fallbackName = "",
            pinHash = pinHash
        )

    /** Calls `rv_load_player`, which verifies the PIN server-side and rate-limits wrong guesses. */
    private fun executeLoadRpc(args: JSONObject, fallbackName: String, pinHash: String): FetchOutcome {
        val url = MultiplayerConfig.restRpcUrl(RPC_LOAD_PLAYER, urlProvider(), anonKeyProvider())
            ?: return FetchOutcome.NetworkError
        val req = baseRequestBuilder(url)
            .post(args.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return FetchOutcome.NetworkError
                val row = JSONObject(resp.body?.string().orEmpty())
                when (row.optString("status")) {
                    "ok" -> Unit
                    "locked" -> return FetchOutcome.Locked
                    else -> return FetchOutcome.NotFound
                }
                val playerId = row.optString("player_id", "").trim()
                if (playerId.isBlank()) return FetchOutcome.NotFound
                val charName = row.optString("character_name", fallbackName).trim()
                val saveDataObj = row.optJSONObject("save_data")
                    ?: row.optString("save_data", "").takeIf { it.startsWith("{") }?.let { JSONObject(it) }
                    ?: return FetchOutcome.NotFound
                val updatedAtMs = IsoTimestampUtil.parseUtcMillis(row.optString("updated_at", ""))
                val saveData = CloudSavePayloadCodec.decode(saveDataObj, playerId, charName)
                FetchOutcome.Found(
                    RemotePlayerRow(
                        playerId = playerId,
                        characterName = charName,
                        pinHash = pinHash,
                        saveData = saveData,
                        updatedAtMs = updatedAtMs,
                        isAdmin = row.optBoolean("is_admin", false)
                    )
                )
            }
        }.getOrElse { FetchOutcome.NetworkError }
    }

    internal fun upsertPlayerRow(
        session: CloudSession,
        saveData: PlayerSaveData,
        updatedAtMs: Long,
        preEncodedSaveJson: JSONObject? = null
    ): Boolean = savePlayerRow(session, saveData, updatedAtMs, preEncodedSaveJson) is SaveRpcResult.Saved

    /**
     * Calls `rv_save_player`. For an existing row the server verifies the PIN and only updates
     * progress (name, PIN and admin flag cannot be changed); otherwise it creates the character.
     */
    internal fun savePlayerRow(
        session: CloudSession,
        saveData: PlayerSaveData,
        updatedAtMs: Long,
        preEncodedSaveJson: JSONObject? = null
    ): SaveRpcResult {
        val url = MultiplayerConfig.restRpcUrl(RPC_SAVE_PLAYER, urlProvider(), anonKeyProvider())
            ?: return SaveRpcResult.NetworkError

        val payload = JSONObject().apply {
            put("p_player_id", session.playerId)
            put("p_character_name", session.characterName)
            put("p_pin_hash", session.pinHash)
            put("p_save_data", preEncodedSaveJson ?: CloudSavePayloadCodec.encode(saveData))
            put("p_updated_at", IsoTimestampUtil.formatUtc(updatedAtMs))
        }

        val req = baseRequestBuilder(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return SaveRpcResult.NetworkError
                val obj = JSONObject(resp.body?.string().orEmpty())
                when (obj.optString("status")) {
                    "ok" -> SaveRpcResult.Saved(isAdmin = obj.optBoolean("is_admin", false))
                    "name_taken" -> SaveRpcResult.NameTaken
                    "reserved" -> SaveRpcResult.Reserved
                    "locked" -> SaveRpcResult.Locked
                    else -> SaveRpcResult.Rejected
                }
            }
        }.getOrDefault(SaveRpcResult.NetworkError)
    }

    /**
     * Lightweight fallback poll on `public.players` returning recently active online heroes
     * (`updated_at >= sinceEpochMs`), excluding the local player by both UUID and case-insensitive name.
     */
    suspend fun fetchRecentOnlinePlayers(
        sinceEpochMs: Long,
        excludePlayerId: String,
        excludeCharacterName: String,
        limit: Int = 50
    ): List<RemotePlayerState> = withContext(Dispatchers.IO) {
        val base = MultiplayerConfig.restPlayersBaseUrl(urlProvider(), anonKeyProvider()) ?: return@withContext emptyList()
        val isoSince = IsoTimestampUtil.formatUtc(sinceEpochMs)
        val url = base.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("select", "player_id,character_name,save_data,updated_at,is_admin")
            ?.addQueryParameter("updated_at", "gte.$isoSince")
            ?.addQueryParameter("order", "updated_at.desc")
            ?.addQueryParameter("limit", limit.coerceIn(1, 100).toString())
            ?.build()
            ?: return@withContext emptyList()

        val req = baseRequestBuilder(url.toString()).get().build()
        val cleanExcludeId = MultiplayerSanitizer.sanitizeId(excludePlayerId)
        val cleanExcludeName = PinHasher.normalizeNameKey(excludeCharacterName)

        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string().orEmpty()
                val arr = JSONArray(body)
                val out = ArrayList<RemotePlayerState>(arr.length())
                for (i in 0 until arr.length()) {
                    val row = arr.optJSONObject(i) ?: continue
                    val pid = MultiplayerSanitizer.sanitizeId(row.optString("player_id", ""))
                    val rawName = row.optString("character_name", "").trim()
                    if (pid.isBlank()) continue
                    if (cleanExcludeId.isNotBlank() && pid.equals(cleanExcludeId, ignoreCase = true)) continue
                    if (cleanExcludeName.isNotBlank() && PinHasher.normalizeNameKey(rawName) == cleanExcludeName) continue

                    val saveObj = row.optJSONObject("save_data")
                        ?: row.optString("save_data", "").takeIf { it.startsWith("{") }?.let { JSONObject(it) }
                        ?: continue
                    val updatedAtMs = IsoTimestampUtil.parseUtcMillis(row.optString("updated_at", ""))
                        .takeIf { it > 0L } ?: nowProvider()
                    if (updatedAtMs < sinceEpochMs - 5_000L) continue

                    val floor = saveObj.optInt("floor", 0)
                    if (!MultiplayerSanitizer.isValidFloor(floor)) continue
                    val (cx, cy) = MultiplayerSanitizer.clampCoord(
                        floor,
                        saveObj.optInt("x", WorldMaps.TEMPLE_SPAWN_X),
                        saveObj.optInt("y", WorldMaps.TEMPLE_SPAWN_Y)
                    )
                    val maxHp = saveObj.optInt("maxHp", 150).coerceIn(1, 50_000)
                    val hp = saveObj.optInt("hp", maxHp).coerceIn(0, maxHp)
                    val regionId = normalizeRegionId(saveObj.optString("regionId", ""))
                    val facingRaw = saveObj.optString("facing", "").ifBlank { saveObj.optString("direction", "") }
                    val name = MultiplayerSanitizer.sanitizeName(
                        rawName.ifBlank { saveObj.optString("name", "") }
                    )
                    val eqObj = saveObj.optJSONObject("equippedItems")
                    fun optEq(key: String): String? = eqObj?.optString(key, "")?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
                    out.add(
                        RemotePlayerState(
                            playerId = pid,
                            name = name,
                            level = saveObj.optInt("level", 1).coerceIn(1, 999),
                            vocation = MultiplayerSanitizer.safeVocation(saveObj.optString("vocationName", "")),
                            tunicColorIndex = MultiplayerSanitizer.clampTunicIndex(saveObj.optInt("tunicColorIndex", 0)),
                            hairColorIndex = MultiplayerSanitizer.clampHairIndex(saveObj.optInt("hairColorIndex", 0)),
                            x = cx,
                            y = cy,
                            prevX = cx,
                            prevY = cy,
                            lastMoveMs = 0L,
                            floor = floor,
                            hp = hp,
                            maxHp = maxHp,
                            joinedAt = updatedAtMs,
                            isPoisoned = saveObj.optInt("poisonTicks", 0) > 0,
                            isBurning = saveObj.optInt("fireTicks", 0) > 0,
                            direction = MultiplayerSanitizer.safeDirection(facingRaw),
                            regionId = regionId,
                            lastSeenMs = updatedAtMs,
                            weaponId = optEq("weaponId"),
                            offhandId = optEq("shieldId") ?: optEq("offhandId"),
                            armorId = optEq("armorId"),
                            helmetId = optEq("helmetId"),
                            bootsId = optEq("bootsId"),
                            isAdmin = row.optBoolean("is_admin", false)
                        )
                    )
                }
                out
            }
        }.getOrDefault(emptyList())
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
        data object Locked : FetchOutcome()
        data object NetworkError : FetchOutcome()
    }

    internal sealed class SaveRpcResult {
        data class Saved(val isAdmin: Boolean) : SaveRpcResult()
        data object NameTaken : SaveRpcResult()
        data object Reserved : SaveRpcResult()
        data object Locked : SaveRpcResult()
        /** Wrong PIN or invalid payload. */
        data object Rejected : SaveRpcResult()
        data object NetworkError : SaveRpcResult()
    }

    companion object {
        private const val PREFS_NAME = "rookvale_cloud_saves"
        private const val RPC_LOAD_PLAYER = "rv_load_player"
        private const val RPC_SAVE_PLAYER = "rv_save_player"
        const val TOO_MANY_ATTEMPTS_MESSAGE =
            "Too many wrong PINs for this hero. Please wait 15 minutes and try again."
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
