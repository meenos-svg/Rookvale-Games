package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.example.game.model.GameWorldState
import com.example.game.model.isRegionCompatible
import com.example.game.model.normalizeRegionId
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.world.WorldFloor
import com.example.ui.theme.TargetRed
import com.example.ui.theme.TorchGold
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Offscreen bitmap cache for the top-left rotating minimap.
 *
 * Eliminates redrawing thousands of rotated terrain tiles at 60 FPS by caching the rendered minimap
 * into a reusable offscreen [Bitmap] and only re-rendering when:
 * 1. The player's tile `(playerX, playerY)` or `floor` or `radius` changes, OR
 * 2. `cameraYawDeg` shifts by more than [YAW_REDRAW_THRESHOLD_DEG] (2°), OR
 * 3. Nearby entities (monsters within radius, remote players, target, or walk path) change,
 *    throttled to at most ~12 FPS ([ENTITY_REDRAW_INTERVAL_MS] = 83ms).
 */
class MinimapOffscreenCache {
    private var bitmap: Bitmap? = null
    private var imageBitmap: ImageBitmap? = null
    private var canvas: Canvas? = null
    private var cachedWidth: Int = 0
    private var cachedHeight: Int = 0

    private var hasRendered: Boolean = false
    private var lastFloor: Int = Int.MIN_VALUE
    private var lastPlayerX: Int = Int.MIN_VALUE
    private var lastPlayerY: Int = Int.MIN_VALUE
    private var lastRadius: Int = -1
    private var lastYawDeg: Float = Float.NaN
    private var lastEntitySig: Int = 0
    private var lastRedrawUptimeMs: Long = Long.MIN_VALUE

    var redrawCount: Int = 0
        private set

