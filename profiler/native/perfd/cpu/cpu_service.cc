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

#include <stdio.h>

#include "perfd/cpu/simpleperf_manager.h"
#include "proto/profiler.pb.h"
#include "utils/activity_manager.h"
#include "utils/file_reader.h"
#include "utils/process_manager.h"
#include "utils/trace.h"

using grpc::ServerContext;
using grpc::Status;
using grpc::StatusCode;
using profiler::proto::CpuDataRequest;
using profiler::proto::CpuDataResponse;
using profiler::proto::CpuProfilerData;
using profiler::proto::CpuProfilerType;
using profiler::proto::CpuProfilingAppStartRequest;
using profiler::proto::CpuProfilingAppStartResponse;
using profiler::proto::CpuProfilingAppStopRequest;
using profiler::proto::CpuProfilingAppStopResponse;
using profiler::proto::CpuStartRequest;
using profiler::proto::CpuStartResponse;
using profiler::proto::CpuStopRequest;
using profiler::proto::CpuStopResponse;
using profiler::proto::GetThreadsRequest;
using profiler::proto::GetThreadsResponse;
using profiler::proto::ProfilingStateRequest;
using profiler::proto::ProfilingStateResponse;
using std::map;
using std::string;
using std::vector;

namespace profiler {

grpc::Status CpuServiceImpl::GetData(ServerContext* context,
                                     const CpuDataRequest* request,
                                     CpuDataResponse* response) {
  int64_t id_in_request = request->process_id();
  int64_t id = (id_in_request == CpuDataRequest::ANY_APP ? proto::AppId::ANY
                                                         : id_in_request);
  Trace trace("CPU:GetData");
  const vector<CpuProfilerData>& data =
      cache_.Retrieve(id, request->start_timestamp(), request->end_timestamp());
  for (const auto& datum : data) {
    *(response->add_data()) = datum;
  }
  return Status::OK;
}

grpc::Status CpuServiceImpl::GetThreads(ServerContext* context,
                                        const GetThreadsRequest* request,
                                        GetThreadsResponse* response) {
  int64_t id = request->process_id();
  if (id == CpuDataRequest::ANY_APP) {
    // |response| expects a single app, so we return early (with error) if
    // request does not provide a single app process id.
    return Status(StatusCode::INVALID_ARGUMENT, "Invalid process id");
  }

  Trace trace("CPU:GetThreads");
  CpuCache::ThreadSampleResponse threads_response = cache_.GetThreads(
      id, request->start_timestamp(), request->end_timestamp());
  // Samples containing all the activities that should be added to the response.
  const vector<ThreadsSample>& samples = threads_response.activity_samples;

  // Snapshot that should be included in the response.
  auto snapshot = threads_response.snapshot;
  if (snapshot.threads().empty()) {
    // If there are no threads in the |snapshot|, we use the snapshot of the
    // first sample from |samples|, in case it's not empty
    if (!samples.empty()) {
      *(response->mutable_initial_snapshot()) = samples.front().snapshot;
    }
  } else {
    *(response->mutable_initial_snapshot()) = snapshot;
  }

  // Threads that should be added to the response, ordered by thread id.
  // The activities detected by the sampled should be grouped by thread.
  map<int32_t, GetThreadsResponse::Thread> threads;

  for (const auto& sample : samples) {
    for (const auto& activity : sample.activities) {
      auto tid = activity.tid;
      // Add the thread to the map if it's not there yet.
      if (threads.find(tid) == threads.end()) {
        GetThreadsResponse::Thread thread;
        thread.set_tid(tid);
        thread.set_name(activity.name);
        threads[tid] = thread;
      }
      auto* thread_activity = threads[tid].add_activities();
      thread_activity->set_timestamp(activity.timestamp);
      thread_activity->set_new_state(activity.state);
    }
  }

  // Add all the threads to the response.
  for (const auto& thread : threads) {
    *(response->add_threads()) = thread.second;
  }
  return Status::OK;
}

grpc::Status CpuServiceImpl::StartMonitoringApp(ServerContext* context,
                                                const CpuStartRequest* request,
                                                CpuStartResponse* response) {
  if (!cache_.AllocateAppCache(request->process_id())) {
    return Status(StatusCode::RESOURCE_EXHAUSTED,
                  "Cannot allocate a cache for CPU data");
  }
  auto status = usage_sampler_.AddProcess(request->process_id());
  if (status == CpuStartResponse::SUCCESS) {
    status = thread_monitor_.AddProcess(request->process_id());
  }
  response->set_status(status);
  return Status::OK;
}

grpc::Status CpuServiceImpl::StopMonitoringApp(ServerContext* context,
                                               const CpuStopRequest* request,
                                               CpuStopResponse* response) {
  cache_.DeallocateAppCache(request->process_id());
  auto status = usage_sampler_.RemoveProcess(request->process_id());
  if (status == CpuStopResponse::SUCCESS) {
    status = thread_monitor_.RemoveProcess(request->process_id());
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

  if (request->profiler_type() == CpuProfilerType::SIMPLE_PERF) {
    success = simplerperf_manager_.StartProfiling(
        request->app_pkg_name(), request->sampling_interval_us(), &trace_path_,
        &error);
  } else {
    // TODO: Move the activity manager to the daemon.
    // It should be shared with everything in perfd.
    ActivityManager* manager = ActivityManager::Instance();
    auto mode = ActivityManager::SAMPLING;
    if (request->mode() == CpuProfilingAppStartRequest::INSTRUMENTED) {
      mode = ActivityManager::INSTRUMENTED;
    }
    success = manager->StartProfiling(mode, request->app_pkg_name(),
                                      request->sampling_interval_us(),
                                      &trace_path_, &error);
  }

  if (success) {
    response->set_status(CpuProfilingAppStartResponse::SUCCESS);
    last_start_profiling_timestamps_[request->app_pkg_name()] =
        clock_.GetCurrentTime();
    last_start_profiling_requests_[request->app_pkg_name()] = *request;
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
  if (request->profiler_type() == CpuProfilerType::SIMPLE_PERF) {
    success =
        simplerperf_manager_.StopProfiling(request->app_pkg_name(), &error);
  } else {  // Profiler is ART
    ActivityManager* manager = ActivityManager::Instance();
    success = manager->StopProfiling(request->app_pkg_name(), &error);
  }

  if (success) {
    response->set_status(CpuProfilingAppStopResponse::SUCCESS);
    string trace_content;
    FileReader::Read(trace_path_, &trace_content);
    response->set_trace(trace_content);
    // Set the trace id to a random integer
    // TODO: Change to something more predictable/robust
    int trace_id = rand() % INT_MAX;
    response->set_trace_id(trace_id);
    remove(trace_path_.c_str());  // No more use of this file. Delete it.
    trace_path_.clear();          // Make it clear no trace file is alive.
    last_start_profiling_timestamps_.erase(request->app_pkg_name());
    last_start_profiling_requests_.erase(request->app_pkg_name());
  } else {
    response->set_status(CpuProfilingAppStopResponse::FAILURE);
    response->set_error_message(error);
  }
  return Status::OK;
}

grpc::Status CpuServiceImpl::CheckAppProfilingState(
    ServerContext* context, const ProfilingStateRequest* request,
    ProfilingStateResponse* response) {
  const auto& last_request =
      last_start_profiling_requests_.find(request->app_pkg_name());

  // Whether the app is being profiled (there is a stored start profiling
  // request corresponding to the app)
  bool is_being_profiled = last_request != last_start_profiling_requests_.end();
  response->set_being_profiled(is_being_profiled);
  response->set_check_timestamp(clock_.GetCurrentTime());
  if (is_being_profiled) {
    // App is being profiled. Include the start profiling request and its
    // timestamp in the response.
    response->set_start_timestamp(
        last_start_profiling_timestamps_[request->app_pkg_name()]);
    *(response->mutable_start_request()) =
        last_start_profiling_requests_[request->app_pkg_name()];
  }

  return Status::OK;
}

}  // namespace profiler
