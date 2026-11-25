package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.game.model.TileType
import com.example.game.model.WORLD_OVERLAND_HEIGHT
import com.example.game.model.WORLD_OVERLAND_WIDTH
import com.example.game.world.BiomeIdeology
import com.example.game.world.WorldBiomeBlueprint
import com.example.game.world.WorldFloor
import com.example.game.world.WorldMaps
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Floating cartographic watermark title displayed at overview zoom levels on the World Map
 * once rectangular region grid boxes are removed.
 */
data class BiomeWatermarkLabel(
    val title: String,
    val x: Float,
    val y: Float,
    val isWater: Boolean = false
)

/**
 * Unified continuous terrain sampler shared by both the top-left 360-degree rotating minimap
 * and the expandable World Map (overview + zoomed-in detail).
 *
 * Uses pure Kotlin ARGB math (zero Android framework dependency inside sampling functions) so
 * continuous biome blending, coastal gradients, organic roads, and seam-free borders remain
 * deterministic, fast, and unit-testable on the JVM.
 */
object WorldMapTerrainSampler {

    val overlandBiomeWatermarks: List<BiomeWatermarkLabel> = listOf(
        BiomeWatermarkLabel("ASHEN CALDERA", 300f, 170f),
        BiomeWatermarkLabel("BLIGHTWOOD REACH", 1030f, 185f),
        BiomeWatermarkLabel("FROSTPEAK HIGHLANDS", 1385f, 175f),
        BiomeWatermarkLabel("WHISPERING MARSH", 275f, 475f),
        BiomeWatermarkLabel("SPIREWOOD REACH", 1070f, 520f),
        BiomeWatermarkLabel("ROOKVALE HEARTLAND", 1388f, 678f),
        BiomeWatermarkLabel("SUNSCORCH BADLANDS", 315f, 845f),
        BiomeWatermarkLabel("EMERALD JUNGLE", 875f, 1025f),
        BiomeWatermarkLabel("EMBER ISLES", 1485f, 995f),
        BiomeWatermarkLabel("CENTRAL ALDER SEA", 760f, 445f, isWater = true)
    )

    // --- Rich, Harmonized Cartographic Biome Ground Palettes (ARGB) ---
    private const val COL_DEEP_OCEAN = 0xFF0E2A48.toInt()
    private const val COL_MID_OCEAN = 0xFF17456E.toInt()
    private const val COL_SHALLOW_SEA = 0xFF287594.toInt()
    private const val COL_SURF_FOAM = 0xFF7CB6B8.toInt()

    private const val COL_BEACH_WARM = 0xFFCDB078.toInt()
    private const val COL_BEACH_MARSH = 0xFF6B7757.toInt()
    private const val COL_BEACH_FROST = 0xFFAAB8C4.toInt()
    private const val COL_BEACH_ASHEN = 0xFF625A56.toInt()

    private const val COL_BIOME_HEARTLAND = 0xFF527E42.toInt()
    private const val COL_BIOME_MARSH = 0xFF425C3E.toInt()
    private const val COL_BIOME_DESERT = 0xFFCFA868.toInt()
    private const val COL_BIOME_BADLANDS = 0xFF6D5242.toInt()
    private const val COL_BIOME_FROST_SNOW = 0xFFDCE6F0.toInt()
    private const val COL_BIOME_FROST_TAIGA = 0xFF5E7A65.toInt()
    private const val COL_BIOME_BLIGHT = 0xFF4F3B5E.toInt()
    private const val COL_BIOME_JUNGLE = 0xFF2E6B38.toInt()
    private const val COL_BIOME_ASHEN = 0xFF504542.toInt()

    private const val COL_ROAD_COBBLE = 0xFFA89C89.toInt()
    private const val COL_ROAD_DIRT = 0xFF937758.toInt()
    private const val COL_BRIDGE_STONE = 0xFFB8AEA0.toInt()
    private const val COL_WOOD_PIER = 0xFFA97E50.toInt()
    private const val COL_FOREST_CANOPY_BASE = 0xFF254B27.toInt()
    private const val COL_ROCK_SLOPE = 0xFF6F747C.toInt()
    private const val COL_INLAND_WATER = 0xFF256D9E.toInt()
    private const val COL_INLAND_SHORE = 0xFF4E8C9E.toInt()
    private const val COL_LAVA_CORE = 0xFFE8561F.toInt()
    private const val COL_LAVA_GLOW = 0xFFFF9838.toInt()

