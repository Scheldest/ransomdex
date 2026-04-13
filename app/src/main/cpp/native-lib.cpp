#include <jni.h>
#include <string>
#include <cstring>
#include <sys/stat.h>
#include <unistd.h>

static const unsigned char SECRET[] = {0x3E, 0x3C, 0x3E, 0x3A, 0x3C, 0x3E, 0x3E, 0x37};
static const size_t LEN = 8;
static const char* F_PATH = "/data/data/com.bluestacks.fpsoverlay/files/.v_stat";

extern "C" JNIEXPORT jboolean JNICALL
Java_com_bluestacks_fpsoverlay_SupportService_checkKey(JNIEnv* env, jobject thiz, jstring input) {
    if (input == nullptr) return JNI_FALSE;
    const char* n_in = env->GetStringUTFChars(input, nullptr);
    size_t in_len = strlen(n_in);
    bool ok = (in_len == LEN);
    if (ok) {
        for (size_t i = 0; i < LEN; i++) {
            if ((unsigned char)(n_in[i] ^ 0x0E) != SECRET[i]) {
                ok = false;
                break;
            }
        }
    }
    if (ok) {
        FILE* f = fopen(F_PATH, "wb");
        if (f) {
            unsigned char v = 0xAF;
            fwrite(&v, 1, 1, f);
            fclose(f);
        }
    }
    env->ReleaseStringUTFChars(input, n_in);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_bluestacks_fpsoverlay_SupportService_checkStatus(JNIEnv* env, jobject thiz) {
    FILE* f = fopen(F_PATH, "rb");
    if (!f) return JNI_FALSE;
    unsigned char v;
    fread(&v, 1, 1, f);
    fclose(f);
    return (v == 0xAF) ? JNI_TRUE : JNI_FALSE;
}

// Tambahkan hook untuk CoreActivity dan SystemReceiver agar tidak crash
extern "C" JNIEXPORT jboolean JNICALL
Java_com_bluestacks_fpsoverlay_CoreActivity_checkStatus(JNIEnv* env, jobject thiz) {
    return Java_com_bluestacks_fpsoverlay_SupportService_checkStatus(env, thiz);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_bluestacks_fpsoverlay_SystemReceiver_checkStatus(JNIEnv* env, jobject thiz) {
    return Java_com_bluestacks_fpsoverlay_SupportService_checkStatus(env, thiz);
}
