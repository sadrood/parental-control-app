package com.guardianstar.parent.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.guardianstar.parent.data.Schedule;

import java.util.List;

@Dao
public interface ScheduleDao {

    @Insert
    void insert(Schedule schedule);

    @Update
    void update(Schedule schedule);

    @Delete
    void delete(Schedule schedule);

    @Query("SELECT * FROM schedules WHERE deviceId = :deviceId ORDER BY startHour, startMinute")
    LiveData<List<Schedule>> getSchedulesByDevice(String deviceId);

    @Query("SELECT * FROM schedules WHERE deviceId = :deviceId AND enabled = 1 ORDER BY startHour, startMinute")
    List<Schedule> getEnabledSchedules(String deviceId);

    @Query("DELETE FROM schedules WHERE deviceId = :deviceId")
    void deleteByDeviceId(String deviceId);

    @Query("SELECT COUNT(*) FROM schedules WHERE deviceId = :deviceId AND enabled = 1")
    int getEnabledCount(String deviceId);

    @Query("SELECT * FROM schedules WHERE deviceId = :deviceId")
    List<Schedule> getSchedulesForDevice(String deviceId);
}
