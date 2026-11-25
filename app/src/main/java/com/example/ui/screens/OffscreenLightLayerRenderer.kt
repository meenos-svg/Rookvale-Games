package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.game.model.GraphicsQuality
import com.example.game.model.LightingDetailMode
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pre-baked soft radial light/glow sprite atlas cached once at startup.
 * Eliminates per-frame [androidx.compose.ui.graphics.Brush.radialGradient] allocations for torches,
 * lava pools, crystal pillars, arcane runes, boss auras, and equipment proc glows.
 */
object LightGlowSpriteAtlas {
    const val SPRITE_PX = 128

    @Volatile
    private var outerHaloBitmap: Bitmap? = null

    @Volatile
    private var innerBloomBitmap: Bitmap? = null

    @Volatile
    private var outerHaloImage: ImageBitmap? = null

    @Volatile
    private var innerBloomImage: ImageBitmap? = null

    fun prewarm() {
        getOuterHaloBitmap()
        getInnerBloomBitmap()
    }

    fun isPrewarmed(): Boolean = outerHaloBitmap != null && innerBloomBitmap != null

    fun getOuterHaloBitmap(): Bitmap {
        outerHaloBitmap?.let { return it }
        return synchronized(this) {
            outerHaloBitmap ?: bakeRadialGlowBitmap(
                midStop = 0.46f,
                midAlpha = 0.42f,
                edgePower = 0.08f
            ).also {
                outerHaloBitmap = it
                outerHaloImage = it.asImageBitmap()
            }
        }
    }

    fun getInnerBloomBitmap(): Bitmap {
        innerBloomBitmap?.let { return it }
        return synchronized(this) {
            innerBloomBitmap ?: bakeRadialGlowBitmap(
                midStop = 0.38f,
                midAlpha = 0.56f,
                edgePower = 0.12f
            ).also {
                innerBloomBitmap = it
                innerBloomImage = it.asImageBitmap()
            }
        }
    }

    fun getOuterHaloImageBitmap(): ImageBitmap {
        getOuterHaloBitmap()
        return outerHaloImage!!
    }

    fun getInnerBloomImageBitmap(): ImageBitmap {
        getInnerBloomBitmap()
        return innerBloomImage!!
    }

