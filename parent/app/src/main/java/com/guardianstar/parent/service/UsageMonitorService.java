package com.guardianstar.parent.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.Map;

public class UsageMonitorService extends AccessibilityService {

    private static final String TAG = "UsageMonitorService";
    private static UsageMonitorService instance;
    private Map<String, Long> appUsageTime = new HashMap<>();
    private Map<String, Long> appStartTime = new HashMap<>();
    private String currentPackage = "";
    private SharedPreferences prefs;

    public static UsageMonitorService getInstance() { return instance; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = getSharedPreferences("guardian_prefs", MODE_PRIVATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() != null) {
                String packageName = event.getPackageName().toString();
                handleAppChange(packageName);
            }
        }
    }

    private void handleAppChange(String packageName) {
        if (packageName.equals(currentPackage)) {
            return;
        }

        if (!currentPackage.isEmpty()) {
            recordUsageTime(currentPackage);
        }

        currentPackage = packageName;
        appStartTime.put(packageName, System.currentTimeMillis());

        checkAppLimit(packageName);
    }

    private void recordUsageTime(String packageName) {
        Long startTime = appStartTime.get(packageName);
        if (startTime != null) {
            long usageTime = System.currentTimeMillis() - startTime;
            long currentTotal = appUsageTime.getOrDefault(packageName, 0L);
            appUsageTime.put(packageName, currentTotal + usageTime);

            prefs.edit().putLong("usage_" + packageName, currentTotal + usageTime).apply();

            Log.d(TAG, "应用: " + packageName + " 使用了 " + (usageTime / 1000) + " 秒");
        }
    }

    private void checkAppLimit(String packageName) {
        long limitMinutes = prefs.getLong("limit_" + packageName, 0);
        if (limitMinutes <= 0) {
            return;
        }

        long usedMinutes = appUsageTime.getOrDefault(packageName, 0L) / (60 * 1000);

        if (usedMinutes >= limitMinutes) {
            Intent lockIntent = new Intent(GuardianService.ACTION_LOCK);
            sendBroadcast(lockIntent);
            Log.d(TAG, "应用: " + packageName + " 超过使用限制，锁定设备");
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "服务中断");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.DEFAULT;
        setServiceInfo(info);
        Log.d(TAG, "应用使用监控服务已启动");
    }

    public long getAppUsageTime(String packageName) {
        return appUsageTime.getOrDefault(packageName, 0L);
    }

    public Map<String, Long> getAllUsageTime() {
        return new HashMap<>(appUsageTime);
    }
}