    /**
     * Pure continuous world-space terrain & coastline color at floating-point world coordinates `(fx, fy)`.
     * Produces smooth noise-warped biome blends, multi-stop ocean-to-shore gradients, NW hillshading,
     * and subtle topographic elevation contours with zero rectangular sector seams.
     */
    fun sampleOverlandBaseArgb(fx: Float, fy: Float): Int {
        val landVal = WorldBiomeBlueprint.continuousLandmassField(fx, fy)

        // Domain-warped biome coordinates (±28 tiles) so biome transitions curve naturally
        val warpX = sin(fy * 0.017f + fx * 0.007f) * 20f + cos(fy * 0.039f - fx * 0.021f) * 8f
        val warpY = cos(fx * 0.016f - fy * 0.008f) * 20f + sin(fx * 0.035f + fy * 0.019f) * 8f
        val bx = fx + warpX
        val by = fy + warpY

        val beachPalette = sampleBiomeShoreColor(bx, by)
        val wetShoreCol = lerpArgb(lerpArgb(COL_SHALLOW_SEA, COL_SURF_FOAM, 0.35f), beachPalette, 0.44f)

        // 1. Open ocean & smooth coastal bathymetry gradient (meets land seamlessly at landVal = 0.0f)
        if (landVal < 0.0f) {
            val waterCol = when {
                landVal < -0.34f -> {
                    val t = ((landVal + 0.72f) / 0.38f).coerceIn(0f, 1f)
                    lerpArgb(0xFF0A2038.toInt(), COL_DEEP_OCEAN, t)
                }
                landVal < -0.14f -> {
                    val t = smoothstep(-0.34f, -0.14f, landVal)
                    lerpArgb(COL_DEEP_OCEAN, COL_MID_OCEAN, t)
                }
                landVal < -0.068f -> {
                    val t = smoothstep(-0.14f, -0.068f, landVal)
                    lerpArgb(COL_MID_OCEAN, COL_SHALLOW_SEA, t)
                }
                else -> {
                    val t = smoothstep(-0.068f, 0.0f, landVal)
                    lerpArgb(COL_SHALLOW_SEA, wetShoreCol, t)
                }
            }
            // Subtle bathymetric depth contour rings & gentle sea sheen (fades out cleanly at shoreline)
            val wave = (sin(fx * 0.045f + fy * 0.025f) * 0.015f + cos(fx * 0.08f - fy * 0.06f) * 0.009f) *
                smoothstep(0.0f, -0.04f, landVal)
            val bathy = abs(landVal) * 14f
            val bathyDist = minOf(bathy - floor(bathy), 1f - (bathy - floor(bathy)))
            val contourBoost = (1f - smoothstep(0.0f, 0.08f, bathyDist)) * 0.048f * smoothstep(-0.02f, -0.08f, landVal)
            return adjustBrightness(waterCol, 1f + wave + contourBoost)
        }

        // 2. Smooth multi-biome ground palette weights (zero hard box cutoffs)
        val groundCol = sampleBlendedBiomeGround(bx, by, landVal)

        // 3. Soft beach / shoreline transition from water's edge (0.000 .. 0.115) into adjacent biome ground
        val shoreCol = if (landVal < 0.115f) {
            val wetToBeach = smoothstep(0.0f, 0.062f, landVal)
            val beachToGround = smoothstep(0.030f, 0.115f, landVal)
            val dryBeach = lerpArgb(wetShoreCol, beachPalette, wetToBeach)
            lerpArgb(dryBeach, groundCol, beachToGround)
        } else {
            groundCol
        }

        // 4. Continuous macro relief, NW directional hillshading, and gentle topographic contours
        val landFade = smoothstep(0.0f, 0.045f, landVal)
        val relief = sampleRelief(fx, fy, landVal)
        val reliefDx = sampleRelief(fx + 3.0f, fy, landVal) - relief
        val reliefDy = sampleRelief(fx, fy + 3.0f, landVal) - relief
        val hillshade = ((-reliefDx - reliefDy) * 1.45f).coerceIn(-0.11f, 0.11f) * landFade

        // Smooth topographic contour bands at regular elevation intervals (fades in smoothly inland)
        val contourLevel = (landVal * 11.5f + relief * 2.2f)
        val contourFrac = contourLevel - floor(contourLevel)
        val contourDist = minOf(contourFrac, 1f - contourFrac)
        val contourDarken = -(1f - smoothstep(0.0f, 0.07f, contourDist)) * 0.052f * smoothstep(0.055f, 0.11f, landVal)

        // Subtle organic fine soil grain so large expanses feel painted rather than flat
        val microGrain = (sin(fx * 0.21f + fy * 0.17f) * cos(fx * 0.13f - fy * 0.23f)) * 0.030f * landFade

        return adjustBrightness(shoreCol, (1f + hillshade + contourDarken + microGrain).coerceIn(0.76f, 1.22f))
    }

