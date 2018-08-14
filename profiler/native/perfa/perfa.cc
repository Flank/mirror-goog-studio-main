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
#include <cassert>
#include <string>
#include <unordered_map>

#include "agent/agent.h"
#include "jvmti_helper.h"
#include "memory/memory_tracking_env.h"
#include "scoped_local_ref.h"
#include "utils/config.h"
#include "utils/log.h"

#include "slicer/reader.h"
#include "slicer/writer.h"

#include "transform/android_activitythread_transform.h"
#include "transform/android_alarmmanager_listenerwrapper_transform.h"
#include "transform/android_alarmmanager_transform.h"
#include "transform/android_debug_transform.h"
#include "transform/android_fragment_transform.h"
#include "transform/android_instrumentation_transform.h"
#include "transform/android_intentservice_transform.h"
#include "transform/android_jobschedulerimpl_transform.h"
#include "transform/android_jobservice_transform.h"
#include "transform/android_jobserviceengine_jobhandler_transform.h"
#include "transform/android_locationmanager_listenertransport_transform.h"
#include "transform/android_locationmanager_transform.h"
#include "transform/android_pendingintent_transform.h"
#include "transform/android_powermanager_transform.h"
#include "transform/android_powermanager_wakelock_transform.h"
#include "transform/gms_fusedlocationproviderclient_transform.h"
#include "transform/java_url_transform.h"
#include "transform/okhttp3_okhttpclient_transform.h"
#include "transform/okhttp_okhttpclient_transform.h"
#include "transform/transform.h"

using profiler::Agent;
using profiler::Log;
using profiler::MemoryTrackingEnv;
using profiler::ScopedLocalRef;
using profiler::proto::AgentConfig;

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

std::unordered_map<std::string, Transform*>* GetClassTransforms() {
  static auto* transformations =
      new std::unordered_map<std::string, Transform*>();
  return transformations;
}

// Retrieve the app's data directory path
static std::string GetAppDataPath() {
  Dl_info dl_info;
  dladdr((void*)Agent_OnAttach, &dl_info);
  std::string so_path(dl_info.dli_fname);
  return so_path.substr(0, so_path.find_last_of('/') + 1);
}

// ClassPrepare event callback to invoke transformation of selected classes.
// In pre-P, this saves expensive OnClassFileLoaded calls for other classes.
void JNICALL OnClassPrepare(jvmtiEnv* jvmti_env, JNIEnv* jni_env,
                            jthread thread, jclass klass) {
  char* sig_mutf8;
  jvmti_env->GetClassSignature(klass, &sig_mutf8, nullptr);
  auto class_transforms = GetClassTransforms();
  if (class_transforms->find(sig_mutf8) != class_transforms->end()) {
    CheckJvmtiError(
        jvmti_env, jvmti_env->SetEventNotificationMode(
                       JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread));
    CheckJvmtiError(jvmti_env, jvmti_env->RetransformClasses(1, &klass));
    CheckJvmtiError(jvmti_env, jvmti_env->SetEventNotificationMode(
                                   JVMTI_DISABLE,
                                   JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread));
  }
  if (sig_mutf8 != nullptr) {
    jvmti_env->Deallocate((unsigned char*)sig_mutf8);
  }
}

void JNICALL OnClassFileLoaded(jvmtiEnv* jvmti_env, JNIEnv* jni_env,
                               jclass class_being_redefined, jobject loader,
                               const char* name, jobject protection_domain,
                               jint class_data_len,
                               const unsigned char* class_data,
                               jint* new_class_data_len,
                               unsigned char** new_class_data) {
  // The tooling interface will specify class names like "java/net/URL"
  // however, in .dex these classes are stored using the "Ljava/net/URL;"
  // format.
  std::string desc = "L" + std::string(name) + ";";
  auto class_transforms = GetClassTransforms();
  auto transform = class_transforms->find(desc);
  if (transform == class_transforms->end()) return;

  dex::Reader reader(class_data, class_data_len);
  auto class_index = reader.FindClassIndex(desc.c_str());
  if (class_index == dex::kNoIndex) {
    Log::V("Could not find class index for %s", name);
    return;
  }

  reader.CreateClassIr(class_index);
  auto dex_ir = reader.GetIr();
  transform->second->Apply(dex_ir);

  size_t new_image_size = 0;
  dex::u1* new_image = nullptr;
  dex::Writer writer(dex_ir);

  JvmtiAllocator allocator(jvmti_env);
  new_image = writer.CreateImage(&allocator, &new_image_size);

  *new_class_data_len = new_image_size;
  *new_class_data = new_image;
  Log::V("Transformed class: %s", name);
}  // namespace profiler

