package com.guardianstar.parent.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.guardianstar.parent.data.SOSAlert;

import java.util.List;

@Dao
public interface SOSAlertDao {

    @Insert
    void insert(SOSAlert sosAlert);

    @Update
    void update(SOSAlert sosAlert);

    @Delete
    void delete(SOSAlert sosAlert);

    @Query("SELECT * FROM sos_alerts ORDER BY alertTime DESC")
    LiveData<List<SOSAlert>> getAllAlerts();

    @Query("SELECT * FROM sos_alerts WHERE isRead = 0 ORDER BY alertTime DESC")
    LiveData<List<SOSAlert>> getUnreadAlerts();

    @Query("UPDATE sos_alerts SET isRead = 1 WHERE id = :id")
    void markAsRead(int id);

    @Query("UPDATE sos_alerts SET isRead = 1")
    void markAllAsRead();

    @Query("SELECT COUNT(*) FROM sos_alerts WHERE isRead = 0")
    int getUnreadCount();
}
