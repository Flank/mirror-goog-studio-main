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
#ifndef PERFD_CPU_CPU_COLLECTOR_H_
#define PERFD_CPU_CPU_COLLECTOR_H_

#include <atomic>
#include <cstdint>
#include <thread>

#include "perfd/cpu/cpu_usage_sampler.h"
#include "perfd/cpu/thread_monitor.h"

namespace profiler {

class CpuCollector {
 public:
  // Creates a collector that will invoke |usage_sampler| and |thread_monitor|
  // every |interval_in_us| microseconds.
  CpuCollector(int64_t interval_in_us, CpuUsageSampler* usage_sampler,
               ThreadMonitor* thread_monitor)
      : usage_sampler_(*usage_sampler),
        thread_monitor_(*thread_monitor),
        sampling_interval_in_us_(interval_in_us) {}

  ~CpuCollector();

  // Creates a thread that collects and saves data continually.
  // Assumes |Start()| and |Stop()| are called by the same thread.
  void Start();

  // Stops collecting data and wait for thread exit.
  // Assumes |Start()| and |Stop()| are called by the same thread.
  void Stop();

 private:
  // Collects and saves CPU sampling data continually.
  void Collect();

  // Thread that sampling operations run on.
  std::thread sampler_thread_;
  // True if sampling operations is running.
  std::atomic_bool is_running_{false};
  // Holder of sampler operations.
  CpuUsageSampler& usage_sampler_;
  // Monitor of thread activities.
  ThreadMonitor& thread_monitor_;
  // Sampling window size in microseconds.
  int64_t sampling_interval_in_us_;
};

}  // namespace profiler

#endif  // PERFD_CPU_CPU_COLLECTOR_H_
