package com.guardianstar.parent.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.guardianstar.parent.R;
import com.guardianstar.parent.data.AppDatabase;
import com.guardianstar.parent.data.WebFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebFilterActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView emptyText;
    private FloatingActionButton fabAdd;
    private List<WebFilter> filterList;
    private WebFilterAdapter adapter;
    private String deviceId = "default_device";
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_filter);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("网页过滤");
        }

        if (getIntent().hasExtra("deviceId")) {
            deviceId = getIntent().getStringExtra("deviceId");
        }

        initViews();
        loadFilters();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        emptyText = findViewById(R.id.emptyText);
        fabAdd = findViewById(R.id.fabAdd);

        filterList = new ArrayList<>();
        adapter = new WebFilterAdapter(filterList, this::deleteFilter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> showAddFilterDialog());
    }

    private void loadFilters() {
        dbExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            List<WebFilter> filters = db.webFilterDao().getFiltersForDevice(deviceId);
            runOnUiThread(() -> {
                filterList.clear();
                filterList.addAll(filters);
                adapter.notifyDataSetChanged();
                updateEmptyView();
            });
        });
    }

    private void showAddFilterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("添加网页过滤");

        EditText input = new EditText(this);
        input.setHint("输入网址或关键词（如 youtube.com）");
        input.setSingleLine(true);
        builder.setView(input);

        builder.setPositiveButton("添加", (dialog, which) -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                WebFilter filter = new WebFilter();
                filter.setDeviceId(deviceId);
                filter.setUrl(text);
                filter.setCategory(text.startsWith("http") ? "url" : "keyword");
                filter.setBlocked(true);
                filter.setCreateTime(System.currentTimeMillis());

                dbExecutor.execute(() -> {
                    AppDatabase db = AppDatabase.getInstance(this);
                    db.webFilterDao().insert(filter);
                    runOnUiThread(() -> {
                        filterList.add(0, filter);
                        adapter.notifyItemInserted(0);
                        updateEmptyView();
                        notifyFilterChanged();
                        Toast.makeText(this, "已添加", Toast.LENGTH_SHORT).show();
                    });
                });
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void deleteFilter(WebFilter filter) {
        dbExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            db.webFilterDao().delete(filter);
            runOnUiThread(() -> {
                filterList.remove(filter);
                adapter.notifyDataSetChanged();
                updateEmptyView();
                notifyFilterChanged();
            });
        });
    }

    private void notifyFilterChanged() {
        Intent wsIntent = new Intent(this, com.guardianstar.parent.service.WebSocketService.class);
        wsIntent.setAction("ACTION_UPDATE_WEB_FILTER");
        wsIntent.putExtra("deviceId", deviceId);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(wsIntent);
        } else {
            startService(wsIntent);
        }
    }

    private void updateEmptyView() {
        if (filterList.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
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

    static class WebFilterAdapter extends RecyclerView.Adapter<WebFilterAdapter.VH> {
        private final List<WebFilter> items;
        private final OnDeleteListener listener;

        interface OnDeleteListener { void onDelete(WebFilter filter); }

        WebFilterAdapter(List<WebFilter> items, OnDeleteListener listener) {
            this.items = items; this.listener = listener;
        }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_web_filter, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            WebFilter f = items.get(pos);
            String label = f.getUrl();
            h.text.setText(label);
            h.deleteBtn.setOnClickListener(v -> { if (listener != null) listener.onDelete(f); });
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView text; ImageButton deleteBtn;
            VH(View v) {
                super(v);
                text = v.findViewById(R.id.tvFilterText);
                deleteBtn = v.findViewById(R.id.btnDeleteFilter);
            }
        }
    }
}
