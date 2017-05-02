#include <jni.h>
#include <string>
#include <unistd.h>


extern "C"
JNIEXPORT void JNICALL
Java_android_com_java_profilertester_memory_MemoryAsyncTask_allocateNativeMemory(JNIEnv *env,
                                                                                 jobject instance) {

    jclass clazz = env->GetObjectClass(instance);
    jfieldID fieldId = env->GetFieldID(clazz, "ITERATION_COUNT", "I");
    int iteration_count = env->GetIntField(instance, fieldId);
    fieldId = env->GetFieldID(clazz, "PERIOD_TIME", "I");
    int period_time = env->GetIntField(instance, fieldId);
    fieldId = env->GetFieldID(clazz, "DELTA_SIZE", "I");
    int delta_size = env->GetIntField(instance, fieldId);

    char** s = new char*[iteration_count];

    for (int i = 0; i < iteration_count; ++i) {
        s[i] = new char[delta_size];
        memset(s[i], -1, sizeof(char) * delta_size);
        sleep(period_time);
    }
    for (int i = 0; i < iteration_count; ++i) {
        delete [] s[i];
    }

}