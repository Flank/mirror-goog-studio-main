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
#include "speed_sampler.h"

#include "utils/clock.h"

namespace profiler {

void SpeedSampler::GetData(profiler::proto::NetworkProfilerData *data) {
  stats_reader_.Refresh();

  SteadyClock clock;
  auto curr_time = clock.GetCurrentTime();

  if (!tx_speed_converter_) {
    tx_speed_converter_.reset(new SpeedConverter(curr_time, stats_reader_.bytes_tx()));
    rx_speed_converter_.reset(new SpeedConverter(curr_time, stats_reader_.bytes_rx()));
  }
  else {
    tx_speed_converter_->Add(curr_time, stats_reader_.bytes_tx());
    rx_speed_converter_->Add(curr_time, stats_reader_.bytes_rx());
  }

  profiler::proto::SpeedData *speed_data = data->mutable_speed_data();
  speed_data->set_sent(tx_speed_converter_->speed());
  speed_data->set_received(rx_speed_converter_->speed());
}

}  // namespace profiler
