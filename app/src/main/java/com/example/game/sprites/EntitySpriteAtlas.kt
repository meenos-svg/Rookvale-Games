package com.example.game.sprites

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.game.model.AdventurerPalette
import com.example.game.model.Direction
import com.example.game.model.FishingCatalog
import com.example.game.model.ItemCatalog
import com.example.game.model.ItemCategory
import com.example.game.model.MonsterType
import com.example.game.model.Vocation
import com.example.game.world.NpcRole

/**
 * Generates crisp 32x32 procedural ImageBitmap sprites for Players (including customizable Adventurers),
 * all 14 Monster types, NPCs (including The Oracle), Corpses, and distinct 16x16 Item Icons.
 */
object EntitySpriteAtlas {
    private const val S = 32
    private const val ITEM_S = 16

    private val playerCache = mutableMapOf<String, ImageBitmap>()
    private val monsterCache = mutableMapOf<Pair<MonsterType, Int>, ImageBitmap>()
    private val npcCache = mutableMapOf<NpcRole, ImageBitmap>()
    private val itemCache = mutableMapOf<String, ImageBitmap>()
    private var monsterCorpseBmp: ImageBitmap? = null
    private var playerCorpseBmp: ImageBitmap? = null

    fun warmUp() {
        Vocation.entries.forEach { v ->
            Direction.entries.forEach { d ->
                playerBitmap(v, 0, direction = d)
                playerBitmap(v, 1, direction = d)
            }
        }
        MonsterType.entries.forEach { m -> monsterBitmap(m, 0); monsterBitmap(m, 1) }
        NpcRole.entries.forEach { npcBitmap(it) }
        corpseBitmap(false); corpseBitmap(true)
        ItemCatalog.all.keys.forEach { itemIcon(it) }
    }

    fun playerBitmap(
        vocation: Vocation,
        frame: Int,
        tunicColorIndex: Int = 0,
        hairColorIndex: Int = 0,
        direction: Direction = Direction.SOUTH
    ): ImageBitmap {
        val f = frame and 1
        val key = if (vocation == Vocation.ADVENTURER) {
            "ADV_${tunicColorIndex.coerceIn(0, AdventurerPalette.tunicColors.lastIndex)}_${hairColorIndex.coerceIn(0, AdventurerPalette.hairColors.lastIndex)}_${direction.name}_$f"
        } else {
            "${vocation.name}_${direction.name}_$f"
        }
        return playerCache.getOrPut(key) { buildPlayer(vocation, f, tunicColorIndex, hairColorIndex, direction) }
    }

    fun monsterBitmap(type: MonsterType, frame: Int): ImageBitmap =
        monsterCache.getOrPut(type to (frame and 1)) { buildMonster(type, frame and 1) }

    fun npcBitmap(role: NpcRole): ImageBitmap = npcCache.getOrPut(role) { buildNpc(role) }

    fun corpseBitmap(isPlayer: Boolean): ImageBitmap = if (isPlayer) {
        playerCorpseBmp ?: buildPlayerCorpse().also { playerCorpseBmp = it }
    } else {
        monsterCorpseBmp ?: buildMonsterCorpse().also { monsterCorpseBmp = it }
    }

    fun itemIcon(itemId: String): ImageBitmap = itemCache.getOrPut(itemId) { buildItemIcon(itemId) }

    private fun buf32() = IntArray(S * S) { 0 }
    private fun buf16() = IntArray(ITEM_S * ITEM_S) { 0 }
    private fun px(buf: IntArray, size: Int, x: Int, y: Int, color: Int) {
        if (x in 0 until size && y in 0 until size) buf[y * size + x] = color
    }
    private fun rect(buf: IntArray, size: Int, x: Int, y: Int, w: Int, h: Int, color: Int) {
        for (yy in y until (y + h)) for (xx in x until (x + w)) px(buf, size, xx, yy, color)
    }
    private fun toBmp(buf: IntArray, size: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.setPixels(buf, 0, size, 0, 0, size, size)
        return bmp.asImageBitmap()
    }
    private fun drawShadow(buf: IntArray, x: Int = 8, y: Int = 25, w: Int = 16, h: Int = 5) {
        rect(buf, S, x, y, w, h, 0x66000000)
    }

    private fun flipHorizontal(buf: IntArray, size: Int = S): IntArray {
        val out = IntArray(size * size)
        for (y in 0 until size) {
            val row = y * size
            for (x in 0 until size) {
                out[row + (size - 1 - x)] = buf[row + x]
            }
        }
        return out
    }

    private fun buildPlayer(
        vocation: Vocation,
        frame: Int,
        tunicColorIndex: Int,
        hairColorIndex: Int,
        direction: Direction
    ): ImageBitmap {
        if (direction == Direction.WEST) {
            val eastBuf = buildPlayerPixels(vocation, frame, tunicColorIndex, hairColorIndex, Direction.EAST)
            return toBmp(flipHorizontal(eastBuf, S), S)
        }
        return toBmp(buildPlayerPixels(vocation, frame, tunicColorIndex, hairColorIndex, direction), S)
    }

