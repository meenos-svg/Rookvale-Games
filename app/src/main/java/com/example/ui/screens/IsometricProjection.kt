package com.example.ui.screens

import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.game.model.GraphicsQuality
import com.example.game.model.LightingDetailMode
import com.example.game.model.TileType
import com.example.game.world.BuildingFootprint
import com.example.game.world.WorldFloor
import com.example.ui.theme.FireOrange
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Visible rotated tile bounds for strict 60 FPS viewport culling on 400x400 chunked maps.
 */
data class IsoBounds(
    val minTx: Int,
    val maxTx: Int,
    val minTy: Int,
    val maxTy: Int
)

/**
 * Smooth 360-degree rotatable 2.5D isometric camera with exact forward/inverse rotation matrices,
 * rotated basis vectors for 3D prisms, dynamic depth calculation, and tight rotated viewport culling.
 */
class IsometricCamera {
    var tileW: Float = 96f
    var tileH: Float = 48f
    var zoom: Float = 1f
    var yawDeg: Float = DEFAULT_YAW_DEG

    var viewportCenterX: Float = 0f
    var viewportCenterY: Float = 0f
    var camTileX: Float = 0f
    var camTileY: Float = 0f

    var cosYaw: Float = 0.70710677f
        private set
    var sinYaw: Float = 0.70710677f
        private set
    var unitScale: Float = 67.88f
        private set
    var pitchRatio: Float = 0.5f
        private set

    // Screen-space delta for +1 world tile X and +1 world tile Y
    var ux: Float = 48f
        private set
    var uy: Float = 24f
        private set
    var vx: Float = -48f
        private set
    var vy: Float = 24f
        private set

    val halfW: Float get() = tileW * 0.5f
    val halfH: Float get() = tileH * 0.5f

    fun configure(
        viewportW: Float,
        viewportH: Float,
        camTileX: Float,
        camTileY: Float,
        zoomFactor: Float,
        cameraYawDeg: Float = DEFAULT_YAW_DEG
    ) {
        zoom = zoomFactor.coerceIn(MIN_ZOOM, MAX_ZOOM)
        yawDeg = normalizeYawDeg(cameraYawDeg)
        val rad = yawDeg * (PI / 180.0)
        cosYaw = cos(rad).toFloat()
        sinYaw = sin(rad).toFloat()

        val baseTileW = (min(viewportW, viewportH) * 0.27f).coerceIn(88f, 156f)
        tileW = baseTileW * zoom
        tileH = tileW * 0.5f
        // At yaw = 45°, cos=sin=0.70710677 so unitScale * 0.70710677 == tileW * 0.5f (classic 2:1 diamond)
        unitScale = tileW * 0.70710677f
        pitchRatio = 0.5f

        this.camTileX = camTileX
        this.camTileY = camTileY
        viewportCenterX = viewportW * 0.5f
        viewportCenterY = viewportH * 0.53f

        ux = cosYaw * unitScale
        uy = sinYaw * unitScale * pitchRatio
        vx = -sinYaw * unitScale
        vy = cosYaw * unitScale * pitchRatio
    }

    fun tileCenterToScreen(tx: Float, ty: Float): Offset {
        val dx = tx - camTileX
        val dy = ty - camTileY
        return Offset(
            x = viewportCenterX + dx * ux + dy * vx,
            y = viewportCenterY + dx * uy + dy * vy
        )
    }

    /**
     * Zero-allocation projection writing screen coordinates into [outXY] (`[0] = sx, [1] = sy`).
     */
    fun tileCenterToScreenOut(tx: Float, ty: Float, outXY: FloatArray) {
        val dx = tx - camTileX
        val dy = ty - camTileY
        outXY[0] = viewportCenterX + dx * ux + dy * vx
        outXY[1] = viewportCenterY + dx * uy + dy * vy
    }

    /**
     * Fast screen-space frustum rejection accounting for tile half-dimensions and tall wall/light overhang.
     */
    fun isScreenPointInFrustum(
        sx: Float,
        sy: Float,
        viewportW: Float,
        viewportH: Float,
        marginX: Float = halfW * 2.0f,
        marginTop: Float = halfH * 2.2f,
        marginBottom: Float = halfH * 3.8f
    ): Boolean {
        return sx + marginX >= 0f &&
            sx - marginX <= viewportW &&
            sy + marginBottom >= 0f &&
            sy - marginTop <= viewportH
    }

    fun screenToTileFloat(sx: Float, sy: Float): Pair<Float, Float> {
        val scaleX = unitScale.coerceAtLeast(1f)
        val scaleY = (unitScale * pitchRatio).coerceAtLeast(1f)
        val rx = (sx - viewportCenterX) / scaleX
        val ry = (sy - viewportCenterY) / scaleY
        val dx = rx * cosYaw + ry * sinYaw
        val dy = -rx * sinYaw + ry * cosYaw
        return (camTileX + dx) to (camTileY + dy)
    }

    fun screenToTile(sx: Float, sy: Float): Pair<Int, Int> {
        val (fx, fy) = screenToTileFloat(sx, sy)
        return floor(fx + 0.5f).toInt() to floor(fy + 0.5f).toInt()
    }

    fun screenToWorldTile(sx: Float, sy: Float): Pair<Int, Int> = screenToTile(sx, sy)

    /**
     * Rotated camera-space depth for dynamic 360-degree painter's algorithm sorting.
     * Larger values are closer to the bottom of the screen (drawn later/in front).
     */
    fun depthOf(tx: Float, ty: Float): Float {
        return (tx - camTileX) * sinYaw + (ty - camTileY) * cosYaw
    }

    /**
     * Computes the tight world-tile bounding box covering the rotated & zoomed screen viewport,
     * padded by [margin] tiles (default [FRUSTUM_MARGIN_TILES] = 2) so tall walls, trees, and lights
     * entering the edges never pop in while off-screen tiles are skipped completely.
     */
    fun visibleTileBounds(
        viewportW: Float,
        viewportH: Float,
        floorWidth: Int,
        floorHeight: Int,
        margin: Int = FRUSTUM_MARGIN_TILES
    ): IsoBounds {
        // Tight vertical padding tuned for tall 2.4x walls & point lights entering bottom/top edges
        val padTop = tileH * 1.4f
        val padBottom = tileH * 2.2f
        val c0 = screenToTileFloat(0f, -padTop)
        val c1 = screenToTileFloat(viewportW, -padTop)
        val c2 = screenToTileFloat(0f, viewportH + padBottom)
        val c3 = screenToTileFloat(viewportW, viewportH + padBottom)
        val minFx = min(min(c0.first, c1.first), min(c2.first, c3.first))
        val maxFx = max(max(c0.first, c1.first), max(c2.first, c3.first))
        val minFy = min(min(c0.second, c1.second), min(c2.second, c3.second))
        val maxFy = max(max(c0.second, c1.second), max(c2.second, c3.second))

        return IsoBounds(
            minTx = (floor(minFx).toInt() - margin).coerceIn(0, (floorWidth - 1).coerceAtLeast(0)),
            maxTx = (floor(maxFx).toInt() + margin).coerceIn(0, (floorWidth - 1).coerceAtLeast(0)),
            minTy = (floor(minFy).toInt() - margin).coerceIn(0, (floorHeight - 1).coerceAtLeast(0)),
            maxTy = (floor(maxFy).toInt() + margin).coerceIn(0, (floorHeight - 1).coerceAtLeast(0))
        )
    }

    companion object {
        const val DEFAULT_YAW_DEG = 45f
        const val MIN_ZOOM = 0.70f
        const val MAX_ZOOM = 2.00f
        const val FRUSTUM_MARGIN_TILES = 2

        fun normalizeYawDeg(deg: Float): Float {
            var r = deg % 360f
            if (r < 0f) r += 360f
            return r
        }

        fun shortestAngleDeltaDeg(fromDeg: Float, toDeg: Float): Float {
            var diff = (toDeg - fromDeg) % 360f
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f
            return diff
        }
    }
}

/**
 * High-FPS 360-degree rotatable 2.5D RPG renderer:
 * - Slim connected timber-and-stone walls with foundation trim, corner beams, windows & stained glass
 * - Smooth cutaway pitched roofs when player approaches or enters buildings
 * - Rich interior props (pews, candles, chests, crates, barrels, awnings, rune circles, crystal pillars, bookshelves)
 * - Softened riverbanks with curved rocks, reeds, lily pads, animated ripples & moored harbor boats
 */
object IsometricTileDrawer {

    fun isTallProp(tile: TileType): Boolean = when (tile) {
        TileType.STONE_WALL,
        TileType.CITY_WALL,
        TileType.TORCH_WALL,
        TileType.TIMBER_WALL,
        TileType.TIMBER_WINDOW_WALL,
        TileType.TEMPLE_WALL,
        TileType.GATE_PILLAR,
        TileType.MOUNTAIN_ROCK,
        TileType.CAVE_WALL,
        TileType.TREE,
        TileType.TREE_STUMP,
        TileType.DEPOT_CHEST,
        TileType.MINI_DEPOT,
        TileType.SHOP_COUNTER,
        TileType.TEMPLE_ALTAR,
        TileType.SMELTING_FURNACE,
        TileType.BLACKSMITH_ANVIL,
        TileType.FLETCHING_BENCH,
        TileType.RUNECRAFTING_TABLE,
        TileType.ORE_COPPER,
        TileType.ORE_TIN,
        TileType.ORE_BOG_IRON,
        TileType.ORE_VESPERITE,
        TileType.ORE_CINDERITE,
        TileType.DEPLETED_VEIN,
        TileType.FOUNTAIN,
        TileType.HEDGE,
        TileType.WOOD_FENCE,
        TileType.LANTERN_POST,
        TileType.MARKET_AWNING,
        TileType.CRATE_BARREL,
        TileType.BOOKSHELF,
        TileType.CRYSTAL_PILLAR,
        TileType.TEMPLE_PEW,
        TileType.WEAPON_RACK,
        TileType.MOORED_BOAT,
        TileType.WOOD_BED,
        TileType.IRON_BAR_GATE,
        TileType.SANDSTONE_WALL,
        TileType.GRANITE_WALL,
        TileType.OBSIDIAN_WALL,
        TileType.WAR_BANNER,
        TileType.CAMPFIRE_BRAZIER -> true
        else -> false
    }

    /** Full standing architectural wall height multiplier (~2.4x isometric tile height). */
    const val TALL_WALL_HEIGHT_MULT = 2.40f
    /** Cutaway height multiplier when a foreground wall sits between camera and player. */
    const val FOREGROUND_WALL_CUTAWAY_MIN = 0.35f

    data class WallCutaway(
        val heightScale: Float,
        val alpha: Float
    )

    /**
     * Computes dynamic foreground wall cutaway across all 360° camera rotations.
     * Tall walls standing closer to the camera than the player and within ~4.8 tiles smoothly
     * lower to ~35% height and soften slightly so the hero and interior furniture stay unobstructed.
     */
    fun computeWallCutaway(
        cam: IsometricCamera,
        wallTx: Int,
        wallTy: Int,
        playerFx: Float,
        playerFy: Float,
        enabled: Boolean = true
    ): WallCutaway {
        if (!enabled || playerFx < -500f) return WallCutaway(1f, 1f)
        val depthDelta = cam.depthOf(wallTx.toFloat(), wallTy.toFloat()) - cam.depthOf(playerFx, playerFy)
        if (depthDelta <= 0.12f) return WallCutaway(1f, 1f)
        val dist = hypot(wallTx.toFloat() - playerFx, wallTy.toFloat() - playerFy)
        if (dist >= 4.8f) return WallCutaway(1f, 1f)

        val distFactor = ((4.8f - dist) / 2.2f).coerceIn(0f, 1f)
        val depthFactor = ((depthDelta - 0.12f) / 0.85f).coerceIn(0f, 1f)
        val t = (distFactor * depthFactor).coerceIn(0f, 1f)
        val hScale = 1f - t * (1f - FOREGROUND_WALL_CUTAWAY_MIN)
        val alpha = 1f - t * 0.38f
        return WallCutaway(heightScale = hScale, alpha = alpha)
    }

    private fun isBuildingWall(tile: TileType): Boolean = when (tile) {
        TileType.TIMBER_WALL,
        TileType.TIMBER_WINDOW_WALL,
        TileType.TEMPLE_WALL,
        TileType.GATE_PILLAR,
        TileType.STONE_WALL,
        TileType.CITY_WALL,
        TileType.TORCH_WALL,
        TileType.SANDSTONE_WALL,
        TileType.GRANITE_WALL,
        TileType.OBSIDIAN_WALL -> true
        else -> false
    }

    private fun isIndoorWalkableFloor(tile: TileType): Boolean = when (tile) {
        TileType.WOOD_PLANK_FLOOR,
        TileType.TEMPLE_FLOOR,
        TileType.ARCANE_FLOOR,
        TileType.COBBLESTONE,
        TileType.CRYPT_FLOOR,
        TileType.OBSIDIAN_FLOOR,
        TileType.STONE_BRIDGE,
        TileType.IRON_BAR_GATE -> true
        else -> false
    }

    private fun tileHash(tx: Int, ty: Int, seed: Int = 1): Int {
        var h = tx * 374761393 + ty * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    fun drawContactShadow(
        scope: DrawScope,
        cx: Float,
        cy: Float,
        hw: Float,
        hh: Float,
        scale: Float = 1f,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT
    ) {
        if (!quality.drawContactShadows) return
        val w = hw * 0.88f * scale
        val h = hh * 0.62f * scale
        if (quality.drawHdStonePolish) {
            scope.drawOval(
                color = Color(0x28000000),
                topLeft = Offset(cx - w * 0.62f, cy - h * 0.62f),
                size = Size(w * 1.24f, h * 1.24f)
            )
            scope.drawOval(
                color = Color(0x5E000000),
                topLeft = Offset(cx - w * 0.48f, cy - h * 0.48f),
                size = Size(w * 0.96f, h * 0.96f)
            )
        } else {
            scope.drawOval(
                color = Color(0x52000000),
                topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f),
                size = Size(w, h)
            )
        }
    }

    fun drawRotatedQuad(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        cx: Float,
        cy: Float,
        fracU: Float = 1.0f,
        fracV: Float = 1.0f,
        offsetU: Float = 0f,
        offsetV: Float = 0f,
        zPx: Float = 0f,
        fillColor: Color,
        strokeColor: Color? = null,
        strokeWidth: Float = 1f
    ) {
        val ox = cx + offsetU * cam.ux + offsetV * cam.vx
        val oy = cy + offsetU * cam.uy + offsetV * cam.vy - zPx
        val huX = cam.ux * (fracU * 0.5f)
        val huY = cam.uy * (fracU * 0.5f)
        val hvX = cam.vx * (fracV * 0.5f)
        val hvY = cam.vy * (fracV * 0.5f)

        path.rewind()
        path.moveTo(ox - huX - hvX, oy - huY - hvY)
        path.lineTo(ox + huX - hvX, oy + huY - hvY)
        path.lineTo(ox + huX + hvX, oy + huY + hvY)
        path.lineTo(ox - huX + hvX, oy - huY + hvY)
        path.close()
        scope.drawPath(path, color = fillColor)
        if (strokeColor != null) {
            scope.drawPath(path, color = strokeColor, style = Stroke(width = strokeWidth))
        }
    }

