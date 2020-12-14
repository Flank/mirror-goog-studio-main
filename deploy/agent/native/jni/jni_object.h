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

#include <jni.h>

#include <string>

#include "tools/base/deploy/agent/native/jni/jni_util.h"

#define NO_DEFAULT_SPECIALIZATION(T) static_assert(sizeof(T) == 0, "");

namespace deploy {

class JniObject {
 public:
  JniObject(JNIEnv* jni, jobject object) : jni_(jni), object_(object) {
    // TODO - handle case where object is null.
    class_ = jni_->GetObjectClass(object);
  }

  ~JniObject() { jni_->DeleteLocalRef(class_); }

  JniObject(JniObject&&) = default;
  JniObject& operator=(JniObject&&) = default;

  std::string ToString() {
    jstring value =
        (jstring)this->CallMethod<jobject>("toString", "()Ljava/lang/String;");
    std::string copy = JStringToString(jni_, value);
    jni_->DeleteLocalRef(value);
    return copy;
  }

  template <typename T>
  T CallMethod(const std::string& name, const std::string& signature) {
    return CallMethod<T>(name, signature, nullptr);
  }

  template <typename T>
  T CallMethod(const std::string& name, const std::string& signature,
               jvalue* args) {
    NO_DEFAULT_SPECIALIZATION(T)
  }

  template <typename T>
  T GetField(const std::string& name, const std::string& type) {
    NO_DEFAULT_SPECIALIZATION(T)
  }

  void SetField(const std::string& name, const std::string& type,
                jobject value) {
    jfieldID id = jni_->GetFieldID(class_, name.c_str(), type.c_str());
    jni_->SetObjectField(object_, id, value);
  }

 private:
  JNIEnv* jni_;
  jclass class_;
  jobject object_;

  JniObject(const JniObject&) = delete;
  JniObject& operator=(const JniObject&) = delete;
};

template <>
inline void JniObject::CallMethod(const std::string& name,
                                  const std::string& signature, jvalue* args) {
  jmethodID id = jni_->GetMethodID(class_, name.c_str(), signature.c_str());
  jni_->CallVoidMethodA(object_, id, args);
}

template <>
inline jobject JniObject::CallMethod(const std::string& name,
                                     const std::string& signature,
                                     jvalue* args) {
  jmethodID id = jni_->GetMethodID(class_, name.c_str(), signature.c_str());
  return jni_->CallObjectMethodA(object_, id, args);
}

template <>
inline JniObject JniObject::CallMethod(const std::string& name,
                                       const std::string& signature,
                                       jvalue* args) {
  jobject object = CallMethod<jobject>(name, signature, args);
  return JniObject(jni_, object);
}

template <>
inline jobject JniObject::GetField(const std::string& name,
                                   const std::string& type) {
  jfieldID id = jni_->GetFieldID(class_, name.c_str(), type.c_str());
  return jni_->GetObjectField(object_, id);
}

template <>
inline JniObject JniObject::GetField(const std::string& name,
                                     const std::string& type) {
  jfieldID id = jni_->GetFieldID(class_, name.c_str(), type.c_str());
  jobject object = jni_->GetObjectField(object_, id);
  return JniObject(jni_, object);
}

}  // namespace deploy

#endif