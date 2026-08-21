package com.blockspace.tetris.controls

import org.junit.Assert.assertEquals
import org.junit.Test

class HandlingSettingsTest {
    @Test
    fun advancedHandlingIsClampedToTheSupportedSafeRange() {
        val settings = ControlSettings().applyAdvancedHandling(
            dasMillis = 1L,
            arrMillis = 500L
        )

        assertEquals(HandlingPreset.CUSTOM, settings.preset)
        assertEquals(100L, settings.handling.dasMillis)
        assertEquals(80L, settings.handling.arrMillis)
    }

    @Test
    fun fallSpeedIsIndependentFromHandlingAndHasTransparentMultipliers() {
        val settings = ControlSettings()
            .applyAdvancedHandling(dasMillis = 145L, arrMillis = 35L)
            .applyFallSpeed(FallSpeedPreset.TURBO)

        assertEquals(HandlingPreset.CUSTOM, settings.preset)
        assertEquals(145L, settings.handling.dasMillis)
        assertEquals(35L, settings.handling.arrMillis)
        assertEquals(FallSpeedPreset.TURBO, settings.fallSpeed)
        assertEquals(1.5, settings.fallSpeed.gravityMultiplier, 0.0)
        assertEquals(1.5, settings.fallSpeed.challengeScoreMultiplier, 0.0)
    }

    @Test
    fun soundEffectsDefaultToEnabledAndCanBeChangedIndependently() {
        val settings = ControlSettings()
            .applyAdvancedHandling(dasMillis = 145L, arrMillis = 35L)
            .applyPieceRandomizer(PieceRandomizerMode.TRUE_RANDOM)
            .applySoundEffectsEnabled(false)

        assertEquals(false, settings.soundEffectsEnabled)
        assertEquals(HandlingPreset.CUSTOM, settings.preset)
        assertEquals(145L, settings.handling.dasMillis)
        assertEquals(35L, settings.handling.arrMillis)
        assertEquals(PieceRandomizerMode.TRUE_RANDOM, settings.pieceRandomizer)
    }

    @Test
    fun choosingPresetReplacesCustomHandlingValues() {
        val settings = ControlSettings()
            .applyAdvancedHandling(dasMillis = 210L, arrMillis = 30L)
            .applyPreset(HandlingPreset.COMPETITIVE)

        assertEquals(HandlingPreset.COMPETITIVE, settings.preset)
        assertEquals(120L, settings.handling.dasMillis)
        assertEquals(33L, settings.handling.arrMillis)
    }
}
