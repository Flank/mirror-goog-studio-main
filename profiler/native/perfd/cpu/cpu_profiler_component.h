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
#ifndef PERFD_CPU_CPU_PROFILER_COMPONENT_H_
#define PERFD_CPU_CPU_PROFILER_COMPONENT_H_

#include "perfd/cpu/cpu_cache.h"
#include "perfd/cpu/cpu_collector.h"
#include "perfd/cpu/cpu_profiler_service.h"
#include "perfd/cpu/thread_monitor.h"
#include "perfd/daemon.h"
#include "perfd/profiler_component.h"

namespace profiler {

class CpuProfilerComponent final : public ProfilerComponent {
 private:
  // Deafult collection interval is 100000 microseconds, i.e., 0.1 second.
  static const int64_t kDefaultCollectionIntervalUs = 100000;

 public:
  // Creates a CPU perfd component and starts sampling right away.
  CpuProfilerComponent(const Daemon& daemon)
      : usage_sampler_(daemon, &cache_), thread_monitor_(daemon, &cache_) {
    collector_.Start();
  }

  // Returns the service that talks to desktop clients (e.g., Studio).
  grpc::Service* GetPublicService() override { return &public_service_; }

  // Returns the service that talks to device clients (e.g., perfa).
  grpc::Service* GetInternalService() override { return nullptr; }

 private:
  CpuCache cache_;
  CpuUsageSampler usage_sampler_;
  ThreadMonitor thread_monitor_;
  CpuCollector collector_{kDefaultCollectionIntervalUs, &usage_sampler_,
                          &thread_monitor_};
  CpuProfilerServiceImpl public_service_{&cache_, &usage_sampler_,
                                         &thread_monitor_};
};

}  // namespace profiler

#endif  // PERFD_CPU_CPU_PROFILER_COMPONENT_H_
