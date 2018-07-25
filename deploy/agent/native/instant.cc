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

#include "jni.h"
#include "jvmti.h"

#include <fstream>
#include <memory>
#include <vector>

#include "android_wrapper.h"
#include "capabilities.h"
#include "hotswap.h"
#include "instrumenter.h"
#include "native_callbacks.h"
#include "proto/config.pb.h"

#include "jni/jni_class.h"
#include "jni/jni_object.h"
#include "jni/jni_util.h"

#include "utils/log.h"

using std::string;
using std::unique_ptr;
using std::vector;

using swapper::proto::Config;

namespace swapper {

// We keep a global (for now) reference to the PB that contains of the all
// the info we need to swapping.
extern const Config* config;
const Config* config;

const char* kBreadcrumbClass = "com/android/tools/deploy/instrument/Breadcrumb";
const char* kHandlerWrapperClass =
    "com/android/tools/deploy/instrument/ActivityThreadHandlerWrapper";

// Event that fires when the agent loads a class file.
extern "C" void JNICALL Agent_ClassFileLoadHook(
    jvmtiEnv* jvmti, JNIEnv* jni, jclass class_being_redefined, jobject loader,
    const char* name, jobject protection_domain, jint class_data_len,
    const unsigned char* class_data, jint* new_class_data_len,
    unsigned char** new_class_data) {
  TransformClass(jvmti, name, class_data_len, class_data, new_class_data_len,
                 new_class_data);
}

bool LoadInstrumentationJar(jvmtiEnv* jvmti, JNIEnv* jni,
                            const string& instrumentation_jar) {
  // Check for the existence of a breadcrumb class, indicating a previous agent
  // has already loaded instrumentation. If no previous agent has run on this
  // jvm, add our instrumentation classes to the bootstrap class loader.
  jclass unused = jni->FindClass(kBreadcrumbClass);
  if (unused == nullptr) {
    Log::V("No existing instrumentation found. Loading instrmentation from %s",
           instrumentation_jar.c_str());
    jni->ExceptionClear();
    if (jvmti->AddToBootstrapClassLoaderSearch(instrumentation_jar.c_str()) !=
        JVMTI_ERROR_NONE) {
      return false;
    }
  } else {
    jni->DeleteLocalRef(unused);
  }
  return true;
}

bool Instrument(jvmtiEnv* jvmti, JNIEnv* jni,
                const string& instrumentation_jar) {
  // The breadcrumb class stores some checks between runs of the agent.
  // We can't use the class from the FindClass call because it may not have
  // actually found the class.
  JniClass breadcrumb(jni, kBreadcrumbClass);

  // Ensure that the jar hasn't changed since we last instrumented. If it has,
  // fail out for now. This is an important scenario to guard against, since it
  // would likely cause silent failures.
  jvalue jar_path = {.l = jni->NewStringUTF(instrumentation_jar.c_str())};
  jboolean matches = breadcrumb.CallStaticMethod<jboolean>(
      {"checkHash", "(Ljava/lang/String;)Z"}, &jar_path);
  jni->DeleteLocalRef(jar_path.l);

  if (!matches) {
    Log::E(
        "The instrumentation jar at %s does not match the jar previously used "
        "to instrument. The application must be restarted.",
        instrumentation_jar.c_str());
    return false;
  }

  // Check if we need to instrument, or if a previous agent successfully did.
  if (!breadcrumb.CallStaticMethod<jboolean>(
          {"isFinishedInstrumenting", "()Z"})) {
    vector<NativeBinding> native_bindings;

    native_bindings.emplace_back(kHandlerWrapperClass,
                                 "getApplicationInfoChangedValue", "()I",
                                 (void*)&Native_GetAppInfoChanged);

    native_bindings.emplace_back(kHandlerWrapperClass, "tryRedefineClasses",
                                 "()Z", (void*)&Native_TryRedefineClasses);

    RegisterNatives(jni, native_bindings);

    // Instrument the activity thread handler using RetransformClasses.
    // TODO: If we instrument more, make this more general.

    AddTransform("android/app/ActivityThread$H",
                 new ActivityThreadHandlerTransform());

    jclass activity_thread_h = jni->FindClass("android/app/ActivityThread$H");
    if (jni->ExceptionCheck()) {
      Log::E("Could not find activity thread handler");
      jni->ExceptionClear();
      return false;
    }

    jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                                    JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL);
    jvmti->RetransformClasses(1, &activity_thread_h);
    jvmti->SetEventNotificationMode(JVMTI_DISABLE,
                                    JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL);

    jni->DeleteLocalRef(activity_thread_h);

    DeleteTransforms();

    // Mark that we've finished instrumentation.
    breadcrumb.CallStaticMethod<void>({"setFinishedInstrumenting", "()V"});
  }

