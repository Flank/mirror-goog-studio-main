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
#include "agent_status_sampler.h"
#include "daemon/event_buffer.h"
#include "proto/common.pb.h"

namespace profiler {

using proto::Event;

void AgentStatusSampler::Sample() {
  // GetAgentStatus will behave in one of two ways,
  // 1) If a process is unattachable the GetAgentStatus will always return
  // UNATTACHABLE.
  // 2) If a process is attachable the GetAgentStatus will return
  // UNSPECIFIED until an agent is attached upon attaching ATTACHED will be
  // returned and GetAgentStatus will only ever return ATTACHED for that
  // process.
  int32_t pid = session().info().pid();
  auto updated_status = daemon_->GetAgentStatus(pid);
  if (updated_status != last_agent_status_) {
    Event event;
    event.set_pid(pid);
    event.set_kind(Event::AGENT);
    auto status = event.mutable_agent_data();
    status->set_status(updated_status);
    buffer()->Add(event);
    last_agent_status_ = updated_status;
  }
}

}  // namespace profiler