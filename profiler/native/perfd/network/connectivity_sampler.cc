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

#include "utils/trace.h"
#include "utils/bash_command.h"

namespace {
const char* const kDefaultNetworkLabel = "Active default network: ";
}  // anonymous namespace

namespace profiler {

void ConnectivitySampler::GetData(profiler::proto::NetworkProfilerData* data) {
  data->mutable_connectivity_data()->set_radio_state(GetRadioState());
  data->mutable_connectivity_data()->set_default_network_type(
      GetDefaultNetworkType());
}

proto::ConnectivityData::RadioState ConnectivitySampler::GetRadioState() {
  BashCommandRunner command(radio_state_command_);
  std::string line;
  if (command.Run("", &line)) {
    size_t start = line.find("mNetworkActive=");
    if (start != std::string::npos) {
      size_t end = line.find('\n', start);
      start = line.substr(start, end).find("true");
      return start != std::string::npos
                 ? profiler::proto::ConnectivityData::ACTIVE
                 : profiler::proto::ConnectivityData::SLEEPING;
    }
  }
  return profiler::proto::ConnectivityData::UNSPECIFIED;
}

proto::ConnectivityData::NetworkType
ConnectivitySampler::GetDefaultNetworkType() {
  BashCommandRunner command(network_type_command_);
  std::string line;
  if (!command.Run("", &line)) {
    return proto::ConnectivityData::INVALID;
  }

  // Find the line contains id of the selected default network, for example,
  // "Active default network: 100".
  std::string network_id;
  size_t start = line.find(kDefaultNetworkLabel);
  if (start != std::string::npos) {
    start += strlen(kDefaultNetworkLabel);
    network_id =
        line.substr(start, line.find_first_of(" \n\r\f", start) - start);
  }
  if (network_id.empty()) {
    return proto::ConnectivityData::INVALID;
  }
  for (const char c : network_id) {
    if (!std::isdigit(c)) {
      return proto::ConnectivityData::INVALID;
    }
  }

  // Using token "network{network_id}", find the type of the selected network.
  network_id.assign(" network{" + network_id + "} ");
  size_t network_id_pos = line.find(network_id, start);
  if (network_id_pos != std::string::npos) {
    start = line.find_last_of("\n", network_id_pos);
    if (start != std::string::npos) {
      network_id.assign(line.substr(start + 1, network_id_pos - start - 1));
      if (network_id.find("type: WIFI") != std::string::npos) {
        return proto::ConnectivityData::WIFI;
      } else if (network_id.find("type: MOBILE") != std::string::npos) {
        return proto::ConnectivityData::MOBILE;
      }
    }
  }
  return proto::ConnectivityData::INVALID;
}

}  // namespace profiler
