/*
 * Copyright (C) 2020 The Android Open Source Project
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
#include <agent/agent.h>
#include <jni.h>
#include <iostream>
#include <string>

/**
 * Native calls for loading the properties event protobuf.
 */
extern "C" {
JNIEXPORT jlong JNICALL
Java_com_android_tools_transport_AgentRule_setUpAgentForTest(JNIEnv *env,
                                                             jobject instance,
                                                             jstring channel) {
  auto config = profiler::proto::AgentConfig();
  config.mutable_common()->set_service_address(
      (char *)env->GetStringUTFChars(channel, 0));
  profiler::proto::AgentConfig *oldConfig = new profiler::proto::AgentConfig(
      profiler::Agent::Instance().agent_config());
  profiler::Agent::Instance(true, config);
  return (long)oldConfig;
}

JNIEXPORT void JNICALL Java_com_android_tools_transport_AgentRule_resetAgent(
    JNIEnv *env, jobject instance, jlong origConfigAddr) {
  auto origConfig = (profiler::proto::AgentConfig *)origConfigAddr;
  profiler::Agent::Instance(true, *origConfig);
  delete origConfig;
}
}
