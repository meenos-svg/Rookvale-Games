package com.example

import com.example.game.cloud.CloudAuthResult
import com.example.game.cloud.CloudKeyValueStore
import com.example.game.cloud.CloudSaveOutcome
import com.example.game.cloud.CloudSavePayloadCodec
import com.example.game.cloud.IsoTimestampUtil
import com.example.game.cloud.LocalCloudBackupStore
import com.example.game.cloud.PinHasher
import com.example.game.cloud.SupabaseCloudSaveRepository
import com.example.game.engine.GameEngine
import com.example.game.model.EquippedGear
import com.example.game.model.GameWorldState
import com.example.game.model.ItemStack
import com.example.game.model.PlayerSkills
import com.example.game.model.SkillProgress
import com.example.game.model.Vocation
import com.example.game.multiplayer.MultiplayerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class InMemoryCloudKeyValueStore : CloudKeyValueStore {
    private val map = mutableMapOf<String, Any>()
    override fun getString(key: String): String? = map[key] as? String
    override fun getLong(key: String, defaultValue: Long): Long = (map[key] as? Long) ?: defaultValue
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = (map[key] as? Boolean) ?: defaultValue
    override fun putValues(block: MutableMap<String, Any?>.() -> Unit) {
        val temp = linkedMapOf<String, Any?>()
        temp.block()
        for ((k, v) in temp) {
            if (v == null) map.remove(k) else map[k] = v
        }
    }
    override fun removeKeys(vararg keys: String) {
        keys.forEach { map.remove(it) }
    }
}

private class FakeSupabasePlayersServer : Interceptor {
    val rowsById = linkedMapOf<String, JSONObject>()
    var offline = false
    var upsertCount = 0
    var adminClaimOpen = false
    private val failures = mutableMapOf<String, Int>()

    override fun intercept(chain: Interceptor.Chain): Response {
        if (offline) throw IOException("Simulated offline network")
        val req = chain.request()
        val url = req.url

        if (req.method == "POST" && url.encodedPath.endsWith("/rpc/rv_load_player")) {
            val args = readBody(req)
            val pid = args.optString("p_player_id", "")
            val name = args.optString("p_character_name", "")
            val row = if (pid.isNotBlank()) rowsById[pid] else findByName(name)
            if (row == null) return jsonResponse(req, 200, JSONObject().put("status", "not_found").toString())
            val status = checkPin(row, args.optString("p_pin_hash"))
            if (status != "ok") return jsonResponse(req, 200, JSONObject().put("status", status).toString())
            val out = JSONObject()
                .put("status", "ok")
                .put("player_id", row.getString("player_id"))
                .put("character_name", row.getString("character_name"))
                .put("save_data", row.get("save_data"))
                .put("updated_at", row.getString("updated_at"))
                .put("is_admin", row.optBoolean("is_admin", false))
            return jsonResponse(req, 200, out.toString())
        }

        if (req.method == "POST" && url.encodedPath.endsWith("/rpc/rv_save_player")) {
            val args = readBody(req)
            val pid = args.getString("p_player_id")
            val existing = rowsById[pid]
            if (existing != null) {
                val status = checkPin(existing, args.optString("p_pin_hash"))
                if (status != "ok") return jsonResponse(req, 200, JSONObject().put("status", status).toString())
                existing.put("save_data", args.getJSONObject("p_save_data"))
                existing.put("updated_at", args.getString("p_updated_at"))
                upsertCount++
                return jsonResponse(req, 200, JSONObject().put("status", "ok").put("is_admin", existing.optBoolean("is_admin")).toString())
            }
            val name = args.getString("p_character_name")
            var isAdmin = false
            if (name.trim().lowercase() == "admin") {
                if (!adminClaimOpen) return jsonResponse(req, 200, JSONObject().put("status", "reserved").toString())
                adminClaimOpen = false
                isAdmin = true
            } else if (findByName(name) != null) {
                return jsonResponse(req, 200, JSONObject().put("status", "name_taken").toString())
            }
            rowsById[pid] = JSONObject()
                .put("player_id", pid)
                .put("character_name", name)
                .put("pin_hash", args.getString("p_pin_hash"))
                .put("save_data", args.getJSONObject("p_save_data"))
                .put("updated_at", args.getString("p_updated_at"))
                .put("is_admin", isAdmin)
            upsertCount++
            return jsonResponse(req, 200, JSONObject().put("status", "ok").put("is_admin", isAdmin).toString())
        }

        if (req.method == "GET") {
            // Public roster / name-check reads; pin_hash is never selectable.
            val idFilter = url.queryParameter("player_id")?.removePrefix("eq.")
            val nameFilter = url.queryParameter("character_name")?.removePrefix("eq.")
            val matched = JSONArray()
            for (row in rowsById.values) {
                val okId = idFilter == null || row.optString("player_id") == idFilter
                val okName = nameFilter == null || row.optString("character_name") == nameFilter
                if (okId && okName) {
                    val pub = JSONObject(row.toString())
                    pub.remove("pin_hash")
                    matched.put(pub)
                }
            }
            return jsonResponse(req, 200, matched.toString())
        }

        return jsonResponse(req, 405, "{}")
    }

