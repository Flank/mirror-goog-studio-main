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

#include "tools/base/deploy/agent/native/instrumenter.h"

#include <fcntl.h>
#include <jni.h>
#include <jvmti.h>
#include <sys/stat.h>
#include <unistd.h>

#include <string>

#include "tools/base/deploy/agent/native/instrumentation.jar.cc"
#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/agent/native/native_callbacks.h"
#include "tools/base/deploy/common/env.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/utils.h"

namespace deploy {

const char* kBreadcrumbClass = "com/android/tools/deploy/instrument/Breadcrumb";
const char* kHandlerWrapperClass =
    "com/android/tools/deploy/instrument/InstrumentationHooks";

const char* kDexUtilityClass = "com/android/tools/deploy/instrument/DexUtility";

const std::string kInstrumentationJarName =
    "instruments-"_s + instrumentation_jar_hash + ".jar";

const std::string MethodHooks::kNoHook = "";

const Transform* current_transform = nullptr;

#define FILE_MODE (S_IRUSR | S_IWUSR)

std::string GetInstrumentJarPath(const std::string& package_name) {
  std::string target_jar_dir_ = Env::root() + std::string("/data/data/") +
                                package_name + "/code_cache/.studio/";
  std::string target_jar = target_jar_dir_ + kInstrumentationJarName;
  return target_jar;
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

  // TODO: Would be more efficient to have the offset and size and use
  // sendfile() to avoid a userland trip.
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

bool LoadInstrumentationJar(jvmtiEnv* jvmti, JNIEnv* jni,
                            const std::string& jar_path) {
  // Check for the existence of a breadcrumb class, indicating a previous agent
  // has already loaded instrumentation. If no previous agent has run on this
  // jvm, add our instrumentation classes to the bootstrap class loader.
  jclass unused = jni->FindClass(kBreadcrumbClass);
  if (unused == nullptr) {
    Log::V("No existing instrumentation found. Loading instrumentation from %s",
           kInstrumentationJarName.c_str());
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

bool ApplyTransform(jvmtiEnv* jvmti, JNIEnv* jni, const Transform& transform) {
  jclass klass = jni->FindClass(transform.GetClassName().c_str());
  if (jni->ExceptionCheck()) {
    ErrEvent("Could not find class for instrumentation: " +
             transform.GetClassName());
    jni->ExceptionClear();
    return false;
  }
  current_transform = &transform;
  bool success =
      CheckJvmti(jvmti->RetransformClasses(1, &klass),
                 "Could not retransform class: " + transform.GetClassName());
  jni->DeleteLocalRef(klass);
  return success;
}

bool Instrument(jvmtiEnv* jvmti, JNIEnv* jni, const std::string& jar,
                bool overlay_swap) {
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
        kInstrumentationJarName.c_str());
    return false;
  }

  // Check if we need to instrument, or if a previous agent successfully did.
  if (breadcrumb.CallStaticMethod<jboolean>(
          {"isFinishedInstrumenting", "()Z"})) {
    return true;
  }

  const HookTransform activity_thread(
      /* target class */ "android/app/ActivityThread",
      /* target method */ "handleDispatchPackageBroadcast",
      /* target signature */ "(I[Ljava/lang/String;)V",
      "handleDispatchPackageBroadcastEntry",
      "handleDispatchPackageBroadcastExit");

  const HookTransform dex_path_list_element(
      /* target class */ "dalvik/system/DexPathList$Element",
      /* target method */ "findResource",
      /* target signature */ "(Ljava/lang/String;)Ljava/net/URL;",
      "handleFindResourceEntry", MethodHooks::kNoHook);

  const MethodHooks split_paths(
      /* target method */ "splitPaths",
      /* target signature */ "(Ljava/lang/String;Z)Ljava/util/List;",
      MethodHooks::kNoHook, "handleSplitPathsExit");

  const MethodHooks split_dex_paths(
      /* target method */ "splitDexPath",
      /* target signature */ "(Ljava/lang/String;)Ljava/util/List;",
      MethodHooks::kNoHook, "handleSplitDexPathExit");

  const HookTransform dex_path_list(
      /* target class */ "dalvik/system/DexPathList",
      /* transforms */ {split_paths, split_dex_paths});

  const HookTransform res_manager(
      /* target class */ "android/app/ResourcesManager",
      /* target method */ "applyNewResourceDirsLocked",
      /* target signature */
      "(Landroid/content/pm/ApplicationInfo;[Ljava/lang/String;)V",
      "addResourceOverlays", MethodHooks::kNoHook);

  const HookTransform asset_manager_builder(
      /* target class */ "android/content/res/AssetManager$Builder",
      /* target method */ "build",
      /* target signature */
      "()Landroid/content/res/AssetManager;", "addResourceOverlays",
      MethodHooks::kNoHook);

  // This transform changes the integer cookie passed in to each of these
  // methods to zero. The cookie is used to determine where to look for a
  // resource; a cookie of zero means "look everywhere". Setting this value to
  // zero prevents the app from trying to load content from our resource loader
  // that is not present.
  const ParameterSetTransform asset_manager(
      "android/content/res/AssetManager",
      {{/* target method */ "openXmlBlockAsset",
        /* target signature */ "(ILjava/lang/String;)Landroid/content/res/"
                               "XmlBlock;"},
       {/* target method */ "openNonAsset",
        /* target signature */ "(ILjava/lang/String;I)Ljava/io/InputStream;"},
       {/* target method */ "openNonAssetFd",
        /* target signature */ "(ILjava/lang/String;)Landroid/content/res/"
                               "AssetFileDescriptor;"}});

  bool success = true;
  success &=
      CheckJvmti(jvmti->SetEventNotificationMode(
                     JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL),
                 "Could not enable class file load hook event");

  success &= ApplyTransform(jvmti, jni, activity_thread);
  success &= ApplyTransform(jvmti, jni, dex_path_list_element);

  if (overlay_swap) {
    success &= ApplyTransform(jvmti, jni, dex_path_list);
    success &= ApplyTransform(jvmti, jni, res_manager);
    success &= ApplyTransform(jvmti, jni, asset_manager_builder);
    success &= ApplyTransform(jvmti, jni, asset_manager);
  }

  // Failing to disable this event does not actually have any bearing on
  // whether or not instrumentation was a success, so we do not modify the
  // success flag.
  CheckJvmti(jvmti->SetEventNotificationMode(
                 JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL),
             "Could not disable class file load hook event");

  if (success) {
    breadcrumb.CallStaticMethod<void>({"setFinishedInstrumenting", "()V"});
    LogEvent("Finished instrumenting");
  }

  return success;
}

// Event that fires when the agent loads a class file.
extern "C" void JNICALL Agent_ClassFileLoadHook(
    jvmtiEnv* jvmti, JNIEnv* jni, jclass class_being_redefined, jobject loader,
    const char* name, jobject protection_domain, jint class_data_len,
    const unsigned char* class_data, jint* new_class_data_len,
    unsigned char** new_class_data) {
  if (current_transform == nullptr ||
      current_transform->GetClassName() != name) {
    return;
  }

  // The class name needs to be in JNI-format.
  string descriptor = "L" + current_transform->GetClassName() + ";";

  dex::Reader reader(class_data, class_data_len);
  auto class_index = reader.FindClassIndex(descriptor.c_str());
  if (class_index == dex::kNoIndex) {
    // TODO: Handle failure.
    return;
  }

  reader.CreateClassIr(class_index);
  auto dex_ir = reader.GetIr();
  current_transform->Apply(dex_ir);

  size_t new_image_size = 0;
  dex::u1* new_image = nullptr;
  dex::Writer writer(dex_ir);

  JvmtiAllocator allocator(jvmti);
  new_image = writer.CreateImage(&allocator, &new_image_size);

  *new_class_data_len = new_image_size;
  *new_class_data = new_image;
}

bool InstrumentApplication(jvmtiEnv* jvmti, JNIEnv* jni,
                           const std::string& package_name, bool overlay_swap) {
  jvmtiEventCallbacks callbacks;
  callbacks.ClassFileLoadHook = Agent_ClassFileLoadHook;

  if (jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks)) !=
      JVMTI_ERROR_NONE) {
    Log::E("Error setting event callbacks.");
    return false;
  }

