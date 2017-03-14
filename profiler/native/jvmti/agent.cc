/*
 * Copyright (C) 2009 The Android Open Source Project
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

#include "utils/log.h"

using profiler::Log;

extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options,
                                               void *reserved) {
  Log::V("StudioProfilers agent loaded.");
  return JNI_OK;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *vm, char *options,
                                                 void *reserved) {
  Log::V("StudioProfilers agent attached.");
  return JNI_OK;
}

extern "C" JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm) {
  Log::V("StudioProfilers agent unloaded.");
}