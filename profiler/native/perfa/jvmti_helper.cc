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

bool CheckJvmtiError(jvmtiEnv* jvmti, jvmtiError err_num,
                     const std::string &message) {
  if (err_num == JVMTI_ERROR_NONE) {
    return false;
  }

  char* error = nullptr;
  jvmti->GetErrorName(err_num, &error);
  Log::E("JVMTI error: %d(%s) %s",
         err_num, error == nullptr ? "Unknown" : error, message.c_str());
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

std::string GetMangledName(const char* klass_signature,
                           const char* method_name) {
  std::string klass_string(klass_signature);
  std::string method_string(method_name);

  std::string mangled("Java_");
  mangled.append(MangleForJni(klass_string));
  mangled.append("_");
  mangled.append(MangleForJni(method_string));

  return mangled;
}

std::string MangleForJni(const std::string& mutf8) {
  std::stringstream ss;
  const char* char_ptr = &mutf8[0];
  const char* end = char_ptr + mutf8.length();
  while (char_ptr < end) {
    uint16_t ch = GetUtf16FromMutf8(&char_ptr);
    if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') ||
        (ch >= '0' && ch <= '9')) {
      ss << (char)ch;
    } else if (ch == '.' || ch == '/') {
      ss << "_";
    } else if (ch == '_') {
      ss << "_1";
    } else if (ch == ';') {
      ss << "_2";
    } else if (ch == '[') {
      ss << "_3";
    } else {
      ss << "_0" << std::setfill('0') << std::setw(4) << std::hex << ch;
    }
  }

  return ss.str();
}

uint16_t GetUtf16FromMutf8(const char** mutf8_data) {
  const uint8_t one = *(*mutf8_data)++;
  if ((one & 0x80) == 0) {
    // one-byte encoding
    return one;
  }

  const uint8_t two = *(*mutf8_data)++;
  if ((one & 0x20) == 0) {
    // two-byte encoding
    return ((one & 0x1f) << 6) | (two & 0x3f);
  }

  const uint8_t three = *(*mutf8_data)++;
  if ((one & 0x10) == 0) {
    // three-byte encoding
    return ((one & 0x0f) << 12) | ((two & 0x3f) << 6) | (three & 0x3f);
  }

  // TODO: Handle 6-byte encoding (high/low surrogate pairs)
  // In practice, we most likely will not need this as we don't have any method
  // names using anything outside the basic multilingual plane.
  *mutf8_data += 3;
  return 0;
}

}  // namespace profiler
