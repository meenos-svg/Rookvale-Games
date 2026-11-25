package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.model.GameWorldState
import com.example.game.model.OreVeinKind
import com.example.game.model.normalizeRegionId
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.world.WorldBiomeBlueprint
import com.example.game.world.WorldFloor
import com.example.game.world.WorldMaps
import com.example.game.world.WorldPinCategory
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.StoneBorderDark
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.StoneSurface
import com.example.ui.theme.TargetRed
import com.example.ui.theme.TorchGold
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Immutable camera state for the Regional/World Map modal in world-tile coordinates.
 * Storing focus in tile coordinates guarantees exact symmetry between canvas rendering,
 * pinch/drag navigation, and tap-to-walk coordinate projection across any screen aspect ratio.
 */
data class WorldMapCameraState(
    val floorWidth: Int,
    val floorHeight: Int,
    val isOverland: Boolean,
    val centerX: Float,
    val centerY: Float,
    val mapZoom: Float
)

/**
 * Pure, deterministic viewport projection and zoom/pan math for the expandable World Map modal.
 */
object WorldMapViewportCamera {
    const val MIN_ZOOM = 1.0f
    const val MAX_ZOOM = 6.0f
    const val DEFAULT_OVERLAND_ZOOM = 3.5f
    const val DEFAULT_DUNGEON_ZOOM = 2.5f
    const val ZOOM_STEP = 0.75f
    const val CAMP_AND_LEVELS_MIN_ZOOM = 1.6f
    const val DETAIL_FADE_START_ZOOM = 2.2f
    const val DETAIL_FADE_FULL_ZOOM = 3.0f
    const val VEINS_AND_LANDMARKS_MIN_ZOOM = 2.5f

    /**
     * Zoom-aware World Map pin filter:
     * - Overview zoom (< 1.6x): shows major Empires, Cities, Coastal Ocean Ports, and Major Dungeons.
     * - Zoomed in (>= 1.6x): auto-reveals Forward Camps and localized dungeon/camp pins.
     */
    fun filterVisiblePins(
        pins: List<com.example.game.world.WorldMapPin>,
        mapZoom: Float,
        showCities: Boolean,
        showFerries: Boolean,
        showDungeons: Boolean
    ): List<com.example.game.world.WorldMapPin> {
        return pins.filter { pin ->
            val categoryEnabled = when (pin.category) {
                WorldPinCategory.EMPIRE, WorldPinCategory.CITY, WorldPinCategory.CAMP, WorldPinCategory.FORGE -> showCities
                WorldPinCategory.FERRY -> showFerries
                WorldPinCategory.DUNGEON -> showDungeons
            }
            categoryEnabled && mapZoom >= pin.minZoomToShow
        }
    }

    /**
     * Formats World Map pin labels so settlement/empire level ranges auto-reveal when zoomed in (>= 1.6x).
     */
    fun formatPinLabel(pin: com.example.game.world.WorldMapPin, mapZoom: Float): String {
        val base = pin.label.take(18)
        return if (mapZoom >= CAMP_AND_LEVELS_MIN_ZOOM && pin.levelRange.isNotBlank()) {
            "$base (${pin.levelRange})"
        } else {
            base
        }
    }

    // At 1.0f -> 1x (full 1600x1200 world); at 3.5f -> 6x (~267x200 tiles, ~100-133 tile radius); at 6.0f -> 36x (~44x33 tiles)
    private const val OVERLAND_MAX_MAGNIFICATION = 36.0f

    fun initialCenteredOnPlayer(
        floorWidth: Int,
        floorHeight: Int,
        isOverland: Boolean,
        playerX: Int,
        playerY: Int,
        initialZoom: Float = if (isOverland) DEFAULT_OVERLAND_ZOOM else DEFAULT_DUNGEON_ZOOM
    ): WorldMapCameraState {
        val clampedZoom = initialZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val (cx, cy) = clampCenter(
            centerX = playerX + 0.5f,
            centerY = playerY + 0.5f,
            floorWidth = floorWidth,
            floorHeight = floorHeight,
            mapZoom = clampedZoom,
            isOverland = isOverland
        )
        return WorldMapCameraState(
            floorWidth = floorWidth,
            floorHeight = floorHeight,
            isOverland = isOverland,
            centerX = cx,
            centerY = cy,
            mapZoom = clampedZoom
        )
    }

    fun effectiveMagnification(mapZoom: Float, isOverland: Boolean): Float {
        val z = mapZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        return if (isOverland) {
            val normalized = (z - MIN_ZOOM) / (MAX_ZOOM - MIN_ZOOM)
            OVERLAND_MAX_MAGNIFICATION.toDouble().pow(normalized.toDouble()).toFloat()
        } else {
            z
        }
    }

    fun visibleTileSpan(
        floorWidth: Int,
        floorHeight: Int,
        mapZoom: Float,
        isOverland: Boolean
    ): Pair<Float, Float> {
        val mag = effectiveMagnification(mapZoom, isOverland).coerceAtLeast(1f)
        val visW = (floorWidth.toFloat() / mag).coerceIn(12f, floorWidth.toFloat())
        val visH = (floorHeight.toFloat() / mag).coerceIn(12f, floorHeight.toFloat())
        return visW to visH
    }

    fun clampCenter(
        centerX: Float,
        centerY: Float,
        floorWidth: Int,
        floorHeight: Int,
        mapZoom: Float,
        isOverland: Boolean
    ): Pair<Float, Float> {
        val (visW, visH) = visibleTileSpan(floorWidth, floorHeight, mapZoom, isOverland)
        val halfW = visW * 0.5f
        val halfH = visH * 0.5f
        val clampedX = if (visW >= floorWidth) floorWidth * 0.5f else centerX.coerceIn(halfW, floorWidth - halfW)
        val clampedY = if (visH >= floorHeight) floorHeight * 0.5f else centerY.coerceIn(halfH, floorHeight - halfH)
        return clampedX to clampedY
    }

    fun viewportBounds(camera: WorldMapCameraState): FloatArray {
        val (visW, visH) = visibleTileSpan(camera.floorWidth, camera.floorHeight, camera.mapZoom, camera.isOverland)
        val left = (camera.centerX - visW * 0.5f).coerceIn(0f, (camera.floorWidth - visW).coerceAtLeast(0f))
        val top = (camera.centerY - visH * 0.5f).coerceIn(0f, (camera.floorHeight - visH).coerceAtLeast(0f))
        return floatArrayOf(left, top, left + visW, top + visH)
    }

    fun worldToScreen(
        worldX: Float,
        worldY: Float,
        camera: WorldMapCameraState,
        canvasWidth: Float,
        canvasHeight: Float
    ): Offset {
        val w = canvasWidth.coerceAtLeast(1f)
        val h = canvasHeight.coerceAtLeast(1f)
        val bounds = viewportBounds(camera)
        val visW = (bounds[2] - bounds[0]).coerceAtLeast(1f)
        val visH = (bounds[3] - bounds[1]).coerceAtLeast(1f)
        val sx = ((worldX - bounds[0]) / visW) * w
        val sy = ((worldY - bounds[1]) / visH) * h
        return Offset(sx, sy)
    }

