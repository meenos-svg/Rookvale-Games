package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.Direction
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCatalog
import com.example.game.model.MonsterEntity
import com.example.game.model.ThreatBand
import com.example.game.model.TileType
import com.example.game.multiplayer.ChatChannel
import com.example.game.multiplayer.ChatMessage
import com.example.game.multiplayer.ConnectionStatus
import com.example.game.multiplayer.MultiplayerUiState
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.sprites.EntitySpriteAtlas
import com.example.game.world.WorldFloor
import com.example.game.world.WorldMaps
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.FireOrange
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.HealthCrimsonDark
import com.example.ui.theme.ManaSapphire
import com.example.ui.theme.ManaSapphireDark
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.PoisonGreen
import com.example.ui.theme.SoulViolet
import com.example.ui.theme.StoneBorderDark
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.StoneElevated
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.TargetRed
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure minimap tap-to-tile helper matching the 45-degree isometric diamond orientation.
 */
object MinimapCoordinateMapper {
    fun screenTapToWorldTile(
        tapX: Float,
        tapY: Float,
        canvasW: Float,
        canvasH: Float,
        playerX: Int,
        playerY: Int,
        radius: Int,
        floorWidth: Int,
        floorHeight: Int
    ): Pair<Int, Int> {
        if (canvasW <= 1f || canvasH <= 1f) return playerX to playerY
        val cx = canvasW * 0.5f
        val cy = canvasH * 0.5f
        val scale = (minOf(canvasW, canvasH) * 0.46f) / radius.coerceAtLeast(1).toFloat()
        val u = (tapX - cx) / scale
        val v = (tapY - cy) / scale
        // Inverse of iso minimap: screenX = cx + (dx - dy) * 0.707 * scale, screenY = cy + (dx + dy) * 0.707 * scale
        val invFactor = 0.70710677f
        val dx = ((u + v) * invFactor).roundToInt()
        val dy = ((v - u) * invFactor).roundToInt()
        val wx = (playerX + dx).coerceIn(0, (floorWidth - 1).coerceAtLeast(0))
        val wy = (playerY + dy).coerceIn(0, (floorHeight - 1).coerceAtLeast(0))
        return wx to wy
    }
}

private fun minimapTileColor(tile: TileType): Color = when (tile) {
    TileType.GRASS -> Color(0xFF3E6E33)
    TileType.DARK_GRASS -> Color(0xFF1F4023)
    TileType.COBBLESTONE, TileType.TEMPLE_FLOOR -> Color(0xFF8C8275)
    TileType.DIRT_FLOOR, TileType.WOOD_PIER -> Color(0xFF6E533B)
    TileType.CRYPT_FLOOR -> Color(0xFF4F545E)
    TileType.OBSIDIAN_FLOOR -> Color(0xFF3A3142)
    TileType.WATER -> Color(0xFF23669E)
    TileType.LAVA -> Color(0xFFD94A1E)
    TileType.STAIRS_DOWN, TileType.STAIRS_UP, TileType.ROPE_HOLE_DOWN, TileType.ROPE_SPOT_UP -> TorchGold
    TileType.SMELTING_FURNACE, TileType.BLACKSMITH_ANVIL -> FireOrange
    TileType.FLETCHING_BENCH -> Color(0xFFD4A054)
    TileType.RUNECRAFTING_TABLE -> Color(0xFFA86BFF)
    TileType.TREE -> Color(0xFF1A3A1D)
    TileType.TREE_STUMP -> Color(0xFF5A422C)
    TileType.DEPOT_CHEST, TileType.MINI_DEPOT, TileType.SHOP_COUNTER, TileType.TEMPLE_ALTAR -> Color(0xFF5CE1FF)
    TileType.ORE_OCHRE, TileType.ORE_BOGSTONE, TileType.ORE_BRIMSTONE, TileType.ORE_AETHERIUM -> Color(0xFFB599FF)
    else -> Color(0xFF242120)
}

/**
 * TOP-LEFT CLUSTER: Clickable Square Isometric Minimap (N compass, +/- zoom, expand Regional Map)
 * paired directly beside the Character Portrait, Name, EXP/HP/MP bars, and Gold/Cap/Online status.
 */
@Composable
fun TopLeftHudCluster(
    state: GameWorldState,
    multiplayerState: MultiplayerUiState,
    saveBanner: String?,
    onWalkToTile: (Int, Int) -> Unit,
    onExpandWorldMap: () -> Unit,
    onMultiplayerStatusTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zoomRadii = remember { listOf(12, 18, 26) }
    var zoomIndex by rememberSaveable { mutableIntStateOf(1) }
    val currentRadius = zoomRadii[zoomIndex.coerceIn(0, zoomRadii.lastIndex)]

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (state.showMinimap) {
            IsometricMinimapCard(
                state = state,
                remotePlayers = multiplayerState.remotePlayers,
                radius = currentRadius,
                canZoomIn = zoomIndex > 0,
                canZoomOut = zoomIndex < zoomRadii.lastIndex,
                onZoomIn = { if (zoomIndex > 0) zoomIndex-- },
                onZoomOut = { if (zoomIndex < zoomRadii.lastIndex) zoomIndex++ },
                onTapWalk = onWalkToTile,
                onExpandWorldMap = onExpandWorldMap
            )
        }

        CharacterVitalsHudCard(
            state = state,
            multiplayerState = multiplayerState,
            saveBanner = saveBanner,
            onMultiplayerStatusTapped = onMultiplayerStatusTapped
        )
    }
}

