
/*
 * Copyright (C) 2009 The Android Open Source Project
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
#include <jni.h>
#include <unistd.h>

#include "agent/agent.h"
#include "agent/support/jni_wrappers.h"
#include "utils/clock.h"
#include "utils/config.h"

using profiler::Agent;
using profiler::Config;
using profiler::JStringWrapper;
using profiler::proto::ActivityData;
using profiler::proto::AgentConfig;

extern "C" {
// TODO: Figure out how to autogenerate this class, to avoid typo errors.
// This function only gets called by non-jvmti instrumented apps. As such
// only the service address is used.
JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_ProfilerService_initializeNative(
    JNIEnv* env, jobject thiz, jstring jtext) {
  JStringWrapper text(env, jtext);
  AgentConfig agent_config;
  agent_config.set_service_address(text.get());
  agent_config.set_socket_type(profiler::proto::UNSPECIFIED_SOCKET);
  Config config(agent_config);
  Agent::Instance(&config);
}
}