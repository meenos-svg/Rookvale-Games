package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.game.world.WorldBiomeBlueprint
import com.example.game.world.WorldPinCategory
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.BossSpecialKind
import com.example.game.model.Direction
import com.example.game.model.GameWorldState
import com.example.game.model.ItemCatalog
import com.example.game.model.MonsterEntity
import com.example.game.model.PlayerSkills
import com.example.game.model.ThreatBand
import com.example.game.model.TileType
import com.example.game.model.normalizeRegionId
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure minimap tap-to-tile helper synced dynamically with the 360-degree cameraYaw orientation.
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
        floorHeight: Int,
        cameraYawDeg: Float = IsometricCamera.DEFAULT_YAW_DEG
    ): Pair<Int, Int> {
        if (canvasW <= 1f || canvasH <= 1f) return playerX to playerY
        val cx = canvasW * 0.5f
        val cy = canvasH * 0.5f
        val scale = (minOf(canvasW, canvasH) * 0.46f) / radius.coerceAtLeast(1).toFloat()
        val u = (tapX - cx) / scale
        val v = (tapY - cy) / scale
        val rad = IsometricCamera.normalizeYawDeg(cameraYawDeg) * (PI / 180.0)
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        // Exact inverse of: u = dx*c - dy*s, v = dx*s + dy*c
        val dx = (u * c + v * s).roundToInt()
        val dy = (-u * s + v * c).roundToInt()
        val wx = (playerX + dx).coerceIn(0, (floorWidth - 1).coerceAtLeast(0))
        val wy = (playerY + dy).coerceIn(0, (floorHeight - 1).coerceAtLeast(0))
        return wx to wy
    }
}

internal fun minimapTileColor(tile: TileType): Color = when (tile) {
    TileType.GRASS -> Color(0xFF3E6E33)
    TileType.FLOWER_GRASS -> Color(0xFF538A3E)
    TileType.DARK_GRASS -> Color(0xFF1F4023)
    TileType.COBBLESTONE -> Color(0xFF948A7C)
    TileType.TEMPLE_FLOOR -> Color(0xFFD8CEB8)
    TileType.WOOD_PLANK_FLOOR -> Color(0xFF8C6541)
    TileType.ARCANE_FLOOR -> Color(0xFF4C3F73)
    TileType.DIRT_FLOOR -> Color(0xFF73573E)
    TileType.WOOD_PIER, TileType.WOOD_BRIDGE -> Color(0xFFA87E52)
    TileType.STONE_BRIDGE -> Color(0xFF8C867B)
    TileType.IRON_BAR_GATE -> Color(0xFF697380)
    TileType.CRYPT_FLOOR -> Color(0xFF4F545E)
    TileType.OBSIDIAN_FLOOR -> Color(0xFF3A3142)
    TileType.WATER -> Color(0xFF23669E)
    TileType.DEEP_WATER -> Color(0xFF103052)
    TileType.SAND -> Color(0xFFD6B36D)
    TileType.MARSH -> Color(0xFF3E5234)
    TileType.SNOW -> Color(0xFFE6EEF7)
    TileType.JUNGLE_FLOOR -> Color(0xFF235E2A)
    TileType.VOLCANIC_ASH -> Color(0xFF453D3B)
    TileType.BLIGHT_SOIL -> Color(0xFF422C52)
    TileType.FOUNTAIN -> Color(0xFF4FC3F7)
    TileType.MOORED_BOAT -> Color(0xFF8D5E34)
    TileType.LAVA -> Color(0xFFD94A1E)
    TileType.STAIRS_DOWN, TileType.STAIRS_UP, TileType.ROPE_HOLE_DOWN, TileType.ROPE_SPOT_UP -> TorchGold
    TileType.SMELTING_FURNACE, TileType.BLACKSMITH_ANVIL -> FireOrange
    TileType.FLETCHING_BENCH, TileType.WEAPON_RACK -> Color(0xFFD4A054)
    TileType.RUNECRAFTING_TABLE, TileType.CRYSTAL_PILLAR -> Color(0xFFA86BFF)
    TileType.BOOKSHELF -> Color(0xFF7A4B2A)
    TileType.TREE -> Color(0xFF1A3A1D)
    TileType.HEDGE -> Color(0xFF2B6127)
    TileType.WOOD_FENCE -> Color(0xFF735030)
    TileType.LANTERN_POST -> Color(0xFFFFD86B)
    TileType.MARKET_AWNING -> Color(0xFFC9443C)
    TileType.WOOD_BED -> Color(0xFFC44B45)
    TileType.CRATE_BARREL, TileType.TEMPLE_PEW -> Color(0xFF946B3C)
    TileType.TREE_STUMP -> Color(0xFF5A422C)
    TileType.DEPOT_CHEST, TileType.MINI_DEPOT, TileType.SHOP_COUNTER, TileType.TEMPLE_ALTAR -> Color(0xFF5CE1FF)
    TileType.ORE_COPPER -> Color(0xFFE08234)
    TileType.ORE_TIN -> Color(0xFFA9B9C8)
    TileType.ORE_BOG_IRON -> Color(0xFF7D90A3)
    TileType.ORE_VESPERITE -> Color(0xFFB599FF)
    TileType.ORE_CINDERITE -> Color(0xFFFFB22E)
    TileType.TIMBER_WALL, TileType.TIMBER_WINDOW_WALL -> Color(0xFFCBBCA4)
    TileType.TEMPLE_WALL -> Color(0xFFE0D6C3)
    TileType.GATE_PILLAR -> Color(0xFFAAA091)
    TileType.SANDSTONE_WALL -> Color(0xFFD9B271)
    TileType.GRANITE_WALL -> Color(0xFF8B949E)
    TileType.OBSIDIAN_WALL -> Color(0xFF423452)
    TileType.WAR_BANNER -> Color(0xFFC82838)
    TileType.CAMPFIRE_BRAZIER -> Color(0xFFFF9638)
    else -> Color(0xFF242120)
}

