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
#include "connectivity_sampler.h"

#include "utils/bash_command.h"

namespace profiler {

void ConnectivitySampler::Refresh() {
  network_type_ = network_type_provider_->GetDefaultNetworkType();
  if (network_type_ == proto::ConnectivityData::MOBILE) {
    radio_state_ = GetRadioState();
  } else {
    radio_state_ = profiler::proto::ConnectivityData::UNSPECIFIED;
  }
}

proto::NetworkProfilerData ConnectivitySampler::Sample(const uint32_t uid) {
  proto::NetworkProfilerData data;
  data.mutable_connectivity_data()->set_default_network_type(network_type_);
  data.mutable_connectivity_data()->set_radio_state(radio_state_);
  return data;
}

proto::ConnectivityData::RadioState ConnectivitySampler::GetRadioState() {
  BashCommandRunner command(radio_state_command_);
  std::string line;
  if (command.Run("", &line)) {
    size_t start = line.find("mNetworkActive=");
    if (start != std::string::npos) {
      if (line.find("mNetworkActive=true", start) != std::string::npos) {
        return profiler::proto::ConnectivityData::HIGH;
      }
      else if (line.find("mNetworkActive=false", start) != std::string::npos) {
        return profiler::proto::ConnectivityData::LOW;
      }
    }
  }
  return profiler::proto::ConnectivityData::UNSPECIFIED;
}

}  // namespace profiler
