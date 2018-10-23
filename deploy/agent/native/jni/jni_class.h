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

#include <jni.h>

#include "tools/base/deploy/agent/native/jni/jni_object.h"
#include "tools/base/deploy/agent/native/jni/jni_signature.h"

namespace deploy {

class JniClass {
 public:
  JniClass(JNIEnv* jni, const char* name) : jni_(jni) {
    // TODO: handle class not found.
    class_ = jni_->FindClass(name);
  }

  ~JniClass() { jni_->DeleteLocalRef(class_); }

  JniClass& operator=(JniClass&&) = default;

  template <typename T>
  T CallStaticMethod(const JniSignature& method) {
    return CallStaticMethod<T>(method, nullptr);
  }

  template <typename T>
  T CallStaticMethod(const JniSignature& method, jvalue* args) {
    NO_DEFAULT_SPECIALIZATION(T)
  }

  template <typename T>
  T GetStaticField(const JniSignature& field) {
    NO_DEFAULT_SPECIALIZATION(T)
  }

 private:
  JNIEnv* jni_;
  jclass class_;

  JniClass(const JniClass&) = delete;
  JniClass& operator=(const JniClass&) = delete;
};

template <>
inline void JniClass::CallStaticMethod(const JniSignature& method,
                                       jvalue* args) {
  jmethodID id = jni_->GetStaticMethodID(class_, method.name, method.signature);
  jni_->CallStaticVoidMethodA(class_, id, args);
}

template <>
inline jboolean JniClass::CallStaticMethod(const JniSignature& method,
                                           jvalue* args) {
  jmethodID id = jni_->GetStaticMethodID(class_, method.name, method.signature);
  return jni_->CallStaticBooleanMethodA(class_, id, args);
}

template <>
inline jobject JniClass::CallStaticMethod(const JniSignature& method,
                                          jvalue* args) {
  jmethodID id = jni_->GetStaticMethodID(class_, method.name, method.signature);
  return jni_->CallStaticObjectMethodA(class_, id, args);
}

template <>
inline JniObject JniClass::CallStaticMethod(const JniSignature& method,
                                            jvalue* args) {
  jobject object = CallStaticMethod<jobject>(method, args);
  return JniObject(jni_, object);
}

}  // namespace deploy

#endif