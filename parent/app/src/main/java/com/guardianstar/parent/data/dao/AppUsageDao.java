package com.guardianstar.parent.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.guardianstar.parent.data.AppUsage;

import java.util.List;

@Dao
public interface AppUsageDao {

    @Insert
    void insert(AppUsage appUsage);

    @Update
    void update(AppUsage appUsage);

    @Delete
    void delete(AppUsage appUsage);

    @Query("SELECT * FROM app_usage WHERE deviceId = :deviceId AND date >= :startDate ORDER BY usageTime DESC")
    LiveData<List<AppUsage>> getAppUsageByDevice(String deviceId, long startDate);

    @Query("SELECT SUM(usageTime) FROM app_usage WHERE deviceId = :deviceId AND date >= :startDate")
    long getTotalUsageTime(String deviceId, long startDate);

    @Query("SELECT * FROM app_usage WHERE deviceId = :deviceId ORDER BY date DESC LIMIT 50")
    LiveData<List<AppUsage>> getRecentAppUsage(String deviceId);

    @Query("DELETE FROM app_usage WHERE deviceId = :deviceId")
    void deleteByDeviceId(String deviceId);
}
