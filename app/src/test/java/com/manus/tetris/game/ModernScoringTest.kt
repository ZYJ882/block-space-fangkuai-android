package com.manus.tetris.game

import org.junit.Assert.assertEquals
import org.junit.Test

class ModernScoringTest {
    @Test
    fun comboPointsIncreaseThenCapAtEightSteps() {
        assertEquals(0, ModernScoring.comboPoints(comboIndex = 0, level = 4))
        assertEquals(600, ModernScoring.comboPoints(comboIndex = 3, level = 4))
        assertEquals(1600, ModernScoring.comboPoints(comboIndex = 8, level = 4))
        assertEquals(1600, ModernScoring.comboPoints(comboIndex = 15, level = 4))
    }

    @Test
    fun perfectClearBonusUsesDedicatedB2BTetrisValue() {
        assertEquals(800, ModernScoring.perfectClearBonus(cleared = 1, b2bApplied = false))
        assertEquals(1800, ModernScoring.perfectClearBonus(cleared = 3, b2bApplied = false))
        assertEquals(2000, ModernScoring.perfectClearBonus(cleared = 4, b2bApplied = false))
        assertEquals(3200, ModernScoring.perfectClearBonus(cleared = 4, b2bApplied = true))
    }
}
