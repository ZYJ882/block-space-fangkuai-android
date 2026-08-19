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

enum class ControlAction(val label: String, val symbol: String) {
    MOVE_LEFT("左移", "←"),
    MOVE_RIGHT("右移", "→"),
    SOFT_DROP("软降", "↓"),
    ROTATE("旋转", "↻"),
    HARD_DROP("直降", "⇊");

    companion object {
        fun fromName(value: String?): ControlAction =
            entries.firstOrNull { it.name == value } ?: MOVE_LEFT
    }
}

/** 五个固定视觉槽位；每个槽位的尺寸、间距和命中区域由界面统一控制。 */
enum class ControlSlot(val label: String) {
    LEFT_TOP_LEFT("左上左"),
    LEFT_TOP_RIGHT("左上右"),
    LEFT_BOTTOM("左下"),
    RIGHT_TOP("右上"),
    RIGHT_BOTTOM("右下")
}

/**
 * 一个始终完整、无重叠的按键布局。
 * moveActionTo 以“交换槽位”而不是覆盖方式更新，因此五个动作永远各占一个唯一位置。
 */
data class CustomControlLayout(val bindings: Map<ControlAction, ControlSlot>) {
    init {
        require(bindings.keys.containsAll(ControlAction.entries)) { "每个操作都必须分配一个槽位" }
        require(bindings.values.toSet().size == ControlAction.entries.size) { "按键槽位不能重叠" }
    }

    fun slotOf(action: ControlAction): ControlSlot = bindings.getValue(action)

    fun actionAt(slot: ControlSlot): ControlAction =
        bindings.entries.first { it.value == slot }.key

    fun moveActionTo(action: ControlAction, target: ControlSlot): CustomControlLayout {
        val source = slotOf(action)
        if (source == target) return this
        val displaced = actionAt(target)
        return CustomControlLayout(
            bindings.toMutableMap().apply {
                this[action] = target
                this[displaced] = source
            }
        )
    }

    fun encode(): String = ControlAction.entries.joinToString(";") { action ->
        "${action.name}:${slotOf(action).name}"
    }

    companion object {
        fun standard(): CustomControlLayout = CustomControlLayout(
            mapOf(
                ControlAction.MOVE_LEFT to ControlSlot.LEFT_TOP_LEFT,
                ControlAction.MOVE_RIGHT to ControlSlot.LEFT_TOP_RIGHT,
                ControlAction.SOFT_DROP to ControlSlot.LEFT_BOTTOM,
                ControlAction.ROTATE to ControlSlot.RIGHT_TOP,
                ControlAction.HARD_DROP to ControlSlot.RIGHT_BOTTOM
            )
        )

        fun decode(value: String?, fallback: CustomControlLayout): CustomControlLayout {
            if (value.isNullOrBlank()) return fallback
            val parsed = value.split(';').mapNotNull { entry ->
                val pair = entry.split(':', limit = 2)
                if (pair.size != 2) return@mapNotNull null
                val action = ControlAction.entries.firstOrNull { it.name == pair[0] } ?: return@mapNotNull null
                val slot = ControlSlot.entries.firstOrNull { it.name == pair[1] } ?: return@mapNotNull null
                action to slot
            }.toMap()
            return runCatching { CustomControlLayout(parsed) }.getOrElse { fallback }
        }
    }
}

data class ControlSettings(
    val preset: HandlingPreset = HandlingPreset.COMFORT,
    val layout: CustomControlLayout = CustomControlLayout.standard()
)

class ControlSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "block_space_controls",
        Context.MODE_PRIVATE
    )

    fun load(): ControlSettings {
        val fallback = CustomControlLayout.standard()
        return ControlSettings(
            preset = HandlingPreset.fromName(preferences.getString(KEY_PRESET, null)),
            layout = CustomControlLayout.decode(preferences.getString(KEY_LAYOUT, null), fallback)
        )
    }

    fun save(settings: ControlSettings) {
        preferences.edit()
            .putString(KEY_PRESET, settings.preset.name)
            .putString(KEY_LAYOUT, settings.layout.encode())
            .apply()
    }

    private companion object {
        const val KEY_PRESET = "handling_preset"
        const val KEY_LAYOUT = "custom_layout"
    }
}
