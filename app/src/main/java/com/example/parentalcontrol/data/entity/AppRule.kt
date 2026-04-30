package com.example.parentalcontrol.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String = "其他",
    val isBlocked: Boolean = false,
    val dailyTimeLimit: Int = 0, // 0 = 不限制，单位分钟
    val isWhitelisted: Boolean = false,
    val isStudyApp: Boolean = false
)
