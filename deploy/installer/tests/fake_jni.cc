#include "tools/base/deploy/installer/tests/fake_jni.h"

#include "tools/base/deploy/common/log.h"

namespace deploy {

FakeJNIEnv::FakeJNIEnv() : functions_{0} {
  functions_.CallObjectMethodA = &CallObjectMethodA;
  functions_.CallStaticBooleanMethodA = &CallStaticBooleanMethodA;
  functions_.CallStaticObjectMethodA = &CallStaticObjectMethodA;
  functions_.CallStaticVoidMethodA = &CallStaticVoidMethodA;
  functions_.CallVoidMethodA = &CallVoidMethodA;
  functions_.DeleteLocalRef = &DeleteLocalRef;
  functions_.ExceptionCheck = &ExceptionCheck;
  functions_.ExceptionClear = &ExceptionClear;
  functions_.ExceptionDescribe = &ExceptionDescribe;
  functions_.FindClass = &FindClass;
  functions_.GetFieldID = &GetFieldID;
  functions_.GetMethodID = &GetMethodID;
  functions_.GetObjectClass = &GetObjectClass;
  functions_.GetStaticMethodID = &GetStaticMethodID;
  functions_.NewByteArray = &NewByteArray;
  functions_.NewObjectArray = &NewObjectArray;
  functions_.NewStringUTF = &NewStringUTF;
  functions_.RegisterNatives = &RegisterNatives;
  functions_.SetByteArrayRegion = &SetByteArrayRegion;
  functions_.SetObjectArrayElement = &SetObjectArrayElement;
  functions_.SetObjectField = &SetObjectField;

  functions = &functions_;
}

jclass FakeJNIEnv::FindClass(JNIEnv* env, const char* name) {
  Log::I("JNI::FindClass");
  jclass ret = new FakeClass();
  ((FakeJNIEnv*)env)->objects_.insert(ret);
  return ret;
}

jboolean FakeJNIEnv::ExceptionCheck(JNIEnv* env) {
  Log::I("JNI::ExceptionCheck");
  return false;
}

void FakeJNIEnv::ExceptionClear(JNIEnv* env) { Log::I("JNI::ExceptionClear"); }

jint FakeJNIEnv::RegisterNatives(JNIEnv* env, jclass clazz,
                                 const JNINativeMethod* methods,
                                 jint nMethods) {
  Log::I("JNI::RegisterNatives");
  return 0;
}

jstring FakeJNIEnv::NewStringUTF(JNIEnv* env, const char* utf) {
  Log::I("JNI::NewStringUTF");
  jstring ret = new FakeString();
  ((FakeJNIEnv*)env)->objects_.insert(ret);
  return ret;
}

void FakeJNIEnv::DeleteLocalRef(JNIEnv* env, jobject obj) {
  Log::I("JNI::DeleteLocalRef");
}

jboolean FakeJNIEnv::CallStaticBooleanMethodA(JNIEnv* env, jclass clazz,
                                              jmethodID methodID,
                                              const jvalue* args) {
  Log::I("JNI::CallStaticBooleanMethodA");
  return true;
}

jobject FakeJNIEnv::CallStaticObjectMethodA(JNIEnv* env, jclass clazz,
                                            jmethodID methodID,
                                            const jvalue* args) {
  Log::I("JNI::CallStaticObjectMethodA");
  return nullptr;
}

void FakeJNIEnv::CallStaticVoidMethodA(JNIEnv* env, jclass cls,
                                       jmethodID methodID, const jvalue* args) {
  Log::I("JNI::CallStaticVoidMethodA");
}

jmethodID FakeJNIEnv::GetStaticMethodID(JNIEnv* env, jclass clazz,
                                        const char* name, const char* sig) {
  Log::I("JNI::GetStaticMethodID");
  return nullptr;
}

void FakeJNIEnv::ExceptionDescribe(JNIEnv* env) {
  Log::I("JNI::ExceptionDescribe");
}

jbyteArray FakeJNIEnv::NewByteArray(JNIEnv* env, jsize len) {
  Log::I("JNI::NewByteArray");
  return nullptr;
}

jobjectArray FakeJNIEnv::NewObjectArray(JNIEnv* env, jsize len, jclass clazz,
                                        jobject init) {
  Log::I("JNI::NewObjectArray");
  return nullptr;
}

void FakeJNIEnv::SetByteArrayRegion(JNIEnv* env, jbyteArray array, jsize start,
                                    jsize len, const jbyte* buf) {
  Log::I("JNI::SetByteArrayRegion");
}

void FakeJNIEnv::SetObjectArrayElement(JNIEnv* env, jobjectArray array,
                                       jsize index, jobject val) {
  Log::I("JNI::SetObjectArrayElement");
}

jobject FakeJNIEnv::CallObjectMethodA(JNIEnv* env, jobject obj,
                                      jmethodID methodID, const jvalue* args) {
  Log::I("JNI::CallObjectMethodA");
  return nullptr;
}

void FakeJNIEnv::CallVoidMethodA(JNIEnv* env, jobject obj, jmethodID methodID,
                                 const jvalue* args) {
  Log::I("JNI::CallVoidMethodA");
}

jfieldID FakeJNIEnv::GetFieldID(JNIEnv* env, jclass clazz, const char* name,
                                const char* sig) {
  Log::I("JNI::GetFieldID");
  return nullptr;
}

jmethodID FakeJNIEnv::GetMethodID(JNIEnv* env, jclass clazz, const char* name,
                                  const char* sig) {
  Log::I("JNI::GetMethodID");
  return nullptr;
}

jclass FakeJNIEnv::GetObjectClass(JNIEnv* env, jobject obj) {
  Log::I("JNI::GetObjectClass");
  return nullptr;
}

void FakeJNIEnv::SetObjectField(JNIEnv* env, jobject obj, jfieldID fieldID,
                                jobject val) {
  Log::I("JNI::SetObjectField");
}

}  // namespace deploy