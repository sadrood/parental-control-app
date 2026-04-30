package com.example.parentalcontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.parentalcontrol.MainActivity
import com.example.parentalcontrol.R
import com.example.parentalcontrol.data.db.AppDatabase
import com.example.parentalcontrol.data.entity.UsageRecord
import com.example.parentalcontrol.util.SettingsManager
import com.example.parentalcontrol.util.TimeChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settingsManager: SettingsManager

    companion object {
        private const val CHANNEL_ID = "parental_control_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "MonitoringService"
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
        Log.d(TAG, "监控服务已启动")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "家长控制监控",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "持续监控应用使用情况"
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
            .setContentText("正在保护您的孩子")
            .setSmallIcon(R.drawable.ic_launcher_simple)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (true) {
                try {
                    collectUsageStats()
                } catch (e: Exception) {
                    Log.e(TAG, "收集使用统计出错: ${e.message}")
                }
                delay(60_000) // 每分钟收集一次
            }
        }
    }

    private fun collectUsageStats() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val db = AppDatabase.getDatabase(this)

        stats?.forEach { usageStat ->
            if (usageStat.totalTimeInForeground > 0) {
                val pm = packageManager
                try {
                    val appInfo = pm.getApplicationInfo(usageStat.packageName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()

                    val record = UsageRecord(
                        packageName = usageStat.packageName,
                        appName = appName,
                        date = today,
                        usageTimeMs = usageStat.totalTimeInForeground,
                        category = categorizeApp(usageStat.packageName)
                    )
                    db.usageRecordDao().insertRecord(record)
                } catch (e: Exception) {
                    // Ignore system apps
                }
            }
        }

        Log.d(TAG, "使用统计数据已更新")
    }

    private fun categorizeApp(packageName: String): String {
        return when {
            packageName.contains("game") || packageName.contains("tencent") -> "游戏"
            packageName.contains("edu") || packageName.contains("study") || packageName.contains("homework") -> "学习"
            packageName.contains("wechat") || packageName.contains("qq") || packageName.contains("tiktok") || packageName.contains("douyin") -> "社交"
            packageName.contains("video") || packageName.contains("player") -> "娱乐"
            else -> "其他"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "监控服务已销毁")
    }
}
