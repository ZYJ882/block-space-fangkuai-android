package com.blockspace.tetris.game

import com.blockspace.tetris.controls.PieceRandomizerMode
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class LanSeededGameTest {
    @Test
    fun equalLanSeedProducesEqualSevenBagPreviewSequence() {
        val first = TetrisGame(Random(1))
        val second = TetrisGame(Random(2))

        first.setPieceRandomizerMode(PieceRandomizerMode.SEVEN_BAG)
        second.setPieceRandomizerMode(PieceRandomizerMode.SEVEN_BAG)
        first.startNewGame(seed = 7_290_341)
        second.startNewGame(seed = 7_290_341)

        repeat(14) {
            assertEquals(first.activePiece?.type, second.activePiece?.type)
            assertEquals(first.upcomingTypes, second.upcomingTypes)
            first.advanceTime(InputSafetyRules.HARD_DROP_SPAWN_GUARD_MILLIS)
            second.advanceTime(InputSafetyRules.HARD_DROP_SPAWN_GUARD_MILLIS)
            first.hardDrop()
            second.hardDrop()
        }
    }

    @Test
    fun seededStartStillHonorsSevenBagWhenPreviousSettingWasTrueRandom() {
        val game = TetrisGame(Random(3))
        game.setPieceRandomizerMode(PieceRandomizerMode.TRUE_RANDOM)
        game.setPieceRandomizerMode(PieceRandomizerMode.SEVEN_BAG)

        game.startNewGame(seed = 42)

        val visible = buildList {
            add(game.activePiece!!.type)
            addAll(game.upcomingTypes)
        }
        assertEquals(4, visible.distinct().size)
    }
}
