package com.guardianstar.parent.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.guardianstar.parent.R;
import com.guardianstar.parent.data.AppDatabase;
import com.guardianstar.parent.network.ApiClient;
import com.guardianstar.parent.service.WebSocketService;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DeviceDetailActivity extends AppCompatActivity {

    private TextView deviceName, deviceModel, deviceStatus, batteryLevel, tvDeviceId, tvBindCode;
    private Button btnLock, btnUnlock;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_detail);

        deviceId = getIntent().getStringExtra("deviceId");

        initViews();
        loadDeviceInfo();
        setupButtons();
    }

    private void initViews() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("设备详情");

        deviceName = findViewById(R.id.deviceName);
        deviceModel = findViewById(R.id.deviceModel);
        deviceStatus = findViewById(R.id.deviceStatus);
        batteryLevel = findViewById(R.id.batteryLevel);
        tvDeviceId = findViewById(R.id.tvDeviceId);
        tvBindCode = findViewById(R.id.tvBindCode);
        btnLock = findViewById(R.id.btnLock);
        btnUnlock = findViewById(R.id.btnUnlock);

        SharedPreferences prefs = getSharedPreferences("guardian_prefs", MODE_PRIVATE);
        if (tvDeviceId != null) {
            tvDeviceId.setText("设备ID: " + (deviceId != null ? deviceId : prefs.getString("device_id", "")));
        }
        if (tvBindCode != null) {
            tvBindCode.setText("绑定码: " + prefs.getString("bind_code", ""));
        }
    }

    private void loadDeviceInfo() {
        // 从 Room DB 加载基本信息
        AppDatabase db = AppDatabase.getInstance(this);
        db.deviceDao().getDeviceById(deviceId).observe(this, device -> {
            if (device != null) {
                deviceName.setText(device.getDeviceName());
                deviceModel.setText(device.getDeviceModel());
            }
        });

        // 从服务器 API 加载实时状态
        ApiClient.getApiService().getDashboard(deviceId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> r) {
                if (r.isSuccessful() && r.body() != null) {
                    boolean online = Boolean.TRUE.equals(r.body().get("is_online"));
                    deviceStatus.setText(online ? "在线" : "离线");
                    deviceStatus.setTextColor(getResources().getColor(online ? R.color.online : R.color.offline));
                    if (online) {
                        int battery = ((Number) r.body().getOrDefault("battery_level", 0)).intValue();
                        batteryLevel.setText("电量: " + battery + "%");
                        batteryLevel.setVisibility(View.VISIBLE);
                    } else {
                        batteryLevel.setText("电量: --");
                    }
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.w("DeviceDetail", "API失败: " + t.getMessage());
            }
        });
    }

    private void setupButtons() {
        btnLock.setOnClickListener(v -> {
            new Thread(() -> {
                AppDatabase.getInstance(this).deviceDao().updateDeviceLockStatus(deviceId, true);
            }).start();
            sendCommand("ACTION_LOCK");
            Toast.makeText(this, "锁定指令已发送", Toast.LENGTH_SHORT).show();
        });

        btnUnlock.setOnClickListener(v -> {
            new Thread(() -> {
                AppDatabase.getInstance(this).deviceDao().updateDeviceLockStatus(deviceId, false);
            }).start();
            sendCommand("ACTION_UNLOCK");
            Toast.makeText(this, "解锁指令已发送", Toast.LENGTH_SHORT).show();
        });
    }

    private void sendCommand(String action) {
        Intent si = new Intent(this, WebSocketService.class);
        si.setAction(action);
        si.putExtra("deviceId", deviceId);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(si);
        } else {
            startService(si);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
