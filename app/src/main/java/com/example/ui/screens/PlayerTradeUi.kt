package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.engine.GameEngine
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemStack
import com.example.game.multiplayer.PlayerTradeSession
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.multiplayer.TradeOfferSnapshot
import com.example.game.multiplayer.TradeSessionStatus
import com.example.game.sprites.EntitySpriteAtlas
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.ManaSapphire
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.StoneBorderDark
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.StoneElevated
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold

/**
 * Compact floating Adventurer Action Card displayed when the player taps a remote adventurer
 * on the isometric map, providing immediate Trade initiation alongside player info.
 */
@Composable
fun AdventurerActionCard(
    peer: RemotePlayerState,
    canTrade: Boolean,
    onTrade: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = BasaltDark.copy(alpha = 0.96f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.4.dp, TorchGold.copy(alpha = 0.8f)),
        shadowElevation = 10.dp,
        modifier = modifier
            .widthIn(min = 240.dp, max = 320.dp)
            .pointerInput(Unit) { detectTapGestures { /* Consume taps */ } }
            .testTag("adventurer_action_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(ExpEmerald)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (peer.isAdmin) TorchGold else Color(0xFF8CF0FF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Lvl ${peer.level} ${peer.vocation.displayName} • ${peer.hp}/${peer.maxHp} HP",
                    style = MaterialTheme.typography.labelSmall,
                    color = ParchmentMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onTrade,
                enabled = canTrade,
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TorchGold,
                    contentColor = ObsidianVoid,
                    disabledContainerColor = StoneSurface,
                    disabledContentColor = ParchmentMuted
                ),
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .testTag("adventurer_card_trade_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Trade",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Trade",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("adventurer_card_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss player card",
                    tint = ParchmentMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Floating banner shown at top-center for pending outgoing (`INVITING`) or incoming
 * (`INCOMING_INVITE`) player trade invitations.
 */
@Composable
fun TradeInviteBanner(
    session: PlayerTradeSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isIncoming = session.status == TradeSessionStatus.INCOMING_INVITE
    Surface(
        color = BasaltDark.copy(alpha = 0.97f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.6.dp, if (isIncoming) TorchGold else ManaSapphire),
        shadowElevation = 12.dp,
        modifier = modifier
            .widthIn(min = 280.dp, max = 440.dp)
            .pointerInput(Unit) { detectTapGestures { /* Consume taps */ } }
            .testTag("trade_invite_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Trade Request",
                tint = if (isIncoming) TorchGold else ManaSapphire,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isIncoming) {
                        "${session.partnerName} wants to trade"
                    } else {
                        "Waiting for ${session.partnerName}..."
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = ParchmentLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Lvl ${session.partnerLevel} ${session.partnerVocation.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ParchmentMuted,
                    maxLines = 1
                )
            }
            if (isIncoming) {
                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpEmerald,
                        contentColor = ObsidianVoid
                    ),
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .testTag("trade_invite_accept_button")
                ) {
                    Text("Accept", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold))
                }
                OutlinedButton(
                    onClick = onDecline,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, HealthCrimson.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .testTag("trade_invite_decline_button")
                ) {
                    Text("Decline", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFF8A8A))
                }
            } else {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, StoneBorderLight),
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .testTag("trade_invite_cancel_button")
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelMedium, color = ParchmentLight)
                }
            }
        }
    }
}

/**
 * Dual-pane medieval Rookvale Trade Ledger modal supporting simultaneous items + gold offers,
 * real-time Cap (oz) validation, auto-unlock on any edit, and two-step Lock & Confirm exchange.
 */
