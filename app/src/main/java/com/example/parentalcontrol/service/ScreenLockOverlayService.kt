package com.example.parentalcontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.parentalcontrol.MainActivity
import com.example.parentalcontrol.R
import com.example.parentalcontrol.util.SettingsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全屏锁屏覆盖服务
 * 借鉴 CST (Child Screen Time) 的 ScreenLockService
 * 
 * 核心防绕过机制：
 * 1. 全屏覆盖窗口，覆盖所有系统 UI
 * 2. 拦截所有硬件按键（返回/Home/最近任务）
 * 3. 使用 FLAG_SECURE 防止截图
 * 4. 沉浸式模式隐藏导航栏和状态栏
 * 5. 前台服务保活
 * 6. 屏幕状态监控（省电优化）
 */
class ScreenLockOverlayService : Service() {

    companion object {
        private const val TAG = "ScreenLockOverlay"
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "screen_lock_overlay"

        @Volatile
        private var isBlocking = false

        fun isBlockingActive(): Boolean = isBlocking

        /**
         * 显示锁屏覆盖
         */
        fun showBlock(context: Context, reason: String = "使用时间已到") {
            isBlocking = true
            val intent = Intent(context, ScreenLockOverlayService::class.java).apply {
                action = "SHOW_BLOCK"
                putExtra("reason", reason)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 隐藏锁屏覆盖
         */
        fun hideBlock(context: Context) {
            isBlocking = false
            val intent = Intent(context, ScreenLockOverlayService::class.java).apply {
                action = "HIDE_BLOCK"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var blockingView: View? = null
    private var isShowingOverlay = false
    private var powerManager: PowerManager? = null
    private var isScreenOn = true
    private var screenReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        setupScreenStateMonitoring()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("锁屏服务运行中"))

        Log.d(TAG, "锁屏覆盖服务已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            "SHOW_BLOCK" -> {
                val reason = intent.getStringExtra("reason") ?: "使用时间已到"
                showBlockingOverlay(reason)
            }
            "HIDE_BLOCK" -> {
                hideBlockingOverlay()
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        hideBlockingOverlay()
        stopScreenStateMonitoring()
        isBlocking = false
        Log.d(TAG, "锁屏覆盖服务已销毁")
    }

    // ==================== 全屏覆盖 ====================

    private fun showBlockingOverlay(reason: String) {
        if (isShowingOverlay) return
        if (!hasOverlayPermission()) {
            Log.w(TAG, "没有悬浮窗权限，无法显示锁屏覆盖")
            return
        }

        try {
            val inflater = LayoutInflater.from(this)
            blockingView = inflater.inflate(R.layout.service_blocking_layout, null)

            // 设置阻止原因
            blockingView?.findViewById<TextView>(R.id.tvBlockReason)?.text = reason
            blockingView?.findViewById<TextView>(R.id.tvBlockMessage)?.text = reason

            // 设置时间信息
            val settingsManager = SettingsManager(this)
            val todayTotal = settingsManager.dailyTimeLimit
            blockingView?.findViewById<TextView>(R.id.tvTimeInfo)?.text =
                "今日限额：${todayTotal} 分钟"

            // 联系家长按钮
            blockingView?.findViewById<View>(R.id.btnContactParent)?.setOnClickListener {
                // 发送安全事件到服务器
                try {
                    val callIntent = Intent(Intent.ACTION_DIAL).apply {
                        data = android.net.Uri.parse("tel:${settingsManager.parentPhone}")
                    }
                    callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(callIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "无法拨打电话: ${e.message}")
                }
            }

            // 配置全屏覆盖参数（借鉴 CST）
            setupBlockingView(blockingView!!)

            // 创建窗口参数
            val params = createOverlayParams()
            windowManager?.addView(blockingView, params)
            isShowingOverlay = true
            isBlocking = true

            Log.d(TAG, "锁屏覆盖已显示: $reason")

        } catch (e: Exception) {
            Log.e(TAG, "显示锁屏覆盖失败: ${e.message}")
        }
    }

    private fun hideBlockingOverlay() {
        if (!isShowingOverlay || blockingView == null) return

        try {
            windowManager?.removeView(blockingView)
        } catch (e: Exception) {
            Log.e(TAG, "移除锁屏覆盖失败: ${e.message}")
        }

        blockingView = null
        isShowingOverlay = false
        isBlocking = false
        Log.d(TAG, "锁屏覆盖已隐藏")
    }

    /**
     * 配置阻塞视图（借鉴 CST 的 setupBlockingView）
     * - 沉浸式模式
     * - 拦截所有硬件按键
     * - 获取焦点
     */
    private fun setupBlockingView(view: View) {
        // 沉浸式模式 - 隐藏状态栏和导航栏
        view.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )

        // 使视图可聚焦以捕获所有触摸事件
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()

        // 拦截所有硬件按键（借鉴 CST 的按键拦截）
        view.setOnKeyListener { _, keyCode, event ->
            when (keyCode) {
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_SEARCH,
                KeyEvent.KEYCODE_APP_SWITCH -> {
                    Log.d(TAG, "拦截硬件按键: $keyCode")
                    true // 消费事件，阻止默认行为
                }
                else -> false // 允许音量键等
            }
        }

        Log.d(TAG, "阻塞视图已配置（沉浸式 + 按键拦截）")
    }

    /**
     * 创建全屏覆盖窗口参数（借鉴 CST 的 createOverlayParams）
     */
    private fun createOverlayParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // 全屏覆盖标志（借鉴 CST）
            flags = (WindowManager.LayoutParams.FLAG_FULLSCREEN
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                    or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                    or WindowManager.LayoutParams.FLAG_SECURE) // 防止截图

            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT

            // 覆盖刘海屏区域
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    // ==================== 屏幕状态监控（借鉴 CST） ====================

    private fun setupScreenStateMonitoring() {
        isScreenOn = powerManager?.isInteractive ?: true

        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        Log.d(TAG, "屏幕开启")
                        // 如果应该阻塞但覆盖没显示，重新显示
                        if (isBlocking && !isShowingOverlay) {
                            showBlockingOverlay("使用时间已到")
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        Log.d(TAG, "屏幕关闭")
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun stopScreenStateMonitoring() {
        try {
            screenReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "停止屏幕监控失败: ${e.message}")
        }
    }

    // ==================== 通知 ====================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "锁屏覆盖服务",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "全屏锁屏覆盖服务"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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
            .setContentTitle("家长控制 - 锁屏保护")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_simple)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
