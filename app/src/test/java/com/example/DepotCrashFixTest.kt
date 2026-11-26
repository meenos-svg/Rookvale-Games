package com.example

import com.example.game.cloud.CloudKeyValueStore
import com.example.game.cloud.CloudSaveOutcome
import com.example.game.cloud.CloudSavePayloadCodec
import com.example.game.cloud.CloudSession
import com.example.game.cloud.LocalCloudBackupStore
import com.example.game.cloud.SupabaseCloudSaveRepository
import com.example.game.engine.GameEngine
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemStack
import com.example.game.model.Vocation
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
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

private class DepotTestKeyValueStore : CloudKeyValueStore {
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

private class RecordingSupabaseInterceptor : Interceptor {
    val postedPayloads = mutableListOf<JSONObject>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.method == "POST") {
            val buf = Buffer()
            req.body?.writeTo(buf)
            postedPayloads.add(JSONObject(buf.readUtf8()))
            return Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{\"status\":\"ok\",\"is_admin\":false}".toResponseBody("application/json".toMediaType()))
                .build()
        }
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("[]".toResponseBody("application/json".toMediaType()))
            .build()
    }
}

class DepotCrashFixTest {

    @Test
    fun rapidDepositAndWithdrawKeepImmutableSnapshotsAndRemoveDepletedStacks() {
        val initial = GameEngine.createNewWorld("Idemon", Vocation.KNIGHT).copy(
            backpack = listOf(
                ItemStack("health_potion", 3),
                ItemStack("short_sword", 1),
                ItemStack("short_sword", 1),
                ItemStack("blank_rune", 2)
            ),
            toolBag = listOf(ItemStack("fishing_rod", 1)),
            depot = emptyList()
        )
        val initialBackpackSnapshot = initial.backpack
        val initialDepotSnapshot = initial.depot

        // Rapidly deposit all 3 health potions one by one + an extra tap after depleted
        var state = initial
        repeat(4) {
            state = GameEngine.depositItem(state, "health_potion")
        }

        // Prior state references must remain completely untouched
        assertEquals(4, initialBackpackSnapshot.size)
        assertEquals(3, initialBackpackSnapshot.first().count)
        assertTrue(initialDepotSnapshot.isEmpty())
        assertNotSame(initialBackpackSnapshot, state.backpack)
        assertNotSame(initialDepotSnapshot, state.depot)

        // Depleted health_potion stack must be removed from backpack and merged in depot
        assertFalse(state.backpack.any { it.itemId == "health_potion" })
        assertEquals(3, state.depot.first { it.itemId == "health_potion" }.count)
        assertTrue(state.backpack.all { it.count > 0 })
        assertTrue(state.depot.all { it.count > 0 })

        // Deposit duplicate non-stackable short swords and tool bag item
        state = GameEngine.depositItem(state, "short_sword")
        state = GameEngine.depositItem(state, "short_sword")
        state = GameEngine.depositItem(state, "fishing_rod")

        assertFalse(state.backpack.any { it.itemId == "short_sword" })
        assertEquals(2, state.depot.count { it.itemId == "short_sword" })
        assertFalse(state.toolBag.any { it.itemId == "fishing_rod" })
        assertEquals(1, state.depot.count { it.itemId == "fishing_rod" })

        // Withdraw all 3 health potions + an extra tap after depleted
        repeat(4) {
            state = GameEngine.withdrawItem(state, "health_potion")
        }
        assertFalse(state.depot.any { it.itemId == "health_potion" })
        assertEquals(3, state.backpack.first { it.itemId == "health_potion" }.count)
        val usedCap = state.usedCapOz()
        assertTrue(usedCap.isFinite() && usedCap >= 0.0)
    }

