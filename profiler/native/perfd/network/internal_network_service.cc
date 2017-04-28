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
    Daemon::Utilities *utilities, NetworkCache *network_cache)
    : file_cache_(*utilities->file_cache()), network_cache_(*network_cache) {}

Status InternalNetworkServiceImpl::RegisterHttpData(
    ServerContext *context, const proto::HttpDataRequest *httpData,
    proto::EmptyNetworkReply *reply) {
  auto details =
      network_cache_.AddConnection(httpData->conn_id(), httpData->process_id(),
                                   httpData->start_timestamp());
  details->request.url = httpData->url();
  details->request.trace = httpData->trace();
  return Status::OK;
}

Status InternalNetworkServiceImpl::SendChunk(ServerContext *context,
                                             const proto::ChunkRequest *chunk,
                                             proto::EmptyNetworkReply *reply) {
  std::stringstream filename;
  filename << chunk->conn_id();

  file_cache_.AddChunk(filename.str(), chunk->content());
  return Status::OK;
}

Status InternalNetworkServiceImpl::SendHttpEvent(
    ServerContext *context, const proto::HttpEventRequest *httpEvent,
    proto::EmptyNetworkReply *reply) {
  switch (httpEvent->event()) {
    case proto::HttpEventRequest::CREATED: {
      // TODO: Handle this case for now to avoid printing an error message to
      // the user. We should probably remove this case later as it is already
      // handled by |RegisterHttpData|.
    }

    break;

    case proto::HttpEventRequest::DOWNLOAD_STARTED: {
      auto details = network_cache_.GetDetails(httpEvent->conn_id());
      if (details != nullptr) {
        details->downloading_timestamp = httpEvent->timestamp();
      }
    }

    break;

    case proto::HttpEventRequest::DOWNLOAD_COMPLETED: {
      // Since the download is finished, move from partial to complete
      // TODO: Name the dest file based on a hash of the contents. For now, we
      // don't have a hash function, so just keep the name.
      std::stringstream filename;
      filename << httpEvent->conn_id();
      auto payload_file = file_cache_.Complete(filename.str());

      auto details = network_cache_.GetDetails(httpEvent->conn_id());
      if (details != nullptr) {
        details->response.payload_id = payload_file->name();
        details->end_timestamp = httpEvent->timestamp();
      }
    }

    break;

    case proto::HttpEventRequest::ABORTED: {
      std::stringstream filename;
      filename << httpEvent->conn_id();

      file_cache_.Abort(filename.str());

      auto details = network_cache_.GetDetails(httpEvent->conn_id());
      if (details != nullptr) {
        details->end_timestamp = httpEvent->timestamp();
      }
      // TODO: Somehow mark the connection as aborted?
    } break;

    default:
      Log::V("Unhandled http event (%d)", httpEvent->event());
  }

  return Status::OK;
}

Status InternalNetworkServiceImpl::SendHttpRequest(
    ServerContext *context,
    const proto::HttpRequestRequest *httpRequest,
    proto::EmptyNetworkReply *reply) {
  ConnectionDetails* conn = network_cache_.GetDetails(httpRequest->conn_id());
  if (conn != nullptr) {
    conn->request.fields = httpRequest->fields();
    conn->request.method = httpRequest->method();
  }
  else {
    Log::V("Unhandled http request (%lld)", (long long) httpRequest->conn_id());
  }
  return Status::OK;
}

Status InternalNetworkServiceImpl::SendHttpResponse(
    ServerContext *context, const proto::HttpResponseRequest *httpResponse,
    proto::EmptyNetworkReply *reply) {
  ConnectionDetails *conn = network_cache_.GetDetails(httpResponse->conn_id());
  if (conn != nullptr) {
    conn->response.fields = httpResponse->fields();
  } else {
    Log::V("Unhandled http response (%lld)", (long long) httpResponse->conn_id());
  }
  return Status::OK;
}

Status InternalNetworkServiceImpl::TrackThread(
    ServerContext *context, const proto::JavaThreadRequest *threadData,
    proto::EmptyNetworkReply *reply) {
  ConnectionDetails* conn = network_cache_.GetDetails(threadData->conn_id());
  if (conn != nullptr) {
    bool found = false;
    for (auto thread: conn->threads) {
      if (thread.id == threadData->thread().id()) {
        found = true;
        break;
      }
    }
    if (!found) {
      conn->threads.emplace_back(threadData->thread().id(), threadData->thread().name());
    }
  }
  return Status::OK;
}

}  // namespace profiler
