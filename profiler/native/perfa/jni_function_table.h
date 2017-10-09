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
#ifndef JVMTI_JNI_FUNCTION_TABLE_H
#define JVMTI_JNI_FUNCTION_TABLE_H
#include "jvmti.h"

namespace profiler {

// Interface that needs to be implemented by a class if it needs to
// be notified when global JNI references are being created and
// deleted.
class GlobalRefListener {
 public:
  virtual void AfterGlobalRefCreated(jobject prototype, jobject gref) = 0;
  virtual void BeforeGlobalRefDeleted(jobject gref) = 0;

  virtual void AfterGlobalWeakRefCreated(jobject prototype, jweak gref) = 0;
  virtual void BeforeGlobalWeakRefDeleted(jweak gref) = 0;
 protected:
  // We don't want people to delete instances of children by parent's pointer.
  ~GlobalRefListener() = default;
};

// Registers a new JNI Env functions table, that will be used by all JNI
// environments in the system. That allows profiler to intercept
// Java related activities in native code.
// Currently we only use it for tracking global JNI references, but in the
// future it can be used for much more.
bool RegisterNewJniTable(jvmtiEnv *jvmti_env, GlobalRefListener *gref_listener);
}  // namespace profiler

#endif  // JVMTI_JNI_FUNCTION_TABLE_H
