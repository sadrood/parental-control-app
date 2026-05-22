package com.guardianstar.parent.ui;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.guardianstar.parent.R;
import com.guardianstar.parent.service.GuardianService;

public class LockActivity extends AppCompatActivity {

    private static final String TAG = "LockActivity";
    private TextView tvMessage, tvTimeLeft;
    private CardView btnRequestUnlock, btnEmergency;
    private BroadcastReceiver unlockReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        initViews();
        setupUnlockReceiver();

        // 屏幕固定锁定，防止通过手势退出
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                startLockTask();
            } catch (Exception e) {
                Log.w(TAG, "屏幕固定失败: " + e.getMessage());
            }
        }
    }

    private void initViews() {
        tvMessage = findViewById(R.id.tvMessage);
        tvTimeLeft = findViewById(R.id.tvTimeLeft);
        btnRequestUnlock = findViewById(R.id.btnRequestUnlock);
        btnEmergency = findViewById(R.id.btnEmergency);

        tvMessage.setText("使用时间已到，请休息一下");
        tvTimeLeft.setText("等待家长解锁...");

        btnRequestUnlock.setOnClickListener(v -> requestUnlock());
        btnEmergency.setOnClickListener(v -> makeEmergencyCall());
    }

    private void setupUnlockReceiver() {
        unlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.guardianstar.UNLOCK".equals(intent.getAction())) {
                    try { stopLockTask(); } catch (Exception ignored) {}
                    finish();
                }
            }
        };
        registerReceiver(unlockReceiver, new IntentFilter("com.guardianstar.UNLOCK"));
    }

    private void requestUnlock() {
        Intent intent = new Intent("com.guardianstar.REQUEST_UNLOCK");
        sendBroadcast(intent);
        tvTimeLeft.setText("已发送解锁请求，请等待家长回应");
        btnRequestUnlock.setAlpha(0.5f);
        btnRequestUnlock.setClickable(false);
    }

    private void makeEmergencyCall() {
        // 触发 SOS 上报（锁屏状态下电源键被拦截，通过按钮触发）
        Intent sosIntent = new Intent("com.guardianstar.SOS_TRIGGERED");
        sosIntent.setPackage(getPackageName());
        sendBroadcast(sosIntent);

        // 弹窗告知
        new android.app.AlertDialog.Builder(this)
                .setTitle("🚨 SOS 紧急求助")
                .setMessage("SOS 已触发，家长将收到通知。是否拨打紧急电话？")
                .setPositiveButton("拨打", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onBackPressed() {}

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_APP_SWITCH
                || keyCode == KeyEvent.KEYCODE_BACK) {
            // 按 Home 键后重新拉回锁屏
            new android.os.Handler().postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    moveTaskToBack(true);
                    Intent restart = new Intent(this, LockActivity.class);
                    restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(restart);
                }
            }, 500);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // 用户切到后台时，延迟重新拉回锁屏
        new android.os.Handler().postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                Intent restart = new Intent(this, LockActivity.class);
                restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(restart);
            }
        }, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unlockReceiver != null) {
            try { unregisterReceiver(unlockReceiver); } catch (Exception ignored) {}
        }
        // 被系统杀死后重启 GuardianService
        Intent serviceIntent = new Intent(this, GuardianService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
}
