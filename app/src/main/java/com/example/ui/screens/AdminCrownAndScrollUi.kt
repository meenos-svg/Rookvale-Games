package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.cloud.AdminAuditAction
import com.example.game.cloud.AdminAuditUiState
import com.example.game.model.AdminEquipmentClassification
import com.example.game.model.DEFAULT_TIME_OF_DAY_HOURS
import com.example.game.model.GameWorldState
import com.example.game.world.WeatherType
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.StoneBorderDark
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.StoneElevated
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold
import kotlin.math.abs

private val CrownGoldMid = Color(0xFFF5C542)
private val CrownGoldHighlight = Color(0xFFFFF0A6)
private val CrownGoldDeep = Color(0xFFC88B14)
private val CrownBronzeRim = Color(0xFF7A4B08)
private val CrownRuby = Color(0xFFE53935)
private val CrownRubyGlint = Color(0xFFFF9E9E)
private val CrownEmerald = Color(0xFF2ED573)
private val CrownEmeraldGlint = Color(0xFFB5FFCF)
private val SpectatorViolet = Color(0xFFA78BFA)

/**
 * Crisp custom multi-layered Jeweled Golden Crown rendered in Jetpack Compose UI
 * (HUD header, Battle/Online allies list, Chat lines, Cached Quick-Resume card, and Admin Control FAB).
 */
@Composable
fun AdminGoldenCrownIcon(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 14.dp
) {
    val widthDp = sizeDp * 1.32f
    Canvas(
        modifier = modifier
            .size(width = widthDp, height = sizeDp)
            .semantics { contentDescription = "Admin Golden Crown" }
            .testTag("admin_golden_crown_icon")
    ) {
        drawIsometricAdminCrown(
            leftX = 0f,
            centerY = size.height * 0.5f,
            heightPx = size.height
        )
    }
}

/**
 * Shared vector-quality DrawScope renderer for the Jeweled Golden Crown.
 * Used both on the 360-degree isometric world canvas (floating beside Admin's nameplate)
 * and inside [AdminGoldenCrownIcon] for consistent visual identity everywhere.
 */
