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

#include "perfd/cpu/cpu_cache.h"
#include "perfd/cpu/cpu_usage_sampler.h"
#include "perfd/cpu/thread_monitor.h"
#include "proto/cpu.grpc.pb.h"
#include "perfd/cpu/simpleperf_manager.h"

namespace profiler {

// CPU profiler specific service for desktop clients (e.g., Android Studio).
class CpuServiceImpl final : public profiler::proto::CpuService::Service {
 public:
  CpuServiceImpl(const Clock& clock, CpuCache* cpu_cache,
                 CpuUsageSampler* usage_sampler, ThreadMonitor* thread_monitor)
      : cache_(*cpu_cache),
        usage_sampler_(*usage_sampler),
        thread_monitor_(*thread_monitor),
        simplerperf_manager_(clock){}

  grpc::Status GetData(grpc::ServerContext* context,
                       const profiler::proto::CpuDataRequest* request,
                       profiler::proto::CpuDataResponse* response) override;

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

 private:
  // Data cache that will be queried to serve requests.
  CpuCache& cache_;
  // The monitor that samples CPU usage data.
  CpuUsageSampler& usage_sampler_;
  // The monitor that detects thread activities (i.e., state changes).
  ThreadMonitor& thread_monitor_;
  SimplePerfManager simplerperf_manager_;
};

}  // namespace profiler

#endif  // PERFD_CPU_CPU_PROFILER_SERVICE_H_
