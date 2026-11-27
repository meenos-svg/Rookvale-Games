package com.example

import com.example.game.cloud.CloudSavePayloadCodec
import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.ItemStack
import com.example.game.model.PlayerSkills
import com.example.game.model.SkillProgress
import com.example.game.model.SkillType
import com.example.game.model.Vocation
import com.example.ui.screens.IsometricCamera
import com.example.ui.screens.MinimapCoordinateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsometricViewportAndHudTest {

    @Test
    fun isometricProjectionRoundTripsExactGridCoordinatesAcrossZoomLevels() {
        val cam = IsometricCamera()
        for (zoom in listOf(0.65f, 1.0f, 1.45f)) {
            cam.configure(
                viewportW = 2400f,
                viewportH = 1080f,
                camTileX = 47f,
                camTileY = 46f,
                zoomFactor = zoom
            )
            assertEquals("Isometric diamond must maintain 2:1 aspect ratio", cam.tileW * 0.5f, cam.tileH, 0.01f)

            val samples = listOf(47 to 46, 40 to 43, 54 to 49, 45 to 48, 50 to 44)
            for ((tx, ty) in samples) {
                val screen = cam.tileCenterToScreen(tx.toFloat(), ty.toFloat())
                val (pickedX, pickedY) = cam.screenToTile(screen.x, screen.y)
                assertEquals("X mismatch at zoom $zoom", tx, pickedX)
                assertEquals("Y mismatch at zoom $zoom", ty, pickedY)
            }

            val bounds = cam.visibleTileBounds(2400f, 1080f, 96, 96, margin = 2)
            assertTrue("Player X must be inside culled bounds", 47 in bounds.minTx..bounds.maxTx)
            assertTrue("Player Y must be inside culled bounds", 46 in bounds.minTy..bounds.maxTy)
            assertTrue("Strict culling must not draw entire 96x96 map", (bounds.maxTx - bounds.minTx) < 60)
        }
    }

    @Test
    fun minimapTapMapperResolvesCenterAndOffsetCoordinatesWithinFloorBounds() {
        val (cx, cy) = MinimapCoordinateMapper.screenTapToWorldTile(
            tapX = 100f,
            tapY = 100f,
            canvasW = 200f,
            canvasH = 200f,
            playerX = 47,
            playerY = 46,
            radius = 18,
            floorWidth = 96,
            floorHeight = 96
        )
        assertEquals(47, cx)
        assertEquals(46, cy)

        val (offX, offY) = MinimapCoordinateMapper.screenTapToWorldTile(
            tapX = 160f,
            tapY = 140f,
            canvasW = 200f,
            canvasH = 200f,
            playerX = 47,
            playerY = 46,
            radius = 18,
            floorWidth = 96,
            floorHeight = 96
        )
        assertTrue(offX in 0 until 96)
        assertTrue(offY in 0 until 96)
        assertTrue("Offset tap should pick a tile away from player center", offX != 47 || offY != 46)
    }

    @Test
    fun separateMeleeWeaponSkillsTrainPersistAndMigrateLegacySaves() {
        assertEquals(SkillType.FIST, PlayerSkills.meleeSkillTypeForWeapon(null))
        assertEquals(SkillType.CLUB, PlayerSkills.meleeSkillTypeForWeapon("wooden_club"))
        assertEquals(SkillType.SWORD, PlayerSkills.meleeSkillTypeForWeapon("rookvale_gladius"))
        assertEquals(SkillType.AXE, PlayerSkills.meleeSkillTypeForWeapon("bog_cleaver_axe"))

        var world = GameEngine.createNewWorld("iDemon", Vocation.KNIGHT)
        world = GameEngine.advanceSkillPublic(world, SkillType.SWORD, 45.0, 1000L)
        world = GameEngine.advanceSkillPublic(world, SkillType.AXE, 25.0, 2000L)
        assertTrue("Sword skill should advance", world.skills.sword.level > 10 || world.skills.sword.points > 0.0)
        assertTrue("Legacy melee skill mirrors best melee discipline", world.skills.melee.level >= world.skills.sword.level)

        // Round-trip through Supabase save_data JSON
        val encoded = CloudSavePayloadCodec.encode(GameEngine.toSaveData(world))
        val decoded = CloudSavePayloadCodec.decode(encoded, "slot_iso", "iDemon")
        assertEquals(world.skills.sword.level, decoded.skills.sword.level)
        assertEquals(world.skills.axe.level, decoded.skills.axe.level)
        assertEquals(world.skills.fist.level, decoded.skills.fist.level)
        assertEquals(world.skills.club.level, decoded.skills.club.level)

        // Legacy payload missing fist/club/sword/axe inherits melee skill level seamlessly
        val skillsObj = encoded.getJSONObject("skills")
        skillsObj.put("melee", org.json.JSONObject().put("level", 17).put("points", 4.0))
        skillsObj.remove("fist")
        skillsObj.remove("club")
        skillsObj.remove("sword")
        skillsObj.remove("axe")
        val migrated = CloudSavePayloadCodec.decode(encoded, "slot_legacy", "iDemon")
        assertEquals(17, migrated.skills.fist.level)
        assertEquals(17, migrated.skills.club.level)
        assertEquals(17, migrated.skills.sword.level)
        assertEquals(17, migrated.skills.axe.level)
    }

    @Test
    fun battleListTargetingMinimapWalkAndCustomHotbarSlotsWorkEndToEnd() {
        val world = GameEngine.createNewWorld("iDemon", Vocation.MAGE).copy(
            activePanel = ActivePanel.WORLD_MAP,
            hp = 80,
            maxHp = 150,
            backpack = listOf(ItemStack("fresh_fish", 3), ItemStack("health_potion", 2))
        )

        // Minimap / Regional Map auto-walk closes WORLD_MAP and sets A* walkPath
        val walking = GameEngine.walkToCoordinate(world, world.playerX + 2, world.playerY)
        assertEquals(ActivePanel.NONE, walking.activePanel)
        assertTrue("Walk path should be populated", walking.walkPath.isNotEmpty())

        // Battle list direct targeting locks target & closes open panels
        val monster = walking.monsters.first { it.floor == walking.floor }
        val targeted = GameEngine.targetMonsterById(walking.copy(activePanel = ActivePanel.BACKPACK), monster.id)
        assertEquals(monster.id, targeted.targetMonsterId)
        assertEquals(ActivePanel.NONE, targeted.activePanel)

        // Custom hotbar binding, persistence, and usage
        val bound = GameEngine.bindCustomHotbarSlot(targeted, 0, "fresh_fish")
        assertEquals("fresh_fish", bound.customHotbarSlots[0])
        assertEquals(com.example.game.model.HotbarActions.MANA_POTION, bound.customHotbarSlots[1])

        val encoded = CloudSavePayloadCodec.encode(GameEngine.toSaveData(bound))
        val restored = GameEngine.fromSaveData(CloudSavePayloadCodec.decode(encoded, "slot_hb", "iDemon"))
        assertEquals("fresh_fish", restored.customHotbarSlots[0])

        val afterEat = GameEngine.useCustomHotbarSlot(restored, 0, 5000L)
        assertTrue("Eating bound food from custom slot should restore HP", afterEat.hp > restored.hp)
        assertEquals(2, afterEat.itemCount("fresh_fish"))

        val cleared = GameEngine.bindCustomHotbarSlot(afterEat, 0, null)
        assertNull(cleared.customHotbarSlots[0])
        assertNotNull(cleared.statusLog.lastOrNull())
    }
}