    private fun buildPlayerPixels(
        vocation: Vocation,
        frame: Int,
        tunicColorIndex: Int,
        hairColorIndex: Int,
        direction: Direction
    ): IntArray {
        val buf = buf32()
        val bob = if (frame == 1) -1 else 0
        drawShadow(buf)
        val skin = 0xFFE8BE96.toInt()
        val boot = 0xFF3D2514.toInt()
        val outline = 0xFF14100E.toInt()

        if (direction == Direction.EAST) {
            // Side-facing stride boots
            rect(buf, S, 11 + (if (frame == 1) 2 else -1), 23, 5, 4, boot)
            rect(buf, S, 15 + (if (frame == 1) -2 else 1), 23, 5, 4, boot)
        } else {
            rect(buf, S, 10, 23 + (if (frame == 1) -1 else 0), 4, 4, boot)
            rect(buf, S, 17, 23 + (if (frame == 1) 1 else 0), 4, 4, boot)
        }

        when (vocation) {
            Vocation.ADVENTURER -> {
                val tunic = AdventurerPalette.tunicAt(tunicColorIndex)
                val hair = AdventurerPalette.hairAt(hairColorIndex)
                when (direction) {
                    Direction.SOUTH -> {
                        rect(buf, S, 10, 18 + bob, 11, 6, 0xFF4F3B2A.toInt())
                        rect(buf, S, 9, 10 + bob, 13, 9, tunic.primaryArgb)
                        rect(buf, S, 11, 11 + bob, 9, 7, tunic.highlightArgb)
                        rect(buf, S, 10, 16 + bob, 11, 2, 0xFF6B4423.toInt())
                        rect(buf, S, 14, 16 + bob, 3, 2, 0xFFE6B44C.toInt())
                        px(buf, S, 11, 10 + bob, 0xFF543318.toInt())
                        px(buf, S, 12, 11 + bob, 0xFF543318.toInt())
                        px(buf, S, 13, 12 + bob, 0xFF543318.toInt())
                        px(buf, S, 14, 13 + bob, 0xFF543318.toInt())
                        rect(buf, S, 12, 5 + bob, 7, 5, skin)
                        rect(buf, S, 11, 2 + bob, 9, 4, hair.primaryArgb)
                        rect(buf, S, 12, 2 + bob, 7, 2, hair.highlightArgb)
                        rect(buf, S, 11, 5 + bob, 1, 3, hair.primaryArgb)
                        rect(buf, S, 19, 5 + bob, 1, 3, hair.primaryArgb)
                        px(buf, S, 13, 7 + bob, outline); px(buf, S, 17, 7 + bob, outline)
                        rect(buf, S, 6, 12 + bob, 2, 7, 0xFFD99A4E.toInt())
                        rect(buf, S, 5, 16 + bob, 4, 2, 0xFF7D4E26.toInt())
                        rect(buf, S, 22, 12 + bob, 5, 8, 0xFF7D4E26.toInt())
                        rect(buf, S, 23, 13 + bob, 3, 6, 0xFFA66C38.toInt())
                    }
                    Direction.NORTH -> {
                        rect(buf, S, 10, 18 + bob, 11, 6, 0xFF4F3B2A.toInt())
                        rect(buf, S, 9, 10 + bob, 13, 9, tunic.primaryArgb)
                        rect(buf, S, 10, 16 + bob, 11, 2, 0xFF6B4423.toInt())
                        // Adventurer leather pack on back
                        rect(buf, S, 11, 11 + bob, 9, 6, 0xFF6B4423.toInt())
                        rect(buf, S, 12, 12 + bob, 7, 4, 0xFF8C5A2E.toInt())
                        // Back of hair covering head
                        rect(buf, S, 11, 2 + bob, 9, 8, hair.primaryArgb)
                        rect(buf, S, 12, 2 + bob, 7, 3, hair.highlightArgb)
                        rect(buf, S, 6, 13 + bob, 2, 6, 0xFFD99A4E.toInt())
                        rect(buf, S, 22, 12 + bob, 4, 7, 0xFF7D4E26.toInt())
                    }
                    else -> { // EAST (WEST mirrors this)
                        rect(buf, S, 12, 18 + bob, 8, 6, 0xFF4F3B2A.toInt())
                        rect(buf, S, 11, 10 + bob, 10, 9, tunic.primaryArgb)
                        rect(buf, S, 14, 11 + bob, 6, 7, tunic.highlightArgb)
                        rect(buf, S, 11, 16 + bob, 10, 2, 0xFF6B4423.toInt())
                        // Back satchel profile
                        rect(buf, S, 9, 11 + bob, 3, 6, 0xFF6B4423.toInt())
                        // Profile head & hair
                        rect(buf, S, 13, 5 + bob, 7, 5, skin)
                        rect(buf, S, 11, 2 + bob, 9, 4, hair.primaryArgb)
                        rect(buf, S, 11, 5 + bob, 3, 4, hair.primaryArgb)
                        rect(buf, S, 13, 2 + bob, 6, 2, hair.highlightArgb)
                        px(buf, S, 18, 7 + bob, outline) // single forward eye
                        // Forward dirk held out right + buckler on flank
                        rect(buf, S, 21, 13 + bob, 6, 2, 0xFFD99A4E.toInt())
                        rect(buf, S, 20, 12 + bob, 2, 4, 0xFF7D4E26.toInt())
                        rect(buf, S, 15, 13 + bob, 4, 7, 0xFF7D4E26.toInt())
                        rect(buf, S, 16, 14 + bob, 2, 5, 0xFFA66C38.toInt())
                    }
                }
            }
            Vocation.KNIGHT -> {
                when (direction) {
                    Direction.SOUTH -> {
                        rect(buf, S, 10, 18 + bob, 11, 6, 0xFF6B7380.toInt())
                        rect(buf, S, 9, 10 + bob, 13, 9, 0xFFB82828.toInt())
                        rect(buf, S, 11, 11 + bob, 9, 7, 0xFFD93B3B.toInt())
                        rect(buf, S, 15, 11 + bob, 2, 6, 0xFFE6B44C.toInt())
                        rect(buf, S, 13, 13 + bob, 6, 2, 0xFFE6B44C.toInt())
                        rect(buf, S, 11, 3 + bob, 9, 8, 0xFF9EA8B5.toInt())
                        rect(buf, S, 12, 6 + bob, 7, 3, skin)
                        rect(buf, S, 13, 7 + bob, 2, 1, outline); rect(buf, S, 17, 7 + bob, 2, 1, outline)
                        rect(buf, S, 14, 1 + bob, 3, 3, 0xFFD93B3B.toInt())
                        rect(buf, S, 5, 7 + bob, 3, 12, 0xFFD8E0EB.toInt())
                        rect(buf, S, 4, 16 + bob, 5, 2, 0xFFE6B44C.toInt())
                        rect(buf, S, 21, 11 + bob, 7, 10, 0xFF56606E.toInt())
                        rect(buf, S, 22, 12 + bob, 5, 8, 0xFF2D5CA6.toInt())
                        rect(buf, S, 24, 14 + bob, 2, 4, 0xFFE6B44C.toInt())
                    }
                    Direction.NORTH -> {
                        rect(buf, S, 10, 18 + bob, 11, 6, 0xFF6B7380.toInt())
                        rect(buf, S, 9, 10 + bob, 13, 11, 0xFF961E1E.toInt()) // crimson cape back
                        rect(buf, S, 11, 11 + bob, 9, 8, 0xFFB82828.toInt())
                        rect(buf, S, 11, 3 + bob, 9, 8, 0xFF8C96A3.toInt()) // back of steel helm
                        rect(buf, S, 14, 1 + bob, 3, 4, 0xFFD93B3B.toInt()) // helmet plume
                        rect(buf, S, 5, 8 + bob, 3, 10, 0xFFD8E0EB.toInt())
                        rect(buf, S, 21, 11 + bob, 6, 9, 0xFF56606E.toInt())
                    }
                    else -> { // EAST
                        rect(buf, S, 12, 18 + bob, 8, 6, 0xFF6B7380.toInt())
                        rect(buf, S, 10, 10 + bob, 11, 9, 0xFFB82828.toInt())
                        rect(buf, S, 9, 11 + bob, 3, 10, 0xFF8C1C1C.toInt()) // trailing cape
                        rect(buf, S, 12, 3 + bob, 8, 8, 0xFF9EA8B5.toInt())
                        rect(buf, S, 15, 6 + bob, 5, 3, skin)
                        rect(buf, S, 17, 7 + bob, 2, 1, outline)
                        rect(buf, S, 11, 1 + bob, 5, 3, 0xFFD93B3B.toInt()) // plume sweeping back
                        // Forward greatsword + kite shield
                        rect(buf, S, 22, 9 + bob, 3, 10, 0xFFD8E0EB.toInt())
                        rect(buf, S, 21, 16 + bob, 5, 2, 0xFFE6B44C.toInt())
                        rect(buf, S, 15, 12 + bob, 6, 9, 0xFF56606E.toInt())
                        rect(buf, S, 16, 13 + bob, 4, 7, 0xFF2D5CA6.toInt())
                    }
                }
            }
            Vocation.PALADIN -> {
                when (direction) {
                    Direction.SOUTH -> {
                        rect(buf, S, 10, 18 + bob, 11, 6, 0xFF594028.toInt())
                        rect(buf, S, 9, 10 + bob, 13, 9, 0xFF286E3C.toInt())
                        rect(buf, S, 11, 11 + bob, 9, 7, 0xFF398C4E.toInt())
                        rect(buf, S, 10, 16 + bob, 11, 2, 0xFFD4A23C.toInt())
                        rect(buf, S, 10, 3 + bob, 11, 8, 0xFF205430.toInt())
                        rect(buf, S, 12, 6 + bob, 7, 4, skin)
                        px(buf, S, 13, 7 + bob, outline); px(buf, S, 17, 7 + bob, outline)
                        rect(buf, S, 5, 6 + bob, 2, 15, 0xFF8C5828.toInt())
                        rect(buf, S, 7, 7 + bob, 1, 13, 0xFFDCD3C2.toInt())
                        rect(buf, S, 21, 9 + bob, 4, 9, 0xFF7D4E26.toInt())
                    }
                    Direction.NORTH -> {
                        rect(buf, S, 10, 18 + bob, 11, 6, 0xFF594028.toInt())
                        rect(buf, S, 9, 10 + bob, 13, 9, 0xFF286E3C.toInt())
                        rect(buf, S, 10, 3 + bob, 11, 8, 0xFF205430.toInt()) // hood back
                        // Quiver strapped across back with gold fletching
                        rect(buf, S, 13, 10 + bob, 5, 8, 0xFF7D4E26.toInt())
                        rect(buf, S, 14, 8 + bob, 3, 2, 0xFFE6B44C.toInt())
                        rect(buf, S, 5, 7 + bob, 2, 13, 0xFF8C5828.toInt())
                    }
                    else -> { // EAST
                        rect(buf, S, 12, 18 + bob, 8, 6, 0xFF594028.toInt())
                        rect(buf, S, 11, 10 + bob, 10, 9, 0xFF286E3C.toInt())
                        rect(buf, S, 14, 11 + bob, 6, 7, 0xFF398C4E.toInt())
                        rect(buf, S, 8, 10 + bob, 4, 8, 0xFF7D4E26.toInt()) // quiver on back
                        rect(buf, S, 11, 3 + bob, 9, 8, 0xFF205430.toInt())
                        rect(buf, S, 15, 6 + bob, 5, 4, skin)
                        px(buf, S, 18, 7 + bob, outline)
                        // Forward bow facing east
                        rect(buf, S, 22, 6 + bob, 2, 15, 0xFF8C5828.toInt())
                        rect(buf, S, 21, 7 + bob, 1, 13, 0xFFDCD3C2.toInt())
                    }
                }
            }
            Vocation.MAGE -> {
                val orbColor = if (frame == 0) 0xFF66E8FF.toInt() else 0xFFB899FF.toInt()
                when (direction) {
                    Direction.SOUTH -> {
                        rect(buf, S, 9, 11 + bob, 13, 13, 0xFF234794.toInt())
                        rect(buf, S, 11, 12 + bob, 9, 11, 0xFF3361BD.toInt())
                        rect(buf, S, 14, 12 + bob, 3, 11, 0xFFE6B44C.toInt())
                        rect(buf, S, 12, 6 + bob, 7, 5, skin)
                        px(buf, S, 13, 8 + bob, outline); px(buf, S, 17, 8 + bob, outline)
                        rect(buf, S, 8, 5 + bob, 15, 2, 0xFF1D397A.toInt())
                        rect(buf, S, 11, 2 + bob, 9, 3, 0xFF2C54AB.toInt())
                        rect(buf, S, 23, 6 + bob, 2, 19, 0xFF6B4226.toInt())
                        rect(buf, S, 22, 4 + bob, 4, 4, orbColor)
                    }
                    Direction.NORTH -> {
                        rect(buf, S, 9, 11 + bob, 13, 13, 0xFF1E3C7D.toInt())
                        rect(buf, S, 11, 12 + bob, 9, 11, 0xFF2B52A6.toInt())
                        rect(buf, S, 8, 5 + bob, 15, 2, 0xFF1D397A.toInt())
                        rect(buf, S, 11, 2 + bob, 9, 8, 0xFF2C54AB.toInt()) // wizard hat & cloak rear
                        rect(buf, S, 23, 6 + bob, 2, 19, 0xFF6B4226.toInt())
                        rect(buf, S, 22, 4 + bob, 4, 4, orbColor)
                    }
                    else -> { // EAST
                        rect(buf, S, 11, 11 + bob, 10, 13, 0xFF234794.toInt())
                        rect(buf, S, 14, 12 + bob, 6, 11, 0xFF3361BD.toInt())
                        rect(buf, S, 18, 12 + bob, 2, 11, 0xFFE6B44C.toInt())
                        rect(buf, S, 14, 6 + bob, 6, 5, skin)
                        px(buf, S, 18, 8 + bob, outline)
                        rect(buf, S, 9, 5 + bob, 13, 2, 0xFF1D397A.toInt())
                        rect(buf, S, 11, 2 + bob, 8, 3, 0xFF2C54AB.toInt())
                        // Staff held forward
                        rect(buf, S, 23, 6 + bob, 2, 19, 0xFF6B4226.toInt())
                        rect(buf, S, 22, 4 + bob, 4, 4, orbColor)
                    }
                }
            }
        }
        return buf
    }

