package com.example.game.multiplayer

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Supabase Realtime WebSocket manager over OkHttp implementing Phoenix Channels v1 (Presence + Broadcast).
 * - Partitioned per floor ("realtime:rookvale:floor:<floor>") plus shared "realtime:rookvale:world" for callouts/world chat.
 * - Deterministic host election via Presence joinedAt timestamps.
 * - Automatic offline solo fallback if credentials are unset/invalid or network disconnects.
 */
class RealtimeMultiplayerManager(
    private val scope: CoroutineScope,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val animClockProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val moshi = Moshi.Builder().build()
    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    private val _uiState = MutableStateFlow(MultiplayerUiState())
    val uiState: StateFlow<MultiplayerUiState> = _uiState.asStateFlow()

    private val _monsterSyncEvents = MutableSharedFlow<Pair<Int, List<SyncedMonsterSnapshot>>>(extraBufferCapacity = 32)
    val monsterSyncEvents: SharedFlow<Pair<Int, List<SyncedMonsterSnapshot>>> = _monsterSyncEvents.asSharedFlow()

    private val _monsterHitEvents = MutableSharedFlow<RemoteMonsterHitEvent>(extraBufferCapacity = 64)
    val monsterHitEvents: SharedFlow<RemoteMonsterHitEvent> = _monsterHitEvents.asSharedFlow()

    private val _monsterAttackEvents = MutableSharedFlow<RemoteMonsterAttackEvent>(extraBufferCapacity = 64)
    val monsterAttackEvents: SharedFlow<RemoteMonsterAttackEvent> = _monsterAttackEvents.asSharedFlow()

    private val refCounter = AtomicLong(1L)
    private var activeWebSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    @Volatile
    private var sessionActive = false
    @Volatile
    private var currentFloor = 0
    @Volatile
    private var floorJoinedAtMs = 0L
    @Volatile
    private var latestLocalPayload: LocalPlayerPresencePayload? = null
    @Volatile
    private var lastChatSentMs = 0L

    private val presenceMap = LinkedHashMap<String, RemotePlayerState>()

    enum class RetryResult {
        CONNECTING,
        ALREADY_ONLINE,
        MISSING_CREDENTIALS
    }

    fun connect(initialPayload: LocalPlayerPresencePayload) {
        sessionActive = true
        latestLocalPayload = initialPayload
        currentFloor = initialPayload.floor
        floorJoinedAtMs = initialPayload.joinedAt
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null

        if (!MultiplayerConfig.isConfigured()) {
            transitionToOfflineSolo(localPlayerId = initialPayload.playerId)
            return
        }
        openSocket()
    }

    fun ensureConnectedOrRetry(payload: LocalPlayerPresencePayload): RetryResult {
        sessionActive = true
        latestLocalPayload = payload.copy(joinedAt = if (floorJoinedAtMs > 0L) floorJoinedAtMs else payload.joinedAt)
        currentFloor = payload.floor
        if (floorJoinedAtMs == 0L) floorJoinedAtMs = payload.joinedAt

        if (!MultiplayerConfig.isConfigured()) {
            transitionToOfflineSolo(localPlayerId = payload.playerId)
            return RetryResult.MISSING_CREDENTIALS
        }
        if (_uiState.value.status == ConnectionStatus.ONLINE && activeWebSocket != null) {
            broadcastLocalState(latestLocalPayload!!)
            return RetryResult.ALREADY_ONLINE
        }
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null
        openSocket()
        return RetryResult.CONNECTING
    }

    fun disconnect() {
        sessionActive = false
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        heartbeatJob = null
        reconnectJob = null
        activeWebSocket?.close(1000, "Session ended")
        activeWebSocket = null
        synchronized(presenceMap) { presenceMap.clear() }
        val localId = latestLocalPayload?.playerId.orEmpty()
        transitionToOfflineSolo(localPlayerId = localId)
    }

    fun switchFloor(newFloor: Int, updatedPayload: LocalPlayerPresencePayload) {
        val oldFloor = currentFloor
        latestLocalPayload = updatedPayload
        currentFloor = newFloor
        floorJoinedAtMs = updatedPayload.joinedAt

        synchronized(presenceMap) { presenceMap.clear() }
        recomputePresenceAndHost()

        val ws = activeWebSocket
        if (ws != null && _uiState.value.status == ConnectionStatus.ONLINE) {
            if (oldFloor != newFloor) {
                sendPhoenixFrame(ws, MultiplayerConfig.floorChannelTopic(oldFloor), "phx_leave", emptyMap())
            }
            joinFloorChannel(ws, newFloor)
        }
    }

    fun broadcastLocalState(payload: LocalPlayerPresencePayload) {
        latestLocalPayload = payload.copy(joinedAt = floorJoinedAtMs)
        val ws = activeWebSocket ?: return
        if (_uiState.value.status != ConnectionStatus.ONLINE) return

        val body = playerPayloadToMap(latestLocalPayload!!)
        sendBroadcast(ws, MultiplayerConfig.floorChannelTopic(currentFloor), "player_state", body)
    }

    fun broadcastMonsterSync(floor: Int, monsters: List<SyncedMonsterSnapshot>) {
        val ws = activeWebSocket ?: return
        if (_uiState.value.status != ConnectionStatus.ONLINE) return
        if (!_uiState.value.isFloorHost || floor != currentFloor) return

        val listPayload = monsters.map { m ->
            val aoeEncoded = m.pendingAoeTiles.take(16).map { (ax, ay) -> listOf(ax, ay) }
            mapOf(
                "id" to m.id,
                "type" to m.typeName,
                "floor" to m.floor,
                "x" to m.x,
                "y" to m.y,
                "sx" to m.spawnX,
                "sy" to m.spawnY,
                "hp" to m.hp,
                "mhp" to m.maxHp,
                "tgt" to m.targetPlayerId,
                "sum" to m.isSummon,
                "aoe" to aoeEncoded
            )
        }
        sendBroadcast(
            ws,
            MultiplayerConfig.floorChannelTopic(floor),
            "monster_sync",
            mapOf(
                "hostId" to (latestLocalPayload?.playerId ?: ""),
                "floor" to floor,
                "monsters" to listPayload
            )
        )
    }

    fun broadcastMonsterHit(hit: LocalMonsterHitEvent, attackerId: String, attackerName: String) {
        val ws = activeWebSocket ?: return
        if (_uiState.value.status != ConnectionStatus.ONLINE) return

        sendBroadcast(
            ws,
            MultiplayerConfig.floorChannelTopic(hit.floor),
            "monster_hit",
            mapOf(
                "mid" to hit.monsterId,
                "floor" to hit.floor,
                "aid" to attackerId,
                "aname" to MultiplayerSanitizer.sanitizeName(attackerName),
                "dmg" to hit.damage,
                "hp" to hit.resultingHp,
                "proj" to hit.projectileKindName,
                "fx" to hit.fromX,
                "fy" to hit.fromY
            )
        )
    }

    fun broadcastMonsterAttack(attack: LocalMonsterAttackEvent, senderId: String) {
        val ws = activeWebSocket ?: return
        if (_uiState.value.status != ConnectionStatus.ONLINE) return

        val aoeEncoded = attack.aoeTiles.take(16).map { (ax, ay) -> listOf(ax, ay) }
        sendBroadcast(
            ws,
            MultiplayerConfig.floorChannelTopic(attack.floor),
            "monster_attack",
            mapOf(
                "sid" to senderId,
                "mid" to attack.monsterId,
                "floor" to attack.floor,
                "fx" to attack.fromX,
                "fy" to attack.fromY,
                "tx" to attack.toX,
                "ty" to attack.toY,
                "proj" to attack.projectileKindName,
                "aoe" to aoeEncoded,
                "tgt" to attack.targetPlayerId,
                "st" to attack.inflictedStatus
            )
        )
    }

    fun sendChatMessage(rawText: String, channel: ChatChannel, senderName: String, floor: Int) {
        val clean = MultiplayerSanitizer.sanitizeChatText(rawText)
        if (clean.isBlank()) return
        val now = nowProvider()
        if (now - lastChatSentMs < 450L) return
        lastChatSentMs = now

        val localMsg = ChatMessage(
            id = "c_${UUID.randomUUID()}",
            senderName = MultiplayerSanitizer.sanitizeName(senderName),
            text = clean,
            channel = channel,
            floor = floor,
            timestampMs = now,
            isLocal = true
        )
        appendChatMessage(localMsg, incrementUnread = false)

        val ws = activeWebSocket ?: return
        if (_uiState.value.status != ConnectionStatus.ONLINE) return
        val topic = if (channel == ChatChannel.WORLD) {
            MultiplayerConfig.WORLD_CHANNEL_TOPIC
        } else {
            MultiplayerConfig.floorChannelTopic(floor)
        }
        sendBroadcast(
            ws,
            topic,
            "chat_msg",
            mapOf(
                "id" to localMsg.id,
                "sender" to localMsg.senderName,
                "text" to localMsg.text,
                "ch" to channel.name,
                "floor" to floor,
                "ts" to now
            )
        )
    }

    fun broadcastSystemCallout(text: String, floor: Int) {
        val clean = MultiplayerSanitizer.sanitizeChatText(text)
        if (clean.isBlank()) return
        val now = nowProvider()
        val msg = ChatMessage(
            id = "sys_${UUID.randomUUID()}",
            senderName = "Rookvale",
            text = clean,
            channel = ChatChannel.SYSTEM,
            floor = floor,
            timestampMs = now,
            isLocal = true
        )
        appendChatMessage(msg, incrementUnread = false)

        val ws = activeWebSocket ?: return
        if (_uiState.value.status != ConnectionStatus.ONLINE) return
        sendBroadcast(
            ws,
            MultiplayerConfig.WORLD_CHANNEL_TOPIC,
            "chat_msg",
            mapOf(
                "id" to msg.id,
                "sender" to "Rookvale",
                "text" to clean,
                "ch" to ChatChannel.SYSTEM.name,
                "floor" to floor,
                "ts" to now
            )
        )
    }

    fun toggleChatExpanded() {
        _uiState.update { st ->
            val nextOpen = !st.chatExpanded
            st.copy(
                chatExpanded = nextOpen,
                unreadChatCount = if (nextOpen) 0 else st.unreadChatCount
            )
        }
    }

    fun selectChatChannel(channel: ChatChannel) {
        if (channel == ChatChannel.SYSTEM) return
        _uiState.update { it.copy(activeChatChannel = channel) }
    }

    private fun openSocket() {
        val wsUrl = MultiplayerConfig.realtimeWebSocketUrl()
        if (wsUrl == null) {
            transitionToOfflineSolo(latestLocalPayload?.playerId.orEmpty())
            return
        }

        heartbeatJob?.cancel()
        activeWebSocket?.cancel()
        _uiState.update { it.copy(status = ConnectionStatus.CONNECTING) }

        val request = Request.Builder()
            .url(wsUrl)
            .header("apikey", MultiplayerConfig.SUPABASE_ANON_KEY)
            .build()

        activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!sessionActive) {
                    webSocket.close(1000, "Inactive")
                    return
                }
                reconnectAttempt = 0
                startHeartbeat(webSocket)
                joinWorldChannel(webSocket)
                joinFloorChannel(webSocket, currentFloor)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingPhoenixFrame(text, webSocket)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleDisconnect()
            }
        })
    }

    private fun handleDisconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        activeWebSocket = null
        synchronized(presenceMap) { presenceMap.clear() }
        transitionToOfflineSolo(latestLocalPayload?.playerId.orEmpty())

        if (sessionActive && MultiplayerConfig.isConfigured()) {
            reconnectJob?.cancel()
            val backoffMs = when (reconnectAttempt) {
                0 -> 2500L
                1 -> 5000L
                2 -> 10000L
                else -> 15000L
            }
            reconnectAttempt++
            reconnectJob = scope.launch(Dispatchers.IO) {
                delay(backoffMs)
                if (sessionActive) openSocket()
            }
        }
    }

    private fun transitionToOfflineSolo(localPlayerId: String) {
        _uiState.update { st ->
            st.copy(
                status = ConnectionStatus.OFFLINE_SOLO,
                isFloorHost = true,
                hostPlayerId = localPlayerId,
                floorOnlineCount = 1,
                remotePlayers = emptyList()
            )
        }
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (sessionActive) {
                delay(25_000L)
                sendPhoenixFrame(webSocket, "phoenix", "heartbeat", emptyMap())
            }
        }
    }

    private fun joinWorldChannel(webSocket: WebSocket) {
        val payload = mapOf(
            "config" to mapOf(
                "broadcast" to mapOf("self" to false, "ack" to false)
            ),
            "access_token" to MultiplayerConfig.SUPABASE_ANON_KEY
        )
        sendPhoenixFrame(webSocket, MultiplayerConfig.WORLD_CHANNEL_TOPIC, "phx_join", payload, includeJoinRef = true)
    }

    private fun joinFloorChannel(webSocket: WebSocket, floor: Int) {
        val localId = latestLocalPayload?.playerId ?: UUID.randomUUID().toString()
        val payload = mapOf(
            "config" to mapOf(
                "broadcast" to mapOf("self" to false, "ack" to false),
                "presence" to mapOf("key" to localId)
            ),
            "access_token" to MultiplayerConfig.SUPABASE_ANON_KEY
        )
        sendPhoenixFrame(webSocket, MultiplayerConfig.floorChannelTopic(floor), "phx_join", payload, includeJoinRef = true)
    }

    private fun trackLocalPresence(webSocket: WebSocket, floor: Int) {
        val local = latestLocalPayload ?: return
        val presencePayload = mapOf(
            "type" to "presence",
            "event" to "track",
            "payload" to playerPayloadToMap(local.copy(floor = floor, joinedAt = floorJoinedAtMs))
        )
        sendPhoenixFrame(webSocket, MultiplayerConfig.floorChannelTopic(floor), "presence", presencePayload)
    }

    private fun sendBroadcast(webSocket: WebSocket, topic: String, eventName: String, body: Map<String, Any?>) {
        val envelope = mapOf(
            "type" to "broadcast",
            "event" to eventName,
            "payload" to body
        )
        sendPhoenixFrame(webSocket, topic, "broadcast", envelope)
    }

    private fun sendPhoenixFrame(
        webSocket: WebSocket,
        topic: String,
        event: String,
        payload: Map<String, Any?>,
        includeJoinRef: Boolean = false
    ) {
        val ref = refCounter.getAndIncrement().toString()
        val frame = LinkedHashMap<String, Any?>()
        frame["topic"] = topic
        frame["event"] = event
        frame["payload"] = payload
        frame["ref"] = ref
        if (includeJoinRef) frame["join_ref"] = ref
        val json = runCatching { mapAdapter.toJson(frame) }.getOrNull() ?: return
        webSocket.send(json)
    }

    internal fun handleIncomingPhoenixFrame(rawJson: String, webSocket: WebSocket? = null) {
        val frame = runCatching { mapAdapter.fromJson(rawJson) }.getOrNull() ?: return
        val topic = frame["topic"] as? String ?: return
        val event = frame["event"] as? String ?: return
        val payload = frame["payload"].asStringMap() ?: return

        val expectedFloorTopic = MultiplayerConfig.floorChannelTopic(currentFloor)

        when (event) {
            "phx_reply" -> {
                val status = payload["status"] as? String
                if (status == "ok" && topic == expectedFloorTopic) {
                    _uiState.update { it.copy(status = ConnectionStatus.ONLINE) }
                    if (webSocket != null) {
                        trackLocalPresence(webSocket, currentFloor)
                        latestLocalPayload?.let { broadcastLocalState(it) }
                    }
                    recomputePresenceAndHost()
                }
            }
            "presence_state" -> {
                if (topic != expectedFloorTopic) return
                applyPresenceState(payload)
            }
            "presence_diff" -> {
                if (topic != expectedFloorTopic) return
                applyPresenceDiff(payload)
            }
            "broadcast" -> {
                val innerEvent = payload["event"] as? String ?: return
                val innerBody = payload["payload"].asStringMap() ?: return
                when (innerEvent) {
                    "player_state" -> if (topic == expectedFloorTopic) applyRemotePlayerState(innerBody)
                    "monster_sync" -> if (topic == expectedFloorTopic) applyMonsterSync(innerBody)
                    "monster_hit" -> if (topic == expectedFloorTopic) applyRemoteMonsterHit(innerBody)
                    "monster_attack" -> if (topic == expectedFloorTopic) applyRemoteMonsterAttack(innerBody)
                    "chat_msg" -> applyIncomingChat(innerBody)
                }
            }
        }
    }

    private fun applyPresenceState(stateMap: Map<String, Any?>) {
        val localId = latestLocalPayload?.playerId
        val animNow = animClockProvider()
        synchronized(presenceMap) {
            presenceMap.clear()
            for ((key, rawEntry) in stateMap) {
                val entry = rawEntry.asStringMap() ?: continue
                val meta = extractFirstMeta(entry) ?: continue
                val parsed = parseRemotePlayer(meta, fallbackId = key, animNow = animNow, existing = null) ?: continue
                if (parsed.playerId != localId && parsed.floor == currentFloor) {
                    presenceMap[parsed.playerId] = parsed
                }
            }
        }
        recomputePresenceAndHost()
    }

    private fun applyPresenceDiff(diffMap: Map<String, Any?>) {
        val localId = latestLocalPayload?.playerId
        val animNow = animClockProvider()
        val joins = diffMap["joins"].asStringMap()
        val leaves = diffMap["leaves"].asStringMap()

        synchronized(presenceMap) {
            leaves?.forEach { (key, rawEntry) ->
                val meta = rawEntry.asStringMap()?.let { extractFirstMeta(it) }
                val id = MultiplayerSanitizer.sanitizeId(meta?.get("playerId") as? String ?: key)
                presenceMap.remove(id)
                presenceMap.remove(MultiplayerSanitizer.sanitizeId(key))
            }
            joins?.forEach { (key, rawEntry) ->
                val entry = rawEntry.asStringMap() ?: return@forEach
                val meta = extractFirstMeta(entry) ?: return@forEach
                val id = MultiplayerSanitizer.sanitizeId(meta["playerId"] as? String ?: key)
                if (id.isNotBlank() && id != localId) {
                    val existing = presenceMap[id]
                    val parsed = parseRemotePlayer(meta, fallbackId = id, animNow = animNow, existing = existing)
                    if (parsed != null && parsed.floor == currentFloor) {
                        presenceMap[parsed.playerId] = parsed
                    }
                }
            }
        }
        recomputePresenceAndHost()
    }

    private fun applyRemotePlayerState(body: Map<String, Any?>) {
        val localId = latestLocalPayload?.playerId
        val id = MultiplayerSanitizer.sanitizeId(body["playerId"] as? String)
        if (id.isBlank() || id == localId) return
        val animNow = animClockProvider()

        synchronized(presenceMap) {
            val existing = presenceMap[id]
            val parsed = parseRemotePlayer(body, fallbackId = id, animNow = animNow, existing = existing) ?: return
            if (parsed.floor != currentFloor) {
                presenceMap.remove(id)
            } else {
                presenceMap[id] = parsed
            }
        }
        recomputePresenceAndHost()
    }

    private fun applyMonsterSync(body: Map<String, Any?>) {
        val floor = body["floor"].asIntOrNull() ?: return
        if (!MultiplayerSanitizer.isValidFloor(floor) || floor != currentFloor) return
        // Ignore our own host broadcasts if echoed
        val hostId = MultiplayerSanitizer.sanitizeId(body["hostId"] as? String)
        if (hostId.isNotBlank() && hostId == latestLocalPayload?.playerId) return

        val rawList = body["monsters"] as? List<*> ?: return
        val snapshots = rawList.mapNotNull { item ->
            val m = item.asStringMap() ?: return@mapNotNull null
            val id = m["id"].asIntOrNull() ?: return@mapNotNull null
            val typeName = (m["type"] as? String).orEmpty()
            val rawX = m["x"].asIntOrNull() ?: return@mapNotNull null
            val rawY = m["y"].asIntOrNull() ?: return@mapNotNull null
            val rawSx = m["sx"].asIntOrNull() ?: rawX
            val rawSy = m["sy"].asIntOrNull() ?: rawY
            val (cx, cy) = MultiplayerSanitizer.clampCoord(floor, rawX, rawY)
            val (csx, csy) = MultiplayerSanitizer.clampCoord(floor, rawSx, rawSy)
            val maxHp = (m["mhp"].asIntOrNull() ?: 50).coerceIn(1, 50_000)
            val hp = (m["hp"].asIntOrNull() ?: maxHp).coerceIn(0, maxHp)
            val tgt = (m["tgt"] as? String)?.let { MultiplayerSanitizer.sanitizeId(it) }?.ifBlank { null }
            val isSummon = (m["sum"] as? Boolean) ?: false
            val aoeTiles = parseTilePairs(m["aoe"], floor)
            SyncedMonsterSnapshot(id, typeName, floor, cx, cy, csx, csy, hp, maxHp, tgt, isSummon, aoeTiles)
        }
        _monsterSyncEvents.tryEmit(floor to snapshots)
    }

    private fun applyRemoteMonsterHit(body: Map<String, Any?>) {
        val localId = latestLocalPayload?.playerId
        val attackerId = MultiplayerSanitizer.sanitizeId(body["aid"] as? String)
        if (attackerId.isBlank() || attackerId == localId) return
        val floor = body["floor"].asIntOrNull() ?: return
        if (!MultiplayerSanitizer.isValidFloor(floor) || floor != currentFloor) return
        val monsterId = body["mid"].asIntOrNull() ?: return
        val dmg = (body["dmg"].asIntOrNull() ?: return).coerceIn(1, 5000)
        val resHp = (body["hp"].asIntOrNull() ?: 0).coerceIn(0, 50_000)
        val (fx, fy) = MultiplayerSanitizer.clampCoord(
            floor,
            body["fx"].asIntOrNull() ?: 0,
            body["fy"].asIntOrNull() ?: 0
        )
        val evt = RemoteMonsterHitEvent(
            monsterId = monsterId,
            floor = floor,
            attackerId = attackerId,
            attackerName = MultiplayerSanitizer.sanitizeName(body["aname"] as? String),
            damage = dmg,
            resultingHp = resHp,
            projectileKindName = (body["proj"] as? String)?.take(24),
            fromX = fx,
            fromY = fy
        )
        _monsterHitEvents.tryEmit(evt)
    }

    private fun applyRemoteMonsterAttack(body: Map<String, Any?>) {
        val localId = latestLocalPayload?.playerId
        val senderId = MultiplayerSanitizer.sanitizeId(body["sid"] as? String)
        if (senderId.isNotBlank() && senderId == localId) return
        val floor = body["floor"].asIntOrNull() ?: return
        if (!MultiplayerSanitizer.isValidFloor(floor) || floor != currentFloor) return
        val monsterId = body["mid"].asIntOrNull() ?: return
        val (fx, fy) = MultiplayerSanitizer.clampCoord(floor, body["fx"].asIntOrNull() ?: 0, body["fy"].asIntOrNull() ?: 0)
        val (tx, ty) = MultiplayerSanitizer.clampCoord(floor, body["tx"].asIntOrNull() ?: fx, body["ty"].asIntOrNull() ?: fy)
        val proj = (body["proj"] as? String)?.take(24)
        val aoeTiles = parseTilePairs(body["aoe"], floor)
        val targetId = (body["tgt"] as? String)?.let { MultiplayerSanitizer.sanitizeId(it) }?.ifBlank { null }
        val status = (body["st"] as? String)?.uppercase()?.takeIf { it == "POISON" || it == "BURN" }
        _monsterAttackEvents.tryEmit(
            RemoteMonsterAttackEvent(
                monsterId = monsterId,
                floor = floor,
                fromX = fx,
                fromY = fy,
                toX = tx,
                toY = ty,
                projectileKindName = proj,
                aoeTiles = aoeTiles,
                targetPlayerId = targetId,
                inflictedStatus = status
            )
        )
    }

    private fun parseTilePairs(raw: Any?, floor: Int): List<Pair<Int, Int>> {
        val list = raw as? List<*> ?: return emptyList()
        return list.take(16).mapNotNull { entry ->
            val pair = entry as? List<*> ?: return@mapNotNull null
            if (pair.size < 2) return@mapNotNull null
            val rx = pair[0].asIntOrNull() ?: return@mapNotNull null
            val ry = pair[1].asIntOrNull() ?: return@mapNotNull null
            MultiplayerSanitizer.clampCoord(floor, rx, ry)
        }
    }

    private fun applyIncomingChat(body: Map<String, Any?>) {
        val text = MultiplayerSanitizer.sanitizeChatText(body["text"] as? String)
        if (text.isBlank()) return
        val sender = MultiplayerSanitizer.sanitizeName(body["sender"] as? String)
        val chName = (body["ch"] as? String).orEmpty()
        val channel = runCatching { ChatChannel.valueOf(chName) }.getOrDefault(ChatChannel.FLOOR)
        val floor = body["floor"].asIntOrNull() ?: currentFloor
        val msgId = MultiplayerSanitizer.sanitizeId(body["id"] as? String).ifBlank { "r_${UUID.randomUUID()}" }
        val ts = body["ts"].asLongOrNull() ?: nowProvider()

        appendChatMessage(
            ChatMessage(
                id = msgId,
                senderName = sender,
                text = text,
                channel = channel,
                floor = floor,
                timestampMs = ts,
                isLocal = false
            ),
            incrementUnread = true
        )
    }

    private fun appendChatMessage(msg: ChatMessage, incrementUnread: Boolean) {
        _uiState.update { st ->
            if (st.chatMessages.any { it.id == msg.id }) return@update st
            val unread = if (incrementUnread && !st.chatExpanded) (st.unreadChatCount + 1).coerceAtMost(99) else st.unreadChatCount
            st.copy(
                chatMessages = (st.chatMessages + msg).takeLast(40),
                unreadChatCount = unread
            )
        }
    }

    private fun recomputePresenceAndHost() {
        val localId = latestLocalPayload?.playerId.orEmpty()
        val remotes = synchronized(presenceMap) { presenceMap.values.toList() }
        val electedHost = electFloorHost(localId, floorJoinedAtMs, remotes)
        val isHost = electedHost == localId || _uiState.value.status != ConnectionStatus.ONLINE
        _uiState.update { st ->
            st.copy(
                isFloorHost = isHost,
                hostPlayerId = electedHost,
                floorOnlineCount = if (st.status == ConnectionStatus.ONLINE) 1 + remotes.size else 1,
                remotePlayers = remotes
            )
        }
    }

    private fun parseRemotePlayer(
        meta: Map<String, Any?>,
        fallbackId: String,
        animNow: Long,
        existing: RemotePlayerState?
    ): RemotePlayerState? {
        val id = MultiplayerSanitizer.sanitizeId(meta["playerId"] as? String ?: fallbackId)
        if (id.isBlank()) return null
        val floor = meta["floor"].asIntOrNull() ?: currentFloor
        if (!MultiplayerSanitizer.isValidFloor(floor)) return null
        val rawX = meta["x"].asIntOrNull() ?: existing?.x ?: return null
        val rawY = meta["y"].asIntOrNull() ?: existing?.y ?: return null
        val (cx, cy) = MultiplayerSanitizer.clampCoord(floor, rawX, rawY)
        val moved = existing != null && (existing.x != cx || existing.y != cy)
        val maxHp = (meta["maxHp"].asIntOrNull() ?: existing?.maxHp ?: 150).coerceIn(1, 50_000)
        val hp = (meta["hp"].asIntOrNull() ?: existing?.hp ?: maxHp).coerceIn(0, maxHp)
        val joinedAt = meta["joinedAt"].asLongOrNull() ?: existing?.joinedAt ?: nowProvider()

        return RemotePlayerState(
            playerId = id,
            name = MultiplayerSanitizer.sanitizeName(meta["name"] as? String ?: existing?.name),
            level = (meta["level"].asIntOrNull() ?: existing?.level ?: 1).coerceIn(1, 999),
            vocation = MultiplayerSanitizer.safeVocation(meta["vocation"] as? String ?: existing?.vocation?.name),
            tunicColorIndex = MultiplayerSanitizer.clampTunicIndex(meta["tunicColorIndex"].asIntOrNull() ?: existing?.tunicColorIndex ?: 0),
            hairColorIndex = MultiplayerSanitizer.clampHairIndex(meta["hairColorIndex"].asIntOrNull() ?: existing?.hairColorIndex ?: 0),
            x = cx,
            y = cy,
            prevX = if (moved) existing!!.x else cx,
            prevY = if (moved) existing!!.y else cy,
            lastMoveMs = if (moved) animNow else (existing?.lastMoveMs ?: animNow),
            floor = floor,
            hp = hp,
            maxHp = maxHp,
            joinedAt = joinedAt,
            isPoisoned = (meta["psn"] as? Boolean) ?: existing?.isPoisoned ?: false,
            isBurning = (meta["brn"] as? Boolean) ?: existing?.isBurning ?: false
        )
    }

    private fun extractFirstMeta(entry: Map<String, Any?>): Map<String, Any?>? {
        val metas = entry["metas"] as? List<*> ?: return entry
        return metas.firstOrNull().asStringMap()
    }

    private fun playerPayloadToMap(p: LocalPlayerPresencePayload): Map<String, Any?> = mapOf(
        "playerId" to p.playerId,
        "joinedAt" to p.joinedAt,
        "name" to MultiplayerSanitizer.sanitizeName(p.name),
        "level" to p.level,
        "vocation" to p.vocation.name,
        "tunicColorIndex" to p.tunicColorIndex,
        "hairColorIndex" to p.hairColorIndex,
        "x" to p.x,
        "y" to p.y,
        "floor" to p.floor,
        "hp" to p.hp,
        "maxHp" to p.maxHp,
        "psn" to p.isPoisoned,
        "brn" to p.isBurning
    )

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asStringMap(): Map<String, Any?>? = this as? Map<String, Any?>

    private fun Any?.asIntOrNull(): Int? = when (this) {
        is Number -> this.toInt()
        is String -> this.toIntOrNull()
        else -> null
    }

    private fun Any?.asLongOrNull(): Long? = when (this) {
        is Number -> this.toLong()
        is String -> this.toLongOrNull()
        else -> null
    }
}
