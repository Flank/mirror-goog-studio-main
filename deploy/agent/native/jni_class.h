/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#ifndef JNI_CLASS_H
#define JNI_CLASS_H

#include "jni.h"
#include "jni_object.h"
#include "jni_util.h"

namespace swapper {

class JniClass {
 public:
  JniClass(JNIEnv* jni, const char* name) : jni_(jni) {
    // TODO: handle class not found.
    class_ = jni_->FindClass(name);
  }

  JniClass(JniClass&&) = default;

  ~JniClass() { jni_->DeleteLocalRef(class_); }

  jmethodID GetMethodID(const JniSignature& method) {
    return jni_->GetMethodID(class_, method.name, method.signature);
  }

  jfieldID GetFieldID(const JniSignature& field) {
    return jni_->GetFieldID(class_, field.name, field.signature);
  }

  template <typename T>
  T CallStatic(const JniSignature& method) {
    jmethodID id =
        jni_->GetStaticMethodID(class_, method.name, method.signature);
    return JniCallStaticMethod<T>(id, {});
  }

  template <typename T>
  T CallStatic(const JniSignature& method, jvalue* args) {
    jmethodID id =
        jni_->GetStaticMethodID(class_, method.name, method.signature);
    return JniCallStaticMethod<T>(id, args);
  }

  template <typename T>
  T GetStaticField(const JniSignature& field) {
    jfieldID id = jni_->GetStaticFieldID(class_, field.name, field.signature);
    return JniGetStaticField<T>(id);
  }

 private:
  JNIEnv* jni_;
  jclass class_;

  JniClass(const JniClass&) = delete;
  JniClass& operator=(const JniClass&) = delete;

  template <typename T>
  T JniCallStaticMethod(jmethodID method, jvalue* args) {
    NO_DEFAULT_SPECIALIZATION(T)
  }

  template <typename T>
  T JniGetStaticField(jfieldID field) {
    NO_DEFAULT_SPECIALIZATION(T)
  }
};

// Add specializations as needed.

// Get static int fields.
template <>
jint JniClass::JniGetStaticField(jfieldID field) {
  return jni_->GetStaticIntField(class_, field);
}

// Call static void methods.
template <>
void JniClass::JniCallStaticMethod(jmethodID method, jvalue* args) {
  jni_->CallStaticVoidMethodA(class_, method, args);
}

// Call static int methods.
template <>
jint JniClass::JniCallStaticMethod(jmethodID method, jvalue* args) {
  return jni_->CallStaticIntMethodA(class_, method, args);
}

// Call static object methods.
template <>
JniObject JniClass::JniCallStaticMethod(jmethodID method, jvalue* args) {
  jobject obj = jni_->CallStaticObjectMethodA(class_, method, args);
  return JniObject(jni_, obj);
}

} // namespace swapper

#endif