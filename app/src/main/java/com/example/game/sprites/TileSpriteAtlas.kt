package com.example.game.sprites

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.game.model.TileType

/**
 * Generates crisp 32x32 procedural ImageBitmap tiles in classic Tibia oblique top-down perspective.
 */
object TileSpriteAtlas {
    private const val S = 32

    private val cacheFrame0 = mutableMapOf<TileType, ImageBitmap>()
    private val cacheFrame1 = mutableMapOf<TileType, ImageBitmap>()

    fun tileBitmap(type: TileType, animFrame: Int): ImageBitmap {
        val frame = animFrame and 1
        val targetMap = if (frame == 0) cacheFrame0 else cacheFrame1
        return targetMap.getOrPut(type) { buildTile(type, frame) }
    }

    fun warmUp() {
        TileType.entries.forEach { type ->
            tileBitmap(type, 0)
            tileBitmap(type, 1)
        }
    }

    private fun newPixels(baseColor: Int): IntArray = IntArray(S * S) { baseColor }

    private fun px(buf: IntArray, x: Int, y: Int, color: Int) {
        if (x in 0 until S && y in 0 until S) buf[y * S + x] = color
    }

    private fun rect(buf: IntArray, x: Int, y: Int, w: Int, h: Int, color: Int) {
        for (yy in y until (y + h)) {
            for (xx in x until (x + w)) px(buf, xx, yy, color)
        }
    }

    private fun toImageBitmap(buf: IntArray): ImageBitmap {
        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        bmp.setPixels(buf, 0, S, 0, 0, S, S)
        return bmp.asImageBitmap()
    }

