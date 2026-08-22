package com.blockspace.tetris.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceRulesTest {
    @Test
    fun fortyLinesIsTheOnlyImmediateRaceCompletionTarget() {
        assertTrue(!RaceRules.hasFinished(39))
        assertTrue(RaceRules.hasFinished(40))
        assertTrue(RaceRules.hasFinished(45))
    }

    @Test
    fun timeoutPrioritizesMoreLinesBeforeAnyOtherMetric() {
        val winner = RaceRules.timeoutWinner(
            mapOf(
                "slow-but-safe" to RaceRules.Progress(lines = 18, stackHeight = 2, score = 50_000),
                "faster" to RaceRules.Progress(lines = 19, stackHeight = 19, score = 1)
            )
        )
        assertEquals("faster", winner)
    }

    @Test
    fun timeoutUsesLowerStackHeightWhenLinesAreEqual() {
        val winner = RaceRules.timeoutWinner(
            mapOf(
                "safer" to RaceRules.Progress(lines = 22, stackHeight = 6, score = 1),
                "higher" to RaceRules.Progress(lines = 22, stackHeight = 7, score = 99_999)
            )
        )
        assertEquals("safer", winner)
    }

    @Test
    fun timeoutUsesScoreOnlyAfterLinesAndHeightTie() {
        val winner = RaceRules.timeoutWinner(
            mapOf(
                "higher-score" to RaceRules.Progress(lines = 22, stackHeight = 6, score = 10_001),
                "lower-score" to RaceRules.Progress(lines = 22, stackHeight = 6, score = 10_000)
            )
        )
        assertEquals("higher-score", winner)
    }

    @Test
    fun exactTimeoutTieRequestsReplayInsteadOfArbitraryWinner() {
        assertNull(
            RaceRules.timeoutWinner(
                mapOf(
                    "a" to RaceRules.Progress(lines = 22, stackHeight = 6, score = 10_000),
                    "b" to RaceRules.Progress(lines = 22, stackHeight = 6, score = 10_000)
                )
            )
        )
    }

    @Test
    fun stackHeightCountsFromHighestOccupiedRow() {
        val board = Array(20) { IntArray(10) }
        assertEquals(0, RaceRules.stackHeight(board))
        board[16][0] = 1
        assertEquals(4, RaceRules.stackHeight(board))
        board[2][4] = 1
        assertEquals(18, RaceRules.stackHeight(board))
    }
}
