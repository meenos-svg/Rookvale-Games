package com.example

import com.example.game.cloud.AdminAccountPolicy
import com.example.game.cloud.AdminAuditAction
import com.example.game.cloud.AdminAuditEntry
import com.example.game.cloud.AdminAuditLogRepository
import com.example.game.cloud.AdminAuditSanitizer
import com.example.game.cloud.AdminAuditSyncStatus
import com.example.game.cloud.AdminAuditUiState
import com.example.game.cloud.AdminTeleportCatalog
import com.example.game.cloud.AdminWarpCategory
import com.example.game.cloud.CloudKeyValueStore
import com.example.game.cloud.CloudSession
import com.example.game.cloud.LocalAdminAuditQueueStore
import com.example.game.cloud.PinHasher
import com.example.game.engine.GameEngine
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.Vocation
import com.example.game.multiplayer.MultiplayerConfig
import com.example.game.world.WorldBiomeBlueprint
import com.example.game.world.WorldMaps
import kotlinx.coroutines.runBlocking
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class InMemoryAuditKvStore : CloudKeyValueStore {
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
class AdminAuditLogAndTeleportTest {

    @Test
    fun adminTeleportWarpsAcrossLandmarksAndDungeonFloorsClampsBoundsAndBlocksNonAdmin() {
        val admin = GameEngine.createNewWorld("Admin", Vocation.ADVENTURER, isAdmin = true).copy(
            walkPath = listOf(1397 to 592, 1398 to 592),
            targetMonsterId = 77,
            autoFollowTarget = true,
            monsters = listOf(
                MonsterEntity(
                    id = 77,
                    type = MonsterType.CAVE_RAT,
                    x = 1398,
                    y = 592,
                    spawnX = 1398,
                    spawnY = 592,
                    floor = 0,
                    hp = 20,
                    maxHp = 20,
                    targetPlayerId = "local"
                )
            )
        )

        // 1. Warp to Dungeon Floor -5 Grimmaw Quarry
        val grimmawPreset = AdminTeleportCatalog.dungeonFloors.first { it.floor == -5 }
        val inDungeon = GameEngine.teleportAdmin(
            state = admin,
            targetFloor = grimmawPreset.floor,
            targetX = grimmawPreset.x,
            targetY = grimmawPreset.y,
            destinationLabel = grimmawPreset.label,
            nowMs = 1000L
        )
        assertEquals(-5, inDungeon.floor)
        assertTrue(WorldMaps.getFloor(-5).tileAt(inDungeon.playerX, inDungeon.playerY).walkable)
        assertTrue("Walk path must clear on teleport", inDungeon.walkPath.isEmpty())
        assertNull("Target monster must clear on teleport", inDungeon.targetMonsterId)
        assertFalse(inDungeon.autoFollowTarget)
        assertEquals(inDungeon.effectiveMaxHp(), inDungeon.hp)

        // 2. Warp to Overland Mirewatch Fortress and verify region updates
        val mirewatchPreset = AdminTeleportCatalog.overlandLandmarks.first { it.id == "mirewatch" }
        val atMirewatch = GameEngine.teleportAdmin(
            state = inDungeon,
            targetFloor = mirewatchPreset.floor,
            targetX = mirewatchPreset.x,
            targetY = mirewatchPreset.y,
            destinationLabel = mirewatchPreset.label,
            nowMs = 2000L
        )
        assertEquals(0, atMirewatch.floor)
        assertTrue(WorldMaps.getFloor(0).tileAt(atMirewatch.playerX, atMirewatch.playerY).walkable)
        assertEquals(WorldMaps.regionForGlobal(atMirewatch.playerX, atMirewatch.playerY).regionId, atMirewatch.regionId)

        // 3. Invalid floor & out-of-bounds coords clamp safely onto walkable tiles
        val clamped = GameEngine.teleportAdmin(
            state = atMirewatch,
            targetFloor = -99,
            targetX = 999_999,
            targetY = -500,
            nowMs = 3000L
        )
        assertEquals(0, clamped.floor)
        assertTrue(WorldMaps.getFloor(0).tileAt(clamped.playerX, clamped.playerY).walkable)

        // 4. Non-Admin player is strictly blocked from teleportAdmin
        val normalPlayer = GameEngine.createNewWorld("Roland", Vocation.ADVENTURER, isAdmin = false)
        val blocked = GameEngine.teleportAdmin(normalPlayer, -5, 24, 40, "Blocked")
        assertEquals(normalPlayer.floor, blocked.floor)
        assertEquals(normalPlayer.playerX, blocked.playerX)
        assertEquals(normalPlayer.playerY, blocked.playerY)
    }

    @Test
    fun expandedAdminWarpCatalogCoversAllSettlementsCampsAndDungeonSafeZonesWithValidLandings() {
        assertEquals(9, AdminTeleportCatalog.townsAndEmpires.size)
        assertEquals(6, AdminTeleportCatalog.overlandCamps.size)
        assertEquals(15, AdminTeleportCatalog.dungeonCamps.size)
        assertEquals(15, AdminTeleportCatalog.overlandLandmarks.size)
        assertEquals(15, AdminTeleportCatalog.dungeonFloors.size)
        assertEquals(30, AdminTeleportCatalog.allPresets.size)

        // Category filter lookup matches catalog lists exactly
        assertEquals(AdminTeleportCatalog.allPresets, AdminTeleportCatalog.presetsFor(null))
        assertEquals(AdminTeleportCatalog.townsAndEmpires, AdminTeleportCatalog.presetsFor(AdminWarpCategory.TOWNS_EMPIRES))
        assertEquals(AdminTeleportCatalog.overlandCamps, AdminTeleportCatalog.presetsFor(AdminWarpCategory.OVERLAND_CAMPS))
        assertEquals(AdminTeleportCatalog.dungeonCamps, AdminTeleportCatalog.presetsFor(AdminWarpCategory.DUNGEON_CAMPS))

        // Every preset has a unique ID and a non-blank level range badge
        val ids = AdminTeleportCatalog.allPresets.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        AdminTeleportCatalog.allPresets.forEach { preset ->
            assertTrue("Missing level range badge on ${preset.id}", preset.levelRangeLabel.startsWith("Lv "))
            if (preset.isDungeon) {
                assertTrue("Dungeon camp badge should show PZ on ${preset.id}", preset.levelRangeLabel.contains("PZ"))
            }
        }

        // Every biome settlement & forward camp in WorldBiomeBlueprint.secondarySettlements is represented
        WorldBiomeBlueprint.secondarySettlements.forEach { settlement ->
            val matched = AdminTeleportCatalog.overlandLandmarks.firstOrNull {
                it.floor == 0 && it.x == settlement.centerX && it.y == settlement.centerY
            }
            assertNotNull(
                "Admin Warp catalog must include settlement '${settlement.name}' (${settlement.centerX},${settlement.centerY})",
                matched
            )
        }

        // Every underground forward camp in WorldMaps.undergroundCamps lands inside its safe PZ
        WorldMaps.undergroundCamps.forEach { camp ->
            val matched = AdminTeleportCatalog.dungeonCamps.firstOrNull { it.floor == camp.floorId }
            assertNotNull("Admin Warp catalog must include dungeon camp for floor ${camp.floorId}", matched)
            assertTrue(
                "Preset ${matched!!.id} X=${matched.x} outside camp ${camp.minX}..${camp.maxX}",
                matched.x in camp.minX..camp.maxX
            )
            assertTrue(
                "Preset ${matched.id} Y=${matched.y} outside camp ${camp.minY}..${camp.maxY}",
                matched.y in camp.minY..camp.maxY
            )
            assertTrue(
                "Preset ${matched.id} must land inside dungeon camp Protection Zone",
                WorldMaps.getFloor(camp.floorId).isProtectionZone(matched.x, matched.y)
            )
        }

        // Every single one of the 21 presets warps an Admin onto a walkable tile on the target floor
        var admin = GameEngine.createNewWorld("Admin", Vocation.ADVENTURER, isAdmin = true)
        AdminTeleportCatalog.allPresets.forEachIndexed { idx, preset ->
            admin = GameEngine.teleportAdmin(
                state = admin,
                targetFloor = preset.floor,
                targetX = preset.x,
                targetY = preset.y,
                destinationLabel = preset.label,
                nowMs = 10_000L + idx * 100L
            )
            assertEquals("Floor mismatch after warping to ${preset.id}", preset.floor, admin.floor)
            assertTrue(
                "Landing tile (${admin.playerX},${admin.playerY}) on floor ${admin.floor} for ${preset.id} must be walkable",
                WorldMaps.getFloor(admin.floor).tileAt(admin.playerX, admin.playerY).walkable
            )
        }
    }

    @Test
    fun auditLogQueuesOfflineSanitizesCredentialsEnforcesAdminAuthAndFiltersCleanly() = runBlocking {
        val kv = InMemoryAuditKvStore()
        var clock = 1_732_500_000_000L
        val offlineRepo = AdminAuditLogRepository(
            localStore = LocalAdminAuditQueueStore(kv),
            urlProvider = { "" },
            anonKeyProvider = { "" },
            nowProvider = { clock }
        )

        val validAdminSession = CloudSession(
            playerId = "admin-slot-1",
            characterName = AdminAccountPolicy.ADMIN_CANONICAL_NAME,
            pinHash = PinHasher.hashPin(AdminAccountPolicy.ADMIN_CANONICAL_NAME, "5555"),
            isAdmin = true // server-verified
        )
        val spoofedSession = CloudSession(
            playerId = "fake-admin",
            characterName = "Admin",
            pinHash = PinHasher.hashPin("Admin", "0000"),
            isAdmin = false // named "Admin" but not verified by the server
        )

        // Non-Admin flag or spoofed session must be rejected cleanly
        assertNull(
            offlineRepo.recordEvent(
                isAdminFlag = false,
                session = validAdminSession,
                action = AdminAuditAction.LEVEL_CHANGE,
                targetName = "Admin",
                summary = "Blocked non-admin"
            )
        )
        assertNull(
            offlineRepo.recordEvent(
                isAdminFlag = true,
                session = spoofedSession,
                action = AdminAuditAction.GRANT_GEAR,
                targetName = "Admin",
                summary = "Blocked bad hash"
            )
        )
        assertTrue(offlineRepo.loadLocalEntries().isEmpty())

        // Record offline events with sensitive keys & control characters to verify sanitizer
        val res1 = offlineRepo.recordEvent(
            isAdminFlag = true,
            session = validAdminSession,
            action = AdminAuditAction.LEVEL_CHANGE,
            targetName = "Admin\u0007",
            summary = "Set level Lv 1 -> Lv 120\n",
            details = mapOf(
                "from" to "Lv 1",
                "to" to "Lv 120",
                "pin" to "5555",
                "pinHash" to "deadbeef",
                "apiKey" to "should-not-log",
                "secretToken" to "nope"
            ),
            eventId = "evt-level-1"
        )
        assertNotNull(res1)
        assertFalse(res1!!.syncedFromServer)
        assertEquals(1, res1.pendingQueueCount)
        val stored1 = res1.entries.first()
        assertEquals(AdminAuditSyncStatus.QUEUED, stored1.syncStatus)
        assertEquals("Admin", stored1.targetName)
        assertEquals("Set level Lv 1 -> Lv 120", stored1.summary)
        assertEquals("Lv 1", stored1.details["from"])
        assertEquals("Lv 120", stored1.details["to"])
        assertFalse("Credentials must never be logged", stored1.details.containsKey("pin"))
        assertFalse("PIN hashes must never be logged", stored1.details.containsKey("pinHash"))
        assertFalse("API keys must never be logged", stored1.details.containsKey("apiKey"))
        assertFalse("Secret tokens must never be logged", stored1.details.containsKey("secretToken"))

        clock += 5_000L
        offlineRepo.recordEvent(
            isAdminFlag = true,
            session = validAdminSession,
            action = AdminAuditAction.TELEPORT,
            targetName = "Admin",
            summary = "Teleported to F-5 Grimmaw",
            details = mapOf("from" to "F0 (1396,592)", "to" to "F-5 (24,40)"),
            eventId = "evt-warp-2"
        )

        // Verify filter logic on AdminAuditUiState
        val uiState = AdminAuditUiState(
            entries = offlineRepo.loadLocalEntries(),
            selectedFilter = AdminAuditAction.TELEPORT
        )
        assertEquals(2, uiState.entries.size)
        assertEquals(1, uiState.filteredEntries.size)
        assertEquals("evt-warp-2", uiState.filteredEntries.first().eventId)
    }

    @Test
    fun auditLogFlushesOfflineQueueToSupabaseAndMergesServerRowsAppendOnly() = runBlocking {
        val kv = InMemoryAuditKvStore()
        val validAdminSession = CloudSession(
            playerId = "admin-slot-1",
            characterName = AdminAccountPolicy.ADMIN_CANONICAL_NAME,
            pinHash = PinHasher.hashPin(AdminAccountPolicy.ADMIN_CANONICAL_NAME, "5555"),
            isAdmin = true // server-verified
        )
        val serverRows = mutableListOf<JSONObject>()
        val observedMethods = mutableListOf<String>()
        val observedUrls = mutableListOf<String>()

        val mockHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                observedMethods += req.method
                observedUrls += req.url.toString()
                when (req.method) {
                    "POST" -> {
                        val buf = okio.Buffer()
                        req.body?.writeTo(buf)
                        val body = JSONObject(buf.readUtf8())
                        assertEquals("admin-slot-1", body.getString("p_player_id"))
                        val arr = body.getJSONArray("p_entries")
                        for (i in 0 until arr.length()) {
                            val row = arr.getJSONObject(i).put("actor_name", "Admin")
                            val id = row.getString("event_id")
                            if (serverRows.none { it.getString("event_id") == id }) {
                                serverRows += row
                            }
                        }
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("{\"status\":\"ok\"}".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    "GET" -> {
                        val out = JSONArray()
                        serverRows.reversed().forEach { out.put(it) }
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(out.toString().toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    else -> error("Unexpected non-append-only HTTP method: ${req.method}")
                }
            }
            .build()

        // Seed 1 pre-existing server-side row from another device
        serverRows += JSONObject().apply {
            put("event_id", "evt-remote-0")
            put("created_at", "2026-11-24T10:00:00.000Z")
            put("action_type", "GRANT_GEAR")
            put("actor_name", "Admin")
            put("target_name", "Admin")
            put("summary", "Granted Tier 5 All Classes Armory")
            put("details", JSONObject().put("classification", "ALL"))
        }

        // 1. Queue an entry while offline
        val offlineRepo = AdminAuditLogRepository(
            localStore = LocalAdminAuditQueueStore(kv),
            urlProvider = { "" },
            anonKeyProvider = { "" },
            nowProvider = { 1_795_520_000_000L }
        )
        offlineRepo.recordEvent(
            isAdminFlag = true,
            session = validAdminSession,
            action = AdminAuditAction.SPECTATOR_TOGGLE,
            targetName = "Admin",
            summary = "Spectator Mode ENABLED (hidden)",
            eventId = "evt-offline-1"
        )
        assertEquals(AdminAuditSyncStatus.QUEUED, offlineRepo.loadLocalEntries().first().syncStatus)

        // 2. Come back online and refresh -> must flush queued row & merge server rows without duplicates
        assertEquals(
            "https://example.supabase.co/rest/v1/admin_audit_logs",
            MultiplayerConfig.restAdminAuditLogsBaseUrl("https://example.supabase.co", "test-anon-key-123")
        )
        val onlineRepo = AdminAuditLogRepository(
            localStore = LocalAdminAuditQueueStore(kv),
            httpClient = mockHttp,
            urlProvider = { "https://example.supabase.co" },
            anonKeyProvider = { "test-anon-key-123" },
            nowProvider = { 1_795_520_010_000L }
        )
        val refreshed = onlineRepo.refreshFromServer(isAdminFlag = true, session = validAdminSession)
        assertNotNull(refreshed)
        assertTrue(refreshed!!.syncedFromServer)
        assertEquals(0, refreshed.pendingQueueCount)
        assertEquals(2, refreshed.entries.size)
        assertTrue(refreshed.entries.all { it.syncStatus == AdminAuditSyncStatus.SYNCED })
        assertTrue(refreshed.entries.any { it.eventId == "evt-offline-1" && it.actionType == AdminAuditAction.SPECTATOR_TOGGLE })
        assertTrue(refreshed.entries.any { it.eventId == "evt-remote-0" && it.actionType == AdminAuditAction.GRANT_GEAR })

        // Verify strictly append-only HTTP methods (POST & GET only — zero PATCH/PUT/DELETE)
        assertTrue(observedMethods.all { it == "POST" || it == "GET" })
        assertTrue(observedUrls.any { it.endsWith("/rest/v1/rpc/rv_append_admin_audit") })
        assertTrue(observedUrls.any { it.contains("order=created_at.desc") })

        // Verify sanitizer caps long input cleanly
        assertEquals(10, AdminAuditSanitizer.cleanText("12345678901234567890", 10).length)
    }
}
