package com.example.parentalcontrol.data.repository

import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.parentalcontrol.model.AppUsage
import com.example.parentalcontrol.util.LogUtil
import java.util.Calendar

class UsageStatsRepository(private val context: Context) {

    companion object {
        private const val TAG = "UsageStatsRepo"
    }

    private val usageStatsManager: UsageStatsManager
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun getTodayUsageTime(): Long {
        return try {
            val stats = queryTodayStats()
            stats?.filter { it.totalTimeInForeground > 0 }
                ?.sumOf { it.totalTimeInForeground } ?: 0L
        } catch (e: Exception) {
            LogUtil.e(TAG, "获取今日使用时长失败", e)
            0L
        }
    }

    fun getAppUsageStats(): List<AppUsage> {
        return try {
            val stats = queryTodayStats() ?: return emptyList()
            stats.filter { it.totalTimeInForeground > 0 }
                .map { AppUsage(it.packageName, it.totalTimeInForeground) }
                .sortedByDescending { it.usageTime }
        } catch (e: Exception) {
            LogUtil.e(TAG, "获取应用使用统计失败", e)
            emptyList()
        }
    }

    fun getTopApps(count: Int = 3): List<AppUsage> {
        return try {
            getAppUsageStats().take(count)
        } catch (e: Exception) {
            LogUtil.e(TAG, "获取Top应用失败", e)
            emptyList()
        }
    }

    private fun queryTodayStats(): List<android.app.usage.UsageStats>? {
        return try {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime
            )
        } catch (e: Exception) {
            LogUtil.e(TAG, "查询系统使用统计失败", e)
            null
        }
    }
}
