package com.example.parentalcontrol.data.entity

import androidx.room.ColumnInfo

data class DailyAppUsage(
    val packageName: String,
    val appName: String,
    @ColumnInfo(name = "usageTimeMs")
    val usageTimeMs: Long,
    val category: String
)