    private fun buildMonster(type: MonsterType, frame: Int): ImageBitmap {
        val buf = buf32()
        val bob = if (frame == 1) -1 else 0
        when (type) {
            MonsterType.CAVE_RAT -> {
                drawShadow(buf, 8, 23, 15, 4)
                rect(buf, S, 22, 19 + bob, 5, 2, 0xFFD98B9A.toInt())
                rect(buf, S, 9, 14 + bob, 13, 9, 0xFF6E5038.toInt())
                rect(buf, S, 11, 13 + bob, 9, 6, 0xFF876549.toInt())
                rect(buf, S, 6, 16 + bob, 5, 5, 0xFF7D5D42.toInt())
                px(buf, S, 8, 17 + bob, 0xFFFF2B2B.toInt())
            }
            MonsterType.WILD_RABBIT -> {
                drawShadow(buf, 9, 24, 14, 4)
                val hop = if (frame == 1) -2 else 0
                val fur = 0xFFD9CDB8.toInt()
                val inner = 0xFFF2ECE1.toInt()
                rect(buf, S, 10, 15 + hop, 12, 9, fur)
                rect(buf, S, 12, 17 + hop, 8, 6, inner)
                rect(buf, S, 21, 17 + hop, 3, 3, 0xFFFFFFFF.toInt()) // fluffy tail
                rect(buf, S, 8, 11 + hop, 7, 6, fur) // head
                rect(buf, S, 9, 5 + hop, 2, 6, fur); rect(buf, S, 13, 5 + hop, 2, 6, fur) // tall ears
                px(buf, S, 10, 7 + hop, 0xFFE6A1AD.toInt()); px(buf, S, 14, 7 + hop, 0xFFE6A1AD.toInt())
                px(buf, S, 10, 13 + hop, 0xFF1F1815.toInt()) // dark eye
                px(buf, S, 8, 14 + hop, 0xFFE68A9A.toInt()) // pink nose
            }
            MonsterType.YOUNG_SNAKE -> {
                drawShadow(buf, 7, 24, 18, 4)
                val slither = if (frame == 1) 1 else -1
                val scale = 0xFF3E8C3A.toInt()
                val hi = 0xFF66B856.toInt()
                rect(buf, S, 8 + slither, 20, 15, 4, scale)
                rect(buf, S, 10 + slither, 21, 11, 2, hi)
                rect(buf, S, 16 - slither, 16, 6, 5, scale)
                rect(buf, S, 11, 11 + bob, 7, 6, scale) // raised hood/head
                rect(buf, S, 12, 12 + bob, 5, 3, hi)
                px(buf, S, 12, 13 + bob, 0xFFFFD43B.toInt()); px(buf, S, 16, 13 + bob, 0xFFFFD43B.toInt())
                rect(buf, S, 8, 14 + bob, 3, 1, 0xFFFF3344.toInt()) // forked tongue
            }
            MonsterType.CRAG_WOLF -> {
                drawShadow(buf, 6, 24, 20, 4)
                val fur = 0xFF6B6E75.toInt()
                val light = 0xFF9AA0AA.toInt()
                rect(buf, S, 8, 14 + bob, 16, 8, fur)
                rect(buf, S, 10, 13 + bob, 11, 5, light)
                rect(buf, S, 5, 11 + bob, 7, 7, fur)
                rect(buf, S, 6, 8 + bob, 2, 3, fur); rect(buf, S, 10, 8 + bob, 2, 3, fur)
                rect(buf, S, 8, 21 + (if (frame == 1) -1 else 0), 3, 5, 0xFF474A52.toInt())
                rect(buf, S, 19, 21 + (if (frame == 1) 1 else 0), 3, 5, 0xFF474A52.toInt())
                rect(buf, S, 24, 14 + bob, 4, 3, light)
                px(buf, S, 7, 13 + bob, 0xFFFFC933.toInt())
                px(buf, S, 6, 16 + bob, 0xFFFFFFFF.toInt())
            }
            MonsterType.SWAMP_TROLL -> {
                drawShadow(buf, 6, 25, 20, 5)
                val hide = 0xFF4F6B43.toInt()
                val moss = 0xFF709459.toInt()
                rect(buf, S, 8, 19 + bob, 5, 8, hide); rect(buf, S, 19, 19 + bob, 5, 8, hide)
                rect(buf, S, 7, 9 + bob, 18, 11, hide)
                rect(buf, S, 9, 10 + bob, 14, 8, moss)
                rect(buf, S, 11, 3 + bob, 10, 7, hide)
                rect(buf, S, 10, 2 + bob, 12, 2, 0xFF39522F.toInt()) // swamp moss hair
                px(buf, S, 13, 6 + bob, 0xFFFFB333.toInt()); px(buf, S, 18, 6 + bob, 0xFFFFB333.toInt())
                rect(buf, S, 24, 10 + bob, 4, 12, 0xFF6B4423.toInt()) // heavy swamp club
            }
            MonsterType.SKELETON -> {
                drawShadow(buf, 8, 25, 16, 4)
                val bone = 0xFFE8E2D5.toInt()
                rect(buf, S, 11, 19 + bob, 3, 7, 0xFFADA699.toInt()); rect(buf, S, 17, 19 + bob, 3, 7, 0xFFADA699.toInt())
                rect(buf, S, 10, 11 + bob, 11, 8, 0xFF24211E.toInt())
                rect(buf, S, 11, 12 + bob, 9, 2, bone); rect(buf, S, 11, 15 + bob, 9, 2, bone)
                rect(buf, S, 11, 3 + bob, 9, 8, bone)
                rect(buf, S, 12, 6 + bob, 2, 2, 0xFF14100E.toInt()); rect(buf, S, 17, 6 + bob, 2, 2, 0xFF14100E.toInt())
                rect(buf, S, 5, 8 + bob, 3, 11, 0xFF94745E.toInt())
                rect(buf, S, 22, 12 + bob, 6, 9, 0xFF6B533B.toInt())
            }
            MonsterType.GOBLIN_SCOUT -> {
                drawShadow(buf, 8, 24, 16, 4)
                val skin = 0xFF5D8F3B.toInt()
                val tunic = 0xFF7D532B.toInt()
                rect(buf, S, 11, 20 + bob, 3, 6, skin); rect(buf, S, 17, 20 + bob, 3, 6, skin)
                rect(buf, S, 10, 13 + bob, 11, 8, tunic)
                rect(buf, S, 11, 6 + bob, 9, 7, skin) // goblin head
                rect(buf, S, 7, 7 + bob, 4, 2, skin); rect(buf, S, 20, 7 + bob, 4, 2, skin) // pointy ears
                px(buf, S, 13, 9 + bob, 0xFFFFD43B.toInt()); px(buf, S, 17, 9 + bob, 0xFFFFD43B.toInt())
                // Sling & stone pouch
                rect(buf, S, 22, 11 + bob, 4, 4, 0xFF9BA3AD.toInt())
                rect(buf, S, 5, 13 + bob, 4, 5, 0xFF543318.toInt())
            }
            MonsterType.OUTLAW_BANDIT -> {
                drawShadow(buf, 7, 25, 18, 5)
                val cloak = 0xFF7D2E28.toInt()
                val leather = 0xFF5E412A.toInt()
                rect(buf, S, 10, 20 + bob, 4, 7, 0xFF33261E.toInt()); rect(buf, S, 18, 20 + bob, 4, 7, 0xFF33261E.toInt())
                rect(buf, S, 8, 11 + bob, 16, 10, leather)
                rect(buf, S, 9, 10 + bob, 14, 3, cloak) // crimson scarf
                rect(buf, S, 11, 4 + bob, 10, 7, 0xFFD9B08C.toInt())
                rect(buf, S, 10, 3 + bob, 12, 3, cloak) // bandit hood
                rect(buf, S, 11, 8 + bob, 10, 2, cloak) // face mask
                px(buf, S, 13, 6 + bob, 0xFF1A1412.toInt()); px(buf, S, 18, 6 + bob, 0xFF1A1412.toInt())
                rect(buf, S, 24, 6 + bob, 3, 14, 0xFFD4DCE6.toInt()) // notched cutlass
                rect(buf, S, 5, 13 + bob, 4, 7, 0xFF7A5533.toInt()) // buckler
            }
            MonsterType.OUTLAW_ROGUE -> {
                drawShadow(buf, 7, 25, 18, 5)
                val cowl = 0xFF2B3832.toInt()
                val vest = 0xFF38473F.toInt()
                rect(buf, S, 10, 20 + bob, 4, 7, 0xFF1F2624.toInt()); rect(buf, S, 18, 20 + bob, 4, 7, 0xFF1F2624.toInt())
                rect(buf, S, 9, 11 + bob, 14, 10, vest)
                rect(buf, S, 10, 3 + bob, 12, 8, cowl) // dark hooded cowl
                rect(buf, S, 12, 6 + bob, 8, 3, 0xFF171A19.toInt())
                px(buf, S, 13, 7 + bob, 0xFF9EFFB2.toInt()); px(buf, S, 18, 7 + bob, 0xFF9EFFB2.toInt())
                rect(buf, S, 24, 6 + bob, 3, 15, 0xFF9C7242.toInt()) // recurve shortbow
                rect(buf, S, 5, 11 + bob, 3, 9, 0xFFC5D0DC.toInt()) // offhand dirk
            }
            MonsterType.POISON_SPIDER -> {
                drawShadow(buf, 6, 23, 20, 5)
                val legShift = if (frame == 0) 0 else 2
                for (i in 0..3) {
                    val ly = 11 + i * 4
                    rect(buf, S, 4, ly + (if (i % 2 == 0) legShift else -legShift) / 2, 7, 2, 0xFF1D2419.toInt())
                    rect(buf, S, 21, ly + (if (i % 2 == 1) legShift else -legShift) / 2, 7, 2, 0xFF1D2419.toInt())
                }
                rect(buf, S, 10, 9 + bob, 12, 10, 0xFF283321.toInt())
                rect(buf, S, 13, 11 + bob, 6, 4, 0xFF5EE034.toInt()) // venom abdomen mark
                rect(buf, S, 11, 18 + bob, 10, 6, 0xFF1E261A.toInt())
                px(buf, S, 13, 20 + bob, 0xFF8CFF33.toInt()); px(buf, S, 18, 20 + bob, 0xFF8CFF33.toInt())
            }
            MonsterType.GOREK_RAIDER, MonsterType.GOREK_PHALANX, MonsterType.GOREK_HEX_CALLER, MonsterType.VAELGOR_LABYRINTH_WARDEN -> {
                drawShadow(buf, 4, 25, 24, 6)
                val isBoss = type == MonsterType.VAELGOR_LABYRINTH_WARDEN
                val armor = if (isBoss) 0xFF211824.toInt() else 0xFF342C3D.toInt()
                val trim = if (isBoss) 0xFFFFB238.toInt() else 0xFFD9953B.toInt()
                val eye = if (isBoss) 0xFFFFF08A.toInt() else 0xFFFF8C24.toInt()
                rect(buf, S, 8, 20 + bob, 5, 7, 0xFF261E29.toInt()); rect(buf, S, 19, 20 + bob, 5, 7, 0xFF261E29.toInt())
                rect(buf, S, 7, 10 + bob, 18, 11, armor)
                rect(buf, S, 11, 12 + bob, 10, 6, trim)
                rect(buf, S, 10, 3 + bob, 12, 8, 0xFF4A342E.toInt())
                // Sweeping obsidian curved horns
                rect(buf, S, 5, 1 + bob, 5, 4, 0xFF1D1721.toInt()); rect(buf, S, 22, 1 + bob, 5, 4, 0xFF1D1721.toInt())
                px(buf, S, 6, 1 + bob, trim); px(buf, S, 25, 1 + bob, trim)
                px(buf, S, 13, 6 + bob, eye); px(buf, S, 18, 6 + bob, eye)
                when (type) {
                    MonsterType.GOREK_PHALANX -> {
                        rect(buf, S, 3, 10 + bob, 5, 12, armor) // obsidian tower shield
                        rect(buf, S, 25, 4 + bob, 3, 18, trim) // pike
                    }
                    MonsterType.GOREK_HEX_CALLER -> {
                        rect(buf, S, 24, 5 + bob, 3, 17, 0xFF3E2638.toInt())
                        rect(buf, S, 23, 3 + bob, 5, 4, 0xFFFF6D1A.toInt()) // hex brazier
                    }
                    else -> {
                        rect(buf, S, 25, 5 + bob, 5, 15, if (isBoss) 0xFFFF7A1F.toInt() else 0xFF8C7E9E.toInt())
                    }
                }
            }
            MonsterType.ORC_WARRIOR -> {
                drawShadow(buf, 6, 25, 20, 5)
                val skin = 0xFF477A3B.toInt()
                val steel = 0xFF5D6570.toInt()
                rect(buf, S, 9, 20 + bob, 5, 7, 0xFF3E3A36.toInt()); rect(buf, S, 18, 20 + bob, 5, 7, 0xFF3E3A36.toInt())
                rect(buf, S, 7, 10 + bob, 18, 11, steel)
                rect(buf, S, 10, 11 + bob, 12, 7, 0xFF7B8494.toInt())
                rect(buf, S, 10, 3 + bob, 12, 7, skin)
                rect(buf, S, 10, 2 + bob, 12, 3, steel) // iron warhelm
                px(buf, S, 12, 9 + bob, 0xFFFFFFFF.toInt()); px(buf, S, 19, 9 + bob, 0xFFFFFFFF.toInt()) // tusks
                px(buf, S, 13, 6 + bob, 0xFFFF4D2E.toInt()); px(buf, S, 18, 6 + bob, 0xFFFF4D2E.toInt())
                rect(buf, S, 4, 7 + bob, 3, 14, 0xFFD0D8E2.toInt()) // orc saber
                rect(buf, S, 24, 12 + bob, 5, 9, 0xFF7D502E.toInt()) // iron-rimmed shield
            }
            MonsterType.GHOUL -> {
                drawShadow(buf, 8, 25, 16, 5)
                rect(buf, S, 9, 11 + bob, 14, 14, 0xFF393E47.toInt())
                rect(buf, S, 11, 12 + bob, 10, 10, 0xFF5D7868.toInt())
                rect(buf, S, 11, 4 + bob, 10, 8, 0xFF779481.toInt())
                rect(buf, S, 13, 7 + bob, 2, 2, 0xFF8CFF4D.toInt()); rect(buf, S, 17, 7 + bob, 2, 2, 0xFF8CFF4D.toInt())
            }
            MonsterType.FIRE_DEVIL -> {
                drawShadow(buf, 8, 25, 16, 4)
                val crimson = 0xFFB82424.toInt()
                val flame = if (frame == 0) 0xFFFF6E1A.toInt() else 0xFFFFB833.toInt()
                rect(buf, S, 10, 19 + bob, 4, 7, 0xFF7D1616.toInt()); rect(buf, S, 18, 19 + bob, 4, 7, 0xFF7D1616.toInt())
                rect(buf, S, 9, 10 + bob, 14, 10, crimson)
                rect(buf, S, 11, 12 + bob, 10, 6, flame) // fiery core
                rect(buf, S, 11, 3 + bob, 10, 7, crimson)
                rect(buf, S, 10, 0 + bob, 2, 4, 0xFF2B1B1B.toInt()); rect(buf, S, 20, 0 + bob, 2, 4, 0xFF2B1B1B.toInt()) // devil horns
                px(buf, S, 13, 6 + bob, 0xFFFFE57F.toInt()); px(buf, S, 18, 6 + bob, 0xFFFFE57F.toInt())
                rect(buf, S, 23, 8 + bob, 5, 5, flame) // floating fireball
            }
            MonsterType.GRIMMAW_QUARRYMAN, MonsterType.GRIMMAW_ROCK_HURLER, MonsterType.GRIMMAW_CRUSHER, MonsterType.KRAGOR_THE_STONE_EYED -> {
                drawShadow(buf, 4, 25, 24, 6)
                val isBoss = type == MonsterType.KRAGOR_THE_STONE_EYED
                val stone = if (isBoss) 0xFF534C6B.toInt() else 0xFF6E727D.toInt()
                val hi = if (isBoss) 0xFF9C8CE0.toInt() else 0xFF9EA3B0.toInt()
                val eyeCore = if (isBoss) 0xFFB58CFF.toInt() else 0xFFFF9826.toInt()
                rect(buf, S, 8, 20 + bob, 6, 8, stone); rect(buf, S, 18, 20 + bob, 6, 8, stone)
                rect(buf, S, 8, 17 + bob, 16, 5, 0xFF353740.toInt())
                rect(buf, S, 6, 7 + bob, 20, 11, stone); rect(buf, S, 9, 8 + bob, 14, 8, hi)
                rect(buf, S, 11, 1 + bob, 10, 7, hi)
                // Single luminous subterranean Grimmaw stone-eye
                rect(buf, S, 14, 3 + bob, 4, 3, 0xFFFFF4E0.toInt()); rect(buf, S, 15, 4 + bob, 2, 2, eyeCore)
                if (isBoss) {
                    rect(buf, S, 13, 0 + bob, 6, 2, 0xFFC7A8FF.toInt()) // Vesperite crystal brow crown
                    rect(buf, S, 24, 3 + bob, 6, 17, 0xFF8C6BFF.toInt()) // Vesperite monolith maul
                } else {
                    rect(buf, S, 25, 4 + bob, 5, 16, 0xFF5D626E.toInt())
                }
            }
            MonsterType.DRAGON_HATCHLING -> {
                drawShadow(buf, 7, 24, 18, 5)
                val wy = if (frame == 0) 7 else 10
                rect(buf, S, 4, wy, 7, 9, 0xFF9E2B2B.toInt()); rect(buf, S, 21, wy, 7, 9, 0xFF9E2B2B.toInt())
                rect(buf, S, 10, 11 + bob, 12, 12, 0xFF288A47.toInt())
                rect(buf, S, 12, 13 + bob, 8, 8, 0xFFE0B44C.toInt())
                rect(buf, S, 11, 5 + bob, 10, 7, 0xFF39A85C.toInt())
                px(buf, S, 13, 7 + bob, 0xFFFF661A.toInt()); px(buf, S, 18, 7 + bob, 0xFFFF661A.toInt())
            }
            MonsterType.NECROMANCER -> {
                drawShadow(buf, 8, 25, 16, 5)
                val cloak = 0xFF2B1C3D.toInt()
                val trim = 0xFF6B3AA6.toInt()
                val glow = if (frame == 0) 0xFFC77DFF.toInt() else 0xFFFF4D6D.toInt()
                rect(buf, S, 8, 10 + bob, 16, 16, cloak)
                rect(buf, S, 13, 11 + bob, 6, 14, trim)
                rect(buf, S, 10, 3 + bob, 12, 8, 0xFF1A1226.toInt()) // hooded face
                rect(buf, S, 12, 6 + bob, 2, 2, glow); rect(buf, S, 18, 6 + bob, 2, 2, glow)
                rect(buf, S, 24, 6 + bob, 2, 19, 0xFF473A52.toInt()) // skull staff
                rect(buf, S, 23, 3 + bob, 4, 4, 0xFFE8E2D5.toInt())
            }
            MonsterType.DEMON_LORD -> {
                drawShadow(buf, 3, 25, 26, 6)
                val wingY = if (frame == 0) 5 else 8
                val skin = 0xFFA6171F.toInt()
                val armor = 0xFF261C24.toInt()
                val hellfire = if (frame == 0) 0xFFFF6E1A.toInt() else 0xFFFFD447.toInt()
                // Bat-like abyssal wings
                rect(buf, S, 1, wingY, 8, 12, 0xFF3D141C.toInt()); rect(buf, S, 23, wingY, 8, 12, 0xFF3D141C.toInt())
                rect(buf, S, 1, wingY, 8, 2, 0xFF7D1922.toInt()); rect(buf, S, 23, wingY, 8, 2, 0xFF7D1922.toInt())
                // Broad demon torso + obsidian pauldrons
                rect(buf, S, 8, 10 + bob, 16, 15, skin)
                rect(buf, S, 8, 10 + bob, 16, 4, armor)
                rect(buf, S, 12, 14 + bob, 8, 7, hellfire)
                // Horned demonic crown
                rect(buf, S, 10, 2 + bob, 12, 8, skin)
                rect(buf, S, 7, 0 + bob, 4, 5, 0xFFE6D4A8.toInt()); rect(buf, S, 21, 0 + bob, 4, 5, 0xFFE6D4A8.toInt())
                rect(buf, S, 12, 5 + bob, 2, 2, 0xFFFFE57F.toInt()); rect(buf, S, 18, 5 + bob, 2, 2, 0xFFFFE57F.toInt())
            }
            MonsterType.ELDER_DRAGON -> {
                drawShadow(buf, 3, 25, 26, 6)
                val wingY = if (frame == 0) 4 else 7
                rect(buf, S, 1, wingY, 9, 13, 0xFF8C2424.toInt()); rect(buf, S, 22, wingY, 9, 13, 0xFF8C2424.toInt())
                rect(buf, S, 1, wingY, 9, 2, 0xFF1F7A3D.toInt()); rect(buf, S, 22, wingY, 9, 2, 0xFF1F7A3D.toInt())
                rect(buf, S, 8, 10 + bob, 16, 15, 0xFF1F7A3D.toInt())
                rect(buf, S, 11, 13 + bob, 10, 11, 0xFFE0B44C.toInt())
                rect(buf, S, 10, 2 + bob, 12, 9, 0xFF34A857.toInt())
                rect(buf, S, 9, 0 + bob, 3, 4, 0xFFE6D4A8.toInt()); rect(buf, S, 20, 0 + bob, 3, 4, 0xFFE6D4A8.toInt())
                rect(buf, S, 12, 5 + bob, 2, 2, 0xFFFF551A.toInt()); rect(buf, S, 18, 5 + bob, 2, 2, 0xFFFF551A.toInt())
            }
            MonsterType.BROODMOTHER_ARACHNE -> {
                drawShadow(buf, 3, 25, 26, 6)
                val carapace = 0xFF1F1629.toInt()
                val crimson = 0xFF8C1D3A.toInt()
                val venom = if (frame == 0) 0xFF6CFF2B.toInt() else 0xFFA6FF66.toInt()
                // Spiked arachnid legs
                for (ly in listOf(12, 16, 20)) {
                    rect(buf, S, 2, ly + bob, 7, 2, carapace)
                    rect(buf, S, 23, ly + bob, 7, 2, carapace)
                    px(buf, S, 4, ly + bob, venom); px(buf, S, 27, ly + bob, venom)
                }
                // Colossal abdomen & venom hourglass
                rect(buf, S, 8, 9 + bob, 16, 13, carapace)
                rect(buf, S, 10, 11 + bob, 12, 9, crimson)
                rect(buf, S, 13, 13 + bob, 6, 5, venom)
                // Crowned head & 6 venom eyes
                rect(buf, S, 10, 4 + bob, 12, 7, carapace)
                rect(buf, S, 12, 6 + bob, 2, 2, venom); rect(buf, S, 15, 5 + bob, 2, 2, venom); rect(buf, S, 18, 6 + bob, 2, 2, venom)
            }
            MonsterType.IGNIS_LORD_OF_THE_PIT -> {
                drawShadow(buf, 3, 25, 26, 6)
                val basalt = 0xFF282026.toInt()
                val lava = if (frame == 0) 0xFFFF5A1F.toInt() else 0xFFFFB833.toInt()
                val core = 0xFFFFEE88.toInt()
                // Molten pauldrons & horned silhouette
                rect(buf, S, 4, 10 + bob, 6, 12, basalt); rect(buf, S, 22, 10 + bob, 6, 12, basalt)
                rect(buf, S, 5, 11 + bob, 4, 4, lava); rect(buf, S, 23, 11 + bob, 4, 4, lava)
                // Basalt torso with molten magma core
                rect(buf, S, 9, 9 + bob, 14, 16, basalt)
                rect(buf, S, 12, 12 + bob, 8, 9, lava)
                rect(buf, S, 14, 14 + bob, 4, 5, core)
                // Volcanic horned helm & blazing eyes
                rect(buf, S, 10, 3 + bob, 12, 7, basalt)
                rect(buf, S, 7, 0 + bob, 3, 5, lava); rect(buf, S, 22, 0 + bob, 3, 5, lava)
                rect(buf, S, 12, 5 + bob, 2, 2, core); rect(buf, S, 18, 5 + bob, 2, 2, core)
            }
            // --- NEW DUNGEON-SPECIFIC MONSTERS & LOWER-FLOOR BOSSES ---
            MonsterType.BRIAR_CRYPT_CRAWLER -> {
                drawShadow(buf, 6, 23, 20, 5)
                val shell = 0xFF2D4029.toInt()
                val moss = 0xFF5CA347.toInt()
                val venom = 0xFFA2FF4A.toInt()
                for (i in 0..3) {
                    val ly = 11 + i * 4
                    rect(buf, S, 4, ly + bob, 6, 2, 0xFF1F2B1C.toInt())
                    rect(buf, S, 22, ly + bob, 6, 2, 0xFF1F2B1C.toInt())
                }
                rect(buf, S, 9, 10 + bob, 14, 12, shell)
                rect(buf, S, 11, 12 + bob, 10, 6, moss)
                px(buf, S, 13, 20 + bob, venom); px(buf, S, 18, 20 + bob, venom)
            }
            MonsterType.CATACOMB_BONE_ARCHER -> {
                drawShadow(buf, 8, 25, 16, 4)
                val bone = 0xFFE3DAC9.toInt()
                rect(buf, S, 11, 19 + bob, 3, 7, 0xFF9C9486.toInt()); rect(buf, S, 17, 19 + bob, 3, 7, 0xFF9C9486.toInt())
                rect(buf, S, 10, 11 + bob, 11, 8, 0xFF334230.toInt())
                rect(buf, S, 11, 3 + bob, 9, 8, bone)
                px(buf, S, 13, 6 + bob, 0xFF7BFF5C.toInt()); px(buf, S, 18, 6 + bob, 0xFF7BFF5C.toInt())
                rect(buf, S, 23, 6 + bob, 2, 16, 0xFF8C5D32.toInt())
                rect(buf, S, 22, 8 + bob, 1, 12, 0xFFE8E2D5.toInt())
            }
            MonsterType.TUNNEL_KOBOLD_SAPPER -> {
                drawShadow(buf, 8, 24, 16, 4)
                val skin = 0xFF9E5835.toInt()
                val helm = 0xFFC78338.toInt()
                rect(buf, S, 11, 20 + bob, 3, 6, skin); rect(buf, S, 17, 20 + bob, 3, 6, skin)
                rect(buf, S, 10, 12 + bob, 11, 9, 0xFF543C29.toInt())
                rect(buf, S, 11, 5 + bob, 9, 7, skin)
                rect(buf, S, 10, 4 + bob, 11, 3, helm)
                px(buf, S, 15, 4 + bob, 0xFFFFEA66.toInt()) // miner candle
                px(buf, S, 13, 8 + bob, 0xFFFFB833.toInt()); px(buf, S, 17, 8 + bob, 0xFFFFB833.toInt())
                rect(buf, S, 23, 10 + bob, 4, 5, 0xFFFF6E24.toInt())
            }
            MonsterType.QUARRY_MOLE_BRUTE -> {
                drawShadow(buf, 6, 25, 20, 5)
                val fur = 0xFF59493E.toInt()
                val snout = 0xFFD99A8C.toInt()
                rect(buf, S, 8, 19 + bob, 5, 8, fur); rect(buf, S, 19, 19 + bob, 5, 8, fur)
                rect(buf, S, 7, 9 + bob, 18, 12, fur)
                rect(buf, S, 10, 4 + bob, 12, 7, 0xFF705D50.toInt())
                rect(buf, S, 13, 8 + bob, 6, 3, snout)
                rect(buf, S, 4, 13 + bob, 4, 7, 0xFFD9D2C5.toInt()); rect(buf, S, 24, 13 + bob, 4, 7, 0xFFD9D2C5.toInt())
            }
            MonsterType.CRAG_HARPY, MonsterType.SKREE_RIDGE_MATRIARCH -> {
                val isBoss = type == MonsterType.SKREE_RIDGE_MATRIARCH
                drawShadow(buf, 5, 25, 22, 5)
                val feather = if (isBoss) 0xFF6B3C7D.toInt() else 0xFF6A7A8C.toInt()
                val hi = if (isBoss) 0xFFE6A84C.toInt() else 0xFFAAB8C7.toInt()
                val wy = if (frame == 0) 6 else 9
                rect(buf, S, 2, wy, 7, 11, feather); rect(buf, S, 23, wy, 7, 11, feather)
                rect(buf, S, 10, 11 + bob, 12, 12, feather)
                rect(buf, S, 12, 13 + bob, 8, 7, hi)
                rect(buf, S, 11, 4 + bob, 10, 7, hi)
                px(buf, S, 13, 7 + bob, 0xFFFFD43B.toInt()); px(buf, S, 18, 7 + bob, 0xFFFFD43B.toInt())
                rect(buf, S, 15, 8 + bob, 2, 3, 0xFFE6A133.toInt())
            }
            MonsterType.SHALE_GOLEM, MonsterType.FOREMAN_IRONJAW, MonsterType.WARDEN_STONEHELM -> {
                val isBoss = type.isBoss
                drawShadow(buf, 5, 25, 22, 5)
                val body = when (type) {
                    MonsterType.FOREMAN_IRONJAW -> 0xFF9C5E33.toInt()
                    MonsterType.WARDEN_STONEHELM -> 0xFF4F5966.toInt()
                    else -> 0xFF6D7378.toInt()
                }
                val trim = when (type) {
                    MonsterType.FOREMAN_IRONJAW -> 0xFFFFB240.toInt()
                    MonsterType.WARDEN_STONEHELM -> 0xFF99CCFF.toInt()
                    else -> 0xFF9DA5AD.toInt()
                }
                rect(buf, S, 8, 20 + bob, 6, 7, body); rect(buf, S, 18, 20 + bob, 6, 7, body)
                rect(buf, S, 6, 9 + bob, 20, 12, body)
                rect(buf, S, 10, 11 + bob, 12, 7, trim)
                rect(buf, S, 10, 2 + bob, 12, 7, body)
                if (isBoss) rect(buf, S, 11, 1 + bob, 10, 2, trim)
                px(buf, S, 13, 5 + bob, 0xFFFFE57F.toInt()); px(buf, S, 18, 5 + bob, 0xFFFFE57F.toInt())
                rect(buf, S, 24, 5 + bob, 5, 16, trim)
            }
            MonsterType.THORN_BRAMBLE_STALKER, MonsterType.SPORELING_LOBBER, MonsterType.GNARLROOT_THE_BLIGHTED -> {
                val isBoss = type == MonsterType.GNARLROOT_THE_BLIGHTED
                drawShadow(buf, 5, 25, 22, 5)
                val bark = if (isBoss) 0xFF38261E.toInt() else 0xFF4A3728.toInt()
                val spore = if (type == MonsterType.SPORELING_LOBBER) 0xFFC77DFF.toInt() else 0xFF6CD936.toInt()
                rect(buf, S, 9, 19 + bob, 5, 8, bark); rect(buf, S, 18, 19 + bob, 5, 8, bark)
                rect(buf, S, 7, 9 + bob, 18, 11, bark)
                rect(buf, S, 10, 11 + bob, 12, 7, spore)
                rect(buf, S, 10, 3 + bob, 12, 7, bark)
                if (isBoss) {
                    rect(buf, S, 6, 1 + bob, 4, 5, bark); rect(buf, S, 22, 1 + bob, 4, 5, bark)
                }
                px(buf, S, 13, 6 + bob, 0xFFB8FF4D.toInt()); px(buf, S, 18, 6 + bob, 0xFFB8FF4D.toInt())
            }
            MonsterType.VAULT_SENTINEL, MonsterType.HOLLOW_CROSSBOWMAN, MonsterType.WATCHWOOD_DREAD_KNIGHT -> {
                drawShadow(buf, 7, 25, 18, 5)
                val plate = when (type) {
                    MonsterType.WATCHWOOD_DREAD_KNIGHT -> 0xFF2B2433.toInt()
                    MonsterType.HOLLOW_CROSSBOWMAN -> 0xFF47524A.toInt()
                    else -> 0xFF5D6A78.toInt()
                }
                val accent = when (type) {
                    MonsterType.WATCHWOOD_DREAD_KNIGHT -> 0xFFFF5A36.toInt()
                    MonsterType.HOLLOW_CROSSBOWMAN -> 0xFFD4A23C.toInt()
                    else -> 0xFFAEC2D6.toInt()
                }
                rect(buf, S, 10, 20 + bob, 4, 7, plate); rect(buf, S, 18, 20 + bob, 4, 7, plate)
                rect(buf, S, 8, 10 + bob, 16, 11, plate)
                rect(buf, S, 11, 12 + bob, 10, 6, accent)
                rect(buf, S, 11, 3 + bob, 10, 7, plate)
                px(buf, S, 13, 6 + bob, accent); px(buf, S, 18, 6 + bob, accent)
                rect(buf, S, 24, 7 + bob, 3, 14, accent)
            }
            MonsterType.CINDER_IMP -> {
                drawShadow(buf, 9, 24, 14, 4)
                val imp = 0xFFD93B1E.toInt()
                val glow = if (frame == 0) 0xFFFF9A2B.toInt() else 0xFFFFE066.toInt()
                rect(buf, S, 11, 19 + bob, 3, 6, imp); rect(buf, S, 17, 19 + bob, 3, 6, imp)
                rect(buf, S, 10, 12 + bob, 11, 8, imp)
                rect(buf, S, 12, 14 + bob, 7, 4, glow)
                rect(buf, S, 11, 5 + bob, 9, 7, imp)
                px(buf, S, 13, 8 + bob, 0xFFFFF08A.toInt()); px(buf, S, 17, 8 + bob, 0xFFFFF08A.toInt())
                rect(buf, S, 23, 9 + bob, 4, 4, glow)
            }
            MonsterType.MIRE_LURKER, MonsterType.BOG_LEECH_WEAVER, MonsterType.MIREMAW_THE_DROWNED -> {
                val isBoss = type == MonsterType.MIREMAW_THE_DROWNED
                drawShadow(buf, 5, 25, 22, 5)
                val scale = if (isBoss) 0xFF1F4D47.toInt() else 0xFF2E6B59.toInt()
                val fin = if (isBoss) 0xFF66FFE0.toInt() else 0xFF7BFF70.toInt()
                rect(buf, S, 9, 19 + bob, 5, 8, scale); rect(buf, S, 18, 19 + bob, 5, 8, scale)
                rect(buf, S, 7, 9 + bob, 18, 12, scale)
                rect(buf, S, 10, 11 + bob, 12, 6, fin)
                rect(buf, S, 10, 3 + bob, 12, 7, scale)
                px(buf, S, 13, 6 + bob, 0xFFFFE57F.toInt()); px(buf, S, 18, 6 + bob, 0xFFFFE57F.toInt())
            }
            MonsterType.TEMPLE_SERPENT_GUARD, MonsterType.SUN_PRIEST_ACOLYTE, MonsterType.XOLATL_THE_SUN_FANG -> {
                val isBoss = type == MonsterType.XOLATL_THE_SUN_FANG
                drawShadow(buf, 5, 25, 22, 5)
                val jade = if (isBoss) 0xFF1D7A54.toInt() else 0xFF2F8C5A.toInt()
                val gold = 0xFFFFC947.toInt()
                rect(buf, S, 9, 19 + bob, 5, 8, jade); rect(buf, S, 18, 19 + bob, 5, 8, jade)
                rect(buf, S, 8, 10 + bob, 16, 11, jade)
                rect(buf, S, 11, 11 + bob, 10, 8, gold)
                rect(buf, S, 10, 3 + bob, 12, 7, jade)
                rect(buf, S, 8, 1 + bob, 16, 2, gold) // solar crest
                px(buf, S, 13, 6 + bob, 0xFFFF5A1F.toInt()); px(buf, S, 18, 6 + bob, 0xFFFF5A1F.toInt())
                rect(buf, S, 24, 5 + bob, 3, 17, gold)
            }
            MonsterType.BARROW_WIGHT, MonsterType.FROST_BANSHEE, MonsterType.LORD_MALGRATH_THE_UNDYING -> {
                val isBoss = type == MonsterType.LORD_MALGRATH_THE_UNDYING
                drawShadow(buf, 6, 25, 20, 5)
                val cloak = if (isBoss) 0xFF1B243B.toInt() else 0xFF2C3E5E.toInt()
                val frost = 0xFF7BE8FF.toInt()
                rect(buf, S, 8, 10 + bob, 16, 16, cloak)
                rect(buf, S, 12, 12 + bob, 8, 12, frost)
                rect(buf, S, 10, 3 + bob, 12, 7, 0xFFDCEBF5.toInt())
                if (isBoss) rect(buf, S, 9, 1 + bob, 14, 2, frost)
                px(buf, S, 13, 6 + bob, frost); px(buf, S, 18, 6 + bob, frost)
                rect(buf, S, 24, 5 + bob, 3, 17, frost)
            }
            MonsterType.SLAG_AUTOMATON, MonsterType.ASH_PYROMANCER, MonsterType.OBSIDIAN_CULTIST, MonsterType.VULKARIS_THE_FORGE_TITAN -> {
                val isBoss = type == MonsterType.VULKARIS_THE_FORGE_TITAN
                drawShadow(buf, 4, 25, 24, 6)
                val iron = 0xFF2E272B.toInt()
                val magma = if (frame == 0) 0xFFFF6E1A.toInt() else 0xFFFFC233.toInt()
                rect(buf, S, 8, 20 + bob, 6, 7, iron); rect(buf, S, 18, 20 + bob, 6, 7, iron)
                rect(buf, S, 6, 9 + bob, 20, 12, iron)
                rect(buf, S, 11, 12 + bob, 10, 7, magma)
                rect(buf, S, 10, 2 + bob, 12, 7, iron)
                if (isBoss) rect(buf, S, 8, 0 + bob, 16, 3, magma)
                px(buf, S, 13, 5 + bob, 0xFFFFEE88.toInt()); px(buf, S, 18, 5 + bob, 0xFFFFEE88.toInt())
                rect(buf, S, 24, 4 + bob, 5, 17, magma)
            }
            MonsterType.EMBER_DRAKE, MonsterType.IGNIVAR_ASH_WING_WYRM -> {
                val isBoss = type == MonsterType.IGNIVAR_ASH_WING_WYRM
                drawShadow(buf, 3, 25, 26, 6)
                val wy = if (frame == 0) 5 else 8
                val scale = if (isBoss) 0xFF331C24.toInt() else 0xFF8C261F.toInt()
                val ember = 0xFFFF7A1F.toInt()
                rect(buf, S, 1, wy, 9, 12, scale); rect(buf, S, 22, wy, 9, 12, scale)
                rect(buf, S, 1, wy, 9, 2, ember); rect(buf, S, 22, wy, 9, 2, ember)
                rect(buf, S, 8, 10 + bob, 16, 14, scale)
                rect(buf, S, 11, 13 + bob, 10, 9, ember)
                rect(buf, S, 10, 2 + bob, 12, 8, scale)
                rect(buf, S, 12, 5 + bob, 2, 2, 0xFFFFEE88.toInt()); rect(buf, S, 18, 5 + bob, 2, 2, 0xFFFFEE88.toInt())
            }
        }
        return toBmp(buf, S)
    }

