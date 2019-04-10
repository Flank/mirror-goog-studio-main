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

#include "perfd/cpu/cpu_config.h"
#include "perfd/cpu/profiling_app.h"
#include "perfd/cpu/simpleperf_manager.h"
#include "proto/common.pb.h"
#include "utils/activity_manager.h"
#include "utils/file_reader.h"
#include "utils/log.h"
#include "utils/process_manager.h"
#include "utils/trace.h"

using grpc::ServerContext;
using grpc::Status;
using grpc::StatusCode;
using profiler::proto::CpuCoreConfigRequest;
using profiler::proto::CpuCoreConfigResponse;
using profiler::proto::CpuDataRequest;
using profiler::proto::CpuDataResponse;
using profiler::proto::CpuProfilerConfiguration;
using profiler::proto::CpuProfilingAppStartRequest;
using profiler::proto::CpuProfilingAppStartResponse;
using profiler::proto::CpuProfilingAppStopRequest;
using profiler::proto::CpuProfilingAppStopResponse;
using profiler::proto::CpuStartRequest;
using profiler::proto::CpuStartResponse;
using profiler::proto::CpuStopRequest;
using profiler::proto::CpuStopResponse;
using profiler::proto::CpuTraceInfo;
using profiler::proto::CpuTraceMode;
using profiler::proto::CpuTraceType;
using profiler::proto::CpuUsageData;
using profiler::proto::GetThreadsRequest;
using profiler::proto::GetThreadsResponse;
using profiler::proto::GetTraceInfoRequest;
using profiler::proto::GetTraceInfoResponse;
using profiler::proto::GetTraceRequest;
using profiler::proto::GetTraceResponse;
using profiler::proto::ProfilingStateRequest;
using profiler::proto::ProfilingStateResponse;
using profiler::proto::TraceInitiationType;
using std::map;
using std::string;
using std::vector;

