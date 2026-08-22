package com.blockspace.tetris.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.blockspace.tetris.game.FallingPiece
import com.blockspace.tetris.game.TetrisGame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

private const val SERVICE_TYPE = "_blockspace._tcp."
private const val PROTOCOL_VERSION = "2"
private const val MAX_MESSAGE_LENGTH = 1_024
private const val MAX_PLAYERS = 4
private const val HOST_PLAYER_ID = "HOST"
private const val HEARTBEAT_INTERVAL_MILLIS = 4_000L
private const val HEARTBEAT_TIMEOUT_MILLIS = 12_000L

enum class LanStatus {
    IDLE,
    DISCOVERING,
    HOSTING,
    JOINING,
    LOBBY,
    PLAYING,
    FINISHED,
    ERROR
}

data class DiscoveredRoom(
    val serviceName: String
)

data class RemotePieceSnapshot(
    val typeOrdinal: Int,
    val rotation: Int,
    val row: Int,
    val column: Int
)

data class OpponentSnapshot(
    val board: Array<IntArray>,
    val score: Int,
    val lines: Int,
    val level: Int,
    val activePiece: RemotePieceSnapshot?,
    val ghostPiece: RemotePieceSnapshot?
)

data class LanPlayer(
    val id: String,
    val name: String,
    val isHost: Boolean,
    val isAlive: Boolean = true,
    val snapshot: OpponentSnapshot? = null
)

data class LanUiState(
    val status: LanStatus = LanStatus.IDLE,
    val roomName: String? = null,
    val isHost: Boolean = false,
    val localPlayerId: String? = null,
    val rooms: List<DiscoveredRoom> = emptyList(),
    val players: List<LanPlayer> = emptyList(),
    val pendingGarbageLines: Int = 0,
    val winnerName: String? = null,
    val message: String = "选择“创建房间”或搜索同一 Wi‑Fi 下的房间。"
) {
    val remotePlayers: List<LanPlayer>
        get() = players.filter { it.id != localPlayerId }
}

/**
 * 同一局域网内最多四人的俄罗斯方块比赛管理器。
 *
 * 创建房间的设备为可信房主中继：它仅接收已通过 Android NSD/mDNS 解析的连接，维护房间名单，
 * 并转发长度受限的实时状态和攻击消息。没有账号、互联网、云端、IP 段扫描、文件传输或远程执行。
 */
