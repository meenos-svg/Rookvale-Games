package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.model.DEFAULT_TIME_OF_DAY_HOURS
import com.example.game.model.GraphicsQuality
import com.example.game.world.BiomeWeatherSystem
import com.example.game.world.ResolvedWeatherState
import com.example.game.world.WeatherType
import com.example.game.world.WeatherVisualProfile
import com.example.ui.theme.BasaltDark
import com.example.ui.theme.ExpEmerald
import com.example.ui.theme.FireOrange
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.ParchmentMuted
import com.example.ui.theme.PoisonGreen
import com.example.ui.theme.SoulViolet
import com.example.ui.theme.StoneBorderLight
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Zero-GC procedural weather renderer & cached atmospheric gradient pass.
 *
 * Scales smoothly across all four [GraphicsQuality] tiers without full-screen RuntimeShader stalls:
 * - LOW: Ultra-light ambient tint only (0 particles)
 * - MEDIUM: Lightweight single-layer particles + subtle atmospheric veil
 * - HIGH: Multi-depth parallax particles, ground rain ripples, ember glows & cached multi-stop gradients
 * - HD: Enriched parallax particles, ground ripples, ember glows & cached atmospheric gradients
 */
class WeatherOverlayRenderer {
    // Smooth region-crossing / admin-override interpolation state
    private var displayedWeather: WeatherType = WeatherType.CLEAR
    private var fadingWeather: WeatherType = WeatherType.CLEAR
    private var transitionProgress: Float = 1f
    private var lastFrameMs: Long = 0L
    private val leafPath = Path()

    // Viewport-keyed atmospheric gradient Brush cache (zero per-frame gradient allocations)
    private var cachedGradW: Int = -1
    private var cachedGradH: Int = -1
    private var cachedGradWeather: WeatherType = WeatherType.CLEAR
    private var cachedGradBrush: Brush? = null
    private var gradCacheRebuilds: Int = 0

    val isRuntimeShaderActive: Boolean
        get() = false

    val gradientCacheRebuildCount: Int
        get() = gradCacheRebuilds

    fun getOrCreateAtmosphericBrush(w: Float, h: Float, weather: WeatherType): Brush? {
        if (weather == WeatherType.CLEAR || w <= 0f || h <= 0f) return null
        val wi = w.roundToInt()
        val hi = h.roundToInt()
        val existing = cachedGradBrush
        if (existing != null && cachedGradW == wi && cachedGradH == hi && cachedGradWeather == weather) {
            return existing
        }
        val created: Brush = when (weather) {
            WeatherType.LIGHT_RAIN -> Brush.verticalGradient(
                0.0f to Color(0xFF1F334D).copy(alpha = 0.22f),
                0.55f to Color(0xFF284160).copy(alpha = 0.11f),
                1.0f to Color(0xFF132236).copy(alpha = 0.18f)
            )
            WeatherType.FALLING_LEAVES -> Brush.radialGradient(
                0.0f to Color(0xFFF2B359).copy(alpha = 0.09f),
                0.72f to Color.Transparent,
                1.0f to Color(0xFF5A3718).copy(alpha = 0.12f),
                center = Offset(w * 0.28f, h * 0.20f),
                radius = maxOf(w, h) * 0.88f
            )
            WeatherType.SNOW -> Brush.radialGradient(
                0.0f to Color(0xFFDCEEFF).copy(alpha = 0.06f),
                0.62f to Color(0xFFB7D6F5).copy(alpha = 0.10f),
                1.0f to Color(0xFF8CB4E0).copy(alpha = 0.22f),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = maxOf(w, h) * 0.72f
            )
            WeatherType.VOLCANIC_ASH -> Brush.verticalGradient(
                0.0f to Color(0xFF2B2220).copy(alpha = 0.20f),
                0.60f to Color(0xFF3E2920).copy(alpha = 0.12f),
                1.0f to Color(0xFF8C3618).copy(alpha = 0.20f)
            )
            WeatherType.SWAMP_MIST -> Brush.verticalGradient(
                0.0f to Color(0xFF1F332B).copy(alpha = 0.14f),
                0.55f to Color(0xFF47695B).copy(alpha = 0.18f),
                1.0f to Color(0xFF729E88).copy(alpha = 0.26f)
            )
            WeatherType.CLEAR -> return null
        }
        cachedGradW = wi
        cachedGradH = hi
        cachedGradWeather = weather
        cachedGradBrush = created
        gradCacheRebuilds++
        return created
    }

