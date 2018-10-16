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

#include "tools/base/deploy/agent/native/jni/jni_object.h"

#include <string>

#include "tools/base/deploy/agent/native/jni/jni_util.h"

using std::string;

namespace deploy {

string JniObject::ToString() {
  jstring value =
      (jstring)this->CallMethod<jobject>({"toString", "()Ljava/lang/String;"});

  string copy = JStringToString(jni_, value);
  jni_->DeleteLocalRef(value);

  return copy;
}

// Call void methods.
template <>
void JniObject::JniCallMethod(jmethodID method, jvalue* args) {
  jni_->CallVoidMethodA(object_, method, args);
}

// Call object methods, returning an unwrapped jobject. Cleanup of this
// reference is the responsibility of the caller.
template <>
jobject JniObject::JniCallMethod(jmethodID method, jvalue* args) {
  return jni_->CallObjectMethodA(object_, method, args);
}

// Call object methods, wrapping the returned object in a JniObject to allow
// call chaining/automatic cleanup of local references.
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

}  // namespace deploy