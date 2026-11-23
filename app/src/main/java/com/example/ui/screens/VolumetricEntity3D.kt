package com.example.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.game.model.GraphicsQuality
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Shared volumetric 2.5D/3D shading primitives with crisp `#14100E` ink outlines, cylindrical &
 * spherical light-to-shadow gradients, subtle rim highlights, and dynamic world-light ground shadows.
 * Zero-allocation in hot Compose Canvas loops: reuses caller-provided [Path].
 */
object VolumetricShading {

    val INK = Color(0xFF14100E)
    private val COOL_RIM_TINT = Color(0xFFBFD4FF)
    private val WARM_SPEC_TINT = Color(0xFFFFF4D6)

    /** Heroic scale multiplier (~20% larger grounded 2.5D/3D presence relative to tile height). */
    const val HEROIC_HUMANOID_SCALE = 1.18f
    const val HEROIC_MONSTER_SCALE = 1.16f

    /** Blends [base] toward cool twilight dusk indigo for volumetric core shadows. */
    fun coolShadow(base: Color, amount: Float = 0.48f): Color {
        val t = amount.coerceIn(0f, 1f)
        return Color(
            red = (base.red * (1f - t) + 0.08f * t).coerceIn(0f, 1f),
            green = (base.green * (1f - t) + 0.10f * t).coerceIn(0f, 1f),
            blue = (base.blue * (1f - t) + 0.22f * t).coerceIn(0f, 1f),
            alpha = base.alpha
        )
    }

    /** Blends [base] toward warm top-left sunlight for volumetric highlights. */
    fun warmHighlight(base: Color, amount: Float = 0.36f): Color {
        val t = amount.coerceIn(0f, 1f)
        return Color(
            red = (base.red + (WARM_SPEC_TINT.red - base.red) * t).coerceIn(0f, 1f),
            green = (base.green + (WARM_SPEC_TINT.green - base.green) * t).coerceIn(0f, 1f),
            blue = (base.blue + (WARM_SPEC_TINT.blue - base.blue) * t).coerceIn(0f, 1f),
            alpha = base.alpha
        )
    }

