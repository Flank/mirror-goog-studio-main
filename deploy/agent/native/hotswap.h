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

#include "tools/base/deploy/agent/native/class_finder.h"
#include "tools/base/deploy/proto/deploy.pb.h"

#define STRUCTRUAL_REDEFINE_EXTENSION \
  "com.android.art.class.structurally_redefine_classes"

namespace deploy {

struct SwapResult {
  enum Status {
    SUCCESS,
    CLASS_NOT_FOUND,
    JVMTI_ERROR,
    UNSUPPORTED_REINIT,
    UNSUPPORTED_REINIT_STATIC_PRIMITIVE,
    UNSUPPORTED_REINIT_STATIC_PRIMITIVE_NOT_CONSTANT,
    UNSUPPORTED_REINIT_STATIC_OBJECT,
    UNSUPPORTED_REINIT_STATIC_ARRAY,
    UNSUPPORTED_REINIT_NON_STATIC_PRIMITIVE,
    UNSUPPORTED_REINIT_NON_STATIC_OBJECT,
    UNSUPPORTED_REINIT_NON_STATIC_ARRAY,
    UNSUPPORTED_REINIT_R_CLASS_VALUE_MODIFIED
  };
  Status status;
  std::string error_details;
  std::vector<proto::JvmtiError::Details> jvmti_error_details;
};

class HotSwap {
 public:
  HotSwap(jvmtiEnv* jvmti, JNIEnv* jni)
      : jvmti_(jvmti), jni_(jni), class_finder_(jvmti_, jni_) {}

  // Invokes JVMTI RedefineClasses with class definitions in the message.
  SwapResult DoHotSwap(const proto::SwapRequest& message) const;

 private:
  jvmtiEnv* jvmti_;
  JNIEnv* jni_;
  ClassFinder class_finder_;

  // Adds new classes to the application class loader.
  void DefineNewClasses(const proto::SwapRequest& message) const;
};

}  // namespace deploy

#endif
