package com.manus.tetris.controls

import android.content.Context

enum class HandlingPreset(
    val label: String,
    val initialDelayMillis: Long,
    val repeatIntervalMillis: Long
) {
    COMFORT("舒适", 160L, 50L),
    FAST("快速", 130L, 40L),
    COMPETITIVE("竞技", 120L, 33L);

    companion object {
        fun fromName(value: String?): HandlingPreset =
            entries.firstOrNull { it.name == value } ?: COMFORT
    }
}

data class ControlSettings(
    val preset: HandlingPreset = HandlingPreset.COMFORT,
    val mirrored: Boolean = false
)

class ControlSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "block_space_controls",
        Context.MODE_PRIVATE
    )

    fun load(): ControlSettings = ControlSettings(
        preset = HandlingPreset.fromName(preferences.getString(KEY_PRESET, null)),
        mirrored = preferences.getBoolean(KEY_MIRRORED, false)
    )

    fun save(settings: ControlSettings) {
        preferences.edit()
            .putString(KEY_PRESET, settings.preset.name)
            .putBoolean(KEY_MIRRORED, settings.mirrored)
            .apply()
    }

    private companion object {
        const val KEY_PRESET = "handling_preset"
        const val KEY_MIRRORED = "mirrored_layout"
    }
}
