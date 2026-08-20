package com.manus.tetris.game

import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

enum class TetrominoType(val color: Long) {
    I(0xFF32D7FF),
    O(0xFFFFD84D),
    T(0xFFC77DFF),
    S(0xFF55E58A),
    Z(0xFFFF5B75),
    J(0xFF4C7DFF),
    L(0xFFFFA84A)
}

data class Block(val row: Int, val column: Int)

data class FallingPiece(
    val type: TetrominoType,
    val rotation: Int,
    val row: Int,
    val column: Int
) {
    fun blocks(): List<Block> = PieceLibrary.blocks(type, rotation).map { (x, y) ->
        Block(row + y, column + x)
    }

    fun moved(rowDelta: Int = 0, columnDelta: Int = 0): FallingPiece =
        copy(row = row + rowDelta, column = column + columnDelta)

    fun rotated(): FallingPiece = copy(rotation = (rotation + 1) % 4)
}

/** 最近一次可见得分事件，用于界面向玩家解释分数来源。 */
data class ScoreEvent(
    val title: String,
    val points: Int,
    val combo: Int,
    val backToBackApplied: Boolean,
    val perfectClear: Boolean
)

private enum class ClearAction(
    val title: String,
    val basePoints: Int,
    val isDifficult: Boolean
) {
    SINGLE("单消", 100, false),
    DOUBLE("双消", 300, false),
    TRIPLE("三消", 500, false),
    TETRIS("四消", 800, true),
    T_SPIN_ZERO("T-SPIN", 400, false),
    T_SPIN_SINGLE("T-SPIN 单消", 800, true),
    T_SPIN_DOUBLE("T-SPIN 双消", 1200, true),
    T_SPIN_TRIPLE("T-SPIN 三消", 1600, true)
}

/** 可独立验证的现代竞赛计分公式。 */
object ModernScoring {
    const val COMBO_CAP = 8

    fun comboPoints(comboIndex: Int, level: Int): Int =
        min(50 * comboIndex * level, 50 * COMBO_CAP * level)

    fun perfectClearBonus(cleared: Int, b2bApplied: Boolean): Int = when (cleared) {
        1 -> 800
        2 -> 1200
        3 -> 1800
        else -> if (b2bApplied) 3200 else 2000
    }
}

/**
 * 现代 Marathon（Tetris Worlds / Guideline）重力模型。
 * 采用每级 10 行、最高第 15 级的曲线；无尽游戏在 15 级保持最高普通模式重力。
 */
object ModernGravity {
    const val LINES_PER_LEVEL = 10
    const val MAX_LEVEL = 15
    const val TARGET_FRAME_RATE = 60.0
    const val LOCK_DELAY_MILLIS = 500L
    const val MAX_LOCK_RESETS = 15

    fun levelForLines(lines: Int): Int = (lines / LINES_PER_LEVEL + 1).coerceAtMost(MAX_LEVEL)

    fun gravityG(level: Int): Double {
        val boundedLevel = level.coerceIn(1, MAX_LEVEL)
        val base = 0.8 - ((boundedLevel - 1) * 0.007)
        val secondsPerCell = base.pow(boundedLevel - 1)
        return 1.0 / (secondsPerCell * TARGET_FRAME_RATE)
    }

    fun cellsPerSecond(level: Int): Double = gravityG(level) * TARGET_FRAME_RATE

    fun nominalCellIntervalMillis(level: Int): Long = ceil(1000.0 / cellsPerSecond(level))
        .toLong()
        .coerceAtLeast(1L)

    fun shouldLock(elapsedMillis: Double): Boolean = elapsedMillis >= LOCK_DELAY_MILLIS

    fun canResetLock(resetCount: Int): Boolean = resetCount < MAX_LOCK_RESETS
}

/** 新方块生成后的输入安全规则，仅拦截可能被残留点击触发的直降。 */
object InputSafetyRules {
    const val HARD_DROP_SPAWN_GUARD_MILLIS = 120L

    fun canHardDrop(remainingMillis: Long): Boolean = remainingMillis <= 0L
}

