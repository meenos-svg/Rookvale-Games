package com.example.game.multiplayer

import com.example.game.model.AdventurerPalette
import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.Direction
import com.example.game.model.FloatingTextKind
import com.example.game.model.Vocation
import com.example.game.model.normalizeRegionId
import com.example.game.world.WorldMaps

enum class PlayerActionState {
    IDLE,
    WALKING,
    ATTACKING,
    CASTING,
    HEALING
}

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
    val isLocal: Boolean = false,
    val senderIsAdmin: Boolean = false
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
    val isBurning: Boolean = false,
    val direction: Direction = Direction.SOUTH,
    val actionState: PlayerActionState = PlayerActionState.IDLE,
    val lastActionMs: Long = 0L,
    val regionId: String = DEFAULT_REGION_ID,
    val lastSeenMs: Long = joinedAt,
    val weaponId: String? = null,
    val offhandId: String? = null,
    val armorId: String? = null,
    val helmetId: String? = null,
    val bootsId: String? = null,
    val targetX: Int = x,
    val targetY: Int = y,
    val moveSpeed: Float = RemotePlayerInterpolator.WALK_SPEED_TILES_PER_SEC,
    val networkReceivedMs: Long = lastMoveMs,
    val isAdmin: Boolean = false,
    val isSpectator: Boolean = false
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
    val isBurning: Boolean = false,
    val direction: Direction = Direction.SOUTH,
    val actionState: PlayerActionState = PlayerActionState.IDLE,
    val regionId: String = DEFAULT_REGION_ID,
    val lastSeenMs: Long = joinedAt,
    val weaponId: String? = null,
    val offhandId: String? = null,
    val armorId: String? = null,
    val helmetId: String? = null,
    val bootsId: String? = null,
    val fromX: Int = x,
    val fromY: Int = y,
    val targetX: Int = x,
    val targetY: Int = y,
    val moveSpeed: Float = RemotePlayerInterpolator.WALK_SPEED_TILES_PER_SEC,
    val isAdmin: Boolean = false,
    val isSpectator: Boolean = false
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

data class LocalPlayerActionFxEvent(
    val floor: Int,
    val x: Int,
    val y: Int,
    val text: String? = null,
    val floatingKindName: String? = null,
    val actionState: PlayerActionState = PlayerActionState.IDLE,
    val projectileKindName: String? = null,
    val toX: Int? = null,
    val toY: Int? = null
)

data class RemotePlayerActionFxEvent(
    val playerId: String,
    val floor: Int,
    val x: Int,
    val y: Int,
    val text: String? = null,
    val floatingKind: FloatingTextKind? = null,
    val actionState: PlayerActionState = PlayerActionState.IDLE,
    val projectileKindName: String? = null,
    val toX: Int? = null,
    val toY: Int? = null
)

data class SharedLootSpawnEvent(
    val corpseId: String,
    val floor: Int,
    val x: Int,
    val y: Int,
    val label: String,
    val ownerPlayerId: String,
    val ownerName: String,
    val isDroppedBag: Boolean,
    val monsterTypeName: String? = null,
    val gold: Int = 0,
    val items: List<com.example.game.model.ItemStack> = emptyList(),
    val lockRemainingMs: Long = com.example.game.model.LOOT_OWNER_PROTECTION_MS,
    val lifetimeRemainingMs: Long = com.example.game.model.REGULAR_CORPSE_LIFETIME_MS
)

data class SharedLootClaimEvent(
    val corpseId: String,
    val floor: Int,
    val claimerId: String,
    val claimerName: String,
    val remainingGold: Int = 0,
    val remainingItems: List<com.example.game.model.ItemStack> = emptyList(),
    val emptied: Boolean = false
)

enum class TradeSessionStatus {
    INVITING,
    INCOMING_INVITE,
    NEGOTIATING
}

enum class TradeSignalType {
    INVITE,
    ACCEPT,
    DECLINE,
    OFFER,
    LOCK,
    CONFIRM,
    CANCEL
}

data class TradeOfferSnapshot(
    val gold: Int = 0,
    val items: List<com.example.game.model.ItemStack> = emptyList(),
    val locked: Boolean = false,
    val confirmed: Boolean = false,
    val version: Int = 0
)

data class PlayerTradeSession(
    val tradeId: String,
    val partnerId: String,
    val partnerName: String,
    val partnerLevel: Int,
    val partnerVocation: Vocation,
    val status: TradeSessionStatus,
    val expiresAtMs: Long = 0L,
    val localOffer: TradeOfferSnapshot = TradeOfferSnapshot(),
    val partnerOffer: TradeOfferSnapshot = TradeOfferSnapshot(),
    val statusNotice: String? = null
)

data class TradeSignalEvent(
    val type: TradeSignalType,
    val tradeId: String,
    val fromPlayerId: String,
    val fromPlayerName: String,
    val fromLevel: Int = 1,
    val fromVocation: Vocation = Vocation.ADVENTURER,
    val toPlayerId: String,
    val gold: Int = 0,
    val items: List<com.example.game.model.ItemStack> = emptyList(),
    val locked: Boolean = false,
    val confirmed: Boolean = false,
    val offerVersion: Int = 0,
    val reason: String? = null
)

data class MultiplayerUiState(
    val status: ConnectionStatus = ConnectionStatus.OFFLINE_SOLO,
    val isFloorHost: Boolean = true,
    val hostPlayerId: String = "",
    val floorOnlineCount: Int = 1,
    val remotePlayers: List<RemotePlayerState> = emptyList(),
    val realmOnlineCount: Int = 1,
    val realmOnlinePlayers: List<RemotePlayerState> = emptyList(),
    val pingMs: Int? = null,
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
    private val validFloors = (com.example.game.world.BiomeProgressionRules.MIN_DUNGEON_FLOOR..0).toSet()
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

    fun safeDirection(raw: String?): Direction =
        runCatching { Direction.valueOf(raw.orEmpty().uppercase()) }.getOrDefault(Direction.SOUTH)

    fun safeActionState(raw: String?): PlayerActionState =
        runCatching { PlayerActionState.valueOf(raw.orEmpty().uppercase()) }.getOrDefault(PlayerActionState.IDLE)

    fun safeFloatingKind(raw: String?): FloatingTextKind? =
        raw?.let { runCatching { FloatingTextKind.valueOf(it.uppercase()) }.getOrNull() }

    fun sanitizeFloatingText(raw: String?): String? {
        val cleaned = raw.orEmpty()
            .replace(controlRegex, "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
            .take(20)
        return cleaned.ifEmpty { null }
    }

    fun clampTunicIndex(idx: Int): Int = idx.coerceIn(0, AdventurerPalette.tunicColors.lastIndex)
    fun clampHairIndex(idx: Int): Int = idx.coerceIn(0, AdventurerPalette.hairColors.lastIndex)

    fun sanitizeRegionId(raw: String?): String = normalizeRegionId(raw)

    fun clampCoord(floor: Int, x: Int, y: Int): Pair<Int, Int> {
        val fl = WorldMaps.getFloor(floor)
        return x.coerceIn(0, fl.width - 1) to y.coerceIn(0, fl.height - 1)
    }
}
