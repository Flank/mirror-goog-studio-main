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

#include "tools/base/deploy/agent/native/swapper.h"

#include <unistd.h>

#include "tools/base/deploy/agent/native/hotswap.h"
#include "tools/base/deploy/agent/native/instrumenter.h"
#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

Swapper* Swapper::instance_ = nullptr;

Swapper& Swapper::Instance() {
  if (instance_ == nullptr) {
    instance_ = new Swapper();
  }
  return *instance_;
}

proto::AgentSwapResponse Swapper::Swap(jvmtiEnv* jvmti, JNIEnv* jni,
                                       const proto::SwapRequest& request) {
  proto::AgentSwapResponse response;
  response.set_pid(getpid());

  if (!InstrumentApplication(jvmti, jni, request.package_name())) {
    ErrEvent("Could not instrument application");
    response.set_status(proto::AgentSwapResponse::INSTRUMENTATION_FAILED);
    return response;
  }

  HotSwap code_swap(jvmti, jni);

  SwapResult result = code_swap.DoHotSwap(request);
  if (result.status == SwapResult::SUCCESS) {
    response.set_status(proto::AgentSwapResponse::OK);
  } else if (result.status == SwapResult::CLASS_NOT_FOUND) {
    response.set_status(proto::AgentSwapResponse::CLASS_NOT_FOUND);
    response.set_class_name(result.error_details);
  } else {
    response.set_status(proto::AgentSwapResponse::JVMTI_ERROR);
    response.mutable_jvmti_error()->set_error_code(result.error_details);
    for (auto& details : result.jvmti_error_details) {
      *response.mutable_jvmti_error()->add_details() = details;
    }
  }

  // Prepare the instrumented code to restart after the package installation (if
  // a restart was requested).
  if (response.status() == proto::AgentSwapResponse::OK) {
    JniClass instrument(
        jni,
        "com/android/tools/deploy/instrument/ActivityThreadInstrumentation");
    jvalue arg{.z = request.restart_activity()};
    instrument.CallStaticMethod<void>({"setRestart", "(Z)V"}, &arg);
  }

  return response;
}

}  // namespace deploy
