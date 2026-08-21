package com.blockspace.tetris.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LanGarbageRuleTest {
    @Test
    fun incomingGarbageAddsRowsWithExactlyOneHolePerRow() {
        val game = TetrisGame(Random(101))

        assertTrue(game.applyGarbage(2))
        val board = game.board()
        val garbageRows = board.takeLast(2)

        garbageRows.forEach { row ->
            assertEquals(TetrisGame.COLUMNS - 1, row.count { it == TetrisGame.GARBAGE_CELL })
            assertEquals(1, row.count { it == 0 })
        }
        assertFalse(game.isGameOver)
    }

    @Test
    fun zeroGarbageDoesNotMutateFreshBoard() {
        val game = TetrisGame(Random(103))
        val before = game.board()

        assertFalse(game.applyGarbage(0))
        assertTrue(before.indices.all { row -> before[row].contentEquals(game.board()[row]) })
    }

    @Test
    fun garbageLineCountIsBoundedToBoardHeight() {
        val game = TetrisGame(Random(107))

        assertTrue(game.applyGarbage(TetrisGame.ROWS + 8))
        val board = game.board()
        assertEquals(TetrisGame.ROWS * (TetrisGame.COLUMNS - 1), board.sumOf { row -> row.count { it == TetrisGame.GARBAGE_CELL } })
    }
}
