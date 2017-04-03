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
#include "generic_component.h"

#include <unistd.h>

#include "utils/clock.h"
#include "utils/thread_name.h"

namespace profiler {

GenericComponent::GenericComponent(Daemon::Utilities* utilities)
    : generic_public_service_(utilities, &heartbeat_timestamp_map_),
      agent_service_(utilities->clock(), &heartbeat_timestamp_map_),
      clock_(utilities->clock()) {
  status_thread_ = std::thread(&GenericComponent::RunAgentStatusThread, this);
}

void GenericComponent::RunAgentStatusThread() {
  SetThreadName("AgentStatus");
  while (true) {
    int64_t current_time = clock_.GetCurrentTime();
    for (auto map : heartbeat_timestamp_map_) {
      proto::AgentStatusResponse::Status status =
          kHeartbeatThresholdNs > (current_time - map.second)
              ? proto::AgentStatusResponse::ATTACHED
              : proto::AgentStatusResponse::DETACHED;
      auto got = heartbeat_status_map_.find(map.first);
      if (got == heartbeat_status_map_.end() || got->second != status) {
        for (auto callback : agent_status_changed_callbacks_) {
          callback(map.first, status);
        }
      }
      heartbeat_status_map_[map.first] = status;
    }
    usleep(Clock::ns_to_us(kHeartbeatThresholdNs));
  }
}
}