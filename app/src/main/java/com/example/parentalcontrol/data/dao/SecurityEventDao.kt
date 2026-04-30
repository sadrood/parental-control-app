package com.example.parentalcontrol.data.dao

import androidx.room.*
import com.example.parentalcontrol.data.entity.SecurityEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityEventDao {
    @Query("SELECT * FROM security_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<SecurityEvent>>

    @Query("SELECT COUNT(*) FROM security_events WHERE isResolved = 0")
    fun getUnresolvedCount(): Flow<Int>

    @Insert
    fun insertEvent(event: SecurityEvent): Long

    @Update
    fun updateEvent(event: SecurityEvent): Int

    @Query("DELETE FROM security_events WHERE isResolved = 1")
    fun deleteResolvedEvents(): Int
}