    fun screenToWorldExact(
        screenX: Float,
        screenY: Float,
        camera: WorldMapCameraState,
        canvasWidth: Float,
        canvasHeight: Float
    ): Pair<Float, Float> {
        val w = canvasWidth.coerceAtLeast(1f)
        val h = canvasHeight.coerceAtLeast(1f)
        val bounds = viewportBounds(camera)
        val visW = (bounds[2] - bounds[0]).coerceAtLeast(1f)
        val visH = (bounds[3] - bounds[1]).coerceAtLeast(1f)
        val wx = (bounds[0] + (screenX / w) * visW).coerceIn(0f, camera.floorWidth.toFloat())
        val wy = (bounds[1] + (screenY / h) * visH).coerceIn(0f, camera.floorHeight.toFloat())
        return wx to wy
    }

    fun screenToWorldTile(
        screenX: Float,
        screenY: Float,
        camera: WorldMapCameraState,
        canvasWidth: Float,
        canvasHeight: Float
    ): Pair<Int, Int> {
        val (wx, wy) = screenToWorldExact(screenX, screenY, camera, canvasWidth, canvasHeight)
        val tx = floor(wx.toDouble()).toInt().coerceIn(0, (camera.floorWidth - 1).coerceAtLeast(0))
        val ty = floor(wy.toDouble()).toInt().coerceIn(0, (camera.floorHeight - 1).coerceAtLeast(0))
        return tx to ty
    }

    fun panByScreenDelta(
        camera: WorldMapCameraState,
        deltaScreenX: Float,
        deltaScreenY: Float,
        canvasWidth: Float,
        canvasHeight: Float
    ): WorldMapCameraState {
        val w = canvasWidth.coerceAtLeast(1f)
        val h = canvasHeight.coerceAtLeast(1f)
        val (visW, visH) = visibleTileSpan(camera.floorWidth, camera.floorHeight, camera.mapZoom, camera.isOverland)
        val deltaTileX = -(deltaScreenX / w) * visW
        val deltaTileY = -(deltaScreenY / h) * visH
        val (cx, cy) = clampCenter(
            centerX = camera.centerX + deltaTileX,
            centerY = camera.centerY + deltaTileY,
            floorWidth = camera.floorWidth,
            floorHeight = camera.floorHeight,
            mapZoom = camera.mapZoom,
            isOverland = camera.isOverland
        )
        return camera.copy(centerX = cx, centerY = cy)
    }

    fun stepZoom(camera: WorldMapCameraState, deltaZoom: Float): WorldMapCameraState {
        val targetZoom = (camera.mapZoom + deltaZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val (cx, cy) = clampCenter(
            centerX = camera.centerX,
            centerY = camera.centerY,
            floorWidth = camera.floorWidth,
            floorHeight = camera.floorHeight,
            mapZoom = targetZoom,
            isOverland = camera.isOverland
        )
        return camera.copy(centerX = cx, centerY = cy, mapZoom = targetZoom)
    }

    fun zoomAroundFocal(
        camera: WorldMapCameraState,
        targetZoom: Float,
        focalScreenX: Float,
        focalScreenY: Float,
        canvasWidth: Float,
        canvasHeight: Float
    ): WorldMapCameraState {
        val newZoom = targetZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val w = canvasWidth.coerceAtLeast(1f)
        val h = canvasHeight.coerceAtLeast(1f)
        val (anchorWorldX, anchorWorldY) = screenToWorldExact(focalScreenX, focalScreenY, camera, w, h)
        val (newVisW, newVisH) = visibleTileSpan(camera.floorWidth, camera.floorHeight, newZoom, camera.isOverland)
        val fracX = (focalScreenX / w).coerceIn(0f, 1f)
        val fracY = (focalScreenY / h).coerceIn(0f, 1f)
        val rawLeft = anchorWorldX - fracX * newVisW
        val rawTop = anchorWorldY - fracY * newVisH
        val (cx, cy) = clampCenter(
            centerX = rawLeft + newVisW * 0.5f,
            centerY = rawTop + newVisH * 0.5f,
            floorWidth = camera.floorWidth,
            floorHeight = camera.floorHeight,
            mapZoom = newZoom,
            isOverland = camera.isOverland
        )
        return camera.copy(centerX = cx, centerY = cy, mapZoom = newZoom)
    }

    fun recenterOnPlayer(
        camera: WorldMapCameraState,
        playerX: Int,
        playerY: Int
    ): WorldMapCameraState {
        val (cx, cy) = clampCenter(
            centerX = playerX + 0.5f,
            centerY = playerY + 0.5f,
            floorWidth = camera.floorWidth,
            floorHeight = camera.floorHeight,
            mapZoom = camera.mapZoom,
            isOverland = camera.isOverland
        )
        return camera.copy(centerX = cx, centerY = cy)
    }

    /**
     * Smooth crossfade factor for overland hybrid detail:
     * 0f at zoom <= 2.2f (painted parchment continent), smoothly ramping to 1f at zoom >= 3.0f (crisp live tiles).
     */
    fun localTileDetailAlpha(mapZoom: Float, isOverland: Boolean): Float {
        if (!isOverland) return 1f
        return ((mapZoom - DETAIL_FADE_START_ZOOM) / (DETAIL_FADE_FULL_ZOOM - DETAIL_FADE_START_ZOOM))
            .coerceIn(0f, 1f)
    }
}

private data class LocalTileWindowCache(
    val floorId: Int,
    val minX: Int,
    val minY: Int,
    val width: Int,
    val height: Int,
    val step: Int,
    val bitmap: ImageBitmap
) {
    fun covers(reqMinX: Int, reqMinY: Int, reqMaxX: Int, reqMaxY: Int, reqStep: Int): Boolean {
        return step == reqStep &&
            reqMinX >= minX &&
            reqMinY >= minY &&
            reqMaxX <= minX + width &&
            reqMaxY <= minY + height
    }
}

private fun buildLocalTileWindowBitmap(
    floorMap: WorldFloor,
    reqMinX: Int,
    reqMinY: Int,
    reqMaxX: Int,
    reqMaxY: Int,
    step: Int
): LocalTileWindowCache {
    val margin = 28 * step
    val startX = (reqMinX - margin).coerceIn(0, (floorMap.width - 1).coerceAtLeast(0))
    val startY = (reqMinY - margin).coerceIn(0, (floorMap.height - 1).coerceAtLeast(0))
    val endX = (reqMaxX + margin).coerceIn(startX + 1, floorMap.width)
    val endY = (reqMaxY + margin).coerceIn(startY + 1, floorMap.height)
    val spanW = (endX - startX).coerceAtLeast(1)
    val spanH = (endY - startY).coerceAtLeast(1)
    val bmpW = ((spanW + step - 1) / step).coerceIn(1, 512)
    val bmpH = ((spanH + step - 1) / step).coerceIn(1, 512)
    val pixels = IntArray(bmpW * bmpH)
    for (py in 0 until bmpH) {
        val wy = (startY + py * step).coerceAtMost(floorMap.height - 1)
        val rowOff = py * bmpW
        for (px in 0 until bmpW) {
            val wx = (startX + px * step).coerceAtMost(floorMap.width - 1)
            pixels[rowOff + px] = minimapTileColor(floorMap.tileAt(wx, wy)).toArgb()
        }
    }
    val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, bmpW, 0, 0, bmpW, bmpH)
    return LocalTileWindowCache(
        floorId = floorMap.floorId,
        minX = startX,
        minY = startY,
        width = bmpW * step,
        height = bmpH * step,
        step = step,
        bitmap = bmp.asImageBitmap()
    )
}