    // Pre-allocated Android drawing tools (zero allocations per redraw)
    private val tilePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = false
    }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = TorchGold.copy(alpha = 0.22f).toArgb()
    }
    private val conePath = Path()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
        textSize = 18f
        color = android.graphics.Color.parseColor("#8CF0FF")
    }
    private val labelOutlinePaint = Paint(labelPaint).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.2f
        color = android.graphics.Color.BLACK
    }

    /**
     * Pure deterministic dirty check implementing the tile/yaw/12-FPS entity redraw policy.
     */
    fun shouldRedraw(
        widthPx: Int,
        heightPx: Int,
        floor: Int,
        playerX: Int,
        playerY: Int,
        radius: Int,
        cameraYawDeg: Float,
        entitySignature: Int,
        nowUptimeMs: Long
    ): Boolean {
        if (!hasRendered || bitmap == null) return true
        if (widthPx != cachedWidth || heightPx != cachedHeight) return true
        if (floor != lastFloor || radius != lastRadius) return true
        if (playerX != lastPlayerX || playerY != lastPlayerY) return true
        val yawDelta = abs(IsometricCamera.shortestAngleDeltaDeg(cameraYawDeg, lastYawDeg))
        if (yawDelta > YAW_REDRAW_THRESHOLD_DEG) return true
        if (entitySignature != lastEntitySig) {
            val elapsed = nowUptimeMs - lastRedrawUptimeMs
            if (elapsed >= ENTITY_REDRAW_INTERVAL_MS || elapsed < 0L) return true
        }
        return false
    }

    /**
     * Zero-allocation signature of nearby dynamic minimap entities (monsters in radius, walk path,
     * selected target, and compatible remote players on the active floor).
     */
    fun computeEntitySignature(
        state: GameWorldState,
        remotePlayers: List<RemotePlayerState>,
        radius: Int
    ): Int {
        var h = state.targetMonsterId ?: 0
        h = 31 * h + state.walkPath.size
        val pathLimit = minOf(state.walkPath.size, 8)
        for (i in 0 until pathLimit) {
            val pt = state.walkPath[i]
            h = 31 * h + pt.first
            h = 31 * h + pt.second
        }
        val px = state.playerX
        val py = state.playerY
        val fl = state.floor
        val monsters = state.monsters
        for (i in 0 until monsters.size) {
            val m = monsters[i]
            if (m.floor == fl && abs(m.x - px) <= radius && abs(m.y - py) <= radius) {
                h = 31 * h + m.id
                h = 31 * h + (m.x shl 16) + m.y
            }
        }
        val localRegionId = normalizeRegionId(state.regionId)
        for (i in 0 until remotePlayers.size) {
            val rp = remotePlayers[i]
            if (!rp.isSpectator && rp.floor == fl && isRegionCompatible(fl, localRegionId, rp.regionId)) {
                h = 31 * h + rp.playerId.hashCode()
                h = 31 * h + (rp.x shl 16) + rp.y
            }
        }
        return h
    }

    /**
     * Returns the cached [ImageBitmap], re-rendering the offscreen buffer only when dirty.
     */
    fun getOrRender(
        widthPx: Int,
        heightPx: Int,
        floorMap: WorldFloor,
        state: GameWorldState,
        remotePlayers: List<RemotePlayerState>,
        radius: Int,
        cameraYawDeg: Float,
        nowUptimeMs: Long
    ): ImageBitmap? {
        val safeW = widthPx.coerceAtLeast(1)
        val safeH = heightPx.coerceAtLeast(1)
        val entitySig = computeEntitySignature(state, remotePlayers, radius)
        if (!shouldRedraw(safeW, safeH, state.floor, state.playerX, state.playerY, radius, cameraYawDeg, entitySig, nowUptimeMs)) {
            return imageBitmap
        }

        if (bitmap == null || cachedWidth != safeW || cachedHeight != safeH) {
            bitmap?.recycle()
            val bmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
            bitmap = bmp
            imageBitmap = bmp.asImageBitmap()
            canvas = Canvas(bmp)
            cachedWidth = safeW
            cachedHeight = safeH
        }

        val c = canvas ?: return imageBitmap
        renderMinimapIntoCanvas(c, safeW.toFloat(), safeH.toFloat(), floorMap, state, remotePlayers, radius, cameraYawDeg)

        hasRendered = true
        lastFloor = state.floor
        lastPlayerX = state.playerX
        lastPlayerY = state.playerY
        lastRadius = radius
        lastYawDeg = IsometricCamera.normalizeYawDeg(cameraYawDeg)
        lastEntitySig = entitySig
        lastRedrawUptimeMs = nowUptimeMs
        redrawCount++
        return imageBitmap
    }

    private fun renderMinimapIntoCanvas(
        c: Canvas,
        width: Float,
        height: Float,
        floorMap: WorldFloor,
        state: GameWorldState,
        remotePlayers: List<RemotePlayerState>,
        radius: Int,
        cameraYawDeg: Float
    ) {
        c.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val cx = width * 0.5f
        val cy = height * 0.5f
        val scale = (minOf(width, height) * 0.46f) / radius.coerceAtLeast(1).toFloat()
        val rad = IsometricCamera.normalizeYawDeg(cameraYawDeg) * (PI / 180.0)
        val cosY = cos(rad).toFloat() * scale
        val sinY = sin(rad).toFloat() * scale
        val dotSize = (scale * 1.32f).coerceAtLeast(2.2f)
        val halfDot = dotSize * 0.5f

        val playerX = state.playerX
        val playerY = state.playerY
        val floorW = floorMap.width
        val floorH = floorMap.height

        // 1. Rotated terrain pips synchronized with cameraYaw
        for (dy in -radius..radius) {
            val wy = playerY + dy
            if (wy < 0 || wy >= floorH) continue
            for (dx in -radius..radius) {
                val wx = playerX + dx
                if (wx < 0 || wx >= floorW) continue
                val sx = cx + dx * cosY - dy * sinY
                val sy = cy + dx * sinY + dy * cosY
                if (sx < -dotSize || sx > width + dotSize || sy < -dotSize || sy > height + dotSize) continue
                tilePaint.color = minimapTileColor(floorMap.tileAt(wx, wy)).toArgb()
                c.drawRect(sx - halfDot, sy - halfDot, sx + halfDot, sy + halfDot, tilePaint)
            }
        }

        // 2. Live camera forward-view direction cone
        val coneLen = minOf(width, height) * 0.34f
        val coneHalfW = coneLen * 0.48f
        conePath.reset()
        conePath.moveTo(cx, cy)
        conePath.lineTo(cx - coneHalfW, cy - coneLen)
        conePath.lineTo(cx + coneHalfW, cy - coneLen)
        conePath.close()
        c.drawPath(conePath, conePaint)

        // 3. Walk path breadcrumbs
        val goldBreadcrumb = TorchGold.copy(alpha = 0.88f).toArgb()
        circlePaint.color = goldBreadcrumb
        val walkPath = state.walkPath
        for (i in 0 until walkPath.size) {
            val (wx, wy) = walkPath[i]
            val dx = wx - playerX
            val dy = wy - playerY
            if (abs(dx) <= radius && abs(dy) <= radius) {
                val sx = cx + dx * cosY - dy * sinY
                val sy = cy + dx * sinY + dy * cosY
                c.drawCircle(sx, sy, 1.8f, circlePaint)
            }
        }

        // 4. Nearby monsters as crimson dots
        circlePaint.color = TargetRed.toArgb()
        val monsters = state.monsters
        for (i in 0 until monsters.size) {
            val m = monsters[i]
            if (m.floor == state.floor) {
                val dx = m.x - playerX
                val dy = m.y - playerY
                if (abs(dx) <= radius && abs(dy) <= radius) {
                    val sx = cx + dx * cosY - dy * sinY
                    val sy = cy + dx * sinY + dy * cosY
                    c.drawCircle(sx, sy, if (m.id == state.targetMonsterId) 3.4f else 2.2f, circlePaint)
                }
            }
        }

        // 5. Remote players: cyan/gold dots + crisp name tooltips (or directional border pip)
        val localRegionId = normalizeRegionId(state.regionId)
        val goldArgb = TorchGold.toArgb()
        val cyanArgb = Color(0xFF47D7FF).toArgb()
        for (i in 0 until remotePlayers.size) {
            val rp = remotePlayers[i]
            if (!rp.isSpectator && rp.floor == state.floor && isRegionCompatible(state.floor, localRegionId, rp.regionId)) {
                val dx = rp.x - playerX
                val dy = rp.y - playerY
                val sx = cx + dx * cosY - dy * sinY
                val sy = cy + dx * sinY + dy * cosY
                val inBounds = sx in 5f..(width - 5f) && sy in 5f..(height - 5f)
                if (inBounds) {
                    circlePaint.color = android.graphics.Color.BLACK
                    c.drawCircle(sx, sy, 4.8f, circlePaint)
                    circlePaint.color = goldArgb
                    c.drawCircle(sx, sy, 3.8f, circlePaint)
                    circlePaint.color = cyanArgb
                    c.drawCircle(sx, sy, 2.6f, circlePaint)
                    val shortName = rp.name.take(9)
                    val labelX = sx.coerceIn(22f, (width - 22f).coerceAtLeast(22f))
                    val labelY = (sy - 7f).coerceIn(16f, (height - 4f).coerceAtLeast(16f))
                    c.drawText(shortName, labelX, labelY, labelOutlinePaint)
                    c.drawText(shortName, labelX, labelY, labelPaint)
                } else {
                    val vx = sx - cx
                    val vy = sy - cy
                    val halfBoxX = (width * 0.5f - 6f).coerceAtLeast(6f)
                    val halfBoxY = (height * 0.5f - 6f).coerceAtLeast(6f)
                    val scaleToEdge = minOf(
                        if (abs(vx) > 0.001f) halfBoxX / abs(vx) else Float.MAX_VALUE,
                        if (abs(vy) > 0.001f) halfBoxY / abs(vy) else Float.MAX_VALUE
                    ).coerceIn(0f, 1f)
                    val ex = cx + vx * scaleToEdge
                    val ey = cy + vy * scaleToEdge
                    circlePaint.color = android.graphics.Color.BLACK
                    c.drawCircle(ex, ey, 3.6f, circlePaint)
                    circlePaint.color = goldArgb
                    c.drawCircle(ex, ey, 2.7f, circlePaint)
                    circlePaint.color = cyanArgb
                    c.drawCircle(ex, ey, 1.7f, circlePaint)
                }
            }
        }

        // 6. Local player center marker
        circlePaint.color = android.graphics.Color.BLACK
        c.drawCircle(cx, cy, 4f, circlePaint)
        circlePaint.color = android.graphics.Color.WHITE
        c.drawCircle(cx, cy, 2.6f, circlePaint)
    }

    companion object {
        const val YAW_REDRAW_THRESHOLD_DEG: Float = 2.0f
        const val ENTITY_REDRAW_INTERVAL_MS: Long = 83L // ~12 FPS cap for dynamic entity updates
    }
}
