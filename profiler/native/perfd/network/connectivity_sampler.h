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
  // Read device's connectivity information like selected network type.
  void Refresh() override;
  // After |Refresh| is called, this method returns this device's selected
  // network type is wifi or mobile, if mobile, also returns the mobile radio
  // power status. Given app uid is ignored and not needed to get device data.
  proto::NetworkProfilerData Sample(const uint32_t uid = 0) override;

 private:
  // Returns network radio power status when using mobile data, it is not
  // applicable when using non mobile data like wifi.
  proto::ConnectivityData::RadioState GetRadioState();

  // Returns the selected default network type.
  proto::ConnectivityData::NetworkType GetDefaultNetworkType();

  const std::string radio_state_command_;
  const std::string network_type_command_;
  proto::ConnectivityData::NetworkType network_type_;
  proto::ConnectivityData::RadioState radio_state_;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_CONNECTIVITY_SAMPLER_H_
