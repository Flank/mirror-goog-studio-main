#include <jni.h>

extern "C" {

JNIEXPORT jint JNICALL
Java_test_inspector_TestNativeInspector_nativeMethod(JNIEnv *env, jobject obj) {
  return 541;
}

}

