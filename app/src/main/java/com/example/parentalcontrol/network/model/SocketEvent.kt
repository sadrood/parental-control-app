package com.example.parentalcontrol.network.model

/**
 * WebSocket 事件类型
 */
sealed class SocketEvent {

    // ==================== 连接状态事件 ====================

    object Connected : SocketEvent()
    object Disconnected : SocketEvent()
    data class ConnectionError(val message: String) : SocketEvent()
    data class BindSuccess(val userId: String) : SocketEvent()
    data class BindError(val message: String) : SocketEvent()

    // ==================== 业务事件 ====================

    data class RuleUpdate(val data: String) : SocketEvent()
    data class RuleDelete(val data: String) : SocketEvent()
    data class TimeRuleUpdate(val data: String) : SocketEvent()
    data class LockScreen(val data: String) : SocketEvent()
    object UnlockScreen : SocketEvent()
    data class UsageUpdate(val data: String) : SocketEvent()
    data class SecurityEvent(val data: String) : SocketEvent()
    object SyncRequest : SocketEvent()
    data class StudyModeCommand(val data: String) : SocketEvent()
}