    private fun readBody(req: okhttp3.Request): JSONObject {
        val buf = Buffer()
        req.body?.writeTo(buf)
        return JSONObject(buf.readUtf8())
    }

    private fun findByName(name: String): JSONObject? =
        rowsById.values.firstOrNull { it.optString("character_name").trim().lowercase() == name.trim().lowercase() }

    private fun checkPin(row: JSONObject, pinHash: String): String {
        val key = row.optString("character_name").lowercase()
        if ((failures[key] ?: 0) >= 5) return "locked"
        return if (row.optString("pin_hash") == pinHash) {
            failures.remove(key)
            "ok"
        } else {
            failures[key] = (failures[key] ?: 0) + 1
            "bad"
        }
    }

    private fun jsonResponse(req: okhttp3.Request, code: Int, body: String): Response =
        Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
}

class CloudSaveTest {

    @Test
    fun pinHasherValidatesInputsBlocksFilterCharsAndHashesDeterministically() {
        assertTrue(PinHasher.isValidPin("4829"))
        assertFalse(PinHasher.isValidPin("482"))
        assertFalse(PinHasher.isValidPin("48a9"))
        assertFalse(PinHasher.isValidPin("48290"))

        assertTrue(PinHasher.isValidCharacterName("Aldric"))
        assertTrue(PinHasher.isValidCharacterName("Sir Kael-V"))
        assertFalse(PinHasher.isValidCharacterName("A"))
        assertFalse(PinHasher.isValidCharacterName("Aldric,or(1=1)"))
        assertFalse(PinHasher.isValidCharacterName("Aldric*"))

        val h1 = PinHasher.hashPin("Aldric", "4829")
        val h2 = PinHasher.hashPin("aldric ", "4829")
        val h3 = PinHasher.hashPin("Aldric", "4820")
        val h4 = PinHasher.hashPin("Lyra", "4829")
        assertEquals(64, h1.length)
        assertEquals("Normalized name + PIN must hash identically across devices", h1, h2)
        assertNotEquals(h1, h3)
        assertNotEquals("Different heroes with same PIN must have distinct salted hashes", h1, h4)
    }

    @Test
    fun isoTimestampUtilParsesPostgresTimestamptzAndUtcMillis() {
        val sampleMs = 1_763_850_000_123L
        val formatted = IsoTimestampUtil.formatUtc(sampleMs)
        assertEquals(sampleMs, IsoTimestampUtil.parseUtcMillis(formatted))

        val pgMicros = IsoTimestampUtil.parseUtcMillis("2026-11-22T23:59:59.456789+00:00")
        val pgSimple = IsoTimestampUtil.parseUtcMillis("2026-11-22T23:59:59.456Z")
        assertTrue(pgMicros > 0L)
        assertEquals(pgSimple, pgMicros)
    }

