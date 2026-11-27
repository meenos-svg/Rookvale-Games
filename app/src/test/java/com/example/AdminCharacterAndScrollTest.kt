package com.example

import com.example.game.cloud.AdminAccountPolicy
import com.example.game.cloud.CloudAuthResult
import com.example.game.cloud.CloudKeyValueStore
import com.example.game.cloud.CloudSession
import com.example.game.cloud.LocalCloudBackupStore
import com.example.game.cloud.SupabaseCloudSaveRepository
import com.example.game.engine.GameEngine
import com.example.game.model.AdminEquipmentClassification
import com.example.game.model.DEFAULT_REGION_ID
import com.example.game.model.DEFAULT_TIME_OF_DAY_HOURS
import com.example.game.model.Direction
import com.example.game.model.ItemStack
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.ProjectileKind
import com.example.game.model.Vocation
import com.example.game.multiplayer.ChatChannel
import com.example.game.multiplayer.LocalPlayerPresencePayload
import com.example.game.multiplayer.RealtimeMultiplayerManager
import com.example.game.multiplayer.RemoteMonsterAttackEvent
import com.example.game.multiplayer.RemotePlayerState
import com.example.ui.screens.IsometricLightingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class InMemoryCloudKvStore : CloudKeyValueStore {
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
class AdminCharacterAndScrollTest {

    private fun newOfflineCloudRepo(): SupabaseCloudSaveRepository {
        val kv = InMemoryCloudKvStore()
        return SupabaseCloudSaveRepository(
            localStore = LocalCloudBackupStore(kv),
            urlProvider = { "" },
            anonKeyProvider = { "" }
        )
    }

    @Test
    fun adminStatusComesOnlyFromServerAndAdminSaveHydratesAndMigratesLegacyScroll() = runBlocking {
        val repo = newOfflineCloudRepo()

        // No client-side passcode exists any more: offline, "Admin" can neither log in nor be created
        val offlineLogin = repo.loginAndRecover("Admin", "1119")
        assertTrue(offlineLogin is CloudAuthResult.Failure)

        val offlineCreate = repo.createCharacter("admin", "1119") { cleanName ->
            GameEngine.toSaveData(GameEngine.createNewWorld(cleanName, Vocation.ADVENTURER))
        }
        assertTrue(offlineCreate is CloudAuthResult.Failure)

        // A session named "Admin" is not admin unless the server said so
        val baseAdminSave = GameEngine.toSaveData(GameEngine.createNewWorld("Admin", Vocation.ADVENTURER, isAdmin = true))
        val unverified = AdminAccountPolicy.enforceAdminFlag(
            CloudSession(playerId = "a1", characterName = "Admin", pinHash = "x", isAdmin = false),
            baseAdminSave
        )
        assertFalse(unverified.isAdmin)

        // Server-verified Admin session (as returned by rv_load_player / rv_save_player)
        val verifiedSave = AdminAccountPolicy.enforceAdminFlag(
            CloudSession(playerId = "a1", characterName = "admin", pinHash = "x", isAdmin = true),
            baseAdminSave
        )
        val success = CloudAuthResult.Success(
            session = CloudSession(playerId = "a1", characterName = "Admin", pinHash = "x", isAdmin = true),
            saveData = verifiedSave,
            syncedFromCloud = true,
            notice = ""
        )
        assertEquals("Admin", success.saveData.name)
        assertTrue(success.saveData.isAdmin)
        assertEquals(1, success.saveData.level)

        val hydrated = GameEngine.fromSaveData(success.saveData)
        assertEquals("Admin", hydrated.playerName)
        assertTrue(hydrated.isAdmin)
        assertTrue(hydrated.infiniteHealth)
        assertTrue(hydrated.isInvulnerable)
        assertFalse(hydrated.isSpectator)
        assertEquals(DEFAULT_TIME_OF_DAY_HOURS, hydrated.timeOfDayHours, 0.001f)
        assertEquals(hydrated.effectiveMaxHp(), hydrated.hp)
        assertFalse("Backpack Sovereign's Scroll is replaced by the Admin Control HUD overlay", hydrated.hasItem("admin_sovereign_scroll"))

        // Legacy Admin save containing admin_sovereign_scroll cleanly strips it on hydration
        val legacySave = success.saveData.copy(
            backpack = listOf(ItemStack("admin_sovereign_scroll", 1), ItemStack("health_potion", 3))
        )
        val migratedAdmin = GameEngine.fromSaveData(legacySave)
        assertTrue(migratedAdmin.isAdmin)
        assertFalse("Legacy Sovereign's Scroll should be cleaned on load", migratedAdmin.hasItem("admin_sovereign_scroll"))
        assertTrue(migratedAdmin.hasItem("health_potion"))

        // Standard character cannot claim Admin flag or keep adminOnly items
        val stdCreate = repo.createCharacter("Roland", "4321") { cleanName ->
            GameEngine.toSaveData(
                GameEngine.createNewWorld(cleanName, Vocation.ADVENTURER, isAdmin = false)
                    .copy(
                        isAdmin = true, // Attempted spoof
                        backpack = listOf(ItemStack("admin_sovereign_scroll", 1), ItemStack("health_potion", 2))
                    )
            )
        }
        assertTrue(stdCreate is CloudAuthResult.Success)
        val stdSave = (stdCreate as CloudAuthResult.Success).saveData
        assertFalse("Standard player must never get isAdmin=true", stdSave.isAdmin)
        val stdWorld = GameEngine.fromSaveData(stdSave)
        assertFalse(stdWorld.isAdmin)
        assertFalse(stdWorld.infiniteHealth)
        assertFalse(stdWorld.isInvulnerable)
        assertFalse(stdWorld.hasItem("admin_sovereign_scroll"))
    }

    @Test
    fun adminInfiniteHealthBlocksAllCombatBossAoePoisonAndFireDamage() {
        val admin = GameEngine.createNewWorld("Admin", Vocation.ADVENTURER, isAdmin = true).copy(
            floor = -4,
            playerX = 50,
            playerY = 50,
            poisonTicks = 5,
            fireTicks = 4
        )
        val deadlyBoss = MonsterEntity(
            id = 9001,
            type = MonsterType.ELDER_DRAGON,
            x = 51,
            y = 50,
            spawnX = 51,
            spawnY = 50,
            floor = -4,
            hp = MonsterType.ELDER_DRAGON.maxHp,
            maxHp = MonsterType.ELDER_DRAGON.maxHp,
            targetPlayerId = admin.saveId
        )
        val fighting = admin.copy(monsters = listOf(deadlyBoss))

        // Run several combat ticks directly adjacent to an endgame boss with poison & fire ticks active
        var cur = fighting
        for (tick in 1..6) {
            cur = GameEngine.processCombatTick(
                state = cur,
                nowMs = tick * 1500L,
                isFloorHost = true,
                localPlayerId = cur.saveId
            )
        }

        assertEquals("Admin HP must remain locked at effectiveMaxHp", cur.effectiveMaxHp(), cur.hp)
        assertEquals("Admin must be immune to poison", 0, cur.poisonTicks)
        assertEquals("Admin must be immune to fire", 0, cur.fireTicks)
        assertNull("Admin must never die", cur.deathReport)
        assertTrue("Admin must never lose items to Reaper Vault", cur.reaperVault.isEmpty())

        // Remote co-op boss AoE wave must also deal 0 damage and apply 0 burn/poison
        val afterRemoteBoss = GameEngine.applyRemoteMonsterAttack(
            state = cur,
            event = RemoteMonsterAttackEvent(
                monsterId = 9001,
                floor = -4,
                fromX = 51,
                fromY = 50,
                toX = 50,
                toY = 50,
                projectileKindName = ProjectileKind.FIRE_BREATH.name,
                aoeTiles = listOf(50 to 50),
                targetPlayerId = cur.saveId,
                inflictedStatus = "FIRE"
            ),
            localPlayerId = cur.saveId,
            nowMs = 15_000L
        )
        assertEquals(afterRemoteBoss.effectiveMaxHp(), afterRemoteBoss.hp)
        assertEquals(0, afterRemoteBoss.poisonTicks)
        assertEquals(0, afterRemoteBoss.fireTicks)
        assertNull(afterRemoteBoss.deathReport)
    }

    @Test
    fun adminControlInlineLevelAndArmoryAndTimeOfDayAreAdminOnly() {
        val admin = GameEngine.createNewWorld("Admin", Vocation.ADVENTURER, isAdmin = true)

        // Set Level to arbitrary value (e.g. 120) and verify bounds clamping (1..999)
        val lv120 = GameEngine.setAdminLevel(admin, 120, syncSkills = true, nowMs = 200L)
        assertEquals(120, lv120.level)
        assertEquals(lv120.effectiveMaxHp(), lv120.hp)
        assertTrue(lv120.maxHp > admin.maxHp)
        assertTrue(lv120.maxMp > admin.maxMp)
        assertTrue(lv120.skills.sword.level >= 50)

        val clampedMax = GameEngine.setAdminLevel(lv120, 9999, syncSkills = true, nowMs = 300L)
        assertEquals(999, clampedMax.level)
        val clampedMin = GameEngine.setAdminLevel(lv120, -5, syncSkills = true, nowMs = 400L)
        assertEquals(1, clampedMin.level)

        // Grant every individual classification & ALL at Level 1 (verifying level/skill equip bypass for Admin)
        val swordKit = GameEngine.grantAdminEquipmentKit(clampedMin, AdminEquipmentClassification.SWORD, nowMs = 500L)
        assertEquals("cinderite_greatsword", swordKit.equipment.weaponId)
        assertEquals("cinderite_warplate", swordKit.equipment.armorId)
        assertEquals("cinderheart_sovereign_torc", swordKit.equipment.amuletId)
        assertEquals("cinderite_overlord_band", swordKit.equipment.ringId)
        assertEquals(swordKit.effectiveMaxHp(), swordKit.hp)

        val axeKit = GameEngine.grantAdminEquipmentKit(swordKit, AdminEquipmentClassification.AXE, nowMs = 600L)
        assertEquals("cinderite_greataxe", axeKit.equipment.weaponId)

        val clubKit = GameEngine.grantAdminEquipmentKit(axeKit, AdminEquipmentClassification.CLUB, nowMs = 700L)
        assertEquals("cinderite_worldbreaker", clubKit.equipment.weaponId)

        val distKit = GameEngine.grantAdminEquipmentKit(clubKit, AdminEquipmentClassification.DISTANCE, nowMs = 800L)
        assertEquals("cinderite_ashen_bow", distKit.equipment.weaponId)
        assertEquals("venom_shafts", distKit.equipment.ammoId)

        val magicKit = GameEngine.grantAdminEquipmentKit(distKit, AdminEquipmentClassification.MAGIC, nowMs = 900L)
        assertEquals("cinderite_soulspire", magicKit.equipment.weaponId)
        assertEquals("sd_rune", magicKit.boundAttackRuneId)

        val allKit = GameEngine.grantAdminEquipmentKit(clampedMin, AdminEquipmentClassification.ALL, nowMs = 1000L)
        assertTrue(allKit.hasItem("cinderite_greataxe"))
        assertTrue(allKit.hasItem("cinderite_worldbreaker"))
        assertTrue(allKit.hasItem("cinderite_ashen_bow"))
        assertTrue(allKit.hasItem("cinderite_soulspire"))
        assertTrue(allKit.hasItem("cinderite_bastion"))
        assertEquals("cinderite_greatsword", allKit.equipment.weaponId)

        // Local 24-hour Time-of-Day adjustment & lighting profile interpolation
        val atNoon = GameEngine.setAdminTimeOfDay(admin, 12.0f)
        assertEquals(12.0f, atNoon.timeOfDayHours, 0.001f)
        val clampedHighTime = GameEngine.setAdminTimeOfDay(admin, 48.0f)
        assertEquals(24.0f, clampedHighTime.timeOfDayHours, 0.001f)
        val clampedLowTime = GameEngine.setAdminTimeOfDay(admin, -5.0f)
        assertEquals(0.0f, clampedLowTime.timeOfDayHours, 0.001f)
        val nanFallback = GameEngine.setAdminTimeOfDay(admin, Float.NaN)
        assertEquals(DEFAULT_TIME_OF_DAY_HOURS, nanFallback.timeOfDayHours, 0.001f)

        val dawnProfile = IsometricLightingEngine.computeTimeOfDayProfile(6.0f)
        val noonProfile = IsometricLightingEngine.computeTimeOfDayProfile(12.0f)
        val duskProfile = IsometricLightingEngine.computeTimeOfDayProfile(19.5f)
        val midnightProfile = IsometricLightingEngine.computeTimeOfDayProfile(0.0f)

        assertEquals("Dawn", dawnProfile.phaseLabel)
        assertEquals("Noon", noonProfile.phaseLabel)
        assertEquals("Dusk", duskProfile.phaseLabel)
        assertEquals("Midnight", midnightProfile.phaseLabel)
        assertEquals("06:00", IsometricLightingEngine.formatTimeOfDay(6.0f))
        assertEquals("19:30", IsometricLightingEngine.formatTimeOfDay(19.5f))
        assertTrue("Midnight should be darker than Noon", midnightProfile.darknessFactor > noonProfile.darknessFactor)
        assertTrue("Midnight torches should glow brighter than Noon", midnightProfile.pointLightBoost > noonProfile.pointLightBoost)
        assertTrue("Midnight ambient alpha should exceed Noon", midnightProfile.ambientOverlay.alpha > noonProfile.ambientOverlay.alpha)

        // Non-Admin accounts are strictly blocked from all Admin Control mutations
        val normalPlayer = GameEngine.createNewWorld("Aldric", Vocation.ADVENTURER, isAdmin = false)
        assertEquals(1, GameEngine.setAdminLevel(normalPlayer, 500).level)
        val blockedGrant = GameEngine.grantAdminEquipmentKit(normalPlayer, AdminEquipmentClassification.ALL)
        assertEquals(normalPlayer.equipment, blockedGrant.equipment)
        assertFalse(blockedGrant.hasItem("cinderite_greatsword"))
        assertFalse(GameEngine.toggleAdminSpectatorMode(normalPlayer, true).isSpectator)
        assertEquals(DEFAULT_TIME_OF_DAY_HOURS, GameEngine.setAdminTimeOfDay(normalPlayer, 12.0f).timeOfDayHours, 0.001f)
    }

    @Test
    fun spectatorModeMakesMonstersIgnoreAdminAndHidesAdminFromOtherPlayers() {
        val admin = GameEngine.createNewWorld("Admin", Vocation.ADVENTURER, isAdmin = true).copy(
            floor = -2,
            playerX = 40,
            playerY = 40
        )
        val nearbyMonster = MonsterEntity(
            id = 505,
            type = MonsterType.GOREK_RAIDER,
            x = 42,
            y = 40,
            spawnX = 42,
            spawnY = 40,
            floor = -2,
            hp = MonsterType.GOREK_RAIDER.maxHp,
            maxHp = MonsterType.GOREK_RAIDER.maxHp,
            targetPlayerId = admin.saveId
        )
        val worldWithFoe = admin.copy(monsters = listOf(nearbyMonster), targetMonsterId = 505)

        // Enabling Spectator Mode clears existing aggro immediately and keeps monster from chasing/attacking
        val spectating = GameEngine.toggleAdminSpectatorMode(worldWithFoe, true, nowMs = 1000L)
        assertTrue(spectating.isSpectator)
        assertNull(spectating.targetMonsterId)
        assertNull(spectating.monsters.first().targetPlayerId)

        val afterTick = GameEngine.processCombatTick(
            state = spectating,
            nowMs = 3000L,
            isFloorHost = true,
            localPlayerId = spectating.saveId
        )
        assertNull("Monsters must not re-aggro spectating Admin", afterTick.monsters.first().targetPlayerId)
        assertEquals(afterTick.effectiveMaxHp(), afterTick.hp)

        // Disabling Spectator Mode restores normal visibility to monsters
        val visibleAgain = GameEngine.toggleAdminSpectatorMode(afterTick, false, nowMs = 4000L)
        assertFalse(visibleAgain.isSpectator)

        // Verify RealtimeMultiplayerManager encodes isAdmin, senderIsAdmin, and filters out spectating remote players
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val mp = RealtimeMultiplayerManager(
                scope = scope,
                nowProvider = { 2500L },
                animClockProvider = { 2500L }
            )
            mp.sendChatMessage(
                rawText = "Testing royal crown chat",
                channel = ChatChannel.WORLD,
                senderName = "Admin",
                floor = 0,
                senderIsAdmin = true
            )
            val sentMsg = mp.uiState.value.chatMessages.lastOrNull()
            assertNotNull(sentMsg)
            assertEquals("Admin", sentMsg!!.senderName)
            assertTrue("Admin chat messages must carry senderIsAdmin=true", sentMsg.senderIsAdmin)

            val visibleAdmin = RemotePlayerState(
                playerId = "admin-visible",
                name = "Admin",
                level = 40,
                vocation = Vocation.KNIGHT,
                tunicColorIndex = 0,
                hairColorIndex = 0,
                x = 120,
                y = 120,
                floor = 0,
                regionId = DEFAULT_REGION_ID,
                hp = 500,
                maxHp = 500,
                joinedAt = 1000L,
                lastSeenMs = 2000L,
                isAdmin = true,
                isSpectator = false
            )
            val hiddenAdmin = visibleAdmin.copy(playerId = "admin-spectating", isSpectator = true)
            mp.mergePolledPlayers(
                polled = listOf(visibleAdmin, hiddenAdmin),
                localFloor = 0,
                localRegionId = DEFAULT_REGION_ID,
                localPlayerId = "local-player-1",
                localPlayerName = "Aldric"
            )
            val listed = mp.uiState.value.remotePlayers
            assertEquals(1, listed.size)
            assertEquals("admin-visible", listed.first().playerId)
            assertTrue(listed.first().isAdmin)
            assertFalse(listed.first().isSpectator)

            // Crowns on realtime frames are honored only for server-verified id + name pairs
            assertTrue(mp.isVerifiedAdmin("admin-visible", "Admin"))
            assertFalse("Impersonating the admin's id under another name", mp.isVerifiedAdmin("admin-visible", "Imposter"))
            assertFalse("Claiming the Admin name with an unverified id", mp.isVerifiedAdmin("random-id", "Admin"))

            val payload = LocalPlayerPresencePayload(
                playerId = "admin-1",
                name = "Admin",
                level = 50,
                vocation = Vocation.ADVENTURER,
                tunicColorIndex = 0,
                hairColorIndex = 0,
                x = 100,
                y = 100,
                floor = 0,
                regionId = DEFAULT_REGION_ID,
                hp = 800,
                maxHp = 800,
                joinedAt = 1000L,
                lastSeenMs = 2000L,
                direction = Direction.SOUTH,
                isAdmin = true,
                isSpectator = false
            )
            assertTrue(payload.isAdmin)
            assertFalse(payload.isSpectator)
            assertTrue(AdminAccountPolicy.isAdminName(" admin "))
            assertFalse(AdminAccountPolicy.isAdminName("Administrator"))
        } finally {
            scope.cancel()
        }
    }
}
