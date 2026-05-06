package com.example.parentalcontrol.network

import android.util.Log
import com.example.parentalcontrol.network.model.SocketEvent
import com.example.parentalcontrol.util.ChildLogUtil
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * WebSocket 管理器
 * 负责与服务器建立实时连接，接收推送消息
 */
class WebSocketManager private constructor() {

    companion object {
        private const val TAG = "WebSocketManager"
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_DELAY_MS = 5000L
        private const val HEARTBEAT_INTERVAL_MS = 15000L

        @Volatile
        private var instance: WebSocketManager? = null

        fun getInstance(): WebSocketManager {
            return instance ?: synchronized(this) {
                instance ?: WebSocketManager().also { instance = it }
            }
        }
    }

    private var socket: Socket? = null

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    private var isConnected = false
    private var currentServerUrl: String? = null
    private var currentUserId: String? = null
    private var currentToken: String? = null
    private var reconnectCount = 0
    private var isDestoyed = false
    private var socketId: String? = null
    private var heartbeatRunnable: Runnable? = null
    private var lastPongTime: Long = 0L

    /**
     * 更新服务器地址（断开后下次连接生效）
     */
    fun updateServerUrl(url: String) {
        currentServerUrl = url
    }

    /**
     * 连接到服务器
     */
    fun connect(serverUrl: String, userId: String? = null) {
        currentServerUrl = if (userId != null) {
            "${serverUrl}?userId=$userId"
        } else {
            serverUrl
        }
        currentUserId = userId
        reconnectCount = 0
        isDestoyed = false
        ChildLogUtil.i(TAG, "WebSocket 连接初始化 | url=$currentServerUrl | userId=$userId")
        doConnect()
    }

    private fun doConnect() {
        if (isDestoyed) {
            ChildLogUtil.w(TAG, "连接已销毁，跳过连接")
            return
        }

        if (socket?.connected() == true) {
            ChildLogUtil.d(TAG, "已经连接，跳过 | socketId=$socketId")
            return
        }

        try {
            val options = IO.Options().apply {
                reconnection = false
                timeout = 10000
            }

            socket = IO.socket(currentServerUrl, options)
            setupListeners()
            socket?.connect()

            ChildLogUtil.i(TAG, "开始连接: $currentServerUrl | 超时=${options.timeout}ms")
        } catch (e: URISyntaxException) {
            ChildLogUtil.e(TAG, "URL格式错误: ${e.message}", e)
            _events.tryEmit(SocketEvent.ConnectionError(e.message ?: "URL格式错误"))
        }
    }

