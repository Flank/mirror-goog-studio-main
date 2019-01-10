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
#ifndef PERFD_EVENT_EVENT_PROFILER_COMPONENT_H_
#define PERFD_EVENT_EVENT_PROFILER_COMPONENT_H_

#include <string>

#include "daemon/profiler_component.h"
#include "perfd/event/event_service.h"
#include "perfd/event/internal_event_service.h"
#include "proto/common.grpc.pb.h"
#include "utils/clock.h"
#include "utils/process_manager.h"

namespace profiler {

class EventProfilerComponent final : public ProfilerComponent {
 public:
  explicit EventProfilerComponent(Clock* clock)
      : cache_(clock), public_service_(&cache_), internal_service_(&cache_) {}

  // Returns the service that talks to desktop clients (e.g., Studio).
  grpc::Service* GetPublicService() override { return &public_service_; }

  // Returns the service that talks to device clients (e.g., the agent).
  grpc::Service* GetInternalService() override { return &internal_service_; }

  void AgentStatusChangedCallback(int process_id) {
    // Check if the process is alive.
    std::string app_name = ProcessManager::GetCmdlineForPid(process_id);
    if (app_name.empty()) {
      // Process is not available.
      cache_.MarkActivitiesAsTerminated(process_id);
    }
  }

 private:
  EventCache cache_;
  EventServiceImpl public_service_;
  InternalEventServiceImpl internal_service_;
};

}  // namespace profiler

#endif  // PERFD_EVENT_EVENT_PROFILER_COMPONENT_H_
