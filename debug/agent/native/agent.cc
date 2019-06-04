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

#include "tools/base/debug/agent/native/agent.h"

#include <dlfcn.h>
#include <cstddef>
#include <map>
#include <string>

#include <jni.h>
#include <jvmti.h>
#include "slicer/instrumentation.h"
#include "slicer/reader.h"
#include "slicer/writer.h"
#include "tools/base/debug/agent/native/async-stack/init.h"
#include "tools/base/debug/agent/native/log.h"
#include "tools/base/debug/agent/native/transform.h"
#include "tools/base/debug/agent/native/util.h"

namespace debug {

// Maps from a JNI class signature to applicable tranformations.
// Must not be mutated after the ClassFileLoadHook is installed, since
// it will be accessed by multiple class loading threads.
static std::multimap<std::string, std::unique_ptr<ClassTransform>>
    class_transforms;

void RegisterClassTransform(std::unique_ptr<ClassTransform> transform) {
  class_transforms.emplace(transform->class_desc(), std::move(transform));
}

static void JNICALL ClassFileLoadHook(
    jvmtiEnv* jvmti_env, JNIEnv* jni_env, jclass class_being_redefined,
    jobject loader, const char* name, jobject protection_domain,
    jint class_data_len, const unsigned char* class_data,
    jint* new_class_data_len, unsigned char** new_class_data) {
  // Find applicable transformations.
  std::string desc = "L" + std::string(name) + ";";
  auto range = class_transforms.equal_range(desc);
  auto begin = range.first;
  auto end = range.second;
  if (begin == end) {
    return;
  }

  Log::V("Instrumenting %s", name);

  // Find class index.
  dex::Reader reader(class_data, class_data_len);
  auto class_index = reader.FindClassIndex(desc.c_str());
  if (class_index == dex::kNoIndex) {
    Log::E("Could not find class index for %s", name);
    return;
  }

  // Apply transformations.
  reader.CreateClassIr(class_index);
  auto dex_ir = reader.GetIr();
  for (auto it = begin; it != end; ++it) {
    auto& transform = it->second;
    if (!transform->Apply(dex_ir)) {
      Log::E("Transformation failed for %s", name);
      // We abort instrumentation if any transform fails, because the failed
      // transform may have left [dex_ir] in a bad state.
      //
      // If we wanted better isolation between transformations, we could:
      // disable the faulty transformation; re-parse [class_data]; and then
      // retry the other transformations.
      return;
    }
  }

  // Write new dex image.
  dex::Writer writer(dex_ir);
  JvmtiAllocator allocator(jvmti_env);
  size_t new_image_size = 0;
  dex::u1* new_image = writer.CreateImage(&allocator, &new_image_size);

  *new_class_data_len = new_image_size;
  *new_class_data = new_image;
}

// Retransforms all loaded classes for which we
// have an applicable transformation.
static void RetransformLoadedClasses(jvmtiEnv* jvmti, JNIEnv* jni) {
  // Get loaded classes.
  jint num_loaded_classes;
  jclass* loaded_classes;
  auto err = jvmti->GetLoadedClasses(&num_loaded_classes, &loaded_classes);
  if (CheckJvmtiError(jvmti, err, "Failed to get loaded classes")) {
    return;
  }

  // Collect classes to retransform.
  std::vector<jclass> to_retransform;
  for (jint i = 0; i < num_loaded_classes; ++i) {
    char* sig;
    err = jvmti->GetClassSignature(loaded_classes[i], &sig, nullptr);
    if (CheckJvmtiError(jvmti, err, "Failed to get class signature")) {
      continue;
    }
    if (class_transforms.find(sig) != class_transforms.end()) {
      to_retransform.push_back(loaded_classes[i]);
    }
    Deallocate(jvmti, sig);
  }

  // Retransform classes.
  Log::V("Retransforming %zu class(es)", to_retransform.size());
  if (!to_retransform.empty()) {
    err =
        jvmti->RetransformClasses(to_retransform.size(), to_retransform.data());
    CheckJvmtiError(jvmti, err, "Failed to retransform loaded classes");
  }

  // Cleanup.
  for (jint i = 0; i < num_loaded_classes; ++i) {
    jni->DeleteLocalRef(loaded_classes[i]);
  }
  Deallocate(jvmti, loaded_classes);
}

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* options,
                                                 void* reserved) {
  // TODO: "Before P ClassFileLoadHook has significant performance overhead"

  InitAsyncStackInstrumentation();

  jvmtiEnv* jvmti;
  if (vm->GetEnv((void**)&jvmti, JVMTI_VERSION_1_2) != JNI_OK) {
    Log::E("Error retrieving JVMTI function table.");
    return JNI_ERR;
  }

  JNIEnv* jni;
  if (vm->GetEnv((void**)&jni, JNI_VERSION_1_2) != JNI_OK) {
    Log::E("Error retrieving JNI function table.");
    return JNI_ERR;
  }

  // Add debug agent dex code to class path.
  Dl_info dl_info;
  if (!dladdr((void*)Agent_OnAttach, &dl_info)) {
    Log::E("Could not find address for symbol Agent_OnAttach");
    return JNI_ERR;
  }
  std::string so_path(dl_info.dli_fname);
  auto filename_start = so_path.find_last_of('/') + 1;
  auto dex_path = so_path.substr(0, filename_start) + "debug.jar";
  auto err = jvmti->AddToBootstrapClassLoaderSearch(dex_path.c_str());
  if (CheckJvmtiError(jvmti, err, "Failed to inject agent dex code")) {
    return JNI_ERR;
  }

  // Set JVMTI capabilities.
  jvmtiCapabilities capabilities = {};
  capabilities.can_retransform_classes = 1;
  err = jvmti->AddCapabilities(&capabilities);
  if (CheckJvmtiError(jvmti, err, "Failed to add capabilities")) {
    return JNI_ERR;
  }

  // Set JVMTI callbacks.
  jvmtiEventCallbacks callbacks = {};
  callbacks.ClassFileLoadHook = ClassFileLoadHook;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (CheckJvmtiError(jvmti, err, "Failed to set event callbacks")) {
    return JNI_ERR;
  }

  // Enable JVMTI events.
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                                        JVMTI_EVENT_CLASS_FILE_LOAD_HOOK,
                                        /*thread*/ nullptr);
  if (CheckJvmtiError(jvmti, err, "Failed to enable events")) {
    return JNI_ERR;
  }

  Log::V("Studio debug agent initialized");

  // Apply transformations to classes already loaded.
  RetransformLoadedClasses(jvmti, jni);

  return JNI_OK;
}

}  // namespace debug
