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
#ifndef PERFD_NETWORK_INTERNAL_NETWORK_SERVICE_H_
#define PERFD_NETWORK_INTERNAL_NETWORK_SERVICE_H_

#include <grpc++/grpc++.h>
#include <string>

#include "proto/internal_network.grpc.pb.h"

namespace profiler {

class InternalNetworkServiceImpl final
    : public proto::InternalNetworkService::Service {
 public:
  explicit InternalNetworkServiceImpl(const std::string &root_path);
  ~InternalNetworkServiceImpl() override = default;

  grpc::Status RegisterHttpData(grpc::ServerContext *context,
                                const proto::HttpDataRequest *httpData,
                                proto::EmptyNetworkReply *reply) override;

  grpc::Status SendChunk(grpc::ServerContext *context,
                         const proto::ChunkRequest *chunk,
                         proto::EmptyNetworkReply *reply) override;

  grpc::Status SendHttpEvent(grpc::ServerContext *context,
                             const proto::HttpEventRequest *httpEvent,
                             proto::EmptyNetworkReply *reply) override;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_INTERNAL_NETWORK_SERVICE_H_
