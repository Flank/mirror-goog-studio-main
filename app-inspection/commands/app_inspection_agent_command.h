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

#ifndef APP_INSPECTION_INSPECTOR_COMMAND_H_
#define APP_INSPECTION_INSPECTOR_COMMAND_H_

#include "agent/agent.h"

using app::inspection::AppInspectionCommand;
using app::inspection::CreateInspectorCommand;
using app::inspection::DisposeInspectorCommand;
using app::inspection::ServiceResponse;
using profiler::Agent;
using profiler::proto::Command;

class AppInspectionAgentCommand {
 public:
  static void RegisterAppInspectionCommandHandler(JavaVM* vm) {
    Agent::Instance().RegisterCommandHandler(
        Command::APP_INSPECTION, [vm](const Command* command) -> void {
          JNIEnv* jni_env = profiler::GetThreadLocalJNI(vm);
          jclass service_class = jni_env->FindClass(
              "com/android/tools/agent/app/inspection/"
              "AppInspectionService");
          jmethodID instance_method = jni_env->GetStaticMethodID(
              service_class, "instance",
              "()Lcom/android/tools/agent/app/inspection/"
              "AppInspectionService;");
          jobject service =
              jni_env->CallStaticObjectMethod(service_class, instance_method);

          int32_t command_id = command->command_id();
          auto& app_command = command->androidx_inspection_command();
          if (app_command.has_create_inspector_command()) {
            auto& create_inspector = app_command.create_inspector_command();
            jstring inspector_id =
                jni_env->NewStringUTF(create_inspector.inspector_id().c_str());
            jstring dex_path =
                jni_env->NewStringUTF(create_inspector.dex_path().c_str());
            jmethodID create_inspector_method = jni_env->GetMethodID(
                service_class, "createInspector",
                "(Ljava/lang/String;Ljava/lang/String;I)V");
            jni_env->CallVoidMethod(service, create_inspector_method,
                                    inspector_id, dex_path, command_id);
          } else if (app_command.has_dispose_inspector_command()) {
            auto& dispose_inspector = app_command.dispose_inspector_command();
            jstring inspector_id =
                jni_env->NewStringUTF(dispose_inspector.inspector_id().c_str());
            jmethodID dispose_inspector_method = jni_env->GetMethodID(
                service_class, "disposeInspector", "(Ljava/lang/String;I)V");
            jni_env->CallVoidMethod(service, dispose_inspector_method,
                                    inspector_id, command_id);
          } else if (app_command.has_raw_inspector_command()) {
            auto& raw_inspector_command = app_command.raw_inspector_command();
            jstring inspector_id = jni_env->NewStringUTF(
                raw_inspector_command.inspector_id().c_str());
            const std::string& cmd = raw_inspector_command.content();
            jbyteArray raw_command = jni_env->NewByteArray(cmd.length());
            jni_env->SetByteArrayRegion(raw_command, 0, cmd.length(),
                                        (const jbyte*)cmd.c_str());
            jmethodID raw_inspector_method = jni_env->GetMethodID(
                service_class, "sendCommand", "(Ljava/lang/String;I[B)V");
            jni_env->CallVoidMethod(service, raw_inspector_method, inspector_id,
                                    command_id, raw_command);
            jni_env->DeleteLocalRef(raw_command);
          }
        });
  }
};
#endif  // APP_INSPECTION_INSPECTOR_COMMAND_H_