class LanMultiplayerManager(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()
    private val roomsByName = ConcurrentHashMap<String, NsdServiceInfo>()
    private val secureRandom = SecureRandom()
    private val roomPlayers = LinkedHashMap<String, LanPlayer>()
    private val hostPeers = ConcurrentHashMap<String, HostPeer>()
    private val pendingHostPeers = ConcurrentHashMap.newKeySet<HostPeer>()

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(LanUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<LanUiState> = _uiState

    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var serverSocket: ServerSocket? = null
    private var clientConnection: ClientConnection? = null
    private var isHostSession = false
    private var localPlayerId: String? = null
    private var matchStarted = false
    private var localFinished = false
    private var lastPublishedLockRevision = -1
    private var winnerName: String? = null

    private class HostPeer(val socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        val outputLock = Any()
        @Volatile var id: String? = null
        @Volatile var token: String? = null
        @Volatile var lastReceivedAtMillis: Long = System.currentTimeMillis()
    }

    private class ClientConnection(val socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        val outputLock = Any()
        @Volatile var token: String? = null
        @Volatile var lastReceivedAtMillis: Long = System.currentTimeMillis()
    }

    fun startDiscovery() {
        synchronized(stateLock) {
            if (discoveryListener != null) return
            acquireMulticastLock()
            roomsByName.clear()
            publishState(status = LanStatus.DISCOVERING, rooms = emptyList(), message = "正在搜索多人局域网房间…")
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) = Unit

                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceType != SERVICE_TYPE || service.serviceName == _uiState.value.roomName) return
                    roomsByName[service.serviceName] = service
                    publishState(rooms = roomsByName.keys.sorted().map(::DiscoveredRoom))
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    roomsByName.remove(service.serviceName)
                    publishState(rooms = roomsByName.keys.sorted().map(::DiscoveredRoom))
                }

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    stopDiscoveryInternal()
                    publishState(status = LanStatus.ERROR, message = "无法搜索房间（错误 $errorCode）。请确认设备在同一 Wi‑Fi 或热点。")
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    stopDiscoveryInternal()
                }
            }
            discoveryListener = listener
            try {
                nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (error: Exception) {
                discoveryListener = null
                releaseMulticastLock()
                publishState(status = LanStatus.ERROR, message = "无法启动局域网搜索：${error.message ?: "未知错误"}")
            }
        }
    }

    fun stopDiscovery() = synchronized(stateLock) {
        stopDiscoveryInternal()
        if (_uiState.value.status == LanStatus.DISCOVERING) {
            publishState(status = LanStatus.IDLE, rooms = emptyList(), message = "已停止搜索。")
        }
    }

    fun hostRoom(displayName: String) {
        synchronized(stateLock) {
            resetSessionInternal(notifyPeers = false)
            val roomName = buildRoomName(displayName)
            try {
                val listeningSocket = ServerSocket(0).apply { reuseAddress = true }
                serverSocket = listeningSocket
                isHostSession = true
                localPlayerId = HOST_PLAYER_ID
                matchStarted = false
                localFinished = false
                winnerName = null
                lastPublishedLockRevision = -1
                roomPlayers[HOST_PLAYER_ID] = LanPlayer(HOST_PLAYER_ID, "房主", isHost = true)
                acquireMulticastLock()
                publishState(
                    status = LanStatus.HOSTING,
                    roomName = roomName,
                    isHost = true,
                    localPlayerId = HOST_PLAYER_ID,
                    rooms = emptyList(),
                    message = "房间已创建（1/$MAX_PLAYERS），等待玩家加入…"
                )
                registerRoom(roomName, listeningSocket.localPort)
                scope.launch { acceptLoop(listeningSocket) }
            } catch (error: Exception) {
                publishState(status = LanStatus.ERROR, message = "无法创建房间：${error.message ?: "未知错误"}")
                resetSessionInternal(notifyPeers = false)
            }
        }
    }

    fun joinRoom(room: DiscoveredRoom) {
        val service = roomsByName[room.serviceName]
        if (service == null) {
            publishState(status = LanStatus.ERROR, message = "该房间已离开局域网，请重新搜索。")
            return
        }
        synchronized(stateLock) {
            resetSessionInternal(notifyPeers = false)
            isHostSession = false
            localPlayerId = "G-${randomToken().take(8)}"
            publishState(
                status = LanStatus.JOINING,
                roomName = room.serviceName,
                isHost = false,
                localPlayerId = localPlayerId,
                message = "正在连接 ${room.serviceName}…"
            )
            try {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        publishState(status = LanStatus.ERROR, message = "无法解析房间地址（错误 $errorCode）。")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host
                        val port = serviceInfo.port
                        if (host == null || port !in 1..65535) {
                            publishState(status = LanStatus.ERROR, message = "房间未提供有效连接地址。")
                            return
                        }
                        scope.launch {
                            try {
                                val connected = Socket(host, port).apply { tcpNoDelay = true; soTimeout = 0 }
                                synchronized(stateLock) {
                                    if (_uiState.value.status == LanStatus.JOINING) attachClient(connected)
                                    else connected.close()
                                }
                            } catch (error: Exception) {
                                publishState(status = LanStatus.ERROR, message = "连接失败：${error.message ?: "请确认房主仍在大厅中"}")
                            }
                        }
                    }
                })
            } catch (error: Exception) {
                publishState(status = LanStatus.ERROR, message = "无法发起连接：${error.message ?: "未知错误"}")
            }
        }
    }

    /** 房主在至少两名玩家进入大厅后调用，统一开始比赛。 */
    fun startMatch() {
        synchronized(stateLock) {
            if (!isHostSession || _uiState.value.status !in setOf(LanStatus.HOSTING, LanStatus.LOBBY)) return
            if (roomPlayers.size < 2) {
                publishState(message = "至少需要两名玩家才能开始比赛。")
                return
            }
            matchStarted = true
            localFinished = false
            winnerName = null
            lastPublishedLockRevision = -1
            roomPlayers.replaceAll { _, player -> player.copy(isAlive = true, snapshot = null) }
            unregisterRoomInternal()
            closeServerInternal()
            publishState(status = LanStatus.PLAYING, message = "比赛开始，共 ${roomPlayers.size} 名玩家。")
            broadcastRoster()
            broadcastToPeers("START|${randomToken().take(8)}")
        }
    }

    /** 在游戏规则状态真正变化后调用，同步自己的棋盘并向所有其他存活玩家发送攻击。 */
    fun publishGame(game: TetrisGame) {
        val snapshot = snapshotFrom(game)
        val attack = if (game.lockRevision != lastPublishedLockRevision) {
            lastPublishedLockRevision = game.lockRevision
            attackForLines(game.lastClearedLines)
        } else {
            0
        }
        val localId = localPlayerId ?: return
        val shouldFinish = game.isGameOver
        synchronized(stateLock) {
            if (_uiState.value.status != LanStatus.PLAYING || localFinished) return
            updatePlayerSnapshot(localId, snapshot)
            if (isHostSession) {
                broadcastToPeers("STATE|$localId|${serializeSnapshot(snapshot)}")
                if (attack > 0) routeAttack(localId, attack)
            } else {
                sendToClient("STATE|${clientConnection?.token ?: return}|${serializeSnapshot(snapshot)}")
                if (attack > 0) sendToClient("ATTACK|${clientConnection?.token ?: return}|$attack")
            }
        }
        if (shouldFinish) markLocalEliminated()
    }

    fun consumePendingGarbage(): Int = synchronized(stateLock) {
        if (_uiState.value.status != LanStatus.PLAYING) return 0
        val pending = _uiState.value.pendingGarbageLines
        if (pending > 0) publishState(pendingGarbageLines = 0)
        pending
    }

    fun close() = synchronized(stateLock) {
        resetSessionInternal(notifyPeers = true)
        publishState(
            status = LanStatus.IDLE,
            roomName = null,
            isHost = false,
            localPlayerId = null,
            rooms = emptyList(),
            players = emptyList(),
            pendingGarbageLines = 0,
            winnerName = null,
            message = "已退出局域网对战。"
        )
    }

    fun release() {
        close()
        scope.cancel()
    }

    private suspend fun acceptLoop(listeningSocket: ServerSocket) {
        try {
            while (scope.isActive && serverSocket === listeningSocket && !matchStarted) {
                val accepted = listeningSocket.accept().apply { tcpNoDelay = true; soTimeout = 0 }
                synchronized(stateLock) {
                    if (serverSocket !== listeningSocket || matchStarted || roomPlayers.size >= MAX_PLAYERS) {
                        rejectAndClose(accepted, if (roomPlayers.size >= MAX_PLAYERS) "房间已满（最多 $MAX_PLAYERS 人）。" else "比赛已开始。")
                    } else {
                        val peer = HostPeer(accepted)
                        pendingHostPeers.add(peer)
                        scope.launch { hostReadLoop(peer) }
                        scope.launch { hostHeartbeatLoop(peer) }
                    }
                }
            }
        } catch (_: Exception) {
            synchronized(stateLock) {
                if (serverSocket === listeningSocket && !matchStarted) {
                    publishState(status = LanStatus.ERROR, message = "房间监听已停止。")
                }
            }
        }
    }

    private fun attachClient(connected: Socket) {
        closeClientInternal(notifyHost = false)
        val connection = ClientConnection(connected)
        clientConnection = connection
        lastPublishedLockRevision = -1
        stopDiscoveryInternal()
        releaseMulticastLock()
        val playerId = localPlayerId ?: return
        sendToClient("HELLO|$PROTOCOL_VERSION|$playerId|PLAYER")
        scope.launch { clientReadLoop(connection) }
        scope.launch { clientHeartbeatLoop(connection) }
    }

    private suspend fun hostReadLoop(peer: HostPeer) {
        try {
            while (scope.isActive && !peer.socket.isClosed) {
                val line = peer.reader.readLine() ?: break
                if (line.length > MAX_MESSAGE_LENGTH) break
                peer.lastReceivedAtMillis = System.currentTimeMillis()
                handleHostLine(peer, line)
            }
        } catch (_: Exception) {
            // 在 finally 中统一释放该玩家。
        } finally {
            synchronized(stateLock) { removeHostPeer(peer, "玩家已离开房间。") }
        }
    }

    private suspend fun clientReadLoop(connection: ClientConnection) {
        try {
            while (scope.isActive && clientConnection === connection) {
                val line = connection.reader.readLine() ?: break
                if (line.length > MAX_MESSAGE_LENGTH) break
                connection.lastReceivedAtMillis = System.currentTimeMillis()
                handleClientLine(connection, line)
            }
        } catch (_: Exception) {
            // 在 finally 中统一切换状态。
        } finally {
            synchronized(stateLock) {
                if (clientConnection === connection) {
                    closeClientInternal(notifyHost = false)
                    publishState(status = LanStatus.ERROR, message = "与房主的局域网连接已断开。")
                }
            }
        }
    }

    private suspend fun hostHeartbeatLoop(peer: HostPeer) {
        while (scope.isActive && !peer.socket.isClosed) {
            delay(HEARTBEAT_INTERVAL_MILLIS)
            if (System.currentTimeMillis() - peer.lastReceivedAtMillis > HEARTBEAT_TIMEOUT_MILLIS) {
                try { peer.socket.close() } catch (_: Exception) { }
                break
            }
            peer.token?.let { sendToPeer(peer, "PING|$it") }
        }
    }

    private suspend fun clientHeartbeatLoop(connection: ClientConnection) {
        while (scope.isActive && clientConnection === connection && !connection.socket.isClosed) {
            delay(HEARTBEAT_INTERVAL_MILLIS)
            if (System.currentTimeMillis() - connection.lastReceivedAtMillis > HEARTBEAT_TIMEOUT_MILLIS) {
                try { connection.socket.close() } catch (_: Exception) { }
                break
            }
            connection.token?.let { sendToClient("PING|$it") }
        }
    }

    private fun handleHostLine(peer: HostPeer, line: String) {
        val parts = line.split('|', limit = 4)
        when (parts.firstOrNull()) {
            "HELLO" -> {
                if (parts.size != 4 || parts[1] != PROTOCOL_VERSION || peer.id != null) {
                    rejectPeer(peer, "协议不兼容。")
                    return
                }
                val id = sanitizeIdentifier(parts[2])
                val name = sanitizeName(parts[3])
                synchronized(stateLock) {
                    if (id.isBlank() || matchStarted || roomPlayers.size >= MAX_PLAYERS || id == HOST_PLAYER_ID || hostPeers.containsKey(id)) {
                        rejectPeer(peer, if (roomPlayers.size >= MAX_PLAYERS) "房间已满（最多 $MAX_PLAYERS 人）。" else "该房间不能再加入。")
                        return
                    }
                    peer.id = id
                    peer.token = randomToken()
                    pendingHostPeers.remove(peer)
                    hostPeers[id] = peer
                    roomPlayers[id] = LanPlayer(id, name, isHost = false)
                    sendToPeer(peer, "WELCOME|${peer.token}|$id")
                    publishState(status = LanStatus.LOBBY, message = "已有 ${roomPlayers.size}/$MAX_PLAYERS 名玩家，可由房主开始比赛。")
                    broadcastRoster()
                }
            }
            "STATE" -> {
                if (!validateHostToken(peer, parts, 3)) return
                parseSnapshot(parts[2])?.let { snapshot ->
                    synchronized(stateLock) {
                        val id = peer.id ?: return@synchronized
                        if (_uiState.value.status == LanStatus.PLAYING) {
                            updatePlayerSnapshot(id, snapshot)
                            broadcastToPeers("STATE|$id|${serializeSnapshot(snapshot)}")
                        }
                    }
                }
            }
            "ATTACK" -> {
                if (!validateHostToken(peer, parts, 3)) return
                val lines = parts[2].toIntOrNull()?.coerceIn(0, 4) ?: 0
                if (lines > 0) synchronized(stateLock) { routeAttack(peer.id ?: return, lines) }
            }
            "FINISH" -> {
                if (!validateHostToken(peer, parts, 2)) return
                synchronized(stateLock) { eliminatePlayer(peer.id ?: return) }
            }
            "PING" -> if (validateHostToken(peer, parts, 2)) peer.token?.let { sendToPeer(peer, "PONG|$it") }
            "PONG" -> Unit
            "BYE" -> try { peer.socket.close() } catch (_: Exception) { }
        }
    }

    private fun handleClientLine(connection: ClientConnection, line: String) {
        val parts = line.split('|', limit = 4)
        when (parts.firstOrNull()) {
            "WELCOME" -> {
                if (parts.size == 3 && parts[1].length in 8..40 && parts[2] == localPlayerId) {
                    connection.token = parts[1]
                    publishState(status = LanStatus.LOBBY, message = "已加入房间，等待房主开始比赛。")
                }
            }
            "ROSTER" -> if (parts.size == 2) {
                parseRoster(parts[1])?.let { players ->
                    synchronized(stateLock) {
                        roomPlayers.clear()
                        players.forEach { roomPlayers[it.id] = it }
                        publishState(
                            status = if (matchStarted) LanStatus.PLAYING else LanStatus.LOBBY,
                            message = if (matchStarted) "比赛进行中。" else "房间已有 ${players.size}/$MAX_PLAYERS 名玩家，等待房主开始。"
                        )
                    }
                }
            }
            "START" -> if (parts.size == 2 && connection.token != null) {
                synchronized(stateLock) {
                    matchStarted = true
                    localFinished = false
                    winnerName = null
                    lastPublishedLockRevision = -1
                    publishState(status = LanStatus.PLAYING, winnerName = null, message = "比赛开始，共 ${roomPlayers.size} 名玩家。")
                }
            }
            "STATE" -> if (parts.size == 3) {
                parseSnapshot(parts[2])?.let { snapshot ->
                    synchronized(stateLock) {
                        if (_uiState.value.status == LanStatus.PLAYING) updatePlayerSnapshot(parts[1], snapshot)
                    }
                }
            }
            "GARBAGE" -> if (parts.size == 3) {
                val lines = parts[2].toIntOrNull()?.coerceIn(0, 4) ?: 0
                if (lines > 0) synchronized(stateLock) {
                    publishState(pendingGarbageLines = (_uiState.value.pendingGarbageLines + lines).coerceAtMost(TetrisGame.ROWS))
                }
            }
            "ELIM" -> if (parts.size == 2) synchronized(stateLock) { markEliminatedInState(parts[1]) }
            "RESULT" -> if (parts.size == 2) synchronized(stateLock) {
                val winner = roomPlayers[parts[1]]?.name ?: "未命名玩家"
                winnerName = winner
                publishState(status = LanStatus.FINISHED, winnerName = winner, message = "比赛结束，胜者：$winner")
            }
            "REJECT" -> {
                val reason = parts.getOrNull(1) ?: "房主拒绝了连接。"
                synchronized(stateLock) {
                    closeClientInternal(notifyHost = false)
                    publishState(status = LanStatus.ERROR, message = reason)
                }
            }
            "PING" -> connection.token?.let { sendToClient("PONG|$it") }
            "PONG" -> Unit
            "BYE" -> try { connection.socket.close() } catch (_: Exception) { }
        }
    }

    private fun markLocalEliminated() = synchronized(stateLock) {
        if (localFinished || _uiState.value.status != LanStatus.PLAYING) return@synchronized
        localFinished = true
        val id = localPlayerId ?: return@synchronized
        if (isHostSession) {
            eliminatePlayer(id)
        } else {
            clientConnection?.token?.let { sendToClient("FINISH|$it") }
        }
    }

    private fun markEliminatedInState(playerId: String) {
        val player = roomPlayers[playerId] ?: return
        roomPlayers[playerId] = player.copy(isAlive = false)
        publishState()
    }

    private fun routeAttack(sourceId: String, lines: Int) {
        if (_uiState.value.status != LanStatus.PLAYING) return
        roomPlayers.values.filter { it.id != sourceId && it.isAlive }.forEach { target ->
            if (target.id == HOST_PLAYER_ID) {
                publishState(pendingGarbageLines = (_uiState.value.pendingGarbageLines + lines).coerceAtMost(TetrisGame.ROWS))
            } else {
                hostPeers[target.id]?.let { sendToPeer(it, "GARBAGE|$sourceId|$lines") }
            }
        }
    }

    private fun eliminatePlayer(playerId: String) {
        val player = roomPlayers[playerId] ?: return
        if (!player.isAlive) return
        roomPlayers[playerId] = player.copy(isAlive = false)
        broadcastToPeers("ELIM|$playerId")
        publishState(message = "${player.name} 已淘汰。")
        val survivors = roomPlayers.values.filter { it.isAlive }
        if (survivors.size <= 1) {
            val winner = survivors.firstOrNull()?.name ?: "无人"
            winnerName = winner
            publishState(status = LanStatus.FINISHED, winnerName = winner, message = "比赛结束，胜者：$winner")
            broadcastToPeers("RESULT|${survivors.firstOrNull()?.id ?: "NONE"}")
        } else {
            broadcastRoster()
        }
    }

    private fun removeHostPeer(peer: HostPeer, reason: String) {
        pendingHostPeers.remove(peer)
        val id = peer.id
        try { peer.socket.close() } catch (_: Exception) { }
        if (id == null) return
        hostPeers.remove(id, peer)
        if (matchStarted) {
            eliminatePlayer(id)
        } else {
            roomPlayers.remove(id)
            publishState(status = LanStatus.LOBBY, message = reason)
            broadcastRoster()
        }
    }

    private fun rejectPeer(peer: HostPeer, reason: String) {
        sendToPeer(peer, "REJECT|$reason")
        try { peer.socket.close() } catch (_: Exception) { }
    }

    private fun rejectAndClose(socket: Socket, reason: String) {
        try {
            BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)).use { writer ->
                writer.write("REJECT|$reason")
                writer.newLine()
                writer.flush()
            }
        } catch (_: Exception) { }
        try { socket.close() } catch (_: Exception) { }
    }

    private fun validateHostToken(peer: HostPeer, parts: List<String>, expectedSize: Int): Boolean {
        return parts.size == expectedSize && peer.token != null && parts[1] == peer.token && peer.id != null
    }

    private fun updatePlayerSnapshot(playerId: String, snapshot: OpponentSnapshot) {
        val current = roomPlayers[playerId] ?: return
        roomPlayers[playerId] = current.copy(snapshot = snapshot)
        publishState()
    }

    private fun broadcastRoster() {
        val roster = serializeRoster(roomPlayers.values)
        broadcastToPeers("ROSTER|$roster")
        publishState()
    }

    private fun broadcastToPeers(message: String) {
        hostPeers.values.forEach { peer -> sendToPeer(peer, message) }
    }

    private fun sendToPeer(peer: HostPeer, message: String) {
        if (message.length > MAX_MESSAGE_LENGTH) return
        scope.launch {
            synchronized(peer.outputLock) {
                try {
                    peer.writer.write(message)
                    peer.writer.newLine()
                    peer.writer.flush()
                } catch (_: Exception) {
                    try { peer.socket.close() } catch (_: Exception) { }
                }
            }
        }
    }

    private fun sendToClient(message: String) {
        val connection = clientConnection ?: return
        if (message.length > MAX_MESSAGE_LENGTH) return
        scope.launch {
            synchronized(connection.outputLock) {
                try {
                    connection.writer.write(message)
                    connection.writer.newLine()
                    connection.writer.flush()
                } catch (_: Exception) {
                    try { connection.socket.close() } catch (_: Exception) { }
                }
            }
        }
    }

    private fun snapshotFrom(game: TetrisGame): OpponentSnapshot = OpponentSnapshot(
        board = game.board(),
        score = game.score,
        lines = game.lines,
        level = game.level,
        activePiece = game.activePiece.toRemotePiece(),
        ghostPiece = game.ghostPiece().toRemotePiece()
    )

    private fun serializeSnapshot(snapshot: OpponentSnapshot): String {
        val active = snapshot.activePiece.toWireFields()
        val ghost = snapshot.ghostPiece.toWireFields()
        val board = buildString(TetrisGame.ROWS * TetrisGame.COLUMNS) {
            snapshot.board.forEach { row -> row.forEach { append(it.coerceIn(0, TetrisGame.GARBAGE_CELL)) } }
        }
        return listOf(snapshot.score, snapshot.lines, snapshot.level, active, ghost, board).joinToString(",")
    }

    private fun FallingPiece?.toRemotePiece(): RemotePieceSnapshot? = this?.let {
        RemotePieceSnapshot(it.type.ordinal, it.rotation, it.row, it.column)
    }

    private fun RemotePieceSnapshot?.toWireFields(): String = if (this == null) "-1,-1,-1,-1" else {
        "$typeOrdinal,$rotation,$row,$column"
    }

    private fun parseSnapshot(payload: String): OpponentSnapshot? {
        val fields = payload.split(',', limit = 12)
        if (fields.size != 12) return null
        val score = fields[0].toIntOrNull() ?: return null
        val lines = fields[1].toIntOrNull() ?: return null
        val level = fields[2].toIntOrNull() ?: return null
        val active = parsePiece(fields, 3)
        val ghost = parsePiece(fields, 7)
        val boardText = fields[11]
        if (boardText.length != TetrisGame.ROWS * TetrisGame.COLUMNS || boardText.any { it !in '0'..'8' }) return null
        val board = Array(TetrisGame.ROWS) { row ->
            IntArray(TetrisGame.COLUMNS) { column -> boardText[row * TetrisGame.COLUMNS + column].digitToInt() }
        }
        return OpponentSnapshot(
            board = board,
            score = score.coerceAtLeast(0),
            lines = lines.coerceAtLeast(0),
            level = level.coerceIn(1, 15),
            activePiece = active,
            ghostPiece = ghost
        )
    }

    private fun parsePiece(fields: List<String>, start: Int): RemotePieceSnapshot? {
        val type = fields[start].toIntOrNull() ?: return null
        val rotation = fields[start + 1].toIntOrNull() ?: return null
        val row = fields[start + 2].toIntOrNull() ?: return null
        val column = fields[start + 3].toIntOrNull() ?: return null
        if (type == -1) return null
        if (type !in 0..6 || rotation !in 0..3 || row !in -4..TetrisGame.ROWS || column !in -4..TetrisGame.COLUMNS) return null
        return RemotePieceSnapshot(type, rotation, row, column)
    }

    private fun serializeRoster(players: Collection<LanPlayer>): String = players.joinToString(";") { player ->
        "${player.id},${player.name},${if (player.isHost) 1 else 0},${if (player.isAlive) 1 else 0}"
    }

    private fun parseRoster(payload: String): List<LanPlayer>? {
        if (payload.isBlank()) return emptyList()
        val parsed = payload.split(';').mapNotNull { entry ->
            val fields = entry.split(',')
            if (fields.size != 4) return@mapNotNull null
            val id = sanitizeIdentifier(fields[0])
            val name = sanitizeName(fields[1])
            val isHost = fields[2] == "1"
            val isAlive = fields[3] == "1"
            if (id.isBlank() || name.isBlank()) null else LanPlayer(id, name, isHost, isAlive)
        }
        return if (parsed.isNotEmpty() && parsed.size <= MAX_PLAYERS) parsed else null
    }

    private fun attackForLines(lines: Int): Int = when (lines) {
        2 -> 1
        3 -> 2
        4 -> 4
        else -> 0
    }

    private fun registerRoom(roomName: String, port: Int) {
        unregisterRoomInternal()
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                publishState(roomName = serviceInfo.serviceName, message = "房间已创建（1/$MAX_PLAYERS），等待玩家加入…")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                publishState(status = LanStatus.ERROR, message = "房间广播失败（错误 $errorCode）。")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        val info = NsdServiceInfo().apply {
            serviceName = roomName
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        try {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (error: Exception) {
            publishState(status = LanStatus.ERROR, message = "无法广播房间：${error.message ?: "未知错误"}")
        }
    }

    private fun unregisterRoomInternal() {
        val listener = registrationListener ?: return
        registrationListener = null
        try { nsdManager.unregisterService(listener) } catch (_: Exception) { }
    }

    private fun stopDiscoveryInternal() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        try { nsdManager.stopServiceDiscovery(listener) } catch (_: Exception) { }
        roomsByName.clear()
        if (clientConnection == null && serverSocket == null) releaseMulticastLock()
    }

    private fun closeClientInternal(notifyHost: Boolean) {
        val connection = clientConnection ?: return
        if (notifyHost) connection.token?.let { sendToClient("BYE|$it") }
        try { connection.reader.close() } catch (_: Exception) { }
        try { connection.writer.close() } catch (_: Exception) { }
        try { connection.socket.close() } catch (_: Exception) { }
        clientConnection = null
    }

    private fun closeServerInternal() {
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
    }

    private fun resetSessionInternal(notifyPeers: Boolean) {
        if (notifyPeers) {
            clientConnection?.token?.let { sendToClient("BYE|$it") }
            hostPeers.values.forEach { peer -> peer.token?.let { sendToPeer(peer, "BYE|$it") } }
        }
        closeClientInternal(notifyHost = false)
        hostPeers.values.forEach { peer -> try { peer.socket.close() } catch (_: Exception) { } }
        pendingHostPeers.forEach { peer -> try { peer.socket.close() } catch (_: Exception) { } }
        hostPeers.clear()
        pendingHostPeers.clear()
        stopDiscoveryInternal()
        unregisterRoomInternal()
        closeServerInternal()
        roomsByName.clear()
        releaseMulticastLock()
        roomPlayers.clear()
        isHostSession = false
        localPlayerId = null
        matchStarted = false
        localFinished = false
        winnerName = null
        lastPublishedLockRevision = -1
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        multicastLock = wifiManager.createMulticastLock("blockspace-nsd").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        } catch (_: Exception) { }
        multicastLock = null
    }

    private fun publishState(
        status: LanStatus = _uiState.value.status,
        roomName: String? = _uiState.value.roomName,
        isHost: Boolean = isHostSession,
        localPlayerId: String? = this.localPlayerId,
        rooms: List<DiscoveredRoom> = _uiState.value.rooms,
        players: List<LanPlayer> = roomPlayers.values.toList(),
        pendingGarbageLines: Int = _uiState.value.pendingGarbageLines,
        winnerName: String? = this.winnerName,
        message: String = _uiState.value.message
    ) {
        _uiState.value = LanUiState(
            status = status,
            roomName = roomName,
            isHost = isHost,
            localPlayerId = localPlayerId,
            rooms = rooms,
            players = players,
            pendingGarbageLines = pendingGarbageLines,
            winnerName = winnerName,
            message = message
        )
    }

    private fun buildRoomName(displayName: String): String {
        val normalized = sanitizeName(displayName).take(16).ifBlank { "ROOM" }
        return "BlockSpace-$normalized-${secureRandom.nextInt(10_000).toString().padStart(4, '0')}"
    }

    private fun sanitizeIdentifier(value: String): String = value.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(24)
    private fun sanitizeName(value: String): String = value.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(16).ifBlank { "PLAYER" }

    private fun randomToken(): String = buildString(16) {
        repeat(16) { append("0123456789abcdef"[secureRandom.nextInt(16)]) }
    }
}
