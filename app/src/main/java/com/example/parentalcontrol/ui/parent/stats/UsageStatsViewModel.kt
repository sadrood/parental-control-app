package com.example.parentalcontrol.ui.parent.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.parentalcontrol.data.repository.RemainingTimeRepository
import com.example.parentalcontrol.data.repository.UsageStatsRepository
import com.example.parentalcontrol.model.AppUsage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class UsageStatsViewModel(application: Application) : AndroidViewModel(application) {

    private val usageStatsRepository = UsageStatsRepository(application)
    private val remainingTimeRepository = RemainingTimeRepository(application)

    private val _todayUsageTime = MutableLiveData(0L)
    val todayUsageTime: LiveData<Long> = _todayUsageTime

    private val _appUsageStats = MutableLiveData<List<AppUsage>>(emptyList())
    val appUsageStats: LiveData<List<AppUsage>> = _appUsageStats

    private val _topApps = MutableLiveData<List<AppUsage>>(emptyList())
    val topApps: LiveData<List<AppUsage>> = _topApps

    private val _remainingTime = MutableLiveData(0L)
    val remainingTime: LiveData<Long> = _remainingTime

    private val _dailyLimit = MutableLiveData(60)
    val dailyLimit: LiveData<Int> = _dailyLimit

    init {
        refresh()
        startPeriodicRefresh()
    }

    fun refresh() {
        val allStats = usageStatsRepository.getAppUsageStats()
        _todayUsageTime.value = usageStatsRepository.getTodayUsageTime()
        _appUsageStats.value = allStats
        _topApps.value = allStats.take(3)
        _remainingTime.value = remainingTimeRepository.getRemainingMinutes()
        _dailyLimit.value = remainingTimeRepository.getLimitTime()
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(30_000)
            }
        }
    }
}
