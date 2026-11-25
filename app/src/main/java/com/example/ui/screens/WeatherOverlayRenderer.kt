package com.example.ui.screens

import android.graphics.RuntimeShader
import android.os.Build
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
import androidx.compose.ui.graphics.ShaderBrush
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
import kotlin.math.sin

/**
 * Zero-GC procedural weather renderer & Android 13+ AGSL atmospheric weather shader pass.
 *
 * Scales smoothly across all four [GraphicsQuality] tiers:
 * - LOW: Ultra-light ambient tint only (0 particles, 0 shader)
 * - MEDIUM: Lightweight single-layer particles + subtle atmospheric veil
 * - HIGH: Multi-depth parallax particles, ground rain ripples, ember glows & multi-stop atmospheric fog gradients
 * - HD: Full cinematic parallax particles + Android 13+ AGSL [RuntimeShader] atmospheric weather pass
 *       (with seamless layered gradient fallback on API 24-32)
 */
class WeatherOverlayRenderer {
    private var agslShader: RuntimeShader? = null
    private var agslAttempted: Boolean = false

    // Smooth region-crossing / admin-override interpolation state
    private var displayedWeather: WeatherType = WeatherType.CLEAR
    private var fadingWeather: WeatherType = WeatherType.CLEAR
    private var transitionProgress: Float = 1f
    private var lastFrameMs: Long = 0L
    private val leafPath = Path()

    val isRuntimeShaderSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private fun getOrInitShader(): RuntimeShader? {
        if (!isRuntimeShaderSupported) return null
        if (!agslAttempted) {
            agslAttempted = true
            agslShader = runCatching {
                RuntimeShader(HD_WEATHER_AGSL)
            }.getOrNull()
        }
        return agslShader
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

        // HD Tier on Android 13+: AGSL RuntimeShader procedural weather atmosphere
        if (profile.enableAgslShader) {
            val shader = getOrInitShader()
            if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching {
                    shader.setFloatUniform("uResolution", w, h)
                    shader.setFloatUniform("uTime", timeSec)
                    shader.setFloatUniform("uWeatherMode", weather.shaderModeId)
                    shader.setFloatUniform("uIntensity", intensity)
                    shader.setFloatUniform("uWind", weather.windX, weather.windY)
                    shader.setFloatUniform("uDaylight", daylightFactor)
                    scope.drawRect(
                        brush = ShaderBrush(shader),
                        topLeft = Offset.Zero,
                        size = scope.size
                    )
                    return
                }
            }
        }

