/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <jni.h>
#include "agent/jvmti_helper.h"
#include "utils/log.h"

using profiler::CheckJvmtiError;
using profiler::CreateJvmtiEnv;
using profiler::Log;

extern "C" {

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  Log::D(Log::Tag::LAYOUT_INSPECT, "%s", "Agent_OnLoad");
  jvmtiEnv *jvmti = CreateJvmtiEnv(vm);
  jvmtiCapabilities caps;
  jvmtiError error;
  error = jvmti->GetPotentialCapabilities(&caps);
  CheckJvmtiError(jvmti, error);
  caps.can_access_local_variables = 1;
  caps.can_access_local_variables = 1;
  error = jvmti->AddCapabilities(&caps);
  CheckJvmtiError(jvmti, error);
  return 0;
}
}
