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

using profiler::proto::GraphicsDataRequest;
using profiler::proto::GraphicsDataResponse;
using profiler::proto::GraphicsStartRequest;
using profiler::proto::GraphicsStartResponse;
using profiler::proto::GraphicsStopRequest;
using profiler::proto::GraphicsStopResponse;

namespace profiler {

grpc::Status GraphicsServiceImpl::StartMonitoringGraphics(
    grpc::ServerContext *context, const GraphicsStartRequest *request,
    GraphicsStartResponse *response) {
  if (!collector_.IsRunning()) {
    collector_.Start();
  }
  response->set_status(GraphicsStartResponse::SUCCESS);
  return grpc::Status::OK;
}

grpc::Status GraphicsServiceImpl::StopMonitoringGraphics(
    grpc::ServerContext *context, const GraphicsStopRequest *request,
    GraphicsStopResponse *response) {
  if (collector_.IsRunning()) {
    collector_.Stop();
  }
  response->set_status(GraphicsStopResponse::SUCCESS);
  return grpc::Status::OK;
}

grpc::Status GraphicsServiceImpl::GetData(grpc::ServerContext *context,
                                          const GraphicsDataRequest *request,
                                          GraphicsDataResponse *response) {
  Trace trace("GRAPHICS:GetData");
  if (!collector_.IsRunning()) {
    return grpc::Status(grpc::StatusCode::NOT_FOUND,
                        "The graphics collector has not been started yet.");
  }
  collector_.graphics_cache().LoadGraphicsData(
      request->start_timestamp(), request->end_timestamp(), response);
  return grpc::Status::OK;
}

}  // namespace profiler
