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
#include "agent_task.h"

using grpc::ClientContext;
using profiler::proto::AgentService;
using profiler::proto::EmptyResponse;
using profiler::proto::SendBytesRequest;
using std::string;

namespace profiler {

// Since gRPC 1.22.0, the max size per gRPC message is 4 MB.
// In each message, we set the max size of the |bytes| field slightly under 4 MB
// to leave some space for other fields such as |name| and the overhead from
// protobuf message packaging and serialization.
const int kMaxChunkSizePerMessage = 4 * 1000 * 1000;

const std::vector<AgentServiceTask> CreateTasksToSendPayload(
    const string& payload_name, const string& payload, bool is_complete) {
  std::vector<AgentServiceTask> tasks;
  int size = payload.size();  // number of bytes remained to be sent
  int start = 0;  // start position of the substring to send in next task
  int length = payload.size();  // length of the chunk to send in next task
  do {
    if (size <= kMaxChunkSizePerMessage) {
      length = size;
    } else {
      length = kMaxChunkSizePerMessage;
    }
    tasks.emplace_back([payload_name, payload, start, length](
                           AgentService::Stub& stub, ClientContext& ctx) {
      SendBytesRequest request;
      request.set_name(payload_name);
      request.set_bytes(payload.substr(start, length));
      EmptyResponse response;
      return stub.SendBytes(&ctx, request, &response);
    });
    start += length;
    size -= length;
  } while (size > 0);

  if (is_complete) {
    tasks.emplace_back(
        [payload_name](AgentService::Stub& stub, ClientContext& ctx) {
          SendBytesRequest request;
          request.set_name(payload_name);
          request.set_is_complete(true);
          EmptyResponse response;
          return stub.SendBytes(&ctx, request, &response);
        });
  }
  return tasks;
}

}  // end of namespace profiler
