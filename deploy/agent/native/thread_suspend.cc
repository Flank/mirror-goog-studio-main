/*
 * Copyright (C) 2019 The Android Open Source Project
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
#include <string.h>

#include "tools/base/deploy/agent/native/capabilities.h"
#include "tools/base/deploy/agent/native/thread_suspend.h"

namespace deploy {

std::string ThreadSuspend::SuspendUserThreads() {
  jthread* threads;
  jint threads_count;

  jvmtiCapabilities cap = REQUIRED_CAPABILITIES;
  // Devices that has the class structural redefinition
  // should can_suspend capabilities.
  cap.can_suspend = 1;

  if (jvmti_->AddCapabilities(&cap) != JVMTI_ERROR_NONE) {
    return "Cannot AddCapabilities can_suspend";
  }

  if (jvmti_->GetAllThreads(&threads_count, &threads) != JVMTI_ERROR_NONE) {
    return "Cannot GetAllThread to suspend";
  }

  for (size_t t = 0; t < threads_count; t++) {
    jvmtiThreadInfo threadInfo;
    if (jvmti_->GetThreadInfo(threads[t], &threadInfo) != JVMTI_ERROR_NONE) {
      return "Cannot GetThreadInfo";
    }

    jthreadGroup group = threadInfo.thread_group;
    jvmtiThreadGroupInfo thread_group_info;
    if (jvmti_->GetThreadGroupInfo(group, &thread_group_info) !=
        JVMTI_ERROR_NONE) {
      return "Cannot GetThreadGroupInfo";
    }

    // Suspend all main thread group since it can contain
    // application classes.
    if (strcmp(thread_group_info.name, "main")) {
      continue;
    }

    // The only thing we skip is the main thread of the main
    // thread group which is currently handling the agent.
    jclass thread_class = jni_->FindClass("java/lang/Thread");
    jmethodID current_thread_mid = jni_->GetStaticMethodID(
        thread_class, "currentThread", "()Ljava/lang/Thread;");
    jobject cur_thread_obj =
        jni_->CallStaticObjectMethod(thread_class, current_thread_mid);
    if (jni_->IsSameObject(cur_thread_obj, threads[t])) {
      continue;
    }

    if (jvmti_->SuspendThread(threads[t]) != JVMTI_ERROR_NONE) {
      return "Cannot SuspendThread";
    }

    suspended_thread_.push_back(threads[t]);
  }

  return "";
}

std::string ThreadSuspend::ResumeSuspendedThreads() {
  for (auto it = suspended_thread_.begin(); it != suspended_thread_.end();
       ++it) {
    if (jvmti_->ResumeThread(*it) != JVMTI_ERROR_NONE) {
      return "Cannot ResumeThread";
    }
  }
  return "";
}

}  // namespace deploy
