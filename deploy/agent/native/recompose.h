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
 *
 */
#ifndef RECOMPOSE_H
#define RECOMPOSE_H

#include <jni.h>
#include <jvmti.h>

#include <string>

#include "tools/base/deploy/agent/native/class_finder.h"

#define HOT_RELOADER_CLASS "androidx/compose/runtime/HotReloader"
#define HOT_RELOADER_VMTYPE "Landroidx/compose/runtime/HotReloader$Companion;"

namespace deploy {

class Recompose {
 public:
  Recompose(jvmtiEnv* jvmti, JNIEnv* jni)
      : jvmti_(jvmti), jni_(jni), class_finder_(jvmti_, jni_) {}

  // Save state for Jetpack Compose before activity restart.
  jobject SaveStateAndDispose(jobject reloader) const;

  // Load state for Jetpack Compose after activity restart.
  void LoadStateAndCompose(jobject reloader, jobject state) const;

  // Create ComposeHotReload object if needed.
  jobject GetComposeHotReload() const;

 private:
  jvmtiEnv* jvmti_;
  JNIEnv* jni_;
  ClassFinder class_finder_;
};

}  // namespace deploy

#endif