    private fun handleConnectionError(error: String) {
        isConnected = false
        socketId = null
        stopHeartbeat()
        _events.tryEmit(SocketEvent.ConnectionError(error))
        ChildLogUtil.w(TAG, "连接失败 | error=$error | reconnectCount=$reconnectCount/$MAX_RECONNECT_ATTEMPTS")

        if (isDestoyed) return

        reconnectCount++
        if (reconnectCount <= MAX_RECONNECT_ATTEMPTS) {
            val delay = RECONNECT_DELAY_MS * (1 shl (reconnectCount - 1))
            ChildLogUtil.i(TAG, "第 $reconnectCount/$MAX_RECONNECT_ATTEMPTS 次重连，${delay}ms后重试（指数退避）")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                doConnect()
            }, delay)
        } else {
            ChildLogUtil.e(TAG, "超过最大重连次数 ($MAX_RECONNECT_ATTEMPTS)，停止重连")
            _events.tryEmit(SocketEvent.ConnectionError("连接失败，已重试 $MAX_RECONNECT_ATTEMPTS 次"))
        }
    }

    // ==================== 心跳机制 ====================

    private fun startHeartbeat() {
        stopHeartbeat()
        lastPongTime = System.currentTimeMillis()
        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (isDestoyed || socket?.connected() != true) return
                try {
                    val pingTime = System.currentTimeMillis()
                    ChildLogUtil.d(TAG, "发送心跳 ping | socketId=$socketId")
                    socket?.emit("ping")
                    // 检查上次 pong 是否超时
                    val elapsed = pingTime - lastPongTime
                    if (elapsed > HEARTBEAT_INTERVAL_MS * 2) {
                        ChildLogUtil.w(TAG, "心跳超时告警 | 上次pong距今${elapsed}ms | socketId=$socketId | 可能已离线")
                        _events.tryEmit(SocketEvent.ConnectionError("心跳超时 (${elapsed}ms)"))
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, HEARTBEAT_INTERVAL_MS)
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "发送心跳异常: ${e.message}", e)
                }
            }
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(heartbeatRunnable!!, HEARTBEAT_INTERVAL_MS)
        ChildLogUtil.i(TAG, "心跳机制已启动 | interval=${HEARTBEAT_INTERVAL_MS}ms")
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let {
            android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it)
        }
        heartbeatRunnable = null
        ChildLogUtil.d(TAG, "心跳机制已停止")
    }

    /**
     * 绑定用户身份
     */
    fun bindUser(userId: String, token: String) {
        currentUserId = userId
        currentToken = token

        ChildLogUtil.i(TAG, "绑定用户请求 | userId=$userId | socketConnected=${socket?.connected()}")
        if (socket?.connected() == true) {
            doBindUser(userId, token)
        }
    }

    private fun doBindUser(userId: String, token: String) {
        val data = JSONObject().apply {
            put("userId", userId)
            put("token", token)
        }
        socket?.emit("bind", data)
        ChildLogUtil.i(TAG, "发送绑定请求 | userId=$userId | socketId=$socketId")
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        ChildLogUtil.i(TAG, "主动断开连接 | socketId=$socketId | wasConnected=$isConnected")
        isDestoyed = true
        stopHeartbeat()
        socket?.disconnect()
        socket?.off()
        socket = null
        isConnected = false
        socketId = null
        currentServerUrl = null
        currentUserId = null
        currentToken = null
        reconnectCount = 0
        ChildLogUtil.i(TAG, "已断开连接，资源已清理")
    }

    private fun setupListeners() {
        socket?.apply {
            on(Socket.EVENT_CONNECT) {
                try {
                    socketId = socket?.id()
                    isConnected = true
                    reconnectCount = 0
                    ChildLogUtil.i(TAG, "WebSocket 连接成功 | socketId=$socketId | serverUrl=$currentServerUrl")
                    startHeartbeat()
                    _events.tryEmit(SocketEvent.Connected)
                    currentUserId?.let { uid ->
                        currentToken?.let { tk -> doBindUser(uid, tk) }
                    }
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "处理连接成功事件异常", e)
                }
            }

            on(Socket.EVENT_CONNECT_ERROR) { args ->
                try {
                    val error = args.firstOrNull()?.toString() ?: "未知错误"
                    ChildLogUtil.w(TAG, "连接失败事件 | error=$error | socketId=$socketId")
                    handleConnectionError(error)
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "处理连接失败事件异常", e)
                }
            }

            on(Socket.EVENT_DISCONNECT) { args ->
                try {
                    val reason = args.firstOrNull()?.toString() ?: "未知原因"
                    isConnected = false
                    stopHeartbeat()
                    ChildLogUtil.w(TAG, "WebSocket 连接断开 | reason=$reason | socketId=$socketId")
                    _events.tryEmit(SocketEvent.Disconnected)
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "处理断开事件异常", e)
                }
            }

            on("bind_success") { args ->
                try {
                    val rawData = args.firstOrNull()
                    ChildLogUtil.i(TAG, "收到 bind_success | rawData=$rawData | socketId=$socketId")
                    val data = rawData as? JSONObject
                    val userId = data?.optString("userId", "") ?: ""
                    ChildLogUtil.i(TAG, "绑定成功 | userId=$userId")
                    _events.tryEmit(SocketEvent.BindSuccess(userId))
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "处理绑定成功事件异常", e)
                }
            }

            on("bind_error") { args ->
                try {
                    val rawData = args.firstOrNull()
                    ChildLogUtil.w(TAG, "收到 bind_error | rawData=$rawData | socketId=$socketId")
                    val data = rawData as? JSONObject
                    val error = data?.optString("error", "绑定失败") ?: "绑定失败"
                    ChildLogUtil.e(TAG, "绑定失败 | error=$error")
                    _events.tryEmit(SocketEvent.BindError(error))
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "处理绑定失败事件异常", e)
                }
            }

            on("rule_update") { args ->
                try {
                    val data = args.firstOrNull()?.toString() ?: "{}"
                    ChildLogUtil.i(TAG, "收到指令：rule_update | rawData=$data")
                    _events.tryEmit(SocketEvent.RuleUpdate(data))
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "解析规则更新失败", e)
                }
            }

            on("rule_delete") { args ->
                try {
                    val data = args.firstOrNull()?.toString() ?: "{}"
                    ChildLogUtil.i(TAG, "收到指令：rule_delete | rawData=$data")
                    _events.tryEmit(SocketEvent.RuleDelete(data))
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "解析规则删除失败", e)
                }
            }

            on("time_rule_update") { args ->
                try {
                    val data = args.firstOrNull()?.toString() ?: "{}"
                    ChildLogUtil.i(TAG, "收到指令：time_rule_update | rawData=$data")
                    _events.tryEmit(SocketEvent.TimeRuleUpdate(data))
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "解析时间规则更新失败", e)
                }
            }

            on("lock_screen") { args ->
                try {
                    val data = args.firstOrNull()?.toString() ?: "{}"
                    ChildLogUtil.i(TAG, "接收指令：[LOCK] 锁屏 | rawData=$data | socketId=$socketId")
                    _events.tryEmit(SocketEvent.LockScreen(data))
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "解析锁屏指令失败", e)
                }
            }

            on("unlock_screen") { args ->
                try {
                    val data = args.firstOrNull()?.toString() ?: "{}"
                    ChildLogUtil.i(TAG, "接收指令：[UNLOCK] 解锁 | rawData=$data | socketId=$socketId")
                    _events.tryEmit(SocketEvent.UnlockScreen)
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "处理解锁指令失败", e)
                }
            }

            on("study_mode") { args ->
                try {
                    val data = args.firstOrNull()?.toString() ?: "{}"
                    ChildLogUtil.i(TAG, "接收指令：[STUDY_MODE] 学习模式 | rawData=$data | socketId=$socketId")
                    _events.tryEmit(SocketEvent.StudyModeCommand(data))
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "解析学习模式指令失败", e)
                }
            }

            on("usage_update") { args ->
                try {
                    val data = args.firstOrNull()?.toString() ?: "{}"
                    ChildLogUtil.d(TAG, "收到使用记录更新 | rawData=$data")
                    _events.tryEmit(SocketEvent.UsageUpdate(data))
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "解析使用记录更新失败", e)
                }
            }

            on("security_event") { args ->
                try {
                    val data = args.firstOrNull()?.toString() ?: "{}"
                    ChildLogUtil.w(TAG, "收到安全事件 | rawData=$data")
                    _events.tryEmit(SocketEvent.SecurityEvent(data))
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "解析安全事件失败", e)
                }
            }

            on("sync_request") {
                try {
                    ChildLogUtil.d(TAG, "收到同步请求")
                    _events.tryEmit(SocketEvent.SyncRequest)
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "处理同步请求失败", e)
                }
            }

            on("pong") { args ->
                try {
                    lastPongTime = System.currentTimeMillis()
                    val data = args.firstOrNull()?.toString() ?: ""
                    ChildLogUtil.d(TAG, "收到心跳 pong | data=$data | rtt=${lastPongTime - (heartbeatRunnable?.let { lastPongTime - HEARTBEAT_INTERVAL_MS } ?: 0)}ms")
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "处理pong异常", e)
                }
            }
        }
    }

    fun isConnectionActive(): Boolean = socket?.connected() == true
}
