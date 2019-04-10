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

#include "perfd/network/connection_count_sampler.h"
#include "perfd/network/connectivity_sampler.h"
#include "perfd/network/network_constants.h"
#include "perfd/network/speed_sampler.h"
#include "perfd/statsd/pulled_atoms/mobile_bytes_transfer.h"
#include "perfd/statsd/pulled_atoms/wifi_bytes_transfer.h"
#include "perfd/statsd/statsd_subscriber.h"
#include "proto/common.pb.h"
#include "statsd/proto/atoms.pb.h"
#include "utils/clock.h"
#include "utils/device_info.h"
#include "utils/thread_name.h"
#include "utils/trace.h"
#include "utils/uid_fetcher.h"

using android::os::statsd::Atom;

namespace profiler {

namespace {

// Helper function to look up statsd atoms and update their network buffer.
void UpdateStatsdBuffer(int32_t pid, NetworkProfilerBuffer* buffer) {
  auto* wifi_bytes_transfer =
      StatsdSubscriber::Instance().FindAtom<WifiBytesTransfer>(
          Atom::PulledCase::kWifiBytesTransfer);
  if (wifi_bytes_transfer != nullptr) {
    assert(wifi_bytes_transfer->pid() == pid);
    wifi_bytes_transfer->SetLegacyBuffer(buffer);
  }

  auto* mobile_bytes_transfer =
      StatsdSubscriber::Instance().FindAtom<MobileBytesTransfer>(
          Atom::PulledCase::kMobileBytesTransfer);
  if (mobile_bytes_transfer != nullptr) {
    assert(mobile_bytes_transfer->pid() == pid);
    mobile_bytes_transfer->SetLegacyBuffer(buffer);
  }
}
}  // namespace

NetworkCollector::NetworkCollector(const DaemonConfig& config, Clock* clock,
                                   int sample_ms)
    : clock_(clock), sample_us_(sample_ms * 1000) {
  if (!config.GetConfig().common().profiler_unified_pipeline()) {
    samplers_.emplace_back(new ConnectivitySampler());
    samplers_.emplace_back(
        new ConnectionCountSampler(NetworkConstants::GetConnectionFilePaths()));

    // On Q+ devices we use statsd to collect network speed data.
    if (DeviceInfo::feature_level() < DeviceInfo::Q) {
      samplers_.emplace_back(
          new SpeedSampler(clock, NetworkConstants::GetTrafficBytesFilePath()));
    }

    is_running_.exchange(true);
    profiler_thread_ = std::thread(&NetworkCollector::Collect, this);
  }
}

NetworkCollector::~NetworkCollector() {
  is_running_.exchange(false);
  if (profiler_thread_.joinable()) {
    profiler_thread_.join();
  }
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
      for (auto& sampler : samplers_) {
        sampler->Refresh();
      }
      StoreDataToBuffer();
    }
    usleep(sample_us_);
  }
}

void NetworkCollector::StoreDataToBuffer() {
  auto time = clock_->GetCurrentTime();
  std::lock_guard<std::mutex> lock(buffer_mutex_);
  for (auto it = uid_to_buffers_.begin(); it != uid_to_buffers_.end(); it++) {
    auto& uid = it->first;
    auto& buffer = it->second;
    for (auto& sampler : samplers_) {
      auto response = sampler->Sample(uid);
      response.set_end_timestamp(time);
      buffer->Add(response, time);
    }
  }
}

void NetworkCollector::Start(int32_t pid, NetworkProfilerBuffer* buffer) {
  int uid = UidFetcher::GetUid(pid);
  std::lock_guard<std::mutex> lock(buffer_mutex_);
  if (uid >= 0) {
    uid_to_buffers_.emplace(uid, buffer);
  }

  // Q+: initialize statsd data buffer now that we have the network data buffer.
  if (DeviceInfo::feature_level() >= DeviceInfo::Q) {
    UpdateStatsdBuffer(pid, buffer);
  }
}

void NetworkCollector::Stop(int32_t pid) {
  // Q+: reset statsd data buffer to stop speed data from being written into it.
  if (DeviceInfo::feature_level() >= DeviceInfo::Q) {
    UpdateStatsdBuffer(pid, nullptr);
  }

  std::lock_guard<std::mutex> lock(buffer_mutex_);
  for (auto it = uid_to_buffers_.begin(); it != uid_to_buffers_.end(); it++) {
    if (pid == it->second->id()) {
      uid_to_buffers_.erase(it);
      return;
    }
  }
}

}  // namespace profiler
