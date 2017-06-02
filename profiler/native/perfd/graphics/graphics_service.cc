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
#include "graphics_service.h"

#include "utils/trace.h"

using profiler::proto::GraphicsDataResponse;
using profiler::proto::GraphicsDataRequest;
using profiler::proto::GraphicsStartRequest;
using profiler::proto::GraphicsStartResponse;
using profiler::proto::GraphicsStopRequest;
using profiler::proto::GraphicsStopResponse;

namespace profiler {

grpc::Status GraphicsServiceImpl::StartMonitoringApp(
    grpc::ServerContext *context, const GraphicsStartRequest *request,
    GraphicsStartResponse *response) {
  std::string app_and_package_name =
      request->app_package_name() + "/" + request->activity_name();
  GraphicsCollector *collector = GetCollector(app_and_package_name);
  if (!collector->IsRunning()) {
    collector->Start();
  }
  response->set_status(GraphicsStartResponse::SUCCESS);
  return grpc::Status::OK;
}

grpc::Status GraphicsServiceImpl::StopMonitoringApp(
    grpc::ServerContext *context, const GraphicsStopRequest *request,
    GraphicsStopResponse *response) {
  std::string app_and_package_name =
      request->app_package_name() + "/" + request->activity_name();
  auto got = collectors_.find(app_and_package_name);
  if (got != collectors_.end() && got->second.IsRunning()) {
    got->second.Stop();
  }
  response->set_status(GraphicsStopResponse::SUCCESS);
  return grpc::Status::OK;
}

grpc::Status GraphicsServiceImpl::GetData(grpc::ServerContext *context,
                                          const GraphicsDataRequest *request,
                                          GraphicsDataResponse *response) {
  Trace trace("GRAPHICS:GetData");
  std::string app_and_package_name =
      request->app_package_name() + "/" + request->activity_name();
  auto result = collectors_.find(app_and_package_name);
  if (result == collectors_.end()) {
    return grpc::Status(
        grpc::StatusCode::NOT_FOUND,
        "The graphics collector for the specified package name and activity has"
        " not been started yet.");
  }
  result->second.graphics_cache().LoadGraphicsData(
      request->start_timestamp(), request->end_timestamp(), response);
  return grpc::Status::OK;
}

GraphicsCollector *GraphicsServiceImpl::GetCollector(
    const std::string &app_and_activity_name) {
  auto got = collectors_.find(app_and_activity_name);
  // There is no collector for this combination yet
  if (got == collectors_.end()) {
    // Use the forward version of pair to avoid defining a move constructor.
    auto emplace_result = collectors_.emplace(
        std::piecewise_construct, forward_as_tuple(app_and_activity_name),
        forward_as_tuple(app_and_activity_name, clock_));
    assert(emplace_result.second);
    got = emplace_result.first;
  }
  return &got->second;
}

}  // namespace profiler
