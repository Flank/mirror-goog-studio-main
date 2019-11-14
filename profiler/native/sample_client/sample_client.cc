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

#include <grpc++/grpc++.h>
#include <cstring>
#include <sstream>

#include "gflags/gflags.h"
#include "proto/commands.pb.h"
#include "proto/common.pb.h"
#include "proto/transport.grpc.pb.h"

// This is a sample client that connects to the daemon running on the device
// which collects profiler data. For demonstration purpose, this client starts a
// profiler session and then prints out memory data it receives.
//
// clang-format off
//
// ===== Example Usage =====
//
// On device, start the daemon
//  /data/local/tmp/perfd/transport -config_file=/data/local/tmp/perfd/daemon.config
// On host
//  adb forward tcp:2019 localabstract:AndroidStudioTransport
//  ~/repos/studio-master-dev/bazel-bin/tools/base/profiler/native/sample_client/sample_client --port 2019 --pid <PID>
//
// clang-format on

using grpc::Channel;
using grpc::ClientContext;
using grpc::Status;
using profiler::proto::Command;
using profiler::proto::Event;
using profiler::proto::TransportService;

DEFINE_int32(port, 0, "Host port to connect to the device");
DEFINE_int32(pid, 0, "PID of the process to profile");

namespace profiler {

class SampleClient {
 public:
  SampleClient(std::shared_ptr<Channel> channel, int pid)
      : stub_(TransportService::NewStub(channel)), pid_(pid) {}

  void StartMemoryProfiling() {
    StartProfilerSession();
    ReceiveEvent();
  }

 private:
  void StartProfilerSession() {
    ClientContext context;
    proto::ExecuteRequest request;
    auto* command = request.mutable_command();
    command->set_type(Command::BEGIN_SESSION);
    command->set_pid(pid_);
    proto::ExecuteResponse response;
    stub_->Execute(&context, request, &response);
  }

  void ReceiveEvent() {
    while (true) {
      proto::GetEventsRequest request;
      ClientContext context;
      std::unique_ptr<grpc::ClientReader<Event>> reader{
          stub_->GetEvents(&context, request)};
      Event event;
      while (reader->Read(&event)) {
        if (event.kind() == Event::MEMORY_USAGE) {
          const auto& usage = event.memory_usage();
          std::cout << "[TimeNs " << event.timestamp()
                    << "] java:" << usage.java_mem()
                    << " native:" << usage.native_mem()
                    << " stack:" << usage.stack_mem()
                    << " graphics:" << usage.graphics_mem()
                    << " code:" << usage.code_mem()
                    << " others:" << usage.others_mem()
                    << " total:" << usage.total_mem() << std::endl;
        }
      }
      Status status = reader->Finish();
    }
  }

  std::unique_ptr<TransportService::Stub> stub_;
  int pid_;
};

}  // namespace profiler

int main(int argc, char** argv) {
  gflags::ParseCommandLineFlags(&argc, &argv, true);
  if (FLAGS_port == 0) {
    printf("--port is required\n");
    exit(1);
  }
  if (FLAGS_pid == 0) {
    printf("--pid is required\n");
    exit(1);
  }

  std::ostringstream oss;
  oss << "localhost:" << FLAGS_port;
  profiler::SampleClient client(
      grpc::CreateChannel(oss.str(), grpc::InsecureChannelCredentials()),
      FLAGS_pid);
  client.StartMemoryProfiling();
  return 0;
}
