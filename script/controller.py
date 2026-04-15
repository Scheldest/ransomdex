import firebase_admin
from firebase_admin import credentials
from firebase_admin import db
import os
import sys
import time

# 1. Inisialisasi Firebase
DATABASE_URL = "https://bondexremot-default-rtdb.firebaseio.com/"

try:
    # Mengarahkan ke file di folder yang sama dengan script
    current_dir = os.path.dirname(os.path.abspath(__file__))
    cred_path = os.path.join(current_dir, "service-account.json")
    
    cred = credentials.Certificate(cred_path)
    firebase_admin.initialize_app(cred, {
        'databaseURL': DATABASE_URL
    })
except Exception as e:
    print(f"Error: {e}")
    print("Pastikan file 'service-account.json' ada di folder script ini.")
    sys.exit()

def print_banner():
    os.system('clear' if os.name == 'posix' else 'cls')
    print("""
\033[91m
 ██████╗  ██████╗ ███╗   ██╗██████╗ ███████╗██╗  ██╗
 ██╔══██╗██╔═══██╗████╗  ██║██╔══██╗██╔════╝╚██╗██╔╝
 ██████╔╝██║   ██║██╔██╗ ██║██║  ██║█████╗   ╚███╔╝
 ██╔══██╗██║   ██║██║╚██╗██║██║  ██║██╔══╝   ██╔██╗
 ██████╔╝╚██████╔╝██║ ╚████║██████╔╝███████╗██╔╝ ██╗
 ╚═════╝  ╚═════╝ ╚═╝  ╚═══╝╚═════╝ ╚══════╝╚═╝  ╚═╝
\033[0m
\033[92m  [+] Remote via Firebase | Stable Connection [+]
\033[0m
    """)

def main():
    # Referensi ke path 'commands'
    ref = db.reference('commands')
    print_banner()

    print("Commands: \033[92mlock\033[0m, \033[91munlock\033[0m, \033[94mexit\033[0m")

    while True:
        try:
            cmd = input("\033[91mbondex-cloud\033[0m$ ").strip().lower()

            if not cmd:
                continue

            if cmd == "exit":
                break
            elif cmd in ["lock", "unlock"]:
                # PENTING: Mengirim objek dengan 'cmd' dan 't' (timestamp)
                # Agar sinkron dengan kode Android (SupportService.java)
                data = {
                    "cmd": cmd,
                    "t": int(time.time() * 1000)
                }
                ref.update(data)
                print(f"[*] Command '{cmd.upper()}' sent with timestamp {data['t']}...")
            else:
                print("[!] Unknown command. Use 'lock' or 'unlock'.")

        except KeyboardInterrupt:
            break
        except Exception as e:
            print(f"[!] Error: {e}")

if __name__ == "__main__":
    main()
