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
#include "energy_cache.h"

namespace profiler {

void EnergyCache::AddEnergyEvent(const EnergyEvent& data) {
  std::lock_guard<std::mutex> lock(energy_events_mutex_);
  energy_events_.emplace(data.pid(), CircularBuffer<EnergyEvent>(500))
      .first->second.Add(data);
}

const vector<EnergyEvent> EnergyCache::GetEnergyEvents(int32_t app_id,
                                                       int64_t start_time_excl,
                                                       int64_t end_time_incl) {
  std::lock_guard<std::mutex> lock(energy_events_mutex_);
  vector<EnergyEvent> result;
  auto search = energy_events_.find(app_id);
  if (search != energy_events_.end()) {
    for (size_t i = 0; i < search->second.size(); ++i) {
      const auto& energy_event = search->second.Get(i);
      int64_t timestamp = energy_event.timestamp();
      if (timestamp > start_time_excl && timestamp <= end_time_incl) {
        result.push_back(energy_event);
      }
    }
  }
  return result;
}

}  // namespace profiler