    /**
     * Draws a realistic angled elliptical ground shadow projected along Rookvale's world directional
     * light vector, scaling and shifting dynamically during jump/hop strides or combat lunges,
     * layered over tight ambient contact darkening directly under feet/paws.
     */
    fun drawGroundedEntityShadow(
        scope: DrawScope,
        camera: IsometricCamera,
        cx: Float,
        feetY: Float,
        radiusScale: Float,
        heightTiles: Float,
        jumpLiftRatio: Float = 0f,
        lungeShiftX: Float = 0f,
        lungeShiftY: Float = 0f,
        quality: GraphicsQuality
    ) = with(scope) {
        if (!quality.drawContactShadows) return@with
        val hw = camera.halfW
        val hh = camera.halfH
        val lift = jumpLiftRatio.coerceIn(0f, 1f)
        val spreadScale = (1f - lift * 0.16f).coerceIn(0.76f, 1.05f)
        val sx = cx + lungeShiftX * 0.65f
        val sy = feetY + lungeShiftY * 0.50f

        if (quality.drawCastShadows) {
            val shadowShift = IsometricLightingEngine.castShadowScreenOffset(camera, heightTiles * (1f + lift * 0.35f))
            val shadowAngleDeg = (atan2(shadowShift.y.toDouble(), shadowShift.x.toDouble()) * 180.0 / PI).toFloat()
            val lenPx = hypot(shadowShift.x, shadowShift.y).coerceAtLeast(hw * 0.45f)
            val castW = (lenPx * 1.48f + hw * 0.56f) * radiusScale * spreadScale
            val castH = hh * 0.68f * radiusScale * spreadScale
            val castCx = sx + shadowShift.x * 0.58f
            val castCy = sy + shadowShift.y * 0.58f

            withTransform({ rotate(shadowAngleDeg, Offset(castCx, castCy)) }) {
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x52050814).copy(alpha = (0.48f - lift * 0.12f).coerceIn(0.18f, 0.54f)),
                            Color(0x30070C1B),
                            Color.Transparent
                        ),
                        center = Offset(castCx, castCy),
                        radius = (castW * 0.56f).coerceAtLeast(1f)
                    ),
                    topLeft = Offset(castCx - castW * 0.55f, castCy - castH * 0.58f),
                    size = Size(castW * 1.10f, castH * 1.16f)
                )
            }
        }

        // Tight ambient contact occlusion pool directly beneath feet/paws
        val contactW = hw * 0.94f * radiusScale * spreadScale
        val contactH = hh * 0.58f * radiusScale * spreadScale
        val contactAlpha = (if (quality.drawHdStonePolish) 0.68f else 0.56f) * (1f - lift * 0.28f)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF05070E).copy(alpha = contactAlpha),
                    Color(0xFF070A14).copy(alpha = contactAlpha * 0.52f),
                    Color.Transparent
                ),
                center = Offset(sx, sy),
                radius = (contactW * 0.56f).coerceAtLeast(1f)
            ),
            topLeft = Offset(sx - contactW * 0.5f, sy - contactH * 0.5f),
            size = Size(contactW, contactH)
        )
    }

    /** Draws subtle ambient contact darkening directly under an individual planted foot or paw. */
    fun drawFootContactDot(scope: DrawScope, footPos: Offset, radiusX: Float, radiusY: Float) {
        scope.drawOval(
            color = Color(0x5805070D),
            topLeft = Offset(footPos.x - radiusX, footPos.y - radiusY * 0.55f),
            size = Size(radiusX * 2f, radiusY * 1.1f)
        )
    }

    /**
     * Draws a volumetric 3D ellipsoid/sphere with offset warm top-left sunlight specular, rich midtone,
     * cool bottom-right core shadow, optional subtle rim highlight, and crisp dark ink outline.
     */
    fun drawVolumetricEllipsoid(
        scope: DrawScope,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        main: Color,
        highlight: Color = warmHighlight(main, 0.36f),
        shadow: Color = coolShadow(main, 0.50f),
        outlineW: Float,
        lightX: Float = -0.70f,
        lightY: Float = -0.62f,
        useSpecular: Boolean = true,
        useRim: Boolean = false
    ) = with(scope) {
        val safeRx = rx.coerceAtLeast(0.5f)
        val safeRy = ry.coerceAtLeast(0.5f)
        val topLeft = Offset(cx - safeRx, cy - safeRy)
        val sz = Size(safeRx * 2f, safeRy * 2f)

        val focusX = cx + lightX * safeRx * 0.45f
        val focusY = cy + lightY * safeRy * 0.48f
        val gradR = (maxOf(safeRx, safeRy) * 1.55f).coerceAtLeast(1f)

        drawOval(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to highlight,
                    0.46f to main,
                    0.85f to shadow,
                    1.0f to coolShadow(shadow, 0.35f)
                ),
                center = Offset(focusX, focusY),
                radius = gradR
            ),
            topLeft = topLeft,
            size = sz
        )

        if (useSpecular) {
            val specW = safeRx * 0.62f
            val specH = safeRy * 0.38f
            drawOval(
                color = WARM_SPEC_TINT.copy(alpha = 0.24f),
                topLeft = Offset(focusX - specW * 0.45f, focusY - specH * 0.45f),
                size = Size(specW, specH)
            )
        }

        if (useRim) {
            drawArc(
                color = COOL_RIM_TINT.copy(alpha = 0.30f),
                startAngle = 15f,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = Offset(topLeft.x + outlineW * 0.6f, topLeft.y + outlineW * 0.6f),
                size = Size((sz.width - outlineW * 1.2f).coerceAtLeast(1f), (sz.height - outlineW * 1.2f).coerceAtLeast(1f)),
                style = Stroke(width = (outlineW * 0.85f).coerceAtLeast(0.9f), cap = StrokeCap.Round)
            )
        }

        drawOval(INK, topLeft = topLeft, size = sz, style = Stroke(width = outlineW))
    }

    /**
     * Draws a tapered volumetric 3D cylinder/limb between [start] and [end] with cross-sectional
     * cylindrical gradient (sunlit side -> midtone -> cool core shadow) and crisp dark ink outline.
     */
    fun drawVolumetricCylinder(
        scope: DrawScope,
        start: Offset,
        end: Offset,
        rStart: Float,
        rEnd: Float,
        main: Color,
        highlight: Color = warmHighlight(main, 0.38f),
        shadow: Color = coolShadow(main, 0.50f),
        outlineW: Float,
        lightX: Float = -0.70f,
        useHighlightStrip: Boolean = true,
        path: Path
    ) = with(scope) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        val nx = -dy / len
        val ny = dx / len
        // Align normal toward light side so gradient runs sunlit -> shaded across cylinder thickness
        val dot = nx * lightX + ny * -0.55f
        val lx = if (dot >= 0f) nx else -nx
        val ly = if (dot >= 0f) ny else -ny

        val s1 = Offset(start.x + lx * rStart, start.y + ly * rStart)
        val s2 = Offset(start.x - lx * rStart, start.y - ly * rStart)
        val e2 = Offset(end.x - lx * rEnd, end.y - ly * rEnd)
        val e1 = Offset(end.x + lx * rEnd, end.y + ly * rEnd)

        path.reset()
        path.moveTo(s1.x, s1.y)
        path.lineTo(e1.x, e1.y)
        path.quadraticTo(
            end.x + (dx / len) * rEnd * 0.85f,
            end.y + (dy / len) * rEnd * 0.85f,
            e2.x, e2.y
        )
        path.lineTo(s2.x, s2.y)
        path.quadraticTo(
            start.x - (dx / len) * rStart * 0.85f,
            start.y - (dy / len) * rStart * 0.85f,
            s1.x, s1.y
        )
        path.close()

        val midX = (start.x + end.x) * 0.5f
        val midY = (start.y + end.y) * 0.5f
        val avgR = ((rStart + rEnd) * 0.5f).coerceAtLeast(1f)
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to highlight,
                    0.45f to main,
                    0.88f to shadow,
                    1.0f to coolShadow(shadow, 0.30f)
                ),
                start = Offset(midX + lx * avgR * 1.1f, midY + ly * avgR * 1.1f),
                end = Offset(midX - lx * avgR * 1.1f, midY - ly * avgR * 1.1f)
            )
        )
        if (useHighlightStrip) {
            drawLine(
                color = WARM_SPEC_TINT.copy(alpha = 0.25f),
                start = Offset(start.x + lx * rStart * 0.42f, start.y + ly * rStart * 0.42f),
                end = Offset(end.x + lx * rEnd * 0.42f, end.y + ly * rEnd * 0.42f),
                strokeWidth = (avgR * 0.34f).coerceAtLeast(0.8f),
                cap = StrokeCap.Round
            )
        }
        drawPath(path, INK, style = Stroke(width = outlineW, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }

    /**
     * Draws a volumetric 3D tapered torso/ribcage prism with foreshortened top shoulder dome,
     * cylindrical body gradient, optional rim light, and crisp ink outline.
     */
    fun drawVolumetricTorso(
        scope: DrawScope,
        topCenter: Offset,
        bottomCenter: Offset,
        topHalfW: Float,
        botHalfW: Float,
        domeHeight: Float,
        main: Color,
        highlight: Color,
        shadow: Color,
        outlineW: Float,
        lightX: Float,
        useRim: Boolean,
        path: Path
    ) = with(scope) {
        path.reset()
        path.moveTo(topCenter.x - topHalfW, topCenter.y)
        path.quadraticTo(topCenter.x, topCenter.y - domeHeight, topCenter.x + topHalfW, topCenter.y)
        path.lineTo(bottomCenter.x + botHalfW, bottomCenter.y)
        path.quadraticTo(bottomCenter.x, bottomCenter.y + domeHeight * 0.72f, bottomCenter.x - botHalfW, bottomCenter.y)
        path.close()

        val litStartX = topCenter.x + lightX * topHalfW * 1.15f
        val litEndX = bottomCenter.x - lightX * botHalfW * 1.25f
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to highlight,
                    0.42f to main,
                    0.86f to shadow,
                    1.0f to coolShadow(shadow, 0.32f)
                ),
                start = Offset(litStartX, topCenter.y - domeHeight * 0.6f),
                end = Offset(litEndX, bottomCenter.y + domeHeight * 0.4f)
            )
        )
        if (useRim) {
            val rimSign = if (lightX < 0f) 1f else -1f
            drawLine(
                color = COOL_RIM_TINT.copy(alpha = 0.28f),
                start = Offset(topCenter.x + rimSign * topHalfW * 0.88f, topCenter.y + domeHeight * 0.1f),
                end = Offset(bottomCenter.x + rimSign * botHalfW * 0.88f, bottomCenter.y - domeHeight * 0.1f),
                strokeWidth = (outlineW * 0.95f).coerceAtLeast(1f),
                cap = StrokeCap.Round
            )
        }
        drawPath(path, INK, style = Stroke(width = outlineW, join = StrokeJoin.Round))
    }
}
