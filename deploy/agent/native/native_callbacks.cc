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

#include "native_callbacks.h"

#include <unistd.h>

#include "capabilities.h"
#include "config.h"
#include "hotswap.h"
#include "jni/jni_class.h"
#include "jni/jni_util.h"
#include "socket.h"
#include "utils/log.h"

namespace swapper {

bool RegisterNatives(JNIEnv* jni, const vector<NativeBinding>& bindings) {
  for (auto& binding : bindings) {
    jclass klass = jni->FindClass(binding.class_name);

    if (jni->ExceptionCheck()) {
      jni->ExceptionClear();
      return false;
    }

    jni->RegisterNatives(klass, &binding.native_method, 1);
    jni->DeleteLocalRef(klass);
  }

  return true;
}

int Native_GetAppInfoChanged(JNIEnv* jni, jobject object) {
  JniClass activity_thread_h(jni, "android/app/ActivityThread$H");
  return activity_thread_h.GetStaticField<jint>(
      {"APPLICATION_INFO_CHANGED", "I"});
}

bool Native_TryRedefineClasses(JNIEnv* jni, jobject object, jlong request_ptr,
                               jlong socket_ptr) {
  JavaVM* vm;
  if (jni->GetJavaVM(&vm) != 0) {
    return false;
  }

  jvmtiEnv* jvmti;
  if (!GetJvmti(vm, jvmti)) {
    return false;
  }

  HotSwap code_swap(jvmti, jni);

  // TODO(noahz): Prevent us from creating two JVMTI instances.
  if (jvmti->AddCapabilities(&REQUIRED_CAPABILITIES) != JVMTI_ERROR_NONE) {
    return false;
  }

  auto request = reinterpret_cast<proto::SwapRequest*>(request_ptr);
  auto socket = reinterpret_cast<deploy::Socket*>(socket_ptr);

  proto::SwapResponse response;
  response.set_pid(getpid());

  if (!code_swap.DoHotSwap(*request, response.mutable_error_details())) {
    response.set_status(proto::SwapResponse::ERROR);
  } else {
    response.set_status(proto::SwapResponse::OK);
  }

  std::string response_bytes;
  response.SerializeToString(&response_bytes);
  socket->Write(response_bytes);

  jvmti->RelinquishCapabilities(&REQUIRED_CAPABILITIES);

  // These were allocated in AttachAgent.
  delete request;
  delete socket;

  return response.status() == proto::SwapResponse::OK;
}

}  // namespace swapper
