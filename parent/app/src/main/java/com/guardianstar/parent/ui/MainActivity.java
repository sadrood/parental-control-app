package com.guardianstar.parent.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.guardianstar.parent.R;
import com.guardianstar.parent.network.ApiClient;
import com.guardianstar.parent.network.ApiService;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";
    private CardView cardDevices, cardAppUsage, cardAppLimits, cardWebFilter;
    private CardView cardSchedule, cardSOS, cardStatistics, cardBindDevice, cardReport;
    private TextView tvDeviceCount, tvOnlineCount, tvTodayUsage;
    private ApiService api;
    private String deviceId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        api = ApiClient.getApiService();
        SharedPreferences prefs = getSharedPreferences("guardian_prefs", MODE_PRIVATE);
        deviceId = prefs.getString("device_id", "");

        initViews();
        setupClickListeners();
        loadDashboardFromServer();
    }

    private void initViews() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("守护星");
        }

        cardDevices = findViewById(R.id.cardDevices);
        cardAppUsage = findViewById(R.id.cardAppUsage);
        cardAppLimits = findViewById(R.id.cardAppLimits);
        cardWebFilter = findViewById(R.id.cardWebFilter);
        cardSchedule = findViewById(R.id.cardSchedule);
        cardSOS = findViewById(R.id.cardSOS);
        cardStatistics = findViewById(R.id.cardStatistics);
        cardBindDevice = findViewById(R.id.cardBindDevice);
        cardReport = findViewById(R.id.cardReport);

        tvDeviceCount = findViewById(R.id.tvDeviceCount);
        tvOnlineCount = findViewById(R.id.tvOnlineCount);
        tvTodayUsage = findViewById(R.id.tvTodayUsage);

        if (cardReport != null) {
            cardReport.setOnClickListener(v -> triggerManualReport());
        }
    }

    private void setupClickListeners() {
        cardDevices.setOnClickListener(this);
        cardAppUsage.setOnClickListener(this);
        cardAppLimits.setOnClickListener(this);
        cardWebFilter.setOnClickListener(this);
        cardSchedule.setOnClickListener(this);
        cardSOS.setOnClickListener(this);
        cardStatistics.setOnClickListener(this);
        cardBindDevice.setOnClickListener(this);
    }

    private void loadDashboardFromServer() {
        if (deviceId.isEmpty()) {
            tvDeviceCount.setText("0");
            tvOnlineCount.setText("0");
            tvTodayUsage.setText("0m");
            return;
        }

        api.getDashboard(deviceId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> data = response.body();

                    boolean online = Boolean.TRUE.equals(data.get("is_online"));
                    double totalMin = ((Number) data.getOrDefault("today_usage_minutes", 0)).doubleValue();
                    int limitCount = ((Number) data.getOrDefault("limit_count", 0)).intValue();

                    tvOnlineCount.setText(online ? "1" : "0");
                    tvDeviceCount.setText(String.valueOf(limitCount));

                    if (totalMin >= 60) {
                        tvTodayUsage.setText(String.format("%dh %dm", (int)totalMin / 60, (int)totalMin % 60));
                    } else {
                        tvTodayUsage.setText((int)totalMin + "m");
                    }

                    Log.i(TAG, "仪表盘数据已加载: online=" + online + ", usage=" + totalMin + "m");
                } else {
                    tvDeviceCount.setText("--");
                    tvOnlineCount.setText("--");
                    tvTodayUsage.setText("--");
                    Log.w(TAG, "仪表盘加载失败: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                tvDeviceCount.setText("--");
                tvOnlineCount.setText("--");
                tvTodayUsage.setText("--");
                Log.e(TAG, "仪表盘网络错误: " + t.getMessage());
            }
        });
    }

    private void triggerManualReport() {
        if (deviceId.isEmpty()) { return; }
        Toast.makeText(this, "正在请求上报...", Toast.LENGTH_SHORT).show();
        api.triggerReport(deviceId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    boolean ok = Boolean.TRUE.equals(response.body().get("success"));
                    Toast.makeText(MainActivity.this, ok ? "已触发上报" : "设备不在线", Toast.LENGTH_SHORT).show();
                    if (ok) {
                        // 延迟刷新
                        new android.os.Handler().postDelayed(() -> loadDashboardFromServer(), 2000);
                    }
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        Intent intent = null;

        if (id == R.id.cardDevices) intent = new Intent(this, DeviceListActivity.class);
        else if (id == R.id.cardAppUsage) intent = new Intent(this, AppUsageActivity.class);
        else if (id == R.id.cardAppLimits) intent = new Intent(this, AppLimitActivity.class);
        else if (id == R.id.cardWebFilter) intent = new Intent(this, WebFilterActivity.class);
        else if (id == R.id.cardSchedule) intent = new Intent(this, ScheduleActivity.class);
        else if (id == R.id.cardSOS) { Toast.makeText(this, "SOS告警功能开发中", Toast.LENGTH_SHORT).show(); return; }
        else if (id == R.id.cardStatistics) intent = new Intent(this, StatisticsActivity.class);
        else if (id == R.id.cardBindDevice) intent = new Intent(this, BindDeviceActivity.class);

        if (intent != null) startActivity(intent);
    }

    private long lastRefreshMs = 0;
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;

    @Override
    protected void onResume() {
        super.onResume();
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs > REFRESH_INTERVAL_MS) {
            loadDashboardFromServer();
            lastRefreshMs = now;
        }
    }
}
