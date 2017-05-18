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
#include "perfd/network/speed_sampler.h"
#include "proto/profiler.pb.h"
#include "utils/clock.h"
#include "utils/thread_name.h"
#include "utils/trace.h"
#include "utils/uid_fetcher.h"

namespace profiler {

NetworkCollector::NetworkCollector(int sample_ms)
    : sample_us_(sample_ms * 1000) {
  samplers_.emplace_back(new ConnectivitySampler(
      NetworkConstants::GetRadioStatusCommand(),
      NetworkConstants::GetDefaultNetworkTypeCommand()));
  samplers_.emplace_back(
      new SpeedSampler(NetworkConstants::GetTrafficBytesFilePath()));
  samplers_.emplace_back(
      new ConnectionSampler(NetworkConstants::GetConnectionFilePaths()));

  is_running_.exchange(true);
  profiler_thread_ = std::thread(&NetworkCollector::Collect, this);
}

NetworkCollector::~NetworkCollector() {
  is_running_.exchange(false);
  profiler_thread_.join();
}

void NetworkCollector::Collect() {
  SetThreadName("Studio:PollNet");
  while (is_running_.load()) {
    bool should_sample;
    {
      std::lock_guard<std::mutex> lock(buffer_mutex_);
      should_sample = !uid_to_buffers_.empty();
    }

    if (should_sample) {
      Trace trace("NET:Collect");
      for (auto &sampler : samplers_) {
        sampler->Refresh();
      }
      StoreDataToBuffer();
    }
    usleep(sample_us_);
  }
}

void NetworkCollector::StoreDataToBuffer() {
  // TODO: Use clock from Daemon::Utilities from profiler component
  SteadyClock clock;
  auto time = clock.GetCurrentTime();
  std::lock_guard<std::mutex> lock(buffer_mutex_);
  for (auto it = uid_to_buffers_.begin(); it != uid_to_buffers_.end(); it++) {
    auto &uid = it->first;
    auto &buffer = it->second;
    for (auto &sampler : samplers_) {
      auto response = sampler->Sample(uid);
      response.mutable_basic_info()->set_process_id(buffer->pid());
      response.mutable_basic_info()->set_end_timestamp(time);
      buffer->Add(response, time);
    }
  }
}

void NetworkCollector::Start(int32_t pid, NetworkProfilerBuffer *buffer) {
  int uid = UidFetcher::GetUid(pid);
  std::lock_guard<std::mutex> lock(buffer_mutex_);
  if (uid >= 0) {
    uid_to_buffers_.emplace(uid, buffer);
  }
}

void NetworkCollector::Stop(int32_t pid) {
  std::lock_guard<std::mutex> lock(buffer_mutex_);
  for (auto it = uid_to_buffers_.begin(); it != uid_to_buffers_.end(); it++) {
    if (pid == it->second->pid()) {
      uid_to_buffers_.erase(it);
      return;
    }
  }
}

}  // namespace profiler
