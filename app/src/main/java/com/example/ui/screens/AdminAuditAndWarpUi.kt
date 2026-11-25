package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.game.cloud.AdminAuditAction
import com.example.game.cloud.AdminAuditEntry
import com.example.game.cloud.AdminAuditSyncStatus
import com.example.game.cloud.AdminAuditUiState
import com.example.game.cloud.AdminTeleportCatalog
import com.example.game.cloud.AdminTeleportPreset
import com.example.game.cloud.AdminWarpCategory
import com.example.game.model.GameWorldState
import com.example.game.world.OverlandBiomeTier
import com.example.game.world.WorldMaps
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.FireOrange
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.ManaSapphire
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.PoisonGreen
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AuditViolet = Color(0xFFA78BFA)
private val AuditCyan = Color(0xFF38BDF8)

private enum class AdminWarpFilter(
    val label: String,
    val testTagSuffix: String,
    val category: AdminWarpCategory?
) {
    ALL("All", "all", null),
    TOWNS_EMPIRES("Towns & Empires", "towns_empires", AdminWarpCategory.TOWNS_EMPIRES),
    OVERLAND_CAMPS("Overland Camps", "overland_camps", AdminWarpCategory.OVERLAND_CAMPS),
    DUNGEON_CAMPS("Dungeon Camps", "dungeon_camps", AdminWarpCategory.DUNGEON_CAMPS)
}

private fun biomeTierAccentColor(preset: AdminTeleportPreset): Color {
    if (preset.isDungeon) return AuditViolet
    return when (preset.biomeTier) {
        OverlandBiomeTier.ROOKVALE_HEARTLAND -> ExpEmerald
        OverlandBiomeTier.WHISPERING_MARSH -> PoisonGreen
        OverlandBiomeTier.SUNSCORCH_BADLANDS -> TorchAmber
        OverlandBiomeTier.FROSTPEAK_HIGHLANDS -> AuditCyan
        OverlandBiomeTier.ASHEN_CALDERA -> FireOrange
    }
}

/**
 * Admin Control Warp Tab: one-tap Overland & Dungeon landmark teleports plus
 * custom (Floor, X, Y) coordinate teleportation with bounds validation.
 */