    private fun buildNpc(role: NpcRole): ImageBitmap {
        val buf = buf32()
        drawShadow(buf)
        val skin = 0xFFE8BE96.toInt()
        when (role) {
            NpcRole.SHOP_ALCHEMIST -> {
                rect(buf, S, 9, 11, 14, 15, 0xFF54307A.toInt()); rect(buf, S, 14, 12, 4, 13, 0xFFE6B44C.toInt())
                rect(buf, S, 11, 4, 10, 8, skin); rect(buf, S, 10, 3, 12, 3, 0xFF472569.toInt())
                rect(buf, S, 5, 14, 4, 6, 0xFF4DE07A.toInt())
            }
            NpcRole.TEMPLE_PRIEST -> {
                rect(buf, S, 9, 11, 14, 15, 0xFFDCD3C2.toInt()); rect(buf, S, 13, 11, 6, 15, 0xFFE6B44C.toInt())
                rect(buf, S, 11, 4, 10, 8, skin); rect(buf, S, 12, 1, 8, 1, 0xFFFFDF73.toInt())
            }
            NpcRole.ORACLE -> {
                // Iconic Rookvale golden-marble winged Oracle statue with arcane sapphire glow
                rect(buf, S, 6, 22, 20, 6, 0xFF7D776E.toInt()) // stone pedestal
                rect(buf, S, 8, 21, 16, 2, 0xFFE6B44C.toInt()) // gold trim
                // Sculpted marble wings
                rect(buf, S, 4, 9, 6, 10, 0xFFEDE6D6.toInt())
                rect(buf, S, 22, 9, 6, 10, 0xFFEDE6D6.toInt())
                rect(buf, S, 5, 10, 4, 7, 0xFFD9CFAF.toInt())
                rect(buf, S, 23, 10, 4, 7, 0xFFD9CFAF.toInt())
                // Marble pillar figure & golden stole
                rect(buf, S, 10, 9, 12, 13, 0xFFE8E2D5.toInt())
                rect(buf, S, 14, 10, 4, 11, 0xFFE6B44C.toInt())
                // Crowned Oracle head + luminous arcane eyes
                rect(buf, S, 11, 3, 10, 7, 0xFFF5EFE0.toInt())
                rect(buf, S, 10, 2, 12, 2, 0xFFFFD447.toInt())
                px(buf, S, 15, 1, 0xFF5CE1FF.toInt()); px(buf, S, 16, 1, 0xFF5CE1FF.toInt())
                rect(buf, S, 13, 6, 2, 1, 0xFF5CE1FF.toInt()); rect(buf, S, 17, 6, 2, 1, 0xFF5CE1FF.toInt())
            }
            NpcRole.HARBOR_CAPTAIN -> {
                rect(buf, S, 9, 11, 14, 15, 0xFF1E3A63.toInt()); rect(buf, S, 14, 12, 4, 13, 0xFFF0C451.toInt())
                rect(buf, S, 11, 4, 10, 8, skin); rect(buf, S, 9, 2, 14, 3, 0xFF152947.toInt())
                rect(buf, S, 10, 4, 12, 1, 0xFFF0C451.toInt())
            }
            NpcRole.REAPER_KEEPER -> {
                rect(buf, S, 9, 10, 14, 17, 0xFF1F162E.toInt()); rect(buf, S, 14, 11, 4, 15, 0xFF9B6BFF.toInt())
                rect(buf, S, 10, 3, 12, 8, 0xFF140D20.toInt())
                px(buf, S, 13, 7, 0xFFD6B8FF.toInt()); px(buf, S, 18, 7, 0xFFD6B8FF.toInt())
            }
            NpcRole.FISHERMAN, NpcRole.BLACKSMITH, NpcRole.FLETCHER, NpcRole.WANDERING_MERCHANT,
            NpcRole.HUNT_MASTER, NpcRole.BESTIARY_SAGE, NpcRole.TOWN_CRIER, NpcRole.DUNGEON_GUIDE -> {
                val (body, trim, hat) = when (role) {
                    NpcRole.FISHERMAN -> Triple(0xFF3E6E8C.toInt(), 0xFFC9A646.toInt(), 0xFFC9A646.toInt())
                    NpcRole.BLACKSMITH -> Triple(0xFF5A3B2A.toInt(), 0xFF7D4B28.toInt(), 0xFF2B1D14.toInt())
                    NpcRole.FLETCHER -> Triple(0xFF4F7A3A.toInt(), 0xFF7D4B28.toInt(), 0xFFB5652B.toInt())
                    NpcRole.WANDERING_MERCHANT -> Triple(0xFF7A2E5C.toInt(), 0xFFF0C451.toInt(), 0xFF2F2A55.toInt())
                    NpcRole.HUNT_MASTER -> Triple(0xFF8A8F99.toInt(), 0xFF6B1F1F.toInt(), 0xFF5C4630.toInt())
                    NpcRole.BESTIARY_SAGE -> Triple(0xFF2E5E57.toInt(), 0xFF6EE0C8.toInt(), 0xFF2E5E57.toInt())
                    NpcRole.TOWN_CRIER -> Triple(0xFF2D3F8C.toInt(), 0xFFF0C451.toInt(), 0xFF8C1F2A.toInt())
                    else -> Triple(0xFF5C5A3A.toInt(), 0xFF7D4B28.toInt(), 0xFF7D4B28.toInt())
                }
                rect(buf, S, 9, 11, 14, 15, body); rect(buf, S, 14, 12, 4, 13, trim)
                rect(buf, S, 11, 4, 10, 8, skin); rect(buf, S, 10, 2, 12, 3, hat)
            }
        }
        return toBmp(buf, S)
    }

