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
#include "perfd/commands/discover_profileable.h"
#include "perfd/commands/end_session.h"
#include "perfd/commands/get_cpu_core_config.h"
#include "perfd/common_profiler_component.h"
#include "perfd/cpu/commands/start_cpu_trace.h"
#include "perfd/cpu/commands/stop_cpu_trace.h"
#include "perfd/cpu/cpu_profiler_component.h"
#include "perfd/cpu/trace_manager.h"
#include "perfd/energy/energy_profiler_component.h"
#include "perfd/event/event_profiler_component.h"
#include "perfd/graphics/graphics_profiler_component.h"
#include "perfd/memory/commands/heap_dump.h"
#include "perfd/memory/commands/start_native_sample.h"
#include "perfd/memory/commands/stop_native_sample.h"
#include "perfd/memory/heap_dump_manager.h"
#include "perfd/memory/memory_profiler_component.h"
#include "perfd/memory/native_heap_manager.h"
#include "perfd/network/network_profiler_component.h"
#include "perfd/sessions/sessions_manager.h"
#include "utils/current_process.h"
#include "utils/daemon_config.h"
#include "utils/termination_service.h"
#include "utils/trace.h"

namespace profiler {

int Perfd::Initialize(Daemon* daemon) {
  Trace::Init();
  auto daemon_config = daemon->config()->GetConfig();

  auto* termination_service = TerminationService::Instance();

  // Intended to be shared between legacy and new cpu tracing pipelines.
  static TraceManager trace_manager(daemon->clock(), daemon_config.cpu(),
                                    termination_service);

  static HeapDumpManager heap_dumper(daemon->file_cache());
  static NativeHeapManager heap_sampler(daemon->file_cache(), *trace_manager.perfetto_manager());

  // Register Components
  daemon->RegisterProfilerComponent(std::unique_ptr<CommonProfilerComponent>(
      new CommonProfilerComponent(daemon)));

  daemon->RegisterProfilerComponent(std::unique_ptr<CpuProfilerComponent>(
      new CpuProfilerComponent(daemon->clock(), daemon->file_cache(),
                               daemon_config.cpu(), &trace_manager)));

  daemon->RegisterProfilerComponent(std::unique_ptr<MemoryProfilerComponent>(
      new MemoryProfilerComponent(daemon->clock(), &heap_dumper)));

  std::unique_ptr<EventProfilerComponent> event_component(
      new EventProfilerComponent(daemon->clock()));

  daemon->AddAgentStatusChangedCallback(
      std::bind(&EventProfilerComponent::AgentStatusChangedCallback,
                event_component.get(), std::placeholders::_1));
  daemon->RegisterProfilerComponent(std::move(event_component));

  daemon->RegisterProfilerComponent(
      std::unique_ptr<NetworkProfilerComponent>(new NetworkProfilerComponent(
          *(daemon->config()), daemon->clock(), daemon->file_cache())));

  if (daemon_config.common().energy_profiler_enabled()) {
    daemon->RegisterProfilerComponent(std::unique_ptr<EnergyProfilerComponent>(
        new EnergyProfilerComponent()));
  }

  daemon->RegisterProfilerComponent(std::unique_ptr<GraphicsProfilerComponent>(
      new GraphicsProfilerComponent(daemon->clock())));

  // Register Commands.
  daemon->RegisterCommandHandler(proto::Command::BEGIN_SESSION,
                                 &BeginSession::Create);
  daemon->RegisterCommandHandler(proto::Command::END_SESSION,
                                 &EndSession::Create);
  daemon->RegisterCommandHandler(proto::Command::DISCOVER_PROFILEABLE,
                                 &DiscoverProfileable::Create);
  daemon->RegisterCommandHandler(proto::Command::GET_CPU_CORE_CONFIG,
                                 &GetCpuCoreConfig::Create);
  daemon->RegisterCommandHandler(
      proto::Command::START_CPU_TRACE, [](proto::Command command) {
        return StartCpuTrace::Create(command, &trace_manager,
                                     SessionsManager::Instance());
      });
  daemon->RegisterCommandHandler(
      proto::Command::STOP_CPU_TRACE, [](proto::Command command) {
        return StopCpuTrace::Create(command, &trace_manager);
      });

  daemon->RegisterCommandHandler(
      proto::Command::HEAP_DUMP, [](proto::Command command) {
        return HeapDump::Create(command, &heap_dumper);
      });
  daemon->RegisterCommandHandler(
      proto::Command::START_NATIVE_HEAP_SAMPLE, [](proto::Command command) {
        return StartNativeSample::Create(command, &heap_sampler,
                                         SessionsManager::Instance());
      });
  daemon->RegisterCommandHandler(
      proto::Command::STOP_NATIVE_HEAP_SAMPLE, [](proto::Command command) {
        return StopNativeSample::Create(command, &heap_sampler);
      });

  return 0;
}

}  // namespace profiler