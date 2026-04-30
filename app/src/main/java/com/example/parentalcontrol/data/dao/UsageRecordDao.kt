package com.example.parentalcontrol.data.dao

import androidx.room.*
import com.example.parentalcontrol.data.entity.DailyAppUsage
import com.example.parentalcontrol.data.entity.UsageRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageRecordDao {
    @Query("SELECT * FROM usage_records WHERE date = :date ORDER BY usageTimeMs DESC")
    fun getRecordsByDate(date: String): Flow<List<UsageRecord>>

    @Query("SELECT * FROM usage_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC, usageTimeMs DESC")
    fun getRecordsByDateRange(startDate: String, endDate: String): Flow<List<UsageRecord>>

    @Query("SELECT SUM(usageTimeMs) FROM usage_records WHERE date = :date")
    fun getTotalUsageByDate(date: String): kotlin.Long?

    @Query("SELECT packageName, appName, SUM(usageTimeMs) as usageTimeMs, category FROM usage_records WHERE date = :date GROUP BY packageName ORDER BY usageTimeMs DESC")
    fun getDailyAppUsage(date: String): Flow<List<DailyAppUsage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecord(record: UsageRecord): Long

    @Query("DELETE FROM usage_records WHERE date = :date")
    fun deleteRecordsByDate(date: String): Int
}
