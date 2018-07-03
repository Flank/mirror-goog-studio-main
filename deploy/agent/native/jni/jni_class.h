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

#define NO_DEFAULT_SPECIALIZATION(T) static_assert(sizeof(T) == 0, "");

#include "jni.h"
#include "jni_object.h"
#include "jni_signature.h"

namespace swapper {

class JniClass {
 public:
  JniClass(JNIEnv* jni, const char* name) : jni_(jni) {
    // TODO: handle class not found.
    class_ = jni_->FindClass(name);
  }

  JniClass(JniClass&&) = default;

  ~JniClass() { jni_->DeleteLocalRef(class_); }

  template <typename T>
  T CallStaticMethod(const JniSignature& method) {
    jmethodID id =
        jni_->GetStaticMethodID(class_, method.name, method.signature);
    return JniCallStaticMethod<T>(id, {});
  }

  template <typename T>
  T CallStaticMethod(const JniSignature& method, jvalue* args) {
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

template <>
jint JniClass::JniGetStaticField(jfieldID);

template <>
void JniClass::JniCallStaticMethod(jmethodID, jvalue*);

template <>
jint JniClass::JniCallStaticMethod(jmethodID, jvalue*);

template <>
jboolean JniClass::JniCallStaticMethod(jmethodID, jvalue*);

template <>
JniObject JniClass::JniCallStaticMethod(jmethodID, jvalue*);

}  // namespace swapper

#endif