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

#include "slicer/instrumentation.h"
#include "slicer/reader.h"
#include "slicer/writer.h"

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

proto::AgentConfig agent_config;

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
         (strcmp(sig_mutf8, "Lcom/squareup/okhttp/OkHttpClient;") == 0) ||
         (strcmp(sig_mutf8, "Landroid/os/Debug;") == 0 &&
          agent_config.cpu_api_tracing_enabled()) ||
         (agent_config.energy_profiler_enabled() &&
          (strcmp(sig_mutf8, "Landroid/app/Instrumentation;") == 0 ||
           strcmp(sig_mutf8, "Landroid/app/ActivityThread;") == 0 ||
           strcmp(sig_mutf8, "Landroid/app/AlarmManager;") == 0 ||
           strcmp(sig_mutf8, "Landroid/app/AlarmManager$ListenerWrapper;") ==
               0 ||
           strcmp(sig_mutf8, "Landroid/app/IntentService;") == 0 ||
           strcmp(sig_mutf8, "Landroid/app/JobSchedulerImpl;") == 0 ||
           strcmp(sig_mutf8, "Landroid/app/job/JobService;") == 0 ||
           strcmp(sig_mutf8, "Landroid/app/job/JobServiceEngine$JobHandler;") ==
               0 ||
           strcmp(sig_mutf8, "Landroid/app/PendingIntent;") == 0 ||
           strcmp(sig_mutf8, "Landroid/location/LocationManager;") == 0 ||
           strcmp(sig_mutf8,
                  "Landroid/location/LocationManager$ListenerTransport;") ==
               0 ||
           strcmp(sig_mutf8, "Landroid/os/PowerManager;") == 0 ||
           strcmp(sig_mutf8, "Landroid/os/PowerManager$WakeLock;") == 0 ||
           strcmp(sig_mutf8,
                  "Lcom/google/android/gms/location/"
                  "FusedLocationProviderClient;") == 0));
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
  if (!IsRetransformClassSignature(desc.c_str())) return;

  dex::Reader reader(class_data, class_data_len);
  auto class_index = reader.FindClassIndex(desc.c_str());
  if (class_index == dex::kNoIndex) {
    Log::V("Could not find class index for %s", name);
    return;
  }

  reader.CreateClassIr(class_index);
  auto dex_ir = reader.GetIr();

  if (strcmp(name, "java/net/URL") == 0) {
    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/network/httpurl/HttpURLWrapper;",
        "wrapURLConnection"));
    if (!mi.InstrumentMethod(ir::MethodId(desc.c_str(), "openConnection",
                                          "()Ljava/net/URLConnection;"))) {
      Log::E("Error instrumenting URL.openConnection");
    }
  } else if (strcmp(name, "okhttp3/OkHttpClient") == 0) {
    slicer::MethodInstrumenter mi(dex_ir);
    // Add Entry hook method with this argument passed as type Object.
    mi.AddTransformation<slicer::EntryHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/network/okhttp/"
                     "OkHttp3Wrapper;",
                     "setOkHttpClassLoader"),
        true);
    mi.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/network/okhttp/OkHttp3Wrapper;",
        "insertInterceptor"));
    if (!mi.InstrumentMethod(ir::MethodId(desc.c_str(), "networkInterceptors",
                                          "()Ljava/util/List;"))) {
      Log::E("Error instrumenting OkHttp3 OkHttpClient");
    }
  } else if (strcmp(name, "com/squareup/okhttp/OkHttpClient") == 0) {
    slicer::MethodInstrumenter mi(dex_ir);
    // Add Entry hook method with this argument passed as type Object.
    mi.AddTransformation<slicer::EntryHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/network/okhttp/"
                     "OkHttp2Wrapper;",
                     "setOkHttpClassLoader"),
        true);
    mi.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/network/okhttp/OkHttp2Wrapper;",
        "insertInterceptor"));
    if (!mi.InstrumentMethod(ir::MethodId(desc.c_str(), "networkInterceptors",
                                          "()Ljava/util/List;"))) {
      Log::E("Error instrumenting OkHttp2 OkHttpClient");
    }
  } else if (strcmp(name, "android/os/Debug") == 0) {
    // Instrument startMethodTracing(String tracePath) at entry.
    slicer::MethodInstrumenter mi_start(dex_ir);
    mi_start.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/cpu/TraceOperationTracker;",
        "onStartMethodTracing"));
    if (!mi_start.InstrumentMethod(ir::MethodId(
            desc.c_str(), "startMethodTracing", "(Ljava/lang/String;)V"))) {
      Log::E("Error instrumenting Debug.startMethodTracing(String)");
    }

    // Instrument stopMethodTracing() at exit.
    slicer::MethodInstrumenter mi_stop(dex_ir);
    mi_stop.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/cpu/TraceOperationTracker;",
        "onStopMethodTracing"));
    if (!mi_stop.InstrumentMethod(
            ir::MethodId(desc.c_str(), "stopMethodTracing", "()V"))) {
      Log::E("Error instrumenting Debug.stopMethodTracing");
    }
  } else if (strcmp(name, "android/os/PowerManager") == 0) {
    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/WakeLockWrapper;",
        "onNewWakeLockEntry"));
    mi.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/WakeLockWrapper;",
        "onNewWakeLockExit"));
    if (!mi.InstrumentMethod(ir::MethodId(
            desc.c_str(), "newWakeLock",
            "(ILjava/lang/String;)Landroid/os/PowerManager$WakeLock;"))) {
      Log::E("Error instrumenting PowerManager.newWakeLock");
    }
  } else if (strcmp(name, "android/os/PowerManager$WakeLock") == 0) {
    // Instrument acquire() and acquire(long).
    slicer::MethodInstrumenter mi_acq(dex_ir);
    mi_acq.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/WakeLockWrapper;",
        "wrapAcquire"));
    if (!mi_acq.InstrumentMethod(
            ir::MethodId(desc.c_str(), "acquire", "()V"))) {
      Log::E("Error instrumenting WakeLock.acquire");
    }
    if (!mi_acq.InstrumentMethod(
            ir::MethodId(desc.c_str(), "acquire", "(J)V"))) {
      Log::E("Error instrumenting WakeLock.acquire(long)");
    }

    // Instrument release(int).
    slicer::MethodInstrumenter mi_rel(dex_ir);
    mi_rel.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/WakeLockWrapper;",
        "onReleaseEntry"));
    mi_rel.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/WakeLockWrapper;",
        "onReleaseExit"));
    if (!mi_rel.InstrumentMethod(
            ir::MethodId(desc.c_str(), "release", "(I)V"))) {
      Log::E("Error instrumenting WakeLock.release");
    }
  } else if (strcmp(name, "android/app/AlarmManager") == 0) {
    // Instrument setImpl.
    slicer::MethodInstrumenter mi_set(dex_ir);
    mi_set.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/AlarmManagerWrapper;",
        "wrapSetImpl"));
    if (!mi_set.InstrumentMethod(ir::MethodId(
            desc.c_str(), "setImpl",
            "(IJJJILandroid/app/PendingIntent;"
            "Landroid/app/AlarmManager$OnAlarmListener;Ljava/lang/String;"
            "Landroid/os/Handler;Landroid/os/WorkSource;"
            "Landroid/app/AlarmManager$AlarmClockInfo;)V"))) {
      Log::E("Error instrumenting AlarmManager.setImpl");
    }

    // Instrument cancel(PendingIntent) and cancel(OnAlarmListener).
    slicer::MethodInstrumenter mi_cancel(dex_ir);
    mi_cancel.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/AlarmManagerWrapper;",
        "wrapCancel"));
    if (!mi_cancel.InstrumentMethod(ir::MethodId(
            desc.c_str(), "cancel", "(Landroid/app/PendingIntent;)V"))) {
      Log::E("Error instrumenting AlarmManager.cancel(PendingIntent)");
    }
    if (!mi_cancel.InstrumentMethod(
            ir::MethodId(desc.c_str(), "cancel",
                         "(Landroid/app/AlarmManager$OnAlarmListener;)V"))) {
      Log::E("Error instrumenting AlarmManager.cancel(OnAlarmListener)");
    }
  } else if (strcmp(name, "android/app/AlarmManager$ListenerWrapper") == 0) {
    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::DetourInterfaceInvoke>(
        ir::MethodId("Landroid/app/AlarmManager$OnAlarmListener;", "onAlarm",
                     "()V"),
        ir::MethodId(
            "Lcom/android/tools/profiler/support/energy/AlarmManagerWrapper;",
            "wrapListenerOnAlarm"));
    if (!mi.InstrumentMethod(ir::MethodId(desc.c_str(), "run", "()V"))) {
      Log::E("Error instrumenting ListenerWrapper.run");
    }
  } else if (strcmp(name, "android/app/JobSchedulerImpl") == 0) {
    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::EntryHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/energy/JobWrapper;",
                     "onScheduleJobEntry"),
        true);
    mi.AddTransformation<slicer::ExitHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/energy/JobWrapper;",
                     "onScheduleJobExit"));
    if (!mi.InstrumentMethod(ir::MethodId(desc.c_str(), "schedule",
                                          "(Landroid/app/job/JobInfo;)I"))) {
      Log::E("Error instrumenting JobScheduler.schedule");
    }
  } else if (strcmp(name, "android/app/job/JobService") == 0) {
    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::EntryHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/energy/JobWrapper;",
                     "wrapJobFinished"));
    if (!mi.InstrumentMethod(
            ir::MethodId(desc.c_str(), "jobFinished",
                         "(Landroid/app/job/JobParameters;Z)V"))) {
      Log::E("Error instrumenting JobService.jobFinished");
    }
  } else if (strcmp(name, "android/app/job/JobServiceEngine$JobHandler") == 0) {
    // ackStartMessage is non-abstract and calls onStartJob.
    slicer::MethodInstrumenter mi_start(dex_ir);
    mi_start.AddTransformation<slicer::EntryHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/energy/JobWrapper;",
                     "wrapOnStartJob"),
        true);
    if (!mi_start.InstrumentMethod(
            ir::MethodId(desc.c_str(), "ackStartMessage",
                         "(Landroid/app/job/JobParameters;Z)V"))) {
      Log::E("Error instrumenting JobHandler.ackStartMessage");
    }

    // ackStopMessage is non-abstract and calls onStopJob.
    slicer::MethodInstrumenter mi_stop(dex_ir);
    mi_stop.AddTransformation<slicer::EntryHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/energy/JobWrapper;",
                     "wrapOnStopJob"),
        true);
    if (!mi_stop.InstrumentMethod(
            ir::MethodId(desc.c_str(), "ackStopMessage",
                         "(Landroid/app/job/JobParameters;Z)V"))) {
      Log::E("Error instrumenting JobHandler.ackStopMessage");
    }
  } else if (strcmp(name, "android/location/LocationManager") == 0) {
    // Instrument all versions of requestLocationUpdates.
    slicer::MethodInstrumenter mi_req(dex_ir);
    mi_req.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/LocationManagerWrapper;",
        "wrapRequestLocationUpdates"));
    if (!mi_req.InstrumentMethod(
            ir::MethodId(desc.c_str(), "requestLocationUpdates",
                         "(Ljava/lang/String;JFLandroid/location/"
                         "LocationListener;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestLocationUpdates(String, "
          "long, float, LocationListener)");
    }
    if (!mi_req.InstrumentMethod(
            ir::MethodId(desc.c_str(), "requestLocationUpdates",
                         "(JFLandroid/location/Criteria;Landroid/location/"
                         "LocationListener;Landroid/os/Looper;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestLocationUpdates(long, "
          "float, Criteria, LocationListener, Looper)");
    }
    if (!mi_req.InstrumentMethod(
            ir::MethodId(desc.c_str(), "requestLocationUpdates",
                         "(Ljava/lang/String;JFLandroid/location/"
                         "LocationListener;Landroid/os/Looper;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestLocationUpdates(String, "
          "long, float, LocationListener, Looper)");
    }
    if (!mi_req.InstrumentMethod(ir::MethodId(
            desc.c_str(), "requestLocationUpdates",
            "(JFLandroid/location/Criteria;Landroid/app/PendingIntent;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestLocationUpdates(long, "
          "float, Criteria, PendingIntent)");
    }
    if (!mi_req.InstrumentMethod(ir::MethodId(
            desc.c_str(), "requestLocationUpdates",
            "(Ljava/lang/String;JFLandroid/app/PendingIntent;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestLocationUpdates(String, "
          "long, float, PendingIntent)");
    }

    // Instrument all versions of requestSingleUpdate.
    slicer::MethodInstrumenter mi_req_s(dex_ir);
    mi_req_s.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/LocationManagerWrapper;",
        "wrapRequestSingleUpdate"));
    if (!mi_req_s.InstrumentMethod(
            ir::MethodId(desc.c_str(), "requestSingleUpdate",
                         "(Ljava/lang/String;Landroid/app/PendingIntent;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestSingleUpdate(String, "
          "PendingIntent)");
    }
    if (!mi_req_s.InstrumentMethod(ir::MethodId(
            desc.c_str(), "requestSingleUpdate",
            "(Landroid/location/Criteria;Landroid/app/PendingIntent;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestSingleUpdate(Criteria, "
          "PendingIntent)");
    }
    if (!mi_req_s.InstrumentMethod(
            ir::MethodId(desc.c_str(), "requestSingleUpdate",
                         "(Ljava/lang/String;Landroid/location/"
                         "LocationListener;Landroid/os/Looper;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestSingleUpdate(String, "
          "LocationListener, Looper)");
    }
    if (!mi_req_s.InstrumentMethod(
            ir::MethodId(desc.c_str(), "requestSingleUpdate",
                         "(Landroid/location/Criteria;Landroid/location/"
                         "LocationListener;Landroid/os/Looper;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.requestSingleUpdate(Criteria, "
          "LocationListener, Looper)");
    }

    // Instrument all versions of removeUpdates
    slicer::MethodInstrumenter mi_remove(dex_ir);
    mi_remove.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/LocationManagerWrapper;",
        "wrapRemoveUpdates"));
    if (!mi_remove.InstrumentMethod(
            ir::MethodId(desc.c_str(), "removeUpdates",
                         "(Landroid/location/LocationListener;)V"))) {
      Log::E(
          "Error instrumenting "
          "LocationManager.removeUpdates(LocationListener)");
    }
    if (!mi_remove.InstrumentMethod(ir::MethodId(
            desc.c_str(), "removeUpdates", "(Landroid/app/PendingIntent;)V"))) {
      Log::E(
          "Error instrumenting LocationManager.removeUpdates(PendingIntent)");
    }
  } else if (strcmp(name,
                    "android/location/LocationManager$ListenerTransport") ==
             0) {
    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::DetourInterfaceInvoke>(
        ir::MethodId("Landroid/location/LocationListener;", "onLocationChanged",
                     "(Landroid/location/Location;)V"),
        ir::MethodId("Lcom/android/tools/profiler/support/energy/"
                     "LocationManagerWrapper;",
                     "wrapOnLocationChanged"));
    if (!mi.InstrumentMethod(ir::MethodId(desc.c_str(), "_handleMessage",
                                          "(Landroid/os/Message;)V"))) {
      Log::E("Error instrumenting LocationListener.onLocationChanged");
    }
  } else if (strcmp(name, "android/app/PendingIntent") == 0) {
    slicer::MethodInstrumenter mi_activity(dex_ir);
    mi_activity.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
        "onGetActivityEntry"));
    mi_activity.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
        "onGetActivityExit"));
    if (!mi_activity.InstrumentMethod(
            ir::MethodId(desc.c_str(), "getActivity",
                         "(Landroid/content/Context;ILandroid/content/Intent;I"
                         "Landroid/os/Bundle;)Landroid/app/PendingIntent;"))) {
      Log::E("Error instrumenting PendingIntent.getActivity");
    }

    slicer::MethodInstrumenter mi_service(dex_ir);
    mi_service.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
        "onGetServiceEntry"));
    mi_service.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
        "onGetServiceExit"));
    if (!mi_service.InstrumentMethod(
            ir::MethodId(desc.c_str(), "getService",
                         "(Landroid/content/Context;ILandroid/content/Intent;I)"
                         "Landroid/app/PendingIntent;"))) {
      Log::E("Error instrumenting PendingIntent.getService");
    }

    slicer::MethodInstrumenter mi_broadcast(dex_ir);
    mi_broadcast.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
        "onGetBroadcastEntry"));
    mi_broadcast.AddTransformation<slicer::ExitHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
        "onGetBroadcastExit"));
    if (!mi_broadcast.InstrumentMethod(
            ir::MethodId(desc.c_str(), "getBroadcast",
                         "(Landroid/content/Context;ILandroid/content/Intent;I)"
                         "Landroid/app/PendingIntent;"))) {
      Log::E("Error instrumenting PendingIntent.getBroadcast");
    }
  } else if (strcmp(name, "android/app/Instrumentation") == 0) {
    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
        "wrapActivityCreate"));
    if (!mi.InstrumentMethod(
            ir::MethodId(desc.c_str(), "callActivityOnCreate",
                         "(Landroid/app/Activity;Landroid/os/Bundle;)V"))) {
      Log::E(
          "Error instrumenting Instrumentation.callActivityOnCreate(Bundle)");
    }
    if (!mi.InstrumentMethod(
            ir::MethodId(desc.c_str(), "callActivityOnCreate",
                         "(Landroid/app/Activity;Landroid/os/Bundle;Landroid/"
                         "os/PersistableBundle;)V"))) {
      Log::E(
          "Error instrumenting Instrumentation.callActivityOnCreate(Bundle, "
          "PersistableBundle)");
    }
  } else if (strcmp(name, "android/app/IntentService") == 0) {
    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::EntryHook>(ir::MethodId(
        "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
        "wrapServiceStart"));
    if (!mi.InstrumentMethod(ir::MethodId(desc.c_str(), "onStartCommand",
                                          "(Landroid/content/Intent;II)I"))) {
      Log::E("Error instrumenting IntentService.onStartCommand");
    }
  } else if (strcmp(name, "android/app/ActivityThread") == 0) {
    slicer::MethodInstrumenter mi(dex_ir);
    mi.AddTransformation<slicer::DetourVirtualInvoke>(
        ir::MethodId("Landroid/content/BroadcastReceiver;", "onReceive",
                     "(Landroid/content/Context;Landroid/content/Intent;)V"),
        ir::MethodId(
            "Lcom/android/tools/profiler/support/energy/PendingIntentWrapper;",
            "wrapBroadcastReceive"));
    if (!mi.InstrumentMethod(
            ir::MethodId(desc.c_str(), "handleReceiver",
                         "(Landroid/app/ActivityThread$ReceiverData;)V"))) {
      Log::E("Error instrumenting BroadcastReceiver.onReceive");
    }
  } else if (strcmp(name,
                    "com/google/android/gms/location/"
                    "FusedLocationProviderClient") == 0) {
    slicer::MethodInstrumenter mi_req(dex_ir);
    mi_req.AddTransformation<slicer::EntryHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/energy/gms/"
                     "FusedLocationProviderClientWrapper;",
                     "wrapRequestLocationUpdates"),
        true);
    if (!mi_req.InstrumentMethod(ir::MethodId(
            desc.c_str(), "requestLocationUpdates",
            "(Lcom/google/android/gms/location/LocationRequest;"
            "Lcom/google/android/gms/location/LocationCallback;"
            "Landroid/os/Looper;)Lcom/google/android/gms/tasks/Task;"))) {
      Log::E(
          "Error instrumenting "
          "FusedLocationProviderClient.requestLocationUpdates("
          "LocationCallback)");
    }
    if (!mi_req.InstrumentMethod(ir::MethodId(
            desc.c_str(), "requestLocationUpdates",
            "(Lcom/google/android/gms/location/LocationRequest;Landroid/app/"
            "PendingIntent;)Lcom/google/android/gms/tasks/Task;"))) {
      Log::E(
          "Error instrumenting "
          "FusedLocationProviderClient.requestLocationUpdates(PendingIntent)");
    }

    slicer::MethodInstrumenter mi_rmv(dex_ir);
    mi_rmv.AddTransformation<slicer::EntryHook>(
        ir::MethodId("Lcom/android/tools/profiler/support/energy/gms/"
                     "FusedLocationProviderClientWrapper;",
                     "wrapRemoveLocationUpdates"),
        true);
    if (!mi_rmv.InstrumentMethod(
            ir::MethodId(desc.c_str(), "removeLocationUpdates",
                         "(Lcom/google/android/gms/location/LocationCallback;)"
                         "Lcom/google/android/gms/tasks/Task;"))) {
      Log::E(
          "Error instrumenting "
          "FusedLocationProviderClient.removeLocationUpdates("
          "LocationCallback)");
    }
    if (!mi_rmv.InstrumentMethod(
            ir::MethodId(desc.c_str(), "removeLocationUpdates",
                         "(Landroid/app/PendingIntent;)"
                         "Lcom/google/android/gms/tasks/Task;"))) {
      Log::E(
          "Error instrumenting "
          "FusedLocationProviderClient.removeLocationUpdates(PendingIntent)");
    }
  } else {
    Log::V("No transformation applied for class: %s", name);
    return;
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

void LoadDex(jvmtiEnv* jvmti, JNIEnv* jni, AgentConfig* agent_config) {
  // Load in perfa.jar which should be in to data/data.
  std::string agent_lib_path(GetAppDataPath());
  agent_lib_path.append("perfa.jar");
  jvmti->AddToBootstrapClassLoaderSearch(agent_lib_path.c_str());

  // TODO: Removed these once the auto-JNI-binding feature becomes
  // available in all published O system images.
  if (agent_config->energy_profiler_enabled()) {
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/energy/WakeLockWrapper",
        "sendWakeLockAcquired", "(IILjava/lang/String;JLjava/lang/String;)V");
    BindJNIMethod(jni,
                  "com/android/tools/profiler/support/energy/WakeLockWrapper",
                  "sendWakeLockReleased", "(IIZLjava/lang/String;)V");
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/energy/AlarmManagerWrapper",
        "sendIntentAlarmScheduled",
        "(IIJJJLjava/lang/String;ILjava/lang/String;)V");
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/energy/AlarmManagerWrapper",
        "sendListenerAlarmScheduled",
        "(IIJJJLjava/lang/String;Ljava/lang/String;)V");
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/energy/AlarmManagerWrapper",
        "sendIntentAlarmCancelled",
        "(ILjava/lang/String;ILjava/lang/String;)V");
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/energy/AlarmManagerWrapper",
        "sendListenerAlarmCancelled",
        "(ILjava/lang/String;Ljava/lang/String;)V");
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/energy/AlarmManagerWrapper",
        "sendIntentAlarmFired", "(ILjava/lang/String;IZ)V");
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/energy/AlarmManagerWrapper",
        "sendListenerAlarmFired", "(ILjava/lang/String;)V");
    BindJNIMethod(jni, "com/android/tools/profiler/support/energy/JobWrapper",
                  "sendJobScheduled",
                  "(IILjava/lang/String;IJZJJJJI[Ljava/lang/String;JJZZZZZ"
                  "Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)V");
    BindJNIMethod(jni, "com/android/tools/profiler/support/energy/JobWrapper",
                  "sendJobStarted",
                  "(II[Ljava/lang/String;[Ljava/lang/String;ZLjava/lang/String;"
                  "Ljava/lang/String;Z)V");
    BindJNIMethod(jni, "com/android/tools/profiler/support/energy/JobWrapper",
                  "sendJobStopped",
                  "(II[Ljava/lang/String;[Ljava/lang/String;ZLjava/lang/String;"
                  "Ljava/lang/String;Z)V");
    BindJNIMethod(jni, "com/android/tools/profiler/support/energy/JobWrapper",
                  "sendJobFinished",
                  "(II[Ljava/lang/String;[Ljava/lang/String;ZLjava/lang/String;"
                  "Ljava/lang/String;ZLjava/lang/String;)V");
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/energy/LocationManagerWrapper",
        "sendListenerLocationUpdateRequested",
        "(ILjava/lang/String;JJFIIILjava/lang/String;)V");
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/energy/LocationManagerWrapper",
        "sendIntentLocationUpdateRequested",
        "(ILjava/lang/String;JJFIIILjava/lang/String;ILjava/lang/String;)V");
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/energy/LocationManagerWrapper",
        "sendListenerLocationUpdateRemoved", "(ILjava/lang/String;)V");
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/energy/LocationManagerWrapper",
        "sendIntentLocationUpdateRemoved",
        "(ILjava/lang/String;ILjava/lang/String;)V");
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/energy/LocationManagerWrapper",
        "sendListenerLocationChanged", "(ILjava/lang/String;FDD)V");
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/energy/LocationManagerWrapper",
        "sendIntentLocationChanged",
        "(ILjava/lang/String;FDDLjava/lang/String;I)V");
  }

  if (agent_config->cpu_api_tracing_enabled()) {
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/cpu/TraceOperationTracker",
        "sendStartOperation", "(ILjava/lang/String;)V");
    BindJNIMethod(
        jni, "com/android/tools/profiler/support/cpu/TraceOperationTracker",
        "sendStopOperation", "(I)V");
  }

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
                "reportBytes", "(J[BI)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/network/"
                "HttpTracker$OutputStreamTracker",
                "onClose", "(J)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/network/"
                "HttpTracker$OutputStreamTracker",
                "onWriteBegin", "(J)V");
  BindJNIMethod(jni,
                "com/android/tools/profiler/support/network/"
                "HttpTracker$OutputStreamTracker",
                "reportBytes", "(J[BI)V");
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
  jmethodID initialize = jni->GetStaticMethodID(service, "initialize", "(ZZ)V");
  bool log_live_alloc_count = agent_config->mem_config().use_live_alloc();
  bool network_request_payload =
      agent_config->profiler_network_request_payload();
  jni->CallStaticVoidMethod(service, initialize, !log_live_alloc_count,
                            network_request_payload);
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
  profiler::Config config(options);
  agent_config = config.GetAgentConfig();
  Agent::Instance(&config);

  JNIEnv* jni_env = GetThreadLocalJNI(vm);
  LoadDex(jvmti_env, jni_env, &agent_config);

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
      jvmti_env->Deallocate((unsigned char*)sig_mutf8);
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

  jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE,
                                      nullptr);

  for (int i = 0; i < class_count; ++i) {
    jni_env->DeleteLocalRef(loaded_classes[i]);
  }
  jvmti_env->Deallocate(reinterpret_cast<unsigned char*>(loaded_classes));

  Agent::Instance().AddPerfdConnectedCallback([vm] {
    // MemoryTackingEnv needs a connection to perfd, which may not be always the
    // case. If we don't postpone until there is a connection, MemoryTackingEnv
    // is going to busy-wait, so not allowing the application to finish
    // initialization. This callback will be called each time perfd connects.
    MemoryTrackingEnv::Instance(vm, agent_config.mem_config());
    // Perf-test currently waits on this message to determine that perfa is
    // connected to perfd.
    Log::V("Perfa connected to Perfd.");
  });

  return JNI_OK;
}

}  // namespace profiler
