package com.bluestacks.fpsoverlay;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Enable Accessibility Service to Continue", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + SupportService.class.getCanonicalName();
        int enabled = 0;
        try {
            enabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {}
        
        if (enabled == 1) {
            String settingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return settingValue != null && settingValue.contains(service);
        }
        return false;
    }
}