fun DrawScope.drawIsometricAdminCrown(
    leftX: Float,
    centerY: Float,
    heightPx: Float
) {
    val h = heightPx.coerceAtLeast(8f)
    val w = h * 1.32f
    val top = centerY - h * 0.5f
    val bottom = centerY + h * 0.5f
    val baseTop = top + h * 0.72f
    val baseBottom = bottom - h * 0.06f

    // Subtle dark drop shadow for crisp legibility over bright tiles/snow/grass
    val shadowPath = Path().apply {
        moveTo(leftX + w * 0.12f, baseTop + 1.2f)
        lineTo(leftX + w * 0.05f, top + h * 0.25f + 1.2f)
        lineTo(leftX + w * 0.30f, top + h * 0.52f + 1.2f)
        lineTo(leftX + w * 0.50f, top + h * 0.10f + 1.2f)
        lineTo(leftX + w * 0.70f, top + h * 0.52f + 1.2f)
        lineTo(leftX + w * 0.95f, top + h * 0.25f + 1.2f)
        lineTo(leftX + w * 0.88f, baseTop + 1.2f)
        close()
    }
    drawPath(shadowPath, Color.Black.copy(alpha = 0.65f))
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.65f),
        topLeft = Offset(leftX + w * 0.10f, baseTop + 1.0f),
        size = Size(w * 0.80f, (baseBottom - baseTop).coerceAtLeast(2f)),
        cornerRadius = CornerRadius(h * 0.10f, h * 0.10f)
    )

    // Crown body with warm golden bevel gradient
    val crownPath = Path().apply {
        moveTo(leftX + w * 0.12f, baseTop)
        lineTo(leftX + w * 0.05f, top + h * 0.24f)
        lineTo(leftX + w * 0.30f, top + h * 0.50f)
        lineTo(leftX + w * 0.50f, top + h * 0.09f)
        lineTo(leftX + w * 0.70f, top + h * 0.50f)
        lineTo(leftX + w * 0.95f, top + h * 0.24f)
        lineTo(leftX + w * 0.88f, baseTop)
        close()
    }
    val goldBrush = Brush.verticalGradient(
        colors = listOf(CrownGoldHighlight, CrownGoldMid, CrownGoldDeep),
        startY = top,
        endY = baseBottom
    )
    drawPath(crownPath, brush = goldBrush)

    // Warm inner highlight facet along left & center spires
    val highlightPath = Path().apply {
        moveTo(leftX + w * 0.18f, baseTop - h * 0.04f)
        lineTo(leftX + w * 0.12f, top + h * 0.34f)
        lineTo(leftX + w * 0.31f, top + h * 0.56f)
        lineTo(leftX + w * 0.50f, top + h * 0.20f)
        lineTo(leftX + w * 0.50f, baseTop - h * 0.04f)
        close()
    }
    drawPath(highlightPath, CrownGoldHighlight.copy(alpha = 0.42f))

    // Burnished bronze rim outline
    drawPath(
        path = crownPath,
        color = CrownBronzeRim,
        style = Stroke(width = (h * 0.085f).coerceAtLeast(0.9f), join = StrokeJoin.Round)
    )

    // Crown headband base with highlight stripe
    drawRoundRect(
        brush = goldBrush,
        topLeft = Offset(leftX + w * 0.10f, baseTop - h * 0.02f),
        size = Size(w * 0.80f, (baseBottom - baseTop + h * 0.02f).coerceAtLeast(2.2f)),
        cornerRadius = CornerRadius(h * 0.09f, h * 0.09f)
    )
    drawRoundRect(
        color = CrownBronzeRim,
        topLeft = Offset(leftX + w * 0.10f, baseTop - h * 0.02f),
        size = Size(w * 0.80f, (baseBottom - baseTop + h * 0.02f).coerceAtLeast(2.2f)),
        cornerRadius = CornerRadius(h * 0.09f, h * 0.09f),
        style = Stroke(width = (h * 0.08f).coerceAtLeast(0.85f))
    )

    // Golden spire tips (3 pearls atop the crown peaks)
    val tipRadius = (h * 0.085f).coerceAtLeast(1.0f)
    val tips = listOf(
        Offset(leftX + w * 0.06f, top + h * 0.23f),
        Offset(leftX + w * 0.50f, top + h * 0.09f),
        Offset(leftX + w * 0.94f, top + h * 0.23f)
    )
    tips.forEach { pt ->
        drawCircle(CrownBronzeRim, radius = tipRadius + 0.55f, center = pt)
        drawCircle(CrownGoldHighlight, radius = tipRadius, center = pt)
    }

    // Center Ruby Jewel + flanking Emerald Jewels along the band
    val bandY = (baseTop + baseBottom) * 0.5f
    val rubyR = (h * 0.105f).coerceAtLeast(1.3f)
    val emeraldR = (h * 0.085f).coerceAtLeast(1.05f)

    // Center Ruby
    val rubyCenter = Offset(leftX + w * 0.50f, bandY)
    drawCircle(CrownBronzeRim, radius = rubyR + 0.5f, center = rubyCenter)
    drawCircle(CrownRuby, radius = rubyR, center = rubyCenter)
    drawCircle(CrownRubyGlint, radius = rubyR * 0.38f, center = Offset(rubyCenter.x - rubyR * 0.28f, rubyCenter.y - rubyR * 0.28f))

    // Left & Right Emeralds
    val leftGem = Offset(leftX + w * 0.27f, bandY)
    val rightGem = Offset(leftX + w * 0.73f, bandY)
    listOf(leftGem, rightGem).forEach { gem ->
        drawCircle(CrownBronzeRim, radius = emeraldR + 0.45f, center = gem)
        drawCircle(CrownEmerald, radius = emeraldR, center = gem)
        drawCircle(CrownEmeraldGlint, radius = emeraldR * 0.38f, center = Offset(gem.x - emeraldR * 0.25f, gem.y - emeraldR * 0.25f))
    }
}

