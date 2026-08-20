package com.blockspace.tetris.controls

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
    COMPETITIVE("竞技", 120L, 33L),
    CUSTOM("自定义", 160L, 50L);

    companion object {
        fun fromName(value: String?): HandlingPreset =
            entries.firstOrNull { it.name == value } ?: COMFORT
    }
}

/**
 * 高级长按参数。范围限定为 DAS 100–220ms、ARR 25–80ms，
 * 以防止触屏连续移动过快导致难以停止，或过慢失去操作意义。
 */
data class HandlingSettings(
    val dasMillis: Long,
    val arrMillis: Long
) {
    init {
        require(dasMillis in DAS_RANGE) { "DAS 必须在 ${DAS_RANGE.first}–${DAS_RANGE.last}ms 之间" }
        require(arrMillis in ARR_RANGE) { "ARR 必须在 ${ARR_RANGE.first}–${ARR_RANGE.last}ms 之间" }
    }

    companion object {
        val DAS_RANGE: LongRange = 100L..220L
        val ARR_RANGE: LongRange = 25L..80L

        fun fromPreset(preset: HandlingPreset): HandlingSettings = HandlingSettings(
            dasMillis = preset.initialDelayMillis,
            arrMillis = preset.repeatIntervalMillis
        )

        fun clamp(dasMillis: Long, arrMillis: Long): HandlingSettings = HandlingSettings(
            dasMillis = dasMillis.coerceIn(DAS_RANGE),
            arrMillis = arrMillis.coerceIn(ARR_RANGE)
        )
    }
}

/**
 * 自动重力的时间倍率与挑战分倍率。下落动作分保持固定，只有清行等挑战结算随此倍率变化。
 */
enum class FallSpeedPreset(
    val label: String,
    val gravityMultiplier: Double,
    val challengeScoreMultiplier: Double
) {
    RELAXED("悠闲", 0.75, 0.75),
    STANDARD("标准", 1.00, 1.00),
    FAST("快速", 1.25, 1.25),
    TURBO("极速", 1.50, 1.50);

    companion object {
        fun fromName(value: String?): FallSpeedPreset =
            entries.firstOrNull { it.name == value } ?: STANDARD
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
                ControlAction.MOVE_LEFT to RelativeControlPosition(0f, 0f),
                ControlAction.MOVE_RIGHT to RelativeControlPosition(0.30f, 0f),
                ControlAction.SOFT_DROP to RelativeControlPosition(0.15f, 1f),
                ControlAction.ROTATE to RelativeControlPosition(1f, 0f),
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
    val handling: HandlingSettings = HandlingSettings.fromPreset(HandlingPreset.COMFORT),
    val fallSpeed: FallSpeedPreset = FallSpeedPreset.STANDARD,
    val layout: FreeControlLayout = FreeControlLayout.standard()
) {
    fun applyPreset(preset: HandlingPreset): ControlSettings = copy(
        preset = preset,
        handling = HandlingSettings.fromPreset(preset)
    )

    fun applyAdvancedHandling(dasMillis: Long, arrMillis: Long): ControlSettings = copy(
        preset = HandlingPreset.CUSTOM,
        handling = HandlingSettings.clamp(dasMillis, arrMillis)
    )

    fun applyFallSpeed(fallSpeed: FallSpeedPreset): ControlSettings = copy(fallSpeed = fallSpeed)
}

class ControlSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "block_space_controls",
        Context.MODE_PRIVATE
    )

    fun load(): ControlSettings {
        val preset = HandlingPreset.fromName(preferences.getString(KEY_PRESET, null))
        val defaultHandling = HandlingSettings.fromPreset(preset)
        return ControlSettings(
            preset = preset,
            fallSpeed = FallSpeedPreset.fromName(preferences.getString(KEY_FALL_SPEED, null)),
            handling = HandlingSettings.clamp(
                preferences.getLong(KEY_DAS, defaultHandling.dasMillis),
                preferences.getLong(KEY_ARR, defaultHandling.arrMillis)
            ),
            layout = FreeControlLayout.decode(
                preferences.getString(KEY_LAYOUT, null),
                FreeControlLayout.standard()
            )
        )
    }

    fun save(settings: ControlSettings) {
        preferences.edit()
            .putString(KEY_PRESET, settings.preset.name)
            .putLong(KEY_DAS, settings.handling.dasMillis)
            .putLong(KEY_ARR, settings.handling.arrMillis)
            .putString(KEY_FALL_SPEED, settings.fallSpeed.name)
            .putString(KEY_LAYOUT, settings.layout.encode())
            .apply()
    }

    private companion object {
        const val KEY_PRESET = "handling_preset"
        const val KEY_DAS = "handling_das_ms"
        const val KEY_ARR = "handling_arr_ms"
        const val KEY_FALL_SPEED = "fall_speed_preset"
        const val KEY_LAYOUT = "free_layout"
    }
}
