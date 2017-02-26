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
#include "perfd/cpu/cpu_collector.h"

#include <unistd.h>
#include <atomic>
#include <thread>

#include "utils/clock.h"
#include "utils/stopwatch.h"
#include "utils/thread_name.h"
#include "utils/trace.h"

namespace profiler {

CpuCollector::~CpuCollector() {
  if (is_running_.load()) {
    Stop();
  }
}

void CpuCollector::Start() {
  if (!is_running_.exchange(true)) {
    sampler_thread_ = std::thread(&CpuCollector::Collect, this);
  }
}

void CpuCollector::Stop() {
  if (is_running_.exchange(false)) {
    sampler_thread_.join();
  }
}

void CpuCollector::Collect() {
  SetThreadName("Studio:PollCpu");

  Stopwatch stopwatch;
  while (is_running_.load()) {
    Trace::Begin("CPU:Collect");
    stopwatch.Start();
    usage_sampler_.Sample();
    thread_monitor_.Monitor();
    int64_t elapsed_time_us = Clock::ns_to_us(stopwatch.GetElapsed());
    Trace::End();
    if (sampling_interval_in_us_ > elapsed_time_us) {
      usleep(sampling_interval_in_us_ - elapsed_time_us);
    } else {
      // Do not sleep. Read data for the next round immediately.
    }
  }
}

}  // namespace profiler
