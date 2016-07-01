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
#include "network_collector.h"

#include <unistd.h>
#include <cstdint>

#include "perfd/network/connection_sampler.h"
#include "perfd/network/connectivity_sampler.h"
#include "perfd/network/network_constants.h"
#include "perfd/network/traffic_sampler.h"
#include "utils/trace.h"
#include "utils/clock.h"

namespace profiler {

NetworkCollector::~NetworkCollector() {
  if (is_running_) {
    Stop();
  }
}

void NetworkCollector::Start() {
  if (samplers_.empty()) {
    CreateSamplers();
  }
  if (!is_running_.exchange(true)) {
    profiler_thread_ = std::thread(&NetworkCollector::Collect, this);
  }
}

void NetworkCollector::Stop() {
  if (is_running_.exchange(false)) {
    profiler_thread_.join();
  }
}

void NetworkCollector::Collect() {
  profiler::SteadyClock clock;
  while (is_running_.load()) {
    Trace::Begin("NET:Collect");
    for (const auto &sampler : samplers_) {
      profiler::proto::NetworkProfilerData response;
      sampler->GetData(&response);
      int64_t time = clock.GetCurrentTime();
      response.mutable_basic_info()->set_app_id(pid_);
      response.mutable_basic_info()->set_end_timestamp(time);
      buffer_.Add(response, time);
    }
    Trace::End();
    usleep(sample_us_);
  }
}

void NetworkCollector::CreateSamplers() {
  // TODO: Define a centralized ANY_APP id.
  // TODO: This class will be replaced by a follow up CL soon.
  NetworkConstants network_constants;
  if (pid_ == -1) {
    samplers_.emplace_back(new ConnectivitySampler(
        network_constants.GetRadioStatusCommand(),
        network_constants.GetDefaultNetworkTypeCommand()));
    return;
  }

  std::string uid;
  bool has_uid = NetworkSampler::GetUidString(
      network_constants.GetPidStatusFilePath(pid_), &uid);
  if (has_uid) {
    samplers_.emplace_back(
        new TrafficSampler(uid, network_constants.GetTrafficBytesFilePath()));
    samplers_.emplace_back(
        new ConnectionSampler(uid, network_constants.GetConnectionFilePaths()));
  }
}

int NetworkCollector::pid() { return pid_; }

}  // profiler