/**
 * TOP-LEFT CLUSTER: Clickable Square 360-Degree Rotatable Minimap (tap N compass to snap North,
 * live camera-view cone, Rotate-Drag toggle, +/- zoom, expand Regional World Map)
 * paired directly beside the Character Portrait, Name, EXP/HP/MP bars, and Gold/Cap/Online status.
 */
@Composable
fun TopLeftHudCluster(
    state: GameWorldState,
    multiplayerState: MultiplayerUiState,
    saveBanner: String?,
    cameraYawDeg: Float = IsometricCamera.DEFAULT_YAW_DEG,
    cameraYawProvider: (() -> Float)? = null,
    rotateDragMode: Boolean = false,
    onResetNorth: () -> Unit = {},
    onToggleRotateDragMode: () -> Unit = {},
    onWalkToTile: (Int, Int) -> Unit,
    onExpandWorldMap: () -> Unit,
    onMultiplayerStatusTapped: () -> Unit,
    onOpenAdminScroll: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val zoomRadii = remember { listOf(14, 22, 34) }
    var zoomIndex by rememberSaveable { mutableIntStateOf(1) }
    val currentRadius = zoomRadii[zoomIndex.coerceIn(0, zoomRadii.lastIndex)]
    val resolvedYawProvider: () -> Float = cameraYawProvider ?: { cameraYawDeg }

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
                cameraYawProvider = resolvedYawProvider,
                rotateDragMode = rotateDragMode,
                canZoomIn = zoomIndex > 0,
                canZoomOut = zoomIndex < zoomRadii.lastIndex,
                onZoomIn = { if (zoomIndex > 0) zoomIndex-- },
                onZoomOut = { if (zoomIndex < zoomRadii.lastIndex) zoomIndex++ },
                onResetNorth = onResetNorth,
                onToggleRotateDragMode = onToggleRotateDragMode,
                onTapWalk = onWalkToTile,
                onExpandWorldMap = onExpandWorldMap
            )
        }

        CharacterVitalsHudCard(
            state = state,
            multiplayerState = multiplayerState,
            saveBanner = saveBanner,
            onMultiplayerStatusTapped = onMultiplayerStatusTapped,
            onOpenAdminScroll = onOpenAdminScroll
        )
    }
}