/**
 * Collapsible bottom-right 'Admin Control' HUD overlay.
 * Strictly rendered ONLY when [GameWorldState.isAdmin] is true.
 * Replaces the backpack Sovereign's Scroll with inline controls for:
 *  - Spectator Mode toggle (invisible to monsters & other online players)
 *  - Local 24-hour Time-of-Day lighting (4 presets + smooth slider)
 *  - Custom Level Setter (1..999 + quick presets)
 *  - Top Tier 5 Cinderite Equipment Armory (all combat classifications + Grant All)
 */
@Composable
fun AdminControlHudOverlay(
    state: GameWorldState,
    adminAuditState: AdminAuditUiState = AdminAuditUiState(),
    expanded: Boolean,
    onToggleExpanded: (Boolean) -> Unit,
    onToggleSpectator: (Boolean) -> Unit,
    onSetTimeOfDay: (Float, Boolean) -> Unit,
    onSetWeatherOverride: (WeatherType?) -> Unit = {},
    onSetLevel: (Int) -> Unit,
    onGrantEquipment: (AdminEquipmentClassification) -> Unit,
    onTeleport: (floor: Int, x: Int, y: Int, label: String?) -> Unit = { _, _, _, _ -> },
    onSelectAuditFilter: (AdminAuditAction?) -> Unit = {},
    onRefreshAuditLog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!state.isAdmin) return

    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Time, 1 = Level, 2 = Armory, 3 = Warp, 4 = Audit

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Surface(
                color = BasaltDark.copy(alpha = 0.95f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.4.dp,
                    if (state.isSpectator) SpectatorViolet else TorchGold.copy(alpha = 0.85f)
                ),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .width(316.dp)
                    .heightIn(max = 368.dp)
                    .pointerInput(Unit) { detectTapGestures { /* Swallow background map taps */ } }
                    .testTag("admin_control_drawer")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Drawer Header: Crown + Title + Level/Immortal + Close
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StoneElevated)
                            .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AdminGoldenCrownIcon(sizeDp = 15.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ADMIN CONTROL",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = TorchGold,
                                maxLines = 1
                            )
                            Text(
                                text = "Lv ${state.level} • ∞ HP • ${IsometricLightingEngine.formatTimeOfDay(state.timeOfDayHours)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                color = ParchmentMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onToggleExpanded(false) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("admin_control_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Collapse Admin Control",
                                tint = ParchmentLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = StoneBorderDark, thickness = 1.dp)

                    // Always-accessible Spectator Mode quick-toggle bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (state.isSpectator) Color(0xFF21183A) else StoneSurface.copy(alpha = 0.88f))
                            .clickable { onToggleSpectator(!state.isSpectator) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isSpectator) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Spectator Mode",
                            tint = if (state.isSpectator) SpectatorViolet else ParchmentMuted,
                            modifier = Modifier.size(17.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.isSpectator) "SPECTATOR • ACTIVE" else "SPECTATOR MODE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (state.isSpectator) SpectatorViolet else ParchmentLight,
                                maxLines = 1
                            )
                            Text(
                                text = if (state.isSpectator) "Hidden from monsters & players" else "Visible to world & players",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = ParchmentMuted,
                                maxLines = 1
                            )
                        }
                        Switch(
                            checked = state.isSpectator,
                            onCheckedChange = onToggleSpectator,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianVoid,
                                checkedTrackColor = SpectatorViolet,
                                uncheckedThumbColor = ParchmentMuted,
                                uncheckedTrackColor = BasaltDark
                            ),
                            modifier = Modifier.testTag("admin_spectator_switch")
                        )
                    }

                    HorizontalDivider(color = StoneBorderDark, thickness = 1.dp)

                    // 5 Compact Section Tabs: Time, Level, Armory, Warp, Audit
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AdminTabChip(
                            label = "Time",
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            testTag = "admin_tab_time",
                            modifier = Modifier.weight(1f)
                        )
                        AdminTabChip(
                            label = "Level",
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            testTag = "admin_tab_level",
                            modifier = Modifier.weight(1f)
                        )
                        AdminTabChip(
                            label = "Armory",
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            testTag = "admin_tab_armory",
                            modifier = Modifier.weight(1f)
                        )
                        AdminTabChip(
                            label = "Warp",
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            testTag = "admin_tab_warp",
                            modifier = Modifier.weight(1f)
                        )
                        AdminTabChip(
                            label = "Audit",
                            selected = selectedTab == 4,
                            onClick = {
                                selectedTab = 4
                                if (adminAuditState.lastSyncedAtMs == null && !adminAuditState.isLoading) {
                                    onRefreshAuditLog()
                                }
                            },
                            testTag = "admin_tab_audit",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Active Tab Body (scrollable if height constrained)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (selectedTab) {
                            0 -> AdminTimeOfDaySection(
                                state = state,
                                onSetTimeOfDay = onSetTimeOfDay,
                                onSetWeatherOverride = onSetWeatherOverride
                            )
                            1 -> AdminLevelSetterSection(
                                state = state,
                                onSetLevel = onSetLevel
                            )
                            2 -> AdminArmorySection(
                                onGrantEquipment = onGrantEquipment
                            )
                            3 -> AdminWarpSection(
                                state = state,
                                onTeleport = onTeleport
                            )
                            else -> AdminAuditLogSection(
                                auditState = adminAuditState,
                                onSelectFilter = onSelectAuditFilter,
                                onRefresh = onRefreshAuditLog
                            )
                        }
                    }
                }
            }
        }

        // Collapsible Golden Crown FAB in bottom-right safe area (min 48dp touch target)
        val fabBorder = when {
            state.isSpectator -> SpectatorViolet
            expanded -> TorchGold
            else -> TorchGold.copy(alpha = 0.85f)
        }
        val fabBg = when {
            state.isSpectator -> Color(0xFF21183A).copy(alpha = 0.94f)
            expanded -> StoneElevated.copy(alpha = 0.96f)
            else -> BasaltDark.copy(alpha = 0.92f)
        }
        Surface(
            color = fabBg,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, fabBorder),
            shadowElevation = 6.dp,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable { onToggleExpanded(!expanded) }
                .semantics { contentDescription = "Toggle Admin Control Overlay" }
                .testTag("admin_control_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AdminGoldenCrownIcon(sizeDp = 16.dp)
                Text(
                    text = if (state.isSpectator) "ADMIN • GHOST" else "ADMIN",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (state.isSpectator) SpectatorViolet else TorchGold,
                    maxLines = 1
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = null,
                    tint = if (state.isSpectator) SpectatorViolet else TorchGold,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AdminTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) TorchGold else StoneSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (selected) TorchGold else StoneBorderLight),
        modifier = modifier
            .heightIn(min = 34.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.5.sp
                ),
                color = if (selected) ObsidianVoid else ParchmentLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminTimeOfDaySection(
    state: GameWorldState,
    onSetTimeOfDay: (Float, Boolean) -> Unit,
    onSetWeatherOverride: (WeatherType?) -> Unit = {}
) {
    val formattedClock = IsometricLightingEngine.formatTimeOfDay(state.timeOfDayHours)
    val phaseLabel = IsometricLightingEngine.labelForTimeOfDay(state.timeOfDayHours)
    val isDefault = abs(state.timeOfDayHours - DEFAULT_TIME_OF_DAY_HOURS) < 0.05f

    Surface(
        color = StoneSurface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, StoneBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = TorchGold,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "$formattedClock • $phaseLabel",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = TorchGold,
                        modifier = Modifier.testTag("admin_time_readout")
                    )
                }
                if (!isDefault) {
                    Surface(
                        color = ObsidianVoid,
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, StoneBorderLight),
                        modifier = Modifier
                            .clickable { onSetTimeOfDay(DEFAULT_TIME_OF_DAY_HOURS, true) }
                            .testTag("admin_time_reset_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Time of Day",
                                tint = ParchmentMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                color = ParchmentLight
                            )
                        }
                    }
                }
            }

            // 4 Quick Presets: Dawn, Noon, Dusk, Midnight
            val presets = listOf(
                Triple("Dawn", 6.0f, "admin_time_preset_dawn"),
                Triple("Noon", 12.0f, "admin_time_preset_noon"),
                Triple("Dusk", 19.5f, "admin_time_preset_dusk"),
                Triple("Night", 0.0f, "admin_time_preset_midnight")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                presets.forEach { (label, targetHour, tag) ->
                    val active = abs(state.timeOfDayHours - targetHour) < 0.35f ||
                        (targetHour == 0.0f && state.timeOfDayHours >= 23.65f)
                    Surface(
                        color = if (active) TorchAmber else ObsidianVoid,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (active) TorchGold else StoneBorderLight),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .clickable { onSetTimeOfDay(targetHour, true) }
                            .testTag(tag)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (active) ObsidianVoid else ParchmentLight,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Smooth 24-Hour Lighting Slider (logs audit entry on release)
            Slider(
                value = state.timeOfDayHours.coerceIn(0f, 24f),
                onValueChange = { hours -> onSetTimeOfDay(hours, false) },
                onValueChangeFinished = { onSetTimeOfDay(state.timeOfDayHours, true) },
                valueRange = 0f..24f,
                colors = SliderDefaults.colors(
                    thumbColor = TorchGold,
                    activeTrackColor = TorchAmber,
                    inactiveTrackColor = ObsidianVoid
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_time_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("00:00", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = ParchmentMuted)
                Text("06:00", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = ParchmentMuted)
                Text("12:00", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = ParchmentMuted)
                Text("18:00", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = ParchmentMuted)
                Text("24:00", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = ParchmentMuted)
            }

            HorizontalDivider(color = StoneBorderDark, thickness = 1.dp)

            // Weather Override controls: Auto (Biome Clock) or forced local weather
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = TorchGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "WEATHER OVERRIDE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = TorchGold
                    )
                }
                Text(
                    text = state.adminWeatherOverride?.displayName ?: "Auto (Biome)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = ParchmentMuted,
                    modifier = Modifier.testTag("admin_weather_readout")
                )
            }

            val weatherChoices: List<Pair<String, WeatherType?>> = listOf(
                "Auto" to null,
                "Clear" to WeatherType.CLEAR,
                "Rain" to WeatherType.LIGHT_RAIN,
                "Leaves" to WeatherType.FALLING_LEAVES,
                "Snow" to WeatherType.SNOW,
                "Ash" to WeatherType.VOLCANIC_ASH,
                "Mist" to WeatherType.SWAMP_MIST
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_weather_chips")
            ) {
                weatherChoices.forEach { (label, choice) ->
                    val selected = state.adminWeatherOverride == choice
                    val chipKey = choice?.name?.lowercase() ?: "auto"
                    Surface(
                        color = if (selected) TorchAmber else ObsidianVoid,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (selected) TorchGold else StoneBorderLight),
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .clickable { onSetWeatherOverride(choice) }
                            .testTag("admin_weather_chip_$chipKey")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (selected) ObsidianVoid else ParchmentLight,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (state.floor < 0) {
                Text(
                    text = "Underground floor: full daylight/night & weather changes apply on the overland surface.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = ParchmentMuted
                )
            }
        }
    }
}

