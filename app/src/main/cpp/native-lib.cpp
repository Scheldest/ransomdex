#include <jni.h>
#include <string>
#include <cstring>

// Kunci: "02042009" (XOR 0x0E)
static const unsigned char SECRET_KEY[] = {0x3E, 0x3C, 0x3E, 0x3A, 0x3C, 0x3E, 0x3E, 0x37};
static const size_t KEY_LEN = 8;

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