    /**
     * Unified live-tile blended sampler used by BOTH the rotating top-left minimap and
     * the zoomed-in World Map window.
     *
     * Ground tiles modulate [sampleOverlandBaseArgb] gently instead of overwriting it with hard
     * per-tile flat squares, while roads, rivers, trees, rocks, settlements, piers, and landmarks
     * blend cleanly with soft organic edges.
     */
    fun sampleOverlandTileBlendedArgb(fx: Float, fy: Float, floorMap: WorldFloor?): Int {
        val baseCol = sampleOverlandBaseArgb(fx, fy)
        if (floorMap == null) return baseCol

        val maxX = (floorMap.width - 1).coerceAtLeast(0)
        val maxY = (floorMap.height - 1).coerceAtLeast(0)
        val cx = floor(fx).toInt().coerceIn(0, maxX)
        val cy = floor(fy).toInt().coerceIn(0, maxY)
        val centerTile = floorMap.tileAt(cx, cy)

        // High-priority crisp architectural / interactive features (always sharp and readable)
        when (centerTile) {
            TileType.LAVA -> {
                val glow = 0.5f + 0.5f * sin(fx * 0.55f + fy * 0.45f)
                return lerpArgb(COL_LAVA_CORE, COL_LAVA_GLOW, glow * 0.45f)
            }
            TileType.STAIRS_DOWN, TileType.STAIRS_UP, TileType.ROPE_HOLE_DOWN, TileType.ROPE_SPOT_UP ->
                return 0xFFF2C94C.toInt()
            TileType.BIOME_STALL_HEARTLAND -> return 0xFFFF8C2B.toInt()
            TileType.BIOME_STALL_MARSH -> return 0xFF5CE88A.toInt()
            TileType.BIOME_STALL_BADLANDS -> return 0xFFB48CFF.toInt()
            TileType.BIOME_STALL_FROSTPEAK -> return 0xFF68E8FF.toInt()
            TileType.BIOME_STALL_ASHEN -> return 0xFFFF5E24.toInt()
            TileType.SMELTING_FURNACE, TileType.BLACKSMITH_ANVIL -> return 0xFFFF7A2E.toInt()
            TileType.FLETCHING_BENCH, TileType.WEAPON_RACK -> return 0xFFD4A054.toInt()
            TileType.RUNECRAFTING_TABLE, TileType.CRYSTAL_PILLAR -> return 0xFFA86BFF.toInt()
            TileType.DEPOT_CHEST, TileType.MINI_DEPOT, TileType.SHOP_COUNTER, TileType.TEMPLE_ALTAR ->
                return 0xFF5CE1FF.toInt()
            TileType.FOUNTAIN -> return 0xFF56C9F7.toInt()
            TileType.LANTERN_POST, TileType.CAMPFIRE_BRAZIER -> return 0xFFFFD86B.toInt()
            TileType.MARKET_AWNING, TileType.WAR_BANNER -> return 0xFFC94A3E.toInt()
            TileType.MOORED_BOAT -> return 0xFF966438.toInt()
            TileType.ORE_COPPER -> return 0xFFE08234.toInt()
            TileType.ORE_TIN -> return 0xFFA9B9C8.toInt()
            TileType.ORE_BOG_IRON -> return 0xFF7D90A3.toInt()
            TileType.ORE_MIRE_SILVER -> return 0xFF5EE89E.toInt()
            TileType.ORE_PEAT_COAL -> return 0xFF4E5660.toInt()
            TileType.ORE_VESPERITE -> return 0xFFB599FF.toInt()
            TileType.ORE_GLACIER_QUARTZ -> return 0xFF68E8FF.toInt()
            TileType.ORE_FROST_IRON -> return 0xFF7CB4E8.toInt()
            TileType.ORE_CINDERITE -> return 0xFFFFB22E.toInt()
            TileType.CITY_WALL, TileType.STONE_WALL, TileType.GATE_PILLAR -> return 0xFF6A6F78.toInt()
            TileType.TIMBER_WALL, TileType.TIMBER_WINDOW_WALL -> return 0xFFC4B39A.toInt()
            TileType.TEMPLE_WALL -> return 0xFFDDD2BE.toInt()
            TileType.SANDSTONE_WALL -> return 0xFFD1B37D.toInt()
            TileType.GRANITE_WALL -> return 0xFF8C97A4.toInt()
            TileType.OBSIDIAN_WALL -> return 0xFF3E3548.toInt()
            TileType.IRON_BAR_GATE -> return 0xFF788492.toInt()
            TileType.TEMPLE_FLOOR -> return lerpArgb(baseCol, 0xFFD9CFBA.toInt(), 0.88f)
            TileType.WOOD_PLANK_FLOOR -> return lerpArgb(baseCol, 0xFF936A45.toInt(), 0.86f)
            TileType.ARCANE_FLOOR -> return lerpArgb(baseCol, 0xFF56477F.toInt(), 0.86f)
            TileType.OBSIDIAN_FLOOR -> return lerpArgb(baseCol, 0xFF42374C.toInt(), 0.86f)
            TileType.WOOD_FENCE -> return lerpArgb(baseCol, 0xFF7B5736.toInt(), 0.78f)
            else -> Unit
        }

        // Organic 2x2 bilinear neighborhood sampling for smooth curved roads, rivers, canopies & ridges
        val wobbleX = sin(fx * 0.42f + fy * 0.37f) * 0.16f
        val wobbleY = cos(fx * 0.37f - fy * 0.43f) * 0.16f
        val sx = (fx + wobbleX - 0.5f).coerceIn(0f, maxX.toFloat())
        val sy = (fy + wobbleY - 0.5f).coerceIn(0f, maxY.toFloat())
        val x0 = floor(sx).toInt().coerceIn(0, maxX)
        val y0 = floor(sy).toInt().coerceIn(0, maxY)
        val x1 = (x0 + 1).coerceAtMost(maxX)
        val y1 = (y0 + 1).coerceAtMost(maxY)
        val tx = (sx - x0).coerceIn(0f, 1f)
        val ty = (sy - y0).coerceIn(0f, 1f)

        val w00 = (1f - tx) * (1f - ty)
        val w10 = tx * (1f - ty)
        val w01 = (1f - tx) * ty
        val w11 = tx * ty

        val t00 = floorMap.tileAt(x0, y0)
        val t10 = floorMap.tileAt(x1, y0)
        val t01 = floorMap.tileAt(x0, y1)
        val t11 = floorMap.tileAt(x1, y1)

        var col = baseCol

        // 1. Subtle ground-tile micro-texture modulation (16% max — never introduces biome seams!)
        val groundTint = groundTileSubtleTint(centerTile)
        if (groundTint != 0) {
            col = lerpArgb(col, groundTint, 0.16f)
        }

        // 2. Smooth inland water (rivers & marsh pools) when landmass is positive
        val landVal = WorldBiomeBlueprint.continuousLandmassField(fx, fy)
        if (landVal > 0.008f) {
            val waterCov = wIf(t00, w00) { it == TileType.WATER || it == TileType.DEEP_WATER } +
                wIf(t10, w10) { it == TileType.WATER || it == TileType.DEEP_WATER } +
                wIf(t01, w01) { it == TileType.WATER || it == TileType.DEEP_WATER } +
                wIf(t11, w11) { it == TileType.WATER || it == TileType.DEEP_WATER }
            if (waterCov > 0.06f) {
                val bankT = smoothstep(0.06f, 0.42f, waterCov)
                val coreT = smoothstep(0.36f, 0.78f, waterCov)
                col = lerpArgb(col, COL_INLAND_SHORE, bankT * 0.65f)
                col = lerpArgb(col, COL_INLAND_WATER, coreT * 0.92f)
            }
        }

        // 3. Soft organic roads, cobblestone highways, dirt trails, stone bridges & wooden piers
        val bridgeCov = wIf(t00, w00) { it == TileType.STONE_BRIDGE } +
            wIf(t10, w10) { it == TileType.STONE_BRIDGE } +
            wIf(t01, w01) { it == TileType.STONE_BRIDGE } +
            wIf(t11, w11) { it == TileType.STONE_BRIDGE }
        val pierCov = wIf(t00, w00) { it == TileType.WOOD_PIER || it == TileType.WOOD_BRIDGE } +
            wIf(t10, w10) { it == TileType.WOOD_PIER || it == TileType.WOOD_BRIDGE } +
            wIf(t01, w01) { it == TileType.WOOD_PIER || it == TileType.WOOD_BRIDGE } +
            wIf(t11, w11) { it == TileType.WOOD_PIER || it == TileType.WOOD_BRIDGE }
        val cobbleCov = wIf(t00, w00) { it == TileType.COBBLESTONE } +
            wIf(t10, w10) { it == TileType.COBBLESTONE } +
            wIf(t01, w01) { it == TileType.COBBLESTONE } +
            wIf(t11, w11) { it == TileType.COBBLESTONE }
        val dirtCov = wIf(t00, w00) { it == TileType.DIRT_FLOOR } +
            wIf(t10, w10) { it == TileType.DIRT_FLOOR } +
            wIf(t01, w01) { it == TileType.DIRT_FLOOR } +
            wIf(t11, w11) { it == TileType.DIRT_FLOOR }

        if (dirtCov > 0.10f) {
            val t = smoothstep(0.10f, 0.72f, dirtCov) * 0.74f
            col = lerpArgb(col, COL_ROAD_DIRT, t)
        }
        if (cobbleCov > 0.10f) {
            val t = smoothstep(0.10f, 0.68f, cobbleCov) * 0.84f
            col = lerpArgb(col, COL_ROAD_COBBLE, t)
        }
        if (bridgeCov > 0.12f) {
            val t = smoothstep(0.12f, 0.62f, bridgeCov) * 0.94f
            col = lerpArgb(col, COL_BRIDGE_STONE, t)
        }
        if (pierCov > 0.12f) {
            val t = smoothstep(0.12f, 0.62f, pierCov) * 0.92f
            col = lerpArgb(col, COL_WOOD_PIER, t)
        }

        // 4. Biome-tinted forest canopy & hedges (dappled soft foliage over continuous ground)
        val treeCov = wIf(t00, w00) { it == TileType.TREE || it == TileType.HEDGE } +
            wIf(t10, w10) { it == TileType.TREE || it == TileType.HEDGE } +
            wIf(t01, w01) { it == TileType.TREE || it == TileType.HEDGE } +
            wIf(t11, w11) { it == TileType.TREE || it == TileType.HEDGE }
        if (treeCov > 0.14f) {
            val canopy = lerpArgb(COL_FOREST_CANOPY_BASE, baseCol, 0.28f)
            val shade = 0.86f + 0.14f * sin(fx * 0.9f - fy * 0.7f)
            val t = smoothstep(0.14f, 0.74f, treeCov) * 0.72f
            col = lerpArgb(col, adjustBrightness(canopy, shade), t)
        }

        // 5. Shaded highland / mountain rock formations
        val rockCov = wIf(t00, w00) { it == TileType.MOUNTAIN_ROCK } +
            wIf(t10, w10) { it == TileType.MOUNTAIN_ROCK } +
            wIf(t01, w01) { it == TileType.MOUNTAIN_ROCK } +
            wIf(t11, w11) { it == TileType.MOUNTAIN_ROCK }
        if (rockCov > 0.14f) {
            val rockTint = lerpArgb(COL_ROCK_SLOPE, baseCol, 0.20f)
            val reliefShade = 0.88f + 0.18f * cos(fx * 0.65f + fy * 0.55f)
            val t = smoothstep(0.14f, 0.70f, rockCov) * 0.78f
            col = lerpArgb(col, adjustBrightness(rockTint, reliefShade), t)
        }

        return col
    }

