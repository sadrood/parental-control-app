package com.example.parentalcontrol.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.parentalcontrol.MainActivity
import com.example.parentalcontrol.R
import com.example.parentalcontrol.data.db.AppDatabase
import com.example.parentalcontrol.data.entity.UsageRecord
import com.example.parentalcontrol.data.repository.RepositoryResult
import com.example.parentalcontrol.network.ApiClient
import com.example.parentalcontrol.network.AuthManager
import com.example.parentalcontrol.network.SafeApiCaller
import com.example.parentalcontrol.network.WebSocketManager
import com.example.parentalcontrol.network.model.SocketEvent
import com.example.parentalcontrol.network.model.UploadUsageRequest
import com.example.parentalcontrol.network.model.UploadResponse
import com.example.parentalcontrol.util.ChildLogUtil
import com.example.parentalcontrol.util.LogUtil
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

class CloudSyncService : Service() {

    companion object {
        private const val TAG = "CloudSync"
        private const val CHANNEL_ID = "cloud_sync_channel"
        private const val NOTIFICATION_ID = 1002
        private const val SYNC_INTERVAL = 60_000L
        private const val RETRY_INTERVAL = 30_000L
        private const val PENDING_UPLOADS_KEY = "pending_usage_uploads"
        private const val PENDING_PREFS = "pending_uploads_prefs"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var authManager: AuthManager
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var pendingPrefs: SharedPreferences
    private val gson = Gson()

    override fun onCreate() {
        super.onCreate()
        authManager = AuthManager.getInstance(this)
        webSocketManager = WebSocketManager.getInstance()
        pendingPrefs = getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("云端同步运行中"))

        startPeriodicSync()
        observeSocketEvents()

        LogUtil.i(TAG, "云同步服务已启动")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        LogUtil.i(TAG, "云同步服务已销毁")
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

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("家长控制中心")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_simple)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun startPeriodicSync() {
        serviceScope.launch {
            while (true) {
                try {
                    if (authManager.isLoggedIn() && authManager.isPaired.value) {
                        uploadUsageRecords()
                        retryPendingUploads()
                    }
                } catch (e: Exception) {
                    LogUtil.e(TAG, "同步循环异常", e)
                }
                delay(SYNC_INTERVAL)
            }
        }
    }

    private suspend fun uploadUsageRecords() {
        try {
            val db = AppDatabase.getDatabase(this@CloudSyncService)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val records = db.usageRecordDao().getRecordsByDate(today).first()

            if (records.isEmpty()) return

            val items = records.map { record ->
                com.example.parentalcontrol.network.model.UsageRecordItem(
                    packageName = record.packageName,
                    date = record.date,
                    durationMinutes = if (record.usageTimeMs > 0) record.usageTimeMs / 60_000 else 1,
                    sessionCount = 1
                )
            }

            val request = UploadUsageRequest(items)
            val result = SafeApiCaller.call<UploadResponse>(TAG) {
                ApiClient.getApiService().uploadUsageRecords(request)
            }

            when (result) {
                is RepositoryResult.Success -> {
                    LogUtil.i(TAG, "上传 ${items.size} 条使用记录成功")
                }
                is RepositoryResult.Error -> {
                    LogUtil.w(TAG, "上传失败: ${result.message}")
                    savePendingUploads(items)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "上传使用记录异常", e)
        }
    }

    private fun savePendingUploads(items: List<com.example.parentalcontrol.network.model.UsageRecordItem>) {
        try {
            val existing = getPendingUploads().toMutableList()
            existing.addAll(items)
            val json = gson.toJson(existing)
            pendingPrefs.edit().putString(PENDING_UPLOADS_KEY, json).apply()
            LogUtil.d(TAG, "已缓存 ${items.size} 条待上传记录")
        } catch (e: Exception) {
            LogUtil.e(TAG, "缓存待上传记录失败", e)
        }
    }

    private fun getPendingUploads(): List<com.example.parentalcontrol.network.model.UsageRecordItem> {
        try {
            val json = pendingPrefs.getString(PENDING_UPLOADS_KEY, null) ?: return emptyList()
            val type = object : TypeToken<List<com.example.parentalcontrol.network.model.UsageRecordItem>>() {}.type
            return gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            LogUtil.e(TAG, "读取缓存记录失败", e)
            return emptyList()
        }
    }

    private fun clearPendingUploads() {
        pendingPrefs.edit().remove(PENDING_UPLOADS_KEY).apply()
    }

    private suspend fun retryPendingUploads() {
        val pending = getPendingUploads()
        if (pending.isEmpty()) return

        LogUtil.d(TAG, "尝试重试 ${pending.size} 条待上传记录")
        val request = UploadUsageRequest(pending)
        val result = SafeApiCaller.call<UploadResponse>(TAG) {
            ApiClient.getApiService().uploadUsageRecords(request)
        }

        when (result) {
            is RepositoryResult.Success -> {
                LogUtil.i(TAG, "重试上传 ${pending.size} 条记录成功")
                clearPendingUploads()
            }
            is RepositoryResult.Error -> {
                LogUtil.w(TAG, "重试上传失败: ${result.message}")
            }
        }
    }

    private fun observeSocketEvents() {
        serviceScope.launch {
            webSocketManager.events
                .catch { e -> LogUtil.e(TAG, "WebSocket事件流异常", e) }
                .collect { event ->
                    try {
                        handleSocketEvent(event)
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "处理WebSocket事件异常", e)
                    }
                }
        }
    }

