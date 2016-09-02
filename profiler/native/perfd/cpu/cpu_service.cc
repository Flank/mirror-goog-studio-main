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
#include "perfd/cpu/cpu_service.h"

#include "perfd/cpu/simpleperf_manager.h"
#include "utils/activity_manager.h"
#include "utils/process_manager.h"
#include "utils/trace.h"

using grpc::ServerContext;
using grpc::Status;
using profiler::proto::CpuDataRequest;
using profiler::proto::CpuDataResponse;
using profiler::proto::CpuProfilerData;
using profiler::proto::CpuProfilingAppStartRequest;
using profiler::proto::CpuProfilingAppStartResponse;
using profiler::proto::CpuProfilingAppStopRequest;
using profiler::proto::CpuProfilingAppStopResponse;
using profiler::proto::CpuStartRequest;
using profiler::proto::CpuStartResponse;
using profiler::proto::CpuStopRequest;
using profiler::proto::CpuStopResponse;
using std::string;
using std::vector;

namespace profiler {

Status CpuServiceImpl::GetData(ServerContext* context,
                               const CpuDataRequest* request,
                               CpuDataResponse* response) {
  int64_t id_in_request = request->app_id();
  int64_t id = (id_in_request == CpuDataRequest::ANY_APP ? CpuCache::kAnyApp
                                                         : id_in_request);
  Trace trace("CPU:GetData");
  const vector<CpuProfilerData>& data =
      cache_.Retrieve(id, request->start_timestamp(), request->end_timestamp());
  for (const auto& datum : data) {
    *(response->add_data()) = datum;
  }
  return Status::OK;
}

grpc::Status CpuServiceImpl::StartMonitoringApp(ServerContext* context,
                                                const CpuStartRequest* request,
                                                CpuStartResponse* response) {
  auto status = usage_sampler_.AddProcess(request->app_id());
  if (status == CpuStartResponse::SUCCESS) {
    status = thread_monitor_.AddProcess(request->app_id());
  }
  response->set_status(status);
  return Status::OK;
}

grpc::Status CpuServiceImpl::StopMonitoringApp(ServerContext* context,
                                               const CpuStopRequest* request,
                                               CpuStopResponse* response) {
  auto status = usage_sampler_.RemoveProcess(request->app_id());
  if (status == CpuStopResponse::SUCCESS) {
    status = thread_monitor_.RemoveProcess(request->app_id());
  }
  response->set_status(status);
  return Status::OK;
}

grpc::Status CpuServiceImpl::StartProfilingApp(
    ServerContext* context, const CpuProfilingAppStartRequest* request,
    CpuProfilingAppStartResponse* response) {
  Trace trace("CPU:StartProfilingApp");

  ProcessManager process_manager;
  pid_t pid = process_manager.GetPidForBinary(request->app_pkg_name());
  if (pid < 0) {
    response->set_error_message("App is not running.");
    response->set_status(CpuProfilingAppStartResponse::FAILURE);
    return Status::OK;
  }

  bool success = false;
  string error;
  string trace_path;

  if (request->profiler() == CpuProfilingAppStartRequest::SIMPLE_PERF) {
    success = simplerperf_manager_.StartProfiling(request->app_pkg_name(),
                                                  &trace_path, &error);
  } else {
    // TODO: Move the activity manager to the daemon.
    // It should be shared with everything in perfd.
    ActivityManager* manager = ActivityManager::Instance();
    auto mode = ActivityManager::SAMPLING;
    if (request->mode() == CpuProfilingAppStartRequest::INSTRUMENTED) {
      mode = ActivityManager::INSTRUMENTED;
    }
    success = manager->StartProfiling(mode, request->app_pkg_name(), &trace_path,
                                     &error);
  }

  if (success) {
    response->set_trace_filename(trace_path);
    response->set_status(CpuProfilingAppStartResponse::SUCCESS);
  } else {
    response->set_status(CpuProfilingAppStartResponse::FAILURE);
    response->set_error_message(error);
  }
  return Status::OK;
}

grpc::Status CpuServiceImpl::StopProfilingApp(
    ServerContext* context, const CpuProfilingAppStopRequest* request,
    CpuProfilingAppStopResponse* response) {
  string error;
  bool success = false;
  if (request->profiler() == CpuProfilingAppStopRequest::SIMPLE_PERF) {
    success =
        simplerperf_manager_.StopProfiling(request->app_pkg_name(), &error);
  } else {  // Profiler is ART
    ActivityManager* manager = ActivityManager::Instance();
    success = manager->StopProfiling(request->app_pkg_name(), &error);
  }

  if (success) {
    response->set_status(CpuProfilingAppStopResponse::SUCCESS);
  } else {
    response->set_status(CpuProfilingAppStopResponse::FAILURE);
    response->set_error_message(error);
  }
  return Status::OK;
}

}  // namespace profiler
