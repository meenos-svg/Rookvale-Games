package com.example

import com.example.game.engine.GameEngine
import com.example.game.model.EquippedGear
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.Vocation
import com.example.game.multiplayer.ChatChannel
import com.example.game.multiplayer.ConnectionStatus
import com.example.game.multiplayer.LocalPlayerPresencePayload
import com.example.game.multiplayer.MultiplayerConfig
import com.example.game.multiplayer.MultiplayerSanitizer
import com.example.game.multiplayer.RealtimeMultiplayerManager
import com.example.game.multiplayer.RemoteMonsterHitEvent
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.multiplayer.SyncedMonsterSnapshot
import com.example.game.multiplayer.electFloorHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        val monster = MonsterEntity(42, MonsterType.CYCLOPS, -1, 16, 15, 16, 15, 200, 200)

        // Contributor player hits the Cyclops first
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
}
