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
#ifndef PERFA_PERFA_H_
#define PERFA_PERFA_H_

#include "jvmti.h"

#include "proto/agent_service.grpc.pb.h"

namespace profiler {
void RegisterPerfaCommandHandlers(JavaVM* vm, jvmtiEnv* jvmti_env,
                                  const proto::AgentConfig& agent_config);
}  // end of namespace profiler

#endif  // PERFA_PERFA_H_