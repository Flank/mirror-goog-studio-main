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

#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <cstring>
#include <fstream>
#include <iostream>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

#include "android_wrapper.h"
#include "capabilities.h"
#include "config.h"
#include "hotswap.h"
#include "instrumenter.h"
#include "native_callbacks.h"

#include "jni/jni_class.h"
#include "jni/jni_object.h"
#include "jni/jni_util.h"
#include "socket.h"
#include "utils/log.h"

using std::string;
using std::unique_ptr;
using std::vector;

namespace swapper {

const char* kBreadcrumbClass = "com/android/tools/deploy/instrument/Breadcrumb";
const char* kHandlerWrapperClass =
    "com/android/tools/deploy/instrument/ActivityThreadHandlerWrapper";
#include "instrumentation.jar.cc"

const std::string kInstrumentation_jar_name =
    std::string("instruments-") + instrumentation_jar_hash + ".jar";

#define FILE_MODE (S_IRUSR | S_IWUSR)

// Watch as I increment between runs!
static int run_counter = 0;

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
                            const std::string& jar_path) {
  // Check for the existence of a breadcrumb class, indicating a previous agent
  // has already loaded instrumentation. If no previous agent has run on this
  // jvm, add our instrumentation classes to the bootstrap class loader.
  jclass unused = jni->FindClass(kBreadcrumbClass);
  if (unused == nullptr) {
    Log::V("No existing instrumentation found. Loading instrumentation from %s",
           kInstrumentation_jar_name.c_str());
    jni->ExceptionClear();
    if (jvmti->AddToBootstrapClassLoaderSearch(jar_path.c_str()) !=
        JVMTI_ERROR_NONE) {
      return false;
    }
  } else {
    jni->DeleteLocalRef(unused);
  }
  return true;
}

bool Instrument(jvmtiEnv* jvmti, JNIEnv* jni, const std::string& jar) {
  // The breadcrumb class stores some checks between runs of the agent.
  // We can't use the class from the FindClass call because it may not have
  // actually found the class.
  JniClass breadcrumb(jni, kBreadcrumbClass);

  // Ensure that the jar hasn't changed since we last instrumented. If it has,
  // fail out for now. This is an important scenario to guard against, since it
  // would likely cause silent failures.
  jvalue jar_hash = {.l = jni->NewStringUTF(instrumentation_jar_hash)};
  jboolean matches = breadcrumb.CallStaticMethod<jboolean>(
      {"checkHash", "(Ljava/lang/String;)Z"}, &jar_hash);
  jni->DeleteLocalRef(jar_hash.l);

  if (!matches) {
    Log::E(
        "The instrumentation jar at %s does not match the jar previously used "
        "to instrument. The application must be restarted.",
        kInstrumentation_jar_name.c_str());
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
    Log::V("Finished instrumenting");
  }

  return true;
}

jint DoHotSwap(jvmtiEnv* jvmti, JNIEnv* jni, const Config& config) {
  HotSwap code_swap(jvmti, jni);
  if (!code_swap.DoHotSwap(config.GetSwapRequest())) {
    // TODO: Log meaningful error.
    Log::E("Hot swap failed.");
    return JNI_ERR;
  }
  return JNI_OK;
}

// Check if the jar_path exists. If it doesn't, generate its content using the
// jar embedded in the .data section of this executable.
// TODO: Don't write to disk. Have jvmti load the jar directly from a memory
// mapped fd to agent.so.
bool WriteJarToDiskIfNecessary(const std::string& jar_path) {
  // If file exists, there is no need to do anything.
  if (access(jar_path.c_str(), F_OK) != -1) {
    return true;
  }

  // TODO: Would be more efficient to have the offet and size and use sendfile()
  // to avoid a userland trip.
  int fd = open(jar_path.c_str(), O_WRONLY | O_CREAT, FILE_MODE);
  if (fd == -1) {
    Log::E("WriteJarToDiskIfNecessary(). Unable to open().");
    return false;
  }
  int written = write(fd, instrumentation_jar, instrumentation_jar_len);
  if (written == -1) {
    Log::E("WriteJarToDiskIfNecessary(). Unable to write().");
    return false;
  }

  int closeResult = close(fd);
  if (closeResult == -1) {
    Log::E("WriteJarToDiskIfNecessary(). Unable to close().");
    return false;
  }

  return true;
}

