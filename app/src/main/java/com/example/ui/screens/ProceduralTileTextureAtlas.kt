package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.example.game.model.TileType
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Deterministic handcrafted 32x32 pixel-mosaic ground texture atlas for High and HD tiers.
 *
 * Each surface family bakes 4 subtle deterministic variants (or 3 animated frames for water/lava)
 * once into tiny ARGB_8888 Bitmaps (~4 KB each) and maps them onto any 360-degree rotated
 * isometric diamond in a single hardware-accelerated affine blit with nearest-neighbor crispness.
 */
object ProceduralTileTextureAtlas {
    const val TILE_PX = 32
    const val VARIANT_COUNT = 4
    const val WATER_FRAMES = 3

    enum class SurfaceFamily {
        COBBLESTONE,
        GRASS,
        FLOWER_GRASS,
        DARK_GRASS,
        DIRT_FLOOR,
        WATER_DEEP,
        WATER_SHALLOW,
        LAVA,
        TEMPLE_FLOOR,
        WOOD_PLANK_FLOOR,
        WOOD_BRIDGE,
        ARCANE_FLOOR,
        CRYPT_FLOOR,
        OBSIDIAN_FLOOR
    }

    private data class CacheKey(val family: SurfaceFamily, val variant: Int)

    private val bitmapCache = ConcurrentHashMap<CacheKey, Bitmap>()
    private val matrixValues = FloatArray(9)