    /**
     * Draws a 3D axis-aligned prism rotated cleanly to [cam.yawDeg], automatically selecting the two
     * front-facing vertical walls based on screen-space depth and applying world-space directional
     * twilight lighting per face normal in High & HD tiers.
     */
    fun drawRotatedPrism(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        cx: Float,
        cy: Float,
        fracU: Float,
        fracV: Float,
        heightPx: Float,
        baseZ: Float = 0f,
        offsetU: Float = 0f,
        offsetV: Float = 0f,
        topColor: Color,
        faceXColor: Color,
        faceYColor: Color,
        outlineColor: Color = Color(0x5514100E),
        quality: GraphicsQuality = GraphicsQuality.DEFAULT,
        isOverland: Boolean = true,
        alpha: Float = 1f,
        drawMortarCourses: Boolean = false
    ) {
        val ox = cx + offsetU * cam.ux + offsetV * cam.vx
        val oy = cy + offsetU * cam.uy + offsetV * cam.vy - baseZ
        val huX = cam.ux * (fracU * 0.5f)
        val huY = cam.uy * (fracU * 0.5f)
        val hvX = cam.vx * (fracV * 0.5f)
        val hvY = cam.vy * (fracV * 0.5f)

        // 4 base corners in world order: NW(0), NE(1), SE(2), SW(3)
        val b0x = ox - huX - hvX; val b0y = oy - huY - hvY
        val b1x = ox + huX - hvX; val b1y = oy + huY - hvY
        val b2x = ox + huX + hvX; val b2y = oy + huY + hvY
        val b3x = ox - huX + hvX; val b3y = oy - huY + hvY

        val useDir = quality.useDirectionalLighting
        val strokeW = 1.05f * quality.strokeScale
        val effOutline = outlineColor.copy(alpha = (outlineColor.alpha * alpha).coerceIn(0f, 1f))

        // Helper to draw a vertical face if its midpoint is closer to viewer (y > center oy)
        fun drawSideIfVisible(ax: Float, ay: Float, bx: Float, by: Float, baseColor: Color, nx: Float, ny: Float) {
            val midY = (ay + by) * 0.5f
            if (midY >= oy - 0.1f) {
                val shaded = (if (useDir) {
                    IsometricLightingEngine.shadeColorForFace(baseColor, nx, ny, 0f, isOverland, quality)
                } else {
                    baseColor
                }).copy(alpha = (baseColor.alpha * alpha).coerceIn(0f, 1f))
                path.rewind()
                path.moveTo(ax, ay)
                path.lineTo(bx, by)
                path.lineTo(bx, by - heightPx)
                path.lineTo(ax, ay - heightPx)
                path.close()
                scope.drawPath(path, color = shaded)
                if (drawMortarCourses && quality != GraphicsQuality.LOW && heightPx > 14f) {
                    val mortarCol = Color(0xFF28231E).copy(alpha = 0.42f * alpha)
                    val hiStone = Color(0xFFFFF5E2).copy(alpha = 0.14f * alpha)
                    val courses = (heightPx / 12f).toInt().coerceIn(2, 6)
                    for (c in 1 until courses) {
                        val z = heightPx * (c.toFloat() / courses.toFloat())
                        scope.drawLine(mortarCol, Offset(ax, ay - z), Offset(bx, by - z), strokeWidth = 1.15f)
                        scope.drawLine(hiStone, Offset(ax, ay - z + 1.2f), Offset(bx, by - z + 1.2f), strokeWidth = 0.9f)
                        val stagger = if (c and 1 == 0) 0.34f else 0.66f
                        val mx = ax + (bx - ax) * stagger
                        val my = ay + (by - ay) * stagger
                        val nextZ = heightPx * ((c + 1).coerceAtMost(courses).toFloat() / courses.toFloat())
                        scope.drawLine(mortarCol, Offset(mx, my - z), Offset(mx, my - nextZ), strokeWidth = 1.0f)
                    }
                }
                scope.drawPath(path, color = effOutline, style = Stroke(width = strokeW))
                if (quality.drawHdStonePolish && (nx < 0f || ny < 0f)) {
                    scope.drawLine(
                        color = Color.White.copy(alpha = 0.20f * alpha),
                        start = Offset(ax, ay - heightPx + 1.2f),
                        end = Offset(bx, by - heightPx + 1.2f),
                        strokeWidth = 1.3f
                    )
                }
            }
        }

        val neutralBase = if (useDir) {
            Color(
                red = (faceXColor.red + faceYColor.red) * 0.5f,
                green = (faceXColor.green + faceYColor.green) * 0.5f,
                blue = (faceXColor.blue + faceYColor.blue) * 0.5f,
                alpha = faceYColor.alpha
            )
        } else {
            faceYColor
        }
        drawSideIfVisible(b0x, b0y, b1x, b1y, if (useDir) neutralBase else faceYColor, nx = 0f, ny = -1f)
        drawSideIfVisible(b1x, b1y, b2x, b2y, if (useDir) neutralBase else faceXColor, nx = 1f, ny = 0f)
        drawSideIfVisible(b2x, b2y, b3x, b3y, if (useDir) neutralBase else faceYColor, nx = 0f, ny = 1f)
        drawSideIfVisible(b3x, b3y, b0x, b0y, if (useDir) neutralBase else faceXColor, nx = -1f, ny = 0f)

        val shadedTop = (if (useDir) {
            IsometricLightingEngine.shadeColorForFace(topColor, 0f, 0f, 1f, isOverland, quality)
        } else {
            topColor
        }).copy(alpha = (topColor.alpha * alpha).coerceIn(0f, 1f))
        path.rewind()
        path.moveTo(b0x, b0y - heightPx)
        path.lineTo(b1x, b1y - heightPx)
        path.lineTo(b2x, b2y - heightPx)
        path.lineTo(b3x, b3y - heightPx)
        path.close()
        scope.drawPath(path, color = shadedTop)
        scope.drawPath(path, color = effOutline, style = Stroke(width = strokeW))
    }