object PieceLibrary {
    private val shapes: Map<TetrominoType, Array<Array<Pair<Int, Int>>>> = mapOf(
        TetrominoType.I to arrayOf(
            arrayOf(0 to 1, 1 to 1, 2 to 1, 3 to 1),
            arrayOf(2 to 0, 2 to 1, 2 to 2, 2 to 3),
            arrayOf(0 to 2, 1 to 2, 2 to 2, 3 to 2),
            arrayOf(1 to 0, 1 to 1, 1 to 2, 1 to 3)
        ),
        TetrominoType.O to arrayOf(
            arrayOf(1 to 0, 2 to 0, 1 to 1, 2 to 1),
            arrayOf(1 to 0, 2 to 0, 1 to 1, 2 to 1),
            arrayOf(1 to 0, 2 to 0, 1 to 1, 2 to 1),
            arrayOf(1 to 0, 2 to 0, 1 to 1, 2 to 1)
        ),
        TetrominoType.T to arrayOf(
            arrayOf(1 to 0, 0 to 1, 1 to 1, 2 to 1),
            arrayOf(1 to 0, 1 to 1, 2 to 1, 1 to 2),
            arrayOf(0 to 1, 1 to 1, 2 to 1, 1 to 2),
            arrayOf(1 to 0, 0 to 1, 1 to 1, 1 to 2)
        ),
        TetrominoType.S to arrayOf(
            arrayOf(1 to 0, 2 to 0, 0 to 1, 1 to 1),
            arrayOf(1 to 0, 1 to 1, 2 to 1, 2 to 2),
            arrayOf(1 to 1, 2 to 1, 0 to 2, 1 to 2),
            arrayOf(0 to 0, 0 to 1, 1 to 1, 1 to 2)
        ),
        TetrominoType.Z to arrayOf(
            arrayOf(0 to 0, 1 to 0, 1 to 1, 2 to 1),
            arrayOf(2 to 0, 1 to 1, 2 to 1, 1 to 2),
            arrayOf(0 to 1, 1 to 1, 1 to 2, 2 to 2),
            arrayOf(1 to 0, 0 to 1, 1 to 1, 0 to 2)
        ),
        TetrominoType.J to arrayOf(
            arrayOf(0 to 0, 0 to 1, 1 to 1, 2 to 1),
            arrayOf(1 to 0, 2 to 0, 1 to 1, 1 to 2),
            arrayOf(0 to 1, 1 to 1, 2 to 1, 2 to 2),
            arrayOf(1 to 0, 1 to 1, 0 to 2, 1 to 2)
        ),
        TetrominoType.L to arrayOf(
            arrayOf(2 to 0, 0 to 1, 1 to 1, 2 to 1),
            arrayOf(1 to 0, 1 to 1, 1 to 2, 2 to 2),
            arrayOf(0 to 1, 1 to 1, 2 to 1, 0 to 2),
            arrayOf(0 to 0, 1 to 0, 1 to 1, 1 to 2)
        )
    )

    fun blocks(type: TetrominoType, rotation: Int): Array<Pair<Int, Int>> =
        shapes.getValue(type)[rotation % 4]
}

class TetrisGame(private val random: Random = Random.Default) {
    companion object {
        const val ROWS = 20
        const val COLUMNS = 10
        private const val EMPTY = 0
        private const val SPAWN_COLUMN = 3
    }

    private val cells = Array(ROWS) { IntArray(COLUMNS) { EMPTY } }
    private var active: FallingPiece? = null
    private val upcomingQueue = ArrayDeque<TetrominoType>()
    private var lastActionWasRotation = false
    private var consecutiveClears = 0
    private var gravityProgressCells = 0.0
    private var lockDelayElapsedMillis = 0.0
    private var lockResetCount = 0
    private var hardDropGuardMillis = 0L

    var score: Int = 0
        private set
    var lines: Int = 0
        private set
    var isPaused: Boolean = false
        private set
    var isGameOver: Boolean = false
        private set
    var combo: Int = 0
        private set
    var isBackToBack: Boolean = false
        private set
    var lastScoreEvent: ScoreEvent? = null
        private set

    val level: Int get() = ModernGravity.levelForLines(lines)
    val fallDelayMillis: Long get() = ModernGravity.nominalCellIntervalMillis(level)
    val gravityCellsPerSecond: Double get() = ModernGravity.cellsPerSecond(level)
    val lockDelayRemainingMillis: Long get() = ceil(
        (ModernGravity.LOCK_DELAY_MILLIS - lockDelayElapsedMillis).coerceAtLeast(0.0)
    ).toLong()
    val hardDropGuardRemainingMillis: Long get() = hardDropGuardMillis
    val activePiece: FallingPiece? get() = active
    val nextType: TetrominoType get() = upcomingQueue.first()
    val upcomingTypes: List<TetrominoType> get() = upcomingQueue.take(3)

    init {
        startNewGame()
    }

    fun board(): Array<IntArray> = Array(ROWS) { cells[it].copyOf() }

    fun startNewGame() {
        cells.forEach { it.fill(EMPTY) }
        score = 0
        lines = 0
        combo = 0
        consecutiveClears = 0
        isBackToBack = false
        lastScoreEvent = null
        lastActionWasRotation = false
        resetPieceTiming()
        isPaused = false
        isGameOver = false
        upcomingQueue.clear()
        repeat(4) { upcomingQueue.addLast(randomType()) }
        spawnPiece(guardHardDrop = false)
    }