  return true;
}

jint DoHotSwap(jvmtiEnv* jvmti, JNIEnv* jni) {
  HotSwap code_swap(jvmti, jni);
  if (!code_swap.DoHotSwap(config)) {
    // TODO: Log meaningful error.
    Log::E("Hot swap failed.");
    return JNI_ERR;
  }
  return JNI_OK;
}

jint DoHotSwapAndRestart(jvmtiEnv* jvmti, JNIEnv* jni) {
  jvmtiEventCallbacks callbacks;
  callbacks.ClassFileLoadHook = Agent_ClassFileLoadHook;

  if (jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks)) !=
      JVMTI_ERROR_NONE) {
    Log::E("Error setting event callbacks.");
    return JNI_ERR;
  }

  if (!LoadInstrumentationJar(jvmti, jni,
                              config->instrumentation_jar().c_str())) {
    Log::E("Error loading instrumentation jar.");
    return JNI_ERR;
  }

  if (!Instrument(jvmti, jni, config->instrumentation_jar().c_str())) {
    Log::E("Error instrumenting application.");
    return JNI_ERR;
  }

  // Enable hot-swapping via the callback.
  JniClass handlerWrapper(jni, kHandlerWrapperClass);
  handlerWrapper.CallStaticMethod<void>({"prepareForHotSwap", "()V"});

  // Perform hot swap through the activity restart callback path.
  AndroidWrapper wrapper(jni);
  wrapper.RestartActivity(config->package_name().c_str());

  return JNI_OK;
}

void ParseConfig(char* file_location) {
  Config* cfg = new Config();
  std::fstream stream(file_location, std::ios::in | std::ios::binary);
  if (!cfg->ParseFromIstream(&stream)) {
    Log::E("Could not parse config in %s", file_location);
  }
  config = cfg;
}

// Event that fires when the agent hooks onto a running VM.
extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* input,
                                                 void* reserved) {
  jvmtiEnv* jvmti;
  JNIEnv* jni;

  ParseConfig(input);
  if (!config) {
    Log::E("Could not parse config");
    return JNI_ERR;
  }

  if (!GetJvmti(vm, jvmti)) {
    Log::E("Error retrieving JVMTI function table.");
    return JNI_ERR;
  }

  if (!GetJni(vm, jni)) {
    Log::E("Error retrieving JNI function table.");
    return JNI_ERR;
  }

  if (jvmti->AddCapabilities(&REQUIRED_CAPABILITIES) != JVMTI_ERROR_NONE) {
    Log::E("Error setting capabilities.");
    return JNI_ERR;
  }

  jint ret = JNI_ERR;
  if (config->restart_activity()) {
    ret = DoHotSwapAndRestart(jvmti, jni);
  } else {
    ret = DoHotSwap(jvmti, jni);
    delete config;
    jvmti->RelinquishCapabilities(&REQUIRED_CAPABILITIES);
    Log::V("Finished.");
  }
  return ret;
}

}  // namespace swapper
