package com.blockspace.tetris.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.blockspace.tetris.controls.FallSpeedPreset
import kotlin.random.Random

class ModernGravityTest {
    @Test
    fun marathonCurveMatchesExpectedEarlyAndMidLevelPacing() {
        assertEquals(1, ModernGravity.levelForLines(0))
        assertEquals(5, ModernGravity.levelForLines(40))
        assertEquals(15, ModernGravity.levelForLines(140))
        assertEquals(15, ModernGravity.levelForLines(999))

        assertEquals(1_000L, ModernGravity.nominalCellIntervalMillis(1))
        assertTrue(ModernGravity.nominalCellIntervalMillis(5) in 350L..360L)
        assertTrue(ModernGravity.nominalCellIntervalMillis(10) in 60L..70L)
    }

    @Test
    fun highGravityStageSupportsFasterThanOneCellPerFrame() {
        assertTrue(ModernGravity.gravityG(13) < 1.0)
        assertTrue(ModernGravity.gravityG(14) > 1.0)
        assertTrue(ModernGravity.nominalCellIntervalMillis(14) <= 12L)
        assertTrue(ModernGravity.cellsPerSecond(15) > 100.0)
    }

    @Test
    fun automaticGravityMovesWithoutAwardingDropPoints() {
        val game = TetrisGame(Random(7))
        val startRow = game.activePiece!!.row

        assertTrue(game.advanceTime(1_000L))

        assertEquals(startRow + 1, game.activePiece!!.row)
        assertEquals(0, game.score)
    }

    @Test
    fun groundedSoftDropReceivesFullLockDelayBeforeLocking() {
        val game = TetrisGame(Random(11))
        while (game.softDrop()) Unit
        val groundedPiece = game.activePiece!!

        assertFalse(game.advanceTime(499L))
        assertEquals(groundedPiece, game.activePiece)

        assertTrue(game.advanceTime(1L))
        assertEquals(0, game.activePiece!!.row)
    }

    @Test
    fun idleGravityFrameDoesNotRequestRedraw() {
        val game = TetrisGame(Random(17))

        assertFalse(game.advanceTime(16L))
        assertEquals(0, game.activePiece!!.row)
    }

    @Test
    fun nextRuleEventUsesNearestGravityGuardAndLockDeadline() {
        val game = TetrisGame(Random(31))

        assertEquals(1_000L, game.nextRuleEventDelayMillis())
        assertFalse(game.advanceTime(250L))
        assertEquals(750L, game.nextRuleEventDelayMillis())

        assertTrue(game.hardDrop())
        assertEquals(InputSafetyRules.HARD_DROP_SPAWN_GUARD_MILLIS, game.nextRuleEventDelayMillis())
        assertFalse(game.advanceTime(InputSafetyRules.HARD_DROP_SPAWN_GUARD_MILLIS))
        assertEquals(880L, game.nextRuleEventDelayMillis())

        while (game.softDrop()) Unit
        assertEquals(ModernGravity.LOCK_DELAY_MILLIS.toLong(), game.nextRuleEventDelayMillis())
        assertFalse(game.advanceTime(499L))
        assertEquals(1L, game.nextRuleEventDelayMillis())
    }

    @Test
    fun pausedGameHasNoPendingRuleWakeup() {
        val game = TetrisGame(Random(37))

        game.togglePause()

        assertEquals(TetrisGame.NO_PENDING_EVENT_MILLIS, game.nextRuleEventDelayMillis())
    }

    @Test
    fun spawnedPieceHardDropGuardPreventsResidualTapButKeepsOtherControlsAvailable() {
        val game = TetrisGame(Random(19))
        game.hardDrop()
        val spawnedPiece = game.activePiece!!
        val blocksAfterFirstDrop = game.board().sumOf { row -> row.count { it != 0 } }

        game.hardDrop()
        assertEquals(spawnedPiece, game.activePiece)
        assertEquals(4, blocksAfterFirstDrop)
        assertEquals(InputSafetyRules.HARD_DROP_SPAWN_GUARD_MILLIS, game.hardDropGuardRemainingMillis)
        assertTrue(game.moveLeft())

        game.advanceTime(InputSafetyRules.HARD_DROP_SPAWN_GUARD_MILLIS)
        assertEquals(0L, game.hardDropGuardRemainingMillis)
        game.hardDrop()

        val blocksAfterSecondDrop = game.board().sumOf { row -> row.count { it != 0 } }
        assertEquals(8, blocksAfterSecondDrop)
    }

    @Test
    fun lockingEmitsVisualEventDataWithoutChangingBoardRules() {
        val game = TetrisGame(Random(29))

        assertTrue(game.hardDrop())

        assertEquals(1, game.lockRevision)
        assertEquals(0, game.clearRevision)
        assertEquals(0, game.lastClearedLines)
        assertTrue(game.lastClearedRows.isEmpty())
    }

    @Test
    fun speedPresetChangesGravityAndChallengeScoresButNotDropPoints() {
        val game = TetrisGame(Random(23))
        val baseGravity = game.gravityCellsPerSecond
        val dropDistance = generateSequence(game.activePiece) { piece ->
            val next = piece.moved(rowDelta = 1)
            if (next.blocks().all { it.row in 0 until TetrisGame.ROWS }) next else null
        }.count() - 1

        game.setFallSpeed(FallSpeedPreset.TURBO)
        assertEquals(baseGravity * 1.5, game.gravityCellsPerSecond, 0.0001)
        assertEquals(1_200, ModernScoring.applyChallengeMultiplier(800, game.challengeScoreMultiplier))
        assertEquals(2, ModernScoring.applyChallengeMultiplier(2, 1.0))

        game.hardDrop()
        assertEquals(dropDistance * 2, game.score)
    }

    @Test
    fun lockDelayAndMoveResetLimitsFollowModernRules() {
        assertFalse(ModernGravity.shouldLock(499.0))
        assertTrue(ModernGravity.shouldLock(500.0))
        assertTrue(ModernGravity.canResetLock(14))
        assertFalse(ModernGravity.canResetLock(15))
    }
}
