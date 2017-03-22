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

#include "jvmti_helper.h"
#include "perfa/perfa.h"
#include "scoped_local_ref.h"
#include "utils/log.h"

using profiler::Log;
using profiler::Perfa;
using profiler::ScopedLocalRef;

namespace profiler {

static jvmtiEnv *jvmti_;

/**
 * Returns the jclass object based on a name string by looking through the
 * class loaders in the following order:
 * 1. Bootstrap class loader (which is the loader associated with the calling
 *    native method to the JNI's FindClass API).
 * 2. System's class loader obtained via ClassLoader.getSystemClassLoader()
 * 3. Per-thread's context class loader obtained via JVMTI API. *
 *
 * Returns null if a class cannot be found.
 */
jclass FindClass(jvmtiEnv *jvmti, JNIEnv *jni, const char *class_name) {
  // Try boot path class loader.
  jclass klass = jni->FindClass(class_name);
  if (klass != nullptr) {
    return klass;
  }
  jni->ExceptionClear();

  ScopedLocalRef<jclass> classloader_klass(
      jni, jni->FindClass("java/lang/ClassLoader"));
  jmethodID findclass_method =
      jni->GetMethodID(classloader_klass.get(), "findClass",
                       "(Ljava/lang/String;)Ljava/lang/Class;");
  std::string dot_name(class_name);
  std::replace(dot_name.begin(), dot_name.end(), '/', '.');
  ScopedLocalRef<jstring> dot_name_jstr(jni,
                                        jni->NewStringUTF(dot_name.c_str()));

  // Try system class loader.
  jmethodID getsystemclassloader_method =
      jni->GetStaticMethodID(classloader_klass.get(), "getSystemClassLoader",
                             "()Ljava/lang/ClassLoader;");
  ScopedLocalRef<jobject> system_class_loader(
      jni, jni->CallStaticObjectMethod(classloader_klass.get(),
                                       getsystemclassloader_method));
  klass = static_cast<jclass>(jni->CallObjectMethod(
      system_class_loader.get(), findclass_method, dot_name_jstr.get()));

  if (klass != nullptr) {
    return klass;
  }
  jni->ExceptionClear();

  // Go through all threads class loader
  jint thread_counter = 0;
  jthread *threads;
  jvmtiError error = jvmti->GetAllThreads(&thread_counter, &threads);
  CheckJvmtiError(jvmti, error, false);
  for (int i = 0; i < thread_counter; i++) {
    // Everything inside jvmtiThreadInfo are jvmti-allocated or jni local refs,
    // and require manual management.
    jvmtiThreadInfo thread_info;
    error = jvmti->GetThreadInfo(threads[i], &thread_info);
    CheckJvmtiError(jvmti, error, false);
    Deallocate(jvmti, thread_info.name);

    if (thread_info.context_class_loader != nullptr) {
      if (klass == nullptr) {
        klass = static_cast<jclass>(
            jni->CallObjectMethod(thread_info.context_class_loader,
                                  findclass_method, dot_name_jstr.get()));
        if (klass == nullptr) {
          jni->ExceptionClear();
        }
      }
      jni->DeleteLocalRef(thread_info.context_class_loader);
    }

    if (thread_info.thread_group != nullptr) {
      jni->DeleteLocalRef(thread_info.thread_group);
    }

    jni->DeleteLocalRef(threads[i]);
  }
  Deallocate(jvmti, threads);

  return klass;
}

/**
 * Given a class identified by class_name, find all its native methods and
 * bind them to the corresponding mangled method names
 */
void BindMethods(jvmtiEnv *jvmti, JNIEnv *jni, const char *class_name) {
  ScopedLocalRef<jclass> klass(jni, FindClass(jvmti, jni, class_name));
  if (klass.get() == nullptr) {
    Log::V("Failed to find jclass for %s", class_name);
    return;
  }

  char *klass_signature;
  jvmtiError error =
      jvmti->GetClassSignature(klass.get(), &klass_signature, nullptr);
  if (CheckJvmtiError(jvmti, error, false) || klass_signature == nullptr) {
    return;
  }

  jint count = 0;
  jmethodID *methods;
  error = jvmti->GetClassMethods(klass.get(), &count, &methods);
  CheckJvmtiError(jvmti, error, false);
  for (int i = 0; i < count; i++) {
    jboolean is_native = false;
    error = jvmti->IsMethodNative(methods[i], &is_native);
    CheckJvmtiError(jvmti, error, false);
    if (is_native) {
      char *name;
      char *signature;
      error = jvmti->GetMethodName(methods[i], &name, &signature, nullptr);
      CheckJvmtiError(jvmti, error, false);

      if (name != nullptr && signature != nullptr) {
        std::string mangled_name(GetMangledName(klass_signature, name));
        void *sym = dlsym(RTLD_DEFAULT, mangled_name.c_str());
        if (sym != nullptr) {
          JNINativeMethod native_method;
          native_method.fnPtr = sym;
          native_method.name = name;
          native_method.signature = signature;
          jni->RegisterNatives(klass.get(), &native_method, 1);
        } else {
          Log::V("Failed to find symbol for %s", mangled_name.c_str());
        }
      }
      Deallocate(jvmti, name);
      Deallocate(jvmti, signature);
    }
  }
  Deallocate(jvmti, methods);
  Deallocate(jvmti, klass_signature);
}

void InitPerfa(jvmtiEnv *jvmti, JNIEnv *jni) {
  Perfa::Instance();

  // Load in agenlib.jar which should be in to data/data.
  Dl_info dl_info;
  dladdr((void *)Agent_OnAttach, &dl_info);
  std::string so_path(dl_info.dli_fname);
  std::string agent_lib_path(so_path.substr(0, so_path.find_last_of('/')));
  agent_lib_path.append("/agentlib.jar");
  jvmti->AddToBootstrapClassLoaderSearch(agent_lib_path.c_str());

  // Bind the native methods for all tracker classes.
  BindMethods(
      jvmti, jni,
      "com/android/tools/profiler/support/event/WindowProfilerCallback");
  BindMethods(
      jvmti, jni,
      "com/android/tools/profiler/support/event/InputConnectionWrapper");
  BindMethods(
      jvmti, jni,
      "com/android/tools/profiler/support/event/WindowProfilerCallback");
  BindMethods(jvmti, jni,
              "com/android/tools/profiler/support/memory/VmStatsSampler");
  BindMethods(jvmti, jni,
              "com/android/tools/profiler/support/network/"
              "HttpTracker$InputStreamTracker");
  BindMethods(jvmti, jni,
              "com/android/tools/profiler/support/network/"
              "HttpTracker$OutputStreamTracker");
  BindMethods(
      jvmti, jni,
      "com/android/tools/profiler/support/network/HttpTracker$Connection");
  BindMethods(jvmti, jni,
              "com/android/tools/profiler/support/profilers/EventProfiler");

  // Enable the PERFA_ENABLED flag and call initialize
  ScopedLocalRef<jclass> profiler_service_klass(
      jni, FindClass(jvmti, jni,
                     "com/android/tools/profiler/support/ProfilerService"));
  jfieldID enable_field =
      jni->GetStaticFieldID(profiler_service_klass.get(), "PERFA_ENABLED", "Z");
  if (enable_field == nullptr) {
    Log::V("ProfilerService.PERFA_ENABLED field not found.");
  } else {
    jni->SetStaticBooleanField(profiler_service_klass.get(), enable_field,
                               true);
  }

  jmethodID initialize_method =
      jni->GetStaticMethodID(profiler_service_klass.get(), "initialize", "()V");
  if (initialize_method == nullptr) {
    Log::V("ProfilerService.initialize() method not found.");
  } else {
    jni->CallStaticVoidMethod(profiler_service_klass.get(), initialize_method);
  }
}

jint InitAgent(JavaVM *vm) {
  jint result = vm->GetEnv((void **)&jvmti_, JVMTI_VERSION_1_2);
  if (result != JNI_OK) {
    Log::E("Error getting jvmtiEnv pointer.");
    return result;
  }

  SetAllCapabilities(jvmti_);
  InitPerfa(jvmti_, GetThreadLocalJNI(vm));

  return JNI_OK;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options,
                                               void *reserved) {
  Log::V("StudioProfilers agent loaded.");
  return JNI_OK;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *vm, char *options,
                                                 void *reserved) {
  Log::V("StudioProfilers agent attached.");
  return InitAgent(vm);
}

extern "C" JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm) {
  Log::V("StudioProfilers agent unloaded.");
}

}  // namespace profiler
