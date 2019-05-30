/*
 * Copyright (C) 2018 The Android Open Source Project
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
#ifndef PERFD_ENERGY_ENERGY_CACHE_H_
#define PERFD_ENERGY_ENERGY_CACHE_H_

#include <mutex>
#include <unordered_map>
#include <vector>

#include "proto/common.pb.h"
#include "proto/energy.grpc.pb.h"
#include "utils/circular_buffer.h"

namespace profiler {

class EnergyCache {
 public:
  EnergyCache() = default;

  // Add a copy of the passed-in energy event to the internal cache, returning a
  // pointer to the copy (so the caller can optionally edit it further).
  proto::Event* AddEnergyEvent(const proto::Event& data);

  // Query for all the energy events for a given app within the time range
  // (start_time_excl, end_time_incl].
  const std::vector<proto::Event> GetEnergyEvents(int32_t app_id,
                                                  int64_t start_time_excl,
                                                  int64_t end_time_incl);

 private:
  // Mutex that guards the cache.
  std::mutex energy_events_mutex_;
  // Map of app_id to buffer of energy events
  std::unordered_map<int32_t, CircularBuffer<proto::Event>> energy_events_;
};

}  // end of namespace profiler

#endif  // PERFD_ENERGY_ENERGY_CACHE_H_
