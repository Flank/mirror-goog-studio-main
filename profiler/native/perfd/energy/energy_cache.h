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
#ifndef ENERGY_CACHE_H_
#define ENERGY_CACHE_H_

#include <mutex>

#include "proto/energy.pb.h"

namespace profiler {

// Stores energy samples and loads stored samples into an EnergyDataResponse
// proto.
// This cache will store up to a number of samples determined by
// samples_capacity. Upon reaching capacity old samples will be discarded in
// favor of new ones.
class EnergyCache final {
 public:
  explicit EnergyCache(int32_t samples_capacity)
      : samples_capacity_(samples_capacity),
        samples_(
            new proto::EnergyDataResponse::EnergySample[samples_capacity]){};

  void SaveEnergySample(const proto::EnergyDataResponse::EnergySample& sample);

  // Note that this function will not clobber any previously added samples to
  // the EnergyDataResponse, it will add the new samples instead.
  void LoadEnergyData(int64_t start_time_excl, int64_t end_time_incl,
                      proto::EnergyDataResponse* response);

 private:
  int32_t samples_capacity_;
  // TODO replace with circular buffer class when it becomes available.
  int32_t samples_head_{0};
  int32_t samples_size_{0};
  std::unique_ptr<proto::EnergyDataResponse::EnergySample[]> samples_;
  std::mutex samples_mutex_;

  // Returns an index bounded by the size of the circular buffer.
  int32_t ToValidIndex(int32_t current_index) const;
};

}  // namespace profiler

#endif  // ENERGY_CACHE_H_
