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

#include "jni_util.h"

namespace deploy {

// Sets the pointer jni to the current JNI instance.
bool GetJni(JavaVM* vm, JNIEnv*& jni) {
  if (vm->GetEnv((void**)&jni, JNI_VERSION_1_2) != JNI_OK) {
    return false;
  }
  return true;
}

// Sets the pointer jvmti to the current JVMTI instance.
bool GetJvmti(JavaVM* vm, jvmtiEnv*& jvmti) {
  if (vm->GetEnv((void**)&jvmti, JVMTI_VERSION_1_2) != JNI_OK) {
    return false;
  }
  return true;
}

string JStringToString(JNIEnv* jni, jstring str) {
  const char* nativeString = jni->GetStringUTFChars(str, 0);
  string copy(nativeString);
  jni->ReleaseStringUTFChars(str, nativeString);
  return copy;
}

}  // namespace deploy