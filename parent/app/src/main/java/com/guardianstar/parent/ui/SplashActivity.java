package com.guardianstar.parent.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.guardianstar.parent.R;
import com.guardianstar.parent.service.WebSocketService;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY = 2000;
    private static final String PREFS_NAME = "guardian_prefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        initViews();
        startAnimations();
        startWebSocketService();

        new Handler(Looper.getMainLooper()).postDelayed(this::checkAndNavigate, SPLASH_DELAY);
    }

    private void initViews() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
    }

    private void startAnimations() {
        ImageView logoIcon = findViewById(R.id.logoIcon);
        TextView logoText = findViewById(R.id.logoText);
        TextView logoSubtitle = findViewById(R.id.logoSubtitle);

        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);

        logoIcon.startAnimation(fadeIn);
        logoText.startAnimation(slideUp);
        logoSubtitle.startAnimation(slideUp);
    }

    private void startWebSocketService() {
        Intent serviceIntent = new Intent(this, WebSocketService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void checkAndNavigate() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true);
        String userRole = prefs.getString("user_role", "");

        Intent intent;
        if (isFirstLaunch) {
            intent = new Intent(this, GuideActivity.class);
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
        } else if (userRole.isEmpty()) {
            intent = new Intent(this, RoleSelectionActivity.class);
        } else if ("child".equals(userRole)) {
            intent = new Intent(this, ChildMainActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }

        startActivity(intent);
        finish();
    }
}
