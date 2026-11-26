package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.game.multiplayer.ChatChannel
import com.example.game.multiplayer.ChatMessage
import com.example.game.multiplayer.ConnectionStatus
import com.example.game.multiplayer.MultiplayerUiState
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.StoneElevated
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold

@Composable
fun MultiplayerStatusBadge(
    mpState: MultiplayerUiState,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dotColor = when (mpState.status) {
        ConnectionStatus.ONLINE -> ExpEmerald
        ConnectionStatus.CONNECTING -> TorchAmber
        ConnectionStatus.OFFLINE_SOLO -> ParchmentMuted
    }
    val onlineCount = maxOf(mpState.realmOnlineCount, mpState.floorOnlineCount)
    val label = when (mpState.status) {
        ConnectionStatus.ONLINE -> {
            val hostTag = if (mpState.isFloorHost && mpState.floorOnlineCount > 1) " • Host" else ""
            "$onlineCount online$hostTag"
        }
        ConnectionStatus.CONNECTING -> "Syncing..."
        ConnectionStatus.OFFLINE_SOLO -> "Offline Solo"
    }

    Surface(
        color = dotColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.65f)),
        modifier = modifier
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .testTag("multiplayer_status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (mpState.status == ConnectionStatus.OFFLINE_SOLO) ParchmentMuted else dotColor,
                maxLines = 1
            )
            val ping = mpState.pingMs
            if (mpState.status == ConnectionStatus.ONLINE && ping != null) {
                val pingColor = when {
                    ping <= 90 -> ExpEmerald
                    ping <= 180 -> TorchGold
                    else -> HealthCrimson
                }
                Text(
                    text = "• ${ping}ms",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = pingColor,
                    maxLines = 1,
                    modifier = Modifier.testTag("multiplayer_badge_ping")
                )
            }
        }
    }
}

@Composable
fun CollapsibleFloorChatBar(
    mpState: MultiplayerUiState,
    currentFloor: Int,
    onToggleExpanded: () -> Unit,
    onSelectChannel: (ChatChannel) -> Unit,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val visibleMessages = mpState.chatMessages.filter { msg ->
        msg.channel == ChatChannel.SYSTEM ||
            msg.channel == ChatChannel.WORLD ||
            (msg.channel == ChatChannel.FLOOR && msg.floor == currentFloor)
    }

    LaunchedEffect(visibleMessages.size, mpState.chatExpanded) {
        if (mpState.chatExpanded && visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    Surface(
        color = BasaltDark.copy(alpha = if (mpState.chatExpanded) 0.94f else 0.78f),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        border = BorderStroke(1.dp, StoneBorderLight.copy(alpha = 0.85f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Compact collapsible header strip (always >= 40dp; tap anywhere to expand/collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp)
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("chat_toggle_bar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Toggle Floor and World Chat",
                    tint = TorchGold,
                    modifier = Modifier.size(16.dp)
                )
                val latest = visibleMessages.lastOrNull()
                if (mpState.chatExpanded) {
                    Text(
                        text = "FLOOR & WORLD CHAT",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TorchGold,
                        modifier = Modifier.weight(1f)
                    )
                } else if (latest != null) {
                    ChatLineText(
                        msg = latest,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = if (mpState.status == ConnectionStatus.ONLINE) {
                            "Floor Chat (Tap to message nearby heroes)"
                        } else {
                            "Chat & Callouts (Offline Solo)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ParchmentMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!mpState.chatExpanded && mpState.unreadChatCount > 0) {
                    Surface(
                        color = TorchGold,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${mpState.unreadChatCount}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ObsidianVoid,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }

                Icon(
                    imageVector = if (mpState.chatExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = if (mpState.chatExpanded) "Collapse Chat" else "Expand Chat",
                    tint = ParchmentLight,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (mpState.chatExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChatChannelChip(
                            label = "Floor ($currentFloor)",
                            selected = mpState.activeChatChannel == ChatChannel.FLOOR,
                            onClick = { onSelectChannel(ChatChannel.FLOOR) },
                            testTag = "chat_channel_floor"
                        )
                        ChatChannelChip(
                            label = "World",
                            selected = mpState.activeChatChannel == ChatChannel.WORLD,
                            onClick = { onSelectChannel(ChatChannel.WORLD) },
                            testTag = "chat_channel_world"
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (mpState.status == ConnectionStatus.ONLINE) "Live Co-Op" else "Offline Solo",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mpState.status == ConnectionStatus.ONLINE) ExpEmerald else ParchmentMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp)
                            .background(ObsidianVoid.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                    ) {
                        if (visibleMessages.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No messages yet. Level-ups, boss kills, and hero chat appear here.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ParchmentMuted
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(visibleMessages, key = { it.id }) { msg ->
                                    ChatLineText(msg = msg, maxLines = 2)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { if (it.length <= 140) draft = it },
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = if (mpState.status == ConnectionStatus.ONLINE) {
                                        "Message ${mpState.activeChatChannel.label}..."
                                    } else {
                                        "Local note (Offline Solo)..."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ParchmentMuted
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = ParchmentLight),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TorchGold,
                                unfocusedBorderColor = StoneBorderLight,
                                focusedContainerColor = StoneSurface,
                                unfocusedContainerColor = StoneSurface
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (draft.isNotBlank()) {
                                        onSendMessage(draft)
                                        draft = ""
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("chat_input_field")
                        )
                        Surface(
                            color = TorchGold,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (draft.isNotBlank()) {
                                        onSendMessage(draft)
                                        draft = ""
                                    }
                                },
                                modifier = Modifier.testTag("chat_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Chat Message",
                                    tint = ObsidianVoid,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatChannelChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (selected) TorchGold else StoneElevated,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, if (selected) TorchGold else StoneBorderLight),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (selected) ObsidianVoid else ParchmentLight,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ChatLineText(
    msg: ChatMessage,
    maxLines: Int,
    modifier: Modifier = Modifier
) {
    val tagColor = when (msg.channel) {
        ChatChannel.SYSTEM -> TorchGold
        ChatChannel.WORLD -> Color(0xFF67E8F9)
        ChatChannel.FLOOR -> ExpEmerald
    }
    if (msg.senderIsAdmin && msg.channel != ChatChannel.SYSTEM) {
        val channelTag = if (msg.channel == ChatChannel.WORLD) "[W] " else "[F] "
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier
        ) {
            Text(
                text = channelTag,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = tagColor,
                maxLines = 1
            )
            AdminGoldenCrownIcon(sizeDp = 12.dp)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TorchGold, fontWeight = FontWeight.ExtraBold)) {
                        append("${msg.senderName}: ")
                    }
                    withStyle(SpanStyle(color = ParchmentLight)) {
                        append(msg.text)
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        val prefix = when (msg.channel) {
            ChatChannel.SYSTEM -> "[!] "
            ChatChannel.WORLD -> "[W] ${msg.senderName}: "
            ChatChannel.FLOOR -> "[F] ${msg.senderName}: "
        }
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Bold)) {
                    append(prefix)
                }
                withStyle(SpanStyle(color = if (msg.channel == ChatChannel.SYSTEM) TorchGold else ParchmentLight)) {
                    append(msg.text)
                }
            },
            style = MaterialTheme.typography.labelSmall,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}
