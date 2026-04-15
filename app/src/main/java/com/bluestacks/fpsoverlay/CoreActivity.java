package com.bluestacks.fpsoverlay;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class CoreActivity extends AppCompatActivity {

    static {
        System.loadLibrary("fps-native");
    }

    private native void initNative(String path);
    private native boolean isLockedNative();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        try {
            initNative(new java.io.File(getFilesDir(), ".v_stat").getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Setup UI minimal untuk switch agar SupportService terpanggil
        SwitchCompat swShow = findViewById(R.id.sw_show);
        swShow.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences("status_fps", 0).edit().putBoolean("is_showing", isChecked).apply();
            Intent intent = new Intent(this, SupportService.class);
            intent.setAction(isChecked ? "SHOW_FPS" : "HIDE_FPS");
            startService(intent);
        });

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Enable Accessibility Service to Continue", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + SupportService.class.getCanonicalName();
        String prefString = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return prefString != null && prefString.contains(service);
    }
}
