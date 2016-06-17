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
#include "network_profiler_service.h"

using grpc::ServerContext;
using grpc::Status;
using profiler::proto::NetworkDataRequest;
using profiler::proto::NetworkProfilerData;

namespace profiler {

NetworkProfilerServiceImpl::NetworkProfilerServiceImpl() { StartCollector(-1); }

grpc::Status
NetworkProfilerServiceImpl::GetData(grpc::ServerContext *context,
                                    const proto::NetworkDataRequest *request,
                                    proto::NetworkDataResponse *response) {
  int pid = request->app_id();
  NetworkProfilerBuffer *found_buffer = nullptr;
  for (const auto &buffer : buffers_) {
    if (pid == buffer->pid()) {
      found_buffer = buffer.get();
      break;
    }
  }
  if (found_buffer == nullptr) {
    // TODO: Log request that has invalid pid.
    return ::grpc::Status(::grpc::StatusCode::NOT_FOUND,
                          "Network data for specific pid not found.");
  }

  proto::NetworkDataRequest_NetworkDataType type = request->data_type();
  int64_t start_time = request->start_timestamp();
  int64_t end_time = request->end_timestamp();
  for (auto &value : found_buffer->GetValues(start_time, end_time)) {
    if (type == NetworkDataRequest::ALL ||
        (type == NetworkDataRequest::TRAFFIC && value.has_traffic_data()) ||
        (type == NetworkDataRequest::CONNECTIVITY &&
         value.has_connectivity_data()) ||
        (type == NetworkDataRequest::CONNECTIONS &&
         value.has_connection_data())) {
      response->add_data()->Swap(&value);
    }
  }
  return Status::OK;
}

grpc::Status NetworkProfilerServiceImpl::StartMonitoringApp(
    grpc::ServerContext *context, const proto::NetworkStartRequest *request,
    proto::NetworkStartResponse *response) {
  StartCollector(request->app_id());
  return Status::OK;
}

grpc::Status NetworkProfilerServiceImpl::StopMonitoringApp(
    grpc::ServerContext *context, const proto::NetworkStopRequest *request,
    proto::NetworkStopResponse *response) {
  int pid = request->app_id();
  for (auto it = buffers_.begin(); it != buffers_.end(); it++) {
    if (pid == (*it)->pid()) {
      buffers_.erase(it);
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

void NetworkProfilerServiceImpl::StartCollector(int pid) {
  // Network collector for any app uses dumpsys command, while network collector
  // for an app reads from system file. Their sampling rates are different.
  int sample_milliseconds = pid == -1 ? 400 : 200;
  buffers_.emplace_back(new NetworkProfilerBuffer(kBufferCapacity, pid));
  collectors_.emplace_back(
      new NetworkCollector(pid, sample_milliseconds, buffers_.back()));
  collectors_.back()->Start();
}

}  // namespace profiler
