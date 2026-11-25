package com.example.game.multiplayer

import com.example.game.engine.GameEngine
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemStack
import java.util.UUID

/**
 * Pure, testable state-machine coordinator for Rookvale's real-time two-step player-to-player trade.
 *
 * Guarantees:
 * - Editing either offer always increments `offerVersion` and resets both `locked` and `confirmed`
 *   flags so neither player can bait-and-switch after locking.
 * - `CONFIRM` is only accepted when both sides are locked on matching offer versions.
 * - Completed trade IDs are tracked so duplicate `CONFIRM` frames can never execute a swap twice.
 */
object PlayerTradeCoordinator {

    const val INVITE_TIMEOUT_MS: Long = 20_000L

    data class TransitionResult(
        val session: PlayerTradeSession?,
        val worldState: GameWorldState,
        val outboundSignal: TradeSignalEvent? = null,
        val swapCompleted: Boolean = false,
        val logMessage: String? = null
    )

    fun startOutgoingInvite(
        world: GameWorldState,
        currentSession: PlayerTradeSession?,
        partner: RemotePlayerState,
        nowMs: Long
    ): TransitionResult {
        if (world.deathReport != null) {
            return TransitionResult(currentSession, world, logMessage = "You cannot trade while defeated.")
        }
        if (partner.playerId.isBlank() || partner.playerId == world.saveId) {
            return TransitionResult(currentSession, world, logMessage = "Cannot trade with yourself.")
        }
        if (currentSession != null) {
            return TransitionResult(currentSession, world, logMessage = "Finish or cancel your active trade first.")
        }
        val tradeId = "tr_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val session = PlayerTradeSession(
            tradeId = tradeId,
            partnerId = partner.playerId,
            partnerName = partner.name,
            partnerLevel = partner.level,
            partnerVocation = partner.vocation,
            status = TradeSessionStatus.INVITING,
            expiresAtMs = nowMs + INVITE_TIMEOUT_MS,
            statusNotice = "Waiting for ${partner.name} to accept..."
        )
        val signal = TradeSignalEvent(
            type = TradeSignalType.INVITE,
            tradeId = tradeId,
            fromPlayerId = world.saveId,
            fromPlayerName = world.playerName,
            fromLevel = world.level,
            fromVocation = world.vocation,
            toPlayerId = partner.playerId
        )
        return TransitionResult(
            session = session,
            worldState = world.copy(inspectedPlayerId = null, inspectedPlayerUntilMs = 0L),
            outboundSignal = signal,
            logMessage = "Sent trade request to ${partner.name}."
        )
    }

    fun acceptIncomingInvite(
        world: GameWorldState,
        currentSession: PlayerTradeSession?
    ): TransitionResult {
        val session = currentSession ?: return TransitionResult(null, world)
        if (session.status != TradeSessionStatus.INCOMING_INVITE) return TransitionResult(session, world)
        if (world.deathReport != null) {
            return cancelTrade(world, session, "Cannot trade while defeated.")
        }
        val active = session.copy(
            status = TradeSessionStatus.NEGOTIATING,
            expiresAtMs = 0L,
            statusNotice = "Add items or gold, then Lock Offer."
        )
        val signal = TradeSignalEvent(
            type = TradeSignalType.ACCEPT,
            tradeId = session.tradeId,
            fromPlayerId = world.saveId,
            fromPlayerName = world.playerName,
            fromLevel = world.level,
            fromVocation = world.vocation,
            toPlayerId = session.partnerId
        )
        return TransitionResult(
            session = active,
            worldState = world.copy(activePanel = com.example.game.model.ActivePanel.NONE),
            outboundSignal = signal,
            logMessage = "Opened trade with ${session.partnerName}."
        )
    }

    fun declineIncomingInvite(
        world: GameWorldState,
        currentSession: PlayerTradeSession?
    ): TransitionResult {
        val session = currentSession ?: return TransitionResult(null, world)
        val signal = TradeSignalEvent(
            type = TradeSignalType.DECLINE,
            tradeId = session.tradeId,
            fromPlayerId = world.saveId,
            fromPlayerName = world.playerName,
            fromLevel = world.level,
            fromVocation = world.vocation,
            toPlayerId = session.partnerId,
            reason = "${world.playerName} declined the trade."
        )
        return TransitionResult(
            session = null,
            worldState = world,
            outboundSignal = signal,
            logMessage = "Declined trade from ${session.partnerName}."
        )
    }

    fun cancelTrade(
        world: GameWorldState,
        currentSession: PlayerTradeSession?,
        reason: String = "Trade cancelled."
    ): TransitionResult {
        val session = currentSession ?: return TransitionResult(null, world)
        val signal = TradeSignalEvent(
            type = TradeSignalType.CANCEL,
            tradeId = session.tradeId,
            fromPlayerId = world.saveId,
            fromPlayerName = world.playerName,
            fromLevel = world.level,
            fromVocation = world.vocation,
            toPlayerId = session.partnerId,
            reason = "${world.playerName} cancelled the trade."
        )
        return TransitionResult(
            session = null,
            worldState = world,
            outboundSignal = signal,
            logMessage = reason
        )
    }

    fun addLocalItem(
        world: GameWorldState,
        currentSession: PlayerTradeSession?,
        itemId: String,
        delta: Int = 1
    ): TransitionResult {
        val session = currentSession ?: return TransitionResult(null, world)
        if (session.status != TradeSessionStatus.NEGOTIATING || delta <= 0) return TransitionResult(session, world)
        val def = ItemCatalog.get(itemId)
        if (def.adminOnly) {
            return TransitionResult(
                session = session.copy(statusNotice = "${def.name} is Admin-bound and cannot be traded."),
                worldState = world
            )
        }
        val owned = world.itemCount(itemId)
        val currentOffered = session.localOffer.items.firstOrNull { it.itemId == itemId }?.count ?: 0
        val nextCount = (currentOffered + delta).coerceAtMost(owned)
        if (nextCount == currentOffered) {
            return TransitionResult(
                session = session.copy(statusNotice = "No more ${def.name} available in your backpack."),
                worldState = world
            )
        }
        val updatedItems = updateStackCount(session.localOffer.items, itemId, nextCount)
        return applyLocalOfferEdit(world, session, session.localOffer.gold, updatedItems)
    }

    fun removeLocalItem(
        world: GameWorldState,
        currentSession: PlayerTradeSession?,
        itemId: String,
        delta: Int = 1
    ): TransitionResult {
        val session = currentSession ?: return TransitionResult(null, world)
        if (session.status != TradeSessionStatus.NEGOTIATING || delta <= 0) return TransitionResult(session, world)
        val currentOffered = session.localOffer.items.firstOrNull { it.itemId == itemId }?.count ?: 0
        if (currentOffered <= 0) return TransitionResult(session, world)
        val nextCount = (currentOffered - delta).coerceAtLeast(0)
        val updatedItems = updateStackCount(session.localOffer.items, itemId, nextCount)
        return applyLocalOfferEdit(world, session, session.localOffer.gold, updatedItems)
    }

    fun setLocalGold(
        world: GameWorldState,
        currentSession: PlayerTradeSession?,
        requestedGold: Int
    ): TransitionResult {
        val session = currentSession ?: return TransitionResult(null, world)
        if (session.status != TradeSessionStatus.NEGOTIATING) return TransitionResult(session, world)
        val clamped = requestedGold.coerceIn(0, world.gold.coerceAtLeast(0))
        if (clamped == session.localOffer.gold) return TransitionResult(session, world)
        return applyLocalOfferEdit(world, session, clamped, session.localOffer.items)
    }

    private fun applyLocalOfferEdit(
        world: GameWorldState,
        session: PlayerTradeSession,
        newGold: Int,
        newItems: List<ItemStack>
    ): TransitionResult {
        val wasLocked = session.localOffer.locked || session.partnerOffer.locked
        val nextVersion = session.localOffer.version + 1
        val nextLocal = TradeOfferSnapshot(
            gold = newGold,
            items = newItems,
            locked = false,
            confirmed = false,
            version = nextVersion
        )
        val nextPartner = session.partnerOffer.copy(locked = false, confirmed = false)
        val notice = if (wasLocked) "Offer updated — both players must re-lock." else null
        val updated = session.copy(
            localOffer = nextLocal,
            partnerOffer = nextPartner,
            statusNotice = notice
        )
        val signal = TradeSignalEvent(
            type = TradeSignalType.OFFER,
            tradeId = session.tradeId,
            fromPlayerId = world.saveId,
            fromPlayerName = world.playerName,
            fromLevel = world.level,
            fromVocation = world.vocation,
            toPlayerId = session.partnerId,
            gold = newGold,
            items = newItems,
            locked = false,
            confirmed = false,
            offerVersion = nextVersion
        )
        return TransitionResult(session = updated, worldState = world, outboundSignal = signal)
    }

    fun toggleLocalLock(
        world: GameWorldState,
        currentSession: PlayerTradeSession?
    ): TransitionResult {
        val session = currentSession ?: return TransitionResult(null, world)
        if (session.status != TradeSessionStatus.NEGOTIATING) return TransitionResult(session, world)

        val nextLocked = !session.localOffer.locked
        if (nextLocked) {
            val canCarry = GameEngine.canCarryTradeDelta(
                state = world,
                outgoingGold = session.localOffer.gold,
                outgoingItems = session.localOffer.items,
                incomingGold = session.partnerOffer.gold,
                incomingItems = session.partnerOffer.items
            )
            if (!canCarry) {
                return TransitionResult(
                    session = session.copy(statusNotice = "Too heavy! Not enough Cap (oz) to accept ${session.partnerName}'s offer."),
                    worldState = world
                )
            }
        }
        val nextLocal = session.localOffer.copy(locked = nextLocked, confirmed = false)
        val nextPartner = if (!nextLocked) session.partnerOffer.copy(confirmed = false) else session.partnerOffer
        val notice = when {
            nextLocked && nextPartner.locked -> "Both offers locked — tap Confirm Trade to swap!"
            nextLocked -> "Offer locked — waiting for ${session.partnerName} to lock."
            else -> "Offer unlocked for editing."
        }
        val updated = session.copy(
            localOffer = nextLocal,
            partnerOffer = nextPartner,
            statusNotice = notice
        )
        val signal = TradeSignalEvent(
            type = TradeSignalType.LOCK,
            tradeId = session.tradeId,
            fromPlayerId = world.saveId,
            fromPlayerName = world.playerName,
            fromLevel = world.level,
            fromVocation = world.vocation,
            toPlayerId = session.partnerId,
            gold = nextLocal.gold,
            items = nextLocal.items,
            locked = nextLocked,
            confirmed = false,
            offerVersion = nextLocal.version
        )
        return TransitionResult(session = updated, worldState = world, outboundSignal = signal)
    }

    fun confirmTrade(
        world: GameWorldState,
        currentSession: PlayerTradeSession?,
        completedTradeIds: MutableSet<String>,
        nowMs: Long
    ): TransitionResult {
        val session = currentSession ?: return TransitionResult(null, world)
        if (session.status != TradeSessionStatus.NEGOTIATING) return TransitionResult(session, world)
        if (!session.localOffer.locked || !session.partnerOffer.locked) {
            return TransitionResult(
                session = session.copy(statusNotice = "Both players must Lock Offer before confirming."),
                worldState = world
            )
        }
        if (session.localOffer.confirmed) return TransitionResult(session, world)

        val confirmSignal = TradeSignalEvent(
            type = TradeSignalType.CONFIRM,
            tradeId = session.tradeId,
            fromPlayerId = world.saveId,
            fromPlayerName = world.playerName,
            fromLevel = world.level,
            fromVocation = world.vocation,
            toPlayerId = session.partnerId,
            gold = session.localOffer.gold,
            items = session.localOffer.items,
            locked = true,
            confirmed = true,
            offerVersion = session.localOffer.version
        )

        if (session.partnerOffer.confirmed) {
            return finalizeSwap(world, session, confirmSignal, completedTradeIds, nowMs)
        }

        val waiting = session.copy(
            localOffer = session.localOffer.copy(confirmed = true),
            statusNotice = "Confirmed! Waiting for ${session.partnerName} to confirm..."
        )
        return TransitionResult(session = waiting, worldState = world, outboundSignal = confirmSignal)
    }

    fun handleIncomingSignal(
        world: GameWorldState,
        currentSession: PlayerTradeSession?,
        signal: TradeSignalEvent,
        completedTradeIds: MutableSet<String>,
        nowMs: Long
    ): TransitionResult {
        if (signal.toPlayerId != world.saveId || signal.fromPlayerId == world.saveId) {
            return TransitionResult(currentSession, world)
        }

        return when (signal.type) {
            TradeSignalType.INVITE -> {
                if (world.deathReport != null || currentSession != null) {
                    val busyReason = if (world.deathReport != null) {
                        "${world.playerName} cannot trade right now."
                    } else {
                        "${world.playerName} is busy in another trade."
                    }
                    TransitionResult(
                        session = currentSession,
                        worldState = world,
                        outboundSignal = TradeSignalEvent(
                            type = TradeSignalType.DECLINE,
                            tradeId = signal.tradeId,
                            fromPlayerId = world.saveId,
                            fromPlayerName = world.playerName,
                            fromLevel = world.level,
                            fromVocation = world.vocation,
                            toPlayerId = signal.fromPlayerId,
                            reason = busyReason
                        )
                    )
                } else {
                    val incoming = PlayerTradeSession(
                        tradeId = signal.tradeId,
                        partnerId = signal.fromPlayerId,
                        partnerName = signal.fromPlayerName,
                        partnerLevel = signal.fromLevel,
                        partnerVocation = signal.fromVocation,
                        status = TradeSessionStatus.INCOMING_INVITE,
                        expiresAtMs = nowMs + INVITE_TIMEOUT_MS,
                        statusNotice = "${signal.fromPlayerName} wants to trade."
                    )
                    TransitionResult(
                        session = incoming,
                        worldState = world,
                        logMessage = "${signal.fromPlayerName} sent you a trade request."
                    )
                }
            }

            TradeSignalType.ACCEPT -> {
                val cur = currentSession
                if (cur == null || cur.tradeId != signal.tradeId || cur.status != TradeSessionStatus.INVITING) {
                    return TransitionResult(currentSession, world)
                }
                TransitionResult(
                    session = cur.copy(
                        status = TradeSessionStatus.NEGOTIATING,
                        expiresAtMs = 0L,
                        statusNotice = "${signal.fromPlayerName} accepted! Add items or gold, then Lock Offer."
                    ),
                    worldState = world.copy(activePanel = com.example.game.model.ActivePanel.NONE),
                    logMessage = "${signal.fromPlayerName} accepted your trade request."
                )
            }

            TradeSignalType.DECLINE -> {
                val cur = currentSession
                if (cur == null || cur.tradeId != signal.tradeId) {
                    return TransitionResult(currentSession, world)
                }
                TransitionResult(
                    session = null,
                    worldState = world,
                    logMessage = signal.reason ?: "${signal.fromPlayerName} declined the trade."
                )
            }

            TradeSignalType.CANCEL -> {
                val cur = currentSession
                if (cur == null || cur.tradeId != signal.tradeId) {
                    return TransitionResult(currentSession, world)
                }
                TransitionResult(
                    session = null,
                    worldState = world,
                    logMessage = signal.reason ?: "${signal.fromPlayerName} cancelled the trade."
                )
            }

            TradeSignalType.OFFER -> {
                val cur = currentSession
                if (cur == null || cur.tradeId != signal.tradeId || cur.status != TradeSessionStatus.NEGOTIATING) {
                    return TransitionResult(currentSession, world)
                }
                if (signal.offerVersion < cur.partnerOffer.version) {
                    return TransitionResult(cur, world)
                }
                val wasLocked = cur.localOffer.locked || cur.partnerOffer.locked
                val updated = cur.copy(
                    localOffer = cur.localOffer.copy(locked = false, confirmed = false),
                    partnerOffer = TradeOfferSnapshot(
                        gold = signal.gold.coerceAtLeast(0),
                        items = signal.items.filter { it.count > 0 && it.itemId.isNotBlank() },
                        locked = false,
                        confirmed = false,
                        version = signal.offerVersion
                    ),
                    statusNotice = if (wasLocked) {
                        "${cur.partnerName} updated their offer — review & re-lock."
                    } else {
                        "${cur.partnerName} updated their offer."
                    }
                )
                TransitionResult(session = updated, worldState = world)
            }

            TradeSignalType.LOCK -> {
                val cur = currentSession
                if (cur == null || cur.tradeId != signal.tradeId || cur.status != TradeSessionStatus.NEGOTIATING) {
                    return TransitionResult(currentSession, world)
                }
                val unseenRevision = signal.offerVersion > cur.partnerOffer.version
                val nextPartner = TradeOfferSnapshot(
                    gold = signal.gold.coerceAtLeast(0),
                    items = signal.items.filter { it.count > 0 && it.itemId.isNotBlank() },
                    locked = signal.locked,
                    confirmed = if (!signal.locked) false else cur.partnerOffer.confirmed,
                    version = maxOf(cur.partnerOffer.version, signal.offerVersion)
                )
                val nextLocal = if (unseenRevision || !signal.locked) {
                    cur.localOffer.copy(
                        locked = if (unseenRevision) false else cur.localOffer.locked,
                        confirmed = false
                    )
                } else {
                    cur.localOffer
                }
                val notice = when {
                    unseenRevision -> "${cur.partnerName} updated & locked — review before locking."
                    nextPartner.locked && nextLocal.locked -> "Both offers locked — tap Confirm Trade!"
                    nextPartner.locked -> "${cur.partnerName} locked their offer."
                    else -> "${cur.partnerName} unlocked their offer."
                }
                TransitionResult(
                    session = cur.copy(
                        localOffer = nextLocal,
                        partnerOffer = nextPartner,
                        statusNotice = notice
                    ),
                    worldState = world
                )
            }

            TradeSignalType.CONFIRM -> {
                val cur = currentSession
                if (cur == null || cur.tradeId != signal.tradeId || cur.status != TradeSessionStatus.NEGOTIATING) {
                    return TransitionResult(currentSession, world)
                }
                if (signal.offerVersion != cur.partnerOffer.version || !cur.partnerOffer.locked) {
                    return TransitionResult(
                        session = cur.copy(
                            localOffer = cur.localOffer.copy(locked = false, confirmed = false),
                            partnerOffer = cur.partnerOffer.copy(locked = false, confirmed = false),
                            statusNotice = "Offer mismatch detected — please re-lock to verify."
                        ),
                        worldState = world
                    )
                }
                val updated = cur.copy(
                    partnerOffer = cur.partnerOffer.copy(confirmed = true),
                    statusNotice = "${cur.partnerName} confirmed! Tap Confirm Trade to finish."
                )
                if (updated.localOffer.confirmed) {
                    finalizeSwap(world, updated, outboundOnSuccess = null, completedTradeIds, nowMs)
                } else {
                    TransitionResult(session = updated, worldState = world)
                }
            }
        }
    }

    private fun finalizeSwap(
        world: GameWorldState,
        session: PlayerTradeSession,
        outboundOnSuccess: TradeSignalEvent?,
        completedTradeIds: MutableSet<String>,
        nowMs: Long
    ): TransitionResult {
        if (!completedTradeIds.add(session.tradeId)) {
            return TransitionResult(session = null, worldState = world)
        }
        val (swappedWorld, error) = GameEngine.executePlayerTradeSwap(
            state = world,
            partnerName = session.partnerName,
            outgoingGold = session.localOffer.gold,
            outgoingItems = session.localOffer.items,
            incomingGold = session.partnerOffer.gold,
            incomingItems = session.partnerOffer.items,
            nowMs = nowMs
        )
        if (error != null) {
            completedTradeIds.remove(session.tradeId)
            return TransitionResult(
                session = null,
                worldState = world,
                outboundSignal = TradeSignalEvent(
                    type = TradeSignalType.CANCEL,
                    tradeId = session.tradeId,
                    fromPlayerId = world.saveId,
                    fromPlayerName = world.playerName,
                    fromLevel = world.level,
                    fromVocation = world.vocation,
                    toPlayerId = session.partnerId,
                    reason = "Trade aborted: $error"
                ),
                logMessage = error
            )
        }
        return TransitionResult(
            session = null,
            worldState = swappedWorld,
            outboundSignal = outboundOnSuccess,
            swapCompleted = true
        )
    }

    private fun updateStackCount(
        current: List<ItemStack>,
        itemId: String,
        newCount: Int
    ): List<ItemStack> {
        val list = current.toMutableList()
        val idx = list.indexOfFirst { it.itemId == itemId }
        if (newCount <= 0) {
            if (idx >= 0) list.removeAt(idx)
        } else if (idx >= 0) {
            list[idx] = ItemStack(itemId, newCount)
        } else {
            list.add(ItemStack(itemId, newCount))
        }
        return list
    }
}
