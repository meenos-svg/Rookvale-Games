package com.example

import com.example.game.engine.GameEngine
import com.example.game.model.ItemStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepotMaterialsTest {

    @Test
    fun depositAllMaterialsStoresCraftingGoodsAndKeepsGearPotionsAndBait() {
        val hero = GameEngine.createNewWorld("Hoarder")
        val pack = hero.backpack + listOf(
            ItemStack("cinderite_ore", 7),
            ItemStack("molten_core", 2),
            ItemStack("cinderite_bar", 3),
            ItemStack("emberfin_sturgeon", 4),
            ItemStack("health_potion", 5),
            ItemStack("cinder_emberworm", 10),
            ItemStack("cinderite_crown", 1)
        )
        val start = hero.copy(backpack = pack, depot = emptyList())
        val after = GameEngine.depositAllMaterials(start)

        fun depot(id: String) = after.depot.filter { it.itemId == id }.sumOf { it.count }
        assertEquals(7, depot("cinderite_ore"))
        assertEquals(2, depot("molten_core"))
        assertEquals(3, depot("cinderite_bar"))
        assertEquals(4, depot("emberfin_sturgeon"))
        assertEquals(0, after.itemCount("cinderite_ore"))
        // Gear, potions and bait stay in the bag
        for (kept in listOf("health_potion", "cinder_emberworm", "cinderite_crown")) {
            assertEquals(kept, start.itemCount(kept), after.itemCount(kept))
            assertEquals(kept, 0, depot(kept))
        }
        assertTrue(after.statusLog.last().startsWith("Stored "))

        // Nothing to store: a friendly message and no changes
        val again = GameEngine.depositAllMaterials(after)
        assertEquals(after.depot, again.depot)
        assertTrue(again.statusLog.last().contains("no materials"))
    }
}