private fun oreVeinMarkerColor(kind: OreVeinKind): Color = when (kind) {
    OreVeinKind.COPPER -> Color(0xFFFF9442)
    OreVeinKind.TIN -> Color(0xFFD6E4F0)
    OreVeinKind.BOG_IRON -> Color(0xFF7CA7C9)
    OreVeinKind.MIRE_SILVER -> Color(0xFF66E8B8)
    OreVeinKind.PEAT_COAL -> Color(0xFF5D6A62)
    OreVeinKind.VESPERITE -> Color(0xFFC29DFF)
    OreVeinKind.GLACIER_QUARTZ -> Color(0xFF7AE8FF)
    OreVeinKind.FROST_IRON -> Color(0xFF8FB8E8)
    OreVeinKind.CINDERITE -> Color(0xFFFFC13B)
}

/**
 * Expandable Regional/World Map Modal Content:
 * - Opens zoomed directly onto the player's local neighborhood (3.5x on Floor 0, 2.5x in dungeons).
 * - Hybrid overland rendering: painted parchment continent when zoomed out, smoothly crossfading into
 *   crisp live terrain tiles (roads, plazas, buildings, dungeon entrances, ore veins) when zoomed in.
 * - Clean +, -, and Center-on-Player controls matching the top-left minimap aesthetic.
 * - Smooth pinch-to-zoom, one-finger drag panning, zoom-aware non-overlapping labels, Veins toggle,
 *   and mathematically exact tap-to-walk coordinate conversion while keeping the modal open.
 */
