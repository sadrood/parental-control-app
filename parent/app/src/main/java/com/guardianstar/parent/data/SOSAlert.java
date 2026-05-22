package com.guardianstar.parent.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sos_alerts")
public class SOSAlert {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String deviceId;
    private String deviceName;
    private double latitude;
    private double longitude;
    private String address;
    private long alertTime;
    private boolean isRead;

    public SOSAlert() {
    }

    public SOSAlert(String deviceId, String deviceName, double latitude, double longitude) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.alertTime = System.currentTimeMillis();
        this.isRead = false;
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

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public long getAlertTime() {
        return alertTime;
    }

    public void setAlertTime(long alertTime) {
        this.alertTime = alertTime;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }
}