    @Test
    fun payloadCodecRoundTripsCompleteSaveDataIncludingSkillsGearBackpackAndQuestFlags() {
        val world = GameEngine.createNewWorld("Lyra", Vocation.PALADIN, tunicColorIndex = 3, hairColorIndex = 2).copy(
            level = 24,
            xp = 29_500L,
            hp = 295,
            maxHp = 330,
            mp = 140,
            maxMp = 180,
            soul = 92,
            maxCapOz = GameWorldState.maxCapForLevel(24, Vocation.PALADIN),
            gold = 4_250,
            bankGold = 18_500L,
            floor = -2,
            playerX = 18,
            playerY = 22,
            skills = PlayerSkills(
                melee = SkillProgress(14, 12.5),
                distance = SkillProgress(28, 44.0),
                magic = SkillProgress(16, 8.0),
                shielding = SkillProgress(21, 5.5),
                fishing = SkillProgress(15, 3.0),
                mining = SkillProgress(19, 11.0)
            ),
            equipment = EquippedGear(
                helmetId = "viking_helmet",
                armorId = "brass_armor",
                weaponId = "ashwood_shortbow",
                ammoId = "ochre_shafts"
            ),
            backpack = listOf(ItemStack("health_potion", 9), ItemStack("sd_rune", 4)),
            questFlags = mapOf("visited_floor_-2" to true, "oracle_vocation_chosen" to true)
        )
        val original = GameEngine.toSaveData(world)
        val encoded = CloudSavePayloadCodec.encode(original)

        // Verify expected top-level save_data JSON keys exist
        assertTrue(encoded.has("cap"))
        assertTrue(encoded.has("bankGold"))
        assertTrue(encoded.has("skills"))
        assertTrue(encoded.has("equippedItems"))
        assertTrue(encoded.has("backpackItems"))
        assertTrue(encoded.has("questFlags"))

        val decoded = CloudSavePayloadCodec.decode(encoded, original.slotId, original.name)
        assertEquals(original.level, decoded.level)
        assertEquals(original.xp, decoded.xp)
        assertEquals(original.hp, decoded.hp)
        assertEquals(original.maxCapOz, decoded.maxCapOz, 0.01)
        assertEquals(original.gold, decoded.gold)
        assertEquals(original.bankGold, decoded.bankGold)
        assertEquals(original.floor, decoded.floor)
        assertEquals(original.x, decoded.x)
        assertEquals(original.y, decoded.y)
        assertEquals(28, decoded.skills.distance.level)
        assertEquals(15, decoded.skills.fishing.level)
        assertEquals("ashwood_shortbow", decoded.equipment.weaponId)
        assertEquals(2, decoded.backpack.size)
        assertEquals(true, decoded.questFlags["oracle_vocation_chosen"])
    }

    @Test
    fun createRecoverOfflineFallbackAndTimestampConflictResolutionWorkEndToEnd() = runBlocking {
        assertEquals(
            "https://demo.supabase.co/rest/v1/players",
            MultiplayerConfig.restPlayersBaseUrl("https://demo.supabase.co", "sb_publishable_demo")
        )

        val server = FakeSupabasePlayersServer()
        val client = OkHttpClient.Builder().addInterceptor(server).build()
        var clockMs = 1_760_000_000_000L

        // Device A creates hero "Aldric" with PIN 1357
        val deviceAStore = LocalCloudBackupStore(InMemoryCloudKeyValueStore())
        val repoA = SupabaseCloudSaveRepository(
            localStore = deviceAStore,
            httpClient = client,
            urlProvider = { "https://demo.supabase.co" },
            anonKeyProvider = { "sb_publishable_demo" },
            nowProvider = { clockMs }
        )

        val created = repoA.createCharacter("Aldric", "1357") { cleanName ->
            GameEngine.toSaveData(GameEngine.createNewWorld(cleanName))
        }
        assertTrue(created is CloudAuthResult.Success)
        val sessionA = (created as CloudAuthResult.Success).session
        assertEquals(1, server.rowsById.size)
        assertNotNull("Session must be cached locally for auto-login", repoA.getCachedSession())

        // Duplicate name creation must be rejected while online
        val duplicate = repoA.createCharacter("Aldric", "9999") { cleanName ->
            GameEngine.toSaveData(GameEngine.createNewWorld(cleanName))
        }
        assertTrue("Duplicate hero name must fail", duplicate is CloudAuthResult.Failure)

        // Device A levels up to Level 7 and saves to cloud
        clockMs += 10_000L
        val lvl7Save = created.saveData.copy(level = 7, gold = 950, floor = -1, x = 18, y = 14)
        val saveOutcome = repoA.saveProgress(sessionA, lvl7Save)
        assertEquals(CloudSaveOutcome.SYNCED_CLOUD, saveOutcome)

        // Device B (fresh install with empty SharedPreferences) recovers "Aldric" via Name + PIN
        val deviceBStore = LocalCloudBackupStore(InMemoryCloudKeyValueStore())
        val repoB = SupabaseCloudSaveRepository(
            localStore = deviceBStore,
            httpClient = client,
            urlProvider = { "https://demo.supabase.co" },
            anonKeyProvider = { "sb_publishable_demo" },
            nowProvider = { clockMs }
        )
        val wrongPin = repoB.loginAndRecover("Aldric", "0000")
        assertTrue("Wrong PIN must not recover hero", wrongPin is CloudAuthResult.Failure)

        val recovered = repoB.loginAndRecover("Aldric", "1357")
        assertTrue(recovered is CloudAuthResult.Success)
        val recoveredData = (recovered as CloudAuthResult.Success).saveData
        assertEquals(7, recoveredData.level)
        assertEquals(950, recoveredData.gold)
        assertEquals(-1, recoveredData.floor)
        assertEquals(18, recoveredData.x)

        // Device B goes offline, levels up to Level 9, saves locally -> pendingCloudSync = true
        server.offline = true
        clockMs += 20_000L
        val lvl9Offline = recoveredData.copy(level = 9, gold = 1400)
        val offlineOutcome = repoB.saveProgress(recovered.session, lvl9Offline)
        assertEquals(CloudSaveOutcome.SAVED_LOCAL_OFFLINE, offlineOutcome)
        assertTrue(deviceBStore.getLocalBackupByPlayerId(recovered.session.playerId)!!.pendingCloudSync)

        // Signal returns -> syncPendingOfflineBackup pushes Level 9 because its timestamp is newer!
        server.offline = false
        val synced = repoB.syncPendingOfflineBackup(recovered.session)
        assertEquals(CloudSaveOutcome.SYNCED_CLOUD, synced)
        assertFalse(deviceBStore.getLocalBackupByPlayerId(recovered.session.playerId)!!.pendingCloudSync)

        // Auto-login on Device A pulls the newer Level 9 cloud progress instead of older Level 7 local cache!
        val autoLoginA = repoA.autoLoginCachedSession()
        assertTrue(autoLoginA is CloudAuthResult.Success)
        assertEquals(9, (autoLoginA as CloudAuthResult.Success).saveData.level)
        assertEquals(1400, autoLoginA.saveData.gold)
    }

