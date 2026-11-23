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

    override fun intercept(chain: Interceptor.Chain): Response {
        if (offline) throw IOException("Simulated offline network")
        val req = chain.request()
        val url = req.url

        if (req.method == "POST") {
            val buf = Buffer()
            req.body?.writeTo(buf)
            val bodyJson = JSONObject(buf.readUtf8())
            val pid = bodyJson.getString("player_id")
            rowsById[pid] = bodyJson
            upsertCount++
            return jsonResponse(req, 201, "[]")
        }

        if (req.method == "GET") {
            val idFilter = url.queryParameter("player_id")?.removePrefix("eq.")
            val nameFilter = url.queryParameter("character_name")?.removePrefix("eq.")
            val pinFilter = url.queryParameter("pin_hash")?.removePrefix("eq.")
            val matched = JSONArray()
            for (row in rowsById.values) {
                val okId = idFilter == null || row.optString("player_id") == idFilter
                val okName = nameFilter == null || row.optString("character_name") == nameFilter
                val okPin = pinFilter == null || row.optString("pin_hash") == pinFilter
                if (okId && okName && okPin) matched.put(row)
            }
            return jsonResponse(req, 200, matched.toString())
        }

        return jsonResponse(req, 405, "{}")
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
            gold = 4_250,
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
}
