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

#include "layoutinspector_agent_command.h"

#include "agent/agent.h"
#include "agent/jvmti_helper.h"

using layoutinspector::EditProperty;
using layoutinspector::LayoutInspectorCommand;
using layoutinspector::LayoutInspectorCommand_Type;
using layoutinspector::PropertyEvent;
using profiler::Agent;
using profiler::proto::Command;
using profiler::proto::SendEventRequest;

void LayoutInspectorAgentCommand::RegisterAgentLayoutInspectorCommandHandler(
    JavaVM* vm) {
  // Register command handlers for agent based commands.
  Agent::Instance().RegisterCommandHandler(
      Command::LAYOUT_INSPECTOR, [vm](const Command* command) -> void {
        const LayoutInspectorCommand* liCommand = &command->layout_inspector();

        JNIEnv* jni_env = profiler::GetThreadLocalJNI(vm);
        jclass inspector_class = jni_env->FindClass(
            "com/android/tools/agent/layoutinspector/LayoutInspectorService");
        // Grab our static instance method.
        jmethodID instance_method = jni_env->GetStaticMethodID(
            inspector_class, "instance",
            "()Lcom/android/tools/agent/layoutinspector/"
            "LayoutInspectorService;");
        jobject inspector_service =
            jni_env->CallStaticObjectMethod(inspector_class, instance_method);

        switch (liCommand->type()) {
          case LayoutInspectorCommand::GET_PROPERTIES: {
            jmethodID properties_command_method = jni_env->GetMethodID(
                inspector_class, "onGetPropertiesInspectorCommand", "(J)V");
            jni_env->CallVoidMethod(inspector_service,
                                    properties_command_method,
                                    liCommand->view_id());
            break;
          }

          case LayoutInspectorCommand::STOP: {
            jmethodID stop_command_method = jni_env->GetMethodID(
                inspector_class, "onStopLayoutInspectorCommand", "()V");
            jni_env->CallVoidMethod(inspector_service, stop_command_method);
            break;
          }

          case LayoutInspectorCommand::START: {
            jmethodID start_command_method = jni_env->GetMethodID(
                inspector_class, "onStartLayoutInspectorCommand", "(Z)V");
            jni_env->CallVoidMethod(inspector_service, start_command_method,
                                    liCommand->compose_mode());
            break;
          }

          case LayoutInspectorCommand::EDIT_PROPERTY: {
            const EditProperty* edit_command = &liCommand->edit_property();

            jmethodID edit_property_command_method = jni_env->GetMethodID(
                inspector_class, "onEditPropertyInspectorCommand", "(JII)V");

            jni_env->CallVoidMethod(
                inspector_service, edit_property_command_method,
                liCommand->view_id(), edit_command->attribute_id(),
                edit_command->int32_value());
            break;
          }

          case LayoutInspectorCommand::USE_SCREENSHOT_MODE: {
            jmethodID screenshot_mode_command_method = jni_env->GetMethodID(
                inspector_class, "onUseScreenshotModeCommand", "(Z)V");

            jni_env->CallVoidMethod(inspector_service,
                                    screenshot_mode_command_method,
                                    liCommand->screenshot_mode());
            break;
          }

          case LayoutInspectorCommand::REFRESH: {
            jmethodID refresh_command_method = jni_env->GetMethodID(
                inspector_class, "onRefreshLayoutInspectorCommand", "()V");

            jni_env->CallVoidMethod(inspector_service, refresh_command_method);
            break;
          }

          default:
            // Ignore unknown commands
            break;
        }
      });
}
