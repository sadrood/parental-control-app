package com.guardianstar.parent.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "web_filters")
public class WebFilter {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String deviceId;
    private String url;
    private String category;
    private boolean isBlocked;
    private long createTime;

    public WebFilter() {
    }

    public WebFilter(String deviceId, String url, String category) {
        this.deviceId = deviceId;
        this.url = url;
        this.category = category;
        this.isBlocked = true;
        this.createTime = System.currentTimeMillis();
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void setBlocked(boolean blocked) {
        isBlocked = blocked;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
}
