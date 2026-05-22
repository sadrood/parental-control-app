package com.guardianstar.parent;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import com.guardianstar.parent.data.AppDatabase;

import java.security.SecureRandom;
import java.util.UUID;

public class GuardianStarApp extends Application {

    public static final String CHANNEL_WEBSOCKET = "websocket_channel";
    public static final String CHANNEL_SOS = "sos_channel";
    public static final String CHANNEL_ALERT = "alert_channel";
    public static final String CHANNEL_CONTROL = "control_channel";
    public static final String CHANNEL_GUARDIAN = "guardian_service_channel";

    private static GuardianStarApp instance;
    private AppDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannels();
        database = AppDatabase.getInstance(this);
        initPreferences();
    }

    public static GuardianStarApp getInstance() {
        return instance;
    }

    public AppDatabase getDatabase() {
        return database;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);

            // WebSocket服务通道
            NotificationChannel wsChannel = new NotificationChannel(
                    CHANNEL_WEBSOCKET,
                    "数据同步服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            wsChannel.setDescription("后台数据同步服务通知");
            wsChannel.setShowBadge(false);

            // SOS紧急求助通道
            NotificationChannel sosChannel = new NotificationChannel(
                    CHANNEL_SOS,
                    "SOS紧急求助",
                    NotificationManager.IMPORTANCE_HIGH
            );
            sosChannel.setDescription("SOS紧急求助通知");
            sosChannel.enableVibration(true);
            sosChannel.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500});

            // 提醒通知通道
            NotificationChannel alertChannel = new NotificationChannel(
                    CHANNEL_ALERT,
                    "使用提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            alertChannel.setDescription("使用时长提醒和限制通知");

            // 快捷控制通道
            NotificationChannel controlChannel = new NotificationChannel(
                    CHANNEL_CONTROL,
                    "快捷控制",
                    NotificationManager.IMPORTANCE_LOW
            );
            controlChannel.setDescription("通知栏快捷控制按钮");

            // 儿童端守护服务通道
            NotificationChannel guardianChannel = new NotificationChannel(
                    CHANNEL_GUARDIAN,
                    "守护服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            guardianChannel.setDescription("后台守护服务通知");

            manager.createNotificationChannel(wsChannel);
            manager.createNotificationChannel(sosChannel);
            manager.createNotificationChannel(alertChannel);
            manager.createNotificationChannel(controlChannel);
            manager.createNotificationChannel(guardianChannel);
        }
    }

    private void initPreferences() {
        SharedPreferences prefs = getSharedPreferences("guardian_prefs", MODE_PRIVATE);
        if (!prefs.contains("device_id")) {
            String androidId = Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.ANDROID_ID);
            String deviceId = (androidId != null && !androidId.isEmpty()) ?
                    androidId.substring(0, Math.min(8, androidId.length())) :
                    UUID.randomUUID().toString().substring(0, 8);

            String bindCode = generateSecureBindCode();

            prefs.edit()
                    .putString("device_id", deviceId)
                    .putString("bind_code", bindCode)
                    .putBoolean("service_running", false)
                    .putBoolean("device_locked", false)
                    .apply();
        }
    }

    /** 使用 SecureRandom 生成不可预测的 6 位绑定码 */
    private String generateSecureBindCode() {
        SecureRandom sr = new SecureRandom();
        int code = sr.nextInt(900000) + 100000;
        return String.valueOf(code);
    }
}
