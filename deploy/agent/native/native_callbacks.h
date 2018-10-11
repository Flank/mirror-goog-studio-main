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

#ifndef NATIVE_CALLBACKS_H
#define NATIVE_CALLBACKS_H

#include <vector>

#include <jni.h>
#include <jvmti.h>

namespace deploy {

struct NativeBinding {
  const char* class_name;
  JNINativeMethod native_method;

  NativeBinding(const char* name, const char* method_name,
                const char* method_signature, void* native_ptr)
      : class_name(const_cast<char*>(name)) {
    native_method.name = const_cast<char*>(method_name);
    native_method.signature = const_cast<char*>(method_signature);
    native_method.fnPtr = native_ptr;
  }
};

bool RegisterNative(JNIEnv* jni, const NativeBinding& binding);

// This method is bound to ActivityThreadInstrumentation#updateApplicationInfo()
void Native_UpdateApplicationInfo(JNIEnv* jni, jobject object,
                                  jobject activity_thread);
}  // namespace deploy

#endif
