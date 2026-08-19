package com.manus.tetris.game

import kotlin.math.max
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
    private var next: TetrominoType = randomType()

    var score: Int = 0
        private set
    var lines: Int = 0
        private set
    var isPaused: Boolean = false
        private set
    var isGameOver: Boolean = false
        private set

    val level: Int get() = lines / 10 + 1
    val fallDelayMillis: Long get() = max(120, 820 - (level - 1) * 65).toLong()
    val activePiece: FallingPiece? get() = active
    val nextType: TetrominoType get() = next

    init {
        startNewGame()
    }

    fun board(): Array<IntArray> = Array(ROWS) { cells[it].copyOf() }

    fun startNewGame() {
        cells.forEach { it.fill(EMPTY) }
        score = 0
        lines = 0
        isPaused = false
        isGameOver = false
        next = randomType()
        spawnPiece()
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
            score += 1
            true
        } else {
            lockPiece()
            false
        }
    }

    fun hardDrop() {
        if (!canControl()) return
        var distance = 0
        while (canPlace(active!!.moved(rowDelta = 1))) {
            active = active!!.moved(rowDelta = 1)
            distance++
        }
        score += distance * 2
        lockPiece()
    }

    fun tick() {
        if (!canControl()) return
        val moved = active!!.moved(rowDelta = 1)
        if (canPlace(moved)) active = moved else lockPiece()
    }

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
            true
        } else false
    }

    private fun lockPiece() {
        active?.blocks()?.forEach { block ->
            if (block.row in 0 until ROWS && block.column in 0 until COLUMNS) {
                cells[block.row][block.column] = active!!.type.ordinal + 1
            }
        }
        clearCompletedRows()
        spawnPiece()
    }

    private fun clearCompletedRows() {
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
        if (cleared > 0) {
            val points = when (cleared) {
                1 -> 100
                2 -> 300
                3 -> 500
                else -> 800
            }
            score += points * level
            lines += cleared
        }
    }

    private fun spawnPiece() {
        val type = next
        next = randomType()
        val candidate = FallingPiece(type, rotation = 0, row = 0, column = SPAWN_COLUMN)
        if (canPlace(candidate)) active = candidate else {
            active = null
            isGameOver = true
            isPaused = false
        }
    }

    private fun canControl(): Boolean = active != null && !isPaused && !isGameOver

    private fun canPlace(piece: FallingPiece): Boolean = piece.blocks().all { block ->
        block.row in 0 until ROWS &&
            block.column in 0 until COLUMNS &&
            cells[block.row][block.column] == EMPTY
    }

    private fun randomType(): TetrominoType = TetrominoType.entries[random.nextInt(TetrominoType.entries.size)]
}
