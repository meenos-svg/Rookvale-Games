package com.example.ui.screens

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.game.model.DEFAULT_TIME_OF_DAY_HOURS
import com.example.game.model.GraphicsQuality
import com.example.game.model.LightingDetailMode
import com.example.game.model.ProjectileKind
import com.example.game.model.TileType
import com.example.game.world.WorldFloor
import com.example.ui.theme.FireOrange
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Dynamic point-light descriptor collected during the chunk-culled viewport pass
 * and rendered cohesively over the twilight atmosphere in High & HD modes.
 */
data class ScenePointLight(
    val screenX: Float,
    val screenY: Float,
    val radiusPx: Float,
    val coreColor: Color,
    val outerColor: Color,
    val intensity: Float,
    val flickerSeed: Float,
    val isArcane: Boolean = false
)

/**
 * Smoothly interpolated local 24-hour atmospheric lighting grade for overland rendering.
 */
data class TimeOfDayLightingProfile(
    val normalizedHours: Float,
    val phaseLabel: String,
    val ambientOverlay: Color,
    val horizonStartColor: Color,
    val horizonEndColor: Color,
    val pointLightBoost: Float,
    val hdVignetteScale: Float,
    val darknessFactor: Float
)

/**
 * 360-degree yaw-aware Directional Twilight Lighting, Base Ambient Occlusion, Angled Cast Shadows,
 * Dynamic Point-Light Pool Compositor, and Android 13+ AGSL RuntimeShader HD Post-Processor.
 */
object IsometricLightingEngine {

    // Canonical world-space twilight directional light vector (normalized, from NW-up at ~38 deg)
    private const val RAW_LX = -0.68f
    private const val RAW_LY = -0.52f
    private const val RAW_LZ = 0.52f
    private val NORM_LEN = sqrt(RAW_LX * RAW_LX + RAW_LY * RAW_LY + RAW_LZ * RAW_LZ)
    val LIGHT_DIR_X: Float = RAW_LX / NORM_LEN
    val LIGHT_DIR_Y: Float = RAW_LY / NORM_LEN
    val LIGHT_DIR_Z: Float = RAW_LZ / NORM_LEN

    // World-space shadow cast direction (opposite horizontal component of incoming light)
    val SHADOW_DIR_WORLD_X: Float = -LIGHT_DIR_X
    val SHADOW_DIR_WORLD_Y: Float = -LIGHT_DIR_Y

    /**
     * Computes world-space directional lighting for any 3D face normal `(nx, ny, nz)`.
     * Because normals and light direction live in world coordinates, rotating `cameraYaw` 360°
     * naturally reveals warm-silver twilight-lit faces and cool indigo-shaded faces consistently.
     */
    fun shadeColorForFace(
        baseColor: Color,
        nx: Float,
        ny: Float,
        nz: Float,
        isOverland: Boolean,
        quality: GraphicsQuality
    ): Color {
        if (!quality.useDirectionalLighting) return baseColor
        val ndotl = (nx * LIGHT_DIR_X + ny * LIGHT_DIR_Y + nz * LIGHT_DIR_Z).coerceIn(-1f, 1f)
        val hdBoost = if (quality.drawHdStonePolish) 1.12f else 1.0f

        return if (ndotl >= 0f) {
            // Subtle warm-lavender twilight rim highlight on light-facing sides
            val lift = ndotl * (if (isOverland) 0.22f else 0.16f) * hdBoost
            val tintR = if (isOverland) 0.98f else 0.96f
            val tintG = if (isOverland) 0.95f else 0.92f
            val tintB = if (isOverland) 1.00f else 0.94f
            Color(
                red = (baseColor.red + (tintR - baseColor.red) * lift).coerceIn(0f, 1f),
                green = (baseColor.green + (tintG - baseColor.green) * lift).coerceIn(0f, 1f),
                blue = (baseColor.blue + (tintB - baseColor.blue) * lift).coerceIn(0f, 1f),
                alpha = baseColor.alpha
            )
        } else {
            // Cool moody dusk indigo shadow on sides turned away from directional light
            val shade = (-ndotl) * (if (isOverland) 0.44f else 0.48f) * hdBoost
            val shadowR = if (isOverland) 0.10f else 0.08f
            val shadowG = if (isOverland) 0.14f else 0.10f
            val shadowB = if (isOverland) 0.26f else 0.18f
            Color(
                red = (baseColor.red * (1f - shade) + shadowR * shade).coerceIn(0f, 1f),
                green = (baseColor.green * (1f - shade) + shadowG * shade).coerceIn(0f, 1f),
                blue = (baseColor.blue * (1f - shade) + shadowB * shade).coerceIn(0f, 1f),
                alpha = baseColor.alpha
            )
        }
    }

