package com.guardianstar.parent.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.guardianstar.parent.R;
import com.guardianstar.parent.data.AppDatabase;
import com.guardianstar.parent.data.AppLimit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatisticsActivity extends AppCompatActivity {

    private BarChart barChart;
    private TextView tvTotal, tvAvg, tvExceeded, emptyText;
    private String deviceId = "default_device";
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("使用统计");
        }

        if (getIntent().hasExtra("deviceId")) {
            deviceId = getIntent().getStringExtra("deviceId");
        }

        initViews();
        loadData();
    }

    private void initViews() {
        barChart = findViewById(R.id.barChart);
        tvTotal = findViewById(R.id.tvTotalUsage);
        tvAvg = findViewById(R.id.tvAvgUsage);
        tvExceeded = findViewById(R.id.tvExceeded);
        emptyText = findViewById(R.id.emptyText);

        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);
        barChart.setPinchZoom(false);
        barChart.getAxisLeft().setDrawGridLines(true);
        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisRight().setEnabled(false);
        barChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        barChart.getXAxis().setDrawGridLines(false);
        barChart.getXAxis().setGranularity(1f);
        barChart.getLegend().setEnabled(false);
    }

    private void loadData() {
        dbExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            List<AppLimit> limits = db.appLimitDao().getLimitsForDevice(deviceId);
            long total = db.appLimitDao().getTotalUsageToday(deviceId);
            int exceededCount = db.appLimitDao().getExceededCount(deviceId);

            runOnUiThread(() -> {
                if (limits.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                    barChart.setVisibility(View.GONE);
                    tvTotal.setText("0 分钟");
                    tvAvg.setText("0 分钟");
                    tvExceeded.setText("0");
                    return;
                }

                emptyText.setVisibility(View.GONE);
                barChart.setVisibility(View.VISIBLE);

                tvTotal.setText(total + " 分钟");
                long avg = limits.isEmpty() ? 0 : total / limits.size();
                tvAvg.setText(avg + " 分钟");
                tvExceeded.setText(String.valueOf(exceededCount));

                // 构建柱状图数据（最多显示前6个应用）
                int count = Math.min(limits.size(), 6);
                List<BarEntry> entries = new ArrayList<>();
                List<String> labels = new ArrayList<>();

                for (int i = 0; i < count; i++) {
                    AppLimit app = limits.get(i);
                    entries.add(new BarEntry(i, app.getUsedMinutesToday()));
                    labels.add(app.getAppName());
                }

                BarDataSet dataSet = new BarDataSet(entries, "使用时间（分钟）");
                dataSet.setColor(Color.parseColor("#5B8DEF"));
                dataSet.setValueTextSize(10f);
                dataSet.setValueTextColor(Color.parseColor("#7B8CA8"));

                BarData barData = new BarData(dataSet);
                barData.setBarWidth(0.6f);

                barChart.setData(barData);
                barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
                barChart.animateY(800);
                barChart.invalidate();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdown();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
