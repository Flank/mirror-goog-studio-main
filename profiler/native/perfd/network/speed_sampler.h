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
#ifndef PERFD_NETWORK_SPEED_SAMPLER_H_
#define PERFD_NETWORK_SPEED_SAMPLER_H_

#include "perfd/network/net_stats_file_reader.h"
#include "perfd/network/network_sampler.h"
#include "perfd/network/speed_converter.h"
#include "proto/network.pb.h"

#include <string>
#include <unordered_map>

namespace profiler {

// Data collector of network traffic information. For example, it provides sent
// and received network speeds of an app.
class SpeedSampler final : public NetworkSampler {
 public:
  SpeedSampler(const std::string &file) : stats_reader_(file) {}

  // Read every app's traffic bytes sent and received, and save data internally.
  void Refresh() override;
  // Returns a given app's traffic bytes from last refresh.
  proto::NetworkProfilerData Sample(const uint32_t uid) override;

 private:
  NetStatsFileReader stats_reader_;
  // Mapping of app uid to the app's bytes sent speed converter.
  std::unordered_map<uint32_t, SpeedConverter> tx_speed_converters_;
  // Mapping of app uid to the app's bytes received speed converter.
  std::unordered_map<uint32_t, SpeedConverter> rx_speed_converters_;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_SPEED_SAMPLER_H_
