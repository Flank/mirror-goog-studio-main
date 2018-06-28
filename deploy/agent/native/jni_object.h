#ifndef JNI_OBJECT_H
#define JNI_OBJECT_H

#include "jni.h"
#include "jni_util.h"
#include "utils/log.h"

namespace swapper {

class JniObject {
 public:
  JniObject(JNIEnv* jni, jobject object) : jni_(jni), object_(object) {
    // TODO - handle case where object is null.
    class_ = jni_->GetObjectClass(object);
  }

  JniObject(JniObject&&) = default;

  ~JniObject() {
    jni_->DeleteLocalRef(object_);
    jni_->DeleteLocalRef(class_);
  }

  jobject GetJObject() { return object_; }

  template <typename T>
  T Call(const JniSignature& method) {
    jmethodID id = jni_->GetMethodID(class_, method.name, method.signature);
    return JniCallMethod<T>(id, {});
  }

  template <typename T>
  T Call(const JniSignature& method, jvalue* args) {
    jmethodID id = jni_->GetMethodID(class_, method.name, method.signature);
    return JniCallMethod<T>(id, args);
  }

  template <typename T>
  T GetField(const JniSignature& field) {
    jfieldID id = jni_->GetFieldID(class_, field.name, field.signature);
    return JniGetField<T>(id);
  }

  template <typename T>
  void SetField(const JniSignature& field, T value) {
    jfieldID id = jni_->GetFieldID(class_, field.name, field.signature);
    JniSetField<T>(id, value);
  }

 private:
  JNIEnv* jni_;
  jclass class_;
  jobject object_;

  JniObject(const JniObject&) = delete;
  JniObject& operator=(const JniObject&) = delete;

  template <typename T>
  T JniCallMethod(jmethodID method, jvalue* args) {
    NO_DEFAULT_SPECIALIZATION(T)
  }

  template <typename T>
  void JniSetField(jfieldID field, T value) {
    NO_DEFAULT_SPECIALIZATION(T)
  }

  template <typename T>
  T JniGetField(jfieldID field) {
    NO_DEFAULT_SPECIALIZATION(T)
  }
};

// Add specializations as needed.

// Call void methods.
template <>
void JniObject::JniCallMethod(jmethodID method, jvalue* args) {
  jni_->CallVoidMethodA(object_, method, args);
}

// Call object methods.
template <>
JniObject JniObject::JniCallMethod(jmethodID method, jvalue* args) {
  jobject obj = jni_->CallObjectMethodA(object_, method, args);
  return JniObject(jni_, obj);
}

// Get int fields.
template <>
jint JniObject::JniGetField(jfieldID field) {
  return jni_->GetIntField(object_, field);
}

// Set int fields.
template <>
void JniObject::JniSetField(jfieldID field, jint value) {
  jni_->SetIntField(object_, field, value);
}

} // namespace swapper

#endif