    @Test
    fun depositAndWithdrawHandleMalformedStacksAndCapLimitsGracefully() {
        val swordWeight = ItemCatalog.get("short_sword")!!.weightOz
        val stateWithMalformed = GameEngine.createNewWorld("Idemon", Vocation.MAGE).copy(
            maxCapOz = swordWeight * 0.5, // Too low to withdraw a short_sword
            backpack = listOf(
                ItemStack("", 2),
                ItemStack("health_potion", 0),
                ItemStack("health_potion", -5),
                ItemStack("blank_rune", 2)
            ),
            depot = listOf(
                ItemStack("short_sword", 1),
                ItemStack("unknown_legacy_item", 1),
                ItemStack("health_potion", 0)
            )
        )

        // Blank / unknown item IDs must not crash
        val afterBlank = GameEngine.depositItem(stateWithMalformed, "")
        assertTrue(afterBlank.statusLog.last().contains("Warning"))

        val afterUnknown = GameEngine.withdrawItem(stateWithMalformed, "unknown_legacy_item")
        assertTrue(afterUnknown.statusLog.last().contains("Warning"))

        // Withdrawing an item heavier than available Cap must block cleanly without crashing
        val afterTooHeavy = GameEngine.withdrawItem(stateWithMalformed, "short_sword")
        assertTrue(afterTooHeavy.statusLog.last().contains("Not enough Cap"))
        assertEquals(1, afterTooHeavy.depot.count { it.itemId == "short_sword" })

        // Depositing a valid item also strips malformed/depleted entries cleanly
        val afterValidDeposit = GameEngine.depositItem(stateWithMalformed, "blank_rune")
        assertEquals(1, afterValidDeposit.backpack.first { it.itemId == "blank_rune" }.count)
        assertTrue(afterValidDeposit.backpack.all { it.itemId.isNotBlank() && it.count > 0 })
        assertTrue(afterValidDeposit.depot.all { it.itemId.isNotBlank() && it.count > 0 })
    }

    @Test
    fun cloudPayloadDecoderHandlesMissingNullAndMalformedDepotItemsSafely() {
        val baseWorld = GameEngine.createNewWorld("Idemon", Vocation.KNIGHT)
        val encoded = CloudSavePayloadCodec.encode(GameEngine.toSaveData(baseWorld))

        // Case 1: depotItems completely missing from legacy save_data JSON
        encoded.remove("depotItems")
        val decodedMissing = CloudSavePayloadCodec.decode(encoded, "slot_1", "Idemon")
        assertTrue(decodedMissing.depot.isEmpty())

        // Case 2: depotItems explicitly JSONObject.NULL
        encoded.put("depotItems", JSONObject.NULL)
        val decodedNull = CloudSavePayloadCodec.decode(encoded, "slot_1", "Idemon")
        assertTrue(decodedNull.depot.isEmpty())

        // Case 3: depotItems contains mix of valid, zero-count, blank-id, and corrupt entries
        val dirtyArray = JSONArray().apply {
            put(JSONObject().put("id", "blank_rune").put("count", 5))
            put(JSONObject().put("id", "health_potion").put("count", 0))
            put(JSONObject().put("id", "").put("count", 3))
            put("not_an_object")
            put(JSONObject().put("id", "short_sword").put("count", 1))
        }
        encoded.put("depotItems", dirtyArray)
        val decodedCleaned = CloudSavePayloadCodec.decode(encoded, "slot_1", "Idemon")
        assertEquals(2, decodedCleaned.depot.size)
        assertEquals(ItemStack("blank_rune", 5), decodedCleaned.depot[0])
        assertEquals(ItemStack("short_sword", 1), decodedCleaned.depot[1])
    }

    @Test
    fun preEncodedMainThreadSnapshotPersistsExactDepotStateToCloud() = runBlocking {
        val interceptor = RecordingSupabaseInterceptor()
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val localStore = LocalCloudBackupStore(DepotTestKeyValueStore())
        val repo = SupabaseCloudSaveRepository(
            localStore = localStore,
            httpClient = client,
            urlProvider = { "https://demo.supabase.co" },
            anonKeyProvider = { "sb_publishable_demo" },
            nowProvider = { 1_765_000_000_000L }
        )
        val session = CloudSession(playerId = "player_1", characterName = "Idemon", pinHash = "abc123")

        val worldAfterDeposit = GameEngine.createNewWorld("Idemon", Vocation.MAGE).copy(
            backpack = listOf(ItemStack("blank_rune", 4)),
            depot = emptyList()
        ).let { GameEngine.depositItem(it, "blank_rune") }

        val saveData = GameEngine.toSaveData(worldAfterDeposit)
        val preEncodedSnapshot = CloudSavePayloadCodec.encode(saveData)

        val outcome = repo.saveProgress(
            session = session,
            saveData = saveData,
            preEncodedSaveJson = preEncodedSnapshot
        )

        assertEquals(CloudSaveOutcome.SYNCED_CLOUD, outcome)
        assertEquals(1, interceptor.postedPayloads.size)
        val uploadedSaveData = interceptor.postedPayloads.first().getJSONObject("p_save_data")
        val uploadedDepot = uploadedSaveData.getJSONArray("depotItems")
        assertEquals(1, uploadedDepot.length())
        assertEquals("blank_rune", uploadedDepot.getJSONObject(0).getString("id"))
        assertEquals(1, uploadedDepot.getJSONObject(0).getInt("count"))
    }
}
