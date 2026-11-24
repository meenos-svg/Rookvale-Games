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

    /**
     * Renders the Moody Twilight atmospheric grade and composites all visible flickering warm amber
     * and cool cyan/arcane point-light pools over the terrain, architecture, and characters.
     */
    fun drawTwilightAndPointLightPass(
        scope: DrawScope,
        floorId: Int,
        nowMs: Long,
        quality: GraphicsQuality,
        lights: List<ScenePointLight>
    ) {
        val isUnderground = floorId < 0
        val drawDungeonMood = isUnderground && quality != GraphicsQuality.LOW
        if (!quality.drawTwilightAtmosphere && !quality.drawPointLightPools && !drawDungeonMood) return
        val w = scope.size.width
        val h = scope.size.height
        if (w <= 0f || h <= 0f) return

        val isOverland = floorId == 0
        val isHd = quality.lightingMode == LightingDetailMode.HD_MULTI_LAYER

        // 1. Moody Twilight on overland; tighter moody torchlit shadow veil in underground dungeons
        if (quality.drawTwilightAtmosphere || drawDungeonMood) {
            val duskTint = when {
                isOverland && isHd -> Color(0x420D162E)
                isOverland -> Color(0x38101A33)
                isHd -> Color(0x66060812)
                quality == GraphicsQuality.HIGH -> Color(0x58070914)
                else -> Color(0x44080A14)
            }
            scope.drawRect(
                color = duskTint,
                topLeft = Offset.Zero,
                size = scope.size
            )
            // Subtle cool moonlit twilight top-left horizon gradient on overland,
            // or moody subterranean edge vignette on underground floors
            if (isOverland) {
                scope.drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x1C6E8FFF),
                            Color.Transparent,
                            Color(0x28060B1A)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    ),
                    topLeft = Offset.Zero,
                    size = scope.size
                )
            } else {
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
        if ((quality.drawPointLightPools || drawDungeonMood) && lights.isNotEmpty()) {
            val timeSec = nowMs * 0.001
            for (light in lights) {
                val r = light.radiusPx
                if (light.screenX + r < 0f || light.screenX - r > w ||
                    light.screenY + r < 0f || light.screenY - r > h
                ) continue

                val waveA = sin(timeSec * (if (light.isArcane) 3.8 else 7.2) + light.flickerSeed).toFloat()
                val waveB = cos(timeSec * (if (light.isArcane) 2.1 else 11.3) + light.flickerSeed * 1.7).toFloat()
                val flicker = (0.91f + 0.06f * waveA + 0.03f * waveB).coerceIn(0.80f, 1.04f)
                val center = Offset(light.screenX, light.screenY)
                val scaledR = (r * flicker).coerceAtLeast(16f)

                if (isHd) {
                    // HD Layer 1: Wide atmospheric twilight light pool with additive/screen radiance
                    val wideR = scaledR * 1.32f
                    scope.drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to light.outerColor.copy(alpha = (0.28f * light.intensity * flicker).coerceIn(0f, 0.55f)),
                                0.48f to light.outerColor.copy(alpha = (0.12f * light.intensity * flicker).coerceIn(0f, 0.30f)),
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
                                0.0f to light.coreColor.copy(alpha = (0.44f * light.intensity * flicker).coerceIn(0f, 0.65f)),
                                0.42f to light.outerColor.copy(alpha = (0.20f * light.intensity * flicker).coerceIn(0f, 0.40f)),
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
                    // High tier: Single balanced glowing twilight light pool
                    scope.drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to light.coreColor.copy(alpha = (0.34f * light.intensity * flicker).coerceIn(0f, 0.52f)),
                                0.46f to light.outerColor.copy(alpha = (0.16f * light.intensity * flicker).coerceIn(0f, 0.30f)),
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
        nowMs: Long
    ) {
        if (!quality.drawHdVignette) return
        val w = scope.size.width
        val h = scope.size.height
        if (w <= 0f || h <= 0f) return

        val shader = if (quality.useHdPostProcessShader) getOrInitShader() else null
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                shader.setFloatUniform("uResolution", w, h)
                shader.setFloatUniform("uTime", ((nowMs % 60_000L) / 1000f))
                shader.setFloatUniform("uIsOverland", if (floorId == 0) 1f else 0f)
                scope.drawRect(
                    brush = ShaderBrush(shader),
                    topLeft = Offset.Zero,
                    size = scope.size
                )
                return
            }
        }

        // Clean API 24-32 (or fallback) layered radial twilight depth contrast & vignette
        val radius = maxOf(w, h) * 0.74f
        val center = Offset(w * 0.5f, h * 0.5f)
        scope.drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to if (floorId == 0) Color(0x0D9CC4FF) else Color.Transparent,
                    0.52f to Color.Transparent,
                    0.82f to Color(0x2A080C1A),
                    1.0f to Color(0x5A050712)
                ),
                center = center,
                radius = radius
            ),
            topLeft = Offset.Zero,
            size = scope.size
        )
    }

    companion object {
        // Android 13+ AGSL shader: moody twilight edge depth vignette + subtle animated air shimmer
        private const val HD_TWILIGHT_AGSL = """
            uniform float2 uResolution;
            uniform float uTime;
            uniform float uIsOverland;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / max(uResolution, float2(1.0, 1.0));
                float2 centered = (uv - 0.5) * float2(1.18, 1.0);
                float dist = length(centered);

                // Smooth twilight obsidian-indigo edge depth contrast
                float vignette = smoothstep(0.46, 0.96, dist);
                float shimmer = 0.5 + 0.5 * sin(uv.x * 9.0 + uv.y * 6.0 + uTime * 1.15);

                float3 edgeTint = mix(float3(0.03, 0.04, 0.09), float3(0.04, 0.06, 0.14), uIsOverland);
                float alpha = vignette * (0.34 + 0.03 * shimmer);

                // Very subtle cool moonlit rim lift near center on overland
                float centerLift = (1.0 - smoothstep(0.0, 0.52, dist)) * uIsOverland * 0.045;
                float3 outColor = mix(edgeTint, float3(0.55, 0.72, 1.0), centerLift / max(alpha + centerLift, 0.001));
                float outAlpha = clamp(alpha + centerLift, 0.0, 0.42);
                return half4(outColor * outAlpha, outAlpha);
            }
        """
    }
}