    /**
     * Projects the world-space angled shadow offset into screen pixels for the current [cameraYaw].
     * Rotating the camera 360° smoothly rotates every cast shadow with the world geometry!
     */
    fun castShadowScreenOffset(cam: IsometricCamera, heightTiles: Float): Offset {
        val worldShiftX = SHADOW_DIR_WORLD_X * heightTiles * 0.56f
        val worldShiftY = SHADOW_DIR_WORLD_Y * heightTiles * 0.56f
        return Offset(
            x = worldShiftX * cam.ux + worldShiftY * cam.vx,
            y = worldShiftX * cam.uy + worldShiftY * cam.vy
        )
    }

    /**
     * Draws soft directional cast shadow + tight grounding contact shadow beneath entities,
     * lampposts, fountains, and trees.
     */
    fun drawAngledCastShadow(
        scope: DrawScope,
        cam: IsometricCamera,
        cx: Float,
        cy: Float,
        radiusScale: Float = 1.0f,
        heightTiles: Float = 0.85f,
        quality: GraphicsQuality
    ) {
        if (!quality.drawContactShadows) return
        val hw = cam.halfW
        val hh = cam.halfH

        if (quality.drawCastShadows) {
            val shadowShift = castShadowScreenOffset(cam, heightTiles)
            val castW = hw * 1.18f * radiusScale
            val castH = hh * 0.72f * radiusScale
            val castCx = cx + shadowShift.x * 0.72f
            val castCy = cy + shadowShift.y * 0.72f
            // Soft outer twilight-tinted angled cast shadow
            scope.drawOval(
                color = if (quality.drawHdStonePolish) Color(0x36080D1C) else Color(0x2E090E1D),
                topLeft = Offset(castCx - castW * 0.55f, castCy - castH * 0.55f),
                size = Size(castW * 1.10f, castH * 1.10f)
            )
            // Mid angled cast shadow body
            scope.drawOval(
                color = Color(0x42060A16),
                topLeft = Offset(cx + shadowShift.x * 0.36f - castW * 0.44f, cy + shadowShift.y * 0.36f - castH * 0.44f),
                size = Size(castW * 0.88f, castH * 0.88f)
            )
        }

        // Grounding contact shadow directly under feet/base
        val w = hw * 0.84f * radiusScale
        val h = hh * 0.56f * radiusScale
        scope.drawOval(
            color = if (quality.drawHdStonePolish) Color(0x6205070E) else Color(0x5006080F),
            topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f),
            size = Size(w, h)
        )
    }

    /**
     * Draws subtle ambient occlusion shading strips along the ground seams where a walkable tile
     * borders walls, cliffs, rocks, or solid building foundations, plus soft directional wall shadow.
     */
    fun drawGroundBaseAmbientOcclusion(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        floorMap: WorldFloor,
        tx: Int,
        ty: Int,
        cx: Float,
        cy: Float,
        quality: GraphicsQuality
    ) {
        if (!quality.drawAmbientOcclusion) return
        val self = floorMap.tileAt(tx, ty)
        if (isAoOccluder(self) || self == TileType.WATER || self == TileType.LAVA) return

        val aoAlpha = if (quality.drawHdStonePolish) 0.46f else 0.36f
        val aoColor = Color(0xFF060914).copy(alpha = aoAlpha)
        val castColor = Color(0xFF060916).copy(alpha = if (quality.drawHdStonePolish) 0.32f else 0.24f)

        val nOcc = isAoOccluder(floorMap.tileAt(tx, ty - 1))
        val sOcc = isAoOccluder(floorMap.tileAt(tx, ty + 1))
        val wOcc = isAoOccluder(floorMap.tileAt(tx - 1, ty))
        val eOcc = isAoOccluder(floorMap.tileAt(tx + 1, ty))

        // Base contact AO along shared wall/cliff edges + dark directional floor shadow cast by 2.4x walls
        if (nOcc) {
            IsometricTileDrawer.drawRotatedQuad(
                scope, path, cam, cx, cy,
                fracU = 1.02f, fracV = 0.26f, offsetV = -0.37f, fillColor = aoColor
            )
            if (quality.drawCastShadows) {
                IsometricTileDrawer.drawRotatedQuad(
                    scope, path, cam, cx, cy,
                    fracU = 1.02f, fracV = 0.64f, offsetV = -0.18f, fillColor = castColor
                )
            }
        }
        if (wOcc) {
            IsometricTileDrawer.drawRotatedQuad(
                scope, path, cam, cx, cy,
                fracU = 0.26f, fracV = 1.02f, offsetU = -0.37f, fillColor = aoColor
            )
            if (quality.drawCastShadows) {
                IsometricTileDrawer.drawRotatedQuad(
                    scope, path, cam, cx, cy,
                    fracU = 0.64f, fracV = 1.02f, offsetU = -0.18f, fillColor = castColor
                )
            }
        }
        if (sOcc) {
            IsometricTileDrawer.drawRotatedQuad(
                scope, path, cam, cx, cy,
                fracU = 1.02f, fracV = 0.20f, offsetV = 0.40f, fillColor = aoColor
            )
        }
        if (eOcc) {
            IsometricTileDrawer.drawRotatedQuad(
                scope, path, cam, cx, cy,
                fracU = 0.20f, fracV = 1.02f, offsetU = 0.40f, fillColor = aoColor
            )
        }
    }

    private fun isAoOccluder(tile: TileType): Boolean = when (tile) {
        TileType.TIMBER_WALL,
        TileType.TIMBER_WINDOW_WALL,
        TileType.TEMPLE_WALL,
        TileType.STONE_WALL,
        TileType.CITY_WALL,
        TileType.TORCH_WALL,
        TileType.GATE_PILLAR,
        TileType.CAVE_WALL,
        TileType.MOUNTAIN_ROCK,
        TileType.SMELTING_FURNACE -> true
        else -> false
    }

    /**
     * Collects dynamic point lights emitted by visible architectural and environmental tiles
     * into [outLights] for cohesive twilight light-pool compositing.
     */
    fun collectTilePointLight(
        tile: TileType,
        tx: Int,
        ty: Int,
        cx: Float,
        cy: Float,
        cam: IsometricCamera,
        outLights: MutableList<ScenePointLight>
    ) {
        val unitW = cam.tileW
        val unitH = cam.tileH
        val seed = (tx * 1.7f + ty * 2.3f)
        when (tile) {
            TileType.LANTERN_POST -> outLights.add(
                ScenePointLight(
                    screenX = cx + 4f,
                    screenY = cy - unitH * 0.45f,
                    radiusPx = unitW * 2.35f,
                    coreColor = Color(0xFFFFD97D),
                    outerColor = TorchAmber,
                    intensity = 0.92f,
                    flickerSeed = seed
                )
            )
            TileType.TORCH_WALL -> outLights.add(
                ScenePointLight(
                    screenX = cx,
                    screenY = cy - unitH * 0.62f,
                    radiusPx = unitW * 2.85f,
                    coreColor = Color(0xFFFFD478),
                    outerColor = FireOrange,
                    intensity = 1.05f,
                    flickerSeed = seed
                )
            )
            TileType.GATE_PILLAR -> outLights.add(
                ScenePointLight(
                    screenX = cx,
                    screenY = cy - unitH * 0.72f,
                    radiusPx = unitW * 2.15f,
                    coreColor = Color(0xFFFFD87A),
                    outerColor = TorchAmber,
                    intensity = 0.85f,
                    flickerSeed = seed
                )
            )
            TileType.SMELTING_FURNACE -> outLights.add(
                ScenePointLight(
                    screenX = cx,
                    screenY = cy - unitH * 0.20f,
                    radiusPx = unitW * 2.55f,
                    coreColor = Color(0xFFFF9A40),
                    outerColor = Color(0xFFE64817),
                    intensity = 1.0f,
                    flickerSeed = seed * 1.4f
                )
            )
            TileType.TIMBER_WINDOW_WALL -> outLights.add(
                ScenePointLight(
                    screenX = cx,
                    screenY = cy - unitH * 0.40f,
                    radiusPx = unitW * 1.45f,
                    coreColor = TorchGold,
                    outerColor = TorchAmber,
                    intensity = 0.56f,
                    flickerSeed = seed
                )
            )
            TileType.TEMPLE_ALTAR -> outLights.add(
                ScenePointLight(
                    screenX = cx,
                    screenY = cy - unitH * 0.48f,
                    radiusPx = unitW * 2.25f,
                    coreColor = Color(0xFFFFEAB0),
                    outerColor = TorchGold,
                    intensity = 0.86f,
                    flickerSeed = seed
                )
            )
            TileType.RUNECRAFTING_TABLE -> outLights.add(
                ScenePointLight(
                    screenX = cx,
                    screenY = cy - unitH * 0.52f,
                    radiusPx = unitW * 2.50f,
                    coreColor = Color(0xFF75F0FF),
                    outerColor = Color(0xFF8C5EFF),
                    intensity = 0.96f,
                    flickerSeed = seed,
                    isArcane = true
                )
            )
            TileType.CRYSTAL_PILLAR -> outLights.add(
                ScenePointLight(
                    screenX = cx,
                    screenY = cy - unitH * 0.65f,
                    radiusPx = unitW * 1.95f,
                    coreColor = Color(0xFFA8F2FF),
                    outerColor = Color(0xFF9C6BFF),
                    intensity = 0.80f,
                    flickerSeed = seed,
                    isArcane = true
                )
            )
            TileType.ORE_VESPERITE -> outLights.add(
                ScenePointLight(
                    screenX = cx,
                    screenY = cy - unitH * 0.45f,
                    radiusPx = unitW * 1.35f,
                    coreColor = Color(0xFFE6D6FF),
                    outerColor = Color(0xFF8E63FF),
                    intensity = 0.48f,
                    flickerSeed = seed,
                    isArcane = true
                )
            )
            TileType.ORE_CINDERITE -> outLights.add(
                ScenePointLight(
                    screenX = cx,
                    screenY = cy - unitH * 0.45f,
                    radiusPx = unitW * 1.50f,
                    coreColor = Color(0xFFFFE48C),
                    outerColor = FireOrange,
                    intensity = 0.56f,
                    flickerSeed = seed * 1.2f
                )
            )
            TileType.FOUNTAIN -> outLights.add(
                ScenePointLight(
                    screenX = cx,
                    screenY = cy - unitH * 0.32f,
                    radiusPx = unitW * 2.30f,
                    coreColor = Color(0xFF7BE8FF),
                    outerColor = Color(0xFF2B88C9),
                    intensity = 0.72f,
                    flickerSeed = seed,
                    isArcane = true
                )
            )
            TileType.LAVA -> {
                if (((tx + ty) and 1) == 0) {
                    outLights.add(
                        ScenePointLight(
                            screenX = cx,
                            screenY = cy,
                            radiusPx = unitW * 1.65f,
                            coreColor = FireOrange,
                            outerColor = Color(0xFFC72B0C),
                            intensity = 0.64f,
                            flickerSeed = seed
                        )
                    )
                }
            }
            else -> Unit
        }
    }

    /**
     * Adds moving point lights for active magic runes & elemental projectiles in flight.
     */
    fun collectProjectilePointLight(
        kind: ProjectileKind,
        px: Float,
        py: Float,
        hw: Float,
        outLights: MutableList<ScenePointLight>
    ) {
        when (kind) {
            ProjectileKind.FIREBALL_AOE, ProjectileKind.FIREBALL, ProjectileKind.FIRE_BREATH -> outLights.add(
                ScenePointLight(
                    screenX = px,
                    screenY = py,
                    radiusPx = hw * 2.9f,
                    coreColor = Color(0xFFFFDC7A),
                    outerColor = FireOrange,
                    intensity = 0.95f,
                    flickerSeed = px * 0.05f
                )
            )
            ProjectileKind.ENERGY_BOLT, ProjectileKind.ARCANE_DART -> outLights.add(
                ScenePointLight(
                    screenX = px,
                    screenY = py,
                    radiusPx = hw * 2.3f,
                    coreColor = Color(0xFF8CF4FF),
                    outerColor = Color(0xFF3DA6FF),
                    intensity = 0.85f,
                    flickerSeed = px * 0.05f,
                    isArcane = true
                )
            )
            ProjectileKind.HEAL_RUNE -> outLights.add(
                ScenePointLight(
                    screenX = px,
                    screenY = py,
                    radiusPx = hw * 2.4f,
                    coreColor = Color(0xFF8CFFB8),
                    outerColor = Color(0xFF33D486),
                    intensity = 0.80f,
                    flickerSeed = px * 0.04f,
                    isArcane = true
                )
            )
            ProjectileKind.DEATH_RUNE, ProjectileKind.NECRO_BOLT -> outLights.add(
                ScenePointLight(
                    screenX = px,
                    screenY = py,
                    radiusPx = hw * 2.4f,
                    coreColor = Color(0xFFFF5C8A),
                    outerColor = Color(0xFF9E33FF),
                    intensity = 0.88f,
                    flickerSeed = px * 0.06f,
                    isArcane = true
                )
            )
            ProjectileKind.VENOM_GLOB -> outLights.add(
                ScenePointLight(
                    screenX = px,
                    screenY = py,
                    radiusPx = hw * 2.5f,
                    coreColor = Color(0xFFA8FF5C),
                    outerColor = Color(0xFF2ED95B),
                    intensity = 0.90f,
                    flickerSeed = px * 0.06f,
                    isArcane = true
                )
            )
            else -> Unit
        }
    }

    private data class TimeKeyframe(
        val hour: Float,
        val ambientR: Float,
        val ambientG: Float,
        val ambientB: Float,
        val ambientA: Float,
        val horizonStartR: Float,
        val horizonStartG: Float,
        val horizonStartB: Float,
        val horizonStartA: Float,
        val horizonEndR: Float,
        val horizonEndG: Float,
        val horizonEndB: Float,
        val horizonEndA: Float,
        val pointLightBoost: Float,
        val hdVignetteScale: Float,
        val darknessFactor: Float
    )

    private val timeKeyframes = listOf(
        // 00:00 Midnight — deep moonlit obsidian-indigo canopy, bright flickering torches/lanterns
        TimeKeyframe(
            hour = 0.0f,
            ambientR = 0.03f, ambientG = 0.06f, ambientB = 0.16f, ambientA = 0.44f,
            horizonStartR = 0.34f, horizonStartG = 0.50f, horizonStartB = 0.95f, horizonStartA = 0.16f,
            horizonEndR = 0.02f, horizonEndG = 0.03f, horizonEndB = 0.09f, horizonEndA = 0.30f,
            pointLightBoost = 1.38f, hdVignetteScale = 1.22f, darknessFactor = 1.0f
        ),
        // 06:00 Dawn — warm rose-amber morning horizon lift, gentle cool shadows
        TimeKeyframe(
            hour = 6.0f,
            ambientR = 0.96f, ambientG = 0.62f, ambientB = 0.42f, ambientA = 0.14f,
            horizonStartR = 1.00f, horizonStartG = 0.72f, horizonStartB = 0.46f, horizonStartA = 0.22f,
            horizonEndR = 0.22f, horizonEndG = 0.26f, horizonEndB = 0.44f, horizonEndA = 0.14f,
            pointLightBoost = 0.72f, hdVignetteScale = 0.68f, darknessFactor = 0.35f
        ),
        // 12:00 Noon — bright sunlit overland daylight, subtle warm sunlight glaze, subdued lamps
        TimeKeyframe(
            hour = 12.0f,
            ambientR = 1.00f, ambientG = 0.97f, ambientB = 0.86f, ambientA = 0.06f,
            horizonStartR = 1.00f, horizonStartG = 0.96f, horizonStartB = 0.78f, horizonStartA = 0.14f,
            horizonEndR = 0.92f, horizonEndG = 0.96f, horizonEndB = 1.00f, horizonEndA = 0.03f,
            pointLightBoost = 0.35f, hdVignetteScale = 0.42f, darknessFactor = 0.0f
        ),
        // 17:30 Golden Hour — rich sunset amber glow ahead of dusk
        TimeKeyframe(
            hour = 17.5f,
            ambientR = 0.95f, ambientG = 0.54f, ambientB = 0.26f, ambientA = 0.16f,
            horizonStartR = 1.00f, horizonStartG = 0.64f, horizonStartB = 0.28f, horizonStartA = 0.24f,
            horizonEndR = 0.18f, horizonEndG = 0.14f, horizonEndB = 0.32f, horizonEndA = 0.16f,
            pointLightBoost = 0.84f, hdVignetteScale = 0.82f, darknessFactor = 0.45f
        ),
        // 19:30 Dusk (Rookvale signature default twilight)
        TimeKeyframe(
            hour = 19.5f,
            ambientR = 0.05f, ambientG = 0.09f, ambientB = 0.18f, ambientA = 0.25f,
            horizonStartR = 0.43f, horizonStartG = 0.56f, horizonStartB = 1.00f, horizonStartA = 0.11f,
            horizonEndR = 0.02f, horizonEndG = 0.04f, horizonEndB = 0.10f, horizonEndA = 0.16f,
            pointLightBoost = 1.00f, hdVignetteScale = 1.00f, darknessFactor = 0.72f
        ),
        // 24:00 Midnight (seamless wrap back to 00:00)
        TimeKeyframe(
            hour = 24.0f,
            ambientR = 0.03f, ambientG = 0.06f, ambientB = 0.16f, ambientA = 0.44f,
            horizonStartR = 0.34f, horizonStartG = 0.50f, horizonStartB = 0.95f, horizonStartA = 0.16f,
            horizonEndR = 0.02f, horizonEndG = 0.03f, horizonEndB = 0.09f, horizonEndA = 0.30f,
            pointLightBoost = 1.38f, hdVignetteScale = 1.22f, darknessFactor = 1.0f
        )
    )

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun labelForTimeOfDay(hours: Float): String {
        val h = ((hours % 24f) + 24f) % 24f
        return when {
            h < 4.5f || h >= 21.5f -> "Midnight"
            h < 8.5f -> "Dawn"
            h < 16.5f -> "Noon"
            h < 18.5f -> "Golden Hour"
            else -> "Dusk"
        }
    }

    fun formatTimeOfDay(hours: Float): String {
        val wrapped = ((hours % 24f) + 24f) % 24f
        val totalMinutes = (wrapped * 60f).toInt().coerceIn(0, 23 * 60 + 59)
        val hh = totalMinutes / 60
        val mm = totalMinutes % 60
        return String.format(java.util.Locale.US, "%02d:%02d", hh, mm)
    }

    fun computeTimeOfDayProfile(hours: Float): TimeOfDayLightingProfile {
        val h = if (hours.isNaN() || hours.isInfinite()) DEFAULT_TIME_OF_DAY_HOURS else hours.coerceIn(0f, 24f)
        var left = timeKeyframes.first()
        var right = timeKeyframes.last()
        for (i in 0 until timeKeyframes.lastIndex) {
            val a = timeKeyframes[i]
            val b = timeKeyframes[i + 1]
            if (h in a.hour..b.hour) {
                left = a
                right = b
                break
            }
        }
        val span = (right.hour - left.hour).coerceAtLeast(0.001f)
        val t = ((h - left.hour) / span).coerceIn(0f, 1f)
        return TimeOfDayLightingProfile(
            normalizedHours = h,
            phaseLabel = labelForTimeOfDay(h),
            ambientOverlay = Color(
                red = lerp(left.ambientR, right.ambientR, t).coerceIn(0f, 1f),
                green = lerp(left.ambientG, right.ambientG, t).coerceIn(0f, 1f),
                blue = lerp(left.ambientB, right.ambientB, t).coerceIn(0f, 1f),
                alpha = lerp(left.ambientA, right.ambientA, t).coerceIn(0f, 1f)
            ),
            horizonStartColor = Color(
                red = lerp(left.horizonStartR, right.horizonStartR, t).coerceIn(0f, 1f),
                green = lerp(left.horizonStartG, right.horizonStartG, t).coerceIn(0f, 1f),
                blue = lerp(left.horizonStartB, right.horizonStartB, t).coerceIn(0f, 1f),
                alpha = lerp(left.horizonStartA, right.horizonStartA, t).coerceIn(0f, 1f)
            ),
            horizonEndColor = Color(
                red = lerp(left.horizonEndR, right.horizonEndR, t).coerceIn(0f, 1f),
                green = lerp(left.horizonEndG, right.horizonEndG, t).coerceIn(0f, 1f),
                blue = lerp(left.horizonEndB, right.horizonEndB, t).coerceIn(0f, 1f),
                alpha = lerp(left.horizonEndA, right.horizonEndA, t).coerceIn(0f, 1f)
            ),
            pointLightBoost = lerp(left.pointLightBoost, right.pointLightBoost, t),
            hdVignetteScale = lerp(left.hdVignetteScale, right.hdVignetteScale, t),
            darknessFactor = lerp(left.darknessFactor, right.darknessFactor, t)
        )
    }

    /**
     * Renders the time-of-day atmospheric grade (defaulting to Rookvale's signature Moody Twilight
     * at 19:30) and composites all visible flickering warm amber and cool cyan/arcane point-light
     * pools over the terrain, architecture, and characters.
     */
    fun drawTwilightAndPointLightPass(
        scope: DrawScope,
        floorId: Int,
        nowMs: Long,
        quality: GraphicsQuality,
        lights: List<ScenePointLight>,
        timeOfDayHours: Float = DEFAULT_TIME_OF_DAY_HOURS
    ) {
        val isUnderground = floorId < 0
        val isCustomTime = abs(timeOfDayHours - DEFAULT_TIME_OF_DAY_HOURS) > 0.01f
        val drawDungeonMood = isUnderground && quality != GraphicsQuality.LOW
        if (!quality.drawTwilightAtmosphere && !quality.drawPointLightPools && !drawDungeonMood && !isCustomTime) return
        val w = scope.size.width
        val h = scope.size.height
        if (w <= 0f || h <= 0f) return

        val isOverland = floorId == 0
        val isHd = quality.lightingMode == LightingDetailMode.HD_MULTI_LAYER
        val tod = computeTimeOfDayProfile(timeOfDayHours)

        // 1. Dynamic 24-hour atmospheric grade on overland; moody torchlit cavern veil underground
        if (quality.drawTwilightAtmosphere || drawDungeonMood || isCustomTime) {
            if (isOverland) {
                val hdAlphaScale = if (isHd) 1.04f else 0.92f
                val ambientColor = tod.ambientOverlay.copy(
                    alpha = (tod.ambientOverlay.alpha * hdAlphaScale).coerceIn(0f, 0.56f)
                )
                scope.drawRect(
                    color = ambientColor,
                    topLeft = Offset.Zero,
                    size = scope.size
                )
                scope.drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            tod.horizonStartColor,
                            Color.Transparent,
                            tod.horizonEndColor
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    ),
                    topLeft = Offset.Zero,
                    size = scope.size
                )
            } else {
                val dungeonTint = when {
                    isHd -> Color(0x66060812)
                    quality == GraphicsQuality.HIGH -> Color(0x58070914)
                    else -> Color(0x44080A14)
                }
                scope.drawRect(
                    color = dungeonTint,
                    topLeft = Offset.Zero,
                    size = scope.size
                )
                val vigR = maxOf(w, h) * 0.68f
                scope.drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.48f to Color(0x1A050710),
                            1.0f to Color(0x5204050C)
                        ),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = vigR
                    ),
                    topLeft = Offset.Zero,
                    size = scope.size
                )
            }
        }

        // 2. Dynamic flickering warm amber & cool cyan/arcane radial point-light pools
        val lightMultiplier = if (isOverland) tod.pointLightBoost else 1.0f
        if ((quality.drawPointLightPools || drawDungeonMood || (isOverland && isCustomTime)) && lights.isNotEmpty() && lightMultiplier > 0.05f) {
            val timeSec = nowMs * 0.001
            for (light in lights) {
                val r = light.radiusPx
                if (light.screenX + r < 0f || light.screenX - r > w ||
                    light.screenY + r < 0f || light.screenY - r > h
                ) continue

                val waveA = sin(timeSec * (if (light.isArcane) 3.8 else 7.2) + light.flickerSeed).toFloat()
                val waveB = cos(timeSec * (if (light.isArcane) 2.1 else 11.3) + light.flickerSeed * 1.7).toFloat()
                val flicker = (0.91f + 0.06f * waveA + 0.03f * waveB).coerceIn(0.80f, 1.04f)
                val effIntensity = (light.intensity * lightMultiplier).coerceIn(0.08f, 1.5f)
                val center = Offset(light.screenX, light.screenY)
                val scaledR = (r * flicker).coerceAtLeast(16f)

                if (isHd) {
                    // HD Layer 1: Wide atmospheric light pool with additive/screen radiance
                    val wideR = scaledR * 1.32f
                    scope.drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to light.outerColor.copy(alpha = (0.28f * effIntensity * flicker).coerceIn(0f, 0.62f)),
                                0.48f to light.outerColor.copy(alpha = (0.12f * effIntensity * flicker).coerceIn(0f, 0.34f)),
                                1.0f to Color.Transparent
                            ),
                            center = center,
                            radius = wideR
                        ),
                        radius = wideR,
                        center = center,
                        blendMode = BlendMode.Screen
                    )
                    // HD Layer 2: Crisp incandescent/arcane inner bloom
                    scope.drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to light.coreColor.copy(alpha = (0.44f * effIntensity * flicker).coerceIn(0f, 0.72f)),
                                0.42f to light.outerColor.copy(alpha = (0.20f * effIntensity * flicker).coerceIn(0f, 0.44f)),
                                1.0f to Color.Transparent
                            ),
                            center = center,
                            radius = scaledR * 0.78f
                        ),
                        radius = scaledR * 0.78f,
                        center = center,
                        blendMode = BlendMode.Plus
                    )
                } else {
                    // High / Custom Time tier: Single balanced glowing light pool
                    scope.drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to light.coreColor.copy(alpha = (0.34f * effIntensity * flicker).coerceIn(0f, 0.58f)),
                                0.46f to light.outerColor.copy(alpha = (0.16f * effIntensity * flicker).coerceIn(0f, 0.34f)),
                                1.0f to Color.Transparent
                            ),
                            center = center,
                            radius = scaledR
                        ),
                        radius = scaledR,
                        center = center,
                        blendMode = BlendMode.Screen
                    )
                }
            }
        }
    }
}