@Composable
private fun IsometricMinimapCard(
    state: GameWorldState,
    remotePlayers: List<RemotePlayerState>,
    radius: Int,
    cameraYawProvider: () -> Float,
    rotateDragMode: Boolean,
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetNorth: () -> Unit,
    onToggleRotateDragMode: () -> Unit,
    onTapWalk: (Int, Int) -> Unit,
    onExpandWorldMap: () -> Unit
) {
    val floorMap = remember(state.floor) { WorldMaps.getFloor(state.floor) }
    val isRotatedAwayFromNorth by remember(cameraYawProvider) {
        derivedStateOf {
            abs(IsometricCamera.shortestAngleDeltaDeg(cameraYawProvider(), IsometricCamera.DEFAULT_YAW_DEG)) > 2f
        }
    }
    val minimapCache = remember { MinimapOffscreenCache() }

    Surface(
        color = BasaltDark.copy(alpha = 0.90f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.4.dp, if (rotateDragMode) TorchGold else StoneBorderLight),
        modifier = Modifier
            .size(108.dp)
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
                                floorHeight = floorMap.height,
                                cameraYawDeg = cameraYawProvider()
                            )
                            onTapWalk(wx, wy)
                        }
                    }
            ) {
                val widthPx = size.width.roundToInt().coerceAtLeast(1)
                val heightPx = size.height.roundToInt().coerceAtLeast(1)
                val cachedBitmap = minimapCache.getOrRender(
                    widthPx = widthPx,
                    heightPx = heightPx,
                    floorMap = floorMap,
                    state = state,
                    remotePlayers = remotePlayers,
                    radius = radius,
                    cameraYawDeg = cameraYawProvider(),
                    nowUptimeMs = SystemClock.uptimeMillis()
                )
                if (cachedBitmap != null) {
                    drawImage(
                        image = cachedBitmap,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(widthPx, heightPx),
                        filterQuality = FilterQuality.Low
                    )
                }
            }

            // Clickable North compass badge: tap to smoothly snap cameraYaw back to North (45°)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isRotatedAwayFromNorth) TorchGold.copy(alpha = 0.25f) else ObsidianVoid.copy(alpha = 0.84f))
                    .border(1.dp, if (isRotatedAwayFromNorth) TorchGold else TorchGold.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .clickable(onClick = onResetNorth)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
                    .testTag("minimap_compass_north")
            ) {
                Text(
                    text = "N",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold),
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
                    .background(ObsidianVoid.copy(alpha = 0.84f))
                    .border(1.dp, StoneBorderLight, RoundedCornerShape(4.dp))
                    .clickable(onClick = onExpandWorldMap)
                    .testTag("minimap_expand_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.OpenInFull, "Open Regional World Map", tint = TorchGold, modifier = Modifier.size(13.dp))
            }

            // Rotate-Drag Mode + / - Zoom controls (bottom-right of minimap)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (rotateDragMode) TorchGold.copy(alpha = 0.28f) else ObsidianVoid.copy(alpha = 0.84f))
                        .border(1.dp, if (rotateDragMode) TorchGold else StoneBorderLight, RoundedCornerShape(4.dp))
                        .clickable(onClick = onToggleRotateDragMode)
                        .testTag("minimap_rotate_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = "Toggle 360 Camera Drag Rotate",
                        tint = if (rotateDragMode) TorchGold else ParchmentLight,
                        modifier = Modifier.size(12.dp)
                    )
                }
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
                    .background(ObsidianVoid.copy(alpha = 0.84f), RoundedCornerShape(3.dp))
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

@Immutable
private data class CharacterVitalsSnapshot(
    val playerName: String,
    val level: Int,
    val expFraction: Float,
    val hp: Int,
    val effMaxHp: Int,
    val mp: Int,
    val maxMp: Int,
    val gold: Int,
    val remainingCap: Double,
    val soul: Int,
    val weaponNeedsAmmo: Boolean,
    val ammoCount: Int,
    val isAdmin: Boolean,
    val isInvulnerable: Boolean,
    val isSpectator: Boolean,
    val inPz: Boolean,
    val poisoned: Boolean,
    val burning: Boolean,
    val saveBanner: String?,
    val connectionStatus: ConnectionStatus,
    val floorOnlineCount: Int
)

