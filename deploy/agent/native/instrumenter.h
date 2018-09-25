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

#ifndef INSTRUMENTER_H
#define INSTRUMENTER_H

#include "slicer/dex_ir.h"
#include "slicer/instrumentation.h"
#include "slicer/reader.h"
#include "slicer/writer.h"

#include "utils/log.h"

#include <memory>
#include <unordered_map>
#include "jvmti.h"

using std::shared_ptr;
using std::string;
using std::unordered_map;

namespace deploy {

bool InstrumentApplication(jvmtiEnv* jvmti, JNIEnv* jni,
                           const std::string& package_name);

// Probably should be in a utility header, but also only used here.
class JvmtiAllocator : public dex::Writer::Allocator {
 public:
  JvmtiAllocator(jvmtiEnv* jvmti) : jvmti_(jvmti) {}

  virtual void* Allocate(size_t size) {
    unsigned char* alloc = nullptr;
    jvmti_->Allocate(size, &alloc);
    return (void*)alloc;
  }

  virtual void Free(void* ptr) {
    if (ptr == nullptr) {
      return;
    }

    jvmti_->Deallocate((unsigned char*)ptr);
  }

 private:
  jvmtiEnv* jvmti_;
};

class Transform {
 public:
  virtual void Apply(std::shared_ptr<ir::DexFile> dex_ir) = 0;
  virtual ~Transform() = default;
};

class ActivityThreadHandlerTransform : public Transform {
 public:
  void Apply(shared_ptr<ir::DexFile> dex_ir) {
    static const ir::MethodId handleMessage("Landroid/app/ActivityThread$H;",
                                            "handleMessage",
                                            "(Landroid/os/Message;)V");
    static const ir::MethodId entryHook(
        "Lcom/android/tools/deploy/instrument/ActivityThreadHandlerWrapper;",
        "entryHook");

    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::EntryHook>(entryHook, true);
    if (!mi.InstrumentMethod(handleMessage)) {
      Log::E("Error instrumenting ActivityThread$H.handleMessage");
    }
  }
};

// TODO: Static globals are gross, but we also only have one class being
// instrumented, so anything more elegant feels like overkill right now. If we
// instrument a few more things, probably worth refactoring this.

void AddTransform(const string& class_name, Transform* transform);
const unordered_map<string, Transform*>& GetTransforms();
void DeleteTransforms();

}  // namespace deploy

#endif
