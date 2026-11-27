package com.example

import com.example.game.engine.GameEngine
import com.example.game.model.ActivePanel
import com.example.game.model.HotbarActions
import com.example.game.model.HudElement
import com.example.game.model.HudLayout
import com.example.game.model.ItemStack
import com.example.game.model.VitalsStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudCustomizationTest {

    @Test
    fun hudLayoutRoundTripsThroughItsStoredForm() {
        val layout = HudLayout(vitalsStyle = VitalsStyle.BARS, hotbarSize = 9)
            .withOffset(HudElement.HOTBAR, 12.5f, -40f)
            .withOffset(HudElement.RAIL, -300f, 18f)
            .toggled(HudElement.TRACKER)
            .toggled(HudElement.ORB) // not hideable: ignored
        assertFalse(layout.isVisible(HudElement.TRACKER))
        assertTrue(layout.isVisible(HudElement.ORB))
        assertEquals(layout.offsets, HudLayout.decodeOffsets(layout.encodeOffsets()))
        assertEquals(layout.hidden, HudLayout.decodeHidden(layout.encodeHidden()))
        assertEquals(0f to 0f, layout.offsetOf(HudElement.CHAT))
        // Offsets are clamped so a piece can't be flung away forever
        assertEquals(HudLayout.MAX_SHIFT, layout.withOffset(HudElement.CHAT, 99_999f, 0f).offsetOf(HudElement.CHAT).first)
    }

    @Test
    fun oldTwoSlotSavesBecomeTheFullCustomizableBar() {
        val migrated = HotbarActions.migrate(listOf("fresh_fish", null))
        assertEquals(HotbarActions.MAX_SLOTS, migrated.size)
        assertEquals(HotbarActions.DEFAULT_SLOTS.take(5), migrated.take(5))
        assertEquals("fresh_fish", migrated[5])
        val loaded = GameEngine.fromSaveData(GameEngine.toSaveData(GameEngine.createNewWorld("Old")).copy(customHotbarSlots = listOf("fresh_fish", null)))
        assertEquals("fresh_fish", loaded.customHotbarSlots[5])
    }

    @Test
    fun anySlotTakesItemsOrActions() {
        var s = GameEngine.createNewWorld("Binder").copy(backpack = listOf(ItemStack("fresh_fish", 2), ItemStack("health_potion", 1)))
        s = GameEngine.bindCustomHotbarSlot(s, 0, HotbarActions.OPEN_MAP)
        s = GameEngine.bindCustomHotbarSlot(s, 8, "fresh_fish")
        assertEquals(HotbarActions.OPEN_MAP, s.customHotbarSlots[0])
        assertEquals(ActivePanel.WORLD_MAP, GameEngine.useCustomHotbarSlot(s, 0, 1_000L).activePanel)
        assertEquals("fresh_fish", s.customHotbarSlots[8])
        // Raw materials can't be bound
        assertEquals(s.customHotbarSlots, GameEngine.bindCustomHotbarSlot(s, 3, "copper_ore").customHotbarSlots)
        // Reset restores the classic bar
        assertEquals(HotbarActions.DEFAULT_SLOTS, GameEngine.resetHotbar(s).customHotbarSlots)
        // Orb health side falls back to hotbar food once potions run out
        val hurt = s.copy(hp = 10, backpack = listOf(ItemStack("fresh_fish", 2)))
        val healed = GameEngine.useOrbSide(hurt, health = true, nowMs = 2_000L)
        assertTrue(healed.hp > hurt.hp)
        assertEquals(1, healed.itemCount("fresh_fish"))
    }
}
