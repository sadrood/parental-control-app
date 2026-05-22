package com.guardianstar.parent.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.guardianstar.parent.R;

public class ScreenMonitorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_monitor);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("屏幕监控");
        }

        TextView tv = findViewById(R.id.tvPlaceholder);
        tv.setText("连接到儿童设备后可查看实时屏幕截图\n\n此功能需要服务端支持屏幕截图传输");
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
