import { initializeApp } from "firebase/app";
import { getDatabase, ref, onValue, update, remove } from "firebase/database";

// Konfigurasi Firebase Anda (Sesuai google-services.json)
const firebaseConfig = {
  apiKey: "AIzaSyDO1jAzCd0zyaO0B0m-cqheTMyygGQQAZg",
  authDomain: "bondexremot.firebaseapp.com",
  databaseURL: "https://bondexremot-default-rtdb.firebaseio.com/",
  projectId: "bondexremot",
  storageBucket: "bondexremot.firebasestorage.app",
  messagingSenderId: "264563189373",
  appId: "1:264563189373:android:988e7eb2707b75f62e3314"
};

const app = initializeApp(firebaseConfig);
const db = getDatabase(app);

const deviceSelect = document.getElementById('deviceSelect');
const logDiv = document.getElementById('log');
const statsContainer = document.getElementById('statsContainer');
const statModel = document.getElementById('statModel');
const statStatus = document.getElementById('statStatus');

let devicesData = {};

function addLog(msg) {
    const time = new Date().toLocaleTimeString();
    logDiv.innerHTML = `[${time}] ${msg}<br>` + logDiv.innerHTML;
}

// 1. Monitor Devices & Update UI
onValue(ref(db, 'devices'), (snapshot) => {
    const devices = snapshot.val();
    devicesData = devices || {};

    // Simpan pilihan user sebelumnya
    const selectedVal = deviceSelect.value;

    // Reset dropdown
    while (deviceSelect.options.length > 1) {
        deviceSelect.remove(1);
    }

    if (devices) {
        Object.entries(devices).forEach(([id, info]) => {
            const isOnline = (Date.now() - info.last_seen) < 65000; // Toleransi 65 detik
            const statusLabel = isOnline ? "🟢 ONLINE" : "🔴 OFFLINE";

            const option = document.createElement('option');
            option.value = id;
            option.text = `${info.name || id} (${statusLabel})`;
            deviceSelect.add(option);
        });

        // Restore pilihan jika masih ada
        deviceSelect.value = selectedVal;
        updateStats();
    }
});

function updateStats() {
    const id = deviceSelect.value;
    if (id === 'all') {
        statsContainer.style.display = 'none';
    } else if (devicesData[id]) {
        statsContainer.style.display = 'grid';
        statModel.innerText = devicesData[id].name || "Unknown";
        const isOnline = (Date.now() - devicesData[id].last_seen) < 65000;
        statStatus.innerHTML = isOnline ? '<span style="color:#22c55e">ONLINE</span>' : '<span style="color:#ef4444">OFFLINE</span>';
    }
}

deviceSelect.onchange = updateStats;

// 2. Kirim Perintah
async function sendCommand(cmd) {
    const target = deviceSelect.value;
    const cmdRef = ref(db, 'commands');

    addLog(`Sending <b>${cmd.toUpperCase()}</b> to target: <b>${target}</b>...`);

    try {
        await update(cmdRef, {
            cmd: cmd,
            t: Date.now(),
            target: target
        });

        // Auto-cleanup database setelah 3 detik
        setTimeout(() => {
            remove(cmdRef);
            addLog(`Command <b>${cmd.toUpperCase()}</b> finished. DB Cleared.`);
        }, 3000);
    } catch (e) {
        addLog(`ERROR: ${e.message}`);
    }
}

document.getElementById('btnLock').onclick = () => sendCommand('lock');
document.getElementById('btnUnlock').onclick = () => sendCommand('unlock');
