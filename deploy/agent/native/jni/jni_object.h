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

#ifndef JNI_OBJECT_H
#define JNI_OBJECT_H

#define NO_DEFAULT_SPECIALIZATION(T) static_assert(sizeof(T) == 0, "");

#include "jni.h"
#include "jni_signature.h"

#include <string>

using std::string;

namespace deploy {

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

  string ToString();

  template <typename T>
  T CallMethod(const JniSignature& method) {
    jmethodID id = jni_->GetMethodID(class_, method.name, method.signature);
    return JniCallMethod<T>(id, {});
  }

  template <typename T>
  T CallMethod(const JniSignature& method, jvalue* args) {
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

template <>
void JniObject::JniCallMethod(jmethodID, jvalue*);

template <>
jobject JniObject::JniCallMethod(jmethodID, jvalue*);

template <>
JniObject JniObject::JniCallMethod(jmethodID, jvalue*);

template <>
jint JniObject::JniGetField(jfieldID);

template <>
void JniObject::JniSetField(jfieldID, jint);

}  // namespace deploy

#endif