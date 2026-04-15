package com.bluestacks.fpsoverlay;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminActivity extends AppCompatActivity {
    private Spinner deviceSpinner;
    private TextView logStatus;
    private DatabaseReference dbRef;
    private Map<String, String> deviceMap = new HashMap<>();
    private List<String> deviceList = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        deviceSpinner = findViewById(R.id.device_spinner);
        logStatus = findViewById(R.id.log_status);
        Button btnLock = findViewById(R.id.btn_lock_all);
        Button btnUnlock = findViewById(R.id.btn_unlock_all);

        dbRef = FirebaseDatabase.getInstance().getReference();
        
        setupDeviceSpinner();

        btnLock.setOnClickListener(v -> sendCommand("lock"));
        btnUnlock.setOnClickListener(v -> sendCommand("unlock"));
    }

    private void setupDeviceSpinner() {
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);

        dbRef.child("devices").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                deviceList.clear();
                deviceMap.clear();
                deviceList.add("ALL DEVICES");
                deviceMap.put("ALL DEVICES", "all");

                for (DataSnapshot dev : snapshot.getChildren()) {
                    String id = dev.getKey();
                    String name = dev.child("name").getValue(String.class);
                    long lastSeen = dev.child("last_seen").getValue(Long.class) != null ? dev.child("last_seen").getValue(Long.class) : 0;
                    
                    boolean isOnline = (System.currentTimeMillis() - lastSeen) < 60000;
                    String status = isOnline ? " (ONLINE)" : " (OFFLINE)";
                    
                    String displayName = (name != null ? name : id) + status;
                    deviceList.add(displayName);
                    deviceMap.put(displayName, id);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void sendCommand(String cmd) {
        String selected = deviceSpinner.getSelectedItem().toString();
        String targetId = deviceMap.get(selected);

        Map<String, Object> data = new HashMap<>();
        data.put("cmd", cmd);
        data.put("t", System.currentTimeMillis());
        data.put("target", targetId);

        dbRef.child("commands").updateChildren(data).addOnSuccessListener(aVoid -> {
            logStatus.append("\n[OK] Sent " + cmd.toUpperCase() + " to " + selected);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                dbRef.child("commands").removeValue();
            }, 2000);
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }
}
