package com.example.parentalcontrol.ui.parent.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.parentalcontrol.data.repository.RemainingTimeRepository
import com.example.parentalcontrol.data.repository.UsageStatsRepository
import com.example.parentalcontrol.model.AppUsage
import com.example.parentalcontrol.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsageStatsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "UsageStatsVM"
        private const val REFRESH_INTERVAL = 30_000L
    }

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

    private val _childOnlineStatus = MutableLiveData(false)
    val childOnlineStatus: LiveData<Boolean> = _childOnlineStatus

    private val _syncStatus = MutableLiveData<SyncStatus>(SyncStatus.IDLE)
    val syncStatus: LiveData<SyncStatus> = _syncStatus

    enum class SyncStatus { IDLE, SYNCING, SUCCESS, FAILED }

    init {
        refresh()
        startPeriodicRefresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allStats = usageStatsRepository.getAppUsageStats()
                val todayUsage = usageStatsRepository.getTodayUsageTime()
                val remaining = remainingTimeRepository.getRemainingMinutes()
                val limit = remainingTimeRepository.getLimitTime()

                withContext(Dispatchers.Main) {
                    _appUsageStats.value = allStats
                    _todayUsageTime.value = todayUsage
                    _topApps.value = allStats.take(3)
                    _remainingTime.value = remaining
                    _dailyLimit.value = limit
                    _syncStatus.value = SyncStatus.SUCCESS
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "刷新数据失败", e)
                withContext(Dispatchers.Main) {
                    _syncStatus.value = SyncStatus.FAILED
                }
            }
        }
    }

    fun updateChildOnlineStatus(online: Boolean) {
        _childOnlineStatus.value = online
    }

    fun updateSyncStatus(status: SyncStatus) {
        _syncStatus.value = status
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(REFRESH_INTERVAL)
            }
        }
    }
}
