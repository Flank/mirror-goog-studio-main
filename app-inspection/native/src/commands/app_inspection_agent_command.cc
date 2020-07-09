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

#include "app_inspection_agent_command.h"

#include "agent/agent.h"
#include "agent/jvmti_helper.h"

using app_inspection::AppInspectionCommand;
using app_inspection::CreateInspectorCommand;
using app_inspection::DisposeInspectorCommand;
using app_inspection::ServiceResponse;
using profiler::Agent;
using profiler::proto::Command;

void AppInspectionAgentCommand::RegisterAppInspectionCommandHandler(
    JavaVM* vm) {
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

        if (service == nullptr) {
          // failed to instantiate AppInspectionService,
          // errors will have been logged indicating failures.
          return;
        }

        auto& app_command = command->app_inspection_command();
        int32_t command_id = app_command.command_id();
        jstring inspector_id =
            jni_env->NewStringUTF(app_command.inspector_id().c_str());
        if (app_command.has_create_inspector_command()) {
          auto& create_inspector = app_command.create_inspector_command();
          jstring dex_path =
              jni_env->NewStringUTF(create_inspector.dex_path().c_str());
          jstring project = jni_env->NewStringUTF(
              create_inspector.launch_metadata().launched_by_name().c_str());
          jboolean force = create_inspector.launch_metadata().force();
          jmethodID create_inspector_method = jni_env->GetMethodID(
              service_class, "createInspector",
              "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZI)V");
          jni_env->CallVoidMethod(service, create_inspector_method,
                                  inspector_id, dex_path, project, force,
                                  command_id);
        } else if (app_command.has_dispose_inspector_command()) {
          auto& dispose_inspector = app_command.dispose_inspector_command();
          jmethodID dispose_inspector_method = jni_env->GetMethodID(
              service_class, "disposeInspector", "(Ljava/lang/String;I)V");
          jni_env->CallVoidMethod(service, dispose_inspector_method,
                                  inspector_id, command_id);
        } else if (app_command.has_raw_inspector_command()) {
          auto& raw_inspector_command = app_command.raw_inspector_command();
          const std::string& cmd = raw_inspector_command.content();
          jbyteArray raw_command = jni_env->NewByteArray(cmd.length());
          jni_env->SetByteArrayRegion(raw_command, 0, cmd.length(),
                                      (const jbyte*)cmd.c_str());
          jmethodID raw_inspector_method = jni_env->GetMethodID(
              service_class, "sendCommand", "(Ljava/lang/String;I[B)V");
          jni_env->CallVoidMethod(service, raw_inspector_method, inspector_id,
                                  command_id, raw_command);
          jni_env->DeleteLocalRef(raw_command);
        } else if (app_command.has_cancellation_command()) {
          auto& cancellation_command = app_command.cancellation_command();
          int32_t cancelled_command_id =
              cancellation_command.cancelled_command_id();
          jmethodID cancel_command_method =
              jni_env->GetMethodID(service_class, "cancelCommand", "(I)V");
          jni_env->CallVoidMethod(service, cancel_command_method,
                                  cancelled_command_id);
        }
      });
}
