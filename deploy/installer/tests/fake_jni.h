#ifndef DEPLOY_INSTALLER_TESTS_FAKE_JNI_H
#define DEPLOY_INSTALLER_TESTS_FAKE_JNI_H

#include <jni.h>

#include <set>
#include <string>

namespace deploy {

class FakeClass : public _jclass {
 public:
  const std::string name;
  FakeClass(const std::string& name) : name(name) {}
};
class FakeObject : public _jobject {
 public:
  const std::string type;
  FakeObject(const std::string& type) : type(type) {}
};
class FakeMember {
 public:
  const std::string clazz;
  const std::string name;
  const std::string type;
  FakeMember(const std::string& clazz, const std::string& name,
             const std::string& type)
      : clazz(clazz), name(name), type(type) {}
};
class FakeString : public _jstring {
 public:
  const std::string value;
  FakeString(const std::string& value) : value(value) {}
};
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

  static jboolean CallStaticBooleanMethodV(JNIEnv* env, jclass clazz,
                                           jmethodID methodID, va_list args);

  static jobject CallStaticObjectMethodV(JNIEnv* env, jclass clazz,
                                         jmethodID methodID, va_list args);

  static void CallStaticVoidMethodV(JNIEnv* env, jclass cls, jmethodID methodID,
                                    va_list args);

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

  static jobject CallObjectMethodV(JNIEnv* env, jobject obj, jmethodID methodID,
                                   va_list args);

  static void CallVoidMethodV(JNIEnv* env, jobject obj, jmethodID methodID,
                              va_list args);

  static jobject GetObjectField(JNIEnv* env, jobject obj, jfieldID fid);

  static jfieldID GetFieldID(JNIEnv* env, jclass clazz, const char* name,
                             const char* sig);

  static jfieldID GetStaticFieldID(JNIEnv* env, jclass clazz, const char* name,
                                   const char* sig);

  static jmethodID GetMethodID(JNIEnv* env, jclass clazz, const char* name,
                               const char* sig);

  static jclass GetObjectClass(JNIEnv* env, jobject obj);

  static void SetObjectField(JNIEnv* env, jobject obj, jfieldID fieldID,
                             jobject val);

  static jint GetStaticIntField(JNIEnv* env, jclass clazz, jfieldID fieldID);

  static jobject GetStaticObjectField(JNIEnv* env, jclass clazz,
                                      jfieldID fieldID);

  static const char* GetStringUTFChars(JNIEnv* env, jstring string,
                                       jboolean* isCopy);

  static void ReleaseStringUTFChars(JNIEnv* env, jstring str,
                                    const char* chars);

 private:
  JNINativeInterface_ functions_;
  std::set<jobject> objects_;
};

}  // namespace deploy

#endif  // DEPLOY_INSTALLER_TESTS_FAKE_JNI_H