    private fun bakeRadialGlowBitmap(
        midStop: Float,
        midAlpha: Float,
        edgePower: Float
    ): Bitmap {
        val bmp = Bitmap.createBitmap(SPRITE_PX, SPRITE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val half = SPRITE_PX * 0.5f
        val midA = (midAlpha * 255f).roundToInt().coerceIn(0, 255)
        val edgeA = (edgePower * 255f).roundToInt().coerceIn(0, 255)
        val shader = RadialGradient(
            half,
            half,
            half,
            intArrayOf(
                android.graphics.Color.argb(255, 255, 255, 255),
                android.graphics.Color.argb(midA, 255, 255, 255),
                android.graphics.Color.argb(edgeA, 255, 255, 255),
                android.graphics.Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, midStop, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
        }
        canvas.drawCircle(half, half, half, paint)
        return bmp
    }
}

/**
 * Half-resolution (`0.5x`) offscreen dynamic point-light layer renderer for High & HD tiers.
 *
 * Caps simultaneous point lights to the closest prioritized sources around the camera viewport
 * (max 14 in HD, 10 in High, 6 in Medium dungeons) and renders each light in a single clean
 * `SCREEN`-blended sprite draw onto a reusable `0.5x` offscreen bitmap—eliminating excessive
 * overlapping `BlendMode.Plus` / `PorterDuff.Mode.ADD` double-draws and compositing the entire
 * light layer in one hardware-accelerated bilinear pass.
 */
class HalfResLightLayerRenderer(
    val resolutionScale: Float = HALF_RES_SCALE
) {
    private var layerBitmap: Bitmap? = null
    private var layerImage: ImageBitmap? = null
    private var layerCanvas: Canvas? = null
    private var allocatedW: Int = 0
    private var allocatedH: Int = 0

    // Pre-allocated drawing tools and light-selection scratch buffers (zero per-frame heap allocations)
    private val screenPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private val dstRect = RectF()
    private val colorFilterCache = HashMap<Int, PorterDuffColorFilter>(32)
    private val selectedLights = ArrayList<ScenePointLight>(IsometricLightingEngine.MAX_SIMULTANEOUS_POINT_LIGHTS)
    private val selectedScores = FloatArray(IsometricLightingEngine.MAX_SIMULTANEOUS_POINT_LIGHTS)

    var lastRenderedLightCount: Int = 0
        private set

    val usesAdditiveOverlapPass: Boolean
        get() = false

    val currentBufferWidth: Int get() = allocatedW
    val currentBufferHeight: Int get() = allocatedH

    fun ensureLayerSize(viewportW: Float, viewportH: Float): Boolean {
        val targetW = (viewportW * resolutionScale).roundToInt().coerceIn(MIN_BUFFER_DIM, MAX_BUFFER_WIDTH)
        val targetH = (viewportH * resolutionScale).roundToInt().coerceIn(MIN_BUFFER_DIM, MAX_BUFFER_HEIGHT)
        if (targetW <= 0 || targetH <= 0) return false
        if (layerBitmap != null && allocatedW == targetW && allocatedH == targetH) return true

        layerBitmap?.recycle()
        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        layerBitmap = bmp
        layerImage = bmp.asImageBitmap()
        layerCanvas = Canvas(bmp)
        allocatedW = targetW
        allocatedH = targetH
        return true
    }

    /**
     * Selects up to [maxLightsOverride] (or [quality.maxDynamicPointLights], hard-capped at 14)
     * closest prioritized lights, renders them in a single clean `SCREEN` pass onto the half-res
     * offscreen buffer, and composites the layer in one bilinear `BlendMode.Screen` pass over [scope].
     *
     * @return number of lights rendered into the layer (always `<= 14`).
     */
    fun renderAndCompositeLights(
        scope: DrawScope,
        lights: List<ScenePointLight>,
        quality: GraphicsQuality,
        nowMs: Long,
        lightMultiplier: Float,
        maxLightsOverride: Int? = null
    ): Int {
        if (lights.isEmpty() || lightMultiplier <= 0.05f) {
            lastRenderedLightCount = 0
            return 0
        }
        val w = scope.size.width
        val h = scope.size.height
        if (w <= 0f || h <= 0f) {
            lastRenderedLightCount = 0
            return 0
        }
        val cap = (maxLightsOverride ?: quality.maxDynamicPointLights)
            .coerceIn(0, IsometricLightingEngine.MAX_SIMULTANEOUS_POINT_LIGHTS)
        if (cap == 0) {
            lastRenderedLightCount = 0
            return 0
        }

        val selectedCount = IsometricLightingEngine.selectClosestPrioritizedLights(
            lights = lights,
            viewportW = w,
            viewportH = h,
            maxLights = cap,
            outSelected = selectedLights,
            scratchScores = selectedScores
        )
        if (selectedCount == 0 || !ensureLayerSize(w, h)) {
            lastRenderedLightCount = 0
            return 0
        }

        val canvas = layerCanvas ?: return 0
        val image = layerImage ?: return 0
        val scaleX = allocatedW.toFloat() / w
        val scaleY = allocatedH.toFloat() / h
        val isHd = quality.lightingMode == LightingDetailMode.HD_MULTI_LAYER

        // HD uses the richer inner-bloom sprite in a single clean SCREEN pass (no redundant ADD double-draw)
        val sprite = if (isHd) {
            LightGlowSpriteAtlas.getInnerBloomBitmap()
        } else {
            LightGlowSpriteAtlas.getOuterHaloBitmap()
        }

        // Clear offscreen light layer to transparent black
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val timeSec = nowMs * 0.001
        var drawnCount = 0

        for (i in 0 until selectedCount) {
            val light = selectedLights[i]
            val waveA = sin(timeSec * (if (light.isArcane) 3.8 else 7.2) + light.flickerSeed).toFloat()
            val waveB = cos(timeSec * (if (light.isArcane) 2.1 else 11.3) + light.flickerSeed * 1.7).toFloat()
            val flicker = (0.91f + 0.06f * waveA + 0.03f * waveB).coerceIn(0.80f, 1.04f)
            val effIntensity = (light.intensity * lightMultiplier).coerceIn(0.08f, 1.45f)
            val scaledR = (light.radiusPx * flicker).coerceAtLeast(16f)

            val lx = light.screenX * scaleX
            val ly = light.screenY * scaleY
            val radiusScale = if (isHd) 1.22f else 1.05f
            val rx = scaledR * radiusScale * scaleX
            val ry = scaledR * radiusScale * scaleY
            val alphaFactor = if (isHd) 0.54f else 0.42f
            val maxAlpha = if (isHd) 0.78f else 0.68f
            val drawAlpha = ((alphaFactor * effIntensity * flicker).coerceIn(0f, maxAlpha) * 255f).roundToInt()
            if (drawAlpha > 2) {
                screenPaint.colorFilter = getCachedTintFilter(light.coreColor)
                screenPaint.alpha = drawAlpha
                dstRect.set(lx - rx, ly - ry, lx + rx, ly + ry)
                canvas.drawBitmap(sprite, null, dstRect, screenPaint)
                drawnCount++
            }
        }

        if (drawnCount > 0) {
            // Single bilinear upscaled composite pass over the entire world viewport
            scope.drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(allocatedW, allocatedH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                alpha = 1f,
                blendMode = BlendMode.Screen,
                filterQuality = FilterQuality.Low
            )
        }
        lastRenderedLightCount = drawnCount
        return drawnCount
    }

    private fun getCachedTintFilter(color: Color): PorterDuffColorFilter {
        // Strip alpha so identical RGB light hues reuse the exact same cached filter object
        val rgbKey = color.toArgb() or (0xFF shl 24)
        colorFilterCache[rgbKey]?.let { return it }
        if (colorFilterCache.size >= MAX_TINT_CACHE) {
            colorFilterCache.clear()
        }
        val filter = PorterDuffColorFilter(rgbKey, PorterDuff.Mode.SRC_IN)
        colorFilterCache[rgbKey] = filter
        return filter
    }

    companion object {
        const val HALF_RES_SCALE = 0.5f
        private const val MIN_BUFFER_DIM = 64
        private const val MAX_BUFFER_WIDTH = 1024
        private const val MAX_BUFFER_HEIGHT = 600
        private const val MAX_TINT_CACHE = 64
    }
}
