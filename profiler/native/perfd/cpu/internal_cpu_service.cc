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

#include <sstream>
#include <string>

#include "utils/clock.h"
#include "utils/log.h"
#include "utils/process_manager.h"

using grpc::ServerContext;
using grpc::Status;
using profiler::proto::CpuTraceConfiguration;
using profiler::proto::CpuTraceMode;
using profiler::proto::CpuTraceOperationRequest;
using profiler::proto::CpuTraceOperationResponse;
using profiler::proto::CpuTraceType;
using profiler::proto::TraceInitiationType;
using profiler::proto::TraceStartStatus;
using profiler::proto::TraceStopStatus;

namespace profiler {
Status InternalCpuServiceImpl::SendTraceEvent(
    ServerContext* context, const CpuTraceOperationRequest* request,
    CpuTraceOperationResponse* response) {
  int pid = request->pid();
  std::cout << "CPU SendTraceEvent " << pid << " " << request->timestamp()
            << " " << request->detail_case();

  ProcessManager process_manager;
  std::string app_name(process_manager.GetCmdlineForPid(pid));
  if (request->has_start()) {
    CpuTraceConfiguration configuration;
    configuration.set_app_name(app_name);
    configuration.set_initiation_type(TraceInitiationType::INITIATED_BY_API);
    auto* user_options = configuration.mutable_user_options();
    user_options->set_trace_type(CpuTraceType::ART);
    user_options->set_trace_mode(CpuTraceMode::INSTRUMENTED);

    TraceStartStatus start_status;
    auto* capture = trace_manager_->StartProfiling(
        request->timestamp(), configuration, &start_status);
    if (capture == nullptr) {
      std::cout << " START request ignored. " << start_status.error_message()
                << std::endl;
      return Status::OK;
    }

    response->set_start_operation_allowed(true);
    std::cout << " START " << request->start().method_name() << " "
              << request->start().method_signature() << " '"
              << request->start().arg_trace_path() << "'"
              << " trace_id=" << capture->trace_id << std::endl;
  } else if (request->has_stop()) {
    const auto* ongoing = trace_manager_->GetOngoingCapture(app_name);
    if (ongoing == nullptr) {
      Log::E(Log::Tag::PROFILER,
             "No running trace when Debug.stopMethodTracing() is called");
    } else if (ongoing->configuration.initiation_type() !=
               TraceInitiationType::INITIATED_BY_API) {
      Log::E(Log::Tag::PROFILER,
             "Debug.stopMethodTracing() is called but the running trace is not "
             "initiated by startMetghodTracing* APIs");
    } else {
      TraceStopStatus stop_status;
      auto* capture = trace_manager_->StopProfiling(
          request->timestamp(), app_name, false, &stop_status);
      assert(capture != nullptr);
      std::ostringstream oss;
      oss << capture->trace_id;
      std::string file_name = oss.str();
      file_cache_->AddChunk(file_name, request->stop().trace_content());
      file_cache_->Complete(file_name);
    }
    std::cout << " STOP trace_id=" << ongoing->trace_id
              << " size=" << request->stop().trace_content().size()
              << std::endl;
  }
  return Status::OK;
}

}  // namespace profiler