    fun drawWeatherPass(
        scope: DrawScope,
        resolved: ResolvedWeatherState,
        quality: GraphicsQuality,
        nowMs: Long,
        camTileX: Float,
        camTileY: Float,
        cameraYawDeg: Float,
        timeOfDayHours: Float = DEFAULT_TIME_OF_DAY_HOURS
    ) {
        val w = scope.size.width
        val h = scope.size.height
        if (w <= 0f || h <= 0f) return

        // Update smooth crossfade when stepping across biome borders or changing Admin override
        val dtSec = if (lastFrameMs == 0L) {
            0.016f
        } else {
            ((nowMs - lastFrameMs).coerceIn(0L, 250L)) / 1000f
        }
        lastFrameMs = nowMs

        val targetWeather = if (resolved.isSuppressedUnderground) WeatherType.CLEAR else resolved.activeWeather
        if (targetWeather != displayedWeather) {
            fadingWeather = displayedWeather
            displayedWeather = targetWeather
            transitionProgress = 0f
        } else if (transitionProgress < 1f) {
            transitionProgress = (transitionProgress + dtSec / REGION_CROSSFADE_SEC).coerceAtMost(1f)
        }

        // Combine epoch blend & region crossfade cleanly
        val profile = BiomeWeatherSystem.visualProfileFor(quality)
        val tod = IsometricLightingEngine.computeTimeOfDayProfile(timeOfDayHours)
        val daylightFactor = (1f - tod.darknessFactor).coerceIn(0.15f, 1f)
        val timeSec = (nowMs % 3_600_000L) / 1000f

        val primaryAlpha = if (displayedWeather == WeatherType.CLEAR) {
            0f
        } else {
            val epochWeight = if (resolved.activeWeather == displayedWeather) resolved.transitionBlend else 1f
            (transitionProgress * epochWeight).coerceIn(0f, 1f)
        }
        val secondaryAlpha = if (fadingWeather != WeatherType.CLEAR && fadingWeather != displayedWeather && transitionProgress < 1f) {
            (1f - transitionProgress).coerceIn(0f, 1f)
        } else if (resolved.previousWeather != WeatherType.CLEAR &&
            resolved.previousWeather != displayedWeather &&
            resolved.transitionBlend < 1f
        ) {
            (1f - resolved.transitionBlend).coerceIn(0f, 1f)
        } else {
            0f
        }

        val fadingSource = if (fadingWeather != WeatherType.CLEAR && fadingWeather != displayedWeather && transitionProgress < 1f) {
            fadingWeather
        } else {
            resolved.previousWeather
        }

        if (primaryAlpha <= 0.01f && secondaryAlpha <= 0.01f) return

        // 1. Atmospheric Veil / Multi-Stop Gradient / HD AGSL Shader Pass
        if (secondaryAlpha > 0.01f) {
            drawAtmosphericPass(scope, fadingSource, secondaryAlpha, profile, timeSec, daylightFactor)
        }
        if (primaryAlpha > 0.01f) {
            drawAtmosphericPass(scope, displayedWeather, primaryAlpha, profile, timeSec, daylightFactor)
        }

        // 2. Zero-GC Procedural Weather Particles (Medium, High, HD)
        if (profile.particleCount > 0) {
            if (secondaryAlpha > 0.04f) {
                drawProceduralParticles(
                    scope = scope,
                    weather = fadingSource,
                    alpha = secondaryAlpha,
                    profile = profile,
                    timeSec = timeSec,
                    camTileX = camTileX,
                    camTileY = camTileY,
                    cameraYawDeg = cameraYawDeg,
                    daylightFactor = daylightFactor
                )
            }
            if (primaryAlpha > 0.04f) {
                drawProceduralParticles(
                    scope = scope,
                    weather = displayedWeather,
                    alpha = primaryAlpha,
                    profile = profile,
                    timeSec = timeSec,
                    camTileX = camTileX,
                    camTileY = camTileY,
                    cameraYawDeg = cameraYawDeg,
                    daylightFactor = daylightFactor
                )
            }
        }
    }

