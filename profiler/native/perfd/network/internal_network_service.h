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

#include "proto/internal_network.grpc.pb.h"

#include "perfd/daemon.h"
#include "perfd/network/network_cache.h"

namespace profiler {

class InternalNetworkServiceImpl final
    : public proto::InternalNetworkService::Service {
 public:
  InternalNetworkServiceImpl(Daemon::Utilities *utilities,
                             NetworkCache *network_cache);

  grpc::Status RegisterHttpData(grpc::ServerContext *context,
                                const proto::HttpDataRequest *httpData,
                                proto::EmptyNetworkReply *reply) override;

  grpc::Status SendChunk(grpc::ServerContext *context,
                         const proto::ChunkRequest *chunk,
                         proto::EmptyNetworkReply *reply) override;

  grpc::Status SendHttpEvent(grpc::ServerContext *context,
                             const proto::HttpEventRequest *httpEvent,
                             proto::EmptyNetworkReply *reply) override;

  grpc::Status SendHttpRequest(grpc::ServerContext *context,
                               const proto::HttpRequestRequest *httpRequest,
                               proto::EmptyNetworkReply *reply) override;

  grpc::Status SendHttpResponse(grpc::ServerContext *context,
                                const proto::HttpResponseRequest *httpResponse,
                                proto::EmptyNetworkReply *reply) override;

  grpc::Status TrackThread(grpc::ServerContext *context,
                           const proto::JavaThreadRequest *thread,
                           proto::EmptyNetworkReply *reply) override;

 private:
  FileCache &file_cache_;
  NetworkCache &network_cache_;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_INTERNAL_NETWORK_SERVICE_H_