/**
 * HD post-processing pass featuring Android 13+ (API 33+) AGSL [RuntimeShader] atmospheric edge
 * depth contrast, twilight color-depth polish, and subtle animated micro-shimmer, with a seamless
 * Compose layered radial-gradient fallback on Android 7.0–12 (API 24–32).
 */
class HdPostProcessRenderer {
    private var agslShader: RuntimeShader? = null
    private var agslAttempted: Boolean = false

    val isRuntimeShaderSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private fun getOrInitShader(): RuntimeShader? {
        if (!isRuntimeShaderSupported) return null
        if (!agslAttempted) {
            agslAttempted = true
            agslShader = runCatching {
                RuntimeShader(HD_TWILIGHT_AGSL)
            }.getOrNull()
        }
        return agslShader
    }

    fun drawHdPostProcess(
        scope: DrawScope,
        quality: GraphicsQuality,
        floorId: Int,
        nowMs: Long,
        timeOfDayHours: Float = DEFAULT_TIME_OF_DAY_HOURS
    ) {
        if (!quality.drawHdVignette) return
        val w = scope.size.width
        val h = scope.size.height
        if (w <= 0f || h <= 0f) return

        val tod = IsometricLightingEngine.computeTimeOfDayProfile(timeOfDayHours)
        val vignetteScale = if (floorId == 0) tod.hdVignetteScale else 1.0f

        val shader = if (quality.useHdPostProcessShader) getOrInitShader() else null
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                shader.setFloatUniform("uResolution", w, h)
                shader.setFloatUniform("uTime", ((nowMs % 60_000L) / 1000f))
                shader.setFloatUniform("uIsOverland", if (floorId == 0) 1f else 0f)
                shader.setFloatUniform("uVignetteScale", vignetteScale)
                scope.drawRect(
                    brush = ShaderBrush(shader),
                    topLeft = Offset.Zero,
                    size = scope.size
                )
                return
            }
        }

        // Clean API 24-32 (or fallback) layered radial depth contrast & vignette
        val radius = maxOf(w, h) * 0.74f
        val center = Offset(w * 0.5f, h * 0.5f)
        val midAlpha = (0.16f * vignetteScale).coerceIn(0.03f, 0.28f)
        val edgeAlpha = (0.35f * vignetteScale).coerceIn(0.08f, 0.48f)
        scope.drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to if (floorId == 0) Color(0xFF9CC4FF).copy(alpha = (0.05f * vignetteScale).coerceIn(0.01f, 0.08f)) else Color.Transparent,
                    0.52f to Color.Transparent,
                    0.82f to Color(0xFF080C1A).copy(alpha = midAlpha),
                    1.0f to Color(0xFF050712).copy(alpha = edgeAlpha)
                ),
                center = center,
                radius = radius
            ),
            topLeft = Offset.Zero,
            size = scope.size
        )
    }

    companion object {
        // Android 13+ AGSL shader: time-of-day responsive edge depth vignette + subtle animated air shimmer
        private const val HD_TWILIGHT_AGSL = """
            uniform float2 uResolution;
            uniform float uTime;
            uniform float uIsOverland;
            uniform float uVignetteScale;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / max(uResolution, float2(1.0, 1.0));
                float2 centered = (uv - 0.5) * float2(1.18, 1.0);
                float dist = length(centered);

                // Smooth obsidian-indigo edge depth contrast scaled by time of day
                float vignette = smoothstep(0.46, 0.96, dist);
                float shimmer = 0.5 + 0.5 * sin(uv.x * 9.0 + uv.y * 6.0 + uTime * 1.15);

                float3 edgeTint = mix(float3(0.03, 0.04, 0.09), float3(0.04, 0.06, 0.14), uIsOverland);
                float alpha = vignette * (0.34 + 0.03 * shimmer) * uVignetteScale;

                // Subtle rim lift near center on overland
                float centerLift = (1.0 - smoothstep(0.0, 0.52, dist)) * uIsOverland * 0.045 * uVignetteScale;
                float3 outColor = mix(edgeTint, float3(0.55, 0.72, 1.0), centerLift / max(alpha + centerLift, 0.001));
                float outAlpha = clamp(alpha + centerLift, 0.0, 0.48);
                return half4(outColor * outAlpha, outAlpha);
            }
        """
    }
}
