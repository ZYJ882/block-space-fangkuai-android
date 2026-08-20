package com.blockspace.tetris.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SevenBagGeneratorTest {
    @Test
    fun everyCompleteBagContainsEachTetrominoExactlyOnce() {
        val generator = SevenBagGenerator(Random(101))

        repeat(5) {
            val bag = List(TetrominoType.entries.size) { generator.next() }
            assertEquals(TetrominoType.entries.toSet(), bag.toSet())
            assertEquals(TetrominoType.entries.size, bag.distinct().size)
        }
    }

    @Test
    fun fixedSeedProducesTheSameBagSequence() {
        val first = SevenBagGenerator(Random(2026))
        val second = SevenBagGenerator(Random(2026))

        val firstSequence = List(28) { first.next() }
        val secondSequence = List(28) { second.next() }

        assertEquals(firstSequence, secondSequence)
    }

    @Test
    fun previewQueueMatchesTheNextSpawnedPieces() {
        val game = TetrisGame(Random(303))
        val previewBeforeLock = game.upcomingTypes

        assertEquals(3, previewBeforeLock.size)
        assertTrue(game.hardDrop())
        assertEquals(previewBeforeLock.first(), game.activePiece!!.type)
        assertEquals(previewBeforeLock.drop(1), game.upcomingTypes.take(2))
    }
}
