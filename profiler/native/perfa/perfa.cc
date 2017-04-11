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
#include "memory/memory_agent.h"
#include "scoped_local_ref.h"
#include "utils/log.h"

#include "dex/slicer/instrumentation.h"
#include "dex/slicer/reader.h"
#include "dex/slicer/writer.h"

using profiler::Log;
using profiler::Agent;
using profiler::MemoryAgent;
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
  if (class_being_redefined == nullptr || strcmp(name, "java/net/URL") != 0) {
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
  mi.AddTransformation<slicer::ExitHook>(
      ir::MethodId("Lcom/android/tools/profiler/support/network/HttpWrapper;",
                   "wrapURLConnection"));
  if (!mi.InstrumentMethod(ir::MethodId(desc.c_str(), "openConnection",
                                        "()Ljava/net/URLConnection;"))) {
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

void BindJNIMethod(JNIEnv* jni, const char* class_name, const char* method_name,
                   const char* signature) {
  jclass klass = jni->FindClass(class_name);
  std::string mangled_name(GetMangledName(class_name, method_name));
  void* sym = dlsym(RTLD_DEFAULT, mangled_name.c_str());
  if (sym != nullptr) {
    JNINativeMethod native_method;
    native_method.fnPtr = sym;
    native_method.name = const_cast<char*>(method_name);
    native_method.signature = const_cast<char*>(signature);
    jni->RegisterNatives(klass, &native_method, 1);
  } else {
    Log::V("Failed to find symbol for %s", mangled_name.c_str());
  }
}

void LoadDex(jvmtiEnv* jvmti, JNIEnv* jni) {
  // Load in perfa.jar which should be in to data/data.
  Dl_info dl_info;
  dladdr((void*)Agent_OnAttach, &dl_info);
  std::string so_path(dl_info.dli_fname);
  std::string agent_lib_path(so_path.substr(0, so_path.find_last_of('/')));
  agent_lib_path.append("/perfa.jar");
  jvmti->AddToBootstrapClassLoaderSearch(agent_lib_path.c_str());

  // We need to manually bind these two methods, because they are called from a thread that spanws
  // in the "initialize" call, and the agent functions are only available after attaching.
  // We will remove this once the tracking is done via JVMTI.
  BindJNIMethod(jni, "com/android/tools/profiler/support/memory/VmStatsSampler", "logAllocStats", "(II)V");
  BindJNIMethod(jni, "com/android/tools/profiler/support/memory/VmStatsSampler", "logGcStats", "()V");

  jclass service =
      jni->FindClass("com/android/tools/profiler/support/ProfilerService");
  jmethodID initialize = jni->GetStaticMethodID(service, "initialize", "()V");
  jni->CallStaticVoidMethod(service, initialize);
}

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* options,
                                                 void* reserved) {
  jvmtiEnv* jvmti_env = CreateJvmtiEnv(vm);
  if (jvmti_env == nullptr) {
    return JNI_ERR;
  }

  Log::V("StudioProfilers agent attached.");

  JNIEnv* jni_env = GetThreadLocalJNI(vm);
  LoadDex(jvmti_env, jni_env);

  SetAllCapabilities(jvmti_env);
  jvmti_env->SetEventNotificationMode(
      JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(jvmtiEventCallbacks));
  callbacks.ClassFileLoadHook = OnClassFileLoaded;
  CheckJvmtiError(jvmti_env,
                  jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks)));

  // Sample instrumentation
  jclass klass = jni_env->FindClass("java/net/URL");
  jclass classes[] = {klass};
  CheckJvmtiError(jvmti_env, jvmti_env->RetransformClasses(1, classes));

  Agent::Instance(Agent::SocketType::kAbstractSocket);
  MemoryAgent::Instance(vm);

  return JNI_OK;
}

}  // namespace profiler
