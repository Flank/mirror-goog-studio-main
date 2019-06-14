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
#ifndef HOTSWAP_H
#define HOTSWAP_H

#include <jni.h>
#include <jvmti.h>

#include <string>

#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

struct SwapResult {
  enum Status { SUCCESS, CLASS_NOT_FOUND, JVMTI_ERROR };
  Status status;
  std::string error_details;
  std::vector<proto::JvmtiError::Details> jvmti_error_details;
};

class HotSwap {
 public:
  HotSwap(jvmtiEnv* jvmti, JNIEnv* jni) : jvmti_(jvmti), jni_(jni) {}

  // Invokes JVMTI RedefineClasses with class definitions in the message.
  SwapResult DoHotSwap(const proto::SwapRequest& message) const;

 private:
  jvmtiEnv* jvmti_;
  JNIEnv* jni_;

  // Finds a class definition.
  jclass FindClass(const std::string& name) const;

  // Finds a class definition by searching the specified class loader.
  jclass FindInClassLoader(jobject class_loader, const std::string& name) const;

  // Finds a class definition by enumerating all loaded classes in the VM.
  jclass FindInLoadedClasses(const std::string& name) const;

  // Adds new classes to the application class loader.
  void DefineNewClasses(const proto::SwapRequest& message) const;
};

}  // namespace deploy

#endif
