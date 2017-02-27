/*
 * Copyright (C) 2016 The Android Open Source Project
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
#include "perfa_service.h"

using grpc::ServerContext;
using profiler::proto::CommonData;
using profiler::proto::HeartBeatResponse;

namespace profiler {

grpc::Status PerfaServiceImpl::HeartBeat(ServerContext* context,
                                         const CommonData* data,
                                         HeartBeatResponse* response) {
  heartbeat_timestamp_map_[data->process_id()] = clock_.GetCurrentTime();
  return grpc::Status::OK;
}

}  // namespace profiler