@Composable
private fun CharacterVitalsHudCard(
    state: GameWorldState,
    multiplayerState: MultiplayerUiState,
    saveBanner: String?,
    onMultiplayerStatusTapped: () -> Unit,
    onOpenAdminScroll: () -> Unit = {}
) {
    val portraitAppearance = remember(state.vocation, state.tunicColorIndex, state.hairColorIndex, state.equipment) {
        PaperDollAppearance.fromEquipment(
            vocation = state.vocation,
            tunicColorIndex = state.tunicColorIndex,
            hairColorIndex = state.hairColorIndex,
            equipment = state.equipment
        )
    }
    val floorInfo = remember(state.floor) { WorldMaps.getFloor(state.floor) }
    val weaponNeedsAmmo = state.equipment.weaponDef()?.requiredAmmoKind != null
    val snapshot = CharacterVitalsSnapshot(
        playerName = state.playerName,
        level = state.level,
        expFraction = state.levelProgressFraction(),
        hp = state.hp,
        effMaxHp = state.effectiveMaxHp(),
        mp = state.mp,
        maxMp = state.maxMp,
        gold = state.gold,
        remainingCap = state.remainingCapOz(),
        soul = state.soul,
        weaponNeedsAmmo = weaponNeedsAmmo,
        ammoCount = if (weaponNeedsAmmo) state.activeAmmoCount() else 0,
        isAdmin = state.isAdmin,
        isInvulnerable = state.isInvulnerable,
        isSpectator = state.isSpectator,
        inPz = floorInfo.isProtectionZone(state.playerX, state.playerY),
        poisoned = state.poisonTicks > 0,
        burning = state.fireTicks > 0,
        saveBanner = saveBanner,
        connectionStatus = multiplayerState.status,
        floorOnlineCount = multiplayerState.floorOnlineCount
    )
    CharacterVitalsHudCardContent(
        snapshot = snapshot,
        portraitAppearance = portraitAppearance,
        onMultiplayerStatusTapped = onMultiplayerStatusTapped,
        onOpenAdminScroll = onOpenAdminScroll
    )
}

