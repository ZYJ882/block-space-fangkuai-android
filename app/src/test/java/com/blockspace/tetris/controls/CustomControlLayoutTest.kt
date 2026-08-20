package com.blockspace.tetris.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomControlLayoutTest {
    private val geometry = ControlAreaGeometry(
        width = 360f,
        height = 132f,
        buttonWidth = 64f,
        buttonHeight = 58f,
        minimumGap = 8f
    )

    @Test
    fun standardLayoutFitsWithinBoundsAndRespectsMinimumGap() {
        val layout = FreeControlLayout.standard()

        assertTrue(layout.isValidFor(geometry))
        ControlAction.entries.forEach { action ->
            val rect = geometry.rect(layout.positionOf(action))
            assertTrue(rect.left >= 0f && rect.top >= 0f)
            assertTrue(rect.right <= geometry.width && rect.bottom <= geometry.height)
        }
    }

    @Test
    fun movingIntoAnotherButtonsHitAreaIsRejected() {
        val layout = FreeControlLayout.standard()
        val occupiedPoint = geometry.toPixel(layout.positionOf(ControlAction.MOVE_RIGHT))

        val result = layout.moveIfValid(ControlAction.MOVE_LEFT, occupiedPoint, geometry)

        assertNull(result)
    }

    @Test
    fun draggingBeyondBoundaryIsClampedToSafeRegion() {
        val layout = FreeControlLayout.standard()
        val moved = layout.moveIfValid(ControlAction.MOVE_LEFT, PixelPoint(-9999f, -9999f), geometry)

        assertNotNull(moved)
        val rect = geometry.rect(moved!!.positionOf(ControlAction.MOVE_LEFT))
        assertEquals(0f, rect.left, 0.001f)
        assertEquals(0f, rect.top, 0.001f)
    }

    @Test
    fun incompleteSavedLayoutFallsBackToStandardLayout() {
        val fallback = FreeControlLayout.standard()
        val decoded = FreeControlLayout.decode("MOVE_LEFT:0.2,0.5", fallback)

        assertEquals(fallback, decoded)
        assertFalse(decoded.positions.isEmpty())
    }
}
