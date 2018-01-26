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
#ifndef PERFD_CPU_CPU_PROFILER_SERVICE_H_
#define PERFD_CPU_CPU_PROFILER_SERVICE_H_

#include <grpc++/grpc++.h>
#include <map>
#include <string>

#include "perfd/cpu/atrace_manager.h"
#include "perfd/cpu/cpu_cache.h"
#include "perfd/cpu/cpu_usage_sampler.h"
#include "perfd/cpu/simpleperf.h"
#include "perfd/cpu/simpleperf_manager.h"
#include "perfd/cpu/thread_monitor.h"
#include "proto/cpu.grpc.pb.h"
#include "utils/current_process.h"
#include "utils/device_info.h"

namespace profiler {

// CPU profiler specific service for desktop clients (e.g., Android Studio).
class CpuServiceImpl final : public profiler::proto::CpuService::Service {
 public:
  CpuServiceImpl(const Clock& clock, CpuCache* cpu_cache,
                 CpuUsageSampler* usage_sampler, ThreadMonitor* thread_monitor)
      : cache_(*cpu_cache),
        clock_(clock),
        usage_sampler_(*usage_sampler),
        thread_monitor_(*thread_monitor),
        simpleperf_manager_(clock, simpleperf_),
        // Number of millis to wait between atrace dumps when profiling.
        atrace_manager_(clock, 500) {}

  grpc::Status GetData(grpc::ServerContext* context,
                       const profiler::proto::CpuDataRequest* request,
                       profiler::proto::CpuDataResponse* response) override;

  grpc::Status GetThreads(
      grpc::ServerContext* context,
      const profiler::proto::GetThreadsRequest* request,
      profiler::proto::GetThreadsResponse* response) override;

  // TODO: Handle the case if there is no such a running process.
  grpc::Status StartMonitoringApp(
      grpc::ServerContext* context,
      const profiler::proto::CpuStartRequest* request,
      profiler::proto::CpuStartResponse* response) override;

  grpc::Status StopMonitoringApp(
      grpc::ServerContext* context,
      const profiler::proto::CpuStopRequest* request,
      profiler::proto::CpuStopResponse* response) override;

  grpc::Status StartProfilingApp(
      grpc::ServerContext* context,
      const profiler::proto::CpuProfilingAppStartRequest* request,
      profiler::proto::CpuProfilingAppStartResponse* response) override;

  grpc::Status StopProfilingApp(
      grpc::ServerContext* context,
      const profiler::proto::CpuProfilingAppStopRequest* request,
      profiler::proto::CpuProfilingAppStopResponse* response) override;

  grpc::Status CheckAppProfilingState(
      grpc::ServerContext* context,
      const profiler::proto::ProfilingStateRequest* request,
      profiler::proto::ProfilingStateResponse* response) override;

 private:
  // Stops profiling process of |pid|, regardless of whether it is alive or
  // dead. If |response| is not null, populate it with the capture data (trace);
  // otherwise, discard any capture result.
  void StopProfilingAndCleanUp(
      int32_t pid, profiler::proto::CpuProfilingAppStopResponse* response);

  // Data cache that will be queried to serve requests.
  CpuCache& cache_;
  // Clock that timestamps start profiling requests.
  const Clock& clock_;
  // The monitor that samples CPU usage data.
  CpuUsageSampler& usage_sampler_;
  // The monitor that detects thread activities (i.e., state changes).
  ThreadMonitor& thread_monitor_;
  const Simpleperf simpleperf_{CurrentProcess::dir(),
                               DeviceInfo::is_emulator()};
  SimpleperfManager simpleperf_manager_;
  AtraceManager atrace_manager_;
  // Absolute on-device path to the trace file. Activity manager or simpleperf
  // determines the path and populate the file with trace data.
  std::string trace_path_;
  // The timestamp when the last start profiling request was processed
  // successfully. Map from an app name to its corresponding timestamp.
  std::map<std::string, int64_t> last_start_profiling_timestamps_;
  // The last start profiling requests processed successfully.
  // Map from an app name to its corresponding request.
  std::map<std::string, profiler::proto::CpuProfilingAppStartRequest>
      last_start_profiling_requests_;
  // Map a pid to its corresponding app name. It's used for sanity check if an
  // app has restarted and got a new pid.
  std::map<int, std::string> app_pids_;
};

}  // namespace profiler

#endif  // PERFD_CPU_CPU_PROFILER_SERVICE_H_
