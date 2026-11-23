package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.game.model.DisplaySettings
import com.example.game.model.GraphicsQuality
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.ExpEmerald
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
 * Reusable Settings Panel content shared by both the in-game Settings overlay
 * (`ActivePanel.SETTINGS`) and the Title Screen Settings modal dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPanelContent(
    displaySettings: DisplaySettings,
    onSelectGraphicsQuality: (GraphicsQuality) -> Unit,
    onToggleMinimap: (Boolean) -> Unit,
    onToggleFpsCounter: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeQuality = displaySettings.graphicsQuality
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(12.dp)
            .testTag("settings_panel_content"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header: Graphics Quality
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = TorchGold,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.settings_graphics_quality).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = TorchGold
                )
            }
            Text(
                text = "Applies instantly",
                style = MaterialTheme.typography.labelSmall,
                color = ExpEmerald
            )
        }

        // 4-Tier Segmented Pill Row: Low | Medium | High | HD
        Surface(
            color = ObsidianVoid.copy(alpha = 0.72f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, StoneBorderDark),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("graphics_quality_selector")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GraphicsQuality.entries.forEach { tier ->
                    val isSelected = tier == activeQuality
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) TorchGold else StoneSurface)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) TorchGold else StoneBorderLight.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectGraphicsQuality(tier) }
                            .testTag("graphics_quality_${tier.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tier.displayName,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                            ),
                            color = if (isSelected) ObsidianVoid else ParchmentLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Active Tier Priority Note + Visual Capability Chips
        Surface(
            color = StoneSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, TorchGold.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("graphics_quality_note")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${activeQuality.displayName}: ${activeQuality.summaryNote}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = ParchmentLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    activeQuality.capabilityTags.forEach { tag ->
                        Surface(
                            color = StoneElevated,
                            shape = RoundedCornerShape(5.dp),
                            border = BorderStroke(0.8.dp, StoneBorderLight.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = TorchAmber,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = StoneBorderDark, thickness = 1.dp)

        // Display Toggles: Show Isometric Minimap & Live FPS Counter
        SettingsToggleRow(
            icon = Icons.Default.Explore,
            title = "Show Isometric Minimap",
            subtitle = "Toggle the top-left 360° compass minimap",
            checked = displaySettings.showMinimap,
            onCheckedChange = onToggleMinimap,
            testTag = "settings_toggle_minimap"
        )

        SettingsToggleRow(
            icon = Icons.Default.Speed,
            title = "Live FPS Counter",
            subtitle = "Show real-time FPS & quality tier badge in HUD",
            checked = displaySettings.showFpsCounter,
            onCheckedChange = onToggleFpsCounter,
            testTag = "settings_toggle_fps"
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        color = StoneSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, StoneBorderDark),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (checked) TorchGold else ParchmentMuted,
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = ParchmentLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = ParchmentMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianVoid,
                    checkedTrackColor = TorchGold,
                    uncheckedThumbColor = ParchmentMuted,
                    uncheckedTrackColor = ObsidianVoid,
                    uncheckedBorderColor = StoneBorderLight
                )
            )
        }
    }
}

/**
 * Compact modal Settings dialog opened from the Title & Character Creation screen.
 */
@Composable
fun TitleSettingsDialog(
    displaySettings: DisplaySettings,
    onSelectGraphicsQuality: (GraphicsQuality) -> Unit,
    onToggleMinimap: (Boolean) -> Unit,
    onToggleFpsCounter: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = BasaltDark.copy(alpha = 0.98f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, TorchGold.copy(alpha = 0.85f)),
            modifier = Modifier
                .widthIn(min = 340.dp, max = 500.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .testTag("title_settings_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StoneElevated)
                        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = TorchGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.panel_settings).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = TorchGold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("title_settings_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = ParchmentLight
                        )
                    }
                }
                HorizontalDivider(color = StoneBorderDark, thickness = 1.dp)
                SettingsPanelContent(
                    displaySettings = displaySettings,
                    onSelectGraphicsQuality = onSelectGraphicsQuality,
                    onToggleMinimap = onToggleMinimap,
                    onToggleFpsCounter = onToggleFpsCounter
                )
            }
        }
    }
}

/**
 * Unobtrusive dark-framed HUD pill displaying live smoothed FPS + active Graphics Quality badge.
 */
@Composable
fun LiveFpsHudBadge(
    fps: Int,
    quality: GraphicsQuality,
    modifier: Modifier = Modifier
) {
    val fpsColor = when {
        fps >= 50 -> ExpEmerald
        fps >= 30 -> TorchGold
        else -> Color(0xFFFF5A5F)
    }
    Surface(
        color = BasaltDark.copy(alpha = 0.84f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, StoneBorderLight.copy(alpha = 0.8f)),
        modifier = modifier.testTag("live_fps_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "$fps FPS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = fpsColor
            )
            Text(
                text = "•",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = ParchmentMuted
            )
            Text(
                text = quality.shortBadge,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TorchGold
            )
        }
    }
}