    @Test
    fun serverEnforcesPinLockoutReservedAdminAndRefusesForeignOverwrites() = runBlocking {
        val server = FakeSupabasePlayersServer()
        val client = OkHttpClient.Builder().addInterceptor(server).build()
        fun repo() = SupabaseCloudSaveRepository(
            localStore = LocalCloudBackupStore(InMemoryCloudKeyValueStore()),
            httpClient = client,
            urlProvider = { "https://demo.supabase.co" },
            anonKeyProvider = { "sb_publishable_demo" }
        )

        val owner = repo().createCharacter("Lyra", "2468") { GameEngine.toSaveData(GameEngine.createNewWorld(it)) }
        assertTrue(owner is CloudAuthResult.Success)
        val ownerSession = (owner as CloudAuthResult.Success).session
        assertFalse("New heroes are never admin", ownerSession.isAdmin)

        // An attacker who knows Lyra's public player id cannot overwrite her save without the PIN
        val attacker = repo()
        val before = server.rowsById[ownerSession.playerId]!!.getJSONObject("save_data").toString()
        val forged = ownerSession.copy(pinHash = PinHasher.hashPin("Lyra", "0000"))
        val wiped = owner.saveData.copy(level = 1, gold = 0, x = 1, y = 1)
        assertEquals(CloudSaveOutcome.SAVED_LOCAL_OFFLINE, attacker.saveProgress(forged, wiped))
        assertEquals(before, server.rowsById[ownerSession.playerId]!!.getJSONObject("save_data").toString())

        // Five wrong PINs lock the hero, even for the correct PIN afterwards
        repeat(4) { attacker.loginAndRecover("Lyra", "1111") }
        val locked = attacker.loginAndRecover("Lyra", "2468")
        assertTrue(locked is CloudAuthResult.Failure)
        assertEquals(SupabaseCloudSaveRepository.TOO_MANY_ATTEMPTS_MESSAGE, (locked as CloudAuthResult.Failure).message)

        // "Admin" is reserved until the owner opens the claim window
        val denied = repo().createCharacter("Admin", "1119") { GameEngine.toSaveData(GameEngine.createNewWorld(it)) }
        assertTrue(denied is CloudAuthResult.Failure)

        server.adminClaimOpen = true
        val claimed = repo().createCharacter("admin", "5555") { GameEngine.toSaveData(GameEngine.createNewWorld(it)) }
        assertTrue(claimed is CloudAuthResult.Success)
        claimed as CloudAuthResult.Success
        assertTrue(claimed.session.isAdmin)
        assertTrue(claimed.saveData.isAdmin)
        assertEquals("Admin", claimed.saveData.name)

        // Logging in elsewhere picks up admin status from the server, not from the name
        val adminLogin = repo().loginAndRecover("Admin", "5555")
        assertTrue(adminLogin is CloudAuthResult.Success)
        assertTrue((adminLogin as CloudAuthResult.Success).saveData.isAdmin)
        val normalLogin = repo().loginAndRecover("Lyra", "2468")
        assertTrue(normalLogin is CloudAuthResult.Failure) // still locked
    }
}