    private suspend fun handleSocketEvent(event: SocketEvent) {
        when (event) {
            is SocketEvent.Connected -> {
                val role = authManager.getCurrentUserRole() ?: "unknown"
                if (role == "parent") {
                    com.example.parentalcontrol.util.ParentLogUtil.i(TAG, "[ONLINE] 设备上线 | role=$role | userId=${authManager.getCurrentUserId()}")
                } else {
                    ChildLogUtil.i(TAG, "[ONLINE] 设备上线 | role=$role | userId=${authManager.getCurrentUserId()}")
                }
                authManager.getCurrentUserId()?.let { uid ->
                    ApiClient.authToken?.let { tk ->
                        webSocketManager.bindUser(uid, tk)
                    }
                }
                notifyOnlineStatus(true)
                retryPendingUploads()
            }

            is SocketEvent.Disconnected -> {
                val role = authManager.getCurrentUserRole() ?: "unknown"
                if (role == "parent") {
                    com.example.parentalcontrol.util.ParentLogUtil.w(TAG, "[OFFLINE] 设备离线 | role=$role | userId=${authManager.getCurrentUserId()}")
                } else {
                    ChildLogUtil.w(TAG, "[OFFLINE] 设备离线 | role=$role | userId=${authManager.getCurrentUserId()}")
                }
                notifyOnlineStatus(false)
            }

            is SocketEvent.LockScreen -> {
                ChildLogUtil.i(TAG, "[LOCK] 锁屏指令处理开始 | rawData=${event.data}")
                try {
                    performLockScreen()
                    ChildLogUtil.i(TAG, "[LOCK] 锁屏指令执行完成")
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "[LOCK] 锁屏指令执行失败: ${e.message}", e)
                }
            }

            is SocketEvent.UnlockScreen -> {
                ChildLogUtil.i(TAG, "[UNLOCK] 解锁指令处理开始")
                try {
                    // 1. 隐藏霸屏覆盖
                    ScreenLockOverlayService.hideBlock(this@CloudSyncService)
                    ChildLogUtil.i(TAG, "[UNLOCK] 霸屏覆盖已隐藏")
                    // 2. 唤醒屏幕
                    performScreenWake()
                    // 3. 解锁设备
                    performDeviceUnlock()
                    // 4. 通知服务器解锁已执行
                    notifyUnlockExecuted()
                    ChildLogUtil.i(TAG, "[UNLOCK] 解锁全流程执行完成 | 霸屏隐藏+屏幕唤醒+设备解锁+服务器通知")
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "[UNLOCK] 解锁指令执行失败: ${e.message}", e)
                }
            }