@Composable
private fun IsometricMinimapCard(
    state: GameWorldState,
    remotePlayers: List<RemotePlayerState>,
    radius: Int,
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onTapWalk: (Int, Int) -> Unit,
    onExpandWorldMap: () -> Unit
) {
    val floorMap = remember(state.floor) { WorldMaps.getFloor(state.floor) }

    Surface(
        color = BasaltDark.copy(alpha = 0.90f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.4.dp, StoneBorderLight),
        modifier = Modifier
            .size(104.dp)
            .testTag("minimap_overlay")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .pointerInput(state.floor, state.playerX, state.playerY, radius) {
                        detectTapGestures { tap ->
                            val (wx, wy) = MinimapCoordinateMapper.screenTapToWorldTile(
                                tapX = tap.x,
                                tapY = tap.y,
                                canvasW = size.width.toFloat(),
                                canvasH = size.height.toFloat(),
                                playerX = state.playerX,
                                playerY = state.playerY,
                                radius = radius,
                                floorWidth = floorMap.width,
                                floorHeight = floorMap.height
                            )
                            onTapWalk(wx, wy)
                        }
                    }
            ) {
                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                val scale = (minOf(size.width, size.height) * 0.46f) / radius.toFloat()
                val stepFactor = 0.70710677f * scale
                val dotSize = (scale * 1.15f).coerceAtLeast(2.2f)

                // Isometric rotated terrain pips
                for (dy in -radius..radius) {
                    val wy = state.playerY + dy
                    if (wy !in 0 until floorMap.height) continue
                    for (dx in -radius..radius) {
                        val wx = state.playerX + dx
                        if (wx !in 0 until floorMap.width) continue
                        val sx = cx + (dx - dy) * stepFactor
                        val sy = cy + (dx + dy) * stepFactor
                        if (sx < -dotSize || sx > size.width + dotSize || sy < -dotSize || sy > size.height + dotSize) continue
                        val col = minimapTileColor(floorMap.tileAt(wx, wy))
                        drawRect(col, Offset(sx - dotSize * 0.5f, sy - dotSize * 0.5f), Size(dotSize, dotSize))
                    }
                }

                // Walk path breadcrumbs on minimap
                state.walkPath.forEach { (wx, wy) ->
                    val dx = wx - state.playerX
                    val dy = wy - state.playerY
                    if (abs(dx) <= radius && abs(dy) <= radius) {
                        val sx = cx + (dx - dy) * stepFactor
                        val sy = cy + (dx + dy) * stepFactor
                        drawCircle(TorchGold.copy(alpha = 0.85f), radius = 1.8f, center = Offset(sx, sy))
                    }
                }

                // Nearby monsters as crimson dots
                state.monsters.forEach { m ->
                    if (m.floor == state.floor) {
                        val dx = m.x - state.playerX
                        val dy = m.y - state.playerY
                        if (abs(dx) <= radius && abs(dy) <= radius) {
                            val sx = cx + (dx - dy) * stepFactor
                            val sy = cy + (dx + dy) * stepFactor
                            drawCircle(TargetRed, radius = if (m.id == state.targetMonsterId) 3.4f else 2.2f, center = Offset(sx, sy))
                        }
                    }
                }

                // Remote players as cyan dots
                remotePlayers.forEach { rp ->
                    if (rp.floor == state.floor) {
                        val dx = rp.x - state.playerX
                        val dy = rp.y - state.playerY
                        if (abs(dx) <= radius && abs(dy) <= radius) {
                            val sx = cx + (dx - dy) * stepFactor
                            val sy = cy + (dx + dy) * stepFactor
                            drawCircle(Color.Black, radius = 3.2f, center = Offset(sx, sy))
                            drawCircle(Color(0xFF47D7FF), radius = 2.2f, center = Offset(sx, sy))
                        }
                    }
                }

                // Local player center marker
                drawCircle(Color.Black, radius = 4f, center = Offset(cx, cy))
                drawCircle(Color.White, radius = 2.6f, center = Offset(cx, cy))
            }

            // North compass badge (top-left of minimap; isometric North points top-right/up-left so badge anchors top-left)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(ObsidianVoid.copy(alpha = 0.82f), RoundedCornerShape(4.dp))
                    .border(1.dp, TorchGold.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "N",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                    color = TorchGold
                )
            }

            // Expand Regional World Map button (top-right of minimap)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ObsidianVoid.copy(alpha = 0.82f))
                    .border(1.dp, StoneBorderLight, RoundedCornerShape(4.dp))
                    .clickable(onClick = onExpandWorldMap)
                    .testTag("minimap_expand_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.OpenInFull, "Open Regional World Map", tint = TorchGold, modifier = Modifier.size(13.dp))
            }

            // + / - Zoom controls (bottom-right of minimap)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MinimapTinyButton(
                    icon = Icons.Default.Add,
                    contentDesc = "Zoom In Minimap",
                    enabled = canZoomIn,
                    onClick = onZoomIn,
                    testTag = "minimap_zoom_in"
                )
                MinimapTinyButton(
                    icon = Icons.Default.Remove,
                    contentDesc = "Zoom Out Minimap",
                    enabled = canZoomOut,
                    onClick = onZoomOut,
                    testTag = "minimap_zoom_out"
                )
            }

            // Floor indicator pill (bottom-left of minimap)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp)
                    .background(ObsidianVoid.copy(alpha = 0.82f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "F${state.floor}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = ParchmentLight
                )
            }
        }
    }
}

