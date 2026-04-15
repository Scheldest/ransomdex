import { initializeApp } from "firebase/app";
import { getDatabase, ref, onValue, update, remove } from "firebase/database";

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
const deviceListDiv = document.getElementById('deviceList');
const statusMsg = document.getElementById('statusMsg');

// Load Devices
onValue(ref(db, 'devices'), (snapshot) => {
    const devices = snapshot.val();
    deviceListDiv.innerHTML = '';

    // Clear dynamic options but keep 'all'
    while (deviceSelect.options.length > 1) {
        deviceSelect.remove(1);
    }

    if (devices) {
        Object.entries(devices).forEach(([id, info]) => {
            // Add to dropdown
            const option = document.createElement('option');
            option.value = id;
            option.text = info.name || id;
            deviceSelect.add(option);

            // Add to list display
            const isOnline = (Date.now() - info.last_seen) < 60000;
            const item = document.createElement('div');
            item.className = 'device-item';
            item.innerHTML = `
                <span>${info.name || 'Unknown'}</span>
                <span class="status ${isOnline ? 'online' : 'offline'}">${isOnline ? 'ONLINE' : 'OFFLINE'}</span>
            `;
            deviceListDiv.appendChild(item);
        });
    }
});

async function sendCommand(cmd) {
    const target = deviceSelect.value;
    const cmdRef = ref(db, 'commands');

    statusMsg.innerText = `Sending ${cmd.toUpperCase()} to ${target}...`;

    await update(cmdRef, {
        cmd: cmd,
        t: Date.now(),
        target: target
    });

    setTimeout(() => {
        remove(cmdRef);
        statusMsg.innerText = `Command Finished. Database Cleaned.`;
    }, 2000);
}

document.getElementById('btnLock').onclick = () => sendCommand('lock');
document.getElementById('btnUnlock').onclick = () => sendCommand('unlock');
