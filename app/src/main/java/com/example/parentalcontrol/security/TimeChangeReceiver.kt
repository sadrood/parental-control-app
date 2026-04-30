package com.example.parentalcontrol.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.parentalcontrol.network.ApiClient
import com.example.parentalcontrol.network.model.SecurityEventRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 时间防篡改接收器
 * 监听系统时间变化，检测儿童是否通过修改时间绕过管控
 */
class TimeChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TimeChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            "android.intent.action.TIME_SET",
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.w(TAG, "⚠️ 检测到系统时间被修改！action=${intent.action}")

                // 上报安全事件到服务器
                reportTimeTampering(context)

                // 采取防护措施：强制锁屏
                handleTimeTampering(context)
            }
        }
    }

    /**
     * 上报时间篡改事件
     */
    private fun reportTimeTampering(context: Context) {
        try {
            val apiService = ApiClient.getApiService()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    apiService.reportSecurityEvent(
                        SecurityEventRequest(
                            eventType = "TIME_TAMPERING",
                            description = "检测到系统时间被修改，可能试图绕过时段管控",
                            severity = "critical"
                        )
                    )
                    Log.d(TAG, "时间篡改事件已上报到服务器")
                } catch (e: Exception) {
                    Log.e(TAG, "上报时间篡改事件失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "上报异常: ${e.message}")
        }
    }

    /**
     * 处理时间篡改
     * 防护措施：显示锁屏覆盖
     */
    private fun handleTimeTampering(context: Context) {
        // 显示锁屏覆盖，阻止儿童继续操作
        com.example.parentalcontrol.service.ScreenLockOverlayService.showBlock(
            context,
            "检测到时间被修改，请联系家长"
        )
    }
}