@Composable
private fun MinimapTinyButton(
    icon: ImageVector,
    contentDesc: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ObsidianVoid.copy(alpha = 0.84f))
            .border(1.dp, if (enabled) StoneBorderLight else StoneBorderDark, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (enabled) ParchmentLight else ParchmentMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
private fun CharacterVitalsHudCard(
    state: GameWorldState,
    multiplayerState: MultiplayerUiState,
    saveBanner: String?,
    onMultiplayerStatusTapped: () -> Unit
) {
    val portraitSprite = remember(state.vocation, state.tunicColorIndex, state.hairColorIndex) {
        EntitySpriteAtlas.playerBitmap(
            vocation = state.vocation,
            frame = 0,
            tunicColorIndex = state.tunicColorIndex,
            hairColorIndex = state.hairColorIndex,
            direction = Direction.SOUTH
        )
    }
    val floorInfo = remember(state.floor) { WorldMaps.getFloor(state.floor) }
    val inPz = floorInfo.isProtectionZone(state.playerX, state.playerY)
    val remainingCap = state.remainingCapOz()
    val weaponNeedsAmmo = state.equipment.weaponDef()?.requiredAmmoKind != null

    Surface(
        color = BasaltDark.copy(alpha = 0.88f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.2.dp, StoneBorderLight),
        modifier = Modifier
            .widthIn(min = 212.dp, max = 278.dp)
            .testTag("character_vitals_hud")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Character Portrait Frame
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ObsidianVoid)
                    .border(1.5.dp, TorchGold.copy(alpha = 0.8f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                PixelSpritePreview(
                    bitmap = portraitSprite,
                    modifier = Modifier.size(40.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(BasaltDark.copy(alpha = 0.9f), RoundedCornerShape(topStart = 4.dp))
                        .padding(horizontal = 3.dp, vertical = 0.5.dp)
                ) {
                    Text(
                        text = "${state.level}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                        color = TorchGold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Name + Online Status Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = state.playerName,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 13.sp),
                        color = TorchGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    CompactOnlineIndicator(
                        mpState = multiplayerState,
                        onClick = onMultiplayerStatusTapped
                    )
                }

                // EXP Progress Bar
                CompactMeterBar(
                    label = "EXP",
                    valueText = "${(state.levelProgressFraction() * 100).toInt()}%",
                    fraction = state.levelProgressFraction(),
                    fillColor = ExpEmerald,
                    trackColor = Color(0xFF15291B),
                    heightDp = 7
                )

                // Red HP Bar
                CompactMeterBar(
                    label = "HP",
                    valueText = "${state.hp}/${state.maxHp}",
                    fraction = (state.hp.toFloat() / state.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                    fillColor = HealthCrimson,
                    trackColor = HealthCrimsonDark,
                    heightDp = 11
                )

                // Blue MP Bar
                CompactMeterBar(
                    label = "MP",
                    valueText = "${state.mp}/${state.maxMp}",
                    fraction = (state.mp.toFloat() / state.maxMp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                    fillColor = ManaSapphire,
                    trackColor = ManaSapphireDark,
                    heightDp = 11
                )

                // Gold / Cap / Soul / Live Status Chips (zero text clipping)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.gold}gp",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = TorchGold,
                        maxLines = 1
                    )
                    Text(
                        text = "Cap ${formatOz(remainingCap)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = if (remainingCap < 25.0) HealthCrimson else ParchmentLight,
                        maxLines = 1,
                        modifier = Modifier.testTag("hud_cap_text")
                    )
                    Text(
                        text = "S:${state.soul}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = SoulViolet,
                        maxLines = 1,
                        modifier = Modifier.testTag("hud_soul_text")
                    )
                    if (weaponNeedsAmmo) {
                        val ammoQty = state.activeAmmoCount()
                        HudMiniPill("A:$ammoQty", if (ammoQty > 0) TorchAmber else HealthCrimson)
                    }
                    if (inPz) HudMiniPill("PZ", ExpEmerald)
                    if (state.poisonTicks > 0) HudMiniPill("PSN", PoisonGreen)
                    if (state.fireTicks > 0) HudMiniPill("BRN", FireOrange)
                    if (saveBanner != null) HudMiniPill("Saved", TorchGold)
                }
            }
        }
    }
}

@Composable
private fun CompactOnlineIndicator(
    mpState: MultiplayerUiState,
    onClick: () -> Unit
) {
    val dotColor = when (mpState.status) {
        ConnectionStatus.ONLINE -> ExpEmerald
        ConnectionStatus.CONNECTING -> TorchAmber
        ConnectionStatus.OFFLINE_SOLO -> ParchmentMuted
    }
    val text = when (mpState.status) {
        ConnectionStatus.ONLINE -> "Online (${mpState.floorOnlineCount})"
        ConnectionStatus.CONNECTING -> "Sync..."
        ConnectionStatus.OFFLINE_SOLO -> "Offline"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(dotColor.copy(alpha = 0.16f))
            .border(1.dp, dotColor.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 1.5.dp)
            .testTag("multiplayer_status_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = dotColor,
            maxLines = 1
        )
    }
}

@Composable
private fun CompactMeterBar(
    label: String,
    valueText: String,
    fraction: Float,
    fillColor: Color,
    trackColor: Color,
    heightDp: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(trackColor)
            .border(0.8.dp, StoneBorderDark, RoundedCornerShape(3.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(fillColor)
        )
        if (heightDp >= 10) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold),
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1
                )
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HudMiniPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.22f), RoundedCornerShape(3.dp))
            .border(0.8.dp, color, RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp, vertical = 0.5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1
        )
    }
}