void LoadDex(jvmtiEnv* jvmti, JNIEnv* jni) {
  // Load in perfa.jar which should be in to data/data.
  std::string agent_lib_path(GetAppDataPath());
  agent_lib_path.append("perfa.jar");
  jvmti->AddToBootstrapClassLoaderSearch(agent_lib_path.c_str());
}

// Populate the map of transforms we want to apply to different classes.
void RegisterTransforms(
    const proto::AgentConfig& config,
    std::unordered_map<std::string, Transform*>* transforms) {
  transforms->insert({"Ljava/net/URL;", new JavaUrlTransform()});
  transforms->insert({"Lokhttp3/OkHttpClient;", new Okhttp3ClientTransform()});
  transforms->insert(
      {"Lcom/squareup/okhttp/OkHttpClient;", new OkhttpClientTransform()});
  if (config.cpu_api_tracing_enabled()) {
    transforms->insert({"Landroid/os/Debug;", new AndroidDebugTransform()});
  }
  transforms->insert({"Landroid/support/v4/app/Fragment;", new AndroidFragmentTransform()});

  if (config.energy_profiler_enabled()) {
    transforms->insert({"Landroid/app/Instrumentation;",
                        new AndroidInstrumentationTransform()});
    transforms->insert(
        {"Landroid/app/ActivityThread;", new AndroidActivityThreadTransform()});
    transforms->insert(
        {"Landroid/app/AlarmManager;", new AndroidAlarmManagerTransform()});
    transforms->insert({"Landroid/app/AlarmManager$ListenerWrapper;",
                        new AndroidAlarmManagerListenerWrapperTransform()});
    transforms->insert(
        {"Landroid/app/IntentService;", new AndroidIntentServiceTransform()});
    transforms->insert({"Landroid/app/JobSchedulerImpl;",
                        new AndroidJobSchedulerImplTransform()});
    transforms->insert(
        {"Landroid/app/job/JobService;", new AndroidJobServiceTransform()});
    transforms->insert({"Landroid/app/job/JobServiceEngine$JobHandler;",
                        new AndroidJobServiceEngineJobHandlerTransform()});
    transforms->insert(
        {"Landroid/app/PendingIntent;", new AndroidPendingIntentTransform()});
    transforms->insert({"Landroid/location/LocationManager;",
                        new AndroidLocationManagerTransform()});
    transforms->insert(
        {"Landroid/location/LocationManager$ListenerTransport;",
         new AndroidLocationManagerListenerTransportTransform()});
    transforms->insert(
        {"Landroid/os/PowerManager;", new AndroidPowerManagerTransform()});
    transforms->insert({"Landroid/os/PowerManager$WakeLock;",
                        new AndroidPowerManagerWakeLockTransform()});
    transforms->insert(
        {"Lcom/google/android/gms/location/FusedLocationProviderClient;",
         new GmsFusedLocationProviderClientTransform()});
  }
}

