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
#include <google/protobuf/empty.pb.h>
#include <grpc++/grpc++.h>
#include <future>
#include <mutex>
#include <thread>
#include "skia.grpc.pb.h"
#include "tree_building_canvas.h"

#ifndef SKIA_SERVER_H
#define SKIA_SERVER_H

class SkiaParserServiceImpl final
    : public ::layoutinspector::proto::SkiaParserService::Service {
 private:
  std::unique_ptr<::grpc::Server> server;
  std::promise<void> exit_requested;

  static void SplitOutImages(
      ::layoutinspector::proto::InspectorView* node,
      ::grpc::ServerReaderWriter<::layoutinspector::proto::GetViewTreeResponse,
                                 ::layoutinspector::proto::GetViewTreeRequest>*
          stream,
      int& id);

 public:
  ::grpc::Status Ping(::grpc::ServerContext* context,
                      const google::protobuf::Empty* request,
                      google::protobuf::Empty* response) override;

  ::grpc::Status Shutdown(::grpc::ServerContext* context,
                          const google::protobuf::Empty* request,
                          google::protobuf::Empty* response) override;

  ::grpc::Status GetViewTree(
      ::grpc::ServerContext* context,
      const ::layoutinspector::proto::GetViewTreeRequest* request,
      ::layoutinspector::proto::GetViewTreeResponse* response) override;

  ::grpc::Status GetViewTree2(
      ::grpc::ServerContext* context,
      ::grpc::ServerReaderWriter<::layoutinspector::proto::GetViewTreeResponse,
                                 ::layoutinspector::proto::GetViewTreeRequest>*
          stream) override;

  static void RunServer(const std::string &port) {
    std::string server_address("0.0.0.0:");
    server_address.append(port);

    ::grpc::ServerBuilder builder;
    builder.AddListeningPort(server_address,
                             ::grpc::InsecureServerCredentials());
    SkiaParserServiceImpl service;
    builder.RegisterService(&service);
    service.server = builder.BuildAndStart();
    bool exit_requested(false);
    std::thread shutdown_thread([&service, &exit_requested]() {
      service.exit_requested.get_future().wait();
      exit_requested = true;
      service.server->Shutdown(std::chrono::system_clock::now());
    });
    service.server->Wait();
    if (!exit_requested) {
      service.exit_requested.set_value();
    }
    shutdown_thread.join();
  }
};

#endif  // SKIA_SERVER_H
