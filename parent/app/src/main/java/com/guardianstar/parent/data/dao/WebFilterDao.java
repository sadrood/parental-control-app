package com.guardianstar.parent.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.guardianstar.parent.data.WebFilter;

import java.util.List;

@Dao
public interface WebFilterDao {

    @Insert
    void insert(WebFilter webFilter);

    @Update
    void update(WebFilter webFilter);

    @Delete
    void delete(WebFilter webFilter);

    @Query("SELECT * FROM web_filters WHERE deviceId = :deviceId ORDER BY createTime DESC")
    LiveData<List<WebFilter>> getFiltersByDevice(String deviceId);

    @Query("SELECT * FROM web_filters WHERE deviceId = :deviceId AND isBlocked = 1 ORDER BY createTime DESC")
    LiveData<List<WebFilter>> getBlockedUrls(String deviceId);

    @Query("DELETE FROM web_filters WHERE deviceId = :deviceId AND url = :url")
    void deleteByUrl(String deviceId, String url);

    @Query("DELETE FROM web_filters WHERE deviceId = :deviceId")
    void deleteByDeviceId(String deviceId);

    @Query("SELECT * FROM web_filters WHERE deviceId = :deviceId")
    List<WebFilter> getFiltersForDevice(String deviceId);
}