    private fun drawAtmosphericPass(
        scope: DrawScope,
        weather: WeatherType,
        intensity: Float,
        profile: WeatherVisualProfile,
        timeSec: Float,
        daylightFactor: Float
    ) {
        if (weather == WeatherType.CLEAR || intensity <= 0.01f) return
        val w = scope.size.width
        val h = scope.size.height

        // HIGH & HD Tiers: Viewport-cached multi-stop atmospheric weather gradient (zero RuntimeShader / zero per-frame Brush alloc)
        if (profile.enableAtmosphericGradient) {
            val cachedBrush = getOrCreateAtmosphericBrush(w, h, weather)
            if (cachedBrush != null) {
                val modAlpha = when (weather) {
                    WeatherType.FALLING_LEAVES -> (intensity * (0.65f + 0.35f * daylightFactor)).coerceIn(0f, 1f)
                    WeatherType.SWAMP_MIST -> (intensity * (0.86f + 0.14f * sin(timeSec * 0.55f))).coerceIn(0f, 1f)
                    else -> intensity.coerceIn(0f, 1f)
                }
                scope.drawRect(
                    brush = cachedBrush,
                    topLeft = Offset.Zero,
                    size = scope.size,
                    alpha = modAlpha
                )
            }
            return
        }

        // LOW & MEDIUM Tier: Ultra-light single flat tint veil (minimal GPU cost)
        if (profile.enableSubtleVeil) {
            val scale = if (profile.particleCount == 0) 0.55f else 0.85f
            val veilColor = when (weather) {
                WeatherType.LIGHT_RAIN -> Color(0xFF233954).copy(alpha = 0.11f * intensity * scale)
                WeatherType.FALLING_LEAVES -> Color(0xFFB57830).copy(alpha = 0.06f * intensity * scale)
                WeatherType.SNOW -> Color(0xFFCDE3FA).copy(alpha = 0.10f * intensity * scale)
                WeatherType.VOLCANIC_ASH -> Color(0xFF3A2720).copy(alpha = 0.12f * intensity * scale)
                WeatherType.SWAMP_MIST -> Color(0xFF496B5A).copy(alpha = 0.14f * intensity * scale)
                WeatherType.CLEAR -> Color.Transparent
            }
            if (veilColor.alpha > 0.005f) {
                scope.drawRect(color = veilColor, topLeft = Offset.Zero, size = scope.size)
            }
        }
    }

