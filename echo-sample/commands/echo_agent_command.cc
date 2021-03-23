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

#include "echo_agent_command.h"

#include "agent/agent.h"
#include "jvmti/jvmti_helper.h"

using profiler::Agent;
using profiler::proto::Command;

void EchoAgentCommand::RegisterAgentEchoCommandHandler(JavaVM* vm) {
  // Register command handlers for agent based commands.
  Agent::Instance().RegisterCommandHandler(
      Command::ECHO, [vm](const Command* command) -> void {
        JNIEnv* jni_env = profiler::GetThreadLocalJNI(vm);
        // Grab a java Class object to represent our echo class.
        jclass echo_class =
            jni_env->FindClass("com/android/tools/agent/echo/EchoService");
        // Grab our static instance method.
        jmethodID instance_method = jni_env->GetStaticMethodID(
            echo_class, "Instance",
            "()Lcom/android/tools/agent/echo/EchoService;");
        // Call it to grab a pointer to our echo service.
        jobject echo_service =
            jni_env->CallStaticObjectMethod(echo_class, instance_method);
        // Grab a handle to our echo command method.
        jmethodID echo_command_method = jni_env->GetMethodID(
            echo_class, "onEchoCommand", "(Ljava/lang/String;)V");
        // Call it with our command arguments.
        jstring message =
            jni_env->NewStringUTF(command->echo_data().data().c_str());
        jni_env->CallVoidMethod(echo_service, echo_command_method, message);
        jni_env->DeleteLocalRef(message);
      });
}
