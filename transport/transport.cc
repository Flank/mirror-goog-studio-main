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
#include "daemon/connector.h"
#include "daemon/daemon.h"
#include "gflags/gflags.h"
#include "perfd/perfd.h"
#include "proto/common.pb.h"
#include "utils/config.h"
#include "utils/current_process.h"
#include "utils/device_info.h"
#include "utils/file_cache.h"
#include "utils/fs/path.h"
#include "utils/log.h"
#include "utils/socket_utils.h"
#include "utils/trace.h"

DEFINE_bool(experimental_pipeline, false, "Use unified pipeline");
DEFINE_bool(profiler_test, false, "Run profiler test");
DEFINE_string(config_file, profiler::kConfigFileDefaultPath,
              "Path to agent config file");
DEFINE_string(connect, "", "Communicate with an agent");

void RegisterTransports(profiler::Daemon* daemon) {
  if (profiler::Perfd::Initialize(daemon) != 0) {
    profiler::Log::E("Failed to initialize perfd");
  }
}
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
    // Note that in this case we should not initialize various
    // components as the following code does. They create threads but the
    // associated thread objects might be destructed before the threads exit,
    // causing 'terminate called without an active exception' error.
  }
  profiler::SteadyClock clock;
  profiler::Config config(FLAGS_config_file);
  profiler::EventBuffer buffer(&clock);
  profiler::FileCache file_cache(FLAGS_profiler_test
                                     ? getenv("TEST_TMPDIR")
                                     : profiler::CurrentProcess::dir());
  profiler::Daemon daemon(&clock, &config, &file_cache, &buffer);

  RegisterTransports(&daemon);

  auto agent_config = daemon.config()->GetAgentConfig();
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
