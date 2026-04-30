package com.example.parentalcontrol.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.parentalcontrol.data.dao.AppRuleDao
import com.example.parentalcontrol.data.dao.SecurityEventDao
import com.example.parentalcontrol.data.dao.TimeRuleDao
import com.example.parentalcontrol.data.dao.UsageRecordDao
import com.example.parentalcontrol.data.entity.AppRule
import com.example.parentalcontrol.data.entity.SecurityEvent
import com.example.parentalcontrol.data.entity.TimeRule
import com.example.parentalcontrol.data.entity.UsageRecord

@Database(
    entities = [AppRule::class, TimeRule::class, UsageRecord::class, SecurityEvent::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
    abstract fun timeRuleDao(): TimeRuleDao
    abstract fun usageRecordDao(): UsageRecordDao
    abstract fun securityEventDao(): SecurityEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parental_control_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
