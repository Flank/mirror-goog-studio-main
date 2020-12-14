#include "tools/base/deploy/installer/tests/fake_jni.h"

#include <string.h>

#include <algorithm>
#include <string>

#include "tools/base/deploy/common/log.h"

#define FAKE_JNI_GET_ID(TYPE, NAME)                                  \
  TYPE FakeJNIEnv::NAME(JNIEnv* env, jclass clazz, const char* name, \
                        const char* sig) {                           \
    FakeClass* cls = (FakeClass*)clazz;                              \
    Log::I("JNI::" #NAME ":%s#%s", cls->name.c_str(), name);         \
    return (TYPE) new FakeMember(cls->name.c_str(), name,            \
                                 GetTypeFromSignature(sig));         \
  }

#define MEMBER_ACCESS(NAME, ID)         \
  FakeMember* member = (FakeMember*)ID; \
  Log::I("JNI::" #NAME ":%s#%s", member->clazz.c_str(), member->name.c_str());

namespace deploy {

namespace {
// Converts a JNI style type signature into a java type name.
std::string GetTypeFromSignature(const std::string& sig) {
  size_t start = 0;
  size_t end = sig.size();
  size_t pos = sig.find_last_of(')', 0);
  if (pos != std::string::npos) {
    start = pos + 1;
  }
  if (sig[start] == 'L') {
    start += 1;
    end -= 1;
  }
  std::string type(sig.begin() + start, sig.end() - start);
  std::replace(type.begin(), type.end(), '/', '.');
  return type;
}
}  // namespace

FakeJNIEnv::FakeJNIEnv() : functions_{0} {
  functions_.CallObjectMethodV = &CallObjectMethodV;
  functions_.CallStaticBooleanMethodV = &CallStaticBooleanMethodV;
  functions_.CallStaticObjectMethodV = &CallStaticObjectMethodV;
  functions_.CallStaticVoidMethodV = &CallStaticVoidMethodV;
  functions_.CallVoidMethodV = &CallVoidMethodV;
  functions_.DeleteLocalRef = &DeleteLocalRef;
  functions_.ExceptionCheck = &ExceptionCheck;
  functions_.ExceptionClear = &ExceptionClear;
  functions_.ExceptionDescribe = &ExceptionDescribe;
  functions_.FindClass = &FindClass;
  functions_.GetFieldID = &GetFieldID;
  functions_.GetMethodID = &GetMethodID;
  functions_.GetObjectClass = &GetObjectClass;
  functions_.GetObjectField = &GetObjectField;
  functions_.GetStaticFieldID = &GetStaticFieldID;
  functions_.GetStaticIntField = &GetStaticIntField;
  functions_.GetStaticMethodID = &GetStaticMethodID;
  functions_.GetStaticObjectField = &GetStaticObjectField;
  functions_.GetStringUTFChars = &GetStringUTFChars;
  functions_.NewByteArray = &NewByteArray;
  functions_.NewObjectArray = &NewObjectArray;
  functions_.NewStringUTF = &NewStringUTF;
  functions_.RegisterNatives = &RegisterNatives;
  functions_.ReleaseStringUTFChars = &ReleaseStringUTFChars;
  functions_.SetByteArrayRegion = &SetByteArrayRegion;
  functions_.SetObjectArrayElement = &SetObjectArrayElement;
  functions_.SetObjectField = &SetObjectField;

  functions = &functions_;
}

jclass FakeJNIEnv::FindClass(JNIEnv* env, const char* name) {
  const std::string& clazz = GetTypeFromSignature(name);
  Log::I("JNI::FindClass:%s", clazz.c_str());
  jclass ret = new FakeClass(clazz);
  ((FakeJNIEnv*)env)->objects_.insert(ret);
  return ret;
}

FAKE_JNI_GET_ID(jmethodID, GetStaticMethodID)
FAKE_JNI_GET_ID(jfieldID, GetStaticFieldID)
FAKE_JNI_GET_ID(jmethodID, GetMethodID)
FAKE_JNI_GET_ID(jfieldID, GetFieldID)

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
  Log::I("JNI::NewStringUTF:%s", utf);
  jstring ret = new FakeString(utf);
  ((FakeJNIEnv*)env)->objects_.insert(ret);
  return ret;
}

void FakeJNIEnv::DeleteLocalRef(JNIEnv* env, jobject obj) {
  Log::I("JNI::DeleteLocalRef");
}

jboolean FakeJNIEnv::CallStaticBooleanMethodV(JNIEnv* env, jclass clazz,
                                              jmethodID methodID,
                                              va_list args) {
  MEMBER_ACCESS(CallStaticBooleanMethodV, methodID)
  // Always force the agent to instrument.
  if (member->clazz == "com.android.tools.deploy.instrument.Breadcrumb" &&
      member->name == "isFinishedInstrumenting") {
    return false;
  }
  return true;
}

jobject FakeJNIEnv::CallStaticObjectMethodV(JNIEnv* env, jclass clazz,
                                            jmethodID methodID, va_list args) {
  MEMBER_ACCESS(CallStaticObjectMethodV, methodID)
  return (jobject) new FakeObject(member->type);
}

void FakeJNIEnv::CallStaticVoidMethodV(JNIEnv* env, jclass cls,
                                       jmethodID methodID, va_list args) {
  MEMBER_ACCESS(CallStaticVoidMethodV, methodID)
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

jobject FakeJNIEnv::CallObjectMethodV(JNIEnv* env, jobject obj,
                                      jmethodID methodID, va_list args) {
  MEMBER_ACCESS(CallObjectMethodV, methodID)
  if (member->name == "findClass") {
    FakeString* str = va_arg(args, FakeString*);
    // Behaves like the app is non-JetPack Compose app.
    // There is test coverage with FakeAndroid / Host ART for those.
    if (str->value == "androidx/compose/Compose$HotReloader") {
      return nullptr;
    }
    return (jobject) new FakeClass(GetTypeFromSignature(str->value));
  }
  return (jobject) new FakeObject(member->type);
}

void FakeJNIEnv::CallVoidMethodV(JNIEnv* env, jobject obj, jmethodID methodID,
                                 va_list args) {
  MEMBER_ACCESS(CallVoidMethodV, methodID)
  return;
}

jint FakeJNIEnv::GetStaticIntField(JNIEnv* env, jclass clazz,
                                   jfieldID fieldID) {
  MEMBER_ACCESS(GetStaticIntField, fieldID)
  return 0;
}

jobject FakeJNIEnv::GetStaticObjectField(JNIEnv* env, jclass clazz,
                                         jfieldID fieldID) {
  MEMBER_ACCESS(GetStaticObjectField, fieldID)
  return (jobject) new FakeObject(member->type);
}

jobject FakeJNIEnv::GetObjectField(JNIEnv* env, jobject obj, jfieldID fid) {
  MEMBER_ACCESS(GetObjectField, fid)
  return (jobject) new FakeObject(member->type);
}

jclass FakeJNIEnv::GetObjectClass(JNIEnv* env, jobject obj) {
  Log::I("JNI::GetObjectClass");
  FakeObject* object = (FakeObject*)obj;
  return (jclass) new FakeClass(object->type);
}

void FakeJNIEnv::SetObjectField(JNIEnv* env, jobject obj, jfieldID fieldID,
                                jobject val) {
  Log::I("JNI::SetObjectField");
}

const char* FakeJNIEnv::GetStringUTFChars(JNIEnv* env, jstring string,
                                          jboolean* isCopy) {
  Log::I("JNI::GetStringUTFChars");
  return "";
}

void FakeJNIEnv::ReleaseStringUTFChars(JNIEnv* env, jstring str,
                                       const char* chars) {
  Log::I("JNI::ReleaseStringUTFChars");
}

}  // namespace deploy
