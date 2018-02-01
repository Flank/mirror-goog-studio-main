/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include "perfd/cpu/internal_cpu_service.h"

using grpc::ServerContext;
using grpc::Status;
using profiler::proto::CpuTraceOperationRequest;
using profiler::proto::EmptyCpuResponse;

namespace profiler {

Status InternalCpuServiceImpl::SendTraceOperation(
    ServerContext* context, const CpuTraceOperationRequest* request,
    EmptyCpuResponse* response) {
  std::cout << "CPU SendTraceOperation " << request->pid() << " "
            << request->thread_id() << " " << request->timestamp() << " "
            << request->api_name() << " " << request->api_signature()
            << std::endl;
  for (int i = 0; i < request->arguments_size(); i++) {
    std::cout << "arg[" << i << "] '" << request->arguments(i) << "'"
              << std::endl;
  }
  return Status::OK;
}

}  // namespace profiler
