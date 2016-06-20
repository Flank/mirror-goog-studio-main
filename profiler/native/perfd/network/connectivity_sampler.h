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
#ifndef PERFD_NETWORK_CONNECTIVITY_SAMPLER_H_
#define PERFD_NETWORK_CONNECTIVITY_SAMPLER_H_

#include <string>

#include "network_sampler.h"
#include "proto/network.pb.h"

namespace profiler {

class ConnectivitySampler final : public NetworkSampler {
 public:
  ConnectivitySampler(const std::string &radio_state_command,
                      const std::string &network_type_command)
      : radio_state_command_(radio_state_command),
        network_type_command_(network_type_command) {}

  enum NetworkType {
    // Value is consistent with |ConnectivityManager#TYPE_MOBILE|.
    MOBILE = 0,
    // Value is consistent with |ConnectivityManager#TYPE_WIFI|.
    WIFI = 1,
    // Value for failing to find the network type.
    INVALID = -1,
  };

  void GetData(profiler::proto::NetworkProfilerData *data) override;

 private:
  profiler::proto::ConnectivityData::RadioState GetRadioState();

  // Returns the selected default network type.
  NetworkType GetDefaultNetworkType();

  const std::string radio_state_command_;
  const std::string network_type_command_;
};

}  // namespace profiler

#endif // PERFD_NETWORK_CONNECTIVITY_SAMPLER_H_
