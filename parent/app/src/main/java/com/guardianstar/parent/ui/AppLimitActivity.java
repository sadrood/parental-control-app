package com.guardianstar.parent.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.guardianstar.parent.R;
import com.guardianstar.parent.adapter.AppLimitAdapter;
import com.guardianstar.parent.data.AppDatabase;
import com.guardianstar.parent.data.AppLimit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppLimitActivity extends AppCompatActivity {

    private static final String TAG = "AppLimitActivity";
    private RecyclerView recyclerView;
    private AppLimitAdapter adapter;
    private List<AppLimit> limitList;
    private TextView emptyText, errorText;
    private ProgressBar loadingBar;
    private FloatingActionButton fabAdd;
    private String deviceId = "default_device";
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_limit);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("应用时长限制");
        }

        if (getIntent().hasExtra("deviceId")) {
            deviceId = getIntent().getStringExtra("deviceId");
        }

        initViews();
        setupRecyclerView();
        loadLimitsFromDb();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        emptyText = findViewById(R.id.emptyText);
        fabAdd = findViewById(R.id.fabAdd);
        loadingBar = findViewById(R.id.loadingBar);
        errorText = findViewById(R.id.errorText);

        fabAdd.setOnClickListener(v -> {
            if (isFinishing() || isDestroyed()) return;
            showAddLimitDialog();
        });
    }

    private void setupRecyclerView() {
        limitList = new ArrayList<>();
        adapter = new AppLimitAdapter(this, limitList);
        adapter.setOnItemClickListener(new AppLimitAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                if (position < limitList.size()) showEditLimitDialog(limitList.get(position));
            }
            @Override
            public void onToggleClick(int position, boolean isChecked) {
                if (position < limitList.size()) {
                    AppLimit limit = limitList.get(position);
                    limit.setLimitEnabled(isChecked);
                    saveLimitToDb(limit);
                    notifySettingsChanged();
                }
            }
            @Override
            public void onDeleteClick(int position) {
                if (position < limitList.size()) {
                    AppLimit removed = limitList.remove(position);
                    adapter.notifyItemRemoved(position);
                    updateEmptyView();
                    deleteLimitFromDb(removed);
                    notifySettingsChanged();
                }
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void showLoading() {
        loadingBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        if (errorText != null) errorText.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        loadingBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        if (errorText != null) {
            errorText.setText("加载失败\n" + msg);
            errorText.setVisibility(View.VISIBLE);
        }
        Toast.makeText(this, "数据加载失败", Toast.LENGTH_SHORT).show();
    }

    private void showContent() {
        loadingBar.setVisibility(View.GONE);
        if (errorText != null) errorText.setVisibility(View.GONE);
        updateEmptyView();
    }

    private void loadLimitsFromDb() {
        showLoading();
        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                List<AppLimit> limits = db.appLimitDao().getLimitsForDevice(deviceId);
                if (limits == null || limits.isEmpty()) {
                    insertSampleLimits(db);
                    limits = db.appLimitDao().getLimitsForDevice(deviceId);
                }
                final List<AppLimit> finalLimits = limits != null ? limits : new ArrayList<>();
                runOnUiThread(() -> {
                    limitList.clear();
                    limitList.addAll(finalLimits);
                    adapter.notifyDataSetChanged();
                    showContent();
                });
            } catch (Exception e) {
                Log.e(TAG, "加载限制数据失败", e);
                final String msg = e.getMessage();
                runOnUiThread(() -> showError(msg != null ? msg : "未知错误"));
            }
        });
    }

    private void insertSampleLimits(AppDatabase db) {
        try {
            AppLimit[] samples = {
                new AppLimit(deviceId, "com.tencent.mm", "微信", 60),
                new AppLimit(deviceId, "com.tencent.mobileqq", "QQ", 90),
                new AppLimit(deviceId, "com.ss.android.ugc.aweme", "抖音", 30),
                new AppLimit(deviceId, "com.tencent.tmgp.sgame", "王者荣耀", 60),
                new AppLimit(deviceId, "tv.danmaku.bili", "哔哩哔哩", 120)
            };
            samples[0].setUsedMinutesToday(45);
            samples[1].setUsedMinutesToday(78);
            samples[2].setUsedMinutesToday(35);
            samples[3].setUsedMinutesToday(20);
            samples[4].setUsedMinutesToday(90);
            for (AppLimit limit : samples) {
                db.appLimitDao().insert(limit);
            }
        } catch (Exception e) {
            Log.e(TAG, "插入示例数据失败", e);
        }
    }

    private void showAddLimitDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_limit, null);
        final EditText etAppName = view.findViewById(R.id.etAppName);
        final EditText etPackageName = view.findViewById(R.id.etPackageName);
        final EditText etLimitMinutes = view.findViewById(R.id.etLimitMinutes);
        final Switch switchWhitelist = view.findViewById(R.id.switchWhitelist);

        new AlertDialog.Builder(this)
            .setTitle("添加应用限制")
            .setView(view)
            .setPositiveButton("添加", (dialog, which) -> {
                String appName = etAppName.getText().toString().trim();
                String packageName = etPackageName.getText().toString().trim();
                String minutesStr = etLimitMinutes.getText().toString().trim();
                if (appName.isEmpty() || minutesStr.isEmpty()) return;
                try {
                    long minutes = Long.parseLong(minutesStr);
                    if (minutes <= 0) {
                        Toast.makeText(this, "时间必须大于0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    AppLimit limit = new AppLimit(deviceId, packageName, appName, minutes);
                    limit.setWhitelist(switchWhitelist.isChecked());
                    limitList.add(0, limit);
                    adapter.notifyItemInserted(0);
                    updateEmptyView();
                    saveLimitToDb(limit);
                    notifySettingsChanged();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showEditLimitDialog(final AppLimit limit) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_limit, null);
        final EditText etAppName = view.findViewById(R.id.etAppName);
        final EditText etPackageName = view.findViewById(R.id.etPackageName);
        final EditText etLimitMinutes = view.findViewById(R.id.etLimitMinutes);
        final Switch switchWhitelist = view.findViewById(R.id.switchWhitelist);

        etAppName.setText(limit.getAppName());
        etPackageName.setText(limit.getPackageName());
        etLimitMinutes.setText(String.valueOf(limit.getDailyLimitMinutes()));
        switchWhitelist.setChecked(limit.isWhitelist());

        new AlertDialog.Builder(this)
            .setTitle("编辑应用限制")
            .setView(view)
            .setPositiveButton("保存", (dialog, which) -> {
                String appName = etAppName.getText().toString().trim();
                String minutesStr = etLimitMinutes.getText().toString().trim();
                if (appName.isEmpty() || minutesStr.isEmpty()) return;
                try {
                    limit.setAppName(appName);
                    limit.setDailyLimitMinutes(Long.parseLong(minutesStr));
                    limit.setWhitelist(switchWhitelist.isChecked());
                    adapter.notifyItemChanged(limitList.indexOf(limit));
                    saveLimitToDb(limit);
                    notifySettingsChanged();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void updateEmptyView() {
        if (limitList == null || limitList.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void saveLimitToDb(AppLimit limit) {
        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                db.appLimitDao().insert(limit);
            } catch (Exception e) {
                Log.e(TAG, "保存限制失败", e);
            }
        });
    }

    private void deleteLimitFromDb(AppLimit limit) {
        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                db.appLimitDao().delete(limit);
            } catch (Exception e) {
                Log.e(TAG, "删除限制失败", e);
            }
        });
    }

    private void notifySettingsChanged() {
        try {
            Intent wsIntent = new Intent(this, com.guardianstar.parent.service.WebSocketService.class);
            wsIntent.setAction("ACTION_UPDATE_LIMITS");
            wsIntent.putExtra("deviceId", deviceId);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(wsIntent);
            } else {
                startService(wsIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "启动WebSocket服务失败", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdownNow();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