    fun drawGroundTile(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        floorMap: WorldFloor,
        tx: Int,
        ty: Int,
        tile: TileType,
        cx: Float,
        cy: Float,
        nowMs: Long,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT,
        tileMatrix: Matrix? = null,
        tilePaint: Paint? = null
    ) {
        val h = tileHash(tx, ty, 3) and 15
        val shimmer = if (quality == GraphicsQuality.LOW) 0f else sin(nowMs * 0.0038 + tx * 0.9 + ty * 0.7).toFloat()

        // Ground base underneath props/walls matches local setting cleanly
        val baseTile = when (tile) {
            TileType.TREE, TileType.TREE_STUMP, TileType.HEDGE, TileType.WOOD_FENCE -> when {
                floorMap.floorId == 0 && tx >= 1472 && ty in 400..799 -> TileType.DARK_GRASS
                floorMap.floorId == 0 && ty < 265 && tx >= 1145 -> TileType.SNOW
                floorMap.floorId == 0 && tx in 925..1145 && ty < 290 -> TileType.BLIGHT_SOIL
                floorMap.floorId == 0 && tx < 445 && ty in 355..670 -> TileType.MARSH
                floorMap.floorId == 0 && tx in 540..1240 && ty >= 870 -> TileType.JUNGLE_FLOOR
                else -> TileType.GRASS
            }
            TileType.MOORED_BOAT -> TileType.WATER
            TileType.DEPOT_CHEST, TileType.SHOP_COUNTER, TileType.CRATE_BARREL, TileType.WEAPON_RACK ->
                if (floorMap.floorId == 0) TileType.WOOD_PLANK_FLOOR else TileType.COBBLESTONE
            TileType.WOOD_BED -> when {
                tx in 1388..1404 && ty in 574..587 -> TileType.TEMPLE_FLOOR
                tx in 1416..1434 && ty in 574..587 -> TileType.ARCANE_FLOOR
                else -> TileType.WOOD_PLANK_FLOOR
            }
            TileType.TEMPLE_ALTAR, TileType.TEMPLE_PEW, TileType.TEMPLE_WALL -> TileType.TEMPLE_FLOOR
            TileType.RUNECRAFTING_TABLE, TileType.BOOKSHELF, TileType.CRYSTAL_PILLAR -> when {
                tx in 1416..1434 && ty in 574..587 -> TileType.ARCANE_FLOOR
                tx in 1388..1404 && ty in 574..587 -> TileType.TEMPLE_FLOOR
                else -> TileType.WOOD_PLANK_FLOOR
            }
            TileType.FOUNTAIN, TileType.MARKET_AWNING, TileType.SMELTING_FURNACE, TileType.BLACKSMITH_ANVIL,
            TileType.FLETCHING_BENCH, TileType.LANTERN_POST, TileType.GATE_PILLAR -> TileType.COBBLESTONE
            TileType.TIMBER_WALL, TileType.TIMBER_WINDOW_WALL -> TileType.COBBLESTONE
            TileType.SANDSTONE_WALL -> TileType.SAND
            TileType.GRANITE_WALL -> TileType.COBBLESTONE
            TileType.OBSIDIAN_WALL -> TileType.OBSIDIAN_FLOOR
            TileType.WAR_BANNER -> if (floorMap.floorId == 0) TileType.COBBLESTONE else TileType.OBSIDIAN_FLOOR
            TileType.CAMPFIRE_BRAZIER -> if (floorMap.floorId == 0) TileType.COBBLESTONE else TileType.CRYPT_FLOOR
            TileType.IRON_BAR_GATE -> TileType.CRYPT_FLOOR
            else -> tile
        }

        val isNearLandWater = if (baseTile == TileType.WATER) {
            val n = floorMap.tileAt(tx, ty - 1)
            val s = floorMap.tileAt(tx, ty + 1)
            val w = floorMap.tileAt(tx - 1, ty)
            val e = floorMap.tileAt(tx + 1, ty)
            listOf(n, s, w, e).any { nt ->
                nt != TileType.WATER && nt != TileType.DEEP_WATER && nt != TileType.MOORED_BOAT && nt != TileType.WOOD_PIER && nt != TileType.WOOD_BRIDGE
            }
        } else {
            false
        }

        // High & HD tiers draw crisp cached 32x32 pixel-mosaic textures mapped onto the rotated diamond!
        val drewTextured = if (quality.useProceduralTileTextures && tileMatrix != null && tilePaint != null) {
            val waterFrameMs = if (quality.drawEnhancedWater) 310L else 480L
            val waterFrame = ((nowMs / waterFrameMs) % ProceduralTileTextureAtlas.WATER_FRAMES).toInt()
            ProceduralTileTextureAtlas.drawTexturedGroundTile(
                scope = scope,
                cam = cam,
                baseTile = baseTile,
                tx = tx,
                ty = ty,
                cx = cx,
                cy = cy,
                isShallowWater = isNearLandWater,
                waterFrame = waterFrame,
                reusableMatrix = tileMatrix,
                reusablePaint = tilePaint
            )
        } else {
            false
        }

        if (!drewTextured) {
            val groundColor = when (baseTile) {
                TileType.GRASS -> when {
                    h < 4 -> Color(0xFF477836)
                    h < 8 -> Color(0xFF3E6C30)
                    else -> Color(0xFF4F823D)
                }
                TileType.FLOWER_GRASS -> if (h < 7) Color(0xFF4D843B) else Color(0xFF568F41)
                TileType.DARK_GRASS -> if (h < 6) Color(0xFF234725) else Color(0xFF2B522D)
                TileType.COBBLESTONE -> if ((tx + ty) and 1 == 0) Color(0xFF8E8577) else Color(0xFF837A6D)
                TileType.TEMPLE_FLOOR -> if ((tx + ty) and 1 == 0) Color(0xFFDFD5C2) else Color(0xFFCFC2AA)
                TileType.WOOD_PLANK_FLOOR -> if ((tx + ty) and 1 == 0) Color(0xFF8B6441) else Color(0xFF7D5837)
                TileType.ARCANE_FLOOR -> if ((tx + ty) and 1 == 0) Color(0xFF37314A) else Color(0xFF2E2840)
                TileType.WOOD_PIER -> if (ty and 1 == 0) Color(0xFF8F6642) else Color(0xFF7B5636)
                TileType.WOOD_BRIDGE -> if (ty and 1 == 0) Color(0xFF997149) else Color(0xFF87613C)
                TileType.STONE_BRIDGE -> if ((tx + ty) and 1 == 0) Color(0xFF706B65) else Color(0xFF635E58)
                TileType.DIRT_FLOOR -> if (h < 6) Color(0xFF785C42) else Color(0xFF6B5038)
                TileType.CRYPT_FLOOR -> if ((tx + ty) and 1 == 0) Color(0xFF505561) else Color(0xFF444954)
                TileType.OBSIDIAN_FLOOR -> if ((tx + ty) and 1 == 0) Color(0xFF362F40) else Color(0xFF2C2636)
                TileType.WATER -> Color(
                    red = (0.11f + shimmer * 0.025f).coerceIn(0f, 1f),
                    green = (0.41f + shimmer * 0.045f).coerceIn(0f, 1f),
                    blue = (0.63f + shimmer * 0.04f).coerceIn(0f, 1f)
                )
                TileType.DEEP_WATER -> Color(
                    red = (0.06f + shimmer * 0.015f).coerceIn(0f, 1f),
                    green = (0.19f + shimmer * 0.025f).coerceIn(0f, 1f),
                    blue = (0.34f + shimmer * 0.03f).coerceIn(0f, 1f)
                )
                TileType.SAND -> if (h < 6) Color(0xFFD2B06B) else Color(0xFFDFBE79)
                TileType.MARSH -> if (h < 6) Color(0xFF3A4B30) else Color(0xFF475C3A)
                TileType.SNOW -> if (h < 6) Color(0xFFE3ECF5) else Color(0xFFF1F7FC)
                TileType.JUNGLE_FLOOR -> if (h < 6) Color(0xFF215A28) else Color(0xFF2B6E33)
                TileType.VOLCANIC_ASH -> if (h < 6) Color(0xFF3E3736) else Color(0xFF4D4442)
                TileType.BLIGHT_SOIL -> if (h < 6) Color(0xFF3C294A) else Color(0xFF4A335C)
                TileType.LAVA -> Color(
                    red = (0.88f + shimmer * 0.09f).coerceIn(0f, 1f),
                    green = (0.31f + shimmer * 0.08f).coerceIn(0f, 1f),
                    blue = 0.07f
                )
                TileType.STAIRS_DOWN, TileType.STAIRS_UP -> Color(0xFF6A6258)
                TileType.ROPE_HOLE_DOWN, TileType.ROPE_SPOT_UP -> Color(0xFF6A543E)
                else -> Color(0xFF544E46)
            }

            val strokeCol = if (quality == GraphicsQuality.LOW) {
                null
            } else {
                when (baseTile) {
                    TileType.COBBLESTONE -> Color(0x362C2722)
                    TileType.TEMPLE_FLOOR -> Color(0x45B09658)
                    TileType.WOOD_PLANK_FLOOR, TileType.WOOD_PIER, TileType.WOOD_BRIDGE -> Color(0x422A1B0E)
                    TileType.ARCANE_FLOOR -> Color(0x3C8A6BFF)
                    TileType.WATER, TileType.DEEP_WATER, TileType.LAVA -> null
                    else -> Color(0x18000000)
                }
            }

            // Slightly overdraw ground diamonds (1.03f) to eliminate any hairline subpixel seams at arbitrary yaw angles
            drawRotatedQuad(
                scope, path, cam, cx, cy,
                fracU = 1.03f, fracV = 1.03f,
                fillColor = groundColor,
                strokeColor = strokeCol,
                strokeWidth = 0.85f * quality.strokeScale
            )
        }

        // Detailed ground surface overlays & soft biome transition borders scaled by GraphicsQuality tier
        when (baseTile) {
            TileType.GRASS, TileType.DARK_GRASS -> {
                if (quality.drawGroundClutter) {
                    if (h == 1 || h == 9) {
                        val blade = if (baseTile == TileType.DARK_GRASS) Color(0xFF427A46) else Color(0xFF68A34D)
                        scope.drawLine(blade, Offset(cx - 2f, cy + 1f), Offset(cx - 4f, cy - 4f), 1.3f * quality.strokeScale)
                        scope.drawLine(blade, Offset(cx + 1f, cy + 2f), Offset(cx + 3f, cy - 3f), 1.2f * quality.strokeScale)
                        if (quality.drawHdStonePolish) {
                            scope.drawLine(Color(0xFF84C466), Offset(cx, cy + 1f), Offset(cx - 1f, cy - 5f), 1.1f)
                        }
                    } else if (h == 14) {
                        scope.drawOval(Color(0x668C857A), Offset(cx - 3f, cy - 1f), Size(5f, 3f))
                    }
                    // Soft feathered transition borders where grass meets cobblestone roads or dirt trails
                    if (quality.useProceduralTileTextures) {
                        drawGrassEdgeTransitions(scope, path, cam, floorMap, tx, ty, cx, cy)
                    }
                }
            }
            TileType.FLOWER_GRASS -> {
                if (quality.drawWildflowers && !drewTextured) {
                    val blossomColors = arrayOf(Color(0xFFF6D660), Color(0xFFE87EA1), Color(0xFFB39DFF), Color(0xFFF7F4E8))
                    val c1 = blossomColors[h and 3]
                    val c2 = blossomColors[(h + 2) and 3]
                    val r = (cam.tileW * 0.035f).coerceIn(2f, 4f)
                    scope.drawCircle(Color(0xFF6BA64E), r * 1.1f, Offset(cx - cam.tileW * 0.12f, cy + cam.tileH * 0.04f))
                    scope.drawCircle(c1, r, Offset(cx - cam.tileW * 0.12f, cy))
                    scope.drawCircle(c2, r * 0.9f, Offset(cx + cam.tileW * 0.11f, cy - cam.tileH * 0.06f))
                    scope.drawCircle(Color(0xFFF7E58B), r * 0.75f, Offset(cx + cam.tileW * 0.02f, cy + cam.tileH * 0.12f))
                } else if (quality.useProceduralTileTextures) {
                    drawGrassEdgeTransitions(scope, path, cam, floorMap, tx, ty, cx, cy)
                }
            }
            TileType.COBBLESTONE -> {
                if (quality.drawGroundClutter) {
                    if (!drewTextured && h < 10) {
                        scope.drawLine(
                            Color(0x28302A24),
                            Offset(cx - cam.ux * 0.25f, cy - cam.uy * 0.25f),
                            Offset(cx + cam.ux * 0.25f, cy + cam.uy * 0.25f),
                            0.9f * quality.strokeScale
                        )
                    }
                    // Dressed stone curb border along all 4 cardinal edges meeting grass/meadow
                    val curbLight = Color(0x6CBAB0A0)
                    val curbShadow = Color(0x4A2B2620)
                    val nGrass = isGrassLike(floorMap.tileAt(tx, ty - 1))
                    val sGrass = isGrassLike(floorMap.tileAt(tx, ty + 1))
                    val wGrass = isGrassLike(floorMap.tileAt(tx - 1, ty))
                    val eGrass = isGrassLike(floorMap.tileAt(tx + 1, ty))
                    if (nGrass) {
                        drawRotatedQuad(scope, path, cam, cx, cy, fracU = 1.0f, fracV = 0.13f, offsetV = -0.44f, fillColor = curbLight)
                    }
                    if (sGrass) {
                        drawRotatedQuad(scope, path, cam, cx, cy, fracU = 1.0f, fracV = 0.13f, offsetV = 0.44f, fillColor = curbShadow)
                    }
                    if (wGrass) {
                        drawRotatedQuad(scope, path, cam, cx, cy, fracU = 0.13f, fracV = 1.0f, offsetU = -0.44f, fillColor = curbLight)
                    }
                    if (eGrass) {
                        drawRotatedQuad(scope, path, cam, cx, cy, fracU = 0.13f, fracV = 1.0f, offsetU = 0.44f, fillColor = curbShadow)
                    }
                }
            }
            TileType.TEMPLE_FLOOR -> {
                // Gold-bordered marble diamond mosaic inlay
                if (quality != GraphicsQuality.LOW && (tx + ty) % 2 == 0) {
                    drawRotatedQuad(
                        scope, path, cam, cx, cy, fracU = 0.56f, fracV = 0.56f,
                        fillColor = Color(0x2AE6C775), strokeColor = Color(0x66C7A34E), strokeWidth = 1f * quality.strokeScale
                    )
                    if (quality.drawHdStonePolish) {
                        drawRotatedQuad(
                            scope, path, cam, cx, cy, fracU = 0.26f, fracV = 0.26f,
                            fillColor = Color(0x38FFF1C1)
                        )
                    }
                }
            }
            TileType.ARCANE_FLOOR -> {
                // Glowing violet arcane rune circle inlaid in Runecrafting Academy floor
                if (quality != GraphicsQuality.LOW && tx in 221..229 && ty in 177..183) {
                    val pulse = 0.35f + 0.18f * sin(nowMs * 0.0045 + tx + ty).toFloat()
                    scope.drawOval(
                        color = Color(0xFFB388FF).copy(alpha = pulse),
                        topLeft = Offset(cx - cam.tileW * 0.26f, cy - cam.tileH * 0.26f),
                        size = Size(cam.tileW * 0.52f, cam.tileH * 0.52f),
                        style = Stroke(width = 1.4f * quality.strokeScale)
                    )
                    if (quality.drawHdStonePolish) {
                        scope.drawOval(
                            color = Color(0xFFE5D1FF).copy(alpha = pulse * 0.75f),
                            topLeft = Offset(cx - cam.tileW * 0.14f, cy - cam.tileH * 0.14f),
                            size = Size(cam.tileW * 0.28f, cam.tileH * 0.28f),
                            style = Stroke(width = 1.0f)
                        )
                    }
                }
            }
            TileType.WOOD_PLANK_FLOOR -> {
                if (quality != GraphicsQuality.LOW) {
                    // Warm timber plank grain lines
                    val grain = Color(0x383A2210)
                    scope.drawLine(grain, Offset(cx - cam.ux * 0.42f, cy - cam.uy * 0.42f), Offset(cx + cam.ux * 0.42f, cy + cam.uy * 0.42f), 1f * quality.strokeScale)
                }
            }
            TileType.WOOD_PIER, TileType.WOOD_BRIDGE -> {
                // Plank lines + bridge/pier railings along water edges
                val railColor = Color(0xFF5C3E24)
                val postColor = Color(0xFF422A16)
                if (quality != GraphicsQuality.LOW) {
                    scope.drawLine(Color(0x443B2310), Offset(cx - cam.ux * 0.44f, cy - cam.uy * 0.44f), Offset(cx + cam.ux * 0.44f, cy + cam.uy * 0.44f), 1.1f * quality.strokeScale)
                }
                val westWater = floorMap.tileAt(tx - 1, ty) == TileType.WATER || floorMap.tileAt(tx - 1, ty) == TileType.MOORED_BOAT
                val eastWater = floorMap.tileAt(tx + 1, ty) == TileType.WATER || floorMap.tileAt(tx + 1, ty) == TileType.MOORED_BOAT
                val railH = cam.tileH * 0.38f
                if (westWater) {
                    val p0 = Offset(cx - cam.ux * 0.46f - cam.vx * 0.44f, cy - cam.uy * 0.46f - cam.vy * 0.44f)
                    val p1 = Offset(cx - cam.ux * 0.46f + cam.vx * 0.44f, cy - cam.uy * 0.46f + cam.vy * 0.44f)
                    scope.drawLine(postColor, p0, Offset(p0.x, p0.y - railH), 2.2f)
                    scope.drawLine(postColor, p1, Offset(p1.x, p1.y - railH), 2.2f)
                    scope.drawLine(railColor, Offset(p0.x, p0.y - railH * 0.85f), Offset(p1.x, p1.y - railH * 0.85f), 2.2f)
                }
                if (eastWater) {
                    val p0 = Offset(cx + cam.ux * 0.46f - cam.vx * 0.44f, cy + cam.uy * 0.46f - cam.vy * 0.44f)
                    val p1 = Offset(cx + cam.ux * 0.46f + cam.vx * 0.44f, cy + cam.uy * 0.46f + cam.vy * 0.44f)
                    scope.drawLine(postColor, p0, Offset(p0.x, p0.y - railH), 2.2f)
                    scope.drawLine(postColor, p1, Offset(p1.x, p1.y - railH), 2.2f)
                    scope.drawLine(railColor, Offset(p0.x, p0.y - railH * 0.85f), Offset(p1.x, p1.y - railH * 0.85f), 2.2f)
                }
            }
            TileType.STONE_BRIDGE -> {
                // Dressed mortared stone bridge deck with low stone parapet curbs bordering water/lava chasms
                if (quality != GraphicsQuality.LOW) {
                    val seam = Color(0x55221F1C)
                    scope.drawLine(seam, Offset(cx - cam.ux * 0.44f, cy - cam.uy * 0.44f), Offset(cx + cam.ux * 0.44f, cy + cam.uy * 0.44f), 1.3f * quality.strokeScale)
                    scope.drawLine(Color(0x28FFF2D6), Offset(cx - cam.vx * 0.42f, cy - cam.vy * 0.42f), Offset(cx + cam.vx * 0.42f, cy + cam.vy * 0.42f), 1.0f)
                }
                val isHazard = { t: TileType -> t == TileType.WATER || t == TileType.LAVA }
                val curbTop = Color(0xFF91897D)
                val curbSideX = Color(0xFF544E47)
                val curbSideY = Color(0xFF6B645B)
                val curbH = cam.tileH * 0.28f
                if (isHazard(floorMap.tileAt(tx - 1, ty))) {
                    drawRotatedPrism(scope, path, cam, cx, cy, fracU = 0.16f, fracV = 0.96f, heightPx = curbH, offsetU = -0.42f, topColor = curbTop, faceXColor = curbSideX, faceYColor = curbSideY, quality = quality, isOverland = false)
                }
                if (isHazard(floorMap.tileAt(tx + 1, ty))) {
                    drawRotatedPrism(scope, path, cam, cx, cy, fracU = 0.16f, fracV = 0.96f, heightPx = curbH, offsetU = 0.42f, topColor = curbTop, faceXColor = curbSideX, faceYColor = curbSideY, quality = quality, isOverland = false)
                }
                if (isHazard(floorMap.tileAt(tx, ty - 1))) {
                    drawRotatedPrism(scope, path, cam, cx, cy, fracU = 0.96f, fracV = 0.16f, heightPx = curbH, offsetV = -0.42f, topColor = curbTop, faceXColor = curbSideX, faceYColor = curbSideY, quality = quality, isOverland = false)
                }
                if (isHazard(floorMap.tileAt(tx, ty + 1))) {
                    drawRotatedPrism(scope, path, cam, cx, cy, fracU = 0.96f, fracV = 0.16f, heightPx = curbH, offsetV = 0.42f, topColor = curbTop, faceXColor = curbSideX, faceYColor = curbSideY, quality = quality, isOverland = false)
                }
            }
            TileType.WATER -> {
                if (quality != GraphicsQuality.LOW) {
                    // Animated water ripples
                    val rippleOffset = shimmer * cam.tileW * 0.08f
                    scope.drawLine(
                        color = Color(0x6CE0F7FF),
                        start = Offset(cx - cam.tileW * 0.16f + rippleOffset, cy - cam.tileH * 0.08f),
                        end = Offset(cx + cam.tileW * 0.12f + rippleOffset, cy - cam.tileH * 0.08f),
                        strokeWidth = 1.6f * quality.strokeScale
                    )
                    if (quality.drawShorelineDetails || quality.drawEnhancedWater) {
                        scope.drawLine(
                            color = Color(0x45B0E4FF),
                            start = Offset(cx - cam.tileW * 0.06f - rippleOffset, cy + cam.tileH * 0.14f),
                            end = Offset(cx + cam.tileW * 0.18f - rippleOffset, cy + cam.tileH * 0.14f),
                            strokeWidth = 1.2f * quality.strokeScale
                        )
                    }
                    if (quality.drawEnhancedWater) {
                        // HD shimmering specular water glint
                        val glintAlpha = (0.30f + 0.35f * abs(shimmer)).coerceIn(0f, 0.7f)
                        scope.drawCircle(
                            color = Color(0xFFFFFFFF).copy(alpha = glintAlpha),
                            radius = (cam.tileW * 0.026f).coerceIn(1.5f, 3.2f),
                            center = Offset(cx + rippleOffset * 0.6f, cy - cam.tileH * 0.02f)
                        )
                    }
                    // Softened natural shoreline: curved rocks, swaying reeds & floating lily pads near land!
                    if (quality.drawShorelineDetails) {
                        val nearLand = listOf(tx - 1 to ty, tx + 1 to ty, tx to ty - 1, tx to ty + 1).any { (nx, ny) ->
                            val nt = floorMap.tileAt(nx, ny)
                            nt != TileType.WATER && nt != TileType.MOORED_BOAT && nt != TileType.WOOD_PIER && nt != TileType.WOOD_BRIDGE
                        }
                        if (nearLand) {
                            if (quality.drawEnhancedWater) {
                                // Subtle shoreline whitewater foam ring
                                scope.drawOval(
                                    color = Color(0x42E8FAFF),
                                    topLeft = Offset(cx - cam.tileW * 0.30f, cy - cam.tileH * 0.22f),
                                    size = Size(cam.tileW * 0.60f, cam.tileH * 0.44f),
                                    style = Stroke(width = 1.5f)
                                )
                            }
                            if (h < 6) {
                                // Curved smooth shoreline stones
                                scope.drawOval(
                                    Color(0xFF7D786F),
                                    Offset(cx - cam.tileW * 0.22f, cy - cam.tileH * 0.14f),
                                    Size(cam.tileW * 0.22f, cam.tileH * 0.22f)
                                )
                                scope.drawOval(
                                    Color(0xFF9E988C),
                                    Offset(cx - cam.tileW * 0.19f, cy - cam.tileH * 0.13f),
                                    Size(cam.tileW * 0.14f, cam.tileH * 0.12f)
                                )
                            } else if (h < 11) {
                                // Swaying shoreline reeds
                                val sway = shimmer * 2.2f
                                val reedCol = Color(0xFF3A7A3A)
                                scope.drawLine(reedCol, Offset(cx + 2f, cy + 2f), Offset(cx + 2f + sway, cy - cam.tileH * 0.42f), 1.8f)
                                scope.drawLine(Color(0xFF559C4B), Offset(cx + 6f, cy + 4f), Offset(cx + 7f + sway * 0.8f, cy - cam.tileH * 0.34f), 1.6f)
                                scope.drawCircle(Color(0xFF6B4423), 2f, Offset(cx + 2f + sway, cy - cam.tileH * 0.42f))
                            } else {
                                // Floating lily pad with soft pink blossom
                                val padW = cam.tileW * 0.24f
                                val padH = cam.tileH * 0.24f
                                scope.drawOval(Color(0xFF2F7D3E), Offset(cx - padW * 0.5f, cy - padH * 0.5f), Size(padW, padH))
                                scope.drawOval(Color(0xFF4BA356), Offset(cx - padW * 0.38f, cy - padH * 0.4f), Size(padW * 0.72f, padH * 0.7f))
                                if (h == 14 || h == 15) {
                                    scope.drawCircle(Color(0xFFFFB7D5), 2.4f, Offset(cx, cy - 1f))
                                }
                            }
                        }
                    }
                }
            }
            TileType.STAIRS_DOWN -> drawStairsSteps(scope, cam, cx, cy, goingUp = false)
            TileType.STAIRS_UP -> drawStairsSteps(scope, cam, cx, cy, goingUp = true)
            TileType.ROPE_HOLE_DOWN -> {
                scope.drawOval(Color(0xFF14100E), Offset(cx - cam.tileW * 0.25f, cy - cam.tileH * 0.26f), Size(cam.tileW * 0.50f, cam.tileH * 0.52f))
                scope.drawOval(Color(0xFF9C7A52), Offset(cx - cam.tileW * 0.25f, cy - cam.tileH * 0.26f), Size(cam.tileW * 0.50f, cam.tileH * 0.52f), style = Stroke(2f))
            }
            TileType.ROPE_SPOT_UP -> {
                scope.drawOval(Color(0x55EED078), Offset(cx - cam.tileW * 0.24f, cy - cam.tileH * 0.24f), Size(cam.tileW * 0.48f, cam.tileH * 0.48f))
                scope.drawOval(TorchGold, Offset(cx - cam.tileW * 0.18f, cy - cam.tileH * 0.18f), Size(cam.tileW * 0.36f, cam.tileH * 0.36f), style = Stroke(1.8f))
            }
            else -> Unit
        }

        // Base ambient occlusion & directional wall cast shadow seams on adjacent walkable ground
        if (quality.drawAmbientOcclusion) {
            IsometricLightingEngine.drawGroundBaseAmbientOcclusion(
                scope = scope,
                path = path,
                cam = cam,
                floorMap = floorMap,
                tx = tx,
                ty = ty,
                cx = cx,
                cy = cy,
                quality = quality
            )
        }
    }

