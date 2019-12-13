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
#include <functional>
#include <vector>

#include <grpc++/grpc++.h>

#include "proto/agent_service.grpc.pb.h"

namespace profiler {

// Function for submitting an agent grpc request via |stub| using the given
// |context|. Returns the status from the grpc call.
using AgentServiceTask = std::function<grpc::Status(
    proto::AgentService::Stub& stub, grpc::ClientContext& context)>;

// Creates a list of agent service tasks to send |payload| of |playload_name|
// in chunks that are compatible with gPRC size limit (4 MB). Marks the payload
// as complete if |is_complete| is true.
const std::vector<AgentServiceTask> CreateTasksToSendPayload(
    const std::string& playload_name, const std::string& payload,
    bool is_complete);

}  // end of namespace profiler
