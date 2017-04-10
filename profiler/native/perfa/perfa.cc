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
 *
 */
#include "jvmti.h"

#include <dlfcn.h>
#include <unistd.h>
#include <algorithm>

#include "agent/agent.h"
#include "jvmti_helper.h"
#include "scoped_local_ref.h"
#include "utils/log.h"

#include "dex/slicer/code_ir.h"
#include "dex/slicer/dex_ir.h"
#include "dex/slicer/dex_ir_builder.h"
#include "dex/slicer/reader.h"
#include "dex/slicer/writer.h"
#include "dex/slicer/instrumentation.h"

using profiler::Log;
using profiler::Agent;
using profiler::ScopedLocalRef;

namespace profiler {

class JvmtiAllocator : public dex::Writer::Allocator {
 public:
  JvmtiAllocator(jvmtiEnv* jvmti_env) : jvmti_env_(jvmti_env) {}

  virtual void* Allocate(size_t size) {
    return profiler::Allocate(jvmti_env_, size);
  }

  virtual void Free(void* ptr) { profiler::Deallocate(jvmti_env_, ptr); }

 private:
  jvmtiEnv* jvmti_env_;
};

void JNICALL OnClassFileLoaded(jvmtiEnv* jvmti_env, JNIEnv* jni_env,
                               jclass class_being_redefined, jobject loader,
                               const char* name, jobject protection_domain,
                               jint class_data_len,
                               const unsigned char* class_data,
                               jint* new_class_data_len,
                               unsigned char** new_class_data) {
  if (class_being_redefined == nullptr || strcmp(name, "java/net/URL")) {
    return;
  }

  dex::Reader reader(class_data, class_data_len);
  // The tooling interface will specify class names like "java/net/URL"
  // however, in .dex these classes are stored using the "Ljava/net/URL;"
  // format.
  std::string desc = "L" + std::string(name) + ";";
  auto class_index = reader.FindClassIndex(desc.c_str());
  if (class_index == dex::kNoIndex) {
    Log::V("Could not find");
    return;
  }

  reader.CreateClassIr(class_index);
  auto dex_ir = reader.GetIr();

  slicer::MethodInstrumenter mi(dex_ir);
  mi.AddTransformation<slicer::EntryHook>(ir::MethodId(
      "Lcom/android/tools/profiler/ProfilerAgent;", "urlOpenConnection"));
  mi.AddTransformation<slicer::DetourVirtualInvoke>(
      ir::MethodId("LBase;", "foo", "(ILjava/lang/String;)I"),
      ir::MethodId("LTracer;", "wrapFoo"));

  if (!mi.InstrumentMethod(
          ir::MethodId(desc.c_str(), "openConnection", "()Ljava/net/URLConnection;"))) {
    Log::E("Error instrumenting URL.openConnection");
  }

  size_t new_image_size = 0;
  dex::u1* new_image = nullptr;
  dex::Writer writer(dex_ir);

  JvmtiAllocator allocator(jvmti_env);
  new_image = writer.CreateImage(&allocator, &new_image_size);

  *new_class_data_len = new_image_size;
  *new_class_data = new_image;
  Log::V("Transformed class: %s", name);
}

void LoadDex(jvmtiEnv* jvmti) {
  // Load in perfa.jar which should be in to data/data.
  Dl_info dl_info;
  dladdr((void*)Agent_OnAttach, &dl_info);
  std::string so_path(dl_info.dli_fname);
  std::string agent_lib_path(so_path.substr(0, so_path.find_last_of('/')));
  agent_lib_path.append("/perfa.jar");
  jvmti->AddToBootstrapClassLoaderSearch(agent_lib_path.c_str());
}

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* options,
                                                 void* reserved) {
  jvmtiEnv* jvmti_env;

  Log::V("StudioProfilers agent attached.");
  jint result = vm->GetEnv((void**)&jvmti_env, JVMTI_VERSION_1_2);
  if (result != JNI_OK) {
    Log::E("Error creating jvmti environment.");
    return result;
  }

  LoadDex(jvmti_env);

  SetAllCapabilities(jvmti_env);
  jvmti_env->SetEventNotificationMode(
      JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(jvmtiEventCallbacks));
  callbacks.ClassFileLoadHook = OnClassFileLoaded;
  CheckJvmtiError(jvmti_env,
                  jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks)));

  // Sample instrumentation
  JNIEnv* jniEnv = GetThreadLocalJNI(vm);
  jclass klass = jniEnv->FindClass("java/net/URL");
  jclass classes[] = {klass};
  CheckJvmtiError(jvmti_env, jvmti_env->RetransformClasses(1, classes));

  Agent::Instance();
  return result;
}

extern "C" JNIEXPORT void JNICALL Agent_OnUnload(JavaVM* vm) {
  Log::V("StudioProfilers agent unloaded.");
}

}  // namespace profiler
