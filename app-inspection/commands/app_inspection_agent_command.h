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
 */

#ifndef APP_INSPECTION_AGENT_COMMAND_H_
#define APP_INSPECTION_AGENT_COMMAND_H_

#include "agent/agent.h"

using app::inspection::ServiceResponse;
using profiler::Agent;
using profiler::proto::Command;

class AppInspectionAgentCommand {
 public:
  static void RegisterAppInspectionCommandHandler(JavaVM *vm) {
    Agent::Instance().RegisterCommandHandler(
        Command::APP_INSPECTION, [vm](const Command *command) -> void {
          JNIEnv *jni_env = profiler::GetThreadLocalJNI(vm);
          jclass service_class = jni_env->FindClass(
              "com/android/tools/agent/app/inspection/"
              "AppInspectionService");
          jmethodID instance_method = jni_env->GetStaticMethodID(
              service_class, "instance",
              "()Lcom/android/tools/agent/app/inspection/"
              "AppInspectionService;");
          jobject service =
              jni_env->CallStaticObjectMethod(service_class, instance_method);
          jmethodID command_method =
              jni_env->GetMethodID(service_class, "onCommandStub", "()V");
          jni_env->CallVoidMethod(service, command_method);
        });
  }
};
#endif  // APP_INSPECTION_AGENT_COMMAND_H_