package com.example

import com.example.game.engine.GameEngine
import com.example.game.model.CorpseEntity
import com.example.game.model.GameWorldState
import com.example.game.model.ItemStack
import com.example.game.model.LOOT_OWNER_PROTECTION_MS
import com.example.game.model.MonsterEntity
import com.example.game.model.MonsterType
import com.example.game.model.Vocation
import com.example.game.multiplayer.LocalPlayerPresencePayload
import com.example.game.multiplayer.PlayerTradeCoordinator
import com.example.game.multiplayer.RealtimeMultiplayerManager
import com.example.game.multiplayer.RemotePlayerState
import com.example.game.multiplayer.SharedLootClaimEvent
import com.example.game.multiplayer.SharedLootSpawnEvent
import com.example.game.multiplayer.TradeSessionStatus
import com.example.game.multiplayer.TradeSignalEvent
import com.example.game.multiplayer.TradeSignalType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedLootAndTradeTest {

    @Test
    fun monsterKillLootLockedForBystanderForFiveSecondsThenPublic() {
        val nowMs = 10_000L
        val rat = MonsterEntity(
            id = 42,
            type = MonsterType.CAVE_RAT,
            floor = 0,
            x = 200,
            y = 200,
            spawnX = 200,
            spawnY = 200,
            hp = 10,
            maxHp = 20
        )
        val killerCorpse = GameEngine.generateMonsterCorpse(
            m = rat,
            nowMs = nowMs,
            ownerPlayerId = "p_killer",
            ownerName = "Roland"
        ).copy(gold = 35, items = listOf(ItemStack("cheese", 1)))

        assertEquals(nowMs + LOOT_OWNER_PROTECTION_MS, killerCorpse.publicAtMs)
        assertFalse("Owner must be allowed to loot immediately at 0s", killerCorpse.isOwnerLockedFor("p_killer", nowMs))
        assertTrue("Bystander must be locked at 0s", killerCorpse.isOwnerLockedFor("p_bystander", nowMs))
        assertTrue("Bystander must remain locked at 4999ms", killerCorpse.isOwnerLockedFor("p_bystander", nowMs + 4_999L))
        assertFalse("Bystander must be unlocked at exactly 5000ms", killerCorpse.isOwnerLockedFor("p_bystander", nowMs + 5_000L))

        val bystanderBase = GameWorldState(
            saveId = "p_bystander",
            playerName = "Lyra",
            floor = 0,
            playerX = 200,
            playerY = 201,
            gold = 10,
            corpses = listOf(killerCorpse)
        )

        // Blocked at t + 2s
        val blockedGold = GameEngine.lootGoldFromCorpse(bystanderBase, killerCorpse.id, nowMs + 2_000L)
        assertEquals(10, blockedGold.gold)
        assertEquals(35, blockedGold.corpses.first().gold)
        assertTrue(blockedGold.statusLog.last().contains("protected"))

        val blockedItem = GameEngine.lootItemFromCorpse(bystanderBase, killerCorpse.id, 0, nowMs + 4_500L)
        assertEquals(0, blockedItem.itemCount("cheese"))

        val blockedAll = GameEngine.lootAllFromCorpse(bystanderBase, killerCorpse.id, nowMs + 4_999L)
        assertEquals(10, blockedAll.gold)
        assertEquals(0, blockedAll.itemCount("cheese"))

        // Allowed after 5 seconds (t + 5s)
        val lootedAll = GameEngine.lootAllFromCorpse(bystanderBase, killerCorpse.id, nowMs + 5_000L)
        assertEquals(45, lootedAll.gold)
        assertEquals(1, lootedAll.itemCount("cheese"))
        assertTrue("Emptied corpse should disappear once public looter claims everything", lootedAll.corpses.isEmpty())
    }

    @Test
    fun droppedBackpackItemSpawnsFiveSecondProtectedGroundBagAndSyncsClaim() {
        val nowMs = 20_000L
        val dropper = GameWorldState(
            saveId = "p_dropper",
            playerName = "Aldric",
            floor = 0,
            playerX = 150,
            playerY = 160,
            backpack = listOf(ItemStack("health_potion", 2))
        )

        val afterDrop = GameEngine.dropItem(dropper, "health_potion", nowMs)
        assertEquals(1, afterDrop.itemCount("health_potion"))
        assertEquals(1, afterDrop.corpses.size)
        assertEquals(1, afterDrop.outboundLootSpawns.size)

        val bag = afterDrop.corpses.first()
        assertTrue(bag.isDroppedBag)
        assertEquals("p_dropper", bag.ownerPlayerId)
        assertEquals(nowMs + 5_000L, bag.publicAtMs)
        assertEquals(listOf(ItemStack("health_potion", 1)), bag.items)

        // Bystander receives spawn via applyRemoteLootSpawn
        val spawnEvent = SharedLootSpawnEvent(
            corpseId = bag.id,
            floor = bag.floor,
            x = bag.x,
            y = bag.y,
            label = bag.label,
            ownerPlayerId = "p_dropper",
            ownerName = "Aldric",
            isDroppedBag = true,
            gold = 0,
            items = bag.items,
            lockRemainingMs = 5_000L,
            lifetimeRemainingMs = 90_000L
        )
        val bystanderNow = 50_000L // Completely different device uptime epoch!
        val bystanderWithBag = GameEngine.applyRemoteLootSpawn(
            state = GameWorldState(saveId = "p_other", playerName = "Mira", floor = 0, playerX = 150, playerY = 161),
            event = spawnEvent,
            nowMs = bystanderNow
        )
        assertEquals(1, bystanderWithBag.corpses.size)
        // Blocked at +3s on bystander's own uptime clock
        val tooEarly = GameEngine.lootItemFromCorpse(bystanderWithBag, bag.id, 0, bystanderNow + 3_000L)
        assertEquals(0, tooEarly.itemCount("health_potion"))

        // Allowed at +5s
        val claimed = GameEngine.lootItemFromCorpse(bystanderWithBag, bag.id, 0, bystanderNow + 5_000L)
        assertEquals(1, claimed.itemCount("health_potion"))
        assertTrue(claimed.corpses.isEmpty())

        // Original dropper receives loot_claim and removes the emptied bag
        val dropperSynced = GameEngine.applyRemoteLootClaim(
            state = afterDrop,
            event = SharedLootClaimEvent(
                corpseId = bag.id,
                floor = 0,
                claimerId = "p_other",
                claimerName = "Mira",
                remainingGold = 0,
                remainingItems = emptyList(),
                emptied = true
            )
        )
        assertTrue("Claim broadcast must remove bag from dropper's floor", dropperSynced.corpses.isEmpty())
    }

    @Test
    fun realtimeManagerRoundTripsLootSpawnLootClaimAndTradeSignalFrames() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val mgr = RealtimeMultiplayerManager(scope = scope)
        mgr.connect(
            LocalPlayerPresencePayload(
                playerId = "p_b",
                name = "Lyra",
                level = 12,
                vocation = Vocation.PALADIN,
                tunicColorIndex = 0,
                hairColorIndex = 0,
                x = 200,
                y = 200,
                floor = 0,
                hp = 200,
                maxHp = 200,
                joinedAt = 100L
            )
        )
        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"phx_reply","payload":{"status":"ok"},"ref":"1"}"""
        )

        val spawns = mutableListOf<SharedLootSpawnEvent>()
        val claims = mutableListOf<SharedLootClaimEvent>()
        val trades = mutableListOf<TradeSignalEvent>()
        val j1 = scope.launch { mgr.lootSpawnEvents.collect { spawns.add(it) } }
        val j2 = scope.launch { mgr.lootClaimEvents.collect { claims.add(it) } }
        val j3 = scope.launch { mgr.tradeSignalEvents.collect { trades.add(it) } }

        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"broadcast","payload":{"event":"loot_spawn","payload":{"cid":"drop_99","floor":0,"x":201,"y":200,"label":"Roland's Dropped Bag","oid":"p_a","oname":"Roland","drop":true,"gold":50,"items":[{"id":"health_potion","c":2}],"lockMs":5000,"lifeMs":90000}}}"""
        )
        assertEquals(1, spawns.size)
        assertEquals("drop_99", spawns.first().corpseId)
        assertTrue(spawns.first().isDroppedBag)
        assertEquals(50, spawns.first().gold)
        assertEquals(5000L, spawns.first().lockRemainingMs)

        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:floor:0","event":"broadcast","payload":{"event":"loot_claim","payload":{"cid":"drop_99","floor":0,"pid":"p_a","pname":"Roland","gold":0,"items":[],"empty":true}}}"""
        )
        assertEquals(1, claims.size)
        assertTrue(claims.first().emptied)

        mgr.handleIncomingPhoenixFrame(
            """{"topic":"realtime:rookvale:world","event":"broadcast","payload":{"event":"trade_signal","payload":{"type":"INVITE","tid":"tr_abc123","fromId":"p_a","fromName":"Roland","fromLvl":18,"fromVoc":"KNIGHT","toId":"p_b","gold":0,"items":[],"locked":false,"confirmed":false,"ver":0}}}"""
        )
        assertEquals(1, trades.size)
        assertEquals(TradeSignalType.INVITE, trades.first().type)
        assertEquals("tr_abc123", trades.first().tradeId)
        assertEquals("p_a", trades.first().fromPlayerId)

        j1.cancel()
        j2.cancel()
        j3.cancel()
    }

    @Test
    fun playerTradeTwoStepLockConfirmSwapsItemsAndGoldAndResetsLockOnEdit() {
        var worldA = GameWorldState(
            saveId = "p_a",
            playerName = "Roland",
            level = 15,
            vocation = Vocation.KNIGHT,
            gold = 500,
            backpack = listOf(ItemStack("health_potion", 5))
        )
        var worldB = GameWorldState(
            saveId = "p_b",
            playerName = "Lyra",
            level = 14,
            vocation = Vocation.PALADIN,
            gold = 200,
            backpack = listOf(ItemStack("mana_potion", 4))
        )
        val completedA = mutableSetOf<String>()
        val completedB = mutableSetOf<String>()
        val now = 100_000L

        val peerB = RemotePlayerState(
            playerId = "p_b",
            name = "Lyra",
            level = 14,
            vocation = Vocation.PALADIN,
            tunicColorIndex = 0,
            hairColorIndex = 0,
            x = 201,
            y = 200,
            floor = 0,
            hp = 200,
            maxHp = 200
        )

        // 1. A invites B
        val inviteRes = PlayerTradeCoordinator.startOutgoingInvite(worldA, null, peerB, now)
        var sessA = inviteRes.session
        assertEquals(TradeSessionStatus.INVITING, sessA?.status)
        assertNotNull(inviteRes.outboundSignal)

        // 2. B receives invite & accepts
        val bRecvInvite = PlayerTradeCoordinator.handleIncomingSignal(worldB, null, inviteRes.outboundSignal!!, completedB, now)
        var sessB = bRecvInvite.session
        assertEquals(TradeSessionStatus.INCOMING_INVITE, sessB?.status)

        val bAccept = PlayerTradeCoordinator.acceptIncomingInvite(worldB, sessB)
        sessB = bAccept.session
        assertEquals(TradeSessionStatus.NEGOTIATING, sessB?.status)

        val aRecvAccept = PlayerTradeCoordinator.handleIncomingSignal(worldA, sessA, bAccept.outboundSignal!!, completedA, now)
        sessA = aRecvAccept.session
        assertEquals(TradeSessionStatus.NEGOTIATING, sessA?.status)

        // 3. A offers 150 gold + 2x health_potion; B offers 50 gold + 3x mana_potion
        val aGold = PlayerTradeCoordinator.setLocalGold(worldA, sessA, 150)
        sessA = aGold.session
        sessB = PlayerTradeCoordinator.handleIncomingSignal(worldB, sessB, aGold.outboundSignal!!, completedB, now).session

        val aItem = PlayerTradeCoordinator.addLocalItem(worldA, sessA, "health_potion", 2)
        sessA = aItem.session
        sessB = PlayerTradeCoordinator.handleIncomingSignal(worldB, sessB, aItem.outboundSignal!!, completedB, now).session

        val bGold = PlayerTradeCoordinator.setLocalGold(worldB, sessB, 50)
        sessB = bGold.session
        sessA = PlayerTradeCoordinator.handleIncomingSignal(worldA, sessA, bGold.outboundSignal!!, completedA, now).session

        val bItem = PlayerTradeCoordinator.addLocalItem(worldB, sessB, "mana_potion", 3)
        sessB = bItem.session
        sessA = PlayerTradeCoordinator.handleIncomingSignal(worldA, sessA, bItem.outboundSignal!!, completedA, now).session

        // 4. Both lock
        val aLock1 = PlayerTradeCoordinator.toggleLocalLock(worldA, sessA)
        sessA = aLock1.session
        sessB = PlayerTradeCoordinator.handleIncomingSignal(worldB, sessB, aLock1.outboundSignal!!, completedB, now).session

        val bLock1 = PlayerTradeCoordinator.toggleLocalLock(worldB, sessB)
        sessB = bLock1.session
        sessA = PlayerTradeCoordinator.handleIncomingSignal(worldA, sessA, bLock1.outboundSignal!!, completedA, now).session
        assertTrue(sessA!!.localOffer.locked && sessA.partnerOffer.locked)
        assertTrue(sessB!!.localOffer.locked && sessB.partnerOffer.locked)

        // 5. Bait-and-switch protection: A changes gold from 150 -> 100 -> BOTH sides MUST auto-unlock!
        val aEditAfterLock = PlayerTradeCoordinator.setLocalGold(worldA, sessA, 100)
        sessA = aEditAfterLock.session
        assertFalse("Editing offer must unlock local side", sessA!!.localOffer.locked)
        assertFalse("Editing offer must unlock partner side", sessA.partnerOffer.locked)
        sessB = PlayerTradeCoordinator.handleIncomingSignal(worldB, sessB, aEditAfterLock.outboundSignal!!, completedB, now).session
        assertFalse("Partner edit must unlock B's lock too", sessB!!.localOffer.locked)
        assertFalse(sessB.partnerOffer.locked)

        // 6. Both re-lock & confirm
        val aLock2 = PlayerTradeCoordinator.toggleLocalLock(worldA, sessA)
        sessA = aLock2.session
        sessB = PlayerTradeCoordinator.handleIncomingSignal(worldB, sessB, aLock2.outboundSignal!!, completedB, now).session

        val bLock2 = PlayerTradeCoordinator.toggleLocalLock(worldB, sessB)
        sessB = bLock2.session
        sessA = PlayerTradeCoordinator.handleIncomingSignal(worldA, sessA, bLock2.outboundSignal!!, completedA, now).session

        val aConfirm = PlayerTradeCoordinator.confirmTrade(worldA, sessA, completedA, now)
        sessA = aConfirm.session
        assertTrue(sessA!!.localOffer.confirmed)
        assertFalse("First confirm should wait for partner", aConfirm.swapCompleted)

        val bRecvAConfirm = PlayerTradeCoordinator.handleIncomingSignal(worldB, sessB, aConfirm.outboundSignal!!, completedB, now)
        sessB = bRecvAConfirm.session
        assertTrue(sessB!!.partnerOffer.confirmed)

        // B confirms second -> B swaps immediately and emits CONFIRM to A
        val bConfirm = PlayerTradeCoordinator.confirmTrade(worldB, sessB, completedB, now)
        assertTrue("Second confirm must finalize swap for B", bConfirm.swapCompleted)
        assertNull(bConfirm.session)
        worldB = bConfirm.worldState

        // A receives B's CONFIRM -> A swaps immediately
        val aFinal = PlayerTradeCoordinator.handleIncomingSignal(worldA, sessA, bConfirm.outboundSignal!!, completedA, now)
        assertTrue("A must finalize swap upon receiving B's confirm", aFinal.swapCompleted)
        assertNull(aFinal.session)
        worldA = aFinal.worldState

        // Verify exact final balances & inventories
        assertEquals(450, worldA.gold) // 500 - 100 + 50
        assertEquals(3, worldA.itemCount("health_potion")) // 5 - 2
        assertEquals(3, worldA.itemCount("mana_potion")) // 0 + 3

        assertEquals(250, worldB.gold) // 200 - 50 + 100
        assertEquals(2, worldB.itemCount("health_potion")) // 0 + 2
        assertEquals(1, worldB.itemCount("mana_potion")) // 4 - 3
    }
}
