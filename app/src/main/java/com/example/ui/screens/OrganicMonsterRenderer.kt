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
import com.example.game.model.MonsterType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Crisp dark-ink-outlined, directionally shaded organic vector renderer for all 17 Rookvale
 * MonsterType species. Replaces blocky pixel rectangles with multi-part articulated creatures
 * featuring idle breathing, leg/wing/tail gait cycles, attack lunges, and quality-scaled effects.
 */
object OrganicMonsterRenderer {

    private val INK = Color(0xFF14100E)

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

    fun drawMonster(
        scope: DrawScope,
        cx: Float,
        feetY: Float,
        heightPx: Float,
        type: MonsterType,
        facing: ResolvedScreenFacing,
        walkPhase: Float,
        lungeProgress: Float, // 0f = idle, (0f..1f) = forward attack lunge
        animSeed: Int,
        nowMs: Long,
        quality: GraphicsQuality,
        path: Path
    ) = with(scope) {
        val u = heightPx / 46f
        val outlineW = (u * 1.12f).coerceIn(1.1f, 2.7f)
        val seedRad = (animSeed % 360) * 0.01745f
        val timeSec = nowMs / 1000f
        val breath = sin(timeSec * 3.1f + seedRad) * u * 0.7f
        val stride = sin(walkPhase)
        val bob = abs(stride) * u * 1.35f
        val lungeImpulse = if (lungeProgress > 0.001f && lungeProgress < 0.999f) {
            sin(lungeProgress * PI.toFloat())
        } else 0f

        val lungeX = facing.screenDirX * lungeImpulse * u * 6.5f
        val lungeY = facing.screenDirY * lungeImpulse * u * 3.2f
        val dirSign = when (facing.quadrant) {
            ScreenFacing.LEFT -> -1f
            ScreenFacing.RIGHT -> 1f
            else -> if (facing.screenDirX < -0.15f) -1f else 1f
        }
        val baseX = cx + lungeX
        val baseY = feetY + lungeY - bob

        when (type) {
            MonsterType.WILD_RABBIT -> drawWildRabbit(baseX, baseY, u, outlineW, dirSign, stride, breath, lungeImpulse, quality, path)
            MonsterType.CAVE_RAT -> drawCaveRat(baseX, baseY, u, outlineW, dirSign, stride, breath, lungeImpulse, quality, path)
            MonsterType.YOUNG_SNAKE -> drawYoungSnake(baseX, baseY, u, outlineW, dirSign, timeSec + seedRad, lungeImpulse, quality, path)
            MonsterType.CRAG_WOLF -> drawCragWolf(baseX, baseY, u, outlineW, dirSign, stride, breath, lungeImpulse, quality, path)
            MonsterType.POISON_SPIDER -> drawPoisonSpider(baseX, baseY, u, outlineW, stride, timeSec + seedRad, lungeImpulse, quality, path)
            MonsterType.SWAMP_TROLL -> drawSwampTroll(baseX, baseY, u, outlineW, dirSign, stride, breath, lungeImpulse, quality, path)
            MonsterType.SKELETON -> drawSkeleton(baseX, baseY, u, outlineW, dirSign, stride, breath, lungeImpulse, quality, path)
            MonsterType.GOBLIN_SCOUT -> drawGoblinScout(baseX, baseY, u, outlineW, dirSign, stride, breath, lungeImpulse, quality, path)
            MonsterType.MINOTAUR -> drawMinotaur(baseX, baseY, u, outlineW, dirSign, stride, breath, lungeImpulse, quality, path)
            MonsterType.ORC_WARRIOR -> drawOrcWarrior(baseX, baseY, u, outlineW, dirSign, stride, breath, lungeImpulse, quality, path)
            MonsterType.GHOUL -> drawGhoul(baseX, baseY, u, outlineW, dirSign, stride, breath, lungeImpulse, timeSec + seedRad, quality, path)
            MonsterType.FIRE_DEVIL -> drawFireDevil(baseX, baseY, u, outlineW, dirSign, stride, breath, lungeImpulse, timeSec + seedRad, quality, path)
            MonsterType.CYCLOPS -> drawCyclops(baseX, baseY, u, outlineW, dirSign, stride, breath, lungeImpulse, quality, path)
            MonsterType.DRAGON_HATCHLING -> drawDragon(baseX, baseY, u, outlineW, dirSign, stride, timeSec + seedRad, lungeImpulse, isElder = false, quality, path)
            MonsterType.NECROMANCER -> drawNecromancer(baseX, baseY, u, outlineW, dirSign, stride, breath, lungeImpulse, timeSec + seedRad, quality, path)
            MonsterType.DEMON_LORD -> drawDemonLord(baseX, baseY, u, outlineW, dirSign, stride, breath, lungeImpulse, timeSec + seedRad, quality, path)
            MonsterType.ELDER_DRAGON -> drawDragon(baseX, baseY, u, outlineW, dirSign, stride, timeSec + seedRad, lungeImpulse, isElder = true, quality, path)
        }

        // Crisp claw/fang slash arc on lunge for High/HD quality
        if (lungeImpulse > 0.2f && (quality == GraphicsQuality.HIGH || quality == GraphicsQuality.HD)) {
            val slashAlpha = (lungeImpulse * 0.72f).coerceIn(0f, 0.78f)
            val slashColor = when (type) {
                MonsterType.FIRE_DEVIL, MonsterType.DEMON_LORD, MonsterType.ELDER_DRAGON, MonsterType.DRAGON_HATCHLING -> Color(0xFFFF7A26)
                MonsterType.POISON_SPIDER, MonsterType.YOUNG_SNAKE, MonsterType.SWAMP_TROLL -> Color(0xFF7BFF33)
                MonsterType.NECROMANCER, MonsterType.GHOUL -> Color(0xFFC07BFF)
                else -> Color(0xFFFFE7B0)
            }
            val arcCenter = Offset(baseX + dirSign * u * 10f, baseY - u * 18f)
            drawArc(
                color = slashColor.copy(alpha = slashAlpha),
                startAngle = if (dirSign > 0f) -60f else 120f,
                sweepAngle = 115f,
                useCenter = false,
                topLeft = Offset(arcCenter.x - u * 9f, arcCenter.y - u * 9f),
                size = Size(u * 18f, u * 18f),
                style = Stroke(width = u * 2.4f, cap = StrokeCap.Round)
            )
        }
    }

    // -------------------------------------------------------------------------
    // 1. WILD RABBIT — Soft rounded fur haunches, fluffy tail, long ears, hop
    // -------------------------------------------------------------------------
    private fun DrawScope.drawWildRabbit(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        stride: Float, breath: Float, lunge: Float, quality: GraphicsQuality, path: Path
    ) {
        val furMain = Color(0xFFC9B093)
        val furHi = Color(0xFFEBD9C3)
        val furSh = Color(0xFF8B7157)
        val hopY = abs(stride) * u * 3.5f + lunge * u * 2.5f
        val y = feetY - hopY

        // Fluffy white cotton tail
        drawShadedCircle(cx - dir * u * 10.5f, y - u * 10f, u * 4.2f, Color(0xFFF6F1E8), Color.White, Color(0xFFC9C1B4), ow)

        // Muscular rounded haunch
        drawShadedOval(cx - dir * u * 4.5f, y - u * 10.5f, u * 13.5f, u * 11.5f, furMain, furHi, furSh, ow)
        // Hind paw
        drawShadedOval(cx - dir * u * 5.5f - stride * u * 2.5f, y - u * 3.5f, u * 8.5f, u * 4.2f, furHi, Color(0xFFF6ECDD), furSh, ow)

        // Torso
        drawShadedOval(cx + dir * u * 1.5f, y - u * 12.5f + breath * 0.5f, u * 14f, u * 11f, furMain, furHi, furSh, ow)
        // Front paw
        drawShadedOval(cx + dir * u * 6.5f + stride * u * 2.5f, y - u * 4f, u * 5.2f, u * 3.8f, furHi, Color(0xFFF6ECDD), furSh, ow)

        // Long ears (back ear then front ear)
        val earTwitch = sin(stride * 2f) * 6f
        val headX = cx + dir * u * 7.5f
        val headY = y - u * 19f + breath * 0.5f
        withTransform({ rotate(dir * (-18f + earTwitch), Offset(headX - dir * u * 2f, headY - u * 3f)) }) {
            drawShadedOval(headX - dir * u * 2.5f, headY - u * 11f, u * 4.6f, u * 12.5f, furSh, furMain, furSh, ow)
        }
        withTransform({ rotate(dir * (-6f - earTwitch * 0.6f), Offset(headX, headY - u * 3f)) }) {
            drawShadedOval(headX - dir * u * 0.5f, headY - u * 11.5f, u * 4.8f, u * 13f, furMain, furHi, furSh, ow)
            drawOval(Color(0xFFE6A2A8), Offset(headX - dir * u * 0.5f - u * 1.2f, headY - u * 15.5f), Size(u * 2.4f, u * 8.5f))
        }

        // Head
        drawShadedCircle(headX, headY, u * 6.5f, furMain, furHi, furSh, ow)
        // Bright dark eye + catchlight + pink nose
        drawCircle(INK, u * 1.35f, Offset(headX + dir * u * 2.5f, headY - u * 1.2f))
        if (quality != GraphicsQuality.LOW) {
            drawCircle(Color.White, u * 0.55f, Offset(headX + dir * u * 2.2f, headY - u * 1.6f))
        }
        drawCircle(Color(0xFFE58C96), u * 1.2f, Offset(headX + dir * u * 5.8f, headY + u * 0.6f))
    }

