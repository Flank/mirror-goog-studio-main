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
#include "energy_collector.h"

#include "utils/clock.h"
#include "utils/stopwatch.h"
#include "utils/thread_name.h"

#include <unistd.h>

namespace profiler {

EnergyCollector::~EnergyCollector() { Stop(); }

void EnergyCollector::Start(int32_t pid) {
  if (!is_running_.exchange(true)) {
    pid_ = pid;
    server_thread_ = std::thread([this] { this->CollectorMain(); });
  }
}

void EnergyCollector::Stop() {
  if (is_running_.exchange(false)) {
    server_thread_.join();
  }
}

void EnergyCollector::CollectorMain() {
  SetThreadName("EnergyCollector");

  Stopwatch stopwatch;
  while (is_running_) {
    int64_t start_time_ns = stopwatch.GetElapsed();

    proto::EnergySample sample;
    energy_usage_sampler_.GetProcessEnergyUsage(pid_, &sample);
    energy_cache_->SaveEnergySample(sample);

    int64_t elapsed_time_ns = stopwatch.GetElapsed() - start_time_ns;
    if (kSleepNs > elapsed_time_ns) {
      int64_t sleep_time_us = Clock::ns_to_us(kSleepNs - elapsed_time_ns);
      usleep(static_cast<uint64_t>(sleep_time_us));
    }
  }

  is_running_.exchange(false);
}
}  // namespace profiler
