package com.example.parentalcontrol.utils

import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.parentalcontrol.model.AppUsageInfo
import java.util.*

object UsageStatsHelper {

    fun getTodayUsageStats(context: Context): List<AppUsageInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val usageInfoList = mutableListOf<AppUsageInfo>()

        stats?.forEach { usageStat ->
            if (usageStat.totalTimeInForeground > 0) {
                val pm = context.packageManager
                try {
                    val appInfo = pm.getApplicationInfo(usageStat.packageName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    usageInfoList.add(AppUsageInfo(usageStat.packageName, appName, usageStat.totalTimeInForeground))
                } catch (e: Exception) {
                    // 忽略未找到的应用
                }
            }
        }
        return usageInfoList.sortedByDescending { it.usageTime }
    }
}
