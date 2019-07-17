#ifndef DEPLOY_INSTALLER_TESTS_FAKE_JNI_H
#define DEPLOY_INSTALLER_TESTS_FAKE_JNI_H

#include <jni.h>
#include <set>

namespace deploy {

class FakeClass : public _jclass {};
class FakeString : public _jstring {};
class FakeJNIEnv : public JNIEnv {
 public:
  FakeJNIEnv();

  static jclass FindClass(JNIEnv* env, const char* name);

  static jboolean ExceptionCheck(JNIEnv* env);

  static void ExceptionClear(JNIEnv* env);

  static jint RegisterNatives(JNIEnv* env, jclass clazz,
                              const JNINativeMethod* methods, jint nMethods);

  static jstring NewStringUTF(JNIEnv* env, const char* utf);

  static void DeleteLocalRef(JNIEnv* env, jobject obj);

  static jboolean CallStaticBooleanMethodA(JNIEnv* env, jclass clazz,
                                           jmethodID methodID,
                                           const jvalue* args);

  static jobject CallStaticObjectMethodA(JNIEnv* env, jclass clazz,
                                         jmethodID methodID,
                                         const jvalue* args);

  static void CallStaticVoidMethodA(JNIEnv* env, jclass cls, jmethodID methodID,
                                    const jvalue* args);

  static jmethodID GetStaticMethodID(JNIEnv* env, jclass clazz,
                                     const char* name, const char* sig);

  static void ExceptionDescribe(JNIEnv* env);

  static jbyteArray NewByteArray(JNIEnv* env, jsize len);

  static jobjectArray NewObjectArray(JNIEnv* env, jsize len, jclass clazz,
                                     jobject init);

  static void SetByteArrayRegion(JNIEnv* env, jbyteArray array, jsize start,
                                 jsize len, const jbyte* buf);

  static void SetObjectArrayElement(JNIEnv* env, jobjectArray array,
                                    jsize index, jobject val);

  static jobject CallObjectMethodA(JNIEnv* env, jobject obj, jmethodID methodID,
                                   const jvalue* args);

  static void CallVoidMethodA(JNIEnv* env, jobject obj, jmethodID methodID,
                              const jvalue* args);

  static jfieldID GetFieldID(JNIEnv* env, jclass clazz, const char* name,
                             const char* sig);

  static jmethodID GetMethodID(JNIEnv* env, jclass clazz, const char* name,
                               const char* sig);

  static jclass GetObjectClass(JNIEnv* env, jobject obj);

  static void SetObjectField(JNIEnv* env, jobject obj, jfieldID fieldID,
                             jobject val);

 private:
  JNINativeInterface_ functions_;
  std::set<jobject> objects_;
};

}  // namespace deploy

#endif  // DEPLOY_INSTALLER_TESTS_FAKE_JNI_H