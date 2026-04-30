package com.example.parentalcontrol.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_records")
data class UsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appName: String,
    val date: String, // yyyy-MM-dd
    val usageTimeMs: Long = 0,
    val category: String = "其他"
)
