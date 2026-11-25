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
import com.example.game.model.Vocation
import com.example.game.world.NpcRole
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Distinct 3D humanoid anatomical layers sorted dynamically by projected camera-space depth
 * across all 8 isometric viewing octants (`entityFacing - cameraYaw`).
 */
enum class HumanoidPart {
    BACK_CAPE,
    LEFT_LEG,
    RIGHT_LEG,
    LEFT_ARM_OFFHAND,
    RIGHT_ARM_WEAPON,
    TORSO,
    LEFT_PAULDRON,
    RIGHT_PAULDRON,
    HEAD_AND_HELMET
}

/**
 * Volumetric 2.5D/3D isometric humanoid renderer with crisp `#14100E` dark ink outlines,
 * continuous camera-relative 3D joint projection, dynamic 8-way body-part depth sorting,
 * metallic/wood/arcane specular shading on live paper-doll gear, and thematic Rookvale NPCs.
 */
object ArticulatedHumanoidRenderer {

    private val INK = VolumetricShading.INK
    private val BELT_LEATHER = Color(0xFF4F3118)
    private val BELT_BUCKLE = Color(0xFFF0C660)
    private val SPECULAR_WHITE = Color(0xFFFFF8E2)

    /**
     * Pure deterministic 3D part depth ordering for any [ResolvedScreenFacing].
     * Smaller depths draw first (behind); larger depths draw last (in front).
     */
    fun computePartDrawOrder(facing: ResolvedScreenFacing): List<HumanoidPart> {
        val parts = HumanoidPart.entries
        return parts.sortedBy { depthForPart(it, facing, atkReach = 0f, stride = 0f) }
    }

    private fun depthForPart(
        part: HumanoidPart,
        facing: ResolvedScreenFacing,
        atkReach: Float,
        stride: Float
    ): Float = when (part) {
        HumanoidPart.BACK_CAPE -> if (facing.isBackFacing) 0.35f else -5.6f
        HumanoidPart.LEFT_LEG -> facing.depthOf(-3.2f, -stride * 4.4f) - 0.8f
        HumanoidPart.RIGHT_LEG -> facing.depthOf(+3.2f, +stride * 4.4f) - 0.8f
        HumanoidPart.TORSO -> 0.0f
        HumanoidPart.LEFT_ARM_OFFHAND -> facing.depthOf(-7.8f, 1.8f)
        HumanoidPart.RIGHT_ARM_WEAPON -> facing.depthOf(+7.8f, 2.2f + atkReach * 4.2f)
        HumanoidPart.LEFT_PAULDRON -> 0.45f + facing.depthOf(-7.0f, 0.2f) * 0.85f
        HumanoidPart.RIGHT_PAULDRON -> 0.45f + facing.depthOf(+7.0f, 0.2f) * 0.85f
        HumanoidPart.HEAD_AND_HELMET -> 0.72f + facing.forwardDepth * 0.35f
    }

    fun drawPlayer(
        scope: DrawScope,
        cx: Float,
        feetY: Float,
        heightPx: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        walkPhase: Float,
        attackProgress: Float,
        nowMs: Long,
        quality: GraphicsQuality,
        path: Path
    ) = with(scope) {
        val u = heightPx / 46f
        val ow = (u * 1.38f).coerceIn(1.5f, 3.4f)
        val useSpecular = quality != GraphicsQuality.LOW
        val useRim = quality == GraphicsQuality.HIGH || quality == GraphicsQuality.HD

        val isMoving = abs(walkPhase) > 0.01f
        val stride = sin(walkPhase * PI.toFloat() * 2f)
        val bobUp = if (isMoving) abs(stride) * 1.7f else sin(nowMs * 0.0032f) * 0.45f

        val atkEase = if (attackProgress in 0.001f..0.999f) sin(attackProgress * PI.toFloat()) else 0f
        val lungeX = facing.forwardScreenX * atkEase * 4.2f * u
        val lungeY = facing.forwardDepth * atkEase * 2.0f * u

        val ox = cx + lungeX
        val oy = feetY + lungeY

        val hasCape = appearance.armorKind == ArmorVisualKind.ROBE ||
            appearance.vocation == Vocation.PALADIN ||
            appearance.armorKind == ArmorVisualKind.WYRMSCALE

        // Zero-allocation insertion sort of the 9 3D body layers by exact projected depth
        val order = IntArray(9) { it }
        val depths = FloatArray(9) { idx ->
            depthForPart(HumanoidPart.entries[idx], facing, atkEase, stride)
        }
        for (i in 1 until 9) {
            val keyIdx = order[i]
            val keyD = depths[keyIdx]
            var j = i - 1
            while (j >= 0 && depths[order[j]] > keyD) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = keyIdx
        }

        for (i in 0 until 9) {
            when (HumanoidPart.entries[order[i]]) {
                HumanoidPart.BACK_CAPE -> if (hasCape) {
                    drawCape3D(ox, oy, u, ow, appearance, facing, bobUp, stride, useRim, path)
                }
                HumanoidPart.LEFT_LEG -> drawLeg3D(
                    ox, oy, u, ow, appearance, facing, localSide = -1f,
                    strideF = -stride * 4.8f, liftUp = (-stride).coerceAtLeast(0f) * 2.2f,
                    bobUp = bobUp, useSpecular = useSpecular, path = path
                )
                HumanoidPart.RIGHT_LEG -> drawLeg3D(
                    ox, oy, u, ow, appearance, facing, localSide = +1f,
                    strideF = +stride * 4.8f, liftUp = stride.coerceAtLeast(0f) * 2.2f,
                    bobUp = bobUp, useSpecular = useSpecular, path = path
                )
                HumanoidPart.TORSO -> drawTorso3D(
                    ox, oy, u, ow, appearance, facing, bobUp, useSpecular, useRim, path
                )
                HumanoidPart.LEFT_PAULDRON -> drawPauldron3D(
                    ox, oy, u, ow, appearance, facing, localSide = -1f, bobUp = bobUp,
                    useSpecular = useSpecular, useRim = useRim
                )
                HumanoidPart.RIGHT_PAULDRON -> drawPauldron3D(
                    ox, oy, u, ow, appearance, facing, localSide = +1f, bobUp = bobUp,
                    useSpecular = useSpecular, useRim = useRim
                )
                HumanoidPart.LEFT_ARM_OFFHAND -> drawOffhandArmAndGear3D(
                    ox, oy, u, ow, appearance, facing, bobUp, stride, atkEase, nowMs, quality, path
                )
                HumanoidPart.RIGHT_ARM_WEAPON -> drawWeaponArmAndGear3D(
                    ox, oy, u, ow, appearance, facing, bobUp, stride, atkEase, attackProgress, nowMs, quality, path
                )
                HumanoidPart.HEAD_AND_HELMET -> drawHeadAndHelmet3D(
                    ox, oy, u, ow, appearance, facing, bobUp, useSpecular, useRim, path
                )
            }
        }

        // Crisp 3D Combat Swing Arc / Arcane Cast Ring on Medium/High/HD
        if (attackProgress in 0.04f..0.95f && quality != GraphicsQuality.LOW) {
            drawCombatAttackTrail3D(ox, oy, u, appearance, facing, bobUp, attackProgress, quality)
        }
    }

