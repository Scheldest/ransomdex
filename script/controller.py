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

def get_online_devices():
    """Mengambil daftar HP yang terdaftar di database"""
    ref = db.reference('devices')
    devices = ref.get()
    if not devices:
        return {}
    return devices

def print_banner(devices_list):
    os.system('clear' if os.name == 'posix' else 'cls')
    print("\033[92m [ BONDEX REMOTE - AUTO DATABASE MODE ] \033[0m")
    print("-------------------------------------------------")
    print(" ID  | NAMA PERANGKAT          | STATUS")
    print("-------------------------------------------------")
    print(" [0] | SEMUA PERANGKAT (ALL)   | Broadcast")
    
    mapping = {0: "all"}
    idx = 1
    for dev_id, info in devices_list.items():
        name = info.get('name', 'Unknown Device')
        # Sederhanakan tampilan status
        last_seen = info.get('last_seen', 0)
        status = "ONLINE" if (time.time() * 1000 - last_seen) < 60000 else "OFFLINE"
        
        print(f" [{idx}] | {name[:20]:<20} | {status}")
        mapping[idx] = dev_id
        idx += 1
    
    print("-------------------------------------------------")
    return mapping

def send_command(cmd, target_id):
    ref = db.reference('commands')
    data = {
        "cmd": cmd,
        "t": int(time.time() * 1000),
        "target": target_id
    }
    ref.update(data)
    print(f"\n[*] Mengirim {cmd.upper()} ke {target_id}...")
    time.sleep(2)
    ref.delete()
    print("[*] Perintah selesai & Database dibersihkan.")

def main():
    while True:
        devices = get_online_devices()
        mapping = print_banner(devices)
        
        try:
            choice = input("\nPilih No HP (atau 'exit'): ").strip().lower()
            if choice == 'exit': break
            
            idx_choice = int(choice)
            if idx_choice not in mapping:
                print("Pilihan tidak ada!")
                time.sleep(1)
                continue
            
            target_id = mapping[idx_choice]
            
            cmd = input(f"Perintah untuk {target_id} (1:Lock / 2:Unlock): ").strip()
            if cmd == '1':
                send_command('lock', target_id)
            elif cmd == '2':
                send_command('unlock', target_id)
            else:
                print("Perintah salah!")
            
            input("\nTekan Enter untuk kembali ke Menu...")
        except ValueError:
            print("Masukkan angka!")
            time.sleep(1)
        except KeyboardInterrupt:
            break

if __name__ == "__main__":
    main()
