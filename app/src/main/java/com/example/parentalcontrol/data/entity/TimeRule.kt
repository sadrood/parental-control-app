package com.example.parentalcontrol.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_rules")
data class TimeRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val isAllowed: Boolean = false,
    val weekdays: String = "1,2,3,4,5", // 1=周一, 7=周日
    val isEnabled: Boolean = true
)