@Composable
fun AdminWarpSection(
    state: GameWorldState,
    onTeleport: (floor: Int, x: Int, y: Int, label: String?) -> Unit
) {
    var selectedFloor by remember(state.floor) { mutableIntStateOf(state.floor.coerceIn(-10, 0)) }
    var xInput by remember(state.playerX, state.floor) { mutableStateOf(state.playerX.toString()) }
    var yInput by remember(state.playerY, state.floor) { mutableStateOf(state.playerY.toString()) }
    var coordError by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(AdminWarpFilter.ALL) }

    val selectedFloorMap = remember(selectedFloor) { WorldMaps.getFloor(selectedFloor) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Category Filter Chips Bar (All / Towns & Empires / Overland Camps / Dungeon Camps)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AdminWarpFilter.entries.forEach { filter ->
                val count = AdminTeleportCatalog.presetsFor(filter.category).size
                val accent = when (filter) {
                    AdminWarpFilter.ALL -> TorchGold
                    AdminWarpFilter.TOWNS_EMPIRES -> TorchGold
                    AdminWarpFilter.OVERLAND_CAMPS -> ExpEmerald
                    AdminWarpFilter.DUNGEON_CAMPS -> AuditViolet
                }
                AdminWarpFilterChip(
                    label = "${filter.label} ($count)",
                    selected = selectedFilter == filter,
                    accentColor = accent,
                    onClick = { selectedFilter = filter },
                    testTag = "admin_warp_filter_${filter.testTagSuffix}"
                )
            }
        }

        // 1. Towns & Empires Card
        if (selectedFilter == AdminWarpFilter.ALL || selectedFilter == AdminWarpFilter.TOWNS_EMPIRES) {
            AdminWarpCategoryCard(
                title = "TOWNS & EMPIRES (${AdminTeleportCatalog.townsAndEmpires.size})",
                icon = Icons.Default.AccountBalance,
                headerColor = TorchGold,
                presets = AdminTeleportCatalog.townsAndEmpires,
                onSelect = { preset ->
                    coordError = null
                    onTeleport(preset.floor, preset.x, preset.y, preset.label)
                }
            )
        }

        // 2. Overland Forward & Frontier Camps Card
        if (selectedFilter == AdminWarpFilter.ALL || selectedFilter == AdminWarpFilter.OVERLAND_CAMPS) {
            AdminWarpCategoryCard(
                title = "OVERLAND CAMPS (${AdminTeleportCatalog.overlandCamps.size})",
                icon = Icons.Default.Flag,
                headerColor = ExpEmerald,
                presets = AdminTeleportCatalog.overlandCamps,
                onSelect = { preset ->
                    coordError = null
                    onTeleport(preset.floor, preset.x, preset.y, preset.label)
                }
            )
        }

        // 3. Subterranean Dungeon Forward Camps Card
        if (selectedFilter == AdminWarpFilter.ALL || selectedFilter == AdminWarpFilter.DUNGEON_CAMPS) {
            AdminWarpCategoryCard(
                title = "DUNGEON CAMPS (${AdminTeleportCatalog.dungeonCamps.size})",
                icon = Icons.Default.Place,
                headerColor = AuditViolet,
                presets = AdminTeleportCatalog.dungeonCamps,
                onSelect = { preset ->
                    coordError = null
                    onTeleport(preset.floor, preset.x, preset.y, preset.label)
                }
            )
        }

        // 3. Custom Coordinate Warp Card
        Surface(
            color = StoneSurface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, StoneBorderLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CUSTOM COORDINATES",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = TorchGold
                    )
                    Text(
                        text = "Now: F${state.floor} (${state.playerX},${state.playerY})",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = ParchmentMuted
                    )
                }

                // Floor Selector Chips (0 .. -10)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(0, -1, -2, -3, -4, -5, -6, -7, -8, -9, -10).forEach { fl ->
                        val active = selectedFloor == fl
                        Surface(
                            color = if (active) TorchGold else ObsidianVoid,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (active) TorchGold else StoneBorderLight),
                            modifier = Modifier
                                .heightIn(min = 34.dp)
                                .clickable {
                                    selectedFloor = fl
                                    val (fx, fy) = WorldMaps.fallbackLandingForFloor(fl)
                                    xInput = fx.toString()
                                    yInput = fy.toString()
                                    coordError = null
                                }
                                .testTag("admin_warp_floor_$fl")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (fl == 0) "F0" else "F$fl",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (active) ObsidianVoid else ParchmentLight
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Bounds: X 0..${selectedFloorMap.width - 1}, Y 0..${selectedFloorMap.height - 1}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = ParchmentMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = xInput,
                        onValueChange = { raw ->
                            xInput = raw.filter { it.isDigit() }.take(4)
                            coordError = null
                        },
                        singleLine = true,
                        label = { Text("X", style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TorchGold,
                            unfocusedBorderColor = StoneBorderLight,
                            focusedTextColor = ParchmentLight,
                            unfocusedTextColor = ParchmentLight,
                            cursorColor = TorchGold,
                            focusedLabelColor = TorchGold,
                            unfocusedLabelColor = ParchmentMuted
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_warp_x_input")
                    )
                    OutlinedTextField(
                        value = yInput,
                        onValueChange = { raw ->
                            yInput = raw.filter { it.isDigit() }.take(4)
                            coordError = null
                        },
                        singleLine = true,
                        label = { Text("Y", style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TorchGold,
                            unfocusedBorderColor = StoneBorderLight,
                            focusedTextColor = ParchmentLight,
                            unfocusedTextColor = ParchmentLight,
                            cursorColor = TorchGold,
                            focusedLabelColor = TorchGold,
                            unfocusedLabelColor = ParchmentMuted
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_warp_y_input")
                    )
                }

                if (coordError != null) {
                    Text(
                        text = coordError.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = HealthCrimson
                    )
                }

                Button(
                    onClick = {
                        val px = xInput.toIntOrNull()
                        val py = yInput.toIntOrNull()
                        if (px == null || py == null) {
                            coordError = "Enter valid X and Y coordinates."
                        } else if (px !in 0 until selectedFloorMap.width || py !in 0 until selectedFloorMap.height) {
                            coordError = "Keep X within 0..${selectedFloorMap.width - 1} and Y within 0..${selectedFloorMap.height - 1}."
                        } else {
                            coordError = null
                            onTeleport(selectedFloor, px, py, null)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TorchGold,
                        contentColor = ObsidianVoid
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_warp_execute_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "TELEPORT",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminWarpFilterChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Surface(
            color = if (selected) accentColor else ObsidianVoid,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, if (selected) accentColor else StoneBorderLight)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp
                ),
                color = if (selected) ObsidianVoid else ParchmentLight,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun AdminWarpCategoryCard(
    title: String,
    icon: ImageVector,
    headerColor: Color,
    presets: List<AdminTeleportPreset>,
    onSelect: (AdminTeleportPreset) -> Unit
) {
    Surface(
        color = StoneSurface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, StoneBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = headerColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = headerColor
                )
            }
            AdminPresetGrid(
                presets = presets,
                onSelect = onSelect
            )
        }
    }
}

@Composable
private fun AdminPresetGrid(
    presets: List<AdminTeleportPreset>,
    onSelect: (AdminTeleportPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.chunked(2).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowPresets.forEach { preset ->
                    val tierAccent = biomeTierAccentColor(preset)
                    Surface(
                        color = ObsidianVoid.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, tierAccent.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable { onSelect(preset) }
                            .testTag("admin_warp_preset_${preset.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
                        ) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = ParchmentLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    color = tierAccent.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.7.dp, tierAccent.copy(alpha = 0.72f))
                                ) {
                                    Text(
                                        text = preset.levelRangeLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 8.sp
                                        ),
                                        color = tierAccent,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = "F${preset.floor} • ${preset.x},${preset.y}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = ParchmentMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (rowPresets.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Append-only Server-Side Admin Audit Log panel inside the Admin Control drawer.
 * Displays chronological history with action-type filters, sync badges, and Refresh.
 * Strictly read-only (no edit or delete controls).
 */
@Composable
fun AdminAuditLogSection(
    auditState: AdminAuditUiState,
    onSelectFilter: (AdminAuditAction?) -> Unit,
    onRefresh: () -> Unit
) {
    val filtered = auditState.filteredEntries

    Surface(
        color = StoneSurface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, StoneBorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_audit_log_panel")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Header row: Title + Queue Count + 48dp Refresh Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TorchGold,
                        modifier = Modifier.size(15.dp)
                    )
                    Column {
                        Text(
                            text = "AUDIT LOG (${filtered.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = TorchGold,
                            maxLines = 1
                        )
                        val subtitle = when {
                            auditState.isLoading || auditState.isSyncing -> "Syncing with server..."
                            auditState.pendingQueueCount > 0 -> "${auditState.pendingQueueCount} pending offline sync"
                            !auditState.statusMessage.isNullOrBlank() -> auditState.statusMessage
                            else -> "Append-only server history"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                            color = if (auditState.pendingQueueCount > 0) TorchAmber else ParchmentMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !auditState.isLoading,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("admin_audit_refresh_button")
                ) {
                    if (auditState.isLoading) {
                        CircularProgressIndicator(
                            color = TorchGold,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Admin Audit Log",
                            tint = TorchGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Action-Type Filter Chips (All + 5 action categories)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AdminAuditFilterChip(
                    label = "All",
                    selected = auditState.selectedFilter == null,
                    accentColor = TorchGold,
                    onClick = { onSelectFilter(null) },
                    testTag = "admin_audit_filter_all"
                )
                AdminAuditAction.entries.forEach { action ->
                    AdminAuditFilterChip(
                        label = action.label,
                        selected = auditState.selectedFilter == action,
                        accentColor = auditActionColor(action),
                        onClick = { onSelectFilter(action) },
                        testTag = "admin_audit_filter_${action.wireName.lowercase(Locale.US)}"
                    )
                }
            }

            // Append-only chronological feed
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianVoid.copy(alpha = 0.72f))
                        .padding(vertical = 16.dp, horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (auditState.selectedFilter == null) {
                            "No administrative events recorded yet."
                        } else {
                            "No ${auditState.selectedFilter.label} events recorded."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ParchmentMuted
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    filtered.take(50).forEach { entry ->
                        AdminAuditEntryCard(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminAuditFilterChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (selected) accentColor else ObsidianVoid,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) accentColor else StoneBorderLight),
        modifier = Modifier
            .heightIn(min = 30.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp
                ),
                color = if (selected) ObsidianVoid else ParchmentLight,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AdminAuditEntryCard(entry: AdminAuditEntry) {
    val badgeColor = auditActionColor(entry.actionType)
    val isSynced = entry.syncStatus == AdminAuditSyncStatus.SYNCED
    val timeText = remember(entry.createdAtMs) { formatAuditTimestamp(entry.createdAtMs) }

    Surface(
        color = ObsidianVoid.copy(alpha = 0.84f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, StoneBorderLight.copy(alpha = 0.75f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_audit_entry_${entry.eventId}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.8.dp, badgeColor.copy(alpha = 0.8f))
                    ) {
                        Text(
                            text = entry.actionType.label.uppercase(Locale.US),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 8.5.sp
                            ),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = if (entry.actorName == entry.targetName) entry.actorName else "${entry.actorName} → ${entry.targetName}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp
                        ),
                        color = ParchmentLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                        color = ParchmentMuted
                    )
                    Icon(
                        imageVector = if (isSynced) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                        contentDescription = if (isSynced) "Synced to server" else "Queued offline",
                        tint = if (isSynced) ExpEmerald else TorchAmber,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                color = ParchmentLight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Optional concise metadata line (e.g., Floor / Coordinates)
            val metaParts = buildList {
                entry.details["from"]?.let { fromVal ->
                    entry.details["to"]?.let { toVal -> add("$fromVal → $toVal") }
                }
                entry.details["weapon"]?.let { add("Weapon: $it") }
                entry.details["clock"]?.let { add("Clock: $it") }
            }
            if (metaParts.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.7f))
                    )
                    Text(
                        text = metaParts.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                        color = ParchmentMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun auditActionColor(action: AdminAuditAction): Color = when (action) {
    AdminAuditAction.LEVEL_CHANGE -> TorchGold
    AdminAuditAction.GRANT_GEAR -> ExpEmerald
    AdminAuditAction.TELEPORT -> AuditCyan
    AdminAuditAction.SPECTATOR_TOGGLE -> AuditViolet
    AdminAuditAction.TIME_OF_DAY -> ManaSapphire
}

private fun formatAuditTimestamp(epochMs: Long): String {
    if (epochMs <= 0L) return "--:--"
    return runCatching {
        SimpleDateFormat("MMM d HH:mm", Locale.US).format(Date(epochMs))
    }.getOrDefault("--:--")
}