    private fun isGrassLike(tile: TileType): Boolean =
        tile == TileType.GRASS || tile == TileType.FLOWER_GRASS || tile == TileType.DARK_GRASS

    private fun drawGrassEdgeTransitions(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        floorMap: WorldFloor,
        tx: Int,
        ty: Int,
        cx: Float,
        cy: Float
    ) {
        fun blendColorFor(neighbor: TileType): Color? = when (neighbor) {
            TileType.COBBLESTONE -> Color(0x526E695E)
            TileType.DIRT_FLOOR -> Color(0x5A74573E)
            else -> null
        }
        blendColorFor(floorMap.tileAt(tx, ty - 1))?.let { col ->
            drawRotatedQuad(scope, path, cam, cx, cy, fracU = 1.02f, fracV = 0.16f, offsetV = -0.42f, fillColor = col)
        }
        blendColorFor(floorMap.tileAt(tx, ty + 1))?.let { col ->
            drawRotatedQuad(scope, path, cam, cx, cy, fracU = 1.02f, fracV = 0.16f, offsetV = 0.42f, fillColor = col)
        }
        blendColorFor(floorMap.tileAt(tx - 1, ty))?.let { col ->
            drawRotatedQuad(scope, path, cam, cx, cy, fracU = 0.16f, fracV = 1.02f, offsetU = -0.42f, fillColor = col)
        }
        blendColorFor(floorMap.tileAt(tx + 1, ty))?.let { col ->
            drawRotatedQuad(scope, path, cam, cx, cy, fracU = 0.16f, fracV = 1.02f, offsetU = 0.42f, fillColor = col)
        }
    }

    private fun drawStairsSteps(scope: DrawScope, cam: IsometricCamera, cx: Float, cy: Float, goingUp: Boolean) {
        for (i in 0..3) {
            val frac = (i - 1.5f) * 0.18f
            val stepColor = if (goingUp) {
                if (i % 2 == 0) Color(0xFFBDB4A4) else Color(0xFF9B9283)
            } else {
                if (i % 2 == 0) Color(0xFF3E3A35) else Color(0xFF282522)
            }
            val ox = cx + frac * cam.vx
            val oy = cy + frac * cam.vy
            scope.drawLine(
                color = stepColor,
                start = Offset(ox - cam.ux * 0.32f, oy - cam.uy * 0.32f),
                end = Offset(ox + cam.ux * 0.32f, oy + cam.uy * 0.32f),
                strokeWidth = (cam.tileH * 0.13f).coerceAtLeast(3f)
            )
        }
    }

