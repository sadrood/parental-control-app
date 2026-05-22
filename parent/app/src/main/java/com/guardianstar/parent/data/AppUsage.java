package com.guardianstar.parent.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "app_usage")
public class AppUsage {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String deviceId;
    private String packageName;
    private String appName;
    private long usageTime;
    private long startTime;
    private long endTime;
    private long date;

    public AppUsage() {
    }

    public AppUsage(String deviceId, String packageName, String appName, long usageTime) {
        this.deviceId = deviceId;
        this.packageName = packageName;
        this.appName = appName;
        this.usageTime = usageTime;
        this.date = System.currentTimeMillis();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public long getUsageTime() {
        return usageTime;
    }

    public void setUsageTime(long usageTime) {
        this.usageTime = usageTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }
}
