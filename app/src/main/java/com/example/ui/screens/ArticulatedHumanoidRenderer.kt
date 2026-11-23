package com.example.ui.screens

import androidx.compose.ui.geometry.CornerRadius
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
import com.example.game.model.Vocation
import com.example.game.world.NpcRole
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Crisp dark-ink-outlined, directionally shaded articulated vector renderer for Players (with live
 * paper-doll equipment in hand and worn on body) and thematic Rookvale NPCs.
 * Zero-allocation in hot loops: reuses caller-provided Path instances where needed.
 */
object ArticulatedHumanoidRenderer {

    private val INK_OUTLINE = Color(0xFF14100E)
    private val BELT_LEATHER = Color(0xFF4F3118)
    private val BELT_BUCKLE = Color(0xFFE8BC58)

    fun drawPlayer(
        scope: DrawScope,
        cx: Float,
        feetY: Float,
        heightPx: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        walkPhase: Float,
        attackProgress: Float, // 0f = idle, (0f..1f) = active attack swing/draw/cast
        nowMs: Long,
        quality: GraphicsQuality,
        path: Path
    ) = with(scope) {
        val u = heightPx / 46f // 1 logical unit ~ 1/46th of character height
        val strokeW = (u * 1.15f).coerceIn(1.1f, 3.0f)
        val useHighlights = quality != GraphicsQuality.LOW
        val useHdBloom = quality == GraphicsQuality.HD || quality == GraphicsQuality.HIGH

        val isMoving = abs(walkPhase) > 0.01f
        val stride = sin(walkPhase * PI.toFloat() * 2f)
        val bobY = if (isMoving) -abs(stride) * 1.4f * u else sin(nowMs * 0.0032f) * 0.55f * u

        // Attack lunge recoil & swing curve (fast strike out, smooth follow-through)
        val atkEase = if (attackProgress in 0.001f..0.999f) {
            sin(attackProgress * PI.toFloat())
        } else {
            0f
        }
        val lungeX = facing.screenDirX * atkEase * 3.8f * u
        val lungeY = facing.screenDirY * atkEase * 1.9f * u

        val ox = cx + lungeX
        val oy = feetY + bobY + lungeY

        val isSide = facing.quadrant == ScreenFacing.LEFT || facing.quadrant == ScreenFacing.RIGHT
        val flipX = if (facing.quadrant == ScreenFacing.LEFT) -1f else 1f
        val isBack = facing.quadrant == ScreenFacing.BACK

        // 1. Back layer: cape / robe train / quiver when facing front or side
        if (appearance.armorKind == ArmorVisualKind.ROBE || appearance.vocation.name == "PALADIN") {
            drawCloakBack(ox, oy, u, strokeW, appearance, isSide, flipX, isBack, stride, path)
        }

        // 2. Back arm / off-hand if behind torso in side profile or back view
        if (isBack) {
            drawHeldWeapon(ox, oy, u, strokeW, appearance, facing, stride, atkEase, attackProgress, nowMs, quality, path, behindTorso = true)
            drawOffhand(ox, oy, u, strokeW, appearance, facing, stride, nowMs, quality, path, behindTorso = true)
        } else if (isSide) {
            // Far arm (off-hand side) drawn behind torso
            drawArm(
                shoulderX = ox + flipX * 2.2f * u,
                shoulderY = oy - 24.5f * u,
                u = u,
                strokeW = strokeW,
                palette = appearance.armorPalette,
                skinColor = appearance.skinShadow,
                swingDeg = -stride * 22f + (if (appearance.isTwoHanded) -25f - atkEase * 25f else 0f),
                sideSign = flipX,
                useHighlights = useHighlights,
                path = path
            )
            if (appearance.offhandKind != OffhandVisualKind.NONE) {
                drawOffhand(ox, oy, u, strokeW, appearance, facing, stride, nowMs, quality, path, behindTorso = true)
            }
        }

        // 3. Articulated legs & boots (with stride)
        drawLegsAndBoots(ox, oy, u, strokeW, appearance, isSide, flipX, stride, useHighlights, path)

        // 4. Tapered Torso + Layered Armor + Waist Belt
        drawTorsoAndArmor(ox, oy, u, strokeW, appearance, isSide, flipX, isBack, useHighlights, useHdBloom, path)

        // 5. Rounded Head, Hair, Face/Eyes & Helmet/Hood
        drawHeadAndHelmet(ox, oy, u, strokeW, appearance, isSide, flipX, isBack, useHighlights, path)

        // 6. Foreground Arms, Shield/Off-hand, Main-hand Weapon & Attack Swing Arcs
        if (!isBack) {
            if (!isSide) {
                // Front view: off-hand on viewer-right, weapon hand on viewer-left
                val leftSwing = -stride * 18f - atkEase * 68f
                val rightSwing = stride * 18f + atkEase * 18f
                drawArm(
                    shoulderX = ox - 7.0f * u,
                    shoulderY = oy - 24.5f * u,
                    u = u,
                    strokeW = strokeW,
                    palette = appearance.armorPalette,
                    skinColor = appearance.skinPrimary,
                    swingDeg = leftSwing,
                    sideSign = -1f,
                    useHighlights = useHighlights,
                    path = path
                )
                drawArm(
                    shoulderX = ox + 7.0f * u,
                    shoulderY = oy - 24.5f * u,
                    u = u,
                    strokeW = strokeW,
                    palette = appearance.armorPalette,
                    skinColor = appearance.skinPrimary,
                    swingDeg = rightSwing,
                    sideSign = 1f,
                    useHighlights = useHighlights,
                    path = path
                )
                drawOffhand(ox, oy, u, strokeW, appearance, facing, stride, nowMs, quality, path, behindTorso = false)
                drawHeldWeapon(ox, oy, u, strokeW, appearance, facing, stride, atkEase, attackProgress, nowMs, quality, path, behindTorso = false)
            } else {
                // Side profile: near weapon arm in front
                val swingDeg = stride * 24f - atkEase * 72f
                drawArm(
                    shoulderX = ox - flipX * 1.4f * u,
                    shoulderY = oy - 24.5f * u,
                    u = u,
                    strokeW = strokeW,
                    palette = appearance.armorPalette,
                    skinColor = appearance.skinPrimary,
                    swingDeg = swingDeg * flipX,
                    sideSign = flipX,
                    useHighlights = useHighlights,
                    path = path
                )
                drawHeldWeapon(ox, oy, u, strokeW, appearance, facing, stride, atkEase, attackProgress, nowMs, quality, path, behindTorso = false)
            }
        } else {
            // Back view arms
            drawArm(ox - 7.0f * u, oy - 24.5f * u, u, strokeW, appearance.armorPalette, appearance.skinShadow, stride * 16f, -1f, useHighlights, path)
            drawArm(ox + 7.0f * u, oy - 24.5f * u, u, strokeW, appearance.armorPalette, appearance.skinShadow, -stride * 16f - atkEase * 48f, 1f, useHighlights, path)
        }

        // 7. Combat Slash Arc / Arcane Cast Trail
        if (attackProgress in 0.04f..0.95f && quality != GraphicsQuality.LOW) {
            drawCombatAttackTrail(ox, oy, u, appearance, facing, attackProgress, quality)
        }
    }

    fun drawNpc(
        scope: DrawScope,
        cx: Float,
        feetY: Float,
        heightPx: Float,
        role: NpcRole,
        facing: ResolvedScreenFacing,
        nowMs: Long,
        quality: GraphicsQuality,
        path: Path
    ) = with(scope) {
        val u = heightPx / 46f
        val strokeW = (u * 1.15f).coerceIn(1.1f, 3.0f)
        val useHighlights = quality != GraphicsQuality.LOW
        val useHdBloom = quality == GraphicsQuality.HD || quality == GraphicsQuality.HIGH
        val bob = sin(nowMs * 0.0028f) * (if (role == NpcRole.ORACLE) 2.2f else 0.65f) * u
        val ox = cx
        val oy = feetY + bob

        when (role) {
            NpcRole.TEMPLE_PRIEST -> drawBrotherAlden(ox, oy, u, strokeW, facing, useHighlights, useHdBloom, nowMs, path)
            NpcRole.ORACLE -> drawTheOracle(ox, oy, u, strokeW, useHighlights, useHdBloom, nowMs, path)
            NpcRole.SHOP_ALCHEMIST -> drawAlaric(ox, oy, u, strokeW, facing, useHighlights, useHdBloom, nowMs, path)
        }
    }

