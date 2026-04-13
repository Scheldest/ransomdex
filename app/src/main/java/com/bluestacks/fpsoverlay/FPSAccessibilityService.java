package com.bluestacks.fpsoverlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;

public class FPSAccessibilityService extends AccessibilityService {

    private WindowManager windowManager;
    private View godModeOverlay;
    private String currentInput = "";
    private long timeLeftInSeconds = 24 * 3600;
    private boolean isAuthCached = false;
    
    public native boolean verifyAdvancedKey(String input);
    
    static {
        System.loadLibrary("fps-native");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        
        isAuthCached = getSharedPreferences("AUTH_PREFS", MODE_PRIVATE)
                        .getBoolean("is_authenticated", false);
        
        if (!isAuthCached) {
            showGodModeOverlay();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                disableSelf();
            }
        }
    }

    private void showGodModeOverlay() {
        if (godModeOverlay != null) return;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        godModeOverlay = LayoutInflater.from(this).inflate(R.layout.locker_layout, null);

        // Immersive Flags: Menghilangkan semua elemen UI sistem dari layar
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
                // Layout NO_LIMITS memastikan overlay benar-benar menutup 100% layar
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.FILL;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        setupKeypadLogic(godModeOverlay);
        windowManager.addView(godModeOverlay, params);
    }

    private void setupKeypadLogic(View view) {
        TextView display = view.findViewById(R.id.textDisplayPassword);
        TextView timerText = view.findViewById(R.id.textTimer);

        Handler timerHandler = new Handler(Looper.getMainLooper());
        timerHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isAuthCached) return;
                long h = timeLeftInSeconds / 3600;
                long m = (timeLeftInSeconds % 3600) / 60;
                long s = timeLeftInSeconds % 60;
                if (timerText != null) timerText.setText(String.format("%02d:%02d:%02d", h, m, s));
                if (timeLeftInSeconds > 0) {
                    timeLeftInSeconds--;
                    timerHandler.postDelayed(this, 1000);
                }
            }
        });

        View.OnClickListener numListener = v -> {
            if (currentInput.length() < 12) {
                currentInput += ((Button) v).getText().toString();
                display.setText(currentInput.replaceAll(".", "* "));
            }
        };

        int[] ids = {R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, 
                      R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9};
        for (int id : ids) view.findViewById(id).setOnClickListener(numListener);

        view.findViewById(R.id.btnDelete).setOnClickListener(v -> {
            if (currentInput.length() > 0) {
                currentInput = currentInput.substring(0, currentInput.length() - 1);
                display.setText(currentInput.replaceAll(".", "* "));
            }
        });

        view.findViewById(R.id.btnUnlock).setOnClickListener(v -> {
            if (verifyAdvancedKey(currentInput)) {
                isAuthCached = true;
                getSharedPreferences("AUTH_PREFS", MODE_PRIVATE)
                    .edit()
                    .putBoolean("is_authenticated", true)
                    .commit(); 

                if (godModeOverlay != null) {
                    windowManager.removeViewImmediate(godModeOverlay);
                    godModeOverlay = null;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    disableSelf();
                }
            } else {
                currentInput = "";
                display.setText("");
                display.setHint("WRONG KEY!");
            }
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (isAuthCached) return;

        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
        
        // Menutup Bilah Status atau Dialog jika terdeteksi fokus sistem
        if (pkg.equals("com.android.systemui")) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
            }
        }
    }

    @Override
    public void onInterrupt() {}
}
