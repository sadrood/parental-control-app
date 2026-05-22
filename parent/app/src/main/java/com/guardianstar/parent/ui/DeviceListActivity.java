package com.guardianstar.parent.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.guardianstar.parent.R;
import com.guardianstar.parent.adapter.DeviceAdapter;
import com.guardianstar.parent.data.Device;
import com.guardianstar.parent.network.ApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DeviceListActivity extends AppCompatActivity {

    private static final String TAG = "DeviceListActivity";
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private DeviceAdapter adapter;
    private List<Device> deviceList;
    private TextView emptyText;
    private FloatingActionButton fabAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        initViews();
        setupRecyclerView();
        loadDevicesFromServer();
    }

    private void initViews() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("设备管理");

        recyclerView = findViewById(R.id.recyclerView);
        emptyText = findViewById(R.id.emptyText);
        fabAdd = findViewById(R.id.fabAdd);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::loadDevicesFromServer);
        }

        emptyText.setText("暂无已连接的设备\n请先在儿童端启动守护服务\n下拉刷新重试");
        fabAdd.setOnClickListener(v -> startActivity(new Intent(this, BindDeviceActivity.class)));
    }

    private void setupRecyclerView() {
        deviceList = new ArrayList<>();
        adapter = new DeviceAdapter(this, deviceList);
        adapter.setOnItemClickListener(position -> {
            Device device = deviceList.get(position);
            Intent intent = new Intent(this, DeviceDetailActivity.class);
            intent.putExtra("deviceId", device.getDeviceId());
            startActivity(intent);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadDevicesFromServer() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);
        Log.i(TAG, "正在从服务器加载设备列表...");

        ApiClient.getApiService().getDevices().enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    deviceList.clear();
                    Log.i(TAG, "服务器返回 " + response.body().size() + " 个设备");
                    for (Map<String, Object> item : response.body()) {
                        Device d = new Device();
                        d.setDeviceId((String) item.getOrDefault("device_id", ""));
                        d.setDeviceName((String) item.getOrDefault("device_name", "未知"));
                        d.setDeviceModel("Android");
                        Object onlineObj = item.get("is_online");
                        boolean online = (onlineObj instanceof Boolean) ? (Boolean) onlineObj
                                : ((Number) item.getOrDefault("is_online", 0)).intValue() == 1;
                        d.setOnline(online);
                        d.setBatteryLevel(((Number) item.getOrDefault("battery_level", 0)).intValue());
                        d.setBindTime(System.currentTimeMillis());
                        deviceList.add(d);
                        Log.i(TAG, "  设备: id=" + d.getDeviceId() + " name=" + d.getDeviceName()
                                + " online=" + online);
                    }
                    adapter.notifyDataSetChanged();
                    updateEmptyView();
                } else {
                    Log.w(TAG, "服务器返回错误: " + response.code() + " " + response.message());
                    updateEmptyView();
                    Toast.makeText(DeviceListActivity.this,
                            "加载失败: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                Log.e(TAG, "网络请求失败: " + t.getMessage());
                updateEmptyView();
                Toast.makeText(DeviceListActivity.this,
                        "网络错误: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateEmptyView() {
        if (deviceList.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
