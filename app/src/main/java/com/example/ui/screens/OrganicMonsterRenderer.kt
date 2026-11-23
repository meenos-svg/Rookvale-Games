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
import com.example.game.model.GraphicsQuality
import com.example.game.model.MonsterType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Volumetric 2.5D/3D 8-directional isometric renderer for all 17 Rookvale [MonsterType] species.
 * Projects entity-local 3D skeletons `(localR, localF, localUp)` using `entityFacing - cameraYaw`,
 * dynamically depth-sorts limbs, wings, tails, weapons, and heads across all 8 octants, and shades
 * every body volume with warm top-left sunlight highlights, cool twilight core shadows, subtle rim
 * lights (High/HD), and crisp `#14100E` dark ink outlines.
 */
object OrganicMonsterRenderer {

    private val INK = VolumetricShading.INK

    fun heightScaleFor(type: MonsterType): Float = when (type) {
        MonsterType.WILD_RABBIT -> 0.72f
        MonsterType.CAVE_RAT -> 0.74f
        MonsterType.YOUNG_SNAKE -> 0.78f
        MonsterType.POISON_SPIDER -> 0.88f
        MonsterType.GOBLIN_SCOUT -> 0.90f
        MonsterType.CRAG_WOLF -> 0.94f
        MonsterType.SWAMP_TROLL, MonsterType.MINOTAUR, MonsterType.ORC_WARRIOR -> 1.06f
        MonsterType.CYCLOPS -> 1.18f
        MonsterType.DRAGON_HATCHLING -> 1.10f
        MonsterType.DEMON_LORD -> 1.32f
        MonsterType.ELDER_DRAGON -> 1.36f
        else -> 1.0f
    }

    /**
     * Pure deterministic 3D leg draw-order helper for quadrupeds (back-to-front by camera depth).
     * Returns leg IDs from `"BL"`, `"BR"`, `"FL"`, `"FR"`.
     */
    fun computeQuadrupedLegOrder(facing: ResolvedScreenFacing): List<String> {
        val entries = listOf(
            "BL" to facing.depthOf(-4.2f, -6.5f),
            "BR" to facing.depthOf(+4.2f, -6.5f),
            "FL" to facing.depthOf(-4.2f, +5.5f),
            "FR" to facing.depthOf(+4.2f, +5.5f)
        )
        return entries.sortedBy { it.second }.map { it.first }
    }

    fun drawMonster(
        scope: DrawScope,
        cx: Float,
        feetY: Float,
        heightPx: Float,
        type: MonsterType,
        facing: ResolvedScreenFacing,
        walkPhase: Float,
        lungeProgress: Float, // 0f = idle, (0f..1f) = forward 3D attack lunge
        animSeed: Int,
        nowMs: Long,
        quality: GraphicsQuality,
        path: Path
    ) = with(scope) {
        val u = heightPx / 46f
        val ow = (u * 1.10f).coerceIn(1.1f, 2.7f)
        val useSpec = quality != GraphicsQuality.LOW
        val useRim = quality == GraphicsQuality.HIGH || quality == GraphicsQuality.HD
        val seedRad = (animSeed % 360) * 0.01745f
        val timeSec = nowMs / 1000f
        val breath = sin(timeSec * 3.1f + seedRad) * 0.72f
        val isMoving = abs(walkPhase) > 0.01f
        val stride = if (isMoving) sin(walkPhase) else 0f
        val bobUp = if (isMoving) abs(stride) * 1.5f else 0f
        val lunge = if (lungeProgress > 0.001f && lungeProgress < 0.999f) {
            sin(lungeProgress * PI.toFloat())
        } else 0f

        val ox = cx + facing.forwardScreenX * lunge * u * 6.0f
        val oy = feetY + facing.forwardDepth * lunge * u * 2.8f

        when (type) {
            MonsterType.WILD_RABBIT -> drawQuadruped(
                ox, oy, u, ow, facing, stride, bobUp, breath, lunge,
                style = QuadrupedStyle.RABBIT, useSpec = useSpec, useRim = useRim, path = path
            )
            MonsterType.CAVE_RAT -> drawQuadruped(
                ox, oy, u, ow, facing, stride, bobUp, breath, lunge,
                style = QuadrupedStyle.RAT, useSpec = useSpec, useRim = useRim, path = path
            )
            MonsterType.CRAG_WOLF -> drawQuadruped(
                ox, oy, u, ow, facing, stride, bobUp, breath, lunge,
                style = QuadrupedStyle.WOLF, useSpec = useSpec, useRim = useRim, path = path
            )
            MonsterType.YOUNG_SNAKE -> drawYoungSnake(
                ox, oy, u, ow, facing, timeSec + seedRad, lunge, useSpec, useRim, path
            )
            MonsterType.POISON_SPIDER -> drawPoisonSpider(
                ox, oy, u, ow, facing, walkPhase, timeSec + seedRad, lunge, useSpec, useRim, path
            )
            MonsterType.DRAGON_HATCHLING -> drawDragon3D(
                ox, oy, u, ow, facing, stride, bobUp, timeSec + seedRad, lunge,
                isElder = false, useSpec = useSpec, useRim = useRim, quality = quality, path = path
            )
            MonsterType.ELDER_DRAGON -> drawDragon3D(
                ox, oy, u, ow, facing, stride, bobUp, timeSec + seedRad, lunge,
                isElder = true, useSpec = useSpec, useRim = useRim, quality = quality, path = path
            )
            MonsterType.SWAMP_TROLL -> drawBipedMonster(
                ox, oy, u, ow, facing, stride, bobUp, breath, lunge, timeSec + seedRad,
                species = BipedMonsterStyle.SWAMP_TROLL, useSpec = useSpec, useRim = useRim, quality = quality, path = path
            )
            MonsterType.SKELETON -> drawBipedMonster(
                ox, oy, u, ow, facing, stride, bobUp, breath, lunge, timeSec + seedRad,
                species = BipedMonsterStyle.SKELETON, useSpec = useSpec, useRim = useRim, quality = quality, path = path
            )
            MonsterType.GOBLIN_SCOUT -> drawBipedMonster(
                ox, oy, u, ow, facing, stride, bobUp, breath, lunge, timeSec + seedRad,
                species = BipedMonsterStyle.GOBLIN_SCOUT, useSpec = useSpec, useRim = useRim, quality = quality, path = path
            )
            MonsterType.MINOTAUR -> drawBipedMonster(
                ox, oy, u, ow, facing, stride, bobUp, breath, lunge, timeSec + seedRad,
                species = BipedMonsterStyle.MINOTAUR, useSpec = useSpec, useRim = useRim, quality = quality, path = path
            )
            MonsterType.ORC_WARRIOR -> drawBipedMonster(
                ox, oy, u, ow, facing, stride, bobUp, breath, lunge, timeSec + seedRad,
                species = BipedMonsterStyle.ORC_WARRIOR, useSpec = useSpec, useRim = useRim, quality = quality, path = path
            )
            MonsterType.GHOUL -> drawBipedMonster(
                ox, oy, u, ow, facing, stride, bobUp, breath, lunge, timeSec + seedRad,
                species = BipedMonsterStyle.GHOUL, useSpec = useSpec, useRim = useRim, quality = quality, path = path
            )
            MonsterType.FIRE_DEVIL -> drawBipedMonster(
                ox, oy, u, ow, facing, stride, bobUp, breath, lunge, timeSec + seedRad,
                species = BipedMonsterStyle.FIRE_DEVIL, useSpec = useSpec, useRim = useRim, quality = quality, path = path
            )
            MonsterType.CYCLOPS -> drawBipedMonster(
                ox, oy, u, ow, facing, stride, bobUp, breath, lunge, timeSec + seedRad,
                species = BipedMonsterStyle.CYCLOPS, useSpec = useSpec, useRim = useRim, quality = quality, path = path
            )
            MonsterType.NECROMANCER -> drawBipedMonster(
                ox, oy, u, ow, facing, stride, bobUp, breath, lunge, timeSec + seedRad,
                species = BipedMonsterStyle.NECROMANCER, useSpec = useSpec, useRim = useRim, quality = quality, path = path
            )
            MonsterType.DEMON_LORD -> drawBipedMonster(
                ox, oy, u, ow, facing, stride, bobUp, breath, lunge, timeSec + seedRad,
                species = BipedMonsterStyle.DEMON_LORD, useSpec = useSpec, useRim = useRim, quality = quality, path = path
            )
        }

        // Directional 3D attack slash arc on High/HD quality
        if (lunge > 0.20f && (quality == GraphicsQuality.HIGH || quality == GraphicsQuality.HD)) {
            val slashAlpha = (lunge * 0.76f).coerceIn(0f, 0.80f)
            val slashColor = when (type) {
                MonsterType.FIRE_DEVIL, MonsterType.DEMON_LORD, MonsterType.ELDER_DRAGON, MonsterType.DRAGON_HATCHLING -> Color(0xFFFF7A26)
                MonsterType.POISON_SPIDER, MonsterType.YOUNG_SNAKE, MonsterType.SWAMP_TROLL -> Color(0xFF7BFF33)
                MonsterType.NECROMANCER, MonsterType.GHOUL -> Color(0xFFC07BFF)
                else -> Color(0xFFFFE7B0)
            }
            val arcCenter = facing.project(ox, oy, u, 0f, 10.5f, 18f)
            val aimDeg = (atan2(facing.forwardDepth.toDouble() * 0.55, facing.forwardScreenX.toDouble()) * 180.0 / PI).toFloat()
            val arcR = u * 9.5f
            drawArc(
                color = slashColor.copy(alpha = slashAlpha),
                startAngle = aimDeg - 58f,
                sweepAngle = 116f,
                useCenter = false,
                topLeft = Offset(arcCenter.x - arcR, arcCenter.y - arcR * 0.78f),
                size = Size(arcR * 2f, arcR * 1.56f),
                style = Stroke(width = u * 2.4f, cap = StrokeCap.Round)
            )
        }
    }

    // -------------------------------------------------------------------------
    // 1. VOLUMETRIC 3D QUADRUPEDS (Wild Rabbit, Cave Rat, Crag Wolf)
    // -------------------------------------------------------------------------

    private enum class QuadrupedStyle { RABBIT, RAT, WOLF }

