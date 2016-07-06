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
#include "energy_cache.h"

using profiler::proto::EnergyDataResponse;
using profiler::proto::EnergySample;
using profiler::proto::WakeLockDataResponse;
using profiler::proto::WakeLockEvent;

namespace profiler {

void EnergyCache::SaveEnergySample(const EnergySample& sample) {
  std::lock_guard<std::mutex> lock(energy_samples_mutex_);
  energy_samples_head_ = GetNextValidIndex(energy_samples_head_);
  energy_samples_[energy_samples_head_].CopyFrom(sample);
}

void EnergyCache::SaveWakeLockEvent(const WakeLockEvent& event) {
  std::lock_guard<std::mutex> lock(wake_lock_events_mutex_);
  wake_lock_samples_head_ = GetNextValidIndex(wake_lock_samples_head_);
  wake_lock_events_[wake_lock_samples_head_].CopyFrom(event);
}

void EnergyCache::LoadEnergyData(int64_t start_time_excl, int64_t end_time_incl,
                                 EnergyDataResponse* response) {
  std::lock_guard<std::mutex> sample_lock(energy_samples_mutex_);

  // Can we gurantee the samples be in monotonically increasing timestamp?
  // If so we can optimize this by stopping the moment we hit out later than
  // the end time.

  // Load energy samples.
  int32_t iteration_index = energy_samples_head_;
  do {
    iteration_index = GetNextValidIndex(iteration_index);
    EnergySample& sample = energy_samples_[iteration_index];

    if (sample.timestamp() > start_time_excl &&
        sample.timestamp() <= end_time_incl) {
      response->add_energy_samples()->CopyFrom(
          energy_samples_[iteration_index]);
    }
  } while (iteration_index != energy_samples_head_);
}

void EnergyCache::LoadWakeLockData(int64_t start_time_excl,
                                   int64_t end_time_incl,
                                   WakeLockDataResponse* response) {
  std::lock_guard<std::mutex> event_lock(wake_lock_events_mutex_);

  // Load wake lock events.
  int32_t iteration_index = wake_lock_samples_head_;
  do {
    iteration_index = GetNextValidIndex(iteration_index);
    WakeLockEvent& event = wake_lock_events_[iteration_index];

    if (event.timestamp() > start_time_excl &&
        event.timestamp() <= end_time_incl) {
      response->add_wake_lock_events()->CopyFrom(
          wake_lock_events_[iteration_index]);
    }

  } while (iteration_index != wake_lock_samples_head_);
}

int32_t EnergyCache::GetNextValidIndex(int32_t current_index) const {
  return (current_index + 1) % samples_size_;
}

}  // namespace profiler
