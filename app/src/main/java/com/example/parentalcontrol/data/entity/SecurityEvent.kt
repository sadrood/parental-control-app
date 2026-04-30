package com.example.parentalcontrol.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_events")
data class SecurityEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val eventType: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false
)
