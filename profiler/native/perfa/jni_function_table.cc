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

#include <cstdarg>

namespace profiler {

static jniNativeInterface *g_old_native_table = nullptr;
static GlobalRefListener *g_gref_listener = nullptr;

namespace jni_wrappers {

static jobject NewGlobalRef(JNIEnv *env, jobject lobj) {
  auto result = g_old_native_table->NewGlobalRef(env, lobj);
  g_gref_listener->AfterGlobalRefCreated(lobj, result);
  return result;
}

static void DeleteGlobalRef(JNIEnv *env, jobject gref) {
  g_gref_listener->BeforeGlobalRefDeleted(gref);
  g_old_native_table->DeleteGlobalRef(env, gref);
}

static jweak NewWeakGlobalRef(JNIEnv *env, jobject obj) {
  auto result = g_old_native_table->NewWeakGlobalRef(env, obj);
  g_gref_listener->AfterGlobalWeakRefCreated(obj, result);
  return result;
}

static void DeleteWeakGlobalRef(JNIEnv *env, jweak ref) {
  g_gref_listener->BeforeGlobalWeakRefDeleted(ref);
  g_old_native_table->DeleteWeakGlobalRef(env, ref);
}

}  // namespace jni_wrappers

bool RegisterNewJniTable(jvmtiEnv *jvmti_env,
                         GlobalRefListener *gref_listener) {
  // We must have both arguments to successfully register new JNI table.
  if (jvmti_env == nullptr || gref_listener == nullptr) return false;

  // We can call RegisterNewJniTable only once.
  if (g_old_native_table != nullptr || g_gref_listener != nullptr) return false;

  jvmtiError error = jvmti_env->GetJNIFunctionTable(&g_old_native_table);
  if (error != JNI_OK || g_old_native_table == nullptr) return false;

  // Copy an old table into a new one and amend it with our wrappers around
  // global reference related functions.
  static jniNativeInterface new_native_table = *g_old_native_table;
  new_native_table.NewGlobalRef = jni_wrappers::NewGlobalRef;
  new_native_table.DeleteGlobalRef = jni_wrappers::DeleteGlobalRef;
  new_native_table.NewWeakGlobalRef = jni_wrappers::NewWeakGlobalRef;
  new_native_table.DeleteWeakGlobalRef = jni_wrappers::DeleteWeakGlobalRef;

  error = jvmti_env->SetJNIFunctionTable(&new_native_table);
  if (error != JNI_OK) return false;

  g_gref_listener = gref_listener;

  return true;
}
}  // namespace profiler
