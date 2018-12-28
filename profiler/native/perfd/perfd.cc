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

#include <cstring>
#include "gflags/gflags.h"
#include "perfd/connector.h"
#include "perfd/cpu/cpu_profiler_component.h"
#include "perfd/daemon.h"
#include "perfd/energy/energy_profiler_component.h"
#include "perfd/event/event_profiler_component.h"
#include "perfd/generic_component.h"
#include "perfd/graphics/graphics_profiler_component.h"
#include "perfd/memory/memory_profiler_component.h"
#include "perfd/network/network_profiler_component.h"
#include "perfd/termination_service.h"
#include "utils/config.h"
#include "utils/current_process.h"
#include "utils/device_info.h"
#include "utils/file_cache.h"
#include "utils/fs/path.h"
#include "utils/socket_utils.h"
#include "utils/trace.h"

DEFINE_bool(experimental_pipeline, false, "Use unified pipeline");
DEFINE_bool(profiler_test, false, "Run profiler test");
DEFINE_string(config_file, profiler::kConfigFileDefaultPath,
              "Path to agent config file");
DEFINE_string(connect, "", "Communicate with an agent");

int main(int argc, char** argv) {
  gflags::ParseCommandLineFlags(&argc, &argv, true);

  // If directed by command line argument, establish a communication channel
  // with the agent which is running a Unix socket server and send the arguments
  // over.  When this argument is used, the program is usually invoked by from
  // GenericComponent's ProfilerServiceImpl::AttachAgent().
  if (!FLAGS_connect.empty()) {
    if (profiler::ConnectAndSendDataToPerfa(FLAGS_connect)) {
      return 0;
    } else {
      return -1;
    }
    // Note that in this case we should not initialize various profiler
    // components as the following code does. They create threads but the
    // associated thread objects might be destructed before the threads exit,
    // causing 'terminate called without an active exception' error.
  }

  profiler::Trace::Init();

  profiler::SteadyClock clock;
  profiler::Config config(FLAGS_config_file);
  profiler::EventBuffer buffer(&clock);
  profiler::FileCache file_cache(FLAGS_profiler_test
                                     ? getenv("TEST_TMPDIR")
                                     : profiler::CurrentProcess::dir());
  auto* termination_service = profiler::TerminationService::Instance();
  profiler::Daemon daemon(&clock, &config, &file_cache, &buffer);
  auto agent_config = daemon.config()->GetAgentConfig();

  profiler::GenericComponent generic_component(&daemon);
  daemon.RegisterComponent(&generic_component);

  profiler::CpuProfilerComponent cpu_component(
      &clock, &file_cache, agent_config.cpu_config(), termination_service);
  daemon.RegisterComponent(&cpu_component);

  profiler::MemoryProfilerComponent memory_component(&clock, &file_cache);
  daemon.RegisterComponent(&memory_component);

  profiler::EventProfilerComponent event_component(&clock);
  daemon.RegisterComponent(&event_component);
  generic_component.AddAgentStatusChangedCallback(
      std::bind(&profiler::EventProfilerComponent::AgentStatusChangedCallback,
                &event_component, std::placeholders::_1));

  profiler::NetworkProfilerComponent network_component(*(daemon.config()),
                                                       &clock, &file_cache);
  daemon.RegisterComponent(&network_component);

  profiler::EnergyProfilerComponent energy_component(&file_cache);
  if (agent_config.energy_profiler_enabled()) {
    daemon.RegisterComponent(&energy_component);
  }

  profiler::GraphicsProfilerComponent graphics_component(&clock);
  daemon.RegisterComponent(&graphics_component);

  if (profiler::DeviceInfo::feature_level() >= 26 &&
      agent_config.socket_type() == profiler::proto::ABSTRACT_SOCKET) {
    // For O and newer devices, use a Unix abstract socket.
    // Since we are building a gRPC server, we need a special prefix to inform
    // gRPC that this is a Unix socket name.
    std::string grpc_target{profiler::kGrpcUnixSocketAddrPrefix};
    grpc_target.append(agent_config.service_socket_name());

    daemon.RunServer(grpc_target);
  } else {
    // For legacy devices (Nougat or older), use an internet address.
    daemon.RunServer(agent_config.service_address());
  }

  return 0;
}