    fun togglePause() {
        if (!isGameOver) isPaused = !isPaused
    }

    fun moveLeft(): Boolean = tryMove(columnDelta = -1)

    fun moveRight(): Boolean = tryMove(columnDelta = 1)

    fun rotateClockwise(): Boolean {
        if (!canControl()) return false
        val candidate = active!!.rotated()
        val kicks = listOf(0 to 0, 0 to -1, 0 to 1, 0 to -2, 0 to 2, -1 to 0)
        kicks.forEach { (rowKick, columnKick) ->
            val kicked = candidate.moved(rowKick, columnKick)
            if (canPlace(kicked)) {
                active = kicked
                lastActionWasRotation = true
                afterGroundedManipulation()
                return true
            }
        }
        return false
    }

    fun softDrop(): Boolean {
        if (!canControl()) return false
        val moved = active!!.moved(rowDelta = 1)
        return if (canPlace(moved)) {
            active = moved
            gravityProgressCells = 0.0
            lockDelayElapsedMillis = 0.0
            lastActionWasRotation = false
            score += 1
            true
        } else {
            // 现代 SRS 风格软降在落地时不立即锁定；锁定延迟由 advanceTime 统一处理。
            false
        }
    }

    fun hardDrop() {
        if (!canControl() || !InputSafetyRules.canHardDrop(hardDropGuardMillis)) return
        var distance = 0
        while (canPlace(active!!.moved(rowDelta = 1))) {
            active = active!!.moved(rowDelta = 1)
            distance++
        }
        if (distance > 0) lastActionWasRotation = false
        score += distance * 2
        lockPiece()
    }

    /**
     * 按真实经过的时间推进游戏。累积小数格重力可准确支持超过 1G 的高等级速度。
     */
    fun advanceTime(deltaMillis: Long) {
        if (!canControl() || deltaMillis <= 0L) return
        hardDropGuardMillis = (hardDropGuardMillis - deltaMillis).coerceAtLeast(0L)

        if (isGrounded()) {
            advanceLockDelay(deltaMillis)
            return
        }

        gravityProgressCells += gravityCellsPerSecond * deltaMillis / 1000.0
        val wholeCells = gravityProgressCells.toInt()
        if (wholeCells <= 0) return
        gravityProgressCells -= wholeCells

        repeat(wholeCells) {
            val moved = active!!.moved(rowDelta = 1)
            if (canPlace(moved)) {
                active = moved
                lastActionWasRotation = false
            } else {
                advanceLockDelay(deltaMillis)
                return
            }
        }
    }

    /** 保留给兼容调用方的单步推进入口。 */
    fun tick() = advanceTime(fallDelayMillis)

    fun ghostPiece(): FallingPiece? {
        val piece = active ?: return null
        var ghost = piece
        while (canPlace(ghost.moved(rowDelta = 1))) ghost = ghost.moved(rowDelta = 1)
        return ghost
    }

    private fun tryMove(rowDelta: Int = 0, columnDelta: Int = 0): Boolean {
        if (!canControl()) return false
        val candidate = active!!.moved(rowDelta, columnDelta)
        return if (canPlace(candidate)) {
            active = candidate
            lastActionWasRotation = false
            if (rowDelta == 0 && columnDelta != 0) afterGroundedManipulation()
            true
        } else {
            false
        }
    }

    private fun lockPiece() {
        val lockedPiece = active ?: return
        val tSpin = isTSpin(lockedPiece)
        lockedPiece.blocks().forEach { block ->
            if (block.row in 0 until ROWS && block.column in 0 until COLUMNS) {
                cells[block.row][block.column] = lockedPiece.type.ordinal + 1
            }
        }

        val cleared = clearCompletedRows()
        val perfectClear = cleared > 0 && cells.all { row -> row.all { it == EMPTY } }
        applyScoring(cleared, tSpin, perfectClear)
        lastActionWasRotation = false
        resetPieceTiming()
        spawnPiece(guardHardDrop = true)
    }

    private fun clearCompletedRows(): Int {
        var cleared = 0
        var writeRow = ROWS - 1
        for (readRow in ROWS - 1 downTo 0) {
            if (cells[readRow].all { it != EMPTY }) {
                cleared++
            } else {
                if (writeRow != readRow) cells[readRow].copyInto(cells[writeRow])
                writeRow--
            }
        }
        while (writeRow >= 0) {
            cells[writeRow].fill(EMPTY)
            writeRow--
        }
        return cleared
    }

