package com.example

import com.example.game.cloud.CloudKeyValueStore
import com.example.game.cloud.IsoTimestampUtil
import com.example.game.cloud.LocalCloudBackupStore
import com.example.game.cloud.SupabaseCloudSaveRepository
import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.Direction
import com.example.game.model.Vocation
import com.example.game.model.normalizeRegionId
import com.example.game.multiplayer.ChatChannel
import com.example.game.multiplayer.LocalPlayerPresencePayload
import com.example.game.multiplayer.RealtimeMultiplayerManager
import com.example.game.multiplayer.RemotePlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class MapKeyValueStore : CloudKeyValueStore {
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiplayerVisibilityTest {

    @Test
    fun normalizeRegionIdUnifiesLegacyAndBlankNamesToRookvaleValley() {
        assertEquals(DEFAULT_REGION_ID, normalizeRegionId(null))
        assertEquals(DEFAULT_REGION_ID, normalizeRegionId(""))
        assertEquals(DEFAULT_REGION_ID, normalizeRegionId("   "))
        assertEquals(DEFAULT_REGION_ID, normalizeRegionId("Rookvale"))
        assertEquals(DEFAULT_REGION_ID, normalizeRegionId("rookvale_city"))
        assertEquals(DEFAULT_REGION_ID, normalizeRegionId("rookvale_overland"))
        assertEquals(DEFAULT_REGION_ID, normalizeRegionId("overland"))
        assertEquals(DEFAULT_REGION_ID, normalizeRegionId("main"))
        assertEquals(DEFAULT_REGION_ID, normalizeRegionId("rookvale_valley"))
        assertEquals("frozen_citadel", normalizeRegionId("Frozen_Citadel"))
    }

    @Test
    fun fetchRecentOnlinePlayersExcludesSelfNormalizesRegionAndParsesFacing() = runBlocking {
        val nowMs = 1_750_000_000_000L
        val recentIso = IsoTimestampUtil.formatUtc(nowMs - 10_000L)
        val staleIso = IsoTimestampUtil.formatUtc(nowMs - 120_000L)

        val rows = JSONArray().apply {
            // Self by UUID (must be excluded)
            put(
                JSONObject().apply {
                    put("player_id", "uuid-local")
                    put("character_name", "Roland")
                    put("updated_at", recentIso)
                    put(
                        "save_data",
                        JSONObject().apply {
                            put("floor", 0); put("x", 200); put("y", 200); put("regionId", "rookvale_valley")
                        }
                    )
                }
            )
            // Self duplicate by case-insensitive name (must be excluded)
            put(
                JSONObject().apply {
                    put("player_id", "uuid-local-alt")
                    put("character_name", "roland")
                    put("updated_at", recentIso)
                    put(
                        "save_data",
                        JSONObject().apply {
                            put("floor", 0); put("x", 200); put("y", 200); put("regionId", "rookvale_valley")
                        }
                    )
                }
            )
            // Active friend with legacy regionId ("rookvale_city") and facing WEST
            put(
                JSONObject().apply {
                    put("player_id", "uuid-lyra")
                    put("character_name", "Lyra")
                    put("updated_at", recentIso)
                    put(
                        "save_data",
                        JSONObject().apply {
                            put("floor", 0)
                            put("x", 204)
                            put("y", 198)
                            put("level", 18)
                            put("vocationName", "PALADIN")
                            put("hp", 260)
                            put("maxHp", 290)
                            put("regionId", "rookvale_city")
                            put("facing", "WEST")
                        }
                    )
                }
            )
            // Stale offline player older than 60s (must be excluded)
            put(
                JSONObject().apply {
                    put("player_id", "uuid-stale")
                    put("character_name", "OldHero")
                    put("updated_at", staleIso)
                    put(
                        "save_data",
                        JSONObject().apply {
                            put("floor", 0); put("x", 195); put("y", 195); put("regionId", "")
                        }
                    )
                }
            )
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val req = chain.request()
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(rows.toString().toResponseBody("application/json".toMediaType()))
                        .build()
                }
            )
            .build()

        val repo = SupabaseCloudSaveRepository(
            localStore = LocalCloudBackupStore(MapKeyValueStore()),
            httpClient = client,
            urlProvider = { "https://demo.supabase.co" },
            anonKeyProvider = { "sb_publishable_demo_key" },
            nowProvider = { nowMs }
        )

        val fetched = repo.fetchRecentOnlinePlayers(
            sinceEpochMs = nowMs - 60_000L,
            excludePlayerId = "uuid-local",
            excludeCharacterName = "Roland"
        )
        assertEquals(1, fetched.size)
        val lyra = fetched.first()
        assertEquals("uuid-lyra", lyra.playerId)
        assertEquals("Lyra", lyra.name)
        assertEquals(DEFAULT_REGION_ID, lyra.regionId)
        assertEquals(Direction.WEST, lyra.direction)
        assertEquals(204, lyra.x)
        assertEquals(198, lyra.y)
        assertEquals(18, lyra.level)
        assertEquals(Vocation.PALADIN, lyra.vocation)
    }

    @Test
    fun polledFallbackAndRealtimePresenceMergeCleanlyAndExpireStalePeers() {
        var nowMs = 1_750_000_000_000L
        val mgr = RealtimeMultiplayerManager(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            nowProvider = { nowMs },
            animClockProvider = { nowMs }
        )
        mgr.connect(
            LocalPlayerPresencePayload(
                playerId = "p_local",
                name = "Roland",
                level = 12,
                vocation = Vocation.KNIGHT,
                tunicColorIndex = 0,
                hairColorIndex = 0,
                x = 200,
                y = 200,
                floor = 0,
                regionId = "rookvale",
                hp = 240,
                maxHp = 240,
                joinedAt = nowMs,
                lastSeenMs = nowMs
            )
        )
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"phx_reply","payload":{"status":"ok"},"ref":"1"}"""
        )

        // 1. Polled player appears even before any WebSocket packet arrives (legacy region normalized)
        val polledLyra = RemotePlayerState(
            playerId = "p_lyra",
            name = "Lyra",
            level = 15,
            vocation = Vocation.PALADIN,
            tunicColorIndex = 2,
            hairColorIndex = 1,
            x = 205,
            y = 200,
            floor = 0,
            regionId = "rookvale_city",
            hp = 220,
            maxHp = 250,
            joinedAt = nowMs - 5_000L,
            lastSeenMs = nowMs - 2_000L
        )
        val polledOtherRegion = polledLyra.copy(
            playerId = "p_far_region",
            name = "FrostMage",
            regionId = "frozen_citadel"
        )
        mgr.mergePolledPlayers(
            polled = listOf(polledLyra, polledOtherRegion),
            localFloor = 0,
            localRegionId = DEFAULT_REGION_ID,
            localPlayerId = "p_local",
            localPlayerName = "Roland"
        )

        assertEquals(1, mgr.uiState.value.remotePlayers.size)
        assertEquals(2, mgr.uiState.value.floorOnlineCount)
        assertEquals(205, mgr.uiState.value.remotePlayers.first().x)

        // 2. Fresher Realtime broadcast moves Lyra to (206, 200) -> older poll must NOT revert her back to 205!
        nowMs += 1_000L
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"broadcast","payload":{"event":"player_state","payload":{"playerId":"p_lyra","name":"Lyra","level":15,"vocation":"PALADIN","x":206,"y":200,"floor":0,"regionId":"rookvale_valley","facing":"EAST","hp":220,"maxHp":250,"lastSeen":$nowMs}}}"""
        )
        mgr.mergePolledPlayers(
            polled = listOf(polledLyra), // older timestamp (nowMs - 3000)
            localFloor = 0,
            localRegionId = DEFAULT_REGION_ID,
            localPlayerId = "p_local",
            localPlayerName = "Roland"
        )
        val afterStalePoll = mgr.uiState.value.remotePlayers.first { it.playerId == "p_lyra" }
        assertEquals("Fresher Realtime position must not be reverted by older poll", 206, afterStalePoll.x)
        assertEquals(Direction.EAST, afterStalePoll.direction)

        // 3. Polled-only player (p_bob) arrives and later expires when omitted from poll
        val polledBob = polledLyra.copy(playerId = "p_bob", name = "Bob", x = 210, y = 200, lastSeenMs = nowMs)
        mgr.mergePolledPlayers(
            polled = listOf(polledBob),
            localFloor = 0,
            localRegionId = DEFAULT_REGION_ID,
            localPlayerId = "p_local",
            localPlayerName = "Roland"
        )
        assertEquals(2, mgr.uiState.value.remotePlayers.size)
        assertTrue(mgr.uiState.value.remotePlayers.any { it.playerId == "p_bob" })

        // Empty poll removes polled-only Bob while keeping Realtime Lyra intact
        mgr.mergePolledPlayers(
            polled = emptyList(),
            localFloor = 0,
            localRegionId = DEFAULT_REGION_ID,
            localPlayerId = "p_local",
            localPlayerName = "Roland"
        )
        assertFalse("Expired polled-only player should be removed", mgr.uiState.value.remotePlayers.any { it.playerId == "p_bob" })
        assertTrue("Active Realtime player must remain visible", mgr.uiState.value.remotePlayers.any { it.playerId == "p_lyra" })
    }

    @Test
    fun nearbyEnterAndLeaveNotificationsLogSystemMessagesWithHysteresis() {
        var nowMs = 1_750_000_000_000L
        val mgr = RealtimeMultiplayerManager(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            nowProvider = { nowMs },
            animClockProvider = { nowMs }
        )
        mgr.connect(
            LocalPlayerPresencePayload(
                playerId = "p_local",
                name = "Roland",
                level = 10,
                vocation = Vocation.KNIGHT,
                tunicColorIndex = 0,
                hairColorIndex = 0,
                x = 200,
                y = 200,
                floor = 0,
                regionId = DEFAULT_REGION_ID,
                hp = 200,
                maxHp = 200,
                joinedAt = nowMs,
                lastSeenMs = nowMs
            )
        )
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"phx_reply","payload":{"status":"ok"},"ref":"1"}"""
        )

        // Player enters within 24 tiles -> emits "[Online] Lyra is nearby at (208, 202)." without unread spam
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"broadcast","payload":{"event":"player_state","payload":{"playerId":"p_lyra","name":"Lyra","level":14,"vocation":"MAGE","x":208,"y":202,"floor":0,"regionId":"rookvale_valley","hp":180,"maxHp":180,"lastSeen":$nowMs}}}"""
        )
        val enterMsgs = mgr.uiState.value.chatMessages.filter { it.channel == ChatChannel.SYSTEM && it.text.contains("Lyra is nearby") }
        assertEquals(1, enterMsgs.size)
        assertEquals("[Online] Lyra is nearby at (208, 202).", enterMsgs.first().text)
        assertEquals("System presence notice must not inflate unread badge", 0, mgr.uiState.value.unreadChatCount)

        // Small step to 28 tiles away (inside 32-tile exit hysteresis) -> does NOT spam leave/enter
        nowMs += 500L
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"broadcast","payload":{"event":"player_state","payload":{"playerId":"p_lyra","name":"Lyra","level":14,"vocation":"MAGE","x":228,"y":200,"floor":0,"regionId":"rookvale_valley","hp":180,"maxHp":180,"lastSeen":$nowMs}}}"""
        )
        assertTrue(mgr.uiState.value.chatMessages.none { it.text.contains("left the area") })

        // Walks 40 tiles away (> 32 tiles) -> emits "[Online] Lyra left the area."
        nowMs += 500L
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"broadcast","payload":{"event":"player_state","payload":{"playerId":"p_lyra","name":"Lyra","level":14,"vocation":"MAGE","x":240,"y":200,"floor":0,"regionId":"rookvale_valley","hp":180,"maxHp":180,"lastSeen":$nowMs}}}"""
        )
        val leaveMsg = mgr.uiState.value.chatMessages.lastOrNull { it.channel == ChatChannel.SYSTEM }
        assertNotNull(leaveMsg)
        assertEquals("[Online] Lyra left the area.", leaveMsg!!.text)
    }
}
