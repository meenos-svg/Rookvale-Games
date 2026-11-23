package com.example.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.game.model.TileType
import com.example.game.world.WorldFloor
import com.example.ui.theme.FireOrange
import com.example.ui.theme.TorchAmber
import com.example.ui.theme.TorchGold
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure 2:1 diamond isometric camera math (screen <-> tile projection and strict viewport culling).
 */
data class IsoBounds(
    val minTx: Int,
    val maxTx: Int,
    val minTy: Int,
    val maxTy: Int
)

class IsometricCamera {
    var tileW: Float = 96f
    var tileH: Float = 48f
    var originX: Float = 0f
    var originY: Float = 0f
    var zoom: Float = 1f

    val halfW: Float get() = tileW * 0.5f
    val halfH: Float get() = tileH * 0.5f

    fun configure(
        viewportW: Float,
        viewportH: Float,
        camTileX: Float,
        camTileY: Float,
        zoomFactor: Float
    ) {
        zoom = zoomFactor.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val baseTileW = (min(viewportW, viewportH) * 0.21f).coerceIn(68f, 124f)
        tileW = baseTileW * zoom
        tileH = tileW * 0.5f // True 2:1 isometric diamond ratio
        // Place camera target centered slightly below mid-screen so tall extruded walls/trees fit comfortably
        originX = viewportW * 0.5f - (camTileX - camTileY) * halfW
        originY = viewportH * 0.52f - (camTileX + camTileY) * halfH
    }

    fun tileCenterToScreen(tx: Float, ty: Float): Offset = Offset(
        x = originX + (tx - ty) * halfW,
        y = originY + (tx + ty) * halfH
    )

    fun screenToTileFloat(sx: Float, sy: Float): Pair<Float, Float> {
        val hw = halfW.coerceAtLeast(1f)
        val hh = halfH.coerceAtLeast(1f)
        val dx = sx - originX
        val dy = sy - originY
        val fx = (dx / hw + dy / hh) * 0.5f
        val fy = (dy / hh - dx / hw) * 0.5f
        return fx to fy
    }

    fun screenToTile(sx: Float, sy: Float): Pair<Int, Int> {
        val (fx, fy) = screenToTileFloat(sx, sy)
        return floor(fx + 0.5f).toInt() to floor(fy + 0.5f).toInt()
    }

    fun visibleTileBounds(
        viewportW: Float,
        viewportH: Float,
        floorWidth: Int,
        floorHeight: Int,
        margin: Int = 3
    ): IsoBounds {
        val c0 = screenToTileFloat(0f, 0f)
        val c1 = screenToTileFloat(viewportW, 0f)
        val c2 = screenToTileFloat(0f, viewportH)
        val c3 = screenToTileFloat(viewportW, viewportH)
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
        const val MIN_ZOOM = 0.65f
        const val MAX_ZOOM = 1.65f
    }
}

/**
 * High-FPS 2.5D procedural isometric tile, extruded wall, doorway, tree, water, torch & prop renderer.
 * Uses a single reusable Path to eliminate per-tile heap allocations.
 */
object IsometricTileDrawer {

    fun isTallProp(tile: TileType): Boolean = when (tile) {
        TileType.STONE_WALL,
        TileType.CITY_WALL,
        TileType.TORCH_WALL,
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
        TileType.DEPLETED_VEIN -> true
        else -> false
    }

    fun drawDiamond(
        scope: DrawScope,
        path: Path,
        cx: Float,
        cy: Float,
        hw: Float,
        hh: Float,
        fillColor: Color,
        strokeColor: Color? = null,
        strokeWidth: Float = 1f
    ) {
        path.reset()
        path.moveTo(cx, cy - hh)
        path.lineTo(cx + hw, cy)
        path.lineTo(cx, cy + hh)
        path.lineTo(cx - hw, cy)
        path.close()
        scope.drawPath(path, color = fillColor)
        if (strokeColor != null) {
            scope.drawPath(path, color = strokeColor, style = Stroke(width = strokeWidth))
        }
    }