    /**
     * Pure deterministic integer hash for `(tx, ty, seed)` so every world tile on the 400x400 map
     * always picks the exact same handcrafted mosaic pattern across sessions and camera rotations.
     */
    fun deterministicHash(tx: Int, ty: Int, seed: Int = 11): Int {
        var h = tx * 374761393 + ty * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    fun variantIndex(tx: Int, ty: Int, count: Int = VARIANT_COUNT): Int {
        if (count <= 1) return 0
        return (deterministicHash(tx, ty, 19) and 0x7FFFFFFF) % count
    }

    fun resolveSurfaceFamily(baseTile: TileType, isShallowWater: Boolean): SurfaceFamily? = when (baseTile) {
        TileType.COBBLESTONE -> SurfaceFamily.COBBLESTONE
        TileType.GRASS -> SurfaceFamily.GRASS
        TileType.FLOWER_GRASS -> SurfaceFamily.FLOWER_GRASS
        TileType.DARK_GRASS -> SurfaceFamily.DARK_GRASS
        TileType.DIRT_FLOOR, TileType.ROPE_HOLE_DOWN, TileType.ROPE_SPOT_UP -> SurfaceFamily.DIRT_FLOOR
        TileType.WATER -> if (isShallowWater) SurfaceFamily.WATER_SHALLOW else SurfaceFamily.WATER_DEEP
        TileType.LAVA -> SurfaceFamily.LAVA
        TileType.TEMPLE_FLOOR -> SurfaceFamily.TEMPLE_FLOOR
        TileType.WOOD_PLANK_FLOOR -> SurfaceFamily.WOOD_PLANK_FLOOR
        TileType.WOOD_PIER, TileType.WOOD_BRIDGE -> SurfaceFamily.WOOD_BRIDGE
        TileType.ARCANE_FLOOR -> SurfaceFamily.ARCANE_FLOOR
        TileType.CRYPT_FLOOR, TileType.STAIRS_DOWN, TileType.STAIRS_UP,
        TileType.STONE_BRIDGE, TileType.IRON_BAR_GATE -> SurfaceFamily.CRYPT_FLOOR
        TileType.OBSIDIAN_FLOOR -> SurfaceFamily.OBSIDIAN_FLOOR
        else -> null
    }

    /**
     * Configures [outMatrix] so unit tile bitmap `[0..TILE_PX, 0..TILE_PX]` maps exactly onto the
     * 360-degree rotated isometric diamond centered at `(cx, cy)` with slight [overdraw] to prevent
     * hairline seams at arbitrary camera yaw angles.
     */
    fun configureTileMatrix(
        outMatrix: Matrix,
        cam: IsometricCamera,
        cx: Float,
        cy: Float,
        overdraw: Float = 1.035f
    ) {
        val invS = overdraw / TILE_PX.toFloat()
        val half = overdraw * 0.5f
        val nwX = cx - cam.ux * half - cam.vx * half
        val nwY = cy - cam.uy * half - cam.vy * half

        matrixValues[0] = cam.ux * invS
        matrixValues[1] = cam.vx * invS
        matrixValues[2] = nwX
        matrixValues[3] = cam.uy * invS
        matrixValues[4] = cam.vy * invS
        matrixValues[5] = nwY
        matrixValues[6] = 0f
        matrixValues[7] = 0f
        matrixValues[8] = 1f
        outMatrix.setValues(matrixValues)
    }

    fun drawTexturedGroundTile(
        scope: DrawScope,
        cam: IsometricCamera,
        baseTile: TileType,
        tx: Int,
        ty: Int,
        cx: Float,
        cy: Float,
        isShallowWater: Boolean,
        waterFrame: Int,
        reusableMatrix: Matrix,
        reusablePaint: Paint
    ): Boolean {
        val family = resolveSurfaceFamily(baseTile, isShallowWater) ?: return false
        val variant = when (family) {
            SurfaceFamily.WATER_DEEP, SurfaceFamily.WATER_SHALLOW, SurfaceFamily.LAVA ->
                ((waterFrame + ((tx + ty) and 1)) % WATER_FRAMES + WATER_FRAMES) % WATER_FRAMES
            SurfaceFamily.TEMPLE_FLOOR -> (tx + ty) and 1
            else -> variantIndex(tx, ty, VARIANT_COUNT)
        }
        val bmp = getOrBakeTile(family, variant)
        configureTileMatrix(reusableMatrix, cam, cx, cy)
        scope.drawContext.canvas.nativeCanvas.drawBitmap(bmp, reusableMatrix, reusablePaint)
        return true
    }

    private fun getOrBakeTile(family: SurfaceFamily, variant: Int): Bitmap {
        val key = CacheKey(family, variant)
        return bitmapCache.getOrPut(key) {
            bakeTileBitmap(family, variant)
        }
    }

    private fun bakeTileBitmap(family: SurfaceFamily, variant: Int): Bitmap {
        val pixels = IntArray(TILE_PX * TILE_PX)
        when (family) {
            SurfaceFamily.COBBLESTONE -> bakeCobblestone(pixels, variant)
            SurfaceFamily.GRASS -> bakeGrass(pixels, variant, dark = false, flowers = false)
            SurfaceFamily.FLOWER_GRASS -> bakeGrass(pixels, variant, dark = false, flowers = true)
            SurfaceFamily.DARK_GRASS -> bakeGrass(pixels, variant, dark = true, flowers = false)
            SurfaceFamily.DIRT_FLOOR -> bakeDirt(pixels, variant)
            SurfaceFamily.WATER_DEEP -> bakeWater(pixels, variant, shallow = false)
            SurfaceFamily.WATER_SHALLOW -> bakeWater(pixels, variant, shallow = true)
            SurfaceFamily.LAVA -> bakeLava(pixels, variant)
            SurfaceFamily.TEMPLE_FLOOR -> bakeTempleMarble(pixels, variant)
            SurfaceFamily.WOOD_PLANK_FLOOR -> bakeWoodPlanks(pixels, variant, weathered = false)
            SurfaceFamily.WOOD_BRIDGE -> bakeWoodPlanks(pixels, variant, weathered = true)
            SurfaceFamily.ARCANE_FLOOR -> bakeArcaneRunestone(pixels, variant)
            SurfaceFamily.CRYPT_FLOOR -> bakeCryptFlagstone(pixels, variant)
            SurfaceFamily.OBSIDIAN_FLOOR -> bakeObsidianFloor(pixels, variant)
        }
        return Bitmap.createBitmap(pixels, TILE_PX, TILE_PX, Bitmap.Config.ARGB_8888)
    }

    private fun pxNoise(x: Int, y: Int, seed: Int): Int {
        var n = x * 73856093 xor y * 19349663 xor seed * 83492791
        n = (n xor (n ushr 13)) * 1274126177
        return (n xor (n ushr 16)) and 0xFF
    }

    private fun shiftRgb(argb: Int, dr: Int, dg: Int, db: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (((argb ushr 16) and 0xFF) + dr).coerceIn(0, 255)
        val g = (((argb ushr 8) and 0xFF) + dg).coerceIn(0, 255)
        val b = ((argb and 0xFF) + db).coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Handcrafted beveled cobblestone blocks with top-left warm highlights,
     * bottom-right deep shadow bevels, dark charcoal mortar seams, and per-stone hue variation.
     */
    private fun bakeCobblestone(pixels: IntArray, variant: Int) {
        val mortar = 0xFF3E3832.toInt()
        val deepMortar = 0xFF2D2824.toInt()
        val baseStones = intArrayOf(
            0xFF8C8274.toInt(),
            0xFF80776A.toInt(),
            0xFF968C7D.toInt(),
            0xFF7A7369.toInt(),
            0xFF8A7E70.toInt()
        )
        val rowHeight = 8
        for (y in 0 until TILE_PX) {
            val row = y / rowHeight
            val ly = y % rowHeight
            val rowShift = ((row * 5) + variant * 3) and 15
            for (x in 0 until TILE_PX) {
                val sx = (x + rowShift) % TILE_PX
                val colW = if ((row + variant) and 1 == 0) 8 else 10
                val col = sx / colW
                val lx = sx % colW
                val blockEndX = colW - 1
                val blockEndY = rowHeight - 1

                val isMortar = lx == 0 || ly == 0 ||
                    (lx == 1 && ly == 1) || (lx == blockEndX && ly == blockEndY)
                if (isMortar) {
                    val mNoise = pxNoise(x, y, variant + 101)
                    pixels[y * TILE_PX + x] = if (mNoise < 90) deepMortar else mortar
                    continue
                }

                val stoneHash = pxNoise(col + row * 13, row + variant * 7, 37)
                var base = baseStones[stoneHash % baseStones.size]
                // Subtle per-tile warm/cool stone shift
                val tileShift = (variant - 1) * 4
                val grain = (pxNoise(x, y, variant + 17) % 11) - 5
                base = shiftRgb(base, tileShift + grain, tileShift + grain - 1, tileShift + grain - 2)

                // Crisp pixel beveling: top-left warm rim highlight, bottom-right recessed bevel
                val color = when {
                    lx == 1 || ly == 1 -> shiftRgb(base, 22, 20, 14)
                    lx == 2 && ly == 2 -> shiftRgb(base, 14, 12, 8)
                    lx == blockEndX || ly == blockEndY -> shiftRgb(base, -26, -24, -20)
                    lx == blockEndX - 1 && ly == blockEndY - 1 -> shiftRgb(base, -12, -11, -9)
                    else -> base
                }
                pixels[y * TILE_PX + x] = color
            }
        }
    }

    /**
     * Layered moss & emerald grass mosaic with deterministic grass-blade tufts, soil flecks,
     * and crisp pixel wildflowers on meadow tiles.
     */
    private fun bakeGrass(pixels: IntArray, variant: Int, dark: Boolean, flowers: Boolean) {
        val palette = if (dark) {
            intArrayOf(0xFF1F3E22.toInt(), 0xFF254928.toInt(), 0xFF2B542E.toInt(), 0xFF1A341C.toInt())
        } else if (flowers) {
            intArrayOf(0xFF427734.toInt(), 0xFF4C853C.toInt(), 0xFF569143.toInt(), 0xFF39682C.toInt())
        } else {
            intArrayOf(0xFF3C6C2E.toInt(), 0xFF447735.toInt(), 0xFF4D833C.toInt(), 0xFF345E27.toInt())
        }
        val bladeLight = if (dark) 0xFF3C7340.toInt() else 0xFF69A84F.toInt()
        val bladeTip = if (dark) 0xFF4F8B52.toInt() else 0xFF83C464.toInt()
        val mossShadow = if (dark) 0xFF162C18.toInt() else 0xFF2D5323.toInt()

        for (y in 0 until TILE_PX) {
            for (x in 0 until TILE_PX) {
                // Organic 2x2 mosaic patch clustering for rich handmade pixel feel
                val patch = pxNoise(x / 2, y / 2, variant * 19 + 5)
                val fine = pxNoise(x, y, variant * 31 + 13)
                var col = palette[(patch shr 4) and 3]
                if (fine < 36) col = mossShadow
                else if (fine > 224) col = shiftRgb(col, 8, 13, 6)
                pixels[y * TILE_PX + x] = col
            }
        }

        // Deterministic pixel grass-blade tufts (3-4 per tile)
        val tuftCount = if (flowers) 5 else 4
        for (i in 0 until tuftCount) {
            val h = pxNoise(i * 11 + variant * 5, variant * 17 + i, 88)
            val tx = 3 + (h % (TILE_PX - 6))
            val ty = 4 + ((h * 7 + i * 13) % (TILE_PX - 6))
            setPx(pixels, tx, ty, mossShadow)
            setPx(pixels, tx - 1, ty - 1, bladeLight)
            setPx(pixels, tx + 1, ty - 1, bladeLight)
            setPx(pixels, tx, ty - 2, bladeTip)
            setPx(pixels, tx - 1, ty - 2, bladeLight)
        }

        // Tiny crisp pixel wildflowers on meadow tiles
        if (flowers) {
            val petalPalette = intArrayOf(
                0xFFF7DC6F.toInt(), // Buttercup gold
                0xFFF08AB0.toInt(), // Meadow rose
                0xFFC4B0FF.toInt(), // Wild lavender
                0xFFFFFAF0.toInt()  // Daisy white
            )
            for (i in 0 until 4) {
                val h = pxNoise(i * 23 + variant * 13, variant * 7 + i * 5, 211)
                val fx = 4 + (h % (TILE_PX - 8))
                val fy = 4 + (((h ushr 2) + i * 9) % (TILE_PX - 8))
                val petal = petalPalette[(h + i + variant) and 3]
                setPx(pixels, fx, fy + 1, 0xFF2B5924.toInt())
                setPx(pixels, fx - 1, fy, petal)
                setPx(pixels, fx + 1, fy, petal)
                setPx(pixels, fx, fy - 1, petal)
                setPx(pixels, fx, fy + 1, petal)
                setPx(pixels, fx, fy, 0xFFFFF4B8.toInt())
            }
        }
    }

    /**
     * Textured earth grain, tiny scattered pebbles, and subtle worn wagon-track shading.
     */
    private fun bakeDirt(pixels: IntArray, variant: Int) {
        val earthBase = 0xFF74573E.toInt()
        for (y in 0 until TILE_PX) {
            for (x in 0 until TILE_PX) {
                val n1 = pxNoise(x / 2, y / 2, variant * 13 + 7)
                val n2 = pxNoise(x, y, variant * 29 + 3)
                val delta = ((n1 % 15) - 7) + ((n2 % 9) - 4)
                var col = shiftRgb(earthBase, delta, (delta * 0.85f).toInt(), (delta * 0.65f).toInt())

                // Subtle worn twin wagon-track ruts running across highway tiles
                val inTrack = (x in 7..10) || (x in 21..24)
                if (inTrack && (variant and 1 == 0 || y % 2 == 0)) {
                    col = shiftRgb(col, -12, -10, -8)
                }
                pixels[y * TILE_PX + x] = col
            }
        }

        // Tiny deterministic pebbles with highlight & bottom shadow
        for (i in 0 until 5) {
            val h = pxNoise(i * 17, variant * 23 + i, 155)
            val px = 2 + (h % (TILE_PX - 5))
            val py = 2 + (((h ushr 1) + i * 11) % (TILE_PX - 5))
            setPx(pixels, px, py, 0xFFA49684.toInt())
            setPx(pixels, px + 1, py, 0xFF8C7E6D.toInt())
            setPx(pixels, px, py + 1, 0xFF4F3B29.toInt())
            setPx(pixels, px + 1, py + 1, 0xFF4A3726.toInt())
        }
    }

    /**
     * Multi-tone twilight river water: lighter shallow coastal turquoise vs deep twilight sapphire,
     * with animated horizontal specular ripple highlights.
     */
    private fun bakeWater(pixels: IntArray, frame: Int, shallow: Boolean) {
        val deepBase = if (shallow) 0xFF1F6F8C.toInt() else 0xFF144B73.toInt()
        val waveShift = frame * 10
        for (y in 0 until TILE_PX) {
            for (x in 0 until TILE_PX) {
                val wx = (x + waveShift) % TILE_PX
                val ripple = pxNoise(wx / 4, y / 2, 73 + (y / 8))
                val waveBand = ((y + (wx / 6)) % 8 == 0)
                val crest = ((y + frame * 3) % 11 == 2) && (wx in 6..15 || wx in 21..27)

                var col = deepBase
                val vShift = (ripple % 18) - 9
                col = shiftRgb(col, vShift / 2, vShift, (vShift * 1.15f).toInt())

                if (waveBand) {
                    col = shiftRgb(col, 10, 22, 26)
                }
                if (crest) {
                    col = if (shallow) 0xFF9BE6F5.toInt() else 0xFF66C8EB.toInt()
                }
                if (crest && (wx == 10 || wx == 24)) {
                    col = 0xFFD9F8FF.toInt() // Crisp specular glint pixel
                }
                pixels[y * TILE_PX + x] = col
            }
        }
    }

    /**
     * Volcanic cavern magma with cooled dark basalt plates over glowing molten veins.
     */
    private fun bakeLava(pixels: IntArray, frame: Int) {
        val shift = frame * 8
        for (y in 0 until TILE_PX) {
            for (x in 0 until TILE_PX) {
                val sx = (x + shift) % TILE_PX
                val n = pxNoise(sx / 3, y / 3, 409)
                val col = when {
                    n > 205 -> 0xFFFFE066.toInt()
                    n > 155 -> 0xFFFF7A1A.toInt()
                    n > 105 -> 0xFFD63B14.toInt()
                    else -> 0xFF421A12.toInt()
                }
                pixels[y * TILE_PX + x] = col
            }
        }
    }

    /**
     * Polished checkered Temple Sanctuary marble with diagonal specular sheen,
     * delicate warm veining, and subtle gold border trim.
     */
    private fun bakeTempleMarble(pixels: IntArray, variant: Int) {
        val isLight = (variant and 1) == 0
        val base = if (isLight) 0xFFE3D9C6.toInt() else 0xFFCCC0AA.toInt()
        val goldTrim = 0xFFC7A24D.toInt()
        val seam = 0xFF8C7E66.toInt()

        for (y in 0 until TILE_PX) {
            for (x in 0 until TILE_PX) {
                if (x == 0 || y == 0) {
                    pixels[y * TILE_PX + x] = seam
                    continue
                }
                if (x == 1 || y == 1) {
                    pixels[y * TILE_PX + x] = shiftRgb(base, 18, 16, 12)
                    continue
                }
                if (x == TILE_PX - 1 || y == TILE_PX - 1) {
                    pixels[y * TILE_PX + x] = shiftRgb(base, -22, -20, -18)
                    continue
                }
                // Gold inlay square frame on light marble tiles
                if (isLight && ((x in 6..25 && (y == 6 || y == 25)) || (y in 6..25 && (x == 6 || x == 25)))) {
                    pixels[y * TILE_PX + x] = goldTrim
                    continue
                }
                var col = base
                // Delicate marble veining
                val vein = abs((x * 2 + y + variant * 7) % 19 - 9)
                if (vein == 0 && pxNoise(x / 2, y / 2, 61) > 110) {
                    col = shiftRgb(col, -14, -13, -10)
                }
                // Diagonal specular sheen highlight band
                val diag = x + y
                if (diag in 18..21 || diag == 24) {
                    col = shiftRgb(col, 16, 15, 13)
                }
                pixels[y * TILE_PX + x] = col
            }
        }
    }

    /**
     * Grained oak/timber planks for Depot, Shops, Piers & Footbridges with plank seam lines,
     * staggered end-joints, and tiny iron nail heads.
     */
    private fun bakeWoodPlanks(pixels: IntArray, variant: Int, weathered: Boolean) {
        val basePlank = if (weathered) 0xFF8C6641.toInt() else 0xFF825B39.toInt()
        val seamCol = 0xFF3D2513.toInt()
        val nailCol = 0xFF282422.toInt()
        val nailHi = 0xFF6B6560.toInt()
        val plankH = 8

        for (y in 0 until TILE_PX) {
            val plankIdx = y / plankH
            val ly = y % plankH
            val plankTone = ((pxNoise(plankIdx, variant, 91) % 17) - 8)
            val jointX = (7 + plankIdx * 11 + variant * 5) % (TILE_PX - 4) + 2

            for (x in 0 until TILE_PX) {
                if (ly == 0 || x == jointX) {
                    pixels[y * TILE_PX + x] = seamCol
                    continue
                }
                val grain = ((pxNoise(x / 3, y, plankIdx + variant * 7) % 13) - 6)
                var col = shiftRgb(basePlank, plankTone + grain, plankTone + grain - 2, plankTone + grain - 4)
                if (ly == 1) col = shiftRgb(col, 14, 11, 7)
                else if (ly == plankH - 1) col = shiftRgb(col, -16, -13, -10)

                // Iron nail dots near each plank joint
                val isNail = (ly == 2 || ly == plankH - 2) && (x == jointX - 2 || x == jointX + 2)
                if (isNail) {
                    col = nailCol
                } else if (ly == 1 && (x == jointX - 2 || x == jointX + 2)) {
                    col = nailHi
                }
                pixels[y * TILE_PX + x] = col
            }
        }
    }

    /**
     * Runecrafting Academy carved dark-indigo stone inlaid with luminous arcane cyan/violet runes.
     */
    private fun bakeArcaneRunestone(pixels: IntArray, variant: Int) {
        val base = 0xFF2E2942.toInt()
        val seam = 0xFF1A1629.toInt()
        val runeCyan = 0xFF62E6FF.toInt()
        val runeViolet = 0xFFB084FF.toInt()

        for (y in 0 until TILE_PX) {
            for (x in 0 until TILE_PX) {
                if (x == 0 || y == 0 || x == 16 || y == 16) {
                    pixels[y * TILE_PX + x] = seam
                    continue
                }
                val grain = (pxNoise(x, y, variant + 303) % 11) - 5
                var col = shiftRgb(base, grain, grain, grain + 4)
                if (x == 1 || y == 1 || x == 17 || y == 17) {
                    col = shiftRgb(col, 14, 12, 22)
                }
                pixels[y * TILE_PX + x] = col
            }
        }

        // Crisp inlaid glowing pixel glyph in the tile center
        val glyphCol = if ((variant and 1) == 0) runeCyan else runeViolet
        val glowCol = if ((variant and 1) == 0) 0xFF2B6F8C.toInt() else 0xFF563B8C.toInt()
        for (d in -5..5) {
            val ax = 16 + d
            val ay1 = 16 - (5 - abs(d))
            val ay2 = 16 + (5 - abs(d))
            setPx(pixels, ax, ay1, glyphCol)
            setPx(pixels, ax, ay2, glyphCol)
            setPx(pixels, ax, ay1 - 1, glowCol)
            setPx(pixels, ax, ay2 + 1, glowCol)
        }
        setPx(pixels, 16, 16, 0xFFE6FAFF.toInt())
    }

    /**
     * Weathered stone dungeon flagstones with chipped cracks & damp mortar.
     */
    private fun bakeCryptFlagstone(pixels: IntArray, variant: Int) {
        val base = 0xFF4B505C.toInt()
        val mortar = 0xFF252830.toInt()
        for (y in 0 until TILE_PX) {
            for (x in 0 until TILE_PX) {
                val isSeam = x == 0 || y == 0 || (y == 16) || (x == 16 && y < 16) || (x == 10 && y >= 16)
                if (isSeam) {
                    pixels[y * TILE_PX + x] = mortar
                    continue
                }
                val n = (pxNoise(x, y, variant * 17 + 77) % 15) - 7
                var col = shiftRgb(base, n, n, n + 2)
                if (x == 1 || y == 1 || y == 17) col = shiftRgb(col, 14, 15, 17)
                if (x == TILE_PX - 1 || y == TILE_PX - 1 || y == 15) col = shiftRgb(col, -16, -16, -14)
                pixels[y * TILE_PX + x] = col
            }
        }
    }

    /**
     * Deep volcanic obsidian slabs with subtle glowing amethyst/ember fractures.
     */
    private fun bakeObsidianFloor(pixels: IntArray, variant: Int) {
        val base = 0xFF2F283B.toInt()
        val seam = 0xFF16111F.toInt()
        for (y in 0 until TILE_PX) {
            for (x in 0 until TILE_PX) {
                if (x == 0 || y == 0 || x == 16 || y == 16) {
                    pixels[y * TILE_PX + x] = seam
                    continue
                }
                val n = (pxNoise(x, y, variant * 23 + 99) % 13) - 6
                var col = shiftRgb(base, n, n - 1, n + 3)
                if (x == 1 || y == 1) col = shiftRgb(col, 14, 10, 22)
                // Subtle glowing volcanic fissure pixel line on variant 0/2
                if ((variant and 1) == 0 && abs(x - y) == 1 && x in 6..13) {
                    col = 0xFF9C4DFF.toInt()
                }
                pixels[y * TILE_PX + x] = col
            }
        }
    }

    private fun setPx(pixels: IntArray, x: Int, y: Int, argb: Int) {
        if (x in 0 until TILE_PX && y in 0 until TILE_PX) {
            pixels[y * TILE_PX + x] = argb
        }
    }
}
