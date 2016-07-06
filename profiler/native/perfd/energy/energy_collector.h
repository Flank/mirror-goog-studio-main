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
#ifndef ENERGY_COLLECTOR_H_
#define ENERGY_COLLECTOR_H_

#include "energy_cache.h"
#include "energy_usage_sampler.h"
#include "proto/energy.grpc.pb.h"
#include "utils/clock.h"

#include <atomic>
#include <thread>

namespace profiler {

// A polling based energy stats collector that saves collected energy samples to
// the provided Energy Cache. Currently each collector will only collect for one
// process at a time, calling start more than once will have no affect after the
// first call.
class EnergyCollector final {
 public:
  EnergyCollector(const Clock& clock, EnergyCache& energy_cache)
      : energy_cache_(energy_cache), energy_usage_sampler_(clock) {}
  ~EnergyCollector();

  // Currently each collector will only collect for one process at a time,
  // Calling this function after the first time will have no affect.
  void Start(int32_t pid);
  void Stop();

 private:
  static constexpr int64_t kSleepNs = profiler::Clock::ms_to_ns(500);
  int32_t pid_;
  EnergyCache& energy_cache_;
  EnergyUsageSampler energy_usage_sampler_;
  std::thread server_thread_;
  std::atomic_bool is_running_{false};

  void CollectorMain();
};  // EnergyCollector

}  // namespace profiler

#endif  // ENERGY_COLLECTOR_H_
