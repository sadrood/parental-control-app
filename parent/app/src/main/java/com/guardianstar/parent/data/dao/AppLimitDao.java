package com.guardianstar.parent.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.guardianstar.parent.data.AppLimit;

import java.util.List;

@Dao
public interface AppLimitDao {

    @Insert
    void insert(AppLimit appLimit);

    @Update
    void update(AppLimit appLimit);

    @Delete
    void delete(AppLimit appLimit);

    @Query("SELECT * FROM app_limits WHERE deviceId = :deviceId ORDER BY appName ASC")
    LiveData<List<AppLimit>> getLimitsByDevice(String deviceId);

    @Query("SELECT * FROM app_limits WHERE deviceId = :deviceId AND packageName = :packageName")
    AppLimit getLimitByPackage(String deviceId, String packageName);

    @Query("SELECT * FROM app_limits WHERE deviceId = :deviceId AND isWhitelist = 1 ORDER BY appName ASC")
    LiveData<List<AppLimit>> getWhitelistApps(String deviceId);

    @Query("UPDATE app_limits SET usedMinutesToday = 0 WHERE deviceId = :deviceId")
    void resetDailyUsage(String deviceId);

    @Query("UPDATE app_limits SET usedMinutesToday = usedMinutesToday + :minutes WHERE deviceId = :deviceId AND packageName = :packageName")
    void addUsageTime(String deviceId, String packageName, long minutes);

    @Query("DELETE FROM app_limits WHERE deviceId = :deviceId")
    void deleteByDeviceId(String deviceId);

    @Query("SELECT SUM(usedMinutesToday) FROM app_limits WHERE deviceId = :deviceId")
    long getTotalUsageToday(String deviceId);

    @Query("SELECT COUNT(*) FROM app_limits WHERE deviceId = :deviceId AND usedMinutesToday >= dailyLimitMinutes AND isLimitEnabled = 1")
    int getExceededCount(String deviceId);

    @Query("SELECT * FROM app_limits WHERE deviceId = :deviceId")
    List<AppLimit> getLimitsForDevice(String deviceId);
}
