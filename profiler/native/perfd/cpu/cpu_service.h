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
#include "proto/agent_service.grpc.pb.h"
#include "proto/cpu.grpc.pb.h"
#include "utils/activity_manager.h"
#include "utils/current_process.h"
#include "utils/device_info.h"
#include "utils/fs/disk_file_system.h"
#include "utils/termination_service.h"

namespace profiler {

// CPU profiler specific service for desktop clients (e.g., Android Studio).
class CpuServiceImpl final : public profiler::proto::CpuService::Service {
 public:
  CpuServiceImpl(Clock* clock, CpuCache* cpu_cache,
                 CpuUsageSampler* usage_sampler, ThreadMonitor* thread_monitor,
                 const profiler::proto::AgentConfig::CpuConfig& cpu_config,
                 TerminationService* termination_service)
      : CpuServiceImpl(
            clock, cpu_cache, usage_sampler, thread_monitor, cpu_config,
            termination_service, ActivityManager::Instance(),
            std::unique_ptr<SimpleperfManager>(new SimpleperfManager(clock)),
            // Number of millis to wait between atrace dumps when profiling.
            // The average user will run a capture around 20 seconds, however to
            // support longer captures we should dump the data (causing a
            // hitch). This data dump enables us to have long captures.
            std::unique_ptr<AtraceManager>(new AtraceManager(
                std::unique_ptr<FileSystem>(new DiskFileSystem()), clock,
                1000 * 30))) {}

  CpuServiceImpl(Clock* clock, CpuCache* cpu_cache,
                 CpuUsageSampler* usage_sampler, ThreadMonitor* thread_monitor,
                 const profiler::proto::AgentConfig::CpuConfig& cpu_config,
                 TerminationService* termination_service,
                 ActivityManager* activity_manager,
                 std::unique_ptr<SimpleperfManager> simpleperf_manager,
                 std::unique_ptr<AtraceManager> atrace_manager)
      : cache_(*cpu_cache),
        clock_(clock),
        usage_sampler_(*usage_sampler),
        thread_monitor_(*thread_monitor),
        cpu_config_(cpu_config),
        activity_manager_(activity_manager),
        simpleperf_manager_(std::move(simpleperf_manager)),
        atrace_manager_(std::move(atrace_manager)) {
    termination_service->RegisterShutdownCallback([this](int signal) {
      this->activity_manager_->Shutdown();
      this->simpleperf_manager_->Shutdown();
      this->atrace_manager_->Shutdown();
    });
  }

  grpc::Status GetData(grpc::ServerContext* context,
                       const profiler::proto::CpuDataRequest* request,
                       profiler::proto::CpuDataResponse* response) override;

  grpc::Status GetThreads(
      grpc::ServerContext* context,
      const profiler::proto::GetThreadsRequest* request,
      profiler::proto::GetThreadsResponse* response) override;

  grpc::Status GetTraceInfo(
      grpc::ServerContext* context,
      const profiler::proto::GetTraceInfoRequest* request,
      profiler::proto::GetTraceInfoResponse* response) override;

  grpc::Status GetTrace(grpc::ServerContext* context,
                        const profiler::proto::GetTraceRequest* request,
                        profiler::proto::GetTraceResponse* response) override;

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

  grpc::Status StartStartupProfiling(
      grpc::ServerContext* context,
      const profiler::proto::StartupProfilingRequest* request,
      profiler::proto::StartupProfilingResponse* response) override;

  int64_t GetEarliestDataTime(int32_t pid);

  grpc::Status GetCpuCoreConfig(
      grpc::ServerContext* context,
      const profiler::proto::CpuCoreConfigRequest* request,
      profiler::proto::CpuCoreConfigResponse* response) override;

  // Visible for testing.
  SimpleperfManager* simpleperf_manager() { return simpleperf_manager_.get(); }

  // Visible for testing.
  AtraceManager* atrace_manager() { return atrace_manager_.get(); }

 private:
  // Stops profiling process of |pid|, regardless of whether it is alive or
  // dead. If |response| is not null, populate it with the capture data (trace);
  // otherwise, discard any capture result.
  void DoStopProfilingApp(
      int32_t pid, profiler::proto::CpuProfilingAppStopResponse* response);

  // Data cache that will be queried to serve requests.
  CpuCache& cache_;
  // Clock that timestamps start profiling requests.
  Clock* clock_;
  // The monitor that samples CPU usage data.
  CpuUsageSampler& usage_sampler_;
  // The monitor that detects thread activities (i.e., state changes).
  ThreadMonitor& thread_monitor_;

  const proto::AgentConfig::CpuConfig cpu_config_;
  ActivityManager* activity_manager_;
  std::unique_ptr<SimpleperfManager> simpleperf_manager_;
  std::unique_ptr<AtraceManager> atrace_manager_;
};

}  // namespace profiler

#endif  // PERFD_CPU_CPU_PROFILER_SERVICE_H_
