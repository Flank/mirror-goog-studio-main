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
#include "jni_function_table.h"

#include <atomic>
#include <cstdarg>
#include <mutex>

#include "utils/log.h"

namespace profiler {

static jniNativeInterface *g_original_native_table = nullptr;
static std::atomic<GlobalRefListener *>g_gref_listener(nullptr);

namespace jni_wrappers {

static jobject NewGlobalRef(JNIEnv *env, jobject lobj) {
  auto result = g_original_native_table->NewGlobalRef(env, lobj);
  GlobalRefListener *gref_listener = g_gref_listener;
  if (gref_listener != nullptr) {
    gref_listener->AfterGlobalRefCreated(lobj, result);
  }
  return result;
}

static void DeleteGlobalRef(JNIEnv *env, jobject gref) {
  GlobalRefListener *gref_listener = g_gref_listener;
  if (gref_listener != nullptr) {
    gref_listener->BeforeGlobalRefDeleted(gref);
  }
  g_original_native_table->DeleteGlobalRef(env, gref);
}

}  // namespace jni_wrappers

bool RegisterJniTableListener(jvmtiEnv *jvmti_env,
                              GlobalRefListener *gref_listener) {
  static std::mutex g_mutex;
  std::lock_guard<std::mutex> guard(g_mutex);
  jvmtiError error = JVMTI_ERROR_NONE;

  if (jvmti_env == nullptr) return false;

  // Get original JNI table when RegisterJniTableListener called for the
  // very first time.
  if (g_original_native_table == nullptr) {
    jvmtiError error = jvmti_env->GetJNIFunctionTable(&g_original_native_table);
    if (error != JVMTI_ERROR_NONE || g_original_native_table == nullptr) {
      Log::E("Failed obtain original JNI table.");
      return false;
    }
  }
  // Copy an old table into a new one.
  // Even though JVM TI spec says that SetJNIFunctionTable copies JNI table
  // rather than just save a pointer. ART's implementation just stores
  // a pointer given to it. (bug 69483740)
  // That's why new_native_table needs to be static.
  static jniNativeInterface new_native_table;
  new_native_table = *g_original_native_table;

  // If needed amend the new table with our wrappers around
  // global reference related functions.
  if (gref_listener != nullptr) {
    new_native_table.NewGlobalRef = jni_wrappers::NewGlobalRef;
    new_native_table.DeleteGlobalRef = jni_wrappers::DeleteGlobalRef;
  }

  g_gref_listener = gref_listener;
  error = jvmti_env->SetJNIFunctionTable(&new_native_table);
  if (error != JVMTI_ERROR_NONE) {
    Log::E("Failed to set new JNI table");
    return false;
  }
  Log::V("New JNI table set");

  return true;
}
}  // namespace profiler
