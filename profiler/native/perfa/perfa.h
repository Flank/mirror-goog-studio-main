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
#ifndef PERFA_PERFA_H_
#define PERFA_PERFA_H_

#include <memory>
#include <thread>

#include <grpc++/grpc++.h>

#include "proto/internal_network.grpc.pb.h"
#include "proto/perfa_service.grpc.pb.h"

namespace profiler {

class Perfa {
 public:
  static void Initialize();
  // Grab the singleton instance of Perfa. This will initialize the class if
  // necessary, but consider calling |Initialize| on your own first.
  static Perfa& Instance();

  bool WriteData(const proto::ProfilerData& data);

  const proto::InternalNetworkService::Stub& network_stub() {
    return *network_stub_;
  }

 private:
  // Use Perfa::Initialize to initialize
  explicit Perfa(const char* address);
  ~Perfa() = delete;  // TODO: Support destroying perfa.

  std::unique_ptr<proto::PerfaService::Stub> service_stub_;
  std::unique_ptr<proto::InternalNetworkService::Stub> network_stub_;

  // TODO: Move this over to the StreamingRpcManager when it is ready
  std::thread control_thread_;
  grpc::ClientContext control_context_;
  std::unique_ptr<grpc::ClientReader<proto::PerfaControlRequest>>
      control_stream_;

  grpc::ClientContext data_context_;
  proto::DataStreamResponse data_response_;
  std::unique_ptr<grpc::ClientWriter<proto::ProfilerData>> data_stream_;

  void RunControlThread();
};

}  // end of namespace profiler

#endif  // PERFA_PEFA_H_
