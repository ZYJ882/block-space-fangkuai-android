package com.blockspace.tetris.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RestartAfterGameOverTest {
    @Test
    fun startNewGameRestoresPlayableStateAfterGarbageGameOver() {
        val game = TetrisGame(Random(211))

        assertTrue(game.applyGarbage(TetrisGame.ROWS))
        assertTrue(game.isGameOver)

        game.startNewGame()

        assertFalse(game.isGameOver)
        assertFalse(game.isPaused)
        assertEquals(0, game.score)
        assertEquals(0, game.lines)
        assertTrue(game.board().all { row -> row.all { it == 0 } })
        assertNotNull(game.activePiece)
    }
}
