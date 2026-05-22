package com.guardianstar.parent.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.guardianstar.parent.R;
import com.guardianstar.parent.data.AppDatabase;
import com.guardianstar.parent.data.Device;

import java.util.UUID;

public class BindDeviceActivity extends AppCompatActivity {

    private EditText etDeviceId, etDeviceName, etBindCode;
    private Button btnBind;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bind_device);

        initViews();
        setupBindButton();
    }

    private void initViews() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("绑定设备");

        etDeviceId = findViewById(R.id.etDeviceId);
        etDeviceName = findViewById(R.id.etDeviceName);
        etBindCode = findViewById(R.id.etBindCode);
        btnBind = findViewById(R.id.btnBind);
    }

    private void setupBindButton() {
        btnBind.setOnClickListener(v -> {
            String deviceId = etDeviceId.getText().toString().trim();
            String deviceName = etDeviceName.getText().toString().trim();
            String bindCode = etBindCode.getText().toString().trim();

            if (deviceId.isEmpty() || deviceName.isEmpty() || bindCode.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show();
                return;
            }

            bindDevice(deviceId, deviceName, bindCode);
        });
    }

    private void bindDevice(String deviceId, String deviceName, String bindCode) {
        AppDatabase db = AppDatabase.getInstance(this);
        new Thread(() -> {
            Device device = new Device(deviceId, deviceName, "Android Device", bindCode);
            device.setOnline(true);
            device.setBatteryLevel(85);
            db.deviceDao().insert(device);

            runOnUiThread(() -> {
                Toast.makeText(this, "设备绑定成功", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
