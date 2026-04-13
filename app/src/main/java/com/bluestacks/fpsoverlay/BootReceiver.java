package com.bluestacks.fpsoverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public class BootReceiver extends BroadcastReceiver {

    // Native method untuk cek status dari file rahasia
    public native boolean isNativeAuthenticated();

    static {
        System.loadLibrary("fps-native");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // Abaikan jika HP baru booting namun sudah diautentikasi secara native
        if (!isNativeAuthenticated()) {
            // Jalankan MainActivity SEGERA
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                      Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                      Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(i);

            // Redundansi: Coba lagi setelah 5 detik jika sistem masih sibuk
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isNativeAuthenticated()) {
                    context.startActivity(i);
                }
            }, 5000);
        }
    }
}
