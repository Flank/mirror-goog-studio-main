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
#include "agent/agent.h"
#include "scoped_local_ref.h"
#include "utils/log.h"

using profiler::Log;
using profiler::Agent;
using profiler::ScopedLocalRef;

namespace profiler {

static jvmtiEnv *jvmti_;

jint InitAgent(JavaVM *vm) {
  jint result = vm->GetEnv((void **)&jvmti_, JVMTI_VERSION_1_2);
  if (result != JNI_OK) {
    Log::E("Error getting jvmtiEnv pointer.");
    return result;
  }

  SetAllCapabilities(jvmti_);
  Agent::Instance();

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
