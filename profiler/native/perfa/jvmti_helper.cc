/*
 * Copyright (C) 2017 The Android Open Source Project
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
 */
#include "jvmti_helper.h"

#include "scoped_local_ref.h"
#include "stdlib.h"
#include "utils/log.h"

#include <iomanip>
#include <sstream>

namespace profiler {

jvmtiEnv* CreateJvmtiEnv(JavaVM* vm) {
  jvmtiEnv* jvmti_env;
  jint result = vm->GetEnv((void**)&jvmti_env, JVMTI_VERSION_1_2);
  if (result != JNI_OK) {
    Log::E("Error creating jvmti environment.");
    return nullptr;
  }

  return jvmti_env;
}

bool CheckJvmtiError(jvmtiEnv* jvmti, jvmtiError err_num) {
  if (err_num == JVMTI_ERROR_NONE) {
    return false;
  }

  char* error = nullptr;
  jvmti->GetErrorName(err_num, &error);
  Log::E("JVMTI error: %d(%s)", err_num, error == nullptr ? "Unknown" : error);
  Deallocate(jvmti, error);
  return true;
}

void SetAllCapabilities(jvmtiEnv* jvmti) {
  jvmtiCapabilities caps;
  jvmtiError error;
  error = jvmti->GetPotentialCapabilities(&caps);
  CheckJvmtiError(jvmti, error);
  error = jvmti->AddCapabilities(&caps);
  CheckJvmtiError(jvmti, error);
}

void SetEventNotification(jvmtiEnv* jvmti, jvmtiEventMode mode,
                          jvmtiEvent event_type) {
  jvmtiError err = jvmti->SetEventNotificationMode(mode, event_type, nullptr);
  CheckJvmtiError(jvmti, err);
}

JNIEnv* GetThreadLocalJNI(JavaVM* vm) {
  JNIEnv* jni;
  jint result =
      vm->GetEnv((void**)&jni, JNI_VERSION_1_6);  // ndk is only up to 1.6.
  if (result == JNI_EDETACHED) {
    Log::V("JNIEnv not attached");
#ifdef __ANDROID__
    if (vm->AttachCurrentThread(&jni, nullptr) != 0) {
#else
    // TODO get rid of this. Currently bazel built with the jdk's jni headers
    // which has a slightly different signature. Once bazel has swtiched to
    // platform-dependent headers we will remove this.
    if (vm->AttachCurrentThread((void**)&jni, nullptr) != 0) {
#endif
      Log::V("Failed to attach JNIEnv");
      return nullptr;
    }
  }

  return jni;
}

jthread AllocateJavaThread(jvmtiEnv* jvmti, JNIEnv* jni) {
  ScopedLocalRef<jclass> klass(jni, jni->FindClass("java/lang/Thread"));
  if (klass.get() == nullptr) {
    Log::E("Failed to find Thread class.");
  }

  jmethodID method = jni->GetMethodID(klass.get(), "<init>", "()V");
  if (method == nullptr) {
    Log::E("Failed to find Thread.<init> method.");
  }

  jthread result = jni->NewObject(klass.get(), method);
  if (result == nullptr) {
    Log::E("Failed to create new Thread object.");
  }

  return result;
}

void* Allocate(jvmtiEnv* jvmti, jlong size) {
  unsigned char* alloc = nullptr;
  jvmtiError err = jvmti->Allocate(size, &alloc);
  CheckJvmtiError(jvmti, err);
  return (void*)alloc;
}

void Deallocate(jvmtiEnv* jvmti, void* ptr) {
  if (ptr == nullptr) {
    return;
  }

  jvmtiError err = jvmti->Deallocate((unsigned char*)ptr);
  CheckJvmtiError(jvmti, err);
}

}  // namespace profiler
