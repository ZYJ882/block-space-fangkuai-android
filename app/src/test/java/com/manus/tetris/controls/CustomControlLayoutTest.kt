package com.manus.tetris.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomControlLayoutTest {
    @Test
    fun movingToOccupiedSlotSwapsActionsAndPreservesFiveUniqueSlots() {
        val standard = CustomControlLayout.standard()
        val moved = standard.moveActionTo(ControlAction.HARD_DROP, ControlSlot.LEFT_TOP_LEFT)

        assertEquals(ControlSlot.LEFT_TOP_LEFT, moved.slotOf(ControlAction.HARD_DROP))
        assertEquals(ControlSlot.RIGHT_BOTTOM, moved.slotOf(ControlAction.MOVE_LEFT))
        assertEquals(ControlAction.entries.size, moved.bindings.values.toSet().size)
    }

    @Test
    fun decoderFallsBackWhenPersistedLayoutIsIncompleteOrOverlapping() {
        val fallback = CustomControlLayout.standard()
        val incomplete = CustomControlLayout.decode("MOVE_LEFT:LEFT_TOP_LEFT", fallback)
        val overlap = CustomControlLayout.decode(
            "MOVE_LEFT:LEFT_TOP_LEFT;MOVE_RIGHT:LEFT_TOP_LEFT;SOFT_DROP:LEFT_BOTTOM;ROTATE:RIGHT_TOP;HARD_DROP:RIGHT_BOTTOM",
            fallback
        )

        assertEquals(fallback, incomplete)
        assertEquals(fallback, overlap)
        assertTrue(fallback.bindings.values.toSet().size == ControlAction.entries.size)
        assertNotEquals(ControlSlot.LEFT_TOP_LEFT, fallback.slotOf(ControlAction.HARD_DROP))
    }
}
