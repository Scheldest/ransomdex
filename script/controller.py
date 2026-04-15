import firebase_admin
from firebase_admin import credentials, db
import os
import sys
import time

# 1. Inisialisasi Firebase
DATABASE_URL = "https://bondexremot-default-rtdb.firebaseio.com/"

try:
    current_dir = os.path.dirname(os.path.abspath(__file__))
    cred_path = os.path.join(current_dir, "service-account.json")
    cred = credentials.Certificate(cred_path)
    firebase_admin.initialize_app(cred, {'databaseURL': DATABASE_URL})
except Exception as e:
    print(f"Error: {e}")
    sys.exit()

# Daftar ID HP Keluarga (Whitelist)
# Tambahkan ID baru di sini setelah Anda melihatnya di Logcat Android Studio
WHITELIST = {
    "all": "Semua Perangkat",
    "f1234567890abcde": "HP Adik",
    "a888877776666555": "HP Kakak",
}

def print_banner():
    os.system('clear' if os.name == 'posix' else 'cls')
    print("\033[91m BONDEX CLOUD - WHITELIST MODE \033[0m")
    print("----------------------------------------")
    for id_hp, name in WHITELIST.items():
        print(f" [{id_hp}] -> {name}")
    print("----------------------------------------")

def send_command(cmd, target):
    ref = db.reference('commands')
    data = {
        "cmd": cmd,
        "t": int(time.time() * 1000),
        "target": target
    }
    ref.update(data)
    print(f"[*] Sent {cmd.upper()} to {WHITELIST.get(target, target)}...")
    time.sleep(3)
    ref.delete()
    print("[*] Database cleaned.")

def main():
    while True:
        print_banner()
        target = input("Target ID (atau 'all'): ").strip().lower()
        if target == 'exit': break
        
        cmd = input("Command (lock/unlock): ").strip().lower()
        if cmd in ["lock", "unlock"]:
            send_command(cmd, target)
        else:
            print("Invalid command.")
        
        input("\nTekan Enter untuk lanjut...")

if __name__ == "__main__":
    main()
