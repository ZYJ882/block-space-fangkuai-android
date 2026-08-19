package com.manus.tetris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.manus.tetris.game.FallingPiece
import com.manus.tetris.game.PieceLibrary
import com.manus.tetris.game.TetrisGame
import com.manus.tetris.game.TetrominoType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val BoardBackground = Color(0xFF0B1726)
private val GridLine = Color(0xFF1E344D)
private val GhostColor = Color(0xFF7894B3)
private val ButtonSurface = Color(0xFF182A3E)

@Composable
fun TetrisApp() {
    val game = remember { TetrisGame() }
    var revision by remember { mutableIntStateOf(0) }
    var hasStarted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(game, hasStarted) {
        while (isActive) {
            if (hasStarted && !game.isPaused && !game.isGameOver) {
                delay(game.fallDelayMillis)
                game.tick()
                revision++
            } else {
                delay(100)
            }
        }
    }

    fun updateGame(action: () -> Unit) {
        action()
        revision++
    }

    if (!hasStarted) {
        StartScreen(
            onStart = {
                updateGame { game.startNewGame() }
                hasStarted = true
            }
        )
    } else {
        GameScreen(
            game = game,
            revision = revision,
            onMoveLeft = { updateGame { game.moveLeft() } },
            onMoveRight = { updateGame { game.moveRight() } },
            onRotate = { updateGame { game.rotateClockwise() } },
            onSoftDrop = { updateGame { game.softDrop() } },
            onHardDrop = { updateGame { game.hardDrop() } },
            onPause = { updateGame { game.togglePause() } },
            onRestart = { updateGame { game.startNewGame() } }
        )
    }
}

