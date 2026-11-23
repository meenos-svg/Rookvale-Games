package com.example

import com.example.game.cloud.CloudKeyValueStore
import com.example.game.cloud.IsoTimestampUtil
import com.example.game.cloud.LocalCloudBackupStore
import com.example.game.cloud.SupabaseCloudSaveRepository
import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.Direction
import com.example.game.model.EquippedGear
import com.example.game.model.MonsterType
import com.example.game.model.Vocation
import com.example.game.multiplayer.LocalPlayerPresencePayload
import com.example.game.multiplayer.RealtimeMultiplayerManager
import com.example.game.multiplayer.RemotePlayerState
import com.example.ui.screens.ArmorVisualKind
import com.example.ui.screens.BootsVisualKind
import com.example.ui.screens.HelmetVisualKind
import com.example.ui.screens.IsometricCamera
import com.example.ui.screens.OffhandVisualKind
import com.example.ui.screens.OrganicMonsterRenderer
import com.example.ui.screens.PaperDollAppearance
import com.example.ui.screens.ScreenFacing
import com.example.ui.screens.ScreenFacingResolver
import com.example.ui.screens.WeaponVisualKind
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
}
