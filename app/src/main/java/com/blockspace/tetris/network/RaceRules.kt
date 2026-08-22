package com.blockspace.tetris.network

/**
 * 无垃圾 Race 的明确完成目标与超时裁决。
 *
 * Race 不是“最后存活者”模式：任何玩家先完成 [TARGET_LINES] 行立即赢下本局。
 * 超时后才按消行数、较低堆高、分数的顺序比较；若三项均相同，则本局平局重赛，
 * 不以网络到达顺序或玩家 ID 随机决定胜者。
 */
object RaceRules {
    const val TARGET_LINES = 40
    const val TIME_LIMIT_MILLIS = 150_000L

    data class Progress(
        val lines: Int,
        val stackHeight: Int,
        val score: Int
    )

    fun hasFinished(lines: Int): Boolean = lines >= TARGET_LINES

    fun stackHeight(board: Array<IntArray>): Int {
        val firstOccupiedRow = board.indexOfFirst { row -> row.any { it != 0 } }
        return if (firstOccupiedRow < 0) 0 else board.size - firstOccupiedRow
    }

    /**
     * 返回超时的唯一领先玩家；返回 null 表示完全并列，应重赛且本局不计入 FT 胜场。
     */
    fun timeoutWinner(progressByPlayer: Map<String, Progress>): String? {
        if (progressByPlayer.isEmpty()) return null
        val ordered = progressByPlayer.entries.sortedWith(
            compareByDescending<Map.Entry<String, Progress>> { it.value.lines }
                .thenBy { it.value.stackHeight }
                .thenByDescending { it.value.score }
                .thenBy { it.key }
        )
        val leader = ordered.first()
        val runnerUp = ordered.getOrNull(1)
        return if (runnerUp == null || compare(leader.value, runnerUp.value) != 0) leader.key else null
    }

    /** 负数表示 first 更优，零表示完全相同，正数表示 second 更优。 */
    fun compare(first: Progress, second: Progress): Int = when {
        first.lines != second.lines -> second.lines.compareTo(first.lines)
        first.stackHeight != second.stackHeight -> first.stackHeight.compareTo(second.stackHeight)
        first.score != second.score -> second.score.compareTo(first.score)
        else -> 0
    }
}
