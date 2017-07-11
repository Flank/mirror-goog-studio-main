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
#include <string>

#include "agent/agent.h"
#include "jvmti_helper.h"
#include "memory/memory_tracking_env.h"
#include "scoped_local_ref.h"
#include "utils/config.h"
#include "utils/log.h"

#include "dex/slicer/instrumentation.h"
#include "dex/slicer/reader.h"
#include "dex/slicer/writer.h"

using profiler::Log;
using profiler::Agent;
using profiler::MemoryTrackingEnv;
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

// Retrieve the app's data directory path
static std::string GetAppDataPath() {
  Dl_info dl_info;
  dladdr((void*)Agent_OnAttach, &dl_info);
  std::string so_path(dl_info.dli_fname);
  return so_path.substr(0, so_path.find_last_of('/') + 1);
}

static bool IsRetransformClassSignature(const char* sig_mutf8) {
  return (strcmp(sig_mutf8, "Ljava/net/URL;") == 0) ||
      (strcmp(sig_mutf8, "Lokhttp3/OkHttpClient;") == 0) ||
      (strcmp(sig_mutf8, "Lcom/squareup/okhttp/OkHttpClient;") == 0);
}

// ClassPrepare event callback to invoke transformation of selected
// classes, saves expensive OnClassFileLoaded calls for other classes.
void JNICALL OnClassPrepare(jvmtiEnv* jvmti_env, JNIEnv* jni_env,
                            jthread thread, jclass klass) {
  char* sig_mutf8;
  jvmti_env->GetClassSignature(klass, &sig_mutf8, nullptr);
  if (IsRetransformClassSignature(sig_mutf8)) {
    jvmti_env->SetEventNotificationMode(
        JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread);
    CheckJvmtiError(jvmti_env, jvmti_env->RetransformClasses(1, &klass));
    jvmti_env->SetEventNotificationMode(
        JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread);
  }
  if (sig_mutf8 != nullptr) {
    jvmti_env->Deallocate((unsigned char*) sig_mutf8);
  }
}

void JNICALL OnClassFileLoaded(jvmtiEnv* jvmti_env, JNIEnv* jni_env,
                               jclass class_being_redefined, jobject loader,
                               const char* name, jobject protection_domain,
                               jint class_data_len,
                               const unsigned char* class_data,
                               jint* new_class_data_len,
                               unsigned char** new_class_data) {
  bool transformed = true;
  if (strcmp(name, "java/net/URL") == 0) {
    dex::Reader reader(class_data, class_data_len);
    // The tooling interface will specify class names like "java/net/URL"
    // however, in .dex these classes are stored using the "Ljava/net/URL;"
    // format.
    std::string desc = "L" + std::string(name) + ";";
    auto class_index = reader.FindClassIndex(desc.c_str());
    if (class_index == dex::kNoIndex) {
      Log::V("Could not find class index for %s", name);
      return;
    }

    reader.CreateClassIr(class_index);
    auto dex_ir = reader.GetIr();

    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/network/httpurl/HttpURLWrapper;",
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
  } else if (strcmp(name, "okhttp3/OkHttpClient") == 0) {
    dex::Reader reader(class_data, class_data_len);
    std::string desc = "L" + std::string(name) + ";";
    auto class_index = reader.FindClassIndex(desc.c_str());
    if (class_index == dex::kNoIndex) {
      Log::V("Could not find class index for %s", name);
      return;
    }

    reader.CreateClassIr(class_index);
    auto dex_ir = reader.GetIr();

    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/network/okhttp/OkHttp3Wrapper;",
        "insertInterceptor"));
    if (!mi.InstrumentMethod(ir::MethodId(desc.c_str(), "networkInterceptors",
                                          "()Ljava/util/List;"))) {
      Log::E("Error instrumenting OkHttp3 OkHttpClient");
    }

    size_t new_image_size = 0;
    dex::u1* new_image = nullptr;
    dex::Writer writer(dex_ir);

    JvmtiAllocator allocator(jvmti_env);
    new_image = writer.CreateImage(&allocator, &new_image_size);

    *new_class_data_len = new_image_size;
    *new_class_data = new_image;
  } else if (strcmp(name, "com/squareup/okhttp/OkHttpClient") == 0) {
    dex::Reader reader(class_data, class_data_len);
    std::string desc = "L" + std::string(name) + ";";
    auto class_index = reader.FindClassIndex(desc.c_str());
    if (class_index == dex::kNoIndex) {
      Log::V("Could not find class index for %s", name);
      return;
    }

    reader.CreateClassIr(class_index);
    auto dex_ir = reader.GetIr();

    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/network/okhttp/OkHttp2Wrapper;",
        "insertInterceptor"));
    if (!mi.InstrumentMethod(ir::MethodId(desc.c_str(), "networkInterceptors",
                                          "()Ljava/util/List;"))) {
      Log::E("Error instrumenting OkHttp2 OkHttpClient");
    }

    size_t new_image_size = 0;
    dex::u1* new_image = nullptr;
    dex::Writer writer(dex_ir);

    JvmtiAllocator allocator(jvmti_env);
    new_image = writer.CreateImage(&allocator, &new_image_size);

    *new_class_data_len = new_image_size;
    *new_class_data = new_image;
  } else {
    transformed = false;
  }

  if (transformed) {
    Log::V("Transformed class: %s", name);
  }
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

