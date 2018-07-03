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

#include "jni_class.h"

namespace swapper {

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

// Call static boolean methods.
template <>
jboolean JniClass::JniCallStaticMethod(jmethodID method, jvalue* args) {
  return jni_->CallStaticBooleanMethodA(class_, method, args);
}

// Call static object methods.
template <>
JniObject JniClass::JniCallStaticMethod(jmethodID method, jvalue* args) {
  jobject obj = jni_->CallStaticObjectMethodA(class_, method, args);
  return JniObject(jni_, obj);
}

}  // namespace swapper