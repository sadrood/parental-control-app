package com.example.parentalcontrol.network

import android.util.Log
import com.example.parentalcontrol.network.model.SocketEvent
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
        doConnect()
    }

    private fun doConnect() {
        if (isDestoyed) return

        if (socket?.connected() == true) {
            Log.d(TAG, "已经连接")
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

            Log.d(TAG, "开始连接: $currentServerUrl")
        } catch (e: URISyntaxException) {
            Log.e(TAG, "URL格式错误: ${e.message}")
            _events.tryEmit(SocketEvent.ConnectionError(e.message ?: "URL格式错误"))
        }
    }

    private fun handleConnectionError(error: String) {
        isConnected = false
        _events.tryEmit(SocketEvent.ConnectionError(error))

        if (isDestoyed) return

        reconnectCount++
        if (reconnectCount <= MAX_RECONNECT_ATTEMPTS) {
            Log.d(TAG, "第 $reconnectCount/$MAX_RECONNECT_ATTEMPTS 次重连，${RECONNECT_DELAY_MS}ms后重试")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                doConnect()
            }, RECONNECT_DELAY_MS)
        } else {
            Log.e(TAG, "超过最大重连次数 ($MAX_RECONNECT_ATTEMPTS)，停止重连")
            _events.tryEmit(SocketEvent.ConnectionError("连接失败，已重试 $MAX_RECONNECT_ATTEMPTS 次"))
        }
    }

    /**
     * 绑定用户身份
     */
    fun bindUser(userId: String, token: String) {
        currentUserId = userId
        currentToken = token

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
        Log.d(TAG, "发送绑定请求: $userId")
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        isDestoyed = true
        socket?.disconnect()
        socket?.off()
        socket = null
        isConnected = false
        currentServerUrl = null
        currentUserId = null
        currentToken = null
        reconnectCount = 0
        Log.d(TAG, "已断开连接")
    }

    private fun setupListeners() {
        socket?.apply {
            on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "连接成功")
                isConnected = true
                _events.tryEmit(SocketEvent.Connected)
                currentUserId?.let { uid ->
                    currentToken?.let { tk -> doBindUser(uid, tk) }
                }
            }

            on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "未知错误"
                Log.e(TAG, "连接失败: $error")
                handleConnectionError(error)
            }

            on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "连接断开")
                isConnected = false
                _events.tryEmit(SocketEvent.Disconnected)
            }

            on("bind_success") { args ->
                val data = args.firstOrNull() as? JSONObject
                val userId = data?.optString("userId", "") ?: ""
                Log.d(TAG, "绑定成功: $userId")
                _events.tryEmit(SocketEvent.BindSuccess(userId))
            }

            on("bind_error") { args ->
                val data = args.firstOrNull() as? JSONObject
                val error = data?.optString("error", "绑定失败") ?: "绑定失败"
                Log.e(TAG, "绑定失败: $error")
                _events.tryEmit(SocketEvent.BindError(error))
            }

            // 业务事件
            on("rule_update") { args ->
                val data = args.firstOrNull()?.toString() ?: "{}"
                Log.d(TAG, "收到规则更新: $data")
                _events.tryEmit(SocketEvent.RuleUpdate(data))
            }

            on("rule_delete") { args ->
                val data = args.firstOrNull()?.toString() ?: "{}"
                Log.d(TAG, "收到规则删除: $data")
                _events.tryEmit(SocketEvent.RuleDelete(data))
            }

            on("time_rule_update") { args ->
                val data = args.firstOrNull()?.toString() ?: "{}"
                Log.d(TAG, "收到时间规则更新: $data")
                _events.tryEmit(SocketEvent.TimeRuleUpdate(data))
            }

            on("lock_screen") { args ->
                val data = args.firstOrNull()?.toString() ?: "{}"
                Log.d(TAG, "收到锁屏指令: $data")
                _events.tryEmit(SocketEvent.LockScreen(data))
            }

            on("unlock_screen") {
                Log.d(TAG, "收到解锁指令")
                _events.tryEmit(SocketEvent.UnlockScreen)
            }

            on("usage_update") { args ->
                val data = args.firstOrNull()?.toString() ?: "{}"
                Log.d(TAG, "收到使用记录更新: $data")
                _events.tryEmit(SocketEvent.UsageUpdate(data))
            }

            on("security_event") { args ->
                val data = args.firstOrNull()?.toString() ?: "{}"
                Log.d(TAG, "收到安全事件: $data")
                _events.tryEmit(SocketEvent.SecurityEvent(data))
            }

            on("sync_request") {
                Log.d(TAG, "收到同步请求")
                _events.tryEmit(SocketEvent.SyncRequest)
            }
        }
    }

    fun isConnectionActive(): Boolean = socket?.connected() == true
}