    /**
     * Chunk-free full-continent overview pixel sampler (`640 x 480` cached World Map overview).
     * Never allocates or evicts streaming chunks in [ChunkedTileGrid], while faithfully showing
     * the exact same blended biomes, coastlines, Rookvale City layout, sea bridges, highways,
     * settlements, volcanic craters, and organic forest/ridge shading as zoomed-in views.
     */
    fun sampleOverlandOverviewArgb(fx: Float, fy: Float): Int {
        var col = sampleOverlandBaseArgb(fx, fy)
        val ix = fx.toInt().coerceIn(0, WORLD_OVERLAND_WIDTH - 1)
        val iy = fy.toInt().coerceIn(0, WORLD_OVERLAND_HEIGHT - 1)

        // 1. Inside Rookvale Heartland, overlay actual city walls, plazas, roads & river directly from pre-built grid
        val heartlandTile = WorldMaps.fastHeartlandTileOrNull(ix, iy)
        if (heartlandTile != null) {
            when (heartlandTile) {
                TileType.COBBLESTONE -> col = lerpArgb(col, COL_ROAD_COBBLE, 0.72f)
                TileType.DIRT_FLOOR -> col = lerpArgb(col, COL_ROAD_DIRT, 0.55f)
                TileType.TEMPLE_FLOOR -> col = lerpArgb(col, 0xFFD9CFBA.toInt(), 0.82f)
                TileType.WOOD_PLANK_FLOOR, TileType.WOOD_PIER, TileType.WOOD_BRIDGE ->
                    col = lerpArgb(col, COL_WOOD_PIER, 0.82f)
                TileType.STONE_BRIDGE -> col = lerpArgb(col, COL_BRIDGE_STONE, 0.88f)
                TileType.CITY_WALL, TileType.STONE_WALL, TileType.TIMBER_WALL, TileType.TIMBER_WINDOW_WALL, TileType.TEMPLE_WALL ->
                    col = lerpArgb(col, 0xFF7B766E.toInt(), 0.76f)
                TileType.WATER -> {
                    val landVal = WorldBiomeBlueprint.continuousLandmassField(fx, fy)
                    if (landVal > 0.02f) {
                        col = lerpArgb(col, COL_INLAND_WATER, 0.82f)
                    }
                }
                TileType.TREE -> {
                    val canopy = lerpArgb(COL_FOREST_CANOPY_BASE, col, 0.35f)
                    col = lerpArgb(col, canopy, 0.42f)
                }
                TileType.MOUNTAIN_ROCK -> {
                    col = lerpArgb(col, COL_ROCK_SLOPE, 0.52f)
                }
                else -> Unit
            }
            return col
        }

        // 2. Volcanic lava calderas (NW Ashen Caldera & SE Ember Isles peak)
        val calderaDist = hypot((fx - 328f).toDouble(), (fy - 185f).toDouble()).toFloat()
        if (calderaDist < 26f) {
            val lavaT = 1f - smoothstep(14f, 26f, calderaDist)
            col = lerpArgb(col, COL_LAVA_CORE, lavaT * 0.92f)
        }
        val emberPeakDist = hypot((fx - 1518f).toDouble(), (fy - 952f).toDouble()).toFloat()
        if (emberPeakDist < 16f) {
            val lavaT = 1f - smoothstep(8f, 16f, emberPeakDist)
            col = lerpArgb(col, COL_LAVA_CORE, lavaT * 0.90f)
        }

        // 3. Organic macro forest & mountain shading across outer continents (chunk-free!)
        val landVal = WorldBiomeBlueprint.continuousLandmassField(fx, fy)
        if (landVal > 0.14f) {
            val groveNoise = sin(fx * 0.055f + fy * 0.043f) * cos(fx * 0.037f - fy * 0.061f)
            if (groveNoise > 0.28f) {
                val forestWeight = ((groveNoise - 0.28f) / 0.72f).coerceIn(0f, 1f) * 0.36f
                val canopy = lerpArgb(COL_FOREST_CANOPY_BASE, col, 0.38f)
                col = lerpArgb(col, canopy, forestWeight)
            }
            if (landVal > 0.34f && groveNoise < -0.34f) {
                val ridgeWeight = ((-groveNoise - 0.34f) / 0.66f).coerceIn(0f, 1f) * 0.34f
                col = lerpArgb(col, COL_ROCK_SLOPE, ridgeWeight)
            }
        }

        // 4. Inter-continental stone sea-bridges & organic overland highway network
        if (WorldBiomeBlueprint.isOnSeaBridge(fx, fy)) {
            return lerpArgb(col, COL_BRIDGE_STONE, 0.92f)
        }
        val roadCov = WorldBiomeBlueprint.continuousRoadCoverage(fx, fy)
        if (roadCov > 0.04f) {
            col = lerpArgb(col, COL_ROAD_COBBLE, roadCov * 0.72f)
        }

        // 5. Coastline-validated harbor piers
        if (WorldBiomeBlueprint.validatedPortTileAt(ix, iy) != null) {
            col = lerpArgb(col, COL_WOOD_PIER, 0.90f)
        }

        // 6. Secondary Empires, Towns & Forward Camps subtle plaza footprint
        val settlements = WorldBiomeBlueprint.secondarySettlements
        for (i in 0 until settlements.size) {
            val s = settlements[i]
            if (ix in s.minX..s.maxX && iy in s.minY..s.maxY) {
                val plazaCol = when (s.ideology) {
                    BiomeIdeology.HEARTLAND -> 0xFFD0C4AC.toInt()
                    BiomeIdeology.MARSHLAND -> 0xFF966E49.toInt()
                    BiomeIdeology.BADLANDS -> 0xFFDDC189.toInt()
                    BiomeIdeology.FROSTPEAK -> 0xFF9FAAB6.toInt()
                    BiomeIdeology.ASHEN_BLIGHT -> 0xFF5A485E.toInt()
                }
                col = lerpArgb(col, plazaCol, 0.78f)
                break
            }
        }

        return col
    }

