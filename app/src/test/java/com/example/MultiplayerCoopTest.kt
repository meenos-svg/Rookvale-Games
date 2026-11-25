package com.example

import com.example.game.engine.GameEngine
import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.Direction
import com.example.game.model.EquippedGear
import com.example.game.model.FloatingTextKind
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.ProjectileKind
import com.example.game.model.TileType
import com.example.game.model.Vocation
import com.example.game.multiplayer.ChatChannel
import com.example.game.multiplayer.ConnectionStatus
import com.example.game.multiplayer.LocalPlayerPresencePayload
import com.example.game.multiplayer.MultiplayerConfig
import com.example.game.multiplayer.MultiplayerSanitizer
import com.example.game.multiplayer.PlayerActionState
import com.example.game.multiplayer.RealtimeMultiplayerManager
import com.example.game.multiplayer.RemoteMonsterHitEvent
import com.example.game.multiplayer.RemotePlayerActionFxEvent
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.multiplayer.SyncedMonsterSnapshot
import com.example.game.multiplayer.electFloorHost
import com.example.game.world.WorldMaps
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiplayerCoopTest {

    @Test
    fun multiplayerConfigValidatesUrlsRejectsPlaceholdersAndFallsBackOffline() {
        assertFalse(MultiplayerConfig.isConfigured("", ""))
        assertFalse(MultiplayerConfig.isConfigured("https://example.supabase.co", "YOUR_SUPABASE_ANON_KEY"))
        assertFalse(MultiplayerConfig.isConfigured("http://example.supabase.co", "valid_key_123"))
        assertFalse(MultiplayerConfig.isConfigured("https://localhost", "valid_key_123"))
        assertNull(MultiplayerConfig.realtimeWebSocketUrl("https://example.supabase.co", ""))

        assertTrue(MultiplayerConfig.isConfigured("https://demo.supabase.co", "sb_publishable_demo_token"))
        val wsUrl = MultiplayerConfig.realtimeWebSocketUrl("https://demo.supabase.co", "sb_publishable_demo_token")
        assertEquals("wss://demo.supabase.co/realtime/v1/websocket?apikey=sb_publishable_demo_token&vsn=1.0.0", wsUrl)
        assertEquals("realtime:rookvale:floor:-2", MultiplayerConfig.floorChannelTopic(-2))
    }

    @Test
    fun deterministicHostElectionPicksEarliestJoinedAndHandlesTakeoverOnLeave() {
        val alice = RemotePlayerState("p_alice", "Alice", 12, Vocation.KNIGHT, 0, 0, 47, 46, floor = 0, hp = 200, maxHp = 200, joinedAt = 1000L)
        val bob = RemotePlayerState("p_bob", "Bob", 10, Vocation.MAGE, 1, 2, 48, 46, floor = 0, hp = 150, maxHp = 150, joinedAt = 2000L)

        // Earliest joinedAt wins
        assertEquals("p_alice", electFloorHost("p_local", 3000L, listOf(bob, alice)))
        // Tie-break lexicographically by playerId
        assertEquals("p_alice", electFloorHost("p_bob", 1000L, listOf(alice)))

        // Simulate Phoenix presence_state + host leaving via presence_diff
        val mgr = RealtimeMultiplayerManager(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        mgr.connect(
            LocalPlayerPresencePayload(
                playerId = "p_local",
                name = "Roland",
                level = 15,
                vocation = Vocation.ADVENTURER,
                tunicColorIndex = 0,
                hairColorIndex = 0,
                x = 47,
                y = 46,
                floor = 0,
                hp = 200,
                maxHp = 200,
                joinedAt = 2000L
            )
        )

        // Simulate successful channel join + earlier host presence
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"phx_reply","payload":{"status":"ok","response":{}},"ref":"1"}"""
        )
        assertEquals(ConnectionStatus.ONLINE, mgr.uiState.value.status)

        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"presence_state","payload":{"p_host":{"metas":[{"playerId":"p_host","name":"EarlierHost","level":22,"vocation":"KNIGHT","tunicColorIndex":1,"hairColorIndex":2,"x":46,"y":46,"floor":0,"hp":320,"maxHp":340,"joinedAt":1000}]}}}"""
        )
        assertEquals(2, mgr.uiState.value.floorOnlineCount)
        assertFalse("Earlier player should be host", mgr.uiState.value.isFloorHost)
        assertEquals("p_host", mgr.uiState.value.hostPlayerId)
        assertEquals(1, mgr.uiState.value.remotePlayers.size)

        // Host leaves -> local player seamlessly takes over as floor host
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"presence_diff","payload":{"joins":{},"leaves":{"p_host":{"metas":[{"playerId":"p_host"}]}}}}"""
        )
        assertEquals(1, mgr.uiState.value.floorOnlineCount)
        assertTrue("Local player must take over as host when previous host leaves", mgr.uiState.value.isFloorHost)
        assertEquals("p_local", mgr.uiState.value.hostPlayerId)
        assertTrue(mgr.uiState.value.remotePlayers.isEmpty())
    }

    @Test
    fun phoenixBroadcastParsesPlayerMovementAndSanitizesChat() {
        val mgr = RealtimeMultiplayerManager(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        mgr.connect(
            LocalPlayerPresencePayload("p_local", "Roland", 5, Vocation.ADVENTURER, 0, 0, 47, 46, 0, 150, 150, 1000L)
        )
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"phx_reply","payload":{"status":"ok"},"ref":"1"}"""
        )

        // Remote player initial state at (46, 46) then steps to (46, 47)
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"broadcast","payload":{"event":"player_state","payload":{"playerId":"p_lyra","name":"Lyra","level":20,"vocation":"PALADIN","tunicColorIndex":3,"hairColorIndex":1,"x":46,"y":46,"floor":0,"hp":280,"maxHp":300,"joinedAt":1500}}}"""
        )
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"broadcast","payload":{"event":"player_state","payload":{"playerId":"p_lyra","name":"Lyra","level":20,"vocation":"PALADIN","tunicColorIndex":3,"hairColorIndex":1,"x":46,"y":47,"floor":0,"hp":260,"maxHp":300,"joinedAt":1500}}}"""
        )
        val lyra = mgr.uiState.value.remotePlayers.firstOrNull { it.playerId == "p_lyra" }
        assertNotNull(lyra)
        assertEquals(46, lyra!!.prevX)
        assertEquals(46, lyra.prevY)
        assertEquals(46, lyra.x)
        assertEquals(47, lyra.y)
        assertEquals(Vocation.PALADIN, lyra.vocation)

        // Chat message with control characters gets sanitized
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:world","event":"broadcast","payload":{"event":"chat_msg","payload":{"id":"m1","sender":"Lyra\n\r","text":"Dragon up!\u0007 Ready?","ch":"WORLD","floor":-4,"ts":2000}}}"""
        )
        val chat = mgr.uiState.value.chatMessages.lastOrNull()
        assertNotNull(chat)
        assertEquals("Lyra", chat!!.senderName)
        assertEquals("Dragon up! Ready?", chat.text)
        assertEquals(ChatChannel.WORLD, chat.channel)
        assertEquals(1, mgr.uiState.value.unreadChatCount)
        assertEquals("SafeName", MultiplayerSanitizer.sanitizeName("SafeName\u0000\n"))
    }

    @Test
    fun coopSharedMonsterDropsPersonalInstancedLootOnlyForContributors() {
        val monster = MonsterEntity(42, MonsterType.GRIMMAW_CRUSHER, -1, 16, 15, 16, 15, 200, 200)

        // Contributor player hits the Grimmaw Crusher first
        val contributor = GameEngine.createNewWorld("Roland").copy(
            saveId = "p_roland",
            floor = -1,
            playerX = 15,
            playerY = 15,
            monsters = listOf(monster),
            targetMonsterId = 42,
            lastPlayerAttackMs = 0L,
            equipment = EquippedGear(weaponId = "sunbrass_dirk")
        )
        val afterLocalHit = GameEngine.processPlayerAttackSubTick(contributor, 1000L)
        assertTrue("Local hit must queue outbound hit event", afterLocalHit.outboundMonsterHits.size == 1)
        assertTrue(
            "Local player must be recorded as contributor",
            "p_roland" in afterLocalHit.monsters.first().contributors
        )

        // Teammate delivers the killing blow via broadcast -> Contributor gets personal instanced loot & XP!
        val xpBefore = afterLocalHit.xp
        val killingBlow = RemoteMonsterHitEvent(
            monsterId = 42,
            floor = -1,
            attackerId = "p_lyra",
            attackerName = "Lyra",
            damage = 200,
            resultingHp = 0
        )
        val afterCoopKill = GameEngine.applyRemoteMonsterHit(afterLocalHit, killingBlow, "p_roland", isFloorHost = false, nowMs = 2000L)
        assertTrue("Slain monster must be removed", afterCoopKill.monsters.none { it.id == 42 })
        assertTrue("Contributor must receive personal instanced corpse", afterCoopKill.corpses.any { !it.isPlayerCorpse && it.x == 16 && it.y == 15 })
        assertTrue("Contributor must receive XP", afterCoopKill.xp > xpBefore)

        // Non-contributing bystander on same floor sees monster die but does NOT get unearned loot or XP
        val bystander = contributor.copy(saveId = "p_bystander", targetMonsterId = null)
        val afterBystander = GameEngine.applyRemoteMonsterHit(bystander, killingBlow, "p_bystander", isFloorHost = false, nowMs = 2000L)
        assertTrue(afterBystander.monsters.none { it.id == 42 })
        assertTrue("Non-contributor must NOT spawn unearned loot", afterBystander.corpses.isEmpty())
        assertEquals(0L, afterBystander.xp)
    }

    @Test
    fun hostAiTargetsClosestCoopPlayerAndNonHostSyncPreservesContributors() {
        val monster = MonsterEntity(50, MonsterType.SKELETON, -1, 18, 15, 18, 15, 115, 115)
        val remoteCloser = RemotePlayerState("p_lyra", "Lyra", 12, Vocation.KNIGHT, 0, 0, 17, 15, floor = -1, hp = 200, maxHp = 200, joinedAt = 2000L)
        val hostState = GameEngine.createNewWorld("Roland").copy(
            saveId = "p_roland",
            floor = -1,
            playerX = 12,
            playerY = 15,
            monsters = listOf(monster)
        )

        val tickedHost = GameEngine.processCombatTick(
            state = hostState,
            nowMs = 1000L,
            isFloorHost = true,
            localPlayerId = "p_roland",
            remotePlayersOnFloor = listOf(remoteCloser)
        )
        assertEquals("Host AI must target closer co-op teammate", "p_lyra", tickedHost.monsters.first().targetPlayerId)

        // Non-host client sync updates monster HP/pos/target while preserving its own contributor eligibility
        val nonHost = hostState.copy(
            saveId = "p_lyra",
            monsters = listOf(monster.copy(contributors = setOf("p_lyra")))
        )
        val synced = GameEngine.applyRemoteMonsterSync(
            state = nonHost,
            floor = -1,
            snapshots = listOf(
                SyncedMonsterSnapshot(50, MonsterType.SKELETON.name, -1, 17, 16, 18, 15, 70, 115, "p_lyra")
            ),
            localPlayerId = "p_lyra",
            nowMs = 2000L
        )
        val syncedM = synced.monsters.first { it.id == 50 }
        assertEquals(70, syncedM.hp)
        assertEquals(17, syncedM.x)
        assertEquals(16, syncedM.y)
        assertEquals("p_lyra", syncedM.targetPlayerId)
        assertTrue("Host sync must preserve local contributor tracking", "p_lyra" in syncedM.contributors)
    }

    @Test
    fun playerStateSyncsDirectionActionStateOutfitColorsAndRemovesOnFloorLeave() {
        val mgr = RealtimeMultiplayerManager(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        mgr.connect(
            LocalPlayerPresencePayload(
                playerId = "p_local",
                name = "Roland",
                level = 10,
                vocation = Vocation.KNIGHT,
                tunicColorIndex = 0,
                hairColorIndex = 0,
                x = 15,
                y = 15,
                floor = -1,
                hp = 200,
                maxHp = 200,
                joinedAt = 1000L,
                direction = Direction.SOUTH,
                actionState = PlayerActionState.IDLE
            )
        )
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:-1","event":"phx_reply","payload":{"status":"ok"},"ref":"1"}"""
        )

        // Incoming player_state with outfitColors object, direction, and CASTING actionState
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:-1","event":"broadcast","payload":{"event":"player_state","payload":{"playerId":"p_mage","name":"Elara","level":18,"vocation":"MAGE","outfitColors":{"tunic":4,"hair":2},"x":16,"y":15,"floor":-1,"hp":190,"maxHp":210,"direction":"WEST","actionState":"CASTING","joinedAt":1200}}}"""
        )
        val elara = mgr.uiState.value.remotePlayers.firstOrNull { it.playerId == "p_mage" }
        assertNotNull(elara)
        assertEquals(4, elara!!.tunicColorIndex)
        assertEquals(2, elara.hairColorIndex)
        assertEquals(Direction.WEST, elara.direction)
        assertEquals(PlayerActionState.CASTING, elara.actionState)
        assertEquals(2, mgr.uiState.value.floorOnlineCount)

        // Remote player climbs/descends -> player_leave removes them immediately
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:-1","event":"broadcast","payload":{"event":"player_leave","payload":{"playerId":"p_mage","floor":-1}}}"""
        )
        assertTrue("Remote player must be removed on floor change", mgr.uiState.value.remotePlayers.isEmpty())
        assertEquals(1, mgr.uiState.value.floorOnlineCount)
    }

    @Test
    fun remotePlayerActionFxRendersFloatingNumbersAndSpellProjectiles() = runBlocking {
        val mgr = RealtimeMultiplayerManager(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        mgr.connect(
            LocalPlayerPresencePayload("p_local", "Roland", 8, Vocation.ADVENTURER, 0, 0, 14, 15, -1, 180, 180, 1000L)
        )
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:-1","event":"phx_reply","payload":{"status":"ok"},"ref":"1"}"""
        )

        var received: RemotePlayerActionFxEvent? = null
        val job = launch(Dispatchers.Unconfined) {
            received = mgr.playerActionFxEvents.first()
        }

        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:-1","event":"broadcast","payload":{"event":"player_fx","payload":{"playerId":"p_lyra","floor":-1,"x":16,"y":15,"text":"+95 HP","floatingKind":"HEAL","actionState":"HEALING","proj":"ENERGY_BOLT","toX":18,"toY":15}}}"""
        )
        job.join()

        assertNotNull("Expected player_fx event to emit", received)
        assertEquals("+95 HP", received!!.text)
        assertEquals(FloatingTextKind.HEAL, received!!.floatingKind)
        assertEquals(PlayerActionState.HEALING, received!!.actionState)

        val world = GameEngine.createNewWorld("Roland").copy(floor = -1, playerX = 14, playerY = 15)
        val updated = GameEngine.applyRemotePlayerActionFx(world, received!!, nowMs = 5000L)
        assertTrue("Remote heal text must appear on same floor", updated.floatingTexts.any { it.text == "+95 HP" && it.kind == FloatingTextKind.HEAL })
        assertTrue("Remote spell projectile must spawn on same floor", updated.projectiles.any { it.kind == ProjectileKind.ENERGY_BOLT && it.toX == 18f && it.toY == 15f })

        // Different floor FX must be ignored
        val otherFloorWorld = world.copy(floor = 0)
        val ignored = GameEngine.applyRemotePlayerActionFx(otherFloorWorld, received!!, nowMs = 5000L)
        assertTrue(ignored.floatingTexts.isEmpty())
        assertTrue(ignored.projectiles.isEmpty())
    }

    @Test
    fun tibiaCollisionBlocksInNonPzAllowsPassingInPzAndPortalsAndSupportsTapToInspect() {
        // 1. Non-PZ Dungeon (Floor -1): remote player at (16, 15) blocks direct step & pathfinding
        val dungeonMap = WorldMaps.getFloor(-1)
        assertFalse("Dungeon tile (16, 15) should not be PZ", dungeonMap.isProtectionZone(16, 15))
        val blocker = RemotePlayerState("p_lyra", "Lyra", 20, Vocation.PALADIN, 2, 1, 16, 15, floor = -1, hp = 260, maxHp = 280, joinedAt = 1000L)
        val dungeonState = GameEngine.createNewWorld("Roland").copy(
            floor = -1,
            playerX = 15,
            playerY = 15,
            monsters = emptyList()
        )

        val blockedStep = GameEngine.stepDirection(dungeonState, Direction.EAST, 1000L, listOf(blocker))
        assertEquals("Non-PZ player must block direct step", 15, blockedStep.playerX)
        assertEquals(15, blockedStep.playerY)
        assertEquals("Blocked step still updates facing direction", Direction.EAST, blockedStep.playerFacing)

        // Tap-to-Inspect on remote player's tile
        val inspected = GameEngine.handleTileTap(dungeonState, 16, 15, 1000L, listOf(blocker))
        assertEquals("p_lyra", inspected.inspectedPlayerId)
        assertTrue(
            "Inspect must log Tibia-style You see message",
            inspected.statusLog.last().contains("You see Lyra (Level 20 Paladin).")
        )

        // Tapping a walkable tile behind the blocker routes around them instead of stepping onto (16, 15)
        val walkedAround = GameEngine.handleTileTap(dungeonState, 17, 15, 1000L, listOf(blocker))
        assertFalse("Path must never step onto occupied non-PZ player tile", walkedAround.playerX == 16 && walkedAround.playerY == 15)
        assertFalse("Queued path must never contain occupied non-PZ player tile", (16 to 15) in walkedAround.walkPath)

        // 2. Protection Zone (Rookvale Temple Floor 0): players pass freely through each other!
        val pzFloor = WorldMaps.getFloor(0)
        val templeX = WorldMaps.TEMPLE_SPAWN_X
        val templeY = WorldMaps.TEMPLE_SPAWN_Y
        assertTrue("Temple spawn +1 east must be PZ", pzFloor.isProtectionZone(templeX + 1, templeY))
        val templeAlly = RemotePlayerState("p_ally", "AldenFan", 5, Vocation.ADVENTURER, 0, 0, templeX + 1, templeY, floor = 0, hp = 150, maxHp = 150, joinedAt = 1000L)
        val pzState = GameEngine.createNewWorld("Roland").copy(
            floor = 0,
            playerX = templeX,
            playerY = templeY,
            monsters = emptyList()
        )
        val steppedThroughPz = GameEngine.stepDirection(pzState, Direction.EAST, 1000L, listOf(templeAlly))
        assertEquals("PZ must allow walking through another player", templeX + 1, steppedThroughPz.playerX)
        assertEquals(templeY, steppedThroughPz.playerY)

        // 3. Portal anti-grief rule: players standing on stairs/rope tiles never block entrances
        var stairsCoord: Pair<Int, Int>? = null
        for (y in 0 until dungeonMap.height) {
            for (x in 0 until dungeonMap.width) {
                val t = dungeonMap.tileAt(x, y)
                if (t == TileType.STAIRS_UP || t == TileType.STAIRS_DOWN || t == TileType.ROPE_SPOT_UP) {
                    stairsCoord = x to y
                    break
                }
            }
            if (stairsCoord != null) break
        }
        assertNotNull("Floor -1 should have stairs/rope tile", stairsCoord)
        val portalBlocker = blocker.copy(x = stairsCoord!!.first, y = stairsCoord.second)
        assertFalse(
            "Portal tiles must never be blocked by remote players",
            GameEngine.isBlockedByRemotePlayer(dungeonMap, stairsCoord.first, stairsCoord.second, listOf(portalBlocker))
        )
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun joinFramesEnablePresenceAttachJoinRefAndDoNotLoopOnPresenceReplies() {
        val moshi = Moshi.Builder().build()
        val mapAdapter = moshi.adapter<Map<String, Any?>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        )
        val sentFrames = mutableListOf<Map<String, Any?>>()
        val fakeWs = object : WebSocket {
            override fun request(): Request = Request.Builder().url("https://demo.supabase.co").build()
            override fun queueSize(): Long = 0L
            override fun send(text: String): Boolean {
                sentFrames.add(mapAdapter.fromJson(text)!!)
                return true
            }
            override fun send(bytes: ByteString): Boolean = true
            override fun close(code: Int, reason: String?): Boolean = true
            override fun cancel() {}
        }

        val nowMs = 1_750_000_000_000L
        val mgr = RealtimeMultiplayerManager(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            nowProvider = { nowMs },
            animClockProvider = { nowMs }
        )
        mgr.connect(
            LocalPlayerPresencePayload(
                playerId = "p_local",
                name = "Roland",
                level = 14,
                vocation = Vocation.KNIGHT,
                tunicColorIndex = 0,
                hairColorIndex = 0,
                x = 200,
                y = 200,
                floor = 0,
                regionId = DEFAULT_REGION_ID,
                hp = 250,
                maxHp = 250,
                joinedAt = nowMs,
                lastSeenMs = nowMs
            )
        )

        mgr.joinWorldChannel(fakeWs)
        mgr.joinFloorChannel(fakeWs, 0)
        assertEquals(2, sentFrames.size)

        val worldJoin = sentFrames[0]
        val floorJoin = sentFrames[1]
        assertEquals("realtime:rookvale:world", worldJoin["topic"])
        assertEquals("phx_join", worldJoin["event"])
        assertEquals(worldJoin["ref"], worldJoin["join_ref"])
        val worldPresenceCfg = ((worldJoin["payload"] as Map<String, Any?>)["config"] as Map<String, Any?>)["presence"] as Map<String, Any?>
        assertEquals("World channel must opt into Supabase Realtime v2 presence", true, worldPresenceCfg["enabled"])
        assertEquals("p_local", worldPresenceCfg["key"])

        assertEquals("realtime:rookvale:floor:0", floorJoin["topic"])
        assertEquals("phx_join", floorJoin["event"])
        assertEquals(floorJoin["ref"], floorJoin["join_ref"])
        val floorPresenceCfg = ((floorJoin["payload"] as Map<String, Any?>)["config"] as Map<String, Any?>)["presence"] as Map<String, Any?>
        assertEquals("Floor channel must opt into Supabase Realtime v2 presence", true, floorPresenceCfg["enabled"])
        assertEquals("p_local", floorPresenceCfg["key"])

        val worldJoinRef = worldJoin["ref"].toString()
        val floorJoinRef = floorJoin["ref"].toString()
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:world","event":"phx_reply","payload":{"status":"ok","response":{}},"ref":"$worldJoinRef"}""",
            fakeWs
        )
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"phx_reply","payload":{"status":"ok","response":{}},"ref":"$floorJoinRef"}""",
            fakeWs
        )
        assertEquals(ConnectionStatus.ONLINE, mgr.uiState.value.status)

        val afterJoinCount = sentFrames.size
        val floorPresenceTrack = sentFrames.firstOrNull {
            it["topic"] == "realtime:rookvale:floor:0" && it["event"] == "presence"
        }
        val worldPresenceTrack = sentFrames.firstOrNull {
            it["topic"] == "realtime:rookvale:world" && it["event"] == "presence"
        }
        assertNotNull("Floor presence track must be sent after join ok", floorPresenceTrack)
        assertNotNull("World presence track must be sent after join ok", worldPresenceTrack)
        assertEquals(floorJoinRef, floorPresenceTrack!!["join_ref"])
        assertEquals(worldJoinRef, worldPresenceTrack!!["join_ref"])

        // Regression test: presence track phx_reply MUST NOT re-trigger trackLocalPresence/broadcastLocalState
        val floorTrackRef = floorPresenceTrack["ref"].toString()
        val worldTrackRef = worldPresenceTrack["ref"].toString()
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"phx_reply","payload":{"status":"ok","response":{}},"ref":"$floorTrackRef"}""",
            fakeWs
        )
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:world","event":"phx_reply","payload":{"status":"ok","response":{}},"ref":"$worldTrackRef"}""",
            fakeWs
        )
        assertEquals(
            "Presence track phx_reply must NEVER re-trigger trackLocalPresence/broadcastLocalState",
            afterJoinCount,
            sentFrames.size
        )
    }

    @Test
    fun worldChannelPresenceRegistersOnlinePlayersAcrossDifferentFloorsAndPolledFallback() {
        val nowMs = 1_750_000_000_000L
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
                regionId = DEFAULT_REGION_ID,
                hp = 240,
                maxHp = 240,
                joinedAt = nowMs,
                lastSeenMs = nowMs
            )
        )
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:world","event":"phx_reply","payload":{"status":"ok"},"ref":"1"}"""
        )
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"phx_reply","payload":{"status":"ok"},"ref":"2"}"""
        )

        // World presence_state reports one player in a Lower Boss Dungeon (floor -16) and one on Overland (floor 0)
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:world","event":"presence_state","payload":{"p_delver":{"metas":[{"playerId":"p_delver","name":"Kael","level":25,"vocation":"KNIGHT","x":24,"y":20,"floor":-16,"regionId":"rookvale_valley","hp":350,"maxHp":380,"joinedAt":$nowMs,"lastSeen":$nowMs}]},"p_overland":{"metas":[{"playerId":"p_overland","name":"Lyra","level":18,"vocation":"PALADIN","x":210,"y":200,"floor":0,"regionId":"sunspire_desert","hp":260,"maxHp":280,"joinedAt":$nowMs,"lastSeen":$nowMs}]}}}"""
        )

        val state = mgr.uiState.value
        assertEquals(ConnectionStatus.ONLINE, state.status)
        assertEquals("Realm count must include local + overland + dungeon delver", 3, state.realmOnlineCount)
        assertEquals(2, state.realmOnlinePlayers.size)
        assertEquals("Floor 0 remotePlayers must include cross-biome overland peer only", 1, state.remotePlayers.size)
        assertEquals("p_overland", state.remotePlayers.first().playerId)
        assertEquals(2, state.floorOnlineCount)

        // Dungeon delver disconnects from world channel -> realm count drops cleanly to 2
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:world","event":"presence_diff","payload":{"joins":{},"leaves":{"p_delver":{"metas":[{"playerId":"p_delver"}]}}}}"""
        )
        assertEquals(2, mgr.uiState.value.realmOnlineCount)
        assertEquals(1, mgr.uiState.value.realmOnlinePlayers.size)
        assertFalse(mgr.uiState.value.realmOnlinePlayers.any { it.playerId == "p_delver" })

        // REST polled fallback adds a dungeon player on floor -18 into realmOnlinePlayers without polluting Floor 0 remotePlayers
        val polledDungeonPeer = RemotePlayerState(
            playerId = "p_polled_cave",
            name = "Mireille",
            level = 22,
            vocation = Vocation.MAGE,
            tunicColorIndex = 1,
            hairColorIndex = 2,
            x = 18,
            y = 14,
            floor = -18,
            regionId = DEFAULT_REGION_ID,
            hp = 210,
            maxHp = 210,
            joinedAt = nowMs - 5_000L,
            lastSeenMs = nowMs - 2_000L
        )
        mgr.mergePolledPlayers(
            polled = listOf(polledDungeonPeer),
            localFloor = 0,
            localRegionId = DEFAULT_REGION_ID,
            localPlayerId = "p_local",
            localPlayerName = "Roland"
        )
        assertEquals(3, mgr.uiState.value.realmOnlineCount)
        assertTrue(mgr.uiState.value.realmOnlinePlayers.any { it.playerId == "p_polled_cave" })
        assertEquals("Floor 0 remotePlayers must stay at 1", 1, mgr.uiState.value.remotePlayers.size)
    }
}
