package com.example.parentalcontrol.data.dao

import androidx.room.*
import com.example.parentalcontrol.data.entity.TimeRule
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeRuleDao {
    @Query("SELECT * FROM time_rules WHERE isEnabled = 1 ORDER BY startHour, startMinute")
    fun getAllEnabledRules(): Flow<List<TimeRule>>

    @Query("SELECT * FROM time_rules ORDER BY id")
    fun getAllRules(): Flow<List<TimeRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRule(rule: TimeRule): Long

    @Update
    fun updateTimeRule(rule: TimeRule): Int

    @Delete
    fun deleteTimeRule(rule: TimeRule): Int
}
