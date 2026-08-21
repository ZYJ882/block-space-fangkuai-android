package com.blockspace.tetris.network

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blockspace.tetris.game.PieceLibrary
import com.blockspace.tetris.game.TetrisGame
import com.blockspace.tetris.game.TetrominoType

private val LanPanel = Color(0xFF073875)
private val LanPanelLight = Color(0xFF0C5EAE)
private val LanStroke = Color(0xFF5DB9FF)
private val LanGhost = Color(0xFFB6D7F4)
private val GarbageColor = Color(0xFF64748B)

@Composable
fun LanLobbyScreen(
    state: LanUiState,
    onCreateRoom: () -> Unit,
    onRefresh: () -> Unit,
    onJoinRoom: (DiscoveredRoom) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06255E))
            .padding(horizontal = 20.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("局域网对战", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text("LAN BATTLE", style = MaterialTheme.typography.labelLarge, color = Color(0xFFB9E7FF))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = LanPanel),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD8EEFF)
                )
                Text(
                    "仅使用同一 Wi‑Fi 或手机热点；不需要互联网、账号或云端服务器。",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF9CCBF0)
                )
            }
        }

        when (state.status) {
            LanStatus.CONNECTED -> Text("双方已准备好，正在进入对局…", color = Color(0xFF91F6C0))
            LanStatus.HOSTING, LanStatus.JOINING -> WaitingRoomCard(state, onBack)
            else -> RoomBrowser(state, onCreateRoom, onRefresh, onJoinRoom, onBack)
        }
    }
}

@Composable
private fun WaitingRoomCard(state: LanUiState, onBack: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LanPanelLight),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (state.isHost) "房间已创建" else "正在连接", style = MaterialTheme.typography.titleLarge, color = Color.White)
            state.roomName?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = Color(0xFFCBECFF), textAlign = TextAlign.Center)
            }
            Text("请保持应用在前台，并让对手连接到同一 Wi‑Fi 或热点。", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE0F3FF), textAlign = TextAlign.Center)
            OutlinedButton(onClick = onBack) { Text("取消并返回") }
        }
    }
}

@Composable
private fun ColumnScope.RoomBrowser(
    state: LanUiState,
    onCreateRoom: () -> Unit,
    onRefresh: () -> Unit,
    onJoinRoom: (DiscoveredRoom) -> Unit,
    onBack: () -> Unit
) {
    Button(
        onClick = onCreateRoom,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA319))
    ) {
        Text("创建局域网房间", style = MaterialTheme.typography.titleMedium)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("搜索到的房间", style = MaterialTheme.typography.titleMedium, color = Color.White)
        OutlinedButton(onClick = onRefresh) { Text("刷新") }
    }
    Card(
        modifier = Modifier.fillMaxWidth().weight(1f),
        colors = CardDefaults.cardColors(containerColor = LanPanel.copy(alpha = 0.82f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (state.rooms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text("暂未发现房间\n请确认两台设备在同一局域网。", color = Color(0xFFB9DDF6), textAlign = TextAlign.Center)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.rooms.forEach { room ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D4D97)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(room.serviceName, modifier = Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { onJoinRoom(room) }) { Text("加入") }
                        }
                    }
                }
            }
        }
    }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回单人模式") }
}

@Composable
fun OpponentPanel(snapshot: OpponentSnapshot?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = LanPanel.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(13.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("对手", style = MaterialTheme.typography.labelLarge, color = Color.White)
            Text("OPPONENT", style = MaterialTheme.typography.labelSmall, color = Color(0xFFA7D8FC))
            if (snapshot == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("等待状态", color = Color(0xFFB8DDF8), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RemoteMetric("分", snapshot.score.toString())
                    RemoteMetric("行", snapshot.lines.toString())
                }
                OpponentBoard(snapshot, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun RemoteMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9BCFF5))
        Text(value, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun OpponentBoard(snapshot: OpponentSnapshot, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.border(1.dp, LanStroke, RoundedCornerShape(5.dp))) {
        val cell = size.width / TetrisGame.COLUMNS
        drawRect(Color(0xFF061E49))
        for (row in 0..TetrisGame.ROWS) {
            drawLine(Color(0xFF275B96), Offset(0f, row * cell), Offset(size.width, row * cell), 0.6f)
        }
        for (column in 0..TetrisGame.COLUMNS) {
            drawLine(Color(0xFF275B96), Offset(column * cell, 0f), Offset(column * cell, size.height), 0.6f)
        }
        snapshot.board.forEachIndexed { row, cells ->
            cells.forEachIndexed { column, value ->
                if (value != 0) drawRemoteBlock(cellColor(value), column * cell, row * cell, cell)
            }
        }
        snapshot.ghostPiece?.let { piece ->
            val shape = PieceLibrary.blocks(TetrominoType.entries[piece.typeOrdinal], piece.rotation)
            for ((x, y) in shape) {
                val row = piece.row + y
                val column = piece.column + x
                if (row in 0 until TetrisGame.ROWS && column in 0 until TetrisGame.COLUMNS) {
                    drawRemoteOutline(column * cell, row * cell, cell)
                }
            }
        }
        snapshot.activePiece?.let { piece ->
            val shape = PieceLibrary.blocks(TetrominoType.entries[piece.typeOrdinal], piece.rotation)
            val color = Color(TetrominoType.entries[piece.typeOrdinal].color)
            for ((x, y) in shape) {
                val row = piece.row + y
                val column = piece.column + x
                if (row in 0 until TetrisGame.ROWS && column in 0 until TetrisGame.COLUMNS) {
                    drawRemoteBlock(color, column * cell, row * cell, cell)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRemoteBlock(color: Color, left: Float, top: Float, side: Float) {
    drawRoundRect(color = color, topLeft = Offset(left + side * 0.06f, top + side * 0.06f), size = androidx.compose.ui.geometry.Size(side * 0.88f, side * 0.88f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.15f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRemoteOutline(left: Float, top: Float, side: Float) {
    drawRoundRect(color = LanGhost, topLeft = Offset(left + side * 0.18f, top + side * 0.18f), size = androidx.compose.ui.geometry.Size(side * 0.64f, side * 0.64f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.14f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = (side * 0.08f).coerceAtLeast(1f)))
}

private fun cellColor(value: Int): Color = when (value) {
    TetrisGame.GARBAGE_CELL -> GarbageColor
    in 1..TetrominoType.entries.size -> Color(TetrominoType.entries[value - 1].color)
    else -> Color.Transparent
}
