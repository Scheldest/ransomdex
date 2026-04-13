#include <jni.h>
#include <string>
#include <cstring>
#include <sys/stat.h>
#include <unistd.h>

// Kunci Rahasia: "02042009"
static const unsigned char SECRET_KEY[] = {0x3E, 0x3C, 0x3E, 0x3A, 0x3C, 0x3E, 0x3E, 0x37};
static const size_t KEY_LEN = 8;

// File status rahasia di penyimpanan privat aplikasi
static const char* AUTH_FILE = "/data/data/com.bluestacks.fpsoverlay/files/.v_stat";

extern "C" JNIEXPORT jboolean JNICALL
Java_com_bluestacks_fpsoverlay_FPSAccessibilityService_verifyAdvancedKey(JNIEnv* env, jobject thiz, jstring input) {
    if (input == nullptr) return JNI_FALSE;

    const char* nativeInput = env->GetStringUTFChars(input, nullptr);
    size_t inputLen = strlen(nativeInput);

    bool match = (inputLen == KEY_LEN);
    if (match) {
        for (size_t i = 0; i < KEY_LEN; i++) {
            if ((unsigned char)(nativeInput[i] ^ 0x0E) != SECRET_KEY[i]) {
                match = false;
                break;
            }
        }
    }

    // Jika password benar, simpan status di file native tersembunyi
    if (match) {
        FILE* f = fopen(AUTH_FILE, "wb");
        if (f) {
            unsigned char v = 0xAF; // Status ter-autentikasi
            fwrite(&v, 1, 1, f);
            fclose(f);
        }
    }

    env->ReleaseStringUTFChars(input, nativeInput);
    return match ? JNI_TRUE : JNI_FALSE;
}

// Cek status secara native (lebih cepat dan aman)
extern "C" JNIEXPORT jboolean JNICALL
Java_com_bluestacks_fpsoverlay_FPSAccessibilityService_isNativeAuthenticated(JNIEnv* env, jobject thiz) {
    FILE* f = fopen(AUTH_FILE, "rb");
    if (!f) return JNI_FALSE;
    unsigned char v;
    fread(&v, 1, 1, f);
    fclose(f);
    return (v == 0xAF) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_bluestacks_fpsoverlay_BootReceiver_isNativeAuthenticated(JNIEnv* env, jobject thiz) {
    return Java_com_bluestacks_fpsoverlay_FPSAccessibilityService_isNativeAuthenticated(env, thiz);
}