    private fun drawProceduralParticles(
        scope: DrawScope,
        weather: WeatherType,
        alpha: Float,
        profile: WeatherVisualProfile,
        timeSec: Float,
        camTileX: Float,
        camTileY: Float,
        cameraYawDeg: Float,
        daylightFactor: Float
    ) {
        val w = scope.size.width
        val h = scope.size.height
        val count = when (weather) {
            WeatherType.SWAMP_MIST -> (profile.particleCount / 3).coerceAtLeast(10)
            WeatherType.FALLING_LEAVES -> (profile.particleCount * 0.58f).toInt().coerceAtLeast(14)
            else -> profile.particleCount
        }
        val yawRad = cameraYawDeg * (PI / 180.0)
        val camShiftX = (camTileX * cos(yawRad) - camTileY * sin(yawRad)).toFloat() * 24f
        val camShiftY = (camTileX * sin(yawRad) + camTileY * cos(yawRad)).toFloat() * 14f
        val scale = profile.particleScale

        when (weather) {
            WeatherType.LIGHT_RAIN -> {
                val rainColorNear = Color(0xFFC8E3FF)
                val rainColorFar = Color(0xFF8CB8E8)
                for (i in 0 until count) {
                    val h1 = frac(i * 0.61803399f + 0.13f)
                    val h2 = frac(i * 0.38196601f + 0.47f)
                    val depth = 0.45f + 0.55f * frac(i * 0.75487766f)
                    val speed = (460f + 260f * depth)
                    val px = wrapMod(h1 * w + timeSec * weather.windX * speed - camShiftX * depth, w)
                    val py = wrapMod(h2 * h + timeSec * weather.windY * speed - camShiftY * depth, h)
                    val streakLen = (12f + 16f * depth) * scale
                    val dx = weather.windX * streakLen
                    val dy = weather.windY * streakLen
                    val dropAlpha = ((0.22f + 0.34f * depth) * alpha).coerceIn(0f, 0.72f)
                    scope.drawLine(
                        color = if (depth > 0.72f) rainColorNear.copy(alpha = dropAlpha) else rainColorFar.copy(alpha = dropAlpha),
                        start = Offset(px, py),
                        end = Offset(px + dx, py + dy),
                        strokeWidth = (1.1f + 0.9f * depth) * scale,
                        cap = StrokeCap.Round
                    )
                }
                // Ground splash ripples on High & HD
                if (profile.enableGroundRipples) {
                    val rippleCount = (count / 6).coerceIn(6, 18)
                    for (r in 0 until rippleCount) {
                        val phase = frac(timeSec * 1.15f + r * 0.371f)
                        val cycleIndex = (timeSec * 1.15f + r * 0.371f).toInt()
                        val rx = wrapMod(frac((r + cycleIndex * 13) * 0.7548776f) * w - camShiftX * 0.8f, w)
                        val ry = wrapMod(frac((r + cycleIndex * 29) * 0.5698403f) * h - camShiftY * 0.8f, h)
                        val radiusX = (3f + phase * 11f) * scale
                        val radiusY = radiusX * 0.48f
                        val rAlpha = ((1f - phase) * 0.34f * alpha).coerceIn(0f, 0.45f)
                        scope.drawOval(
                            color = Color(0xFFCFE6FF).copy(alpha = rAlpha),
                            topLeft = Offset(rx - radiusX, ry - radiusY),
                            size = Size(radiusX * 2f, radiusY * 2f),
                            style = Stroke(width = 1.2f)
                        )
                    }
                }
            }

            WeatherType.FALLING_LEAVES -> {
                for (i in 0 until count) {
                    val h1 = frac(i * 0.61803399f + 0.21f)
                    val h2 = frac(i * 0.38196601f + 0.63f)
                    val depth = 0.5f + 0.5f * frac(i * 0.43219f)
                    val speed = 64f + 42f * depth
                    val sway = sin(timeSec * (1.5f + depth) + i * 1.7f) * (24f * depth)
                    val px = wrapMod(h1 * w + timeSec * weather.windX * speed + sway - camShiftX * depth * 0.7f, w)
                    val py = wrapMod(h2 * h + timeSec * weather.windY * speed - camShiftY * depth * 0.7f, h)
                    val angle = timeSec * (1.8f + (i % 3) * 0.55f) + i
                    val rx = (4.2f + 3.4f * depth) * scale
                    val ry = rx * (0.42f + 0.25f * kotlin.math.abs(sin(angle)))
                    val cosA = cos(angle)
                    val sinA = sin(angle)

                    val leafColor = when (i % 4) {
                        0 -> Color(0xFFE69D3A) // Autumn Gold
                        1 -> Color(0xFFC85D2C) // Russet Leaf
                        2 -> Color(0xFFD9B24C) // Sunlit Amber
                        else -> Color(0xFF5FA649) // Canopy Emerald
                    }.copy(alpha = ((0.55f + 0.35f * depth) * alpha * (0.65f + 0.35f * daylightFactor)).coerceIn(0f, 0.92f))

                    leafPath.rewind()
                    leafPath.moveTo(px + rx * cosA, py + rx * sinA)
                    leafPath.lineTo(px - ry * sinA, py + ry * cosA)
                    leafPath.lineTo(px - rx * cosA, py - rx * sinA)
                    leafPath.lineTo(px + ry * sinA, py - ry * cosA)
                    leafPath.close()
                    scope.drawPath(leafPath, color = leafColor)
                }
            }

            WeatherType.SNOW -> {
                for (i in 0 until count) {
                    val h1 = frac(i * 0.61803399f + 0.09f)
                    val h2 = frac(i * 0.38196601f + 0.81f)
                    val depth = 0.38f + 0.62f * frac(i * 0.52917f)
                    val speed = 58f + 56f * depth
                    val drift = sin(timeSec * (0.9f + depth * 0.7f) + i * 2.13f) * (18f * depth)
                    val px = wrapMod(h1 * w + timeSec * weather.windX * speed + drift - camShiftX * depth * 0.65f, w)
                    val py = wrapMod(h2 * h + timeSec * weather.windY * speed - camShiftY * depth * 0.65f, h)
                    val radius = (1.6f + 2.4f * depth) * scale
                    val flakeAlpha = ((0.35f + 0.52f * depth) * alpha).coerceIn(0f, 0.92f)
                    if (profile.enableAtmosphericGradient && depth > 0.74f) {
                        scope.drawCircle(
                            color = Color(0xFFD8EEFF).copy(alpha = flakeAlpha * 0.28f),
                            radius = radius * 2.0f,
                            center = Offset(px, py)
                        )
                    }
                    scope.drawCircle(
                        color = Color(0xFFF3F9FF).copy(alpha = flakeAlpha),
                        radius = radius,
                        center = Offset(px, py)
                    )
                }
            }

            WeatherType.VOLCANIC_ASH -> {
                for (i in 0 until count) {
                    val h1 = frac(i * 0.61803399f + 0.31f)
                    val h2 = frac(i * 0.38196601f + 0.19f)
                    val depth = 0.42f + 0.58f * frac(i * 0.8317f)
                    val isEmber = (i % 5 == 0)
                    val speed = if (isEmber) 54f + 46f * depth else 42f + 34f * depth
                    val sway = sin(timeSec * (1.2f + depth) + i * 1.9f) * (16f * depth)
                    val vx = weather.windX * speed
                    val vy = if (isEmber) -0.58f * speed else weather.windY * speed
                    val px = wrapMod(h1 * w + timeSec * vx + sway - camShiftX * depth * 0.7f, w)
                    val py = wrapMod(h2 * h + timeSec * vy - camShiftY * depth * 0.7f, h)

                    if (isEmber) {
                        val pulse = 0.65f + 0.35f * sin(timeSec * 5.2f + i)
                        val emberRadius = (1.8f + 1.6f * depth) * scale
                        val emberAlpha = ((0.55f + 0.40f * depth) * pulse * alpha).coerceIn(0f, 0.95f)
                        if (profile.enableAtmosphericGradient) {
                            scope.drawCircle(
                                color = FireOrange.copy(alpha = emberAlpha * 0.34f),
                                radius = emberRadius * 2.8f,
                                center = Offset(px, py)
                            )
                        }
                        scope.drawCircle(
                            color = if (i % 10 == 0) TorchGold.copy(alpha = emberAlpha) else FireOrange.copy(alpha = emberAlpha),
                            radius = emberRadius,
                            center = Offset(px, py)
                        )
                    } else {
                        val ashRadius = (1.5f + 1.9f * depth) * scale
                        val ashAlpha = ((0.30f + 0.38f * depth) * alpha).coerceIn(0f, 0.72f)
                        scope.drawCircle(
                            color = Color(0xFFB3ABA6).copy(alpha = ashAlpha),
                            radius = ashRadius,
                            center = Offset(px, py)
                        )
                    }
                }
            }

            WeatherType.SWAMP_MIST -> {
                // Soft drifting low-lying fog puffs + occasional luminous marsh spores
                for (i in 0 until count) {
                    val h1 = frac(i * 0.61803399f + 0.53f)
                    val h2 = frac(i * 0.38196601f + 0.27f)
                    val depth = 0.4f + 0.6f * frac(i * 0.6731f)
                    val isSpore = (i % 3 != 0)
                    if (!isSpore) {
                        val speed = 22f + 18f * depth
                        val px = wrapMod(h1 * (w + 220f) + timeSec * speed - camShiftX * depth * 0.35f, w + 220f) - 110f
                        val py = h * (0.28f + 0.68f * h2) + sin(timeSec * 0.45f + i) * 18f
                        val fogRadius = (72f + 68f * depth) * scale
                        val puffColor = if (i % 6 == 0) Color(0xFF7D6B99) else Color(0xFF7FA892)
                        val puffAlpha = ((0.06f + 0.05f * depth) * alpha).coerceIn(0f, 0.14f)
                        val center = Offset(px, py)
                        scope.drawCircle(
                            color = puffColor.copy(alpha = puffAlpha * 0.55f),
                            radius = fogRadius,
                            center = center
                        )
                        scope.drawCircle(
                            color = puffColor.copy(alpha = puffAlpha),
                            radius = fogRadius * 0.58f,
                            center = center
                        )
                    } else {
                        val speed = 16f + 14f * depth
                        val swayX = sin(timeSec * 0.85f + i * 2.3f) * 20f
                        val swayY = cos(timeSec * 0.70f + i * 1.7f) * 14f
                        val px = wrapMod(h1 * w + timeSec * weather.windX * speed + swayX - camShiftX * depth * 0.6f, w)
                        val py = wrapMod(h2 * h + timeSec * weather.windY * speed + swayY - camShiftY * depth * 0.6f, h)
                        val shimmer = 0.55f + 0.45f * sin(timeSec * 2.8f + i)
                        val sporeR = (1.6f + 1.4f * depth) * scale
                        val sporeAlpha = ((0.35f + 0.40f * depth) * shimmer * alpha).coerceIn(0f, 0.82f)
                        val sporeColor = if (i % 4 == 0) SoulViolet else PoisonGreen
                        scope.drawCircle(
                            color = sporeColor.copy(alpha = sporeAlpha * 0.32f),
                            radius = sporeR * 2.4f,
                            center = Offset(px, py)
                        )
                        scope.drawCircle(
                            color = sporeColor.copy(alpha = sporeAlpha),
                            radius = sporeR,
                            center = Offset(px, py)
                        )
                    }
                }
            }

            WeatherType.CLEAR -> Unit
        }
    }

