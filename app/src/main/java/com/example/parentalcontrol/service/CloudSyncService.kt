package com.example.parentalcontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.parentalcontrol.MainActivity
import com.example.parentalcontrol.R
import com.example.parentalcontrol.data.db.AppDatabase
import com.example.parentalcontrol.network.ApiClient
import com.example.parentalcontrol.network.AuthManager
import com.example.parentalcontrol.network.WebSocketManager
import com.example.parentalcontrol.network.model.SocketEvent
import com.example.parentalcontrol.network.model.UsageRecordItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 云同步服务
 * 功能：
 * 1. 定时上传使用记录到服务器
 * 2. 监听 WebSocket 推送（锁屏、规则更新等）
 * 3. 同步服务器规则到本地数据库
 */
class CloudSyncService : Service() {

    companion object {
        private const val TAG = "CloudSyncService"
        private const val CHANNEL_ID = "cloud_sync_channel"
        private const val NOTIFICATION_ID = 1002
        private const val SYNC_INTERVAL = 60_000L // 每分钟同步一次
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var authManager: AuthManager
    private lateinit var webSocketManager: WebSocketManager

    override fun onCreate() {
        super.onCreate()
        authManager = AuthManager.getInstance(this)
        webSocketManager = WebSocketManager.getInstance()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // 启动定时同步
        startPeriodicSync()

        // 监听 WebSocket 事件
        observeSocketEvents()

        Log.d(TAG, "云同步服务已启动")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "云同步服务已销毁")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "云端同步",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "与服务器同步数据"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("家长控制中心")
            .setContentText("云端同步运行中")
            .setSmallIcon(R.drawable.ic_launcher_simple)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * 定时同步使用记录到服务器
     */
    private fun startPeriodicSync() {
        serviceScope.launch {
            while (true) {
                try {
                    if (authManager.isLoggedIn() && authManager.isPaired.value) {
                        uploadUsageRecords()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "同步出错: ${e.message}")
                }
                delay(SYNC_INTERVAL)
            }
        }
    }

    /**
     * 上传本地使用记录到服务器
     */
    private suspend fun uploadUsageRecords() {
        try {
            val db = AppDatabase.getDatabase(this@CloudSyncService)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val records = db.usageRecordDao().getRecordsByDate(today).first()

            if (records.isEmpty()) return

            val items = records.map { record ->
                UsageRecordItem(
                    packageName = record.packageName,
                    date = record.date,
                    durationMinutes = record.usageTimeMs / 60_000,
                    sessionCount = 1
                )
            }

            val apiService = ApiClient.getApiService()
            val response = apiService.uploadUsageRecords(
                com.example.parentalcontrol.network.model.UploadUsageRequest(items)
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "上传 ${items.size} 条使用记录成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "上传使用记录失败: ${e.message}")
        }
    }

    /**
     * 监听 WebSocket 推送事件
     */
    private fun observeSocketEvents() {
        serviceScope.launch {
            webSocketManager.events
                .catch { e -> Log.e(TAG, "WebSocket事件流异常: ${e.message}") }
                .collect { event ->
                    when (event) {
                        is SocketEvent.Connected -> {
                            Log.d(TAG, "WebSocket 已连接")
                            // 重新绑定用户
                            authManager.getCurrentUserId()?.let { uid ->
                                ApiClient.authToken?.let { tk ->
                                    webSocketManager.bindUser(uid, tk)
                                }
                            }
                        }

                        is SocketEvent.LockScreen -> {
                            Log.w(TAG, "收到锁屏指令")
                            performLockScreen()
                        }

                        is SocketEvent.UnlockScreen -> {
                            Log.d(TAG, "收到解锁指令，解除全屏锁屏")
                            ScreenLockOverlayService.hideBlock(this@CloudSyncService)
                        }

                        is SocketEvent.RuleUpdate -> {
                            Log.d(TAG, "收到规则更新，同步到本地")
                            syncRulesFromServer()
                        }

                        is SocketEvent.UsageUpdate -> {
                            Log.d(TAG, "收到使用记录更新通知")
                            // 家长端收到后可以刷新UI
                        }

                        is SocketEvent.SecurityEvent -> {
                            Log.w(TAG, "收到安全事件: ${event.data}")
                        }

                        is SocketEvent.SyncRequest -> {
                            Log.d(TAG, "收到同步请求")
                            uploadUsageRecords()
                        }

                        else -> {}
                    }
                }
        }
    }

    /**
     * 从服务器同步规则到本地数据库
     */
    private suspend fun syncRulesFromServer() {
        try {
            val apiService = ApiClient.getApiService()
            val response = apiService.getAppRules()

            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body() ?: return
                val rules = body.rules ?: return
                val db = AppDatabase.getDatabase(this@CloudSyncService)

                for (rule in rules) {
                    val localRule = com.example.parentalcontrol.data.entity.AppRule(
                        packageName = rule.packageName,
                        appName = rule.packageName, // 服务器没有appName，用packageName代替
                        category = rule.category,
                        isBlocked = rule.isBlocked,
                        dailyTimeLimit = rule.dailyLimitMinutes,
                        isWhitelisted = false,
                        isStudyApp = false
                    )
                    db.appRuleDao().insertRule(localRule)
                }
                Log.d(TAG, "同步 ${rules.size} 条规则到本地")
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步规则失败: ${e.message}")
        }
    }

    /**
     * 执行锁屏
     */
    private fun performLockScreen() {
        try {
            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val admin = android.content.ComponentName(this, com.example.parentalcontrol.receiver.AdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) {
                dpm.lockNow()
                Log.d(TAG, "锁屏成功")
            } else {
                Log.w(TAG, "没有设备管理器权限，无法锁屏")
            }
        } catch (e: Exception) {
            Log.e(TAG, "锁屏失败: ${e.message}")
        }
    }
}
