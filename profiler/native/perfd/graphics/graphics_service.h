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
#ifndef PERFD_GRAPHICS_GRAPHICS_SERVICE_H_
#define PERFD_GRAPHICS_GRAPHICS_SERVICE_H_

#include <grpc++/grpc++.h>
#include <map>

#include "perfd/daemon.h"
#include "perfd/graphics/graphics_collector.h"
#include "proto/graphics.grpc.pb.h"
#include "utils/clock.h"

namespace profiler {

class GraphicsServiceImpl final
    : public profiler::proto::GraphicsService::Service {
 public:
  GraphicsServiceImpl(Daemon::Utilities *utilities,
                      std::map<std::string, GraphicsCollector> *collectors)
      : clock_(utilities->clock()), collectors_(*collectors) {}

  virtual ~GraphicsServiceImpl() = default;

  grpc::Status StartMonitoringApp(
      grpc::ServerContext *context,
      const profiler::proto::GraphicsStartRequest *request,
      profiler::proto::GraphicsStartResponse *response) override;

  grpc::Status StopMonitoringApp(
      grpc::ServerContext *context,
      const profiler::proto::GraphicsStopRequest *request,
      profiler::proto::GraphicsStopResponse *response) override;

  grpc::Status GetData(
      grpc::ServerContext *context,
      const profiler::proto::GraphicsDataRequest *request,
      profiler::proto::GraphicsDataResponse *response) override;

 private:
  // Return the GraphicsCollector associated with the |app_and_activity_name|.
  // |app_and_activity_name| should be formatted as app name + "/" + activity
  // name.
  GraphicsCollector *GetCollector(const std::string &app_and_activity_name);

  const Clock &clock_;
  // Mapping "app/activity"->GraphicsCollector.
  std::map<std::string, GraphicsCollector> &collectors_;
};
}  // namespace profiler

#endif  // PERFD_GRAPHICS_GRAPHICS_SERVICE_H_
