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

#include "perfd/daemon.h"
#include "perfd/samplers/network_connection_count_sampler.h"
#include "perfd/samplers/network_speed_sampler.h"

namespace profiler {

Session::Session(int64_t device_id, int32_t pid, int64_t start_timestamp,
                 Daemon* daemon) {
  // TODO: Revisit uniqueness of this:
  info_.set_session_id(device_id ^ (start_timestamp << 1));
  info_.set_device_id(device_id);
  info_.set_pid(pid);
  info_.set_start_timestamp(start_timestamp);
  info_.set_end_timestamp(LLONG_MAX);

  if (daemon->config()->GetAgentConfig().unified_pipeline()) {
    samplers_.push_back(
        new profiler::NetworkConnectionCountSampler(*this, daemon->buffer()));
    samplers_.push_back(new profiler::NetworkSpeedSampler(
        *this, daemon->clock(), daemon->buffer()));
  }
}

bool Session::IsActive() const { return info_.end_timestamp() == LLONG_MAX; }

bool Session::End(int64_t timestamp) {
  if (!IsActive()) {
    return false;
  }

  for (auto sampler : samplers_) {
    sampler->Stop();
    delete sampler;
  }
  samplers_.clear();

  info_.set_end_timestamp(timestamp);
  return true;
}

}  // namespace profiler
