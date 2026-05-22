package com.guardianstar.parent.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.guardianstar.parent.data.Device;

import java.util.List;

@Dao
public interface DeviceDao {

    @Insert
    void insert(Device device);

    @Update
    void update(Device device);

    @Delete
    void delete(Device device);

    @Query("SELECT * FROM devices ORDER BY bindTime DESC")
    LiveData<List<Device>> getAllDevices();

    @Query("SELECT * FROM devices WHERE deviceId = :deviceId")
    LiveData<Device> getDeviceById(String deviceId);

    @Query("UPDATE devices SET isOnline = :online, lastOnlineTime = :time WHERE deviceId = :deviceId")
    void updateDeviceOnlineStatus(String deviceId, boolean online, long time);

    @Query("UPDATE devices SET isLocked = :locked WHERE deviceId = :deviceId")
    void updateDeviceLockStatus(String deviceId, boolean locked);

    @Query("UPDATE devices SET batteryLevel = :level WHERE deviceId = :deviceId")
    void updateBatteryLevel(String deviceId, int level);

    @Query("SELECT COUNT(*) FROM devices")
    int getDeviceCount();
}
