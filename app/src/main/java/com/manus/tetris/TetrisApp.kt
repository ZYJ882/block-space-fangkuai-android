package com.manus.tetris

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.manus.tetris.game.FallingPiece
import com.manus.tetris.game.PieceLibrary
import com.manus.tetris.game.TetrisGame
import com.manus.tetris.game.TetrominoType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val ArenaTop = Color(0xFF0A5BBC)
private val ArenaMid = Color(0xFF063B8A)
private val ArenaBottom = Color(0xFF06255E)
private val BoardBackground = Color(0xFF08265B)
private val BoardInnerGlow = Color(0xFF113E7A)
private val GridLine = Color(0xFF2C67A8)
private val PanelBlue = Color(0xFF0A4F9C)
private val PanelBlueLight = Color(0xFF146CC3)
private val PanelStroke = Color(0xFF4FA2E9)
private val GhostColor = Color(0xFF9DC5F4)
private val ActionBlue = Color(0xFF178EF1)
private val ActionPurple = Color(0xFF8D45EE)
private val ActionGold = Color(0xFFFFA319)

private val ArenaBackground = Brush.verticalGradient(
    colors = listOf(ArenaTop, ArenaMid, ArenaBottom)
)

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
            .background(ArenaBackground)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("方块空间", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Text(
            "BLOCK SPACE",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFB9E7FF)
        )
        Spacer(Modifier.height(28.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PanelBlue.copy(alpha = 0.88f)),
            border = BorderStroke(1.dp, PanelStroke),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StartMark()
                Text("经典俄罗斯方块", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "点击开始后，方块才会下落。\n左移、旋转、直落，一触即发。",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFD5ECFF),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionGold, contentColor = Color.White)
                ) {
                    Text("开始游戏", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
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
    @Suppress("UNUSED_VARIABLE")
    val observedRevision = revision
    val board = game.board()
    val activePiece = game.activePiece
    val ghostPiece = game.ghostPiece()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ArenaBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GameTopBar(
            paused = game.isPaused,
            onPause = onPause,
            onRestart = onRestart
        )
        Spacer(Modifier.height(4.dp))
        ScoreBanner(score = game.score)
        Spacer(Modifier.height(5.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val sideInfoWidth = 54.dp
            val previewWidth = 62.dp
            val sectionGap = 5.dp
            val widestBoard = maxWidth - sideInfoWidth - previewWidth - sectionGap * 2
            val boardHeight = minOf(widestBoard * 2f, maxHeight)
            val boardWidth = boardHeight / 2f
            val playAreaWidth = boardWidth + sideInfoWidth + previewWidth + sectionGap * 2

            Row(
                modifier = Modifier
                    .width(playAreaWidth)
                    .height(boardHeight),
                horizontalArrangement = Arrangement.spacedBy(sectionGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SideInfoPanel(
                    score = game.score,
                    lines = game.lines,
                    level = game.level,
                    modifier = Modifier
                        .width(sideInfoWidth)
                        .fillMaxHeight()
                )

                Box(
                    modifier = Modifier
                        .width(boardWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    TetrisBoard(
                        board = board,
                        activePiece = activePiece,
                        ghostPiece = ghostPiece,
                        modifier = Modifier.fillMaxSize()
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

                UpcomingPanel(
                    types = game.upcomingTypes,
                    modifier = Modifier
                        .width(previewWidth)
                        .fillMaxHeight()
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        TouchControls(
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
private fun GameTopBar(paused: Boolean, onPause: () -> Unit, onRestart: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = PanelBlue.copy(alpha = 0.82f)),
            border = BorderStroke(1.dp, PanelStroke.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(ActionGold),
                    contentAlignment = Alignment.Center
                ) {
                    Text("T", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
                Column {
                    Text("方块空间", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("单机挑战 · SOLO", style = MaterialTheme.typography.labelLarge, color = Color(0xFFC5E9FF))
                }
            }
        }
        HeaderAction(if (paused) "▶" else "Ⅱ", onPause)
        HeaderAction("↺", onRestart)
    }
}

@Composable
private fun HeaderAction(symbol: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(13.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PanelBlueLight, contentColor = Color.White)
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ScoreBanner(score: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("当前得分", style = MaterialTheme.typography.labelLarge, color = Color(0xFFBCE6FF))
        Text(
            score.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
    }
}

@Composable
private fun SideInfoPanel(score: Int, lines: Int, level: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        CompactInfoCard("得分", score.toString(), Modifier.weight(1f))
        CompactInfoCard("等级", level.toString(), Modifier.weight(1f))
        CompactInfoCard("消行", lines.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun CompactInfoCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PanelBlue.copy(alpha = 0.82f)),
        border = BorderStroke(1.dp, PanelStroke.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(13.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Color(0xFFCAEBFF))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun UpcomingPanel(types: List<TetrominoType>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = PanelBlue.copy(alpha = 0.87f)),
        border = BorderStroke(1.dp, PanelStroke),
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("未来", style = MaterialTheme.typography.labelLarge, color = Color.White)
            Text("NEXT 3", style = MaterialTheme.typography.labelLarge, color = Color(0xFFBFE8FF))
            types.forEachIndexed { index, type ->
                UpcomingPieceSlot(
                    type = type,
                    index = index + 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun UpcomingPieceSlot(type: TetrominoType, index: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BoardBackground.copy(alpha = 0.80f)),
        border = BorderStroke(1.dp, PanelStroke.copy(alpha = 0.60f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                index.toString(),
                modifier = Modifier.padding(start = 5.dp, top = 3.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFBCE5FF)
            )
            NextPiecePreview(type = type, modifier = Modifier.fillMaxSize().padding(7.dp))
        }
    }
}

@Composable
private fun NextPiecePreview(type: TetrominoType, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cell = minOf(size.width / 4.8f, size.height / 4.6f)
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
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, PanelStroke, RoundedCornerShape(16.dp))
    ) {
        val cell = size.width / TetrisGame.COLUMNS
        drawRect(BoardBackground)
        drawRoundRect(
            color = BoardInnerGlow.copy(alpha = 0.38f),
            topLeft = Offset(cell * 0.18f, cell * 0.18f),
            size = Size(size.width - cell * 0.36f, size.height - cell * 0.36f),
            cornerRadius = CornerRadius(cell * 0.35f)
        )

        for (row in 0..TetrisGame.ROWS) {
            drawLine(
                color = GridLine.copy(alpha = 0.65f),
                start = Offset(0f, row * cell),
                end = Offset(size.width, row * cell),
                strokeWidth = 1f
            )
        }
        for (column in 0..TetrisGame.COLUMNS) {
            drawLine(
                color = GridLine.copy(alpha = 0.65f),
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
        color = Color.White.copy(alpha = 0.22f),
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
        color = color.copy(alpha = 0.80f),
        topLeft = Offset(left + gap, top + gap),
        size = Size(side - gap * 2, side - gap * 2),
        cornerRadius = CornerRadius(side * 0.12f),
        style = Stroke(width = maxOf(1.5f, side * 0.05f))
    )
}

@Composable
private fun GameOverlay(title: String, subtitle: String, buttonLabel: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.requiredWidthIn(max = 205.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xED08265B)),
        border = BorderStroke(1.dp, PanelStroke),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFD5ECFF),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActionGold)
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun TouchControls(
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlDisc("←", ActionBlue, enabled, onMoveLeft, repeatOnHold = true)
            ControlDisc("→", ActionBlue, enabled, onMoveRight, repeatOnHold = true)
            ControlDisc("↓", ActionBlue, enabled, onSoftDrop, repeatOnHold = true)
            ControlDisc("⇊", ActionGold, enabled, onHardDrop)
            ControlDisc("↻", ActionPurple, enabled, onRotate)
        }
        Button(
            onClick = onPause,
            enabled = enabled,
            modifier = Modifier.height(30.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PanelBlueLight, contentColor = Color.White)
        ) {
            Text(if (paused) "▶" else "Ⅱ", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ControlDisc(
    symbol: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    repeatOnHold: Boolean = false
) {
    if (!repeatOnHold) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
        ) {
            Text(symbol, style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    val latestAction by rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()
    val repeatJobHolder = remember { arrayOfNulls<Job>(1) }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (enabled) color else color.copy(alpha = 0.38f))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        latestAction()
                        repeatJobHolder[0] = scope.launch {
                            delay(260)
                            while (true) {
                                latestAction()
                                delay(72)
                            }
                        }
                        try {
                            tryAwaitRelease()
                        } finally {
                            repeatJobHolder[0]?.cancel()
                            repeatJobHolder[0] = null
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}
