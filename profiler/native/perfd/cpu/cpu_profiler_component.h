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
#include "perfd/cpu/cpu_service.h"
#include "perfd/cpu/thread_monitor.h"
#include "perfd/daemon.h"
#include "perfd/profiler_component.h"

namespace profiler {

class CpuProfilerComponent final : public ProfilerComponent {
 private:
  // Default collection interval is 200 milliseconds, i.e., 0.2 second.
  static const int64_t kDefaultCollectionIntervalUs = Clock::ms_to_us(200);
  // The length of data kept by the CPU component in the daemon.
  static const int64_t kSecondsToBuffer = 5;
  // In CPU cache, a datum is added at each collection event which happens
  // in every collection interval. Dividing the length of history we want
  // to keep by the interval leads to the capacity. We add the capacity
  // by 1 to round up the division.
  static const int64_t kBufferCapacity =
      Clock::s_to_us(kSecondsToBuffer) / kDefaultCollectionIntervalUs + 1;

 public:
  // Creates a CPU perfd component and starts sampling right away.
  explicit CpuProfilerComponent(Daemon::Utilities* utilities)
      : clock_(utilities->clock()),
        usage_sampler_(utilities, &cache_),
        thread_monitor_(utilities, &cache_) {
    collector_.Start();
  }

  // Returns the service that talks to desktop clients (e.g., Studio).
  grpc::Service* GetPublicService() override { return &public_service_; }

  // Returns the service that talks to device clients (e.g., the agent).
  grpc::Service* GetInternalService() override { return nullptr; }

 private:
  const Clock& clock_;
  CpuCache cache_{kBufferCapacity};
  CpuUsageSampler usage_sampler_;
  ThreadMonitor thread_monitor_;
  CpuCollector collector_{kDefaultCollectionIntervalUs, &usage_sampler_,
                          &thread_monitor_};
  CpuServiceImpl public_service_{clock_, &cache_, &usage_sampler_,
                                 &thread_monitor_};
};

}  // namespace profiler

#endif  // PERFD_CPU_CPU_PROFILER_COMPONENT_H_
