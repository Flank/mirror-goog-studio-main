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

#include "trace_processor_service.h"

using grpc::ServerBuilder;
using profiler::perfetto::TraceProcessorServiceImpl;

void RunServer() {
  ServerBuilder builder;

  // Register the handler for TraceProcessorService.
  TraceProcessorServiceImpl service;
  builder.RegisterService(&service);

  // BuildAndStart() will modify this with the picked port.
  int port = 0;
  // Bind to to loopback only, as we will only communicate with localhost.
  // TODO: Add flag to configure port.
  std::string server_address("127.0.0.1:20204");

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

  RunServer();
  return 0;
}
