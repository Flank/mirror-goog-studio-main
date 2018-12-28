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

GenericComponent::GenericComponent(Daemon* daemon)
    : daemon_(daemon), generic_public_service_(daemon), agent_service_(daemon) {
  status_thread_ = std::thread(&GenericComponent::RunAgentStatusThread, this);
}

void GenericComponent::RunAgentStatusThread() {
  SetThreadName("AgentStatus");
  while (true) {
    int64_t current_time = daemon_->clock()->GetCurrentTime();
    for (auto map : daemon_->heartbeat_timestamp_map()) {
      // If we have a heartbeat then we attached the agent once as such we
      // update the status.
      proto::AgentData::Status status = proto::AgentData::ATTACHED;
      auto got = daemon_->agent_status_map().find(map.first);
      // Call the callback if our heartbeat timeouts or its the first time we
      // see the process.
      if (got == daemon_->agent_status_map().end() ||
          kHeartbeatThresholdNs > (current_time - map.second)) {
        for (auto callback : agent_status_changed_callbacks_) {
          callback(map.first);
        }
      }
      daemon_->agent_status_map()[map.first] = status;
    }
    usleep(Clock::ns_to_us(kHeartbeatThresholdNs));
  }
}
}  // namespace profiler
