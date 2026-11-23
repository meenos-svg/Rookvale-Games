package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.game.model.Direction
import com.example.game.model.GraphicsQuality
import com.example.game.model.MonsterType
import com.example.game.world.NpcRole
import kotlinx.coroutines.delay

/**
 * Reusable live paper-doll character preview rendered with ArticulatedHumanoidRenderer.
 * Used across the Equipment panel (with interactive tap-to-turn), HUD portrait, Battle List allies,
 * Title/Creation screen, Saved Character cards, and Oracle vocation ceremony.
 */
@Composable
fun LivePaperDollPreview(
    appearance: PaperDollAppearance,
    modifier: Modifier = Modifier,
    initialDirection: Direction = Direction.SOUTH,
    interactiveRotate: Boolean = false,
    animateIdle: Boolean = false,
    quality: GraphicsQuality = GraphicsQuality.HIGH,
    contentDesc: String = "Hero paper-doll preview"
) {
    val octantAngles = remember { floatArrayOf(0f, 45f, 90f, 135f, 180f, -135f, -90f, -45f) }
    val initialIndex = remember(initialDirection) {
        when (initialDirection) {
            Direction.SOUTH -> 0
            Direction.EAST -> 2
            Direction.NORTH -> 4
            Direction.WEST -> 6
        }
    }
    var angleIndex by remember(initialDirection) { mutableIntStateOf(initialIndex) }
    var clockMs by remember { mutableLongStateOf(0L) }
    val reusablePath = remember { Path() }

    LaunchedEffect(animateIdle) {
        if (!animateIdle) return@LaunchedEffect
        while (true) {
            clockMs = System.currentTimeMillis()
            delay(50L)
        }
    }

    val activeAngleDeg = octantAngles[angleIndex % octantAngles.size]
    val resolvedFacing = remember(activeAngleDeg) {
        ScreenFacingResolver.resolveForRelativeAngleDeg(activeAngleDeg)
    }

    val clickMod = if (interactiveRotate) {
        Modifier
            .clickable(role = Role.Button) { angleIndex = (angleIndex + 1) % octantAngles.size }
            .semantics {
                contentDescription = "$contentDesc (${resolvedFacing.octant.label} view). Tap to rotate character 8 ways."
            }
    } else {
        Modifier.semantics { contentDescription = contentDesc }
    }

    Box(modifier = modifier.then(clickMod)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.minDimension * 0.90f
            val cx = size.width * 0.5f
            val feetY = size.height * 0.89f
            // Angled studio cast shadow + tight contact occlusion pool
            drawOval(
                color = Color(0x3D060914),
                topLeft = Offset(cx - h * 0.24f, feetY - h * 0.055f),
                size = Size(h * 0.62f, h * 0.14f)
            )
            drawOval(
                color = Color.Black.copy(alpha = 0.50f),
                topLeft = Offset(cx - h * 0.26f, feetY - h * 0.055f),
                size = Size(h * 0.52f, h * 0.13f)
            )
            ArticulatedHumanoidRenderer.drawPlayer(
                scope = this,
                cx = cx,
                feetY = feetY,
                heightPx = h,
                appearance = appearance,
                facing = resolvedFacing,
                walkPhase = 0f,
                attackProgress = 0f,
                nowMs = clockMs,
                quality = quality,
                path = reusablePath
            )
        }
    }
}

/**
 * Crisp 2.5D/3D volumetric monster preview for Battle List entries and inspect modals.
 */
@Composable
fun ArticulatedMonsterPreview(
    type: MonsterType,
    modifier: Modifier = Modifier,
    animateIdle: Boolean = false,
    quality: GraphicsQuality = GraphicsQuality.MEDIUM
) {
    var clockMs by remember { mutableLongStateOf(0L) }
    val reusablePath = remember { Path() }

    LaunchedEffect(animateIdle) {
        if (!animateIdle) return@LaunchedEffect
        while (true) {
            clockMs = System.currentTimeMillis()
            delay(65L)
        }
    }

    // Classic heroic 3/4 isometric angle highlights side snout/wings/legs depth + front face
    val facing = remember { ScreenFacingResolver.resolveForRelativeAngleDeg(32f) }

    Canvas(
        modifier = modifier.semantics {
            contentDescription = "${type.displayName} portrait"
        }
    ) {
        val scale = OrganicMonsterRenderer.heightScaleFor(type).coerceIn(0.85f, 1.05f)
        val h = size.minDimension * 0.84f * scale
        val cx = size.width * 0.5f
        val feetY = size.height * 0.87f
        drawOval(
            color = Color.Black.copy(alpha = 0.44f),
            topLeft = Offset(cx - h * 0.29f, feetY - h * 0.055f),
            size = Size(h * 0.58f, h * 0.135f)
        )
        OrganicMonsterRenderer.drawMonster(
            scope = this,
            cx = cx,
            feetY = feetY,
            heightPx = h,
            type = type,
            facing = facing,
            walkPhase = 0f,
            lungeProgress = 0f,
            animSeed = type.ordinal * 41,
            nowMs = clockMs,
            quality = quality,
            path = reusablePath
        )
    }
}

/**
 * Crisp 2.5D/3D volumetric thematic NPC preview for dialogs (e.g., The Oracle, Brother Alden, Alaric).
 */
@Composable
fun ArticulatedNpcPreview(
    role: NpcRole,
    modifier: Modifier = Modifier,
    animateIdle: Boolean = true,
    quality: GraphicsQuality = GraphicsQuality.HIGH
) {
    var clockMs by remember { mutableLongStateOf(0L) }
    val reusablePath = remember { Path() }

    LaunchedEffect(animateIdle) {
        if (!animateIdle) return@LaunchedEffect
        while (true) {
            clockMs = System.currentTimeMillis()
            delay(50L)
        }
    }

    val facing = remember { ScreenFacingResolver.resolveForRelativeAngleDeg(20f) }

    Canvas(
        modifier = modifier.semantics {
            contentDescription = "${role.name.lowercase()} portrait"
        }
    ) {
        val h = size.minDimension * 0.88f
        val cx = size.width * 0.5f
        val feetY = size.height * 0.89f
        drawOval(
            color = Color.Black.copy(alpha = 0.46f),
            topLeft = Offset(cx - h * 0.28f, feetY - h * 0.06f),
            size = Size(h * 0.56f, h * 0.14f)
        )
        ArticulatedHumanoidRenderer.drawNpc(
            scope = this,
            cx = cx,
            feetY = feetY,
            heightPx = h,
            role = role,
            facing = facing,
            nowMs = clockMs,
            quality = quality,
            path = reusablePath
        )
    }
}
