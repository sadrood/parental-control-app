package com.example.parentalcontrol.data.dao

import androidx.room.*
import com.example.parentalcontrol.data.entity.AppRule
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules ORDER BY appName ASC")
    fun getAllRules(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules WHERE isBlocked = 1")
    fun getBlockedApps(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules WHERE isWhitelisted = 1")
    fun getWhitelistedApps(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName")
    fun getRuleByPackage(packageName: String): AppRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRule(rule: AppRule): Long

    @Update
    fun updateRule(rule: AppRule): Int

    @Delete
    fun deleteRule(rule: AppRule): Int

    @Query("DELETE FROM app_rules")
    fun deleteAllRules(): Int
}