@Composable
private fun CharacterVitalsHudCardContent(
    snapshot: CharacterVitalsSnapshot,
    portraitAppearance: PaperDollAppearance,
    onMultiplayerStatusTapped: () -> Unit,
    onOpenAdminScroll: () -> Unit
) {
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
            // Live Articulated Character Portrait Frame
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ObsidianVoid)
                    .border(1.5.dp, TorchGold.copy(alpha = 0.8f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                LivePaperDollPreview(
                    appearance = portraitAppearance,
                    contentDesc = "${snapshot.playerName} portrait",
                    modifier = Modifier.size(44.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(BasaltDark.copy(alpha = 0.9f), RoundedCornerShape(topStart = 4.dp))
                        .padding(horizontal = 3.dp, vertical = 0.5.dp)
                ) {
                    Text(
                        text = "${snapshot.level}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                        color = TorchGold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Name + Admin Golden Crown + Online Status Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (snapshot.isAdmin) {
                        AdminGoldenCrownIcon(sizeDp = 14.dp)
                    }
                    Text(
                        text = snapshot.playerName,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 13.sp),
                        color = TorchGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    CompactOnlineIndicator(
                        status = snapshot.connectionStatus,
                        floorOnlineCount = snapshot.floorOnlineCount,
                        onClick = onMultiplayerStatusTapped
                    )
                }

                // EXP Progress Bar
                CompactMeterBar(
                    label = "EXP",
                    valueText = "${(snapshot.expFraction * 100).toInt()}%",
                    fraction = snapshot.expFraction,
                    fillColor = ExpEmerald,
                    trackColor = Color(0xFF15291B),
                    heightDp = 7
                )

                // Red HP Bar (with ∞ Immortal indicator for Admin)
                CompactMeterBar(
                    label = "HP",
                    valueText = if (snapshot.isInvulnerable) "∞ / ${snapshot.effMaxHp}" else "${snapshot.hp}/${snapshot.effMaxHp}",
                    fraction = if (snapshot.isInvulnerable) 1f else (snapshot.hp.toFloat() / snapshot.effMaxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                    fillColor = if (snapshot.isInvulnerable) TorchGold else HealthCrimson,
                    trackColor = HealthCrimsonDark,
                    heightDp = 11
                )

                // Blue MP Bar
                CompactMeterBar(
                    label = "MP",
                    valueText = "${snapshot.mp}/${snapshot.maxMp}",
                    fraction = (snapshot.mp.toFloat() / snapshot.maxMp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
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
                        text = "${snapshot.gold}gp",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = TorchGold,
                        maxLines = 1
                    )
                    Text(
                        text = "Cap ${formatOz(snapshot.remainingCap)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = if (snapshot.remainingCap < 25.0) HealthCrimson else ParchmentLight,
                        maxLines = 1,
                        modifier = Modifier.testTag("hud_cap_text")
                    )
                    Text(
                        text = "S:${snapshot.soul}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = SoulViolet,
                        maxLines = 1,
                        modifier = Modifier.testTag("hud_soul_text")
                    )
                    if (snapshot.weaponNeedsAmmo) {
                        HudMiniPill("A:${snapshot.ammoCount}", if (snapshot.ammoCount > 0) TorchAmber else HealthCrimson)
                    }
                    if (snapshot.isSpectator) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF281E44))
                                .border(0.8.dp, Color(0xFFA78BFA), RoundedCornerShape(4.dp))
                                .clickable(onClick = onOpenAdminScroll)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                .testTag("hud_spectator_pill")
                        ) {
                            Text(
                                text = "SPECTATOR",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold),
                                color = Color(0xFFC4B5FD),
                                maxLines = 1
                            )
                        }
                    }
                    if (snapshot.inPz) HudMiniPill("PZ", ExpEmerald)
                    if (snapshot.poisoned) HudMiniPill("PSN", PoisonGreen)
                    if (snapshot.burning) HudMiniPill("BRN", FireOrange)
                    if (snapshot.saveBanner != null) HudMiniPill("Saved", TorchGold)
                }
            }
        }
    }
}

