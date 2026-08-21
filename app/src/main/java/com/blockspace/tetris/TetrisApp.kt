package com.blockspace.tetris

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.blockspace.tetris.controls.ControlAction
import com.blockspace.tetris.controls.ControlAreaGeometry
import com.blockspace.tetris.controls.ControlSettings
import com.blockspace.tetris.controls.ControlSettingsStore
import com.blockspace.tetris.controls.FallSpeedPreset
import com.blockspace.tetris.controls.FreeControlLayout
import com.blockspace.tetris.controls.PixelPoint
import com.blockspace.tetris.controls.PieceRandomizerMode
import com.blockspace.tetris.controls.HandlingPreset
import com.blockspace.tetris.controls.HandlingSettings
import com.blockspace.tetris.audio.GameSoundEffect
import com.blockspace.tetris.audio.GameSoundPlayer
import com.blockspace.tetris.game.FallingPiece
import com.blockspace.tetris.game.PieceLibrary
import com.blockspace.tetris.game.TetrisGame
import com.blockspace.tetris.game.TetrominoType
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val context = LocalContext.current
    val soundPlayer = remember(context) { GameSoundPlayer(context.applicationContext) }
    val controlSettingsStore = remember(context) { ControlSettingsStore(context) }
    var controlSettings by remember { mutableStateOf(controlSettingsStore.load()) }
    var revision by remember { mutableIntStateOf(0) }
    var timingRevision by remember { mutableIntStateOf(0) }
    var hasStarted by rememberSaveable { mutableStateOf(false) }
    var showControlSettings by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var appIsActive by remember { mutableStateOf(true) }

    DisposableEffect(soundPlayer) {
        onDispose { soundPlayer.release() }
    }

    LaunchedEffect(soundPlayer, controlSettings.soundEffectsEnabled) {
        soundPlayer.setEnabled(controlSettings.soundEffectsEnabled)
    }

    var observedLockRevision by remember { mutableIntStateOf(game.lockRevision) }
    LaunchedEffect(revision) {
        val currentLockRevision = game.lockRevision
        if (currentLockRevision <= observedLockRevision) {
            observedLockRevision = currentLockRevision
            return@LaunchedEffect
        }
        observedLockRevision = currentLockRevision
        when {
            game.isGameOver -> soundPlayer.play(GameSoundEffect.GAME_OVER)
            game.lastClearedLines > 0 && (
                game.lastScoreEvent?.title?.contains("T-SPIN") == true ||
                    game.lastScoreEvent?.perfectClear == true
                ) -> soundPlayer.play(GameSoundEffect.SPECIAL_CLEAR)
            game.lastClearedLines > 0 -> soundPlayer.play(GameSoundEffect.LINE_CLEAR)
            else -> soundPlayer.play(GameSoundEffect.LOCK)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    appIsActive = false
                    timingRevision++
                }
                Lifecycle.Event.ON_RESUME -> {
                    appIsActive = true
                    timingRevision++
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun updateControlSettings(next: ControlSettings) {
        game.setFallSpeed(next.fallSpeed)
        game.setPieceRandomizerMode(next.pieceRandomizer)
        controlSettings = next
        controlSettingsStore.save(next)
        timingRevision++
    }

    LaunchedEffect(game, controlSettings.fallSpeed, controlSettings.pieceRandomizer) {
        game.setFallSpeed(controlSettings.fallSpeed)
        game.setPieceRandomizerMode(controlSettings.pieceRandomizer)
    }

    LaunchedEffect(game, hasStarted, appIsActive, timingRevision) {
        if (!hasStarted || !appIsActive || game.isPaused || game.isGameOver) return@LaunchedEffect

        val wakeDelayMillis = game.nextRuleEventDelayMillis()
        val startedAtNanos = System.nanoTime()
        delay(wakeDelayMillis)
        val elapsedMillis = ((System.nanoTime() - startedAtNanos) / 1_000_000L)
            .coerceAtLeast(1L)

        if (game.advanceTime(elapsedMillis)) {
            revision++
        }
        // 规则事件到期、输入或状态切换后重新计算最近的到期时间。
        timingRevision++
    }

    fun updateGame(action: () -> Unit) {
        action()
        revision++
        // 输入会改变重力进度、锁定时间或直降保护，需要取消旧等待并重新安排。
        timingRevision++
    }

    if (!hasStarted) {
        StartScreen(
            onStart = {
                updateGame {
                    game.setPieceRandomizerMode(controlSettings.pieceRandomizer)
                    game.startNewGame()
                    soundPlayer.play(GameSoundEffect.START)
                }
                hasStarted = true
            }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            GameScreen(
                game = game,
                revision = revision,
                onMoveLeft = { updateGame { if (game.moveLeft()) soundPlayer.play(GameSoundEffect.MOVE) } },
                onMoveRight = { updateGame { if (game.moveRight()) soundPlayer.play(GameSoundEffect.MOVE) } },
                onRotate = { updateGame { if (game.rotateClockwise()) soundPlayer.play(GameSoundEffect.ROTATE) } },
                onSoftDrop = { updateGame { if (game.softDrop()) soundPlayer.play(GameSoundEffect.SOFT_DROP) } },
                onHardDrop = { updateGame { if (game.hardDrop()) soundPlayer.play(GameSoundEffect.HARD_DROP) } },
                onPause = {
                    updateGame {
                        if (!game.isGameOver) {
                            game.togglePause()
                            soundPlayer.play(GameSoundEffect.PAUSE)
                        }
                    }
                },
                onRestart = {
                    updateGame {
                        game.startNewGame()
                        soundPlayer.play(GameSoundEffect.START)
                    }
                },
                controlSettings = controlSettings,
                isEditingControls = showControlSettings,
                onControlSettingsChange = ::updateControlSettings,
                onFinishControlEditing = { showControlSettings = false },
                onOpenControlSettings = {
                    if (!game.isPaused) updateGame { game.togglePause() }
                    showControlSettings = true
                }
            )
        }
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
    onRestart: () -> Unit,
    controlSettings: ControlSettings,
    isEditingControls: Boolean,
    onControlSettingsChange: (ControlSettings) -> Unit,
    onFinishControlEditing: () -> Unit,
    onOpenControlSettings: () -> Unit
) {
    @Suppress("UNUSED_VARIABLE")
    val observedRevision = revision
    val board = game.board()
    val activePiece = game.activePiece
    val ghostPiece = game.ghostPiece()
    val scoreEvent = game.lastScoreEvent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArenaBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                paused = game.isPaused,
                onPause = onPause,
                onRestart = onRestart,
                onOpenControlSettings = onOpenControlSettings
            )
            Spacer(Modifier.height(4.dp))
            ScoreBanner(
                score = game.score,
                eventTitle = scoreEvent?.title,
                eventPoints = scoreEvent?.points ?: 0,
                combo = game.combo,
                b2bReady = game.isBackToBack,
                fallSpeed = controlSettings.fallSpeed
            )
            Spacer(Modifier.height(5.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 128.dp),
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
                            clearRevision = game.clearRevision,
                            clearedRows = game.lastClearedRows,
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
        }

        FreeTouchControls(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(132.dp)
                .padding(horizontal = 6.dp),
            gameOver = game.isGameOver,
            controlSettings = controlSettings,
            isEditing = isEditingControls,
            onControlSettingsChange = onControlSettingsChange,
            onMoveLeft = onMoveLeft,
            onMoveRight = onMoveRight,
            onRotate = onRotate,
            onSoftDrop = onSoftDrop,
            onHardDrop = onHardDrop
        )
        if (isEditingControls) {
            FullScreenControlEditorToolbar(
                settings = controlSettings,
                onSettingsChange = onControlSettingsChange,
                onRestoreDefaults = { onControlSettingsChange(controlSettings.copy(layout = FreeControlLayout.standard())) },
                onDone = onFinishControlEditing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun FullScreenControlEditorToolbar(
    settings: ControlSettings,
    onSettingsChange: (ControlSettings) -> Unit,
    onRestoreDefaults: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvancedHandling by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        colors = CardDefaults.cardColors(containerColor = BoardBackground.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, PanelStroke),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("键位编辑", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Button(
                    onClick = onDone,
                    modifier = Modifier.height(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionGold, contentColor = Color.White)
                ) {
                    Text("完成", style = MaterialTheme.typography.labelLarge)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("① 长按灵敏度", style = MaterialTheme.typography.labelLarge, color = Color(0xFFBFE8FF))
                Text(
                    "只影响左移、右移、软降的连续触发速度；不会改变按键位置。",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFD5ECFF)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HandlingPreset.entries.filter { it != HandlingPreset.CUSTOM }.forEach { preset ->
                        Button(
                            onClick = { onSettingsChange(settings.applyPreset(preset)) },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(11.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (settings.preset == preset) ActionPurple else PanelBlueLight,
                                contentColor = Color.White
                            )
                        ) {
                            Text(preset.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Button(
                    onClick = { showAdvancedHandling = !showAdvancedHandling },
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showAdvancedHandling) PanelBlueLight else PanelBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (showAdvancedHandling) "收起高级灵敏度" else "高级灵敏度（DAS / ARR）",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                if (showAdvancedHandling) {
                    AdvancedHandlingPanel(
                        settings = settings,
                        onSettingsChange = onSettingsChange
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("② 下落速度与挑战分", style = MaterialTheme.typography.labelLarge, color = Color(0xFFBFE8FF))
                Text(
                    "只改变自动下落速度。软降 +1/格、直降 +2/格固定；清行、T-Spin、连击和全消按挑战倍率结算。",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFD5ECFF)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    FallSpeedPreset.entries.forEach { speed ->
                        Button(
                            onClick = { onSettingsChange(settings.applyFallSpeed(speed)) },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(11.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (settings.fallSpeed == speed) ActionGold else PanelBlueLight,
                                contentColor = Color.White
                            )
                        ) {
                            Text(speed.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Text(
                    "当前：自动下落 ×${settings.fallSpeed.gravityMultiplier} · 挑战分 ×${settings.fallSpeed.challengeScoreMultiplier}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFFFD38A)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("③ 方块刷新逻辑", style = MaterialTheme.typography.labelLarge, color = Color(0xFFBFE8FF))
                Text(
                    "选择会立即保存，并从下一局开始生效；不会改写当前对局已生成的预览队列。",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFD5ECFF)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PieceRandomizerMode.entries.forEach { mode ->
                        Button(
                            onClick = { onSettingsChange(settings.applyPieceRandomizer(mode)) },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(11.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (settings.pieceRandomizer == mode) ActionPurple else PanelBlueLight,
                                contentColor = Color.White
                            )
                        ) {
                            Text(mode.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Text(
                    when (settings.pieceRandomizer) {
                        PieceRandomizerMode.SEVEN_BAG -> "7-Bag：每一袋恰好包含七种方块各一个，适合公平挑战与提前规划，不会长期缺少某一种。"
                        PieceRandomizerMode.TRUE_RANDOM -> "真随机：每次独立等概率抽取，可能连续出现相同方块，也可能长时间不出现某一种。"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFFFD38A)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("④ 游戏音效", style = MaterialTheme.typography.labelLarge, color = Color(0xFFBFE8FF))
                Text(
                    "包含移动、旋转、下落、锁定、消行、暂停与游戏结束提示；长按操作会自动节流，避免声音堆叠。",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFD5ECFF)
                )
                Button(
                    onClick = {
                        onSettingsChange(settings.applySoundEffectsEnabled(!settings.soundEffectsEnabled))
                    },
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (settings.soundEffectsEnabled) ActionGold else PanelBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (settings.soundEffectsEnabled) "音效已开启（点击关闭）" else "音效已关闭（点击开启）",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("⑤ 键位位置", style = MaterialTheme.typography.labelLarge, color = Color(0xFFBFE8FF))
                    Text("直接拖动下方真实按键即可调整位置。", style = MaterialTheme.typography.labelLarge, color = Color(0xFFD5ECFF))
                }
                Button(
                    onClick = onRestoreDefaults,
                    modifier = Modifier.height(38.dp),
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PanelBlue, contentColor = Color.White)
                ) {
                    Text("恢复标准键位", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun AdvancedHandlingPanel(
    settings: ControlSettings,
    onSettingsChange: (ControlSettings) -> Unit
) {
    val handling = settings.handling
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PanelBlue.copy(alpha = 0.74f))
            .border(1.dp, PanelStroke.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            "当前：${handling.dasMillis}ms DAS · ${handling.arrMillis}ms ARR",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
        Text(
            "DAS 是长按后开始连发前的等待时间。",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFD5ECFF)
        )
        Text(
            "DAS：${handling.dasMillis}ms（100–220ms）",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFBFE8FF)
        )
        Slider(
            value = handling.dasMillis.toFloat(),
            onValueChange = { value ->
                onSettingsChange(
                    settings.applyAdvancedHandling(
                        dasMillis = value.roundToInt().toLong(),
                        arrMillis = handling.arrMillis
                    )
                )
            },
            valueRange = HandlingSettings.DAS_RANGE.first.toFloat()..HandlingSettings.DAS_RANGE.last.toFloat(),
            steps = 11
        )
        Text(
            "ARR：${handling.arrMillis}ms（25–80ms）",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFBFE8FF)
        )
        Slider(
            value = handling.arrMillis.toFloat(),
            onValueChange = { value ->
                onSettingsChange(
                    settings.applyAdvancedHandling(
                        dasMillis = handling.dasMillis,
                        arrMillis = value.roundToInt().toLong()
                    )
                )
            },
            valueRange = HandlingSettings.ARR_RANGE.first.toFloat()..HandlingSettings.ARR_RANGE.last.toFloat(),
            steps = 10
        )
        Text(
            "ARR 是连发触发间隔；数值越小，连续移动越快。",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFD5ECFF)
        )
        Button(
            onClick = { onSettingsChange(settings.applyPreset(HandlingPreset.COMFORT)) },
            modifier = Modifier.fillMaxWidth().height(32.dp),
            shape = RoundedCornerShape(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PanelBlueLight, contentColor = Color.White)
        ) {
            Text("恢复推荐值（160ms / 50ms）", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun GameTopBar(
    paused: Boolean,
    onPause: () -> Unit,
    onRestart: () -> Unit,
    onOpenControlSettings: () -> Unit
) {
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
        HeaderAction("⚙", onOpenControlSettings)
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
private fun ScoreBanner(
    score: Int,
    eventTitle: String?,
    eventPoints: Int,
    combo: Int,
    b2bReady: Boolean,
    fallSpeed: FallSpeedPreset
) {
    val displayedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "scoreCounter"
    )
    var showScoreEvent by remember { mutableStateOf(false) }
    LaunchedEffect(eventTitle, eventPoints, score) {
        if (eventTitle == null) {
            showScoreEvent = false
            return@LaunchedEffect
        }
        showScoreEvent = true
        delay(950)
        showScoreEvent = false
    }
    val eventAlpha by animateFloatAsState(
        targetValue = if (showScoreEvent) 1f else 0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "scoreEventAlpha"
    )
    val eventScale by animateFloatAsState(
        targetValue = if (showScoreEvent) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "scoreEventScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text("当前得分", style = MaterialTheme.typography.labelLarge, color = Color(0xFFBCE6FF))
        Text(
            displayedScore.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Text(
            "速度 ×${fallSpeed.gravityMultiplier} · 挑战分 ×${fallSpeed.challengeScoreMultiplier}",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFBCE6FF)
        )
        when {
            eventTitle != null && eventAlpha > 0.01f -> Text(
                "$eventTitle  +$eventPoints",
                modifier = Modifier.graphicsLayer {
                    alpha = eventAlpha
                    scaleX = eventScale
                    scaleY = eventScale
                },
                style = MaterialTheme.typography.labelLarge,
                color = ActionGold
            )
            b2bReady -> Text(
                "B2B READY",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFD39AFF)
            )
        }
        if (combo > 0) {
            Text(
                "COMBO ×$combo",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF86EEBB)
            )
        }
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
    clearRevision: Int,
    clearedRows: List<Int>,
    modifier: Modifier = Modifier
) {
    val visualRow = remember { Animatable(activePiece?.row?.toFloat() ?: 0f) }
    val visualColumn = remember { Animatable(activePiece?.column?.toFloat() ?: 0f) }
    var previousPiece by remember { mutableStateOf(activePiece) }
    val clearFlash = remember { Animatable(0f) }

    LaunchedEffect(activePiece) {
        val target = activePiece
        if (target == null) return@LaunchedEffect
        val previous = previousPiece
        val shouldSnap = previous == null ||
            previous.type != target.type ||
            target.row < previous.row ||
            abs(target.row - previous.row) > 1
        if (shouldSnap) {
            visualRow.snapTo(target.row.toFloat())
            visualColumn.snapTo(target.column.toFloat())
        } else {
            launch {
                visualRow.animateTo(
                    target.row.toFloat(),
                    animationSpec = tween(durationMillis = 72, easing = FastOutSlowInEasing)
                )
            }
            launch {
                visualColumn.animateTo(
                    target.column.toFloat(),
                    animationSpec = tween(durationMillis = 64, easing = FastOutSlowInEasing)
                )
            }
        }
        previousPiece = target
    }

    LaunchedEffect(clearRevision) {
        if (clearRevision == 0) return@LaunchedEffect
        clearFlash.snapTo(1f)
        clearFlash.animateTo(0f, animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing))
    }
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
                val offsetRow = visualRow.value - activePiece.row
                val offsetColumn = visualColumn.value - activePiece.column
                drawBlock(
                    color = Color(activePiece.type.color),
                    left = (block.column + offsetColumn) * cell,
                    top = (block.row + offsetRow) * cell,
                    side = cell
                )
            }
        }

        if (clearFlash.value > 0f) {
            clearedRows.forEach { row ->
                drawRect(
                    color = Color.White.copy(alpha = clearFlash.value * 0.20f),
                    topLeft = Offset(0f, row * cell),
                    size = Size(size.width, cell)
                )
                drawLine(
                    color = ActionGold.copy(alpha = clearFlash.value * 0.46f),
                    start = Offset(0f, row * cell + cell / 2f),
                    end = Offset(size.width, row * cell + cell / 2f),
                    strokeWidth = cell * 0.045f
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
private fun ControlSettingsOverlay(
    settings: ControlSettings,
    onSettingsChange: (ControlSettings) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB7081636))
            .padding(horizontal = 22.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .requiredWidthIn(max = 360.dp),
            colors = CardDefaults.cardColors(containerColor = BoardBackground),
            border = BorderStroke(1.dp, PanelStroke),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("控制设置", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("长按速度", style = MaterialTheme.typography.labelLarge, color = Color(0xFFBFE8FF))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HandlingPreset.entries.filter { it != HandlingPreset.CUSTOM }.forEach { preset ->
                        val selected = settings.preset == preset
                        Button(
                            onClick = { onSettingsChange(settings.applyPreset(preset)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) ActionGold else PanelBlueLight,
                                contentColor = Color.White
                            )
                        ) {
                            Text(preset.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Text(
                    "${settings.handling.dasMillis}ms 启动 · ${settings.handling.arrMillis}ms 重复",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFD5ECFF)
                )
                Text("自由键位", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    "直接拖动按钮到控制区域任意位置。触及边界会被限制；与其他按钮的命中区重叠或间距不足时会停在上一个有效位置。",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFBFE8FF),
                    textAlign = TextAlign.Center
                )
                FreeLayoutEditor(
                    layout = settings.layout,
                    onLayoutChange = { next -> onSettingsChange(settings.copy(layout = next)) }
                )
                Button(
                    onClick = { onSettingsChange(settings.copy(layout = FreeControlLayout.standard())) },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PanelBlueLight, contentColor = Color.White)
                ) {
                    Text("恢复标准位置", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    "边界：按钮只能位于控制区域内；按钮命中区之间至少保留 8dp 间距。",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFBFE8FF),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionGold, contentColor = Color.White)
                ) {
                    Text("完成", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun FreeLayoutEditor(
    layout: FreeControlLayout,
    onLayoutChange: (FreeControlLayout) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PanelBlue.copy(alpha = 0.62f))
            .border(1.dp, PanelStroke, RoundedCornerShape(18.dp))
    ) {
        val density = LocalDensity.current
        val geometry = with(density) {
            ControlAreaGeometry(
                width = maxWidth.toPx(),
                height = maxHeight.toPx(),
                buttonWidth = 64.dp.toPx(),
                buttonHeight = 58.dp.toPx(),
                minimumGap = 8.dp.toPx()
            )
        }
        ControlAction.entries.forEach { action ->
            FreePositionedEditorButton(
                action = action,
                layout = layout,
                geometry = geometry,
                onLayoutChange = onLayoutChange
            )
        }
    }
}

@Composable
private fun FreePositionedEditorButton(
    action: ControlAction,
    layout: FreeControlLayout,
    geometry: ControlAreaGeometry,
    onLayoutChange: (FreeControlLayout) -> Unit
) {
    val density = LocalDensity.current
    val latestLayout by rememberUpdatedState(layout)
    val latestOnLayoutChange by rememberUpdatedState(onLayoutChange)
    val pixelPosition = geometry.toPixel(layout.positionOf(action))
    val buttonWidth = with(density) { geometry.buttonWidth.toDp() }
    val buttonHeight = with(density) { geometry.buttonHeight.toDp() }

    Box(
        modifier = Modifier
            .offset { IntOffset(pixelPosition.x.roundToInt(), pixelPosition.y.roundToInt()) }
            .size(buttonWidth, buttonHeight)
            .clip(RoundedCornerShape(13.dp))
            .background(actionColor(action))
            .pointerInput(action, geometry) {
                var currentPoint = PixelPoint(0f, 0f)
                detectDragGestures(
                    onDragStart = {
                        currentPoint = geometry.toPixel(latestLayout.positionOf(action))
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val candidate = geometry.clamp(
                            PixelPoint(currentPoint.x + dragAmount.x, currentPoint.y + dragAmount.y)
                        )
                        val next = latestLayout.moveIfValid(action, candidate, geometry)
                        if (next != null) {
                            currentPoint = candidate
                            latestOnLayoutChange(next)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(action.symbol, style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}

@Composable
private fun FreeTouchControls(
    modifier: Modifier,
    gameOver: Boolean,
    controlSettings: ControlSettings,
    isEditing: Boolean,
    onControlSettingsChange: (ControlSettings) -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRotate: () -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit
) {
    val canvasModifier = if (isEditing) {
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(PanelBlue.copy(alpha = 0.38f))
            .border(1.dp, PanelStroke, RoundedCornerShape(18.dp))
    } else {
        modifier
    }
    BoxWithConstraints(modifier = canvasModifier) {
        val density = LocalDensity.current
        val buttonWidth = if (maxWidth >= 340.dp) 64.dp else 56.dp
        val buttonHeight = if (maxWidth >= 340.dp) 58.dp else 54.dp
        val geometry = with(density) {
            ControlAreaGeometry(
                width = maxWidth.toPx(),
                height = maxHeight.toPx(),
                buttonWidth = buttonWidth.toPx(),
                buttonHeight = buttonHeight.toPx(),
                minimumGap = 8.dp.toPx()
            )
        }
        val activeLayout = if (controlSettings.layout.isValidFor(geometry)) {
            controlSettings.layout
        } else {
            FreeControlLayout.standard()
        }
        ControlAction.entries.forEach { action ->
            FreeGameplayButton(
                action = action,
                layout = activeLayout,
                geometry = geometry,
                enabled = !gameOver,
                isEditing = isEditing,
                onLayoutChange = { next -> onControlSettingsChange(controlSettings.copy(layout = next)) },
                buttonWidth = buttonWidth,
                buttonHeight = buttonHeight,
                initialDelayMillis = controlSettings.handling.dasMillis,
                repeatIntervalMillis = controlSettings.handling.arrMillis,
                onMoveLeft = onMoveLeft,
                onMoveRight = onMoveRight,
                onSoftDrop = onSoftDrop,
                onRotate = onRotate,
                onHardDrop = onHardDrop
            )
        }
    }
}

@Composable
private fun FreeGameplayButton(
    action: ControlAction,
    layout: FreeControlLayout,
    geometry: ControlAreaGeometry,
    enabled: Boolean,
    isEditing: Boolean,
    onLayoutChange: (FreeControlLayout) -> Unit,
    buttonWidth: Dp,
    buttonHeight: Dp,
    initialDelayMillis: Long,
    repeatIntervalMillis: Long,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onSoftDrop: () -> Unit,
    onRotate: () -> Unit,
    onHardDrop: () -> Unit
) {
    if (isEditing) {
        FreePositionedEditorButton(
            action = action,
            layout = layout,
            geometry = geometry,
            onLayoutChange = onLayoutChange
        )
        return
    }
    val position = geometry.toPixel(layout.positionOf(action))
    ControlButtonForAction(
        action = action,
        enabled = enabled,
        buttonWidth = buttonWidth,
        buttonHeight = buttonHeight,
        initialDelayMillis = initialDelayMillis,
        repeatIntervalMillis = repeatIntervalMillis,
        onMoveLeft = onMoveLeft,
        onMoveRight = onMoveRight,
        onSoftDrop = onSoftDrop,
        onRotate = onRotate,
        onHardDrop = onHardDrop,
        modifier = Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
    )
}

@Composable
private fun ControlButtonForAction(
    action: ControlAction,
    enabled: Boolean,
    buttonWidth: Dp,
    buttonHeight: Dp,
    initialDelayMillis: Long,
    repeatIntervalMillis: Long,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onSoftDrop: () -> Unit,
    onRotate: () -> Unit,
    onHardDrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (action) {
        ControlAction.MOVE_LEFT -> ControlDisc(action.symbol, actionColor(action), enabled, buttonWidth, buttonHeight, onMoveLeft, true, initialDelayMillis, repeatIntervalMillis, modifier)
        ControlAction.MOVE_RIGHT -> ControlDisc(action.symbol, actionColor(action), enabled, buttonWidth, buttonHeight, onMoveRight, true, initialDelayMillis, repeatIntervalMillis, modifier)
        ControlAction.SOFT_DROP -> ControlDisc(action.symbol, actionColor(action), enabled, buttonWidth, buttonHeight, onSoftDrop, true, initialDelayMillis, repeatIntervalMillis, modifier)
        ControlAction.ROTATE -> ControlDisc(action.symbol, actionColor(action), enabled, buttonWidth, buttonHeight, onRotate, false, modifier = modifier)
        ControlAction.HARD_DROP -> ControlDisc(action.symbol, actionColor(action), enabled, buttonWidth, buttonHeight, onHardDrop, false, modifier = modifier)
    }
}

private fun actionColor(action: ControlAction): Color = when (action) {
    ControlAction.MOVE_LEFT, ControlAction.MOVE_RIGHT, ControlAction.SOFT_DROP -> ActionBlue
    ControlAction.ROTATE -> ActionPurple
    ControlAction.HARD_DROP -> ActionGold
}

@Composable
private fun ControlDisc(
    symbol: String,
    color: Color,
    enabled: Boolean,
    buttonWidth: Dp,
    buttonHeight: Dp,
    onClick: () -> Unit,
    repeatOnHold: Boolean = false,
    initialDelayMillis: Long = 0L,
    repeatIntervalMillis: Long = 0L,
    modifier: Modifier = Modifier
) {
    if (!repeatOnHold) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(width = buttonWidth, height = buttonHeight),
            shape = RoundedCornerShape(16.dp),
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
        modifier = modifier
            .size(width = buttonWidth, height = buttonHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) color else color.copy(alpha = 0.38f))
            .pointerInput(enabled, initialDelayMillis, repeatIntervalMillis) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        latestAction()
                        repeatJobHolder[0] = scope.launch {
                            delay(initialDelayMillis)
                            while (true) {
                                latestAction()
                                delay(repeatIntervalMillis)
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
