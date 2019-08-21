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
 */

#ifndef SWAP_ACTION_H_
#define SWAP_ACTION_H_

#include <jni.h>
#include <jvmti.h>

#include <memory>
#include <string>

#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

class Swapper {
 public:
  ~Swapper() = default;

  // Performs a swap using the specified swap request.
  proto::AgentSwapResponse Swap(jvmtiEnv* jvmti, JNIEnv* jni,
                                const proto::SwapRequest& request);

  // Returns the current swapper instance.
  static Swapper& Instance();

 private:
  static Swapper* instance_;
  Swapper() = default;
};
}  // namespace deploy

#endif