    private fun frac(x: Float): Float {
        val f = x - kotlin.math.floor(x)
        return if (f < 0f) f + 1f else f
    }

    private fun wrapMod(value: Float, modulus: Float): Float {
        if (modulus <= 0f) return 0f
        val r = value % modulus
        return if (r < 0f) r + modulus else r
    }

    companion object {
        private const val REGION_CROSSFADE_SEC: Float = 2.5f
    }
}

/**
 * Compact dark-stone HUD badge showing the active overland weather (and subtle override indicator).
 */
@Composable
fun LiveWeatherHudBadge(
    weather: ResolvedWeatherState,
    modifier: Modifier = Modifier
) {
    if (weather.isSuppressedUnderground) return

    val active = weather.activeWeather
    val (icon, accent) = weatherVisualToken(active)

    Surface(
        color = BasaltDark.copy(alpha = 0.84f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (weather.isAdminOverride) TorchGold.copy(alpha = 0.85f) else StoneBorderLight.copy(alpha = 0.8f)
        ),
        modifier = modifier.testTag("live_weather_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = active.displayName,
                tint = accent,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = active.displayName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ParchmentLight,
                maxLines = 1
            )
            if (weather.isAdminOverride) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = ParchmentMuted
                )
                Text(
                    text = "OVR",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = TorchGold
                )
            }
        }
    }
}

internal fun weatherVisualToken(weather: WeatherType): Pair<ImageVector, Color> = when (weather) {
    WeatherType.CLEAR -> Icons.Default.WbSunny to TorchGold
    WeatherType.LIGHT_RAIN -> Icons.Default.WaterDrop to Color(0xFF70B8FF)
    WeatherType.FALLING_LEAVES -> Icons.Default.Eco to TorchAmber
    WeatherType.SNOW -> Icons.Default.AcUnit to Color(0xFFC8E7FF)
    WeatherType.VOLCANIC_ASH -> Icons.Default.Whatshot to FireOrange
    WeatherType.SWAMP_MIST -> Icons.Default.BlurOn to ExpEmerald
}
