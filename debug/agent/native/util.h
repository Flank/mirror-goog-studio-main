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

#ifndef UTIL_H_
#define UTIL_H_

#include <cstddef>

#include <jvmti.h>
#include "slicer/writer.h"

namespace debug {

void* Allocate(jvmtiEnv* jvmti, jlong size);

void Deallocate(jvmtiEnv* jvmti, void* ptr);

class JvmtiAllocator : public dex::Writer::Allocator {
 public:
  JvmtiAllocator(jvmtiEnv* jvmti) : jvmti_(jvmti) {}

  virtual void* Allocate(size_t size) override;

  virtual void Free(void* ptr) override;

 private:
  jvmtiEnv* jvmti_;
};

// Logs a message and returns true iff there was a JVMTI error.
bool CheckJvmtiError(jvmtiEnv* jvmti, jvmtiError err, const char* msg);

}  // namespace debug

#endif  // UTIL_H_
