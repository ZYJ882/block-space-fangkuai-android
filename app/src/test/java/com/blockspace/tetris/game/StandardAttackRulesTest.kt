package com.blockspace.tetris.game

import org.junit.Assert.assertEquals
import org.junit.Test

class StandardAttackRulesTest {
    @Test
    fun basicLineClearTableMatchesDeclaredRules() {
        assertEquals(0, StandardAttackRules.linesFor(event(cleared = 1)))
        assertEquals(1, StandardAttackRules.linesFor(event(cleared = 2)))
        assertEquals(2, StandardAttackRules.linesFor(event(cleared = 3)))
        assertEquals(4, StandardAttackRules.linesFor(event(cleared = 4)))
    }

    @Test
    fun tSpinAndBackToBackAddExpectedAttack() {
        assertEquals(2, StandardAttackRules.linesFor(event(cleared = 1, tSpin = true)))
        assertEquals(4, StandardAttackRules.linesFor(event(cleared = 2, tSpin = true)))
        assertEquals(5, StandardAttackRules.linesFor(event(cleared = 4, b2b = true)))
    }

    @Test
    fun comboStartsOnSecondConsecutiveClearAndIsCapped() {
        assertEquals(0, StandardAttackRules.comboBonus(0))
        assertEquals(1, StandardAttackRules.comboBonus(1))
        assertEquals(2, StandardAttackRules.comboBonus(3))
        assertEquals(4, StandardAttackRules.comboBonus(99))
    }

    @Test
    fun allClearAndLargeModifiersAreCappedForNetworkSafety() {
        assertEquals(10, StandardAttackRules.linesFor(event(cleared = 4, perfectClear = true)))
        assertEquals(
            StandardAttackRules.MAX_OUTGOING_LINES,
            StandardAttackRules.linesFor(event(cleared = 3, tSpin = true, b2b = true, combo = 99, perfectClear = true))
        )
    }

    @Test
    fun nonClearingEventsNeverAttack() {
        assertEquals(0, StandardAttackRules.linesFor(null))
        assertEquals(0, StandardAttackRules.linesFor(event(cleared = 0, tSpin = true)))
    }

    private fun event(
        cleared: Int,
        tSpin: Boolean = false,
        b2b: Boolean = false,
        combo: Int = 0,
        perfectClear: Boolean = false
    ) = ScoreEvent(
        title = "test",
        points = 0,
        combo = combo,
        backToBackApplied = b2b,
        perfectClear = perfectClear,
        clearedLines = cleared,
        isTSpin = tSpin
    )
}
