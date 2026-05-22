package com.guardianstar.parent.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "app_limits")
public class AppLimit {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String deviceId;
    private String packageName;
    private String appName;
    private long dailyLimitMinutes;    // 每日使用时长限制（分钟）
    private long usedMinutesToday;     // 今日已使用时长
    private boolean isLimitEnabled;    // 是否启用限制
    private boolean isWhitelist;       // 是否白名单应用（学习模式可用）
    private long lastUpdateTime;

    public AppLimit() {
    }

    public AppLimit(String deviceId, String packageName, String appName, long dailyLimitMinutes) {
        this.deviceId = deviceId;
        this.packageName = packageName;
        this.appName = appName;
        this.dailyLimitMinutes = dailyLimitMinutes;
        this.usedMinutesToday = 0;
        this.isLimitEnabled = true;
        this.isWhitelist = false;
        this.lastUpdateTime = System.currentTimeMillis();
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

    public long getDailyLimitMinutes() {
        return dailyLimitMinutes;
    }

    public void setDailyLimitMinutes(long dailyLimitMinutes) {
        this.dailyLimitMinutes = dailyLimitMinutes;
    }

    public long getUsedMinutesToday() {
        return usedMinutesToday;
    }

    public void setUsedMinutesToday(long usedMinutesToday) {
        this.usedMinutesToday = usedMinutesToday;
    }

    public boolean isLimitEnabled() {
        return isLimitEnabled;
    }

    public void setLimitEnabled(boolean limitEnabled) {
        isLimitEnabled = limitEnabled;
    }

    public boolean isWhitelist() {
        return isWhitelist;
    }

    public void setWhitelist(boolean whitelist) {
        isWhitelist = whitelist;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public long getRemainingMinutes() {
        return Math.max(0, dailyLimitMinutes - usedMinutesToday);
    }

    public boolean isTimeExceeded() {
        return isLimitEnabled && usedMinutesToday >= dailyLimitMinutes;
    }

    public int getUsagePercent() {
        if (dailyLimitMinutes == 0) return 0;
        return (int) (usedMinutesToday * 100 / dailyLimitMinutes);
    }
}