    // -------------------------------------------------------------------------
    // 1. 3D Volumetric Back Cape / Paladin Cloak / Mage Mantle
    // -------------------------------------------------------------------------
    private fun DrawScope.drawCape3D(
        ox: Float, oy: Float, u: Float, ow: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        bobUp: Float, stride: Float, useRim: Boolean, path: Path
    ) {
        val sway = stride * 2.2f
        val topL = facing.project(ox, oy, u, -6.2f, -3.4f, 28.5f + bobUp)
        val topR = facing.project(ox, oy, u, +6.2f, -3.4f, 28.5f + bobUp)
        val botL = facing.project(ox, oy, u, -8.5f, -6.4f - abs(sway) * 0.4f, 6.2f + bobUp * 0.4f)
        val botR = facing.project(ox, oy, u, +8.5f, -6.4f - abs(sway) * 0.4f, 6.2f + bobUp * 0.4f)
        val botMid = facing.project(ox, oy, u, sway * 0.4f, -7.2f - abs(sway) * 0.5f, 4.4f + bobUp * 0.3f)

        val pal = appearance.armorPalette
        path.reset()
        path.moveTo(topL.x, topL.y)
        path.lineTo(topR.x, topR.y)
        path.lineTo(botR.x, botR.y)
        path.quadraticTo(botMid.x, botMid.y, botL.x, botL.y)
        path.close()

        val minX = minOf(topL.x, topR.x, botL.x, botR.x)
        val maxX = maxOf(topL.x, topR.x, botL.x, botR.x)
        val topY = minOf(topL.y, topR.y)
        val botY = maxOf(botL.y, botR.y, botMid.y)

        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to if (facing.isBackFacing) pal.highlight else pal.primary,
                    0.50f to pal.primary,
                    1.0f to VolumetricShading.coolShadow(pal.shadow, 0.35f)
                ),
                start = Offset(minX, topY),
                end = Offset(maxX, botY)
            )
        )
        if (facing.isBackFacing) {
            // Vertical volumetric drapery fold crease & golden embroidered hem on back view
            val foldTop = facing.project(ox, oy, u, 0f, -3.8f, 27.5f + bobUp)
            drawLine(
                color = VolumetricShading.coolShadow(pal.shadow, 0.4f).copy(alpha = 0.55f),
                start = foldTop,
                end = botMid,
                strokeWidth = 1.6f * u,
                cap = StrokeCap.Round
            )
            drawLine(
                color = pal.accent,
                start = Offset(botL.x + (botR.x - botL.x) * 0.1f, botL.y - 1.5f * u),
                end = Offset(botR.x - (botR.x - botL.x) * 0.1f, botR.y - 1.5f * u),
                strokeWidth = 1.6f * u,
                cap = StrokeCap.Round
            )
        }
        if (useRim && facing.isBackFacing) {
            drawLine(
                color = Color(0x48BFD4FF),
                start = topL,
                end = topR,
                strokeWidth = ow,
                cap = StrokeCap.Round
            )
        }
        drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))
    }

    // -------------------------------------------------------------------------
    // 2. 3D Volumetric Articulated Leg & Foreshortened Boot + Contact Shadow
    // -------------------------------------------------------------------------
    private fun DrawScope.drawLeg3D(
        ox: Float, oy: Float, u: Float, ow: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        localSide: Float,
        strideF: Float,
        liftUp: Float,
        bobUp: Float,
        useSpecular: Boolean,
        path: Path
    ) {
        val hip = facing.project(ox, oy, u, localSide * 3.1f, 0f, 15.8f + bobUp)
        val knee = facing.project(ox, oy, u, localSide * 3.2f, strideF * 0.55f + 0.9f, 8.6f + liftUp * 0.6f)
        val ankle = facing.project(ox, oy, u, localSide * 3.2f, strideF, 2.5f + liftUp)
        val toe = facing.project(ox, oy, u, localSide * 3.2f, strideF + 3.0f, 1.4f + liftUp * 0.65f)

        // Subtle ambient contact darkening directly under planted foot
        if (liftUp < 0.9f) {
            VolumetricShading.drawFootContactDot(this, ankle, 3.2f * u, 1.8f * u)
        }

        val legPal = appearance.legsPalette
        VolumetricShading.drawVolumetricCylinder(
            this, hip, knee, 3.05f * u, 2.65f * u,
            legPal.primary, legPal.highlight, legPal.shadow, ow, facing.lightScreenX, useSpecular, path
        )
        val bootPal = appearance.bootsPalette
        VolumetricShading.drawVolumetricCylinder(
            this, knee, ankle, 2.65f * u, 2.35f * u,
            bootPal.primary, bootPal.highlight, bootPal.shadow, ow, facing.lightScreenX, useSpecular, path
        )
        // Sculpted boot cuff / armored knee greave
        VolumetricShading.drawVolumetricEllipsoid(
            this, knee.x, knee.y, 2.55f * u, 1.55f * u,
            bootPal.accent, bootPal.highlight, bootPal.shadow, ow * 0.72f,
            facing.lightScreenX, facing.lightScreenY, useSpecular = appearance.bootsKind == BootsVisualKind.PLATE && useSpecular
        )
        // Foreshortened 3D boot foot capsule extending forward along facing
        VolumetricShading.drawVolumetricCylinder(
            this, ankle, toe, 2.45f * u, 2.1f * u,
            bootPal.primary, bootPal.highlight, bootPal.shadow, ow, facing.lightScreenX, useSpecular, path
        )
        if (appearance.bootsKind == BootsVisualKind.PLATE && useSpecular) {
            drawCircle(SPECULAR_WHITE.copy(alpha = 0.55f), 1.05f * u, knee)
        }
    }

    // -------------------------------------------------------------------------
    // 3. 3D Volumetric Torso, Layered Armor (Front & Back), Waist Belt & Amulet
    // -------------------------------------------------------------------------
    private fun DrawScope.drawTorso3D(
        ox: Float, oy: Float, u: Float, ow: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        bobUp: Float,
        useSpecular: Boolean,
        useRim: Boolean,
        path: Path
    ) {
        val pal = appearance.armorPalette
        val chestTop = facing.project(ox, oy, u, 0f, 0f, 29.2f + bobUp)
        val waist = facing.project(ox, oy, u, 0f, 0f, 15.6f + bobUp)

        // Heroic broad shoulders tapering into an athletic waist (foreshortened cleanly in side profile)
        val profileFactor = 0.74f + 0.26f * abs(facing.forwardDepth)
        val armorBulk = if (appearance.armorKind == ArmorVisualKind.PLATE || appearance.armorKind == ArmorVisualKind.WYRMSCALE) 1.15f else 0f
        val topHalfW = (8.2f * profileFactor + armorBulk) * u
        val botHalfW = (if (appearance.armorKind == ArmorVisualKind.ROBE) 8.6f else 5.2f * profileFactor) * u
        val domeH = 3.5f * u

        // Flowing lower robe skirt for Mage Robes
        if (appearance.armorKind == ArmorVisualKind.ROBE) {
            val hemCenter = facing.project(ox, oy, u, 0f, 0f, 4.8f + bobUp * 0.4f)
            VolumetricShading.drawVolumetricTorso(
                this, waist, hemCenter, botHalfW * 0.88f, 9.2f * u * (0.78f + 0.22f * abs(facing.forwardDepth)),
                2.8f * u, pal.primary, pal.highlight, pal.shadow, ow, facing.lightScreenX, useRim, path
            )
            // Vertical embroidered gold trim down front or back of robe
            val trimStart = facing.project(ox, oy, u, 0f, if (facing.isBackFacing) -4.2f else 4.2f, 16.0f + bobUp)
            val trimEnd = facing.project(ox, oy, u, 0f, if (facing.isBackFacing) -5.4f else 5.4f, 5.4f + bobUp * 0.4f)
            drawLine(pal.accent, trimStart, trimEnd, strokeWidth = 1.9f * u, cap = StrokeCap.Round)
        }

        // Main volumetric tapered torso
        VolumetricShading.drawVolumetricTorso(
            this, chestTop, waist, topHalfW, botHalfW, domeH,
            pal.primary, pal.highlight, pal.shadow, ow, facing.lightScreenX, useRim, path
        )

        // Foreshortened 38° collar / yoke ellipse at top of torso
        val collarCenter = facing.project(ox, oy, u, 0f, 0f, 29.5f + bobUp)
        VolumetricShading.drawVolumetricEllipsoid(
            this, collarCenter.x, collarCenter.y, topHalfW * 0.78f, 2.5f * u,
            pal.shadow, pal.primary, VolumetricShading.coolShadow(pal.shadow, 0.45f), ow * 0.85f,
            facing.lightScreenX, facing.lightScreenY, useSpecular = false
        )

        // Surface armor reliefs — distinct Front vs. Back details!
        val chestFaceF = if (facing.isBackFacing) -3.8f else 3.8f
        val chestCenter = facing.project(ox, oy, u, 0f, chestFaceF, 23.2f + bobUp)
        when (appearance.armorKind) {
            ArmorVisualKind.PLATE -> {
                if (!facing.isBackFacing) {
                    // Sculpted 3D breastplate ridge & specular V-plastron
                    val topPl = facing.project(ox, oy, u, 0f, 4.3f, 27.6f + bobUp)
                    val botPl = facing.project(ox, oy, u, 0f, 3.6f, 17.5f + bobUp)
                    drawLine(pal.highlight, topPl, botPl, strokeWidth = 1.8f * u, cap = StrokeCap.Round)
                    if (useSpecular) {
                        val specL = facing.project(ox, oy, u, -2.5f, 4.2f, 25.0f + bobUp)
                        val specR = facing.project(ox, oy, u, +2.5f, 4.2f, 25.0f + bobUp)
                        drawLine(SPECULAR_WHITE.copy(alpha = 0.50f), specL, specR, strokeWidth = 1.2f * u, cap = StrokeCap.Round)
                    }
                } else {
                    // Dorsal back-plate armor seam & shoulder-blade rivets
                    val topBk = facing.project(ox, oy, u, 0f, -4.2f, 27.4f + bobUp)
                    val botBk = facing.project(ox, oy, u, 0f, -3.5f, 17.4f + bobUp)
                    drawLine(pal.shadow, topBk, botBk, strokeWidth = 1.6f * u, cap = StrokeCap.Round)
                    drawCircle(pal.accent, 1.0f * u, facing.project(ox, oy, u, -2.6f, -4.0f, 25.2f + bobUp))
                    drawCircle(pal.accent, 1.0f * u, facing.project(ox, oy, u, +2.6f, -4.0f, 25.2f + bobUp))
                }
            }
            ArmorVisualKind.WYRMSCALE -> {
                // Overlapping layered emerald dragon scales with golden crest
                for (row in 0..2) {
                    val rowUp = 26.0f - row * 3.1f + bobUp
                    for (col in -1..1) {
                        val sc = facing.project(ox, oy, u, col * 2.4f, chestFaceF, rowUp)
                        drawCircle(pal.highlight, 1.45f * u, sc)
                        drawCircle(INK, 1.45f * u, sc, style = Stroke(ow * 0.55f))
                    }
                }
                if (useRim) {
                    drawCircle(pal.accent.copy(alpha = 0.65f), 1.6f * u, chestCenter)
                }
            }
            ArmorVisualKind.LEATHER -> {
                // Cross-chest stitched leather baldric strap
                val s1 = facing.project(ox, oy, u, -4.4f, chestFaceF, 27.6f + bobUp)
                val s2 = facing.project(ox, oy, u, +4.4f, chestFaceF, 17.2f + bobUp)
                drawLine(INK, s1, s2, strokeWidth = 2.6f * u, cap = StrokeCap.Round)
                drawLine(BELT_LEATHER, s1, s2, strokeWidth = 1.6f * u, cap = StrokeCap.Round)
                drawCircle(BELT_BUCKLE, 1.15f * u, chestCenter)
            }
            ArmorVisualKind.ROBE -> {
                val trimTop = facing.project(ox, oy, u, 0f, chestFaceF, 28.0f + bobUp)
                val trimBot = facing.project(ox, oy, u, 0f, chestFaceF, 16.5f + bobUp)
                drawLine(pal.accent, trimTop, trimBot, strokeWidth = 2.0f * u, cap = StrokeCap.Round)
            }
            ArmorVisualKind.TUNIC -> {
                if (!facing.isBackFacing) {
                    val vTop = facing.project(ox, oy, u, 0f, 4.0f, 28.2f + bobUp)
                    val vBot = facing.project(ox, oy, u, 0f, 3.8f, 23.8f + bobUp)
                    drawLine(pal.accent, vTop, vBot, strokeWidth = 1.4f * u, cap = StrokeCap.Round)
                }
            }
        }

        // 3D Waist Belt wrapping around foreshortened ellipse + Buckle in front
        val beltL = Offset(waist.x - botHalfW * 1.03f, waist.y - 0.8f * u)
        val beltR = Offset(waist.x + botHalfW * 1.03f, waist.y - 0.8f * u)
        val beltDip = Offset(waist.x, waist.y + 1.4f * u)
        path.reset()
        path.moveTo(beltL.x, beltL.y)
        path.quadraticTo(beltDip.x, beltDip.y, beltR.x, beltR.y)
        drawPath(path, INK, style = Stroke(width = 3.5f * u, cap = StrokeCap.Round))
        drawPath(path, BELT_LEATHER, style = Stroke(width = 2.2f * u, cap = StrokeCap.Round))
        if (!facing.isBackFacing) {
            val buckle = facing.project(ox, oy, u, 0f, 4.2f, 16.0f + bobUp)
            VolumetricShading.drawVolumetricEllipsoid(
                this, buckle.x, buckle.y, 1.8f * u, 1.4f * u,
                BELT_BUCKLE, SPECULAR_WHITE, Color(0xFF9E7022), ow * 0.7f,
                facing.lightScreenX, facing.lightScreenY, useSpecular = false
            )
        }

        // Glowing Ruby Amulet when equipped & facing viewer
        if (appearance.hasAmulet && !facing.isBackFacing) {
            val am = facing.project(ox, oy, u, 0f, 4.5f, 27.6f + bobUp)
            drawCircle(Color(0xFFFF4D6A), 1.5f * u, am)
            drawCircle(INK, 1.5f * u, am, style = Stroke(ow * 0.65f))
        }
    }

    // -------------------------------------------------------------------------
    // 4. 3D Rounded Shoulder Pauldron
    // -------------------------------------------------------------------------
    private fun DrawScope.drawPauldron3D(
        ox: Float, oy: Float, u: Float, ow: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        localSide: Float,
        bobUp: Float,
        useSpecular: Boolean,
        useRim: Boolean
    ) {
        val pos = facing.project(ox, oy, u, localSide * 7.8f, 0.2f, 27.8f + bobUp)
        val isArmored = appearance.armorKind == ArmorVisualKind.PLATE || appearance.armorKind == ArmorVisualKind.WYRMSCALE
        val rx = (if (isArmored) 4.3f else 3.3f) * u
        val ry = (if (isArmored) 3.5f else 2.8f) * u
        val pal = appearance.armorPalette
        VolumetricShading.drawVolumetricEllipsoid(
            this, pos.x, pos.y, rx, ry,
            pal.primary, pal.highlight, pal.shadow, ow,
            facing.lightScreenX, facing.lightScreenY,
            useSpecular = useSpecular && isArmored,
            useRim = useRim
        )
        if (isArmored) {
            drawArc(
                color = pal.accent,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(pos.x - rx * 0.78f, pos.y - ry * 0.55f),
                size = Size(rx * 1.56f, ry * 1.30f),
                style = Stroke(width = 1.25f * u, cap = StrokeCap.Round)
            )
        }
    }

    // -------------------------------------------------------------------------
    // 5. 3D Volumetric Head, Hair/Face OR Rear Hair/Helmet-Back
    // -------------------------------------------------------------------------
    private fun DrawScope.drawHeadAndHelmet3D(
        ox: Float, oy: Float, u: Float, ow: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        bobUp: Float,
        useSpecular: Boolean,
        useRim: Boolean,
        path: Path
    ) {
        val neck = facing.project(ox, oy, u, 0f, 0.2f, 30.2f + bobUp)
        val headCenter = facing.project(ox, oy, u, 0f, 0.4f, 35.6f + bobUp)
        val headR = 5.9f * u

        // Cylindrical neck grounding the head onto the torso
        VolumetricShading.drawVolumetricCylinder(
            this, neck, Offset(headCenter.x, headCenter.y + headR * 0.45f),
            2.4f * u, 2.2f * u,
            appearance.skinPrimary, appearance.skinHighlight, appearance.skinShadow,
            ow * 0.85f, facing.lightScreenX, useHighlightStrip = false, path = path
        )

        // Volumetric 3D spherical cranium
        VolumetricShading.drawVolumetricEllipsoid(
            this, headCenter.x, headCenter.y, headR, headR * 0.96f,
            appearance.skinPrimary, appearance.skinHighlight, appearance.skinShadow, ow,
            facing.lightScreenX, facing.lightScreenY,
            useSpecular = useSpecular, useRim = useRim
        )

        val hp = appearance.hairPalette
        if (facing.isBackFacing) {
            // TRUE BACK VIEW: Full sculpted back-of-head hair & nape — NO flat front face!
            VolumetricShading.drawVolumetricEllipsoid(
                this, headCenter.x, headCenter.y - 0.2f * u, headR * 1.06f, headR * 1.02f,
                hp.primary, hp.highlight, hp.shadow, ow,
                facing.lightScreenX, facing.lightScreenY, useSpecular = useSpecular, useRim = useRim
            )
            // Hair strands / nape taper
            val napeL = Offset(headCenter.x - headR * 0.72f, headCenter.y + headR * 0.62f)
            val napeR = Offset(headCenter.x + headR * 0.72f, headCenter.y + headR * 0.62f)
            drawLine(hp.shadow, napeL, napeR, strokeWidth = 1.6f * u, cap = StrokeCap.Round)
        } else {
            // FRONT / 3/4 / SIDE VIEW: Eyes & nose bridge project smoothly across the spherical face!
            val eyeF = 5.4f
            val eyeUp = 35.2f + bobUp
            val leftEyeDepth = facing.depthOf(-2.0f, eyeF)
            val rightEyeDepth = facing.depthOf(+2.0f, eyeF)
            if (leftEyeDepth > 0.2f) {
                val eL = facing.project(ox, oy, u, -2.0f, eyeF, eyeUp)
                drawCircle(INK, 1.05f * u, eL)
                if (useSpecular) drawCircle(Color.White, 0.36f * u, Offset(eL.x - 0.25f * u, eL.y - 0.28f * u))
            }
            if (rightEyeDepth > 0.2f) {
                val eR = facing.project(ox, oy, u, +2.0f, eyeF, eyeUp)
                drawCircle(INK, 1.05f * u, eR)
                if (useSpecular) drawCircle(Color.White, 0.36f * u, Offset(eR.x - 0.25f * u, eR.y - 0.28f * u))
            }
            // Crisp comic brow & nose definition shifting with continuous yaw
            val nose = facing.project(ox, oy, u, 0f, 6.0f, 33.8f + bobUp)
            drawCircle(appearance.skinShadow, 0.80f * u, nose)

            // Sculpted jaw beard accent for rugged Knights when front-facing
            if (appearance.vocation == Vocation.KNIGHT && appearance.helmetKind != HelmetVisualKind.MAGE_HOOD) {
                val chin = facing.project(ox, oy, u, 0f, 5.1f, 31.8f + bobUp)
                VolumetricShading.drawVolumetricEllipsoid(
                    this, chin.x, chin.y, 3.2f * u, 2.0f * u,
                    hp.primary, hp.highlight, hp.shadow, ow * 0.62f,
                    facing.lightScreenX, facing.lightScreenY, useSpecular = false
                )
            }

            // Sculpted volumetric hair crown & comic hair locks when bareheaded
            if (appearance.helmetKind == HelmetVisualKind.NONE) {
                val crown = facing.project(ox, oy, u, 0f, -0.8f, 39.0f + bobUp)
                VolumetricShading.drawVolumetricEllipsoid(
                    this, crown.x, crown.y, headR * 1.08f, headR * 0.66f,
                    hp.primary, hp.highlight, hp.shadow, ow,
                    facing.lightScreenX, facing.lightScreenY, useSpecular = useSpecular
                )
                val browLock = facing.project(ox, oy, u, 1.4f, 4.4f, 38.4f + bobUp)
                drawLine(hp.highlight, crown, browLock, strokeWidth = 1.4f * u, cap = StrokeCap.Round)
            }
        }

        // 3D Volumetric Helmets / Hoods / Wizard Hats
        drawHelmet3D(ox, oy, u, ow, headCenter, headR, appearance, facing, bobUp, useSpecular, useRim, path)
    }

    private fun DrawScope.drawHelmet3D(
        ox: Float, oy: Float, u: Float, ow: Float,
        headCenter: Offset, headR: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        bobUp: Float,
        useSpecular: Boolean,
        useRim: Boolean,
        path: Path
    ) {
        val pal = appearance.helmetPalette
        when (appearance.helmetKind) {
            HelmetVisualKind.NONE -> Unit
            HelmetVisualKind.LEATHER_CAP -> {
                val domeY = headCenter.y - headR * 0.26f
                VolumetricShading.drawVolumetricEllipsoid(
                    this, headCenter.x, domeY, headR * 1.12f, headR * 0.78f,
                    pal.primary, pal.highlight, pal.shadow, ow,
                    facing.lightScreenX, facing.lightScreenY, useSpecular = useSpecular, useRim = useRim
                )
                // Stitched leather brow band
                drawArc(
                    pal.accent, 10f, 160f, false,
                    Offset(headCenter.x - headR * 1.08f, domeY - headR * 0.2f),
                    Size(headR * 2.16f, headR * 1.1f),
                    style = Stroke(width = 1.4f * u, cap = StrokeCap.Round)
                )
            }
            HelmetVisualKind.VIKING_HELM -> {
                // Curved 3D horns projecting from helmet temples along +/-localR!
                for (side in listOf(-1f, 1f)) {
                    val base = facing.project(ox, oy, u, side * 5.6f, 0.2f, 38.4f + bobUp)
                    val mid = facing.project(ox, oy, u, side * 9.5f, 1.2f, 41.2f + bobUp)
                    val tip = facing.project(ox, oy, u, side * 8.4f, 2.2f, 46.2f + bobUp)
                    path.reset()
                    path.moveTo(base.x, base.y + 1.4f * u)
                    path.quadraticTo(mid.x, mid.y + 1.4f * u, tip.x, tip.y)
                    path.quadraticTo(mid.x, mid.y - 1.6f * u, base.x, base.y - 1.6f * u)
                    path.close()
                    drawPath(
                        path,
                        Brush.linearGradient(
                            listOf(Color(0xFFFFF8E8), Color(0xFFE6D7B8), Color(0xFF9C8A6D)),
                            start = tip, end = base
                        )
                    )
                    drawPath(path, INK, style = Stroke(width = ow * 0.9f, join = StrokeJoin.Round))
                }
                // Metallic domed steel helm
                val domeY = headCenter.y - headR * 0.25f
                VolumetricShading.drawVolumetricEllipsoid(
                    this, headCenter.x, domeY, headR * 1.15f, headR * 0.85f,
                    pal.primary, pal.highlight, pal.shadow, ow,
                    facing.lightScreenX, facing.lightScreenY, useSpecular = true, useRim = useRim
                )
                // Gold rim band + nasal guard in front OR steel neck guard in back!
                drawArc(
                    pal.accent, 8f, 164f, false,
                    Offset(headCenter.x - headR * 1.12f, domeY - headR * 0.1f),
                    Size(headR * 2.24f, headR * 1.15f),
                    style = Stroke(width = 1.6f * u, cap = StrokeCap.Round)
                )
                if (!facing.isBackFacing) {
                    val nTop = facing.project(ox, oy, u, 0f, 6.2f, 37.6f + bobUp)
                    val nBot = facing.project(ox, oy, u, 0f, 6.4f, 33.4f + bobUp)
                    VolumetricShading.drawVolumetricCylinder(
                        this, nTop, nBot, 1.0f * u, 0.85f * u,
                        pal.highlight, SPECULAR_WHITE, pal.shadow, ow * 0.7f, facing.lightScreenX, false, path
                    )
                } else {
                    val guardCenter = facing.project(ox, oy, u, 0f, -5.2f, 32.8f + bobUp)
                    VolumetricShading.drawVolumetricEllipsoid(
                        this, guardCenter.x, guardCenter.y, headR * 0.98f, 2.8f * u,
                        pal.shadow, pal.primary, VolumetricShading.coolShadow(pal.shadow, 0.4f), ow * 0.85f,
                        facing.lightScreenX, facing.lightScreenY, useSpecular = false
                    )
                }
            }
            HelmetVisualKind.MAGE_HOOD -> {
                val hoodPeak = facing.project(ox, oy, u, 0f, -3.4f, 44.2f + bobUp)
                val hoodL = Offset(headCenter.x - headR * 1.24f, headCenter.y + headR * 0.45f)
                val hoodR = Offset(headCenter.x + headR * 1.24f, headCenter.y + headR * 0.45f)
                path.reset()
                path.moveTo(hoodPeak.x, hoodPeak.y)
                path.quadraticTo(headCenter.x + headR * 1.35f, headCenter.y - headR * 0.4f, hoodR.x, hoodR.y)
                path.quadraticTo(headCenter.x, headCenter.y + headR * 0.95f, hoodL.x, hoodL.y)
                path.quadraticTo(headCenter.x - headR * 1.35f, headCenter.y - headR * 0.4f, hoodPeak.x, hoodPeak.y)
                path.close()
                drawPath(
                    path,
                    Brush.linearGradient(
                        listOf(pal.highlight, pal.primary, VolumetricShading.coolShadow(pal.shadow, 0.4f)),
                        start = Offset(hoodL.x, hoodPeak.y), end = hoodR
                    )
                )
                drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))
                if (!facing.isBackFacing) {
                    val faceC = facing.project(ox, oy, u, 0f, 4.2f, 35.4f + bobUp)
                    drawOval(
                        Color(0xFF1A1424),
                        Offset(faceC.x - headR * 0.65f, faceC.y - headR * 0.55f),
                        Size(headR * 1.30f, headR * 1.12f)
                    )
                    val eL = facing.project(ox, oy, u, -1.8f, 5.2f, 35.4f + bobUp)
                    val eR = facing.project(ox, oy, u, +1.8f, 5.2f, 35.4f + bobUp)
                    drawCircle(Color(0xFF8AEBFF), 1.0f * u, eL)
                    drawCircle(Color(0xFF8AEBFF), 1.0f * u, eR)
                }
            }
            HelmetVisualKind.WIZARD_HAT -> {
                val brimCenter = facing.project(ox, oy, u, 0f, 0f, 39.4f + bobUp)
                // Foreshortened 38° wide elliptical wizard hat brim
                VolumetricShading.drawVolumetricEllipsoid(
                    this, brimCenter.x, brimCenter.y, headR * 1.75f, headR * 0.72f,
                    pal.primary, pal.highlight, pal.shadow, ow,
                    facing.lightScreenX, facing.lightScreenY, useSpecular = false, useRim = useRim
                )
                // Tilted pointed wizard cone
                val tip = facing.project(ox, oy, u, -2.0f, -3.5f, 51.5f + bobUp)
                val baseL = Offset(brimCenter.x - headR * 0.92f, brimCenter.y - 0.4f * u)
                val baseR = Offset(brimCenter.x + headR * 0.92f, brimCenter.y - 0.4f * u)
                path.reset()
                path.moveTo(baseL.x, baseL.y)
                path.quadraticTo(brimCenter.x - headR * 0.3f, brimCenter.y - headR * 1.2f, tip.x, tip.y)
                path.quadraticTo(brimCenter.x + headR * 0.6f, brimCenter.y - headR * 0.9f, baseR.x, baseR.y)
                path.quadraticTo(brimCenter.x, brimCenter.y + 1.8f * u, baseL.x, baseL.y)
                path.close()
                drawPath(
                    path,
                    Brush.linearGradient(listOf(pal.highlight, pal.primary, pal.shadow), start = baseL, end = baseR)
                )
                drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))
                // Golden hatband
                drawLine(pal.accent, baseL, baseR, strokeWidth = 1.6f * u, cap = StrokeCap.Round)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 6. 3D Left (Off-hand) Arm + Curved 3D Shield / Arcane Focus Orb
    // -------------------------------------------------------------------------
    private fun DrawScope.drawOffhandArmAndGear3D(
        ox: Float, oy: Float, u: Float, ow: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        bobUp: Float, stride: Float, atkEase: Float, nowMs: Long,
        quality: GraphicsQuality, path: Path
    ) {
        val useSpec = quality != GraphicsQuality.LOW
        val useRim = quality == GraphicsQuality.HIGH || quality == GraphicsQuality.HD
        val shPos = facing.project(ox, oy, u, -7.8f, 0.3f, 27.0f + bobUp)
        val hasShield = appearance.offhandKind != OffhandVisualKind.NONE
        val handF = if (hasShield) 5.6f + atkEase * 1.4f else 2.2f + stride * 3.8f
        val handUp = if (hasShield) 20.6f + bobUp else 17.5f + bobUp
        val elbow = facing.project(ox, oy, u, -9.0f, handF * 0.45f, 21.8f + bobUp)
        val hand = facing.project(ox, oy, u, -8.2f, handF, handUp)

        val sleevePal = appearance.armorPalette
        val bracerPal = if (appearance.armorKind == ArmorVisualKind.PLATE || appearance.armorKind == ArmorVisualKind.WYRMSCALE) {
            appearance.armorPalette
        } else {
            appearance.bootsPalette
        }
        VolumetricShading.drawVolumetricCylinder(
            this, shPos, elbow, 2.65f * u, 2.25f * u,
            sleevePal.primary, sleevePal.highlight, sleevePal.shadow, ow, facing.lightScreenX, useSpec, path
        )
        // Armored bracer forearm transitioning into sculpted hand
        VolumetricShading.drawVolumetricCylinder(
            this, elbow, hand, 2.25f * u, 1.90f * u,
            bracerPal.primary, bracerPal.highlight, bracerPal.shadow, ow, facing.lightScreenX, useSpec, path
        )
        VolumetricShading.drawVolumetricEllipsoid(
            this, hand.x, hand.y, 2.05f * u, 1.85f * u,
            appearance.skinPrimary, appearance.skinHighlight, appearance.skinShadow, ow * 0.85f,
            facing.lightScreenX, facing.lightScreenY, useSpecular = false
        )

        if (hasShield) {
            drawOffhandGear3D(hand, u, ow, appearance, facing, nowMs, useSpec, useRim, path)
        }
    }

    private fun DrawScope.drawOffhandGear3D(
        hand: Offset, u: Float, ow: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        nowMs: Long, useSpec: Boolean, useRim: Boolean, path: Path
    ) {
        val pal = appearance.offhandPalette
        // Shield normal points outward-left & forward from the hero: (-0.55 R, +0.83 F)
        val shieldFaceDot = facing.depthOf(-0.55f, 0.83f)
        val showingOuterFace = shieldFaceDot >= -0.12f
        val foreshortenW = (0.48f + 0.52f * abs(shieldFaceDot)).coerceIn(0.45f, 1.0f)

        when (appearance.offhandKind) {
            OffhandVisualKind.NONE -> Unit
            OffhandVisualKind.ARCANE_ORB -> {
                val pulse = 1f + 0.12f * sin(nowMs * 0.007f)
                val orbC = Offset(hand.x, hand.y - 2.6f * u)
                if (useRim) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf((pal.glow ?: Color(0xFF6CE8FF)).copy(alpha = 0.52f), Color.Transparent),
                            center = orbC, radius = 8.5f * u * pulse
                        ),
                        radius = 8.5f * u * pulse, center = orbC
                    )
                }
                VolumetricShading.drawVolumetricEllipsoid(
                    this, orbC.x, orbC.y, 3.6f * u * pulse, 3.6f * u * pulse,
                    pal.primary, Color.White, pal.shadow, ow,
                    facing.lightScreenX, facing.lightScreenY, useSpecular = true, useRim = useRim
                )
            }
            OffhandVisualKind.ROUND_SHIELD -> {
                val rx = 6.2f * u * foreshortenW
                val ry = 6.6f * u
                val fillMain = if (showingOuterFace) pal.primary else VolumetricShading.coolShadow(pal.shadow, 0.35f)
                val fillHi = if (showingOuterFace) pal.highlight else pal.shadow
                VolumetricShading.drawVolumetricEllipsoid(
                    this, hand.x, hand.y, rx, ry,
                    fillMain, fillHi, pal.shadow, ow,
                    facing.lightScreenX, facing.lightScreenY, useSpecular = useSpec && showingOuterFace, useRim = useRim
                )
                // Metallic rim ring
                drawOval(
                    pal.accent,
                    Offset(hand.x - rx * 0.88f, hand.y - ry * 0.88f),
                    Size(rx * 1.76f, ry * 1.76f),
                    style = Stroke(width = 1.4f * u)
                )
                if (showingOuterFace) {
                    // 3D domed central steel boss
                    VolumetricShading.drawVolumetricEllipsoid(
                        this, hand.x, hand.y, 2.2f * u * foreshortenW, 2.2f * u,
                        pal.accent, SPECULAR_WHITE, Color(0xFF6E5322), ow * 0.7f,
                        facing.lightScreenX, facing.lightScreenY, useSpecular = true
                    )
                } else {
                    // Inner leather grip strap on back of shield
                    drawLine(BELT_LEATHER, Offset(hand.x - rx * 0.4f, hand.y), Offset(hand.x + rx * 0.4f, hand.y), strokeWidth = 2.0f * u, cap = StrokeCap.Round)
                }
            }
            OffhandVisualKind.KITE_SHIELD, OffhandVisualKind.DRAGON_SHIELD -> {
                val hw = 6.0f * u * foreshortenW
                val hh = 8.4f * u
                path.reset()
                path.moveTo(hand.x - hw, hand.y - hh * 0.72f)
                path.quadraticTo(hand.x, hand.y - hh * 1.02f, hand.x + hw, hand.y - hh * 0.72f)
                path.quadraticTo(hand.x + hw * 0.92f, hand.y + hh * 0.32f, hand.x, hand.y + hh * 1.05f)
                path.quadraticTo(hand.x - hw * 0.92f, hand.y + hh * 0.32f, hand.x - hw, hand.y - hh * 0.72f)
                path.close()

                val mainC = if (showingOuterFace) pal.primary else VolumetricShading.coolShadow(pal.shadow, 0.35f)
                val hiC = if (showingOuterFace) pal.highlight else pal.shadow
                drawPath(
                    path,
                    Brush.linearGradient(
                        colorStops = arrayOf(0.0f to hiC, 0.45f to mainC, 1.0f to VolumetricShading.coolShadow(pal.shadow, 0.35f)),
                        start = Offset(hand.x - hw, hand.y - hh),
                        end = Offset(hand.x + hw, hand.y + hh)
                    )
                )
                // Metallic rim bevel
                drawPath(path, pal.accent, style = Stroke(width = 1.6f * u, join = StrokeJoin.Round))
                drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))
                if (showingOuterFace) {
                    if (appearance.offhandKind == OffhandVisualKind.DRAGON_SHIELD) {
                        // Glowing wyrmscale emblem + central ridge
                        drawLine(pal.accent, Offset(hand.x, hand.y - hh * 0.55f), Offset(hand.x, hand.y + hh * 0.65f), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
                        drawCircle(pal.glow ?: Color(0xFF5DF09E), 2.1f * u * foreshortenW, Offset(hand.x, hand.y - 0.6f * u))
                    } else {
                        // Dwarven riveted steel cross & specular gleam
                        drawLine(pal.accent, Offset(hand.x, hand.y - hh * 0.58f), Offset(hand.x, hand.y + hh * 0.68f), strokeWidth = 1.5f * u)
                        drawLine(pal.accent, Offset(hand.x - hw * 0.62f, hand.y - hh * 0.14f), Offset(hand.x + hw * 0.62f, hand.y - hh * 0.14f), strokeWidth = 1.5f * u)
                    }
                    if (useSpec) {
                        drawLine(
                            SPECULAR_WHITE.copy(alpha = 0.42f),
                            Offset(hand.x - hw * 0.52f, hand.y - hh * 0.55f),
                            Offset(hand.x - hw * 0.18f, hand.y + hh * 0.20f),
                            strokeWidth = 1.3f * u, cap = StrokeCap.Round
                        )
                    }
                } else {
                    drawLine(BELT_LEATHER, Offset(hand.x - hw * 0.45f, hand.y), Offset(hand.x + hw * 0.45f, hand.y), strokeWidth = 2.0f * u, cap = StrokeCap.Round)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 7. 3D Right (Weapon) Arm + Volumetric 3D-Shaded Weapons
    // -------------------------------------------------------------------------
    private fun DrawScope.drawWeaponArmAndGear3D(
        ox: Float, oy: Float, u: Float, ow: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        bobUp: Float, stride: Float, atkEase: Float, attackProgress: Float,
        nowMs: Long, quality: GraphicsQuality, path: Path
    ) {
        val useSpec = quality != GraphicsQuality.LOW
        val useRim = quality == GraphicsQuality.HIGH || quality == GraphicsQuality.HD

        val shPos = facing.project(ox, oy, u, +7.8f, 0.3f, 27.0f + bobUp)
        val swingF = 3.2f - stride * 3.8f + atkEase * 8.0f
        val swingUp = 18.6f + bobUp + atkEase * 5.4f
        val elbow = facing.project(ox, oy, u, +9.0f, swingF * 0.45f, 21.8f + bobUp + atkEase * 1.5f)
        val hand = facing.project(ox, oy, u, +8.2f, swingF, swingUp)

        val sleevePal = appearance.armorPalette
        val bracerPal = if (appearance.armorKind == ArmorVisualKind.PLATE || appearance.armorKind == ArmorVisualKind.WYRMSCALE) {
            appearance.armorPalette
        } else {
            appearance.bootsPalette
        }
        VolumetricShading.drawVolumetricCylinder(
            this, shPos, elbow, 2.65f * u, 2.25f * u,
            sleevePal.primary, sleevePal.highlight, sleevePal.shadow, ow, facing.lightScreenX, useSpec, path
        )
        VolumetricShading.drawVolumetricCylinder(
            this, elbow, hand, 2.25f * u, 1.90f * u,
            bracerPal.primary, bracerPal.highlight, bracerPal.shadow, ow, facing.lightScreenX, useSpec, path
        )
        VolumetricShading.drawVolumetricEllipsoid(
            this, hand.x, hand.y, 2.05f * u, 1.85f * u,
            appearance.skinPrimary, appearance.skinHighlight, appearance.skinShadow, ow * 0.85f,
            facing.lightScreenX, facing.lightScreenY, useSpecular = false
        )

        if (appearance.weaponKind != WeaponVisualKind.NONE) {
            drawHeldWeapon3D(ox, oy, hand, u, ow, appearance, facing, bobUp, swingF, swingUp, atkEase, nowMs, useSpec, useRim, path)
        }
    }

    private fun DrawScope.drawHeldWeapon3D(
        ox: Float, oy: Float, hand: Offset, u: Float, ow: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        bobUp: Float, swingF: Float, swingUp: Float, atkEase: Float,
        nowMs: Long, useSpec: Boolean, useRim: Boolean, path: Path
    ) {
        val pal = appearance.weaponPalette
        // Project weapon tip in 3D space so blade/staff/bow rotates smoothly across all 360°!
        val reachF = swingF + when (appearance.weaponKind) {
            WeaponVisualKind.CLAYMORE, WeaponVisualKind.GLAIVE -> 12.5f + atkEase * 7.5f
            WeaponVisualKind.STAFF -> 6.0f + atkEase * 3.5f
            WeaponVisualKind.BOW, WeaponVisualKind.ARBALEST -> 7.5f
            WeaponVisualKind.DAGGER -> 6.8f + atkEase * 4.2f
            else -> 10.0f + atkEase * 6.5f
        }
        val tipUp = swingUp + when (appearance.weaponKind) {
            WeaponVisualKind.STAFF -> 22.0f + atkEase * 3.0f
            WeaponVisualKind.BOW, WeaponVisualKind.ARBALEST -> 6.5f
            WeaponVisualKind.CLAYMORE, WeaponVisualKind.GLAIVE -> 15.5f - atkEase * 12.5f
            WeaponVisualKind.WAND -> 9.5f + atkEase * 3.0f
            else -> 12.5f - atkEase * 11.0f
        }
        val tipR = +8.8f - atkEase * 4.5f
        val tip = facing.project(ox, oy, u, tipR, reachF, tipUp)
        val pommel = facing.project(ox, oy, u, +7.2f, swingF - 2.6f, swingUp - 3.2f)

        when (appearance.weaponKind) {
            WeaponVisualKind.NONE -> Unit
            WeaponVisualKind.DAGGER, WeaponVisualKind.SWORD, WeaponVisualKind.FALCHION,
            WeaponVisualKind.RAPIER, WeaponVisualKind.CLAYMORE -> {
                val bladeRadius = when (appearance.weaponKind) {
                    WeaponVisualKind.CLAYMORE -> 2.4f * u
                    WeaponVisualKind.FALCHION -> 2.2f * u
                    WeaponVisualKind.RAPIER -> 1.15f * u
                    WeaponVisualKind.DAGGER -> 1.45f * u
                    else -> 1.85f * u
                }
                // Hilt & Pommel
                VolumetricShading.drawVolumetricCylinder(
                    this, pommel, hand, 1.1f * u, 1.1f * u,
                    BELT_LEATHER, Color(0xFF7D5029), Color(0xFF2B180B), ow * 0.7f, facing.lightScreenX, false, path
                )
                drawCircle(pal.accent, 1.4f * u, pommel)
                // Volumetric metallic blade with specular reflection highlight
                if (useRim && pal.glow != null) {
                    drawLine(
                        color = pal.glow.copy(alpha = 0.36f),
                        start = hand,
                        end = tip,
                        strokeWidth = bladeRadius * 3.1f,
                        cap = StrokeCap.Round
                    )
                }
                VolumetricShading.drawVolumetricCylinder(
                    this, hand, tip, bladeRadius, bladeRadius * 0.35f,
                    pal.primary, pal.highlight, pal.shadow, ow, facing.lightScreenX, useHighlightStrip = true, path = path
                )
                if (useSpec) {
                    // Crisp beveled metallic fuller & razor reflection gleam along blade
                    drawLine(
                        (pal.glow ?: pal.shadow).copy(alpha = if (pal.glow != null) 0.72f else 0.60f),
                        Offset(hand.x + (tip.x - hand.x) * 0.14f, hand.y + (tip.y - hand.y) * 0.14f),
                        Offset(hand.x + (tip.x - hand.x) * 0.78f, hand.y + (tip.y - hand.y) * 0.78f),
                        strokeWidth = (bladeRadius * 0.30f).coerceAtLeast(0.85f),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        SPECULAR_WHITE.copy(alpha = 0.82f),
                        Offset(hand.x + (tip.x - hand.x) * 0.16f - 0.5f * u, hand.y + (tip.y - hand.y) * 0.16f - 0.5f * u),
                        Offset(hand.x + (tip.x - hand.x) * 0.88f - 0.3f * u, hand.y + (tip.y - hand.y) * 0.88f - 0.3f * u),
                        strokeWidth = (bladeRadius * 0.42f).coerceAtLeast(1.0f),
                        cap = StrokeCap.Round
                    )
                }
                // Sculpted metallic crossguard perpendicular to blade
                val bDx = tip.x - hand.x
                val bDy = tip.y - hand.y
                val bLen = hypot(bDx, bDy).coerceAtLeast(1f)
                val gx = (-bDy / bLen) * (if (appearance.weaponKind == WeaponVisualKind.CLAYMORE) 4.8f else 3.4f) * u
                val gy = (bDx / bLen) * (if (appearance.weaponKind == WeaponVisualKind.CLAYMORE) 4.8f else 3.4f) * u
                VolumetricShading.drawVolumetricCylinder(
                    this, Offset(hand.x - gx, hand.y - gy), Offset(hand.x + gx, hand.y + gy),
                    1.25f * u, 1.25f * u, pal.accent, SPECULAR_WHITE, Color(0xFF78531C), ow * 0.75f, facing.lightScreenX, false, path
                )
            }
            WeaponVisualKind.AXE, WeaponVisualKind.GLAIVE -> {
                val butt = facing.project(ox, oy, u, +7.0f, swingF - 5.2f, swingUp - 8.5f)
                val haftMain = if (pal.glow != null) pal.shadow else MaterialPalettes.OAK_WOOD.primary
                val haftHi = if (pal.glow != null) pal.primary else MaterialPalettes.OAK_WOOD.highlight
                VolumetricShading.drawVolumetricCylinder(
                    this, butt, tip, 1.35f * u, 1.25f * u,
                    haftMain, haftHi, MaterialPalettes.OAK_WOOD.shadow,
                    ow * 0.85f, facing.lightScreenX, false, path
                )
                // Curved metallic axehead with gleaming cutting edge (+ twin back-bit for high-tier greataxes)
                val headC = Offset(hand.x + (tip.x - hand.x) * 0.85f, hand.y + (tip.y - hand.y) * 0.85f)
                val bladeOff = facing.project(ox, oy, u, tipR, reachF + 3.4f, tipUp - 1.4f)
                val frontBit = Offset((headC.x + bladeOff.x) * 0.5f, (headC.y + bladeOff.y) * 0.5f)
                if (useRim && pal.glow != null) {
                    drawCircle(
                        Brush.radialGradient(listOf(pal.glow.copy(alpha = 0.42f), Color.Transparent), center = headC, radius = 7.4f * u),
                        radius = 7.4f * u, center = headC
                    )
                }
                if (appearance.isTwoHanded || pal.glow != null) {
                    val backBitOff = facing.project(ox, oy, u, tipR, reachF - 2.6f, tipUp + 0.6f)
                    VolumetricShading.drawVolumetricEllipsoid(
                        this, (headC.x + backBitOff.x) * 0.5f, (headC.y + backBitOff.y) * 0.5f,
                        3.1f * u, 3.8f * u, pal.shadow, pal.primary, INK, ow * 0.9f,
                        facing.lightScreenX, facing.lightScreenY, useSpecular = useSpec, useRim = useRim
                    )
                }
                VolumetricShading.drawVolumetricEllipsoid(
                    this, frontBit.x, frontBit.y,
                    4.0f * u, 4.9f * u, pal.primary, pal.highlight, pal.shadow, ow,
                    facing.lightScreenX, facing.lightScreenY, useSpecular = useSpec, useRim = useRim
                )
                if (pal.glow != null) {
                    drawCircle(pal.accent, 1.35f * u, headC)
                }
            }
            WeaponVisualKind.CLUB -> {
                if (pal.glow != null) {
                    // Tier 4/5 Flanged Warmaul / Worldbreaker with glowing runed hammerhead
                    val butt = facing.project(ox, oy, u, +7.0f, swingF - 4.8f, swingUp - 7.5f)
                    VolumetricShading.drawVolumetricCylinder(
                        this, butt, tip, 1.35f * u, 1.35f * u,
                        pal.shadow, pal.primary, INK, ow * 0.85f, facing.lightScreenX, false, path
                    )
                    if (useRim) {
                        drawCircle(
                            Brush.radialGradient(listOf(pal.glow.copy(alpha = 0.45f), Color.Transparent), center = tip, radius = 8.0f * u),
                            radius = 8.0f * u, center = tip
                        )
                    }
                    val headA = facing.project(ox, oy, u, tipR, reachF - 2.6f, tipUp - 1.6f)
                    val headB = facing.project(ox, oy, u, tipR, reachF + 3.8f, tipUp + 1.6f)
                    VolumetricShading.drawVolumetricCylinder(
                        this, headA, headB, 3.4f * u, 3.4f * u,
                        pal.primary, pal.highlight, pal.shadow, ow, facing.lightScreenX, true, path
                    )
                    drawCircle(pal.glow, 1.7f * u, tip)
                    drawCircle(SPECULAR_WHITE, 0.75f * u, tip)
                } else {
                    VolumetricShading.drawVolumetricCylinder(
                        this, pommel, tip, 1.3f * u, 2.9f * u,
                        pal.primary, pal.highlight, pal.shadow, ow, facing.lightScreenX, useSpec, path
                    )
                    drawCircle(pal.accent, 1.2f * u, tip)
                }
            }
            WeaponVisualKind.JAVELIN -> {
                val butt = facing.project(ox, oy, u, +7.0f, swingF - 7.5f, swingUp - 7.0f)
                VolumetricShading.drawVolumetricCylinder(
                    this, butt, tip, 1.1f * u, 1.1f * u,
                    MaterialPalettes.OAK_WOOD.primary, MaterialPalettes.OAK_WOOD.highlight, MaterialPalettes.OAK_WOOD.shadow,
                    ow * 0.8f, facing.lightScreenX, false, path
                )
                val spearPt = facing.project(ox, oy, u, tipR, reachF + 4.5f, tipUp + 3.2f)
                VolumetricShading.drawVolumetricCylinder(
                    this, tip, spearPt, 1.8f * u, 0.4f * u,
                    pal.primary, SPECULAR_WHITE, pal.shadow, ow * 0.8f, facing.lightScreenX, true, path
                )
            }
            WeaponVisualKind.BOW, WeaponVisualKind.ARBALEST -> {
                val limbTop = facing.project(ox, oy, u, +7.6f, swingF + 3.8f, swingUp + 12.2f)
                val limbBot = facing.project(ox, oy, u, +7.6f, swingF + 3.8f, swingUp - 11.2f)
                val bowBelly = facing.project(ox, oy, u, +7.6f, swingF + 9.2f, swingUp + 0.5f)
                val pullBack = facing.project(ox, oy, u, +6.2f, swingF + 1.2f - atkEase * 4.5f, swingUp + 0.8f)

                // Taut bowstring pulling back during attack draw (glows for Vesperite/Cinderite bows!)
                val stringColor = pal.glow?.copy(alpha = 0.92f) ?: Color(0xFFE8DFCE)
                drawLine(stringColor, limbTop, pullBack, strokeWidth = (if (pal.glow != null) 1.25f else 1.0f) * u)
                drawLine(stringColor, pullBack, limbBot, strokeWidth = (if (pal.glow != null) 1.25f else 1.0f) * u)

                // Curved volumetric wooden / Vesperite / Cinderite bow limbs
                path.reset()
                path.moveTo(limbTop.x, limbTop.y)
                path.quadraticTo(bowBelly.x, bowBelly.y, limbBot.x, limbBot.y)
                if (useRim && pal.glow != null) {
                    drawPath(path, pal.glow.copy(alpha = 0.34f), style = Stroke(width = 6.2f * u, cap = StrokeCap.Round))
                }
                drawPath(path, INK, style = Stroke(width = 3.8f * u, cap = StrokeCap.Round))
                drawPath(path, pal.primary, style = Stroke(width = 2.3f * u, cap = StrokeCap.Round))
                if (useSpec) {
                    drawPath(path, pal.highlight.copy(alpha = 0.78f), style = Stroke(width = 1.0f * u, cap = StrokeCap.Round))
                }
                if (pal.glow != null) {
                    drawCircle(pal.accent, 1.3f * u, limbTop)
                    drawCircle(pal.accent, 1.3f * u, limbBot)
                }

                // Nocked arrow / quarrel pointing forward
                val arrowTip = facing.project(ox, oy, u, +7.6f, swingF + 13.5f, swingUp + 0.8f)
                drawLine(Color(0xFFD6B485), pullBack, arrowTip, strokeWidth = 1.3f * u, cap = StrokeCap.Round)
                drawCircle(pal.glow ?: SPECULAR_WHITE, 1.25f * u, arrowTip)
            }
            WeaponVisualKind.WAND, WeaponVisualKind.STAFF -> {
                val staffBase = if (appearance.weaponKind == WeaponVisualKind.STAFF) {
                    facing.project(ox, oy, u, +7.4f, swingF - 0.5f, 1.5f)
                } else pommel
                VolumetricShading.drawVolumetricCylinder(
                    this, staffBase, tip, 1.35f * u, 1.25f * u,
                    pal.shadow, pal.primary, VolumetricShading.coolShadow(pal.shadow, 0.4f), ow * 0.85f, facing.lightScreenX, useSpec, path
                )
                // Glowing crystal / arcane tip with bloom & sculpted metallic prongs for high-tier staves
                val glowC = pal.glow ?: pal.accent
                val pulse = 1f + 0.14f * sin(nowMs * 0.008f) + atkEase * 0.35f
                if (useRim) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(glowC.copy(alpha = 0.62f), Color.Transparent),
                            center = tip, radius = 9.0f * u * pulse
                        ),
                        radius = 9.0f * u * pulse, center = tip
                    )
                }
                if (appearance.weaponKind == WeaponVisualKind.STAFF && pal.glow != null) {
                    val prongL = Offset(tip.x - 3.6f * u, tip.y - 2.6f * u)
                    val prongR = Offset(tip.x + 3.6f * u, tip.y - 2.6f * u)
                    val collar = Offset(tip.x, tip.y + 2.8f * u)
                    drawLine(pal.accent, collar, prongL, strokeWidth = 1.4f * u, cap = StrokeCap.Round)
                    drawLine(pal.accent, collar, prongR, strokeWidth = 1.4f * u, cap = StrokeCap.Round)
                }
                VolumetricShading.drawVolumetricEllipsoid(
                    this, tip.x, tip.y, 2.9f * u * pulse, 3.3f * u * pulse,
                    glowC, Color.White, pal.primary, ow * 0.8f,
                    facing.lightScreenX, facing.lightScreenY, useSpecular = true, useRim = useRim
                )
            }
        }
    }

    private fun DrawScope.drawCombatAttackTrail3D(
        ox: Float, oy: Float, u: Float,
        appearance: PaperDollAppearance,
        facing: ResolvedScreenFacing,
        bobUp: Float,
        attackProgress: Float,
        quality: GraphicsQuality
    ) {
        val alpha = (sin(attackProgress * PI.toFloat()) * (if (quality == GraphicsQuality.HD) 0.86f else 0.65f)).coerceIn(0f, 0.9f)
        val arcCenter = facing.project(ox, oy, u, 2.0f, 7.5f, 22.0f + bobUp)
        val trailColor = appearance.weaponPalette.glow ?: when (appearance.weaponKind) {
            WeaponVisualKind.WAND, WeaponVisualKind.STAFF -> Color(0xFF6CE8FF)
            WeaponVisualKind.BOW, WeaponVisualKind.ARBALEST -> Color(0xFFFFE69A)
            else -> Color(0xFFFFF0C2)
        }
        val dirAngleDeg = (atan2(facing.forwardDepth.toDouble() * 0.5, facing.forwardScreenX.toDouble()) * 180.0 / PI).toFloat()
        val r = 11.5f * u
        drawArc(
            color = trailColor.copy(alpha = alpha),
            startAngle = dirAngleDeg - 68f,
            sweepAngle = 136f * attackProgress.coerceIn(0.25f, 1f),
            useCenter = false,
            topLeft = Offset(arcCenter.x - r, arcCenter.y - r * 0.72f),
            size = Size(r * 2f, r * 1.44f),
            style = Stroke(width = (if (quality == GraphicsQuality.HD) 3.4f else 2.4f) * u, cap = StrokeCap.Round)
        )
    }

    // -------------------------------------------------------------------------
    // 8. Thematic 2.5D/3D Volumetric NPCs (Brother Alden, The Oracle, Alaric)
    // -------------------------------------------------------------------------
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
        val ow = (u * 1.38f).coerceIn(1.5f, 3.4f)
        val useSpec = quality != GraphicsQuality.LOW
        val useRim = quality == GraphicsQuality.HIGH || quality == GraphicsQuality.HD
        val bobUp = if (role == NpcRole.ORACLE) {
            3.2f + sin(nowMs * 0.0032f) * 2.2f
        } else {
            sin(nowMs * 0.0028f) * 0.55f
        }

        when (role) {
            NpcRole.TEMPLE_PRIEST -> {
                // Brother Alden: Crimson & gold trimmed clerical vestments, silver beard, cross-staff & gilded tome
                val aldenAppearance = PaperDollAppearance(
                    vocation = Vocation.MAGE,
                    tunicPalette = MaterialPalette(Color(0xFF9C222B), Color(0xFFD6454F), Color(0xFF541016), Color(0xFFF2C95E)),
                    hairPalette = MaterialPalette(Color(0xFFD9D8D4), Color(0xFFFFFFFF), Color(0xFF8C8C88)),
                    weaponKind = WeaponVisualKind.STAFF,
                    weaponPalette = MaterialPalette(Color(0xFFD4A23E), Color(0xFFFFE594), Color(0xFF7A5616), Color(0xFFFFE88A), Color(0xFFFFDF7A)),
                    offhandKind = OffhandVisualKind.NONE,
                    armorKind = ArmorVisualKind.ROBE,
                    armorPalette = MaterialPalette(Color(0xFF9C222B), Color(0xFFD6454F), Color(0xFF541016), Color(0xFFF2C95E)),
                    helmetKind = HelmetVisualKind.NONE,
                    bootsKind = BootsVisualKind.DEFAULT
                )
                drawPlayer(this, cx, feetY, heightPx, aldenAppearance, facing, 0f, 0f, nowMs, quality, path)
                // Clerical gilded scripture tome in off-hand & silver beard when front-facing
                val tomePos = facing.project(cx, feetY, u, -7.8f, 4.6f, 20.5f + bobUp)
                VolumetricShading.drawVolumetricEllipsoid(
                    this, tomePos.x, tomePos.y, 3.3f * u, 4.1f * u,
                    Color(0xFF5B311B), Color(0xFF8F5230), Color(0xFF2B150A), ow * 0.85f,
                    facing.lightScreenX, facing.lightScreenY, useSpecular = false
                )
                drawLine(Color(0xFFF2C95E), Offset(tomePos.x, tomePos.y - 2.4f * u), Offset(tomePos.x, tomePos.y + 2.4f * u), strokeWidth = 1.2f * u)
                if (!facing.isBackFacing) {
                    val beard = facing.project(cx, feetY, u, 0f, 5.4f, 32.0f + bobUp)
                    VolumetricShading.drawVolumetricEllipsoid(
                        this, beard.x, beard.y, 3.6f * u, 3.2f * u,
                        Color(0xFFE8E6E0), Color.White, Color(0xFF9A9890), ow * 0.75f,
                        facing.lightScreenX, facing.lightScreenY, useSpecular = false
                    )
                }
            }
            NpcRole.ORACLE -> {
                // The Oracle: Luminous white-and-gold robes, radiant halo & aura rings
                val auraCenter = facing.project(cx, feetY, u, 0f, 0f, 22.0f + bobUp)
                if (useSpec) {
                    val pulse = 0.28f + 0.14f * sin(nowMs * 0.004f)
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0xFFFFE57F).copy(alpha = pulse), Color.Transparent),
                            center = auraCenter, radius = 22f * u
                        ),
                        radius = 22f * u, center = auraCenter
                    )
                }
                val oracleAppearance = PaperDollAppearance(
                    vocation = Vocation.MAGE,
                    tunicPalette = MaterialPalette(Color(0xFFF2ECE0), Color.White, Color(0xFFB5A992), Color(0xFFF5C94C)),
                    hairPalette = MaterialPalette(Color(0xFFF5D87A), Color.White, Color(0xFFB89232)),
                    weaponKind = WeaponVisualKind.STAFF,
                    weaponPalette = MaterialPalette(Color(0xFFF5C94C), Color.White, Color(0xFF9E721E), Color(0xFFFFF0A8), Color(0xFFFFE57F)),
                    offhandKind = OffhandVisualKind.ARCANE_ORB,
                    offhandPalette = MaterialPalette(Color(0xFFFFE07D), Color.White, Color(0xFFB8861E), Color(0xFFFFF4B8), Color(0xFFFFE57F)),
                    armorKind = ArmorVisualKind.ROBE,
                    armorPalette = MaterialPalette(Color(0xFFF2ECE0), Color.White, Color(0xFFB5A992), Color(0xFFF5C94C)),
                    helmetKind = HelmetVisualKind.MAGE_HOOD,
                    helmetPalette = MaterialPalette(Color(0xFFF2ECE0), Color.White, Color(0xFFB5A992), Color(0xFFF5C94C))
                )
                drawPlayer(this, cx, feetY - bobUp * 0.6f * u, heightPx, oracleAppearance, facing, 0f, 0f, nowMs, quality, path)
                // Floating foreshortened 3D golden halo crown above hood
                val halo = facing.project(cx, feetY - bobUp * 0.6f * u, u, 0f, 0f, 46.8f)
                drawOval(
                    color = Color(0xFFFFDF6B),
                    topLeft = Offset(halo.x - 8.2f * u, halo.y - 2.6f * u),
                    size = Size(16.4f * u, 5.2f * u),
                    style = Stroke(width = 2.0f * u)
                )
                if (useRim) {
                    drawOval(
                        color = Color(0x88FFF5B8),
                        topLeft = Offset(halo.x - 9.4f * u, halo.y - 3.2f * u),
                        size = Size(18.8f * u, 6.4f * u),
                        style = Stroke(width = 1.0f * u)
                    )
                }
            }
            NpcRole.SHOP_ALCHEMIST -> {
                // Alaric: Merchant emerald tunic, stitched leather apron, coin pouch & glowing emerald alchemical flask
                val alaricAppearance = PaperDollAppearance(
                    vocation = Vocation.ADVENTURER,
                    tunicPalette = MaterialPalette(Color(0xFF2F6E56), Color(0xFF52A382), Color(0xFF194032), Color(0xFFE8BC58)),
                    hairPalette = MaterialPalette(Color(0xFF6B4226), Color(0xFF9E6841), Color(0xFF3A2112)),
                    weaponKind = WeaponVisualKind.NONE,
                    offhandKind = OffhandVisualKind.NONE,
                    armorKind = ArmorVisualKind.LEATHER,
                    armorPalette = MaterialPalette(Color(0xFF7D522D), Color(0xFFB07846), Color(0xFF472C16), Color(0xFFE8BC58)),
                    helmetKind = HelmetVisualKind.LEATHER_CAP,
                    helmetPalette = MaterialPalette(Color(0xFF2F6E56), Color(0xFF52A382), Color(0xFF194032), Color(0xFFE8BC58)),
                    bootsKind = BootsVisualKind.TRAVELER
                )
                drawPlayer(this, cx, feetY, heightPx, alaricAppearance, facing, 0f, 0f, nowMs, quality, path)
                // Glowing emerald alchemical flask held in right hand
                val flaskPos = facing.project(cx, feetY, u, +8.2f, 4.6f, 20.5f + bobUp)
                if (useRim) {
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0x664CE678), Color.Transparent), center = flaskPos, radius = 6.5f * u),
                        radius = 6.5f * u, center = flaskPos
                    )
                }
                VolumetricShading.drawVolumetricEllipsoid(
                    this, flaskPos.x, flaskPos.y, 3.0f * u, 3.4f * u,
                    Color(0xFF39C968), Color(0xFFA8FFBD), Color(0xFF196634), ow * 0.85f,
                    facing.lightScreenX, facing.lightScreenY, useSpecular = true
                )
                // Merchant gold coin pouch at hip
                val pouch = facing.project(cx, feetY, u, -5.2f, 2.2f, 14.5f + bobUp)
                VolumetricShading.drawVolumetricEllipsoid(
                    this, pouch.x, pouch.y, 2.2f * u, 2.4f * u,
                    Color(0xFFD9A338), SPECULAR_WHITE, Color(0xFF7A5314), ow * 0.7f,
                    facing.lightScreenX, facing.lightScreenY, useSpecular = false
                )
            }
            NpcRole.HARBOR_CAPTAIN -> {
                // Harbor Ferry Captain: Deep maritime navy coat, gold brass trim, sea boots & brass spyglass
                val captainAppearance = PaperDollAppearance(
                    vocation = Vocation.KNIGHT,
                    tunicPalette = MaterialPalette(Color(0xFF1E3A63), Color(0xFF365E99), Color(0xFF0E1F38), Color(0xFFF0C451)),
                    hairPalette = MaterialPalette(Color(0xFF4A3525), Color(0xFF78583E), Color(0xFF261A10)),
                    weaponKind = WeaponVisualKind.NONE,
                    offhandKind = OffhandVisualKind.NONE,
                    armorKind = ArmorVisualKind.ROBE,
                    armorPalette = MaterialPalette(Color(0xFF1E3A63), Color(0xFF365E99), Color(0xFF0E1F38), Color(0xFFF0C451)),
                    helmetKind = HelmetVisualKind.LEATHER_CAP,
                    helmetPalette = MaterialPalette(Color(0xFF152947), Color(0xFF2D4F82), Color(0xFF0B1629), Color(0xFFF0C451)),
                    bootsKind = BootsVisualKind.TRAVELER
                )
                drawPlayer(this, cx, feetY, heightPx, captainAppearance, facing, 0f, 0f, nowMs, quality, path)
                // Brass spyglass held in right hand
                val glassPos = facing.project(cx, feetY, u, +8.0f, 4.4f, 21.0f + bobUp)
                VolumetricShading.drawVolumetricEllipsoid(
                    this, glassPos.x, glassPos.y, 3.6f * u, 1.8f * u,
                    Color(0xFFD9A338), SPECULAR_WHITE, Color(0xFF7A5314), ow * 0.75f,
                    facing.lightScreenX, facing.lightScreenY, useSpecular = true
                )
            }
            NpcRole.REAPER_KEEPER -> {
                // Mortis, Keeper of Reapers: Deep obsidian-violet hooded robes, silver-amethyst trim & glowing soul lantern
                val reaperAppearance = PaperDollAppearance(
                    vocation = Vocation.MAGE,
                    tunicPalette = MaterialPalette(Color(0xFF1F162E), Color(0xFF3E2D5C), Color(0xFF0E0917), Color(0xFF9B6BFF)),
                    hairPalette = MaterialPalette(Color(0xFF181420), Color(0xFF2E263D), Color(0xFF09070D)),
                    weaponKind = WeaponVisualKind.STAFF,
                    weaponPalette = MaterialPalette(Color(0xFF2B2438), Color(0xFF6B5B8A), Color(0xFF120E1A), Color(0xFFC39BFF)),
                    offhandKind = OffhandVisualKind.NONE,
                    armorKind = ArmorVisualKind.ROBE,
                    armorPalette = MaterialPalette(Color(0xFF1F162E), Color(0xFF3E2D5C), Color(0xFF0E0917), Color(0xFF9B6BFF)),
                    helmetKind = HelmetVisualKind.MAGE_HOOD,
                    helmetPalette = MaterialPalette(Color(0xFF1A1226), Color(0xFF362752), Color(0xFF0B0712), Color(0xFF9B6BFF)),
                    bootsKind = BootsVisualKind.TRAVELER
                )
                drawPlayer(this, cx, feetY, heightPx, reaperAppearance, facing, 0f, 0f, nowMs, quality, path)
                // Glowing spectral soul lantern orbiting left hand
                val pulse = 0.85f + 0.15f * sin(nowMs * 0.0045f)
                val lanternPos = facing.project(cx, feetY, u, -8.4f, 3.6f, 22.5f + bobUp + sin(nowMs * 0.003f) * 1.1f)
                drawCircle(Color(0xFF9B6BFF).copy(alpha = 0.28f * pulse), radius = 6.2f * u, center = lanternPos)
                drawCircle(Color(0xFFD6B8FF).copy(alpha = 0.92f), radius = 2.6f * u, center = lanternPos)
                drawCircle(Color.White.copy(alpha = 0.95f), radius = 1.2f * u, center = lanternPos)
            }
        }
    }
}
