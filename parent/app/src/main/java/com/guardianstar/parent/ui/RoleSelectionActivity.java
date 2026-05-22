package com.guardianstar.parent.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.guardianstar.parent.R;
import com.guardianstar.parent.service.GuardianService;
import com.guardianstar.parent.service.WebSocketService;

public class RoleSelectionActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "guardian_prefs";
    public static final String KEY_USER_ROLE = "user_role";
    public static final String ROLE_PARENT = "parent";
    public static final String ROLE_CHILD = "child";

    private CardView cardParent, cardChild;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        cardParent = findViewById(R.id.cardParent);
        cardChild = findViewById(R.id.cardChild);

        cardParent.setOnClickListener(v -> selectRole(ROLE_PARENT));
        cardChild.setOnClickListener(v -> selectRole(ROLE_CHILD));
    }

    private void selectRole(String role) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_USER_ROLE, role).apply();

        Toast.makeText(this,
                ROLE_PARENT.equals(role) ? "已选择家长角色" : "已选择儿童角色",
                Toast.LENGTH_SHORT).show();

        Intent intent;
        if (ROLE_PARENT.equals(role)) {
            // 启动家长端 WebSocket 服务
            Intent serviceIntent = new Intent(this, WebSocketService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, ChildMainActivity.class);
        }

        startActivity(intent);
        finish();
    }
}
