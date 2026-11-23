package com.example.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

        val baseTileW = (min(viewportW, viewportH) * 0.21f).coerceIn(68f, 124f)
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

    fun visibleTileBounds(
        viewportW: Float,
        viewportH: Float,
        floorWidth: Int,
        floorHeight: Int,
        margin: Int = 4
    ): IsoBounds {
        // Expand vertical sampling upward/downward to include tall trees & roofs overhanging viewport edges
        val padTop = tileH * 2.5f
        val padBottom = tileH * 2.0f
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
        const val MIN_ZOOM = 0.60f
        const val MAX_ZOOM = 1.80f

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
        TileType.ORE_OCHRE,
        TileType.ORE_BOGSTONE,
        TileType.ORE_BRIMSTONE,
        TileType.ORE_AETHERIUM,
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
        TileType.MOORED_BOAT -> true
        else -> false
    }

    private fun isBuildingWall(tile: TileType): Boolean = when (tile) {
        TileType.TIMBER_WALL,
        TileType.TIMBER_WINDOW_WALL,
        TileType.TEMPLE_WALL,
        TileType.GATE_PILLAR,
        TileType.STONE_WALL,
        TileType.CITY_WALL,
        TileType.TORCH_WALL -> true
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
        scale: Float = 1f
    ) {
        val w = hw * 0.88f * scale
        val h = hh * 0.62f * scale
        scope.drawOval(
            color = Color(0x52000000),
            topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f),
            size = Size(w, h)
        )
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

        path.reset()
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
     * front-facing vertical walls based on screen-space depth so 360-degree spinning has zero clipping!
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
        outlineColor: Color = Color(0x44000000)
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

        // Helper to draw a vertical face if its midpoint is closer to viewer (y > center oy)
        fun drawSideIfVisible(ax: Float, ay: Float, bx: Float, by: Float, color: Color) {
            val midY = (ay + by) * 0.5f
            if (midY >= oy - 0.1f) {
                path.reset()
                path.moveTo(ax, ay)
                path.lineTo(bx, by)
                path.lineTo(bx, by - heightPx)
                path.lineTo(ax, ay - heightPx)
                path.close()
                scope.drawPath(path, color = color)
                scope.drawPath(path, color = outlineColor, style = Stroke(width = 0.9f))
            }
        }

        // Edge 0-1 (North, -V), Edge 1-2 (East, +U), Edge 2-3 (South, +V), Edge 3-0 (West, -U)
        drawSideIfVisible(b0x, b0y, b1x, b1y, faceYColor)
        drawSideIfVisible(b1x, b1y, b2x, b2y, faceXColor)
        drawSideIfVisible(b2x, b2y, b3x, b3y, faceYColor)
        drawSideIfVisible(b3x, b3y, b0x, b0y, faceXColor)

        // Top cap
        path.reset()
        path.moveTo(b0x, b0y - heightPx)
        path.lineTo(b1x, b1y - heightPx)
        path.lineTo(b2x, b2y - heightPx)
        path.lineTo(b3x, b3y - heightPx)
        path.close()
        scope.drawPath(path, color = topColor)
        scope.drawPath(path, color = outlineColor, style = Stroke(width = 0.9f))
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
        nowMs: Long
    ) {
        val h = tileHash(tx, ty, 3) and 15
        val shimmer = sin(nowMs * 0.0038 + tx * 0.9 + ty * 0.7).toFloat()

        // Ground base underneath props/walls matches local setting cleanly
        val baseTile = when (tile) {
            TileType.TREE, TileType.TREE_STUMP, TileType.HEDGE, TileType.WOOD_FENCE ->
                if (tx >= 272 && floorMap.floorId == 0) TileType.DARK_GRASS else TileType.GRASS
            TileType.MOORED_BOAT -> TileType.WATER
            TileType.DEPOT_CHEST, TileType.SHOP_COUNTER, TileType.CRATE_BARREL, TileType.WEAPON_RACK ->
                if (floorMap.floorId == 0) TileType.WOOD_PLANK_FLOOR else TileType.COBBLESTONE
            TileType.TEMPLE_ALTAR, TileType.TEMPLE_PEW, TileType.TEMPLE_WALL -> TileType.TEMPLE_FLOOR
            TileType.RUNECRAFTING_TABLE, TileType.BOOKSHELF, TileType.CRYSTAL_PILLAR -> TileType.ARCANE_FLOOR
            TileType.FOUNTAIN, TileType.MARKET_AWNING, TileType.SMELTING_FURNACE, TileType.BLACKSMITH_ANVIL,
            TileType.FLETCHING_BENCH, TileType.LANTERN_POST, TileType.GATE_PILLAR -> TileType.COBBLESTONE
            TileType.TIMBER_WALL, TileType.TIMBER_WINDOW_WALL -> TileType.COBBLESTONE
            else -> tile
        }

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
            TileType.DIRT_FLOOR -> if (h < 6) Color(0xFF785C42) else Color(0xFF6B5038)
            TileType.CRYPT_FLOOR -> if ((tx + ty) and 1 == 0) Color(0xFF505561) else Color(0xFF444954)
            TileType.OBSIDIAN_FLOOR -> if ((tx + ty) and 1 == 0) Color(0xFF362F40) else Color(0xFF2C2636)
            TileType.WATER -> Color(
                red = (0.11f + shimmer * 0.025f).coerceIn(0f, 1f),
                green = (0.41f + shimmer * 0.045f).coerceIn(0f, 1f),
                blue = (0.63f + shimmer * 0.04f).coerceIn(0f, 1f)
            )
            TileType.LAVA -> Color(
                red = (0.88f + shimmer * 0.09f).coerceIn(0f, 1f),
                green = (0.31f + shimmer * 0.08f).coerceIn(0f, 1f),
                blue = 0.07f
            )
            TileType.STAIRS_DOWN, TileType.STAIRS_UP -> Color(0xFF6A6258)
            TileType.ROPE_HOLE_DOWN, TileType.ROPE_SPOT_UP -> Color(0xFF6A543E)
            else -> Color(0xFF544E46)
        }

        val strokeCol = when (baseTile) {
            TileType.COBBLESTONE -> Color(0x362C2722)
            TileType.TEMPLE_FLOOR -> Color(0x45B09658)
            TileType.WOOD_PLANK_FLOOR, TileType.WOOD_PIER, TileType.WOOD_BRIDGE -> Color(0x422A1B0E)
            TileType.ARCANE_FLOOR -> Color(0x3C8A6BFF)
            TileType.WATER, TileType.LAVA -> null
            else -> Color(0x18000000)
        }

        // Slightly overdraw ground diamonds (1.03f) to eliminate any hairline subpixel seams at arbitrary yaw angles
        drawRotatedQuad(scope, path, cam, cx, cy, fracU = 1.03f, fracV = 1.03f, fillColor = groundColor, strokeColor = strokeCol, strokeWidth = 0.85f)

        // Detailed ground surface overlays
        when (baseTile) {
            TileType.GRASS -> {
                if (h == 1 || h == 9) {
                    val blade = Color(0xFF68A34D)
                    scope.drawLine(blade, Offset(cx - 2f, cy + 1f), Offset(cx - 4f, cy - 4f), 1.3f)
                    scope.drawLine(blade, Offset(cx + 1f, cy + 2f), Offset(cx + 3f, cy - 3f), 1.2f)
                } else if (h == 14) {
                    // Subtle pebble
                    scope.drawOval(Color(0x668C857A), Offset(cx - 3f, cy - 1f), Size(5f, 3f))
                }
            }
            TileType.FLOWER_GRASS -> {
                val blossomColors = arrayOf(Color(0xFFF6D660), Color(0xFFE87EA1), Color(0xFFB39DFF), Color(0xFFF7F4E8))
                val c1 = blossomColors[h and 3]
                val c2 = blossomColors[(h + 2) and 3]
                val r = (cam.tileW * 0.035f).coerceIn(2f, 4f)
                scope.drawCircle(Color(0xFF6BA64E), r * 1.1f, Offset(cx - cam.tileW * 0.12f, cy + cam.tileH * 0.04f))
                scope.drawCircle(c1, r, Offset(cx - cam.tileW * 0.12f, cy))
                scope.drawCircle(c2, r * 0.9f, Offset(cx + cam.tileW * 0.11f, cy - cam.tileH * 0.06f))
                scope.drawCircle(Color(0xFFF7E58B), r * 0.75f, Offset(cx + cam.tileW * 0.02f, cy + cam.tileH * 0.12f))
            }
            TileType.COBBLESTONE -> {
                // Subtle stone paving divider + soft curb edge where cobble meets grass
                if (h < 10) {
                    scope.drawLine(
                        Color(0x28302A24),
                        Offset(cx - cam.ux * 0.25f, cy - cam.uy * 0.25f),
                        Offset(cx + cam.ux * 0.25f, cy + cam.uy * 0.25f),
                        0.9f
                    )
                }
                val edgeColor = Color(0x55B8AE9E)
                val nGrass = floorMap.tileAt(tx, ty - 1).let { it == TileType.GRASS || it == TileType.FLOWER_GRASS }
                val sGrass = floorMap.tileAt(tx, ty + 1).let { it == TileType.GRASS || it == TileType.FLOWER_GRASS }
                if (nGrass) {
                    drawRotatedQuad(scope, path, cam, cx, cy, fracU = 1.0f, fracV = 0.12f, offsetV = -0.44f, fillColor = edgeColor)
                }
                if (sGrass) {
                    drawRotatedQuad(scope, path, cam, cx, cy, fracU = 1.0f, fracV = 0.12f, offsetV = 0.44f, fillColor = edgeColor)
                }
            }
            TileType.TEMPLE_FLOOR -> {
                // Gold-bordered marble diamond mosaic inlay
                if ((tx + ty) % 2 == 0) {
                    drawRotatedQuad(
                        scope, path, cam, cx, cy, fracU = 0.56f, fracV = 0.56f,
                        fillColor = Color(0x2AE6C775), strokeColor = Color(0x66C7A34E), strokeWidth = 1f
                    )
                }
            }
            TileType.ARCANE_FLOOR -> {
                // Glowing violet arcane rune circle inlaid in Runecrafting Academy floor
                if (tx in 221..229 && ty in 177..183) {
                    val pulse = 0.35f + 0.18f * sin(nowMs * 0.0045 + tx + ty).toFloat()
                    scope.drawOval(
                        color = Color(0xFFB388FF).copy(alpha = pulse),
                        topLeft = Offset(cx - cam.tileW * 0.26f, cy - cam.tileH * 0.26f),
                        size = Size(cam.tileW * 0.52f, cam.tileH * 0.52f),
                        style = Stroke(width = 1.4f)
                    )
                }
            }
            TileType.WOOD_PLANK_FLOOR -> {
                // Warm timber plank grain lines
                val grain = Color(0x383A2210)
                scope.drawLine(grain, Offset(cx - cam.ux * 0.42f, cy - cam.uy * 0.42f), Offset(cx + cam.ux * 0.42f, cy + cam.uy * 0.42f), 1f)
            }
            TileType.WOOD_PIER, TileType.WOOD_BRIDGE -> {
                // Plank lines + bridge/pier railings along water edges
                val railColor = Color(0xFF5C3E24)
                val postColor = Color(0xFF422A16)
                scope.drawLine(Color(0x443B2310), Offset(cx - cam.ux * 0.44f, cy - cam.uy * 0.44f), Offset(cx + cam.ux * 0.44f, cy + cam.uy * 0.44f), 1.1f)
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
            TileType.WATER -> {
                // Animated water ripples
                val rippleOffset = shimmer * cam.tileW * 0.08f
                scope.drawLine(
                    color = Color(0x6CE0F7FF),
                    start = Offset(cx - cam.tileW * 0.16f + rippleOffset, cy - cam.tileH * 0.08f),
                    end = Offset(cx + cam.tileW * 0.12f + rippleOffset, cy - cam.tileH * 0.08f),
                    strokeWidth = 1.6f
                )
                scope.drawLine(
                    color = Color(0x45B0E4FF),
                    start = Offset(cx - cam.tileW * 0.06f - rippleOffset, cy + cam.tileH * 0.14f),
                    end = Offset(cx + cam.tileW * 0.18f - rippleOffset, cy + cam.tileH * 0.14f),
                    strokeWidth = 1.2f
                )
                // Softened natural shoreline: curved rocks, swaying reeds & floating lily pads near land!
                val nearLand = listOf(tx - 1 to ty, tx + 1 to ty, tx to ty - 1, tx to ty + 1).any { (nx, ny) ->
                    val nt = floorMap.tileAt(nx, ny)
                    nt != TileType.WATER && nt != TileType.MOORED_BOAT && nt != TileType.WOOD_PIER && nt != TileType.WOOD_BRIDGE
                }
                if (nearLand) {
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
        nowMs: Long
    ) {
        val unitH = cam.tileH
        when (tile) {
            TileType.TIMBER_WALL, TileType.TIMBER_WINDOW_WALL -> {
                drawDetailedTimberWall(scope, path, cam, floorMap, tx, ty, cx, cy, hasWindow = tile == TileType.TIMBER_WINDOW_WALL, nowMs = nowMs)
            }
            TileType.TEMPLE_WALL -> {
                drawDetailedTempleWall(scope, path, cam, floorMap, tx, ty, cx, cy, nowMs)
            }
            TileType.GATE_PILLAR -> {
                // Grand dressed stone gatehouse pillar + golden brazier lantern on top
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.64f, fracV = 0.64f, heightPx = unitH * 0.25f,
                    topColor = Color(0xFF968E82), faceXColor = Color(0xFF6B645A), faceYColor = Color(0xFF7D756A)
                )
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.46f, fracV = 0.46f, heightPx = unitH * 1.15f, baseZ = unitH * 0.25f,
                    topColor = Color(0xFFB3AA9C), faceXColor = Color(0xFF7A7267), faceYColor = Color(0xFF91887B)
                )
                drawWarmFlickerGlow(scope, cx, cy - unitH * 1.52f, unitH * 0.85f, nowMs, tx, ty)
            }
            TileType.STONE_WALL, TileType.CITY_WALL, TileType.TORCH_WALL -> {
                // Slimmed dressed dungeon/stone wall (not a bulky slab!)
                val hPx = unitH * 1.12f
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.82f, fracV = 0.82f, heightPx = unitH * 0.22f,
                    topColor = Color(0xFF6A645C), faceXColor = Color(0xFF47423C), faceYColor = Color(0xFF57514A)
                )
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.72f, fracV = 0.72f, heightPx = hPx, baseZ = unitH * 0.22f,
                    topColor = Color(0xFF8C8478), faceXColor = Color(0xFF5D564D), faceYColor = Color(0xFF70685E)
                )
                if (tile == TileType.TORCH_WALL) {
                    drawWarmFlickerGlow(scope, cx, cy - hPx * 0.92f, unitH * 1.15f, nowMs, tx, ty)
                }
            }
            TileType.CAVE_WALL, TileType.MOUNTAIN_ROCK -> {
                val isCave = tile == TileType.CAVE_WALL
                val hPx = unitH * (if (isCave) 1.20f else 1.05f)
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.88f, fracV = 0.88f, heightPx = hPx * 0.62f,
                    topColor = if (isCave) Color(0xFF47444A) else Color(0xFF7B736A),
                    faceXColor = if (isCave) Color(0xFF2B282E) else Color(0xFF4F4942),
                    faceYColor = if (isCave) Color(0xFF39353D) else Color(0xFF615A52)
                )
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.54f, fracV = 0.54f, heightPx = hPx * 0.52f, baseZ = hPx * 0.58f,
                    topColor = if (isCave) Color(0xFF59545C) else Color(0xFF948B80),
                    faceXColor = if (isCave) Color(0xFF38343B) else Color(0xFF635C54),
                    faceYColor = if (isCave) Color(0xFF46424A) else Color(0xFF787066)
                )
            }
            TileType.TREE -> drawTreeProp(scope, path, cam, cx, cy, tx, ty, nowMs)
            TileType.TREE_STUMP -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.36f, fracV = 0.36f, heightPx = unitH * 0.32f,
                    topColor = Color(0xFFB58958), faceXColor = Color(0xFF5C3D22), faceYColor = Color(0xFF734E2C)
                )
            }
            TileType.FOUNTAIN -> drawStoneFountain(scope, path, cam, cx, cy, nowMs)
            TileType.HEDGE -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.72f, fracV = 0.72f, heightPx = unitH * 0.46f,
                    topColor = Color(0xFF3E7A38), faceXColor = Color(0xFF214A1E), faceYColor = Color(0xFF2D5F28)
                )
                scope.drawCircle(Color(0xFF63A854), cam.tileW * 0.08f, Offset(cx - cam.tileW * 0.1f, cy - unitH * 0.42f))
                scope.drawCircle(Color(0xFFF5D36C), 2.2f, Offset(cx + cam.tileW * 0.08f, cy - unitH * 0.46f))
            }
            TileType.WOOD_FENCE -> drawWoodenFence(scope, path, cam, floorMap, tx, ty, cx, cy)
            TileType.LANTERN_POST -> drawLanternPost(scope, path, cam, cx, cy, nowMs, tx, ty)
            TileType.DEPOT_CHEST, TileType.MINI_DEPOT -> {
                val isMini = tile == TileType.MINI_DEPOT
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.68f, fracV = 0.56f, heightPx = unitH * 0.48f,
                    topColor = if (isMini) Color(0xFF56738A) else Color(0xFF9C693B),
                    faceXColor = if (isMini) Color(0xFF2F4557) else Color(0xFF5C3A1D),
                    faceYColor = if (isMini) Color(0xFF405A70) else Color(0xFF784E28)
                )
                // Iron & gold chest bands
                scope.drawCircle(TorchGold, (cam.tileW * 0.045f).coerceAtLeast(2.8f), Offset(cx, cy - unitH * 0.24f))
            }
            TileType.SHOP_COUNTER -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.84f, fracV = 0.58f, heightPx = unitH * 0.52f,
                    topColor = Color(0xFFB38352), faceXColor = Color(0xFF634323), faceYColor = Color(0xFF825930)
                )
                // Ruby & sapphire potion bottles on Alaric's counter
                scope.drawCircle(Color(0xFFE54848), 3f, Offset(cx - 5f, cy - unitH * 0.56f))
                scope.drawCircle(Color(0xFF42A5F5), 3f, Offset(cx + 5f, cy - unitH * 0.56f))
            }
            TileType.MARKET_AWNING -> drawMarketAwningStall(scope, path, cam, cx, cy)
            TileType.CRATE_BARREL -> drawCrateAndBarrel(scope, path, cam, cx, cy)
            TileType.TEMPLE_ALTAR -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.80f, fracV = 0.60f, heightPx = unitH * 0.58f,
                    topColor = Color(0xFFF2ECE0), faceXColor = Color(0xFFB8AE9C), faceYColor = Color(0xFFD1C8B8)
                )
                // Crimson-and-gold altar runner + twin flickering altar candles
                drawRotatedQuad(scope, path, cam, cx, cy, fracU = 0.36f, fracV = 0.62f, zPx = unitH * 0.59f, fillColor = Color(0xFFA82C2C), strokeColor = TorchGold, strokeWidth = 1.2f)
                drawWarmFlickerGlow(scope, cx - cam.tileW * 0.14f, cy - unitH * 0.72f, unitH * 0.45f, nowMs, tx, ty)
                drawWarmFlickerGlow(scope, cx + cam.tileW * 0.14f, cy - unitH * 0.72f, unitH * 0.45f, nowMs, tx + 3, ty)
            }
            TileType.TEMPLE_PEW -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.76f, fracV = 0.34f, heightPx = unitH * 0.28f,
                    topColor = Color(0xFF8C5D35), faceXColor = Color(0xFF523319), faceYColor = Color(0xFF6E4625)
                )
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.76f, fracV = 0.12f, heightPx = unitH * 0.30f, baseZ = unitH * 0.26f, offsetV = -0.11f,
                    topColor = Color(0xFF9C6B3F), faceXColor = Color(0xFF5A391D), faceYColor = Color(0xFF784E2A)
                )
            }
            TileType.SMELTING_FURNACE -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.74f, fracV = 0.70f, heightPx = unitH * 0.88f,
                    topColor = Color(0xFF6B5951), faceXColor = Color(0xFF42342F), faceYColor = Color(0xFF57443E)
                )
                drawWarmFlickerGlow(scope, cx, cy - unitH * 0.36f, unitH * 0.72f, nowMs, tx, ty)
            }
            TileType.BLACKSMITH_ANVIL -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.48f, fracV = 0.48f, heightPx = unitH * 0.30f,
                    topColor = Color(0xFF87613B), faceXColor = Color(0xFF523920), faceYColor = Color(0xFF694A2A)
                )
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.60f, fracV = 0.34f, heightPx = unitH * 0.24f, baseZ = unitH * 0.30f,
                    topColor = Color(0xFF7D8591), faceXColor = Color(0xFF3E444D), faceYColor = Color(0xFF575F6B)
                )
            }
            TileType.FLETCHING_BENCH -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.78f, fracV = 0.48f, heightPx = unitH * 0.46f,
                    topColor = Color(0xFFC4945A), faceXColor = Color(0xFF73502B), faceYColor = Color(0xFF916739)
                )
                // Stacked timber logs beside bench
                scope.drawLine(Color(0xFF5C3A1E), Offset(cx - cam.tileW * 0.16f, cy - unitH * 0.48f), Offset(cx + cam.tileW * 0.16f, cy - unitH * 0.54f), 3f)
            }
            TileType.WEAPON_RACK -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.68f, fracV = 0.26f, heightPx = unitH * 0.74f,
                    topColor = Color(0xFF785230), faceXColor = Color(0xFF472F19), faceYColor = Color(0xFF5E3F22)
                )
                // Glinting blades & round shield
                scope.drawLine(Color(0xFFDCE4ED), Offset(cx - 5f, cy - unitH * 0.18f), Offset(cx - 5f, cy - unitH * 0.72f), 2.2f)
                scope.drawLine(Color(0xFFDCE4ED), Offset(cx + 4f, cy - unitH * 0.18f), Offset(cx + 4f, cy - unitH * 0.72f), 2.2f)
                scope.drawCircle(Color(0xFFC9A04A), 4f, Offset(cx, cy - unitH * 0.44f))
            }
            TileType.RUNECRAFTING_TABLE -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.76f, fracV = 0.64f, heightPx = unitH * 0.56f,
                    topColor = Color(0xFF564B78), faceXColor = Color(0xFF332C4A), faceYColor = Color(0xFF443B61)
                )
                val pulse = 0.7f + 0.3f * sin(nowMs * 0.006).toFloat()
                val bob = sin(nowMs * 0.005).toFloat() * 3f
                scope.drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFD7B8FF).copy(alpha = 0.85f * pulse), Color(0xFF7C4DFF).copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(cx, cy - unitH * 0.84f + bob),
                        radius = unitH * 0.62f
                    ),
                    radius = unitH * 0.62f,
                    center = Offset(cx, cy - unitH * 0.84f + bob)
                )
                scope.drawCircle(Color(0xFFEAD9FF), 4.5f, Offset(cx, cy - unitH * 0.84f + bob))
            }
            TileType.BOOKSHELF -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.76f, fracV = 0.36f, heightPx = unitH * 0.96f,
                    topColor = Color(0xFF6E4726), faceXColor = Color(0xFF422914), faceYColor = Color(0xFF57361C)
                )
                // Colorful tome bands across shelves
                val shelfColors = arrayOf(Color(0xFFC94444), Color(0xFF4788C7), Color(0xFF4DA663), Color(0xFFE0B24C))
                for (i in 0..3) {
                    scope.drawCircle(shelfColors[i], 2.2f, Offset(cx + (i - 1.5f) * 4.5f, cy - unitH * 0.52f))
                    scope.drawCircle(shelfColors[(i + 2) and 3], 2.2f, Offset(cx + (i - 1.5f) * 4.5f, cy - unitH * 0.76f))
                }
            }
            TileType.CRYSTAL_PILLAR -> {
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.44f, fracV = 0.44f, heightPx = unitH * 0.45f,
                    topColor = Color(0xFF6B677A), faceXColor = Color(0xFF413E4D), faceYColor = Color(0xFF524E61)
                )
                val pulse = 0.72f + 0.28f * sin(nowMs * 0.005 + tx).toFloat()
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.24f, fracV = 0.24f, heightPx = unitH * 0.76f, baseZ = unitH * 0.45f,
                    topColor = Color(0xFFE8D5FF), faceXColor = Color(0xFF7C4DFF), faceYColor = Color(0xFFB388FF)
                )
                scope.drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFC79BFF).copy(alpha = 0.65f * pulse), Color.Transparent),
                        center = Offset(cx, cy - unitH * 0.95f),
                        radius = unitH * 0.72f
                    ),
                    radius = unitH * 0.72f,
                    center = Offset(cx, cy - unitH * 0.95f)
                )
            }
            TileType.MOORED_BOAT -> drawMooredRowboat(scope, path, cam, cx, cy, nowMs, tx, ty)
            TileType.ORE_OCHRE, TileType.ORE_BOGSTONE, TileType.ORE_BRIMSTONE, TileType.ORE_AETHERIUM, TileType.DEPLETED_VEIN -> {
                val veinColor = when (tile) {
                    TileType.ORE_OCHRE -> Color(0xFFE38C3B)
                    TileType.ORE_BOGSTONE -> Color(0xFFA6B9CC)
                    TileType.ORE_BRIMSTONE -> FireOrange
                    TileType.ORE_AETHERIUM -> Color(0xFFA98AFF)
                    else -> Color(0xFF5C5854)
                }
                drawRotatedPrism(
                    scope, path, cam, cx, cy, fracU = 0.72f, fracV = 0.68f, heightPx = unitH * 0.66f,
                    topColor = Color(0xFF696460), faceXColor = Color(0xFF403D3A), faceYColor = Color(0xFF524E4A)
                )
                if (tile != TileType.DEPLETED_VEIN) {
                    scope.drawCircle(veinColor, cam.tileW * 0.065f, Offset(cx - 5f, cy - unitH * 0.44f))
                    scope.drawCircle(veinColor.copy(alpha = 0.85f), cam.tileW * 0.055f, Offset(cx + 6f, cy - unitH * 0.34f))
                }
            }
            else -> Unit
        }
    }

    /**
     * Slim, connected timber-framed plaster walls with dressed stone foundation plinth,
     * dark oak corner pillars, cross-braces, and optional glowing lattice windows!
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
        nowMs: Long
    ) {
        val h = cam.tileH
        val hasN = isBuildingWall(floorMap.tileAt(tx, ty - 1))
        val hasS = isBuildingWall(floorMap.tileAt(tx, ty + 1))
        val hasW = isBuildingWall(floorMap.tileAt(tx - 1, ty))
        val hasE = isBuildingWall(floorMap.tileAt(tx + 1, ty))

        val foundationH = h * 0.24f
        val plasterH = h * 1.02f
        val beamThickness = 0.34f

        val plasterTop = Color(0xFF6B523B) // Timber top wall-plate
        val plasterFaceA = Color(0xFFE6DCC8)
        val plasterFaceB = Color(0xFFD1C4AB)

        fun drawSegment(fracU: Float, fracV: Float, offU: Float, offV: Float) {
            // Dressed stone foundation trim
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = fracU + 0.04f, fracV = fracV + 0.04f,
                heightPx = foundationH, offsetU = offU, offsetV = offV,
                topColor = Color(0xFF8C857A), faceXColor = Color(0xFF5D574F), faceYColor = Color(0xFF706960)
            )
            // Warm cream plaster wall body
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = fracU, fracV = fracV,
                heightPx = plasterH, baseZ = foundationH, offsetU = offU, offsetV = offV,
                topColor = plasterTop, faceXColor = plasterFaceB, faceYColor = plasterFaceA
            )
        }

        // Core dark timber corner pillar
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.36f, fracV = 0.36f,
            heightPx = foundationH + plasterH + h * 0.04f,
            topColor = Color(0xFF5C3E26), faceXColor = Color(0xFF3E2817), faceYColor = Color(0xFF4D331E)
        )

        if (hasE) drawSegment(0.50f, beamThickness, 0.25f, 0f)
        if (hasW) drawSegment(0.50f, beamThickness, -0.25f, 0f)
        if (hasS) drawSegment(beamThickness, 0.50f, 0f, 0.25f)
        if (hasN) drawSegment(beamThickness, 0.50f, 0f, -0.25f)

        // Warm glowing mullioned window
        if (hasWindow) {
            val flicker = 0.85f + 0.15f * sin(nowMs * 0.005 + tx * 2.1).toFloat()
            val winY = cy - foundationH - plasterH * 0.52f
            val winW = cam.tileW * 0.15f
            val winH = h * 0.34f
            scope.drawRect(Color(0xFF4A311D), Offset(cx - winW * 0.6f, winY - winH * 0.55f), Size(winW * 1.2f, winH * 1.1f))
            scope.drawRect(Color(0xFFFFD87A).copy(alpha = flicker), Offset(cx - winW * 0.45f, winY - winH * 0.42f), Size(winW * 0.9f, winH * 0.84f))
            scope.drawLine(Color(0xFF4A311D), Offset(cx, winY - winH * 0.42f), Offset(cx, winY + winH * 0.42f), 1.2f)
            scope.drawLine(Color(0xFF4A311D), Offset(cx - winW * 0.45f, winY), Offset(cx + winW * 0.45f, winY), 1.2f)
        }
    }

    /**
     * Dressed pale-limestone Temple Sanctuary walls with golden frieze trim & glowing stained glass accents.
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
        nowMs: Long
    ) {
        val h = cam.tileH
        val hasN = isBuildingWall(floorMap.tileAt(tx, ty - 1))
        val hasS = isBuildingWall(floorMap.tileAt(tx, ty + 1))
        val hasW = isBuildingWall(floorMap.tileAt(tx - 1, ty))
        val hasE = isBuildingWall(floorMap.tileAt(tx + 1, ty))
        val beamThickness = 0.36f

        fun drawSeg(fracU: Float, fracV: Float, offU: Float, offV: Float) {
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = fracU + 0.05f, fracV = fracV + 0.05f,
                heightPx = h * 0.24f, offsetU = offU, offsetV = offV,
                topColor = Color(0xFFB5AA98), faceXColor = Color(0xFF82796B), faceYColor = Color(0xFF998F7F)
            )
            drawRotatedPrism(
                scope, path, cam, cx, cy, fracU = fracU, fracV = fracV,
                heightPx = h * 1.18f, baseZ = h * 0.24f, offsetU = offU, offsetV = offV,
                topColor = Color(0xFFD9B86A), faceXColor = Color(0xFFC4B9A5), faceYColor = Color(0xFFDDD3C1)
            )
        }

        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.40f, fracV = 0.40f,
            heightPx = h * 1.46f,
            topColor = Color(0xFFE0C070), faceXColor = Color(0xFFB3A894), faceYColor = Color(0xFFCCC2AE)
        )
        if (hasE) drawSeg(0.50f, beamThickness, 0.25f, 0f)
        if (hasW) drawSeg(0.50f, beamThickness, -0.25f, 0f)
        if (hasS) drawSeg(beamThickness, 0.50f, 0f, 0.25f)
        if (hasN) drawSeg(beamThickness, 0.50f, 0f, -0.25f)

        // Glowing stained-glass lancet accent on every other temple wall bay
        if ((tx + ty) % 3 == 0) {
            val shimmer = 0.8f + 0.2f * sin(nowMs * 0.004 + tx).toFloat()
            val gy = cy - h * 0.85f
            scope.drawOval(Color(0xFF63B8FF).copy(alpha = shimmer), Offset(cx - 4f, gy - 8f), Size(8f, 14f))
            scope.drawOval(TorchGold, Offset(cx - 4f, gy - 8f), Size(8f, 14f), style = Stroke(1.2f))
        }
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
        playerFy: Float
    ) {
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

        val mainCol = Color(building.roofColorArgb).copy(alpha = alpha)
        val hiCol = Color(building.roofHighlightArgb).copy(alpha = alpha)
        val trimCol = Color(building.roofTrimArgb).copy(alpha = alpha)

        // North roof slope
        path.reset()
        path.moveTo(nw.x, nw.y); path.lineTo(ne.x, ne.y); path.lineTo(ridgeE.x, ridgeE.y); path.lineTo(ridgeW.x, ridgeW.y); path.close()
        scope.drawPath(path, color = mainCol)
        scope.drawPath(path, color = trimCol, style = Stroke(width = 1.6f))

        // South roof slope
        path.reset()
        path.moveTo(sw.x, sw.y); path.lineTo(se.x, se.y); path.lineTo(ridgeE.x, ridgeE.y); path.lineTo(ridgeW.x, ridgeW.y); path.close()
        scope.drawPath(path, color = hiCol)
        scope.drawPath(path, color = trimCol, style = Stroke(width = 1.6f))

        // West & East gables
        path.reset()
        path.moveTo(nw.x, nw.y); path.lineTo(sw.x, sw.y); path.lineTo(ridgeW.x, ridgeW.y); path.close()
        scope.drawPath(path, color = mainCol)

        path.reset()
        path.moveTo(ne.x, ne.y); path.lineTo(se.x, se.y); path.lineTo(ridgeE.x, ridgeE.y); path.close()
        scope.drawPath(path, color = mainCol)

        // Ridge beam highlight
        scope.drawLine(trimCol, ridgeW, ridgeE, strokeWidth = 2.6f)
    }

    private fun drawStoneFountain(scope: DrawScope, path: Path, cam: IsometricCamera, cx: Float, cy: Float, nowMs: Long) {
        val h = cam.tileH
        // Wide octagonal stone fountain basin
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.90f, fracV = 0.90f, heightPx = h * 0.34f,
            topColor = Color(0xFFB5ADA0), faceXColor = Color(0xFF7D766B), faceYColor = Color(0xFF968E82)
        )
        // Sparkling azure water pool
        drawRotatedQuad(scope, path, cam, cx, cy, fracU = 0.72f, fracV = 0.72f, zPx = h * 0.35f, fillColor = Color(0xFF399FD9))
        // Center pedestal & animated water plume
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.24f, fracV = 0.24f, heightPx = h * 0.52f, baseZ = h * 0.34f,
            topColor = Color(0xFFD1C9BC), faceXColor = Color(0xFF8C857A), faceYColor = Color(0xFFA8A094)
        )
        val spray = sin(nowMs * 0.008).toFloat() * 3f
        val topY = cy - h * 0.96f + spray
        scope.drawCircle(Color(0xBFE0F7FF), cam.tileW * 0.08f, Offset(cx, topY))
        scope.drawLine(Color(0xAA88D8FF), Offset(cx, topY), Offset(cx - cam.tileW * 0.16f, cy - h * 0.36f), 2f)
        scope.drawLine(Color(0xAA88D8FF), Offset(cx, topY), Offset(cx + cam.tileW * 0.16f, cy - h * 0.36f), 2f)
    }

    private fun drawWoodenFence(
        scope: DrawScope,
        path: Path,
        cam: IsometricCamera,
        floorMap: WorldFloor,
        tx: Int,
        ty: Int,
        cx: Float,
        cy: Float
    ) {
        val h = cam.tileH
        val postCol = Color(0xFF6B4728)
        val railCol = Color(0xFF8C623B)
        // Center fence post
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.16f, fracV = 0.16f, heightPx = h * 0.56f,
            topColor = railCol, faceXColor = Color(0xFF4A301A), faceYColor = postCol
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
        ty: Int
    ) {
        val h = cam.tileH
        // Stone base + slim wrought-iron pole
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.24f, fracV = 0.24f, heightPx = h * 0.18f,
            topColor = Color(0xFF7D7872), faceXColor = Color(0xFF4A4743), faceYColor = Color(0xFF5E5A55)
        )
        scope.drawLine(Color(0xFF2B2C30), Offset(cx, cy - h * 0.14f), Offset(cx, cy - h * 1.28f), 2.8f)
        scope.drawLine(Color(0xFF2B2C30), Offset(cx, cy - h * 1.24f), Offset(cx + 6f, cy - h * 1.24f), 2.2f)
        // Warm lantern glow
        drawWarmFlickerGlow(scope, cx + 5f, cy - h * 1.18f, h * 0.92f, nowMs, tx, ty)
    }

    private fun drawMarketAwningStall(scope: DrawScope, path: Path, cam: IsometricCamera, cx: Float, cy: Float) {
        val h = cam.tileH
        // Timber goods counter underneath
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.78f, fracV = 0.58f, heightPx = h * 0.36f,
            topColor = Color(0xFFB58954), faceXColor = Color(0xFF6B4A26), faceYColor = Color(0xFF876035)
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

    private fun drawCrateAndBarrel(scope: DrawScope, path: Path, cam: IsometricCamera, cx: Float, cy: Float) {
        val h = cam.tileH
        // Wooden crate
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.44f, fracV = 0.44f, heightPx = h * 0.44f, offsetU = -0.18f, offsetV = -0.06f,
            topColor = Color(0xFFB88B56), faceXColor = Color(0xFF73502B), faceYColor = Color(0xFF946B3C)
        )
        // Oak barrel beside crate
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.34f, fracV = 0.34f, heightPx = h * 0.50f, offsetU = 0.20f, offsetV = 0.14f,
            topColor = Color(0xFF8C5D33), faceXColor = Color(0xFF52331A), faceYColor = Color(0xFF6B4524)
        )
    }

    private fun drawMooredRowboat(scope: DrawScope, path: Path, cam: IsometricCamera, cx: Float, cy: Float, nowMs: Long, tx: Int, ty: Int) {
        val bob = sin(nowMs * 0.0035 + tx + ty).toFloat() * 2.2f
        val h = cam.tileH
        // Gentle water wake around hull
        scope.drawOval(
            Color(0x48D6F5FF),
            Offset(cx - cam.tileW * 0.36f, cy - h * 0.20f + bob),
            Size(cam.tileW * 0.72f, h * 0.44f),
            style = Stroke(1.4f)
        )
        // Timber rowboat hull & bench plank
        drawRotatedPrism(
            scope, path, cam, cx, cy + bob, fracU = 0.78f, fracV = 0.38f, heightPx = h * 0.22f,
            topColor = Color(0xFF6E4726), faceXColor = Color(0xFF472B14), faceYColor = Color(0xFF5C3A1D)
        )
        drawRotatedQuad(scope, path, cam, cx, cy + bob, fracU = 0.18f, fracV = 0.34f, zPx = h * 0.23f, fillColor = Color(0xFFA87B4C))
    }

    private fun drawTreeProp(scope: DrawScope, path: Path, cam: IsometricCamera, cx: Float, cy: Float, tx: Int, ty: Int, nowMs: Long) {
        val h = cam.tileH
        val w = cam.tileW
        val sway = sin(nowMs * 0.002 + tx * 0.7 + ty * 0.5).toFloat() * 1.8f

        // Ground contact shadow
        scope.drawOval(Color(0x45000000), Offset(cx - w * 0.30f, cy - h * 0.14f), Size(w * 0.60f, h * 0.36f))

        // Bark trunk
        drawRotatedPrism(
            scope, path, cam, cx, cy, fracU = 0.20f, fracV = 0.20f, heightPx = h * 0.65f,
            topColor = Color(0xFF5C3A20), faceXColor = Color(0xFF3E2614), faceYColor = Color(0xFF4F321B)
        )

        // Multi-tiered lush foliage canopy
        val darkLeaf = if (tx >= 272) Color(0xFF16361D) else Color(0xFF1F4F24)
        val midLeaf = if (tx >= 272) Color(0xFF214D29) else Color(0xFF2E6E32)
        val hiLeaf = if (tx >= 272) Color(0xFF346E3C) else Color(0xFF4B9144)

        scope.drawOval(darkLeaf, Offset(cx - w * 0.38f + sway * 0.5f, cy - h * 1.48f), Size(w * 0.76f, h * 0.94f))
        scope.drawOval(midLeaf, Offset(cx - w * 0.32f + sway * 0.8f, cy - h * 1.78f), Size(w * 0.64f, h * 0.82f))
        scope.drawOval(hiLeaf, Offset(cx - w * 0.20f + sway, cy - h * 2.02f), Size(w * 0.42f, h * 0.52f))
    }

    private fun drawWarmFlickerGlow(scope: DrawScope, x: Float, y: Float, radius: Float, nowMs: Long, tx: Int, ty: Int) {
        val flicker = 0.86f + 0.14f * sin(nowMs * 0.011 + tx * 1.7 + ty * 2.3).toFloat()
        scope.drawCircle(
            brush = Brush.radialGradient(
                listOf(TorchGold.copy(alpha = 0.80f * flicker), TorchAmber.copy(alpha = 0.26f * flicker), Color.Transparent),
                center = Offset(x, y),
                radius = radius * flicker
            ),
            radius = radius * flicker,
            center = Offset(x, y)
        )
        scope.drawCircle(Color(0xFFFFF2B2), 2.8f, Offset(x, y))
    }
}
