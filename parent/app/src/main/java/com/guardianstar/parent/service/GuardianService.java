package com.guardianstar.parent.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.guardianstar.parent.BuildConfig;
import com.guardianstar.parent.R;
import com.guardianstar.parent.ui.ChildMainActivity;
import com.guardianstar.parent.ui.LockActivity;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;

public class GuardianService extends Service {

    private static final String TAG = "GuardianService";
    private static final int NOTIFICATION_ID = 1001;
    private static final int MAX_RECONNECT_DELAY_MS = 60_000;
    public static final String CHANNEL_ID = "guardian_service_channel";
    public static final String ACTION_LOCK = "com.guardianstar.ACTION_LOCK";
    public static final String ACTION_UNLOCK = "com.guardianstar.ACTION_UNLOCK";
    public static final String ACTION_UPDATE_LIMITS = "com.guardianstar.ACTION_UPDATE_LIMITS";

    private WebSocketClient webSocketClient;
    private Timer heartbeatTimer;
    private boolean isDeviceLocked = false;
    private int reconnectAttempts = 0;
    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("guardian_prefs", MODE_PRIVATE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        registerCommandReceiver();
        connectWebSocket();
        startHeartbeat();
        startUsageMonitor();
        startPeriodicReport();
        prefs.edit().putBoolean("service_running", true).apply();
    }

