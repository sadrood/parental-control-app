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
import com.example.parentalcontrol.util.ChildLogUtil
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
            try {
                val overlayPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.provider.Settings.canDrawOverlays(context)
                } else true
                ChildLogUtil.i(TAG, "[霸屏] 显示请求 | reason=$reason | overlayPermission=$overlayPerm | sdkVersion=${Build.VERSION.SDK_INT}")
                if (!overlayPerm) {
                    ChildLogUtil.e(TAG, "[霸屏] 启动失败：未获取到悬浮窗权限（SYSTEM_ALERT_WINDOW）")
                }
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
                ChildLogUtil.i(TAG, "[霸屏] 前台服务启动指令已发送 | action=SHOW_BLOCK")
            } catch (e: Exception) {
                ChildLogUtil.e(TAG, "[霸屏] 启动锁屏覆盖失败: ${e.message}", e)
                isBlocking = false
            }
        }

        /**
         * 隐藏锁屏覆盖
         */
        fun hideBlock(context: Context) {
            try {
                ChildLogUtil.i(TAG, "[霸屏] 隐藏请求 | wasBlocking=$isBlocking")
                isBlocking = false
                val intent = Intent(context, ScreenLockOverlayService::class.java).apply {
                    action = "HIDE_BLOCK"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                ChildLogUtil.i(TAG, "[霸屏] 隐藏指令已发送 | action=HIDE_BLOCK")
            } catch (e: Exception) {
                ChildLogUtil.e(TAG, "[霸屏] 隐藏锁屏覆盖失败: ${e.message}", e)
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
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
            powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val overlayPerm = hasOverlayPermission()

            ChildLogUtil.i(TAG, "[霸屏] 锁屏覆盖服务创建 | overlayPermission=$overlayPerm | windowManagerReady=${windowManager != null} | sdkVersion=${Build.VERSION.SDK_INT}")

            setupScreenStateMonitoring()
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification("锁屏服务运行中"))

            ChildLogUtil.i(TAG, "[霸屏] 前台通知已显示 | notificationId=$NOTIFICATION_ID")
        } catch (e: Exception) {
            ChildLogUtil.e(TAG, "[霸屏] 服务创建失败: ${e.message}", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        ChildLogUtil.d(TAG, "[霸屏] onStartCommand | action=$action | flags=$flags | startId=$startId")

        when (action) {
            "SHOW_BLOCK" -> {
                val reason = intent.getStringExtra("reason") ?: "使用时间已到"
                ChildLogUtil.i(TAG, "[霸屏] 执行 SHOW_BLOCK | reason=$reason")
                showBlockingOverlay(reason)
            }
            "HIDE_BLOCK" -> {
                ChildLogUtil.i(TAG, "[霸屏] 执行 HIDE_BLOCK")
                hideBlockingOverlay()
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ChildLogUtil.i(TAG, "[霸屏] 锁屏覆盖服务销毁 | wasShowing=$isShowingOverlay | wasBlocking=$isBlocking")
        super.onDestroy()
        hideBlockingOverlay()
        stopScreenStateMonitoring()
        isBlocking = false
        ChildLogUtil.i(TAG, "[霸屏] 锁屏覆盖服务已销毁，资源已清理")
    }

    // ==================== 全屏覆盖 ====================

    private fun showBlockingOverlay(reason: String) {
        if (isShowingOverlay) {
            ChildLogUtil.d(TAG, "[霸屏] 覆盖已显示，跳过重复请求 | reason=$reason")
            return
        }
        if (windowManager == null) {
            ChildLogUtil.e(TAG, "[霸屏] 启动失败：WindowManager 未初始化")
            return
        }
        val overlayPerm = hasOverlayPermission()
        if (!overlayPerm) {
            ChildLogUtil.e(TAG, "[霸屏] 启动失败：没有悬浮窗权限（SYSTEM_ALERT_WINDOW 未授权）| 请检查：设置→应用→悬浮窗权限")
            return
        }

        try {
            ChildLogUtil.i(TAG, "[霸屏] 开始创建全屏覆盖 | reason=$reason | overlayPermission=$overlayPerm")
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
                try {
                    val callIntent = Intent(Intent.ACTION_DIAL).apply {
                        data = android.net.Uri.parse("tel:${settingsManager.parentPhone}")
                    }
                    callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(callIntent)
                    ChildLogUtil.i(TAG, "[霸屏] 儿童点击联系家长 | phone=${settingsManager.parentPhone}")
                } catch (e: Exception) {
                    ChildLogUtil.e(TAG, "[霸屏] 无法拨打电话: ${e.message}", e)
                }
            }

            // 配置全屏覆盖参数（借鉴 CST）
            setupBlockingView(blockingView!!)
            ChildLogUtil.i(TAG, "[霸屏] 阻塞视图已配置 | 沉浸式模式+按键拦截+防截图(FLAG_SECURE)")

            // 创建窗口参数
            val params = createOverlayParams()
            windowManager?.addView(blockingView, params)
            isShowingOverlay = true
            isBlocking = true

            ChildLogUtil.i(TAG, "[霸屏] 霸屏已启动 | reason=$reason | windowType=${params.type} | 状态：全屏锁定，禁止退出")

        } catch (e: SecurityException) {
            ChildLogUtil.e(TAG, "[霸屏] 启动失败：安全异常（权限被拒绝）| ${e.message}", e)
        } catch (e: android.view.WindowManager.BadTokenException) {
            ChildLogUtil.e(TAG, "[霸屏] 启动失败：窗口Token无效 | ${e.message}", e)
        } catch (e: Exception) {
            ChildLogUtil.e(TAG, "[霸屏] 启动失败：未知异常 | ${e.message}", e)
        }
    }

    private fun hideBlockingOverlay() {
        if (!isShowingOverlay || blockingView == null) {
            ChildLogUtil.d(TAG, "[霸屏] 未显示覆盖，跳过隐藏")
            return
        }

        try {
            windowManager?.removeView(blockingView)
            ChildLogUtil.i(TAG, "[霸屏] 霸屏已解除 | 状态：恢复正常使用")
        } catch (e: Exception) {
            ChildLogUtil.e(TAG, "[霸屏] 移除锁屏覆盖失败: ${e.message}", e)
        }

        blockingView = null
        isShowingOverlay = false
        isBlocking = false
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
                        ChildLogUtil.d(TAG, "[霸屏] 屏幕开启 | isBlocking=$isBlocking | isShowingOverlay=$isShowingOverlay")
                        // 如果应该阻塞但覆盖没显示（可能被系统杀死），重新显示
                        if (isBlocking && !isShowingOverlay) {
                            ChildLogUtil.w(TAG, "[霸屏] 检测到霸屏丢失！屏幕开启后覆盖未显示，自动恢复霸屏")
                            showBlockingOverlay("使用时间已到")
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        ChildLogUtil.d(TAG, "[霸屏] 屏幕关闭")
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
            screenReceiver?.let {
                unregisterReceiver(it)
                ChildLogUtil.d(TAG, "[霸屏] 屏幕状态监控已停止")
            }
        } catch (e: Exception) {
            ChildLogUtil.e(TAG, "[霸屏] 停止屏幕监控失败: ${e.message}", e)
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
