package com.example.ui.screens

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.game.model.CorpseEntity
import com.example.game.model.FloatingTextKind
import com.example.game.model.GameWorldState
import com.example.game.model.GraphicsQuality
import com.example.game.model.MonsterEntity
import com.example.game.model.ParticleDetailLevel
import com.example.game.model.ProjectileKind
import com.example.game.model.ThreatBand
import com.example.game.model.TileType
import com.example.game.model.normalizeRegionId
import com.example.game.multiplayer.PlayerActionState
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.sprites.EntitySpriteAtlas
import com.example.game.world.NpcRole
import com.example.game.world.NpcSpot
import com.example.game.world.WorldMaps
import com.example.ui.theme.FireOrange
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PoisonGreen
import com.example.ui.theme.TargetRed
import com.example.ui.theme.TorchGold
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private sealed class DepthSortableItem(val depth: Float, val subOrder: Int) {
    class TallTile(depth: Float, val tx: Int, val ty: Int, val tile: TileType) : DepthSortableItem(depth, 1)
    class NpcItem(depth: Float, val npc: NpcSpot) : DepthSortableItem(depth, 2)
    class MonsterItem(depth: Float, val monster: MonsterEntity, val fx: Float, val fy: Float) : DepthSortableItem(depth, 3)
    class RemotePlayerItem(depth: Float, val player: RemotePlayerState, val fx: Float, val fy: Float, val moveFrac: Float) : DepthSortableItem(depth, 4)
    class LocalPlayerItem(depth: Float, val fx: Float, val fy: Float, val moveFrac: Float) : DepthSortableItem(depth, 5)
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
    state: GameWorldState,
    remotePlayers: List<RemotePlayerState> = emptyList(),
    graphicsQuality: GraphicsQuality = GraphicsQuality.DEFAULT,
    measureFps: Boolean = false,
    onFpsUpdated: (Int) -> Unit = {},
    cameraYawDeg: Float = IsometricCamera.DEFAULT_YAW_DEG,
    rotateDragMode: Boolean = false,
    onCameraYawDelta: (Float) -> Unit = {},
    onTileTapped: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestState by rememberUpdatedState(state)
    val latestRemotePlayers by rememberUpdatedState(remotePlayers)
    val latestGraphicsQuality by rememberUpdatedState(graphicsQuality)
    val latestMeasureFps by rememberUpdatedState(measureFps)
    val latestOnFpsUpdated by rememberUpdatedState(onFpsUpdated)
    val latestOnTileTapped by rememberUpdatedState(onTileTapped)
    val latestCameraYaw by rememberUpdatedState(cameraYawDeg)
    val latestRotateDragMode by rememberUpdatedState(rotateDragMode)
    val latestOnCameraYawDelta by rememberUpdatedState(onCameraYawDelta)

    var frameUptimeMs by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var targetZoom by remember { mutableFloatStateOf(1.0f) }
    var smoothZoom by remember { mutableFloatStateOf(1.0f) }
    var smoothYaw by remember { mutableFloatStateOf(cameraYawDeg) }

    LaunchedEffect(Unit) {
        var fpsWindowStartMs = SystemClock.uptimeMillis()
        var fpsFrameCount = 0
        while (true) {
            withFrameMillis {
                val nowMs = SystemClock.uptimeMillis()
                frameUptimeMs = nowMs
                if (latestMeasureFps) {
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
                val diff = targetZoom - smoothZoom
                if (abs(diff) > 0.002f) {
                    smoothZoom += diff * 0.22f
                } else {
                    smoothZoom = targetZoom
                }
                val yawDiff = IsometricCamera.shortestAngleDeltaDeg(smoothYaw, latestCameraYaw)
                if (abs(yawDiff) > 0.08f) {
                    smoothYaw = IsometricCamera.normalizeYawDeg(smoothYaw + yawDiff * 0.22f)
                } else {
                    smoothYaw = IsometricCamera.normalizeYawDeg(latestCameraYaw)
                }
            }
        }
    }

    val floatingPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
            isFakeBoldText = true
            textSize = 30f
        }
    }
    val outlinePaint = remember {
        Paint(floatingPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5.5f
            color = android.graphics.Color.BLACK
        }
    }
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
            isFakeBoldText = true
            textSize = 21f
            color = android.graphics.Color.parseColor("#EDE3CE")
        }
    }
    val labelShadowPaint = remember {
        Paint(labelPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = android.graphics.Color.BLACK
        }
    }

    val camera = remember { IsometricCamera() }
    val reusablePath = remember { Path() }
    val reusableTileMatrix = remember { Matrix() }
    val remoteVisualPos = remember { HashMap<String, FloatArray>(16) }
    val activeRemoteIds = remember { HashSet<String>(16) }
    val lerpClockMs = remember { longArrayOf(SystemClock.uptimeMillis()) }
    val reusableTilePaint = remember {
        Paint().apply {
            isFilterBitmap = false // Nearest-neighbor keeps handcrafted pixel-mosaic tiles razor sharp!
            isAntiAlias = false
        }
    }
    val depthQueue = remember { ArrayList<DepthSortableItem>(256) }
    val visibleLights = remember { ArrayList<ScenePointLight>(128) }
    val hdPostProcess = remember { HdPostProcessRenderer() }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianVoid)
                .testTag("game_world_canvas")
                .pointerInput(Unit) {
                    // Unified gesture handler:
                    // - Two-finger twist rotates cameraYaw 360° + pinch zooms 0.6x..1.8x
                    // - Single-finger horizontal drag rotates camera when Rotate-Drag Mode is toggled ON
                    // - Crisp single tap converts inverse-rotated screen coords to exact (x, y) world tile
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
                                    val floorMap = WorldMaps.getFloor(latestState.floor)
                                    val (tx, ty) = camera.screenToWorldTile(lastPos.x, lastPos.y)
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
                                if (latestRotateDragMode && movedTooFar && abs(deltaX) > 0.5f) {
                                    latestOnCameraYawDelta(deltaX * 0.35f)
                                    change.consume()
                                }
                            }
                        }
                    }
                }
        ) {
            val st = latestState
            val quality = latestGraphicsQuality
            val now = frameUptimeMs
            val animFrame = ((now / 420L) % 2L).toInt()
            val floorMap = WorldMaps.getFloor(st.floor)

            val moveFrac = ((now - st.lastPlayerMoveMs).toFloat() / 180f).coerceIn(0f, 1f)
            val camPlayerX = st.prevPlayerX + (st.playerX - st.prevPlayerX) * moveFrac
            val camPlayerY = st.prevPlayerY + (st.playerY - st.prevPlayerY) * moveFrac

            camera.configure(
                viewportW = size.width,
                viewportH = size.height,
                camTileX = camPlayerX,
                camTileY = camPlayerY,
                zoomFactor = smoothZoom,
                cameraYawDeg = smoothYaw
            )

            val bounds = camera.visibleTileBounds(size.width, size.height, floorMap.width, floorMap.height, margin = quality.cullMargin)
            val chunkBounds = floorMap.visibleChunkBounds(bounds.minTx, bounds.maxTx, bounds.minTy, bounds.maxTy)
            val chunkSize = floorMap.chunkedGrid.chunkSize
            val hw = camera.halfW
            val hh = camera.halfH
            val spritePx = (camera.tileW * 0.78f * quality.spriteScaleMultiplier).roundToInt().coerceIn(32, 168)

            depthQueue.clear()
            visibleLights.clear()

            // PASS 1: Chunk-Culled & Rotated-Viewport-Culled 2.5D Terrain Quads (Pixel-Mosaic in High/HD)
            for (cy in chunkBounds.minChunkY..chunkBounds.maxChunkY) {
                val yStart = maxOf(bounds.minTy, cy * chunkSize)
                val yEnd = minOf(bounds.maxTy, (cy + 1) * chunkSize - 1)
                for (cxChunk in chunkBounds.minChunkX..chunkBounds.maxChunkX) {
                    val xStart = maxOf(bounds.minTx, cxChunk * chunkSize)
                    val xEnd = minOf(bounds.maxTx, (cxChunk + 1) * chunkSize - 1)
                    for (ty in yStart..yEnd) {
                        for (tx in xStart..xEnd) {
                            val center = camera.tileCenterToScreen(tx.toFloat(), ty.toFloat())
                            if (center.x + hw * 2.4f < 0f || center.x - hw * 2.4f > size.width ||
                                center.y + hh * 4.5f < 0f || center.y - hh * 4.5f > size.height
                            ) continue

                            val baseTile = floorMap.tileAt(tx, ty)
                            val renderTile = when {
                                baseTile.oreVeinKind() != null &&
                                    st.oreNodes[Triple(st.floor, tx, ty)]?.isDepleted == true -> TileType.DEPLETED_VEIN
                                baseTile == TileType.TREE &&
                                    (st.choppedTrees[Triple(st.floor, tx, ty)] ?: 0) > 0 -> TileType.TREE_STUMP
                                else -> baseTile
                            }

                            IsometricTileDrawer.drawGroundTile(
                                scope = this,
                                path = reusablePath,
                                cam = camera,
                                floorMap = floorMap,
                                tx = tx,
                                ty = ty,
                                tile = renderTile,
                                cx = center.x,
                                cy = center.y,
                                nowMs = now,
                                quality = quality,
                                tileMatrix = reusableTileMatrix,
                                tilePaint = reusableTilePaint
                            )

                            if (quality.drawPointLightPools) {
                                IsometricLightingEngine.collectTilePointLight(
                                    tile = renderTile,
                                    tx = tx,
                                    ty = ty,
                                    cx = center.x,
                                    cy = center.y,
                                    cam = camera,
                                    outLights = visibleLights
                                )
                            }

                            if (IsometricTileDrawer.isTallProp(renderTile)) {
                                depthQueue.add(
                                    DepthSortableItem.TallTile(
                                        depth = camera.depthOf(tx.toFloat(), ty.toFloat()),
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
                        val c = camera.tileCenterToScreen(wx.toFloat(), wy.toFloat())
                        IsometricTileDrawer.drawRotatedQuad(
                            this, reusablePath, camera, c.x, c.y, fracU = 0.36f, fracV = 0.36f,
                            fillColor = TorchGold.copy(alpha = 0.32f),
                            strokeColor = TorchGold.copy(alpha = 0.72f),
                            strokeWidth = 1.6f
                        )
                    }
                }
            }

            val aoePulse = if (((now / 160L) % 2L) == 0L) 0.44f else 0.24f
            st.monsters.forEach { m ->
                if (m.floor == st.floor && m.pendingAoeTiles.isNotEmpty()) {
                    m.pendingAoeTiles.forEach { (ax, ay) ->
                        if (ax in bounds.minTx..bounds.maxTx && ay in bounds.minTy..bounds.maxTy) {
                            val c = camera.tileCenterToScreen(ax.toFloat(), ay.toFloat())
                            IsometricTileDrawer.drawRotatedQuad(
                                this, reusablePath, camera, c.x, c.y, fracU = 0.90f, fracV = 0.90f,
                                fillColor = FireOrange.copy(alpha = aoePulse),
                                strokeColor = TargetRed.copy(alpha = 0.92f),
                                strokeWidth = 3f
                            )
                            IsometricTileDrawer.drawRotatedQuad(
                                this, reusablePath, camera, c.x, c.y, fracU = 0.38f, fracV = 0.38f,
                                fillColor = Color(0xFFFFE066).copy(alpha = aoePulse + 0.25f)
                            )
                        }
                    }
                }
            }

            st.corpses.forEach { corpse ->
                if (corpse.floor == st.floor && corpse.x in bounds.minTx..bounds.maxTx && corpse.y in bounds.minTy..bounds.maxTy) {
                    drawIsometricCorpse(corpse, camera, reusablePath, hw, hh, spritePx, animFrame)
                }
            }

            // Enqueue visible NPCs, Monsters, Remote Players, and Local Player into Dynamic Rotated Depth Queue
            WorldMaps.npcs.forEach { npc ->
                if (npc.floor == st.floor && npc.x in bounds.minTx..bounds.maxTx && npc.y in bounds.minTy..bounds.maxTy) {
                    depthQueue.add(
                        DepthSortableItem.NpcItem(
                            depth = camera.depthOf(npc.x.toFloat(), npc.y.toFloat()),
                            npc = npc
                        )
                    )
                }
            }

            st.monsters.forEach { m ->
                if (m.floor == st.floor && m.x in (bounds.minTx - 1)..(bounds.maxTx + 1) && m.y in (bounds.minTy - 1)..(bounds.maxTy + 1)) {
                    val mFrac = ((now - m.lastMoveTimeMs).toFloat() / 220f).coerceIn(0f, 1f)
                    val mxF = m.lastMoveFromX + (m.x - m.lastMoveFromX) * mFrac
                    val myF = m.lastMoveFromY + (m.y - m.lastMoveFromY) * mFrac
                    depthQueue.add(
                        DepthSortableItem.MonsterItem(
                            depth = camera.depthOf(mxF, myF),
                            monster = m,
                            fx = mxF,
                            fy = myF
                        )
                    )
                }
            }

            val localRegionId = normalizeRegionId(st.regionId)
            val dtSec = ((now - lerpClockMs[0]).coerceIn(1L, 120L)) / 1000f
            lerpClockMs[0] = now
            val smoothAlpha = (1f - exp(-10.5f * dtSec)).coerceIn(0.08f, 1f)
            activeRemoteIds.clear()

            latestRemotePlayers.forEach { rp ->
                if (rp.floor != st.floor || normalizeRegionId(rp.regionId) != localRegionId) return@forEach
                activeRemoteIds.add(rp.playerId)
                val targetX = rp.x.toFloat()
                val targetY = rp.y.toFloat()
                val vis = remoteVisualPos[rp.playerId]
                val visX: Float
                val visY: Float
                if (vis == null || vis[2].toInt() != rp.floor || hypot(targetX - vis[0], targetY - vis[1]) > 12f) {
                    val fresh = floatArrayOf(targetX, targetY, rp.floor.toFloat())
                    remoteVisualPos[rp.playerId] = fresh
                    visX = targetX
                    visY = targetY
                } else {
                    val nx = vis[0] + (targetX - vis[0]) * smoothAlpha
                    val ny = vis[1] + (targetY - vis[1]) * smoothAlpha
                    val snappedX = if (abs(targetX - nx) < 0.015f) targetX else nx
                    val snappedY = if (abs(targetY - ny) < 0.015f) targetY else ny
                    vis[0] = snappedX
                    vis[1] = snappedY
                    visX = snappedX
                    visY = snappedY
                }
                val isGliding = hypot(targetX - visX, targetY - visY) > 0.03f
                val rpMoveFrac = if (isGliding) 0.5f else ((now - rp.lastMoveMs).toFloat() / 190f).coerceIn(0f, 1f)
                if (visX >= bounds.minTx - 2 && visX <= bounds.maxTx + 2 &&
                    visY >= bounds.minTy - 2 && visY <= bounds.maxTy + 2
                ) {
                    depthQueue.add(
                        DepthSortableItem.RemotePlayerItem(
                            depth = camera.depthOf(visX, visY),
                            player = rp,
                            fx = visX,
                            fy = visY,
                            moveFrac = rpMoveFrac
                        )
                    )
                }
            }
            if (remoteVisualPos.size > activeRemoteIds.size) {
                val iter = remoteVisualPos.keys.iterator()
                while (iter.hasNext()) {
                    if (iter.next() !in activeRemoteIds) iter.remove()
                }
            }

            depthQueue.add(
                DepthSortableItem.LocalPlayerItem(
                    depth = camera.depthOf(camPlayerX, camPlayerY),
                    fx = camPlayerX,
                    fy = camPlayerY,
                    moveFrac = moveFrac
                )
            )

            // PASS 3: Dynamic 360-Degree Rotated Painter's Algorithm Depth Sort
            depthQueue.sortWith(compareBy<DepthSortableItem> { it.depth }.thenBy { it.subOrder })

            for (item in depthQueue) {
                when (item) {
                    is DepthSortableItem.TallTile -> {
                        val c = camera.tileCenterToScreen(item.tx.toFloat(), item.ty.toFloat())
                        IsometricTileDrawer.drawTallTileProp(
                            scope = this,
                            path = reusablePath,
                            cam = camera,
                            floorMap = floorMap,
                            tx = item.tx,
                            ty = item.ty,
                            tile = item.tile,
                            cx = c.x,
                            cy = c.y,
                            nowMs = now,
                            quality = quality
                        )
                    }
                    is DepthSortableItem.NpcItem -> {
                        drawIsometricNpc(item.npc, camera, hw, hh, spritePx, animFrame, labelPaint, labelShadowPaint, quality)
                    }
                    is DepthSortableItem.MonsterItem -> {
                        drawIsometricMonster(
                            m = item.monster,
                            fx = item.fx,
                            fy = item.fy,
                            state = st,
                            camera = camera,
                            path = reusablePath,
                            hw = hw,
                            hh = hh,
                            spritePx = spritePx,
                            now = now,
                            labelPaint = labelPaint,
                            labelShadowPaint = labelShadowPaint,
                            quality = quality
                        )
                    }
                    is DepthSortableItem.RemotePlayerItem -> {
                        drawIsometricRemotePlayer(
                            rp = item.player,
                            fx = item.fx,
                            fy = item.fy,
                            moveFrac = item.moveFrac,
                            state = st,
                            camera = camera,
                            path = reusablePath,
                            hw = hw,
                            hh = hh,
                            spritePx = spritePx,
                            animFrame = animFrame,
                            now = now,
                            labelPaint = labelPaint,
                            labelShadowPaint = labelShadowPaint,
                            quality = quality
                        )
                    }
                    is DepthSortableItem.LocalPlayerItem -> {
                        drawIsometricLocalPlayer(
                            state = st,
                            fx = item.fx,
                            fy = item.fy,
                            moveFrac = item.moveFrac,
                            camera = camera,
                            hw = hw,
                            hh = hh,
                            spritePx = spritePx,
                            animFrame = animFrame,
                            now = now,
                            labelPaint = labelPaint,
                            labelShadowPaint = labelShadowPaint,
                            quality = quality
                        )
                    }
                }
            }

            // PASS 3B: Smart Cutaway Pitched Building Roofs (fade translucent as player nears, vanish indoors!)
            if (quality.drawCutawayRoofs) {
                for (b in floorMap.buildings) {
                    if (b.maxX >= bounds.minTx && b.minX <= bounds.maxTx && b.maxY >= bounds.minTy && b.minY <= bounds.maxTy) {
                        IsometricTileDrawer.drawBuildingCutawayRoof(
                            scope = this,
                            path = reusablePath,
                            cam = camera,
                            building = b,
                            playerFx = camPlayerX,
                            playerFy = camPlayerY,
                            quality = quality
                        )
                    }
                }
            }

            // PASS 4: Projectiles, Spells, Splashes & Sparks (also registers moving spell lights!)
            drawIsometricProjectiles(st, camera, hw, now, quality, visibleLights)

            // PASS 5: Moody Twilight Atmospheric Grade & Dynamic Warm/Arcane Point-Light Pools
            if (quality.drawTwilightAtmosphere || quality.drawPointLightPools) {
                IsometricLightingEngine.drawTwilightAndPointLightPass(
                    scope = this,
                    floorId = st.floor,
                    nowMs = now,
                    quality = quality,
                    lights = visibleLights
                )
            }

            // PASS 6: HD Post-Processing (Android 13+ AGSL RuntimeShader with clean Compose gradient fallback)
            if (quality.drawHdVignette) {
                hdPostProcess.drawHdPostProcess(
                    scope = this,
                    quality = quality,
                    floorId = st.floor,
                    nowMs = now
                )
            }

            // PASS 7: Crisp Unshaded Landmarks & Floating Combat / Resource Text (Always 100% legible!)
            WorldMaps.landmarks.forEach { lm ->
                if (lm.floor == st.floor && lm.x in bounds.minTx..bounds.maxTx && lm.y in bounds.minTy..bounds.maxTy) {
                    val c = camera.tileCenterToScreen(lm.x.toFloat(), lm.y.toFloat())
                    val prevSize = labelPaint.textSize
                    val prevShadowSize = labelShadowPaint.textSize
                    labelPaint.textSize = 18f
                    labelShadowPaint.textSize = 18f
                    drawContext.canvas.nativeCanvas.apply {
                        drawText(lm.label, c.x, c.y - hh * 2.1f, labelShadowPaint)
                        drawText(lm.label, c.x, c.y - hh * 2.1f, labelPaint)
                    }
                    labelPaint.textSize = prevSize
                    labelShadowPaint.textSize = prevShadowSize
                }
            }

            st.floatingTexts.forEach { ft ->
                if (ft.floor != st.floor) return@forEach
                val age = (now - ft.createdAtMs).toFloat()
                if (age < 0f || age > 1300f) return@forEach
                val progress = (age / 1300f).coerceIn(0f, 1f)
                val c = camera.tileCenterToScreen(ft.tileX, ft.tileY)
                val fx = c.x
                val fy = c.y - spritePx * 0.72f - progress * (hh * 2.2f)
                val alpha = ((1f - progress * 0.85f) * 255).toInt().coerceIn(0, 255)

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
                    FloatingTextKind.INFO -> android.graphics.Color.parseColor("#EDE3CE")
                }
                floatingPaint.alpha = alpha
                outlinePaint.alpha = alpha
                drawContext.canvas.nativeCanvas.apply {
                    drawText(ft.text, fx, fy, outlinePaint)
                    drawText(ft.text, fx, fy, floatingPaint)
                }
            }
        }
    }
}

private fun DrawScope.drawIsometricCorpse(
    corpse: CorpseEntity,
    camera: IsometricCamera,
    path: Path,
    hw: Float,
    hh: Float,
    spritePx: Int,
    animFrame: Int
) {
    val c = camera.tileCenterToScreen(corpse.x.toFloat(), corpse.y.toFloat())
    if (corpse.isPlayerCorpse) {
        val pulse = if (animFrame == 0) 0.65f else 0.30f
        IsometricTileDrawer.drawRotatedQuad(
            this, path, camera, c.x, c.y, fracU = 0.88f, fracV = 0.88f,
            fillColor = TorchGold.copy(alpha = pulse * 0.22f),
            strokeColor = TorchGold.copy(alpha = pulse),
            strokeWidth = 3.2f
        )
    }
    val size = (spritePx * 0.78f).roundToInt()
    drawImage(
        image = EntitySpriteAtlas.corpseBitmap(corpse.isPlayerCorpse),
        dstOffset = IntOffset((c.x - size * 0.5f).roundToInt(), (c.y - size * 0.65f).roundToInt()),
        dstSize = IntSize(size, size),
        filterQuality = FilterQuality.None
    )
}

private fun DrawScope.drawIsometricNpc(
    npc: NpcSpot,
    camera: IsometricCamera,
    hw: Float,
    hh: Float,
    spritePx: Int,
    animFrame: Int,
    labelPaint: Paint,
    labelShadowPaint: Paint,
    quality: GraphicsQuality = GraphicsQuality.DEFAULT
) {
    val c = camera.tileCenterToScreen(npc.x.toFloat(), npc.y.toFloat())
    if (quality.drawCastShadows) {
        IsometricLightingEngine.drawAngledCastShadow(this, camera, c.x, c.y + hh * 0.10f, radiusScale = 0.96f, heightTiles = 0.85f, quality = quality)
    } else {
        IsometricTileDrawer.drawContactShadow(this, c.x, c.y + hh * 0.10f, hw, hh, quality = quality)
    }
    if (npc.role == NpcRole.ORACLE && quality != GraphicsQuality.LOW) {
        val auraAlpha = if (animFrame == 0) 0.36f else 0.18f
        drawCircle(
            color = TorchGold.copy(alpha = auraAlpha),
            radius = hw * 0.75f,
            center = Offset(c.x, c.y - spritePx * 0.35f)
        )
    }
    val drawX = (c.x - spritePx * 0.5f).roundToInt()
    val drawY = (c.y - spritePx * 0.84f).roundToInt()
    drawImage(
        image = EntitySpriteAtlas.npcBitmap(npc.role),
        dstOffset = IntOffset(drawX, drawY),
        dstSize = IntSize(spritePx, spritePx),
        filterQuality = FilterQuality.None
    )
    val prevColor = labelPaint.color
    if (npc.role == NpcRole.ORACLE) {
        labelPaint.color = android.graphics.Color.parseColor("#FFD447")
    }
    drawContext.canvas.nativeCanvas.apply {
        drawText(npc.name, c.x, drawY - 6f, labelShadowPaint)
        drawText(npc.name, c.x, drawY - 6f, labelPaint)
    }
    labelPaint.color = prevColor
}

private fun DrawScope.drawIsometricMonster(
    m: MonsterEntity,
    fx: Float,
    fy: Float,
    state: GameWorldState,
    camera: IsometricCamera,
    path: Path,
    hw: Float,
    hh: Float,
    spritePx: Int,
    now: Long,
    labelPaint: Paint,
    labelShadowPaint: Paint,
    quality: GraphicsQuality = GraphicsQuality.DEFAULT
) {
    val c = camera.tileCenterToScreen(fx, fy)
    val isBoss = m.type.isBoss
    val drawSize = if (isBoss) (spritePx * 1.28f).roundToInt() else spritePx

    if (m.id == state.targetMonsterId) {
        val pulseAlpha = if (state.autoFollowTarget && ((now / 220L) % 2L) == 0L) 1.0f else 0.78f
        IsometricTileDrawer.drawRotatedQuad(
            this, path, camera, c.x, c.y, fracU = 0.96f, fracV = 0.96f,
            fillColor = TargetRed.copy(alpha = 0.18f),
            strokeColor = TargetRed.copy(alpha = pulseAlpha),
            strokeWidth = if (state.autoFollowTarget) 4.8f else 3.8f
        )
    }

    if (quality.drawCastShadows) {
        IsometricLightingEngine.drawAngledCastShadow(
            this, camera, c.x, c.y + hh * 0.10f,
            radiusScale = if (isBoss) 1.32f else 0.96f,
            heightTiles = if (isBoss) 1.15f else 0.80f,
            quality = quality
        )
    } else {
        IsometricTileDrawer.drawContactShadow(this, c.x, c.y + hh * 0.10f, hw, hh, scale = if (isBoss) 1.3f else 0.96f, quality = quality)
    }

    val mFrame = (((now + (m.id * 170L)) / 420L) % 2L).toInt()
    val drawX = (c.x - drawSize * 0.5f).roundToInt()
    val drawY = (c.y - drawSize * 0.84f).roundToInt()
    drawImage(
        image = EntitySpriteAtlas.monsterBitmap(m.type, mFrame),
        dstOffset = IntOffset(drawX, drawY),
        dstSize = IntSize(drawSize, drawSize),
        filterQuality = FilterQuality.None
    )

    val hpFrac = (m.hp.toFloat() / m.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val barW = hw * 1.15f
    val barX = c.x - barW * 0.5f
    val barY = drawY - 10f
    drawRect(Color.Black, Offset(barX - 1f, barY - 1f), Size(barW + 2f, 8f))
    val hpColor = when {
        hpFrac > 0.6f -> Color(0xFF39C94A)
        hpFrac > 0.3f -> Color(0xFFE6B44C)
        else -> HealthCrimson
    }
    drawRect(hpColor, Offset(barX, barY), Size(barW * hpFrac, 6f))

    val threatBand = m.type.threatBandFor(state.level)
    val prevColor = labelPaint.color
    labelPaint.color = when (threatBand) {
        ThreatBand.EASY -> android.graphics.Color.parseColor("#4CE06A")
        ThreatBand.EVEN -> android.graphics.Color.parseColor("#EDE3CE")
        ThreatBand.CHALLENGING -> android.graphics.Color.parseColor("#F5C443")
        ThreatBand.DEADLY -> android.graphics.Color.parseColor("#FF4557")
    }
    val textY = barY - 5f
    val textWidth = labelPaint.measureText(m.type.displayName)
    if (threatBand == ThreatBand.DEADLY) {
        val skullPx = 2.0f
        val skullX = c.x - textWidth * 0.5f - skullPx * 7.5f
        val skullY = textY - skullPx * 7.0f
        drawRect(Color.Black, Offset(skullX - 1f, skullY - 1f), Size(skullPx * 6f + 2f, skullPx * 6f + 2f))
        drawRect(Color(0xFFF3EFE6), Offset(skullX + skullPx, skullY), Size(skullPx * 4f, skullPx * 4f))
        drawRect(Color(0xFFF3EFE6), Offset(skullX, skullY + skullPx), Size(skullPx * 6f, skullPx * 2.5f))
        drawRect(Color(0xFFF3EFE6), Offset(skullX + skullPx * 1.5f, skullY + skullPx * 4f), Size(skullPx * 3f, skullPx * 1.8f))
        drawRect(Color(0xFFB71C1C), Offset(skullX + skullPx * 1.1f, skullY + skullPx * 1.6f), Size(skullPx * 1.2f, skullPx * 1.2f))
        drawRect(Color(0xFFB71C1C), Offset(skullX + skullPx * 3.7f, skullY + skullPx * 1.6f), Size(skullPx * 1.2f, skullPx * 1.2f))
    }
    drawContext.canvas.nativeCanvas.apply {
        drawText(m.type.displayName, c.x, textY, labelShadowPaint)
        drawText(m.type.displayName, c.x, textY, labelPaint)
    }
    labelPaint.color = prevColor
}

private fun DrawScope.drawIsometricRemotePlayer(
    rp: RemotePlayerState,
    fx: Float,
    fy: Float,
    moveFrac: Float,
    state: GameWorldState,
    camera: IsometricCamera,
    path: Path,
    hw: Float,
    hh: Float,
    spritePx: Int,
    animFrame: Int,
    now: Long,
    labelPaint: Paint,
    labelShadowPaint: Paint,
    quality: GraphicsQuality = GraphicsQuality.DEFAULT
) {
    val c = camera.tileCenterToScreen(fx, fy)
    val rpFrame = if (moveFrac < 1f) ((now / 110L) % 2L).toInt() else animFrame

    if (rp.playerId == state.inspectedPlayerId && now < state.inspectedPlayerUntilMs) {
        val pulseAlpha = if (((now / 180L) % 2L) == 0L) 0.92f else 0.52f
        IsometricTileDrawer.drawRotatedQuad(
            this, path, camera, c.x, c.y, fracU = 0.92f, fracV = 0.92f,
            fillColor = Color(0xFF47D7FF).copy(alpha = 0.16f),
            strokeColor = Color(0xFF47D7FF).copy(alpha = pulseAlpha),
            strokeWidth = 4f
        )
    }

    if (quality.drawCastShadows) {
        IsometricLightingEngine.drawAngledCastShadow(this, camera, c.x, c.y + hh * 0.10f, radiusScale = 1.0f, heightTiles = 0.88f, quality = quality)
    } else {
        IsometricTileDrawer.drawContactShadow(this, c.x, c.y + hh * 0.10f, hw, hh, quality = quality)
    }

    val haloCenter = Offset(c.x, c.y - spritePx * 0.35f)
    if (rp.isPoisoned) drawCircle(PoisonGreen.copy(alpha = 0.28f), hw * 0.65f, haloCenter)
    if (rp.isBurning) drawCircle(FireOrange.copy(alpha = 0.34f), hw * 0.68f, haloCenter)

    val actionAge = now - rp.lastActionMs
    if (actionAge in 0L..420L) {
        val aFrac = 1f - (actionAge.toFloat() / 420f)
        when (rp.actionState) {
            PlayerActionState.ATTACKING -> drawCircle(Color(0xFFFFE082).copy(alpha = 0.65f * aFrac), hw * (0.30f + 0.18f * (1f - aFrac)), haloCenter, style = Stroke(3.5f))
            PlayerActionState.CASTING -> drawCircle(Color(0xFF5CE1FF).copy(alpha = 0.72f * aFrac), hw * (0.45f + 0.22f * (1f - aFrac)), haloCenter, style = Stroke(3.5f))
            PlayerActionState.HEALING -> drawCircle(Color(0xFF4CE678).copy(alpha = 0.72f * aFrac), hw * (0.42f + 0.24f * (1f - aFrac)), haloCenter, style = Stroke(3.5f))
            else -> Unit
        }
    }

    val drawX = (c.x - spritePx * 0.5f).roundToInt()
    val drawY = (c.y - spritePx * 0.85f).roundToInt()
    drawImage(
        image = EntitySpriteAtlas.playerBitmap(rp.vocation, rpFrame, rp.tunicColorIndex, rp.hairColorIndex, rp.direction),
        dstOffset = IntOffset(drawX, drawY),
        dstSize = IntSize(spritePx, spritePx),
        filterQuality = FilterQuality.None
    )

    val rHpFrac = (rp.hp.toFloat() / rp.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val rBarW = hw * 1.18f
    val rBarX = c.x - rBarW * 0.5f
    val rBarY = drawY - 10f
    drawRect(Color.Black, Offset(rBarX - 1f, rBarY - 1f), Size(rBarW + 2f, 8f))
    drawRect(
        when {
            rHpFrac > 0.6f -> Color(0xFF47D7FF)
            rHpFrac > 0.3f -> TorchGold
            else -> HealthCrimson
        },
        Offset(rBarX, rBarY),
        Size(rBarW * rHpFrac, 6f)
    )

    val prevColor = labelPaint.color
    labelPaint.color = android.graphics.Color.parseColor("#8CF0FF")
    val tag = "${rp.name} [Lvl ${rp.level}]"
    val tagWidth = labelPaint.measureText(tag)
    val pipCenter = Offset(c.x - tagWidth * 0.5f - 9f, rBarY - 12f)
    drawCircle(Color.Black, radius = 4.6f, center = pipCenter)
    drawCircle(TorchGold, radius = 3.6f, center = pipCenter)
    drawCircle(Color(0xFF47D7FF), radius = 2.4f, center = pipCenter)
    drawContext.canvas.nativeCanvas.apply {
        drawText(tag, c.x, rBarY - 5f, labelShadowPaint)
        drawText(tag, c.x, rBarY - 5f, labelPaint)
    }
    labelPaint.color = prevColor
}

private fun DrawScope.drawIsometricLocalPlayer(
    state: GameWorldState,
    fx: Float,
    fy: Float,
    moveFrac: Float,
    camera: IsometricCamera,
    hw: Float,
    hh: Float,
    spritePx: Int,
    animFrame: Int,
    now: Long,
    labelPaint: Paint,
    labelShadowPaint: Paint,
    quality: GraphicsQuality = GraphicsQuality.DEFAULT
) {
    val c = camera.tileCenterToScreen(fx, fy)
    val playerFrame = if (moveFrac < 1f) ((now / 110L) % 2L).toInt() else animFrame

    if (quality.drawCastShadows) {
        IsometricLightingEngine.drawAngledCastShadow(this, camera, c.x, c.y + hh * 0.10f, radiusScale = 1.05f, heightTiles = 0.92f, quality = quality)
    } else {
        IsometricTileDrawer.drawContactShadow(this, c.x, c.y + hh * 0.10f, hw, hh, scale = 1.05f, quality = quality)
    }

    val auraCenter = Offset(c.x, c.y - spritePx * 0.35f)
    if (state.poisonTicks > 0) drawCircle(PoisonGreen.copy(alpha = 0.28f), hw * 0.68f, auraCenter)
    if (state.fireTicks > 0) drawCircle(FireOrange.copy(alpha = 0.34f), hw * 0.72f, auraCenter)

    val drawX = (c.x - spritePx * 0.5f).roundToInt()
    val drawY = (c.y - spritePx * 0.86f).roundToInt()
    drawImage(
        image = EntitySpriteAtlas.playerBitmap(
            vocation = state.vocation,
            frame = playerFrame,
            tunicColorIndex = state.tunicColorIndex,
            hairColorIndex = state.hairColorIndex,
            direction = state.playerFacing
        ),
        dstOffset = IntOffset(drawX, drawY),
        dstSize = IntSize(spritePx, spritePx),
        filterQuality = FilterQuality.None
    )

    val pHpFrac = (state.hp.toFloat() / state.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val pBarW = hw * 1.22f
    val pBarX = c.x - pBarW * 0.5f
    val pBarY = drawY - 10f
    drawRect(Color.Black, Offset(pBarX - 1f, pBarY - 1f), Size(pBarW + 2f, 8f))
    drawRect(
        when {
            pHpFrac > 0.6f -> Color(0xFF39D952)
            pHpFrac > 0.3f -> TorchGold
            else -> HealthCrimson
        },
        Offset(pBarX, pBarY),
        Size(pBarW * pHpFrac, 6f)
    )
    drawContext.canvas.nativeCanvas.apply {
        drawText(state.playerName, c.x, pBarY - 5f, labelShadowPaint)
        drawText(state.playerName, c.x, pBarY - 5f, labelPaint)
    }
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

        if (quality.drawPointLightPools && outLights != null) {
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
