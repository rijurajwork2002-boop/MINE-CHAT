package com.minechat.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.minechat.app.R;
import com.minechat.app.service.MeshService;

public class SplashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences prefs = getSharedPreferences("minechat", MODE_PRIVATE);
            String name = prefs.getString(MeshService.PREF_USER_NAME, "");
            if (name.isEmpty()) {
                startActivity(new Intent(this, ProfileSetupActivity.class));
            } else {
                startActivity(new Intent(this, MainActivity.class));
            }
            finish();
        }, 1500);
    }
}
