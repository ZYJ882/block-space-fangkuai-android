package com.blockspace.tetris.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.blockspace.tetris.R
import java.util.concurrent.ConcurrentHashMap

/** 游戏中可独立播放的短音效。 */
enum class GameSoundEffect {
    MOVE,
    ROTATE,
    SOFT_DROP,
    HARD_DROP,
    LOCK,
    LINE_CLEAR,
    SPECIAL_CLEAR,
    PAUSE,
    START,
    GAME_OVER
}

/**
 * 以 SoundPool 预载短音效，避免把播放延迟放到输入路径中。
 * MOVE 与 SOFT_DROP 会在长按场景下节流，以保证声音清晰且不占满并发音轨。
 */
class GameSoundPlayer(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val loadedSoundIds = ConcurrentHashMap.newKeySet<Int>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedSoundIds += sampleId
        }
    }

    private val soundIds = mapOf(
        GameSoundEffect.MOVE to soundPool.load(context, R.raw.sfx_move, LOAD_PRIORITY),
        GameSoundEffect.ROTATE to soundPool.load(context, R.raw.sfx_rotate, LOAD_PRIORITY),
        GameSoundEffect.SOFT_DROP to soundPool.load(context, R.raw.sfx_soft_drop, LOAD_PRIORITY),
        GameSoundEffect.HARD_DROP to soundPool.load(context, R.raw.sfx_hard_drop, LOAD_PRIORITY),
        GameSoundEffect.LOCK to soundPool.load(context, R.raw.sfx_lock, LOAD_PRIORITY),
        GameSoundEffect.LINE_CLEAR to soundPool.load(context, R.raw.sfx_line_clear, LOAD_PRIORITY),
        GameSoundEffect.SPECIAL_CLEAR to soundPool.load(context, R.raw.sfx_special_clear, LOAD_PRIORITY),
        GameSoundEffect.PAUSE to soundPool.load(context, R.raw.sfx_pause, LOAD_PRIORITY),
        GameSoundEffect.START to soundPool.load(context, R.raw.sfx_start, LOAD_PRIORITY),
        GameSoundEffect.GAME_OVER to soundPool.load(context, R.raw.sfx_game_over, LOAD_PRIORITY)
    )
    private val lastPlayNanos = mutableMapOf<GameSoundEffect, Long>()

    var isEnabled: Boolean = true
        private set

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun play(effect: GameSoundEffect) {
        if (!isEnabled) return

        val now = System.nanoTime()
        val minimumInterval = effect.minimumIntervalNanos
        val previous = lastPlayNanos[effect]
        if (previous != null && now - previous < minimumInterval) return

        val soundId = soundIds.getValue(effect)
        if (soundId !in loadedSoundIds) return

        soundPool.play(
            soundId,
            VOLUME,
            VOLUME,
            PLAY_PRIORITY,
            NO_LOOP,
            NORMAL_RATE
        )
        lastPlayNanos[effect] = now
    }

    fun release() {
        soundPool.release()
        lastPlayNanos.clear()
        loadedSoundIds.clear()
    }

    private val GameSoundEffect.minimumIntervalNanos: Long
        get() = when (this) {
            GameSoundEffect.MOVE -> 36_000_000L
            GameSoundEffect.SOFT_DROP -> 44_000_000L
            else -> 0L
        }

    private companion object {
        const val MAX_STREAMS = 4
        const val LOAD_PRIORITY = 1
        const val PLAY_PRIORITY = 1
        const val NO_LOOP = 0
        const val NORMAL_RATE = 1f
        const val VOLUME = 0.72f
    }
}
