package com.blockspace.tetris.game

/**
 * 轻量、透明的现代多人攻击表。
 *
 * 此规则只服务于 LAN「标准攻击」模式；单人挑战分仍由 [ModernScoring] 独立结算。
 * 攻击在发送前可抵消尚未进入棋盘的来袭垃圾，单次外发最多 12 行。
 */
object StandardAttackRules {
    const val MAX_OUTGOING_LINES = 12
    const val GARBAGE_DELAY_MILLIS = 220L

    fun linesFor(event: ScoreEvent?): Int {
        if (event == null || event.clearedLines <= 0) return 0

        val base = when {
            event.isTSpin -> when (event.clearedLines) {
                1 -> 2
                2 -> 4
                else -> 6
            }
            else -> when (event.clearedLines) {
                1 -> 0
                2 -> 1
                3 -> 2
                else -> 4
            }
        }
        val backToBack = if (event.backToBackApplied) 1 else 0
        val combo = comboBonus(event.combo)
        val perfectClear = if (event.perfectClear) 6 else 0
        return (base + backToBack + combo + perfectClear).coerceIn(0, MAX_OUTGOING_LINES)
    }

    /** 游戏引擎的首个连续消行组合值为 0；从第二次连续消行开始产生攻击加成。 */
    fun comboBonus(combo: Int): Int = when {
        combo < 1 -> 0
        combo < 3 -> 1
        combo < 5 -> 2
        combo < 7 -> 3
        else -> 4
    }
}