            is SocketEvent.StudyModeCommand -> {
                ChildLogUtil.i(TAG, "[STUDY_MODE] 学习模式指令处理开始 | rawData=${event.data}")
                try {
                    val json = org.json.JSONObject(event.data)
                    val mode = json.optString("mode", "start")
                    ChildLogUtil.i(TAG, "[STUDY_MODE] 解析结果 | mode=$mode | childId=${json.optString("childId")} | parentId=${json.optString("parentId")}")
                    if (mode == "start") {
                        ChildLogUtil.i(TAG, "[STUDY_MODE] 启动霸屏（学习模式）...")
                        ScreenLockOverlayService.showBlock(this@CloudSyncService, "学习模式已开启，请专心学习")
                        ChildLogUtil.i(TAG, "[STUDY_MODE] 霸屏指令已发送到锁屏服务")
                    } else if (mode == "stop") {
                        ChildLogUtil.i(TAG, "[STUDY_MODE] 解除霸屏（退出学习模式）...")
                        ScreenLockOverlayService.hideBlock(this@CloudSyncService)
                        ChildLogUtil.i(TAG, "[STUDY_MODE] 解除霸屏指令已发送到锁屏服务")
                    }
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "[STUDY_MODE] 学习模式指令执行失败: ${e.message}", e)
                }
            }

            is SocketEvent.RuleUpdate -> {
                LogUtil.d(TAG, "收到规则更新")
                syncRulesFromServer()
            }

            is SocketEvent.UsageUpdate -> {
                LogUtil.d(TAG, "收到使用记录更新通知")
            }

            is SocketEvent.SecurityEvent -> {
                LogUtil.w(TAG, "收到安全事件: ${event.data}")
            }

            is SocketEvent.SyncRequest -> {
                LogUtil.d(TAG, "收到同步请求")
                uploadUsageRecords()
            }

            else -> {}
        }
    }

    private suspend fun syncRulesFromServer() {
        try {
            val result = SafeApiCaller.call<com.example.parentalcontrol.network.model.AppRulesResponse>(TAG) {
                ApiClient.getApiService().getAppRules()
            }

            when (result) {
                is RepositoryResult.Success -> {
                    val rules = result.data.rules ?: return
                    val db = AppDatabase.getDatabase(this@CloudSyncService)

                    for (rule in rules) {
                        val localRule = com.example.parentalcontrol.data.entity.AppRule(
                            packageName = rule.packageName,
                            appName = rule.packageName,
                            category = rule.category,
                            isBlocked = rule.isBlocked,
                            dailyTimeLimit = rule.dailyLimitMinutes,
                            isWhitelisted = false,
                            isStudyApp = false
                        )
                        db.appRuleDao().insertRule(localRule)
                    }
                    LogUtil.i(TAG, "同步 ${rules.size} 条规则到本地")
                }
                is RepositoryResult.Error -> {
                    LogUtil.w(TAG, "同步规则失败: ${result.message}")
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "同步规则异常", e)
        }
    }

    private fun performScreenWake() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ParentalControl:UnlockWake"
            )
            wakeLock.acquire(3000) // 3秒后自动释放
            ChildLogUtil.i(TAG, "[UNLOCK] 屏幕唤醒API调用成功 | wakeLock.acquire(3s)")
        } catch (e: Exception) {
            ChildLogUtil.e(TAG, "[UNLOCK] 屏幕唤醒失败: ${e.message}", e)
        }
    }

    private fun performDeviceUnlock() {
        try {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km?.isKeyguardLocked == true) {
                // 使用 DevicePolicyManager 解锁
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = android.content.ComponentName(this, com.example.parentalcontrol.receiver.AdminReceiver::class.java)
                if (dpm.isAdminActive(admin)) {
                    dpm.lockNow()
                    // 锁屏后需要先解锁keyguard才能解
                    ChildLogUtil.i(TAG, "[UNLOCK] 设备管理器keyguard解锁 | isAdminActive=${dpm.isAdminActive(admin)}")
                } else {
                    ChildLogUtil.w(TAG, "[UNLOCK] 设备管理器权限未激活，跳过系统解锁 | 仅执行霸屏解除")
                }
            } else {
                ChildLogUtil.d(TAG, "[UNLOCK] Keyguard已解锁，无需系统解锁")
            }
        } catch (e: Exception) {
            ChildLogUtil.e(TAG, "[UNLOCK] 系统解锁失败: ${e.message}", e)
        }
    }

    private fun notifyUnlockExecuted() {
        try {
            val userId = authManager.getCurrentUserId() ?: return
            ChildLogUtil.i(TAG, "[UNLOCK] 通知服务器解锁已执行 | userId=$userId")
        } catch (e: Exception) {
            ChildLogUtil.e(TAG, "[UNLOCK] 通知服务器失败: ${e.message}", e)
        }
    }

    private fun notifyOnlineStatus(online: Boolean) {
        try {
            val role = authManager.getCurrentUserRole() ?: return
            val userId = authManager.getCurrentUserId() ?: return
            if (role == "parent") {
                com.example.parentalcontrol.util.ParentLogUtil.i(TAG, "[STATUS] 上报在线状态 | online=$online | userId=$userId | role=$role")
            } else {
                ChildLogUtil.i(TAG, "[STATUS] 上报在线状态 | online=$online | userId=$userId | role=$role")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "上报在线状态失败: ${e.message}", e)
        }
    }

    private fun performLockScreen() {
        try {
            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val admin = android.content.ComponentName(this, com.example.parentalcontrol.receiver.AdminReceiver::class.java)
            val isAdminActive = dpm.isAdminActive(admin)
            ChildLogUtil.i(TAG, "[LOCK] 执行锁屏操作 | api=devicePolicyManager.lockNow() | isAdminActive=$isAdminActive")
            if (isAdminActive) {
                dpm.lockNow()
                ChildLogUtil.i(TAG, "[LOCK] 锁屏系统API调用成功 | 设备已锁定")
            } else {
                ChildLogUtil.w(TAG, "[LOCK] 锁屏失败：未获取到设备管理器权限 | adminComponent=${admin.className}")
            }
        } catch (e: SecurityException) {
            ChildLogUtil.e(TAG, "[LOCK] 锁屏失败：权限不足 | SecurityException=${e.message}", e)
        } catch (e: Exception) {
            ChildLogUtil.e(TAG, "[LOCK] 锁屏失败：${e.message}", e)
        }
    }
}
