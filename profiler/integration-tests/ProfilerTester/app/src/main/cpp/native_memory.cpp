#include <jni.h>
#include <string.h>
#include <unistd.h>


extern "C"
JNIEXPORT void JNICALL
Java_android_com_java_profilertester_taskcategory_MemoryTaskCategory_allocateNativeMemory(
        JNIEnv *env,
        jobject instance) {

    jclass clazz = env->GetObjectClass(instance);
    jfieldID fieldId = env->GetFieldID(clazz, "ITERATION_COUNT", "I");
    int iteration_count = env->GetIntField(instance, fieldId);
    fieldId = env->GetFieldID(clazz, "PERIOD_TIME", "I");
    int period_time = env->GetIntField(instance, fieldId);
    fieldId = env->GetFieldID(clazz, "DELTA_SIZE", "I");
    int delta_size = env->GetIntField(instance, fieldId);

    char **s = new char *[iteration_count];

    for (int i = 0; i < iteration_count; ++i) {
        s[i] = new char[delta_size];
        memset(s[i], -1, sizeof(char) * delta_size);
        sleep(period_time);
    }
    for (int i = 0; i < iteration_count; ++i) {
        delete[] s[i];
    }

}

// newRef[1,2,3] and freeRef[1,2,3] are here just to provide
// for nonempty allocation/deallocation call stacks.
__attribute__((noinline)) jobject newRef3(JNIEnv *env,
                jobject o)
{
    return env->NewGlobalRef(o);
}

__attribute__((noinline)) jobject newRef2(JNIEnv *env,
                jobject o)
{
    return newRef3(env, o);
}

__attribute__((noinline)) jobject newRef1(JNIEnv *env,
             jobject o)
{
    return newRef2(env, o);
}


__attribute__((noinline)) void freeRef3(JNIEnv *env, jobject o)
{
    env->DeleteGlobalRef(o);
}

__attribute__((noinline)) void freeRef2(JNIEnv *env, jobject o)
{
    freeRef3(env, o);
}

__attribute__((noinline)) void freeRef1(JNIEnv *env, jobject o)
{
    freeRef2(env, o);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_android_com_java_profilertester_taskcategory_MemoryTaskCategory_allocateJniRef(JNIEnv *env,
                                                                                    jobject instance,
                                                                                    jobject o) {
    jobject ref = newRef1(env, o);
    return reinterpret_cast<jlong>(ref);
}

extern "C"
JNIEXPORT void JNICALL
Java_android_com_java_profilertester_taskcategory_MemoryTaskCategory_freeJniRef(JNIEnv *env,
                                                                                jobject instance,
                                                                                jlong refValue) {
    jobject ref = reinterpret_cast<jobject >(refValue);
    freeRef1(env, ref);
}