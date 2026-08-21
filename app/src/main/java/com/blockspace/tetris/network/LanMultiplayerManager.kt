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
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

private const val SERVICE_TYPE = "_blockspace._tcp."
private const val PROTOCOL_VERSION = "1"
private const val MAX_MESSAGE_LENGTH = 1_024
private const val HEARTBEAT_INTERVAL_MILLIS = 4_000L
private const val HEARTBEAT_TIMEOUT_MILLIS = 12_000L

enum class LanStatus {
    IDLE,
    DISCOVERING,
    HOSTING,
    JOINING,
    CONNECTED,
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

data class LanUiState(
    val status: LanStatus = LanStatus.IDLE,
    val roomName: String? = null,
    val isHost: Boolean = false,
    val rooms: List<DiscoveredRoom> = emptyList(),
    val opponent: OpponentSnapshot? = null,
    val pendingGarbageLines: Int = 0,
    val message: String = "选择“创建房间”或搜索同一 Wi‑Fi 下的房间。"
)

/**
 * 局域网双人会话管理器。
 *
 * 房间通过 Android NSD/mDNS 发现，连接只会建立到解析出的服务地址。消息为长度受限的
 * UTF-8 单行协议，不包含账户、文件、互联网地址扫描或远程执行能力。
 */
class LanMultiplayerManager(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()
    private val outputLock = Any()
    private val roomsByName = ConcurrentHashMap<String, NsdServiceInfo>()
    private val secureRandom = SecureRandom()

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(LanUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<LanUiState> = _uiState

    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var sessionToken: String? = null
    private var lastReceivedAtMillis: Long = 0L
    private var lastPublishedLockRevision = -1

    fun startDiscovery() {
        synchronized(stateLock) {
            if (discoveryListener != null) return
            acquireMulticastLock()
            roomsByName.clear()
            publishState(status = LanStatus.DISCOVERING, rooms = emptyList(), message = "正在搜索局域网房间…")
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
                    publishState(status = LanStatus.ERROR, message = "无法搜索房间（错误 $errorCode）。请确认两台设备连接同一 Wi‑Fi 或热点。")
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

    fun stopDiscovery() {
        synchronized(stateLock) {
            stopDiscoveryInternal()
            if (_uiState.value.status == LanStatus.DISCOVERING) {
                publishState(status = LanStatus.IDLE, rooms = emptyList(), message = "已停止搜索。")
            }
        }
    }

    fun hostRoom(displayName: String) {
        synchronized(stateLock) {
            closeConnectionInternal(notifyPeer = false)
            unregisterRoomInternal()
            closeServerInternal()
            stopDiscoveryInternal()
            val roomName = buildRoomName(displayName)
            try {
                val listeningSocket = ServerSocket(0)
                listeningSocket.reuseAddress = true
                serverSocket = listeningSocket
                sessionToken = null
                lastPublishedLockRevision = -1
                acquireMulticastLock()
                publishState(
                    status = LanStatus.HOSTING,
                    roomName = roomName,
                    isHost = true,
                    rooms = emptyList(),
                    opponent = null,
                    pendingGarbageLines = 0,
                    message = "房间已创建，等待对手加入…"
                )
                registerRoom(roomName, listeningSocket.localPort)
                scope.launch {
                    try {
                        val accepted = listeningSocket.accept()
                        synchronized(stateLock) {
                            if (serverSocket !== listeningSocket || socket != null) {
                                accepted.close()
                            } else {
                                attachConnection(accepted, isHost = true)
                            }
                        }
                    } catch (_: Exception) {
                        if (serverSocket === listeningSocket && socket == null) {
                            publishState(status = LanStatus.ERROR, message = "房间监听已停止。")
                        }
                    }
                }
            } catch (error: Exception) {
                publishState(status = LanStatus.ERROR, message = "无法创建房间：${error.message ?: "未知错误"}")
                releaseMulticastLock()
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
            closeConnectionInternal(notifyPeer = false)
            unregisterRoomInternal()
            closeServerInternal()
            publishState(status = LanStatus.JOINING, roomName = room.serviceName, isHost = false, message = "正在连接 ${room.serviceName}…")
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
                                val connected = Socket(host, port)
                                synchronized(stateLock) {
                                    if (_uiState.value.status == LanStatus.JOINING) attachConnection(connected, isHost = false)
                                    else connected.close()
                                }
                            } catch (error: Exception) {
                                publishState(status = LanStatus.ERROR, message = "连接失败：${error.message ?: "请确认对手仍在房间中"}")
                            }
                        }
                    }
                })
            } catch (error: Exception) {
                publishState(status = LanStatus.ERROR, message = "无法发起连接：${error.message ?: "未知错误"}")
            }
        }
    }

    /** 在规则状态真正变化后调用；发送当前可见棋盘与必要的对手观察信息。 */
    fun publishGame(game: TetrisGame) {
        val token = sessionToken ?: return
        if (_uiState.value.status != LanStatus.CONNECTED) return
        sendLine("STATE|$token|${serializeGame(game)}")
        if (game.lockRevision != lastPublishedLockRevision) {
            lastPublishedLockRevision = game.lockRevision
            val attack = attackForLines(game.lastClearedLines)
            if (attack > 0) sendLine("ATTACK|$token|$attack")
        }
    }

    fun consumePendingGarbage(): Int = synchronized(stateLock) {
        val pending = _uiState.value.pendingGarbageLines
        if (pending > 0) publishState(pendingGarbageLines = 0)
        pending
    }

    fun close() {
        synchronized(stateLock) {
            closeConnectionInternal(notifyPeer = true)
            stopDiscoveryInternal()
            unregisterRoomInternal()
            closeServerInternal()
            roomsByName.clear()
            releaseMulticastLock()
            sessionToken = null
            lastPublishedLockRevision = -1
            publishState(
                status = LanStatus.IDLE,
                roomName = null,
                isHost = false,
                rooms = emptyList(),
                opponent = null,
                pendingGarbageLines = 0,
                message = "已退出局域网对战。"
            )
        }
    }

    fun release() {
        close()
        scope.cancel()
    }

    private fun attachConnection(connected: Socket, isHost: Boolean) {
        closeConnectionInternal(notifyPeer = false)
        socket = connected
        connected.tcpNoDelay = true
        connected.soTimeout = 0
        reader = BufferedReader(InputStreamReader(connected.getInputStream(), Charsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(connected.getOutputStream(), Charsets.UTF_8))
        lastReceivedAtMillis = System.currentTimeMillis()
        lastPublishedLockRevision = -1
        if (isHost) sessionToken = randomToken()
        // 已有确定的 TCP 会话后，不再需要持续发现或保留可加入的广播房间。
        stopDiscoveryInternal()
        if (isHost) {
            unregisterRoomInternal()
            closeServerInternal()
        }
        releaseMulticastLock()
        publishState(
            status = if (isHost) LanStatus.HOSTING else LanStatus.JOINING,
            isHost = isHost,
            opponent = null,
            pendingGarbageLines = 0,
            message = if (isHost) "对手已加入，等待双方确认开始…" else "已连接房主，正在确认对局…"
        )
        sendLine("HELLO|$PROTOCOL_VERSION|${if (isHost) "HOST" else "GUEST"}")
        scope.launch { readLoop(connected) }
        scope.launch { heartbeatLoop(connected) }
    }

    private suspend fun readLoop(connected: Socket) {
        try {
            while (scope.isActive && socket === connected) {
                val line = reader?.readLine() ?: break
                if (line.length > MAX_MESSAGE_LENGTH) break
                lastReceivedAtMillis = System.currentTimeMillis()
                handleIncoming(line)
            }
        } catch (_: Exception) {
            // 统一在 finally 中将会话切换为断线状态。
        } finally {
            synchronized(stateLock) {
                if (socket === connected) {
                    closeConnectionInternal(notifyPeer = false)
                    unregisterRoomInternal()
                    closeServerInternal()
                    releaseMulticastLock()
                    publishState(status = LanStatus.ERROR, opponent = null, message = "与对手的局域网连接已断开。")
                }
            }
        }
    }

    private suspend fun heartbeatLoop(connected: Socket) {
        while (scope.isActive && socket === connected) {
            delay(HEARTBEAT_INTERVAL_MILLIS)
            if (System.currentTimeMillis() - lastReceivedAtMillis > HEARTBEAT_TIMEOUT_MILLIS) {
                try { connected.close() } catch (_: Exception) { }
                break
            }
            sessionToken?.let { sendLine("PING|$it") }
        }
    }

    private fun handleIncoming(line: String) {
        val parts = line.split('|', limit = 3)
        if (parts.isEmpty()) return
        when (parts[0]) {
            "HELLO" -> {
                if (parts.size == 3 && parts[1] == PROTOCOL_VERSION) {
                    sessionToken?.let { sendLine("WELCOME|$it") }
                }
            }
            "WELCOME" -> {
                if (parts.size == 2 && parts[1].length in 8..40) {
                    sessionToken = parts[1]
                    sendLine("READY|${parts[1]}")
                    publishState(status = LanStatus.JOINING, message = "已连接房主，等待对局开始…")
                }
            }
            "READY" -> {
                if (parts.size == 2 && parts[1] == sessionToken && _uiState.value.isHost) {
                    publishState(status = LanStatus.CONNECTED, message = "双方已准备好，开始对战。")
                    sendLine("START|${parts[1]}")
                }
            }
            "START" -> {
                if (parts.size == 2 && parts[1] == sessionToken) {
                    publishState(status = LanStatus.CONNECTED, message = "双方已准备好，开始对战。")
                }
            }
            "STATE" -> {
                if (parts.size == 3 && parts[1] == sessionToken) {
                    parseSnapshot(parts[2])?.let { snapshot -> publishState(opponent = snapshot) }
                }
            }
            "ATTACK" -> {
                if (parts.size == 3 && parts[1] == sessionToken) {
                    val lines = parts[2].toIntOrNull()?.coerceIn(0, 4) ?: 0
                    if (lines > 0) publishState(pendingGarbageLines = _uiState.value.pendingGarbageLines + lines)
                }
            }
            "PING" -> if (parts.size == 2 && parts[1] == sessionToken) sendLine("PONG|${parts[1]}")
            "PONG" -> Unit
            "BYE" -> socket?.close()
        }
    }

    private fun serializeGame(game: TetrisGame): String {
        val active = game.activePiece.toWireFields()
        val ghost = game.ghostPiece().toWireFields()
        val board = buildString(TetrisGame.ROWS * TetrisGame.COLUMNS) {
            game.board().forEach { row -> row.forEach { append(it.coerceIn(0, TetrisGame.GARBAGE_CELL)) } }
        }
        return listOf(
            game.score,
            game.lines,
            game.level,
            active,
            ghost,
            board
        ).joinToString(",")
    }

    private fun FallingPiece?.toWireFields(): String = if (this == null) "-1,-1,-1,-1" else {
        "${type.ordinal},$rotation,$row,$column"
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
                publishState(roomName = serviceInfo.serviceName, message = "房间已创建，等待对手加入…")
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
        if (socket == null && serverSocket == null) releaseMulticastLock()
    }

    private fun closeConnectionInternal(notifyPeer: Boolean) {
        if (notifyPeer) sessionToken?.let { sendLine("BYE|$it") }
        try { reader?.close() } catch (_: Exception) { }
        try { writer?.close() } catch (_: Exception) { }
        try { socket?.close() } catch (_: Exception) { }
        reader = null
        writer = null
        socket = null
        sessionToken = null
    }

    private fun closeServerInternal() {
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
    }

    private fun sendLine(message: String) {
        if (message.length > MAX_MESSAGE_LENGTH) return
        scope.launch {
            synchronized(outputLock) {
                try {
                    writer?.apply {
                        write(message)
                        newLine()
                        flush()
                    }
                } catch (_: Exception) {
                    try { socket?.close() } catch (_: Exception) { }
                }
            }
        }
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
        isHost: Boolean = _uiState.value.isHost,
        rooms: List<DiscoveredRoom> = _uiState.value.rooms,
        opponent: OpponentSnapshot? = _uiState.value.opponent,
        pendingGarbageLines: Int = _uiState.value.pendingGarbageLines,
        message: String = _uiState.value.message
    ) {
        _uiState.value = LanUiState(status, roomName, isHost, rooms, opponent, pendingGarbageLines, message)
    }

    private fun buildRoomName(displayName: String): String {
        val normalized = displayName.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(16).ifBlank { "ROOM" }
        return "BlockSpace-$normalized-${secureRandom.nextInt(10_000).toString().padStart(4, '0')}"
    }

    private fun randomToken(): String = buildString(16) {
        repeat(16) { append("0123456789abcdef"[secureRandom.nextInt(16)]) }
    }
}