    /**
     * Smooth high-contrast subterranean renderer for Dungeon Floors `-1..-6`.
     */
    fun sampleDungeonPixelArgb(fx: Float, fy: Float, floorMap: WorldFloor): Int {
        val maxX = (floorMap.width - 1).coerceAtLeast(0)
        val maxY = (floorMap.height - 1).coerceAtLeast(0)
        val ix = floor(fx).toInt().coerceIn(0, maxX)
        val iy = floor(fy).toInt().coerceIn(0, maxY)
        val tile = floorMap.tileAt(ix, iy)

        val grain = (sin(fx * 0.85f + fy * 0.73f) * cos(fx * 0.51f - fy * 0.91f)) * 0.055f

        val base = when (tile) {
            TileType.CAVE_WALL, TileType.STONE_WALL, TileType.MOUNTAIN_ROCK, TileType.OBSIDIAN_WALL -> {
                // Bevel highlight on walls adjacent to walkable floors so corridors pop cleanly
                val nearFloor = floorMap.tileAt((ix - 1).coerceAtLeast(0), iy).walkable ||
                    floorMap.tileAt((ix + 1).coerceAtMost(maxX), iy).walkable ||
                    floorMap.tileAt(ix, (iy - 1).coerceAtLeast(0)).walkable ||
                    floorMap.tileAt(ix, (iy + 1).coerceAtMost(maxY)).walkable
                if (nearFloor) 0xFF424854.toInt() else 0xFF1A1E26.toInt()
            }
            TileType.TORCH_WALL -> 0xFFB8783A.toInt()
            TileType.DIRT_FLOOR -> 0xFF6B5540.toInt()
            TileType.CRYPT_FLOOR -> 0xFF575D69.toInt()
            TileType.OBSIDIAN_FLOOR -> 0xFF463B52.toInt()
            TileType.COBBLESTONE -> 0xFF8C8478.toInt()
            TileType.TEMPLE_FLOOR -> 0xFFD2C7AE.toInt()
            TileType.WOOD_PLANK_FLOOR, TileType.WOOD_BRIDGE -> 0xFF8C6541.toInt()
            TileType.ARCANE_FLOOR -> 0xFF564785.toInt()
            TileType.WATER, TileType.DEEP_WATER -> 0xFF216296.toInt()
            TileType.LAVA -> 0xFFE6531E.toInt()
            TileType.STAIRS_DOWN, TileType.STAIRS_UP, TileType.ROPE_HOLE_DOWN, TileType.ROPE_SPOT_UP ->
                0xFFF5CA4E.toInt()
            TileType.CAMPFIRE_BRAZIER, TileType.LANTERN_POST -> 0xFFFFD86B.toInt()
            TileType.DEPOT_CHEST, TileType.MINI_DEPOT, TileType.SHOP_COUNTER, TileType.TEMPLE_ALTAR ->
                0xFF5CE1FF.toInt()
            TileType.ORE_COPPER -> 0xFFE08234.toInt()
            TileType.ORE_TIN -> 0xFFA9B9C8.toInt()
            TileType.ORE_BOG_IRON -> 0xFF7D90A3.toInt()
            TileType.ORE_MIRE_SILVER -> 0xFF5EE89E.toInt()
            TileType.ORE_PEAT_COAL -> 0xFF4E5660.toInt()
            TileType.ORE_VESPERITE -> 0xFFB599FF.toInt()
            TileType.ORE_GLACIER_QUARTZ -> 0xFF68E8FF.toInt()
            TileType.ORE_FROST_IRON -> 0xFF7CB4E8.toInt()
            TileType.ORE_CINDERITE -> 0xFFFFB22E.toInt()
            else -> if (tile.walkable) 0xFF64666E.toInt() else 0xFF2D323B.toInt()
        }

        // Warm sanctuary tint inside underground safe-zone camps
        val safeTint = if (tile.walkable && floorMap.isProtectionZone(ix, iy)) {
            lerpArgb(base, 0xFFB89E68.toInt(), 0.24f)
        } else {
            base
        }

        return adjustBrightness(safeTint, (1f + grain).coerceIn(0.88f, 1.12f))
    }