@Composable
fun EnhancedRegionalWorldMapContent(
    state: GameWorldState,
    remotePlayers: List<RemotePlayerState>,
    onWalkToTile: (Int, Int) -> Unit
) {
    val floorMap = remember(state.floor) { WorldMaps.getFloor(state.floor) }
    val isOverland = state.floor == 0
    val currentRegion = remember(state.floor, state.playerX, state.playerY) {
        if (isOverland) WorldMaps.regionForGlobal(state.playerX, state.playerY) else null
    }
    val floorLandmarks = remember(state.floor) { WorldMaps.landmarks.filter { it.floor == state.floor } }
    val localRegionId = remember(state.regionId) { normalizeRegionId(state.regionId) }
    val floorRemotePlayers = remember(remotePlayers, state.floor, localRegionId, state.playerX, state.playerY) {
        remotePlayers
            .filter {
                !it.isSpectator && it.floor == state.floor && (isOverland || normalizeRegionId(it.regionId) == localRegionId)
            }
            .sortedBy { max(abs(it.x - state.playerX), abs(it.y - state.playerY)) }
    }
    val floorOreNodes = remember(state.oreNodes, state.floor) {
        state.oreNodes.values.filter { it.floor == state.floor }
    }

    var showCities by rememberSaveable { mutableStateOf(true) }
    var showFerries by rememberSaveable { mutableStateOf(true) }
    var showDungeons by rememberSaveable { mutableStateOf(true) }
    var showVeins by rememberSaveable { mutableStateOf(true) }
    var showAllies by rememberSaveable { mutableStateOf(true) }

    // Camera state initialized zoomed directly into the player's surrounding area on open / floor switch
    var camera by remember(state.floor) {
        mutableStateOf(
            WorldMapViewportCamera.initialCenteredOnPlayer(
                floorWidth = floorMap.width,
                floorHeight = floorMap.height,
                isOverland = isOverland,
                playerX = state.playerX,
                playerY = state.playerY
            )
        )
    }
    var followPlayer by remember(state.floor) { mutableStateOf(true) }

    // Smoothly track the player as they walk while followPlayer is enabled
    LaunchedEffect(state.playerX, state.playerY, state.floor, followPlayer) {
        if (followPlayer) {
            camera = WorldMapViewportCamera.recenterOnPlayer(camera, state.playerX, state.playerY)
        }
    }

    val visiblePins = remember(state.floor, camera.mapZoom, showCities, showFerries, showDungeons) {
        if (!isOverland) emptyList()
        else WorldMapViewportCamera.filterVisiblePins(
            pins = WorldBiomeBlueprint.worldMapPins,
            mapZoom = camera.mapZoom,
            showCities = showCities,
            showFerries = showFerries,
            showDungeons = showDungeons
        )
    }

    val parchmentBitmap: ImageBitmap? = if (isOverland) {
        ImageBitmap.imageResource(id = com.example.R.drawable.world_map_parchment)
    } else null

    // Full dungeon bitmap cache for compact underground floors (< 0)
    val cachedDungeonBitmap: ImageBitmap? = remember(state.floor) {
        if (isOverland) return@remember null
        val w = floorMap.width
        val h = floorMap.height
        val pixels = IntArray(w * h)
        for (ty in 0 until h) {
            val rowOff = ty * w
            for (tx in 0 until w) {
                pixels[rowOff + tx] = minimapTileColor(floorMap.tileAt(tx, ty)).toArgb()
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        bmp.asImageBitmap()
    }

    // Buffered live local terrain tile window for Floor 0 when zoomed in (detailAlpha > 0)
    var localWindowCache by remember(state.floor) { mutableStateOf<LocalTileWindowCache?>(null) }
    val detailAlpha = WorldMapViewportCamera.localTileDetailAlpha(camera.mapZoom, isOverland)
    val activeLocalWindow = if (isOverland && detailAlpha > 0.01f) {
        val bounds = WorldMapViewportCamera.viewportBounds(camera)
        val reqMinX = floor(bounds[0].toDouble()).toInt().coerceIn(0, floorMap.width - 1)
        val reqMinY = floor(bounds[1].toDouble()).toInt().coerceIn(0, floorMap.height - 1)
        val reqMaxX = ceil(bounds[2].toDouble()).toInt().coerceIn(reqMinX + 1, floorMap.width)
        val reqMaxY = ceil(bounds[3].toDouble()).toInt().coerceIn(reqMinY + 1, floorMap.height)
        val spanMax = max(reqMaxX - reqMinX, reqMaxY - reqMinY)
        val step = if (spanMax > 320) 2 else 1
        val existing = localWindowCache
        if (existing != null && existing.floorId == state.floor && existing.covers(reqMinX, reqMinY, reqMaxX, reqMaxY, step)) {
            existing
        } else {
            val rebuilt = buildLocalTileWindowBitmap(floorMap, reqMinX, reqMinY, reqMaxX, reqMaxY, step)
            localWindowCache = rebuilt
            rebuilt
        }
    } else null

    val pulseTransition = rememberInfiniteTransition(label = "world_map_pulse")
    val beaconPulse by pulseTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_pulse"
    )

    val allyLabelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
            isFakeBoldText = true
            textSize = 18f
            color = android.graphics.Color.parseColor("#8CF0FF")
        }
    }
    val landmarkLabelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.SANS_SERIF
            isFakeBoldText = true
            textSize = 16f
            color = android.graphics.Color.parseColor("#F5E6C4")
        }
    }
    val mapTooltipBgPaint = remember {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = android.graphics.Color.argb(212, 13, 17, 23)
        }
    }
    val mapTooltipBorderPaint = remember {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            color = android.graphics.Color.parseColor("#E8B949")
        }
    }
    val landmarkBorderPaint = remember {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = android.graphics.Color.argb(170, 212, 175, 92)
        }
    }
    val tooltipRect = remember { RectF() }

    // Latest refs for pointerInput so gesture loop never restarts mid-drag or mid-pinch
    val latestCamera by rememberUpdatedState(camera)
    val latestRemotePlayers by rememberUpdatedState(floorRemotePlayers)
    val latestPins by rememberUpdatedState(visiblePins)
    val latestShowAllies by rememberUpdatedState(showAllies)
    val latestOnWalkToTile by rememberUpdatedState(onWalkToTile)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(318.dp)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Filter chips bar on Floor 0 (including Veins chip!)
        if (isOverland) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WorldMapChip(
                        label = "Cities",
                        selected = showCities,
                        accent = TorchGold,
                        onClick = { showCities = !showCities },
                        testTag = "world_map_filter_cities"
                    )
                    WorldMapChip(
                        label = "Ferries",
                        selected = showFerries,
                        accent = Color(0xFF47B8FF),
                        onClick = { showFerries = !showFerries },
                        testTag = "world_map_filter_ferries"
                    )
                    WorldMapChip(
                        label = "Dungeons",
                        selected = showDungeons,
                        accent = TargetRed,
                        onClick = { showDungeons = !showDungeons },
                        testTag = "world_map_filter_dungeons"
                    )
                    WorldMapChip(
                        label = "Veins",
                        selected = showVeins,
                        accent = Color(0xFFFF9E44),
                        onClick = { showVeins = !showVeins },
                        testTag = "world_map_filter_veins"
                    )
                    WorldMapChip(
                        label = "Allies (${floorRemotePlayers.size})",
                        selected = showAllies,
                        accent = Color(0xFF7BE8FF),
                        onClick = { showAllies = !showAllies },
                        testTag = "world_map_filter_allies"
                    )
                }
                Text(
                    text = "${currentRegion?.displayName ?: floorMap.name} • (${state.playerX},${state.playerY})",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    color = TorchGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Interactive Zoomable & Pannable World Map Viewport
            Surface(
                color = ObsidianVoid,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.4.dp, StoneBorderLight),
                modifier = Modifier
                    .weight(1.52f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val mapContainerModifier = if (isOverland) {
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(4f / 3f, matchHeightConstraintsFirst = true)
                            .clip(RoundedCornerShape(6.dp))
                    } else {
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                    }

                    Box(modifier = mapContainerModifier) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(state.floor) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val downPos = down.position
                                        var movedBeyondSlop = false
                                        var multiTouchUsed = false
                                        val touchSlopPx = 10f

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val activePointers = event.changes.filter { it.pressed }
                                            if (activePointers.isEmpty()) break

                                            val w = size.width.coerceAtLeast(1).toFloat()
                                            val h = size.height.coerceAtLeast(1).toFloat()

                                            if (activePointers.size >= 2) {
                                                multiTouchUsed = true
                                                movedBeyondSlop = true
                                                val zoomChange = event.calculateZoom()
                                                val panChange = event.calculatePan()
                                                val centroid = event.calculateCentroid(useCurrent = true)
                                                var nextCam = latestCamera
                                                if (abs(zoomChange - 1f) > 0.002f) {
                                                    // Map multiplicative pinch factor smoothly into 1.0..6.0 zoom scale
                                                    val zoomDelta = (zoomChange - 1f) * 2.4f
                                                    nextCam = WorldMapViewportCamera.zoomAroundFocal(
                                                        camera = nextCam,
                                                        targetZoom = nextCam.mapZoom + zoomDelta,
                                                        focalScreenX = centroid.x,
                                                        focalScreenY = centroid.y,
                                                        canvasWidth = w,
                                                        canvasHeight = h
                                                    )
                                                }
                                                if (panChange != Offset.Zero) {
                                                    nextCam = WorldMapViewportCamera.panByScreenDelta(
                                                        camera = nextCam,
                                                        deltaScreenX = panChange.x,
                                                        deltaScreenY = panChange.y,
                                                        canvasWidth = w,
                                                        canvasHeight = h
                                                    )
                                                    followPlayer = false
                                                }
                                                camera = nextCam
                                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                            } else {
                                                val change = activePointers.first()
                                                val distFromDown = hypot(change.position.x - downPos.x, change.position.y - downPos.y)
                                                if (!movedBeyondSlop && distFromDown > touchSlopPx) {
                                                    movedBeyondSlop = true
                                                }
                                                if (movedBeyondSlop && !multiTouchUsed) {
                                                    val pan = event.calculatePan()
                                                    if (pan != Offset.Zero) {
                                                        followPlayer = false
                                                        camera = WorldMapViewportCamera.panByScreenDelta(
                                                            camera = latestCamera,
                                                            deltaScreenX = pan.x,
                                                            deltaScreenY = pan.y,
                                                            canvasWidth = w,
                                                            canvasHeight = h
                                                        )
                                                        change.consume()
                                                    }
                                                }
                                            }
                                        }

                                        // Clean single tap (no drag/pinch) -> accurate tap-to-walk at active zoom/pan!
                                        if (!movedBeyondSlop && !multiTouchUsed) {
                                            val w = size.width.coerceAtLeast(1).toFloat()
                                            val h = size.height.coerceAtLeast(1).toFloat()
                                            val cam = latestCamera

                                            // 1. Snap to online player marker if tapped nearby on screen
                                            val tappedAlly = if (latestShowAllies || !cam.isOverland) {
                                                latestRemotePlayers.minByOrNull { rp ->
                                                    val pos = WorldMapViewportCamera.worldToScreen(rp.x + 0.5f, rp.y + 0.5f, cam, w, h)
                                                    hypot(downPos.x - pos.x, downPos.y - pos.y)
                                                }?.takeIf { rp ->
                                                    val pos = WorldMapViewportCamera.worldToScreen(rp.x + 0.5f, rp.y + 0.5f, cam, w, h)
                                                    hypot(downPos.x - pos.x, downPos.y - pos.y) <= 24f
                                                }
                                            } else null
                                            if (tappedAlly != null) {
                                                latestOnWalkToTile(tappedAlly.x, tappedAlly.y)
                                                return@awaitEachGesture
                                            }

                                            // 2. Snap to City / Ferry / Dungeon pin if tapped nearby on screen
                                            val tappedPin = latestPins.minByOrNull { pin ->
                                                val pos = WorldMapViewportCamera.worldToScreen(pin.x + 0.5f, pin.y + 0.5f, cam, w, h)
                                                hypot(downPos.x - pos.x, downPos.y - pos.y)
                                            }?.takeIf { pin ->
                                                val pos = WorldMapViewportCamera.worldToScreen(pin.x + 0.5f, pin.y + 0.5f, cam, w, h)
                                                hypot(downPos.x - pos.x, downPos.y - pos.y) <= 22f
                                            }
                                            if (tappedPin != null) {
                                                latestOnWalkToTile(tappedPin.x, tappedPin.y)
                                                return@awaitEachGesture
                                            }

                                            // 3. Exact inverse projection from screen tap to world (tx, ty)
                                            val (tx, ty) = WorldMapViewportCamera.screenToWorldTile(downPos.x, downPos.y, cam, w, h)
                                            latestOnWalkToTile(tx, ty)
                                        }
                                    }
                                }
                                .testTag("regional_world_map_canvas")
                        ) {
                            val w = size.width.coerceAtLeast(1f)
                            val h = size.height.coerceAtLeast(1f)
                            val dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
                            val bounds = WorldMapViewportCamera.viewportBounds(camera)
                            val viewLeft = bounds[0]
                            val viewTop = bounds[1]
                            val viewRight = bounds[2]
                            val viewBottom = bounds[3]
                            val visW = (viewRight - viewLeft).coerceAtLeast(1f)
                            val visH = (viewBottom - viewTop).coerceAtLeast(1f)
                            val pxPerTile = w / visW

                            // LAYER 1: Cropped Painted Parchment Continent (Floor 0 when detailAlpha < 1f)
                            if (parchmentBitmap != null && detailAlpha < 0.99f) {
                                val srcX = ((viewLeft / floorMap.width.toFloat()) * parchmentBitmap.width).toInt()
                                    .coerceIn(0, (parchmentBitmap.width - 1).coerceAtLeast(0))
                                val srcY = ((viewTop / floorMap.height.toFloat()) * parchmentBitmap.height).toInt()
                                    .coerceIn(0, (parchmentBitmap.height - 1).coerceAtLeast(0))
                                val srcW = ((visW / floorMap.width.toFloat()) * parchmentBitmap.width).roundToInt()
                                    .coerceIn(1, (parchmentBitmap.width - srcX).coerceAtLeast(1))
                                val srcH = ((visH / floorMap.height.toFloat()) * parchmentBitmap.height).roundToInt()
                                    .coerceIn(1, (parchmentBitmap.height - srcY).coerceAtLeast(1))
                                drawImage(
                                    image = parchmentBitmap,
                                    srcOffset = IntOffset(srcX, srcY),
                                    srcSize = IntSize(srcW, srcH),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = dstSize,
                                    filterQuality = FilterQuality.Low
                                )
                            }

                            // LAYER 2: Crisp Live Terrain Tile Window (Floor 0 close-zoom hybrid) or Dungeon Bitmap
                            if (activeLocalWindow != null && detailAlpha > 0.01f) {
                                val srcLeftF = (viewLeft - activeLocalWindow.minX) / activeLocalWindow.step.toFloat()
                                val srcTopF = (viewTop - activeLocalWindow.minY) / activeLocalWindow.step.toFloat()
                                val srcWF = visW / activeLocalWindow.step.toFloat()
                                val srcHF = visH / activeLocalWindow.step.toFloat()
                                val srcX = srcLeftF.toInt().coerceIn(0, (activeLocalWindow.bitmap.width - 1).coerceAtLeast(0))
                                val srcY = srcTopF.toInt().coerceIn(0, (activeLocalWindow.bitmap.height - 1).coerceAtLeast(0))
                                val srcW = srcWF.roundToInt().coerceIn(1, (activeLocalWindow.bitmap.width - srcX).coerceAtLeast(1))
                                val srcH = srcHF.roundToInt().coerceIn(1, (activeLocalWindow.bitmap.height - srcY).coerceAtLeast(1))
                                drawImage(
                                    image = activeLocalWindow.bitmap,
                                    srcOffset = IntOffset(srcX, srcY),
                                    srcSize = IntSize(srcW, srcH),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = dstSize,
                                    alpha = detailAlpha,
                                    filterQuality = FilterQuality.None
                                )
                            } else if (cachedDungeonBitmap != null) {
                                val srcX = viewLeft.toInt().coerceIn(0, (cachedDungeonBitmap.width - 1).coerceAtLeast(0))
                                val srcY = viewTop.toInt().coerceIn(0, (cachedDungeonBitmap.height - 1).coerceAtLeast(0))
                                val srcW = visW.roundToInt().coerceIn(1, (cachedDungeonBitmap.width - srcX).coerceAtLeast(1))
                                val srcH = visH.roundToInt().coerceIn(1, (cachedDungeonBitmap.height - srcY).coerceAtLeast(1))
                                drawImage(
                                    image = cachedDungeonBitmap,
                                    srcOffset = IntOffset(srcX, srcY),
                                    srcSize = IntSize(srcW, srcH),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = dstSize,
                                    filterQuality = FilterQuality.None
                                )
                            }

                            // LAYER 3: Subtle 4x3 400x400 region grid lines on Floor 0
                            if (isOverland) {
                                val gridAlpha = (0.28f - detailAlpha * 0.14f).coerceAtLeast(0.10f)
                                val gridCol = Color(0xFFF2D68A).copy(alpha = gridAlpha)
                                for (c in 1..3) {
                                    val gx = WorldMapViewportCamera.worldToScreen(c * 400f, viewTop, camera, w, h).x
                                    if (gx in -2f..(w + 2f)) {
                                        drawLine(gridCol, Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                                    }
                                }
                                for (r in 1..2) {
                                    val gy = WorldMapViewportCamera.worldToScreen(viewLeft, r * 400f, camera, w, h).y
                                    if (gy in -2f..(h + 2f)) {
                                        drawLine(gridCol, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                                    }
                                }
                            }

                            // LAYER 4: Mining Ore Veins (auto-revealed at close zoom >= 2.5f when Veins chip is on, or in dungeons)
                            val showLocalVeins = showVeins && (!isOverland || camera.mapZoom >= WorldMapViewportCamera.VEINS_AND_LANDMARKS_MIN_ZOOM)
                            if (showLocalVeins) {
                                val veinRadius = (2.4f + (camera.mapZoom - 2.5f).coerceAtLeast(0f) * 0.65f).coerceIn(2.4f, 5.2f)
                                floorOreNodes.forEach { ore ->
                                    if (ore.x + 1f < viewLeft || ore.x - 1f > viewRight || ore.y + 1f < viewTop || ore.y - 1f > viewBottom) {
                                        return@forEach
                                    }
                                    val pos = WorldMapViewportCamera.worldToScreen(ore.x + 0.5f, ore.y + 0.5f, camera, w, h)
                                    val veinColor = oreVeinMarkerColor(ore.kind)
                                    val active = ore.chargesLeft > 0
                                    val diamond = Path().apply {
                                        moveTo(pos.x, pos.y - veinRadius)
                                        lineTo(pos.x + veinRadius, pos.y)
                                        lineTo(pos.x, pos.y + veinRadius)
                                        lineTo(pos.x - veinRadius, pos.y)
                                        close()
                                    }
                                    if (active) {
                                        drawPath(diamond, Color.Black.copy(alpha = 0.85f), style = Stroke(width = 2.2f))
                                        drawPath(diamond, veinColor)
                                    } else {
                                        drawPath(diamond, veinColor.copy(alpha = 0.45f), style = Stroke(width = 1.2f))
                                    }
                                }
                            }

                            // LAYER 5: Active Auto-Walk Route (dashed golden line + waypoint goal ring)
                            if (state.walkPath.isNotEmpty() || state.longRangeWaypoints.isNotEmpty() || state.longRangeGoal != null) {
                                val routePoints = buildList {
                                    add(state.playerX to state.playerY)
                                    if (state.walkPath.isNotEmpty()) add(state.walkPath.last())
                                    addAll(state.longRangeWaypoints)
                                    state.longRangeGoal?.let { add(it) }
                                }
                                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 5f), 0f)
                                val routeStroke = (2.2f + (camera.mapZoom - 1f) * 0.25f).coerceIn(2.2f, 3.6f)
                                for (i in 0 until routePoints.lastIndex) {
                                    val a = routePoints[i]
                                    val b = routePoints[i + 1]
                                    val startPos = WorldMapViewportCamera.worldToScreen(a.first + 0.5f, a.second + 0.5f, camera, w, h)
                                    val endPos = WorldMapViewportCamera.worldToScreen(b.first + 0.5f, b.second + 0.5f, camera, w, h)
                                    drawLine(
                                        color = TorchGold,
                                        start = startPos,
                                        end = endPos,
                                        strokeWidth = routeStroke,
                                        pathEffect = dashEffect
                                    )
                                }
                                val goalPt = state.longRangeGoal ?: state.walkPath.lastOrNull()
                                if (goalPt != null) {
                                    val gPos = WorldMapViewportCamera.worldToScreen(goalPt.first + 0.5f, goalPt.second + 0.5f, camera, w, h)
                                    val goalR = (4.2f + (camera.mapZoom - 1f) * 0.35f).coerceIn(4.2f, 6.2f)
                                    drawCircle(TorchGold.copy(alpha = 0.32f), radius = goalR * 2.0f, center = gPos)
                                    drawCircle(Color.Black, radius = goalR + 1.2f, center = gPos)
                                    drawCircle(TorchGold, radius = goalR, center = gPos)
                                }
                            }

                            // LAYER 6: Dungeon Floor Monsters
                            if (!isOverland) {
                                val foeR = (pxPerTile * 0.42f).coerceIn(2.4f, 6.5f)
                                state.monsters.forEach { m ->
                                    if (m.floor == state.floor) {
                                        val mPos = WorldMapViewportCamera.worldToScreen(m.x + 0.5f, m.y + 0.5f, camera, w, h)
                                        if (mPos.x in -10f..(w + 10f) && mPos.y in -10f..(h + 10f)) {
                                            drawCircle(Color.Black.copy(alpha = 0.7f), radius = foeR + 1f, center = mPos)
                                            drawCircle(TargetRed, radius = foeR, center = mPos)
                                        }
                                    }
                                }
                            }

                            // Track screen-space label boxes so labels NEVER overlap at any zoom level
                            val placedLabels = mutableListOf<RectF>()
                            fun tryDrawBadgeLabel(
                                text: String,
                                anchorX: Float,
                                anchorY: Float,
                                textPaint: Paint,
                                borderPaint: Paint,
                                padH: Float = 5f,
                                boxH: Float = 19f
                            ) {
                                val tw = textPaint.measureText(text)
                                val boxW = tw + padH * 2f
                                val cx = anchorX.coerceIn(boxW * 0.5f + 3f, (w - boxW * 0.5f - 3f).coerceAtLeast(boxW * 0.5f + 3f))
                                val bottomY = (anchorY - 7f).coerceIn(boxH + 3f, (h - 3f).coerceAtLeast(boxH + 3f))
                                val candidate = RectF(cx - boxW * 0.5f, bottomY - boxH, cx + boxW * 0.5f, bottomY)
                                if (placedLabels.any { RectF.intersects(it, candidate) }) return
                                placedLabels += candidate
                                tooltipRect.set(candidate)
                                drawContext.canvas.nativeCanvas.apply {
                                    drawRoundRect(tooltipRect, 4.5f, 4.5f, mapTooltipBgPaint)
                                    drawRoundRect(tooltipRect, 4.5f, 4.5f, borderPaint)
                                    drawText(text, cx, bottomY - 4.5f, textPaint)
                                }
                            }

                            // LAYER 7: Zoom-Scaled City / Ferry / Dungeon Pins + Non-Overlapping Labels
                            val zoomFactor = ((camera.mapZoom - 1f) / 5f).coerceIn(0f, 1f)
                            landmarkLabelPaint.textSize = (13.5f + zoomFactor * 4.5f).coerceIn(13.5f, 18f)
                            visiblePins.forEach { pin ->
                                val pos = WorldMapViewportCamera.worldToScreen(pin.x + 0.5f, pin.y + 0.5f, camera, w, h)
                                if (pos.x !in -16f..(w + 16f) || pos.y !in -16f..(h + 16f)) return@forEach
                                val pinColor = when (pin.category) {
                                    WorldPinCategory.EMPIRE -> Color(0xFFFFD65A)
                                    WorldPinCategory.CITY -> TorchGold
                                    WorldPinCategory.CAMP -> Color(0xFFFF9843)
                                    WorldPinCategory.FORGE -> Color(0xFFFF7A33)
                                    WorldPinCategory.FERRY -> Color(0xFF47B8FF)
                                    WorldPinCategory.DUNGEON -> TargetRed
                                }
                                val baseRadius = when (pin.category) {
                                    WorldPinCategory.EMPIRE -> 5.8f
                                    WorldPinCategory.CITY -> 5.0f
                                    WorldPinCategory.CAMP -> 4.3f
                                    WorldPinCategory.FORGE -> 5.2f
                                    WorldPinCategory.FERRY -> 4.2f
                                    WorldPinCategory.DUNGEON -> 4.0f
                                }
                                val pinRadius = (baseRadius + zoomFactor * 2.2f).coerceIn(4.0f, 8.2f)
                                if (pin.category == WorldPinCategory.FORGE) {
                                    // Crisp anvil/forge badge icon for Biome Crafting Stalls
                                    drawCircle(Color.Black.copy(alpha = 0.88f), radius = pinRadius + 2.0f, center = pos)
                                    drawCircle(Color(0xFF2B1A12), radius = pinRadius + 0.6f, center = pos)
                                    drawCircle(pinColor, radius = pinRadius + 0.6f, center = pos, style = Stroke(width = 1.6f))
                                    val r = pinRadius * 0.56f
                                    drawLine(Color(0xFFFFD68A), Offset(pos.x - r, pos.y - r * 0.25f), Offset(pos.x + r, pos.y - r * 0.25f), strokeWidth = 2.4f)
                                    drawLine(Color(0xFFFFB24C), Offset(pos.x, pos.y - r * 0.25f), Offset(pos.x, pos.y + r * 0.65f), strokeWidth = 2.0f)
                                    drawLine(Color(0xFFFFB24C), Offset(pos.x - r * 0.65f, pos.y + r * 0.65f), Offset(pos.x + r * 0.65f, pos.y + r * 0.65f), strokeWidth = 2.0f)
                                } else {
                                    drawCircle(Color.Black.copy(alpha = 0.86f), radius = pinRadius + 1.8f, center = pos)
                                    drawCircle(pinColor, radius = pinRadius, center = pos)
                                    if (pin.category == WorldPinCategory.EMPIRE || pin.category == WorldPinCategory.CITY) {
                                        drawCircle(Color.White.copy(alpha = 0.90f), radius = (pinRadius * 0.38f).coerceAtLeast(1.8f), center = pos)
                                    }
                                }
                                val showPinLabel = when {
                                    pin.category == WorldPinCategory.EMPIRE -> true
                                    (pin.category == WorldPinCategory.CITY || pin.category == WorldPinCategory.CAMP || pin.category == WorldPinCategory.FORGE) &&
                                        camera.mapZoom >= WorldMapViewportCamera.CAMP_AND_LEVELS_MIN_ZOOM -> true
                                    camera.mapZoom >= 2.2f -> true
                                    else -> false
                                }
                                if (showPinLabel) {
                                    val labelText = if (pin.category == WorldPinCategory.FORGE) {
                                        "⚒ ${pin.label.take(22)}"
                                    } else {
                                        WorldMapViewportCamera.formatPinLabel(pin, camera.mapZoom)
                                    }
                                    tryDrawBadgeLabel(
                                        text = labelText,
                                        anchorX = pos.x,
                                        anchorY = pos.y - pinRadius * 0.4f,
                                        textPaint = landmarkLabelPaint,
                                        borderPaint = landmarkBorderPaint,
                                        boxH = 18f
                                    )
                                }
                            }

                            // LAYER 8: Local District Landmarks (auto-revealed at close zoom >= 2.5f on Floor 0, or on dungeons)
                            if (!isOverland || camera.mapZoom >= WorldMapViewportCamera.VEINS_AND_LANDMARKS_MIN_ZOOM) {
                                floorLandmarks.forEach { lm ->
                                    if (lm.x < viewLeft || lm.x > viewRight || lm.y < viewTop || lm.y > viewBottom) return@forEach
                                    val pos = WorldMapViewportCamera.worldToScreen(lm.x + 0.5f, lm.y + 0.5f, camera, w, h)
                                    val dotR = (2.6f + zoomFactor * 1.4f).coerceIn(2.6f, 4.4f)
                                    drawCircle(Color.Black.copy(alpha = 0.8f), radius = dotR + 1.2f, center = pos)
                                    drawCircle(TorchGold.copy(alpha = 0.9f), radius = dotR, center = pos)
                                    if (camera.mapZoom >= 3.0f || !isOverland) {
                                        val compactLabel = lm.label
                                            .replace("TEMPLE & SANCTUARY PLAZA", "Temple Plaza")
                                            .replace("ROOKVALE ROYAL HARBOR FERRY", "Royal Harbor")
                                            .replace("ALARIC'S MARKET QUARTER", "Market Quarter")
                                            .replace("RUNECRAFTING ACADEMY", "Rune Academy")
                                            .replace("CRAFT & FLETCHING WARD", "Craft Ward")
                                            .replace("TOWN SQUARE FOUNTAIN", "Town Square")
                                            .replace("TOWN DEPOT HALL", "Depot Hall")
                                            .replace("CITY SEWERS (FLOOR -1)", "City Sewers")
                                            .take(18)
                                        tryDrawBadgeLabel(
                                            text = compactLabel,
                                            anchorX = pos.x,
                                            anchorY = pos.y,
                                            textPaint = landmarkLabelPaint,
                                            borderPaint = landmarkBorderPaint,
                                            boxH = 17f
                                        )
                                    }
                                }
                            }

                            // LAYER 9: Online Allies (with Golden Crown for Admin!)
                            if (showAllies || !isOverland) {
                                val allyR = (3.4f + zoomFactor * 2.0f).coerceIn(3.4f, 6.0f)
                                allyLabelPaint.textSize = (15f + zoomFactor * 3.5f).coerceIn(15f, 18.5f)
                                floorRemotePlayers.forEach { rp ->
                                    val pos = WorldMapViewportCamera.worldToScreen(rp.x + 0.5f, rp.y + 0.5f, camera, w, h)
                                    if (pos.x !in -16f..(w + 16f) || pos.y !in -16f..(h + 16f)) return@forEach
                                    drawCircle(Color.Black, radius = allyR + 2.4f, center = pos)
                                    drawCircle(TorchGold, radius = allyR + 1.1f, center = pos)
                                    drawCircle(Color(0xFF47D7FF), radius = allyR, center = pos)
                                    val label = if (rp.isAdmin) "★ ${rp.name.take(10)}" else rp.name.take(12)
                                    tryDrawBadgeLabel(
                                        text = label,
                                        anchorX = pos.x,
                                        anchorY = pos.y - allyR * 0.5f,
                                        textPaint = allyLabelPaint,
                                        borderPaint = mapTooltipBorderPaint,
                                        boxH = 20f
                                    )
                                }
                            }

                            // LAYER 10: Pulsing Local Player Beacon
                            val pPos = WorldMapViewportCamera.worldToScreen(state.playerX + 0.5f, state.playerY + 0.5f, camera, w, h)
                            val beaconR = (4.0f + zoomFactor * 1.6f).coerceIn(4.0f, 6.2f)
                            drawCircle(TorchGold.copy(alpha = 0.34f), radius = beaconR * 2.0f * beaconPulse, center = pPos)
                            drawCircle(Color.Black, radius = beaconR + 1.8f, center = pPos)
                            drawCircle(Color.White, radius = beaconR, center = pPos)
                        }

                        // TOP-LEFT VIEWPORT STATUS PILL (Floor + Detail Mode)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(5.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(ObsidianVoid.copy(alpha = 0.84f))
                                .border(1.dp, StoneBorderLight.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("world_map_zoom_badge")
                        ) {
                            val modeLabel = when {
                                !isOverland -> "F${state.floor}"
                                camera.mapZoom >= WorldMapViewportCamera.DETAIL_FADE_FULL_ZOOM -> "Local"
                                camera.mapZoom <= WorldMapViewportCamera.DETAIL_FADE_START_ZOOM -> "World"
                                else -> "Region"
                            }
                            Text(
                                text = String.format(Locale.US, "%s • %.1fx", modeLabel, camera.mapZoom),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = TorchGold
                            )
                        }

                        // BOTTOM-RIGHT MAP CONTROLS: Center on Player, +, - (styled consistently with top-left minimap)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            WorldMapControlButton(
                                icon = Icons.Default.MyLocation,
                                contentDesc = "Center Map on Player",
                                active = followPlayer,
                                enabled = true,
                                onClick = {
                                    followPlayer = true
                                    camera = WorldMapViewportCamera.recenterOnPlayer(camera, state.playerX, state.playerY)
                                },
                                testTag = "world_map_recenter_button"
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                WorldMapControlButton(
                                    icon = Icons.Default.Remove,
                                    contentDesc = "Zoom Out World Map",
                                    active = false,
                                    enabled = camera.mapZoom > WorldMapViewportCamera.MIN_ZOOM + 0.01f,
                                    onClick = {
                                        camera = WorldMapViewportCamera.stepZoom(camera, -WorldMapViewportCamera.ZOOM_STEP)
                                    },
                                    testTag = "world_map_zoom_out"
                                )
                                WorldMapControlButton(
                                    icon = Icons.Default.Add,
                                    contentDesc = "Zoom In World Map",
                                    active = false,
                                    enabled = camera.mapZoom < WorldMapViewportCamera.MAX_ZOOM - 0.01f,
                                    onClick = {
                                        camera = WorldMapViewportCamera.stepZoom(camera, WorldMapViewportCamera.ZOOM_STEP)
                                    },
                                    testTag = "world_map_zoom_in"
                                )
                            }
                        }
                    }
                }
            }

            // Right Sidebar: Online Allies + Cities / Harbors / Dungeons Quick Auto-Walk List
            Column(
                modifier = Modifier
                    .weight(0.92f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = if (isOverland) {
                        currentRegion?.displayName ?: "Rookvale Realm"
                    } else {
                        "${floorMap.name} (Floor ${state.floor})"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = TorchGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Pinch or use +/- to zoom, drag to pan, tap anywhere to auto-walk:",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = ParchmentMuted
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (floorRemotePlayers.isNotEmpty()) {
                        item(key = "world_map_online_header") {
                            Text(
                                text = "ONLINE PLAYERS (${floorRemotePlayers.size})",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold),
                                color = Color(0xFF7BE8FF),
                                modifier = Modifier.padding(top = 2.dp, bottom = 1.dp)
                            )
                        }
                        items(floorRemotePlayers, key = { "wm_ally_${it.playerId}" }) { rp ->
                            val dist = max(abs(rp.x - state.playerX), abs(rp.y - state.playerY))
                            Surface(
                                color = Color(0xFF142834),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF47D7FF).copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        followPlayer = false
                                        camera = WorldMapViewportCamera.recenterOnPlayer(camera, rp.x, rp.y)
                                        onWalkToTile(rp.x, rp.y)
                                    }
                                    .testTag("world_map_ally_${rp.playerId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (rp.isAdmin) {
                                            AdminGoldenCrownIcon(sizeDp = 12.dp)
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF47D7FF))
                                                    .border(1.dp, TorchGold, CircleShape)
                                            )
                                        }
                                        Text(
                                            text = "${rp.name} (L${rp.level})",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (rp.isAdmin) TorchGold else Color(0xFF8CF0FF),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "(${rp.x},${rp.y}) ${dist}t",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TorchGold
                                    )
                                }
                            }
                        }
                    }

                    if (isOverland) {
                        item(key = "world_map_pins_header") {
                            Text(
                                text = "WORLD DESTINATIONS (${visiblePins.size})",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold),
                                color = TorchGold,
                                modifier = Modifier.padding(top = 4.dp, bottom = 1.dp)
                            )
                        }
                        items(visiblePins, key = { it.id }) { pin ->
                            val badgeCol = when (pin.category) {
                                WorldPinCategory.EMPIRE -> Color(0xFFFFD65A)
                                WorldPinCategory.CITY -> TorchGold
                                WorldPinCategory.CAMP -> Color(0xFFFF9843)
                                WorldPinCategory.FERRY -> Color(0xFF47B8FF)
                                WorldPinCategory.FORGE -> Color(0xFFFFB74D)
                                WorldPinCategory.DUNGEON -> TargetRed
                            }
                            Surface(
                                color = StoneSurface,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, badgeCol.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onWalkToTile(pin.x, pin.y) }
                                    .testTag("world_map_pin_${pin.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pin.label,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = ParchmentLight,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (pin.subtitle.isNotBlank()) {
                                            Text(
                                                text = pin.subtitle,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = ParchmentMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        text = "(${pin.x},${pin.y})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeCol
                                    )
                                }
                            }
                        }
                    } else {
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
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
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
    }
}

@Composable
private fun WorldMapControlButton(
    icon: ImageVector,
    contentDesc: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bg = when {
        !enabled -> ObsidianVoid.copy(alpha = 0.55f)
        active -> TorchGold.copy(alpha = 0.28f)
        else -> ObsidianVoid.copy(alpha = 0.88f)
    }
    val border = when {
        !enabled -> StoneBorderDark
        active -> TorchGold
        else -> StoneBorderLight
    }
    val tint = when {
        !enabled -> ParchmentMuted.copy(alpha = 0.42f)
        active -> TorchGold
        else -> ParchmentLight
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = contentDesc }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .border(1.2.dp, border, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun WorldMapChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (selected) accent.copy(alpha = 0.22f) else StoneSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (selected) accent else StoneBorderDark),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            color = if (selected) accent else ParchmentMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}
