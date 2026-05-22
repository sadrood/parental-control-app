package com.guardianstar.parent.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.guardianstar.parent.R;
import com.guardianstar.parent.data.AppDatabase;
import com.guardianstar.parent.data.AppLimit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppUsageActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvTotalUsage, tvAppCount, emptyText;
    private UsageAdapter adapter;
    private String deviceId = "default_device";
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_usage);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("应用使用统计");
        }

        if (getIntent().hasExtra("deviceId")) {
            deviceId = getIntent().getStringExtra("deviceId");
        }

        initViews();
        loadData();
    }

    private void initViews() {
        tvTotalUsage = findViewById(R.id.tvTotalUsage);
        tvAppCount = findViewById(R.id.tvAppCount);
        recyclerView = findViewById(R.id.recyclerView);
        emptyText = findViewById(R.id.emptyText);

        adapter = new UsageAdapter(new ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadData() {
        dbExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            List<AppLimit> limits = db.appLimitDao().getLimitsForDevice(deviceId);
            long totalMinutes = db.appLimitDao().getTotalUsageToday(deviceId);

            runOnUiThread(() -> {
                tvTotalUsage.setText(totalMinutes + " 分钟");
                tvAppCount.setText(String.valueOf(limits.size()));
                adapter.setData(limits);

                if (limits.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    emptyText.setVisibility(View.VISIBLE);
                } else {
                    recyclerView.setVisibility(View.VISIBLE);
                    emptyText.setVisibility(View.GONE);
                }
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

    static class UsageAdapter extends RecyclerView.Adapter<UsageAdapter.VH> {
        private List<AppLimit> items;

        UsageAdapter(List<AppLimit> items) { this.items = items; }

        void setData(List<AppLimit> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_usage_row, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            AppLimit app = items.get(pos);
            h.tvName.setText(app.getAppName());
            h.tvTime.setText(app.getUsedMinutesToday() + " / " + app.getDailyLimitMinutes() + " 分钟");

            int percent = app.getUsagePercent();
            h.progress.setProgress(Math.min(percent, 100));

            int color;
            if (percent >= 100) color = ContextCompat.getColor(h.itemView.getContext(), R.color.error);
            else if (percent >= 80) color = ContextCompat.getColor(h.itemView.getContext(), R.color.warning);
            else color = ContextCompat.getColor(h.itemView.getContext(), R.color.success);
            h.progress.setProgressTintList(ColorStateList.valueOf(color));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvTime;
            ProgressBar progress;
            VH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvAppName);
                tvTime = v.findViewById(R.id.tvAppTime);
                progress = v.findViewById(R.id.progressBar);
            }
        }
    }
}
