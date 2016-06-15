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
#include "perfd/cpu/cpu_profiler_service.h"

#include "proto/cpu_profiler_data.grpc.pb.h"
#include "utils/activity_manager.h"

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

Status CpuProfilerServiceImpl::GetData(ServerContext* context,
                                       const CpuDataRequest* request,
                                       CpuDataResponse* response) {
  int64_t id_in_request = request->app_id();
  int64_t id = (id_in_request == CpuDataRequest::ANY_APP ? CpuCache::kAnyApp
                                                         : id_in_request);
  const vector<CpuProfilerData>& data =
      cache_.Retrieve(id, request->start_timestamp(), request->end_timestamp());
  for (const auto& datum : data) {
    *(response->add_data()) = datum;
  }
  return Status::OK;
}

grpc::Status CpuProfilerServiceImpl::StartMonitoringApp(
    ServerContext* context, const CpuStartRequest* request,
    CpuStartResponse* response) {
  auto status = usage_sampler_.AddProcess(request->app_id());
  if (status == CpuStartResponse::SUCCESS) {
    status = thread_monitor_.AddProcess(request->app_id());
  }
  response->set_status(status);
  return Status::OK;
}

grpc::Status CpuProfilerServiceImpl::StopMonitoringApp(
    ServerContext* context, const CpuStopRequest* request,
    CpuStopResponse* response) {
  auto status = usage_sampler_.RemoveProcess(request->app_id());
  if (status == CpuStopResponse::SUCCESS) {
    status = thread_monitor_.RemoveProcess(request->app_id());
  }
  response->set_status(status);
  return Status::OK;
}

grpc::Status CpuProfilerServiceImpl::StartProfilingApp(
    ServerContext* context, const CpuProfilingAppStartRequest* request,
    CpuProfilingAppStartResponse* response) {
  // TODO: Move the activity manager to the daemon.
  // It should be shared with everything in perfd.
  ActivityManager am;
  string trace_path;
  string error;
  auto mode = ActivityManager::SAMPLING;
  if (request->mode() == CpuProfilingAppStartRequest::INSTRUMENTED) {
    mode = ActivityManager::INSTRUMENTED;
  }
  bool success =
      am.StartProfiling(mode, request->app_pkg_name(), &trace_path, &error);
  if (success) {
    response->set_trace_filename(trace_path);
    response->set_status(CpuProfilingAppStartResponse::SUCCESS);
  } else {
    response->set_status(CpuProfilingAppStartResponse::FAILURE);
    response->set_error_message(error);
  }
  return Status::OK;
}

grpc::Status CpuProfilerServiceImpl::StopProfilingApp(
    ServerContext* context, const CpuProfilingAppStopRequest* request,
    CpuProfilingAppStopResponse* response) {
  ActivityManager am;
  string error;
  bool success = am.StopProfiling(request->app_pkg_name(), &error);
  if (success) {
    response->set_status(CpuProfilingAppStopResponse::SUCCESS);
  } else {
    response->set_status(CpuProfilingAppStopResponse::FAILURE);
    response->set_error_message(error);
  }
  return Status::OK;
}

}  // namespace profiler