    private fun applyScoring(cleared: Int, tSpin: Boolean, perfectClear: Boolean) {
        val levelAtClear = level
        val action = actionFor(cleared, tSpin) ?: run {
            if (cleared == 0) {
                consecutiveClears = 0
                combo = 0
                lastScoreEvent = null
            }
            return
        }

        if (cleared == 0) {
            val tSpinPoints = action.basePoints * levelAtClear
            score += tSpinPoints
            consecutiveClears = 0
            combo = 0
            lastScoreEvent = ScoreEvent(
                title = action.title,
                points = tSpinPoints,
                combo = 0,
                backToBackApplied = false,
                perfectClear = false
            )
            return
        }

        val b2bApplied = action.isDifficult && isBackToBack
        val basePoints = action.basePoints * levelAtClear
        val lineClearPoints = if (b2bApplied) basePoints * 3 / 2 else basePoints
        val comboIndex = consecutiveClears
        val comboPoints = ModernScoring.comboPoints(comboIndex, levelAtClear)
        val perfectClearPoints = if (perfectClear) ModernScoring.perfectClearBonus(cleared, b2bApplied) * levelAtClear else 0
        val totalPoints = lineClearPoints + comboPoints + perfectClearPoints

        score += totalPoints
        lines += cleared
        consecutiveClears++
        combo = comboIndex
        isBackToBack = action.isDifficult
        lastScoreEvent = ScoreEvent(
            title = buildString {
                append(action.title)
                if (b2bApplied) append(" • B2B")
                if (perfectClear) append(" • 全消")
            },
            points = totalPoints,
            combo = comboIndex,
            backToBackApplied = b2bApplied,
            perfectClear = perfectClear
        )
    }

    private fun actionFor(cleared: Int, tSpin: Boolean): ClearAction? = when {
        tSpin && cleared == 0 -> ClearAction.T_SPIN_ZERO
        tSpin && cleared == 1 -> ClearAction.T_SPIN_SINGLE
        tSpin && cleared == 2 -> ClearAction.T_SPIN_DOUBLE
        tSpin && cleared >= 3 -> ClearAction.T_SPIN_TRIPLE
        !tSpin && cleared == 1 -> ClearAction.SINGLE
        !tSpin && cleared == 2 -> ClearAction.DOUBLE
        !tSpin && cleared == 3 -> ClearAction.TRIPLE
        !tSpin && cleared >= 4 -> ClearAction.TETRIS
        else -> null
    }

    private fun isTSpin(piece: FallingPiece): Boolean {
        if (piece.type != TetrominoType.T || !lastActionWasRotation) return false
        val pivotRow = piece.row + 1
        val pivotColumn = piece.column + 1
        val corners = listOf(
            Block(pivotRow - 1, pivotColumn - 1),
            Block(pivotRow - 1, pivotColumn + 1),
            Block(pivotRow + 1, pivotColumn - 1),
            Block(pivotRow + 1, pivotColumn + 1)
        )
        return corners.count { corner ->
            corner.row !in 0 until ROWS ||
                corner.column !in 0 until COLUMNS ||
                cells[corner.row][corner.column] != EMPTY
        } >= 3
    }

    private fun spawnPiece(guardHardDrop: Boolean) {
        val type = upcomingQueue.removeFirst()
        upcomingQueue.addLast(randomType())
        val candidate = FallingPiece(type, rotation = 0, row = 0, column = SPAWN_COLUMN)
        if (canPlace(candidate)) {
            active = candidate
            resetPieceTiming()
            hardDropGuardMillis = if (guardHardDrop) {
                InputSafetyRules.HARD_DROP_SPAWN_GUARD_MILLIS
            } else {
                0L
            }
        } else {
            active = null
            isGameOver = true
            isPaused = false
        }
    }

    private fun isGrounded(): Boolean = active?.let { piece ->
        !canPlace(piece.moved(rowDelta = 1))
    } ?: false

    private fun advanceLockDelay(deltaMillis: Long) {
        lockDelayElapsedMillis += deltaMillis
        if (ModernGravity.shouldLock(lockDelayElapsedMillis)) lockPiece()
    }

    private fun afterGroundedManipulation() {
        if (!isGrounded()) {
            lockDelayElapsedMillis = 0.0
            return
        }
        if (ModernGravity.canResetLock(lockResetCount)) {
            lockDelayElapsedMillis = 0.0
            lockResetCount++
        }
    }

    private fun resetPieceTiming() {
        gravityProgressCells = 0.0
        lockDelayElapsedMillis = 0.0
        lockResetCount = 0
    }

    private fun canControl(): Boolean = active != null && !isPaused && !isGameOver

    private fun canPlace(piece: FallingPiece): Boolean = piece.blocks().all { block ->
        block.row in 0 until ROWS &&
            block.column in 0 until COLUMNS &&
            cells[block.row][block.column] == EMPTY
    }

    private fun randomType(): TetrominoType = TetrominoType.entries[random.nextInt(TetrominoType.entries.size)]
}
