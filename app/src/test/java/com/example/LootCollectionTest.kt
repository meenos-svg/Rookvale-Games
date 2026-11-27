package com.example

import com.example.game.engine.AutoLootMode
import com.example.game.engine.GameEngine
import com.example.game.engine.LootCollection
import com.example.game.engine.NpcInteractions
import com.example.game.model.ActivePanel
import com.example.game.model.CorpseEntity
import com.example.game.model.GameWorldState
import com.example.game.model.ItemStack
import com.example.game.model.REGULAR_CORPSE_LIFETIME_MS
import com.example.game.world.WorldMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LootCollectionTest {

    /** A hero standing in the middle of a 13x13 patch of open ground on floor 0. */
    private fun hero(): GameWorldState {
        val f = WorldMaps.getFloor(0)
        val start = WorldMaps.npcs.first { it.name == "Captain Hale" }
        for (r in 0..40) for (dy in -r..r) for (dx in -r..r) {
            val cx = start.x + dx; val cy = start.y + dy
            var ok = true
            for (oy in -6..6) for (ox in -6..6) if (!f.tileAt(cx + ox, cy + oy).walkable || WorldMaps.npcs.any { it.floor == 0 && it.x == cx + ox && it.y == cy + oy }) ok = false
            if (ok) return GameEngine.createNewWorld("Looter").copy(floor = 0, playerX = cx, playerY = cy, monsters = emptyList(), corpses = emptyList())
        }
        error("no open ground")
    }

    private fun corpse(id: String, s: GameWorldState, dx: Int, dy: Int, gold: Int = 50, items: List<ItemStack> = listOf(ItemStack("health_potion", 1)), owner: String? = null, now: Long = 1_000L) =
        CorpseEntity(id, s.floor, s.playerX + dx, s.playerY + dy, "Slain Rat", false, "CAVE_RAT", gold, items,
            createdAtMs = now, expiresAtMs = now + REGULAR_CORPSE_LIFETIME_MS, ownerPlayerId = owner, ownerName = owner,
            publicAtMs = if (owner != null) now + 5_000L else 0L)

    @Test
    fun corpsesLastTwoMinutes() = assertEquals(120_000L, REGULAR_CORPSE_LIFETIME_MS)

    @Test
    fun tappingAFarCorpseWalksOverAndOpensIt() {
        val h = hero()
        val s0 = h.copy(corpses = listOf(corpse("far", h, 4, 0)))
        var s = GameEngine.handleTileTap(s0, h.playerX + 4, h.playerY, 1_000L)
        assertEquals("far", s.pendingLootCorpseId)
        assertTrue(s.walkPath.isNotEmpty() || s.activePanel == ActivePanel.LOOT_CORPSE)
        var t = 1_000L
        repeat(40) { if (s.walkPath.isNotEmpty()) { t += 600L; s = GameEngine.stepAlongPath(s, t) } }
        assertEquals(ActivePanel.LOOT_CORPSE, s.activePanel)
        assertEquals("far", s.inspectingCorpseId)
        assertNull(s.pendingLootCorpseId)
    }

    @Test
    fun lootNearbyEmptiesEveryReachableCorpseButRespectsProtection() {
        val h = hero()
        val s0 = h.copy(corpses = listOf(
            corpse("a", h, 1, 0), corpse("b", h, -2, 2), corpse("c", h, 0, -2),
            corpse("far", h, 5, 0),
            corpse("locked", h, 1, 1, owner = "someone_else")
        ))
        val s = NpcInteractions.handleAction(s0, "loot_nearby", 2_000L)
        assertEquals(setOf("far", "locked"), s.corpses.map { it.id }.toSet())
        assertEquals(h.gold + 150, s.gold)
        assertEquals(h.itemCount("health_potion") + 3, s.itemCount("health_potion"))
    }

    @Test
    fun autoLootTakesGoldOrRememberedItemsAndUpdatesTheMultiplayerCopy() {
        val h = hero()
        // Off: nothing happens
        val c = corpse("k", h, 1, 0, items = listOf(ItemStack("bile_gland", 2), ItemStack("health_potion", 1)))
        val base = h.copy(corpses = listOf(c), outboundLootSpawns = listOf(c))
        assertEquals(base, LootCollection.autoLootOnKill(base, "k", 1_000L))

        // Gold only
        val gold = LootCollection.autoLootOnKill(LootCollection.setMode(base, AutoLootMode.GOLD), "k", 1_000L)
        assertEquals(h.gold + 50, gold.gold)
        assertEquals(0, gold.corpses.single().gold)
        assertEquals(0, gold.outboundLootSpawns.single().gold)

        // Smart: remembers what you pick by hand
        var smart = LootCollection.setMode(base, AutoLootMode.SMART)
        val other = corpse("pick", h, -1, 0, gold = 0, items = listOf(ItemStack("bile_gland", 1)))
        smart = GameEngine.lootItemFromCorpse(smart.copy(corpses = smart.corpses + other), "pick", 0, 1_000L)
        assertTrue("bile_gland" in LootCollection.rememberedItems(smart))
        smart = LootCollection.autoLootOnKill(smart, "k", 1_000L)
        val left = smart.corpses.single { it.id == "k" }
        assertEquals(listOf(ItemStack("health_potion", 1)), left.items)
        assertEquals(left, smart.outboundLootSpawns.single())

        // Too far away: no auto-loot
        val far = corpse("faraway", h, 4, 0)
        val farState = LootCollection.setMode(h.copy(corpses = listOf(far)), AutoLootMode.GOLD)
        assertEquals(50, LootCollection.autoLootOnKill(farState, "faraway", 1_000L).corpses.single().gold)

        // Forget
        assertTrue(LootCollection.rememberedItems(NpcInteractions.handleAction(smart, "autoloot_forget", 1_000L)).isEmpty())
    }

    @Test
    fun corpsesStackedOnOneTileOpenOneAfterAnother() {
        val h = hero()
        val s0 = h.copy(corpses = listOf(corpse("under", h, 1, 0), corpse("top", h, 1, 0)))
        var s = GameEngine.handleTileTap(s0, h.playerX + 1, h.playerY, 1_000L)
        assertEquals("top", s.inspectingCorpseId)
        s = GameEngine.lootAllFromCorpse(s, "top", 2_000L)
        assertEquals(ActivePanel.LOOT_CORPSE, s.activePanel)
        assertEquals("under", s.inspectingCorpseId)
        s = GameEngine.lootAllFromCorpse(s, "under", 2_000L)
        assertEquals(ActivePanel.NONE, s.activePanel)
        assertTrue(s.corpses.isEmpty())
    }
}
