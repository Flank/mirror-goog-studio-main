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
#include "server.h"
#include <grpc++/grpc++.h>
#include <future>
#include <mutex>
#include <thread>
#include "skia.grpc.pb.h"
#include "tree_building_canvas.h"

::grpc::Status SkiaParserServiceImpl::Ping(::grpc::ServerContext*,
                                           const google::protobuf::Empty*,
                                           google::protobuf::Empty*) {
  return ::grpc::Status::OK;
}

::grpc::Status SkiaParserServiceImpl::Shutdown(::grpc::ServerContext*,
                                               const google::protobuf::Empty*,
                                               google::protobuf::Empty*) {
  exit_requested.set_value();
  return ::grpc::Status::OK;
}

::grpc::Status SkiaParserServiceImpl::GetViewTree(
    ::grpc::ServerContext*,
    const ::layoutinspector::proto::GetViewTreeRequest* request,
    ::layoutinspector::proto::GetViewTreeResponse* response) {
  v1::TreeBuildingCanvas::ParsePicture(
      request->skp().data(), request->skp().length(), request->version(),
      &request->requested_nodes(), request->scale() == 0 ? 1 : request->scale(),
      response->mutable_root());
  return ::grpc::Status::OK;
}

int main(int argc, char* argv[]) {
  if (argc != 2) {
    std::cerr << "usage: SkiaParserServer <port>" << std::endl;
    return 1;
  }

  SkiaParserServiceImpl::RunServer(std::string(argv[1]));
  return 0;
}
