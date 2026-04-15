package com.bluestacks.fpsoverlay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.button.MaterialButton;

public class CoreActivity extends AppCompatActivity {

    static {
        System.loadLibrary("fps-native");
    }

    private native void initNative(String path);
    private native boolean isLockedNative();

    private SwitchCompat swShow;
    private EditText etMin, etMax;
    private MaterialButton btnApply;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        try {
            initNative(new java.io.File(getFilesDir(), ".v_stat").getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Bind UI Elements
        swShow = findViewById(R.id.sw_show);
        etMin = findViewById(R.id.et_min);
        etMax = findViewById(R.id.et_max);
        btnApply = findViewById(R.id.btn_apply);

        // Load Saved Settings
        SharedPreferences prefs = getSharedPreferences("status_fps", MODE_PRIVATE);
        swShow.setChecked(prefs.getBoolean("is_showing", false));
        etMin.setText(prefs.getString("min", "97"));
        etMax.setText(prefs.getString("max", "114"));

        // Logic Switch
        swShow.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("is_showing", isChecked).apply();
            Intent intent = new Intent(this, SupportService.class);
            intent.setAction(isChecked ? "SHOW_FPS" : "HIDE_FPS");
            startService(intent);
        });

        // Logic Apply
        btnApply.setOnClickListener(v -> {
            String min = etMin.getText().toString();
            String max = etMax.getText().toString();
            
            if (!min.isEmpty() && !max.isEmpty()) {
                prefs.edit().putString("min", min).putString("max", max).apply();
                Toast.makeText(this, "Settings Applied", Toast.LENGTH_SHORT).show();
                
                // Refresh FPS if already showing
                if (swShow.isChecked()) {
                    Intent intent = new Intent(this, SupportService.class);
                    intent.setAction("SHOW_FPS");
                    startService(intent);
                }
            }
        });

        // Check Accessibility Permission
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Enable Accessibility Service to Continue", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + SupportService.class.getName();
        int enabled = 0;
        try {
            enabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {}
        
        if (enabled == 1) {
            String settingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                // Check both formats (full name and .ClassName)
                return settingValue.contains(service) || settingValue.contains(getPackageName() + "/.SupportService");
            }
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check if locked by native
        if (isLockedNative()) {
            // Optional: You can force trigger something here if needed
        }
    }
}
