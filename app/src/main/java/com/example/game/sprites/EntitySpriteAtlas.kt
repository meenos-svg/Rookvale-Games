package com.example.game.sprites

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.game.model.AdventurerPalette
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
        Vocation.entries.forEach { v -> playerBitmap(v, 0); playerBitmap(v, 1) }
        MonsterType.entries.forEach { m -> monsterBitmap(m, 0); monsterBitmap(m, 1) }
        NpcRole.entries.forEach { npcBitmap(it) }
        corpseBitmap(false); corpseBitmap(true)
        ItemCatalog.all.keys.forEach { itemIcon(it) }
    }

    fun playerBitmap(
        vocation: Vocation,
        frame: Int,
        tunicColorIndex: Int = 0,
        hairColorIndex: Int = 0
    ): ImageBitmap {
        val f = frame and 1
        val key = if (vocation == Vocation.ADVENTURER) {
            "ADV_${tunicColorIndex.coerceIn(0, AdventurerPalette.tunicColors.lastIndex)}_${hairColorIndex.coerceIn(0, AdventurerPalette.hairColors.lastIndex)}_$f"
        } else {
            "${vocation.name}_$f"
        }
        return playerCache.getOrPut(key) { buildPlayer(vocation, f, tunicColorIndex, hairColorIndex) }
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

    private fun buildPlayer(
        vocation: Vocation,
        frame: Int,
        tunicColorIndex: Int,
        hairColorIndex: Int
    ): ImageBitmap {
        val buf = buf32()
        val bob = if (frame == 1) -1 else 0
        drawShadow(buf)
        val skin = 0xFFE8BE96.toInt()
        val boot = 0xFF3D2514.toInt()
        val outline = 0xFF14100E.toInt()
        rect(buf, S, 10, 23 + (if (frame == 1) -1 else 0), 4, 4, boot)
        rect(buf, S, 17, 23 + (if (frame == 1) 1 else 0), 4, 4, boot)

        when (vocation) {
            Vocation.ADVENTURER -> {
                val tunic = AdventurerPalette.tunicAt(tunicColorIndex)
                val hair = AdventurerPalette.hairAt(hairColorIndex)
                // Breeches
                rect(buf, S, 10, 18 + bob, 11, 6, 0xFF4F3B2A.toInt())
                // Customizable medieval tunic + leather satchel strap
                rect(buf, S, 9, 10 + bob, 13, 9, tunic.primaryArgb)
                rect(buf, S, 11, 11 + bob, 9, 7, tunic.highlightArgb)
                rect(buf, S, 10, 16 + bob, 11, 2, 0xFF6B4423.toInt()) // belt
                rect(buf, S, 14, 16 + bob, 3, 2, 0xFFE6B44C.toInt()) // brass buckle
                // Satchel shoulder strap
                px(buf, S, 11, 10 + bob, 0xFF543318.toInt())
                px(buf, S, 12, 11 + bob, 0xFF543318.toInt())
                px(buf, S, 13, 12 + bob, 0xFF543318.toInt())
                px(buf, S, 14, 13 + bob, 0xFF543318.toInt())
                // Head & customizable hair
                rect(buf, S, 12, 5 + bob, 7, 5, skin)
                rect(buf, S, 11, 2 + bob, 9, 4, hair.primaryArgb)
                rect(buf, S, 12, 2 + bob, 7, 2, hair.highlightArgb)
                rect(buf, S, 11, 5 + bob, 1, 3, hair.primaryArgb)
                rect(buf, S, 19, 5 + bob, 1, 3, hair.primaryArgb)
                px(buf, S, 13, 7 + bob, outline); px(buf, S, 17, 7 + bob, outline)
                // Starter dirk in left hand + small wooden buckler on right
                rect(buf, S, 6, 12 + bob, 2, 7, 0xFFD99A4E.toInt())
                rect(buf, S, 5, 16 + bob, 4, 2, 0xFF7D4E26.toInt())
                rect(buf, S, 22, 12 + bob, 5, 8, 0xFF7D4E26.toInt())
                rect(buf, S, 23, 13 + bob, 3, 6, 0xFFA66C38.toInt())
            }
            Vocation.KNIGHT -> {
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
            Vocation.PALADIN -> {
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
            Vocation.MAGE -> {
                rect(buf, S, 9, 11 + bob, 13, 13, 0xFF234794.toInt())
                rect(buf, S, 11, 12 + bob, 9, 11, 0xFF3361BD.toInt())
                rect(buf, S, 14, 12 + bob, 3, 11, 0xFFE6B44C.toInt())
                rect(buf, S, 12, 6 + bob, 7, 5, skin)
                px(buf, S, 13, 8 + bob, outline); px(buf, S, 17, 8 + bob, outline)
                rect(buf, S, 8, 5 + bob, 15, 2, 0xFF1D397A.toInt())
                rect(buf, S, 11, 2 + bob, 9, 3, 0xFF2C54AB.toInt())
                rect(buf, S, 23, 6 + bob, 2, 19, 0xFF6B4226.toInt())
                rect(buf, S, 22, 4 + bob, 4, 4, if (frame == 0) 0xFF66E8FF.toInt() else 0xFFB899FF.toInt())
            }
        }
        return toBmp(buf, S)
    }

    private fun buildMonster(type: MonsterType, frame: Int): ImageBitmap {
        val buf = buf32()
        val bob = if (frame == 1) -1 else 0
        when (type) {
            MonsterType.RAT -> {
                drawShadow(buf, 8, 23, 15, 4)
                rect(buf, S, 22, 19 + bob, 5, 2, 0xFFD98B9A.toInt())
                rect(buf, S, 9, 14 + bob, 13, 9, 0xFF6E5038.toInt())
                rect(buf, S, 11, 13 + bob, 9, 6, 0xFF876549.toInt())
                rect(buf, S, 6, 16 + bob, 5, 5, 0xFF7D5D42.toInt())
                px(buf, S, 8, 17 + bob, 0xFFFF2B2B.toInt())
            }
            MonsterType.CRAG_WOLF -> {
                drawShadow(buf, 6, 24, 20, 4)
                val fur = 0xFF6B6E75.toInt()
                val light = 0xFF9AA0AA.toInt()
                rect(buf, S, 8, 14 + bob, 16, 8, fur)
                rect(buf, S, 10, 13 + bob, 11, 5, light)
                rect(buf, S, 5, 11 + bob, 7, 7, fur) // wolf head
                rect(buf, S, 6, 8 + bob, 2, 3, fur); rect(buf, S, 10, 8 + bob, 2, 3, fur) // ears
                rect(buf, S, 8, 21 + (if (frame == 1) -1 else 0), 3, 5, 0xFF474A52.toInt())
                rect(buf, S, 19, 21 + (if (frame == 1) 1 else 0), 3, 5, 0xFF474A52.toInt())
                rect(buf, S, 24, 14 + bob, 4, 3, light) // tail
                px(buf, S, 7, 13 + bob, 0xFFFFC933.toInt()) // amber eye
                px(buf, S, 6, 16 + bob, 0xFFFFFFFF.toInt()) // fang
            }
            MonsterType.VESPER_BAT -> {
                drawShadow(buf, 9, 25, 14, 3)
                val wingY = if (frame == 0) 9 else 14
                rect(buf, S, 3, wingY, 10, 7, 0xFF3E314A.toInt())
                rect(buf, S, 19, wingY, 10, 7, 0xFF3E314A.toInt())
                rect(buf, S, 12, 11 + bob, 8, 9, 0xFF544363.toInt())
                rect(buf, S, 12, 8 + bob, 2, 3, 0xFF544363.toInt()); rect(buf, S, 18, 8 + bob, 2, 3, 0xFF544363.toInt())
                px(buf, S, 14, 14 + bob, 0xFFFF4D4D.toInt()); px(buf, S, 17, 14 + bob, 0xFFFF4D4D.toInt())
            }
            MonsterType.CAVE_SPIDER -> {
                drawShadow(buf, 6, 23, 20, 5)
                val legShift = if (frame == 0) 0 else 2
                for (i in 0..3) {
                    val ly = 11 + i * 4
                    rect(buf, S, 4, ly + (if (i % 2 == 0) legShift else -legShift) / 2, 7, 2, 0xFF1F1C24.toInt())
                    rect(buf, S, 21, ly + (if (i % 2 == 1) legShift else -legShift) / 2, 7, 2, 0xFF1F1C24.toInt())
                }
                rect(buf, S, 10, 9 + bob, 12, 10, 0xFF2D2736.toInt())
                rect(buf, S, 11, 18 + bob, 10, 6, 0xFF1F1A26.toInt())
                px(buf, S, 13, 20 + bob, 0xFF7BFF33.toInt()); px(buf, S, 18, 20 + bob, 0xFF7BFF33.toInt())
            }
            MonsterType.STONEHORN_TROLL -> {
                drawShadow(buf, 6, 25, 20, 5)
                val hide = 0xFF5F6E54.toInt()
                rect(buf, S, 8, 19 + bob, 5, 8, hide); rect(buf, S, 19, 19 + bob, 5, 8, hide)
                rect(buf, S, 7, 9 + bob, 18, 11, hide)
                rect(buf, S, 9, 10 + bob, 14, 8, 0xFF758768.toInt())
                rect(buf, S, 11, 3 + bob, 10, 7, hide)
                rect(buf, S, 9, 1 + bob, 3, 4, 0xFFDCD3C2.toInt()); rect(buf, S, 20, 1 + bob, 3, 4, 0xFFDCD3C2.toInt()) // stone horns
                px(buf, S, 13, 6 + bob, 0xFFFF7A29.toInt()); px(buf, S, 18, 6 + bob, 0xFFFF7A29.toInt())
            }
            MonsterType.SKELETON, MonsterType.MARROW_ARCHER -> {
                drawShadow(buf, 8, 25, 16, 4)
                val bone = 0xFFE8E2D5.toInt()
                rect(buf, S, 11, 19 + bob, 3, 7, 0xFFADA699.toInt()); rect(buf, S, 17, 19 + bob, 3, 7, 0xFFADA699.toInt())
                rect(buf, S, 10, 11 + bob, 11, 8, 0xFF24211E.toInt())
                rect(buf, S, 11, 12 + bob, 9, 2, bone); rect(buf, S, 11, 15 + bob, 9, 2, bone)
                rect(buf, S, 11, 3 + bob, 9, 8, bone)
                rect(buf, S, 12, 6 + bob, 2, 2, 0xFF14100E.toInt()); rect(buf, S, 17, 6 + bob, 2, 2, 0xFF14100E.toInt())
                if (type == MonsterType.MARROW_ARCHER) {
                    rect(buf, S, 4, 6 + bob, 3, 15, 0xFFA36E39.toInt()) // bone bow
                    rect(buf, S, 10, 2 + bob, 11, 3, 0xFF8C2424.toInt()) // crimson archer hood
                } else {
                    rect(buf, S, 5, 8 + bob, 3, 11, 0xFF94745E.toInt())
                    rect(buf, S, 22, 12 + bob, 6, 9, 0xFF6B533B.toInt())
                }
            }
            MonsterType.GHOUL -> {
                drawShadow(buf, 8, 25, 16, 5)
                rect(buf, S, 9, 11 + bob, 14, 14, 0xFF393E47.toInt())
                rect(buf, S, 11, 12 + bob, 10, 10, 0xFF5D7868.toInt())
                rect(buf, S, 11, 4 + bob, 10, 8, 0xFF779481.toInt())
                rect(buf, S, 13, 7 + bob, 2, 2, 0xFF8CFF4D.toInt()); rect(buf, S, 17, 7 + bob, 2, 2, 0xFF8CFF4D.toInt())
            }
            MonsterType.CYCLOPS, MonsterType.FORGE_BRUTE -> {
                drawShadow(buf, 5, 25, 22, 6)
                val skin = if (type == MonsterType.FORGE_BRUTE) 0xFF995D42.toInt() else 0xFFB8875A.toInt()
                val hi = if (type == MonsterType.FORGE_BRUTE) 0xFFB87454.toInt() else 0xFFD4A272.toInt()
                rect(buf, S, 8, 20 + bob, 6, 8, skin); rect(buf, S, 18, 20 + bob, 6, 8, skin)
                rect(buf, S, 8, 17 + bob, 16, 5, 0xFF3D2E29.toInt())
                rect(buf, S, 6, 7 + bob, 20, 11, skin); rect(buf, S, 9, 8 + bob, 14, 8, hi)
                rect(buf, S, 11, 1 + bob, 10, 7, hi)
                rect(buf, S, 14, 3 + bob, 4, 3, 0xFFFFF4D1.toInt()); rect(buf, S, 15, 4 + bob, 2, 2, 0xFFE65C1A.toInt())
                rect(buf, S, 25, 4 + bob, 5, 16, if (type == MonsterType.FORGE_BRUTE) 0xFF545B66.toInt() else 0xFF5C3A1E.toInt())
            }
            MonsterType.RUNIC_GOLEM -> {
                drawShadow(buf, 5, 25, 22, 6)
                rect(buf, S, 7, 19 + bob, 6, 9, 0xFF4B5059.toInt()); rect(buf, S, 19, 19 + bob, 6, 9, 0xFF4B5059.toInt())
                rect(buf, S, 5, 7 + bob, 22, 13, 0xFF636B78.toInt())
                rect(buf, S, 10, 2 + bob, 12, 6, 0xFF7B8596.toInt())
                val runeGlow = if (frame == 0) 0xFF8B6BFF.toInt() else 0xFFD1B8FF.toInt()
                rect(buf, S, 13, 11 + bob, 6, 6, runeGlow)
                rect(buf, S, 12, 4 + bob, 2, 2, runeGlow); rect(buf, S, 18, 4 + bob, 2, 2, runeGlow)
            }
            MonsterType.WYRM_HATCHLING -> {
                drawShadow(buf, 7, 24, 18, 5)
                val wy = if (frame == 0) 7 else 10
                rect(buf, S, 4, wy, 7, 9, 0xFF9E2B2B.toInt()); rect(buf, S, 21, wy, 7, 9, 0xFF9E2B2B.toInt())
                rect(buf, S, 10, 11 + bob, 12, 12, 0xFF288A47.toInt())
                rect(buf, S, 12, 13 + bob, 8, 8, 0xFFE0B44C.toInt())
                rect(buf, S, 11, 5 + bob, 10, 7, 0xFF39A85C.toInt())
                px(buf, S, 13, 7 + bob, 0xFFFF661A.toInt()); px(buf, S, 18, 7 + bob, 0xFFFF661A.toInt())
            }
            MonsterType.CINDER_WRAITH -> {
                drawShadow(buf, 9, 25, 14, 4)
                rect(buf, S, 8, 10 + bob, 16, 14, 0xFF2E1A1F.toInt())
                rect(buf, S, 10, 12 + bob, 12, 10, if (frame == 0) 0xFFFF5E1A.toInt() else 0xFFFF882B.toInt())
                rect(buf, S, 11, 4 + bob, 10, 8, 0xFF1F1216.toInt())
                rect(buf, S, 13, 7 + bob, 2, 2, 0xFFFFE57F.toInt()); rect(buf, S, 17, 7 + bob, 2, 2, 0xFFFFE57F.toInt())
            }
            MonsterType.DRAGON_BOSS -> {
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
            itemId.contains("moonforge") || itemId.contains("starlight") || itemId.contains("starstone") -> 0xFFB599FF.toInt()
            itemId.contains("wyrm") || itemId.contains("drake") || itemId.contains("cinderheart") || itemId.contains("fire") -> 0xFFFF6E24.toInt()
            itemId.contains("quenchsteel") || itemId.contains("colossus") || itemId.contains("tallowbone") -> 0xFF9AC2E6.toInt()
            itemId.contains("duskmetal") || itemId.contains("bog") || itemId.contains("brindle") || itemId.contains("bile") -> 0xFF7B8B9E.toInt()
            itemId.contains("sunbrass") || itemId.contains("ochre") || itemId.contains("rookvale") -> 0xFFE39B42.toInt()
            else -> 0xFFD2DBE6.toInt()
        }

        when (def.category) {
            ItemCategory.RAW_ORE -> {
                val oreCol = when (itemId) {
                    "ochre_nugget" -> 0xFFD97D2B.toInt()
                    "bogstone_shard" -> 0xFF7B8B9E.toInt()
                    "cinder_pitch" -> 0xFFE65319.toInt()
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
            ItemCategory.AMMO -> {
                for (i in 0..10) px(buf, ITEM_S, 2 + i, 13 - i, 0xFFA36E39.toInt())
                rect(buf, ITEM_S, 10, 2, 4, 4, tierTint)
                rect(buf, ITEM_S, 2, 11, 3, 3, if (itemId.contains("venom")) 0xFF6CD936.toInt() else 0xFFEDE3CE.toInt())
            }
            ItemCategory.CRAFT_MATERIAL, ItemCategory.LOOT_VALUABLE -> {
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
                    else -> 0xFFD4C9B0.toInt()
                }
                rect(buf, ITEM_S, 4, 4, 8, 8, c)
                rect(buf, ITEM_S, 6, 5, 3, 3, 0xFFFFFFFF.toInt())
            }
            ItemCategory.POTION_HP -> {
                rect(buf, ITEM_S, 6, 2, 4, 2, 0xFFC7A06B.toInt()); rect(buf, ITEM_S, 4, 5, 8, 9, 0xFFC92424.toInt()); rect(buf, ITEM_S, 5, 6, 3, 6, 0xFFFF5C5C.toInt())
            }
            ItemCategory.POTION_MP -> {
                rect(buf, ITEM_S, 6, 2, 4, 2, 0xFFC7A06B.toInt()); rect(buf, ITEM_S, 4, 5, 8, 9, 0xFF245DC9.toInt()); rect(buf, ITEM_S, 5, 6, 3, 6, 0xFF66A6FF.toInt())
            }
            ItemCategory.RUNE_ATTACK -> {
                val glow = if (itemId == "sd_rune") 0xFFFF4D6D.toInt() else 0xFF4DD2FF.toInt()
                rect(buf, ITEM_S, 3, 2, 10, 12, 0xFF6B7380.toInt()); rect(buf, ITEM_S, 4, 3, 8, 10, 0xFF8C96A6.toInt()); rect(buf, ITEM_S, 6, 5, 4, 6, glow)
            }
            ItemCategory.TOOL_ROPE -> {
                rect(buf, ITEM_S, 3, 4, 10, 9, 0xFFB88D4E.toInt()); rect(buf, ITEM_S, 5, 6, 6, 5, 0)
            }
            ItemCategory.TOOL_FISHING_ROD -> {
                for (i in 0..10) px(buf, ITEM_S, 3 + i, 13 - i, 0xFF8C5A2B.toInt()); rect(buf, ITEM_S, 13, 3, 1, 9, 0xFFDCD3C2.toInt())
            }
            ItemCategory.FOOD -> {
                val c = if (itemId == "fresh_fish") 0xFF5A9BD4.toInt() else if (itemId == "dragon_ham") 0xFFB8442A.toInt() else 0xFFE3C254.toInt()
                rect(buf, ITEM_S, 3, 5, 10, 6, c); rect(buf, ITEM_S, 12, 4, 2, 8, c)
            }
            ItemCategory.WEAPON_MELEE -> {
                if (itemId.contains("axe") || itemId.contains("glaive") || itemId.contains("halberd")) {
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
                val col = when (itemId) { "dragon_shield" -> 0xFF288C46.toInt(); "steel_shield" -> 0xFF7B8694.toInt(); else -> 0xFF8C5A2B.toInt() }
                rect(buf, ITEM_S, 3, 2, 10, 11, col); rect(buf, ITEM_S, 5, 4, 6, 7, 0xFFE6B44C.toInt())
            }
            ItemCategory.ARMOR -> {
                val col = when (itemId) { "dragon_mail" -> 0xFF268F4A.toInt(); "brass_armor" -> 0xFFB8963E.toInt(); "mystic_robe" -> 0xFF3361BD.toInt(); else -> 0xFF7D502E.toInt() }
                rect(buf, ITEM_S, 3, 3, 10, 11, col); rect(buf, ITEM_S, 6, 3, 4, 3, 0xFF1A1715.toInt())
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
                rect(buf, ITEM_S, 5, 3, 6, 4, 0xFFE6B44C.toInt()); rect(buf, ITEM_S, 6, 7, 4, 5, 0xFFD92B3A.toInt())
            }
        }
        return toBmp(buf, ITEM_S)
    }
}
