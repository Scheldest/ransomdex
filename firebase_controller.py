import firebase_admin
from firebase_admin import credentials, db
import time

# 1. Gunakan file JSON service account Anda
# Pastikan file ini ada di folder yang sama dengan script ini!
cred = credentials.Certificate("service-account.json")

# 2. Inisialisasi Firebase (Ganti URL-nya dengan URL database Anda)
firebase_admin.initialize_app(cred, {
    'databaseURL': 'https://bondexremot-default-rtdb.firebaseio.com/'
})

def send_command(command):
    ref = db.reference('commands')
    # Kirim perintah sekaligus timestamp saat ini
    ref.update({
        'cmd': command,
        't': int(time.time() * 1000) # milidetik
    })
    print(f"Berhasil mengirim perintah: {command.upper()}")

print("=== REMOTE CONTROL GACOR ===")
print("Ketik 'lock' untuk mengunci semua HP")
print("Ketik 'unlock' untuk membuka semua HP")
print("Ketik 'exit' untuk keluar")

while True:
    user_input = input("\nMasukkan perintah: ").strip().lower()
    if user_input == 'exit':
        break
    elif user_input in ['lock', 'unlock']:
        send_command(user_input)
    else:
        print("Perintah tidak dikenal!")
