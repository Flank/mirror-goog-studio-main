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
#include <future>
#include <mutex>
#include <thread>
#include "skia.grpc.pb.h"
#include "tree_building_canvas.h"

class SkiaParserServiceImpl final
    : public ::layoutinspector::proto::SkiaParserService::Service {
 private:
  std::unique_ptr<::grpc::Server> server;
  std::promise<void> exit_requested;

 public:
  ::grpc::Status GetViewTree(
      ::grpc::ServerContext* context,
      const ::layoutinspector::proto::GetViewTreeRequest* request,
      ::layoutinspector::proto::GetViewTreeResponse* response) override {
    v1::TreeBuildingCanvas::ParsePicture(
        request->skp().data(), request->skp().length(), request->version(),
        &request->requested_nodes(),
        request->scale() == 0 ? 1 : request->scale(), response->mutable_root());
    return ::grpc::Status::OK;
  }

  ::grpc::Status Ping(::grpc::ServerContext*, const google::protobuf::Empty*,
                      google::protobuf::Empty*) override {
    return ::grpc::Status::OK;
  }

  ::grpc::Status Shutdown(::grpc::ServerContext* context,
                          const google::protobuf::Empty* request,
                          google::protobuf::Empty* response) override {
    exit_requested.set_value();
    return ::grpc::Status::OK;
  }

  static void RunServer(const std::string &port) {
    std::string server_address("0.0.0.0:");
    server_address.append(port);

    ::grpc::ServerBuilder builder;
    builder.AddListeningPort(server_address,
                             ::grpc::InsecureServerCredentials());
    SkiaParserServiceImpl service;
    builder.RegisterService(&service);
    service.server = builder.BuildAndStart();
    std::thread shutdown_thread([&service]() {
      service.exit_requested.get_future().wait();
      service.server->Shutdown(std::chrono::system_clock::now());;
    });
    service.server->Wait();
    service.exit_requested.set_value();
    shutdown_thread.join();
  }
};

int main(int argc, char* argv[]) {
  if (argc != 2) {
    std::cerr << "usage: SkiaParserServer <port>" << std::endl;
    return 1;
  }

  SkiaParserServiceImpl::RunServer(std::string(argv[1]));
  return 0;
}
