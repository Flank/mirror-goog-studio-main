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
#include <mutex>
#include <thread>
#include "skia.grpc.pb.h"
#include "tree_building_canvas.h"

void SkiaParserServiceImpl::SplitOutImages(
    ::layoutinspector::proto::InspectorView* node,
    ::grpc::ServerReaderWriter<::layoutinspector::proto::GetViewTreeResponse,
                               ::layoutinspector::proto::GetViewTreeRequest>*
        stream,
    int& id) {
  if (!node->image().empty()) {
    ::layoutinspector::proto::GetViewTreeResponse response;
    response.set_allocated_image(node->release_image());
    response.set_image_id(++id);
    node->set_image_id(id);
    stream->Write(response);
  }
  for (::layoutinspector::proto::InspectorView& child :
       *node->mutable_children()) {
    SplitOutImages(&child, stream, id);
  }
}

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

// V1, no support for large messages. Used by studio prior to 2020.3.1
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

// Version with support for large messages
grpc::Status SkiaParserServiceImpl::GetViewTree2(
    ::grpc::ServerContext*,
    ::grpc::ServerReaderWriter<::layoutinspector::proto::GetViewTreeResponse,
                               ::layoutinspector::proto::GetViewTreeRequest>*
        stream) {
  ::layoutinspector::proto::GetViewTreeRequest request;
  int size = 0;
  int current_offset = 0;
  char* buffer = nullptr;

  while (stream->Read(&request)) {
    if (size == 0) {
      size = request.total_size();
      buffer = new char[size];
      current_offset = 0;
    }
    if (request.skp().length() + current_offset > size) {
      return ::grpc::Status(
          ::grpc::StatusCode::OUT_OF_RANGE,
          "Expected skp size was " + std::to_string(size) +
              " but message was at least " +
              std::to_string(current_offset + request.skp().length()));
    }
    memcpy(buffer + current_offset, request.skp().data(),
           request.skp().length());

    current_offset += request.skp().length();
  }
  if (current_offset == size) {
    ::layoutinspector::proto::GetViewTreeResponse response;
    v1::TreeBuildingCanvas::ParsePicture(
        buffer, size, request.version(), &request.requested_nodes(),
        request.scale() == 0 ? 1 : request.scale(), response.mutable_root());
    if (request.version() >= 2) {
      int image_id = 0;
      SplitOutImages(response.mutable_root(), stream, image_id);
    }
    stream->Write(response);
    return ::grpc::Status::OK;
  } else {
    return ::grpc::Status(::grpc::StatusCode::ABORTED,
                          "Expected skp size was " + std::to_string(size) +
                              " but message ended at " +
                              std::to_string(current_offset));
  }
}

int main(int argc, char* argv[]) {
  if (argc != 2) {
    std::cerr << "usage: SkiaParserServer <port>" << std::endl;
    return 1;
  }

  SkiaParserServiceImpl::RunServer(std::string(argv[1]));
  return 0;
}
