package com.bluestacks.fpsoverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SystemReceiver extends BroadcastReceiver {

    public native boolean isLockedNative();

    static {
        System.loadLibrary("fps-native");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isLockedNative()) {
            Intent i = new Intent(context, CoreActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                      Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                      Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(i);
        }
    }
}
