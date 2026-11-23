package com.example

import com.example.game.cloud.CloudKeyValueStore
import com.example.game.cloud.IsoTimestampUtil
import com.example.game.cloud.LocalCloudBackupStore
import com.example.game.cloud.SupabaseCloudSaveRepository
import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.Direction
import com.example.game.model.EquippedGear
import com.example.game.model.MonsterType
import com.example.game.model.TileType
import com.example.game.model.Vocation
import com.example.game.multiplayer.LocalPlayerPresencePayload
import com.example.game.multiplayer.RealtimeMultiplayerManager
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.world.WorldMaps
import com.example.ui.screens.ArmorVisualKind
import com.example.ui.screens.ArticulatedHumanoidRenderer
import com.example.ui.screens.BootsVisualKind
import com.example.ui.screens.HelmetVisualKind
import com.example.ui.screens.HumanoidPart
import com.example.ui.screens.IsometricCamera
import com.example.ui.screens.IsometricTileDrawer
import com.example.ui.screens.OffhandVisualKind
import com.example.ui.screens.OrganicMonsterRenderer
import com.example.ui.screens.PaperDollAppearance
import com.example.ui.screens.ScreenFacing
import com.example.ui.screens.ScreenFacingResolver
import com.example.ui.screens.ScreenOctant
import com.example.ui.screens.VolumetricShading
import com.example.ui.screens.WeaponVisualKind
import kotlin.math.PI
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class PaperDollMapStore : CloudKeyValueStore {
    private val map = mutableMapOf<String, Any>()
    override fun getString(key: String): String? = map[key] as? String
    override fun getLong(key: String, defaultValue: Long): Long = (map[key] as? Long) ?: defaultValue
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = (map[key] as? Boolean) ?: defaultValue
    override fun putValues(block: MutableMap<String, Any?>.() -> Unit) {
        val temp = linkedMapOf<String, Any?>()
        temp.block()
        for ((k, v) in temp) {
            if (v == null) map.remove(k) else map[k] = v
        }
    }
    override fun removeKeys(vararg keys: String) {
        keys.forEach { map.remove(it) }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArticulatedEntityAndPaperDollTest {

    @Test
    fun screenFacingResolverMapsCardinalsAtDefaultYawAndRotatesWithCamera() {
        val defaultYaw = IsometricCamera.DEFAULT_YAW_DEG // 45 deg
        assertEquals(ScreenFacing.FRONT, ScreenFacingResolver.resolve(Direction.SOUTH, defaultYaw).quadrant)
        assertEquals(ScreenFacing.RIGHT, ScreenFacingResolver.resolve(Direction.EAST, defaultYaw).quadrant)
        assertEquals(ScreenFacing.BACK, ScreenFacingResolver.resolve(Direction.NORTH, defaultYaw).quadrant)
        assertEquals(ScreenFacing.LEFT, ScreenFacingResolver.resolve(Direction.WEST, defaultYaw).quadrant)

        // Rotating the camera by +180 degrees flips SOUTH from FRONT to BACK
        assertEquals(ScreenFacing.BACK, ScreenFacingResolver.resolve(Direction.SOUTH, defaultYaw + 180f).quadrant)
        assertEquals(ScreenFacing.FRONT, ScreenFacingResolver.resolve(Direction.NORTH, defaultYaw + 180f).quadrant)
    }

    @Test
    fun screenFacingResolverPrioritizesCombatTargetVectorOverIdleFacing() {
        // Character idle-facing NORTH (BACK), but combat target lies due SOUTH (+0, +3)
        val resolved = ScreenFacingResolver.resolve(
            worldFacing = Direction.NORTH,
            cameraYawDeg = IsometricCamera.DEFAULT_YAW_DEG,
            targetDeltaX = 0f,
            targetDeltaY = 3f
        )
        assertEquals(ScreenFacing.FRONT, resolved.quadrant)
    }

    @Test
    fun paperDollAppearanceMapsWeaponsOffhandArmorHelmetsAndTwoHandedLocking() {
        // Sword + Dwarven Kite Shield + Brass Plate + Viking Helm + Plate Boots
        val knightGear = EquippedGear(
            weaponId = "rookvale_gladius",
            shieldId = "steel_shield",
            armorId = "brass_armor",
            helmetId = "viking_helmet",
            bootsId = "plate_boots"
        )
        val knightLook = PaperDollAppearance.fromEquipment(Vocation.KNIGHT, 0, 1, knightGear)
        assertEquals(WeaponVisualKind.SWORD, knightLook.weaponKind)
        assertEquals(OffhandVisualKind.KITE_SHIELD, knightLook.offhandKind)
        assertEquals(ArmorVisualKind.PLATE, knightLook.armorKind)
        assertEquals(HelmetVisualKind.VIKING_HELM, knightLook.helmetKind)
        assertEquals(BootsVisualKind.PLATE, knightLook.bootsKind)

        // 2H Bow suppresses any shield slot visual cleanly
        val paladinGear = EquippedGear(
            weaponId = "ashwood_shortbow",
            shieldId = "wooden_shield",
            armorId = "leather_armor",
            helmetId = "leather_helmet",
            bootsId = "leather_boots"
        )
        val paladinLook = PaperDollAppearance.fromEquipment(Vocation.PALADIN, 1, 2, paladinGear)
        assertEquals(WeaponVisualKind.BOW, paladinLook.weaponKind)
        assertTrue(paladinLook.isTwoHanded)
        assertEquals(OffhandVisualKind.NONE, paladinLook.offhandKind)
        assertEquals(ArmorVisualKind.LEATHER, paladinLook.armorKind)
        assertEquals(HelmetVisualKind.LEATHER_CAP, paladinLook.helmetKind)
        assertEquals(BootsVisualKind.TRAVELER, paladinLook.bootsKind)

        // 1H Wand automatically conjures an offhand arcane focus orb when no shield is held
        val mageGear = EquippedGear(
            weaponId = "sunbrass_scepter",
            shieldId = null,
            armorId = "mystic_robe",
            helmetId = "mage_hat"
        )
        val mageLook = PaperDollAppearance.fromEquipment(Vocation.MAGE, 2, 0, mageGear)
        assertEquals(WeaponVisualKind.WAND, mageLook.weaponKind)
        assertEquals(OffhandVisualKind.ARCANE_ORB, mageLook.offhandKind)
        assertEquals(ArmorVisualKind.ROBE, mageLook.armorKind)
        assertEquals(HelmetVisualKind.WIZARD_HAT, mageLook.helmetKind)

        // Wyrmscale & Dragon Shield tier
        val dragonLook = PaperDollAppearance.fromItemIds(
            vocation = Vocation.KNIGHT,
            tunicColorIndex = 0,
            hairColorIndex = 0,
            weaponId = "wyrmtooth_claymore",
            offhandId = "dragon_shield",
            armorId = "dragon_mail",
            helmetId = "viking_helmet"
        )
        assertEquals(WeaponVisualKind.CLAYMORE, dragonLook.weaponKind)
        assertTrue(dragonLook.isTwoHanded)
        assertEquals(ArmorVisualKind.WYRMSCALE, dragonLook.armorKind)
    }

    @Test
    fun allMonsterSpeciesHavePositiveHeightScales() {
        for (species in MonsterType.entries) {
            val scale = OrganicMonsterRenderer.heightScaleFor(species)
            assertTrue("Expected positive heightScale for $species", scale in 0.6f..1.6f)
        }
        assertTrue(
            OrganicMonsterRenderer.heightScaleFor(MonsterType.DEMON_LORD) >
                OrganicMonsterRenderer.heightScaleFor(MonsterType.WILD_RABBIT)
        )
    }

    @Test
    fun realtimeManagerRoundTripsEquippedGearIdsAndPreservesUnequipClears() {
        var nowMs = 1_750_000_000_000L
        val mgr = RealtimeMultiplayerManager(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            nowProvider = { nowMs },
            animClockProvider = { nowMs }
        )
        mgr.connect(
            LocalPlayerPresencePayload(
                playerId = "p_local",
                name = "Roland",
                level = 12,
                vocation = Vocation.KNIGHT,
                tunicColorIndex = 0,
                hairColorIndex = 0,
                x = 200,
                y = 200,
                floor = 0,
                regionId = DEFAULT_REGION_ID,
                hp = 240,
                maxHp = 240,
                joinedAt = nowMs,
                lastSeenMs = nowMs
            )
        )
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"phx_reply","payload":{"status":"ok"},"ref":"1"}"""
        )

        // Incoming player_state broadcast with full equipped paper-doll gear
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"broadcast","payload":{"event":"player_state","payload":{"playerId":"hero-1","name":"SerAldric","level":25,"vocation":"KNIGHT","x":201,"y":200,"floor":0,"regionId":"rookvale_valley","facing":"SOUTH","hp":420,"maxHp":420,"weaponId":"iron_sword","offhandId":"dwarven_shield","armorId":"brass_armor","helmetId":"viking_helmet","bootsId":"plate_boots","lastSeen":$nowMs}}}"""
        )

        val parsed = mgr.uiState.value.remotePlayers.first { it.playerId == "hero-1" }
        assertEquals("iron_sword", parsed.weaponId)
        assertEquals("dwarven_shield", parsed.offhandId)
        assertEquals("brass_armor", parsed.armorId)
        assertEquals("viking_helmet", parsed.helmetId)
        assertEquals("plate_boots", parsed.bootsId)

        val look = PaperDollAppearance.fromRemotePlayer(parsed)
        assertEquals(WeaponVisualKind.SWORD, look.weaponKind)
        assertEquals(OffhandVisualKind.KITE_SHIELD, look.offhandKind)
        assertEquals(ArmorVisualKind.PLATE, look.armorKind)
        assertEquals(HelmetVisualKind.VIKING_HELM, look.helmetKind)
        assertEquals(BootsVisualKind.PLATE, look.bootsKind)

        // Subsequent broadcast where player unequipped weapon & offhand clears them cleanly to null
        nowMs += 500L
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"broadcast","payload":{"event":"player_state","payload":{"playerId":"hero-1","name":"SerAldric","level":25,"vocation":"KNIGHT","x":201,"y":200,"floor":0,"regionId":"rookvale_valley","facing":"SOUTH","hp":420,"maxHp":420,"weaponId":"","offhandId":"","armorId":"brass_armor","helmetId":"viking_helmet","bootsId":"plate_boots","lastSeen":$nowMs}}}"""
        )
        val parsedUnequipped = mgr.uiState.value.remotePlayers.first { it.playerId == "hero-1" }
        assertNull(parsedUnequipped.weaponId)
        assertNull(parsedUnequipped.offhandId)
        assertEquals("brass_armor", parsedUnequipped.armorId)
    }

    @Test
    fun supabaseOnlinePollHydratesRemotePlayerEquippedPaperDollGear() = runBlocking {
        val nowMs = 1_750_000_000_000L
        val recentIso = IsoTimestampUtil.formatUtc(nowMs - 8_000L)
        val rows = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("player_id", "uuid-mira")
                    put("character_name", "Mira")
                    put("updated_at", recentIso)
                    put(
                        "save_data",
                        JSONObject().apply {
                            put("floor", 0)
                            put("x", 206)
                            put("y", 201)
                            put("level", 22)
                            put("vocationName", "MAGE")
                            put("hp", 240)
                            put("maxHp", 240)
                            put("regionId", "rookvale_valley")
                            put(
                                "equippedItems",
                                JSONObject().apply {
                                    put("weaponId", "cinder_wand")
                                    put("shieldId", "wooden_shield")
                                    put("armorId", "mystic_robe")
                                    put("helmetId", "mage_hat")
                                    put("bootsId", "traveler_boots")
                                }
                            )
                        }
                    )
                }
            )
        }

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(rows.toString().toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val repo = SupabaseCloudSaveRepository(
            localStore = LocalCloudBackupStore(PaperDollMapStore()),
            httpClient = fakeClient,
            urlProvider = { "https://example.supabase.co" },
            anonKeyProvider = { "anon-key" },
            nowProvider = { nowMs }
        )

        val polled = repo.fetchRecentOnlinePlayers(
            sinceEpochMs = nowMs - 60_000L,
            excludePlayerId = "uuid-local",
            excludeCharacterName = "Roland"
        )
        assertEquals(1, polled.size)
        val mira = polled.first()
        assertEquals("cinder_wand", mira.weaponId)
        assertEquals("wooden_shield", mira.offhandId)
        assertEquals("mystic_robe", mira.armorId)
        assertEquals("mage_hat", mira.helmetId)
        assertEquals("traveler_boots", mira.bootsId)

        val miraLook = PaperDollAppearance.fromRemotePlayer(mira)
        assertEquals(WeaponVisualKind.WAND, miraLook.weaponKind)
        assertEquals(OffhandVisualKind.ROUND_SHIELD, miraLook.offhandKind)
        assertEquals(ArmorVisualKind.ROBE, miraLook.armorKind)
        assertEquals(HelmetVisualKind.WIZARD_HAT, miraLook.helmetKind)
        assertEquals(BootsVisualKind.TRAVELER, miraLook.bootsKind)
    }

    @Test
    fun eightDirectionalOctantsAndRearViewsResolveAcrossFull360Rotation() {
        val expected = listOf(
            0f to ScreenOctant.FRONT,
            45f to ScreenOctant.FRONT_RIGHT,
            90f to ScreenOctant.RIGHT,
            135f to ScreenOctant.BACK_RIGHT,
            180f to ScreenOctant.BACK,
            -135f to ScreenOctant.BACK_LEFT,
            -90f to ScreenOctant.LEFT,
            -45f to ScreenOctant.FRONT_LEFT
        )
        for ((deg, oct) in expected) {
            val resolved = ScreenFacingResolver.resolveForRelativeAngleDeg(deg)
            assertEquals("Angle $deg should map to $oct", oct, resolved.octant)
            assertEquals("Rear flag mismatch for $oct", oct.isRear, resolved.isBackFacing)
        }

        // Rotating cameraYaw around a stationary SOUTH-facing hero sweeps smoothly through FRONT -> LEFT -> BACK -> RIGHT
        val yaw0 = ScreenFacingResolver.resolve(Direction.SOUTH, 0f)
        val yaw90 = ScreenFacingResolver.resolve(Direction.SOUTH, 90f)
        val yaw180 = ScreenFacingResolver.resolve(Direction.SOUTH, 180f)
        val yaw270 = ScreenFacingResolver.resolve(Direction.SOUTH, 270f)
        assertEquals(ScreenOctant.FRONT, yaw0.octant)
        assertEquals(ScreenOctant.LEFT, yaw90.octant)
        assertEquals(ScreenOctant.BACK, yaw180.octant)
        assertEquals(ScreenOctant.RIGHT, yaw270.octant)
        assertTrue(yaw180.isBackFacing)
    }

    @Test
    fun humanoidDynamicDepthSortingPlacesFarLimbBehindTorsoAndRearCapeOnBack() {
        // Facing RIGHT (+90°): left arm/offhand is far side (behind torso), right weapon arm is near side (in front)
        val rightOrder = ArticulatedHumanoidRenderer.computePartDrawOrder(
            ScreenFacingResolver.resolveForRelativeAngleDeg(90f)
        )
        assertTrue(rightOrder.indexOf(HumanoidPart.LEFT_ARM_OFFHAND) < rightOrder.indexOf(HumanoidPart.TORSO))
        assertTrue(rightOrder.indexOf(HumanoidPart.RIGHT_ARM_WEAPON) > rightOrder.indexOf(HumanoidPart.TORSO))

        // Facing LEFT (-90°): right weapon arm is far side (behind torso), left offhand arm is near side (in front)
        val leftOrder = ArticulatedHumanoidRenderer.computePartDrawOrder(
            ScreenFacingResolver.resolveForRelativeAngleDeg(-90f)
        )
        assertTrue(leftOrder.indexOf(HumanoidPart.RIGHT_ARM_WEAPON) < leftOrder.indexOf(HumanoidPart.TORSO))
        assertTrue(leftOrder.indexOf(HumanoidPart.LEFT_ARM_OFFHAND) > leftOrder.indexOf(HumanoidPart.TORSO))

        // Facing BACK (180°): cape drapes over back (draws AFTER torso), while front-reaching arms draw BEFORE torso
        val backOrder = ArticulatedHumanoidRenderer.computePartDrawOrder(
            ScreenFacingResolver.resolveForRelativeAngleDeg(180f)
        )
        assertTrue(backOrder.indexOf(HumanoidPart.BACK_CAPE) > backOrder.indexOf(HumanoidPart.TORSO))
        assertTrue(backOrder.indexOf(HumanoidPart.RIGHT_ARM_WEAPON) < backOrder.indexOf(HumanoidPart.TORSO))

        // Facing FRONT (0°): cape sits behind torso
        val frontOrder = ArticulatedHumanoidRenderer.computePartDrawOrder(
            ScreenFacingResolver.resolveForRelativeAngleDeg(0f)
        )
        assertTrue(frontOrder.indexOf(HumanoidPart.BACK_CAPE) < frontOrder.indexOf(HumanoidPart.TORSO))
    }

    @Test
    fun quadrupedLegDepthOrderAndShortestAngleLerpWrapCleanly() {
        // Facing FRONT (0°): back legs draw BEFORE front legs
        val frontLegs = OrganicMonsterRenderer.computeQuadrupedLegOrder(
            ScreenFacingResolver.resolveForRelativeAngleDeg(0f)
        )
        assertTrue(frontLegs.indexOf("BL") < frontLegs.indexOf("FL"))
        assertTrue(frontLegs.indexOf("BR") < frontLegs.indexOf("FR"))

        // Facing BACK (180°): front legs are farther away and draw BEFORE back legs
        val backLegs = OrganicMonsterRenderer.computeQuadrupedLegOrder(
            ScreenFacingResolver.resolveForRelativeAngleDeg(180f)
        )
        assertTrue(backLegs.indexOf("FL") < backLegs.indexOf("BL"))
        assertTrue(backLegs.indexOf("FR") < backLegs.indexOf("BR"))

        // Shortest-arc lerp across +PI / -PI boundary moves smoothly across PI instead of spinning 340 degrees
        val start = (170.0 * PI / 180.0).toFloat()
        val target = (-170.0 * PI / 180.0).toFloat()
        val mid = ScreenFacingResolver.shortestAngleLerp(start, target, 0.5f)
        assertTrue("Expected shortest arc near PI, got $mid", abs(abs(mid) - PI.toFloat()) < 0.05f)

        // Heroic grounded scale (~20% larger than 1.0)
        assertTrue(VolumetricShading.HEROIC_HUMANOID_SCALE in 1.15f..1.25f)
        assertTrue(VolumetricShading.HEROIC_MONSTER_SCALE in 1.12f..1.25f)
    }

    @Test
    fun heroicCameraZoomBoundsHeroHeightAndBossProportionsMeetTargetRatios() {
        assertEquals(0.70f, IsometricCamera.MIN_ZOOM, 0.001f)
        assertEquals(2.00f, IsometricCamera.MAX_ZOOM, 0.001f)
        assertEquals(2.40f, IsometricTileDrawer.TALL_WALL_HEIGHT_MULT, 0.001f)

        val cam = IsometricCamera()
        cam.configure(
            viewportW = 1080f,
            viewportH = 2200f,
            camTileX = 196f,
            camTileY = 192f,
            zoomFactor = 1.0f,
            cameraYawDeg = IsometricCamera.DEFAULT_YAW_DEG
        )

        val defaultSpritePx = cam.tileW * 0.78f
        val heroHeightTiles = (defaultSpritePx * VolumetricShading.HEROIC_HUMANOID_SCALE) / cam.tileH
        assertTrue("Expected hero height in 1.80..2.05 tiles, got $heroHeightTiles", heroHeightTiles in 1.80f..2.05f)

        val eliteAndBosses = listOf(
            MonsterType.CRAG_WOLF,
            MonsterType.ORC_WARRIOR,
            MonsterType.SWAMP_TROLL,
            MonsterType.MINOTAUR,
            MonsterType.CYCLOPS,
            MonsterType.DEMON_LORD,
            MonsterType.ELDER_DRAGON
        )
        for (species in eliteAndBosses) {
            val monsterTiles = (defaultSpritePx * OrganicMonsterRenderer.heightScaleFor(species) * VolumetricShading.HEROIC_MONSTER_SCALE) / cam.tileH
            assertTrue("Expected $species ($monsterTiles tiles) taller than hero ($heroHeightTiles tiles)", monsterTiles > heroHeightTiles)
        }

        val demonTiles = (defaultSpritePx * OrganicMonsterRenderer.heightScaleFor(MonsterType.DEMON_LORD) * VolumetricShading.HEROIC_MONSTER_SCALE) / cam.tileH
        val dragonTiles = (defaultSpritePx * OrganicMonsterRenderer.heightScaleFor(MonsterType.ELDER_DRAGON) * VolumetricShading.HEROIC_MONSTER_SCALE) / cam.tileH
        assertTrue("Expected Demon Lord >= 2.25 tiles tall, got $demonTiles", demonTiles >= 2.25f)
        assertTrue("Expected Elder Dragon >= 2.30 tiles tall, got $dragonTiles", dragonTiles >= 2.30f)
    }

    @Test
    fun foregroundWallCutawayLowersAndSoftensWallsInFrontOfPlayerAcross360Rotation() {
        val cam = IsometricCamera()
        val px = 160f
        val py = 182f

        // Test all 4 diagonal camera yaws: walls closer to the camera (positive depthDelta) lower & fade,
        // while walls behind the player (negative depthDelta) and distant walls remain at full height.
        val yawAndFrontOffset = listOf(
            45f to (2 to 2),
            135f to (2 to -2),
            225f to (-2 to -2),
            315f to (-2 to 2)
        )
        for ((yaw, offset) in yawAndFrontOffset) {
            cam.configure(1080f, 1920f, px, py, 1.0f, yaw)
            val frontWall = IsometricTileDrawer.computeWallCutaway(
                cam = cam,
                wallTx = px.toInt() + offset.first,
                wallTy = py.toInt() + offset.second,
                playerFx = px,
                playerFy = py
            )
            assertTrue("Expected foreground wall at yaw=$yaw to lower, got ${frontWall.heightScale}", frontWall.heightScale < 0.70f)
            assertTrue("Expected foreground wall at yaw=$yaw to soften alpha, got ${frontWall.alpha}", frontWall.alpha < 0.85f)

            val rearWall = IsometricTileDrawer.computeWallCutaway(
                cam = cam,
                wallTx = px.toInt() - offset.first,
                wallTy = py.toInt() - offset.second,
                playerFx = px,
                playerFy = py
            )
            assertEquals("Rear wall at yaw=$yaw should remain full height", 1.0f, rearWall.heightScale, 0.001f)
            assertEquals("Rear wall at yaw=$yaw should remain opaque", 1.0f, rearWall.alpha, 0.001f)

            val distantWall = IsometricTileDrawer.computeWallCutaway(
                cam = cam,
                wallTx = px.toInt() + offset.first * 4,
                wallTy = py.toInt() + offset.second * 4,
                playerFx = px,
                playerFy = py
            )
            assertEquals("Distant wall at yaw=$yaw should remain full height", 1.0f, distantWall.heightScale, 0.001f)
        }
    }

    @Test
    fun partitionedTownRoomsIncludeRichInteriorPropsAndPreserveSafeCoordinates() {
        val surface = WorldMaps.getFloor(0)
        val counts = mutableMapOf<TileType, Int>()
        for (y in 170..215) {
            for (x in 152..238) {
                val t = surface.tileAt(x, y)
                counts[t] = (counts[t] ?: 0) + 1
            }
        }
        assertTrue("Expected wooden beds in partitioned cozy rooms", (counts[TileType.WOOD_BED] ?: 0) >= 6)
        assertTrue("Expected bookshelves in town rooms", (counts[TileType.BOOKSHELF] ?: 0) >= 6)
        assertTrue("Expected glowing smelting furnace in forge room", (counts[TileType.SMELTING_FURNACE] ?: 0) >= 1)
        assertTrue("Expected blacksmith anvil in forge room", (counts[TileType.BLACKSMITH_ANVIL] ?: 0) >= 1)
        assertTrue("Expected stone temple altar", (counts[TileType.TEMPLE_ALTAR] ?: 0) >= 1)
        assertTrue("Expected street lantern posts outside town buildings", (counts[TileType.LANTERN_POST] ?: 0) >= 4)

        // Critical coordinates remain walkable & unblocked
        assertTrue(surface.tileAt(WorldMaps.TEMPLE_SPAWN_X, WorldMaps.TEMPLE_SPAWN_Y).walkable)
        assertTrue(surface.tileAt(WorldMaps.ORACLE_X - 1, WorldMaps.ORACLE_Y).walkable)
        assertTrue(surface.tileAt(162, 204).walkable) // Alaric
        assertTrue(surface.tileAt(193, 179).walkable) // Brother Alden
    }
}
