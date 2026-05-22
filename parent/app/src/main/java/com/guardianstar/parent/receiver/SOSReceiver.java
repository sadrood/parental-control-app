package com.guardianstar.parent.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

public class SOSReceiver extends BroadcastReceiver {

    private static int powerButtonCount = 0;
    private static final long TIMEOUT = 3000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable resetRunnable = () -> powerButtonCount = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            powerButtonCount++;
            handler.removeCallbacks(resetRunnable);

            if (powerButtonCount >= 3) {
                triggerSOS(context);
                powerButtonCount = 0;
            } else {
                handler.postDelayed(resetRunnable, TIMEOUT);
            }
        }
    }

    private void triggerSOS(Context context) {
        // 1. 振动提醒（2000ms，兼容全版本）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vm != null) {
                Vibrator v = vm.getDefaultVibrator();
                if (v != null) v.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Vibrator v = context.getSystemService(Vibrator.class);
            if (v != null) v.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            @SuppressWarnings("deprecation")
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(2000);
        }

        // 2. 显示 SOS 通知
        showSOSNotification(context);

        // 3. 广播通知 GuardianService 通过 WebSocket 上报
        Intent sosIntent = new Intent("com.guardianstar.SOS_TRIGGERED");
        sosIntent.setPackage(context.getPackageName());
        context.sendBroadcast(sosIntent);
    }

    private void showSOSNotification(Context context) {
        String channelId = "child_sos_channel";
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    channelId, "SOS紧急求助", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("儿童端 SOS 紧急求助通知");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500});
            nm.createNotificationChannel(ch);
        }

        Notification n = new androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setContentTitle("🚨 SOS 紧急求助已触发")
                .setContentText("已向家长发送求助信号，请保持手机畅通")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setVibrate(new long[]{0, 500, 200, 500, 200, 500})
                .build();
        nm.notify(3001, n);
    }
}
