/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include "session.h"

#include "daemon/daemon.h"
#include "perfd/samplers/cpu_thread_sampler.h"
#include "perfd/samplers/cpu_usage_sampler.h"
#include "perfd/samplers/memory_usage_sampler.h"
#include "perfd/samplers/network_connection_count_sampler.h"
#include "perfd/samplers/network_speed_sampler.h"
#include "perfd/statsd/pulled_atoms/mobile_bytes_transfer.h"
#include "perfd/statsd/pulled_atoms/wifi_bytes_transfer.h"
#include "perfd/statsd/statsd_subscriber.h"
#include "utils/device_info.h"
#include "utils/procfs_files.h"
#include "utils/uid_fetcher.h"

namespace profiler {

Session::Session(int64_t stream_id, int32_t pid, int64_t start_timestamp,
                 Daemon* daemon) {
  // TODO: Revisit uniqueness of this:
  info_.set_session_id(stream_id ^ (start_timestamp << 1));
  info_.set_stream_id(stream_id);
  info_.set_pid(pid);
  info_.set_start_timestamp(start_timestamp);
  info_.set_end_timestamp(LLONG_MAX);

  if (daemon->config()->GetConfig().common().profiler_unified_pipeline()) {
    samplers_.push_back(std::unique_ptr<Sampler>(
        new profiler::NetworkConnectionCountSampler(*this, daemon->buffer())));
    samplers_.push_back(
        std::unique_ptr<Sampler>(new profiler::NetworkSpeedSampler(
            *this, daemon->clock(), daemon->buffer())));
    samplers_.push_back(
        std::unique_ptr<Sampler>(new profiler::CpuUsageDataSampler(
            *this, daemon->clock(), daemon->buffer())));
    samplers_.push_back(std::unique_ptr<Sampler>(new profiler::CpuThreadSampler(
        *this, daemon->clock(), daemon->buffer())));
    samplers_.push_back(
        std::unique_ptr<Sampler>(new profiler::MemoryUsageSampler(
            *this, daemon->clock(), daemon->buffer())));
  }

  if (DeviceInfo::feature_level() >= DeviceInfo::Q) {
    // statsd is supported on Q+ devices.
    int32_t uid = UidFetcher::GetUid(pid);
    Log::V("Subscribe to statsd atoms for pid %d (uid: %d)", pid, uid);
    if (uid >= 0) {
      StatsdSubscriber::Instance().SubscribeToPulledAtom(
          std::unique_ptr<WifiBytesTransfer>(new WifiBytesTransfer(uid)));
      StatsdSubscriber::Instance().SubscribeToPulledAtom(
          std::unique_ptr<MobileBytesTransfer>(new MobileBytesTransfer(uid)));
    }
  }
}

bool Session::IsActive() const { return info_.end_timestamp() == LLONG_MAX; }

void Session::StartSamplers() {
  for (auto& sampler : samplers_) {
    sampler->Start();
  }

  if (DeviceInfo::feature_level() >= DeviceInfo::Q) {
    StatsdSubscriber::Instance().Run();
  }
}

void Session::StopSamplers() {
  for (auto& sampler : samplers_) {
    sampler->Stop();
  }

  if (DeviceInfo::feature_level() >= DeviceInfo::Q) {
    StatsdSubscriber::Instance().Stop();
  }
}

bool Session::End(int64_t timestamp) {
  if (!IsActive()) {
    return false;
  }

  StopSamplers();
  info_.set_end_timestamp(timestamp);
  return true;
}

}  // namespace profiler