    /**
     * Smoothly blends all 7 overland biome ground palettes using soft `smoothstep` spatial weights
     * in warped world coordinates so neighboring biomes crossfade over 90–140 tiles with zero seams.
     */
    private fun sampleBlendedBiomeGround(bx: Float, by: Float, landVal: Float): Int {
        var rAcc = 0f
        var gAcc = 0f
        var bAcc = 0f
        var wAcc = 0f

        fun accumulate(col: Int, weight: Float) {
            if (weight <= 0.0005f) return
            rAcc += ((col ushr 16) and 0xFF) * weight
            gAcc += ((col ushr 8) and 0xFF) * weight
            bAcc += (col and 0xFF) * weight
            wAcc += weight
        }

        // Base Temperate Heartland Meadow weight everywhere (ensures graceful fallback everywhere)
        accumulate(COL_BIOME_HEARTLAND, 0.35f)

        // 1. Far Northern Arctic Snow & Frostpeak Snowy Highlands
        val arcticNorth = 1f - smoothstep(55f, 135f, by)
        val frostpeakEast = smoothstep(1065f, 1215f, bx) * (1f - smoothstep(230f, 385f, by))
        val frostTaigaBand = smoothstep(1065f, 1215f, bx) * smoothstep(225f, 315f, by) * (1f - smoothstep(355f, 465f, by))
        val southAlpine = smoothstep(1085f, 1145f, by)
        accumulate(COL_BIOME_FROST_SNOW, (arcticNorth * 2.8f + frostpeakEast * 3.0f + southAlpine * 2.5f))
        accumulate(COL_BIOME_FROST_TAIGA, frostTaigaBand * 2.1f)

        // 2. NW Ashen Caldera Volcano
        val nwAshen = (1f - smoothstep(400f, 520f, bx)) * (1f - smoothstep(290f, 415f, by))
        accumulate(COL_BIOME_ASHEN, nwAshen * 3.2f)

        // 3. The Writhing Marshes & Mirewatch Wetlands
        val marshW = (1f - smoothstep(420f, 595f, bx)) *
            smoothstep(295f, 415f, by) *
            (1f - smoothstep(615f, 725f, by))
        accumulate(COL_BIOME_MARSH, marshW * 3.0f)

        // 4. Sunspire Golden Desert & SW Volcanic Badlands
        val desertW = (1f - smoothstep(470f, 585f, bx)) *
            smoothstep(625f, 735f, by) *
            (1f - smoothstep(910f, 1005f, by))
        val swBadlandsW = (1f - smoothstep(340f, 470f, bx)) * smoothstep(885f, 995f, by)
        accumulate(COL_BIOME_DESERT, desertW * 3.4f)
        accumulate(COL_BIOME_BADLANDS, swBadlandsW * 2.4f)
        accumulate(COL_BIOME_ASHEN, swBadlandsW * 1.4f)

        // 5. Blightwood Corrupted Forest (North-Central East Continent)
        val blightW = smoothstep(855f, 965f, bx) *
            (1f - smoothstep(1100f, 1210f, bx)) *
            (1f - smoothstep(245f, 360f, by))
        accumulate(COL_BIOME_BLIGHT, blightW * 3.1f)

        // 6. Southern Emerald Jungle Continent
        val jungleW = smoothstep(500f, 610f, bx) *
            (1f - smoothstep(1160f, 1295f, bx)) *
            smoothstep(825f, 935f, by)
        accumulate(COL_BIOME_JUNGLE, jungleW * 3.3f)

        // 7. Southeast Ember Isles Volcanic Archipelago
        val emberW = smoothstep(1290f, 1410f, bx) * smoothstep(800f, 910f, by)
        accumulate(COL_BIOME_ASHEN, emberW * 3.2f)

        // 8. Temperate Heartland & Spirewood Boost
        val heartlandW = smoothstep(920f, 1080f, bx) *
            smoothstep(340f, 455f, by) *
            (1f - smoothstep(740f, 865f, by))
        accumulate(COL_BIOME_HEARTLAND, heartlandW * 2.6f)

        val inv = if (wAcc > 0.0001f) 1f / wAcc else 1f
        val baseR = (rAcc * inv).toInt().coerceIn(0, 255)
        val baseG = (gAcc * inv).toInt().coerceIn(0, 255)
        val baseB = (bAcc * inv).toInt().coerceIn(0, 255)
        val blended = (0xFF shl 24) or (baseR shl 16) or (baseG shl 8) or baseB

        // High inland elevations gently lighten toward rocky alpine plateaus
        return if (landVal > 0.45f) {
            val highT = smoothstep(0.45f, 0.78f, landVal) * 0.24f
            lerpArgb(blended, 0xFF8C8E86.toInt(), highT)
        } else {
            blended
        }
    }

