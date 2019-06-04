/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "tools/base/debug/agent/native/util.h"

#include "tools/base/debug/agent/native/log.h"

namespace debug {

void* Allocate(jvmtiEnv* jvmti, jlong size) {
  unsigned char* ptr = nullptr;
  jvmtiError err = jvmti->Allocate(size, &ptr);
  CheckJvmtiError(jvmti, err, "Allocation failed");
  return ptr;
}

void Deallocate(jvmtiEnv* jvmti, void* ptr) {
  if (ptr != nullptr) {
    auto err = jvmti->Deallocate((unsigned char*)ptr);
    CheckJvmtiError(jvmti, err, "Deallocation failed");
  }
}

void* JvmtiAllocator::Allocate(size_t size) {
  return debug::Allocate(jvmti_, size);
}

void JvmtiAllocator::Free(void* ptr) { Deallocate(jvmti_, ptr); }

bool CheckJvmtiError(jvmtiEnv* jvmti, jvmtiError err, const char* msg) {
  if (err == JVMTI_ERROR_NONE) {
    return false;
  }
  // Note: to avoid recursion we ignore JVMTI errors within this method.
  char* name = nullptr;
  jvmti->GetErrorName(err, &name);
  auto desc = name != nullptr ? name : "Unknown";
  Log::E("JVMTI error: %d(%s) %s", err, desc, msg);
  jvmti->Deallocate((unsigned char*)name);
  return true;
}

}  // namespace debug
