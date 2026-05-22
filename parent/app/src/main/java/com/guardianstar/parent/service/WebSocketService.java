package com.guardianstar.parent.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.guardianstar.parent.BuildConfig;
import com.guardianstar.parent.GuardianStarApp;
import com.guardianstar.parent.R;
import com.guardianstar.parent.data.AppDatabase;
import com.guardianstar.parent.data.Device;
import com.guardianstar.parent.ui.MainActivity;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class WebSocketService extends Service {

    private static final String TAG = "WebSocketService";
    private static final int NOTIFICATION_ID = 1001;
    private static final int MAX_RECONNECT_DELAY_MS = 60_000;  // 最大重连间隔 60s
    private static final String DEFAULT_SERVER_URL = BuildConfig.WS_URL;

    private WebSocketClient webSocketClient;
    private Timer heartbeatTimer;
    private Timer reconnectTimer;
    private boolean isConnected = false;
    private int reconnectAttempts = 0;
    private final AtomicInteger notificationIdCounter = new AtomicInteger(3000);

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NOTIFICATION_ID, createForegroundNotification());
        connectWebSocket();
        startHeartbeat();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "ACTION_LOCK":
                    sendCommand("LOCK", intent.getStringExtra("deviceId"));
                    break;
                case "ACTION_UNLOCK":
                    sendCommand("UNLOCK", intent.getStringExtra("deviceId"));
                    break;
                case "ACTION_UPDATE_LIMITS":
                    sendCommand("UPDATE_LIMITS", intent.getStringExtra("deviceId"));
                    break;
                case "ACTION_UPDATE_SCHEDULES":
                    sendCommand("UPDATE_SCHEDULES", intent.getStringExtra("deviceId"));
                    break;
                case "ACTION_UPDATE_WEB_FILTER":
                    sendCommand("UPDATE_WEB_FILTER", intent.getStringExtra("deviceId"));
                    break;
            }
        }
        return START_STICKY;
    }

    private Notification createForegroundNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, GuardianStarApp.CHANNEL_WEBSOCKET)
                .setContentTitle("守护星服务运行中")
                .setContentText("实时监控设备状态")
                .setSmallIcon(R.drawable.ic_shield)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
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
            URI uri = new URI(DEFAULT_SERVER_URL);
            Log.i(TAG, "正在连接 WebSocket: " + DEFAULT_SERVER_URL + " ...");

            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    isConnected = true;
                    reconnectAttempts = 0;
                    Log.i(TAG, "✓ WebSocket 已连接 (HTTP " + handshakedata.getHttpStatus() + ")");
                    sendRegisterMessage();
                }

                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "收到消息: " + (message.length() > 120 ? message.substring(0, 120) + "..." : message));
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    isConnected = false;
                    Log.w(TAG, "✗ WebSocket 断开 (code=" + code + ", reason=" + reason + ", remote=" + remote + ")");
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    isConnected = false;
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
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("type", "register_parent");
            if (webSocketClient != null && webSocketClient.isOpen()) {
                webSocketClient.send(json.toString());
                Log.i(TAG, "已发送 register_parent");
            } else {
                Log.w(TAG, "WebSocket 未连接，无法注册");
            }
        } catch (Exception e) {
            Log.e(TAG, "注册消息发送失败: " + e.getMessage());
        }
    }

    private void handleMessage(String message) {
        try {
            org.json.JSONObject json = new org.json.JSONObject(message);
            String type = json.optString("type");

            switch (type) {
                case "SOS_ALERT":
                    handleSOSAlert(json);
                    break;
                case "DEVICE_STATUS":
                    handleDeviceStatus(json);
                    break;
                case "USAGE_UPDATE":
                    handleUsageUpdate(json);
                    break;
                case "LOCK_CONFIRM":
                    showNotification("设备已锁定", "远程锁定命令已执行");
                    break;
                case "UNLOCK_CONFIRM":
                    showNotification("设备已解锁", "远程解锁命令已执行");
                    break;
            }

            // 发送广播通知UI更新
            Intent intent = new Intent("com.guardianstar.DATA_UPDATED");
            intent.putExtra("message", message);
            sendBroadcast(intent);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleSOSAlert(org.json.JSONObject json) {
        try {
            String deviceName = json.optString("deviceName", "未知设备");
            double latitude = json.optDouble("latitude", 0);
            double longitude = json.optDouble("longitude", 0);

            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("show_sos", true);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 2, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            Notification notification = new NotificationCompat.Builder(this, GuardianStarApp.CHANNEL_SOS)
                    .setContentTitle("🚨 SOS紧急求助")
                    .setContentText(deviceName + " 发送了紧急求助")
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(deviceName + " 发送了紧急求助\n" +
                                    "位置: " + latitude + ", " + longitude))
                    .setSmallIcon(R.drawable.ic_sos)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND)
                    .setAutoCancel(true)
                    .build();

            NotificationManagerCompat.from(this).notify(2001, notification);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDeviceStatus(org.json.JSONObject json) {
        try {
            String deviceId = json.optString("deviceId");
            if (deviceId.isEmpty()) return;

            boolean online = "online".equals(json.optString("status"));
            int batteryLevel = json.optInt("batteryLevel", -1);

            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(this);
                db.deviceDao().updateDeviceOnlineStatus(deviceId, online, System.currentTimeMillis());
                if (batteryLevel >= 0) {
                    db.deviceDao().updateBatteryLevel(deviceId, batteryLevel);
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleUsageUpdate(org.json.JSONObject json) {
        try {
            String deviceId = json.optString("deviceId");
            String packageName = json.optString("packageName");
            long usageSeconds = json.optLong("usageSeconds", 0);

            // 广播通知UI更新
            Intent intent = new Intent("com.guardianstar.USAGE_UPDATED");
            intent.putExtra("deviceId", deviceId);
            intent.putExtra("packageName", packageName);
            intent.putExtra("usageSeconds", usageSeconds);
            sendBroadcast(intent);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendCommand(String command, String deviceId) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                AppDatabase db = AppDatabase.getInstance(this);

                org.json.JSONObject json = new org.json.JSONObject();
                json.put("type", "COMMAND");
                json.put("command", command);
                json.put("device_id", deviceId);

                // 附带完整数据
                if ("UPDATE_LIMITS".equals(command)) {
                    List<com.guardianstar.parent.data.AppLimit> limits =
                            db.appLimitDao().getLimitsForDevice(deviceId);
                    org.json.JSONArray limitsArray = new org.json.JSONArray();
                    for (com.guardianstar.parent.data.AppLimit limit : limits) {
                        org.json.JSONObject limitObj = new org.json.JSONObject();
                        limitObj.put("packageName", limit.getPackageName());
                        limitObj.put("appName", limit.getAppName());
                        limitObj.put("dailyLimitMinutes", limit.getDailyLimitMinutes());
                        limitObj.put("isWhitelist", limit.isWhitelist());
                        limitObj.put("isLimitEnabled", limit.isLimitEnabled());
                        limitsArray.put(limitObj);
                    }
                    json.put("limits", limitsArray);
                } else if ("UPDATE_SCHEDULES".equals(command)) {
                    List<com.guardianstar.parent.data.Schedule> schedules =
                            db.scheduleDao().getSchedulesForDevice(deviceId);
                    org.json.JSONArray schedArray = new org.json.JSONArray();
                    for (com.guardianstar.parent.data.Schedule schedule : schedules) {
                        org.json.JSONObject schedObj = new org.json.JSONObject();
                        schedObj.put("name", schedule.getName());
                        schedObj.put("type", schedule.getType());
                        schedObj.put("startHour", schedule.getStartHour());
                        schedObj.put("startMinute", schedule.getStartMinute());
                        schedObj.put("endHour", schedule.getEndHour());
                        schedObj.put("endMinute", schedule.getEndMinute());
                        schedObj.put("enabled", schedule.isEnabled());
                        schedArray.put(schedObj);
                    }
                    json.put("schedules", schedArray);
                } else if ("UPDATE_WEB_FILTER".equals(command)) {
                    List<com.guardianstar.parent.data.WebFilter> filters =
                            db.webFilterDao().getFiltersForDevice(deviceId);
                    org.json.JSONArray filterArray = new org.json.JSONArray();
                    for (com.guardianstar.parent.data.WebFilter filter : filters) {
                        org.json.JSONObject filterObj = new org.json.JSONObject();
                        filterObj.put("url", filter.getUrl());
                        filterObj.put("category", filter.getCategory());
                        filterObj.put("isBlocked", filter.isBlocked());
                        filterArray.put(filterObj);
                    }
                    json.put("filters", filterArray);
                }

                webSocketClient.send(json.toString());
                Log.i(TAG, "命令已发送: " + command + " → device=" + deviceId);
            } catch (Exception e) {
                Log.e(TAG, "命令发送失败: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(WebSocketService.this, "发送命令失败", Toast.LENGTH_SHORT).show());
            }
        } else {
            Log.w(TAG, "WebSocket 未连接，命令未发送: " + command);
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(WebSocketService.this, "未连接到服务器，命令未发送", Toast.LENGTH_SHORT).show());
        }
    }

    private void scheduleReconnect() {
        if (reconnectTimer != null) {
            reconnectTimer.cancel();
            reconnectTimer = null;
        }

        // 指数退避: 2s, 4s, 8s, 16s, 32s... 最大 60s
        reconnectAttempts++;
        long delayMs = Math.min(2000L * (1L << Math.min(reconnectAttempts - 1, 5)), MAX_RECONNECT_DELAY_MS);
        Log.i(TAG, "将在 " + (delayMs / 1000) + "s 后重连 (第 " + reconnectAttempts + " 次)");

        reconnectTimer = new Timer();
        reconnectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                connectWebSocket();
            }
        }, delayMs);
    }

    private void startHeartbeat() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
        heartbeatTimer = new Timer("WS-Heartbeat");
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

    private void showNotification(String title, String content) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, GuardianStarApp.CHANNEL_ALERT)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();

        NotificationManagerCompat.from(this).notify(notificationIdCounter.incrementAndGet(), notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
        if (reconnectTimer != null) {
            reconnectTimer.cancel();
            reconnectTimer = null;
        }
        if (webSocketClient != null) {
            try {
                webSocketClient.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
