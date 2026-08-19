package com.manus.tetris.controls

import android.content.Context
import kotlin.math.max
import kotlin.math.min

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

/** 相对位置以控制区域内按键左上角的可用移动范围为基准，取值始终在 0–1。 */
data class RelativeControlPosition(val x: Float, val y: Float) {
    init {
        require(x in 0f..1f && y in 0f..1f) { "相对位置必须位于 0 到 1 之间" }
    }
}

data class PixelPoint(val x: Float, val y: Float)

data class PixelRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * 运行时几何参数。所有坐标均为像素，minimumGap 为两个按钮命中区之间保留的最小间距。
 */
data class ControlAreaGeometry(
    val width: Float,
    val height: Float,
    val buttonWidth: Float,
    val buttonHeight: Float,
    val minimumGap: Float
) {
    init {
        require(width >= buttonWidth && height >= buttonHeight) { "控制区域必须容纳一个完整按钮" }
    }

    private val movableWidth: Float get() = width - buttonWidth
    private val movableHeight: Float get() = height - buttonHeight

    fun toPixel(position: RelativeControlPosition): PixelPoint = PixelPoint(
        x = position.x * movableWidth,
        y = position.y * movableHeight
    )

    fun toRelative(point: PixelPoint): RelativeControlPosition = RelativeControlPosition(
        x = if (movableWidth == 0f) 0f else (point.x / movableWidth).coerceIn(0f, 1f),
        y = if (movableHeight == 0f) 0f else (point.y / movableHeight).coerceIn(0f, 1f)
    )

    fun clamp(point: PixelPoint): PixelPoint = PixelPoint(
        x = min(max(point.x, 0f), movableWidth),
        y = min(max(point.y, 0f), movableHeight)
    )

    fun rect(position: RelativeControlPosition): PixelRect {
        val point = toPixel(position)
        return PixelRect(point.x, point.y, buttonWidth, buttonHeight)
    }

    fun intersectsWithGap(first: PixelRect, second: PixelRect): Boolean =
        first.left < second.right + minimumGap &&
            first.right + minimumGap > second.left &&
            first.top < second.bottom + minimumGap &&
            first.bottom + minimumGap > second.top
}

/**
 * 五个动作可以在底部控制区域中自由摆放。位置以相对值持久化，因此不同屏幕宽度会按比例适配。
 * moveIfValid 会先夹紧边界，再检测与其他四个按钮的最小间距；无效拖拽返回 null，界面保持上一次有效位置。
 */
data class FreeControlLayout(val positions: Map<ControlAction, RelativeControlPosition>) {
    init {
        require(positions.keys.containsAll(ControlAction.entries)) { "每个操作都必须具有位置" }
    }

    fun positionOf(action: ControlAction): RelativeControlPosition = positions.getValue(action)

    fun isValidFor(geometry: ControlAreaGeometry): Boolean {
        val actions = ControlAction.entries
        return actions.indices.none { firstIndex ->
            val first = actions[firstIndex]
            actions.drop(firstIndex + 1).any { second ->
                geometry.intersectsWithGap(geometry.rect(positionOf(first)), geometry.rect(positionOf(second)))
            }
        }
    }

    fun moveIfValid(
        action: ControlAction,
        desiredTopLeft: PixelPoint,
        geometry: ControlAreaGeometry
    ): FreeControlLayout? {
        val candidate = geometry.toRelative(geometry.clamp(desiredTopLeft))
        val candidateRect = geometry.rect(candidate)
        val collides = ControlAction.entries
            .filter { it != action }
            .any { other -> geometry.intersectsWithGap(candidateRect, geometry.rect(positionOf(other))) }
        if (collides) return null
        return copy(positions = positions.toMutableMap().apply { this[action] = candidate })
    }

    fun encode(): String = ControlAction.entries.joinToString(";") { action ->
        val position = positionOf(action)
        "${action.name}:${position.x},${position.y}"
    }

    companion object {
        fun standard(): FreeControlLayout = FreeControlLayout(
            mapOf(
                ControlAction.MOVE_LEFT to RelativeControlPosition(0f, 0.74f),
                ControlAction.MOVE_RIGHT to RelativeControlPosition(0.30f, 0.74f),
                ControlAction.SOFT_DROP to RelativeControlPosition(0.15f, 1f),
                ControlAction.ROTATE to RelativeControlPosition(1f, 0.74f),
                ControlAction.HARD_DROP to RelativeControlPosition(1f, 1f)
            )
        )

        fun decode(value: String?, fallback: FreeControlLayout): FreeControlLayout {
            if (value.isNullOrBlank()) return fallback
            val parsed = value.split(';').mapNotNull { entry ->
                val actionAndPoint = entry.split(':', limit = 2)
                if (actionAndPoint.size != 2) return@mapNotNull null
                val action = ControlAction.entries.firstOrNull { it.name == actionAndPoint[0] } ?: return@mapNotNull null
                val point = actionAndPoint[1].split(',', limit = 2)
                if (point.size != 2) return@mapNotNull null
                val x = point[0].toFloatOrNull() ?: return@mapNotNull null
                val y = point[1].toFloatOrNull() ?: return@mapNotNull null
                if (x !in 0f..1f || y !in 0f..1f) return@mapNotNull null
                action to RelativeControlPosition(x, y)
            }.toMap()
            return runCatching { FreeControlLayout(parsed) }.getOrElse { fallback }
        }
    }
}

data class ControlSettings(
    val preset: HandlingPreset = HandlingPreset.COMFORT,
    val layout: FreeControlLayout = FreeControlLayout.standard()
)

class ControlSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "block_space_controls",
        Context.MODE_PRIVATE
    )

    fun load(): ControlSettings = ControlSettings(
        preset = HandlingPreset.fromName(preferences.getString(KEY_PRESET, null)),
        layout = FreeControlLayout.decode(
            preferences.getString(KEY_LAYOUT, null),
            FreeControlLayout.standard()
        )
    )

    fun save(settings: ControlSettings) {
        preferences.edit()
            .putString(KEY_PRESET, settings.preset.name)
            .putString(KEY_LAYOUT, settings.layout.encode())
            .apply()
    }

    private companion object {
        const val KEY_PRESET = "handling_preset"
        const val KEY_LAYOUT = "free_layout"
    }
}
