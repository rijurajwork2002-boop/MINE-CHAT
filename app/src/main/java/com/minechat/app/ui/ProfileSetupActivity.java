package com.minechat.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.minechat.app.R;
import com.minechat.app.service.MeshService;

public class ProfileSetupActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        EditText nameField = findViewById(R.id.edit_name);
        Button saveBtn = findViewById(R.id.btn_save);

        SharedPreferences prefs = getSharedPreferences("minechat", MODE_PRIVATE);
        String existing = prefs.getString(MeshService.PREF_USER_NAME, "");
        if (!existing.isEmpty()) nameField.setText(existing);

        saveBtn.setOnClickListener(v -> {
            String name = nameField.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString(MeshService.PREF_USER_NAME, name).apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}