@Composable
private fun CompactOnlineIndicator(
    status: ConnectionStatus,
    floorOnlineCount: Int,
    onClick: () -> Unit
) {
    val dotColor = when (status) {
        ConnectionStatus.ONLINE -> ExpEmerald
        ConnectionStatus.CONNECTING -> TorchAmber
        ConnectionStatus.OFFLINE_SOLO -> ParchmentMuted
    }
    val text = when (status) {
        ConnectionStatus.ONLINE -> "Online ($floorOnlineCount)"
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
    VerticalSkillDock(
        skills = state.skills,
        onOpenSkills = onOpenSkills,
        modifier = modifier
    )
}

@Composable
fun VerticalSkillDock(
    skills: PlayerSkills,
    onOpenSkills: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = remember(skills) {
        listOf(
            SkillDockEntry("MAG", skills.magic.level, Icons.Default.AutoFixHigh, Color(0xFFC084FC)),
            SkillDockEntry("FST", skills.fist.level, Icons.Default.SportsMma, Color(0xFFE8A468)),
            SkillDockEntry("CLB", skills.club.level, Icons.Default.Gavel, Color(0xFFD68C45)),
            SkillDockEntry("SWD", skills.sword.level, Icons.Default.MilitaryTech, Color(0xFFE05A47)),
            SkillDockEntry("AXE", skills.axe.level, Icons.Default.Hardware, Color(0xFFEF7B45)),
            SkillDockEntry("DST", skills.distance.level, Icons.Default.GpsFixed, Color(0xFF47B86E)),
            SkillDockEntry("SHD", skills.shielding.level, Icons.Default.Shield, Color(0xFF5CA8FF)),
            SkillDockEntry("FSH", skills.fishing.level, Icons.Default.SetMeal, Color(0xFF4AD2D9))
        )
    }

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

@Immutable
private data class BattleHudSnapshot(
    val activePanel: ActivePanel,
    val playerLevel: Int,
    val playerX: Int,
    val playerY: Int,
    val targetMonsterId: Int?,
    val nearbyMonsters: List<MonsterEntity>,
    val onlineAllies: List<RemotePlayerState>
)

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
    onWalkToTile: (Int, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val nearbyMonsters = remember(state.monsters, state.floor, state.playerX, state.playerY) {
        state.monsters
            .filter { m ->
                m.floor == state.floor &&
                    max(abs(m.x - state.playerX), abs(m.y - state.playerY)) <= 14
            }
            .sortedBy { m -> max(abs(m.x - state.playerX), abs(m.y - state.playerY)) }
    }
    val localRegionId = remember(state.regionId) { normalizeRegionId(state.regionId) }
    val onlineAllies = remember(remotePlayers, state.floor, localRegionId, state.playerX, state.playerY) {
        remotePlayers
            .filter { rp ->
                !rp.isSpectator && rp.floor == state.floor && normalizeRegionId(rp.regionId) == localRegionId
            }
            .sortedBy { rp -> max(abs(rp.x - state.playerX), abs(rp.y - state.playerY)) }
    }
    val snapshot = BattleHudSnapshot(
        activePanel = state.activePanel,
        playerLevel = state.level,
        playerX = state.playerX,
        playerY = state.playerY,
        targetMonsterId = state.targetMonsterId,
        nearbyMonsters = nearbyMonsters,
        onlineAllies = onlineAllies
    )
    TopRightUtilityAndBattleClusterContent(
        snapshot = snapshot,
        onOpenPanel = onOpenPanel,
        onSaveManual = onSaveManual,
        onReturnTitle = onReturnTitle,
        onTargetMonster = onTargetMonster,
        onInspectAlly = onInspectAlly,
        onWalkToTile = onWalkToTile,
        modifier = modifier
    )
}

@Composable
private fun TopRightUtilityAndBattleClusterContent(
    snapshot: BattleHudSnapshot,
    onOpenPanel: (ActivePanel) -> Unit,
    onSaveManual: () -> Unit,
    onReturnTitle: () -> Unit,
    onTargetMonster: (Int) -> Unit,
    onInspectAlly: (String) -> Unit,
    onWalkToTile: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var battleExpanded by rememberSaveable { mutableStateOf(true) }
    val nearbyMonsters = snapshot.nearbyMonsters
    val onlineAllies = snapshot.onlineAllies
    val liveOnlineCount = 1 + onlineAllies.size

    Column(
        modifier = modifier,
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
                UtilityIconChip(Icons.Default.Backpack, "Backpack", snapshot.activePanel == ActivePanel.BACKPACK, { onOpenPanel(ActivePanel.BACKPACK) }, "tab_backpack")
                UtilityIconChip(Icons.Default.Shield, "Equipment", snapshot.activePanel == ActivePanel.EQUIPMENT, { onOpenPanel(ActivePanel.EQUIPMENT) }, "tab_equipment")
                UtilityIconChip(Icons.Default.MilitaryTech, "Skills", snapshot.activePanel == ActivePanel.SKILLS, { onOpenPanel(ActivePanel.SKILLS) }, "tab_skills")
                UtilityIconChip(Icons.Default.CloudUpload, "Save", false, onSaveManual, "manual_save_button", accentTint = TorchGold)
                UtilityIconChip(Icons.Default.Map, "World Map", snapshot.activePanel == ActivePanel.WORLD_MAP, { onOpenPanel(ActivePanel.WORLD_MAP) }, "utility_world_map_button")
                UtilityIconChip(Icons.Default.Settings, "Settings", snapshot.activePanel == ActivePanel.SETTINGS, { onOpenPanel(ActivePanel.SETTINGS) }, "utility_settings_button")
                UtilityIconChip(Icons.Default.Home, "Character Select", false, onReturnTitle, "return_title_button")
            }
        }

        // Live Online & Battle List Panel
        Surface(
            color = BasaltDark.copy(alpha = 0.86f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.2.dp, StoneBorderLight),
            modifier = Modifier
                .width(214.dp)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "BATTLE (${nearbyMonsters.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold),
                            color = TorchGold,
                            maxLines = 1
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ObsidianVoid.copy(alpha = 0.85f))
                                .border(0.8.dp, Color(0xFF47D7FF).copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                .testTag("battle_list_online_count"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (onlineAllies.isNotEmpty()) Color(0xFF47D7FF) else TorchGold)
                            )
                            Text(
                                text = "ONLINE $liveOnlineCount",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold),
                                color = Color(0xFF8CF0FF),
                                maxLines = 1
                            )
                        }
                    }
                    Icon(
                        imageVector = if (battleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Battle List",
                        tint = ParchmentMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (battleExpanded) {
                    if (nearbyMonsters.isEmpty() && onlineAllies.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Area clear — no foes or allies",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = ParchmentMuted,
                                maxLines = 1
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 158.dp),
                            contentPadding = PaddingValues(4.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            items(onlineAllies, key = { "p_${it.playerId}" }) { ally ->
                                val dist = max(abs(ally.x - snapshot.playerX), abs(ally.y - snapshot.playerY))
                                BattleAllyRow(
                                    ally = ally,
                                    distanceTiles = dist,
                                    onInspect = { onInspectAlly(ally.playerId) },
                                    onWalkTo = { onWalkToTile(ally.x, ally.y) }
                                )
                            }
                            items(nearbyMonsters, key = { "m_${it.id}" }) { monster ->
                                BattleMonsterRow(
                                    monster = monster,
                                    playerLevel = snapshot.playerLevel,
                                    isTargeted = snapshot.targetMonsterId == monster.id,
                                    onClick = { onTargetMonster(monster.id) }
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
    val hpFrac = (monster.hp.toFloat() / monster.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val hpColor = when {
        hpFrac > 0.6f -> Color(0xFF39C94A)
        hpFrac > 0.25f -> Color(0xFFE6B44C)
        else -> HealthCrimson
    }
    val band = monster.threatBandFor(playerLevel)
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
            ArticulatedMonsterPreview(
                type = monster.type,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ObsidianVoid)
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
                        text = "L${monster.level}",
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
    distanceTiles: Int,
    onInspect: () -> Unit,
    onWalkTo: () -> Unit
) {
    val allyAppearance = remember(
        ally.vocation, ally.tunicColorIndex, ally.hairColorIndex,
        ally.weaponId, ally.offhandId, ally.armorId, ally.helmetId, ally.bootsId
    ) {
        PaperDollAppearance.fromRemotePlayer(ally)
    }
    val hpFrac = (ally.hp.toFloat() / ally.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    Surface(
        color = StoneSurface.copy(alpha = 0.90f),
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(0.9.dp, Color(0xFF47D7FF).copy(alpha = 0.75f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onInspect)
            .testTag("battle_ally_${ally.playerId}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            LivePaperDollPreview(
                appearance = allyAppearance,
                contentDesc = "${ally.name} portrait",
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ObsidianVoid)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (ally.isAdmin) {
                            AdminGoldenCrownIcon(sizeDp = 11.dp)
                        }
                        Text(
                            text = "${ally.name} L${ally.level}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                            color = if (ally.isAdmin) TorchGold else Color(0xFF7BE8FF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "(${ally.x},${ally.y}) ${distanceTiles}t",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = TorchGold,
                        maxLines = 1
                    )
                }
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
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ObsidianVoid.copy(alpha = 0.85f))
                    .border(0.8.dp, TorchGold.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .clickable(onClick = onWalkTo)
                    .testTag("battle_ally_walk_${ally.playerId}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "Walk to ${ally.name}",
                    tint = TorchGold,
                    modifier = Modifier.size(13.dp)
                )
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
    if (msg.senderIsAdmin && msg.channel != ChatChannel.SYSTEM) {
        val channelTag = if (msg.channel == ChatChannel.WORLD) "[W] " else "[F] "
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = channelTag,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                color = tagColor,
                maxLines = 1
            )
            AdminGoldenCrownIcon(sizeDp = 11.dp)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TorchGold, fontWeight = FontWeight.ExtraBold)) {
                        append("${msg.senderName}: ")
                    }
                    withStyle(SpanStyle(color = ParchmentLight)) { append(msg.text) }
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                maxLines = 2,
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
                withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Bold)) { append(prefix) }
                withStyle(SpanStyle(color = if (msg.channel == ChatChannel.SYSTEM) TorchGold else ParchmentLight)) { append(msg.text) }
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
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
 * REGIONAL & FULL WORLD MAP PANEL CONTENT:
 * - On Floor 0: Painted parchment 1600x1200 continent map (`R.drawable.world_map_parchment`)
 *   with live pulsing player beacon, dashed auto-walk route, filterable Cities / Ferries / Dungeons / Allies pins,
 *   and tap-to-auto-walk via hierarchical regional routing.
 * - On Dungeon Floors (< 0): Cached crisp pixel dungeon overview with landmarks, foes, allies, and tap-to-A*-walk.
 */
@Composable
fun RegionalWorldMapContent(
    state: GameWorldState,
    remotePlayers: List<RemotePlayerState>,
    onWalkToTile: (Int, Int) -> Unit
) {
    EnhancedRegionalWorldMapContent(
        state = state,
        remotePlayers = remotePlayers,
        onWalkToTile = onWalkToTile
    )
}

/**
 * Top-center Boss HP Nameplate displayed when engaging or approaching a Dungeon Boss
 * (Broodmother Arachne, Ignis Lord of the Pit, Demon Lord, Elder Dragon).
 * Pulses a dodge alert whenever the boss is telegraphing a ground danger-zone special.
 */
@Composable
fun BossHpNameplateBar(
    boss: MonsterEntity,
    modifier: Modifier = Modifier
) {
    val hpFrac = (boss.hp.toFloat() / boss.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val isTelegraphing = boss.pendingAoeTiles.isNotEmpty()
    val (accentColor, barColor) = when (boss.type.specialKind) {
        BossSpecialKind.VENOM_NOVA -> Color(0xFF7BFF33) to Color(0xFF56D938)
        BossSpecialKind.QUARRY_QUAKE -> Color(0xFFB692FF) to Color(0xFF7D5CE6)
        BossSpecialKind.LABYRINTH_PYRE -> Color(0xFFFFC14D) to Color(0xFFD93B2B)
        else -> FireOrange to HealthCrimson
    }
    val infiniteTransition = rememberInfiniteTransition(label = "boss_telegraph_pulse")
    val warnAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360),
            repeatMode = RepeatMode.Reverse
        ),
        label = "boss_warn_alpha"
    )

    Surface(
        color = BasaltDark.copy(alpha = 0.92f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (isTelegraphing) 1.8.dp else 1.3.dp,
            color = if (isTelegraphing) accentColor.copy(alpha = warnAlpha) else TorchGold.copy(alpha = 0.8f)
        ),
        modifier = modifier
            .widthIn(min = 228.dp, max = 330.dp)
            .testTag("boss_hp_nameplate")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .background(accentColor.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                            .border(1.dp, accentColor.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "BOSS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = accentColor
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = boss.type.displayName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = ParchmentLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (boss.type.bossTitle.isNotEmpty()) {
                            Text(
                                text = boss.type.bossTitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                color = ParchmentMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${boss.hp} / ${boss.maxHp}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = ParchmentLight
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ObsidianVoid)
                    .border(0.8.dp, StoneBorderDark, RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(hpFrac)
                        .fillMaxHeight()
                        .background(barColor)
                )
            }

            if (isTelegraphing) {
                val warnText = when (boss.type.specialKind) {
                    BossSpecialKind.VENOM_NOVA -> "VENOM NOVA — DODGE DANGER ZONES!"
                    BossSpecialKind.QUARRY_QUAKE -> "QUARRY QUAKE — DODGE DANGER ZONES!"
                    BossSpecialKind.LABYRINTH_PYRE -> "LABYRINTH PYRE — DODGE DANGER ZONES!"
                    else -> "FLAME BURST — DODGE DANGER ZONES!"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor.copy(alpha = 0.18f * warnAlpha))
                        .border(0.8.dp, accentColor.copy(alpha = warnAlpha), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Boss Special Telegraph Warning",
                        tint = accentColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = warnText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

