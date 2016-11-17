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
#include "internal_network_service.h"

#include "utils/log.h"

namespace profiler {

using grpc::ServerContext;
using grpc::Status;

InternalNetworkServiceImpl::InternalNetworkServiceImpl(
    NetworkCache *network_cache)
    : network_cache_(*network_cache) {
}

Status InternalNetworkServiceImpl::RegisterHttpData(
    ServerContext *context, const proto::HttpDataRequest *httpData,
    proto::EmptyNetworkReply *reply) {
  auto details =
      network_cache_.AddConnection(httpData->conn_id(), httpData->app_id());
  details->request.url = httpData->url();
  return Status::OK;
}

Status InternalNetworkServiceImpl::SendChunk(ServerContext *context,
                                             const proto::ChunkRequest *chunk,
                                             proto::EmptyNetworkReply *reply) {
  std::stringstream filename;
  filename << chunk->conn_id();

  network_cache_.AddPayloadChunk(filename.str(), chunk->content());
  return Status::OK;
}

Status InternalNetworkServiceImpl::SendHttpEvent(
    ServerContext *context, const proto::HttpEventRequest *httpEvent,
    proto::EmptyNetworkReply *reply) {
  switch (httpEvent->event()) {
    case proto::HttpEventRequest::DOWNLOAD_STARTED: {
      auto details = network_cache_.GetDetails(httpEvent->conn_id());
      details->downloading_timestamp = httpEvent->timestamp();
    }

    break;

    case proto::HttpEventRequest::DOWNLOAD_COMPLETED: {
      // Since the download is finished, move from partial to complete
      // TODO: Name the dest file based on a hash of the contents. For now, we
      // don't have a hash function, so just keep the name.
      std::stringstream filename;
      filename << httpEvent->conn_id();
      auto payload_file = network_cache_.FinishPayload(filename.str());

      auto details = network_cache_.GetDetails(httpEvent->conn_id());
      details->response.payload_id = payload_file->name();
      details->end_timestamp = httpEvent->timestamp();
    }

    break;

    case proto::HttpEventRequest::ABORTED: {
      std::stringstream filename;
      filename << httpEvent->conn_id();

      network_cache_.AbortPayload(filename.str());

      auto details = network_cache_.GetDetails(httpEvent->conn_id());
      details->end_timestamp = httpEvent->timestamp();
      // TODO: Somehow mark the connection as aborted?
    } break;

    default:
      Log::V("Unhandled http event (%d)", httpEvent->event());
  }

  return Status::OK;
}

Status InternalNetworkServiceImpl::SendHttpResponse(
    ServerContext *context,
    const proto::HttpResponseRequest *httpResponse,
    proto::EmptyNetworkReply *reply) {
  ConnectionDetails* conn = network_cache_.GetDetails(httpResponse->conn_id());
  if (conn != nullptr) {
    conn->response.fields = httpResponse->fields();
  }
  else {
    Log::V("Unhandled http response (%ld)", (long) httpResponse->conn_id());
  }
  return Status::OK;
}

}  // namespace profiler
