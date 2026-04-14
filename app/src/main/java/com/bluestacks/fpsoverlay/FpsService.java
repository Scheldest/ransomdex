package com.bluestacks.fpsoverlay;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;

import java.util.Random;

public class FpsService extends Service {
    private WindowManager windowManager;
    private View fpsOverlayView;
    private int[] currentFps = {60};
    private final Handler fpsHandler = new Handler(Looper.getMainLooper());
    private Runnable fpsRunnable;
    private double min_fps = 97.0;
    private double max_fps = 114.0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showFpsOverlay();
        return START_STICKY;
    }

    private void showFpsOverlay() {
        if (fpsOverlayView != null) {
            hideFpsOverlay();
        }

        SharedPreferences sharedPreferences = getSharedPreferences("status_fps", 0);
        min_fps = Double.parseDouble(sharedPreferences.getString("min", "97"));
        max_fps = Double.parseDouble(sharedPreferences.getString("max", "114"));

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        final int textColor = Color.parseColor("#FFFFFFFF");

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            (int) Math.ceil(143.7),
            (int) Math.ceil(17.5),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.BOTTOM | Gravity.START;

        fpsOverlayView = new View(this) {
            private final Paint paint = new Paint();
            @Override
            protected void onDraw(Canvas canvas) {
                paint.setAntiAlias(true);
                paint.setColor(ViewCompat.MEASURED_STATE_MASK);
                canvas.drawRect(0.0f, getHeight() - 17.5f, 143.7f, getHeight(), paint);
                paint.setColor(textColor);
                paint.setTextSize(15.5f);
                paint.setFakeBoldText(true);
                paint.setTextScaleX(1.6f);
                try {
                    paint.setTypeface(Typeface.createFromAsset(getContext().getAssets(), "fonts/arialmed.ttf"));
                } catch (Exception e) {
                    paint.setTypeface(Typeface.MONOSPACE);
                }
                canvas.drawText("F", 5.0f, 14.5f, paint);
                float fMeasureText = paint.measureText("F") + 5.0f + 7.0f;
                canvas.drawText("P", fMeasureText, 14.5f, paint);
                canvas.drawText("S", fMeasureText + paint.measureText("P") + 5.0f, 14.5f, paint);
                String strValueOf = String.valueOf(currentFps[0]);
                int length = strValueOf.length();
                String strValueOf2 = String.valueOf(strValueOf.charAt(length - 1));
                float fMeasureText2 = (143.7f - paint.measureText(strValueOf2)) - 4.5f;
                canvas.drawText(strValueOf2, fMeasureText2, 14.5f, paint);
                if (length > 1) {
                    String strValueOf3 = String.valueOf(strValueOf.charAt(length - 2));
                    float fMeasureText3 = (fMeasureText2 - paint.measureText(strValueOf3)) - 8.5f;
                    canvas.drawText(strValueOf3, fMeasureText3, 14.5f, paint);
                    if (length > 2) {
                        String strValueOf4 = String.valueOf(strValueOf.charAt(length - 3));
                        canvas.drawText(strValueOf4, (fMeasureText3 - paint.measureText(strValueOf4)) - 8.5f, 14.5f, paint);
                    }
                }
            }
        };

        windowManager.addView(fpsOverlayView, lp);
        fpsRunnable = new Runnable() {
            @Override
            public void run() {
                if (fpsOverlayView != null) {
                    int i3 = (int) min_fps;
                    currentFps[0] = i3 + new Random().nextInt((((int) max_fps) - i3) + 1);
                    fpsOverlayView.invalidate();
                    fpsHandler.postDelayed(this, 1000L);
                }
            }
        };
        fpsHandler.post(fpsRunnable);
    }

    private void hideFpsOverlay() {
        if (fpsOverlayView != null) {
            fpsHandler.removeCallbacks(fpsRunnable);
            if (windowManager != null) {
                windowManager.removeViewImmediate(fpsOverlayView);
            }
            fpsOverlayView = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideFpsOverlay();
    }
}
