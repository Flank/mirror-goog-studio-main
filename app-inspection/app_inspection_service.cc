/*
 * Copyright (C) 2019 The Android Open Source Project
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
 */
#include "app_inspection_service.h"

#include "agent/jvmti_helper.h"
#include "utils/log.h"

using namespace profiler;

namespace app_inspection {

AppInspectionService* AppInspectionService::create(JNIEnv* env) {
  JavaVM* vm;
  int error = env->GetJavaVM(&vm);
  if (error != 0) {
    Log::E(
        "Failed to get JavaVM instance for AppInspectionService with error "
        "code: %d",
        error);
    return nullptr;
  }
  // This will attach the current thread to the vm, otherwise
  // CreateJvmtiEnv(vm) below will return JNI_EDETACHED error code.
  GetThreadLocalJNI(vm);
  // Create a stand-alone jvmtiEnv to avoid any callback conflicts
  // with other profilers' agents.
  jvmtiEnv* jvmti = CreateJvmtiEnv(vm);
  if (jvmti == nullptr) {
    Log::E("Failed to initialize JVMTI env for AppInspectionService");
    return nullptr;
  }
  return new AppInspectionService(jvmti);
}

AppInspectionService::AppInspectionService(jvmtiEnv* jvmti) : jvmti_(jvmti) {}

}  // namespace app_inspection