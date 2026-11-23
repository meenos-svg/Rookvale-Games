package com.example.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.game.model.Direction
import com.example.game.model.FloatingTextKind
import com.example.game.model.GameWorldState
import com.example.game.model.ProjectileKind
import com.example.game.model.ThreatBand
import com.example.game.model.TileType
import com.example.game.multiplayer.PlayerActionState
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.sprites.EntitySpriteAtlas
import com.example.game.sprites.TileSpriteAtlas
import com.example.game.world.NpcRole
import com.example.game.world.WorldMaps
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.FireOrange
import com.example.ui.theme.HealthCrimson
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PoisonGreen
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.TargetRed
import com.example.ui.theme.TorchGold
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * High-FPS Compose Canvas rendering per-floor 96x96 overland & 48x48 dungeon grids
 * using procedural 32x32 ImageBitmap sprites with FilterQuality.None.
 */
@Composable
fun GameCanvasView(
    state: GameWorldState,
    remotePlayers: List<RemotePlayerState> = emptyList(),
    onTileTapped: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestState by rememberUpdatedState(state)
    val latestRemotePlayers by rememberUpdatedState(remotePlayers)
    val latestOnTileTapped by rememberUpdatedState(onTileTapped)

    var frameUptimeMs by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frameUptimeMs = SystemClock.uptimeMillis() }
        }
    }

    val floatingPaint = remember {
        Paint().apply {
            isAntiAlias = false
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
            isFakeBoldText = true
            textSize = 32f
        }
    }
    val outlinePaint = remember {
        Paint(floatingPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = android.graphics.Color.BLACK
        }
    }
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = false
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
            isFakeBoldText = true
            textSize = 23f
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

    val cameraMetrics = remember { CameraMetrics() }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianVoid)
                .testTag("game_world_canvas")
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val tilePx = cameraMetrics.tilePx
                        if (tilePx <= 0f) return@detectTapGestures
                        val floorMap = WorldMaps.getFloor(latestState.floor)
                        val tx = floor((tapOffset.x - cameraMetrics.originX) / tilePx).toInt()
                        val ty = floor((tapOffset.y - cameraMetrics.originY) / tilePx).toInt()
                        if (tx in 0 until floorMap.width && ty in 0 until floorMap.height) {
                            latestOnTileTapped(tx, ty)
                        }
                    }
                }
        ) {
            val st = latestState
            val now = frameUptimeMs
            val animFrame = ((now / 420L) % 2L).toInt()

            val scale = (size.width / (32f * 9.5f)).roundToInt().coerceIn(2, 5)
            val tilePx = (32 * scale).toFloat()
            val tileInt = 32 * scale

            val moveFrac = ((now - st.lastPlayerMoveMs).toFloat() / 180f).coerceIn(0f, 1f)
            val camPlayerX = st.prevPlayerX + (st.playerX - st.prevPlayerX) * moveFrac
            val camPlayerY = st.prevPlayerY + (st.playerY - st.prevPlayerY) * moveFrac

            val originX = size.width * 0.5f - (camPlayerX + 0.5f) * tilePx
            val originY = size.height * 0.5f - (camPlayerY + 0.5f) * tilePx
            cameraMetrics.tilePx = tilePx
            cameraMetrics.originX = originX
            cameraMetrics.originY = originY

            val floorMap = WorldMaps.getFloor(st.floor)
            val minTx = floor(-originX / tilePx).toInt().coerceIn(0, floorMap.width - 1)
            val maxTx = floor((size.width - originX) / tilePx).toInt().coerceIn(0, floorMap.width - 1)
            val minTy = floor(-originY / tilePx).toInt().coerceIn(0, floorMap.height - 1)
            val maxTy = floor((size.height - originY) / tilePx).toInt().coerceIn(0, floorMap.height - 1)

            // 1. Viewport-culled Terrain + Live Ore Depletion
            for (ty in minTy..maxTy) {
                val py = (originY + ty * tilePx).roundToInt()
                for (tx in minTx..maxTx) {
                    val px = (originX + tx * tilePx).roundToInt()
                    val baseTile = floorMap.tileAt(tx, ty)
                    val renderTile = when {
                        baseTile.oreVeinKind() != null &&
                            st.oreNodes[Triple(st.floor, tx, ty)]?.isDepleted == true -> TileType.DEPLETED_VEIN
                        baseTile == TileType.TREE &&
                            (st.choppedTrees[Triple(st.floor, tx, ty)] ?: 0) > 0 -> TileType.TREE_STUMP
                        else -> baseTile
                    }
                    drawImage(
                        image = TileSpriteAtlas.tileBitmap(renderTile, animFrame),
                        dstOffset = IntOffset(px, py),
                        dstSize = IntSize(tileInt, tileInt),
                        filterQuality = FilterQuality.None
                    )
                }
            }

            // Walk path breadcrumbs
            if (st.walkPath.isNotEmpty()) {
                st.walkPath.forEach { (wx, wy) ->
                    if (wx in minTx..maxTx && wy in minTy..maxTy) {
                        drawRect(
                            color = TorchGold.copy(alpha = 0.25f),
                            topLeft = Offset(originX + wx * tilePx + tilePx * 0.35f, originY + wy * tilePx + tilePx * 0.35f),
                            size = Size(tilePx * 0.3f, tilePx * 0.3f)
                        )
                    }
                }
            }

            // 2. Corpses
            st.corpses.forEach { corpse ->
                if (corpse.floor == st.floor && corpse.x in minTx..maxTx && corpse.y in minTy..maxTy) {
                    val cx = (originX + corpse.x * tilePx).roundToInt()
                    val cy = (originY + corpse.y * tilePx).roundToInt()
                    if (corpse.isPlayerCorpse) {
                        val pulse = if (animFrame == 0) 0.55f else 0.28f
                        drawRect(TorchGold.copy(alpha = pulse), Offset(cx.toFloat(), cy.toFloat()), Size(tilePx, tilePx), style = Stroke(width = 4f))
                    }
                    drawImage(
                        image = EntitySpriteAtlas.corpseBitmap(corpse.isPlayerCorpse),
                        dstOffset = IntOffset(cx, cy),
                        dstSize = IntSize(tileInt, tileInt),
                        filterQuality = FilterQuality.None
                    )
                }
            }

            // 3. NPCs (including golden-aura Oracle statue in Rookvale Temple)
            WorldMaps.npcs.forEach { npc ->
                if (npc.floor == st.floor && npc.x in minTx..maxTx && npc.y in minTy..maxTy) {
                    val nx = (originX + npc.x * tilePx).roundToInt()
                    val ny = (originY + npc.y * tilePx).roundToInt()
                    if (npc.role == NpcRole.ORACLE) {
                        val auraAlpha = if (animFrame == 0) 0.34f else 0.18f
                        drawCircle(
                            color = TorchGold.copy(alpha = auraAlpha),
                            radius = tilePx * 0.54f,
                            center = Offset(nx + tilePx * 0.5f, ny + tilePx * 0.55f)
                        )
                    }
                    drawImage(
                        image = EntitySpriteAtlas.npcBitmap(npc.role),
                        dstOffset = IntOffset(nx, ny),
                        dstSize = IntSize(tileInt, tileInt),
                        filterQuality = FilterQuality.None
                    )
                    val prevColor = labelPaint.color
                    if (npc.role == NpcRole.ORACLE) {
                        labelPaint.color = android.graphics.Color.parseColor("#FFD447")
                    }
                    drawContext.canvas.nativeCanvas.apply {
                        drawText(npc.name, nx + tilePx * 0.5f, ny - 6f, labelShadowPaint)
                        drawText(npc.name, nx + tilePx * 0.5f, ny - 6f, labelPaint)
                    }
                    labelPaint.color = prevColor
                }
            }

            // 3.5 Telegraphed Boss Fire Wave AoE Warning Tiles
            val aoePulse = if (((now / 160L) % 2L) == 0L) 0.44f else 0.24f
            st.monsters.forEach { m ->
                if (m.floor == st.floor && m.pendingAoeTiles.isNotEmpty()) {
                    m.pendingAoeTiles.forEach { (ax, ay) ->
                        if (ax in minTx..maxTx && ay in minTy..maxTy) {
                            val left = originX + ax * tilePx
                            val top = originY + ay * tilePx
                            drawRect(
                                color = FireOrange.copy(alpha = aoePulse),
                                topLeft = Offset(left + 2f, top + 2f),
                                size = Size(tilePx - 4f, tilePx - 4f)
                            )
                            drawRect(
                                color = TargetRed.copy(alpha = 0.90f),
                                topLeft = Offset(left + 2f, top + 2f),
                                size = Size(tilePx - 4f, tilePx - 4f),
                                style = Stroke(width = 3.5f)
                            )
                            drawRect(
                                color = Color(0xFFFFE066).copy(alpha = aoePulse + 0.25f),
                                topLeft = Offset(left + tilePx * 0.36f, top + tilePx * 0.36f),
                                size = Size(tilePx * 0.28f, tilePx * 0.28f)
                            )
                        }
                    }
                }
            }

            // 4. Monsters (with color-coded threat nameplates + pixel skull for Deadly/Boss foes)
            st.monsters.forEach { m ->
                if (m.floor == st.floor && m.x in (minTx - 1)..(maxTx + 1) && m.y in (minTy - 1)..(maxTy + 1)) {
                    val mFrac = ((now - m.lastMoveTimeMs).toFloat() / 220f).coerceIn(0f, 1f)
                    val mxF = m.lastMoveFromX + (m.x - m.lastMoveFromX) * mFrac
                    val myF = m.lastMoveFromY + (m.y - m.lastMoveFromY) * mFrac
                    val sx = (originX + mxF * tilePx).roundToInt()
                    val sy = (originY + myF * tilePx).roundToInt()

                    if (m.id == st.targetMonsterId) {
                        val pulseAlpha = if (st.autoFollowTarget && ((now / 220L) % 2L) == 0L) 1.0f else 0.78f
                        val left = sx.toFloat() + 1f
                        val top = sy.toFloat() + 1f
                        val boxSize = tilePx - 2f
                        drawRect(
                            color = TargetRed.copy(alpha = pulseAlpha),
                            topLeft = Offset(left, top),
                            size = Size(boxSize, boxSize),
                            style = Stroke(width = if (st.autoFollowTarget) 5.5f else 4.5f)
                        )
                        // Crisp L-shaped corner brackets for high-visibility combat lock
                        val corner = tilePx * 0.24f
                        val cornerColor = if (st.autoFollowTarget) Color(0xFFFF7A7A) else TargetRed
                        val strokeW = 4f
                        drawLine(cornerColor, Offset(left, top), Offset(left + corner, top), strokeWidth = strokeW)
                        drawLine(cornerColor, Offset(left, top), Offset(left, top + corner), strokeWidth = strokeW)
                        drawLine(cornerColor, Offset(left + boxSize, top), Offset(left + boxSize - corner, top), strokeWidth = strokeW)
                        drawLine(cornerColor, Offset(left + boxSize, top), Offset(left + boxSize, top + corner), strokeWidth = strokeW)
                        drawLine(cornerColor, Offset(left, top + boxSize), Offset(left + corner, top + boxSize), strokeWidth = strokeW)
                        drawLine(cornerColor, Offset(left, top + boxSize), Offset(left, top + boxSize - corner), strokeWidth = strokeW)
                        drawLine(cornerColor, Offset(left + boxSize, top + boxSize), Offset(left + boxSize - corner, top + boxSize), strokeWidth = strokeW)
                        drawLine(cornerColor, Offset(left + boxSize, top + boxSize), Offset(left + boxSize, top + boxSize - corner), strokeWidth = strokeW)
                    }

                    val mFrame = (((now + (m.id * 170L)) / 420L) % 2L).toInt()
                    val drawSize = if (m.type.isBoss) (tileInt * 1.25f).roundToInt() else tileInt
                    val drawOffsetX = if (m.type.isBoss) sx - (drawSize - tileInt) / 2 else sx
                    val drawOffsetY = if (m.type.isBoss) sy - (drawSize - tileInt) else sy

                    drawImage(
                        image = EntitySpriteAtlas.monsterBitmap(m.type, mFrame),
                        dstOffset = IntOffset(drawOffsetX, drawOffsetY),
                        dstSize = IntSize(drawSize, drawSize),
                        filterQuality = FilterQuality.None
                    )

                    val hpFrac = (m.hp.toFloat() / m.maxHp.toFloat()).coerceIn(0f, 1f)
                    val barW = tilePx * 0.86f
                    val barX = sx + (tilePx - barW) * 0.5f
                    val barY = drawOffsetY - 12f
                    drawRect(Color.Black, Offset(barX - 1f, barY - 1f), Size(barW + 2f, 9f))
                    val hpColor = when {
                        hpFrac > 0.6f -> Color(0xFF39C94A)
                        hpFrac > 0.3f -> Color(0xFFE6B44C)
                        else -> HealthCrimson
                    }
                    drawRect(hpColor, Offset(barX, barY), Size(barW * hpFrac, 7f))

                    val threatBand = m.type.threatBandFor(st.level)
                    val prevColor = labelPaint.color
                    labelPaint.color = when (threatBand) {
                        ThreatBand.EASY -> android.graphics.Color.parseColor("#4CE06A")
                        ThreatBand.EVEN -> android.graphics.Color.parseColor("#EDE3CE")
                        ThreatBand.CHALLENGING -> android.graphics.Color.parseColor("#F5C443")
                        ThreatBand.DEADLY -> android.graphics.Color.parseColor("#FF4557")
                    }
                    val centerX = sx + tilePx * 0.5f
                    val textY = barY - 6f
                    val textWidth = labelPaint.measureText(m.type.displayName)

                    if (threatBand == ThreatBand.DEADLY) {
                        // Crisp procedural pixel skull icon beside Deadly / Boss nameplates
                        val skullPx = 2.2f
                        val skullX = centerX - textWidth * 0.5f - skullPx * 7.5f
                        val skullY = textY - skullPx * 7.2f
                        drawRect(Color.Black, Offset(skullX - 1f, skullY - 1f), Size(skullPx * 6f + 2f, skullPx * 6f + 2f))
                        drawRect(Color(0xFFF3EFE6), Offset(skullX + skullPx, skullY), Size(skullPx * 4f, skullPx * 4f))
                        drawRect(Color(0xFFF3EFE6), Offset(skullX, skullY + skullPx), Size(skullPx * 6f, skullPx * 2.5f))
                        drawRect(Color(0xFFF3EFE6), Offset(skullX + skullPx * 1.5f, skullY + skullPx * 4f), Size(skullPx * 3f, skullPx * 1.8f))
                        // Eye sockets & nose notch
                        drawRect(Color(0xFFB71C1C), Offset(skullX + skullPx * 1.1f, skullY + skullPx * 1.6f), Size(skullPx * 1.2f, skullPx * 1.2f))
                        drawRect(Color(0xFFB71C1C), Offset(skullX + skullPx * 3.7f, skullY + skullPx * 1.6f), Size(skullPx * 1.2f, skullPx * 1.2f))
                    }

                    drawContext.canvas.nativeCanvas.apply {
                        drawText(m.type.displayName, centerX, textY, labelShadowPaint)
                        drawText(m.type.displayName, centerX, textY, labelPaint)
                    }
                    labelPaint.color = prevColor
                }
            }

            // 4.5 Remote Co-Op Players on the same floor
            latestRemotePlayers.forEach { rp ->
                if (rp.floor == st.floor && rp.x in (minTx - 1)..(maxTx + 1) && rp.y in (minTy - 1)..(maxTy + 1)) {
                    val rpMoveFrac = ((now - rp.lastMoveMs).toFloat() / 190f).coerceIn(0f, 1f)
                    val rpX = rp.prevX + (rp.x - rp.prevX) * rpMoveFrac
                    val rpY = rp.prevY + (rp.y - rp.prevY) * rpMoveFrac
                    val rx = (originX + rpX * tilePx).roundToInt()
                    val ry = (originY + rpY * tilePx).roundToInt()
                    val rpFrame = if (rpMoveFrac < 1f) ((now / 110L) % 2L).toInt() else animFrame
                    val center = Offset(rx + tilePx * 0.5f, ry + tilePx * 0.55f)

                    // Tap-to-Inspect highlight ring
                    if (rp.playerId == st.inspectedPlayerId && now < st.inspectedPlayerUntilMs) {
                        val pulseAlpha = if (((now / 180L) % 2L) == 0L) 0.92f else 0.52f
                        drawRect(
                            color = Color(0xFF47D7FF).copy(alpha = pulseAlpha),
                            topLeft = Offset(rx.toFloat() + 1f, ry.toFloat() + 1f),
                            size = Size(tilePx - 2f, tilePx - 2f),
                            style = Stroke(width = 4.5f)
                        )
                    }

                    if (rp.isPoisoned) {
                        drawCircle(PoisonGreen.copy(alpha = 0.28f), tilePx * 0.56f, center)
                    }
                    if (rp.isBurning) {
                        drawCircle(FireOrange.copy(alpha = 0.34f), tilePx * 0.58f, center)
                    }

                    // Brief actionState visual cue (melee slash, arcane cast ring, or emerald heal halo)
                    val actionAge = now - rp.lastActionMs
                    if (actionAge in 0L..420L) {
                        val aFrac = 1f - (actionAge.toFloat() / 420f)
                        when (rp.actionState) {
                            PlayerActionState.ATTACKING -> {
                                val offX = rp.direction.dx * tilePx * 0.36f
                                val offY = rp.direction.dy * tilePx * 0.36f
                                drawCircle(
                                    color = Color(0xFFFFE082).copy(alpha = 0.65f * aFrac),
                                    radius = tilePx * (0.22f + 0.14f * (1f - aFrac)),
                                    center = Offset(center.x + offX, center.y + offY),
                                    style = Stroke(width = 4f)
                                )
                            }
                            PlayerActionState.CASTING -> {
                                drawCircle(
                                    color = Color(0xFF5CE1FF).copy(alpha = 0.72f * aFrac),
                                    radius = tilePx * (0.42f + 0.18f * (1f - aFrac)),
                                    center = center,
                                    style = Stroke(width = 4f)
                                )
                            }
                            PlayerActionState.HEALING -> {
                                drawCircle(
                                    color = Color(0xFF4CE678).copy(alpha = 0.72f * aFrac),
                                    radius = tilePx * (0.38f + 0.20f * (1f - aFrac)),
                                    center = center,
                                    style = Stroke(width = 4f)
                                )
                            }
                            else -> Unit
                        }
                    }

                    drawImage(
                        image = EntitySpriteAtlas.playerBitmap(
                            vocation = rp.vocation,
                            frame = rpFrame,
                            tunicColorIndex = rp.tunicColorIndex,
                            hairColorIndex = rp.hairColorIndex,
                            direction = rp.direction
                        ),
                        dstOffset = IntOffset(rx, ry),
                        dstSize = IntSize(tileInt, tileInt),
                        filterQuality = FilterQuality.None
                    )

                    val rHpFrac = (rp.hp.toFloat() / rp.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    val rBarW = tilePx * 0.88f
                    val rBarX = rx + (tilePx - rBarW) * 0.5f
                    val rBarY = ry - 12f
                    drawRect(Color.Black, Offset(rBarX - 1f, rBarY - 1f), Size(rBarW + 2f, 9f))
                    drawRect(
                        when {
                            rHpFrac > 0.6f -> Color(0xFF47D7FF)
                            rHpFrac > 0.3f -> TorchGold
                            else -> HealthCrimson
                        },
                        Offset(rBarX, rBarY),
                        Size(rBarW * rHpFrac, 7f)
                    )

                    val prevColor = labelPaint.color
                    labelPaint.color = android.graphics.Color.parseColor("#8CF0FF")
                    val tag = "${rp.name} [Lvl ${rp.level}]"
                    drawContext.canvas.nativeCanvas.apply {
                        drawText(tag, rx + tilePx * 0.5f, rBarY - 6f, labelShadowPaint)
                        drawText(tag, rx + tilePx * 0.5f, rBarY - 6f, labelPaint)
                    }
                    labelPaint.color = prevColor
                }
            }

            // 5. Player
            val playerScreenX = (originX + camPlayerX * tilePx).roundToInt()
            val playerScreenY = (originY + camPlayerY * tilePx).roundToInt()
            val playerFrame = if (moveFrac < 1f) ((now / 110L) % 2L).toInt() else animFrame

            if (st.poisonTicks > 0) {
                drawCircle(PoisonGreen.copy(alpha = 0.28f), tilePx * 0.56f, Offset(playerScreenX + tilePx * 0.5f, playerScreenY + tilePx * 0.55f))
            }
            if (st.fireTicks > 0) {
                drawCircle(FireOrange.copy(alpha = 0.34f), tilePx * 0.58f, Offset(playerScreenX + tilePx * 0.5f, playerScreenY + tilePx * 0.55f))
            }

            drawImage(
                image = EntitySpriteAtlas.playerBitmap(
                    vocation = st.vocation,
                    frame = playerFrame,
                    tunicColorIndex = st.tunicColorIndex,
                    hairColorIndex = st.hairColorIndex,
                    direction = st.playerFacing
                ),
                dstOffset = IntOffset(playerScreenX, playerScreenY),
                dstSize = IntSize(tileInt, tileInt),
                filterQuality = FilterQuality.None
            )

            val pHpFrac = (st.hp.toFloat() / st.maxHp.toFloat()).coerceIn(0f, 1f)
            val pBarW = tilePx * 0.9f
            val pBarX = playerScreenX + (tilePx - pBarW) * 0.5f
            val pBarY = playerScreenY - 12f
            drawRect(Color.Black, Offset(pBarX - 1f, pBarY - 1f), Size(pBarW + 2f, 9f))
            drawRect(
                when { pHpFrac > 0.6f -> Color(0xFF39D952); pHpFrac > 0.3f -> TorchGold; else -> HealthCrimson },
                Offset(pBarX, pBarY),
                Size(pBarW * pHpFrac, 7f)
            )
            drawContext.canvas.nativeCanvas.apply {
                drawText(st.playerName, playerScreenX + tilePx * 0.5f, pBarY - 6f, labelShadowPaint)
                drawText(st.playerName, playerScreenX + tilePx * 0.5f, pBarY - 6f, labelPaint)
            }

            // 6. Landmarks
            WorldMaps.landmarks.forEach { lm ->
                if (lm.floor == st.floor && lm.x in minTx..maxTx && lm.y in minTy..maxTy) {
                    val sx = originX + (lm.x + 0.5f) * tilePx
                    val sy = originY + lm.y * tilePx - 4f
                    drawContext.canvas.nativeCanvas.apply {
                        drawText(lm.label, sx, sy, labelShadowPaint)
                        drawText(lm.label, sx, sy, labelPaint)
                    }
                }
            }

            // 7. Projectiles, Fishing Splashes & Mining Sparks
            st.projectiles.forEach { proj ->
                if (proj.floor != st.floor) return@forEach
                val t = ((now - proj.createdAtMs).toFloat() / proj.durationMs.toFloat()).coerceIn(0f, 1f)
                if (t >= 1f) return@forEach
                val px = originX + (proj.fromX + (proj.toX - proj.fromX) * t + 0.5f) * tilePx
                val py = originY + (proj.fromY + (proj.toY - proj.fromY) * t + 0.5f) * tilePx
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
                        drawCircle(Color(0xFF4DD2FF), 12f, Offset(px, py))
                        drawCircle(Color.White, 5f, Offset(px, py))
                    }
                    ProjectileKind.DEATH_RUNE -> {
                        drawCircle(Color(0xFF1A0F14), 15f, Offset(px, py))
                        drawCircle(Color(0xFFFF3355), 9f, Offset(px, py))
                    }
                    ProjectileKind.FIRE_BREATH -> {
                        drawCircle(FireOrange, 16f + t * 8f, Offset(px, py))
                        drawCircle(Color(0xFFFFE57F), 8f, Offset(px, py))
                    }
                    ProjectileKind.FIREBALL -> {
                        drawCircle(TargetRed, 14f, Offset(px, py))
                        drawCircle(FireOrange, 10f, Offset(px, py))
                        drawCircle(Color(0xFFFFE57F), 5f, Offset(px, py))
                    }
                    ProjectileKind.THROWN_STONE -> {
                        drawRect(Color(0xFF5A5E66), Offset(px - 6f, py - 6f), Size(12f, 12f))
                        drawRect(Color(0xFFA8ADB8), Offset(px - 4f, py - 4f), Size(6f, 6f))
                    }
                    ProjectileKind.NECRO_BOLT -> {
                        drawCircle(Color(0xFF2E1042), 14f, Offset(px, py))
                        drawCircle(Color(0xFFB34BFF), 9f, Offset(px, py))
                        drawCircle(Color(0xFFFF66A3), 4f, Offset(px, py))
                    }
                    ProjectileKind.SPLASH -> {
                        val wx = originX + (proj.toX + 0.5f) * tilePx
                        val wy = originY + (proj.toY + 0.5f) * tilePx
                        drawCircle(Color(0xFF8CD9FF).copy(alpha = 1f - t), 8f + t * tilePx * 0.4f, Offset(wx, wy), style = Stroke(4f))
                    }
                    ProjectileKind.MINING_SPARK -> {
                        val wx = originX + (proj.toX + 0.5f) * tilePx
                        val wy = originY + (proj.toY + 0.5f) * tilePx
                        val spread = t * tilePx * 0.35f
                        drawRect(TorchGold.copy(alpha = 1f - t), Offset(wx - spread, wy - spread), Size(6f, 6f))
                        drawRect(Color.White.copy(alpha = 1f - t), Offset(wx + spread, wy - spread * 0.6f), Size(5f, 5f))
                        drawRect(FireOrange.copy(alpha = 1f - t), Offset(wx, wy + spread * 0.7f), Size(6f, 6f))
                    }
                    ProjectileKind.WOOD_CHIP -> {
                        val wx = originX + (proj.toX + 0.5f) * tilePx
                        val wy = originY + (proj.toY + 0.5f) * tilePx
                        val spread = t * tilePx * 0.38f
                        val alpha = (1f - t).coerceIn(0f, 1f)
                        drawRect(Color(0xFFC48A47).copy(alpha = alpha), Offset(wx - spread, wy - spread * 0.8f), Size(7f, 5f))
                        drawRect(Color(0xFF7D4F24).copy(alpha = alpha), Offset(wx + spread * 0.9f, wy - spread * 0.5f), Size(6f, 6f))
                        drawRect(Color(0xFFE0B472).copy(alpha = alpha), Offset(wx - spread * 0.3f, wy + spread * 0.7f), Size(6f, 4f))
                        drawRect(Color(0xFF4F8A3A).copy(alpha = alpha), Offset(wx + spread * 0.4f, wy + spread * 0.4f), Size(5f, 5f))
                    }
                }
            }

            // 8. Floating Combat & Resource Text
            st.floatingTexts.forEach { ft ->
                if (ft.floor != st.floor) return@forEach
                val age = (now - ft.createdAtMs).toFloat()
                if (age < 0f || age > 1300f) return@forEach
                val progress = (age / 1300f).coerceIn(0f, 1f)
                val fx = originX + (ft.tileX + 0.5f) * tilePx
                val fy = originY + ft.tileY * tilePx - progress * (tilePx * 0.95f)
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

        // Toggleable Corner Minimap Overlay for effortless 96x96 & 48x48 navigation
        if (state.showMinimap) {
            MinimapOverlay(
                state = state,
                remotePlayers = remotePlayers,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun MinimapOverlay(
    state: GameWorldState,
    remotePlayers: List<RemotePlayerState>,
    modifier: Modifier = Modifier
) {
    Surface(
        color = BasaltDark.copy(alpha = 0.88f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.2.dp, StoneBorderLight),
        modifier = modifier
            .size(104.dp)
            .testTag("minimap_overlay")
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            val fl = WorldMaps.getFloor(state.floor)
            val radius = 16
            val span = radius * 2 + 1
            val cell = size.width / span.toFloat()

            for (dy in -radius..radius) {
                val wy = state.playerY + dy
                for (dx in -radius..radius) {
                    val wx = state.playerX + dx
                    val col = if (wx !in 0 until fl.width || wy !in 0 until fl.height) {
                        ObsidianVoid
                    } else {
                        when (fl.tileAt(wx, wy)) {
                            TileType.GRASS -> Color(0xFF3E6E33)
                            TileType.DARK_GRASS -> Color(0xFF1F4023)
                            TileType.COBBLESTONE, TileType.TEMPLE_FLOOR -> Color(0xFF8C8275)
                            TileType.DIRT_FLOOR, TileType.WOOD_PIER -> Color(0xFF69503A)
                            TileType.CRYPT_FLOOR -> Color(0xFF4F545E)
                            TileType.OBSIDIAN_FLOOR -> Color(0xFF3A3142)
                            TileType.WATER -> Color(0xFF23669E)
                            TileType.LAVA -> Color(0xFFD94A1E)
                            TileType.STAIRS_DOWN, TileType.STAIRS_UP, TileType.ROPE_HOLE_DOWN, TileType.ROPE_SPOT_UP -> TorchGold
                            TileType.SMELTING_FURNACE, TileType.BLACKSMITH_ANVIL -> FireOrange
                            TileType.FLETCHING_BENCH -> Color(0xFFD4A054)
                            TileType.TREE_STUMP -> Color(0xFF5A422C)
                            TileType.DEPOT_CHEST, TileType.MINI_DEPOT, TileType.SHOP_COUNTER, TileType.TEMPLE_ALTAR -> Color(0xFF5CE1FF)
                            TileType.ORE_OCHRE, TileType.ORE_BOGSTONE, TileType.ORE_BRIMSTONE, TileType.ORE_AETHERIUM -> Color(0xFFB599FF)
                            else -> Color(0xFF1E1B1A)
                        }
                    }
                    drawRect(col, Offset((dx + radius) * cell, (dy + radius) * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
            // Nearby monsters as crimson pips
            state.monsters.forEach { m ->
                if (m.floor == state.floor) {
                    val dx = m.x - state.playerX
                    val dy = m.y - state.playerY
                    if (dx in -radius..radius && dy in -radius..radius) {
                        drawRect(TargetRed, Offset((dx + radius) * cell, (dy + radius) * cell), Size(cell, cell))
                    }
                }
            }
            // Remote co-op allies as high-contrast cyan pips with dark outline
            remotePlayers.forEach { rp ->
                if (rp.floor == state.floor) {
                    val dx = rp.x - state.playerX
                    val dy = rp.y - state.playerY
                    if (dx in -radius..radius && dy in -radius..radius) {
                        val px = (dx + radius) * cell
                        val py = (dy + radius) * cell
                        drawRect(Color.Black, Offset(px - 1.5f, py - 1.5f), Size(cell + 3f, cell + 3f))
                        drawRect(Color(0xFF47D7FF), Offset(px - 0.5f, py - 0.5f), Size(cell + 1f, cell + 1f))
                    }
                }
            }
            // Player bright white center pip
            drawRect(Color.White, Offset(radius * cell - 1f, radius * cell - 1f), Size(cell + 2f, cell + 2f))
        }
    }
}

private class CameraMetrics {
    var tilePx: Float = 96f
    var originX: Float = 0f
    var originY: Float = 0f
}
