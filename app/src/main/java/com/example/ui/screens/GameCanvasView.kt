package com.example.ui.screens

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.game.model.BossSpecialKind
import com.example.game.model.CorpseEntity
import com.example.game.model.DEFAULT_TIME_OF_DAY_HOURS
import com.example.game.model.Direction
import com.example.game.model.DisplaySettings
import com.example.game.model.FloatingTextKind
import com.example.game.model.GameWorldState
import com.example.game.model.GraphicsQuality
import com.example.game.model.MonsterEntity
import com.example.game.model.ParticleDetailLevel
import com.example.game.model.ProcVisualStyle
import com.example.game.model.ProjectileKind
import com.example.game.model.ThreatBand
import com.example.game.model.TileType
import com.example.game.model.normalizeRegionId
import com.example.game.multiplayer.PlayerActionState
import com.example.game.multiplayer.RemotePlayerInterpolator
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.sprites.EntitySpriteAtlas
import com.example.game.world.BiomeWeatherSystem
import com.example.game.world.NpcRole
import com.example.game.world.NpcSpot
import com.example.game.world.WorldMaps
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.FireOrange
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PoisonGreen
import com.example.ui.theme.TargetRed
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private sealed class DepthSortableItem(val depth: Float, val subOrder: Int) {
    class TallTile(depth: Float, val tx: Int, val ty: Int, val tile: TileType) : DepthSortableItem(depth, 1)
    class NpcItem(depth: Float, val npc: NpcSpot) : DepthSortableItem(depth, 2)
    class MonsterItem(depth: Float, val monster: MonsterEntity, val fx: Float, val fy: Float, val facingRad: Float) : DepthSortableItem(depth, 3)
    class RemotePlayerItem(depth: Float, val player: RemotePlayerState, val fx: Float, val fy: Float, val walkPhase: Float, val facingRad: Float) : DepthSortableItem(depth, 4)
    class LocalPlayerItem(depth: Float, val fx: Float, val fy: Float, val moveFrac: Float, val facingRad: Float) : DepthSortableItem(depth, 5)
}

private val EMPTY_LONG_SET: Set<Long> = emptySet()