    fun drawGroundTile(
        scope: DrawScope,
        path: Path,
        tile: TileType,
        floorMap: WorldFloor,
        tx: Int,
        ty: Int,
        cx: Float,
        cy: Float,
        hw: Float,
        hh: Float,
        nowMs: Long
    ) {
        val hash = tileHash(tx, ty)
        val shadeJitter = ((hash % 5) - 2) * 0.015f

        val baseColor = when (tile) {
            TileType.GRASS -> adjustBrightness(Color(0xFF3F6F36), shadeJitter)
            TileType.DARK_GRASS -> adjustBrightness(Color(0xFF244729), shadeJitter)
            TileType.COBBLESTONE -> adjustBrightness(Color(0xFF7B7468), shadeJitter)
            TileType.TEMPLE_FLOOR -> adjustBrightness(Color(0xFFA8A092), shadeJitter * 0.7f)
            TileType.WOOD_PIER -> adjustBrightness(Color(0xFF7C593B), shadeJitter)
            TileType.DIRT_FLOOR -> adjustBrightness(Color(0xFF634B36), shadeJitter)
            TileType.CRYPT_FLOOR -> adjustBrightness(Color(0xFF494E5A), shadeJitter)
            TileType.OBSIDIAN_FLOOR -> adjustBrightness(Color(0xFF332B3D), shadeJitter)
            TileType.WATER -> Color(0xFF1E5685)
            TileType.LAVA -> Color(0xFFB83214)
            TileType.STAIRS_DOWN, TileType.STAIRS_UP -> Color(0xFF5D574F)
            TileType.ROPE_HOLE_DOWN -> Color(0xFF2E2722)
            TileType.ROPE_SPOT_UP -> Color(0xFF736549)
            TileType.TREE, TileType.TREE_STUMP -> adjustBrightness(
                if (floorMap.floorId == 0 && tx > 62) Color(0xFF244729) else Color(0xFF386631),
                shadeJitter
            )
            TileType.STONE_WALL, TileType.CITY_WALL, TileType.TORCH_WALL -> Color(0xFF47433E)
            TileType.CAVE_WALL, TileType.MOUNTAIN_ROCK -> Color(0xFF3A3634)
            TileType.DEPOT_CHEST, TileType.MINI_DEPOT, TileType.SHOP_COUNTER,
            TileType.TEMPLE_ALTAR, TileType.RUNECRAFTING_TABLE -> Color(0xFF837B6F)
            TileType.SMELTING_FURNACE, TileType.BLACKSMITH_ANVIL, TileType.FLETCHING_BENCH -> Color(0xFF6B6256)
            TileType.ORE_OCHRE, TileType.ORE_BOGSTONE, TileType.ORE_BRIMSTONE,
            TileType.ORE_AETHERIUM, TileType.DEPLETED_VEIN -> Color(0xFF4D4339)
        }

        // Slightly expand by +0.6px to prevent subpixel hairline seams between adjacent diamonds
        drawDiamond(
            scope = scope,
            path = path,
            cx = cx,
            cy = cy,
            hw = hw + 0.6f,
            hh = hh + 0.4f,
            fillColor = baseColor,
            strokeColor = when (tile) {
                TileType.COBBLESTONE, TileType.TEMPLE_FLOOR -> Color.Black.copy(alpha = 0.16f)
                TileType.CRYPT_FLOOR, TileType.OBSIDIAN_FLOOR -> Color.Black.copy(alpha = 0.22f)
                else -> Color.Black.copy(alpha = 0.06f)
            },
            strokeWidth = 1f
        )

        when (tile) {
            TileType.GRASS, TileType.DARK_GRASS -> {
                val tuftCol = if (tile == TileType.GRASS) Color(0xFF579147) else Color(0xFF346239)
                if (hash % 3 == 0) {
                    val ox = ((hash % 7) - 3) * hw * 0.11f
                    val oy = (((hash / 7) % 5) - 2) * hh * 0.14f
                    scope.drawLine(tuftCol, Offset(cx + ox, cy + oy), Offset(cx + ox - 2f, cy + oy - hh * 0.22f), strokeWidth = 2f)
                    scope.drawLine(tuftCol, Offset(cx + ox + 2f, cy + oy + 1f), Offset(cx + ox + 4f, cy + oy - hh * 0.18f), strokeWidth = 1.8f)
                }
                if (tile == TileType.GRASS && hash % 19 == 0) {
                    scope.drawCircle(Color(0xFFEBD97A), radius = hw * 0.045f, center = Offset(cx + hw * 0.16f, cy - hh * 0.1f))
                }
                // Roadside wooden pasture fence accent along water/road border on Floor 0
                if (floorMap.floorId == 0 && tile == TileType.GRASS && tx in 37..58 && ty in 35..51) {
                    val northWater = floorMap.tileAt(tx, ty - 1) == TileType.WATER
                    val southWater = floorMap.tileAt(tx, ty + 1) == TileType.WATER
                    if (northWater || southWater) {
                        drawPastureRail(scope, cx, cy, hw, hh, northEdge = northWater)
                    }
                }
            }
            TileType.COBBLESTONE, TileType.TEMPLE_FLOOR -> {
                // Crisp inner bevel stone seams
                val seamCol = if (tile == TileType.TEMPLE_FLOOR) Color(0xFFC9C0AE).copy(alpha = 0.4f) else Color(0xFF5E574D).copy(alpha = 0.45f)
                scope.drawLine(seamCol, Offset(cx - hw * 0.48f, cy - hh * 0.02f), Offset(cx + hw * 0.02f, cy - hh * 0.48f), strokeWidth = 1.2f)
                scope.drawLine(seamCol, Offset(cx - hw * 0.02f, cy + hh * 0.48f), Offset(cx + hw * 0.48f, cy + hh * 0.02f), strokeWidth = 1.2f)
                if (tile == TileType.TEMPLE_FLOOR && (tx + ty) % 2 == 0) {
                    drawDiamond(scope, path, cx, cy, hw * 0.42f, hh * 0.42f, Color(0xFFD6C8A5).copy(alpha = 0.16f))
                }
            }
            TileType.WOOD_PIER -> {
                // Wooden pier planks + dark water pilings underneath
                val plankCol = Color(0xFF5A3D25)
                for (i in -2..2) {
                    val f = i * 0.18f
                    scope.drawLine(
                        plankCol,
                        Offset(cx - hw * 0.55f + f * hw, cy - f * hh),
                        Offset(cx + hw * 0.05f + f * hw, cy + hh * 0.55f - f * hh),
                        strokeWidth = 1.6f
                    )
                }
                // Corner pier post
                if ((tx + ty) % 3 == 0) {
                    scope.drawRect(Color(0xFF472F1C), Offset(cx - hw * 0.12f, cy - hh * 0.85f), Size(hw * 0.14f, hh * 0.8f))
                    scope.drawRect(Color(0xFF9C7552), Offset(cx - hw * 0.12f, cy - hh * 0.88f), Size(hw * 0.14f, hh * 0.14f))
                }
            }
            TileType.WATER -> {
                val wavePhase = (nowMs / 360.0) + tx * 0.7 + ty * 0.5
                val shimmer = ((sin(wavePhase) + 1.0) * 0.5).toFloat()
                // Shimmering water highlights
                val rippleAlpha = 0.22f + shimmer * 0.28f
                val rippleOffset = (shimmer - 0.5f) * hw * 0.18f
                scope.drawLine(
                    Color(0xFF7FD0FF).copy(alpha = rippleAlpha),
                    Offset(cx - hw * 0.32f + rippleOffset, cy - hh * 0.12f),
                    Offset(cx + hw * 0.18f + rippleOffset, cy - hh * 0.12f),
                    strokeWidth = 2.2f
                )
                scope.drawLine(
                    Color(0xFF9FE1FF).copy(alpha = rippleAlpha * 0.85f),
                    Offset(cx - hw * 0.12f - rippleOffset, cy + hh * 0.20f),
                    Offset(cx + hw * 0.30f - rippleOffset, cy + hh * 0.20f),
                    strokeWidth = 1.8f
                )
                // Shoreline rocks & reeds wherever water touches walkable land
                val touchesLand = floorMap.tileAt(tx - 1, ty).walkable ||
                    floorMap.tileAt(tx + 1, ty).walkable ||
                    floorMap.tileAt(tx, ty - 1).walkable ||
                    floorMap.tileAt(tx, ty + 1).walkable
                if (touchesLand) {
                    if (hash % 2 == 0) {
                        // Shoreline smooth boulder
                        scope.drawOval(Color(0xFF4D5863), Offset(cx - hw * 0.36f, cy - hh * 0.26f), Size(hw * 0.28f, hh * 0.32f))
                        scope.drawOval(Color(0xFF73818F), Offset(cx - hw * 0.33f, cy - hh * 0.26f), Size(hw * 0.18f, hh * 0.16f))
                    }
                    if (hash % 3 != 1) {
                        // Swaying shoreline cattail reeds
                        val sway = sin(wavePhase * 0.9).toFloat() * 2.5f
                        val rx = cx + hw * 0.18f
                        val ry = cy + hh * 0.05f
                        scope.drawLine(Color(0xFF3E783B), Offset(rx, ry), Offset(rx + sway, ry - hh * 0.65f), strokeWidth = 2.2f)
                        scope.drawLine(Color(0xFF4E8C46), Offset(rx + 4f, ry + 2f), Offset(rx + 6f + sway, ry - hh * 0.55f), strokeWidth = 2f)
                        scope.drawCircle(Color(0xFF6B3E26), radius = 2.4f, center = Offset(rx + sway, ry - hh * 0.58f))
                    }
                }
            }
            TileType.LAVA -> {
                val pulse = ((sin(nowMs / 280.0 + tx + ty) + 1.0) * 0.5).toFloat()
                drawDiamond(scope, path, cx, cy, hw * 0.65f, hh * 0.65f, FireOrange.copy(alpha = 0.45f + pulse * 0.3f))
                scope.drawLine(
                    Color(0xFFFFE066).copy(alpha = 0.65f + pulse * 0.3f),
                    Offset(cx - hw * 0.26f, cy),
                    Offset(cx + hw * 0.24f, cy - hh * 0.12f),
                    strokeWidth = 2.6f
                )
            }
            TileType.STAIRS_DOWN, TileType.STAIRS_UP -> {
                val stepColor = if (tile == TileType.STAIRS_UP) Color(0xFFB5A996) else Color(0xFF3B3630)
                val rimColor = if (tile == TileType.STAIRS_UP) TorchGold else Color(0xFF211E1A)
                drawDiamond(scope, path, cx, cy, hw * 0.72f, hh * 0.72f, rimColor)
                for (step in 0..3) {
                    val f = (step - 1.5f) * 0.22f
                    val yOff = cy + f * hh
                    scope.drawLine(stepColor, Offset(cx - hw * 0.42f, yOff), Offset(cx + hw * 0.42f, yOff), strokeWidth = hh * 0.17f)
                }
                if (tile == TileType.STAIRS_UP) {
                    scope.drawCircle(TorchGold.copy(alpha = 0.22f), hw * 0.45f, Offset(cx, cy - hh * 0.25f))
                }
            }
            TileType.ROPE_HOLE_DOWN -> {
                scope.drawOval(Color(0xFF14110F), Offset(cx - hw * 0.42f, cy - hh * 0.42f), Size(hw * 0.84f, hh * 0.84f))
                scope.drawOval(Color(0xFF6E5C48), Offset(cx - hw * 0.42f, cy - hh * 0.42f), Size(hw * 0.84f, hh * 0.84f), style = Stroke(2.5f))
            }
            TileType.ROPE_SPOT_UP -> {
                scope.drawOval(TorchGold.copy(alpha = 0.30f), Offset(cx - hw * 0.48f, cy - hh * 0.48f), Size(hw * 0.96f, hh * 0.96f))
                scope.drawOval(Color(0xFFFFF0A8).copy(alpha = 0.55f), Offset(cx - hw * 0.28f, cy - hh * 0.28f), Size(hw * 0.56f, hh * 0.56f))
            }
            else -> Unit
        }

        // Open doorway threshold arch when a walkable tile sits directly between two walls
        if (tile.walkable && isDoorwayTile(floorMap, tx, ty)) {
            drawOpenDoorwayThreshold(scope, cx, cy, hw, hh)
        }
    }

