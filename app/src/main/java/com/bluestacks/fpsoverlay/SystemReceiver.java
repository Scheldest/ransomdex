package com.bluestacks.fpsoverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.content.SharedPreferences;

public class SystemReceiver extends BroadcastReceiver {

    public native boolean checkStatus();

    static {
        System.loadLibrary("fps-native");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!checkStatus()) {
            // Update reboot counter hanya jika ini adalah BOOT_COMPLETED
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || 
                "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
                
                SharedPreferences prefs = context.getSharedPreferences("lock_prefs", Context.MODE_PRIVATE);
                int count = prefs.getInt("reboot_count", 0);
                prefs.edit().putInt("reboot_count", count + 1).apply();
            }

            Intent i = new Intent(context, CoreActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                      Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                      Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(i);
        }
    }
}
