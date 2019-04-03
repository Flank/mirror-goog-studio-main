/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include "jvmti.h"

#include <dlfcn.h>
#include <fstream>
#include <string>

#include "agent/agent.h"
#include "jvmti_helper.h"
#include "perfa/perfa.h"
#include "utils/device_info.h"
#include "utils/log.h"

#include "commands/echo_agent_command.h"
#include "commands/layoutinspector_agent_command.h"

using profiler::Agent;
using profiler::Log;
using profiler::proto::AgentConfig;

namespace profiler {

// Retrieve the app's data directory path
static std::string GetAppDataPath() {
  Dl_info dl_info;
  dladdr((void*)Agent_OnAttach, &dl_info);
  std::string so_path(dl_info.dli_fname);
  return so_path.substr(0, so_path.find_last_of('/') + 1);
}

static bool ParseConfigFromPath(const std::string& path, AgentConfig* config) {
  std::fstream input(path, std::ios::in | std::ios::binary);
  bool ret = config->ParseFromIstream(&input);
  input.close();
  return ret;
}

void LoadDex(jvmtiEnv* jvmti, JNIEnv* jni) {
  // Load in perfa.jar which should be in to data/data.
  std::string agent_lib_path(GetAppDataPath());
  agent_lib_path.append("perfa.jar");
  jvmti->AddToBootstrapClassLoaderSearch(agent_lib_path.c_str());
}

// JVMTI callback to perform after the agent is attached.
// See https://docs.oracle.com/javase/8/docs/platform/jvmti/jvmti.html#onattach
extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* options,
                                                 void* reserved) {
  jvmtiEnv* jvmti_env = CreateJvmtiEnv(vm);
  if (jvmti_env == nullptr) {
    return JNI_ERR;
  }

  if (options == nullptr) {
    Log::E("Config file parameter was not specified");
    return JNI_ERR;
  }

  SetAllCapabilities(jvmti_env);

  AgentConfig config;
  if (!ParseConfigFromPath(options, &config)) {
    Log::E("Failed to parse config from %s", options);
    return JNI_ERR;
  }
  Agent::Instance(config);

  JNIEnv* jni_env = GetThreadLocalJNI(vm);
  LoadDex(jvmti_env, jni_env);

  // Echo example agent.
  EchoAgentCommand::RegisterAgentEchoCommandHandler(vm);

  // Resource inspector agent.
  if (DeviceInfo::feature_level() >= DeviceInfo::Q) {
    LayoutInspectorAgentCommand::RegisterAgentLayoutInspectorCommandHandler(vm);
  }

  // Profiler agent.
  SetupPerfa(vm, jvmti_env, config);

  Agent::Instance().AddDaemonConnectedCallback([] {
    Agent::Instance().StartHeartbeat();
    // Perf-test currently waits on this message to determine that agent is
    // connected to daemon.
    Log::V("Transport agent connected to daemon.");
  });

  return JNI_OK;
}

}  // namespace profiler
