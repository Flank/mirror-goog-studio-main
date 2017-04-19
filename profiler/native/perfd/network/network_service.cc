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
#include "network_service.h"

#include "utils/log.h"
#include "utils/trace.h"

namespace profiler {

using grpc::ServerContext;
using grpc::Status;
using profiler::proto::HttpConnectionData;
using profiler::proto::HttpDetailsRequest;
using profiler::proto::HttpDetailsResponse;
using profiler::proto::HttpRangeRequest;
using profiler::proto::HttpRangeResponse;
using profiler::proto::NetworkDataRequest;
using profiler::Log;
using std::string;

namespace {
// Network collector for device data uses dumpsys command, while network
// collector for an app reads from system file. Sampling rates are chosen
// based on how heavyweight each collector is expected to be.
static const int32_t kDeviceSampleRateMs = 400;
static const int32_t kAppSampleRateMs = 200;
}

NetworkServiceImpl::NetworkServiceImpl(NetworkCache *network_cache)
    : network_cache_(*network_cache) {
  StartDeviceCollector();
}

grpc::Status NetworkServiceImpl::GetData(
    grpc::ServerContext *context, const proto::NetworkDataRequest *request,
    proto::NetworkDataResponse *response) {
  Trace trace("NET:GetData");
  int pid = request->process_id();
  NetworkProfilerBuffer *app_buffer = nullptr;
  for (const auto &buffer : app_buffers_) {
    if (pid == buffer->pid()) {
      app_buffer = buffer.get();
      break;
    }
  }
  if (app_buffer == nullptr) {
    // TODO: Log request that has invalid pid.
    return ::grpc::Status(::grpc::StatusCode::NOT_FOUND,
                          "Network data for specific pid not found.");
  }

  auto type = request->type();
  int64_t start_time = request->start_timestamp();
  int64_t end_time = request->end_timestamp();

  for (const auto &value : device_buffer_->GetValues(start_time, end_time)) {
    if (type == NetworkDataRequest::ALL ||
        (type == NetworkDataRequest::CONNECTIVITY &&
         value.has_connectivity_data())) {
      *(response->add_data()) = value;
    }
  }

  for (const auto &value : app_buffer->GetValues(start_time, end_time)) {
    if (type == NetworkDataRequest::ALL ||
        (type == NetworkDataRequest::SPEED && value.has_speed_data()) ||
        (type == NetworkDataRequest::CONNECTIONS &&
         value.has_connection_data())) {
      *(response->add_data()) = value;
    }
  }
  return Status::OK;
}

grpc::Status NetworkServiceImpl::StartMonitoringApp(
    grpc::ServerContext *context, const proto::NetworkStartRequest *request,
    proto::NetworkStartResponse *response) {
  StartAppCollector(request->process_id());
  return Status::OK;
}

grpc::Status NetworkServiceImpl::StopMonitoringApp(
    grpc::ServerContext *context, const proto::NetworkStopRequest *request,
    proto::NetworkStopResponse *response) {
  int pid = request->process_id();
  for (auto it = app_buffers_.begin(); it != app_buffers_.end(); it++) {
    if (pid == (*it)->pid()) {
      app_buffers_.erase(it);
      break;
    }
  }
  for (auto it = collectors_.begin(); it != collectors_.end(); it++) {
    if (pid == (*it)->pid()) {
      (*it)->Stop();
      collectors_.erase(it);
      break;
    }
  }
  return Status::OK;
}

grpc::Status NetworkServiceImpl::GetHttpRange(grpc::ServerContext *context,
                                              const HttpRangeRequest *httpRange,
                                              HttpRangeResponse *response) {
  auto range =
      network_cache_.GetRange(httpRange->process_id(), httpRange->start_timestamp(),
                              httpRange->end_timestamp());

  for (const auto &conn : range) {
    HttpConnectionData *data = response->add_data();
    data->set_conn_id(conn.id);
    data->set_start_timestamp(conn.start_timestamp);
    data->set_downloading_timestamp(conn.downloading_timestamp);
    data->set_end_timestamp(conn.end_timestamp);
  }

  return Status::OK;
}

grpc::Status NetworkServiceImpl::GetHttpDetails(
    grpc::ServerContext *context, const HttpDetailsRequest *httpDetails,
    HttpDetailsResponse *response) {
  ConnectionDetails *conn = network_cache_.GetDetails(httpDetails->conn_id());
  HttpDetailsRequest::Type type = httpDetails->type();
  if (conn != nullptr && type != HttpDetailsRequest::UNSPECIFIED) {
    switch (type) {
      case HttpDetailsRequest::REQUEST: {
        auto request_details = response->mutable_request();
        request_details->set_url(conn->request.url);
        request_details->set_method(conn->request.method);
        request_details->set_fields(conn->request.fields);
        request_details->set_trace(conn->request.trace);
      } break;

      case HttpDetailsRequest::RESPONSE: {
        auto response_details = response->mutable_response();
        response_details->set_code(conn->response.code);
        response_details->set_fields(conn->response.fields);
      } break;

      case HttpDetailsRequest::REQUEST_BODY: {
        if (conn->request.payload_id != "") {
          auto body_details = response->mutable_request_body();
          body_details->set_payload_id(conn->request.payload_id);
        }
      } break;

      case HttpDetailsRequest::RESPONSE_BODY: {
        if (conn->response.payload_id != "") {
          auto body_details = response->mutable_response_body();
          body_details->set_payload_id(conn->response.payload_id);
        }
      } break;

      case HttpDetailsRequest::ACCESSING_THREADS: {
        if (conn->response.payload_id != "") {
          auto accessing_threads = response->mutable_accessing_threads();
          for (auto thread: conn->threads) {
            auto t = accessing_threads->add_thread();
            t->set_id(thread.id);
            t->set_name(thread.name);
          }
        }
      } break;

      default:
        Log::V("Unhandled details type (%d)", type);
        break;
    }
  }

  return Status::OK;
}

void NetworkServiceImpl::StartDeviceCollector() {
  auto *buffer = new NetworkProfilerBuffer(kBufferCapacity,
                                           proto::NetworkDataRequest::ANY_APP);
  device_buffer_.reset(buffer);
  StartCollectorFor(buffer, kDeviceSampleRateMs);
}

void NetworkServiceImpl::StartAppCollector(int pid) {
  auto *buffer = new NetworkProfilerBuffer(kBufferCapacity, pid);
  app_buffers_.emplace_back(buffer);
  StartCollectorFor(buffer, kAppSampleRateMs);
}

void NetworkServiceImpl::StartCollectorFor(NetworkProfilerBuffer *buffer,
                                           int32_t sample_rate_ms) {
  auto *collector = new NetworkCollector(buffer->pid(), sample_rate_ms, buffer);
  collectors_.emplace_back(collector);
  collector->Start();
}

}  // namespace profiler