    private fun hash(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    private fun buildTile(type: TileType, frame: Int): ImageBitmap = when (type) {
        TileType.GRASS -> drawGrass()
        TileType.DARK_GRASS -> drawDarkGrass()
        TileType.COBBLESTONE -> drawCobblestone()
        TileType.TEMPLE_FLOOR -> drawTempleFloor()
        TileType.TEMPLE_ALTAR -> drawTempleAltar(frame)
        TileType.WOOD_PIER -> drawWoodPier()
        TileType.WATER -> drawWater(frame)
        TileType.STONE_WALL -> drawObliqueWall(0xFF6B655E.toInt(), 0xFF47423D.toInt(), 0xFF2B2724.toInt(), 0xFF332E2A.toInt())
        TileType.CITY_WALL -> drawObliqueWall(0xFF8C8275.toInt(), 0xFF5F564B.toInt(), 0xFF3A342D.toInt(), 0xFF443D35.toInt())
        TileType.MOUNTAIN_ROCK -> drawMountainRock()
        TileType.TREE -> drawTree()
        TileType.DEPOT_CHEST -> drawDepotChest(false)
        TileType.MINI_DEPOT -> drawDepotChest(true)
        TileType.SHOP_COUNTER -> drawShopCounter()
        TileType.SMELTING_FURNACE -> drawSmeltingFurnace(frame)
        TileType.BLACKSMITH_ANVIL -> drawBlacksmithAnvil()
        TileType.ORE_OCHRE -> drawOreVein(0xFFD97D2B.toInt(), 0xFFFFB85C.toInt(), 0xFF695547.toInt())
        TileType.ORE_BOGSTONE -> drawOreVein(0xFF7B8B9E.toInt(), 0xFFBAC7D6.toInt(), 0xFF4D4A47.toInt())
        TileType.ORE_BRIMSTONE -> drawOreVein(0xFFE65319.toInt(), 0xFFFFC43D.toInt(), 0xFF302926.toInt(), frame)
        TileType.ORE_AETHERIUM -> drawOreVein(0xFF8B6BFF.toInt(), 0xFFD9C7FF.toInt(), 0xFF363245.toInt(), frame)
        TileType.DEPLETED_VEIN -> drawDepletedVein()
        TileType.DIRT_FLOOR -> drawDirtFloor()
        TileType.CAVE_WALL -> drawCaveWall()
        TileType.CRYPT_FLOOR -> drawCryptFloor()
        TileType.LAVA -> drawLava(frame)
        TileType.OBSIDIAN_FLOOR -> drawObsidianFloor()
        TileType.STAIRS_DOWN -> drawStairsDown()
        TileType.STAIRS_UP -> drawStairsUp()
        TileType.ROPE_HOLE_DOWN -> drawRopeHoleDown()
        TileType.ROPE_SPOT_UP -> drawRopeSpotUp()
        TileType.TORCH_WALL -> drawTorchWall(frame)
    }

    private fun drawGrass(): ImageBitmap {
        val buf = newPixels(0xFF38662E.toInt())
        for (y in 0 until S) for (x in 0 until S) {
            val h = hash(x, y, 1) and 15
            px(buf, x, y, when {
                h < 3 -> 0xFF2D5424.toInt()
                h < 6 -> 0xFF457838.toInt()
                h == 15 && (x + y) % 3 == 0 -> 0xFF5D944A.toInt()
                else -> 0xFF38662E.toInt()
            })
        }
        px(buf, 7, 10, 0xFF68A352.toInt()); px(buf, 8, 9, 0xFF68A352.toInt())
        px(buf, 22, 24, 0xFF68A352.toInt()); px(buf, 18, 6, 0xFFE3C866.toInt())
        return toImageBitmap(buf)
    }

    private fun drawDarkGrass(): ImageBitmap {
        val buf = newPixels(0xFF1F3D22.toInt())
        for (y in 0 until S) for (x in 0 until S) {
            val h = hash(x, y, 5) and 15
            px(buf, x, y, when {
                h < 4 -> 0xFF162B18.toInt()
                h < 7 -> 0xFF294F2C.toInt()
                h == 15 -> 0xFF3A6638.toInt()
                else -> 0xFF1F3D22.toInt()
            })
        }
        px(buf, 10, 12, 0xFF528C4D.toInt()); px(buf, 21, 20, 0xFF4A3D66.toInt()) // dark forest mushroom speck
        return toImageBitmap(buf)
    }

    private fun drawCobblestone(): ImageBitmap {
        val buf = newPixels(0xFF3B3632.toInt())
        val stones = listOf(
            intArrayOf(1, 1, 9, 7), intArrayOf(11, 1, 10, 7), intArrayOf(22, 1, 9, 7),
            intArrayOf(1, 9, 13, 7), intArrayOf(15, 9, 9, 7), intArrayOf(25, 9, 6, 7),
            intArrayOf(1, 17, 7, 7), intArrayOf(9, 17, 12, 7), intArrayOf(22, 17, 9, 7),
            intArrayOf(1, 25, 11, 6), intArrayOf(13, 25, 10, 6), intArrayOf(24, 25, 7, 6)
        )
        stones.forEachIndexed { idx, s ->
            val shade = when (idx % 3) { 0 -> 0xFF736B63.toInt(); 1 -> 0xFF665E57.toInt(); else -> 0xFF7D746B.toInt() }
            rect(buf, s[0], s[1], s[2], s[3], shade)
            rect(buf, s[0], s[1], s[2], 1, 0xFF91877D.toInt())
            rect(buf, s[0], s[1], 1, s[3], 0xFF8A8076.toInt())
            rect(buf, s[0], s[1] + s[3] - 1, s[2], 1, 0xFF4F4842.toInt())
        }
        return toImageBitmap(buf)
    }

    private fun drawTempleFloor(): ImageBitmap {
        val buf = newPixels(0xFF9E9484.toInt())
        rect(buf, 0, 0, 16, 16, 0xFFB0A696.toInt())
        rect(buf, 16, 16, 16, 16, 0xFFB0A696.toInt())
        rect(buf, 0, 0, S, 1, 0xFF756C5E.toInt()); rect(buf, 0, 0, 1, S, 0xFF756C5E.toInt())
        rect(buf, 14, 15, 4, 2, 0xFFD9AE48.toInt()); rect(buf, 15, 14, 2, 4, 0xFFD9AE48.toInt())
        return toImageBitmap(buf)
    }

    private fun drawTempleAltar(frame: Int): ImageBitmap {
        val buf = newPixels(0xFFA89E8E.toInt())
        rect(buf, 5, 25, 24, 5, 0xFF474139.toInt())
        rect(buf, 4, 6, 22, 12, 0xFFDCD3C2.toInt())
        rect(buf, 4, 18, 22, 9, 0xFF8C8274.toInt())
        rect(buf, 26, 8, 3, 19, 0xFF635B50.toInt())
        rect(buf, 11, 7, 8, 19, 0xFFC23232.toInt())
        rect(buf, 13, 9, 4, 6, 0xFFE6B44C.toInt())
        rect(buf, 14, 6, 2, 4, if (frame == 0) 0xFFFFE57F.toInt() else 0xFFFFC13B.toInt())
        return toImageBitmap(buf)
    }

    private fun drawWoodPier(): ImageBitmap {
        val buf = newPixels(0xFF4A311D.toInt())
        for (y in 0 until S) {
            if (y % 6 == 0) rect(buf, 0, y, S, 1, 0xFF2B1B0E.toInt())
            else {
                rect(buf, 0, y, S, 1, if ((y / 6) % 2 == 0) 0xFF7D5432.toInt() else 0xFF6E492A.toInt())
                if (y % 6 == 1) rect(buf, 0, y, S, 1, 0xFF94663E.toInt())
            }
        }
        rect(buf, 0, 0, 3, S, 0xFF5C3C22.toInt()); rect(buf, S - 3, 0, 3, S, 0xFF472D18.toInt())
        return toImageBitmap(buf)
    }

    private fun drawWater(frame: Int): ImageBitmap {
        val buf = newPixels(0xFF1D4F7A.toInt())
        val shift = if (frame == 0) 0 else 4
        for (y in 0 until S) for (x in 0 until S) {
            val wave = ((x + shift) / 4 + y / 4) % 4
            px(buf, x, y, when {
                wave == 0 && y % 8 == 2 -> 0xFF4689BD.toInt()
                wave == 1 && y % 8 == 3 -> 0xFF6BB1E0.toInt()
                (x + y + shift) % 11 == 0 -> 0xFF163E61.toInt()
                else -> 0xFF1E5482.toInt()
            })
        }
        return toImageBitmap(buf)
    }

    private fun drawObliqueWall(top: Int, front: Int, dark: Int, mortar: Int): ImageBitmap {
        val buf = newPixels(dark)
        rect(buf, 0, 0, 27, 15, top)
        rect(buf, 0, 0, 27, 2, lighten(top, 22))
        rect(buf, 0, 0, 2, 15, lighten(top, 16))
        rect(buf, 0, 15, 27, 17, front)
        rect(buf, 27, 2, 5, 30, dark)
        for (by in listOf(20, 26)) rect(buf, 0, by, 27, 1, mortar)
        for (bx in listOf(8, 18)) { rect(buf, bx, 15, 1, 5, mortar); rect(buf, bx, 26, 1, 6, mortar) }
        for (bx in listOf(4, 14, 23)) rect(buf, bx, 20, 1, 6, mortar)
        return toImageBitmap(buf)
    }

    private fun drawMountainRock(): ImageBitmap {
        val buf = newPixels(0xFF282524.toInt())
        rect(buf, 0, 0, 28, 17, 0xFF696461.toInt())
        rect(buf, 3, 2, 22, 12, 0xFF827C78.toInt())
        rect(buf, 0, 17, 28, 15, 0xFF4D4947.toInt())
        rect(buf, 28, 3, 4, 29, 0xFF2B2827.toInt())
        // Craggy ridge highlights
        rect(buf, 5, 4, 10, 2, 0xFF9E9893.toInt())
        rect(buf, 14, 9, 10, 2, 0xFF575350.toInt())
        return toImageBitmap(buf)
    }

    private fun drawTree(): ImageBitmap {
        val buf = newPixels(0xFF2A4F24.toInt())
        rect(buf, 11, 22, 17, 8, 0xFF152913.toInt())
        rect(buf, 12, 19, 7, 10, 0xFF59381E.toInt())
        rect(buf, 4, 8, 22, 14, 0xFF1C451C.toInt())
        rect(buf, 6, 4, 18, 15, 0xFF286326.toInt())
        rect(buf, 8, 2, 13, 10, 0xFF3E8537.toInt())
        rect(buf, 10, 4, 7, 5, 0xFF56A649.toInt())
        return toImageBitmap(buf)
    }

    private fun drawDepotChest(isMini: Boolean): ImageBitmap {
        val buf = newPixels(0xFF5E564E.toInt())
        rect(buf, 5, 25, 24, 5, 0xFF24201C.toInt())
        val woodTop = if (isMini) 0xFF525A66.toInt() else 0xFF8C5A2B.toInt()
        val woodFront = if (isMini) 0xFF3B414A.toInt() else 0xFF69411D.toInt()
        rect(buf, 3, 6, 24, 10, woodTop)
        rect(buf, 3, 16, 24, 11, woodFront)
        rect(buf, 27, 8, 3, 19, 0xFF2A241E.toInt())
        rect(buf, 3, 6, 24, 2, 0xFF9EA8B5.toInt())
        rect(buf, 7, 6, 3, 21, 0xFF7A828C.toInt())
        rect(buf, 20, 6, 3, 21, 0xFF7A828C.toInt())
        rect(buf, 13, 14, 4, 6, if (isMini) 0xFF5CE1FF.toInt() else 0xFFE6B44C.toInt())
        rect(buf, 14, 16, 2, 2, 0xFF1A1412.toInt())
        return toImageBitmap(buf)
    }

    private fun drawShopCounter(): ImageBitmap {
        val buf = newPixels(0xFF615950.toInt())
        rect(buf, 2, 6, 26, 10, 0xFF996A3D.toInt())
        rect(buf, 2, 16, 26, 12, 0xFF694524.toInt())
        rect(buf, 28, 8, 3, 20, 0xFF3E2814.toInt())
        rect(buf, 6, 7, 4, 6, 0xFFD92B2B.toInt())
        rect(buf, 13, 7, 4, 6, 0xFF2B6DD9.toInt())
        rect(buf, 20, 8, 5, 5, 0xFF7B8594.toInt()); px(buf, 22, 10, 0xFF66E0FF.toInt())
        return toImageBitmap(buf)
    }

    private fun drawSmeltingFurnace(frame: Int): ImageBitmap {
        val buf = newPixels(0xFF4F4842.toInt())
        rect(buf, 5, 25, 25, 6, 0xFF1F1C1A.toInt())
        // Oblique brick smelter chimney & firebox
        rect(buf, 4, 2, 22, 11, 0xFF7A4B3A.toInt())
        rect(buf, 4, 13, 22, 15, 0xFF593529.toInt())
        rect(buf, 26, 4, 4, 24, 0xFF382018.toInt())
        // Dark chimney flue on top
        rect(buf, 9, 4, 12, 6, 0xFF1C1715.toInt())
        // Roaring molten furnace mouth
        rect(buf, 8, 16, 14, 10, 0xFF24130D.toInt())
        val outerFire = if (frame == 0) 0xFFFF5E1A.toInt() else 0xFFFF7A29.toInt()
        val innerFire = if (frame == 0) 0xFFFFD447.toInt() else 0xFFFFF099.toInt()
        rect(buf, 10, 18, 10, 8, outerFire)
        rect(buf, 12, 20 - frame, 6, 5 + frame, innerFire)
        return toImageBitmap(buf)
    }

    private fun drawBlacksmithAnvil(): ImageBitmap {
        val buf = newPixels(0xFF5E564E.toInt())
        rect(buf, 6, 25, 22, 5, 0xFF24201C.toInt())
        // Thick oak stump base
        rect(buf, 9, 18, 14, 9, 0xFF6E4726.toInt())
        rect(buf, 9, 18, 4, 9, 0xFF875931.toInt())
        // Heavy cast-iron oblique anvil horn & face
        rect(buf, 5, 10, 22, 5, 0xFF636B75.toInt())
        rect(buf, 6, 9, 20, 3, 0xFF9EA8B5.toInt()) // polished steel striking top
        rect(buf, 2, 11, 5, 3, 0xFF7B8591.toInt()) // pointed horn left
        rect(buf, 11, 15, 10, 5, 0xFF424852.toInt()) // waist
        return toImageBitmap(buf)
    }

    private fun drawOreVein(oreMain: Int, oreShine: Int, rockBase: Int, frame: Int = 0): ImageBitmap {
        val buf = newPixels(0xFF473A2E.toInt())
        rect(buf, 5, 24, 24, 6, 0xFF1E1915.toInt())
        // Oblique boulder outcrop
        rect(buf, 4, 7, 22, 11, lighten(rockBase, 14))
        rect(buf, 3, 14, 24, 12, rockBase)
        rect(buf, 25, 10, 4, 16, darken(rockBase, 22))
        // Embedded crystalline ore seams
        rect(buf, 7, 10, 5, 4, oreMain); rect(buf, 8, 10, 2, 2, oreShine)
        rect(buf, 16, 12, 6, 5, oreMain); rect(buf, 18, 13, 2, 2, oreShine)
        rect(buf, 9, 19, 7, 4, oreMain); rect(buf, 11, 20, 2, 1, oreShine)
        if (frame == 1) {
            px(buf, 9, 10, 0xFFFFFFFF.toInt())
            px(buf, 19, 13, 0xFFFFFFFF.toInt())
        }
        return toImageBitmap(buf)
    }

    private fun drawDepletedVein(): ImageBitmap {
        val buf = newPixels(0xFF473A2E.toInt())
        rect(buf, 6, 24, 21, 5, 0xFF1F1A16.toInt())
        rect(buf, 6, 14, 18, 11, 0xFF4D4742.toInt())
        rect(buf, 8, 11, 14, 6, 0xFF615A54.toInt())
        rect(buf, 10, 16, 4, 3, 0xFF2E2A27.toInt()) // empty chipped cavity
        rect(buf, 17, 18, 4, 3, 0xFF2E2A27.toInt())
        return toImageBitmap(buf)
    }

    private fun drawDirtFloor(): ImageBitmap {
        val buf = newPixels(0xFF574433.toInt())
        for (y in 0 until S) for (x in 0 until S) {
            val h = hash(x, y, 7) and 15
            px(buf, x, y, when {
                h < 3 -> 0xFF473627.toInt()
                h < 6 -> 0xFF66503C.toInt()
                h == 15 -> 0xFF3B4D2B.toInt()
                else -> 0xFF574433.toInt()
            })
        }
        return toImageBitmap(buf)
    }

    private fun drawCaveWall(): ImageBitmap {
        val buf = newPixels(0xFF261E18.toInt())
        rect(buf, 0, 0, 28, 16, 0xFF544335.toInt())
        rect(buf, 2, 2, 24, 12, 0xFF635040.toInt())
        rect(buf, 0, 16, 28, 16, 0xFF3D3026.toInt())
        rect(buf, 28, 3, 4, 29, 0xFF211A14.toInt())
        return toImageBitmap(buf)
    }

    private fun drawCryptFloor(): ImageBitmap {
        val buf = newPixels(0xFF35383D.toInt())
        rect(buf, 1, 1, 14, 14, 0xFF474B52.toInt())
        rect(buf, 17, 1, 14, 14, 0xFF41454C.toInt())
        rect(buf, 1, 17, 14, 14, 0xFF41454C.toInt())
        rect(buf, 17, 17, 14, 14, 0xFF4B5057.toInt())
        return toImageBitmap(buf)
    }

    private fun drawLava(frame: Int): ImageBitmap {
        val buf = newPixels(0xFFB83014.toInt())
        val offset = if (frame == 0) 0 else 5
        for (y in 0 until S) for (x in 0 until S) {
            val v = ((x + offset) * 3 + y * 5) % 17
            px(buf, x, y, when {
                v < 3 -> 0xFFFFB833.toInt()
                v < 7 -> 0xFFFF661A.toInt()
                v == 12 -> 0xFF6B180B.toInt()
                else -> 0xFFC93814.toInt()
            })
        }
        return toImageBitmap(buf)
    }

    private fun drawObsidianFloor(): ImageBitmap {
        val buf = newPixels(0xFF262129.toInt())
        for (y in 0 until S) for (x in 0 until S) {
            val h = hash(x, y, 29) and 15
            px(buf, x, y, when {
                h < 3 -> 0xFF1D1820.toInt()
                h < 5 -> 0xFF38303D.toInt()
                h == 15 -> 0xFF543026.toInt()
                else -> 0xFF29232C.toInt()
            })
        }
        return toImageBitmap(buf)
    }

    private fun drawStairsDown(): ImageBitmap {
        val buf = newPixels(0xFF14110F.toInt())
        rect(buf, 0, 0, S, 3, 0xFF6B6258.toInt()); rect(buf, 0, 0, 3, S, 0xFF6B6258.toInt())
        val shades = intArrayOf(0xFF8C8275.toInt(), 0xFF6E655A.toInt(), 0xFF524B42.toInt(), 0xFF38332D.toInt(), 0xFF1F1C19.toInt())
        shades.forEachIndexed { i, c -> rect(buf, 4, 4 + i * 5, 24, 4, c) }
        return toImageBitmap(buf)
    }

    private fun drawStairsUp(): ImageBitmap {
        val buf = newPixels(0xFF4D453E.toInt())
        val shades = intArrayOf(0xFF524A42.toInt(), 0xFF6E645A.toInt(), 0xFF8C8073.toInt(), 0xFFABA091.toInt(), 0xFFC7BCA9.toInt())
        shades.forEachIndexed { i, c -> rect(buf, 4, 24 - i * 5, 24, 4, c) }
        rect(buf, 6, 2, 20, 2, 0xFFFFDF80.toInt())
        return toImageBitmap(buf)
    }

    private fun drawRopeHoleDown(): ImageBitmap {
        val buf = newPixels(0xFF574433.toInt())
        rect(buf, 5, 5, 22, 22, 0xFF735A43.toInt())
        rect(buf, 7, 7, 18, 18, 0xFF3D2E22.toInt())
        rect(buf, 9, 9, 14, 14, 0xFF0A0807.toInt())
        return toImageBitmap(buf)
    }

    private fun drawRopeSpotUp(): ImageBitmap {
        val buf = newPixels(0xFF473A2E.toInt())
        rect(buf, 6, 6, 20, 20, 0xFF856E4E.toInt())
        rect(buf, 8, 8, 16, 16, 0xFFB89E6E.toInt())
        rect(buf, 11, 11, 10, 10, 0xFFE3CA8A.toInt())
        rect(buf, 13, 13, 6, 6, 0xFFFFEAB0.toInt())
        return toImageBitmap(buf)
    }

    private fun drawTorchWall(frame: Int): ImageBitmap {
        val buf = newPixels(0xFF2B2724.toInt())
        rect(buf, 0, 0, 27, 15, 0xFF6B655E.toInt())
        rect(buf, 0, 15, 27, 17, 0xFF47423D.toInt())
        rect(buf, 27, 2, 5, 30, 0xFF2B2724.toInt())
        rect(buf, 13, 16, 4, 9, 0xFF2E2E30.toInt())
        rect(buf, 11, 9, 8, 6, if (frame == 0) 0xFFD9641A.toInt() else 0xFFE6781E.toInt())
        rect(buf, 13, 8 + frame, 4, 6, if (frame == 0) 0xFFFFD447.toInt() else 0xFFFFEB80.toInt())
        return toImageBitmap(buf)
    }

    private fun lighten(argb: Int, delta: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (((argb ushr 16) and 0xFF) + delta).coerceIn(0, 255)
        val g = (((argb ushr 8) and 0xFF) + delta).coerceIn(0, 255)
        val b = ((argb and 0xFF) + delta).coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun darken(argb: Int, delta: Int): Int = lighten(argb, -delta)
}