        // HIGH Tier (and HD API 24-32 fallback): Layered multi-stop atmospheric weather gradients
        if (profile.enableAtmosphericGradient) {
            val drift = sin(timeSec * 0.42f) * w * 0.08f
            when (weather) {
                WeatherType.LIGHT_RAIN -> {
                    scope.drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color(0xFF1F334D).copy(alpha = 0.22f * intensity),
                            0.55f to Color(0xFF284160).copy(alpha = 0.11f * intensity),
                            1.0f to Color(0xFF132236).copy(alpha = 0.18f * intensity)
                        ),
                        topLeft = Offset.Zero,
                        size = scope.size
                    )
                }
                WeatherType.FALLING_LEAVES -> {
                    scope.drawRect(
                        brush = Brush.radialGradient(
                            0.0f to Color(0xFFF2B359).copy(alpha = 0.09f * intensity * daylightFactor),
                            0.72f to Color.Transparent,
                            1.0f to Color(0xFF5A3718).copy(alpha = 0.12f * intensity),
                            center = Offset(w * 0.25f + drift, h * 0.18f),
                            radius = maxOf(w, h) * 0.88f
                        ),
                        topLeft = Offset.Zero,
                        size = scope.size
                    )
                }
                WeatherType.SNOW -> {
                    scope.drawRect(
                        brush = Brush.radialGradient(
                            0.0f to Color(0xFFDCEEFF).copy(alpha = 0.06f * intensity),
                            0.62f to Color(0xFFB7D6F5).copy(alpha = 0.10f * intensity),
                            1.0f to Color(0xFF8CB4E0).copy(alpha = 0.22f * intensity),
                            center = Offset(w * 0.5f, h * 0.5f),
                            radius = maxOf(w, h) * 0.72f
                        ),
                        topLeft = Offset.Zero,
                        size = scope.size
                    )
                }
                WeatherType.VOLCANIC_ASH -> {
                    scope.drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color(0xFF2B2220).copy(alpha = 0.20f * intensity),
                            0.60f to Color(0xFF3E2920).copy(alpha = 0.12f * intensity),
                            1.0f to Color(0xFF8C3618).copy(alpha = 0.20f * intensity)
                        ),
                        topLeft = Offset.Zero,
                        size = scope.size
                    )
                }
                WeatherType.SWAMP_MIST -> {
                    val pulse = 0.85f + 0.15f * sin(timeSec * 0.55f)
                    scope.drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color(0xFF1F332B).copy(alpha = 0.14f * intensity),
                            0.55f to Color(0xFF47695B).copy(alpha = 0.18f * intensity * pulse),
                            1.0f to Color(0xFF729E88).copy(alpha = 0.26f * intensity * pulse)
                        ),
                        topLeft = Offset.Zero,
                        size = scope.size
                    )
                    scope.drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to Color(0xFF89B89E).copy(alpha = 0.16f * intensity * pulse),
                            1.0f to Color.Transparent,
                            center = Offset(w * 0.5f + drift * 1.6f, h * 0.72f),
                            radius = maxOf(w, h) * 0.55f
                        ),
                        radius = maxOf(w, h) * 0.55f,
                        center = Offset(w * 0.5f + drift * 1.6f, h * 0.72f)
                    )
                }
                WeatherType.CLEAR -> Unit
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

                    leafPath.reset()
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
                    if (profile.enableAgslShader && depth > 0.70f) {
                        scope.drawCircle(
                            color = Color(0xFFD8EEFF).copy(alpha = flakeAlpha * 0.32f),
                            radius = radius * 2.1f,
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
                        val puffAlpha = ((0.09f + 0.07f * depth) * alpha).coerceIn(0f, 0.20f)
                        scope.drawCircle(
                            brush = Brush.radialGradient(
                                0.0f to puffColor.copy(alpha = puffAlpha),
                                1.0f to Color.Transparent,
                                center = Offset(px, py),
                                radius = fogRadius
                            ),
                            radius = fogRadius,
                            center = Offset(px, py)
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

        /**
         * Android 13+ (API 33+) AGSL procedural weather atmosphere shader.
         * Modes: 1 = Light Rain, 2 = Falling Leaves, 3 = Snow, 4 = Volcanic Ash, 5 = Swamp Mist.
         */
        internal const val HD_WEATHER_AGSL = """
            uniform float2 uResolution;
            uniform float uTime;
            uniform float uWeatherMode;
            uniform float uIntensity;
            uniform float2 uWind;
            uniform float uDaylight;

            float hash21(float2 p) {
                p = fract(p * float2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            float vnoise(float2 p) {
                float2 i = floor(p);
                float2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = hash21(i);
                float b = hash21(i + float2(1.0, 0.0));
                float c = hash21(i + float2(0.0, 1.0));
                float d = hash21(i + float2(1.0, 1.0));
                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }

            float fbm(float2 p) {
                float v = 0.0;
                float a = 0.5;
                for (int k = 0; k < 3; k++) {
                    v += a * vnoise(p);
                    p = p * 2.03 + float2(7.1, 13.7);
                    a *= 0.5;
                }
                return v;
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / max(uResolution, float2(1.0, 1.0));
                float2 centered = (uv - 0.5) * float2(1.15, 1.0);
                float edge = smoothstep(0.32, 0.96, length(centered));

                float3 col = float3(0.0);
                float alpha = 0.0;

                if (uWeatherMode > 0.5 && uWeatherMode < 1.5) {
                    // LIGHT RAIN: cool overcast sheen + angled drifting mist bands
                    float2 rainUv = float2(uv.x * 5.0 + uv.y * 1.4 - uTime * 0.22, uv.y * 3.0 - uTime * 0.45);
                    float sheen = fbm(rainUv);
                    col = mix(float3(0.11, 0.20, 0.32), float3(0.45, 0.62, 0.82), sheen * 0.45);
                    alpha = (0.11 + 0.10 * sheen + 0.08 * edge) * uIntensity;
                } else if (uWeatherMode >= 1.5 && uWeatherMode < 2.5) {
                    // FALLING LEAVES: warm dappled canopy sunlight & golden breeze glow
                    float dapple = fbm(uv * 3.2 - float2(uTime * 0.14, uTime * 0.08));
                    col = mix(float3(0.55, 0.34, 0.12), float3(0.96, 0.72, 0.34), dapple);
                    alpha = (0.04 + 0.08 * dapple * uDaylight + 0.05 * edge) * uIntensity;
                } else if (uWeatherMode >= 2.5 && uWeatherMode < 3.5) {
                    // SNOW: drifting alpine snow-haze + frosty crisp edge bloom
                    float drift = fbm(uv * 3.6 + float2(uTime * 0.18, -uTime * 0.12));
                    col = mix(float3(0.62, 0.78, 0.94), float3(0.88, 0.95, 1.0), edge * 0.65 + drift * 0.35);
                    alpha = (0.06 + 0.16 * edge + 0.08 * drift) * uIntensity;
                } else if (uWeatherMode >= 3.5 && uWeatherMode < 4.5) {
                    // VOLCANIC ASH: smoky dark haze + rising ember glow near lower horizon
                    float smoke = fbm(uv * 3.4 + float2(uTime * 0.12, uTime * 0.16));
                    float emberBand = smoothstep(0.45, 1.0, uv.y) * (0.65 + 0.35 * sin(uv.x * 8.0 + uTime * 2.2));
                    col = mix(float3(0.18, 0.13, 0.12), float3(0.76, 0.28, 0.10), emberBand * 0.75);
                    alpha = (0.12 + 0.10 * smoke + 0.12 * emberBand) * uIntensity;
                } else if (uWeatherMode >= 4.5) {
                    // SWAMP MIST: undulating low-lying jade-violet bog fog waves
                    float fogA = fbm(float2(uv.x * 2.8 - uTime * 0.10, uv.y * 2.4 + uTime * 0.03));
                    float fogB = fbm(float2(uv.x * 4.5 + uTime * 0.07, uv.y * 3.2 - uTime * 0.04));
                    float lowBand = smoothstep(0.15, 0.95, uv.y);
                    float fog = mix(fogA, fogB, 0.45) * (0.45 + 0.55 * lowBand);
                    col = mix(float3(0.24, 0.42, 0.36), float3(0.52, 0.68, 0.60), fogB);
                    alpha = (0.08 + 0.24 * fog) * uIntensity;
                }

                alpha = clamp(alpha, 0.0, 0.36);
                return half4(col * alpha, alpha);
            }
        """
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