private fun packCanvasTile(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

private val DEPTH_ITEM_COMPARATOR = Comparator<DepthSortableItem> { a, b ->
    val cmp = a.depth.compareTo(b.depth)
    if (cmp != 0) cmp else a.subOrder.compareTo(b.subOrder)
}

/**
 * Pure coordinate and dimension scaling helper for the 0.8f HD world render surface paired with
 * the native 1.0x HUD, nameplate, landmark, and floating combat text overlay.
 */
object WorldViewportScaling {
    fun scaledDimensionPx(nativePx: Int, renderScale: Float): Int {
        if (nativePx <= 0) return 0
        val clamped = GraphicsQuality.clampRenderScale(renderScale)
        if (clamped >= 0.999f) return nativePx
        return (nativePx * clamped).roundToInt().coerceIn(64, nativePx)
    }

    fun toNativeCoord(scaledCoord: Float, renderScale: Float): Float {
        val clamped = GraphicsQuality.clampRenderScale(renderScale)
        return if (clamped >= 0.999f) scaledCoord else scaledCoord / clamped
    }

    fun toScaledCoord(nativeCoord: Float, renderScale: Float): Float {
        val clamped = GraphicsQuality.clampRenderScale(renderScale)
        return if (clamped >= 0.999f) nativeCoord else nativeCoord * clamped
    }
}

private enum class NativeOverlayKind {
    EXPIRING_CORPSE,
    NPC,
    MONSTER,
    REMOTE_PLAYER,
    LOCAL_PLAYER
}

/**
 * Mutable pooled overlay record written by the scaled World Render Layer and consumed immediately
 * in the same frame by the native 1.0x Crisp Overlay Layer (zero per-frame heap allocations).
 */
private class NativeOverlayRecord {
    var kind: NativeOverlayKind = NativeOverlayKind.NPC
    var screenX: Float = 0f
    var anchorY: Float = 0f
    var barWidth: Float = 0f
    var hpFrac: Float = 1f
    var hpColor: Color = Color.Green
    var primaryText: String = ""
    var textColorArgb: Int = android.graphics.Color.WHITE
    var isDeadly: Boolean = false
    var isAdmin: Boolean = false
    var secsLeft: Long = 0L
}

private class NativeOverlayFrameBuffer {
    private val pool = ArrayList<NativeOverlayRecord>(64)
    var activeCount: Int = 0
        private set

    var renderScale: Float = 1.0f
    var camPlayerX: Float = 0f
    var camPlayerY: Float = 0f
    var smoothZoom: Float = 1.0f
    var smoothYaw: Float = IsometricCamera.DEFAULT_YAW_DEG
    var nowMs: Long = 0L
    var floorId: Int = 0
    var minTx: Int = 0
    var maxTx: Int = 0
    var minTy: Int = 0
    var maxTy: Int = 0
    var nativeSpritePx: Int = 64
    var nativeHh: Float = 24f

    fun clearForFrame(
        renderScale: Float,
        camPlayerX: Float,
        camPlayerY: Float,
        smoothZoom: Float,
        smoothYaw: Float,
        nowMs: Long,
        floorId: Int,
        minTx: Int,
        maxTx: Int,
        minTy: Int,
        maxTy: Int
    ) {
        activeCount = 0
        this.renderScale = renderScale
        this.camPlayerX = camPlayerX
        this.camPlayerY = camPlayerY
        this.smoothZoom = smoothZoom
        this.smoothYaw = smoothYaw
        this.nowMs = nowMs
        this.floorId = floorId
        this.minTx = minTx
        this.maxTx = maxTx
        this.minTy = minTy
        this.maxTy = maxTy
    }

    fun acquire(): NativeOverlayRecord {
        val rec = if (activeCount < pool.size) {
            pool[activeCount]
        } else {
            NativeOverlayRecord().also { pool.add(it) }
        }
        activeCount++
        return rec
    }

    fun recordAt(index: Int): NativeOverlayRecord = pool[index]
}

/**
 * Draw-phase resource coordinate index avoiding Compose `remember(state.oreNodes)` recomposition churn.
 * Rebuilds only when the active floor changes or the immutable map reference swaps.
 */
private class FloorResourceIndexCache {
    private var lastOreFloor: Int = Int.MIN_VALUE
    private var lastOreMapRef: Any? = null
    private var depletedOres: Set<Long> = EMPTY_LONG_SET

    private var lastTreeFloor: Int = Int.MIN_VALUE
    private var lastTreeMapRef: Any? = null
    private var choppedTrees: Set<Long> = EMPTY_LONG_SET

    fun depletedOresFor(state: GameWorldState): Set<Long> {
        if (lastOreFloor == state.floor && lastOreMapRef === state.oreNodes) {
            return depletedOres
        }
        lastOreFloor = state.floor
        lastOreMapRef = state.oreNodes
        depletedOres = if (state.oreNodes.isEmpty()) {
            EMPTY_LONG_SET
        } else {
            val out = HashSet<Long>(state.oreNodes.size)
            for ((key, node) in state.oreNodes) {
                if (key.first == state.floor && node.isDepleted) {
                    out.add(packCanvasTile(key.second, key.third))
                }
            }
            out
        }
        return depletedOres
    }

    fun choppedTreesFor(state: GameWorldState): Set<Long> {
        if (lastTreeFloor == state.floor && lastTreeMapRef === state.choppedTrees) {
            return choppedTrees
        }
        lastTreeFloor = state.floor
        lastTreeMapRef = state.choppedTrees
        choppedTrees = if (state.choppedTrees.isEmpty()) {
            EMPTY_LONG_SET
        } else {
            val out = HashSet<Long>(state.choppedTrees.size)
            for ((key, rem) in state.choppedTrees) {
                if (key.first == state.floor && rem > 0) {
                    out.add(packCanvasTile(key.second, key.third))
                }
            }
            out
        }
        return choppedTrees
    }
}

/**
 * Fullscreen 60 FPS 2.5D Isometric World Viewport:
 * - True 2:1 diamond projection with strict viewport culling
 * - Smooth camera follow + two-finger pinch-to-zoom
 * - Depth-sorted extruded walls, open doorways, trees, water/reeds, torches, and entities with ground contact shadows
 * - Exact inverse isometric tap-to-grid (x, y) picking
 */
@Composable
fun GameCanvasView(
    state: GameWorldState? = null,
    stateProvider: (() -> GameWorldState)? = null,
    remotePlayers: List<RemotePlayerState> = emptyList(),
    remotePlayersProvider: (() -> List<RemotePlayerState>)? = null,
    graphicsQuality: GraphicsQuality = GraphicsQuality.DEFAULT,
    graphicsQualityProvider: (() -> GraphicsQuality)? = null,
    displaySettings: DisplaySettings = DisplaySettings(),
    displaySettingsProvider: (() -> DisplaySettings)? = null,
    measureFps: Boolean = false,
    measureFpsProvider: (() -> Boolean)? = null,
    onFpsUpdated: (Int) -> Unit = {},
    cameraYawDeg: Float = IsometricCamera.DEFAULT_YAW_DEG,
    cameraYawProvider: (() -> Float)? = null,
    rotateDragMode: Boolean = false,
    rotateDragModeProvider: (() -> Boolean)? = null,
    onCameraYawDelta: (Float) -> Unit = {},
    onTileTapped: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestStateFallback by rememberUpdatedState(state)
    val latestStateProvider by rememberUpdatedState(stateProvider)
    val latestRemotePlayersFallback by rememberUpdatedState(remotePlayers)
    val latestRemotePlayersProvider by rememberUpdatedState(remotePlayersProvider)
    val latestGraphicsQualityFallback by rememberUpdatedState(graphicsQuality)
    val latestGraphicsQualityProvider by rememberUpdatedState(graphicsQualityProvider)
    val latestDisplaySettingsFallback by rememberUpdatedState(displaySettings)
    val latestDisplaySettingsProvider by rememberUpdatedState(displaySettingsProvider)
    val latestMeasureFpsFallback by rememberUpdatedState(measureFps)
    val latestMeasureFpsProvider by rememberUpdatedState(measureFpsProvider)
    val latestOnFpsUpdated by rememberUpdatedState(onFpsUpdated)
    val latestOnTileTapped by rememberUpdatedState(onTileTapped)
    val latestCameraYaw by rememberUpdatedState(cameraYawDeg)
    val latestCameraYawProvider by rememberUpdatedState(cameraYawProvider)
    val latestRotateDragModeFallback by rememberUpdatedState(rotateDragMode)
    val latestRotateDragModeProvider by rememberUpdatedState(rotateDragModeProvider)
    val latestOnCameraYawDelta by rememberUpdatedState(onCameraYawDelta)

    fun readState(): GameWorldState =
        latestStateProvider?.invoke() ?: requireNotNull(latestStateFallback) { "GameCanvasView requires state or stateProvider" }
    fun readRemotePlayers(): List<RemotePlayerState> =
        latestRemotePlayersProvider?.invoke() ?: latestRemotePlayersFallback
    fun readGraphicsQuality(): GraphicsQuality =
        latestGraphicsQualityProvider?.invoke() ?: latestGraphicsQualityFallback
    fun readDisplaySettings(): DisplaySettings =
        latestDisplaySettingsProvider?.invoke() ?: latestDisplaySettingsFallback
    fun readMeasureFps(): Boolean =
        latestMeasureFpsProvider?.invoke() ?: latestMeasureFpsFallback
    fun readRotateDragMode(): Boolean =
        latestRotateDragModeProvider?.invoke() ?: latestRotateDragModeFallback

    val frameUptimeMsState = remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var targetZoom by remember { mutableFloatStateOf(1.0f) }
    val smoothZoomState = remember { mutableFloatStateOf(1.0f) }
    val smoothYawState = remember { mutableFloatStateOf(cameraYawProvider?.invoke() ?: cameraYawDeg) }

    LaunchedEffect(Unit) {
        var fpsWindowStartMs = SystemClock.uptimeMillis()
        var fpsFrameCount = 0
        while (true) {
            withFrameMillis {
                val nowMs = SystemClock.uptimeMillis()
                frameUptimeMsState.longValue = nowMs
                if (readMeasureFps()) {
                    fpsFrameCount++
                    val elapsed = nowMs - fpsWindowStartMs
                    if (elapsed >= 500L) {
                        val measured = ((fpsFrameCount * 1000f) / elapsed.toFloat()).roundToInt().coerceIn(1, 144)
                        latestOnFpsUpdated(measured)
                        fpsFrameCount = 0
                        fpsWindowStartMs = nowMs
                    }
                } else {
                    fpsFrameCount = 0
                    fpsWindowStartMs = nowMs
                }
                val curZoom = smoothZoomState.floatValue
                val diff = targetZoom - curZoom
                smoothZoomState.floatValue = if (abs(diff) > 0.002f) {
                    curZoom + diff * 0.22f
                } else {
                    targetZoom
                }
                val desiredYaw = latestCameraYawProvider?.invoke() ?: latestCameraYaw
                val curYaw = smoothYawState.floatValue
                val yawDiff = IsometricCamera.shortestAngleDeltaDeg(curYaw, desiredYaw)
                smoothYawState.floatValue = if (abs(yawDiff) > 0.08f) {
                    IsometricCamera.normalizeYawDeg(curYaw + yawDiff * 0.22f)
                } else {
                    IsometricCamera.normalizeYawDeg(desiredYaw)
                }
            }
        }
    }

    // Persistent simulation & render coordinators (allocated once, never recreated during 60 FPS frames)
    val worldCamera = remember { IsometricCamera() }
    val nativeCamera = remember { IsometricCamera() }
    val remoteInterpolator = remember { RemotePlayerInterpolator() }
    val activeRemoteIds = remember { HashSet<String>(16) }
    val localFacingRad = remember { floatArrayOf(ScreenFacingResolver.directionToWorldRadians(Direction.SOUTH)) }
    val remoteFacingRad = remember { HashMap<String, Float>(16) }
    val monsterFacingRad = remember { HashMap<Int, Float>(64) }
    val activeMonsterIds = remember { HashSet<Int>(64) }
    val lerpClockMs = remember { longArrayOf(SystemClock.uptimeMillis()) }
    val depthQueue = remember { ArrayList<DepthSortableItem>(256) }
    val visibleLights = remember { ArrayList<ScenePointLight>(64) }
    val overlayBuffer = remember { NativeOverlayFrameBuffer() }
    val resourceIndexCache = remember { FloorResourceIndexCache() }
    val lightLayerRenderer = remember { HalfResLightLayerRenderer() }
    val hdPostProcess = remember { HdPostProcessRenderer() }
    val weatherRenderer = remember { WeatherOverlayRenderer() }

    // Pre-warm core procedural tile bitmaps and soft radial glow sprites once at startup
    remember {
        ProceduralTileTextureAtlas.prewarmCoreTextures()
        LightGlowSpriteAtlas.prewarm()
        true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("game_world_canvas")
            .pointerInput(Unit) {
                // Unified gesture handler operating in native 1.0x screen coordinates:
                // - Two-finger twist rotates cameraYaw 360° + pinch zooms 0.6x..1.8x
                // - Single-finger horizontal drag rotates camera when Rotate-Drag Mode is toggled ON
                // - Crisp single tap converts inverse-rotated screen coords via nativeCamera to exact (x, y) world tile
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val downPos = firstDown.position
                    var lastPos = downPos
                    var wasMultiTouch = false
                    var movedTooFar = false
                    val touchSlopPx = viewConfiguration.touchSlop

                    while (true) {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        if (activePointers.isEmpty()) {
                            if (!wasMultiTouch && !movedTooFar) {
                                val st = readState()
                                val floorMap = WorldMaps.getFloor(st.floor)
                                val (tx, ty) = nativeCamera.screenToWorldTile(lastPos.x, lastPos.y)
                                if (tx in 0 until floorMap.width && ty in 0 until floorMap.height) {
                                    latestOnTileTapped(tx, ty)
                                }
                            }
                            break
                        }

                        if (activePointers.size >= 2) {
                            wasMultiTouch = true
                            val zoomChange = event.calculateZoom()
                            val rotationChange = event.calculateRotation()
                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                            if (centroidSize > 0f && zoomChange != 1f) {
                                targetZoom = (targetZoom * zoomChange)
                                    .coerceIn(IsometricCamera.MIN_ZOOM, IsometricCamera.MAX_ZOOM)
                                event.changes.forEach { it.consume() }
                            }
                            if (abs(rotationChange) > 0.05f) {
                                latestOnCameraYawDelta(rotationChange)
                                event.changes.forEach { it.consume() }
                            }
                        } else {
                            val change = activePointers.first()
                            val deltaX = change.position.x - lastPos.x
                            lastPos = change.position
                            if (hypot(lastPos.x - downPos.x, lastPos.y - downPos.y) > touchSlopPx) {
                                movedTooFar = true
                            }
                            if (readRotateDragMode() && movedTooFar && abs(deltaX) > 0.5f) {
                                latestOnCameraYawDelta(deltaX * 0.35f)
                                change.consume()
                            }
                        }
                    }
                }
            }
    ) {
        // LAYER 1: GPU Hardware-Scaled World Render Surface (0.8f in HD -> 36% fewer GPU fragments; 1.0f in Low/Med/High)
        // Uses Modifier.layout + Modifier.graphicsLayer + Modifier.drawWithCache so all per-frame state reads
        // occur strictly in layout/draw phases with zero Compose recompositions.
        Spacer(
            modifier = Modifier
                .testTag("world_render_surface")
                .layout { measurable, constraints ->
                    val renderScale = readGraphicsQuality().effectiveRenderScale()
                    val nativeW = constraints.maxWidth.coerceAtLeast(1)
                    val nativeH = constraints.maxHeight.coerceAtLeast(1)
                    val scaledW = WorldViewportScaling.scaledDimensionPx(nativeW, renderScale)
                    val scaledH = WorldViewportScaling.scaledDimensionPx(nativeH, renderScale)
                    val placeable = measurable.measure(Constraints.fixed(scaledW, scaledH))
                    layout(nativeW, nativeH) {
                        placeable.place(0, 0)
                    }
                }
                .graphicsLayer {
                    val renderScale = readGraphicsQuality().effectiveRenderScale()
                    val isScaled = renderScale < 0.999f
                    val invScale = if (isScaled) 1f / renderScale else 1f
                    scaleX = invScale
                    scaleY = invScale
                    transformOrigin = TransformOrigin(0f, 0f)
                    compositingStrategy = if (isScaled) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                }
                .drawWithCache {
                    // Cached per-surface drawing tools (allocated only when surface size changes, NEVER per frame!)
                    val reusablePath = Path()
                    val reusableTileMatrix = Matrix()
                    val reusableTilePaint = Paint().apply {
                        isFilterBitmap = false // Nearest-neighbor keeps handcrafted pixel-mosaic tiles razor sharp!
                        isAntiAlias = false
                    }
                    val ghostLayerPaint = androidx.compose.ui.graphics.Paint().apply { alpha = 0.48f }
                    val reusableScreenXY = FloatArray(2)

                    onDrawBehind {
                        val st = readState()
                        val quality = readGraphicsQuality()
                        val dispSettings = readDisplaySettings()
                        val renderScale = quality.effectiveRenderScale()
                        val now = frameUptimeMsState.longValue
                        val curSmoothZoom = smoothZoomState.floatValue
                        val curSmoothYaw = smoothYawState.floatValue
                        val animFrame = ((now / 420L) % 2L).toInt()
                        val floorMap = WorldMaps.getFloor(st.floor)

                        val moveFrac = ((now - st.lastPlayerMoveMs).toFloat() / 180f).coerceIn(0f, 1f)
                        val camPlayerX = st.prevPlayerX + (st.playerX - st.prevPlayerX) * moveFrac
                        val camPlayerY = st.prevPlayerY + (st.playerY - st.prevPlayerY) * moveFrac

                        // World surface projects at scaled zoom (curSmoothZoom * renderScale) so the exact same
                        // world frustum fills the 0.8x hardware surface before bilinear 1.25x GPU upscaling!
                        worldCamera.configure(
                            viewportW = size.width,
                            viewportH = size.height,
                            camTileX = camPlayerX,
                            camTileY = camPlayerY,
                            zoomFactor = curSmoothZoom * renderScale,
                            cameraYawDeg = curSmoothYaw
                        )

                        val tightMargin = minOf(quality.cullMargin, IsometricCamera.FRUSTUM_MARGIN_TILES)
                        val bounds = worldCamera.visibleTileBounds(size.width, size.height, floorMap.width, floorMap.height, margin = tightMargin)
                        val chunkBounds = floorMap.visibleChunkBounds(bounds.minTx, bounds.maxTx, bounds.minTy, bounds.maxTy)
                        val chunkSize = floorMap.chunkedGrid.chunkSize
                        val hw = worldCamera.halfW
                        val hh = worldCamera.halfH
                        val spritePx = (worldCamera.tileW * 0.78f * quality.spriteScaleMultiplier).roundToInt().coerceIn(32, 320)

                        overlayBuffer.clearForFrame(
                            renderScale = renderScale,
                            camPlayerX = camPlayerX,
                            camPlayerY = camPlayerY,
                            smoothZoom = curSmoothZoom,
                            smoothYaw = curSmoothYaw,
                            nowMs = now,
                            floorId = st.floor,
                            minTx = bounds.minTx,
                            maxTx = bounds.maxTx,
                            minTy = bounds.minTy,
                            maxTy = bounds.maxTy
                        )

                        depthQueue.clear()
                        visibleLights.clear()
                        val customTimeOverride = st.isAdmin && abs(st.timeOfDayHours - DEFAULT_TIME_OF_DAY_HOURS) > 0.01f
                        val collectLights = quality.drawPointLightPools || (st.floor < 0 && quality != GraphicsQuality.LOW) || (customTimeOverride && st.floor == 0)
                        val drawLightingPass = quality.drawTwilightAtmosphere || collectLights || customTimeOverride

                        if (st.floor < 0 && collectLights) {
                            worldCamera.tileCenterToScreenOut(camPlayerX, camPlayerY, reusableScreenXY)
                            visibleLights.add(
                                ScenePointLight(
                                    screenX = reusableScreenXY[0],
                                    screenY = reusableScreenXY[1] - hh * 0.55f,
                                    radiusPx = hw * 4.5f,
                                    coreColor = Color(0xFFFFE4A8),
                                    outerColor = TorchAmber,
                                    intensity = 0.62f,
                                    flickerSeed = 1.7f,
                                    priority = ScenePointLight.PRIORITY_HERO_OR_BOSS
                                )
                            )
                        }

                        // PASS 1: Chunk-Culled & Tight Camera-Rotated Frustum-Culled 2.5D Terrain Quads
                        val depletedSet = resourceIndexCache.depletedOresFor(st)
                        val choppedSet = resourceIndexCache.choppedTreesFor(st)
                        val frustumMarginX = hw * 2.0f
                        val frustumMarginTop = hh * 2.2f
                        val frustumMarginBottom = hh * 3.8f
                        for (cy in chunkBounds.minChunkY..chunkBounds.maxChunkY) {
                            val yStart = maxOf(bounds.minTy, cy * chunkSize)
                            val yEnd = minOf(bounds.maxTy, (cy + 1) * chunkSize - 1)
                            for (cxChunk in chunkBounds.minChunkX..chunkBounds.maxChunkX) {
                                val xStart = maxOf(bounds.minTx, cxChunk * chunkSize)
                                val xEnd = minOf(bounds.maxTx, (cxChunk + 1) * chunkSize - 1)
                                for (ty in yStart..yEnd) {
                                    for (tx in xStart..xEnd) {
                                        worldCamera.tileCenterToScreenOut(tx.toFloat(), ty.toFloat(), reusableScreenXY)
                                        val cx = reusableScreenXY[0]
                                        val cyScr = reusableScreenXY[1]
                                        if (!worldCamera.isScreenPointInFrustum(
                                                sx = cx,
                                                sy = cyScr,
                                                viewportW = size.width,
                                                viewportH = size.height,
                                                marginX = frustumMarginX,
                                                marginTop = frustumMarginTop,
                                                marginBottom = frustumMarginBottom
                                            )
                                        ) continue

                                        val baseTile = floorMap.tileAt(tx, ty)
                                        val renderTile = when {
                                            baseTile.oreVeinKind() != null &&
                                                depletedSet.isNotEmpty() &&
                                                depletedSet.contains(packCanvasTile(tx, ty)) -> TileType.DEPLETED_VEIN
                                            baseTile == TileType.TREE &&
                                                choppedSet.isNotEmpty() &&
                                                choppedSet.contains(packCanvasTile(tx, ty)) -> TileType.TREE_STUMP
                                            else -> baseTile
                                        }

                                        IsometricTileDrawer.drawGroundTile(
                                            scope = this,
                                            path = reusablePath,
                                            cam = worldCamera,
                                            floorMap = floorMap,
                                            tx = tx,
                                            ty = ty,
                                            tile = renderTile,
                                            cx = cx,
                                            cy = cyScr,
                                            nowMs = now,
                                            quality = quality,
                                            tileMatrix = reusableTileMatrix,
                                            tilePaint = reusableTilePaint
                                        )

                                        if (collectLights) {
                                            IsometricLightingEngine.collectTilePointLight(
                                                tile = renderTile,
                                                tx = tx,
                                                ty = ty,
                                                cx = cx,
                                                cy = cyScr,
                                                cam = worldCamera,
                                                outLights = visibleLights
                                            )
                                        }

                                        if (IsometricTileDrawer.isTallProp(renderTile)) {
                                            depthQueue.add(
                                                DepthSortableItem.TallTile(
                                                    depth = worldCamera.depthOf(tx.toFloat(), ty.toFloat()),
                                                    tx = tx,
                                                    ty = ty,
                                                    tile = renderTile
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // PASS 2: Rotated Ground Decals (Walk Path Quads, Boss AoE Telegraphs, Corpses)
                        if (st.walkPath.isNotEmpty()) {
                            st.walkPath.forEach { (wx, wy) ->
                                if (wx in bounds.minTx..bounds.maxTx && wy in bounds.minTy..bounds.maxTy) {
                                    worldCamera.tileCenterToScreenOut(wx.toFloat(), wy.toFloat(), reusableScreenXY)
                                    IsometricTileDrawer.drawRotatedQuad(
                                        this, reusablePath, worldCamera, reusableScreenXY[0], reusableScreenXY[1],
                                        fracU = 0.36f, fracV = 0.36f,
                                        fillColor = TorchGold.copy(alpha = 0.32f),
                                        strokeColor = TorchGold.copy(alpha = 0.72f),
                                        strokeWidth = 1.6f
                                    )
                                }
                            }
                        }

                        val aoeWavePulse = ((sin(now * 0.015) + 1.0) * 0.5).toFloat()
                        val aoeFillAlpha = 0.24f + aoeWavePulse * 0.28f
                        val aoeCoreScale = 0.34f + aoeWavePulse * 0.26f
                        st.monsters.forEach { m ->
                            if (m.floor == st.floor && m.pendingAoeTiles.isNotEmpty()) {
                                val fillColor: Color
                                val strokeColor: Color
                                val coreColor: Color
                                val arcaneFlag: Boolean
                                when (m.type.specialKind) {
                                    BossSpecialKind.VENOM_NOVA -> {
                                        fillColor = Color(0xFF4CFF33); strokeColor = Color(0xFFB4FF6E); coreColor = Color(0xFFDEFF99); arcaneFlag = true
                                    }
                                    BossSpecialKind.QUARRY_QUAKE -> {
                                        fillColor = Color(0xFF8C5EFF); strokeColor = Color(0xFFD8C2FF); coreColor = Color(0xFFF3EAFF); arcaneFlag = true
                                    }
                                    BossSpecialKind.LABYRINTH_PYRE -> {
                                        fillColor = Color(0xFFFF6D1F); strokeColor = Color(0xFFFFD45C); coreColor = Color(0xFFFFF4B8); arcaneFlag = false
                                    }
                                    else -> {
                                        fillColor = FireOrange; strokeColor = TargetRed; coreColor = Color(0xFFFFE066); arcaneFlag = false
                                    }
                                }
                                m.pendingAoeTiles.forEach { (ax, ay) ->
                                    if (ax in bounds.minTx..bounds.maxTx && ay in bounds.minTy..bounds.maxTy) {
                                        worldCamera.tileCenterToScreenOut(ax.toFloat(), ay.toFloat(), reusableScreenXY)
                                        val sx = reusableScreenXY[0]
                                        val sy = reusableScreenXY[1]
                                        IsometricTileDrawer.drawRotatedQuad(
                                            this, reusablePath, worldCamera, sx, sy, fracU = 0.92f, fracV = 0.92f,
                                            fillColor = fillColor.copy(alpha = aoeFillAlpha),
                                            strokeColor = strokeColor.copy(alpha = 0.95f),
                                            strokeWidth = 3.2f
                                        )
                                        IsometricTileDrawer.drawRotatedQuad(
                                            this, reusablePath, worldCamera, sx, sy, fracU = aoeCoreScale, fracV = aoeCoreScale,
                                            fillColor = coreColor.copy(alpha = (aoeFillAlpha + 0.22f).coerceAtMost(0.88f))
                                        )
                                        if (collectLights && visibleLights.size < 44) {
                                            visibleLights.add(
                                                ScenePointLight(
                                                    screenX = sx,
                                                    screenY = sy,
                                                    radiusPx = hw * 1.65f,
                                                    coreColor = coreColor,
                                                    outerColor = fillColor,
                                                    intensity = 0.65f + aoeWavePulse * 0.25f,
                                                    flickerSeed = (ax * 3 + ay * 5).toFloat(),
                                                    isArcane = arcaneFlag,
                                                    priority = ScenePointLight.PRIORITY_COMBAT_FX
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        st.corpses.forEach { corpse ->
                            if (corpse.floor == st.floor && corpse.x in bounds.minTx..bounds.maxTx && corpse.y in bounds.minTy..bounds.maxTy) {
                                drawIsometricCorpse(
                                    corpse = corpse,
                                    localPlayerId = st.saveId,
                                    camera = worldCamera,
                                    path = reusablePath,
                                    spritePx = spritePx,
                                    animFrame = animFrame,
                                    now = now,
                                    overlayBuffer = overlayBuffer
                                )
                            }
                        }

                        // Enqueue visible NPCs, Monsters, Remote Players, and Local Player into Dynamic Rotated Depth Queue
                        WorldMaps.npcs.forEach { npc ->
                            if (npc.floor == st.floor && npc.x in bounds.minTx..bounds.maxTx && npc.y in bounds.minTy..bounds.maxTy) {
                                depthQueue.add(
                                    DepthSortableItem.NpcItem(
                                        depth = worldCamera.depthOf(npc.x.toFloat(), npc.y.toFloat()),
                                        npc = npc
                                    )
                                )
                            }
                        }

                        val localRegionId = normalizeRegionId(st.regionId)
                        val dtSec = ((now - lerpClockMs[0]).coerceIn(1L, 120L)) / 1000f
                        lerpClockMs[0] = now
                        val turnAlpha = (1f - exp(-14.0f * dtSec)).coerceIn(0.12f, 1f)

                        activeMonsterIds.clear()
                        st.monsters.forEach { m ->
                            if (m.floor == st.floor && m.x in (bounds.minTx - 1)..(bounds.maxTx + 1) && m.y in (bounds.minTy - 1)..(bounds.maxTy + 1)) {
                                activeMonsterIds.add(m.id)
                                val mFrac = ((now - m.lastMoveTimeMs).toFloat() / 220f).coerceIn(0f, 1f)
                                val mxF = m.lastMoveFromX + (m.x - m.lastMoveFromX) * mFrac
                                val myF = m.lastMoveFromY + (m.y - m.lastMoveFromY) * mFrac
                                if (m.type.isBoss && collectLights && visibleLights.size < 46) {
                                    worldCamera.tileCenterToScreenOut(mxF, myF, reusableScreenXY)
                                    val bossCore: Color
                                    val bossOuter: Color
                                    val bossArcane: Boolean
                                    when (m.type.specialKind) {
                                        BossSpecialKind.VENOM_NOVA -> {
                                            bossCore = Color(0xFFA8FF6E); bossOuter = Color(0xFF4CFF33); bossArcane = true
                                        }
                                        BossSpecialKind.QUARRY_QUAKE -> {
                                            bossCore = Color(0xFFE0CCFF); bossOuter = Color(0xFF9466FF); bossArcane = true
                                        }
                                        BossSpecialKind.LABYRINTH_PYRE -> {
                                            bossCore = Color(0xFFFFE48C); bossOuter = Color(0xFFFF5E1A); bossArcane = false
                                        }
                                        else -> {
                                            bossCore = Color(0xFFFFC266); bossOuter = FireOrange; bossArcane = false
                                        }
                                    }
                                    visibleLights.add(
                                        ScenePointLight(
                                            screenX = reusableScreenXY[0],
                                            screenY = reusableScreenXY[1] - hh * 0.8f,
                                            radiusPx = hw * 3.4f,
                                            coreColor = bossCore,
                                            outerColor = bossOuter,
                                            intensity = 0.82f,
                                            flickerSeed = (m.id * 11).toFloat(),
                                            isArcane = bossArcane,
                                            priority = ScenePointLight.PRIORITY_HERO_OR_BOSS
                                        )
                                    )
                                }

                                val dxToHero = camPlayerX - mxF
                                val dyToHero = camPlayerY - myF
                                val faceTarget = m.id == st.targetMonsterId || (m.targetPlayerId == null && hypot(dxToHero, dyToHero) <= 4.5f)
                                val moveDx = if (mFrac < 0.99f) (m.x - m.lastMoveFromX).toFloat() else 0f
                                val moveDy = if (mFrac < 0.99f) (m.y - m.lastMoveFromY).toFloat() else 0f
                                val desiredRad = when {
                                    faceTarget -> ScreenFacingResolver.vectorToWorldRadians(dxToHero, dyToHero, m.facing)
                                    hypot(moveDx, moveDy) > 0.05f -> ScreenFacingResolver.vectorToWorldRadians(moveDx, moveDy, m.facing)
                                    else -> ScreenFacingResolver.directionToWorldRadians(m.facing)
                                }
                                val prevRad = monsterFacingRad[m.id]
                                val smoothRad = if (prevRad == null) desiredRad else ScreenFacingResolver.shortestAngleLerp(prevRad, desiredRad, turnAlpha)
                                monsterFacingRad[m.id] = smoothRad

                                depthQueue.add(
                                    DepthSortableItem.MonsterItem(
                                        depth = worldCamera.depthOf(mxF, myF),
                                        monster = m,
                                        fx = mxF,
                                        fy = myF,
                                        facingRad = smoothRad
                                    )
                                )
                            }
                        }
                        if (monsterFacingRad.size > activeMonsterIds.size) {
                            val mIter = monsterFacingRad.keys.iterator()
                            while (mIter.hasNext()) {
                                if (mIter.next() !in activeMonsterIds) mIter.remove()
                            }
                        }

                        activeRemoteIds.clear()
                        readRemotePlayers().forEach { rp ->
                            if (rp.floor != st.floor || !com.example.game.model.isRegionCompatible(st.floor, localRegionId, rp.regionId)) return@forEach
                            activeRemoteIds.add(rp.playerId)
                            val rState = remoteInterpolator.updatePlayer(rp = rp, nowUptimeMs = now, dtSecRaw = dtSec)
                            val visX = rState.renderX
                            val visY = rState.renderY
                            val desiredRpRad = if (rState.isGliding && hypot(rState.velocityX, rState.velocityY) > 0.08f) {
                                ScreenFacingResolver.vectorToWorldRadians(rState.velocityX, rState.velocityY, rp.direction)
                            } else {
                                ScreenFacingResolver.directionToWorldRadians(rp.direction)
                            }
                            val prevRpRad = remoteFacingRad[rp.playerId]
                            val smoothRpRad = if (prevRpRad == null || rState.hardSnappedThisFrame) {
                                desiredRpRad
                            } else {
                                ScreenFacingResolver.shortestAngleLerp(prevRpRad, desiredRpRad, turnAlpha)
                            }
                            remoteFacingRad[rp.playerId] = smoothRpRad

                            if (visX >= bounds.minTx - 2 && visX <= bounds.maxTx + 2 &&
                                visY >= bounds.minTy - 2 && visY <= bounds.maxTy + 2
                            ) {
                                depthQueue.add(
                                    DepthSortableItem.RemotePlayerItem(
                                        depth = worldCamera.depthOf(visX, visY),
                                        player = rp,
                                        fx = visX,
                                        fy = visY,
                                        walkPhase = rState.walkPhase,
                                        facingRad = smoothRpRad
                                    )
                                )
                            }
                        }
                        remoteInterpolator.pruneInactive(activeRemoteIds)
                        if (remoteFacingRad.size > activeRemoteIds.size) {
                            val iter = remoteFacingRad.keys.iterator()
                            while (iter.hasNext()) {
                                if (iter.next() !in activeRemoteIds) iter.remove()
                            }
                        }

                        val lockedMonster = st.targetMonsterId?.let { tid ->
                            st.monsters.firstOrNull { it.id == tid && it.floor == st.floor }
                        }
                        val desiredLocalRad = when {
                            lockedMonster != null -> ScreenFacingResolver.vectorToWorldRadians(
                                lockedMonster.x.toFloat() - camPlayerX,
                                lockedMonster.y.toFloat() - camPlayerY,
                                st.playerFacing
                            )
                            moveFrac < 0.99f && hypot((st.playerX - st.prevPlayerX).toFloat(), (st.playerY - st.prevPlayerY).toFloat()) > 0.05f ->
                                ScreenFacingResolver.vectorToWorldRadians(
                                    (st.playerX - st.prevPlayerX).toFloat(),
                                    (st.playerY - st.prevPlayerY).toFloat(),
                                    st.playerFacing
                                )
                            else -> ScreenFacingResolver.directionToWorldRadians(st.playerFacing)
                        }
                        localFacingRad[0] = ScreenFacingResolver.shortestAngleLerp(localFacingRad[0], desiredLocalRad, turnAlpha)

                        depthQueue.add(
                            DepthSortableItem.LocalPlayerItem(
                                depth = worldCamera.depthOf(camPlayerX, camPlayerY),
                                fx = camPlayerX,
                                fy = camPlayerY,
                                moveFrac = moveFrac,
                                facingRad = localFacingRad[0]
                            )
                        )

                        // PASS 3: Dynamic 360-Degree Rotated Painter's Algorithm Depth Sort (zero-alloc comparator)
                        depthQueue.sortWith(DEPTH_ITEM_COMPARATOR)

                        for (i in 0 until depthQueue.size) {
                            when (val item = depthQueue[i]) {
                                is DepthSortableItem.TallTile -> {
                                    worldCamera.tileCenterToScreenOut(item.tx.toFloat(), item.ty.toFloat(), reusableScreenXY)
                                    IsometricTileDrawer.drawTallTileProp(
                                        scope = this,
                                        path = reusablePath,
                                        cam = worldCamera,
                                        floorMap = floorMap,
                                        tx = item.tx,
                                        ty = item.ty,
                                        tile = item.tile,
                                        cx = reusableScreenXY[0],
                                        cy = reusableScreenXY[1],
                                        nowMs = now,
                                        quality = quality,
                                        playerFx = camPlayerX,
                                        playerFy = camPlayerY
                                    )
                                }
                                is DepthSortableItem.NpcItem -> {
                                    drawIsometricNpc(
                                        npc = item.npc,
                                        camera = worldCamera,
                                        path = reusablePath,
                                        hh = hh,
                                        spritePx = spritePx,
                                        now = now,
                                        overlayBuffer = overlayBuffer,
                                        quality = quality
                                    )
                                }
                                is DepthSortableItem.MonsterItem -> {
                                    drawIsometricMonster(
                                        m = item.monster,
                                        fx = item.fx,
                                        fy = item.fy,
                                        facingRad = item.facingRad,
                                        state = st,
                                        camera = worldCamera,
                                        path = reusablePath,
                                        hw = hw,
                                        hh = hh,
                                        spritePx = spritePx,
                                        now = now,
                                        overlayBuffer = overlayBuffer,
                                        quality = quality
                                    )
                                }
                                is DepthSortableItem.RemotePlayerItem -> {
                                    drawIsometricRemotePlayer(
                                        rp = item.player,
                                        fx = item.fx,
                                        fy = item.fy,
                                        walkPhase = item.walkPhase,
                                        facingRad = item.facingRad,
                                        state = st,
                                        camera = worldCamera,
                                        path = reusablePath,
                                        hw = hw,
                                        hh = hh,
                                        spritePx = spritePx,
                                        now = now,
                                        overlayBuffer = overlayBuffer,
                                        quality = quality
                                    )
                                }
                                is DepthSortableItem.LocalPlayerItem -> {
                                    drawIsometricLocalPlayer(
                                        state = st,
                                        fx = item.fx,
                                        fy = item.fy,
                                        moveFrac = item.moveFrac,
                                        facingRad = item.facingRad,
                                        camera = worldCamera,
                                        path = reusablePath,
                                        ghostLayerPaint = ghostLayerPaint,
                                        hw = hw,
                                        hh = hh,
                                        spritePx = spritePx,
                                        now = now,
                                        overlayBuffer = overlayBuffer,
                                        quality = quality
                                    )
                                }
                            }
                        }

                        // PASS 4: Projectiles, Spells, Equipment Proc AOE Shockwaves & Sparks (registers priority lights!)
                        drawIsometricProjectiles(st, worldCamera, hw, now, quality, if (collectLights) visibleLights else null)
                        drawProcAoeEffects(st, worldCamera, hw, hh, now, quality, if (collectLights) visibleLights else null)

                        // PASS 5: Cached Time-of-Day Atmospheric Grade & Closest-14 Half-Res Dynamic Point-Light Composite
                        if (drawLightingPass) {
                            IsometricLightingEngine.drawTwilightAndPointLightPass(
                                scope = this,
                                floorId = st.floor,
                                nowMs = now,
                                quality = quality,
                                lights = visibleLights,
                                timeOfDayHours = st.timeOfDayHours,
                                lightLayerRenderer = lightLayerRenderer
                            )
                        }

                        // PASS 5B: Zero-Shader Dynamic Biome Weather Particles, Ground Ripples & Cached Gradients
                        if (dispSettings.showWeatherEffects) {
                            val resolvedWeather = BiomeWeatherSystem.resolveWeather(
                                regionId = normalizeRegionId(st.regionId),
                                floor = st.floor,
                                utcMillis = System.currentTimeMillis(),
                                adminOverride = st.adminWeatherOverride
                            )
                            weatherRenderer.drawWeatherPass(
                                scope = this,
                                resolved = resolvedWeather,
                                quality = quality,
                                nowMs = now,
                                camTileX = camPlayerX,
                                camTileY = camPlayerY,
                                cameraYawDeg = curSmoothYaw,
                                timeOfDayHours = st.timeOfDayHours
                            )
                        }

                        // PASS 6: Cached HD Vignette / Depth Contrast Pass (no full-screen RuntimeShader!)
                        if (quality.drawHdVignette) {
                            hdPostProcess.drawHdPostProcess(
                                scope = this,
                                quality = quality,
                                floorId = st.floor,
                                nowMs = now,
                                timeOfDayHours = st.timeOfDayHours
                            )
                        }
                    }
                }
        )

        // LAYER 2: Native 1.0x Crisp Nameplates, HP Bars, Threat Badges, Landmarks & Floating Combat Text
        // Always rendered at full native device resolution even when HD world surface runs at 0.8f scale!
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .testTag("native_overlay_canvas")
                .drawWithCache {
                    val floatingPaint = Paint().apply {
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.MONOSPACE
                        isFakeBoldText = true
                        textSize = 30f
                    }
                    val outlinePaint = Paint(floatingPaint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 5.5f
                        color = android.graphics.Color.BLACK
                    }
                    val labelPaint = Paint().apply {
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.MONOSPACE
                        isFakeBoldText = true
                        textSize = 21f
                        color = android.graphics.Color.parseColor("#EDE3CE")
                    }
                    val labelShadowPaint = Paint(labelPaint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 4f
                        color = android.graphics.Color.BLACK
                    }
                    val crownPath = Path()
                    val overlayScreenXY = FloatArray(2)

                    onDrawBehind {
                        val now = frameUptimeMsState.longValue
                        val st = readState()
                        val quality = readGraphicsQuality()
                        val dispSettings = readDisplaySettings()
                        val curSmoothZoom = smoothZoomState.floatValue
                        val curSmoothYaw = smoothYawState.floatValue
                        val moveFrac = ((now - st.lastPlayerMoveMs).toFloat() / 180f).coerceIn(0f, 1f)
                        val camPlayerX = st.prevPlayerX + (st.playerX - st.prevPlayerX) * moveFrac
                        val camPlayerY = st.prevPlayerY + (st.playerY - st.prevPlayerY) * moveFrac

                        // Synchronize native 1.0x camera for exact tap-to-tile picking & crisp overlay projection
                        nativeCamera.configure(
                            viewportW = size.width,
                            viewportH = size.height,
                            camTileX = camPlayerX,
                            camTileY = camPlayerY,
                            zoomFactor = curSmoothZoom,
                            cameraYawDeg = curSmoothYaw
                        )
                        val nativeHh = nativeCamera.halfH
                        val nativeSpritePx = (nativeCamera.tileW * 0.78f * quality.spriteScaleMultiplier)
                            .roundToInt().coerceIn(36, 320)
                        val renderScale = overlayBuffer.renderScale

                        // 1. Render all queued entity nameplates, HP bars, threat skulls, admin crowns & corpse timers at 1.0x
                        val count = overlayBuffer.activeCount
                        for (i in 0 until count) {
                            val rec = overlayBuffer.recordAt(i)
                            val nx = WorldViewportScaling.toNativeCoord(rec.screenX, renderScale)
                            val ny = WorldViewportScaling.toNativeCoord(rec.anchorY, renderScale)
                            when (rec.kind) {
                                NativeOverlayKind.EXPIRING_CORPSE -> {
                                    val badgeW = 46f
                                    val badgeH = 22f
                                    val bx = nx - badgeW * 0.5f
                                    val by = ny - badgeH
                                    val warnColor = if (rec.secsLeft <= 5L) Color(0xFFFF4040) else Color(0xFFFFB74D)
                                    drawRoundRect(
                                        color = Color(0xE0140E0C),
                                        topLeft = Offset(bx, by),
                                        size = Size(badgeW, badgeH),
                                        cornerRadius = CornerRadius(6f, 6f)
                                    )
                                    drawRoundRect(
                                        color = warnColor,
                                        topLeft = Offset(bx, by),
                                        size = Size(badgeW, badgeH),
                                        cornerRadius = CornerRadius(6f, 6f),
                                        style = Stroke(width = 1.8f)
                                    )
                                    val prevColor = labelPaint.color
                                    val prevSize = labelPaint.textSize
                                    val prevShadowSize = labelShadowPaint.textSize
                                    labelPaint.color = rec.textColorArgb
                                    labelPaint.textSize = 15.5f
                                    labelShadowPaint.textSize = 15.5f
                                    drawContext.canvas.nativeCanvas.apply {
                                        drawText(rec.primaryText, nx, by + badgeH * 0.74f, labelShadowPaint)
                                        drawText(rec.primaryText, nx, by + badgeH * 0.74f, labelPaint)
                                    }
                                    labelPaint.color = prevColor
                                    labelPaint.textSize = prevSize
                                    labelShadowPaint.textSize = prevShadowSize
                                }
                                NativeOverlayKind.NPC -> {
                                    val prevColor = labelPaint.color
                                    labelPaint.color = rec.textColorArgb
                                    drawContext.canvas.nativeCanvas.apply {
                                        drawText(rec.primaryText, nx, ny - 8f, labelShadowPaint)
                                        drawText(rec.primaryText, nx, ny - 8f, labelPaint)
                                    }
                                    labelPaint.color = prevColor
                                }
                                NativeOverlayKind.MONSTER -> {
                                    val barW = WorldViewportScaling.toNativeCoord(rec.barWidth, renderScale)
                                    val barX = nx - barW * 0.5f
                                    val barY = ny - 10f
                                    drawRect(Color.Black, Offset(barX - 1f, barY - 1f), Size(barW + 2f, 8f))
                                    drawRect(rec.hpColor, Offset(barX, barY), Size(barW * rec.hpFrac, 6f))
                                    val prevColor = labelPaint.color
                                    labelPaint.color = rec.textColorArgb
                                    val textY = barY - 5f
                                    if (rec.isDeadly) {
                                        val textWidth = labelPaint.measureText(rec.primaryText)
                                        val skullPx = 2.0f
                                        val skullX = nx - textWidth * 0.5f - skullPx * 7.5f
                                        val skullY = textY - skullPx * 7.0f
                                        drawRect(Color.Black, Offset(skullX - 1f, skullY - 1f), Size(skullPx * 6f + 2f, skullPx * 6f + 2f))
                                        drawRect(Color(0xFFF3EFE6), Offset(skullX + skullPx, skullY), Size(skullPx * 4f, skullPx * 4f))
                                        drawRect(Color(0xFFF3EFE6), Offset(skullX, skullY + skullPx), Size(skullPx * 6f, skullPx * 2.5f))
                                        drawRect(Color(0xFFF3EFE6), Offset(skullX + skullPx * 1.5f, skullY + skullPx * 4f), Size(skullPx * 3f, skullPx * 1.8f))
                                        drawRect(Color(0xFFB71C1C), Offset(skullX + skullPx * 1.1f, skullY + skullPx * 1.6f), Size(skullPx * 1.2f, skullPx * 1.2f))
                                        drawRect(Color(0xFFB71C1C), Offset(skullX + skullPx * 3.7f, skullY + skullPx * 1.6f), Size(skullPx * 1.2f, skullPx * 1.2f))
                                    }
                                    drawContext.canvas.nativeCanvas.apply {
                                        drawText(rec.primaryText, nx, textY, labelShadowPaint)
                                        drawText(rec.primaryText, nx, textY, labelPaint)
                                    }
                                    labelPaint.color = prevColor
                                }
                                NativeOverlayKind.REMOTE_PLAYER -> {
                                    val barW = WorldViewportScaling.toNativeCoord(rec.barWidth, renderScale)
                                    val barX = nx - barW * 0.5f
                                    val barY = ny - 10f
                                    drawRect(Color.Black, Offset(barX - 1f, barY - 1f), Size(barW + 2f, 8f))
                                    drawRect(rec.hpColor, Offset(barX, barY), Size(barW * rec.hpFrac, 6f))
                                    val prevColor = labelPaint.color
                                    labelPaint.color = rec.textColorArgb
                                    val tagWidth = labelPaint.measureText(rec.primaryText)
                                    if (rec.isAdmin) {
                                        val crownH = 14f
                                        val crownW = crownH * 1.32f
                                        val gap = 4f
                                        val totalW = crownW + gap + tagWidth
                                        val crownLeft = nx - totalW * 0.5f
                                        val textCenterX = nx + (crownW + gap) * 0.5f
                                        drawIsometricAdminCrown(
                                            leftX = crownLeft,
                                            centerY = barY - 11.5f,
                                            heightPx = crownH,
                                            reusablePath = crownPath
                                        )
                                        drawContext.canvas.nativeCanvas.apply {
                                            drawText(rec.primaryText, textCenterX, barY - 5f, labelShadowPaint)
                                            drawText(rec.primaryText, textCenterX, barY - 5f, labelPaint)
                                        }
                                    } else {
                                        val pipCenter = Offset(nx - tagWidth * 0.5f - 9f, barY - 12f)
                                        drawCircle(Color.Black, radius = 4.6f, center = pipCenter)
                                        drawCircle(TorchGold, radius = 3.6f, center = pipCenter)
                                        drawCircle(Color(0xFF47D7FF), radius = 2.4f, center = pipCenter)
                                        drawContext.canvas.nativeCanvas.apply {
                                            drawText(rec.primaryText, nx, barY - 5f, labelShadowPaint)
                                            drawText(rec.primaryText, nx, barY - 5f, labelPaint)
                                        }
                                    }
                                    labelPaint.color = prevColor
                                }
                                NativeOverlayKind.LOCAL_PLAYER -> {
                                    val barW = WorldViewportScaling.toNativeCoord(rec.barWidth, renderScale)
                                    val barX = nx - barW * 0.5f
                                    val barY = ny - 10f
                                    drawRect(Color.Black, Offset(barX - 1f, barY - 1f), Size(barW + 2f, 8f))
                                    drawRect(rec.hpColor, Offset(barX, barY), Size(barW * rec.hpFrac, 6f))
                                    if (rec.isAdmin) {
                                        val prevColor = labelPaint.color
                                        labelPaint.color = rec.textColorArgb
                                        val nameW = labelPaint.measureText(rec.primaryText)
                                        val crownH = 14.5f
                                        val crownW = crownH * 1.32f
                                        val gap = 4f
                                        val totalW = crownW + gap + nameW
                                        val crownLeft = nx - totalW * 0.5f
                                        val textCenterX = nx + (crownW + gap) * 0.5f
                                        drawIsometricAdminCrown(
                                            leftX = crownLeft,
                                            centerY = barY - 11.5f,
                                            heightPx = crownH,
                                            reusablePath = crownPath
                                        )
                                        drawContext.canvas.nativeCanvas.apply {
                                            drawText(rec.primaryText, textCenterX, barY - 5f, labelShadowPaint)
                                            drawText(rec.primaryText, textCenterX, barY - 5f, labelPaint)
                                        }
                                        labelPaint.color = prevColor
                                    } else {
                                        drawContext.canvas.nativeCanvas.apply {
                                            drawText(rec.primaryText, nx, barY - 5f, labelShadowPaint)
                                            drawText(rec.primaryText, nx, barY - 5f, labelPaint)
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Crisp Native 1.0x Landmarks
                        val minTx = overlayBuffer.minTx
                        val maxTx = overlayBuffer.maxTx
                        val minTy = overlayBuffer.minTy
                        val maxTy = overlayBuffer.maxTy
                        WorldMaps.landmarks.forEach { lm ->
                            if (lm.floor == st.floor && lm.x in minTx..maxTx && lm.y in minTy..maxTy) {
                                nativeCamera.tileCenterToScreenOut(lm.x.toFloat(), lm.y.toFloat(), overlayScreenXY)
                                val prevSize = labelPaint.textSize
                                val prevShadowSize = labelShadowPaint.textSize
                                labelPaint.textSize = 18f
                                labelShadowPaint.textSize = 18f
                                drawContext.canvas.nativeCanvas.apply {
                                    drawText(lm.label, overlayScreenXY[0], overlayScreenXY[1] - nativeHh * 2.1f, labelShadowPaint)
                                    drawText(lm.label, overlayScreenXY[0], overlayScreenXY[1] - nativeHh * 2.1f, labelPaint)
                                }
                                labelPaint.textSize = prevSize
                                labelShadowPaint.textSize = prevShadowSize
                            }
                        }

                        // 3. Crisp Native 1.0x Floating Combat / Resource / Proc Text
                        st.floatingTexts.forEach { ft ->
                            if (ft.floor != st.floor) return@forEach
                            if (!dispSettings.shouldRenderFloatingText(ft.kind)) return@forEach
                            val age = (now - ft.createdAtMs).toFloat()
                            if (age < 0f || age > 1300f) return@forEach
                            val progress = (age / 1300f).coerceIn(0f, 1f)
                            nativeCamera.tileCenterToScreenOut(ft.tileX, ft.tileY, overlayScreenXY)
                            val fx = overlayScreenXY[0]
                            val fy = overlayScreenXY[1] - nativeSpritePx * 0.72f - progress * (nativeHh * 2.2f)
                            val alpha = ((1f - progress * 0.85f) * 255).toInt().coerceIn(0, 255)

                            val isProcBanner = ft.kind == FloatingTextKind.PROC_CALLOUT
                            val prevTextSize = floatingPaint.textSize
                            val prevOutlineSize = outlinePaint.textSize
                            if (isProcBanner) {
                                floatingPaint.textSize = prevTextSize * 1.18f
                                outlinePaint.textSize = prevOutlineSize * 1.18f
                            }

                            floatingPaint.color = when (ft.kind) {
                                FloatingTextKind.DAMAGE_MONSTER -> android.graphics.Color.parseColor("#FF5238")
                                FloatingTextKind.DAMAGE_PLAYER -> android.graphics.Color.parseColor("#FF2B2B")
                                FloatingTextKind.HEAL -> android.graphics.Color.parseColor("#4CE678")
                                FloatingTextKind.MANA -> android.graphics.Color.parseColor("#5CA8FF")
                                FloatingTextKind.POISON -> android.graphics.Color.parseColor("#7BFF33")
                                FloatingTextKind.FIRE -> android.graphics.Color.parseColor("#FF8524")
                                FloatingTextKind.BLOCK -> android.graphics.Color.parseColor("#CBD5E1")
                                FloatingTextKind.XP -> android.graphics.Color.parseColor("#F5D76E")
                                FloatingTextKind.SKILL_UP -> android.graphics.Color.parseColor("#FFE45E")
                                FloatingTextKind.PROC_CALLOUT -> (ft.customColorHex?.toInt() ?: android.graphics.Color.parseColor("#FFE45E"))
                                FloatingTextKind.INFO -> android.graphics.Color.parseColor("#EDE3CE")
                            }
                            floatingPaint.alpha = alpha
                            outlinePaint.alpha = alpha
                            drawContext.canvas.nativeCanvas.apply {
                                drawText(ft.text, fx, fy, outlinePaint)
                                drawText(ft.text, fx, fy, floatingPaint)
                            }
                            if (isProcBanner) {
                                floatingPaint.textSize = prevTextSize
                                outlinePaint.textSize = prevOutlineSize
                            }
                        }
                    }
                }
        )
    }
}

private fun DrawScope.drawIsometricCorpse(
    corpse: CorpseEntity,
    localPlayerId: String,
    camera: IsometricCamera,
    path: Path,
    spritePx: Int,
    animFrame: Int,
    now: Long,
    overlayBuffer: NativeOverlayFrameBuffer
) {
    val c = camera.tileCenterToScreen(corpse.x.toFloat(), corpse.y.toFloat())
    val expiringSoon = !corpse.isPlayerCorpse && corpse.isExpiringSoon(now)
    val ownerLockedForMe = corpse.isOwnerLockedFor(localPlayerId, now)
    val isPublicOtherLoot = !corpse.isPlayerCorpse &&
        !corpse.ownerPlayerId.isNullOrBlank() &&
        corpse.ownerPlayerId != localPlayerId &&
        !ownerLockedForMe

    if (corpse.isPlayerCorpse) {
        val pulse = if (animFrame == 0) 0.65f else 0.30f
        IsometricTileDrawer.drawRotatedQuad(
            this, path, camera, c.x, c.y, fracU = 0.88f, fracV = 0.88f,
            fillColor = TorchGold.copy(alpha = pulse * 0.22f),
            strokeColor = TorchGold.copy(alpha = pulse),
            strokeWidth = 3.2f
        )
    } else if (ownerLockedForMe) {
        val lockPulse = ((sin(now * 0.010) + 1.0) * 0.5).toFloat()
        IsometricTileDrawer.drawRotatedQuad(
            this, path, camera, c.x, c.y, fracU = 0.88f, fracV = 0.88f,
            fillColor = TorchAmber.copy(alpha = 0.12f + lockPulse * 0.14f),
            strokeColor = TorchAmber.copy(alpha = 0.55f + lockPulse * 0.35f),
            strokeWidth = 2.6f
        )
    } else if (expiringSoon) {
        val warnPulse = ((sin(now * 0.014) + 1.0) * 0.5).toFloat()
        val ringColor = if (warnPulse > 0.5f) Color(0xFFFF5A36) else TorchGold
        IsometricTileDrawer.drawRotatedQuad(
            this, path, camera, c.x, c.y, fracU = 0.90f, fracV = 0.90f,
            fillColor = ringColor.copy(alpha = 0.16f + warnPulse * 0.24f),
            strokeColor = ringColor.copy(alpha = 0.55f + warnPulse * 0.42f),
            strokeWidth = 2.8f
        )
    } else if (isPublicOtherLoot) {
        IsometricTileDrawer.drawRotatedQuad(
            this, path, camera, c.x, c.y, fracU = 0.84f, fracV = 0.84f,
            fillColor = ExpEmerald.copy(alpha = 0.12f),
            strokeColor = ExpEmerald.copy(alpha = 0.68f),
            strokeWidth = 2.2f
        )
    } else if (corpse.isDroppedBag) {
        IsometricTileDrawer.drawRotatedQuad(
            this, path, camera, c.x, c.y, fracU = 0.82f, fracV = 0.82f,
            fillColor = TorchGold.copy(alpha = 0.10f),
            strokeColor = TorchGold.copy(alpha = 0.55f),
            strokeWidth = 2.0f
        )
    }

    val size = (spritePx * if (corpse.isDroppedBag) 0.72f else 0.78f).roundToInt()
    drawImage(
        image = EntitySpriteAtlas.corpseBitmap(corpse.isPlayerCorpse || corpse.isDroppedBag),
        dstOffset = IntOffset((c.x - size * 0.5f).roundToInt(), (c.y - size * 0.65f).roundToInt()),
        dstSize = IntSize(size, size),
        filterQuality = FilterQuality.None
    )

    if (ownerLockedForMe) {
        val remMs = corpse.ownerLockRemainingMs(localPlayerId, now)
        val secsLeft = ((remMs + 999L) / 1000L).coerceIn(1L, 5L)
        val rec = overlayBuffer.acquire()
        rec.kind = NativeOverlayKind.EXPIRING_CORPSE
        rec.screenX = c.x
        rec.anchorY = c.y - size * 0.78f
        rec.primaryText = "LOCK ${secsLeft}s"
        rec.secsLeft = secsLeft
        rec.textColorArgb = android.graphics.Color.parseColor("#FFD166")
    } else if (expiringSoon) {
        val remMs = corpse.remainingMs(now) ?: 0L
        val secsLeft = ((remMs + 999L) / 1000L).coerceIn(1L, 15L)
        val rec = overlayBuffer.acquire()
        rec.kind = NativeOverlayKind.EXPIRING_CORPSE
        rec.screenX = c.x
        rec.anchorY = c.y - size * 0.78f
        rec.primaryText = "${secsLeft}s"
        rec.secsLeft = secsLeft
        rec.textColorArgb = if (secsLeft <= 5L) android.graphics.Color.parseColor("#FF6B6B") else android.graphics.Color.parseColor("#FFD166")
    }
}

private fun DrawScope.drawIsometricNpc(
    npc: NpcSpot,
    camera: IsometricCamera,
    path: Path,
    hh: Float,
    spritePx: Int,
    now: Long,
    overlayBuffer: NativeOverlayFrameBuffer,
    quality: GraphicsQuality = GraphicsQuality.DEFAULT
) {
    val c = camera.tileCenterToScreen(npc.x.toFloat(), npc.y.toFloat())
    val feetY = c.y + hh * 0.14f
    VolumetricShading.drawGroundedEntityShadow(
        scope = this,
        camera = camera,
        cx = c.x,
        feetY = feetY,
        radiusScale = 1.06f,
        heightTiles = 0.96f,
        quality = quality
    )
    val heightPx = spritePx * VolumetricShading.HEROIC_HUMANOID_SCALE
    val resolvedFacing = ScreenFacingResolver.resolve(Direction.SOUTH, camera.yawDeg)
    ArticulatedHumanoidRenderer.drawNpc(
        scope = this,
        cx = c.x,
        feetY = feetY,
        heightPx = heightPx,
        role = npc.role,
        facing = resolvedFacing,
        nowMs = now,
        quality = quality,
        path = path
    )
    val rec = overlayBuffer.acquire()
    rec.kind = NativeOverlayKind.NPC
    rec.screenX = c.x
    rec.anchorY = feetY - heightPx
    rec.primaryText = npc.name
    rec.textColorArgb = if (npc.role == NpcRole.ORACLE) {
        android.graphics.Color.parseColor("#FFD447")
    } else {
        android.graphics.Color.parseColor("#EDE3CE")
    }
}

private fun DrawScope.drawIsometricMonster(
    m: MonsterEntity,
    fx: Float,
    fy: Float,
    facingRad: Float,
    state: GameWorldState,
    camera: IsometricCamera,
    path: Path,
    hw: Float,
    hh: Float,
    spritePx: Int,
    now: Long,
    overlayBuffer: NativeOverlayFrameBuffer,
    quality: GraphicsQuality = GraphicsQuality.DEFAULT
) {
    val c = camera.tileCenterToScreen(fx, fy)
    val feetY = c.y + hh * 0.14f
    val isBoss = m.type.isBoss
    val heightScale = OrganicMonsterRenderer.heightScaleFor(m.type)
    val heightPx = spritePx * VolumetricShading.HEROIC_MONSTER_SCALE * heightScale
    val u = heightPx / 46f

    if (m.id == state.targetMonsterId) {
        val pulseAlpha = if (state.autoFollowTarget && ((now / 220L) % 2L) == 0L) 1.0f else 0.78f
        IsometricTileDrawer.drawRotatedQuad(
            this, path, camera, c.x, c.y, fracU = 0.98f, fracV = 0.98f,
            fillColor = TargetRed.copy(alpha = 0.18f),
            strokeColor = TargetRed.copy(alpha = pulseAlpha),
            strokeWidth = if (state.autoFollowTarget) 4.8f else 3.8f
        )
    }

    val moveAge = now - m.lastMoveTimeMs
    val walkPhase = if (moveAge in 0L..280L) ((now + m.id * 97L) / 70f) else 0f
    val attackAge = now - m.lastAttackTimeMs
    val lungeProgress = if (attackAge in 0L..320L) (attackAge.toFloat() / 320f) else 0f
    val lungeEase = if (lungeProgress in 0.001f..0.999f) sin(lungeProgress * PI.toFloat()) else 0f
    val hopLift = if (abs(walkPhase) > 0.01f) abs(sin(walkPhase)) * 0.42f else 0f

    val resolvedFacing = ScreenFacingResolver.resolveFromRadians(facingRad, camera.yawDeg)

    VolumetricShading.drawGroundedEntityShadow(
        scope = this,
        camera = camera,
        cx = c.x,
        feetY = feetY,
        radiusScale = (1.02f * heightScale).coerceIn(0.72f, 1.48f),
        heightTiles = if (isBoss) 1.28f else 0.92f * heightScale,
        jumpLiftRatio = (hopLift + lungeEase * 0.25f).coerceIn(0f, 1f),
        lungeShiftX = resolvedFacing.forwardScreenX * lungeEase * u * 6.0f,
        lungeShiftY = resolvedFacing.forwardDepth * lungeEase * u * 2.8f,
        quality = quality
    )

    OrganicMonsterRenderer.drawMonster(
        scope = this,
        cx = c.x,
        feetY = feetY,
        heightPx = heightPx,
        type = m.type,
        facing = resolvedFacing,
        walkPhase = walkPhase,
        lungeProgress = lungeProgress,
        animSeed = m.id * 37,
        nowMs = now,
        quality = quality,
        path = path
    )

    val hpFrac = (m.hp.toFloat() / m.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val threatBand = m.threatBandFor(state.level)
    val rec = overlayBuffer.acquire()
    rec.kind = NativeOverlayKind.MONSTER
    rec.screenX = c.x
    rec.anchorY = feetY - heightPx
    rec.barWidth = hw * 1.15f
    rec.hpFrac = hpFrac
    rec.hpColor = when {
        hpFrac > 0.6f -> Color(0xFF39C94A)
        hpFrac > 0.3f -> Color(0xFFE6B44C)
        else -> HealthCrimson
    }
    rec.primaryText = "${m.type.displayName} [L${m.level}]"
    rec.isDeadly = threatBand == ThreatBand.DEADLY
    rec.textColorArgb = when (threatBand) {
        ThreatBand.EASY -> android.graphics.Color.parseColor("#4CE06A")
        ThreatBand.EVEN -> android.graphics.Color.parseColor("#EDE3CE")
        ThreatBand.CHALLENGING -> android.graphics.Color.parseColor("#F5C443")
        ThreatBand.DEADLY -> android.graphics.Color.parseColor("#FF4557")
    }
}

private fun DrawScope.drawIsometricRemotePlayer(
    rp: RemotePlayerState,
    fx: Float,
    fy: Float,
    walkPhase: Float,
    facingRad: Float,
    state: GameWorldState,
    camera: IsometricCamera,
    path: Path,
    hw: Float,
    hh: Float,
    spritePx: Int,
    now: Long,
    overlayBuffer: NativeOverlayFrameBuffer,
    quality: GraphicsQuality = GraphicsQuality.DEFAULT
) {
    val c = camera.tileCenterToScreen(fx, fy)
    val feetY = c.y + hh * 0.14f

    if (rp.playerId == state.inspectedPlayerId && now < state.inspectedPlayerUntilMs) {
        val pulseAlpha = if (((now / 180L) % 2L) == 0L) 0.92f else 0.52f
        IsometricTileDrawer.drawRotatedQuad(
            this, path, camera, c.x, c.y, fracU = 0.95f, fracV = 0.95f,
            fillColor = Color(0xFF47D7FF).copy(alpha = 0.16f),
            strokeColor = Color(0xFF47D7FF).copy(alpha = pulseAlpha),
            strokeWidth = 4f
        )
    }

    val heightPx = spritePx * VolumetricShading.HEROIC_HUMANOID_SCALE
    val u = heightPx / 46f
    val actionAge = now - rp.lastActionMs
    val isAttackAction = rp.actionState == PlayerActionState.ATTACKING || rp.actionState == PlayerActionState.CASTING
    val attackProgress = if (isAttackAction && actionAge in 0L..360L) (actionAge.toFloat() / 360f) else 0f
    val atkEase = if (attackProgress in 0.001f..0.999f) sin(attackProgress * PI.toFloat()) else 0f
    val strideLift = if (abs(walkPhase) > 0.01f) abs(sin(walkPhase * PI.toFloat() * 2f)) * 0.35f else 0f
    val resolvedFacing = ScreenFacingResolver.resolveFromRadians(facingRad, camera.yawDeg)

    VolumetricShading.drawGroundedEntityShadow(
        scope = this,
        camera = camera,
        cx = c.x,
        feetY = feetY,
        radiusScale = 1.08f,
        heightTiles = 1.00f,
        jumpLiftRatio = (strideLift + atkEase * 0.22f).coerceIn(0f, 1f),
        lungeShiftX = resolvedFacing.forwardScreenX * atkEase * 4.2f * u,
        lungeShiftY = resolvedFacing.forwardDepth * atkEase * 2.0f * u,
        quality = quality
    )

    val haloCenter = Offset(c.x, feetY - heightPx * 0.42f)
    if (rp.isPoisoned) drawCircle(PoisonGreen.copy(alpha = 0.28f), hw * 0.68f, haloCenter)
    if (rp.isBurning) drawCircle(FireOrange.copy(alpha = 0.34f), hw * 0.72f, haloCenter)

    if (actionAge in 0L..420L && rp.actionState == PlayerActionState.HEALING) {
        val aFrac = 1f - (actionAge.toFloat() / 420f)
        drawCircle(Color(0xFF4CE678).copy(alpha = 0.72f * aFrac), hw * (0.45f + 0.26f * (1f - aFrac)), haloCenter, style = Stroke(3.5f))
    }

    if (rp.isSpectator) return

    val appearance = PaperDollAppearance.fromRemotePlayer(rp)

    ArticulatedHumanoidRenderer.drawPlayer(
        scope = this,
        cx = c.x,
        feetY = feetY,
        heightPx = heightPx,
        appearance = appearance,
        facing = resolvedFacing,
        walkPhase = walkPhase,
        attackProgress = attackProgress,
        nowMs = now,
        quality = quality,
        path = path
    )

    val rHpFrac = (rp.hp.toFloat() / rp.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val rec = overlayBuffer.acquire()
    rec.kind = NativeOverlayKind.REMOTE_PLAYER
    rec.screenX = c.x
    rec.anchorY = feetY - heightPx
    rec.barWidth = hw * 1.18f
    rec.hpFrac = rHpFrac
    rec.hpColor = when {
        rHpFrac > 0.6f -> Color(0xFF47D7FF)
        rHpFrac > 0.3f -> TorchGold
        else -> HealthCrimson
    }
    rec.primaryText = "${rp.name} [Lvl ${rp.level}]"
    rec.isAdmin = rp.isAdmin
    rec.textColorArgb = if (rp.isAdmin) android.graphics.Color.parseColor("#FFE27A") else android.graphics.Color.parseColor("#8CF0FF")
}

private fun DrawScope.drawIsometricLocalPlayer(
    state: GameWorldState,
    fx: Float,
    fy: Float,
    moveFrac: Float,
    facingRad: Float,
    camera: IsometricCamera,
    path: Path,
    ghostLayerPaint: androidx.compose.ui.graphics.Paint,
    hw: Float,
    hh: Float,
    spritePx: Int,
    now: Long,
    overlayBuffer: NativeOverlayFrameBuffer,
    quality: GraphicsQuality = GraphicsQuality.DEFAULT
) {
    val c = camera.tileCenterToScreen(fx, fy)
    val feetY = c.y + hh * 0.14f
    val heightPx = spritePx * VolumetricShading.HEROIC_HUMANOID_SCALE
    val u = heightPx / 46f

    val walkPhase = if (moveFrac < 0.99f) (now / 62f) else 0f
    val attackAge = now - state.lastPlayerAttackMs
    val attackProgress = if (attackAge in 0L..340L) (attackAge.toFloat() / 340f) else 0f
    val atkEase = if (attackProgress in 0.001f..0.999f) sin(attackProgress * PI.toFloat()) else 0f
    val strideLift = if (abs(walkPhase) > 0.01f) abs(sin(walkPhase * PI.toFloat() * 2f)) * 0.35f else 0f
    val resolvedFacing = ScreenFacingResolver.resolveFromRadians(facingRad, camera.yawDeg)

    VolumetricShading.drawGroundedEntityShadow(
        scope = this,
        camera = camera,
        cx = c.x,
        feetY = feetY,
        radiusScale = 1.12f,
        heightTiles = 1.04f,
        jumpLiftRatio = (strideLift + atkEase * 0.22f).coerceIn(0f, 1f),
        lungeShiftX = resolvedFacing.forwardScreenX * atkEase * 4.2f * u,
        lungeShiftY = resolvedFacing.forwardDepth * atkEase * 2.0f * u,
        quality = quality
    )

    val auraCenter = Offset(c.x, feetY - heightPx * 0.42f)
    if (state.isSpectator) {
        drawCircle(Color(0xFFA78BFA).copy(alpha = 0.22f), hw * 0.78f, auraCenter)
        drawCircle(
            color = Color(0xFFA78BFA).copy(alpha = 0.45f),
            radius = hw * 0.64f,
            center = auraCenter,
            style = Stroke(width = 2.2f)
        )
    }
    if (state.poisonTicks > 0) drawCircle(PoisonGreen.copy(alpha = 0.28f), hw * 0.72f, auraCenter)
    if (state.fireTicks > 0) drawCircle(FireOrange.copy(alpha = 0.34f), hw * 0.76f, auraCenter)

    val appearance = PaperDollAppearance.fromEquipment(
        vocation = state.vocation,
        tunicColorIndex = state.tunicColorIndex,
        hairColorIndex = state.hairColorIndex,
        equipment = state.equipment
    )

    if (state.isSpectator) {
        val layerBounds = androidx.compose.ui.geometry.Rect(
            left = c.x - heightPx * 1.4f,
            top = feetY - heightPx * 1.6f,
            right = c.x + heightPx * 1.4f,
            bottom = feetY + heightPx * 0.45f
        )
        drawContext.canvas.saveLayer(layerBounds, ghostLayerPaint)
        try {
            ArticulatedHumanoidRenderer.drawPlayer(
                scope = this,
                cx = c.x,
                feetY = feetY,
                heightPx = heightPx,
                appearance = appearance,
                facing = resolvedFacing,
                walkPhase = walkPhase,
                attackProgress = attackProgress,
                nowMs = now,
                quality = quality,
                path = path
            )
        } finally {
            drawContext.canvas.restore()
        }
    } else {
        ArticulatedHumanoidRenderer.drawPlayer(
            scope = this,
            cx = c.x,
            feetY = feetY,
            heightPx = heightPx,
            appearance = appearance,
            facing = resolvedFacing,
            walkPhase = walkPhase,
            attackProgress = attackProgress,
            nowMs = now,
            quality = quality,
            path = path
        )
    }

    val pHpFrac = (state.hp.toFloat() / state.effectiveMaxHp().coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val rec = overlayBuffer.acquire()
    rec.kind = NativeOverlayKind.LOCAL_PLAYER
    rec.screenX = c.x
    rec.anchorY = feetY - heightPx
    rec.barWidth = hw * 1.22f
    rec.hpFrac = pHpFrac
    rec.hpColor = when {
        state.isInvulnerable -> TorchGold
        pHpFrac > 0.6f -> Color(0xFF39D952)
        pHpFrac > 0.3f -> TorchGold
        else -> HealthCrimson
    }
    rec.primaryText = state.playerName
    rec.isAdmin = state.isAdmin
    rec.textColorArgb = if (state.isAdmin) android.graphics.Color.parseColor("#FFE27A") else android.graphics.Color.parseColor("#EDE3CE")
}

private fun DrawScope.drawIsometricProjectiles(
    state: GameWorldState,
    camera: IsometricCamera,
    hw: Float,
    now: Long,
    quality: GraphicsQuality = GraphicsQuality.DEFAULT,
    outLights: MutableList<ScenePointLight>? = null
) {
    val particleLevel = quality.particleLevel
    state.projectiles.forEach { proj ->
        if (proj.floor != state.floor) return@forEach
        val t = ((now - proj.createdAtMs).toFloat() / proj.durationMs.toFloat()).coerceIn(0f, 1f)
        if (t >= 1f) return@forEach

        // On LOW quality, skip purely decorative ambient gathering particles
        if (particleLevel == ParticleDetailLevel.MINIMAL &&
            (proj.kind == ProjectileKind.SPLASH || proj.kind == ProjectileKind.MINING_SPARK || proj.kind == ProjectileKind.WOOD_CHIP)
        ) {
            return@forEach
        }

        val start = camera.tileCenterToScreen(proj.fromX, proj.fromY)
        val end = camera.tileCenterToScreen(proj.toX, proj.toY)
        val arcLift = sin(t * Math.PI).toFloat() * hw * 0.45f
        val px = start.x + (end.x - start.x) * t
        val py = start.y + (end.y - start.y) * t - hw * 0.35f - arcLift

        if (outLights != null) {
            IsometricLightingEngine.collectProjectilePointLight(proj.kind, px, py, hw, outLights)
        }

        // HD enriched trailing spell wake along flight path
        val prevT = (t - 0.14f).coerceAtLeast(0f)
        val prevArc = sin(prevT * Math.PI).toFloat() * hw * 0.45f
        val trailX = start.x + (end.x - start.x) * prevT
        val trailY = start.y + (end.y - start.y) * prevT - hw * 0.35f - prevArc

        when (proj.kind) {
            ProjectileKind.SPEAR -> {
                drawRect(Color(0xFFD9C29C), Offset(px - 6f, py - 6f), Size(12f, 12f))
                drawRect(Color.White, Offset(px - 2f, py - 2f), Size(4f, 4f))
            }
            ProjectileKind.ARROW -> {
                drawRect(Color(0xFFE6B44C), Offset(px - 5f, py - 5f), Size(10f, 10f))
                drawRect(Color.White, Offset(px - 2f, py - 2f), Size(4f, 4f))
            }
            ProjectileKind.BOLT -> {
                drawRect(Color(0xFF9AC2E6), Offset(px - 6f, py - 6f), Size(12f, 12f))
                drawRect(Color.White, Offset(px - 2f, py - 2f), Size(5f, 5f))
            }
            ProjectileKind.ENERGY_BOLT -> {
                if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                    drawCircle(Color(0xFF4DD2FF).copy(alpha = 0.28f), 19f, Offset(px, py))
                    drawCircle(Color(0xFF4DD2FF).copy(alpha = 0.35f), 7f, Offset(trailX, trailY))
                }
                drawCircle(Color(0xFF4DD2FF), 11f, Offset(px, py))
                drawCircle(Color.White, 5f, Offset(px, py))
            }
            ProjectileKind.ARCANE_DART -> {
                if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                    drawCircle(Color(0xFF75F0FF).copy(alpha = 0.28f), 16f, Offset(px, py))
                    drawCircle(Color(0xFF75F0FF).copy(alpha = 0.35f), 6f, Offset(trailX, trailY))
                }
                drawCircle(Color(0xFF75F0FF), 9f, Offset(px, py))
                drawCircle(Color.White, 4f, Offset(px, py))
            }
            ProjectileKind.FIREBALL_AOE -> {
                if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                    drawCircle(FireOrange.copy(alpha = 0.30f), 22f, Offset(px, py))
                    drawCircle(FireOrange.copy(alpha = 0.42f), 9f, Offset(trailX, trailY))
                }
                drawCircle(TargetRed, 13f, Offset(px, py))
                drawCircle(FireOrange, 9f, Offset(px, py))
                drawCircle(Color(0xFFFFE57F), 5f, Offset(px, py))
                if (particleLevel != ParticleDetailLevel.MINIMAL && t > 0.55f) {
                    val burstFrac = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    val ringRadius = hw * (0.55f + burstFrac * 1.45f)
                    drawCircle(FireOrange.copy(alpha = (1f - burstFrac) * 0.65f), ringRadius, end, style = Stroke(5f * quality.strokeScale))
                    if (particleLevel >= ParticleDetailLevel.FULL) {
                        drawCircle(Color(0xFFFFD447).copy(alpha = (1f - burstFrac) * 0.30f), ringRadius * 0.85f, end)
                    }
                    if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                        drawCircle(Color(0xFFFFF0A6).copy(alpha = (1f - burstFrac) * 0.55f), ringRadius * 1.18f, end, style = Stroke(2.4f))
                    }
                }
            }
            ProjectileKind.HEAL_RUNE -> {
                val alpha = (1f - t).coerceIn(0f, 1f)
                val center = Offset(end.x, end.y - hw * 0.28f)
                drawCircle(Color(0xFF47F08B).copy(alpha = alpha * 0.35f), hw * (0.35f + t * 0.48f), center)
                if (particleLevel != ParticleDetailLevel.MINIMAL) {
                    drawCircle(Color(0xFF75FFE0).copy(alpha = alpha * 0.85f), hw * (0.42f + t * 0.56f), center, style = Stroke(4f * quality.strokeScale))
                }
                if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                    drawCircle(Color(0xFFD6FFF0).copy(alpha = alpha * 0.55f), hw * (0.26f + t * 0.72f), center, style = Stroke(2.2f))
                }
            }
            ProjectileKind.DEATH_RUNE -> {
                if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                    drawCircle(Color(0xFFFF3355).copy(alpha = 0.28f), 22f, Offset(px, py))
                    drawCircle(Color(0xFF2E1018).copy(alpha = 0.55f), 8f, Offset(trailX, trailY))
                }
                drawCircle(Color(0xFF1A0F14), 14f, Offset(px, py))
                drawCircle(Color(0xFFFF3355), 8.5f, Offset(px, py))
                if (particleLevel != ParticleDetailLevel.MINIMAL && t > 0.60f) {
                    val shockFrac = ((t - 0.60f) / 0.40f).coerceIn(0f, 1f)
                    drawCircle(Color(0xFFFF3355).copy(alpha = (1f - shockFrac) * 0.72f), hw * (0.45f + shockFrac * 1.4f), end, style = Stroke(5f * quality.strokeScale))
                    if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                        drawCircle(Color(0xFFFF9EB0).copy(alpha = (1f - shockFrac) * 0.50f), hw * (0.30f + shockFrac * 1.65f), end, style = Stroke(2.2f))
                    }
                }
            }
            ProjectileKind.FIRE_BREATH -> {
                if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                    drawCircle(FireOrange.copy(alpha = 0.30f), 24f + t * 8f, Offset(px, py))
                }
                drawCircle(FireOrange, 15f + t * 7f, Offset(px, py))
                drawCircle(Color(0xFFFFE57F), 7.5f, Offset(px, py))
            }
            ProjectileKind.FIREBALL -> {
                if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                    drawCircle(FireOrange.copy(alpha = 0.30f), 20f, Offset(px, py))
                    drawCircle(FireOrange.copy(alpha = 0.40f), 8f, Offset(trailX, trailY))
                }
                drawCircle(TargetRed, 13f, Offset(px, py))
                drawCircle(FireOrange, 9f, Offset(px, py))
                drawCircle(Color(0xFFFFE57F), 4.5f, Offset(px, py))
            }
            ProjectileKind.THROWN_STONE -> {
                drawRect(Color(0xFF5A5E66), Offset(px - 5f, py - 5f), Size(10f, 10f))
                drawRect(Color(0xFFA8ADB8), Offset(px - 3f, py - 3f), Size(5f, 5f))
            }
            ProjectileKind.NECRO_BOLT -> {
                if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                    drawCircle(Color(0xFFB34BFF).copy(alpha = 0.30f), 20f, Offset(px, py))
                    drawCircle(Color(0xFFB34BFF).copy(alpha = 0.38f), 7f, Offset(trailX, trailY))
                }
                drawCircle(Color(0xFF2E1042), 13f, Offset(px, py))
                drawCircle(Color(0xFFB34BFF), 8.5f, Offset(px, py))
                drawCircle(Color(0xFFFF66A3), 4f, Offset(px, py))
            }
            ProjectileKind.VENOM_GLOB -> {
                if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                    drawCircle(Color(0xFF56FF33).copy(alpha = 0.30f), 21f, Offset(px, py))
                    drawCircle(Color(0xFF56FF33).copy(alpha = 0.40f), 8f, Offset(trailX, trailY))
                }
                drawCircle(Color(0xFF143312), 13.5f, Offset(px, py))
                drawCircle(Color(0xFF52E639), 9.5f, Offset(px, py))
                drawCircle(Color(0xFFD9FF8C), 4.8f, Offset(px, py))
                if (particleLevel != ParticleDetailLevel.MINIMAL && t > 0.62f) {
                    val splashFrac = ((t - 0.62f) / 0.38f).coerceIn(0f, 1f)
                    drawCircle(
                        Color(0xFF7BFF33).copy(alpha = (1f - splashFrac) * 0.70f),
                        hw * (0.40f + splashFrac * 1.15f),
                        end,
                        style = Stroke(4.2f * quality.strokeScale)
                    )
                }
            }
            ProjectileKind.SPLASH -> {
                drawCircle(Color(0xFF8CD9FF).copy(alpha = 1f - t), 7f + t * hw * 0.55f, end, style = Stroke(3.5f * quality.strokeScale))
                if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                    drawCircle(Color(0xFFD9F4FF).copy(alpha = (1f - t) * 0.7f), 4f + t * hw * 0.85f, end, style = Stroke(1.8f))
                }
            }
            ProjectileKind.MINING_SPARK -> {
                val spread = t * hw * 0.48f
                drawRect(TorchGold.copy(alpha = 1f - t), Offset(end.x - spread, end.y - hw * 0.3f - spread), Size(5.5f, 5.5f))
                drawRect(Color.White.copy(alpha = 1f - t), Offset(end.x + spread, end.y - hw * 0.3f - spread * 0.6f), Size(4.5f, 4.5f))
                if (particleLevel >= ParticleDetailLevel.FULL) {
                    drawRect(FireOrange.copy(alpha = 1f - t), Offset(end.x, end.y - hw * 0.3f + spread * 0.7f), Size(5.5f, 5.5f))
                }
                if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                    drawRect(Color(0xFFFFF3B0).copy(alpha = 1f - t), Offset(end.x - spread * 0.4f, end.y - hw * 0.3f + spread), Size(4.5f, 4.5f))
                }
            }
            ProjectileKind.WOOD_CHIP -> {
                val spread = t * hw * 0.52f
                val alpha = (1f - t).coerceIn(0f, 1f)
                drawRect(Color(0xFFC48A47).copy(alpha = alpha), Offset(end.x - spread, end.y - hw * 0.4f - spread * 0.7f), Size(6.5f, 4.5f))
                drawRect(Color(0xFF7D4F24).copy(alpha = alpha), Offset(end.x + spread * 0.85f, end.y - hw * 0.4f - spread * 0.5f), Size(5.5f, 5.5f))
                if (particleLevel >= ParticleDetailLevel.FULL) {
                    drawRect(Color(0xFF4F8A3A).copy(alpha = alpha), Offset(end.x + spread * 0.35f, end.y - hw * 0.4f + spread * 0.4f), Size(5f, 5f))
                }
                if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                    drawRect(Color(0xFF7BC45A).copy(alpha = alpha), Offset(end.x - spread * 0.6f, end.y - hw * 0.4f + spread * 0.5f), Size(4.5f, 4.5f))
                }
            }
        }
    }
}

/**
 * Renders isometric equipment proc AOE effects (shockwave rings, arcane/volcanic novas,
 * chain-arc lightning ribbons, meteor bursts, defensive thorns pulses, and siphon waves),
 * smoothly scaling detail across LOW / MEDIUM / HIGH / HD graphics settings.
 */
private fun DrawScope.drawProcAoeEffects(
    state: GameWorldState,
    camera: IsometricCamera,
    hw: Float,
    hh: Float,
    now: Long,
    quality: GraphicsQuality,
    lights: MutableList<ScenePointLight>?
) {
    val particleLevel = quality.particleLevel
    val strokeScale = quality.strokeScale

    state.procAoeEffects.forEach { fx ->
        if (fx.floor != state.floor) return@forEach
        val t = ((now - fx.createdAtMs).toFloat() / fx.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        if (t >= 1f) return@forEach

        val center = camera.tileCenterToScreen(fx.centerX, fx.centerY)
        val baseColor = Color(fx.elementColor)
        val fade = (1f - t).coerceIn(0f, 1f)
        val expand = 0.25f + t * 0.90f
        val rx = hw * fx.aoeRadiusTiles * expand
        val ry = hh * fx.aoeRadiusTiles * expand

        // Register high-priority dynamic point-light pool on HIGH / HD lighting passes
        if (lights != null && lights.size < 48) {
            lights.add(
                ScenePointLight(
                    screenX = center.x,
                    screenY = center.y,
                    radiusPx = (rx * 1.45f).coerceAtLeast(hw * 1.8f),
                    coreColor = Color.White,
                    outerColor = baseColor,
                    intensity = 0.52f * fade,
                    flickerSeed = (fx.id % 19L).toFloat(),
                    isArcane = fx.visualStyle == ProcVisualStyle.ARCANE_NOVA ||
                        fx.visualStyle == ProcVisualStyle.CHAIN_ARC ||
                        fx.visualStyle == ProcVisualStyle.ACID_SPLASH ||
                        fx.visualStyle == ProcVisualStyle.FROST_SHATTER,
                    priority = ScenePointLight.PRIORITY_COMBAT_FX
                )
            )
        }

        // Isometric ground ellipse helper
        fun drawIsoRing(radiusX: Float, radiusY: Float, color: Color, strokeWidth: Float) {
            drawOval(
                color = color,
                topLeft = Offset(center.x - radiusX, center.y - radiusY),
                size = Size(radiusX * 2f, radiusY * 2f),
                style = Stroke(width = strokeWidth)
            )
        }

        fun drawIsoFill(radiusX: Float, radiusY: Float, color: Color) {
            drawOval(
                color = color,
                topLeft = Offset(center.x - radiusX, center.y - radiusY),
                size = Size(radiusX * 2f, radiusY * 2f)
            )
        }

        when (fx.visualStyle) {
            ProcVisualStyle.SHOCKWAVE_RING -> {
                if (particleLevel != ParticleDetailLevel.MINIMAL) {
                    drawIsoFill(rx * 0.92f, ry * 0.92f, baseColor.copy(alpha = fade * 0.18f))
                }
                drawIsoRing(rx, ry, baseColor.copy(alpha = fade * 0.88f), 4.6f * strokeScale)
                if (particleLevel >= ParticleDetailLevel.MODERATE) {
                    drawIsoRing(rx * 0.68f, ry * 0.68f, Color.White.copy(alpha = fade * 0.55f), 2.4f * strokeScale)
                }
                if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                    drawIsoRing(rx * 1.14f, ry * 1.14f, baseColor.copy(alpha = fade * 0.36f), 2.2f * strokeScale)
                }
            }

            ProcVisualStyle.ARCANE_NOVA -> {
                drawIsoFill(rx, ry, baseColor.copy(alpha = fade * 0.24f))
                drawIsoRing(rx, ry, baseColor.copy(alpha = fade * 0.92f), 5.2f * strokeScale)
                if (particleLevel >= ParticleDetailLevel.MODERATE) {
                    val spokeCount = if (particleLevel == ParticleDetailLevel.HD_ENHANCED) 8 else 6
                    for (i in 0 until spokeCount) {
                        val angle = (i.toFloat() / spokeCount) * (Math.PI.toFloat() * 2f) + t * 1.3f
                        val ex = center.x + kotlin.math.cos(angle) * rx * 0.96f
                        val ey = center.y + kotlin.math.sin(angle) * ry * 0.96f
                        drawLine(
                            color = Color.White.copy(alpha = fade * 0.65f),
                            start = center,
                            end = Offset(ex, ey),
                            strokeWidth = 2.4f * strokeScale,
                            cap = StrokeCap.Round
                        )
                    }
                }
                if (particleLevel == ParticleDetailLevel.HD_ENHANCED) {
                    drawIsoRing(rx * 0.55f, ry * 0.55f, Color(0xFFFFF4D6).copy(alpha = fade * 0.72f), 3.0f)
                }
            }

            ProcVisualStyle.CHAIN_ARC -> {
                drawIsoRing(rx * 0.78f, ry * 0.78f, baseColor.copy(alpha = fade * 0.72f), 3.4f * strokeScale)
                val pts = fx.chainTargets.map { (tx, ty) ->
                    val sPt = camera.tileCenterToScreen(tx, ty)
                    Offset(sPt.x, sPt.y - hh * 0.55f)
                }
                if (pts.size >= 2) {
                    for (i in 0 until pts.size - 1) {
                        val a = pts[i]
                        val b = pts[i + 1]
                        val midX = (a.x + b.x) * 0.5f + ((if ((i + fx.id.toInt()) % 2 == 0) 1 else -1) * hw * 0.22f * fade)
                        val midY = (a.y + b.y) * 0.5f - hh * 0.35f
                        val mid = Offset(midX, midY)
                        drawLine(baseColor.copy(alpha = fade * 0.92f), a, mid, strokeWidth = 4.2f * strokeScale, cap = StrokeCap.Round)
                        drawLine(baseColor.copy(alpha = fade * 0.92f), mid, b, strokeWidth = 4.2f * strokeScale, cap = StrokeCap.Round)
                        if (particleLevel >= ParticleDetailLevel.FULL) {
                            drawLine(Color.White.copy(alpha = fade * 0.85f), a, mid, strokeWidth = 1.8f * strokeScale, cap = StrokeCap.Round)
                            drawLine(Color.White.copy(alpha = fade * 0.85f), mid, b, strokeWidth = 1.8f * strokeScale, cap = StrokeCap.Round)
                        }
                    }
                }
                pts.forEach { p ->
                    drawCircle(baseColor.copy(alpha = fade * 0.65f), hw * 0.24f * (0.6f + t * 0.5f), p)
                    if (particleLevel >= ParticleDetailLevel.MODERATE) {
                        drawCircle(Color.White.copy(alpha = fade * 0.85f), hw * 0.10f, p)
                    }
                }
            }

            ProcVisualStyle.METEOR_BURST -> {
                // Descending streak in early frames + expanding impact crater ring
                if (t < 0.45f) {
                    val dropT = (t / 0.45f).coerceIn(0f, 1f)
                    val skyStart = Offset(center.x - hw * 0.9f * (1f - dropT), center.y - hh * 4.2f * (1f - dropT))
                    drawLine(baseColor.copy(alpha = 0.92f), skyStart, center, strokeWidth = 6.5f * strokeScale, cap = StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.85f), skyStart, center, strokeWidth = 2.6f * strokeScale, cap = StrokeCap.Round)
                }
                drawIsoFill(rx, ry, baseColor.copy(alpha = fade * 0.28f))
                drawIsoRing(rx, ry, baseColor.copy(alpha = fade * 0.95f), 5.5f * strokeScale)
                if (particleLevel >= ParticleDetailLevel.FULL) {
                    val sparkCount = if (particleLevel == ParticleDetailLevel.HD_ENHANCED) 10 else 6
                    for (i in 0 until sparkCount) {
                        val angle = (i.toFloat() / sparkCount) * (Math.PI.toFloat() * 2f)
                        val sx = center.x + kotlin.math.cos(angle) * rx * 0.85f
                        val sy = center.y + kotlin.math.sin(angle) * ry * 0.85f - hh * (1f - t) * 0.9f
                        drawCircle(Color(0xFFFFE57F).copy(alpha = fade * 0.9f), 4.2f * strokeScale, Offset(sx, sy))
                    }
                }
            }

            ProcVisualStyle.THORNS_PULSE -> {
                drawIsoFill(rx * 0.85f, ry * 0.85f, baseColor.copy(alpha = fade * 0.20f))
                drawIsoRing(rx, ry, baseColor.copy(alpha = fade * 0.90f), 4.2f * strokeScale)
                if (particleLevel >= ParticleDetailLevel.MODERATE) {
                    val spikeCount = if (particleLevel == ParticleDetailLevel.HD_ENHANCED) 10 else 6
                    for (i in 0 until spikeCount) {
                        val angle = (i.toFloat() / spikeCount) * (Math.PI.toFloat() * 2f)
                        val ix = center.x + kotlin.math.cos(angle) * rx * 0.55f
                        val iy = center.y + kotlin.math.sin(angle) * ry * 0.55f
                        val ox = center.x + kotlin.math.cos(angle) * rx * 1.12f
                        val oy = center.y + kotlin.math.sin(angle) * ry * 1.12f
                        drawLine(baseColor.copy(alpha = fade * 0.95f), Offset(ix, iy), Offset(ox, oy), strokeWidth = 3.2f * strokeScale, cap = StrokeCap.Round)
                    }
                }
            }

            ProcVisualStyle.SIPHON_WAVE -> {
                // Inward contracting vampiric ring + outer crimson flash
                val invExpand = (1.15f - t * 0.85f).coerceAtLeast(0.2f)
                val irx = hw * fx.aoeRadiusTiles * invExpand
                val iry = hh * fx.aoeRadiusTiles * invExpand
                drawIsoFill(irx * 0.85f, iry * 0.85f, baseColor.copy(alpha = fade * 0.22f))
                drawIsoRing(irx, iry, baseColor.copy(alpha = fade * 0.92f), 4.4f * strokeScale)
                if (particleLevel >= ParticleDetailLevel.MODERATE) {
                    drawIsoRing(rx * 0.72f, ry * 0.72f, Color(0xFFFF9EB8).copy(alpha = fade * 0.55f), 2.4f * strokeScale)
                }
            }

            ProcVisualStyle.ACID_SPLASH -> {
                // Corrosive Mire-Woven venom splash pool + bubbling acid droplets
                drawIsoFill(rx * 0.94f, ry * 0.94f, baseColor.copy(alpha = fade * 0.26f))
                drawIsoRing(rx, ry, baseColor.copy(alpha = fade * 0.90f), 4.2f * strokeScale)
                if (particleLevel >= ParticleDetailLevel.MODERATE) {
                    val drops = if (particleLevel == ParticleDetailLevel.HD_ENHANCED) 8 else 5
                    for (i in 0 until drops) {
                        val angle = (i.toFloat() / drops) * (Math.PI.toFloat() * 2f) + t * 0.9f
                        val dx = center.x + kotlin.math.cos(angle) * rx * 0.78f
                        val dy = center.y + kotlin.math.sin(angle) * ry * 0.78f - hh * (0.25f + t * 0.45f)
                        drawCircle(Color(0xFFA6FF70).copy(alpha = fade * 0.88f), 4.0f * strokeScale, Offset(dx, dy))
                    }
                }
            }

            ProcVisualStyle.FROST_SHATTER -> {
                // Rime-Plate crystalline frost shatter burst + icy hexagonal rays
                drawIsoFill(rx * 0.88f, ry * 0.88f, baseColor.copy(alpha = fade * 0.22f))
                drawIsoRing(rx, ry, baseColor.copy(alpha = fade * 0.94f), 4.6f * strokeScale)
                drawIsoRing(rx * 0.60f, ry * 0.60f, Color(0xFFE8FFFF).copy(alpha = fade * 0.72f), 2.4f * strokeScale)
                if (particleLevel >= ParticleDetailLevel.MODERATE) {
                    val shards = if (particleLevel == ParticleDetailLevel.HD_ENHANCED) 8 else 6
                    for (i in 0 until shards) {
                        val angle = (i.toFloat() / shards) * (Math.PI.toFloat() * 2f)
                        val ix = center.x + kotlin.math.cos(angle) * rx * 0.35f
                        val iy = center.y + kotlin.math.sin(angle) * ry * 0.35f
                        val ox = center.x + kotlin.math.cos(angle) * rx * 1.12f
                        val oy = center.y + kotlin.math.sin(angle) * ry * 1.12f
                        drawLine(Color(0xFFE0FCFF).copy(alpha = fade * 0.92f), Offset(ix, iy), Offset(ox, oy), strokeWidth = 3.0f * strokeScale, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}

