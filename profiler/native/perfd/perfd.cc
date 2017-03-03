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

#include "perfd/cpu/cpu_profiler_component.h"
#include "perfd/daemon.h"
#include "perfd/event/event_profiler_component.h"
#include "perfd/generic_component.h"
#include "perfd/memory/memory_profiler_component.h"
#include "perfd/network/network_profiler_component.h"
#include "utils/config.h"
#include "utils/current_process.h"
#include "utils/file_cache.h"
#include "utils/fs/path.h"
#include "utils/trace.h"

#include <string>

int main(int argc, char** argv) {
  using std::string;

  profiler::Trace::Init();
  profiler::Daemon daemon;

  profiler::GenericComponent generic_component{&daemon.utilities()};
  daemon.RegisterComponent(&generic_component);

  profiler::CpuProfilerComponent cpu_component{&daemon.utilities()};
  daemon.RegisterComponent(&cpu_component);

  profiler::MemoryProfilerComponent memory_component{&daemon.utilities()};
  daemon.RegisterComponent(&memory_component);

  profiler::EventProfilerComponent event_component{daemon.utilities()};
  daemon.RegisterComponent(&event_component);
  generic_component.AddAgentStatusChangedCallback(std::bind(
      &profiler::EventProfilerComponent::AgentStatusChangedCallback,
      &event_component, std::placeholders::_1, std::placeholders::_2));

  profiler::NetworkProfilerComponent network_component{&daemon.utilities()};
  daemon.RegisterComponent(&network_component);

  daemon.RunServer(profiler::kServerAddress);
  return 0;
}
