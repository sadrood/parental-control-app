package com.guardianstar.parent.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.guardianstar.parent.R;
import com.guardianstar.parent.service.GuardianService;
import com.guardianstar.parent.service.UsageMonitorService;

public class ChildMainActivity extends AppCompatActivity {

    private TextView tvDeviceId, tvBindCode, tvServiceStatus;
    private Switch switchService, switchUsageMonitor;
    private CardView btnTestLock, btnSendSOS, btnPermissionGuide;
    private CardView cardPermissionGuide;
    private View statusDot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_main);

        initViews();
        displayDeviceInfo();
        setupButtons();
        checkPermissions();

        // 自动启动守护服务，确保连接服务器
        autoStartGuardianService();
    }

    private void initViews() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("守护星-儿童端");
        }

        tvDeviceId = findViewById(R.id.tvDeviceId);
        tvBindCode = findViewById(R.id.tvBindCode);
        tvServiceStatus = findViewById(R.id.tvServiceStatus);
        switchService = findViewById(R.id.switchService);
        switchUsageMonitor = findViewById(R.id.switchUsageMonitor);
        btnTestLock = findViewById(R.id.btnTestLock);
        btnSendSOS = findViewById(R.id.btnSendSOS);
        btnPermissionGuide = findViewById(R.id.btnPermissionGuide);
        cardPermissionGuide = findViewById(R.id.cardPermissionGuide);
        statusDot = findViewById(R.id.statusDot);
    }

    private void displayDeviceInfo() {
        SharedPreferences prefs = getSharedPreferences("guardian_prefs", MODE_PRIVATE);
        String deviceId = prefs.getString("device_id", generateDeviceId());
        String bindCode = prefs.getString("bind_code", "123456");

        if (!prefs.contains("device_id")) {
            prefs.edit()
                    .putString("device_id", deviceId)
                    .putString("bind_code", bindCode)
                    .apply();
        }

        tvDeviceId.setText(deviceId);
        tvBindCode.setText(bindCode);
    }

    private String generateDeviceId() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.isEmpty()) {
            androidId = java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        return androidId.substring(0, Math.min(8, androidId.length()));
    }

    private void setupButtons() {
        switchService.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startGuardianService();
            } else {
                stopGuardianService();
            }
        });

        switchUsageMonitor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                requestUsageStatsPermission();
            }
        });

        btnTestLock.setOnClickListener(v -> {
            Intent lockIntent = new Intent(this, LockActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(lockIntent);
        });

        btnSendSOS.setOnClickListener(v -> sendSOSAlert());

        btnPermissionGuide.setOnClickListener(v -> showPermissionGuide());
    }

    private void startGuardianService() {
        Intent serviceIntent = new Intent(this, GuardianService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        tvServiceStatus.setText("运行中");
        tvServiceStatus.setTextColor(getResources().getColor(R.color.success));
        statusDot.setAlpha(1.0f);
        Toast.makeText(this, "守护服务已启动", Toast.LENGTH_SHORT).show();
    }

    private void stopGuardianService() {
        Intent serviceIntent = new Intent(this, GuardianService.class);
        stopService(serviceIntent);
        tvServiceStatus.setText("已停止");
        tvServiceStatus.setTextColor(getResources().getColor(R.color.text_secondary));
        statusDot.setAlpha(0.4f);
        Toast.makeText(this, "守护服务已停止", Toast.LENGTH_SHORT).show();
    }

    private void requestUsageStatsPermission() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, 1001);
        Toast.makeText(this, "请在设置中允许使用情况访问", Toast.LENGTH_LONG).show();
    }

    private void showPermissionGuide() {
        cardPermissionGuide.setVisibility(cardPermissionGuide.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void sendSOSAlert() {
        Toast.makeText(this, "SOS求助信号已发送", Toast.LENGTH_SHORT).show();
    }

    private void autoStartGuardianService() {
        SharedPreferences prefs = getSharedPreferences("guardian_prefs", MODE_PRIVATE);
        boolean isRunning = prefs.getBoolean("service_running", false);
        if (!isRunning) {
            startGuardianService();
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences("guardian_prefs", MODE_PRIVATE);
        boolean isServiceRunning = prefs.getBoolean("service_running", false);
        switchService.setChecked(isServiceRunning);
        tvServiceStatus.setText(isServiceRunning ? "运行中" : "已停止");
        tvServiceStatus.setTextColor(getResources().getColor(
                isServiceRunning ? R.color.success : R.color.text_secondary));
        statusDot.setAlpha(isServiceRunning ? 1.0f : 0.4f);
    }
}
