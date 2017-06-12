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
#include "perfd/connector.h"
#include "perfd/cpu/cpu_profiler_component.h"
#include "perfd/daemon.h"
#include "perfd/event/event_profiler_component.h"
#include "perfd/generic_component.h"
#include "perfd/graphics/graphics_profiler_component.h"
#include "perfd/memory/memory_profiler_component.h"
#include "perfd/network/network_profiler_component.h"
#include "utils/config.h"
#include "utils/device_info.h"
#include "utils/socket_utils.h"
#include "utils/trace.h"

int main(int argc, char** argv) {
  // If directed by command line argument, establish a communication channel
  // with the agent which is running a Unix socket server and send the arguments
  // over.  When this argument is used, the program is usually invoked by from
  // GenericComponent's ProfilerServiceImpl::AttachAgent().
  if (argc >= 3 &&
      strncmp(argv[1], profiler::kConnectCmdLineArg,
              strlen(profiler::kConnectCmdLineArg)) == 0) {
    if (profiler::ConnectAndSendDataToPerfa(argv[1], argv[2])) {
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

  profiler::GraphicsProfilerComponent graphics_component{&daemon.utilities()};
  daemon.RegisterComponent(&graphics_component);

  auto agent_config = profiler::Config::Instance().GetAgentConfig();
  if (profiler::DeviceInfo::feature_level() >= 26 &&
      // TODO: remove the check on argument after agent uses only JVMTI to
      // instrument bytecode on O+ devices.
      agent_config.use_jvmti()) {
    // For O and newer devices, use a Unix abstract socket.
    // Since we are building a gRPC server, we need a special prefix to inform
    // gRPC that this is a Unix socket name.
    std::string grpc_target{profiler::kGrpcUnixSocketAddrPrefix};
    grpc_target.append(profiler::kDaemonSocketName);
    daemon.RunServer(grpc_target);
  } else {
    // For legacy devices (Nougat or older), use an internet address.
    daemon.RunServer(profiler::kServerAddress);
  }
  return 0;
}