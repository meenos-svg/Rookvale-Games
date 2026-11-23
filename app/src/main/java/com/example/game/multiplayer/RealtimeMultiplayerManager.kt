package com.example.game.multiplayer

import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.normalizeRegionId
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
import kotlin.math.abs
import kotlin.math.max

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

    private val _playerActionFxEvents = MutableSharedFlow<RemotePlayerActionFxEvent>(extraBufferCapacity = 64)
    val playerActionFxEvents: SharedFlow<RemotePlayerActionFxEvent> = _playerActionFxEvents.asSharedFlow()

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
    private var currentRegionId = DEFAULT_REGION_ID
    @Volatile
    private var floorJoinedAtMs = 0L
    @Volatile
    private var latestLocalPayload: LocalPlayerPresencePayload? = null
    @Volatile
    private var lastChatSentMs = 0L
    @Volatile
    private var lastPresenceTrackMs = 0L

    private val presenceMap = LinkedHashMap<String, RemotePlayerState>()
    private val polledOnlyIds = LinkedHashSet<String>()
    private val nearbyNotifiedPlayers = LinkedHashMap<String, String>()

    companion object {
        private const val NEARBY_ENTER_TILES = 24
        private const val NEARBY_EXIT_TILES = 32
        private const val PRESENCE_RETRACK_MIN_MS = 1_500L
        private const val POLLED_STALE_MS = 65_000L
    }

    enum class RetryResult {
        CONNECTING,
        ALREADY_ONLINE,
        MISSING_CREDENTIALS
    }

    fun connect(initialPayload: LocalPlayerPresencePayload) {
        sessionActive = true
        val normalized = initialPayload.copy(
            regionId = normalizeRegionId(initialPayload.regionId),
            lastSeenMs = if (initialPayload.lastSeenMs > 0L) initialPayload.lastSeenMs else nowProvider()
        )
        latestLocalPayload = normalized
        currentFloor = normalized.floor
        currentRegionId = normalized.regionId
        floorJoinedAtMs = normalized.joinedAt
        lastPresenceTrackMs = 0L
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null

        if (!MultiplayerConfig.isConfigured()) {
            transitionToOfflineSolo(localPlayerId = normalized.playerId)
            return
        }
        openSocket()
    }

    fun ensureConnectedOrRetry(payload: LocalPlayerPresencePayload): RetryResult {
        sessionActive = true
        val normalized = payload.copy(
            regionId = normalizeRegionId(payload.regionId),
            joinedAt = if (floorJoinedAtMs > 0L) floorJoinedAtMs else payload.joinedAt,
            lastSeenMs = nowProvider()
        )
        latestLocalPayload = normalized
        currentFloor = normalized.floor
        currentRegionId = normalized.regionId
        if (floorJoinedAtMs == 0L) floorJoinedAtMs = normalized.joinedAt

        if (!MultiplayerConfig.isConfigured()) {
            transitionToOfflineSolo(localPlayerId = normalized.playerId)
            return RetryResult.MISSING_CREDENTIALS
        }
        if (_uiState.value.status == ConnectionStatus.ONLINE && activeWebSocket != null) {
            broadcastLocalState(normalized)
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
        synchronized(presenceMap) {
            presenceMap.clear()
            polledOnlyIds.clear()
            nearbyNotifiedPlayers.clear()
        }
        val localId = latestLocalPayload?.playerId.orEmpty()
        transitionToOfflineSolo(localPlayerId = localId)
    }

    fun switchFloor(newFloor: Int, updatedPayload: LocalPlayerPresencePayload) {
        val oldFloor = currentFloor
        val normalized = updatedPayload.copy(
            floor = newFloor,
            regionId = normalizeRegionId(updatedPayload.regionId),
            lastSeenMs = nowProvider()
        )
        latestLocalPayload = normalized
        currentFloor = newFloor
        currentRegionId = normalized.regionId
        floorJoinedAtMs = normalized.joinedAt
        lastPresenceTrackMs = 0L

        synchronized(presenceMap) {
            presenceMap.clear()
            polledOnlyIds.clear()
            nearbyNotifiedPlayers.clear()
        }
        recomputePresenceAndHost()

        val ws = activeWebSocket
        if (ws != null && _uiState.value.status == ConnectionStatus.ONLINE) {
            if (oldFloor != newFloor) {
                val oldTopic = MultiplayerConfig.floorChannelTopic(oldFloor)
                sendBroadcast(
                    ws,
                    oldTopic,
                    "player_leave",
                    mapOf("playerId" to normalized.playerId, "floor" to oldFloor)
                )
                sendPhoenixFrame(ws, oldTopic, "phx_leave", emptyMap())
            }
            joinFloorChannel(ws, newFloor)
        }
    }

    fun broadcastLocalState(payload: LocalPlayerPresencePayload) {
        val now = nowProvider()
        val normalized = payload.copy(
            regionId = normalizeRegionId(payload.regionId),
            joinedAt = floorJoinedAtMs,
            lastSeenMs = now
        )
        latestLocalPayload = normalized
        currentRegionId = normalized.regionId
        refreshNearbyNotifications()

        val ws = activeWebSocket ?: return
        if (_uiState.value.status != ConnectionStatus.ONLINE) return

        val body = playerPayloadToMap(normalized)
        sendBroadcast(ws, MultiplayerConfig.floorChannelTopic(currentFloor), "player_state", body)
        if (now - lastPresenceTrackMs >= PRESENCE_RETRACK_MIN_MS) {
            lastPresenceTrackMs = now
            trackLocalPresence(ws, currentFloor)
        }
    }

    /**
     * Merges active players discovered by the 2-second REST fallback poll on `public.players`.
     * Preserves fresher Realtime Broadcast/Presence updates while keeping visibility rock-solid
     * even if WebSocket packets drop or a client is still reconnecting.
     */
    fun mergePolledPlayers(
        polled: List<RemotePlayerState>,
        localFloor: Int,
        localRegionId: String,
        localPlayerId: String,
        localPlayerName: String
    ) {
        val normalizedRegion = normalizeRegionId(localRegionId)
        if (localFloor != currentFloor) return
        val now = nowProvider()
        val animNow = animClockProvider()
        val staleCutoff = now - POLLED_STALE_MS
        val freshPolledIds = HashSet<String>()

        synchronized(presenceMap) {
            for (raw in polled) {
                val rpRegion = normalizeRegionId(raw.regionId)
                if (raw.floor != localFloor || rpRegion != normalizedRegion) continue
                if (raw.lastSeenMs in 1L until staleCutoff) continue
                if (isSelfIdentity(raw.playerId, raw.name, localPlayerId, localPlayerName)) continue

                freshPolledIds.add(raw.playerId)
                val existing = presenceMap[raw.playerId]
                val effectiveLastSeen = if (raw.lastSeenMs > 0L) raw.lastSeenMs else now
                if (existing == null) {
                    presenceMap[raw.playerId] = raw.copy(
                        regionId = rpRegion,
                        prevX = raw.x,
                        prevY = raw.y,
                        lastMoveMs = animNow,
                        lastSeenMs = effectiveLastSeen
                    )
                    polledOnlyIds.add(raw.playerId)
                } else {
                    val isPolledOnly = raw.playerId in polledOnlyIds
                    // Only overwrite Realtime state if polled snapshot is at least as fresh
                    if (isPolledOnly || effectiveLastSeen >= existing.lastSeenMs) {
                        val moved = existing.x != raw.x || existing.y != raw.y
                        val nextDir = if (moved) {
                            inferDirection(existing.x, existing.y, raw.x, raw.y)
                        } else {
                            raw.direction
                        }
                        presenceMap[raw.playerId] = existing.copy(
                            name = raw.name,
                            level = raw.level,
                            vocation = raw.vocation,
                            tunicColorIndex = raw.tunicColorIndex,
                            hairColorIndex = raw.hairColorIndex,
                            x = raw.x,
                            y = raw.y,
                            prevX = if (moved) existing.x else existing.prevX,
                            prevY = if (moved) existing.y else existing.prevY,
                            lastMoveMs = if (moved) animNow else existing.lastMoveMs,
                            floor = raw.floor,
                            regionId = rpRegion,
                            hp = raw.hp,
                            maxHp = raw.maxHp,
                            direction = nextDir,
                            actionState = if (moved) PlayerActionState.WALKING else existing.actionState,
                            lastSeenMs = maxOf(existing.lastSeenMs, effectiveLastSeen),
                            weaponId = raw.weaponId,
                            offhandId = raw.offhandId,
                            armorId = raw.armorId,
                            helmetId = raw.helmetId,
                            bootsId = raw.bootsId
                        )
                    }
                }
            }

            // Expire polled-only players that are no longer active in the 60s window
            val iter = polledOnlyIds.iterator()
            while (iter.hasNext()) {
                val id = iter.next()
                val existing = presenceMap[id]
                if (id !in freshPolledIds || existing == null || existing.lastSeenMs < staleCutoff ||
                    existing.floor != localFloor || normalizeRegionId(existing.regionId) != normalizedRegion
                ) {
                    presenceMap.remove(id)
                    iter.remove()
                }
            }
        }
        recomputePresenceAndHost()
    }

    fun refreshNearbyNotifications() {
        val remotes = synchronized(presenceMap) { presenceMap.values.toList() }
        updateNearbyPlayerNotifications(remotes)
    }

    fun broadcastPlayerActionFx(event: LocalPlayerActionFxEvent, senderId: String) {
        val ws = activeWebSocket ?: return
        if (_uiState.value.status != ConnectionStatus.ONLINE) return
        if (event.floor != currentFloor) return

        sendBroadcast(
            ws,
            MultiplayerConfig.floorChannelTopic(event.floor),
            "player_fx",
            mapOf(
                "pid" to senderId,
                "floor" to event.floor,
                "x" to event.x,
                "y" to event.y,
                "txt" to event.text,
                "kind" to event.floatingKindName,
                "act" to event.actionState.name,
                "proj" to event.projectileKindName,
                "tx" to event.toX,
                "ty" to event.toY
            )
        )
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
        synchronized(presenceMap) {
            // Drop WebSocket-only presence entries while keeping active polled peers intact until next poll
            val iter = presenceMap.keys.iterator()
            while (iter.hasNext()) {
                val id = iter.next()
                if (id !in polledOnlyIds) iter.remove()
            }
        }
        val remaining = synchronized(presenceMap) { presenceMap.values.toList() }
        if (remaining.isEmpty()) {
            transitionToOfflineSolo(latestLocalPayload?.playerId.orEmpty())
        } else {
            _uiState.update { st ->
                st.copy(
                    status = ConnectionStatus.OFFLINE_SOLO,
                    isFloorHost = true,
                    hostPlayerId = latestLocalPayload?.playerId.orEmpty(),
                    floorOnlineCount = 1 + remaining.size,
                    remotePlayers = remaining
                )
            }
        }

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
        lastPresenceTrackMs = nowProvider()
        val presencePayload = mapOf(
            "type" to "presence",
            "event" to "track",
            "payload" to playerPayloadToMap(
                local.copy(
                    floor = floor,
                    regionId = normalizeRegionId(local.regionId),
                    joinedAt = floorJoinedAtMs,
                    lastSeenMs = lastPresenceTrackMs
                )
            )
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
                    "player_leave" -> if (topic == expectedFloorTopic) applyRemotePlayerLeave(innerBody)
                    "player_fx" -> if (topic == expectedFloorTopic) applyRemotePlayerActionFx(innerBody)
                    "monster_sync" -> if (topic == expectedFloorTopic) applyMonsterSync(innerBody)
                    "monster_hit" -> if (topic == expectedFloorTopic) applyRemoteMonsterHit(innerBody)
                    "monster_attack" -> if (topic == expectedFloorTopic) applyRemoteMonsterAttack(innerBody)
                    "chat_msg" -> applyIncomingChat(innerBody)
                }
            }
        }
    }

    private fun applyPresenceState(stateMap: Map<String, Any?>) {
        val animNow = animClockProvider()
        val seenPresenceIds = HashSet<String>()
        synchronized(presenceMap) {
            for ((key, rawEntry) in stateMap) {
                val entry = rawEntry.asStringMap() ?: continue
                val meta = extractFirstMeta(entry) ?: continue
                val existing = presenceMap[MultiplayerSanitizer.sanitizeId(meta["playerId"] as? String ?: key)]
                val parsed = parseRemotePlayer(meta, fallbackId = key, animNow = animNow, existing = existing) ?: continue
                if (!isLocalPlayer(parsed.playerId, parsed.name) &&
                    parsed.floor == currentFloor &&
                    normalizeRegionId(parsed.regionId) == currentRegionId
                ) {
                    seenPresenceIds.add(parsed.playerId)
                    polledOnlyIds.remove(parsed.playerId)
                    // Keep fresher broadcast coordinates if existing entry is newer than presence snapshot
                    if (existing == null || parsed.lastSeenMs >= existing.lastSeenMs) {
                        presenceMap[parsed.playerId] = parsed
                    }
                }
            }
            // Prune stale non-polled presence entries that are no longer in the channel presence_state
            val iter = presenceMap.keys.iterator()
            while (iter.hasNext()) {
                val id = iter.next()
                if (id !in seenPresenceIds && id !in polledOnlyIds) {
                    iter.remove()
                }
            }
        }
        recomputePresenceAndHost()
    }

    private fun applyPresenceDiff(diffMap: Map<String, Any?>) {
        val animNow = animClockProvider()
        val joins = diffMap["joins"].asStringMap()
        val leaves = diffMap["leaves"].asStringMap()
        var hasNewRemoteJoin = false

        synchronized(presenceMap) {
            leaves?.forEach { (key, rawEntry) ->
                val meta = rawEntry.asStringMap()?.let { extractFirstMeta(it) }
                val id = MultiplayerSanitizer.sanitizeId(meta?.get("playerId") as? String ?: key)
                val sanitizedKey = MultiplayerSanitizer.sanitizeId(key)
                presenceMap.remove(id)
                presenceMap.remove(sanitizedKey)
                polledOnlyIds.remove(id)
                polledOnlyIds.remove(sanitizedKey)
            }
            joins?.forEach { (key, rawEntry) ->
                val entry = rawEntry.asStringMap() ?: return@forEach
                val meta = extractFirstMeta(entry) ?: return@forEach
                val id = MultiplayerSanitizer.sanitizeId(meta["playerId"] as? String ?: key)
                if (id.isNotBlank()) {
                    val existing = presenceMap[id]
                    val parsed = parseRemotePlayer(meta, fallbackId = id, animNow = animNow, existing = existing)
                    if (parsed != null &&
                        !isLocalPlayer(parsed.playerId, parsed.name) &&
                        parsed.floor == currentFloor &&
                        normalizeRegionId(parsed.regionId) == currentRegionId
                    ) {
                        polledOnlyIds.remove(parsed.playerId)
                        if (existing == null || parsed.lastSeenMs >= existing.lastSeenMs) {
                            presenceMap[parsed.playerId] = parsed
                        }
                        hasNewRemoteJoin = true
                    }
                }
            }
        }
        recomputePresenceAndHost()

        // Immediately broadcast our live position so newly joined peers see us right away
        if (hasNewRemoteJoin) {
            val ws = activeWebSocket
            val local = latestLocalPayload
            if (ws != null && local != null && _uiState.value.status == ConnectionStatus.ONLINE) {
                sendBroadcast(
                    ws,
                    MultiplayerConfig.floorChannelTopic(currentFloor),
                    "player_state",
                    playerPayloadToMap(local.copy(lastSeenMs = nowProvider()))
                )
            }
        }
    }

    private fun applyRemotePlayerState(body: Map<String, Any?>) {
        val id = MultiplayerSanitizer.sanitizeId(body["playerId"] as? String)
        if (id.isBlank() || isLocalPlayer(id, body["name"] as? String)) return
        val animNow = animClockProvider()

        synchronized(presenceMap) {
            val existing = presenceMap[id]
            val parsed = parseRemotePlayer(body, fallbackId = id, animNow = animNow, existing = existing) ?: return
            if (parsed.floor != currentFloor || normalizeRegionId(parsed.regionId) != currentRegionId) {
                presenceMap.remove(id)
                polledOnlyIds.remove(id)
            } else {
                presenceMap[id] = parsed
                polledOnlyIds.remove(id)
            }
        }
        recomputePresenceAndHost()
    }

    private fun applyRemotePlayerLeave(body: Map<String, Any?>) {
        val id = MultiplayerSanitizer.sanitizeId(body["playerId"] as? String)
        if (id.isBlank() || isLocalPlayer(id, null)) return
        val removed = synchronized(presenceMap) {
            polledOnlyIds.remove(id)
            presenceMap.remove(id) != null
        }
        if (removed) recomputePresenceAndHost()
    }

    private fun applyRemotePlayerActionFx(body: Map<String, Any?>) {
        val localId = latestLocalPayload?.playerId
        val pid = MultiplayerSanitizer.sanitizeId((body["pid"] as? String) ?: (body["playerId"] as? String))
        if (pid.isBlank() || pid == localId) return
        val floor = body["floor"].asIntOrNull() ?: return
        if (!MultiplayerSanitizer.isValidFloor(floor) || floor != currentFloor) return
        val (cx, cy) = MultiplayerSanitizer.clampCoord(floor, body["x"].asIntOrNull() ?: 0, body["y"].asIntOrNull() ?: 0)
        val rawToX = body["tx"].asIntOrNull() ?: body["toX"].asIntOrNull()
        val rawToY = body["ty"].asIntOrNull() ?: body["toY"].asIntOrNull()
        val clampedTo = if (rawToX != null && rawToY != null) MultiplayerSanitizer.clampCoord(floor, rawToX, rawToY) else null
        val actionState = MultiplayerSanitizer.safeActionState((body["act"] as? String) ?: (body["actionState"] as? String))
        val animNow = animClockProvider()

        if (actionState != PlayerActionState.IDLE) {
            var updated = false
            synchronized(presenceMap) {
                val existing = presenceMap[pid]
                if (existing != null) {
                    presenceMap[pid] = existing.copy(actionState = actionState, lastActionMs = animNow)
                    updated = true
                }
            }
            if (updated) recomputePresenceAndHost()
        }

        _playerActionFxEvents.tryEmit(
            RemotePlayerActionFxEvent(
                playerId = pid,
                floor = floor,
                x = cx,
                y = cy,
                text = MultiplayerSanitizer.sanitizeFloatingText((body["txt"] as? String) ?: (body["text"] as? String)),
                floatingKind = MultiplayerSanitizer.safeFloatingKind((body["kind"] as? String) ?: (body["floatingKind"] as? String)),
                actionState = actionState,
                projectileKindName = (body["proj"] as? String)?.take(24),
                toX = clampedTo?.first,
                toY = clampedTo?.second
            )
        )
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
        val status = (body["st"] as? String)?.uppercase()?.takeIf { it == "POISON" || it == "FIRE" || it == "BURN" }
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

    private fun isLocalPlayer(candidateId: String, candidateName: String?): Boolean {
        val local = latestLocalPayload ?: return false
        return isSelfIdentity(candidateId, candidateName, local.playerId, local.name)
    }

    private fun isSelfIdentity(
        candidateId: String,
        candidateName: String?,
        localId: String,
        localName: String
    ): Boolean {
        if (localId.isNotBlank() && candidateId == localId) return true
        val cleanLocal = MultiplayerSanitizer.sanitizeName(localName)
        val cleanCand = candidateName?.let { MultiplayerSanitizer.sanitizeName(it) }
        return cleanLocal.isNotBlank() && !cleanCand.isNullOrBlank() &&
            cleanCand.equals(cleanLocal, ignoreCase = true)
    }

    private fun recomputePresenceAndHost() {
        val localId = latestLocalPayload?.playerId.orEmpty()
        val remotes = synchronized(presenceMap) {
            presenceMap.values
                .filter { it.floor == currentFloor && normalizeRegionId(it.regionId) == currentRegionId }
                .toList()
        }
        val electedHost = electFloorHost(localId, floorJoinedAtMs, remotes)
        val isHost = electedHost == localId || _uiState.value.status != ConnectionStatus.ONLINE
        _uiState.update { st ->
            val onlineCount = if (st.status == ConnectionStatus.ONLINE || remotes.isNotEmpty()) {
                1 + remotes.size
            } else {
                1
            }
            st.copy(
                isFloorHost = isHost,
                hostPlayerId = electedHost,
                floorOnlineCount = onlineCount,
                remotePlayers = remotes
            )
        }
        updateNearbyPlayerNotifications(remotes)
    }

    private fun updateNearbyPlayerNotifications(remotes: List<RemotePlayerState>) {
        val local = latestLocalPayload ?: return
        val now = nowProvider()
        val systemMessages = ArrayList<ChatMessage>()
        val remotesById = remotes.associateBy { it.playerId }

        synchronized(presenceMap) {
            // Notify when a remote player enters the nearby area
            for (rp in remotes) {
                val dist = max(abs(rp.x - local.x), abs(rp.y - local.y))
                if (dist <= NEARBY_ENTER_TILES && rp.playerId !in nearbyNotifiedPlayers) {
                    nearbyNotifiedPlayers[rp.playerId] = rp.name
                    systemMessages.add(
                        ChatMessage(
                            id = "online_in_${rp.playerId}_${now}_${refCounter.getAndIncrement()}",
                            senderName = "Online",
                            text = "[Online] ${rp.name} is nearby at (${rp.x}, ${rp.y}).",
                            channel = ChatChannel.SYSTEM,
                            floor = currentFloor,
                            timestampMs = now,
                            isLocal = false
                        )
                    )
                }
            }

            // Notify when a previously nearby player moves far away, switches floor/region, or leaves
            val iter = nearbyNotifiedPlayers.entries.iterator()
            while (iter.hasNext()) {
                val (pid, prevName) = iter.next()
                val rp = remotesById[pid]
                val dist = if (rp != null) max(abs(rp.x - local.x), abs(rp.y - local.y)) else Int.MAX_VALUE
                if (rp == null || dist > NEARBY_EXIT_TILES) {
                    iter.remove()
                    systemMessages.add(
                        ChatMessage(
                            id = "online_out_${pid}_${now}_${refCounter.getAndIncrement()}",
                            senderName = "Online",
                            text = "[Online] $prevName left the area.",
                            channel = ChatChannel.SYSTEM,
                            floor = currentFloor,
                            timestampMs = now,
                            isLocal = false
                        )
                    )
                }
            }
        }

        for (msg in systemMessages) {
            appendChatMessage(msg, incrementUnread = false)
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
        val regionId = MultiplayerSanitizer.sanitizeRegionId(
            (meta["regionId"] as? String) ?: existing?.regionId
        )
        val rawX = meta["x"].asIntOrNull() ?: existing?.x ?: return null
        val rawY = meta["y"].asIntOrNull() ?: existing?.y ?: return null
        val (cx, cy) = MultiplayerSanitizer.clampCoord(floor, rawX, rawY)
        val moved = existing != null && (existing.x != cx || existing.y != cy)
        val maxHp = (meta["maxHp"].asIntOrNull() ?: existing?.maxHp ?: 150).coerceIn(1, 50_000)
        val hp = (meta["hp"].asIntOrNull() ?: existing?.hp ?: maxHp).coerceIn(0, maxHp)
        val joinedAt = meta["joinedAt"].asLongOrNull() ?: existing?.joinedAt ?: nowProvider()
        val lastSeenMs = meta["lastSeen"].asLongOrNull()
            ?: meta["lastSeenMs"].asLongOrNull()
            ?: nowProvider()

        val outfitMap = meta["outfitColors"].asStringMap()
        val rawTunic = outfitMap?.get("tunic").asIntOrNull() ?: meta["tunicColorIndex"].asIntOrNull() ?: existing?.tunicColorIndex ?: 0
        val rawHair = outfitMap?.get("hair").asIntOrNull() ?: meta["hairColorIndex"].asIntOrNull() ?: existing?.hairColorIndex ?: 0
        val rawFacing = (meta["facing"] as? String) ?: (meta["direction"] as? String)
        val parsedDir = rawFacing?.let { MultiplayerSanitizer.safeDirection(it) }
            ?: if (moved) inferDirection(existing.x, existing.y, cx, cy) else (existing?.direction ?: com.example.game.model.Direction.SOUTH)
        val parsedAction = (meta["actionState"] as? String)?.let { MultiplayerSanitizer.safeActionState(it) }
            ?: if (moved) PlayerActionState.WALKING else (existing?.actionState ?: PlayerActionState.IDLE)
        val actionTriggered = parsedAction == PlayerActionState.ATTACKING ||
            parsedAction == PlayerActionState.CASTING ||
            parsedAction == PlayerActionState.HEALING

        fun parseGearId(primaryKey: String, altKey: String?, existingVal: String?): String? {
            val hasKey = meta.containsKey(primaryKey) || (altKey != null && meta.containsKey(altKey))
            if (!hasKey) return existingVal
            val raw = (meta[primaryKey] as? String) ?: altKey?.let { meta[it] as? String }
            return raw?.trim()?.take(40)?.filter { it.isLetterOrDigit() || it == '_' }?.takeIf { it.isNotEmpty() }
        }

        val parsedWeaponId = parseGearId("weaponId", null, existing?.weaponId)
        val parsedOffhandId = parseGearId("offhandId", "shieldId", existing?.offhandId)
        val parsedArmorId = parseGearId("armorId", null, existing?.armorId)
        val parsedHelmetId = parseGearId("helmetId", null, existing?.helmetId)
        val parsedBootsId = parseGearId("bootsId", null, existing?.bootsId)

        return RemotePlayerState(
            playerId = id,
            name = MultiplayerSanitizer.sanitizeName(meta["name"] as? String ?: existing?.name),
            level = (meta["level"].asIntOrNull() ?: existing?.level ?: 1).coerceIn(1, 999),
            vocation = MultiplayerSanitizer.safeVocation(meta["vocation"] as? String ?: existing?.vocation?.name),
            tunicColorIndex = MultiplayerSanitizer.clampTunicIndex(rawTunic),
            hairColorIndex = MultiplayerSanitizer.clampHairIndex(rawHair),
            x = cx,
            y = cy,
            prevX = if (moved) existing.x else cx,
            prevY = if (moved) existing.y else cy,
            lastMoveMs = if (moved) animNow else (existing?.lastMoveMs ?: animNow),
            floor = floor,
            hp = hp,
            maxHp = maxHp,
            joinedAt = joinedAt,
            isPoisoned = (meta["psn"] as? Boolean) ?: existing?.isPoisoned ?: false,
            isBurning = (meta["brn"] as? Boolean) ?: existing?.isBurning ?: false,
            direction = parsedDir,
            actionState = parsedAction,
            lastActionMs = if (actionTriggered) animNow else (existing?.lastActionMs ?: 0L),
            regionId = regionId,
            lastSeenMs = lastSeenMs,
            weaponId = parsedWeaponId,
            offhandId = parsedOffhandId,
            armorId = parsedArmorId,
            helmetId = parsedHelmetId,
            bootsId = parsedBootsId
        )
    }

    private fun inferDirection(fromX: Int, fromY: Int, toX: Int, toY: Int): com.example.game.model.Direction {
        val dx = toX - fromX
        val dy = toY - fromY
        return when {
            abs(dx) >= abs(dy) ->
                if (dx >= 0) com.example.game.model.Direction.EAST else com.example.game.model.Direction.WEST
            dy >= 0 -> com.example.game.model.Direction.SOUTH
            else -> com.example.game.model.Direction.NORTH
        }
    }

    private fun extractFirstMeta(entry: Map<String, Any?>): Map<String, Any?>? {
        val metas = entry["metas"] as? List<*> ?: return entry
        return metas.firstOrNull().asStringMap()
    }

    private fun playerPayloadToMap(p: LocalPlayerPresencePayload): Map<String, Any?> = mapOf(
        "playerId" to p.playerId,
        "joinedAt" to p.joinedAt,
        "lastSeen" to (if (p.lastSeenMs > 0L) p.lastSeenMs else nowProvider()),
        "name" to MultiplayerSanitizer.sanitizeName(p.name),
        "level" to p.level,
        "vocation" to p.vocation.name,
        "tunicColorIndex" to p.tunicColorIndex,
        "hairColorIndex" to p.hairColorIndex,
        "outfitColors" to mapOf(
            "tunic" to p.tunicColorIndex,
            "hair" to p.hairColorIndex
        ),
        "x" to p.x,
        "y" to p.y,
        "floor" to p.floor,
        "regionId" to normalizeRegionId(p.regionId),
        "facing" to p.direction.name,
        "direction" to p.direction.name,
        "actionState" to p.actionState.name,
        "hp" to p.hp,
        "maxHp" to p.maxHp,
        "psn" to p.isPoisoned,
        "brn" to p.isBurning,
        "weaponId" to (p.weaponId ?: ""),
        "offhandId" to (p.offhandId ?: ""),
        "armorId" to (p.armorId ?: ""),
        "helmetId" to (p.helmetId ?: ""),
        "bootsId" to (p.bootsId ?: "")
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