void ProfilerInitializationWorker(jvmtiEnv* jvmti, JNIEnv* jni, void* ptr) {
  proto::AgentConfig* config = static_cast<proto::AgentConfig*>(ptr);
  jclass service =
      jni->FindClass("com/android/tools/profiler/support/ProfilerService");
  jmethodID initialize = jni->GetStaticMethodID(service, "initialize", "(Z)V");
  bool log_live_alloc_count = config->mem_config().use_live_alloc();
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

  SetAllCapabilities(jvmti_env);

  // TODO: Update options to support more than one argument if needed.
  static const auto* const config = new profiler::Config(options);
  auto const& agent_config = config->GetAgentConfig();
  Agent::Instance(config);

  JNIEnv* jni_env = GetThreadLocalJNI(vm);
  LoadDex(jvmti_env, jni_env);

  auto class_transforms = GetClassTransforms();
  RegisterTransforms(agent_config, class_transforms);

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.ClassFileLoadHook = OnClassFileLoaded;
  callbacks.ClassPrepare = OnClassPrepare;
  CheckJvmtiError(jvmti_env,
                  jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks)));

  // Before P ClassFileLoadHook has significant performance overhead so we
  // only enable the hook during retransformation (on agent attach and class
  // prepare). For P+ we want to keep the hook events always on to support
  // multiple retransforming agents (and therefore don't need to perform
  // retransformation on class prepare).
  bool filter_class_load_hook = agent_config.android_feature_level() <= 27;
  SetEventNotification(jvmti_env,
                       filter_class_load_hook ? JVMTI_ENABLE : JVMTI_DISABLE,
                       JVMTI_EVENT_CLASS_PREPARE);
  SetEventNotification(jvmti_env,
                       filter_class_load_hook ? JVMTI_DISABLE : JVMTI_ENABLE,
                       JVMTI_EVENT_CLASS_FILE_LOAD_HOOK);

  // Sample instrumentation
  std::vector<jclass> classes;
  jint class_count;
  jclass* loaded_classes;
  char* sig_mutf8;
  jvmti_env->GetLoadedClasses(&class_count, &loaded_classes);
  for (int i = 0; i < class_count; ++i) {
    jvmti_env->GetClassSignature(loaded_classes[i], &sig_mutf8, nullptr);
    if (class_transforms->find(sig_mutf8) != class_transforms->end()) {
      classes.push_back(loaded_classes[i]);
    }
    if (sig_mutf8 != nullptr) {
      jvmti_env->Deallocate((unsigned char*)sig_mutf8);
    }
  }

  if (classes.size() > 0) {
    jthread thread = nullptr;
    jvmti_env->GetCurrentThread(&thread);
    if (filter_class_load_hook) {
      CheckJvmtiError(jvmti_env, jvmti_env->SetEventNotificationMode(
                                     JVMTI_ENABLE,
                                     JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread));
    }
    CheckJvmtiError(jvmti_env,
                    jvmti_env->RetransformClasses(classes.size(), &classes[0]));
    if (filter_class_load_hook) {
      CheckJvmtiError(jvmti_env, jvmti_env->SetEventNotificationMode(
                                     JVMTI_DISABLE,
                                     JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread));
    }
    if (thread != nullptr) {
      jni_env->DeleteLocalRef(thread);
    }
  }

  for (int i = 0; i < class_count; ++i) {
    jni_env->DeleteLocalRef(loaded_classes[i]);
  }
  jvmti_env->Deallocate(reinterpret_cast<unsigned char*>(loaded_classes));

  Agent::Instance().AddPerfdConnectedCallback([vm, &agent_config] {
    // MemoryTackingEnv needs a connection to perfd, which may not be always the
    // case. If we don't postpone until there is a connection, MemoryTackingEnv
    // is going to busy-wait, so not allowing the application to finish
    // initialization. This callback will be called each time perfd connects.
    MemoryTrackingEnv::Instance(vm, agent_config.mem_config());
    // Starts the heartbeat thread after MemoryTrackingEnv is fully initialized
    // and has opened a grpc stream perfd. The order is important as a heartbeat
    // will trigger Studio to start live allocation tracking.
    Agent::Instance().StartHeartbeat();
    // Perf-test currently waits on this message to determine that perfa is
    // connected to perfd.
    Log::V("Perfa connected to Perfd.");
  });

  // ProfilerService#Initialize depends on JNI native methods being auto-binded
  // after the agent finishes attaching. Therefore we call initialize after
  // the VM is unpaused to make sure the runtime can auto-find the JNI methods.
  jvmti_env->RunAgentThread(AllocateJavaThread(jvmti_env, jni_env),
                            &ProfilerInitializationWorker, &agent_config,
                            JVMTI_THREAD_NORM_PRIORITY);

  return JNI_OK;
}

}  // namespace profiler
