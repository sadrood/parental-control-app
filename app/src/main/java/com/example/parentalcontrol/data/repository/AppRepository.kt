package com.example.parentalcontrol.data.repository

import com.example.parentalcontrol.data.dao.AppRuleDao
import com.example.parentalcontrol.data.dao.SecurityEventDao
import com.example.parentalcontrol.data.dao.TimeRuleDao
import com.example.parentalcontrol.data.dao.UsageRecordDao
import com.example.parentalcontrol.data.entity.AppRule
import com.example.parentalcontrol.data.entity.DailyAppUsage
import com.example.parentalcontrol.data.entity.SecurityEvent
import com.example.parentalcontrol.data.entity.TimeRule
import com.example.parentalcontrol.data.entity.UsageRecord
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class AppRepository(
    private val appRuleDao: AppRuleDao,
    private val timeRuleDao: TimeRuleDao,
    private val usageRecordDao: UsageRecordDao,
    private val securityEventDao: SecurityEventDao
) {
    // App Rules
    val allAppRules: Flow<List<AppRule>> = appRuleDao.getAllRules()
    val blockedApps: Flow<List<AppRule>> = appRuleDao.getBlockedApps()
    val whitelistedApps: Flow<List<AppRule>> = appRuleDao.getWhitelistedApps()

    suspend fun getAppRule(packageName: String) = appRuleDao.getRuleByPackage(packageName)
    suspend fun insertAppRule(rule: AppRule) = appRuleDao.insertRule(rule)
    suspend fun updateAppRule(rule: AppRule) = appRuleDao.updateRule(rule)
    suspend fun deleteAppRule(rule: AppRule) = appRuleDao.deleteRule(rule)

    // Time Rules
    val allTimeRules: Flow<List<TimeRule>> = timeRuleDao.getAllRules()
    val enabledTimeRules: Flow<List<TimeRule>> = timeRuleDao.getAllEnabledRules()

    suspend fun insertTimeRule(rule: TimeRule) = timeRuleDao.insertRule(rule)
    suspend fun updateTimeRule(rule: TimeRule) = timeRuleDao.updateTimeRule(rule)
    suspend fun deleteTimeRule(rule: TimeRule) = timeRuleDao.deleteTimeRule(rule)

    // Usage Records
    fun getTodayRecords(): Flow<List<UsageRecord>> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return usageRecordDao.getRecordsByDate(today)
    }

    fun getDailyAppUsage(): Flow<List<DailyAppUsage>> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return usageRecordDao.getDailyAppUsage(today)
    }

    suspend fun getTodayTotalUsage(): Long {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return usageRecordDao.getTotalUsageByDate(today) ?: 0
    }

    suspend fun insertUsageRecord(record: UsageRecord) = usageRecordDao.insertRecord(record)

    // Security Events
    val allSecurityEvents: Flow<List<SecurityEvent>> = securityEventDao.getAllEvents()
    val unresolvedEventCount: Flow<Int> = securityEventDao.getUnresolvedCount()

    suspend fun insertSecurityEvent(event: SecurityEvent) = securityEventDao.insertEvent(event)
    suspend fun updateSecurityEvent(event: SecurityEvent) = securityEventDao.updateEvent(event)
}