    private fun buildMonsterCorpse(): ImageBitmap {
        val buf = buf32()
        rect(buf, S, 6, 14, 20, 11, 0xAA781212.toInt())
        rect(buf, S, 10, 17, 12, 2, 0xFFE8E2D5.toInt())
        rect(buf, S, 19, 19, 4, 4, 0xFFE6B44C.toInt())
        return toBmp(buf, S)
    }

    private fun buildPlayerCorpse(): ImageBitmap {
        val buf = buf32()
        rect(buf, S, 5, 13, 22, 13, 0xAA661010.toInt())
        rect(buf, S, 7, 14, 8, 7, 0xFFE8E2D5.toInt())
        rect(buf, S, 16, 13, 10, 11, 0xFF7D4B24.toInt())
        rect(buf, S, 20, 17, 2, 3, 0xFFFFD447.toInt())
        return toBmp(buf, S)
    }

    private fun buildItemIcon(itemId: String): ImageBitmap {
        val buf = buf16()
        val def = ItemCatalog.get(itemId)
        val tierTint = when {
            itemId.contains("cinderite") -> 0xFFFFAA2B.toInt()
            itemId.contains("rime_plate") || itemId.contains("frostpeak") || itemId.contains("glacier") || itemId.contains("frost_iron") -> 0xFF72E4FF.toInt()
            itemId.contains("vesperite") -> 0xFFB48CFF.toInt()
            itemId.contains("mire_woven") || itemId.contains("bog_steel") || itemId.contains("mire_silver") -> 0xFF5EE89E.toInt()
            itemId.contains("peat_coal") -> 0xFF474E57.toInt()
            itemId.contains("tempered_steel") -> 0xFF9AC2E6.toInt()
            itemId.contains("bronze_pickaxe") -> 0xFFE39B42.toInt()
            itemId.contains("moonforge") || itemId.contains("starlight") || itemId.contains("starstone") -> 0xFFB599FF.toInt()
            itemId.contains("wyrm") || itemId.contains("drake") || itemId.contains("cinderheart") || itemId.contains("fire") -> 0xFFFF6E24.toInt()
            itemId.contains("quenchsteel") || itemId.contains("colossus") || itemId.contains("tallowbone") -> 0xFF9AC2E6.toInt()
            itemId.contains("duskmetal") || itemId.contains("bog") || itemId.contains("brindle") || itemId.contains("bile") -> 0xFF7B8B9E.toInt()
            itemId.contains("sunbrass") || itemId.contains("copper") || itemId.contains("ochre") || itemId.contains("rookvale") -> 0xFFE39B42.toInt()
            itemId.contains("tin") -> 0xFFAAB8C7.toInt()
            else -> 0xFFD2DBE6.toInt()
        }

        when (def.category) {
            ItemCategory.RAW_ORE -> {
                val oreCol = when (itemId) {
                    "copper_ore", "ochre_nugget" -> 0xFFE08234.toInt()
                    "tin_ore" -> 0xFFA9B9C8.toInt()
                    "bog_iron_ore", "bogstone_shard" -> 0xFF7B8B9E.toInt()
                    "mire_silver_ore" -> 0xFF5EE89E.toInt()
                    "peat_coal_ore" -> 0xFF3D424A.toInt()
                    "vesperite_ore", "cinder_pitch" -> 0xFFA782FF.toInt()
                    "glacier_quartz_ore" -> 0xFF68E8FF.toInt()
                    "frost_iron_ore" -> 0xFF7CB4E8.toInt()
                    "cinderite_ore", "pale_starstone" -> 0xFFFFAA2B.toInt()
                    else -> 0xFF9B7BFF.toInt()
                }
                rect(buf, ITEM_S, 3, 5, 10, 8, 0xFF47413D.toInt())
                rect(buf, ITEM_S, 4, 4, 8, 7, oreCol)
                rect(buf, ITEM_S, 6, 5, 3, 2, 0xFFFFFFFF.toInt())
            }
            ItemCategory.METAL_INGOT -> {
                rect(buf, ITEM_S, 2, 6, 12, 6, tierTint)
                rect(buf, ITEM_S, 4, 4, 8, 3, 0xFFFFFFFF.toInt())
                rect(buf, ITEM_S, 2, 11, 12, 2, 0xFF2B2724.toInt())
            }
            ItemCategory.TOOL_PICKAXE -> {
                for (i in 0..9) px(buf, ITEM_S, 3 + i, 12 - i, 0xFF7D4E26.toInt())
                rect(buf, ITEM_S, 5, 2, 8, 3, tierTint)
                rect(buf, ITEM_S, 11, 4, 3, 5, tierTint)
            }
            ItemCategory.TOOL_HATCHET -> {
                for (i in 0..9) {
                    px(buf, ITEM_S, 3 + i, 12 - i, 0xFF8C5A2B.toInt())
                    px(buf, ITEM_S, 4 + i, 12 - i, 0xFF6B4226.toInt())
                }
                rect(buf, ITEM_S, 8, 2, 5, 5, 0xFFB0BCC9.toInt())
                rect(buf, ITEM_S, 11, 2, 2, 5, 0xFFE8F0F8.toInt())
                rect(buf, ITEM_S, 7, 3, 2, 3, 0xFF5D6670.toInt())
            }
            ItemCategory.AMMO -> {
                for (i in 0..10) px(buf, ITEM_S, 2 + i, 13 - i, 0xFFA36E39.toInt())
                rect(buf, ITEM_S, 10, 2, 4, 4, if (itemId == "wooden_arrows") 0xFFBAC7D6.toInt() else tierTint)
                rect(buf, ITEM_S, 2, 11, 3, 3, if (itemId.contains("venom")) 0xFF6CD936.toInt() else 0xFFF5EFE0.toInt())
            }
            ItemCategory.CRAFT_MATERIAL, ItemCategory.LOOT_VALUABLE -> {
                when (itemId) {
                    "wood_log" -> {
                        rect(buf, ITEM_S, 2, 5, 11, 6, 0xFF7D4E26.toInt())
                        rect(buf, ITEM_S, 2, 5, 11, 2, 0xFF9C6635.toInt())
                        rect(buf, ITEM_S, 2, 9, 11, 2, 0xFF593618.toInt())
                        rect(buf, ITEM_S, 11, 6, 3, 4, 0xFFD9AE73.toInt())
                        px(buf, ITEM_S, 12, 7, 0xFF9C6B3B.toInt())
                    }
                    "wild_feather" -> {
                        for (i in 0..9) px(buf, ITEM_S, 3 + i, 12 - i, 0xFFD9CFB8.toInt())
                        rect(buf, ITEM_S, 6, 3, 6, 5, 0xFFF5EFE0.toInt())
                        rect(buf, ITEM_S, 8, 2, 4, 3, 0xFFFFFFFF.toInt())
                        px(buf, ITEM_S, 5, 7, 0xFFE8DFC8.toInt())
                    }
                    else -> {
                        val c = when (itemId) {
                            "brindle_hide" -> 0xFF7B7E85.toInt()
                            "jagged_ivory", "ancient_bone", "dragon_tooth" -> 0xFFEDE3CE.toInt()
                            "spider_silk" -> 0xFFDCE6EE.toInt()
                            "bile_gland" -> 0xFF6CD936.toInt()
                            "vesper_wing" -> 0xFF5A466B.toInt()
                            "ghoul_gem" -> 0xFF9B6BFF.toInt()
                            "colossus_tendon" -> 0xFFB85C42.toInt()
                            "runic_slate" -> 0xFF63768C.toInt()
                            "ember_heart" -> 0xFFFF6E24.toInt()
                            "wyrmscale_plate" -> 0xFF299C4F.toInt()
                            "grimmaw_stone_core" -> 0xFFA682FF.toInt()
                            "gorek_obsidian_horn" -> 0xFFFFA833.toInt()
                            else -> 0xFFD4C9B0.toInt()
                        }
                        rect(buf, ITEM_S, 4, 4, 8, 8, c)
                        rect(buf, ITEM_S, 6, 5, 3, 3, 0xFFFFFFFF.toInt())
                    }
                }
            }
            ItemCategory.POTION_HP -> {
                rect(buf, ITEM_S, 6, 2, 4, 2, 0xFFC7A06B.toInt()); rect(buf, ITEM_S, 4, 5, 8, 9, 0xFFC92424.toInt()); rect(buf, ITEM_S, 5, 6, 3, 6, 0xFFFF5C5C.toInt())
            }
            ItemCategory.POTION_MP -> {
                rect(buf, ITEM_S, 6, 2, 4, 2, 0xFFC7A06B.toInt()); rect(buf, ITEM_S, 4, 5, 8, 9, 0xFF245DC9.toInt()); rect(buf, ITEM_S, 5, 6, 3, 6, 0xFF66A6FF.toInt())
            }
            ItemCategory.RUNE_BLANK -> {
                // Smooth uninscribed carved slate runestone with subtle bevel highlight
                rect(buf, ITEM_S, 3, 2, 10, 12, 0xFF4F5763.toInt())
                rect(buf, ITEM_S, 4, 3, 8, 10, 0xFF8B95A3.toInt())
                rect(buf, ITEM_S, 4, 3, 8, 2, 0xFFB0BAC7.toInt())
                rect(buf, ITEM_S, 5, 6, 6, 4, 0xFF77818F.toInt())
            }
            ItemCategory.RUNE_HEAL -> {
                // Luminous emerald-cyan healing cross sigil on pale marble runestone
                rect(buf, ITEM_S, 3, 2, 10, 12, 0xFF3E615B.toInt())
                rect(buf, ITEM_S, 4, 3, 8, 10, 0xFF8AA8A0.toInt())
                rect(buf, ITEM_S, 7, 4, 2, 8, 0xFF47F08B.toInt())
                rect(buf, ITEM_S, 5, 7, 6, 2, 0xFF47F08B.toInt())
                rect(buf, ITEM_S, 7, 7, 2, 2, 0xFFFFFFFF.toInt())
            }
            ItemCategory.RUNE_ATTACK -> {
                when (itemId) {
                    "magic_dart_rune" -> {
                        // Tier 1: Pale sky-cyan arcane dart chevron
                        rect(buf, ITEM_S, 3, 2, 10, 12, 0xFF485566.toInt())
                        rect(buf, ITEM_S, 4, 3, 8, 10, 0xFF7B899E.toInt())
                        rect(buf, ITEM_S, 7, 4, 2, 8, 0xFF66E8FF.toInt())
                        rect(buf, ITEM_S, 6, 5, 4, 2, 0xFF66E8FF.toInt())
                        px(buf, ITEM_S, 7, 4, 0xFFFFFFFF.toInt()); px(buf, ITEM_S, 8, 4, 0xFFFFFFFF.toInt())
                    }
                    "hmm_rune" -> {
                        // Tier 2: Electric sapphire lightning zig-zag sigil
                        rect(buf, ITEM_S, 3, 2, 10, 12, 0xFF364566.toInt())
                        rect(buf, ITEM_S, 4, 3, 8, 10, 0xFF63769E.toInt())
                        rect(buf, ITEM_S, 7, 4, 3, 2, 0xFF4DA6FF.toInt())
                        rect(buf, ITEM_S, 6, 6, 4, 2, 0xFF75D6FF.toInt())
                        rect(buf, ITEM_S, 7, 8, 3, 2, 0xFF4DA6FF.toInt())
                        rect(buf, ITEM_S, 6, 10, 3, 2, 0xFFFFFFFF.toInt())
                    }
                    "fireball_rune" -> {
                        // Tier 3: Fiery orange-gold ember orb on volcanic stone
                        rect(buf, ITEM_S, 3, 2, 10, 12, 0xFF5C2D22.toInt())
                        rect(buf, ITEM_S, 4, 3, 8, 10, 0xFF8C4C38.toInt())
                        rect(buf, ITEM_S, 5, 5, 6, 6, 0xFFFF5E1A.toInt())
                        rect(buf, ITEM_S, 6, 6, 4, 4, 0xFFFFB833.toInt())
                        rect(buf, ITEM_S, 7, 7, 2, 2, 0xFFFFF4B8.toInt())
                    }
                    else -> {
                        // Tier 4 Sudden Death: Obsidian tablet with crimson skull sigil
                        rect(buf, ITEM_S, 3, 2, 10, 12, 0xFF211824.toInt())
                        rect(buf, ITEM_S, 4, 3, 8, 10, 0xFF3B2C40.toInt())
                        rect(buf, ITEM_S, 5, 4, 6, 5, 0xFFFF3355.toInt())
                        rect(buf, ITEM_S, 6, 9, 4, 2, 0xFFFF3355.toInt())
                        px(buf, ITEM_S, 6, 6, 0xFF140D17.toInt()); px(buf, ITEM_S, 9, 6, 0xFF140D17.toInt())
                    }
                }
            }
            ItemCategory.TOOL_ROPE -> {
                rect(buf, ITEM_S, 3, 4, 10, 9, 0xFFB88D4E.toInt()); rect(buf, ITEM_S, 5, 6, 6, 5, 0)
            }
            ItemCategory.TOOL_FISHING_ROD -> {
                for (i in 0..10) px(buf, ITEM_S, 3 + i, 13 - i, 0xFF8C5A2B.toInt()); rect(buf, ITEM_S, 13, 3, 1, 9, 0xFFDCD3C2.toInt())
            }
            ItemCategory.FOOD -> {
                if (FishingCatalog.tierForFish(itemId) != null) {
                    val (body, belly, accent) = when (itemId) {
                        "fresh_fish" -> Triple(0xFF4F8EC9.toInt(), 0xFFAAD2F2.toInt(), 0xFF2B5B8A.toInt())
                        "silver_trout" -> Triple(0xFFB4C8D8.toInt(), 0xFFEFF6FC.toInt(), 0xFF6394B8.toInt())
                        "marsh_pike" -> Triple(0xFF4B8A4E.toInt(), 0xFFA4D48A.toInt(), 0xFF254D2B.toInt())
                        "sunfin_snapper" -> Triple(0xFFE08A28.toInt(), 0xFFFFD878.toInt(), 0xFFA84214.toInt())
                        "glacier_salmon" -> Triple(0xFF3CC2D9.toInt(), 0xFFC7F6FF.toInt(), 0xFF1D6B8C.toInt())
                        "emberfin_sturgeon" -> Triple(0xFF3A272B.toInt(), 0xFFFF6E24.toInt(), 0xFFFFCA45.toInt())
                        else -> Triple(0xFF5A9BD4.toInt(), 0xFFBFDDF5.toInt(), 0xFF2E6396.toInt())
                    }
                    // Tapered fish body, dorsal fin, forked tail, and eye
                    rect(buf, ITEM_S, 3, 6, 8, 4, body)
                    rect(buf, ITEM_S, 4, 5, 6, 2, body)
                    rect(buf, ITEM_S, 4, 9, 6, 2, belly)
                    rect(buf, ITEM_S, 6, 3, 3, 2, accent) // dorsal fin
                    rect(buf, ITEM_S, 11, 7, 2, 2, body) // tail stem
                    rect(buf, ITEM_S, 13, 4, 2, 3, accent) // upper tail fin
                    rect(buf, ITEM_S, 13, 9, 2, 3, accent) // lower tail fin
                    px(buf, ITEM_S, 2, 7, body); px(buf, ITEM_S, 2, 8, belly) // snout
                    px(buf, ITEM_S, 4, 7, 0xFF120F0E.toInt()) // eye
                    px(buf, ITEM_S, 7, 7, accent); px(buf, ITEM_S, 9, 7, accent) // lateral markings
                } else {
                    val c = when (itemId) {
                        "fresh_meat" -> 0xFFC94A38.toInt()
                        "dragon_ham" -> 0xFFB8442A.toInt()
                        else -> 0xFFE3C254.toInt()
                    }
                    rect(buf, ITEM_S, 3, 5, 10, 6, c); rect(buf, ITEM_S, 12, 4, 2, 8, c)
                    if (itemId == "fresh_meat") rect(buf, ITEM_S, 11, 7, 3, 2, 0xFFEDE3CE.toInt())
                }
            }
            ItemCategory.FISHING_BAIT -> {
                val (body, stripe, glow) = when (itemId) {
                    "garden_worm" -> Triple(0xFFD9847A.toInt(), 0xFFA8524A.toInt(), 0xFFF2B6AC.toInt())
                    "dew_nightcrawler" -> Triple(0xFF9C5E78.toInt(), 0xFF633248.toInt(), 0xFFD9A3B8.toInt())
                    "mire_bloodworm" -> Triple(0xFFC92A38.toInt(), 0xFF7A121C.toInt(), 0xFFFF6E7A.toInt())
                    "dune_sandworm" -> Triple(0xFFD9A252.toInt(), 0xFF8C5B1F.toInt(), 0xFFFFDF99.toInt())
                    "frost_glowworm" -> Triple(0xFF3FD6E8.toInt(), 0xFF1E7E99.toInt(), 0xFFD4FCFF.toInt())
                    "cinder_emberworm" -> Triple(0xFFFF5E1A.toInt(), 0xFF7D2210.toInt(), 0xFFFFE066.toInt())
                    else -> Triple(0xFFD9847A.toInt(), 0xFFA8524A.toInt(), 0xFFF2B6AC.toInt())
                }
                // Curled S-shaped segmented worm silhouette
                rect(buf, ITEM_S, 4, 3, 6, 2, body)
                rect(buf, ITEM_S, 9, 4, 2, 3, body)
                rect(buf, ITEM_S, 5, 6, 5, 2, body)
                rect(buf, ITEM_S, 4, 8, 2, 3, body)
                rect(buf, ITEM_S, 5, 10, 7, 2, body)
                rect(buf, ITEM_S, 11, 9, 2, 2, glow) // head tip
                // Realistic body segments & sheen
                px(buf, ITEM_S, 6, 3, stripe); px(buf, ITEM_S, 8, 4, glow)
                px(buf, ITEM_S, 7, 6, stripe); px(buf, ITEM_S, 9, 7, stripe)
                px(buf, ITEM_S, 4, 9, glow); px(buf, ITEM_S, 7, 10, stripe)
                px(buf, ITEM_S, 9, 10, glow); px(buf, ITEM_S, 10, 11, stripe)
            }
            ItemCategory.WEAPON_MELEE -> {
                if (itemId == "wooden_club") {
                    for (i in 0..9) px(buf, ITEM_S, 3 + i, 12 - i, 0xFF7D4E26.toInt())
                    rect(buf, ITEM_S, 9, 2, 5, 5, 0xFF5C3818.toInt())
                } else if (itemId.contains("warmaul") || itemId.contains("worldbreaker") || itemId.contains("mace") || itemId.contains("warhammer")) {
                    for (i in 0..9) px(buf, ITEM_S, 3 + i, 12 - i, 0xFF4E4047.toInt())
                    rect(buf, ITEM_S, 7, 2, 7, 5, tierTint)
                    rect(buf, ITEM_S, 9, 3, 3, 2, 0xFFFFFFFF.toInt())
                } else if (itemId.contains("axe") || itemId.contains("cleaver") || itemId.contains("glaive") || itemId.contains("halberd")) {
                    for (i in 0..10) px(buf, ITEM_S, 3 + i, 12 - i, 0xFF6B4226.toInt())
                    rect(buf, ITEM_S, 8, 2, 6, 6, tierTint)
                } else {
                    val len = if (itemId.contains("dirk")) 6 else 10
                    for (i in 0..len) { px(buf, ITEM_S, 4 + i, 11 - i, tierTint); px(buf, ITEM_S, 5 + i, 11 - i, tierTint) }
                    rect(buf, ITEM_S, 3, 10, 4, 3, 0xFFE6B44C.toInt())
                }
            }
            ItemCategory.WEAPON_DISTANCE -> {
                if (itemId.contains("arbalest") || itemId.contains("siegebow") || itemId.contains("crossbow")) {
                    rect(buf, ITEM_S, 3, 7, 10, 3, 0xFF7D4E26.toInt())
                    rect(buf, ITEM_S, 7, 3, 3, 10, tierTint)
                } else if (itemId.contains("bow") || itemId.contains("recurve")) {
                    val wood = if (itemId == "hunting_bow") 0xFF9C6635.toInt() else 0xFF7D4E26.toInt()
                    rect(buf, ITEM_S, 4, 2, 3, 2, wood)
                    rect(buf, ITEM_S, 3, 4, 2, 8, wood)
                    rect(buf, ITEM_S, 4, 12, 3, 2, wood)
                    rect(buf, ITEM_S, 7, 3, 1, 10, 0xFFE8DFC8.toInt()) // bowstring
                    rect(buf, ITEM_S, 5, 7, 8, 1, tierTint) // nocked arrow
                    px(buf, ITEM_S, 13, 7, 0xFFFFFFFF.toInt())
                } else {
                    for (i in 0..11) px(buf, ITEM_S, 2 + i, 13 - i, 0xFFA36E39.toInt())
                    rect(buf, ITEM_S, 11, 2, 3, 3, tierTint)
                }
            }
            ItemCategory.WEAPON_WAND -> {
                for (i in 0..8) px(buf, ITEM_S, 3 + i, 12 - i, 0xFF6B4226.toInt())
                rect(buf, ITEM_S, 10, 2, 4, 4, tierTint)
                px(buf, ITEM_S, 11, 3, 0xFFFFFFFF.toInt())
            }
            ItemCategory.SHIELD -> {
                val (col, trim) = when (itemId) {
                    "cinderite_bastion" -> 0xFF33211E.toInt() to 0xFFFF8826.toInt()
                    "rime_plate_wall_shield" -> 0xFF456D8A.toInt() to 0xFF88ECFF.toInt()
                    "vesperite_bulwark" -> 0xFF6B4EB8.toInt() to 0xFFD9C7FF.toInt()
                    "bog_steel_targe" -> 0xFF2D543C.toInt() to 0xFF68F09B.toInt()
                    "dragon_shield" -> 0xFF288C46.toInt() to 0xFFE6B44C.toInt()
                    "steel_shield" -> 0xFF7B8694.toInt() to 0xFFE6B44C.toInt()
                    else -> 0xFF8C5A2B.toInt() to 0xFFE6B44C.toInt()
                }
                rect(buf, ITEM_S, 3, 2, 10, 11, col)
                rect(buf, ITEM_S, 5, 4, 6, 7, trim)
                px(buf, ITEM_S, 7, 6, 0xFFFFFFFF.toInt())
            }
            ItemCategory.ARMOR -> {
                val (col, trim) = when (itemId) {
                    "cinderite_warplate" -> 0xFF2F1F1C.toInt() to 0xFFFF7A29.toInt()
                    "rime_plate_cuirass" -> 0xFF456D8A.toInt() to 0xFF88ECFF.toInt()
                    "vesperite_aegis_mail" -> 0xFF684EB2.toInt() to 0xFFD9C7FF.toInt()
                    "mire_woven_brigandine" -> 0xFF2D543C.toInt() to 0xFF68F09B.toInt()
                    "dragon_mail" -> 0xFF268F4A.toInt() to 0xFFE6B44C.toInt()
                    "brass_armor" -> 0xFFB8963E.toInt() to 0xFFE6C86E.toInt()
                    "mystic_robe" -> 0xFF3361BD.toInt() to 0xFF9AC5FF.toInt()
                    else -> 0xFF7D502E.toInt() to 0xFFA67248.toInt()
                }
                rect(buf, ITEM_S, 3, 3, 10, 11, col)
                rect(buf, ITEM_S, 6, 3, 4, 3, 0xFF1A1715.toInt())
                rect(buf, ITEM_S, 4, 7, 8, 2, trim)
            }
            ItemCategory.HELMET -> {
                rect(buf, ITEM_S, 4, 5, 8, 7, if (itemId == "viking_helmet") 0xFF9EA8B5.toInt() else 0xFF7D502E.toInt())
            }
            ItemCategory.LEGS -> {
                val col = if (itemId == "brass_legs") 0xFFB8963E.toInt() else 0xFF7D502E.toInt()
                rect(buf, ITEM_S, 4, 3, 8, 4, col); rect(buf, ITEM_S, 4, 7, 3, 7, col); rect(buf, ITEM_S, 9, 7, 3, 7, col)
            }
            ItemCategory.BOOTS -> {
                rect(buf, ITEM_S, 3, 6, 4, 7, 0xFF59381E.toInt()); rect(buf, ITEM_S, 9, 6, 4, 7, 0xFF59381E.toInt())
            }
            ItemCategory.AMULET -> {
                val (chainCol, gemCol) = when (itemId) {
                    "cinderheart_sovereign_torc" -> 0xFFFFB247.toInt() to 0xFFFF4D1F.toInt()
                    "vesperite_resonance_necklace" -> 0xFFCFC6E8.toInt() to 0xFFB07BFF.toInt()
                    "stormglass_pendant" -> 0xFFB8D4E8.toInt() to 0xFF4CC6FF.toInt()
                    "bog_iron_ward_pendant" -> 0xFF8C949E.toInt() to 0xFF5BD48B.toInt()
                    else -> 0xFFE6B44C.toInt() to 0xFFD92B3A.toInt()
                }
                // Chain arch + radiant pendant gem
                rect(buf, ITEM_S, 4, 2, 8, 2, chainCol)
                rect(buf, ITEM_S, 4, 4, 2, 4, chainCol)
                rect(buf, ITEM_S, 10, 4, 2, 4, chainCol)
                rect(buf, ITEM_S, 5, 7, 6, 6, chainCol)
                rect(buf, ITEM_S, 6, 8, 4, 4, gemCol)
                px(buf, ITEM_S, 7, 9, 0xFFFFFFFF.toInt())
            }
            ItemCategory.RING -> {
                val (bandCol, gemCol) = when (itemId) {
                    "cinderite_overlord_band" -> 0xFF3A241E.toInt() to 0xFFFF7A29.toInt()
                    "vesperite_catalyst_ring" -> 0xFFCDC4EB.toInt() to 0xFFB280FF.toInt()
                    "frostfang_coil" -> 0xFFBBD7E6.toInt() to 0xFF5CE0FF.toInt()
                    "duskmetal_vampire_band" -> 0xFF6B717A.toInt() to 0xFFE84066.toInt()
                    else -> 0xFFD99A4E.toInt() to 0xFFFFDF80.toInt()
                }
                // Band oval + sparkling top gem
                rect(buf, ITEM_S, 4, 6, 8, 2, bandCol)
                rect(buf, ITEM_S, 4, 12, 8, 2, bandCol)
                rect(buf, ITEM_S, 3, 7, 2, 6, bandCol)
                rect(buf, ITEM_S, 11, 7, 2, 6, bandCol)
                rect(buf, ITEM_S, 6, 3, 4, 4, gemCol)
                px(buf, ITEM_S, 7, 4, 0xFFFFFFFF.toInt())
            }
            ItemCategory.ADMIN_SCROLL -> {
                // Ornate gilded parchment scroll with gold rollers, royal crimson seal & jewel glint
                val goldRoller = 0xFFF5C542.toInt()
                val goldHi = 0xFFFFF0A6.toInt()
                val parchment = 0xFFF2DFB3.toInt()
                val parchmentShade = 0xFFD4B577.toInt()
                val rubySeal = 0xFFE53935.toInt()
                rect(buf, ITEM_S, 2, 2, 12, 2, goldRoller)
                rect(buf, ITEM_S, 3, 2, 10, 1, goldHi)
                rect(buf, ITEM_S, 3, 4, 10, 8, parchment)
                rect(buf, ITEM_S, 3, 10, 10, 2, parchmentShade)
                rect(buf, ITEM_S, 2, 12, 12, 2, goldRoller)
                rect(buf, ITEM_S, 3, 12, 10, 1, goldHi)
                // Royal crimson ribbon & ruby crest
                rect(buf, ITEM_S, 6, 6, 4, 4, rubySeal)
                px(buf, ITEM_S, 7, 7, 0xFFFFD54F.toInt())
                px(buf, ITEM_S, 8, 7, 0xFFFFFFFF.toInt())
            }
        }
        return toBmp(buf, ITEM_S)
    }
}