void LoadDex(jvmtiEnv* jvmti, JNIEnv* jni, bool log_live_alloc_count) {
  // Load in perfa.jar which should be in to data/data.
  std::string agent_lib_path(GetAppDataPath());
  agent_lib_path.append("perfa.jar");
  jvmti->AddToBootstrapClassLoaderSearch(agent_lib_path.c_str());

  // TODO: Removed these once the auto-JNI-binding feature becomes
  // available in all published O system images.
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/network/"
                "HttpTracker$InputStreamTracker",
                "onClose", "(J)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/network/"
                "HttpTracker$InputStreamTracker",
                "onReadBegin", "(J)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/network/"
                "HttpTracker$InputStreamTracker",
                "reportBytes", "(J[B)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/network/"
                "HttpTracker$OutputStreamTracker",
                "onClose", "(J)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/network/"
                "HttpTracker$OutputStreamTracker",
                "onWriteBegin", "(J)V");
  BindJNIMethod(
      jni, "com/android/tools/profiler/support/network/HttpTracker$Connection",
      "nextId", "()J");
  BindJNIMethod(
      jni, "com/android/tools/profiler/support/network/HttpTracker$Connection",
      "trackThread", "(JLjava/lang/String;J)V");
  BindJNIMethod(
      jni, "com/android/tools/profiler/support/network/HttpTracker$Connection",
      "onPreConnect", "(JLjava/lang/String;Ljava/lang/String;)V");
  BindJNIMethod(
      jni, "com/android/tools/profiler/support/network/HttpTracker$Connection",
      "onRequestBody", "(J)V");
  BindJNIMethod(
      jni, "com/android/tools/profiler/support/network/HttpTracker$Connection",
      "onRequest", "(JLjava/lang/String;Ljava/lang/String;)V");
  BindJNIMethod(
      jni, "com/android/tools/profiler/support/network/HttpTracker$Connection",
      "onResponse", "(JLjava/lang/String;Ljava/lang/String;)V");
  BindJNIMethod(
      jni, "com/android/tools/profiler/support/network/HttpTracker$Connection",
      "onResponseBody", "(J)V");
  BindJNIMethod(
      jni, "com/android/tools/profiler/support/network/HttpTracker$Connection",
      "onDisconnect", "(J)V");
  BindJNIMethod(
      jni, "com/android/tools/profiler/support/network/HttpTracker$Connection",
      "onError", "(JLjava/lang/String;)V");

  BindJNIMethod(jni, "com/android/tools/profiler/support/memory/VmStatsSampler",
                "logAllocStats", "(II)V");

  BindJNIMethod(
      jni, "com/android/tools/profiler/support/event/InputConnectionWrapper",
      "sendKeyboardEvent", "(Ljava/lang/String;)V");
  BindJNIMethod(
      jni, "com/android/tools/profiler/support/event/WindowProfilerCallback",
      "sendTouchEvent", "(IJ)V");
  BindJNIMethod(
      jni, "com/android/tools/profiler/support/event/WindowProfilerCallback",
      "sendKeyEvent", "(Ljava/lang/String;J)V");

  BindJNIMethod(jni,
                "com/android/tools/profiler/support/profilers/EventProfiler",
                "sendActivityCreated", "(Ljava/lang/String;I)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/profilers/EventProfiler",
                "sendActivityStarted", "(Ljava/lang/String;I)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/profilers/EventProfiler",
                "sendActivityResumed", "(Ljava/lang/String;I)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/profilers/EventProfiler",
                "sendActivityPaused", "(Ljava/lang/String;I)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/profilers/EventProfiler",
                "sendActivityStopped", "(Ljava/lang/String;I)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/profilers/EventProfiler",
                "sendActivitySaved", "(Ljava/lang/String;I)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/profilers/EventProfiler",
                "sendActivityDestroyed", "(Ljava/lang/String;I)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/profilers/EventProfiler",
                "sendFragmentAdded", "(Ljava/lang/String;II)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/profilers/EventProfiler",
                "sendFragmentRemoved", "(Ljava/lang/String;II)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/profilers/EventProfiler",
                "sendRotationEvent", "(I)V");

  jclass service =
      jni->FindClass("com/android/tools/profiler/support/ProfilerService");
  jmethodID initialize = jni->GetStaticMethodID(service, "initialize", "(Z)V");
  jni->CallStaticVoidMethod(service, initialize, !log_live_alloc_count);
}

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* options,
                                                 void* reserved) {
  jvmtiEnv* jvmti_env = CreateJvmtiEnv(vm);
  if (jvmti_env == nullptr) {
    return JNI_ERR;
  }

  if (options == nullptr) {
    Log::E("Config file parameter was not specified");
    return JNI_ERR;
  }
  // TODO: Update options to support more than one argument if needed.
  profiler::Config config(options);
  Log::V("StudioProfilers agent attached.");
  auto agent_config = config.GetAgentConfig();
  Agent::Instance(Agent::SocketType::kAbstractSocket);

  JNIEnv* jni_env = GetThreadLocalJNI(vm);
  LoadDex(jvmti_env, jni_env, agent_config.use_live_alloc());

  SetAllCapabilities(jvmti_env);

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.ClassFileLoadHook = OnClassFileLoaded;
  callbacks.ClassPrepare = OnClassPrepare;
  CheckJvmtiError(jvmti_env,
                  jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks)));

  // Sample instrumentation
  std::vector<jclass> classes;
  jint class_count;
  jclass* loaded_classes;
  char* sig_mutf8;
  jvmti_env->GetLoadedClasses(&class_count, &loaded_classes);
  for (int i = 0; i < class_count; ++i) {
    jvmti_env->GetClassSignature(loaded_classes[i], &sig_mutf8, nullptr);
    if (IsRetransformClassSignature(sig_mutf8)) {
      classes.push_back(loaded_classes[i]);
    }
    if (sig_mutf8 != nullptr) {
      jvmti_env->Deallocate((unsigned char*) sig_mutf8);
    }
  }

  if (classes.size() > 0) {
    jthread thread = nullptr;
    jvmti_env->GetCurrentThread(&thread);
    jvmti_env->SetEventNotificationMode(
        JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread);
    CheckJvmtiError(jvmti_env,
                    jvmti_env->RetransformClasses(classes.size(), &classes[0]));
    jvmti_env->SetEventNotificationMode(
        JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread);
    if (thread != nullptr) {
      jni_env->DeleteLocalRef(thread);
    }
  }

  jvmti_env->SetEventNotificationMode(
      JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, nullptr);

  for (int i = 0; i < class_count; ++i) {
    jni_env->DeleteLocalRef(loaded_classes[i]);
  }
  jvmti_env->Deallocate(reinterpret_cast<unsigned char*>(loaded_classes));

  MemoryTrackingEnv::Instance(vm, agent_config.use_live_alloc());

  return JNI_OK;
}

}  // namespace profiler
