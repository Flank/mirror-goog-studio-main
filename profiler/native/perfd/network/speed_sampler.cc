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

#include "perfd/statsd/pulled_atoms/wifi_bytes_transfer.h"
#include "perfd/statsd/statsd_subscriber.h"
#include "statsd/proto/atoms.pb.h"
#include "utils/clock.h"
#include "utils/device_info.h"

using android::os::statsd::Atom;

namespace profiler {

void SpeedSampler::Refresh() {
  if (DeviceInfo::feature_level() < DeviceInfo::Q) {
    stats_reader_.Refresh();
  }
  // In Q, we use statsd and handle data via callback. No need to refresh.
}

proto::NetworkProfilerData SpeedSampler::Sample(const uint32_t uid) {
  proto::NetworkProfilerData data;
  uint64_t bytes_sent;
  uint64_t bytes_received;

  if (DeviceInfo::feature_level() < DeviceInfo::Q) {
    bytes_sent = stats_reader_.bytes_tx(uid);
    bytes_received = stats_reader_.bytes_rx(uid);
  } else {
    // stats file is deprecated in Q. Use statsd.
    auto* wifi_bytes_transfer =
        StatsdSubscriber::Instance().FindAtom<WifiBytesTransfer>(
            Atom::PulledCase::kWifiBytesTransfer);
    if (wifi_bytes_transfer != nullptr) {
      assert(wifi_bytes_transfer->uid() == uid);
      bytes_sent = wifi_bytes_transfer->tx_bytes();
      bytes_received = wifi_bytes_transfer->rx_bytes();
    } else {
      bytes_sent = 0;
      bytes_received = 0;
    }
  }

  auto time = clock_->GetCurrentTime();
  if (tx_speed_converters_.find(uid) == tx_speed_converters_.end()) {
    SpeedConverter tx_converter(time, bytes_sent);
    SpeedConverter rx_converter(time, bytes_received);
    tx_speed_converters_.emplace(uid, tx_converter);
    rx_speed_converters_.emplace(uid, rx_converter);
  } else {
    tx_speed_converters_.at(uid).Add(time, bytes_sent);
    rx_speed_converters_.at(uid).Add(time, bytes_received);
  }

  profiler::proto::SpeedData* speed_data = data.mutable_speed_data();
  speed_data->set_sent(tx_speed_converters_.at(uid).speed());
  speed_data->set_received(rx_speed_converters_.at(uid).speed());
  return data;
}

}  // namespace profiler