/**
 * LEFT EDGE VERTICAL SKILL DOCK:
 * Compact dark-framed column displaying icon, short label, and live level for
 * Magic, Fist, Club, Sword, Axe, Distance, Shielding, and Fishing.
 */
private data class SkillDockEntry(
    val shortLabel: String,
    val level: Int,
    val icon: ImageVector,
    val accent: Color
)

@Composable
fun VerticalSkillDock(
    state: GameWorldState,
    onOpenSkills: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = listOf(
        SkillDockEntry("MAG", state.skills.magic.level, Icons.Default.AutoFixHigh, Color(0xFFC084FC)),
        SkillDockEntry("FST", state.skills.fist.level, Icons.Default.SportsMma, Color(0xFFE8A468)),
        SkillDockEntry("CLB", state.skills.club.level, Icons.Default.Gavel, Color(0xFFD68C45)),
        SkillDockEntry("SWD", state.skills.sword.level, Icons.Default.MilitaryTech, Color(0xFFE05A47)),
        SkillDockEntry("AXE", state.skills.axe.level, Icons.Default.Hardware, Color(0xFFEF7B45)),
        SkillDockEntry("DST", state.skills.distance.level, Icons.Default.GpsFixed, Color(0xFF47B86E)),
        SkillDockEntry("SHD", state.skills.shielding.level, Icons.Default.Shield, Color(0xFF5CA8FF)),
        SkillDockEntry("FSH", state.skills.fishing.level, Icons.Default.SetMeal, Color(0xFF4AD2D9))
    )

    Surface(
        color = BasaltDark.copy(alpha = 0.84f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.2.dp, StoneBorderLight),
        modifier = modifier
            .width(58.dp)
            .testTag("vertical_skill_dock")
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 3.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            entries.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(StoneSurface.copy(alpha = 0.92f))
                        .border(0.8.dp, entry.accent.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .clickable(onClick = onOpenSkills)
                        .padding(horizontal = 3.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = entry.shortLabel,
                        tint = entry.accent,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = entry.shortLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                        color = ParchmentMuted,
                        maxLines = 1
                    )
                    Text(
                        text = "${entry.level}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold),
                        color = ParchmentLight,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * TOP-RIGHT UTILITY BAR & LIVE BATTLE LIST:
 * Quick-menu icon row (Backpack, Equipment, Skills, Save, World Map, Title) +
 * Live scrollable Battle List of nearby monsters & online allies.
 */
@Composable
fun TopRightUtilityAndBattleCluster(
    state: GameWorldState,
    remotePlayers: List<RemotePlayerState>,
    onOpenPanel: (ActivePanel) -> Unit,
    onSaveManual: () -> Unit,
    onReturnTitle: () -> Unit,
    onTargetMonster: (Int) -> Unit,
    onInspectAlly: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var battleExpanded by rememberSaveable { mutableStateOf(true) }

    val nearbyMonsters = remember(state.monsters, state.floor, state.playerX, state.playerY) {
        state.monsters
            .filter { m ->
                m.floor == state.floor &&
                    max(abs(m.x - state.playerX), abs(m.y - state.playerY)) <= 14
            }
            .sortedBy { m -> max(abs(m.x - state.playerX), abs(m.y - state.playerY)) }
    }
    val nearbyAllies = remember(remotePlayers, state.floor, state.playerX, state.playerY) {
        remotePlayers
            .filter { rp ->
                rp.floor == state.floor &&
                    max(abs(rp.x - state.playerX), abs(rp.y - state.playerY)) <= 16
            }
            .sortedBy { rp -> max(abs(rp.x - state.playerX), abs(rp.y - state.playerY)) }
    }

    Column(
        modifier = modifier.width(204.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Quick-Menu Icon Bar
        Surface(
            color = BasaltDark.copy(alpha = 0.90f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.2.dp, StoneBorderLight)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UtilityIconChip(Icons.Default.Backpack, "Backpack", state.activePanel == ActivePanel.BACKPACK, { onOpenPanel(ActivePanel.BACKPACK) }, "tab_backpack")
                UtilityIconChip(Icons.Default.Shield, "Equipment", state.activePanel == ActivePanel.EQUIPMENT, { onOpenPanel(ActivePanel.EQUIPMENT) }, "tab_equipment")
                UtilityIconChip(Icons.Default.MilitaryTech, "Skills", state.activePanel == ActivePanel.SKILLS, { onOpenPanel(ActivePanel.SKILLS) }, "tab_skills")
                UtilityIconChip(Icons.Default.CloudUpload, "Save", false, onSaveManual, "manual_save_button", accentTint = TorchGold)
                UtilityIconChip(Icons.Default.Map, "World Map", state.activePanel == ActivePanel.WORLD_MAP, { onOpenPanel(ActivePanel.WORLD_MAP) }, "utility_world_map_button")
                UtilityIconChip(Icons.Default.Home, "Character Select", false, onReturnTitle, "return_title_button")
            }
        }

        // Live Battle List Panel
        Surface(
            color = BasaltDark.copy(alpha = 0.86f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.2.dp, StoneBorderLight),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("battle_list_panel")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StoneElevated.copy(alpha = 0.92f))
                        .clickable { battleExpanded = !battleExpanded }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "BATTLE LIST (${nearbyMonsters.size + nearbyAllies.size})",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                        color = TorchGold,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = if (battleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Battle List",
                        tint = ParchmentMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (battleExpanded) {
                    if (nearbyMonsters.isEmpty() && nearbyAllies.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Area clear — no foes nearby",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = ParchmentMuted,
                                maxLines = 1
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 148.dp),
                            contentPadding = PaddingValues(4.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            items(nearbyMonsters, key = { "m_${it.id}" }) { monster ->
                                BattleMonsterRow(
                                    monster = monster,
                                    playerLevel = state.level,
                                    isTargeted = state.targetMonsterId == monster.id,
                                    onClick = { onTargetMonster(monster.id) }
                                )
                            }
                            items(nearbyAllies, key = { "p_${it.playerId}" }) { ally ->
                                BattleAllyRow(
                                    ally = ally,
                                    onClick = { onInspectAlly(ally.playerId) }
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
private fun UtilityIconChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
    accentTint: Color? = null
) {
    val bg = if (active) TorchGold else StoneSurface
    val tint = when {
        active -> ObsidianVoid
        accentTint != null -> accentTint
        else -> ParchmentLight
    }
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, if (active) TorchGold else StoneBorderDark, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun BattleMonsterRow(
    monster: MonsterEntity,
    playerLevel: Int,
    isTargeted: Boolean,
    onClick: () -> Unit
) {
    val icon = remember(monster.type) { EntitySpriteAtlas.monsterBitmap(monster.type, 0) }
    val hpFrac = (monster.hp.toFloat() / monster.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val hpColor = when {
        hpFrac > 0.6f -> Color(0xFF39C94A)
        hpFrac > 0.25f -> Color(0xFFE6B44C)
        else -> HealthCrimson
    }
    val band = monster.type.threatBandFor(playerLevel)
    val nameColor = when (band) {
        ThreatBand.EASY -> Color(0xFF6EF087)
        ThreatBand.EVEN -> ParchmentLight
        ThreatBand.CHALLENGING -> TorchGold
        ThreatBand.DEADLY -> TargetRed
    }

    Surface(
        color = if (isTargeted) Color(0xFF3B1518) else StoneSurface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(if (isTargeted) 1.5.dp else 0.8.dp, if (isTargeted) TargetRed else StoneBorderDark),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("battle_monster_${monster.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PixelSpritePreview(
                bitmap = icon,
                modifier = Modifier
                    .size(22.dp)
                    .background(ObsidianVoid, RoundedCornerShape(3.dp))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = monster.type.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = nameColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "L${monster.type.threatLevel}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                        color = ParchmentMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ObsidianVoid)
                ) {
                    Box(modifier = Modifier.fillMaxWidth(hpFrac).fillMaxHeight().background(hpColor))
                }
            }
        }
    }
}

@Composable
private fun BattleAllyRow(
    ally: RemotePlayerState,
    onClick: () -> Unit
) {
    val icon = remember(ally.vocation, ally.tunicColorIndex, ally.hairColorIndex) {
        EntitySpriteAtlas.playerBitmap(ally.vocation, 0, ally.tunicColorIndex, ally.hairColorIndex, Direction.SOUTH)
    }
    val hpFrac = (ally.hp.toFloat() / ally.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    Surface(
        color = StoneSurface.copy(alpha = 0.88f),
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(0.8.dp, Color(0xFF47D7FF).copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("battle_ally_${ally.playerId}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PixelSpritePreview(
                bitmap = icon,
                modifier = Modifier
                    .size(22.dp)
                    .background(ObsidianVoid, RoundedCornerShape(3.dp))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${ally.name} (L${ally.level})",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = Color(0xFF7BE8FF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ObsidianVoid)
                ) {
                    Box(modifier = Modifier.fillMaxWidth(hpFrac).fillMaxHeight().background(Color(0xFF47D7FF)))
                }
            }
        }
    }
}

/**
 * BOTTOM-LEFT TRANSLUCENT COMBAT & CHAT CONSOLE:
 * Compact collapsible log/chat widget tucked into the bottom-left with channel switch & input bar.
 */
@Composable
fun BottomLeftConsoleOverlay(
    statusLog: List<String>,
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

    LaunchedEffect(visibleMessages.size, statusLog.size, mpState.chatExpanded) {
        if (mpState.chatExpanded) {
            val total = visibleMessages.size + statusLog.size
            if (total > 0) listState.animateScrollToItem((total - 1).coerceAtLeast(0))
        }
    }

    Surface(
        color = BasaltDark.copy(alpha = if (mpState.chatExpanded) 0.90f else 0.72f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, StoneBorderLight.copy(alpha = 0.78f)),
        modifier = modifier
            .width(236.dp)
            .testTag("landscape_console_overlay")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .testTag("chat_toggle_bar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.Terminal, "Toggle Console", tint = TorchGold, modifier = Modifier.size(13.dp))
                Text(
                    text = if (mpState.chatExpanded) "CONSOLE & CHAT" else (statusLog.lastOrNull() ?: "Console"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = if (mpState.chatExpanded) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (mpState.chatExpanded) TorchGold else ParchmentLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!mpState.chatExpanded && mpState.unreadChatCount > 0) {
                    Box(
                        modifier = Modifier
                            .background(TorchGold, RoundedCornerShape(8.dp))
                            .padding(horizontal = 5.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = "${mpState.unreadChatCount}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold),
                            color = ObsidianVoid
                        )
                    }
                }
                Icon(
                    imageVector = if (mpState.chatExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = if (mpState.chatExpanded) "Collapse Console" else "Expand Console",
                    tint = ParchmentLight,
                    modifier = Modifier.size(15.dp)
                )
            }

            if (!mpState.chatExpanded && statusLog.size >= 2) {
                Text(
                    text = statusLog[statusLog.lastIndex - 1],
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = ParchmentMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 7.dp, end = 7.dp, bottom = 4.dp)
                )
            }

            if (mpState.chatExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ConsoleChip("Floor $currentFloor", mpState.activeChatChannel == ChatChannel.FLOOR) {
                            onSelectChannel(ChatChannel.FLOOR)
                        }
                        ConsoleChip("World", mpState.activeChatChannel == ChatChannel.WORLD) {
                            onSelectChannel(ChatChannel.WORLD)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                            .background(ObsidianVoid.copy(alpha = 0.72f), RoundedCornerShape(5.dp))
                    ) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(statusLog.takeLast(5)) { line ->
                                Text(
                                    text = "> $line",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                    color = ParchmentMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            items(visibleMessages, key = { it.id }) { msg ->
                                ConsoleChatLine(msg)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(StoneSurface)
                                .border(1.dp, StoneBorderLight, RoundedCornerShape(5.dp))
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (draft.isEmpty()) {
                                Text(
                                    text = "Say in ${mpState.activeChatChannel.label}...",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = ParchmentMuted,
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = draft,
                                onValueChange = { if (it.length <= 140) draft = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = ParchmentLight,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                cursorBrush = SolidColor(TorchGold),
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
                                    .fillMaxWidth()
                                    .testTag("chat_input_field")
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(TorchGold)
                                .clickable {
                                    if (draft.isNotBlank()) {
                                        onSendMessage(draft)
                                        draft = ""
                                    }
                                }
                                .testTag("chat_send_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send Chat", tint = ObsidianVoid, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) TorchGold else StoneElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = if (selected) ObsidianVoid else ParchmentLight
        )
    }
}

@Composable
private fun ConsoleChatLine(msg: ChatMessage) {
    val tagColor = when (msg.channel) {
        ChatChannel.SYSTEM -> TorchGold
        ChatChannel.WORLD -> Color(0xFF67E8F9)
        ChatChannel.FLOOR -> ExpEmerald
    }
    val prefix = when (msg.channel) {
        ChatChannel.SYSTEM -> "[!] "
        ChatChannel.WORLD -> "[W] ${msg.senderName}: "
        ChatChannel.FLOOR -> "[F] ${msg.senderName}: "
    }
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Bold)) { append(prefix) }
            withStyle(SpanStyle(color = if (msg.channel == ChatChannel.SYSTEM) TorchGold else ParchmentLight)) { append(msg.text) }
        },
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * BOTTOM-CENTER QUICK ACTION HOTBAR:
 * Core slots (Heal, Mana, Heal Rune, Attack Rune, Rope) + 2 customizable quick-bind slots
 * synced with live backpack counts.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomCenterQuickHotbar(
    state: GameWorldState,
    onHotkeyHp: () -> Unit,
    onHotkeyMp: () -> Unit,
    onHotkeyHealRune: () -> Unit,
    onHotkeyRune: () -> Unit,
    onHotkeyRope: () -> Unit,
    onUseCustomSlot: (Int) -> Unit,
    onBindCustomSlot: (Int, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var bindingSlotIndex by remember { mutableStateOf<Int?>(null) }

    val resolvedHealId = state.resolvedHealRuneId()
    val healRuneCount = state.itemCount(resolvedHealId)
    val resolvedAtkId = state.resolvedAttackRuneId() ?: "magic_dart_rune"
    val atkRuneCount = state.itemCount(resolvedAtkId)
    val atkRuneShortLabel = when (resolvedAtkId) {
        "magic_dart_rune" -> "Dart"
        "hmm_rune" -> "HMM"
        "fireball_rune" -> "GFB"
        "sd_rune" -> "SD"
        else -> stringResource(R.string.hotkey_rune)
    }

    Surface(
        color = BasaltDark.copy(alpha = 0.90f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.3.dp, StoneBorderLight),
        modifier = modifier.testTag("landscape_quick_hotbar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IsoHotbarSlot(
                itemId = "health_potion",
                label = "Heal",
                count = state.itemCount("health_potion"),
                accent = HealthCrimson,
                onClick = onHotkeyHp,
                testTag = "hotkey_health_potion"
            )
            IsoHotbarSlot(
                itemId = "mana_potion",
                label = "Mana",
                count = state.itemCount("mana_potion"),
                accent = ManaSapphire,
                onClick = onHotkeyMp,
                testTag = "hotkey_mana_potion"
            )
            IsoHotbarSlot(
                itemId = resolvedHealId,
                label = "H-Rn",
                count = healRuneCount,
                accent = ExpEmerald,
                onClick = onHotkeyHealRune,
                testTag = "hotkey_heal_rune"
            )
            IsoHotbarSlot(
                itemId = resolvedAtkId,
                label = atkRuneShortLabel,
                count = atkRuneCount,
                accent = SoulViolet,
                onClick = onHotkeyRune,
                testTag = "hotkey_attack_rune"
            )
            IsoHotbarSlot(
                itemId = "rope",
                label = "Rope",
                count = state.itemCount("rope"),
                accent = TorchGold,
                onClick = onHotkeyRope,
                testTag = "hotkey_rope"
            )

            // 2 Customizable Quick-Bind Slots (Tap to use or bind; Long-press to rebind/clear)
            for (slotIdx in 0..1) {
                val boundId = state.customHotbarSlots.getOrNull(slotIdx)
                val boundDef = boundId?.let { ItemCatalog.all[it] }
                if (boundId != null && boundDef != null) {
                    val count = state.itemCount(boundId)
                    IsoHotbarSlot(
                        itemId = boundId,
                        label = boundDef.name.take(5),
                        count = count,
                        accent = TorchAmber,
                        onClick = { onUseCustomSlot(slotIdx) },
                        onLongClick = { bindingSlotIndex = slotIdx },
                        testTag = "hotkey_custom_$slotIdx"
                    )
                } else {
                    Surface(
                        color = StoneSurface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, StoneBorderLight.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .size(width = 48.dp, height = 48.dp)
                            .combinedClickable(
                                onClick = { bindingSlotIndex = slotIdx },
                                onLongClick = { bindingSlotIndex = slotIdx }
                            )
                            .testTag("hotkey_custom_$slotIdx")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Add, "Bind Custom Slot ${slotIdx + 1}", tint = TorchGold, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Slot ${slotIdx + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                color = ParchmentMuted
                            )
                        }
                    }
                }
            }
        }
    }

    val activeSlot = bindingSlotIndex
    if (activeSlot != null) {
        CustomHotbarBindDialog(
            state = state,
            slotIndex = activeSlot,
            onSelect = { itemId ->
                onBindCustomSlot(activeSlot, itemId)
                bindingSlotIndex = null
            },
            onDismiss = { bindingSlotIndex = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IsoHotbarSlot(
    itemId: String,
    label: String,
    count: Int,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    testTag: String
) {
    val icon = remember(itemId) { EntitySpriteAtlas.itemIcon(itemId) }
    val alpha = if (count > 0) 1f else 0.48f
    Surface(
        color = StoneSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.2.dp, if (count > 0) accent.copy(alpha = 0.85f) else StoneBorderDark),
        modifier = Modifier
            .size(width = 48.dp, height = 48.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag(testTag)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            PixelSpritePreview(
                bitmap = icon,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopCenter)
                    .background(ObsidianVoid, RoundedCornerShape(4.dp))
            )
            // Live backpack count pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(ObsidianVoid.copy(alpha = 0.88f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp)
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (count > 0) TorchGold else ParchmentMuted
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold),
                color = ParchmentLight.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun CustomHotbarBindDialog(
    state: GameWorldState,
    slotIndex: Int,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val bindableItems = remember(state.backpack, state.toolBag) {
        (state.backpack + state.toolBag)
            .filter { it.count > 0 && GameEngine.isCustomHotbarBindable(it.itemId) }
            .distinctBy { it.itemId }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = BasaltDark,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, TorchGold),
            modifier = Modifier.widthIn(min = 260.dp, max = 380.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BIND CUSTOM SLOT ${slotIndex + 1}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TorchGold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = ParchmentLight, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    text = "Pick any potion, rune, or food from your bag. Long-press slot later to change.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ParchmentMuted
                )

                if (bindableItems.isEmpty()) {
                    Text(
                        text = "No potions, runes, or food currently in your backpack.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ParchmentMuted,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 175.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(bindableItems, key = { it.itemId }) { stack ->
                            val def = ItemCatalog.get(stack.itemId)
                            val icon = remember(stack.itemId) { EntitySpriteAtlas.itemIcon(stack.itemId) }
                            Surface(
                                color = StoneSurface,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, StoneBorderLight),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(stack.itemId) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PixelSpritePreview(icon, Modifier.size(26.dp).background(ObsidianVoid, RoundedCornerShape(4.dp)))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(def.name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = ParchmentLight)
                                        Text("Owned: x${state.itemCount(stack.itemId)}", style = MaterialTheme.typography.labelSmall, color = TorchGold)
                                    }
                                    Text("BIND", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ExpEmerald)
                                }
                            }
                        }
                    }
                }

                if (state.customHotbarSlots.getOrNull(slotIndex) != null) {
                    Surface(
                        color = HealthCrimson.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, HealthCrimson),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(null) }
                    ) {
                        Text(
                            text = "Clear Slot ${slotIndex + 1}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = HealthCrimson,
                            modifier = Modifier.padding(vertical = 7.dp).align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

/**
 * REGIONAL WORLD MAP PANEL CONTENT:
 * Full interactive floor overview with landmarks, stairs/rope holes, allies, foes, and tap-to-A*-walk.
 */
@Composable
fun RegionalWorldMapContent(
    state: GameWorldState,
    remotePlayers: List<RemotePlayerState>,
    onWalkToTile: (Int, Int) -> Unit
) {
    val floorMap = remember(state.floor) { WorldMaps.getFloor(state.floor) }
    val floorLandmarks = remember(state.floor) { WorldMaps.landmarks.filter { it.floor == state.floor } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(258.dp)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Interactive full-floor map canvas
        Surface(
            color = ObsidianVoid,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.2.dp, StoneBorderLight),
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .pointerInput(state.floor) {
                        detectTapGestures { tap ->
                            val tx = ((tap.x / size.width.coerceAtLeast(1)) * floorMap.width).toInt()
                                .coerceIn(0, floorMap.width - 1)
                            val ty = ((tap.y / size.height.coerceAtLeast(1)) * floorMap.height).toInt()
                                .coerceIn(0, floorMap.height - 1)
                            onWalkToTile(tx, ty)
                        }
                    }
                    .testTag("regional_world_map_canvas")
            ) {
                val cellW = size.width / floorMap.width.toFloat()
                val cellH = size.height / floorMap.height.toFloat()
                for (ty in 0 until floorMap.height) {
                    for (tx in 0 until floorMap.width) {
                        val col = minimapTileColor(floorMap.tileAt(tx, ty))
                        drawRect(col, Offset(tx * cellW, ty * cellH), Size(cellW + 0.6f, cellH + 0.6f))
                    }
                }
                // Walk path line
                state.walkPath.forEach { (wx, wy) ->
                    drawRect(TorchGold, Offset(wx * cellW, wy * cellH), Size(max(cellW, 2.5f), max(cellH, 2.5f)))
                }
                // Monsters
                state.monsters.forEach { m ->
                    if (m.floor == state.floor) {
                        drawCircle(TargetRed, radius = max(cellW * 0.8f, 2.4f), center = Offset((m.x + 0.5f) * cellW, (m.y + 0.5f) * cellH))
                    }
                }
                // Allies
                remotePlayers.forEach { rp ->
                    if (rp.floor == state.floor) {
                        drawCircle(Color(0xFF47D7FF), radius = max(cellW * 1.1f, 3.2f), center = Offset((rp.x + 0.5f) * cellW, (rp.y + 0.5f) * cellH))
                    }
                }
                // Player bright marker
                val px = (state.playerX + 0.5f) * cellW
                val py = (state.playerY + 0.5f) * cellH
                drawCircle(Color.Black, radius = 5.5f, center = Offset(px, py))
                drawCircle(Color.White, radius = 3.8f, center = Offset(px, py))
            }
        }

        // Landmarks quick-travel list (runs A* auto-walk to any landmark!)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${floorMap.name} (Floor ${state.floor})",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = TorchGold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Tap anywhere on the map or pick a landmark below to A* auto-walk:",
                style = MaterialTheme.typography.labelSmall,
                color = ParchmentMuted
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(floorLandmarks, key = { "${it.floor}_${it.x}_${it.y}_${it.label}" }) { lm ->
                    Surface(
                        color = StoneSurface,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, StoneBorderDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onWalkToTile(lm.x, lm.y) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lm.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ParchmentLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "(${lm.x},${lm.y})",
                                style = MaterialTheme.typography.labelSmall,
                                color = TorchGold
                            )
                        }
                    }
                }
            }
        }
    }
}
