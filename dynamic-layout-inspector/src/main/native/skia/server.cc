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
#include "skia.grpc.pb.h"
#include "tree_building_canvas.h"

using std::cerr;
using std::endl;
using namespace ::layoutinspector::proto;

class SkiaParserServiceImpl final : public SkiaParserService::Service {
  ::grpc::Status GetViewTree(::grpc::ServerContext* context,
                             const GetViewTreeRequest* request,
                             GetViewTreeResponse* response) override {
    TreeBuildingCanvas::ParsePicture(request->skp().data(),
                                     request->skp().length(),
                                     response->mutable_root());
    return ::grpc::Status::OK;
  }
};

void RunServer(char* port) {
  std::string server_address("0.0.0.0:");
  server_address.append(port);
  SkiaParserServiceImpl service;

  ::grpc::ServerBuilder builder;
  builder.AddListeningPort(server_address, ::grpc::InsecureServerCredentials());
  builder.RegisterService(&service);
  std::unique_ptr<::grpc::Server> server(builder.BuildAndStart());
  server->Wait();
}

int main(int argc, char* argv[]) {
  if (argc != 2) {
    cerr << "usage: SkiaParserServer <port>" << endl;
    return 1;
  }

  RunServer(argv[1]);
  return 0;
}
