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
using profiler::proto::ChunkRequest;

InternalNetworkServiceImpl::InternalNetworkServiceImpl(
    FileCache *file_cache, NetworkCache *network_cache)
    : file_cache_(file_cache), network_cache_(network_cache) {}

Status InternalNetworkServiceImpl::SendChunk(ServerContext *context,
                                             const proto::ChunkRequest *chunk,
                                             proto::EmptyNetworkReply *reply) {
  auto &filename = GetPayloadFileName(chunk->conn_id(),
                                      chunk->type() == ChunkRequest::REQUEST);
  file_cache_->AddChunk(filename, chunk->content());
  return Status::OK;
}

// Since the download is finished, move from partial to complete
// TODO: Name the dest file based on a hash of the contents. For now, we
// don't have a hash function, so just keep the name.
const std::string InternalNetworkServiceImpl::GetPayloadFileName(
    int64_t id, bool isRequestPayload) {
  std::stringstream filename;
  filename << id;
  if (isRequestPayload) {
    filename << "_request";
  }
  return filename.str();
}

Status InternalNetworkServiceImpl::SendHttpEvent(
    ServerContext *context, const proto::HttpEventRequest *httpEvent,
    proto::EmptyNetworkReply *reply) {
  switch (httpEvent->event()) {
    case proto::HttpEventRequest::DOWNLOAD_STARTED: {
      auto details = network_cache_->GetDetails(httpEvent->conn_id());
      if (details != nullptr) {
        details->downloading_timestamp = httpEvent->timestamp();
      }
    }

    break;

    case proto::HttpEventRequest::DOWNLOAD_COMPLETED: {
      auto &filename = GetPayloadFileName(httpEvent->conn_id(), false);
      auto payload_file = file_cache_->Complete(filename);

      auto details = network_cache_->GetDetails(httpEvent->conn_id());
      if (details != nullptr) {
        details->response.payload_id = payload_file->name();
        details->response.payload_size = payload_file->size();
        details->end_timestamp = httpEvent->timestamp();
      }
    }

    break;

    case proto::HttpEventRequest::UPLOAD_COMPLETED: {
      auto &filename = GetPayloadFileName(httpEvent->conn_id(), true);
      auto payload_file = file_cache_->Complete(filename);
      auto details = network_cache_->GetDetails(httpEvent->conn_id());
      if (details != nullptr) {
        details->request.payload_id = payload_file->name();
        details->uploaded_timestamp = httpEvent->timestamp();
      }
    }

    break;

    case proto::HttpEventRequest::ABORTED: {
      auto &filename = GetPayloadFileName(httpEvent->conn_id(), false);
      file_cache_->Abort(filename);

      auto details = network_cache_->GetDetails(httpEvent->conn_id());
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
    ServerContext *context, const proto::HttpRequestRequest *httpRequest,
    proto::EmptyNetworkReply *reply) {
  auto details = network_cache_->AddConnection(httpRequest->conn_id(), httpRequest->pid(), httpRequest->start_timestamp());
  details->request.url = httpRequest->url();
  details->request.trace_id = file_cache_->AddString(httpRequest->trace());
  details->request.fields = httpRequest->fields();
  details->request.method = httpRequest->method();
  return Status::OK;
}

Status InternalNetworkServiceImpl::SendHttpResponse(
    ServerContext *context, const proto::HttpResponseRequest *httpResponse,
    proto::EmptyNetworkReply *reply) {
  ConnectionDetails *conn = network_cache_->GetDetails(httpResponse->conn_id());
  if (conn != nullptr) {
    conn->response.fields = httpResponse->fields();
  } else {
    Log::V("Unhandled http response (%lld)",
           (long long)httpResponse->conn_id());
  }
  return Status::OK;
}

Status InternalNetworkServiceImpl::TrackThread(
    ServerContext *context, const proto::JavaThreadRequest *threadData,
    proto::EmptyNetworkReply *reply) {
  ConnectionDetails *conn = network_cache_->GetDetails(threadData->conn_id());
  if (conn != nullptr) {
    bool found = false;
    for (auto thread : conn->threads) {
      if (thread.id == threadData->thread().id()) {
        found = true;
        break;
      }
    }
    if (!found) {
      conn->threads.emplace_back(threadData->thread().id(),
                                 threadData->thread().name());
    }
  }
  return Status::OK;
}

}  // namespace profiler