    fun drawTallProp(
        scope: DrawScope,
        path: Path,
        tile: TileType,
        floorMap: WorldFloor,
        tx: Int,
        ty: Int,
        cx: Float,
        cy: Float,
        hw: Float,
        hh: Float,
        nowMs: Long
    ) {
        when (tile) {
            TileType.STONE_WALL -> {
                val isTownBuilding = floorMap.floorId == 0 && tx in 35..60 && ty in 33..54
                drawExtrudedBlock(
                    scope = scope,
                    path = path,
                    cx = cx,
                    cy = cy,
                    hw = hw,
                    hh = hh,
                    height = hh * 1.65f,
                    topColor = if (isTownBuilding) Color(0xFF7D6B59) else Color(0xFF6B655D),
                    leftColor = if (isTownBuilding) Color(0xFF5C4E41) else Color(0xFF524D47),
                    rightColor = if (isTownBuilding) Color(0xFF42372E) else Color(0xFF3B3733),
                    timberTrim = isTownBuilding
                )
                // Add decorative wooden merchant cart beside town shop/depot exterior wall
                if (isTownBuilding && ((tx == 53 && ty == 42) || (tx == 41 && ty == 42))) {
                    drawMerchantCartAccent(scope, cx + hw * 0.35f, cy + hh * 0.15f, hw * 0.52f, hh * 0.52f)
                }
            }
            TileType.CITY_WALL -> {
                drawExtrudedBlock(
                    scope = scope,
                    path = path,
                    cx = cx,
                    cy = cy,
                    hw = hw,
                    hh = hh,
                    height = hh * 2.15f,
                    topColor = Color(0xFF7E7A74),
                    leftColor = Color(0xFF5C5853),
                    rightColor = Color(0xFF403D39),
                    battlements = true
                )
            }
            TileType.TORCH_WALL -> {
                drawExtrudedBlock(
                    scope = scope,
                    path = path,
                    cx = cx,
                    cy = cy,
                    hw = hw,
                    hh = hh,
                    height = hh * 1.75f,
                    topColor = Color(0xFF70685F),
                    leftColor = Color(0xFF575049),
                    rightColor = Color(0xFF3E3934),
                    timberTrim = floorMap.floorId == 0
                )
                drawFlickeringWallTorch(scope, cx, cy - hh * 1.15f, hw, hh, nowMs, tx * 31 + ty * 17)
            }
            TileType.MOUNTAIN_ROCK, TileType.CAVE_WALL -> {
                val isCave = tile == TileType.CAVE_WALL
                drawExtrudedBlock(
                    scope = scope,
                    path = path,
                    cx = cx,
                    cy = cy,
                    hw = hw * 0.98f,
                    hh = hh * 0.98f,
                    height = hh * (if (isCave) 1.55f else 1.85f),
                    topColor = if (isCave) Color(0xFF534B45) else Color(0xFF726D67),
                    leftColor = if (isCave) Color(0xFF3E3833) else Color(0xFF56514C),
                    rightColor = if (isCave) Color(0xFF2B2724) else Color(0xFF3D3935),
                    craggyTop = true
                )
            }
            TileType.TREE -> {
                drawIsometricTree(scope, path, cx, cy, hw, hh, nowMs, tx, ty, darkForest = floorMap.floorId == 0 && tx > 62)
            }
            TileType.TREE_STUMP -> {
                scope.drawOval(Color.Black.copy(alpha = 0.28f), Offset(cx - hw * 0.34f, cy - hh * 0.16f), Size(hw * 0.68f, hh * 0.42f))
                scope.drawRect(Color(0xFF6B472B), Offset(cx - hw * 0.22f, cy - hh * 0.32f), Size(hw * 0.44f, hh * 0.38f))
                scope.drawOval(Color(0xFFB88C5A), Offset(cx - hw * 0.22f, cy - hh * 0.42f), Size(hw * 0.44f, hh * 0.24f))
                scope.drawOval(Color(0xFF8C6239), Offset(cx - hw * 0.13f, cy - hh * 0.37f), Size(hw * 0.26f, hh * 0.14f), style = Stroke(1.3f))
            }
            TileType.DEPOT_CHEST, TileType.MINI_DEPOT -> {
                drawExtrudedBlock(
                    scope, path, cx, cy, hw * 0.72f, hh * 0.72f, hh * 0.85f,
                    topColor = Color(0xFF9C693B),
                    leftColor = Color(0xFF784E29),
                    rightColor = Color(0xFF59381B)
                )
                // Gold lock clasp & iron bands
                scope.drawRect(TorchGold, Offset(cx - hw * 0.08f, cy - hh * 0.55f), Size(hw * 0.16f, hh * 0.26f))
            }
            TileType.SHOP_COUNTER -> {
                drawExtrudedBlock(
                    scope, path, cx, cy, hw * 0.84f, hh * 0.84f, hh * 0.85f,
                    topColor = Color(0xFFA6794B),
                    leftColor = Color(0xFF7B5734),
                    rightColor = Color(0xFF5D4025)
                )
                // Potion bottles on shop counter
                scope.drawCircle(Color(0xFFE04848), hw * 0.08f, Offset(cx - hw * 0.18f, cy - hh * 0.92f))
                scope.drawCircle(Color(0xFF4A90E2), hw * 0.08f, Offset(cx + hw * 0.14f, cy - hh * 0.84f))
            }
            TileType.TEMPLE_ALTAR -> {
                drawExtrudedBlock(
                    scope, path, cx, cy, hw * 0.78f, hh * 0.78f, hh * 1.05f,
                    topColor = Color(0xFFE6DEC8),
                    leftColor = Color(0xFFB8AF9C),
                    rightColor = Color(0xFF91897A)
                )
                drawDiamond(scope, path, cx, cy - hh * 1.05f, hw * 0.45f, hh * 0.45f, TorchGold.copy(alpha = 0.65f))
            }
            TileType.SMELTING_FURNACE -> {
                drawExtrudedBlock(
                    scope, path, cx, cy, hw * 0.86f, hh * 0.86f, hh * 1.35f,
                    topColor = Color(0xFF5D5753),
                    leftColor = Color(0xFF45403D),
                    rightColor = Color(0xFF332F2D)
                )
                val ember = ((sin(nowMs / 180.0 + tx) + 1.0) * 0.5).toFloat()
                scope.drawCircle(FireOrange.copy(alpha = 0.75f + ember * 0.2f), hw * 0.20f, Offset(cx + hw * 0.22f, cy - hh * 0.35f))
                scope.drawCircle(TorchGold, hw * 0.10f, Offset(cx + hw * 0.22f, cy - hh * 0.35f))
            }
            TileType.BLACKSMITH_ANVIL -> {
                drawExtrudedBlock(
                    scope, path, cx, cy, hw * 0.58f, hh * 0.58f, hh * 0.65f,
                    topColor = Color(0xFF7B5734),
                    leftColor = Color(0xFF5E4025),
                    rightColor = Color(0xFF472F1A)
                )
                drawExtrudedBlock(
                    scope, path, cx, cy - hh * 0.62f, hw * 0.54f, hh * 0.40f, hh * 0.32f,
                    topColor = Color(0xFF9AA3AD),
                    leftColor = Color(0xFF6B737D),
                    rightColor = Color(0xFF4E555E)
                )
            }
            TileType.FLETCHING_BENCH -> {
                drawExtrudedBlock(
                    scope, path, cx, cy, hw * 0.82f, hh * 0.82f, hh * 0.78f,
                    topColor = Color(0xFFB58652),
                    leftColor = Color(0xFF876037),
                    rightColor = Color(0xFF664728)
                )
                // Arrow shafts on bench
                scope.drawLine(Color(0xFFEBD6A7), Offset(cx - hw * 0.32f, cy - hh * 0.80f), Offset(cx + hw * 0.28f, cy - hh * 0.72f), strokeWidth = 2.6f)
                scope.drawLine(Color(0xFFE05A47), Offset(cx - hw * 0.32f, cy - hh * 0.80f), Offset(cx - hw * 0.22f, cy - hh * 0.86f), strokeWidth = 3f)
            }
            TileType.RUNECRAFTING_TABLE -> {
                drawExtrudedBlock(
                    scope, path, cx, cy, hw * 0.82f, hh * 0.82f, hh * 0.98f,
                    topColor = Color(0xFF4B3D66),
                    leftColor = Color(0xFF372C4D),
                    rightColor = Color(0xFF281F38)
                )
                val pulse = ((sin(nowMs / 260.0) + 1.0) * 0.5).toFloat()
                scope.drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFC084FC).copy(alpha = 0.55f + pulse * 0.3f), Color.Transparent),
                        center = Offset(cx, cy - hh * 1.05f),
                        radius = hw * 0.65f
                    ),
                    radius = hw * 0.65f,
                    center = Offset(cx, cy - hh * 1.05f)
                )
                drawDiamond(scope, path, cx, cy - hh * 0.98f, hw * 0.42f, hh * 0.42f, Color(0xFF9E6BFF).copy(alpha = 0.72f), Color(0xFFE9D5FF), 1.5f)
            }
            TileType.ORE_OCHRE, TileType.ORE_BOGSTONE, TileType.ORE_BRIMSTONE, TileType.ORE_AETHERIUM, TileType.DEPLETED_VEIN -> {
                val gemColor = when (tile) {
                    TileType.ORE_OCHRE -> Color(0xFFD99538)
                    TileType.ORE_BOGSTONE -> Color(0xFF56B870)
                    TileType.ORE_BRIMSTONE -> Color(0xFFE65C32)
                    TileType.ORE_AETHERIUM -> Color(0xFFA78BFA)
                    else -> Color(0xFF5C5852)
                }
                drawExtrudedBlock(
                    scope, path, cx, cy, hw * 0.72f, hh * 0.72f, hh * 0.88f,
                    topColor = Color(0xFF595550),
                    leftColor = Color(0xFF44403C),
                    rightColor = Color(0xFF33302D),
                    craggyTop = true
                )
                if (tile != TileType.DEPLETED_VEIN) {
                    scope.drawCircle(gemColor, hw * 0.13f, Offset(cx - hw * 0.14f, cy - hh * 0.82f))
                    scope.drawCircle(gemColor.copy(alpha = 0.9f), hw * 0.10f, Offset(cx + hw * 0.18f, cy - hh * 0.58f))
                    scope.drawCircle(Color.White.copy(alpha = 0.65f), hw * 0.045f, Offset(cx - hw * 0.17f, cy - hh * 0.86f))
                }
            }
            else -> Unit
        }
    }

    fun drawContactShadow(scope: DrawScope, cx: Float, cy: Float, hw: Float, hh: Float, scale: Float = 1f) {
        val sw = hw * 0.92f * scale
        val sh = hh * 0.62f * scale
        scope.drawOval(
            color = Color.Black.copy(alpha = 0.36f),
            topLeft = Offset(cx - sw * 0.5f, cy - sh * 0.35f),
            size = Size(sw, sh)
        )
    }

    private fun drawExtrudedBlock(
        scope: DrawScope,
        path: Path,
        cx: Float,
        cy: Float,
        hw: Float,
        hh: Float,
        height: Float,
        topColor: Color,
        leftColor: Color,
        rightColor: Color,
        timberTrim: Boolean = false,
        battlements: Boolean = false,
        craggyTop: Boolean = false
    ) {
        // Soft ground drop shadow on SE side
        scope.drawOval(
            color = Color.Black.copy(alpha = 0.26f),
            topLeft = Offset(cx - hw * 0.92f, cy - hh * 0.55f),
            size = Size(hw * 1.95f, hh * 1.45f)
        )

        val topY = cy - height

        // Left (SW) face
        path.reset()
        path.moveTo(cx - hw, topY)
        path.lineTo(cx, topY + hh)
        path.lineTo(cx, cy + hh)
        path.lineTo(cx - hw, cy)
        path.close()
        scope.drawPath(path, color = leftColor)
        scope.drawPath(path, color = Color.Black.copy(alpha = 0.24f), style = Stroke(1f))

        // Right (SE) face
        path.reset()
        path.moveTo(cx + hw, topY)
        path.lineTo(cx, topY + hh)
        path.lineTo(cx, cy + hh)
        path.lineTo(cx + hw, cy)
        path.close()
        scope.drawPath(path, color = rightColor)
        scope.drawPath(path, color = Color.Black.copy(alpha = 0.28f), style = Stroke(1f))

        // Top diamond cap
        drawDiamond(
            scope = scope,
            path = path,
            cx = cx,
            cy = topY,
            hw = hw,
            hh = hh,
            fillColor = topColor,
            strokeColor = Color.White.copy(alpha = 0.12f),
            strokeWidth = 1f
        )

        // Subtle stone masonry courses or timber framing
        if (timberTrim) {
            val timber = Color(0xFF3E2918)
            scope.drawLine(timber, Offset(cx - hw, topY + height * 0.5f), Offset(cx, topY + hh + height * 0.5f), strokeWidth = 2.4f)
            scope.drawLine(timber, Offset(cx, topY + hh + height * 0.5f), Offset(cx + hw, topY + height * 0.5f), strokeWidth = 2.4f)
            scope.drawLine(timber, Offset(cx, topY + hh), Offset(cx, cy + hh), strokeWidth = 2.8f)
        } else {
            val mortar = Color.Black.copy(alpha = 0.18f)
            scope.drawLine(mortar, Offset(cx - hw, topY + height * 0.45f), Offset(cx, topY + hh + height * 0.45f), strokeWidth = 1.4f)
            scope.drawLine(mortar, Offset(cx, topY + hh + height * 0.45f), Offset(cx + hw, topY + height * 0.45f), strokeWidth = 1.4f)
        }

        if (battlements) {
            // Raised parapet merlon blocks on city walls
            drawDiamond(scope, path, cx - hw * 0.38f, topY - hh * 0.22f, hw * 0.26f, hh * 0.26f, Color(0xFF948F88))
            drawDiamond(scope, path, cx + hw * 0.38f, topY - hh * 0.22f, hw * 0.26f, hh * 0.26f, Color(0xFF948F88))
        }

        if (craggyTop) {
            // Sunlit ridge highlight on mountain/cave rock
            drawDiamond(scope, path, cx - hw * 0.12f, topY - hh * 0.12f, hw * 0.48f, hh * 0.44f, Color.White.copy(alpha = 0.10f))
        }
    }

    private fun drawIsometricTree(
        scope: DrawScope,
        path: Path,
        cx: Float,
        cy: Float,
        hw: Float,
        hh: Float,
        nowMs: Long,
        tx: Int,
        ty: Int,
        darkForest: Boolean
    ) {
        val sway = sin(nowMs / 650.0 + tx * 0.8 + ty * 0.5).toFloat() * hw * 0.045f
        // Ground contact shadow
        scope.drawOval(
            color = Color.Black.copy(alpha = 0.34f),
            topLeft = Offset(cx - hw * 0.72f, cy - hh * 0.28f),
            size = Size(hw * 1.44f, hh * 0.82f)
        )
        // Bark trunk
        val trunkW = hw * 0.24f
        val trunkH = hh * 1.35f
        scope.drawRect(Color(0xFF5C3A21), Offset(cx - trunkW * 0.5f, cy - trunkH), Size(trunkW, trunkH + hh * 0.12f))
        scope.drawRect(Color(0xFF3F2614), Offset(cx, cy - trunkH), Size(trunkW * 0.5f, trunkH + hh * 0.12f))

        // 3 layered foliage tiers for lush 2.5D depth
        val cBase = if (darkForest) Color(0xFF193B22) else Color(0xFF235927)
        val cMid = if (darkForest) Color(0xFF235230) else Color(0xFF337835)
        val cTop = if (darkForest) Color(0xFF357344) else Color(0xFF52A347)

        drawFoliageTier(scope, path, cx + sway * 0.4f, cy - hh * 1.05f, hw * 0.88f, hh * 1.05f, cBase)
        drawFoliageTier(scope, path, cx + sway * 0.75f, cy - hh * 1.72f, hw * 0.72f, hh * 0.96f, cMid)
        drawFoliageTier(scope, path, cx + sway, cy - hh * 2.32f, hw * 0.50f, hh * 0.78f, cTop)
    }

    private fun drawFoliageTier(
        scope: DrawScope,
        path: Path,
        cx: Float,
        baseY: Float,
        radiusX: Float,
        height: Float,
        color: Color
    ) {
        path.reset()
        path.moveTo(cx, baseY - height)
        path.lineTo(cx + radiusX, baseY)
        path.lineTo(cx, baseY + height * 0.28f)
        path.lineTo(cx - radiusX, baseY)
        path.close()
        scope.drawPath(path, color = color)
        // Sunlit left slope highlight
        path.reset()
        path.moveTo(cx, baseY - height)
        path.lineTo(cx, baseY + height * 0.28f)
        path.lineTo(cx - radiusX, baseY)
        path.close()
        scope.drawPath(path, color = Color.White.copy(alpha = 0.09f))
    }

    private fun drawFlickeringWallTorch(
        scope: DrawScope,
        cx: Float,
        cy: Float,
        hw: Float,
        hh: Float,
        nowMs: Long,
        seed: Int
    ) {
        val flicker = ((sin(nowMs / 110.0 + seed) + sin(nowMs / 173.0 + seed * 1.7)) * 0.25 + 0.5).toFloat()
        val glowRadius = hw * (1.25f + flicker * 0.35f)
        // Warm flickering radial light pool
        scope.drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    TorchAmber.copy(alpha = 0.42f + flicker * 0.18f),
                    FireOrange.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = glowRadius
            ),
            radius = glowRadius,
            center = Offset(cx, cy)
        )
        // Iron sconce bracket & torch handle
        scope.drawLine(Color(0xFF2B2826), Offset(cx, cy + hh * 0.38f), Offset(cx, cy + hh * 0.04f), strokeWidth = 3.5f)
        // Animated flame
        val flameR = hw * (0.11f + flicker * 0.04f)
        scope.drawCircle(FireOrange, flameR * 1.3f, Offset(cx, cy))
        scope.drawCircle(TorchGold, flameR * 0.85f, Offset(cx, cy - flameR * 0.22f))
        scope.drawCircle(Color(0xFFFFF8D6), flameR * 0.42f, Offset(cx, cy - flameR * 0.30f))
    }

    private fun isDoorwayTile(floorMap: WorldFloor, tx: Int, ty: Int): Boolean {
        val wL = floorMap.tileAt(tx - 1, ty).blocksSight
        val wR = floorMap.tileAt(tx + 1, ty).blocksSight
        val wU = floorMap.tileAt(tx, ty - 1).blocksSight
        val wD = floorMap.tileAt(tx, ty + 1).blocksSight
        return (wL && wR && !wU && !wD) || (wU && wD && !wL && !wR)
    }

    private fun drawOpenDoorwayThreshold(scope: DrawScope, cx: Float, cy: Float, hw: Float, hh: Float) {
        // Warm timber doorposts framing the open doorway without blocking view of entering players
        val postColor = Color(0xFF6B4729)
        val capColor = Color(0xFF9C7046)
        scope.drawRect(postColor, Offset(cx - hw * 0.48f, cy - hh * 1.15f), Size(hw * 0.13f, hh * 1.15f))
        scope.drawRect(postColor, Offset(cx + hw * 0.35f, cy - hh * 1.15f), Size(hw * 0.13f, hh * 1.15f))
        scope.drawLine(capColor, Offset(cx - hw * 0.50f, cy - hh * 1.16f), Offset(cx + hw * 0.50f, cy - hh * 1.16f), strokeWidth = 3.5f)
    }

    private fun drawPastureRail(scope: DrawScope, cx: Float, cy: Float, hw: Float, hh: Float, northEdge: Boolean) {
        val yShift = if (northEdge) -hh * 0.45f else hh * 0.35f
        val railCol = Color(0xFF7D5836)
        val postCol = Color(0xFF593B22)
        scope.drawLine(railCol, Offset(cx - hw * 0.45f, cy + yShift - hh * 0.22f), Offset(cx + hw * 0.45f, cy + yShift - hh * 0.22f), strokeWidth = 2.4f)
        scope.drawLine(railCol, Offset(cx - hw * 0.45f, cy + yShift - hh * 0.06f), Offset(cx + hw * 0.45f, cy + yShift - hh * 0.06f), strokeWidth = 2.2f)
        scope.drawRect(postCol, Offset(cx - hw * 0.38f, cy + yShift - hh * 0.36f), Size(3.5f, hh * 0.40f))
        scope.drawRect(postCol, Offset(cx + hw * 0.34f, cy + yShift - hh * 0.36f), Size(3.5f, hh * 0.40f))
    }

    private fun drawMerchantCartAccent(scope: DrawScope, cx: Float, cy: Float, w: Float, h: Float) {
        // Compact wooden merchant cart resting beside the shop/depot wall
        scope.drawOval(Color.Black.copy(alpha = 0.28f), Offset(cx - w * 0.6f, cy - h * 0.15f), Size(w * 1.2f, h * 0.65f))
        scope.drawRect(Color(0xFF7C512B), Offset(cx - w * 0.5f, cy - h * 0.65f), Size(w, h * 0.55f))
        scope.drawRect(Color(0xFFA87643), Offset(cx - w * 0.5f, cy - h * 0.72f), Size(w, h * 0.16f))
        // Spoked wooden wheel
        scope.drawCircle(Color(0xFF3E2614), h * 0.30f, Offset(cx - w * 0.22f, cy + h * 0.02f), style = Stroke(2.8f))
        scope.drawCircle(Color(0xFF3E2614), h * 0.30f, Offset(cx + w * 0.26f, cy + h * 0.02f), style = Stroke(2.8f))
    }

    private fun tileHash(tx: Int, ty: Int): Int {
        var h = tx * 374761393 + ty * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7FFFFFFF
    }

    private fun adjustBrightness(color: Color, delta: Float): Color = Color(
        red = (color.red + delta).coerceIn(0f, 1f),
        green = (color.green + delta).coerceIn(0f, 1f),
        blue = (color.blue + delta).coerceIn(0f, 1f),
        alpha = color.alpha
    )
}
