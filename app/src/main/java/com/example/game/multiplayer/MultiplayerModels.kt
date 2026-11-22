package com.example.game.multiplayer

import com.example.game.model.AdventurerPalette
import com.example.game.model.Vocation
import com.example.game.world.WorldMaps

enum class ConnectionStatus {
    OFFLINE_SOLO,
    CONNECTING,
    ONLINE
}

enum class ChatChannel(val label: String) {
    FLOOR("Floor"),
    WORLD("World"),
    SYSTEM("System")
}

data class ChatMessage(
    val id: String,
    val senderName: String,
    val text: String,
    val channel: ChatChannel,
    val floor: Int,
    val timestampMs: Long,
    val isLocal: Boolean = false
)

data class RemotePlayerState(
    val playerId: String,
    val name: String,
    val level: Int,
    val vocation: Vocation,
    val tunicColorIndex: Int,
    val hairColorIndex: Int,
    val x: Int,
    val y: Int,
    val prevX: Int = x,
    val prevY: Int = y,
    val lastMoveMs: Long = 0L,
    val floor: Int,
    val hp: Int,
    val maxHp: Int,
    val joinedAt: Long = 0L,
    val isPoisoned: Boolean = false,
    val isBurning: Boolean = false
)

data class LocalPlayerPresencePayload(
    val playerId: String,
    val name: String,
    val level: Int,
    val vocation: Vocation,
    val tunicColorIndex: Int,
    val hairColorIndex: Int,
    val x: Int,
    val y: Int,
    val floor: Int,
    val hp: Int,
    val maxHp: Int,
    val joinedAt: Long,
    val isPoisoned: Boolean = false,
    val isBurning: Boolean = false
)

data class SyncedMonsterSnapshot(
    val id: Int,
    val typeName: String,
    val floor: Int,
    val x: Int,
    val y: Int,
    val spawnX: Int,
    val spawnY: Int,
    val hp: Int,
    val maxHp: Int,
    val targetPlayerId: String? = null,
    val isSummon: Boolean = false,
    val pendingAoeTiles: List<Pair<Int, Int>> = emptyList()
)

data class RemoteMonsterHitEvent(
    val monsterId: Int,
    val floor: Int,
    val attackerId: String,
    val attackerName: String,
    val damage: Int,
    val resultingHp: Int,
    val projectileKindName: String? = null,
    val fromX: Int = 0,
    val fromY: Int = 0
)

data class LocalMonsterHitEvent(
    val monsterId: Int,
    val floor: Int,
    val damage: Int,
    val resultingHp: Int,
    val monsterTypeName: String,
    val isBoss: Boolean,
    val projectileKindName: String? = null,
    val fromX: Int = 0,
    val fromY: Int = 0
)

data class LocalMonsterAttackEvent(
    val monsterId: Int,
    val floor: Int,
    val fromX: Int,
    val fromY: Int,
    val toX: Int,
    val toY: Int,
    val projectileKindName: String? = null,
    val aoeTiles: List<Pair<Int, Int>> = emptyList(),
    val targetPlayerId: String? = null,
    val inflictedStatus: String? = null
)

data class RemoteMonsterAttackEvent(
    val monsterId: Int,
    val floor: Int,
    val fromX: Int,
    val fromY: Int,
    val toX: Int,
    val toY: Int,
    val projectileKindName: String? = null,
    val aoeTiles: List<Pair<Int, Int>> = emptyList(),
    val targetPlayerId: String? = null,
    val inflictedStatus: String? = null
)

data class MultiplayerUiState(
    val status: ConnectionStatus = ConnectionStatus.OFFLINE_SOLO,
    val isFloorHost: Boolean = true,
    val hostPlayerId: String = "",
    val floorOnlineCount: Int = 1,
    val remotePlayers: List<RemotePlayerState> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val chatExpanded: Boolean = false,
    val unreadChatCount: Int = 0,
    val activeChatChannel: ChatChannel = ChatChannel.FLOOR
)

/**
 * Deterministic floor host election:
 * Chooses the earliest-connected player on the floor (smallest joinedAt),
 * breaking ties lexicographically by playerId so every client elects the exact same host.
 */
fun electFloorHost(
    localPlayerId: String,
    localJoinedAt: Long,
    remotePlayers: List<RemotePlayerState>
): String {
    val candidates = buildList {
        if (localPlayerId.isNotBlank()) add(localPlayerId to localJoinedAt)
        remotePlayers.forEach { add(it.playerId to it.joinedAt) }
    }
    return candidates
        .sortedWith(compareBy<Pair<String, Long>> { it.second }.thenBy { it.first })
        .firstOrNull()
        ?.first
        ?: localPlayerId
}

object MultiplayerSanitizer {
    private val validFloors = setOf(0, -1, -2, -3, -4)
    private val controlRegex = Regex("[\\p{Cntrl}&&[^\t]]")

    fun isValidFloor(floor: Int): Boolean = floor in validFloors

    fun sanitizeName(raw: String?): String {
        val cleaned = raw.orEmpty()
            .replace(controlRegex, "")
            .replace("\n", " ")
            .replace("\r", " ")
            .trim()
            .take(18)
        return cleaned.ifEmpty { "Adventurer" }
    }

    fun sanitizeChatText(raw: String?): String {
        return raw.orEmpty()
            .replace(controlRegex, "")
            .replace("\n", " ")
            .replace("\r", " ")
            .trim()
            .take(140)
    }

    fun sanitizeId(raw: String?): String {
        return raw.orEmpty()
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(64)
    }

    fun safeVocation(raw: String?): Vocation =
        runCatching { Vocation.valueOf(raw.orEmpty()) }.getOrDefault(Vocation.ADVENTURER)

    fun clampTunicIndex(idx: Int): Int = idx.coerceIn(0, AdventurerPalette.tunicColors.lastIndex)
    fun clampHairIndex(idx: Int): Int = idx.coerceIn(0, AdventurerPalette.hairColors.lastIndex)

    fun clampCoord(floor: Int, x: Int, y: Int): Pair<Int, Int> {
        val fl = WorldMaps.getFloor(floor)
        return x.coerceIn(0, fl.width - 1) to y.coerceIn(0, fl.height - 1)
    }
}