    private void updateNotification(String text) {
        Intent intent = new Intent(this, ChildMainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("守护星服务")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_shield_child)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, n);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_LOCK:
                    lockDevice();
                    break;
                case ACTION_UNLOCK:
                    unlockDevice();
                    break;
            }
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "守护服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("守护星后台保护服务");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, ChildMainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("守护星服务运行中")
                .setContentText("正在保护设备安全")
                .setSmallIcon(R.drawable.ic_shield_child)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setShowWhen(false);

        return builder.build();
    }

    private void registerCommandReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_LOCK);
        filter.addAction(ACTION_UNLOCK);
        filter.addAction(ACTION_UPDATE_LIMITS);
        filter.addAction("com.guardianstar.SOS_TRIGGERED");
        registerReceiver(commandReceiver, filter);
    }

    private BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) return;
            switch (intent.getAction()) {
                case ACTION_LOCK: lockDevice(); break;
                case ACTION_UNLOCK: unlockDevice(); break;
                case "com.guardianstar.SOS_TRIGGERED": reportSOSToServer(); break;
            }
        }
    };

    /** 通过 WebSocket 向服务端上报 SOS 事件 */
    private void reportSOSToServer() {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            Log.w(TAG, "WebSocket 未连接，SOS 上报暂存");
            return;
        }
        try {
            String deviceId = prefs.getString("device_id", "");
            String bindCode = prefs.getString("bind_code", "");
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("type", "SOS_ALERT");
            json.put("device_id", deviceId);
            json.put("device_name", bindCode);
            json.put("latitude", 0);
            json.put("longitude", 0);
            webSocketClient.send(json.toString());
            Log.i(TAG, "🚨 SOS 事件已上报到服务端");
        } catch (Exception e) {
            Log.e(TAG, "SOS 上报失败: " + e.getMessage());
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void connectWebSocket() {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "无网络连接，稍后重连");
            scheduleReconnect();
            return;
        }

        try {
            URI uri = new URI(BuildConfig.WS_URL);
            Log.i(TAG, "正在连接 WebSocket: " + BuildConfig.WS_URL + " ...");

            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    reconnectAttempts = 0;
                    Log.i(TAG, "✓ WebSocket 已连接");
                    updateNotification("守护星 · 已连接服务器");
                    sendRegisterMessage();
                    // 连接成功后立即上报一次数据
                    new Handler(Looper.getMainLooper()).postDelayed(() -> doStatusReport(), 3000);
                }

                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "收到: " + (message.length() > 120 ? message.substring(0, 120) + "..." : message));
                    handleWebSocketMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.w(TAG, "✗ WebSocket 断开 (code=" + code + ", remote=" + remote + ")");
                    updateNotification("守护星 · 未连接，正在重连...");
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "✗ WebSocket 错误: " + ex.getMessage());
                }
            };
            webSocketClient.setConnectionLostTimeout(30);
            webSocketClient.connect();
        } catch (Exception e) {
            Log.e(TAG, "WebSocket 连接异常: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void sendRegisterMessage() {
        try {
            String deviceId = prefs.getString("device_id", "");
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("type", "register_child");
            json.put("device_id", deviceId);
            if (webSocketClient != null && webSocketClient.isOpen()) {
                webSocketClient.send(json.toString());
                Log.i(TAG, "已发送 register_child, deviceId=" + deviceId);
            }
        } catch (Exception e) {
            Log.e(TAG, "注册消息发送失败: " + e.getMessage());
        }
    }

    private void handleWebSocketMessage(String message) {
        try {
            org.json.JSONObject json = new org.json.JSONObject(message);
            String type = json.optString("type");

            if ("COMMAND".equals(type)) {
                String command = json.optString("command");
                switch (command) {
                    case "LOCK": lockDevice(); break;
                    case "UNLOCK": unlockDevice(); break;
                    case "UPDATE_LIMITS": handleUpdateLimits(json); break;
                    case "UPDATE_SCHEDULES": handleUpdateSchedules(json); break;
                }
            } else if ("REPORT_REQUEST".equals(type)) {
                // 家长端手动触发上报
                Log.i(TAG, "收到上报请求，立即上报数据...");
                doStatusReport();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 上报设备状态 + 使用数据 */
    private void doStatusReport() {
        if (webSocketClient == null || !webSocketClient.isOpen()) return;
        try {
            String deviceId = prefs.getString("device_id", "");
            String bindCode = prefs.getString("bind_code", "");

            // 读取电量
            android.content.IntentFilter batteryFilter = new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
            android.content.Intent batteryIntent = registerReceiver(null, batteryFilter);
            int batteryPct = 100;
            if (batteryIntent != null) {
                int level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) batteryPct = level * 100 / scale;
            }

            org.json.JSONObject json = new org.json.JSONObject();
            json.put("type", "STATUS_REPORT");
            json.put("device_id", deviceId);
            json.put("bind_code", bindCode);
            json.put("battery_level", batteryPct);

            // 上报使用数据（从 UsageMonitorService 获取）
            org.json.JSONArray usageArray = new org.json.JSONArray();
            java.util.Map<String, Long> allUsage = UsageMonitorService.getInstance() != null ?
                    UsageMonitorService.getInstance().getAllUsageTime() : new java.util.HashMap<>();
            for (java.util.Map.Entry<String, Long> entry : allUsage.entrySet()) {
                org.json.JSONObject u = new org.json.JSONObject();
                u.put("packageName", entry.getKey());
                u.put("usageSeconds", entry.getValue() / 1000);
                usageArray.put(u);
            }
            json.put("usage_data", usageArray);

            webSocketClient.send(json.toString());
            Log.i(TAG, "数据已上报: 电量=" + batteryPct + "%, 使用记录=" + usageArray.length() + "条");
        } catch (Exception e) {
            Log.e(TAG, "数据上报失败: " + e.getMessage());
        }
    }

    private Timer reportTimer;
    private void startPeriodicReport() {
        if (reportTimer != null) reportTimer.cancel();
        reportTimer = new Timer();
        reportTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() { doStatusReport(); }
        }, 60000, 15 * 60 * 1000); // 启动后1分首次上报，之后每15分钟
    }

    private void stopPeriodicReport() {
        if (reportTimer != null) { reportTimer.cancel(); reportTimer = null; }
    }

    private void handleUpdateLimits(org.json.JSONObject json) {
        try {
            org.json.JSONArray limits = json.optJSONArray("limits");
            if (limits == null) return;

            SharedPreferences.Editor editor = prefs.edit();
            for (int i = 0; i < limits.length(); i++) {
                org.json.JSONObject obj = limits.getJSONObject(i);
                String packageName = obj.optString("packageName");
                // 安全校验：包名不能为空，且不超过 64 字符
                if (packageName == null || packageName.isEmpty() || packageName.length() > 64) {
                    Log.w(TAG, "忽略无效包名: " + packageName);
                    continue;
                }
                long dailyLimitMinutes = obj.optLong("dailyLimitMinutes", 0);
                boolean isLimitEnabled = obj.optBoolean("isLimitEnabled", true);
                boolean isWhitelist = obj.optBoolean("isWhitelist", false);

                if (isLimitEnabled || isWhitelist) {
                    editor.putLong("limit_" + packageName, dailyLimitMinutes);
                } else {
                    editor.remove("limit_" + packageName);
                }
                if (isWhitelist) {
                    editor.putBoolean("whitelist_" + packageName, true);
                } else {
                    editor.remove("whitelist_" + packageName);
                }
            }
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleUpdateSchedules(org.json.JSONObject json) {
        try {
            org.json.JSONArray schedules = json.optJSONArray("schedules");
            if (schedules == null) return;

            // 保存定时任务数据到 SharedPreferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("schedule_count", schedules.length());
            for (int i = 0; i < schedules.length(); i++) {
                org.json.JSONObject obj = schedules.getJSONObject(i);
                String prefix = "schedule_" + i + "_";
                editor.putString(prefix + "name", obj.optString("name"));
                editor.putInt(prefix + "type", obj.optInt("type"));
                editor.putInt(prefix + "startHour", obj.optInt("startHour"));
                editor.putInt(prefix + "startMinute", obj.optInt("startMinute"));
                editor.putInt(prefix + "endHour", obj.optInt("endHour"));
                editor.putInt(prefix + "endMinute", obj.optInt("endMinute"));
                editor.putBoolean(prefix + "enabled", obj.optBoolean("enabled", true));
            }
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void scheduleReconnect() {
        reconnectAttempts++;
        long delayMs = Math.min(2000L * (1L << Math.min(reconnectAttempts - 1, 5)), MAX_RECONNECT_DELAY_MS);
        Log.i(TAG, "将在 " + (delayMs / 1000) + "s 后重连 (第 " + reconnectAttempts + " 次)");

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (webSocketClient != null && !webSocketClient.isOpen()) {
                connectWebSocket();
            }
        }, delayMs);
    }

    private void startHeartbeat() {
        heartbeatTimer = new Timer("Guardian-Heartbeat");
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (webSocketClient != null && webSocketClient.isOpen()) {
                        org.json.JSONObject json = new org.json.JSONObject();
                        json.put("type", "ping");
                        webSocketClient.send(json.toString());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "心跳发送失败: " + e.getMessage());
                }
            }
        }, 30000, 30000);
    }

    private void startUsageMonitor() {
        Intent intent = new Intent(this, UsageMonitorService.class);
        startService(intent);
    }

    private void lockDevice() {
        isDeviceLocked = true;
        prefs.edit().putBoolean("device_locked", true).apply();

        Intent lockIntent = new Intent(this, LockActivity.class);
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(lockIntent);

        sendStatusUpdate("locked");
    }

    private void unlockDevice() {
        isDeviceLocked = false;
        prefs.edit().putBoolean("device_locked", false).apply();

        Intent unlockIntent = new Intent("com.guardianstar.UNLOCK");
        sendBroadcast(unlockIntent);

        sendStatusUpdate("unlocked");
    }

    private void sendStatusUpdate(String status) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                org.json.JSONObject json = new org.json.JSONObject();
                json.put("type", "status_update");
                json.put("status", status);
                webSocketClient.send(json.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        prefs.edit().putBoolean("service_running", false).apply();

        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
        }
        stopPeriodicReport();

        if (webSocketClient != null) {
            try {
                webSocketClient.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            unregisterReceiver(commandReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