@Composable
private fun StartScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("方块空间", style = MaterialTheme.typography.headlineLarge)
        Text(
            "BLOCK SPACE",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(28.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StartMark()
                Text("经典俄罗斯方块", style = MaterialTheme.typography.titleLarge)
                Text(
                    "点击开始后，方块才会下落。\n消除更多行，挑战更高分。",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text("开始游戏", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "左移 · 旋转 · 直落",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StartMark() {
    Canvas(modifier = Modifier.size(148.dp)) {
        val side = size.width / 3.5f
        val blocks = listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1)
        val startX = (size.width - side * 3f) / 2f
        val startY = (size.height - side * 2f) / 2f
        blocks.forEach { (column, row) ->
            drawBlock(
                color = Color(TetrominoType.T.color),
                left = startX + column * side,
                top = startY + row * side,
                side = side
            )
        }
    }
}

@Composable
private fun GameScreen(
    game: TetrisGame,
    revision: Int,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRotate: () -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
    onPause: () -> Unit,
    onRestart: () -> Unit
) {
    // `revision` makes the mutable game engine state observable to Compose.
    @Suppress("UNUSED_VARIABLE")
    val observedRevision = revision
    val board = game.board()
    val activePiece = game.activePiece
    val ghostPiece = game.ghostPiece()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Header(onRestart = onRestart)
        Spacer(Modifier.height(10.dp))
        StatusStrip(
            score = game.score,
            lines = game.lines,
            level = game.level,
            nextType = game.nextType
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            TetrisBoard(
                board = board,
                activePiece = activePiece,
                ghostPiece = ghostPiece,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.5f, matchHeightConstraintsFirst = true)
            )

            when {
                game.isGameOver -> GameOverlay(
                    title = "游戏结束",
                    subtitle = "本局得分 ${game.score}",
                    buttonLabel = "再来一局",
                    onClick = onRestart
                )
                game.isPaused -> GameOverlay(
                    title = "已暂停",
                    subtitle = "准备好后继续挑战",
                    buttonLabel = "继续游戏",
                    onClick = onPause
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Controls(
            paused = game.isPaused,
            gameOver = game.isGameOver,
            onMoveLeft = onMoveLeft,
            onMoveRight = onMoveRight,
            onRotate = onRotate,
            onSoftDrop = onSoftDrop,
            onHardDrop = onHardDrop,
            onPause = onPause
        )
    }
}

@Composable
private fun Header(onRestart: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("方块空间", style = MaterialTheme.typography.headlineLarge)
            Text(
                "经典模式 · 挑战更高分",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onRestart, shape = RoundedCornerShape(12.dp)) {
            Text("重开")
        }
    }
}

@Composable
private fun StatusStrip(score: Int, lines: Int, level: Int, nextType: TetrominoType) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCard(label = "得分", value = score.toString(), modifier = Modifier.weight(1.15f))
        MetricCard(label = "等级", value = level.toString(), modifier = Modifier.weight(0.8f))
        MetricCard(label = "消行", value = lines.toString(), modifier = Modifier.weight(0.8f))
        NextCard(type = nextType, modifier = Modifier.weight(1.25f))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(74.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun NextCard(type: TetrominoType, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(74.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text("下一个", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            NextPiecePreview(type = type, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun NextPiecePreview(type: TetrominoType, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cell = minOf(size.width / 5f, size.height / 3.5f)
        val blocks = PieceLibrary.blocks(type, 0)
        val minX = blocks.minOf { it.first }
        val maxX = blocks.maxOf { it.first }
        val minY = blocks.minOf { it.second }
        val maxY = blocks.maxOf { it.second }
        val groupWidth = (maxX - minX + 1) * cell
        val groupHeight = (maxY - minY + 1) * cell
        val startX = (size.width - groupWidth) / 2f - minX * cell
        val startY = (size.height - groupHeight) / 2f - minY * cell
        blocks.forEach { (x, y) ->
            drawBlock(
                color = Color(type.color),
                left = startX + x * cell,
                top = startY + y * cell,
                side = cell
            )
        }
    }
}

@Composable
private fun TetrisBoard(
    board: Array<IntArray>,
    activePiece: FallingPiece?,
    ghostPiece: FallingPiece?,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
    ) {
        val cell = size.width / TetrisGame.COLUMNS
        drawRect(BoardBackground)

        for (row in 0..TetrisGame.ROWS) {
            drawLine(
                color = GridLine,
                start = Offset(0f, row * cell),
                end = Offset(size.width, row * cell),
                strokeWidth = 1f
            )
        }
        for (column in 0..TetrisGame.COLUMNS) {
            drawLine(
                color = GridLine,
                start = Offset(column * cell, 0f),
                end = Offset(column * cell, size.height),
                strokeWidth = 1f
            )
        }

        board.forEachIndexed { row, columns ->
            columns.forEachIndexed { column, value ->
                if (value != 0) {
                    drawBlock(
                        color = Color(TetrominoType.entries[value - 1].color),
                        left = column * cell,
                        top = row * cell,
                        side = cell
                    )
                }
            }
        }

        ghostPiece?.blocks()?.forEach { block ->
            if (block.row in 0 until TetrisGame.ROWS && block.column in 0 until TetrisGame.COLUMNS) {
                drawBlockOutline(
                    color = GhostColor,
                    left = block.column * cell,
                    top = block.row * cell,
                    side = cell
                )
            }
        }

        activePiece?.blocks()?.forEach { block ->
            if (block.row in 0 until TetrisGame.ROWS && block.column in 0 until TetrisGame.COLUMNS) {
                drawBlock(
                    color = Color(activePiece.type.color),
                    left = block.column * cell,
                    top = block.row * cell,
                    side = cell
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlock(
    color: Color,
    left: Float,
    top: Float,
    side: Float
) {
    val gap = side * 0.075f
    drawRoundRect(
        color = color,
        topLeft = Offset(left + gap, top + gap),
        size = Size(side - gap * 2, side - gap * 2),
        cornerRadius = CornerRadius(side * 0.15f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.20f),
        topLeft = Offset(left + gap * 1.8f, top + gap * 1.8f),
        size = Size(side - gap * 3.6f, (side - gap * 3.6f) * 0.22f),
        cornerRadius = CornerRadius(side * 0.08f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlockOutline(
    color: Color,
    left: Float,
    top: Float,
    side: Float
) {
    val gap = side * 0.12f
    drawRoundRect(
        color = color.copy(alpha = 0.75f),
        topLeft = Offset(left + gap, top + gap),
        size = Size(side - gap * 2, side - gap * 2),
        cornerRadius = CornerRadius(side * 0.12f),
        style = Stroke(width = maxOf(1.5f, side * 0.05f))
    )
}

@Composable
private fun GameOverlay(title: String, subtitle: String, buttonLabel: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.requiredWidthIn(max = 220.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE101C2B)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun Controls(
    paused: Boolean,
    gameOver: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRotate: () -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
    onPause: () -> Unit
) {
    val enabled = !gameOver
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GameButton("←", "左移", enabled, onMoveLeft, Modifier.weight(1f))
            GameButton("↻", "旋转", enabled, onRotate, Modifier.weight(1f))
            GameButton("→", "右移", enabled, onMoveRight, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GameButton("↓", "下落", enabled, onSoftDrop, Modifier.weight(1f))
            GameButton("⇊", "直落", enabled, onHardDrop, Modifier.weight(1f))
            GameButton(if (paused) "▶" else "Ⅱ", if (paused) "继续" else "暂停", enabled, onPause, Modifier.weight(1f))
        }
    }
}

@Composable
private fun GameButton(
    symbol: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ButtonSurface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(symbol, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
