package com.guardianstar.parent.ui;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.guardianstar.parent.R;
import com.guardianstar.parent.adapter.ScheduleAdapter;
import com.guardianstar.parent.data.AppDatabase;
import com.guardianstar.parent.data.Schedule;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScheduleActivity extends AppCompatActivity {

    private static final String TAG = "ScheduleActivity";
    private RecyclerView recyclerView;
    private ScheduleAdapter adapter;
    private List<Schedule> scheduleList;
    private TextView emptyText, errorText;
    private ProgressBar loadingBar;
    private FloatingActionButton fabAdd;
    private String deviceId = "default_device";
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("定时任务");
        }

        if (getIntent().hasExtra("deviceId")) {
            deviceId = getIntent().getStringExtra("deviceId");
        }

        initViews();
        setupRecyclerView();
        loadSchedulesFromDb();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        emptyText = findViewById(R.id.emptyText);
        loadingBar = findViewById(R.id.loadingBar);
        errorText = findViewById(R.id.errorText);
        fabAdd = findViewById(R.id.fabAdd);

        fabAdd.setOnClickListener(v -> {
            if (isFinishing() || isDestroyed()) return;
            showAddScheduleDialog();
        });
    }

    private void setupRecyclerView() {
        scheduleList = new ArrayList<>();
        adapter = new ScheduleAdapter(this, scheduleList);
        adapter.setOnItemClickListener(new ScheduleAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                if (position < scheduleList.size()) showEditScheduleDialog(scheduleList.get(position));
            }
            @Override
            public void onToggleClick(int position, boolean isChecked) {
                if (position < scheduleList.size()) {
                    Schedule s = scheduleList.get(position);
                    s.setEnabled(isChecked);
                    saveScheduleToDb(s);
                    notifySettingsChanged();
                }
            }
            @Override
            public void onDeleteClick(int position) {
                if (position < scheduleList.size()) {
                    Schedule removed = scheduleList.remove(position);
                    adapter.notifyItemRemoved(position);
                    updateEmptyView();
                    deleteScheduleFromDb(removed);
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

    private void loadSchedulesFromDb() {
        showLoading();
        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                List<Schedule> schedules = db.scheduleDao().getSchedulesForDevice(deviceId);
                if (schedules == null || schedules.isEmpty()) {
                    insertSampleSchedules(db);
                    schedules = db.scheduleDao().getSchedulesForDevice(deviceId);
                }
                final List<Schedule> finalSchedules = schedules != null ? schedules : new ArrayList<>();
                runOnUiThread(() -> {
                    scheduleList.clear();
                    scheduleList.addAll(finalSchedules);
                    adapter.notifyDataSetChanged();
                    showContent();
                });
            } catch (Exception e) {
                Log.e(TAG, "加载定时任务失败", e);
                final String msg = e.getMessage();
                runOnUiThread(() -> showError(msg != null ? msg : "未知错误"));
            }
        });
    }

    private void insertSampleSchedules(AppDatabase db) {
        try {
            Schedule s1 = new Schedule(deviceId, "睡觉时间", Schedule.TYPE_LOCK);
            s1.setStartHour(22); s1.setStartMinute(0); s1.setEndHour(7); s1.setEndMinute(0);
            s1.setRepeatMonday(true); s1.setRepeatTuesday(true); s1.setRepeatWednesday(true);
            s1.setRepeatThursday(true); s1.setRepeatFriday(true); s1.setEnabled(true);

            Schedule s2 = new Schedule(deviceId, "学习时间", Schedule.TYPE_STUDY);
            s2.setStartHour(19); s2.setStartMinute(0); s2.setEndHour(21); s2.setEndMinute(0);
            s2.setRepeatMonday(true); s2.setRepeatTuesday(true); s2.setRepeatWednesday(true);
            s2.setRepeatThursday(true); s2.setEnabled(true);

            Schedule s3 = new Schedule(deviceId, "周末锁定", Schedule.TYPE_LOCK);
            s3.setStartHour(10); s3.setStartMinute(0); s3.setEndHour(16); s3.setEndMinute(0);
            s3.setRepeatSaturday(true); s3.setRepeatSunday(true); s3.setEnabled(false);

            db.scheduleDao().insert(s1);
            db.scheduleDao().insert(s2);
            db.scheduleDao().insert(s3);
        } catch (Exception e) {
            Log.e(TAG, "插入示例任务失败", e);
        }
    }

    private void showAddScheduleDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_schedule, null);
        final TextView tvStartTime = view.findViewById(R.id.tvStartTime);
        final TextView tvEndTime = view.findViewById(R.id.tvEndTime);
        final Spinner spinnerType = view.findViewById(R.id.spinnerType);
        final CheckBox[] cbs = {
            view.findViewById(R.id.cbMon), view.findViewById(R.id.cbTue), view.findViewById(R.id.cbWed),
            view.findViewById(R.id.cbThu), view.findViewById(R.id.cbFri), view.findViewById(R.id.cbSat),
            view.findViewById(R.id.cbSun)
        };

        Calendar now = Calendar.getInstance();
        tvStartTime.setOnClickListener(v -> showTimePickerDialog(tvStartTime, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)));
        tvEndTime.setOnClickListener(v -> showTimePickerDialog(tvEndTime, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)));

        new AlertDialog.Builder(this)
            .setTitle("添加定时任务")
            .setView(view)
            .setPositiveButton("添加", (dialog, which) -> {
                String startTime = tvStartTime.getText().toString();
                String endTime = tvEndTime.getText().toString();
                if ("选择时间".equals(startTime) || "选择时间".equals(endTime)) {
                    Toast.makeText(this, "请选择时间", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    String[] sp = startTime.split(":"), ep = endTime.split(":");
                    Schedule s = new Schedule(deviceId, "定时任务", spinnerType.getSelectedItemPosition());
                    s.setStartHour(Integer.parseInt(sp[0])); s.setStartMinute(Integer.parseInt(sp[1]));
                    s.setEndHour(Integer.parseInt(ep[0])); s.setEndMinute(Integer.parseInt(ep[1]));
                    s.setRepeatMonday(cbs[0].isChecked()); s.setRepeatTuesday(cbs[1].isChecked());
                    s.setRepeatWednesday(cbs[2].isChecked()); s.setRepeatThursday(cbs[3].isChecked());
                    s.setRepeatFriday(cbs[4].isChecked()); s.setRepeatSaturday(cbs[5].isChecked());
                    s.setRepeatSunday(cbs[6].isChecked()); s.setEnabled(true);
                    scheduleList.add(0, s);
                    adapter.notifyItemInserted(0);
                    updateEmptyView();
                    saveScheduleToDb(s);
                    notifySettingsChanged();
                } catch (Exception e) {
                    Toast.makeText(this, "时间格式错误", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showEditScheduleDialog(final Schedule schedule) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_schedule, null);
        final TextView tvStartTime = view.findViewById(R.id.tvStartTime);
        final TextView tvEndTime = view.findViewById(R.id.tvEndTime);
        final Spinner spinnerType = view.findViewById(R.id.spinnerType);
        final CheckBox[] cbs = {
            view.findViewById(R.id.cbMon), view.findViewById(R.id.cbTue), view.findViewById(R.id.cbWed),
            view.findViewById(R.id.cbThu), view.findViewById(R.id.cbFri), view.findViewById(R.id.cbSat),
            view.findViewById(R.id.cbSun)
        };

        tvStartTime.setText(schedule.getStartTimeString());
        tvEndTime.setText(schedule.getEndTimeString());
        spinnerType.setSelection(schedule.getType());
        cbs[0].setChecked(schedule.isRepeatMonday()); cbs[1].setChecked(schedule.isRepeatTuesday());
        cbs[2].setChecked(schedule.isRepeatWednesday()); cbs[3].setChecked(schedule.isRepeatThursday());
        cbs[4].setChecked(schedule.isRepeatFriday()); cbs[5].setChecked(schedule.isRepeatSaturday());
        cbs[6].setChecked(schedule.isRepeatSunday());

        tvStartTime.setOnClickListener(v -> showTimePickerDialog(tvStartTime, schedule.getStartHour(), schedule.getStartMinute()));
        tvEndTime.setOnClickListener(v -> showTimePickerDialog(tvEndTime, schedule.getEndHour(), schedule.getEndMinute()));

        new AlertDialog.Builder(this)
            .setTitle("编辑定时任务")
            .setView(view)
            .setPositiveButton("保存", (dialog, which) -> {
                String startTime = tvStartTime.getText().toString();
                String endTime = tvEndTime.getText().toString();
                if ("选择时间".equals(startTime) || "选择时间".equals(endTime)) return;
                try {
                    String[] sp = startTime.split(":"), ep = endTime.split(":");
                    schedule.setStartHour(Integer.parseInt(sp[0])); schedule.setStartMinute(Integer.parseInt(sp[1]));
                    schedule.setEndHour(Integer.parseInt(ep[0])); schedule.setEndMinute(Integer.parseInt(ep[1]));
                    schedule.setType(spinnerType.getSelectedItemPosition());
                    schedule.setRepeatMonday(cbs[0].isChecked()); schedule.setRepeatTuesday(cbs[1].isChecked());
                    schedule.setRepeatWednesday(cbs[2].isChecked()); schedule.setRepeatThursday(cbs[3].isChecked());
                    schedule.setRepeatFriday(cbs[4].isChecked()); schedule.setRepeatSaturday(cbs[5].isChecked());
                    schedule.setRepeatSunday(cbs[6].isChecked());
                    adapter.notifyItemChanged(scheduleList.indexOf(schedule));
                    saveScheduleToDb(schedule);
                    notifySettingsChanged();
                } catch (Exception e) {
                    Toast.makeText(this, "时间格式错误", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showTimePickerDialog(final TextView tv, int hour, int minute) {
        new TimePickerDialog(this, (v, h, m) -> tv.setText(String.format("%02d:%02d", h, m)), hour, minute, true).show();
    }

    private void updateEmptyView() {
        if (scheduleList == null || scheduleList.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void saveScheduleToDb(Schedule schedule) {
        dbExecutor.execute(() -> {
            try { AppDatabase.getInstance(this).scheduleDao().insert(schedule); }
            catch (Exception e) { Log.e(TAG, "保存任务失败", e); }
        });
    }

    private void deleteScheduleFromDb(Schedule schedule) {
        dbExecutor.execute(() -> {
            try { AppDatabase.getInstance(this).scheduleDao().delete(schedule); }
            catch (Exception e) { Log.e(TAG, "删除任务失败", e); }
        });
    }

    private void notifySettingsChanged() {
        try {
            Intent wsIntent = new Intent(this, com.guardianstar.parent.service.WebSocketService.class);
            wsIntent.setAction("ACTION_UPDATE_SCHEDULES");
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