    private fun DrawScope.drawQuadruped(
        ox: Float,
        oy: Float,
        u: Float,
        ow: Float,
        facing: ResolvedScreenFacing,
        stride: Float,
        bobUp: Float,
        breath: Float,
        lunge: Float,
        style: QuadrupedStyle,
        useSpec: Boolean,
        useRim: Boolean,
        path: Path
    ) {
        val (bodyMain, bodyHi, bodySh, bellyColor, accentColor) = when (style) {
            QuadrupedStyle.RABBIT -> listOf(
                Color(0xFFC8A682), Color(0xFFEAD4B7), Color(0xFF836447), Color(0xFFF6EFE6), Color(0xFFE8A4A8)
            )
            QuadrupedStyle.RAT -> listOf(
                Color(0xFF6B625D), Color(0xFF948A84), Color(0xFF3E3734), Color(0xFF968780), Color(0xFFD68E92)
            )
            QuadrupedStyle.WOLF -> listOf(
                Color(0xFF6B7482), Color(0xFFA0ACBD), Color(0xFF3A414E), Color(0xFFC2C9D4), Color(0xFFFFB733)
            )
        }

        val hopLift = when (style) {
            QuadrupedStyle.RABBIT -> bobUp * 2.2f + lunge * 3.8f
            QuadrupedStyle.RAT -> bobUp * 0.9f + lunge * 1.6f
            QuadrupedStyle.WOLF -> bobUp * 1.3f + lunge * 2.4f
        }
        val bodyUp = when (style) {
            QuadrupedStyle.RABBIT -> 9.2f + hopLift + breath * 0.45f
            QuadrupedStyle.RAT -> 7.2f + hopLift + breath * 0.4f
            QuadrupedStyle.WOLF -> 13.8f + hopLift + breath * 0.55f
        }

        val legSpreadR = when (style) {
            QuadrupedStyle.RABBIT -> 3.8f
            QuadrupedStyle.RAT -> 3.6f
            QuadrupedStyle.WOLF -> 4.6f
        }
        val backF = when (style) {
            QuadrupedStyle.RABBIT -> -5.5f
            QuadrupedStyle.RAT -> -6.5f
            QuadrupedStyle.WOLF -> -7.5f
        }
        val frontF = when (style) {
            QuadrupedStyle.RABBIT -> +4.2f
            QuadrupedStyle.RAT -> +5.2f
            QuadrupedStyle.WOLF -> +6.8f
        }

        // 8 sortable 3D anatomical parts: 0=TAIL, 1=BL, 2=BR, 3=FL, 4=FR, 5=HAUNCH, 6=CHEST, 7=HEAD
        val order = IntArray(8) { it }
        val depths = floatArrayOf(
            facing.depthOf(0f, backF - 6.0f),                                   // 0 TAIL
            facing.depthOf(-legSpreadR, backF - stride * 2.5f) - 0.3f,          // 1 BL
            facing.depthOf(+legSpreadR, backF + stride * 2.5f) - 0.3f,          // 2 BR
            facing.depthOf(-legSpreadR, frontF + stride * 3.0f) - 0.2f,         // 3 FL
            facing.depthOf(+legSpreadR, frontF - stride * 3.0f) - 0.2f,         // 4 FR
            facing.depthOf(0f, backF * 0.55f),                                  // 5 HAUNCH
            facing.depthOf(0f, frontF * 0.55f),                                 // 6 CHEST
            facing.depthOf(0f, frontF + 4.8f) + 0.5f                            // 7 HEAD
        )
        sortIndices(order, depths)

        for (i in 0 until 8) {
            when (order[i]) {
                0 -> { // TAIL
                    val tailBase = facing.project(ox, oy, u, 0f, backF - 2.4f, bodyUp + 1.0f)
                    when (style) {
                        QuadrupedStyle.RABBIT -> {
                            val tailPos = facing.project(ox, oy, u, 0f, backF - 4.5f, bodyUp + 1.6f)
                            VolumetricShading.drawVolumetricEllipsoid(
                                this, tailPos.x, tailPos.y, u * 3.4f, u * 3.0f,
                                main = Color(0xFFF6F1E9), highlight = Color.White, shadow = Color(0xFFBEB5A8),
                                outlineW = ow * 0.85f, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
                                useSpecular = useSpec
                            )
                        }
                        QuadrupedStyle.RAT -> {
                            val wag = sin(stride * 2.2f + breath * 2f) * 3.2f
                            val mid = facing.project(ox, oy, u, wag, backF - 9.5f, 3.2f)
                            val tip = facing.project(ox, oy, u, -wag * 0.8f, backF - 15.5f, 4.6f)
                            VolumetricShading.drawVolumetricCylinder(
                                this, tailBase, mid, u * 1.35f, u * 0.9f,
                                main = accentColor, outlineW = ow * 0.72f, lightX = facing.lightScreenX, path = path
                            )
                            VolumetricShading.drawVolumetricCylinder(
                                this, mid, tip, u * 0.88f, u * 0.45f,
                                main = accentColor, outlineW = ow * 0.65f, lightX = facing.lightScreenX, path = path
                            )
                        }
                        QuadrupedStyle.WOLF -> {
                            val wag = sin(breath * 2.4f) * 2.6f
                            val mid = facing.project(ox, oy, u, wag, backF - 6.8f, bodyUp - 1.2f)
                            val tip = facing.project(ox, oy, u, wag * 1.4f, backF - 11.8f, bodyUp + 2.4f)
                            VolumetricShading.drawVolumetricCylinder(
                                this, tailBase, mid, u * 2.6f, u * 2.2f,
                                main = bodySh, highlight = bodyMain, shadow = VolumetricShading.coolShadow(bodySh, 0.4f),
                                outlineW = ow * 0.88f, lightX = facing.lightScreenX, path = path
                            )
                            VolumetricShading.drawVolumetricCylinder(
                                this, mid, tip, u * 2.1f, u * 1.1f,
                                main = bodyHi, highlight = Color(0xFFDDE4EE), shadow = bodySh,
                                outlineW = ow * 0.82f, lightX = facing.lightScreenX, path = path
                            )
                        }
                    }
                }
                1, 2, 3, 4 -> { // 4 ARTICULATED LEGS
                    val isBack = order[i] == 1 || order[i] == 2
                    val isRight = order[i] == 2 || order[i] == 4
                    val rSide = if (isRight) legSpreadR else -legSpreadR
                    val fBase = if (isBack) backF else frontF
                    val legPhase = if ((isBack && isRight) || (!isBack && !isRight)) stride else -stride
                    val stepF = fBase + legPhase * (if (style == QuadrupedStyle.WOLF) 4.4f else 2.8f)
                    val liftUp = (if (legPhase > 0f) legPhase * 2.2f else 0f) + if (style == QuadrupedStyle.RABBIT) hopLift * 0.35f else 0f
                    val hip = facing.project(ox, oy, u, rSide * 0.82f, fBase, bodyUp - 1.5f)
                    val knee = facing.project(ox, oy, u, rSide * 0.95f, (fBase + stepF) * 0.5f + if (isBack) -1.2f else 0.8f, bodyUp * 0.46f + liftUp * 0.6f)
                    val paw = facing.project(ox, oy, u, rSide, stepF, 1.0f + liftUp)

                    if (liftUp < 1.2f) {
                        VolumetricShading.drawFootContactDot(this, paw, u * 2.2f, u * 1.2f)
                    }
                    val legMain = if (style == QuadrupedStyle.RAT) accentColor else bodyMain
                    val legR = when (style) {
                        QuadrupedStyle.RABBIT -> if (isBack) u * 2.6f else u * 1.8f
                        QuadrupedStyle.RAT -> u * 1.45f
                        QuadrupedStyle.WOLF -> if (isBack) u * 2.5f else u * 2.2f
                    }
                    VolumetricShading.drawVolumetricCylinder(
                        this, hip, knee, legR, legR * 0.78f,
                        main = bodyMain, highlight = bodyHi, shadow = bodySh,
                        outlineW = ow * 0.85f, lightX = facing.lightScreenX, path = path
                    )
                    VolumetricShading.drawVolumetricCylinder(
                        this, knee, paw, legR * 0.76f, legR * 0.68f,
                        main = legMain, highlight = if (style == QuadrupedStyle.RAT) accentColor else bodyHi,
                        shadow = bodySh, outlineW = ow * 0.80f, lightX = facing.lightScreenX, path = path
                    )
                    VolumetricShading.drawVolumetricEllipsoid(
                        this, paw.x, paw.y, legR * 0.95f, legR * 0.62f,
                        main = if (style == QuadrupedStyle.WOLF) bodySh else legMain,
                        outlineW = ow * 0.75f, lightX = facing.lightScreenX, lightY = facing.lightScreenY, useSpecular = false
                    )
                }
                5 -> { // HAUNCH / REAR TORSO
                    val haunch = facing.project(ox, oy, u, 0f, backF * 0.58f, bodyUp + 0.6f)
                    val rx = when (style) {
                        QuadrupedStyle.RABBIT -> u * (6.4f + abs(facing.forwardScreenX) * 2.0f)
                        QuadrupedStyle.RAT -> u * (6.0f + abs(facing.forwardScreenX) * 2.4f)
                        QuadrupedStyle.WOLF -> u * (6.8f + abs(facing.forwardScreenX) * 3.8f)
                    }
                    val ry = when (style) {
                        QuadrupedStyle.RABBIT -> u * 5.8f
                        QuadrupedStyle.RAT -> u * 4.8f
                        QuadrupedStyle.WOLF -> u * 5.8f
                    }
                    VolumetricShading.drawVolumetricEllipsoid(
                        this, haunch.x, haunch.y, rx, ry,
                        main = bodyMain, highlight = bodyHi, shadow = bodySh,
                        outlineW = ow, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
                        useSpecular = useSpec, useRim = useRim
                    )
                }
                6 -> { // CHEST / SHOULDERS / MANE
                    val chest = facing.project(ox, oy, u, 0f, frontF * 0.58f, bodyUp + (if (style == QuadrupedStyle.WOLF) 1.6f else 0.8f))
                    val rx = when (style) {
                        QuadrupedStyle.RABBIT -> u * (5.6f + abs(facing.forwardScreenX) * 1.4f)
                        QuadrupedStyle.RAT -> u * (5.2f + abs(facing.forwardScreenX) * 1.8f)
                        QuadrupedStyle.WOLF -> u * (7.2f + abs(facing.forwardScreenX) * 2.8f)
                    }
                    val ry = when (style) {
                        QuadrupedStyle.RABBIT -> u * 5.2f
                        QuadrupedStyle.RAT -> u * 4.5f
                        QuadrupedStyle.WOLF -> u * 6.4f
                    }
                    VolumetricShading.drawVolumetricEllipsoid(
                        this, chest.x, chest.y, rx, ry,
                        main = if (style == QuadrupedStyle.WOLF) bodyHi else bodyMain,
                        highlight = if (style == QuadrupedStyle.WOLF) bellyColor else bodyHi,
                        shadow = bodySh,
                        outlineW = ow, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
                        useSpecular = useSpec, useRim = useRim
                    )
                    // Wolf jagged shoulder ruff / mane tuft
                    if (style == QuadrupedStyle.WOLF) {
                        val ruff = facing.project(ox, oy, u, 0f, frontF * 0.80f, bodyUp + 3.2f)
                        VolumetricShading.drawVolumetricEllipsoid(
                            this, ruff.x, ruff.y, u * 5.8f, u * 4.2f,
                            main = bodySh, highlight = bodyHi, shadow = VolumetricShading.coolShadow(bodySh, 0.35f),
                            outlineW = ow * 0.9f, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
                            useSpecular = useSpec
                        )
                    }
                }
                7 -> { // HEAD, EARS, SNOUT, EYES (turns cleanly 360°)
                    val headUp = when (style) {
                        QuadrupedStyle.RABBIT -> bodyUp + 5.8f
                        QuadrupedStyle.RAT -> bodyUp + 2.4f
                        QuadrupedStyle.WOLF -> bodyUp + 6.2f + lunge * 1.4f
                    }
                    val headF = frontF + when (style) {
                        QuadrupedStyle.RABBIT -> 3.4f
                        QuadrupedStyle.RAT -> 4.6f
                        QuadrupedStyle.WOLF -> 4.8f + lunge * 2.5f
                    }
                    val headPos = facing.project(ox, oy, u, 0f, headF, headUp)

                    // Ears behind head if front-facing, or in front of forehead if rear-facing
                    fun drawEars() {
                        val earLen = when (style) {
                            QuadrupedStyle.RABBIT -> 9.5f
                            QuadrupedStyle.RAT -> 3.4f
                            QuadrupedStyle.WOLF -> 5.2f
                        }
                        for (side in -1..1 step 2) {
                            val rOff = side * (if (style == QuadrupedStyle.RABBIT) 2.4f else 3.2f)
                            val earBase = facing.project(ox, oy, u, rOff, headF - 1.2f, headUp + 2.8f)
                            val earTip = facing.project(
                                ox, oy, u,
                                rOff * 1.35f,
                                headF - (if (style == QuadrupedStyle.RABBIT) 3.4f else 1.8f),
                                headUp + 2.8f + earLen
                            )
                            if (style == QuadrupedStyle.RAT) {
                                VolumetricShading.drawVolumetricEllipsoid(
                                    this, earBase.x, earBase.y - u * 1.2f, u * 2.4f, u * 2.3f,
                                    main = bodyMain, highlight = accentColor, shadow = bodySh,
                                    outlineW = ow * 0.78f, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
                                    useSpecular = false
                                )
                            } else {
                                VolumetricShading.drawVolumetricCylinder(
                                    this, earBase, earTip,
                                    rStart = u * (if (style == QuadrupedStyle.RABBIT) 2.0f else 1.8f),
                                    rEnd = u * (if (style == QuadrupedStyle.RABBIT) 1.35f else 0.55f),
                                    main = bodyMain, highlight = bodyHi, shadow = bodySh,
                                    outlineW = ow * 0.85f, lightX = facing.lightScreenX, path = path
                                )
                                if (!facing.isBackFacing && style == QuadrupedStyle.RABBIT) {
                                    drawLine(
                                        color = accentColor,
                                        start = Offset((earBase.x + earTip.x) * 0.5f, (earBase.y + earTip.y) * 0.5f),
                                        end = Offset(earBase.x + (earTip.x - earBase.x) * 0.78f, earBase.y + (earTip.y - earBase.y) * 0.78f),
                                        strokeWidth = u * 1.3f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }

                    if (!facing.isBackFacing) drawEars()

                    val headRx = when (style) {
                        QuadrupedStyle.RABBIT -> u * 4.8f
                        QuadrupedStyle.RAT -> u * 4.4f
                        QuadrupedStyle.WOLF -> u * 5.2f
                    }
                    val headRy = when (style) {
                        QuadrupedStyle.RABBIT -> u * 4.4f
                        QuadrupedStyle.RAT -> u * 3.6f
                        QuadrupedStyle.WOLF -> u * 4.6f
                    }
                    VolumetricShading.drawVolumetricEllipsoid(
                        this, headPos.x, headPos.y, headRx, headRy,
                        main = bodyMain, highlight = bodyHi, shadow = bodySh,
                        outlineW = ow, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
                        useSpecular = useSpec, useRim = useRim
                    )

                    if (facing.isBackFacing) drawEars()

                    // Projecting 3D Snout / Muzzle (visible from front, 3/4, and side angles!)
                    if (facing.forwardDepth > -0.45f) {
                        val snoutLen = when (style) {
                            QuadrupedStyle.RABBIT -> 2.8f
                            QuadrupedStyle.RAT -> 5.0f
                            QuadrupedStyle.WOLF -> 5.2f
                        }
                        val snoutPos = facing.project(ox, oy, u, 0f, headF + snoutLen, headUp - 1.1f)
                        VolumetricShading.drawVolumetricEllipsoid(
                            this, snoutPos.x, snoutPos.y,
                            rx = u * (if (style == QuadrupedStyle.WOLF) 3.1f else 2.4f),
                            ry = u * (if (style == QuadrupedStyle.WOLF) 2.5f else 2.0f),
                            main = if (style == QuadrupedStyle.WOLF) bellyColor else bodyHi,
                            highlight = Color.White.copy(alpha = 0.9f), shadow = bodySh,
                            outlineW = ow * 0.82f, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
                            useSpecular = false
                        )
                        // Nose tip
                        val nosePos = facing.project(ox, oy, u, 0f, headF + snoutLen + 1.8f, headUp - 0.7f)
                        drawCircle(
                            color = if (style == QuadrupedStyle.WOLF) INK else accentColor,
                            radius = u * 1.15f,
                            center = nosePos
                        )
                        // Wolf bared fangs on attack lunge
                        if (style == QuadrupedStyle.WOLF && lunge > 0.15f) {
                            for (s in -1..1 step 2) {
                                val fang = facing.project(ox, oy, u, s * 1.4f, headF + snoutLen + 0.8f, headUp - 2.6f)
                                drawCircle(Color.White, u * 0.95f, fang)
                            }
                        }
                    }

                    // Eyes (when face is visible toward camera)
                    if (!facing.isBackFacing) {
                        val eyeColor = when (style) {
                            QuadrupedStyle.RABBIT -> INK
                            QuadrupedStyle.RAT -> Color(0xFFFF4545)
                            QuadrupedStyle.WOLF -> accentColor
                        }
                        for (side in -1..1 step 2) {
                            val eyeDepth = facing.depthOf(side * 2.4f, headF + 3.2f)
                            if (eyeDepth > facing.depthOf(0f, headF) - 1.2f) {
                                val ep = facing.project(ox, oy, u, side * 2.3f, headF + 2.8f, headUp + 0.8f)
                                drawCircle(INK, u * 1.35f, ep)
                                drawCircle(eyeColor, u * 0.92f, ep)
                                if (style == QuadrupedStyle.RABBIT) {
                                    drawCircle(Color.White, u * 0.35f, Offset(ep.x - u * 0.3f, ep.y - u * 0.3f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 2. VOLUMETRIC 3D ARACHNID (Poison Spider — 8 depth-sorted 3D jointed legs)
    // -------------------------------------------------------------------------

    private fun DrawScope.drawPoisonSpider(
        ox: Float,
        oy: Float,
        u: Float,
        ow: Float,
        facing: ResolvedScreenFacing,
        walkPhase: Float,
        t: Float,
        lunge: Float,
        useSpec: Boolean,
        useRim: Boolean,
        path: Path
    ) {
        val bodyMain = Color(0xFF282430)
        val bodyHi = Color(0xFF564D66)
        val bodySh = Color(0xFF131018)
        val venomGreen = Color(0xFF7BFF33)
        val bodyUp = 10.5f + sin(t * 3.6f) * 0.7f + lunge * 2.6f

        // 10 sortable items: 0..7 = 8 articulated 3D legs (4 left, 4 right), 8 = ABDOMEN, 9 = CEPHALOTHORAX
        val legLocalF = floatArrayOf(-5.5f, -1.8f, +2.0f, +5.6f, -5.5f, -1.8f, +2.0f, +5.6f)
        val legSide = floatArrayOf(-1f, -1f, -1f, -1f, +1f, +1f, +1f, +1f)
        val order = IntArray(10) { it }
        val depths = FloatArray(10) { idx ->
            when (idx) {
                in 0..7 -> facing.depthOf(legSide[idx] * 11.5f, legLocalF[idx] * 1.2f)
                8 -> facing.depthOf(0f, -7.2f)
                else -> facing.depthOf(0f, +4.2f) + 0.3f
            }
        }
        sortIndices(order, depths)

        for (i in 0 until 10) {
            when (val id = order[i]) {
                in 0..7 -> {
                    val side = legSide[id]
                    val fBase = legLocalF[id]
                    val tripod = if ((id % 2) == 0) 1f else -1f
                    val step = if (abs(walkPhase) > 0.01f) sin(walkPhase + tripod * 1.4f) else sin(t * 2.8f + id) * 0.18f
                    val lift = (step * 2.2f).coerceAtLeast(0f)
                    val coxa = facing.project(ox, oy, u, side * 3.5f, fBase * 0.55f + 1.0f, bodyUp - 0.5f)
                    val knee = facing.project(ox, oy, u, side * 10.5f, fBase * 1.15f + step * 1.8f, bodyUp + 6.5f + lift * 0.6f)
                    val foot = facing.project(ox, oy, u, side * 16.2f, fBase * 1.65f + step * 2.4f, 0.8f + lift)

                    if (lift < 1.1f) {
                        VolumetricShading.drawFootContactDot(this, foot, u * 1.8f, u * 1.0f)
                    }
                    VolumetricShading.drawVolumetricCylinder(
                        this, coxa, knee, u * 1.55f, u * 1.25f,
                        main = bodyMain, highlight = bodyHi, shadow = bodySh,
                        outlineW = ow * 0.82f, lightX = facing.lightScreenX, path = path
                    )
                    VolumetricShading.drawVolumetricCylinder(
                        this, knee, foot, u * 1.22f, u * 0.68f,
                        main = bodyMain, highlight = bodyHi, shadow = bodySh,
                        outlineW = ow * 0.78f, lightX = facing.lightScreenX, path = path
                    )
                    // Toxic green knuckle accent
                    drawCircle(venomGreen.copy(alpha = 0.78f), u * 0.85f, knee)
                }
                8 -> { // BULBOUS GLOSSY ABDOMEN (with hourglass dorsal marking)
                    val abd = facing.project(ox, oy, u, 0f, -7.2f, bodyUp + 2.8f)
                    VolumetricShading.drawVolumetricEllipsoid(
                        this, abd.x, abd.y, u * 9.0f, u * 7.6f,
                        main = bodyMain, highlight = bodyHi, shadow = bodySh,
                        outlineW = ow * 1.05f, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
                        useSpecular = useSpec, useRim = useRim
                    )
                    // Venomous dorsal hourglass marking (clear from top/rear/3-4 angles)
                    val markTop = facing.project(ox, oy, u, 0f, -5.8f, bodyUp + 8.5f)
                    val markBot = facing.project(ox, oy, u, 0f, -10.8f, bodyUp + 6.4f)
                    val markMid = Offset((markTop.x + markBot.x) * 0.5f, (markTop.y + markBot.y) * 0.5f)
                    drawCircle(venomGreen, u * 2.2f, markTop)
                    drawCircle(venomGreen, u * 1.6f, markBot)
                    drawCircle(Color(0xFFD8FF9E), u * 0.9f, markMid)
                }
                9 -> { // CEPHALOTHORAX + CLUSTERED EYES + VENOM FANGS
                    val ceph = facing.project(ox, oy, u, 0f, +4.2f, bodyUp)
                    VolumetricShading.drawVolumetricEllipsoid(
                        this, ceph.x, ceph.y, u * 5.8f, u * 4.8f,
                        main = bodyMain, highlight = bodyHi, shadow = bodySh,
                        outlineW = ow, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
                        useSpecular = useSpec, useRim = useRim
                    )
                    if (!facing.isBackFacing) {
                        // 4 glowing venomous green eyes
                        val eyeOffsets = floatArrayOf(-2.4f, -0.9f, +0.9f, +2.4f)
                        for (idx in eyeOffsets.indices) {
                            val r = eyeOffsets[idx]
                            val ep = facing.project(ox, oy, u, r, +8.4f, bodyUp + if (idx == 1 || idx == 2) 1.4f else 0.7f)
                            drawCircle(INK, u * 1.25f, ep)
                            drawCircle(venomGreen, u * 0.9f, ep)
                        }
                        // Dripping mandibles
                        for (side in -1..1 step 2) {
                            val fBase = facing.project(ox, oy, u, side * 2.1f, +8.2f, bodyUp - 1.5f)
                            val fTip = facing.project(ox, oy, u, side * 1.5f, +10.2f + lunge * 2.2f, bodyUp - 4.4f)
                            VolumetricShading.drawVolumetricCylinder(
                                this, fBase, fTip, u * 1.2f, u * 0.45f,
                                main = Color(0xFF433C4D), highlight = venomGreen, shadow = INK,
                                outlineW = ow * 0.7f, lightX = facing.lightScreenX, path = path
                            )
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 3. VOLUMETRIC 3D SERPENT (Young Snake — stacked coils + turning 3D hood)
    // -------------------------------------------------------------------------

    private fun DrawScope.drawYoungSnake(
        ox: Float,
        oy: Float,
        u: Float,
        ow: Float,
        facing: ResolvedScreenFacing,
        t: Float,
        lunge: Float,
        useSpec: Boolean,
        useRim: Boolean,
        path: Path
    ) {
        val scaleMain = Color(0xFF3E8F43)
        val scaleHi = Color(0xFF7AD665)
        val scaleSh = Color(0xFF1F4E24)
        val belly = Color(0xFFD8D08B)
        val sway = sin(t * 4.2f) * 2.6f

        // Stacked 3D coiled rings resting on ground
        for (ring in 0..2) {
            val rUp = 2.6f + ring * 3.6f
            val rF = -ring * 1.2f
            val rx = u * (10.5f - ring * 1.7f)
            val ry = u * (5.0f - ring * 0.6f)
            val rc = facing.project(ox, oy, u, sway * 0.15f * ring, rF, rUp)
            VolumetricShading.drawVolumetricEllipsoid(
                this, rc.x, rc.y, rx, ry,
                main = scaleMain, highlight = scaleHi, shadow = scaleSh,
                outlineW = ow, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
                useSpecular = useSpec, useRim = useRim && ring == 0
            )
            // Dorsal diamond pattern stripe
            val stripe = facing.project(ox, oy, u, -3.5f + ring * 1.5f, rF, rUp + 1.4f)
            drawCircle(scaleSh.copy(alpha = 0.65f), u * 1.5f, stripe)
        }

        // Rising sinuous S-neck projecting toward entity facing
        val neckBase = facing.project(ox, oy, u, 0f, 0f, 9.8f)
        val neckMid = facing.project(ox, oy, u, sway * 0.7f, 3.8f + lunge * 2.5f, 17.5f)
        val headPos = facing.project(ox, oy, u, sway * 0.35f, 7.6f + lunge * 5.2f, 23.8f - lunge * 1.8f)

        VolumetricShading.drawVolumetricCylinder(
            this, neckBase, neckMid, u * 3.4f, u * 2.8f,
            main = scaleMain, highlight = scaleHi, shadow = scaleSh,
            outlineW = ow, lightX = facing.lightScreenX, path = path
        )
        // Flared cobra/viper hood
        val hoodPos = facing.project(ox, oy, u, sway * 0.45f, 5.8f + lunge * 3.8f, 21.4f)
        VolumetricShading.drawVolumetricEllipsoid(
            this, hoodPos.x, hoodPos.y, u * 5.6f, u * 4.4f,
            main = if (facing.isBackFacing) scaleMain else belly,
            highlight = if (facing.isBackFacing) scaleHi else Color(0xFFFFF3B8),
            shadow = scaleSh,
            outlineW = ow * 0.92f, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
            useSpecular = useSpec
        )
        VolumetricShading.drawVolumetricCylinder(
            this, neckMid, headPos, u * 2.7f, u * 2.5f,
            main = scaleMain, highlight = scaleHi, shadow = scaleSh,
            outlineW = ow * 0.92f, lightX = facing.lightScreenX, path = path
        )

        // Angular 3D viper head
        VolumetricShading.drawVolumetricEllipsoid(
            this, headPos.x, headPos.y, u * 4.5f, u * 3.4f,
            main = scaleMain, highlight = scaleHi, shadow = scaleSh,
            outlineW = ow, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
            useSpecular = useSpec, useRim = useRim
        )

        if (!facing.isBackFacing) {
            for (side in -1..1 step 2) {
                val ep = facing.project(ox, oy, u, sway * 0.35f + side * 2.0f, 10.2f + lunge * 5.2f, 24.4f - lunge * 1.8f)
                drawCircle(INK, u * 1.2f, ep)
                drawCircle(Color(0xFFFFD84A), u * 0.85f, ep)
            }
            // Flickering crimson forked tongue
            if (sin(t * 7.5f) > 0.1f || lunge > 0.15f) {
                val tBase = facing.project(ox, oy, u, sway * 0.35f, 11.4f + lunge * 5.2f, 22.8f - lunge * 1.8f)
                val tMid = facing.project(ox, oy, u, sway * 0.35f, 14.8f + lunge * 6.0f, 22.0f - lunge * 1.8f)
                val tFork1 = facing.project(ox, oy, u, sway * 0.35f - 1.4f, 16.8f + lunge * 6.0f, 21.4f - lunge * 1.8f)
                val tFork2 = facing.project(ox, oy, u, sway * 0.35f + 1.4f, 16.8f + lunge * 6.0f, 21.4f - lunge * 1.8f)
                drawLine(Color(0xFFE63946), tBase, tMid, strokeWidth = u * 1.0f, cap = StrokeCap.Round)
                drawLine(Color(0xFFE63946), tMid, tFork1, strokeWidth = u * 0.85f, cap = StrokeCap.Round)
                drawLine(Color(0xFFE63946), tMid, tFork2, strokeWidth = u * 0.85f, cap = StrokeCap.Round)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 4. VOLUMETRIC 3D DRAGONS (Dragon Hatchling & Elder Dragon)
    // -------------------------------------------------------------------------

    private fun DrawScope.drawDragon3D(
        ox: Float,
        oy: Float,
        u: Float,
        ow: Float,
        facing: ResolvedScreenFacing,
        stride: Float,
        bobUp: Float,
        t: Float,
        lunge: Float,
        isElder: Boolean,
        useSpec: Boolean,
        useRim: Boolean,
        quality: GraphicsQuality,
        path: Path
    ) {
        val scaleMain = if (isElder) Color(0xFF9E1F1F) else Color(0xFF2E7D4A)
        val scaleHi = if (isElder) Color(0xFFE34A32) else Color(0xFF5DC47A)
        val scaleSh = if (isElder) Color(0xFF520C0E) else Color(0xFF154225)
        val belly = if (isElder) Color(0xFFE8B44C) else Color(0xFFD9CC7A)
        val wingMembrane = if (isElder) Color(0xFF5E1618) else Color(0xFF205434)
        val wingSpan = if (isElder) 22f else 17f
        val flap = sin(t * 5.0f) * 5.5f + lunge * 4.0f
        val bodyUp = 15.5f + bobUp + sin(t * 2.8f) * 0.8f

        // 9 sortable 3D parts: 0=TAIL, 1=LEFT_WING, 2=RIGHT_WING, 3=BL, 4=BR, 5=FL, 6=FR, 7=BODY, 8=NECK_HEAD
        val order = IntArray(9) { it }
        val depths = floatArrayOf(
            facing.depthOf(0f, -13.5f),                        // 0 TAIL
            facing.depthOf(-wingSpan * 0.55f, -1.5f),          // 1 LEFT_WING
            facing.depthOf(+wingSpan * 0.55f, -1.5f),          // 2 RIGHT_WING
            facing.depthOf(-5.2f, -6.5f) - 0.3f,               // 3 BL
            facing.depthOf(+5.2f, -6.5f) - 0.3f,               // 4 BR
            facing.depthOf(-5.2f, +5.8f) - 0.2f,               // 5 FL
            facing.depthOf(+5.2f, +5.8f) - 0.2f,               // 6 FR
            facing.depthOf(0f, 0f),                            // 7 BODY
            facing.depthOf(0f, +9.8f) + 0.6f                   // 8 NECK_HEAD
        )
        sortIndices(order, depths)

        for (i in 0 until 9) {
            when (order[i]) {
                0 -> { // LASHING SPIKED TAIL
                    val wag = sin(t * 3.4f) * 4.5f
                    val base = facing.project(ox, oy, u, 0f, -6.5f, bodyUp - 1.5f)
                    val mid = facing.project(ox, oy, u, wag, -13.5f, bodyUp - 5.5f)
                    val tip = facing.project(ox, oy, u, -wag * 0.8f, -20.5f, bodyUp - 2.0f)
                    VolumetricShading.drawVolumetricCylinder(
                        this, base, mid, u * 3.8f, u * 2.4f,
                        main = scaleMain, highlight = scaleHi, shadow = scaleSh,
                        outlineW = ow * 0.92f, lightX = facing.lightScreenX, path = path
                    )
                    VolumetricShading.drawVolumetricCylinder(
                        this, mid, tip, u * 2.3f, u * 1.0f,
                        main = scaleMain, highlight = scaleHi, shadow = scaleSh,
                        outlineW = ow * 0.85f, lightX = facing.lightScreenX, path = path
                    )
                    // Tail spade tip
                    VolumetricShading.drawVolumetricEllipsoid(
                        this, tip.x, tip.y, u * 2.6f, u * 1.9f,
                        main = belly, highlight = Color(0xFFFFF2B0), shadow = scaleSh,
                        outlineW = ow * 0.78f, lightX = facing.lightScreenX, lightY = facing.lightScreenY, useSpecular = false
                    )
                }
                1, 2 -> { // 3D BAT WINGS (Left / Right)
                    val side = if (order[i] == 1) -1f else 1f
                    val shoulder = facing.project(ox, oy, u, side * 4.2f, +1.5f, bodyUp + 3.5f)
                    val elbow = facing.project(ox, oy, u, side * (wingSpan * 0.56f), -1.0f, bodyUp + 12.5f + flap * 0.7f)
                    val tip = facing.project(ox, oy, u, side * wingSpan, -5.5f, bodyUp + 8.0f + flap)
                    val trailing1 = facing.project(ox, oy, u, side * (wingSpan * 0.72f), -7.0f, bodyUp + 1.5f + flap * 0.45f)
                    val trailing2 = facing.project(ox, oy, u, side * (wingSpan * 0.36f), -5.0f, bodyUp + 0.5f + flap * 0.25f)

                    path.reset()
                    path.moveTo(shoulder.x, shoulder.y)
                    path.lineTo(elbow.x, elbow.y)
                    path.lineTo(tip.x, tip.y)
                    path.quadraticTo(trailing1.x, trailing1.y + u * 2.5f, trailing1.x, trailing1.y)
                    path.quadraticTo(trailing2.x, trailing2.y + u * 2.0f, trailing2.x, trailing2.y)
                    path.lineTo(shoulder.x, shoulder.y)
                    path.close()
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(scaleMain, wingMembrane, VolumetricShading.coolShadow(wingMembrane, 0.45f)),
                            start = elbow,
                            end = trailing2
                        )
                    )
                    drawPath(path, INK, style = Stroke(width = ow * 0.85f, join = StrokeJoin.Round))
                    // Wing bone spars
                    VolumetricShading.drawVolumetricCylinder(
                        this, shoulder, elbow, u * 1.5f, u * 1.25f,
                        main = scaleHi, highlight = belly, shadow = scaleSh,
                        outlineW = ow * 0.72f, lightX = facing.lightScreenX, path = path
                    )
                    VolumetricShading.drawVolumetricCylinder(
                        this, elbow, tip, u * 1.2f, u * 0.65f,
                        main = scaleMain, highlight = scaleHi, shadow = scaleSh,
                        outlineW = ow * 0.68f, lightX = facing.lightScreenX, path = path
                    )
                    drawLine(INK.copy(alpha = 0.72f), elbow, trailing1, strokeWidth = ow * 0.7f)
                }
                3, 4, 5, 6 -> { // 4 CLAWED DRAGON LEGS
                    val isBack = order[i] == 3 || order[i] == 4
                    val side = if (order[i] == 4 || order[i] == 6) 1f else -1f
                    val fBase = if (isBack) -5.8f else +5.2f
                    val phase = if ((isBack && side > 0) || (!isBack && side < 0)) stride else -stride
                    val hip = facing.project(ox, oy, u, side * 4.5f, fBase, bodyUp - 2.0f)
                    val foot = facing.project(ox, oy, u, side * 5.6f, fBase + phase * 3.5f, 1.2f + (phase * 1.8f).coerceAtLeast(0f))
                    VolumetricShading.drawFootContactDot(this, foot, u * 2.6f, u * 1.4f)
                    VolumetricShading.drawVolumetricCylinder(
                        this, hip, foot, u * 2.9f, u * 2.2f,
                        main = scaleMain, highlight = scaleHi, shadow = scaleSh,
                        outlineW = ow * 0.9f, lightX = facing.lightScreenX, path = path
                    )
                    VolumetricShading.drawVolumetricEllipsoid(
                        this, foot.x, foot.y, u * 2.8f, u * 1.7f,
                        main = scaleSh, highlight = belly, shadow = INK,
                        outlineW = ow * 0.8f, lightX = facing.lightScreenX, lightY = facing.lightScreenY, useSpecular = false
                    )
                }
                7 -> { // VOLUMETRIC SCALED BODY + BELLY PLATES
                    val body = facing.project(ox, oy, u, 0f, -0.5f, bodyUp)
                    VolumetricShading.drawVolumetricEllipsoid(
                        this, body.x, body.y,
                        rx = u * (9.2f + abs(facing.forwardScreenX) * 3.2f),
                        ry = u * 7.6f,
                        main = scaleMain, highlight = scaleHi, shadow = scaleSh,
                        outlineW = ow * 1.05f, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
                        useSpecular = useSpec, useRim = useRim
                    )
                    if (!facing.isBackFacing) {
                        val chestPlate = facing.project(ox, oy, u, 0f, +4.8f, bodyUp - 1.2f)
                        VolumetricShading.drawVolumetricEllipsoid(
                            this, chestPlate.x, chestPlate.y, u * 5.2f, u * 4.2f,
                            main = belly, highlight = Color(0xFFFFF0B8), shadow = VolumetricShading.coolShadow(belly, 0.4f),
                            outlineW = ow * 0.78f, lightX = facing.lightScreenX, lightY = facing.lightScreenY, useSpecular = false
                        )
                    } else {
                        // Dorsal ridge spines on back
                        for (s in -1..1) {
                            val sp = facing.project(ox, oy, u, 0f, s * 3.8f - 1.0f, bodyUp + 6.8f)
                            drawCircle(belly, u * 1.5f, sp)
                            drawCircle(INK, u * 1.5f, sp, style = Stroke(ow * 0.65f))
                        }
                    }
                }
                8 -> { // RISING NECK + HORNED DRAGON HEAD + FIRE BREATH
                    val neckBase = facing.project(ox, oy, u, 0f, +5.8f, bodyUp + 3.2f)
                    val headPos = facing.project(ox, oy, u, 0f, +10.2f + lunge * 4.0f, bodyUp + 12.2f - lunge * 1.5f)
                    VolumetricShading.drawVolumetricCylinder(
                        this, neckBase, headPos, u * 3.8f, u * 3.0f,
                        main = scaleMain, highlight = scaleHi, shadow = scaleSh,
                        outlineW = ow, lightX = facing.lightScreenX, path = path
                    )
                    // Sweeping back-curved golden/obsidian horns
                    for (side in -1..1 step 2) {
                        val hBase = facing.project(ox, oy, u, side * 2.4f, +8.8f + lunge * 4.0f, bodyUp + 14.5f)
                        val hTip = facing.project(ox, oy, u, side * 5.2f, +4.2f + lunge * 4.0f, bodyUp + 20.5f)
                        VolumetricShading.drawVolumetricCylinder(
                            this, hBase, hTip, u * 1.5f, u * 0.55f,
                            main = if (isElder) Color(0xFF2E272B) else belly,
                            highlight = Color(0xFFFFE7A0), shadow = INK,
                            outlineW = ow * 0.78f, lightX = facing.lightScreenX, path = path
                        )
                    }
                    VolumetricShading.drawVolumetricEllipsoid(
                        this, headPos.x, headPos.y, u * 5.4f, u * 4.5f,
                        main = scaleMain, highlight = scaleHi, shadow = scaleSh,
                        outlineW = ow, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
                        useSpecular = useSpec, useRim = useRim
                    )
                    if (facing.forwardDepth > -0.45f) {
                        val snout = facing.project(ox, oy, u, 0f, +15.2f + lunge * 4.2f, bodyUp + 10.8f - lunge * 1.5f)
                        VolumetricShading.drawVolumetricEllipsoid(
                            this, snout.x, snout.y, u * 3.6f, u * 2.8f,
                            main = scaleMain, highlight = scaleHi, shadow = scaleSh,
                            outlineW = ow * 0.88f, lightX = facing.lightScreenX, lightY = facing.lightScreenY, useSpecular = false
                        )
                        // Flame breath cone on attack lunge
                        if (lunge > 0.18f && quality != GraphicsQuality.LOW) {
                            val flameTip = facing.project(ox, oy, u, 0f, +25.0f + lunge * 6.0f, bodyUp + 8.5f)
                            drawLine(Color(0xFFFF6A1A).copy(alpha = 0.75f * lunge), snout, flameTip, strokeWidth = u * 5.2f, cap = StrokeCap.Round)
                            drawLine(Color(0xFFFFEE73).copy(alpha = 0.85f * lunge), snout, flameTip, strokeWidth = u * 2.4f, cap = StrokeCap.Round)
                        }
                    }
                    if (!facing.isBackFacing) {
                        for (side in -1..1 step 2) {
                            val ep = facing.project(ox, oy, u, side * 2.4f, +13.0f + lunge * 4.0f, bodyUp + 12.8f - lunge * 1.5f)
                            drawCircle(INK, u * 1.4f, ep)
                            drawCircle(Color(0xFFFFCF33), u * 1.0f, ep)
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 5. VOLUMETRIC 3D BIPEDAL / HUMANOID MONSTERS (10 distinct species)
    // -------------------------------------------------------------------------

    private enum class BipedMonsterStyle {
        SWAMP_TROLL,
        SKELETON,
        GOBLIN_SCOUT,
        MINOTAUR,
        ORC_WARRIOR,
        GHOUL,
        FIRE_DEVIL,
        CYCLOPS,
        NECROMANCER,
        DEMON_LORD
    }

    private fun DrawScope.drawBipedMonster(
        ox: Float,
        oy: Float,
        u: Float,
        ow: Float,
        facing: ResolvedScreenFacing,
        stride: Float,
        bobUp: Float,
        breath: Float,
        lunge: Float,
        t: Float,
        species: BipedMonsterStyle,
        useSpec: Boolean,
        useRim: Boolean,
        quality: GraphicsQuality,
        path: Path
    ) {
        val (skinMain, skinHi, skinSh) = when (species) {
            BipedMonsterStyle.SWAMP_TROLL -> Triple(Color(0xFF4F7A46), Color(0xFF7CB26B), Color(0xFF284524))
            BipedMonsterStyle.SKELETON -> Triple(Color(0xFFE6DEC8), Color(0xFFFFFBF0), Color(0xFF9C917A))
            BipedMonsterStyle.GOBLIN_SCOUT -> Triple(Color(0xFF5D8F3B), Color(0xFF8CC45E), Color(0xFF2F4F1C))
            BipedMonsterStyle.MINOTAUR -> Triple(Color(0xFF7A4428), Color(0xFFAC6944), Color(0xFF422112))
            BipedMonsterStyle.ORC_WARRIOR -> Triple(Color(0xFF51703B), Color(0xFF7CA15B), Color(0xFF283B1C))
            BipedMonsterStyle.GHOUL -> Triple(Color(0xFF7D857B), Color(0xFFAAB5A8), Color(0xFF444A45))
            BipedMonsterStyle.FIRE_DEVIL -> Triple(Color(0xFFC9331E), Color(0xFFFF6D3B), Color(0xFF6B120B))
            BipedMonsterStyle.CYCLOPS -> Triple(Color(0xFFA6744E), Color(0xFFD6A074), Color(0xFF5E3C23))
            BipedMonsterStyle.NECROMANCER -> Triple(Color(0xFF2A2236), Color(0xFF524469), Color(0xFF140F1C))
            BipedMonsterStyle.DEMON_LORD -> Triple(Color(0xFF8F1B1E), Color(0xFFD93B32), Color(0xFF450A0E))
        }

        val torsoWidth = when (species) {
            BipedMonsterStyle.DEMON_LORD, BipedMonsterStyle.CYCLOPS, BipedMonsterStyle.MINOTAUR -> 10.2f
            BipedMonsterStyle.SWAMP_TROLL, BipedMonsterStyle.ORC_WARRIOR -> 9.2f
            BipedMonsterStyle.SKELETON, BipedMonsterStyle.GHOUL -> 6.8f
            BipedMonsterStyle.GOBLIN_SCOUT -> 7.2f
            else -> 8.2f
        }
        val shoulderR = torsoWidth * 0.96f
        val hipUp = 13.5f + bobUp
        val shoulderUp = 28.5f + bobUp + breath * 0.55f
        val headUp = 36.2f + bobUp + breath * 0.6f
        val hasWings = species == BipedMonsterStyle.DEMON_LORD
        val hasTail = species == BipedMonsterStyle.FIRE_DEVIL || species == BipedMonsterStyle.DEMON_LORD
        val hasCape = species == BipedMonsterStyle.NECROMANCER || species == BipedMonsterStyle.GHOUL

        // 8 sortable layers: 0=BACK_EXTRAS(wings/tail/cape), 1=L_LEG, 2=R_LEG, 3=L_ARM_OFF, 4=R_ARM_WEAP, 5=TORSO, 6=HEAD
        val order = IntArray(7) { it }
        val depths = floatArrayOf(
            if (facing.isBackFacing && hasCape) 0.4f else facing.depthOf(0f, -6.5f) - 1.2f, // 0 BACK_EXTRAS
            facing.depthOf(-3.6f, -stride * 4.0f) - 0.6f,                                    // 1 L_LEG
            facing.depthOf(+3.6f, +stride * 4.0f) - 0.6f,                                    // 2 R_LEG
            facing.depthOf(-shoulderR, 1.5f),                                                // 3 L_ARM_OFF
            facing.depthOf(+shoulderR, 2.5f + lunge * 4.5f),                                 // 4 R_ARM_WEAP
            0.0f,                                                                            // 5 TORSO
            0.7f + facing.forwardDepth * 0.35f                                               // 6 HEAD
        )
        sortIndices(order, depths)

        for (i in 0 until 7) {
            when (order[i]) {
                0 -> { // WINGS / TAIL / NECRO CAPE
                    if (hasWings) {
                        val flap = sin(t * 4.0f) * 3.8f + lunge * 4.0f
                        for (side in -1..1 step 2) {
                            val sh = facing.project(ox, oy, u, side * 5.0f, -3.5f, shoulderUp)
                            val tip = facing.project(ox, oy, u, side * 23.0f, -9.5f, shoulderUp + 12.5f + flap)
                            val bot = facing.project(ox, oy, u, side * 14.5f, -7.5f, hipUp + 1.0f + flap * 0.4f)
                            path.reset()
                            path.moveTo(sh.x, sh.y)
                            path.lineTo(tip.x, tip.y)
                            path.quadraticTo((tip.x + bot.x) * 0.5f, bot.y + u * 2.5f, bot.x, bot.y)
                            path.lineTo(sh.x, sh.y)
                            path.close()
                            drawPath(
                                path = path,
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF3A1219), Color(0xFF1A090E)),
                                    start = tip,
                                    end = sh
                                )
                            )
                            drawPath(path, INK, style = Stroke(ow * 0.9f, join = StrokeJoin.Round))
                            VolumetricShading.drawVolumetricCylinder(
                                this, sh, tip, u * 1.8f, u * 0.8f,
                                main = Color(0xFF2B181E), highlight = Color(0xFFFF6D3B), shadow = INK,
                                outlineW = ow * 0.72f, lightX = facing.lightScreenX, path = path
                            )
                        }
                    }
                    if (hasTail) {
                        val wag = sin(t * 4.8f) * 3.8f
                        val tb = facing.project(ox, oy, u, 0f, -4.5f, hipUp)
                        val tm = facing.project(ox, oy, u, wag, -11.5f, hipUp - 4.0f)
                        val tt = facing.project(ox, oy, u, wag * 1.4f, -15.5f, hipUp + 1.5f)
                        VolumetricShading.drawVolumetricCylinder(
                            this, tb, tm, u * 1.6f, u * 1.0f,
                            main = skinMain, highlight = skinHi, shadow = skinSh,
                            outlineW = ow * 0.75f, lightX = facing.lightScreenX, path = path
                        )
                        VolumetricShading.drawVolumetricCylinder(
                            this, tm, tt, u * 1.0f, u * 0.55f,
                            main = skinSh, highlight = skinHi, shadow = INK,
                            outlineW = ow * 0.68f, lightX = facing.lightScreenX, path = path
                        )
                    }
                    if (hasCape) {
                        val capeColor = if (species == BipedMonsterStyle.NECROMANCER) Color(0xFF5E162B) else Color(0xFF474F49)
                        val topL = facing.project(ox, oy, u, -6.5f, -3.5f, shoulderUp)
                        val topR = facing.project(ox, oy, u, +6.5f, -3.5f, shoulderUp)
                        val botR = facing.project(ox, oy, u, +8.2f, -5.8f, 4.0f + bobUp * 0.3f)
                        val botL = facing.project(ox, oy, u, -8.2f, -5.8f, 4.0f + bobUp * 0.3f)
                        path.reset()
                        path.moveTo(topL.x, topL.y)
                        path.lineTo(topR.x, topR.y)
                        path.lineTo(botR.x, botR.y)
                        path.quadraticTo((botL.x + botR.x) * 0.5f, maxOf(botL.y, botR.y) + u * 2.0f, botL.x, botL.y)
                        path.close()
                        drawPath(path, capeColor)
                        drawPath(path, INK, style = Stroke(ow * 0.85f, join = StrokeJoin.Round))
                    }
                }
                1, 2 -> { // LEGS (with contact dots & species foot shapes)
                    val isRight = order[i] == 2
                    val side = if (isRight) 1f else -1f
                    val step = if (isRight) stride else -stride
                    val hip = facing.project(ox, oy, u, side * 3.6f, 0f, hipUp)
                    val foot = facing.project(ox, oy, u, side * 4.0f, step * 4.4f, 1.4f + (step * 1.8f).coerceAtLeast(0f))
                    VolumetricShading.drawFootContactDot(this, foot, u * 2.6f, u * 1.3f)

                    if (species == BipedMonsterStyle.NECROMANCER) {
                        // Flowing lower robe skirt covers legs cleanly
                        VolumetricShading.drawVolumetricCylinder(
                            this, hip, foot, u * 4.2f, u * 4.8f,
                            main = skinMain, highlight = skinHi, shadow = skinSh,
                            outlineW = ow * 0.85f, lightX = facing.lightScreenX, path = path
                        )
                    } else {
                        val legR = if (species == BipedMonsterStyle.SKELETON) u * 1.45f else u * 2.9f
                        VolumetricShading.drawVolumetricCylinder(
                            this, hip, foot, legR, legR * 0.82f,
                            main = skinMain, highlight = skinHi, shadow = skinSh,
                            outlineW = ow * 0.9f, lightX = facing.lightScreenX, path = path
                        )
                        val footColor = when (species) {
                            BipedMonsterStyle.MINOTAUR, BipedMonsterStyle.DEMON_LORD, BipedMonsterStyle.FIRE_DEVIL -> Color(0xFF261C1B)
                            else -> skinSh
                        }
                        VolumetricShading.drawVolumetricEllipsoid(
                            this, foot.x, foot.y, legR * 1.15f, legR * 0.65f,
                            main = footColor, highlight = skinMain, shadow = INK,
                            outlineW = ow * 0.8f, lightX = facing.lightScreenX, lightY = facing.lightScreenY, useSpecular = false
                        )
                    }
                }
                5 -> { // VOLUMETRIC 3D TORSO (species armor, ribcage, molten runes, belt)
                    val topC = facing.project(ox, oy, u, 0f, 0f, shoulderUp)
                    val botC = facing.project(ox, oy, u, 0f, 0f, hipUp)
                    val profileScale = 0.72f + 0.28f * abs(facing.forwardDepth)
                    val topHW = u * torsoWidth * profileScale
                    val botHW = u * (torsoWidth * 0.74f) * profileScale

                    if (species == BipedMonsterStyle.SKELETON) {
                        // 3D Spine + curved ivory ribs + pelvis
                        VolumetricShading.drawVolumetricCylinder(
                            this, topC, botC, u * 1.8f, u * 1.6f,
                            main = skinMain, highlight = skinHi, shadow = skinSh,
                            outlineW = ow * 0.82f, lightX = facing.lightScreenX, path = path
                        )
                        for (rib in 0..2) {
                            val rUp = shoulderUp - 2.5f - rib * 3.6f
                            val rc = facing.project(ox, oy, u, 0f, if (facing.isBackFacing) -1.2f else +1.2f, rUp)
                            VolumetricShading.drawVolumetricEllipsoid(
                                this, rc.x, rc.y, topHW * (0.92f - rib * 0.08f), u * 1.45f,
                                main = skinMain, highlight = skinHi, shadow = skinSh,
                                outlineW = ow * 0.78f, lightX = facing.lightScreenX, lightY = facing.lightScreenY, useSpecular = false
                            )
                        }
                        VolumetricShading.drawVolumetricEllipsoid(
                            this, botC.x, botC.y, botHW * 0.95f, u * 2.5f,
                            main = skinMain, highlight = skinHi, shadow = skinSh,
                            outlineW = ow * 0.82f, lightX = facing.lightScreenX, lightY = facing.lightScreenY, useSpecular = false
                        )
                    } else {
                        val torsoMain = when (species) {
                            BipedMonsterStyle.ORC_WARRIOR -> Color(0xFF5D4029)
                            BipedMonsterStyle.GOBLIN_SCOUT -> Color(0xFF6B4C2E)
                            else -> skinMain
                        }
                        val torsoHi = when (species) {
                            BipedMonsterStyle.ORC_WARRIOR -> Color(0xFF8C6342)
                            BipedMonsterStyle.GOBLIN_SCOUT -> Color(0xFF966E45)
                            else -> skinHi
                        }
                        val torsoSh = VolumetricShading.coolShadow(torsoMain, 0.48f)
                        VolumetricShading.drawVolumetricTorso(
                            scope = this,
                            topCenter = topC,
                            bottomCenter = botC,
                            topHalfW = topHW,
                            botHalfW = botHW,
                            domeHeight = u * 3.4f,
                            main = torsoMain,
                            highlight = torsoHi,
                            shadow = torsoSh,
                            outlineW = ow,
                            lightX = facing.lightScreenX,
                            useRim = useRim,
                            path = path
                        )

                        // Species torso details (chest runes, leather cross-strap, crimson necromancer stole)
                        when (species) {
                            BipedMonsterStyle.DEMON_LORD, BipedMonsterStyle.FIRE_DEVIL -> {
                                val runeC = facing.project(ox, oy, u, 0f, if (facing.isBackFacing) -3.2f else +3.5f, (shoulderUp + hipUp) * 0.54f)
                                drawCircle(Color(0xFFFF7A26).copy(alpha = 0.75f), u * 3.4f, runeC)
                                drawCircle(Color(0xFFFFE77A), u * 1.7f, runeC)
                            }
                            BipedMonsterStyle.CYCLOPS, BipedMonsterStyle.MINOTAUR -> {
                                val strapStart = facing.project(ox, oy, u, -6.5f, 0f, shoulderUp)
                                val strapEnd = facing.project(ox, oy, u, +5.5f, 0f, hipUp + 1.5f)
                                drawLine(Color(0xFF3E2614), strapStart, strapEnd, strokeWidth = u * 2.6f, cap = StrokeCap.Round)
                                drawLine(Color(0xFFD9A64C), strapStart, strapEnd, strokeWidth = u * 0.75f)
                            }
                            BipedMonsterStyle.NECROMANCER -> {
                                val trimTop = facing.project(ox, oy, u, 0f, if (facing.isBackFacing) -2.8f else +3.2f, shoulderUp)
                                val trimBot = facing.project(ox, oy, u, 0f, if (facing.isBackFacing) -2.8f else +3.2f, hipUp - 3.5f)
                                drawLine(Color(0xFF9C2240), trimTop, trimBot, strokeWidth = u * 2.8f, cap = StrokeCap.Round)
                                drawLine(Color(0xFFD9A84E), trimTop, trimBot, strokeWidth = u * 0.7f)
                            }
                            else -> Unit
                        }
                    }
                }
                3 -> { // LEFT ARM + OFFHAND SHIELD / CLAW / ORB
                    val sh = facing.project(ox, oy, u, -shoulderR, 0f, shoulderUp - 1.5f)
                    val hand = facing.project(ox, oy, u, -shoulderR - 1.5f, +4.2f - stride * 2.2f, hipUp + 4.5f)
                    val armR = if (species == BipedMonsterStyle.SKELETON) u * 1.35f else u * 2.4f
                    VolumetricShading.drawVolumetricCylinder(
                        this, sh, hand, armR, armR * 0.82f,
                        main = skinMain, highlight = skinHi, shadow = skinSh,
                        outlineW = ow * 0.85f, lightX = facing.lightScreenX, path = path
                    )
                    // Offhand shields for armed foes (Skeleton, Goblin Scout, Orc Warrior)
                    if (species == BipedMonsterStyle.SKELETON || species == BipedMonsterStyle.GOBLIN_SCOUT || species == BipedMonsterStyle.ORC_WARRIOR) {
                        val shC = facing.project(ox, oy, u, -shoulderR - 2.6f, +5.8f, hipUp + 5.2f)
                        val isIron = species == BipedMonsterStyle.ORC_WARRIOR
                        val shieldRx = u * (if (isIron) 4.4f else 3.8f) * (0.55f + 0.45f * abs(facing.forwardDepth))
                        VolumetricShading.drawVolumetricEllipsoid(
                            this, shC.x, shC.y, shieldRx, u * (if (isIron) 5.8f else 4.4f),
                            main = if (isIron) Color(0xFF5D6775) else Color(0xFF8A592E),
                            highlight = if (isIron) Color(0xFFB3C0D4) else Color(0xFFBF8652),
                            shadow = Color(0xFF2B211B),
                            outlineW = ow * 0.9f, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
                            useSpecular = useSpec
                        )
                        drawCircle(Color(0xFFD9A84E), u * 1.15f, shC)
                    } else if (species == BipedMonsterStyle.NECROMANCER && quality != GraphicsQuality.LOW) {
                        // Orbiting violet soul wisp
                        val wisp = facing.project(ox, oy, u, -shoulderR - 3.5f + cos(t * 4f) * 1.8f, +5.0f, hipUp + 8.5f + sin(t * 4f) * 1.8f)
                        drawCircle(Color(0xFFAE5CFF).copy(alpha = 0.45f), u * 3.4f, wisp)
                        drawCircle(Color(0xFFE8C8FF), u * 1.5f, wisp)
                    }
                }
                4 -> { // RIGHT ARM + 3D WEAPON (Club, Sword, Battleaxe, Cleaver, Trident, Staff, Greatsword)
                    val sh = facing.project(ox, oy, u, +shoulderR, 0f, shoulderUp - 1.5f)
                    val swingF = 4.6f + stride * 2.2f + lunge * 6.5f
                    val swingUp = hipUp + 5.0f + lunge * 4.5f
                    val hand = facing.project(ox, oy, u, +shoulderR + 1.2f, swingF, swingUp)
                    val armR = if (species == BipedMonsterStyle.SKELETON) u * 1.35f else u * 2.5f
                    VolumetricShading.drawVolumetricCylinder(
                        this, sh, hand, armR, armR * 0.85f,
                        main = skinMain, highlight = skinHi, shadow = skinSh,
                        outlineW = ow * 0.85f, lightX = facing.lightScreenX, path = path
                    )

                    val tip = facing.project(ox, oy, u, +shoulderR + 3.2f, swingF + 9.5f + lunge * 5.0f, swingUp + 12.5f - lunge * 6.0f)
                    when (species) {
                        BipedMonsterStyle.SWAMP_TROLL, BipedMonsterStyle.CYCLOPS -> {
                            VolumetricShading.drawVolumetricCylinder(
                                this, hand, tip, u * 1.5f, u * 3.6f,
                                main = Color(0xFF754C29), highlight = Color(0xFFA87648), shadow = Color(0xFF3E2410),
                                outlineW = ow * 0.9f, lightX = facing.lightScreenX, path = path
                            )
                        }
                        BipedMonsterStyle.SKELETON, BipedMonsterStyle.ORC_WARRIOR, BipedMonsterStyle.GOBLIN_SCOUT -> {
                            VolumetricShading.drawVolumetricCylinder(
                                this, hand, tip, u * 1.35f, u * 0.75f,
                                main = Color(0xFF9EA8B8), highlight = Color(0xFFE8F0FC), shadow = Color(0xFF4F5866),
                                outlineW = ow * 0.85f, lightX = facing.lightScreenX, path = path
                            )
                        }
                        BipedMonsterStyle.MINOTAUR -> {
                            VolumetricShading.drawVolumetricCylinder(
                                this, hand, tip, u * 1.1f, u * 1.1f,
                                main = Color(0xFF6B4323), outlineW = ow * 0.8f, lightX = facing.lightScreenX, path = path
                            )
                            VolumetricShading.drawVolumetricEllipsoid(
                                this, tip.x, tip.y, u * 4.4f, u * 3.2f,
                                main = Color(0xFF9DA8B8), highlight = Color(0xFFF0F5FC), shadow = Color(0xFF47505E),
                                outlineW = ow * 0.9f, lightX = facing.lightScreenX, lightY = facing.lightScreenY, useSpecular = useSpec
                            )
                        }
                        BipedMonsterStyle.FIRE_DEVIL, BipedMonsterStyle.DEMON_LORD -> {
                            val bladeW = if (species == BipedMonsterStyle.DEMON_LORD) u * 2.5f else u * 1.4f
                            VolumetricShading.drawVolumetricCylinder(
                                this, hand, tip, bladeW, bladeW * 0.55f,
                                main = Color(0xFFFF6D26), highlight = Color(0xFFFFEE88), shadow = Color(0xFF6B140C),
                                outlineW = ow * 0.9f, lightX = facing.lightScreenX, path = path
                            )
                        }
                        BipedMonsterStyle.NECROMANCER -> {
                            val staffBot = facing.project(ox, oy, u, +shoulderR + 1.0f, swingF - 1.5f, 2.0f)
                            VolumetricShading.drawVolumetricCylinder(
                                this, staffBot, tip, u * 1.15f, u * 1.05f,
                                main = Color(0xFF3E2924), highlight = Color(0xFF6E4B42), shadow = INK,
                                outlineW = ow * 0.8f, lightX = facing.lightScreenX, path = path
                            )
                            VolumetricShading.drawVolumetricEllipsoid(
                                this, tip.x, tip.y, u * 2.6f, u * 2.4f,
                                main = Color(0xFFE6DEC8), highlight = Color(0xFFD08CFF), shadow = Color(0xFF5A4E44),
                                outlineW = ow * 0.78f, lightX = facing.lightScreenX, lightY = facing.lightScreenY, useSpecular = useSpec
                            )
                        }
                        BipedMonsterStyle.GHOUL -> {
                            // Elongated hooked necrotic claws
                            drawLine(Color(0xFFCFD9C8), hand, tip, strokeWidth = u * 1.4f, cap = StrokeCap.Round)
                        }
                    }
                }
                6 -> { // TURNING 3D HEAD (Horns, Tusks, Ears, Single Cyclops Eye, Hood)
                    val headPos = facing.project(ox, oy, u, 0f, +1.0f, headUp)
                    val headR = when (species) {
                        BipedMonsterStyle.CYCLOPS, BipedMonsterStyle.DEMON_LORD -> u * 5.8f
                        BipedMonsterStyle.GOBLIN_SCOUT -> u * 5.2f
                        else -> u * 5.4f
                    }

                    // Goblin long pointed side-swept ears (visible across all 8 octants!)
                    if (species == BipedMonsterStyle.GOBLIN_SCOUT) {
                        for (side in -1..1 step 2) {
                            val eb = facing.project(ox, oy, u, side * 4.5f, +0.5f, headUp + 0.5f)
                            val et = facing.project(ox, oy, u, side * 10.8f, -1.0f, headUp + 2.8f)
                            VolumetricShading.drawVolumetricCylinder(
                                this, eb, et, u * 1.8f, u * 0.5f,
                                main = skinMain, highlight = skinHi, shadow = skinSh,
                                outlineW = ow * 0.8f, lightX = facing.lightScreenX, path = path
                            )
                        }
                    }

                    // Sweeping 3D Horns (Minotaur, Fire Devil, Demon Lord)
                    if (species == BipedMonsterStyle.MINOTAUR || species == BipedMonsterStyle.FIRE_DEVIL || species == BipedMonsterStyle.DEMON_LORD) {
                        val hornColor = if (species == BipedMonsterStyle.MINOTAUR) Color(0xFFE8DFC8) else Color(0xFF2B2226)
                        val hornSpan = if (species == BipedMonsterStyle.DEMON_LORD) 10.5f else 8.4f
                        for (side in -1..1 step 2) {
                            val hb = facing.project(ox, oy, u, side * 3.8f, +0.5f, headUp + 3.5f)
                            val ht = facing.project(ox, oy, u, side * hornSpan, +2.0f, headUp + 9.2f)
                            VolumetricShading.drawVolumetricCylinder(
                                this, hb, ht, u * 1.8f, u * 0.55f,
                                main = hornColor, highlight = Color(0xFFFFF4DC), shadow = INK,
                                outlineW = ow * 0.82f, lightX = facing.lightScreenX, path = path
                            )
                        }
                    }

                    VolumetricShading.drawVolumetricEllipsoid(
                        this, headPos.x, headPos.y, headR, headR * 0.94f,
                        main = skinMain, highlight = skinHi, shadow = skinSh,
                        outlineW = ow, lightX = facing.lightScreenX, lightY = facing.lightScreenY,
                        useSpecular = useSpec, useRim = useRim
                    )

                    // Face features ONLY when facing toward or sideways to the camera!
                    if (!facing.isBackFacing) {
                        when (species) {
                            BipedMonsterStyle.CYCLOPS -> {
                                // Iconic huge central eye
                                val eye = facing.project(ox, oy, u, 0f, +5.8f, headUp + 0.8f)
                                drawCircle(INK, u * 2.8f, eye)
                                drawCircle(Color(0xFFFFF8E8), u * 2.2f, eye)
                                drawCircle(Color(0xFFE04832), u * 1.25f, eye)
                                drawCircle(INK, u * 0.6f, eye)
                            }
                            BipedMonsterStyle.NECROMANCER -> {
                                // Shadowed hood void with glowing violet eyes
                                val voidP = facing.project(ox, oy, u, 0f, +4.4f, headUp - 0.2f)
                                drawOval(
                                    color = Color(0xFF0B0812),
                                    topLeft = Offset(voidP.x - u * 3.5f, voidP.y - u * 3.4f),
                                    size = Size(u * 7.0f, u * 6.8f)
                                )
                                for (side in -1..1 step 2) {
                                    val ep = facing.project(ox, oy, u, side * 1.6f, +5.4f, headUp + 0.4f)
                                    drawCircle(Color(0xFFCC7BFF), u * 1.0f, ep)
                                }
                            }
                            BipedMonsterStyle.SKELETON -> {
                                for (side in -1..1 step 2) {
                                    val ep = facing.project(ox, oy, u, side * 1.9f, +5.4f, headUp + 0.5f)
                                    drawCircle(INK, u * 1.45f, ep)
                                    drawCircle(Color(0xFF66FFE3), u * 0.55f, ep)
                                }
                                val jaw = facing.project(ox, oy, u, 0f, +5.4f, headUp - 2.6f)
                                drawLine(INK, Offset(jaw.x - u * 2.0f, jaw.y), Offset(jaw.x + u * 2.0f, jaw.y), strokeWidth = ow * 0.85f)
                            }
                            else -> {
                                val eyeColor = when (species) {
                                    BipedMonsterStyle.DEMON_LORD, BipedMonsterStyle.FIRE_DEVIL -> Color(0xFFFFD83B)
                                    BipedMonsterStyle.GHOUL -> Color(0xFF8CFF7A)
                                    BipedMonsterStyle.GOBLIN_SCOUT -> Color(0xFFFFE04A)
                                    else -> Color(0xFFFF5238)
                                }
                                for (side in -1..1 step 2) {
                                    val ep = facing.project(ox, oy, u, side * 2.1f, +5.4f, headUp + 0.8f)
                                    drawCircle(INK, u * 1.3f, ep)
                                    drawCircle(eyeColor, u * 0.9f, ep)
                                }
                                // Tusks for Orc / Troll / Minotaur nose ring
                                if (species == BipedMonsterStyle.ORC_WARRIOR || species == BipedMonsterStyle.SWAMP_TROLL) {
                                    for (side in -1..1 step 2) {
                                        val tk = facing.project(ox, oy, u, side * 2.0f, +5.8f, headUp - 2.4f)
                                        drawCircle(Color(0xFFF5EED6), u * 1.05f, tk)
                                        drawCircle(INK, u * 1.05f, tk, style = Stroke(ow * 0.55f))
                                    }
                                } else if (species == BipedMonsterStyle.MINOTAUR) {
                                    val ring = facing.project(ox, oy, u, 0f, +6.6f, headUp - 2.2f)
                                    drawCircle(Color(0xFFF0C450), u * 1.55f, ring, style = Stroke(u * 0.85f))
                                }
                            }
                        }
                    } else if (species == BipedMonsterStyle.ORC_WARRIOR || species == BipedMonsterStyle.SWAMP_TROLL) {
                        // Back-of-head dark hair / topknot detail visible when viewed from behind
                        val braid = facing.project(ox, oy, u, 0f, -4.6f, headUp + 1.2f)
                        VolumetricShading.drawVolumetricEllipsoid(
                            this, braid.x, braid.y, u * 2.8f, u * 3.4f,
                            main = Color(0xFF28201B), highlight = Color(0xFF4D3F35), shadow = INK,
                            outlineW = ow * 0.78f, lightX = facing.lightScreenX, lightY = facing.lightScreenY, useSpecular = false
                        )
                    }
                }
            }
        }
    }

    /** Zero-allocation in-place insertion sort of small 3D layer index arrays by depth. */
    private fun sortIndices(order: IntArray, depths: FloatArray) {
        for (i in 1 until order.size) {
            val keyIdx = order[i]
            val keyD = depths[keyIdx]
            var j = i - 1
            while (j >= 0 && depths[order[j]] > keyD) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = keyIdx
        }
    }
}