    // -------------------------------------------------------------------------
    // 2. CAVE RAT — Hunched furry rodent, curving pink tail, snout & whiskers
    // -------------------------------------------------------------------------
    private fun DrawScope.drawCaveRat(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        stride: Float, breath: Float, lunge: Float, quality: GraphicsQuality, path: Path
    ) {
        val furMain = Color(0xFF7D6B5D)
        val furHi = Color(0xFFA99585)
        val furSh = Color(0xFF473B33)
        val tailPink = Color(0xFFD5989B)

        // Curving pink tail
        path.reset()
        path.moveTo(cx - dir * u * 9f, feetY - u * 7f)
        path.cubicTo(
            cx - dir * u * 17f, feetY - u * 14f,
            cx - dir * u * 19f, feetY - u * 3f,
            cx - dir * u * 13f, feetY - u * 1.5f + stride * u * 1.5f
        )
        drawPath(path, INK, style = Stroke(width = u * 3.4f, cap = StrokeCap.Round))
        drawPath(path, tailPink, style = Stroke(width = u * 1.8f, cap = StrokeCap.Round))

        // Scurrying paws
        drawShadedOval(cx - dir * u * 5f - stride * u * 2.2f, feetY - u * 2.8f, u * 6f, u * 3.2f, tailPink, Color(0xFFEEC1C3), Color(0xFF9F686B), ow)
        drawShadedOval(cx + dir * u * 4.5f + stride * u * 2.2f, feetY - u * 2.8f, u * 5.5f, u * 3f, tailPink, Color(0xFFEEC1C3), Color(0xFF9F686B), ow)

        // Hunched body
        drawShadedOval(cx - dir * u * 1f, feetY - u * 9.5f + breath * 0.4f, u * 18f, u * 11.5f, furMain, furHi, furSh, ow)

        // Pointed rodent head & snout
        val hx = cx + dir * u * 8.5f
        val hy = feetY - u * 11.5f - lunge * u * 1.5f
        drawShadedCircle(hx - dir * u * 2.5f, hy - u * 5.2f, u * 3.2f, furSh, tailPink, furSh, ow)
        path.reset()
        path.moveTo(hx - dir * u * 5f, hy - u * 4.5f)
        path.quadraticTo(hx + dir * u * 1f, hy - u * 5.5f, hx + dir * u * 7.5f, hy + u * 0.5f)
        path.quadraticTo(hx + dir * u * 1.5f, hy + u * 4.2f, hx - dir * u * 5f, hy + u * 3.2f)
        path.close()
        drawPath(path, Brush.linearGradient(listOf(furHi, furMain, furSh), Offset(hx - u * 5f, hy - u * 5f), Offset(hx + u * 6f, hy + u * 4f)))
        drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))

        // Rounded front ear, crimson eye, nose & whiskers
        drawShadedCircle(hx - dir * u * 0.5f, hy - u * 4.8f, u * 3.1f, furMain, tailPink, furSh, ow)
        drawCircle(Color(0xFFE83A3A), u * 1.35f, Offset(hx + dir * u * 1.8f, hy - u * 1f))
        drawCircle(Color(0xFFE58C96), u * 1.2f, Offset(hx + dir * u * 7.4f, hy + u * 0.4f))
        if (quality != GraphicsQuality.LOW) {
            drawLine(INK, Offset(hx + dir * u * 5.5f, hy + u * 1.4f), Offset(hx + dir * u * 9.5f, hy + u * 3.2f), strokeWidth = ow * 0.7f)
            drawLine(INK, Offset(hx + dir * u * 5.5f, hy + u * 0.6f), Offset(hx + dir * u * 9.8f, hy - u * 1.2f), strokeWidth = ow * 0.7f)
        }
    }

    // -------------------------------------------------------------------------
    // 3. YOUNG SNAKE — S-coiled scaled viper, flared hood, flickering tongue
    // -------------------------------------------------------------------------
    private fun DrawScope.drawYoungSnake(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        t: Float, lunge: Float, quality: GraphicsQuality, path: Path
    ) {
        val scaleMain = Color(0xFF3FA34D)
        val scaleHi = Color(0xFF7BE06B)
        val scaleSh = Color(0xFF1F5D2B)
        val belly = Color(0xFFCFE67E)
        val sway = sin(t * 4.5f) * u * 2.4f

        // Coiled base
        drawShadedOval(cx - dir * u * 2f, feetY - u * 4.5f, u * 20f, u * 7.5f, scaleMain, scaleHi, scaleSh, ow)
        drawShadedOval(cx - dir * u * 1f, feetY - u * 8.5f, u * 15f, u * 6.5f, scaleMain, belly, scaleSh, ow)

        // Sinuous rising neck
        val headX = cx + dir * (u * 5.5f + lunge * u * 5f) + sway * 0.6f
        val headY = feetY - u * 22f + sin(t * 3.6f) * u * 1.2f
        path.reset()
        path.moveTo(cx - dir * u * 2f, feetY - u * 9f)
        path.cubicTo(
            cx - dir * u * 9f + sway, feetY - u * 15f,
            cx + dir * u * 4f - sway * 0.5f, feetY - u * 18f,
            headX, headY
        )
        drawPath(path, INK, style = Stroke(width = u * 7.8f, cap = StrokeCap.Round))
        drawPath(path, scaleMain, style = Stroke(width = u * 5.4f, cap = StrokeCap.Round))
        drawPath(path, scaleHi.copy(alpha = 0.7f), style = Stroke(width = u * 2.1f, cap = StrokeCap.Round))

        // Flared viper hood behind head
        drawShadedOval(headX - dir * u * 1.8f, headY + u * 2.5f, u * 10.5f, u * 7.2f, scaleMain, belly, scaleSh, ow)

        // Angular viper head
        path.reset()
        path.moveTo(headX - dir * u * 5.5f, headY - u * 2.5f)
        path.lineTo(headX + dir * u * 6.5f, headY - u * 0.5f)
        path.lineTo(headX + dir * u * 2.5f, headY + u * 3.5f)
        path.lineTo(headX - dir * u * 5.5f, headY + u * 2.5f)
        path.close()
        drawPath(path, Brush.linearGradient(listOf(scaleHi, scaleMain, scaleSh), Offset(headX - u * 5f, headY - u * 3f), Offset(headX + u * 6f, headY + u * 4f)))
        drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))

        // Slitted golden eye
        drawCircle(Color(0xFFFFD84D), u * 1.6f, Offset(headX + dir * u * 1.4f, headY - u * 0.6f))
        drawLine(INK, Offset(headX + dir * u * 1.4f, headY - u * 1.8f), Offset(headX + dir * u * 1.4f, headY + u * 0.6f), strokeWidth = ow * 0.8f)

        // Flickering forked crimson tongue
        if (sin(t * 7f) > -0.2f || lunge > 0.1f) {
            val tx = headX + dir * u * 6.5f
            val ty = headY + u * 0.5f
            val tipX = tx + dir * u * 4.5f
            drawLine(Color(0xFFE63946), Offset(tx, ty), Offset(tipX, ty + u * 0.6f), strokeWidth = ow * 0.9f, cap = StrokeCap.Round)
            drawLine(Color(0xFFE63946), Offset(tipX, ty + u * 0.6f), Offset(tipX + dir * u * 2f, ty - u * 0.8f), strokeWidth = ow * 0.7f)
            drawLine(Color(0xFFE63946), Offset(tipX, ty + u * 0.6f), Offset(tipX + dir * u * 2f, ty + u * 2.1f), strokeWidth = ow * 0.7f)
        }
    }

    // -------------------------------------------------------------------------
    // 4. CRAG WOLF — Quadruped muscular body, jagged mane, snout, glowing eyes
    // -------------------------------------------------------------------------
    private fun DrawScope.drawCragWolf(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        stride: Float, breath: Float, lunge: Float, quality: GraphicsQuality, path: Path
    ) {
        val furMain = Color(0xFF72767D)
        val furHi = Color(0xFFA7ACB5)
        val furSh = Color(0xFF3E4148)
        val maneCol = Color(0xFF4F535C)
        val bodyY = feetY - u * 16.5f + breath * 0.6f

        // Bushy tail
        path.reset()
        path.moveTo(cx - dir * u * 11f, bodyY - u * 1f)
        path.quadraticTo(cx - dir * u * 19f, bodyY - u * 7f + stride * u * 2f, cx - dir * u * 21f, bodyY + u * 1f)
        path.quadraticTo(cx - dir * u * 15f, bodyY + u * 3.5f, cx - dir * u * 10f, bodyY + u * 2f)
        path.close()
        drawPath(path, furSh)
        drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))

        // Far legs (back & front)
        drawQuadLeg(cx - dir * u * 7.5f, bodyY + u * 3f, feetY, -stride * u * 4.5f, u, ow, furSh, furMain)
        drawQuadLeg(cx + dir * u * 7f, bodyY + u * 3f, feetY, stride * u * 4.5f, u, ow, furSh, furMain)

        // Muscular quadruped torso
        drawShadedOval(cx, bodyY, u * 25f, u * 12.5f, furMain, furHi, furSh, ow)

        // Near legs (back & front)
        drawQuadLeg(cx - dir * u * 9f, bodyY + u * 3.5f, feetY, stride * u * 5f, u, ow, furMain, furHi)
        drawQuadLeg(cx + dir * u * 5.5f, bodyY + u * 3.5f, feetY, -stride * u * 5f + lunge * u * 3f, u, ow, furMain, furHi)

        // Jagged fur mane around shoulders
        val neckX = cx + dir * u * 9.5f
        val neckY = bodyY - u * 3f
        path.reset()
        path.moveTo(neckX - dir * u * 5f, neckY - u * 5f)
        path.lineTo(neckX + dir * u * 1f, neckY - u * 9f)
        path.lineTo(neckX + dir * u * 6f, neckY - u * 4f)
        path.lineTo(neckX + dir * u * 5f, neckY + u * 5f)
        path.lineTo(neckX - dir * u * 2f, neckY + u * 7f)
        path.lineTo(neckX - dir * u * 6f, neckY + u * 2f)
        path.close()
        drawPath(path, maneCol)
        drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))

        // Pointed wolf head, ears & snout
        val hx = cx + dir * u * 14f
        val hy = bodyY - u * 6.5f - lunge * u * 1.2f
        // Pointed ears
        path.reset()
        path.moveTo(hx - dir * u * 3.5f, hy - u * 3.5f)
        path.lineTo(hx - dir * u * 5f, hy - u * 10.5f)
        path.lineTo(hx - dir * u * 0.5f, hy - u * 4.5f)
        path.close()
        drawPath(path, furSh)
        drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))

        // Head + snout
        path.reset()
        path.moveTo(hx - dir * u * 5f, hy - u * 5f)
        path.lineTo(hx + dir * u * 3f, hy - u * 4.5f)
        path.lineTo(hx + dir * u * 8.5f, hy - u * 0.5f)
        path.lineTo(hx + dir * u * 7f, hy + u * 2.8f)
        path.lineTo(hx - dir * u * 3.5f, hy + u * 3.8f)
        path.close()
        drawPath(path, Brush.linearGradient(listOf(furHi, furMain, furSh), Offset(hx - u * 5f, hy - u * 5f), Offset(hx + u * 8f, hy + u * 4f)))
        drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))

        // Front ear
        path.reset()
        path.moveTo(hx - dir * u * 1.5f, hy - u * 4f)
        path.lineTo(hx - dir * u * 2.2f, hy - u * 10.5f)
        path.lineTo(hx + dir * u * 1.5f, hy - u * 4f)
        path.close()
        drawPath(path, furMain)
        drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))

        // Glowing amber eye & fangs
        if (quality != GraphicsQuality.LOW) {
            drawCircle(Color(0xFFFFB830).copy(alpha = 0.4f), u * 2.6f, Offset(hx + dir * u * 2f, hy - u * 1.6f))
        }
        drawCircle(Color(0xFFFFCF40), u * 1.35f, Offset(hx + dir * u * 2f, hy - u * 1.6f))
        drawCircle(INK, u * 1.15f, Offset(hx + dir * u * 8.2f, hy - u * 0.4f))
        drawLine(Color.White, Offset(hx + dir * u * 5.2f, hy + u * 2.2f), Offset(hx + dir * u * 5.4f, hy + u * 4.2f), strokeWidth = ow * 0.85f)
    }

    private fun DrawScope.drawQuadLeg(
        hipX: Float, hipY: Float, feetY: Float, swingX: Float, u: Float, ow: Float, fill: Color, highlight: Color
    ) {
        val kneeX = hipX + swingX * 0.45f
        val kneeY = (hipY + feetY) * 0.5f
        val pawX = hipX + swingX
        val pawY = feetY - u * 1.8f
        drawLine(INK, Offset(hipX, hipY), Offset(kneeX, kneeY), strokeWidth = u * 4.2f, cap = StrokeCap.Round)
        drawLine(INK, Offset(kneeX, kneeY), Offset(pawX, pawY), strokeWidth = u * 3.6f, cap = StrokeCap.Round)
        drawLine(fill, Offset(hipX, hipY), Offset(kneeX, kneeY), strokeWidth = u * 2.4f, cap = StrokeCap.Round)
        drawLine(highlight, Offset(kneeX, kneeY), Offset(pawX, pawY), strokeWidth = u * 2f, cap = StrokeCap.Round)
    }

    // -------------------------------------------------------------------------
    // 5. POISON SPIDER — Segmented cephalothorax/abdomen, 8 articulated legs
    // -------------------------------------------------------------------------
    private fun DrawScope.drawPoisonSpider(
        cx: Float, feetY: Float, u: Float, ow: Float,
        stride: Float, t: Float, lunge: Float, quality: GraphicsQuality, path: Path
    ) {
        val chitinMain = Color(0xFF2B2836)
        val chitinHi = Color(0xFF56506B)
        val chitinSh = Color(0xFF14121B)
        val venom = Color(0xFF7BFF33)
        val bodyY = feetY - u * 11f - lunge * u * 2f

        // 8 Articulated spider legs (4 left, 4 right) in alternating gait
        for (side in listOf(-1f, 1f)) {
            for (i in 0..3) {
                val spread = (i - 1.5f) * 0.55f
                val phase = if ((i + (if (side > 0f) 0 else 1)) % 2 == 0) stride else -stride
                val anchorX = cx + side * u * (3.5f + i * 0.4f)
                val anchorY = bodyY + (i - 1.5f) * u * 1.8f
                val kneeX = cx + side * u * (13.5f + abs(i - 1.5f) * 2.2f)
                val kneeY = bodyY - u * (7.5f - i * 1.4f) + phase * u * 2.4f
                val footX = cx + side * u * (19.5f - abs(i - 1.5f) * 1.4f) + spread * u * 2f
                val footY = feetY - u * (2.5f - i * 0.9f) + phase * u * 1.8f
                drawLine(INK, Offset(anchorX, anchorY), Offset(kneeX, kneeY), strokeWidth = u * 3.2f, cap = StrokeCap.Round)
                drawLine(INK, Offset(kneeX, kneeY), Offset(footX, footY), strokeWidth = u * 2.6f, cap = StrokeCap.Round)
                drawLine(chitinHi, Offset(anchorX, anchorY), Offset(kneeX, kneeY), strokeWidth = u * 1.5f, cap = StrokeCap.Round)
                drawLine(chitinMain, Offset(kneeX, kneeY), Offset(footX, footY), strokeWidth = u * 1.3f, cap = StrokeCap.Round)
            }
        }

        // Plump venom-marked abdomen (rear)
        val abdY = bodyY - u * 6.5f + sin(t * 3.5f) * u * 0.6f
        drawShadedOval(cx, abdY, u * 17.5f, u * 14f, chitinMain, chitinHi, chitinSh, ow)
        // Venomous hourglass / chevron markings
        path.reset()
        path.moveTo(cx, abdY - u * 4.5f)
        path.lineTo(cx + u * 3.5f, abdY)
        path.lineTo(cx, abdY + u * 4.5f)
        path.lineTo(cx - u * 3.5f, abdY)
        path.close()
        drawPath(path, venom)
        drawPath(path, INK, style = Stroke(width = ow * 0.7f))

        // Cephalothorax (front)
        val cephY = bodyY + u * 2.5f
        drawShadedCircle(cx, cephY, u * 6.2f, chitinMain, chitinHi, chitinSh, ow)

        // Clustered glowing green eyes
        val eyeGlow = 0.55f + 0.35f * sin(t * 5f)
        if (quality != GraphicsQuality.LOW) {
            drawCircle(venom.copy(alpha = eyeGlow * 0.4f), u * 5.2f, Offset(cx, cephY + u * 2.2f))
        }
        drawCircle(venom, u * 1.4f, Offset(cx - u * 2.2f, cephY + u * 1.8f))
        drawCircle(venom, u * 1.4f, Offset(cx + u * 2.2f, cephY + u * 1.8f))
        drawCircle(venom, u * 0.95f, Offset(cx - u * 4.1f, cephY + u * 0.8f))
        drawCircle(venom, u * 0.95f, Offset(cx + u * 4.1f, cephY + u * 0.8f))

        // Curved venom fangs
        drawLine(venom, Offset(cx - u * 2.5f, cephY + u * 5f), Offset(cx - u * 1.5f, cephY + u * 8.2f), strokeWidth = ow * 1.2f, cap = StrokeCap.Round)
        drawLine(venom, Offset(cx + u * 2.5f, cephY + u * 5f), Offset(cx + u * 1.5f, cephY + u * 8.2f), strokeWidth = ow * 1.2f, cap = StrokeCap.Round)
    }

    // -------------------------------------------------------------------------
    // 6. SWAMP TROLL — Hunched mossy brute, tusks, heavy gnarled club
    // -------------------------------------------------------------------------
    private fun DrawScope.drawSwampTroll(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        stride: Float, breath: Float, lunge: Float, quality: GraphicsQuality, path: Path
    ) {
        val skin = Color(0xFF5D7A47)
        val hi = Color(0xFF88AB68)
        val sh = Color(0xFF334725)
        val moss = Color(0xFF3A5F28)

        drawHumanoidMonsterLegs(cx, feetY, u, ow, stride, skin, hi, sh, Color(0xFF4F3823))

        // Broad hunched torso
        val torsoY = feetY - u * 23f + breath
        drawShadedOval(cx - dir * u * 1f, torsoY, u * 20f, u * 18f, skin, hi, sh, ow)
        // Mossy ragged loincloth & shoulder moss
        drawShadedOval(cx, torsoY + u * 7f, u * 17f, u * 6.5f, Color(0xFF5C4228), Color(0xFF805D3B), Color(0xFF362516), ow)
        drawShadedOval(cx - dir * u * 3f, torsoY - u * 6.5f, u * 14f, u * 6f, moss, Color(0xFF699B44), Color(0xFF1F3815), ow)

        // Heavy club arm (swings forward on lunge!)
        val armAngle = dir * (-35f + lunge * 95f + stride * 12f)
        val shoulder = Offset(cx + dir * u * 8.5f, torsoY - u * 4f)
        withTransform({ rotate(armAngle, shoulder) }) {
            drawShadedLimb(shoulder, Offset(shoulder.x + dir * u * 5f, shoulder.y + u * 11f), u * 4.5f, skin, hi, ow)
            // Gnarled wooden club
            val hand = Offset(shoulder.x + dir * u * 5.5f, shoulder.y + u * 11.5f)
            drawShadedLimb(hand, Offset(hand.x + dir * u * 12f, hand.y - u * 8f), u * 4.8f, Color(0xFF6E4A29), Color(0xFF9C6D41), ow)
            drawShadedCircle(hand.x + dir * u * 12.5f, hand.y - u * 8.5f, u * 4.2f, Color(0xFF6E4A29), Color(0xFF9C6D41), Color(0xFF3C2512), ow)
        }

        // Low-set hunched head with tusks
        val hx = cx + dir * u * 4f
        val hy = torsoY - u * 9.5f
        drawShadedCircle(hx, hy, u * 6.8f, skin, hi, sh, ow)
        drawCircle(Color(0xFFFFD147), u * 1.3f, Offset(hx + dir * u * 2.5f, hy - u * 1.2f))
        // Tusks
        drawLine(Color(0xFFF3EAD3), Offset(hx + dir * u * 3f, hy + u * 3.5f), Offset(hx + dir * u * 4.5f, hy + u * 0.5f), strokeWidth = ow * 1.2f, cap = StrokeCap.Round)
        drawLine(Color(0xFFF3EAD3), Offset(hx + dir * u * 0.5f, hy + u * 3.8f), Offset(hx + dir * u * 1.4f, hy + u * 1.2f), strokeWidth = ow * 1.1f, cap = StrokeCap.Round)
    }

    // -------------------------------------------------------------------------
    // 7. SKELETON — Articulated bone limbs, ribcage, skull, sword & buckler
    // -------------------------------------------------------------------------
    private fun DrawScope.drawSkeleton(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        stride: Float, breath: Float, lunge: Float, quality: GraphicsQuality, path: Path
    ) {
        val bone = Color(0xFFE7DEC9)
        val boneHi = Color(0xFFFAF5E8)
        val boneSh = Color(0xFFA89C84)
        val torsoY = feetY - u * 23.5f + breath * 0.5f

        // Skeletal legs
        for (side in listOf(-1f, 1f)) {
            val swing = stride * side * u * 3.8f
            val hip = Offset(cx + side * u * 3.2f, feetY - u * 15.5f)
            val knee = Offset(hip.x + swing * 0.5f, feetY - u * 8f)
            val foot = Offset(hip.x + swing, feetY - u * 1.5f)
            drawShadedLimb(hip, knee, u * 2.7f, bone, boneHi, ow)
            drawShadedLimb(knee, foot, u * 2.5f, bone, boneHi, ow)
        }

        // Pelvis & spine + ribcage
        drawShadedOval(cx, feetY - u * 15.5f, u * 10.5f, u * 4.8f, bone, boneHi, boneSh, ow)
        drawShadedLimb(Offset(cx, feetY - u * 16f), Offset(cx, torsoY - u * 6f), u * 2.6f, bone, boneHi, ow)
        for (r in 0..2) {
            val ry = torsoY - u * 4f + r * u * 3.1f
            drawShadedOval(cx, ry, u * (11.5f - r * 1.3f), u * 2.4f, bone, boneHi, boneSh, ow)
        }

        // Offhand cracked iron buckler
        drawShadedCircle(cx - dir * u * 9f, torsoY + u * 1.5f, u * 5.6f, Color(0xFF7D7B78), Color(0xFFB3B0AA), Color(0xFF3E3C3A), ow)
        drawCircle(Color(0xFF9E6E3B), u * 1.6f, Offset(cx - dir * u * 9f, torsoY + u * 1.5f))

        // Sword arm + notched rusty blade
        val shPos = Offset(cx + dir * u * 6.5f, torsoY - u * 4.5f)
        withTransform({ rotate(dir * (-25f + lunge * 95f + stride * 10f), shPos) }) {
            val hand = Offset(shPos.x + dir * u * 5.5f, shPos.y + u * 8.5f)
            drawShadedLimb(shPos, hand, u * 2.5f, bone, boneHi, ow)
            drawShadedLimb(hand, Offset(hand.x + dir * u * 12f, hand.y - u * 10f), u * 2.4f, Color(0xFFA7AEB5), Color(0xFFE1E6EB), ow)
            drawLine(Color(0xFF8C5B2B), Offset(hand.x - dir * u * 2f, hand.y - u * 2f), Offset(hand.x + dir * u * 2.5f, hand.y + u * 2.5f), strokeWidth = u * 1.8f, cap = StrokeCap.Round)
        }

        // Skull with dark eye sockets
        val hy = torsoY - u * 11.5f
        drawShadedCircle(cx, hy, u * 6.2f, bone, boneHi, boneSh, ow)
        drawShadedOval(cx, hy + u * 4.2f, u * 6.5f, u * 4f, bone, boneHi, boneSh, ow)
        drawCircle(INK, u * 1.65f, Offset(cx + dir * u * 2.2f - u * 0.5f, hy - u * 0.5f))
        drawCircle(INK, u * 1.5f, Offset(cx + dir * u * 2.2f + u * 2.4f, hy - u * 0.5f))
        if (quality != GraphicsQuality.LOW) {
            drawCircle(Color(0xFF7CFFCB), u * 0.65f, Offset(cx + dir * u * 2.2f - u * 0.5f, hy - u * 0.5f))
            drawCircle(Color(0xFF7CFFCB), u * 0.65f, Offset(cx + dir * u * 2.2f + u * 2.4f, hy - u * 0.5f))
        }
    }

    // -------------------------------------------------------------------------
    // 8. GOBLIN SCOUT — Pointed ears, hunched agile frame, hood, sling/dagger
    // -------------------------------------------------------------------------
    private fun DrawScope.drawGoblinScout(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        stride: Float, breath: Float, lunge: Float, quality: GraphicsQuality, path: Path
    ) {
        val skin = Color(0xFF6B9943)
        val hi = Color(0xFF9AC966)
        val sh = Color(0xFF395922)
        val cloth = Color(0xFF7B5634)

        drawHumanoidMonsterLegs(cx, feetY, u * 0.88f, ow, stride, skin, hi, sh, Color(0xFF47311E))

        val torsoY = feetY - u * 20f + breath
        drawShadedOval(cx, torsoY, u * 14f, u * 13f, cloth, Color(0xFFA6784B), Color(0xFF462F1B), ow)

        // Dagger/sling arm
        val shPos = Offset(cx + dir * u * 6.5f, torsoY - u * 3f)
        withTransform({ rotate(dir * (-20f + lunge * 90f), shPos) }) {
            val hand = Offset(shPos.x + dir * u * 5f, shPos.y + u * 7f)
            drawShadedLimb(shPos, hand, u * 3.1f, skin, hi, ow)
            drawShadedLimb(hand, Offset(hand.x + dir * u * 8.5f, hand.y - u * 4f), u * 2.2f, Color(0xFFB4BCC4), Color(0xFFEDF2F7), ow)
        }

        // Head + big pointed ears
        val hx = cx + dir * u * 2f
        val hy = torsoY - u * 9.5f
        for (earSide in listOf(-1f, 1f)) {
            path.reset()
            path.moveTo(hx + earSide * u * 4.5f, hy - u * 1f)
            path.lineTo(hx + earSide * u * 12.5f, hy - u * 4.5f)
            path.lineTo(hx + earSide * u * 5f, hy + u * 2.5f)
            path.close()
            drawPath(path, skin)
            drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))
        }
        drawShadedCircle(hx, hy, u * 6.2f, skin, hi, sh, ow)
        // Tattered scout hood
        drawArc(cloth, 190f, 160f, true, Offset(hx - u * 6.8f, hy - u * 7.2f), Size(u * 13.6f, u * 11f))
        drawArc(INK, 190f, 160f, false, Offset(hx - u * 6.8f, hy - u * 7.2f), Size(u * 13.6f, u * 11f), style = Stroke(ow))
        // Glowing yellow eyes
        drawCircle(Color(0xFFFFE147), u * 1.4f, Offset(hx + dir * u * 2.4f, hy - u * 0.5f))
    }

    // -------------------------------------------------------------------------
    // 9. MINOTAUR — Horned bull-warrior, bronze fur, brass nose ring, battleaxe
    // -------------------------------------------------------------------------
    private fun DrawScope.drawMinotaur(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        stride: Float, breath: Float, lunge: Float, quality: GraphicsQuality, path: Path
    ) {
        val fur = Color(0xFF7D4B2B)
        val hi = Color(0xFFAD6D44)
        val sh = Color(0xFF472814)

        drawHumanoidMonsterLegs(cx, feetY, u, ow, stride, fur, hi, sh, Color(0xFF231B18))

        val torsoY = feetY - u * 24f + breath
        drawShadedOval(cx, torsoY, u * 21f, u * 17.5f, fur, hi, sh, ow)
        // Leather war harness
        drawLine(Color(0xFF472C16), Offset(cx - u * 8f, torsoY - u * 7f), Offset(cx + u * 8f, torsoY + u * 7f), strokeWidth = u * 3f)
        drawShadedOval(cx, torsoY + u * 7.5f, u * 16f, u * 5.2f, Color(0xFF5A371C), Color(0xFF8A5933), Color(0xFF331D0E), ow)

        // Heavy battleaxe arm
        val shPos = Offset(cx + dir * u * 9f, torsoY - u * 4f)
        withTransform({ rotate(dir * (-35f + lunge * 105f), shPos) }) {
            val hand = Offset(shPos.x + dir * u * 5.5f, shPos.y + u * 10f)
            drawShadedLimb(shPos, hand, u * 4.5f, fur, hi, ow)
            val tip = Offset(hand.x + dir * u * 14f, hand.y - u * 10f)
            drawShadedLimb(hand, tip, u * 2.2f, Color(0xFF6B4423), Color(0xFF9C693B), ow)
            // Curved double-bit steel axehead
            drawShadedOval(tip.x, tip.y, u * 8.5f, u * 10.5f, Color(0xFFA6B1BC), Color(0xFFE3EAF0), Color(0xFF56606B), ow)
        }

        // Bull head + sweeping ivory horns + brass nose ring
        val hx = cx + dir * u * 2.5f
        val hy = torsoY - u * 10.5f
        for (s in listOf(-1f, 1f)) {
            path.reset()
            path.moveTo(hx + s * u * 4.5f, hy - u * 3f)
            path.quadraticTo(hx + s * u * 12f, hy - u * 4f, hx + s * u * 13.5f, hy - u * 11.5f)
            path.quadraticTo(hx + s * u * 9f, hy - u * 7f, hx + s * u * 4.5f, hy - u * 5.5f)
            path.close()
            drawPath(path, Color(0xFFEFE4CC))
            drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))
        }
        drawShadedOval(hx, hy, u * 12.5f, u * 11.5f, fur, hi, sh, ow)
        drawShadedOval(hx + dir * u * 3.5f, hy + u * 3.2f, u * 8f, u * 5.2f, Color(0xFF5C351E), fur, sh, ow)
        drawCircle(Color(0xFFE8BC58), u * 2.1f, Offset(hx + dir * u * 5.5f, hy + u * 5.2f), style = Stroke(u * 1.1f))
        drawCircle(Color(0xFFFF5238), u * 1.35f, Offset(hx + dir * u * 2.8f, hy - u * 1.2f))
    }

    // -------------------------------------------------------------------------
    // 10. ORC WARRIOR — Green muscular fighter, pauldrons, cleaver & iron shield
    // -------------------------------------------------------------------------
    private fun DrawScope.drawOrcWarrior(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        stride: Float, breath: Float, lunge: Float, quality: GraphicsQuality, path: Path
    ) {
        val skin = Color(0xFF557C3A)
        val hi = Color(0xFF7FA85C)
        val sh = Color(0xFF2D471C)
        val iron = Color(0xFF7A838C)
        val ironHi = Color(0xFFB8C2CC)
        val ironSh = Color(0xFF42484F)

        drawHumanoidMonsterLegs(cx, feetY, u, ow, stride, skin, hi, sh, Color(0xFF3E2918))

        val torsoY = feetY - u * 23.5f + breath
        drawShadedOval(cx, torsoY, u * 19.5f, u * 16.5f, Color(0xFF594330), Color(0xFF82654A), Color(0xFF332418), ow)
        // Iron pauldrons
        drawShadedCircle(cx - u * 8.5f, torsoY - u * 4.5f, u * 4.8f, iron, ironHi, ironSh, ow)
        drawShadedCircle(cx + u * 8.5f, torsoY - u * 4.5f, u * 4.8f, iron, ironHi, ironSh, ow)

        // Bolted iron shield (offhand)
        drawShadedOval(cx - dir * u * 10.5f, torsoY + u * 2f, u * 10f, u * 13f, iron, ironHi, ironSh, ow)

        // Jagged cleaver arm
        val shPos = Offset(cx + dir * u * 8.5f, torsoY - u * 4f)
        withTransform({ rotate(dir * (-30f + lunge * 98f), shPos) }) {
            val hand = Offset(shPos.x + dir * u * 5f, shPos.y + u * 9f)
            drawShadedLimb(shPos, hand, u * 4.1f, skin, hi, ow)
            drawShadedLimb(hand, Offset(hand.x + dir * u * 12f, hand.y - u * 9f), u * 3.6f, iron, ironHi, ow)
        }

        // Fierce orc head with tusks
        val hy = torsoY - u * 11f
        drawShadedCircle(cx, hy, u * 6.5f, skin, hi, sh, ow)
        drawCircle(Color(0xFFFF4D38), u * 1.3f, Offset(cx + dir * u * 2.5f, hy - u * 1f))
        drawLine(Color(0xFFF6EFE0), Offset(cx + dir * u * 2.8f, hy + u * 3.5f), Offset(cx + dir * u * 4.2f, hy + u * 0.8f), strokeWidth = ow * 1.15f)
    }

    // -------------------------------------------------------------------------
    // 11. GHOUL — Gaunt tattered undead, outstretched claws, grave-mist wisps
    // -------------------------------------------------------------------------
    private fun DrawScope.drawGhoul(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        stride: Float, breath: Float, lunge: Float, t: Float, quality: GraphicsQuality, path: Path
    ) {
        val skin = Color(0xFF7B947E)
        val hi = Color(0xFFA7BFA9)
        val sh = Color(0xFF425245)
        val shroud = Color(0xFF4E4A59)

        if (quality != GraphicsQuality.LOW) {
            drawCircle(Color(0xFF7BFF9C).copy(alpha = 0.14f + 0.08f * sin(t * 4f)), u * 14f, Offset(cx, feetY - u * 18f))
        }

        drawHumanoidMonsterLegs(cx, feetY, u * 0.96f, ow, stride, skin, hi, sh, shroud)
        val torsoY = feetY - u * 22.5f + breath
        drawShadedOval(cx + dir * u * 1.5f, torsoY, u * 14f, u * 16f, shroud, Color(0xFF746F84), Color(0xFF282530), ow)

        // Outstretched clawed arms
        val reach = u * (9f + lunge * 7f)
        val sh1 = Offset(cx + dir * u * 5f, torsoY - u * 4f)
        val cl1 = Offset(sh1.x + dir * reach, sh1.y + u * 2f + sin(t * 5f) * u * 1.5f)
        drawShadedLimb(sh1, cl1, u * 3f, skin, hi, ow)
        drawLine(Color(0xFFE6F0D8), cl1, Offset(cl1.x + dir * u * 3.5f, cl1.y + u * 1.5f), strokeWidth = ow)
        drawLine(Color(0xFFE6F0D8), cl1, Offset(cl1.x + dir * u * 3.5f, cl1.y - u * 1.2f), strokeWidth = ow)

        val hy = torsoY - u * 10.5f
        drawShadedCircle(cx + dir * u * 2.5f, hy, u * 6.2f, skin, hi, sh, ow)
        drawCircle(Color(0xFF9BFF80), u * 1.45f, Offset(cx + dir * u * 5f, hy - u * 0.8f))
    }

    // -------------------------------------------------------------------------
    // 12. FIRE DEVIL — Lithe crimson fiend, horns, barbed tail, flaming claws
    // -------------------------------------------------------------------------
    private fun DrawScope.drawFireDevil(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        stride: Float, breath: Float, lunge: Float, t: Float, quality: GraphicsQuality, path: Path
    ) {
        val skin = Color(0xFFC93424)
        val hi = Color(0xFFFF6645)
        val sh = Color(0xFF6B140C)
        val flame = Color(0xFFFFB830)

        // Barbed tail
        path.reset()
        path.moveTo(cx - dir * u * 6f, feetY - u * 15f)
        path.cubicTo(cx - dir * u * 17f, feetY - u * 10f, cx - dir * u * 19f, feetY - u * 22f, cx - dir * u * 14f, feetY - u * 25f + sin(t * 6f) * u * 2f)
        drawPath(path, INK, style = Stroke(width = u * 3f, cap = StrokeCap.Round))
        drawPath(path, sh, style = Stroke(width = u * 1.6f, cap = StrokeCap.Round))

        drawHumanoidMonsterLegs(cx, feetY, u, ow, stride, skin, hi, sh, Color(0xFF281614))
        val torsoY = feetY - u * 23f + breath
        drawShadedOval(cx, torsoY, u * 15.5f, u * 16f, skin, hi, sh, ow)

        // Flaming clawed arm
        val shPos = Offset(cx + dir * u * 7f, torsoY - u * 4f)
        val hand = Offset(shPos.x + dir * u * (7.5f + lunge * 6f), shPos.y + u * (4f - lunge * 6f))
        drawShadedLimb(shPos, hand, u * 3.4f, skin, hi, ow)
        if (quality != GraphicsQuality.LOW) {
            drawCircle(flame.copy(alpha = 0.55f), u * (4.5f + 0.8f * sin(t * 10f)), hand)
        }
        drawCircle(Color(0xFFFFF0A8), u * 2.2f, hand)

        // Horned fiend head
        val hy = torsoY - u * 11f
        for (s in listOf(-1f, 1f)) {
            path.reset()
            path.moveTo(cx + s * u * 3.5f, hy - u * 4f)
            path.quadraticTo(cx + s * u * 8.5f, hy - u * 9f, cx + s * u * 7f, hy - u * 13.5f)
            path.quadraticTo(cx + s * u * 5f, hy - u * 9f, cx + s * u * 2f, hy - u * 5.5f)
            path.close()
            drawPath(path, Color(0xFF2E1E1D))
            drawPath(path, INK, style = Stroke(ow))
        }
        drawShadedCircle(cx, hy, u * 6.2f, skin, hi, sh, ow)
        drawCircle(flame, u * 1.5f, Offset(cx + dir * u * 2.4f, hy - u * 1f))
    }

    // -------------------------------------------------------------------------
    // 13. CYCLOPS — Towering muscular giant, single glowing eye, massive club
    // -------------------------------------------------------------------------
    private fun DrawScope.drawCyclops(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        stride: Float, breath: Float, lunge: Float, quality: GraphicsQuality, path: Path
    ) {
        val skin = Color(0xFFC48A5E)
        val hi = Color(0xFFE8B48B)
        val sh = Color(0xFF7D5031)

        drawHumanoidMonsterLegs(cx, feetY, u * 1.05f, ow, stride, skin, hi, sh, Color(0xFF52351E))

        val torsoY = feetY - u * 25f + breath
        drawShadedOval(cx, torsoY, u * 23f, u * 20f, skin, hi, sh, ow)
        // Cross-chest leather strap & loincloth
        drawLine(Color(0xFF4D2E18), Offset(cx - u * 9.5f, torsoY - u * 8f), Offset(cx + u * 9.5f, torsoY + u * 8f), strokeWidth = u * 3.6f)
        drawShadedOval(cx, torsoY + u * 8.5f, u * 18f, u * 6.5f, Color(0xFF613D22), Color(0xFF8C5A34), Color(0xFF382111), ow)

        // Massive boulder-club arm
        val shPos = Offset(cx + dir * u * 10.5f, torsoY - u * 5f)
        withTransform({ rotate(dir * (-40f + lunge * 110f), shPos) }) {
            val hand = Offset(shPos.x + dir * u * 6f, shPos.y + u * 11f)
            drawShadedLimb(shPos, hand, u * 5.2f, skin, hi, ow)
            val clubTop = Offset(hand.x + dir * u * 14f, hand.y - u * 11f)
            drawShadedLimb(hand, clubTop, u * 5.2f, Color(0xFF6B4729), Color(0xFF9C6D44), ow)
            drawShadedCircle(clubTop.x, clubTop.y, u * 5.2f, Color(0xFF6B4729), Color(0xFF9C6D44), Color(0xFF3A2311), ow)
        }

        // Giant head with ONE central eye
        val hy = torsoY - u * 12.5f
        drawShadedCircle(cx, hy, u * 7.2f, skin, hi, sh, ow)
        drawCircle(Color.White, u * 2.9f, Offset(cx + dir * u * 2f, hy - u * 1.2f))
        drawCircle(INK, u * 2.9f, Offset(cx + dir * u * 2f, hy - u * 1.2f), style = Stroke(ow * 0.9f))
        drawCircle(Color(0xFFFFB02E), u * 1.6f, Offset(cx + dir * u * 2.3f, hy - u * 1.2f))
        drawCircle(INK, u * 0.75f, Offset(cx + dir * u * 2.5f, hy - u * 1.2f))
    }

    // -------------------------------------------------------------------------
    // 14 & 17. DRAGON HATCHLING & ELDER DRAGON — Sweeping wings, serpentine neck
    // -------------------------------------------------------------------------
    private fun DrawScope.drawDragon(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        stride: Float, t: Float, lunge: Float, isElder: Boolean, quality: GraphicsQuality, path: Path
    ) {
        val scale = if (isElder) Color(0xFFB52B24) else Color(0xFF2E8A47)
        val scaleHi = if (isElder) Color(0xFFFF6E4A) else Color(0xFF66C975)
        val scaleSh = if (isElder) Color(0xFF5E100E) else Color(0xFF174A26)
        val belly = if (isElder) Color(0xFFE8B454) else Color(0xFFCDE07A)
        val wingMembrane = if (isElder) Color(0xFF7D1B18) else Color(0xFF236636)
        val flap = sin(t * (if (isElder) 4.2f else 6.2f)) * u * 5.5f
        val bodyY = feetY - u * 15f

        if (isElder && quality != GraphicsQuality.LOW) {
            drawCircle(Color(0xFFFF7A26).copy(alpha = 0.22f + 0.08f * sin(t * 5f)), u * 22f, Offset(cx, bodyY - u * 6f))
        }

        // Spiked lashing tail
        path.reset()
        path.moveTo(cx - dir * u * 9f, bodyY + u * 2f)
        path.cubicTo(
            cx - dir * u * 20f, bodyY + u * 6f,
            cx - dir * u * 23f, bodyY - u * 4f + sin(t * 4f) * u * 3f,
            cx - dir * u * 18f, bodyY - u * 9f + sin(t * 4f) * u * 3f
        )
        drawPath(path, INK, style = Stroke(width = u * 5.2f, cap = StrokeCap.Round))
        drawPath(path, scale, style = Stroke(width = u * 3.2f, cap = StrokeCap.Round))

        // Far bat-wing
        drawDragonWing(cx - dir * u * 3f, bodyY - u * 5f, dir * -1f, u, ow, flap * 0.85f, scaleSh, wingMembrane, path)

        // Clawed legs
        drawQuadLeg(cx - dir * u * 6f, bodyY + u * 4f, feetY, stride * u * 3.5f, u, ow, scaleSh, scale)
        drawQuadLeg(cx + dir * u * 5f, bodyY + u * 4f, feetY, -stride * u * 3.5f, u, ow, scale, scaleHi)

        // Scaled arched torso & golden/plated belly
        drawShadedOval(cx, bodyY, u * 21f, u * 13.5f, scale, scaleHi, scaleSh, ow)
        drawShadedOval(cx + dir * u * 2f, bodyY + u * 3f, u * 14f, u * 6.5f, belly, Color(0xFFFFE79E), scaleSh, ow * 0.8f)

        // Near sweeping bat-wing
        drawDragonWing(cx - dir * u * 1f, bodyY - u * 4f, dir, u * 1.08f, ow, flap, scale, wingMembrane, path)

        // Sinuous dragon neck & horned skull
        val hx = cx + dir * (u * 12f + lunge * u * 5f)
        val hy = bodyY - u * 15f - sin(t * 3.2f) * u * 1.5f
        path.reset()
        path.moveTo(cx + dir * u * 5f, bodyY - u * 3f)
        path.quadraticTo(cx + dir * u * 12f, bodyY - u * 9f, hx - dir * u * 2f, hy + u * 2f)
        drawPath(path, INK, style = Stroke(width = u * 8.2f, cap = StrokeCap.Round))
        drawPath(path, scale, style = Stroke(width = u * 5.6f, cap = StrokeCap.Round))

        // Crowned dragon horns
        path.reset()
        path.moveTo(hx - dir * u * 2f, hy - u * 3f)
        path.lineTo(hx - dir * u * 10f, hy - u * 10f)
        path.lineTo(hx - dir * u * 4f, hy - u * 1f)
        path.close()
        drawPath(path, if (isElder) Color(0xFFF5C85B) else Color(0xFFE8DFC5))
        drawPath(path, INK, style = Stroke(ow))

        // Dragon snout + fiery eye + ember breath on lunge
        path.reset()
        path.moveTo(hx - dir * u * 5f, hy - u * 4.5f)
        path.lineTo(hx + dir * u * 8.5f, hy - u * 1.5f)
        path.lineTo(hx + dir * u * 7f, hy + u * 3.2f)
        path.lineTo(hx - dir * u * 4.5f, hy + u * 3.5f)
        path.close()
        drawPath(path, Brush.linearGradient(listOf(scaleHi, scale, scaleSh), Offset(hx - u * 5f, hy - u * 5f), Offset(hx + u * 8f, hy + u * 4f)))
        drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))
        drawCircle(Color(0xFFFFE14D), u * 1.6f, Offset(hx + dir * u * 1.8f, hy - u * 1.2f))

        if ((lunge > 0.15f || isElder) && quality != GraphicsQuality.LOW) {
            val fireCenter = Offset(hx + dir * u * 10f, hy + u * 1.5f)
            drawCircle(Color(0xFFFF7A26).copy(alpha = if (lunge > 0.15f) 0.8f else 0.35f), u * (2.5f + lunge * 5.5f), fireCenter)
            drawCircle(Color(0xFFFFF0A0).copy(alpha = if (lunge > 0.15f) 0.9f else 0.45f), u * (1.2f + lunge * 2.8f), fireCenter)
        }
    }

    private fun DrawScope.drawDragonWing(
        rootX: Float, rootY: Float, dir: Float, u: Float, ow: Float,
        flap: Float, sparColor: Color, membrane: Color, path: Path
    ) {
        val tipX = rootX - dir * u * 16f
        val tipY = rootY - u * 15f + flap
        val midX = rootX - dir * u * 9f
        val midY = rootY - u * 4f + flap * 0.5f
        path.reset()
        path.moveTo(rootX, rootY)
        path.lineTo(rootX - dir * u * 7f, rootY - u * 13f + flap * 0.7f)
        path.lineTo(tipX, tipY)
        path.quadraticTo(tipX + dir * u * 4f, midY, midX, midY + u * 3f)
        path.quadraticTo(rootX - dir * u * 4f, rootY + u * 2f, rootX, rootY + u * 3f)
        path.close()
        drawPath(path, membrane)
        drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))
        drawLine(sparColor, Offset(rootX, rootY), Offset(tipX, tipY), strokeWidth = ow * 1.4f, cap = StrokeCap.Round)
    }

    // -------------------------------------------------------------------------
    // 15. NECROMANCER — Hooded obsidian/crimson cultist, skull staff, soul wisps
    // -------------------------------------------------------------------------
    private fun DrawScope.drawNecromancer(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        stride: Float, breath: Float, lunge: Float, t: Float, quality: GraphicsQuality, path: Path
    ) {
        val robe = Color(0xFF281D33)
        val robeHi = Color(0xFF4C3963)
        val robeSh = Color(0xFF120C18)
        val trim = Color(0xFFA82438)
        val violet = Color(0xFFC978FF)

        // Sweeping hooded robe skirt
        path.reset()
        path.moveTo(cx - u * 7.5f, feetY - u * 29f + breath)
        path.lineTo(cx + u * 7.5f, feetY - u * 29f + breath)
        path.lineTo(cx + u * 11f + stride * u * 1.2f, feetY - u * 1.5f)
        path.lineTo(cx - u * 11f - stride * u * 1.2f, feetY - u * 1.5f)
        path.close()
        drawPath(path, Brush.linearGradient(listOf(robeHi, robe, robeSh), Offset(cx - u * 10f, feetY - u * 28f), Offset(cx + u * 10f, feetY)))
        drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))
        // Crimson vertical stole
        drawLine(trim, Offset(cx, feetY - u * 28f + breath), Offset(cx, feetY - u * 3f), strokeWidth = u * 3.4f)

        // Skull-topped arcane staff
        val staffX = cx + dir * u * 10.5f
        val staffTopY = feetY - u * 39f - lunge * u * 4f
        drawShadedLimb(Offset(staffX, feetY - u * 3f), Offset(staffX, staffTopY + u * 4f), u * 2.4f, Color(0xFF4A3832), Color(0xFF7A6158), ow)
        drawShadedCircle(staffX, staffTopY, u * 3.6f, Color(0xFFE8E0CE), Color.White, Color(0xFF9C9280), ow)
        if (quality != GraphicsQuality.LOW) {
            drawCircle(violet.copy(alpha = 0.45f + 0.25f * sin(t * 7f)), u * 6.5f, Offset(staffX, staffTopY))
        }
        drawCircle(violet, u * 1.6f, Offset(staffX, staffTopY))

        // Deep pointed cultist hood & glowing eyes
        val hy = feetY - u * 34.5f + breath
        path.reset()
        path.moveTo(cx, hy - u * 9.5f)
        path.lineTo(cx + u * 8.5f, hy + u * 4.5f)
        path.lineTo(cx - u * 8.5f, hy + u * 4.5f)
        path.close()
        drawPath(path, robe)
        drawPath(path, INK, style = Stroke(width = ow, join = StrokeJoin.Round))
        drawOval(INK, Offset(cx - u * 4.5f, hy - u * 3.5f), Size(u * 9f, u * 7.5f))
        drawCircle(violet, u * 1.35f, Offset(cx - u * 1.8f + dir * u * 0.8f, hy))
        drawCircle(violet, u * 1.35f, Offset(cx + u * 1.8f + dir * u * 0.8f, hy))

        // Orbiting soul wisps on Medium/High/HD
        if (quality != GraphicsQuality.LOW) {
            val wispX = cx + cos(t * 3.2f) * u * 13f
            val wispY = feetY - u * 22f + sin(t * 3.2f) * u * 4f
            drawCircle(violet.copy(alpha = 0.35f), u * 3.6f, Offset(wispX, wispY))
            drawCircle(Color(0xFFF3D9FF), u * 1.5f, Offset(wispX, wispY))
        }
    }

    // -------------------------------------------------------------------------
    // 16. DEMON LORD — Colossal horned dreadlord, wings, molten runes, blade
    // -------------------------------------------------------------------------
    private fun DrawScope.drawDemonLord(
        cx: Float, feetY: Float, u: Float, ow: Float, dir: Float,
        stride: Float, breath: Float, lunge: Float, t: Float, quality: GraphicsQuality, path: Path
    ) {
        val skin = Color(0xFF8C1D1B)
        val hi = Color(0xFFD13A2E)
        val sh = Color(0xFF3E0A0B)
        val molten = Color(0xFFFFAE2B)

        if (quality != GraphicsQuality.LOW) {
            drawCircle(Color(0xFFFF5E1A).copy(alpha = 0.24f + 0.1f * sin(t * 5f)), u * 21f, Offset(cx, feetY - u * 22f))
        }

        val torsoY = feetY - u * 25.5f + breath
        // Jagged demonic wings behind shoulders
        drawDragonWing(cx - u * 5f, torsoY - u * 6f, 1f, u * 0.95f, ow, sin(t * 4.5f) * u * 3f, Color(0xFF2B1214), Color(0xFF4F1316), path)
        drawDragonWing(cx + u * 5f, torsoY - u * 6f, -1f, u * 0.95f, ow, sin(t * 4.5f) * u * 3f, Color(0xFF2B1214), Color(0xFF4F1316), path)

        drawHumanoidMonsterLegs(cx, feetY, u * 1.08f, ow, stride, skin, hi, sh, Color(0xFF1F1618))

        // Broad muscular dreadlord chest + glowing molten chest runes
        drawShadedOval(cx, torsoY, u * 24f, u * 20f, skin, hi, sh, ow)
        drawLine(molten, Offset(cx - u * 4.5f, torsoY - u * 4f), Offset(cx, torsoY + u * 3f), strokeWidth = u * 1.8f, cap = StrokeCap.Round)
        drawLine(molten, Offset(cx + u * 4.5f, torsoY - u * 4f), Offset(cx, torsoY + u * 3f), strokeWidth = u * 1.8f, cap = StrokeCap.Round)

        // Blazing hellfire greatsword
        val shPos = Offset(cx + dir * u * 11f, torsoY - u * 5f)
        withTransform({ rotate(dir * (-38f + lunge * 112f), shPos) }) {
            val hand = Offset(shPos.x + dir * u * 6f, shPos.y + u * 10f)
            drawShadedLimb(shPos, hand, u * 4.8f, skin, hi, ow)
            val bladeTip = Offset(hand.x + dir * u * 18f, hand.y - u * 15f)
            drawLine(INK, hand, bladeTip, strokeWidth = u * 5.6f, cap = StrokeCap.Round)
            drawLine(Color(0xFFFF5E1A), hand, bladeTip, strokeWidth = u * 3.6f, cap = StrokeCap.Round)
            drawLine(Color(0xFFFFE98A), hand, bladeTip, strokeWidth = u * 1.5f, cap = StrokeCap.Round)
        }

        // Dreadlord skull-horned head
        val hy = torsoY - u * 13f
        for (s in listOf(-1f, 1f)) {
            path.reset()
            path.moveTo(cx + s * u * 4.5f, hy - u * 3f)
            path.quadraticTo(cx + s * u * 15f, hy - u * 7f, cx + s * u * 13f, hy - u * 16.5f)
            path.quadraticTo(cx + s * u * 9f, hy - u * 10f, cx + s * u * 3f, hy - u * 6f)
            path.close()
            drawPath(path, Color(0xFF2B1B1D))
            drawPath(path, INK, style = Stroke(ow))
        }
        drawShadedCircle(cx, hy, u * 7.2f, skin, hi, sh, ow)
        drawCircle(molten, u * 1.6f, Offset(cx - u * 2.2f + dir * u * 1f, hy - u * 1f))
        drawCircle(molten, u * 1.6f, Offset(cx + u * 2.2f + dir * u * 1f, hy - u * 1f))
    }

    // -------------------------------------------------------------------------
    // Shared Shaded Organic Primitives
    // -------------------------------------------------------------------------
    private fun DrawScope.drawHumanoidMonsterLegs(
        cx: Float, feetY: Float, u: Float, ow: Float, stride: Float,
        skin: Color, hi: Color, sh: Color, bootColor: Color
    ) {
        for (side in listOf(-1f, 1f)) {
            val swing = stride * side * u * 4.2f
            val hip = Offset(cx + side * u * 4.2f, feetY - u * 15f)
            val ankle = Offset(hip.x + swing, feetY - u * 3f)
            drawShadedLimb(hip, ankle, u * 4.6f, skin, hi, ow)
            drawShadedOval(ankle.x, feetY - u * 2.4f, u * 6.5f, u * 4.2f, bootColor, skin, sh, ow)
        }
    }

    private fun DrawScope.drawShadedCircle(
        cx: Float, cy: Float, radius: Float,
        main: Color, hi: Color, sh: Color, outlineW: Float
    ) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(hi, main, sh),
                center = Offset(cx - radius * 0.35f, cy - radius * 0.35f),
                radius = radius * 1.45f
            ),
            radius = radius,
            center = Offset(cx, cy)
        )
        drawCircle(INK, radius = radius, center = Offset(cx, cy), style = Stroke(width = outlineW))
    }

    private fun DrawScope.drawShadedOval(
        cx: Float, cy: Float, width: Float, height: Float,
        main: Color, hi: Color, sh: Color, outlineW: Float
    ) {
        val topLeft = Offset(cx - width * 0.5f, cy - height * 0.5f)
        val size = Size(width, height)
        drawOval(
            brush = Brush.linearGradient(
                colors = listOf(hi, main, sh),
                start = topLeft,
                end = Offset(topLeft.x + width, topLeft.y + height)
            ),
            topLeft = topLeft,
            size = size
        )
        drawOval(INK, topLeft = topLeft, size = size, style = Stroke(width = outlineW))
    }

    private fun DrawScope.drawShadedLimb(
        start: Offset, end: Offset, thickness: Float,
        main: Color, hi: Color, outlineW: Float
    ) {
        drawLine(INK, start, end, strokeWidth = thickness + outlineW * 1.8f, cap = StrokeCap.Round)
        drawLine(main, start, end, strokeWidth = thickness, cap = StrokeCap.Round)
        drawLine(hi.copy(alpha = 0.65f), start, end, strokeWidth = thickness * 0.38f, cap = StrokeCap.Round)
    }
}
