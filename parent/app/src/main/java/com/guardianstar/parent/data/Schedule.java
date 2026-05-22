package com.guardianstar.parent.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "schedules")
public class Schedule {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String deviceId;
    private String name;                    // 任务名称
    private int startHour;                  // 开始时间-时
    private int startMinute;                // 开始时间-分
    private int endHour;                    // 结束时间-时
    private int endMinute;                  // 结束时间-分
    private int type;                       // 类型：0-锁定设备，1-学习模式
    private boolean enabled;                // 是否启用
    private boolean repeatMonday;
    private boolean repeatTuesday;
    private boolean repeatWednesday;
    private boolean repeatThursday;
    private boolean repeatFriday;
    private boolean repeatSaturday;
    private boolean repeatSunday;

    public static final int TYPE_LOCK = 0;      // 锁定模式
    public static final int TYPE_STUDY = 1;     // 学习模式

    public Schedule() {
    }

    public Schedule(String deviceId, String name, int type) {
        this.deviceId = deviceId;
        this.name = name;
        this.type = type;
        this.enabled = true;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getStartHour() {
        return startHour;
    }

    public void setStartHour(int startHour) {
        this.startHour = startHour;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public void setStartMinute(int startMinute) {
        this.startMinute = startMinute;
    }

    public int getEndHour() {
        return endHour;
    }

    public void setEndHour(int endHour) {
        this.endHour = endHour;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public void setEndMinute(int endMinute) {
        this.endMinute = endMinute;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRepeatMonday() {
        return repeatMonday;
    }

    public void setRepeatMonday(boolean repeatMonday) {
        this.repeatMonday = repeatMonday;
    }

    public boolean isRepeatTuesday() {
        return repeatTuesday;
    }

    public void setRepeatTuesday(boolean repeatTuesday) {
        this.repeatTuesday = repeatTuesday;
    }

    public boolean isRepeatWednesday() {
        return repeatWednesday;
    }

    public void setRepeatWednesday(boolean repeatWednesday) {
        this.repeatWednesday = repeatWednesday;
    }

    public boolean isRepeatThursday() {
        return repeatThursday;
    }

    public void setRepeatThursday(boolean repeatThursday) {
        this.repeatThursday = repeatThursday;
    }

    public boolean isRepeatFriday() {
        return repeatFriday;
    }

    public void setRepeatFriday(boolean repeatFriday) {
        this.repeatFriday = repeatFriday;
    }

    public boolean isRepeatSaturday() {
        return repeatSaturday;
    }

    public void setRepeatSaturday(boolean repeatSaturday) {
        this.repeatSaturday = repeatSaturday;
    }

    public boolean isRepeatSunday() {
        return repeatSunday;
    }

    public void setRepeatSunday(boolean repeatSunday) {
        this.repeatSunday = repeatSunday;
    }

    public String getStartTimeString() {
        return String.format("%02d:%02d", startHour, startMinute);
    }

    public String getEndTimeString() {
        return String.format("%02d:%02d", endHour, endMinute);
    }

    public String getRepeatDaysString() {
        StringBuilder sb = new StringBuilder();
        if (repeatMonday) sb.append("一 ");
        if (repeatTuesday) sb.append("二 ");
        if (repeatWednesday) sb.append("三 ");
        if (repeatThursday) sb.append("四 ");
        if (repeatFriday) sb.append("五 ");
        if (repeatSaturday) sb.append("六 ");
        if (repeatSunday) sb.append("日 ");
        return sb.length() > 0 ? sb.toString().trim() : "仅一次";
    }

    public String getTypeString() {
        return type == TYPE_LOCK ? "锁定模式" : "学习模式";
    }
}