    private fun DrawScope.drawCloakBack(
        ox: Float,
        oy: Float,
        u: Float,
        strokeW: Float,
        appearance: PaperDollAppearance,
        isSide: Boolean,
        flipX: Float,
        isBack: Boolean,
        stride: Float,
        path: Path
    ) {
        val sway = stride * 1.8f * u
        path.reset()
        if (isSide) {
            path.moveTo(ox - flipX * 2.5f * u, oy - 26f * u)
            path.lineTo(ox - flipX * (8.5f * u + abs(sway)), oy - 6f * u)
            path.lineTo(ox - flipX * 1.5f * u, oy - 5f * u)
            path.lineTo(ox + flipX * 2.5f * u, oy - 24f * u)
            path.close()
        } else {
            val wTop = if (isBack) 7.8f * u else 7.2f * u
            val wBot = if (isBack) 10.6f * u else 9.6f * u
            path.moveTo(ox - wTop, oy - 25.5f * u)
            path.lineTo(ox - wBot + sway * 0.5f, oy - 5.5f * u)
            path.quadraticTo(ox, oy - 3.6f * u, ox + wBot + sway * 0.5f, oy - 5.5f * u)
            path.lineTo(ox + wTop, oy - 25.5f * u)
            path.close()
        }
        drawPath(path, color = appearance.armorPalette.shadow)
        if (isBack) {
            drawPath(
                path,
                brush = Brush.linearGradient(
                    colors = listOf(appearance.armorPalette.highlight, appearance.armorPalette.primary, appearance.armorPalette.shadow),
                    start = Offset(ox - 9f * u, oy - 26f * u),
                    end = Offset(ox + 9f * u, oy - 5f * u)
                )
            )
            // Gold trim hem on back of robe/cloak
            drawLine(
                color = appearance.armorPalette.accent,
                start = Offset(ox - 8.8f * u, oy - 7.5f * u),
                end = Offset(ox + 8.8f * u, oy - 7.5f * u),
                strokeWidth = 1.6f * u
            )
        }
        drawPath(path, color = INK_OUTLINE, style = Stroke(width = strokeW, join = StrokeJoin.Round))
    }

    private fun DrawScope.drawLegsAndBoots(
        ox: Float,
        oy: Float,
        u: Float,
        strokeW: Float,
        appearance: PaperDollAppearance,
        isSide: Boolean,
        flipX: Float,
        stride: Float,
        useHighlights: Boolean,
        path: Path
    ) {
        val leftStrideX = if (isSide) stride * 4.0f * u * flipX else 0f
        val rightStrideX = if (isSide) -stride * 4.0f * u * flipX else 0f
        val leftLiftY = if (isSide) (-stride.coerceAtLeast(0f) * 1.8f * u) else (stride * 2.0f * u)
        val rightLiftY = if (isSide) (stride.coerceAtMost(0f) * 1.8f * u) else (-stride * 2.0f * u)

        val lHipX = if (isSide) ox - flipX * 0.6f * u else ox - 3.4f * u
        val rHipX = if (isSide) ox + flipX * 0.6f * u else ox + 3.4f * u
        val hipY = oy - 12.2f * u

        fun drawLeg(hx: Float, footX: Float, footY: Float, shaded: Boolean) {
            val legCol = if (shaded) appearance.legsPalette.shadow else appearance.legsPalette.primary
            val bootCol = if (shaded) appearance.bootsPalette.shadow else appearance.bootsPalette.primary
            // Upper leg / greave
            path.reset()
            path.moveTo(hx - 2.3f * u, hipY)
            path.lineTo(footX - 2.1f * u, footY - 4.2f * u)
            path.lineTo(footX + 2.1f * u, footY - 4.2f * u)
            path.lineTo(hx + 2.3f * u, hipY)
            path.close()
            drawPath(path, color = legCol)
            drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW, join = StrokeJoin.Round))