@Composable
private fun AdminLevelSetterSection(
    state: GameWorldState,
    onSetLevel: (Int) -> Unit
) {
    var levelInput by remember(state.level) { mutableStateOf(state.level.toString()) }
    var levelError by remember { mutableStateOf<String?>(null) }

    Surface(
        color = StoneSurface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, StoneBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "SET CHARACTER LEVEL (1 – 999)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = TorchGold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = levelInput,
                    onValueChange = { raw ->
                        levelInput = raw.filter { it.isDigit() }.take(3)
                        levelError = null
                    },
                    singleLine = true,
                    label = { Text("Level", style = MaterialTheme.typography.labelSmall) },
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
                        .testTag("admin_level_input")
                )
                Button(
                    onClick = {
                        val parsed = levelInput.toIntOrNull()
                        if (parsed == null || parsed !in 1..999) {
                            levelError = "Enter 1–999."
                        } else {
                            levelError = null
                            onSetLevel(parsed)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TorchGold,
                        contentColor = ObsidianVoid
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("admin_apply_level_button")
                ) {
                    Text(
                        text = "APPLY",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
            if (levelError != null) {
                Text(
                    text = levelError.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = HealthCrimson
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(1, 28, 40, 100, 250).forEach { preset ->
                    Surface(
                        color = if (state.level == preset) TorchAmber else ObsidianVoid,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (state.level == preset) TorchGold else StoneBorderLight),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 36.dp)
                            .clickable {
                                levelInput = preset.toString()
                                levelError = null
                                onSetLevel(preset)
                            }
                            .testTag("admin_level_preset_$preset")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = "L$preset",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (state.level == preset) ObsidianVoid else ParchmentLight,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminArmorySection(
    onGrantEquipment: (AdminEquipmentClassification) -> Unit
) {
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
            Text(
                text = "TIER 5 CINDERITE ARMORY",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = TorchGold
            )
            val singleClasses = listOf(
                AdminEquipmentClassification.SWORD to "Sword",
                AdminEquipmentClassification.AXE to "Axe",
                AdminEquipmentClassification.CLUB to "Club",
                AdminEquipmentClassification.DISTANCE to "Distance",
                AdminEquipmentClassification.MAGIC to "Magic"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                singleClasses.take(3).forEach { (cls, label) ->
                    OutlinedButton(
                        onClick = { onGrantEquipment(cls) },
                        border = BorderStroke(1.dp, TorchAmber.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(7.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("admin_grant_${cls.name.lowercase()}")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TorchGold,
                            maxLines = 1
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                singleClasses.drop(3).forEach { (cls, label) ->
                    OutlinedButton(
                        onClick = { onGrantEquipment(cls) },
                        border = BorderStroke(1.dp, TorchAmber.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(7.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("admin_grant_${cls.name.lowercase()}")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TorchGold,
                            maxLines = 1
                        )
                    }
                }
            }
            Button(
                onClick = { onGrantEquipment(AdminEquipmentClassification.ALL) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = TorchGold,
                    contentColor = ObsidianVoid
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_grant_all")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "GRANT ALL CLASSES",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Legacy panel entrypoint kept compatible with [com.example.game.model.ActivePanel.ADMIN_SCROLL];
 * delegates to the same inline Level, Armory, and Spectator controls.
 */
@Composable
fun AdminScrollPanelContent(
    state: GameWorldState,
    onSetLevel: (Int) -> Unit,
    onGrantEquipment: (AdminEquipmentClassification) -> Unit,
    onToggleSpectator: (Boolean) -> Unit
) {
    if (!state.isAdmin) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Admin Control is available only to Admin.",
                style = MaterialTheme.typography.bodyMedium,
                color = ParchmentMuted
            )
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AdminLevelSetterSection(state = state, onSetLevel = onSetLevel)
        AdminArmorySection(onGrantEquipment = onGrantEquipment)
    }
}