@Composable
fun PlayerTradeLedgerModal(
    state: GameWorldState,
    session: PlayerTradeSession,
    onAddItem: (String, Int) -> Unit,
    onRemoveItem: (String, Int) -> Unit,
    onSetGold: (Int) -> Unit,
    onToggleLock: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCancel)

    var itemQty by remember { mutableIntStateOf(1) }
    val netDeltaOz = remember(session.localOffer, session.partnerOffer) {
        GameEngine.tradeNetWeightDeltaOz(
            outgoingGold = session.localOffer.gold,
            outgoingItems = session.localOffer.items,
            incomingGold = session.partnerOffer.gold,
            incomingItems = session.partnerOffer.items
        )
    }
    val capFits = state.remainingCapOz() + 1e-6 >= netDeltaOz
    val bothLocked = session.localOffer.locked && session.partnerOffer.locked
    val tradeableStacks = remember(state.backpack, state.toolBag, session.localOffer.items) {
        val offeredById = session.localOffer.items.associate { it.itemId to it.count }
        (state.backpack + state.toolBag)
            .groupBy { it.itemId }
            .map { (id, list) -> ItemStack(id, list.sumOf { it.count }) }
            .filter { stack ->
                val def = ItemCatalog.get(stack.itemId)
                !def.adminOnly && (stack.count - (offeredById[stack.itemId] ?: 0)) > 0
            }
    }

    Surface(
        color = BasaltDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.6.dp, TorchGold.copy(alpha = 0.75f)),
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .pointerInput(Unit) { detectTapGestures { /* Consume taps on modal */ } }
            .testTag("player_trade_modal")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StoneElevated)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = TorchGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "TRADE LEDGER • ${session.partnerName.uppercase()}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = TorchGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val deltaSign = if (netDeltaOz >= 0.0) "+" else ""
                        Text(
                            text = "Lvl ${session.partnerLevel} ${session.partnerVocation.displayName}  •  Cap Δ ${deltaSign}${formatOz(netDeltaOz)} oz (Free: ${formatOz(state.remainingCapOz())} oz)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (capFits) ParchmentMuted else Color(0xFFFF7B7B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("trade_modal_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel trade",
                        tint = ParchmentLight
                    )
                }
            }

            // Status notice strip
            if (!session.statusNotice.isNullOrBlank()) {
                val warnColor = if (!capFits) HealthCrimson else TorchAmber
                Surface(
                    color = warnColor.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trade_status_notice")
                ) {
                    Text(
                        text = session.statusNotice,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (!capFits) Color(0xFFFF8A8A) else TorchGold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = StoneBorderDark)

            // Dual Offer Panes (Your Offer | Partner's Offer)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TradeOfferPane(
                    title = "YOUR OFFER",
                    offer = session.localOffer,
                    accentColor = TorchGold,
                    isEditable = true,
                    removeQty = itemQty,
                    onRemoveItem = { itemId -> onRemoveItem(itemId, itemQty) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                TradeOfferPane(
                    title = session.partnerName.uppercase(),
                    offer = session.partnerOffer,
                    accentColor = ManaSapphire,
                    isEditable = false,
                    removeQty = 1,
                    onRemoveItem = null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            HorizontalDivider(color = StoneBorderDark)

            // Offer Builder Controls (Gold quick chips + Backpack tradeable strip)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianVoid.copy(alpha = 0.72f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = "Offer Gold",
                        tint = TorchGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Gold (${state.gold} gp):",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TorchGold
                    )
                    TradeGoldChip("+1", enabled = state.gold > session.localOffer.gold, tag = "trade_gold_add_1") {
                        onSetGold((session.localOffer.gold + 1).coerceAtMost(state.gold))
                    }
                    TradeGoldChip("+5", enabled = state.gold > session.localOffer.gold, tag = "trade_gold_add_5") {
                        onSetGold((session.localOffer.gold + 5).coerceAtMost(state.gold))
                    }
                    TradeGoldChip("+10", enabled = state.gold > session.localOffer.gold, tag = "trade_gold_add_10") {
                        onSetGold((session.localOffer.gold + 10).coerceAtMost(state.gold))
                    }
                    TradeGoldChip("All", enabled = state.gold > session.localOffer.gold, tag = "trade_gold_all") {
                        onSetGold(state.gold)
                    }
                    if (session.localOffer.gold > 0) {
                        TradeGoldChip("Clear", enabled = true, accent = HealthCrimson, tag = "trade_gold_clear") {
                            onSetGold(0)
                        }
                    }
                }

                // Item batch quantity toggle bar (x1, x5, x10)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Items:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ParchmentMuted
                    )
                    listOf(1, 5, 10).forEach { qty ->
                        val selected = itemQty == qty
                        Surface(
                            color = if (selected) TorchGold else StoneSurface,
                            shape = RoundedCornerShape(5.dp),
                            border = BorderStroke(1.dp, if (selected) TorchGold else StoneBorderLight.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .clickable { itemQty = qty }
                                .testTag("trade_qty_$qty")
                        ) {
                            Text(
                                text = "x$qty",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                                color = if (selected) ObsidianVoid else ParchmentLight,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Tap bag to add • offer to remove",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = ParchmentMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (tradeableStacks.isEmpty()) {
                    Text(
                        text = "No more tradeable items in backpack (tap items in Your Offer above to remove).",
                        style = MaterialTheme.typography.labelSmall,
                        color = ParchmentMuted
                    )
                } else {
                    val offeredById = remember(session.localOffer.items) {
                        session.localOffer.items.associate { it.itemId to it.count }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(tradeableStacks, key = { it.itemId }) { stack ->
                            val remaining = (stack.count - (offeredById[stack.itemId] ?: 0)).coerceAtLeast(0)
                            val addAmt = minOf(itemQty, remaining).coerceAtLeast(1)
                            val def = ItemCatalog.get(stack.itemId)
                            val bmp = remember(stack.itemId) { EntitySpriteAtlas.itemIcon(stack.itemId) }
                            Surface(
                                color = StoneSurface,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, StoneBorderLight.copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .clickable { onAddItem(stack.itemId, itemQty) }
                                    .testTag("trade_bag_item_${stack.itemId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    PixelSpritePreview(
                                        bitmap = bmp,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(ObsidianVoid, RoundedCornerShape(4.dp))
                                    )
                                    Column {
                                        Text(
                                            text = def.name,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                            color = ParchmentLight,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 96.dp)
                                        )
                                        Text(
                                            text = "+$addAmt (x$remaining)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = TorchGold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = StoneBorderDark)

            // Two-Step Lock & Confirm Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StoneElevated)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, HealthCrimson.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("trade_cancel_button")
                ) {
                    Text("Cancel", color = Color(0xFFFF8A8A), style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = onToggleLock,
                    enabled = capFits || session.localOffer.locked,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (session.localOffer.locked) StoneSurface else TorchGold,
                        contentColor = if (session.localOffer.locked) TorchGold else ObsidianVoid
                    ),
                    border = if (session.localOffer.locked) BorderStroke(1.2.dp, TorchGold) else null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("trade_lock_button")
                ) {
                    Icon(
                        imageVector = if (session.localOffer.locked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (session.localOffer.locked) "Unlock" else "Lock Offer",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }

                Button(
                    onClick = onConfirm,
                    enabled = bothLocked && capFits && !session.localOffer.confirmed,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpEmerald,
                        contentColor = ObsidianVoid,
                        disabledContainerColor = StoneSurface,
                        disabledContentColor = ParchmentMuted
                    ),
                    modifier = Modifier
                        .weight(1.15f)
                        .heightIn(min = 44.dp)
                        .testTag("trade_confirm_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (session.localOffer.confirmed) "Confirmed..." else "Confirm Trade",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun TradeOfferPane(
    title: String,
    offer: TradeOfferSnapshot,
    accentColor: Color,
    isEditable: Boolean,
    removeQty: Int = 1,
    onRemoveItem: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        offer.confirmed -> "CONFIRMED"
        offer.locked -> "LOCKED"
        else -> "EDITING"
    }
    val statusColor = when {
        offer.confirmed -> ExpEmerald
        offer.locked -> TorchGold
        else -> ParchmentMuted
    }
    Surface(
        color = StoneSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (offer.locked || offer.confirmed) 1.4.dp else 1.dp,
            color = if (offer.confirmed) ExpEmerald else if (offer.locked) TorchGold else StoneBorderDark
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp),
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Offered Gold Row
            Surface(
                color = ObsidianVoid,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, TorchGold.copy(alpha = if (offer.gold > 0) 0.65f else 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = null,
                        tint = TorchGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${offer.gold} gp",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (offer.gold > 0) TorchGold else ParchmentMuted
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (offer.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isEditable) "Tap backpack items below to offer" else "No items offered yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = ParchmentMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(offer.items, key = { it.itemId }) { stack ->
                        val def = ItemCatalog.get(stack.itemId)
                        val bmp = remember(stack.itemId) { EntitySpriteAtlas.itemIcon(stack.itemId) }
                        Surface(
                            color = ObsidianVoid,
                            shape = RoundedCornerShape(5.dp),
                            border = BorderStroke(1.dp, StoneBorderDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isEditable && onRemoveItem != null) {
                                        Modifier.clickable { onRemoveItem(stack.itemId) }
                                    } else Modifier
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PixelSpritePreview(
                                    bitmap = bmp,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (stack.count > 1) "${def.name} x${stack.count}" else def.name,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = ParchmentLight,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isEditable) {
                                        val remAmt = minOf(removeQty, stack.count).coerceAtLeast(1)
                                        Text(
                                            text = "Tap to remove $remAmt",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                            color = ParchmentMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeGoldChip(
    label: String,
    enabled: Boolean,
    accent: Color = TorchGold,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        color = if (enabled) accent.copy(alpha = 0.16f) else StoneSurface,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, if (enabled) accent.copy(alpha = 0.7f) else StoneBorderDark),
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = if (enabled) accent else ParchmentMuted.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}