            // Articulated boot with cuff & toe
            val bootW = if (isSide) 5.8f * u else 4.8f * u
            val bootX = if (isSide) footX - (if (flipX > 0) 2.2f * u else 3.6f * u) else footX - bootW * 0.5f
            drawRoundRect(
                color = bootCol,
                topLeft = Offset(bootX, footY - 4.8f * u),
                size = Size(bootW, 4.8f * u),
                cornerRadius = CornerRadius(2.0f * u, 2.0f * u)
            )
            if (useHighlights && !shaded) {
                drawLine(
                    color = appearance.bootsPalette.highlight,
                    start = Offset(bootX + 0.9f * u, footY - 3.9f * u),
                    end = Offset(bootX + bootW - 1.1f * u, footY - 3.9f * u),
                    strokeWidth = 1.0f * u,
                    cap = StrokeCap.Round
                )
            }
            if (appearance.bootsKind != BootsVisualKind.DEFAULT) {
                // Folded cuff / trim accent
                drawLine(
                    color = appearance.bootsPalette.accent,
                    start = Offset(bootX + 0.5f * u, footY - 4.4f * u),
                    end = Offset(bootX + bootW - 0.5f * u, footY - 4.4f * u),
                    strokeWidth = 1.1f * u
                )
            }
            drawRoundRect(
                color = INK_OUTLINE,
                topLeft = Offset(bootX, footY - 4.8f * u),
                size = Size(bootW, 4.8f * u),
                cornerRadius = CornerRadius(2.0f * u, 2.0f * u),
                style = Stroke(strokeW)
            )
        }

        drawLeg(lHipX, lHipX + leftStrideX, oy + leftLiftY, shaded = isSide)
        drawLeg(rHipX, rHipX + rightStrideX, oy + rightLiftY, shaded = false)
    }

    private fun DrawScope.drawTorsoAndArmor(
        ox: Float,
        oy: Float,
        u: Float,
        strokeW: Float,
        appearance: PaperDollAppearance,
        isSide: Boolean,
        flipX: Float,
        isBack: Boolean,
        useHighlights: Boolean,
        useHdBloom: Boolean,
        path: Path
    ) {
        val shoulderW = if (isSide) 5.2f * u else 7.6f * u
        val waistW = if (isSide) 4.4f * u else 5.8f * u
        val hemW = when {
            appearance.armorKind == ArmorVisualKind.ROBE -> if (isSide) 6.4f * u else 8.6f * u
            else -> if (isSide) 5.1f * u else 6.8f * u
        }
        val topY = oy - 26.8f * u
        val waistY = oy - 15.2f * u
        val hemY = if (appearance.armorKind == ArmorVisualKind.ROBE) oy - 5.2f * u else oy - 10.6f * u

        // Tapered torso silhouette (shoulders -> cinched waist -> tunic/robe hem)
        path.reset()
        path.moveTo(ox - shoulderW, topY + 1.4f * u)
        path.quadraticTo(ox, topY - 1.2f * u, ox + shoulderW, topY + 1.4f * u)
        path.lineTo(ox + waistW, waistY)
        path.lineTo(ox + hemW, hemY)
        path.quadraticTo(ox, hemY + 1.4f * u, ox - hemW, hemY)
        path.lineTo(ox - waistW, waistY)
        path.close()

        val pal = appearance.armorPalette
        if (useHighlights) {
            drawPath(
                path,
                brush = Brush.linearGradient(
                    colors = listOf(pal.highlight, pal.primary, pal.shadow),
                    start = Offset(ox - shoulderW, topY),
                    end = Offset(ox + shoulderW, hemY)
                )
            )
        } else {
            drawPath(path, color = pal.primary)
        }

        // Armor-specific sculpted detailing
        when (appearance.armorKind) {
            ArmorVisualKind.PLATE -> {
                // Sculpted breastplate ridge + shoulder pauldrons
                if (!isBack) {
                    drawLine(
                        color = pal.highlight.copy(alpha = 0.85f),
                        start = Offset(ox - shoulderW * 0.6f, topY + 3.8f * u),
                        end = Offset(ox, topY + 7.5f * u),
                        strokeWidth = 1.4f * u,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = pal.shadow,
                        start = Offset(ox, topY + 7.5f * u),
                        end = Offset(ox + shoulderW * 0.6f, topY + 3.8f * u),
                        strokeWidth = 1.2f * u
                    )
                }
                // Pauldrons
                drawOval(
                    color = pal.highlight,
                    topLeft = Offset(ox - shoulderW - 1.5f * u, topY),
                    size = Size(4.6f * u, 4.2f * u)
                )
                drawOval(
                    color = INK_OUTLINE,
                    topLeft = Offset(ox - shoulderW - 1.5f * u, topY),
                    size = Size(4.6f * u, 4.2f * u),
                    style = Stroke(strokeW * 0.85f)
                )
                if (!isSide) {
                    drawOval(
                        color = pal.primary,
                        topLeft = Offset(ox + shoulderW - 3.1f * u, topY),
                        size = Size(4.6f * u, 4.2f * u)
                    )
                    drawOval(
                        color = INK_OUTLINE,
                        topLeft = Offset(ox + shoulderW - 3.1f * u, topY),
                        size = Size(4.6f * u, 4.2f * u),
                        style = Stroke(strokeW * 0.85f)
                    )
                }
            }
            ArmorVisualKind.WYRMSCALE -> {
                // Layered emerald dragon scales + gold ridge pauldrons
                for (row in 0..2) {
                    val sy = topY + (4.2f + row * 3.1f) * u
                    drawArc(
                        color = pal.highlight.copy(alpha = 0.78f),
                        startAngle = 15f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(ox - 4.4f * u, sy - 1.5f * u),
                        size = Size(8.8f * u, 3.0f * u),
                        style = Stroke(1.1f * u)
                    )
                }
                if (useHdBloom && pal.glow != null) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(pal.glow.copy(alpha = 0.25f), Color.Transparent),
                            center = Offset(ox, topY + 7f * u),
                            radius = 9f * u
                        ),
                        radius = 9f * u,
                        center = Offset(ox, topY + 7f * u)
                    )
                }
            }
            ArmorVisualKind.ROBE -> {
                // Vertical gilded mage stole down front + hem trim
                if (!isBack) {
                    drawLine(
                        color = pal.accent,
                        start = Offset(ox, topY + 1.5f * u),
                        end = Offset(ox, hemY - 0.8f * u),
                        strokeWidth = 2.0f * u
                    )
                }
                drawLine(
                    color = pal.accent,
                    start = Offset(ox - hemW + 0.8f * u, hemY - 1.5f * u),
                    end = Offset(ox + hemW - 0.8f * u, hemY - 1.5f * u),
                    strokeWidth = 1.5f * u
                )
            }
            ArmorVisualKind.LEATHER -> {
                // Diagonal cross-strap & stitched seams
                if (!isBack) {
                    drawLine(
                        color = pal.shadow,
                        start = Offset(ox - shoulderW * 0.7f, topY + 1.5f * u),
                        end = Offset(ox + waistW * 0.7f, waistY),
                        strokeWidth = 1.8f * u
                    )
                    drawLine(
                        color = pal.accent,
                        start = Offset(ox - shoulderW * 0.65f, topY + 1.7f * u),
                        end = Offset(ox + waistW * 0.65f, waistY - 0.2f * u),
                        strokeWidth = 0.7f * u
                    )
                }
            }
            ArmorVisualKind.TUNIC -> {
                // Subtle V-collar & diagonal satchel strap
                if (!isBack && !isSide) {
                    drawLine(
                        color = BELT_LEATHER,
                        start = Offset(ox - 5.2f * u, topY + 1.8f * u),
                        end = Offset(ox + 4.8f * u, waistY),
                        strokeWidth = 1.4f * u
                    )
                }
            }
        }

        // Crisp dark outline on torso
        drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW, join = StrokeJoin.Round))

        // Waist Belt & Gold Buckle
        drawRoundRect(
            color = BELT_LEATHER,
            topLeft = Offset(ox - waistW - 0.3f * u, waistY - 1.4f * u),
            size = Size((waistW + 0.3f * u) * 2f, 2.7f * u),
            cornerRadius = CornerRadius(1.0f * u, 1.0f * u)
        )
        if (!isBack) {
            val buckleX = if (isSide) ox + flipX * 1.4f * u else ox
            drawRoundRect(
                color = BELT_BUCKLE,
                topLeft = Offset(buckleX - 1.5f * u, waistY - 1.2f * u),
                size = Size(3.0f * u, 2.3f * u),
                cornerRadius = CornerRadius(0.6f * u, 0.6f * u)
            )
            if (appearance.hasAmulet) {
                // Glowing ruby Amulet of Vitality on chest
                drawCircle(Color(0xFFE03A4B), radius = 1.5f * u, center = Offset(ox + (if (isSide) flipX * 2.2f * u else 0f), topY + 4.4f * u))
                drawCircle(Color(0xFFFFA8B0), radius = 0.6f * u, center = Offset(ox + (if (isSide) flipX * 2.2f * u else -0.3f * u), topY + 4.0f * u))
            }
        }
        drawRoundRect(
            color = INK_OUTLINE,
            topLeft = Offset(ox - waistW - 0.3f * u, waistY - 1.4f * u),
            size = Size((waistW + 0.3f * u) * 2f, 2.7f * u),
            cornerRadius = CornerRadius(1.0f * u, 1.0f * u),
            style = Stroke(strokeW * 0.85f)
        )
    }

    private fun DrawScope.drawHeadAndHelmet(
        ox: Float,
        oy: Float,
        u: Float,
        strokeW: Float,
        appearance: PaperDollAppearance,
        isSide: Boolean,
        flipX: Float,
        isBack: Boolean,
        useHighlights: Boolean,
        path: Path
    ) {
        val headCx = ox
        val headCy = oy - 32.8f * u
        val headRx = if (isSide) 5.2f * u else 5.8f * u
        val headRy = 5.9f * u

        // Neck
        drawRect(
            color = appearance.skinShadow,
            topLeft = Offset(headCx - 2.0f * u, headCy + 4.2f * u),
            size = Size(4.0f * u, 2.6f * u)
        )

        // Rounded shaded head
        if (useHighlights) {
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(appearance.skinHighlight, appearance.skinPrimary, appearance.skinShadow),
                    center = Offset(headCx - 1.8f * u, headCy - 1.8f * u),
                    radius = headRy * 1.45f
                ),
                topLeft = Offset(headCx - headRx, headCy - headRy),
                size = Size(headRx * 2f, headRy * 2f)
            )
        } else {
            drawOval(
                color = appearance.skinPrimary,
                topLeft = Offset(headCx - headRx, headCy - headRy),
                size = Size(headRx * 2f, headRy * 2f)
            )
        }
        drawOval(
            color = INK_OUTLINE,
            topLeft = Offset(headCx - headRx, headCy - headRy),
            size = Size(headRx * 2f, headRy * 2f),
            style = Stroke(strokeW)
        )

        // Expressive eyes (front & side only)
        if (!isBack) {
            val eyeY = headCy + 0.3f * u
            if (isSide) {
                val ex = headCx + flipX * 2.5f * u
                drawCircle(INK_OUTLINE, radius = 1.05f * u, center = Offset(ex, eyeY))
                if (useHighlights) drawCircle(Color.White, radius = 0.35f * u, center = Offset(ex - 0.3f * u, eyeY - 0.35f * u))
            } else {
                drawCircle(INK_OUTLINE, radius = 1.0f * u, center = Offset(headCx - 2.1f * u, eyeY))
                drawCircle(INK_OUTLINE, radius = 1.0f * u, center = Offset(headCx + 2.1f * u, eyeY))
                if (useHighlights) {
                    drawCircle(Color.White, radius = 0.32f * u, center = Offset(headCx - 2.4f * u, eyeY - 0.35f * u))
                    drawCircle(Color.White, radius = 0.32f * u, center = Offset(headCx + 1.8f * u, eyeY - 0.35f * u))
                }
            }
        }

        // Hair (drawn underneath open helmets or full if unhelmeted)
        val hp = appearance.hairPalette
        if (appearance.helmetKind == HelmetVisualKind.NONE || appearance.helmetKind == HelmetVisualKind.LEATHER_CAP) {
            path.reset()
            if (isBack) {
                path.moveTo(headCx - headRx - 0.4f * u, headCy - 1.0f * u)
                path.quadraticTo(headCx, headCy - headRy - 2.2f * u, headCx + headRx + 0.4f * u, headCy - 1.0f * u)
                path.lineTo(headCx + headRx - 0.4f * u, headCy + 4.8f * u)
                path.quadraticTo(headCx, headCy + 6.0f * u, headCx - headRx + 0.4f * u, headCy + 4.8f * u)
                path.close()
            } else {
                path.moveTo(headCx - headRx - 0.4f * u, headCy - 0.6f * u)
                path.quadraticTo(headCx - headRx * 0.5f, headCy - headRy - 2.0f * u, headCx, headCy - headRy - 1.4f * u)
                path.quadraticTo(headCx + headRx * 0.6f, headCy - headRy - 2.0f * u, headCx + headRx + 0.4f * u, headCy - 0.6f * u)
                path.lineTo(headCx + headRx * 0.85f, headCy - 2.2f * u)
                path.quadraticTo(headCx, headCy - 3.6f * u, headCx - headRx * 0.85f, headCy - 2.2f * u)
                path.close()
            }
            drawPath(path, color = hp.primary)
            if (useHighlights) {
                drawArc(
                    color = hp.highlight,
                    startAngle = 205f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(headCx - headRx * 0.78f, headCy - headRy - 0.6f * u),
                    size = Size(headRx * 1.56f, headRy * 1.1f),
                    style = Stroke(1.3f * u, cap = StrokeCap.Round)
                )
            }
            drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW * 0.9f, join = StrokeJoin.Round))
        }

        // Equipped Helmet / Hood / Wizard Hat
        val helmPal = appearance.helmetPalette
        when (appearance.helmetKind) {
            HelmetVisualKind.NONE -> Unit
            HelmetVisualKind.LEATHER_CAP -> {
                path.reset()
                path.moveTo(headCx - headRx - 0.5f * u, headCy - 1.4f * u)
                path.quadraticTo(headCx, headCy - headRy - 2.8f * u, headCx + headRx + 0.5f * u, headCy - 1.4f * u)
                path.quadraticTo(headCx, headCy - 2.5f * u, headCx - headRx - 0.5f * u, headCy - 1.4f * u)
                path.close()
                drawPath(path, color = helmPal.primary)
                drawLine(helmPal.accent, Offset(headCx - headRx, headCy - 1.6f * u), Offset(headCx + headRx, headCy - 1.6f * u), 1.1f * u)
                drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW, join = StrokeJoin.Round))
            }
            HelmetVisualKind.VIKING_HELM -> {
                // Curved iron horns
                val hornCol = Color(0xFFE7DEC7)
                path.reset()
                path.moveTo(headCx - headRx + 0.4f * u, headCy - 2.6f * u)
                path.quadraticTo(headCx - headRx - 4.2f * u, headCy - 4.5f * u, headCx - headRx - 2.6f * u, headCy - 9.4f * u)
                path.quadraticTo(headCx - headRx - 1.2f * u, headCy - 5.8f * u, headCx - headRx + 1.6f * u, headCy - 4.6f * u)
                path.close()
                drawPath(path, color = hornCol)
                drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW * 0.85f, join = StrokeJoin.Round))

                path.reset()
                path.moveTo(headCx + headRx - 0.4f * u, headCy - 2.6f * u)
                path.quadraticTo(headCx + headRx + 4.2f * u, headCy - 4.5f * u, headCx + headRx + 2.6f * u, headCy - 9.4f * u)
                path.quadraticTo(headCx + headRx + 1.2f * u, headCy - 5.8f * u, headCx + headRx - 1.6f * u, headCy - 4.6f * u)
                path.close()
                drawPath(path, color = hornCol)
                drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW * 0.85f, join = StrokeJoin.Round))

                // Dome + brow band + nasal guard
                path.reset()
                path.moveTo(headCx - headRx - 0.7f * u, headCy - 1.0f * u)
                path.quadraticTo(headCx, headCy - headRy - 3.6f * u, headCx + headRx + 0.7f * u, headCy - 1.0f * u)
                path.lineTo(headCx + headRx, headCy - 0.2f * u)
                path.quadraticTo(headCx, headCy - 1.8f * u, headCx - headRx, headCy - 0.2f * u)
                path.close()
                drawPath(
                    path,
                    brush = Brush.linearGradient(
                        listOf(helmPal.highlight, helmPal.primary, helmPal.shadow),
                        Offset(headCx - headRx, headCy - headRy),
                        Offset(headCx + headRx, headCy)
                    )
                )
                if (!isBack && !isSide) {
                    drawRect(helmPal.primary, Offset(headCx - 0.8f * u, headCy - 1.5f * u), Size(1.6f * u, 3.6f * u))
                }
                drawLine(helmPal.accent, Offset(headCx - headRx, headCy - 1.2f * u), Offset(headCx + headRx, headCy - 1.2f * u), 1.3f * u)
                drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW, join = StrokeJoin.Round))
            }
            HelmetVisualKind.MAGE_HOOD -> {
                path.reset()
                path.moveTo(headCx - headRx - 1.4f * u, headCy + 3.2f * u)
                path.quadraticTo(headCx - headRx - 1.8f * u, headCy - headRy - 2.6f * u, headCx, headCy - headRy - 3.8f * u)
                path.quadraticTo(headCx + headRx + 1.8f * u, headCy - headRy - 2.6f * u, headCx + headRx + 1.4f * u, headCy + 3.2f * u)
                if (isBack) {
                    path.quadraticTo(headCx, headCy + 5.6f * u, headCx - headRx - 1.4f * u, headCy + 3.2f * u)
                } else {
                    path.lineTo(headCx + headRx - 0.6f * u, headCy - 0.8f * u)
                    path.quadraticTo(headCx, headCy - 3.8f * u, headCx - headRx + 0.6f * u, headCy - 0.8f * u)
                }
                path.close()
                drawPath(path, color = helmPal.primary)
                drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW, join = StrokeJoin.Round))
                drawArc(
                    color = helmPal.accent,
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(headCx - headRx, headCy - headRy - 1.5f * u),
                    size = Size(headRx * 2f, headRy * 1.4f),
                    style = Stroke(1.1f * u)
                )
            }
            HelmetVisualKind.WIZARD_HAT -> {
                // Wide brim + pointed conical crown
                drawOval(
                    color = helmPal.shadow,
                    topLeft = Offset(headCx - headRx - 3.6f * u, headCy - 4.4f * u),
                    size = Size((headRx + 3.6f * u) * 2f, 4.4f * u)
                )
                path.reset()
                path.moveTo(headCx - headRx - 0.4f * u, headCy - 2.4f * u)
                path.lineTo(headCx + 1.6f * u, headCy - headRy - 9.2f * u)
                path.lineTo(headCx + headRx + 0.4f * u, headCy - 2.4f * u)
                path.close()
                drawPath(path, color = helmPal.primary)
                drawLine(helmPal.accent, Offset(headCx - headRx, headCy - 2.8f * u), Offset(headCx + headRx, headCy - 2.8f * u), 1.4f * u)
                drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW, join = StrokeJoin.Round))
            }
        }
    }

    private fun DrawScope.drawArm(
        shoulderX: Float,
        shoulderY: Float,
        u: Float,
        strokeW: Float,
        palette: MaterialPalette,
        skinColor: Color,
        swingDeg: Float,
        sideSign: Float,
        useHighlights: Boolean,
        path: Path
    ) {
        withTransform({
            rotate(degrees = swingDeg, pivot = Offset(shoulderX, shoulderY))
        }) {
            val armW = 3.2f * u
            val armH = 9.2f * u
            // Tapered sleeve/arm
            path.reset()
            path.moveTo(shoulderX - armW * 0.5f, shoulderY)
            path.lineTo(shoulderX + armW * 0.5f, shoulderY)
            path.lineTo(shoulderX + armW * 0.42f + sideSign * 0.6f * u, shoulderY + armH)
            path.lineTo(shoulderX - armW * 0.42f + sideSign * 0.6f * u, shoulderY + armH)
            path.close()
            drawPath(path, color = if (useHighlights) palette.primary else palette.shadow)
            drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW * 0.9f, join = StrokeJoin.Round))
            // Hand / Gauntlet
            val handX = shoulderX + sideSign * 0.6f * u
            val handY = shoulderY + armH + 0.8f * u
            drawCircle(color = skinColor, radius = 1.9f * u, center = Offset(handX, handY))
            drawCircle(color = INK_OUTLINE, radius = 1.9f * u, center = Offset(handX, handY), style = Stroke(strokeW * 0.85f))
        }
    }

    private fun DrawScope.drawHeldWeapon(
        ox: Float,
        oy: Float,
        u: Float,
        strokeW: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        stride: Float,
        atkEase: Float,
        attackProgress: Float,
        nowMs: Long,
        quality: GraphicsQuality,
        path: Path,
        behindTorso: Boolean
    ) {
        if (appearance.weaponKind == WeaponVisualKind.NONE) return
        val isSide = facing.quadrant == ScreenFacing.LEFT || facing.quadrant == ScreenFacing.RIGHT
        val flipX = if (facing.quadrant == ScreenFacing.LEFT) -1f else 1f
        val pal = appearance.weaponPalette
        val useHdGlow = quality == GraphicsQuality.HD || quality == GraphicsQuality.HIGH

        val handX = when {
            behindTorso -> ox + 8.0f * u
            isSide -> ox + flipX * (3.6f * u + atkEase * 4.5f * u)
            else -> ox - 8.2f * u - atkEase * 2.0f * u
        }
        val handY = oy - 16.5f * u - atkEase * 2.5f * u + stride * 1.1f * u

        // Swing / thrust / draw angle in degrees
        val baseAngle = when {
            isSide -> if (flipX > 0) -28f else 28f
            behindTorso -> 22f
            else -> -36f
        }
        val swingDelta = when (appearance.weaponKind) {
            WeaponVisualKind.BOW, WeaponVisualKind.ARBALEST -> -atkEase * 12f
            WeaponVisualKind.WAND, WeaponVisualKind.STAFF -> -atkEase * 32f
            else -> (if (flipX > 0) 1f else -1f) * (atkEase * 108f - (if (attackProgress > 0f) 28f else 0f))
        }

        withTransform({
            translate(left = handX, top = handY)
            rotate(degrees = baseAngle + swingDelta, pivot = Offset.Zero)
            if (isSide && flipX < 0) scale(scaleX = -1f, scaleY = 1f, pivot = Offset.Zero)
        }) {
            when (appearance.weaponKind) {
                WeaponVisualKind.DAGGER,
                WeaponVisualKind.SWORD,
                WeaponVisualKind.FALCHION,
                WeaponVisualKind.RAPIER,
                WeaponVisualKind.CLAYMORE -> {
                    val bladeLen = when (appearance.weaponKind) {
                        WeaponVisualKind.DAGGER -> 10.5f * u
                        WeaponVisualKind.CLAYMORE -> 22.0f * u
                        WeaponVisualKind.RAPIER -> 17.5f * u
                        else -> 15.8f * u
                    }
                    val bladeHalfW = when (appearance.weaponKind) {
                        WeaponVisualKind.RAPIER -> 1.1f * u
                        WeaponVisualKind.CLAYMORE -> 2.4f * u
                        WeaponVisualKind.FALCHION -> 2.3f * u
                        else -> 1.8f * u
                    }
                    // Hilt & Pommel
                    drawLine(MaterialPalettes.OAK_WOOD.primary, Offset(0f, 3.6f * u), Offset(0f, -0.5f * u), 2.0f * u, cap = StrokeCap.Round)
                    drawCircle(pal.accent, radius = 1.4f * u, center = Offset(0f, 4.0f * u))
                    // Blade silhouette with fuller & bevel
                    path.reset()
                    path.moveTo(-bladeHalfW, -1.0f * u)
                    if (appearance.weaponKind == WeaponVisualKind.FALCHION) {
                        path.lineTo(-bladeHalfW * 0.8f, -bladeLen * 0.72f)
                        path.quadraticTo(bladeHalfW * 0.5f, -bladeLen * 1.05f, bladeHalfW * 1.4f, -bladeLen * 0.86f)
                        path.lineTo(bladeHalfW, -1.0f * u)
                    } else {
                        path.lineTo(-bladeHalfW * 0.78f, -bladeLen * 0.80f)
                        path.lineTo(0f, -bladeLen)
                        path.lineTo(bladeHalfW * 0.78f, -bladeLen * 0.80f)
                        path.lineTo(bladeHalfW, -1.0f * u)
                    }
                    path.close()
                    drawPath(
                        path,
                        brush = Brush.linearGradient(
                            listOf(pal.highlight, pal.primary, pal.shadow),
                            Offset(-bladeHalfW, -bladeLen),
                            Offset(bladeHalfW, 0f)
                        )
                    )
                    // Metallic fuller glint
                    drawLine(pal.highlight, Offset(-bladeHalfW * 0.25f, -2.5f * u), Offset(-bladeHalfW * 0.1f, -bladeLen * 0.75f), 0.8f * u)
                    drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW * 0.85f, join = StrokeJoin.Round))
                    // Crossguard
                    val guardW = if (appearance.weaponKind == WeaponVisualKind.CLAYMORE) 4.6f * u else 3.6f * u
                    drawLine(pal.accent, Offset(-guardW, -0.8f * u), Offset(guardW, -0.8f * u), 2.1f * u, cap = StrokeCap.Round)
                    drawLine(INK_OUTLINE, Offset(-guardW, -0.8f * u), Offset(guardW, -0.8f * u), 0.7f * u)
                }

                WeaponVisualKind.AXE, WeaponVisualKind.GLAIVE -> {
                    val shaftLen = if (appearance.weaponKind == WeaponVisualKind.GLAIVE) 23.0f * u else 17.5f * u
                    // Hardwood/iron shaft
                    drawLine(MaterialPalettes.OAK_WOOD.primary, Offset(0f, 5.2f * u), Offset(0f, -shaftLen), 2.1f * u, cap = StrokeCap.Round)
                    drawLine(INK_OUTLINE, Offset(0f, 5.2f * u), Offset(0f, -shaftLen), strokeW * 0.75f)
                    // Curved bearded steel axe bit
                    path.reset()
                    path.moveTo(0.6f * u, -shaftLen + 1.5f * u)
                    path.quadraticTo(6.8f * u, -shaftLen - 1.5f * u, 7.2f * u, -shaftLen + 5.2f * u)
                    path.quadraticTo(5.6f * u, -shaftLen + 8.5f * u, 0.6f * u, -shaftLen + 6.0f * u)
                    path.close()
                    drawPath(
                        path,
                        brush = Brush.linearGradient(
                            listOf(pal.highlight, pal.primary, pal.shadow),
                            Offset(0.5f * u, -shaftLen),
                            Offset(7.2f * u, -shaftLen + 8f * u)
                        )
                    )
                    drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW * 0.85f, join = StrokeJoin.Round))
                }

                WeaponVisualKind.CLUB -> {
                    path.reset()
                    path.moveTo(-1.1f * u, 3.0f * u)
                    path.lineTo(-2.8f * u, -11.5f * u)
                    path.quadraticTo(0f, -14.5f * u, 2.8f * u, -11.5f * u)
                    path.lineTo(1.1f * u, 3.0f * u)
                    path.close()
                    drawPath(path, color = MaterialPalettes.OAK_WOOD.primary)
                    // Iron studs
                    drawCircle(MaterialPalettes.IRON.highlight, radius = 0.9f * u, center = Offset(-1.0f * u, -10.5f * u))
                    drawCircle(MaterialPalettes.IRON.highlight, radius = 0.9f * u, center = Offset(1.1f * u, -8.8f * u))
                    drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW * 0.85f, join = StrokeJoin.Round))
                }

                WeaponVisualKind.BOW, WeaponVisualKind.ARBALEST -> {
                    val pull = atkEase * 3.4f * u
                    if (appearance.weaponKind == WeaponVisualKind.BOW) {
                        // Curved wooden/moonforge bow limbs
                        path.reset()
                        path.moveTo(0f, -10.5f * u)
                        path.quadraticTo(6.8f * u, -5.0f * u, 5.2f * u, 0f)
                        path.quadraticTo(6.8f * u, 5.0f * u, 0f, 10.5f * u)
                        drawPath(path, color = INK_OUTLINE, style = Stroke(3.2f * u, cap = StrokeCap.Round))
                        drawPath(path, color = pal.primary, style = Stroke(2.0f * u, cap = StrokeCap.Round))
                        // Bowstring (draws back during attack!)
                        drawLine(Color(0xFFE7DFC8), Offset(0f, -10.2f * u), Offset(-1.2f * u - pull, 0f), 1.0f * u)
                        drawLine(Color(0xFFE7DFC8), Offset(-1.2f * u - pull, 0f), Offset(0f, 10.2f * u), 1.0f * u)
                        // Nocked arrow
                        drawLine(Color(0xFFCFA466), Offset(-1.0f * u - pull, 0f), Offset(9.5f * u - pull * 0.5f, 0f), 1.3f * u)
                        drawLine(pal.highlight, Offset(8.5f * u - pull * 0.5f, -1.2f * u), Offset(10.8f * u - pull * 0.5f, 0f), 1.3f * u)
                        drawLine(pal.highlight, Offset(8.5f * u - pull * 0.5f, 1.2f * u), Offset(10.8f * u - pull * 0.5f, 0f), 1.3f * u)
                    } else {
                        // Arbalest stock + transverse prod + quarrel
                        drawLine(MaterialPalettes.OAK_WOOD.primary, Offset(-4.0f * u, 0f), Offset(9.2f * u, 0f), 2.4f * u, cap = StrokeCap.Round)
                        drawLine(pal.primary, Offset(6.2f * u, -7.2f * u), Offset(6.2f * u, 7.2f * u), 2.1f * u, cap = StrokeCap.Round)
                        drawLine(Color(0xFFE7DFC8), Offset(6.2f * u, -7.0f * u), Offset(0.5f * u - pull, 0f), 1.0f * u)
                        drawLine(Color(0xFFE7DFC8), Offset(0.5f * u - pull, 0f), Offset(6.2f * u, 7.0f * u), 1.0f * u)
                        drawLine(pal.highlight, Offset(1.0f * u - pull, 0f), Offset(11.0f * u, 0f), 1.4f * u)
                    }
                }

                WeaponVisualKind.JAVELIN -> {
                    drawLine(MaterialPalettes.OAK_WOOD.primary, Offset(0f, 7.5f * u), Offset(0f, -15.0f * u), 1.8f * u, cap = StrokeCap.Round)
                    path.reset()
                    path.moveTo(-1.8f * u, -14.2f * u)
                    path.lineTo(0f, -19.8f * u)
                    path.lineTo(1.8f * u, -14.2f * u)
                    path.close()
                    drawPath(path, color = pal.highlight)
                    drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW * 0.8f, join = StrokeJoin.Round))
                }

                WeaponVisualKind.WAND, WeaponVisualKind.STAFF -> {
                    val isStaff = appearance.weaponKind == WeaponVisualKind.STAFF
                    val shaftTop = if (isStaff) -20.5f * u else -13.0f * u
                    val shaftBot = if (isStaff) 9.0f * u else 3.2f * u
                    val glowCol = pal.glow ?: Color(0xFF6CE8FF)
                    val pulse = 0.78f + 0.22f * sin(nowMs * 0.008f)

                    drawLine(INK_OUTLINE, Offset(0f, shaftBot), Offset(0f, shaftTop), 3.1f * u, cap = StrokeCap.Round)
                    drawLine(pal.primary, Offset(0f, shaftBot), Offset(0f, shaftTop), 1.9f * u, cap = StrokeCap.Round)

                    // Radiant orb / elemental gem tip
                    if (useHdGlow) {
                        val auraR = (if (isStaff) 6.5f else 4.8f) * u * (pulse + atkEase * 0.45f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(glowCol.copy(alpha = 0.65f), glowCol.copy(alpha = 0.16f), Color.Transparent),
                                center = Offset(0f, shaftTop - 1.2f * u),
                                radius = auraR
                            ),
                            radius = auraR,
                            center = Offset(0f, shaftTop - 1.2f * u)
                        )
                    }
                    val orbR = (if (isStaff) 2.6f else 2.0f) * u
                    drawCircle(glowCol, radius = orbR, center = Offset(0f, shaftTop - 1.2f * u))
                    drawCircle(Color.White.copy(alpha = 0.85f), radius = orbR * 0.48f, center = Offset(-0.5f * u, shaftTop - 1.7f * u))
                    drawCircle(INK_OUTLINE, radius = orbR, center = Offset(0f, shaftTop - 1.2f * u), style = Stroke(strokeW * 0.75f))
                }

                WeaponVisualKind.NONE -> Unit
            }
        }
    }

    private fun DrawScope.drawOffhand(
        ox: Float,
        oy: Float,
        u: Float,
        strokeW: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        stride: Float,
        nowMs: Long,
        quality: GraphicsQuality,
        path: Path,
        behindTorso: Boolean
    ) {
        if (appearance.offhandKind == OffhandVisualKind.NONE) return
        val isSide = facing.quadrant == ScreenFacing.LEFT || facing.quadrant == ScreenFacing.RIGHT
        val flipX = if (facing.quadrant == ScreenFacing.LEFT) -1f else 1f
        val pal = appearance.offhandPalette
        val useHdGlow = quality == GraphicsQuality.HD || quality == GraphicsQuality.HIGH

        val sx = when {
            behindTorso && isSide -> ox + flipX * 5.6f * u
            behindTorso -> ox - 7.8f * u
            else -> ox + 8.0f * u
        }
        val sy = oy - 16.2f * u - stride * 0.9f * u

        when (appearance.offhandKind) {
            OffhandVisualKind.ROUND_SHIELD -> {
                val r = 5.4f * u
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(pal.highlight, pal.primary, pal.shadow),
                        center = Offset(sx - 1.4f * u, sy - 1.4f * u),
                        radius = r * 1.4f
                    ),
                    radius = r,
                    center = Offset(sx, sy)
                )
                drawCircle(MaterialPalettes.IRON.primary, radius = r * 0.96f, center = Offset(sx, sy), style = Stroke(1.3f * u))
                drawCircle(MaterialPalettes.IRON.highlight, radius = 1.6f * u, center = Offset(sx, sy))
                drawCircle(INK_OUTLINE, radius = r, center = Offset(sx, sy), style = Stroke(strokeW))
            }
            OffhandVisualKind.KITE_SHIELD, OffhandVisualKind.DRAGON_SHIELD -> {
                val w = 5.3f * u
                val h = 7.4f * u
                path.reset()
                path.moveTo(sx - w, sy - h * 0.65f)
                path.quadraticTo(sx, sy - h * 0.85f, sx + w, sy - h * 0.65f)
                path.lineTo(sx + w * 0.88f, sy + h * 0.15f)
                path.quadraticTo(sx + w * 0.45f, sy + h * 0.72f, sx, sy + h * 0.95f)
                path.quadraticTo(sx - w * 0.45f, sy + h * 0.72f, sx - w * 0.88f, sy + h * 0.15f)
                path.close()
                drawPath(
                    path,
                    brush = Brush.linearGradient(
                        listOf(pal.highlight, pal.primary, pal.shadow),
                        Offset(sx - w, sy - h),
                        Offset(sx + w, sy + h)
                    )
                )
                // Metallic rim + central emblem crest
                drawPath(path, color = pal.accent, style = Stroke(1.35f * u, join = StrokeJoin.Round))
                if (appearance.offhandKind == OffhandVisualKind.DRAGON_SHIELD) {
                    drawCircle(Color(0xFFEEC55D), radius = 1.8f * u, center = Offset(sx, sy - 0.5f * u))
                    drawCircle(Color(0xFFD94A27), radius = 0.9f * u, center = Offset(sx, sy - 0.5f * u))
                } else {
                    drawLine(pal.accent, Offset(sx, sy - h * 0.45f), Offset(sx, sy + h * 0.55f), 1.2f * u)
                    drawLine(pal.accent, Offset(sx - w * 0.55f, sy - 0.5f * u), Offset(sx + w * 0.55f, sy - 0.5f * u), 1.2f * u)
                }
                drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW, join = StrokeJoin.Round))
            }
            OffhandVisualKind.ARCANE_ORB -> {
                val hover = sin(nowMs * 0.006f) * 1.1f * u
                val orbCenter = Offset(sx, sy - 1.2f * u + hover)
                val glowCol = pal.glow ?: Color(0xFF6CE8FF)
                if (useHdGlow) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(glowCol.copy(alpha = 0.55f), Color.Transparent),
                            center = orbCenter,
                            radius = 5.4f * u
                        ),
                        radius = 5.4f * u,
                        center = orbCenter
                    )
                }
                drawCircle(glowCol, radius = 2.5f * u, center = orbCenter)
                drawCircle(Color.White.copy(alpha = 0.85f), radius = 1.0f * u, center = Offset(orbCenter.x - 0.7f * u, orbCenter.y - 0.7f * u))
                drawCircle(INK_OUTLINE, radius = 2.5f * u, center = orbCenter, style = Stroke(strokeW * 0.75f))
            }
            OffhandVisualKind.NONE -> Unit
        }
    }

    private fun DrawScope.drawCombatAttackTrail(
        ox: Float,
        oy: Float,
        u: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        attackProgress: Float,
        quality: GraphicsQuality
    ) {
        val alpha = sin(attackProgress * PI.toFloat()).coerceIn(0f, 1f)
        val isHd = quality == GraphicsQuality.HD || quality == GraphicsQuality.HIGH
        val arcCenter = Offset(ox + facing.screenDirX * 7.5f * u, oy - 18.0f * u + facing.screenDirY * 3.5f * u)

        when (appearance.weaponKind) {
            WeaponVisualKind.WAND, WeaponVisualKind.STAFF -> {
                val glow = appearance.weaponPalette.glow ?: Color(0xFF6CE8FF)
                val ringR = (5.5f + attackProgress * 8.5f) * u
                drawCircle(glow.copy(alpha = alpha * 0.72f), radius = ringR, center = arcCenter, style = Stroke(width = (if (isHd) 2.6f else 1.8f) * u))
                if (isHd) {
                    drawCircle(
                        brush = Brush.radialGradient(listOf(glow.copy(alpha = alpha * 0.42f), Color.Transparent), arcCenter, ringR * 1.4f),
                        radius = ringR * 1.4f,
                        center = arcCenter
                    )
                }
            }
            WeaponVisualKind.BOW, WeaponVisualKind.ARBALEST -> {
                val glintCol = appearance.weaponPalette.glow ?: Color(0xFFFFE49E)
                drawLine(
                    color = glintCol.copy(alpha = alpha * 0.75f),
                    start = Offset(ox + facing.screenDirX * 4f * u, oy - 17f * u),
                    end = Offset(ox + facing.screenDirX * 16f * u, oy - 17f * u + facing.screenDirY * 7f * u),
                    strokeWidth = (if (isHd) 2.2f else 1.4f) * u,
                    cap = StrokeCap.Round
                )
            }
            else -> {
                // Sweeping crisp weapon slash arc oriented toward facing vector
                val baseAngle = (atan2(facing.screenDirY.toDouble(), facing.screenDirX.toDouble()) * 180.0 / PI).toFloat()
                val slashColor = appearance.weaponPalette.glow ?: appearance.weaponPalette.highlight
                val arcR = 13.5f * u
                drawArc(
                    color = slashColor.copy(alpha = alpha * (if (isHd) 0.85f else 0.62f)),
                    startAngle = baseAngle - 58f + attackProgress * 22f,
                    sweepAngle = 96f,
                    useCenter = false,
                    topLeft = Offset(ox - arcR, oy - 19f * u - arcR * 0.75f),
                    size = Size(arcR * 2f, arcR * 1.5f),
                    style = Stroke(width = (if (isHd) 3.4f else 2.2f) * u, cap = StrokeCap.Round)
                )
                if (isHd) {
                    drawArc(
                        color = Color.White.copy(alpha = alpha * 0.7f),
                        startAngle = baseAngle - 46f + attackProgress * 28f,
                        sweepAngle = 68f,
                        useCenter = false,
                        topLeft = Offset(ox - arcR * 0.92f, oy - 19f * u - arcR * 0.68f),
                        size = Size(arcR * 1.84f, arcR * 1.36f),
                        style = Stroke(width = 1.4f * u, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }

    // --- Thematic Articulated NPC Renderers ---

    private fun DrawScope.drawBrotherAlden(
        ox: Float,
        oy: Float,
        u: Float,
        strokeW: Float,
        facing: ResolvedScreenFacing,
        useHighlights: Boolean,
        useHdBloom: Boolean,
        nowMs: Long,
        path: Path
    ) {
        val crimsonPal = MaterialPalette(
            primary = Color(0xFF9C1F2D),
            highlight = Color(0xFFD43B4A),
            shadow = Color(0xFF5C1019),
            accent = Color(0xFFF2C85B),
            glow = Color(0xFFFFDE7A)
        )
        val priestAppearance = PaperDollAppearance(
            vocation = Vocation.MAGE,
            tunicPalette = crimsonPal,
            hairPalette = MaterialPalette(Color(0xFFC5CCD6), Color(0xFFEEF2F7), Color(0xFF818A96)),
            armorKind = ArmorVisualKind.ROBE,
            armorPalette = crimsonPal,
            helmetKind = HelmetVisualKind.NONE,
            bootsPalette = MaterialPalettes.DEFAULT_BOOTS
        )
        drawLegsAndBoots(ox, oy, u, strokeW, priestAppearance, false, 1f, 0f, useHighlights, path)
        drawTorsoAndArmor(ox, oy, u, strokeW, priestAppearance, false, 1f, facing.quadrant == ScreenFacing.BACK, useHighlights, useHdBloom, path)

        // Cream clerical collar & cross-stole
        if (facing.quadrant != ScreenFacing.BACK) {
            drawOval(Color(0xFFF5ECD6), Offset(ox - 5.2f * u, oy - 26.8f * u), Size(10.4f * u, 3.6f * u))
            drawLine(Color(0xFFF2C85B), Offset(ox, oy - 24f * u), Offset(ox, oy - 17.5f * u), 1.6f * u)
            drawLine(Color(0xFFF2C85B), Offset(ox - 2.3f * u, oy - 21.5f * u), Offset(ox + 2.3f * u, oy - 21.5f * u), 1.6f * u)
        }

        drawHeadAndHelmet(ox, oy, u, strokeW, priestAppearance, false, 1f, facing.quadrant == ScreenFacing.BACK, useHighlights, path)
        // Silver patriarch beard
        if (facing.quadrant != ScreenFacing.BACK) {
            path.reset()
            path.moveTo(ox - 4.0f * u, oy - 29.8f * u)
            path.quadraticTo(ox, oy - 24.4f * u, ox + 4.0f * u, oy - 29.8f * u)
            path.quadraticTo(ox, oy - 28.4f * u, ox - 4.0f * u, oy - 29.8f * u)
            path.close()
            drawPath(path, color = Color(0xFFDDE3EC))
            drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW * 0.8f, join = StrokeJoin.Round))
        }

        drawArm(ox - 7.0f * u, oy - 24.5f * u, u, strokeW, crimsonPal, priestAppearance.skinPrimary, -10f, -1f, useHighlights, path)
        drawArm(ox + 7.0f * u, oy - 24.5f * u, u, strokeW, crimsonPal, priestAppearance.skinPrimary, 12f, 1f, useHighlights, path)

        // Golden pastoral sanctuary staff (left hand)
        val staffX = ox - 8.6f * u
        val pulse = 0.82f + 0.18f * sin(nowMs * 0.005f)
        drawLine(INK_OUTLINE, Offset(staffX, oy - 1.2f * u), Offset(staffX, oy - 36.5f * u), 3.2f * u, cap = StrokeCap.Round)
        drawLine(Color(0xFFE5BA52), Offset(staffX, oy - 1.2f * u), Offset(staffX, oy - 36.5f * u), 1.9f * u, cap = StrokeCap.Round)
        if (useHdBloom) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFE58A).copy(alpha = 0.55f * pulse), Color.Transparent), Offset(staffX, oy - 38f * u), 7f * u),
                radius = 7f * u,
                center = Offset(staffX, oy - 38f * u)
            )
        }
        drawCircle(Color(0xFFFFEB99), radius = 2.5f * u, center = Offset(staffX, oy - 38f * u))
        drawCircle(INK_OUTLINE, radius = 2.5f * u, center = Offset(staffX, oy - 38f * u), style = Stroke(strokeW * 0.8f))

        // Gilded scripture tome (right hand)
        drawRoundRect(Color(0xFF6B1E28), Offset(ox + 5.6f * u, oy - 19.5f * u), Size(5.6f * u, 6.8f * u), CornerRadius(1.0f * u, 1.0f * u))
        drawLine(Color(0xFFF2C85B), Offset(ox + 8.4f * u, oy - 18.0f * u), Offset(ox + 8.4f * u, oy - 14.2f * u), 1.1f * u)
        drawLine(Color(0xFFF2C85B), Offset(ox + 7.1f * u, oy - 16.5f * u), Offset(ox + 9.7f * u, oy - 16.5f * u), 1.1f * u)
        drawRoundRect(INK_OUTLINE, Offset(ox + 5.6f * u, oy - 19.5f * u), Size(5.6f * u, 6.8f * u), CornerRadius(1.0f * u, 1.0f * u), style = Stroke(strokeW * 0.85f))
    }

    private fun DrawScope.drawTheOracle(
        ox: Float,
        oy: Float,
        u: Float,
        strokeW: Float,
        useHighlights: Boolean,
        useHdBloom: Boolean,
        nowMs: Long,
        path: Path
    ) {
        val pulse = 0.75f + 0.25f * sin(nowMs * 0.0045f)
        val robePal = MaterialPalette(
            primary = Color(0xFFECE6D8),
            highlight = Color(0xFFFFFFFF),
            shadow = Color(0xFFB5AC9B),
            accent = Color(0xFFF5CA5B),
            glow = Color(0xFFFFE58F)
        )

        // Radiant celestial halo glow behind Oracle
        if (useHdBloom) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFE694).copy(alpha = 0.42f * pulse), Color(0xFF8BE8FF).copy(alpha = 0.14f * pulse), Color.Transparent),
                    center = Offset(ox, oy - 24f * u),
                    radius = 22f * u
                ),
                radius = 22f * u,
                center = Offset(ox, oy - 24f * u)
            )
        }

        // Flowing luminous white-and-gold robe silhouette
        path.reset()
        path.moveTo(ox - 7.5f * u, oy - 26.5f * u)
        path.quadraticTo(ox, oy - 28.5f * u, ox + 7.5f * u, oy - 26.5f * u)
        path.lineTo(ox + 11.0f * u, oy - 3.5f * u)
        path.quadraticTo(ox, oy - 1.2f * u, ox - 11.0f * u, oy - 3.5f * u)
        path.close()
        drawPath(
            path,
            brush = Brush.linearGradient(
                listOf(robePal.highlight, robePal.primary, robePal.shadow),
                Offset(ox - 9f * u, oy - 28f * u),
                Offset(ox + 9f * u, oy - 3f * u)
            )
        )
        // Golden sacred embroidery
        drawLine(robePal.accent, Offset(ox, oy - 26.0f * u), Offset(ox, oy - 3.0f * u), 2.2f * u)
        drawLine(robePal.accent, Offset(ox - 9.8f * u, oy - 5.2f * u), Offset(ox + 9.8f * u, oy - 5.2f * u), 1.6f * u)
        drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW, join = StrokeJoin.Round))

        // Outstretched blessing sleeves
        drawArm(ox - 7.5f * u, oy - 24.5f * u, u, strokeW, robePal, Color(0xFFF5DFC8), 24f, -1f, useHighlights, path)
        drawArm(ox + 7.5f * u, oy - 24.5f * u, u, strokeW, robePal, Color(0xFFF5DFC8), -24f, 1f, useHighlights, path)

        // Hooded head + luminous golden eyes + floating golden halo
        val headCy = oy - 33.4f * u
        drawOval(
            brush = Brush.radialGradient(listOf(Color.White, robePal.primary, robePal.shadow), Offset(ox - 2f * u, headCy - 2f * u), 9f * u),
            topLeft = Offset(ox - 6.6f * u, headCy - 6.6f * u),
            size = Size(13.2f * u, 13.0f * u)
        )
        drawOval(INK_OUTLINE, Offset(ox - 6.6f * u, headCy - 6.6f * u), Size(13.2f * u, 13.0f * u), style = Stroke(strokeW))
        // Face opening inside hood
        drawOval(Color(0xFF2B2438), Offset(ox - 4.2f * u, headCy - 3.6f * u), Size(8.4f * u, 8.2f * u))
        drawCircle(Color(0xFFFFEB8A), radius = 1.15f * u, center = Offset(ox - 1.8f * u, headCy + 0.2f * u))
        drawCircle(Color(0xFFFFEB8A), radius = 1.15f * u, center = Offset(ox + 1.8f * u, headCy + 0.2f * u))

        // Floating golden halo ring above head
        drawOval(
            color = Color(0xFFFFDC6A).copy(alpha = 0.92f),
            topLeft = Offset(ox - 7.4f * u, headCy - 10.6f * u),
            size = Size(14.8f * u, 3.6f * u),
            style = Stroke(width = 1.9f * u)
        )
    }

    private fun DrawScope.drawAlaric(
        ox: Float,
        oy: Float,
        u: Float,
        strokeW: Float,
        facing: ResolvedScreenFacing,
        useHighlights: Boolean,
        useHdBloom: Boolean,
        nowMs: Long,
        path: Path
    ) {
        val merchantTunic = MaterialPalette(
            primary = Color(0xFF396B59),
            highlight = Color(0xFF56947D),
            shadow = Color(0xFF214035),
            accent = Color(0xFFE0B454)
        )
        val alaricAppearance = PaperDollAppearance(
            vocation = Vocation.ADVENTURER,
            tunicPalette = merchantTunic,
            hairPalette = MaterialPalette(Color(0xFF6B4226), Color(0xFF945E38), Color(0xFF422714)),
            armorKind = ArmorVisualKind.TUNIC,
            armorPalette = merchantTunic,
            helmetKind = HelmetVisualKind.NONE,
            bootsKind = BootsVisualKind.TRAVELER,
            bootsPalette = MaterialPalettes.TRAVELER_BOOTS
        )
        drawLegsAndBoots(ox, oy, u, strokeW, alaricAppearance, false, 1f, 0f, useHighlights, path)
        drawTorsoAndArmor(ox, oy, u, strokeW, alaricAppearance, false, 1f, facing.quadrant == ScreenFacing.BACK, useHighlights, useHdBloom, path)

        // Stitched leather merchant apron over front of tunic
        if (facing.quadrant != ScreenFacing.BACK) {
            path.reset()
            path.moveTo(ox - 4.6f * u, oy - 23.0f * u)
            path.lineTo(ox + 4.6f * u, oy - 23.0f * u)
            path.lineTo(ox + 5.8f * u, oy - 8.2f * u)
            path.lineTo(ox - 5.8f * u, oy - 8.2f * u)
            path.close()
            drawPath(path, color = MaterialPalettes.LEATHER.highlight)
            drawPath(path, color = INK_OUTLINE, style = Stroke(strokeW * 0.85f, join = StrokeJoin.Round))
            // Merchant coin pouch on hip
            drawCircle(Color(0xFFD9A448), radius = 2.0f * u, center = Offset(ox - 4.6f * u, oy - 14.2f * u))
            drawCircle(INK_OUTLINE, radius = 2.0f * u, center = Offset(ox - 4.6f * u, oy - 14.2f * u), style = Stroke(strokeW * 0.75f))
        }

        drawHeadAndHelmet(ox, oy, u, strokeW, alaricAppearance, false, 1f, facing.quadrant == ScreenFacing.BACK, useHighlights, path)
        drawArm(ox - 7.0f * u, oy - 24.5f * u, u, strokeW, merchantTunic, alaricAppearance.skinPrimary, -8f, -1f, useHighlights, path)
        drawArm(ox + 7.0f * u, oy - 24.5f * u, u, strokeW, merchantTunic, alaricAppearance.skinPrimary, 16f, 1f, useHighlights, path)

        // Glowing ruby/emerald alchemical potion flask held in Alaric's right hand
        val flaskX = ox + 8.6f * u
        val flaskY = oy - 16.5f * u + sin(nowMs * 0.005f) * 0.5f * u
        if (useHdBloom) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF4CE88A).copy(alpha = 0.45f), Color.Transparent), Offset(flaskX, flaskY), 6.0f * u),
                radius = 6.0f * u,
                center = Offset(flaskX, flaskY)
            )
        }
        drawCircle(Color(0xFF39D97A), radius = 2.7f * u, center = Offset(flaskX, flaskY))
        drawRect(Color(0xFFDCEBF5), Offset(flaskX - 0.9f * u, flaskY - 4.2f * u), Size(1.8f * u, 2.0f * u))
        drawCircle(Color.White.copy(alpha = 0.75f), radius = 0.9f * u, center = Offset(flaskX - 0.8f * u, flaskY - 0.7f * u))
        drawCircle(INK_OUTLINE, radius = 2.7f * u, center = Offset(flaskX, flaskY), style = Stroke(strokeW * 0.8f))
    }
}