namespace profiler {

grpc::Status CpuServiceImpl::GetData(ServerContext* context,
                                     const CpuDataRequest* request,
                                     CpuDataResponse* response) {
  Trace trace("CPU:GetData");
  const vector<CpuUsageData>& data =
      cache_.Retrieve(request->session().pid(), request->start_timestamp(),
                      request->end_timestamp());
  for (const auto& datum : data) {
    *(response->add_data()) = datum;
  }
  return Status::OK;
}

grpc::Status CpuServiceImpl::GetThreads(ServerContext* context,
                                        const GetThreadsRequest* request,
                                        GetThreadsResponse* response) {
  Trace trace("CPU:GetThreads");
  CpuCache::ThreadSampleResponse threads_response =
      cache_.GetThreads(request->session().pid(), request->start_timestamp(),
                        request->end_timestamp());
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

grpc::Status CpuServiceImpl::GetTraceInfo(ServerContext* context,
                                          const GetTraceInfoRequest* request,
                                          GetTraceInfoResponse* response) {
  Trace trace("CPU:GetTraceInfo");
  response->set_response_timestamp(clock_->GetCurrentTime());
  const vector<ProfilingApp>& data =
      cache_.GetCaptures(request->session().pid(), request->from_timestamp(),
                         request->to_timestamp());
  for (const auto& datum : data) {
    // Do not return in-progress captures.
    if (datum.end_timestamp == -1) continue;
    CpuTraceInfo* info = response->add_trace_info();
    info->set_trace_type(datum.configuration.trace_type());
    info->set_trace_mode(datum.configuration.trace_mode());
    info->set_initiation_type(datum.initiation_type);
    info->set_from_timestamp(datum.start_timestamp);
    info->set_to_timestamp(datum.end_timestamp);
    info->set_trace_id(datum.trace_id);
    info->set_trace_file_path(datum.trace_path);
  }
  return Status::OK;
}

grpc::Status CpuServiceImpl::GetTrace(ServerContext* context,
                                      const GetTraceRequest* request,
                                      GetTraceResponse* response) {
  string content;
  bool found = cache_.RetrieveTraceContent(request->session().pid(),
                                           request->trace_id(), &content);
  if (found) {
    response->set_status(GetTraceResponse::SUCCESS);
    response->set_data(content);
    response->set_trace_type(CpuTraceType::ART);
    response->set_trace_mode(CpuTraceMode::INSTRUMENTED);
  } else {
    response->set_status(GetTraceResponse::FAILURE);
  }
  return Status::OK;
}

grpc::Status CpuServiceImpl::StartMonitoringApp(ServerContext* context,
                                                const CpuStartRequest* request,
                                                CpuStartResponse* response) {
  int32_t pid = request->session().pid();
  if (!cache_.AllocateAppCache(pid)) {
    return Status(StatusCode::RESOURCE_EXHAUSTED,
                  "Cannot allocate a cache for CPU data");
  }
  auto status = usage_sampler_.AddProcess(pid);
  if (status == CpuStartResponse::SUCCESS) {
    status = thread_monitor_.AddProcess(pid);
  }
  response->set_status(status);
  return Status::OK;
}

grpc::Status CpuServiceImpl::StopMonitoringApp(ServerContext* context,
                                               const CpuStopRequest* request,
                                               CpuStopResponse* response) {
  int32_t pid = request->session().pid();
  auto status = usage_sampler_.RemoveProcess(pid);
  if (status == CpuStopResponse::SUCCESS) {
    status = thread_monitor_.RemoveProcess(pid);
  }
  response->set_status(status);
  DoStopProfilingApp(pid, nullptr);
  // cache_.DeallocateAppCache(pid) must happen last because prior actions such
  // as DoStopProfilingApp() depends on data from the cache to act.
  cache_.DeallocateAppCache(pid);
  return Status::OK;
}

grpc::Status CpuServiceImpl::StartProfilingApp(
    ServerContext* context, const CpuProfilingAppStartRequest* request,
    CpuProfilingAppStartResponse* response) {
  Trace trace("CPU:StartProfilingApp");
  int32_t pid = request->session().pid();
  ProcessManager process_manager;
  string app_pkg_name = process_manager.GetCmdlineForPid(pid);
  // GetCmdlineForPid will return an empty string
  // if it can't find an app name corresponding to the given pid.
  if (app_pkg_name.empty()) {
    response->set_error_message("App is not running.");
    response->set_status(CpuProfilingAppStartResponse::FAILURE);
    return Status::OK;
  }

  bool success = false;
  string error;
  string trace_path;
  const CpuProfilerConfiguration& configuration = request->configuration();
  if (configuration.trace_type() == CpuTraceType::SIMPLEPERF) {
    success = simpleperf_manager_->StartProfiling(
        app_pkg_name, request->abi_cpu_arch(),
        configuration.sampling_interval_us(), &trace_path, &error);
  } else if (configuration.trace_type() == CpuTraceType::ATRACE) {
    int acquired_buffer_size_kb = 0;
    if (usePerfetto()) {
      // Perfetto always acquires the proper buffer size.
      acquired_buffer_size_kb = configuration.buffer_size_in_mb() * 1024;
      // TODO: We may want to pass this in from studio for a more flexible
      // config.
      perfetto::protos::TraceConfig config =
          PerfettoManager::BuildConfig(app_pkg_name, acquired_buffer_size_kb);
      success = perfetto_manager_->StartProfiling(
          app_pkg_name, request->abi_cpu_arch(), config, &trace_path, &error);
    } else {
      success = atrace_manager_->StartProfiling(
          app_pkg_name, configuration.sampling_interval_us(),
          configuration.buffer_size_in_mb(), &acquired_buffer_size_kb,
          &trace_path, &error);
    }
    response->set_buffer_size_acquired_kb(acquired_buffer_size_kb);
  } else {
    auto mode = ActivityManager::SAMPLING;
    if (configuration.trace_mode() == CpuTraceMode::INSTRUMENTED) {
      mode = ActivityManager::INSTRUMENTED;
    }
    success = activity_manager_->StartProfiling(
        mode, app_pkg_name, configuration.sampling_interval_us(), &trace_path,
        &error);
  }

  if (success) {
    response->set_status(CpuProfilingAppStartResponse::SUCCESS);

    int64_t timestampNs = clock_->GetCurrentTime();
    ProfilingApp profiling_app;
    profiling_app.app_pkg_name = app_pkg_name;
    profiling_app.trace_id = timestampNs;
    profiling_app.trace_path = trace_path;
    profiling_app.start_timestamp = timestampNs;
    profiling_app.end_timestamp = -1;  // -1 means not end yet (ongoing)
    profiling_app.configuration = configuration;
    profiling_app.initiation_type = TraceInitiationType::INITIATED_BY_UI;

    cache_.AddProfilingStart(pid, profiling_app);
  } else {
    response->set_status(CpuProfilingAppStartResponse::FAILURE);
    response->set_error_message(error);
  }
  return Status::OK;
}

grpc::Status CpuServiceImpl::StopProfilingApp(
    ServerContext* context, const CpuProfilingAppStopRequest* request,
    CpuProfilingAppStopResponse* response) {
  DoStopProfilingApp(request->session().pid(), response);
  return Status::OK;
}

void CpuServiceImpl::DoStopProfilingApp(int32_t pid,
                                        CpuProfilingAppStopResponse* response) {
  ProfilingApp* app = cache_.GetOngoingCapture(pid);
  if (app == nullptr) {
    if (response != nullptr) {
      response->set_status(CpuProfilingAppStopResponse::NO_ONGOING_PROFILING);
    }
    return;
  }
  CpuTraceType trace_type = app->configuration.trace_type();
  string error;
  CpuProfilingAppStopResponse::Status status =
      CpuProfilingAppStopResponse::SUCCESS;
  bool need_trace = response != nullptr;
  if (trace_type == CpuTraceType::SIMPLEPERF) {
    status = simpleperf_manager_->StopProfiling(
        app->app_pkg_name, need_trace, cpu_config_.simpleperf_host(), &error);
  } else if (trace_type == CpuTraceType::ATRACE) {
    if (usePerfetto()) {
      status = perfetto_manager_->StopProfiling(&error);
    } else {
      status =
          atrace_manager_->StopProfiling(app->app_pkg_name, need_trace, &error);
    }
  } else {  // Profiler is ART
    status = activity_manager_->StopProfiling(
        app->app_pkg_name, need_trace, &error,
        cpu_config_.art_stop_timeout_sec(), app->is_startup_profiling);
  }

  if (need_trace) {
    if (status == CpuProfilingAppStopResponse::SUCCESS) {
      string trace_content;
      if (FileReader::Read(app->trace_path, &trace_content)) {
        response->set_status(status);
        response->set_trace(trace_content);
        response->set_trace_id(app->trace_id);
      } else {
        response->set_status(CpuProfilingAppStopResponse::CANNOT_READ_FILE);
        response->set_error_message("Failed to read trace from device");
      }
    } else {
      response->set_status(status);
      response->set_error_message(error);
    }
  }

  remove(app->trace_path.c_str());  // No more use of this file. Delete it.
  app->trace_path.clear();
  cache_.AddProfilingStop(pid);
  cache_.AddStartupProfilingStop(app->app_pkg_name);
}

grpc::Status CpuServiceImpl::CheckAppProfilingState(
    ServerContext* context, const ProfilingStateRequest* request,
    ProfilingStateResponse* response) {
  int32_t pid = request->session().pid();
  ProfilingApp* app = cache_.GetOngoingCapture(pid);
  // Whether the app is being profiled (there is a stored start profiling
  // request corresponding to the app)
  response->set_check_timestamp(clock_->GetCurrentTime());
  bool is_being_profiled = app != nullptr;
  response->set_being_profiled(is_being_profiled);

  if (is_being_profiled) {
    // App is being profiled. Include the start profiling request and its
    // timestamp in the response.
    response->set_start_timestamp(app->start_timestamp);
    response->set_is_startup_profiling(app->is_startup_profiling);
    response->set_initiation_type(app->initiation_type);
    *(response->mutable_configuration()) = app->configuration;
  }

  return Status::OK;
}

bool CpuServiceImpl::usePerfetto() {
  return DeviceInfo::feature_level() >= DeviceInfo::P &&
         cpu_config_.use_perfetto();
}

grpc::Status CpuServiceImpl::StartStartupProfiling(
    grpc::ServerContext* context,
    const profiler::proto::StartupProfilingRequest* request,
    profiler::proto::StartupProfilingResponse* response) {
  ProfilingApp app;
  int64_t timestampNs = clock_->GetCurrentTime();
  app.app_pkg_name = request->app_package();
  app.trace_id = timestampNs;
  app.start_timestamp = timestampNs;
  app.end_timestamp = -1;
  app.configuration = request->configuration();
  app.initiation_type = TraceInitiationType::INITIATED_BY_STARTUP;
  app.is_startup_profiling = true;

  CpuTraceType trace_type = app.configuration.trace_type();
  string error;
  bool success = false;
  // TODO: Art should be handled by Debug.startMethodTracing and
  // Debug.stopMethodTracing APIs instrumentation instead. Once our codebase
  // supports instrumenting them, this code should be removed.
  if (trace_type == CpuTraceType::ART) {
    auto mode = ActivityManager::SAMPLING;
    if (app.configuration.trace_mode() == CpuTraceMode::INSTRUMENTED) {
      mode = ActivityManager::INSTRUMENTED;
    }
    success = activity_manager_->StartProfiling(
        mode, app.app_pkg_name, app.configuration.sampling_interval_us(),
        &app.trace_path, &error, true);
    response->set_file_path(app.trace_path);
  } else if (trace_type == CpuTraceType::SIMPLEPERF) {
    success = simpleperf_manager_->StartProfiling(
        app.app_pkg_name, request->abi_cpu_arch(),
        app.configuration.sampling_interval_us(), &app.trace_path, &error,
        true);
  } else if (trace_type == CpuTraceType::ATRACE) {
    int acquired_buffer_size_kb = 0;
    if (usePerfetto()) {
      // Perfetto always acquires the proper buffer size.
      acquired_buffer_size_kb = app.configuration.buffer_size_in_mb() * 1024;
      // TODO: We may want to pass this in from studio for a more flexible
      // config.
      perfetto::protos::TraceConfig config = PerfettoManager::BuildConfig(
          app.app_pkg_name, acquired_buffer_size_kb);
      success = perfetto_manager_->StartProfiling(
          app.app_pkg_name, request->abi_cpu_arch(), config, &app.trace_path,
          &error);
    } else {
      success = atrace_manager_->StartProfiling(
          app.app_pkg_name, app.configuration.sampling_interval_us(),
          app.configuration.buffer_size_in_mb(), &acquired_buffer_size_kb,
          &app.trace_path, &error);
    }
    response->set_buffer_size_acquired_kb(acquired_buffer_size_kb);
  }
  if (success) {
    response->set_status(proto::StartupProfilingResponse::SUCCESS);
  } else {
    response->set_status(proto::StartupProfilingResponse::FAILURE);
    response->set_error_message(error);
  }

  cache_.AddStartupProfilingStart(app.app_pkg_name, app);
  return Status::OK;
}

int64_t CpuServiceImpl::GetEarliestDataTime(int32_t pid) {
  string app_pkg_name = ProcessManager::GetCmdlineForPid(pid);
  ProfilingApp* app = cache_.GetOngoingStartupProfiling(app_pkg_name);
  return app != nullptr ? app->start_timestamp : LLONG_MAX;
}

Status CpuServiceImpl::GetCpuCoreConfig(ServerContext* context,
                                        const CpuCoreConfigRequest* request,
                                        CpuCoreConfigResponse* response) {
  return CpuConfig::GetCpuCoreConfig(response);
}

}  // namespace profiler