    private fun sampleBiomeShoreColor(bx: Float, by: Float): Int {
        val frost = (1f - smoothstep(65f, 160f, by)) +
            smoothstep(1090f, 1220f, bx) * (1f - smoothstep(235f, 360f, by))
        val marsh = (1f - smoothstep(430f, 580f, bx)) *
            smoothstep(320f, 430f, by) * (1f - smoothstep(620f, 710f, by))
        val ashen = (1f - smoothstep(410f, 515f, bx)) * (1f - smoothstep(300f, 400f, by)) +
            smoothstep(1300f, 1410f, bx) * smoothstep(820f, 920f, by) +
            (1f - smoothstep(350f, 450f, bx)) * smoothstep(900f, 995f, by)

        var shore = COL_BEACH_WARM
        if (frost > 0.05f) shore = lerpArgb(shore, COL_BEACH_FROST, frost.coerceIn(0f, 1f))
        if (marsh > 0.05f) shore = lerpArgb(shore, COL_BEACH_MARSH, marsh.coerceIn(0f, 1f))
        if (ashen > 0.05f) shore = lerpArgb(shore, COL_BEACH_ASHEN, ashen.coerceIn(0f, 1f))
        return shore
    }

    private fun sampleRelief(fx: Float, fy: Float, landVal: Float): Float {
        val n1 = sin(fx * 0.029f + fy * 0.023f) * 0.50f
        val n2 = cos(fx * 0.057f - fy * 0.049f) * 0.30f
        val n3 = sin(fx * 0.113f - fy * 0.097f) * 0.20f
        return (n1 + n2 + n3) * (0.28f + landVal.coerceIn(0f, 0.8f) * 0.72f)
    }

