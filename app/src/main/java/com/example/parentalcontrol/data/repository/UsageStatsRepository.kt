package com.example.parentalcontrol.data.repository

import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.parentalcontrol.model.AppUsage
import java.util.Calendar

class UsageStatsRepository(private val context: Context) {

    private val usageStatsManager: UsageStatsManager
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun getTodayUsageTime(): Long {
        val stats = queryTodayStats()
        return stats?.filter { it.totalTimeInForeground > 0 }
            ?.sumOf { it.totalTimeInForeground } ?: 0L
    }

    fun getAppUsageStats(): List<AppUsage> {
        val stats = queryTodayStats() ?: return emptyList()
        return stats.filter { it.totalTimeInForeground > 0 }
            .map { AppUsage(it.packageName, it.totalTimeInForeground) }
            .sortedByDescending { it.usageTime }
    }

    fun getTopApps(count: Int = 3): List<AppUsage> {
        return getAppUsageStats().take(count)
    }

    private fun queryTodayStats(): List<android.app.usage.UsageStats>? {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )
    }
}