    /**
     * Draws all rotated 3D architectural walls, pillars, trees, fountains, furniture, and boats.
     */
    fun drawTallTileProp(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        floorMap: WorldFloor,
        tx: Int,
        ty: Int,
        tile: TileType,
        cx: Float,
        cy: Float,
        nowMs: Long,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT,
        playerFx: Float = -1000f,
        playerFy: Float = -1000f
    ) {
        val unitH = cam.tileH
        val isOverland = floorMap.floorId == 0
        when (tile) {
            TileType.TIMBER_WALL, TileType.TIMBER_WINDOW_WALL -> {
                val cut = computeWallCutaway(cam, tx, ty, playerFx, playerFy)
                drawDetailedTimberWall(scope, path, cam, floorMap, tx, ty, cx, cy, hasWindow = tile == TileType.TIMBER_WINDOW_WALL, nowMs = nowMs, quality = quality, cutaway = cut)
            }
            TileType.TEMPLE_WALL -> {
                val cut = computeWallCutaway(cam, tx, ty, playerFx, playerFy)
                drawDetailedTempleWall(scope, path, cam, floorMap, tx, ty, cx, cy, nowMs, quality, cutaway = cut)
            }
            TileType.GATE_PILLAR -> {
                val cut = computeWallCutaway(cam, tx, ty, playerFx, playerFy)
                val cScale = cut.heightScale
                val cAlpha = cut.alpha
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.66f, fracV = 0.66f, heightPx = unitH * 0.36f * cScale,
                    topColor = Color(0xFF968E82), faceXColor = Color(0xFF6B645A), faceYColor = Color(0xFF7D756A),
                    quality = quality, isOverland = isOverland, alpha = cAlpha, drawMortarCourses = true
                )
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.48f, fracV = 0.48f, heightPx = unitH * 1.98f * cScale, baseZ = unitH * 0.36f * cScale,
                    topColor = Color(0xFFB3AA9C), faceXColor = Color(0xFF7A7267), faceYColor = Color(0xFF91887B),
                    quality = quality, isOverland = isOverland, alpha = cAlpha, drawMortarCourses = true
                )
                if (cScale > 0.6f) {
                    drawWarmFlickerGlow(scope, cx, cy - unitH * 2.42f * cScale, unitH * 0.92f, nowMs, tx, ty, quality)
                }
            }
            TileType.IRON_BAR_GATE -> {
                val cut = computeWallCutaway(cam, tx, ty, playerFx, playerFy)
                drawIronBarGateArch(scope, path, cam, floorMap, tx, ty, cx, cy, quality, cut)
            }
            TileType.STONE_WALL, TileType.CITY_WALL, TileType.TORCH_WALL -> {
                val cut = computeWallCutaway(cam, tx, ty, playerFx, playerFy)
                drawDetailedStoneWall(scope, path, cam, floorMap, tx, ty, cx, cy, hasTorch = tile == TileType.TORCH_WALL, nowMs = nowMs, quality = quality, cutaway = cut)
            }
            TileType.SANDSTONE_WALL, TileType.GRANITE_WALL, TileType.OBSIDIAN_WALL -> {
                val cut = computeWallCutaway(cam, tx, ty, playerFx, playerFy)
                drawBiomeIdeologyWall(scope, path, cam, floorMap, tx, ty, cx, cy, tile = tile, quality = quality, cutaway = cut)
            }
            TileType.WAR_BANNER -> {
                drawWarBannerProp(scope, path, cam, cx, cy, tx, ty, nowMs, quality, isOverland)
            }
            TileType.CAMPFIRE_BRAZIER -> {
                drawCampfireBrazierProp(scope, path, cam, cx, cy, tx, ty, nowMs, quality, isOverland)
            }
            TileType.CAVE_WALL, TileType.MOUNTAIN_ROCK -> {
                val isCave = tile == TileType.CAVE_WALL
                val cut = computeWallCutaway(cam, tx, ty, playerFx, playerFy)
                val hPx = unitH * (if (isCave) 1.85f else 1.50f) * cut.heightScale
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.88f, fracV = 0.88f, heightPx = hPx * 0.62f,
                    topColor = if (isCave) Color(0xFF47444A) else Color(0xFF7B736A),
                    faceXColor = if (isCave) Color(0xFF2B282E) else Color(0xFF4F4942),
                    faceYColor = if (isCave) Color(0xFF39353D) else Color(0xFF615A52),
                    quality = quality, isOverland = isOverland, alpha = cut.alpha, drawMortarCourses = isCave
                )
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.54f, fracV = 0.54f, heightPx = hPx * 0.52f, baseZ = hPx * 0.58f,
                    topColor = if (isCave) Color(0xFF59545C) else Color(0xFF948B80),
                    faceXColor = if (isCave) Color(0xFF38343B) else Color(0xFF635C54),
                    faceYColor = if (isCave) Color(0xFF46424A) else Color(0xFF787066),
                    quality = quality, isOverland = isOverland, alpha = cut.alpha
                )
                // Damp sewer moss seams & stone fractures on underground cavern walls
                if (isCave && !isOverland && quality != GraphicsQuality.LOW && cut.heightScale > 0.55f) {
                    val hSeed = tileHash(tx, ty, 31) and 15
                    if (floorMap.floorId == -1 && hSeed < 9) {
                        val mossColor = Color(0xFF3A6B39).copy(alpha = 0.68f * cut.alpha)
                        scope.drawCircle(mossColor, cam.tileW * 0.08f, Offset(cx - cam.tileW * 0.14f, cy - hPx * 0.34f))
                        scope.drawCircle(Color(0xFF284D28).copy(alpha = 0.55f * cut.alpha), cam.tileW * 0.06f, Offset(cx + cam.tileW * 0.12f, cy - hPx * 0.22f))
                    }
                    if (hSeed % 3 == 0) {
                        val crack = Color(0xAA141216).copy(alpha = 0.65f * cut.alpha)
                        scope.drawLine(crack, Offset(cx - cam.tileW * 0.08f, cy - hPx * 0.55f), Offset(cx + cam.tileW * 0.03f, cy - hPx * 0.28f), 1.4f)
                        scope.drawLine(crack, Offset(cx + cam.tileW * 0.03f, cy - hPx * 0.28f), Offset(cx - cam.tileW * 0.02f, cy - hPx * 0.14f), 1.2f)
                    }
                }
            }
            TileType.TREE -> drawTreeProp(scope, path, cam, cx, cy, tx, ty, nowMs, quality)
            TileType.TREE_STUMP -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.36f, fracV = 0.36f, heightPx = unitH * 0.34f,
                    topColor = Color(0xFFB58958), faceXColor = Color(0xFF5C3D22), faceYColor = Color(0xFF734E2C),
                    quality = quality, isOverland = isOverland
                )
            }
            TileType.FOUNTAIN -> drawStoneFountain(scope, path, cam, cx, cy, nowMs, quality)
            TileType.HEDGE -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.72f, fracV = 0.72f, heightPx = unitH * 0.54f,
                    topColor = Color(0xFF3E7A38), faceXColor = Color(0xFF214A1E), faceYColor = Color(0xFF2D5F28),
                    quality = quality, isOverland = isOverland
                )
                if (quality != GraphicsQuality.LOW) {
                    scope.drawCircle(Color(0xFF63A854), cam.tileW * 0.08f, Offset(cx - cam.tileW * 0.1f, cy - unitH * 0.48f))
                    scope.drawCircle(Color(0xFFF5D36C), 2.4f, Offset(cx + cam.tileW * 0.08f, cy - unitH * 0.52f))
                }
            }
            TileType.WOOD_FENCE -> drawWoodenFence(scope, path, cam, floorMap, tx, ty, cx, cy, quality)
            TileType.LANTERN_POST -> drawLanternPost(scope, path, cam, cx, cy, nowMs, tx, ty, quality)
            TileType.WOOD_BED -> drawWoodenBed(scope, path, cam, cx, cy, quality)
            TileType.DEPOT_CHEST, TileType.MINI_DEPOT -> {
                val isMini = tile == TileType.MINI_DEPOT
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.72f, fracV = 0.58f, heightPx = unitH * 0.54f,
                    topColor = if (isMini) Color(0xFF56738A) else Color(0xFF9C693B),
                    faceXColor = if (isMini) Color(0xFF2F4557) else Color(0xFF5C3A1D),
                    faceYColor = if (isMini) Color(0xFF405A70) else Color(0xFF784E28),
                    quality = quality, isOverland = isOverland
                )
                drawRotatedQuad(scope, path, cam, cx, cy, fracU = 0.74f, fracV = 0.14f, zPx = unitH * 0.55f, fillColor = Color(0xFF3B3E45))
                scope.drawCircle(TorchGold, (cam.tileW * 0.048f).coerceAtLeast(3.2f), Offset(cx, cy - unitH * 0.28f))
            }
            TileType.SHOP_COUNTER -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.88f, fracV = 0.62f, heightPx = unitH * 0.62f,
                    topColor = Color(0xFFC4925D), faceXColor = Color(0xFF634323), faceYColor = Color(0xFF855B31),
                    quality = quality, isOverland = isOverland
                )
                drawRotatedQuad(scope, path, cam, cx, cy, fracU = 0.78f, fracV = 0.44f, zPx = unitH * 0.63f, fillColor = Color(0xFFD6A874))
                // Ruby, emerald & sapphire potion bottles + ledger on counter
                scope.drawCircle(Color(0xFFE54848), 3.8f, Offset(cx - cam.tileW * 0.09f, cy - unitH * 0.66f))
                scope.drawCircle(Color(0xFF42A5F5), 3.6f, Offset(cx + cam.tileW * 0.07f, cy - unitH * 0.66f))
                scope.drawCircle(Color(0xFF4CE06A), 3.0f, Offset(cx, cy - unitH * 0.69f))
            }
            TileType.MARKET_AWNING -> drawMarketAwningStall(scope, path, cam, cx, cy, quality)
            TileType.CRATE_BARREL -> drawCrateAndBarrel(scope, path, cam, cx, cy, quality)
            TileType.TEMPLE_ALTAR -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.84f, fracV = 0.64f, heightPx = unitH * 0.66f,
                    topColor = Color(0xFFF4EFE4), faceXColor = Color(0xFFB8AE9C), faceYColor = Color(0xFFD6CEBE),
                    quality = quality, isOverland = isOverland
                )
                drawRotatedQuad(scope, path, cam, cx, cy, fracU = 0.38f, fracV = 0.66f, zPx = unitH * 0.67f, fillColor = Color(0xFFA82C2C), strokeColor = TorchGold, strokeWidth = 1.5f)
                drawWarmFlickerGlow(scope, cx - cam.tileW * 0.16f, cy - unitH * 0.82f, unitH * 0.52f, nowMs, tx, ty, quality)
                drawWarmFlickerGlow(scope, cx + cam.tileW * 0.16f, cy - unitH * 0.82f, unitH * 0.52f, nowMs, tx + 3, ty, quality)
            }
            TileType.TEMPLE_PEW -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.78f, fracV = 0.36f, heightPx = unitH * 0.32f,
                    topColor = Color(0xFF8C5D35), faceXColor = Color(0xFF523319), faceYColor = Color(0xFF6E4625),
                    quality = quality, isOverland = isOverland
                )
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.78f, fracV = 0.14f, heightPx = unitH * 0.36f, baseZ = unitH * 0.30f, offsetV = -0.11f,
                    topColor = Color(0xFF9C6B3F), faceXColor = Color(0xFF5A391D), faceYColor = Color(0xFF784E2A),
                    quality = quality, isOverland = isOverland
                )
            }
            TileType.SMELTING_FURNACE -> {
                // Detailed stone forge hearth with glowing molten lava/coal bed & chimney stack
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.84f, fracV = 0.78f, heightPx = unitH * 0.78f,
                    topColor = Color(0xFF6E625B), faceXColor = Color(0xFF433935), faceYColor = Color(0xFF574B46),
                    quality = quality, isOverland = isOverland, drawMortarCourses = true
                )
                // Glowing molten lava/coal bed on top of forge basin
                val emberPulse = if (quality == GraphicsQuality.LOW) 0.9f else 0.85f + 0.15f * sin(nowMs * 0.012 + tx).toFloat()
                drawRotatedQuad(
                    scope, path, cam, cx, cy, fracU = 0.56f, fracV = 0.50f, zPx = unitH * 0.79f,
                    fillColor = Color(0xFFFF4A1C).copy(alpha = emberPulse),
                    strokeColor = Color(0xFF2B1B17), strokeWidth = 1.8f
                )
                drawRotatedQuad(
                    scope, path, cam, cx, cy, fracU = 0.34f, fracV = 0.28f, zPx = unitH * 0.81f,
                    fillColor = Color(0xFFFFD859).copy(alpha = emberPulse)
                )
                // Chimney hood rising on back side
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.44f, fracV = 0.36f, heightPx = unitH * 0.82f, baseZ = unitH * 0.78f, offsetV = -0.20f,
                    topColor = Color(0xFF524A45), faceXColor = Color(0xFF332D2A), faceYColor = Color(0xFF423B37),
                    quality = quality, isOverland = isOverland, drawMortarCourses = true
                )
                drawWarmFlickerGlow(scope, cx, cy - unitH * 0.82f, unitH * 1.05f, nowMs, tx, ty, quality)
            }
            TileType.BLACKSMITH_ANVIL -> {
                // Heavy oak stump + sculpted horned steel anvil
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.52f, fracV = 0.52f, heightPx = unitH * 0.36f,
                    topColor = Color(0xFF8C643E), faceXColor = Color(0xFF523920), faceYColor = Color(0xFF6B4B2B),
                    quality = quality, isOverland = isOverland
                )
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.68f, fracV = 0.34f, heightPx = unitH * 0.28f, baseZ = unitH * 0.36f,
                    topColor = Color(0xFF939CA8), faceXColor = Color(0xFF3B414A), faceYColor = Color(0xFF58606E),
                    quality = quality, isOverland = isOverland
                )
                // Anvil tapered horn
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.26f, fracV = 0.18f, heightPx = unitH * 0.18f, baseZ = unitH * 0.44f, offsetU = 0.38f,
                    topColor = Color(0xFFB4BECC), faceXColor = Color(0xFF49505C), faceYColor = Color(0xFF697282),
                    quality = quality, isOverland = isOverland
                )
            }
            TileType.FLETCHING_BENCH -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.82f, fracV = 0.52f, heightPx = unitH * 0.52f,
                    topColor = Color(0xFFC4945A), faceXColor = Color(0xFF73502B), faceYColor = Color(0xFF916739),
                    quality = quality, isOverland = isOverland
                )
                scope.drawLine(Color(0xFF5C3A1E), Offset(cx - cam.tileW * 0.18f, cy - unitH * 0.54f), Offset(cx + cam.tileW * 0.18f, cy - unitH * 0.60f), 3.4f)
            }
            TileType.WEAPON_RACK -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.68f, fracV = 0.26f, heightPx = unitH * 0.74f,
                    topColor = Color(0xFF785230), faceXColor = Color(0xFF472F19), faceYColor = Color(0xFF5E3F22),
                    quality = quality, isOverland = isOverland
                )
                // Glinting blades & round shield
                scope.drawLine(Color(0xFFDCE4ED), Offset(cx - 5f, cy - unitH * 0.18f), Offset(cx - 5f, cy - unitH * 0.72f), 2.2f)
                scope.drawLine(Color(0xFFDCE4ED), Offset(cx + 4f, cy - unitH * 0.18f), Offset(cx + 4f, cy - unitH * 0.72f), 2.2f)
                scope.drawCircle(Color(0xFFC9A04A), 4f, Offset(cx, cy - unitH * 0.44f))
            }
            TileType.RUNECRAFTING_TABLE -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.76f, fracV = 0.64f, heightPx = unitH * 0.56f,
                    topColor = Color(0xFF564B78), faceXColor = Color(0xFF332C4A), faceYColor = Color(0xFF443B61),
                    quality = quality, isOverland = isOverland
                )
                val bob = if (quality == GraphicsQuality.LOW) 0f else sin(nowMs * 0.005).toFloat() * 3f
                val orbCenter = Offset(cx, cy - unitH * 0.84f + bob)
                when (quality.lightingMode) {
                    LightingDetailMode.OFF -> Unit
                    LightingDetailMode.STATIC_HALO -> {
                        scope.drawCircle(Color(0x389B6BFF), unitH * 0.46f, orbCenter)
                    }
                    LightingDetailMode.DYNAMIC_FLICKER -> {
                        val pulse = 0.7f + 0.3f * sin(nowMs * 0.006).toFloat()
                        scope.drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFFD7B8FF).copy(alpha = 0.85f * pulse), Color(0xFF7C4DFF).copy(alpha = 0.28f), Color.Transparent),
                                center = orbCenter,
                                radius = unitH * 0.62f
                            ),
                            radius = unitH * 0.62f,
                            center = orbCenter
                        )
                    }
                    LightingDetailMode.HD_MULTI_LAYER -> {
                        val pulse = 0.72f + 0.28f * sin(nowMs * 0.006).toFloat()
                        scope.drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFF9C6BFF).copy(alpha = 0.34f * pulse), Color.Transparent),
                                center = orbCenter,
                                radius = unitH * 0.96f
                            ),
                            radius = unitH * 0.96f,
                            center = orbCenter
                        )
                        scope.drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFFEAD9FF).copy(alpha = 0.92f * pulse), Color(0xFF7C4DFF).copy(alpha = 0.34f), Color.Transparent),
                                center = orbCenter,
                                radius = unitH * 0.62f
                            ),
                            radius = unitH * 0.62f,
                            center = orbCenter
                        )
                    }
                }
                scope.drawCircle(Color(0xFFEAD9FF), 4.5f, orbCenter)
            }
            TileType.BOOKSHELF -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.76f, fracV = 0.36f, heightPx = unitH * 0.96f,
                    topColor = Color(0xFF6E4726), faceXColor = Color(0xFF422914), faceYColor = Color(0xFF57361C),
                    quality = quality, isOverland = isOverland
                )
                // Colorful tome bands across shelves
                if (quality != GraphicsQuality.LOW) {
                    val shelfColors = arrayOf(Color(0xFFC94444), Color(0xFF4788C7), Color(0xFF4DA663), Color(0xFFE0B24C))
                    for (i in 0..3) {
                        scope.drawCircle(shelfColors[i], 2.2f, Offset(cx + (i - 1.5f) * 4.5f, cy - unitH * 0.52f))
                        scope.drawCircle(shelfColors[(i + 2) and 3], 2.2f, Offset(cx + (i - 1.5f) * 4.5f, cy - unitH * 0.76f))
                    }
                }
            }
            TileType.CRYSTAL_PILLAR -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.44f, fracV = 0.44f, heightPx = unitH * 0.45f,
                    topColor = Color(0xFF6B677A), faceXColor = Color(0xFF413E4D), faceYColor = Color(0xFF524E61),
                    quality = quality, isOverland = isOverland
                )
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.24f, fracV = 0.24f, heightPx = unitH * 0.76f, baseZ = unitH * 0.45f,
                    topColor = Color(0xFFE8D5FF), faceXColor = Color(0xFF7C4DFF), faceYColor = Color(0xFFB388FF),
                    quality = quality, isOverland = isOverland
                )
                val glowCenter = Offset(cx, cy - unitH * 0.95f)
                when (quality.lightingMode) {
                    LightingDetailMode.OFF -> Unit
                    LightingDetailMode.STATIC_HALO -> {
                        scope.drawCircle(Color(0x30C79BFF), unitH * 0.52f, glowCenter)
                    }
                    LightingDetailMode.DYNAMIC_FLICKER -> {
                        val pulse = 0.72f + 0.28f * sin(nowMs * 0.005 + tx).toFloat()
                        scope.drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFFC79BFF).copy(alpha = 0.65f * pulse), Color.Transparent),
                                center = glowCenter,
                                radius = unitH * 0.72f
                            ),
                            radius = unitH * 0.72f,
                            center = glowCenter
                        )
                    }
                    LightingDetailMode.HD_MULTI_LAYER -> {
                        val pulse = 0.74f + 0.26f * sin(nowMs * 0.005 + tx).toFloat()
                        scope.drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFFB388FF).copy(alpha = 0.34f * pulse), Color.Transparent),
                                center = glowCenter,
                                radius = unitH * 1.05f
                            ),
                            radius = unitH * 1.05f,
                            center = glowCenter
                        )
                        scope.drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFFEBD9FF).copy(alpha = 0.76f * pulse), Color.Transparent),
                                center = glowCenter,
                                radius = unitH * 0.62f
                            ),
                            radius = unitH * 0.62f,
                            center = glowCenter
                        )
                    }
                }
            }
            TileType.MOORED_BOAT -> drawMooredRowboat(scope, path, cam, cx, cy, nowMs, tx, ty, quality)
            TileType.ORE_COPPER, TileType.ORE_TIN, TileType.ORE_BOG_IRON,
            TileType.ORE_VESPERITE, TileType.ORE_CINDERITE, TileType.DEPLETED_VEIN -> {
                val (rockTop, rockFaceX, rockFaceY) = when (tile) {
                    TileType.ORE_COPPER -> Triple(Color(0xFF73675D), Color(0xFF473F38), Color(0xFF5C524A))
                    TileType.ORE_TIN -> Triple(Color(0xFF757678), Color(0xFF494A4D), Color(0xFF5E6063))
                    TileType.ORE_BOG_IRON -> Triple(Color(0xFF58605C), Color(0xFF353B38), Color(0xFF454C49))
                    TileType.ORE_VESPERITE -> Triple(Color(0xFF4F4668), Color(0xFF2D273D), Color(0xFF3D3552))
                    TileType.ORE_CINDERITE -> Triple(Color(0xFF34282C), Color(0xFF1D1519), Color(0xFF281E22))
                    else -> Triple(Color(0xFF635E5A), Color(0xFF3D3A37), Color(0xFF4F4B47))
                }
                val (veinColor, glintColor) = when (tile) {
                    TileType.ORE_COPPER -> Color(0xFFE08234) to Color(0xFFFFCA85)
                    TileType.ORE_TIN -> Color(0xFFA9B9C8) to Color(0xFFF0F6FC)
                    TileType.ORE_BOG_IRON -> Color(0xFF7D90A3) to Color(0xFFC5D6E8)
                    TileType.ORE_VESPERITE -> Color(0xFFA782FF) to Color(0xFFF0E6FF)
                    TileType.ORE_CINDERITE -> Color(0xFFFFAA2B) to Color(0xFFFFF2A6)
                    else -> Color(0xFF5C5854) to Color(0xFF78736E)
                }
                val heightMult = when (tile) {
                    TileType.ORE_VESPERITE -> 0.78f
                    TileType.ORE_CINDERITE -> 0.82f
                    TileType.DEPLETED_VEIN -> 0.44f
                    else -> 0.68f
                }
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.74f, fracV = 0.70f, heightPx = unitH * heightMult,
                    topColor = rockTop, faceXColor = rockFaceX, faceYColor = rockFaceY,
                    quality = quality, isOverland = isOverland
                )
                if (tile != TileType.DEPLETED_VEIN) {
                    val shimmer = 0.76f + 0.24f * sin(nowMs * 0.0055 + tx * 1.3 + ty * 0.9).toFloat()
                    if (tile == TileType.ORE_VESPERITE || tile == TileType.ORE_CINDERITE) {
                        val auraCenter = Offset(cx, cy - unitH * 0.48f)
                        scope.drawCircle(
                            brush = Brush.radialGradient(
                                listOf(veinColor.copy(alpha = 0.34f * shimmer), Color.Transparent),
                                center = auraCenter,
                                radius = cam.tileW * 0.34f
                            ),
                            radius = cam.tileW * 0.34f,
                            center = auraCenter
                        )
                    }
                    // Primary 3D mineral seams & specular glints
                    scope.drawCircle(veinColor, cam.tileW * 0.072f, Offset(cx - cam.tileW * 0.09f, cy - unitH * 0.46f))
                    scope.drawCircle(glintColor.copy(alpha = shimmer), cam.tileW * 0.034f, Offset(cx - cam.tileW * 0.10f, cy - unitH * 0.49f))
                    scope.drawCircle(veinColor.copy(alpha = 0.92f), cam.tileW * 0.062f, Offset(cx + cam.tileW * 0.10f, cy - unitH * 0.36f))
                    scope.drawCircle(glintColor.copy(alpha = shimmer), cam.tileW * 0.028f, Offset(cx + cam.tileW * 0.09f, cy - unitH * 0.39f))
                    scope.drawCircle(veinColor.copy(alpha = 0.85f), cam.tileW * 0.052f, Offset(cx + cam.tileW * 0.01f, cy - unitH * 0.62f))
                    if (tile == TileType.ORE_VESPERITE || tile == TileType.ORE_CINDERITE) {
                        // Protruding crystalline / molten spire crest
                        scope.drawLine(
                            color = glintColor.copy(alpha = 0.92f * shimmer),
                            start = Offset(cx, cy - unitH * 0.56f),
                            end = Offset(cx + cam.tileW * 0.03f, cy - unitH * (heightMult + 0.18f)),
                            strokeWidth = (cam.tileW * 0.048f).coerceAtLeast(2.2f),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            else -> Unit
        }
    }

    /**
     * Tall (~2.4x tile height) white plaster walls divided by dark vertical oak timber beams,
     * dressed stone foundation trim, timber cross-rails, glowing mullioned windows, and overhead
     * dark oak lintel crossbeams spanning double-wide doorways. Automatically lowers & fades in front of player.
     */
    private fun drawDetailedTimberWall(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        floorMap: WorldFloor,
        tx: Int,
        ty: Int,
        cx: Float,
        cy: Float,
        hasWindow: Boolean,
        nowMs: Long,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT,
        cutaway: WallCutaway = WallCutaway(1f, 1f)
    ) {
        val h = cam.tileH
        val cScale = cutaway.heightScale
        val cAlpha = cutaway.alpha
        val hasN = isBuildingWall(floorMap.tileAt(tx, ty - 1))
        val hasS = isBuildingWall(floorMap.tileAt(tx, ty + 1))
        val hasW = isBuildingWall(floorMap.tileAt(tx - 1, ty))
        val hasE = isBuildingWall(floorMap.tileAt(tx + 1, ty))

        val totalH = h * TALL_WALL_HEIGHT_MULT * cScale
        val foundationH = totalH * 0.16f
        val plasterH = totalH * 0.84f
        val beamThickness = 0.34f

        val oakTop = Color(0xFF593B22)
        val oakSideA = Color(0xFF3C2515)
        val oakSideB = Color(0xFF4B301B)
        val plasterFaceA = Color(0xFFEFE6D4) // Crisp bright alabaster-white plaster
        val plasterFaceB = Color(0xFFD8CCB6)

        val isOverland = floorMap.floorId == 0
        fun drawSegment(fracU: Float, fracV: Float, offU: Float, offV: Float) {
            // Dressed stone foundation plinth
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = fracU + 0.05f, fracV = fracV + 0.05f,
                heightPx = foundationH, offsetU = offU, offsetV = offV,
                topColor = Color(0xFF8C857A), faceXColor = Color(0xFF5D574F), faceYColor = Color(0xFF706960),
                quality = quality, isOverland = isOverland, alpha = cAlpha
            )
            // White plaster wall panel
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = fracU, fracV = fracV,
                heightPx = plasterH, baseZ = foundationH, offsetU = offU, offsetV = offV,
                topColor = oakTop, faceXColor = plasterFaceB, faceYColor = plasterFaceA,
                quality = quality, isOverland = isOverland, alpha = cAlpha
            )
            // Mid-height dark oak horizontal timber beam divider
            if (cScale > 0.55f) {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = fracU + 0.02f, fracV = fracV + 0.02f,
                    heightPx = h * 0.14f * cScale, baseZ = foundationH + plasterH * 0.46f, offsetU = offU, offsetV = offV,
                    topColor = oakTop, faceXColor = oakSideA, faceYColor = oakSideB,
                    quality = quality, isOverland = isOverland, alpha = cAlpha
                )
            }
            // Top dark oak wall-plate cap beam
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = fracU + 0.04f, fracV = fracV + 0.04f,
                heightPx = h * 0.14f * cScale, baseZ = foundationH + plasterH - h * 0.08f * cScale, offsetU = offU, offsetV = offV,
                topColor = oakTop, faceXColor = oakSideA, faceYColor = oakSideB,
                quality = quality, isOverland = isOverland, alpha = cAlpha
            )
        }

        // Core dark vertical oak timber post framing every tile bay & corner
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.38f, fracV = 0.38f,
            heightPx = totalH + h * 0.06f * cScale,
            topColor = oakTop, faceXColor = oakSideA, faceYColor = oakSideB,
            quality = quality, isOverland = isOverland, alpha = cAlpha
        )

        if (hasE) drawSegment(0.50f, beamThickness, 0.25f, 0f)
        if (hasW) drawSegment(0.50f, beamThickness, -0.25f, 0f)
        if (hasS) drawSegment(beamThickness, 0.50f, 0f, 0.25f)
        if (hasN) drawSegment(beamThickness, 0.50f, 0f, -0.25f)

        // Overhead dark oak lintel crossbeam above double-wide open doorways
        if (cScale > 0.62f) {
            drawDoorwayTimberLintelIfAdjacent(scope, path, cam, floorMap, tx, ty, cx, cy, totalH, cAlpha, quality)
        }

        // Warm glowing mullioned window
        if (hasWindow && cScale > 0.65f) {
            val flicker = if (quality == GraphicsQuality.LOW) 0.9f else 0.85f + 0.15f * sin(nowMs * 0.005 + tx * 2.1).toFloat()
            val winY = cy - foundationH - plasterH * 0.56f
            val winW = cam.tileW * 0.17f
            val winH = h * 0.54f * cScale
            if (quality.drawHdStonePolish) {
                scope.drawCircle(TorchGold.copy(alpha = 0.24f * flicker * cAlpha), winW * 1.15f, Offset(cx, winY))
            }
            scope.drawRect(oakSideA.copy(alpha = cAlpha), Offset(cx - winW * 0.62f, winY - winH * 0.56f), Size(winW * 1.24f, winH * 1.12f))
            scope.drawRect(Color(0xFFFFDA7E).copy(alpha = flicker * cAlpha), Offset(cx - winW * 0.46f, winY - winH * 0.42f), Size(winW * 0.92f, winH * 0.84f))
            if (quality != GraphicsQuality.LOW) {
                scope.drawLine(oakSideA.copy(alpha = cAlpha), Offset(cx, winY - winH * 0.42f), Offset(cx, winY + winH * 0.42f), 1.4f * quality.strokeScale)
                scope.drawLine(oakSideA.copy(alpha = cAlpha), Offset(cx - winW * 0.46f, winY), Offset(cx + winW * 0.46f, winY), 1.4f * quality.strokeScale)
            }
        }
    }

    /**
     * Tall (~2.4x tile height) textured mortared cobblestone walls for stone structures (Forge, Dungeons)
     * with stone foundation trim, timber lintel doorway beams, and dynamic foreground cutaway.
     */
    private fun drawDetailedStoneWall(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        floorMap: WorldFloor,
        tx: Int,
        ty: Int,
        cx: Float,
        cy: Float,
        hasTorch: Boolean,
        nowMs: Long,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT,
        cutaway: WallCutaway = WallCutaway(1f, 1f)
    ) {
        val h = cam.tileH
        val cScale = cutaway.heightScale
        val cAlpha = cutaway.alpha
        val isOverland = floorMap.floorId == 0
        val hasN = isBuildingWall(floorMap.tileAt(tx, ty - 1))
        val hasS = isBuildingWall(floorMap.tileAt(tx, ty + 1))
        val hasW = isBuildingWall(floorMap.tileAt(tx - 1, ty))
        val hasE = isBuildingWall(floorMap.tileAt(tx + 1, ty))

        val totalH = h * TALL_WALL_HEIGHT_MULT * cScale
        val plinthH = totalH * 0.16f
        val wallH = totalH * 0.84f
        val thick = if (isOverland) 0.44f else 0.72f

        fun drawSeg(fracU: Float, fracV: Float, offU: Float, offV: Float) {
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = fracU + 0.05f, fracV = fracV + 0.05f,
                heightPx = plinthH, offsetU = offU, offsetV = offV,
                topColor = Color(0xFF706A61), faceXColor = Color(0xFF47423C), faceYColor = Color(0xFF5A544D),
                quality = quality, isOverland = isOverland, alpha = cAlpha
            )
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = fracU, fracV = fracV,
                heightPx = wallH, baseZ = plinthH, offsetU = offU, offsetV = offV,
                topColor = Color(0xFF8C8478), faceXColor = Color(0xFF5E574E), faceYColor = Color(0xFF787066),
                quality = quality, isOverland = isOverland, alpha = cAlpha, drawMortarCourses = true
            )
        }

        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = thick + 0.04f, fracV = thick + 0.04f,
            heightPx = totalH + h * 0.04f * cScale,
            topColor = Color(0xFF968E82), faceXColor = Color(0xFF59534A), faceYColor = Color(0xFF70685E),
            quality = quality, isOverland = isOverland, alpha = cAlpha, drawMortarCourses = true
        )
        if (hasE) drawSeg(0.50f, thick, 0.25f, 0f)
        if (hasW) drawSeg(0.50f, thick, -0.25f, 0f)
        if (hasS) drawSeg(thick, 0.50f, 0f, 0.25f)
        if (hasN) drawSeg(thick, 0.50f, 0f, -0.25f)

        if (isOverland && cScale > 0.62f) {
            drawDoorwayTimberLintelIfAdjacent(scope, path, cam, floorMap, tx, ty, cx, cy, totalH, cAlpha, quality)
        }
        // Damp moss patches & cracked dungeon mortar underground
        if (!isOverland && quality != GraphicsQuality.LOW && cScale > 0.55f) {
            val hSeed = tileHash(tx, ty, 47) and 15
            if (floorMap.floorId == -1 && hSeed < 8) {
                scope.drawCircle(
                    Color(0xFF3C6E3B).copy(alpha = 0.62f * cAlpha),
                    cam.tileW * 0.075f,
                    Offset(cx - cam.tileW * 0.12f, cy - totalH * 0.30f)
                )
                scope.drawCircle(
                    Color(0xFF294E28).copy(alpha = 0.52f * cAlpha),
                    cam.tileW * 0.055f,
                    Offset(cx + cam.tileW * 0.10f, cy - totalH * 0.42f)
                )
            }
            if (hSeed % 3 == 1) {
                val crackColor = Color(0xB0171412).copy(alpha = 0.7f * cAlpha)
                scope.drawLine(crackColor, Offset(cx - cam.tileW * 0.07f, cy - totalH * 0.68f), Offset(cx + cam.tileW * 0.04f, cy - totalH * 0.44f), 1.5f)
                scope.drawLine(crackColor, Offset(cx + cam.tileW * 0.04f, cy - totalH * 0.44f), Offset(cx - cam.tileW * 0.01f, cy - totalH * 0.24f), 1.2f)
            }
        }
        if (hasTorch && cScale > 0.55f) {
            // Wrought-iron wall sconce bracket + flickering torch head
            val sconceY = cy - totalH * 0.68f
            val ironColor = Color(0xFF252830).copy(alpha = cAlpha)
            scope.drawLine(ironColor, Offset(cx - cam.tileW * 0.03f, sconceY + h * 0.24f), Offset(cx + cam.tileW * 0.04f, sconceY + h * 0.04f), 3.0f)
            scope.drawLine(Color(0xFF593E26).copy(alpha = cAlpha), Offset(cx + cam.tileW * 0.04f, sconceY + h * 0.04f), Offset(cx + cam.tileW * 0.06f, sconceY - h * 0.14f), 3.4f)
            drawWarmFlickerGlow(scope, cx + cam.tileW * 0.06f, sconceY - h * 0.16f, h * 1.22f, nowMs, tx, ty, quality)
        }
    }

    /**
     * Walk-under vaulted dungeon archway with raised wrought-iron portcullis bars.
     * Automatically softens/lowers via [cutaway] when standing in front of the player.
     */
    private fun drawIronBarGateArch(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        floorMap: WorldFloor,
        tx: Int,
        ty: Int,
        cx: Float,
        cy: Float,
        quality: GraphicsQuality,
        cutaway: WallCutaway
    ) {
        val h = cam.tileH
        val cScale = cutaway.heightScale
        val cAlpha = cutaway.alpha
        val totalH = h * (TALL_WALL_HEIGHT_MULT * 0.92f) * cScale

        val hasWallW = isBuildingWall(floorMap.tileAt(tx - 1, ty)) || floorMap.tileAt(tx - 1, ty) == TileType.IRON_BAR_GATE
        val hasWallE = isBuildingWall(floorMap.tileAt(tx + 1, ty)) || floorMap.tileAt(tx + 1, ty) == TileType.IRON_BAR_GATE
        val spansAlongX = hasWallW || hasWallE

        val ironDark = Color(0xFF20242C).copy(alpha = cAlpha)
        val ironMid = Color(0xFF49505C).copy(alpha = cAlpha)
        val ironHighlight = Color(0xFF838C9C).copy(alpha = cAlpha)

        // Overhead stone arch beam (leaves clear headroom so characters walk cleanly beneath!)
        val archBaseZ = totalH * 0.68f
        val archThickness = totalH * 0.24f
        if (spansAlongX) {
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = 1.02f, fracV = 0.28f,
                heightPx = archThickness, baseZ = archBaseZ,
                topColor = Color(0xFF7B756D), faceXColor = Color(0xFF4B4742), faceYColor = Color(0xFF615C55),
                quality = quality, isOverland = false, alpha = cAlpha, drawMortarCourses = true
            )
        } else {
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = 0.28f, fracV = 1.02f,
                heightPx = archThickness, baseZ = archBaseZ,
                topColor = Color(0xFF7B756D), faceXColor = Color(0xFF4B4742), faceYColor = Color(0xFF615C55),
                quality = quality, isOverland = false, alpha = cAlpha, drawMortarCourses = true
            )
        }

        // Raised wrought-iron portcullis spikes hanging from the stone arch lintel + side iron frame rails
        if (cScale > 0.45f) {
            val ax = if (spansAlongX) cam.ux else cam.vx
            val ay = if (spansAlongX) cam.uy else cam.vy
            val barStroke = (cam.tileW * 0.032f).coerceIn(1.8f, 3.6f)
            val topYOff = archBaseZ
            val teethBottomOff = totalH * 0.36f
            for (i in -2..2) {
                val frac = i * 0.20f
                val bx = cx + ax * frac
                val by = cy + ay * frac
                val barBottom = if (i == -2 || i == 2) 0f else teethBottomOff
                scope.drawLine(ironDark, Offset(bx, by - topYOff), Offset(bx, by - barBottom), barStroke + 1.1f)
                scope.drawLine(ironMid, Offset(bx, by - topYOff), Offset(bx, by - barBottom), barStroke * 0.72f)
                if (i != -2 && i != 2) {
                    // Pointed iron portcullis tooth tip
                    scope.drawCircle(ironHighlight, barStroke * 0.55f, Offset(bx, by - barBottom))
                }
            }
            // Crossbar tying the raised portcullis spears together
            val pL = Offset(cx - ax * 0.44f, cy - ay * 0.44f - totalH * 0.52f)
            val pR = Offset(cx + ax * 0.44f, cy + ay * 0.44f - totalH * 0.52f)
            scope.drawLine(ironDark, pL, pR, barStroke + 1.0f)
            scope.drawLine(ironHighlight, pL, pR, barStroke * 0.55f)
        }
    }

    /**
     * Tall (~2.4x tile height) dressed pale-limestone Temple Sanctuary walls with mortared ashlar seams,
     * golden frieze trim, glowing stained glass lancets, timber doorway lintels, and foreground cutaway.
     */
    private fun drawDetailedTempleWall(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        floorMap: WorldFloor,
        tx: Int,
        ty: Int,
        cx: Float,
        cy: Float,
        nowMs: Long,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT,
        cutaway: WallCutaway = WallCutaway(1f, 1f)
    ) {
        val h = cam.tileH
        val cScale = cutaway.heightScale
        val cAlpha = cutaway.alpha
        val isOverland = floorMap.floorId == 0
        val hasN = isBuildingWall(floorMap.tileAt(tx, ty - 1))
        val hasS = isBuildingWall(floorMap.tileAt(tx, ty + 1))
        val hasW = isBuildingWall(floorMap.tileAt(tx - 1, ty))
        val hasE = isBuildingWall(floorMap.tileAt(tx + 1, ty))
        val beamThickness = 0.38f
        val totalH = h * TALL_WALL_HEIGHT_MULT * cScale
        val plinthH = totalH * 0.15f
        val wallH = totalH * 0.85f

        fun drawSeg(fracU: Float, fracV: Float, offU: Float, offV: Float) {
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = fracU + 0.05f, fracV = fracV + 0.05f,
                heightPx = plinthH, offsetU = offU, offsetV = offV,
                topColor = Color(0xFFB5AA98), faceXColor = Color(0xFF82796B), faceYColor = Color(0xFF998F7F),
                quality = quality, isOverland = isOverland, alpha = cAlpha
            )
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = fracU, fracV = fracV,
                heightPx = wallH, baseZ = plinthH, offsetU = offU, offsetV = offV,
                topColor = Color(0xFFD9B86A), faceXColor = Color(0xFFC9BEAA), faceYColor = Color(0xFFE6DDD0),
                quality = quality, isOverland = isOverland, alpha = cAlpha, drawMortarCourses = true
            )
        }

        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.42f, fracV = 0.42f,
            heightPx = totalH + h * 0.05f * cScale,
            topColor = Color(0xFFE0C070), faceXColor = Color(0xFFB3A894), faceYColor = Color(0xFFD1C7B4),
            quality = quality, isOverland = isOverland, alpha = cAlpha, drawMortarCourses = true
        )
        if (hasE) drawSeg(0.50f, beamThickness, 0.25f, 0f)
        if (hasW) drawSeg(0.50f, beamThickness, -0.25f, 0f)
        if (hasS) drawSeg(beamThickness, 0.50f, 0f, 0.25f)
        if (hasN) drawSeg(beamThickness, 0.50f, 0f, -0.25f)

        if (cScale > 0.62f) {
            drawDoorwayTimberLintelIfAdjacent(scope, path, cam, floorMap, tx, ty, cx, cy, totalH, cAlpha, quality)
        }

        // Glowing stained-glass lancet accent on every third temple wall bay
        if (quality != GraphicsQuality.LOW && cScale > 0.65f && (tx + ty) % 3 == 0) {
            val shimmer = 0.8f + 0.2f * sin(nowMs * 0.004 + tx).toFloat()
            val gy = cy - totalH * 0.58f
            if (quality.drawHdStonePolish) {
                scope.drawCircle(Color(0xFF63B8FF).copy(alpha = 0.24f * shimmer * cAlpha), 13f, Offset(cx, gy))
            }
            scope.drawOval(Color(0xFF63B8FF).copy(alpha = shimmer * cAlpha), Offset(cx - 5f, gy - 11f), Size(10f, 18f))
            scope.drawOval(TorchGold.copy(alpha = cAlpha), Offset(cx - 5f, gy - 11f), Size(10f, 18f), style = Stroke(1.4f * quality.strokeScale))
        }
    }

    /**
     * Automatically detects double-wide (2- or 3-tile) doorway openings immediately East or South of a
     * wall jamb and spans a crisp dark-oak lintel header crossbeam overhead at ~88% wall height.
     */
    private fun drawDoorwayTimberLintelIfAdjacent(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        floorMap: WorldFloor,
        tx: Int,
        ty: Int,
        cx: Float,
        cy: Float,
        wallTotalH: Float,
        alpha: Float,
        quality: GraphicsQuality
    ) {
        val lintelZ = wallTotalH * 0.84f
        val lintelH = (wallTotalH * 0.14f).coerceAtLeast(6f)
        val oakTop = Color(0xFF614025)
        val oakDark = Color(0xFF3A2313)
        val oakMid = Color(0xFF4F321B)

        // Check Eastward 2- or 3-tile doorway span along X
        if (isIndoorWalkableFloor(floorMap.tileAt(tx + 1, ty))) {
            val span = when {
                isIndoorWalkableFloor(floorMap.tileAt(tx + 2, ty)) && isBuildingWall(floorMap.tileAt(tx + 3, ty)) -> 2
                isIndoorWalkableFloor(floorMap.tileAt(tx + 2, ty)) &&
                    isIndoorWalkableFloor(floorMap.tileAt(tx + 3, ty)) &&
                    isBuildingWall(floorMap.tileAt(tx + 4, ty)) -> 3
                else -> 0
            }
            if (span > 0) {
                drawRotatedPrism(
                    scope, path, cam, cx, cy,
                    fracU = span.toFloat() + 0.45f,
                    fracV = 0.34f,
                    heightPx = lintelH,
                    baseZ = lintelZ,
                    offsetU = (span + 1) * 0.5f,
                    offsetV = 0f,
                    topColor = oakTop,
                    faceXColor = oakDark,
                    faceYColor = oakMid,
                    quality = quality,
                    isOverland = floorMap.floorId == 0,
                    alpha = alpha
                )
            }
        }

        // Check Southward 2- or 3-tile doorway span along Y
        if (isIndoorWalkableFloor(floorMap.tileAt(tx, ty + 1))) {
            val span = when {
                isIndoorWalkableFloor(floorMap.tileAt(tx, ty + 2)) && isBuildingWall(floorMap.tileAt(tx, ty + 3)) -> 2
                isIndoorWalkableFloor(floorMap.tileAt(tx, ty + 2)) &&
                    isIndoorWalkableFloor(floorMap.tileAt(tx, ty + 3)) &&
                    isBuildingWall(floorMap.tileAt(tx, ty + 4)) -> 3
                else -> 0
            }
            if (span > 0) {
                drawRotatedPrism(
                    scope, path, cam, cx, cy,
                    fracU = 0.34f,
                    fracV = span.toFloat() + 0.45f,
                    heightPx = lintelH,
                    baseZ = lintelZ,
                    offsetU = 0f,
                    offsetV = (span + 1) * 0.5f,
                    topColor = oakTop,
                    faceXColor = oakDark,
                    faceYColor = oakMid,
                    quality = quality,
                    isOverland = floorMap.floorId == 0,
                    alpha = alpha
                )
            }
        }
    }

    /**
     * Cozy wooden bed interior prop with dark oak headboard/footboard, quilted crimson blanket,
     * crisp cream folded top sheet, and plump linen pillow.
     */
    private fun drawWoodenBed(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        cx: Float,
        cy: Float,
        quality: GraphicsQuality
    ) {
        val h = cam.tileH
        // Oak bed frame base
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.84f, fracV = 0.62f, heightPx = h * 0.24f,
            topColor = Color(0xFF7D5430), faceXColor = Color(0xFF472C16), faceYColor = Color(0xFF633F22),
            quality = quality, isOverland = true
        )
        // Quilted crimson wool blanket
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.78f, fracV = 0.56f, heightPx = h * 0.16f, baseZ = h * 0.22f, offsetU = 0.04f,
            topColor = Color(0xFFB83530), faceXColor = Color(0xFF751D1A), faceYColor = Color(0xFF942723),
            quality = quality, isOverland = true
        )
        // Folded cream linen sheet band
        drawRotatedQuad(
            scope, path, cam, cx, cy, fracU = 0.18f, fracV = 0.56f, offsetU = -0.14f, zPx = h * 0.39f,
            fillColor = Color(0xFFF2E8D5), strokeColor = Color(0x5514100E), strokeWidth = 1.0f
        )
        // Plump cream pillow at headboard end
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.22f, fracV = 0.44f, heightPx = h * 0.12f, baseZ = h * 0.34f, offsetU = -0.28f,
            topColor = Color(0xFFFFF8EB), faceXColor = Color(0xFFC9BEA8), faceYColor = Color(0xFFE0D6C3),
            quality = quality, isOverland = true
        )
        // Tall dark oak headboard
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.12f, fracV = 0.64f, heightPx = h * 0.64f, offsetU = -0.40f,
            topColor = Color(0xFF6E4726), faceXColor = Color(0xFF3E2614), faceYColor = Color(0xFF54351C),
            quality = quality, isOverland = true
        )
    }

    /**
     * Renders a pitched cutaway roof over a [BuildingFootprint].
     * Smoothly fades translucent when the player approaches and cuts away completely when indoors!
     */
    fun drawBuildingCutawayRoof(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        building: BuildingFootprint,
        playerFx: Float,
        playerFy: Float,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT
    ) {
        if (!quality.drawCutawayRoofs) return
        val dist = building.distanceToPoint(playerFx, playerFy)
        val alpha = when {
            building.containsPoint(playerFx, playerFy, margin = 0.9f) -> 0f
            dist < 4.0f -> ((dist - 0.9f) / 3.1f).coerceIn(0f, 1f) * 0.78f
            else -> 0.84f
        }
        if (alpha <= 0.03f) return

        val wallTopZ = cam.tileH * 1.32f
        val ridgeZ = wallTopZ + cam.tileH * 1.15f
        val pad = 0.35f
        val x0 = building.minX - pad
        val x1 = building.maxX + pad
        val y0 = building.minY - pad
        val y1 = building.maxY + pad
        val midY = (y0 + y1) * 0.5f

        val nw = cam.tileCenterToScreen(x0, y0).let { Offset(it.x, it.y - wallTopZ) }
        val ne = cam.tileCenterToScreen(x1, y0).let { Offset(it.x, it.y - wallTopZ) }
        val se = cam.tileCenterToScreen(x1, y1).let { Offset(it.x, it.y - wallTopZ) }
        val sw = cam.tileCenterToScreen(x0, y1).let { Offset(it.x, it.y - wallTopZ) }
        val ridgeW = cam.tileCenterToScreen(x0, midY).let { Offset(it.x, it.y - ridgeZ) }
        val ridgeE = cam.tileCenterToScreen(x1, midY).let { Offset(it.x, it.y - ridgeZ) }

        val baseMain = Color(building.roofColorArgb)
        val baseHi = Color(building.roofHighlightArgb)
        val northCol = IsometricLightingEngine.shadeColorForFace(baseHi, 0f, -0.72f, 0.69f, true, quality).copy(alpha = alpha)
        val southCol = IsometricLightingEngine.shadeColorForFace(baseMain, 0f, 0.72f, 0.69f, true, quality).copy(alpha = alpha)
        val westCol = IsometricLightingEngine.shadeColorForFace(baseMain, -1f, 0f, 0.2f, true, quality).copy(alpha = alpha)
        val eastCol = IsometricLightingEngine.shadeColorForFace(baseMain, 1f, 0f, 0.2f, true, quality).copy(alpha = alpha)
        val trimCol = Color(building.roofTrimArgb).copy(alpha = alpha)

        // North roof slope (catches NW twilight rim light)
        path.rewind()
        path.moveTo(nw.x, nw.y); path.lineTo(ne.x, ne.y); path.lineTo(ridgeE.x, ridgeE.y); path.lineTo(ridgeW.x, ridgeW.y); path.close()
        scope.drawPath(path, color = northCol)
        scope.drawPath(path, color = trimCol, style = Stroke(width = 1.6f * quality.strokeScale))

        // South roof slope (cooler dusk shadow side)
        path.rewind()
        path.moveTo(sw.x, sw.y); path.lineTo(se.x, se.y); path.lineTo(ridgeE.x, ridgeE.y); path.lineTo(ridgeW.x, ridgeW.y); path.close()
        scope.drawPath(path, color = southCol)
        scope.drawPath(path, color = trimCol, style = Stroke(width = 1.6f * quality.strokeScale))

        // West & East gables
        path.rewind()
        path.moveTo(nw.x, nw.y); path.lineTo(sw.x, sw.y); path.lineTo(ridgeW.x, ridgeW.y); path.close()
        scope.drawPath(path, color = westCol)

        path.rewind()
        path.moveTo(ne.x, ne.y); path.lineTo(se.x, se.y); path.lineTo(ridgeE.x, ridgeE.y); path.close()
        scope.drawPath(path, color = eastCol)

        // HD pitched shingle highlight courses
        if (quality.drawHdStonePolish) {
            val courseCol = Color.White.copy(alpha = alpha * 0.16f)
            val m1a = Offset((sw.x + ridgeW.x) * 0.5f, (sw.y + ridgeW.y) * 0.5f)
            val m1b = Offset((se.x + ridgeE.x) * 0.5f, (se.y + ridgeE.y) * 0.5f)
            scope.drawLine(courseCol, m1a, m1b, strokeWidth = 1.4f)
            val n1a = Offset((nw.x + ridgeW.x) * 0.5f, (nw.y + ridgeW.y) * 0.5f)
            val n1b = Offset((ne.x + ridgeE.x) * 0.5f, (ne.y + ridgeE.y) * 0.5f)
            scope.drawLine(Color.White.copy(alpha = alpha * 0.22f), n1a, n1b, strokeWidth = 1.4f)
        }

        // Ridge beam highlight
        scope.drawLine(trimCol, ridgeW, ridgeE, strokeWidth = 2.6f * quality.strokeScale)
    }

    private fun drawStoneFountain(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        cx: Float,
        cy: Float,
        nowMs: Long,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT
    ) {
        val h = cam.tileH
        if (quality.drawCastShadows) {
            IsometricLightingEngine.drawAngledCastShadow(scope, cam, cx, cy, radiusScale = 1.25f, heightTiles = 0.70f, quality = quality)
        }
        // Wide octagonal stone fountain basin
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.90f, fracV = 0.90f, heightPx = h * 0.34f,
            topColor = Color(0xFFB5ADA0), faceXColor = Color(0xFF7D766B), faceYColor = Color(0xFF968E82),
            quality = quality, isOverland = true
        )
        // Sparkling azure water pool
        drawRotatedQuad(scope, path, cam, cx, cy, fracU = 0.72f, fracV = 0.72f, zPx = h * 0.35f, fillColor = Color(0xFF399FD9))
        // Center pedestal & animated water plume
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.24f, fracV = 0.24f, heightPx = h * 0.52f, baseZ = h * 0.34f,
            topColor = Color(0xFFD1C9BC), faceXColor = Color(0xFF8C857A), faceYColor = Color(0xFFA8A094),
            quality = quality, isOverland = true
        )
        val spray = if (quality == GraphicsQuality.LOW) 0f else sin(nowMs * 0.008).toFloat() * 3f
        val topY = cy - h * 0.96f + spray
        scope.drawCircle(Color(0xBFE0F7FF), cam.tileW * 0.08f, Offset(cx, topY))
        if (quality != GraphicsQuality.LOW) {
            scope.drawLine(Color(0xAA88D8FF), Offset(cx, topY), Offset(cx - cam.tileW * 0.16f, cy - h * 0.36f), 2f * quality.strokeScale)
            scope.drawLine(Color(0xAA88D8FF), Offset(cx, topY), Offset(cx + cam.tileW * 0.16f, cy - h * 0.36f), 2f * quality.strokeScale)
        }
        if (quality.drawEnhancedWater) {
            scope.drawCircle(Color(0x88FFFFFF), cam.tileW * 0.04f, Offset(cx, topY - 2f))
        }
    }

    private fun drawWoodenFence(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        floorMap: WorldFloor,
        tx: Int,
        ty: Int,
        cx: Float,
        cy: Float,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT
    ) {
        val h = cam.tileH
        val postCol = Color(0xFF6B4728)
        val railCol = Color(0xFF8C623B)
        // Center fence post
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.16f, fracV = 0.16f, heightPx = h * 0.56f,
            topColor = railCol, faceXColor = Color(0xFF4A301A), faceYColor = postCol,
            quality = quality, isOverland = floorMap.floorId == 0
        )
        fun railTo(dx: Int, dy: Int) {
            val n = floorMap.tileAt(tx + dx, ty + dy)
            if (n == TileType.WOOD_FENCE || n == TileType.HEDGE || n == TileType.GATE_PILLAR || isBuildingWall(n)) {
                val tx2 = cx + (dx * cam.ux + dy * cam.vx) * 0.5f
                val ty2 = cy + (dx * cam.uy + dy * cam.vy) * 0.5f
                scope.drawLine(railCol, Offset(cx, cy - h * 0.44f), Offset(tx2, ty2 - h * 0.44f), 2.4f)
                scope.drawLine(postCol, Offset(cx, cy - h * 0.24f), Offset(tx2, ty2 - h * 0.24f), 2.2f)
            }
        }
        railTo(1, 0); railTo(-1, 0); railTo(0, 1); railTo(0, -1)
    }

    private fun drawLanternPost(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        cx: Float,
        cy: Float,
        nowMs: Long,
        tx: Int,
        ty: Int,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT
    ) {
        val h = cam.tileH
        if (quality.drawCastShadows) {
            IsometricLightingEngine.drawAngledCastShadow(scope, cam, cx, cy, radiusScale = 0.62f, heightTiles = 1.65f, quality = quality)
        }
        // Dressed stone pedestal
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.28f, fracV = 0.28f, heightPx = h * 0.26f,
            topColor = Color(0xFF8C857A), faceXColor = Color(0xFF4F4A44), faceYColor = Color(0xFF666058),
            quality = quality, isOverland = true
        )
        // Tall dark-oak timber street lantern post (~2.15x tile height) with cross-arm & brace
        val postTopY = cy - h * 2.15f
        val armEndX = cx + cam.tileW * 0.16f
        scope.drawLine(VolumetricShading.INK, Offset(cx, cy - h * 0.20f), Offset(cx, postTopY), 6.2f)
        scope.drawLine(Color(0xFF54351C), Offset(cx, cy - h * 0.20f), Offset(cx, postTopY), 4.0f)
        // Diagonal timber bracket & horizontal overhang beam
        scope.drawLine(Color(0xFF3E2614), Offset(cx, postTopY + h * 0.28f), Offset(cx + cam.tileW * 0.10f, postTopY + h * 0.08f), 2.8f)
        scope.drawLine(VolumetricShading.INK, Offset(cx - 3f, postTopY + h * 0.06f), Offset(armEndX + 2f, postTopY + h * 0.06f), 4.6f)
        scope.drawLine(Color(0xFF634023), Offset(cx - 2f, postTopY + h * 0.06f), Offset(armEndX + 1f, postTopY + h * 0.06f), 2.8f)
        // Hanging wrought-iron glass lantern housing + flickering flame
        val lanternY = postTopY + h * 0.26f
        scope.drawLine(Color(0xFF23252A), Offset(armEndX, postTopY + h * 0.06f), Offset(armEndX, lanternY - 5f), 1.8f)
        scope.drawRect(Color(0xFF23252A), Offset(armEndX - 5.5f, lanternY - 6f), Size(11f, 13f))
        scope.drawRect(Color(0xFFFFE07A), Offset(armEndX - 3.5f, lanternY - 4f), Size(7f, 9f))
        drawWarmFlickerGlow(scope, armEndX, lanternY, h * 1.12f, nowMs, tx, ty, quality)
    }

    private fun drawMarketAwningStall(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        cx: Float,
        cy: Float,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT
    ) {
        val h = cam.tileH
        // Timber goods counter underneath
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.78f, fracV = 0.58f, heightPx = h * 0.36f,
            topColor = Color(0xFFB58954), faceXColor = Color(0xFF6B4A26), faceYColor = Color(0xFF876035),
            quality = quality, isOverland = true
        )
        // Four slim wooden canopy posts
        val postCol = Color(0xFF54361C)
        val canopyZ = h * 0.94f
        listOf(-0.34f to -0.24f, 0.34f to -0.24f, 0.34f to 0.24f, -0.34f to 0.24f).forEach { (ou, ov) ->
            val px = cx + ou * cam.ux + ov * cam.vx
            val py = cy + ou * cam.uy + ov * cam.vy
            scope.drawLine(postCol, Offset(px, py), Offset(px, py - canopyZ), 2.2f)
        }
        // Crimson & cream striped market awning canopy
        drawRotatedQuad(scope, path, cam, cx, cy, fracU = 0.88f, fracV = 0.72f, zPx = canopyZ, fillColor = Color(0xFFB5322A), strokeColor = Color(0xFF5E1B16), strokeWidth = 1.4f)
        drawRotatedQuad(scope, path, cam, cx, cy, fracU = 0.28f, fracV = 0.70f, offsetU = -0.22f, zPx = canopyZ + 1f, fillColor = Color(0xFFF0E2C4))
        drawRotatedQuad(scope, path, cam, cx, cy, fracU = 0.28f, fracV = 0.70f, offsetU = 0.22f, zPx = canopyZ + 1f, fillColor = Color(0xFFF0E2C4))
    }

    private fun drawCrateAndBarrel(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        cx: Float,
        cy: Float,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT
    ) {
        val h = cam.tileH
        // Wooden crate
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.44f, fracV = 0.44f, heightPx = h * 0.44f, offsetU = -0.18f, offsetV = -0.06f,
            topColor = Color(0xFFB88B56), faceXColor = Color(0xFF73502B), faceYColor = Color(0xFF946B3C),
            quality = quality, isOverland = true
        )
        // Oak barrel beside crate
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.34f, fracV = 0.34f, heightPx = h * 0.50f, offsetU = 0.20f, offsetV = 0.14f,
            topColor = Color(0xFF8C5D33), faceXColor = Color(0xFF52331A), faceYColor = Color(0xFF6B4524),
            quality = quality, isOverland = true
        )
    }

    private fun drawMooredRowboat(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        cx: Float,
        cy: Float,
        nowMs: Long,
        tx: Int,
        ty: Int,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT
    ) {
        val bob = if (quality == GraphicsQuality.LOW) 0f else sin(nowMs * 0.0035 + tx + ty).toFloat() * 2.2f
        val h = cam.tileH
        // Gentle water wake around hull
        if (quality != GraphicsQuality.LOW) {
            scope.drawOval(
                Color(0x48D6F5FF),
                Offset(cx - cam.tileW * 0.36f, cy - h * 0.20f + bob),
                Size(cam.tileW * 0.72f, h * 0.44f),
                style = Stroke(1.4f * quality.strokeScale)
            )
        }
        // Timber rowboat hull & bench plank
        drawRotatedPrism(
            scope, path, cam, cx, cy + bob, fracU = 0.78f, fracV = 0.38f, heightPx = h * 0.22f,
            topColor = Color(0xFF6E4726), faceXColor = Color(0xFF472B14), faceYColor = Color(0xFF5C3A1D),
            quality = quality, isOverland = true
        )
        drawRotatedQuad(scope, path, cam, cx, cy + bob, fracU = 0.18f, fracV = 0.34f, zPx = h * 0.23f, fillColor = Color(0xFFA87B4C))
    }

    private fun drawTreeProp(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        cx: Float,
        cy: Float,
        tx: Int,
        ty: Int,
        nowMs: Long,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT
    ) {
        val h = cam.tileH
        val w = cam.tileW
        val sway = if (quality.treeDetailLayers >= 3) {
            sin(nowMs * 0.002 + tx * 0.7 + ty * 0.5).toFloat() * 1.8f
        } else {
            0f
        }

        // Angled directional twilight cast shadow (High/HD) or simple oval contact shadow (Medium)
        if (quality.drawCastShadows) {
            IsometricLightingEngine.drawAngledCastShadow(
                scope = scope,
                cam = cam,
                cx = cx,
                cy = cy + h * 0.04f,
                radiusScale = 1.15f,
                heightTiles = 1.35f,
                quality = quality
            )
        } else if (quality.drawContactShadows) {
            scope.drawOval(Color(0x45000000), Offset(cx - w * 0.30f, cy - h * 0.14f), Size(w * 0.60f, h * 0.36f))
        }

        // Bark trunk with yaw-aware directional lighting
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.20f, fracV = 0.20f, heightPx = h * 0.65f,
            topColor = Color(0xFF5C3A20), faceXColor = Color(0xFF3E2614), faceYColor = Color(0xFF4F321B),
            quality = quality, isOverland = true
        )

        // Tier-scaled lush foliage canopy with directional twilight highlight offset
        val darkLeaf = if (tx >= 272) Color(0xFF16361D) else Color(0xFF1F4F24)
        val midLeaf = if (tx >= 272) Color(0xFF214D29) else Color(0xFF2E6E32)
        val hiLeaf = if (tx >= 272) Color(0xFF346E3C) else Color(0xFF4B9144)

        val shadowShift = if (quality.useDirectionalLighting) {
            IsometricLightingEngine.castShadowScreenOffset(cam, 0.24f)
        } else {
            Offset.Zero
        }

        scope.drawOval(darkLeaf, Offset(cx - w * 0.38f + sway * 0.5f + shadowShift.x * 0.35f, cy - h * 1.48f), Size(w * 0.76f, h * 0.94f))
        if (quality.treeDetailLayers >= 2) {
            scope.drawOval(midLeaf, Offset(cx - w * 0.32f + sway * 0.8f, cy - h * 1.78f), Size(w * 0.64f, h * 0.82f))
        }
        if (quality.treeDetailLayers >= 3) {
            scope.drawOval(hiLeaf, Offset(cx - w * 0.20f + sway - shadowShift.x * 0.45f, cy - h * 2.02f), Size(w * 0.42f, h * 0.52f))
        }
        if (quality.drawHdStonePolish) {
            // Twilight moonlit HD canopy crown highlight
            scope.drawOval(
                Color(0x3CD2F5B8),
                Offset(cx - w * 0.11f + sway - shadowShift.x * 0.55f, cy - h * 1.98f),
                Size(w * 0.22f, h * 0.22f)
            )
        }
    }

    private fun drawWarmFlickerGlow(
        scope: DrawScope,
        x: Float,
        y: Float,
        radius: Float,
        nowMs: Long,
        tx: Int,
        ty: Int,
        quality: GraphicsQuality = GraphicsQuality.DEFAULT
    ) {
        when (quality.lightingMode) {
            LightingDetailMode.OFF -> {
                scope.drawCircle(Color(0xFFFFE594), 2.5f, Offset(x, y))
            }
            LightingDetailMode.STATIC_HALO -> {
                scope.drawCircle(TorchGold.copy(alpha = 0.22f), radius * 0.72f, Offset(x, y))
                scope.drawCircle(Color(0xFFFFF2B2), 2.8f, Offset(x, y))
            }
            LightingDetailMode.DYNAMIC_FLICKER -> {
                val flicker = 0.86f + 0.14f * sin(nowMs * 0.011 + tx * 1.7 + ty * 2.3).toFloat()
                val center = Offset(x, y)
                scope.drawCircle(TorchAmber.copy(alpha = 0.22f * flicker), radius * 0.92f * flicker, center)
                scope.drawCircle(TorchGold.copy(alpha = 0.48f * flicker), radius * 0.48f * flicker, center)
                scope.drawCircle(Color(0xFFFFF2B2), 2.8f, center)
            }
            LightingDetailMode.HD_MULTI_LAYER -> {
                // Zero-allocation crisp flame core & tight rim; broad atmospheric light pool is composited
                // in a single pass by HalfResLightLayerRenderer using pre-baked glow sprites.
                val flicker = 0.88f + 0.12f * sin(nowMs * 0.011 + tx * 1.7 + ty * 2.3).toFloat()
                val center = Offset(x, y)
                scope.drawCircle(TorchAmber.copy(alpha = 0.24f * flicker), radius * 1.05f * flicker, center)
                scope.drawCircle(TorchGold.copy(alpha = 0.50f * flicker), radius * 0.56f * flicker, center)
                scope.drawCircle(Color(0xFFFFF4C2).copy(alpha = 0.82f * flicker), radius * 0.26f * flicker, center)
                scope.drawCircle(Color(0xFFFFFAE0), 3.1f, center)
            }
        }
    }

    /**
     * Connected open-top cutaway biome-ideology walls:
     * - [TileType.SANDSTONE_WALL]: Warm golden-ochre sandstone ashlar courses & sunlit desert parapet
     * - [TileType.GRANITE_WALL]: Heavy mortared highland blue-grey granite citadel masonry
     * - [TileType.OBSIDIAN_WALL]: Volcanic iron-reinforced obsidian fortress blocks with ember trim
     */
    private fun drawBiomeIdeologyWall(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        floorMap: WorldFloor,
        tx: Int,
        ty: Int,
        cx: Float,
        cy: Float,
        tile: TileType,
        quality: GraphicsQuality,
        cutaway: WallCutaway
    ) {
        val h = cam.tileH
        val cScale = cutaway.heightScale
        val cAlpha = cutaway.alpha
        val isOverland = floorMap.floorId == 0
        val hasN = isBuildingWall(floorMap.tileAt(tx, ty - 1))
        val hasS = isBuildingWall(floorMap.tileAt(tx, ty + 1))
        val hasW = isBuildingWall(floorMap.tileAt(tx - 1, ty))
        val hasE = isBuildingWall(floorMap.tileAt(tx + 1, ty))
        val beamThickness = when (tile) {
            TileType.GRANITE_WALL -> 0.44f
            TileType.OBSIDIAN_WALL -> 0.42f
            else -> 0.38f
        }
        val totalH = h * (if (tile == TileType.GRANITE_WALL) 2.50f else TALL_WALL_HEIGHT_MULT) * cScale
        val plinthH = totalH * 0.16f
        val wallH = totalH * 0.84f

        val (plinthTop, plinthX, plinthY) = when (tile) {
            TileType.SANDSTONE_WALL -> Triple(Color(0xFFC4A06A), Color(0xFF947446), Color(0xFFAD8A56))
            TileType.GRANITE_WALL -> Triple(Color(0xFF6B737D), Color(0xFF454C54), Color(0xFF565E68))
            else -> Triple(Color(0xFF2E2636), Color(0xFF1A1520), Color(0xFF231C2B))
        }
        val (wallTop, wallX, wallY) = when (tile) {
            TileType.SANDSTONE_WALL -> Triple(Color(0xFFE5C486), Color(0xFFB89258), Color(0xFFD4AF72))
            TileType.GRANITE_WALL -> Triple(Color(0xFF9AA3AD), Color(0xFF626A73), Color(0xFF7B848E))
            else -> Triple(Color(0xFF463B54), Color(0xFF241D2E), Color(0xFF332940))
        }

        fun drawSeg(fracU: Float, fracV: Float, offU: Float, offV: Float) {
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = fracU + 0.05f, fracV = fracV + 0.05f,
                heightPx = plinthH, offsetU = offU, offsetV = offV,
                topColor = plinthTop, faceXColor = plinthX, faceYColor = plinthY,
                quality = quality, isOverland = isOverland, alpha = cAlpha
            )
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = fracU, fracV = fracV,
                heightPx = wallH, baseZ = plinthH, offsetU = offU, offsetV = offV,
                topColor = wallTop, faceXColor = wallX, faceYColor = wallY,
                quality = quality, isOverland = isOverland, alpha = cAlpha, drawMortarCourses = true
            )
        }

        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = beamThickness + 0.06f, fracV = beamThickness + 0.06f,
            heightPx = totalH + h * 0.06f * cScale,
            topColor = wallTop, faceXColor = wallX, faceYColor = wallY,
            quality = quality, isOverland = isOverland, alpha = cAlpha, drawMortarCourses = true
        )
        if (hasE) drawSeg(0.50f, beamThickness, 0.25f, 0f)
        if (hasW) drawSeg(0.50f, beamThickness, -0.25f, 0f)
        if (hasS) drawSeg(beamThickness, 0.50f, 0f, 0.25f)
        if (hasN) drawSeg(beamThickness, 0.50f, 0f, -0.25f)

        if (tile == TileType.OBSIDIAN_WALL && cScale > 0.6f && quality != GraphicsQuality.LOW) {
            drawRotatedQuad(
                scope, path, cam, cx, cy, fracU = beamThickness + 0.04f, fracV = beamThickness + 0.04f,
                zPx = totalH + h * 0.07f * cScale,
                fillColor = Color(0xFFFF6B2B).copy(alpha = 0.42f * cAlpha)
            )
        }
    }

    /**
     * Fortified iron-shod standard pole with an animated crimson & gold imperial war banner.
     */
    private fun drawWarBannerProp(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        cx: Float,
        cy: Float,
        tx: Int,
        ty: Int,
        nowMs: Long,
        quality: GraphicsQuality,
        isOverland: Boolean
    ) {
        val h = cam.tileH
        val w = cam.tileW
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.42f, fracV = 0.42f, heightPx = h * 0.24f,
            topColor = Color(0xFF6B6560), faceXColor = Color(0xFF3E3A37), faceYColor = Color(0xFF524D49),
            quality = quality, isOverland = isOverland
        )
        val poleTopY = cy - h * 1.95f
        val poleStroke = (w * 0.045f).coerceIn(2.0f, 4.2f)
        scope.drawLine(Color(0xFF2A262B), Offset(cx, cy - h * 0.22f), Offset(cx, poleTopY), poleStroke + 1.2f)
        scope.drawLine(Color(0xFF9EA5B0), Offset(cx, cy - h * 0.22f), Offset(cx, poleTopY), poleStroke * 0.65f)
        scope.drawCircle(TorchGold, (w * 0.055f).coerceIn(2.6f, 5.2f), Offset(cx, poleTopY - 2f))

        val flutter = sin((nowMs / 240.0) + (tx * 0.9 + ty * 1.3)).toFloat() * (w * 0.06f)
        val flagW = w * 0.44f
        val flagH = h * 0.88f
        path.rewind()
        path.moveTo(cx + 1.5f, poleTopY + h * 0.08f)
        path.lineTo(cx + flagW, poleTopY + h * 0.16f + flutter)
        path.lineTo(cx + flagW * 0.82f, poleTopY + flagH * 0.56f + flutter * 1.2f)
        path.lineTo(cx + flagW, poleTopY + flagH + flutter * 0.85f)
        path.lineTo(cx + 1.5f, poleTopY + flagH * 0.92f)
        path.close()
        scope.drawPath(path, Color(0xFFA81D2B))
        scope.drawPath(path, TorchGold, style = Stroke(width = 1.6f))
        scope.drawCircle(TorchGold, (w * 0.048f).coerceIn(2.0f, 4.0f), Offset(cx + flagW * 0.42f, poleTopY + flagH * 0.48f + flutter * 0.5f))
    }

    /**
     * Forged iron campfire brazier with glowing embers and flickering flames for camps & strongholds.
     */
    private fun drawCampfireBrazierProp(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        cx: Float,
        cy: Float,
        tx: Int,
        ty: Int,
        nowMs: Long,
        quality: GraphicsQuality,
        isOverland: Boolean
    ) {
        val h = cam.tileH
        val w = cam.tileW
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.54f, fracV = 0.54f, heightPx = h * 0.36f,
            topColor = Color(0xFF474348), faceXColor = Color(0xFF262328), faceYColor = Color(0xFF343036),
            quality = quality, isOverland = isOverland
        )
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.64f, fracV = 0.64f, heightPx = h * 0.24f, baseZ = h * 0.34f,
            topColor = Color(0xFF3A241A), faceXColor = Color(0xFF2B282E), faceYColor = Color(0xFF3E3A42),
            quality = quality, isOverland = isOverland
        )
        val flameBaseY = cy - h * 0.60f
        scope.drawCircle(Color(0xFFD93814), (w * 0.16f).coerceIn(4.5f, 12f), Offset(cx, flameBaseY))

        val flicker = sin((nowMs / 135.0) + (tx * 1.7 + ty * 2.1)).toFloat()
        val flameH = h * (0.68f + flicker * 0.14f)
        val sway = flicker * w * 0.045f
        path.rewind()
        path.moveTo(cx - w * 0.14f, flameBaseY)
        path.lineTo(cx + sway, flameBaseY - flameH)
        path.lineTo(cx + w * 0.14f, flameBaseY)
        path.close()
        scope.drawPath(path, Color(0xFFFF7A1A))

        path.rewind()
        path.moveTo(cx - w * 0.08f, flameBaseY)
        path.lineTo(cx - sway * 0.6f, flameBaseY - flameH * 0.68f)
        path.lineTo(cx + w * 0.08f, flameBaseY)
        path.close()
        scope.drawPath(path, Color(0xFFFFE066))

        drawWarmFlickerGlow(scope, cx, flameBaseY - flameH * 0.35f, h * 1.15f, nowMs, tx, ty, quality)
    }
}
