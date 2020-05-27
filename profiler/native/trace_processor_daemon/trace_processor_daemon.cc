/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "absl/flags/flag.h"
#include "absl/flags/parse.h"
#include "trace_processor_service.h"

using grpc::ServerBuilder;
using profiler::perfetto::TraceProcessorServiceImpl;

ABSL_FLAG(uint16_t, port, 20204, "Port to open the gRPC server");

void RunServer() {
  ServerBuilder builder;

  // Register the handler for TraceProcessorService.
  TraceProcessorServiceImpl service;
  builder.RegisterService(&service);

  auto port_flag = absl::GetFlag(FLAGS_port);
  // Bind to to loopback only, as we will only communicate with localhost.
  std::string server_address("127.0.0.1:" + std::to_string(port_flag));

  // BuildAndStart() will modify this with the picked port.
  int port = 0;
  builder.AddListeningPort(server_address, grpc::InsecureServerCredentials(),
                           &port);
  std::unique_ptr<grpc::Server> server(builder.BuildAndStart());
  if (port == 0) {
    // The port wasn't successfully bound to the server by BuildAndStart().
    std::cout << "Server failed to start. A port number wasn't bound."
              << std::endl;
    exit(EXIT_FAILURE);
  }

  std::cout << "Server listening on " << server_address << std::endl;
  server->Wait();
}

int main(int argc, char** argv) {
  GOOGLE_PROTOBUF_VERIFY_VERSION;
  absl::ParseCommandLine(argc, argv);

  RunServer();
  return 0;
}
