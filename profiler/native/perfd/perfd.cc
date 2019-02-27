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

#include "perfd/perfd.h"
#include <cstring>
#include "daemon/daemon.h"
#include "perfd/commands/begin_session.h"
#include "perfd/commands/end_session.h"
#include "perfd/common_profiler_component.h"
#include "perfd/cpu/cpu_profiler_component.h"
#include "perfd/energy/energy_profiler_component.h"
#include "perfd/event/event_profiler_component.h"
#include "perfd/graphics/graphics_profiler_component.h"
#include "perfd/memory/memory_profiler_component.h"
#include "perfd/network/network_profiler_component.h"
#include "utils/config.h"
#include "utils/current_process.h"
#include "utils/termination_service.h"
#include "utils/trace.h"

namespace profiler {

int Perfd::Initialize(Daemon* daemon) {
  Trace::Init();
  auto agent_config = daemon->config()->GetAgentConfig();

  auto* termination_service = TerminationService::Instance();

  // Register Components
  daemon->RegisterProfilerComponent(std::unique_ptr<CommonProfilerComponent>(
      new CommonProfilerComponent(daemon)));

  daemon->RegisterProfilerComponent(
      std::unique_ptr<CpuProfilerComponent>(new CpuProfilerComponent(
          daemon->clock(), daemon->file_cache(), agent_config.cpu_config(),
          termination_service)));

  daemon->RegisterProfilerComponent(std::unique_ptr<MemoryProfilerComponent>(
      new MemoryProfilerComponent(daemon->clock(), daemon->file_cache())));

  std::unique_ptr<EventProfilerComponent> event_component(
      new EventProfilerComponent(daemon->clock()));

  daemon->AddAgentStatusChangedCallback(
      std::bind(&EventProfilerComponent::AgentStatusChangedCallback,
                event_component.get(), std::placeholders::_1));
  daemon->RegisterProfilerComponent(std::move(event_component));

  daemon->RegisterProfilerComponent(
      std::unique_ptr<NetworkProfilerComponent>(new NetworkProfilerComponent(
          *(daemon->config()), daemon->clock(), daemon->file_cache())));

  if (agent_config.energy_profiler_enabled()) {
    daemon->RegisterProfilerComponent(std::unique_ptr<EnergyProfilerComponent>(
        new EnergyProfilerComponent(daemon->file_cache())));
  }

  daemon->RegisterProfilerComponent(std::unique_ptr<GraphicsProfilerComponent>(
      new GraphicsProfilerComponent(daemon->clock())));

  // Register Commands.
  daemon->RegisterCommandHandler(proto::Command::BEGIN_SESSION,
                                 &BeginSession::Create);
  daemon->RegisterCommandHandler(proto::Command::END_SESSION,
                                 &EndSession::Create);

  return 0;
}

}  // namespace profiler