std::string GetInstrumentJarPath(const std::string& package_name) {
#ifdef __ANDROID__
  std::string target_jar_dir_ =
      std::string("/data/data/") + package_name + "/.studio/";
  std::string target_jar = target_jar_dir_ + kInstrumentation_jar_name;
  return target_jar;
#else
  // For tests purposes.
  char* tmp_dir = getenv("TEST_TMPDIR");
  Log::E("GetInstrumentPath:%s", tmp_dir);
  if (tmp_dir == nullptr) {
    return kInstrumentation_jar_name;
  } else {
    return std::string(tmp_dir) + "/" + kInstrumentation_jar_name;
  }
#endif
}

jint DoHotSwapAndRestart(jvmtiEnv* jvmti, JNIEnv* jni, const Config& config) {
  jvmtiEventCallbacks callbacks;
  callbacks.ClassFileLoadHook = Agent_ClassFileLoadHook;

  if (jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks)) !=
      JVMTI_ERROR_NONE) {
    Log::E("Error setting event callbacks.");
    return JNI_ERR;
  }

  std::string instrument_jar_path =
      GetInstrumentJarPath(config.GetSwapRequest().package_name());

  // Make sure the instrumentation jar is ready on disk.
  if (!WriteJarToDiskIfNecessary(instrument_jar_path)) {
    Log::E("Error writing instrumentation.jar to disk.");
    return JNI_ERR;
  }

  if (!LoadInstrumentationJar(jvmti, jni, instrument_jar_path)) {
    Log::E("Error loading instrumentation dex.");
    return JNI_ERR;
  }

  if (!Instrument(jvmti, jni, instrument_jar_path)) {
    Log::E("Error instrumenting application.");
    return JNI_ERR;
  }

  // Enable hot-swapping via the callback.
  JniClass handlerWrapper(jni, kHandlerWrapperClass);
  handlerWrapper.CallStaticMethod<void>({"prepareForHotSwap", "()V"});

  // Perform hot swap through the activity restart callback path.
  AndroidWrapper wrapper(jni);
  wrapper.RestartActivity(config.GetSwapRequest().package_name().c_str());

  return JNI_OK;
}

// Event that fires when the agent hooks onto a running VM.
extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* input,
                                                 void* reserved) {
  jvmtiEnv* jvmti;
  JNIEnv* jni;

  Log::V("Prior agent invocations in this VM: %d", run_counter++);

  std::string config_bytes(input);
  bool could_parse = false;

  if (config_bytes.empty()) {
    deploy::Socket socket;
    if (!socket.Open()) {
      Log::E("Could not open new socket");
    } else if (!socket.Connect(deploy::Socket::kDefaultAddress, 1000)) {
      Log::E("Could not connect to socket");
    } else if (!socket.Read(&config_bytes)) {
      Log::E("Could not read from socket");
    } else {
      // If there are no passed-in config bytes, read from socket.
      could_parse = Config::ParseFromString(config_bytes);

      //  Debug code. Remove when agent sends a real response.
      if (!socket.Write(config_bytes)) {
        Log::E("Could not send to socket");
      }
    }
  } else {
    // If there are passed-in config bytes, use them.
    could_parse = Config::ParseFromFile(config_bytes);
  }

  if (!could_parse) {
    Log::E("Could not parse swap request");
    return JNI_OK;
  }

  if (!GetJvmti(vm, jvmti)) {
    Log::E("Error retrieving JVMTI function table.");
    return JNI_OK;
  }

  if (!GetJni(vm, jni)) {
    Log::E("Error retrieving JNI function table.");
    return JNI_OK;
  }

  if (jvmti->AddCapabilities(&REQUIRED_CAPABILITIES) != JVMTI_ERROR_NONE) {
    Log::E("Error setting capabilities.");
    return JNI_OK;
  }

  const Config& config = Config::GetInstance();

  if (config.GetSwapRequest().restart_activity()) {
    DoHotSwapAndRestart(jvmti, jni, config);
  } else {
    DoHotSwap(jvmti, jni, config);
  }

  // If the agent could succesfully attach, we return JNI_OK even if the hot
  // swap fails.
  return JNI_OK;
}  // namespace swapper

}  // namespace swapper
