package com.example.parentalcontrol.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.parentalcontrol.data.db.AppDatabase
import com.example.parentalcontrol.security.SecurityChecker
import com.example.parentalcontrol.security.SettingsBlockManager
import com.example.parentalcontrol.util.SettingsManager
import com.example.parentalcontrol.util.TimeChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

/**
 * 应用控制服务（辅助功能服务）
 * 增强版：集成 CST/Blocker 防绕过机制
 * 
 * 功能：
 * 1. 监控前台应用，执行管控规则
 * 2. 拦截设置页面（防强制停止/清除数据/关闭辅助功能/取消设备管理员）
 * 3. 安全环境检测（Root/ADB/安全模式）
 * 4. 时间到时触发全屏锁屏覆盖
 */
class AppControlService : AccessibilityService() {

    companion object {
        private const val TAG = "AppControlService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settingsManager: SettingsManager
    private lateinit var settingsBlockManager: SettingsBlockManager

    // 节流控制：防止频繁触发
    private var lastBlockTime = 0L
    private val BLOCK_THROTTLE_MS = 500L

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsManager = SettingsManager(this)
        settingsBlockManager = SettingsBlockManager(this)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        serviceInfo = info

        Log.d(TAG, "辅助功能服务已连接（增强防绕过模式）")

        // 启动时执行安全检查
        performSecurityCheck()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // 跳过系统关键包名
        if (isSystemPackage(packageName)) return

        // ===== 防绕过：拦截设置页面 =====
        if (settingsBlockManager.shouldBlock(event)) {
            handleSettingsBlock()
            return
        }

        // ===== 正常管控逻辑 =====
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "当前前台应用: $packageName")

            serviceScope.launch {
                checkAndControlApp(packageName)
            }
        }
    }

    /**
     * 处理设置页面拦截
     * 借鉴 Blocker：执行返回操作或显示覆盖
     */
    private fun handleSettingsBlock() {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < BLOCK_THROTTLE_MS) return
        lastBlockTime = now

        Log.w(TAG, "⚠️ 检测到设置页面威胁，执行拦截")

        // 方式一：执行返回键（优先）
        performGlobalAction(GLOBAL_ACTION_BACK)

        // 方式二：如果返回失败，显示全屏覆盖（更激进）
        // ScreenLockOverlayService.showBlock(this, "不允许访问设置")
    }

    /**
     * 检查并管控应用（增强版）
     */
    private suspend fun checkAndControlApp(packageName: String) {
        val db = AppDatabase.getDatabase(this@AppControlService)
        val rule = db.appRuleDao().getRuleByPackage(packageName)

        // 1. 检查应用是否被屏蔽
        if (rule != null && rule.isBlocked) {
            Log.w(TAG, "应用 $packageName 已被禁用")
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        // 2. 检查学习模式
        if (settingsManager.studyMode) {
            if (rule != null && !rule.isStudyApp && !rule.isWhitelisted) {
                Log.w(TAG, "学习模式：$packageName 不在白名单")
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }

        // 3. 检查单个应用时间限制
        if (rule != null && rule.dailyTimeLimit > 0) {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            // 简化检查
        }

        // 4. 检查时段规则
        val timeRules = db.timeRuleDao().getAllEnabledRules().first()

        if (!TimeChecker.isCurrentlyAllowed(timeRules)) {
            Log.w(TAG, "当前时段不允许使用设备")
            performGlobalAction(GLOBAL_ACTION_HOME)
            // 显示全屏锁屏覆盖（借鉴 CST）
            withContext(Dispatchers.Main) {
                ScreenLockOverlayService.showBlock(this@AppControlService, "当前时段不允许使用设备")
            }
            return
        }

        // 5. 检查每日总时间限制（借鉴 CST 的核心逻辑）
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val todayTotal = db.usageRecordDao().getTotalUsageByDate(today) ?: 0
        val remaining = TimeChecker.getRemainingTimeToday(settingsManager.dailyTimeLimit, todayTotal)

        if (remaining <= 0) {
            Log.w(TAG, "今日使用时长已用完 (${todayTotal}ms >= ${settingsManager.dailyTimeLimit}min)")
            performGlobalAction(GLOBAL_ACTION_HOME)
            // 触发全屏锁屏覆盖（借鉴 CST 的核心防绕过机制）
            withContext(Dispatchers.Main) {
                ScreenLockOverlayService.showBlock(this@AppControlService, "今日使用时间已用完")
            }
        }
    }

    /**
     * 执行安全环境检查
     */
    private fun performSecurityCheck() {
        serviceScope.launch {
            try {
                val threats = SecurityChecker.performSecurityCheck(this@AppControlService)

                if (threats.isNotEmpty()) {
                    Log.w(TAG, "安全威胁检测: ${threats.size} 个")

                    // 上报安全事件到服务器
                    try {
                        val apiService = com.example.parentalcontrol.network.ApiClient.getApiService()
                        for (threat in threats) {
                            apiService.reportSecurityEvent(
                                com.example.parentalcontrol.network.model.SecurityEventRequest(
                                    eventType = threat.name,
                                    description = threat.description,
                                    severity = if (threat.isCritical) "critical" else "warning"
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "上报安全事件失败: ${e.message}")
                    }

                    // 对严重威胁采取行动
                    if (threats.any { it.isCritical }) {
                        Log.e(TAG, "检测到严重安全威胁！")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "安全检查异常: ${e.message}")
            }
        }
    }

    /**
     * 判断是否为系统关键包名
     */
    private fun isSystemPackage(packageName: String): Boolean {
        return when (packageName) {
            "com.example.parentalcontrol",      // 本应用
            "com.android.systemui",             // 系统 UI
            "com.android.launcher",             // 启动器
            "com.android.launcher3",            // 启动器3
            "com.android.keyguard",             // 锁屏
            "com.android.inputmethod.latin",    // 输入法
            "com.google.android.inputmethod.latin" -> true
            else -> false
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "辅助功能服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "辅助功能服务已销毁")
    }
}