  std::string instrument_jar_path = GetInstrumentJarPath(package_name);

  // Make sure the instrumentation jar is ready on disk.
  if (!WriteJarToDiskIfNecessary(instrument_jar_path)) {
    Log::E("Error writing instrumentation.jar to disk.");
    return false;
  }

  if (!LoadInstrumentationJar(jvmti, jni, instrument_jar_path)) {
    Log::E("Error loading instrumentation dex.");
    return false;
  }

  if (!Instrument(jvmti, jni, instrument_jar_path, overlay_swap)) {
    Log::E("Error instrumenting application.");
    return false;
  }

  // Need to register native methods every time; otherwise, the Java methods
  // could potentially call old versions if a previous agent.so was loaded.
  RegisterNative(jni, {kHandlerWrapperClass, "fixAppContext",
                       "(Ljava/lang/Object;)Ljava/lang/Object;",
                       (void*)&Native_FixAppContext});

  RegisterNative(jni, {kHandlerWrapperClass, "getActivityClientRecords",
                       "(Ljava/lang/Object;)Ljava/util/Collection;",
                       (void*)&Native_GetActivityClientRecords});

  RegisterNative(jni, {kHandlerWrapperClass, "fixActivityContext",
                       "(Ljava/lang/Object;Ljava/lang/Object;)V",
                       (void*)&Native_FixActivityContext});

  RegisterNative(
      jni, {kHandlerWrapperClass, "updateApplicationInfo",
            "(Ljava/lang/Object;)V", (void*)&Native_UpdateApplicationInfo});

  // Register utility methods.
  RegisterNative(jni,
                 {kDexUtilityClass, "makeInMemoryDexElements",
                  "([Ljava/nio/ByteBuffer;Ljava/util/List;)[Ljava/lang/Object;",
                  (void*)&Native_MakeInMemoryDexElements});

  return true;
}

}  // namespace deploy
