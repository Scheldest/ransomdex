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

        // Hanya cek Accessibility. Jika belum aktif, lempar ke settings.
        if (!isAccessibilityEnabled()) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Jika user mencoba lari dari aplikasi saat Accessibility aktif, tarik balik
        if (!hasFocus && isAccessibilityEnabled()) {
            // (Opsional) bisa tambahkan logika penarik balik di sini jika diperlukan
        }
    }

    @Override
    public void onBackPressed() {
        // Blokir tombol back total
        return;
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
        // Method ini sekarang opsional karena AccessibilityService sudah menangani overlay
    }
}
