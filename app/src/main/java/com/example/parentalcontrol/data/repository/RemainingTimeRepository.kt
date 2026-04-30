package com.example.parentalcontrol.data.repository

import android.content.Context
import com.example.parentalcontrol.util.SettingsManager

class RemainingTimeRepository(context: Context) {

    private val settingsManager = SettingsManager(context)
    private val usageStatsRepository = UsageStatsRepository(context)

    fun getLimitTime(): Int {
        return settingsManager.dailyTimeLimit
    }

    fun getUsedTimeMs(): Long {
        return usageStatsRepository.getTodayUsageTime()
    }

    fun getRemainingMinutes(): Long {
        val limitMinutes = settingsManager.dailyTimeLimit.toLong()
        val usedMinutes = usageStatsRepository.getTodayUsageTime() / 60000
        val remaining = limitMinutes - usedMinutes
        return if (remaining > 0) remaining else 0L
    }
}
