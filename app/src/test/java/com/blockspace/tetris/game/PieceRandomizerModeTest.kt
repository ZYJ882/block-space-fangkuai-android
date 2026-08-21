package com.blockspace.tetris.game

import com.blockspace.tetris.controls.PieceRandomizerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PieceRandomizerModeTest {
    /**
     * 固定返回 0 的随机源使真随机每次都抽到 I，稳定地证明真随机不具备 7-Bag 的去重保证。
     */
    private class ZeroRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }

    @Test
    fun trueRandomAllowsRepeatedTypesWithinTheFirstSevenPieces() {
        val game = TetrisGame(ZeroRandom())
        game.setPieceRandomizerMode(PieceRandomizerMode.TRUE_RANDOM)
        game.startNewGame()

        val sequence = mutableListOf(game.activePiece!!.type)
        repeat(6) {
            game.advanceTime(InputSafetyRules.HARD_DROP_SPAWN_GUARD_MILLIS)
            assertTrue(game.hardDrop())
            sequence += game.activePiece!!.type
        }

        assertEquals(setOf(TetrominoType.I), sequence.toSet())
        assertTrue(sequence.distinct().size < TetrominoType.entries.size)
    }

    @Test
    fun changingRandomizerModeDoesNotRewriteTheCurrentGame() {
        val game = TetrisGame(ZeroRandom())
        val activeBeforeChange = game.activePiece!!.type
        val previewBeforeChange = game.upcomingTypes

        game.setPieceRandomizerMode(PieceRandomizerMode.TRUE_RANDOM)

        assertEquals(PieceRandomizerMode.TRUE_RANDOM, game.pieceRandomizerMode)
        assertEquals(PieceRandomizerMode.SEVEN_BAG, game.activeGameRandomizerMode)
        assertEquals(activeBeforeChange, game.activePiece!!.type)
        assertEquals(previewBeforeChange, game.upcomingTypes)

        game.startNewGame()

        assertEquals(PieceRandomizerMode.TRUE_RANDOM, game.activeGameRandomizerMode)
        assertEquals(TetrominoType.I, game.activePiece!!.type)
        assertEquals(List(3) { TetrominoType.I }, game.upcomingTypes)
    }
}