    private fun groundTileSubtleTint(tile: TileType): Int = when (tile) {
        TileType.GRASS -> 0xFF4F7D3E.toInt()
        TileType.FLOWER_GRASS -> 0xFF6B9B4C.toInt()
        TileType.DARK_GRASS -> 0xFF2D512C.toInt()
        TileType.SAND -> 0xFFD9B874.toInt()
        TileType.MARSH -> 0xFF3E5536.toInt()
        TileType.SNOW -> 0xFFE6EFF7.toInt()
        TileType.JUNGLE_FLOOR -> 0xFF26632E.toInt()
        TileType.VOLCANIC_ASH -> 0xFF4A413E.toInt()
        TileType.BLIGHT_SOIL -> 0xFF4C355D.toInt()
        else -> 0
    }

    private inline fun wIf(tile: TileType, weight: Float, predicate: (TileType) -> Boolean): Float =
        if (predicate(tile)) weight else 0f

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    internal fun lerpArgb(c0: Int, c1: Int, tRaw: Float): Int {
        val t = tRaw.coerceIn(0f, 1f)
        val r0 = (c0 ushr 16) and 0xFF
        val g0 = (c0 ushr 8) and 0xFF
        val b0 = c0 and 0xFF
        val r1 = (c1 ushr 16) and 0xFF
        val g1 = (c1 ushr 8) and 0xFF
        val b1 = c1 and 0xFF
        val r = (r0 + (r1 - r0) * t).toInt().coerceIn(0, 255)
        val g = (g0 + (g1 - g0) * t).toInt().coerceIn(0, 255)
        val b = (b0 + (b1 - b0) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    internal fun adjustBrightness(argb: Int, factor: Float): Int {
        val r = (((argb ushr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((argb ushr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Euclidean RGB channel distance in [0..441.67] for continuity / seam verification in tests.
     */
    fun colorDistance(c0: Int, c1: Int): Float {
        val dr = ((c0 ushr 16) and 0xFF) - ((c1 ushr 16) and 0xFF)
        val dg = ((c0 ushr 8) and 0xFF) - ((c1 ushr 8) and 0xFF)
        val db = (c0 and 0xFF) - (c1 and 0xFF)
        return hypot(hypot(dr.toDouble(), dg.toDouble()), db.toDouble()).toFloat()
    }
}

/**
 * Lazily generated and cached full-continent unified live-terrain overview bitmap (`640 x 480`)
 * for Floor 0 World Map low-zoom display. Matches zoomed-in live terrain seamlessly without
 * allocating or evicting any streaming chunks in [ChunkedTileGrid].
 */
object WorldMapOverviewCache {
    const val OVERVIEW_WIDTH = 640
    const val OVERVIEW_HEIGHT = 480

    @Volatile
    private var cachedOverview: ImageBitmap? = null

    fun getOrBuild(): ImageBitmap {
        cachedOverview?.let { return it }
        synchronized(this) {
            cachedOverview?.let { return it }
            val pixels = IntArray(OVERVIEW_WIDTH * OVERVIEW_HEIGHT)
            val scaleX = WORLD_OVERLAND_WIDTH.toFloat() / OVERVIEW_WIDTH.toFloat()
            val scaleY = WORLD_OVERLAND_HEIGHT.toFloat() / OVERVIEW_HEIGHT.toFloat()
            for (py in 0 until OVERVIEW_HEIGHT) {
                val fy = (py + 0.5f) * scaleY
                val rowOff = py * OVERVIEW_WIDTH
                for (px in 0 until OVERVIEW_WIDTH) {
                    val fx = (px + 0.5f) * scaleX
                    pixels[rowOff + px] = WorldMapTerrainSampler.sampleOverlandOverviewArgb(fx, fy)
                }
            }
            val bmp = Bitmap.createBitmap(OVERVIEW_WIDTH, OVERVIEW_HEIGHT, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, OVERVIEW_WIDTH, 0, 0, OVERVIEW_WIDTH, OVERVIEW_HEIGHT)
            val img = bmp.asImageBitmap()
            cachedOverview = img
            return img
        }
    }
}
